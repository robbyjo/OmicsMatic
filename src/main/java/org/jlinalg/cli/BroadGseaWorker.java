/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.enrichment.BroadGseaEngine;
import static org.jlinalg.cli.BroadGseaFiles.*;
import static org.jlinalg.cli.BroadGseaRecipe.*;

/** Owned worker. It only consumes captured prepared data and the effective recipe. */
final class BroadGseaWorker {
    private BroadGseaWorker(){ }
    static int run(Path root,PrintStream out,PrintStream err){
        int completed=0;String phase="initialization";String caseId="";
        try{
            var request=BroadGseaRecipe.run(capturedObject(root,"effective-request.json",RECIPE_BYTES));
            if(!same(request.raw(),effective(request)))throw new IOException("Worker effective request has nonportable or undeclared paths");
            sameProducer(capturedObject(root,"producer.json",RECIPE_BYTES),producer());
            var bindings=capturedObject(root,"input-bindings.json",RECIPE_BYTES);
            if(bindings.size()!=request.analyses().size())throw new IOException("Input binding population differs");
            for(int i=0;i<request.analyses().size();i++){
                var a=request.analyses().get(i);caseId=a.id();phase="input_validation";byte[] raw=captured(root,a.input(),request.caps().inputBytes());
                var binding=object(bindings.get(a.input()),"input binding");if(!same(pin(raw),binding.get("captured")))throw new IOException("Captured input changed for "+a.id());
                var input=prepared(raw,a,request.caps());Path result=root.resolve(outputName(i));Files.createDirectory(result);phase="native_computation";
                BroadGseaEngine.Result computed;
                try(var progress=new BroadGseaOutputs.Progress(result.resolve("partial"))){
                    computed=switch(a.form()){
                        case "PRERANKED"->BroadGseaEngine.preranked(input.genes(),input.scores(),input.sets(),a.settings(),progress);
                        case "EXPRESSION_GENE_SET"->BroadGseaEngine.expressionGeneSetNull(input.genes(),input.expression(),input.grouping(),a.ranking(),input.sets(),a.settings(),progress);
                        case "EXPRESSION_PHENOTYPE"->BroadGseaEngine.expressionPhenotypeNull(input.genes(),input.expression(),input.grouping(),a.ranking(),a.randomization(),input.sets(),a.settings(),progress);
                        default->throw new IOException("Unknown form");
                    };phase="result_serialization";BroadGseaOutputs.write(result,computed);
                }
                completed++;out.println("Completed analysis "+NextflowJson.json(a.id())+"; complete family sets="+computed.sets().size());out.flush();
            }
            json(root.resolve("worker/status.json"),Map.of("status","PASS_NATIVE_ENGINE","complete_analyses",completed,"requested_analyses",request.analyses().size()));return 0;
        }catch(Throwable error){
            error.printStackTrace(err);err.flush();int code=failure(error);
            try{
                var details=new LinkedHashMap<String,Object>();details.put("status",code==3?"SKIP3":"FAIL2");details.put("complete_result",false);details.put("complete_analyses",completed);details.put("phase",phase);details.put("analysis_id",caseId);details.put("error",error.toString());details.put("required_action",ACTION);
                if(error instanceof BroadGseaEngine.Failure e){details.put("engine_phase",e.phase());details.put("set",e.set());details.put("permutation_index",e.permutation());}
                json(root.resolve("worker/status.json"),details);
            }catch(Throwable closure){closure.printStackTrace(err);}return code;
        }
    }
}
