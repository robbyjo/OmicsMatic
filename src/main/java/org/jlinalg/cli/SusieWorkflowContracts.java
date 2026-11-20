/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed, edition-specific original SuSiE fitting requests; no user R code. */
final class SusieWorkflowContracts {
    private SusieWorkflowContracts() { }
    static final Set<String> PROFILES=Set.of("SuSiE-0.11.92-R4.6.1","SuSiE-0.12.35-R4.6.1","SuSiE-0.14.2-R4.6.1");
    static final List<String> INPUTS=List.of("variants","matrix","design","phenotype");
    static boolean legacy(Map<String,Object> request){return "SuSiE-0.11.92-R4.6.1".equals(request.get("profile"));}
    static boolean legacyRss(Map<String,Object> request){return legacy(request)&&"RSS_Z".equals(request.get("model"));}
    static boolean noSampleRss(Map<String,Object> request){return "RSS_Z".equals(request.get("model"))&&
        (legacy(request)||"not-provided".equals(request.get("sample_size")));}
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","model","out","variants","matrix","design","phenotype",
            "sample_size","variance_y","yty","ld_origin","effects","maximum_iterations","tolerance","coverage","min_abs_corr",
            "estimate_prior_variance","estimate_prior_method","scaled_prior_variance","prior_variance","residual_variance",
            "estimate_residual_variance","standardize","intercept","refine","n_purity","check_prior","prior_tol","seed",
            "rscript","r_libraries","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_variables",
            "maximum_samples","maximum_matrix_entries","maximum_work","preparation_note"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"RUN".equals(text(raw,"mode")))throw new IOException("Require schema_version1 and mode RUN");
        choice(raw,"profile","",PROFILES);String model=choice(raw,"model","",Set.of("INDIVIDUAL","RSS_Z","RSS_BETA_SE","SUFFICIENT","SUFFICIENT_BETA_SE"));
        var r=new LinkedHashMap<String,Object>(raw);for(String key:List.of("out","rscript"))text(raw,key);
        if(raw.containsKey("preparation_note"))identity(text(raw,"preparation_note"));
        if(model.equals("INDIVIDUAL")) {
            text(raw,"design");text(raw,"phenotype");if(raw.containsKey("variants"))text(raw,"variants");
            refuse(raw,"matrix","sample_size","variance_y","yty","ld_origin","check_prior");
        }else {
            text(raw,"variants");text(raw,"matrix");refuse(raw,"design","phenotype","intercept");
            if(model.equals("SUFFICIENT")) {
                requireField(raw,"sample_size");requireField(raw,"yty");
                r.put("sample_size",integer(raw,"sample_size",0,3,2_000_000_000L));
                r.put("yty",real(raw,"yty",1,0,Double.MAX_VALUE,false));
                refuse(raw,"variance_y","ld_origin");
            }else {
                choice(raw,"ld_origin","",Set.of("in-sample","reference"));refuse(raw,"yty");
                if(model.equals("SUFFICIENT_BETA_SE")!=legacy(r)) {
                    if(model.equals("SUFFICIENT_BETA_SE")||model.equals("RSS_BETA_SE"))
                        throw new IOException("0.11.92 beta/SE uses SUFFICIENT_BETA_SE; later editions use RSS_BETA_SE");
                }
                if(legacyRss(r)){refuse(raw,"sample_size","variance_y");}
                else {
                    if(model.equals("RSS_Z")&&"not-provided".equals(raw.get("sample_size")))r.put("sample_size","not-provided");
                    else {requireField(raw,"sample_size");r.put("sample_size",integer(raw,"sample_size",0,3,2_000_000_000L));}
                    if(model.equals("RSS_Z"))refuse(raw,"variance_y");
                    else {requireField(raw,"variance_y");r.put("variance_y",real(raw,"variance_y",1,0,Double.MAX_VALUE,false));}
                }
            }
        }
        r.put("effects",integer(raw,"effects",10,1,10000));r.put("maximum_iterations",integer(raw,"maximum_iterations",100,1,100000));
        r.put("tolerance",real(raw,"tolerance",.001,0,1e100,false));r.put("coverage",real(raw,"coverage",.95,0,1,false));
        r.put("min_abs_corr",real(raw,"min_abs_corr",.5,0,1,true));r.put("prior_tol",real(raw,"prior_tol",1e-9,0,1e100,true));
        r.put("estimate_prior_variance",bool(raw,"estimate_prior_variance",true));
        r.put("estimate_prior_method",choice(raw,"estimate_prior_method","optim",Set.of("optim","EM","simple")));
        r.put("refine",bool(raw,"refine",false));r.put("n_purity",integer(raw,"n_purity",100,1,1000000));
        if(noSampleRss(r)) {
            refuse(raw,"scaled_prior_variance","standardize");
            r.put("prior_variance",real(raw,"prior_variance",50,0,Double.MAX_VALUE,false));
        }else {
            refuse(raw,"prior_variance");r.put("scaled_prior_variance",real(raw,"scaled_prior_variance",.2,0,1,true));
            if(((Number)r.get("scaled_prior_variance")).doubleValue()==0)throw new IOException("scaled_prior_variance must be positive");
            r.put("standardize",bool(raw,"standardize",true));
        }
        if(legacyRss(r)){refuse(raw,"estimate_residual_variance","residual_variance");}
        else {
            boolean estimate=bool(raw,"estimate_residual_variance",model.equals("INDIVIDUAL")||model.startsWith("SUFFICIENT"));
            if(estimate&&"reference".equals(raw.get("ld_origin")))throw new IOException("Residual-variance estimation requires declared in-sample LD; no automatic reference substitution");
            r.put("estimate_residual_variance",estimate);
            if(raw.containsKey("residual_variance"))r.put("residual_variance",real(raw,"residual_variance",Double.NaN,0,Double.MAX_VALUE,false));
        }
        if(model.equals("INDIVIDUAL"))r.put("intercept",bool(raw,"intercept",true));
        else r.put("check_prior",bool(raw,"check_prior",model.startsWith("RSS")));
        r.put("seed",integer(raw,"seed",1,0,Integer.MAX_VALUE));
        r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512_000_000,1,20_000_000_000L));
        r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2_000_000_000L,1,40_000_000_000L));
        r.put("maximum_variables",integer(raw,"maximum_variables",5000,1,20000));
        r.put("maximum_samples",integer(raw,"maximum_samples",1000000,3,2000000));
        r.put("maximum_matrix_entries",integer(raw,"maximum_matrix_entries",10000000,1,400000000));
        r.put("maximum_work",integer(raw,"maximum_work",2_000_000_000L,1,100_000_000_000_000L));
        return r;
    }
    private static void refuse(Map<String,Object> r,String...names)throws IOException {
        for(String name:names)if(r.containsKey(name))throw new IOException("Field is not used by the declared model/profile: "+name);
    }
    private static void requireField(Map<String,Object> r,String name)throws IOException {
        if(!r.containsKey(name))throw new IOException("Required explicit field missing: "+name);
    }
}
