/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Entire original files and raw-directory namespaces, including empty directories. */
final class DiannInputs {
    private DiannInputs() { }
    private record Node(String kind,Pin pin) { }
    static String runRoot(int index,DiannRequest.Run run){return"inputs/raw/"+String.format(Locale.ROOT,"%04d",index+1)+"/"+run.basename();}
    static Map<String,String> originals(DiannRequest.Spec s){var result=new LinkedHashMap<String,String>();result.put("inputs/reference.fasta",s.reference());for(int i=0;i<s.runs().size();i++)result.put(runRoot(i,s.runs().get(i)),s.runs().get(i).original());return result;}
    static void capture(Path root,DiannRequest.Spec s,Runnable check)throws IOException {
        Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("inputs/raw"));
        var rows=new LinkedHashMap<String,Object>();long bytes=0,nodes=0;
        for(var entry:originals(s).entrySet()){
            check.run();Path source=Path.of(entry.getValue()),target=root.resolve(entry.getKey());require(!root.startsWith(source)&&!source.startsWith(root),"Capture and original input must be separate");
            var before=scan(source,s,check);nodes=Math.addExact(nodes,before.size());for(Node node:before.values())if(node.pin()!=null)bytes=Math.addExact(bytes,node.pin().bytes());
            require(nodes<=s.capacity().inputNodes()&&bytes<=s.capacity().inputBytes(),"Complete original input population exceeds declared node/byte capacities");
            Files.createDirectories(target.getParent());
            for(var node:before.entrySet()){
                check.run();Path from=node.getKey().equals(".")?source:source.resolve(node.getKey()),to=node.getKey().equals(".")?target:target.resolve(node.getKey());
                if(node.getValue().kind().equals("directory"))Files.createDirectory(to);else Files.copy(from,to);
            }
            require(before.equals(scan(source,s,check))&&before.equals(scan(target,s,check)),"Original input changed during complete capture");
            rows.put(entry.getKey(),Map.of("original",entry.getValue(),"tree",description(before)));
        }
        writeJson(root.resolve("source-inputs.json"),Map.of("schema_version",1,"originals",rows,"complete_original_bytes",bytes,"complete_original_nodes",nodes,"original_bytes_unchanged",true));
        admitReferenceAndRaw(root,s,check);
    }
    static void verify(Path root,DiannRequest.Spec s,boolean consultOriginals,Runnable check)throws IOException {
        var saved=object(json(root.resolve("source-inputs.json"),(int)Math.min(1000000000L,s.capacity().outputBytes())));keys(saved,"schema_version","originals","complete_original_bytes","complete_original_nodes","original_bytes_unchanged");integer(saved.get("schema_version"),1,1);
        require(Boolean.TRUE.equals(saved.get("original_bytes_unchanged")),"Incomplete original-byte capture");var rows=object(saved.get("originals"));var expected=originals(s);require(rows.keySet().equals(expected.keySet()),"Original input population differs");long bytes=0,nodes=0;var namespace=new HashSet<String>(Set.of("inputs","inputs/raw"));
        for(var entry:expected.entrySet()){
            var row=object(rows.get(entry.getKey()));keys(row,"original","tree");require(entry.getValue().equals(row.get("original")),"Original path declaration changed");
            var captured=scan(root.resolve(entry.getKey()),s,check);require(NextflowJson.json(description(captured)).equals(NextflowJson.json(row.get("tree"))),"Captured original input tree changed");
            for(String relative:captured.keySet()){
                String name=entry.getKey()+(relative.equals(".")?"":"/"+relative);namespace.add(name);
                while(name.contains("/")){name=name.substring(0,name.lastIndexOf('/'));namespace.add(name);}
            }
            if(consultOriginals)require(captured.equals(scan(Path.of(entry.getValue()),s,check)),"Original input changed before result admission");
            nodes=Math.addExact(nodes,captured.size());for(Node node:captured.values())if(node.pin()!=null)bytes=Math.addExact(bytes,node.pin().bytes());
        }
        var actual=new HashSet<String>();try(var paths=Files.walk(root.resolve("inputs"))){for(Path path:paths.limit((long)s.capacity().inputNodes()+2L*s.runs().size()+4).toList()){check.run();require(!Files.isSymbolicLink(path)&&path.toRealPath().equals(path),"Linked/noncanonical captured input member");actual.add(root.relativize(path).toString().replace('\\','/'));}}
        require(actual.equals(namespace),"Extra/missing captured original file or empty directory");
        require(bytes==integer(saved.get("complete_original_bytes"),1,s.capacity().inputBytes())&&nodes==integer(saved.get("complete_original_nodes"),1,s.capacity().inputNodes()),"Original input totals differ");admitReferenceAndRaw(root,s,check);
    }
    static void admitReferenceAndRaw(Path root,DiannRequest.Spec s,Runnable check)throws IOException {
        DiannReference.read(root.resolve("inputs/reference.fasta"),new DiannReference.Limits(s.capacity().referenceRecords(),s.capacity().referenceResidues(),s.capacity().inputBytes(),1048576),check);
        for(int i=0;i<s.runs().size();i++){
            var run=s.runs().get(i);Path path=root.resolve(runRoot(i,run));
            if(run.format().equals("bruker-tdf-d")){
                if(!Files.isRegularFile(path.resolve("analysis.tdf"),LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(path.resolve("analysis.tdf_bin"),LinkOption.NOFOLLOW_LINKS))throw new DiannRuntime.Unavailable("Bruker TDF input is incomplete or is a different .d format: require original analysis.tdf and analysis.tdf_bin. No file is fabricated; supply the complete supported original or retain SKIP3");
            }else require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Files.size(path)>0,"Empty/nonfile original mass-spectrometry input");
        }
    }
    private static Map<String,Node> scan(Path supplied,DiannRequest.Spec s,Runnable check)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();require(root.toRealPath().equals(root),"Linked/noncanonical original tree root");var result=new TreeMap<String,Node>();long[] total={0};
        Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
            private void add(Path path,boolean directory)throws IOException {
                check.run();require(result.size()<s.capacity().inputNodes()&&!Files.isSymbolicLink(path)&&path.toRealPath().equals(path),"Original tree exceeds capacity or contains a linked/noncanonical member");String name=path.equals(root)?".":root.relativize(path).toString().replace('\\','/');
                if(directory)result.put(name,new Node("directory",null));else {
                    require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Original input tree contains an unsupported special node");Pin value=pin(path,s.capacity().inputBytes(),check);total[0]=Math.addExact(total[0],value.bytes());require(total[0]<=s.capacity().inputBytes(),"Complete original tree exceeds input byte capacity");result.put(name,new Node("file",value));
                }
            }
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attributes)throws IOException{add(dir,true);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException{add(file,false);return FileVisitResult.CONTINUE;}
        });return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> description(Map<String,Node> tree){var result=new TreeMap<String,Object>();tree.forEach((name,node)->result.put(name,node.pin()==null?Map.of("kind","directory"):Map.of("kind","file","pin",node.pin())));return result;}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}
