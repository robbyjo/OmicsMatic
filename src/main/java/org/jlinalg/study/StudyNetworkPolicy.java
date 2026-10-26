/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prespecified descriptive networks, bounded module search and joint module GO. */
public final class StudyNetworkPolicy {
    private StudyNetworkPolicy() { }
    public static final Set<String> MOLECULAR = Set.of("generic", "transcriptomics", "expression", "mirna", "lincrna", "lncrna", "extracellular_rna", "epigenomics", "ewas", "proteomics", "metabolomics");
    public static Map<String,Object> resolve(Map<String,Object> supplied) {
        var p = new LinkedHashMap<String,Object>(supplied);
        Set<String> fields = Set.of("enabled","correlation","threshold","thresholds","pathway_threshold","minimum_samples","max_features","max_samples","max_pair_work","bootstraps","independent_samples","seed","categorical_covariates","preprocessing","cliques","minimum_clique_size","max_cliques","max_search_states","max_clique_depth","go_min_size","go_max_size","go_min_module_genes","max_go_tests","go_adjustment","go_database","source_kind","membership_column","membership_gene_id_type","gene_sets","term_alpha");
        var allowed=new java.util.HashSet<>(fields);allowed.add("complete_missing");
        for(String key:p.keySet()) if(!allowed.contains(key)) throw new IllegalArgumentException("Unknown network policy field: " + key);
        p.putIfAbsent("complete_missing",true);
        p.putIfAbsent("enabled",true);p.putIfAbsent("correlation","spearman");p.putIfAbsent("threshold",.7);p.putIfAbsent("thresholds",List.of(.6,.7,.8));
        p.putIfAbsent("minimum_samples",10);p.putIfAbsent("max_features",2000);p.putIfAbsent("max_samples",10000);p.putIfAbsent("max_pair_work",100_000_000);
        p.putIfAbsent("bootstraps",0);p.putIfAbsent("independent_samples",false);p.putIfAbsent("seed",1);p.putIfAbsent("categorical_covariates",List.of());
        p.putIfAbsent("preprocessing","not_recorded");p.putIfAbsent("cliques",true);p.putIfAbsent("minimum_clique_size",3);p.putIfAbsent("max_cliques",10000);
        p.putIfAbsent("max_search_states",1_000_000);p.putIfAbsent("max_clique_depth",128);p.putIfAbsent("go_min_size",10);p.putIfAbsent("go_max_size",500);
        p.putIfAbsent("go_adjustment","BH");p.putIfAbsent("source_kind","ranked_gsea");p.putIfAbsent("term_alpha",.05);
        p.putIfAbsent("membership_gene_id_type","unknown");
        p.putIfAbsent("pathway_threshold",.25);
        p.putIfAbsent("go_min_module_genes",3);p.putIfAbsent("max_go_tests",1000000);
        for(String key:List.of("enabled","complete_missing","independent_samples","cliques")) if(!(p.get(key) instanceof Boolean)) throw new IllegalArgumentException("network."+key+" must be boolean");
        choice(p,"correlation",Set.of("pearson","spearman"));choice(p,"go_adjustment",Set.of("BH","BY","NONE"));choice(p,"source_kind",Set.of("ranked_gsea","gene_ontology","pathway_enrichment"));
        choice(p,"membership_gene_id_type",Set.of("symbol","ensembl_gene","entrez","unknown"));
        for(String key:List.of("threshold","pathway_threshold","term_alpha")) probability(p.get(key),key);
        var thresholds=new ArrayList<Double>();
        for(Object value:StudyIO.list(p.get("thresholds"),"network.thresholds")) { probability(value,"thresholds");double v=((Number)value).doubleValue();if(!thresholds.contains(v))thresholds.add(v); }
        if(thresholds.isEmpty()||thresholds.size()>20)throw new IllegalArgumentException("network.thresholds requires 1..20 prespecified values");p.put("thresholds",thresholds);
        integer(p,"minimum_samples",4,1000000);integer(p,"max_features",2,2000);integer(p,"max_samples",4,1000000);integer(p,"max_pair_work",1,2_000_000_000);
        integer(p,"bootstraps",0,1000);integer(p,"seed",0,Integer.MAX_VALUE);integer(p,"minimum_clique_size",2,128);integer(p,"max_cliques",1,100000);
        integer(p,"max_search_states",1,10000000);integer(p,"max_clique_depth",2,128);integer(p,"go_min_size",1,100000);integer(p,"go_max_size",1,100000);
        integer(p,"go_min_module_genes",1,100000);integer(p,"max_go_tests",1,1000000);
        if(((Number)p.get("go_min_size")).intValue()>((Number)p.get("go_max_size")).intValue())throw new IllegalArgumentException("network GO size bounds conflict");
        for(Object value:StudyIO.list(p.get("categorical_covariates"),"categorical_covariates")) if(!(value instanceof String s)||s.isBlank())throw new IllegalArgumentException("network categorical covariates must be names");
        for(String key:List.of("preprocessing","go_database","gene_sets","membership_column")) if(p.containsKey(key)&&(!(p.get(key) instanceof String s)||s.isBlank())) throw new IllegalArgumentException("network."+key+" must be nonblank text");
        if(p.containsKey("gene_sets")&&p.containsKey("membership_column"))throw new IllegalArgumentException("Choose network.gene_sets or membership_column explicitly, not both");
        return p;
    }
    private static void choice(Map<String,Object> p,String key,Set<String> values) { if(!values.contains(String.valueOf(p.get(key))))throw new IllegalArgumentException("Unsupported network."+key); }
    private static void probability(Object value,String key) { if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>1)throw new IllegalArgumentException("network."+key+" must be in (0,1]"); }
    private static void integer(Map<String,Object> p,String key,int min,int max) { if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw new IllegalArgumentException("network."+key+" is outside its integer bounds"); }
}
