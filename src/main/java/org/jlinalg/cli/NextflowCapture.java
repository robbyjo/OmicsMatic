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

    static Snapshot working(Path owned,Path source,Limits limits,Runnable check)throws IOException {
        NextflowFiles.directory(owned);NextflowFiles.directory(source);
        if(!source.startsWith(owned)||source.startsWith(owned.resolve("objects")))throw new IOException("Working capture must stay within the session resource tree");
        var items=collect(source,owned.resolve("objects"),limits,new long[]{0},new int[]{0},check,true);
        return new Snapshot(List.of(new Root("r0",source.toString(),source.getFileName().toString(),"directory",items)),Map.of("working","r0/"+source.getFileName()));
    }

    static Snapshot decode(Object value,Limits limits)throws IOException {
        Map<?,?> raw=NextflowFiles.object(value);NextflowFiles.keys(raw,"roots","aliases");
        var roots=new ArrayList<Root>();var keys=new java.util.HashSet<String>();long bytes=0;int count=0;
        for(Object element:NextflowFiles.list(raw.get("roots"))){
            Map<?,?> root=NextflowFiles.object(element);NextflowFiles.keys(root,"key","source","leaf","kind","items");
            String key=NextflowFiles.text(root.get("key")),leaf=NextflowFiles.member(NextflowFiles.text(root.get("leaf")));
            if(!key.matches("r[0-9]+")||!keys.add(key)||leaf.contains("/"))throw new IOException("Invalid captured root key/basename");
            var items=new TreeMap<String,Item>();
            for(var row:NextflowFiles.object(root.get("items")).entrySet()){
                String name=NextflowFiles.text(row.getKey());if(!name.isEmpty())NextflowFiles.member(name);
                if(++count>limits.files())throw new IOException("Complete recorded capture file capacity exceeded");
                Map<?,?> item=NextflowFiles.object(row.getValue());NextflowFiles.keys(item,"kind","original","target","resolved","mode");String kind=NextflowFiles.text(item.get("kind"));int mode=(int)NextflowFiles.integer(item.get("mode"),-1,0777);
                NextflowFiles.Identity identity=null;String target=null,resolved=null;
                if(kind.equals("file")){
                    Map<?,?> original=NextflowFiles.object(item.get("original"));NextflowFiles.keys(original,"pin","fileKey","modified","mode","links");
                    var pin=NextflowFiles.fingerprint(original.get("pin"),limits.fileBytes());bytes=Math.addExact(bytes,pin.bytes());if(bytes>limits.bytes())throw new IOException("Complete recorded capture byte capacity exceeded");
                    identity=new NextflowFiles.Identity(pin,NextflowFiles.text(original.get("fileKey")),NextflowFiles.text(original.get("modified")),(int)NextflowFiles.integer(original.get("mode"),-1,0777),NextflowFiles.integer(original.get("links"),-1,Long.MAX_VALUE));
                    if(mode!=identity.mode())throw new IOException("Captured permission metadata differs");
                }else if(kind.equals("fifo")||kind.equals("socket")){
                    Map<?,?> original=NextflowFiles.object(item.get("original"));NextflowFiles.keys(original,"pin","fileKey","modified","mode","links");if(original.get("pin")!=null)throw new IOException("Special workspace objects cannot claim captured stream bytes");
                    identity=new NextflowFiles.Identity(null,NextflowFiles.text(original.get("fileKey")),NextflowFiles.text(original.get("modified")),(int)NextflowFiles.integer(original.get("mode"),0,0777),NextflowFiles.integer(original.get("links"),1,Long.MAX_VALUE));if(mode!=identity.mode())throw new IOException("Special workspace mode metadata differs");
                }else if(kind.equals("symlink")){target=NextflowFiles.text(item.get("target"));resolved=item.get("resolved")==null?null:NextflowFiles.text(item.get("resolved"));if(mode!=-1)throw new IOException("Unexpected symlink permission record");}
                else if(!kind.equals("directory"))throw new IOException("Unknown captured member kind");
                if(!java.util.Set.of("file","fifo","socket").contains(kind)&&item.get("original")!=null||!kind.equals("symlink")&&(item.get("target")!=null||item.get("resolved")!=null))throw new IOException("Unexpected capture metadata fields");
                items.put(name,new Item(kind,identity,target,resolved,mode));
            }
            String kind=NextflowFiles.text(root.get("kind"));if(!items.containsKey("")||!kind.equals(items.get("").kind()))throw new IOException("Missing captured root member");
            for(String name:items.keySet())if(!name.isEmpty()){int slash=name.lastIndexOf('/');String parent=slash<0?"":name.substring(0,slash);if(!items.containsKey(parent)||!items.get(parent).kind().equals("directory"))throw new IOException("Incomplete captured directory family");}
            roots.add(new Root(key,NextflowFiles.text(root.get("source")),leaf,kind,items));
        }
        var aliases=new TreeMap<String,String>();
        for(var row:NextflowFiles.object(raw.get("aliases")).entrySet()){
            String id=NextflowFiles.text(row.getKey()),name=NextflowFiles.member(NextflowFiles.text(row.getValue()));if(!id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw new IOException("Invalid recorded resource ID");
            boolean found=false;for(Root root:roots){String prefix=root.key()+"/"+root.leaf();if(name.equals(prefix)||name.startsWith(prefix+"/")){String suffix=name.equals(prefix)?"":name.substring(prefix.length()+1);found=root.items().containsKey(suffix);break;}}
            if(!found)throw new IOException("Recorded alias has no captured member");aliases.put(id,name);
        }
        if(roots.isEmpty()||aliases.isEmpty())throw new IOException("Missing complete captured resource family");return new Snapshot(roots,aliases);
    }

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
        return collect(source,objects,limits,bytes,count,check,false);
    }
    private static Map<String,Item> collect(Path source,Path objects,Limits limits,long[] bytes,int[] count,Runnable check,boolean working)throws IOException {
        var result=new TreeMap<String,Item>();
        Files.walkFileTree(source,new SimpleFileVisitor<>(){
            private String name(Path path){return path.equals(source)?"":posix(path.getFileSystem().getSeparator(),source.relativize(path).toString());}
            private void charge()throws IOException{check.run();if(++count[0]>limits.files())throw new IOException("Complete Nextflow capture file/directory capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{charge();result.put(name(path),new Item("directory",null,null,null,NextflowFiles.mode(path)));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{
                charge();String name=name(path);if(!name.isEmpty())NextflowFiles.member(name);
                if(attrs.isSymbolicLink()){
                    Path target=Files.readSymbolicLink(path);String resolved;
                    try{resolved=path.toRealPath().toString();}catch(java.nio.file.NoSuchFileException dangling){if(!working)throw dangling;resolved=null;}
                    result.put(name,new Item("symlink",null,target.toString(),resolved,-1));
                }else if(attrs.isRegularFile()){
                    var identity=NextflowFiles.identity(path,limits.fileBytes(),check);bytes[0]=Math.addExact(bytes[0],identity.pin().bytes());if(bytes[0]>limits.bytes())throw new IOException("Complete Nextflow capture byte capacity exceeded");
                    store(objects,path,identity.pin(),limits.fileBytes(),check);if(!identity.equals(NextflowFiles.identity(path,limits.fileBytes(),check)))throw new IOException("Declared source changed during complete capture: "+path);
                    result.put(name,new Item("file",identity,null,null,identity.mode()));
                }else if(working&&path.getFileSystem().supportedFileAttributeViews().contains("unix")){
                    int bits=((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue(),kind=bits&0170000;String type=kind==0010000?"fifo":kind==0140000?"socket":null;
                    if(type==null)throw new IOException("Unsupported workspace special object: "+path);
                    long links=((Number)Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue();var identity=new NextflowFiles.Identity(null,String.valueOf(attrs.fileKey()),attrs.lastModifiedTime().toString(),bits&0777,links);
                    result.put(name,new Item(type,identity,null,null,bits&0777));
                }else throw new IOException("Nonregular declared Nextflow input: "+path);
                return FileVisitResult.CONTINUE;
            }
        });
        return Map.copyOf(result);
    }

    static Map<String,Path> materialize(Path owned,Snapshot snapshot,Path destination,Limits limits,Runnable check)throws IOException {
        NextflowFiles.directory(owned);Files.createDirectory(destination);var locations=new TreeMap<String,Path>();var hardlinks=new java.util.HashMap<String,Path>();
        for(Root root:snapshot.roots()){
            Path folder=destination.resolve(root.key());Files.createDirectory(folder);Path target=folder.resolve(root.leaf());locations.put(root.key(),target);
            for(var row:sorted(root.items()).entrySet())if(row.getValue().kind().equals("directory"))Files.createDirectories(resolve(target,row.getKey()));
            for(var row:sorted(root.items()).entrySet())if(row.getValue().kind().equals("file")){
                Path file=resolve(target,row.getKey());Files.createDirectories(file.getParent());Item item=row.getValue();Path object=owned.resolve("objects").resolve(item.original().pin().sha256());
                if(!NextflowFiles.pin(object,limits.fileBytes(),check).equals(item.original().pin()))throw new IOException("Captured object differs before materialization");
                String key=item.original().links()>1&&!item.original().fileKey().equals("null")?item.original().fileKey():null;Path previous=key==null?null:hardlinks.get(key);
                if(previous==null){Files.copy(object,file);if(key!=null)hardlinks.put(key,file);}
                else try{Files.createLink(file,previous);}catch(IOException|UnsupportedOperationException forbidden){throw new NextflowRuntime.Unavailable("Preserving declared internal hardlink aliases is forbidden on the destination filesystem; use a permitted filesystem and NEW session paths: "+file);}
                permissions(file,item.mode());Files.setLastModifiedTime(file,FileTime.from(Instant.parse(item.original().modified())));
                if(!NextflowFiles.pin(file,limits.fileBytes(),check).equals(item.original().pin()))throw new IOException("Complete materialized file differs");
                if(item.mode()>=0&&(item.mode()&0111)!=0&&!Files.isExecutable(file))throw new NextflowRuntime.Unavailable("Captured workflow executable permission is forbidden: "+file);
            }
        }
        var links=new ArrayList<MaterializedLink>();
        for(Root root:snapshot.roots())for(var row:sorted(root.items()).entrySet())if(row.getValue().kind().equals("symlink")){
            Path source=Path.of(row.getValue().resolved()),mapped=null;
            for(Root candidate:snapshot.roots()){Path original=Path.of(candidate.source());if(source.startsWith(original)){mapped=locations.get(candidate.key()).resolve(original.relativize(source));break;}}
            if(mapped==null)throw new IOException("Linked workflow/data target must be included among the declared source roots: "+row.getValue().resolved());
            Path link=resolve(locations.get(root.key()),row.getKey());Files.createDirectories(link.getParent());Path raw=Path.of(row.getValue().target());
            Files.createSymbolicLink(link,raw.isAbsolute()?link.getParent().relativize(mapped):raw);links.add(new MaterializedLink(link,mapped));
        }
        // Preserve relative link spelling/topology whenever it still resolves to
        // the captured target. Absolute/cross-root links need owned replacements.
        for(int pass=0;pass<=links.size();pass++){
            boolean changed=false;
            for(var link:links){check.run();boolean matches;try{matches=link.path().toRealPath().equals(link.target());}catch(IOException unresolved){matches=false;}
                if(!matches){Files.delete(link.path());Files.createSymbolicLink(link.path(),link.path().getParent().relativize(link.target()));changed=true;}}
            if(!changed)break;if(pass==links.size())throw new IOException("Captured symlink topology cannot be materialized faithfully");
        }
        var aliases=new TreeMap<String,Path>();for(var row:snapshot.aliases().entrySet()){String[] parts=row.getValue().split("/",2);Path path=destination.resolve(row.getValue()).normalize();if(!path.startsWith(destination)||parts.length!=2||!Files.exists(path))throw new IOException("Incomplete materialized declared resource: "+row.getKey());aliases.put(row.getKey(),path);}
        return Map.copyOf(aliases);
    }
    private record MaterializedLink(Path path,Path target) { }

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
