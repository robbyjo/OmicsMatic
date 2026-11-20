/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Prepared-data native/source GCTA RUN, offline VERIFY and explicit REPLAY. */
public final class GctaWorkflowCli {
    private GctaWorkflowCli() { }
    static final String ENGINE="versioned-gcta-prepared-workflow-1";
    static final String SCOPE="DEVELOPMENT_SCOPE: explicit source edition and native/original engine on prepared inputs. No automatic engine fallback, genotype/phenotype recoding, strand/ID repair, imputation or undeclared cohort filtering. Raw source model, null, partial and diagnostic exports are retained. Original-model VERIFY checks capture integrity, deterministic intake and declared export invariants; it does not refit or authenticate coordinated replacement. Native GRM is recomputed. Runtime flavor and source stochastic differences remain explicit. No whole-paper or whole-tool completion claim.";
    static final String ACTION="SKIP3: dependent analysis skipped; supply the named exact permitted runtime/input or retain the skip if installation is forbidden. FAIL2: NO COMPLETE RESULT; preserve inputs, complete available model/null objects, partials and logs, correct the stated source/data/numeric/convergence/integrity/capacity/process cause and retry NEW output. Never refresh an old hash, clamp a valid unconstrained estimate or silently replace an algorithm/edition/allele/identifier/population/threshold/nonfinite value. Read WARNINGS.md, analysis-status.json, manifest.json, model-objects.json and all worker logs. "+SCOPE;

    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")) {
                out.println("GctaWorkflowCli --request UTF8_JSON | --verify COMPLETE_BUNDLE\nRUN: explicit native GRM/sparse conversion or original GRM/PCA/COJO/REML/LD-score/sparse/binary-fastGWA. VERIFY: offline captured-input/export checks. REPLAY: NEW output, preserving parent capture.\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));out.println("Verified saved GCTA capture. "+SCOPE);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=bytes(source,2_000_000);var raw=parse(original);String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){control(raw,false);verify(resolve(source.getParent(),text(raw,"analysis")));out.println("Verified saved GCTA capture. "+SCOPE);return 0;}
            if(mode.equals("REPLAY")) {
                control(raw,true);Path parent=resolve(source.getParent(),text(raw,"analysis"));verify(parent);
                Path destination=resolve(source.getParent(),text(raw,"out"));outside(parent,destination);
                var request=absoluteCaptured(parent,read(parent.resolve("request.json")));request.put("out",destination.toString());
                if(request.get("engine").equals("original")) {
                    var binding=read(parent.resolve("worker/runtime-bindings.json"));
                    request.put("runtime",raw.containsKey("runtime")?resolve(source.getParent(),text(raw,"runtime")).toString():text(binding,"runtime"));
                    boolean parentWindows=object(binding.get("runtime_sha256"),"runtime pins").containsValue(GctaWorkflowRuntime.WINDOWS);
                    boolean windows=System.getProperty("os.name").startsWith("Windows");
                    if(parentWindows!=windows&&!bool(raw,"allow_runtime_flavor_change",false))throw new IOException("REPLAY runtime flavor differs; explicitly set allow_runtime_flavor_change:true and supply the corresponding exact runtime. Source calibration may differ; parent remains unchanged");
                }else if(raw.containsKey("runtime")||raw.containsKey("allow_runtime_flavor_change"))throw new IOException("Native REPLAY has no original-runtime override");
                return execute(source.getParent(),request,RsemJson.bytes(request),Map.of("analysis",parent.toString(),"seal_sha256",FollowupSupport.hash(parent.resolve("capture-files.json"))),out,err);
            }
            return execute(source.getParent(),raw,original,Map.of(),out,err);
        }catch(GctaWorkflowRuntime.Unavailable unavailable){err.println("SKIP3 — DEPENDENT ANALYSIS SKIPPED: "+unavailable.getMessage()+"\n"+ACTION);return 3;}
        catch(OutOfMemoryError failed){err.println("FAIL2 — NO COMPLETE RESULT: Java heap exhausted; preserve diagnostics and retry NEW output with explicit capacities/heap. "+ACTION);return 2;}
        catch(Exception failed){err.println("FAIL2 — NO COMPLETE RESULT: "+failed+"\n"+ACTION);return 2;}
    }

    private static int execute(Path parent,Map<String,Object> raw,byte[] original,Map<String,Object> replay,PrintStream out,PrintStream err) throws Exception {
        var request=GctaWorkflowContracts.validate(raw);Path root=newOutput(resolve(parent,text(request,"out")));
        var manifest=new LinkedHashMap<String,Object>();manifest.put("schema_version",1);manifest.put("engine",ENGINE);manifest.put("scope",SCOPE);manifest.put("profile",request.get("profile"));manifest.put("model",request.get("model"));manifest.put("selected_engine",request.get("engine"));manifest.put("source_request_sha256",sha(original));
        int code=2;String error=null;boolean interrupted=false;Map<String,Object> objects=Map.of();
        json(root.resolve("analysis-status.json"),state("RUNNING_NO_RESULT_ADMITTED",false,null,"pending"));
        var pins=new TreeMap<String,Object>();var unavailable=new ArrayList<String>();
        try {
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);
            if(!replay.isEmpty())json(root.resolve("replay-source.json"),replay);
            Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));
            long remaining=((Number)request.get("maximum_input_bytes")).longValue();
            for(var binding:GctaWorkflowContracts.bindings(raw)) {
                try {
                    Path target=root.resolve(binding.name());remaining-=capture(resolve(parent,binding.source()),target,remaining);pins.put(binding.name(),FollowupSupport.hash(target));
                }catch(GctaWorkflowRuntime.Unavailable missing){unavailable.add(missing.getMessage());}
            }
            var captured=GctaWorkflowContracts.captured(raw);json(root.resolve("request.json"),captured);
            var effective=GctaWorkflowContracts.validate(captured);json(root.resolve("effective-request.json"),effective);
            manifest.put("captured_input_sha256",pins);
            if(!unavailable.isEmpty())throw new GctaWorkflowRuntime.Unavailable(String.join("; ",unavailable));
            var prepared=GctaWorkflowFiles.prepare(root,effective,false);
            if(request.get("model").equals("REML")&&list(effective.get("grms"),"grms").size()>1) {
                var lines=new StringBuilder();for(int i=0;i<list(effective.get("grms"),"grms").size();i++)lines.append("../inputs/grm").append(i).append('\n');
                Files.writeString(root.resolve("worker/grms.list"),lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }
            GctaWorkflowRuntime.run(root,parent,request,prepared,manifest);
            for(var entry:pins.entrySet())if(!FollowupSupport.hash(root.resolve(entry.getKey())).equals(entry.getValue()))throw new IOException("Captured original input changed during fitting");
            GctaWorkflowFiles.prepare(root,effective,true);objects=GctaWorkflowAudit.outputs(root,effective,prepared,false);GctaWorkflowAudit.provenance(root,effective,manifest,objects);code=0;
        }catch(GctaWorkflowRuntime.Unavailable missing){code=3;error=missing.toString();}
        catch(OutOfMemoryError failed){error="Java heap exhausted; preserve all available inputs/objects and retry NEW output with explicit heap/capacities";}
        catch(Exception failed){error=failed.toString();if(failed instanceof InterruptedException||failed instanceof InterruptedIOException||Thread.currentThread().isInterrupted()){interrupted=true;Thread.interrupted();}}
        if(!manifest.containsKey("captured_input_sha256"))manifest.put("captured_input_sha256",pins);
        String kind=objects.getOrDefault("result_kind","model-result").toString();
        String status=code==0?(kind.equals("null-selection")?"completed-null-with-warnings":"completed-with-warnings"):code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
        manifest.put("analysis_status",status);manifest.put("results_admitted",code==0);manifest.put("result_kind",kind);if(error!=null)manifest.put("error",error);
        json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state(status,code==0,error,kind));
        Files.writeString(root.resolve("WARNINGS.md"),warnings(root,status,error),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try{seal(root);if(code==0)verify(root);}
        catch(Exception failed){code=2;error=failed.toString();manifest.put("analysis_status","FAIL2_NO_COMPLETE_RESULT");manifest.put("results_admitted",false);manifest.put("verification_error",error);json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state("FAIL2_NO_COMPLETE_RESULT",false,error,kind));Files.writeString(root.resolve("WARNINGS.md"),warnings(root,"FAIL2_NO_COMPLETE_RESULT",error),StandardCharsets.UTF_8);seal(root);}
        if(interrupted)Thread.currentThread().interrupt();
        (code==0?out:err).println((code==0?"Completed GCTA "+kind+": ":code==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO COMPLETE RESULT: ")+root);
        if(error!=null)err.println("CAUSE / REQUIRED RECOVERY: "+error);
        err.println("WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return code;
    }

    static void verify(Path root) throws Exception {
        root=root.toAbsolutePath().normalize();var seal=read(root.resolve("capture-files.json"),16_000_000);keys(seal,Set.of("schema_version","authentication","files"));
        if(integer(seal,"schema_version",0,1,1)!=1||!Boolean.FALSE.equals(seal.get("authentication")))throw new IOException("Invalid capture seal/authentication claim");
        var expected=object(seal.get("files"),"capture file pins");var actual=GctaWorkflowRuntime.footprint(root);actual.remove("capture-files.json");
        if(!actual.keySet().equals(expected.keySet()))throw new IOException("Capture file population differs");
        for(var entry:expected.entrySet())if(!FollowupSupport.hash(safe(root,entry.getKey())).equals(entry.getValue()))throw new IOException("Capture hash differs: "+entry.getKey());
        var manifest=read(root.resolve("manifest.json"));var status=read(root.resolve("analysis-status.json"));
        Set<String> completed=Set.of("completed-with-warnings","completed-null-with-warnings");
        if(!ENGINE.equals(manifest.get("engine"))||!SCOPE.equals(manifest.get("scope"))||!completed.contains(manifest.get("analysis_status"))||!Objects.equals(manifest.get("analysis_status"),status.get("status"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!Boolean.TRUE.equals(status.get("results_admitted"))||!ACTION.equals(status.get("required_action")))throw new IOException("Failed/skipped/incomplete captures are not admitted results");
        var original=read(root.resolve("source-request.json"));GctaWorkflowContracts.validate(original);
        if(!FollowupSupport.hash(root.resolve("source-request.json")).equals(manifest.get("source_request_sha256")))throw new IOException("Original request identity differs");
        var captured=GctaWorkflowContracts.captured(original);if(!same(captured,read(root.resolve("request.json"))))throw new IOException("Captured request differs from original input bindings");
        var effective=GctaWorkflowContracts.validate(captured);if(!same(effective,read(root.resolve("effective-request.json"))))throw new IOException("Effective scientific settings differ");
        if(!Objects.equals(effective.get("model"),manifest.get("model"))||!Objects.equals(effective.get("profile"),manifest.get("profile"))||!Objects.equals(effective.get("engine"),manifest.get("selected_engine")))throw new IOException("Model/profile/engine records differ");
        var inputPins=new TreeMap<String,Object>();for(var binding:GctaWorkflowContracts.bindings(captured))inputPins.put(binding.name(),FollowupSupport.hash(root.resolve(binding.name())));
        if(!same(inputPins,manifest.get("captured_input_sha256")))throw new IOException("Captured input inventory differs");
        var inputNames=new TreeSet<String>();for(String name:GctaWorkflowRuntime.footprint(root.resolve("inputs")).keySet())inputNames.add("inputs/"+name);
        if(!inputNames.equals(inputPins.keySet()))throw new IOException("Undeclared input capture file");
        if(effective.get("model").equals("REML")&&list(effective.get("grms"),"grms").size()>1) {
            var lines=new StringBuilder();for(int i=0;i<list(effective.get("grms"),"grms").size();i++)lines.append("../inputs/grm").append(i).append('\n');
            GctaWorkflowFiles.compareOrWrite(root.resolve("worker/grms.list"),lines.toString().getBytes(StandardCharsets.UTF_8),true);
        }
        var prepared=GctaWorkflowFiles.prepare(root,effective,true);var objects=GctaWorkflowAudit.outputs(root,effective,prepared,true);
        GctaWorkflowAudit.provenance(root,effective,manifest,objects);
        String kind=objects.getOrDefault("result_kind","model-result").toString();if(!Objects.equals(kind,manifest.get("result_kind"))||!Objects.equals(kind,status.get("result_kind")))throw new IOException("Null/model result-kind record differs");
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings(root,manifest.get("analysis_status").toString(),null)))throw new IOException("Warnings and recovery instructions differ");
    }
    static void seal(Path root) throws IOException {
        var pins=new TreeMap<String,Object>();for(String name:GctaWorkflowRuntime.footprint(root).keySet())if(!name.equals("capture-files.json"))pins.put(name,FollowupSupport.hash(safe(root,name)));
        json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",pins));
    }
    private static Path safe(Path root,String name) throws IOException {
        Path relative=Path.of(name);if(relative.isAbsolute()||!relative.normalize().equals(relative)||name.contains("\\")||name.contains("..")||!root.resolve(relative).normalize().startsWith(root))throw new IOException("Unsafe capture member");return root.resolve(relative);
    }
    private static void control(Map<String,Object> request,boolean replay) throws IOException {
        keys(request,replay?Set.of("schema_version","mode","analysis","out","runtime","allow_runtime_flavor_change"):Set.of("schema_version","mode","analysis"));
        if(integer(request,"schema_version",0,1,1)!=1)throw new IOException("Require control schema_version 1");text(request,"analysis");
        if(replay){text(request,"out");if(request.containsKey("runtime"))text(request,"runtime");bool(request,"allow_runtime_flavor_change",false);}
    }
    private static Map<String,Object> absoluteCaptured(Path root,Map<String,Object> captured) throws IOException {
        var result=new LinkedHashMap<String,Object>(captured);
        if(result.containsKey("genotypes")){var files=object(result.get("genotypes"),"genotypes");for(String key:List.of("bed","bim","fam"))files.put(key,resolve(root,text(files,key)).toString());result.put("genotypes",files);}
        if(result.containsKey("grms")){var matrices=new ArrayList<Map<String,Object>>();for(Object item:list(result.get("grms"),"grms")){var files=object(item,"GRM");for(String key:new ArrayList<>(files.keySet()))files.put(key,resolve(root,text(files,key)).toString());matrices.add(files);}result.put("grms",matrices);}
        if(result.containsKey("sparse")){var files=object(result.get("sparse"),"sparse");for(String key:List.of("values","ids"))files.put(key,resolve(root,text(files,key)).toString());result.put("sparse",files);}
        for(String key:List.of("phenotype","qcovar","covar","summary","condition","extract"))if(result.containsKey(key))result.put(key,resolve(root,text(result,key)).toString());return result;
    }
    private static Path newOutput(Path root) throws IOException {
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Preserve existing output and choose a NEW directory");
        Path existing=root.getParent();while(existing!=null&&!Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
        if(existing==null)throw new IOException("Cannot resolve output parent");
        for(Path p=existing.toRealPath();p!=null;p=p.getParent())if(Files.isRegularFile(p.resolve("capture-files.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Output cannot be nested in a retained capture; choose a NEW sibling directory");
        Files.createDirectories(root.getParent());return Files.createDirectory(root);
    }
    private static void outside(Path prior,Path proposed) throws IOException {
        Path parent=prior.toRealPath(),node=proposed;var missing=new ArrayDeque<Path>();
        while(!Files.exists(node,LinkOption.NOFOLLOW_LINKS)){if(node.getParent()==null)throw new IOException("Cannot resolve replay output");missing.addFirst(node.getFileName());node=node.getParent();}
        Path actual=node.toRealPath();for(Path name:missing)actual=actual.resolve(name);if(actual.normalize().startsWith(parent))throw new IOException("REPLAY output must be outside the retained parent capture");
    }
    private static long capture(Path source,Path target,long cap) throws IOException {
        if(!Files.exists(source)||!Files.isReadable(source))throw new GctaWorkflowRuntime.Unavailable("Required permitted input is unavailable: "+source);
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>cap)throw new IOException("Input nonregular or beyond maximum_input_bytes: "+source);
        String before=FollowupSupport.hash(source);long count=0;
        try(var input=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input capture interrupted");if(n>cap-count)throw new IOException("Input grew beyond capture capacity");output.write(buffer,0,n);count+=n;}
        }
        if(!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(target)))throw new IOException("Input changed during capture");return count;
    }
    private static byte[] bytes(Path path,long cap) throws IOException {
        if(!Files.exists(path))throw new GctaWorkflowRuntime.Unavailable("Required JSON request is unavailable: "+path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>cap)throw new IOException("Require bounded regular UTF-8 request");return Files.readAllBytes(path);
    }
    private static Map<String,Object> parse(byte[] bytes) throws IOException {
        return object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()),"request");
    }
    private static String sha(byte[] bytes) {try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static Map<String,Object> state(String status,boolean admitted,String error,String kind) {
        var state=new LinkedHashMap<String,Object>();state.put("schema_version",1);state.put("status",status);state.put("results_admitted",admitted);state.put("result_kind",kind);state.put("required_action",ACTION);if(error!=null)state.put("error",error);return state;
    }
    private static String warnings(Path root,String status,String error) {
        String precision="";
        try {
            if(Files.isRegularFile(root.resolve("model-objects.json"),LinkOption.NOFOLLOW_LINKS)
                    &&Boolean.TRUE.equals(read(root.resolve("model-objects.json")).get("rounded_zero_variance_total")))
                precision="**SOURCE PRECISION WARNING:** original REML variances/SE use six fixed decimals and can round nonzero internal estimates to zero. These printed values are preserved; do not reinterpret them as exact zeros or replace them. Read the original scientific-notation sampling covariance and source h2 exports. If internal parameter precision is required, retain this capture and obtain a separately qualified higher-precision export.\n\n";
        }catch(IOException unavailable){precision="The optional precision note could not be read; retain diagnostics and inspect model-objects.json directly.\n\n";}
        return "# GCTA warnings and required actions\n\nStatus: "+status+"\n\n"+(error==null?"":error+"\n\n")+ACTION+"\n\n"
            +precision
            +"Original Windows/Linux runtime flavors can yield different stochastic calibration at the same seed. Cross-flavor replay requires explicit approval in the request, and all differences remain retained.\n\n"
            +"Source and native GRM zero/N=0 placeholders are not complete estimates. Valid converged unconstrained variance/heritability estimates are not clamped to 0..1.\n\n"
            +"COJO effective n is an original positive real, not a bounded integer; adjusted LD scores may be negative. Model internal state that the original program does not export is not invented.\n\n"
            +"Resources: input/output, metadata, entry and logical Java-work caps are explicit. External worker timeout/output monitoring is not an OS memory/file quota.\n\n"
            +"Inspect every original stdout/stderr/log and all available model/null/partial exports under worker/. Original warnings remain verbatim there. SHA256 provides integrity, not authentication.\n";
    }
}
