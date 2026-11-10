/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.CellChatStrengthRequest.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Owned original-package execution with typed input and explicit result admission. */
public final class CellChatStrengthCli {
    private CellChatStrengthCli() { }
    static final String ACTION="Require exit0, result_admitted=true and successful complete-bundle verification. Read WARNINGS.md, analysis-status.json, provenance.json, original stdout/stderr and every original-centrality-fallbacks.tsv. SKIP3: skip dependent analysis; supply the exact permitted Windows R4.6.1/CellChat2.2.0/Presto1.0.0 runtime and source profile where installation is allowed, or leave analysis skipped. Analysis installs nothing and changes no source edition or do.fast setting. FAIL2: admit no result, including earlier completed fits. Preserve partial files, correct the named input/source/runtime/process/capacity cause and retry NEW paths. Do not repair identifiers, replace unselected interactions or nonfinite original statistics with zeros, or interpret original centrality fallback zeros as successful estimates.";
    static final String SCOPE="Original CellChat2.2.0 RNA inference on explicitly prepared expression: two pooled conditions plus each sample, full original LR/pathway networks and centrality. Incoming/outgoing LR and pathway strengths per cell type are contrasted using explicit strict positive, negative or absolute difference selection, followed by complete-case original unpaired Wilcoxon tests across declared samples. An ancillary directed-edge comparison is retained separately. Native Java checks complete network transports, source-specific node strengths, all selection flags, missingness and midrank test statistics. P-values and other statistical/graph estimators are not independently reimplemented. Unselected features/groups remain missing; source fallback zeros and nonfinite tests remain visible. No spatial model, PPI smoothing, FlowSig, multiple-testing correction, historical paper environment, biological independence or unspecified sign convention is inferred. File/time limits are logical bounds, not hard RAM/disk quotas.";
    private static final String RESOURCE="/org/jlinalg/omics/cellchat-strength/";
    private static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }
    private record Captured(Path original,Path copy,Pin fingerprint) {
        void verify()throws IOException {require(fingerprint.equals(pin(original,fingerprint.bytes(),()->{}))&&fingerprint.equals(pin(copy,fingerprint.bytes(),()->{})),"Captured/original input changed: "+original);}
    }
    private record Runtime(Path source,Path rscript,Path rHome,List<Path> libraries,Map<Path,Pin> files) { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path out=null;Process process=null;int exit=2;var state=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
        state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("result_admitted",false);warnings.add(SCOPE);
        try {
            if(args.length==1&&args[0].equals("--help")) {
                output.println("CellChatStrengthCli --request request.json\nCellChatStrengthCli --verify completed-bundle\nExplicit schema_version1, NEW out, runtime_profile, analysis and limits required. Dense literal UTF-8 expression TSV begins gene_id followed by phenotype-ordered cell IDs; metadata columns are cell_id,group,sample,condition in that exact order. Full field schema: CellChatStrengthRequest.\n"+SCOPE+"\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")){verifyBundle(Path.of(args[1]).toAbsolutePath().normalize());output.println("Verified complete original CellChat bundle structure and native network transports; no R refit or producer authentication.");return 0;}
            require(args.length==2&&args[0].equals("--request"),"Require --request JSON, --verify BUNDLE or --help");
            Path requestPath=Path.of(args[1]).toAbsolutePath().normalize();Pin requestPin=pin(requestPath,1048576,()->{});byte[] raw=bytes(requestPath,1048576);
            require(requestPin.sha256().equals(sha(raw)),"Request changed during intake");Request request=CellChatStrengthRequest.parse(NextflowParameters.parse(utf8(raw),1048576,100000),requestPath.getParent());
            Path target=resolved(request.out());require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and choose NEW paths");
            Runtime runtime=runtime(request.runtimeProfile(),request.limits());
            for(Path protectedPath:runtime.libraries())overlap(target,protectedPath);
            for(Path protectedPath:List.of(runtime.rHome(),runtime.source(),request.expression(),request.metadata(),requestPath,request.runtimeProfile()))overlap(target,protectedPath);
            Files.createDirectories(target.getParent());out=Files.createDirectory(target);
            Files.writeString(out.resolve("WARNINGS.initial.md"),"# PROVISIONAL: NO RESULT ADMITTED\n\n"+SCOPE+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.write(out.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);Captured capturedRequest=new Captured(requestPath,out.resolve("source-request.json"),requestPin);capturedRequest.verify();
            Captured profile=capture(request.runtimeProfile(),out.resolve("runtime-profile.json"),67108864);Path inputs=Files.createDirectory(out.resolve("inputs"));
            Captured expression=capture(request.expression(),inputs.resolve("expression.tsv"),request.limits().inputBytes());Captured metadata=capture(request.metadata(),inputs.resolve("metadata.tsv"),request.limits().inputBytes()-expression.fingerprint.bytes());
            var population=CellChatStrengthTables.inputs(expression.copy,metadata.copy,request);
            byte[] workerSource=resource("worker.R"),sourceLock=resource("SOURCE-LOCK.json");Files.write(out.resolve("worker-source.R"),workerSource,StandardOpenOption.CREATE_NEW);Files.write(out.resolve("source-lock.json"),sourceLock,StandardOpenOption.CREATE_NEW);
            var analysis=new LinkedHashMap<>(request.analysis());analysis.put("expression",expression.copy.toString());analysis.put("metadata",metadata.copy.toString());writeJson(out.resolve("effective-analysis.json"),analysis);
            outputBounds(out,request.limits());verifyRuntime(runtime,request.limits());Path worker=out.resolve("worker");
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))throw new Unavailable("Original CellChat runtime profile supports Windows R4.6.1; use portable --verify elsewhere");
            var command=new ArrayList<String>();command.add(runtime.rscript.toString());command.add("--vanilla");command.add(out.resolve("worker-source.R").toString());command.add(out.resolve("effective-analysis.json").toString());command.add(runtime.source.toString());command.add(out.resolve("source-lock.json").toString());command.add(worker.toString());for(Path p:runtime.libraries)command.add(p.toString());
            writeJson(out.resolve("command.json"),command);ProcessBuilder builder=new ProcessBuilder(command).redirectOutput(out.resolve("original.stdout").toFile()).redirectError(out.resolve("original.stderr").toFile());
            var environment=builder.environment();environment.keySet().removeIf(k->k.startsWith("LC_")||k.startsWith("R_")||Set.of("LANG","LANGUAGE").contains(k));
            environment.put("R_HOME",runtime.rHome.toString());environment.put("LANG","C");environment.put("LC_ALL","C");environment.put("LANGUAGE","en");environment.put("OMP_NUM_THREADS","1");environment.put("OPENBLAS_NUM_THREADS","1");environment.put("MKL_NUM_THREADS","1");
            long start=System.nanoTime();process=builder.start();long pid=process.pid();writeJson(out.resolve("process-started.json"),Map.of("pid",pid,"owned",true,"command",command));
            while(!process.waitFor(250,TimeUnit.MILLISECONDS)) {
                require(System.nanoTime()-start<=Duration.ofSeconds(request.limits().wallSeconds()).toNanos(),"Original CellChat time capacity exceeded");outputBounds(out,request.limits());
            }
            outputBounds(out,request.limits());writeJson(out.resolve("process.json"),Map.of("pid",pid,"exit_code",process.exitValue(),"elapsed_nanos",System.nanoTime()-start));
            if(process.exitValue()!=0) {
                String detail="No complete original worker report";Path reportPath=worker.resolve("worker-report.json");
                if(Files.isRegularFile(reportPath)){Map<?,?> failure=object(json(reportPath,16777216));if(failure.get("detail") instanceof String cause)detail=cause.length()>16384?cause.substring(0,16384):cause;}
                throw new IOException("Original CellChat exit"+process.exitValue()+": "+detail+"; inspect original stderr and retained worker-report.json");
            }
            for(Captured c:List.of(capturedRequest,profile,expression,metadata))c.verify();verifyRuntime(runtime,request.limits());
            require(java.util.Arrays.equals(resource("worker.R"),workerSource)&&java.util.Arrays.equals(resource("SOURCE-LOCK.json"),sourceLock),"Packaged worker/source-lock changed");
            Map<String,Object> audit=CellChatStrengthAudit.verify(worker,request,population);long fallbacks=((Number)audit.get("original_centrality_fallbacks")).longValue(),nonfinite=((Number)audit.get("original_nonfinite_centrality_fields")).longValue(),nonfiniteTests=((Number)audit.get("original_nonfinite_sample_test_fields")).longValue(),notTested=((Number)audit.get("not_tested_sample_comparisons")).longValue(),notComparable=((Number)audit.get("not_comparable_pooled_rows")).longValue();
            if(fallbacks>0)warnings.add("ORIGINAL CENTRALITY FALLBACK WARNING: "+fallbacks+" flowbet/info calculations failed and original CellChat emitted zeros. Every cause is retained in original-centrality-fallbacks.tsv. These zeros are source fallbacks and do not establish successful graph estimates.");
            if(nonfinite>0)warnings.add("ORIGINAL NONFINITE CENTRALITY WARNING: "+nonfinite+" fields are retained literally; no repair or successful finite-estimate claim is made.");
            if(nonfiniteTests>0)warnings.add("ORIGINAL NONFINITE SAMPLE TEST WARNING: "+nonfiniteTests+" original Wilcoxon results are nonfinite. Their literal tokens and statuses are retained; they do not establish an association or a null result.");
            if(notTested>0||notComparable>0)warnings.add("COMPARISON COVERAGE WARNING: "+notTested+" sample-test rows were not tested and "+notComparable+" pooled rows were not comparable. Inspect the complete comparison table; unselected interactions/groups are retained as NA rather than filled with zeros.");
            long pooledZeroSamples=((Number)audit.get("positive_pooled_rows_with_zero_selected_sample_scores")).longValue();
            if(pooledZeroSamples>0)warnings.add("POOLED VERSUS SAMPLE WARNING: "+pooledZeroSamples+" pooled directed LR scores are positive while every corresponding selected per-sample score is zero. Original results are retained. Review filtering/normalization and the complete condition/sample tables before interpreting replicate support; provenance includes up to five examples. These are raw filtered scores, not a claim of significant interactions.");
            for(String field:List.of("not_comparable_pooled_strengths","not_tested_strength_comparisons","original_nonfinite_strength_test_fields","original_strength_test_warning_count","missing_case_strengths")) {
                long number=((Number)audit.get(field)).longValue();if(number>0)warnings.add("STRENGTH COMPARISON WARNING: "+field+"="+number+". Review the complete strengths-by-case and pooled-and-sample-strengths tables; missing features/groups are never zero-filled and original nonfinite tests are not estimated evidence.");
            }
            for(String field:List.of("strength_comparison_rows","selected_strength_comparisons","not_comparable_pooled_strengths","not_tested_strength_comparisons","original_nonfinite_strength_test_fields","tested_strength_comparisons","original_strength_test_warning_count","missing_case_strengths"))state.put(field,audit.get(field));
            Map<?,?> report=object(json(worker.resolve("worker-report.json"),16777216));for(Object message:list(report.get("warnings")))warnings.add("ORIGINAL R WARNING: "+text(message));
            writeJson(out.resolve("provenance.json"),audit);Files.move(worker,out.resolve("results"));outputBounds(out,request.limits());
            state.put("status",fallbacks>0||nonfinite>0||nonfiniteTests>0||notTested>0||notComparable>0||pooledZeroSamples>0||((Number)audit.get("not_tested_strength_comparisons")).longValue()>0||!list(report.get("warnings")).isEmpty()?"completed-original-cellchat-strength-workflow-with-source-warnings":"completed-original-cellchat-strength-workflow");state.put("result_admitted",true);state.put("original_centrality_fallbacks",fallbacks);state.put("original_nonfinite_centrality_fields",nonfinite);state.put("original_nonfinite_sample_test_fields",nonfiniteTests);state.put("not_tested_sample_comparisons",notTested);state.put("not_comparable_pooled_rows",notComparable);state.put("positive_pooled_rows_with_zero_selected_sample_scores",pooledZeroSamples);exit=0;
        } catch(Unavailable unavailable){exit=3;state.put("status","SKIP3_DEPENDENCY_UNAVAILABLE");state.put("cause",unavailable.toString());warnings.add("SKIP3: DEPENDENT ANALYSIS SKIPPED. "+unavailable.getMessage());}
        catch(Exception failure){state.put("cause",failure.toString());warnings.add("FAIL2: "+failure.getMessage());}
        catch(OutOfMemoryError failure){state.put("cause","Java heap exhausted");warnings.add("FAIL2: Java heap exhausted; preserve every observation and retry with a permitted heap/input capacity.");}
        finally {
            if(process!=null&&process.isAlive())stop(process);
            if(out!=null)try {
                if(exit!=0)for(String name:List.of("worker","results")) {
                    Path from=out.resolve(name),to=out.resolve("unadmitted-"+name);require(from.toAbsolutePath().normalize().getParent().equals(out)&&to.getParent().equals(out),"Unsafe owned quarantine path");if(Files.exists(from))Files.move(from,to);
                }
                state.put("required_action",ACTION);writeJson(out.resolve("analysis-status.json"),state);
                Files.writeString(out.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                var files=inventory(out);var completion=new LinkedHashMap<String,Object>();completion.putAll(state);completion.put("schema_version",1);completion.put("files",files);completion.put("scope",SCOPE);
                Path pending=out.resolve("COMPLETED.pending.json");writeJson(pending,completion);Files.move(pending,out.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
            }catch(Exception finalization){exit=2;error.println("FAIL2 FINALIZATION: NO RESULT ADMITTED. "+finalization+"\n"+ACTION);}
        }
        (exit==0?output:error).println((exit==0?"Original CellChat workflow completed with retained source diagnostics: ":exit==3?"SKIP3 DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 NO RESULT ADMITTED: ")+out);
        for(String warning:warnings)error.println(warning);error.println("WARNINGS / REQUIRED ACTIONS: "+(out==null?"no output directory created":out.resolve("WARNINGS.md"))+"\n"+ACTION);return exit;
    }
    private static Runtime runtime(Path profile,Limits limits)throws IOException {
        if(!Files.isRegularFile(profile))throw new Unavailable("Missing original CellChat runtime profile: "+profile);
        Map<?,?> p=object(json(profile,67108864));keys(p,"schema_version","status","source_label","rscript","r_home","libraries","source_root","files","setup_receipts","scope");integer(p.get("schema_version"),1,1);equal(p,"status","validated-original-cellchat-runtime-profile-only");equal(p,"source_label","2.2.0-2025-release");
        Path base=profile.getParent(),rscript=path(base,p.get("rscript")),home=path(base,p.get("r_home")),source=path(base,p.get("source_root"));var libraries=new ArrayList<Path>();for(Object raw:list(p.get("libraries")))libraries.add(path(base,raw));require(!libraries.isEmpty()&&libraries.size()<=32&&new java.util.HashSet<>(libraries).size()==libraries.size(),"Invalid ordered runtime libraries");
        if(!Files.isRegularFile(rscript)||!Files.isDirectory(home)||!Files.isDirectory(source))throw new Unavailable("Missing declared R executable/home/original CellChat source");for(Path library:libraries)if(!Files.isDirectory(library))throw new Unavailable("Missing declared private R library: "+library);
        Map<?,?> entries=object(p.get("files"));require(entries.size()<=limits.files(),"Runtime file count capacity exceeded");var files=new LinkedHashMap<Path,Pin>();long bytes=0;
        for(var entry:entries.entrySet()){Path file=path(base,entry.getKey());require(file.startsWith(home)||libraries.stream().anyMatch(file::startsWith),"Runtime file lies outside declared roots");Pin pin=fingerprint(entry.getValue(),limits.runtimeBytes());bytes=Math.addExact(bytes,pin.bytes());require(bytes<=limits.runtimeBytes()&&files.put(file,pin)==null,"Runtime inventory byte/duplicate capacity exceeded");}
        require(files.containsKey(rscript),"Runtime executable is not bound");return new Runtime(source,rscript,home,List.copyOf(libraries),Map.copyOf(files));
    }
    private static void verifyRuntime(Runtime runtime,Limits limits)throws IOException {
        // Independent complete-file reads can run concurrently. Every byte and
        // source identity is still checked; no cached-stat shortcut is used.
        var executor=java.util.concurrent.Executors.newFixedThreadPool(8);
        var pending=new ArrayList<java.util.concurrent.Future<Void>>();
        try {
            for(var entry:runtime.files.entrySet())pending.add(executor.submit(()->{
                if(!Files.isRegularFile(entry.getKey()))throw new Unavailable("Missing original runtime file: "+entry.getKey());
                require(entry.getValue().equals(pin(entry.getKey(),entry.getValue().bytes(),()->{})),"Changed original runtime file: "+entry.getKey());return null;
            }));
            for(var future:pending)try{future.get();}
            catch(java.util.concurrent.ExecutionException failure){
                if(failure.getCause() instanceof IOException io)throw io;throw new IOException("Original runtime verification failed",failure.getCause());
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Runtime verification interrupted",interrupted);}
        }finally{for(var future:pending)if(!future.isDone())future.cancel(true);executor.shutdownNow();}
        for(Path library:runtime.libraries)try(var paths=Files.walk(library)) {
            for(Path p:paths.toList()) {require(!Files.isSymbolicLink(p),"Linked R library entry refused");if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))require(runtime.files.containsKey(p),"Unbound extra R library file: "+p);}
        }
        require(runtime.files.size()<=limits.files(),"Runtime count capacity exceeded");
    }
    private static Captured capture(Path source,Path copy,long maximum)throws IOException {
        require(maximum>=0,"Combined input capacity exceeded");Pin p=pin(source,maximum,()->{});Files.copy(source,copy);Captured result=new Captured(source,copy,p);result.verify();return result;
    }
    private static byte[] resource(String name)throws IOException {
        try(var input=CellChatStrengthCli.class.getResourceAsStream(RESOURCE+name)){require(input!=null,"Missing packaged original workflow resource");byte[] raw=input.readNBytes(2097153);require(raw.length<=2097152,"Packaged workflow resource capacity exceeded");return raw;}
    }
    private static void outputBounds(Path root,Limits limits)throws IOException {
        long bytes=0;int count=0;try(var paths=Files.walk(root)){for(Path p:paths.toList()) {
            require(!Files.isSymbolicLink(p),"Linked worker output refused");if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){count++;bytes=Math.addExact(bytes,Files.size(p));require(count<=limits.files()&&bytes<=limits.outputBytes(),"Original CellChat output count/byte capacity exceeded");}
        }}
    }
    private static Map<String,Object> inventory(Path root)throws IOException {
        var result=new java.util.TreeMap<String,Object>();try(var paths=Files.walk(root)){for(Path p:paths.toList())if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(p),"Linked bundle file refused");Pin pin=pin(p,Long.MAX_VALUE,()->{});result.put(root.relativize(p).toString().replace('\\','/'),Map.of("bytes",pin.bytes(),"sha256",pin.sha256()));
        }}return result;
    }
    private static void stop(Process process) {
        List<ProcessHandle> children=process.descendants().toList();for(int i=children.size()-1;i>=0;i--)children.get(i).destroyForcibly();process.destroyForcibly();try{process.waitFor(10,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
    private static void overlap(Path out,Path input)throws IOException {Path actual=resolved(input);require(!out.startsWith(actual)&&!actual.startsWith(out),"Output overlaps protected input/runtime/source");}
    private static Path resolved(Path path)throws IOException {
        var missing=new ArrayDeque<Path>();Path current=path.toAbsolutePath().normalize();while(!Files.exists(current,LinkOption.NOFOLLOW_LINKS)){require(current.getParent()!=null,"No existing path ancestor");missing.addFirst(current.getFileName());current=current.getParent();}current=current.toRealPath();for(Path part:missing)current=current.resolve(part);return current.normalize();
    }
    static void verifyBundle(Path root)throws IOException {
        Map<?,?> completion=object(json(root.resolve("COMPLETED.json"),67108864));require(Boolean.TRUE.equals(completion.get("result_admitted"))&&Set.of("completed-original-cellchat-strength-workflow","completed-original-cellchat-strength-workflow-with-source-warnings").contains(text(completion.get("status"))),"No admitted complete CellChat result");
        Map<?,?> files=object(completion.get("files"));var expected=new java.util.HashSet<String>();for(var entry:files.entrySet()) {
            String name=member(text(entry.getKey()));expected.add(name);Pin wanted=fingerprint(entry.getValue(),Long.MAX_VALUE);require(wanted.equals(pin(root.resolve(name),wanted.bytes(),()->{})),"Changed/incomplete bundle file: "+name);
        }
        var actual=new java.util.HashSet<String>();try(var paths=Files.walk(root)){for(Path p:paths.toList()){require(!Files.isSymbolicLink(p),"Linked completed bundle entry refused");if(Files.isRegularFile(p))actual.add(root.relativize(p).toString().replace('\\','/'));}}
        expected.add("COMPLETED.json");require(actual.equals(expected),"Extra/missing complete bundle files");
        Map<?,?> state=object(json(root.resolve("analysis-status.json"),1048576));
        require(Boolean.TRUE.equals(state.get("result_admitted"))&&state.get("status").equals(completion.get("status"))&&ACTION.equals(state.get("required_action")),"Analysis/completion admission protocol differs");
        require(integer(object(json(root.resolve("process.json"),1048576)).get("exit_code"),0,0)==0,"Original process did not exit successfully");
        require(sha(resource("worker.R")).equals(sha(bytes(root.resolve("worker-source.R"),2097152)))&&sha(resource("SOURCE-LOCK.json")).equals(sha(bytes(root.resolve("source-lock.json"),2097152))),"Unsupported captured worker/source profile");
        Path source=root.resolve("source-request.json");Request request=CellChatStrengthRequest.parse(NextflowParameters.parse(utf8(bytes(source,1048576)),1048576,100000),root);
        var population=CellChatStrengthTables.inputs(root.resolve("inputs/expression.tsv"),root.resolve("inputs/metadata.tsv"),request);Map<String,Object> audit=CellChatStrengthAudit.verify(root.resolve("results"),request,population);
        require(NextflowJson.json(audit).equals(NextflowJson.json(json(root.resolve("provenance.json"),16777216))),"Native provenance replay differs");
        long fallbacks=((Number)audit.get("original_centrality_fallbacks")).longValue(),nonfinite=((Number)audit.get("original_nonfinite_centrality_fields")).longValue(),nonfiniteTests=((Number)audit.get("original_nonfinite_sample_test_fields")).longValue(),notTested=((Number)audit.get("not_tested_sample_comparisons")).longValue(),notComparable=((Number)audit.get("not_comparable_pooled_rows")).longValue();
        require(integer(state.get("original_centrality_fallbacks"),0,Long.MAX_VALUE)==fallbacks&&integer(state.get("original_nonfinite_centrality_fields"),0,Long.MAX_VALUE)==nonfinite,"Source-warning counts differ");
        require(integer(state.get("original_nonfinite_sample_test_fields"),0,Long.MAX_VALUE)==nonfiniteTests&&integer(state.get("not_tested_sample_comparisons"),0,Long.MAX_VALUE)==notTested&&integer(state.get("not_comparable_pooled_rows"),0,Long.MAX_VALUE)==notComparable,"Comparison coverage/warning counts differ");
        long pooledZeroSamples=((Number)audit.get("positive_pooled_rows_with_zero_selected_sample_scores")).longValue();require(integer(state.get("positive_pooled_rows_with_zero_selected_sample_scores"),0,Long.MAX_VALUE)==pooledZeroSamples,"Pooled/sample discrepancy count differs");
        Map<?,?> original=object(json(root.resolve("results/worker-report.json"),16777216));boolean sourceWarnings=fallbacks>0||nonfinite>0||nonfiniteTests>0||notTested>0||notComparable>0||pooledZeroSamples>0||((Number)audit.get("not_tested_strength_comparisons")).longValue()>0||!list(original.get("warnings")).isEmpty();
        require(text(state.get("status")).equals(sourceWarnings?"completed-original-cellchat-strength-workflow-with-source-warnings":"completed-original-cellchat-strength-workflow"),"Original source-warning disposition differs");
        for(String field:List.of("strength_comparison_rows","selected_strength_comparisons","not_comparable_pooled_strengths","not_tested_strength_comparisons","original_nonfinite_strength_test_fields","tested_strength_comparisons","original_strength_test_warning_count","missing_case_strengths"))require(integer(state.get(field),0,Long.MAX_VALUE)==((Number)audit.get(field)).longValue(),"Strength comparison census differs: "+field);
        String warning=utf8(bytes(root.resolve("WARNINGS.md"),16777216));require(warning.contains(ACTION)&&warning.contains(SCOPE)&&warning.startsWith("# "+state.get("status")),"Prominent warning/action text is missing");
        if(fallbacks>0)require(warning.contains("ORIGINAL CENTRALITY FALLBACK WARNING: "+fallbacks),"Source fallback warning is missing");
        if(nonfiniteTests>0)require(warning.contains("ORIGINAL NONFINITE SAMPLE TEST WARNING: "+nonfiniteTests),"Original nonfinite sample-test warning is missing");
        if(notTested>0||notComparable>0)require(warning.contains("COMPARISON COVERAGE WARNING: "+notTested),"Comparison coverage warning is missing");
        if(pooledZeroSamples>0)require(warning.contains("POOLED VERSUS SAMPLE WARNING: "+pooledZeroSamples),"Pooled/sample discrepancy warning is missing");
        for(String field:List.of("not_comparable_pooled_strengths","not_tested_strength_comparisons","original_nonfinite_strength_test_fields","original_strength_test_warning_count","missing_case_strengths"))if(((Number)audit.get(field)).longValue()>0)require(warning.contains(field+"="+audit.get(field)),"Required strength diagnostic is missing: "+field);
    }
}
