/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashSet;
import java.util.Map;

/** Metadata for cohorts represented by a supplied analysis, never cohort estimates. */
public final class StudyAnalysisReferences {
    public static final String STAGE = "analysis_reference";
    private StudyAnalysisReferences() { }

    public static boolean isReference(Map<String,Object> dataset) {
        return dataset != null && STAGE.equals(dataset.get("stage"));
    }

    public static void validate(Map<String,Object> hypothesis, Map<String,Object> analysis,
            Map<String,Map<String,Object>> datasets) {
        var selected = new LinkedHashSet<String>();
        var inputs = StudyIO.optionalMap(hypothesis.get("inputs"), "inputs");
        for (Object raw : inputs.values()) {
            if (raw instanceof String id) selected.add(id);
            else for (Object id : StudyIO.list(raw, "input role")) selected.add(String.valueOf(id));
        }
        if (inputs.isEmpty()) datasets.forEach((id,d) -> { if (!"retired".equals(d.get("status"))) selected.add(id); });
        var supplied = StudySuppliedResultPolicy.resolve(analysis);
        for (String id : selected) {
            var dataset = datasets.get(id);
            if (!isReference(dataset)) continue;
            if (!StudyIO.map(dataset.get("files"), "reference.files").isEmpty())
                throw new IllegalArgumentException("An analysis_reference has files: {}; it cannot stand in for cohort estimates");
            String owner = StudyIO.requireString(dataset, "supplied_result_id", "analysis_reference");
            var matches = supplied.stream().filter(d -> owner.equals(d.get("id")) && "primary".equals(d.get("role"))).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("Analysis reference " + id + " requires its supplied primary analysis in this hypothesis");
            var scope = StudyIO.map(matches.get(0).get("scope"), "pool.scope");
            var sourceDatasets = StudyIO.optionalList(scope.get("datasets"), "scope.datasets");
            var sourceCohorts = StudyIO.optionalList(scope.get("cohorts"), "scope.cohorts");
            var cohorts = new LinkedHashSet<>(StudyIO.optionalList(dataset.get("cohorts"), "cohorts"));
            if (dataset.get("cohort") instanceof String cohort && !cohort.isBlank()) cohorts.add(cohort);
            if (cohorts.isEmpty()) throw new IllegalArgumentException("Analysis reference " + id + " requires declared cohort membership");
            if (scope.containsKey("datasets") ? !sourceDatasets.contains(id) : sourceCohorts.isEmpty() || !sourceCohorts.containsAll(cohorts))
                throw new IllegalArgumentException("Analysis reference " + id + " is outside its supplied pool's declared scope");
        }
    }
}
