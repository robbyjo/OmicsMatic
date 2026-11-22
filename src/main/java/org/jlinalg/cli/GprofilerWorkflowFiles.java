/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.gprofiler.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Bounded immutable captures and complete inventories, separate from other saved producers. */
final class GprofilerWorkflowFiles {
    private GprofilerWorkflowFiles() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    static byte[] bytes(Path path,long maximum)throws IOException{
        try{
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(path.toString());
            require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require unlinked regular file: "+path);
            require(Files.size(path)<=maximum,"File capacity exceeded: "+path);
            try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                byte[] raw=in.readNBytes(Math.toIntExact(Math.min(maximum+1,Integer.MAX_VALUE)));
                require(raw.length<=maximum,"File grew beyond byte capacity: "+path);return raw;
            }
        }catch(NoSuchFileException|AccessDeniedException e){throw new Unavailable("Restore the named exact permitted prerequisite: "+path,e);}
    }
    static Map<String,Object> read(Path path,long maximum)throws IOException{return object(GprofilerJson.read(bytes(path,maximum)));}
    static byte[] json(Object value)throws IOException{
        byte[] body=GprofilerJson.write(value,67108864),raw=Arrays.copyOf(body,body.length+1);raw[body.length]='\n';return raw;
    }
    static boolean same(Object a,Object b)throws IOException{return Objects.equals(GprofilerJson.read(json(a)),GprofilerJson.read(json(b)));}
    static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static String sha(byte[] raw){return HexFormat.of().formatHex(digest().digest(raw));}
    static Map<String,Object> pin(byte[] raw){return map("bytes",(long)raw.length,"sha256",sha(raw));}
    static Map<String,Object> pin(Path path)throws IOException{
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular recorded file: "+path);
        var hash=digest();long total=0;
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
            byte[] block=new byte[65536];for(int n;(n=in.read(block))>=0;){checkInterrupted();hash.update(block,0,n);total+=n;}
        }
        require(total==Files.size(path),"File changed during hashing: "+path);
        return map("bytes",total,"sha256",HexFormat.of().formatHex(hash.digest()));
    }
    record Tree(Map<String,Object> files,List<String> directories,long bytes) { }
    static Tree inventory(Path root)throws IOException{
        require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"Require regular capture directory");
        var files=new TreeMap<String,Object>();var directories=new TreeSet<String>();long total=0;int entries=0;
        try(var walk=Files.walk(root)){
            var it=walk.iterator();while(it.hasNext()){
                Path p=it.next();require(++entries<=128,"Bundle entry capacity exceeded");
                require(!Files.isSymbolicLink(p),"Bundle links are refused");String name=root.relativize(p).toString().replace('\\','/');if(name.isEmpty())continue;
                if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)){directories.add(name);continue;}
                require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Nonregular bundle entry: "+name);
                if(!Set.of("manifest.json","COMPLETED.json","COMPLETED.pending").contains(name)){
                    var identity=pin(p);files.put(name,identity);total=Math.addExact(total,number(identity,"bytes"));
                }
            }
        }
        return new Tree(files,List.copyOf(directories),total);
    }
    static final class Budget {
        private final long maximum;
        private long total;
        Budget(long maximum,long initial)throws IOException{this.maximum=maximum;reserve(initial);}
        long remaining(){return maximum-total;}
        void reserve(long bytes)throws IOException{require(bytes>=0&&bytes<=maximum-total,"Combined output capacity exceeded; partial outputs retained");total+=bytes;}
    }
    static final class Outputs {
        private final Path root;
        private final Budget budget;
        private final Map<String,Object> pins=new TreeMap<>();
        Outputs(Path root,Budget budget){this.root=root;this.budget=budget;}
        Map<String,Object> pins(){return new TreeMap<>(pins);}
        void json(String name,Object object)throws IOException{bytes(name,GprofilerWorkflowFiles.json(object));}
        void text(String name,String text)throws IOException{bytes(name,text.getBytes(StandardCharsets.UTF_8));}
        void bytes(String name,byte[] raw)throws IOException{
            require(name.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")&&!name.contains("..")&&!pins.containsKey(name),"Invalid/duplicate output name");
            int n=(int)Math.min(raw.length,budget.remaining());budget.reserve(n);
            if(root!=null){Path p=root.resolve(name);Files.createDirectories(p.getParent());
                try(var out=Files.newOutputStream(p,StandardOpenOption.CREATE_NEW)){out.write(raw,0,n);}}
            require(n==raw.length,"Combined output capacity exceeded; captured prefix retained");pins.put(name,pin(raw));
        }
        void jsonLines(String name,List<?> rows)throws IOException{
            require(!pins.containsKey(name),"Duplicate output name");var hash=digest();long count=0;
            OutputStream stream=root==null?OutputStream.nullOutputStream():Files.newOutputStream(root.resolve(name),StandardOpenOption.CREATE_NEW);
            try(var out=stream){for(Object row:rows){checkInterrupted();byte[] raw=GprofilerWorkflowFiles.json(row);
                int n=(int)Math.min(raw.length,budget.remaining());budget.reserve(n);out.write(raw,0,n);hash.update(raw,0,n);count+=n;
                require(n==raw.length,"Combined output capacity exceeded; JSONL prefix retained");}}
            pins.put(name,map("bytes",count,"sha256",HexFormat.of().formatHex(hash.digest())));
        }
    }
    static Map<String,Object> producer()throws IOException{
        var classes=new TreeMap<String,Object>();
        for(Class<?> type:List.of(GprofilerGostClient.class,GprofilerJson.class,GprofilerWorkflowContracts.class,
            GprofilerWorkflowFiles.class,GprofilerWorkflowWire.class,GprofilerWorkflowModel.class,GprofilerWorkflowCli.class))classPins(type,classes);
        try{classPins(Class.forName("org.jlinalg.gprofiler.GprofilerHttpTransport"),classes);}catch(ClassNotFoundException e){throw new Unavailable("Restore original g:Profiler transport class",e);}
        return map("selected_class_sha256",classes,"java_version",System.getProperty("java.version"),"os_name",System.getProperty("os.name"),"authentication",false);
    }
    private static void classPins(Class<?> type,Map<String,Object> pins)throws IOException{
        String name=type.getName();try(var in=type.getResourceAsStream("/"+name.replace('.','/')+".class")){
            if(in==null)throw new Unavailable("Restore exact producer class "+name,null);pins.put(name,sha(in.readAllBytes()));}
        for(Class<?> child:type.getDeclaredClasses())classPins(child,pins);
    }
    static Path newOutput(Path target)throws IOException{
        require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output must be NEW; preserve existing output: "+target);
        target=physicalNewPath(target);
        require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output aliases an existing destination");
        require(target.getParent()!=null,"Output must have a parent directory");Files.createDirectories(target.getParent());Files.createDirectory(target);return target;
    }
    static Path physicalNewPath(Path path)throws IOException{
        Path at=path;var suffix=new ArrayList<Path>();
        while(!Files.exists(at,LinkOption.NOFOLLOW_LINKS)){require(at.getParent()!=null,"No existing output ancestor");suffix.add(at.getFileName());at=at.getParent();}
        Path real=at.toRealPath();for(int i=suffix.size()-1;i>=0;i--)real=real.resolve(suffix.get(i));return real.normalize();
    }
    static void checkInterrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("g:Profiler operation interrupted; partials retained");}
}
