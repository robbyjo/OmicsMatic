/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.coloc.*;

/** Bounded immutable capture and complete bundle inventory; no overwrite or cleanup. */
final class ColocAbfFiles {
    private ColocAbfFiles() { }
    static final class Unavailable extends IOException {
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    static byte[] bytes(Path path,long cap)throws IOException {
        try {
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(path.toString());
                throw new IOException("Require regular unlinked file: "+path);
            }
            if(Files.size(path)>cap)throw new IOException("File exceeds byte capacity: "+path);
            try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                byte[] raw=in.readNBytes(Math.toIntExact(cap+1));
                if(raw.length>cap)throw new IOException("File grew beyond byte capacity: "+path);return raw;
            }
        }catch(NoSuchFileException|AccessDeniedException e){throw new Unavailable("Missing/unreadable prerequisite: "+path,e);}
    }
    static Map<String,Object> read(Path path)throws IOException {
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes(path,2_000_000))).toString();
        return SeuratDifferentialContracts.object(SimpleJson.parse(text),"COLOC JSON");
    }
    static void json(Path path,Object value)throws IOException {
        Files.writeString(path,NextflowJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static String sha(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static Map<String,Object> pin(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Bundle file missing/nonregular: "+path);
        MessageDigest md;try{md=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        long count=0;
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
            byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("COLOC file audit interrupted");
                md.update(buffer,0,n);count+=n;
            }
        }
        if(count!=Files.size(path))throw new IOException("File changed during hash: "+path);
        return Map.of("bytes",count,"sha256",HexFormat.of().formatHex(md.digest()));
    }
    static long capture(Path source,Path target,long remaining)throws IOException {
        try {
            if(!Files.exists(source,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(source.toString());
            if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require regular unlinked input: "+source);
            if(Files.size(source)>remaining)throw new IOException("Combined input byte capacity exceeded: "+source);
            var before=pin(source);long count=0;
            try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
                byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("COLOC input capture interrupted");
                    if(n>remaining-count)throw new IOException("Input grew beyond byte capacity");out.write(buffer,0,n);count+=n;
                }
            }
            if(!SeuratDifferentialContracts.same(before,pin(source))||!SeuratDifferentialContracts.same(before,pin(target)))throw new IOException("Input changed during complete capture: "+source);
            return count;
        }catch(NoSuchFileException|AccessDeniedException e){throw new Unavailable("Missing/unreadable summary input: "+source,e);}
    }
    static Map<String,Object> inventory(Path root)throws IOException {
        var result=new TreeMap<String,Object>();
        try(var walk=Files.walk(root)){
            var iterator=walk.iterator();int visited=0;
            while(iterator.hasNext()){
                Path path=iterator.next();if(++visited>32)throw new IOException("Unexpected complete bundle entry population");
                if(Files.isSymbolicLink(path))throw new IOException("Bundle symlinks are not supported");
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;
                String name=root.relativize(path).toString().replace('\\','/');
                if(!Set.of("manifest.json","COMPLETED.json").contains(name))result.put(name,pin(path));
                if(result.size()>20)throw new IOException("Unexpected bundle file population");
            }
        }
        return result;
    }
    static Map<String,Object> producer()throws IOException {
        var classes=new TreeMap<String,Object>();
        for(Class<?> type:List.of(ColocAbf.class,ColocAbfInput.class,ColocAbfOptions.class,ColocAbfResult.class,
                ColocAbfContracts.class,ColocAbfFiles.class,ColocAbfTables.class,ColocAbfWorkflowCli.class,
                SimpleJson.class,NextflowJson.class,jdistlib.Normal.class))classPins(type,classes);
        return Map.of("selected_class_sha256",classes,"java_version",System.getProperty("java.version"),
                "os_name",System.getProperty("os.name"),"authentication",false);
    }
    private static void classPins(Class<?> type,Map<String,Object> pins)throws IOException {
        String name=type.getName(),resource="/"+name.replace('.','/')+".class";
        try(var in=type.getResourceAsStream(resource)){
            if(in==null)throw new Unavailable("Restore original producer class: "+name,null);pins.put(name,sha(in.readAllBytes()));
        }
        for(Class<?> child:type.getDeclaredClasses())classPins(child,pins);
    }
}
