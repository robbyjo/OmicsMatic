/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Streaming complete declared compiler trees and transitive filesystem aliases.
 * Manifest capacity counts actual encoded bytes; file content is never copied into a report. */
final class RsemFileGraph {
    private RsemFileGraph() { }
    record Limits(long fileBytes,long logicalBytes,int members,int roots,long metadataBytes) {
        Limits {
            if(fileBytes<1||fileBytes>1099511627776L||logicalBytes<1||logicalBytes>1099511627776L||members<1||members>1000000
                    ||roots<1||roots>128||metadataBytes<1024||metadataBytes>536870912L)throw new IllegalArgumentException("Invalid explicit compiler file-graph capacities");
        }
    }
    record Snapshot(List<String> roots,List<String> files,Limits limits,long logicalBytes,int members,long metadataBytes,String sha256) {
        Snapshot {roots=List.copyOf(roots);files=List.copyOf(files);}
        void verify(Runnable check)throws IOException{
            Snapshot actual=capture(roots.stream().map(Path::of).toList(),files.stream().map(Path::of).toList(),limits,null,check);
            if(!equals(actual))throw new IOException("Declared compiler files,tree membership,aliases or permissions changed;compare the retained compiler manifest and use a NEW permitted setup");
        }
    }
    private record Stamp(Object key,long size,Object modified,Object created,Object changed,int mode) { }
    static Snapshot capture(List<Path> roots,List<Path> files,Limits limits,Path newManifest,Runnable check)throws IOException{
        if(roots.size()>limits.roots()||files.size()>limits.members())throw new IOException("Compiler graph request population exceeds capacity");
        List<String> rootNames=names(roots),fileNames=names(files);
        if(newManifest!=null){
            Path output=newManifest.toAbsolutePath().normalize();
            if(output.getParent()==null||!output.getParent().toRealPath().equals(output.getParent())||roots.stream().anyMatch(output::startsWith)||files.contains(output))
                throw new IOException("Compiler manifest must be NEW,canonical and outside the captured inputs");
        }
        try(OutputStream output=newManifest==null?OutputStream.nullOutputStream():Files.newOutputStream(newManifest,StandardOpenOption.CREATE_NEW)){
            var capture=new Capture(limits,output,check,roots,files);
            capture.row(Map.of("schema_version",1,"roots",rootNames,"files",fileNames,"limits",limits,
                    "scope","Complete declared trees and linked targets,including missing nodes and parent aliases. Logical regular-file bytes count every captured spelling;physical hashes may be reused. Not an OS sandbox or signature."));
            for(String name:rootNames)capture.request(Path.of(name),true);
            for(String name:fileNames)capture.request(Path.of(name),false);
            capture.read();
            return new Snapshot(rootNames,fileNames,limits,capture.logical,capture.count,capture.encoded,
                    java.util.HexFormat.of().formatHex(capture.digest.digest()));
        }
    }
    private static List<String> names(List<Path> paths)throws IOException{
        var names=new TreeSet<String>();for(Path path:paths){
            if(!path.isAbsolute()||!path.normalize().equals(path)||path.toString().length()>4096||!names.add(path.toString()))
                throw new IOException("Compiler graph requires distinct normalized absolute paths: "+path);
        }return List.copyOf(names);
    }
    private static final class Capture {
        final Limits limits;final OutputStream output;final Runnable check;final MessageDigest digest;
        final TreeMap<String,Boolean> pending=new TreeMap<>();final Set<String> emitted=new HashSet<>(),walked=new HashSet<>();
        final Set<Path> permitted=new HashSet<>();
        final Map<Stamp,String> hashes=new HashMap<>();long logical,encoded;int count;
        Capture(Limits limits,OutputStream output,Runnable check,List<Path> roots,List<Path> files){this.limits=limits;this.output=output;this.check=java.util.Objects.requireNonNull(check);
            permitted.addAll(roots);files.forEach(p->{if(p.getParent()!=null)permitted.add(p.getParent());});
            if("Linux".equals(System.getProperty("os.name")))for(String p:List.of("/usr","/etc","/bin","/sbin","/lib","/lib64"))permitted.add(Path.of(p));
            try{digest=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
        void row(Object value)throws IOException{
            byte[] raw=RsemJson.bytes(value);encoded=Math.addExact(encoded,raw.length);
            if(encoded>limits.metadataBytes())throw new IOException("Complete compiler manifest exceeds its actual encoded metadata capacity");
            output.write(raw);digest.update(raw);
        }
        void request(Path path,boolean tree)throws IOException{
            if(!path.isAbsolute()||!path.normalize().equals(path)||path.toString().length()>4096)throw new IOException("Unbounded/noncanonical compiler graph path: "+path);
            String name=path.toString();if(!emitted.contains(name)||(tree&&!walked.contains(name)))pending.merge(name,tree,(a,b)->a||b);
            if((long)pending.size()+emitted.size()>2L*limits.members())throw new IOException("Compiler graph pending population exceeds capacity");
            for(Path parent=path.getParent();parent!=null;parent=parent.getParent())if(!emitted.contains(parent.toString()))pending.putIfAbsent(parent.toString(),false);
        }
        void read()throws IOException{
            while(!pending.isEmpty()){
                check.run();var next=pending.pollFirstEntry();Path path=Path.of(next.getKey());boolean tree=next.getValue();
                if(emitted.contains(path.toString())&&(!tree||walked.contains(path.toString())))continue;
                BasicFileAttributes attributes;
                try{attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
                catch(NoSuchFileException missing){if(emitted.add(path.toString())){member();row(Map.of("path",path,"kind","ABSENT"));}continue;}
                if(attributes.isSymbolicLink()){
                    Path raw=Files.readSymbolicLink(path),real=null;
                    try{real=resolution(path,new HashSet<>(),new int[]{0});}catch(NoSuchFileException dangling){/* The link and absent target remain evidence. */}
                    if(emitted.add(path.toString())){
                        member();var record=new LinkedHashMap<String,Object>();record.put("path",path);record.put("kind","LINK");record.put("target",raw.toString());record.put("real",real);record.put("mode",mode(path));row(record);
                    }
                    if(real!=null){
                        if(permitted.stream().noneMatch(real::startsWith))throw new IOException("Compiler alias resolves outside declared/system provider roots: "+path+" -> "+real);
                        request(real,tree);
                    }if(tree)walked.add(path.toString());
                    continue;
                }
                if(attributes.isDirectory()){
                    if(emitted.add(path.toString())){member();row(Map.of("path",path,"kind","DIRECTORY","mode",mode(path)));}
                    if(tree&&walked.add(path.toString())){
                        var children=new ArrayList<Path>();try(var entries=Files.newDirectoryStream(path)){
                            for(Path child:entries){check.run();if(children.size()>=limits.members())throw new IOException("Compiler directory population exceeds capacity");children.add(child);}
                        }
                        children.sort(Comparator.comparing(Path::toString));for(Path child:children)request(child,true);
                    }continue;
                }
                if(!attributes.isRegularFile())throw new IOException("Compiler provider is nonregular: "+path);
                if(emitted.add(path.toString())){
                    member();Stamp before=stamp(path);logical=Math.addExact(logical,before.size());
                    if(before.size()>limits.fileBytes()||logical>limits.logicalBytes())throw new IOException("Complete compiler file bytes exceed capacity: "+path);
                    String hash=hashes.get(before);
                    if(hash==null){hash=RepeatInputs.hash(path,limits.fileBytes(),check);hashes.put(before,hash);}
                    if(!before.equals(stamp(path)))throw new IOException("Compiler file changed while hashing: "+path);
                    var record=new LinkedHashMap<String,Object>();record.put("path",path);record.put("kind","FILE");record.put("bytes",before.size());record.put("sha256",hash);record.put("mode",before.mode());
                    record.put("modified",before.modified().toString());record.put("created",before.created().toString());record.put("changed",before.changed()==null?null:before.changed().toString());
                    record.put("readable",Files.isReadable(path));record.put("executable",Files.isExecutable(path));row(record);
                }
            }
        }
        private Path resolution(Path spelling,Set<String> stack,int[] hops)throws IOException{
            Path current=spelling.getRoot();
            for(Path component:spelling){
                if(!Files.isDirectory(current))throw new IOException("Compiler alias crosses a nondirectory component: "+current);
                String name=component.toString();if(name.equals("."))continue;
                if(name.equals("..")){if(current.getParent()!=null)current=current.getParent();continue;}
                Path candidate=current.resolve(component);BasicFileAttributes a;
                try{a=Files.readAttributes(candidate,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
                catch(NoSuchFileException missing){request(candidate,false);throw missing;}
                if(a.isSymbolicLink()){
                    if(++hops[0]>64||!stack.add(candidate.toString()))throw new IOException("Compiler link-resolution loop/capacity exceeded: "+candidate);
                    request(candidate,false);Path target=Files.readSymbolicLink(candidate);
                    current=resolution(target.isAbsolute()?target:candidate.getParent().resolve(target),stack,hops);stack.remove(candidate.toString());
                }else current=candidate;
            }
            return current;
        }
        private void member()throws IOException{if(++count>limits.members())throw new IOException("Complete compiler graph member capacity exceeded");}
    }
    private static int mode(Path path)throws IOException{
        try{return ((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue();}
        catch(UnsupportedOperationException unsupported){return (Files.isReadable(path)?4:0)|(Files.isWritable(path)?2:0)|(Files.isExecutable(path)?1:0);}
    }
    private static Stamp stamp(Path path)throws IOException{
        BasicFileAttributes a=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile())throw new IOException("Compiler file became linked/nonregular: "+path);Object changed;
        try{changed=Files.getAttribute(path,"unix:ctime",LinkOption.NOFOLLOW_LINKS);}catch(UnsupportedOperationException unsupported){changed=null;}
        return new Stamp(a.fileKey(),a.size(),a.lastModifiedTime(),a.creationTime(),changed,mode(path));
    }
}
