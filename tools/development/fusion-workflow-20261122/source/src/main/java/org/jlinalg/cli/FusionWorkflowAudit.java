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
        long finite=integer(summary.get("finite_result_rows"),0,Long.MAX_VALUE),absent=integer(summary.get("no_result_rows"),0,selected),coloc=integer(summary.get("missing_requested_coloc_rows"),0,selected);
        var policy=object(request.get("admission"));require(selected>0||Boolean.TRUE.equals(policy.get("allow_empty")),"Empty source result is not admitted");
        require(absent+coloc==0||Boolean.TRUE.equals(policy.get("allow_no_result_rows")),"Original no-result rows are not admitted by the declared policy");
        if(request.get("operation").equals("ASSOCIATE"))require(finite+absent==selected,"Original association row counts differ");
        var original=read(root.resolve("worker/original-process.json"));integer(original.get("exit_code"),0,0);
        require(same(original.get("arguments"),FusionWorkflowRuntime.arguments(request)),"Original child arguments differ from scientific request");
        var args=GprofilerJson.read(bytes(root.resolve("worker/original-arguments.json"),67108864));require(same(args,FusionWorkflowRuntime.arguments(request)),"Original parser arguments differ");
        var dims=read(root.resolve("worker/input-dimensions.json"));long n=integer(dims.get("samples"),3,FusionWorkflowContracts.cap(request,"maximum_samples")),p=integer(dims.get("variants"),1,FusionWorkflowContracts.cap(request,"maximum_variants"));
        require(integer(dims.get("cells"),1,FusionWorkflowContracts.cap(request,"maximum_cells"))==n*p,"Prepared PLINK population dimensions differ");
        for(Object value:list(summary.get("original_export_files"))){String file=relative(text(value,"original export"));require(file.startsWith("out/")&&Files.isRegularFile(root.resolve("worker").resolve(file),LinkOption.NOFOLLOW_LINKS),"Missing original scientific export");}
        return summary;
    }
    static Map<String,Object> verify(Path root)throws IOException {
        var marker=read(root.resolve("COMPLETED.json"));require(Boolean.FALSE.equals(marker.get("authentication"))&&pin(root.resolve("manifest.json")).get("sha256").equals(marker.get("manifest_sha256")),"Missing/invalid completed manifest marker");
        var manifest=read(root.resolve("manifest.json"));require("fusion-original-workflow-v1".equals(manifest.get("workflow"))&&Boolean.TRUE.equals(manifest.get("results_admitted")),"No admitted original result");
        var request=read(root.resolve("canonical-request.json"));require(same(manifest.get("inventory"),inventory(root,request)),"Closed captured file/directory inventory differs");
        require(same(read(root.resolve("producer.json")).get("selected_class_sha256"),producer().get("selected_class_sha256")),"Selected producer classes differ; use the exact saved producer");
        var binding=read(root.resolve("request-binding.json"));require(same(binding.get("canonical"),request)&&same(binding.get("original"),GprofilerJson.read(bytes(root.resolve("source-request.json"),67108864))),"Original/canonical request binding differs");
        require(same(read(root.resolve("worker/request.json")),FusionWorkflowRuntime.workerRequest(request)),"Worker control differs from scientific request");
        require(same(read(root.resolve("source-profile.json")),FusionWorkflowRuntime.profile()),"Saved original source profile differs");
        for(var member:object(FusionWorkflowRuntime.profile().get("members")).entrySet()){
            var identity=object(member.getValue());require(same(pin(root.resolve("worker").resolve(relative(member.getKey()))),map("bytes",identity.get("bytes"),"sha256",identity.get("sha256"))),"Original/source wrapper bytes differ");
        }
        var captured=read(root.resolve("input-inventory.json"));for(var entry:captured.entrySet())require(same(pin(root.resolve(relative(entry.getKey()))),entry.getValue()),"Captured immutable input differs");
        require(same(read(root.resolve("runtime-before.json")),read(root.resolve("runtime-after.json"))),"Declared runtime changed during original execution");
        require(Files.readAllLines(root.resolve("worker/READY")).equals(List.of("FUSION_PREFLIGHT_9346C122_V1"))&&Files.readAllLines(root.resolve("worker/ACK")).equals(List.of("FUSION_ACK_9346C122_V1")),"Original pre-call handshake differs");
        var runtime=read(root.resolve("worker/runtime.json"));require(request.get("expected_R").equals(runtime.get("R")),"Saved runtime R version differs");
        var versions=map();for(Object value:list(runtime.get("packages"))){var packageRow=object(value);versions.put(packageRow.get("name").toString(),packageRow.get("version"));}
        for(var expected:object(request.get("expected_packages")).entrySet())require(expected.getValue().equals(versions.get(expected.getKey())),"Saved runtime package edition differs");
        require("COMPLETED".equals(FusionWorkflowRuntime.outcome(root).get("status")),"Original outcome is not complete");
        var summary=summary(root,request);require(same(summary,read(root.resolve("summary.json"))),"Admitted source-derived summary differs");
        for(Object value:list(summary.get("original_export_files"))){String path=value.toString();require(same(pin(root.resolve("worker").resolve(path)),pin(root.resolve("admitted").resolve(path))),"Admitted original export differs");}
        require(same(pin(root.resolve("worker/audit.rds")),pin(root.resolve("admitted/audit.rds"))),"Admitted parsed R object differs");
        var processes=list(manifest.get("owned_processes"));require(!processes.isEmpty(),"Missing owned-process identity evidence");for(Object value:processes){var process=object(value);require(Boolean.FALSE.equals(process.get("same_identity_alive")),"Recorded worker/observed child remained live");integer(process.get("pid"),1,Long.MAX_VALUE);java.time.Instant.parse(text(process.get("start"),"process start"));}
        integer(manifest.get("process_exit_code"),0,0);return summary;
    }
}
