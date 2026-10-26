/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registered overlap and heterogeneity diagnostics; neither selects a new primary analysis. */
public final class StudyCohortPolicy {
    private StudyCohortPolicy() { }
    public static Map<String,Object> overlap(Map<String,Object> raw) {
        var p=new LinkedHashMap<String,Object>(raw);
        unknown(p,Set.of("enabled","universe","validation_family","direction","alpha","threshold_comparison","maximum_sets","maximum_features","maximum_cells","plot_patterns","zero_overlap_alert","minimum_shared_tested","minimum_selected_features"),"cohort_overlap");
        p.putIfAbsent("enabled",true);p.putIfAbsent("universe","intersection");p.putIfAbsent("validation_family","analysis");p.putIfAbsent("direction","report");
        p.putIfAbsent("threshold_comparison","analysis");p.putIfAbsent("maximum_sets",64);p.putIfAbsent("maximum_features",1000000);p.putIfAbsent("maximum_cells",5000000);p.putIfAbsent("plot_patterns",40);
        p.putIfAbsent("zero_overlap_alert",true);p.putIfAbsent("minimum_shared_tested",1);p.putIfAbsent("minimum_selected_features",1);
        booleans(p,"enabled","zero_overlap_alert");choice(p,"universe","intersection","union");choice(p,"validation_family","analysis","all_results","discovery_subset");choice(p,"direction","report","require_discovery");choice(p,"threshold_comparison","analysis","less_than","less_than_or_equal");
        if(p.containsKey("alpha"))probability(p,"alpha",false);
        integer(p,"maximum_sets",2,128);integer(p,"maximum_features",1,5000000);integer(p,"maximum_cells",1,10000000);integer(p,"plot_patterns",1,100);integer(p,"minimum_shared_tested",1,5000000);integer(p,"minimum_selected_features",1,5000000);
        return p;
    }
    public static Map<String,Object> heterogeneity(Map<String,Object> raw) {
        var p=new LinkedHashMap<String,Object>(raw);
        unknown(p,Set.of("enabled","metric","i_squared_threshold","q_alpha","q_adjustment","feature_scope","minimum_sources","minimum_features","minimum_high_features","widespread_fraction","fraction_comparison","association_alpha","minimum_retained_cohorts","minimum_common_features","comparison_universe","leave_one_cohort_out","rationale"),"heterogeneity");
        p.putIfAbsent("enabled",true);p.putIfAbsent("metric","auto");p.putIfAbsent("i_squared_threshold",50.0);p.putIfAbsent("q_alpha",.05);p.putIfAbsent("q_adjustment","BH");p.putIfAbsent("feature_scope","all_estimable");
        p.putIfAbsent("minimum_sources",2);p.putIfAbsent("minimum_features",10);p.putIfAbsent("minimum_high_features",1);p.putIfAbsent("widespread_fraction",.10);p.putIfAbsent("fraction_comparison","greater_than_or_equal");
        p.putIfAbsent("minimum_retained_cohorts",2);p.putIfAbsent("minimum_common_features",1);p.putIfAbsent("comparison_universe","all_sensitivities");p.putIfAbsent("leave_one_cohort_out","always");
        booleans(p,"enabled");choice(p,"metric","auto","i_squared","q_p_value");choice(p,"q_adjustment","BH","BY","NONE");choice(p,"feature_scope","all_estimable","primary_significant");choice(p,"fraction_comparison","greater_than","greater_than_or_equal");choice(p,"comparison_universe","all_sensitivities","pairwise");choice(p,"leave_one_cohort_out","always","on_alert","disabled");
        number(p,"i_squared_threshold",0,100);probability(p,"q_alpha",false);probability(p,"widespread_fraction",false);if(p.containsKey("association_alpha"))probability(p,"association_alpha",false);
        integer(p,"minimum_sources",2,1000000);integer(p,"minimum_features",1,5000000);integer(p,"minimum_high_features",1,5000000);integer(p,"minimum_retained_cohorts",2,1000000);integer(p,"minimum_common_features",1,5000000);
        if(p.containsKey("rationale")&&(!(p.get("rationale") instanceof String s)||s.isBlank()))throw new IllegalArgumentException("heterogeneity.rationale must be nonblank text");
        return p;
    }
    private static void unknown(Map<String,Object> p,Set<String> fields,String name){for(String key:p.keySet())if(!fields.contains(key))throw new IllegalArgumentException("Unknown "+name+" field: "+key);}
    private static void booleans(Map<String,Object> p,String... fields){for(String key:fields)if(!(p.get(key) instanceof Boolean))throw new IllegalArgumentException(key+" must be boolean");}
    private static void choice(Map<String,Object> p,String key,String... values){if(!List.of(values).contains(p.get(key)))throw new IllegalArgumentException("Invalid "+key+": "+p.get(key));}
    private static void probability(Map<String,Object> p,String key,boolean zero){double v=number(p,key,0,1);if(!zero&&v==0)throw new IllegalArgumentException(key+" must exceed zero");}
    private static void integer(Map<String,Object> p,String key,int min,int max){double v=number(p,key,min,max);if(v!=Math.rint(v))throw new IllegalArgumentException(key+" must be an integer");}
    private static double number(Map<String,Object> p,String key,double min,double max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IllegalArgumentException(key+" outside ["+min+","+max+"]");return n.doubleValue();}
}
