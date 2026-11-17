/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Complete literal software/cache trees. Payload links are data, never host paths to follow or rewrite. */
final class SnakemakeOriginalSoftwareCapture {
    static final String SCOPE="Complete regular-file bytes, member census, literal symlink targets and POSIX object metadata for declared software/cache roots. No links/devices are followed/opened. This does not seal arbitrary external dependencies loaded by trusted workflow code, relocate an installed Conda prefix, certify an author environment or admit analysis.";
    record Node(String kind,int mode,Pin content,String target,String fileKey,String modified,long links,long device) { }
    record Tree(String source,Map<String,Node> nodes) {Tree {nodes=Map.copyOf(nodes);}}
    record Snapshot(int schemaVersion,String scope,Map<String,Tree> roots,long bytes,int members) {Snapshot {roots=Map.copyOf(roots);}}
    private SnakemakeOriginalSoftwareCapture() { }
    static Snapshot capture(Path owned,Map<String,Path> roots,SnakemakeOriginalCapture.Limits limits,Runnable check)throws IOException {
        directory(owned);directory(owned.resolve("objects"));Snapshot first=scan(owned.resolve("objects"),roots,limits,check);Snapshot second=scan(null,roots,limits,check);
        if(!first.equals(second))throw new IOException("Declared software/cache tree changed during complete capture; preserve both records and retry NEW output");return first;
    }
    static void verifyLive(Snapshot saved,SnakemakeOriginalCapture.Limits limits,Runnable check)throws IOException {
        var roots=new TreeMap<String,Path>();for(var row:saved.roots().entrySet())roots.put(row.getKey(),Path.of(row.getValue().source()));
        if(!saved.equals(scan(null,roots,limits,check)))throw new IOException("Declared original software/cache bytes, links, metadata or complete member census changed");
    }
    private static Snapshot scan(Path objects,Map<String,Path> selected,SnakemakeOriginalCapture.Limits limits,Runnable check)throws IOException {
        if(!System.getProperty("os.name").equals("Linux"))throw new SnakemakeOriginalRuntime.Unavailable("Complete managed software capture requires permitted native Linux metadata; skip dependent analysis on this host");
        long[] size={0};int[] count={0};var roots=new TreeMap<String,Tree>();
        for(var root:new TreeMap<>(selected).entrySet()){
            if(!root.getKey().matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw new IOException("Invalid declared software/cache root key");Path source=directory(root.getValue());if(objects!=null&&(source.startsWith(objects)||objects.startsWith(source)))throw new IOException("Software roots and their content store must be disjoint");var nodes=new TreeMap<String,Node>();
            Files.walkFileTree(source,new SimpleFileVisitor<>(){
                private void add(Path path,BasicFileAttributes attrs)throws IOException {
                    check.run();if(++count[0]>limits.files())throw new IOException("Complete software/cache member capacity exceeded");String name=source.equals(path)?"":source.relativize(path).toString();if(!name.isEmpty())member(name);
                    int raw=((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue(),mode=raw&07777;long links=((Number)Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue(),device=0;String kind,target=null;Pin content=null;
                    if(attrs.isDirectory())kind="directory";
                    else if(attrs.isSymbolicLink()){kind="symlink";target=Files.readSymbolicLink(path).toString();size[0]=Math.addExact(size[0],target.getBytes(StandardCharsets.UTF_8).length);}
                    else if(attrs.isRegularFile()){
                        kind="file";Identity before=identity(path,limits.fileBytes(),check);content=before.pin();size[0]=Math.addExact(size[0],content.bytes());if(size[0]>limits.bytes())throw new IOException("Complete software/cache byte capacity exceeded");
                        if(objects!=null)SnakemakeOriginalCapture.store(objects,path,content,limits.fileBytes(),check);
                        if(!before.equals(identity(path,limits.fileBytes(),check))||mode!=(((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&07777))throw new IOException("Software file changed during complete capture");
                    }else {kind=switch(raw&0170000){case 0010000->"fifo";case 0140000->"socket";case 0020000->"character-device";case 0060000->"block-device";default->throw new IOException("Unknown software/cache object kind");};if(kind.endsWith("-device"))device=((Number)Files.getAttribute(path,"unix:rdev",LinkOption.NOFOLLOW_LINKS)).longValue();}
                    if(size[0]>limits.bytes())throw new IOException("Complete software/cache byte capacity exceeded");nodes.put(name,new Node(kind,mode,content,target,String.valueOf(attrs.fileKey()),attrs.lastModifiedTime().toString(),links,device));
                }
                @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{add(path,attrs);return FileVisitResult.CONTINUE;}
                @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{add(path,attrs);return FileVisitResult.CONTINUE;}
            });
            roots.put(root.getKey(),new Tree(source.toString(),nodes));
        }
        if(roots.isEmpty())throw new IOException("Declare complete managed software/cache roots");return new Snapshot(1,SCOPE,roots,size[0],count[0]);
    }
    static Snapshot decode(Object value,SnakemakeOriginalCapture.Limits limits)throws IOException {
        Map<?,?> raw=object(value);keys(raw,"schemaVersion","scope","roots","bytes","members");integer(raw.get("schemaVersion"),1,1);if(!SCOPE.equals(raw.get("scope")))throw new IOException("Software capture scope/format differs");var roots=new TreeMap<String,Tree>();var pins=new TreeMap<String,Pin>();long size=0;int count=0;
        for(var row:object(raw.get("roots")).entrySet()){
            String key=text(row.getKey());if(!key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw new IOException("Invalid recorded software root key");Map<?,?> tree=object(row.getValue());keys(tree,"source","nodes");String source=SnakemakeOriginalRecorded.absolute(text(tree.get("source")));var nodes=new TreeMap<String,Node>();
            for(var item:object(tree.get("nodes")).entrySet()){
                String name=text(item.getKey());if(!name.isEmpty())member(name);if(++count>limits.files())throw new IOException("Recorded complete software member capacity exceeded");Map<?,?> node=object(item.getValue());keys(node,"kind","mode","content","target","fileKey","modified","links","device");String kind=text(node.get("kind"));int mode=(int)integer(node.get("mode"),0,07777);long links=integer(node.get("links"),1,Long.MAX_VALUE),device=integer(node.get("device"),0,Long.MAX_VALUE);Pin content=null;String target=null;
                if(kind.equals("file")){keys(object(node.get("content")),"bytes","sha256");content=fingerprint(node.get("content"),limits.fileBytes());Pin prior=pins.putIfAbsent(content.sha256(),content);if(prior!=null&&!prior.equals(content))throw new IOException("A shared software blob has contradictory byte counts");size=Math.addExact(size,content.bytes());}
                else if(kind.equals("symlink")){target=text(node.get("target"));if(target.isEmpty()||target.indexOf('\0')>=0)throw new IOException("Invalid literal software symlink text");size=Math.addExact(size,target.getBytes(StandardCharsets.UTF_8).length);}
                else if(!Set.of("directory","fifo","socket","character-device","block-device").contains(kind))throw new IOException("Unknown recorded software/cache kind");
                if(!kind.equals("file")&&node.get("content")!=null||!kind.equals("symlink")&&node.get("target")!=null||!kind.endsWith("-device")&&device!=0)throw new IOException("Contradictory software/cache object metadata");
                if(size>limits.bytes())throw new IOException("Recorded complete software byte capacity exceeded");nodes.put(name,new Node(kind,mode,content,target,text(node.get("fileKey")),text(node.get("modified")),links,device));
            }
            if(!nodes.containsKey("")||!nodes.get("").kind().equals("directory"))throw new IOException("Missing complete software root directory");
            for(String name:nodes.keySet())if(!name.isEmpty()){int slash=name.lastIndexOf('/');Node parent=nodes.get(slash<0?"":name.substring(0,slash));if(parent==null||!parent.kind().equals("directory"))throw new IOException("Incomplete recorded software parent census");}
            roots.put(key,new Tree(source,nodes));
        }
        if(roots.isEmpty()||integer(raw.get("bytes"),0,limits.bytes())!=size||integer(raw.get("members"),1,limits.files())!=count)throw new IOException("Recorded software/cache totals or roots differ");return new Snapshot(1,SCOPE,roots,size,count);
    }
    static void verifyObjects(Path owned,Snapshot snapshot,SnakemakeOriginalCapture.Limits limits,Runnable check)throws IOException {
        decode(object(SnakemakeOriginalParameters.parse(SnakemakeOriginalJson.json(snapshot),67_108_864,10_000_000)),limits);var verified=new java.util.HashSet<String>();
        for(Tree tree:snapshot.roots().values())for(Node node:tree.nodes().values())if(node.content()!=null&&verified.add(node.content().sha256()))if(!pin(owned.resolve("objects").resolve(node.content().sha256()),limits.fileBytes(),check).equals(node.content()))throw new IOException("Captured software payload bytes changed");
    }
}
