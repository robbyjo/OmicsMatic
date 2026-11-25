/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Exact original-source and private-provider admission; no download, install or resealing. */
final class CrossmapRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String text){super(text);}
    }
    private final Path home,report;
    private final Map<?,?> marker,lock;
    private final Pin markerPin,reportPin,healthPin;
    private final Map<String,Pin> executionHelperPins;
    CrossmapRuntime(Path supplied,Path setupReport)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("CrossMap execution requires the qualified Linux x86_64 provider; use Linux Java inside WSL on Windows");
        var helpers=new TreeMap<String,Pin>();
        for(String name:List.of("/usr/bin/setsid","/usr/bin/prlimit")){
            Path helper=Path.of(name);if(!Files.isRegularFile(helper)||!Files.isExecutable(helper)||!Files.isReadable(helper))throw new Unavailable("Required permitted Linux execution helper is unavailable: "+name+"; install it separately where allowed or retain SKIP3");
            helpers.put(name,pin(helper,16777216,()->{}));
        }
        if(!Files.isReadable(Path.of("/proc/self/stat"))||!Files.isReadable(Path.of("/proc/self/status")))throw new Unavailable("Readable Linux /proc process metadata is required for owned process/resource monitoring; dependent conversion skipped");
        executionHelperPins=Map.copyOf(helpers);
        home=supplied.toAbsolutePath().normalize();report=setupReport.toAbsolutePath().normalize();
        if(!Files.isRegularFile(home.resolve("runtime.json"),LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(report.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact completed private runtime and its setup report are required; dependent conversion skipped");
        directory(home);directory(report);lock=object(SimpleJson.parse(utf8(resource("source-lock.json"))));markerPin=pin(home.resolve("runtime.json"),4194304,()->{});reportPin=pin(report.resolve("COMPLETED.json"),1048576,()->{});healthPin=pin(report.resolve("health/COMPLETED.json"),1048576,()->{});marker=object(json(home.resolve("runtime.json"),4194304));
        require(integer(marker.get("schema_version"),1,1)==1&&text(lock.get("profile")).equals(marker.get("profile"))&&fingerprint(marker.get("source_lock"),1048576).equals(bytesPin(resource("source-lock.json"))),"Changed runtime schema/profile/source lock; do not refresh old seals");
        require(Boolean.FALSE.equals(marker.get("user_analysis_admitted"))&&Boolean.FALSE.equals(marker.get("author_Python37_reproduced")),"Expanded runtime or author-environment claim");
        var completed=object(json(report.resolve("COMPLETED.json"),1048576));require("COMPLETE_PRIVATE_CROSSMAP_RUNTIME_HEALTH".equals(completed.get("status"))&&fingerprint(completed.get("runtime_marker"),4194304).equals(markerPin)&&lock.get("profile").equals(completed.get("profile")),"Setup report does not admit this exact runtime marker");
        require(list(completed.get("original_source_editions")).equals(List.of("0.5.2","0.6.3"))&&list(completed.get("original_formats_checked")).equals(List.of("bed","vcf","bigwig"))&&Boolean.FALSE.equals(completed.get("user_analysis_admitted")),"Incomplete source-edition/format health populations");
        var health=object(json(report.resolve("health/COMPLETED.json"),1048576));require("PASS_BOTH_ORIGINAL_EDITIONS_THREE_FORMATS".equals(health.get("status"))&&Boolean.TRUE.equals(health.get("artificial_controls"))&&Boolean.FALSE.equals(health.get("user_analysis_admitted")),"Original synthetic runtime health is absent");
        var actual=new HashSet<String>();for(Object raw:list(health.get("cases"))){var row=object(raw);require("PASS_ORIGINAL_ARTIFICIAL_HEALTH".equals(row.get("status")),"A selected original health case failed");require(actual.add(text(row.get("edition"))+":"+text(row.get("format"))),"Duplicate health case");}
        require(actual.equals(Set.of("0.5.2:bed","0.5.2:vcf","0.5.2:bigwig","0.6.3:bed","0.6.3:vcf","0.6.3:bigwig")),"Incomplete original health cases");verify();
    }
    static byte[] resource(String name)throws IOException {
        if(!Set.of("source-lock.json","setup.py","health.py","analysis.py").contains(name))throw new IOException("Unknown CrossMap support resource");
        try(var input=CrossmapRuntime.class.getResourceAsStream("/org/jlinalg/cli/crossmap-setup/"+name)){if(input==null)throw new IOException("Missing bundled CrossMap support: "+name);return input.readAllBytes();}
    }
    static Pin bytesPin(byte[] raw){return new Pin(raw.length,sha(raw));}
    Path home(){return home;}
    Path setupReport(){return report;}
    Path python(){return home.resolve("bin/python");}
    Path source(String version)throws IOException {
        if(!object(lock.get("sources")).containsKey(version))throw new Unavailable("No qualified original CrossMap edition "+version+"; no edition is inferred or substituted");return home.resolve("sources").resolve(version);
    }
    Path entrypoint(String version)throws IOException {return source(version).resolve(member(text(object(object(lock.get("sources")).get(version)).get("entrypoint"))));}
    void verify()throws IOException {
        for(var row:executionHelperPins.entrySet())require(pin(Path.of(row.getKey()),16777216,()->{}).equals(row.getValue()),"Linux execution helper changed during conversion: "+row.getKey());
        require(pin(home.resolve("runtime.json"),4194304,()->{}).equals(markerPin)&&pin(report.resolve("COMPLETED.json"),1048576,()->{}).equals(reportPin)&&pin(report.resolve("health/COMPLETED.json"),1048576,()->{}).equals(healthPin),"Runtime/setup evidence changed");
        require(pin(home.resolve("support/health.py"),1048576,()->{}).equals(bytesPin(resource("health.py"))),"Runtime helper differs from this executable; prepare a new private home");
        var expected=object(marker.get("tree"));var seen=new TreeSet<String>();int[] nodes={0};
        Files.walkFileTree(home,new SimpleFileVisitor<>(){
            private void node(Path p,BasicFileAttributes attrs)throws IOException {
                if(p.equals(home)||p.equals(home.resolve("runtime.json")))return;if(++nodes[0]>50000)throw new IOException("CrossMap runtime namespace exceeds capacity");String name=home.relativize(p).toString().replace('\\','/');require(seen.add(name)&&expected.containsKey(name),"Extra/duplicate runtime member: "+name);var row=object(expected.get(name));String kind=text(row.get("kind"));
                if(attrs.isSymbolicLink()){require(kind.equals("symlink")&&Files.readSymbolicLink(p).toString().replace('\\','/').equals(text(row.get("target")))&&p.toRealPath().startsWith(home),"Changed/escaping runtime link: "+name);}
                else if(attrs.isDirectory())require(kind.equals("directory"),"Changed runtime directory: "+name);
                else require(attrs.isRegularFile()&&kind.equals("file")&&pin(p,1000000000L,()->{}).equals(fingerprint(row,1000000000L)),"Changed/nonregular runtime file: "+name);
            }
            @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException{node(p,a);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException{node(p,a);return FileVisitResult.CONTINUE;}
        });require(seen.equals(expected.keySet()),"Runtime members are missing");
        var external=object(marker.get("external"));for(var row:object(external.get("files")).entrySet()){Path p=Path.of(text(row.getKey()));require(pin(p,1000000000L,()->{}).equals(fingerprint(row.getValue(),1000000000L)),"Current-host interpreter/library/helper changed: "+p);}
        for(var row:object(external.get("symlinks")).entrySet()){Path p=Path.of(text(row.getKey()));require(Files.isSymbolicLink(p)&&Files.readSymbolicLink(p).toString().equals(text(row.getValue())),"Current-host library link changed: "+p);}
        for(var raw:object(lock.get("sources")).entrySet()){
            String version=text(raw.getKey());var declaration=object(raw.getValue());Path root=source(version);var members=object(declaration.get("members"));var files=new TreeSet<String>();try(var paths=Files.walk(root)){for(Path p:paths.toList()){if(p.equals(root)||Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;String n=root.relativize(p).toString().replace('\\','/');require(files.add(n)&&members.containsKey(n)&&pin(p,16777216,()->{}).equals(fingerprint(members.get(n),16777216)),"Original source body/namespace differs: "+version+"/"+n);}}require(files.equals(members.keySet()),"Original source members missing: "+version);
        }
    }
    Map<String,Object> provenance(){return Map.of("profile",lock.get("profile"),"marker",markerPin,"setup_report",reportPin,"synthetic_health",healthPin,"runtime_members_checked",objectSize(marker.get("tree")),"execution_helpers",executionHelperPins,"author_Python37_reproduced",false);}
    private static int objectSize(Object value){return ((Map<?,?>)value).size();}
    private static void require(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
}
