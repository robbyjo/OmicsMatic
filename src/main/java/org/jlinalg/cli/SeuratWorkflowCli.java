/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Captured original Seurat RNA preprocessing, PCA, graph, cluster and embeddings. */
public final class SeuratWorkflowCli {
    private SeuratWorkflowCli() { }
    private static final String ENGINE="upstream-Seurat-RNA-workflow-R";
    private static final String SCOPE="DEVELOPMENT_SCOPE: original Seurat5.6.0 unsplit RNA workflow, explicit current settings and exact identity transport. Native surrounding normalization/scaling/PCA/SNN audits do not independently refit VST, regression, SCT, clustering, UMAP or t-SNE. No historical whole-package, whole-paper, biological or installed-DLL authentication claim. No integration, WNN, transfer, Harmony, cell-type annotation or reusable UMAP projection model is supplied.";
    private static final String ACTION="Read WARNINGS.md, analysis-status.json, diagnostics.json and worker/worker-report.json/logs. SKIP3 means the affected analysis was skipped: supply the named exact permitted runtime/backend in declared private libraries, or leave it skipped. FAIL2 admits no complete result: preserve inputs, partials and diagnostics, correct the named input/model/source/process/capacity cause and retry into NEW output paths. No silent identifier repair, nonfinite replacement, population truncation or algorithm/backend substitution. VERIFY checks captured consistency and stated native arithmetic without R; it does not authenticate a coordinated fabricated capture or refit original models. REPLAY runs the original models again and needs the exact permitted runtime; stochastic/host-dependent byte equality is not promised.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")){emit(out,"SeuratWorkflowCli --request UTF8_JSON\nRUN: captured original RNA normalization, variable features, regression/scaling, PCA, NN/SNN, clustering and optional UMAP/t-SNE.\nVERIFY: Java-only capture/native arithmetic audit. REPLAY: verify and rerun into a NEW output.\nRelative paths resolve against the request directory. "+SCOPE+"\n"+ACTION);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_JSON or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();Map<String,Object> raw=read(source);
            if("VERIFY".equals(raw.get("mode"))){keys(raw,Set.of("schema_version","mode","analysis"));if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");verify(resolve(source.getParent(),text(raw,"analysis")));emit(out,"Verified original RNA capture and native surrounding arithmetic without R. "+ACTION);return 0;}
            if("REPLAY".equals(raw.get("mode")))return replay(source,raw,out,err);
            return execute(source,raw,out,err);
        }catch(OutOfMemoryError e){emit(err,"FAIL2 — NO RESULT ADMITTED: Java heap exhausted. Use suitable explicit heap/capacities and NEW output paths. "+ACTION);return 2;}
        catch(Exception e){emit(err,"FAIL2 — NO RESULT ADMITTED: "+e+"\n"+ACTION);return 2;}
    }
    private static int execute(Path source,Map<String,Object> raw,PrintStream out,PrintStream err)throws Exception {
        Map<String,Object> r=SeuratWorkflowContracts.validate(raw);byte[] original=Files.readAllBytes(source);String sourceHash=SeuratWorkflowRuntime.sha(original);
        if(!same(raw,object(SimpleJson.parse(new String(original,StandardCharsets.UTF_8)),"source request")))throw new IOException("Source request changed while reading");
        Path destination=resolve(source.getParent(),text(r,"out"));int[] exit={0};
        FollowupSupport.externalTransaction(Map.of("out",destination.toString()),"original-seurat-RNA-workflow-development",(stage,manifest)->{
            List<String> warnings=new ArrayList<>();warnings.add(SCOPE);var state=new LinkedHashMap<String,Object>();state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("results_admitted",false);state.put("engine",ENGINE);state.put("profile",SeuratWorkflowContracts.PROFILE);
            try {
                if(!sourceHash.equals(FollowupSupport.hash(source)))throw new IOException("Source request changed before capture");Files.write(stage.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);
                Files.createDirectory(stage.resolve("inputs"));long remaining=((Number)r.get("maximum_input_bytes")).longValue();Map<String,String> pins=new TreeMap<>();var captured=new LinkedHashMap<String,Object>(raw);captured.put("out",".");
                for(String key:SeuratWorkflowContracts.INPUTS)if(raw.containsKey(key)){Path target=stage.resolve("inputs/"+key);remaining-=capture(resolve(source.getParent(),text(raw,key)),target,remaining);pins.put("inputs/"+key,FollowupSupport.hash(target));captured.put(key,"inputs/"+key);}
                json(stage.resolve("request.json"),captured);var effective=SeuratWorkflowContracts.validate(captured);json(stage.resolve("effective-request.json"),effective);
                SeuratWorkflowRuntime.sources(stage,false);var prepared=SeuratWorkflowInputs.prepare(stage,effective,false);warnings.addAll(prepared.warnings());
                manifest.put("captured_input_sha256",pins);manifest.put("source_request_sha256",sourceHash);
                SeuratWorkflowRuntime.run(stage,source.getParent(),r,manifest);
                for(var e:pins.entrySet())if(!e.getValue().equals(FollowupSupport.hash(stage.resolve(e.getKey()))))throw new IOException("Captured input changed during original execution");
                SeuratWorkflowRuntime.sources(stage,true);SeuratWorkflowInputs.prepare(stage,effective,true);warnings.addAll(SeuratWorkflowOutputs.publish(stage,effective,prepared,false));
                state.put("status","completed-with-warnings");state.put("results_admitted",true);state.put("features",prepared.counts().features().size());state.put("cells",prepared.counts().cells().size());
            }catch(SeuratWorkflowRuntime.Unavailable e){exit[0]=3;state.put("status","SKIP3_DEPENDENT_ANALYSIS_SKIPPED");state.put("error",e.toString());warnings.add("SKIP3: "+e+". Dependent analysis was skipped; no complete result admitted.");}
            catch(Exception e){exit[0]=2;state.put("error",e.toString());warnings.add("FAIL2: "+e+". Partial outputs are retained evidence, not admitted results.");}
            catch(OutOfMemoryError e){exit[0]=2;state.put("error","Java heap exhausted");warnings.add("FAIL2: Java heap exhausted. Increase suitable explicit heap/capacities or reduce the declared analysis, and use NEW output paths. No partial result admitted.");}
            state.put("required_action",ACTION);json(stage.resolve("analysis-status.json"),state);Files.writeString(stage.resolve("WARNINGS.md"),warningText(state.get("status").toString(),warnings),StandardCharsets.UTF_8);
            manifest.put("engine",ENGINE);manifest.put("profile",SeuratWorkflowContracts.PROFILE);manifest.put("analysis_status",state.get("status"));manifest.put("results_admitted",state.get("results_admitted"));manifest.put("scope",SCOPE);manifest.put("independent_native_model_refit",false);manifest.put("installed_DLL_source_authentication",false);json(stage.resolve("manifest.json"),manifest);
            json(stage.resolve("verify-request.json"),Map.of("schema_version",1,"mode","VERIFY","analysis","."));
        });
        SeuratDifferentialCli.seal(destination);
        if(exit[0]==0)try{verify(destination);}catch(Exception e){exit[0]=2;reject(destination,e);}
        emit(exit[0]==0?out:err,(exit[0]==0?"Completed captured original Seurat RNA workflow: ":exit[0]==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO RESULT ADMITTED: ")+destination);
        emit(err,"WARNINGS / REQUIRED ACTIONS: "+destination.resolve("WARNINGS.md")+"\n"+ACTION);return exit[0];
    }
    private static int replay(Path path,Map<String,Object> raw,PrintStream out,PrintStream err)throws Exception {
        keys(raw,Set.of("schema_version","mode","analysis","out","rscript","r_libraries"));if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");Path prior=resolve(path.getParent(),text(raw,"analysis"));verify(prior);
        Map<String,Object> request=read(prior.resolve("request.json"));for(String key:SeuratWorkflowContracts.INPUTS)if(request.containsKey(key))request.put(key,resolve(prior,text(request,key)).toString());
        var bindings=read(prior.resolve("worker/runtime-bindings.json"));request.put("rscript",raw.containsKey("rscript")?resolve(path.getParent(),text(raw,"rscript")).toString():text(bindings,"rscript"));
        List<String> libraries=new ArrayList<>();for(Object v:list(raw.getOrDefault("r_libraries",bindings.get("libraries")),"r_libraries"))libraries.add(resolve(path.getParent(),v.toString()).toString());request.put("r_libraries",libraries);request.put("out",resolve(path.getParent(),text(raw,"out")).toString());
        // Store an explicit new source request outside the immutable old capture.
        Path destination=Path.of(text(request,"out"));if(Files.exists(destination))throw new IOException("Replay output already exists");Files.createDirectories(destination.getParent());Path protocol=Files.createTempFile(destination.getParent(),"seurat-replay-request-",".json");
        try {json(protocol,request);return execute(protocol,request,out,err);}finally{Files.deleteIfExists(protocol);}
    }
    static void verify(Path root)throws IOException {
        root=root.toAbsolutePath().normalize();var seal=read(root.resolve("capture-files.json"),16000000);keys(seal,Set.of("schema_version","authentication","files"));if(integer(seal,"schema_version",0,1,1)!=1||!Boolean.FALSE.equals(seal.get("authentication")))throw new IOException("Capture schema invalid or falsely claims authentication");
        var pins=object(seal.get("files"),"capture pins");var actual=SeuratWorkflowRuntime.footprint(root);actual.remove("capture-files.json");if(!pins.keySet().equals(actual.keySet()))throw new IOException("Capture file population changed");
        for(var e:pins.entrySet()){String name=e.getKey();Path relative=Path.of(name);if(relative.isAbsolute()||!relative.normalize().equals(relative)||name.contains("..")||name.contains("\\")||!FollowupSupport.hash(root.resolve(relative)).equals(e.getValue()))throw new IOException("Invalid capture path/hash: "+name);}
        var manifest=read(root.resolve("manifest.json"));var state=read(root.resolve("analysis-status.json"));
        if(!ENGINE.equals(manifest.get("engine"))||!SeuratWorkflowContracts.PROFILE.equals(manifest.get("profile"))||!"completed-with-warnings".equals(manifest.get("analysis_status"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted")))throw new IOException("Failed/skipped/incomplete captures are not admitted results");
        if(!SCOPE.equals(manifest.get("scope"))||!Boolean.FALSE.equals(manifest.get("independent_native_model_refit"))||!Boolean.FALSE.equals(manifest.get("installed_DLL_source_authentication")))throw new IOException("Scientific audit/authentication scope changed");
        var original=read(root.resolve("source-request.json"));SeuratWorkflowContracts.validate(original);if(!FollowupSupport.hash(root.resolve("source-request.json")).equals(manifest.get("source_request_sha256")))throw new IOException("Original request hash differs");
        var expected=new LinkedHashMap<String,Object>(original);expected.put("out",".");for(String key:SeuratWorkflowContracts.INPUTS)if(original.containsKey(key))expected.put(key,"inputs/"+key);
        var request=read(root.resolve("request.json"));if(!same(expected,request))throw new IOException("Captured settings differ from original request");var effective=SeuratWorkflowContracts.validate(request);if(!same(effective,read(root.resolve("effective-request.json"))))throw new IOException("Effective request differs");
        Set<String> names=new HashSet<>();var inputPins=object(manifest.get("captured_input_sha256"),"input pins");for(String key:SeuratWorkflowContracts.INPUTS)if(request.containsKey(key)){String name="inputs/"+key;names.add(name);if(!FollowupSupport.hash(root.resolve(name)).equals(inputPins.get(name)))throw new IOException("Input hash differs");}if(!names.equals(inputPins.keySet()))throw new IOException("Input pin population differs");
        var bindings=read(root.resolve("worker/runtime-bindings.json"));if(!SeuratWorkflowContracts.PROFILE.equals(bindings.get("profile"))||!list(manifest.get("command"),"command").equals(List.of(text(bindings,"rscript"),"--vanilla","../source/seurat_workflow.R"))||integer(manifest,"process_exit_code",-1,0,0)!=0||!text(manifest,"rscript_sha256").matches("[0-9a-f]{64}"))throw new IOException("Recorded original process/profile differs");
        List<String> status=Files.readAllLines(root.resolve("worker/worker-status.tsv"),StandardCharsets.UTF_8);if(status.size()!=2||!status.get(0).equals("status\tdetail\trequired_action")||!status.get(1).startsWith("completed-with-warnings\t"))throw new IOException("Original worker final status incomplete");
        SeuratWorkflowRuntime.sources(root,true);var prepared=SeuratWorkflowInputs.prepare(root,effective,true);List<String> sourceWarnings=SeuratWorkflowOutputs.publish(root,effective,prepared,true);List<String> warnings=new ArrayList<>();warnings.add(SCOPE);warnings.addAll(prepared.warnings());warnings.addAll(sourceWarnings);
        if(integer(state,"features",0,0,Integer.MAX_VALUE)!=prepared.counts().features().size()||integer(state,"cells",0,0,Integer.MAX_VALUE)!=prepared.counts().cells().size()||!ACTION.equals(state.get("required_action"))||!warningText("completed-with-warnings",warnings).equals(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8)))throw new IOException("Published warnings/population/recovery guidance changed");
    }
    private static long capture(Path source,Path target,long cap)throws IOException {
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>cap)throw new IOException("Input unavailable/nonregular or exceeds remaining maximum_input_bytes: "+source);String before=FollowupSupport.hash(source);long bytes=0;
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input capture interrupted");if(n>cap-bytes)throw new IOException("Input grew beyond byte capacity");out.write(buffer,0,n);bytes+=n;}}
        if(!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(target)))throw new IOException("Input changed during capture");return bytes;
    }
    private static void reject(Path root,Exception failure)throws IOException {var manifest=read(root.resolve("manifest.json"));manifest.put("analysis_status","FAIL2_PUBLISHED_VERIFICATION_FAILED");manifest.put("results_admitted",false);json(root.resolve("manifest.json"),manifest);var state=read(root.resolve("analysis-status.json"));state.put("status","FAIL2_PUBLISHED_VERIFICATION_FAILED");state.put("results_admitted",false);state.put("error",failure.toString());json(root.resolve("analysis-status.json"),state);Files.writeString(root.resolve("WARNINGS.md"),"# FAIL2 — NO RESULT ADMITTED\n\n"+failure+"\n\n"+ACTION+"\n\n"+Files.readString(root.resolve("WARNINGS.md")),StandardCharsets.UTF_8);SeuratDifferentialCli.seal(root);}
    private static String warningText(String status,List<String> warnings){return "# "+status+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+ACTION+"\n";}
    private static void emit(PrintStream stream,String message){stream.writeBytes((message+"\n").getBytes(StandardCharsets.UTF_8));}
}
