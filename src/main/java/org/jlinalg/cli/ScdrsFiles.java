/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.ScdrsJson.*;

/** Owned directories, exact byte inventories and supervised source processes. */
final class ScdrsFiles {
    private ScdrsFiles() { }
    static void newDirectory(Path path)throws IOException {
        need(!Files.exists(path,LinkOption.NOFOLLOW_LINKS),"Output already exists; preserve it and choose a NEW path");
        Path ancestor=path.getParent();while(ancestor!=null&&!Files.exists(ancestor,LinkOption.NOFOLLOW_LINKS))ancestor=ancestor.getParent();
        need(ancestor!=null&&ancestor.toRealPath().equals(ancestor),"Output ancestor must be canonical and unlinked");
        Files.createDirectories(path.getParent());Files.createDirectory(path);
    }
    static void protect(ScdrsRequest.Request request)throws IOException {
        if(Files.isRegularFile(request.profile())){
            Map<?,?> p=object(ScdrsJson.read(request.profile(),67108864));
            for(String key:List.of("environment","source"))if(p.get(key) instanceof String s){
                Path root=Path.of(s);if(root.isAbsolute())need(!request.out().startsWith(root)&&!root.startsWith(request.out()),"Output overlaps an immutable runtime root");
            }
        }
    }
    static Map<String,Object> inventory(Path root,ScdrsRequest.Limits limits,Set<String> excluded)throws IOException {
        var files=new TreeMap<String,Object>();long[] bytes={0};int[] entries={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a)throws IOException {
                need(++entries[0]<=limits.files(),"Bundle directory/file capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes a)throws IOException {
                String name=root.relativize(file).toString().replace('\\','/');ScdrsRuntime.member(name);
                need(++entries[0]<=limits.files()&&a.isRegularFile()&&!a.isSymbolicLink(),"Nonregular bundle file or file capacity exceeded");
                if(!excluded.contains(name)){
                    Map<String,Object> pin=ScdrsRuntime.pin(file,limits.outputBytes());
                    bytes[0]=Math.addExact(bytes[0],(Long)pin.get("bytes"));need(bytes[0]<=limits.outputBytes(),"Bundle byte capacity exceeded");files.put(name,pin);
                }
                return FileVisitResult.CONTINUE;
            }
        });return files;
    }
    static void monitor(Path root,ScdrsRequest.Limits limits)throws IOException {
        long[] bytes={0};int[] entries={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a)throws IOException {need(++entries[0]<=limits.files(),"Process bundle file/directory capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes a)throws IOException {
                need(++entries[0]<=limits.files()&&a.isRegularFile()&&!a.isSymbolicLink(),"Process created an unsupported file or exceeded file capacity");
                bytes[0]=Math.addExact(bytes[0],a.size());need(bytes[0]<=limits.outputBytes(),"Process bundle byte capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file,IOException error)throws IOException {if(error instanceof java.nio.file.NoSuchFileException)return FileVisitResult.CONTINUE;throw error;}
        });
    }
    static int supervise(List<String> command,Path out,ScdrsRequest.Limits limits)throws IOException,InterruptedException {
        ScdrsJson.write(out.resolve("source-process-command.json"),map("command",command,"shell",false));
        ProcessBuilder builder=new ProcessBuilder(command);builder.directory(out.toFile());builder.environment().clear();
        builder.environment().putAll(Map.ofEntries(Map.entry("PATH","/usr/bin:/bin"),Map.entry("HOME",out.toString()),
            Map.entry("LC_ALL","C.UTF-8"),Map.entry("LANG","C.UTF-8"),Map.entry("OPENBLAS_NUM_THREADS","1"),Map.entry("OMP_NUM_THREADS","1"),
            Map.entry("MKL_NUM_THREADS","1"),Map.entry("NUMBA_NUM_THREADS","1"),Map.entry("PYTHONDONTWRITEBYTECODE","1"),
            Map.entry("PYTHONNOUSERSITE","1"),Map.entry("PYTHONHASHSEED","0"),Map.entry("NUMBA_CACHE_DIR",out.resolve("derived-cache/numba").toString()),
            Map.entry("MPLCONFIGDIR",out.resolve("derived-cache/matplotlib").toString())));
        builder.redirectOutput(out.resolve("source.stdout").toFile());builder.redirectError(out.resolve("source.stderr").toFile());
        long started=System.nanoTime();Process process=builder.start();var descendants=new HashMap<Long,Instant>();String failure=null;boolean finished=false;
        try {
            while(true){
                process.descendants().forEach(p->p.info().startInstant().ifPresent(t->descendants.putIfAbsent(p.pid(),t)));
                if(process.waitFor(200,TimeUnit.MILLISECONDS)){finished=true;break;}
                if(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)>=limits.seconds()){failure="Source process exceeded wall_seconds";break;}
                try{monitor(out,limits);}catch(IOException capacity){failure=capacity.getMessage();break;}
            }
            if(!finished)throw new IOException(failure==null?"Source process interrupted":failure);
            ScdrsJson.write(out.resolve("source-process.json"),map("command",command,"pid",process.pid(),"exit_code",(long)process.exitValue(),
                "source_process_finished",true,"elapsed_milliseconds",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)));
            need(System.nanoTime()-started<=TimeUnit.SECONDS.toNanos(limits.seconds()),"Source process completed after declared wall_seconds; no result admission");
            for(var entry:descendants.entrySet())need(!alive(entry.getKey(),entry.getValue()),"Owned descendant remains active after source exit");
            return process.exitValue();
        } finally {
            if(process.isAlive())process.destroyForcibly();
            for(var entry:descendants.entrySet())if(alive(entry.getKey(),entry.getValue()))ProcessHandle.of(entry.getKey()).ifPresent(ProcessHandle::destroyForcibly);
            if(!finished){process.waitFor(30,TimeUnit.SECONDS);if(!Files.exists(out.resolve("source-process.json")))ScdrsJson.write(out.resolve("source-process.json"),map("pid",process.pid(),"source_process_finished",!process.isAlive(),"admitted",false,"cause",failure==null?"interrupted":failure));}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            while(descendants.entrySet().stream().anyMatch(e->alive(e.getKey(),e.getValue()))&&System.nanoTime()<deadline)Thread.sleep(50);
            var remaining=new ArrayList<Long>();for(var entry:descendants.entrySet())if(alive(entry.getKey(),entry.getValue()))remaining.add(entry.getKey());
            ScdrsJson.write(out.resolve("source-process-cleanup.json"),map("owned_descendants",(long)descendants.size(),"remaining_owned_pids",remaining,"parent_still_alive",process.isAlive()));
            need(remaining.isEmpty()&&!process.isAlive(),"Owned processes not confirmed stopped; inspect recorded PIDs before retry");
        }
    }
    private static boolean alive(long pid,Instant started){return ProcessHandle.of(pid).filter(p->p.isAlive()&&p.info().startInstant().map(started::equals).orElse(false)).isPresent();}
}
