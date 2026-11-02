/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Complete content-addressed workflow/data captures and preserved declared aliases. */
final class NextflowCapture {
    private NextflowCapture() { }
    record Limits(int files,long bytes,long fileBytes) {
        Limits {if(files<1||files>1000000||bytes<1||fileBytes<1||fileBytes>bytes)throw new IllegalArgumentException("Invalid complete Nextflow capture capacities");}
    }
    record Item(String kind,NextflowFiles.Identity original,String target,String resolved,int mode) { }
    record Root(String key,String source,String leaf,String kind,Map<String,Item> items) {Root {items=Map.copyOf(items);}}
    record Snapshot(List<Root> roots,Map<String,String> aliases) {Snapshot {roots=List.copyOf(roots);aliases=Map.copyOf(aliases);}}

    static Snapshot create(Path owned,Map<String,Path> sources,Limits limits,Runnable check)throws IOException {
        NextflowFiles.directory(owned);Path objects=owned.resolve("objects");Files.createDirectory(objects);
        var selected=new LinkedHashMap<String,Path>();
        for(var row:sources.entrySet()){
            if(!row.getKey().matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw new IOException("Invalid declared Nextflow resource ID: "+row.getKey());
            Path source=row.getValue().toAbsolutePath().normalize();
            if(!source.toRealPath().equals(source)||source.getFileName()==null)throw new IOException("Declare a canonical named source root, not a root symlink/filesystem root: "+source);
            if(source.startsWith(owned)||owned.startsWith(source))throw new IOException("Captured sources and owned output session must be disjoint: "+source);
            selected.put(row.getKey(),source);
        }
        var reduced=new ArrayList<Path>();
        for(Path source:selected.values().stream().distinct().sorted(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString)).toList())if(reduced.stream().noneMatch(source::startsWith))reduced.add(source);
        var roots=new ArrayList<Root>();var aliases=new TreeMap<String,String>();long[] bytes={0};int[] count={0};
        for(int i=0;i<reduced.size();i++){
            Path source=reduced.get(i);String key="r"+i;Map<String,Item> items=collect(source,objects,limits,bytes,count,check);String kind=items.get("").kind();
            roots.add(new Root(key,source.toString(),source.getFileName().toString(),kind,items));
        }
        for(var row:selected.entrySet())for(Root root:roots){Path source=Path.of(root.source());if(row.getValue().startsWith(source)){String suffix=source.relativize(row.getValue()).toString();aliases.put(row.getKey(),root.key()+"/"+root.leaf()+(suffix.isEmpty()?"":"/"+posix(row.getValue().getFileSystem().getSeparator(),suffix)));break;}}
        if(aliases.size()!=selected.size())throw new IOException("Incomplete captured resource alias family");
        return new Snapshot(roots,aliases);
    }

