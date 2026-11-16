/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Linux-only real temporary-file churn control; permission errors remain failures. */
public final class SamplingRace {
    public static void main(String[] args)throws Exception {
        if(!System.getProperty("os.name").equals("Linux"))throw new IllegalStateException("Use the permitted Linux JVM");
        Path root=Path.of(args[0]);Files.createDirectory(root);
        var c=SambambaStreamingWorkflowFiles.config(SambambaStreamingWorkflowFiles.load(Path.of(args[1])));
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread writer=new Thread(()->{
            try{
                for(int i=0;i<2000;i++){
                    Path scratch=root.resolve("new.tmp"),target=root.resolve("sorted.0.bam");Files.write(scratch,new byte[]{1,2,3});
                    Files.move(scratch,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);Files.delete(target);
                }
            }catch(Throwable e){failure.set(e);}
        });
        writer.start();int samples=0,vanishedWalks=0;
        try{
            while(writer.isAlive()){
                try{SambambaStreamingWorkflowFiles.inventory(root,c,false);samples++;}
                catch(java.io.UncheckedIOException gone){
                    if(!(gone.getCause() instanceof java.nio.file.NoSuchFileException))throw gone;vanishedWalks++;
                }
            }
        }finally{writer.join();}
        if(failure.get()!=null)throw new IllegalStateException("Synthetic writer failed",failure.get());
        if(samples==0||SambambaStreamingWorkflowFiles.inventory(root,c,true).entries()!=0)throw new AssertionError("Concurrent sampling did not finish cleanly");
        System.out.println("PASS_LINUX_CONCURRENT_TEMPFILE_ATTRIBUTES samples="+samples+" transient_vanished_walks="+vanishedWalks+" permission_errors_suppressed=false");
    }
}
