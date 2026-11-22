/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit versioned prepared-input QTLtools operations with retained raw exports. */
public final class QtltoolsWorkflowCli {
    private QtltoolsWorkflowCli() { }
    static final String ENGINE="qtltools-prepared-workflow-1";
    static final String SCOPE="Prepared-input operations for explicit QTLtools1.1,1.2,1.3.1 builds;native only selected1.3.1 fdensity. No edition/engine fallback,identifier/allele/coordinate repair,input cleaning,automatic index creation or FDR threshold generation. Original sample intersection,missing-data handling,normalization,grouping,rounding and edition-specific behavior remain upstream. VERIFY checks exact captured implementation,inventory,intake,provenance and complete export invariants;it does not refit original models or authenticate coordinated replacement. Native fdensity is fully recomputed. REPLAY reruns into NEW output. No whole-paper or whole-tool claim.";
    static final String ACTION="SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply the named exact permitted executable,libraries,input/index or compatible Linux x86_64/Python3.11+/WSL environment. Download/private installation is allowed where permitted;if installation is prohibited,retain the skip and state what must be provided. FAIL2: NO COMPLETE RESULT. Preserve raw inputs,logs,null/partial/failed exports and process ledgers;correct the named data,numeric,source,capacity,timeout or cleanup cause and retry NEW output. Never overwrite old evidence,refresh an old source hash,repair NaN/Inf or substitute an edition/method. Read WARNINGS.md,analysis-status.json,manifest.json,output-audit.json and worker logs.";

    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")){out.println("QtltoolsWorkflowCli --request UTF8_JSON | --verify CAPTURE\nRUN: CIS_NOMINAL,CIS_PERMUTE,CIS_CONDITIONAL,TRANS_NOMINAL,TRANS_PERMUTE,TRANS_SAMPLE,TRANS_ADJUST,MBV,QUAN,FDENSITY. Explicit edition and original/native engine required. VERIFY: offline saved checks. REPLAY: rerun into NEW output.\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));out.println("Verified saved QTLtools capture. "+SCOPE);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request JSON,--verify CAPTURE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new QtltoolsWorkflowRuntime.Unavailable("Required request file unavailable: "+source);if(Files.size(source)>2_000_000)throw new IOException("Request exceeds2MB capacity");
            byte[] original=Files.readAllBytes(source);var raw=object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(original)).toString()),"request");String mode=text(raw,"mode");
            if(mode.equals("VERIFY")||mode.equals("REPLAY")) {
                boolean replay=mode.equals("REPLAY");keys(raw,replay?Set.of("schema_version","mode","analysis","out","runtime"):Set.of("schema_version","mode","analysis"));if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require control schema_version1");
                Path parent=resolve(source.getParent(),text(raw,"analysis"));verify(parent);
                if(!replay){out.println("Verified saved QTLtools capture. "+SCOPE);return 0;}
                Path destination=resolve(source.getParent(),text(raw,"out"));if(destination.startsWith(parent.toRealPath()))throw new IOException("Replay output must be outside its parent capture");
                String parentSeal=FollowupSupport.hash(parent.resolve("capture-files.json"));var request=QtltoolsWorkflowContracts.paths(read(parent.resolve("request.json")),parent,false);request.put("out",destination.toString());
                if(text(request,"engine").equals("original"))request.put("runtime",raw.containsKey("runtime")?object(raw.get("runtime"),"runtime"):read(parent.resolve("worker/runtime-bindings.json")));
                else if(raw.containsKey("runtime"))throw new IOException("Native replay has no external-runtime override");
                int code=execute(source.getParent(),request,RsemJson.bytes(request),Map.of("analysis",parent.toString(),"seal_sha256",parentSeal),out,err);
                verify(parent);if(!FollowupSupport.hash(parent.resolve("capture-files.json")).equals(parentSeal))throw new IOException("Replay changed its parent capture");return code;
            }
            return execute(source.getParent(),raw,original,Map.of(),out,err);
        }catch(QtltoolsWorkflowRuntime.Unavailable missing){err.println("SKIP3 - DEPENDENT ANALYSIS SKIPPED: "+missing.getMessage()+"\n"+ACTION);return 3;}
        catch(OutOfMemoryError exhausted){err.println("FAIL2 - NO COMPLETE RESULT: Java heap exhausted;preserve evidence and retry NEW output with explicit heap/capacities.\n"+ACTION);return 2;}
        catch(Exception error){err.println("FAIL2 - NO COMPLETE RESULT: "+error+"\n"+ACTION);return 2;}
    }
    private static int execute(Path parent,Map<String,Object> raw,byte[] original,Map<String,Object> replay,PrintStream out,PrintStream err)throws Exception {
        var validated=QtltoolsWorkflowContracts.validate(raw);Path root=QtltoolsWorkflowFiles.newOutput(resolve(parent,text(validated,"out")));var manifest=new LinkedHashMap<String,Object>();
        manifest.put("schema_version",1);manifest.put("engine",ENGINE);manifest.put("scope",SCOPE);manifest.put("source_request_sha256",NextflowFiles.sha(original));manifest.put("edition",validated.get("edition"));manifest.put("operation",validated.get("operation"));manifest.put("selected_engine",validated.get("engine"));manifest.put("java_version",System.getProperty("java.version"));
        int code=2;String error=null;boolean interrupted=false;var pins=new TreeMap<String,Object>();
        json(root.resolve("analysis-status.json"),state("RUNNING_NO_RESULT_ADMITTED",false,null));
        try {
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);if(!replay.isEmpty())json(root.resolve("replay-source.json"),replay);
            for(String folder:List.of("inputs","outputs","source","worker"))Files.createDirectory(root.resolve(folder));
            QtltoolsWorkflowRuntime.sources(root,false);
            var captured=QtltoolsWorkflowContracts.paths(raw,parent,true);json(root.resolve("request.json"),captured);var effective=QtltoolsWorkflowContracts.validate(captured);json(root.resolve("effective-request.json"),effective);
            long remaining=QtltoolsWorkflowContracts.limit(validated,"maximum_input_bytes");var unavailable=new ArrayList<String>();
            for(var input:QtltoolsWorkflowContracts.inputs(raw)) {
                try{remaining-=QtltoolsWorkflowFiles.capture(resolve(parent,text(input.descriptor(),"path")),root.resolve(input.captured()),remaining);pins.put(input.captured(),FollowupSupport.hash(root.resolve(input.captured())));}catch(QtltoolsWorkflowRuntime.Unavailable missing){unavailable.add(missing.getMessage());}
                if(input.capturedIndex()!=null)try{remaining-=QtltoolsWorkflowFiles.capture(resolve(parent,text(input.descriptor(),"index")),root.resolve(input.capturedIndex()),remaining);pins.put(input.capturedIndex(),FollowupSupport.hash(root.resolve(input.capturedIndex())));}catch(QtltoolsWorkflowRuntime.Unavailable missing){unavailable.add(missing.getMessage());}
            }
            if(!unavailable.isEmpty())throw new QtltoolsWorkflowRuntime.Unavailable(String.join("; ",unavailable));
            var prepared=QtltoolsWorkflowFiles.prepare(root,effective);json(root.resolve("prepared-inputs.json"),prepared);
            if(text(validated,"engine").equals("native"))manifest.put("native_metadata",QtltoolsWorkflowFiles.nativeRun(root,effective));
            else {var runtimeRequest=new LinkedHashMap<>(effective);runtimeRequest.put("runtime",QtltoolsWorkflowRuntime.absoluteRuntime(parent,raw));QtltoolsWorkflowRuntime.run(root,runtimeRequest,manifest);}
            for(var entry:pins.entrySet())if(!FollowupSupport.hash(root.resolve(entry.getKey())).equals(entry.getValue()))throw new IOException("Captured original input/index changed during execution");
            QtltoolsWorkflowAudit.provenance(root,effective,manifest);var audit=QtltoolsWorkflowAudit.outputs(root,effective);json(root.resolve("output-audit.json"),audit);
            if(text(effective,"engine").equals("native")&&!same(audit.get("fdensity_metadata"),manifest.get("native_metadata")))throw new IOException("Native metadata differs from complete recomputation");code=0;
        }catch(QtltoolsWorkflowRuntime.Unavailable missing){code=3;error=missing.toString();}
        catch(OutOfMemoryError exhausted){error="Java heap exhausted;retain all raw evidence and retry NEW output with explicit capacities/heap";}
        catch(Exception failed){error=failed.toString();if(failed instanceof InterruptedException||failed instanceof InterruptedIOException||Thread.currentThread().isInterrupted()){interrupted=true;Thread.interrupted();}}
        manifest.put("captured_input_sha256",pins);String kind=code==0?text(read(root.resolve("output-audit.json")),"result_kind"):"no-admitted-result";String status=code==0?(kind.startsWith("null-")?"completed-null-with-warnings":"completed-with-warnings"):code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
        manifest.put("analysis_status",status);manifest.put("result_kind",kind);manifest.put("results_admitted",code==0);if(error!=null)manifest.put("error",error);json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state(status,code==0,error,kind));
        Files.writeString(root.resolve("WARNINGS.md"),warnings(root,status,error),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try{QtltoolsWorkflowFiles.seal(root);if(code==0)verify(root);}catch(Exception failed){code=2;error=failed.toString();manifest.put("analysis_status","FAIL2_NO_COMPLETE_RESULT");manifest.put("result_kind","no-admitted-result");manifest.put("results_admitted",false);manifest.put("verification_error",error);json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state("FAIL2_NO_COMPLETE_RESULT",false,error,"no-admitted-result"));Files.writeString(root.resolve("WARNINGS.md"),warnings(root,"FAIL2_NO_COMPLETE_RESULT",error),StandardCharsets.UTF_8);QtltoolsWorkflowFiles.seal(root);}
        if(interrupted)Thread.currentThread().interrupt();(code==0?out:err).println((code==0?"Completed prepared QTLtools operation ("+kind+"): ":code==3?"SKIP3 - DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 - NO COMPLETE RESULT: ")+root);if(error!=null)err.println("CAUSE / REQUIRED RECOVERY: "+error);err.println("WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return code;
    }
    static void verify(Path root)throws Exception {
        root=root.toAbsolutePath().normalize();var seal=read(root.resolve("capture-files.json"));keys(seal,Set.of("schema_version","authentication","files"));if(integer(seal,"schema_version",0,1,1)!=1||!Boolean.FALSE.equals(seal.get("authentication")))throw new IOException("Invalid seal or authentication claim");
        var expected=object(seal.get("files"),"files");var actual=PeerWorkflowRuntime.footprint(root);actual.remove("capture-files.json");if(!expected.keySet().equals(actual.keySet()))throw new IOException("Capture population differs");for(var entry:expected.entrySet())if(!FollowupSupport.hash(QtltoolsWorkflowFiles.safe(root,entry.getKey())).equals(entry.getValue()))throw new IOException("Changed captured file: "+entry.getKey());
        var manifest=read(root.resolve("manifest.json"));var status=read(root.resolve("analysis-status.json"));if(!ENGINE.equals(manifest.get("engine"))||!SCOPE.equals(manifest.get("scope"))||!Set.of("completed-with-warnings","completed-null-with-warnings").contains(manifest.get("analysis_status"))||!Objects.equals(manifest.get("analysis_status"),status.get("status"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!Boolean.TRUE.equals(status.get("results_admitted"))||!ACTION.equals(status.get("required_action")))throw new IOException("Failed,skipped or incomplete captures are not admitted results");
        var original=read(root.resolve("source-request.json"));QtltoolsWorkflowContracts.validate(original);if(!FollowupSupport.hash(root.resolve("source-request.json")).equals(manifest.get("source_request_sha256")))throw new IOException("Original request identity differs");
        var captured=QtltoolsWorkflowContracts.paths(original,root,true);if(!same(captured,read(root.resolve("request.json"))))throw new IOException("Captured input bindings differ from original request");var effective=QtltoolsWorkflowContracts.validate(captured);if(!same(effective,read(root.resolve("effective-request.json"))))throw new IOException("Effective request differs");
        if(!Objects.equals(effective.get("edition"),manifest.get("edition"))||!Objects.equals(effective.get("operation"),manifest.get("operation"))||!Objects.equals(effective.get("engine"),manifest.get("selected_engine")))throw new IOException("Edition,operation or engine differs");
        var pins=new TreeMap<String,Object>();for(var input:QtltoolsWorkflowContracts.inputs(effective)){pins.put(input.captured(),FollowupSupport.hash(root.resolve(input.captured())));if(input.capturedIndex()!=null)pins.put(input.capturedIndex(),FollowupSupport.hash(root.resolve(input.capturedIndex())));}
        if(!same(pins,manifest.get("captured_input_sha256")))throw new IOException("Input/index inventory differs");var names=new TreeSet<String>();for(String name:PeerWorkflowRuntime.footprint(root.resolve("inputs")).keySet())names.add("inputs/"+name);if(!names.equals(pins.keySet()))throw new IOException("Undeclared captured input/index");
        long bytes=0;for(String name:names){long size=Files.size(root.resolve(name));if(size>QtltoolsWorkflowContracts.limit(effective,"maximum_input_bytes")-bytes)throw new IOException("Captured inputs exceed declared capacity");bytes+=size;}
        QtltoolsWorkflowRuntime.sources(root,true);if(!same(QtltoolsWorkflowFiles.prepare(root,effective),read(root.resolve("prepared-inputs.json"))))throw new IOException("Prepared input/header metadata differs");
        QtltoolsWorkflowAudit.provenance(root,effective,manifest);var audit=QtltoolsWorkflowAudit.outputs(root,effective);if(!same(audit,read(root.resolve("output-audit.json"))))throw new IOException("Complete output audit differs");
        String kind=text(audit,"result_kind");if(!kind.equals(manifest.get("result_kind"))||!kind.equals(status.get("result_kind"))||!(kind.startsWith("null-")?"completed-null-with-warnings":"completed-with-warnings").equals(status.get("status")))throw new IOException("Null/model result-kind classification differs");
        if(text(effective,"engine").equals("native")&&!same(audit.get("fdensity_metadata"),manifest.get("native_metadata")))throw new IOException("Native metadata differs");
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings(root,text(manifest,"analysis_status"),null)))throw new IOException("Warnings/recovery instructions differ");
    }
    private static Map<String,Object> state(String status,boolean admitted,String error){return state(status,admitted,error,"pending");}
    private static Map<String,Object> state(String status,boolean admitted,String error,String kind){var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("status",status);value.put("result_kind",kind);value.put("results_admitted",admitted);value.put("required_action",ACTION);if(error!=null)value.put("error",error);return value;}
    private static String warnings(Path root,String status,String error)throws IOException {
        String details="",orderWarning="",nullWarning="";Path audit=root.resolve("output-audit.json");if(Files.isRegularFile(audit,LinkOption.NOFOLLOW_LINKS)){details="Complete export/null/undefined-metric counts and raw hashes:\n"+Files.readString(audit,StandardCharsets.UTF_8)+"\n\n";var saved=read(audit);String kind=text(saved,"result_kind");if(kind.startsWith("null-"))nullWarning="**NULL RESULT - "+kind+": no corresponding association,defined genotype-match concordance or quantified count is admitted as a positive finding.** Retain the original null exports. Review the explicit inputs,coverage and prespecified selection criteria;any change is a NEW declared analysis. Do not invent a p-value,donor match score or read count.\n\n";if(saved.containsKey("fdensity_metadata")){long skipped=((Number)object(saved.get("fdensity_metadata"),"fdensity metadata").get("source_order_guarded_qtl_rows")).longValue();if(skipped>0)orderWarning="**FDENSITY SOURCE-ORDER WARNING: "+skipped+" QTL rows had their chromosome search skipped by the original small-tree guard. Report this explicitly;do not silently sort input.**\n\n";}}
        return "# QTLtools warnings and required actions\n\nStatus: "+status+"\n\n"+(error==null?"":"**CAUSE / REQUIRED RECOVERY:** "+error+"\n\n")+nullWarning+ACTION+"\n\n"+SCOPE+"\n\n"
            +"**SCIENTIFIC INPUTS:** Supply exact genome assembly,coordinates,strand,prepared cohort,phenotypes,covariates,indexes,thresholds and edition. No cohort preparation,QTL lead selection,FDR/qvalue generation,allele harmonization or adaptive permutation scheme is inferred. Cis --permute is a fixed integer count. TRANS_ADJUST reads the LAST null-file column in1.1/1.2 and column THREE in1.3.1;the1.2 best export ends with a variant ID and is not directly a compatible null file. Supply a deliberately prepared numeric null table;do not silently remove columns.\n\n"
            +"**RETAINED SOURCE SEMANTICS:** Unadjusted trans exports retain the -1 adjusted-p sentinel. Zero-variant cis permutation rows retain their complete NA tail. MBV percentages with no covered variants and quan diagnostic ratios with zero denominators remain raw undefined NaN values with explicit counts in the audit;they are not invented zero scores. Nonfinite fitted/test/quantification values are refused. Original dosage-missing handling and outputs can differ across editions. Quan sample/header/hash and chromosome export behavior are retained. Source quantification hash names include captured relative paths and declared options.\n\n"
            +"**LEGACY MBV1.1 WARNING:** The exact1.1 source divides integer concordance counters with integer division. Its exported fractions are truncated to0 or1;they are not continuous match rates. The adapter preserves them and the complete counters. Do not infer zero consistent sites from an exported0 or silently replace the original field.1.2/1.3.1 use floating-point division.\n\n"
            +orderWarning+"**FDENSITY ORDER WARNING:** For fewer than64 annotations on a chromosome,the original interval-tree guard can skip matches based on the first input row. Read source_order_guarded_qtl_rows in the1.3.1 audit and report it;never silently sort. Counts are per QTL with any overlap in a bin,not annotation multiplicity or enrichment significance. Native fdensity ignores seed because it is deterministic.\n\n"
            +"**VERIFICATION LIMITS:** No original model is refit by VERIFY. Hashes are integrity checks,not signatures. Original full raw exports are retained;review null/undefined rows and upstream warnings before interpreting a result. Decoder limits apply per text input/export;maximum_input_bytes also bounds total captured inputs,indexes included. Live original output growth is monitored against maximum_output_bytes. Core dumps are disabled for the newly owned child to bound crash output;all previous evidence,raw inputs,partial exports,logs and signal/exit records remain retained.\n\n"+details;
    }
}
