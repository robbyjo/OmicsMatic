/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Reuse the immutable base runtime; optional binaries have independent payload pins. */
final class GenomicsemPrepRuntime {
    private GenomicsemPrepRuntime() { }
    static final String RESOURCE="/org/jlinalg/omics/genomicsem-preparation/";
    static byte[] resource(String name)throws IOException {
        require(name.matches("[A-Za-z0-9_.-]+"),"Unsafe preparation resource name");try(var in=GenomicsemPrepRuntime.class.getResourceAsStream(RESOURCE+name)){require(in!=null,"Missing preparation resource: "+name);return in.readAllBytes();}
    }
    static Map<String,Object> metadata()throws IOException {byte[] raw=resource("SOURCE.json");require(sha(raw).equals(GenomicsemPrepSource.SOURCE_SHA),"Changed packaged preparation source descriptor");return object(GprofilerJson.read(raw));}
    static void sources(Path root,boolean compare)throws IOException {
        var descriptor=metadata();var base=GenomicsemRuntime.metadata();require(same(descriptor.get("base_source_descriptor"),pin(GenomicsemRuntime.resource("SOURCE.json"))),"Changed base source descriptor");
        for(var e:object(base.get("resource_files")).entrySet())require(same(e.getValue(),pin(GenomicsemRuntime.resource(e.getKey()))),"Changed base package resource: "+e.getKey());
        var resources=new LinkedHashMap<String,Object>(object(descriptor.get("resource_files")));resources.put("SOURCE.json",pin(resource("SOURCE.json")));
        for(var e:resources.entrySet()){
            byte[] raw=resource(e.getKey());require(same(pin(raw),e.getValue()),"Changed packaged preparation resource: "+e.getKey());Path target=root.resolve("source/"+e.getKey());
            if(compare)require(Arrays.equals(bytes(target,Math.max(1,raw.length)),raw),"Changed captured preparation source");else{Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);}
        }
    }
    static Map<String,Object> addons(Path extra,Path rscript)throws IOException {
        if(extra==null)return map("enabled",false);
        if(!Files.isDirectory(extra))throw new GenomicsemRuntime.Unavailable("Missing optional pinned Rmpfr/GMP library; use tools/literature/install_genomicsem_preparation_addons.py,or retain the dependent analysis skip: "+extra);
        require(extra.toRealPath().equals(extra.toAbsolutePath().normalize()),"Use a physical optional dependency directory");byte[] profile=resource("ADDONS.json");require(Arrays.equals(bytes(extra.resolve("PROFILE.json"),1048576),profile),"Optional dependency profile differs");
        var receipt=read(extra.resolve("INSTALLATION.json"));var expected=object(GprofilerJson.read(profile));require("PASS_PINNED_OPTIONAL_GENOMICSEM_PREPARATION_ADDONS".equals(receipt.get("status"))&&Objects.equals(expected.get("profile"),receipt.get("profile"))&&same(receipt.get("profile_identity"),pin(profile))&&same(receipt.get("rscript_identity"),pin(rscript)),"Unrecognized/mismatched optional dependency staging receipt");
        var payload=new TreeMap<String,Object>();for(Object p:list(expected.get("packages")))for(var e:object(object(p).get("files")).entrySet())require(payload.put(e.getKey(),e.getValue())==null,"Duplicate optional package member");require(same(payload,receipt.get("library_files")),"Forged/incomplete optional payload receipt");
        var found=new TreeSet<String>();Path library=extra.resolve("library");try(var stream=Files.walk(library)){
            for(Path file:stream.toList()){
                require(!Files.isSymbolicLink(file)&&file.toRealPath().equals(file.toAbsolutePath().normalize()),"Optional runtime reparse/symlink member");if(Files.isDirectory(file))continue;String name=library.relativize(file).toString().replace('\\','/');require(found.add(name)&&payload.containsKey(name)&&same(payload.get(name),pin(file)),"Changed/unexpected original optional package member: "+name);
            }
        }require(found.equals(payload.keySet()),"Incomplete optional dependency file closure");return receipt;
    }
    private static final class Probe {
        final Path root,home,extra,rscript;final String nonce;boolean acknowledged;Path rhome;Map<String,Object> observed;
        Probe(Path r,Path h,Path e,Path rs,String n){root=r;home=h;extra=e;rscript=rs;nonce=n;}
        List<Path> roots(){var paths=new ArrayList<>(List.of(home.resolve("library"),rhome.resolve("bin"),rhome.resolve("library")));if(extra!=null)paths.add(extra.resolve("library"));return paths;}
        Map<String,Object> normalize(Map<String,Object>snapshot)throws IOException {
            var out=new TreeMap<String,Object>();for(var e:object(snapshot.get("files")).entrySet()){
                Path path=Path.of(e.getKey());String name;
                if(extra!=null&&path.startsWith(extra.resolve("library")))name="addons/"+extra.resolve("library").relativize(path).toString().replace('\\','/');
                else if(path.startsWith(home.resolve("library")))name="library/"+home.resolve("library").relativize(path).toString().replace('\\','/');
                else{require(path.startsWith(rhome),"Observed file lies outside declared R roots");name="R_HOME/"+rhome.relativize(path).toString().replace('\\','/');}require(out.put(name,e.getValue())==null,"Normalized runtime collision");
            }return map("profile",GenomicsemPrepContracts.PROFILE,"files",out,"scope","Observed declared R/core/base/optional package files;not complete OS loader,vendor authenticity or host attestation");
        }
        void check()throws IOException {
            if(acknowledged||!Files.exists(root.resolve("worker/READY.json")))return;var ready=read(root.resolve("worker/READY.json"));require(nonce.equals(ready.get("nonce"))&&"READY_BEFORE_FIRST_ORIGINAL_CALL".equals(ready.get("status")),"Wrong original-call handshake");
            var actual=read(root.resolve("worker/runtime-probe.json"));require(nonce.equals(actual.get("nonce"))&&"4.6.1".equals(actual.get("R_version"))&&"x86_64-w64-mingw32".equals(actual.get("platform"))&&number(actual,"namespace_functions")==44,"Wrong actual original runtime profile");
            rhome=Path.of(text(actual.get("R_home"),"R_home")).toRealPath();require(rscript.toRealPath().startsWith(rhome.resolve("bin")),"R launcher is outside observed R home");
            for(Object value:list(actual.get("DLLs"))){String dll=text(value,"loaded DLL");if(dll.equals("base")){require(Files.isRegularFile(rhome.resolve("bin/x64/R.dll")),"Missing virtual base DLL backing file");continue;}Path file=Path.of(dll).toRealPath();require(file.startsWith(home.resolve("library"))||file.startsWith(rhome.resolve("bin"))||file.startsWith(rhome.resolve("library"))||extra!=null&&file.startsWith(extra.resolve("library")),"Loaded R DLL lies outside declared package/R roots");}
            GenomicsemRuntime.namespace(root,"namespace-before.tsv");observed=runtimeFiles(roots());write(root.resolve("runtime-before.json"),normalize(observed));Path partial=root.resolve("worker/ACK.partial");write(partial,map("nonce",nonce,"runtime_snapshot",pin(root.resolve("runtime-before.json")),"GenomicSEM_lavaan_namespace_functions",1056));Files.move(partial,root.resolve("worker/ACK.json"),StandardCopyOption.ATOMIC_MOVE);acknowledged=true;
        }
        void recheck()throws IOException {if(!acknowledged)return;var after=runtimeFiles(roots());require(same(observed,after),"Declared runtime bytes/addresses changed during original preparation");write(root.resolve("runtime-after.json"),normalize(after));}
    }
    static void execute(Path root,Path home,Path extra,Map<String,Object>request,GenomicsemProcess.Check inputs)throws Exception {
        var base=GenomicsemRuntime.identity(home);Path rscript=Path.of(text(base.get("rscript"),"Rscript"));var additional=addons(extra,rscript);String nonce=UUID.randomUUID().toString();
        write(root.resolve("worker/config.json"),map("root",root.toString(),"runtime_home",home.toString(),"runtime_addons",extra==null?null:extra.toString(),"request",request,"nonce",nonce));
        write(root.resolve("runtime-binding.json"),map("runtime_home",home.toString(),"installation",pin(home.resolve("INSTALLATION.json")),"rscript",rscript.toString(),"rscript_identity",pin(rscript),"runtime_addons",extra==null?null:extra.toString(),"addons_installation",extra==null?null:pin(extra.resolve("INSTALLATION.json")),"addons_profile",additional.get("profile")));
        var env=new LinkedHashMap<>(GenomicsemRuntime.environment(root,rscript,home.resolve("library")));if(extra!=null){String libraries=extra.resolve("library")+File.pathSeparator+home.resolve("library");env.put("R_LIBS",libraries);env.put("R_LIBS_USER",libraries);}
        var probe=new Probe(root,home,extra,rscript,nonce);var process=map();int code;
        try{code=GenomicsemProcess.execute(List.of(rscript.toString(),"--vanilla",root.resolve("source/worker.R").toString(),root.resolve("worker/config.json").toString()),env,root,request,process,()->{inputs.verify();probe.check();});}
        finally{try{probe.recheck();GenomicsemRuntime.identity(home);addons(extra,rscript);sources(root,true);}finally{write(root.resolve("process.json"),process);}}
        require(Files.isRegularFile(root.resolve("worker/worker-status.json")),"Original preparation terminated before a status was written;read worker stdout/stderr");var status=read(root.resolve("worker/worker-status.json"));
        if(code==3&&"SKIP3_DEPENDENT_ANALYSIS_SKIPPED".equals(status.get("status"))){require(number(status,"original_function_calls")<=1,"Invalid dependency-skip provenance");throw new GenomicsemRuntime.Unavailable(text(status.get("message"),"missing preparation prerequisite")+";a partial original attempt may have occurred and is retained");}
        require(code==0&&"ORIGINAL_PREPARATION_RETURNED_REVIEW_REQUIRED".equals(status.get("status"))&&number(status,"original_function_calls")==1&&probe.acknowledged,"Original preparation failed or its pre-call handshake was incomplete;read worker logs/status");GenomicsemRuntime.namespace(root,"namespace-after.tsv");
    }
}
