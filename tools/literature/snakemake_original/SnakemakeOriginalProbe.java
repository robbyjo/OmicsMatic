/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Path;
import java.util.Map;

/** Qualification entrypoint for real runtime/event/process checks; not a user analysis CLI. */
public final class SnakemakeOriginalProbe {
    private SnakemakeOriginalProbe() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Use runtime ADOPTION or events CONTROL or process REQUEST.json");
        var root=Path.of(args[1]).toAbsolutePath().normalize();
        Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Probe interrupted");};
        switch(args[0]) {
            case "installation-policy" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,4_194_304));var owned=new java.util.HashSet<Long>();
                for(Object pid:SnakemakeOriginalFiles.list(request.get("owned_processes")))owned.add(SnakemakeOriginalFiles.integer(pid,1,Long.MAX_VALUE));
                if(!(request.get("allow_install") instanceof Boolean allow))throw new IllegalArgumentException("Require an explicit installation permission");
                var result=SnakemakeOriginalInstallationPolicy.audit(Path.of(SnakemakeOriginalFiles.text(request.get("records"))),Path.of(SnakemakeOriginalFiles.text(request.get("configuration"))),SnakemakeOriginalFiles.integer(request.get("controller_pid"),1,Long.MAX_VALUE),owned,SnakemakeOriginalFiles.text(request.get("run_id")),allow,SnakemakeOriginalFiles.text(request.get("python")),SnakemakeOriginalFiles.text(request.get("conda_prefix")),SnakemakeOriginalFiles.text(request.get("image_prefix")),4096,100000,4_194_304,67_108_864,check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_PROPAGATED_INSTALLATION_POLICY_ONLY_NOT_WORKFLOW_ADMISSION","audit",result)));
            }
            case "providers" -> {
                long start=System.nanoTime();var providers=SnakemakeOriginalDeployment.open(root,check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_COMPLETE_SELECTED_DEPLOYMENT_PROVIDERS_NOT_WORKFLOW_ADMISSION","providers",providers.providers(),"elf_objects",providers.elfObjects(),"external_paths",providers.externalPaths(),"elapsed_millis",(System.nanoTime()-start)/1_000_000)));
            }
            case "provider-tree" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,67_108_864));
                SnakemakeOriginalDeployment.verifyTree(Path.of(SnakemakeOriginalFiles.text(request.get("prefix"))),SnakemakeOriginalFiles.object(request.get("nodes")),check);
                System.out.println("PASS_COMPLETE_PROVIDER_TREE_ONLY_NOT_ANALYSIS");
            }
            case "runtime" -> {
                long start=System.nanoTime();var runtime=SnakemakeOriginalRuntime.open(root,check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_CURRENT_COMPLETE_SELECTED_RUNTIME_NOT_ANALYSIS","entries",runtime.entries(),"manifest",runtime.manifest(),"elapsed_millis",(System.nanoTime()-start)/1_000_000)));
            }
            case "events" -> {
                var receipt=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root.resolve("DRIVER.json"),4_194_304));
                var audit=SnakemakeOriginalEvents.audit(root.resolve("events"),receipt,100000,4_194_304,67_108_864,true,check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_COMPLETE_ORIGINAL_CONTROLLER_EVENTS_NOT_ANALYSIS","controller_pid",audit.controllerPid(),"run_id",audit.runId(),"streams",audit.streams(),"source_records",audit.sourceRecords(),"final_done",audit.finalDone(),"final_total",audit.finalTotal(),"error_diagnostics",audit.errorDiagnostics(),"jobs",audit.jobs().values().stream().sorted(java.util.Comparator.comparingLong(SnakemakeOriginalEvents.Job::id)).toList())));
            }
            case "payload" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var result=SnakemakeOriginalRuntime.verifyPayload(Path.of(SnakemakeOriginalFiles.text(request.get("manifest"))),Path.of(SnakemakeOriginalFiles.text(request.get("environment"))),(int)SnakemakeOriginalFiles.integer(request.get("entries"),1,200000),check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_COMPLETE_PAYLOAD_ONLY_NOT_SELECTED_RUNTIME_OR_ANALYSIS","system_paths",result.systemPaths().size(),"logical_bytes",result.logicalBytes())));
            }
            case "workflow" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var result=SnakemakeOriginalWorkflow.execute(Path.of(SnakemakeOriginalFiles.text(request.get("recipe"))),Path.of(SnakemakeOriginalFiles.text(request.get("runtime"))),Path.of(SnakemakeOriginalFiles.text(request.get("out"))),check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_DEVELOPMENT_CAPTURED_SOURCE_EXECUTION_NOT_FINAL_ADMISSION","logical_jobs",result.events().jobs().size(),"output_declarations",result.outputs().size())));
            }
            case "bundle" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var result=SnakemakeOriginalWorkflow.run(Path.of(SnakemakeOriginalFiles.text(request.get("recipe"))),Path.of(SnakemakeOriginalFiles.text(request.get("runtime"))),Path.of(SnakemakeOriginalFiles.text(request.get("out"))),check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_COMPLETE_DECLARED_LOCAL_WORKFLOW_BUNDLE_NOT_PAPER_REPRODUCTION","manifest",result.manifest(),"logical_jobs",result.events().jobs().size())));
            }
            case "verify" -> {
                var result=SnakemakeOriginalBundle.verify(root,check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_PORTABLE_BUNDLE_AUDIT_WITHOUT_ENGINE_EXECUTION","manifest",result.manifest(),"logical_jobs",result.events().jobs().size())));
            }
            case "replay" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var result=SnakemakeOriginalReplay.execute(Path.of(SnakemakeOriginalFiles.text(request.get("bundle"))),Path.of(SnakemakeOriginalFiles.text(request.get("runtime"))),Path.of(SnakemakeOriginalFiles.text(request.get("out"))),check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_GENUINE_ORIGINAL_ENGINE_RERUN_AND_DECLARED_COMPARISON","manifest",result.manifest(),"logical_jobs",result.events().jobs().size())));
            }
            case "process" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var command=SnakemakeOriginalFiles.strings(request.get("command"));var environment=new java.util.TreeMap<String,String>();
                for(var row:SnakemakeOriginalFiles.object(request.get("environment")).entrySet())environment.put(SnakemakeOriginalFiles.text(row.getKey()),SnakemakeOriginalFiles.text(row.getValue()));
                Path owned=Path.of(SnakemakeOriginalFiles.text(request.get("owned"))),working=Path.of(SnakemakeOriginalFiles.text(request.get("working")));
                var limits=new SnakemakeOriginalProcess.Limits(SnakemakeOriginalFiles.integer(request.get("wall_millis"),1,60000),1_073_741_824,60,16_777_216,256,134_217_728,1000,16,128,536_870_912,10);
                var result=SnakemakeOriginalProcess.executeSession(command,environment,owned.resolve("process"),working,owned,limits,()->{},check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_OWNED_PROCESS_ONLY_NOT_ANALYSIS","result",result)));
            }
            default -> throw new IllegalArgumentException("Unknown qualification mode");
        }
    }
}
