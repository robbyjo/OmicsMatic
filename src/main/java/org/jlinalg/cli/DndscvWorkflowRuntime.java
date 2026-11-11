/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Exact source capture and lifetime of the separate original dndscv R program. */
final class DndscvWorkflowRuntime {
    private DndscvWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException{try(var stream=DndscvWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/dndscv-workflow/"+name)){if(stream==null)throw new IOException("Missing packaged dndscv resource: "+name);return stream.readAllBytes();}}
    static void sources(Path stage,DndscvWorkflowRequest.Request r,boolean compare)throws IOException{
        var profile=object(NextflowParameters.parse(NextflowFiles.utf8(resource("SOURCE-PROFILE.json")),1048576,100000));require(text(profile.get("profile")).equals(DndscvWorkflowRequest.PROFILE),"Packaged source profile differs");
        if(!Files.isDirectory(r.source(),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact declared original dndscv source unavailable: "+r.source());long total=0;
        for(var entry:object(profile.get("files")).entrySet()){
            NextflowFiles.member(entry.getKey());var expected=NextflowFiles.fingerprint(entry.getValue(),600000000);require(expected.bytes()<=600000000-total,"Original source/data capture exceeds capacity");total+=expected.bytes();Path original=r.source().resolve(entry.getKey()),copy=stage.resolve("source/original").resolve(entry.getKey());
            try{
                require(expected.equals(NextflowFiles.pin(original,expected.bytes(),()->{})),"Exact source/data hash or size differs: "+entry.getKey());
                if(compare)require(expected.equals(NextflowFiles.pin(copy,expected.bytes(),()->{})),"Captured original source/data changed: "+entry.getKey());
                else{Files.createDirectories(copy.getParent());Files.copy(original,copy);require(expected.equals(NextflowFiles.pin(copy,expected.bytes(),()->{}))&&expected.equals(NextflowFiles.pin(original,expected.bytes(),()->{})),"Original source/data changed while capturing: "+entry.getKey());}
            }catch(IOException failure){throw new Unavailable("Exact original source/data unavailable or changed; dependent analysis skipped: "+entry.getKey(),failure);}
        }
        for(String name:List.of("SOURCE-PROFILE.json","worker.R","NOTICE.txt","GPL-3.txt")){Path path=stage.resolve("source").resolve(name);byte[] raw=resource(name);if(compare)require(Arrays.equals(raw,NextflowFiles.bytes(path,1048576)),"Captured dndscv worker/profile changed: "+name);else{Files.createDirectories(path.getParent());Files.write(path,raw,StandardOpenOption.CREATE_NEW);}}
    }
    static void run(Path stage,DndscvWorkflowRequest.Request r,Map<String,Object> manifest)throws Exception{
        if(!System.getProperty("os.name").startsWith("Windows"))throw new Unavailable("This exact dndscv profile requires Windows Java and R4.6.1 Windows/private dependencies; a Linux R profile needs separate validation. No edition/platform fallback occurs.");
        if(!Files.isDirectory(r.rHome(),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Declared R home is unavailable: "+r.rHome());Path executable=r.rscript();if(!Files.isRegularFile(executable,LinkOption.NOFOLLOW_LINKS)||!Files.isExecutable(executable))throw new Unavailable("Declared Rscript unavailable or execution forbidden: "+executable);for(Path p:r.libraries())if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Declared exact private R library unavailable: "+p);
        Path worker=stage.resolve("worker");Files.write(worker.resolve("runtime-paths.txt"),r.libraries().stream().map(Path::toString).toList(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);NextflowFiles.writeJson(worker.resolve("runtime-bindings.json"),Map.of("rscript",executable.toString(),"r_home",r.rHome().toString(),"libraries",r.libraries().stream().map(Path::toString).toList()));var executablePin=NextflowFiles.pin(executable,67108864,()->{});var baseline=CiceroWorkflowRuntime.footprint(worker,r.outputFiles());var parametersPin=NextflowFiles.pin(worker.resolve("parameters.json"),1048576,()->{});List<String> command=List.of(executable.toString(),"--vanilla","../source/worker.R");var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS","LD_LIBRARY_PATH");builder.environment().keySet().removeIf(k->k.startsWith("LC_")||remove.contains(k));builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1","R_HOME",r.rHome().toString()));Path scratch=Files.createDirectory(worker.resolve("scratch"));for(String key:List.of("TMPDIR","TEMP","TMP","R_USER_CACHE_DIR"))builder.environment().put(key,scratch.toString());
        manifest.put("command",command);manifest.put("rscript_sha256",executablePin.sha256());manifest.put("rscript_bytes",executablePin.bytes());var owned=new AtomicReference<Process>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"dndscv-owned-worker-cleanup");Runtime.getRuntime().addShutdownHook(hook);Process process=null;var descendants=new ArrayList<ProcessHandle>();long start=System.nanoTime();
        try{
            try{process=builder.start();}catch(IOException failure){throw new Unavailable("Declared Rscript could not start; dependent analysis skipped",failure);}owned.set(process);process.getOutputStream().close();manifest.put("owned_worker_pid",process.pid());
            while(!process.waitFor(500,TimeUnit.MILLISECONDS)){remember(process,descendants);require(System.nanoTime()-start<=TimeUnit.SECONDS.toNanos(r.timeout()),"Original dndscv workflow timed out; owned tree cleanup requested");capacity(worker,baseline,r);}
            remember(process,descendants);capacity(worker,baseline,r);manifest.put("worker_exit_code",process.exitValue());var lines=Files.readAllLines(worker.resolve("worker-status.tsv"),StandardCharsets.UTF_8);require(lines.size()==2&&lines.get(0).equals("status\tdetail\trequired_action"),"Original worker has no valid terminal status");String[] row=lines.get(1).split("\t",-1);require(row.length==3,"Invalid original terminal status width");if(process.exitValue()==3&&row[0].equals("SKIP3_DEPENDENCY_UNAVAILABLE"))throw new Unavailable(row[1]+". "+row[2]);require(process.exitValue()==0&&row[0].equals("completed-with-warnings"),"Original dndscv failed: "+row[1]);require(executablePin.equals(NextflowFiles.pin(executable,67108864,()->{})),"Rscript changed during original execution");require(parametersPin.equals(NextflowFiles.pin(worker.resolve("parameters.json"),1048576,()->{})),"Typed parameters changed during original execution");
        }catch(InterruptedException failure){Thread.currentThread().interrupt();throw failure;}
        finally{
            if(process!=null){var cleanup=stop(process,descendants);long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);manifest.put("owned_process_cleanup",cleanup);manifest.put("worker_elapsed_millis",elapsed);var receipt=new LinkedHashMap<String,Object>();receipt.put("command",command);receipt.put("owned_worker_pid",process.pid());receipt.put("elapsed_millis",elapsed);receipt.put("owned_process_cleanup",cleanup);if(!process.isAlive())receipt.put("worker_exit_code",process.exitValue());NextflowFiles.writeJson(worker.resolve("process-receipt.json"),receipt);owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}require(cleanup.stream().noneMatch(m->Boolean.TRUE.equals(m.get("alive_after_cleanup"))),"Owned dndscv process survived cleanup; no result admitted");}
            else{owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}}
        }
    }
    private static void capacity(Path worker,Map<String,Long> baseline,DndscvWorkflowRequest.Request r)throws IOException{long total=0;for(var e:CiceroWorkflowRuntime.footprint(worker,r.outputFiles()).entrySet()){long extra=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));require(extra<=r.outputBytes()-total,"Original dndscv output growth exceeds maximum_output_bytes");total+=extra;}}
    private static void remember(Process process,List<ProcessHandle> result){process.descendants().forEach(p->{if(result.stream().noneMatch(old->old.pid()==p.pid()))result.add(p);});}
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants){
        boolean interrupted=Thread.interrupted();remember(process,descendants);var handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());for(var handle:handles)if(handle.isAlive())handle.destroy();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}for(var handle:handles)if(handle.isAlive())handle.destroyForcibly();until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}var result=new ArrayList<Map<String,Object>>();for(var handle:handles)result.add(Map.of("pid",handle.pid(),"alive_after_cleanup",handle.isAlive()));if(interrupted)Thread.currentThread().interrupt();return result;
    }
}
