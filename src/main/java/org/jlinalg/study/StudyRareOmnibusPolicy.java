/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prespecified cross-mask omnibus and a separate set/gene testing family. */
public final class StudyRareOmnibusPolicy {
    private StudyRareOmnibusPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input) {
        var p=new LinkedHashMap<String,Object>(input);
        var allowed=Set.of("enabled","selection","unit","method","component_tests","masks","tests","gene_map","component_eligibility","adjustment","alpha","maximum_rows","maximum_units","maximum_components","maximum_grid_work");
        for(String field:p.keySet())if(!allowed.contains(field))throw new IllegalArgumentException("Unknown rare_omnibus field: "+field);
        p.putIfAbsent("enabled",true);p.putIfAbsent("selection","supplemental");p.putIfAbsent("unit","set");p.putIfAbsent("method","bonferroni_min");p.putIfAbsent("component_tests","primary");
        p.putIfAbsent("masks",List.of());p.putIfAbsent("tests",List.of());p.putIfAbsent("adjustment","BY");p.putIfAbsent("alpha",.05);
        p.putIfAbsent("maximum_rows",2000000);p.putIfAbsent("maximum_units",100000);p.putIfAbsent("maximum_components",1000);p.putIfAbsent("maximum_grid_work",2000000);
        if(!(p.get("enabled") instanceof Boolean))throw new IllegalArgumentException("rare_omnibus.enabled must be boolean");
        choice(p,"selection",Set.of("primary","supplemental"));choice(p,"unit",Set.of("set","gene"));choice(p,"method",Set.of("bonferroni_min","acat"));choice(p,"component_tests",Set.of("primary","all"));choice(p,"adjustment",Set.of("BH","BY","NONE"));
        if(Boolean.FALSE.equals(p.get("enabled"))&&p.get("selection").equals("primary"))throw new IllegalArgumentException("A disabled rare omnibus cannot own primary inference");
        if(p.containsKey("gene_map")&&!p.get("unit").equals("gene"))throw new IllegalArgumentException("gene_map requires rare_omnibus.unit: gene");
        for(String field:List.of("masks","tests")){var values=StudyIO.list(p.get(field),field);if(values.size()>100||new java.util.HashSet<>(values).size()!=values.size())throw new IllegalArgumentException("Omnibus component labels must be unique and bounded");for(Object raw:values)if(!(raw instanceof String s)||s.isBlank()||s.length()>128||s.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid omnibus component label");}
        if(p.get("component_tests").equals("primary")&&!StudyIO.list(p.get("tests"),"tests").isEmpty())throw new IllegalArgumentException("Explicit tests require component_tests: all; primary tests come from the sealed source registry");
        for(String field:List.of("gene_map","component_eligibility"))if(p.containsKey(field)&&(!(p.get(field) instanceof String s)||s.isBlank()||s.length()>8000))throw new IllegalArgumentException("Omnibus input paths must be nonblank and bounded");
        integer(p,"maximum_rows",1,10000000);integer(p,"maximum_units",1,1000000);integer(p,"maximum_components",1,10000);integer(p,"maximum_grid_work",1,10000000);
        if(!(p.get("alpha") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>1)throw new IllegalArgumentException("Omnibus alpha must be in (0,1]");
        return p;
    }
    public static boolean primary(Map<String,Object> analysis){return "primary".equals(resolve(StudyIO.optionalMap(analysis.get("rare_omnibus"),"rare_omnibus")).get("selection"));}
    private static void choice(Map<String,Object> p,String key,Set<String> choices){if(!choices.contains(p.get(key)))throw new IllegalArgumentException("Unsupported rare_omnibus."+key);}
    private static void integer(Map<String,Object> p,String key,int min,int max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw new IllegalArgumentException("rare_omnibus."+key+" is outside its integer bounds");}
}
