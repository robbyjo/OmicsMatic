/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Complete native input census and explicit source-format admission, without identifier repair. */
final class WaspInputs {
    private WaspInputs() { }
    record Reference(long length,String md5,long ambiguous) { }
    record Report(Map<String,Object> audit,Map<String,Reference> reference,Set<String> mappedContigs,Set<String> requiredVariantContigs,List<String> warnings) { }

    static Report audit(Path inputs,Map<?,?> effective,WaspRequest.Spec spec,Runnable check)throws IOException {
        var reference=fasta(inputs.resolve(member(text(effective.get("reference")))),spec.gzipReference(),spec,check);
        var mapped=new TreeSet<String>();var requiredVariants=new TreeSet<String>();long records=0,characters=0,unmappedFlag=0,unplaced=0,secondary=0,supplementary=0,paired=0,m5=0;var dictionary=new ArrayList<Object>();
        Path bam=inputs.resolve(member(text(effective.get("bam"))));
        try(var reader=SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(bam)) {
            for(var sequence:reader.getFileHeader().getSequenceDictionary().getSequences()) {
                String name=sequence.getSequenceName();contig(name);Reference expected=reference.get(name);
                require(expected!=null&&expected.length()==sequence.getSequenceLength(),"BAM dictionary and complete literal reference FASTA differ for "+name+". Supply the actual compatible reference/build; no renaming or coordinate repair occurs");
                String md5=sequence.getMd5();if(md5!=null){require(md5.equalsIgnoreCase(expected.md5()),"BAM M5 and complete FASTA sequence MD5 differ for "+name);m5++;}
                dictionary.add(map("name",name,"length",sequence.getSequenceLength(),"BAM_M5_present",md5!=null,"FASTA_uppercase_sequence_MD5",expected.md5(),"ambiguous_reference_bases",expected.ambiguous()));
            }
            for(var read:reader) {
                check.run();require(++records<=spec.records(),"Complete input BAM record capacity exceeded");characters=Math.addExact(characters,read.getSAMString().length());require(characters<=spec.nativeCharacters(),"Input native SAM-character census capacity exceeded");
                if(read.getReferenceIndex()<0)unplaced++;else{String name=read.getReferenceName();mapped.add(name);require(reference.containsKey(name),"Mapped BAM reference not in literal FASTA");}
                if(read.getReadUnmappedFlag())unmappedFlag++;if(read.isSecondaryAlignment())secondary++;if(read.getSupplementaryAlignmentFlag())supplementary++;
                if(read.getReadPairedFlag()){paired++;require(spec.paired(),"Paired BAM records require paired-with-optional-single-reads mode");}
                if(read.getReferenceIndex()>=0&&!read.isSecondaryOrSupplementary()&&(!read.getReadPairedFlag()||!read.getMateUnmappedFlag()&&read.getProperPairFlag()&&read.getReferenceIndex().equals(read.getMateReferenceIndex())))requiredVariants.add(read.getReferenceName());
            }
        }catch(RuntimeException e){throw new IOException("Invalid or unreadable complete input BAM",e);}
        require(records>0,"No input BAM records; no complete WASP analysis admitted");
        var warnings=new ArrayList<String>();
        warnings.add("The actual source BAM alignment and remapping-reference/options relationship is caller-supplied provenance. Dictionary lengths and available M5 tags are checked; matching names/lengths alone do not establish identical sequence build, mapper or paper preparation.");
        Map<String,Object> variants;
        if(spec.mode().equals("text"))variants=textVariants(inputs.resolve("variants/text"),requiredVariants,reference,spec,check,warnings);
        else variants=map("mode",spec.mode(),"HDF5_population_census_and_sample_contig_validation","required before original find");
        var audit=map("status","PASS_COMPLETE_NATIVE_WASP_BAM_FASTA_INPUT_CENSUS","BAM_records",records,"BAM_unplaced_records",unplaced,"BAM_unmapped_flag_records",unmappedFlag,"BAM_secondary_records",secondary,"BAM_supplementary_records",supplementary,"BAM_paired_records",paired,"BAM_dictionary",dictionary,"BAM_M5_checked_contigs",m5,"FASTA_contigs",reference.size(),"mapped_contigs",mapped,"contigs_with_reads_eligible_for_variant_filtering",requiredVariants,"variants",variants,"source_BAM_will_be_sorted_in_owned_workspace",true,"identifier_or_coordinate_repairs",false,"warnings",warnings);
        return new Report(audit,Map.copyOf(reference),Set.copyOf(mapped),Set.copyOf(requiredVariants),List.copyOf(warnings));
    }

