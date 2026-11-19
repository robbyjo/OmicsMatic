/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Exact packaged worker/profile and bounded lifetime of the explicitly owned Python. */
final class ScanpyWorkflowRuntime {
    private ScanpyWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException {
        try(var stream=ScanpyWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/scanpy-workflow/"+name)) {
            if(stream==null)throw new IOException("Required packaged Scanpy source missing: "+name);return stream.readAllBytes();
        }
    }
    static void sources(Path root,boolean compare)throws IOException {
        for(String name:List.of("worker.py","SOURCE-PROFILE.json")) {
            byte[] bytes=resource(name);Path path=root.resolve("source/"+name);
            if(compare){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(bytes,Files.readAllBytes(path)))throw new IOException("Saved source differs from this verifier's packaged source: "+name);}
            else {Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);}
        }
    }
    static void run(Path root,Path parent,Map<String,Object> request,Map<String,Object> manifest)throws Exception {
        Path python=resolve(parent,text(request,"python"));
        if(!Files.isRegularFile(python)||!Files.isExecutable(python))throw new Unavailable("Declared Python unavailable or execution forbidden: "+python);
        Path worker=root.resolve("worker");json(worker.resolve("runtime-binding.json"),Map.of("python",python.toString(),"profile",ScanpyWorkflowContracts.PROFILE));
        List<String> command=List.of(python.toString(),"-X","utf8","-I","-B","../source/worker.py","..");
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        builder.environment().keySet().removeIf(k->k.startsWith("PYTHON")||k.startsWith("LC_")||k.equals("LANGUAGE"));
        builder.environment().putAll(Map.of("PYTHONUTF8","1","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1","NUMBA_NUM_THREADS","1"));
        manifest.put("command",command);manifest.put("python_sha256",FollowupSupport.hash(python));
        manifest.put("runtime_admission_scope","Exact recorded Python-package versions and installed Scanpy source bytes; no native-binary authentication or historical author-runtime equivalence.");
        AtomicReference<Process> owned=new AtomicReference<>();List<ProcessHandle> descendants=new ArrayList<>();
        Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"scanpy-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;long started=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Declared Python could not start; dependent analysis skipped",e);}
            owned.set(process);manifest.put("owned_worker_pid",process.pid());manifest.put("worker_started_at",Instant.now().toString());process.getOutputStream().close();
            long timeout=((Number)request.get("timeout_seconds")).longValue()*1_000_000_000L;
            long cap=((Number)request.get("maximum_output_bytes")).longValue();
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                checkOutput(worker,cap);if(System.nanoTime()-started>timeout)throw new IOException("Declared worker timeout exceeded; no complete result");
            }
            checkOutput(worker,cap);manifest.put("process_exit_code",process.exitValue());
            var state=read(worker.resolve("status.json"));keys(state,Set.of("status","results_admitted","required_action"));
            if(process.exitValue()==3&&"SKIP3_DEPENDENT_ANALYSIS_SKIPPED".equals(state.get("status"))&&!Boolean.TRUE.equals(state.get("results_admitted")))throw new Unavailable("Exact original Python dependency unavailable; read worker/report.json and restore the named version/source");
            if(process.exitValue()!=0||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted")))throw new IOException("Original Scanpy failed or admitted no result; read worker/report.json and retained partials");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
        finally {
            if(process!=null) {
                if(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive)||Thread.currentThread().isInterrupted())manifest.put("owned_process_cleanup",stop(process,descendants));
                manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            }
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}
        }
    }
    static Map<String,Long> footprint(Path root)throws IOException {return footprint(root,false);}
    private static Map<String,Long> footprint(Path root,boolean live)throws IOException {
        Map<String,Long> result=new TreeMap<>();
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException {
                if(!attributes.isRegularFile()||attributes.isSymbolicLink()||result.size()>=10000)throw new IOException("Nonregular/symbolic/excessive capture node: "+path);
                result.put(root.relativize(path).toString().replace('\\','/'),attributes.size());return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path,IOException error)throws IOException {
                // Matplotlib/JIT atomically replace temporary cache files while the worker runs.
                // Only that live observation tolerates a vanished node; completed seals are strict.
                if(live&&error instanceof NoSuchFileException)return FileVisitResult.CONTINUE;throw error;
            }
        });return result;
    }
    private static void checkOutput(Path root,long cap)throws IOException {long total=0;for(long bytes:footprint(root,true).values()){if(bytes>cap-total)throw new IOException("Original worker exceeded maximum_output_bytes; partials retained, no complete result");total+=bytes;}}
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants) {
        boolean interrupted=Thread.interrupted();process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
        List<ProcessHandle> handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());
        for(var handle:handles)if(handle.isAlive())handle.destroy();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(var handle:handles)if(handle.isAlive())handle.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        List<Map<String,Object>> result=new ArrayList<>();for(var handle:handles)result.add(Map.of("pid",handle.pid(),"alive_after_cleanup",handle.isAlive()));
        if(interrupted)Thread.currentThread().interrupt();return result;
    }
}
