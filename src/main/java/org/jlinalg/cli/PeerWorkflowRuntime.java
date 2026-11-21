/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit original-runtime binding, saved sources and owned process supervision. */
final class PeerWorkflowRuntime {
    private PeerWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static void sources(Path root,boolean compare)throws IOException {
        for(String name:List.of("SOURCE-PROFILE.json","peer_capture.cpp","CMakeLists.txt","peer_owned_worker.py","PEER-LICENSE.txt")) {
            byte[] raw;try(var stream=PeerWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/peer-original/"+name)) {
                if(stream==null)throw new IOException("Packaged PEER source profile is missing: "+name);raw=stream.readAllBytes();
            }
            Path path=root.resolve("source/"+name);if(!compare)Files.createDirectories(path.getParent());PeerWorkflowFiles.compareOrWrite(path,raw,compare);
        }
    }
    static String path(Path source,boolean wsl)throws IOException {
        String path=source.toAbsolutePath().normalize().toString().replace('\\','/');
        if(!wsl)return path;
        if(!System.getProperty("os.name").startsWith("Windows")||!path.matches("[A-Za-z]:/.*"))throw new Unavailable("WSL transport requires Windows drive-backed capture/runtime paths; declare a host-native runtime on this host");
        return "/mnt/"+Character.toLowerCase(path.charAt(0))+path.substring(2);
    }
    static Map<String,String> scientificOptions(Map<String,Object> r) {
        var result=new LinkedHashMap<String,String>();result.put("factors",r.get("factors").toString());result.put("seed",r.get("seed").toString());
        result.put("initialization",r.get("initialization").toString());result.put("add-mean",Boolean.TRUE.equals(r.get("add_mean"))?"1":"0");
        for(String key:List.of("max_iterations","tolerance","variance_tolerance","alpha_a","alpha_b","epsilon_a","epsilon_b","covariate_precision","verbose"))result.put(key.replace('_','-'),r.get(key).toString());return result;
    }
    static void run(Path root,Path requestDirectory,Map<String,Object> r,Map<String,Object> manifest)throws Exception {
        var runtime=object(r.get("runtime"),"runtime");Path executable=resolve(requestDirectory,text(runtime,"executable"));
        if(!Files.isRegularFile(executable,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact declared PEER observer runtime is unavailable: "+executable+"; build it from the pinned source and supply its SHA-256");
        if(!FollowupSupport.hash(executable).equals(text(runtime,"sha256")))throw new IOException("PEER runtime SHA-256 differs; preserve the original binding and supply the exact permitted executable");
        boolean wsl=runtime.containsKey("wsl_distribution");var originalCommand=new ArrayList<String>();
        originalCommand.add(path(executable,wsl));originalCommand.addAll(List.of("--data",path(root.resolve("worker/data.tsv"),wsl),"--out",path(root.resolve("worker/capture"),wsl)));
        if(r.containsKey("covariates"))originalCommand.addAll(List.of("--covariates",path(root.resolve("worker/covariates.tsv"),wsl)));
        if(r.containsKey("measurement_variance"))originalCommand.addAll(List.of("--measurement-variance",path(root.resolve("worker/variance.tsv"),wsl)));
        for(var option:scientificOptions(r).entrySet())originalCommand.addAll(List.of("--"+option.getKey(),option.getValue()));
        String token=UUID.randomUUID().toString();var command=new ArrayList<String>();
        if(wsl)command.addAll(List.of("wsl.exe","-d",text(runtime,"wsl_distribution"),"--","python3",path(root.resolve("source/peer_owned_worker.py"),true),"launch","--ledger",path(root.resolve("worker/owned-wsl.json"),true),"--token",token,"--command"));
        command.addAll(originalCommand);
        json(root.resolve("worker/runtime-bindings.json"),Map.of("executable",executable.toString(),"sha256",text(runtime,"sha256"),"transport",wsl?"explicit-wsl":"host-native","wsl_distribution",wsl?text(runtime,"wsl_distribution"):"","owned_wsl_token",wsl?token:"","request_directory",requestDirectory.toString()));
        json(root.resolve("worker/command.json"),Map.of("arguments",command,"original_arguments",originalCommand,"scientific_options",scientificOptions(r),"source_profile",r.get("profile"),"runtime_identity_scope","Caller-declared exact executable hash; not source authentication or a claim of identical C-library PRNGs on other hosts"));
        Files.createDirectory(root.resolve("worker/capture"));Map<String,Long> baseline=footprint(root);
        var builder=new ProcessBuilder(command).directory(root.resolve("worker").toFile()).redirectOutput(root.resolve("worker/stdout.log").toFile()).redirectError(root.resolve("worker/stderr.log").toFile());
        builder.environment().putAll(Map.of("LC_ALL","C","LANG","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        AtomicReference<Process> owned=new AtomicReference<>();Thread hook=new Thread(()->{
            Process process=owned.get();if(process!=null) {
                if(wsl&&Files.isRegularFile(root.resolve("worker/owned-wsl.json"),LinkOption.NOFOLLOW_LINKS))try{stopWsl(root,runtime,token);}catch(Exception ignored){/* Retained cleanup diagnostics identify an incomplete attempt. */}
                stop(process,new ArrayList<>());
            }
        },"peer-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;var descendants=new ArrayList<ProcessHandle>();long started=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException missing){throw new Unavailable("Declared PEER runtime/transport could not start; supply the exact runtime and permitted execution environment",missing);}
            owned.set(process);process.getOutputStream().close();manifest.put("worker_pid",process.pid());manifest.put("worker_started_at",Instant.now().toString());
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                if(System.nanoTime()-started>TimeUnit.SECONDS.toNanos(PeerWorkflowContracts.limit(r,"timeout_seconds")))throw new IOException("Original PEER timed out; owned process cleanup requested");
                capacity(root,baseline,PeerWorkflowContracts.limit(r,"maximum_output_bytes"));
            }
            capacity(root,baseline,PeerWorkflowContracts.limit(r,"maximum_output_bytes"));manifest.put("process_exit_code",process.exitValue());
            if(process.exitValue()==3)throw new Unavailable("The declared original PEER executable could not start in the selected environment; inspect the retained owned-worker unavailable receipt");
            if(process.exitValue()!=0)throw new IOException("Original PEER exited "+process.exitValue()+"; preserve raw model/state/history and worker logs. A reached iteration limit has no complete admitted result");
            if(wsl&&!Boolean.TRUE.equals(read(root.resolve("worker/owned-wsl.json.closed.json")).get("complete_cleanup")))throw new IOException("Owned WSL process group did not close completely");
        } finally {
            if(process!=null) {
                if(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive)||Thread.currentThread().isInterrupted()) {
                    if(wsl&&Files.isRegularFile(root.resolve("worker/owned-wsl.json"),LinkOption.NOFOLLOW_LINKS)) {
                        try{manifest.put("owned_wsl_cleanup",stopWsl(root,runtime,token));}catch(Exception failure){manifest.put("owned_wsl_cleanup_error",failure.toString());}
                    }
                    manifest.put("owned_process_cleanup",stop(process,descendants));
                }
                manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            }
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown hook owns cleanup. */}
        }
        if(!FollowupSupport.hash(executable).equals(text(runtime,"sha256")))throw new IOException("PEER executable changed during execution");
    }
    private static Map<String,Object> stopWsl(Path root,Map<String,Object> runtime,String token)throws Exception {
        boolean interrupted=Thread.interrupted();
        try {
            var command=List.of("wsl.exe","-d",text(runtime,"wsl_distribution"),"--","python3",path(root.resolve("source/peer_owned_worker.py"),true),"stop","--ledger",path(root.resolve("worker/owned-wsl.json"),true),"--token",token);
            Process cleanup=new ProcessBuilder(command).redirectOutput(root.resolve("worker/wsl-cleanup.json").toFile()).redirectError(root.resolve("worker/wsl-cleanup.stderr").toFile()).start();cleanup.getOutputStream().close();
            if(!cleanup.waitFor(20,TimeUnit.SECONDS)){cleanup.destroyForcibly();throw new IOException("Owned WSL cleanup timed out; preserve its exact ledger and inspect only the recorded worker group");}
            var result=read(root.resolve("worker/wsl-cleanup.json"));if(cleanup.exitValue()!=0||!Boolean.TRUE.equals(result.get("complete_cleanup")))throw new IOException("Owned WSL group still has live writers; inspect the recorded ledger and cleanup receipt");return result;
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    private static void capacity(Path root,Map<String,Long> baseline,long cap)throws IOException {
        long bytes=0;for(var entry:footprint(root).entrySet()){long extra=Math.max(0,entry.getValue()-baseline.getOrDefault(entry.getKey(),0L));if(extra>cap-bytes)throw new IOException("PEER maximum_output_bytes exceeded; no result admitted");bytes+=extra;}
    }
    static Map<String,Long> footprint(Path root)throws IOException {
        var result=new TreeMap<String,Long>();Path actualRoot=root.toRealPath();int[] directories={0};
        Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
            private void regular(Path path)throws IOException {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("PEER capture inventory interrupted");
                if(Files.isSymbolicLink(path)||!path.toRealPath().equals(actualRoot.resolve(root.relativize(path))))throw new IOException("Aliases/junctions/symbolic links are not capture members");
            }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException {regular(path);if(++directories[0]>1024)throw new IOException("PEER directory capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException {
                regular(path);if(!attributes.isRegularFile()||result.size()>=10000)throw new IOException("PEER file-count/type capacity exceeded");
                try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){result.put(root.relativize(path).toString().replace('\\','/'),channel.size());}return FileVisitResult.CONTINUE;
            }
        });return result;
    }
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants) {
        boolean interrupted=Thread.interrupted();process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
        var handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());for(var handle:handles)if(handle.isAlive())handle.destroy();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException failed){interrupted=true;}
        for(var handle:handles)if(handle.isAlive())handle.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException failed){interrupted=true;}
        var result=new ArrayList<Map<String,Object>>();for(var handle:handles)result.add(Map.of("pid",handle.pid(),"alive_after_cleanup",handle.isAlive()));if(interrupted)Thread.currentThread().interrupt();return result;
    }
}
