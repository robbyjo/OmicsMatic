/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Exact original compact runtime/source closure, checked prefix relocation and owned process supervision. */
final class LofteeWorkflowRuntime {
    private LofteeWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record Group(Path root,Map<String,Object> expected,Map<String,NextflowFiles.Pin> actual,Map<String,String> links,LofteeRuntimeTree.Snapshot tree) {
        void verify()throws IOException {
            tree.verify();
            for(var entry:actual.entrySet())require(entry.getValue().equals(LofteeRuntimeTree.read(tree,root.resolve(entry.getKey()),entry.getValue().bytes(),false).pin()),"Runtime/source bytes changed: "+root.resolve(entry.getKey()));
            for(var entry:links.entrySet()){Path file=root.resolve(entry.getKey());require(Files.isSymbolicLink(file)&&Files.readSymbolicLink(file).toString().equals(entry.getValue())&&file.toRealPath().startsWith(root),"Runtime link changed/escaped: "+file);}
            tree.verify();
        }
        Map<String,Object> describe(){var result=new TreeMap<String,Object>();actual.forEach((n,v)->result.put(n,Map.of("kind","file","bytes",v.bytes(),"sha256",v.sha256())));links.forEach((n,v)->result.put(n,Map.of("kind","symlink","target",v)));return Map.of("root",root.toString(),"files",result);}
    }
    record Prepared(Path environment,Path bioperl,Path source,Path perl,Path vep,List<Group> groups,Map<String,Object> host,Map<String,Object> profile) {
        void verify()throws IOException{verifyHost(host);for(Group group:groups)group.verify();for(String name:List.of("perl","samtools","bgzip","tabix","gzip","gunzip","zcat"))require(Files.isExecutable(environment.resolve("bin/"+name)),"Required exact command is no longer executable: "+name);}
        Map<String,String> environment(Path home){return Map.of("HOME",home.toString(),"PATH",environment.resolve("bin").toString(),"PERL5LIB",source+":"+environment.resolve("share/ensembl-vep-105.0-1")+":"+bioperl,"LD_LIBRARY_PATH",environment.resolve("lib").toString(),"LC_ALL","C","LANG","C","TZ","UTC","PERL_USE_UNSAFE_INC","0","TMPDIR",home.toString());}
        List<String> command(List<String> options){var result=new ArrayList<String>();result.add(perl.toString());result.add(vep.toString());result.addAll(options);return result;}
    }
    static byte[] resource(String name)throws IOException {
        try(var in=LofteeWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/loftee-workflow/"+name)){if(in==null)throw new IOException("Missing packaged LOFTEE runtime profile/notice: "+name);return in.readAllBytes();}
    }
    static void quick(LofteeWorkflowRequest.Request r)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Exact original VEP/LOFTEE execution requires Linux x86_64 Java. Native LofteeVcfAudit is portable. No Windows interoperability or software-version fallback is used.");
        if(r.transcripts().get("kind").equals("vep-cache"))throw new Unavailable("Original VEP105 cache execution has not been validated for this development profile. Supply the validated explicitly indexed GTF/FASTA file profile where appropriate, or keep cache-dependent annotation skipped; do not relabel or deserialize an unvalidated cache.");
        for(var e:r.runtime().entrySet())try{NextflowFiles.directory(e.getValue());}catch(IOException error){throw new Unavailable("Missing/linked/forbidden exact "+e.getKey()+" runtime directory; prepare the reviewed compact runtime or skip",error);}
    }
    static Prepared prepare(Path stage,LofteeWorkflowRequest.Request r,Map<String,Object> manifest)throws IOException {
        quick(r);var profile=object(NextflowParameters.parse(NextflowFiles.utf8(resource("SOURCE-PROFILE.json")),33554432,2000000));require(text(profile.get("profile")).equals(LofteeWorkflowRequest.PROFILE),"Packaged runtime profile differs from requested profile");var host=object(profile.get("host_files"));var groups=new ArrayList<Group>();
        try{
            verifyHost(host);Path environment=r.runtime().get("environment"),bioperl=r.runtime().get("bioperl"),original=r.runtime().get("source");
            groups.add(verifyGroup(environment,object(object(profile.get("environment")).get("files"))));groups.add(verifyGroup(bioperl,object(object(profile.get("bioperl")).get("files"))));var edition=object(object(profile.get("editions")).get(r.edition()));var sourceFiles=object(edition.get("source_files"));var expected=new LinkedHashMap<String,Object>();for(var e:sourceFiles.entrySet()){var value=object(e.getValue());value.put("kind","file");expected.put(e.getKey(),value);}groups.add(verifyGroup(original,expected));
            Path copied=stage.resolve("source/original");Files.createDirectories(copied);for(String name:expected.keySet()){Path destination=copied.resolve(name);Files.createDirectories(destination.getParent());Files.copy(original.resolve(name),destination);}
            groups.add(verifyGroup(copied,expected));Path metadata=stage.resolve("source");for(String name:List.of("SOURCE-PROFILE.json","APACHE-2.0.txt","NOTICE.txt"))Files.write(metadata.resolve(name),resource(name),StandardOpenOption.CREATE_NEW);
            var result=new Prepared(environment,bioperl,copied,environment.resolve(text(profile.get("perl_program"))),environment.resolve(text(profile.get("vep_program"))),List.copyOf(groups),host,profile);for(String name:List.of("perl","samtools","bgzip","tabix","gzip","gunzip","zcat"))require(Files.isExecutable(environment.resolve("bin/"+name)),"Required exact command is not executable: "+name);NextflowFiles.writeJson(stage.resolve("runtime-file-identities.json"),groups.stream().map(Group::describe).toList());manifest.put("original_LOFTEE_source_commit",edition.get("source_commit"));manifest.put("original_LOFTEE_repository",edition.get("repository"));manifest.put("original_VEP_version",profile.get("vep_version"));manifest.put("runtime_profile_sha256",NextflowFiles.sha(resource("SOURCE-PROFILE.json")));manifest.put("runtime_environment_fallback",false);manifest.put("runtime_installs_software",false);manifest.put("runtime_closed_PATH",environment.resolve("bin").toString());return result;
        }catch(IOException error){throw new Unavailable("Exact original runtime/source/host closure is unavailable, changed or forbidden; dependent analysis skipped. "+error.getMessage(),error);}
    }
    private static void verifyHost(Map<String,Object> expected)throws IOException {
        for(var entry:expected.entrySet()){Path file=Path.of(entry.getKey());var value=NextflowFiles.fingerprint(entry.getValue(),536870912);require(value.equals(NextflowFiles.pin(file,value.bytes(),()->{})),"Exact host dependency differs: "+file);}
        require(Path.of("/bin/sh").toRealPath().equals(Path.of("/usr/bin/dash")),"Original shell-call profile requires the recorded dash interpreter");require(Path.of("/lib64/ld-linux-x86-64.so.2").toRealPath().equals(Path.of("/usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2")),"ELF interpreter alias differs from the recorded host profile");
    }
    private static Group verifyGroup(Path root,Map<String,Object> expected)throws IOException {
        var tree=LofteeRuntimeTree.snapshot(root);require(tree.names().equals(expected.keySet()),"Require the exact exported runtime/source tree; extra/missing files are refused: "+root);var actual=new LinkedHashMap<String,NextflowFiles.Pin>();var links=new LinkedHashMap<String,String>();
        for(var e:expected.entrySet()){
            String name=NextflowFiles.member(e.getKey());var item=object(e.getValue());Path file=root.resolve(name);String kind=text(item.get("kind"));
            if(kind.equals("symlink")){String target=text(item.get("target"));String baseline=findBaseline(expected);if(target.startsWith(baseline+"/"))target=root+target.substring(baseline.length());require(Files.isSymbolicLink(file)&&Files.readSymbolicLink(file).toString().equals(target)&&file.toRealPath().startsWith(root),"Exact runtime link differs/escapes: "+file);links.put(name,target);continue;}
            require(kind.equals("file")&&!Files.isSymbolicLink(file),"Require an original regular runtime file: "+file);NextflowFiles.Pin value;
            if(item.containsKey("prefix_relocation")){var read=LofteeRuntimeTree.read(tree,file,536870912,true);byte[] normalized=LofteeRuntimePrefix.baseline(read.content(),object(item.get("prefix_relocation")),root.toString());var expectedPin=NextflowFiles.fingerprint(item,536870912);require(normalized.length==expectedPin.bytes()&&NextflowFiles.sha(normalized).equals(expectedPin.sha256()),"Runtime differs outside exact allowed prefix substitutions: "+file);value=read.pin();}
            else{var expectedPin=NextflowFiles.fingerprint(item,536870912);value=LofteeRuntimeTree.read(tree,file,expectedPin.bytes(),false).pin();require(value.equals(expectedPin),"Exact runtime/source bytes differ: "+file);}actual.put(name,value);
        }
        tree.verify();return new Group(root,Collections.unmodifiableMap(expected),Collections.unmodifiableMap(actual),Collections.unmodifiableMap(links),tree);
    }
    private static String findBaseline(Map<String,Object> expected)throws IOException {for(Object raw:expected.values()){var row=object(raw);if(row.containsKey("prefix_relocation"))return text(object(row.get("prefix_relocation")).get("old_prefix"));}return "__NO_ABSOLUTE_PREFIX_ALIAS__";}
    private static Set<String> names(Path root)throws IOException {
        var names=new HashSet<String>();int[] count={0};Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{require(++count[0]<=100000,"Runtime tree capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException {require(++count[0]<=100000&&(attrs.isRegularFile()||attrs.isSymbolicLink()),"Nonregular/oversized runtime tree");names.add(root.relativize(file).toString().replace('\\','/'));return FileVisitResult.CONTINUE;}
        });return names;
    }
    static void health(Path stage,LofteeWorkflowRequest.Request r,Prepared runtime,Map<String,Object> manifest)throws Exception {
        Path worker=stage.resolve("worker");Files.createDirectory(worker);Path home=Files.createDirectory(worker.resolve("health"));int code=execute(worker,home,"vep-help",runtime.command(List.of("--help")),r,runtime,manifest);String help=NextflowFiles.utf8(NextflowFiles.bytes(home.resolve("stdout.log"),1048576));if(code!=0||!help.contains("ensembl-vep")||!help.contains("105.0"))throw new Unavailable("Exact VEP105 help failed; preserve health logs and skip dependent analysis");
        for(String name:List.of("samtools","bgzip","tabix","gzip")){Path directory=Files.createDirectory(worker.resolve("health-"+name));if(execute(worker,directory,"health-"+name,List.of(runtime.environment().resolve("bin/"+name).toString(),"--version"),r,runtime,manifest)!=0)throw new Unavailable("Required exact "+name+" cannot execute; dependent analysis skipped");}
        Path script=home.resolve("module-health.pl");Files.writeString(script,"use JSON::PP; use LoF; use Bio::DB::BigFile; use DBD::SQLite; use List::MoreUtils; print JSON::PP->new->canonical->encode({perl=>$^V->normal,modules=>{map {$_=>$INC{$_}} sort keys %INC}}),\"\\n\";\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);Path modules=Files.createDirectory(worker.resolve("health-modules"));if(execute(worker,modules,"health-modules",List.of(runtime.perl().toString(),script.toString()),r,runtime,manifest)!=0)throw new Unavailable("Exact original LOFTEE/BioPerl modules cannot load; dependent analysis skipped");
        var observed=object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(modules.resolve("stdout.log"),4194304)),4194304,100000));var loaded=object(observed.get("modules"));require(text(loaded.get("LoF.pm")).equals(runtime.source().resolve("LoF.pm").toString()),"Wrong original LOFTEE module loaded");var allowed=new HashMap<Path,NextflowFiles.Pin>();for(Group group:runtime.groups())for(var e:group.actual().entrySet())allowed.put(group.root().resolve(e.getKey()).toRealPath(),e.getValue());for(var e:loaded.entrySet()){Path file=Path.of(text(e.getValue())).toRealPath();require(allowed.containsKey(file),"Unprofiled Perl module loaded: "+e.getKey()+" -> "+file);}NextflowFiles.writeJson(stage.resolve("loaded-module-identities.json"),observed);
    }
    static int execute(Path worker,Path home,String identifier,List<String> command,LofteeWorkflowRequest.Request r,Prepared runtime,Map<String,Object> manifest)throws Exception {
        require(home.toAbsolutePath().normalize().startsWith(worker.toAbsolutePath().normalize()),"Original process output escaped owned worker");var baseline=CiceroWorkflowRuntime.footprint(worker,r.outputFiles());var builder=new ProcessBuilder(command).directory(home.toFile()).redirectOutput(home.resolve("stdout.log").toFile()).redirectError(home.resolve("stderr.log").toFile());builder.environment().clear();builder.environment().putAll(runtime.environment(home));var owned=new AtomicReference<Process>();var children=new ArrayList<ProcessHandle>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"loftee-owned-process-cleanup");Runtime.getRuntime().addShutdownHook(hook);Process process=null;long start=System.nanoTime();
        try{
            try{process=builder.start();}catch(IOException error){throw new Unavailable("Execution of the exact permitted original program is unavailable; dependent analysis skipped",error);}owned.set(process);process.getOutputStream().close();manifest.put("last_owned_original_pid",process.pid());
            while(!process.waitFor(50,TimeUnit.MILLISECONDS)){remember(process,children);limits(worker,baseline,start,r);}remember(process,children);limits(worker,baseline,start,r);return process.exitValue();
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw error;}finally{
            if(process!=null){var cleanup=stop(process,children);var receipt=new LinkedHashMap<String,Object>();receipt.put("id",identifier);receipt.put("command",command);receipt.put("owned_pid",process.pid());receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));receipt.put("owned_process_cleanup",cleanup);if(!process.isAlive())receipt.put("original_exit_code",process.exitValue());NextflowFiles.writeJson(home.resolve("process-receipt.json"),receipt);owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* shutdown owns cleanup */}require(cleanup.stream().noneMatch(p->Boolean.TRUE.equals(p.get("alive_after_cleanup"))),"Owned original process survived cleanup; no result admitted");}
            else{owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* shutdown owns cleanup */}}
        }
    }
    private static void limits(Path worker,Map<String,Long> baseline,long start,LofteeWorkflowRequest.Request r)throws IOException {require(System.nanoTime()-start<=TimeUnit.MILLISECONDS.toNanos(r.timeoutMillis()),"Original process exceeded timeout_millis; owned descendants will be stopped");long bytes=0;for(var e:CiceroWorkflowRuntime.footprint(worker,r.outputFiles()).entrySet()){long extra=Math.max(0,e.getValue()-baseline.getOrDefault(e.getKey(),0L));require(extra<=r.outputBytes()-bytes,"Original output growth exceeded maximum_output_bytes");bytes+=extra;}}
    static void finalCapacity(Path worker,LofteeWorkflowRequest.Request r)throws IOException{long bytes=0;for(long value:CiceroWorkflowRuntime.footprint(worker,r.outputFiles()).values()){require(value<=r.outputBytes()-bytes,"Complete original/native worker outputs exceed maximum_output_bytes");bytes+=value;}}
    static void publishedCapacity(Path stage,LofteeWorkflowRequest.Request r)throws IOException{long bytes=0;int files=0;for(String name:List.of("worker","results"))for(long value:CiceroWorkflowRuntime.footprint(stage.resolve(name),r.outputFiles()).values()){require(++files<=r.outputFiles()&&value<=r.outputBytes()-bytes,"Complete worker and published result outputs exceed maximum_output_bytes/files");bytes+=value;}}
    private static void remember(Process process,List<ProcessHandle> handles){process.descendants().forEach(p->{if(handles.stream().noneMatch(old->old.pid()==p.pid()))handles.add(p);});}
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> descendants){boolean interrupted=Thread.interrupted();remember(process,descendants);var handles=new ArrayList<>(descendants);Collections.reverse(handles);handles.add(process.toHandle());for(var p:handles)if(p.isAlive())p.destroy();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}for(var p:handles)if(p.isAlive())p.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}var receipt=new ArrayList<Map<String,Object>>();for(var p:handles)receipt.add(Map.of("pid",p.pid(),"alive_after_cleanup",p.isAlive()));if(interrupted)Thread.currentThread().interrupt();return receipt;}
}
