/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Closed complete evidence namespaces and byte-pinned bundled resources. */
final class MemeRuntimeFiles {
    private MemeRuntimeFiles() { }
    static final String LOCK_SHA="192db1b8a88fcdaabbd364d3935225506878e1c0827eeb510d94c4ca15c2f3d0";
    static final String RESOURCE="/org/jlinalg/omics/meme-runtime-setup/";
    record Limits(long fileBytes,long totalBytes,int members) {
        Limits {if(fileBytes<1||fileBytes>1099511627776L||totalBytes<1||totalBytes>1099511627776L||members<1||members>1000000)throw new IllegalArgumentException("Invalid complete MEME evidence capacities");}
    }
    static Map<String,Object> tree(Path root,Limits limits,Runnable check)throws IOException {
        NextflowFiles.directory(root);var result=new TreeMap<String,Object>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes a)throws IOException {
                check.run();require(++count[0]<=limits.members()&&!a.isSymbolicLink(),"Complete MEME directory/link capacity failure");String name=path.equals(root)?".":member(root,path);result.put(name,Map.of("kind","directory","mode",NextflowFiles.mode(path)));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes a)throws IOException {
                check.run();require(++count[0]<=limits.members()&&a.isRegularFile()&&!a.isSymbolicLink(),"Unsupported or excessive MEME evidence namespace member");var identity=NextflowFiles.identity(path,limits.fileBytes(),check);bytes[0]=Math.addExact(bytes[0],identity.pin().bytes());require(bytes[0]<=limits.totalBytes(),"Complete MEME evidence bytes exceed capacity");
                result.put(member(root,path),Map.of("kind","file","bytes",identity.pin().bytes(),"sha256",identity.pin().sha256(),"mode",identity.mode()));return FileVisitResult.CONTINUE;
            }
        });return Map.copyOf(result);
    }
    static String member(Path root,Path path)throws IOException {return NextflowFiles.member(root.relativize(path).toString().replace(path.getFileSystem().getSeparator(),"/"));}
    static Map<?,?> lock()throws IOException {return NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(resource("/org/jlinalg/omics/meme-runtime-lock.json",new NextflowFiles.Pin(88598,LOCK_SHA)))));}
    static byte[] resource(String absoluteName,NextflowFiles.Pin pin)throws IOException {
        require(pin.bytes()<=2097152,"MEME resource exceeds bounded metadata capacity");byte[] raw;try(var in=MemeRuntimeFiles.class.getResourceAsStream(absoluteName)){if(in==null)throw new IOException("Missing pinned MEME resource: "+absoluteName);raw=in.readNBytes(Math.toIntExact(pin.bytes()+1));}
        require(raw.length==pin.bytes()&&NextflowFiles.sha(raw).equals(pin.sha256()),"Pinned MEME resource differs: "+absoluteName);return raw;
    }
    static Map<String,Object> pin(NextflowFiles.Pin value){return Map.of("bytes",value.bytes(),"sha256",value.sha256());}
    static void write(Path path,Object value)throws IOException {Files.writeString(path,RsemJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static Map<String,Object> object(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
