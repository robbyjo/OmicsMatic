/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.MtagJson.*;

/** Complete private original-runtime admission, distinct from analysis or producer authentication. */
final class MtagRuntime {
    private MtagRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }
    record Runtime(Path profile,Path python,Path environment,Path source,Map<?,?> metadata,Map<?,?> closure,Map<?,?> lock) { }
    static String digest(byte[] bytes) {return HexFormat.of().formatHex(hash().digest(bytes));}
    private static MessageDigest hash() {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static Map<String,Object> pin(Path file,long maximum)throws IOException {
        need(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Complete regular MTAG file required: "+file);
        BasicFileAttributes before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        need(before.size()<=maximum,"MTAG file exceeds declared byte capacity: "+file);MessageDigest h=hash();long n=0;
        try(InputStream input=Files.newInputStream(file)){byte[] b=new byte[1048576];for(int count;(count=input.read(b))!=-1;){n=Math.addExact(n,count);need(n<=maximum,"MTAG file grew beyond byte capacity: "+file);h.update(b,0,count);}}
        BasicFileAttributes after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        need(n==before.size()&&n==after.size()&&before.lastModifiedTime().equals(after.lastModifiedTime())&&java.util.Objects.equals(before.fileKey(),after.fileKey()),"MTAG file changed during complete hashing: "+file);
        return map("bytes",n,"sha256",HexFormat.of().formatHex(h.digest()));
    }
    static String member(Object value)throws IOException {
        String s=text(value);need(!s.isEmpty()&&!s.startsWith("/")&&!s.contains("\\")&&!s.contains(":"),"Invalid relative MTAG inventory member");
        for(String part:s.split("/",-1))need(!part.isEmpty()&&!part.equals(".")&&!part.equals("..")&&part.chars().noneMatch(c->c<32||c==127),"Unsafe MTAG inventory path");return s;
    }
    static Path canonical(Path path,boolean directory)throws IOException {
        need(path.isAbsolute()&&path.normalize().equals(path)&&!Files.isSymbolicLink(path)&&path.toRealPath().equals(path),"Require canonical unlinked MTAG path: "+path);
        need(directory?Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS):Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"MTAG path kind differs: "+path);return path;
    }
    static byte[] resource(String name)throws IOException {
        try(InputStream in=MtagRuntime.class.getResourceAsStream("/org/jlinalg/omics/mtag/"+name)){
            need(in!=null,"Packaged MTAG resource is missing: "+name);return in.readAllBytes();
        }
    }
    static Map<String,Object> closure(Path root,int maximumFiles,long maximumBytes)throws IOException {
        canonical(root,true);var files=new TreeMap<String,Object>();long[] total={0};int[] entries={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a)throws IOException {
                need(++entries[0]<=maximumFiles&&!a.isSymbolicLink(),"MTAG runtime directory/link capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes a)throws IOException {
                need(++entries[0]<=maximumFiles,"MTAG runtime file capacity exceeded");String name=member(root.relativize(file).toString());
                if(a.isSymbolicLink()){
                    Path target=Files.readSymbolicLink(file);need(file.toRealPath().startsWith(root),"MTAG runtime link escapes its declared root: "+name);
                    files.put(name,map("kind","symlink","target",target.toString()));
                } else {
                    need(a.isRegularFile(),"Nonregular MTAG runtime file: "+name);Map<String,Object> p=pin(file,maximumBytes);long size=(Long)p.get("bytes");
                    total[0]=Math.addExact(total[0],size);need(total[0]<=maximumBytes,"Complete MTAG runtime byte capacity exceeded");
                    long mode=((Number)Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS)).longValue()&0777;
                    files.put(name,map("kind","file","mode",mode,"bytes",size,"sha256",p.get("sha256")));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return map("files",files,"regular_bytes",total[0]);
    }
    static Runtime read(Path path)throws IOException {
        if(!System.getProperty("os.name").equalsIgnoreCase("Linux")||!List.of("amd64","x86_64").contains(System.getProperty("os.arch")))
            throw new Unavailable("Original MTAG execution requires Linux x86_64 Java; use an existing permitted Linux/WSL runtime. Saved bundle verification works separately on Windows.");
        if(!Files.isRegularFile(path))throw new Unavailable("The admitted MTAG runtime profile is missing; export the setup helper and prepare a permitted exact private runtime");
        canonical(path,false);Map<?,?> p=object(MtagJson.read(path,67108864));
        keys(p,"schema_version","tool","version","source_commit","platform","python","environment","source","versions","closure","source_lock","preparer","setup_only","system_loader_and_libraries_independently_pinned","hashes_are_authenticity");
        need(integer(p.get("schema_version"),1,1)==1&&text(p.get("tool")).equals("MTAG"),"Invalid MTAG runtime profile");
        byte[] lockRaw=resource("mtag-workflow-lock.json");Map<?,?> lock=object(SimpleJson.parse(new String(lockRaw,java.nio.charset.StandardCharsets.UTF_8)));
        if(!text(p.get("version")).equals("1.0.8")||!text(p.get("source_commit")).equals(text(lock.get("source_commit")))||!text(p.get("platform")).equals("linux-x86_64"))
            throw new Unavailable("The exact tested MTAG1.0.8/Linux source profile is unavailable; no version or platform substitution");
        need(object(p.get("source_lock")).equals(map("bytes",(long)lockRaw.length,"sha256",digest(lockRaw))),"MTAG source/dependency lock differs from the packaged lock");
        byte[] helper=resource("prepare_mtag_runtime.py");need(object(p.get("preparer")).equals(map("bytes",(long)helper.length,"sha256",digest(helper))),"Runtime profile was not prepared with this exact helper; preserve it and prepare NEW paths");
        need(bool(p.get("setup_only"))&&!bool(p.get("system_loader_and_libraries_independently_pinned"))&&!bool(p.get("hashes_are_authenticity")),"Unsupported runtime admission/attestation claims");
        Path environment=Path.of(text(p.get("environment"))),source=Path.of(text(p.get("source"))),python=Path.of(text(p.get("python")));
        if(!Files.isDirectory(environment)||!Files.isDirectory(source)||!Files.isExecutable(python))throw new Unavailable("The recorded private MTAG environment/source/executable is unavailable; skip dependent analysis and recreate it where permitted");
        canonical(environment,true);canonical(source,true);need(!source.startsWith(environment)&&!environment.startsWith(source),"MTAG runtime roots must be separate");
        need(python.equals(environment.resolve("bin/python"))&&python.toRealPath().startsWith(environment),"MTAG executable escapes its explicit private environment");
        Map<?,?> versions=object(p.get("versions"));need(text(versions.get("python")).equals(text(lock.get("python_version")))&&text(versions.get("mtag")).equals("1.0.8")&&object(versions.get("versions")).equals(object(lock.get("versions"))),"MTAG dependency-version evidence differs");
        Map<?,?> expected=object(p.get("closure"));keys(expected,"environment","source");
        Map<?,?> sourceFiles=object(object(expected.get("source")).get("files")),lockedSource=object(object(lock.get("source")).get("files"));
        need(sourceFiles.keySet().equals(lockedSource.keySet()),"Original MTAG source inventory differs; no extra bytecode or source aliases admitted");
        for(var entry:sourceFiles.entrySet()){
            member(entry.getKey());Map<?,?> f=object(entry.getValue()),wanted=object(lockedSource.get(entry.getKey()));
            need(text(f.get("kind")).equals("file")&&object(map("bytes",f.get("bytes"),"sha256",f.get("sha256"))).equals(wanted),"Original MTAG file bytes differ from source lock: "+entry.getKey());
        }
        Runtime runtime=new Runtime(path,python,environment,source,p,expected,lock);verify(runtime);return runtime;
    }
    static void verify(Runtime runtime)throws IOException {
        Map<String,Object> current=map("environment",closure(runtime.environment(),100000,4_000_000_000L),"source",closure(runtime.source(),100000,4_000_000_000L));
        need(current.equals(runtime.closure()),"Complete MTAG runtime/source closure changed; no result admission or silent profile refresh");
    }
}
