/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.MtagJson.*;

/** Supervised original MTAG1.0.8 with explicit inputs, source-sensitive native auditing and saved verification. */
public final class MtagToolsCli {
    private MtagToolsCli() { }
    static final String SCOPE="Original MTAG1.0.8, commit9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc, on an exact private Linux x86_64 Python2.7.15/NumPy1.16.4/pandas0.24.2/SciPy1.2.1 runtime. Explicit ordered two-to-eight-trait autosomal biallelic SNP summary statistics; common-SNP analysis, LDSC or provided residual covariance, GMM/numerical/provided genetic covariance, declared perfect/equal-heritability constraints and original maxFDR choices. Native checks retain complete input bytes, original pandas decimal parsing, identity/coordinate/allele operations, every trait-result row, full-matrix effect algebra and original SciPy-tail underflow. Result N is the original per-SNP effective N; it is not boosted GWAS-equivalent N. The original trait-level GWAS-equivalent maximum N remains in mtag.log. These definitions differ from per-SNP, case/control and imputation effective sizes; explicitly choose and label any downstream N instead of silently interchanging them. Fitting, full source QC and maxFDR formulas execute upstream. The separate preparation sidecar repeats original input parsing/QC without refitting. Native unconstrained covariance assessment uses exact binary64 rational PSD checks and may refuse an unassessable source matrix. Logical capacities are not OS process-memory quotas. System loader/libraries are not independently pinned. Source beta/SE mode is disabled upstream; ineffective no_allele_flipping, broken n_max, union meta_format and NPY covariance intake are not admitted. No paper settings, biological validity or whole-package equivalence are inferred.";
    static final String ACTION="Require exit0, result_admitted=true, a valid COMPLETED.json and successful mtag-tools --verify before interpreting results. Read WARNINGS.md, session.json, scientific-audit.json, native-audit/variant-dispositions.tsv, native-audit/input-parser-differences.tsv and unchanged original logs. SKIP3: SKIP dependent analysis when the exact runtime/capability is missing, unavailable or forbidden; provide permitted prerequisites using the exported helper and retry NEW paths. FAIL2: NO RESULT ADMITTED for invalid input, integrity, process, capacity or unassessable scientific results. Preserve the full attempt and unadmitted-original files, correct the named cause and retry NEW paths without repairing IDs, NaN/Inf, covariances, P values or scientific settings to hide failures. Hashes provide integrity, not producer authenticity.";
    private static final Set<String> FINAL_FILES=Set.of("manifest.json","COMPLETED.json");
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path out=null;MtagRequest.Request request=null;MtagRuntime.Runtime runtime=null;boolean ownedOutput=false;boolean[] originalStarted={false};
        Map<String,Object> state=map("status","FAIL2_NO_MTAG_RESULT_ADMITTED","result_admitted",false,"original_analysis_executed",false);
        try {
            if(args.length==1&&args[0].equals("--help")){output.println("mtag-tools --request JSON | --verify BUNDLE | --export-runtime-helper NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-helper")){
                Path target=Path.of(args[1]).toAbsolutePath().normalize();newDirectory(target);
                for(String name:List.of("prepare_mtag_runtime.py","mtag-workflow-lock.json","scipy121-LICENSE.txt"))Files.write(target.resolve(name),MtagRuntime.resource(name),StandardOpenOption.CREATE_NEW);
                Files.writeString(target.resolve("WARNINGS.md"),"# Runtime preparation only\n\n"+SCOPE+"\n\n"+ACTION+"\n\nRun the helper with Linux Python3.11+. Supply NEW --out and either --existing-environment or NEW native-Linux --environment-home. Installation/downloads require explicit --allow-install/--allow-download. Optional --archive-dir supplies offline archives named as in the lock; mtag-source.zip is the original source archive. Native Linux package cache and environment paths avoid the observed NTFS symlink failure. Setup performs no GWAS analysis.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                output.println("Exported MTAG setup helper/lock only; no download, installation or analysis.\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]).toAbsolutePath().normalize());output.println("PASS complete saved MTAG bundle and stated native contracts; no original-runtime fit, independent LDSC/maxFDR refit or producer authentication.");return 0;}
            need(args.length==2&&args[0].equals("--request"),"Require --request JSON, --verify BUNDLE or --help");
            Path recipe=MtagRuntime.canonical(Path.of(args[1]).toAbsolutePath().normalize(),false);Map<String,Object> recipePin=MtagRuntime.pin(recipe,1048576);
            request=MtagRequest.read(recipe);out=request.out();protectDeclaredRoots(request);newDirectory(out);ownedOutput=true;
            Files.writeString(out.resolve("WARNINGS.md"),"# MTAG warnings and result requirements\n\n"+SCOPE+"\n\n**"+ACTION+"**\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            MtagJson.write(out.resolve("STARTED.json"),map("status","RUNNING_MTAG_PREFLIGHT_ONLY","result_admitted",false));
            Files.copy(recipe,out.resolve("request-original.json"));need(recipePin.equals(MtagRuntime.pin(recipe,1048576))&&recipePin.equals(MtagRuntime.pin(out.resolve("request-original.json"),1048576)),"Original MTAG request changed during immutable copy");
            MtagCommand.capability(request);runtime=MtagRuntime.read(request.profile());
            Map<String,Object> runtimeProfilePin=MtagRuntime.pin(request.profile(),67108864);Files.copy(request.profile(),out.resolve("runtime-profile.json"));
            need(runtimeProfilePin.equals(MtagRuntime.pin(out.resolve("runtime-profile.json"),67108864)),"Runtime profile changed during immutable copy");
            MtagInputs.Staged staged=MtagInputs.stage(request,runtime,out);
            MtagJson.write(out.resolve("request-for-verification.json"),savedRequest(request,staged,out));
            Path raw=out.resolve("original");Files.createDirectory(raw);List<String> options=MtagCommand.options(request,staged,raw.resolve("mtag"));
            MtagJson.write(out.resolve("original-options.json"),options);
            var command=new ArrayList<String>();command.add(runtime.python().toString());command.add("-s");command.add("-B");command.add(runtime.source().resolve("mtag.py").toString());command.addAll(options);
            inventory(out,request.limits(),Set.of());
            supervise(command,out,"original",request.limits(),()->originalStarted[0]=true);
            MtagAudit.requireOriginalCompleted(raw,request.limits().outputBytes());
            Path worker=out.resolve("capture-worker.py");Files.write(worker,MtagRuntime.resource("capture_mtag_inputs.py"),StandardOpenOption.CREATE_NEW);
            supervise(List.of(runtime.python().toString(),"-s","-B",worker.toString(),runtime.source().toString(),out.resolve("original-options.json").toString(),out.resolve("captured").toString()),out,"capture",request.limits(),()->{});
            Path diagnostics=out.resolve("native-audit");Files.createDirectory(diagnostics);
            MtagRequest.Request saved=MtagRequest.read(out.resolve("request-for-verification.json"));Map<String,Object> scientific=MtagAudit.inspect(saved,out,diagnostics);
            MtagJson.write(out.resolve("scientific-audit.json"),scientific);MtagRuntime.verify(runtime);
            verifyOriginals(request,staged,recipePin,runtimeProfilePin);
            Map<String,Object> before=inventory(out,request.limits(),FINAL_FILES);
            need(object(MtagJson.read(out.resolve("scientific-audit.json"),16777216)).equals(MtagAudit.inspect(saved,out,null)),"Complete saved MTAG audit differs before result admission");
            need(before.equals(inventory(out,request.limits(),FINAL_FILES)),"MTAG bundle changed during complete saved-audit validation");
            verifyOriginals(request,staged,recipePin,runtimeProfilePin);
            state=map("status","PASS_SCOPED_ORIGINAL_MTAG_AND_NATIVE_AUDIT","result_admitted",true,"original_analysis_executed",true,
                "source_commit",runtime.metadata().get("source_commit"),"original_request",recipePin,"runtime_profile",runtimeProfilePin,"source_runtime_unchanged",true,
                "analysis_refitted_for_verification",false,"whole_paper_reproduced",false,"producer_authenticated",false);
            MtagJson.write(out.resolve("session.json"),state);
            appendWarnings(out,state,list(scientific.get("warnings")));
            Map<String,Object> manifest=map("schema_version",1L,"files",inventory(out,request.limits(),FINAL_FILES));MtagJson.write(out.resolve("manifest.json"),manifest);
            MtagJson.write(out.resolve("COMPLETED.json"),map("status","COMPLETE_SCOPED_MTAG_BUNDLE","result_admitted",true,"manifest",MtagRuntime.pin(out.resolve("manifest.json"),67108864)));
            output.println("PASS scoped original MTAG and complete native audit. Read "+out.resolve("WARNINGS.md")+" and run --verify before interpretation.\n"+ACTION);return 0;
        } catch(MtagRuntime.Unavailable missing) {
            state=map("status","SKIP3_MTAG_ANALYSIS_UNAVAILABLE","result_admitted",false,"original_analysis_executed",originalStarted[0],"cause",missing.getMessage());
            error.println("SKIP3: "+missing.getMessage()+"\nRequired action: "+ACTION);
        } catch(LinkageError missingJavaDependency) {
            state=map("status","SKIP3_MTAG_JAVA_DEPENDENCY_UNAVAILABLE","result_admitted",false,"original_analysis_executed",originalStarted[0],"cause",missingJavaDependency.toString());
            error.println("SKIP3: Required Java support classes are unavailable/incompatible: "+missingJavaDependency+"\nRequired action: use the complete documented development/canonical classpath and skip dependent analysis until permitted exact prerequisites are available. "+ACTION);
        } catch(Exception failure) {
            state=map("status","FAIL2_NO_MTAG_RESULT_ADMITTED","result_admitted",false,"original_analysis_executed",originalStarted[0],"cause",failure.toString());
            error.println("FAIL2: NO MTAG RESULT ADMITTED: "+failure+"\nRequired action: "+ACTION);
        }
        if(ownedOutput&&out!=null&&Files.isDirectory(out,LinkOption.NOFOLLOW_LINKS))try {
            if(Files.exists(out.resolve("COMPLETED.json")))Files.move(out.resolve("COMPLETED.json"),out.resolve("COMPLETED-unadmitted.json"));
            if(Files.exists(out.resolve("original")))Files.move(out.resolve("original"),out.resolve("unadmitted-original"));
            if(Files.exists(out.resolve("session.json")))Files.move(out.resolve("session.json"),out.resolve("session-before-finalization-failure.json"));
            MtagJson.write(out.resolve("session.json"),state);appendWarnings(out,state,List.of());
            if(request!=null)MtagJson.write(out.resolve("attempt-inventory.json"),map("files",inventory(out,request.limits(),Set.of("attempt-inventory.json"))));
        } catch(Exception diagnosticFailure){error.println("FAIL2 diagnostic finalization failed; preserve the entire attempt: "+diagnosticFailure);}
        return textUnchecked(state.get("status")).startsWith("SKIP3")?3:2;
    }
    private static String textUnchecked(Object value){return value==null?"":value.toString();}
    private static void protectDeclaredRoots(MtagRequest.Request r)throws IOException {
        // Inspect declared roots without executing software, before creating an
        // output directory that could otherwise mutate an existing runtime.
        if(Files.isRegularFile(r.profile())){
            Map<?,?> metadata=object(MtagJson.read(r.profile(),67108864));
            for(String key:List.of("environment","source"))if(metadata.get(key) instanceof String value){Path root=Path.of(value);if(root.isAbsolute())need(!r.out().startsWith(root)&&!root.startsWith(r.out()),"Output overlaps a declared immutable MTAG runtime root");}
        }
        if(text(r.covariance().get("ld_reference")).equals("provided")){
            Path root=MtagRequest.path(r.source().getParent(),r.covariance().get("ld_reference_dir"));
            need(!r.out().startsWith(root)&&!root.startsWith(r.out()),"Output overlaps the supplied LD-reference input directory");
        }
    }
    private static void newDirectory(Path path)throws IOException {
        need(!Files.exists(path,LinkOption.NOFOLLOW_LINKS),"Output/helper directory already exists; preserve it and choose NEW paths");
        Path ancestor=path.getParent();while(ancestor!=null&&!Files.exists(ancestor,LinkOption.NOFOLLOW_LINKS))ancestor=ancestor.getParent();
        need(ancestor!=null&&ancestor.toRealPath().equals(ancestor),"Output path traverses a linked/noncanonical ancestor");
        Files.createDirectories(path.getParent());Files.createDirectory(path);
    }
    private static Map<String,Object> savedRequest(MtagRequest.Request r,MtagInputs.Staged staged,Path out)throws IOException {
        Map<?,?> original=object(MtagJson.read(r.source(),1048576));var copy=new LinkedHashMap<String,Object>();for(var e:original.entrySet())copy.put(text(e.getKey()),e.getValue());
        copy.put("runtime_profile","runtime-profile.json");copy.put("output_dir",".");var traits=new ArrayList<Object>();
        for(int i=0;i<r.traits().size();i++)traits.add(map("name",r.traits().get(i).name(),"input",relative(out,staged.traits().get(i))));copy.put("traits",traits);
        var filters=new LinkedHashMap<String,Object>();for(var e:r.filters().entrySet())filters.put(text(e.getKey()),e.getValue());
        filters.put("include",staged.include().stream().map(p->relative(out,p)).toList());filters.put("exclude",staged.exclude().stream().map(p->relative(out,p)).toList());copy.put("filters",filters);
        var covariance=new LinkedHashMap<String,Object>();for(var e:r.covariance().entrySet())covariance.put(text(e.getKey()),e.getValue());
        covariance.put("sigma_file",staged.sigma()==null?null:relative(out,staged.sigma()));covariance.put("omega_file",staged.omega()==null?null:relative(out,staged.omega()));
        if(text(covariance.get("ld_reference")).equals("provided"))covariance.put("ld_reference_dir",relative(out,staged.ldReference()));copy.put("covariance",covariance);
        var fdr=new LinkedHashMap<String,Object>();for(var e:r.fdr().entrySet())fdr.put(text(e.getKey()),e.getValue());fdr.put("grid_file",staged.grid()==null?null:relative(out,staged.grid()));copy.put("fdr",fdr);return copy;
    }
    private static String relative(Path root,Path path){return root.relativize(path).toString().replace('\\','/');}
    private static void verifyOriginals(MtagRequest.Request r,MtagInputs.Staged staged,Map<String,Object> recipe,Map<String,Object> profile)throws IOException {
        need(recipe.equals(MtagRuntime.pin(r.source(),1048576))&&profile.equals(MtagRuntime.pin(r.profile(),67108864)),"Original request/runtime profile changed during analysis");
        for(var entry:staged.originals().entrySet())need(entry.getValue().equals(MtagRuntime.pin(entry.getKey(),r.limits().inputBytes())),"Original input changed during analysis: "+entry.getKey());
        for(var entry:staged.copies().entrySet())need(staged.originals().get(entry.getValue()).equals(MtagRuntime.pin(entry.getKey(),r.limits().inputBytes())),"Prepared input bytes changed during analysis");
    }
    private static void appendWarnings(Path out,Map<String,Object> state,List<?> warnings)throws IOException {
        StringBuilder text=new StringBuilder("\n\n## Final disposition: "+state.get("status")+"\n\n");
        if(state.get("cause")!=null)text.append("**NO RESULT ADMITTED: ").append(state.get("cause")).append("**\n\n");
        for(Object item:warnings)text.append("- ").append(item).append('\n');
        text.append("\nRequired action: ").append(ACTION).append('\n');
        Files.writeString(out.resolve("WARNINGS.md"),text.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private static Map<String,Object> inventory(Path root,MtagRequest.Limits limits,Set<String> excluded)throws IOException {
        var files=new TreeMap<String,Object>();long[] size={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {need(++count[0]<=limits.files()&&!attrs.isSymbolicLink(),"MTAG bundle directory/file capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {
                need(++count[0]<=limits.files()&&attrs.isRegularFile()&&!attrs.isSymbolicLink(),"Unlinked complete regular MTAG bundle files required");String name=relative(root,path);MtagRuntime.member(name);
                if(!excluded.contains(name)){Map<String,Object> value=MtagRuntime.pin(path,limits.outputBytes());size[0]=Math.addExact(size[0],(Long)value.get("bytes"));need(size[0]<=limits.outputBytes(),"Complete MTAG bundle byte capacity exceeded");files.put(name,value);}return FileVisitResult.CONTINUE;
            }
        });return new LinkedHashMap<>(files);
    }
    private static void monitorCapacity(Path root,MtagRequest.Limits limits)throws IOException {
        // A live source writer is expected to change its files. Monitoring
        // counts/lengths must not invoke stable-file hashing while it writes.
        long[] bytes={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {need(++count[0]<=limits.files()&&!attrs.isSymbolicLink(),"MTAG process output directory/file capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {
                need(++count[0]<=limits.files()&&attrs.isRegularFile()&&!attrs.isSymbolicLink(),"Unlinked regular MTAG process output required");bytes[0]=Math.addExact(bytes[0],attrs.size());need(bytes[0]<=limits.outputBytes(),"MTAG process output byte capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file,IOException cause)throws IOException {
                if(cause instanceof java.nio.file.NoSuchFileException)return FileVisitResult.CONTINUE;throw cause;
            }
        });
    }
    static void verify(Path root)throws IOException {
        MtagRuntime.canonical(root,true);Map<?,?> completion=object(MtagJson.read(root.resolve("COMPLETED.json"),1048576));keys(completion,"status","result_admitted","manifest");
        need(text(completion.get("status")).equals("COMPLETE_SCOPED_MTAG_BUNDLE")&&bool(completion.get("result_admitted")),"No completed admitted MTAG result exists");
        need(object(completion.get("manifest")).equals(MtagRuntime.pin(root.resolve("manifest.json"),67108864)),"Complete MTAG manifest hash differs");
        Map<?,?> manifest=object(MtagJson.read(root.resolve("manifest.json"),67108864));keys(manifest,"schema_version","files");integer(manifest.get("schema_version"),1,1);
        MtagRequest.Request r=MtagRequest.read(root.resolve("request-for-verification.json"));Map<?,?> expected=object(manifest.get("files"));
        need(expected.equals(inventory(root,r.limits(),FINAL_FILES)),"Complete saved MTAG file inventory/bytes differ");
        Map<?,?> session=object(MtagJson.read(root.resolve("session.json"),1048576));need(bool(session.get("result_admitted"))&&text(session.get("status")).equals("PASS_SCOPED_ORIGINAL_MTAG_AND_NATIVE_AUDIT"),"Saved MTAG session admits no result");
        need(object(MtagJson.read(root.resolve("scientific-audit.json"),16777216)).equals(MtagAudit.inspect(r,root,null)),"Saved MTAG scientific contracts differ");
        need(expected.equals(inventory(root,r.limits(),FINAL_FILES)),"Saved MTAG files changed during complete verification");
    }
    private static void supervise(List<String> command,Path out,String label,MtagRequest.Limits limits,Runnable onStarted)throws IOException,InterruptedException {
        MtagJson.write(out.resolve(label+"-process-command.json"),map("command",command,"shell",false));
        ProcessBuilder builder=new ProcessBuilder(command);builder.directory(out.toFile());builder.environment().clear();
        builder.environment().putAll(Map.of("PATH","/usr/bin:/bin","HOME",out.toString(),"LC_ALL","C","LANG","C","OPENBLAS_NUM_THREADS","1","OMP_NUM_THREADS","1","MKL_NUM_THREADS","1","PYTHONDONTWRITEBYTECODE","1","PYTHONNOUSERSITE","1","PYTHONHASHSEED","0"));
        builder.redirectOutput(out.resolve(label+".stdout").toFile());builder.redirectError(out.resolve(label+".stderr").toFile());
        long started=System.nanoTime();Process process=builder.start();onStarted.run();var descendants=new HashMap<Long,Instant>();String failure=null;boolean finished=false;
        try {
            while(true){process.descendants().forEach(p->p.info().startInstant().ifPresent(t->descendants.putIfAbsent(p.pid(),t)));
                if(process.waitFor(200,TimeUnit.MILLISECONDS)){finished=true;break;}
                if(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)>=limits.seconds()){failure="Original MTAG process exceeded wall_seconds";break;}
                try{monitorCapacity(out,limits);}catch(IOException capacity){failure=capacity.getMessage();break;}
            }
            if(!finished)throw new IOException(failure==null?"Original MTAG process was interrupted":failure);
            MtagJson.write(out.resolve(label+"-process.json"),map("command",command,"pid",process.pid(),"exit_code",(long)process.exitValue(),"source_process_finished",true,"elapsed_milliseconds",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)));
            need(process.exitValue()==0,"Original "+label+" process failed: exit"+process.exitValue()+"; preserve full stdout/stderr/source logs");
            for(var entry:descendants.entrySet())if(alive(entry.getKey(),entry.getValue()))throw new IOException("Original process left an owned descendant running; no result admission");
        } finally {
            if(process.isAlive())process.destroyForcibly();
            for(var entry:descendants.entrySet())if(alive(entry.getKey(),entry.getValue()))ProcessHandle.of(entry.getKey()).ifPresent(ProcessHandle::destroyForcibly);
            if(!finished){process.waitFor(30,TimeUnit.SECONDS);if(!Files.exists(out.resolve(label+"-process.json")))MtagJson.write(out.resolve(label+"-process.json"),map("command",command,"pid",process.pid(),"source_process_finished",!process.isAlive(),"admitted",false,"cause",failure==null?"interrupted":failure));}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            while(descendants.entrySet().stream().anyMatch(e->alive(e.getKey(),e.getValue()))&&System.nanoTime()<deadline)Thread.sleep(50);
            var remaining=new ArrayList<Long>();for(var entry:descendants.entrySet())if(alive(entry.getKey(),entry.getValue()))remaining.add(entry.getKey());
            MtagJson.write(out.resolve(label+"-process-cleanup.json"),map("owned_descendants",(long)descendants.size(),"remaining_owned_pids",remaining,"parent_still_alive",process.isAlive()));
            need(remaining.isEmpty()&&!process.isAlive(),"Owned source processes could not be confirmed stopped; preserve the attempt and inspect named PIDs before any retry");
        }
    }
    private static boolean alive(long pid,Instant started){return ProcessHandle.of(pid).filter(p->p.isAlive()&&p.info().startInstant().map(started::equals).orElse(false)).isPresent();}
}
