/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shutdown cleanup for descendants admitted by the unique owned working directory. */
final class FavorProcessGuard implements AutoCloseable {
    private record Owned(ProcessHandle handle,Instant started) {
        boolean same(){return handle.info().startInstant().filter(started::equals).isPresent();}
    }
    private final Path work;private final String label;private final Map<Long,Owned> owned=new ConcurrentHashMap<>();private final Thread hook;
    private FavorProcessGuard(Path work,String label)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!label.matches("[a-z0-9-]+")||!work.toRealPath().equals(work))throw new IOException("FAVOR shutdown guard requires the canonical owned Linux work directory");
        this.work=work;this.label=label;hook=new Thread(this::shutdown,"favor-owned-"+label+"-shutdown");Runtime.getRuntime().addShutdownHook(hook);
    }
    static FavorProcessGuard register(Path work,String label)throws IOException {return new FavorProcessGuard(work,label);}
    void observe(){
        ProcessHandle.current().descendants().forEach(handle->{
            try{if(Path.of("/proc",Long.toString(handle.pid()),"cwd").toRealPath().equals(work)){admit(handle);handle.descendants().forEach(this::admit);}}
            catch(IOException ended){/* A vanished child cannot be admitted by a stale PID. */}
        });
    }
    private void admit(ProcessHandle handle){handle.info().startInstant().ifPresent(started->owned.putIfAbsent(handle.pid(),new Owned(handle,started)));}
    private void shutdown(){
        var record=new LinkedHashMap<String,Object>();record.put("status","shutdown-cleanup-started");record.put("work",work.toString());record.put("operation",label);record.put("started_at",Instant.now().toString());
        try {
            observe();for(var child:owned.values())if(child.same())child.handle().descendants().forEach(this::admit);
            for(var child:owned.values())if(child.same()&&child.handle().isAlive())child.handle().destroyForcibly();
            long deadline=System.nanoTime()+10000000000L;boolean alive;
            do{alive=false;for(var child:owned.values())if(active(child))alive=true;if(alive)Thread.sleep(20);}while(alive&&System.nanoTime()<deadline);
            record.put("owned_processes",owned.values().stream().map(child->Map.of("pid",child.handle().pid(),"started_at",child.started().toString())).toList());record.put("owned_processes_alive",alive);record.put("status",alive?"shutdown-cleanup-failed":"shutdown-owned-processes-terminal");
        } catch(Exception failure){record.put("status","shutdown-cleanup-failed");record.put("cause",failure.toString());if(failure instanceof InterruptedException)Thread.currentThread().interrupt();}
        record.put("finished_at",Instant.now().toString());record.put("required_action","This interrupted workflow is incomplete. Keep its provisional session,shutdown ledger and logs;verify any recorded nonterminal owned process before rerunning to a NEW output.");
        try{Files.writeString(work.resolve("shutdown-"+label+".json"),RegenieRuntime.json(record)+"\n",StandardOpenOption.CREATE_NEW);}
        catch(IOException failure){System.err.println("FAVOR SHUTDOWN CLEANUP DIAGNOSTIC FAILED: "+failure+". Required action: inspect the retained session and owned-process state;never admit interrupted results.");}
    }
    private static boolean active(Owned child)throws IOException {
        if(!child.same()||!child.handle().isAlive())return false;Path status=Path.of("/proc",Long.toString(child.handle().pid()),"status");
        try{return !HomerRuntime.terminalStatus(Files.readString(status));}catch(IOException ended){if(!child.handle().isAlive())return false;throw ended;}
    }
    @Override public void close(){try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shuttingDown){/* The hook owns cleanup during JVM shutdown. */}}
}
