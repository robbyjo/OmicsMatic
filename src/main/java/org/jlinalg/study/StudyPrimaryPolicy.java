/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Prespecified primary identity; data-dependent source or cohort optimization is never inferred. */
public final class StudyPrimaryPolicy {
    private StudyPrimaryPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> raw) {
        var p=new LinkedHashMap<String,Object>(raw);
        for(String key:p.keySet())if(!Set.of("kind","operation_id","dataset_id","term","rationale").contains(key))throw new IllegalArgumentException("Unknown primary_result field: "+key);
        p.putIfAbsent("kind","auto");if(!Set.of("auto","meta_analysis","association").contains(p.get("kind")))throw new IllegalArgumentException("primary_result.kind must be auto, meta_analysis or association");
        for(String key:Set.of("operation_id","dataset_id","term","rationale"))if(p.containsKey(key)&&(!(p.get(key) instanceof String s)||s.isBlank()||s.length()>4000||s.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("primary_result."+key+" must be nonblank bounded text without control characters");
        for(String key:Set.of("operation_id","dataset_id"))if(p.containsKey(key)&&!p.get(key).toString().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}"))throw new IllegalArgumentException("primary_result."+key+" must be a safe identifier");
        if(p.containsKey("dataset_id")&&p.get("kind").equals("meta_analysis"))throw new IllegalArgumentException("A meta-analysis primary cannot designate a single dataset_id");
        if((!p.get("kind").equals("auto")||p.containsKey("operation_id")||p.containsKey("dataset_id")||p.containsKey("term"))&&!p.containsKey("rationale"))throw new IllegalArgumentException("An explicit primary source or contrast needs its prespecified rationale");
        return p;
    }
}
