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
            case "software-capture", "software-check", "software-live" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,4_194_304));var limits=new SnakemakeOriginalCapture.Limits((int)SnakemakeOriginalFiles.integer(request.get("files"),1,1000000),SnakemakeOriginalFiles.integer(request.get("bytes"),1,1000000000000L),SnakemakeOriginalFiles.integer(request.get("file_bytes"),1,1000000000000L));Path owned=Path.of(SnakemakeOriginalFiles.text(request.get("owned"))),metadata=Path.of(SnakemakeOriginalFiles.text(request.get("metadata")));SnakemakeOriginalSoftwareCapture.Snapshot snapshot;
                if(args[0].equals("software-capture")){var roots=new java.util.TreeMap<String,Path>();for(var row:SnakemakeOriginalFiles.object(request.get("roots")).entrySet())roots.put(SnakemakeOriginalFiles.text(row.getKey()),Path.of(SnakemakeOriginalFiles.text(row.getValue())));snapshot=SnakemakeOriginalSoftwareCapture.capture(owned,roots,limits,check);SnakemakeOriginalFiles.writeJson(metadata,snapshot);}
                else {snapshot=SnakemakeOriginalSoftwareCapture.decode(SnakemakeOriginalFiles.json(metadata,67_108_864),limits);if(args[0].equals("software-live"))SnakemakeOriginalSoftwareCapture.verifyLive(snapshot,limits,check);else SnakemakeOriginalSoftwareCapture.verifyObjects(owned,snapshot,limits,check);}
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_LITERAL_SOFTWARE_PAYLOAD_ONLY_NOT_ANALYSIS","bytes",snapshot.bytes(),"members",snapshot.members())));
            }
            case "managed-parser-policy" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,4_194_304));
                if(!(request.get("allow_install") instanceof Boolean allow))throw new IllegalArgumentException("Require an explicit parser/installation permission");
                var result=SnakemakeOriginalManagedPolicy.audit(request.get("policy"),SnakemakeOriginalFiles.object(request.get("controller")),allow,SnakemakeOriginalFiles.text(request.get("working")),SnakemakeOriginalFiles.text(request.get("conda")),SnakemakeOriginalFiles.text(request.get("images")));
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_INDEPENDENT_MANAGED_PARSER_POLICY_ONLY","audit",result,"user_analysis_admitted",false)));
            }
            case "supervised-policy" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,4_194_304));var environment=new java.util.TreeMap<String,String>();
                for(var row:SnakemakeOriginalFiles.object(request.get("environment")).entrySet())environment.put(SnakemakeOriginalFiles.text(row.getKey()),SnakemakeOriginalFiles.text(row.getValue()));
                var limits=SnakemakeOriginalFiles.object(request.get("limits"));
                var selected=new SnakemakeOriginalProcess.Limits(SnakemakeOriginalFiles.integer(limits.get("wall_seconds"),1,3600)*1000,SnakemakeOriginalFiles.integer(limits.get("address_space_bytes"),16777216,1099511627776L),SnakemakeOriginalFiles.integer(limits.get("cpu_seconds"),1,3600),SnakemakeOriginalFiles.integer(limits.get("file_bytes"),1,1000000000000L),(int)SnakemakeOriginalFiles.integer(limits.get("open_files"),8,1048576),SnakemakeOriginalFiles.integer(limits.get("workspace_bytes"),1,1000000000000L),(int)SnakemakeOriginalFiles.integer(limits.get("files"),4,1000000),(int)SnakemakeOriginalFiles.integer(limits.get("processes"),1,4096),(int)SnakemakeOriginalFiles.integer(limits.get("threads"),1,1048576),SnakemakeOriginalFiles.integer(limits.get("sampled_rss_bytes"),1,1099511627776L),(int)SnakemakeOriginalFiles.integer(limits.get("poll_millis"),1,1000));
                var invocation=new SnakemakeOriginalManagedProcess.Invocation(Path.of(SnakemakeOriginalFiles.text(request.get("root"))),Path.of(SnakemakeOriginalFiles.text(request.get("working"))),Path.of(SnakemakeOriginalFiles.text(request.get("runtime"))),Path.of(SnakemakeOriginalFiles.text(request.get("providers"))),Path.of(SnakemakeOriginalFiles.text(request.get("controller_request"))),environment,selected);
                var result=SnakemakeOriginalManagedProcess.execute(invocation,check);System.out.println(SnakemakeOriginalJson.json(Map.of("status","SUPERVISED_ORIGINAL_PROCESS_POLICY_ONLY_NOT_WORKFLOW_ADMISSION","bound",result)));if(result.exitCode()!=0)System.exit(result.exitCode());
            }
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
            case "managed-bundle" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var result=SnakemakeOriginalManagedBundle.run(Path.of(SnakemakeOriginalFiles.text(request.get("recipe"))),Path.of(SnakemakeOriginalFiles.text(request.get("runtime"))),Path.of(SnakemakeOriginalFiles.text(request.get("out"))),check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_COMPLETE_MANAGED_WORKFLOW_EVIDENCE_NOT_PUBLIC_ADMISSION","manifest",result.manifest(),"logical_jobs",result.events().jobs().size())));
            }
            case "bundle" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var result=SnakemakeOriginalWorkflow.run(Path.of(SnakemakeOriginalFiles.text(request.get("recipe"))),Path.of(SnakemakeOriginalFiles.text(request.get("runtime"))),Path.of(SnakemakeOriginalFiles.text(request.get("out"))),check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_COMPLETE_DECLARED_LOCAL_WORKFLOW_BUNDLE_NOT_PAPER_REPRODUCTION","manifest",result.manifest(),"logical_jobs",result.events().jobs().size())));
            }
            case "verify-zip" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                Path source=Path.of(SnakemakeOriginalFiles.text(request.get("archive"))).toAbsolutePath().normalize();var before=SnakemakeOriginalFiles.pin(source,1_000_000_000_000L,check);
                try(var zip=new java.util.zip.ZipFile(source.toFile())){var names=new java.util.HashSet<String>();var entries=zip.entries();while(entries.hasMoreElements()){var entry=entries.nextElement();String name=entry.getName();SnakemakeOriginalFiles.member(entry.isDirectory()?name.substring(0,name.length()-1):name);if(!names.add(name))throw new java.io.IOException("Duplicate standalone archive member");}}
                try(var archive=java.nio.file.FileSystems.newFileSystem(source,Map.of())){
                    var verified=SnakemakeOriginalBundle.verify(archive.getPath("/"),check);
                    System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_PORTABLE_STANDALONE_ZIP_WORKFLOW_EVIDENCE","manifest",verified.manifest(),"logical_jobs",verified.events().jobs().size())));
                }
                if(!before.equals(SnakemakeOriginalFiles.pin(source,1_000_000_000_000L,check)))throw new java.io.IOException("Standalone archive changed during complete verification");
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
            case "process", "process-data" -> {
                var request=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root,1_048_576));
                var command=SnakemakeOriginalFiles.strings(request.get("command"));var environment=new java.util.TreeMap<String,String>();
                for(var row:SnakemakeOriginalFiles.object(request.get("environment")).entrySet())environment.put(SnakemakeOriginalFiles.text(row.getKey()),SnakemakeOriginalFiles.text(row.getValue()));
                Path owned=Path.of(SnakemakeOriginalFiles.text(request.get("owned"))),working=Path.of(SnakemakeOriginalFiles.text(request.get("working")));
                var limits=new SnakemakeOriginalProcess.Limits(SnakemakeOriginalFiles.integer(request.get("wall_millis"),1,60000),1_073_741_824,60,16_777_216,256,134_217_728,1000,16,128,536_870_912,10);
                var data=new java.util.ArrayList<Path>();if(args[0].equals("process-data"))for(Object path:SnakemakeOriginalFiles.list(request.get("software_data")))data.add(Path.of(SnakemakeOriginalFiles.text(path)));
                var result=args[0].equals("process-data")?SnakemakeOriginalProcess.executeManagedSession(command,environment,owned.resolve("process"),working,owned,limits,data,()->{},check):SnakemakeOriginalProcess.executeSession(command,environment,owned.resolve("process"),working,owned,limits,()->{},check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_OWNED_PROCESS_ONLY_NOT_ANALYSIS","result",result)));
            }
            default -> throw new IllegalArgumentException("Unknown qualification mode");
        }
    }
}
