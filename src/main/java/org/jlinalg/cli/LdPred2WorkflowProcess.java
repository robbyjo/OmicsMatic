/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.LdPred2WorkflowFiles.*;

/** Owned child execution, with a JVM heap cap and observed work/time limits. */
final class LdPred2WorkflowProcess {
    private record Child(ProcessHandle handle,Instant start) {}
    private static final class Owner {
        private final Process root;
        private final Map<Long,Child> children=new LinkedHashMap<>();
        Owner(Process root){this.root=root;}
        synchronized void observe(){
            List<ProcessHandle> parents=new ArrayList<>();parents.add(root.toHandle());for(Child c:children.values())if(matches(c))parents.add(c.handle);
            for(ProcessHandle parent:parents)for(ProcessHandle child:OwnedProcesses.descendants(parent).toList())child.info().startInstant().ifPresent(start->children.putIfAbsent(child.pid(),new Child(child,start)));
        }
        synchronized void stop(){
            observe();for(Child child:children.values())if(matches(child))child.handle.destroyForcibly();if(root.isAlive())root.destroyForcibly();
            boolean interrupted=Thread.interrupted();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            try{while(System.nanoTime()<until&&(root.isAlive()||children.values().stream().anyMatch(LdPred2WorkflowProcess::matches))){for(Child child:children.values())if(matches(child))child.handle.destroyForcibly();try{Thread.sleep(10);}catch(InterruptedException e){interrupted=true;}}}
            finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    private LdPred2WorkflowProcess() {}
    private static boolean matches(Child c){return c.handle.isAlive()&&c.handle.info().startInstant().map(c.start::equals).orElse(false);}
    static void capacity(Path root,Config config)throws IOException {
        long bytes=0,entries=0;try(var paths=Files.walk(root)) {
            var it=paths.iterator();while(it.hasNext()){Path p=it.next();if(++entries>config.integer("max_files"))throw new IOException("Work entry count exceeds max_files");if(Files.isSymbolicLink(p))throw new IOException("Unexpected work alias: "+p);if(Files.isRegularFile(p)){bytes=Math.addExact(bytes,Files.size(p));if(bytes>config.number("max_work_bytes"))throw new IOException("Work bytes exceed max_work_bytes; no result accepted");}}
        }
    }
    static void execute(Path root,Config c)throws Exception {
        boolean windows=System.getProperty("os.name").startsWith("Windows");Path jvm=Path.of(System.getProperty("java.home"),"bin",windows?"java.exe":"java");
        if(!Files.isExecutable(jvm))throw new Prerequisite("Worker JVM unavailable: "+jvm+". Supply a permitted Java runtime or skip dependent analysis.");
        var classpath=new java.util.LinkedHashSet<String>();
        classpath.add(Path.of(LdPred2WorkflowWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        classpath.add(Path.of(jdistlib.Normal.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        var resource=LdPred2WorkflowWorker.class.getResource(LdPred2OriginalRuntime.RESOURCE);
        if(resource!=null&&resource.getProtocol().equals("file")){Path base=Path.of(resource.toURI());for(int i=0;i<Path.of(LdPred2OriginalRuntime.RESOURCE.substring(1)).getNameCount();i++)base=base.getParent();classpath.add(base.toString());}
        for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator),-1))classpath.add(Path.of(entry.isEmpty()?".":entry).toAbsolutePath().normalize().toString());
        String cp=String.join(java.io.File.pathSeparator,classpath);
        List<String> command=new ArrayList<>();
        if(System.getProperty("os.name").equals("Linux")&&Files.isExecutable(Path.of("/usr/bin/setpriv")))command.addAll(List.of("/usr/bin/setpriv","--pdeathsig","KILL","--"));
        command.addAll(List.of(jvm.toString(),"-Xmx"+c.get("java_heap_mb")+"m","-cp",cp,LdPred2WorkflowWorker.class.getName(),root.toString()));
        write(root.resolve("worker-command.txt"),String.join("\n",command)+"\n");
        Process child=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(root.resolve("worker.stdout").toFile()).redirectError(root.resolve("worker.stderr").toFile()).start();
        Owner owner=new Owner(child);Thread shutdown=new Thread(owner::stop,"ldpred2-owned-child-shutdown");Runtime.getRuntime().addShutdownHook(shutdown);
        long start=System.nanoTime();boolean successful=false;
        try {
            while(!child.waitFor(20,TimeUnit.MILLISECONDS)) {
                owner.observe();capacity(root,c);
                if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>c.number("wall_ms"))throw new IOException("LDpred2 worker exceeded wall_ms; no accepted result");
            }
            owner.observe();capacity(root,c);
            if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>c.number("wall_ms"))throw new IOException("LDpred2 worker exceeded wall_ms; no accepted result");
            int code=child.exitValue();write(root.resolve("worker-exit.txt"),Integer.toString(code)+"\n");
            if(code==3)throw new Prerequisite("Original runtime prerequisite unavailable; read worker.stderr and runtime.stderr. Supply permitted setup or skip dependent analysis.");
            if(code!=0)throw new IOException("LDpred2 worker failed with exit "+code+"; read worker.stderr and runtime.stderr; no accepted result");
            successful=true;
        } finally {
            if(!successful)owner.stop();
            try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException ignored){}
        }
    }
}
