/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Explicit versioned original APIs on data-only prepared local libraries and matrices. */
final class GseapyWorkflowRequest {
    private GseapyWorkflowRequest(){ }
    record Input(String id,Path source,String declaredSource) { }
    record Request(String edition,Path runtime,Path out,List<Map<String,Object>> analyses,List<Input> inputs,
            long inputBytes,long outputBytes,int maximumFiles,int timeout,long auditOperations,
            long maximumMatrixCells,long pythonHashSeed){
        Map<String,Object> worker(Map<String,Path> prepared)throws IOException{
            var cases=new ArrayList<Map<String,Object>>();for(var original:analyses){var a=new LinkedHashMap<>(original);a.put("prepared",prepared.get(text(a.get("id"))).toString());cases.add(a);}
            return Map.of("schema_version",1,"edition",edition,"analyses",cases,"maximum_matrix_cells",maximumMatrixCells,"python_hash_seed",pythonHashSeed);
        }
    }
    static Request parse(Map<String,Object> root,Path parent)throws IOException{
        allowed(root,"schema_version","mode","edition","runtime","out","analyses","maximum_input_bytes","maximum_output_bytes","maximum_files","timeout_seconds","maximum_audit_operations","maximum_matrix_cells","python_hash_seed");integer(root.get("schema_version"),1,1);require(text(root.get("mode")).equals("RUN"),"RUN installs nothing; prepare the exact runtime separately");String edition=text(root.get("edition"));require(Set.of("1.0.4","1.1.2","1.1.3","1.1.8").contains(edition),"Declare an exact supported GSEApy edition; no paper edition is inferred");var analyses=new ArrayList<Map<String,Object>>();var inputs=new ArrayList<Input>();var ids=new HashSet<String>();
        for(Object raw:list(root.get("analyses"))){
            var a=object(raw);allowed(a,"id","method","interface","input","global_seed","options","policies");require(text(a.get("interface")).equals("original-class"),"Declare original-class explicitly; the legacy convenience wrapper does not reliably forward all scientific options");String id=CellPhoneDbWorkflowRequest.id(a.get("id"));require(ids.add(id.toLowerCase(Locale.ROOT))&&analyses.size()<256,"Require1..256 unique portable case IDs, including case folding");String method=text(a.get("method"));require(Set.of("enrichr","prerank","gsea","ssgsea","gsva").contains(method),"Declare an original supported method");require(!(edition.equals("1.0.4")&&method.equals("gsva")),"Original GSEApy1.0.4 has no GSVA API; select a supported explicit edition or skip this analysis");integer(a.get("global_seed"),0,4294967295L);var input=object(a.get("input"));allowed(input,"path","format","source");require(text(input.get("format")).equals("prepared-json-float64"),"Declare the strict data-only JSON float64 profile; no pickle or file-suffix guessing");inputs.add(new Input(id,resolve(parent,label(input.get("path"))),label(input.get("source"))));
            options(method,object(a.get("options")));var policies=object(a.get("policies"));allowed(policies,"source_input_changes","nonfinite_diagnostics","empty_results");for(String name:List.of("source_input_changes","nonfinite_diagnostics","empty_results"))require(Set.of("fail","retain-with-warning").contains(text(policies.get(name))),"Declare fail or retain-with-warning for "+name+"; policies do not repair source exceptions");analyses.add(Collections.unmodifiableMap(new LinkedHashMap<>(a)));
        }
        require(!analyses.isEmpty(),"Require the complete nonempty analysis family");return new Request(edition,resolve(parent,label(root.get("runtime"))),resolve(parent,label(root.get("out"))),List.copyOf(analyses),List.copyOf(inputs),number(root,"maximum_input_bytes",536870912,1,17179869184L),number(root,"maximum_output_bytes",4294967296L,1024,1099511627776L),Math.toIntExact(number(root,"maximum_files",100000,1,1000000)),Math.toIntExact(number(root,"timeout_seconds",1800,1,86400)),number(root,"maximum_audit_operations",2000000000L,1,10000000000000L),number(root,"maximum_matrix_cells",25000000,1,100000000),number(root,"python_hash_seed",0,0,4294967295L));
    }
    private static void options(String method,Map<String,Object> a)throws IOException{
        if(method.equals("enrichr")){allowed(a,"organism","cutoff");require(Set.of("human","mouse","fly","yeast","worm","fish").contains(text(a.get("organism"))),"Declare the original organism explicitly");finite(a.get("cutoff"),0,1);return;}
        if(method.equals("prerank"))allowed(a,"min_size","max_size","permutation_num","weight","ascending","threads","seed");
        if(method.equals("gsea"))allowed(a,"min_size","max_size","permutation_num","weight","ascending","threads","seed","permutation_type","method");
        if(method.equals("ssgsea"))allowed(a,"min_size","max_size","permutation_num","weight","ascending","threads","seed","sample_norm_method","correl_norm_type");
        if(method.equals("gsva"))allowed(a,"min_size","max_size","weight","threads","seed","kcdf","mx_diff","abs_rnk");
        long min=integer(a.get("min_size"),1,10000000),max=integer(a.get("max_size"),1,10000000);require(min<=max,"Gene-set size limits are inverted");finite(a.get("weight"),0,8);integer(a.get("threads"),1,64);integer(a.get("seed"),0,4294967295L);
        if(!method.equals("gsva")){integer(a.get("permutation_num"),0,1000000);bool(a.get("ascending"));}
        if(method.equals("gsea")){require(Set.of("phenotype","gene_set").contains(text(a.get("permutation_type"))),"Declare original phenotype or gene_set permutations");require(Set.of("signal_to_noise","abs_signal_to_noise","t_test","ratio_of_classes","diff_of_classes","log2_ratio_of_classes").contains(text(a.get("method"))),"Declare the original ranking statistic");}
        if(method.equals("ssgsea")){require(a.containsKey("sample_norm_method"),"Declare sample normalization, including explicit null");if(a.get("sample_norm_method")!=null)require(Set.of("rank","log","log_rank","custom").contains(text(a.get("sample_norm_method"))),"Unsupported original sample normalization");require(Set.of("rank","symrank","zscore").contains(text(a.get("correl_norm_type"))),"Declare original rank/symrank/zscore correlation transform");}
        if(method.equals("gsva")){require(a.containsKey("kcdf"),"Declare Gaussian/Poisson kernel or explicit null for direct ECDF");if(a.get("kcdf")!=null)require(Set.of("Gaussian","Poisson").contains(text(a.get("kcdf"))),"Unsupported kernel; no implicit substitution");bool(a.get("mx_diff"));bool(a.get("abs_rnk"));}
    }
    static void allowed(Map<String,Object> value,String... names)throws IOException{var keys=Set.of(names);for(String key:value.keySet())require(keys.contains(key),"Unknown GSEApy workflow field: "+key);}
}
