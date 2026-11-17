/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Complete stable file identities and typed setup inventories, without tool-name aliases. */
final class SnakemakeOriginalFiles {
    private SnakemakeOriginalFiles() { }
    record Pin(long bytes,String sha256) {
        Pin {if(bytes<0||sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid complete SnakemakeOriginal file pin");}
    }
    record Identity(Pin pin,String fileKey,String modified,int mode,long links) { }
    record Entry(String kind,Pin pin,int mode,String target) { }
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static int mode(Path path)throws IOException {
        return path.getFileSystem().supportedFileAttributeViews().contains("unix")?((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&0777:-1;
    }
    private static long links(Path path)throws IOException {
        return path.getFileSystem().supportedFileAttributeViews().contains("unix")?((Number)Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue():-1;
    }
    static Path directory(Path path)throws IOException {
        if(path==null||!path.isAbsolute()||!path.normalize().equals(path)||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Require a canonical unlinked SnakemakeOriginal directory: "+path);return path;
    }
    static Identity identity(Path path,long maximum,Runnable cancellation)throws IOException {
        cancellation.run();
        if(maximum<0||path==null||!path.isAbsolute()||!path.normalize().equals(path)||!path.toRealPath().equals(path))throw new IOException("Require a canonical regular SnakemakeOriginal file and complete byte capacity: "+path);
        var before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);int originalMode=mode(path);long originalLinks=links(path);
        if(!before.isRegularFile()||before.size()>maximum)throw new IOException("Nonregular or oversized SnakemakeOriginal file: "+path);
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        long count=0;
        try(var input=input(path)){
            byte[] buffer=new byte[65536];for(int n=input.read(buffer);n>=0;n=input.read(buffer)){cancellation.run();count=Math.addExact(count,n);if(count>maximum||count>before.size())throw new IOException("SnakemakeOriginal file grew during complete capture: "+path);digest.update(buffer,0,n);}
        }
        var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||count!=before.size()||count!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey())||originalMode!=mode(path)||originalLinks!=links(path)||!path.toRealPath().equals(path))throw new IOException("SnakemakeOriginal file changed while reading: "+path);
        cancellation.run();return new Identity(new Pin(count,HexFormat.of().formatHex(digest.digest())),String.valueOf(after.fileKey()),after.lastModifiedTime().toString(),originalMode,originalLinks);
    }
    static Pin pin(Path path,long maximum,Runnable cancellation)throws IOException{return identity(path,maximum,cancellation).pin();}
    static byte[] bytes(Path path,int maximum)throws IOException {
        if(maximum<0||maximum==Integer.MAX_VALUE)throw new IOException("Invalid complete SnakemakeOriginal metadata byte capacity");
        var before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()>maximum||!path.toRealPath().equals(path))throw new IOException("Missing, linked or oversized SnakemakeOriginal metadata: "+path);
        byte[] raw;try(var input=input(path)){raw=input.readNBytes(maximum+1);}
        var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(raw.length>maximum||raw.length!=before.size()||raw.length!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("SnakemakeOriginal metadata changed or exceeded capacity: "+path);return raw;
    }
    static void copyBytes(Path source,Path target,long maximum,Runnable check)throws IOException {
        if(maximum<0)throw new IOException("Invalid complete SnakemakeOriginal copy capacity");
        try(var input=input(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            long count=0;byte[] buffer=new byte[65536];for(int n=input.read(buffer);n>=0;n=input.read(buffer)){check.run();count=Math.addExact(count,n);if(count>maximum)throw new IOException("Complete SnakemakeOriginal copy exceeded capacity");output.write(buffer,0,n);}
        }
    }
    static String utf8(byte[] raw)throws IOException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();}
    private static java.io.InputStream input(Path path)throws IOException {
        // The JDK ZIP namespace contains archive bytes, never host-resolved payload
        // links, and explicitly rejects NOFOLLOW_LINKS as an input-stream option.
        // Ordinary filesystems retain the original strict no-follow operation.
        if(path.getFileSystem().provider().getClass().getName().equals("jdk.nio.zipfs.ZipFileSystemProvider"))return Files.newInputStream(path);
        return Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS);
    }
    static Object json(Path path,int maximum)throws IOException{return SnakemakeOriginalParameters.parse(utf8(bytes(path,maximum)),maximum,1000000);}
    static void writeJson(Path path,Object value)throws IOException{Files.writeString(path,SnakemakeOriginalJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static Map<?,?> object(Object value)throws IOException{if(!(value instanceof Map<?,?> map))throw new IOException("Expected SnakemakeOriginal JSON object");return map;}
    static List<?> list(Object value)throws IOException{if(!(value instanceof List<?> list))throw new IOException("Expected SnakemakeOriginal JSON list");return list;}
    static String text(Object value)throws IOException{if(!(value instanceof String text))throw new IOException("Expected SnakemakeOriginal JSON text");return text;}
    static long integer(Object value,long minimum,long maximum)throws IOException {
        long number;
        if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)number=((Number)value).longValue();
        else if(value instanceof SnakemakeOriginalParameters.NumberToken token&&token.text().matches("-?(?:0|[1-9][0-9]*)")){try{number=Long.parseLong(token.text());}catch(NumberFormatException invalid){throw new IOException("SnakemakeOriginal integer capacity exceeded",invalid);}}
        else throw new IOException("Expected exact SnakemakeOriginal integer metadata");
        if(number<minimum||number>maximum)throw new IOException("SnakemakeOriginal integer is outside its declared range");return number;
    }
    static Pin fingerprint(Object value,long maximum)throws IOException {
        Map<?,?> map=object(value);long bytes=integer(map.get("bytes"),0,maximum);String sha=text(map.get("sha256"));if(!sha.matches("[a-f0-9]{64}"))throw new IOException("Invalid SnakemakeOriginal SHA256");return new Pin(bytes,sha);
    }
    static void keys(Map<?,?> map,String... keys)throws IOException{if(!map.keySet().equals(Set.of(keys)))throw new IOException("Unexpected/missing SnakemakeOriginal metadata fields: "+map.keySet());}
    static String member(String name)throws IOException {
        if(name.isEmpty()||name.startsWith("/")||name.indexOf('\\')>=0||name.chars().anyMatch(c->c<32||c==127))throw new IOException("Unsafe SnakemakeOriginal relative member");
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Unsafe SnakemakeOriginal relative member");return name;
    }
    static Map<String,Entry> entries(Object value,int maximumFiles,long maximumFileBytes)throws IOException {
        Map<?,?> rows=object(value);if(rows.size()>maximumFiles)throw new IOException("SnakemakeOriginal inventory capacity exceeded");var result=new TreeMap<String,Entry>();
        for(var entry:rows.entrySet()){
            String name=member(text(entry.getKey()));Map<?,?> row=object(entry.getValue());String kind=text(row.get("kind"));
            if(kind.equals("file")){keys(row,"kind","bytes","sha256","mode");result.put(name,new Entry(kind,fingerprint(row,maximumFileBytes),(int)integer(row.get("mode"),0,0777),null));}
            else if(kind.equals("symlink")){keys(row,"kind","target");result.put(name,new Entry(kind,null,-1,text(row.get("target"))));}
            else throw new IOException("Unsupported SnakemakeOriginal inventory kind: "+kind);
        }
        return Map.copyOf(result);
    }
    static Map<String,Entry> tree(Path root,int maximumFiles,long maximumBytes,long maximumFileBytes,Runnable check)throws IOException {
        directory(root);var result=new TreeMap<String,Entry>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{check.run();if(++count[0]>maximumFiles||attrs.isSymbolicLink())throw new IOException("SnakemakeOriginal directory count/link capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {
                check.run();if(++count[0]>maximumFiles)throw new IOException("SnakemakeOriginal inventory file capacity exceeded");String relative=root.relativize(path).toString();if(path.getFileSystem().getSeparator().equals("\\"))relative=relative.replace('\\','/');String name=member(relative);
                if(attrs.isSymbolicLink()){
                    Path target=Files.readSymbolicLink(path);if(!path.getParent().resolve(target).normalize().startsWith(root)||!path.toRealPath().startsWith(root))throw new IOException("SnakemakeOriginal inventory link escapes its root: "+name);
                    result.put(name,new Entry("symlink",null,-1,target.toString()));
                }else if(attrs.isRegularFile()){
                    Identity identity=identity(path,maximumFileBytes,check);bytes[0]=Math.addExact(bytes[0],identity.pin.bytes);if(bytes[0]>maximumBytes)throw new IOException("Complete SnakemakeOriginal inventory byte capacity exceeded");result.put(name,new Entry("file",identity.pin,identity.mode,null));
                }else throw new IOException("Nonregular SnakemakeOriginal inventory member: "+name);
                return FileVisitResult.CONTINUE;
            }
        });
        return Map.copyOf(result);
    }
    static Map<String,Object> manifest(Map<String,Entry> entries){
        var result=new TreeMap<String,Object>();for(var row:entries.entrySet()){Entry value=row.getValue();result.put(row.getKey(),value.kind.equals("file")?Map.of("kind","file","bytes",value.pin.bytes,"sha256",value.pin.sha256,"mode",value.mode):Map.of("kind","symlink","target",value.target));}return result;
    }
    static List<String> strings(Object value)throws IOException{var result=new ArrayList<String>();for(Object item:list(value))result.add(text(item));return List.copyOf(result);}
}
