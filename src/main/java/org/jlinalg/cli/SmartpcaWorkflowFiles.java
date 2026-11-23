/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** New outputs and exact bounded regular-file captures; no directory traversal. */
final class SmartpcaWorkflowFiles {
    private SmartpcaWorkflowFiles() { }
    static byte[] bytes(Path file,long maximum)throws IOException {
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new SmartpcaWorkflowContracts.Unavailable("Missing exact permitted file: "+file);
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<=maximum,"Require regular bounded file: "+file);
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){
            byte[] raw=in.readNBytes(Math.toIntExact(maximum+1));require(raw.length<=maximum,"Input grew beyond capacity: "+file);return raw;
        }
    }
    static Map<String,Object> read(Path path)throws IOException{return object(GprofilerJson.read(bytes(path,67_108_864)));}
    static byte[] json(Object value)throws IOException{byte[] raw=GprofilerJson.write(value,67_108_864),out=Arrays.copyOf(raw,raw.length+1);out[raw.length]='\n';return out;}
    static void write(Path path,Object value)throws IOException{Files.write(path,json(value),StandardOpenOption.CREATE_NEW);}
    static void text(Path path,String value)throws IOException{Files.writeString(path,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static String sha(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static Map<String,Object> pin(byte[] raw){return map("bytes",(long)raw.length,"sha256",sha(raw));}
    static Map<String,Object> pin(Path path)throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require regular file while hashing: "+path);
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        var before=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);long total=0;
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
            byte[] block=new byte[65536];for(int n;(n=in.read(block))>=0;){digest.update(block,0,n);total+=n;}
        }
        var after=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        require(total==before.size()&&total==after.size()&&Objects.equals(before.fileKey(),after.fileKey())&&before.lastModifiedTime().equals(after.lastModifiedTime()),"File changed while hashing: "+path);
        return map("bytes",total,"sha256",HexFormat.of().formatHex(digest.digest()));
    }
    static Path newOutput(Path path)throws IOException {
        path=path.toAbsolutePath().normalize();require(!Files.exists(path,LinkOption.NOFOLLOW_LINKS),"Choose NEW output; existing evidence is preserved");
        Path parent=path.getParent();require(parent!=null&&Files.isDirectory(parent),"Output parent must already exist");
        require(parent.toRealPath().equals(parent),"Use a physical output parent without aliases/junctions");return Files.createDirectory(path);
    }
    static Map<String,Object> capture(Path logical,Path destination,long maximum)throws IOException {
        if(!Files.exists(logical))throw new SmartpcaWorkflowContracts.Unavailable("Supply exact permitted EIGENSTRAT input: "+logical);
        Path physical=logical.toRealPath();var expected=pin(physical);require(number(expected,"bytes")<=maximum,"Complete input exceeds declared byte capacity");
        try(var in=Files.newInputStream(physical,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){
            byte[] block=new byte[65536];long total=0;
            for(int n;(n=in.read(block))>=0;){total=Math.addExact(total,n);require(total<=maximum,"Input grew beyond complete-input byte capacity");out.write(block,0,n);}
        }
        require(equal(expected,pin(destination))&&equal(expected,pin(physical))&&logical.toRealPath().equals(physical),"Input bytes or logical/physical binding changed during capture");
        return map("logical",SmartpcaWorkflowContracts.address(logical),"physical",SmartpcaWorkflowContracts.address(physical),"identity",expected);
    }
    static Map<String,Object> inventory(Path root)throws IOException {
        var files=new TreeMap<String,Object>();var directories=new TreeSet<String>();long total=0,nodes=0;
        try(var walk=Files.walk(root)){
            var cursor=walk.iterator();while(cursor.hasNext()){
                Path path=cursor.next();require(++nodes<=512,"Capture file/directory capacity exceeded");
                require(!Files.isSymbolicLink(path)&&path.toRealPath().equals(path.toAbsolutePath().normalize()),"Aliased captured node: "+path);
                String name=root.relativize(path).toString().replace('\\','/');if(name.isEmpty())continue;
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){directories.add(name);continue;}
                require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Nonregular capture node");
                if(Set.of("capture-files.json","COMPLETED.json").contains(name))continue;
                var identity=pin(path);require(number(identity,"bytes")<=2_147_483_648L,"Capture member capacity exceeded");
                total=Math.addExact(total,number(identity,"bytes"));require(total<=8_589_934_592L,"Capture byte capacity exceeded");files.put(name,identity);
            }
        }
        return map("files",files,"directories",new ArrayList<>(directories));
    }
    static boolean equal(Object a,Object b)throws IOException{return Objects.equals(GprofilerJson.read(json(a)),GprofilerJson.read(json(b)));}
}
