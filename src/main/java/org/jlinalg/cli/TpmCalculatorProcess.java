/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Fixed Java worker, captured streams and owned-child cleanup on Windows and Linux. */
final class TpmCalculatorProcess {
    private TpmCalculatorProcess() { }
    static void run(Path output,Path inputs,TpmCalculatorRequest.Request request,TpmCalculatorRecords.Implementation implementation)throws IOException {
        Path work=Files.createDirectory(output.resolve("worker")),tmp=Files.createDirectory(work.resolve("tmp"));
        Path recipe=work.resolve("REQUEST.json");
        TpmCalculatorRecords.json(recipe,Map.of("schema",1,"effective",request.effective(),"input_root",inputs.toString(),
            "output_root",output.resolve("products").toString(),"implementation",implementation.identity()));
        var recipePin=TpmCalculatorRecords.pin(recipe,TpmCalculatorRecords.METADATA);
        Path javaExecutable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toAbsolutePath().normalize();
        try{TpmCalculatorRecords.identity(javaExecutable,Long.MAX_VALUE);}catch(IOException e){throw new TpmCalculatorRecords.Unavailable("Supply the permitted Java17+ runtime executable: "+javaExecutable,e);}
        String cp=String.join(java.io.File.pathSeparator,implementation.classpath().stream().map(Path::toString).toList());
        var command=new ArrayList<String>(List.of(javaExecutable.toString(),"-Xmx"+request.limits().heapMiB()+"m","-XX:-UsePerfData",
            "-XX:ErrorFile="+work.resolve("jvm-fatal-%p.log"),"-Dfile.encoding=UTF-8","-Duser.language=en","-Duser.country=US","-Duser.timezone=UTC",
            "-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dsun.stdout.encoding=UTF-8","-Dsun.stderr.encoding=UTF-8",
            "-Djava.io.tmpdir="+tmp,"-Dsamjdk.use_libdeflate=false","-cp",cp,TpmCalculatorWorker.class.getName(),recipe.toString()));
        var env=new LinkedHashMap<String,String>();
        if(System.getProperty("os.name").startsWith("Windows"))for(String key:List.of("SystemRoot","WINDIR")){String value=System.getenv(key);if(value!=null)env.put(key,value);}
        env.put("HOME",work.toString());env.put("USERPROFILE",work.toString());env.put("TMP",tmp.toString());env.put("TEMP",tmp.toString());env.put("TMPDIR",tmp.toString());env.put("LANG","C.UTF-8");env.put("LC_ALL","C.UTF-8");
        Process child=null;Thread stdout=null,stderr=null;IOException failure=null;long began=System.nanoTime();
        var handle=new AtomicReference<Process>();var streamFailure=new AtomicReference<IOException>();var logged=new AtomicLong();
        Thread hook=new Thread(()->{Process p=handle.get();if(p!=null&&p.isAlive())p.destroyForcibly();},"native-tpmcalculator-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            var builder=new ProcessBuilder(command).directory(work.toFile());builder.environment().clear();builder.environment().putAll(env);
            try{child=builder.start();handle.set(child);child.getOutputStream().close();}
            catch(IOException e){throw new TpmCalculatorRecords.Unavailable("Permitted native Java worker could not start; supply a usable Java runtime and process permission",e);}
            stdout=pump(child.getInputStream(),work.resolve("stdout.bin"),logged,request.limits().bundleBytes(),streamFailure);
            stderr=pump(child.getErrorStream(),work.resolve("stderr.bin"),logged,request.limits().bundleBytes(),streamFailure);
            TpmCalculatorRecords.json(work.resolve("LAUNCH.json"),Map.of("command",command,"environment",env,"pid",child.pid(),"started_utc",Instant.now().toString(),
                "heap_mib",request.limits().heapMiB(),"wall_seconds",request.limits().wallSeconds(),"external_quantification_program",false,
                "limits_scope","JVM heap bound; monitored wall/workspace/files/streams. These are not OS total-memory or filesystem quotas."));
            for(;;) {
                TpmCalculatorRecords.check();if(streamFailure.get()!=null)throw streamFailure.get();
                if(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-began)>=request.limits().wallSeconds())throw new IOException("Native Java worker exceeded the declared wall_seconds; retain partials and retry NEW paths with permitted adequate limits");
                TpmCalculatorRecords.inventory(output,request.limits().bundleBytes(),request.limits().files(),Set.of(),false);
                if(child.waitFor(100,TimeUnit.MILLISECONDS))break;
            }
            stdout.join();stderr.join();if(streamFailure.get()!=null)throw streamFailure.get();
            if(child.exitValue()!=0)throw new IOException("Native Java worker failed with exit "+child.exitValue()+"; read worker/stderr.bin, including any original parser, heap or post-return recording error");
            if(!TpmCalculatorRecords.pin(recipe,TpmCalculatorRecords.METADATA).equals(recipePin))throw new IOException("Native worker recipe changed during computation");
        }catch(InterruptedException e){Thread.currentThread().interrupt();failure=new IOException("Native Java worker interrupted; no complete result",e);}
        catch(IOException e){failure=e;}
        finally {
            boolean interrupted=Thread.interrupted();
            try {
                if(child!=null&&child.isAlive()) {child.destroy();if(!child.waitFor(2,TimeUnit.SECONDS)){child.destroyForcibly();if(!child.waitFor(5,TimeUnit.SECONDS))throw new IOException("Owned native worker remains alive; no complete result");}}
                for(Thread t:new Thread[]{stdout,stderr})if(t!=null){t.join(5000);if(t.isAlive())throw new IOException("Native stream capture did not finish; no complete result");}
                if(child!=null)TpmCalculatorRecords.json(work.resolve("EXIT.json"),Map.of("pid",child.pid(),"exit_code",child.exitValue(),"elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began),
                    "owned_worker_alive",child.isAlive(),"captured_stream_bytes",logged.get(),"ended_utc",Instant.now().toString()));
            }catch(IOException|InterruptedException e){if(e instanceof InterruptedException)interrupted=true;if(failure==null)failure=new IOException("Native worker cleanup/recording failed",e);else failure.addSuppressed(e);}
            finally{handle.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown already owns the hook. */}if(interrupted)Thread.currentThread().interrupt();}
        }
        if(failure!=null)throw failure;
    }
    private static Thread pump(InputStream source,Path output,AtomicLong used,long maximum,AtomicReference<IOException> failure) {
        Thread thread=new Thread(()->{
            try(source;var out=Files.newOutputStream(output,java.nio.file.StandardOpenOption.CREATE_NEW)) {
                byte[] b=new byte[8192];int n;
                while((n=source.read(b))!=-1) {
                    long offset=used.getAndAdd(n);long remaining=maximum-offset;int keep=(int)Math.min(n,Math.max(0,remaining));
                    if(keep>0)out.write(b,0,keep);if(keep!=n)throw new IOException("Native captured streams exceeded the declared bundle capacity; partial streams retained");
                }
            }catch(IOException e){failure.compareAndSet(null,e);}
        },"native-tpmcalculator-stream");thread.setDaemon(true);thread.start();return thread;
    }
}
