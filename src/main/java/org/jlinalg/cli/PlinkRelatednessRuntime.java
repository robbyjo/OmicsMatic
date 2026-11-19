/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.PlinkRelatednessSupport.*;

/** Exact private original profile and monitored, owned processes; never install at RUN. */
final class PlinkRelatednessRuntime {
    static final String RESOURCE="/org/jlinalg/omics/plink-relatedness-setup/";
    static final String LOCK_SHA="58b05d2d20d9c277436db1dc5a5beddfb43acbe452c175fb51aa396f71c1d421";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    private final Path home,executable,stage;
    private final PlinkRelatednessContracts.Request request;
    private final Clock clock;
    private final Map<String,Object> lock;
    private final Map<String,String> pins=new TreeMap<>();
    private final Map<String,Object> binding=new LinkedHashMap<>();

    static byte[] resource(String name)throws IOException {
        try(var in=PlinkRelatednessRuntime.class.getResourceAsStream(RESOURCE+name)){require(in!=null,"Missing packaged setup/profile resource: "+name);return in.readAllBytes();}
    }
    static Map<String,Object> lock()throws IOException {
        byte[] raw=resource("plink-runtime-lock.json");require(sha(raw).equals(LOCK_SHA),"Bundled relatedness runtime profile changed");
        var value=object(SimpleJson.parse(utf8(raw)),"runtime lock");require(text(value,"profile").equals(PlinkRelatednessContracts.PROFILE),"Different original runtime profile");return value;
    }
    PlinkRelatednessRuntime(Path supplied,Path stage,PlinkRelatednessContracts.Request request,Clock clock)throws IOException {
        this.stage=stage;this.request=request;this.clock=clock;this.lock=lock();home=supplied.toAbsolutePath().normalize();
        var files=object(lock.get("files"),"profile files");
        var engines=object(lock.get("engines"),"profile engines");var engine=object(engines.get(request.engine()),"engine identity");
        executable=home.resolve(text(engine,"executable"));
        try {
            require(System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")&&Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)),"This original profile requires Windows x86_64; VERIFY is portable");
            require(Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(home),"Missing/linked private runtime home");
            var observed=footprint(home,4,128L*1024*1024,clock);var expected=new HashSet<>(files.keySet());expected.add(".jlinalg-plink.json");
            require(observed.keySet().equals(expected),"Runtime has missing or extra files");
            var marker=read(home.resolve(".jlinalg-plink.json"),65536);keys(marker,"profile","runtime_lock_sha256","files");
            require(same(marker.get("files"),files)&&text(marker,"profile").equals(text(lock,"profile"))&&text(marker,"runtime_lock_sha256").equals(LOCK_SHA),"Sealed runtime marker differs from exact profile");
            for(var e:files.entrySet()){require(e.getValue() instanceof String&&e.getValue().toString().matches("[a-f0-9]{64}"),"Invalid bundled binary pin");String actual=hash(home.resolve(e.getKey()),clock);require(actual.equals(e.getValue()),"Changed original runtime file: "+e.getKey());pins.put(e.getKey(),actual);}
            pins.put(".jlinalg-plink.json",hash(home.resolve(".jlinalg-plink.json"),clock));
        }catch(LimitExceeded e){throw e;}
        catch(IOException e){throw new Unavailable("Exact original PLINK dependency unavailable: "+e.getMessage(),e);}
        Files.createDirectory(stage.resolve("worker"));Files.createDirectory(stage.resolve("source"));
        Files.write(stage.resolve("source/runtime-lock.json"),resource("plink-runtime-lock.json"));
        copy(home.resolve(".jlinalg-plink.json"),stage.resolve("source/runtime-marker.json"),65536,clock);
        binding.put("profile",text(lock,"profile"));binding.put("runtime_lock_sha256",LOCK_SHA);binding.put("runtime_home",home.toString());binding.put("requested_runtime_home",request.runtimeHome());
        binding.put("executable",executable.toString());binding.put("engine",request.engine());binding.put("runtime_files",new TreeMap<>(pins));binding.put("windows_system_DLLs_authenticated",false);
        json(stage.resolve("runtime-bindings.json"),binding);
        execute(List.of("--version"),"version");
        require(Files.readString(stage.resolve("worker/version.stdout")).strip().equals(text(engine,"version_stdout"))&&Files.size(stage.resolve("worker/version.stderr"))==0,"Original --version output differs");
    }
    void verify()throws IOException {
        var observed=footprint(home,4,128L*1024*1024,clock);require(observed.keySet().equals(pins.keySet()),"Original runtime acquired missing/extra files during analysis");
        for(var e:pins.entrySet())require(hash(home.resolve(e.getKey()),clock).equals(e.getValue()),"Original runtime changed during analysis: "+e.getKey());
    }
    void execute(List<String> arguments,String name)throws IOException {
        verify();clock.check();var command=new ArrayList<String>();command.add(executable.toString());command.addAll(arguments);
        var record=new LinkedHashMap<String,Object>();record.put("command",command);record.put("working_directory","worker");record.put("stage",name);record.put("started_at",Instant.now().toString());record.put("executable_sha256",pins.get(text(object(object(lock.get("engines"),"engines").get(request.engine()),"engine"),"executable")));
        Path worker=stage.resolve("worker"),stdout=worker.resolve(name+".stdout"),stderr=worker.resolve(name+".stderr");
        Process process=null;boolean completed=false,interrupted=false;
        try {
            var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            String system=System.getenv("SystemRoot");if(system==null)throw new Unavailable("Windows SystemRoot is unavailable",null);
            var environment=builder.environment();environment.clear();environment.put("SystemRoot",system);environment.put("WINDIR",system);environment.put("PATH",Path.of(system,"System32").toString());environment.put("LC_ALL","C");environment.put("OMP_NUM_THREADS","1");
            for(String variable:List.of("TEMP","TMP")){String value=System.getenv(variable);if(value!=null)environment.put(variable,value);}
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Could not launch exact original program: "+e.getMessage(),e);}
            record.put("owned_pid",process.pid());record.put("owned_start_instant",process.info().startInstant().map(Instant::toString).orElse("unavailable"));
            monitor(process,stage,request.limits(),clock);completed=true;record.put("exit_code",process.exitValue());
            require(process.exitValue()==0,"Original "+request.engine()+" "+name+" exited "+process.exitValue()+"; inspect worker/"+name+".stdout/.stderr and result.log");
            verify();
        }catch(InterruptedException e){interrupted=true;throw new InterruptedIOException("Owned original process interrupted");}
        finally {
            interrupted|=Thread.interrupted();
            IOException cleanupFailure=null;
            try{if(process!=null&&process.isAlive())terminateOwned(process,record);}catch(IOException e){cleanupFailure=e;record.put("cleanup_error",e.toString());}
            interrupted|=Thread.interrupted();
            if(process!=null&&!process.isAlive())record.put("exit_code",process.exitValue());
            record.put("process_returned_before_monitor_failure",completed);record.put("owned_alive_after_cleanup",process!=null&&process.isAlive());record.put("ended_at",Instant.now().toString());
            json(worker.resolve(name+"-process.json"),record);
            if(interrupted)Thread.currentThread().interrupt();
            if(cleanupFailure!=null)throw cleanupFailure;
        }
    }
    static void monitor(Process process,Path stage,PlinkRelatednessContracts.Limits l,Clock clock)throws IOException,InterruptedException {
        do {clock.check();footprint(stage,l.files(),l.diskBytes(),clock);footprint(stage.resolve("worker"),l.files(),l.outputBytes(),clock);if(process.waitFor(100,TimeUnit.MILLISECONDS))break;}while(true);
        clock.check();footprint(stage,l.files(),l.diskBytes(),clock);footprint(stage.resolve("worker"),l.files(),l.outputBytes(),clock);
    }
    static void terminateOwned(Process process,Map<String,Object> record)throws IOException {
        var descendants=process.descendants().map(h->Map.entry(h,h.info().startInstant())).toList();
        var stopped=new ArrayList<Long>();var unresolved=new ArrayList<Long>();
        for(var e:descendants){var h=e.getKey();if(h.isAlive()){if(e.getValue().isPresent()&&e.getValue().equals(h.info().startInstant())){h.destroyForcibly();stopped.add(h.pid());}else unresolved.add(h.pid());}}
        process.destroyForcibly();boolean interrupted=Thread.interrupted();long deadline=System.nanoTime()+30_000_000_000L;
        while(process.isAlive()&&System.nanoTime()-deadline<0)try{process.waitFor(100,TimeUnit.MILLISECONDS);}catch(InterruptedException e){interrupted=true;}
        var alive=new ArrayList<Long>();
        for(var e:descendants){var h=e.getKey();while(h.isAlive()&&e.getValue().equals(h.info().startInstant())&&System.nanoTime()-deadline<0)try{Thread.sleep(10);}catch(InterruptedException ignored){interrupted=true;}if(h.isAlive()&&e.getValue().equals(h.info().startInstant()))alive.add(h.pid());}
        record.put("owned_descendants_termination_requested",stopped);record.put("unverified_descendants_not_killed",unresolved);
        record.put("owned_descendants_alive_after_cleanup",alive);if(interrupted)Thread.currentThread().interrupt();
        require(!process.isAlive()&&unresolved.isEmpty()&&alive.isEmpty(),"Owned cleanup incomplete or descendant start identities unavailable; inspect retained process record, no result admitted");
    }
    static void validateCapture(Path root,PlinkRelatednessContracts.Request r,Clock clock)throws IOException {
        var lock=lock();require(hash(root.resolve("source/runtime-lock.json"),clock).equals(LOCK_SHA),"Captured original profile changed");
        var b=read(root.resolve("runtime-bindings.json"));keys(b,"profile","runtime_lock_sha256","runtime_home","requested_runtime_home","executable","engine","runtime_files","windows_system_DLLs_authenticated");
        require(text(b,"profile").equals(PlinkRelatednessContracts.PROFILE)&&text(b,"runtime_lock_sha256").equals(LOCK_SHA)&&text(b,"engine").equals(r.engine())&&!bool(b,"windows_system_DLLs_authenticated"),"Runtime identity/scope differs");
        require(text(b,"requested_runtime_home").equals(r.runtimeHome()),"Captured runtime request binding differs");
        var marker=read(root.resolve("source/runtime-marker.json"),65536);keys(marker,"profile","runtime_lock_sha256","files");
        require(same(marker.get("files"),lock.get("files"))&&text(marker,"profile").equals(PlinkRelatednessContracts.PROFILE)&&text(marker,"runtime_lock_sha256").equals(LOCK_SHA),"Captured marker differs");
        var files=object(b.get("runtime_files"),"runtime pins");var expected=new TreeMap<>(object(lock.get("files"),"original file pins"));expected.put(".jlinalg-plink.json",hash(root.resolve("source/runtime-marker.json"),clock));require(same(files,expected),"Captured binary/marker pins differ");
        var engine=object(object(lock.get("engines"),"engines").get(r.engine()),"engine");String relative=text(engine,"executable");
        String home=text(b,"runtime_home").replace('\\','/');require(text(b,"executable").replace('\\','/').equals(home+"/"+relative),"Executable path does not match recorded runtime home");
        for(String name:List.of("version","analysis")){
            var p=read(root.resolve("worker/"+name+"-process.json"));var command=new ArrayList<String>();command.add(text(b,"executable"));command.addAll(name.equals("version")?List.of("--version"):PlinkRelatednessContracts.arguments(r));
            keys(p,"command","working_directory","stage","started_at","executable_sha256","owned_pid","owned_start_instant","exit_code","process_returned_before_monitor_failure","owned_alive_after_cleanup","ended_at");
            require(same(p.get("command"),command)&&text(p,"working_directory").equals("worker")&&text(p,"stage").equals(name)&&integer(p,"exit_code",0,0)==0&&!bool(p,"owned_alive_after_cleanup")&&bool(p,"process_returned_before_monitor_failure"),"Recorded original process did not complete the declared command");
            require(text(p,"executable_sha256").equals(files.get(relative))&&integer(p,"owned_pid",1,Long.MAX_VALUE)>0,"Recorded executable identity differs");
        }
        require(Files.readString(root.resolve("worker/version.stdout")).strip().equals(text(engine,"version_stdout"))&&Files.size(root.resolve("worker/version.stderr"))==0,"Captured version probe differs");
    }
}
