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
final class TensorQtlWorkflowRuntime {
    private TensorQtlWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException {
        try(var input=TensorQtlWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/"+name)) {
            if(input==null)throw new IOException("Required packaged tensorqtl source resource unavailable: "+name);return input.readAllBytes();
        }
    }
    static void sources(Path root,boolean compare)throws IOException {
        byte[] raw=resource("tensorqtl-workflow/SOURCE-PROFILES.json");
        source(root.resolve("source/SOURCE-PROFILES.json"),raw,compare);
        var all=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)),"profiles");
        for(Object value:object(all.get("profiles"),"profiles").values()) {
            var profile=object(value,"profile");String name=text(profile,"runtime_files_resource");
            if(!name.matches("[0-9.]+-RUNTIME-FILES[.]json[.]gz"))throw new IOException("Unsafe runtime resource");
            byte[] bytes=resource("tensorqtl-workflow/"+name);var wanted=object(profile.get("runtime_files"),"runtime file profile");
            if(bytes.length!=((Number)wanted.get("bytes")).longValue()||!sha(bytes).equals(wanted.get("sha256")))throw new IOException("Packaged runtime file profile differs");
            source(root.resolve("source/"+name),bytes,compare);
        }
        source(root.resolve("source/tensorqtl_workflow.py"),resource("tensorqtl_workflow.py"),compare);
    }
    static void upstream(Path root,Path parent,Map<String,Object> r,boolean compare)throws IOException {
        var profile=TensorQtlWorkflowContracts.profile(r);var expected=object(object(profile.get("original_source"),"original source").get("files"),"original files");
        Path target=root.resolve("source/upstream");
        Path original=compare?target:resolve(parent,text(r,"source_directory"));
        if(!Files.isDirectory(original))throw new Unavailable("Required permitted original tensorQTL source directory is unavailable: "+original);
        if(!compare)Files.createDirectory(target);
        for(var entry:expected.entrySet()) {
            String name=entry.getKey();Path relative=Path.of(name);
            if(relative.isAbsolute()||!relative.normalize().equals(relative)||name.contains("\\")||name.contains(".."))throw new IOException("Unsafe original source path");
            Path path=original.resolve(relative);var wanted=object(entry.getValue(),"source pin");
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Required permitted original source file is missing: "+name);
            if(Files.size(path)!=((Number)wanted.get("bytes")).longValue()||!FollowupSupport.hash(path).equals(wanted.get("sha256")))throw new IOException("Original tensorQTL source changed: "+name+"; restore the exact tagged source");
            if(!compare){Path copy=target.resolve(relative);Files.createDirectories(copy.getParent());Files.copy(path,copy);if(!FollowupSupport.hash(copy).equals(wanted.get("sha256")))throw new IOException("Original source changed during capture");}
        }
        if(!footprint(target).keySet().equals(expected.keySet()))throw new IOException("Captured original source population differs");
    }
    private static void source(Path path,byte[] bytes,boolean compare)throws IOException {
        if(compare){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(bytes,Files.readAllBytes(path)))throw new IOException("Captured source differs from the verifier's packaged reference: "+path.getFileName());}
        else {Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);}
    }
    static String sha(byte[] bytes) {try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static void run(Path root,Path requestDirectory,Map<String,Object> r,Map<String,Object> manifest)throws Exception {
        Path worker=root.resolve("worker"),python=resolve(requestDirectory,text(r,"python"));
        if(!Files.isRegularFile(python)||!Files.isExecutable(python))throw new Unavailable("Declared permitted Python executable is unavailable: "+python);
        Path prefix=python.toRealPath().getParent().getParent();var binding=new LinkedHashMap<String,Object>();
        binding.put("python",python.toString());binding.put("python_prefix",prefix.toString());binding.put("profile",r.get("profile"));binding.put("device",r.get("device"));
        var libraries=new ArrayList<String>();Path rhome=null;
        if(r.containsKey("r_home")) {
            rhome=resolve(requestDirectory,text(r,"r_home"));if(!Files.isDirectory(rhome))throw new Unavailable("Declared permitted R home is unavailable: "+rhome);
            binding.put("r_home",rhome.toString());
            for(Object path:list(r.getOrDefault("r_libraries",List.of(rhome.resolve("library").toString())),"r_libraries")) {
                Path lib=resolve(requestDirectory,path.toString());if(!Files.isDirectory(lib))throw new Unavailable("Declared permitted R library is unavailable: "+lib);libraries.add(lib.toString());
            }
        }
        binding.put("libraries",libraries);json(worker.resolve("runtime-bindings.json"),binding);
        Files.createDirectory(worker.resolve("objects"));Files.createDirectory(root.resolve("results/original"));Files.writeString(worker.resolve("empty-R-profile"),"",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Map<String,Long> baseline=footprint(root);List<String> command=List.of(python.toString(),"-I","-B","-X","pycache_prefix="+worker.resolve("never-written-bytecode"),"../source/tensorqtl_workflow.py");
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("LANG","LANGUAGE","PYTHONPATH","PYTHONHOME","LD_LIBRARY_PATH","LD_PRELOAD","R_HOME","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS","RPY2_CFFI_MODE");
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||remove.contains(k));
        builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        if(r.get("device").equals("CPU"))builder.environment().put("CUDA_VISIBLE_DEVICES","");
        var paths=new ArrayList<String>();paths.add(prefix.resolve("bin").toString());var ld=new ArrayList<String>();
        if(rhome!=null) {
            builder.environment().put("R_HOME",rhome.toString());builder.environment().put("R_LIBS_USER",String.join(File.pathSeparator,libraries));
            for(String variable:List.of("R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER"))builder.environment().put(variable,worker.resolve("empty-R-profile").toString());
            builder.environment().put("RPY2_CFFI_MODE",text(TensorQtlWorkflowContracts.profile(r),"rpy2_cffi_mode"));paths.add(rhome.resolve("bin").toString());ld.add(rhome.resolve("lib").toString());ld.add(rhome.getParent().toString());
        }
        paths.add(builder.environment().getOrDefault("PATH",""));builder.environment().put("PATH",String.join(File.pathSeparator,paths));ld.add(prefix.resolve("lib").toString());builder.environment().put("LD_LIBRARY_PATH",String.join(File.pathSeparator,ld));
        manifest.put("command",command);manifest.put("python_sha256",FollowupSupport.hash(python));manifest.put("runtime_admission_scope","Exact declared Python/package/source editions and critical Python/native/R file bytes, original loaded function code and pre/post guards; no full host, linked-system-library or driver attestation");
        AtomicReference<Process> owned=new AtomicReference<>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"tensorqtl-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;List<ProcessHandle> descendants=new ArrayList<>();long started=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Declared Python could not start; dependent analysis skipped",e);}
            owned.set(process);manifest.put("owned_worker_pid",process.pid());manifest.put("worker_started_at",Instant.now().toString());process.getOutputStream().close();
            long timeout=((Number)r.get("timeout_seconds")).longValue()*1_000_000_000L,cap=((Number)r.get("maximum_output_bytes")).longValue();
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                OwnedProcesses.descendants(process).forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                if(System.nanoTime()-started>timeout)throw new IOException("Original tensorQTL timed out; owned process-tree cleanup requested");
                checkOutputFootprint(root,baseline,cap,manifest,true);
            }
            checkOutputFootprint(root,baseline,cap,manifest,false);manifest.put("process_exit_code",process.exitValue());
            List<String> lines=Files.readAllLines(worker.resolve("worker-status.tsv"),StandardCharsets.UTF_8);
            if(lines.size()!=2||!lines.get(0).equals("status\tdetail\trequired_action"))throw new IOException("Original worker did not emit a complete final status");String[] fields=lines.get(1).split("\t",-1);if(fields.length!=3)throw new IOException("Invalid worker status width");
            if(process.exitValue()==3&&fields[0].equals("SKIP3_DEPENDENCY_UNAVAILABLE"))throw new Unavailable(fields[1]+". "+fields[2]);
            if(process.exitValue()!=0||!fields[0].equals("completed-with-warnings"))throw new IOException("Original tensorQTL failed: "+fields[1]+"; inspect worker/traceback.txt,report.json and original events");
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
        finally {
            if(process!=null){if(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive)||Thread.currentThread().isInterrupted())manifest.put("owned_process_cleanup",stop(process,descendants));manifest.put("worker_elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}
        }
    }
    private static void checkOutputFootprint(Path root,Map<String,Long> baseline,long cap,Map<String,Object> manifest,boolean live)throws IOException {
        Map<String,Long> current=footprint(root,live,manifest);
        long bytes=0;for(var e:current.entrySet()){long extra=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));if(extra>cap-bytes)throw new IOException("maximum_output_bytes exceeded by the original worker; no result admitted");bytes+=extra;}
    }
    static Map<String,Long> footprint(Path root)throws IOException {
        return footprint(root,false,new HashMap<>());
    }
    private static Map<String,Long> footprint(Path root,boolean live,Map<String,Object> manifest)throws IOException {
        Map<String,Long> result=new TreeMap<>();Path actualRoot=root.toRealPath();int[] directories={0};
        Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
            private void regularPath(Path path)throws IOException {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("tensorqtl capture inventory interrupted");
                if(Files.isSymbolicLink(path)||!path.toRealPath().equals(actualRoot.resolve(root.relativize(path))))throw new IOException("Aliases, junctions and symbolic links are not capture members");
            }
            @Override public FileVisitResult preVisitDirectory(Path path,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                regularPath(path);if(++directories[0]>4096)throw new IOException("Worker/capture directory-count capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                try {
                    regularPath(path);if(!attributes.isRegularFile())throw new IOException("Unsupported nonregular capture node");
                    if(result.size()>=10000)throw new IOException("Worker/capture file-count capacity exceeded");
                    // Query live EOF because DrvFS directory entries can lag writers.
                    try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){result.put(root.relativize(path).toString().replace('\\','/'),channel.size());}
                }catch(IOException error){
                    // Only the owned worker's verified journal replacements can
                    // disappear during a live scan. Completed captures stay strict.
                    String relative=root.relativize(path).toString().replace('\\','/');
                    if(!live||!relative.matches("worker/objects/[0-9]{6}-record[.]part")||!Files.notExists(path,LinkOption.NOFOLLOW_LINKS))throw error;
                    manifest.put("live_replaced_part_races",((Number)manifest.getOrDefault("live_replaced_part_races",0L)).longValue()+1);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path,IOException error)throws IOException {
                String relative=root.relativize(path).toString().replace('\\','/');
                if(!live||!relative.matches("worker/objects/[0-9]{6}-record[.]part")||!Files.notExists(path,LinkOption.NOFOLLOW_LINKS))throw error;
                manifest.put("live_replaced_part_races",((Number)manifest.getOrDefault("live_replaced_part_races",0L)).longValue()+1);return FileVisitResult.CONTINUE;
            }
        });return result;
    }
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants) {
        boolean interrupted=Thread.interrupted();OwnedProcesses.descendants(process).forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
        List<ProcessHandle> handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());for(var handle:handles)if(handle.isAlive())handle.destroy();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(var handle:handles)if(handle.isAlive())handle.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        List<Map<String,Object>> result=new ArrayList<>();for(var handle:handles)result.add(Map.of("pid",handle.pid(),"alive_after_cleanup",handle.isAlive()));if(interrupted)Thread.currentThread().interrupt();return result;
    }
}