    private static Map<String,Reference> fasta(Path path,boolean gzip,WaspRequest.Spec spec,Runnable check)throws IOException {
        var result=new LinkedHashMap<String,Reference>();String name=null;StringBuilder token=new StringBuilder();boolean header=false,description=false,lineStart=true,pendingCR=false;long length=0,ambiguous=0,total=0;MessageDigest md5=digest();
        try(InputStream raw=Files.newInputStream(path);InputStream stream=new BufferedInputStream(gzip?new GZIPInputStream(raw):raw)) {
            byte[] buffer=new byte[65536];
            for(int n;(n=stream.read(buffer))>=0;) {
                check.run();total=Math.addExact(total,n);require(total<=spec.decodedBytes(),"Complete decoded FASTA capacity exceeded");
                for(int i=0;i<n;i++) {
                    int c=buffer[i]&255;
                    if(pendingCR){require(c=='\n',"Reference FASTA has a CR outside CRLF; supply the actual standard FASTA without implicit whitespace repair");pendingCR=false;}
                    if(c=='\r'){pendingCR=true;continue;}
                    if(c=='\n') {
                        if(header){name=token.toString();contig(name);header=false;}lineStart=true;continue;
                    }
                    if(lineStart&&c=='>') {
                        if(name!=null){require(length>0&&!result.containsKey(name),"Empty or repeated literal FASTA contig");result.put(name,new Reference(length,HexFormat.of().formatHex(md5.digest()),ambiguous));}
                        require(result.size()<spec.files(),"Reference contig population exceeds declared metadata/file capacity");token.setLength(0);header=true;description=false;name=null;length=0;ambiguous=0;md5=digest();lineStart=false;continue;
                    }
                    lineStart=false;
                    if(header){if(c==' '||c=='\t')description=true;else if(!description){require(c>32&&c<127&&token.length()<spec.lineCharacters(),"Invalid or oversized literal FASTA identifier");token.append((char)c);}continue;}
                    require(name!=null,"Reference sequence precedes a FASTA identifier");
                    int upper=c>='a'&&c<='z'?c-32:c;require("ACGTURYSWKMBDHVN".indexOf(upper)>=0,"Reference FASTA contains whitespace or a non-IUPAC sequence byte; no normalization is performed");
                    length++;require(length<=Integer.MAX_VALUE,"Reference contig exceeds BAM signed-coordinate domain");if("ACGT".indexOf(upper)<0)ambiguous++;md5.update((byte)upper);
                }
            }
        }
        require(!pendingCR,"Incomplete reference CRLF");
        if(header)name=token.toString();
        require(name!=null&&length>0&&!result.containsKey(name),"Missing, empty, repeated or incomplete final reference FASTA contig");contig(name);result.put(name,new Reference(length,HexFormat.of().formatHex(md5.digest()),ambiguous));return result;
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("MD5");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void contig(String name)throws IOException {require(name!=null&&!name.isEmpty()&&!name.equals(".")&&!name.equals("..")&&name.chars().allMatch(c->c>32&&c<127&&c!='/'&&c!='\\'),"Require a literal nonempty ASCII contig token without path separators");}

    private static Map<String,Object> textVariants(Path directory,Set<String> mapped,Map<String,Reference> reference,WaspRequest.Spec spec,Runnable check,List<String> warnings)throws IOException {
        for(String name:mapped)if(!Files.isRegularFile(directory.resolve(name+".snps.txt.gz")))throw new WaspRuntime.Unavailable("Required literal variant file absent for "+name+". Supply "+name+".snps.txt.gz; use an explicit empty gzip file only when that contig truly has no variants. Dependent analysis skipped");
        var files=new ArrayList<Object>();long rows=0,uppercase=0,hyphens=0;long[] decoded={0};
        try(var entries=Files.newDirectoryStream(directory)) {
            var names=new ArrayList<Path>();entries.forEach(names::add);names.sort(Comparator.comparing(p->p.getFileName().toString()));
            for(Path path:names) {
                check.run();String file=path.getFileName().toString();require(file.endsWith(".snps.txt.gz"),"Text variant directory must contain only complete original <contig>.snps.txt.gz files");String chrom=file.substring(0,file.length()-12);contig(chrom);Reference ref=reference.get(chrom);require(ref!=null,"Text variant contig not in literal reference FASTA: "+chrom);
                long last=0,count=0;
                try(var stream=new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
                    for(String line;(line=line(stream,spec.lineCharacters(),spec.decodedBytes(),decoded,check))!=null;) {
                        String[] words=line.strip().split("\\s+");require(words.length>=3,"Original text variant row requires position, ref and alt");long pos;try{pos=Long.parseLong(words[0]);}catch(NumberFormatException e){throw new IOException("Invalid literal SNP position",e);}
                        require(pos>last&&pos<=ref.length(),"Text SNP positions must be positive, unique, increasing and within the literal reference; no source index overwrite or coordinate repair admitted");last=pos;
                        for(int j=1;j<=2;j++){String allele=words[j];require(allele.chars().allMatch(c->c>32&&c<127),"Literal ASCII SNP alleles required");String normalized=allele.toUpperCase(Locale.ROOT).replace("-","");require(normalized.length()<=10,"Original text reader would truncate this allele to S10. Supply compatible original HDF5 input; no complete result admitted");if(!allele.equals(allele.toUpperCase(Locale.ROOT)))uppercase++;if(allele.contains("-"))hyphens++;}
                        count++;require(++rows<=spec.variantRows(),"Complete text variant row capacity exceeded");
                    }
                }
                files.add(map("file",file,"contig",chrom,"rows",count,"last_position",last));
            }
        }
        if(uppercase>0||hyphens>0)warnings.add("Original WASP text loading uppercases alleles and removes hyphens before SNP/indel classification. Raw input bytes are preserved; source normalization is explicit: uppercase-affected alleles="+uppercase+", hyphen-affected alleles="+hyphens+".");
        return map("mode","text","rows",rows,"files",files,"source_uppercase_affected_alleles",uppercase,"source_hyphen_affected_alleles",hyphens,"S10_truncation_or_duplicate_overwrite_admitted",false);
    }
    static String line(InputStream stream,int maximum,long maximumDecoded,long[] decoded,Runnable check)throws IOException {
        var value=new ByteArrayOutputStream();int b;boolean seen=false;
        while((b=stream.read())>=0) {
            seen=true;require(++decoded[0]<=maximumDecoded,"Complete decoded text capacity exceeded");if((decoded[0]&65535)==0)check.run();
            if(b=='\n')break;require(value.size()<maximum,"Complete text line capacity exceeded");value.write(b);
        }
        if(!seen)return null;byte[] raw=value.toByteArray();int length=raw.length;if(length>0&&raw[length-1]=='\r')length--;for(int i=0;i<length;i++)require(raw[i]>0&&raw[i]<127&&raw[i]!='\r',"Non-ASCII or embedded control in source text");return new String(raw,0,length,StandardCharsets.US_ASCII);
    }

    static Map<String,Object> hdf(Object raw,Report input,WaspRequest.Spec spec,Runnable check)throws IOException {
        var census=object(raw);require("COMPLETE_FIXED_TYPE_HDF5_POPULATION_CENSUS".equals(census.get("status")),"Complete fixed-type original HDF5 census required");integer(census.get("cells"),0,spec.hdfCells());
        var files=new HashMap<String,Map<String,Map<?,?>>>();
        for(Object item:list(census.get("files"))) {
            var file=object(item);String role=text(file.get("role"));require(!files.containsKey(role),"Repeated HDF role");var nodes=new HashMap<String,Map<?,?>>();
            for(Object value:list(file.get("nodes"))){var node=object(value);String name=text(node.get("path"));require(nodes.put(name,node)==null,"Repeated HDF node path");}files.put(role,nodes);
        }
        require(files.keySet().equals(Set.of("snp-table.h5","snp-index.h5","haplotypes.h5")),"Complete SNP-table, index and haplotype HDF populations required");
        var table=files.get("snp-table.h5");var index=files.get("snp-index.h5");var haps=files.get("haplotypes.h5");var mappings=new ArrayList<Object>();var warnings=new ArrayList<String>();
        var explicit=new HashSet<>(strings(spec.settings().get("explicit_no_variant_contigs")));require(input.mappedContigs().containsAll(explicit),"Explicit no-variant declaration names an unused BAM contig");
        List<String> requested=spec.variants().get("samples")==null?null:strings(spec.variants().get("samples"));boolean fallback=spec.settings().get("hdf_contig_policy").equals("original-add-remove-chr");long rows=0;
        for(String chrom:new TreeSet<>(input.mappedContigs())) {
            check.run();String nodeName="/"+chrom,other="/"+(chrom.startsWith("chr")?chrom.substring(3):"chr"+chrom);
            if(!table.containsKey(nodeName)&&fallback&&table.containsKey(other))nodeName=other;
            if(!table.containsKey(nodeName)) {
                if(!input.requiredVariantContigs().contains(chrom)&&!table.containsKey(other)) {explicit.remove(chrom);mappings.add(map("BAM_contig",chrom,"original_SNP_node",null,"all_placed_records_ineligible_for_variant_filtering",true));warnings.add("Original HDF source has no variants for "+chrom+", whose placed BAM records are all discarded by source flag/mate rules before variant filtering.");continue;}
                require(explicit.remove(chrom),"HDF variants absent for literal mapped contig "+chrom+". Supply matching data, explicitly select the original chr-prefix policy, or declare a scientifically justified no-variant contig; implicit no-SNP admission refused");
                require(!table.containsKey(other),"A prefix-alternative HDF node exists. An explicit no-variant declaration must not bypass an available original fallback");
                mappings.add(map("BAM_contig",chrom,"original_SNP_node",null,"source_assumes_no_variants",true));warnings.add("EXPLICIT NO-VARIANT CONTIG: original WASP will assume no SNPs on "+chrom+" because the caller explicitly declared missing genotype coverage intentional.");continue;
            }
            require(!explicit.contains(chrom),"No-variant declaration contradicts an existing HDF node");
            var snp=table.get(nodeName);require("table".equals(snp.get("kind")),"Original SNP node must be a fixed-type table");long n=integer(snp.get("rows"),0,spec.variantRows());rows=Math.addExact(rows,n);require(rows<=spec.variantRows(),"Complete HDF SNP-row capacity exceeded");
            var positions=object(snp.get("positions"));require(integer(positions.get("nonpositive"),0,Long.MAX_VALUE)==0&&integer(positions.get("nonincreasing"),0,Long.MAX_VALUE)==0,"HDF SNP positions must be positive, unique and increasing; no source index overwrite or reordering admitted");
            long maximum=positions.get("maximum")==null?0:integer(positions.get("maximum"),1,input.reference().get(chrom).length());
            require(index.containsKey(nodeName)&&haps.containsKey(nodeName),"Source SNP/index/haplotype node correspondence differs");
            List<Long> indexShape=shape(index.get(nodeName));require(indexShape.size()==1&&indexShape.get(0)>=maximum&&indexShape.get(0)<=input.reference().get(chrom).length(),"HDF SNP index shape differs from the literal reference/SNP coordinate domain");
            var correspondence=object(index.get(nodeName).get("variant_index_correspondence"));require(integer(correspondence.get("checked_cells"),0,Long.MAX_VALUE)==indexShape.get(0)&&integer(correspondence.get("SNP_rows"),0,Long.MAX_VALUE)==n&&integer(correspondence.get("mismatched_cells"),0,Long.MAX_VALUE)==0,"Complete HDF index cells do not map the original SNP coordinates to exactly their rows");
            String sampleName="/samples_"+chrom,otherSample="/samples_"+(chrom.startsWith("chr")?chrom.substring(3):"chr"+chrom);
            if(!haps.containsKey(sampleName)&&fallback)sampleName=otherSample;
            require(haps.containsKey(sampleName),"Original HDF sample table unavailable for "+chrom);var sampleTable=haps.get(sampleName);List<String> samples=strings(sampleTable.get("sample_names"));
            require(new HashSet<>(samples).size()==samples.size()&&!samples.isEmpty(),"HDF sample identities must be nonempty and unique; original duplicate/missing-sample bypass refused");for(String sample:samples)WaspRequest.scalar(sample);
            if(requested!=null)require(samples.containsAll(requested),"Requested HDF samples are absent on "+chrom+"; original would silently reduce or bypass variant filtering. Supply matching complete samples; no complete analysis admitted");
            List<Long> hapShape=shape(haps.get(nodeName));require(hapShape.equals(List.of(n,2L*samples.size())),"HDF diploid haplotype shape differs from SNP/sample populations");
            require(integer(haps.get(nodeName).get("invalid_original_cell_values"),0,Long.MAX_VALUE)==0,"HDF haplotype cells must be -1, 0 or 1; no missingness or genotype repair occurs");
            String phaseName="/phase_"+nodeName.substring(1);boolean phase=haps.containsKey(phaseName);
            if(phase)require(shape(haps.get(phaseName)).equals(List.of(n,(long)samples.size()))&&integer(haps.get(phaseName).get("invalid_original_cell_values"),0,Long.MAX_VALUE)==0,"HDF phase matrix differs from SNP/sample populations or has values outside 0/1");
            if(!nodeName.equals("/"+chrom)||!sampleName.equals("/samples_"+chrom))warnings.add("EXPLICIT ORIGINAL CHR-PREFIX MATCH: BAM "+chrom+" uses SNP/index/haplotype "+nodeName+" and sample table "+sampleName+". This is the caller-selected original source fallback; no files or identifiers were rewritten.");
            mappings.add(map("BAM_contig",chrom,"original_SNP_node",nodeName,"original_sample_node",sampleName,"rows",n,"samples",samples.size(),"selected_samples",requested==null?samples:requested,"phase_matrix_present",phase,"source_assumes_no_variants",false));
        }
        require(explicit.isEmpty(),"Unused explicit no-variant contig declaration");
        return map("status","PASS_COMPLETE_HDF_POPULATION_AND_EXPLICIT_SOURCE_CONTIG_SAMPLE_CONTRACT","HDF_cells",census.get("cells"),"SNP_rows_on_mapped_contigs",rows,"contigs",mappings,"warnings",warnings,"identifiers_or_genotypes_rewritten",false);
    }
    private static List<Long> shape(Map<?,?> node)throws IOException {
        require("array".equals(node.get("kind")),"Fixed-type original HDF array required");var result=new ArrayList<Long>();for(Object value:list(object(node.get("data")).get("shape")))result.add(integer(value,0,Integer.MAX_VALUE));return result;
    }

    static Map<String,Object> vcf(Path inputs,Map<?,?> variants,Report input,WaspRequest.Spec spec,Runnable check)throws IOException {
        var chromosomes=new LinkedHashMap<String,Long>();long[] decoded={0};
        try(var stream=new BufferedInputStream(Files.newInputStream(inputs.resolve(member(text(variants.get("chromosomes"))))))) {
            for(String line;(line=line(stream,spec.lineCharacters(),spec.decodedBytes(),decoded,check))!=null;){String[] words=line.strip().split("\\s+");require(words.length==2,"Original chromosome-info rows require exactly name and length");contig(words[0]);long length;try{length=Long.parseLong(words[1]);}catch(NumberFormatException e){throw new IOException("Invalid original chromosome-info length",e);}require(length>0&&length<=Integer.MAX_VALUE&&chromosomes.put(words[0],length)==null,"Repeated or invalid chromosome-info contig");}
        }
        require(!chromosomes.isEmpty(),"Empty original chromosome-info population");
        for(var entry:chromosomes.entrySet())require(input.reference().containsKey(entry.getKey())&&input.reference().get(entry.getKey()).length()==entry.getValue(),"Literal converter chromosome-info names/lengths must match the supplied reference; no build or coordinate repair occurs");
        var files=new ArrayList<Object>();var seen=new HashSet<String>();long rows=0,missing=0,partial=0,unphased=0,nonpass=0,estimatedCells=0;
        for(String file:strings(variants.get("vcfs"))) {
            Path path=inputs.resolve(member(file));String chrom=null;List<String> samples=null;long last=0,count=0;MessageDigest positions=shaDigest(),names=shaDigest(),refs=shaDigest(),alts=shaDigest(),haps=shaDigest(),phase=shaDigest();
            boolean compressed;try(var probe=Files.newInputStream(path)){compressed=probe.read()==31&&probe.read()==139;}
            try(InputStream raw=Files.newInputStream(path);InputStream stream=new BufferedInputStream(compressed?new GZIPInputStream(raw):raw)) {
                for(String line;(line=utf8Line(stream,spec.lineCharacters(),spec.decodedBytes(),decoded,check))!=null;) {
                    check.run();if(line.startsWith("##"))continue;
                    if(line.startsWith("#CHROM\t")) {
                        require(samples==null,"Repeated VCF sample header");String[] fields=line.split("\t",-1);require(fields.length>=10&&Arrays.equals(Arrays.copyOf(fields,9),new String[]{"#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO","FORMAT"}),"Complete VCF GT/sample header required");samples=new ArrayList<>(Arrays.asList(fields).subList(9,fields.length));require(new HashSet<>(samples).size()==samples.size(),"Repeated literal VCF sample identifier");
                        for(String sample:samples)require(!sample.isEmpty()&&sample.chars().noneMatch(Character::isWhitespace)&&sample.getBytes(StandardCharsets.UTF_8).length<100,"Original C converter would truncate or misparse this sample name (100-byte field); supply intact validated HDF5 rather than altering identifiers");continue;
                    }
                    require(samples!=null&&!line.startsWith("#"),"VCF rows require their complete sample header");String[] fields=line.split("\t",-1);require(fields.length==9+samples.size(),"VCF genotype/sample field count differs from header");
                    if(chrom==null){chrom=fields[0];contig(chrom);require(chrom.getBytes(StandardCharsets.UTF_8).length<255&&chromosomes.containsKey(chrom)&&seen.add(chrom),"Supply one complete literal per-chromosome VCF matching chromosome-info names; no filename/identifier repair admitted");}
                    require(chrom.equals(fields[0]),"Original converter requires separate per-chromosome VCFs");long position;try{position=Long.parseLong(fields[1]);}catch(NumberFormatException e){throw new IOException("Invalid source VCF position",e);}require(position>last&&position<=chromosomes.get(chrom),"VCF positions must be positive, unique, increasing and within chromosome-info bounds");last=position;
                    require(!fields[2].isEmpty()&&fields[2].getBytes(StandardCharsets.UTF_8).length<255,"Original C converter would truncate this variant identifier (255-byte field)");
                    for(int index:new int[]{3,4})require(fields[index].matches("[ACGTNacgtn]+")&&fields[index].getBytes(StandardCharsets.US_ASCII).length<100,"Original C converter silently truncates long alleles and cannot represent symbolic/multiallelic alleles faithfully. Supply literal biallelic alleles below its100-byte field limit or intact validated HDF5; no complete result admitted");
                    require(fields[8].getBytes(StandardCharsets.UTF_8).length<1024,"Original C converter would truncate FORMAT");String[] formats=fields[8].split(":",-1);int gt=-1;for(int i=0;i<formats.length;i++)if(formats[i].equals("GT")){require(gt<0,"Repeated VCF GT field");gt=i;}require(gt>=0,"Original haplotype conversion requires an explicit GT field");
                    for(int i=0;i<samples.size();i++) {
                        String[] values=fields[9+i].split(":",-1);require(gt<values.length,"VCF sample lacks its declared GT field");String genotype=values[gt];int end=0;for(int j=0;j<=gt;j++)end+=values[j].getBytes(StandardCharsets.UTF_8).length+(j==0?0:1);require(end<1024,"Original C converter would truncate GT inside its1024-byte sample buffer");
                        require(genotype.equals(".")||genotype.matches("[01.][|/][01.]"),"Original C haplotype conversion cannot preserve this haploid/polyploid/non-biallelic GT. Supply validated compatible HDF5 with actual ploidy preparation; no allele filling or reinterpretation admitted");
                        int one,two,phased;if(genotype.contains(".")){one=-1;two=-1;phased=1;if(genotype.equals(".")||genotype.equals("./.")||genotype.equals(".|."))missing++;else partial++;}else{one=genotype.charAt(0)-'0';two=genotype.charAt(2)-'0';phased=genotype.charAt(1)=='|'?1:0;}
                        if(genotype.contains("/"))unphased++;haps.update((byte)one);haps.update((byte)two);phase.update((byte)phased);
                    }
                    for(int shift=0;shift<64;shift+=8)positions.update((byte)(position>>>shift));fixed(names,fields[2],255);fixed(refs,fields[3],100);fixed(alts,fields[4],100);if(!fields[6].equals("PASS")&&!fields[6].equals("."))nonpass++;
                    count++;require(++rows<=spec.variantRows(),"Complete original VCF row capacity exceeded");
                }
            }
            require(chrom!=null&&samples!=null&&count>0,"Empty original VCF chromosome population; use existing explicit HDF5/no-variant declarations for intentional absence");
            estimatedCells=Math.addExact(estimatedCells,Math.addExact(chromosomes.get(chrom),Math.addExact(samples.size(),Math.multiplyExact(count,3L*samples.size()+4))));require(estimatedCells<=spec.hdfCells(),"Complete original HDF5 cell population would exceed declared capacity");
            files.add(map("file",file,"chromosome",chrom,"chromosome_length",chromosomes.get(chrom),"rows",count,"samples",samples,"name_SHA256",hex(names),"position_SHA256",hex(positions),"reference_SHA256",hex(refs),"alternate_SHA256",hex(alts),"haplotype_SHA256",hex(haps),"phase_SHA256",hex(phase)));
        }
        var warnings=new ArrayList<String>();if(partial>0)warnings.add("ORIGINAL GT MISSINGNESS RULE: "+partial+" partial-missing diploid calls become two missing alleles in original snp2h5; observed half-genotypes are not filled or preserved by that source converter.");if(missing+partial>0)warnings.add("Original snp2h5 sets phase=1 even when GT parsing fails and both alleles become missing. That source flag is retained and does not establish statistical phasing of missing calls.");if(unphased>0)warnings.add("Original conversion sets phase=0 for fully parsed unphased '/' calls; no statistical phasing is performed. Calls with '/'="+unphased+" (including any missing calls handled by the separate original missingness rule).");if(nonpass>0)warnings.add("The supplied VCF contains "+nonpass+" rows with FILTER other than PASS or '.'. The adapter does not apply additional VCF QC filtering; supply the experiment's required prepared variant population.");
        return map("status","PASS_COMPLETE_NATIVE_VCF_TO_ORIGINAL_HDF_CONVERSION_INPUT_CONTRACT","files",files,"rows",rows,"estimated_fixed_type_HDF_cells",estimatedCells,"fully_missing_GT",missing,"partial_missing_GT_source_masks_both_alleles",partial,"GT_with_unphased_separator",unphased,"nonPASS_nonmissing_FILTER_rows",nonpass,"warnings",warnings,"source_field_truncation_admitted",false);
    }
    static void convertedHdf(Object raw,Map<?,?> vcf)throws IOException {
        var files=new HashMap<String,Map<String,Map<?,?>>>();for(Object item:list(object(raw).get("files"))){var f=object(item);var nodes=new HashMap<String,Map<?,?>>();for(Object value:list(f.get("nodes"))){var node=object(value);nodes.put(text(node.get("path")),node);}files.put(text(f.get("role")),nodes);}
        for(Object item:list(vcf.get("files"))) {
            var expected=object(item);String chrom=text(expected.get("chromosome")),name="/"+chrom;var table=files.get("snp-table.h5").get(name);require(table!=null&&integer(table.get("rows"),0,Long.MAX_VALUE)==integer(expected.get("rows"),0,Long.MAX_VALUE),"Original converter chromosome/row population differs from the complete VCF");
            var hashes=new HashMap<String,String>();for(Object value:list(table.get("columns"))){var column=object(value);hashes.put(text(column.get("name")),text(column.get("sha256")));}
            require(hashes.get("name").equals(expected.get("name_SHA256"))&&hashes.get("pos").equals(expected.get("position_SHA256"))&&hashes.get("allele1").equals(expected.get("reference_SHA256"))&&hashes.get("allele2").equals(expected.get("alternate_SHA256")),"Original converter changed, truncated or reordered VCF variant identity/positions/alleles");
            var haps=files.get("haplotypes.h5");require(haps.containsKey(name)&&haps.containsKey("/phase_"+chrom)&&haps.containsKey("/samples_"+chrom),"Complete original converted haplotype/phase/sample nodes required");
            require(object(haps.get(name).get("data")).get("sha256").equals(expected.get("haplotype_SHA256"))&&object(haps.get("/phase_"+chrom).get("data")).get("sha256").equals(expected.get("phase_SHA256")),"Original converted GT/phase cells differ from complete native source GT parsing");
            require(strings(haps.get("/samples_"+chrom).get("sample_names")).equals(strings(expected.get("samples"))),"Original converter changed, truncated or reordered sample identities");
        }
    }
    private static MessageDigest shaDigest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(MessageDigest digest){return HexFormat.of().formatHex(digest.digest());}
    private static void fixed(MessageDigest digest,String text,int width)throws IOException {byte[] raw=text.getBytes(StandardCharsets.UTF_8);require(raw.length<width,"Lossy fixed-width C conversion refused");digest.update(raw);digest.update(new byte[width-raw.length]);}
    private static String utf8Line(InputStream stream,int maximum,long maxBytes,long[] decoded,Runnable check)throws IOException {var out=new ByteArrayOutputStream();boolean seen=false;for(int b;(b=stream.read())>=0;){seen=true;require(++decoded[0]<=maxBytes,"Complete decoded VCF capacity exceeded");if((decoded[0]&65535)==0)check.run();if(b=='\n')break;require(out.size()<maximum,"Complete VCF line capacity exceeded");out.write(b);}if(!seen)return null;byte[] raw=out.toByteArray();if(raw.length>0&&raw[raw.length-1]=='\r')raw=Arrays.copyOf(raw,raw.length-1);return utf8(raw);}
}
