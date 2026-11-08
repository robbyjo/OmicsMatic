/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.require;

/** Whole tree directory identities plus unchanged complete SHA256/leaf identity checks, without repeated ancestor realpath calls. */
final class LofteeRuntimeTree {
    private LofteeRuntimeTree() { }
    record Directory(String key,FileTime modified,int mode) { }
    record Snapshot(Path root,Map<Path,Directory> directories,Set<String> names) {
        void verify()throws IOException {Snapshot now=snapshot(root);require(directories.equals(now.directories)&&names.equals(now.names),"Runtime parent directories, links or file set changed during verification/execution: "+root);}
    }
    record Read(NextflowFiles.Pin pin,byte[] content) { }
    static Snapshot snapshot(Path root)throws IOException {
        NextflowFiles.directory(root);var directories=new LinkedHashMap<Path,Directory>();var names=new HashSet<String>();int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {
                require(++count[0]<=100000&&attrs.isDirectory()&&!attrs.isSymbolicLink(),"Runtime directory/link capacity exceeded");require(attrs.fileKey()!=null,"Runtime filesystem must expose stable directory identities; no ancestor verification fallback is inferred");directories.put(path,new Directory(attrs.fileKey().toString(),attrs.lastModifiedTime(),mode(path)));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {require(++count[0]<=100000&&(attrs.isRegularFile()||attrs.isSymbolicLink()),"Nonregular/oversized runtime tree");names.add(root.relativize(path).toString().replace('\\','/'));return FileVisitResult.CONTINUE;}
        });return new Snapshot(root,Collections.unmodifiableMap(directories),Collections.unmodifiableSet(names));
    }
    static Read read(Snapshot tree,Path file,long maximum,boolean content)throws IOException {
        require(file.isAbsolute()&&file.normalize().equals(file)&&file.startsWith(tree.root)&&tree.names.contains(tree.root.relativize(file).toString().replace('\\','/')),"Runtime file is outside the captured canonical directory tree");var before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);int mode=mode(file);long links=links(file);require(before.isRegularFile()&&!before.isSymbolicLink()&&before.size()<=maximum&&maximum>=0,"Missing, linked or oversized original runtime file: "+file);require(!content||before.size()<=536870912,"Runtime normalization byte capacity exceeded");
        MessageDigest sha;try{sha=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}var result=content?new ByteArrayOutputStream((int)Math.min(65536,before.size())):null;long count=0;
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){require(n<=maximum-count&&n<=before.size()-count,"Original runtime file grew during complete read");sha.update(buffer,0,n);if(result!=null)result.write(buffer,0,n);count+=n;}}
        var after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(after.isRegularFile()&&!after.isSymbolicLink()&&count==before.size()&&count==after.size()&&Objects.equals(before.fileKey(),after.fileKey())&&before.lastModifiedTime().equals(after.lastModifiedTime())&&mode==mode(file)&&links==links(file),"Original runtime leaf identity changed while reading: "+file);return new Read(new NextflowFiles.Pin(count,HexFormat.of().formatHex(sha.digest())),result==null?null:result.toByteArray());
    }
    private static int mode(Path file)throws IOException{return file.getFileSystem().supportedFileAttributeViews().contains("unix")?((Number)Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&0777:-1;}
    private static long links(Path file)throws IOException{return file.getFileSystem().supportedFileAttributeViews().contains("unix")?((Number)Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue():-1;}
}
