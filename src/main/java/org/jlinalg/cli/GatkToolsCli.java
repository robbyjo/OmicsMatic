/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.BufferedLineReader;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import htsjdk.tribble.readers.LineIteratorImpl;
import htsjdk.tribble.readers.SynchronousLineReader;
import htsjdk.variant.variantcontext.*;
import htsjdk.variant.vcf.*;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.FastaIndexes;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed GATK germline calling, gVCF combination/genotyping and annotation hard filters. */
final class GatkToolsCli {
    private GatkToolsCli() { }
    static final String JAR_SHA256="882e180707e0e6887885853fc486fa54098620fe9a80bdcdaaa655c7e3861365";
    private static final Set<String> COMMON=Set.of("method","out");
    private static final Set<String> RUNTIME=Set.of("gatk","java","memory-mb","timeout");
    private static final Set<String> FILTER=Set.of("missing-policy","snp-qd-min","snp-fs-max","snp-mq-min","snp-mq-rank-min","snp-read-pos-min","indel-qd-min","indel-fs-max","indel-read-pos-min");
    static int run(String[] arguments,PrintStream output,PrintStream error) {
        if(Arrays.asList(arguments).contains("--help")){output.println(help());return 0;}
        try {
            var all=new HashSet<>(COMMON);all.addAll(RUNTIME);all.addAll(FILTER);all.addAll(Set.of("reference","input","inputs-list","ploidy","emit","region","min-baseq","call-confidence"));
            Map<String,String> o=options(arguments,all);String method=required(o,"method");var applicable=new HashSet<>(COMMON);
            if(!method.equals("reference"))applicable.addAll(RUNTIME);
            switch(method){
                case"reference"->applicable.add("reference");
                case"haplotype-call"->applicable.addAll(Set.of("reference","input","ploidy","emit","region","min-baseq","call-confidence"));
                case"combine-gvcfs"->applicable.addAll(Set.of("reference","inputs-list"));
                case"genotype-gvcfs"->applicable.addAll(Set.of("reference","input","ploidy","call-confidence"));
                case"hard-filter"->{applicable.add("input");applicable.addAll(FILTER);}
                default->throw new IllegalArgumentException("Unknown gatk-tools method");
            }
            for(String key:o.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            int timeout=integer(o,"timeout",3600,1,86400),memory=integer(o,"memory-mb",2048,256,65536);
            if(Set.of("haplotype-call","genotype-gvcfs").contains(method)){required(o,"ploidy");integer(o,"ploidy",2,1,64);}
            if(method.equals("hard-filter")&&!Set.of("pass","fail").contains(required(o,"missing-policy")))throw new IllegalArgumentException("missing-policy must explicitly be pass or fail");
            int[] exit={0};
            externalTransaction(o,"gatk-tools-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var inputs=new LinkedHashMap<String,String>();var findings=new ArrayList<String>();
                session.put("method",method);session.put("parameters",o);session.put("scope","Typed local germline workflows only; explicit uniform ploidy. No inferred pool/sex/mosaic model, somatic caller, legacy realignment, BQSR/VQSR, Spark or Python workflow.");
                try {
                    if(method.equals("reference")){
                        Path fasta=input(required(o,"reference"),inputs);var dictionary=actualDictionary(fasta);var index=FastaIndexes.build(fasta);
                        index.write(dir.resolve(fasta.getFileName()+".fai"));
                        Path name=ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(fasta);
                        try(var writer=Files.newBufferedWriter(dir.resolve(name.getFileName()),StandardCharsets.UTF_8)){
                            var header=new SAMFileHeader(dictionary);new SAMTextHeaderCodec().encode(writer,header);
                        }
                        session.put("engine","Native Java / HTSJDK 5.0.0");
                        table(dir.resolve("results.tsv"),List.of("metric","value"),List.<String[]>of(new String[]{"sequences",Integer.toString(dictionary.size())}));
                    }else{
                        Path jar=Path.of(required(o,"gatk")).toAbsolutePath().normalize();
                        if(!Files.isRegularFile(jar)||!hash(jar).equals(JAR_SHA256))throw new DependencyUnavailable("GATK JAR is missing or differs from the verified official 4.7.0.0 local JAR");
                        session.put("gatk_jar_sha256",JAR_SHA256);Files.createDirectory(dir.resolve("tmp"));
                        String java=executable(o.getOrDefault("java",Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString()));
                        var base=new ArrayList<>(List.of(java,"-Xmx"+memory+"m","-XX:ParallelGCThreads=1","-Djava.io.tmpdir="+dir.resolve("tmp"),"-Dsamjdk.use_async_io_read_samtools=false","-Dsamjdk.use_async_io_write_samtools=false","-Dsamjdk.use_async_io_write_tribble=false","-jar",jar.toString()));
                        var probe=new ArrayList<>(base);probe.add("--version");
                        try{if(execute(probe,dir,"version",60,session)!=0||!Files.readString(dir.resolve("version.log")).contains("The Genome Analysis Toolkit (GATK) v4.7.0.0"))throw new IOException("Pinned application could not load");}
                        catch(IOException failure){throw new DependencyUnavailable("GATK requires a working Java 17+ runtime: "+failure.getMessage());}
                        Path fasta=null;SAMSequenceDictionary dictionary=null;
                        if(!method.equals("hard-filter")){fasta=input(required(o,"reference"),inputs);dictionary=validatedReference(fasta,inputs);}
                        var command=new ArrayList<>(base);command.add(switch(method){case"haplotype-call"->"HaplotypeCaller";case"combine-gvcfs"->"CombineGVCFs";case"genotype-gvcfs"->"GenotypeGVCFs";default->"VariantFiltration";});
                        command.addAll(List.of("--use-jdk-deflater","true","--use-jdk-inflater","true","--create-output-variant-index","true"));
                        if(fasta!=null)command.addAll(List.of("-R",fasta.toString()));
                        List<String> expectedSamples;VcfSummary before=null;
                        String emit=o.getOrDefault("emit","vcf");
                        Path result=dir.resolve(method.equals("combine-gvcfs")?"combined.g.vcf":method.equals("haplotype-call")&&emit.equals("gvcf")?"variants.g.vcf":"variants.vcf");
                        if(method.equals("haplotype-call")){
                            if(!Set.of("vcf","gvcf").contains(emit))throw new IllegalArgumentException("emit must be vcf or gvcf");
                            Path bam=input(required(o,"input"),inputs);expectedSamples=validateBam(bam,dictionary,inputs);
                            command.addAll(List.of("-I",bam.toString(),"--sample-ploidy",o.get("ploidy"),"--pair-hmm-implementation","LOGLESS_CACHING","--smith-waterman","JAVA","--native-pair-hmm-threads","1","--min-base-quality-score",Integer.toString(integer(o,"min-baseq",10,0,93))));
                            confidence(command,o);
                            if(emit.equals("gvcf")){command.addAll(List.of("-ERC","GVCF"));findings.add("INTERMEDIATE GVCF: reference blocks and <NON_REF> are not final variant calls. Genotype with the intended cohort/model before association or variant interpretation.");}
                            if(o.containsKey("region")){
                                // GATK treats a colon-bearing interval string as an invalid Windows path.
                                var intervals=new IntervalList(new SAMFileHeader(dictionary));intervals.add(region(o.get("region"),dictionary));
                                Path intervalFile=dir.resolve("requested.interval_list");intervals.write(intervalFile.toFile());
                                command.addAll(List.of("-L",intervalFile.toString()));session.put("region",o.get("region"));
                            }
                            session.put("pair_hmm","Explicit Java LOGLESS_CACHING; single compute thread");session.put("smith_waterman","Explicit JAVA; JDK compression");
                        }else if(method.equals("combine-gvcfs")){
                            Path list=input(required(o,"inputs-list"),inputs);if(Files.size(list)>1024*1024)throw new IOException("gVCF input list exceeds 1 MiB");
                            var names=new TreeSet<String>();var files=new HashSet<Path>();int n=0;
                            for(String value:Files.readAllLines(list,StandardCharsets.UTF_8)){
                                if(value.isBlank())continue;if(++n>10000)throw new IOException("At most 10000 gVCF inputs");
                                Path file=input(list.getParent().resolve(value.strip()).toString(),inputs).toRealPath();if(!files.add(file))throw new IOException("Duplicate gVCF input path");
                                VcfSummary v=scanVcf(file,dictionary,true,-1);for(String sample:v.samples)if(!names.add(sample))throw new IOException("Duplicate sample across gVCF inputs: "+sample);
                                command.addAll(List.of("-V",file.toString()));
                            }
                            if(n<1)throw new IOException("gVCF input list is empty");expectedSamples=List.copyOf(names);
                            findings.add("INTERMEDIATE COMBINED GVCF: combination is not cohort genotyping. Run GenotypeGVCFs with the intended uniform-ploidy model before downstream variant analysis.");
                        }else{
                            Path variant=input(required(o,"input"),inputs);before=scanVcf(variant,dictionary,method.equals("genotype-gvcfs"),method.equals("genotype-gvcfs")?integer(o,"ploidy",2,1,64):-1);
                            expectedSamples=before.samples;command.addAll(List.of("-V",variant.toString()));
                            if(method.equals("genotype-gvcfs")){command.addAll(List.of("--sample-ploidy",o.get("ploidy")));confidence(command,o);}
                            else addFilters(o,command,session);
                        }
                        command.addAll(List.of("-O",result.toString()));
                        if(execute(command,dir,"analysis",timeout,session)!=0)throw new IOException("Upstream GATK failed; inspect analysis.log");
                        VcfSummary after=scanVcf(result,dictionary,method.equals("combine-gvcfs")||method.equals("haplotype-call")&&emit.equals("gvcf"),Set.of("haplotype-call","genotype-gvcfs").contains(method)?integer(o,"ploidy",2,1,64):-1);
                        if(!new HashSet<>(after.samples).equals(new HashSet<>(expectedSamples)))throw new IOException("GATK changed sample identities");
                        if(!Files.isRegularFile(Path.of(result+".idx")))throw new IOException("GATK omitted output variant index");
                        if(method.equals("hard-filter")){
                            if(before==null||before.records!=after.records||!before.fingerprint.equals(after.fingerprint)||!before.samples.equals(after.samples))throw new IOException("Site-preserving filter changed variants, annotations or genotypes");
                            if(before.missingAnnotations>0)findings.add(before.missingAnnotations+" input SNP/indel records lack one or more requested annotations. The explicit missing-policy controls GATK expression evaluation; absence of a hard-filter label is not proof all metrics passed.");
                        }
                        if(after.records==0)findings.add("No output variant records; inspect coverage, reference, intervals and explicit calling/filter settings before interpreting absence.");
                        session.put("output_samples",after.samples);session.put("output_variant_index_sha256",hash(Path.of(result+".idx")));
                        table(dir.resolve("results.tsv"),List.of("metric","value"),List.of(new String[]{"output_records",Long.toString(after.records)},new String[]{"output_samples",Integer.toString(after.samples.size())},new String[]{"filtered_records",Long.toString(after.filtered)},new String[]{"variant_file",result.getFileName().toString()}));
                    }
                    for(var entry:inputs.entrySet())if(!entry.getValue().equals(hash(Path.of(entry.getKey()))))throw new IOException("Input changed during GATK workflow: "+entry.getKey());
                    try(var paths=Files.list(dir)){
                        for(Path log:paths.filter(p->p.getFileName().toString().endsWith(".log")).toList())try(var lines=Files.lines(log)){
                            lines.filter(line->line.matches("(?i).*(?:\\bWARN(?:ING)?\\b).*" )).limit(100).forEach(line->findings.add(log.getFileName()+": "+line));
                        }
                    }
                    manifest.put("analysis_status",findings.isEmpty()?"completed":"completed-with-warnings");
                    if(!findings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# GATK WORKFLOW FINDINGS\n\n"+String.join("\n",findings.stream().map(x->"- "+x).toList())+"\n\nRequired action: inspect these findings and preserved logs, sample/ploidy/reference contracts and intermediate-versus-final status before downstream analysis. Correct input/model issues and rerun to a new output directory. No workflow suitability or biological absence is inferred.\n");
                }catch(Exception failure){
                    exit[0]=failure instanceof DependencyUnavailable?3:2;manifest.put("analysis_status",exit[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",String.valueOf(failure.getMessage()));
                    try(var paths=Files.list(dir)){for(Path path:paths.toList())if(!path.getFileName().toString().endsWith(".log")){Files.createDirectories(dir.resolve("partial"));Files.move(path,dir.resolve("partial").resolve(path.getFileName()));}}
                    Files.writeString(dir.resolve("WARNINGS.md"),"# "+(exit[0]==3?"GATK UNAVAILABLE - ANALYSIS SKIPPED":"GATK WORKFLOW FAILED")+"\n\n"+failure.getMessage()+"\n\nRequired action: "+(exit[0]==3?"Where permitted run python tools/literature/install_gatk.py --allow-download, supply the pinned local JAR and Java 17+. If forbidden, skip the dependent analysis until an administrator provides the runtime.":"Correct the named reference/index/read-group/ploidy/gVCF/annotation contract or upstream error and rerun to a new output directory.")+" Inspect version.log/analysis.log where present; partial artifacts are retained under partial/. No successful results.tsv is published. Sources and existing indexes were not overwritten.\n");
                }
                manifest.put("input_sha256",inputs);session.put("findings",findings);
                Files.writeString(dir.resolve("analysis-status.json"),"{\"status\":\""+manifest.get("analysis_status")+"\"}\n");Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if(exit[0]!=0)error.println((exit[0]==3?"SKIPPED ANALYSIS":"GATK WORKFLOW FAILED")+": see "+Path.of(o.get("out"),"WARNINGS.md")+" for the cause and required action.");
            else{output.println("GATK operation completed: "+o.get("out"));if(Files.exists(Path.of(o.get("out"),"WARNINGS.md")))error.println("GATK FINDINGS: see "+Path.of(o.get("out"),"WARNINGS.md"));}return exit[0];
        }catch(Exception failure){error.println("ANALYSIS NOT STARTED: "+failure.getMessage());return 2;}
    }
    private static Path input(String text,Map<String,String> hashes)throws IOException{Path path=Path.of(text).toAbsolutePath().normalize();if(!Files.isRegularFile(path))throw new IOException("Local input does not exist: "+path);hashes.put(path.toString(),hash(path));return path;}
    private static SAMSequenceDictionary actualDictionary(Path fasta)throws IOException{
        var result=new SAMSequenceDictionary();for(var entry:FastaIndexes.build(fasta)){if(entry.getSize()>Integer.MAX_VALUE)throw new IOException("Reference contig exceeds BAM/VCF coordinate limit");result.addSequence(new SAMSequenceRecord(entry.getContig(),(int)entry.getSize()));}return result;
    }
    private static SAMSequenceDictionary validatedReference(Path fasta,Map<String,String> hashes)throws IOException{
        Path fai=input(fasta+".fai",hashes),dict=input(ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(fasta).toString(),hashes);FastaIndexes.validate(fasta,fai);var expected=actualDictionary(fasta);
        try(var lines=new BufferedLineReader(Files.newInputStream(dict))){var supplied=new SAMTextHeaderCodec().decode(lines,dict.toString()).getSequenceDictionary();if(supplied.size()!=expected.size())throw new IOException("Reference dictionary size differs from actual FASTA");dictionary(supplied,expected);}
        return expected;
    }
    private static void dictionary(SAMSequenceDictionary supplied,SAMSequenceDictionary reference)throws IOException{
        if(supplied==null||supplied.isEmpty())throw new IOException("Input omits sequence dictionary");int previous=-1;
        for(var sequence:supplied.getSequences()){var match=reference.getSequence(sequence.getSequenceName());if(match==null||sequence.getSequenceLength()!=match.getSequenceLength()||match.getSequenceIndex()<=previous)throw new IOException("Input/reference contig names, lengths or order differ");previous=match.getSequenceIndex();}
    }
    private static List<String> validateBam(Path bam,SAMSequenceDictionary reference,Map<String,String> hashes)throws Exception{
        if(!bam.toString().toLowerCase(Locale.ROOT).endsWith(".bam"))throw new IOException("Haplotype calling requires coordinate-sorted indexed local BAM");
        var factory=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).enable(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX);var samples=new TreeSet<String>();
        try(var reader=factory.open(bam);var records=reader.iterator()){
            var header=reader.getFileHeader();dictionary(header.getSequenceDictionary(),reference);if(header.getSortOrder()!=SAMFileHeader.SortOrder.coordinate||!reader.hasIndex())throw new IOException("BAM requires coordinate sort and a matching adjacent BAI/CSI index");
            for(var group:header.getReadGroups()){if(group.getSample()==null||group.getSample().isBlank())throw new IOException("Every BAM read group requires an explicit sample");samples.add(group.getSample());}
            if(samples.size()!=1)throw new IOException("HaplotypeCaller requires exactly one sample across explicit read groups");
            SAMRecord previous=null;var order=new SAMRecordCoordinateComparator();
            while(records.hasNext()){var record=records.next();if(record.getReadGroup()==null||previous!=null&&order.fileOrderCompare(previous,record)>0)throw new IOException("BAM is unsorted or a record omits a valid read group");previous=record;}
        }
        Path index=SamFiles.findIndex(bam);if(index==null)throw new IOException("Cannot locate BAM index");input(index.toString(),hashes);return List.copyOf(samples);
    }
    private record VcfSummary(long records,long filtered,List<String> samples,BigInteger fingerprint,long missingAnnotations){}
    private static VcfSummary scanVcf(Path path,SAMSequenceDictionary reference,boolean requireGvcf,int ploidy)throws Exception{
        if(!path.toString().toLowerCase(Locale.ROOT).matches(".+\\.vcf(?:\\.gz)?"))throw new IOException("Require local VCF/VCF.gz input");
        var raw=Files.newInputStream(path);java.io.InputStream stream;
        try{stream=path.toString().toLowerCase(Locale.ROOT).endsWith(".gz")?new GZIPInputStream(raw):raw;}catch(Exception failure){raw.close();throw failure;}
        try(var lines=new LineIteratorImpl(new SynchronousLineReader(stream))){
            var codec=new VCFCodec();var header=(VCFHeader)codec.readActualHeader(lines);if(reference!=null)dictionary(header.getSequenceDictionary(),reference);
            var encoder=new VCFEncoder(header,false,false);long count=0,filtered=0,missing=0;boolean nonRef=false;BigInteger fingerprint=BigInteger.ZERO;var digest=MessageDigest.getInstance("SHA-256");
            int previousContig=-1,previousPosition=-1;var contigs=header.getSequenceDictionary();
            while(lines.hasNext()){
                VariantContext variant=codec.decode(lines.next());if(variant==null||variant.getStart()<1)throw new IOException("Invalid VCF record");
                if(!contigs.isEmpty()){int rank=contigs.getSequenceIndex(variant.getContig());if(rank<0||rank<previousContig||rank==previousContig&&variant.getStart()<previousPosition)throw new IOException("VCF order/contig metadata invalid");previousContig=rank;previousPosition=variant.getStart();}
                if(reference!=null){var sequence=reference.getSequence(variant.getContig());if(sequence==null||variant.getEnd()>sequence.getSequenceLength())throw new IOException("VCF record is outside exact reference dictionary");}
                for(var genotype:variant.getGenotypes())if(ploidy>0&&genotype.getPloidy()!=ploidy)throw new IOException("gVCF/output genotype ploidy differs from the explicit uniform model");
                nonRef|=variant.getAlleles().stream().anyMatch(a->a.getDisplayString().equals("<NON_REF>"));
                List<String> annotations=variant.isSNP()?List.of("QD","FS","MQ","MQRankSum","ReadPosRankSum"):variant.isIndel()?List.of("QD","FS","ReadPosRankSum"):List.of();
                if(annotations.stream().anyMatch(a->!variant.hasAttribute(a)))missing++;
                String canonical=encoder.encode(new VariantContextBuilder(variant).passFilters().make());fingerprint=fingerprint.add(new BigInteger(1,digest.digest(canonical.getBytes(StandardCharsets.UTF_8))));
                count++;if(variant.isFiltered())filtered++;
            }
            if(requireGvcf&&(!nonRef||header.getGenotypeSamples().isEmpty()))throw new IOException("Expected actual gVCF reference blocks/<NON_REF> and sample identities, not an ordinary variant-only VCF");
            return new VcfSummary(count,filtered,List.copyOf(header.getGenotypeSamples()),fingerprint,missing);
        }
    }
    private static void confidence(List<String> command,Map<String,String> o){double value=number(o,"call-confidence",30);if(value<0||value>10000)throw new IllegalArgumentException("call-confidence must be in [0,10000]");command.addAll(List.of("--standard-min-confidence-threshold-for-calling",Double.toString(value)));}
    private static Interval region(String value,SAMSequenceDictionary dictionary){var match=java.util.regex.Pattern.compile("([^:\\s]+):([0-9]+)-([0-9]+)").matcher(value);if(!match.matches())throw new IllegalArgumentException("region must be CHR:START-END, one-based inclusive");int start=Integer.parseInt(match.group(2)),end=Integer.parseInt(match.group(3));var sequence=dictionary.getSequence(match.group(1));if(start<1||end<start||sequence==null||end>sequence.getSequenceLength())throw new IllegalArgumentException("Region outside exact reference dictionary");return new Interval(match.group(1),start,end);}
    private static void addFilters(Map<String,String> o,List<String> command,Map<String,Object> session){
        var values=new LinkedHashMap<String,Double>();for(var entry:Map.of("snp-qd-min",2.,"snp-fs-max",60.,"snp-mq-min",40.,"snp-mq-rank-min",-12.5,"snp-read-pos-min",-8.,"indel-qd-min",2.,"indel-fs-max",200.,"indel-read-pos-min",-20.).entrySet())values.put(entry.getKey(),number(o,entry.getKey(),entry.getValue()));
        String snp="vc.isSNP() && (QD < "+values.get("snp-qd-min")+" || FS > "+values.get("snp-fs-max")+" || MQ < "+values.get("snp-mq-min")+" || MQRankSum < "+values.get("snp-mq-rank-min")+" || ReadPosRankSum < "+values.get("snp-read-pos-min")+")";
        String indel="vc.isIndel() && (QD < "+values.get("indel-qd-min")+" || FS > "+values.get("indel-fs-max")+" || ReadPosRankSum < "+values.get("indel-read-pos-min")+")";
        command.addAll(List.of("--filter-name","JL_SNP_HARD_FILTER","--filter-expression",snp,"--filter-name","JL_INDEL_HARD_FILTER","--filter-expression",indel,"--invalidate-previous-filters","false","--missing-values-evaluate-as-failing",Boolean.toString(o.get("missing-policy").equals("fail"))));session.put("filter_expressions",List.of(snp,indel));session.put("missing_policy",o.get("missing-policy"));
    }
    private static int execute(List<String> command,Path dir,String name,int timeout,Map<String,Object> session)throws Exception{
        session.put(name+"_command",List.copyOf(command));var builder=new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve(name+".log").toFile());builder.environment().put("LC_ALL","C");Process process=builder.start();
        try{if(!process.waitFor(timeout,TimeUnit.SECONDS)){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();throw new IOException("GATK timed out; inspect "+name+".log");}session.put(name+"_exit_code",process.exitValue());return process.exitValue();}catch(InterruptedException failure){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();Thread.currentThread().interrupt();throw failure;}
    }
    private static final class DependencyUnavailable extends IOException{private static final long serialVersionUID=1L;DependencyUnavailable(String message){super(message);}}
    static String help(){return"""
        gatk-tools --method reference|haplotype-call|combine-gvcfs|genotype-gvcfs|hard-filter --out NEW_DIR
        reference --reference LOCAL.fa: native Java .fai/.dict generation inside the output bundle;
          place those verified files beside the exact source to use upstream callers. Sources/old indexes preserved.
        Other methods: --gatk OFFICIAL_4.7.0.0_LOCAL.jar [--java JAVA17_PLUS --memory-mb 2048 --timeout 3600].
        haplotype-call --input COORDINATE_INDEXED.bam --reference LOCAL.fa --ploidy 1..64
          [--emit vcf|gvcf --region CHR:START-END --min-baseq 10 --call-confidence 30].
          Exactly one explicit sample/read groups. Java LOGLESS_CACHING PairHMM and JAVA Smith-Waterman,
          JDK compression; no automatic AVX, Spark or Python. Other upstream defaults retained in logs.
        combine-gvcfs --inputs-list FILE --reference LOCAL.fa: one local gVCF path/line, relative to list;
          1..10000 inputs, disjoint sample IDs; combined.g.vcf remains an intermediate.
        genotype-gvcfs --input INPUT.g.vcf --reference LOCAL.fa --ploidy 1..64 [--call-confidence 30].
          Uniform genotype ploidy must match the input. Intermediate gVCFs are not final variant calls.
        hard-filter --input LOCAL.vcf[.gz] --missing-policy pass|fail
          Explicit SNP/indel annotation expressions, preserves sites/alleles/genotypes/prior filters.
          Defaults: SNP QD<2,FS>60,MQ<40,MQRankSum<-12.5,ReadPosRankSum<-8;
          indel QD<2,FS>200,ReadPosRankSum<-20. Typed threshold options are listed in the guide.
          Missing-policy controls GATK expression evaluation; missing annotations warn. No read-depth,
          biallelic, sample, genotype-level or population filter is inferred; thresholds are study-specific.
        Reference .fai geometry and .dict names/lengths/order must match actual FASTA. No remote data/CRAM.
        Local VCF plus .idx emitted; source hashes, commands, versions, CPU kernels and findings retained.
        Missing runtime: skip 3. Input/execution failures: 2, quarantined partials, no results.tsv.
        Warnings and intermediate/no-call findings require action in WARNINGS.md and console.
        Setup where permitted: python tools/literature/install_gatk.py --allow-download.
        No legacy indel realignment, Mutect2/somatic, BQSR/VQSR or complete old-paper pipeline parity.
        """;}
}
