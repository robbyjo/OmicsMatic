/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
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
            if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){Path p=Path.of(args[1]).toAbsolutePath().normalize();FusionWorkflowAudit.verify(p);out.println("Verified saved original FUSION inventory/relationships offline: "+p);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --verify CAPTURE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(source,67108864);var original=object(GprofilerJson.read(raw));Path parent=null;Map<String,Object> parentSeal=null;Map<String,Object> request;
            if("REPLAY".equals(original.get("mode"))){
                keys(original,Set.of("schema_version","mode","analysis","out"));integer(original.get("schema_version"),1,1);
                parent=FusionWorkflowContracts.path(source.getParent(),text(original.get("analysis"),"analysis"));FusionWorkflowAudit.verify(parent);parent=parent.toRealPath();parentSeal=pin(parent.resolve("manifest.json"));
                request=read(parent.resolve("canonical-request.json"));request.put("out",FusionWorkflowContracts.path(source.getParent(),text(original.get("out"),"out")).toString());
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target)&&target.getParent().toRealPath().equals(target.getParent()),"Replay output must be physically separate from parent");
            }else request=FusionWorkflowContracts.validate(original,source.getParent());
            root=newOutput(Path.of(request.get("out").toString()));for(String name:List.of("inputs","worker","worker/out","worker/tmp"))Files.createDirectories(root.resolve(name));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            write(root.resolve("canonical-request.json"),request);write(root.resolve("request-binding.json"),map("original",original,"canonical",request));write(root.resolve("producer.json"),FusionWorkflowAudit.producer());
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
            text(root.resolve("WARNINGS.md"),"# WARNINGS AND REQUIRED ACTIONS\n\n"+FusionWorkflowContracts.SCOPE+"\n\n"+FusionWorkflowContracts.ACTION+"\n\nOriginal output formatting, missing values and source conditions are retained. Read worker/original.stdout.log, original.stderr.log, audit.rds and summary.json. Source weight/FILE path staging is explicit; original input bytes are captured. Training uses working copies because --rn can rewrite a phenotype file. Runtime fingerprints are limited to the declared/loaded file closure, not host attestation or an OS memory quota.\n");
            manifest.put("results_admitted",true);manifest.put("inventory",inventory(root,request));write(root.resolve("manifest.json"),manifest);write(root.resolve("COMPLETED.json"),map("schema_version",1,"manifest_sha256",pin(root.resolve("manifest.json")).get("sha256"),"authentication",false));FusionWorkflowAudit.verify(root);
            out.println("Completed original FUSION "+request.get("operation")+": "+root+"; read WARNINGS.md and original conditions");return 0;
        }catch(FusionWorkflowContracts.Unavailable error){return failed(root,error,3,err);}
        catch(Exception error){return failed(root,error,2,err);}
    }
    private static int failed(Path root,Exception error,int code,PrintStream err){
        String status=code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED":"FAIL2: NO COMPLETE RESULT";
        if(root!=null)try{
            for(String name:List.of("COMPLETED.json","manifest.json","WARNINGS.md"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("unadmitted-"+name));
            if(Files.exists(root.resolve("admitted"),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve("admitted"),root.resolve("unadmitted-exports"));
            write(root.resolve("FAILURE.json"),map("status",status,"exit_code",code,"results_admitted",false,"cause",error.toString()));text(root.resolve("WARNINGS.md"),"# "+status+"\n\n"+error+"\n\n"+FusionWorkflowContracts.ACTION+"\n");
        }catch(Exception diagnostic){err.println("Additional failure-journal problem: "+diagnostic);}
        err.println(status+": "+error+"\n"+FusionWorkflowContracts.ACTION+(root==null?"":"\nRead "+root.resolve("WARNINGS.md")));return code;
    }
    static String help(){return "fusion-tools --request UTF8_JSON | --verify CAPTURE | --help\nRUN declares exact source profile, TRAIN|ASSOCIATE|POSTPROCESS, Rscript/private libraries and expected versions, original inputs/options, explicit seed, admission and limits. REPLAY={schema_version:1,mode:REPLAY,analysis:PARENT,out:NEW}. REPLAY re-executes the original runtime; VERIFY reads saved integrity/relationships without R.\n"+FusionWorkflowContracts.SCOPE+"\n"+FusionWorkflowContracts.ACTION;}
}
