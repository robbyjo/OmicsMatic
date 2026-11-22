/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Captured regular files, physical destinations and closed inventories. */
final class FusionWorkflowFiles {
    private FusionWorkflowFiles() { }
    static byte[] bytes(Path file,long maximum)throws IOException {
        if(!Files.exists(file))throw new FusionWorkflowContracts.Unavailable("Missing exact permitted prerequisite: "+file);
        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<=maximum,"Require regular bounded file: "+file);
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){
            byte[] raw=in.readNBytes(Math.toIntExact(Math.min(maximum+1,Integer.MAX_VALUE)));require(raw.length<=maximum,"File grew beyond capacity: "+file);return raw;
        }
    }
    static Map<String,Object> read(Path file)throws IOException{return object(GprofilerJson.read(bytes(file,67108864)));}
    static byte[] json(Object value)throws IOException{byte[] body=GprofilerJson.write(value,67108864),out=Arrays.copyOf(body,body.length+1);out[body.length]='\n';return out;}
    static boolean same(Object a,Object b)throws IOException{return Objects.equals(GprofilerJson.read(json(a)),GprofilerJson.read(json(b)));}
    static void write(Path file,Object value)throws IOException {Files.write(file,json(value),StandardOpenOption.CREATE_NEW);}
    static void text(Path file,String value)throws IOException{Files.writeString(file,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static String sha(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static Map<String,Object> pin(byte[] raw){return map("bytes",(long)raw.length,"sha256",sha(raw));}
    static Map<String,Object> pin(Path file)throws IOException {
        require(Files.isRegularFile(file),"Missing/nonregular captured/runtime file: "+file);
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
        long total=0;try(var in=Files.newInputStream(file)){byte[] block=new byte[65536];for(int n;(n=in.read(block))>=0;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("FUSION capture interrupted");digest.update(block,0,n);total+=n;}}
        require(total==Files.size(file),"File changed while hashing: "+file);return map("bytes",total,"sha256",HexFormat.of().formatHex(digest.digest()));
    }
    static Path newOutput(Path target)throws IOException {
        target=target.toAbsolutePath().normalize();require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output must be NEW; existing evidence is preserved");
        Path parent=target.getParent();require(parent!=null&&Files.isDirectory(parent),"Output parent must already exist");
        require(parent.toRealPath().equals(parent),"Use a physical output parent, without junction/symlink aliases");Files.createDirectory(target);return target;
    }
    static String relative(String value)throws IOException {
        require(!value.isBlank()&&!value.startsWith("/")&&!value.contains("\\")&&!value.contains(":")&&value.codePoints().noneMatch(c->c<32||c==127),"Require a portable relative captured-file path");
        for(String part:value.split("/",-1))require(!part.isEmpty()&&!part.equals(".")&&!part.equals(".."),"Captured-file path contains traversal/empty component");return value;
    }
    static void capture(Path source,Path destination,Map<String,Object> request,Map<String,Object> captures,String role)throws IOException {
        if(!Files.exists(source))throw new FusionWorkflowContracts.Unavailable("Missing exact permitted "+role+" input: "+source);
        Path physical=source.toRealPath();require(Files.isRegularFile(physical),"Require regular "+role+" input: "+source);
        long size=Files.size(physical);require(size<=FusionWorkflowContracts.cap(request,"maximum_member_bytes"),"Input member capacity exceeded: "+role);
        var identity=pin(physical);
        String name=role;
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)){require(pin(destination).equals(identity),"Two input bindings collide at "+destination);return;}
        long total=size;for(Object value:captures.values())total=Math.addExact(total,number(object(value),"bytes"));
        require(total<=FusionWorkflowContracts.cap(request,"maximum_input_bytes"),"Combined input capacity exceeded");
        Files.createDirectories(destination.getParent());
        try(var in=Files.newInputStream(physical);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){
            byte[] block=new byte[65536];long copied=0;for(int n;(n=in.read(block))>=0;){copied+=n;require(copied<=size,"Input grew during capture: "+source);out.write(block,0,n);}
        }
        require(pin(destination).equals(identity)&&pin(physical).equals(identity),"Source input changed during capture: "+source);
        captures.put(name,identity);
    }
    static Map<String,Object> inventory(Path root,Map<String,Object> request)throws IOException {
        var files=new TreeMap<String,Object>();var directories=new TreeSet<String>();long total=0;
        try(var stream=Files.walk(root)){
            for(Path file:stream.toList()){
                require(!Files.isSymbolicLink(file)&&file.toRealPath().equals(file.toAbsolutePath().normalize()),"Aliased node in captured output: "+file);
                String name=root.relativize(file).toString().replace('\\','/');if(name.isEmpty())continue;
                if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS)){directories.add(name);continue;}
                require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Nonregular capture node");
                if(Set.of("manifest.json","COMPLETED.json").contains(name))continue;
                var identity=pin(file);require(number(identity,"bytes")<=FusionWorkflowContracts.cap(request,"maximum_member_bytes"),"Capture member capacity exceeded");total=Math.addExact(total,number(identity,"bytes"));
                require(total<=FusionWorkflowContracts.cap(request,"maximum_output_bytes")&&files.size()<FusionWorkflowContracts.cap(request,"maximum_files"),"Capture byte/file capacity exceeded");files.put(name,identity);
            }
        }
        return map("files",files,"directories",new ArrayList<>(directories));
    }
    static String hex(String value){return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));}
    static String literal(Object value){return value instanceof GprofilerJson.Numeric n?n.token():value.toString();}
    static Map<String,Object> runtimeFiles(List<Path> roots)throws IOException {
        var paths=new ArrayDeque<>(roots);var visited=new HashSet<Path>();var aliases=new TreeMap<String,Object>();var files=new TreeMap<String,Object>();long total=0;
        while(!paths.isEmpty()){
            Path logical=paths.removeFirst().toAbsolutePath().normalize(),real=logical.toRealPath();aliases.put(logical.toString(),real.toString());
            require(aliases.size()<=100000,"Runtime address capacity exceeded");
            if(!visited.add(real))continue;
            if(Files.isDirectory(real)){
                try(var children=Files.list(real)){for(Path child:children.sorted().toList())if(!Set.of("help","html","doc","demo","tests","unitTests").contains(child.getFileName().toString()))paths.addLast(child);}
            }else{
                require(Files.isRegularFile(real),"Nonregular declared runtime member: "+logical);long size=Files.size(real);total=Math.addExact(total,size);
                require(total<=4294967296L&&files.size()<50000,"Runtime file/byte fingerprint capacity exceeded");files.put(real.toString(),pin(real));
            }
        }
        return map("files",files,"addresses",aliases);
    }
    static String unhex(String value)throws IOException{try{byte[] raw=HexFormat.of().parseHex(value);String text=new String(raw,StandardCharsets.UTF_8);require(Arrays.equals(text.getBytes(StandardCharsets.UTF_8),raw),"Invalid UTF-8 control value");return text;}catch(IllegalArgumentException e){throw new IOException("Invalid hex control value",e);}}
}
