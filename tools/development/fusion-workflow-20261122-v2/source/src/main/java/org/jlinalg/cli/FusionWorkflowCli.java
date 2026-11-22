/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.FusionWorkflowFiles.*;

/** Original FUSION training/association/post-processing with captured input replay. */
public final class FusionWorkflowCli {
    private FusionWorkflowCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){Path p=Path.of(args[1]).toAbsolutePath().normalize();try{FusionWorkflowAudit.verify(p);}catch(IOException error){throw new IOException("Saved FUSION capture is invalid: "+error.getMessage(),error);}emit(out,"Verified saved original FUSION inventory/relationships offline: "+p);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --verify CAPTURE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(source,67108864);var original=object(GprofilerJson.read(raw));Path parent=null;Map<String,Object> parentSeal=null,parentRequest=null;Map<String,Object> request;
            if("REPLAY".equals(original.get("mode"))){
                keys(original,Set.of("schema_version","mode","analysis","out"));integer(original.get("schema_version"),1,1);
                parent=FusionWorkflowContracts.path(source.getParent(),text(original.get("analysis"),"analysis"));FusionWorkflowAudit.verify(parent);parent=parent.toRealPath();parentSeal=pin(parent.resolve("manifest.json"));
                request=read(parent.resolve("canonical-request.json"));parentRequest=new LinkedHashMap<>(request);request.put("out",FusionWorkflowContracts.path(source.getParent(),text(original.get("out"),"out")).toString());
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target)&&target.getParent().toRealPath().equals(target.getParent()),"Replay output must be physically separate from parent");
            }else request=FusionWorkflowContracts.validate(original,source.getParent());
            root=newOutput(Path.of(request.get("out").toString()));for(String name:List.of("inputs","worker","worker/out","worker/tmp"))Files.createDirectories(root.resolve(name));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            var binding=map("original",original,"canonical",request,"source_base",source.getParent().toString());
            if(parent!=null){binding.put("replay_source_canonical",parentRequest);binding.put("replay_analysis_address",parent.toString());}
            write(root.resolve("canonical-request.json"),request);write(root.resolve("request-binding.json"),binding);write(root.resolve("producer.json"),FusionWorkflowAudit.producer());
            var manifest=map("schema_version",1,"workflow","fusion-original-workflow-v1","mode",parent==null?"RUN":"REPLAY","results_admitted",false);
            if(parent!=null)manifest.put("replay_parent_manifest",parentSeal);
            FusionWorkflowRuntime.sources(root);FusionWorkflowRuntime.execute(root,request,manifest,parent);var summary=FusionWorkflowAudit.summary(root,request);
            if(parent!=null){
                FusionWorkflowAudit.verify(parent);require(same(pin(parent.resolve("manifest.json")),parentSeal),"Replay parent changed");
                var prior=read(parent.resolve("summary.json"));var before=new TreeMap<String,Object>();var after=new TreeMap<String,Object>();
                for(Object value:list(prior.get("original_export_files")))before.put(value.toString(),pin(parent.resolve("worker").resolve(value.toString())));
                for(Object value:list(summary.get("original_export_files")))after.put(value.toString(),pin(root.resolve("worker").resolve(value.toString())));
                boolean equal=same(before,after);write(root.resolve("replay-comparison.json"),map("scientific_exports_byte_identical",equal,"parent",before,"replay",after,"independent_refit",false));
                require(equal||Boolean.TRUE.equals(object(request.get("admission")).get("allow_replay_differences")),"Original re-execution differs from saved scientific exports; retain this attempt and inspect replay-comparison.json. Explicit allow_replay_differences is required to admit differing source fits");
            }
            Files.createDirectory(root.resolve("admitted"));
            for(Object value:list(summary.get("original_export_files"))){String name=relative(value.toString());Path target=root.resolve("admitted").resolve(name);Files.createDirectories(target.getParent());Files.copy(root.resolve("worker").resolve(name),target);}
            Files.copy(root.resolve("worker/audit.rds"),root.resolve("admitted/audit.rds"));write(root.resolve("summary.json"),summary);
            text(root.resolve("WARNINGS.md"),"# WARNINGS AND REQUIRED ACTIONS\n\n"+FusionWorkflowContracts.SCOPE+"\n\n"+FusionWorkflowContracts.ACTION+"\n\n"+resultWarnings(summary)+compatibilityWarnings(request)+"\nOriginal output formatting, missing values and source conditions are retained. Read worker/original.stdout.log, original.stderr.log, audit.rds and summary.json. Source weight/FILE path staging preserves original derived panel labels; original input bytes are captured. Training uses working copies because --rn can rewrite a phenotype file. The declared seed controls R; external tools retain their own source RNG settings/defaults. Runtime fingerprints are limited to the declared/loaded file closure, not host attestation or an OS memory quota.\n"+originalMessages(root));
            manifest.put("results_admitted",true);manifest.put("inventory",inventory(root,request));write(root.resolve("manifest.json"),manifest);write(root.resolve("COMPLETED.json"),map("schema_version",1,"manifest_sha256",pin(root.resolve("manifest.json")).get("sha256"),"authentication",false));FusionWorkflowAudit.verify(root);
            String notices=resultWarnings(summary)+compatibilityWarnings(request);if(!notices.isBlank())emit(err,notices+"Read "+root.resolve("WARNINGS.md"));
            emit(out,"Completed original FUSION "+request.get("operation")+": finite_result_rows="+summary.get("finite_result_rows")+", no_result_rows="+summary.get("no_result_rows")+", missing_requested_coloc_rows="+summary.get("missing_requested_coloc_rows")+"; "+root+"; read WARNINGS.md and original conditions");return 0;
        }catch(FusionWorkflowContracts.Unavailable error){return failed(root,error,3,err);}
        catch(Exception error){return failed(root,error,2,err);}
    }
    private static int failed(Path root,Exception error,int code,PrintStream err){
        String status=code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED":"FAIL2: NO COMPLETE RESULT";
        if(root!=null)try{
            for(String name:List.of("COMPLETED.json","manifest.json","WARNINGS.md"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("unadmitted-"+name));
            if(Files.exists(root.resolve("admitted"),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve("admitted"),root.resolve("unadmitted-exports"));
            write(root.resolve("FAILURE.json"),map("status",status,"exit_code",code,"results_admitted",false,"cause",error.toString()));text(root.resolve("WARNINGS.md"),"# "+status+"\n\n"+error+"\n\n"+FusionWorkflowContracts.ACTION+"\n"+originalMessages(root));
        }catch(Exception diagnostic){emit(err,"Additional failure-journal problem: "+diagnostic);}
        emit(err,status+": "+error+"\n"+FusionWorkflowContracts.ACTION+(root==null?"":"\nRead "+root.resolve("WARNINGS.md")));return code;
    }
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+System.lineSeparator()).getBytes(StandardCharsets.UTF_8));stream.flush();}
    private static String originalMessages(Path root)throws IOException {
        var result=new StringBuilder("\n## Original runtime messages\n\nExcerpts below are limited to the first 8192 bytes per log. Inspect the full logs for additional conditions. Messages are preserved, including newer-R build warnings and upstream plotting warnings; no source settings are repaired. For a runtime/plotting incompatibility, provide the intended compatible package edition and retry NEW output.\n");
        for(String name:List.of("worker/stderr.log","worker/original.stderr.log","worker/original.stdout.log")){
            Path file=root.resolve(name);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;byte[] excerpt;
            try(var stream=Files.newInputStream(file)){excerpt=stream.readNBytes(8192);}if(excerpt.length==0)continue;
            result.append("\n### ").append(name).append(" (excerpt)\n\n");for(String line:new String(excerpt,StandardCharsets.UTF_8).split("\\R",-1))result.append("    ").append(line).append('\n');
        }return result.toString();
    }
    static String compatibilityWarnings(Map<String,Object> request)throws IOException {
        var flags=object(request.get("runtime_compatibility"));var result=new StringBuilder();
        if(Boolean.TRUE.equals(flags.get("gcta_no_analysis_probe_exit1")))result.append("DECLARED GCTA STARTUP COMPATIBILITY: the exact no-analysis exit1 diagnostic is accepted only for the startup probe; original stdout/stderr/status are retained. Actual analysis arguments and statuses are unchanged.\n");
        if(Boolean.TRUE.equals(flags.get("gemma_working_directory_output")))result.append("DECLARED GEMMA OUTPUT ROUTING: -outdir . is explicitly added to original invocations; original prefixes, scientific settings and exit statuses are preserved. Read gemma-invocations.nul and original stderr.\n");
        if(Boolean.TRUE.equals(object(request.get("admission")).get("allow_replay_differences")))result.append("REPLAY DIFFERENCES ALLOWED: differing original scientific exports can be admitted by explicit request. Inspect replay-comparison.json; re-execution is not independent refitting or equivalence proof.\n");
        return result.toString();
    }
    static String resultWarnings(Map<String,Object> summary)throws IOException {
        long absent=number(summary,"no_result_rows"),coloc=number(summary,"missing_requested_coloc_rows"),zero=number(summary,"original_p_zero");var result=new StringBuilder();
        if(absent>0||coloc>0)result.append("ORIGINAL NO-RESULT COUNTS: no_result_rows=").append(absent).append(", missing_requested_coloc_rows=").append(coloc).append(". Explicit policy retains these source absences; they are not finite association/colocalization estimates. Restore complete/matching inputs for a new analysis or retain and report the no-results.\n");
        if(zero>0)result.append("ORIGINAL ZERO P-VALUES: ").append(zero).append(" retained exactly; no positive floor was substituted.\n");
        Object missing=summary.get("permutation_analytical_missing_rows");if(missing!=null&&integer(missing,0,Long.MAX_VALUE)>0)result.append("ORIGINAL PERMUTATION CONDITIONS: analytical permutation p-values are absent in ").append(missing).append(" rows under the source's adaptive/undefined conditions; original empirical p-values and counts are retained.\n");
        return result.toString();
    }
    static String help(){return "fusion-tools --request UTF8_JSON | --verify CAPTURE | --help\nRUN declares exact source profile, TRAIN|ASSOCIATE|POSTPROCESS, Rscript/private libraries and expected versions, original inputs/options, explicit seed, admission and limits. REPLAY={schema_version:1,mode:REPLAY,analysis:PARENT,out:NEW}. REPLAY re-executes the original runtime; VERIFY reads saved integrity/relationships without R.\n"+FusionWorkflowContracts.SCOPE+"\n"+FusionWorkflowContracts.ACTION;}
}
