/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.KallistoTables;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed original kallisto index/quant with sealed reference and native audits. */
final class KallistoToolsCli {
    private KallistoToolsCli() { }
    private static final Set<String> COMMON=Set.of("method","home","out","timeout","max-bytes","max-targets");
    private static final Set<String> INDEX=Set.of("reference","reference-id","reference-kind","reference-processing","kmer-size","max-bases");
    private static final Set<String> QUANT=Set.of("index-model","lanes","sample-id","library","strand","fragment-length-mode","fragment-mean","fragment-sd","bias","bootstraps","seed","single-overhang","threads","max-records","max-cells");
    static int run(String[] arguments,PrintStream out,PrintStream err) {
        if(Arrays.asList(arguments).contains("--help")){out.println(help());return 0;}
        try {
            var allowed=new HashSet<>(COMMON);allowed.addAll(INDEX);allowed.addAll(QUANT);var o=options(arguments,allowed);
            String method=choice(o,"method",Set.of("index","quant"));required(o,"out");
            for(String key:method.equals("index")?QUANT:INDEX)if(o.containsKey(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            int timeout=integer(o,"timeout",3600,1,86400),maxTargets=integer(o,"max-targets",1_000_000,1,1_000_000);
            long maxBytes=limit(o,"max-bytes",8_589_934_592L,1,1_099_511_627_776L);
            if(method.equals("index")) {
                for(String key:List.of("reference","reference-id","reference-kind","reference-processing","kmer-size"))required(o,key);
                choice(o,"reference-kind",Set.of("transcriptome","contigs","personalized-transcripts"));choice(o,"reference-processing",Set.of("strict","upstream"));int k=integer(o,"kmer-size",31,3,31);if(k%2==0)throw new IllegalArgumentException("--kmer-size requires an odd integer in3..31");
                limit(o,"max-bases",1_000_000_000L,1,100_000_000_000L);
            } else {
                for(String key:List.of("index-model","lanes","sample-id","library","strand","fragment-length-mode","bias","bootstraps"))required(o,key);
                choice(o,"library",Set.of("single","paired"));choice(o,"strand",Set.of("unstranded","fr","rf"));choice(o,"fragment-length-mode",Set.of("estimate","supplied"));choice(o,"bias",Set.of("true","false"));
                boolean supplied=o.get("fragment-length-mode").equals("supplied");
                if(o.get("library").equals("single")&&!supplied)throw new IllegalArgumentException("Single-end reads require supplied --fragment-mean and --fragment-sd");
                for(String key:List.of("fragment-mean","fragment-sd")){if(supplied){required(o,key);if(!(number(o,key,0)>0))throw new IllegalArgumentException("--"+key+" must be positive");}else if(o.containsKey(key))throw new IllegalArgumentException("--"+key+" applies only to supplied fragment length");}
                int boots=integer(o,"bootstraps",0,0,1000);if(boots>0){required(o,"seed");integer(o,"seed",42,0,Integer.MAX_VALUE);}else if(o.containsKey("seed"))throw new IllegalArgumentException("--seed applies only when bootstraps are requested");
                if(o.containsKey("single-overhang")){choice(o,"single-overhang",Set.of("true","false"));if(!o.get("library").equals("single"))throw new IllegalArgumentException("--single-overhang applies only to single-end libraries");}
                integer(o,"threads",1,1,256);limit(o,"max-records",100_000_000L,1,2_000_000_000L);limit(o,"max-cells",10_000_000L,1,100_000_000L);
                text(o.get("sample-id"),"Sample ID");
            }
            int[] code={0};externalTransaction(o,"kallisto-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var sources=new LinkedHashMap<Path,String>();var warnings=new ArrayList<String>();session.put("choices",o);session.put("engine","original-kallisto");
                try {
                    Path home=Path.of(o.getOrDefault("home",System.getenv().getOrDefault("JLINALG_KALLISTO_HOME","build/omics-kallisto-0.48.0")));var runtime=new KallistoRuntime(home,dir,session);
                    if(method.equals("index"))index(o,dir,session,sources,warnings,runtime,maxTargets,maxBytes,timeout);else quant(o,dir,session,sources,warnings,runtime,maxTargets,maxBytes,timeout);
                    verify(sources);runtime.verify();var originalWarnings=new LinkedHashSet<>(RnaToolRuntime.warnings(dir));try(var paths=Files.list(dir)){for(Path path:paths.filter(p->p.toString().endsWith(".log")).toList())try(var lines=Files.lines(path)){lines.filter(line->line.matches("(?i)^\\s*\\[~?warn\\].*")).limit(100).forEach(line->originalWarnings.add(path.getFileName()+": "+line));}}warnings.addAll(originalWarnings);
                    if(Boolean.TRUE.equals(session.get("run_info_call_field_reescaped")))warnings.add("WINDOWS METADATA COMPATIBILITY: original upstream/run_info.json has an unescaped Windows call string. Its bytes are preserved. The exact recorded call was checked against launched arguments and re-escaped only in derived run-info-audited.json; all numeric fields are strictly parsed and independently audited. Use the audited copy for JSON consumers,not the invalid original metadata.");
                    warnings.add("ORIGINAL VERSION AND FORMAT: unmodified official Windows kallisto0.48.0,including legacy bias correction. Forward-slash arguments and a strictly limited derived call-field metadata audit handle the original Windows JSON defect. This binary does not produce HDF5 even when reporting bootstraps; Java quant always requests plaintext and verifies every draw. HDF5/Sleuth-dependent workflows require a separately supplied and validated HDF5-enabled environment; they are unavailable through this profile.");
                    warnings.add("SCOPE: pseudoalignment/estimated abundance is not coordinate alignment,observed raw counting or a differential test. BUS/UMI/kb-count,personalized reference construction,fusion/pseudoBAM/genomeBAM,modern0.52.0,historical0.43/0.44/0.46 and full-paper parity remain distinct and unvalidated.");manifest.put("analysis_status","completed-with-warnings");
                }catch(KallistoRuntime.Unavailable failure){code[0]=3;manifest.put("analysis_status","skipped-dependency-unavailable");manifest.put("error",failure.getMessage());RnaToolRuntime.quarantine(dir);warnings.add(0,"ANALYSIS SKIPPED - KALLISTO UNAVAILABLE: "+failure.getMessage());warnings.add(KallistoRuntime.setup());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(dir);warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage());}
                var hashes=new LinkedHashMap<String,String>();sources.forEach((path,digest)->hashes.put(path.toString(),digest));session.put("source_sha256",hashes);session.put("warnings",warnings);
                Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
                Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==0?"KALLISTO WARNINGS REQUIRE REVIEW":code[0]==3?"KALLISTO ANALYSIS SKIPPED":"KALLISTO ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+(code[0]==3?KallistoRuntime.setup():code[0]==0?"Review declared reference/library/strand/fragment/bias choices,all target/length/mapping counters and uncertainty files. Preserve original sources and upstream output; estimated counts are fractional and TPM is not a raw-count matrix.":"Correct the named input,model identity,FASTQ pairing/reference,resource bound or upstream log failure. No result is successful; partial/ retains diagnostics. Inspect bootstrap completeness and empty/low-mapping libraries. Rerun into a new directory after correction; never relabel the failed0.52.0 source build as this0.48.0 runtime.")+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Original kallisto "+method+" completed: "+o.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage());return 2;}
    }
    private static void index(Map<String,String> o,Path dir,Map<String,Object> session,Map<Path,String> sources,List<String> warnings,KallistoRuntime runtime,int maxTargets,long maxBytes,int timeout)throws Exception {
        Path reference=source(o.get("reference"),sources,maxBytes);int k=Integer.parseInt(o.get("kmer-size"));var targets=KallistoTables.reference(reference,maxTargets,limit(o,"max-bases",1_000_000_000L,1,100_000_000_000L),maxBytes,o.get("reference-processing").equals("strict"));
        if(targets.stream().noneMatch(t->t.length()>=k))throw new IOException("Every target is shorter than the chosen k-mer size");
        Path index=dir.resolve("index.idx");runtime.execute(List.of("index","-i",KallistoRuntime.path(index),"-k",Integer.toString(k),KallistoRuntime.path(reference)),dir,"index",timeout);checkFile(index,maxBytes);inspect(runtime,index,dir,k,targets.size(),timeout);
        writeTargets(dir.resolve("targets.tsv"),targets);var rows=new ArrayList<String[]>();for(var target:targets)rows.add(new String[]{target.id(),Integer.toString(target.length()),target.length()<k?"shorter-than-k":"indexed-target"});table(dir.resolve("results.tsv"),List.of("target_id","raw_length","length_status"),rows);
        var model=new LinkedHashMap<String,Object>();model.put("schema","jlinalg-kallisto-index-v1");model.put("engine_version",KallistoRuntime.VERSION);model.put("index_version",10);model.put("kmer_size",k);model.put("target_count",targets.size());model.put("reference_id",text(o.get("reference-id"),"Reference ID"));model.put("reference_kind",o.get("reference-kind"));model.put("reference_processing",o.get("reference-processing"));model.put("reference_sha256",sources.get(reference));model.put("members",Map.of("index.idx",hash(index),"targets.tsv",hash(dir.resolve("targets.tsv"))));Files.writeString(dir.resolve("model.json"),RegenieRuntime.json(model)+"\n");
        long ambiguous=targets.stream().mapToLong(KallistoTables.Target::ambiguous).sum(),u=targets.stream().mapToLong(KallistoTables.Target::uracils).sum(),tails=targets.stream().filter(t->t.terminalAs()>=10).count(),shorts=targets.stream().filter(t->t.length()<k).count();session.put("targets",targets.size());session.put("reference_bases",targets.stream().mapToLong(KallistoTables.Target::length).sum());
        warnings.add("REFERENCE ACTION: verify the declared reference/species/annotation. Model.json seals the exact generated index and raw target dictionary; metadata alone does not prove a reference release. Target IDs use the first FASTA header token. Duplicate/empty IDs fail; descriptions remain in the unchanged source.");
        if(ambiguous>0||u>0||tails>0)warnings.add("UPSTREAM REFERENCE PROCESSING: "+ambiguous+" ambiguous IUPAC bases are replaced pseudorandomly (original seed42),"+u+" U bases become T,and"+tails+" known terminal A runs are at least10bases. Upstream clips poly-A during indexing but retains RAW lengths in abundance output. Inspect targets.tsv and index.log; raw known A runs need not describe tails extended by ambiguity replacement.");
        if(shorts>0)warnings.add("SHORT TARGETS: "+shorts+" raw targets are shorter than k and cannot contribute a full k-mer; they remain in the output dictionary. Inspect zero abundances rather than dropping their IDs.");
    }
    private static void quant(Map<String,String> o,Path dir,Map<String,Object> session,Map<Path,String> sources,List<String> warnings,KallistoRuntime runtime,int maxTargets,long maxBytes,int timeout)throws Exception {
        Path modelPath=source(o.get("index-model"),sources,maxBytes);var model=RegenieRuntime.object(modelPath);
        if(!"jlinalg-kallisto-index-v1".equals(model.get("schema"))||!KallistoRuntime.VERSION.equals(model.get("engine_version"))||counter(model,"index_version")!=10)throw new IOException("Index model schema/version differs; rebuild with this exact adapter/runtime");
        if(!(model.get("members") instanceof Map<?,?> members)||!members.keySet().equals(Set.of("index.idx","targets.tsv")))throw new IOException("Index model member inventory differs");
        for(var entry:members.entrySet()){Path member=source(RegenieRuntime.contained(modelPath.getParent(),entry.getKey().toString()).toString(),sources,maxBytes);if(!hash(member).equals(entry.getValue()))throw new IOException("Index model member changed: "+entry.getKey());}
        Path index=modelPath.getParent().resolve("index.idx");var targets=readTargets(modelPath.getParent().resolve("targets.tsv"),maxTargets);
        if(counter(model,"target_count")!=targets.size())throw new IOException("Index model target count differs");long declaredK=counter(model,"kmer_size");if(declaredK<3||declaredK>31||declaredK%2==0)throw new IOException("Invalid model k-mer size");int k=(int)declaredK;
        if(!(model.get("reference_id") instanceof String referenceId)||referenceId.isBlank()||!Set.of("transcriptome","contigs","personalized-transcripts").contains(model.get("reference_kind"))||!Set.of("strict","upstream").contains(model.get("reference_processing")))throw new IOException("Index model reference declaration differs");
        int boots=Integer.parseInt(o.get("bootstraps"));if((long)targets.size()*(boots+1)>limit(o,"max-cells",10_000_000L,1,100_000_000L))throw new IOException("Target-by-(point+bootstrap) cells exceed --max-cells");inspect(runtime,index,dir,k,targets.size(),timeout);
        Path lanePath=source(o.get("lanes"),sources,maxBytes);var laneData=readLanes(lanePath,o.get("library").equals("paired"));var arguments=new ArrayList<String>();long records=0,bases=0;var laneRows=new ArrayList<String[]>();var seenPaths=new HashSet<Path>();int reused=0;
        for(String[] row:laneData) {
            Path read1=relativeSource(lanePath,row[1],sources,maxBytes),read2=row.length==3?relativeSource(lanePath,row[2],sources,maxBytes):null;
            if(read2!=null&&Files.isSameFile(read1,read2))throw new IOException("FASTQ mates must be different files");for(Path path:read2==null?List.of(read1):List.of(read1,read2))if(!seenPaths.add(path.toRealPath()))reused++;
            long laneCount=0;try(var first=new FastqIO.Reader(read1);var second=read2==null?null:new FastqIO.Reader(read2)) {
                FastqIO.Read a;while((a=first.next())!=null){laneCount++;records++;if(records>limit(o,"max-records",100_000_000L,1,2_000_000_000L))throw new IOException("FASTQ records exceed --max-records");bases=Math.addExact(bases,a.sequence().length());if(second!=null){var b=second.next();if(b==null||!a.pairId().equals(b.pairId()))throw new IOException("FASTQ mate identity/count/order mismatch in lane "+row[0]+" record "+laneCount);bases=Math.addExact(bases,b.sequence().length());}}
                if(second!=null&&second.next()!=null)throw new IOException("FASTQ mate has extra records in lane "+row[0]);
            }
            if(laneCount==0)throw new IOException("Empty FASTQ lane: "+row[0]);arguments.add(KallistoRuntime.path(read1));if(read2!=null)arguments.add(KallistoRuntime.path(read2));laneRows.add(new String[]{row[0],read1.toString(),read2==null?"":read2.toString(),Long.toString(laneCount)});
        }
        Path upstream=dir.resolve("upstream");Files.createDirectory(upstream);var command=new ArrayList<>(List.of("quant","--plaintext","-i",KallistoRuntime.path(index),"-o",KallistoRuntime.path(upstream),"-t",o.getOrDefault("threads","1"),"-b",Integer.toString(boots)));
        if(boots>0)command.addAll(List.of("--seed",o.get("seed")));if(o.get("library").equals("single"))command.add("--single");if(o.getOrDefault("single-overhang","false").equals("true"))command.add("--single-overhang");if(!o.get("strand").equals("unstranded"))command.add("--"+o.get("strand")+"-stranded");if(o.get("bias").equals("true"))command.add("--bias");if(o.get("fragment-length-mode").equals("supplied"))command.addAll(List.of("-l",o.get("fragment-mean"),"-s",o.get("fragment-sd")));command.addAll(arguments);
        try{runtime.execute(command,dir,"quant",timeout);}catch(IOException failure){String log=Files.exists(dir.resolve("quant.log"))?Files.readString(dir.resolve("quant.log")):"";if(log.contains("0 reads pseudoaligned")||log.contains("no reads pseudoaligned"))throw new IOException("Zero pseudoaligned fragments in sample "+o.get("sample-id")+"; inspect reference,library,strand and preprocessing. Original exit status and partial outputs are retained; no zero library is fabricated",failure);throw failure;}
        var info=runtime.runInfo(upstream.resolve("run_info.json"),dir.resolve("run-info-audited.json"));
        if(!KallistoRuntime.VERSION.equals(info.get("kallisto_version"))||counter(info,"index_version")!=10||counter(info,"n_targets")!=targets.size()||counter(info,"n_bootstraps")!=boots||counter(info,"n_processed")!=records)throw new IOException("Original run_info version/target/bootstrap/input-record counters differ");
        long mapped=counter(info,"n_pseudoaligned"),unique=counter(info,"n_unique");if(unique>mapped||mapped>records)throw new IOException("Original mapping counters are inconsistent");
        percentage(info,"p_pseudoaligned",mapped,records);percentage(info,"p_unique",unique,records);
        if(mapped==0)throw new IOException("Zero pseudoaligned fragments in sample "+o.get("sample-id")+"; inspect reference,library,strand and preprocessing. No zero library is imputed or silently dropped");
        var expectedFiles=new HashSet<>(List.of("abundance.tsv","run_info.json"));for(int i=0;i<boots;i++)expectedFiles.add("bs_abundance_"+i+".tsv");var observed=new HashSet<String>();try(var stream=Files.list(upstream)){for(Path path:stream.toList()){checkFile(path,maxBytes);observed.add(path.getFileName().toString());}}
        if(!observed.equals(expectedFiles))throw new IOException("Original point/bootstrap output inventory differs; every requested uncertainty draw must be present (HDF5 is unavailable in this binary)");
        var point=KallistoTables.abundance(upstream.resolve("abundance.tsv"),targets,mapped,maxBytes);var drawRows=new ArrayList<String[]>();
        for(int i=0;i<boots;i++){Path path=upstream.resolve("bs_abundance_"+i+".tsv");var audit=KallistoTables.abundance(path,targets,mapped,maxBytes);drawRows.add(new String[]{Integer.toString(i),"upstream/"+path.getFileName(),Double.toString(audit.totalEstimatedCounts()),Double.toString(audit.totalTpm()),hash(path)});}
        var rows=new ArrayList<String[]>();for(var estimate:point.rows())rows.add(new String[]{estimate.target(),Integer.toString(estimate.length()),Double.toString(estimate.effectiveLength()),Double.toString(estimate.estimatedCount()),Double.toString(estimate.tpm()),o.get("sample-id")});
        table(dir.resolve("results.tsv"),List.of("target_id","length","effective_length","estimated_counts","tpm","sample_id"),rows);table(dir.resolve("bootstrap-inventory.tsv"),List.of("draw_zero_based","file","estimated_count_total","tpm_total","sha256"),drawRows);table(dir.resolve("lanes.tsv"),List.of("lane","read1","read2","input_records_or_pairs"),laneRows);
        session.put("input_records_or_pairs",records);session.put("input_bases",bases);session.put("pseudoaligned",mapped);session.put("unique_pseudoaligned",unique);session.put("upstream_run_info",info);session.put("index_model",model);session.put("bootstrap_draws_verified",boots);session.put("count_total",point.totalEstimatedCounts());
        var outputHashes=new LinkedHashMap<String,String>();try(var stream=Files.walk(upstream)){for(Path path:stream.filter(Files::isRegularFile).toList())outputHashes.put(dir.relativize(path).toString().replace('\\','/'),hash(path));}
        for(String name:List.of("results.tsv","bootstrap-inventory.tsv","lanes.tsv","run-info-audited.json"))outputHashes.put(name,hash(dir.resolve(name)));
        var quantModel=new LinkedHashMap<String,Object>();quantModel.put("schema","jlinalg-kallisto-quant-v1");quantModel.put("engine_version",KallistoRuntime.VERSION);quantModel.put("sample_id",o.get("sample-id"));quantModel.put("reference_id",model.get("reference_id"));quantModel.put("reference_kind",model.get("reference_kind"));quantModel.put("index_model_sha256",sources.get(modelPath));quantModel.put("index_sha256",sources.get(index));quantModel.put("choices",o);quantModel.put("members",outputHashes);Files.writeString(dir.resolve("model.json"),RegenieRuntime.json(quantModel)+"\n");
        warnings.add("LIBRARY ACTION: one biological sample per invocation; lanes are technical chunks. Paired counters count fragments,not individual reads. Verify supplied/estimated fragment length,strand,single-overhang and sequence-bias choices. Exact matched names/order and unchanged input bytes are checked; no trimming,UMI removal or mate repair occurs.");
        warnings.add("ESTIMATED COUNTS AND UNCERTAINTY: results.tsv retains fractional estimates. Original upstream/abundance.tsv can be passed to native transcript-import --format kallisto. Bootstrap uncertainty is "+(boots==0?"explicitly not requested":"retained in all"+boots+"plaintext draw files listed in bootstrap-inventory.tsv")+"; choosing transcript-import --inferential-replicates drop discards it. No counts are rounded to raw observations and no gene mapping is inferred.");
        if(mapped<records)warnings.add("UNMAPPED FRAGMENTS/READS: "+(records-mapped)+" of"+records+" were not pseudoaligned. These remain in input totals and are not assigned to genes; review run_info.json and reference/library compatibility.");
        if(reused>0)warnings.add("REUSED FASTQ FILES: "+reused+" lane file references repeat the same physical source. Original kallisto counts every declared occurrence; this is not independent biological replication.");
    }
    private static void inspect(KallistoRuntime runtime,Path index,Path dir,int k,int targets,int timeout)throws Exception {runtime.execute(List.of("inspect",KallistoRuntime.path(index)),dir,"inspect",timeout);String report=Files.readString(dir.resolve("inspect.log"));for(var e:Map.of("Index version number",10,"k",k,"number of targets",targets).entrySet()){var match=Pattern.compile("(?m)^#\\[inspect\\] "+Pattern.quote(e.getKey())+" = ([0-9,]+)\\s*$").matcher(report);if(!match.find()||Long.parseLong(match.group(1).replace(",",""))!=e.getValue())throw new IOException("Actual index "+e.getKey()+" differs from declared model");}}
    private static void writeTargets(Path path,List<KallistoTables.Target> targets)throws IOException {try(var out=Files.newBufferedWriter(path)){out.write("target\tlength\tambiguous_bases\tu_bases\tknown_terminal_A_run\n");for(var t:targets)out.write(t.id()+"\t"+t.length()+"\t"+t.ambiguous()+"\t"+t.uracils()+"\t"+t.terminalAs()+"\n");}}
    private static List<KallistoTables.Target> readTargets(Path path,int maximum)throws IOException {var result=new ArrayList<KallistoTables.Target>();var seen=new HashSet<String>();try(var input=Files.newBufferedReader(path)){if(!"target\tlength\tambiguous_bases\tu_bases\tknown_terminal_A_run".equals(input.readLine()))throw new IOException("Target model header differs");String line;while((line=input.readLine())!=null){if(result.size()>=maximum)throw new IOException("Target model exceeds --max-targets");String[] f=line.split("\t",-1);if(f.length!=5)throw new IOException("Target model row requires five fields");try{var target=new KallistoTables.Target(f[0],Integer.parseInt(f[1]),Long.parseLong(f[2]),Long.parseLong(f[3]),Integer.parseInt(f[4]));if(!seen.add(target.id()))throw new IOException("Duplicate target model ID");result.add(target);}catch(IllegalArgumentException failure){throw new IOException("Invalid target model row",failure);}}}if(result.isEmpty())throw new IOException("Empty target model");return List.copyOf(result);}
    private static List<String[]> readLanes(Path path,boolean paired)throws IOException {var rows=new ArrayList<String[]>();var ids=new HashSet<String>();try(var input=Files.newBufferedReader(path)){if(!(paired?"lane\tread1\tread2":"lane\tread1").equals(input.readLine()))throw new IOException("Lane manifest requires exact lane/read1"+(paired?"/read2":"")+" TSV header");String line;while((line=input.readLine())!=null){if(rows.size()>=1000)throw new IOException("At most1000technical lanes per sample");String[] f=line.split("\t",-1);if(f.length!=(paired?3:2)||!ids.add(text(f[0],"Lane ID")))throw new IOException("Lane manifest has wrong fields or duplicate ID");for(int i=1;i<f.length;i++)text(f[i],"FASTQ path");rows.add(f);}}if(rows.isEmpty())throw new IOException("Empty lane manifest");return rows;}
    private static Path relativeSource(Path manifest,String name,Map<Path,String> sources,long maximum)throws IOException {Path path=Path.of(name);return source((path.isAbsolute()?path:manifest.getParent().resolve(path)).toString(),sources,maximum);}
    private static Path source(String name,Map<Path,String> sources,long maximum)throws IOException {Path path=Path.of(name).toAbsolutePath().normalize();checkFile(path,maximum);String digest=hash(path),prior=sources.putIfAbsent(path,digest);if(prior!=null&&!prior.equals(digest))throw new IOException("Source changed between uses: "+path);return path;}
    private static void verify(Map<Path,String> sources)throws IOException {for(var entry:sources.entrySet())if(!entry.getValue().equals(hash(entry.getKey())))throw new IOException("Source changed during analysis: "+entry.getKey());}
    private static void checkFile(Path path,long maximum)throws IOException {if(!Files.isRegularFile(path)||Files.size(path)>maximum)throw new IOException("Source/output missing or exceeds --max-bytes: "+path);}
    private static long counter(Map<?,?> object,String key)throws IOException {try{long value=Long.parseLong(String.valueOf(object.get(key)));if(value<0)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("Expected exact nonnegative integer for "+key,failure);}}
    private static void percentage(Map<?,?> object,String key,long numerator,long denominator)throws IOException {try{double value=Double.parseDouble(String.valueOf(object.get(key)));if(!Double.isFinite(value)||Math.abs(value-100.0*numerator/denominator)>.051)throw new NumberFormatException();}catch(NumberFormatException failure){throw new IOException("Mapping percentage differs from original counters: "+key,failure);}}
    private static long limit(Map<String,String> options,String key,long fallback,long low,long high){long value=Long.parseLong(options.getOrDefault(key,Long.toString(fallback)));if(value<low||value>high)throw new IllegalArgumentException("--"+key+" outside"+low+".."+high);return value;}
    private static String text(String value,String name)throws IOException {if(value==null||value.isBlank()||value.chars().anyMatch(Character::isISOControl))throw new IOException(name+" must be nonblank without control characters");return value;}
    private static String choice(Map<String,String> o,String key,Set<String> choices){String value=required(o,key);if(!choices.contains(value))throw new IllegalArgumentException("--"+key+" requires "+choices);return value;}
    private static String help(){return """
        Original kallisto0.48.0 index and bulk quantification; no automatic installation.
        kallisto-tools --method index --reference FASTA --reference-id DECLARED_RELEASE
          --reference-kind transcriptome|contigs|personalized-transcripts
          --reference-processing strict|upstream --kmer-size ODD_3_TO_31 --out NEW_DIR
        kallisto-tools --method quant --index-model INDEX_DIR/model.json --lanes LANES_TSV
          --sample-id ID --library single|paired --strand unstranded|fr|rf
          --fragment-length-mode estimate|supplied [--fragment-mean N --fragment-sd N]
          --bias true|false --bootstraps N [--seed N] --out NEW_DIR
          [--single-overhang true|false] [--threads 1] [--max-records 100000000]
          [--max-cells 10000000]
        Common: --home HOME or JLINALG_KALLISTO_HOME, --timeout 3600, --max-targets 1000000,
        --max-bytes 8589934592. Index additionally accepts --max-bases 1000000000.
        Lane TSV is unquoted lane/read1 or lane/read1/read2; relative paths resolve beside it.
        One biological sample per call. Single-end libraries require supplied fragment lengths.
        Sealed exact index/target/version identities; complete raw upstream/ outputs retained.
        Quant always uses --plaintext and verifies every bootstrap draw; this Windows binary
        does not emit HDF5. HDF5/Sleuth workflows remain unavailable. Estimated counts/TPM
        are not raw observed counts. No BUS/UMI, personalized-reference construction,fusion,
        BAM export,DE,historical/full-paper parity. Missing runtime skips3; errors2 retain
        partial diagnostics and WARNINGS.md with exact recovery. Setup:
        python tools/literature/install_kallisto.py --allow-download
        """;}
}
