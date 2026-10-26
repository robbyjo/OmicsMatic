/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Effect comparability declarations and sampling dependence, separate from heterogeneity. */
public final class StudyPoolingPolicy {
    private StudyPoolingPolicy() { }
    public static final List<String> EFFECT_FIELDS = List.of("estimand", "orientation", "effect_scale", "effect_units", "phenotype_transformation", "feature_transformation");
    public static Map<String, Object> effectContract(Map<String, Object> supplied) {
        if (!EFFECT_FIELDS.containsAll(supplied.keySet())) throw new IllegalArgumentException("Unknown effect_contract field");
        supplied.forEach((key, value) -> string(value, "effect_contract." + key));
        return new LinkedHashMap<>(supplied);
    }
    public static Map<String, Object> resolve(Map<String, Object> supplied) {
        if (!Set.of("effect_contract", "require_complete_contract", "context_exceptions", "dependence").containsAll(supplied.keySet())) throw new IllegalArgumentException("Unknown pooling setting");
        var result = new LinkedHashMap<>(supplied);
        result.putIfAbsent("require_complete_contract", false);
        if (!(result.get("require_complete_contract") instanceof Boolean)) throw new IllegalArgumentException("pooling.require_complete_contract must be boolean");
        result.put("effect_contract", effectContract(StudyIO.optionalMap(result.get("effect_contract"), "pooling.effect_contract")));
        var exceptions = StudyIO.optionalMap(result.get("context_exceptions"), "pooling.context_exceptions");
        if (!Set.of("assay", "tissue", "modality").containsAll(exceptions.keySet())) throw new IllegalArgumentException("Only assay, tissue or modality context differences may have an explicit scientific exception");
        exceptions.forEach((key, value) -> string(value, "pooling.context_exceptions." + key)); result.put("context_exceptions", exceptions);
        var dependence = StudyIO.optionalMap(result.get("dependence"), "pooling.dependence");
        if (!Set.of("method", "matrix", "rationale", "citation").containsAll(dependence.keySet())) throw new IllegalArgumentException("Unknown pooling.dependence setting");
        dependence.putIfAbsent("method", "independent");
        if (!Set.of("independent", "sampling_correlation", "sampling_covariance").contains(dependence.get("method"))) throw new IllegalArgumentException("Unsupported pooling dependence method");
        dependence.forEach((key, value) -> string(value, "pooling.dependence." + key));
        if (!dependence.get("method").equals("independent")) for (String required : List.of("matrix", "rationale", "citation"))
            if (!dependence.containsKey(required)) throw new IllegalArgumentException("Covariance-aware pooling requires dependence." + required);
        if (dependence.get("method").equals("independent") && dependence.containsKey("matrix")) throw new IllegalArgumentException("An independent pooling declaration cannot silently ignore a sampling matrix");
        result.put("dependence", dependence); return result;
    }
    public static boolean correlated(Map<String, Object> analysis) {
        return !"independent".equals(StudyIO.optionalMap(resolve(StudyIO.optionalMap(analysis.get("pooling"), "pooling")).get("dependence"), "dependence").get("method"));
    }
    private static void string(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 4096 || text.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " must be bounded nonblank text");
    }
}
