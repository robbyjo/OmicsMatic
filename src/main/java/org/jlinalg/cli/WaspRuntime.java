/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Exact source-qualified original WASP runtime, isolated helpers and full immutable census. */
final class WaspRuntime {
    private WaspRuntime() { }
    static final String ACTION="WARNINGS AND REQUIRED ACTION: inspect WARNINGS.md, INPUT-AUDIT.json, OUTPUT-AUDIT.json and every original process stdout/stderr. Require COMPLETED.json and a successful fresh wasp-tools --verify with the exact producing JAR and unchanged runtime. Missing/forbidden software, references, installation permission or licences produce SKIP3 and skip dependent analysis; install/supply the named prerequisite where permitted, then create a NEW binding/capture. Invalid/changed bytes, source, runtime, model/QC, cancellation or resource limits produce FAIL2 and no complete result; preserve attempts, correct the named cause and retry NEW paths. This is standalone WASP0.3.4 mapping with BWA0.7.17, not Hornet, STARsolo, CHT or a certified paper recipe.\n";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String text){super(text);}}
    record Context(Path root,Map<?,?> value,Map<?,?> inventory,Pin seal,String profile,Map<Path,Pin> anchors) {
        Context {anchors=Map.copyOf(anchors);}
        Context anchored(Map<Path,Pin> values){return new Context(root,value,inventory,seal,profile,values);}
        void verify(Runnable check)throws IOException {
            require(pin(root.resolve("COMPLETED.json"),1048576,check).equals(seal),"WASP binding seal changed");
            try{MerquryRuntime.verifyInventory(inventory,check);source(WaspRequest.absolute(value.get("wasp_source")),profile,false,check);source(WaspRequest.absolute(value.get("bwa_source")),profile,true,check);}
            catch(MerquryRuntime.Unavailable|Unavailable e){throw new IOException("Previously bound WASP runtime/source is missing or changed; preserve evidence, restore exact bytes or prepare a NEW binding. "+e.getMessage(),e);}
            for(var entry:anchors.entrySet())require(pin(entry.getKey(),1000000000,check).equals(entry.getValue()),"Immutable WASP execution metadata/producer changed: "+entry.getKey());
        }
        String program(String name)throws IOException {
            Object value=object(this.value.get("programs")).get(name);if(value==null)throw new Unavailable("Required permitted "+name+" prerequisite absent from binding. Install it where allowed and prepare a NEW binding; dependent analysis skipped");
            return name.equals("python")?text(value):root.resolve("bin").resolve(name).toString();
        }
        Path mappingSource()throws IOException{return WaspRequest.absolute(value.get("wasp_source")).resolve("mapping");}
        Map<String,String> environment(long seed)throws IOException {
            var saved=object(value.get("environment"));keys(saved,"PATH","LD_LIBRARY_PATH");var env=new HashMap<String,String>();env.put("PATH",text(saved.get("PATH")));env.put("LD_LIBRARY_PATH",text(saved.get("LD_LIBRARY_PATH")));
            env.put("LC_ALL","C");env.put("LANG","C");env.put("PYTHONDONTWRITEBYTECODE","1");env.put("PYTHONNOUSERSITE","1");env.put("PYTHONHASHSEED",Long.toString(seed));env.put("OPENBLAS_NUM_THREADS","1");env.put("OMP_NUM_THREADS","1");return Map.copyOf(env);
        }
        void separate(Path output)throws IOException {
            require(!output.startsWith(root)&&!root.startsWith(output),"WASP capture and binding evidence must not overlap");
            for(String name:strings(inventory.get("roots"))){Path path=Path.of(name);require(!output.startsWith(path)&&!path.startsWith(output),"WASP output overlaps a bound source/runtime/library root");}
        }
    }
    static void platform()throws Unavailable {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original WASP/BWA execution and replay require Linux x86_64 Java; use Linux Java inside WSL on Windows");
    }
    static byte[] resource(String name)throws IOException {
        try(var in=WaspRuntime.class.getResourceAsStream("/org/jlinalg/omics/wasp-mapping/"+name)){if(in==null)throw new IOException("Missing packaged WASP resource: "+name);return in.readAllBytes();}
    }
    static Map<?,?> profiles()throws IOException{return object(SimpleJson.parse(utf8(resource("profiles.json"))));}
    static Map<?,?> profile(String id)throws IOException {var profiles=object(profiles().get("profiles"));require(profiles.containsKey(id),"Choose the explicit source-qualified WASP profile");return object(profiles.get(id));}
    static void source(Path root,String id,boolean bwa,Runnable check)throws IOException {
        if(!Files.isDirectory(root))throw new Unavailable("Required original source unavailable: "+root);var expected=object(profile(id).get(bwa?"BWA_source_files":"WASP_source_files"));
        var actual=tree(root,20000,1073741824,268435456,check);require(actual.keySet().equals(expected.keySet()),"Complete original "+(bwa?"BWA":"WASP")+" source population differs from selected profile");
        for(var row:actual.entrySet())require(row.getValue().kind().equals("file")&&row.getValue().pin().equals(fingerprint(expected.get(row.getKey()),268435456)),"Original source differs: "+row.getKey());
    }
    static Path newRoot(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();require(root.equals(supplied.toAbsolutePath())&&root.getParent()!=null,"Use a NEW canonical output directory");directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and choose a NEW path");return root;
    }
    static Path bind(Path request,Path supplied,Runnable check)throws IOException {
        platform();if(!Files.isReadable(request))throw new Unavailable("Runtime binding request unavailable: "+request);
        var raw=object(json(request,16777216));keys(raw,"schema_version","profile","wasp_source","bwa_source","python","bwa","samtools","snp2h5","library_paths","roots");integer(raw.get("schema_version"),1,1);String id=WaspRequest.scalar(raw.get("profile"));profile(id);
        Path wasp=WaspRequest.absolute(raw.get("wasp_source")),bwa=WaspRequest.absolute(raw.get("bwa_source"));source(wasp,id,false,check);source(bwa,id,true,check);
        var paths=object(raw.get("library_paths"));keys(paths,"python","samtools","snp2h5");var env=new HashMap<String,String>();env.put("PATH","/usr/bin:/bin");env.put("LD_LIBRARY_PATH",text(paths.get("python")));
        var roots=new ArrayList<Path>(List.of(wasp,bwa));
        for(Object item:paths.values())for(String dir:text(item).split(":",-1))if(!dir.isEmpty()){Path path=WaspRequest.absolute(dir);if(!Files.isDirectory(path))throw new Unavailable("Required permitted library directory unavailable: "+path);directory(path);roots.add(path);}
        for(String name:List.of("python","bwa","samtools","snp2h5"))if(raw.get(name)!=null){Path path=WaspRequest.absolute(raw.get(name));if(!Files.isExecutable(path))throw new Unavailable("Required permitted executable unavailable: "+path);}else require(name.equals("snp2h5"),"Declare Python, BWA and Samtools explicitly");
        for(String name:strings(raw.get("roots"))){Path path=WaspRequest.absolute(name);if(!Files.isDirectory(path))throw new Unavailable("Complete permitted runtime directory unavailable: "+path);directory(path);roots.add(path);}
        Path root=newRoot(supplied);for(Path path:roots)require(!root.startsWith(path)&&!path.startsWith(root),"Binding output overlaps runtime/source roots");Files.createDirectory(root);
        try {
            writeJson(root.resolve("request.json"),raw);writeJson(root.resolve("profile.json"),profile(id));Files.write(root.resolve("bind_runtime.py"),resource("bind_runtime.py"),StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve("WARNINGS.md"),ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var limits=new NextflowProcess.Limits(1800000,8589934592L,1800,268435456,4096,2147483648L,20000,64,256,4294967296L,100);
            NextflowProcess.executeSession(List.of(text(raw.get("python")),"-I","-B",root.resolve("bind_runtime.py").toString(),root.toString()),env,root.resolve("process"),root,root,limits,()->{check.run();source(wasp,id,false,check);source(bwa,id,true,check);},check);
            try{MerquryRuntime.verifyInventory(object(json(root.resolve("inventory.json"),134217728)),check);}catch(MerquryRuntime.Unavailable e){throw new Unavailable(e.getMessage());}
            writeJson(root.resolve("manifest.json"),map("schema_version",1,"profile",id,"producer",producer(),"files",manifest(tree(root,20000,2147483648L,268435456,check)),"analysis_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_WASP_RUNTIME_BINDING_ONLY","manifest",pin(root.resolve("manifest.json"),16777216,check),"analysis_admitted",false));open(root,id,check);return root;
        }catch(IOException|RuntimeException|Error e) {
            boolean unavailable=e instanceof Unavailable||e instanceof NextflowProcess.Unavailable;Path failure=root.resolve("BINDING-FAILURE.json");if(Files.exists(failure))unavailable|=Boolean.TRUE.equals(object(json(failure,1048576)).get("unavailable"));Files.deleteIfExists(root.resolve("COMPLETED.json"));
            writeJson(root.resolve("FAILURE.json"),map("status",unavailable?"SKIP3_DEPENDENT_WASP_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_WASP_BINDING","error",e.toString(),"required_action",ACTION));if(unavailable&&!(e instanceof Unavailable))throw new Unavailable(e.getMessage()+"; inspect "+root);throw e;
        }
    }
    static Context open(Path root,String id,Runnable check)throws IOException {
        platform();if(!Files.isDirectory(root)||!Files.exists(root.resolve("COMPLETED.json")))throw new Unavailable("Completed permitted WASP runtime binding unavailable: "+root+"; prepare a NEW binding after prerequisites are available");directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"))&&!Files.exists(root.resolve("BINDING-FAILURE.json")),"Failed WASP binding cannot be admitted");var done=object(json(root.resolve("COMPLETED.json"),1048576));keys(done,"status","manifest","analysis_admitted");
        require("COMPLETE_WASP_RUNTIME_BINDING_ONLY".equals(done.get("status"))&&Boolean.FALSE.equals(done.get("analysis_admitted")),"Missing or expanded WASP binding admission");require(pin(root.resolve("manifest.json"),16777216,check).equals(fingerprint(done.get("manifest"),16777216)),"WASP binding manifest changed");
        var saved=object(json(root.resolve("manifest.json"),16777216));keys(saved,"schema_version","profile","producer","files","analysis_admitted");integer(saved.get("schema_version"),1,1);require(id.equals(saved.get("profile"))&&producer().equals(fingerprint(saved.get("producer"),1000000000))&&Boolean.FALSE.equals(saved.get("analysis_admitted")),"Restore exact WASP producing JAR/profile; never refresh old seals");
        var actual=new TreeMap<>(manifest(tree(root,20000,2147483648L,268435456,check)));actual.remove("manifest.json");actual.remove("COMPLETED.json");require(NextflowJson.json(actual).equals(NextflowJson.json(saved.get("files"))),"Complete WASP binding file population changed");
        require(NextflowJson.json(profile(id)).equals(NextflowJson.json(json(root.resolve("profile.json"),16777216))),"Packaged original WASP profile differs");byte[] helper=resource("bind_runtime.py");require(pin(root.resolve("bind_runtime.py"),16777216,check).equals(new Pin(helper.length,sha(helper))),"Saved WASP binder code differs from producer");
        var value=object(json(root.resolve("runtime.json"),16777216));require(id.equals(value.get("profile"))&&System.getProperty("os.version").equals(object(value.get("host")).get("kernel")),"WASP runtime profile/host kernel changed; prepare a NEW binding");
        var context=new Context(root,value,object(json(root.resolve("inventory.json"),134217728)),pin(root.resolve("COMPLETED.json"),1048576,check),id,Map.of());context.verify(check);return context;
    }
    static Path producerPath()throws IOException {
        try{Path path=Path.of(WaspRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!path.getFileName().toString().endsWith(".jar"))throw new Unavailable("Build and retain the complete source-built executable JAR");return path;}
        catch(java.net.URISyntaxException e){throw new IOException(e);}
    }
    static Pin producer()throws IOException{return pin(producerPath(),1000000000,()->{});}
}
