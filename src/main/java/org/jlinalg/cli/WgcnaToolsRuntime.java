/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Source-bound original R worker with an owned, time/output-limited process lifetime. */
final class WgcnaToolsRuntime {
    private WgcnaToolsRuntime() { }
    static byte[] resource(String name)throws IOException{try(var in=WgcnaToolsRuntime.class.getResourceAsStream("/org/jlinalg/omics/wgcna1721/"+name)){if(in==null)throw new IOException("Missing packaged WGCNA source reference");return in.readAllBytes();}}
    static void sources(Path root,boolean compare)throws IOException {
        for(String name:List.of("worker.R","SOURCE-PROFILE.json")){Path p=root.resolve("source/"+name);byte[] expected=resource(name);if(compare){if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(expected,Files.readAllBytes(p)))throw new IOException("WGCNA source binding changed: "+name);}else Files.write(p,expected,StandardOpenOption.CREATE_NEW);}
    }
    static void run(Path root,Path requestParent,Map<String,Object> r,Map<String,Object> manifest)throws IOException,InterruptedException {
        Path exe=resolve(requestParent,text(r,"rscript"));if(!Files.isRegularFile(exe)||!Files.isExecutable(exe))throw new SvaWorkflowRuntime.Unavailable("Declared Rscript unavailable: "+exe+"; restore the exact permitted runtime");
        var libraries=new ArrayList<String>();for(String name:strings(r.get("r_libraries"),"r_libraries",1,16)){Path p=resolve(requestParent,name);if(!Files.isDirectory(p))throw new SvaWorkflowRuntime.Unavailable("Declared R library unavailable: "+p);libraries.add(p.toString());}
        json(root.resolve("worker/runtime-bindings.json"),Map.of("rscript",exe.toString(),"libraries",libraries));Files.write(root.resolve("worker/runtime-paths.json"),RsemJson.bytes(libraries));
        var baseline=SvaWorkflowRuntime.footprint(root);Path worker=root.resolve("worker");var command=List.of(exe.toString(),"--vanilla","../source/worker.R");
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS").contains(k));
        builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","R_LIBS_SITE","","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        manifest.put("worker_command",command);manifest.put("rscript_sha256",FollowupSupport.hash(exe));
        AtomicReference<Process> owned=new AtomicReference<>();var descendants=new ArrayList<ProcessHandle>();Thread hook=new Thread(()->{Process process=owned.get();if(process!=null)stop(process,List.of());},"wgcna-owned-worker-cleanup");Runtime.getRuntime().addShutdownHook(hook);
        Process process=null;long start=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException e){throw new SvaWorkflowRuntime.Unavailable("Declared Rscript could not start; restore the runtime or retain skip",e);}owned.set(process);manifest.put("owned_worker_pid",process.pid());process.getOutputStream().close();
            do {
                OwnedProcesses.descendants(process).forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                if(System.nanoTime()-start>WgcnaToolsRequest.number(r,"timeout_seconds")*1000000000L)throw new IOException("Original WGCNA worker timeout; no complete result");
                outputCapacity(root,baseline,r);
            }while(!process.waitFor(100,TimeUnit.MILLISECONDS));
            outputCapacity(root,baseline,r);
            manifest.put("worker_exit_code",process.exitValue());var status=read(worker.resolve("status.json"));
            if(process.exitValue()==3&&"SKIP3".equals(status.get("status")))throw new SvaWorkflowRuntime.Unavailable(text(status,"cause"));
            if(process.exitValue()!=0||!"COMPLETE".equals(status.get("status")))throw new IOException("Original WGCNA failed: "+status.get("cause"));
        }finally{if(process!=null){if(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive))manifest.put("owned_cleanup",stop(process,descendants));manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));}owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}}
    }
    private static void outputCapacity(Path root,Map<String,Long> baseline,Map<String,Object> r)throws IOException {long added=0;for(var e:SvaWorkflowRuntime.footprint(root).entrySet()){long size=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));if(size>WgcnaToolsRequest.number(r,"maximum_output_bytes")-added)throw new IOException("Original WGCNA output capacity exceeded");added+=size;}}
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> earlier) {
        boolean interrupted=Thread.interrupted();var owned=new LinkedHashMap<Long,ProcessHandle>();for(var p:earlier)owned.put(p.pid(),p);OwnedProcesses.descendants(process).forEach(p->owned.put(p.pid(),p));var handles=new ArrayList<>(owned.values());Collections.reverse(handles);handles.add(process.toHandle());
        for(var p:handles)if(p.isAlive())p.destroy();long end=System.nanoTime()+2000000000L;while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<end)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(var p:handles)if(p.isAlive())p.destroyForcibly();end=System.nanoTime()+5000000000L;while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<end)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        if(interrupted)Thread.currentThread().interrupt();return handles.stream().map(p->Map.<String,Object>of("pid",p.pid(),"alive_after_cleanup",p.isAlive())).toList();
    }
}
