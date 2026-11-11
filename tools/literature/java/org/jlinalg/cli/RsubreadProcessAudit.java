/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.RsubreadSupport.*;

/** Actual original-R process lifetime controls; these scripts are not scientific count fits. */
public final class RsubreadProcessAudit {
    private RsubreadProcessAudit() { }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static void stopped(Path worker) throws Exception {
        var receipt=read(worker.resolve("process.json"));require(receipt.containsKey("owned_pid"),"No original R process actually started");
        for(Object row:list(receipt.get("owned_process_cleanup")))require(!Boolean.TRUE.equals(object(row).get("same_owned_process_alive")),"Owned original R process survived cleanup");
    }
    private static void direct(Path root,Path rscript,Path rhome,String name,String code,long timeout,long cap,String expected) throws Exception {
        Path home=root.resolve(name);Files.createDirectory(home);Path worker=home.resolve("worker");Files.createDirectory(worker);Path script=worker.resolve("controlled-test.R");Files.writeString(script,code,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Exception problem=null;try{RsubreadProcess.run(home,worker,rscript,rhome,List.of(script.toString()),timeout,cap,new LinkedHashMap<>());}catch(Exception e){problem=e;}
        require(problem!=null && problem.toString().contains(expected),"Expected original process rejection absent: "+name+" "+problem);stopped(worker);json(home.resolve("audit.json"),Map.of("status","passed-actual-original-R-process-rejection","reason",problem.toString(),"analysis_admitted",false));
    }
    private static void asynchronous(Path root,Path rscript,Path rhome,String name,boolean interrupt) throws Exception {
        Path home=root.resolve(name);Files.createDirectory(home);Path worker=home.resolve("worker");Files.createDirectory(worker);Path script=worker.resolve("controlled-test.R");Files.writeString(script,"Sys.sleep(30)\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Map<String,Object> receipt=new ConcurrentHashMap<>();AtomicReference<Throwable> failure=new AtomicReference<>();AtomicBoolean preservedInterrupt=new AtomicBoolean();
        Thread thread=new Thread(()->{try{RsubreadProcess.run(home,worker,rscript,rhome,List.of(script.toString()),60,10000000,receipt);}catch(Throwable e){failure.set(e);}finally{preservedInterrupt.set(Thread.currentThread().isInterrupted());}},"actual-Rsubread-process-control");thread.start();
        long deadline=System.nanoTime()+10_000_000_000L;boolean observed=false;
        while(System.nanoTime()<deadline && thread.isAlive()) {
            Object pid=receipt.get("owned_pid");if(pid instanceof Number n && ProcessHandle.of(n.longValue()).filter(ProcessHandle::isAlive).isPresent()){observed=true;break;}Thread.sleep(10);
        }
        require(observed,"Cancellation/interrupt requires an authoritatively observed live original R PID");
        if(interrupt)thread.interrupt();else Files.writeString(home.resolve("CANCEL"),"cancel observed live original R worker\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        thread.join(15000);require(!thread.isAlive() && failure.get()!=null,"Original R operation did not fail and stop after cancellation/interrupt");
        if(interrupt)require(preservedInterrupt.get(),"Caller interrupt flag was lost");else require(failure.get().toString().contains("Cancellation requested"),"Cancellation was not reported");
        stopped(worker);json(home.resolve("audit.json"),Map.of("status","passed-actual-live-original-R-cancellation-or-interruption","observed_live_original_R",observed,"interrupt_preserved",interrupt?preservedInterrupt.get():false,"error",failure.get().toString(),"analysis_admitted",false));
    }
    public static void main(String[] args) throws Exception {
        require(args.length==3,"Require Rscript, R home and NEW output");Path script=Path.of(args[0]),home=Path.of(args[1]),out=Path.of(args[2]);require(!Files.exists(out),"Require NEW output");Files.createDirectories(out);
        direct(out,script,home,"timeout","Sys.sleep(30)\n",1,10000000,"timed out");
        direct(out,script,home,"output-cap","writeBin(raw(2000000), 'oversize.bin'); Sys.sleep(30)\n",60,10000,"maximum_output_bytes");
        asynchronous(out,script,home,"cancellation",false);asynchronous(out,script,home,"interruption",true);
        json(out.resolve("audit.json"),Map.of("status","passed-four-actual-original-R-owned-process-controls","actual_original_R_processes",4,"runtime_profile","Original permitted R4.6.1; test scripts exercise process mechanics, not scientific counting","analysis_admitted",false,"implementation_credit",false));
        System.out.println("PASS_FOUR_ACTUAL_ORIGINAL_R_PROCESS_CONTROLS");
    }
}
