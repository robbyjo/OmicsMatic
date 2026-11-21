/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal requests; source parameters and editions are never guessed. */
final class TwoSampleMrWorkflowContracts {
    private TwoSampleMrWorkflowContracts() { }
    static final String PROFILE="TwoSampleMR0.7.14-c08ab8a6-R4.6.1";
    static final Set<String> PROFILES=Set.of(PROFILE);
    static final List<String> INPUTS=List.of("exposure","outcome","harmonised");
    static final Set<String> METHODS=Set.of("mr_wald_ratio","mr_two_sample_ml","mr_egger_regression","mr_egger_regression_bootstrap","mr_simple_median","mr_weighted_median","mr_penalised_weighted_median","mr_ivw","mr_ivw_radial","mr_ivw_mre","mr_ivw_fe","mr_simple_mode","mr_weighted_mode","mr_weighted_mode_nome","mr_simple_mode_nome","mr_raps","mr_sign","mr_uwr","mr_grip");
    static boolean mr(String op){return op.equals("MR")||op.equals("HARMONISE_AND_MR");}
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","out","exposure","outcome","harmonised","harmonise_action","methods","parameters","sensitivity","rscript","r_libraries","seed","preparation_note","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_rows","maximum_columns","maximum_entries","maximum_work"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version1 and mode RUN");
        var r=new LinkedHashMap<String,Object>(raw);r.put("profile",choice(raw,"profile","",PROFILES));
        String op=choice(raw,"operation","",Set.of("HARMONISE","MR","HARMONISE_AND_MR","MV_HARMONISE"));
        text(raw,"out");text(raw,"rscript");text(raw,"preparation_note");
        if(op.equals("MR")){text(raw,"harmonised");if(raw.containsKey("exposure")||raw.containsKey("outcome")||raw.containsKey("harmonise_action"))throw new IOException("MR requires only the declared harmonised input");}
        else {text(raw,"exposure");text(raw,"outcome");if(raw.containsKey("harmonised"))throw new IOException("Unused harmonised input");if(!raw.containsKey("harmonise_action"))throw new IOException("Declare harmonise_action explicitly");r.put("harmonise_action",integer(raw,"harmonise_action",0,1,3));}
        if(mr(op)) {
            var methods=strings(raw.get("methods"),"methods",1,19);if(!METHODS.containsAll(methods))throw new IOException("Unknown original mr method; provider-specific MR-PRESSO/CAUSE/MVMR are separate tools");r.put("methods",methods);
            var p=object(raw.getOrDefault("parameters",Map.of()),"parameters");keys(p,Set.of("test_dist","nboot","Cov","penk","phi","alpha","Qthresh","over.dispersion","loss.function","shrinkage"));
            var parameters=new LinkedHashMap<String,Object>();parameters.put("test_dist",choice(p,"test_dist","z",Set.of("z","t")));parameters.put("nboot",integer(p,"nboot",1000,2,1000000));
            parameters.put("Cov",real(p,"Cov",0,-Double.MAX_VALUE,Double.MAX_VALUE,true));parameters.put("penk",real(p,"penk",20,0,Double.MAX_VALUE,false));parameters.put("phi",real(p,"phi",1,0,Double.MAX_VALUE,false));parameters.put("alpha",real(p,"alpha",.05,0,1,false));parameters.put("Qthresh",real(p,"Qthresh",.05,0,1,false));
            parameters.put("over.dispersion",bool(p,"over.dispersion",true));parameters.put("loss.function",choice(p,"loss.function","huber",Set.of("l2","huber","tukey")));parameters.put("shrinkage",bool(p,"shrinkage",false));r.put("parameters",parameters);
            var sensitivity=strings(raw.getOrDefault("sensitivity",List.of()),"sensitivity",0,4);if(!Set.of("HETEROGENEITY","PLEIOTROPY","SINGLE_SNP","LEAVE_ONE_OUT").containsAll(sensitivity))throw new IOException("Unknown sensitivity operation");r.put("sensitivity",sensitivity);
        } else for(String key:List.of("methods","parameters","sensitivity"))if(raw.containsKey(key))throw new IOException("Unused MR option: "+key);
        r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));r.put("seed",integer(raw,"seed",1,0,Integer.MAX_VALUE));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512000000,1,20000000000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2000000000L,1,40000000000L));
        r.put("maximum_rows",integer(raw,"maximum_rows",1000000,1,2000000));r.put("maximum_columns",integer(raw,"maximum_columns",128,1,1024));r.put("maximum_entries",integer(raw,"maximum_entries",20000000,1,200000000));r.put("maximum_work",integer(raw,"maximum_work",10000000000L,1,1000000000000000L));return r;
    }
}
