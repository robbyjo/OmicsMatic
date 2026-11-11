/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Complete captured file namespaces; portable verification does not authorize producer-host execution. */
final class TomtomBundle {
    private TomtomBundle() { }
    static MemeRuntimeFiles.Limits limits(TomtomRecipe.Request request){return new MemeRuntimeFiles.Limits(request.capacity("file_bytes"),request.capacity("workspace_bytes"),(int)request.capacity("files"));}
    static Map<String,Object> inventory(Path root,TomtomRecipe.Request request,Runnable check)throws IOException {return MemeRuntimeFiles.tree(root,limits(request),check);}
    static Map<String,Object> portable(Map<String,Object> inventory)throws IOException {
        var result=new TreeMap<String,Object>();for(var entry:inventory.entrySet()){var row=NextflowFiles.object(entry.getValue());String kind=NextflowFiles.text(row.get("kind"));var normalized=new TreeMap<String,Object>();normalized.put("kind",kind);
            if(kind.equals("file")){var pin=NextflowFiles.fingerprint(row,1099511627776L);normalized.putAll(MemeRuntimeFiles.pin(pin));}else require(kind.equals("directory"),"Unsupported captured Tomtom namespace node");result.put(entry.getKey(),normalized);}
        return Map.copyOf(result);
    }
    static Map<String,Object> recorded(Object raw)throws IOException {var result=new TreeMap<String,Object>();for(var e:NextflowFiles.object(raw).entrySet()){String name=NextflowFiles.text(e.getKey());if(!name.equals("."))NextflowFiles.member(name);var row=NextflowFiles.object(e.getValue());String kind=NextflowFiles.text(row.get("kind"));NextflowFiles.integer(row.get("mode"),-1,0777);if(kind.equals("file"))NextflowFiles.keys(row,"kind","mode","bytes","sha256");else{require(kind.equals("directory"),"Unsupported recorded Tomtom namespace kind");NextflowFiles.keys(row,"kind","mode");}result.put(name,e.getValue());}return Map.copyOf(result);}
    static void verifyInventory(Path root,Object expected,TomtomRecipe.Request request,Set<String> terminal,Runnable check)throws IOException {
        var actual=new TreeMap<>(inventory(root,request,check));for(String name:terminal)require(actual.remove(name)!=null,"Missing required Tomtom terminal metadata: "+name);
        require(NextflowJson.json(portable(actual)).equals(NextflowJson.json(portable(recorded(expected)))),"Complete captured Tomtom bytes, node kinds or namespace differ");
    }
    static void create(Path root)throws IOException {
        require(root.isAbsolute()&&root.normalize().equals(root)&&root.getParent()!=null&&root.getParent().toRealPath().equals(root.getParent()),"Require a canonical existing parent and NEW Tomtom output directory");
        if(root.getFileSystem().supportedFileAttributeViews().contains("posix"))Files.createDirectory(root,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));else Files.createDirectory(root);
    }
    static void copy(Path source,Path destination,NextflowFiles.Pin expected,Runnable check)throws IOException {
        long count=0;try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){check.run();count=Math.addExact(count,n);require(count<=expected.bytes(),"Original Tomtom evidence grew during capture");out.write(buffer,0,n);}}
        require(count==expected.bytes()&&expected.equals(NextflowFiles.pin(source,expected.bytes(),check))&&expected.equals(NextflowFiles.pin(destination,expected.bytes(),check)),"Original/captured Tomtom complete evidence differs");
    }
    static void copyTree(Path source,Path destination,Map<String,Object> before,MemeRuntimeFiles.Limits limits,Runnable check)throws IOException {
        require(before.equals(MemeRuntimeFiles.tree(source,limits,check)),"Original Tomtom source tree changed before capture");create(destination);
        for(String name:new TreeMap<>(before).keySet()){if(name.equals("."))continue;check.run();String member=NextflowFiles.member(name);var row=NextflowFiles.object(before.get(name));Path target=destination.resolve(member);
            if(row.get("kind").equals("directory"))Files.createDirectory(target);else{require(row.get("kind").equals("file"),"Unsupported source Tomtom tree node");copy(source.resolve(member),target,NextflowFiles.fingerprint(row,limits.fileBytes()),check);}}
        require(before.equals(MemeRuntimeFiles.tree(source,limits,check))&&NextflowJson.json(portable(before)).equals(NextflowJson.json(portable(MemeRuntimeFiles.tree(destination,limits,check)))),"Complete copied Tomtom tree differs or changed during capture");
    }
    static Map<?,?> json(Path path,int maximum)throws IOException {return NextflowFiles.object(NextflowFiles.json(path,maximum));}
    static void write(Path path,Object document)throws IOException {NextflowFiles.writeJson(path,document);}
    static void failure(Path root,String stage,Exception failure){
        try{for(String name:List.of("COMPLETED.json","manifest.json"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("UNADMITTED-"+name));boolean skip=failure instanceof TomtomRuntime.Unavailable||failure instanceof org.jlinalg.sequence.TomtomResults.UnsupportedInput||failure instanceof org.jlinalg.sequence.MemeMotifInputs.UnsupportedInput;
            write(root.resolve("FAILURE.json"),Map.of("status",skip?"SKIP3_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED","stage",stage,"error",failure.toString(),"required_action",TomtomWorkflow.ACTION,"analysis_admitted",false));Files.writeString(root.resolve("WARNINGS.md"),"\n\n# "+(skip?"SKIP3 — ANALYSIS SKIPPED":"FAIL2 — NO RESULT ADMITTED")+"\n\nStage: "+stage+"\n\n"+failure+"\n\n"+TomtomWorkflow.ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(IOException diagnostics){failure.addSuppressed(diagnostics);}
    }
    private static void require(boolean value,String message)throws IOException {MemeRuntimeFiles.require(value,message);}
}
