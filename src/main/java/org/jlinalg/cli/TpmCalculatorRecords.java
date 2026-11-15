/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import org.jlinalg.sequence.TpmCalculator;

/** Complete byte inventories and fixed scientific code identities, including anonymous classes. */
final class TpmCalculatorRecords {
    private TpmCalculatorRecords() { }
    static final String HTSJDK_PROFILE="8412c36441b2dd1a8bb490162839ec5c29b261cae3aee4605c266732b1cf3629";
    static final int METADATA=16*1024*1024;
    static final Set<String> TERMINAL=Set.of("MANIFEST.json","COMPLETED.json");
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static void check()throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("Native TPMCalculator operation interrupted");}
    static void json(Path path,Object value)throws IOException{write(path,(NextflowJson.json(value)+"\n").getBytes(StandardCharsets.UTF_8));}
    static void write(Path path,byte[] value)throws IOException{Files.write(path,value,StandardOpenOption.CREATE_NEW);}
    static Object read(Path path)throws IOException{return TpmCalculatorRequest.parse(NextflowFiles.bytes(path,METADATA),METADATA);}
    static Map<String,Object> object(Map<?,?> source)throws IOException {
        var out=new LinkedHashMap<String,Object>();for(var e:source.entrySet())out.put(NextflowFiles.text(e.getKey()),e.getValue());return out;
    }
    static Path child(Path root,String member)throws IOException {
        NextflowFiles.member(member);Path p=root.resolve(member).normalize();if(!p.startsWith(root)||p.equals(root))throw new IOException("Native bundle member escapes its recorded root");return p;
    }
    static NextflowFiles.Identity identity(Path file,long max)throws IOException {
        var id=NextflowFiles.identity(file,max,()->{try{check();}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
        if(id.links()>1)throw new IOException("Hard-linked native input/bundle file is not supported: "+file);return id;
    }
    static NextflowFiles.Pin pin(Path p,long maximum)throws IOException{return identity(p,maximum).pin();}
    static Path newDirectory(Path path)throws IOException {
        Path parent=path.getParent();if(parent==null||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS)||!parent.toRealPath().equals(parent))
            throw new Unavailable("Use a NEW output directory below an existing canonical writable parent: "+path);
        try{Files.createDirectory(path);}catch(java.nio.file.AccessDeniedException e){throw new Unavailable("Output creation is forbidden; supply an existing permitted writable parent and a NEW path",e);}
        if(path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            var mode=PosixFilePermissions.fromString("rwx------");Files.setPosixFilePermissions(path,mode);
            if(!Files.getPosixFilePermissions(path).equals(mode))throw new IOException("Cannot retain private native output-directory permissions");
        }
        return path;
    }
    static void disjoint(Path out,Path input)throws IOException {
        if(out.startsWith(input)||input.startsWith(out))throw new IOException("NEW output overlaps a protected request/input/bundle: "+input);
    }
    static Map<String,Object> capture(Path source,Path target,long maximum)throws IOException {
        NextflowFiles.Identity before;
        try{before=identity(source,maximum);}catch(IOException e){throw new Unavailable("Supply the readable canonical unlinked input and sufficient declared capacity: "+source,e);}
        try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            byte[] bytes=new byte[65536];long count=0;int n;
            while((n=in.read(bytes))!=-1){check();count=Math.addExact(count,n);if(count>maximum)throw new IOException("Native input grew beyond complete capture capacity");out.write(bytes,0,n);}
        }
        if(!identity(source,maximum).equals(before)||!pin(target,maximum).equals(before.pin()))throw new IOException("Native original/captured input changed during capture: "+source);
        return Map.of("original",source.toString(),"captured",target.getFileName().toString(),"source_identity",before,"pin",before.pin());
    }
    static Map<String,Object> inventory(Path root,long maximum,int files,Set<String> excluded,boolean hashes)throws IOException {
        var entries=new TreeMap<String,Object>();long[] bytes={0};long[] count={0};
        if(!root.toRealPath().equals(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require a canonical unlinked native bundle directory");
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException {
                check();if(++count[0]>files||!p.toRealPath().equals(p))throw new IOException("Native directory count/link capacity exceeded");
                if(!p.equals(root))entries.put(member(root,p),Map.of("kind","directory"));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException {
                check();String name=member(root,p);if(excluded.contains(name))return FileVisitResult.CONTINUE;
                if(++count[0]>files||!a.isRegularFile()||a.isSymbolicLink())throw new IOException("Native file count/type/link capacity exceeded: "+name);
                bytes[0]=Math.addExact(bytes[0],a.size());if(bytes[0]>maximum)throw new IOException("Complete native bundle byte capacity exceeded");
                if(hashes) {var pin=pin(p,maximum);entries.put(name,Map.of("kind","file","bytes",pin.bytes(),"sha256",pin.sha256()));}
                else entries.put(name,Map.of("kind","file","bytes",a.size()));return FileVisitResult.CONTINUE;
            }
        });return entries;
    }
    static String member(Path root,Path p)throws IOException {
        String value=root.relativize(p).toString();if(p.getFileSystem().getSeparator().equals("\\"))value=value.replace('\\','/');return NextflowFiles.member(value);
    }
    static void manifest(Path root,TpmCalculatorRequest.Limits limits)throws IOException {
        json(root.resolve("MANIFEST.json"),Map.of("schema",1,"files",inventory(root,limits.bundleBytes(),limits.files(),TERMINAL,true)));
    }
    static void checkManifest(Path root,TpmCalculatorRequest.Limits limits)throws IOException {
        Map<?,?> m=NextflowFiles.object(read(root.resolve("MANIFEST.json")));NextflowFiles.keys(m,"schema","files");
        if(NextflowFiles.integer(m.get("schema"),1,1)!=1)throw new IOException("Unknown native manifest schema");
        String expected=NextflowJson.json(m.get("files")),actual=NextflowJson.json(inventory(root,limits.bundleBytes(),limits.files(),TERMINAL,true));
        if(!expected.equals(actual))throw new IOException("Native bundle complete inventory/bytes differ from the manifest");
    }
    static void complete(Path root,Map<String,Object> value)throws IOException {
        Path finalPath=root.resolve("COMPLETED.json"),pending=root.resolve("COMPLETION-PROVISIONAL.json");
        if(Files.exists(finalPath,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unexpected existing native completion; no complete result");
        json(pending,value);Files.move(pending,finalPath,StandardCopyOption.ATOMIC_MOVE);
    }
    static void revoke(Path root)throws IOException {
        Path path=root.resolve("COMPLETED.json");
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))Files.move(path,root.resolve("COMPLETED-UNADMITTED-"+java.util.UUID.randomUUID()+".json"));
    }
    record Implementation(Map<String,Object> identity,List<Path> classpath) { Implementation{identity=Map.copyOf(identity);classpath=List.copyOf(classpath);} }
    static Path location(Class<?> type)throws IOException {
        try {Path p=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if(!p.toRealPath().equals(p)||p.toString().indexOf(java.io.File.pathSeparatorChar)>=0)throw new IOException("Noncanonical or unrepresentable Java classpath location");return p;
        }catch(Exception e){throw new Unavailable("Supply the permitted packaged Java application/dependencies at canonical classpath locations",e);}
    }
    static Implementation implementation()throws IOException {
        var types=new ArrayList<Class<?>>(List.of(TpmCalculatorCli.class,TpmCalculatorWorker.class,TpmCalculatorProcess.class,TpmCalculatorRequest.class,
            TpmCalculatorProducts.class,TpmCalculatorRecords.class,TpmCalculator.class,NextflowFiles.class,NextflowParameters.class,NextflowJson.class,SimpleJson.class));
        try{types.add(Class.forName("org.jlinalg.sequence.TpmCalculatorOrder",false,TpmCalculator.class.getClassLoader()));}
        catch(ClassNotFoundException e){throw new Unavailable("Required native gene-order implementation is unavailable",e);}
        Path own=location(types.get(0));for(Class<?> type:types)if(!location(type).equals(own))throw new Unavailable("Native workflow scientific classes must come from one recorded application artifact");
        var names=new java.util.HashSet<String>();for(Class<?> type:types)names.add(type.getName().replace('.','/'));
        Map<String,Object> classes=new TreeMap<>();
        if(Files.isDirectory(own)) {
            for(String prefix:names) {
                Path parent=own.resolve(prefix).getParent();String stem=Path.of(prefix).getFileName().toString();
                try(var stream=Files.list(parent)) {
                    for(Path p:stream.filter(p->p.getFileName().toString().equals(stem+".class")||p.getFileName().toString().startsWith(stem+"$")&&p.getFileName().toString().endsWith(".class")).toList()) {
                        var id=pin(p,134217728);classes.put(member(own,p),Map.of("bytes",id.bytes(),"sha256",id.sha256()));
                    }
                }
            }
        }else try(ZipFile zip=new ZipFile(own.toFile())) {
            for(var e:java.util.Collections.list(zip.entries())) {
                String n=e.getName();boolean include=false;for(String prefix:names)if(n.equals(prefix+".class")||n.startsWith(prefix+"$")&&n.endsWith(".class")){include=true;break;}
                if(include){byte[] b=entry(zip,e);classes.put(n,Map.of("bytes",b.length,"sha256",NextflowFiles.sha(b)));}
            }
        }
        for(String name:names)if(!classes.containsKey(name+".class"))throw new Unavailable("Missing native class resource: "+name);
        Path hts=location(htsjdk.samtools.SamReaderFactory.class);if(!Files.isRegularFile(hts,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Supply the declared packaged HTSJDK5.0.0 dependency");
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
        int entries=0;try(ZipFile zip=new ZipFile(hts.toFile())) {
            for(var e:java.util.Collections.list(zip.entries()).stream().sorted(java.util.Comparator.comparing(java.util.zip.ZipEntry::getName)).toList()) {
                String n=e.getName();if(!e.isDirectory()&&(n.startsWith("htsjdk/")||n.startsWith("META-INF/services/htsjdk.")||n.startsWith("META-INF/versions/")&&n.contains("/htsjdk/"))) {
                    byte[] b=entry(zip,e);digest.update((n+"\0"+b.length+"\0"+NextflowFiles.sha(b)+"\n").getBytes(StandardCharsets.UTF_8));entries++;
                }
            }
        }
        String profile=HexFormat.of().formatHex(digest.digest());
        if(!profile.equals(HTSJDK_PROFILE)||entries!=1178)throw new Unavailable("Loaded HTSJDK bytes differ from the qualified5.0.0 transport. Supply the project's exact declared dependency");
        return new Implementation(Map.of("schema",1,"profile",TpmCalculatorRequest.PROFILE,"scientific_class_files",classes,"htsjdk_transport_sha256",profile,
            "original_source_commit",TpmCalculator.SOURCE_COMMIT,"original_tag","0.0.4","original_printed_version","0.0.3","libdeflate_enabled",false),own.equals(hts)?List.of(own):List.of(own,hts));
    }
    private static byte[] entry(ZipFile zip,java.util.zip.ZipEntry entry)throws IOException {
        if(entry.getSize()<0||entry.getSize()>134217728)throw new IOException("Implementation resource exceeds complete identity capacity");
        try(var input=zip.getInputStream(entry)){byte[] b=input.readNBytes(134217729);if(b.length!=entry.getSize())throw new IOException("Implementation resource truncated or changed");return b;}
    }
}
