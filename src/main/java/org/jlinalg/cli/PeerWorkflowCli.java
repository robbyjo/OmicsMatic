/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Source-bound original dense PEER RUN, offline VERIFY and immutable-parent REPLAY. */
public final class PeerWorkflowCli {
    private PeerWorkflowCli() { }
    static final String ENGINE="source-bound-peer-original-dense-1";
    static final String SCOPE="DEVELOPMENT_SCOPE: exact original PMBio PEER 1.3 commit 40bc4b2, dense FP32 matrices, explicit C srand seed and randn/PCA initialization on prepared continuous data. Native legacy Peer is a separate FP64 implementation and is not an engine/fallback of this adapter. Whole author preparation, factor-count optimization, downstream QTL/residualization pipelines, sparse prior-guided PEER and older editions are not supplied. VERIFY checks integrity, literal intake, source profile, dimensions, precision posteriors, selected FP32 arithmetic, observed stopping history and exports; it does not refit or authenticate coordinated replacement.";
    static final String ACTION="SKIP3: DEPENDENT ANALYSIS SKIPPED; supply the named exact permitted source/input/runtime and retry NEW output, or retain the skip if unavailable. Downloads/private installation remain authorized where the environment permits; absence is not an installation prohibition. FAIL2: NO COMPLETE RESULT; retain raw inputs, initial/final model state, histories, warnings and failed attempts, correct the named data/design, numeric/convergence, source/integrity, capacity or process cause and retry NEW output. No silent identifiers, factor count, population, edition, missing/nonfinite-value or estimator repair. Read analysis-status.json, WARNINGS.md and all original worker logs. "+SCOPE;
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")){out.println("PeerWorkflowCli --request UTF8_JSON | --verify COMPLETE_BUNDLE\nRUN: explicit original dense PEER on a feature_id/sample-column TSV; optional exactly ordered sample_id covariates and matching measurement variances. VERIFY: offline capture checks. REPLAY: new output and declared runtime binding; parent preserved.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));out.println("Verified saved PEER capture. "+SCOPE);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();var raw=read(source);String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){keys(raw,Set.of("schema_version","mode","analysis"));if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Control schema differs");verify(resolve(source.getParent(),text(raw,"analysis")));out.println("Verified saved PEER capture");return 0;}
            if(mode.equals("REPLAY")) {
                keys(raw,Set.of("schema_version","mode","analysis","out","runtime"));if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Control schema differs");
                Path parent=resolve(source.getParent(),text(raw,"analysis")),destination=resolve(source.getParent(),text(raw,"out"));verify(parent);
                if(destination.startsWith(parent))throw new IOException("REPLAY output cannot be within its retained parent");
                var request=new LinkedHashMap<String,Object>(read(parent.resolve("request.json")));
                for(String key:PeerWorkflowContracts.INPUTS)if(request.containsKey(key))request.put(key,parent.resolve(text(request,key)).toString());request.put("out",destination.toString());
                if(raw.containsKey("runtime")) {
                    var binding=object(raw.get("runtime"),"runtime");binding.put("executable",resolve(source.getParent(),text(binding,"executable")).toString());request.put("runtime",binding);
                }else {
                    var binding=read(parent.resolve("worker/runtime-bindings.json"));var runtime=new LinkedHashMap<String,Object>();runtime.put("executable",binding.get("executable"));runtime.put("sha256",binding.get("sha256"));
                    if(binding.get("transport").equals("explicit-wsl"))runtime.put("wsl_distribution",binding.get("wsl_distribution"));request.put("runtime",runtime);
                }
                return execute(source.getParent(),request,RsemJson.bytes(request),Map.of("analysis",parent.toString(),"capture_sha256",FollowupSupport.hash(parent.resolve("capture-files.json"))),out,err);
            }
            return execute(source.getParent(),raw,Files.readAllBytes(source),Map.of(),out,err);
        }catch(OutOfMemoryError failure){err.println("FAIL2: NO COMPLETE RESULT; Java heap exhausted. Preserve partials and retry new output with explicit capacities. "+ACTION);return 2;}
        catch(Exception failure){err.println("FAIL2: NO COMPLETE RESULT; "+failure+"\n"+ACTION);return 2;}
    }
    private static int execute(Path parent,Map<String,Object> raw,byte[] original,Map<String,Object> replay,PrintStream out,PrintStream err)throws Exception {
        var validated=PeerWorkflowContracts.validate(raw);Path root=newOutput(resolve(parent,text(raw,"out")));int code=2;String cause=null;boolean interrupted=false;
        var manifest=new LinkedHashMap<String,Object>();manifest.put("engine",ENGINE);manifest.put("scope",SCOPE);manifest.put("profile",validated.get("profile"));manifest.put("source_request_sha256",sha(original));manifest.put("capture_root",root.toString());
        json(root.resolve("analysis-status.json"),status("RUNNING_NO_RESULT_ADMITTED",false,null));var inputPins=new TreeMap<String,Object>();
        try {
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);if(!replay.isEmpty())json(root.resolve("replay-source.json"),replay);
            Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));PeerWorkflowRuntime.sources(root,false);
            var captured=PeerWorkflowContracts.captured(raw);json(root.resolve("request.json"),captured);var effective=PeerWorkflowContracts.validate(captured);json(root.resolve("effective-request.json"),effective);
            long remaining=PeerWorkflowContracts.limit(validated,"maximum_input_bytes");var missing=new ArrayList<String>();
            for(String key:PeerWorkflowContracts.INPUTS)if(raw.containsKey(key)) {
                Path source=resolve(parent,text(raw,key)),target=root.resolve("inputs/"+key+".tsv");
                if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)){missing.add("Missing/inaccessible exact "+key+" input: "+source);continue;}
                remaining-=capture(source,target,remaining);inputPins.put("inputs/"+key+".tsv",FollowupSupport.hash(target));
            }
            manifest.put("captured_input_sha256",inputPins);if(!missing.isEmpty())throw new PeerWorkflowRuntime.Unavailable(String.join("; ",missing));
            if(!PeerWorkflowContracts.PROFILE.equals(validated.get("profile")))throw new PeerWorkflowRuntime.Unavailable("Exact PEER source profile is not supplied: "+validated.get("profile")+"; supported profile is "+PeerWorkflowContracts.PROFILE);
            var prepared=PeerWorkflowFiles.prepare(root,effective,false);PeerWorkflowRuntime.run(root,parent,validated,manifest);
            for(var entry:inputPins.entrySet())if(!FollowupSupport.hash(root.resolve(entry.getKey())).equals(entry.getValue()))throw new IOException("Captured input changed during execution");
            PeerWorkflowFiles.prepare(root,effective,true);PeerWorkflowAudit.outputs(root,effective,prepared,false);code=0;
        }catch(PeerWorkflowRuntime.Unavailable unavailable){code=3;cause=unavailable.toString();}
        catch(OutOfMemoryError failure){cause="Java heap exhausted; preserve all inputs/model partials and retry NEW output with explicit heap/capacities";}
        catch(Exception failure){cause=failure.toString();if(failure instanceof InterruptedException||Thread.currentThread().isInterrupted()){interrupted=true;Thread.interrupted();}}
        if(manifest.containsKey("owned_wsl_cleanup_error")){code=2;cause=String.valueOf(cause)+"; OWNED WSL CLEANUP REQUIRES ATTENTION: "+manifest.get("owned_wsl_cleanup_error")+". Preserve worker/owned-wsl.json and use only its recorded process group and token.";}
        String state=code==0?"completed-with-warnings":code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
        manifest.put("captured_input_sha256",inputPins);manifest.put("analysis_status",state);manifest.put("results_admitted",code==0);if(cause!=null)manifest.put("error",cause);
        json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),status(state,code==0,cause));Files.writeString(root.resolve("WARNINGS.md"),warnings(state,cause),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try{seal(root);if(code==0)verify(root);}catch(Exception failure){code=2;cause="Post-write verification failed: "+failure;manifest.put("analysis_status","FAIL2_NO_COMPLETE_RESULT");manifest.put("results_admitted",false);manifest.put("verification_error",cause);json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),status("FAIL2_NO_COMPLETE_RESULT",false,cause));Files.writeString(root.resolve("WARNINGS.md"),warnings("FAIL2_NO_COMPLETE_RESULT",cause),StandardCharsets.UTF_8);seal(root);}
        if(interrupted)Thread.currentThread().interrupt();(code==0?out:err).println((code==0?"Completed original dense PEER: ":code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED: ":"FAIL2: NO COMPLETE RESULT: ")+root);
        if(cause!=null)err.println("CAUSE / REQUIRED RECOVERY: "+cause);err.println("WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return code;
    }
    static void verify(Path source)throws Exception {
        Path root=source.toAbsolutePath().normalize();var seal=read(root.resolve("capture-files.json"),16_000_000);keys(seal,Set.of("schema_version","authentication","files"));
        if(integer(seal,"schema_version",0,1,1)!=1||!Boolean.FALSE.equals(seal.get("authentication")))throw new IOException("Invalid seal/authentication claim");
        var expected=object(seal.get("files"),"capture file hashes");var actual=PeerWorkflowRuntime.footprint(root);actual.remove("capture-files.json");
        if(!actual.keySet().equals(expected.keySet()))throw new IOException("PEER capture file population differs");for(var entry:expected.entrySet())if(!FollowupSupport.hash(safe(root,entry.getKey())).equals(entry.getValue()))throw new IOException("PEER capture hash differs: "+entry.getKey());
        var manifest=read(root.resolve("manifest.json"));var state=read(root.resolve("analysis-status.json"));
        if(!ENGINE.equals(manifest.get("engine"))||!SCOPE.equals(manifest.get("scope"))||!"completed-with-warnings".equals(manifest.get("analysis_status"))||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!ACTION.equals(state.get("required_action")))throw new IOException("Failed/skipped/incomplete PEER captures are not admitted results");
        if(!FollowupSupport.hash(root.resolve("source-request.json")).equals(manifest.get("source_request_sha256")))throw new IOException("Source request identity differs");
        var original=read(root.resolve("source-request.json"));PeerWorkflowContracts.validate(original);var captured=PeerWorkflowContracts.captured(original);
        if(!same(captured,read(root.resolve("request.json"))))throw new IOException("Captured input bindings differ");var effective=PeerWorkflowContracts.validate(captured);
        if(!same(effective,read(root.resolve("effective-request.json")))||!PeerWorkflowContracts.PROFILE.equals(effective.get("profile"))||!effective.get("profile").equals(manifest.get("profile")))throw new IOException("Effective settings/source profile differ");
        var inputs=new TreeMap<String,Object>();for(String key:PeerWorkflowContracts.INPUTS)if(captured.containsKey(key))inputs.put("inputs/"+key+".tsv",FollowupSupport.hash(root.resolve("inputs/"+key+".tsv")));
        if(!same(inputs,manifest.get("captured_input_sha256")))throw new IOException("Captured input inventory differs");
        var names=new TreeSet<String>();for(String name:PeerWorkflowRuntime.footprint(root.resolve("inputs")).keySet())names.add("inputs/"+name);if(!names.equals(inputs.keySet()))throw new IOException("Undeclared PEER input file");
        PeerWorkflowRuntime.sources(root,true);var prepared=PeerWorkflowFiles.prepare(root,effective,true);PeerWorkflowAudit.outputs(root,effective,prepared,true);PeerWorkflowAudit.provenance(root,effective,manifest);
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings("completed-with-warnings",null)))throw new IOException("Warnings/recovery guidance differ");
    }
    static void seal(Path root)throws IOException {
        var files=new TreeMap<String,Object>();for(String name:PeerWorkflowRuntime.footprint(root).keySet())if(!name.equals("capture-files.json"))files.put(name,FollowupSupport.hash(safe(root,name)));
        json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",files));
    }
    private static Path safe(Path root,String name)throws IOException {Path p=Path.of(name);if(p.isAbsolute()||!p.normalize().equals(p)||name.contains("\\")||name.contains("..")||!root.resolve(p).normalize().startsWith(root))throw new IOException("Unsafe capture member");return root.resolve(p);}
    private static Path newOutput(Path root)throws IOException {
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Preserve existing output and choose a NEW directory");
        Path parent=root.getParent();while(parent!=null&&!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();if(parent==null)throw new IOException("No output parent");
        for(Path p=parent.toRealPath();p!=null;p=p.getParent())if(Files.isRegularFile(p.resolve("capture-files.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Output cannot be nested inside a retained capture");
        Files.createDirectories(root);return root.toRealPath();
    }
    private static long capture(Path source,Path target,long cap)throws IOException {
        if(Files.size(source)>cap)throw new IOException("maximum_input_bytes exceeded; no input truncation");String before=FollowupSupport.hash(source);long total=0;
        try(var input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))>=0){if(count>cap-total)throw new IOException("maximum_input_bytes exceeded during capture; retain partial input");output.write(buffer,0,count);total+=count;}
        }
        if(!before.equals(FollowupSupport.hash(target))||!before.equals(FollowupSupport.hash(source)))throw new IOException("Original input changed during capture; preserve the attempt and retry NEW output");return total;
    }
    private static String sha(byte[] bytes)throws java.security.NoSuchAlgorithmException{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static Map<String,Object> status(String status,boolean complete,String cause){var r=new LinkedHashMap<String,Object>();r.put("status",status);r.put("results_admitted",complete);r.put("complete_result",complete);r.put("required_action",ACTION);if(cause!=null)r.put("error",cause);return r;}
    private static String warnings(String state,String cause){return "# PEER warnings and required actions\n\n**"+state+"**\n\n"+(cause==null?"":cause+"\n\n")+ACTION+"\n\nOriginal matrices use FP32. Prepared input conversion counts, underflows and maximum absolute rounding changes are saved in prepared.json. No value is imputed. Exported latent factors omit known-covariate columns; full original factors/loadings remain in initial/final-state.tsv. Residuals are original joint fitted-model residuals, not an undeclared downstream OLS adjustment. Original iteration history/counters are retained separately from observed executed updates and double-bound monitoring. C-library random streams can differ by host; the seed alone is not cross-host output identity. SHA-256 records integrity, not authentication.\n";}
}
