/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Exact selected original software, complete adoption metadata and host bindings. */
final class SnakemakeOriginalRuntime {
    static final String PROFILE="linux-x86_64-snakemake-9.27.1";
    static final String RESOURCE="/org/jlinalg/omics/snakemake-original-v1/";
    static final String OFFICIAL="c024be4c2d2d814dfdaf78f714dc9b047ea50d612fc98da936d74e2f77d9b67b";
    static final String LOCK="ebc39cd3f2d04a7ae915554e8291bd0e84fe7fe6b9f0edf17dc4bb137c44080a";
    static final List<String> HELPERS=List.of("driver.py","snakemake_logger_plugin_omicsmatic/__init__.py");
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
        int expectedEntries=(int)integer(ready.get("entries"),1,200000);verifyPayload(home.resolve("runtime-files.tsv"),environment,expectedEntries,check);
        return new Context(home,environment,python,metadata,manifest,expectedEntries);
    }
    private static void verifyPayload(Path manifest,Path environment,int expectedEntries,Runnable check) throws IOException {
        String[] lines=utf8(bytes(manifest,67_108_864)).split("\n",-1);
        if(lines.length!=expectedEntries+2||!lines[0].equals("root\tpath\tkind\tmode\tbytes\ttarget\tresolved\tsha256")||!lines[lines.length-1].isEmpty())throw new IOException("Complete software manifest row/header count differs");
        var expectedEnvironment=new HashSet<String>();var duplicate=new HashSet<String>();var packageFiles=new TreeMap<String,Pin>();
        for(int index=1;index<lines.length-1;index++) {
            check.run();String[] row=lines[index].split("\t",-1);if(row.length!=8||!duplicate.add(row[0]+'\t'+row[1]))throw new IOException("Malformed/duplicate software row");
            Path path;
            if(row[0].equals("env")) { member(row[1]);expectedEnvironment.add(row[1]);path=environment.resolve(row[1]); }
            else if(row[0].equals("system")) { path=Path.of(row[1]);if(!path.isAbsolute()||!path.normalize().equals(path))throw new IOException("Unqualified absolute system dependency path"); }
            else throw new IOException("Unknown software root");
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(path))throw new Unavailable("Adopted software dependency unavailable/unreadable: "+path+". Restore the exact permitted setup or skip execution.");
            int mode=Integer.parseInt(row[3]);long size=Long.parseLong(row[4]);
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
            String prefix="lib/python3.12/site-packages/snakemake/";
            if(row[0].equals("env")&&row[1].startsWith(prefix)&&row[2].equals("file"))packageFiles.put(row[1].substring(prefix.length()),new Pin(size,row[7]));
        }
        var currentEnvironment=new HashSet<String>();try(var paths=Files.walk(environment)) { for(var iterator=paths.iterator();iterator.hasNext();) { check.run();Path path=iterator.next();if(!path.equals(environment)) { if(currentEnvironment.size()>=200000)throw new IOException("Runtime member capacity exceeded");currentEnvironment.add(environment.relativize(path).toString()); } } }
        if(!currentEnvironment.equals(expectedEnvironment))throw new IOException("Complete adopted environment membership changed; no analysis admitted");
        Map<?,?> official=object(SnakemakeOriginalParameters.parse(utf8(resource("official-package.json")),1_048_576,100000));var expectedPackage=new TreeMap<String,Pin>();
        for(var entry:object(official.get("files")).entrySet())expectedPackage.put(text(entry.getKey()),fingerprint(entry.getValue(),67_108_864));
        if(!packageFiles.equals(expectedPackage))throw new IOException("Complete installed Snakemake package differs from the pinned original wheel");
        for(String helper:List.of("/usr/bin/setsid","/usr/bin/prlimit","/usr/bin/bash","/usr/bin/ldd","/usr/bin/env","/bin/sh"))if(!Files.isExecutable(Path.of(helper)))throw new Unavailable("Required permitted system helper unavailable: "+helper);
    }
    private static Map<String,String> stringMap(Object value) throws IOException { var result=new TreeMap<String,String>();for(var entry:object(value).entrySet())result.put(text(entry.getKey()),text(entry.getValue()));return result; }
}
