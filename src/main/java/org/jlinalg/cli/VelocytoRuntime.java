/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Explicit original source plus complete permitted Python/numerical dependency binding. */
final class VelocytoRuntime {
    private VelocytoRuntime() { }
    static final String ACTION="WARNINGS AND REQUIRED ACTION: inspect WARNINGS.md, INPUT-AUDIT.json, OUTPUT-AUDIT.json, every original stdout/stderr and any FAILURE.json. Require COMPLETED.json and a successful fresh velocyto-tools --verify using the exact producing JAR and unchanged runtime. Missing/forbidden prerequisites produce SKIP3 and skip dependent analysis: supply or install the named prerequisite where permitted, then use NEW binding/capture paths. Invalid or changed inputs/source/runtime, failed model/QC, cancellation or capacity exhaustion produce FAIL2 and no complete result: preserve every attempt, investigate the named cause and retry NEW paths. Commands install nothing. BAM/tag/annotation preparation, sample identities and actual counting mode require provenance; no RNA velocities or author recipe are inferred.";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String text){super(text);}}
    static byte[] resource(String name)throws IOException {
        try(var in=VelocytoRuntime.class.getResourceAsStream("/org/jlinalg/omics/velocyto01717/"+name)){if(in==null)throw new IOException("Packaged Velocyto resource missing: "+name);return in.readAllBytes();}
    }
    static Object profiles()throws IOException{return SimpleJson.parse(utf8(resource("profiles.json")));}
    static Map<?,?> profile(String id)throws IOException {
        var all=object(object(profiles()).get("profiles"));require(all.containsKey(id),"Select an explicit original Velocyto source profile");return object(all.get(id));
    }
    static Path absolute(Object raw)throws IOException {
        String text=text(raw);require(!text.isBlank()&&text.chars().noneMatch(c->c<32||c==127),"Use a literal absolute path");Path path=Path.of(text);require(path.isAbsolute()&&path.equals(path.normalize()),"Use a normalized absolute path");return path;
    }
    static Path newRoot(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();require(root.equals(supplied.toAbsolutePath())&&root.getParent()!=null,"Use a NEW canonical output directory");directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and choose a NEW directory");return root;
    }
    static void platform()throws Unavailable {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original Velocyto workflow execution and replay require Linux x86_64 Java; use Linux Java inside WSL on Windows. The native BAM preparation and count-matrix methods also run on Windows");
    }
    static void source(Path root,String id,Runnable check)throws IOException {
        if(!Files.isDirectory(root))throw new Unavailable("Required permitted original Velocyto source unavailable: "+root);
        var expected=object(profile(id).get("source_files"));var actual=tree(root,1000,104857600,104857600,check);require(expected.keySet().equals(actual.keySet()),"Original Velocyto source-view file population changed");
        for(var entry:actual.entrySet())require(entry.getValue().kind().equals("file")&&entry.getValue().pin().equals(fingerprint(expected.get(entry.getKey()),104857600)),"Original Velocyto source differs: "+entry.getKey());
    }
    static Path producerPath()throws IOException {
        try{Path path=Path.of(VelocytoRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!path.getFileName().toString().endsWith(".jar"))throw new Unavailable("Build and retain the complete source-built executable JAR");return path;}catch(java.net.URISyntaxException e){throw new IOException(e);}
    }
    static Pin producer()throws IOException{return pin(producerPath(),1000000000,()->{});}
    record Context(Path root,Map<?,?> value,Map<?,?> inventory,Pin seal,String profile,Map<Path,Pin> anchors) {
        Context{anchors=Map.copyOf(anchors);}
        Context anchored(Map<Path,Pin> more){var merged=new HashMap<>(anchors);merged.putAll(more);return new Context(root,value,inventory,seal,profile,merged);}
        void verify(Runnable check)throws IOException {
            require(seal.equals(pin(root.resolve("COMPLETED.json"),1048576,check)),"Saved Velocyto runtime seal changed");
            try{MerquryRuntime.verifyInventory(inventory,check);source(absolute(value.get("source")),profile,check);}
            catch(MerquryRuntime.Unavailable|Unavailable e){throw new IOException("Previously bound Velocyto runtime/source is missing or changed. Restore exact bytes or prepare a NEW binding; preserve the original evidence. "+e.getMessage(),e);}
            for(var entry:anchors.entrySet())require(pin(entry.getKey(),1000000000,check).equals(entry.getValue()),"Immutable Velocyto execution metadata/producer changed: "+entry.getKey());
        }
        Map<String,String> environment()throws IOException {
            var saved=object(value.get("environment"));keys(saved,"PATH","LD_LIBRARY_PATH");var env=new HashMap<String,String>();for(var entry:saved.entrySet())env.put(text(entry.getKey()),text(entry.getValue()));
            env.put("LC_ALL","C");env.put("LANG","C");env.put("PYTHONDONTWRITEBYTECODE","1");env.put("PYTHONNOUSERSITE","1");for(String name:List.of("OPENBLAS_NUM_THREADS","OMP_NUM_THREADS","NUMEXPR_NUM_THREADS","NUMBA_NUM_THREADS"))env.put(name,"1");env.put("MPLBACKEND","Agg");return Map.copyOf(env);
        }
        void separate(Path output)throws IOException {
            require(!root.startsWith(output)&&!output.startsWith(root),"Velocyto output overlaps binding evidence");
            for(Object item:object(inventory.get("nodes")).keySet()){Path path=absolute(item);require(!path.startsWith(output)&&!output.startsWith(path),"Velocyto output overlaps a bound runtime/source/library node");}
        }
    }
    static Path bind(Path request,Path supplied,Runnable check)throws IOException {
        platform();if(!Files.isReadable(request))throw new Unavailable("Velocyto runtime request unavailable: "+request);
        byte[] raw=bytes(request,16777216);var spec=object(SimpleJson.parse(utf8(raw)));keys(spec,"schema_version","profile","source","python","samtools","library_paths","roots");integer(spec.get("schema_version"),1,1);String id=text(spec.get("profile"));profile(id);
        Path source=absolute(spec.get("source")),python=absolute(spec.get("python"));source(source,id,check);if(!Files.isExecutable(python))throw new Unavailable("Required permitted private Python executable unavailable: "+python);
        var roots=new ArrayList<Path>(List.of(source));for(String item:strings(spec.get("roots"))){Path path=absolute(item);if(!Files.isDirectory(path))throw new Unavailable("Required complete runtime root unavailable: "+path);roots.add(path);}
        var libraryPaths=object(spec.get("library_paths"));keys(libraryPaths,"python","samtools");String library=text(libraryPaths.get("python"));for(Object value:libraryPaths.values())for(String item:text(value).split(":",-1))if(!item.isEmpty()){Path path=absolute(item);if(!Files.isDirectory(path))throw new Unavailable("Required permitted runtime library directory unavailable: "+path);roots.add(path);}
        Path samtools=absolute(spec.get("samtools"));if(!Files.isExecutable(samtools))throw new Unavailable("Required permitted original Samtools helper unavailable: "+samtools);require(pin(samtools,104857600,check).equals(fingerprint(profile(id).get("samtools_binary"),104857600)),"Samtools binary differs from selected tested original edition");
        Path root=newRoot(supplied);require(!root.toString().contains(":"),"Binding directory must not contain the PATH separator colon");for(Path path:roots)require(!path.startsWith(root)&&!root.startsWith(path),"Binding output overlaps original runtime/source");Files.createDirectory(root);
        try {
            Files.write(root.resolve("request.json"),raw,StandardOpenOption.CREATE_NEW);writeJson(root.resolve("profile.json"),profile(id));Files.write(root.resolve("bind_runtime.py"),resource("bind_runtime.py"),StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve("WARNINGS.md"),ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var env=Map.of("PATH","/usr/bin:/bin","LD_LIBRARY_PATH",library,"OPENBLAS_NUM_THREADS","1","OMP_NUM_THREADS","1","NUMEXPR_NUM_THREADS","1","NUMBA_NUM_THREADS","1","MPLBACKEND","Agg");
            var limits=new NextflowProcess.Limits(1800000,8589934592L,1800,268435456,4096,2147483648L,20000,64,256,4294967296L,100);
            NextflowProcess.executeSession(List.of(python.toString(),"-I","-B",root.resolve("bind_runtime.py").toString(),root.toString()),env,root.resolve("process"),root,root,limits,()->{source(source,id,check);require(Arrays.equals(raw,bytes(request,16777216)),"Runtime request changed during binding");},check);
            MerquryRuntime.verifyInventory(object(json(root.resolve("inventory.json"),134217728)),check);source(source,id,check);
            writeJson(root.resolve("manifest.json"),map("profile",id,"producer",producer(),"files",manifest(tree(root,20000,2147483648L,268435456,check))));writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_VELOCYTO_RUNTIME_BINDING_ONLY","manifest",pin(root.resolve("manifest.json"),16777216,check),"analysis_admitted",false));open(root,id,check);return root;
        }catch(IOException|RuntimeException|Error e){boolean unavailable=e instanceof Unavailable||e instanceof MerquryRuntime.Unavailable||e instanceof NextflowProcess.Unavailable;Path cause=root.resolve("BINDING-FAILURE.json");if(Files.exists(cause))unavailable|=Boolean.TRUE.equals(object(json(cause,1048576)).get("unavailable"));Files.deleteIfExists(root.resolve("COMPLETED.json"));writeJson(root.resolve("FAILURE.json"),map("status",unavailable?"SKIP3_DEPENDENT_VELOCYTO_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_VELOCYTO_BINDING","error",e.toString(),"required_action",ACTION));if(unavailable)throw new Unavailable(e.getMessage()+"; inspect "+root);throw e;}
    }
    static Context open(Path root,String id,Runnable check)throws IOException {
        platform();profile(id);if(!Files.isRegularFile(root.resolve("COMPLETED.json")))throw new Unavailable("Completed permitted Velocyto runtime binding unavailable: "+root+". Prepare a NEW binding after prerequisites are available");directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"))&&!Files.exists(root.resolve("BINDING-FAILURE.json")),"Failed Velocyto binding cannot be admitted");var done=object(json(root.resolve("COMPLETED.json"),1048576));keys(done,"status","manifest","analysis_admitted");require(done.get("status").equals("COMPLETE_VELOCYTO_RUNTIME_BINDING_ONLY")&&Boolean.FALSE.equals(done.get("analysis_admitted")),"Invalid runtime-only binding admission");
        require(pin(root.resolve("manifest.json"),16777216,check).equals(fingerprint(done.get("manifest"),16777216)),"Velocyto binding manifest changed");var saved=object(json(root.resolve("manifest.json"),16777216));keys(saved,"profile","producer","files");require(id.equals(saved.get("profile"))&&producer().equals(fingerprint(saved.get("producer"),1000000000)),"Restore the exact original Velocyto producing JAR/profile; do not refresh old seals");
        var actual=new TreeMap<>(manifest(tree(root,20000,2147483648L,268435456,check)));actual.remove("manifest.json");actual.remove("COMPLETED.json");require(NextflowJson.json(actual).equals(NextflowJson.json(saved.get("files"))),"Complete Velocyto binding population changed");
        require(NextflowJson.json(profile(id)).equals(NextflowJson.json(json(root.resolve("profile.json"),16777216))),"Saved Velocyto source profile changed");byte[] binder=resource("bind_runtime.py");require(new Pin(binder.length,sha(binder)).equals(pin(root.resolve("bind_runtime.py"),16777216,check)),"Saved Velocyto binder changed");
        var value=object(json(root.resolve("runtime.json"),16777216));require(id.equals(value.get("profile"))&&System.getProperty("os.version").equals(object(value.get("host")).get("kernel")),"Runtime profile or host kernel changed; prepare a NEW binding");
        var anchors=new HashMap<Path,Pin>();anchors.put(producerPath(),producer());for(var entry:actual.entrySet())anchors.put(root.resolve(entry.getKey()),fingerprint(entry.getValue(),268435456));anchors.put(root.resolve("manifest.json"),fingerprint(done.get("manifest"),16777216));
        var context=new Context(root,value,object(json(root.resolve("inventory.json"),134217728)),pin(root.resolve("COMPLETED.json"),1048576,check),id,anchors);context.verify(check);return context;
    }
}
