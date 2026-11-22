/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.SkatWorkflowContracts.require;

/** Owned root and observed-child lifetime; monitored limits are not OS job/RAM quotas. */
final class SkatWorkflowProcess {
    private SkatWorkflowProcess(){ }
    @FunctionalInterface interface Check {void verify()throws IOException;}
    private record Owned(ProcessHandle handle,Instant start) { }
    private static final class Guard {
        private Process process;private boolean closed;private final Map<Long,Owned> known=new LinkedHashMap<>();
        synchronized Process start(ProcessBuilder builder)throws IOException {
            require(!closed,"Worker launch cancelled before start");process=builder.start();observe(process.toHandle());discover();return process;
        }
        private void observe(ProcessHandle handle)throws IOException {
            if(!handle.isAlive())return;var start=handle.info().startInstant();
            require(start.isPresent()||!handle.isAlive(),"Cannot identify a live owned worker/observed child start time");
            if(start.isPresent())known.putIfAbsent(handle.pid(),new Owned(handle,start.get()));
        }
        synchronized void discover()throws IOException {
            if(process==null)return;observe(process.toHandle());for(ProcessHandle child:process.descendants().toList())observe(child);
            for(Owned owned:new ArrayList<>(known.values()))if(same(owned))for(ProcessHandle child:owned.handle().descendants().toList())observe(child);
        }
        private static boolean same(Owned owned){return owned.handle().isAlive()&&owned.handle().info().startInstant().filter(owned.start()::equals).isPresent();}
        synchronized List<Map<String,Object>> close(boolean force)throws IOException {
            closed=true;if(process==null)return List.of();IOException discovery=null;try{discover();}catch(IOException error){discovery=error;}
            boolean live=process.isAlive()||known.values().stream().anyMatch(Guard::same);var rows=new ArrayList<Map<String,Object>>();
            if(force||live){
                var reverse=new ArrayList<>(known.values());java.util.Collections.reverse(reverse);
                for(Owned owned:reverse)if(same(owned))owned.handle().destroy();if(process.isAlive())process.destroy();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(System.nanoTime()<deadline&&(process.isAlive()||known.values().stream().anyMatch(Guard::same)))try{Thread.sleep(20);}catch(InterruptedException error){Thread.currentThread().interrupt();break;}
                for(Owned owned:reverse)if(same(owned))owned.handle().destroyForcibly();if(process.isAlive())process.destroyForcibly();
                deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<deadline&&(process.isAlive()||known.values().stream().anyMatch(Guard::same)))try{Thread.sleep(20);}catch(InterruptedException error){Thread.currentThread().interrupt();break;}
            }
            for(Owned owned:known.values())rows.add(Map.of("pid",owned.handle().pid(),"start",owned.start().toString(),"same_identity_alive",same(owned)));
            if(discovery!=null)throw discovery;require(!process.isAlive()&&known.values().stream().noneMatch(Guard::same),"Owned worker/observed-child cleanup did not finish");return rows;
        }
        synchronized List<Map<String,Object>> identities(){var rows=new ArrayList<Map<String,Object>>();for(Owned owned:known.values())rows.add(Map.of("pid",owned.handle().pid(),"start",owned.start().toString(),"same_identity_alive",same(owned)));return rows;}
        synchronized boolean live(){return process!=null&&(process.isAlive()||known.values().stream().anyMatch(Guard::same));}
    }
    static void footprint(Path root,Map<String,Object> request)throws IOException {
        long total=0,count=0;try(var stream=Files.walk(root)){
            for(Path path:stream.toList()){
                require(!Files.isSymbolicLink(path),"A captured worker node became a symbolic link");
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){require(path.toRealPath().equals(path.toAbsolutePath().normalize()),"A capture directory resolves through an alias");continue;}
                require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Nonregular node in capture");long bytes=Files.size(path);require(bytes<=SkatWorkflowFiles.cap(request,"maximum_member_bytes"),"Worker member byte capacity exceeded");total=Math.addExact(total,bytes);require(total<=SkatWorkflowFiles.cap(request,"maximum_output_bytes")&&++count<=SkatWorkflowFiles.cap(request,"maximum_files"),"Worker capture byte/file capacity exceeded");
            }
        }
    }
    static int execute(List<String> command,Map<String,String> environment,Path root,Map<String,Object> request,Map<String,Object> manifest,Check check)throws Exception {
        var builder=new ProcessBuilder(List.copyOf(command)).directory(root.resolve("worker").toFile()).redirectOutput(root.resolve("worker/stdout.log").toFile()).redirectError(root.resolve("worker/stderr.log").toFile());builder.environment().clear();builder.environment().putAll(environment);
        Guard guard=new Guard();Thread hook=new Thread(()->{try{guard.close(true);}catch(IOException ignored){/* The retained running capture remains unadmitted on shutdown. */}},"skat-owned-worker-cleanup");Runtime.getRuntime().addShutdownHook(hook);long start=System.nanoTime();Process process=null;boolean normal=false;
        manifest.put("command",command);manifest.put("process_scope","Owned root and start-identified observed descendants;periodic workspace/member/file limits and wall time. No claim of OS job containment,hard aggregate RAM limit or observation of escaped children between samples.");
        try {
            footprint(root,request);check.verify();
            try{process=guard.start(builder);}catch(IOException error){throw new SkatWorkflowContracts.Unavailable("Declared Rscript could not start or execution is prohibited;dependent analysis skipped",error);}
            manifest.put("worker_pid",process.pid());manifest.put("worker_started_at",Instant.now().toString());process.getOutputStream().close();long timeout=TimeUnit.SECONDS.toNanos(SkatWorkflowFiles.cap(request,"timeout_seconds"));
            while(!process.waitFor(25,TimeUnit.MILLISECONDS)){
                guard.discover();check.verify();footprint(root,request);if(System.nanoTime()-start>timeout)throw new IOException("Original SKAT worker timed out;owned root and observed children are being closed");
            }
            guard.discover();check.verify();footprint(root,request);long settle=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(guard.live()&&System.nanoTime()<settle){Thread.sleep(20);guard.discover();}require(!guard.live(),"Original worker exited with a live observed child;capture is unadmitted and cleanup is requested");normal=true;manifest.put("process_exit_code",process.exitValue());return process.exitValue();
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new InterruptedIOException("Original SKAT execution interrupted;preserve its partial capture and retry NEW output");}
        finally{
            manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
            try{manifest.put("owned_processes",guard.close(!normal));}
            catch(IOException error){manifest.put("owned_processes",guard.identities());manifest.put("cleanup_error",error.toString());throw error;}
            finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}}
        }
    }
}
