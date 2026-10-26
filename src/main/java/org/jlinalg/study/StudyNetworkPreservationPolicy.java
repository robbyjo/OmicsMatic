/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registered source-graph comparisons; no preservation P values are inferred. */
public final class StudyNetworkPreservationPolicy {
    private StudyNetworkPreservationPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input) {
        var p=new LinkedHashMap<String,Object>(input);
        var allowed=Set.of("enabled","references","graph_namespaces","minimum_common_nodes","minimum_node_coverage","max_graphs","max_nodes","max_edges","max_memberships","max_modules","max_comparisons","max_pair_work");
        for(String key:p.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown network_preservation field: "+key);
        p.putIfAbsent("enabled",true);p.putIfAbsent("references",List.of());p.putIfAbsent("graph_namespaces",Map.of());
        p.putIfAbsent("minimum_common_nodes",3);p.putIfAbsent("minimum_node_coverage",.8);
        p.putIfAbsent("max_graphs",50);p.putIfAbsent("max_nodes",2000);p.putIfAbsent("max_edges",1000000);p.putIfAbsent("max_memberships",200000);
        p.putIfAbsent("max_modules",10000);p.putIfAbsent("max_comparisons",100000);p.putIfAbsent("max_pair_work",100000000);
        if(!(p.get("enabled") instanceof Boolean))throw new IllegalArgumentException("network_preservation.enabled must be boolean");
        var references=StudyIO.list(p.get("references"),"network_preservation.references");
        if(references.size()>50||new java.util.HashSet<>(references).size()!=references.size())throw new IllegalArgumentException("Preservation references must be unique and bounded");
        for(Object alias:references)alias(alias);
        var namespaces=StudyIO.map(p.get("graph_namespaces"),"network_preservation.graph_namespaces");
        if(namespaces.size()>100)throw new IllegalArgumentException("Too many graph namespace declarations");
        for(var entry:namespaces.entrySet()){alias(entry.getKey());if(!(entry.getValue() instanceof String s)||s.isBlank()||s.length()>200||s.chars().anyMatch(Character::isISOControl)||Set.of("unknown","unspecified","auto","unavailable","na","n/a","nan","null","none",".").contains(s.strip().toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Graph namespaces must be explicit bounded scientific labels, without unknown-value placeholders");}
        integer(p,"minimum_common_nodes",2,100000);integer(p,"max_graphs",2,100);integer(p,"max_nodes",2,100000);integer(p,"max_edges",1,1000000);integer(p,"max_memberships",1,1000000);
        integer(p,"max_modules",1,100000);integer(p,"max_comparisons",1,1000000);integer(p,"max_pair_work",1,1000000000);
        if(!(p.get("minimum_node_coverage") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>1)throw new IllegalArgumentException("minimum_node_coverage must be in (0,1]");
        return p;
    }
    private static void alias(Object value){if(!(value instanceof String s)||!s.matches("(dataset|supplied):[A-Za-z][A-Za-z0-9_.-]*"))throw new IllegalArgumentException("Graph aliases must be dataset:ID or supplied:ID");}
    private static void integer(Map<String,Object> p,String key,int min,int max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw new IllegalArgumentException("network_preservation."+key+" is outside its integer bounds");}
}
