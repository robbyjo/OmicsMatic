/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.FusionWorkflowFiles.*;

/** Offline saved integrity/relationships, not an independent RDS parse or refit. */
final class FusionWorkflowAudit {
    private FusionWorkflowAudit() { }
    static Map<String,Object> producer()throws IOException {
        var classes=new TreeMap<String,Object>();
        for(Class<?> type:List.of(FusionWorkflowCli.class,FusionWorkflowContracts.class,FusionWorkflowFiles.class,
            FusionWorkflowRuntime.class,FusionWorkflowProcess.class,FusionWorkflowAudit.class,OwnedProcesses.class,
            GprofilerJson.class,GprofilerWorkflowContracts.class))classPin(type,classes);
        return map("selected_class_sha256",classes,"java_version",System.getProperty("java.version"),"os_name",System.getProperty("os.name"),"authentication",false);
    }
    private static void classPin(Class<?> type,Map<String,Object> classes)throws IOException {
        String name=type.getName();try(var in=type.getResourceAsStream("/"+name.replace('.','/')+".class")){require(in!=null,"Restore producer class "+name);classes.put(name,sha(in.readAllBytes()));}
        for(Class<?> child:type.getDeclaredClasses())classPin(child,classes);
    }
    static Map<String,Object> summary(Path root,Map<String,Object> request)throws IOException {
        var summary=read(root.resolve("worker/summary.json"));require(FusionWorkflowContracts.REVISION.equals(summary.get("source_revision"))&&request.get("operation").equals(summary.get("operation")),"Original summary source/operation differs");
        integer(summary.get("original_exit_code"),0,0);long selected=integer(summary.get("selected_features"),0,FusionWorkflowContracts.cap(request,"maximum_features"));
        long finite=integer(summary.get("finite_result_rows"),0,Long.MAX_VALUE),absent=integer(summary.get("no_result_rows"),0,request.get("operation").equals("POSTPROCESS")?Long.MAX_VALUE:selected),coloc=integer(summary.get("missing_requested_coloc_rows"),0,selected);
        var policy=object(request.get("admission"));require(selected>0||Boolean.TRUE.equals(policy.get("allow_empty")),"Empty source result is not admitted");
        require(absent+coloc==0||Boolean.TRUE.equals(policy.get("allow_no_result_rows")),"Original no-result rows are not admitted by the declared policy");
        require(finite>0||absent>0||Boolean.TRUE.equals(policy.get("allow_empty")),"Empty original scientific exports are not admitted");
        if(request.get("operation").equals("ASSOCIATE")){
            require(finite+absent==selected,"Original association row counts differ");
            require(Boolean.TRUE.equals(summary.get("source_csv_rounding_verified"))&&Boolean.TRUE.equals(summary.get("unrounded_source_state_captured")),"Association lacks exact export/unrounded-source state checks");
            require(number(pin(root.resolve("worker/source-association-state.rds")),"bytes")>0,"Unrounded association state companion is empty");
            require(same(pin(root.resolve("worker/out/result")),pin(root.resolve("worker/source-format-main.tsv"))),"Unrounded state reconstruction differs from original main export");
            if(Files.exists(root.resolve("worker/out/result.MHC")))require(same(pin(root.resolve("worker/out/result.MHC")),pin(root.resolve("worker/source-format-mhc.tsv"))),"Unrounded state reconstruction differs from original MHC export");
            integer(summary.get("coloc_rounded_gate_disagreements"),0,selected);
        }
        var original=read(root.resolve("worker/original-process.json"));integer(original.get("exit_code"),0,0);
        require(same(original.get("arguments"),FusionWorkflowRuntime.arguments(request)),"Original child arguments differ from scientific request");
        var args=GprofilerJson.read(bytes(root.resolve("worker/original-arguments.json"),67108864));require(same(args,FusionWorkflowRuntime.arguments(request)),"Original parser arguments differ");
        var dims=read(root.resolve("worker/input-dimensions.json"));long n=integer(dims.get("samples"),3,FusionWorkflowContracts.cap(request,"maximum_samples")),p=integer(dims.get("variants"),1,FusionWorkflowContracts.cap(request,"maximum_variants"));
        require(integer(dims.get("cells"),1,FusionWorkflowContracts.cap(request,"maximum_cells"))==n*p,"Prepared PLINK population dimensions differ");
        var exports=new TreeSet<String>();for(Object value:list(summary.get("original_export_files"))){String file=relative(text(value,"original export"));require(exports.add(file)&&file.startsWith("out/")&&Files.isRegularFile(root.resolve("worker").resolve(file),LinkOption.NOFOLLOW_LINKS),"Missing/duplicate original scientific export");}
        require(exports.equals(fileNames(root.resolve("worker/out"),"out/")),"Original scientific export index is not closed");
        return summary;
    }
    static Map<String,Object> verify(Path root)throws IOException {
        var marker=read(root.resolve("COMPLETED.json"));require(Boolean.FALSE.equals(marker.get("authentication"))&&pin(root.resolve("manifest.json")).get("sha256").equals(marker.get("manifest_sha256")),"Missing/invalid completed manifest marker");
        var manifest=read(root.resolve("manifest.json"));require("fusion-original-workflow-v1".equals(manifest.get("workflow"))&&Boolean.TRUE.equals(manifest.get("results_admitted")),"No admitted original result");
        var request=read(root.resolve("canonical-request.json"));FusionWorkflowContracts.validateSaved(request);FusionWorkflowProcess.footprint(root,request);require(same(manifest.get("inventory"),inventory(root,request)),"Closed captured file/directory inventory differs");
        require(same(read(root.resolve("producer.json")).get("selected_class_sha256"),producer().get("selected_class_sha256")),"Selected producer classes differ; use the exact saved producer");
        var binding=read(root.resolve("request-binding.json"));require(same(binding.get("canonical"),request)&&same(binding.get("original"),GprofilerJson.read(bytes(root.resolve("source-request.json"),67108864))),"Original/canonical request binding differs");
        var originalRequest=object(binding.get("original"));String base=text(binding.get("source_base"),"original source base");
        if("RUN".equals(manifest.get("mode"))){
            keys(binding,Set.of("original","canonical","source_base"));require(same(request,FusionWorkflowContracts.recordedRequest(originalRequest,base)),"Canonical scientific request is not derived from original RUN settings");
            require(!Files.exists(root.resolve("replay-comparison.json"))&&!manifest.containsKey("replay_parent_manifest"),"RUN capture contains replay-only state");
        }else{
            require("REPLAY".equals(manifest.get("mode")),"Unknown saved workflow mode");keys(binding,Set.of("original","canonical","source_base","replay_source_canonical","replay_analysis_address"));
            keys(originalRequest,Set.of("schema_version","mode","analysis","out"));integer(originalRequest.get("schema_version"),1,1);require("REPLAY".equals(originalRequest.get("mode")),"Replay source mode differs");
            var prior=object(binding.get("replay_source_canonical"));FusionWorkflowContracts.validateSaved(prior);var expected=new LinkedHashMap<>(prior);expected.put("out",FusionWorkflowContracts.recordedPath(base,text(originalRequest.get("out"),"replay output")));
            require(same(expected,request)&&FusionWorkflowContracts.recordedPath(base,text(originalRequest.get("analysis"),"replay analysis")).equals(binding.get("replay_analysis_address")),"Replay changed scientific settings or parent address");
            var parentPin=object(manifest.get("replay_parent_manifest"));validPin(parentPin);var comparison=read(root.resolve("replay-comparison.json"));
            var after=map();for(Object value:list(read(root.resolve("worker/summary.json")).get("original_export_files")))after.put(value.toString(),pin(root.resolve("worker").resolve(relative(value.toString()))));
            require(same(after,comparison.get("replay")),"Replay comparison omits or changes actual scientific exports");boolean equal=same(comparison.get("parent"),comparison.get("replay"));
            require(Boolean.valueOf(equal).equals(comparison.get("scientific_exports_byte_identical"))&&Boolean.FALSE.equals(comparison.get("independent_refit")),"Replay comparison has contradictory equivalence claims");
            require(equal||Boolean.TRUE.equals(object(request.get("admission")).get("allow_replay_differences")),"Differing replay is not explicitly admitted");
        }
        var expectedCommand=new ArrayList<String>(List.of(request.get("rscript").toString(),"--vanilla","driver.R"));expectedCommand.addAll(FusionWorkflowRuntime.arguments(request));require(same(manifest.get("command"),expectedCommand),"Recorded actual worker command differs");
        var processAttempt=read(root.resolve("worker-process.json"));for(String key:List.of("command","process_exit_code","owned_processes"))require(same(manifest.get(key),processAttempt.get(key)),"Final process admission differs from terminal process attempt");
        require(same(read(root.resolve("worker/request.json")),FusionWorkflowRuntime.workerRequest(request)),"Worker control differs from scientific request");
        require(same(read(root.resolve("source-profile.json")),FusionWorkflowRuntime.profile()),"Saved original source profile differs");
        for(var member:object(FusionWorkflowRuntime.profile().get("members")).entrySet()){
            var identity=object(member.getValue());require(same(pin(root.resolve("worker").resolve(relative(member.getKey()))),map("bytes",identity.get("bytes"),"sha256",identity.get("sha256"))),"Original/source wrapper bytes differ");
        }
        var captured=read(root.resolve("input-inventory.json"));long inputBytes=0;for(var entry:captured.entrySet()){require(same(pin(root.resolve(relative(entry.getKey()))),entry.getValue()),"Captured immutable input differs");inputBytes=Math.addExact(inputBytes,number(object(entry.getValue()),"bytes"));}
        require(inputBytes<=FusionWorkflowContracts.cap(request,"maximum_input_bytes")&&captured.keySet().equals(fileNames(root.resolve("inputs"),"inputs/")),"Captured input closure/combined capacity differs");
        var expectedInputs=expectedInputs(request);var needed=list(GprofilerJson.read(bytes(root.resolve("worker/needed-files.json"),67108864)));
        require(!request.get("operation").equals("TRAIN")||needed.isEmpty(),"TRAIN contains association weight inputs");
        for(Object value:needed){String target=relative(text(object(value).get("target"),"weight input target"));require(target.startsWith("inputs/weights/"),"Unexpected captured weight input role");expectedInputs.add(target);}
        require(captured.keySet().equals(expectedInputs),"Captured inputs differ from required operation/selected weights");
        require(same(read(root.resolve("runtime-before.json")),read(root.resolve("runtime-after.json"))),"Declared runtime changed during original execution");
        runtimeClosure(root,request);
        require(Files.readAllLines(root.resolve("worker/READY")).equals(List.of("FUSION_PREFLIGHT_9346C122_V1"))&&Files.readAllLines(root.resolve("worker/ACK")).equals(List.of("FUSION_ACK_9346C122_V1")),"Original pre-call handshake differs");
        var runtime=read(root.resolve("worker/runtime.json"));require(request.get("expected_R").equals(runtime.get("R")),"Saved runtime R version differs");
        var versions=map();for(Object value:list(runtime.get("packages"))){var packageRow=object(value);versions.put(packageRow.get("name").toString(),packageRow.get("version"));}
        for(var expected:object(request.get("expected_packages")).entrySet())require(expected.getValue().equals(versions.get(expected.getKey())),"Saved runtime package edition differs");
        require("COMPLETED".equals(FusionWorkflowRuntime.outcome(root).get("status")),"Original outcome is not complete");
        var summary=summary(root,request);require(same(summary,read(root.resolve("summary.json"))),"Admitted source-derived summary differs");
        for(Object value:list(summary.get("original_export_files"))){String path=value.toString();require(same(pin(root.resolve("worker").resolve(path)),pin(root.resolve("admitted").resolve(path))),"Admitted original export differs");}
        require(same(pin(root.resolve("worker/audit.rds")),pin(root.resolve("admitted/audit.rds"))),"Admitted parsed R object differs");
        var admitted=new TreeSet<String>();admitted.add("audit.rds");for(Object value:list(summary.get("original_export_files")))admitted.add(value.toString());require(admitted.equals(fileNames(root.resolve("admitted"),"")),"Admitted scientific export inventory is not closed");
        String warnings=Files.readString(root.resolve("WARNINGS.md"),java.nio.charset.StandardCharsets.UTF_8);require(warnings.contains(FusionWorkflowCli.compatibilityWarnings(request)),"Missing explicit runtime/replay compatibility warnings");
        require(warnings.contains(FusionWorkflowCli.resultWarnings(summary)),"Missing original no-result/zero-p/permutation condition warnings");
        var processes=list(manifest.get("owned_processes"));require(!processes.isEmpty(),"Missing owned-process identity evidence");for(Object value:processes){var process=object(value);require(Boolean.FALSE.equals(process.get("same_identity_alive")),"Recorded worker/observed child remained live");integer(process.get("pid"),1,Long.MAX_VALUE);java.time.Instant.parse(text(process.get("start"),"process start"));}
        integer(manifest.get("process_exit_code"),0,0);return summary;
    }
    private static Set<String> fileNames(Path root,String prefix)throws IOException {
        require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"Missing captured directory "+root);var result=new TreeSet<String>();
        try(var files=Files.walk(root)){var cursor=files.iterator();while(cursor.hasNext()){Path p=cursor.next();if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))result.add(prefix+root.relativize(p).toString().replace('\\','/'));}}
        return result;
    }
    private static Set<String> expectedInputs(Map<String,Object> request)throws IOException {
        var input=object(request.get("inputs"));var result=new TreeSet<String>();String operation=request.get("operation").toString();
        if(operation.equals("TRAIN")){for(String ext:List.of("bed","bim","fam"))result.add("inputs/train."+ext);for(String key:List.of("phenotype","covariates"))if(input.containsKey(key))result.add("inputs/"+key);}
        else{
            result.add("inputs/sumstats");for(String ext:List.of("bed","bim","fam"))result.add("inputs/reference."+input.get("chromosome")+"."+ext);
            if(operation.equals("ASSOCIATE")){result.add("inputs/weights.pos");if(input.containsKey("panel_sizes"))result.add("inputs/panel-sizes");}
            else{result.add("inputs/association");if(input.containsKey("gene_annotation"))result.add("inputs/gene-annotation");}
        }return result;
    }
    private static void validPin(Map<String,Object> value)throws IOException {
        keys(value,Set.of("bytes","sha256"));integer(value.get("bytes"),0,Long.MAX_VALUE);require(text(value.get("sha256"),"SHA-256").matches("[0-9a-f]{64}"),"Invalid recorded file identity");
    }
    static void runtimeClosure(Path root,Map<String,Object> request)throws IOException {
        var saved=read(root.resolve("runtime-before.json"));var addresses=object(saved.get("addresses"));var files=object(saved.get("files"));require(files.size()<=50000&&addresses.size()<=100000,"Runtime fingerprint population exceeds limits");long total=0;
        for(Object value:files.values()){var identity=object(value);validPin(identity);total=Math.addExact(total,number(identity,"bytes"));}require(total<=4294967296L,"Runtime fingerprint byte capacity exceeded");
        var normalizedAddresses=new TreeMap<String,String>();for(var entry:addresses.entrySet()){String key=runtimeAddress(entry.getKey()),value=runtimeAddress(entry.getValue().toString()),previous=normalizedAddresses.putIfAbsent(key,value);require(previous==null||previous.equals(value),"Conflicting recorded runtime address aliases");}
        var normalizedFiles=new TreeSet<String>();for(String key:files.keySet())normalizedFiles.add(runtimeAddress(key));
        var executables=new ArrayList<>(object(request.get("binaries")).values());executables.add(request.get("rscript"));for(Object value:executables){String key=runtimeAddress(value.toString());require(normalizedAddresses.containsKey(key)&&normalizedFiles.contains(normalizedAddresses.get(key)),"Runtime fingerprints omit the actual declared executable: "+value);}
        var metadata=read(root.resolve("worker/runtime.json"));
        for(Object value:list(metadata.get("packages"))){String path=runtimeAddress(text(object(value).get("path"),"package root"));require(normalizedAddresses.containsKey(path),"Runtime fingerprints omit a loaded package root: "+path);String resolved=normalizedAddresses.get(path);require(normalizedFiles.stream().anyMatch(p->p.startsWith(resolved+"/")),"Loaded package has no fingerprinted files: "+path);}
        for(var adapter:Map.of("gcta","gcta_no_analysis_probe_exit1","gemma","gemma_working_directory_output").entrySet())if(Boolean.TRUE.equals(object(request.get("runtime_compatibility")).get(adapter.getValue())))require(Files.readAllLines(root.resolve("worker/"+adapter.getKey()+"-program.txt")).equals(List.of(object(request.get("binaries")).get(adapter.getKey()).toString())),"Compatibility adapter points to another executable");
    }
    private static String runtimeAddress(String value){return value.matches("[A-Za-z]:[/\\\\].*")||value.startsWith("\\\\")?value.replace('\\','/'):value;}
}
