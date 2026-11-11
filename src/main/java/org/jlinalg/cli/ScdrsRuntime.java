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
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.ScdrsJson.*;

/** Complete private original-runtime admission, distinct from analysis or producer authentication. */
final class ScdrsRuntime {
    private ScdrsRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }
    record Runtime(Path profile,Path python,Path environment,Path source,Map<?,?> metadata,Map<?,?> closure,Map<?,?> lock) { }
    static String digest(byte[] bytes) {return HexFormat.of().formatHex(hash().digest(bytes));}
    private static MessageDigest hash() {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static Map<String,Object> pin(Path file,long maximum)throws IOException {
        need(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Complete regular scDRS file required: "+file);
        BasicFileAttributes before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        need(before.size()<=maximum,"scDRS file exceeds declared byte capacity: "+file);MessageDigest h=hash();long n=0;
        try(InputStream input=Files.newInputStream(file)){byte[] b=new byte[1048576];for(int count;(count=input.read(b))!=-1;){n=Math.addExact(n,count);need(n<=maximum,"scDRS file grew beyond byte capacity: "+file);h.update(b,0,count);}}
        BasicFileAttributes after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        need(n==before.size()&&n==after.size()&&before.lastModifiedTime().equals(after.lastModifiedTime())&&java.util.Objects.equals(before.fileKey(),after.fileKey()),"scDRS file changed during complete hashing: "+file);
        return map("bytes",n,"sha256",HexFormat.of().formatHex(h.digest()));
    }
    static String member(Object value)throws IOException {
        String s=text(value);need(!s.isEmpty()&&!s.startsWith("/")&&!s.contains("\\")&&!s.contains(":"),"Invalid relative scDRS inventory member");
        for(String part:s.split("/",-1))need(!part.isEmpty()&&!part.equals(".")&&!part.equals("..")&&part.chars().noneMatch(c->c<32||c==127),"Unsafe scDRS inventory path");return s;
    }
    static Path canonical(Path path,boolean directory)throws IOException {
        need(path.isAbsolute()&&path.normalize().equals(path)&&!Files.isSymbolicLink(path)&&path.toRealPath().equals(path),"Require canonical unlinked scDRS path: "+path);
        need(directory?Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS):Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"scDRS path kind differs: "+path);return path;
    }
    static byte[] resource(String name)throws IOException {
        try(InputStream in=ScdrsRuntime.class.getResourceAsStream("/org/jlinalg/omics/scdrs/"+name)){
            need(in!=null,"Packaged scDRS resource is missing: "+name);return in.readAllBytes();
        }
    }
    static Map<String,Object> closure(Path root,int maximumFiles,long maximumBytes)throws IOException {
        canonical(root,true);var files=new TreeMap<String,Object>();long[] total={0};int[] entries={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a)throws IOException {
                need(++entries[0]<=maximumFiles&&!a.isSymbolicLink(),"scDRS runtime directory/link capacity exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes a)throws IOException {
                need(++entries[0]<=maximumFiles,"scDRS runtime file capacity exceeded");String name=member(root.relativize(file).toString());
                if(a.isSymbolicLink()){
                    Path target=Files.readSymbolicLink(file);need(file.toRealPath().startsWith(root),"scDRS runtime link escapes its declared root: "+name);
                    files.put(name,map("kind","symlink","target",target.toString()));
                } else {
                    need(a.isRegularFile(),"Nonregular scDRS runtime file: "+name);Map<String,Object> p=pin(file,maximumBytes);long size=(Long)p.get("bytes");
                    total[0]=Math.addExact(total[0],size);need(total[0]<=maximumBytes,"Complete scDRS runtime byte capacity exceeded");
                    long mode=((Number)Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS)).longValue()&0777;
                    files.put(name,map("kind","file","mode",mode,"bytes",size,"sha256",p.get("sha256")));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return map("files",files,"regular_bytes",total[0]);
    }
    static Runtime read(Path path,String version)throws IOException {
        if(!System.getProperty("os.name").equalsIgnoreCase("Linux")||!List.of("amd64","x86_64").contains(System.getProperty("os.arch")))
            throw new Unavailable("Original scDRS execution requires Linux x86_64 Java; use a permitted Linux/WSL host. Saved verification works separately on Windows.");
        if(!Files.isRegularFile(path))throw new Unavailable("Admitted scDRS runtime profile absent; export the helper and prepare permitted exact private prerequisites");
        canonical(path,false);Map<?,?> p=object(ScdrsJson.read(path,67108864));
        keys(p,"schema_version","tool","version","source_commit","platform","python","environment","source","versions","closure","source_lock","preparer","setup_only","system_loader_and_libraries_independently_pinned","hashes_are_authenticity");
        integer(p.get("schema_version"),1,1);need(text(p.get("tool")).equals("scDRS"),"Invalid scDRS runtime profile");
        byte[] raw=resource("scdrs-workflow-lock.json");Map<?,?> lock=object(SimpleJson.parse(new String(raw,java.nio.charset.StandardCharsets.UTF_8)));
        Map<?,?> sources=object(lock.get("sources"));
        if(!sources.containsKey(version)||!text(p.get("version")).equals(version)||!text(p.get("platform")).equals("linux-x86_64"))
            throw new Unavailable("Explicit historical scDRS version/platform differs from available profile; no version substitution");
        savedProfile(p,version);
        Map<?,?> sourceLock=object(sources.get(version));
        need(text(p.get("source_commit")).equals(text(sourceLock.get("commit"))),"Original scDRS immutable commit differs");
        need(object(p.get("source_lock")).equals(map("bytes",(long)raw.length,"sha256",digest(raw))),"scDRS source/dependency lock differs from packaged lock");
        byte[] helper=resource("prepare_scdrs_runtime.py");
        need(object(p.get("preparer")).equals(map("bytes",(long)helper.length,"sha256",digest(helper))),"Runtime profile requires this exact preparer; preserve previous attempts and use NEW paths");
        need(bool(p.get("setup_only"))&&!bool(p.get("system_loader_and_libraries_independently_pinned"))&&!bool(p.get("hashes_are_authenticity")),"Unsupported runtime attestation claims");
        Path environment=Path.of(text(p.get("environment"))),source=Path.of(text(p.get("source"))),python=Path.of(text(p.get("python")));
        if(!Files.isDirectory(environment)||!Files.isDirectory(source)||!Files.isExecutable(python))throw new Unavailable("Private scDRS executable/source/environment is unavailable; skip dependent analysis and recreate permitted prerequisites");
        canonical(environment,true);canonical(source,true);
        need(!source.startsWith(environment)&&!environment.startsWith(source),"Runtime roots must be separate without nesting");
        need(python.equals(environment.resolve("bin/python"))&&python.toRealPath().startsWith(environment),"Python escapes private environment");
        Map<?,?> versions=object(p.get("versions"));
        need(text(versions.get("python")).equals(text(lock.get("python_version")))&&text(versions.get("scdrs")).equals(version)&&object(versions.get("distributions")).equals(object(lock.get("installed_distributions"))),"Private dependency-version evidence differs");
        need(Path.of(text(versions.get("prefix"))).equals(environment),"Private interpreter prefix differs");
        Map<?,?> modules=object(versions.get("modules"));
        for(var e:modules.entrySet())need(Path.of(text(e.getValue())).startsWith(text(e.getKey()).equals("scdrs")?source:environment),"Imported dependency escapes private roots");
        Map<?,?> expected=object(p.get("closure"));keys(expected,"environment","source");
        Map<?,?> sourceFiles=object(object(expected.get("source")).get("files")),lockedFiles=object(sourceLock.get("files"));
        need(sourceFiles.keySet().equals(lockedFiles.keySet()),"Original source file inventory differs; no extra aliases/bytecode admitted");
        for(var entry:sourceFiles.entrySet()){
            member(entry.getKey());Map<?,?> f=object(entry.getValue());
            need(text(f.get("kind")).equals("file")&&object(map("bytes",f.get("bytes"),"sha256",f.get("sha256"))).equals(object(lockedFiles.get(entry.getKey()))),"Original source bytes differ: "+entry.getKey());
        }
        Runtime runtime=new Runtime(path,python,environment,source,p,expected,lock);verify(runtime);return runtime;
    }
    /** Structural source/dependency provenance without executing or requiring the saved Linux runtime. */
    static void savedProfile(Map<?,?> p,String version)throws IOException {
        keys(p,"schema_version","tool","version","source_commit","platform","python","environment","source","versions","closure","source_lock","preparer","setup_only","system_loader_and_libraries_independently_pinned","hashes_are_authenticity");
        integer(p.get("schema_version"),1,1);
        need(text(p.get("tool")).equals("scDRS")&&text(p.get("version")).equals(version)&&text(p.get("platform")).equals("linux-x86_64"),"Saved runtime tool/version/platform differs");
        byte[] raw=resource("scdrs-workflow-lock.json");Map<?,?> lock=object(SimpleJson.parse(new String(raw,java.nio.charset.StandardCharsets.UTF_8)));
        Map<?,?> sources=object(lock.get("sources"));need(sources.containsKey(version),"Saved scDRS version is not in packaged source lock");Map<?,?> sourceLock=object(sources.get(version));
        need(text(p.get("source_commit")).equals(text(sourceLock.get("commit"))),"Saved immutable scDRS source commit differs");
        need(object(p.get("source_lock")).equals(map("bytes",(long)raw.length,"sha256",digest(raw))),"Saved source/dependency lock differs");
        byte[] helper=resource("prepare_scdrs_runtime.py");need(object(p.get("preparer")).equals(map("bytes",(long)helper.length,"sha256",digest(helper))),"Saved runtime preparer differs from packaged helper");
        need(bool(p.get("setup_only"))&&!bool(p.get("system_loader_and_libraries_independently_pinned"))&&!bool(p.get("hashes_are_authenticity")),"Saved runtime makes unsupported attestation claims");
        String environment=posix(text(p.get("environment"))),source=posix(text(p.get("source"))),python=posix(text(p.get("python")));
        need(!inside(environment,source)&&!inside(source,environment)&&python.equals(environment+"/bin/python"),"Saved runtime roots/executable differ");
        Map<?,?> versions=object(p.get("versions"));keys(versions,"python","prefix","scdrs","distributions","modules","search_path");
        need(text(versions.get("python")).equals(text(lock.get("python_version")))&&text(versions.get("scdrs")).equals(version)&&text(versions.get("prefix")).equals(environment)&&object(versions.get("distributions")).equals(object(lock.get("installed_distributions"))),"Saved dependency/Python versions differ");
        Map<?,?> closure=object(p.get("closure"));keys(closure,"environment","source");
        for(String key:List.of("environment","source")){
            Map<?,?> c=object(closure.get(key));keys(c,"files","regular_bytes");Map<?,?> files=object(c.get("files"));need(!files.isEmpty()&&files.size()<=100000,"Saved runtime entry capacity differs");long total=0;
            for(var e:files.entrySet()){
                String name=member(e.getKey());Map<?,?> f=object(e.getValue());String kind=text(f.get("kind"));
                if(kind.equals("file")){
                    keys(f,"kind","mode","bytes","sha256");integer(f.get("mode"),0,0777);long size=integer(f.get("bytes"),0,4_000_000_000L);total=Math.addExact(total,size);
                    need(text(f.get("sha256")).matches("[0-9a-f]{64}"),"Invalid saved runtime digest");
                }else{
                    need(key.equals("environment")&&kind.equals("symlink"),"Only private environment links are admitted");keys(f,"kind","target");
                    String target=text(f.get("target"));need(!target.isEmpty()&&!target.contains("\\")&&target.chars().noneMatch(ch->ch<32||ch==127),"Invalid runtime link target");
                    String parent=environment+"/"+(name.contains("/")?name.substring(0,name.lastIndexOf('/')):"");
                    need(inside(normalizePosix(target.startsWith("/")?target:parent+"/"+target),environment),"Saved environment link escapes root");
                }
            }
            need(total==integer(c.get("regular_bytes"),1,4_000_000_000L),"Saved runtime total byte count differs");
        }
        Map<?,?> sourceFiles=object(object(closure.get("source")).get("files")),lockedFiles=object(sourceLock.get("files"));
        need(sourceFiles.keySet().equals(lockedFiles.keySet()),"Saved original source file inventory differs");
        for(var e:sourceFiles.entrySet()){Map<?,?> f=object(e.getValue());need(text(f.get("kind")).equals("file")&&map("bytes",f.get("bytes"),"sha256",f.get("sha256")).equals(object(lockedFiles.get(e.getKey()))),"Saved original source bytes differ: "+e.getKey());}
        Map<?,?> modules=object(versions.get("modules"));need(modules.keySet().equals(Set.of("scdrs","numpy","scipy","pandas","scanpy","anndata","skmisc","statsmodels","numba","umap","h5py","fire","pkg_resources")),"Saved imported-module inventory differs");
        for(var e:modules.entrySet()){
            String key=text(e.getKey()),path=posix(text(e.getValue())),base=key.equals("scdrs")?source:environment;need(inside(path,base)&&!path.equals(base),"Saved imported module escapes roots");
            Map<?,?> files=object(object(closure.get(key.equals("scdrs")?"source":"environment")).get("files"));need(files.containsKey(path.substring(base.length()+1)),"Saved imported module absent from runtime inventory");
        }
        for(Object v:list(versions.get("search_path"))){String path=text(v);path=path.isEmpty()?source:posix(path);need(inside(path,source)||inside(path,environment),"Saved Python search path escapes private roots");}
    }
    private static boolean inside(String path,String root){return path.equals(root)||path.startsWith(root+"/");}
    static String posix(String path)throws IOException {need(!path.equals("/")&&path.startsWith("/")&&!path.contains("\\")&&path.chars().noneMatch(c->c<32||c==127)&&path.equals(normalizePosix(path)),"Canonical saved Linux path required");return path;}
    private static String normalizePosix(String path)throws IOException {
        need(path.startsWith("/"),"Absolute Linux path required");var parts=new java.util.ArrayList<String>();
        for(String s:path.split("/",-1)){if(s.isEmpty()||s.equals("."))continue;if(s.equals("..")){need(!parts.isEmpty(),"Path escapes Linux root");parts.remove(parts.size()-1);}else parts.add(s);}return "/"+String.join("/",parts);
    }
    static void verify(Runtime runtime)throws IOException {
        Map<String,Object> current=map("environment",closure(runtime.environment(),100000,4_000_000_000L),"source",closure(runtime.source(),100000,4_000_000_000L));
        need(current.equals(runtime.closure()),"Complete scDRS runtime/source closure changed; no result admission or silent profile refresh");
    }
}
