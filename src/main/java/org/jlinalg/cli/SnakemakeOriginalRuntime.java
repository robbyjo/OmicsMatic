/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Exact selected original software, complete adoption metadata and host bindings. */
final class SnakemakeOriginalRuntime {
    static final String PROFILE="linux-x86_64-snakemake-9.27.1";
    static final String RESOURCE="/org/jlinalg/omics/snakemake-original-v1/";
    static final String OFFICIAL="c024be4c2d2d814dfdaf78f714dc9b047ea50d612fc98da936d74e2f77d9b67b";
    static final String LOCK="ebc39cd3f2d04a7ae915554e8291bd0e84fe7fe6b9f0edf17dc4bb137c44080a";
    static final List<String> HELPERS=List.of("driver.py","snakemake_logger_plugin_omicsmatic/__init__.py");
    static final Map<String,String> VERSIONS=Map.of("snakemake","9.27.1","snakemake-interface-common","1.23.0","snakemake-interface-logger-plugins","2.1.0","snakemake-interface-executor-plugins","9.4.0","PyYAML","6.0.3","psutil","7.2.2");
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message) { super(message); }
    }
    record Context(Path home,Path environment,Path python,Map<String,Pin> metadata,Pin manifest,int entries) {
        Context { metadata=Map.copyOf(metadata); }
        void verify(Runnable check) throws IOException { if(!equals(open(home,check)))throw new IOException("Adopted Snakemake software/host/metadata changed during execution; no result admitted"); }
    }
    private SnakemakeOriginalRuntime() { }
    static byte[] resource(String name) throws IOException {
        if(!HELPERS.contains(name)&&!name.equals("official-package.json"))throw new IOException("Unknown bundled Snakemake helper");
        try(var input=SnakemakeOriginalRuntime.class.getResourceAsStream(RESOURCE+name)) {
            if(input==null)throw new IOException("Bundled Snakemake helper unavailable: "+name);byte[] raw=input.readNBytes(1_048_577);if(raw.length>1_048_576)throw new IOException("Oversized bundled helper");return raw;
        }
    }
    static Map<String,String> helperPins() throws IOException { var pins=new TreeMap<String,String>();for(String name:HELPERS)pins.put(name,sha(resource(name)));return Map.copyOf(pins); }
    static Context open(Path supplied,Runnable check) throws IOException {
        check.run();
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original Snakemake execution requires permitted Linux x86-64 Java; on Windows use Linux Java inside WSL. Native benchmark aggregation remains a separate command.");
        Path home=supplied.toAbsolutePath().normalize();
        if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(home.resolve("READY.json"),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Completed exact Snakemake runtime adoption missing; run permitted setup/adoption into NEW paths or skip dependent execution");
        directory(home);Map<?,?> ready=object(json(home.resolve("READY.json"),4_194_304));
        if(integer(ready.get("schema_version"),1,100)!=2)throw new Unavailable("Runtime adoption schema 2 is required; preserve the older record and perform permitted adoption into NEW paths");
        keys(ready,"status","schema_version","profile","environment","python","kernel","architecture","versions","entries","external_paths","logical_environment_file_bytes","runtime_manifest","health_sha256","official_package_sha256","elf_dependencies_sha256","driver_controls_sha256","lock_sha256","adopter_sha256","project_helpers","metadata","project_java_adapter_qualified","user_analysis_admitted","author_environment_reproduced","whole_goal_complete");
        if(!PROFILE.equals(ready.get("profile"))||!"READY_ADOPTED_SELECTED_SNAKEMAKE_RUNTIME_NOT_USER_ANALYSIS".equals(ready.get("status"))||!Boolean.FALSE.equals(ready.get("user_analysis_admitted")))throw new IOException("Snakemake adoption profile/status differs");
        if(!"x86_64".equals(ready.get("architecture"))||!VERSIONS.equals(stringMap(ready.get("versions"))))throw new IOException("Selected Snakemake architecture/version declarations differ");
        for(String field:List.of("project_java_adapter_qualified","author_environment_reproduced","whole_goal_complete"))if(!Boolean.FALSE.equals(ready.get(field)))throw new IOException("Runtime adoption must not claim workflow/paper/goal qualification: "+field);
        if(!System.getProperty("os.version").equals(ready.get("kernel")))throw new Unavailable("Adopted Snakemake host kernel differs; perform a permitted fresh adoption on this host or skip execution");
        var expectedNames=Set.of("WARNINGS.md","OFFICIAL-PACKAGE-FILES.json","DRIVER-CONTROLS.json","HEALTH-PROCESS.json","health.stdout","health.stderr","ELF-DEPENDENCIES.json","runtime-files.tsv","pixi.lock");
        Map<?,?> declared=object(ready.get("metadata"));if(!declared.keySet().equals(expectedNames))throw new IOException("Incomplete adoption metadata census");
        var actualNames=new HashSet<String>();try(var members=Files.list(home)) { for(Path path:members.toList()) { if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unexpected adoption directory/link/member");actualNames.add(path.getFileName().toString()); } }
        var fullNames=new HashSet<>(expectedNames);fullNames.add("READY.json");if(!actualNames.equals(fullNames))throw new IOException("Adoption metadata has missing or unexpected files");
        var metadata=new TreeMap<String,Pin>();
        for(String name:expectedNames) { Pin expected=fingerprint(declared.get(name),67_108_864);Pin actual=pin(home.resolve(name),67_108_864,check);if(!actual.equals(expected))throw new IOException("Adoption metadata changed: "+name);metadata.put(name,actual); }
        metadata.put("READY.json",pin(home.resolve("READY.json"),4_194_304,check));
        if(!OFFICIAL.equals(sha(resource("official-package.json"))))throw new IOException("Bundled official Snakemake wheel source map differs from its fixed source pin");
        if(!OFFICIAL.equals(metadata.get("OFFICIAL-PACKAGE-FILES.json").sha256()))throw new IOException("Adoption's OFFICIAL-PACKAGE-FILES.json differs from the selected original wheel");
        if(!LOCK.equals(metadata.get("pixi.lock").sha256()))throw new IOException("Adoption's dependency lock differs from the selected resolved environment");
        if(!helperPins().equals(stringMap(ready.get("project_helpers"))))throw new IOException("Adopted project driver/recorder helpers differ from the bundled source; preserve old adoption and qualify new helpers in NEW output");
        for(var field:Map.of("health_sha256","health.stdout","official_package_sha256","OFFICIAL-PACKAGE-FILES.json","elf_dependencies_sha256","ELF-DEPENDENCIES.json","driver_controls_sha256","DRIVER-CONTROLS.json","lock_sha256","pixi.lock").entrySet())if(!metadata.get(field.getValue()).sha256().equals(ready.get(field.getKey())))throw new IOException("Adoption record/metadata binding differs: "+field.getKey());
        Path environment=Path.of(text(ready.get("environment"))),python=Path.of(text(ready.get("python")));directory(environment);
        if(!python.equals(environment.resolve("bin/python"))||!Files.isExecutable(python))throw new Unavailable("Exact adopted Python is missing or execution is forbidden; restore permitted setup or skip dependent analysis");
        Pin manifest=fingerprint(ready.get("runtime_manifest"),67_108_864);if(!manifest.equals(metadata.get("runtime-files.tsv")))throw new IOException("Adopted software manifest binding differs");
        int expectedEntries=(int)integer(ready.get("entries"),1,200000);Payload payload=verifyPayload(home.resolve("runtime-files.tsv"),environment,expectedEntries,check);
        if(payload.logicalBytes()!=integer(ready.get("logical_environment_file_bytes"),0,1_000_000_000_000L)||payload.systemPaths().size()!=integer(ready.get("external_paths"),1,200000))throw new IOException("Complete software manifest totals differ from runtime adoption");
        Map<?,?> official=object(SnakemakeOriginalParameters.parse(utf8(resource("official-package.json")),1_048_576,100000));var expectedPackage=new TreeMap<String,Pin>();
        for(var entry:object(official.get("files")).entrySet())expectedPackage.put(text(entry.getKey()),fingerprint(entry.getValue(),67_108_864));
        if(!payload.packageFiles().equals(expectedPackage))throw new IOException("Complete installed Snakemake package differs from the pinned original wheel");
        Map<?,?> elf=object(json(home.resolve("ELF-DEPENDENCIES.json"),4_194_304));
        if(!new HashSet<>(strings(elf.get("external_paths"))).equals(payload.systemPaths()))throw new IOException("Dynamic-loader dependency paths differ from the complete software manifest");
        for(String helper:List.of("/usr/bin/setsid","/usr/bin/prlimit","/usr/bin/bash","/usr/bin/ldd","/usr/bin/env","/bin/sh"))if(!payload.systemPaths().contains(helper)||!Files.isExecutable(Path.of(helper)))throw new Unavailable("Required permitted system helper unavailable or omitted: "+helper);
        Map<?,?> health=object(json(home.resolve("health.stdout"),1_048_576));
        if(!"PASS_SELECTED_SOURCE_IMPORT_HEALTH_NOT_WORKFLOW_ADMISSION".equals(health.get("status"))||!PROFILE.equals(health.get("profile"))||!VERSIONS.equals(stringMap(health.get("versions")))||!python.toString().equals(health.get("executable")))throw new IOException("Selected runtime import-health record differs");
        return new Context(home,environment,python,metadata,manifest,expectedEntries);
    }
    record Payload(Map<String,Pin> packageFiles,Set<String> systemPaths,long logicalBytes) {
        Payload { packageFiles=Map.copyOf(packageFiles);systemPaths=Set.copyOf(systemPaths); }
    }
    private record Node(String key,String modified,long size,int mode,String kind,String target) { }
    private static Map<String,Node> inventory(Path environment,Runnable check)throws IOException {
        var result=new TreeMap<String,Node>();
        Files.walkFileTree(environment,new SimpleFileVisitor<>() {
            private void add(Path path,BasicFileAttributes attrs)throws IOException {
                check.run();if(result.size()>=200001)throw new IOException("Complete runtime member capacity exceeded");
                String kind=attrs.isSymbolicLink()?"link":attrs.isRegularFile()?"file":attrs.isDirectory()?"directory":"special";
                if(kind.equals("special"))throw new IOException("Unexpected installed software special file: "+path);
                String target=attrs.isSymbolicLink()?Files.readSymbolicLink(path).toString():"-";
                result.put(environment.relativize(path).toString(),new Node(String.valueOf(attrs.fileKey()),attrs.lastModifiedTime().toString(),attrs.size(),mode(path),kind,target));
            }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {add(path,attrs);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {add(path,attrs);return FileVisitResult.CONTINUE;}
        });
        return Map.copyOf(result);
    }
    /** Qualification seam: every row is checked in full; this alone is not a selected-runtime admission. */
    static Payload verifyPayload(Path manifest,Path environment,int expectedEntries,Runnable check) throws IOException {
        if(expectedEntries<1||expectedEntries>200000)throw new IOException("Invalid complete software entry capacity");
        directory(environment);Map<String,Node> before=inventory(environment,check);
        String[] lines=utf8(bytes(manifest,67_108_864)).split("\n",-1);
        if(lines.length!=expectedEntries+2||!lines[0].equals("root\tpath\tkind\tmode\tbytes\ttarget\tresolved\tsha256")||!lines[lines.length-1].isEmpty())throw new IOException("Complete software manifest row/header count differs");
        var expectedEnvironment=new HashSet<String>();var systemPaths=new HashSet<String>();var duplicate=new HashSet<String>();var packageFiles=new TreeMap<String,Pin>();var rows=new ArrayList<String[]>();long logicalBytes=0;
        for(int index=1;index<lines.length-1;index++) {
            check.run();String[] row=lines[index].split("\t",-1);if(row.length!=8||!duplicate.add(row[0]+'\t'+row[1]))throw new IOException("Malformed/duplicate software row");
            if(row[0].equals("env")) { member(row[1]);expectedEnvironment.add(row[1]);if(!row[7].equals("-")) {try{logicalBytes=Math.addExact(logicalBytes,Long.parseLong(row[4]));}catch(NumberFormatException|ArithmeticException invalid){throw new IOException("Invalid total installed software bytes",invalid);} } }
            else if(row[0].equals("system")) { Path path=Path.of(row[1]);if(!path.isAbsolute()||!path.normalize().equals(path))throw new IOException("Unqualified absolute system dependency path");systemPaths.add(row[1]); }
            else throw new IOException("Unknown software root");
            rows.add(row);
            String prefix="lib/python3.12/site-packages/snakemake/";
            if(row[0].equals("env")&&row[1].startsWith(prefix)&&row[2].equals("file")) {try{packageFiles.put(row[1].substring(prefix.length()),new Pin(Long.parseLong(row[4]),row[7]));}catch(IllegalArgumentException invalid){throw new IOException("Invalid original package file pin",invalid);} }
        }
        var actualNames=new HashSet<>(before.keySet());actualNames.remove("");
        if(!actualNames.equals(expectedEnvironment))throw new IOException("Complete adopted environment membership changed; no analysis admitted");
        verifyRows(rows,environment,check);
        if(!before.equals(inventory(environment,check)))throw new IOException("Installed software membership, identity, mode, link or metadata changed during complete runtime verification");
        return new Payload(packageFiles,systemPaths,logicalBytes);
    }
    private static void verifyRows(List<String[]> rows,Path environment,Runnable check)throws IOException {
        var pool=Executors.newFixedThreadPool(8);var completed=new ExecutorCompletionService<Void>(pool);var pending=new HashSet<Future<Void>>();int submitted=0,finished=0;
        Runnable guarded=()->{if(Thread.currentThread().isInterrupted())throw new CancellationException("Complete runtime check interrupted");synchronized(check){check.run();}};
        try {
            while(finished<rows.size()) {
                guarded.run();
                while(submitted<rows.size()&&pending.size()<16) {String[] row=rows.get(submitted++);pending.add(completed.submit(()->{verifyRow(row,environment,guarded);return null;}));}
                Future<Void> result=completed.take();pending.remove(result);result.get();finished++;
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Complete runtime verification interrupted; no analysis admitted",interrupted);}
        catch(ExecutionException failed){Throwable cause=failed.getCause();if(cause instanceof IOException io)throw io;if(cause instanceof RuntimeException runtime)throw runtime;throw new IOException("Complete runtime worker failed",cause);}
        finally {
            for(Future<Void> task:pending)task.cancel(true);pool.shutdownNow();boolean interrupted=Thread.interrupted();
            try {if(!pool.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Runtime verifier workers remain active; no analysis admitted");}
            catch(InterruptedException stopped){interrupted=true;throw new IOException("Interrupted while closing runtime verification workers",stopped);}
            finally {if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    private static void verifyRow(String[] row,Path environment,Runnable check)throws IOException {
            check.run();Path path=row[0].equals("env")?environment.resolve(row[1]):Path.of(row[1]);
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(path))throw new Unavailable("Adopted software dependency unavailable/unreadable: "+path+". Restore the exact permitted setup or skip execution.");
            int mode;long size;try{mode=Integer.parseInt(row[3]);size=Long.parseLong(row[4]);}catch(NumberFormatException invalid){throw new IOException("Invalid installed software size/mode",invalid);}
            if(mode<0||mode>0777||size<0||!path.toRealPath().toString().equals(row[6])||mode(path)!=mode)throw new IOException("Adopted software target/mode changed: "+path);
            switch(row[2]) {
                case "directory" -> { if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||size!=0||!row[5].equals("-")||!row[7].equals("-"))throw new IOException("Software directory kind changed"); }
                case "file","link" -> {
                    if(row[2].equals("link")) { if(!Files.isSymbolicLink(path)||!Files.readSymbolicLink(path).toString().equals(row[5]))throw new IOException("Software link changed: "+path); }
                    else if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!row[5].equals("-"))throw new IOException("Software file kind changed");
                    if(Files.isDirectory(path)) { if(size!=0||!row[7].equals("-"))throw new IOException("Software directory alias differs"); }
                    else { Pin actual=pin(path.toRealPath(),Math.max(1,size),check);if(actual.bytes()!=size||!actual.sha256().equals(row[7]))throw new IOException("Adopted software bytes changed: "+path); }
                }
                default -> throw new IOException("Unqualified software object kind");
            }
    }
    private static Map<String,String> stringMap(Object value) throws IOException { var result=new TreeMap<String,String>();for(var entry:object(value).entrySet())result.put(text(entry.getKey()),text(entry.getValue()));return result; }
}
