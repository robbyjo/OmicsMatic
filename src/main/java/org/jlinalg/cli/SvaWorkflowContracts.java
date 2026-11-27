/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed original SVA calls from explicit numeric matrices; no R expressions. */
final class SvaWorkflowContracts {
    private SvaWorkflowContracts() { }
    static final String PROFILE="sva-3.46.0-R4.2.3-linux64";
    static final Set<String> PROFILES=Set.of(PROFILE);
    static final List<String> INPUTS=List.of("matrix","design","null_design","controls","sva_source");
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","out","matrix","design","null_design","controls","sva_source","units","method","n_sv","num_sv_method","iterations","permutations","vfilter","constant","seed","rscript","r_libraries","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_features","maximum_samples","maximum_entries","maximum_design_columns","maximum_work","preparation_note"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"RUN".equals(text(raw,"mode")))throw new IOException("Require schema_version1 and mode RUN");
        choice(raw,"profile","",PROFILES);String op=choice(raw,"operation","",Set.of("SVA","SVASEQ","NUM_SV"));
        for(String field:List.of("out","matrix","design","sva_source","rscript"))text(raw,field);
        for(String field:INPUTS)if(raw.containsKey(field))text(raw,field);
        var r=new LinkedHashMap<String,Object>(raw);
        r.put("units",choice(raw,"units","",op.equals("SVASEQ")?Set.of("raw-counts","normalized-counts","estimated-counts"):Set.of("continuous-values","log2-expression","methylation-M")));
        if(op.equals("NUM_SV")) {
            for(String key:List.of("n_sv","method","iterations","null_design","controls","constant"))if(raw.containsKey(key))throw new IOException("Unused NUM_SV option: "+key);
            r.put("num_sv_method",choice(raw,"num_sv_method","",Set.of("be","leek")));
        }else {
            String method=choice(raw,"method","",Set.of("irw","two-step","supervised"));r.put("method",method);
            if(method.equals("supervised")&&!raw.containsKey("controls"))throw new IOException("Supervised SVA requires controls");
            String effective=raw.containsKey("controls")?"supervised":method;
            if(!effective.equals("irw")&&(raw.containsKey("iterations")||raw.containsKey("null_design")))throw new IOException("iterations/null_design require effective irw; controls override method in original source");
            if(effective.equals("irw"))r.put("iterations",integer(raw,"iterations",5,1,1000));
            if(raw.get("n_sv") instanceof String s&&s.equals("auto"))r.put("num_sv_method",choice(raw,"num_sv_method","",Set.of("be","leek")));
            else {r.put("n_sv",integer(raw,"n_sv",-1,0,10000));if(!raw.containsKey("n_sv")||raw.containsKey("num_sv_method"))throw new IOException("Require explicit n_sv integer or auto with num_sv_method; no unused count method");}
            if(op.equals("SVASEQ"))r.put("constant",real(raw,"constant",1,0,Double.MAX_VALUE,false));
            else if(raw.containsKey("constant"))throw new IOException("constant requires SVASEQ");
        }
        if(raw.containsKey("permutations")&&(!op.equals("NUM_SV")||!r.get("num_sv_method").equals("be")))throw new IOException("permutations configures only NUM_SV be; original SVA/SVASEQ auto uses num.sv B=20");
        if(op.equals("NUM_SV")&&r.get("num_sv_method").equals("be"))r.put("permutations",integer(raw,"permutations",20,1,10000));
        if(raw.containsKey("vfilter"))r.put("vfilter",integer(raw,"vfilter",0,100,2_000_000));
        if(!raw.containsKey("seed"))throw new IOException("Declare a project seed; it is not an inferred author seed");r.put("seed",integer(raw,"seed",0,0,Integer.MAX_VALUE));
        r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512_000_000,1,20_000_000_000L));
        r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2_000_000_000L,1,40_000_000_000L));
        r.put("maximum_features",integer(raw,"maximum_features",100_000,10,2_000_000));r.put("maximum_samples",integer(raw,"maximum_samples",10000,3,100000));
        r.put("maximum_entries",integer(raw,"maximum_entries",10_000_000,1,200_000_000));r.put("maximum_design_columns",integer(raw,"maximum_design_columns",128,1,1024));
        r.put("maximum_work",integer(raw,"maximum_work",2_000_000_000L,1,10_000_000_000_000L));
        if(raw.containsKey("preparation_note"))text(raw,"preparation_note");return r;
    }
}
