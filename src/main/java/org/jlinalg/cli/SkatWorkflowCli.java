/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SkatWorkflowContracts.require;

/** Typed original SKAT matrix/null-model RUN, offline VERIFY and original REPLAY. */
public final class SkatWorkflowCli {
    private SkatWorkflowCli() { }
    static final String ENGINE="original-SKAT-2.2.5-prepared-matrix";
    static final String SCOPE=SkatWorkflowAudit.SCOPE+" "+SkatWorkflowRuntime.SCOPE
        +" Prepared autosomal matrix/null-model workflows only;not SSD,sex-chromosome,SAIGE,REGENIE,REMETA,SEAK,COAST or whole-paper reproduction. Capacities are logical/monitored,not hard OS RAM/job quotas.";
    static final String ACTION="Read WARNINGS.md,analysis-status.json,manifest.json,worker/conditions.tsv and raw logs. "
        +"SKIP3: supply the exact permitted input,R4.6.1 x86_64 executable and declared SKAT2.2.5 package profile;download/install privately where allowed,otherwise retain the skip. "
        +"FAIL2: no complete result;preserve raw,null,failed,partial and superseded evidence,resolve the named input,numerical,capacity,process or integrity cause and retry NEW output. "
        +"Never silently repair IDs,NaN/Inf,p-value endpoints,weights or scientific options. Cell Ranger remains excluded.";
    private static final long REQUEST_BYTES=2_097_152,SEAL_BYTES=268_435_456;
    record Verified(String status,boolean admitted) { }

    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")) {
                out.println("skat-tools --request UTF8_JSON | --verify CAPTURE\nRUN: original SKAT2.2.5 prepared continuous/binary matrix tests. VERIFY: offline integrity and output relationships. REPLAY: same scientific request into NEW output.\nOnly admitted/p-values.tsv is the final export,with analysis-status.json results_admitted=true and a verified complete seal. results/p-values.tsv is the original candidate,also retained on failure.\n"+ACTION+"\n"+SCOPE);return 0;
            }
            if(args.length==2&&args[0].equals("--verify"))return verified(Path.of(args[1]),out,err);
            require(args.length==2&&args[0].equals("--request"),"Require --request JSON,--verify CAPTURE or --help");Path path=Path.of(args[1]).toAbsolutePath().normalize();byte[] bytes=SkatWorkflowFiles.bytes(path,REQUEST_BYTES);Map<String,Object> raw=parse(bytes);String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){control(raw,false);return verified(resolve(path.getParent(),text(raw,"analysis")),out,err);}
            if(mode.equals("REPLAY"))return replay(path.getParent(),raw,out,err);
            return execute(path.getParent(),raw,bytes,Map.of(),out,err);
        }catch(SkatWorkflowContracts.Unavailable error){err.println("SKIP3 — DEPENDENT ANALYSIS SKIPPED: "+error.getMessage()+"\n"+ACTION);return 3;}
        catch(OutOfMemoryError error){err.println("FAIL2 — NO COMPLETE RESULT: Java heap exhausted. Preserve the attempt,provide explicit adequate heap/capacities and retry NEW output. "+ACTION);return 2;}
        catch(Exception error){err.println("FAIL2 — NO COMPLETE RESULT: "+error+"\n"+ACTION);return 2;}
    }
    private static Map<String,Object> parse(byte[] bytes)throws IOException{return SkatWorkflowFiles.map(SimpleJson.parse(SkatWorkflowFiles.utf8(bytes)));}
    private static void control(Map<String,Object> raw,boolean replay)throws IOException {
        keys(raw,replay?Set.of("schema_version","mode","analysis","out","rscript","r_libraries"):Set.of("schema_version","mode","analysis"));require(integer(raw,"schema_version",0,1,1)==1,"Require schema_version1");text(raw,"analysis");if(replay)text(raw,"out");
    }
    private static int verified(Path path,PrintStream out,PrintStream err)throws IOException {
        Verified result=verify(path);out.println("Saved capture verified: "+result.status()+";results_admitted="+result.admitted()+". "+SkatWorkflowAudit.SCOPE);
        if(!result.admitted())err.println("RETAINED "+result.status()+" — no complete result is admitted. Verification did not rerun the analysis. "+ACTION);return 0;
    }
    private static int replay(Path parent,Map<String,Object> raw,PrintStream out,PrintStream err)throws IOException {
        control(raw,true);Path prior=resolve(parent,text(raw,"analysis")).toRealPath();Verified result=verify(prior);require(result.admitted(),"REPLAY requires a completed admitted parent;retain failed/skipped evidence and make a corrected NEW RUN request instead");
        Path target=resolve(parent,text(raw,"out"));Path realTarget=SkatWorkflowFiles.resolvedOutput(target);require(!realTarget.startsWith(prior)&&!prior.startsWith(realTarget),"REPLAY output must be NEW and physically outside the parent capture,including aliases");
        Map<String,Object> originalSeal=SkatWorkflowFiles.pin(prior.resolve("capture-files.json"));var request=SkatWorkflowFiles.read(prior.resolve("request.json"),REQUEST_BYTES);
        for(String key:SkatWorkflowContracts.INPUTS)if(request.containsKey(key))request.put(key,prior.resolve("inputs/"+key+".tsv").toString());
        request.put("out",realTarget.toString());if(raw.containsKey("rscript"))request.put("rscript",resolve(parent,text(raw,"rscript")).toString());if(raw.containsKey("r_libraries"))request.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16).stream().map(s->resolve(parent,s).toString()).toList());
        Map<String,Object> replay=Map.of("parent_analysis",prior.toString(),"parent_seal",originalSeal,"scientific_options_changed",false,"runtime_overrides_explicit",raw.containsKey("rscript")||raw.containsKey("r_libraries"));
        int code=execute(prior,request,RsemJson.bytes(request),replay,out,err);
        try{SkatWorkflowRuntime.matches(originalSeal,SkatWorkflowFiles.pin(prior.resolve("capture-files.json")),"REPLAY parent seal");verify(prior);}
        catch(IOException failure){failFinalization(realTarget,"Parent changed during REPLAY: "+failure,out,err);return 2;}return code;
    }
    private static int execute(Path parent,Map<String,Object> raw,byte[] original,Map<String,Object> replay,PrintStream out,PrintStream err)throws IOException {
        Map<String,Object> validated=SkatWorkflowContracts.validate(raw);Path root=SkatWorkflowFiles.output(resolve(parent,text(validated,"out")));var manifest=new LinkedHashMap<String,Object>();var inputPins=new LinkedHashMap<String,Object>();
        manifest.put("schema_version",1);manifest.put("engine",ENGINE);manifest.put("scope",SCOPE);manifest.put("source_request_sha256",SkatWorkflowFiles.hash(original));manifest.put("source_request_directory",parent.toString());manifest.put("original_analysis_path",root.toString());manifest.put("source_capture_complete",false);manifest.put("input_pins",inputPins);manifest.put("phase","capture");
        var captured=new LinkedHashMap<String,Object>(raw);captured.put("out",".");for(String key:SkatWorkflowContracts.INPUTS)if(raw.containsKey(key))captured.put(key,"inputs/"+key+".tsv");captured.put("rscript",resolve(parent,text(validated,"rscript")).toString());captured.put("r_libraries",strings(validated.get("r_libraries"),"r_libraries",1,16).stream().map(s->resolve(parent,s).toString()).toList());
        Map<String,Object> request=SkatWorkflowContracts.validate(captured);int code=2;String error=null;
        SkatWorkflowFiles.json(root.resolve("analysis-status.json"),state("RUNNING_NO_RESULT_ADMITTED",false,null));
        try {
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);SkatWorkflowFiles.json(root.resolve("request.json"),captured);SkatWorkflowFiles.json(root.resolve("effective-request.json"),request);if(!replay.isEmpty())SkatWorkflowFiles.json(root.resolve("replay-source.json"),replay);
            for(String name:List.of("inputs","worker","results"))Files.createDirectory(root.resolve(name));SkatWorkflowRuntime.sources(root,false);manifest.put("source_capture_complete",true);
            long total=0;for(String key:SkatWorkflowContracts.INPUTS)if(validated.containsKey(key)){Path source=resolve(parent,text(validated,key)),target=root.resolve("inputs/"+key+".tsv");total+=SkatWorkflowFiles.capture(source,target,SkatWorkflowFiles.cap(request,"maximum_input_bytes")-total);inputPins.put(key,SkatWorkflowFiles.pin(target));}
            manifest.put("phase","input-intake");SkatWorkflowFiles.prepare(root,request,false);manifest.put("phase","original-runtime");SkatWorkflowRuntime.run(root,root,request,manifest);manifest.put("phase","output-audit");var audit=SkatWorkflowAudit.completed(root,request);SkatWorkflowFiles.json(root.resolve("output-audit.json"),audit);
            Files.createDirectory(root.resolve("admitted"));Files.copy(root.resolve("results/p-values.tsv"),root.resolve("admitted/p-values.tsv"));manifest.put("phase","completed");code=0;
        }catch(SkatWorkflowContracts.Unavailable failure){code=3;error=failure.toString();}
        catch(OutOfMemoryError failure){error="Java heap exhausted;no complete result is admitted. Preserve the attempt and retry NEW output with adequate explicit heap/capacities.";}
        catch(Exception failure){error=failure.toString();if(failure instanceof InterruptedException)Thread.currentThread().interrupt();}
        String status=code==0?"COMPLETED_WITH_WARNINGS":code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
        manifest.put("analysis_status",status);manifest.put("results_admitted",code==0);if(error!=null)manifest.put("error",error);SkatWorkflowFiles.json(root.resolve("manifest.json"),manifest);SkatWorkflowFiles.json(root.resolve("analysis-status.json"),state(status,code==0,error));Files.writeString(root.resolve("WARNINGS.md"),warnings(root,status,error,manifest),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try{seal(root,request,code==0);verify(root);}
        catch(Exception failure){failFinalization(root,"Capture finalization refused: "+failure,out,err);return 2;}
        out.println("SKAT capture: "+root+"\nStatus: "+status+";results_admitted="+(code==0));err.println("WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return code;
    }
    private static Map<String,Object> state(String status,boolean admitted,String error){var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("engine",ENGINE);result.put("profile",SkatWorkflowContracts.PROFILE);result.put("status",status);result.put("results_admitted",admitted);result.put("required_action",ACTION);if(error!=null)result.put("error",error);return result;}
    private static void seal(Path root,Map<String,Object> request,boolean admitted)throws IOException {
        SkatWorkflowFiles.json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"admission_limits_enforced",admitted,"files",SkatWorkflowFiles.inventory(root,request,admitted)));
    }
    static Verified verify(Path analysis)throws IOException {
        try{return verifyCapture(analysis);}catch(SkatWorkflowContracts.Unavailable failure){throw new IOException("Missing or unsupported captured evidence;verification cannot admit a result",failure);}
    }
    private static Verified verifyCapture(Path analysis)throws IOException {
        Path root=analysis.toAbsolutePath().normalize().toRealPath();Map<String,Object> seal=SkatWorkflowFiles.read(root.resolve("capture-files.json"),SEAL_BYTES);keys(seal,Set.of("schema_version","authentication","admission_limits_enforced","files"));require(integer(seal,"schema_version",0,1,1)==1&&Boolean.FALSE.equals(seal.get("authentication")),"Invalid integrity seal;SHA-256 is not authenticated producer provenance");
        Map<String,Object> raw=SkatWorkflowFiles.read(root.resolve("request.json"),REQUEST_BYTES),request=SkatWorkflowContracts.validate(raw);require(text(raw,"out").equals("."),"Captured request out must be local");var expected=SkatWorkflowFiles.map(seal.get("files"));var actual=SkatWorkflowFiles.inventory(root,request,false);require(actual.keySet().equals(expected.keySet()),"Missing or extra captured files");for(String name:actual.keySet())SkatWorkflowRuntime.matches(SkatWorkflowFiles.map(expected.get(name)),SkatWorkflowFiles.map(actual.get(name)),name);
        require(Arrays.equals(RsemJson.bytes(request),SkatWorkflowFiles.bytes(root.resolve("effective-request.json"),REQUEST_BYTES)),"Changed normalized scientific request");var source=SkatWorkflowContracts.validate(parse(SkatWorkflowFiles.bytes(root.resolve("source-request.json"),REQUEST_BYTES)));require(Arrays.equals(science(source),science(request)),"Captured scientific options differ from original request");
        var manifest=SkatWorkflowFiles.read(root.resolve("manifest.json"),SEAL_BYTES);var state=SkatWorkflowFiles.read(root.resolve("analysis-status.json"),REQUEST_BYTES);require(integer(manifest,"schema_version",0,1,1)==1&&integer(state,"schema_version",0,1,1)==1&&ENGINE.equals(manifest.get("engine"))&&ENGINE.equals(state.get("engine"))&&SkatWorkflowContracts.PROFILE.equals(state.get("profile"))&&ACTION.equals(state.get("required_action")),"Changed captured schema/engine/profile/recovery instructions");require(SkatWorkflowFiles.hash(SkatWorkflowFiles.bytes(root.resolve("source-request.json"),REQUEST_BYTES)).equals(manifest.get("source_request_sha256")),"Changed raw source request");
        require(state.get("results_admitted") instanceof Boolean&&manifest.get("results_admitted") instanceof Boolean,"Missing/invalid admission flag");
        String status=text(state,"status");boolean admitted=bool(state,"results_admitted",false);require(Set.of("COMPLETED_WITH_WARNINGS","SKIP3_DEPENDENT_ANALYSIS_SKIPPED","FAIL2_NO_COMPLETE_RESULT").contains(status)&&admitted==status.equals("COMPLETED_WITH_WARNINGS")&&state.get("results_admitted").equals(manifest.get("results_admitted"))&&status.equals(manifest.get("analysis_status"))&&Boolean.valueOf(admitted).equals(seal.get("admission_limits_enforced")),"Contradictory saved admission status");
        require(admitted?!state.containsKey("error"):state.get("error") instanceof String&&!text(state,"error").isEmpty(),"Missing or contradictory final error detail");require(java.util.Objects.equals(state.get("error"),manifest.get("error")),"Saved error details differ");
        require(Files.isRegularFile(root.resolve("WARNINGS.md"),LinkOption.NOFOLLOW_LINKS),"Missing prominent warnings");String warning=SkatWorkflowFiles.utf8(SkatWorkflowFiles.bytes(root.resolve("WARNINGS.md"),SEAL_BYTES));require(warning.equals(warnings(root,status,(String)state.get("error"),manifest)),"Saved warnings/conditions/scope/recovery actions differ");
        if(Boolean.TRUE.equals(manifest.get("source_capture_complete")))SkatWorkflowRuntime.sources(root,true);else require(!admitted,"Completed result lacks original source capture");
        var pins=SkatWorkflowFiles.map(manifest.get("input_pins"));for(String key:pins.keySet()){require(SkatWorkflowContracts.INPUTS.contains(key)&&raw.containsKey(key),"Unexpected captured input fingerprint");SkatWorkflowRuntime.matches(SkatWorkflowFiles.map(pins.get(key)),SkatWorkflowFiles.pin(root.resolve("inputs/"+key+".tsv")),key);}
        for(String key:SkatWorkflowContracts.INPUTS)if(raw.containsKey(key))require(text(raw,key).equals("inputs/"+key+".tsv"),"Captured input path is not local");
        if(admitted) {
            SkatWorkflowFiles.inventory(root,request,true);for(String key:SkatWorkflowContracts.INPUTS)if(raw.containsKey(key))require(pins.containsKey(key),"Incomplete admitted input capture");var report=SkatWorkflowAudit.completed(root,request);require(Arrays.equals(RsemJson.bytes(report),SkatWorkflowFiles.bytes(root.resolve("output-audit.json"),SEAL_BYTES)),"Changed structural output-audit report");SkatWorkflowRuntime.matches(SkatWorkflowFiles.pin(root.resolve("results/p-values.tsv")),SkatWorkflowFiles.pin(root.resolve("admitted/p-values.tsv")),"final admitted p-values");
        }else require(!Files.exists(root.resolve("admitted/p-values.tsv"),LinkOption.NOFOLLOW_LINKS),"Failed/skipped capture retains a final admitted export");
        return new Verified(status,admitted);
    }
    private static byte[] science(Map<String,Object> request)throws IOException {
        var result=new LinkedHashMap<>(request);for(String key:List.of("out","rscript","r_libraries"))result.remove(key);for(String key:SkatWorkflowContracts.INPUTS)if(result.containsKey(key))result.put(key,"declared-input-present");return RsemJson.bytes(result);
    }
    static String warnings(Path root,String status,String error,Map<String,Object> manifest)throws IOException {
        var text=new StringBuilder("# SKAT WARNINGS / REQUIRED ACTIONS\n\n**"+status+"**\n\n");if(error!=null)text.append("No complete result is admitted.\n\n    ").append(error.replace("\n","\n    ")).append("\n\n");
        text.append(ACTION).append("\n\n").append(SCOPE).append("\n\nOnly admitted/p-values.tsv is a final export when results_admitted=true and the complete capture verifies. The original results/p-values.tsv is a retained candidate,including on failure. Untested sets and source-reported bounds export NA;numeric zero is not an exact probability or infinite significance. Any failed set or unaccepted policy outcome withholds the complete batch;explicitly accepted untested sets remain NA.\n\n");
        text.append("Some original SKAT integration fallbacks have no returned indicator. allow_reported_fallback governs reported flags/conditions only;this workflow cannot certify hidden upstream convergence. Full original objects,conditions and RNG states are retained.\n\n");
        Path sets=root.resolve("worker/set-results.tsv");if(Files.isRegularFile(sets)) {
            var failures=new StringBuilder();var notes=new StringBuilder();int[] counts={0,0};
            SkatWorkflowFiles.rows(sets,SEAL_BYTES,14,100_000,(i,v)->{
                String issue=SkatWorkflowRuntime.unhex(v[11]),note=SkatWorkflowRuntime.unhex(v[12]),bound=SkatWorkflowRuntime.unhex(v[13]);
                String prefix="    set="+v[0]+";id="+v[1]+";kind="+v[6]+";result_admitted="+v[10]+"\n";
                if(!issue.isEmpty()){if(counts[0]++<100)failures.append(prefix).append("    ").append(issue.replace("\n","\n    ")).append('\n');}
                else if(!note.isEmpty()||!bound.isEmpty()){if(counts[1]++<100)notes.append(prefix).append("    ").append((note+(bound.isEmpty()?"":"\nSource bound: "+bound)).replace("\n","\n    ")).append('\n');}
            });
            text.append("## Set admission failures (").append(counts[0]).append(";first100 failures shown)\n\n").append(failures).append('\n');
            text.append("## Other set notes/bounds (").append(counts[1]).append(";first100 shown)\n\n").append(notes).append('\n');
            text.append("Use a NEW output after correcting a failed set. Accept a source test substitution or adjustment omission only when scientifically intended,using its explicit admission flag. For an untested set,correct the declared variants/filters or explicitly retain an untested NA outcome. Numerical fallback acceptance does not certify unreported upstream convergence. All set rows remain in worker/set-results.tsv.\n\n");
        }
        if(manifest.containsKey("runtime_package_built_warnings"))text.append("## Original package build metadata\n\n").append("Binaries may have been built under a different R patch release. The recorded runtime/file fingerprints define this attempt;they do not establish a uniform compiler build. Supply a permitted matching runtime/library and qualify NEW output if replacing dependencies.\n\n    ").append(RsemJson.json(manifest.get("runtime_package_built_warnings"))).append("\n\n");
        Path conditions=root.resolve("worker/conditions.tsv");if(Files.isRegularFile(conditions)) {
            text.append("## Original conditions (first100;all retained in worker/conditions.tsv and conditions.rds)\n\n");
            SkatWorkflowFiles.rows(conditions,SEAL_BYTES,5,1_000_000,(i,v)->{if(i<=100)text.append("    ").append(v[1]).append(";set=").append(v[2]).append(';').append(v[3]).append(": ").append(SkatWorkflowRuntime.unhex(v[4]).replace("\n","\n    ")).append('\n');},true);
        }
        return text.toString();
    }
    private static void failFinalization(Path root,String error,PrintStream out,PrintStream err)throws IOException {
        Path admitted=root.resolve("admitted/p-values.tsv");if(Files.isRegularFile(admitted,LinkOption.NOFOLLOW_LINKS))Files.move(admitted,root.resolve("worker/unadmitted-p-values.tsv"));
        var manifest=SkatWorkflowFiles.read(root.resolve("manifest.json"),SEAL_BYTES);manifest.put("analysis_status","FAIL2_NO_COMPLETE_RESULT");manifest.put("results_admitted",false);manifest.put("finalization_error",error);manifest.put("error",error);SkatWorkflowFiles.json(root.resolve("manifest.json"),manifest);SkatWorkflowFiles.json(root.resolve("analysis-status.json"),state("FAIL2_NO_COMPLETE_RESULT",false,error));Files.writeString(root.resolve("WARNINGS.md"),warnings(root,"FAIL2_NO_COMPLETE_RESULT",error,manifest),StandardCharsets.UTF_8);
        Path old=root.resolve("capture-files.json");if(Files.exists(old))Files.move(old,root.resolve("worker/initial-refused-capture-seal.json"));Map<String,Object> request=SkatWorkflowContracts.validate(SkatWorkflowFiles.read(root.resolve("request.json"),REQUEST_BYTES));seal(root,request,false);out.println("Failed capture preserved: "+root);err.println("FAIL2 — NO COMPLETE RESULT: "+error+"\n"+ACTION);
    }
}
