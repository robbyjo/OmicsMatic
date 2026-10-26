/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Opt-in conditional graph-label tests; descriptive preservation remains separate. */
public final class StudyNetworkNullPolicy {
    private StudyNetworkNullPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input) {
        var p=new LinkedHashMap<String,Object>(input);
        for(String key:p.keySet())if(!Set.of("enabled","statistic","method","exact_maximum","draws","seed","max_work","max_null_rows","adjustment","alpha","exchangeability_rationale","strata_file").contains(key))throw new IllegalArgumentException("Unknown network_preservation_null field: "+key);
        p.putIfAbsent("enabled",false);p.putIfAbsent("statistic","signed_edge_overlap");p.putIfAbsent("method","auto");p.putIfAbsent("exact_maximum",100000);p.putIfAbsent("draws",9999);p.putIfAbsent("seed",17);p.putIfAbsent("max_work",100000000);p.putIfAbsent("max_null_rows",100000);p.putIfAbsent("adjustment","BY");p.putIfAbsent("alpha",.05);
        if(!(p.get("enabled") instanceof Boolean))throw new IllegalArgumentException("network_preservation_null.enabled must be boolean");
        if(!Set.of("signed_edge_overlap","edge_overlap").contains(p.get("statistic")))throw new IllegalArgumentException("Unknown network null statistic");
        if(!Set.of("auto","exact","monte_carlo").contains(p.get("method")))throw new IllegalArgumentException("Unknown network null method");
        if(!Set.of("BY","BH","bonferroni","NONE").contains(p.get("adjustment")))throw new IllegalArgumentException("Unknown network null adjustment");
        integer(p,"exact_maximum",1,1000000);integer(p,"draws",1,1000000);integer(p,"seed",0,Integer.MAX_VALUE);integer(p,"max_work",1,1000000000000L);integer(p,"max_null_rows",1,1000000);
        if(!(p.get("alpha") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>=1)throw new IllegalArgumentException("Network null alpha must be in (0,1)");
        for(String field:Set.of("exchangeability_rationale","strata_file"))if(p.containsKey(field)&&(!(p.get(field) instanceof String s)||s.isBlank()||s.length()>8000||s.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("Network null "+field+" must be bounded text");
        return p;
    }
    private static void integer(Map<String,Object> p,String key,long min,long max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.longValue()||n.longValue()<min||n.longValue()>max)throw new IllegalArgumentException("network_preservation_null."+key+" outside integer bounds");}
}
