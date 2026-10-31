/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Bounded complete study-owned tree: full bytes, aliases, empty directories and access/mode identity. */
final class RepeatOwnedTree {
    private RepeatOwnedTree() { }
    record Entry(String kind,long bytes,String sha256,int mode,boolean readable,boolean executable) { }
    private record Stamp(Object key,long bytes,FileTime modified,FileTime created,Object changed,int mode,boolean directory) { }
    static Map<?,?> snapshot(Path supplied,RepeatBundle.Limits limits,Set<String> excluded,Runnable check)throws IOException{
        Path root=supplied.toAbsolutePath().normalize();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.toRealPath().equals(root))throw new IOException("Original owned tree requires a canonical directory");
        var entries=new TreeMap<String,Entry>();var observed=new TreeMap<Path,Stamp>();long[] bytes={0},metadata={4096};int[] members={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            private String name(Path path){return path.equals(root)?".":root.relativize(path).toString().replace('\\','/');}
            private void reserve(Path path)throws IOException{
                check.run();if(++members[0]>limits.members())throw new IOException("Complete original owned-tree member capacity exceeded");
                metadata[0]=Math.addExact(metadata[0],512L+8L*(name(path).length()+root.toString().length()));
                if(metadata[0]>RepeatRuntime.METADATA)throw new IOException("Complete original owned-tree metadata capacity exceeded");
                if(!path.toRealPath().equals(path))throw new IOException("Original owned tree contains a linked/noncanonical member");
            }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attr)throws IOException{
                reserve(path);if(excluded.contains(name(path)))throw new IOException("Excluded seal names must be regular files");
                var stamp=stamp(path);observed.put(path,stamp);entries.put(name(path),new Entry("DIRECTORY",0,null,stamp.mode(),Files.isReadable(path),Files.isExecutable(path)));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attr)throws IOException{
                reserve(path);if(!attr.isRegularFile())throw new IOException("Original owned tree contains a link/nonregular file");
                var before=stamp(path);bytes[0]=Math.addExact(bytes[0],before.bytes());if(bytes[0]>limits.bytes())throw new IOException("Complete original owned-tree byte capacity exceeded");
                observed.put(path,before);
                if(!excluded.contains(name(path))){String hash=RepeatInputs.hash(path,limits.bytes(),check);if(!before.equals(stamp(path)))throw new IOException("Original owned file changed while hashing");
                    entries.put(name(path),new Entry("REGULAR",before.bytes(),hash,before.mode(),Files.isReadable(path),Files.isExecutable(path)));}
                return FileVisitResult.CONTINUE;
            }
        });
        for(var entry:observed.entrySet()){check.run();if(!entry.getValue().equals(stamp(entry.getKey())))throw new IOException("Original owned-tree population/metadata changed during census");}
        return RepeatBundle.object(RepeatBundle.json(entries));
    }
    private static Stamp stamp(Path path)throws IOException{
        var attr=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);Object changed=null;int mode=-1;
        try{changed=Files.getAttribute(path,"unix:ctime",LinkOption.NOFOLLOW_LINKS);mode=((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&07777;}
        catch(UnsupportedOperationException|IllegalArgumentException unavailable){/* basic file identity remains required */}
        return new Stamp(attr.fileKey(),attr.size(),attr.lastModifiedTime(),attr.creationTime(),changed,mode,attr.isDirectory());
    }
}
