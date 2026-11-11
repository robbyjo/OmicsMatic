/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Exact original wheel/JAR binding, immutable runtime trees and owned Linux worker supervision. Analysis installs nothing. */
final class HailWorkflowRuntime {
    private HailWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException {
        try(var input=HailWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/hail-workflow/"+name)){if(input==null)throw new IOException("Missing packaged Hail workflow resource: "+name);return input.readAllBytes();}
    }
    static Map<String,Object> read(Path path,int capacity)throws IOException{return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(path,capacity)),capacity,4000000));}
    static Map<String,Object> parse(byte[] raw)throws IOException{return object(NextflowParameters.parse(NextflowFiles.utf8(raw),67108864,4000000));}
    record Prepared(Path manifestPath,NextflowFiles.Pin manifestPin,byte[] raw,Map<String,Object> manifest,
                    Map<String,Object> profile,Path prefix,Path javaHome,Path inventory,NextflowFiles.Pin inventoryPin,
                    Path wheelFiles,NextflowFiles.Pin wheelFilesPin) {
        String expectedVersion()throws IOException{return text(profile.get("hail_version"));}
        Path python(){return prefix.resolve("bin/python");}
        void verify()throws IOException {
            require(manifestPin.equals(NextflowFiles.pin(manifestPath,67108864,()->{})),"Original runtime binding changed");require(inventoryPin.equals(NextflowFiles.pin(inventory,67108864,()->{})),"Complete runtime inventory changed");require(wheelFilesPin.equals(NextflowFiles.pin(wheelFiles,67108864,()->{})),"Original installed-wheel inventory changed");var trees=read(inventory,67108864);NextflowFiles.keys(trees,"prefix","java");verifyTree(prefix,object(trees.get("prefix")));verifyTree(javaHome,object(trees.get("java")));var original=read(wheelFiles,67108864);require(original.equals(object(profile.get("wheel_members"))),"Installed source/backend JAR family differs from the exact packaged upstream wheel profile");Path site=prefix.resolve("lib/python"+text(profile.get("python_minor"))+"/site-packages");
            for(var item:original.entrySet()){Path path=site.resolve(NextflowFiles.member(item.getKey()));require(NextflowFiles.fingerprint(item.getValue(),Integer.MAX_VALUE).equals(NextflowFiles.pin(path,Integer.MAX_VALUE,()->{})),"Original Hail/hailtop wheel member changed: "+item.getKey());}
            require(Files.isExecutable(python())&&Files.isExecutable(javaHome.resolve("bin/java")),"Original Python/Java executable permission unavailable");
        }
        void namespaces(Map<String,Object> report)throws IOException {
            Path site=prefix.resolve("lib/python"+text(profile.get("python_minor"))+"/site-packages");require(expectedVersion().equals(text(report.get("hail")))&&Path.of(text(report.get("hail_module_path"))).equals(site.resolve("hail/__init__.py"))&&Path.of(text(report.get("python_prefix"))).equals(prefix),"Original worker loaded an undeclared/shadowed Hail namespace or Python prefix");
            for(Object raw:list(report.get("cases"))){var c=object(raw);if(text(c.get("kind")).equals("batch-local"))require(Path.of(text(c.get("batch_module_path"))).equals(site.resolve("hailtop/batch/__init__.py")),"Batch worker loaded an undeclared/shadowed namespace");}
        }
    }
    static Prepared prepare(HailWorkflowRequest.Request r)throws IOException {
        try{
            require(System.getProperty("os.name").equals("Linux")&&Set.of("amd64","x86_64").contains(System.getProperty("os.arch")),"Original Hail workflow execution requires Linux x86_64. Run the Java CLI inside Linux/WSL with the named Linux runtime; native Java sample QC remains portable");require(!r.needsService(),"Required SERVICE backend is unconfigured and not validated in this profile. Dependent cloud analysis skipped; no credentials are read or cloud jobs submitted. LOCAL execution cannot establish the paper's cloud backend or scale");byte[] raw=NextflowFiles.bytes(r.runtime(),67108864);var manifest=parse(raw);var manifestPin=new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw));require(manifestPin.equals(NextflowFiles.pin(r.runtime(),67108864,()->{})),"Runtime manifest changed during capture");var profiles=object(parse(resource("profiles.json")).get("editions"));var profile=object(profiles.get(r.edition()));require(text(manifest.get("status")).equals("PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY")&&Boolean.TRUE.equals(manifest.get("runtime_trees_unchanged"))&&text(manifest.get("edition")).equals(r.edition()),"A complete unchanged exact-edition runtime binding is required");require(text(manifest.get("hail")).equals(text(profile.get("hail_version")))&&object(manifest.get("original_source")).get("sha256").equals(object(profile.get("wheel")).get("sha256")),"Original wheel/version binding differs");var health=object(manifest.get("health"));require(text(health.get("status")).equals("PASS_ORIGINAL_HAIL_JAVA11_LOCAL_SPARK_HEALTH_ONLY")&&text(health.get("hail")).equals(text(profile.get("hail_version")))&&text(health.get("revision")).equals(text(profile.get("revision")))&&text(health.get("spark")).equals(text(profile.get("spark")))&&text(health.get("python")).startsWith(text(profile.get("python_minor"))+"."),"Original Python/Spark/Hail health binding differs");Path prefix=NextflowFiles.directory(Path.of(text(manifest.get("runtime_environment")))),java=NextflowFiles.directory(Path.of(text(manifest.get("java_home"))));require(Path.of(text(manifest.get("runtime_python"))).equals(prefix.resolve("bin/python")),"Unexpected runtime Python path");Path home=r.runtime().getParent(),inventory=home.resolve("runtime-before.json"),wheelFiles=home.resolve("original-installed-wheel-files.json");var prepared=new Prepared(r.runtime(),manifestPin,raw,manifest,profile,prefix,java,inventory,NextflowFiles.fingerprint(manifest.get("runtime_inventory"),67108864),wheelFiles,NextflowFiles.fingerprint(manifest.get("original_installed_wheel_files"),67108864));prepared.verify();return prepared;
        }catch(IOException|RuntimeException failure){throw new Unavailable("Exact permitted original Hail dependencies unavailable; dependent batch skipped. "+failure,failure);}
    }
    static void verifyTree(Path root,Map<String,Object> expected)throws IOException {
        var seen=new HashSet<String>();var pending=new ArrayList<Future<Void>>();var pool=Executors.newFixedThreadPool(8);
        try{
            Files.walkFileTree(root,new SimpleFileVisitor<>(){
                private Map<String,Object> row(Path path)throws IOException{String name=root.relativize(path).toString().replace('\\','/');require(seen.add(name)&&expected.containsKey(name),"Extra original runtime member: "+path);return object(expected.get(name));}
                @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{if(!path.equals(root))require(text(row(path).get("kind")).equals("directory"),"Runtime directory type differs: "+path);return FileVisitResult.CONTINUE;}
                @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{
                    var expectedRow=row(path);pending.add(pool.submit(()->{verifyMember(path,expectedRow,attrs.isSymbolicLink());return null;}));return FileVisitResult.CONTINUE;
                }
            });pool.shutdown();for(var task:pending)task.get();require(seen.equals(expected.keySet()),"Original runtime members missing: "+root);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Complete runtime verification interrupted",interrupted);}
        catch(ExecutionException failed){if(failed.getCause() instanceof IOException io)throw io;throw new IOException("Complete runtime member verification failed",failed.getCause());}
        finally{pool.shutdownNow();try{require(pool.awaitTermination(10,TimeUnit.SECONDS),"Runtime hash workers did not stop");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
    }
    private static void verifyMember(Path path,Map<String,Object> row,boolean linked)throws IOException {
        String kind=text(row.get("kind"));Runnable cancellation=()->{if(Thread.currentThread().isInterrupted())throw new CancellationException("Complete runtime hash cancelled");};
        if(linked){
            require(kind.equals("symlink")&&Files.readSymbolicLink(path).toString().equals(text(row.get("target"))),"Runtime link target differs: "+path);Path resolved=path.toRealPath();require(resolved.toString().equals(text(row.get("resolved"))),"Runtime link resolution changed: "+path);
            if(text(row.get("target_kind")).equals("file"))require(NextflowFiles.fingerprint(row.get("content"),Long.MAX_VALUE).equals(NextflowFiles.pin(resolved,Long.MAX_VALUE,cancellation)),"Runtime linked-file content differs: "+path);
            else require(text(row.get("target_kind")).equals("directory")&&Files.isDirectory(resolved),"Runtime link target type differs");
        }else require(kind.equals("file")&&NextflowFiles.fingerprint(row,Long.MAX_VALUE).equals(NextflowFiles.pin(path,Long.MAX_VALUE,cancellation)),"Original runtime file content differs: "+path);
    }
    static Map<String,Object> run(Path home,HailWorkflowRequest.Request r,Prepared runtime)throws Exception {
        Files.write(home.resolve("worker.py"),resource("worker.py"),StandardOpenOption.CREATE_NEW);Files.write(home.resolve("supervisor.py"),resource("supervisor.py"),StandardOpenOption.CREATE_NEW);Files.write(home.resolve("runtime.original.json"),runtime.raw(),StandardOpenOption.CREATE_NEW);NextflowFiles.writeJson(home.resolve("cases.json"),r.worker(runtime.expectedVersion()));var command=List.of(runtime.python().toString(),"-I","-B","-X","pycache_prefix="+home.resolve("supervisor-pycache"),home.resolve("supervisor.py").toString(),"--runtime",home.resolve("runtime.original.json").toString(),"--out",home.toString(),"--worker",home.resolve("worker.py").toString(),"--request",home.resolve("cases.json").toString(),"--timeout",Integer.toString(r.timeout()),"--maximum-output-bytes",Long.toString(r.outputBytes()),"--maximum-files",Integer.toString(r.maximumFiles()));var builder=new ProcessBuilder(command).directory(home.toFile()).redirectOutput(home.resolve("supervisor.stdout").toFile()).redirectError(home.resolve("supervisor.stderr").toFile());var env=builder.environment();env.keySet().removeIf(key->key.startsWith("HAIL_")||key.startsWith("SPARK_")||key.startsWith("PYSPARK_")||key.startsWith("PYTHON")||Set.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS").contains(key));var guard=new Guard(home);Thread hook=new Thread(guard::stop,"hail-owned-worker-cleanup");Runtime.getRuntime().addShutdownHook(hook);int code=-1;Exception error=null;long started=System.nanoTime();
        try{
            Process child=guard.launch(builder);child.getOutputStream().close();while(!child.waitFor(100,TimeUnit.MILLISECONDS)){guard.observe();require(System.nanoTime()-started<=TimeUnit.SECONDS.toNanos((long)r.timeout()+30),"Original supervisor exceeded timeout/cleanup grace");}guard.observe();code=child.exitValue();require(code==0,"Original Hail batch failed; all earlier cases remain provisional. Inspect original.stderr, original-error.traceback and original.process.json");var report=read(home.resolve("COMPLETED.json"),67108864);var process=read(home.resolve("original.process.json"),16777216);require(text(report.get("status")).equals("PASS_COMPLETE_ORIGINAL_HAIL_WORKFLOW_CANDIDATE_ONLY")&&Boolean.TRUE.equals(report.get("complete_native_readback_verified"))&&Boolean.FALSE.equals(report.get("namespace_patched"))&&Boolean.FALSE.equals(report.get("cloud_batch_submitted"))&&list(process.get("owned_live_after_cleanup")).isEmpty(),"Original complete-batch/source/cleanup/readback admission failed");require(integer(process.get("exit_code"),0,0)==0&&list(report.get("cases")).size()==r.cases().size(),"Original case family incomplete");runtime.namespaces(report);return report;
        }catch(Exception failure){error=failure;throw failure;}finally{
            boolean interrupted=Thread.interrupted();guard.stop();var receipt=new LinkedHashMap<String,Object>();receipt.put("command",command);receipt.put("exit_code",code);receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));receipt.put("owned_processes",guard.receipts());receipt.put("owned_live_after_cleanup",guard.live());receipt.put("status",error==null&&guard.live().isEmpty()?"completed-process-only":"FAIL2_NO_RESULT_ADMITTED");if(error!=null)receipt.put("error",error.toString());try{NextflowFiles.writeJson(home.resolve("java-process.json"),receipt);}finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}if(interrupted)Thread.currentThread().interrupt();}require(guard.live().isEmpty(),"Owned Java-supervised processes remain alive; inspect java-process.json");
        }
    }
    private static final class Guard {
        final Path home;Process root;boolean closed;final Map<Long,ProcessHandle> handles=new LinkedHashMap<>();final Map<Long,String> starts=new LinkedHashMap<>();
        Guard(Path home){this.home=home;}
        synchronized Process launch(ProcessBuilder builder)throws IOException{require(!closed,"Owned guard already closed");root=builder.start();observe();return root;}
        synchronized void observe(){if(root!=null){remember(root.toHandle());for(ProcessHandle handle:new ArrayList<>(handles.values()))handle.descendants().forEach(this::remember);}}
        void remember(ProcessHandle h){handles.putIfAbsent(h.pid(),h);starts.putIfAbsent(h.pid(),h.info().startInstant().map(Instant::toString).orElse("unavailable"));}
        synchronized List<Long> live(){observe();return handles.values().stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();}
        synchronized List<Map<String,Object>> receipts(){return handles.values().stream().map(h->Map.<String,Object>of("pid",h.pid(),"start_instant",starts.get(h.pid()),"alive",h.isAlive())).toList();}
        synchronized void stop(){
            if(closed)return;closed=true;observe();if(root==null)return;
            try{if(root.isAlive()){try{Files.writeString(home.resolve("CANCEL_REQUESTED"),Instant.now()+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException ignored){/* Signal fallback still owns sampled handles. */}root.waitFor(8,TimeUnit.SECONDS);}}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            for(ProcessHandle handle:new ArrayList<>(handles.values()))if(handle.isAlive())handle.destroy();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(!live().isEmpty()&&System.nanoTime()<deadline){try{Thread.sleep(25);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}}
            for(ProcessHandle handle:new ArrayList<>(handles.values()))if(handle.isAlive())handle.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(!live().isEmpty()&&System.nanoTime()<deadline){try{Thread.sleep(25);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}}
        }
    }
}
