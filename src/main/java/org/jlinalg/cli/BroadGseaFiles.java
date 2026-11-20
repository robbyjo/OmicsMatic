/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;
import org.jlinalg.enrichment.BroadGseaEngine;
import org.jlinalg.enrichment.BroadGseaStatistics;
import static org.jlinalg.cli.BroadGseaRecipe.*;

/** Captured bytes, physical-parent protection and complete native producer identities. */
final class BroadGseaFiles {
    private BroadGseaFiles() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static final Set<String> CLOSURE=Set.of("analysis-status.json","capture-files.json");
    static final List<String> OWNED=List.of("org/jlinalg/cli/BroadGseaRecipe","org/jlinalg/cli/BroadGseaFiles",
        "org/jlinalg/cli/BroadGseaWorkflowCli","org/jlinalg/cli/BroadGseaRuntime","org/jlinalg/cli/BroadGseaWorker",
        "org/jlinalg/cli/BroadGseaOutputs","org/jlinalg/enrichment/BroadGseaEngine","org/jlinalg/enrichment/BroadGseaStatistics",
        "org/jlinalg/cli/NextflowParameters","org/jlinalg/cli/NextflowJson");
    static byte[] jsonBytes(Object value)throws IOException{return (NextflowJson.json(value)+"\n").getBytes(StandardCharsets.UTF_8);}
    static void json(Path path,Object value)throws IOException{Files.write(path,jsonBytes(value),StandardOpenOption.CREATE_NEW);}
    static Object parse(byte[] raw)throws IOException{
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        return NextflowParameters.parse(text,Math.max(1,raw.length),Math.max(1,Math.min(20_000_000,raw.length)));
    }
    static Map<String,Object> read(Path path,long cap)throws IOException{return object(parse(bytes(path,cap)),path.toString());}
    static byte[] captured(Path root,String name,long cap)throws IOException{
        try{return bytes(root.resolve(name),cap);}catch(Unavailable error){throw new IOException("Captured member missing/inaccessible: "+name+"; capture integrity is incomplete. "+error.getMessage());}
    }
    static Map<String,Object> capturedObject(Path root,String name,long cap)throws IOException{return object(parse(captured(root,name,cap)),name);}
    static byte[] bytes(Path path,long cap)throws IOException{
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Missing exact input: "+path);
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Expected a nonlinked regular file: "+path);
        try(InputStream in=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);var out=new ByteArrayOutputStream()){
            byte[] block=new byte[65536];long count=0;int n;
            while((n=in.read(block))!=-1){if(n>cap-count)throw new IOException("Input/metadata capacity exceeded: "+path);out.write(block,0,n);count+=n;}return out.toByteArray();
        }catch(AccessDeniedException error){throw new Unavailable("Exact input is inaccessible: "+path,error);}
    }
    static Map<String,Object> pin(byte[] bytes){return Map.of("bytes",bytes.length,"sha256",sha(bytes));}
    static Map<String,Object> pin(Path path)throws IOException{
        MessageDigest digest=digest();long count=0;
        try(InputStream in=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1){digest.update(block,0,n);count+=n;}
        }return Map.of("bytes",count,"sha256",HexFormat.of().formatHex(digest.digest()));
    }
    static String sha(byte[] bytes){return HexFormat.of().formatHex(digest().digest(bytes));}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException error){throw new AssertionError(error);}}
    static boolean same(Object a,Object b)throws IOException{return NextflowJson.json(a).equals(NextflowJson.json(b));}
    static Path resolve(Path base,String name)throws IOException{
        try{return base.resolve(name).toAbsolutePath().normalize();}catch(InvalidPathException error){throw new IOException("Invalid declared path",error);}
    }
    static Path physical(Path path)throws IOException{
        Path current=path.toAbsolutePath().normalize();var missing=new ArrayDeque<Path>();
        while(!Files.exists(current,LinkOption.NOFOLLOW_LINKS)){
            if(current.getParent()==null)throw new IOException("Cannot resolve output parent "+path);
            missing.addFirst(current.getFileName());current=current.getParent();
        }
        Path real=current.toRealPath();for(Path part:missing)real=real.resolve(part);return real.normalize();
    }
    static void outside(Path target,Path protectedRoot)throws IOException{
        Path t=physical(target),p=physical(protectedRoot);
        if(t.startsWith(p)||p.startsWith(t))throw new IOException("Output aliases, contains or is inside the protected parent capture: "+protectedRoot);
    }
    static Path fresh(Path target)throws IOException{
        target=target.toAbsolutePath().normalize();if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("Preserve existing output; choose a NEW directory: "+target);
        Path actual=physical(target);
        for(Path p=actual.getParent();p!=null;p=p.getParent())if(Files.exists(p.resolve("capture-kind.json"),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Output is nested inside an existing captured analysis; choose a NEW sibling directory: "+p);
        Files.createDirectories(actual.getParent());Files.createDirectory(actual);
        if(!actual.toRealPath().equals(actual))throw new IOException("Output parent changed during creation");return actual;
    }
    static Map<String,Long> footprint(Path root,int cap)throws IOException{
        Path real=root.toRealPath();var result=new TreeMap<String,Long>();int[] directories={0};
        Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
            private void check(Path path)throws IOException{
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Native GSEA inventory interrupted");
                if(Files.isSymbolicLink(path)||!path.toRealPath().equals(real.resolve(root.relativize(path))))throw new IOException("Linked/aliased capture member: "+path);
            }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes a)throws IOException{
                check(path);if(++directories[0]>4096)throw new IOException("Capture directory capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes a)throws IOException{
                check(path);if(!a.isRegularFile()||result.size()>=cap)throw new IOException("Capture file type/count capacity exceeded");
                try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){result.put(root.relativize(path).toString().replace('\\','/'),channel.size());}
                return FileVisitResult.CONTINUE;
            }
        });return result;
    }
    static Map<String,Object> inventory(Path root,int cap)throws IOException{
        var result=new TreeMap<String,Object>();for(String name:footprint(root,cap).keySet())if(!CLOSURE.contains(name))result.put(name,pin(root.resolve(name)));return result;
    }
    static Map<String,Object> scientific(Map<String,Object> inventory){
        var result=new TreeMap<String,Object>();for(var e:inventory.entrySet())if(e.getKey().startsWith("results/"))result.put(e.getKey(),e.getValue());return result;
    }
    static void finish(Path root,int code,String mode,String detail,Caps caps,Map<String,Object> process)throws IOException{
        String status=code==0?"PASS_"+mode:code==3?"SKIP3":"FAIL2";
        Files.writeString(root.resolve("WARNINGS.md"),"# "+(code==0?status:"NO COMPLETE RESULT: "+status)+"\n\n"+detail+"\n\n"+SCOPE+"\n\n"+ACTION+"\n\nHeap, wall time and observed output limits are enforced for the owned Java worker. These are not OS RSS/CPU containment. Preserve all partials. SHA-256 is integrity, not a signature.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        json(root.resolve("process.json"),process);
        // Closure records are bounded separately so a capacity failure can still be explained.
        var inventory=inventory(root,10000);byte[] seal=jsonBytes(inventory);
        if(seal.length>RECIPE_BYTES)throw new IOException("Capture seal metadata capacity exceeded");
        Files.write(root.resolve("capture-files.json"),seal,StandardOpenOption.CREATE_NEW);
        json(root.resolve("analysis-status.json"),Map.of("schema_version",1,"status",status,"complete_result",code==0,"mode",mode,
            "engine",ENGINE,"manifest",pin(seal),"scope",SCOPE,"detail",detail,"required_action",ACTION));
    }
    static Path jar()throws IOException{
        try{
            Path path=Path.of(BroadGseaWorkflowCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(path))throw new Unavailable("Native GSEA requires the normal executable JAR. Build executableJar, then run its BroadGseaWorkflowCli entry point.");
            for(Class<?> type:new Class<?>[]{BroadGseaEngine.class,BroadGseaStatistics.class,NextflowParameters.class,NextflowJson.class,BroadGseaRecipe.class,BroadGseaFiles.class,BroadGseaWorker.class,BroadGseaRuntime.class,BroadGseaOutputs.class})
                if(!Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(path))throw new IOException("Native GSEA class origin differs: "+type.getName());return path;
        }catch(java.net.URISyntaxException error){throw new IOException("Invalid producer location",error);}
    }
    static Map<String,Object> producer()throws IOException{
        Path path=jar();var classes=new TreeMap<String,Object>();
        try(var jar=new JarFile(path.toFile())){
            for(var entry:Collections.list(jar.entries())){
                String n=entry.getName();boolean owned=false;
                for(String prefix:OWNED)if(n.equals(prefix+".class")||n.startsWith(prefix+"$")&&n.endsWith(".class")){owned=true;break;}
                if(owned)try(InputStream in=jar.getInputStream(entry)){classes.put(n,pin(in.readAllBytes()));}
            }
        }
        for(String prefix:OWNED)if(!classes.containsKey(prefix+".class"))throw new IOException("Missing native producer class: "+prefix);
        return Map.of("schema_version",1,"engine",ENGINE,"jar",pin(path),"owned_class_payloads",classes,
            "java_version",System.getProperty("java.version"),"java_vendor",System.getProperty("java.vendor"));
    }
    static void sameProducer(Map<String,Object> old,Map<String,Object> current)throws IOException{
        keys(old,Set.of("schema_version","engine","jar","owned_class_payloads","java_version","java_vendor"),Set.of());
        integer(old,"schema_version",1,1);
        if(!text(old,"engine").equals(ENGINE)||!same(old.get("owned_class_payloads"),current.get("owned_class_payloads")))throw new IOException("Saved owned workflow/engine class payloads differ; use its exact compatible normal producer, not refreshed fingerprints");
    }
    static int failure(Throwable error){
        for(Throwable e=error;e!=null;e=e.getCause())if(e instanceof Unavailable)return 3;return 2;
    }
}
