/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SmartpcaWorkflowFiles.*;

/** Prepared EIGENSTRAT FST: native RUN, read-only recomputation VERIFY and actual REPLAY. */
public final class SmartpcaFstCli {
    private SmartpcaFstCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error) {
        Path root=null;
        try {
            if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){SmartpcaFstAudit.verify(Path.of(args[1]));out.println("Verified complete native smartpca recomputation, source, inputs and field admission. "+SmartpcaFstContracts.SCOPE);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --verify CAPTURE or --help");
            Path path=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(path,16_777_216);var original=object(GprofilerJson.read(raw));
            String base=SmartpcaWorkflowContracts.address(path.getParent()),style=SmartpcaWorkflowContracts.style();
            if("VERIFY".equals(original.get("mode"))) {
                keys(original,Set.of("schema_version","mode","analysis"));integer(original.get("schema_version"),1,1);
                SmartpcaFstAudit.verify(Path.of(SmartpcaWorkflowContracts.resolve(base,text(original.get("analysis"),"analysis"),style)));
                out.println("Verified complete saved native smartpca recomputation.");return 0;
            }
            Map<String,Object> request,parentSeal=null,parentCompletion=null;Path parent=null;
            if("REPLAY".equals(original.get("mode"))) {
                keys(original,Set.of("schema_version","mode","analysis","out"));integer(original.get("schema_version"),1,1);
                parent=SmartpcaFstAudit.existing(Path.of(SmartpcaWorkflowContracts.resolve(base,text(original.get("analysis"),"analysis"),style)));SmartpcaFstAudit.verify(parent);
                parentSeal=pin(parent.resolve("capture-files.json"));parentCompletion=pin(parent.resolve("COMPLETED.json"));
                request=SmartpcaFstAudit.replayRequest(read(parent.resolve("canonical-request.json")),SmartpcaWorkflowContracts.address(parent),SmartpcaWorkflowContracts.resolve(base,text(original.get("out"),"out"),style),base,style);
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target),"REPLAY output must be disjoint from its physical parent capture");
            }else request=SmartpcaFstContracts.validate(original,base,style);
            root=newOutput(Path.of(request.get("out").toString()));
            Files.createDirectory(root.resolve("inputs"));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);
            write(root.resolve("request-binding.json"),map("source_request",original,"canonical_request",request,"source_base",base,"path_style",style,"creation_address",request.get("out")));
            write(root.resolve("PROVISIONAL.json"),SmartpcaFstAudit.provisional());
            var producer=SmartpcaWorkflowSource.producer();SmartpcaFstAudit.producerOrigins();write(root.resolve("producer.json"),producer);SmartpcaWorkflowSource.sources(root,false);
            if(parent!=null) {
                write(root.resolve("replay.json"),map("parent_address",SmartpcaWorkflowContracts.address(parent),"parent_seal_identity",parentSeal,"parent_completion_identity",parentCompletion));
                for(var pair:SmartpcaFstAudit.PARENT_FILES.entrySet())Files.copy(parent.resolve(pair.getKey()),root.resolve(pair.getValue()));
            }
            var bindings=new LinkedHashMap<String,Object>();long total=0;
            for(String name:SmartpcaWorkflowContracts.INPUTS) {
                Path input=Path.of(object(request.get("inputs")).get(name).toString());
                var value=capture(input,root.resolve("inputs").resolve(SmartpcaWorkflowData.NAMES.get(name)),SmartpcaWorkflowContracts.limit(request,"maximum_input_bytes"));
                bindings.put(name,value);total=Math.addExact(total,number(object(value.get("identity")),"bytes"));
                require(total<=SmartpcaWorkflowContracts.limit(request,"maximum_total_input_bytes"),"Complete inputs exceed combined declared byte capacity");
            }
            write(root.resolve("input-binding.json"),bindings);
            var budget=new SmartpcaWorkflowTables.Budget(SmartpcaWorkflowContracts.limit(request,"maximum_output_bytes"));
            var evaluated=SmartpcaFstEvaluation.evaluate(root,request,budget);SmartpcaFstTables.writeOutputs(root,evaluated,budget);
            text(root.resolve("WARNINGS.md"),SmartpcaFstAudit.warnings(evaluated.result()));
            require(Boolean.TRUE.equals(evaluated.result().get("results_admitted")),"Native source FST/inbreeding estimates are unreportable under the declared policy; raw fields retained without a complete admitted result");
            SmartpcaWorkflowSource.sameProducer(producer);SmartpcaWorkflowSource.sources(root,true);SmartpcaFstAudit.binding(root,request);SmartpcaFstAudit.checkOutputs(root,evaluated);
            if(parent!=null) {
                require(equal(parentSeal,pin(parent.resolve("capture-files.json")))&&equal(parentCompletion,pin(parent.resolve("COMPLETED.json")))&&equal(read(parent.resolve("capture-files.json")),inventory(parent)),"Replay parent changed during calculation; child withheld");
                SmartpcaFstTables.compareTables(parent,root);
                write(root.resolve("replay-comparison.json"),SmartpcaFstAudit.comparison());SmartpcaFstAudit.replay(root,request,evaluated.result());
            }
            write(root.resolve("analysis-status.json"),SmartpcaFstAudit.status(evaluated.result()));
            var manifest=inventory(root);SmartpcaFstAudit.namespace(root,manifest,evaluated.result());SmartpcaFstAudit.outputBytes(manifest,request);
            write(root.resolve("capture-files.json"),manifest);write(root.resolve("COMPLETED.json"),SmartpcaFstAudit.completion(root));
            out.println("Completed native smartpca capture: "+root+". Read WARNINGS.md and per-row admission flags. VERIFY recomputes the calculation. "+SmartpcaFstContracts.SCOPE);return 0;
        }catch(Exception failure) {
            int code=failure instanceof SmartpcaWorkflowContracts.Unavailable?3:2;
            if(root!=null)try{retain(root,code,failure);}catch(Exception retention){error.println("Capture retention warning: "+retention);}
            error.println((code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED: ":"FAIL2: NO COMPLETE RESULT: ")+failure+"\n"+SmartpcaFstContracts.ACTION);return code;
        }
    }
    private static void retain(Path root,int code,Exception failure)throws IOException {
        // These files are owned by this newly created capture; retain partial admission attempts.
        for(String name:Set.of("COMPLETED.json","analysis-status.json","capture-files.json"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("retained-partial-"+name));
        write(root.resolve("analysis-status.json"),map("schema_version",1,"profile",SmartpcaFstContracts.PROFILE,"status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT","results_admitted",false,"error",failure.toString(),"original_C_executed",false));
        write(root.resolve("FAILURE.json"),map("error",failure.toString(),"complete_result",false,"required_action",SmartpcaFstContracts.ACTION));
        if(!Files.exists(root.resolve("WARNINGS.md"),LinkOption.NOFOLLOW_LINKS))text(root.resolve("WARNINGS.md"),"# No complete result\n\n"+failure+"\n\n"+SmartpcaFstContracts.SCOPE+"\n\n"+SmartpcaFstContracts.ACTION+"\n");
        write(root.resolve("capture-files.json"),inventory(root));
    }
    private static String help(){return "smartpca FST --request JSON | --verify CAPTURE | --help\nRUN schema1,engine=native,profile="+SmartpcaFstContracts.PROFILE+",operation=FST,out,inputs{genotypes,markers,samples,populations:[literal names]|null,genome_build}. Source options: inbreed=false,blgsize=.05. PCA normalization/allele-count settings are not FST options. Original-source blocks, weighted jackknife, negative/raw estimates and per-row admission are preserved. VERIFY fully recomputes; REPLAY verifies the parent and recalculates into a disjoint NEW output. No external EIGENSOFT runtime is required.\n"+SmartpcaFstContracts.SCOPE+"\n"+SmartpcaFstContracts.ACTION;}
}
