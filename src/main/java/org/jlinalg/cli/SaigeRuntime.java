/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Original Linux R runtime boundary. Setup and installation are separate commands. */
final class SaigeRuntime {
    private SaigeRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String text){super(text);}
        Unavailable(String text,Throwable cause){super(text,cause);}
    }
    private record Lock(Map<?,?> values,String sha256) { }
    private static final Lock LOCK=load();
    private static final String MARKER=".jlinalg-saige.json",ENVIRONMENT="source/.pixi/envs/default";
    record Context(Path home,Path environment,Path rscript,String markerSha256,
                   Map<String,String> files,Map<String,String> links,Map<?,?> versions) {
        Context {files=Map.copyOf(files);links=Map.copyOf(links);versions=Map.copyOf(versions);}
        void verify()throws IOException {
            try {
                Context current=open(home);
                if(!markerSha256.equals(current.markerSha256)||!files.equals(current.files)||!links.equals(current.links))throw new IOException("SAIGE runtime changed during analysis;no produced result can be admitted");
            }catch(Unavailable failure){throw new IOException("SAIGE runtime/dependency changed during analysis;retain diagnostics and do not admit results",failure);}
        }
    }
    private static Lock load() {
        try(var input=SaigeRuntime.class.getResourceAsStream("/org/jlinalg/omics/saige-runtime-lock.json")) {
            if(input==null)throw new IOException("Bundled SAIGE identity unavailable");byte[] raw=input.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized SAIGE lock");return new Lock(object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString())),digest(raw));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static Context open(Path supplied)throws Unavailable {
        try {
            if(!System.getProperty("os.name","").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch","")))throw new IOException("Original SAIGE profile requires Ubuntu24.04 Linux x86_64;on Windows run Linux Java inside WSL");
            String release=Files.readString(Path.of("/etc/os-release"),StandardCharsets.UTF_8);
            if(!release.matches("(?s).*\nID=ubuntu(?:\r?\n|$).*")||!release.matches("(?s).*\nVERSION_ID=\"24\\.04\"(?:\r?\n|$).*"))throw new IOException("This admitted original SAIGE profile requires Ubuntu24.04");
            Path home=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(home)||Files.isSymbolicLink(home)||!home.toRealPath().equals(home))throw new IOException("SAIGE home/ancestor is missing,linked or aliased");
            Path marker=home.resolve(MARKER);byte[] markerBytes=small(marker,32*1048576);String markerHash=digest(markerBytes);Map<?,?> metadata=object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(markerBytes)).toString()));
            if(!LOCK.values.get("profile").equals(metadata.get("profile"))||!LOCK.sha256.equals(metadata.get("runtime_lock_sha256"))||!home.toString().equals(metadata.get("home"))||!LOCK.values.get("source_commit").equals(metadata.get("source_commit")))throw new IOException("SAIGE admission profile,lock,source or home differs");
            if(!FollowupSupport.hash(member(home,"runtime-lock.json")).equals(LOCK.sha256)||!FollowupSupport.hash(member(home,"source/pixi.lock")).equals(LOCK.values.get("environment_lock_sha256")))throw new IOException("Installed runtime/environment lock differs");
            for(var e:object(LOCK.values.get("original_program_files")).entrySet())if(!e.getValue().equals(FollowupSupport.hash(member(home,"source/"+e.getKey()))))throw new IOException("Original SAIGE program differs: "+e.getKey());
            if(!LOCK.values.get("probe_sha256").equals(FollowupSupport.hash(member(home,"adapter/saige_probe.R"))))throw new IOException("Installed SAIGE probe differs");
            var source=object(metadata.get("source_build"));if(!Boolean.FALSE.equals(source.get("program_source_modified"))||!LOCK.values.get("original_reference_files").equals(source.get("original_source_files")))throw new IOException("Original source-build receipt differs");
            var archivePins=new LinkedHashMap<String,String>();for(var e:object(LOCK.values.get("archives")).entrySet())archivePins.put(string(e.getKey()),string(object(e.getValue()).get("sha256")));if(!archivePins.equals(metadata.get("archives_sha256")))throw new IOException("SAIGE source archive receipt differs");
            Path environment=home.resolve(ENVIRONMENT);if(!Files.isDirectory(environment)||!environment.toRealPath().equals(environment))throw new IOException("SAIGE environment prefix is missing or linked");
            Map<?,?> conda=object(LOCK.values.get("conda_packages"));if(!conda.equals(metadata.get("conda_packages")))throw new IOException("Installed package receipt differs from the authenticated lock");var packageNames=new HashSet<String>();
            try(var stream=Files.list(environment.resolve("conda-meta"))) {
                for(Path path:stream.filter(p->p.getFileName().toString().endsWith(".json")).toList()) {
                    Map<?,?> row=readMap(path,16*1048576);String name=string(row.get("name"));if(!packageNames.add(name)||!conda.containsKey(name))throw new IOException("Unknown/duplicate installed package: "+name);var fields=new LinkedHashMap<String,Object>();for(String key:List.of("version","build","sha256","url"))fields.put(key,row.get(key));if(!object(conda.get(name)).equals(fields))throw new IOException("Installed locked-package identity differs: "+name);
                }
            }
            if(!packageNames.equals(conda.keySet()))throw new IOException("Installed package family is incomplete");
            Map<?,?> inventory=object(metadata.get("inventory"));Map<String,String> files=hashes(object(inventory.get("files"))),links=strings(object(inventory.get("links")));if(files.size()<1000||files.size()+links.size()>120000)throw new IOException("Invalid SAIGE runtime inventory size");
            for(var e:files.entrySet())if(!FollowupSupport.hash(member(home,e.getKey())).equals(e.getValue()))throw new IOException("Runtime member changed: "+e.getKey());
            for(var e:links.entrySet()){Path path=path(home,e.getKey());if(!Files.isSymbolicLink(path)||!Files.readSymbolicLink(path).toString().equals(e.getValue()))throw new IOException("Runtime symbolic link changed: "+e.getKey());}
            var foundFiles=new HashSet<String>();var foundLinks=new HashSet<String>();int walked=0;
            for(Object root:(List<?>)LOCK.values.get("inventory_roots")) {
                Path directory=path(home,string(root));if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(directory))throw new IOException("Missing/linked inventory root");
                try(var walk=Files.walk(directory)){var iterator=walk.iterator();while(iterator.hasNext()){Path p=iterator.next();if(++walked>180000)throw new IOException("SAIGE inventory traversal bound exceeded");String name=home.relativize(p).toString();if(Files.isSymbolicLink(p))foundLinks.add(name);else if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))foundFiles.add(name);else if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular runtime member");}}
            }
            for(Object name:(List<?>)LOCK.values.get("inventory_files"))foundFiles.add(string(name));if(!foundFiles.equals(files.keySet())||!foundLinks.equals(links.keySet()))throw new IOException("SAIGE runtime contains missing or unlisted family members");
            Map<?,?> external=object(metadata.get("external_files"));if(external.isEmpty()||external.size()>4096||!external.containsKey("/etc/os-release"))throw new IOException("Invalid OS dependency family");
            for(var e:external.entrySet()){Path p=Path.of(string(e.getKey()));Map<?,?> item=object(e.getValue());if(!p.isAbsolute()||!p.normalize().equals(p)||p.startsWith(home)||!Files.isRegularFile(p)||!p.toRealPath().toString().equals(item.get("real_path"))||!FollowupSupport.hash(p).equals(item.get("sha256")))throw new IOException("OS runtime dependency changed: "+p);}
            Map<?,?> versions=object(metadata.get("versions"));if(!LOCK.values.get("R").equals(versions.get("R"))||!LOCK.values.get("R_packages").equals(versions.get("packages")))throw new IOException("Recorded R/package identity differs");
            Path rscript=member(home,ENVIRONMENT+"/bin/Rscript");if(!Files.isExecutable(rscript)||!markerHash.equals(FollowupSupport.hash(marker)))throw new IOException("Rscript is not executable or marker changed during verification");
            return new Context(home,environment,rscript,markerHash,files,links,versions);
        }catch(Exception failure){throw new Unavailable("Original SAIGE dependency unavailable: "+failure.getMessage(),failure);}
    }
    static void provenance(Context runtime,Map<String,Object> session) {
        session.put("upstream_tool","SAIGE");session.put("upstream_version",LOCK.values.get("version"));session.put("source_commit",LOCK.values.get("source_commit"));session.put("runtime_profile",LOCK.values.get("profile"));session.put("runtime_lock_sha256",LOCK.sha256);session.put("runtime_marker_sha256",runtime.markerSha256);session.put("runtime_home",runtime.home.toString());session.put("runtime_regular_files",runtime.files.size());session.put("runtime_links",runtime.links.size());session.put("runtime_scope",LOCK.values.get("scope"));session.put("runtime_versions",runtime.versions);
    }
    static int execute(Context runtime,Path script,List<String> arguments,Path work,String label,int timeout,Map<String,Object> session)throws Exception {
        if(!label.matches("[a-z0-9-]+")||timeout<1||timeout>86400)throw new IllegalArgumentException("Invalid process label/timeout");
        var command=new ArrayList<String>();command.add(runtime.rscript.toString());command.add("--vanilla");command.add(script.toAbsolutePath().toString());command.addAll(arguments);Path isolated=work.resolve(label+"-home"),temporary=work.resolve(label+"-tmp");Files.createDirectory(isolated);Files.createDirectory(temporary);
        ProcessBuilder builder=new ProcessBuilder(command).directory(work.toFile()).redirectOutput(work.resolve(label+".stdout").toFile()).redirectError(work.resolve(label+".stderr").toFile());Map<String,String> environment=builder.environment();environment.clear();environment.put("PATH",runtime.environment.resolve("bin")+":/usr/bin:/bin");environment.put("HOME",isolated.toString());environment.put("TMPDIR",temporary.toString());environment.put("LC_ALL","C.UTF-8");environment.put("LANGUAGE","en");environment.put("R_LIBS",runtime.environment.resolve("lib/R/library").toString());environment.put("R_LIBS_USER","/nonexistent");environment.put("R_LIBS_SITE","");for(String key:List.of("R_ENVIRON","R_ENVIRON_USER","R_PROFILE","R_PROFILE_USER"))environment.put(key,"/dev/null");for(String key:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS"))environment.put(key,"1");
        var call=new LinkedHashMap<String,Object>();call.put("command",command);call.put("timeout_seconds",timeout);call.put("stdout",label+".stdout");call.put("stderr",label+".stderr");var calls=new ArrayList<Object>();if(session.get("processes") instanceof List<?> list)calls.addAll(list);calls.add(call);session.put("processes",calls);long start=System.nanoTime();Process child;
        try{child=builder.start();}catch(IOException failure){call.put("launch_error",failure.toString());throw new Unavailable("Verified Rscript could not start;inspect its OS/library dependencies",failure);}
        call.put("pid",child.pid());boolean interrupted=false;
        try {if(!child.waitFor(timeout,TimeUnit.SECONDS)){call.put("timed_out",true);throw new IOException("Original SAIGE exceeded timeout;no completed result is admitted");}int code=child.exitValue();call.put("exit_code",code);return code;}
        catch(InterruptedException failure){interrupted=true;throw failure;}
        finally {call.put("seconds",(System.nanoTime()-start)/1e9);if(interrupted)Thread.interrupted();try{if(child.isAlive()){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();if(!child.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned SAIGE process did not terminate;inspect PID "+child.pid());}call.put("alive_after_cleanup",child.isAlive());}finally{if(interrupted)Thread.currentThread().interrupt();}}
    }
    static void probe(Context runtime,Path work,int timeout,Map<String,Object> session)throws Exception {
        int code=execute(runtime,runtime.home.resolve("adapter/saige_probe.R"),List.of(runtime.environment.toString()),work,"runtime-probe",Math.min(timeout,120),session);if(code!=0)throw new Unavailable("Original R/SAIGE version/library probe failed;inspect runtime-probe.stderr");Map<?,?> actual=readMap(work.resolve("runtime-probe.stdout"),2*1048576);
        if(!LOCK.values.get("R").equals(actual.get("R"))||!LOCK.values.get("R_packages").equals(actual.get("packages"))||!runtime.environment.resolve("lib/R/library").toString().equals(actual.get("library_paths")))throw new Unavailable("Actual original R/SAIGE package/library identity differs");
        for(Object value:(List<?>)actual.get("DLL_paths")){String name=string(value);if(name.equals("base")||name.isEmpty())continue;Path p=Path.of(name).toAbsolutePath().normalize();if(!p.startsWith(runtime.home)||!runtime.files.containsKey(runtime.home.relativize(p).toString()))throw new Unavailable("Original R loaded a DLL outside its admitted family: "+name);}
        session.put("actual_runtime_probe",actual);
    }
    static Path program(Context runtime,String name)throws IOException {
        if(!Set.of("createSparseGRM.R","step1_fitNULLGLMM.R","step2_SPAtests.R","step3_LDmat.R").contains(name))throw new IOException("Unexposed original SAIGE program");return member(runtime.home,"source/extdata/"+name);
    }
    static String recovery(){return "Required action: use Ubuntu24.04 Linux x86_64 and an admitted original SAIGE1.5.2/R4.5.2 private runtime. On Windows run Linux Java inside WSL. Where permitted run python3 tools/literature/install_saige.py --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install;add --allow-download only when permitted. A populated --package-cache can supply offline packages. Analysis never installs or invokes Pixi. If installation/dependencies are forbidden,absent or changed,SKIP the SAIGE-dependent analysis. Preserve changed homes and retained diagnostics;correct the named cause and rerun to a new output directory. Native unrelated-select requires no R and does require independently established complete graph coverage.";}
    private static Path path(Path home,String name)throws IOException {if(name.isEmpty()||name.startsWith("/")||name.contains("\\")||name.contains(":")||Arrays.stream(name.split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw new IOException("Unsafe SAIGE member name");Path p=home.resolve(name).normalize();if(!p.startsWith(home))throw new IOException("SAIGE member escapes home");return p;}
    private static Path member(Path home,String name)throws IOException {Path p=path(home,name);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p)||!p.toRealPath().equals(p))throw new IOException("Missing/linked SAIGE regular member: "+name);return p;}
    private static byte[] small(Path p,int max)throws IOException {if(!Files.isRegularFile(p)||Files.isSymbolicLink(p)||Files.size(p)>max)throw new IOException("Missing/oversized SAIGE metadata: "+p);return Files.readAllBytes(p);}
    private static Map<?,?> readMap(Path p,int max)throws IOException {return object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(small(p,max))).toString()));}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require a SAIGE metadata object");return map;}
    private static String string(Object value)throws IOException {if(!(value instanceof String text))throw new IOException("Require a SAIGE metadata string");return text;}
    private static Map<String,String> strings(Map<?,?> map)throws IOException {var result=new LinkedHashMap<String,String>();for(var e:map.entrySet())result.put(string(e.getKey()),string(e.getValue()));return result;}
    private static Map<String,String> hashes(Map<?,?> map)throws IOException {var result=strings(map);for(String value:result.values())if(!value.matches("[a-f0-9]{64}"))throw new IOException("Invalid SAIGE member digest");return result;}
    private static String digest(byte[] raw)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
}