    private static Map<String,Item> collect(Path source,Path objects,Limits limits,long[] bytes,int[] count,Runnable check)throws IOException {
        var result=new TreeMap<String,Item>();
        Files.walkFileTree(source,new SimpleFileVisitor<>(){
            private String name(Path path){return path.equals(source)?"":posix(path.getFileSystem().getSeparator(),source.relativize(path).toString());}
            private void charge()throws IOException{check.run();if(++count[0]>limits.files())throw new IOException("Complete Nextflow capture file/directory capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{charge();result.put(name(path),new Item("directory",null,null,null,NextflowFiles.mode(path)));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{
                charge();String name=name(path);if(!name.isEmpty())NextflowFiles.member(name);
                if(attrs.isSymbolicLink()){
                    Path target=Files.readSymbolicLink(path);result.put(name,new Item("symlink",null,target.toString(),path.toRealPath().toString(),-1));
                }else if(attrs.isRegularFile()){
                    var identity=NextflowFiles.identity(path,limits.fileBytes(),check);bytes[0]=Math.addExact(bytes[0],identity.pin().bytes());if(bytes[0]>limits.bytes())throw new IOException("Complete Nextflow capture byte capacity exceeded");
                    store(objects,path,identity.pin(),limits.fileBytes(),check);if(!identity.equals(NextflowFiles.identity(path,limits.fileBytes(),check)))throw new IOException("Declared source changed during complete capture: "+path);
                    result.put(name,new Item("file",identity,null,null,identity.mode()));
                }else throw new IOException("Nonregular declared Nextflow input: "+path);
                return FileVisitResult.CONTINUE;
            }
        });
        return Map.copyOf(result);
    }

    static Map<String,Path> materialize(Path owned,Snapshot snapshot,Path destination,Limits limits,Runnable check)throws IOException {
        NextflowFiles.directory(owned);Files.createDirectory(destination);var locations=new TreeMap<String,Path>();
        for(Root root:snapshot.roots()){
            Path folder=destination.resolve(root.key());Files.createDirectory(folder);Path target=folder.resolve(root.leaf());locations.put(root.key(),target);
            for(var row:sorted(root.items()).entrySet())if(row.getValue().kind().equals("directory"))Files.createDirectories(resolve(target,row.getKey()));
            for(var row:sorted(root.items()).entrySet())if(row.getValue().kind().equals("file")){
                Path file=resolve(target,row.getKey());Files.createDirectories(file.getParent());Item item=row.getValue();Path object=owned.resolve("objects").resolve(item.original().pin().sha256());
                if(!NextflowFiles.pin(object,limits.fileBytes(),check).equals(item.original().pin()))throw new IOException("Captured object differs before materialization");
                Files.copy(object,file);permissions(file,item.mode());Files.setLastModifiedTime(file,FileTime.from(Instant.parse(item.original().modified())));
                if(!NextflowFiles.pin(file,limits.fileBytes(),check).equals(item.original().pin()))throw new IOException("Complete materialized file differs");
                if(item.mode()>=0&&(item.mode()&0111)!=0&&!Files.isExecutable(file))throw new NextflowRuntime.Unavailable("Captured workflow executable permission is forbidden: "+file);
            }
        }
        for(Root root:snapshot.roots())for(var row:sorted(root.items()).entrySet())if(row.getValue().kind().equals("symlink")){
            Path source=Path.of(row.getValue().resolved()),mapped=null;
            for(Root candidate:snapshot.roots()){Path original=Path.of(candidate.source());if(source.startsWith(original)){mapped=locations.get(candidate.key()).resolve(original.relativize(source));break;}}
            if(mapped==null)throw new IOException("Linked workflow/data target must be included among the declared source roots: "+row.getValue().resolved());
            Path link=resolve(locations.get(root.key()),row.getKey());Files.createDirectories(link.getParent());Files.createSymbolicLink(link,link.getParent().relativize(mapped));
        }
        var aliases=new TreeMap<String,Path>();for(var row:snapshot.aliases().entrySet()){String[] parts=row.getValue().split("/",2);Path path=destination.resolve(row.getValue()).normalize();if(!path.startsWith(destination)||parts.length!=2||!Files.exists(path))throw new IOException("Incomplete materialized declared resource: "+row.getKey());aliases.put(row.getKey(),path);}
        return Map.copyOf(aliases);
    }

    static void verifyOriginals(Snapshot snapshot,Limits limits,Runnable check)throws IOException {
        long[] bytes={0};int[] count={0};
        for(Root root:snapshot.roots()){
            // The object directory is absent: verify without creating any bytes.
            Map<String,Item> current=inspect(Path.of(root.source()),limits,bytes,count,check);
            if(!current.equals(root.items()))throw new IOException("Original declared workflow/data family changed after capture: "+root.source());
        }
    }
    private static Map<String,Item> inspect(Path source,Limits limits,long[] bytes,int[] count,Runnable check)throws IOException{
        var result=new TreeMap<String,Item>();
        Files.walkFileTree(source,new SimpleFileVisitor<>(){
            private String name(Path path){return path.equals(source)?"":posix(path.getFileSystem().getSeparator(),source.relativize(path).toString());}
            private void charge()throws IOException{check.run();if(++count[0]>limits.files())throw new IOException("Complete original resource census exceeds capacity");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{charge();result.put(name(path),new Item("directory",null,null,null,NextflowFiles.mode(path)));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{
                charge();String name=name(path);if(attrs.isSymbolicLink())result.put(name,new Item("symlink",null,Files.readSymbolicLink(path).toString(),path.toRealPath().toString(),-1));
                else if(attrs.isRegularFile()){var identity=NextflowFiles.identity(path,limits.fileBytes(),check);bytes[0]=Math.addExact(bytes[0],identity.pin().bytes());if(bytes[0]>limits.bytes())throw new IOException("Complete original byte capacity exceeded");result.put(name,new Item("file",identity,null,null,identity.mode()));}
                else throw new IOException("Original resource contains a nonregular member: "+path);return FileVisitResult.CONTINUE;
            }
        });return Map.copyOf(result);
    }

    static String store(Path objects,Path source,NextflowFiles.Pin expected,long maximum,Runnable check)throws IOException {
        Path target=objects.resolve(expected.sha256());
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){if(!NextflowFiles.pin(target,maximum,check).equals(expected))throw new IOException("Changed existing captured object");return expected.sha256();}
        long count=0;
        try(var input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];for(int n=input.read(buffer);n>=0;n=input.read(buffer)){check.run();count=Math.addExact(count,n);if(count>maximum||count>expected.bytes())throw new IOException("Complete source object exceeded capacity during capture");output.write(buffer,0,n);}
        }
        if(!NextflowFiles.pin(target,maximum,check).equals(expected))throw new IOException("Complete source object bytes differ after capture");return expected.sha256();
    }
    private static Map<String,Item> sorted(Map<String,Item> values){return new TreeMap<>(values);}
    private static Path resolve(Path root,String member)throws IOException{if(member.isEmpty())return root;NextflowFiles.member(member);Path result=root.resolve(member).normalize();if(!result.startsWith(root))throw new IOException("Captured member escapes owned root");return result;}
    private static String posix(String separator,String value){return separator.equals("\\")?value.replace('\\','/'):value;}
    private static void permissions(Path path,int mode)throws IOException {
        if(mode<0||!path.getFileSystem().supportedFileAttributeViews().contains("posix"))return;
        var values=EnumSet.noneOf(PosixFilePermission.class);PosixFilePermission[] bits={PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE,PosixFilePermission.GROUP_READ,PosixFilePermission.GROUP_WRITE,PosixFilePermission.GROUP_EXECUTE,PosixFilePermission.OTHERS_READ,PosixFilePermission.OTHERS_WRITE,PosixFilePermission.OTHERS_EXECUTE};
        for(int i=0;i<bits.length;i++)if((mode&(0400>>i))!=0)values.add(bits[i]);Files.setPosixFilePermissions(path,values);
    }
}
