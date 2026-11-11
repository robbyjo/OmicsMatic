/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Original-package source guard, declared R binding and owned worker lifetime. */
final class SeuratDifferentialRuntime {
    private SeuratDifferentialRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException {
        try(var input=SeuratDifferentialRuntime.class.getResourceAsStream("/org/jlinalg/omics/"+name)) {
            if(input==null)throw new IOException("Required packaged Seurat source resource unavailable: "+name);return input.readAllBytes();
        }
    }
    static void sources(Path root,boolean compare)throws IOException {
        var profile=object(SimpleJson.parse(new String(resource("seurat-differential-reference/SOURCE-PROFILE.json"),StandardCharsets.UTF_8)),"source profile");
        if(!PROFILE.equals(profile.get("profile")))throw new IOException("Packaged source profile differs");
        Map<String,Object> files=object(profile.get("files"),"source file pins");
        for(var entry:files.entrySet()) {
            String name=entry.getKey();Path rel=Path.of(name);if(rel.isAbsolute()||!rel.normalize().equals(rel)||name.contains("\\")||name.contains(".."))throw new IOException("Unsafe packaged source name");
            byte[] bytes=resource("seurat-differential-reference/"+name);if(!sha(bytes).equals(entry.getValue()))throw new IOException("Packaged reference source hash differs: "+name);
            source(root.resolve("source/reference/"+name),bytes,compare);
        }
        source(root.resolve("source/reference/SOURCE-PROFILE.json"),resource("seurat-differential-reference/SOURCE-PROFILE.json"),compare);
        source(root.resolve("source/seurat_differential.R"),resource("seurat_differential.R"),compare);
    }
    private static void source(Path path,byte[] bytes,boolean compare)throws IOException {
        if(compare){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(bytes,Files.readAllBytes(path)))throw new IOException("Captured source differs from the verifier's packaged reference: "+path.getFileName());}
        else {Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);}
    }
    static String sha(byte[] bytes) {try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static void run(Path root,Path requestDirectory,Map<String,Object> r,Map<String,Object> manifest)throws Exception {
        Path worker=root.resolve("worker"),rscript=resolve(requestDirectory,text(r,"rscript"));
        if(!Files.isRegularFile(rscript)||!Files.isExecutable(rscript))throw new Unavailable("Declared Rscript is unavailable or execution is forbidden: "+rscript);
        List<String> libraries=new ArrayList<>();for(Object value:list(r.get("r_libraries"),"r_libraries")) {
            Path p=resolve(requestDirectory,value.toString());if(!Files.isDirectory(p))throw new Unavailable("Declared R library is unavailable: "+p);libraries.add(p.toString());
        }
        Files.write(worker.resolve("runtime-paths.txt"),libraries,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        json(worker.resolve("runtime-bindings.json"),Map.of("rscript",rscript.toString(),"libraries",libraries,"request_directory",requestDirectory.toString(),"profile",PROFILE));
        Map<String,Long> baseline=footprint(worker);List<String> command=List.of(rscript.toString(),"--vanilla","../source/seurat_differential.R");
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS");
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||remove.contains(k));
        builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        manifest.put("command",command);manifest.put("rscript_sha256",FollowupSupport.hash(rscript));
        manifest.put("runtime_admission_scope","Exact R/Seurat versions, original function body/formal guards and recorded package identities; not full host/native-DLL source authentication");
        AtomicReference<Process> owned=new AtomicReference<>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"seurat-differential-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;List<ProcessHandle> descendants=new ArrayList<>();long started=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Declared Rscript could not start; dependent analysis skipped",e);}
            owned.set(process);manifest.put("owned_worker_pid",process.pid());manifest.put("worker_started_at",Instant.now().toString());process.getOutputStream().close();
            long timeout=((Number)r.get("timeout_seconds")).longValue()*1_000_000_000L;long outputCap=((Number)r.get("maximum_output_bytes")).longValue();
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                if(System.nanoTime()-started>timeout)throw new IOException("Original Seurat worker timed out; owned process tree cleanup was requested");
                checkOutputFootprint(worker,baseline,outputCap);
            }
            checkOutputFootprint(worker,baseline,outputCap);manifest.put("process_exit_code",process.exitValue());
            List<String> status=Files.readAllLines(worker.resolve("worker-status.tsv"),StandardCharsets.UTF_8);
            if(status.size()!=2||!status.get(0).equals("status\tdetail\trequired_action"))throw new IOException("Original worker did not produce a valid final status");
            String[] fields=status.get(1).split("\t",-1);if(fields.length!=3)throw new IOException("Invalid worker status width");
            if(process.exitValue()==3&&fields[0].equals("SKIP3_DEPENDENCY_UNAVAILABLE"))throw new Unavailable(fields[1]+". "+fields[2]);
            if(process.exitValue()!=0||!fields[0].equals("completed-with-warnings"))throw new IOException("Original Seurat failed: "+fields[1]);
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw interrupted;
        } finally {
            if(process!=null) {
                if(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive)||Thread.currentThread().isInterrupted())manifest.put("owned_process_cleanup",stop(process,descendants));
                manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            }
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}
        }
    }
    private static void checkOutputFootprint(Path worker,Map<String,Long> baseline,long cap)throws IOException {
        long bytes=0;for(var e:footprint(worker).entrySet()){long extra=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));if(extra>cap-bytes)throw new IOException("maximum_output_bytes exceeded by the original worker; no result admitted");bytes+=extra;}
    }
    static Map<String,Long> footprint(Path root)throws IOException {
        Map<String,Long> result=new TreeMap<>();try(var stream=Files.walk(root)) {
            for(Path path:stream.toList()) {
                if(Files.isSymbolicLink(path))throw new IOException("Symbolic links are not capture files");
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsupported nonregular capture node");
                if(result.size()>=10000)throw new IOException("Worker/capture file-count capacity exceeded");result.put(root.relativize(path).toString().replace('\\','/'),Files.size(path));
            }
        }return result;
    }
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants) {
        boolean interrupted=Thread.interrupted();process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
        List<ProcessHandle> handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());for(var handle:handles)if(handle.isAlive())handle.destroy();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(var handle:handles)if(handle.isAlive())handle.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        List<Map<String,Object>> result=new ArrayList<>();for(var handle:handles)result.add(Map.of("pid",handle.pid(),"alive_after_cleanup",handle.isAlive()));if(interrupted)Thread.currentThread().interrupt();return result;
    }
}
