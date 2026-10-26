/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit baseline-covariate, independent-participant Cox study contract. */
public final class StudySurvivalPolicy {
    private StudySurvivalPolicy() { }
    public static final List<String> REQUIRED = List.of("time_column", "event_column", "time_units", "event_definition",
        "censoring_definition", "rationale", "independent_censoring_rationale", "proportional_hazards_rationale");
    public static Map<String,Object> resolve(Map<String,Object> configured) {
        var p = new LinkedHashMap<>(configured);
        if (!Set.of("time_column", "event_column", "entry_column", "strata_column", "time_units", "event_definition", "censoring_definition",
                "rationale", "independent_censoring_rationale", "proportional_hazards_rationale", "event_value", "censor_value", "ties",
                "test", "maximum_iterations", "relative_tolerance", "score_tolerance", "maximum_step_halvings", "confidence_level",
                "minimum_events", "maximum_features", "maximum_fit_work", "maximum_feature_missing", "ph_adjustment", "ph_alpha",
                "maximum_likelihood_pair_visits","maximum_likelihood_work","maximum_likelihood_iterations","maximum_likelihood_dimension","maximum_likelihood_certificate_bits","likelihood_tolerance").containsAll(p.keySet()))
            throw new IllegalArgumentException("Unknown association.survival setting");
        for (String key : REQUIRED) if (p.containsKey(key)) text(p.get(key),key);
        for (String key : List.of("time_column","event_column","entry_column","strata_column")) if (p.containsKey(key)) {
            text(p.get(key),key);
            if (!p.get(key).toString().matches("[A-Za-z_][A-Za-z0-9_.]{0,199}")) throw new IllegalArgumentException("survival." + key + " must be a simple column identifier");
        }
        var names = new java.util.HashSet<String>();
        for (String key : List.of("time_column","event_column","entry_column","strata_column")) if (p.containsKey(key) && !names.add(p.get(key).toString()))
            throw new IllegalArgumentException("Survival time, event, entry and strata columns must be distinct");
        p.putIfAbsent("event_value",1); p.putIfAbsent("censor_value",0);
        for (String key : List.of("event_value","censor_value")) {
            Object v=p.get(key);
            if (v instanceof Number n) { if (!Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Finite survival event codes required"); }
            else text(v,key);
            if (Set.of("NA","N/A","NAN",".","NULL","INF","INFINITY","-INF","-INFINITY").contains(v.toString().toUpperCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Survival event codes cannot be missing-value labels");
        }
        if (sameCode(p.get("event_value").toString(),p.get("censor_value"))) throw new IllegalArgumentException("Survival event and censor codes must differ");
        p.putIfAbsent("ties","efron");p.putIfAbsent("test","likelihood_ratio");
        if (!Set.of("efron","breslow").contains(p.get("ties"))) throw new IllegalArgumentException("survival.ties must be efron or breslow");
        if (!Set.of("likelihood_ratio","wald").contains(p.get("test"))) throw new IllegalArgumentException("survival.test must be likelihood_ratio or wald");
        p.putIfAbsent("maximum_iterations",50);p.putIfAbsent("relative_tolerance",1e-9);p.putIfAbsent("score_tolerance",1e-7);
        p.putIfAbsent("maximum_step_halvings",20);p.putIfAbsent("confidence_level",.95);p.putIfAbsent("minimum_events",1);
        p.putIfAbsent("maximum_features",1000000);p.putIfAbsent("maximum_fit_work",10000000000L);p.putIfAbsent("maximum_feature_missing",.02);
        p.putIfAbsent("ph_adjustment","BY");p.putIfAbsent("ph_alpha",.05);
        p.putIfAbsent("maximum_likelihood_pair_visits",100000000L);p.putIfAbsent("maximum_likelihood_work",1000000000L);
        p.putIfAbsent("maximum_likelihood_iterations",1000);p.putIfAbsent("maximum_likelihood_dimension",64);
        p.putIfAbsent("maximum_likelihood_certificate_bits",16384);p.putIfAbsent("likelihood_tolerance",1e-10);
        if (!Set.of("BH","BY","BONFERRONI","NONE").contains(p.get("ph_adjustment"))) throw new IllegalArgumentException("Unsupported survival PH adjustment");
        for (String key:List.of("relative_tolerance","score_tolerance")) number(p,key,Double.MIN_NORMAL,.01);
        number(p,"confidence_level",Double.MIN_NORMAL,Math.nextDown(1.));number(p,"maximum_feature_missing",0,1);number(p,"ph_alpha",Double.MIN_NORMAL,1);
        integer(p,"maximum_iterations",1,100000);integer(p,"maximum_step_halvings",0,1000);integer(p,"minimum_events",1,Integer.MAX_VALUE);
        integer(p,"maximum_features",1,10000000);integer(p,"maximum_fit_work",1,9007199254740991L);
        integer(p,"maximum_likelihood_pair_visits",1,9007199254740991L);integer(p,"maximum_likelihood_work",1,9007199254740991L);
        integer(p,"maximum_likelihood_iterations",1,100000);integer(p,"maximum_likelihood_dimension",1,256);integer(p,"maximum_likelihood_certificate_bits",256,65536);
        number(p,"likelihood_tolerance",1e-14,1e-6);
        return p;
    }
    public static boolean sameCode(String observed,Object expected) {
        try { double a=Double.parseDouble(observed),b=Double.parseDouble(expected.toString());return Double.isFinite(a)&&Double.isFinite(b)&&a==b; }
        catch(NumberFormatException error){return observed.equals(expected.toString());}
    }
    public static String missing(Map<String,Object> p) { return String.join(", ", REQUIRED.stream().filter(key->!p.containsKey(key)).toList()); }
    private static void text(Object value,String key) { if (!(value instanceof String s)||s.isBlank()||s.length()>4000||s.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("survival."+key+" must be bounded nonblank text"); }
    private static double number(Map<String,Object> p,String key,double low,double high){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<low||n.doubleValue()>high)throw new IllegalArgumentException("survival."+key+" is outside its supported range");return n.doubleValue();}
    private static void integer(Map<String,Object> p,String key,long low,long high){double n=number(p,key,low,high);if(n!=Math.rint(n))throw new IllegalArgumentException("survival."+key+" must be an integer");}
}
