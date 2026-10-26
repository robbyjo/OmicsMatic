/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local deterministic prerequisite rules; no data acquisition or LLM calls. */
final class StudyQuestionRules {
    private StudyQuestionRules() { }

    static List<StudyNeed> prerequisites(Map<String, Object> study, Map<String, Object> operation) {
        List<StudyNeed> result = new ArrayList<>();
        String type = String.valueOf(operation.get("type"));
        if (operation.containsKey("supplied_result_id")) return result;
        if ("study".equals(operation.get("scope")) && Set.of("finding_inventory", "impact_assessment", "manuscript_draft", "mr_family_adjustment").contains(type)) return result;
        String hypothesisId = String.valueOf(operation.get("hypothesis"));
        Map<String, Object> hypothesis = object(study, "hypotheses", hypothesisId);
        List<String> selected = strings(operation.get("datasets"));
        boolean samplingCovariance = StudyPoolingPolicy.correlated(StudyIO.optionalMap(hypothesis.get("analysis"), "analysis"));
        boolean supportedPooling = samplingCovariance && Set.of("meta_analysis", "discovery_meta_analysis", "sensitivity_analysis").contains(type);
        if (Set.of("association", "participant_qc", "rare_variant_scores").contains(type)) {
            String id = String.valueOf(operation.get("dataset_id"));
            Map<String, Object> dataset = object(study, "datasets", id);
            Map<String, Object> files = StudyIO.optionalMap(dataset.get("files"), "files");
            if (!files.containsKey("phenotype"))
                result.add(StudyNeed.dataset(id, "files.phenotype",
                    "Which local phenotype file should be used for dataset " + id + "?",
                    "Association requires phenotype measurements and a declared sample-ID mapping.",
                    "local_file", List.of()));
            if (!hypothesis.containsKey("outcome"))
                result.add(new StudyNeed("hypothesis.outcome", "hypothesis:" + hypothesisId + ":outcome",
                    "Which measured phenotype is the outcome for " + hypothesisId + "?",
                    "Specify the dictionary phenotype key or exact column, not a related phenotype.",
                    "text", List.of(), Map.of("collection", "hypotheses", "id", hypothesisId, "field", "outcome"), true));
        }
        if (Set.of("meta_analysis", "discovery_meta_analysis", "discovery_replication", "sensitivity_analysis", "rare_variant_analysis").contains(type)) {
            Set<String> cohorts = new java.util.TreeSet<>();
            for (String id : selected) {
                Map<String, Object> dataset = object(study, "datasets", id);
                if (dataset.get("cohort") != null) cohorts.add(String.valueOf(dataset.get("cohort")));
                cohorts.addAll(strings(dataset.get("cohorts")));
            }
            for (Object raw : StudyIO.optionalList(study.get("assumptions"), "assumptions")) {
                Map<String, Object> assumption = StudyIO.map(raw, "assumption");
                List<String> between = strings(assumption.get("between"));
                if (!"potentially_dependent".equals(assumption.get("type"))
                        || between.size() != 2 || !cohorts.containsAll(between) || supportedPooling
                        || samplingCovariance && type.equals("discovery_replication") && discoveryOnly(study, hypothesis, between)) continue;
                List<String> sorted = between.stream().sorted().toList();
                result.add(new StudyNeed("cohort.relationship", "cohorts:" + String.join(":", sorted),
                    "What relationship exists between cohorts " + String.join(" and ", sorted) + "?",
                    "A shared parent cohort was declared. Independent-cohort inference requires a justified independence declaration or a dependence-aware analysis.",
                    "single_choice", List.of("independent", "overlapping", "related"),
                    Map.of("collection", "cohort_relationships", "between", sorted), true));
            }
            for (Object raw : StudyIO.optionalList(study.get("cohort_relationships"), "cohort_relationships")) {
                Map<String, Object> relation = StudyIO.map(raw, "relationship");
                if ("retired".equals(relation.get("status")) || "independent".equals(relation.get("type"))) continue;
                List<String> involved = strings(relation.get("between")).stream().filter(cohorts::contains).sorted().toList();
                if (involved.size() < 2) continue;
                if (supportedPooling || samplingCovariance && type.equals("discovery_replication") && discoveryOnly(study, hypothesis, involved)) continue;
                result.add(StudyNeed.review("dependent-inference:" + hypothesisId + ":" + String.join(":", involved),
                    "How should dependence between " + String.join(" and ", involved) + " be handled for " + hypothesisId + "?",
                    "The built-in replication/meta-analysis adapters assume independent cohorts. Supply a justified revised cohort selection or a separately supported dependence-aware analysis; acknowledging dependence does not correct it.", true));
            }
        }
        return result;
    }

    private static boolean discoveryOnly(Map<String, Object> study, Map<String, Object> hypothesis, List<String> involved) {
        var inputs = StudyIO.optionalMap(hypothesis.get("inputs"), "inputs");
        Set<String> discovery = new java.util.HashSet<>(), validation = new java.util.HashSet<>();
        for (String role : List.of("discovery", "replication")) for (String id : strings(inputs.get(role))) {
            var dataset = object(study, "datasets", id); var target = role.equals("discovery") ? discovery : validation;
            target.addAll(strings(dataset.get("cohorts"))); if (dataset.containsKey("cohort")) target.add(String.valueOf(dataset.get("cohort")));
        }
        return discovery.containsAll(involved) && involved.stream().noneMatch(validation::contains);
    }

    static Map<String, Object> object(Map<String, Object> study, String collection, String id) {
        for (Object raw : StudyIO.optionalList(study.get(collection), collection)) {
            Map<String, Object> value = StudyIO.map(raw, collection);
            if (id.equals(value.get("id"))) return value;
        }
        throw new IllegalArgumentException("Unknown " + collection + " ID: " + id);
    }

    static List<String> strings(Object value) {
        return StudyIO.optionalList(value, "identifiers").stream().map(String::valueOf).toList();
    }
}
