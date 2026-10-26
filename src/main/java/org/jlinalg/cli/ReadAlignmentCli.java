/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.FastaIndexes;
import static org.jlinalg.cli.FollowupSupport.*;

/** Audited short-read DNA alignment with real, version-pinned upstream programs. */
final class ReadAlignmentCli {
    private ReadAlignmentCli() { }
    private static final Set<String> COMMON=Set.of("method","out","timeout");
    private static final Set<String> RUNTIME=Set.of("bowtie-home","bwa");
    private static final Set<String> INDEX=Set.of("reference","index-mode","algorithm");
    private static final Set<String> READS=Set.of("index","reads","reads2","sample","read-group","library","threads");
    private static final Set<String> BOWTIE=Set.of("mode","preset","seed","min-insert","max-insert","dovetail","mixed","discordant");
    private static final Set<String> MEM=Set.of("seed-length","minimum-score","mark-shorter-split-secondary");
    private static final Set<String> BACKTRACK=Set.of("seed-length","max-differences");

    static int run(String[] arguments,PrintStream output,PrintStream error) {
        if(Arrays.asList(arguments).contains("--help")){output.println(help());return 0;}
        try {
            var allowed=new HashSet<>(COMMON);for(var keys:List.of(RUNTIME,INDEX,READS,BOWTIE,MEM,BACKTRACK))allowed.addAll(keys);
            Map<String,String> o=options(arguments,allowed);String method=required(o,"method");
            boolean bowtie=method.startsWith("bowtie2"),indexing=method.endsWith("-index");
            if(!Set.of("bowtie2-index","bowtie2","bwa-index","bwa-mem","bwa-backtrack").contains(method))throw new IllegalArgumentException("Unknown read-align method");
            var applicable=new HashSet<>(COMMON);applicable.add(bowtie?"bowtie-home":"bwa");
            if(indexing){applicable.add("reference");applicable.add(bowtie?"index-mode":"algorithm");}
            else {applicable.addAll(READS);applicable.addAll(bowtie?BOWTIE:method.equals("bwa-mem")?MEM:BACKTRACK);}
            for(String key:o.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            int timeout=integer(o,"timeout",3600,1,86400);required(o,bowtie?"bowtie-home":"bwa");
            if(indexing)required(o,"reference");else{required(o,"index");required(o,"reads");required(o,"sample");}
            int[] exit={0};
            externalTransaction(o,"read-align-"+method,(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();session.put("method",method);
                session.put("scope","Short-read unspliced DNA, canonical A/C/G/T/N FASTQ Phred+33. Explicit engine/settings, local sealed index bundle; no inferred assembly, trimming, uniqueness or duplicate filtering");
                try {
                    if(indexing)buildIndex(o,bowtie,directory,manifest,session,timeout);
                    else align(o,method,bowtie,directory,manifest,session,timeout);
                    var warnings=new ArrayList<String>();
                    try(var paths=Files.list(directory)){
                        for(Path p:paths.filter(p->p.getFileName().toString().endsWith(".log")).toList())try(var lines=Files.lines(p)){
                            lines.filter(line->line.matches("(?i).*(?:\\bwarning\\b|\\[W::).*" )).limit(100).forEach(line->warnings.add(p.getFileName()+": "+line));
                        }
                    }
                    if(Long.valueOf(0).equals(session.get("mapped_primary_records")))warnings.add("No primary records aligned; inspect reference choice, read preprocessing and mode before downstream analysis.");
                    String state=warnings.isEmpty()?"completed":"completed-with-warnings";manifest.put("analysis_status",state);
                    if(!warnings.isEmpty()){
                        session.put("warnings_first_100_per_log",warnings);
                        Files.writeString(directory.resolve("WARNINGS.md"),"# ALIGNMENT WARNINGS\n\n"+String.join("\n",warnings)+"\n\nRequired action: inspect these logs, mapping counts and the explicit reference/settings before downstream analysis. Low or absent alignment is not evidence that a biological sequence is absent.\n");
                    }
                }catch(Exception failure){
                    exit[0]=failure instanceof DependencyUnavailable?3:2;manifest.put("analysis_status",exit[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",String.valueOf(failure.getMessage()));
                    quarantine(directory);
                    Files.writeString(directory.resolve("WARNINGS.md"),"# "+(exit[0]==3?"ANALYSIS SKIPPED - ALIGNER UNAVAILABLE":"ALIGNMENT FAILED")+"\n\n"+failure.getMessage()
                        +"\n\nRequired action: "+(exit[0]==3?"Where permitted run python tools/literature/install_aligner_dependencies.py --profile "+(bowtie?"bowtie2 --allow-download":"bwa --allow-download --allow-source-build")+" and supply the resulting runtime. If installation/building is forbidden, skip the dependent analysis until an administrator supplies the pinned program. Existing unmanaged runtimes are preserved.":"Inspect native logs and input/index contracts, correct the named cause and rerun to a new output directory.")
                        +" Partial artifacts are retained under partial/. No successful results.tsv is published.\n");
                }
                Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\""+manifest.get("analysis_status")+"\"}\n");
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if(exit[0]!=0)error.println((exit[0]==3?"SKIPPED ANALYSIS":"ALIGNMENT FAILED")+": see "+Path.of(o.get("out"),"WARNINGS.md")+" for the cause and required action.");
            else {output.println("Read alignment operation completed: "+o.get("out"));if(Files.exists(Path.of(o.get("out"),"WARNINGS.md")))error.println("ALIGNMENT WARNINGS: see "+Path.of(o.get("out"),"WARNINGS.md"));}
            return exit[0];
        }catch(Exception failure){error.println("jlinalg: "+failure.getMessage());return 2;}
    }

    private static void buildIndex(Map<String,String> o,boolean bowtie,Path dir,Map<String,Object> manifest,Map<String,Object> session,int timeout)throws Exception{
        Path reference=file(required(o,"reference"));String before=hash(reference);manifest.put("reference_sha256",before);
        var fasta=FastaIndexes.build(reference);long bases=0;var sequences=new ArrayList<Map<String,Object>>();
        for(var entry:fasta){
            if(entry.getSize()>Integer.MAX_VALUE)throw new IOException("A reference sequence exceeds the SAM/Java coordinate limit");
            bases=Math.addExact(bases,entry.getSize());sequences.add(Map.of("name",entry.getContig(),"length",entry.getSize()));
        }
        String mode="bwa";List<String> command;
        if(bowtie){
            mode=o.getOrDefault("index-mode","auto");if(!Set.of("auto","small","large").contains(mode))throw new IllegalArgumentException("index-mode must be auto, small or large");
            if(mode.equals("auto"))mode=bases>=4_000_000_000L?"large":"small";
            if(mode.equals("small")&&bases>=4_000_000_000L)throw new IllegalArgumentException("Small Bowtie index is limited to references below 4 billion bases; use large");
            String binary=bowtieBinary(o,"build",mode);probe(binary,true,dir,session);
            command=List.of(binary,"--wrapper","jlinalg-typed-1","-f",reference.toString(),dir.resolve("reference").toString());
        }else{
            String binary=bwaBinary(o);probe(binary,false,dir,session);String algorithm=o.getOrDefault("algorithm","auto");
            if(!Set.of("auto","is","bwtsw").contains(algorithm))throw new IllegalArgumentException("algorithm must be auto, is or bwtsw");
            if(algorithm.equals("is")&&bases>=Integer.MAX_VALUE)throw new IllegalArgumentException("BWA IS indexing is limited to references below 2 GiB; use auto or bwtsw");
            var c=new ArrayList<>(List.of(binary,"index","-p",dir.resolve("reference").toString()));if(!algorithm.equals("auto"))c.addAll(List.of("-a",algorithm));c.add(reference.toString());command=c;
        }
        checked(command,dir,"index",null,timeout,session);
        var hashes=new LinkedHashMap<String,String>();
        for(String name:indexFiles(bowtie,mode)){
            Path path=dir.resolve(name);if(!Files.isRegularFile(path)||Files.size(path)==0)throw new IOException("Aligner omitted index component "+name);hashes.put(name,hash(path));
        }
        if(!before.equals(hash(reference)))throw new IOException("Reference changed during indexing");
        var index=new LinkedHashMap<String,Object>();index.put("schema_version",1);index.put("engine",bowtie?"bowtie2":"bwa");index.put("version",bowtie?"2.5.5":"0.7.19-r1273");
        index.put("mode",mode);index.put("prefix","reference");index.put("reference_sha256",before);index.put("reference_sequences",sequences);index.put("index_sha256",hashes);
        Files.writeString(dir.resolve("aligner-index.json"),json(index)+"\n");fasta.write(dir.resolve("reference.fai"));
        table(dir.resolve("results.tsv"),List.of("metric","value"),List.of(new String[]{"reference_sequences",Integer.toString(sequences.size())},new String[]{"reference_bases",Long.toString(bases)},new String[]{"index_mode",mode}));
        session.put("index_mode",mode);session.put("reference_sequences",sequences.size());session.put("reference_bases",bases);
    }

    private static void align(Map<String,String> o,String method,boolean bowtie,Path dir,Map<String,Object> manifest,Map<String,Object> session,int timeout)throws Exception{
        Path indexDir=Path.of(required(o,"index")).toAbsolutePath().normalize(),metadata=file(indexDir.resolve("aligner-index.json").toString());
        if(Files.size(metadata)>64*1024*1024)throw new IOException("Index manifest exceeds 64 MiB");
        Object parsed=SimpleJson.parse(Files.readString(metadata));if(!(parsed instanceof Map<?,?> index))throw new IOException("Invalid aligner index manifest");
        if(!(index.get("schema_version") instanceof Number schema)||schema.intValue()!=1||!Objects.equals(index.get("engine"),bowtie?"bowtie2":"bwa")||!Objects.equals(index.get("version"),bowtie?"2.5.5":"0.7.19-r1273")||!Objects.equals(index.get("prefix"),"reference"))throw new IOException("Index engine/version/schema differs from this adapter");
        String mode=String.valueOf(index.get("mode"));if(!(bowtie?Set.of("small","large"):Set.of("bwa")).contains(mode))throw new IOException("Invalid index family");
        if(!(index.get("reference_sha256") instanceof String referenceHash)||!referenceHash.matches("[0-9a-f]{64}"))throw new IOException("Index omits a valid reference SHA-256");
        verifyIndex(indexDir,index,bowtie,mode);String indexHash=hash(metadata);manifest.put("aligner_index_manifest_sha256",indexHash);manifest.put("reference_sha256",index.get("reference_sha256"));
        Path reads=file(required(o,"reads")),reads2=o.containsKey("reads2")?file(o.get("reads2")):null;
        var inputHashes=new LinkedHashMap<String,String>();inputHashes.put(reads.toString(),hash(reads));if(reads2!=null)inputHashes.put(reads2.toString(),hash(reads2));manifest.put("reads_sha256",inputHashes);
        var inspection=FastqIO.inspect(reads,reads2);if(inspection.read1().reads()==0)throw new IOException("Input FASTQ is empty; inspect upstream trimming/QC");
        String sample=identifier(required(o,"sample")),group=identifier(o.getOrDefault("read-group","JL1"));String library=o.containsKey("library")?identifier(o.get("library")):null;
        int threads=integer(o,"threads",1,1,64);String binary=bowtie?bowtieBinary(o,"align",mode):bwaBinary(o);probe(binary,bowtie,dir,session);
        String prefix=indexDir.resolve("reference").toString();Path sam=dir.resolve("alignment.sam");
        if(bowtie){
            String alignmentMode=o.getOrDefault("mode","end-to-end"),preset=o.getOrDefault("preset","sensitive");
            if(!Set.of("end-to-end","local").contains(alignmentMode)||!Set.of("very-fast","fast","sensitive","very-sensitive").contains(preset))throw new IllegalArgumentException("Invalid Bowtie mode/preset");
            int minimum=integer(o,"min-insert",0,0,1000000),maximum=integer(o,"max-insert",500,0,1000000);if(maximum<minimum)throw new IllegalArgumentException("max-insert must be >= min-insert");
            if(reads2==null&&o.keySet().stream().anyMatch(Set.of("min-insert","max-insert","dovetail","mixed","discordant")::contains))throw new IllegalArgumentException("Paired alignment options require --reads2");
            var command=new ArrayList<>(List.of(binary,"--wrapper","jlinalg-typed-1","-x",prefix,"-q","--phred33","--reorder","-p",Integer.toString(threads),"--seed",Integer.toString(integer(o,"seed",1,0,Integer.MAX_VALUE)),"--"+alignmentMode,"--"+preset+(alignmentMode.equals("local")?"-local":""),"--rg-id",group,"--rg","SM:"+sample,"--rg","PL:ILLUMINA"));
            if(library!=null)command.addAll(List.of("--rg","LB:"+library));
            if(reads2==null)command.addAll(List.of("-U",reads.toString()));else{
                command.addAll(List.of("-1",reads.toString(),"-2",reads2.toString(),"-I",Integer.toString(minimum),"-X",Integer.toString(maximum)));
                if(bool(o,"dovetail",false))command.add("--dovetail");if(!bool(o,"mixed",true))command.add("--no-mixed");if(!bool(o,"discordant",true))command.add("--no-discordant");
            }
            command.addAll(List.of("-S",sam.toString()));checked(command,dir,"alignment",null,timeout,session);
            session.put("alignment_mode",alignmentMode);session.put("sensitivity_preset",preset);session.put("seed",integer(o,"seed",1,0,Integer.MAX_VALUE));
        }else{
            String rg="@RG\\tID:"+group+"\\tSM:"+sample+"\\tPL:ILLUMINA"+(library==null?"":"\\tLB:"+library);
            if(method.equals("bwa-mem")){
                var command=new ArrayList<>(List.of(binary,"mem","-t",Integer.toString(threads),"-k",Integer.toString(integer(o,"seed-length",19,1,10000000)),"-T",Integer.toString(integer(o,"minimum-score",30,0,10000000)),"-R",rg));
                if(bool(o,"mark-shorter-split-secondary",false))command.add("-M");command.add(prefix);command.add(reads.toString());if(reads2!=null)command.add(reads2.toString());checked(command,dir,"alignment",sam,timeout,session);
            }else{
                Path sai1=dir.resolve("reads1.sai"),sai2=dir.resolve("reads2.sai");
                var base=new ArrayList<>(List.of(binary,"aln","-t",Integer.toString(threads),"-l",Integer.toString(integer(o,"seed-length",32,1,10000000))));
                if(o.containsKey("max-differences"))base.addAll(List.of("-n",Integer.toString(integer(o,"max-differences",0,0,100000))));
                var first=new ArrayList<>(base);first.add(prefix);first.add(reads.toString());checked(first,dir,"aln1",sai1,timeout,session);
                var command=new ArrayList<>(List.of(binary,reads2==null?"samse":"sampe","-r",rg,prefix,sai1.toString()));
                if(reads2!=null){var second=new ArrayList<>(base);second.add(prefix);second.add(reads2.toString());checked(second,dir,"aln2",sai2,timeout,session);command.add(sai2.toString());}
                command.add(reads.toString());if(reads2!=null)command.add(reads2.toString());checked(command,dir,"alignment",sam,timeout,session);
            }
            session.put("seed_length",integer(o,"seed-length",method.equals("bwa-mem")?19:32,1,10000000));
        }
        inspectOutput(sam,reads,reads2,index,group,sample,session);
        for(var entry:inputHashes.entrySet())if(!entry.getValue().equals(hash(Path.of(entry.getKey()))))throw new IOException("FASTQ input changed during alignment");
        if(!indexHash.equals(hash(metadata)))throw new IOException("Index manifest changed during alignment");verifyIndex(indexDir,index,bowtie,mode);
        var rows=new ArrayList<String[]>();for(String key:List.of("primary_records","mapped_primary_records","proper_pair_records","secondary_records","supplementary_records"))rows.add(new String[]{key,session.get(key).toString()});
        rows.add(new String[]{"input_reads_or_pairs",Long.toString(inspection.read1().reads())});table(dir.resolve("results.tsv"),List.of("metric","value"),rows);
    }

    private static void inspectOutput(Path sam,Path reads,Path reads2,Map<?,?> index,String group,String sample,Map<String,Object> session)throws Exception{
        if(!(index.get("reference_sequences") instanceof List<?> sequences)||sequences.isEmpty())throw new IOException("Index omits reference dictionary");
        long primary=0,mapped=0,proper=0,secondary=0,supplementary=0;
        try(var input1=new FastqIO.Reader(reads);var input2=reads2==null?null:new FastqIO.Reader(reads2);
            var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(sam);var records=reader.iterator()){
            var dictionary=reader.getFileHeader().getSequenceDictionary();if(dictionary.size()!=sequences.size())throw new IOException("Alignment changed reference dictionary size");
            for(int i=0;i<sequences.size();i++){
                if(!(sequences.get(i) instanceof Map<?,?> expected)||!(expected.get("length") instanceof Number n)||!dictionary.getSequence(i).getSequenceName().equals(expected.get("name"))||dictionary.getSequence(i).getSequenceLength()!=n.longValue())throw new IOException("Alignment changed reference names/lengths/order");
            }
            var rg=reader.getFileHeader().getReadGroup(group);if(rg==null||!sample.equals(rg.getSample()))throw new IOException("Aligner changed explicit read group/sample");
            FastqIO.Read first=null,second=null;boolean nextPair=true,expectSecond=false;
            while(records.hasNext()){
                SAMRecord record=records.next();
                if(record.isSecondaryAlignment()){secondary++;continue;}if(record.getSupplementaryAlignmentFlag()){supplementary++;continue;}
                if(nextPair){first=input1.next();second=input2==null?null:input2.next();if(first==null||(input2!=null&&second==null))throw new IOException("Aligner emitted extra primary reads");nextPair=false;}
                FastqIO.Read expected=expectSecond?second:first;
                if(!record.getReadName().replaceFirst("/[12]$","").equals(expected.pairId())||record.getReadPairedFlag()!=(input2!=null)||!group.equals(record.getStringAttribute("RG")))throw new IOException("Aligner changed read identities/order, pairing or read group");
                if(input2!=null&&(record.getFirstOfPairFlag()==expectSecond||record.getSecondOfPairFlag()!=expectSecond))throw new IOException("Aligner changed mate flags");
                String bases=expected.sequence().toUpperCase(Locale.ROOT);if(!bases.matches("[ACGTN]+"))throw new IOException("Alignment contract requires canonical DNA A/C/G/T/N bases");
                String qualities=expected.quality();if(record.getReadNegativeStrandFlag()){bases=reverseComplement(bases);qualities=new StringBuilder(qualities).reverse().toString();}
                if(!bases.equals(record.getReadString())||!qualities.equals(record.getBaseQualityString()))throw new IOException("Aligner changed primary sequence/qualities beyond strand orientation");
                primary++;if(!record.getReadUnmappedFlag())mapped++;if(record.getReadPairedFlag()&&record.getProperPairFlag())proper++;
                if(input2==null||expectSecond){nextPair=true;expectSecond=false;}else expectSecond=true;
            }
            if(expectSecond||!nextPair||input1.next()!=null||(input2!=null&&input2.next()!=null))throw new IOException("Aligner omitted primary reads/mates");
        }
        session.put("primary_records",primary);session.put("mapped_primary_records",mapped);session.put("proper_pair_records",proper);session.put("secondary_records",secondary);session.put("supplementary_records",supplementary);
    }
    private static String reverseComplement(String sequence){var result=new StringBuilder(sequence.length());for(int i=sequence.length()-1;i>=0;i--)result.append(switch(sequence.charAt(i)){case'A'->'T';case'T'->'A';case'C'->'G';case'G'->'C';default->'N';});return result.toString();}
    private static List<String> indexFiles(boolean bowtie,String mode){
        String ext=mode.equals("large")?"bt2l":"bt2";return bowtie?List.of("reference.1."+ext,"reference.2."+ext,"reference.3."+ext,"reference.4."+ext,"reference.rev.1."+ext,"reference.rev.2."+ext):List.of("reference.amb","reference.ann","reference.bwt","reference.pac","reference.sa");
    }
    private static void verifyIndex(Path directory,Map<?,?> index,boolean bowtie,String mode)throws IOException{
        if(!(index.get("index_sha256") instanceof Map<?,?> hashes)||!hashes.keySet().equals(new HashSet<>(indexFiles(bowtie,mode))))throw new IOException("Index has wrong/mixed component family");
        for(String name:indexFiles(bowtie,mode))if(!Objects.equals(hashes.get(name),hash(file(directory.resolve(name).toString()))))throw new IOException("Index component changed or is corrupt: "+name);
    }
    private static String identifier(String value){if(!value.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("Sample/read-group/library IDs must use 1..128 letters, digits, underscore, dot or hyphen");return value;}
    private static Path file(String value)throws IOException{Path path=Path.of(value).toAbsolutePath().normalize();if(!Files.isRegularFile(path))throw new IOException("Local input does not exist: "+path);return path;}
    private static String bowtieBinary(Map<String,String> o,String program,String mode)throws IOException{return runtime(Path.of(required(o,"bowtie-home")).resolve("bowtie2-"+program+"-"+(mode.equals("large")?"l":"s")+(windows()?".exe":"")));}
    private static String bwaBinary(Map<String,String> o)throws IOException{return runtime(Path.of(required(o,"bwa")));}
    private static String runtime(Path path)throws IOException{path=path.toAbsolutePath().normalize();if(!Files.isRegularFile(path))throw new DependencyUnavailable("Aligner executable is unavailable: "+path);return path.toString();}
    private static boolean windows(){return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");}
    private static void probe(String binary,boolean bowtie,Path dir,Map<String,Object> session)throws Exception{
        session.put("executable_sha256",hash(Path.of(binary)));var dlls=new TreeMap<String,String>();
        try(var files=Files.list(Path.of(binary).getParent())){for(Path p:files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".dll")).toList())dlls.put(p.getFileName().toString(),hash(p));}session.put("runtime_dll_sha256",dlls);
        try{
            int code=execute(bowtie?List.of(binary,"--version"):List.of(binary),dir,"version",null,30,session);
            String text=Files.readString(dir.resolve("version.log")).replace("\r\n","\n");
            if(bowtie?(code!=0||!text.contains(" version 2.5.5\n")):(code!=1||!text.contains("Version: 0.7.19-r1273\n")))throw new IOException("Runtime version differs from the pinned adapter");
        }catch(IOException failure){throw new DependencyUnavailable(failure.getMessage());}
    }
    private static void checked(List<String> c,Path dir,String log,Path stdout,int timeout,Map<String,Object> session)throws Exception{if(execute(c,dir,log,stdout,timeout,session)!=0)throw new IOException("Upstream aligner failed; inspect "+log+".log");}
    private static int execute(List<String> c,Path dir,String log,Path stdout,int timeout,Map<String,Object> session)throws Exception{
        session.put(log+"_command",List.copyOf(c));var builder=new ProcessBuilder(c).directory(dir.toFile()).redirectError(dir.resolve(log+".log").toFile());
        if(stdout==null)builder.redirectErrorStream(true).redirectOutput(dir.resolve(log+".log").toFile());else builder.redirectOutput(stdout.toFile());builder.environment().put("LC_ALL","C");builder.environment().remove("BOWTIE2_INDEXES");
        Process process=builder.start();try{if(!process.waitFor(timeout,TimeUnit.SECONDS)){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();throw new IOException("Aligner timed out; inspect "+log+".log");}session.put(log+"_exit_code",process.exitValue());return process.exitValue();}
        catch(InterruptedException failure){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();Thread.currentThread().interrupt();throw failure;}
    }
    private static void quarantine(Path dir)throws IOException{try(var files=Files.list(dir)){for(Path p:files.toList()){String name=p.getFileName().toString();if(name.endsWith(".log")||name.equals("partial"))continue;Files.createDirectories(dir.resolve("partial"));Files.move(p,dir.resolve("partial").resolve(name));}}}
    private static final class DependencyUnavailable extends IOException{private static final long serialVersionUID=1L;DependencyUnavailable(String s){super(s);}}
    private static String json(Object value){
        if(value==null)return"null";if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map){var entries=new ArrayList<String>();for(var e:map.entrySet())entries.add(json(e.getKey().toString())+":"+json(e.getValue()));return"{"+String.join(",",entries)+"}";}
        if(value instanceof List<?> list)return"["+String.join(",",list.stream().map(ReadAlignmentCli::json).toList())+"]";
        var b=new StringBuilder("\"");for(char c:value.toString().toCharArray())switch(c){case'"'->b.append("\\\"");case'\\'->b.append("\\\\");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();
    }
    static String help(){return"""
        read-align --method bowtie2-index|bowtie2|bwa-index|bwa-mem|bwa-backtrack --out NEW_DIR
        Runtime: --bowtie-home DIRECTORY (direct 2.5.5 align/build binaries) or --bwa EXE (0.7.19-r1273).
        Index methods: --reference UNCOMPRESSED.fa; native FASTA validation, reference hash/dictionary
          and every index-file hash retained in aligner-index.json. Bowtie [--index-mode auto|small|large],
          auto selects large at >=4 billion bases. BWA [--algorithm auto|is|bwtsw].
        Align methods: --index INDEX_BUNDLE --reads R1.fastq[.gz] [--reads2 R2.fastq[.gz]]
          --sample SAMPLE [--read-group JL1 --library LIBRARY --threads 1 --timeout 3600].
          IDs: 1..128 letters/digits/underscore/dot/hyphen. Canonical DNA A/C/G/T/N, Phred+33,
          strict four-line FASTQ; pair IDs/order must match after terminal /1 or /2 removal.
        Bowtie: --mode end-to-end|local --preset very-fast|fast|sensitive|very-sensitive --seed 1;
          paired: --min-insert 0 --max-insert 500 --dovetail false --mixed true --discordant true.
          Direct binaries, explicit small/large family, gzip and --reorder; no Perl/Python wrapper needed.
        BWA-MEM: --seed-length 19 --minimum-score 30 --mark-shorter-split-secondary false.
        BWA-backtrack: aln then samse/sampe; --seed-length 32 [--max-differences INTEGER].
          Select the actual algorithm explicitly; old papers that only name BWA do not identify it.
        Output alignment.sam retains unmapped records; exactly one primary per input mate is checked,
          including strand-adjusted bases/qualities, sample/RG and reference dictionary. No automatic
          trimming, uniqueness, duplicate filtering, splice alignment, ALT-aware postprocessing or BAM sort.
        Full index/read hashes are checked before/after use. Logs, effective options and DLL hashes retained.
        Missing/wrong runtime: skip 3; other failure: 2, actionable WARNINGS.md, quarantined partials.
        Setup where permitted: python tools/literature/install_aligner_dependencies.py --profile bowtie2
          --allow-download; BWA also requires --profile bwa --allow-source-build.
        """;}
}
