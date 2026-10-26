/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyMrTestingPolicy;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Full declared primary MR families, without reusing association FDR or sensitivity P values. */
final class StudyMrMultiplicity {
    private StudyMrMultiplicity() { }
    static Map<String, Object> policy(Context context) {
        return StudyMrTestingPolicy.resolve(map(StudyBioinformatics.config(context).get("mr_testing")));
    }
    static Map<String, Object> finish(Context context, List<Object> results, Map<String, Object> automatic, List<StudyNeed> needs) throws IOException {
        var policy = policy(context);
        var eligible = StudyBioinformatics.mrFeatures(context);
        var all = StudyBioinformatics.mrAllFeatures(context);
        Set<String> selected = new LinkedHashSet<>(eligible.stream().map(StudyBioinformatics.Feature::id).toList());
        Map<String, List<Map<String, Object>>> byFeature = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> tests = new LinkedHashMap<>();
        for (int index = 0; index < results.size(); index++) {
            var record = map(results.get(index)); results.set(index, record); String feature = text(record.get("feature_id"));
            byFeature.computeIfAbsent(feature, ignored -> new ArrayList<>()).add(record);
            if (!selected.contains(feature)) continue;
            String identity = identity(context, map(record.get("sources")));
            record.put("mr_test_id", identity);
            tests.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(record);
        }
        Map<String, Map<String, Object>> automaticFeatures = new LinkedHashMap<>();
        for (Object raw : list(automatic.get("features"))) automaticFeatures.put(text(map(raw).get("feature_id")), map(raw));
        List<String> missingSources = new ArrayList<>(); List<Map<String, Object>> dispositions = new ArrayList<>();
        int outsideBudget = 0;
        for (var finding : all) {
            String feature = finding.id(); var pairs = byFeature.getOrDefault(feature, List.of());
            if (!selected.contains(feature)) { outsideBudget++; dispositions.add(disposition(feature, "", "outside_explicit_feature_budget", "The approved MR source budget excludes this eligible feature; no MR result was inferred")); continue; }
            if (pairs.isEmpty()) {
                var acquisition = automaticFeatures.getOrDefault(feature, Map.of()); String status = text(acquisition.get("status"));
                boolean exhausted = status.startsWith("not_estimable") || status.equals("insufficient_strong_candidates_before_outcome_or_ld_lookup");
                String id = "mr-test-" + StudyFindingInventory.hash(feature + "\nno_reviewed_pair").substring(0, 24);
                var record = new LinkedHashMap<String, Object>(); record.put("feature_id", feature); record.put("mr_test_id", id);
                record.put("status", exhausted ? "not_estimable" : "waiting_for_input");
                record.put("reason", acquisition.getOrDefault("reason", exhausted ? status : "No reviewed MR pair or exhausted source disposition exists for this eligible feature; annotation status: " + finding.status()
                    + ". Missing or ambiguous gene annotation does not remove this feature from the MR family. Supply reviewed exact-feature sources and a sourced feature_location when its local location is unavailable."));
                record.put("primary_feature_selection", finding.document());
                record.put("analysis_attempted", false); results.add(record);
                tests.put(id, new ArrayList<>(List.of(record)));
                if (!exhausted) missingSources.add(feature);
                dispositions.add(disposition(feature, id, text(record.get("status")), text(record.get("reason"))));
            } else for (var pair : pairs) dispositions.add(disposition(feature, text(pair.get("mr_test_id")), text(pair.get("status")), text(pair.get("reason"))));
        }
        if (!missingSources.isEmpty()) needs.add(StudyNeed.review("mr-feature-coverage:" + context.operation().get("hypothesis"),
            "Resolve MR source coverage for the remaining eligible features",
            missingSources.size() + " eligible features lack a reviewed pair or a definitive exhausted-source disposition. See mr-feature-dispositions.tsv; missing source coverage is not a null result.", true));
        Map<String, Double> probabilities = new LinkedHashMap<>(); Map<String, Map<String, Object>> primary = new LinkedHashMap<>();
        int unresolved = 0, notEstimable = 0, duplicates = 0;
        for (var test : tests.entrySet()) {
            var aliases = test.getValue(); duplicates += Math.max(0, aliases.size() - 1);
            var complete = aliases.stream().filter(row -> "complete".equals(row.get("status")) && !map(row.get("mr_primary")).isEmpty()).toList();
            boolean pending = aliases.stream().anyMatch(row -> "waiting_for_input".equals(row.get("status")));
            if (pending) { unresolved++; aliases.forEach(row -> row.put("mr_adjustment_status", "unresolved_primary_test")); continue; }
            if (aliases.stream().anyMatch(row -> "complete".equals(row.get("status")))
                    && (complete.size() != aliases.size())) {
                unresolved++; aliases.forEach(row -> row.put("mr_adjustment_status", "conflicting_duplicate_protocol_or_missing_primary"));
                needs.add(StudyNeed.review("mr-primary-protocol:" + test.getKey(), "Resolve the primary MR protocol or missing estimator record",
                    "The same source hypothesis has inconsistent estimability or a completed analysis lacks its declared primary estimate. Retain and resolve the protocol rather than selecting the usable P value.", true));
                continue;
            }
            if (complete.isEmpty()) { notEstimable++; continue; }
            var estimate = map(complete.get(0).get("mr_primary"));
            if (complete.stream().anyMatch(row -> !map(row.get("mr_primary")).equals(estimate))) {
                unresolved++; aliases.forEach(row -> row.put("mr_adjustment_status", "conflicting_duplicate_primary"));
                needs.add(StudyNeed.review("mr-conflicting-primary:" + test.getKey(), "Resolve conflicting primary MR analyses of the same test",
                    "Identical feature/source hypotheses have different primary estimates. Declare the intended primary source/instrument rule rather than selecting its P value. See mr-primary-results.tsv.", true));
                continue;
            }
            double p = numeric(estimate.get("causal_p_value"));
            if (!Double.isFinite(p) || p < 0 || p > 1) throw new IOException("Primary MR P value is invalid: " + test.getKey());
            primary.put(test.getKey(), estimate); probabilities.put(test.getKey(), p);
        }
        String familyStatus = unresolved == 0 ? "complete_declared_family" : "provisional_unresolved_primary_tests";
        Map<String, Double> adjusted = adjust(probabilities, tests.size(), text(policy.get("adjustment")));
        List<String> header = List.of("test_id", "feature_id", "pair_ids", "disposition", "primary_estimator", "estimate", "standard_error", "causal_p_value", "adjusted_p", "adjustment", "family_size", "family_status", "significant_primary");
        List<String[]> rows = new ArrayList<>();
        for (var test : tests.entrySet()) {
            var aliases = test.getValue(); var estimate = primary.getOrDefault(test.getKey(), Map.of()); double q = adjusted.getOrDefault(test.getKey(), Double.NaN);
            var adjustment = new LinkedHashMap<String, Object>(); adjustment.put("test_id", test.getKey()); adjustment.put("adjustment", policy.get("adjustment"));
            adjustment.put("adjusted_p", Double.isFinite(q) ? q : "NA"); adjustment.put("family_size", tests.size()); adjustment.put("family_status", familyStatus);
            adjustment.put("significant_primary", Double.isFinite(q) && q < ((Number) policy.get("alpha")).doubleValue() && unresolved == 0);
            for (var alias : aliases) { alias.put("mr_multiplicity", adjustment); if (!alias.containsKey("mr_adjustment_status")) alias.put("mr_adjustment_status", estimate.isEmpty() ? text(alias.get("status")) : "primary_tested"); }
            String disposition = estimate.isEmpty() ? text(aliases.get(0).get("mr_adjustment_status")) : "primary_tested";
            rows.add(new String[]{test.getKey(), text(aliases.get(0).get("feature_id")), aliases.stream().map(row -> text(row.get("pair_id"))).filter(id -> !id.isBlank()).collect(java.util.stream.Collectors.joining(";")), disposition,
                text(estimate.getOrDefault("requested_estimator", "NA")), text(estimate.getOrDefault("estimate", "NA")), text(estimate.getOrDefault("standard_error", "NA")), text(estimate.getOrDefault("causal_p_value", "NA")),
                Double.isFinite(q) ? Double.toString(q) : "NA", text(policy.get("adjustment")), Integer.toString(tests.size()), familyStatus, text(adjustment.get("significant_primary"))});
        }
        FollowupSupport.table(context.outputDirectory().resolve("mr-primary-results.tsv"), header, rows);
        Map<String,StudyBioinformatics.Feature> inventory = new LinkedHashMap<>(); all.forEach(finding -> inventory.put(finding.id(), finding));
        FollowupSupport.table(context.outputDirectory().resolve("mr-feature-dispositions.tsv"), List.of("feature_id", "test_id", "disposition", "primary_disposition", "reason",
            "annotation_status", "association_p_value", "association_adjusted_p", "association_adjustment", "association_testing_denominator", "association_source_sha256"), dispositions.stream()
            .map(row -> { var finding=inventory.get(text(row.get("feature_id"))); return new String[]{text(row.get("feature_id")), text(row.get("test_id")), text(row.get("disposition")),
                tests.containsKey(text(row.get("test_id"))) ? text(tests.get(text(row.get("test_id"))).get(0).get("mr_adjustment_status")) : text(row.get("disposition")),
                text(row.get("reason")).replaceAll("[\\t\\r\\n]+", " "), finding.status(), Double.toString(finding.p()), Double.toString(finding.fdr()),
                text(finding.selection().get("adjustment")), text(finding.selection().get("testing_denominator")), text(finding.selection().get("source_sha256"))}; }).toList());
        var summary = new LinkedHashMap<String, Object>(); summary.put("policy", policy); summary.put("scope", "primary_exposure_outcome_tests_within_this_hypothesis"); summary.put("status", familyStatus);
        summary.put("eligible_features", all.size()); summary.put("selected_features", eligible.size()); summary.put("outside_explicit_feature_budget", outsideBudget);
        summary.put("eligibility_before_annotation", true); summary.put("eligible_without_local_location", all.stream().filter(finding -> !"located".equals(finding.status())).count());
        summary.put("family_size", tests.size()); summary.put("estimable_primary_tests", probabilities.size()); summary.put("not_estimable_tests", notEstimable); summary.put("unresolved_tests", unresolved); summary.put("duplicate_pair_aliases", duplicates);
        summary.put("single_instrument_primary", "wald-ratio"); summary.put("association_FDR_reused", false);
        summary.put("interpretation", "The declared denominator includes selected eligible hypotheses without estimable P values. Their observed P values remain unavailable; implicit P=1 is used only to compute conservative adjustment bounds. Pending tests make all adjusted results provisional and cannot establish significant_primary. Exact duplicate source hypotheses share one test; conflicting duplicate estimates require review. Sensitivity estimators are retained without joining the primary family. BH requires its usual independence/positive-dependence assumptions; BY or Bonferroni can be declared for broader dependence. Association-based selection, winner's curse and instrument validity remain separate limitations. This is a within-hypothesis family, not cross-phenotype MR correction.");
        write(context.outputDirectory().resolve("mr-testing.yaml"), summary);
        return summary;
    }
    static Map<String, Double> adjust(Map<String, Double> probabilities, int denominator, String method) {
        if (denominator < probabilities.size()) throw new IllegalArgumentException("MR family denominator is smaller than its estimable tests");
        double harmonic = 1;
        if (method.equals("BY")) { harmonic = 0; for (int i = 1; i <= denominator; i++) harmonic += 1.0 / i; }
        var sorted = probabilities.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey())).toList();
        Map<String, Double> adjusted = new LinkedHashMap<>(); double previous = 1;
        for (int i = sorted.size() - 1; i >= 0; i--) {
            var entry = sorted.get(i); double p = entry.getValue();
            if (!Double.isFinite(p) || p < 0 || p > 1) throw new IllegalArgumentException("Invalid MR P value");
            double value = switch (method) { case "NONE" -> p; case "bonferroni" -> Math.min(1, p * denominator); case "BH", "BY" -> Math.min(previous, Math.min(1, p * denominator * harmonic / (i + 1.0))); default -> throw new IllegalArgumentException("Unknown MR adjustment: " + method); };
            adjusted.put(entry.getKey(), value); previous = value;
        }
        return adjusted;
    }
    private static Map<String, Object> disposition(String feature, String test, String status, String reason) { return Map.of("feature_id", feature, "test_id", test, "disposition", status, "reason", reason); }
    private static String identity(Context context, Map<String, Object> pair) throws IOException {
        var identity = new LinkedHashMap<String, Object>(); identity.put("feature", pair.get("feature_id"));
        for (String role : List.of("exposure", "outcome")) {
            var source = map(pair.get(role)); var definition = new LinkedHashMap<String, Object>();
            for (String key : List.of("trait", "feature_id", "tissue", "assay", "genome_build", "ancestry", "sample_size", "columns", "effect_units")) if (source.containsKey(key)) definition.put(key, source.get(key));
            String hash = text(source.get("sha256"));
            if (source.containsKey("prepared_path")) { Path path = Path.of(text(source.get("prepared_path"))); if (Files.isRegularFile(path)) hash = FollowupSupport.hash(path); }
            else if (source.containsKey("file_role")) {
                String fileRole = text(source.get("file_role")); Set<String> found = new LinkedHashSet<>();
                for (var dataset : StudyBioinformatics.datasets(context)) {
                    if (source.containsKey("dataset_id") && !source.get("dataset_id").equals(dataset.get("id"))) continue;
                    String file = text(map(dataset.get("files")).get(fileRole));
                    if (!file.isBlank() && Files.isRegularFile(context.project().resolve(file))) found.add(FollowupSupport.hash(context.project().resolve(file)));
                }
                if (found.size() == 1) hash = found.iterator().next(); else { definition.put("file_role", fileRole); definition.put("dataset_id", source.getOrDefault("dataset_id", "unresolved")); }
            }
            definition.put("source_identity", hash.isBlank() ? source.getOrDefault("url", source.getOrDefault("gwas_catalog_accession", source.getOrDefault("source_id", "unavailable"))) : hash);
            identity.put(role, definition);
        }
        return "mr-test-" + StudyFindingInventory.hash(StudyAssistance.json(identity)).substring(0, 24);
    }
    private static double numeric(Object value) { try { return Double.parseDouble(text(value)); } catch (NumberFormatException invalid) { return Double.NaN; } }
}
