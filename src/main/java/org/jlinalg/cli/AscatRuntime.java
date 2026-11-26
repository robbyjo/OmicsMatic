/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Read-only source/runtime binding. No download, installation or user model fitting. */
final class AscatRuntime {
    static final String ACTION="WARNINGS AND REQUIRED ACTION: inspect WARNINGS.md, OUTPUT-AUDIT.json, SOURCE-WARNINGS.json and original process stdout/stderr. Require COMPLETED.json and a complete fresh ascat-tools --verify replay using the producing JAR and unchanged permitted runtime. Missing/forbidden prerequisites produce SKIP3 and skip the dependent analysis. Invalid or changed input/runtime, failed model/QC, overflow, cancellation and resource exhaustion produce FAIL2 with no complete result. Retain all attempts, correct the named cause and retry NEW paths. Original ASCAT fits models; Java audits complete input/output populations and the original segment/objective functions. No author recipe, version or default is inferred, and no whole package or paper reproduction is admitted.\n";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String text){super(text);}}
    private AscatRuntime() { }
    record Context(Path root,Map<?,?> value,Map<?,?> inventory,Map<String,String> environment,Map<?,?> programs,Pin seal,String profile) {
        void verify(Runnable check)throws IOException {
            require(seal.equals(pin(root.resolve("COMPLETED.json"),1048576,check)),"ASCAT binding seal changed");
            try{MerquryRuntime.verifyInventory(inventory,check);}catch(MerquryRuntime.Unavailable e){throw new Unavailable(e.getMessage());}
            selections(value);source(Path.of(text(value.get("source"))),profile,check);
            counter(value.get("counter"),profile,true,check);
        }
        Map<String,String> executionEnvironment() {
            var env=new HashMap<>(environment);String home=System.getenv("HOME");if(home!=null)env.put("HOME",home);
            env.put("LC_ALL","C.UTF-8");env.put("LANG","C.UTF-8");env.put("OPENBLAS_NUM_THREADS","1");env.put("OMP_NUM_THREADS","1");env.put("PYTHONDONTWRITEBYTECODE","1");return Map.copyOf(env);
        }
        String alleleCounter()throws IOException {
            if(value.get("counter")==null)throw new Unavailable("Original alleleCounter is not present in this binding. Matrix analysis remains available; prepare a NEW binding with the permitted counter to use HTS/targeted preparation");
            String launcher=text(object(value.get("counter")).get("launcher"));require(launcher.matches("[A-Za-z0-9_./-]+"),"Original ASCAT shell preparation requires a binding path without spaces or shell metacharacters");return launcher;
        }
        void loadedMaps(List<String> lines)throws IOException {
            var nodes=object(inventory.get("nodes"));for(String line:lines){String[] fields=line.trim().split("\\s+",6);if(fields.length!=6||!fields[1].contains("x")||!fields[5].startsWith("/"))continue;
                require(!fields[5].endsWith(" (deleted)"),"Loaded ASCAT dependency was deleted or replaced");require(nodes.containsKey(Path.of(fields[5]).toRealPath().toString()),"Active native library is outside the complete bound census; prepare a NEW complete binding");}
        }
    }
    static byte[] resource(String name)throws IOException {
        try(var stream=AscatRuntime.class.getResourceAsStream("/org/jlinalg/omics/ascat/"+name)){if(stream==null)throw new IOException("Missing packaged ASCAT resource: "+name);return stream.readAllBytes();}
    }
    static Map<?,?> profiles()throws IOException{return object(SimpleJson.parse(utf8(resource("profiles.json"))));}
    static Map<?,?> profile(String id)throws IOException {
        var all=profiles();var definitions=object(all.get("profiles"));require(definitions.containsKey(id),"Select a source-qualified original ASCAT/R profile");var source=object(definitions.get(id));var copy=new LinkedHashMap<String,Object>();source.forEach((key,value)->copy.put(key.toString(),value));copy.put("profile_id",id);copy.put("counter_profile",all.get("alleleCounter"));return Collections.unmodifiableMap(copy);
    }
    static void platform()throws Unavailable {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original ASCAT execution/replay requires Linux x86_64 Java. On Windows use Linux Java within WSL");
    }
    static void source(Path path,String profile,Runnable check)throws IOException {sourceFiles(path,object(profile(profile).get("source_files")),"ASCAT",check);}
    private static void sourceFiles(Path path,Map<?,?> expected,String label,Runnable check)throws IOException {
        if(!Files.isDirectory(path))throw new Unavailable("Original unmodified "+label+" source is unavailable: "+path);
        var actual=tree(path,20000,1073741824,268435456,check);require(actual.keySet().equals(expected.keySet()),"Complete original "+label+" source population differs");
        for(var row:actual.entrySet())require(row.getValue().kind().equals("file")&&row.getValue().pin().equals(fingerprint(expected.get(row.getKey()),268435456)),"Original "+label+" source differs: "+row.getKey());
    }
    private static Map<String,String> environment(Object raw)throws IOException {
        var value=object(raw);keys(value,"PATH","LD_LIBRARY_PATH","R_LIBS_USER","R_LIBS_SITE","R_MAKEVARS_USER","R_ENVIRON_USER","R_PROFILE_USER","JAVA_HOME");var env=new TreeMap<String,String>();
        for(var row:value.entrySet()){String key=text(row.getKey()),v=text(row.getValue());require(v.chars().noneMatch(c->c<32||c==127),"Environment control characters refused");env.put(key,v);}
        require(env.get("R_LIBS_SITE").isEmpty(),"Declare an empty R_LIBS_SITE");
        for(String key:List.of("PATH","LD_LIBRARY_PATH","R_LIBS_USER","JAVA_HOME"))for(String entry:env.get(key).split(":",-1))if(!Files.isDirectory(AscatRequest.absolute(entry)))throw new Unavailable("Permitted original runtime directory unavailable: "+entry);
        for(String key:List.of("R_MAKEVARS_USER","R_ENVIRON_USER","R_PROFILE_USER")){Path path=AscatRequest.absolute(env.get(key));if(!Files.isRegularFile(path))throw new Unavailable("Explicit empty R startup file is unavailable: "+path);require(Files.size(path)==0,"Use empty owned R startup files");}
        return Map.copyOf(env);
    }
    private static void counter(Object raw,String id,boolean bound,Runnable check)throws IOException {
        if(raw==null)return;var selected=profile(id);require("3.2.0".equals(selected.get("ASCAT")),"Legacy matrix bindings cannot ignore a3.2 preparation counter");
        var value=object(raw);if(bound)keys(value,"source","executable","environment","launcher");else keys(value,"source","executable","environment");
        sourceFiles(AscatRequest.absolute(value.get("source")),object(object(selected.get("counter_profile")).get("source_files")),"alleleCounter",check);
        Path executable=AscatRequest.absolute(value.get("executable"));if(!Files.isRegularFile(executable)||!Files.isExecutable(executable))throw new Unavailable("Permitted original alleleCounter executable is unavailable: "+executable);
        var env=object(value.get("environment"));keys(env,"LD_LIBRARY_PATH","HTS_PATH");for(Object rawKey:env.keySet()){String key=text(rawKey);for(String entry:text(env.get(key)).split(":",-1))if(!Files.isDirectory(AscatRequest.absolute(entry)))throw new Unavailable("Required isolated alleleCounter dependency directory unavailable: "+entry);}
        if(bound){Path launcher=AscatRequest.absolute(value.get("launcher"));require(Files.isRegularFile(launcher)&&Files.isExecutable(launcher),"Bound counter launcher is missing");}
    }
    static Path newRoot(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();require(root.equals(supplied.toAbsolutePath())&&root.getParent()!=null,"Use a NEW canonical output directory");directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Output exists; choose a NEW path");return root;
    }
    static Path bind(Path request,Path supplied,Runnable check)throws IOException {
        platform();var raw=object(json(request,16777216));keys(raw,"schema_version","profile","source","python","environment","roots","counter");integer(raw.get("schema_version"),1,1);String id=AscatRequest.scalar(raw.get("profile"));var selected=profile(id);
        Path original=AscatRequest.absolute(raw.get("source")),python=AscatRequest.absolute(raw.get("python"));source(original,id,check);counter(raw.get("counter"),id,false,check);var env=environment(raw.get("environment"));if(!Files.isExecutable(python))throw new Unavailable("Permitted Python interpreter unavailable: "+python);
        Path root=newRoot(supplied);for(String entry:strings(raw.get("roots"))){Path path=AscatRequest.absolute(entry);if(!Files.isDirectory(path))throw new Unavailable("Declared complete runtime root unavailable: "+path);require(!root.startsWith(path)&&!path.startsWith(root),"Binding evidence must be separate from runtime roots");}
        Files.createDirectory(root);
        try {
            writeJson(root.resolve("request.json"),raw);writeJson(root.resolve("profile.json"),selected);for(String name:List.of("health.R","bind_runtime.py"))Files.write(root.resolve(name),resource(name),StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve("WARNINGS.md"),ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var run=new HashMap<>(env);String home=System.getenv("HOME");if(home!=null)run.put("HOME",home);run.put("OMICS_ASCAT_JAVA_PID",Long.toString(ProcessHandle.current().pid()));
            var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_ARGB);var buffer=new ByteArrayOutputStream();require(javax.imageio.ImageIO.write(image,"png",buffer)&&javax.imageio.ImageIO.read(new ByteArrayInputStream(buffer.toByteArray()))!=null,"Java PNG decoder health check failed");
            var limits=new NextflowProcess.Limits(1800000,17179869184L,1800,268435456,4096,5368709120L,20000,64,512,8589934592L,100);
            NextflowProcess.executeSessionEnvironment(List.of(python.toString(),"-I","-S","-B",root.resolve("bind_runtime.py").toString(),root.toString()),run,root.resolve("process"),root,root,limits,()->{source(original,id,check);counter(raw.get("counter"),id,false,check);},check);
            var v=object(json(root.resolve("runtime.json"),16777216));require(id.equals(v.get("profile")),"Runtime profile differs");selections(v);counter(v.get("counter"),id,true,check);
            try{MerquryRuntime.verifyInventory(object(json(root.resolve("inventory.json"),134217728)),check);}catch(MerquryRuntime.Unavailable e){throw new Unavailable(e.getMessage());}
            writeJson(root.resolve("manifest.json"),map("producer",producer(),"profile",id,"files",manifest(tree(root,20000,5368709120L,268435456,check)),"analysis_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_ASCAT_RUNTIME_BINDING_ONLY","manifest",pin(root.resolve("manifest.json"),134217728,check),"analysis_admitted",false));return root;
        }catch(IOException|RuntimeException|Error error){
            boolean unavailable=error instanceof Unavailable;Path diagnostic=root.resolve("BINDING-FAILURE.json");if(Files.exists(diagnostic))unavailable|=Boolean.TRUE.equals(object(json(diagnostic,1048576)).get("unavailable"));
            writeJson(root.resolve("FAILURE.json"),map("status",unavailable?"SKIP3_DEPENDENT_ASCAT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_ASCAT_RUNTIME_BINDING","error",error.toString(),"analysis_admitted",false,"required_action",ACTION));if(unavailable&&!(error instanceof Unavailable))throw new Unavailable(error.getMessage()+". Inspect "+diagnostic);throw error;
        }
    }
    static Context open(Path root,String id,Runnable check)throws IOException {
        platform();if(!Files.isDirectory(root)||!Files.exists(root.resolve("COMPLETED.json")))throw new Unavailable("Completed permitted ASCAT binding unavailable: "+root+". Use ascat-tools --bind after prerequisites are installed");directory(root);require(!Files.exists(root.resolve("FAILURE.json"))&&!Files.exists(root.resolve("BINDING-FAILURE.json")),"Failed runtime binding cannot be used");
        var done=object(json(root.resolve("COMPLETED.json"),1048576));require("COMPLETE_ASCAT_RUNTIME_BINDING_ONLY".equals(done.get("status")),"Invalid runtime binding completion");require(pin(root.resolve("manifest.json"),134217728,check).equals(fingerprint(done.get("manifest"),134217728)),"Runtime binding manifest changed");
        var manifest=object(json(root.resolve("manifest.json"),134217728));require(id.equals(manifest.get("profile"))&&producer().equals(fingerprint(manifest.get("producer"),1000000000L)),"Use the exact binding-producing JAR and profile; never refresh old seals");
        var actual=new TreeMap<>(NextflowFiles.manifest(tree(root,20000,5368709120L,268435456,check)));actual.remove("manifest.json");actual.remove("COMPLETED.json");require(NextflowJson.json(actual).equals(NextflowJson.json(manifest.get("files"))),"Complete binding evidence population/bytes changed");
        require(NextflowJson.json(profile(id)).equals(NextflowJson.json(json(root.resolve("profile.json"),16777216))),"Selected original profile differs from producer");
        for(String name:List.of("health.R","bind_runtime.py")){byte[] bytes=resource(name);require(pin(root.resolve(name),16777216,check).equals(new Pin(bytes.length,sha(bytes))),"Binding helper differs from producing JAR");}
        var value=object(json(root.resolve("runtime.json"),16777216));require(id.equals(value.get("profile"))&&System.getProperty("os.version").equals(object(value.get("host")).get("kernel")),"Host/kernel or profile changed; prepare a NEW binding");
        var context=new Context(root,value,object(json(root.resolve("inventory.json"),134217728)),environment(value.get("environment")),object(value.get("programs")),pin(root.resolve("COMPLETED.json"),1048576,check),id);context.verify(check);return context;
    }
    private static void selections(Map<?,?> value)throws IOException {
        var selections=object(value.get("selections"));var programs=object(value.get("programs"));require(selections.keySet().equals(programs.keySet()),"Selected standard program population changed");String search=text(object(value.get("environment")).get("PATH"));
        for(Object key:programs.keySet()){
            Path selected=null;for(String entry:search.split(":",-1)){Path candidate=AscatRequest.absolute(entry).resolve(text(key));if(Files.isRegularFile(candidate)&&Files.isExecutable(candidate)){selected=candidate;break;}}
            if(selected==null)throw new Unavailable("Required permitted runtime program unavailable: "+key);require(selected.toString().equals(selections.get(key))&&selected.toRealPath().toString().equals(programs.get(key)),"Program selection changed: "+key);
        }
    }
    static Path producerPath()throws IOException {
        try{Path path=Path.of(AscatRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!path.getFileName().toString().endsWith(".jar"))throw new Unavailable("Run and retain the complete source-built producing JAR");return path;}catch(java.net.URISyntaxException e){throw new IOException(e);}
    }
    static Pin producer()throws IOException{return pin(producerPath(),1000000000L,()->{});}
}
