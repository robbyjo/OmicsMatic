/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Explicit installed original-runtime binding; never installs software. */
final class DiffbindRuntime {
    private DiffbindRuntime() { }
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String text){super(text);}}
    record Context(Path root,Map<?,?> value,Map<?,?> inventory,Map<String,String> environment,Map<?,?> programs,Pin seal) {
        void verify(Runnable check)throws IOException {
            require(seal.equals(pin(root.resolve("COMPLETED.json"),1048576,check)),"DiffBind binding seal changed");
            try {MerquryRuntime.verifyInventory(inventory,check);}catch(MerquryRuntime.Unavailable e){throw new Unavailable(e.getMessage());}
            selections(value);source(Path.of(text(value.get("source"))),check);
        }
        Map<String,String> executionEnvironment() {
            var env=new HashMap<>(environment);String home=System.getenv("HOME");if(home!=null)env.put("HOME",home);
            env.put("LC_ALL","C.UTF-8");env.put("LANG","C.UTF-8");env.put("OPENBLAS_NUM_THREADS","1");env.put("OMP_NUM_THREADS","1");env.put("PYTHONDONTWRITEBYTECODE","1");
            return Map.copyOf(env);
        }
        void loadedMaps(List<String> lines)throws IOException {
            var nodes=object(inventory.get("nodes"));for(String line:lines){String[] fields=line.trim().split("\\s+",6);if(fields.length!=6||!fields[1].contains("x")||!fields[5].startsWith("/"))continue;
                require(!fields[5].endsWith(" (deleted)"),"Loaded executable/library was deleted or replaced");String real=Path.of(fields[5]).toRealPath().toString();require(nodes.containsKey(real),"Active native library is outside the bound census: "+real+". Prepare a NEW complete binding; retain this attempt");}
        }
    }
    static byte[] resource(String name)throws IOException {
        try(var in=DiffbindRuntime.class.getResourceAsStream("/org/jlinalg/omics/diffbind/"+name)){if(in==null)throw new IOException("Missing packaged DiffBind resource: "+name);return in.readAllBytes();}
    }
    static Map<?,?> profile()throws IOException {return object(SimpleJson.parse(utf8(resource("profile.json"))));}
    static void platform()throws Unavailable {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("DiffBind execution/verification requires Linux x86_64 Java. Use Linux Java inside WSL on Windows");
    }
    static void source(Path source,Runnable check)throws IOException {
        if(!Files.isDirectory(source))throw new Unavailable("Original DiffBind3.24.0 source unavailable: "+source);
        var expected=object(profile().get("source_files"));var actual=tree(source,10000,268435456,134217728,check);
        require(actual.keySet().equals(expected.keySet()),"Complete original DiffBind source population differs");
        for(var row:actual.entrySet())require(row.getValue().kind().equals("file")&&row.getValue().pin().equals(fingerprint(expected.get(row.getKey()),134217728)),"Original source changed: "+row.getKey());
    }
    static Map<String,String> environment(Object raw)throws IOException {
        var value=object(raw);keys(value,"PATH","LD_LIBRARY_PATH","R_LIBS_USER","R_LIBS_SITE","R_MAKEVARS_USER","R_ENVIRON_USER","R_PROFILE_USER","JAVA_HOME");
        var env=new TreeMap<String,String>();for(var row:value.entrySet()){String key=text(row.getKey()),v=text(row.getValue());require(v.chars().noneMatch(c->c<32||c==127),"Environment controls refused");env.put(key,v);}
        require(env.get("R_LIBS_SITE").isEmpty(),"Use an empty explicit R_LIBS_SITE");
        for(String key:List.of("PATH","LD_LIBRARY_PATH","R_LIBS_USER","JAVA_HOME"))for(String entry:env.get(key).split(":",-1)){Path p=DiffbindRequest.absolute(entry);if(!Files.isDirectory(p))throw new Unavailable("Permitted runtime search directory unavailable: "+p);}
        for(String key:List.of("R_MAKEVARS_USER","R_ENVIRON_USER","R_PROFILE_USER")){Path p=DiffbindRequest.absolute(env.get(key));if(!Files.isRegularFile(p))throw new Unavailable("Explicit empty R startup file unavailable: "+p);require(Files.size(p)==0,"Use empty owned R startup files");}
        return Map.copyOf(env);
    }
    static Path bind(Path request,Path supplied,Runnable check)throws IOException {
        platform();var raw=object(json(request,1048576));keys(raw,"schema_version","source","python","environment","roots");integer(raw.get("schema_version"),1,1);
        Path original=DiffbindRequest.absolute(raw.get("source")),python=DiffbindRequest.absolute(raw.get("python"));source(original,check);var env=environment(raw.get("environment"));
        if(!Files.isExecutable(python))throw new Unavailable("Permitted Python interpreter unavailable: "+python);
        Path root=newRoot(supplied);for(String entry:strings(raw.get("roots"))){Path p=DiffbindRequest.absolute(entry);if(!Files.isDirectory(p))throw new Unavailable("Declared complete runtime root unavailable: "+p);require(!root.startsWith(p)&&!p.startsWith(root),"Binding output must be separate from runtime roots");}
        Files.createDirectory(root);
        try {
            writeJson(root.resolve("request.json"),raw);for(String name:List.of("profile.json","health.R","bind_runtime.py"))Files.write(root.resolve(name),resource(name),StandardOpenOption.CREATE_NEW);
            Files.writeString(root.resolve("WARNINGS.md"),DiffbindBundle.ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var run=new HashMap<>(env);String home=System.getenv("HOME");if(home!=null)run.put("HOME",home);run.put("OMICS_DIFFBIND_JAVA_PID",Long.toString(ProcessHandle.current().pid()));
            // Exercise the same Java PNG decoder before recording launcher maps.
            var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_ARGB);var png=new ByteArrayOutputStream();require(javax.imageio.ImageIO.write(image,"png",png)&&javax.imageio.ImageIO.read(new ByteArrayInputStream(png.toByteArray()))!=null,"Java PNG health check failed");
            var limits=new NextflowProcess.Limits(1800000,17179869184L,1800,268435456,4096,5368709120L,20000,64,512,8589934592L,100);
            NextflowProcess.executeSessionEnvironment(List.of(python.toString(),"-I","-S","-B",root.resolve("bind_runtime.py").toString(),root.toString()),run,root.resolve("process"),root,root,limits,()->source(original,check),check);
            Map<?,?> v=object(json(root.resolve("runtime.json"),1048576));selections(v);
            try {MerquryRuntime.verifyInventory(object(json(root.resolve("inventory.json"),134217728)),check);}catch(MerquryRuntime.Unavailable e){throw new Unavailable(e.getMessage());}
            writeJson(root.resolve("manifest.json"),map("producer",producer(),"files",manifest(tree(root,20000,5368709120L,268435456,check)),"analysis_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_DIFFBIND_RUNTIME_BINDING_ONLY","manifest",pin(root.resolve("manifest.json"),134217728,check),"analysis_admitted",false));return root;
        }catch(IOException|RuntimeException|Error e){
            boolean unavailable=e instanceof Unavailable;Path failure=root.resolve("BINDING-FAILURE.json");if(Files.exists(failure))unavailable|=Boolean.TRUE.equals(object(json(failure,1048576)).get("unavailable"));
            writeJson(root.resolve("FAILURE.json"),map("status",unavailable?"SKIP3_DEPENDENT_DIFFBIND_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_DIFFBIND_RUNTIME_BINDING","error",e.toString(),"analysis_admitted",false,"required_action",DiffbindBundle.ACTION));
            if(unavailable&&!(e instanceof Unavailable))throw new Unavailable(e.getMessage()+". Inspect "+failure);throw e;
        }
    }
    static Path newRoot(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();require(root.equals(supplied.toAbsolutePath())&&root.getParent()!=null,"Use a NEW canonical directory");directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Output already exists; choose a NEW path");return root;
    }
    static Context open(Path root,Runnable check)throws IOException {
        platform();if(!Files.isDirectory(root)||!Files.exists(root.resolve("COMPLETED.json")))throw new Unavailable("Completed permitted runtime binding unavailable: "+root+"; run diffbind-tools --bind once prerequisites are installed");directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"))&&!Files.exists(root.resolve("BINDING-FAILURE.json")),"Failed binding cannot be used");
        var done=object(json(root.resolve("COMPLETED.json"),1048576));require("COMPLETE_DIFFBIND_RUNTIME_BINDING_ONLY".equals(done.get("status")),"Invalid binding completion");
        require(pin(root.resolve("manifest.json"),134217728,check).equals(fingerprint(done.get("manifest"),134217728)),"Binding manifest changed");var m=object(json(root.resolve("manifest.json"),134217728));require(producer().equals(fingerprint(m.get("producer"),1000000000L)),"Restore exact binding-producing JAR; never refresh historical hashes");
        var actual=new TreeMap<>(manifest(tree(root,20000,5368709120L,268435456,check)));actual.remove("manifest.json");actual.remove("COMPLETED.json");require(NextflowJson.json(actual).equals(NextflowJson.json(m.get("files"))),"Complete binding evidence changed");
        for(String name:List.of("profile.json","health.R","bind_runtime.py")){byte[] b=resource(name);require(pin(root.resolve(name),16777216,check).equals(new Pin(b.length,sha(b))),"Binding helper differs from producer");}
        var v=object(json(root.resolve("runtime.json"),1048576));var host=object(v.get("host"));require(System.getProperty("os.version").equals(host.get("kernel")),"Host/kernel changed; prepare a NEW binding");
        var context=new Context(root,v,object(json(root.resolve("inventory.json"),134217728)),environment(v.get("environment")),object(v.get("programs")),pin(root.resolve("COMPLETED.json"),1048576,check));context.verify(check);return context;
    }
    static void selections(Map<?,?> v)throws IOException {
        var selections=object(v.get("selections"));var programs=object(v.get("programs"));require(selections.keySet().equals(programs.keySet()),"Selected program population changed");String search=text(object(v.get("environment")).get("PATH"));
        for(Object key:programs.keySet()){
            Path selected=null;for(String part:search.split(":",-1)){Path p=DiffbindRequest.absolute(part).resolve(text(key));if(Files.isRegularFile(p)&&Files.isExecutable(p)){selected=p;break;}}
            if(selected==null)throw new Unavailable("Required permitted program unavailable: "+key);require(selected.toString().equals(selections.get(key))&&selected.toRealPath().toString().equals(programs.get(key)),"Program selection changed: "+key);
        }
    }
    static Path producerPath()throws IOException {
        try{Path path=Path.of(DiffbindRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!path.getFileName().toString().endsWith(".jar"))throw new Unavailable("Execute and retain the packaged producing JAR");return path;}catch(java.net.URISyntaxException e){throw new IOException(e);}
    }
    static Pin producer()throws IOException{return pin(producerPath(),1000000000L,()->{});}
}
