/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Audited native cohort preparation; every other stage retains full-study scope. */
final class StudyCacheScope {
    private StudyCacheScope() { }
    record Scope(String mode, Map<String,Object> context) { }

    static Scope resolve(Map<String,Object> study, Map<String,Object> operation) {
        String type = String.valueOf(operation.get("type"));
        if(type.equals("assay_qc")){var selected=new ArrayList<Object>();for(Object raw:StudyIO.list(study.get("datasets"),"datasets")){var d=StudyIO.map(raw,"dataset");if(!d.get("id").equals(operation.get("dataset_id")))continue;var source=new LinkedHashMap<String,Object>();for(String key:List.of("id","type","stage","units","assay","detection_limit"))if(d.containsKey(key))source.put(key,d.get(key));source.put("files",StudyStageInputs.files(operation,d));selected.add(source);}return new Scope("native_assay_source_v1",Map.of("datasets",selected,"assay_qc_policy",operation.get("assay_qc_policy")));}
        if (!StudyStageInputs.core(type) || "study".equals(operation.get("scope"))
                || operation.containsKey("supplied_result_id")
                || StudyIO.optionalMap(operation.get("configuration"), "configuration").containsKey("command"))
            return new Scope("full_study", study);
        Map<String,Object> hypothesis = null;
        for (Object raw : StudyIO.list(study.get("hypotheses"), "hypotheses")) {
            var h = StudyIO.map(raw, "hypothesis");
            if (h.get("id").equals(operation.get("hypothesis"))) hypothesis = h;
        }
        if (hypothesis == null) return new Scope("full_study", study);
        var analysis = StudyIO.optionalMap(hypothesis.get("analysis"), "analysis");
        Object declaredModel=StudyIO.optionalMap(analysis.get("association"), "association").get("model");
        if (Set.of("association","participant_qc").contains(type) && (declaredModel==null||String.valueOf(declaredModel).isBlank()))
            return new Scope("full_study", study); // Missing-model review consumes public literature labels and declared design context.

        var h = new LinkedHashMap<String,Object>(hypothesis);
        var selectedAnalysis = new LinkedHashMap<String,Object>();
        for (String field : List.of("association", "harmonization", "multiple_testing",
                "transformations", "model", "technical_factors", "covariates"))
            if (analysis.containsKey(field)) selectedAnalysis.put(field, analysis.get(field));
        if (type.equals("participant_qc"))
            for (String field : List.of("genotype_sample_qc", "subgroups", "subgroup_interactions"))
                if (analysis.containsKey(field)) selectedAnalysis.put(field, analysis.get(field));
        h.put("analysis", selectedAnalysis);

        var all = new LinkedHashMap<String,Map<String,Object>>();
        for (Object raw : StudyIO.list(study.get("datasets"), "datasets")) {
            var d = StudyIO.map(raw, "dataset");
            all.put(String.valueOf(d.get("id")), d);
        }
        var selectedIds = new LinkedHashSet<String>();
        collect(h, all.keySet(), selectedIds);
        collect(operation, all.keySet(), selectedIds);
        boolean changed;
        do {
            int count = selectedIds.size();
            for (String id : List.copyOf(selectedIds)) collect(all.get(id), all.keySet(), selectedIds);
            changed = count != selectedIds.size();
        } while (changed);
        var datasets = new ArrayList<Object>();
        for (var entry : all.entrySet()) if (selectedIds.contains(entry.getKey())) {
            var d = new LinkedHashMap<String,Object>(entry.getValue());
            d.put("files", StudyStageInputs.files(operation, d));
            datasets.add(d);
        }

        var context = new LinkedHashMap<String,Object>(study);
        context.remove("_resolution");
        context.put("hypotheses", List.of(h));
        context.put("datasets", datasets);
        context.put("resolver_defaults_version", StudyIO.optionalMap(study.get("_resolution"), "_resolution")
            .getOrDefault("defaults_version", "not_recorded"));
        var project = new LinkedHashMap<>(StudyIO.optionalMap(study.get("project"), "project"));
        for (String field : List.of("manuscript_policy", "finding_grouping", "mr_testing_families")) project.remove(field);
        context.put("project", project);
        context.remove("assistance");
        return new Scope("native_cohort_preparation_v1", context);
    }

    private static void collect(Object raw, Set<String> ids, Set<String> result) {
        if (raw instanceof String id && ids.contains(id)) result.add(id);
        else if (raw instanceof Map<?,?> map) for (Object value : map.values()) collect(value, ids, result);
        else if (raw instanceof Iterable<?> values) for (Object value : values) collect(value, ids, result);
    }
}
