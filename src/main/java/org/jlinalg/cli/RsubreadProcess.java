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
import static org.jlinalg.cli.RsubreadSupport.*;

/** Owned original R worker with timeout, sampled output cap and cancellation. Not a hard OS sandbox. */
final class RsubreadProcess {
    private RsubreadProcess() { }
    private record Owned(ProcessHandle handle, Optional<Instant> start) {
        boolean sameAlive() { return start.isPresent() && handle.isAlive() && handle.info().startInstant().equals(start); }
    }
    private static void discover(Process process,Map<Long,Owned> all) {
        process.descendants().forEach(p->all.putIfAbsent(p.pid(),new Owned(p,p.info().startInstant())));
    }
    private static List<Map<String,Object>> stop(Process process,Map<Long,Owned> all) {
        boolean interrupted=Thread.interrupted();discover(process,all);all.putIfAbsent(process.pid(),new Owned(process.toHandle(),process.info().startInstant()));
        if(process.isAlive() && all.get(process.pid()).start.isEmpty())process.destroyForcibly();
        List<Owned> handles=new ArrayList<>(all.values());Collections.reverse(handles);
        for(Owned p:handles)if(p.sameAlive())p.handle.destroy();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
        while(handles.stream().anyMatch(Owned::sameAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(Owned p:handles)if(p.sameAlive())p.handle.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(Owned::sameAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        List<Map<String,Object>> result=new ArrayList<>();for(Owned p:handles)result.add(Map.of("pid",p.handle.pid(),"same_owned_process_alive",p.sameAlive(),"start_identity_available",p.start.isPresent()));
        if(interrupted)Thread.currentThread().interrupt();return result;
    }
    private static int footprint(Path home,long cap) throws IOException {
        for(int attempt=0;attempt<8;attempt++)try{footprintOnce(home,cap);return attempt;}
        catch(NoSuchFileException vanished){/* Original R may remove its own temporary directory during a sampled scan. Retry a complete scan. */}
        catch(UncheckedIOException vanished){if(!(vanished.getCause() instanceof NoSuchFileException))throw vanished;}
        throw new IOException("Worker temporary population remained unstable across eight complete output-scan attempts");
    }
    private static void footprintOnce(Path home,long cap) throws IOException {
        long size=0,count=0;
        try(var paths=Files.walk(home)) {
            var iterator=paths.iterator();while(iterator.hasNext()) {
                Path path=iterator.next();var attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(attributes.isSymbolicLink())throw new IOException("Worker created an unsupported link");
                if(attributes.isDirectory())continue;
                if(!attributes.isRegularFile()||++count>100000)throw new IOException("Worker file population capacity exceeded");
                long bytes=attributes.size();if(bytes>cap-size)throw new IOException("maximum_output_bytes exceeded; partial outputs are not admitted");size+=bytes;
            }
        }
    }
    static void run(Path root,Path work,Path rscript,Path rhome,List<String> arguments,long timeout,long cap,Map<String,Object> receipt) throws Exception {
        if(!Files.isRegularFile(rscript,LinkOption.NOFOLLOW_LINKS)||!Files.isExecutable(rscript))throw new Unavailable("Declared Rscript unavailable/forbidden; dependent analysis skipped");
        List<String> command=new ArrayList<>(List.of(rscript.toString(),"--vanilla"));command.addAll(arguments);
        var builder=new ProcessBuilder(command).directory(work.toFile()).redirectOutput(work.resolve("stdout.log").toFile()).redirectError(work.resolve("stderr.log").toFile());
        Set<String> remove=Set.of("LANG","LANGUAGE","R_HOME","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS");
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||remove.contains(k));
        builder.environment().putAll(Map.of("R_HOME",rhome.toString(),"LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1","TMPDIR",work.toString(),"TMP",work.toString(),"TEMP",work.toString()));
        receipt.put("command",command);receipt.put("resource_scope","Wall-clock timeout, sampled output bytes/file population and discovered owned-process cleanup; no hard aggregate RSS/output reservation or OS containment");
        AtomicReference<Process> owned=new AtomicReference<>();Map<Long,Owned> descendants=Collections.synchronizedMap(new LinkedHashMap<>());
        Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)synchronized(descendants){stop(p,descendants);}},"Rsubread-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);long started=System.nanoTime();Process process=null;int retries=0;
        try {
            cancel(root);retries+=footprint(work,cap);
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Rscript could not start; dependent analysis skipped",e);}
            owned.set(process);descendants.put(process.pid(),new Owned(process.toHandle(),process.info().startInstant()));receipt.put("owned_pid",process.pid());receipt.put("started",Instant.now().toString());process.getOutputStream().close();
            while(true) {
                synchronized(descendants){discover(process,descendants);if(descendants.values().stream().anyMatch(p->p.start.isEmpty() && p.handle.isAlive()))throw new IOException("Cannot establish owned-process start identity; no analysis admitted");}cancel(root);
                if(System.nanoTime()-started>TimeUnit.SECONDS.toNanos(timeout))throw new IOException("Original Rsubread worker timed out");
                retries+=footprint(work,cap);if(process.waitFor(50,TimeUnit.MILLISECONDS))break;
            }
            synchronized(descendants){discover(process,descendants);if(descendants.values().stream().anyMatch(Owned::sameAlive))throw new IOException("Owned descendant remained alive after the original worker exited");}
            cancel(root);retries+=footprint(work,cap);receipt.put("exit_code",process.exitValue());
            if(process.exitValue()==3 && Files.isRegularFile(work.resolve("worker-status.txt")) && Files.readString(work.resolve("worker-status.txt")).strip().equals("SKIP3_ORIGINAL_RSUBREAD_RUNTIME_UNAVAILABLE"))throw new Unavailable("Original R/package/source guard unavailable or changed; read original-error.txt and skip dependent analysis");
            if(process.exitValue()!=0)throw new IOException("Original R worker failed with exit "+process.exitValue()+"; retain stderr, stdout and original-error.txt");
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
        finally {
            if(process!=null)synchronized(descendants){receipt.put("owned_process_cleanup",stop(process,descendants));}
            receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));owned.set(null);
            receipt.put("sampled_output_scan_transient_retries",retries);
            try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}
            boolean interrupted=Thread.interrupted();try{json(work.resolve("process.json"),receipt);}finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
}
