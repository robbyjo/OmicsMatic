/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.RsubreadSupport.*;

/** Bind an explicitly permitted existing original Rsubread runtime; never installs or refits. */
public final class RsubreadRuntimeCli {
    private RsubreadRuntimeCli() { }
    public static void main(String[] args) { int code=run(args,System.out,System.err);if(code!=0)System.exit(code); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path root=null;boolean ownsRoot=false;Map<String,Object> state=new LinkedHashMap<>();int code=2;
        try {
            if(args.length!=1)throw new IOException("Supply one typed BIND request JSON");Path request=Path.of(args[0]).toAbsolutePath().normalize();var raw=read(request);
            keys(raw,Set.of("schema_version","mode","edition","out","rscript","r_home","r_library","allow_execution","timeout_seconds","maximum_output_bytes"));
            if(integer(raw.get("schema_version"),1,1)!=1||!"BIND".equals(raw.get("mode")))throw new IOException("Require schema_version1/mode BIND");
            String edition=text(raw.get("edition"));var profile=RsubreadProfiles.profile(edition);
            root=resolve(request.getParent(),text(raw.get("out")));if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists; bind NEW paths");Files.createDirectories(root.getParent());Files.createDirectory(root);ownsRoot=true;
            state.putAll(Map.of("schema_version",1,"status","FAIL2_RUNTIME_NOT_BOUND","runtime_bound",false,"analysis_admitted",false,"implementation_credit",false,"edition",edition,"profile",profile.get("profile"),"required_action",ACTION));
            json(root.resolve("source-request.json"),raw);RsubreadProfiles.capture(root,edition);
            if(!bool(raw.getOrDefault("allow_execution",true)))throw new Unavailable("Execution explicitly forbidden; runtime binding and dependent analysis skipped");
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))throw new Unavailable("These original runtime profiles require Windows x86_64; dependent analysis skipped on this platform");
            Path rscript=resolve(request.getParent(),text(raw.get("rscript"))),rhome=resolve(request.getParent(),text(raw.get("r_home"))),lib=resolve(request.getParent(),text(raw.get("r_library")));
            try{checkPin(rscript,profile.get("known_rscript"));}catch(IOException e){throw new Unavailable("Declared Rscript does not match the selected known original profile",e);}
            if(!rscript.startsWith(rhome)||!Files.isDirectory(lib.resolve("Rsubread")))throw new Unavailable("Require declared R home and private Rsubread library");
            Map<String,Object> binding=new LinkedHashMap<>();binding.putAll(Map.of("edition",edition,"profile",profile.get("profile"),"rscript",rscript.toString(),"r_home",rhome.toString(),"r_library",lib.toString(),"package_version",profile.get("package_version"),"R_version",profile.get("R_version")));
            Map<String,Object> before;try{before=runtimeFiles(binding);checkKnownPopulation(binding,profile,before);}catch(IOException e){throw new Unavailable("Selected original runtime population unavailable or changed; skip before executing R",e);}
            Path worker=root.resolve("worker");Files.createDirectory(worker);RsubreadProfiles.paths(worker.resolve("runtime-paths.hex.tsv"),binding);
            RsubreadProcess.run(root,worker,rscript,rhome,List.of(root.resolve("source/rsubread_runtime_guard.R").toString(),worker.resolve("runtime-paths.hex.tsv").toString(),root.resolve("source/reference").toString()),
                integer(raw.getOrDefault("timeout_seconds",120),1,3600),integer(raw.getOrDefault("maximum_output_bytes",16000000),1,1000000000),new LinkedHashMap<>());
            validateWorker(worker,profile,binding);if(!before.equals(runtimeFiles(binding)))throw new IOException("Runtime file population changed while binding; no binding admitted");
            RsubreadProfiles.checkCapture(root,edition);cancel(root);binding.put("runtime_files",before);binding.put("guarded_symbols",Files.readAllLines(worker.resolve("guarded-original-symbols.txt")));
            binding.put("runtime_scope",profile.get("runtime_scope"));binding.put("authentication",false);json(root.resolve("binding.json"),binding);
            state.put("status","completed-original-runtime-bound-only");state.put("runtime_bound",true);state.put("binding",pin(root.resolve("binding.json")));code=0;
        }catch(Unavailable e){code=3;state.put("status","SKIP3_RUNTIME_UNAVAILABLE");state.put("error",e.toString());}
        catch(Exception e){code=2;state.put("status","FAIL2_RUNTIME_NOT_BOUND");state.put("error",e.toString());}
        catch(OutOfMemoryError e){code=2;state.put("status","FAIL2_RUNTIME_NOT_BOUND");state.put("error","Java heap exhausted; use explicit resources and NEW paths");}
        finally {
            if(ownsRoot && Files.isDirectory(root) && !Files.exists(root.resolve("COMPLETED.json"))) {
                boolean interrupted=Thread.interrupted();try{state.put("runtime_bound",code==0);state.put("analysis_admitted",false);state.put("required_action",ACTION);warnings(root,state.get("status").toString(),state.getOrDefault("error","No counting analysis has run. Review the runtime identity and source/runtime limits.").toString());json(root.resolve("COMPLETED.json"),state);}catch(Exception e){code=2;emit(error,"FAIL2: Could not write complete runtime receipt: "+e);}finally{if(interrupted)Thread.currentThread().interrupt();}
            }
        }
        emit(code==0?output:error,(code==0?"BOUND ORIGINAL RUNTIME ONLY: ":code==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO RUNTIME/RESULT ADMITTED: ")+root+"\n"+state.getOrDefault("error","")+"\nWARNINGS / REQUIRED ACTIONS: "+ACTION);return code;
    }
    private static void emit(PrintStream stream,String value) {stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
    static Map<String,Object> runtimeFiles(Map<String,Object> binding) throws IOException {
        Path rhome=Path.of(text(binding.get("r_home"))),lib=Path.of(text(binding.get("r_library")));Map<String,Object> result=new TreeMap<>();
        for(String name:List.of("bin","modules","etc","share","library")) {
            Path root=rhome.resolve(name);for(var e:inventory(root).entrySet())result.put(root.resolve(e.getKey()).toString(),e.getValue());
        }
        for(var e:inventory(lib).entrySet())result.put(lib.resolve(e.getKey()).toString(),e.getValue());
        return result;
    }
    private static void checkKnownPopulation(Map<String,Object> binding,Map<String,Object> profile,Map<String,Object> actual) throws IOException {
        Path rhome=Path.of(text(binding.get("r_home"))),lib=Path.of(text(binding.get("r_library"))).resolve("Rsubread");Map<String,Object> expected=new TreeMap<>();
        for(var e:object(profile.get("known_r_home_files")).entrySet())expected.put(contained(rhome,e.getKey()).toString(),e.getValue());
        for(var e:object(profile.get("known_package_files")).entrySet())expected.put(contained(lib,e.getKey()).toString(),e.getValue());
        if(!actual.equals(expected)) {
            var missing=expected.keySet().stream().filter(k->!actual.containsKey(k)).limit(8).toList();
            var extra=actual.keySet().stream().filter(k->!expected.containsKey(k)).limit(8).toList();
            var changed=expected.keySet().stream().filter(actual::containsKey).filter(k->!expected.get(k).equals(actual.get(k))).limit(8).toList();
            throw new Unavailable("R/package files differ from the selected original population. Missing="+missing+"; extra="+extra+"; changed="+changed+". Skip before executing R. Supply the documented exact clean original R and exported private package; preserve discrepancies and never refresh hashes");
        }
    }
    static void validateWorker(Path worker,Map<String,Object> profile,Map<String,Object> binding) throws IOException {
        if(!Files.readString(worker.resolve("worker-status.txt")).strip().equals("PASS_ORIGINAL_RSUBREAD_RUNTIME_GUARDS"))throw new Unavailable("Original runtime guard did not pass");
        var expected=object(profile.get("known_dlls"));Set<String> names=new HashSet<>();
        for(String row:Files.readAllLines(worker.resolve("loaded-dlls.hex.tsv"),StandardCharsets.US_ASCII)) {
            String[] f=row.split("\t",-1);if(f.length!=2||!names.add(f[0])||!expected.containsKey(f[0]))throw new Unavailable("Unexpected original loaded DLL population");
            String path=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(HexFormat.of().parseHex(f[1]))).toString();
            Path file=f[0].equals("base")?Path.of(text(binding.get("r_home"))).resolve("bin/x64/R.dll"):Path.of(path);
            try{checkPin(file,expected.get(f[0]));}catch(IOException e){throw new Unavailable("Original loaded DLL identity differs: "+f[0],e);}
        }
        if(!names.containsAll(Set.of("base","Rsubread","Matrix")))throw new Unavailable("Required original DLLs were not observed");
        List<String> guarded=Files.readAllLines(worker.resolve("guarded-original-symbols.txt"));if(!guarded.containsAll(List.of("featureCounts",".retrieve.tmp.parameters",".get.annotation.file")))throw new Unavailable("Original helper guard population incomplete");
    }
    static Map<String,Object> loadBinding(Path receipt) throws IOException {
        Map<String,Object> state;try{state=read(receipt);}catch(IOException e){throw new Unavailable("Declared original-runtime binding receipt unavailable; skip dependent counting and supply a permitted complete binding",e);}
        if(!"completed-original-runtime-bound-only".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("runtime_bound")))throw new Unavailable("Runtime receipt is skipped, failed or incomplete");
        Path file=receipt.getParent().resolve("binding.json");Map<String,Object> binding;
        try{checkPin(file,state.get("binding"));binding=read(file);}catch(IOException e){throw new Unavailable("Bound runtime metadata unavailable or changed; skip dependent counting",e);}
        String edition=text(binding.get("edition"));var profile=RsubreadProfiles.profile(edition);
        if(!profile.get("profile").equals(binding.get("profile")))throw new Unavailable("Runtime profile differs");
        RsubreadProfiles.checkCapture(receipt.getParent(),edition);
        try {var actual=runtimeFiles(binding);checkKnownPopulation(binding,profile,actual);if(!actual.equals(object(binding.get("runtime_files"))))throw new Unavailable("Bound runtime file population/bytes changed");checkPin(Path.of(text(binding.get("rscript"))),profile.get("known_rscript"));}
        catch(IOException e){throw new Unavailable("Bound runtime unavailable or changed; skip dependent analysis and bind NEW paths after correcting cause",e);}return binding;
    }
}
