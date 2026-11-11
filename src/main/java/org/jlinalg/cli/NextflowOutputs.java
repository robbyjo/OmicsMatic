/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete declared publication tree; contained original links are dereferenced into immutable blobs. */
final class NextflowOutputs {
    private NextflowOutputs() { }
    record Value(String kind,Pin pin,String resolved,int mode) { }
    record Family(Map<String,Value> members,long bytes,int files) {Family {members=Map.copyOf(members);}}
    static Family capture(Path session,Path published,List<NextflowRecipe.Output> expected,NextflowCapture.Limits limits,Runnable check)throws IOException {
        directory(session);directory(published);var rows=new TreeMap<String,Value>();long[] bytes={0};int[] files={0},count={0};
        Files.walkFileTree(published,EnumSet.of(FileVisitOption.FOLLOW_LINKS),1000,new SimpleFileVisitor<>(){
            private String name(Path path)throws IOException {String name=published.relativize(path).toString();if(!name.isEmpty())member(name);return name;}
            private Path admit(Path path)throws IOException {check.run();if(++count[0]>limits.files())throw new IOException("Complete Nextflow output member capacity exceeded");Path real=path.toRealPath();if(!real.startsWith(session))throw new IOException("Published Nextflow output resolves outside the captured session: "+path);return real;}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {Path real=admit(path);rows.put(name(path),new Value("directory",null,real.toString(),mode(real)));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {
                Path real=admit(path);if(!attrs.isRegularFile())throw new IOException("A published output is not a complete regular file: "+path);
                var identity=identity(real,limits.fileBytes(),check);bytes[0]=Math.addExact(bytes[0],identity.pin().bytes());if(bytes[0]>limits.bytes())throw new IOException("Complete Nextflow output byte capacity exceeded");
                NextflowCapture.store(session.resolve("objects"),real,identity.pin(),limits.fileBytes(),check);if(!identity.equals(identity(real,limits.fileBytes(),check)))throw new IOException("Published output changed during capture");
                rows.put(name(path),new Value("file",identity.pin(),real.toString(),identity.mode()));files[0]++;return FileVisitResult.CONTINUE;
            }
        });
        var family=new Family(rows,bytes[0],files[0]);validate(family,expected,limits);return family;
    }
    static Family decode(Object value,List<NextflowRecipe.Output> expected,NextflowCapture.Limits limits)throws IOException {
        Map<?,?> raw=object(value);keys(raw,"members","bytes","files");var rows=new TreeMap<String,Value>();long bytes=0;int files=0;
        for(var item:object(raw.get("members")).entrySet()){
            String name=text(item.getKey());if(!name.isEmpty())member(name);Map<?,?> row=object(item.getValue());keys(row,"kind","pin","resolved","mode");String kind=text(row.get("kind"));Pin pin=null;
            if(kind.equals("file")){pin=fingerprint(row.get("pin"),limits.fileBytes());bytes=Math.addExact(bytes,pin.bytes());files++;}else if(!kind.equals("directory")||row.get("pin")!=null)throw new IOException("Invalid recorded output kind/pin");
            rows.put(name,new Value(kind,pin,text(row.get("resolved")),(int)integer(row.get("mode"),-1,0777)));
        }
        if(bytes!=integer(raw.get("bytes"),0,limits.bytes())||files!=integer(raw.get("files"),0,limits.files()))throw new IOException("Complete output family totals disagree");
        var result=new Family(rows,bytes,files);validate(result,expected,limits);return result;
    }
    static void validate(Family family,List<NextflowRecipe.Output> expected,NextflowCapture.Limits limits)throws IOException {
        if(family.members().size()>limits.files()||family.bytes()>limits.bytes()||!family.members().containsKey("")||!family.members().get("").kind().equals("directory"))throw new IOException("Incomplete/oversized output publication family");
        for(String name:family.members().keySet())if(!name.isEmpty()){int slash=name.lastIndexOf('/');String parent=slash<0?"":name.substring(0,slash);Value directory=family.members().get(parent);if(directory==null||!directory.kind().equals("directory"))throw new IOException("Incomplete output directory census");}
        for(var output:expected){
            Value value=family.members().get(output.path());if(value==null||!output.kind().equals(value.kind()))throw new IOException("Missing/wrong-kind declared Nextflow output: "+output.path());
            if(!output.allowEmpty()){
                if(value.kind().equals("file")&&value.pin().bytes()==0)throw new IOException("Declared Nextflow output is empty: "+output.path());
                if(value.kind().equals("directory")&&family.members().entrySet().stream().noneMatch(row->row.getKey().startsWith(output.path()+"/")&&row.getValue().kind().equals("file")))throw new IOException("Declared Nextflow output directory contains no regular files: "+output.path());
            }
        }
    }
}
