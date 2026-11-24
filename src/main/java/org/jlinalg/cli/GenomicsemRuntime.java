/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Original source, binary payloads, namespace checks and a pre-fit runtime handshake. */
final class GenomicsemRuntime {
    private GenomicsemRuntime() { }
    static final class Unavailable extends IOException {@java.io.Serial private static final long serialVersionUID=1L;Unavailable(String m){super(m);}Unavailable(String m,Throwable t){super(m,t);} }
    static final String RESOURCE="/org/jlinalg/omics/genomicsem-workflow/";
    static byte[] resource(String name)throws IOException {
        require(!name.contains("..")&&!name.contains("/")&&!name.contains("\\"),"Unsafe GenomicSEM resource name");
        try(var in=GenomicsemRuntime.class.getResourceAsStream(RESOURCE+name)){require(in!=null,"Missing packaged original GenomicSEM resource: "+name);return in.readAllBytes();}
    }
    static Map<String,Object> metadata()throws IOException {byte[] raw=resource("SOURCE.json");require(sha(raw).equals(GenomicsemSource.SOURCE_SHA),"Changed packaged source descriptor");return object(GprofilerJson.read(raw));}
    static void sources(Path root,boolean compare)throws IOException {
        var names=object(metadata().get("resource_files"));var all=new LinkedHashMap<String,Object>(names);all.put("SOURCE.json",pin(resource("SOURCE.json")));
        for(var entry:all.entrySet()){
            String name=entry.getKey();byte[] raw=resource(name);require(same(pin(raw),entry.getValue()),"Changed packaged source resource: "+name);Path file=root.resolve("source/"+name);
            if(compare)require(Arrays.equals(bytes(file,Math.max(1,raw.length)),raw),"Changed captured source resource: "+name);else{Files.createDirectories(file.getParent());Files.write(file,raw,StandardOpenOption.CREATE_NEW);}
        }
    }
    static Map<String,Object> profile()throws IOException{return object(GprofilerJson.read(resource("PROFILE.json")));}
    static Map<String,Object> identity(Path home)throws IOException {
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original runtime profile requires Windows x86_64;Linux upstream R remains separately unqualified");
        if(!Files.isDirectory(home))throw new Unavailable("Missing staged private runtime;use tools/literature/install_genomicsem_runtime.py: "+home);
        require(home.toRealPath().equals(home.toAbsolutePath().normalize()),"Use a physical runtime directory");
        require(Arrays.equals(bytes(home.resolve("PROFILE.json"),8388608),resource("PROFILE.json")),"Staged runtime profile differs");
        var install=read(home.resolve("INSTALLATION.json"));require("STAGED_NOT_SCIENTIFICALLY_QUALIFIED".equals(install.get("status"))&&GenomicsemContracts.PROFILE.equals(install.get("profile")),"Unrecognized original runtime staging receipt");
        var pr=profile();checkFiles(home.resolve("source"),object(object(pr.get("upstream")).get("source_files")),true);
        var library=object(install.get("library_files"));checkFiles(home.resolve("library"),library,true);
        var binaries=object(GprofilerJson.read(resource("BINARY-PACKAGE-FILES.json")));require(binaries.size()==2865,"Changed original binary-package member count");
        for(var e:binaries.entrySet())require(same(e.getValue(),library.get(e.getKey())),"Installed binary-package payload differs from pinned upstream archive: "+e.getKey());
        for(String n:library.keySet())require(binaries.containsKey(n)||n.startsWith("GenomicSEM/")||n.startsWith("lavaan/"),"Unexpected runtime package member: "+n);
        Path rscript=Path.of(text(install.get("rscript"),"rscript")).toAbsolutePath().normalize();if(!Files.isRegularFile(rscript))throw new Unavailable("Restore exact permitted R4.6.1 Rscript.exe: "+rscript);
        require(same(pin(rscript),install.get("rscript_identity")),"Changed Rscript executable identity");return install;
    }
    private static void checkFiles(Path root,Map<String,Object> expected,boolean closed)throws IOException {
        require(root.toRealPath().equals(root.toAbsolutePath().normalize()),"Aliased runtime/source root");var found=new TreeSet<String>();
        try(var stream=Files.walk(root)){
            var it=stream.iterator();while(it.hasNext()){
                Path p=it.next();require(p.toRealPath().equals(p.toAbsolutePath().normalize())&&!Files.isSymbolicLink(p),"Runtime reparse/symlink member");if(Files.isDirectory(p))continue;
                String n=root.relativize(p).toString().replace('\\','/');require(found.add(n)&&expected.containsKey(n)&&same(pin(p),expected.get(n)),"Changed/unexpected original runtime/source member: "+n);
            }
        }if(closed)require(found.equals(expected.keySet()),"Incomplete runtime/source file closure");
    }
    static String unhex(String s)throws IOException {
        require(s.matches("(?:[0-9a-f]{2})*"),"Malformed original UTF-8 hexadecimal token");
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(HexFormat.of().parseHex(s))).toString();}catch(IllegalArgumentException e){throw new IOException("Invalid original hexadecimal token",e);}
    }
    static void namespace(Path root,String filename)throws IOException {
        var expected=object(GprofilerJson.read(resource("NAMESPACE-FILES.json")));Set<String> seen=new HashSet<>();
        try(var in=GenomicsemData.reader(root.resolve("worker/"+filename))){require("namespace\tname_hex\tcanonical_hex".equals(GenomicsemData.line(in,1024)),"Missing original namespace header");String line;
            while((line=GenomicsemData.line(in,16777216))!=null){String[] f=line.split("\t",-1);require(f.length==3,"Malformed namespace reference row");String key=f[0]+"/"+unhex(f[1]);require(expected.containsKey(key)&&seen.add(key),"Unexpected/duplicate original namespace function: "+key);require(same(pin(unhex(f[2]).getBytes(StandardCharsets.UTF_8)),expected.get(key)),"Original namespace function body/formals differ: "+key);}
        }require(seen.equals(expected.keySet()),"Incomplete original GenomicSEM/lavaan namespace snapshot");
    }
    static Map<String,String> environment(Path root,Path rscript,Path library)throws IOException {
        var result=new LinkedHashMap<String,String>();Set<String> allowed=Set.of("SYSTEMROOT","SYSTEMDRIVE","WINDIR","COMSPEC","PATHEXT","USERPROFILE","APPDATA","LOCALAPPDATA","OS","NUMBER_OF_PROCESSORS","PROCESSOR_ARCHITECTURE","PROGRAMFILES","PROGRAMW6432","COMMONPROGRAMFILES","ALLUSERSPROFILE","PROGRAMDATA");
        for(var e:System.getenv().entrySet())if(allowed.contains(e.getKey().toUpperCase(Locale.ROOT)))result.put(e.getKey(),e.getValue());
        Path tmp=root.resolve("worker/tmp");Files.createDirectory(tmp);String system=result.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase("SystemRoot")).map(Map.Entry::getValue).findFirst().orElse("C:\\Windows");
        result.put("PATH",rscript.getParent()+File.pathSeparator+rscript.getParent().resolve("x64")+File.pathSeparator+Path.of(system).resolve("System32"));
        result.putAll(Map.of("TEMP",tmp.toString(),"TMP",tmp.toString(),"LC_ALL","C","LANG","C","TZ","UTC","R_LIBS",library.toString(),"R_LIBS_USER",library.toString(),"R_LIBS_SITE","","R_PROFILE_USER","NUL","R_ENVIRON_USER","NUL"));
        for(String k:List.of("OPENBLAS_NUM_THREADS","OMP_NUM_THREADS","MKL_NUM_THREADS","NUMEXPR_NUM_THREADS","VECLIB_MAXIMUM_THREADS"))result.put(k,"1");return result;
    }
    private static final class Probe {
        final Path root,home,rscript;final Map<String,Object> request;final String nonce;boolean acknowledged;Map<String,Object> observed;Path rhome;
        Probe(Path r,Path h,Path rs,Map<String,Object> q,String n){root=r;home=h;rscript=rs;request=q;nonce=n;}
        void check()throws IOException {
            if(acknowledged||!Files.exists(root.resolve("worker/READY.json")))return;
            var ready=read(root.resolve("worker/READY.json"));require(nonce.equals(ready.get("nonce"))&&"READY_BEFORE_FIRST_ORIGINAL_FIT".equals(ready.get("status")),"Wrong pre-fit runtime handshake");
            var probe=read(root.resolve("worker/runtime-probe.json"));require(nonce.equals(probe.get("nonce"))&&"4.6.1".equals(probe.get("R_version"))&&"x86_64-w64-mingw32".equals(probe.get("platform"))&&number(probe,"namespace_functions")==44,"Wrong actual R/source profile");
            rhome=Path.of(text(probe.get("R_home"),"R_home")).toRealPath();require(rscript.toRealPath().startsWith(rhome.resolve("bin")),"Declared launcher does not belong to observed R home");
            for(Object dll:list(probe.get("DLLs"))){String declared=text(dll,"loaded DLL");if(declared.equals("base")){require(Files.isRegularFile(rhome.resolve("bin/x64/R.dll")),"Missing actual R core for the registered virtual base DLL");continue;}Path p=Path.of(declared).toRealPath();require(p.startsWith(home.resolve("library"))||p.startsWith(rhome.resolve("bin"))||p.startsWith(rhome.resolve("library")),"Loaded R DLL lies outside declared R/package roots");}
            namespace(root,"namespace-before.tsv");observed=runtimeFiles(List.of(home.resolve("library"),rhome.resolve("bin"),rhome.resolve("library")));write(root.resolve("runtime-before.json"),normalized(observed,home,rhome));
            var ack=map("nonce",nonce,"runtime_snapshot",pin(root.resolve("runtime-before.json")),"original_namespaces_checked",1056);Path temporary=root.resolve("worker/ACK.partial");write(temporary,ack);Files.move(temporary,root.resolve("worker/ACK.json"),StandardCopyOption.ATOMIC_MOVE);acknowledged=true;
        }
        void recheck()throws IOException {
            if(!acknowledged)return;var after=runtimeFiles(List.of(home.resolve("library"),rhome.resolve("bin"),rhome.resolve("library")));require(same(observed,after),"Declared R/core/package file bytes or addresses changed during original computation");write(root.resolve("runtime-after.json"),normalized(after,home,rhome));
        }
    }
    private static Map<String,Object> normalized(Map<String,Object> observed,Path home,Path rhome)throws IOException {
        var files=new TreeMap<String,Object>();for(var e:object(observed.get("files")).entrySet()){
            Path p=Path.of(e.getKey());String n;
            if(p.startsWith(home.resolve("library")))n="library/"+home.resolve("library").relativize(p).toString().replace('\\','/');
            else{require(p.startsWith(rhome),"Runtime file outside normalized roots");n="R_HOME/"+rhome.relativize(p).toString().replace('\\','/');}require(files.put(n,e.getValue())==null,"Normalized runtime path collision");
        }return map("profile",GenomicsemContracts.PROFILE,"files",files,"scope","Observed declared package/R files;not full OS loader or host attestation");
    }
    static void execute(Path root,Path home,Map<String,Object> request,GenomicsemProcess.Check inputs)throws Exception {
        var install=identity(home);Path rscript=Path.of(text(install.get("rscript"),"rscript"));String nonce=UUID.randomUUID().toString();write(root.resolve("worker/config.json"),map("root",root.toString(),"runtime_home",home.toString(),"request",request,"nonce",nonce));
        write(root.resolve("runtime-binding.json"),map("runtime_home",home.toString(),"installation",pin(home.resolve("INSTALLATION.json")),"rscript",rscript.toString(),"rscript_identity",pin(rscript)));
        var probe=new Probe(root,home,rscript,request,nonce);var process=map();int code;
        try{code=GenomicsemProcess.execute(List.of(rscript.toString(),"--vanilla",root.resolve("source/worker.R").toString(),root.resolve("worker/config.json").toString()),environment(root,rscript,home.resolve("library")),root,request,process,()->{inputs.verify();probe.check();});}
        finally{try{probe.recheck();identity(home);sources(root,true);}finally{write(root.resolve("process.json"),process);}}
        var status=read(root.resolve("worker/worker-status.json"));
        if(code==3&&"SKIP3_DEPENDENT_ANALYSIS_SKIPPED".equals(status.get("status"))){require(number(status,"original_function_calls")==0,"Dependency skip followed original function execution");throw new Unavailable(text(status.get("message"),"missing dependency"));}
        require(code==0&&"ORIGINAL_CALL_RETURNED_REVIEW_REQUIRED".equals(status.get("status"))&&number(status,"original_function_calls")==1&&probe.acknowledged,"Original GenomicSEM failed or did not pass its pre-fit handshake;read original worker logs/status");namespace(root,"namespace-after.tsv");
    }
}
