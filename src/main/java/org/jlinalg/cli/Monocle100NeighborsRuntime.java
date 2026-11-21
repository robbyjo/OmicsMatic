/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.Monocle100NeighborsFiles.*;
import static org.jlinalg.cli.Monocle100NeighborsContracts.*;

/** Linux worker ownership, declared environment and before/after executable/package-code identities. */
final class Monocle100NeighborsRuntime {
    private Monocle100NeighborsRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    private static Path file(Path path) throws IOException {
        if(!Files.isRegularFile(path)||!Files.isReadable(path))throw new Unavailable("Exact runtime file is missing or inaccessible: "+path);return path.toRealPath();
    }
    private static Path directory(Path path) throws IOException {
        if(!Files.isDirectory(path)||!Files.isReadable(path))throw new Unavailable("Exact runtime directory is missing or inaccessible: "+path);return path.toRealPath();
    }
    static RuntimeConfig prepare(RuntimeConfig value) throws IOException {
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"))throw new Unavailable("RUN/REPLAY requires Linux Java and the exact Linux R4.3.3 profile; Windows users invoke Linux Java in WSL with Linux paths. VERIFY is R-free on Windows.");
        Path python=file(value.python()),rscript=file(value.rscript());if(!Files.isExecutable(python)||!Files.isExecutable(rscript))throw new Unavailable("Declared Python/Rscript execution is forbidden; skip dependent analysis");
        Path rHome=directory(value.rHome());file(rHome.resolve("bin/exec/R"));directory(value.nativeLibraries());var libraries=new ArrayList<Path>();for(Path path:value.libraries())libraries.add(directory(path));
        return new RuntimeConfig(value.source(),python,rscript,rHome,value.nativeLibraries().toRealPath(),List.copyOf(libraries));
    }
    static Map<String,Object> code(RuntimeConfig value) throws IOException {
        var result=new LinkedHashMap<String,Object>();
        for(Path path:List.of(value.python(),value.rscript(),value.rHome().resolve("bin/exec/R")))result.put(path.toString(),pin(NextflowFiles.pin(file(path),536870912,()->{})));
        for(String name:List.of("monocle3","RANN","spdep","slam","Matrix","igraph","jsonlite","digest")) {
            Path selected=null;for(Path library:value.libraries())if(Files.isDirectory(library.resolve(name))){selected=library.resolve(name).toRealPath();break;}
            if(selected==null)throw new Unavailable("Exact original runtime package is missing: "+name);
            try(var paths=Files.walk(selected)) {
                for(Path path:paths.filter(Files::isRegularFile).toList()) {
                    String relative=selected.relativize(path).toString().replace('\\','/');
                    if(relative.startsWith("R/")||relative.startsWith("libs/")||relative.equals("NAMESPACE")||relative.equals("DESCRIPTION")) {
                        Path real=path.toRealPath();require(real.startsWith(selected),"Runtime package code link escapes its package");result.put(real.toString(),pin(NextflowFiles.pin(real,536870912,()->{})));
                    }
                }
            }
        }
        return result;
    }
    static void run(Path root,RuntimeConfig runtime,Map<String,Object> parameters,Caps caps) throws Exception {
        Path worker=root.resolve("worker");json(worker.resolve("runtime.json"),runtime.values());write(worker.resolve("runtime-paths.txt"),String.join("\n",runtime.libraries().stream().map(Path::toString).toList())+"\n");
        var before=code(runtime);json(root.resolve("runtime-code-before.json"),before);
        List<String> command=List.of(runtime.python().toString(),"-I","-B",root.resolve("source/supervisor.py").toString(),root.toString(),Long.toString(ProcessHandle.current().pid()));
        var builder=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(worker.resolve("supervisor.stdout").toFile()).redirectError(worker.resolve("supervisor.stderr").toFile());
        var owned=new AtomicReference<Process>();var descendants=new ArrayList<ProcessHandle>();Thread hook=new Thread(()->{Process process=owned.get();if(process!=null)cleanup(root,process,descendants);},"monocle100-owned-neighbor-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;long started=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException failure){throw new Unavailable("Declared Python supervisor could not start; dependent analysis skipped",failure);}
            owned.set(process);process.getOutputStream().close();json(worker.resolve("SUPERVISOR-STARTED.json"),Map.of("command",command,"pid",process.pid()));
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                remember(process,descendants);require(System.nanoTime()-started<=TimeUnit.SECONDS.toNanos(caps.timeout()+30L),"Owned supervisor exceeded timeout plus cleanup allowance");
            }
            remember(process,descendants);
            var receipt=read(worker.resolve("PROCESS.json"),caps.jsonBytes());require(list(receipt.getOrDefault("owned_live_after_cleanup",List.of())).isEmpty(),"Owned original workers survived supervisor cleanup");
            if(process.exitValue()==3)throw new Unavailable("Exact original runtime is unavailable; read worker terminal.tsv, report/error and original logs");
            require(process.exitValue()==0,"Original neighbor process failed: "+receipt.get("status")+"; retain worker/error.json, original-error.txt, stdout.log and stderr.log");
            List<String> terminal=Files.readAllLines(worker.resolve("terminal.tsv"),StandardCharsets.UTF_8);require(terminal.size()==1&&terminal.get(0).startsWith("PASS\t"),"Original worker did not return a complete successful terminal status");
            require(code(runtime).equals(before),"Runtime executable or selected package-code bytes changed during analysis");json(root.resolve("runtime-code-after.json"),before);
        } finally {
            if(process!=null){List<Long> live=cleanup(root,process,descendants);json(worker.resolve("SUPERVISOR-END.json"),Map.of("pid",process.pid(),"alive_after_cleanup",process.isAlive(),"owned_live_after_cleanup",live,"elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)));require(live.isEmpty(),"Owned supervisor or descendants survived bounded cleanup");}
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown owns cleanup. */}
        }
    }
    private static void remember(Process process,List<ProcessHandle> result) {
        synchronized(result){process.descendants().forEach(child->{if(result.stream().noneMatch(old->old.pid()==child.pid()))result.add(child);});}
    }
    private static List<Long> cleanup(Path root,Process process,List<ProcessHandle> descendants) {
        boolean interrupted=Thread.interrupted();
        if(process.isAlive()) {
            try{if(!Files.exists(root.resolve("CANCEL_REQUESTED")))write(root.resolve("CANCEL_REQUESTED"),"Owned Java caller requested cleanup\n");}catch(IOException ignored){/* ProcessHandle cleanup remains available. */}
            try{process.waitFor(10,TimeUnit.SECONDS);}catch(InterruptedException failure){interrupted=true;}
        }
        remember(process,descendants);var handles=new ArrayList<ProcessHandle>();synchronized(descendants){handles.addAll(descendants);}java.util.Collections.reverse(handles);handles.add(process.toHandle());
        for(ProcessHandle handle:handles)if(handle.isAlive())handle.destroy();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException failure){interrupted=true;}
        for(ProcessHandle handle:handles)if(handle.isAlive())handle.destroyForcibly();
        until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<until)try{Thread.sleep(25);}catch(InterruptedException failure){interrupted=true;}
        List<Long> live=handles.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();
        if(interrupted)Thread.currentThread().interrupt();
        return live;
    }
}
