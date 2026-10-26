/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Declared scope and display choices for descriptive GWAS result diagnostics. */
public final class StudyGwasDiagnosticPolicy {
    private StudyGwasDiagnosticPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> configured) {
        var p=new LinkedHashMap<>(configured);
        var fields=Set.of("enabled","minimum_lambda_tests","maximum_rows","maximum_plot_points","maximum_strata","plot_y_limit","dataset_coverage","maf_breaks","mac_breaks","info_breaks");
        for(String key:p.keySet())if(!fields.contains(key))throw new IllegalArgumentException("Unknown gwas_diagnostics field: "+key);
        p.putIfAbsent("enabled",true);p.putIfAbsent("minimum_lambda_tests",20);p.putIfAbsent("maximum_rows",2000000);
        p.putIfAbsent("maximum_plot_points",100000);p.putIfAbsent("maximum_strata",200);p.putIfAbsent("plot_y_limit",1000.0);
        p.putIfAbsent("dataset_coverage",Map.of());p.putIfAbsent("maf_breaks",List.of(.01,.05));p.putIfAbsent("mac_breaks",List.of(20,100));p.putIfAbsent("info_breaks",List.of(.8,.9));
        if(!(p.get("enabled") instanceof Boolean))throw new IllegalArgumentException("gwas_diagnostics.enabled must be boolean");
        integer(p,"minimum_lambda_tests",2,10000000);integer(p,"maximum_rows",2,10000000);
        integer(p,"maximum_plot_points",2,1000000);integer(p,"maximum_strata",1,10000);
        if(!(p.get("plot_y_limit") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<1||n.doubleValue()>1000000)throw new IllegalArgumentException("plot_y_limit must be finite and within 1..1000000");
        var coverage=StudyIO.map(p.get("dataset_coverage"),"dataset_coverage");
        for(var e:coverage.entrySet())if(e.getKey().isBlank()||!Set.of("complete","selected_only","unknown").contains(e.getValue()))throw new IllegalArgumentException("dataset_coverage must name complete, selected_only or unknown exports");
        for(String key:List.of("maf_breaks","mac_breaks","info_breaks")) {
            var values=StudyIO.list(p.get(key),key);if(values.size()>20)throw new IllegalArgumentException("At most 20 prespecified diagnostic breaks per field");
            double previous=-1;
            for(Object raw:values){if(!(raw instanceof Number value)||!Double.isFinite(value.doubleValue())||value.doubleValue()<=previous||value.doubleValue()<0||key.equals("maf_breaks")&&value.doubleValue()>.5||key.equals("info_breaks")&&value.doubleValue()>1)throw new IllegalArgumentException("Diagnostic breaks must be finite, nonnegative and strictly increasing within their field range");previous=((Number)raw).doubleValue();}
        }
        return p;
    }
    private static void integer(Map<String,Object> p,String key,int low,int high){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<low||n.doubleValue()>high)throw new IllegalArgumentException("gwas_diagnostics."+key+" must be an integer within "+low+".."+high);}
}
