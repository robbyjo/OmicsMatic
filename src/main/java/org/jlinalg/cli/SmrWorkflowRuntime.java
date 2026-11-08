/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Exact original source, binary and ELF-library closure; only owned processes and private copies are executed. */
final class SmrWorkflowRuntime {
    private SmrWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String detail){super(detail);}
        Unavailable(String detail,Throwable cause){super(detail,cause);}
    }
    record Binding(Path original,Path copy,NextflowFiles.Pin pin) {
        void verify()throws IOException {
            if(!pin.equals(NextflowFiles.pin(original,pin.bytes(),()->{}))||!pin.equals(NextflowFiles.pin(copy,pin.bytes(),()->{})))throw new Unavailable("Original/captured SMR source/runtime changed: "+original);
        }
    }
    record Prepared(List<Binding> bindings,Path loader,Path libraryDirectory,Path program,String version) {
        void verify()throws IOException {for(Binding b:bindings)b.verify();}
    }
    static byte[] resource(String name)throws IOException {
        try(var in=SmrWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/smr-workflow/"+name)){
            if(in==null)throw new IOException("Missing packaged SMR profile/notice: "+name);return in.readAllBytes();
        }
    }
    static Prepared prepare(Path stage,SmrWorkflowRequest.Request r,Map<String,Object> manifest)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("This exact original SMR profile requires Linux x86_64 Java. Native SmrStatistics is portable; no Windows interoperability, source-version or estimator fallback occurs.");
        var profile=object(NextflowParameters.parse(NextflowFiles.utf8(resource("SOURCE-PROFILE.json")),1048576,100000));require(text(profile.get("profile")).equals(SmrWorkflowRequest.PROFILE),"Packaged SMR profile differs");var edition=object(object(profile.get("editions")).get(r.version()));var bindings=new ArrayList<Binding>();long[] total={0};
        for(var e:object(edition.get("source_files")).entrySet())capture(r.source(),stage.resolve("source/original"),e.getKey(),e.getValue(),bindings,total);
        for(var e:object(edition.get("runtime_files")).entrySet())capture(r.runtime(),stage.resolve("private-runtime"),e.getKey(),e.getValue(),bindings,total);
        for(String name:List.of("SOURCE-PROFILE.json","GPL-2.txt","NOTICE.txt")){Path path=stage.resolve("source").resolve(name);Files.createDirectories(path.getParent());Files.write(path,resource(name),StandardOpenOption.CREATE_NEW);}
        Path runtime=stage.resolve("private-runtime"),loader=runtime.resolve(text(edition.get("loader"))),program=runtime.resolve(text(edition.get("program"))),lib=runtime.resolve(text(edition.get("library_directory")));
        for(Path executable:List.of(loader,program)){
            if(executable.getFileSystem().supportedFileAttributeViews().contains("posix")){var permissions=new HashSet<>(Files.getPosixFilePermissions(executable));permissions.add(PosixFilePermission.OWNER_EXECUTE);Files.setPosixFilePermissions(executable,permissions);}
            if(!Files.isExecutable(executable))throw new Unavailable("Execution of the exact private SMR program/loader is forbidden: "+executable);
        }
        var prepared=new Prepared(List.copyOf(bindings),loader,lib,program,r.version());prepared.verify();manifest.put("original_source_commit",edition.get("source_commit"));manifest.put("exact_runtime_files",pinMap(edition.get("runtime_files")));manifest.put("original_source_files",pinMap(edition.get("source_files")));manifest.put("runtime_environment_fallback",false);return prepared;
    }
    private static Map<String,Object> pinMap(Object raw)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:object(raw).entrySet()){var pin=NextflowFiles.fingerprint(e.getValue(),536870912);result.put(e.getKey(),Map.of("bytes",pin.bytes(),"sha256",pin.sha256()));}return result;}
    private static void capture(Path root,Path destination,String name,Object raw,List<Binding> bindings,long[] total)throws IOException {
        NextflowFiles.member(name);var expected=NextflowFiles.fingerprint(raw,536870912);require(expected.bytes()<=1073741824-total[0],"Source/runtime capture exceeds1GiB capacity");total[0]+=expected.bytes();Path original=root.resolve(name),copy=destination.resolve(name);
        try{
            if(!expected.equals(NextflowFiles.pin(original,expected.bytes(),()->{})))throw new Unavailable("Exact SMR source/runtime byte identity differs: "+original);
            Files.createDirectories(copy.getParent());Files.copy(original,copy);var binding=new Binding(original,copy,expected);binding.verify();bindings.add(binding);
        }catch(IOException error){throw new Unavailable("Exact original SMR source/runtime unavailable, changed or forbidden: "+original,error);}
    }
    static void health(Path stage,SmrWorkflowRequest.Request r,Prepared runtime,Map<String,Object> manifest)throws Exception {
        Path home=stage.resolve("worker/health");Files.createDirectory(home);int code=execute(stage,home,"runtime-health",List.of("--help"),r,runtime,manifest);
        String stdout=NextflowFiles.utf8(NextflowFiles.bytes(home.resolve("stdout.log"),Math.min(r.tableBytes(),1048576)));
        if(code!=1||!stdout.contains("Version "+r.version())||!stdout.contains("flags include:")||!stdout.contains("--smr-multi")||!stdout.contains("--heidi-mtd"))throw new Unavailable("The exact original SMR loader/runtime cannot execute its declared version/usage profile; inspect health logs. Original help exit1 is expected metadata behavior, never model success.");
    }
    static int execute(Path stage,Path home,String identifier,List<String> options,SmrWorkflowRequest.Request r,Prepared runtime,Map<String,Object> manifest)throws Exception {
        require(home.toAbsolutePath().normalize().startsWith(stage.resolve("worker").toAbsolutePath().normalize()),"Original process directory must be owned by this attempt");runtime.verify();var command=new ArrayList<String>();Collections.addAll(command,runtime.loader().toString(),"--inhibit-cache","--library-path",runtime.libraryDirectory().toString(),"--inhibit-rpath","",runtime.program().toString());command.addAll(options);Path worker=stage.resolve("worker");var baseline=CiceroWorkflowRuntime.footprint(worker,r.outputFiles());var builder=new ProcessBuilder(command).directory(home.toFile()).redirectOutput(home.resolve("stdout.log").toFile()).redirectError(home.resolve("stderr.log").toFile());
        builder.environment().keySet().removeIf(k->k.startsWith("LD_")||k.startsWith("LC_")||Set.of("LANG","LANGUAGE","GLIBC_TUNABLES","GCONV_PATH","LOCPATH","OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS").contains(k));builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS",Integer.toString(r.threads()),"OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1","TMPDIR",home.toString()));
        var owned=new AtomicReference<Process>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"smr-owned-process-cleanup");Runtime.getRuntime().addShutdownHook(hook);Process process=null;var children=new ArrayList<ProcessHandle>();long start=System.nanoTime();
        try{
            try{process=builder.start();}catch(IOException error){throw new Unavailable("The exact private original SMR loader could not start; dependent analysis skipped",error);}owned.set(process);process.getOutputStream().close();manifest.put("last_owned_original_pid",process.pid());
            while(!process.waitFor(50,TimeUnit.MILLISECONDS)){remember(process,children);deadline(start,r);capacity(worker,baseline,r);}
            remember(process,children);deadline(start,r);capacity(worker,baseline,r);runtime.verify();return process.exitValue();
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw error;}
        finally{
            if(process!=null){var cleanup=stop(process,children);var receipt=new LinkedHashMap<String,Object>();receipt.put("id",identifier);receipt.put("command",command);receipt.put("owned_pid",process.pid());receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));receipt.put("owned_process_cleanup",cleanup);if(!process.isAlive())receipt.put("original_exit_code",process.exitValue());NextflowFiles.writeJson(home.resolve("process-receipt.json"),receipt);owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}require(cleanup.stream().noneMatch(p->Boolean.TRUE.equals(p.get("alive_after_cleanup"))),"An owned SMR process survived cleanup; no result admitted");}
            else{owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}}
        }
    }
    private static void deadline(long start,SmrWorkflowRequest.Request r)throws IOException {require(System.nanoTime()-start<=TimeUnit.MILLISECONDS.toNanos(r.timeoutMillis()),"Original SMR process exceeded timeout_millis; owned process cleanup requested");}
    static void finalCapacity(Path worker,SmrWorkflowRequest.Request r)throws IOException {long bytes=0;for(long size:CiceroWorkflowRuntime.footprint(worker,r.outputFiles()).values()){require(size<=r.outputBytes()-bytes,"Complete SMR worker/native-audit outputs exceed maximum_output_bytes");bytes+=size;}}
    private static void capacity(Path worker,Map<String,Long> baseline,SmrWorkflowRequest.Request r)throws IOException {long bytes=0;for(var e:CiceroWorkflowRuntime.footprint(worker,r.outputFiles()).entrySet()){long extra=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));require(extra<=r.outputBytes()-bytes,"Original SMR output growth exceeds maximum_output_bytes");bytes+=extra;}}
    private static void remember(Process process,List<ProcessHandle> result){process.descendants().forEach(p->{if(result.stream().noneMatch(old->old.pid()==p.pid()))result.add(p);});}
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants){
        boolean interrupted=Thread.interrupted();remember(process,descendants);var handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());for(var p:handles)if(p.isAlive())p.destroy();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}for(var p:handles)if(p.isAlive())p.destroyForcibly();until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}var rows=new ArrayList<Map<String,Object>>();for(var p:handles)rows.add(Map.of("pid",p.pid(),"alive_after_cleanup",p.isAlive()));if(interrupted)Thread.currentThread().interrupt();return rows;
    }
}
