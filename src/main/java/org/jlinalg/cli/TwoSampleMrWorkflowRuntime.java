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
final class TwoSampleMrWorkflowRuntime {
    private TwoSampleMrWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException {
        try(var input=TwoSampleMrWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/"+name)) {
            if(input==null)throw new IOException("Required packaged TwoSampleMR source resource unavailable: "+name);return input.readAllBytes();
        }
    }
    static void sources(Path root,boolean compare)throws IOException {
        var profile=object(SimpleJson.parse(new String(resource("twosamplemr-workflow/SOURCE-PROFILE.json"),StandardCharsets.UTF_8)),"source profile");
        if(!object(profile.get("profiles"),"profiles").keySet().equals(TwoSampleMrWorkflowContracts.PROFILES))throw new IOException("Packaged source profiles differ");
        for(var entry:object(profile.get("files"),"source pins").entrySet()) {
            String name=entry.getKey();Path relative=Path.of(name);
            if(relative.isAbsolute()||!relative.normalize().equals(relative)||name.contains("\\")||name.contains(".."))throw new IOException("Unsafe source resource path");
            byte[] bytes=resource("twosamplemr-workflow/"+name);var wanted=object(entry.getValue(),"source pin");
            if(!sha(bytes).equals(wanted.get("sha256"))||((Number)wanted.get("bytes")).longValue()!=bytes.length)throw new IOException("Packaged original source differs: "+name);
            source(root.resolve("source/reference/"+name),bytes,compare);
        }
        source(root.resolve("source/reference/SOURCE-PROFILE.json"),resource("twosamplemr-workflow/SOURCE-PROFILE.json"),compare);
        source(root.resolve("source/twosamplemr_workflow.R"),resource("twosamplemr_workflow.R"),compare);
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
        json(worker.resolve("runtime-bindings.json"),Map.of("rscript",rscript.toString(),"libraries",libraries,"request_directory",requestDirectory.toString(),"profile",text(r,"profile")));
        Map<String,Long> baseline=footprint(root);List<String> command=List.of(rscript.toString(),"--vanilla","../source/twosamplemr_workflow.R");
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS");
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||remove.contains(k));
        builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        manifest.put("command",command);manifest.put("rscript_sha256",FollowupSupport.hash(rscript));
        manifest.put("runtime_admission_scope","Exact R/TwoSampleMR versions, original function body/formal guards and recorded package identities; not full host/native-DLL source authentication");
        AtomicReference<Process> owned=new AtomicReference<>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"twosamplemr-workflows-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;List<ProcessHandle> descendants=new ArrayList<>();long started=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Declared Rscript could not start; dependent analysis skipped",e);}
            owned.set(process);manifest.put("owned_worker_pid",process.pid());manifest.put("worker_started_at",Instant.now().toString());process.getOutputStream().close();
            long timeout=((Number)r.get("timeout_seconds")).longValue()*1_000_000_000L;long outputCap=((Number)r.get("maximum_output_bytes")).longValue();
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                if(System.nanoTime()-started>timeout)throw new IOException("Original TwoSampleMR worker timed out; owned process tree cleanup was requested");
                checkOutputFootprint(root,baseline,outputCap,manifest);
            }
            checkOutputFootprint(root,baseline,outputCap,manifest);manifest.put("process_exit_code",process.exitValue());
            List<String> status=Files.readAllLines(worker.resolve("worker-status.tsv"),StandardCharsets.UTF_8);
            if(status.size()!=2||!status.get(0).equals("status\tdetail\trequired_action"))throw new IOException("Original worker did not produce a valid final status");
            String[] fields=status.get(1).split("\t",-1);if(fields.length!=3)throw new IOException("Invalid worker status width");
            if(process.exitValue()==3&&fields[0].equals("SKIP3_DEPENDENCY_UNAVAILABLE"))throw new Unavailable(fields[1]+". "+fields[2]);
            if(process.exitValue()!=0||!fields[0].equals("completed-with-warnings"))throw new IOException("Original TwoSampleMR failed: "+fields[1]);
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();throw interrupted;
        } finally {
            if(process!=null) {
                if(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive)||Thread.currentThread().isInterrupted())manifest.put("owned_process_cleanup",stop(process,descendants));
                manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            }
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}
        }
        if(!FollowupSupport.hash(rscript).equals(manifest.get("rscript_sha256")))throw new IOException("Declared Rscript changed during analysis; preserve the attempt and restore the exact runtime");
    }
    private static void checkOutputFootprint(Path root,Map<String,Long> baseline,long cap,Map<String,Object> manifest)throws IOException {
        Map<String,Long> current;
        try{current=footprint(root);}catch(NoSuchFileException race) {
            Path gone=race.getFile()==null?null:Path.of(race.getFile()).toAbsolutePath().normalize();
            if(gone==null||!gone.equals(root.resolve("worker/objects-final.rds.part").toAbsolutePath().normalize())||Files.exists(gone,LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(root.resolve("worker/objects.rds"),LinkOption.NOFOLLOW_LINKS))throw race;
            // The original object bundle is published by one known atomic rename.
            // Rescan once; completed seals and every other missing file stay strict.
            current=footprint(root);manifest.put("observed_final_object_publish_races",((Number)manifest.getOrDefault("observed_final_object_publish_races",0L)).longValue()+1);
        }
        long bytes=0;for(var e:current.entrySet()){long extra=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));if(extra>cap-bytes)throw new IOException("maximum_output_bytes exceeded by the original worker; no result admitted");bytes+=extra;}
    }
    static Map<String,Long> footprint(Path root)throws IOException {
        Map<String,Long> result=new TreeMap<>();Path actualRoot=root.toRealPath();int[] directories={0};
        Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
            private void regularPath(Path path)throws IOException {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("TwoSampleMR capture inventory interrupted");
                if(Files.isSymbolicLink(path)||!path.toRealPath().equals(actualRoot.resolve(root.relativize(path))))throw new IOException("Aliases, junctions and symbolic links are not capture members");
            }
            @Override public FileVisitResult preVisitDirectory(Path path,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                regularPath(path);if(++directories[0]>4096)throw new IOException("Worker/capture directory-count capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                regularPath(path);if(!attributes.isRegularFile())throw new IOException("Unsupported nonregular capture node");
                if(result.size()>=10000)throw new IOException("Worker/capture file-count capacity exceeded");
                // Directory-entry sizes can remain stale while Windows writers
                // hold a file open. Query its live EOF through a file handle.
                try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){result.put(root.relativize(path).toString().replace('\\','/'),channel.size());}
                return FileVisitResult.CONTINUE;
            }
        });return result;
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
