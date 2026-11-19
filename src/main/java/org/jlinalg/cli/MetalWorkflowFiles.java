/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Strict capture/integrity primitives; no original METAL implementation is copied. */
final class MetalWorkflowFiles {
    static final String PROFILE="original-metal-20200505-captured-v1";
    record Pin(long bytes,String sha256) { }
    static final class Prerequisite extends IOException {
        private static final long serialVersionUID=1L;
        Prerequisite(String message){super(message);}
    }
    private MetalWorkflowFiles(){ }
    static void require(boolean ok,String message)throws IOException {if(!ok)throw new IOException(message);}
    static final class Lines implements AutoCloseable {
        private final InputStream stream;private final int maximum;
        Lines(Path path,int maximum)throws IOException {regular(path);stream=new BufferedInputStream(Files.newInputStream(path));this.maximum=maximum;}
        String next()throws IOException {
            var bytes=new ByteArrayOutputStream();int value;
            while((value=stream.read())!=-1&&value!='\n'){if(bytes.size()>=maximum)throw new IOException("Line exceeds declared byte capacity; no truncation");bytes.write(value);}
            if(value==-1&&bytes.size()==0)return null;
            byte[] raw=bytes.toByteArray();int length=raw.length;if(length>0&&raw[length-1]=='\r')length--;
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            String line=decoder.decode(ByteBuffer.wrap(raw,0,length)).toString();
            for(int i=0;i<line.length();i++)if(Character.isISOControl(line.charAt(i))&&line.charAt(i)!='\t')throw new IOException("Control character in literal UTF-8 table");
            return line;
        }
        @Override public void close()throws IOException {stream.close();}
    }
    static void regular(Path path)throws IOException {
        require(!Files.isSymbolicLink(path)&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require a regular, non-linked file: "+path);
    }
    static Path input(Path parent,String raw,boolean prerequisite)throws IOException {
        Path path=parent.toAbsolutePath().resolve(raw).normalize();
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
            if(prerequisite)throw new Prerequisite("Missing permitted runtime/notice: "+path+"; supply it or leave dependent analysis skipped");
            throw new IOException("Missing scientific input: "+path);
        }
        regular(path);return path.toRealPath();
    }
    static Path destination(Path target,Collection<Path> inputs)throws IOException {
        Path absolute=target.toAbsolutePath().normalize();require(!Files.exists(absolute,LinkOption.NOFOLLOW_LINKS),"Output exists; retain it and choose NEW output");
        Path existing=absolute.getParent();require(existing!=null,"Output needs a parent");
        while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
        Path resolved=existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        for(Path p:inputs)require(!resolved.startsWith(p)&&!p.startsWith(resolved),"Output overlaps input/runtime: "+p);
        return resolved;
    }
    static Pin pin(Path path)throws IOException {
        regular(path);MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        long bytes=0;try(var in=Files.newInputStream(path)){byte[] buffer=new byte[1<<20];for(int n;(n=in.read(buffer))!=-1;){digest.update(buffer,0,n);bytes=Math.addExact(bytes,n);}}
        return new Pin(bytes,HexFormat.of().formatHex(digest.digest()));
    }
    static void copy(Path source,Path target)throws IOException {
        Pin before=pin(source);Files.copy(source,target);require(before.equals(pin(target))&&before.equals(pin(source)),"Source changed during capture: "+source);
    }
    static Map<String,String> map(Path path)throws IOException {
        var result=new TreeMap<String,String>();require(Files.size(path)<=1<<20,"Key/value metadata exceeds 1 MiB");
        try(var in=new Lines(path,1<<20)){
            require("key\tvalue".equals(in.next()),"Require literal key/value TSV header");
            for(String line;(line=in.next())!=null;){String[] f=line.split("\t",-1);require(f.length==2&&!f[0].isEmpty()&&!f[1].isEmpty()&&result.putIfAbsent(f[0],f[1])==null,"Malformed/duplicate key/value row");}
        }
        return result;
    }
    static void map(Path path,Map<String,String> values)throws IOException {
        var text=new StringBuilder("key\tvalue\n");for(var e:new TreeMap<>(values).entrySet()){
            require(!e.getKey().matches(".*[\t\r\n].*")&&!e.getValue().matches(".*[\t\r\n].*"),"Metadata field contains a delimiter");text.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static Map<String,Pin> inventory(Path root,long maxBytes,long maxFiles,boolean hash)throws IOException {
        var visitor=new InventoryVisitor(root,maxBytes,maxFiles,hash);Files.walkFileTree(root,visitor);return visitor.result;
    }
    private static final class InventoryVisitor extends SimpleFileVisitor<Path> {
        private final Path root;private final long maxBytes,maxFiles;private final boolean hash;
        private final Map<String,Pin> result=new TreeMap<>();private long bytes,files;
        InventoryVisitor(Path root,long maxBytes,long maxFiles,boolean hash){this.root=root;this.maxBytes=maxBytes;this.maxFiles=maxFiles;this.hash=hash;}
        private String admit(Path path,BasicFileAttributes attr)throws IOException {
            require(!attr.isSymbolicLink()&&(attr.isDirectory()||attr.isRegularFile()),"Linked/special bundle entry: "+path);require(++files<=maxFiles,"Workspace entry budget exceeded");
            bytes=Math.addExact(bytes,attr.isRegularFile()?attr.size():0);require(bytes<=maxBytes,"Workspace byte budget exceeded");String name=root.relativize(path).toString().replace('\\','/');require(!name.matches(".*[\t\r\n].*"),"Bundle path contains a delimiter");return name;
        }
        @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attr)throws IOException {String n=admit(path,attr);if(!n.isEmpty())result.put(n+"/",new Pin(0,"directory"));return FileVisitResult.CONTINUE;}
        @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attr)throws IOException {String n=admit(path,attr);result.put(n,hash?pin(path):new Pin(attr.size(),"not-hashed"));return FileVisitResult.CONTINUE;}
    }
    static void manifest(Path root,MetalWorkflowRequest.Config c)throws IOException {
        var files=inventory(root,c.number("max_bytes"),c.number("max_files"),true);require(!files.containsKey("MANIFEST.tsv")&&!files.containsKey("COMPLETED.tsv"),"Closure already exists");
        StringBuilder text=new StringBuilder("path\tbytes\tsha256\n");for(var e:files.entrySet())text.append(e.getKey()).append('\t').append(e.getValue().bytes()).append('\t').append(e.getValue().sha256()).append('\n');
        Files.writeString(root.resolve("MANIFEST.tsv"),text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    static void verifyManifest(Path root,MetalWorkflowRequest.Config c)throws IOException {
        var actual=inventory(root,c.number("max_bytes"),c.number("max_files"),true);actual.remove("MANIFEST.tsv");actual.remove("COMPLETED.tsv");var expected=new TreeMap<String,Pin>();
        try(var in=new Lines(root.resolve("MANIFEST.tsv"),1<<20)){
            require("path\tbytes\tsha256".equals(in.next()),"Unknown manifest header");for(String line;(line=in.next())!=null;){var f=line.split("\t",-1);require(f.length==3,"Malformed manifest row");require(expected.putIfAbsent(f[0],new Pin(Long.parseLong(f[1]),f[2]))==null,"Duplicate manifest entry");}
        }
        require(actual.equals(expected),"Bundle directory membership or hashes differ; no complete result admitted");
        require(pin(root.resolve("MANIFEST.tsv")).sha256().equals(map(root.resolve("COMPLETED.tsv")).get("manifest_sha256")),"Completion does not bind exact manifest");
    }
    static double number(String value,String label)throws IOException {
        require(value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require a literal decimal for "+label);double x=Double.parseDouble(value);require(Double.isFinite(x),"Nonfinite "+label+"; no substitution");return x;
    }
}
