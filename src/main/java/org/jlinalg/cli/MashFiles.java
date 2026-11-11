/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Bounded byte and directory census; linked evidence is never followed. */
final class MashFiles {
    private MashFiles() { }
    record Pin(long bytes,String sha256) { }
    record Inventory(Map<String,Pin> files,Set<String> directories) {
        Inventory {files=Map.copyOf(files);directories=Set.copyOf(directories);}
    }
    static void canonical(Path path)throws IOException {if(!path.isAbsolute()||!path.normalize().equals(path)||!path.toRealPath().equals(path))throw new IOException("Require a canonical unlinked mash path: "+path);}
    static void create(Path root)throws IOException {canonical(root.getParent());Files.createDirectory(root);if(root.getFileSystem().supportedFileAttributeViews().contains("posix"))Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));}
    static String relative(Path root,Path path){return root.relativize(path).toString().replace('\\','/');}
    static Pin pin(Path file,long maximum,Runnable check)throws IOException {
        canonical(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>maximum)throw new IOException("Missing/nonregular/oversized mash evidence file: "+file);
        try {var hash=MessageDigest.getInstance("SHA-256");long bytes=0;byte[] buffer=new byte[65536];try(var input=Files.newInputStream(file)){for(int n;(n=input.read(buffer))!=-1;){check.run();bytes=Math.addExact(bytes,n);if(bytes>maximum)throw new IOException("Mash evidence grew beyond its byte capacity");hash.update(buffer,0,n);}}return new Pin(bytes,HexFormat.of().formatHex(hash.digest()));}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    static Inventory inventory(Path root,MashLimits limits,Runnable check)throws IOException {
        canonical(root);var files=new TreeMap<String,Pin>();var directories=new TreeSet<String>();long[] total={0};int[] entries={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            private void admit(Path path)throws IOException {check.run();canonical(path);if(++entries[0]>limits.entries()||entries[0]*1024L>Runtime.getRuntime().maxMemory()/4)throw new IOException("Complete mash evidence census exceeds entry/storage capacity; no prefix admitted");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {admit(path);directories.add(relative(root,path));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {admit(path);if(!attrs.isRegularFile())throw new IOException("Linked/nonregular mash bundle member");total[0]=Math.addExact(total[0],attrs.size());if(total[0]>limits.bundleBytes())throw new IOException("Complete mash bundle exceeds its declared byte capacity");String name=relative(root,path);if(name.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid mash evidence member path");files.put(name,pin(path,limits.bundleBytes(),check));return FileVisitResult.CONTINUE;}
        });return new Inventory(files,directories);
    }
    static String text(Path path,int maximum)throws IOException {pin(path,maximum,()->{});byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(maximum+1);}if(bytes.length>maximum)throw new IOException("Mash text input grew beyond capacity");return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    static Object json(Path path,int maximum)throws IOException {return SimpleJson.parse(text(path,maximum));}
    static void write(Path path,Object value)throws IOException {Files.writeString(path,MashJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static Set<String> directories(Set<String> files){var result=new TreeSet<String>();result.add("");for(String name:files){int end=name.lastIndexOf('/');while(end>=0){result.add(name.substring(0,end));end=name.lastIndexOf('/',end-1);}}return Set.copyOf(result);}
    static void removeSuccessfulScratch(Path scratch)throws IOException {
        canonical(scratch);if(!scratch.getFileName().toString().startsWith("omicsmatic-mash-verify-"))throw new IOException("Refuse cleanup outside owned successful mash replay scratch");
        Files.walkFileTree(scratch,new SimpleFileVisitor<>(){
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {Files.delete(path);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path path,IOException failure)throws IOException {if(failure!=null)throw failure;Files.delete(path);return FileVisitResult.CONTINUE;}
        });
    }
}
