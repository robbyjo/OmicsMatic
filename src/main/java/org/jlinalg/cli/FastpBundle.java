/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Whole byte census and portable recomputation of the declared full-population audit scope. */
final class FastpBundle {
    private FastpBundle() { }
    static final String TOOL="fastp-original",SCOPE="complete-population-report-and-conservation-envelope";
    record Inventory(Map<String,FastpFiles.Pin> files,Set<String> directories) {
        Inventory {files=Map.copyOf(files);directories=Set.copyOf(directories);}
    }
    static void create(Path root)throws IOException {
        if(root.getParent()==null||!root.isAbsolute()||!root.normalize().equals(root)||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("fastp requires a canonical existing parent and NEW output directory");Files.createDirectory(root);
        if(root.getFileSystem().supportedFileAttributeViews().contains("posix"))Files.setPosixFilePermissions(root,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
    static String relative(Path root,Path path){return root.relativize(path).toString().replace('\\','/');}
    static Inventory inventory(Path root,FastpOriginalStage.Limits limits,Runnable check)throws IOException {
        FastpOriginalStage.budget(root,limits,check);var files=new TreeMap<String,FastpFiles.Pin>();var dirs=new TreeSet<String>();
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {check.run();if(!path.toRealPath().equals(path))throw new IOException("Linked fastp bundle directory");dirs.add(relative(root,path));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {check.run();if(!attrs.isRegularFile())throw new IOException("Nonregular/linked fastp bundle file");files.put(relative(root,path),FastpFiles.pin(path,limits.bundleBytes(),check));return FileVisitResult.CONTINUE;}
        });return new Inventory(files,dirs);
    }
    static void complete(Path root,FastpRecipe.Spec replay,Runnable check)throws IOException {
        var limits=replay.limits().stage();Inventory used=inventory(root,limits,check);
        FastpFiles.writeJson(root.resolve("manifest.json"),Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"status","audited-provisional","limits",replay.limits(),"files",used.files(),"directories",new TreeSet<>(used.directories())));
        var manifest=FastpFiles.pin(root.resolve("manifest.json"),16_777_216,check);
        FastpFiles.writeJson(root.resolve("COMPLETION-PROVISIONAL.json"),Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"manifest",manifest,"scoped_result_admitted",true,"original_tool_executed",true,"biological_accuracy_certified",false,"whole_paper_reproduced",false));
        boolean admitted=false;
        try{verify(root,"COMPLETION-PROVISIONAL.json",check);Files.move(root.resolve("COMPLETION-PROVISIONAL.json"),root.resolve("COMPLETED.json"));admitted=true;inventory(root,limits,check);}
        catch(IOException|RuntimeException failure){if(admitted)try{Files.delete(root.resolve("COMPLETED.json"));}catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;}
    }
    static FastpFiles.Pin verify(Path supplied,String completion,Runnable check)throws IOException {
        if(!Set.of("COMPLETED.json","COMPLETION-PROVISIONAL.json").contains(completion))throw new IOException("Unknown fastp completion name");Path root=supplied.toAbsolutePath().normalize();if(!root.toRealPath().equals(root))throw new IOException("Linked fastp bundle root");
        var markerPin=FastpFiles.pin(root.resolve(completion),262144,check);Map<?,?> marker=FastpChoices.map(FastpFiles.json(root.resolve(completion),262144),Set.of("schema_version","tool","scope","manifest","scoped_result_admitted","original_tool_executed","biological_accuracy_certified","whole_paper_reproduced"));
        Map<?,?> manifest=FastpChoices.map(FastpFiles.json(root.resolve("manifest.json"),16_777_216),Set.of("schema_version","tool","scope","status","limits","files","directories"));
        for(Map<?,?> m:List.of(marker,manifest))if(FastpRuntime.integer(m.get("schema_version"),1,1)!=1||!TOOL.equals(m.get("tool"))||!SCOPE.equals(m.get("scope")))throw new IOException("fastp bundle schema/tool/scope differs");
        if(!Boolean.TRUE.equals(marker.get("scoped_result_admitted"))||!Boolean.TRUE.equals(marker.get("original_tool_executed"))||!Boolean.FALSE.equals(marker.get("biological_accuracy_certified"))||!Boolean.FALSE.equals(marker.get("whole_paper_reproduced"))||!"audited-provisional".equals(manifest.get("status")))throw new IOException("No admitted scoped original fastp result");
        var manifestPin=FastpFiles.pin(root.resolve("manifest.json"),16_777_216,check);if(!manifestPin.equals(FastpRuntime.fingerprint(FastpRuntime.object(marker.get("manifest")))))throw new IOException("fastp manifest/marker hash differs");
        var limits=FastpRecipe.limits(manifest.get("limits"));Inventory before=inventory(root,limits.stage(),check);var actual=new TreeMap<>(before.files());actual.remove(completion);actual.remove("manifest.json");Map<?,?> declared=FastpRuntime.object(manifest.get("files"));
        if(!actual.keySet().equals(declared.keySet()))throw new IOException("Missing or extra fastp bundle files");for(var entry:actual.entrySet())if(!entry.getValue().equals(FastpOutputAudit.capturedPin(FastpRuntime.object(declared.get(entry.getKey())),limits.stage().bundleBytes())))throw new IOException("Changed fastp bundle member: "+entry.getKey());
        List<?> dirs=FastpMetrics.array(manifest.get("directories"));if(dirs.size()!=before.directories().size()||!new HashSet<>(dirs).equals(before.directories()))throw new IOException("Changed fastp bundle directory census");
        FastpWorkflow.verify(root,limits,actual.keySet(),before.directories(),check);
        if(!before.equals(inventory(root,limits.stage(),check))||!markerPin.equals(FastpFiles.pin(root.resolve(completion),262144,check))||!manifestPin.equals(FastpFiles.pin(root.resolve("manifest.json"),16_777_216,check)))throw new IOException("fastp bundle changed during portable full-population recomputation");return manifestPin;
    }
    static Set<String> directoryCensus(Set<String> files){var dirs=new HashSet<String>();dirs.add("");for(String file:files){int end=file.lastIndexOf('/');while(end>=0){dirs.add(file.substring(0,end));end=file.lastIndexOf('/',end-1);}}return Set.copyOf(dirs);}
    static void removeSuccessfulScratch(Path scratch)throws IOException {
        Path checked=scratch.toRealPath();if(!checked.equals(scratch)||!scratch.getFileName().toString().startsWith("omicsmatic-fastp-verify-"))throw new IOException("Refuse cleanup outside the owned successful fastp verification scratch");
        Files.walkFileTree(scratch,new SimpleFileVisitor<>(){
            private void contained(Path path)throws IOException {if(!path.toAbsolutePath().normalize().startsWith(checked)||Files.isSymbolicLink(path))throw new IOException("Linked/escaped fastp verification scratch;preserve it");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {contained(path);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {contained(path);Files.delete(path);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path path,IOException failure)throws IOException {if(failure!=null)throw failure;contained(path);Files.delete(path);return FileVisitResult.CONTINUE;}
        });
    }
}
