/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prespecified binary or mixed-annotation S-LDSC model and complete regression-source contract. */
public final class StudyGwasEnrichmentPolicy {
    private StudyGwasEnrichmentPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input){
        var p=new LinkedHashMap<String,Object>(input);
        unknown(p,Set.of("enabled","annotation_mode","summary","reference","ld_scores","annotations","overlap","blocks","minimum_snps","maximum_snps","minimum_chromosomes","maximum_annotations","maximum_work","minimum_coverage","minimum_h2_z","chi_square_max","excluded_regions","intercept","adjustment"),"gwas_enrichment");
        p.putIfAbsent("enabled",true);p.putIfAbsent("annotation_mode","binary");p.putIfAbsent("blocks",200);p.putIfAbsent("minimum_snps",10000);p.putIfAbsent("maximum_snps",2000000);p.putIfAbsent("minimum_chromosomes",20);p.putIfAbsent("maximum_annotations",128);p.putIfAbsent("maximum_work",10000000000L);p.putIfAbsent("minimum_coverage",.9);p.putIfAbsent("minimum_h2_z",4.0);p.putIfAbsent("intercept","free");p.putIfAbsent("adjustment","BH");
        p.putIfAbsent("excluded_regions",List.of(Map.of("chromosome",6,"start",25000000,"end",34000000,"reason","prespecified broad MHC exclusion")));
        if(!(p.get("enabled") instanceof Boolean))throw new IllegalArgumentException("gwas_enrichment.enabled must be boolean");
        choice(p,"intercept",Set.of("free","one"));choice(p,"adjustment",Set.of("BH","BY","NONE"));
        choice(p,"annotation_mode",Set.of("binary","mixed"));
        integer(p,"blocks",3,2000);integer(p,"minimum_snps",10,2000000);integer(p,"maximum_snps",10,2000000);integer(p,"minimum_chromosomes",1,22);integer(p,"maximum_annotations",2,128);integer(p,"maximum_work",1,1000000000000L);
        if(((Number)p.get("minimum_snps")).longValue()>((Number)p.get("maximum_snps")).longValue())throw new IllegalArgumentException("GWAS enrichment SNP bounds conflict");
        number(p,"minimum_coverage",0,1);number(p,"minimum_h2_z",0,100);
        if(p.containsKey("chi_square_max"))number(p,"chi_square_max",Double.MIN_NORMAL,Double.MAX_VALUE);
        for(String file:List.of("ld_scores","annotations","overlap"))if(p.containsKey(file))text(p,file);
        if(p.containsKey("summary")){
            var s=StudyIO.map(p.get("summary"),"gwas_enrichment.summary");
            unknown(s,Set.of("file","coverage","tested_snps","statistic","statistic_column","id_column","n_column","chromosome_column","position_column","ref_column","alt_column","population","genome_build","calibration","model","qc","ascertainment","provenance"),"gwas_enrichment.summary");
            s.putIfAbsent("coverage","unknown");s.putIfAbsent("statistic","chi_square");s.putIfAbsent("statistic_column","chi_square");s.putIfAbsent("id_column",s.containsKey("file")?"variant_id":"feature_id");s.putIfAbsent("n_column","n");s.putIfAbsent("chromosome_column","chromosome");s.putIfAbsent("position_column","position");s.putIfAbsent("ref_column","ref");s.putIfAbsent("alt_column","alt");
            choice(s,"coverage",Set.of("complete","selected_only","unknown"));choice(s,"statistic",Set.of("chi_square","z","beta_se"));
            if(s.containsKey("calibration"))choice(s,"calibration",Set.of("one_df_chi_square"));
            if(s.containsKey("ascertainment"))choice(s,"ascertainment",Set.of("quantitative","case_control"));
            for(String key:s.keySet())if(!Set.of("tested_snps","provenance").contains(key))text(s,key);
            if(s.containsKey("tested_snps"))integer(s,"tested_snps",1,Integer.MAX_VALUE);
            if(s.containsKey("provenance"))StudyIO.map(s.get("provenance"),"GWAS summary provenance");p.put("summary",s);
        }
        if(p.containsKey("reference")){
            var r=StudyIO.map(p.get("reference"),"gwas_enrichment.reference");
            unknown(r,Set.of("population","genome_build","release","common_variant_definition","regression_snp_definition","annotation_model","provenance"),"gwas_enrichment.reference");
            for(String key:r.keySet())if(!key.equals("provenance"))text(r,key);
            if(r.containsKey("provenance"))StudyIO.map(r.get("provenance"),"GWAS LD provenance");p.put("reference",r);
        }
        for(Object raw:StudyIO.list(p.get("excluded_regions"),"excluded_regions")){
            var r=StudyIO.map(raw,"excluded region");unknown(r,Set.of("chromosome","start","end","reason"),"excluded region");
            integer(r,"chromosome",1,22);integer(r,"start",1,Integer.MAX_VALUE);integer(r,"end",1,Integer.MAX_VALUE);text(r,"reason");
            if(((Number)r.get("start")).intValue()>((Number)r.get("end")).intValue())throw new IllegalArgumentException("GWAS excluded-region coordinates conflict");
        }
        return p;
    }
    private static void unknown(Map<String,Object> p,Set<String> allowed,String where){for(String key:p.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown "+where+" field: "+key);}
    private static void text(Map<String,Object> p,String key){if(!(p.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("GWAS enrichment "+key+" must be nonblank text");}
    private static void choice(Map<String,Object> p,String key,Set<String> allowed){if(!allowed.contains(String.valueOf(p.get(key))))throw new IllegalArgumentException("Unsupported GWAS enrichment "+key);}
    private static void integer(Map<String,Object> p,String key,long lo,long hi){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.longValue()||n.longValue()<lo||n.longValue()>hi)throw new IllegalArgumentException("GWAS enrichment "+key+" must be an integer in "+lo+".."+hi);}
    private static void number(Map<String,Object> p,String key,double lo,double hi){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<lo||n.doubleValue()>hi)throw new IllegalArgumentException("Invalid GWAS enrichment "+key);}
}
