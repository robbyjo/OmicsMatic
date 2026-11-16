/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.SambambaStreamingWorkflowFiles.*;

/** Owned Linux process execution. Address space is an OS limit; work/RSS are sampled. */
final class SambambaStreamingWorkflowProcess {
    record Execution(int exitCode,long elapsedMillis,long peakObservedRss,int rssSamples,List<Long> descendants) {
        Execution{descendants=List.copyOf(descendants);}
    }
    private record Identity(ProcessHandle handle,Instant start) { }
    private static final class Owner {
        final Process root;final Map<Long,Identity> descendants=new LinkedHashMap<>();
        Owner(Process root){this.root=root;}
        synchronized void observe()throws IOException {
            var handles=new ArrayList<ProcessHandle>();handles.add(root.toHandle());
            for(Identity row:descendants.values())if(matches(row))handles.add(row.handle);
            for(ProcessHandle p:handles)for(ProcessHandle child:p.descendants().toList()){
                if(!child.isAlive())continue;
                var start=child.info().startInstant();
                if(start.isEmpty()){if(child.isAlive())throw new IOException("Owned descendant identity is unavailable");continue;}
                descendants.putIfAbsent(child.pid(),new Identity(child,start.get()));
            }
        }
        synchronized List<Long> ids(){return List.copyOf(descendants.keySet());}
        synchronized long rss()throws IOException {
            long rss=SambambaStreamingWorkflowProcess.rss(root.toHandle());for(Identity row:descendants.values())if(matches(row))rss=Math.addExact(rss,SambambaStreamingWorkflowProcess.rss(row.handle));return rss;
        }
        synchronized void stop() {
            boolean interrupted=Thread.interrupted();
            try{
                try{observe();}catch(IOException ignored){ }
                var all=new ArrayList<>(descendants.values());Collections.reverse(all);
                for(Identity row:all)if(matches(row))row.handle.destroyForcibly();
                if(root.isAlive())root.destroyForcibly();
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(System.nanoTime()<until&&(root.isAlive()||all.stream().anyMatch(SambambaStreamingWorkflowProcess::matches))){
                    for(Identity row:all)if(matches(row))row.handle.destroyForcibly();
                    try{Thread.sleep(5);}catch(InterruptedException e){interrupted=true;}
                }
            }finally{if(interrupted)Thread.currentThread().interrupt();}
        }
        synchronized boolean anyRunning(){return root.isAlive()||descendants.values().stream().anyMatch(SambambaStreamingWorkflowProcess::matches);}
    }
    private SambambaStreamingWorkflowProcess(){ }
    private static boolean matches(Identity row){
        return row.handle.isAlive()&&row.handle.info().startInstant().map(row.start::equals).orElse(false)&&running(row.handle.pid());
    }
    private static boolean running(long pid){
        try{String text=Files.readString(Path.of("/proc",Long.toString(pid),"stat"));int end=text.lastIndexOf(')');return end>=0&&text.charAt(end+2)!='Z';}
        catch(IOException|IndexOutOfBoundsException e){return false;}
    }
    private static long rss(ProcessHandle p)throws IOException {
        if(!p.isAlive())return 0;
        try{
            for(String line:Files.readAllLines(Path.of("/proc",Long.toString(p.pid()),"status")))
                if(line.startsWith("VmRSS:"))return Long.parseLong(line.substring(6).strip().split("\\s+")[0])*1024;
            return 0;
        }catch(IOException e){if(p.isAlive()&&running(p.pid()))throw new IOException("Cannot observe owned process RSS",e);return 0;}
    }
    private static Properties startRecord(Process p,List<String> source,List<String> launch){
        Properties record=new Properties();record.setProperty("pid",Long.toString(p.pid()));
        record.setProperty("parent_pid",Long.toString(ProcessHandle.current().pid()));
        record.setProperty("start_instant",p.info().startInstant().map(Instant::toString).orElse("exited-before-observation"));
        record.setProperty("parent_death_signal_requested","KILL");record.setProperty("address_space_limit_method","prlimit_RLIMIT_AS");
        record.setProperty("source_argc",Integer.toString(source.size()));for(int i=0;i<source.size();i++)record.setProperty("source_argv."+i,source.get(i));
        record.setProperty("launch_argc",Integer.toString(launch.size()));for(int i=0;i<launch.size();i++)record.setProperty("launch_argv."+i,launch.get(i));
        try{
            String text=Files.readString(Path.of("/proc",Long.toString(p.pid()),"stat"));
            record.setProperty("linux_start_ticks",text.substring(text.lastIndexOf(')')+2).split("\\s+")[19]);
        }catch(IOException|IndexOutOfBoundsException e){record.setProperty("linux_start_ticks","exited-before-observation");}
        return record;
    }
    static Execution execute(List<String> command,Path out,String phase,Config config,long wallMillis,long addressBytes)throws Exception {
        if(!phase.matches("[a-z0-9-]+"))throw new IllegalArgumentException("Invalid fixed process phase");
        List<String> launch=new ArrayList<>(List.of("/usr/bin/setpriv","--pdeathsig","KILL","/usr/bin/prlimit","--as="+addressBytes,"--"));launch.addAll(command);
        Path stdout=out.resolve(phase+".stdout"),stderr=out.resolve(phase+".stderr");
        if(Files.exists(stdout)||Files.exists(stderr))throw new IOException("Process consoles already exist");
        var builder=new ProcessBuilder(launch).directory(out.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        builder.environment().clear();builder.environment().putAll(Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","TZ","UTC"));
        inventory(out,config,false);long start=System.nanoTime(),peak=0;int samples=0;Process process=null;Owner owner=null;
        Thread hook=null;Exception failure=null;int exit=-1;long processElapsed=-1;
        try{
            process=builder.start();process.getOutputStream().close();owner=new Owner(process);Owner captured=owner;
            hook=new Thread(captured::stop,"sambamba-owned-"+phase);Runtime.getRuntime().addShutdownHook(hook);
            store(out.resolve(phase+".pid.properties"),startRecord(process,command,launch));
            while(process.isAlive()){
                owner.observe();peak=Math.max(peak,owner.rss());samples++;
                if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>wallMillis)throw new IOException("Original "+phase+" process exceeded wall_ms="+wallMillis);
                try{inventory(out,config,false);}catch(UncheckedIOException e){if(!(e.getCause() instanceof java.nio.file.NoSuchFileException))throw e;}
                process.waitFor(5,TimeUnit.MILLISECONDS);
            }
            exit=process.waitFor();processElapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            if(processElapsed>wallMillis)throw new IOException("Original "+phase+" process exceeded wall_ms="+wallMillis);
            owner.observe();
            if(owner.anyRunning())throw new IOException("Owned descendant remains running after the original process returned");
        }catch(Exception e){failure=e;}
        finally{
            if(owner!=null){owner.stop();if(owner.anyRunning()&&failure==null)failure=new IOException("Owned process cleanup could not be confirmed");}
            if(process!=null&&!process.isAlive())exit=process.exitValue();
            if(processElapsed<0)processElapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            if(hook!=null)try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){ }
        }
        long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
        Properties result=new Properties();result.setProperty("phase",phase);result.setProperty("exit_code",Integer.toString(exit));
        result.setProperty("process_elapsed_ms",Long.toString(processElapsed));
        result.setProperty("elapsed_ms",Long.toString(elapsed));result.setProperty("wall_limit_ms",Long.toString(wallMillis));
        result.setProperty("address_space_limit_bytes",Long.toString(addressBytes));result.setProperty("peak_observed_rss_bytes",Long.toString(peak));
        result.setProperty("rss_samples",Integer.toString(samples));result.setProperty("work_limits_are_sampled_not_os_quota","true");
        result.setProperty("owned_descendant_pids",owner==null?"":owner.ids().toString());
        result.setProperty("owned_processes_running_after_cleanup",Boolean.toString(owner!=null&&owner.anyRunning()));
        if(failure!=null)result.setProperty("failure",failure.toString());
        store(out.resolve(phase+".process.properties"),result);
        if(failure!=null)throw failure;
        inventory(out,config,false);
        return new Execution(exit,elapsed,peak,samples,owner==null?List.of():owner.ids());
    }
}
