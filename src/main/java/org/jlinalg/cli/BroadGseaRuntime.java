/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.BroadGseaRecipe.*;
import static org.jlinalg.cli.BroadGseaFiles.*;

/** Supervises only the owned native Java worker; never invokes upstream software. */
final class BroadGseaRuntime {
    private BroadGseaRuntime(){ }
    static void launch(Path root,Caps caps,Map<String,Object> receipt)throws Exception{
        Path executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java");
        if(!Files.isRegularFile(executable)||!Files.isExecutable(executable))throw new Unavailable("Current Java executable is missing or execution is forbidden: "+executable);
        Path worker=root.resolve("worker");Files.createDirectory(worker.resolve("home"));Files.createDirectory(worker.resolve("tmp"));
        Path jar=jar();List<String> command=List.of(executable.toString(),"-Xms16m","-Xmx"+caps.heapMiB()+"m","-XX:ActiveProcessorCount=1",
            "-Dfile.encoding=UTF-8","-Duser.home="+worker.resolve("home"),"-Djava.io.tmpdir="+worker.resolve("tmp"),
            "-cp",jar.toString(),BroadGseaWorkflowCli.class.getName(),"--worker",root.toString());
        json(worker.resolve("runtime.json"),Map.of("command",command,"executable",pin(executable),"jar",pin(jar),
            "heap_mib",caps.heapMiB(),"timeout_seconds",caps.seconds(),"scope","Owned Java heap, wall time and observed live output growth; not OS RSS/CPU containment"));
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH");
        builder.environment().keySet().removeIf(k->remove.stream().anyMatch(x->x.equalsIgnoreCase(k)));
        var owned=new AtomicReference<Process>();var descendants=new ArrayList<ProcessHandle>();
        Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"gsea-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;long started=System.nanoTime();
        try{
            check(root,caps);
            try{process=builder.start();}catch(IOException error){throw new Unavailable("Current Java worker could not start; native GSEA analysis skipped: "+executable,error);}
            owned.set(process);process.getOutputStream().close();receipt.put("owned_worker_pid",process.pid());receipt.put("started_at",Instant.now().toString());
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)){
                process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                if(System.nanoTime()-started>TimeUnit.SECONDS.toNanos(caps.seconds()))throw new IOException("Native GSEA worker exceeded timeout_seconds; NO COMPLETE RESULT");
                check(root,caps);
            }
            check(root,caps);receipt.put("process_exit_code",process.exitValue());
            var status=capturedObject(root,"worker/status.json",RECIPE_BYTES);
            if(process.exitValue()==3&&text(status,"status").equals("SKIP3"))throw new Unavailable("Native worker skipped a named prerequisite; inspect worker/status.json and stderr.log");
            if(process.exitValue()!=0||!text(status,"status").equals("PASS_NATIVE_ENGINE"))throw new IOException("Native GSEA worker failed; inspect worker/status.json and stderr.log");
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
        finally{
            if(process!=null){
                if(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive))receipt.put("owned_process_cleanup",stop(process,descendants));
                receipt.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
                if(!process.isAlive())receipt.put("process_exit_code",process.exitValue());
            }
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}
        }
    }
    static void check(Path root,Caps caps)throws IOException{
        Map<String,Long> current=footprint(root,10000);if(current.size()+4>caps.files())throw new IOException("maximum_files exceeded, including reserved closure records");
        long growth=0;
        for(var e:current.entrySet()){
            if(!e.getKey().startsWith("results/")&&!e.getKey().startsWith("worker/"))continue;
            long extra=e.getValue();
            if(extra>caps.outputBytes()-growth)throw new IOException("maximum_output_bytes exceeded by live native worker output; NO COMPLETE RESULT");growth+=extra;
        }
    }
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants){
        boolean interrupted=Thread.interrupted();process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
        var handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());
        for(var p:handles)if(p.isAlive())p.destroy();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(var p:handles)if(p.isAlive())p.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        var result=new ArrayList<Map<String,Object>>();for(var p:handles)result.add(Map.of("pid",p.pid(),"alive_after_cleanup",p.isAlive()));if(interrupted)Thread.currentThread().interrupt();return result;
    }
}
