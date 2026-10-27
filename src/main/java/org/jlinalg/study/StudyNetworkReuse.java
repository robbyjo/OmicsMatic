/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Population coverage of supplied graphs; it never fabricates per-cohort edges. */
public final class StudyNetworkReuse {
    private StudyNetworkReuse() { }
    public record Coverage(Set<String> covered, boolean resolved, Map<String,List<String>> sources) { }

    static void validatePrimaries(List<Map<String,Object>> definitions) {
        var networks=definitions.stream().filter(d->"network_analysis".equals(d.get("kind"))&&StudySuppliedResultPolicy.ownsStage(d)).toList();
        for(int i=0;i<networks.size();i++)for(int j=0;j<i;j++) {
            var a=StudyIO.map(networks.get(i).get("scope"),"scope");var b=StudyIO.map(networks.get(j).get("scope"),"scope");
            String field=!StudyIO.optionalList(a.get("datasets"),"datasets").isEmpty()&&!StudyIO.optionalList(b.get("datasets"),"datasets").isEmpty()?"datasets":"cohorts";
            var left=new LinkedHashSet<>(StudyIO.optionalList(a.get(field),field));var right=new LinkedHashSet<>(StudyIO.optionalList(b.get(field),field));
            if(left.isEmpty()||right.isEmpty())throw new IllegalArgumentException("Multiple supplied primary networks need comparable explicit disjoint dataset or cohort scopes");
            var overlap=new LinkedHashSet<>(left);overlap.retainAll(right);
            if(!overlap.isEmpty())throw new IllegalArgumentException("Supplied primary network scopes overlap; use distinct hypotheses or a sensitivity role");
        }
    }

    public static Coverage coverage(Map<String,Object> analysis,Map<String,Map<String,Object>> datasets,List<String> selected) {
        var covered=new LinkedHashSet<String>();var sources=new LinkedHashMap<String,List<String>>();boolean resolved=true;
        for(var definition:StudySuppliedResultPolicy.resolve(analysis)) {
            if(!"network_analysis".equals(definition.get("kind"))||!StudySuppliedResultPolicy.ownsStage(definition))continue;
            var one=declaredCoverage(definition,datasets,selected);covered.addAll(one.covered());sources.putAll(one.sources());resolved&=one.resolved();
        }
        return new Coverage(Set.copyOf(covered),resolved,Map.copyOf(sources));
    }

    /** Declared target scope, which alone does not establish completed graph coverage. */
    public static Coverage declaredCoverage(Map<String,Object> definition,Map<String,Map<String,Object>> datasets,List<String> selected) {
            boolean resolved=true;
            var scope=StudyIO.map(definition.get("scope"),"scope");var ids=StudyIO.optionalList(scope.get("datasets"),"datasets");
            var cohorts=StudyIO.optionalList(scope.get("cohorts"),"cohorts");var owned=new ArrayList<String>();
            if(!ids.isEmpty()) {
                for(Object id:ids)if(selected.contains(String.valueOf(id)))owned.add(String.valueOf(id));else resolved=false;
            } else if(!cohorts.isEmpty()) {
                var found=new LinkedHashSet<Object>();
                for(String id:selected) {
                    var d=datasets.get(id);if(d==null)continue;var membership=new LinkedHashSet<>(StudyIO.optionalList(d.get("cohorts"),"cohorts"));
                    if(d.get("cohort") instanceof String cohort)membership.add(cohort);
                    if(!membership.isEmpty()&&cohorts.containsAll(membership)){owned.add(id);found.addAll(membership);}
                }
                if(!found.containsAll(cohorts))resolved=false;
            } else resolved=false;
            return new Coverage(Set.copyOf(owned),resolved,Map.of(String.valueOf(definition.get("id")),List.copyOf(owned)));
    }
}
