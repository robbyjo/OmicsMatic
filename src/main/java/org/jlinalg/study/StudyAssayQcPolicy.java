/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Matrix-source measurement QC, separate from phenotype selection and fitted preprocessing. */
public final class StudyAssayQcPolicy {
    public static final Set<String> MODALITIES=StudyNetworkPolicy.MOLECULAR;
    private static final Set<String> FIELDS=Set.of("enabled","source","mode","value_scale","invalid_values","maximum_feature_missing","maximum_sample_missing","minimum_observed","minimum_sd","drop_constant","detection_kind","detection_threshold","maximum_features","maximum_samples","maximum_cells","maximum_source_mib","maximum_retained_mib","maximum_record_mib","supplied_report","supplied_samples","supplied_features","provenance","dataset_overrides");
    private StudyAssayQcPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input){
        var p=base(input);var overrides=new LinkedHashMap<String,Object>();
        for(var entry:StudyIO.optionalMap(input.get("dataset_overrides"),"assay_qc.dataset_overrides").entrySet()){
            if(entry.getKey().isBlank())throw new IllegalArgumentException("Assay QC overrides require nonblank dataset IDs");var value=StudyIO.map(entry.getValue(),"assay QC override");if(value.containsKey("dataset_overrides"))throw new IllegalArgumentException("Nested assay QC dataset overrides are not allowed");
            var merged=new LinkedHashMap<String,Object>(p);merged.putAll(value);merged.remove("dataset_overrides");overrides.put(entry.getKey(),base(merged));
        }
        p.put("dataset_overrides",overrides);return p;
    }
    public static Map<String,Object> forDataset(Map<String,Object> resolved,String dataset){var p=new LinkedHashMap<String,Object>(resolved);var overrides=StudyIO.optionalMap(p.remove("dataset_overrides"),"assay QC overrides");return overrides.containsKey(dataset)?new LinkedHashMap<>(StudyIO.map(overrides.get(dataset),"assay QC override")):p;}
    public static String fingerprint(Map<String,Object> policy){return StudyIO.hash(policy);}
    private static Map<String,Object> base(Map<String,Object> input){
        var p=new LinkedHashMap<String,Object>(input);if(!FIELDS.containsAll(p.keySet()))throw new IllegalArgumentException("Unknown assay_qc field");p.remove("dataset_overrides");
        p.putIfAbsent("enabled",true);p.putIfAbsent("source","native");p.putIfAbsent("mode","audit");p.putIfAbsent("value_scale","unspecified");p.putIfAbsent("invalid_values","hold");p.putIfAbsent("maximum_feature_missing",.2);p.putIfAbsent("maximum_sample_missing",.2);p.putIfAbsent("minimum_observed",3);p.putIfAbsent("minimum_sd",0.0);p.putIfAbsent("drop_constant",true);p.putIfAbsent("detection_kind","none");p.putIfAbsent("detection_threshold",.01);p.putIfAbsent("maximum_features",2000000);p.putIfAbsent("maximum_samples",1000000);p.putIfAbsent("maximum_cells",1000000000L);p.putIfAbsent("maximum_source_mib",65536);p.putIfAbsent("maximum_retained_mib",512);p.putIfAbsent("maximum_record_mib",64);
        for(String key:Set.of("enabled","drop_constant"))if(!(p.get(key) instanceof Boolean))throw new IllegalArgumentException("assay_qc."+key+" must be boolean");
        choice(p,"source",Set.of("native","supplied"));choice(p,"mode",Set.of("audit","filter"));choice(p,"value_scale",Set.of("unspecified","continuous","nonnegative","counts","proportion"));choice(p,"invalid_values",Set.of("hold","mask"));choice(p,"detection_kind",Set.of("none","p_value","detected"));
        for(String key:Set.of("maximum_feature_missing","maximum_sample_missing","detection_threshold")){double v=number(p,key);if(v<0||v>1)throw new IllegalArgumentException("assay_qc."+key+" must be in [0,1]");}
        if(number(p,"minimum_sd")<0)throw new IllegalArgumentException("Assay minimum_sd must be nonnegative");
        integer(p,"minimum_observed",1,1000000);integer(p,"maximum_features",1,2000000);integer(p,"maximum_samples",1,1000000);integer(p,"maximum_cells",1,50000000000000L);integer(p,"maximum_source_mib",1,1048576);integer(p,"maximum_retained_mib",1,8192);integer(p,"maximum_record_mib",1,1024);
        for(String key:Set.of("supplied_report","supplied_samples","supplied_features"))if(p.containsKey(key)&&(!(p.get(key) instanceof String s)||s.isBlank()))throw new IllegalArgumentException("assay_qc."+key+" must be nonblank text");
        if(p.get("invalid_values").equals("mask")&&!p.get("mode").equals("filter"))throw new IllegalArgumentException("Assay invalid_values: mask requires explicit mode: filter");
        if(p.get("source").equals("supplied")){for(String key:Set.of("supplied_report","supplied_samples","supplied_features"))if(!p.containsKey(key))throw new IllegalArgumentException("Supplied assay QC requires report, sample and feature decision files");if(!p.get("detection_kind").equals("none"))throw new IllegalArgumentException("Supplied assay QC uses its declared matrix/decisions; native detection recomputation is not performed");}
        else if(p.keySet().stream().anyMatch(k->k.startsWith("supplied_")))throw new IllegalArgumentException("Supplied assay QC files require source: supplied");
        if(p.containsKey("provenance"))StudyIO.map(p.get("provenance"),"assay QC provenance");return p;
    }
    private static double number(Map<String,Object> p,String key){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("assay_qc."+key+" must be finite numeric");return n.doubleValue();}
    private static void integer(Map<String,Object> p,String key,long min,long max){double v=number(p,key);if(v!=Math.rint(v)||v<min||v>max)throw new IllegalArgumentException("assay_qc."+key+" outside integer bounds");}
    private static void choice(Map<String,Object> p,String key,Set<String> choices){if(!choices.contains(p.get(key)))throw new IllegalArgumentException("Unsupported assay_qc."+key);}
}
