/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** New current-host core qualification; the older LOFTEE runtime/profile is never refreshed. */
final class VepToolsRuntime {
    private VepToolsRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record Prepared(Path environment,Path bioperl,Path helpers,List<LofteeWorkflowRuntime.Group> groups,Map<String,Object> host) {
        void verify()throws IOException{verifyHost(host);for(var group:groups)group.verify();}
        List<String> command(List<String> options){var command=new ArrayList<String>();command.add(environment.resolve("bin/perl").toString());command.add(environment.resolve("share/ensembl-vep-105.0-1/vep").toString());command.addAll(options);return command;}
        Map<String,String> environment(Path directory,Map<String,Object> request){return Map.of("PATH",helpers+":"+environment.resolve("bin"),"PERL5LIB",environment.resolve("share/ensembl-vep-105.0-1/modules")+":"+environment.resolve("share/ensembl-vep-105.0-1")+":"+bioperl,"LD_LIBRARY_PATH",environment.resolve("lib").toString(),"HOME",directory.toString(),"TMPDIR",directory.toString(),"LC_ALL","C","LANG","C","TZ","UTC","PERL_HASH_SEED",textValue(request,"perl_hash_seed"),"PERL_PERTURB_KEYS","0");}
    }
    private static String textValue(Map<String,Object> map,String key){return(String)map.get(key);}
    static byte[] resource(String name)throws IOException{try(var in=VepToolsRuntime.class.getResourceAsStream("/org/jlinalg/omics/vep105/"+name)){if(in==null)throw new Unavailable("Exact packaged VEP source/reference profile unavailable: "+name+". Build the complete qualified source or retain dependent skip");return in.readAllBytes();}}
    static List<String> sourceNames(Map<String,Object> r)throws IOException{var names=new ArrayList<>(List.of("NATIVE-CONTRACT.json","CONSEQUENCES105.json"));if(text(r,"backend").equals("ORIGINAL")){names.add("RUNTIME.json");if(text(object(r.get("reference"),"reference"),"kind").startsWith("human-cache"))names.add("REFERENCE.json");}return names;}
    static void sources(Path root,Map<String,Object> r,boolean compare)throws IOException{for(String name:sourceNames(r)){Path p=root.resolve("source/"+name);byte[] bytes=resource(name);if(compare){if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(bytes,Files.readAllBytes(p)))throw new IOException("Saved VEP packaged source/reference binding differs: "+name);}else Files.write(p,bytes,StandardOpenOption.CREATE_NEW);}}
    static Prepared prepare(Path root,Path parent,Map<String,Object> r,Map<String,Object> execution)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original VEP profile requires Linux x86_64; native CSQ processing and saved verification are portable");
        var binding=object(r.get("runtime"),"runtime");Map<String,Path> paths=new LinkedHashMap<>();for(String key:List.of("environment","bioperl","helpers")){Path path=resolve(parent,text(binding,key));if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact VEP "+key+" runtime directory unavailable: "+path);paths.put(key,path);}
        var profile=object(SimpleJson.parse(new String(resource("RUNTIME.json"),java.nio.charset.StandardCharsets.UTF_8)),"runtime profile");if(!VepToolsRequest.PROFILE.equals(profile.get("profile")))throw new IOException("VEP runtime profile identity differs");var host=object(profile.get("host_files"),"host_files");verifyHost(host);List<LofteeWorkflowRuntime.Group> groups=new ArrayList<>();
        for(String key:paths.keySet())groups.add(group(paths.get(key),object(object(profile.get(key),key).get("files"),key+" files")));
        for(Path path:paths.values())portable(path);for(String name:List.of("perl","samtools","bgzip","tabix","gzip","gunzip","zcat"))if(!Files.isExecutable(paths.get("environment").resolve("bin/"+name)))throw new IOException("Required exact VEP command is no longer executable: "+name);if(!Files.isExecutable(paths.get("helpers").resolve("which")))throw new IOException("Required exact which helper is not executable");
        var prepared=new Prepared(paths.get("environment"),paths.get("bioperl"),paths.get("helpers"),List.copyOf(groups),host);Map<String,Object> identities=new LinkedHashMap<>();for(var group:groups)identities.put(group.root().toString(),group.describe());json(root.resolve("runtime-bindings.json"),identities);execution.put("runtime_profile",profile.get("profile"));execution.put("runtime_admission_scope",profile.get("scope"));return prepared;
    }
    static void portable(Path path)throws IOException{if(!path.toAbsolutePath().normalize().toString().matches("/[A-Za-z0-9_./-]+"))throw new IOException("Original shell-backed VEP resources require portable ASCII paths; names/paths are not silently rewritten");}
    static void health(Path root,Prepared runtime,Map<String,Object> r,Map<String,Object> execution)throws IOException,InterruptedException {
        Path directory=Files.createDirectory(root.resolve("worker/health"));execute(root,directory,"vep-help",runtime.command(List.of("--help")),runtime,r,execution);String help=Files.readString(directory.resolve("stdout.log"));if(!help.contains("ensembl-vep")||!help.contains("105.0"))throw new Unavailable("Exact VEP105 help identity unavailable; restore qualified runtime or retain skip");
        Path script=directory.resolve("module-health.pl");Files.writeString(script,"use strict; use warnings; use JSON::PP; use Bio::EnsEMBL::VEP::Runner; use Bio::EnsEMBL::ApiVersion; print JSON::PP->new->canonical->encode({api_version=>Bio::EnsEMBL::ApiVersion::software_version(),loaded_modules=>\\%INC}),\"\\n\";\n",java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Path module=Files.createDirectory(root.resolve("worker/module-health"));execute(root,module,"api-module-health",List.of(runtime.environment().resolve("bin/perl").toString(),script.toString()),runtime,r,execution);var observed=object(SimpleJson.parse(Files.readString(module.resolve("stdout.log"))),"module health");if(integer(observed,"api_version",0,105,105)!=105)throw new IOException("Original Ensembl API is not105");Set<Path> allowed=new HashSet<>();for(var group:runtime.groups())for(String name:group.actual().keySet())allowed.add(group.root().resolve(name).toRealPath());Set<String> notLoaded=new TreeSet<>();for(var entry:object(observed.get("loaded_modules"),"original INC").entrySet()){
            Object value=entry.getValue();if(value==null&&Set.of("Sereal.pm","Sereal/Decoder.pm").contains(entry.getKey())){notLoaded.add(entry.getKey());continue;}
            if(!(value instanceof String name)||!allowed.contains(Path.of(name).toRealPath()))throw new IOException("Unprofiled original VEP Perl module loaded: "+entry.getKey()+" => "+value);
        }
        if(!notLoaded.equals(Set.of("Sereal.pm","Sereal/Decoder.pm")))throw new IOException("Qualified original optional-module absence differs");observed.put("not_loaded_optional_require_entries",new ArrayList<>(notLoaded));observed.put("null_INC_entries_are_loaded_modules",false);json(root.resolve("loaded-module-identities.json"),observed);
    }
    private static LofteeWorkflowRuntime.Group group(Path root,Map<String,Object> expected)throws IOException {
        var tree=LofteeRuntimeTree.snapshot(root);if(!tree.names().equals(expected.keySet()))throw new IOException("Exact VEP runtime tree population differs: "+root);Map<String,NextflowFiles.Pin> actual=new LinkedHashMap<>();Map<String,String> links=new LinkedHashMap<>();String baseline="__NO_PREFIX_ALIAS__";
        for(Object value:expected.values()){var row=object(value,"runtime file");if(row.containsKey("prefix_relocation")){baseline=text(object(row.get("prefix_relocation"),"prefix relocation"),"old_prefix");break;}}
        for(var entry:expected.entrySet()){
            String name=NextflowFiles.member(entry.getKey());var item=object(entry.getValue(),"runtime file");Path file=root.resolve(name);String kind=text(item,"kind");
            if(kind.equals("symlink")){String target=text(item,"target");if(target.startsWith(baseline+"/"))target=root+target.substring(baseline.length());if(!Files.isSymbolicLink(file)||!Files.readSymbolicLink(file).toString().equals(target)||!file.toRealPath().startsWith(root))throw new IOException("Exact VEP runtime link differs/escapes: "+name);links.put(name,target);continue;}
            if(!kind.equals("file")||Files.isSymbolicLink(file))throw new IOException("Require original regular VEP runtime bytes: "+name);var expectedPin=NextflowFiles.fingerprint(item,536870912);NextflowFiles.Pin value;
            if(item.containsKey("prefix_relocation")){var read=LofteeRuntimeTree.read(tree,file,536870912,true);byte[] normalized=LofteeRuntimePrefix.baseline(read.content(),object(item.get("prefix_relocation"),"prefix relocation"),root.toString());if(normalized.length!=expectedPin.bytes()||!NextflowFiles.sha(normalized).equals(expectedPin.sha256()))throw new IOException("VEP runtime differs outside exact allowed prefix fields: "+name);value=read.pin();}
            else{value=LofteeRuntimeTree.read(tree,file,expectedPin.bytes(),false).pin();if(!value.equals(expectedPin))throw new IOException("Exact VEP runtime bytes differ: "+name);}actual.put(name,value);
        }
        tree.verify();return new LofteeWorkflowRuntime.Group(root,Collections.unmodifiableMap(expected),Collections.unmodifiableMap(actual),Collections.unmodifiableMap(links),tree);
    }
    private static void verifyHost(Map<String,Object> expected)throws IOException {
        for(var entry:expected.entrySet()){Path file=Path.of(entry.getKey());var pin=NextflowFiles.fingerprint(entry.getValue(),536870912);if(!pin.equals(NextflowFiles.pin(file,pin.bytes(),()->{})))throw new IOException("New VEP core host dependency differs from qualified bytes: "+file);}
        if(!Path.of("/bin/sh").toRealPath().equals(Path.of("/usr/bin/dash"))||!Path.of("/lib64/ld-linux-x86-64.so.2").toRealPath().equals(Path.of("/usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2")))throw new IOException("Qualified shell/ELF interpreter aliases differ");
    }
    static void execute(Path root,Path directory,String id,List<String> command,Prepared runtime,Map<String,Object> r,Map<String,Object> execution)throws IOException,InterruptedException {
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(directory.resolve("stdout.log").toFile()).redirectError(directory.resolve("stderr.log").toFile());builder.environment().clear();builder.environment().putAll(runtime.environment(directory,r));var owned=new AtomicReference<Process>();List<ProcessHandle> children=new ArrayList<>();var receipt=new LinkedHashMap<String,Object>();receipt.put("id",id);receipt.put("command",command);receipt.put("environment",runtime.environment(directory,r));long start=System.nanoTime();Process process=null;long[] transientRaces={0};Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,List.of());},"vep-owned-worker-cleanup");Runtime.getRuntime().addShutdownHook(hook);
        try {
            try{process=builder.start();}catch(IOException e){throw new Unavailable("Exact permitted original VEP process could not start; restore runtime or retain skip",e);}owned.set(process);process.getOutputStream().close();receipt.put("owned_pid",process.pid());
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)){remember(process,children);if(System.nanoTime()-start>VepToolsRequest.number(r,"timeout_seconds")*1000000000L)throw new IOException("Original VEP timeout; no complete result");capacity(root,r,true,transientRaces);}
            remember(process,children);receipt.put("original_exit_code",process.exitValue());if(process.exitValue()!=0)throw new IOException("Original VEP exited "+process.exitValue()+"; inspect retained stdout/stderr/warning files");
        }finally{
            if(process!=null){var cleaned=stop(process,children);receipt.put("owned_process_cleanup",cleaned);receipt.put("elapsed_millis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));receipt.put("observed_disappearing_worker_temporaries",transientRaces[0]);json(directory.resolve("process-receipt.json"),receipt);owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}if(cleaned.stream().anyMatch(x->Boolean.TRUE.equals(x.get("alive_after_cleanup"))))throw new IOException("Owned VEP worker survived cleanup; no result admitted");}
            else{owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}}
        }
        capacity(root,r,false,new long[1]);
    }
    static void capacity(Path root,Map<String,Object> r,boolean active,long[] races)throws IOException {
        long[] bytes={0},files={0};for(String name:List.of("worker","results")){Path directory=root.resolve(name);if(!Files.exists(directory))continue;Files.walkFileTree(directory,new SimpleFileVisitor<>(){
            private void count()throws IOException{if(++files[0]>VepToolsRequest.number(r,"maximum_output_files"))throw new IOException("VEP output file capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException{count();if(Files.isSymbolicLink(p))throw new IOException("Unexpected VEP output directory alias");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException{count();try{if(!a.isRegularFile()||Files.isSymbolicLink(p))throw new IOException("Unexpected nonregular VEP output");try(var channel=Files.newByteChannel(p,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))){long n=channel.size();if(n>VepToolsRequest.number(r,"maximum_output_bytes")-bytes[0])throw new IOException("VEP output byte capacity exceeded");bytes[0]+=n;}}catch(NoSuchFileException e){if(!active)throw e;races[0]++;}return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFileFailed(Path p,IOException e)throws IOException{if(active&&e instanceof NoSuchFileException){races[0]++;return FileVisitResult.CONTINUE;}throw e;}
            @Override public FileVisitResult postVisitDirectory(Path p,IOException e)throws IOException{if(e!=null){if(active&&e instanceof NoSuchFileException)races[0]++;else throw e;}return FileVisitResult.CONTINUE;}
        });}
    }
    private static void remember(Process process,List<ProcessHandle> children){OwnedProcesses.descendants(process).forEach(p->{if(children.stream().noneMatch(old->old.pid()==p.pid()))children.add(p);});}
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> earlier){boolean interrupted=Thread.interrupted();var all=new LinkedHashMap<Long,ProcessHandle>();for(var p:earlier)all.put(p.pid(),p);OwnedProcesses.descendants(process).forEach(p->all.put(p.pid(),p));var handles=new ArrayList<>(all.values());Collections.reverse(handles);handles.add(process.toHandle());for(var p:handles)if(p.isAlive())p.destroy();long end=System.nanoTime()+2000000000L;while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<end)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}for(var p:handles)if(p.isAlive())p.destroyForcibly();end=System.nanoTime()+5000000000L;while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<end)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}if(interrupted)Thread.currentThread().interrupt();return handles.stream().map(p->Map.<String,Object>of("pid",p.pid(),"alive_after_cleanup",p.isAlive())).toList();}
}
