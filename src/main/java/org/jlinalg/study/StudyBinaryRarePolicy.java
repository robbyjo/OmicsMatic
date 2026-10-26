/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Fixed-null conditional binary set protocol; separate from Gaussian score summaries. */
public final class StudyBinaryRarePolicy {
    private StudyBinaryRarePolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input,Map<String,Object> association){
        var p=new LinkedHashMap<String,Object>(input);
        Set<String> allowed=Set.of("method","mode","maximum_exact_states","pilot_draws","draws","maximum_work","maximum_total_work","maximum_cells","strata_column","offset_column","fixed_effects","known_covariate_coefficients","known_effects_provenance","variant_coverage");
        for(String key:p.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown rare_variants.binary field: "+key);
        p.putIfAbsent("method","conditional");p.putIfAbsent("mode","auto");p.putIfAbsent("maximum_exact_states",100000);p.putIfAbsent("pilot_draws",10000);p.putIfAbsent("draws",100000);p.putIfAbsent("maximum_work",1000000000L);p.putIfAbsent("maximum_total_work",10000000000L);p.putIfAbsent("maximum_cells",20000000);p.putIfAbsent("variant_coverage","require_complete");p.putIfAbsent("fixed_effects",false);p.putIfAbsent("known_covariate_coefficients",Map.of());
        if(association.containsKey("exact_strata_column"))p.putIfAbsent("strata_column",association.get("exact_strata_column"));if(association.containsKey("offset_column"))p.putIfAbsent("offset_column",association.get("offset_column"));
        if(!p.get("method").equals("conditional")||!Set.of("auto","exact","monte_carlo").contains(p.get("mode"))||!Set.of("require_complete","intersection").contains(p.get("variant_coverage")))throw new IllegalArgumentException("Unsupported conditional binary rare-set method/mode/coverage");
        integer(p,"maximum_exact_states",1,1000000);integer(p,"pilot_draws",10,1000000);integer(p,"draws",10,10000000);integer(p,"maximum_work",1,9007199254740991L);integer(p,"maximum_total_work",1,9007199254740991L);integer(p,"maximum_cells",1,2000000000L);
        if(!(p.get("fixed_effects") instanceof Boolean))throw new IllegalArgumentException("rare_variants.binary.fixed_effects must be boolean");
        for(String field:Set.of("strata_column","offset_column"))if(p.containsKey(field)&&(!(p.get(field) instanceof String s)||!s.matches("[A-Za-z_][A-Za-z0-9_.]{0,199}")))throw new IllegalArgumentException("Binary rare-set columns must be bounded identifiers");
        var coefficients=StudyIO.map(p.get("known_covariate_coefficients"),"known_covariate_coefficients");for(var e:coefficients.entrySet())if(!e.getKey().matches("[A-Za-z_][A-Za-z0-9_.]{0,199}")||!(e.getValue() instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Known binary coefficients must map declared covariates to finite log-odds slopes");
        if(p.containsKey("known_effects_provenance"))StudyIO.map(p.get("known_effects_provenance"),"known_effects_provenance");return p;
    }
    private static void integer(Map<String,Object> p,String key,long min,long max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.longValue()||n.longValue()<min||n.longValue()>max)throw new IllegalArgumentException("rare_variants.binary."+key+" must be an integer in "+min+".."+max);}
}
