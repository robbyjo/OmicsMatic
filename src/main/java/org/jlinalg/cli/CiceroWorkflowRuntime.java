/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Original source capture, explicit R environment and owned worker lifetime. */
final class CiceroWorkflowRuntime {
    private CiceroWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException{
        try(var stream=CiceroWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/cicero-workflow/"+name)){
            if(stream==null)throw new IOException("Missing packaged Cicero co-accessibility source: "+name);return stream.readAllBytes();
        }
    }
    static void sources(Path stage,String version,boolean compare)throws IOException{
        var profile=object(NextflowParameters.parse(NextflowFiles.utf8(resource("SOURCE-PROFILE.json")),1048576,100000));
        for(var entry:object(profile.get("files")).entrySet()){
            String name=entry.getKey();if(!name.startsWith(version+"/"))continue;
            NextflowFiles.member(name);byte[] bytes=resource(name);var expected=NextflowFiles.fingerprint(entry.getValue(),1048576);
            require(bytes.length==expected.bytes()&&NextflowFiles.sha(bytes).equals(expected.sha256()),"Packaged original source identity mismatch: "+name);
            source(stage.resolve("source").resolve(name),bytes,compare);
        }
        for(String name:List.of("SOURCE-PROFILE.json","worker.R","NOTICE.txt"))source(stage.resolve("source").resolve(name),resource(name),compare);
    }
    private static void source(Path path,byte[] bytes,boolean compare)throws IOException{
        if(compare)require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Arrays.equals(NextflowFiles.bytes(path,1048576),bytes),"Captured original/worker source changed: "+path.getFileName());
        else{Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);}
    }
    static Map<String,Long> footprint(Path root,int maximumFiles)throws IOException{
        var result=new TreeMap<String,Long>();Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException {
                require(attributes.isRegularFile()&&!attributes.isSymbolicLink()&&result.size()<maximumFiles,"Worker/capture node or file-count capacity exceeded");
                result.put(root.relativize(path).toString().replace('\\','/'),attributes.size());return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path,IOException failure)throws IOException {
                if(failure instanceof NoSuchFileException)return FileVisitResult.CONTINUE;throw failure;
            }
            @Override public FileVisitResult postVisitDirectory(Path path,IOException failure)throws IOException {
                if(failure!=null&&!(failure instanceof NoSuchFileException))throw failure;return FileVisitResult.CONTINUE;
            }
        });return result;
    }
    static void run(Path stage,CiceroWorkflowRequest.Request request,Map<String,Object> manifest)throws Exception{
        if(!System.getProperty("os.name").startsWith("Windows"))throw new Unavailable("This captured Cicero profile requires Windows Java and original R4.6.1 Windows/private dependencies. A Linux runtime requires its own separately validated profile; no interop or edition fallback occurs.");
        Path worker=stage.resolve("worker"),rscript=request.rscript();
        if(!Files.isDirectory(request.rHome(),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Declared R_HOME unavailable: "+request.rHome());
        if(!Files.isRegularFile(rscript,LinkOption.NOFOLLOW_LINKS)||!Files.isExecutable(rscript))throw new Unavailable("Declared Rscript unavailable or execution forbidden: "+rscript);
        for(Path library:request.libraries())if(!Files.isDirectory(library,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Declared R library unavailable: "+library);
        Files.write(worker.resolve("runtime-paths.txt"),request.libraries().stream().map(Path::toString).toList(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        NextflowFiles.writeJson(worker.resolve("runtime-bindings.json"),Map.of("rscript",rscript.toString(),"libraries",request.libraries().stream().map(Path::toString).toList()));
        var executable=NextflowFiles.pin(rscript,67108864,()->{});var baseline=footprint(worker,request.outputFiles());
        List<String> command=List.of(rscript.toString(),"--vanilla","../source/worker.R");
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS");
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||remove.contains(k));
        builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        Path scratch=Files.createDirectory(worker.resolve("scratch"));for(String name:List.of("TMPDIR","TEMP","TMP"))builder.environment().put(name,scratch.toString());
        builder.environment().put("R_USER_CACHE_DIR",scratch.toString());
        builder.environment().put("R_HOME",request.rHome().toString());builder.environment().remove("LD_LIBRARY_PATH");
        manifest.put("command",command);manifest.put("rscript_sha256",executable.sha256());manifest.put("rscript_bytes",executable.bytes());
        var owned=new AtomicReference<Process>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"cicero-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;var descendants=new ArrayList<ProcessHandle>();long start=System.nanoTime();
        try{
            try{process=builder.start();}catch(IOException failure){throw new Unavailable("Declared Rscript could not start; dependent analysis skipped",failure);}
            owned.set(process);process.getOutputStream().close();manifest.put("owned_worker_pid",process.pid());manifest.put("worker_started_at",Instant.now().toString());
            while(!process.waitFor(500,TimeUnit.MILLISECONDS)){
                remember(process,descendants);
                require(System.nanoTime()-start<=TimeUnit.SECONDS.toNanos(request.timeout()),"Original Cicero co-accessibility timed out; owned worker tree cleanup requested");
                capacity(worker,baseline,request.outputBytes(),request.outputFiles());
            }
            remember(process,descendants);capacity(worker,baseline,request.outputBytes(),request.outputFiles());manifest.put("worker_exit_code",process.exitValue());
            var lines=Files.readAllLines(worker.resolve("worker-status.tsv"),StandardCharsets.UTF_8);
            require(lines.size()==2&&lines.get(0).equals("status\tdetail\trequired_action"),"Original worker produced no valid terminal status");
            String[] row=lines.get(1).split("\t",-1);require(row.length==3,"Invalid original terminal status width");
            if(process.exitValue()==3&&row[0].equals("SKIP3_DEPENDENCY_UNAVAILABLE"))throw new Unavailable(row[1]+". "+row[2]);
            require(process.exitValue()==0&&row[0].equals("completed-with-warnings"),"Original Cicero co-accessibility failed: "+row[1]);
            require(executable.equals(NextflowFiles.pin(rscript,67108864,()->{})),"Declared Rscript changed during original Cicero co-accessibility");
        }catch(InterruptedException failure){Thread.currentThread().interrupt();throw failure;}
        finally{
            if(process!=null){
                var cleanup=stop(process,descendants);manifest.put("owned_process_cleanup",cleanup);
                manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
                var receipt=new LinkedHashMap<String,Object>();receipt.put("command",command);receipt.put("owned_worker_pid",process.pid());
                receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));receipt.put("owned_process_cleanup",cleanup);
                if(!process.isAlive())receipt.put("worker_exit_code",process.exitValue());
                NextflowFiles.writeJson(worker.resolve("process-receipt.json"),receipt);
                owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}
                require(cleanup.stream().noneMatch(m->Boolean.TRUE.equals(m.get("alive_after_cleanup"))),"Owned Cicero co-accessibility process survived cleanup; no result admitted");
            }else{owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}}
        }
    }
    private static void capacity(Path root,Map<String,Long> baseline,long maximum,int maximumFiles)throws IOException{
        long total=0;for(var e:footprint(root,maximumFiles).entrySet()){long extra=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));require(extra<=maximum-total,"Original Cicero co-accessibility exceeds maximum_output_bytes; no partial result admitted");total+=extra;}
    }
    private static void remember(Process process,List<ProcessHandle> result){process.descendants().forEach(p->{if(result.stream().noneMatch(old->old.pid()==p.pid()))result.add(p);});}
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants){
        boolean interrupted=Thread.interrupted();remember(process,descendants);var handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());
        for(var handle:handles)if(handle.isAlive())handle.destroy();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(var handle:handles)if(handle.isAlive())handle.destroyForcibly();until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        var result=new ArrayList<Map<String,Object>>();for(var handle:handles)result.add(Map.of("pid",handle.pid(),"alive_after_cleanup",handle.isAlive()));
        if(interrupted)Thread.currentThread().interrupt();return result;
    }
}
