/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete captured runtime trees and owned, sampled process supervision. No installs during analysis. */
final class BiomartRuntime {
    private BiomartRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static byte[] resource(String name)throws IOException {
        try(var in=BiomartRuntime.class.getResourceAsStream("/org/jlinalg/omics/biomart-workflow/"+name)){if(in==null)throw new IOException("Missing packaged biomaRt resource: "+name);return in.readAllBytes();}
    }
    static Map<String,Object> read(Path file,int capacity)throws IOException{return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(file,capacity)),capacity,2000000));}
    record Prepared(Map<String,Object> manifest,Map<String,Object> profile,Path manifestPath,NextflowFiles.Pin manifestPin) {
        void verify()throws IOException {
            require(manifestPin.equals(NextflowFiles.pin(manifestPath,1048576,()->{})),"Runtime manifest changed");var roots=object(manifest.get("roots"));var trees=object(profile.get("trees"));require(roots.keySet().equals(trees.keySet()),"Runtime tree family differs");
            for(String name:trees.keySet()){
                Path root=Path.of(text(roots.get(name))).toAbsolutePath().normalize();NextflowFiles.directory(root);var expected=object(trees.get(name));var seen=new HashSet<String>();
                try(var walk=Files.walk(root)){for(Path file:walk.toList()){
                    require(!Files.isSymbolicLink(file),"Linked runtime member: "+file);if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS))continue;String relative=root.relativize(file).toString().replace('\\','/');require(seen.add(relative)&&expected.containsKey(relative),"Extra runtime file: "+file);require(NextflowFiles.fingerprint(expected.get(relative),Integer.MAX_VALUE).equals(NextflowFiles.pin(file,Integer.MAX_VALUE,()->{})),"Original runtime file changed: "+file);
                }}require(seen.equals(expected.keySet()),"Original runtime file missing under "+root);
            }
        }
        Path rscript()throws IOException{return Path.of(text(manifest.get("rscript"))).toAbsolutePath().normalize();}
        List<String> libraries()throws IOException{return NextflowFiles.strings(manifest.get("libraries"));}
        void namespaces(Map<String,Object> report)throws IOException {
            var packages=object(manifest.get("packages"));var roots=object(manifest.get("roots"));Path rhome=Path.of(text(roots.get("r-home"))).toRealPath();
            for(Object item:list(report.get("packages"))){var row=object(item);String name=text(row.get("name"));Path actual=Path.of(text(row.get("path"))).toRealPath();require(packages.containsKey(name)&&actual.equals(Path.of(text(packages.get(name))).toRealPath()),"Original worker loaded an undeclared/shadowed namespace: "+name);Path permitted=roots.containsKey("pkg-"+name)?Path.of(text(roots.get("pkg-"+name))).toRealPath():rhome.resolve("library").resolve(name);require(actual.equals(permitted),"Package binding is outside its captured runtime tree: "+name);}
            for(Object item:list(report.get("DLLs"))){var row=object(item);String raw=text(row.get("path"));if(raw.equals("base"))continue;Path path=Path.of(raw).toRealPath();boolean allowed=false;for(Object value:roots.values())if(path.startsWith(Path.of(text(value)).toRealPath())){allowed=true;break;}require(allowed,"Uncaptured original R DLL: "+path);}
        }
    }
    static Prepared prepare(BiomartRequest.Request r)throws IOException {
        try{
            require(System.getProperty("os.name").startsWith("Windows")&&Set.of("amd64","x86_64").contains(System.getProperty("os.arch")),"Captured original R runtime requires Windows x86_64; native query planning remains portable");Path path=r.runtime().toRealPath();var m=read(path,1048576);NextflowFiles.keys(m,"schema_version","profile","edition","rscript","libraries","roots","packages","profile_sha256");require(integer(m.get("schema_version"),1,1)==1&&r.edition().equals(text(m.get("edition")))&&text(m.get("profile")).equals("original-biomaRt-"+r.edition()+"-Windows-R4.6.1"),"Runtime profile/edition differs");byte[] raw=resource("runtime-"+r.edition()+".json");require(NextflowFiles.sha(raw).equals(text(m.get("profile_sha256"))),"Runtime profile fingerprint differs from the packaged source profile");var p=object(NextflowParameters.parse(NextflowFiles.utf8(raw),16777216,2000000));var prepared=new Prepared(m,p,path,NextflowFiles.pin(path,1048576,()->{}));Path rhome=Path.of(text(object(m.get("roots")).get("r-home"))).toRealPath();require(prepared.rscript().toRealPath().equals(rhome.resolve("bin/Rscript.exe")),"Rscript differs from the complete captured R installation");prepared.verify();return prepared;
        }catch(IOException|RuntimeException failure){throw new Unavailable("Exact original biomaRt runtime unavailable; dependent analysis skipped. "+failure,failure);}
    }
    static void source(Path home,String edition)throws IOException {
        Files.write(home.resolve("worker.R"),resource("worker.R"),StandardOpenOption.CREATE_NEW);var profile=object(NextflowParameters.parse(NextflowFiles.utf8(resource("sources.json")),1048576,100000));var versions=object(profile.get("versions"));var files=object(versions.get(edition));Path directory=Files.createDirectories(home.resolve("source").resolve(edition));
        for(var e:files.entrySet()){String name=NextflowFiles.member(e.getKey());byte[] raw=resource("source/"+edition+"/"+name);require(NextflowFiles.fingerprint(e.getValue(),1048576).equals(new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw))),"Packaged original source changed: "+name);Files.write(directory.resolve(name),raw,StandardOpenOption.CREATE_NEW);}
    }
    static Map<String,Object> run(Path home,String phase,BiomartRequest.Request r,Prepared runtime)throws Exception {
        return run(home,phase,r,runtime,Map.of());
    }
    static Map<String,Object> run(Path home,String phase,BiomartRequest.Request r,Prepared runtime,Map<String,List<String>> plans)throws Exception {
        source(home,r.edition());var request=new LinkedHashMap<>(r.worker());request.put("expected_native_queries",plans);NextflowFiles.writeJson(home.resolve("worker-request.json"),request);var command=new ArrayList<String>(List.of(runtime.rscript().toString(),"--vanilla",home.resolve("worker.R").toString(),home.toString(),phase));command.addAll(runtime.libraries());int code=process(home,command,r.timeoutSeconds(),r.outputBytes());var report=read(home.resolve("worker-report.json"),16777216);runtime.namespaces(report);require(Boolean.FALSE.equals(report.get("namespace_patched")),"Original namespace mutation is forbidden");if(code==3&&text(report.get("status")).equals("SKIP3_EXACT_SERVICE_UNAVAILABLE"))throw new Unavailable("Requested exact service/release/assembly unavailable: "+report.get("error"),null);require(code==0&&text(report.get("status")).equals("PASS_ORIGINAL_OPERATION_ONLY")&&text(report.get("trace_error")).isEmpty(),"Original biomaRt "+phase+" failed: "+report.get("error")+(text(report.get("trace_error")).isEmpty()?"":"; "+report.get("trace_error")+". Increase maximum_http_bytes within its supported bound and retry NEW output paths without omitting data."));return report;
    }
    static long footprint(Path home,long capacity)throws IOException {
        long[] total={0};int[] count={0};Files.walkFileTree(home,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path f,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{require(++count[0]<=100000,"Complete output file count exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path f,java.nio.file.attribute.BasicFileAttributes attrs)throws IOException{require(++count[0]<=100000,"Complete output file count exceeded");require(!attrs.isSymbolicLink()&&!Files.isSymbolicLink(f)&&attrs.isRegularFile(),"Nonregular/linked output member: "+f);require(attrs.size()<=capacity-total[0],"Complete output byte capacity exceeded");total[0]+=attrs.size();return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFileFailed(Path f,IOException failure)throws IOException{if(failure instanceof NoSuchFileException)return FileVisitResult.CONTINUE;throw failure;}
        });return total[0];
    }
    static int process(Path home,List<String> command,int seconds,long bytes)throws Exception {
        Path temp=Files.createDirectory(home.resolve("private-temp"));var builder=new ProcessBuilder(command).directory(home.toFile()).redirectOutput(home.resolve("original.stdout").toFile()).redirectError(home.resolve("original.stderr").toFile());var env=builder.environment();Set<String> removed=Set.of("LANG","LANGUAGE","R_HOME","R_ARCH","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS");env.keySet().removeIf(k->k.startsWith("LC_")||removed.contains(k));env.putAll(Map.of("LANG","C","LC_ALL","C","TMPDIR",temp.toString(),"TMP",temp.toString(),"TEMP",temp.toString(),"BIOMART_CACHE",home.resolve("private-cache").toString(),"OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        var guard=new Guard();Thread hook=new Thread(guard::stop,"biomart-owned-worker-cleanup");Runtime.getRuntime().addShutdownHook(hook);long started=System.nanoTime();var receipt=new LinkedHashMap<String,Object>();receipt.put("command",command);receipt.put("started_at",Instant.now().toString());receipt.put("supervision_scope","Owned ProcessHandle tree sampled every25ms; wall/workspace limits are sampled, not OS job containment or a hard memory bound");Process child=null;Exception failure=null;int code=-1;
        try{
            child=guard.launch(builder);child.getOutputStream().close();for(;;){guard.observe();footprint(home,bytes);require(System.nanoTime()-started<=TimeUnit.SECONDS.toNanos(seconds),"Original worker timeout; owned cleanup requested");if(child.waitFor(25,TimeUnit.MILLISECONDS))break;}guard.observe();code=child.exitValue();require(guard.liveChildren().isEmpty(),"Original worker exited with live owned descendants");
        }catch(Exception e){failure=e;throw e;}finally{
            boolean interrupted=Thread.interrupted();guard.stop();receipt.put("exit_code",code);receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));receipt.put("owned_processes",guard.receipts());receipt.put("owned_live_after_cleanup",guard.live());receipt.put("status",failure==null&&guard.live().isEmpty()?"completed-process-only":"FAIL2_NO_RESULT_ADMITTED");if(failure!=null)receipt.put("error",failure.toString());try{NextflowFiles.writeJson(home.resolve("process.json"),receipt);}finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns this guard. */}if(interrupted)Thread.currentThread().interrupt();}require(guard.live().isEmpty(),"Owned process cleanup incomplete; inspect process.json");
        }return code;
    }
    private static final class Guard {
        private Process root;private boolean closed;private final Map<Long,ProcessHandle> handles=new LinkedHashMap<>();private final Map<Long,String> starts=new LinkedHashMap<>();
        synchronized Process launch(ProcessBuilder builder)throws IOException{require(!closed,"Worker guard already closed");root=builder.start();remember(root.toHandle());observe();return root;}
        private void remember(ProcessHandle h){handles.putIfAbsent(h.pid(),h);starts.putIfAbsent(h.pid(),h.info().startInstant().map(Instant::toString).orElse("unavailable"));}
        synchronized void observe(){if(root!=null)root.descendants().forEach(this::remember);for(var h:new ArrayList<>(handles.values()))if(h.isAlive())h.descendants().forEach(this::remember);}
        synchronized List<Long> live(){return handles.values().stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();}
        synchronized List<Long> liveChildren(){return handles.values().stream().filter(h->root==null||h.pid()!=root.pid()).filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();}
        synchronized List<Map<String,Object>> receipts(){return handles.values().stream().map(h->Map.<String,Object>of("pid",h.pid(),"started_at",starts.get(h.pid()),"alive_after_cleanup",h.isAlive())).toList();}
        synchronized void stop(){closed=true;observe();boolean interrupted=Thread.interrupted();var list=new ArrayList<>(handles.values());Collections.reverse(list);for(var h:list)if(h.isAlive())h.destroyForcibly();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!live().isEmpty()&&System.nanoTime()<deadline)try{Thread.sleep(10);}catch(InterruptedException e){interrupted=true;}if(interrupted)Thread.currentThread().interrupt();}
    }
}
