/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Explicit permitted-runtime binding. Missing/forbidden prerequisites skip analysis; changed bytes fail. */
final class MerquryRuntime {
    private MerquryRuntime() { }
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String message){super(message);}}
    record Context(Path root,Map<?,?> value,Map<?,?> inventory,Map<String,String> environment,Map<String,String> programs,Pin seal,String edition,String sourceMode,Path source) {
        void verify(Runnable check)throws IOException {
            require(seal.equals(pin(root.resolve("COMPLETED.json"),1048576,check)),"Saved Merqury runtime binding seal changed");verifyInventory(inventory,check);selections(value);MerquryProfile.source(source,edition,check);
        }
        Map<String,String> executionEnvironment()throws IOException {
            var env=new HashMap<>(environment);env.put("OMICS_MERQURY_CLASSPATH",MerquryBundle.producerPath().toString());env.put("PYTHONDONTWRITEBYTECODE","1");env.put("LC_ALL","C");env.put("LANG","C");return Map.copyOf(env);
        }
        Map<String,Object> workerContext()throws IOException{return map("environment",executionEnvironment(),"bash",programs.get("bash"),"meryl",programs.get("meryl"),"source_mode",sourceMode);}
    }
    static void platform()throws Unavailable {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Merqury/Meryl execution and binary verification require Linux x86_64 Java. On Windows run Linux Java inside WSL");
    }
    static Path bind(Path request,Path supplied,Runnable check)throws IOException {
        platform();var raw=object(json(request,1048576));keys(raw,"schema_version","edition","source_mode","source","python","environment","roots");integer(raw.get("schema_version"),1,1);
        String edition=text(raw.get("edition")),mode=text(raw.get("source_mode"));MerquryProfile.selection(edition,mode);Path source=MerquryRequest.absolute(raw.get("source")),python=MerquryRequest.absolute(raw.get("python"));
        if(!Files.isDirectory(source)||!Files.isExecutable(python))throw new Unavailable("Original source or permitted Linux Python unavailable; install the named prerequisites where permitted and bind a NEW runtime");
        MerquryProfile.source(source,edition,check);var environment=object(raw.get("environment"));keys(environment,"PATH","LD_LIBRARY_PATH","R_LIBS_USER","JAVA_HOME");environmentPaths(environment);var env=new HashMap<String,String>();for(var entry:environment.entrySet())env.put(text(entry.getKey()),text(entry.getValue()));
        for(String root:strings(raw.get("roots")))if(!Files.isDirectory(MerquryRequest.absolute(root)))throw new Unavailable("Complete permitted runtime directory unavailable: "+root);
        Path root=supplied.toAbsolutePath().normalize();require(root.equals(supplied.toAbsolutePath())&&root.getParent()!=null,"Use a NEW canonical runtime binding path");directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Runtime binding exists; use a NEW path");Files.createDirectory(root);
        try {
            writeJson(root.resolve("request.json"),raw);Files.writeString(root.resolve("WARNINGS.md"),warnings(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);for(String name:List.of("bind_runtime.py","TdfDump.java"))Files.write(root.resolve(name),MerquryProfile.resource(name),StandardOpenOption.CREATE_NEW);
            var limits=new NextflowProcess.Limits(1800000,8589934592L,1800,268435456,4096,5368709120L,10000,64,256,4294967296L,100);
            NextflowProcess.executeSession(List.of(python.toString(),"-I","-S","-B",root.resolve("bind_runtime.py").toString(),root.toString()),env,root.resolve("process"),root,root,limits,()->MerquryProfile.source(source,edition,check),check);
            var value=object(json(root.resolve("runtime.json"),1048576));binaryIdentity(value,edition,check);verifyInventory(object(json(root.resolve("inventory.json"),134217728)),check);
            var files=manifest(tree(root,10000,5368709120L,268435456,check));writeJson(root.resolve("manifest.json"),map("schema_version",1,"producer",MerquryBundle.producer(),"files",files,"analysis_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_MERQURY_RUNTIME_BINDING_ONLY","manifest",pin(root.resolve("manifest.json"),16777216,check),"analysis_admitted",false));open(root,edition,check);return root;
        }catch(IOException|RuntimeException|Error e){
            boolean unavailable=e instanceof Unavailable;String detail=e.toString();Path diagnostic=root.resolve("BINDING-FAILURE.json");
            if(Files.exists(diagnostic))try{var failure=object(json(diagnostic,1048576));unavailable|=Boolean.TRUE.equals(failure.get("unavailable"));detail+="; "+text(failure.get("error"));}catch(IOException invalid){e.addSuppressed(invalid);}
            if(!Files.exists(root.resolve("FAILURE.json")))writeJson(root.resolve("FAILURE.json"),map("status",unavailable?"SKIP3_DEPENDENT_MERQURY_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_MERQURY_RUNTIME_BINDING","error",detail,"analysis_admitted",false,"required_action",MerquryBundle.ACTION));
            if(unavailable&&!(e instanceof Unavailable))throw new Unavailable(detail+". Inspect "+diagnostic+"; dependent analysis was skipped");throw e;
        }
    }
    static Context open(Path root,String requested,Runnable check)throws IOException {
        platform();if(!Files.exists(root))throw new Unavailable("Prepared runtime binding absent: "+root+". Run merqury-tools --bind with permitted complete prerequisites");directory(root);
        if(!Files.exists(root.resolve("COMPLETED.json")))throw new Unavailable("Runtime setup incomplete; analysis skipped. Inspect binding diagnostics and prepare a NEW binding after prerequisites are available");
        require(!Files.exists(root.resolve("FAILURE.json"))&&!Files.exists(root.resolve("BINDING-FAILURE.json")),"Failed runtime binding cannot be admitted");
        var done=object(json(root.resolve("COMPLETED.json"),1048576));require("COMPLETE_MERQURY_RUNTIME_BINDING_ONLY".equals(done.get("status"))&&Boolean.FALSE.equals(done.get("analysis_admitted")),"Invalid runtime completion status");
        require(pin(root.resolve("manifest.json"),16777216,check).equals(fingerprint(done.get("manifest"),16777216)),"Runtime binding manifest changed");var m=object(json(root.resolve("manifest.json"),16777216));require(MerquryBundle.producer().equals(fingerprint(m.get("producer"),1000000000L)),"Restore the exact runtime-binding producing JAR; do not refresh historical seals");
        var actual=new TreeMap<>(manifest(tree(root,10000,5368709120L,268435456,check)));actual.remove("manifest.json");actual.remove("COMPLETED.json");require(NextflowJson.json(actual).equals(NextflowJson.json(m.get("files"))),"Complete runtime binding evidence changed");
        for(String name:List.of("bind_runtime.py","TdfDump.java")){byte[] helper=MerquryProfile.resource(name);require(new Pin(helper.length,sha(helper)).equals(pin(root.resolve(name),16777216,check)),"Binding helper differs from producer: "+name);}
        require(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings()),"Runtime warning/action text differs");
        var v=object(json(root.resolve("runtime.json"),1048576));keys(v,"schema_version","status","edition","source_mode","source","environment","programs","selections","host","analysis_admitted","software_installed");
        require(requested.equals(v.get("edition"))&&"COMPLETE_MERQURY_RUNTIME_BINDING_ONLY".equals(v.get("status"))&&Boolean.FALSE.equals(v.get("software_installed"))&&Boolean.FALSE.equals(v.get("analysis_admitted")),"Runtime edition or scope differs");
        String mode=text(v.get("source_mode"));MerquryProfile.selection(requested,mode);var host=object(v.get("host"));require("linux".equals(host.get("os"))&&System.getProperty("os.version").equals(host.get("kernel")),"Binding host/kernel changed; prepare a NEW permitted binding");
        binaryIdentity(v,requested,check);var env=new HashMap<String,String>();object(v.get("environment")).forEach((k,x)->env.put(k.toString(),x.toString()));var programs=new TreeMap<String,String>();object(v.get("programs")).forEach((k,x)->programs.put(k.toString(),x.toString()));
        var context=new Context(root,v,object(json(root.resolve("inventory.json"),134217728)),Map.copyOf(env),Map.copyOf(programs),pin(root.resolve("COMPLETED.json"),1048576,check),requested,mode,Path.of(text(v.get("source"))));context.verify(check);return context;
    }
    private static void binaryIdentity(Map<?,?> context,String edition,Runnable check)throws IOException {
        var programs=object(context.get("programs"));var expected=object(MerquryProfile.edition(edition).get("meryl"));
        for(String name:List.of("meryl","meryl-lookup")){Path path=MerquryRequest.absolute(programs.get(name));if(!Files.exists(path)||!Files.isExecutable(path))throw new Unavailable("Required permitted original program unavailable: "+path);require(pin(path,268435456,check).equals(fingerprint(expected.get(name),268435456)),"Original "+name+" binary differs from explicit locked edition");}
        Path igv=MerquryRequest.absolute(programs.get("igvtools")).getParent().resolve("lib/igv.jar");require(pin(igv,268435456,check).equals(fingerprint(MerquryProfile.edition(edition).get("igv_2_19_8"),268435456)),"Original IGV2.19.8 TDF implementation differs from locked edition");
    }
    private static void selections(Map<?,?> value)throws IOException {
        environmentPaths(object(value.get("environment")));
        var selected=object(value.get("selections"));var programs=object(value.get("programs"));require(selected.keySet().equals(programs.keySet()),"Complete program selection population differs");String search=text(object(value.get("environment")).get("PATH"));
        for(Object key:programs.keySet()) {
            String name=text(key);Path found=null;for(String prefix:search.split(":",-1)){require(!prefix.isEmpty(),"Empty runtime PATH entry refused");Path path=MerquryRequest.absolute(prefix).resolve(name);if(Files.isRegularFile(path)&&Files.isExecutable(path)){found=path;break;}}
            if(found==null)throw new Unavailable("Permitted required Merqury program unavailable: "+name);require(found.toString().equals(selected.get(key))&&found.toRealPath().toString().equals(programs.get(key)),"Runtime search/interpreter selection changed: "+name);
        }
    }
    private static void environmentPaths(Map<?,?> environment)throws IOException {
        var directories=new ArrayList<String>();directories.add(text(environment.get("JAVA_HOME")));directories.add(text(environment.get("R_LIBS_USER")));
        String libraries=text(environment.get("LD_LIBRARY_PATH"));if(!libraries.isEmpty())directories.addAll(Arrays.asList(libraries.split(":",-1)));
        for(String value:directories){Path path=MerquryRequest.absolute(value);if(!Files.isDirectory(path))throw new Unavailable("Declared permitted runtime directory unavailable: "+path+". Supply it if required or remove an unused library-search entry in a NEW binding request");require(path.toRealPath().equals(path),"Declare canonical Java/R/library directories and preserve their parent aliases: "+path);}
    }
    static void verifyInventory(Map<?,?> inventory,Runnable check)throws IOException {
        keys(inventory,"roots","files","nodes","complete_bytes");var expected=object(inventory.get("nodes"));require(expected.size()<=250000,"Runtime census exceeds capacity");var actual=new HashSet<String>();
        for(String directory:strings(inventory.get("roots"))) {
            Path root=MerquryRequest.absolute(directory);if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Required permitted runtime root missing: "+root);directory(root);
            Files.walkFileTree(root,new SimpleFileVisitor<>(){
                @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {node(path,expected,actual,check);return FileVisitResult.CONTINUE;}
                @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {node(path,expected,actual,check);return FileVisitResult.CONTINUE;}
            });
        }
        for(String name:strings(inventory.get("files")))node(MerquryRequest.absolute(name),expected,actual,check);
        var missing=new TreeSet<String>();for(Object name:expected.keySet())if(!actual.contains(name))missing.add(text(name));require(actual.equals(expected.keySet()),"Complete runtime node population differs; missing declared nodes="+missing.stream().limit(20).toList());
    }
    private static String warnings(){return "MERQURY RUNTIME BINDING: WARNINGS AND REQUIRED ACTION\n\nThis operation reads and hashes the declared runtime, source, R library populations, Python standard library, Java, IGV, selected PATH programs and their dynamic dependencies. It installs nothing and performs no user scientific analysis. Declare complete permitted runtime roots and existing search directories. Required missing or forbidden programs/libraries skip dependent analysis. An unused dangling metadata link (for example Debian JDK src.zip) is retained as an unresolved link; it is not followed or repaired, and verification requires it to remain unchanged and unresolved. Required program and dynamic-library resolution checks still apply. Newly resolvable links, extra directories, changed program selections, source, bytes or modes require investigation and a NEW binding. Preserve original/failed bindings and the exact producing JAR; never refresh historical seals.\n\n"+MerquryBundle.ACTION+"\n";}
    private static void node(Path path,Map<?,?> expected,Set<String> found,Runnable check)throws IOException {
        check.run();String name=path.toString();if(!found.add(name))return;require(found.size()<=250000&&expected.containsKey(name),"Extra runtime member: "+name);var row=object(expected.get(name));String kind=text(row.get("kind"));
        if(kind.equals("symlink"))require(Files.isSymbolicLink(path)&&Files.readSymbolicLink(path).toString().equals(row.get("target"))&&path.toRealPath().toString().equals(row.get("real_path")),"Runtime link changed: "+name);
        else if(kind.equals("dangling-symlink"))require(Files.isSymbolicLink(path)&&!Files.exists(path)&&Files.readSymbolicLink(path).toString().equals(row.get("target")),"Previously unused dangling runtime link changed or became resolvable: "+name);
        else if(kind.equals("directory"))require(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)&&mode(path)==integer(row.get("mode"),0,0777),"Runtime directory/mode changed: "+name);
        else {if(!Files.exists(path))throw new Unavailable("Required permitted runtime file missing: "+name);require(kind.equals("file")&&mode(path)==integer(row.get("mode"),0,0777)&&pin(path,100000000000L,check).equals(fingerprint(row,100000000000L)),"Runtime file bytes/mode changed: "+name);}
    }
}
