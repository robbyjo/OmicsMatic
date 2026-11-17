/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
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

/** Complete native provider/host verification. This does not admit a managed workflow or install software. */
final class SnakemakeOriginalDeployment {
    private static final int MAXIMUM=67_108_864;
    private static final long FILE_MAXIMUM=4_294_967_296L;
    private static final Map<String,String> VERSIONS=Map.of("conda","conda 26.9.4","apptainer","apptainer version 1.5.5");
    private static final Map<String,Pin> ARTIFACTS=Map.of("conda",new Pin(128272241,"bbaf16b3897da24c4be50315934ba74848f617b4495982841d6a0a60e0edbbdf"),"apptainer",new Pin(32997476,"25095d3711504b3e8657dbb2e172ef8db9db43c4503d940c9c36b105db8dce77"));
    private static final Map<String,Pin> LICENSES=Map.of("conda",new Pin(2474,"e94d14f078d66b1cc18d9c4180634ec6f70c10761123b86c26c442d6598bdf97"),"apptainer",new Pin(4495,"db68e0963f8b286d18f96e62b4f0e08b5a76b6388bbb2c595c9dcb0ef178bd69"));
    record Provider(Path prefix,Path executable,String version,Pin manifest,int entries) { }
    record Context(Path home,Map<String,Provider> providers,Map<String,Pin> metadata,int elfObjects,int externalPaths) {
        Context {providers=Map.copyOf(providers);metadata=Map.copyOf(metadata);}
        void verify(Runnable check)throws IOException {if(!equals(open(home,check)))throw new IOException("Deployment providers, host libraries or adoption evidence changed; no workflow result admitted");}
    }
    private record Stamp(String key,String modified,long size,int mode,String kind,String target) { }
    @FunctionalInterface private interface Checked {void run(Runnable check)throws IOException;}
    private SnakemakeOriginalDeployment() { }
    static Context open(Path supplied,Runnable check)throws IOException {
        check.run();
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new SnakemakeOriginalRuntime.Unavailable("Selected deployment providers require permitted Linux x86-64 Java; restore the permitted host or skip dependent analysis");
        Path home=supplied.toAbsolutePath().normalize();
        if(!Files.isRegularFile(home.resolve("ADOPTED.json"),LinkOption.NOFOLLOW_LINKS))throw new SnakemakeOriginalRuntime.Unavailable("Complete provider adoption is missing; perform permitted setup and adoption into NEW paths or skip dependent analysis");
        directory(home);Pin marker=pin(home.resolve("ADOPTED.json"),MAXIMUM,check);Map<?,?> adopted=object(json(home.resolve("ADOPTED.json"),MAXIMUM));
        keys(adopted,"status","schema_version","engine_profile","components","source_controls","host","elf_objects","external_paths","original_diagnostics","metadata","directories","user_analysis_admitted","new_catalog_credit","whole_goal_complete");
        integer(adopted.get("schema_version"),1,1);
        if(!"ADOPTED_SELECTED_DEPLOYMENT_PROVIDERS_NOT_JAVA_ANALYSIS_ADAPTER".equals(adopted.get("status"))||!SnakemakeOriginalRuntime.PROFILE.equals(adopted.get("engine_profile"))||!Boolean.FALSE.equals(adopted.get("user_analysis_admitted"))||!Boolean.FALSE.equals(adopted.get("whole_goal_complete"))||integer(adopted.get("new_catalog_credit"),0,0)!=0)throw new IOException("Provider adoption claims an unqualified profile, analysis or catalog status");
        var expectedMetadata=pinMap(adopted.get("metadata"));var directories=strings(adopted.get("directories"));verifyRecords(home,expectedMetadata,directories,Set.of("ADOPTED.json"),check);
        Map<?,?> host=object(adopted.get("host"));keys(host,"platform","machine","uid","effective_uid");
        if(!"x86_64".equals(host.get("machine"))||!text(host.get("platform")).startsWith("Linux-"+System.getProperty("os.version")+"-"))throw new SnakemakeOriginalRuntime.Unavailable("Provider adoption belongs to another kernel/architecture; adopt the exact permitted software on this host into NEW paths or skip execution");
        String uidLine=Files.readAllLines(Path.of("/proc/self/status")).stream().filter(s->s.startsWith("Uid:")).findFirst().orElseThrow(()->new IOException("Current real/effective Linux UIDs unavailable"));String[] uids=uidLine.trim().split("\\s+");
        if(uids.length!=5||!uids[1].equals(Long.toString(integer(host.get("uid"),0,4294967295L)))||!uids[2].equals(Long.toString(integer(host.get("effective_uid"),0,4294967295L))))throw new SnakemakeOriginalRuntime.Unavailable("Provider qualification used a different real/effective UID; perform a fresh permitted adoption or skip dependent execution");
        Map<?,?> components=object(adopted.get("components"));keys(components,"conda","apptainer");var providers=new TreeMap<String,Provider>();
        for(String name:List.of("conda","apptainer")) {
            Map<?,?> selected=object(components.get(name));keys(selected,"prefix","executable","version","manifest","entries","artifact","license");Path prefix=directory(absolutePath(selected.get("prefix"))),executable=absolutePath(selected.get("executable"));
            if(!executable.equals(prefix.resolve("bin/"+name))||!Files.isExecutable(executable))throw new SnakemakeOriginalRuntime.Unavailable("Selected provider executable missing or forbidden: "+executable+". Restore the exact permitted setup or skip dependent analysis");
            if(!VERSIONS.get(name).equals(selected.get("version"))||!ARTIFACTS.get(name).equals(fingerprint(selected.get("artifact"),FILE_MAXIMUM))||!LICENSES.get(name).equals(fingerprint(selected.get("license"),MAXIMUM)))throw new IOException("Selected provider edition/archive/license differs: "+name);
            Pin manifest=fingerprint(selected.get("manifest"),MAXIMUM);if(!manifest.equals(expectedMetadata.get(name+"-files.json"))||!LICENSES.get(name).equals(expectedMetadata.get(name+"-LICENSE.txt")))throw new IOException("Provider adoption metadata binding differs: "+name);
            Map<?,?> installed=object(json(home.resolve(name.toUpperCase(java.util.Locale.ROOT)+"-INSTALLED.json"),MAXIMUM));
            keys(installed,"status","component","prefix","executable","artifact","license","manifest","entries","version","user_analysis_admitted");
            if(!"INSTALLED_AND_VERSION_PROBED_NOT_SOURCE_WORKFLOW_QUALIFIED".equals(installed.get("status"))||!name.equals(installed.get("component"))||!prefix.toString().equals(installed.get("prefix"))||!executable.toString().equals(installed.get("executable"))||!((String)selected.get("version")+'\n').equals(installed.get("version"))||!Boolean.FALSE.equals(installed.get("user_analysis_admitted")))throw new IOException("Provider installation and adoption declarations disagree: "+name);
            for(String field:List.of("artifact","license","manifest"))if(!fingerprint(selected.get(field),FILE_MAXIMUM).equals(fingerprint(installed.get(field),FILE_MAXIMUM)))throw new IOException("Provider installation pin differs: "+field);
            int entries=(int)integer(selected.get("entries"),1,200000);if(integer(installed.get("entries"),entries,entries)!=entries)throw new IOException("Provider installation entry count differs");
            Map<?,?> tree=object(json(home.resolve(name+"-files.json"),MAXIMUM));keys(tree,"schema_version","root","scope","nodes");integer(tree.get("schema_version"),1,1);text(tree.get("scope"));
            if(!prefix.toString().equals(tree.get("root"))||object(tree.get("nodes")).size()!=entries)throw new IOException("Complete provider prefix/census declaration differs: "+name);
            verifyTree(prefix,object(tree.get("nodes")),check);providers.put(name,new Provider(prefix,executable,VERSIONS.get(name),manifest,entries));
        }
        if(providers.get("conda").prefix().startsWith(providers.get("apptainer").prefix())||providers.get("apptainer").prefix().startsWith(providers.get("conda").prefix()))throw new IOException("Provider prefixes must be separate complete trees");
        Map<?,?> loader=object(json(home.resolve("HOST-ELF.json"),MAXIMUM));keys(loader,"scope","objects","external");text(loader.get("scope"));Map<?,?> external=object(loader.get("external")),objects=object(loader.get("objects"));
        int elfObjects=(int)integer(adopted.get("elf_objects"),1,200000),externalPaths=(int)integer(adopted.get("external_paths"),1,200000);
        if(objects.size()!=elfObjects||external.size()!=externalPaths)throw new IOException("Provider loader census differs");
        for(var entry:objects.entrySet()) {Path path=absolutePath(entry.getKey());if(providers.values().stream().noneMatch(p->path.startsWith(p.prefix())))throw new IOException("Inspected provider object escapes selected prefixes");Map<?,?> value=object(entry.getValue());String component=text(value.get("component"));if(!providers.containsKey(component)||!path.startsWith(providers.get(component).prefix()))throw new IOException("Inspected object/provider binding differs");}
        var hostChecks=new ArrayList<Checked>();
        for(var entry:external.entrySet()) {Path path=absolutePath(entry.getKey());Map<?,?> value=object(entry.getValue());keys(value,"kind","mode","target","resolved","pin");hostChecks.add(c->verifyHost(path,value,c));}
        parallel(hostChecks,check);verifySource(home,object(adopted.get("source_controls")),providers,check);
        List<?> diagnostics=list(json(home.resolve("SOURCE-DIAGNOSTICS.json"),MAXIMUM));if(diagnostics.size()!=integer(adopted.get("original_diagnostics"),0,1000000))throw new IOException("Retained original diagnostic count differs");
        verifyRecords(home,expectedMetadata,directories,Set.of("ADOPTED.json"),check);if(!pin(home.resolve("ADOPTED.json"),MAXIMUM,check).equals(marker))throw new IOException("Provider adoption marker changed during complete verification");
        var metadata=new TreeMap<>(expectedMetadata);metadata.put("ADOPTED.json",marker);return new Context(home,providers,metadata,elfObjects,externalPaths);
    }
    private static void verifySource(Path home,Map<?,?> source,Map<String,Provider> providers,Runnable check)throws IOException {
        keys(source,"path","marker","source","export_index");Path root=directory(absolutePath(source.get("path")));Map<?,?> index=object(json(home.resolve("SOURCE-RECORDS.json"),MAXIMUM));
        if(!pin(home.resolve("SOURCE-RECORDS.json"),MAXIMUM,check).equals(fingerprint(source.get("export_index"),MAXIMUM)))throw new IOException("Original deployment export index binding differs");
        integer(index.get("schema_version"),1,1);var files=pinMap(index.get("files"));Set<String> excludes=Set.of();
        if(Files.isRegularFile(root.resolve("EXPORTED.json"),LinkOption.NOFOLLOW_LINKS)&&pin(root.resolve("EXPORTED.json"),MAXIMUM,check).equals(fingerprint(source.get("export_index"),MAXIMUM)))excludes=Set.of("EXPORTED.json");
        verifyRecords(root,files,null,excludes,check);
        if(Files.exists(root.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS)||!pin(root.resolve("COMPLETED.json"),MAXIMUM,check).equals(fingerprint(source.get("marker"),MAXIMUM))||!pin(root.resolve("SOURCE.json"),MAXIMUM,check).equals(fingerprint(source.get("source"),MAXIMUM)))throw new IOException("Failed or changed original deployment qualification family");
        Map<?,?> completed=object(json(root.resolve("COMPLETED.json"),MAXIMUM)),selected=object(json(root.resolve("SOURCE.json"),MAXIMUM));
        if(!"PASS_THREE_ORIGINAL_SOURCE_DEPLOYMENT_MODES_NOT_JAVA_ADAPTER".equals(completed.get("status"))||!Boolean.TRUE.equals(completed.get("manager_trees_unchanged"))||!Boolean.FALSE.equals(completed.get("user_analysis_admitted"))||!Boolean.FALSE.equals(completed.get("whole_goal_complete"))||integer(completed.get("new_catalog_credit"),0,0)!=0)throw new IOException("Original source qualification does not establish complete selected provider controls");
        if(!Path.of(text(selected.get("manager_installation"))).resolve("conda").equals(providers.get("conda").prefix())||!providers.get("apptainer").prefix().toString().equals(selected.get("apptainer_prefix")))throw new IOException("Original source controls used different provider prefixes");
        var modes=new HashSet<String>();
        for(Object item:list(completed.get("cases"))) {Map<?,?> row=object(item);String mode=text(row.get("mode"));if(!Set.of("conda","apptainer","conda-apptainer").contains(mode)||!modes.add(mode))throw new IOException("Missing/duplicate source deployment mode");Map<?,?> process=object(row.get("process"));integer(process.get("exit_code"),0,0);if(!Boolean.FALSE.equals(process.get("timed_out")))throw new IOException("Original deployment control did not terminate successfully");}
        if(modes.size()!=3)throw new IOException("Incomplete original deployment mode coverage");
    }
    static void verifyTree(Path prefix,Map<?,?> nodes,Runnable check)throws IOException {
        directory(prefix);Map<String,Stamp> before=inventory(prefix,200001,check);var names=new HashSet<>(before.keySet());names.remove("");
        if(!names.equals(nodes.keySet()))throw new IOException("Complete provider tree membership changed: "+prefix);var checks=new ArrayList<Checked>();
        for(var entry:nodes.entrySet()) {String name=member(text(entry.getKey()));Map<?,?> declared=object(entry.getValue());checks.add(c->verifyNode(prefix.resolve(name),declared,c));}
        parallel(checks,check);if(!before.equals(inventory(prefix,200001,check)))throw new IOException("Provider tree identity, modes, links or membership changed during complete verification");
    }
    private static void verifyNode(Path path,Map<?,?> declared,Runnable check)throws IOException {
        check.run();String kind=text(declared.get("kind"));int expected=(int)integer(declared.get("mode"),0,07777);
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new SnakemakeOriginalRuntime.Unavailable("Adopted provider member missing: "+path+". Restore exact permitted software or skip dependent analysis");
        if(unixMode(path)!=expected)throw new IOException("Provider member mode changed: "+path);
        switch(kind) {
            case "directory" -> {keys(declared,"kind","mode");if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Provider directory kind changed: "+path);}
            case "file" -> {keys(declared,"kind","mode","pin");if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!pin(path,FILE_MAXIMUM,check).equals(fingerprint(declared.get("pin"),FILE_MAXIMUM)))throw new IOException("Provider file bytes or kind changed: "+path);}
            case "symlink" -> {
                keys(declared,"kind","mode","target","resolved","target_kind","pin");if(!Files.isSymbolicLink(path)||!Files.readSymbolicLink(path).toString().equals(text(declared.get("target"))))throw new IOException("Provider symlink target changed: "+path);
                Path resolved=resolveLinks(path);if(!resolved.toString().equals(text(declared.get("resolved"))))throw new IOException("Provider symlink resolution changed: "+path);
                String targetKind=Files.isRegularFile(resolved,LinkOption.NOFOLLOW_LINKS)?"file":Files.isDirectory(resolved,LinkOption.NOFOLLOW_LINKS)?"directory":Files.notExists(resolved,LinkOption.NOFOLLOW_LINKS)?"missing":"special-or-unreadable";
                if(!targetKind.equals(declared.get("target_kind")))throw new IOException("Provider symlink target kind changed: "+path);
                if(targetKind.equals("file")){if(!pin(resolved,FILE_MAXIMUM,check).equals(fingerprint(declared.get("pin"),FILE_MAXIMUM)))throw new IOException("Provider symlink file bytes changed: "+path);}
                else if(!Set.of("directory","missing").contains(targetKind)||declared.get("pin")!=null)throw new IOException("Invalid provider directory/dangling symlink pin: "+path);
            }
            default -> throw new IOException("Unexpected provider object kind: "+kind);
        }
    }
    private static void verifyHost(Path path,Map<?,?> value,Runnable check)throws IOException {
        if(!Files.isRegularFile(path)||!Files.isReadable(path))throw new SnakemakeOriginalRuntime.Unavailable("Provider host library missing/unreadable: "+path+". Restore the exact permitted library or skip dependent analysis");
        if(unixMode(path)!=integer(value.get("mode"),0,07777)||!path.toRealPath().toString().equals(text(value.get("resolved"))))throw new IOException("Provider host library mode/resolution changed: "+path);
        String kind=text(value.get("kind"));if(kind.equals("symlink")){if(!Files.isSymbolicLink(path)||!Files.readSymbolicLink(path).toString().equals(text(value.get("target"))))throw new IOException("Provider host library alias changed");}
        else if(!kind.equals("file")||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||value.get("target")!=null)throw new IOException("Provider host library kind changed");
        if(!pin(path.toRealPath(),FILE_MAXIMUM,check).equals(fingerprint(value.get("pin"),FILE_MAXIMUM)))throw new IOException("Provider host library bytes changed: "+path);
    }
    private static void verifyRecords(Path root,Map<String,Pin> files,List<String> declaredDirectories,Set<String> excluded,Runnable check)throws IOException {
        Map<String,Stamp> before=inventory(root,200001,check);var actualFiles=new HashSet<String>();var actualDirectories=new HashSet<String>();
        for(var entry:before.entrySet()) {if(entry.getKey().isEmpty())continue;switch(entry.getValue().kind()){case "file" -> {if(!excluded.contains(entry.getKey()))actualFiles.add(entry.getKey());}case "directory" -> actualDirectories.add(entry.getKey());default -> throw new IOException("Adoption/source records must contain ordinary files and directories only");}}
        if(!actualFiles.equals(files.keySet())||declaredDirectories!=null&&(declaredDirectories.size()!=new HashSet<>(declaredDirectories).size()||!actualDirectories.equals(new HashSet<>(declaredDirectories))))throw new IOException("Complete provider adoption/source metadata census changed");
        var checks=new ArrayList<Checked>();for(var entry:files.entrySet()){Path file=root.resolve(entry.getKey());checks.add(c->{if(!pin(file,MAXIMUM,c).equals(entry.getValue()))throw new IOException("Provider adoption/source record bytes changed: "+file);});}parallel(checks,check);
        if(!before.equals(inventory(root,200001,check)))throw new IOException("Provider adoption/source record identity or census changed during verification");
    }
    private static Map<String,Pin> pinMap(Object value)throws IOException {var result=new TreeMap<String,Pin>();for(var entry:object(value).entrySet()){String name=member(text(entry.getKey()));Map<?,?> declared=object(entry.getValue());keys(declared,"bytes","sha256");result.put(name,fingerprint(declared,MAXIMUM));}return Map.copyOf(result);}
    private static Pin fingerprint(Object value,long maximum)throws IOException {keys(object(value),"bytes","sha256");return SnakemakeOriginalFiles.fingerprint(value,maximum);}
    private static Path absolutePath(Object value)throws IOException {Path path=Path.of(text(value));if(!path.isAbsolute()||!path.normalize().equals(path))throw new IOException("Require canonical absolute Linux provider path");return path;}
    private static int unixMode(Path path)throws IOException {return ((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&07777;}
    private static Map<String,Stamp> inventory(Path root,int maximum,Runnable check)throws IOException {
        var result=new TreeMap<String,Stamp>();Files.walkFileTree(root,new SimpleFileVisitor<>() {
            private void add(Path path,BasicFileAttributes value)throws IOException {check.run();if(result.size()>=maximum)throw new IOException("Complete provider/record inventory capacity exceeded");String kind=value.isRegularFile()?"file":value.isDirectory()?"directory":value.isSymbolicLink()?"symlink":"special";result.put(root.relativize(path).toString(),new Stamp(String.valueOf(value.fileKey()),value.lastModifiedTime().toString(),value.size(),unixMode(path),kind,value.isSymbolicLink()?Files.readSymbolicLink(path).toString():null));}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes value)throws IOException {add(path,value);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes value)throws IOException {add(path,value);return FileVisitResult.CONTINUE;}
        });return Map.copyOf(result);
    }
    /** Resolve existing symlink components while preserving genuinely unresolved package-cache tails. */
    private static Path resolveLinks(Path supplied)throws IOException {
        var remaining=new ArrayDeque<String>();for(Path part:supplied)remaining.addLast(part.toString());Path current=supplied.getRoot();int links=0;
        while(!remaining.isEmpty()) {String name=remaining.removeFirst();if(name.equals("."))continue;if(name.equals("..")){if(current.getParent()!=null)current=current.getParent();continue;}Path candidate=current.resolve(name);
            if(Files.isSymbolicLink(candidate)){if(++links>256)throw new IOException("Cyclic/oversized provider symlink chain");Path target=Files.readSymbolicLink(candidate);var parts=new ArrayList<String>();for(Path part:target)parts.add(part.toString());for(int i=parts.size()-1;i>=0;i--)remaining.addFirst(parts.get(i));if(target.isAbsolute())current=target.getRoot();}
            else current=candidate;
        }return current;
    }
    private static void parallel(List<Checked> checks,Runnable check)throws IOException {
        var pool=Executors.newFixedThreadPool(8);var completion=new ExecutorCompletionService<Void>(pool);var pending=new HashSet<Future<Void>>();int submitted=0,finished=0;
        Runnable guarded=()->{if(Thread.currentThread().isInterrupted())throw new CancellationException("Provider verification interrupted");synchronized(check){check.run();}};
        try {while(finished<checks.size()){guarded.run();while(submitted<checks.size()&&pending.size()<16){Checked next=checks.get(submitted++);pending.add(completion.submit(()->{next.run(guarded);return null;}));}Future<Void> result=completion.take();pending.remove(result);result.get();finished++;}}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Complete provider verification interrupted",interrupted);}
        catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof IOException io)throw io;if(cause instanceof RuntimeException runtime)throw runtime;throw new IOException("Provider verifier worker failed",cause);}
        finally {for(Future<Void> task:pending)task.cancel(true);pool.shutdownNow();boolean interrupted=Thread.interrupted();try{if(!pool.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Provider verifier workers remain live; no result admitted");}catch(InterruptedException failure){interrupted=true;throw new IOException("Provider verification shutdown interrupted",failure);}finally{if(interrupted)Thread.currentThread().interrupt();}}
    }
}
