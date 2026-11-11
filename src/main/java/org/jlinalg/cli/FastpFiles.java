/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;
import java.util.List;

/** Complete stable raw captures and bounded metadata for original fastp workflows. */
final class FastpFiles {
    private FastpFiles() { }
    record Pin(long bytes,String sha256) { }
    record Identity(Pin pin,String key,String modified) { }
    record Capture(String role,String originalPath,Identity identity,String snapshot) { }
    static Identity identity(Path path,long maximum,Runnable check)throws IOException {
        var original=IqtreeFiles.identity(path,maximum,check);
        return new Identity(new Pin(original.bytes(),original.sha256()),original.fileKey(),original.modifiedTime());
    }
    static Pin pin(Path path,long maximum,Runnable check)throws IOException { return identity(path,maximum,check).pin(); }
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static byte[] bytes(Path path,int maximum)throws IOException {
        try(var input=Files.newInputStream(path)){byte[] raw=input.readNBytes(maximum+1);if(raw.length>maximum)throw new IOException("fastp complete metadata exceeds byte capacity");return raw;}
    }
    static String utf8(byte[] raw)throws IOException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString(); }
    static Object json(Path path,int maximum)throws IOException { return SimpleJson.parse(utf8(bytes(path,maximum))); }
    static void writeJson(Path path,Object value)throws IOException { Files.writeString(path,FastpJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
    static Capture capture(Path root,Path source,String role,String member,long maximum,Runnable check)throws IOException {
        if(!root.isAbsolute()||!root.normalize().equals(root)||!root.toRealPath().equals(root)||source.startsWith(root))throw new IOException("fastp snapshots require canonical,disjoint original and owned paths");
        member(member);Identity before=identity(source,maximum,check);Path target=root.resolve(member);
        if(!target.getParent().toRealPath().equals(target.getParent()))throw new IOException("fastp snapshot parent is not canonical");
        long count=0;
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] block=new byte[65536];for(int n=in.read(block);n>=0;n=in.read(block)){check.run();count=Math.addExact(count,n);if(count>maximum||count>before.pin().bytes())throw new IOException("fastp input grew during bounded capture");out.write(block,0,n);}
        }
        if(!identity(source,maximum,check).equals(before)||!pin(target,maximum,check).equals(before.pin()))throw new IOException("fastp original/snapshot bytes or identity changed during capture");
        return new Capture(role,source.toString(),before,member);
    }
    static void verifyOriginals(List<Capture> values,long maximum,Runnable check)throws IOException {
        for(Capture value:values)if(!identity(Path.of(value.originalPath()),maximum,check).equals(value.identity()))throw new IOException("fastp original changed before finalization: "+value.role());
    }
    static String member(String name)throws IOException {
        if(name.isEmpty()||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.chars().anyMatch(c->c<32||c==127))throw new IOException("Unsafe fastp bundle member");
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Unsafe fastp bundle member");return name;
    }
}
