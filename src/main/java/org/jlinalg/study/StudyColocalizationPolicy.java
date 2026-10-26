/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registered inference and reporting controls; public-source selection is a separate contract. */
public final class StudyColocalizationPolicy {
    private StudyColocalizationPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> raw) {
        var p=new LinkedHashMap<String,Object>(raw);
        Set<String> allowed=Set.of("trait1_prior","trait2_prior","shared_prior","shared_prior_sensitivity","minimum_component_overlap","posterior_threshold","maximum_regional_variants","coverage_bins","maximum_effects","maximum_iterations","tolerance","prior_variance","credible_set_coverage","minimum_purity","max_features","rationale");
        for(String key:p.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown colocalization field: "+key);
        p.putIfAbsent("trait1_prior",1e-4);p.putIfAbsent("trait2_prior",1e-4);p.putIfAbsent("shared_prior",5e-6);p.putIfAbsent("shared_prior_sensitivity",List.of(1e-6));
        p.putIfAbsent("minimum_component_overlap",.5);p.putIfAbsent("maximum_regional_variants",1000000);p.putIfAbsent("coverage_bins",50);
        p.putIfAbsent("maximum_effects",10);p.putIfAbsent("maximum_iterations",2000);p.putIfAbsent("tolerance",1e-6);p.putIfAbsent("prior_variance",.2);p.putIfAbsent("credible_set_coverage",.95);p.putIfAbsent("minimum_purity",.5);
        for(String key:List.of("trait1_prior","trait2_prior","shared_prior","credible_set_coverage"))openProbability(p,key);
        double maximumShared=Math.min(number(p,"trait1_prior",0,1),number(p,"trait2_prior",0,1));
        if(number(p,"shared_prior",0,1)>maximumShared)throw new IllegalArgumentException("shared_prior cannot exceed either trait prior");
        if(!(p.get("shared_prior_sensitivity") instanceof List<?> values)||values.isEmpty()||values.size()>20)throw new IllegalArgumentException("shared_prior_sensitivity needs 1..20 distinct priors");
        var unique=new LinkedHashSet<Double>();var grid=new ArrayList<Double>();
        for(Object value:values){if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>maximumShared||n.doubleValue()==((Number)p.get("shared_prior")).doubleValue()||!unique.add(n.doubleValue()))throw new IllegalArgumentException("Sensitivity priors must be positive, distinct from the primary and each other, and no larger than either trait prior");grid.add(n.doubleValue());}
        p.put("shared_prior_sensitivity",List.copyOf(grid));
        number(p,"minimum_component_overlap",0,1);number(p,"minimum_purity",0,1);
        if(p.containsKey("posterior_threshold")){double v=number(p,"posterior_threshold",0,1);if(v==0)throw new IllegalArgumentException("posterior_threshold must exceed zero");}
        integer(p,"maximum_regional_variants",2,5000000);integer(p,"coverage_bins",1,1000);integer(p,"maximum_effects",1,100);integer(p,"maximum_iterations",1,100000);
        if(number(p,"tolerance",0,1)==0||number(p,"prior_variance",0,1000)==0)throw new IllegalArgumentException("tolerance and prior_variance must exceed zero");
        if(p.containsKey("max_features"))integer(p,"max_features",1,10000);
        if(p.containsKey("rationale")&&(!(p.get("rationale") instanceof String s)||s.isBlank()))throw new IllegalArgumentException("colocalization.rationale must be nonblank");
        return p;
    }
    private static void openProbability(Map<String,Object> p,String key){double v=number(p,key,0,1);if(v==0||v==1)throw new IllegalArgumentException(key+" must be in (0,1)");}
    private static void integer(Map<String,Object> p,String key,int min,int max){double v=number(p,key,min,max);if(v!=Math.rint(v))throw new IllegalArgumentException(key+" must be an integer");}
    private static double number(Map<String,Object> p,String key,double min,double max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IllegalArgumentException(key+" outside ["+min+","+max+"]");return n.doubleValue();}
}
