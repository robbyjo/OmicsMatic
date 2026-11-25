/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Read-only admission of separately installed exact original static executables. */
final class GemmaRuntime {
    static final class Unavailable extends IOException{private static final long serialVersionUID=1L;Unavailable(String message){super(message);}}
    static final List<String> RESOURCES=List.of("source-lock.json","health-fixture.json","LICENSE","health.py","setup.py");
    private final Path home,report;
    private final Pin markerPin,completedPin,healthPin;
    private final Map<?,?> marker,lock;
    private final Map<String,Pin> helpers;
    GemmaRuntime(Path supplied,Path setupReport)throws IOException{
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original GEMMA execution requires Linux x86_64 Java; use Linux Java inside WSL or select native kinship where appropriate");
        var helperPins=new TreeMap<String,Pin>();for(String name:List.of("/usr/bin/setsid","/usr/bin/prlimit")){Path p=Path.of(name);if(!Files.isRegularFile(p)||!Files.isExecutable(p)||!Files.isReadable(p))throw new Unavailable("Required permitted execution helper absent: "+name+". Install separately where allowed or retain SKIP3");helperPins.put(name,pin(p,16777216,()->{}));}helpers=Map.copyOf(helperPins);
        if(!Files.isReadable(Path.of("/proc/self/stat"))||!Files.isReadable(Path.of("/proc/self/status")))throw new Unavailable("Readable Linux /proc process metadata is required; dependent analysis skipped");
        home=supplied.toAbsolutePath().normalize();report=setupReport.toAbsolutePath().normalize();
        if(!Files.isRegularFile(home.resolve("runtime.json"),LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(report.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact completed private GEMMA runtime and matching completed setup report are required. Export and run separate permitted setup; dependent analysis skipped");
        directory(home);directory(report);markerPin=pin(home.resolve("runtime.json"),1048576,()->{});completedPin=pin(report.resolve("COMPLETED.json"),1048576,()->{});healthPin=pin(report.resolve("health/COMPLETED.json"),1048576,()->{});marker=object(json(home.resolve("runtime.json"),1048576));lock=object(SimpleJson.parse(utf8(resource("source-lock.json"))));
        require(integer(marker.get("schema_version"),1,1)==1&&lock.get("profile").equals(marker.get("profile"))&&fingerprint(marker.get("source_lock"),1048576).equals(bytesPin(resource("source-lock.json")))&&Boolean.FALSE.equals(marker.get("user_analysis_admitted"))&&Boolean.FALSE.equals(marker.get("original_binaries_modified")),"Changed/expanded runtime marker; never refresh old seals");
        savedEvidence(marker,object(json(report.resolve("COMPLETED.json"),1048576)),object(json(report.resolve("health/COMPLETED.json"),1048576)),markerPin,healthPin);verify();
    }
    static Pin bytesPin(byte[] bytes){return new Pin(bytes.length,sha(bytes));}
    static byte[] resource(String name)throws IOException{if(!RESOURCES.contains(name))throw new IOException("Unknown GEMMA support resource");try(var in=GemmaRuntime.class.getResourceAsStream("/org/jlinalg/cli/gemma-setup/"+name)){if(in==null)throw new IOException("Missing bundled GEMMA support: "+name);return in.readAllBytes();}}
    Path home(){return home;}Path report(){return report;}
    Path program(String edition)throws IOException{if(!object(lock.get("editions")).containsKey(edition))throw new Unavailable("Exact GEMMA edition absent: "+edition);return home.resolve("bin/gemma-"+edition);}
    void verify()throws IOException{
        for(var e:helpers.entrySet())require(pin(Path.of(e.getKey()),16777216,()->{}).equals(e.getValue()),"Execution helper changed");
        require(markerPin.equals(pin(home.resolve("runtime.json"),1048576,()->{}))&&completedPin.equals(pin(report.resolve("COMPLETED.json"),1048576,()->{}))&&healthPin.equals(pin(report.resolve("health/COMPLETED.json"),1048576,()->{})),"Runtime/setup evidence changed");
        var expected=object(marker.get("tree"));var seen=new HashSet<String>();try(var paths=Files.walk(home)){
            var all=paths.limit(102).toList();require(all.size()<=101,"Runtime node count exceeds capacity");
            for(Path p:all){if(p.equals(home)||p.equals(home.resolve("runtime.json")))continue;String name=home.relativize(p).toString().replace('\\','/');require(seen.add(name)&&expected.containsKey(name)&&!Files.isSymbolicLink(p),"Extra/linked runtime member: "+name);var row=object(expected.get(name));if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))require(row.keySet().equals(Set.of("kind"))&&"directory".equals(row.get("kind")),"Changed runtime directory");else require("file".equals(row.get("kind"))&&pin(p,33554432,()->{}).equals(fingerprint(row,33554432)),"Changed runtime file: "+name);}
        }require(seen.equals(expected.keySet()),"Missing runtime members");
        for(String name:List.of("source-lock.json","health-fixture.json","LICENSE","health.py"))require(pin(home.resolve("support").resolve(name),1048576,()->{}).equals(bytesPin(resource(name))),"Runtime support differs from this executable");
        for(var e:object(lock.get("editions")).entrySet()){Path p=program(text(e.getKey()));require(Files.isExecutable(p)&&pin(p,33554432,()->{}).equals(fingerprint(object(e.getValue()).get("program"),33554432)),"Original GEMMA executable differs");}
    }
    static void savedEvidence(Map<?,?> marker,Map<?,?> done,Map<?,?> health,Pin markerPin,Pin healthPin)throws IOException{
        var lock=object(SimpleJson.parse(utf8(resource("source-lock.json"))));require(lock.get("profile").equals(marker.get("profile"))&&fingerprint(marker.get("source_lock"),1048576).equals(bytesPin(resource("source-lock.json")))&&Boolean.FALSE.equals(marker.get("user_analysis_admitted"))&&Boolean.FALSE.equals(marker.get("original_binaries_modified")),"Captured runtime profile/source identity differs");
        require("COMPLETE_PRIVATE_GEMMA_RUNTIME_HEALTH".equals(done.get("status"))&&lock.get("profile").equals(done.get("profile"))&&markerPin.equals(fingerprint(done.get("runtime_marker"),1048576))&&healthPin.equals(fingerprint(done.get("health"),1048576))&&integer(done.get("original_controls"),9,9)==9&&Boolean.FALSE.equals(done.get("user_analysis_admitted")),"Setup does not admit this exact runtime and health");
        require("PASS_THREE_ORIGINAL_GEMMA_EDITIONS_NINE_ARTIFICIAL_CONTROLS".equals(health.get("status"))&&Boolean.FALSE.equals(health.get("user_analysis_admitted"))&&Boolean.FALSE.equals(health.get("whole_paper_reproduced")),"Synthetic health is incomplete or expanded");var cases=new HashSet<String>();for(Object value:list(health.get("cases"))){var row=object(value);require("PASS_ORIGINAL_ARTIFICIAL_HEALTH".equals(row.get("status"))&&cases.add(text(row.get("edition"))+":"+text(row.get("mode"))),"Failed/duplicate artificial health case");}var expected=new HashSet<String>();for(String edition:List.of("0.98.1","0.98.3","0.98.5"))for(String mode:List.of("gk1","gk2","lmm4"))expected.add(edition+":"+mode);require(cases.equals(expected),"Missing/extra original edition/mode health population");
        var tree=object(marker.get("tree"));var wanted=new HashSet<>(Set.of("bin","support"));for(String name:List.of("source-lock.json","health-fixture.json","LICENSE","health.py")){String member="support/"+name;wanted.add(member);require(bytesPin(resource(name)).equals(fingerprint(tree.get(member),1048576)),"Captured support identity differs");}for(var entry:object(lock.get("editions")).entrySet()){String name="bin/gemma-"+text(entry.getKey());wanted.add(name);require(fingerprint(object(entry.getValue()).get("program"),33554432).equals(fingerprint(tree.get(name),33554432)),"Captured original executable identity differs");}require(wanted.equals(tree.keySet()),"Captured runtime namespace differs");
    }
    Map<String,Object> provenance(){return Map.of("runtime_marker",markerPin,"setup_completed",completedPin,"health_completed",healthPin,"helpers",helpers,"original_binaries_modified",false);}
    private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
}
