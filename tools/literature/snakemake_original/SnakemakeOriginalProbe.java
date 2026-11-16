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
            case "runtime" -> {
                long start=System.nanoTime();var runtime=SnakemakeOriginalRuntime.open(root,check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_CURRENT_COMPLETE_SELECTED_RUNTIME_NOT_ANALYSIS","entries",runtime.entries(),"manifest",runtime.manifest(),"elapsed_millis",(System.nanoTime()-start)/1_000_000)));
            }
            case "events" -> {
                var receipt=SnakemakeOriginalFiles.object(SnakemakeOriginalFiles.json(root.resolve("DRIVER.json"),4_194_304));
                var audit=SnakemakeOriginalEvents.audit(root.resolve("events"),receipt,100000,4_194_304,67_108_864,true,check);
                System.out.println(SnakemakeOriginalJson.json(Map.of("status","PASS_COMPLETE_ORIGINAL_CONTROLLER_EVENTS_NOT_ANALYSIS","controller_pid",audit.controllerPid(),"run_id",audit.runId(),"streams",audit.streams(),"source_records",audit.sourceRecords(),"final_done",audit.finalDone(),"final_total",audit.finalTotal(),"error_diagnostics",audit.errorDiagnostics(),"jobs",audit.jobs().values().stream().sorted(java.util.Comparator.comparingLong(SnakemakeOriginalEvents.Job::id)).toList())));
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
