/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit prepared continuous data and the exact original dense PEER profile. */
final class PeerWorkflowContracts {
    static final String PROFILE="PEER-1.3-40bc4b2-original-fp32";
    static final List<String> INPUTS=List.of("matrix","covariates","measurement_variance");
    private PeerWorkflowContracts() { }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","out","matrix","covariates","measurement_variance","preparation_note","runtime",
            "factors","seed","initialization","add_mean","max_iterations","tolerance","variance_tolerance","alpha_a","alpha_b","epsilon_a","epsilon_b","covariate_precision","verbose",
            "timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_cells","maximum_line_bytes"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"RUN".equals(text(raw,"mode")))throw new IOException("Require schema_version 1 and mode RUN");
        String profile=text(raw,"profile");if(!profile.matches("PEER-[A-Za-z0-9][A-Za-z0-9.-]{0,100}"))throw new IOException("Require an explicit PEER source profile");
        text(raw,"out");text(raw,"matrix");identity(text(raw,"preparation_note"));
        for(String name:INPUTS)if(raw.containsKey(name))text(raw,name);
        var runtime=object(raw.get("runtime"),"runtime");keys(runtime,Set.of("executable","sha256","wsl_distribution"));text(runtime,"executable");
        if(!text(runtime,"sha256").matches("[0-9a-f]{64}"))throw new IOException("Runtime requires an explicit lowercase SHA-256");
        if(runtime.containsKey("wsl_distribution")&&!text(runtime,"wsl_distribution").matches("[A-Za-z0-9_.-]{1,128}"))throw new IOException("Invalid explicit WSL distribution");
        var r=new LinkedHashMap<String,Object>(raw);r.put("runtime",runtime);
        if(!raw.containsKey("factors")||!raw.containsKey("seed"))throw new IOException("Require explicit factors and unsigned C srand seed");
        r.put("factors",integer(raw,"factors",0,1,1_000_000));r.put("seed",integer(raw,"seed",0,0,4_294_967_295L));
        r.put("initialization",choice(raw,"initialization","randn",Set.of("randn","pca")));r.put("add_mean",bool(raw,"add_mean",false));
        r.put("max_iterations",integer(raw,"max_iterations",1000,1,1_000_000));r.put("verbose",integer(raw,"verbose",1,0,3));
        r.put("tolerance",real(raw,"tolerance",1e-3,0,1e100,true));r.put("variance_tolerance",real(raw,"variance_tolerance",1e-8,0,1e100,true));
        for(var entry:Map.of("alpha_a",.001,"alpha_b",.1,"epsilon_a",.1,"epsilon_b",10.,"covariate_precision",100.).entrySet())r.put(entry.getKey(),real(raw,entry.getKey(),entry.getValue(),0,1e30,false));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512_000_000,1,20_000_000_000L));
        r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",1_000_000_000,1,40_000_000_000L));r.put("maximum_cells",integer(raw,"maximum_cells",10_000_000,1,50_000_000));
        r.put("maximum_line_bytes",integer(raw,"maximum_line_bytes",2_000_000,1,32_000_000));return r;
    }
    static Map<String,Object> captured(Map<String,Object> raw) {
        var r=new LinkedHashMap<String,Object>(raw);r.put("out",".");
        for(String key:INPUTS)if(raw.containsKey(key))r.put(key,"inputs/"+key+".tsv");return r;
    }
    static long limit(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
}
