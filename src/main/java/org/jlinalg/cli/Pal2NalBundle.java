/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** New owned native bundles and complete portable byte/semantic verification. */
final class Pal2NalBundle {
    private Pal2NalBundle() { }
    static final String TOOL="PAL2NAL-native",SCOPE="complete-codon-backtranslation";
    static final long RESERVE=65536;
    record Pin(long bytes,String sha256) { }
    record Inventory(Map<String,Pin> files,Set<String> directories,long bytes,int members) {
        Inventory {files=Map.copyOf(files);directories=Set.copyOf(directories);}
    }
    @FunctionalInterface interface Text {void emit(Writer writer)throws IOException;}
    static void create(Path root)throws IOException {
        if(root.getParent()==null||!root.isAbsolute()||!root.normalize().equals(root)||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("PAL2NAL output needs a canonical existing parent and a NEW directory");
        Files.createDirectory(root);
        if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
    static Pin pin(Path path,long maximum,Runnable check)throws IOException {var identity=IqtreeFiles.identity(path,Math.max(1,maximum),check);return new Pin(identity.bytes(),identity.sha256());}
    static Inventory inventory(Path root,Pal2NalRecipe.Limits limits,Runnable check)throws IOException {
        var files=new TreeMap<String,Pin>();var dirs=new TreeSet<String>();long[] bytes={0};int[] members={0};
        if(!root.toRealPath().equals(root))throw new IOException("Linked PAL2NAL bundle root");
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            private void member()throws IOException{check.run();if(++members[0]>limits.bundleFiles())throw new IOException("Complete PAL2NAL bundle member capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{member();if(!path.toRealPath().equals(path))throw new IOException("Linked PAL2NAL directory");dirs.add(relative(root,path));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{member();if(!attrs.isRegularFile())throw new IOException("Nonregular/linked PAL2NAL member");bytes[0]=Math.addExact(bytes[0],attrs.size());if(bytes[0]>limits.bundleBytes())throw new IOException("Complete PAL2NAL bundle byte capacity exceeded");files.put(relative(root,path),pin(path,limits.bundleBytes(),check));return FileVisitResult.CONTINUE;}
        });return new Inventory(files,dirs,bytes[0],members[0]);
    }
    static String relative(Path root,Path path){return root.relativize(path).toString().replace('\\','/');}
    static void text(Path root,String name,Pal2NalRecipe.Limits limits,Text generator,Runnable check)throws IOException {
        Inventory used=inventory(root,limits,check);
        if(used.members()+8>limits.bundleFiles())throw new IOException("PAL2NAL metadata/member reserve exceeded");
        long maximum=limits.bundleBytes()-used.bytes()-RESERVE;if(maximum<0)throw new IOException("PAL2NAL metadata byte reserve exceeded");
        Path target=root.resolve(name);if(!target.normalize().startsWith(root)||target.getParent()==null||!Files.isDirectory(target.getParent()))throw new IOException("Invalid owned PAL2NAL output name");
        try(var raw=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW);var bounded=new Bounded(raw,maximum);var writer=new OutputStreamWriter(bounded,StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){generator.emit(writer);check.run();}
    }
    static void json(Path root,String name,Pal2NalRecipe.Limits limits,Object value,Runnable check)throws IOException{text(root,name,limits,w->w.write(Pal2NalJson.json(value)+"\n"),check);}
    static Pin digest(Text generator)throws IOException {
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
        var count=new Bounded(OutputStream.nullOutputStream(),Long.MAX_VALUE);
        try(var writer=new OutputStreamWriter(new DigestOutputStream(count,digest),StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){generator.emit(writer);}
        return new Pin(count.count,HexFormat.of().formatHex(digest.digest()));
    }
    static void complete(Path root,Pal2NalRecipe.Limits limits,Runnable check)throws IOException {
        Inventory inventory=inventory(root,limits,check);
        var manifest=Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"status","audited-provisional","limits",limits,"files",inventory.files(),"directories",inventory.directories());
        json(root,"manifest.json",limits,manifest,check);Pin pin=pin(root.resolve("manifest.json"),16777216,check);
        json(root,"COMPLETION-PROVISIONAL.json",limits,Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"manifest_sha256",pin.sha256(),"scientific_result_admitted",true,"original_tool_executed",false,"biological_accuracy_certified",false,"whole_paper_reproduced",false),check);
        verify(root,"COMPLETION-PROVISIONAL.json",check);
        Files.createLink(root.resolve("COMPLETED.json"),root.resolve("COMPLETION-PROVISIONAL.json"));Files.delete(root.resolve("COMPLETION-PROVISIONAL.json"));verify(root,"COMPLETED.json",check);
    }
    static Pin verify(Path supplied,String completionName,Runnable check)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();if(!root.toRealPath().equals(root))throw new IOException("PAL2NAL needs a canonical real bundle");
        Pin markerPin=pin(root.resolve(completionName),262144,check);
        Map<?,?> marker=Pal2NalJson.object(root.resolve(completionName),262144),manifest=Pal2NalJson.object(root.resolve("manifest.json"),16777216);
        Pal2NalRecipe.keys(marker,Set.of("schema_version","tool","scope","manifest_sha256","scientific_result_admitted","original_tool_executed","biological_accuracy_certified","whole_paper_reproduced"),Set.of());
        Pal2NalRecipe.keys(manifest,Set.of("schema_version","tool","scope","status","limits","files","directories"),Set.of());
        for(Map<?,?> map:List.of(marker,manifest))if(!Long.valueOf(1).equals(map.get("schema_version"))||!TOOL.equals(map.get("tool"))||!SCOPE.equals(map.get("scope")))throw new IOException("PAL2NAL completion schema/tool/scope mismatch");
        if(!Boolean.TRUE.equals(marker.get("scientific_result_admitted"))||!Boolean.FALSE.equals(marker.get("original_tool_executed"))||!Boolean.FALSE.equals(marker.get("biological_accuracy_certified"))||!Boolean.FALSE.equals(marker.get("whole_paper_reproduced"))||!"audited-provisional".equals(manifest.get("status")))throw new IOException("No admitted scoped native PAL2NAL completion");
        Pin manifestPin=pin(root.resolve("manifest.json"),16777216,check);if(!manifestPin.sha256().equals(marker.get("manifest_sha256")))throw new IOException("PAL2NAL manifest/marker hash mismatch");
        // Encoded capacity-record field names are native Java names, not arbitrary types.
        Map<?,?> lm=Pal2NalRecipe.object(manifest.get("limits"));Pal2NalRecipe.keys(lm,Set.of("inputBytes","parser","engine","bundleBytes","bundleFiles"),Set.of());
        var limits=new Pal2NalRecipe.Limits(Pal2NalRecipe.integer(lm.get("inputBytes"),1,1073741824L),Pal2NalRecipe.record(org.jlinalg.sequence.MafftSequences.Limits.class,Pal2NalRecipe.object(lm.get("parser"))),Pal2NalRecipe.record(org.jlinalg.sequence.Pal2Nal.Limits.class,Pal2NalRecipe.object(lm.get("engine"))),Pal2NalRecipe.integer(lm.get("bundleBytes"),1048576,1099511627776L),(int)Pal2NalRecipe.integer(lm.get("bundleFiles"),32,100000));
        Inventory before=inventory(root,limits,check);var expected=Pal2NalRecipe.object(manifest.get("files"));var actual=new TreeMap<>(before.files());actual.remove(completionName);actual.remove("manifest.json");
        if(!actual.keySet().equals(expected.keySet()))throw new IOException("Missing or extra PAL2NAL bundle files");
        for(var entry:actual.entrySet()){Map<?,?> row=Pal2NalRecipe.object(expected.get(entry.getKey()));Pal2NalRecipe.keys(row,Set.of("bytes","sha256"),Set.of());if(entry.getValue().bytes()!=Pal2NalRecipe.integer(row.get("bytes"),0,Long.MAX_VALUE)||!entry.getValue().sha256().equals(row.get("sha256")))throw new IOException("Changed PAL2NAL file: "+entry.getKey());}
        if(!(manifest.get("directories") instanceof List<?> dirs)||dirs.size()!=before.directories().size()||!new HashSet<>(dirs).equals(before.directories())||!before.directories().equals(Set.of("","inputs")))throw new IOException("PAL2NAL directory census differs");
        Pal2NalWorkflow.verify(root,limits,actual,check);
        if(!before.equals(inventory(root,limits,check))||!markerPin.equals(pin(root.resolve(completionName),262144,check))||!manifestPin.equals(pin(root.resolve("manifest.json"),16777216,check)))throw new IOException("PAL2NAL bundle changed during complete native recomputation");
        return manifestPin;
    }
    private static final class Bounded extends OutputStream {
        private final OutputStream delegate;private final long maximum;long count;
        Bounded(OutputStream delegate,long maximum){this.delegate=delegate;this.maximum=maximum;}
        @Override public void write(int value)throws IOException{if(count==maximum)throw new IOException("PAL2NAL output byte capacity exceeded");delegate.write(value);count++;}
        @Override public void write(byte[] bytes,int offset,int length)throws IOException{if(length>maximum-count)throw new IOException("PAL2NAL output byte capacity exceeded");delegate.write(bytes,offset,length);count+=length;}
        @Override public void flush()throws IOException{delegate.flush();}
    }
}
