/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.MrPressoWorkflowFiles.*;

/** Native source-profile MR-PRESSO RUN, full recomputation VERIFY and actual REPLAY. */
public final class MrPressoToolsCli {
    private MrPressoToolsCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error){
        Path root=null;
        try{
            if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){
                MrPressoWorkflowAudit.verify(Path.of(args[1]));out.println("Verified complete captured native MR-PRESSO calculation, source, inputs and admission. "+MrPressoWorkflowContracts.SCOPE);return 0;
            }
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --verify CAPTURE or --help");
            Path path=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(path,16_777_216);var original=object(GprofilerJson.read(raw));
            String base=MrPressoWorkflowContracts.address(path.getParent()),style=MrPressoWorkflowContracts.pathStyle();
            Path parent=null;Map<String,Object> parentSeal=null,parentCompletion=null,request;
            if("REPLAY".equals(original.get("mode"))){
                keys(original,Set.of("schema_version","mode","analysis","out"));integer(original.get("schema_version"),1,1);
                parent=Path.of(MrPressoWorkflowContracts.resolveAddress(base,text(original.get("analysis"),"analysis"),style));MrPressoWorkflowAudit.verify(parent);
                parentSeal=pin(parent.resolve("capture-files.json"));parentCompletion=pin(parent.resolve("COMPLETED.json"));request=read(parent.resolve("canonical-request.json"));
                var inputs=new LinkedHashMap<>(object(request.get("inputs")));inputs.put("associations",MrPressoWorkflowContracts.address(parent.resolve("inputs/associations.tsv")));request.put("inputs",inputs);
                request.put("out",MrPressoWorkflowContracts.resolveAddress(base,text(original.get("out"),"out"),style));request=MrPressoWorkflowContracts.validate(request,base,style);
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target),"Replay output must be physically separate from its parent capture");
            }else{
                if("VERIFY".equals(original.get("mode"))){keys(original,Set.of("schema_version","mode","analysis"));integer(original.get("schema_version"),1,1);MrPressoWorkflowAudit.verify(Path.of(MrPressoWorkflowContracts.resolveAddress(base,text(original.get("analysis"),"analysis"),style)));out.println("Verified complete saved native MR-PRESSO recomputation.");return 0;}
                request=MrPressoWorkflowContracts.validate(original,base,style);
            }
            root=newOutput(Path.of(request.get("out").toString()));Files.createDirectory(root.resolve("inputs"));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);
            write(root.resolve("request-binding.json"),map("source_request",original,"canonical_request",request,"source_base",base,"path_style",style,"creation_address",request.get("out")));
            write(root.resolve("PROVISIONAL.json"),map("phase","RUN_START_NO_RESULT_ADMITTED","profile",MrPressoWorkflowContracts.PROFILE,"original_R_executed",false));
            var producer=MrPressoWorkflowSource.producer();write(root.resolve("producer.json"),producer);MrPressoWorkflowSource.sources(root,false);
            if(parent!=null){
                write(root.resolve("replay.json"),map("parent_address",MrPressoWorkflowContracts.address(parent),"parent_seal_identity",parentSeal,"parent_completion_identity",parentCompletion));
                for(var pair:Map.of("capture-files.json","replay-parent-capture-files.json","canonical-request.json","replay-parent-canonical.json", "native-result.json","replay-parent-result.json","producer.json","replay-parent-producer.json","COMPLETED.json","replay-parent-COMPLETED.json").entrySet())
                    Files.copy(parent.resolve(pair.getKey()),root.resolve(pair.getValue()));
            }
            Path input=Path.of(object(request.get("inputs")).get("associations").toString());
            var inputBinding=capture(input,root.resolve("inputs/associations.tsv"),MrPressoWorkflowContracts.limit(request,"maximum_input_bytes"));write(root.resolve("input-binding.json"),inputBinding);
            var result=MrPressoWorkflowEvaluation.evaluate(root.resolve("inputs/associations.tsv"),request);write(root.resolve("native-result.json"),result);
            text(root.resolve("WARNINGS.md"),MrPressoWorkflowAudit.warnings(result));
            require(Boolean.TRUE.equals(result.get("results_admitted")),"Native source-profile fields are unreportable under the declared policy; raw diagnostics retained; no complete result");
            for(var table:MrPressoWorkflowTables.tables(result).entrySet())text(root.resolve(table.getKey()),table.getValue());
            MrPressoWorkflowSource.sameProducer(producer);
            require(equal(inputBinding,read(root.resolve("input-binding.json")))&&equal(inputBinding.get("identity"),pin(root.resolve("inputs/associations.tsv"))),"Captured input/binding changed during native calculation");
            require(Arrays.equals(bytes(root.resolve("native-result.json"),67_108_864),json(result)),"Native result bytes changed before admission");
            MrPressoWorkflowSource.sources(root,true);MrPressoWorkflowAudit.binding(root,request);
            if(parent!=null){
                require(equal(parentSeal,pin(parent.resolve("capture-files.json")))&&equal(parentCompletion,pin(parent.resolve("COMPLETED.json")))&&equal(read(parent.resolve("capture-files.json")),inventory(parent)),"Replay parent changed during capture; child result withheld");
                require(equal(pin(root.resolve("inputs/associations.tsv")),object(read(parent.resolve("capture-files.json")).get("files")).get("inputs/associations.tsv")),"Replay input bytes differ from parent");
                MrPressoWorkflowEvaluation.compare(read(root.resolve("replay-parent-result.json")),result,"replay");
                write(root.resolve("replay-comparison.json"),map("parent_input_bytes_equal",true,"scientific_fields_equal",true,"integer_counts_ids_rng_exact",true,"finite_cell_relative_tolerance",3e-12,"finite_cell_ulp_tolerance",16,"numeric_zero_exact",true));
            }
            write(root.resolve("analysis-status.json"),MrPressoWorkflowAudit.status(result));write(root.resolve("capture-files.json"),inventory(root));
            write(root.resolve("COMPLETED.json"),map("profile",MrPressoWorkflowContracts.PROFILE,"results_admitted",true,"seal_identity",pin(root.resolve("capture-files.json"))));
            out.println("Completed native MR-PRESSO capture: "+root+". Read WARNINGS.md; VERIFY recomputes the full native calculation. "+MrPressoWorkflowContracts.SCOPE);return 0;
        }catch(Exception failure){
            int code=failure instanceof MrPressoWorkflowContracts.Unavailable||failure instanceof NoSuchFileException?3:2;
            if(root!=null)try{
                if(Files.exists(root.resolve("analysis-status.json"),LinkOption.NOFOLLOW_LINKS)&&!Files.exists(root.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS)){
                    // Retain the exact earlier owned admission attempt before
                    // reporting a later seal/write failure as no result.
                    byte[] earlier=bytes(root.resolve("analysis-status.json"),1_048_576);
                    Files.write(root.resolve("retained-partial-analysis-status.json"),earlier,StandardOpenOption.CREATE_NEW);
                    require(Arrays.equals(earlier,bytes(root.resolve("analysis-status.json"),1_048_576)),"Concurrent status change; preserve it");
                    Files.write(root.resolve("analysis-status.json"),json(map("schema_version",1,"profile",MrPressoWorkflowContracts.PROFILE,"status","FAIL2_NO_COMPLETE_RESULT","results_admitted",false,"error",failure.toString())),StandardOpenOption.TRUNCATE_EXISTING);
                    if(Files.exists(root.resolve("capture-files.json"),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve("capture-files.json"),root.resolve("retained-partial-capture-files.json"));
                }
                if(!Files.exists(root.resolve("analysis-status.json"),LinkOption.NOFOLLOW_LINKS))write(root.resolve("analysis-status.json"),map("schema_version",1,"profile",MrPressoWorkflowContracts.PROFILE,"status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT","results_admitted",false,"error",failure.toString(),"original_R_executed",false));
                write(root.resolve("FAILURE.json"),map("error",failure.toString(),"complete_result",false,"required_action",MrPressoWorkflowContracts.ACTION));
                if(!Files.exists(root.resolve("WARNINGS.md"),LinkOption.NOFOLLOW_LINKS))text(root.resolve("WARNINGS.md"),"# No complete result\n\n"+failure+"\n\n"+MrPressoWorkflowContracts.SCOPE+"\n\n"+MrPressoWorkflowContracts.ACTION+"\n");
                if(!Files.exists(root.resolve("capture-files.json"),LinkOption.NOFOLLOW_LINKS))write(root.resolve("capture-files.json"),inventory(root));
            }catch(Exception retention){error.println("Capture retention warning: "+retention);}
            error.println((code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED: ":"FAIL2: NO COMPLETE RESULT: ")+failure+"\n"+MrPressoWorkflowContracts.ACTION);return code;
        }
    }
    private static String help(){return "mrpresso-tools --request JSON | --verify CAPTURE | --help\nSchema1 modes RUN,VERIFY,REPLAY. RUN: engine=native,profile="+MrPressoWorkflowContracts.PROFILE+",operation=ANALYZE,out,inputs{associations,id_column,beta_outcome,se_outcome,beta_exposure[],se_exposure[]},rng{kind=Mersenne-Twister/Inversion,sample_kind=Rejection|Rounding,seed|state[626]}. Options preserve original defaults: OUTLIERtest=false,DISTORTIONtest=false,SignifThreshold=.05,NbDistribution=1000. Native input: unquoted UTF-8 TSV, explicit selected columns, unique literal row IDs. Mathematical NA/NaN cells trigger source complete-case omission; ID strings remain literal. Explicit limits and policies.allow_unreportable_fields are supported. R is not a runtime prerequisite.\n"+MrPressoWorkflowContracts.SCOPE+"\n"+MrPressoWorkflowContracts.ACTION;}
}
