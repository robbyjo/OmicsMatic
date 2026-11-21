/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit original-source parameters, runtime identity and intake capacities. */
final class TwoSampleMrHistoricalClumpContracts {
    private TwoSampleMrHistoricalClumpContracts() { }
    static final String PROFILE="TwoSampleMR0.6.8-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64";
    static final String PLINK_SHA256="efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b";
    static final Set<String> PROFILES=Set.of(PROFILE);
    static final List<String> INPUTS=List.of("instruments","reference_bed","reference_bim","reference_fam");
    static String captured(String key) {
        if(!INPUTS.contains(key))throw new IllegalArgumentException("Unknown clumping input");
        return key.equals("instruments")?"inputs/instruments":"inputs/reference."+key.substring("reference_".length());
    }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","out","instruments","reference_bed","reference_bim","reference_fam","rscript","r_libraries","plink_executable","plink_sha256","seed","clump_kb","clump_r2","clump_p1","pop","reference_population","source_resources","allow_source_defaults","preparation_note","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_rows","maximum_columns","maximum_entries","maximum_reference_variants","maximum_reference_samples","maximum_genotype_entries","maximum_work"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version1 and mode RUN");
        var r=new LinkedHashMap<String,Object>(raw);r.put("profile",choice(raw,"profile","",PROFILES));
        if(!text(raw,"operation").equals("CLUMP"))throw new IOException("Only original local CLUMP is qualified by this request profile");
        for(String key:INPUTS)text(raw,key);
        for(String key:List.of("out","rscript","plink_executable","reference_population","preparation_note","pop"))text(raw,key);
        String sha=text(raw,"plink_sha256");if(!sha.matches("[0-9a-f]{64}"))throw new IOException("Require lowercase executable SHA256");
        if(!text(raw,"source_resources").equals("original-defaults"))throw new IOException("Explicit source_resources original-defaults is required; source clump_data forwards no thread/memory arguments");
        for(String key:List.of("clump_kb","clump_r2","clump_p1","seed"))if(!raw.containsKey(key))throw new IOException("Declare "+key+" explicitly; no implicit scientific defaults");
        r.put("clump_kb",real(raw,"clump_kb",0,0,Double.MAX_VALUE,true));
        r.put("clump_r2",real(raw,"clump_r2",0,0,1,true));r.put("clump_p1",real(raw,"clump_p1",0,0,1,true));
        r.put("seed",integer(raw,"seed",0,0,Integer.MAX_VALUE));
        var defaults=object(raw.getOrDefault("allow_source_defaults",Map.of()),"allow_source_defaults");keys(defaults,Set.of("generated_ids","pvalues"));
        r.put("allow_source_defaults",Map.of("generated_ids",bool(defaults,"generated_ids",false),"pvalues",bool(defaults,"pvalues",false)));
        r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512000000,1,20000000000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2000000000L,1,40000000000L));
        r.put("maximum_rows",integer(raw,"maximum_rows",1000000,1,2000000));r.put("maximum_columns",integer(raw,"maximum_columns",128,1,1024));r.put("maximum_entries",integer(raw,"maximum_entries",20000000,1,200000000));
        r.put("maximum_reference_variants",integer(raw,"maximum_reference_variants",10000000,1,100000000));r.put("maximum_reference_samples",integer(raw,"maximum_reference_samples",1000000,1,10000000));
        r.put("maximum_genotype_entries",integer(raw,"maximum_genotype_entries",10000000000L,1,1000000000000L));r.put("maximum_work",integer(raw,"maximum_work",10000000000L,1,1000000000000000L));return r;
    }
}
