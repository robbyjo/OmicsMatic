/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prespecified association-effect contrasts, separate from distribution diagnostics. */
public final class StudySubgroupInteractionPolicy {
    private StudySubgroupInteractionPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> configured) {
        var p=new LinkedHashMap<>(configured);
        Set<String> fields=Set.of("enabled","independent_rows","method","groups","features","categorical_covariates",
            "nuisance_interactions","adjustment","alpha","minimum_group_samples","minimum_group_mac",
            "maximum_tests","maximum_work","maximum_parameters","maximum_iterations","tolerance","rationale");
        unknown(p,fields,"subgroup_interactions");
        p.putIfAbsent("enabled",p.containsKey("groups"));p.putIfAbsent("independent_rows",false);p.putIfAbsent("method","inherit");
        p.putIfAbsent("groups",List.of());p.putIfAbsent("features",List.of());p.putIfAbsent("categorical_covariates",List.of());
        p.putIfAbsent("nuisance_interactions","all_two_way");p.putIfAbsent("adjustment","BY");p.putIfAbsent("alpha",.05);
        p.putIfAbsent("minimum_group_samples",10);p.putIfAbsent("minimum_group_mac",20.0);
        p.putIfAbsent("maximum_tests",1000000);p.putIfAbsent("maximum_work",250000000L);p.putIfAbsent("maximum_parameters",100);
        p.putIfAbsent("maximum_iterations",300);p.putIfAbsent("tolerance",1e-8);
        for(String field:List.of("enabled","independent_rows"))if(!(p.get(field) instanceof Boolean))throw new IllegalArgumentException(field+" must be true/false");
        if(!Set.of("inherit","ols-hc3","ols-classical","logit-lrt","firth-lrt").contains(p.get("method")))throw new IllegalArgumentException("Unsupported subgroup interaction method");
        if(!Set.of("all_two_way","group_only","none").contains(p.get("nuisance_interactions")))throw new IllegalArgumentException("nuisance_interactions must be all_two_way, group_only or none");
        if(!Set.of("BH","BY","bonferroni","NONE").contains(p.get("adjustment")))throw new IllegalArgumentException("Interaction adjustment must be BH, BY, bonferroni or NONE");
        number(p,"alpha",Double.MIN_NORMAL,1);number(p,"minimum_group_mac",0,Double.MAX_VALUE);
        integer(p,"minimum_group_samples",3,100000000);integer(p,"maximum_tests",1,10000000);integer(p,"maximum_work",1,9007199254740991L);
        integer(p,"maximum_parameters",4,1000);integer(p,"maximum_iterations",1,100000);number(p,"tolerance",Double.MIN_NORMAL,.01);
        p.put("features",names(p.get("features"),"features"));p.put("categorical_covariates",names(p.get("categorical_covariates"),"categorical_covariates"));
        var ids=new LinkedHashSet<String>();var groups=new ArrayList<Object>();
        for(Object raw:StudyIO.list(p.get("groups"),"subgroup_interactions.groups")) {
            var g=StudyIO.map(raw,"interaction group");unknown(g,Set.of("id","column","levels","cutoff","comparison","derived_from","rationale"),"interaction group");
            String id=text(g.get("id"),"group.id",100),column=text(g.get("column"),"group.column",200);
            if(!id.matches("[A-Za-z][A-Za-z0-9_.-]*")||!ids.add(id)||!column.matches("[A-Za-z_][A-Za-z0-9_.]*"))throw new IllegalArgumentException("Interaction groups require unique safe IDs and column names");
            text(g.get("rationale"),"group.rationale",4000);g.putIfAbsent("derived_from",List.of());g.put("derived_from",names(g.get("derived_from"),"group.derived_from"));
            if(g.containsKey("cutoff")) {
                if(g.containsKey("levels"))throw new IllegalArgumentException("Declare levels or a cutoff, not both");number(g,"cutoff",-Double.MAX_VALUE,Double.MAX_VALUE);
                g.putIfAbsent("comparison","greater_than");if(!Set.of("greater_than","greater_than_or_equal","less_than","less_than_or_equal").contains(g.get("comparison")))throw new IllegalArgumentException("Invalid subgroup cutoff comparison");
            } else {
                var levels=names(g.get("levels"),"group.levels");if(levels.size()!=2||g.containsKey("comparison"))throw new IllegalArgumentException("Categorical interactions require two ordered levels (reference, comparison)");g.put("levels",levels);
            }
            groups.add(g);
        }
        if(groups.size()>20||Boolean.TRUE.equals(p.get("enabled"))&&groups.isEmpty())throw new IllegalArgumentException("Enabled interactions need 1..20 prespecified group contrasts");
        p.put("groups",groups);
        if(p.containsKey("rationale"))text(p.get("rationale"),"subgroup_interactions.rationale",4000);
        if(!"all_two_way".equals(p.get("nuisance_interactions"))&&!p.containsKey("rationale"))throw new IllegalArgumentException("Omitting covariate interaction terms requires an explicit model rationale");
        return p;
    }
    private static List<String> names(Object raw,String where) {
        var seen=new LinkedHashSet<String>();for(Object value:StudyIO.list(raw,where))if(!seen.add(text(value,where,4000)))throw new IllegalArgumentException("Duplicate "+where);return List.copyOf(seen);
    }
    private static String text(Object value,String where,int maximum) {
        if(!(value instanceof String s)||s.isBlank()||s.length()>maximum||s.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException(where+" must be bounded nonblank text");return s;
    }
    private static double number(Map<String,Object> p,String field,double low,double high) {
        if(!(p.get(field) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<low||n.doubleValue()>high)throw new IllegalArgumentException(field+" outside its finite bounds");return n.doubleValue();
    }
    private static void integer(Map<String,Object> p,String field,long low,long high){double n=number(p,field,low,high);if(n!=Math.rint(n))throw new IllegalArgumentException(field+" must be integral");}
    private static void unknown(Map<String,Object> p,Set<String> allowed,String where){for(String field:p.keySet())if(!allowed.contains(field))throw new IllegalArgumentException("Unknown "+where+" field: "+field);}
}
