/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Declared reporting families across primary MR tests, with explicit phenotype membership. */
public final class StudyMrFamilyPolicy {
    private StudyMrFamilyPolicy() { }
    public static List<Map<String,Object>> resolve(Map<String,Object> study) {
        var project=StudyIO.optionalMap(study.get("project"),"project");
        var active=new HashSet<String>();for(Object raw:StudyIO.optionalList(study.get("hypotheses"),"hypotheses")){var h=StudyIO.map(raw,"hypothesis");if(!"retired".equals(h.get("status")))active.add(String.valueOf(h.get("id")));}
        var ids=new HashSet<String>();var assigned=new HashSet<String>();var result=new ArrayList<Map<String,Object>>();
        for(Object raw:StudyIO.optionalList(project.get("mr_testing_families"),"project.mr_testing_families")){
            var input=StudyIO.map(raw,"MR testing family");for(String key:input.keySet())if(!Set.of("id","hypotheses","adjustment","alpha","rationale").contains(key))throw new IllegalArgumentException("Unknown MR testing family field: "+key);
            String id=StudyIO.requireString(input,"id","MR testing family");if(!id.matches("[A-Za-z][A-Za-z0-9._-]{0,127}")||!ids.add(id))throw new IllegalArgumentException("MR testing family IDs must be unique bounded identifiers");
            var members=new ArrayList<String>();for(Object value:StudyIO.list(input.get("hypotheses"),"MR family hypotheses")){
                if(!(value instanceof String h)||!active.contains(h)||members.contains(h)||!assigned.add(h))throw new IllegalArgumentException("Each active hypothesis may belong to at most one cross-hypothesis MR family: "+value);members.add(h);
            }
            if(members.size()<2)throw new IllegalArgumentException("Cross-hypothesis MR families require at least two active hypotheses");
            String method=String.valueOf(input.getOrDefault("adjustment","BY"));if(!Set.of("BH","BY","bonferroni","NONE").contains(method))throw new IllegalArgumentException("MR family adjustment must be BH, BY, bonferroni or NONE");
            Object level=input.getOrDefault("alpha",.05);if(!(level instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>=1)throw new IllegalArgumentException("MR family alpha must be in (0,1)");
            String rationale=StudyIO.requireString(input,"rationale","MR testing family");if(rationale.length()>4000||rationale.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("MR family rationale must be bounded text without control characters");
            var p=new LinkedHashMap<String,Object>();p.put("id",id);p.put("hypotheses",List.copyOf(members));p.put("adjustment",method);p.put("alpha",n.doubleValue());p.put("rationale",rationale);result.add(p);
        }
        return result;
    }
}
