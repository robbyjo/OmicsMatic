/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete ranked universes with prespecified gene-set or model-matched phenotype nulls. */
public final class StudyGseaPolicy {
    private StudyGseaPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input) {
        var p=new LinkedHashMap<String,Object>(input);
        Set<String> keys=Set.of("enabled","gene_sets","collection","collection_provenance","species","gene_id_type","ranking","statistic","statistic_column","collapse","tie_policy","weight_exponent","minimum_size","maximum_size","permutations","maximum_exact_states","seed","maximum_work","max_genes","max_sets","curve_plots","adjustment","null_model","rank_interpretation","phenotype","null_rankings");
        for(String key:p.keySet())if(!keys.contains(key))throw new IllegalArgumentException("Unknown GSEA policy field: "+key);
        p.putIfAbsent("enabled",true);p.putIfAbsent("collection",p.containsKey("gene_sets")?"GMT":"GO");p.putIfAbsent("gene_id_type","symbol");p.putIfAbsent("statistic","beta_se");p.putIfAbsent("statistic_column","statistic");
        p.putIfAbsent("collapse","require_unique");p.putIfAbsent("tie_policy","error");p.putIfAbsent("weight_exponent",1.0);p.putIfAbsent("minimum_size",15);p.putIfAbsent("maximum_size",500);
        p.putIfAbsent("permutations",10000);p.putIfAbsent("maximum_exact_states",100000);p.putIfAbsent("seed",1);p.putIfAbsent("maximum_work",250000000);p.putIfAbsent("max_genes",50000);p.putIfAbsent("max_sets",20000);
        p.putIfAbsent("adjustment","BH");p.putIfAbsent("null_model","gene_set");
        p.putIfAbsent("curve_plots",5);
        if(!(p.get("enabled") instanceof Boolean))throw new IllegalArgumentException("gsea.enabled must be boolean");
        choice(p,"gene_id_type",Set.of("symbol","ensembl_gene","entrez"));choice(p,"statistic",Set.of("beta_se","column"));choice(p,"collapse",Set.of("require_unique","mean","median"));choice(p,"tie_policy",Set.of("error","gene_id"));choice(p,"adjustment",Set.of("BH","BY","NONE"));choice(p,"null_model",Set.of("gene_set","phenotype"));
        choice(p,"collection",Set.of("GO","Reactome","GMT"));
        if(p.containsKey("collection_provenance"))StudyIO.map(p.get("collection_provenance"),"collection_provenance");
        for(String key:List.of("gene_sets","species","statistic_column","rank_interpretation"))if(p.containsKey(key)&&(!(p.get(key) instanceof String s)||s.isBlank()))throw new IllegalArgumentException("gsea."+key+" must be nonblank text");
        if(!(p.get("weight_exponent") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>2)throw new IllegalArgumentException("gsea.weight_exponent must be in [0,2]");
        integer(p,"minimum_size",1,50000);integer(p,"maximum_size",1,50000);integer(p,"permutations",1,1000000);integer(p,"maximum_exact_states",0,1000000);integer(p,"seed",0,Integer.MAX_VALUE);integer(p,"maximum_work",1,2000000000);integer(p,"max_genes",2,100000);integer(p,"max_sets",1,100000);
        integer(p,"curve_plots",0,50);
        if(((Number)p.get("minimum_size")).intValue()>((Number)p.get("maximum_size")).intValue())throw new IllegalArgumentException("GSEA size bounds conflict");
        if(p.containsKey("ranking")) {
            var ranking=StudyIO.map(p.get("ranking"),"gsea.ranking");
            for(String key:ranking.keySet())if(!Set.of("file","gene_column","statistic_column","coverage","tested_genes","provenance").contains(key))throw new IllegalArgumentException("Unknown GSEA ranking field: "+key);
            if(!(ranking.get("file") instanceof String s)||s.isBlank())throw new IllegalArgumentException("gsea.ranking.file is required");
            ranking.putIfAbsent("gene_column","gene_id");ranking.putIfAbsent("statistic_column","statistic");ranking.putIfAbsent("coverage","unknown");
            choice(ranking,"coverage",Set.of("complete","selected_only","unknown"));
            for(String key:List.of("gene_column","statistic_column"))if(!(ranking.get(key) instanceof String columnName)||columnName.isBlank())throw new IllegalArgumentException("GSEA ranking columns must be names");
            if(ranking.containsKey("tested_genes"))integer(ranking,"tested_genes",2,100000);
            if(ranking.containsKey("provenance"))StudyIO.map(ranking.get("provenance"),"ranking.provenance");
            p.put("ranking",ranking);
        }
        if(p.containsKey("phenotype")){
            if(!"phenotype".equals(p.get("null_model"))||p.containsKey("ranking")||p.containsKey("null_rankings"))throw new IllegalArgumentException("Native gsea.phenotype requires null_model: phenotype and cannot be combined with external ranking/null_rankings");
            var nativePolicy=StudyIO.map(p.get("phenotype"),"gsea.phenotype");
            for(String key:nativePolicy.keySet())if(!Set.of("independent_samples","exchangeable_errors","strata_column","minimum_samples","maximum_samples","maximum_features","maximum_cells","maximum_design_columns","rationale").contains(key))throw new IllegalArgumentException("Unknown GSEA phenotype field: "+key);
            for(String key:List.of("independent_samples","exchangeable_errors"))if(nativePolicy.containsKey(key)&&!(nativePolicy.get(key) instanceof Boolean))throw new IllegalArgumentException("gsea.phenotype."+key+" must be boolean");
            nativePolicy.putIfAbsent("minimum_samples",10);nativePolicy.putIfAbsent("maximum_samples",10000);nativePolicy.putIfAbsent("maximum_features",50000);nativePolicy.putIfAbsent("maximum_cells",20000000);nativePolicy.putIfAbsent("maximum_design_columns",100);
            integer(nativePolicy,"minimum_samples",4,100000);integer(nativePolicy,"maximum_samples",4,100000);integer(nativePolicy,"maximum_features",2,100000);integer(nativePolicy,"maximum_cells",8,100000000);integer(nativePolicy,"maximum_design_columns",1,1000);
            if(((Number)nativePolicy.get("minimum_samples")).intValue()>((Number)nativePolicy.get("maximum_samples")).intValue())throw new IllegalArgumentException("GSEA phenotype sample bounds conflict");
            for(String key:List.of("strata_column","rationale"))if(nativePolicy.containsKey(key)&&(!(nativePolicy.get(key) instanceof String s)||s.isBlank()))throw new IllegalArgumentException("gsea.phenotype."+key+" must be nonblank text");
            p.put("phenotype",nativePolicy);
        }
        if(p.containsKey("null_rankings")){
            if(!"phenotype".equals(p.get("null_model"))||!p.containsKey("ranking"))throw new IllegalArgumentException("gsea.null_rankings requires phenotype null and a complete explicit observed ranking");
            var ensemble=StudyIO.map(p.get("null_rankings"),"gsea.null_rankings");
            for(String key:ensemble.keySet())if(!Set.of("file","draws","sampling","coverage","observed_sha256","method","provenance","model","exchangeability").contains(key))throw new IllegalArgumentException("Unknown GSEA null_rankings field: "+key);
            for(String key:List.of("file","method","model","exchangeability"))if(!(ensemble.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("gsea.null_rankings."+key+" is required nonblank text");
            if(!(ensemble.get("observed_sha256") instanceof String s)||!s.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("gsea.null_rankings.observed_sha256 must bind the complete observed ranking file");
            integer(ensemble,"draws",1,1000000);choice(ensemble,"sampling",Set.of("monte_carlo","exhaustive"));choice(ensemble,"coverage",Set.of("complete"));
            if(StudyIO.map(ensemble.get("provenance"),"null_rankings.provenance").isEmpty())throw new IllegalArgumentException("Null-ranking source provenance is required");
            p.put("null_rankings",ensemble);
        }
        return p;
    }
    private static void choice(Map<String,Object> p,String key,Set<String> allowed){if(!allowed.contains(String.valueOf(p.get(key))))throw new IllegalArgumentException("Unsupported GSEA "+key);}
    private static void integer(Map<String,Object> p,String key,int min,int max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw new IllegalArgumentException("gsea."+key+" must be an integer within "+min+".."+max);}
}
