/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit original tensorQTL operations. No formulas, arbitrary flags or inferred author settings. */
final class TensorQtlWorkflowContracts {
    private TensorQtlWorkflowContracts() { }
    static final Set<String> PROFILES=Set.of("tensorqtl-1.0.5-py3.8.10-torch1.8.1+cu111","tensorqtl-1.0.9-py3.11.17-torch2.3.1+cu121");
    static final List<String> INPUTS=List.of("genotypes","variants","phenotypes","phenotype_positions","covariates","paired_covariates","groups","interaction","cis_results","trans_filter_positions");
    static boolean permutation(String op){return Set.of("CIS_PERMUTE","CIS_DISCOVERY","CIS_INDEPENDENT").contains(op);}
    static boolean requiresR(Map<String,Object> r){return Set.of("QVALUES","CIS_DISCOVERY","CIS_INDEPENDENT").contains(r.get("operation"))||r.get("operation").equals("CIS_PERMUTE")&&Boolean.TRUE.equals(r.get("qvalues"));}
    static Map<String,Object> profile(Map<String,Object> r)throws IOException {var all=object(SimpleJson.parse(new String(TensorQtlWorkflowRuntime.resource("tensorqtl-workflow/SOURCE-PROFILES.json"),StandardCharsets.UTF_8)),"profiles");return object(object(all.get("profiles"),"profiles").get(text(r,"profile")),"profile");}
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","out","python","source_directory","r_home","r_libraries","device","cuda_device","acknowledge_original_processing","genotypes","variants","phenotypes","phenotype_positions","covariates","paired_covariates","groups","interaction","cis_results","trans_filter_positions","trans_filter_position_kind","cis_exclusion_window","position_kind","seed","window","maf_threshold","nperm","beta_approx","random_tiebreak","qvalues","qvalue_lambda","fdr","fdr_column","return_sparse","pval_threshold","alleles","return_r2","batch_size","maf_threshold_interaction","run_eigenmt","susie","preparation_note","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_variants","maximum_phenotypes","maximum_samples","maximum_entries","maximum_work"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"RUN".equals(text(raw,"mode")))throw new IOException("Require schema_version1 and mode RUN");
        choice(raw,"profile","",PROFILES);String op=choice(raw,"operation","",Set.of("CIS_NOMINAL","CIS_PERMUTE","CIS_INDEPENDENT","CIS_DISCOVERY","TRANS_NOMINAL","CIS_SUSIE","QVALUES"));
        boolean old=text(raw,"profile").startsWith("tensorqtl-1.0.5-");var r=new LinkedHashMap<String,Object>(raw);
        for(String key:List.of("out","python","source_directory"))text(raw,key);for(String key:INPUTS)if(raw.containsKey(key))text(raw,key);
        if(!bool(raw,"acknowledge_original_processing",false))throw new IOException("Acknowledge original float32 casts, missing-dosage handling, MAF/constant/no-cis filtering and version-specific outputs; the adapter does not repair or harmonize inputs");
        r.put("device",choice(raw,"device","",Set.of("CPU","CUDA")));if(r.get("device").equals("CUDA"))r.put("cuda_device",integer(raw,"cuda_device",0,0,63));else if(raw.containsKey("cuda_device"))throw new IOException("cuda_device requires CUDA; no ignored device option");
        if(op.equals("QVALUES")&&!r.get("device").equals("CPU"))throw new IOException("Original qvalue postprocessing runs in R on CPU; no CUDA computation is claimed for this operation");
        if(!raw.containsKey("seed"))throw new IOException("Declare a project seed; no author seed is inferred");r.put("seed",integer(raw,"seed",0,0,Integer.MAX_VALUE));
        var inputs=new HashSet<String>();var options=new HashSet<String>();
        if(!op.equals("QVALUES")){
            inputs.addAll(List.of("genotypes","phenotypes"));
            if(!op.equals("TRANS_NOMINAL"))inputs.addAll(List.of("variants","phenotype_positions"));
            else if(raw.containsKey("trans_filter_positions"))inputs.add("phenotype_positions");
            for(String key:inputs)text(raw,key);if(raw.containsKey("covariates"))inputs.add("covariates");
            if(inputs.contains("phenotype_positions")){r.put("position_kind",choice(raw,"position_kind","point",old?Set.of("point"):Set.of("point","interval")));options.add("position_kind");}
        }
        if(!op.equals("QVALUES")){r.put("maf_threshold",real(raw,"maf_threshold",op.equals("TRANS_NOMINAL")?.05:0,0,.5,true));options.add("maf_threshold");}
        if(!Set.of("QVALUES","TRANS_NOMINAL").contains(op)){r.put("window",integer(raw,"window",1_000_000,0,Integer.MAX_VALUE));options.add("window");}
        if(permutation(op)) {
            r.put("nperm",integer(raw,"nperm",10000,1,1_000_000));options.add("nperm");
            if(!old){r.put("random_tiebreak",bool(raw,"random_tiebreak",false));options.add("random_tiebreak");}
        }
        if(op.equals("CIS_PERMUTE")){if(!raw.containsKey("qvalues"))throw new IOException("Declare whether original R q-values are requested");r.put("qvalues",bool(raw,"qvalues",false));options.add("qvalues");r.put("beta_approx",bool(raw,"beta_approx",true));options.add("beta_approx");if(Boolean.TRUE.equals(r.get("qvalues"))&&!Boolean.TRUE.equals(r.get("beta_approx")))throw new IOException("Original q-values require beta approximation");}
        if(Set.of("QVALUES","CIS_INDEPENDENT").contains(op)){text(raw,"cis_results");inputs.add("cis_results");}
        if(Set.of("CIS_INDEPENDENT","CIS_DISCOVERY","CIS_SUSIE").contains(op))text(raw,"covariates");
        if(Set.of("QVALUES","CIS_DISCOVERY","CIS_INDEPENDENT").contains(op)||op.equals("CIS_PERMUTE")&&Boolean.TRUE.equals(r.get("qvalues"))){r.put("fdr",real(raw,"fdr",.05,0,1,false));options.add("fdr");if(!op.equals("CIS_INDEPENDENT")&&raw.containsKey("qvalue_lambda")){Object x=raw.get("qvalue_lambda");r.put("qvalue_lambda",x==null?null:real(raw,"qvalue_lambda",Double.NaN,0,1,true));options.add("qvalue_lambda");if(x instanceof Number n&&n.doubleValue()>=1)throw new IOException("qvalue_lambda must be below1");}}
        if(op.equals("CIS_INDEPENDENT")){r.put("fdr_column",raw.containsKey("fdr_column")?text(raw,"fdr_column"):"qval");identity(text(r,"fdr_column"));options.add("fdr_column");}
        if(raw.containsKey("groups")){if(!Set.of("CIS_NOMINAL","CIS_PERMUTE","CIS_INDEPENDENT","CIS_DISCOVERY").contains(op))throw new IOException("groups not used by this original operation");inputs.add("groups");}
        if(raw.containsKey("paired_covariates")){if(old||!Set.of("CIS_NOMINAL","CIS_PERMUTE","CIS_SUSIE").contains(op))throw new IOException("Paired covariates require the original1.0.9 supported operation");text(raw,"covariates");inputs.add("paired_covariates");}
        if(raw.containsKey("interaction")){if(!Set.of("CIS_NOMINAL","TRANS_NOMINAL").contains(op))throw new IOException("Interaction requires original nominal mapping");inputs.add("interaction");}
        if(op.equals("CIS_NOMINAL")&&raw.containsKey("interaction")){r.put("maf_threshold_interaction",real(raw,"maf_threshold_interaction",.05,0,.5,true));r.put("run_eigenmt",bool(raw,"run_eigenmt",false));options.addAll(List.of("maf_threshold_interaction","run_eigenmt"));}
        if(op.equals("TRANS_NOMINAL")){
            r.put("return_sparse",bool(raw,"return_sparse",true));r.put("return_r2",bool(raw,"return_r2",false));r.put("pval_threshold",real(raw,"pval_threshold",1e-5,0,1,false));r.put("alleles",integer(raw,"alleles",2,1,2));r.put("batch_size",integer(raw,"batch_size",20000,1,1_000_000));options.addAll(List.of("return_sparse","return_r2","pval_threshold","alleles","batch_size"));
            if(raw.containsKey("trans_filter_positions")){if(!Boolean.TRUE.equals(r.get("return_sparse")))throw new IOException("Original cis filtering requires sparse trans tables");inputs.add("trans_filter_positions");r.put("trans_filter_position_kind",choice(raw,"trans_filter_position_kind","",Set.of("variant","declared-region-center")));r.put("cis_exclusion_window",integer(raw,"cis_exclusion_window",5_000_000,0,Integer.MAX_VALUE));options.addAll(List.of("trans_filter_position_kind","cis_exclusion_window"));}
        }
        if(op.equals("CIS_SUSIE")){
            if(old)throw new TensorQtlWorkflowRuntime.Unavailable("tensorQTL1.0.5 has no cis_susie implementation; select the explicit separately qualified1.0.9 profile or retain the skip");
            var s=object(raw.getOrDefault("susie",Map.of()),"susie");keys(s,Set.of("L","scaled_prior_variance","estimate_residual_variance","estimate_prior_variance","tolerance","coverage","min_abs_corr","maximum_iterations"));var v=new LinkedHashMap<String,Object>();
            v.put("L",integer(s,"L",10,1,1000));v.put("scaled_prior_variance",real(s,"scaled_prior_variance",.2,0,1,true));v.put("estimate_residual_variance",bool(s,"estimate_residual_variance",true));v.put("estimate_prior_variance",bool(s,"estimate_prior_variance",true));v.put("tolerance",real(s,"tolerance",.001,0,1e6,false));v.put("coverage",real(s,"coverage",.95,0,1,false));v.put("min_abs_corr",real(s,"min_abs_corr",.5,0,1,true));v.put("maximum_iterations",integer(s,"maximum_iterations",200,1,100000));r.put("susie",v);options.add("susie");
        }
        for(String key:INPUTS)if(raw.containsKey(key)&&!inputs.contains(key))throw new IOException("Unused input for selected operation: "+key);
        for(String key:List.of("position_kind","window","maf_threshold","nperm","beta_approx","random_tiebreak","qvalues","qvalue_lambda","fdr","fdr_column","return_sparse","pval_threshold","alleles","return_r2","batch_size","maf_threshold_interaction","run_eigenmt","susie","trans_filter_position_kind","cis_exclusion_window"))if(raw.containsKey(key)&&!options.contains(key))throw new IOException("Unused or unavailable option for selected edition/operation: "+key);
        if(raw.containsKey("r_home"))text(raw,"r_home");if(requiresR(r))text(raw,"r_home");
        if(raw.containsKey("r_libraries")){if(!raw.containsKey("r_home"))throw new IOException("R libraries require explicit R home");r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));}
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512_000_000,1,100_000_000_000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2_000_000_000L,1,100_000_000_000L));r.put("maximum_variants",integer(raw,"maximum_variants",100000,1,10_000_000));r.put("maximum_phenotypes",integer(raw,"maximum_phenotypes",10000,1,1_000_000));r.put("maximum_samples",integer(raw,"maximum_samples",10000,5,100000));r.put("maximum_entries",integer(raw,"maximum_entries",10_000_000,1,200_000_000));r.put("maximum_work",integer(raw,"maximum_work",8_000_000_000_000L,1,1_000_000_000_000_000_000L));
        if(raw.containsKey("preparation_note"))text(raw,"preparation_note");return r;
    }
}
