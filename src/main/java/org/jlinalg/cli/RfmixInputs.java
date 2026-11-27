/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.RfmixOutputAudit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Independent complete raw-input census and source-equivalent selection contract. */
final class RfmixInputs {
    private RfmixInputs() { }
    record Variant(int position,String ref,String alt,int missing,boolean gtFirst,BitSet invalid,BitSet uncertainPhase,BitSet slashHomo) { }
    record Vcf(List<String> samples,List<Variant> selected,long allRecords) { }
    record Result(RfmixOutputAudit.Contract contract,Map<String,Object> audit,List<String> warnings) { }
    private record Anchor(int position,double cm) { }

    static Result audit(Path query,boolean queryGzip,Path reference,boolean referenceGzip,Path sampleMap,
                        boolean mapGzip,Path geneticMap,RfmixRequest.Spec spec,Runnable check)throws IOException {
        long[] decoded={0},cells={0};Vcf q=vcf(query,queryGzip,spec,decoded,cells,check),r=vcf(reference,referenceGzip,spec,decoded,cells,check);
        require((long)q.samples.size()+r.samples.size()<=spec.samples(),"Complete query plus reference VCF sample population exceeds capacity");
        var overlap=new HashSet<>(q.samples);overlap.retainAll(r.samples);require(overlap.isEmpty(),"Query/reference IDs overlap; original loading can overwrite query haplotypes. Supply distinct original sample IDs without automatic renaming");
        var referenceSet=new HashSet<>(r.samples);var refs=new ArrayList<RfmixOutputAudit.Reference>();var used=new HashSet<String>();var canonical=new LinkedHashMap<String,String>();long mapRows=0,ignoredMap=0,blankMap=0;
        try(var lines=new Lines(sampleMap,mapGzip,spec,decoded,check)) {
            for(String line;(line=lines.next())!=null;){check.run();mapRows++;if(line.isEmpty()||line.startsWith("#")||line.startsWith("^")){blankMap++;continue;}String[] f=line.split("\t",-1);if(f[0].isEmpty()||!referenceSet.contains(f[0])){ignoredMap++;continue;}
                require(f.length>=2,"Used original sample-map row needs TAB-separated sample and population");if(f[1].isEmpty()){ignoredMap++;continue;}require(label(f[1]),"Population labels must be literal nonempty text without control characters");require(used.add(f[0]),"Duplicate selected sample-map ID");String folded=asciiFold(f[1]);canonical.putIfAbsent(folded,f[1]);refs.add(new RfmixOutputAudit.Reference(f[0],canonical.get(folded)));
            }
        }
        require(!refs.isEmpty()&&canonical.size()<=spec.populations(),"No mapped reference samples or population capacity exceeded");var indices=new BitSet();for(int i=0;i<r.samples.size();i++)if(used.contains(r.samples.get(i)))indices.set(i);
        List<Anchor> anchors=geneticMap(geneticMap,spec,decoded,check);var retained=new ArrayList<RfmixOutputAudit.Locus>();var dispositions=new ArrayList<Object>();int qi=0,ri=0;int[] range=spec.range();long queryOnly=0,referenceOnly=0,outside=0,queryMissing=0,referenceMissing=0,slashHomo=0,allMissingQuery=0,allMissingReference=0;
        while(qi<q.selected.size()||ri<r.selected.size()) {
            check.run();Variant a=qi<q.selected.size()?q.selected.get(qi):null,b=ri<r.selected.size()?r.selected.get(ri):null;
            if(a==null||(b!=null&&b.position<a.position)){referenceOnly++;dispositions.add(map("position",b.position,"status","reference-only"));ri++;continue;}
            if(b==null||a.position<b.position){queryOnly++;dispositions.add(map("position",a.position,"status","query-only"));qi++;continue;}
            qi++;ri++;String why=null;
            if(a.position<range[0]||a.position>range[1]){outside++;why="outside-source-range";}
            else if(a.missing/(2.0*q.samples.size())>spec.missing()){queryMissing++;why="query-missingness";}
            else if(b.missing/(2.0*r.samples.size())>spec.missing()){referenceMissing++;why="reference-missingness-all-VCF-columns";}
            if(why!=null){dispositions.add(map("position",a.position,"status",why,"query_missing_alleles",a.missing,"reference_missing_alleles_all_columns",b.missing));continue;}
            require(a.ref.equals(b.ref)&&a.alt.equals(b.alt)&&!a.ref.isEmpty()&&!a.alt.isEmpty()&&!a.alt.contains(","),"Retained query/reference alleles differ or are not biallelic. Original joins positions without harmonizing alleles; correct inputs explicitly in NEW files, no automatic swap/repair");
            require(a.gtFirst&&b.gtFirst&&a.invalid.isEmpty()&&!b.invalid.intersects(indices),"Retained calls require GT first and diploid 0/1/missing alleles; no source parsing coercion is admitted");
            require(a.uncertainPhase.isEmpty()&&!b.uncertainPhase.intersects(indices),"Unresolved heterozygous/partially missing phase at a retained locus; supply actually phased calls, without automatic phasing or genotype repair");
            slashHomo+=a.slashHomo.cardinality();var homo=(BitSet)b.slashHomo.clone();homo.and(indices);slashHomo+=homo.cardinality();if(a.missing==2*q.samples.size())allMissingQuery++;if(b.missing==2*r.samples.size())allMissingReference++;
            double cm=interpolate(anchors,a.position);require(Double.isFinite(cm)&&Float.isFinite((float)cm),"Original map interpolation/binary32 storage is nonfinite");retained.add(new RfmixOutputAudit.Locus(a.position,cm));dispositions.add(map("position",a.position,"status","retained","genetic_cm_before_source_float32",cm,"genetic_cm_source_float32",(double)(float)cm,"query_missing_alleles",a.missing,"reference_missing_alleles_all_columns",b.missing));
        }
        require(!retained.isEmpty(),"No common retained loci; original model cannot produce a reportable local-ancestry result");var contract=new RfmixOutputAudit.Contract(spec.chromosome(),q.samples,refs,retained,spec.crfSpacing(),spec.em(),spec.reanalyze(),spec.autoWeight());
        var warnings=new ArrayList<String>();warnings.add("Original reference missingness uses every reference-VCF column, including samples absent from the sample map; no MAF/MAC filter is applied by this source.");warnings.add("Retained query/reference alleles are checked literally; source position-only joins are not used to silently align different allele encodings.");
        if(slashHomo>0)warnings.add("Retained "+slashHomo+" fully observed homozygous slash calls unchanged. Their phase is unambiguous, but original RFMix reports them as unphased; inspect its complete warnings.");
        if(allMissingQuery+allMissingReference>0)warnings.add("Explicit source missingness threshold retained wholly missing loci (query="+allMissingQuery+", reference-all-columns="+allMissingReference+"); inspect model uncertainty. No imputation/filter change was performed.");
        var populations=new ArrayList<>(canonical.values());populations.sort((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)));
        var summary=map("status","PASS_COMPLETE_CAPTURED_RFMIX_INPUT_CENSUS_AND_SOURCE_SELECTION","query_samples",q.samples,"reference_VCF_samples",r.samples,"selected_reference_samples",refs.stream().map(x->map("id",x.sample(),"population",x.population())).toList(),"populations_original_C_locale_order",populations,"query_all_records",q.allRecords,"reference_all_records",r.allRecords,"query_selected_chromosome_records",q.selected.size(),"reference_selected_chromosome_records",r.selected.size(),"retained_loci",retained.size(),"query_only",queryOnly,"reference_only",referenceOnly,"outside_range",outside,"query_missingness_exclusions",queryMissing,"reference_missingness_exclusions",referenceMissing,"source_effective_range_bp",List.of(range[0],range[1]),"sample_map_rows",mapRows,"sample_map_ignored_rows",ignoredMap,"sample_map_comment_or_blank_rows",blankMap,"reference_samples_excluded_by_map",r.samples.stream().filter(s->!used.contains(s)).toList(),"genetic_map_selected_anchors",anchors.size(),"decoded_bytes",decoded[0],"native_genotype_cells_inspected",cells[0],"retained_homozygous_slash_calls",slashHomo,"locus_dispositions",dispositions,"warnings",warnings);
        return new Result(contract,summary,List.copyOf(warnings));
    }
    private static Vcf vcf(Path path,boolean gzip,RfmixRequest.Spec spec,long[] decoded,long[] cells,Runnable check)throws IOException {
        List<String> samples=null;var variants=new ArrayList<Variant>();var seen=new HashSet<String>();boolean selectedBlock=false,selectedFinished=false;int previous=0;long rows=0;
        try(var lines=new Lines(path,gzip,spec,decoded,check)) {
            for(String line;(line=lines.next())!=null;){check.run();if(samples==null){if(line.startsWith("##"))continue;require(line.startsWith("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t"),"Missing exact VCF sample header");var fields=line.split("\t",-1);var names=new ArrayList<String>();for(int i=9;i<fields.length;i++){require(id(fields[i])&&seen.add(fields[i]),"Invalid/duplicate original VCF sample ID");names.add(fields[i]);}require(!names.isEmpty()&&names.size()<=spec.samples(),"VCF sample capacity exceeded or empty cohort");samples=List.copyOf(names);continue;}
                require(!line.isEmpty()&&!line.startsWith("#"),"Unexpected blank/header record within VCF body");String[] f=line.split("\t",-1);require(f.length==samples.size()+9,"Complete VCF row width differs from source header");require(++rows<=spec.variants(),"Complete raw variant-record capacity exceeded");
                if(!f[0].equals(spec.chromosome())){if(selectedBlock)selectedFinished=true;continue;}require(!selectedFinished,"Requested VCF chromosome occurs in multiple blocks; original source would silently ignore later blocks");selectedBlock=true;int pos=position(f[1],false);require(pos>previous,"Selected VCF chromosome positions must be strictly increasing and unique");previous=pos;
                cells[0]=Math.addExact(cells[0],2L*samples.size());require(cells[0]<=spec.cells(),"Complete selected-chromosome genotype cells exceed capacity");int missing=0;var invalid=new BitSet();var phase=new BitSet();var homo=new BitSet();
                for(int i=9;i<f.length;i++){String value=f[i];if(value.length()<2)missing+=2;else{if(!observed(value.charAt(0)))missing++;if(value.length()<3||!observed(value.charAt(2)))missing++;}String gt=value.split(":",2)[0];int sample=i-9;if(!gt.matches("[01.][|/][01.]")){invalid.set(sample);continue;}if(gt.charAt(1)=='/'&&gt.charAt(0)!=gt.charAt(2))phase.set(sample);else if(gt.charAt(1)=='/'&&gt.charAt(0)!='.')homo.set(sample);}
                variants.add(new Variant(pos,f[3],f[4],missing,f[8].equals("GT")||f[8].startsWith("GT:"),invalid,phase,homo));
            }
        }
        require(samples!=null&&!variants.isEmpty(),"No requested chromosome genotypes in complete VCF input");return new Vcf(samples,List.copyOf(variants),rows);
    }
    private static List<Anchor> geneticMap(Path path,RfmixRequest.Spec spec,long[] decoded,Runnable check)throws IOException {
        var result=new ArrayList<Anchor>();long rows=0;
        try(var lines=new Lines(path,false,spec,decoded,check)){
            for(String line;(line=lines.next())!=null;){check.run();require(++rows<=spec.variants(),"Genetic-map row capacity exceeded");if(line.isEmpty()||line.startsWith("#"))continue;String[] f=line.split("[ \t]",-1);String chromosome=f[0];boolean selected=chromosome.equals(spec.chromosome())||(chromosome.length()>=3&&chromosome.substring(0,3).equalsIgnoreCase("chr")&&chromosome.substring(3).equals(spec.chromosome()));if(!selected)continue;
                require(f.length>=3&&!f[1].isEmpty()&&!f[2].isEmpty(),"Selected genetic map needs single TAB/space-delimited chromosome, position and cM fields; no whitespace repair");int position=position(f[1],true);double cm=decimal(f[2]);result.add(new Anchor(position,cm));}
        }
        require(result.size()>=2,"Original source requires >=2 selected genetic-map anchors");result.sort(Comparator.comparingInt(Anchor::position));for(int i=1;i<result.size();i++)require(result.get(i).position>result.get(i-1).position&&result.get(i).cm>=result.get(i-1).cm,"Duplicate map positions or decreasing cM make original interpolation unreportable");return List.copyOf(result);
    }
    private static double interpolate(List<Anchor> anchors,int position) {
        if(position<anchors.get(0).position)return anchors.get(0).cm*(position/(double)anchors.get(0).position);
        int low=0,high=anchors.size()-1;while(low<high){int mid=(low+high+1)/2;if(anchors.get(mid).position<=position)low=mid;else high=mid-1;}int i=Math.min(low,anchors.size()-2);Anchor a=anchors.get(i),b=anchors.get(i+1);double rate=(b.cm-a.cm)/(b.position-(double)a.position);return a.cm+rate*(position-a.position);
    }
    private static boolean observed(char value){return value=='0'||value=='1';}
    private static boolean id(String value){return !value.isEmpty()&&value.chars().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c));}
    private static boolean label(String value){return !value.isEmpty()&&value.chars().noneMatch(Character::isISOControl);}
    private static String asciiFold(String value){StringBuilder result=new StringBuilder();for(char c:value.toCharArray())result.append(c>='A'&&c<='Z'?(char)(c+32):c);return result.toString();}
    private static int position(String value,boolean zero)throws IOException {require(value.matches(zero?"0|[1-9][0-9]*":"[1-9][0-9]*"),"Invalid original signed-int source coordinate");try{return Math.toIntExact(Long.parseLong(value));}catch(ArithmeticException|NumberFormatException e){throw new IOException("Source coordinate exceeds signed-int range",e);}}
    private static double decimal(String value)throws IOException {require(value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Invalid original map decimal");try{double result=Double.parseDouble(value);require(Double.isFinite(result),"Nonfinite map value");return result;}catch(NumberFormatException e){throw new IOException("Invalid map value",e);}}
    private static final class Lines implements AutoCloseable {
        private final BufferedReader reader;private final DigestInputStream raw;private final MessageDigest digest;private final Pin expected;private final RfmixRequest.Spec spec;private final long[] decoded;private final Runnable check;private boolean eof;
        Lines(Path path,boolean gzip,RfmixRequest.Spec spec,long[] decoded,Runnable check)throws IOException {
            this.spec=spec;this.decoded=decoded;this.check=check;expected=pin(path,Math.max(spec.inputBytes(),spec.decodedBytes()),check);try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}raw=new DigestInputStream(Files.newInputStream(path),digest);InputStream source=gzip?new GZIPInputStream(raw):raw;InputStream counted=new FilterInputStream(source){@Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0){check.run();decoded[0]=Math.addExact(decoded[0],n);require(decoded[0]<=spec.decodedBytes(),"Complete decoded input byte capacity exceeded");}return n;}@Override public int read()throws IOException{int value=in.read();if(value!=-1){decoded[0]++;require(decoded[0]<=spec.decodedBytes(),"Decoded input exceeds capacity");}return value;}};
            reader=new BufferedReader(new InputStreamReader(counted,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
        }
        String next()throws IOException {check.run();StringBuilder line=new StringBuilder();for(int c;(c=reader.read())!=-1;){if(c=='\n'){while(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);return line.toString();}require(line.length()<spec.lineCharacters(),"Raw input line character capacity exceeded");require(c!=0,"Embedded NUL would truncate original C parsing; input refused without repair");line.append((char)c);}eof=true;boolean empty=line.isEmpty();while(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);return empty?null:line.toString();}
        @Override public void close()throws IOException{reader.close();require(eof&&HexFormat.of().formatHex(digest.digest()).equals(expected.sha256()),"Actual parsed complete source byte stream changed or was not fully consumed");}
    }
}
