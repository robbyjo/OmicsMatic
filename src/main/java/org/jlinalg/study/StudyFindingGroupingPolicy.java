/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prespecified LD evidence for manuscript redundancy, never association or causal inference. */
public final class StudyFindingGroupingPolicy {
    private StudyFindingGroupingPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> raw) {
        var p=new LinkedHashMap<>(raw);unknown(p,Set.of("enabled","minimum_r_squared","maximum_distance_bp","maximum_variants","maximum_pairs","require_complete_coverage","references"),"finding_grouping");
        p.putIfAbsent("enabled",true);p.putIfAbsent("minimum_r_squared",.8);p.putIfAbsent("maximum_distance_bp",1000000);p.putIfAbsent("maximum_variants",200000);p.putIfAbsent("maximum_pairs",2000000);p.putIfAbsent("require_complete_coverage",true);p.putIfAbsent("references",List.of());
        for(String key:List.of("enabled","require_complete_coverage"))if(!(p.get(key) instanceof Boolean))throw new IllegalArgumentException("finding_grouping."+key+" must be boolean");number(p,"minimum_r_squared",0,1,false);number(p,"maximum_distance_bp",0,2147483647,true);number(p,"maximum_variants",1,2000000,true);number(p,"maximum_pairs",1,10000000,true);
        var refs=new ArrayList<Object>();var ids=new LinkedHashSet<String>();var hypotheses=new LinkedHashSet<String>();
        for(Object value:StudyIO.optionalList(p.get("references"),"finding_grouping.references")) {
            var r=StudyIO.copyMap(StudyIO.map(value,"finding grouping reference"));unknown(r,Set.of("id","genome_build","population","release","hypotheses","variants","pairs","coverage","window_bp","minimum_reported_r_squared","reporting_comparison","rationale"),"finding_grouping reference");String id=text(r,"id",100);if(!id.matches("[A-Za-z][A-Za-z0-9_.-]*")||!ids.add(id))throw new IllegalArgumentException("Grouping reference IDs must be unique safe identifiers");String build=text(r,"genome_build",30);build=switch(build){case "GRCh38","hg38"->"GRCh38";case "GRCh37","hg19"->"GRCh37";default->throw new IllegalArgumentException("Grouping references require explicit GRCh37/GRCh38 coordinates");};r.put("genome_build",build);
            text(r,"population",200);text(r,"release",500);text(r,"variants",8000);text(r,"pairs",8000);if(r.containsKey("rationale"))text(r,"rationale",4000);
            var selected=new ArrayList<String>();for(Object h:StudyIO.list(r.get("hypotheses"),"grouping reference.hypotheses")){if(!(h instanceof String s)||!s.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}")||!hypotheses.add(s))throw new IllegalArgumentException("Each grouped hypothesis must select exactly one reference; identifiers must be safe and unique");selected.add(s);}if(selected.isEmpty())throw new IllegalArgumentException("A grouping reference needs explicit hypotheses");r.put("hypotheses",selected);
            r.putIfAbsent("coverage","positive_edges_only");if(!Set.of("complete_window","thresholded_window","positive_edges_only").contains(r.get("coverage")))throw new IllegalArgumentException("Unsupported finding LD coverage");if(r.containsKey("window_bp"))number(r,"window_bp",0,2147483647,true);if(r.containsKey("minimum_reported_r_squared"))number(r,"minimum_reported_r_squared",0,1,false);
            if(r.containsKey("reporting_comparison")&&!Set.of("greater_than","greater_than_or_equal").contains(r.get("reporting_comparison")))throw new IllegalArgumentException("Invalid grouping source reporting comparison");
            if(!r.get("coverage").equals("positive_edges_only")&&!r.containsKey("window_bp"))throw new IllegalArgumentException("Window-complete grouping sources must declare window_bp");if(r.get("coverage").equals("thresholded_window")&&!r.containsKey("minimum_reported_r_squared"))throw new IllegalArgumentException("A thresholded grouping source must declare its reporting cutoff");refs.add(r);
        }
        p.put("references",refs);return p;
    }
    private static String text(Map<String,Object> r,String key,int max){String s=StudyIO.requireString(r,key,"finding grouping reference");if(s.length()>max||s.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid bounded grouping reference "+key);return s;}
    private static void number(Map<String,Object> p,String key,double min,double max,boolean integer){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max||integer&&n.doubleValue()!=Math.rint(n.doubleValue()))throw new IllegalArgumentException("Invalid finding grouping "+key);}
    private static void unknown(Map<String,Object> p,Set<String> allowed,String context){for(String key:p.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown "+context+" field: "+key);}
}
