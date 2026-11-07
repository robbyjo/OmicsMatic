/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Deseq2Contracts.*;

/** Original DESeq2 inference/transformation from typed, captured inputs. */
public final class Deseq2InferenceCli {
    private Deseq2InferenceCli() { }
    private static final String ENGINE="upstream-DESeq2-R";
    private static final String ACTION="Read WARNINGS.md, analysis-status.json, worker/worker-report.json and worker/stdout.log/stderr.log. Preserve the attempt and use NEW output paths after correcting named count, sample, model, contrast, dependency or capacity errors. SKIP3 means the dependent request was skipped: supply exact R4.6.1, DESeq2 1.52.0 and requested optional packages in declared private libraries only where permitted; if installation is forbidden, skip it. No automatic install, count rounding, identifier repair or statistical substitution occurs. R_round and technical collapse must be explicitly requested. FAIL2 admits no result, including partial tables. VERIFY checks capture consistency and normalization without rerunning NB fitting, statistical tests, iterative factor estimation, transformations or shrinkage. Hashes establish integrity, not authenticity or biological validity.";
    private static final String SCOPE="DEVELOPMENT_SCOPE: original DESeq2 1.52.0 on the declared R4.6.1 environment. Historical paper editions, wrapper pipelines, quantifier import/length offsets, biological design adequacy and native-DLL source authentication are not certified. Raw/estimated count units and the modeled biological replication are caller declarations. Factor labels use explicitly recorded ASCII transport codes inside R; published gene/sample identities are restored exactly.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream output,PrintStream error) {
        try {
            if(args.length==1&&args[0].equals("--help")){emit(output,"Deseq2InferenceCli --request UTF8_JSON\nANALYZE: typed designs, original Wald/LRT results, VST/rlog and explicit optional shrinkage.\nVERIFY: Java-only structural/normalization checks of a completed capture.\nRelative paths resolve against the request directory; output must be NEW.\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_JSON or --help");
            Path path=Path.of(args[1]).toAbsolutePath().normalize();Map<String,Object> raw=read(path);
            if("VERIFY".equals(raw.get("mode"))) {
                keys(raw,Set.of("schema_version","mode","analysis"));if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");
                verify(resolve(path.getParent(),text(raw,"analysis")));emit(output,"Verified original DESeq2 capture consistency without R. "+ACTION);return 0;
            }
            Map<String,Object> request=validate(raw);byte[] source=Files.readAllBytes(path);String sourceHash=Deseq2Runtime.sha(source);
            if(!Deseq2Outputs.same(raw,object(SimpleJson.parse(new String(source,StandardCharsets.UTF_8)),"request")))throw new IOException("Request changed while reading");
            Path destination=resolve(path.getParent(),text(request,"out"));int[] code={0};
            FollowupSupport.externalTransaction(Map.of("out",destination.toString()),"original-deseq2-inference-development",(stage,manifest)->{
                List<String> warnings=new ArrayList<>();warnings.add(SCOPE);Map<String,Object> state=new LinkedHashMap<>();
                state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("results_admitted",false);state.put("engine",ENGINE);state.put("profile",PROFILE);
                try {
                    if(!sourceHash.equals(FollowupSupport.hash(path)))throw new IOException("Source request changed before capture");Files.write(stage.resolve("source-request.json"),source,StandardOpenOption.CREATE_NEW);
                    Map<String,Object> captured=new LinkedHashMap<>(raw);captured.put("out",".");Files.createDirectory(stage.resolve("inputs"));long remaining=((Number)request.get("maximum_input_bytes")).longValue();Map<String,String> inputs=new TreeMap<>();
                    for(String key:INPUTS)if(raw.containsKey(key)) {
                        Path input=resolve(path.getParent(),text(raw,key));String name="inputs/"+key+".tsv";Path target=stage.resolve(name);
                        long copied=capture(input,target,remaining);remaining-=copied;inputs.put(name,FollowupSupport.hash(target));captured.put(key,name);
                    }
                    json(stage.resolve("request.json"),captured);Map<String,Object> effective=validate(captured);json(stage.resolve("effective-request.json"),effective);
                    Deseq2Runtime.sources(stage,false);var prepared=Deseq2Inputs.prepare(stage,effective,false);warnings.addAll(prepared.warnings());
                    Deseq2Runtime.run(stage,path.getParent(),request,manifest);
                    for(var entry:inputs.entrySet())if(!entry.getValue().equals(FollowupSupport.hash(stage.resolve(entry.getKey()))))throw new IOException("Captured input changed during original execution");
                    Deseq2Runtime.sources(stage,true);Deseq2Inputs.prepare(stage,effective,true);warnings.addAll(Deseq2Outputs.publish(stage,effective,prepared,false));
                    state.put("status","completed-with-warnings");state.put("results_admitted",true);state.put("differential_inference_performed",!"none".equals(object(request.get("fit"),"fit").get("test")));
                    state.put("genes",prepared.keep().size());state.put("samples",prepared.analysisSamples().size());
                    manifest.put("captured_input_sha256",inputs);manifest.put("source_request_sha256",sourceHash);manifest.put("historical_whole_packages_executed",false);manifest.put("installed_DLL_source_authentication",false);
                }catch(Deseq2Runtime.Unavailable unavailable){code[0]=3;state.put("status","SKIP3_DEPENDENT_ANALYSIS_SKIPPED");state.put("error",unavailable.toString());warnings.add("SKIP3: "+unavailable+". Dependent analysis was skipped; no result admitted.");}
                catch(Exception failure){code[0]=2;state.put("error",failure.toString());warnings.add("FAIL2: "+failure+". Any partial tables are retained evidence, not admitted results.");}
                catch(OutOfMemoryError failure){code[0]=2;state.put("error","Java heap exhausted");warnings.add("FAIL2: Java heap exhausted. Use a suitable heap/capacities and NEW outputs; no partial result is admitted.");}
                state.put("required_action",ACTION);json(stage.resolve("analysis-status.json"),state);warnings(stage,state.get("status").toString(),warnings);
                manifest.put("engine",ENGINE);manifest.put("profile",PROFILE);manifest.put("analysis_status",state.get("status"));manifest.put("results_admitted",state.get("results_admitted"));manifest.put("scope",SCOPE);manifest.put("verify_replays_NB_inference",false);manifest.put("verify_replays_iterative_size_factors",false);json(stage.resolve("manifest.json"),manifest);
                json(stage.resolve("verify-request.json"),Map.of("schema_version",1,"mode","VERIFY","analysis","."));
            });
            seal(destination);
            if(code[0]==0)try{verify(destination);}catch(Exception failure){code[0]=2;reject(destination,failure);}
            emit(code[0]==0?output:error,(code[0]==0?"Completed original DESeq2 analysis: ":code[0]==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO RESULT ADMITTED: ")+destination);
            emit(error,"WARNINGS / REQUIRED ACTIONS: "+destination.resolve("WARNINGS.md")+"\n"+ACTION);return code[0];
        }catch(OutOfMemoryError failure){emit(error,"FAIL2 — NO RESULT ADMITTED: Java heap exhausted. Use suitable explicit capacities/heap and NEW output paths. "+ACTION);return 2;}
        catch(Exception failure){emit(error,"FAIL2 — NO RESULT ADMITTED: "+failure+"\n"+ACTION);return 2;}
    }
    private static long capture(Path source,Path target,long cap)throws IOException {
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>cap)throw new IOException("Input is unavailable, nonregular or exceeds maximum_input_bytes: "+source);
        String before=FollowupSupport.hash(source);long bytes=0;byte[] buffer=new byte[65536];
        try(var input=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            int n;while((n=input.read(buffer))>=0){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input capture interrupted");if(n>cap-bytes)throw new IOException("Input grew beyond maximum_input_bytes");output.write(buffer,0,n);bytes+=n;}
        }
        if(!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(target)))throw new IOException("Input changed during capture: "+source);return bytes;
    }
    static void verify(Path root)throws IOException {
        root=root.toAbsolutePath().normalize();var capture=read(root.resolve("capture-files.json"),16_000_000);keys(capture,Set.of("schema_version","authentication","files"));
        if(integer(capture,"schema_version",0,1,1)!=1||!Boolean.FALSE.equals(capture.get("authentication")))throw new IOException("Invalid capture seal scope");
        Map<String,Object> hashes=object(capture.get("files"),"capture file pins");var actual=Deseq2Runtime.footprint(root);actual.remove("capture-files.json");
        if(!actual.keySet().equals(hashes.keySet()))throw new IOException("Capture file population changed");
        for(var entry:hashes.entrySet()){Path relative=Path.of(entry.getKey());if(relative.isAbsolute()||!relative.normalize().equals(relative)||entry.getKey().contains("..")||entry.getKey().contains("\\"))throw new IOException("Unsafe capture file path");if(!FollowupSupport.hash(root.resolve(relative)).equals(entry.getValue()))throw new IOException("Capture hash mismatch: "+entry.getKey());}
        var manifest=read(root.resolve("manifest.json"));var state=read(root.resolve("analysis-status.json"));
        if(!ENGINE.equals(manifest.get("engine"))||!PROFILE.equals(manifest.get("profile"))||!"completed-with-warnings".equals(manifest.get("analysis_status"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted")))throw new IOException("Failed/skipped/incomplete captures are not admitted results");
        var request=read(root.resolve("request.json"));for(String key:INPUTS)if(request.containsKey(key)&&!text(request,key).equals("inputs/"+key+".tsv"))throw new IOException("Captured input path differs from the closed input set");
        var effective=validate(request);if(!Deseq2Outputs.same(effective,read(root.resolve("effective-request.json"))))throw new IOException("Effective typed request differs");
        Deseq2Runtime.sources(root,true);var p=Deseq2Inputs.prepare(root,effective,true);Deseq2Outputs.publish(root,effective,p,true);
        if(((Number)state.get("genes")).intValue()!=p.keep().size()||((Number)state.get("samples")).intValue()!=p.analysisSamples().size())throw new IOException("Published population differs");
    }
    static void seal(Path root)throws IOException {
        Map<String,String> files=new TreeMap<>();for(String name:Deseq2Runtime.footprint(root).keySet())if(!name.equals("capture-files.json"))files.put(name,FollowupSupport.hash(root.resolve(name)));
        json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",files));
    }
    private static void reject(Path root,Exception failure)throws IOException {
        var manifest=read(root.resolve("manifest.json"));manifest.put("analysis_status","FAIL2_PUBLISHED_VERIFICATION_FAILED");manifest.put("results_admitted",false);json(root.resolve("manifest.json"),manifest);
        var state=read(root.resolve("analysis-status.json"));state.put("status","FAIL2_PUBLISHED_VERIFICATION_FAILED");state.put("results_admitted",false);state.put("error",failure.toString());json(root.resolve("analysis-status.json"),state);
        Files.writeString(root.resolve("WARNINGS.md"),"# FAIL2 — NO RESULT ADMITTED\n\nPublished capture verification failed: "+failure+"\n\nRequired action: "+ACTION+"\n\n"+Files.readString(root.resolve("WARNINGS.md")),StandardCharsets.UTF_8);seal(root);
    }
    private static void warnings(Path root,String status,List<String> warnings)throws IOException {Files.writeString(root.resolve("WARNINGS.md"),"# "+status+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+ACTION+"\n",StandardCharsets.UTF_8);}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
