/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.jlinalg.cli.GenomicRangesWorkflowContracts.require;

/** Original GenomicRanges workflows on prepared objects, with complete immutable captures. */
public final class GenomicRangesToolsCli {
    private GenomicRangesToolsCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||args[0].equals("--help")||args[0].equals("-h")){help(out);return 0;}
            String action=args[0];require(Set.of("RUN","VERIFY","REPLAY").contains(action),"Use RUN,VERIFY or REPLAY");var options=new LinkedHashMap<String,String>();
            for(int i=1;i<args.length;i+=2){require(i+1<args.length&&args[i].startsWith("--")&&options.putIfAbsent(args[i],args[i+1])==null,"Invalid/duplicate CLI option");}
            require(options.keySet().equals(action.equals("RUN")?Set.of("--request","--out"):action.equals("VERIFY")?Set.of("--run"):Set.of("--run","--out")),"Unknown/missing CLI options");
            if(action.equals("VERIFY")){Path root=Path.of(options.get("--run")).toAbsolutePath().normalize();var manifest=GenomicRangesWorkflowAudit.verify(root);out.write(RsemJson.bytes(Map.of("status","CAPTURE_INTEGRITY_AND_CORE_RELATIONSHIPS_VERIFIED","results_admitted",manifest.get("results_admitted"),"scope","No offline RDS parse,original refit,source authentication or host attestation")));return Boolean.TRUE.equals(manifest.get("results_admitted"))?0:((Number)manifest.get("exit_code")).intValue();}
            Path parent=null;Map<String,Object> request;Map<String,Object> parentSeal=null;
            if(action.equals("RUN")){Path input=Path.of(options.get("--request")).toAbsolutePath().normalize();request=GenomicRangesWorkflowContracts.validate(GenomicRangesWorkflowFiles.read(input,16777216),input.getParent());}
            else{parent=Path.of(options.get("--run")).toAbsolutePath().normalize();var manifest=GenomicRangesWorkflowAudit.verify(parent);require(Boolean.TRUE.equals(manifest.get("results_admitted")),"REPLAY requires a complete admitted parent capture");parentSeal=GenomicRangesWorkflowFiles.pin(parent.resolve("seal.json"));request=GenomicRangesWorkflowFiles.read(parent.resolve("request.json"),16777216);request.put("input_rds",parent.resolve("inputs/objects.rds").toString());request=GenomicRangesWorkflowContracts.validate(request,parent);}
            return execute(action,request,Path.of(options.get("--out")).toAbsolutePath().normalize(),parent,parentSeal,out,err);
        }catch(GenomicRangesWorkflowContracts.Unavailable e){err.println("SKIP3 — dependent GenomicRanges analysis skipped: "+e.getMessage()+". Supply the exact permitted dependency/input and retry NEW output.");return 3;}
        catch(Exception e){err.println("FAIL2 — no complete GenomicRanges result: "+e.getMessage()+". Preserve this attempt,fix the stated cause and retry NEW output.");return 2;}
    }
    private static int execute(String action,Map<String,Object> request,Path root,Path parent,Map<String,Object> parentSeal,PrintStream out,PrintStream err)throws Exception {
        require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Output must be NEW;existing captures are never overwritten");require(root.getParent()!=null&&Files.isDirectory(root.getParent())&&root.getParent().toRealPath().equals(root.getParent()),"Output parent must exist at its physical path");
        if(parent!=null)require(!root.startsWith(parent)&&!parent.startsWith(root)&&!root.equals(parent.toRealPath()),"REPLAY child must be physically separate from its parent");
        Files.createDirectory(root);for(String name:List.of("source","inputs","worker"))Files.createDirectory(root.resolve(name));var manifest=new LinkedHashMap<String,Object>();manifest.put("schema_version",1);manifest.put("workflow","genomicranges-prepared-v1");manifest.put("capture_id",UUID.randomUUID().toString());manifest.put("created_at",Instant.now().toString());manifest.put("action",action);manifest.put("operation",request.get("operation"));manifest.put("profile",request.get("profile"));manifest.put("results_admitted",false);manifest.put("complete",false);int code=2;Exception failure=null;
        if(parent!=null){manifest.put("replay_parent",parent.toString());manifest.put("replay_parent_seal",parentSeal);}
        try {
            Files.write(root.resolve("request.json"),RsemJson.bytes(request),StandardOpenOption.CREATE_NEW);GenomicRangesWorkflowRuntime.captureSources(root,request.get("profile").toString(),false);GenomicRangesWorkflowRuntime.controls(root,request,false);
            GenomicRangesWorkflowFiles.capture(Path.of(request.get("input_rds").toString()),root.resolve("inputs/objects.rds"),GenomicRangesWorkflowFiles.cap(request,"maximum_input_bytes"));
            int worker=GenomicRangesWorkflowRuntime.execute(root,request,manifest);var state=GenomicRangesWorkflowAudit.outcome(root);manifest.put("outcome",state);
            if(worker==3&&state.get("status").equals("SKIP3_DEPENDENT_ANALYSIS_SKIPPED"))throw new GenomicRangesWorkflowContracts.Unavailable(state.get("detail").toString());
            require(worker==0&&state.get("status").equals("COMPLETED_WITH_WARNINGS"),"Original worker did not complete: "+state.get("detail"));
            state=GenomicRangesWorkflowAudit.result(root,request,root.resolve("worker/raw-result"));manifest.put("outcome",state);
            if(parent!=null){GenomicRangesWorkflowAudit.verify(parent);GenomicRangesWorkflowRuntime.matches(parentSeal,GenomicRangesWorkflowFiles.pin(parent.resolve("seal.json")));}
            Path admitted=root.resolve("admitted");Files.createDirectory(admitted);try(var files=Files.list(root.resolve("worker/raw-result"))){for(Path file:files.toList())Files.copy(file,admitted.resolve(file.getFileName()));}
            GenomicRangesWorkflowAudit.result(root,request,admitted);manifest.put("results_admitted",true);manifest.put("complete",true);manifest.put("status","COMPLETED_WITH_WARNINGS");code=0;
        }catch(Exception e){failure=e;code=e instanceof GenomicRangesWorkflowContracts.Unavailable?3:2;manifest.put("status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT");manifest.put("detail",e.getMessage()==null?e.toString():e.getMessage());quarantine(root);manifest.put("results_admitted",false);manifest.put("complete",false);}
        manifest.put("exit_code",code);manifest.put("finished_at",Instant.now().toString());
        String warnings="GenomicRanges prepared-object workflow\n\n"+(code==0?"COMPLETED WITH WARNINGS — full original result.rds is admitted;TSV is a core-field view.\n":code==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED;NO ADMITTED RESULT.\n":"FAIL2 — NO COMPLETE ADMITTED RESULT.\n")
            +"Read worker/conditions.tsv,conditions.rds,stdout.log and stderr.log. Preserve raw/partial/failed/null/superseded evidence.\n"
            +"Supply exact permitted Rscript/packages/inputs for SKIP3. If install is prohibited,skip the dependent analysis and supply the named prerequisite later.\n"
            +"For FAIL2,fix the stated input,capacity,process,numerical or integrity cause;retry into NEW output. No identifiers,endpoints,NaN/Inf or scientific choices were repaired.\n"
            +"Profiles identify exact original GenomicRanges editions with explicit independent R/dependency versions;they do not reproduce a full paper environment.\n"
            +"Promoter distances are transported as R numeric values;original recycleIntegerArg controls integer conversion. Requested values are retained in request.json/options.tsv.\n"
            +"Runtime files are fingerprinted before ACK/after exit,with periodic captured-input metadata checks. This is not continuous monitoring,binary/host attestation or an OS memory/job quota.\n"
            +"Runtime fingerprint scope: "+GenomicRangesWorkflowRuntime.FILE_SCOPE+"\n"
            +"Offline VERIFY checks saved integrity and core relationships;it does not parse RDS,refit or authenticate a resealed capture.\n"
            +"Profile warnings: "+GenomicRangesWorkflowRuntime.profile(request.get("profile").toString()).get("warnings")+"\n"
            +(failure==null?"":"Cause: "+failure.getMessage()+"\n");
        try{Files.writeString(root.resolve("WARNINGS.md"),warnings,StandardOpenOption.CREATE_NEW);Files.write(root.resolve("manifest.json"),RsemJson.bytes(manifest),StandardOpenOption.CREATE_NEW);var files=GenomicRangesWorkflowAudit.inventory(root,request);Files.write(root.resolve("seal.json"),RsemJson.bytes(Map.of("schema_version",1,"files",files)),StandardOpenOption.CREATE_NEW);GenomicRangesWorkflowProcess.footprint(root,request);}
        catch(Exception e){return invalidate(root,request,manifest,e,err);}
        if(code==0){try{GenomicRangesWorkflowAudit.verify(root);if(parent!=null){GenomicRangesWorkflowAudit.verify(parent);GenomicRangesWorkflowRuntime.matches(parentSeal,GenomicRangesWorkflowFiles.pin(parent.resolve("seal.json")));}}catch(Exception e){return invalidate(root,request,manifest,e,err);}out.println("COMPLETED WITH WARNINGS — admitted original result: "+root.resolve("admitted/result.rds"));}
        else err.println((code==3?"SKIP3 — dependent analysis skipped":"FAIL2 — no complete admitted result")+": "+manifest.get("detail")+". Read "+root.resolve("WARNINGS.md"));return code;
    }
    private static void quarantine(Path root)throws IOException {
        Path from=root.resolve("admitted");if(!Files.exists(from,LinkOption.NOFOLLOW_LINKS))return;Path real=root.toRealPath();require(from.toAbsolutePath().normalize().startsWith(real)&&from.getParent().toRealPath().equals(real),"Unsafe admission quarantine path");Files.move(from,root.resolve("failed-exports-"+UUID.randomUUID()));
    }
    private static int invalidate(Path root,Map<String,Object> request,Map<String,Object> manifest,Exception cause,PrintStream err)throws IOException {
        quarantine(root);manifest.put("results_admitted",false);manifest.put("complete",false);manifest.put("exit_code",2);manifest.put("status","FAIL2_NO_COMPLETE_RESULT");manifest.put("detail","Capture finalization/verification failed: "+cause.getMessage());
        Path history=root.resolve("worker/finalization-failure-"+UUID.randomUUID());Files.createDirectory(history);Path real=root.toRealPath();require(history.toRealPath().startsWith(real),"Unsafe finalization history path");
        for(String name:List.of("manifest.json","seal.json","WARNINGS.md")){Path p=root.resolve(name);if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))Files.move(p,history.resolve("invalidated-"+name));}
        Files.writeString(root.resolve("WARNINGS.md"),"FAIL2 — NO COMPLETE ADMITTED RESULT. Finalization failed: "+cause.getMessage()+"\nPreserve raw/invalidated evidence and fix the stated capacity,process or integrity cause;retry NEW output.\n",StandardOpenOption.CREATE_NEW);Files.write(root.resolve("manifest.json"),RsemJson.bytes(manifest),StandardOpenOption.CREATE_NEW);
        try{Files.write(root.resolve("seal.json"),RsemJson.bytes(Map.of("schema_version",1,"files",GenomicRangesWorkflowAudit.inventory(root,request))),StandardOpenOption.CREATE_NEW);}catch(IOException error){err.println("The failed attempt also lacks a complete seal: "+error.getMessage());}
        err.println("FAIL2 — capture finalization failed;no admitted result. Preserve partial output at "+root+";cause: "+cause.getMessage());return 2;
    }
    private static void help(PrintStream out) {
        out.println("genomicranges-tools RUN --request request.json --out NEW_DIRECTORY\n"
            +"genomicranges-tools VERIFY --run CAPTURE\n"
            +"genomicranges-tools REPLAY --run CAPTURE --out NEW_DIRECTORY\n\n"
            +"Original1.44.0/1.58.0/1.60.0 prepared-GRanges operations:find-overlaps,subset-overlaps,nearest,distance-to-nearest,promoters,out-of-bounds,reduce,disjoin.\n"
            +"Explicit profile,Rscript,private libraries,subject mode,operation options and capacity limits are required.\n"
            +"Full result.rds preserves original metadata/seqinfo;TSV exports core fields. Read WARNINGS.md.\n"
            +"Exit0 completed with warnings;exit3 dependency/input skipped with remedy;exit2 no complete admitted result.\n"
            +"VERIFY is saved integrity/core relationships,not an RDS parse,original refit or authentication. No full-paper environment/reproduction claim.");
    }
}
