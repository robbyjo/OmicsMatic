/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SignacPeaksContracts.value;

/** Guarded source/ABI profile and bounded original MACS processes; not host/DLL authentication. */
final class SignacPeaksRuntime {
    private SignacPeaksRuntime() { }
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String message){super(message);}}
    record Bound(Path home,Map<String,Object> fingerprints,Map<String,Object> metadata) { }
    static byte[] resource(String name)throws IOException{try(var in=SignacPeaksRuntime.class.getResourceAsStream("/org/jlinalg/omics/"+name)){if(in==null)throw new IOException("Missing packaged peak source resource: "+name);return in.readAllBytes();}}
    static Map<String,Object> profile()throws IOException{return object(SimpleJson.parse(new String(resource("signac-peaks/SOURCE-PROFILES.json"),StandardCharsets.UTF_8)),"source profile");}
    static void sources(Path root,boolean compare)throws IOException {
        var files=object(profile().get("files"),"source files");
        for(var e:files.entrySet()){String n=e.getKey();if(!n.matches("[A-Za-z0-9._-]+"))throw new IOException("Unsafe source resource name");byte[] raw=resource("signac-peaks/"+n);if(!sha(raw).equals(object(e.getValue(),"source pin").get("sha256")))throw new IOException("Packaged source hash mismatch");write(root.resolve("source/"+n),raw,compare);}
        write(root.resolve("source/SOURCE-PROFILES.json"),resource("signac-peaks/SOURCE-PROFILES.json"),compare);write(root.resolve("source/runtime-probe.py"),resource("signac_peaks_runtime.py"),compare);
    }
    private static void write(Path file,byte[] bytes,boolean compare)throws IOException{if(compare){if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!Arrays.equals(bytes,Files.readAllBytes(file)))throw new IOException("Captured peak source differs: "+file.getFileName());}else{Files.createDirectories(file.getParent());Files.write(file,bytes,StandardOpenOption.CREATE_NEW);}}
    static String sha(byte[] raw){return SeuratDifferentialRuntime.sha(raw);}
    static Map<String,Object> pin(Path file)throws IOException{if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require regular runtime/input file: "+file);return Map.of("bytes",Files.size(file),"sha256",FollowupSupport.hash(file));}
    static Map<String,String> environment(){return Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1","PYTHONDONTWRITEBYTECODE","1","PYTHONNOUSERSITE","1");}
    static RsemProcess.Limits limits(Map<String,Object> r){return new RsemProcess.Limits(value(r,"timeout_seconds")*1000,value(r,"maximum_memory_bytes"),value(r,"maximum_cpu_seconds"),value(r,"maximum_output_bytes"),256,value(r,"maximum_output_bytes"),(int)value(r,"maximum_files"),(int)value(r,"maximum_processes"),(int)value(r,"maximum_threads"),value(r,"maximum_sampled_rss_bytes"),50);}
    static Bound bind(Path root,Path parent,Map<String,Object> r,RsemProcess.Check inputs)throws IOException {
        if(!System.getProperty("os.name").equals("Linux"))throw new Unavailable("Original MACS execution requires Linux Java, including WSL on Windows; dependent peak analysis skipped");
        Path home=resolve(parent,text(r,"macs2_home"));if(!Files.isDirectory(home)||!Files.isExecutable(home.resolve("bin/python"))||!Files.isRegularFile(home.resolve("bin/macs2")))throw new Unavailable("Required private MACS/Python home is unavailable or forbidden: "+home);
        Map<String,Object> before=fingerprints(home);Path probe=root.resolve("runtime-probe");List<String> args=List.of(home.resolve("bin/python").toString(),"-I","-B","-X","pycache_prefix="+root.resolve("unused-pycache"),root.resolve("source/runtime-probe.py").toString());
        RsemProcess.executeOwned(args,environment(),probe,limits(r),()->{inputs.verify();if(!same(before,fingerprints(home)))throw new IOException("MACS runtime changed during admission");},()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();});
        var metadata=read(probe.resolve("process.stdout"));if("SKIP3_DEPENDENCY_UNAVAILABLE".equals(metadata.get("status")))throw new Unavailable("Required MACS/Python package/native dependency unavailable: "+metadata.get("error"));
        var bound=new Bound(home,before,metadata);audit(bound,r);json(root.resolve("runtime-binding.json"),Map.of("home",home.toString(),"fingerprints",before,"metadata",metadata,"compiled_module_source_authentication",false,"whole_host_authentication",false));return bound;
    }
    static void audit(Bound bound,Map<String,Object> r)throws IOException {
        audit(bound.fingerprints,bound.metadata,bound.home.toString(),r);
    }
    static void audit(Map<String,Object> fingerprints,Map<String,Object> m,String home,Map<String,Object> r)throws IOException {
        var p=profile();var versions=object(m.get("versions"),"runtime versions");if(!p.get("required_python").equals(m.get("python"))||!r.get("macs2_version").equals(versions.get("MACS2"))||!"1.24.4".equals(versions.get("numpy")))throw new IOException("Incompatible original MACS/Python/numpy profile");
        String pkg=home+"/lib/python3.9/site-packages/MACS2";if(!pkg.equals(m.get("package"))||!home.equals(m.get("prefix"))||!text(m,"parser").startsWith(pkg+"/IO/Parser."))throw new IOException("Loaded MACS package/runtime prefix differs from the declared private home");
        var version=object(object(p.get("MACS2"),"MACS profiles").get(r.get("macs2_version")),"MACS source profile");var expected=object(version.get("python_files"),"Python source pins");var files=object(fingerprints.get("package_files"),"runtime package pins");Set<String> actual=new HashSet<>(),compiled=new HashSet<>();
        for(var e:files.entrySet()){if(e.getKey().endsWith(".py")||e.getKey().endsWith(".pxd")){actual.add(e.getKey());if(!Objects.equals(expected.get(e.getKey()),object(e.getValue(),"runtime file").get("sha256")))throw new IOException("Installed original MACS source differs: "+e.getKey());}else if(e.getKey().endsWith(".so"))compiled.add(e.getKey());else if(!e.getKey().endsWith(".pyc"))throw new IOException("Unadmitted runtime package member");}
        if(!actual.equals(expected.keySet())||!compiled.equals(new HashSet<>(strings(version.get("compiled_names"),"compiled modules",1,100))))throw new IOException("Original MACS source/module population differs");
        if(!Objects.equals(version.get("original_script_body_sha256"),fingerprints.get("script_body_sha256")))throw new IOException("Installed MACS script body differs from the original edition");
        for(Object raw:files.values()){var pin=object(raw,"runtime pin");integer(pin,"bytes",-1,0,100_000_000);if(!text(pin,"sha256").matches("[0-9a-f]{64}"))throw new IOException("Invalid runtime file digest");}
    }
    static Map<String,Object> fingerprints(Path home)throws IOException {
        Path pkg=home.resolve("lib/python3.9/site-packages/MACS2");if(!Files.isDirectory(pkg))throw new Unavailable("Required MACS package directory is unavailable: "+pkg);Map<String,Object> files=new TreeMap<>();
        try(var paths=Files.walk(pkg)){for(Path path:paths.toList()){if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;if(files.size()>=1000)throw new IOException("Excessive MACS runtime package members");files.put(pkg.relativize(path).toString().replace('\\','/'),pin(path));}}
        byte[] script=Files.readAllBytes(home.resolve("bin/macs2"));int split=0;while(split<script.length&&script[split]!='\n')split++;if(split==script.length)throw new IOException("Invalid original MACS script");return Map.of("package_files",files,"script",pin(home.resolve("bin/macs2")),"script_body_sha256",sha(Arrays.copyOfRange(script,split+1,script.length)),"python_real_path",home.resolve("bin/python").toRealPath().toString(),"python",pin(home.resolve("bin/python").toRealPath()),"venv",pin(home.resolve("pyvenv.cfg")));
    }
    static List<String> command(String home,String stage,Map<String,Object> r,List<String> inputs,String prefix,String out)throws IOException{var command=new ArrayList<>(List.of(home+"/bin/python","-I","-B","-X","pycache_prefix="+stage+"/unused-pycache",home+"/bin/macs2"));command.addAll(SignacPeaksContracts.arguments(r,inputs,prefix,out));return List.copyOf(command);}
    static void execute(Path root,Bound bound,Map<String,Object> r,List<String> inputs,String prefix,RsemProcess.Check integrity)throws IOException {
        Path output=root.resolve("macs/"+prefix);Files.createDirectory(output);var args=command(bound.home.toString(),root.toString(),r,inputs,prefix,output.toString());
        RsemProcess.executeOwned(args,environment(),root.resolve("process/"+prefix),limits(r),()->{integrity.verify();if(!same(bound.fingerprints,fingerprints(bound.home)))throw new IOException("Original MACS runtime changed");},()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();},List.of(root.resolve("prepared"),root.resolve("macs")));
    }
}
