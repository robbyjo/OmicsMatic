/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Original Seurat marker inference/transformation from typed, captured inputs. */
public final class SeuratDifferentialCli {
    private SeuratDifferentialCli() { }
    private static final String ENGINE="upstream-Seurat-marker-R";
    private static final String ACTION="Read WARNINGS.md, analysis-status.json, diagnostics.json, worker/worker-report.json and worker logs. Preserve attempts and use NEW output paths after correcting the named count/metadata/layer/model/source/process/capacity cause. SKIP3 means a required permitted runtime/package is unavailable: install/provide the exact named R4.6.1/Seurat5.6.0 profile in declared private paths only where permitted; otherwise skip dependent analysis. No automatic installation, rounding, identifier repair, pseudobulking or statistical substitution occurs. FAIL2 admits no result, including partial tables. Source ALL can return a partial comparison set: inspect complete_comparison_set and every failed group; missing markers are not negative results. VERIFY audits preparation, summaries, selection, correction and ordering, not independent Wilcoxon/LR or seeded-sampling replay, installed DLL authenticity or coordinated fabricated captures.";
    private static final String SCOPE="DEVELOPMENT_SCOPE: guarded original current Seurat marker tests on explicitly prepared sparse layers, with native Signac TF-IDF when requested. Raw counts, layer units, group identities, biological replication and covariates are caller declarations. Historical source-summary profiles do not imply historical statistical runtimes or full Signac/Seurat pipelines. Exact external identities are restored from explicit ASCII transport codes. Seurat source warnings and nonfinite kinds remain visible.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream output,PrintStream error) {
        try {
            if(args.length==1&&args[0].equals("--help")){emit(output,"SeuratDifferentialCli --request UTF8_JSON\nPAIR: original Wilcoxon/base-R or LR marker comparison.\nALL: original one-versus-rest FindAllMarkers with explicit raw-p return threshold.\nVERIFY: Java-only preparation/summary/correction/capture audits.\nRelative paths resolve against the JSON directory. Use NEW outputs.\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_JSON or --help");
            Path path=Path.of(args[1]).toAbsolutePath().normalize();Map<String,Object> raw=read(path);
            if("VERIFY".equals(raw.get("mode"))) {
                keys(raw,Set.of("schema_version","mode","analysis"));if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");
                verify(resolve(path.getParent(),text(raw,"analysis")));emit(output,"Verified original Seurat marker capture consistency without R. "+ACTION);return 0;
            }
            Map<String,Object> request=validate(raw);byte[] source=Files.readAllBytes(path);String sourceHash=SeuratDifferentialRuntime.sha(source);
            if(!SeuratDifferentialContracts.same(raw,object(SimpleJson.parse(new String(source,StandardCharsets.UTF_8)),"request")))throw new IOException("Request changed while reading");
            Path destination=resolve(path.getParent(),text(request,"out"));int[] code={0},skippedComparisons={0};
            FollowupSupport.externalTransaction(Map.of("out",destination.toString()),"original-seurat-marker-inference-development",(stage,manifest)->{
                List<String> warnings=new ArrayList<>();warnings.add(SCOPE);Map<String,Object> state=new LinkedHashMap<>();
                state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("results_admitted",false);state.put("engine",ENGINE);state.put("profile",PROFILE);
                try {
                    if(!sourceHash.equals(FollowupSupport.hash(path)))throw new IOException("Source request changed before capture");Files.write(stage.resolve("source-request.json"),source,StandardOpenOption.CREATE_NEW);
                    Map<String,Object> captured=new LinkedHashMap<>(raw);captured.put("out",".");Files.createDirectory(stage.resolve("inputs"));long remaining=((Number)request.get("maximum_input_bytes")).longValue();Map<String,String> inputs=new TreeMap<>();
                    for(String key:INPUTS)if(raw.containsKey(key)) {
                        Path input=resolve(path.getParent(),text(raw,key));String name="inputs/"+key;Path target=stage.resolve(name);
                        long copied=capture(input,target,remaining);remaining-=copied;inputs.put(name,FollowupSupport.hash(target));captured.put(key,name);
                    }
                    json(stage.resolve("request.json"),captured);Map<String,Object> effective=validate(captured);json(stage.resolve("effective-request.json"),effective);
                    SeuratDifferentialRuntime.sources(stage,false);var prepared=SeuratDifferentialInputs.prepare(stage,effective,false);warnings.addAll(prepared.warnings());
                    SeuratDifferentialRuntime.run(stage,path.getParent(),request,manifest);
                    for(var entry:inputs.entrySet())if(!entry.getValue().equals(FollowupSupport.hash(stage.resolve(entry.getKey()))))throw new IOException("Captured input changed during original execution");
                    SeuratDifferentialRuntime.sources(stage,true);SeuratDifferentialInputs.prepare(stage,effective,true);warnings.addAll(SeuratDifferentialOutputs.publish(stage,effective,prepared,false));
                    state.put("status","completed-with-warnings");state.put("results_admitted",true);var diagnostics=read(stage.resolve("diagnostics.json"),64_000_000);state.put("complete_comparison_set",diagnostics.get("complete_comparison_set"));state.put("failed_source_comparisons",object(diagnostics.get("original_failed_comparisons"),"failures").size());
                    skippedComparisons[0]=((Number)state.get("failed_source_comparisons")).intValue();if(skippedComparisons[0]>0)warnings.add(0,partialWarning(skippedComparisons[0]));
                    state.put("features",prepared.counts().features().size());state.put("cells",prepared.counts().cells().size());
                    manifest.put("captured_input_sha256",inputs);manifest.put("source_request_sha256",sourceHash);manifest.put("historical_whole_packages_executed",false);manifest.put("installed_DLL_source_authentication",false);
                }catch(SeuratDifferentialRuntime.Unavailable unavailable){code[0]=3;state.put("status","SKIP3_DEPENDENT_ANALYSIS_SKIPPED");state.put("results_admitted",false);state.put("error",unavailable.toString());warnings.add("SKIP3: "+unavailable+". Dependent analysis was skipped; no result admitted.");}
                catch(Exception failure){code[0]=2;state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("results_admitted",false);state.put("error",failure.toString());warnings.add("FAIL2: "+failure+". Any partial tables are retained evidence, not admitted results.");}
                catch(OutOfMemoryError failure){code[0]=2;state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("results_admitted",false);state.put("error","Java heap exhausted");warnings.add("FAIL2: Java heap exhausted. Use a suitable heap/capacities and NEW outputs; no partial result is admitted.");}
                state.put("required_action",ACTION);json(stage.resolve("analysis-status.json"),state);warnings(stage,state.get("status").toString(),warnings);
                manifest.put("engine",ENGINE);manifest.put("profile",PROFILE);manifest.put("analysis_status",state.get("status"));manifest.put("results_admitted",state.get("results_admitted"));manifest.put("scope",SCOPE);manifest.put("verify_replays_statistical_tests",false);manifest.put("verify_replays_sampling",false);json(stage.resolve("manifest.json"),manifest);
                json(stage.resolve("verify-request.json"),Map.of("schema_version",1,"mode","VERIFY","analysis","."));
            });
            seal(destination);
            if(code[0]==0)try{verify(destination);}catch(Exception failure){code[0]=2;reject(destination,failure);}
            emit(code[0]==0?output:error,(code[0]==0?(skippedComparisons[0]>0?"PARTIAL SOURCE RESULT — "+skippedComparisons[0]+" comparisons not tested: ":"Completed original Seurat marker analysis: "):code[0]==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO RESULT ADMITTED: ")+destination);
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
        Map<String,Object> hashes=object(capture.get("files"),"capture file pins");var actual=SeuratDifferentialRuntime.footprint(root);actual.remove("capture-files.json");
        if(!actual.keySet().equals(hashes.keySet()))throw new IOException("Capture file population changed");
        for(var entry:hashes.entrySet()){Path relative=Path.of(entry.getKey());if(relative.isAbsolute()||!relative.normalize().equals(relative)||entry.getKey().contains("..")||entry.getKey().contains("\\"))throw new IOException("Unsafe capture file path");if(!FollowupSupport.hash(root.resolve(relative)).equals(entry.getValue()))throw new IOException("Capture hash mismatch: "+entry.getKey());}
        var manifest=read(root.resolve("manifest.json"));var state=read(root.resolve("analysis-status.json"));
        if(!ENGINE.equals(manifest.get("engine"))||!PROFILE.equals(manifest.get("profile"))||!"completed-with-warnings".equals(manifest.get("analysis_status"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted")))throw new IOException("Failed/skipped/incomplete captures are not admitted results");
        if(!SCOPE.equals(manifest.get("scope"))||!Boolean.FALSE.equals(manifest.get("historical_whole_packages_executed"))||!Boolean.FALSE.equals(manifest.get("installed_DLL_source_authentication"))||!Boolean.FALSE.equals(manifest.get("verify_replays_statistical_tests"))||!Boolean.FALSE.equals(manifest.get("verify_replays_sampling")))throw new IOException("Captured scientific scope or replay/authentication claims differ");
        var original=read(root.resolve("source-request.json"));validate(original);if(!FollowupSupport.hash(root.resolve("source-request.json")).equals(manifest.get("source_request_sha256")))throw new IOException("Original request hash differs");
        var expectedCaptured=new LinkedHashMap<String,Object>(original);expectedCaptured.put("out",".");for(String key:INPUTS)if(original.containsKey(key))expectedCaptured.put(key,"inputs/"+key);
        var request=read(root.resolve("request.json"));if(!same(expectedCaptured,request))throw new IOException("Captured request differs from original scientific settings");
        var expectedPins=object(manifest.get("captured_input_sha256"),"input pins");var inputNames=new HashSet<String>();for(String key:INPUTS)if(request.containsKey(key)){String name="inputs/"+key;inputNames.add(name);if(!FollowupSupport.hash(root.resolve(name)).equals(expectedPins.get(name)))throw new IOException("Captured original input hash differs");}if(!inputNames.equals(expectedPins.keySet()))throw new IOException("Input pin population differs");
        var bindings=read(root.resolve("worker/runtime-bindings.json"));if(!PROFILE.equals(bindings.get("profile"))||!list(manifest.get("command"),"command").equals(List.of(text(bindings,"rscript"),"--vanilla","../source/seurat_differential.R"))||!(manifest.get("process_exit_code") instanceof Number exit)||exit.intValue()!=0||!text(manifest,"rscript_sha256").matches("[0-9a-f]{64}"))throw new IOException("Recorded source process/profile differs");
        var workerStatus=Files.readAllLines(root.resolve("worker/worker-status.tsv"),StandardCharsets.UTF_8);if(workerStatus.size()!=2||!workerStatus.get(0).equals("status\tdetail\trequired_action")||!workerStatus.get(1).startsWith("completed-with-warnings\t"))throw new IOException("Recorded original process did not complete");
        var effective=validate(request);if(!SeuratDifferentialContracts.same(effective,read(root.resolve("effective-request.json"))))throw new IOException("Effective typed request differs");
        SeuratDifferentialRuntime.sources(root,true);var p=SeuratDifferentialInputs.prepare(root,effective,true);var audited=SeuratDifferentialOutputs.publish(root,effective,p,true);
        var diagnostics=read(root.resolve("diagnostics.json"),64_000_000);if(((Number)state.get("features")).intValue()!=p.counts().features().size()||((Number)state.get("cells")).intValue()!=p.counts().cells().size()||!Objects.equals(state.get("complete_comparison_set"),diagnostics.get("complete_comparison_set"))||((Number)state.get("failed_source_comparisons")).intValue()!=object(diagnostics.get("original_failed_comparisons"),"failures").size())throw new IOException("Published population/comparison completeness differs");
        var expectedWarnings=new ArrayList<String>();expectedWarnings.add(SCOPE);expectedWarnings.addAll(p.warnings());expectedWarnings.addAll(audited);int failed=object(diagnostics.get("original_failed_comparisons"),"failures").size();if(failed>0)expectedWarnings.add(0,partialWarning(failed));
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warningText("completed-with-warnings",expectedWarnings))||!ACTION.equals(state.get("required_action")))throw new IOException("Required source warnings/recovery guidance changed");
    }
    static void seal(Path root)throws IOException {
        Map<String,String> files=new TreeMap<>();for(String name:SeuratDifferentialRuntime.footprint(root).keySet())if(!name.equals("capture-files.json"))files.put(name,FollowupSupport.hash(root.resolve(name)));
        json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",files));
    }
    private static void reject(Path root,Exception failure)throws IOException {
        var manifest=read(root.resolve("manifest.json"));manifest.put("analysis_status","FAIL2_PUBLISHED_VERIFICATION_FAILED");manifest.put("results_admitted",false);json(root.resolve("manifest.json"),manifest);
        var state=read(root.resolve("analysis-status.json"));state.put("status","FAIL2_PUBLISHED_VERIFICATION_FAILED");state.put("results_admitted",false);state.put("error",failure.toString());json(root.resolve("analysis-status.json"),state);
        Files.writeString(root.resolve("WARNINGS.md"),"# FAIL2 — NO RESULT ADMITTED\n\nPublished capture verification failed: "+failure+"\n\nRequired action: "+ACTION+"\n\n"+Files.readString(root.resolve("WARNINGS.md")),StandardCharsets.UTF_8);seal(root);
    }
    private static String partialWarning(int n){return "PARTIAL_SOURCE_COMPARISON_SET: "+n+" source comparisons were not tested. The remaining source marker table is retained; absence in a skipped group is not a negative result. Read diagnostics.json and SOURCE_COMPARISON_NOT_TESTED entries, correct the named group/design cause and rerun into NEW paths.";}
    private static String warningText(String status,List<String> warnings){boolean partial=status.equals("completed-with-warnings")&&warnings.stream().anyMatch(w->w.startsWith("PARTIAL_SOURCE_COMPARISON_SET:"));return "# "+(partial?"PARTIAL SOURCE RESULT — SOME COMPARISONS WERE NOT TESTED":status)+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+ACTION+"\n";}
    private static void warnings(Path root,String status,List<String> warnings)throws IOException {Files.writeString(root.resolve("WARNINGS.md"),warningText(status,warnings),StandardCharsets.UTF_8);}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
