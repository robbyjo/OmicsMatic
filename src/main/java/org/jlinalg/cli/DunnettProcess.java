/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Fixed original R worker. Timeout, cancellation and sampled output cap; no OS sandbox claim. */
final class DunnettProcess {
    private DunnettProcess() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    private record Owned(ProcessHandle handle, Optional<Instant> start) {
        boolean alive(){return handle.isAlive() && start.isPresent() && handle.info().startInstant().equals(start);}
    }
    private static void discover(Process p,Map<Long,Owned> known){OwnedProcesses.descendants(p).forEach(h->known.putIfAbsent(h.pid(),new Owned(h,h.info().startInstant())));}
    private static List<Map<String,Object>> stop(Process p,Map<Long,Owned> known) {
        boolean interrupted=Thread.interrupted(); discover(p,known);
        var handles=new ArrayList<>(known.values()); Collections.reverse(handles);
        if(p.isAlive() && p.info().startInstant().isEmpty())p.destroyForcibly();
        for(Owned h:handles)if(h.alive())h.handle.destroy();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
        while(handles.stream().anyMatch(Owned::alive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(Owned h:handles)if(h.alive())h.handle.destroyForcibly();
        deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(Owned::alive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        var result=new ArrayList<Map<String,Object>>();
        for(Owned h:handles)result.add(Map.of("pid",h.handle.pid(),"same_owned_process_alive",h.alive(),"identity_available",h.start.isPresent()));
        if(interrupted)Thread.currentThread().interrupt();return result;
    }
    private static void capacity(Path work,long cap)throws IOException {
        for(int attempt=0;attempt<8;attempt++)try {
            long size=0,count=0;
            try(var paths=Files.walk(work)) {
                var it=paths.iterator();while(it.hasNext()) {
                    var a=Files.readAttributes(it.next(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                    if(a.isSymbolicLink())throw new IOException("Worker created an unsupported link");
                    if(a.isDirectory())continue;
                    if(!a.isRegularFile()||++count>10000||a.size()>cap-size)throw new IOException("Worker output capacity exceeded; no result admitted");
                    size+=a.size();
                }
            }
            return;
        } catch(NoSuchFileException transientRemoval){/* R removes temporary files during execution. */}
          catch(UncheckedIOException e){if(!(e.getCause() instanceof NoSuchFileException))throw e;}
        throw new IOException("Worker temporary population remained unstable across eight full scans");
    }
    static void run(Path work,Path executable,Path home,long seconds,long bytes)throws Exception {
        if(!Files.isRegularFile(executable,LinkOption.NOFOLLOW_LINKS)||!Files.isExecutable(executable))throw new Unavailable("Permitted Rscript unavailable; dependent analysis skipped");
        List<String> command=List.of(executable.toString(),"--vanilla","worker.R");
        var builder=new ProcessBuilder(command).directory(work.toFile()).redirectOutput(work.resolve("stdout.log").toFile()).redirectError(work.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("LANG","LANGUAGE","R_HOME","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS");
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||remove.contains(k));
        builder.environment().putAll(Map.of("R_HOME",home.toString(),"LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1","TMPDIR",work.toString(),"TMP",work.toString(),"TEMP",work.toString()));
        Map<Long,Owned> known=Collections.synchronizedMap(new LinkedHashMap<>());var owned=new AtomicReference<Process>();
        Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)synchronized(known){stop(p,known);}},"Dunnett-owned-worker-cleanup");
        var receipt=new LinkedHashMap<String,Object>();receipt.put("command",command);receipt.put("scope","Wall-time and sampled output bytes/file count; cleanup of discovered owned processes by start identity. No hard memory/output reservation or OS containment; trusted R executes with account permissions.");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;long start=System.nanoTime();
        try {
            capacity(work,bytes);if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Rscript could not start; dependent analysis skipped",e);}
            owned.set(process);known.put(process.pid(),new Owned(process.toHandle(),process.info().startInstant()));receipt.put("owned_pid",process.pid());process.getOutputStream().close();
            while(true) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                synchronized(known){discover(process,known);if(known.values().stream().anyMatch(h->h.handle.isAlive()&&h.start.isEmpty()))throw new IOException("Owned process start identity unavailable");}
                if(Files.exists(work.getParent().resolve("CANCEL")))throw new IOException("Dunnett cancelled by CANCEL marker");
                if(System.nanoTime()-start>TimeUnit.SECONDS.toNanos(seconds))throw new IOException("Original Dunnett worker timed out");
                capacity(work,bytes);if(process.waitFor(50,TimeUnit.MILLISECONDS))break;
            }
            synchronized(known){discover(process,known);if(known.values().stream().anyMatch(Owned::alive))throw new IOException("Owned descendant remained alive after R worker exit");}
            capacity(work,bytes);receipt.put("exit_code",process.exitValue());
            if(process.exitValue()==3 && Files.isRegularFile(work.resolve("worker-status.txt")) && Files.readString(work.resolve("worker-status.txt")).strip().equals("SKIP3_DUNNETT_RUNTIME_UNAVAILABLE"))throw new Unavailable("Original R/package unavailable or wrong version; read worker/original-error.txt and skip dependent analysis");
            if(process.exitValue()!=0)throw new IOException("Original Dunnett inference failed, exit "+process.exitValue()+"; read worker logs/original-error.txt");
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
        finally {
            if(process!=null)synchronized(known){receipt.put("owned_process_cleanup",stop(process,known));}
            receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));owned.set(null);
            try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shuttingDown){/* Hook owns cleanup. */}
            boolean interrupted=Thread.interrupted();try{NextflowFiles.writeJson(work.resolve("process.json"),receipt);}finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
}
