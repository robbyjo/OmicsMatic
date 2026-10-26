/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Separate set-test eligibility, masks, numerical budgets and testing families. */
public final class StudyRareVariantPolicy {
    private StudyRareVariantPolicy() { }
    public static final Set<String> TESTS = Set.of("burden", "skat", "skat-o", "acat-v", "acat-o");
    public static boolean setInput(Map<String, Object> dataset) {
        return Set.of("set_association_results", "rare_score_summary").contains(dataset.get("stage"));
    }
    public static Map<String, Object> resolve(Map<String, Object> configured, Map<String, Object> association, boolean applicable) {
        var policy = new LinkedHashMap<>(configured);
        Set<String> allowed = Set.of("enabled", "tests", "primary_test", "masks", "minimum_mac", "maximum_marker_missing",
            "minimum_info", "minimum_hwe_p", "covariance_window_bp", "maximum_variants", "simulations", "seed", "weights",
            "minimum_cohorts", "unsigned_pooling", "multiplicity", "trait_units", "trait_id", "rationale", "references",
            "skato_calibration", "acat_mac_threshold", "require_burden_same_sign", "leave_one_cohort_out", "binary", "followup", "external_frequency");
        for (String key : policy.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown rare_variants field: " + key);
        policy.putIfAbsent("enabled", applicable);
        if (!(policy.get("enabled") instanceof Boolean)) throw new IllegalArgumentException("rare_variants.enabled must be boolean");
        if (Boolean.FALSE.equals(policy.get("enabled"))) return policy;
        var externalFrequency = StudyRareFrequencyPolicy.resolve(StudyIO.optionalMap(policy.get("external_frequency"), "external_frequency"));
        policy.put("external_frequency", externalFrequency);
        policy.put("followup",StudyRareFollowupPolicy.resolve(StudyIO.optionalMap(policy.get("followup"),"rare_variants.followup")));
        if(policy.containsKey("binary")||"binomial".equals(association.get("family")))policy.put("binary",StudyBinaryRarePolicy.resolve(StudyIO.optionalMap(policy.get("binary"),"binary"),association));
        policy.putIfAbsent("tests", List.of("burden", "skat", "skat-o", "acat-v", "acat-o"));
        List<String> tests = strings(policy.get("tests"), "tests");
        if (tests.isEmpty() || !TESTS.containsAll(tests) || new LinkedHashSet<>(tests).size() != tests.size())
            throw new IllegalArgumentException("rare_variants.tests must contain distinct supported set tests");
        policy.put("tests", tests);
        policy.putIfAbsent("primary_test", tests.contains("acat-o") ? "acat-o" : tests.get(0));
        if (!tests.contains(policy.get("primary_test"))) throw new IllegalArgumentException("rare_variants.primary_test must be in tests");
        policy.putIfAbsent("minimum_mac", 0.0);
        policy.putIfAbsent("maximum_marker_missing", association.getOrDefault("maximum_marker_missing", .02));
        if (association.containsKey("minimum_info")) policy.putIfAbsent("minimum_info", association.get("minimum_info"));
        policy.putIfAbsent("minimum_hwe_p", 0.0);
        policy.putIfAbsent("covariance_window_bp", 1000000);
        policy.putIfAbsent("maximum_variants", 2000);
        policy.putIfAbsent("simulations", 10000);
        policy.putIfAbsent("seed", 20261021L);
        policy.putIfAbsent("minimum_cohorts", 1);
        policy.putIfAbsent("weights", "default");
        policy.putIfAbsent("unsigned_pooling", "disabled");
        policy.putIfAbsent("multiplicity", "joint_tests_and_masks");
        policy.putIfAbsent("skato_calibration", "simulation");
        policy.putIfAbsent("acat_mac_threshold", 10.0);
        policy.putIfAbsent("require_burden_same_sign", false);
        policy.putIfAbsent("leave_one_cohort_out", true);
        if (!(policy.get("require_burden_same_sign") instanceof Boolean)) throw new IllegalArgumentException("require_burden_same_sign must be boolean");
        if (!(policy.get("leave_one_cohort_out") instanceof Boolean)) throw new IllegalArgumentException("leave_one_cohort_out must be boolean");
        if (!Set.of("simulation", "analytic", "deterministic").contains(policy.get("skato_calibration"))) throw new IllegalArgumentException("Unsupported SKAT-O calibration");
        if (!Set.of("disabled", "acat").contains(policy.get("unsigned_pooling"))) throw new IllegalArgumentException("unsigned_pooling must be disabled or acat");
        if (!"joint_tests_and_masks".equals(policy.get("multiplicity"))) throw new IllegalArgumentException("Only joint_tests_and_masks multiplicity is supported");
        if (!Set.of("default", "equal", "mb", "beta", "raremetal-beta").contains(policy.get("weights"))) throw new IllegalArgumentException("Unsupported rare-set weights");
        number(policy, "minimum_mac", 0, Double.MAX_VALUE);
        number(policy, "acat_mac_threshold", 0, Double.MAX_VALUE);
        for (String field : List.of("maximum_marker_missing", "minimum_hwe_p", "minimum_info")) if (policy.containsKey(field)) number(policy, field, 0, 1);
        for (String field : List.of("covariance_window_bp", "maximum_variants", "simulations", "minimum_cohorts")) number(policy, field, 1, Integer.MAX_VALUE);
        for (String field : List.of("covariance_window_bp", "maximum_variants", "simulations", "minimum_cohorts", "seed")) {
            if (!(policy.get(field) instanceof Number value) || !Double.isFinite(value.doubleValue()) || value.doubleValue() != value.longValue())
                throw new IllegalArgumentException("rare_variants." + field + " must be an integer");
        }
        List<Object> masks = new ArrayList<>(); Set<String> seen = new LinkedHashSet<>();
        for (Object raw : StudyIO.optionalList(policy.get("masks"), "masks")) {
            var mask = StudyIO.copyMap(StudyIO.map(raw, "mask"));
            if (!Set.of("id", "consequences", "maximum_maf", "maximum_external_af").containsAll(mask.keySet())) throw new IllegalArgumentException("Unknown rare-set mask field");
            String id = StudyIO.requireString(mask, "id", "mask");
            if (!id.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}") || !seen.add(id)) throw new IllegalArgumentException("Mask IDs must be unique safe identifiers");
            List<String> consequences = strings(mask.get("consequences"), "consequences");
            if (consequences.isEmpty()) throw new IllegalArgumentException("A rare mask requires predeclared consequence labels");
            mask.put("consequences", consequences); mask.putIfAbsent("maximum_maf", .01);
            if (!(number(mask, "maximum_maf", 0, .5) > 0)) throw new IllegalArgumentException("Mask maximum_maf must be positive");
            if (Boolean.TRUE.equals(externalFrequency.get("enabled"))) {
                mask.putIfAbsent("maximum_external_af", mask.get("maximum_maf"));
                if (!(number(mask, "maximum_external_af", 0, .5) > 0)) throw new IllegalArgumentException("Mask maximum_external_af must be positive");
            } else if (mask.containsKey("maximum_external_af"))
                throw new IllegalArgumentException("maximum_external_af requires external_frequency.enabled: true");
            masks.add(mask);
        }
        policy.put("masks", masks);
        for (String field : List.of("trait_id", "trait_units")) if (policy.containsKey(field)) {
            String value = StudyIO.requireString(policy, field, "rare_variants");
            if (value.length() > 150 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " must be bounded public text");
        }
        return policy;
    }
    private static List<String> strings(Object value, String field) {
        List<String> result = new ArrayList<>();
        for (Object item : StudyIO.list(value, field)) {
            if (!(item instanceof String text) || text.isBlank() || text.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " must contain nonblank labels");
            result.add(text);
        }
        return result;
    }
    private static double number(Map<String, Object> values, String field, double min, double max) {
        if (!(values.get(field) instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < min || n.doubleValue() > max)
            throw new IllegalArgumentException("rare_variants." + field + " is outside [" + min + "," + max + "]");
        return n.doubleValue();
    }
}
