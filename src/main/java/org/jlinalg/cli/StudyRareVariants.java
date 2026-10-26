/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import static org.jlinalg.cli.StudyRareSupport.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.raremetal.RareMetalStudy;
import org.jlinalg.settest.Acat;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Study orchestration for predeclared rare sets and typed unsigned results. */
final class StudyRareVariants implements StudyOperationExecutor {
    @Override public Result execute(Context context) throws IOException {
        Files.createDirectories(context.outputDirectory());
        if (context.operation().get("type").equals("rare_variant_masks")) return new StudyRareMasks().execute(context);
        if (context.operation().get("type").equals("rare_variant_scores")) return new StudyRareScores().execute(context);
        var config = policy(context); var selected = datasets(context);
        if (selected.size() > 1 && org.jlinalg.study.StudyPoolingPolicy.correlated(StudyResultTables.analysis(context)))
            return waiting(context, "sampling-covariance-for-sets", "Scalar-effect sampling covariance cannot correct rare-set score/covariance pooling. Use a supported dependence-aware set model or separate compatible hypotheses with an appropriate cohort selection.");
        if (selected.isEmpty()) return waiting(context, "datasets", "Rare-set analysis requires declared genotype data, quantitative score/covariance summaries, or typed set-association results.");
        if (selected.size() > 1) {
            Set<String> cohortIds = new LinkedHashSet<>();
            for (var dataset : selected) {
                String cohort = text(dataset.get("cohort"));
                if (cohort.isBlank() || !cohortIds.add(cohort)) return waiting(context, "independent-cohort-blocks",
                    "Rare-set pooling requires a distinct declared cohort for each independent score/result source. Repeated tissues, visits or models from one cohort cannot be counted as independent replication.");
            }
        }
        boolean imported = selected.stream().allMatch(dataset -> "set_association_results".equals(dataset.get("stage")));
        if (!imported && selected.stream().anyMatch(dataset -> "set_association_results".equals(dataset.get("stage"))))
            return waiting(context, "input-blocks", "Score-level and P-only set inputs require distinct compatible hypotheses/blocks; their effects and pooling methods cannot be silently mixed.");
        Path emptyMembership = imported ? null : StudyResultTables.artifact(context, "rare_variant_masks", "mask-membership.tsv");
        if (emptyMembership != null && DelimitedData.readResult(emptyMembership).rows().isEmpty()) return emptyAnalysis(context, config, emptyMembership);
        if(!imported&&"binomial".equals(map(StudyResultTables.analysis(context).get("association")).get("family")))return new StudyBinaryRareVariants().execute(context);
        List<Row> results = new ArrayList<>(), sensitivity = new ArrayList<>(); List<Object> provenance = new ArrayList<>();
        var roles = roles(context); List<String> discovery = strings(roles.get("discovery")); List<String> validation = strings(roles.get("replication"));
        List<String> all = selected.stream().map(dataset -> text(dataset.get("id"))).toList();
        Map<String, List<String>> omissions = omissionPlan(all, config);
        if (imported) {
            if (Boolean.TRUE.equals(map(config.get("external_frequency")).get("enabled")))
                return waiting(context, "p-only-external-frequency", "P-only set results cannot be refiltered by variant frequency or recover variant membership. Supply compatible score/covariance and annotation inputs to recompute masks, or retain the reviewed upstream frequency policy without enabling a new native external-frequency filter.");
            if (all.size() > 1 && !"acat".equals(config.get("unsigned_pooling")))
                return waiting(context, "unsigned-pooling", "Multiple P-only set cohorts require an explicit reviewed rare_variants.unsigned_pooling: acat contract. Inverse-variance pooling, beta QC and directional heterogeneity are inapplicable to unsigned tests.");
            String definition = null, build = null, trait = null;
            for (var dataset : selected) {
                var metadata = map(dataset.get("metadata"));
                if (!Boolean.TRUE.equals(metadata.get("complete_testing_family"))) return waiting(context, "complete-family:" + dataset.get("id"),
                    "Declare metadata.complete_testing_family: true only for a reviewed complete eligible set-test export. Hit-only inputs cannot establish the full BH denominator.");
                if (all.size() > 1) {
                    String declared = text(metadata.get("set_definition_id")), declaredBuild = text(dataset.get("genome_build"));
                    String declaredTrait = text(metadata.get("trait_id"));
                    if (declaredTrait.isBlank() || trait != null && !trait.equals(declaredTrait))
                        return waiting(context, "trait-identity", "Reviewed P-only cohort combination requires matching nonblank metadata.trait_id; a shared test or gene label does not establish a common phenotype.");
                    trait = declaredTrait;
                    if (declared.isBlank() || declaredBuild.isBlank() || definition != null && !definition.equals(declared) || build != null && !build.equals(declaredBuild))
                        return waiting(context, "compatible-definitions", "P-only cohort combination requires matching nonblank metadata.set_definition_id and genome_build; compare the assay, trait, population, masks and test calibration before approval.");
                    definition = declared; build = declaredBuild;
                }
                results.addAll(importResults(context, dataset, config));
                Path source = context.project().resolve(text(map(dataset.get("files")).get("results")));
                provenance.add(Map.of("dataset", dataset.get("id"), "sha256", FollowupSupport.hash(source), "result_type", "set_test",
                    "full_family_status", "scientist-declared; source hash retained, completeness cannot be reconstructed from rows alone"));
            }
            if (!discovery.isEmpty()) results.addAll(combineImported(results, discovery, "discovery", config));
            results.addAll(combineImported(results, all, "primary", config));
            for (var omission : omissions.entrySet()) sensitivity.addAll(combineImported(results, omission.getValue(), "leave-one-out:" + omission.getKey(), config));
        } else {
            if (config.containsKey("minimum_info")) for (var dataset : selected) if ("rare_score_summary".equals(dataset.get("stage"))) {
                Object applied = map(dataset.get("metadata")).get("minimum_info_applied");
                if (!(applied instanceof Number value) || !Double.isFinite(value.doubleValue()) || value.doubleValue() > 1 || value.doubleValue() < ((Number) config.get("minimum_info")).doubleValue())
                    return waiting(context, "summary-info-qc:" + dataset.get("id"), "Score summaries do not carry per-variant imputation INFO. Supply reviewed metadata.minimum_info_applied meeting the declared threshold, or regenerate appropriately filtered scores.");
            }
            Path masksFile = StudyResultTables.artifact(context, "rare_variant_masks", "masks.yaml");
            if (masksFile == null) return waiting(context, "masks", "Verified prespecified masks must precede rare-set tests");
            var masks = StudyYaml.loadArtifact(masksFile); String build = text(masks.get("genome_build"));
            Map<String, ScoreFiles> scores = scoreFiles(context, selected, build, config, provenance);
            if (scores.size() != selected.size()) return waiting(context, "scores", "Every selected rare-set cohort requires verified score and covariance inputs; no partial cohort substitution is performed.");
            for (String id : all) results.addAll(runNative(context, config, scores, List.of(id), "cohort:" + id, "cohort-" + id, build));
            if (!discovery.isEmpty()) results.addAll(runNative(context, config, scores, discovery, "discovery", "discovery", build));
            results.addAll(runNative(context, config, scores, all, "primary", "primary", build));
            for (var omission : omissions.entrySet()) sensitivity.addAll(runNative(context, config, scores, omission.getValue(),
                "leave-one-out:" + omission.getKey(), "leave-one-out-" + omission.getKey(), build));
            provenance.add(Map.of("mask_manifest_sha256", FollowupSupport.hash(masksFile), "mask_contract", masks));
        }
        var analysis = StudyResultTables.analysis(context); var multiple = map(analysis.get("multiple_testing"));
        String adjustment = text(multiple.getOrDefault("method", "BH")).toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("BH", "BY", "NONE").contains(adjustment)) throw new IOException("Rare-set study testing supports declared BH, BY or NONE adjustment");
        double alpha = ((Number) multiple.getOrDefault("alpha", .05)).doubleValue();
        Map<String, Map<String, Double>> adjusted = adjust(results, adjustment);
        List<Object> families = new ArrayList<>();
        for (var scope : adjusted.entrySet()) families.add(Map.of("scope", scope.getKey(), "testing_denominator", scope.getValue().size(),
            "significant_tests", scope.getValue().values().stream().filter(q -> q < alpha).count(), "family", "all eligible set x mask x registered test rows within this scope"));
        writeResults(context.outputDirectory().resolve("rare-results.tsv"), results, adjusted, text(config.get("primary_test")), alpha);
        validation(context.outputDirectory().resolve("rare-replication.tsv"), results, adjusted, discovery, validation, config, analysis, alpha);
        Map<String, Object> influence = influence(context, results, adjusted, sensitivity, omissions, config, adjustment, alpha, all);
        List<Row> primary = results.stream().filter(row -> row.scope.equals("primary") && row.test.equals(config.get("primary_test"))).toList();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("effective_policy", config); details.put("families", families); details.put("sources", provenance);
        details.put("cohort_influence", influence);
        details.put("primary_identity", Map.of("artifact", "rare-results.tsv", "scope", "primary", "test", config.get("primary_test"),
            "rule", "Prespecified test on all eligible declared cohorts; no minimum-heterogeneity or smallest-P method selection"));
        details.put("result_rows", results.size()); details.put("primary_estimable_tests", primary.stream().filter(row -> Double.isFinite(row.p)).count());
        details.put("validation_design", !discovery.isEmpty() && !validation.isEmpty() ? "discovery_frozen_before_validation" : "not_performed_no_discovery_validation_design");
        details.put("discovery_selected_tests", adjusted.getOrDefault("discovery", Map.of()).values().stream().filter(q -> q < alpha).count());
        details.put("unsigned_semantics", "Unsigned set tests have no beta, SE, direction, Cochran Q or I-squared. They never enter beta outlier QC or signed inverse-variance meta-analysis.");
        details.put("replication_semantics", "Discovery uses discovery cohorts only, with its complete joint gene/mask/test family. Validation tests retain missing coverage as unavailable. Gene-set replication does not prove the same causal variants or mechanism.");
        details.put("model_limitations", imported ? "P-only cohort ACAT combines calibrated P values; it estimates no pooled effect or directional heterogeneity. Input calibration/completeness and compatibility require source review."
            : "Native score pooling assumes independent cohorts, compatible quantitative Gaussian null models and trait units. Sparse binary, survival and unsupported ploidy designs require calibrated adapters. SKAT-O simulation resolution and numerical calibration are retained in native files; a non-significant result at a simulation floor is not evidence of absence.");
        var methodReport = new LinkedHashMap<>(StudyMethods.record(imported ? "Typed imported set tests and approved cohort P-value combination" : "Gaussian score/covariance rare-set association and pooling",
            Map.of("policy", config, "multiple_testing", adjustment, "alpha", alpha, "cohorts", all, "discovery", discovery, "validation", validation),
            list(config.get("references")), config.get("rationale"), List.of("rare-variant association", "SKAT", "ACAT")));
        boolean estimable = results.stream().anyMatch(row -> Double.isFinite(row.p));
        methodReport.put("performed", estimable); details.put("method_report", methodReport);
        details.put("evidence_disposition", estimable ? "estimable" : "not_estimable");
        write(context.outputDirectory().resolve("rare-analysis.yaml"), details);
        return new Result(State.COMPLETE, "Completed typed rare-set tests with separate joint testing families", outputs(context), details);
    }

    private static Result emptyAnalysis(Context context, Map<String,Object> config, Path membership) throws IOException {
        var multiple = map(StudyResultTables.analysis(context).get("multiple_testing"));
        double alpha = ((Number) multiple.getOrDefault("alpha", .05)).doubleValue();
        String adjustment = text(multiple.getOrDefault("method", "BH"));
        writeResults(context.outputDirectory().resolve("rare-results.tsv"), List.of(), Map.of(), text(config.get("primary_test")), alpha);
        var details = new LinkedHashMap<String,Object>();
        details.put("evidence_disposition", "not_estimable"); details.put("result_rows", 0); details.put("primary_estimable_tests", 0);
        details.put("families", List.of()); details.put("empty_mask_universe", true); details.put("effective_policy", config);
        details.put("unsigned_semantics", "No eligible alleles: no set test, effect estimate, direction or multiple-testing family was constructed. An empty eligible universe is not a negative association result.");
        details.put("mask_membership_sha256", FollowupSupport.hash(membership));
        details.put("primary_identity", Map.of("artifact", "rare-results.tsv", "scope", "primary", "test", config.get("primary_test")));
        details.put("method_report", Map.of("method", "Rare-set association", "performed", false,
            "parameters", Map.of("multiple_testing", adjustment, "alpha", alpha, "registration_only", true),
            "reason", "No alleles remain after verified prespecified mask eligibility; no association, pooling or correction was performed."));
        write(context.outputDirectory().resolve("rare-analysis.yaml"), details);
        return new Result(State.COMPLETE, "No eligible rare-set alleles; no test or correction was performed", outputs(context), details);
    }

    static Map<String, List<String>> omissionPlan(List<String> cohorts, Map<String, Object> config) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(config.get("leave_one_cohort_out")) || cohorts.size() < 3) return result;
        int required = Math.max(2, ((Number) config.get("minimum_cohorts")).intValue());
        for (String omitted : cohorts) {
            List<String> remaining = cohorts.stream().filter(id -> !id.equals(omitted)).toList();
            if (remaining.size() >= required) result.put(omitted, remaining);
        }
        return result;
    }

    /** Full eligible families are corrected separately for every omission; the primary table is never replaced. */
    static Map<String, Object> influence(Context context, List<Row> results, Map<String, Map<String, Double>> adjusted,
            List<Row> sensitivity, Map<String, List<String>> omissions, Map<String, Object> config, String adjustment, double alpha, List<String> all) throws IOException {
        var corrected = adjust(sensitivity, adjustment);
        writeResults(context.outputDirectory().resolve("rare-sensitivity-results.tsv"), sensitivity, corrected, text(config.get("primary_test")), alpha);
        Map<String, Row> primary = new TreeMap<>();
        for (Row row : results) if (row.scope.equals("primary")) primary.put(row.key(), row);
        List<String[]> comparisons = new ArrayList<>(); List<Object> scopes = new ArrayList<>();
        long significanceChanges = 0, directionChanges = 0, unavailable = 0;
        for (var omission : omissions.entrySet()) {
            String scope = "leave-one-out:" + omission.getKey();
            Map<String, Row> alternative = new TreeMap<>();
            for (Row row : sensitivity) if (row.scope.equals(scope)) alternative.put(row.key(), row);
            Set<String> keys = new TreeSet<>(primary.keySet()); keys.addAll(alternative.keySet());
            var family = corrected.getOrDefault(scope, Map.of());
            scopes.add(Map.of("scope", scope, "omitted_dataset", omission.getKey(), "included_datasets", omission.getValue(),
                "testing_denominator", family.size(), "significant_tests", family.values().stream().filter(q -> q < alpha).count()));
            for (String key : keys) {
                Row first = primary.get(key), second = alternative.get(key), identity = first == null ? second : first;
                double firstQ = adjusted.getOrDefault("primary", Map.of()).getOrDefault(key, Double.NaN), secondQ = family.getOrDefault(key, Double.NaN);
                boolean paired = first != null && second != null && Double.isFinite(firstQ) && Double.isFinite(secondQ);
                boolean directional = paired && first.type.equals("signed_burden") && second.type.equals("signed_burden")
                    && Double.isFinite(first.beta) && Double.isFinite(second.beta);
                String changed = paired ? Boolean.toString((firstQ < alpha) != (secondQ < alpha)) : "NA";
                String direction = directional ? Boolean.toString(Math.signum(first.beta) != Math.signum(second.beta)) : "NA";
                boolean primaryTest = identity.test.equals(config.get("primary_test"));
                if (primaryTest && changed.equals("true")) significanceChanges++;
                if (primaryTest && direction.equals("true")) directionChanges++;
                if (!paired) unavailable++;
                comparisons.add(new String[] {omission.getKey(), String.join(",", omission.getValue()), identity.set, identity.mask, identity.test,
                    Boolean.toString(primaryTest), first == null ? "NA" : first.type, second == null ? "NA" : second.type,
                    first == null ? "not_available" : first.status, second == null ? "not_available" : second.status,
                    first == null ? "NA" : string(first.p), second == null ? "NA" : string(second.p), string(firstQ), string(secondQ),
                    first == null ? "NA" : string(first.beta), second == null ? "NA" : string(second.beta),
                    first == null ? "NA" : string(first.se), second == null ? "NA" : string(second.se), direction, changed,
                    first == null ? "NA" : first.variants, second == null ? "NA" : second.variants,
                    first == null ? "NA" : first.cohorts, second == null ? "NA" : second.cohorts,
                    first == null ? "sensitivity_only_set" : second == null ? "primary_only_set" : paired ? "tested_both" : "not_estimable_in_both"});
            }
        }
        FollowupSupport.table(context.outputDirectory().resolve("rare-influence.tsv"), List.of("omitted_dataset", "included_datasets", "feature_id", "mask_id", "test", "is_primary_test",
            "primary_result_type", "sensitivity_result_type", "primary_status", "sensitivity_status", "primary_p", "sensitivity_p", "primary_adjusted_p", "sensitivity_adjusted_p",
            "primary_beta", "sensitivity_beta", "primary_se", "sensitivity_se", "effect_direction_changed", "significance_changed",
            "primary_n_variants", "sensitivity_n_variants", "primary_n_cohorts", "sensitivity_n_cohorts", "coverage"), comparisons);
        boolean attempted = !omissions.isEmpty();
        boolean estimable = sensitivity.stream().anyMatch(row -> Double.isFinite(row.p));
        String reason = !Boolean.TRUE.equals(config.get("leave_one_cohort_out")) ? "Disabled by the declared rare-set policy"
            : all.size() < 3 ? "At least three independent cohorts are required, retaining at least two per omission"
            : !attempted ? "No omission retains the declared minimum_cohorts and at least two cohorts"
            : !estimable ? "Omissions were attempted; no set-test family was estimable" : "All permitted single-cohort omissions retained separately";
        return Map.of("attempted", attempted, "performed", estimable, "reason", reason, "scopes", scopes, "comparison_rows", comparisons.size(),
            "primary_test_significance_changes", significanceChanges, "primary_test_direction_changes", directionChanges, "unavailable_comparisons", unavailable,
            "primary_results_sha256", FollowupSupport.hash(context.outputDirectory().resolve("rare-results.tsv")),
            "interpretation", "Exploratory cohort influence, not independent validation or minimum-heterogeneity primary selection. Each omission recomputes its complete eligible set/mask/test family and frequency eligibility; variant membership can change. Unsigned tests have no effect direction, Q or I-squared. Missing or unestimable comparisons are unavailable, not null effects.");
    }

    private record ScoreFiles(Path scores, Path covariance) { }
    private static Map<String, ScoreFiles> scoreFiles(Context context, List<Map<String, Object>> datasets, String build, Map<String, Object> config, List<Object> provenance) throws IOException {
        Map<String, ScoreFiles> result = new LinkedHashMap<>(); org.jlinalg.raremetal.ScoreModelMetadata first = null;
        for (var dataset : datasets) {
            String id = text(dataset.get("id")); var files = map(dataset.get("files")); ScoreFiles input = null;
            if ("rare_score_summary".equals(dataset.get("stage"))) input = new ScoreFiles(context.project().resolve(text(files.get("scores"))), context.project().resolve(text(files.get("covariance"))));
            else for (Path directory : context.dependencyDirectories().values()) {
                if (!Files.isRegularFile(directory.resolve("manifest.yaml"))) continue;
                var manifest = StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));
                if (!"rare_variant_scores".equals(manifest.get("operation_type")) || !"complete".equals(manifest.get("state")) || !id.equals(map(manifest.get("details")).get("dataset"))) continue;
                if (input != null) throw new IOException("Ambiguous rare scores for " + id);
                input = new ScoreFiles(verified(directory, manifest, "cohort.score.txt.gz"), verified(directory, manifest, "cohort.cov.txt.gz"));
            }
            if (input == null) continue;
            try (var reader = new RareMetalStudy(input.scores, input.covariance, 0, 0, true)) {
                if (!build.equals(reader.genomeBuild())) throw new IOException("Score genome build conflicts with mask annotation for " + id);
                if (first == null) first = reader.metadata(); else first.requireCompatible(reader.metadata());
                String expectedUnits = text(config.getOrDefault("trait_units", map(dataset.get("metadata")).get("phenotype_units")));
                if (!expectedUnits.isBlank() && !expectedUnits.equals(reader.metadata().value("TraitUnits"))) throw new IOException("Score trait units conflict with declared phenotype units: " + id);
                provenance.add(Map.of("dataset", id, "score_sha256", FollowupSupport.hash(input.scores), "covariance_sha256", FollowupSupport.hash(input.covariance),
                    "samples", reader.samples(), "model_metadata", reader.metadata().declarations(),
                    "coverage_limitations", "Missing requested variants remain missing; imported summaries cannot recover variants removed upstream, including low-MAC export truncation."));
            }
            result.put(id, input);
        }
        return result;
    }

    record Row(String scope, String set, String mask, String test, String type, double p, double beta, double se,
            String direction, String status, String variants, String cohorts, String calibration, String pooling, String weights) {
        String key() { return set + "\u001f" + mask + "\u001f" + test; }
        Row singleScope(String scope) { return new Row(scope, set, mask, test, type, p, beta, se, direction, status, variants, cohorts, calibration, "single_cohort_set_result_pass_through", weights); }
        Row scope(String scope, double p, int cohorts, String method) { return new Row(scope, set, mask, test, "unsigned_set", p, Double.NaN, Double.NaN, "NA",
            Double.isFinite(p) ? "ok" : "not_estimable", "NA", Integer.toString(cohorts), calibration, method, "equal_cohort_P_weights"); }
    }

    private static List<Row> runNative(Context context, Map<String, Object> config, Map<String, ScoreFiles> inputs, List<String> selected,
            String scope, String directory, String build) throws IOException {
        List<Row> result = new ArrayList<>(); Path folder = context.outputDirectory().resolve("native/" + directory); Files.createDirectories(folder);
        List<String[]> cohorts = new ArrayList<>();
        for (String id : selected) cohorts.add(new String[] {"cohort_" + id, inputs.get(id).scores.toAbsolutePath().toString(), inputs.get(id).covariance.toAbsolutePath().toString()});
        Path manifest = folder.resolve("cohorts.tsv"); FollowupSupport.table(manifest, List.of("cohort", "scores", "covariance"), cohorts);
        List<String> tests = strings(config.get("tests"));
        for (Object raw : list(config.get("masks"))) {
            var mask = map(raw); String maskId = text(mask.get("id"));
            Path groups = StudyResultTables.artifact(context, "rare_variant_masks", "groups/" + maskId + ".txt");
            if (groups == null) throw new IOException("Missing verified rare mask " + maskId);
            Path prefix = folder.resolve(maskId);
            List<String> args = new ArrayList<>(List.of("--cohorts", manifest.toString(), "--groups", groups.toString(), "--genome-build", build,
                "--test", String.join(",", tests), "--out", prefix.toString(), "--model-metadata", "strict", "--maf", text(mask.get("maximum_maf")),
                "--weights", text(config.get("weights")), "--min-cohorts", scope.startsWith("cohort:") ? "1" : text(config.get("minimum_cohorts")),
                "--af-policy", "observed", "--call-rate", text(1 - ((Number) config.get("maximum_marker_missing")).doubleValue()), "--hwe", text(config.get("minimum_hwe_p")),
                "--max-variants", text(config.get("maximum_variants")), "--simulations", text(config.get("simulations")), "--seed", text(config.get("seed")),
                "--skato-calibration", text(config.get("skato_calibration")), "--acat-mac-threshold", text(config.get("acat_mac_threshold"))));
            var console = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
            int status = RareMetaCli.run(args.toArray(String[]::new), new PrintStream(console), new PrintStream(error));
            Files.writeString(folder.resolve(maskId + ".console.txt"), console + "\n" + error);
            if (status != 0) throw new IOException("Rare-set backend failed: " + error);
            for (String test : tests) {
                var table = DelimitedData.readResult(Path.of(prefix + "." + test + ".tsv"));
                for (String[] row : table.rows()) {
                    if (!row[table.column("scope")].equals("meta")) continue;
                    double p = number(row[table.column(test.equals("skat-o") ? "adjusted_p" : "p_value")]);
                    boolean burden = test.equals("burden");
                    result.add(new Row(scope, row[table.column("feature_id")], maskId, test, burden ? "signed_burden" : "unsigned_set", p,
                        burden ? number(row[table.column("beta")]) : Double.NaN, burden ? number(row[table.column("se")]) : Double.NaN,
                        burden ? row[table.column("direction")] : "NA", row[table.column("status")], row[table.column("n_variants")], row[table.column("n_cohorts")],
                        table.header().contains("calibration") ? row[table.column("calibration")] : "Cauchy combination; inspect native components", "compatible_score_covariance_pool", row[table.column("weights")]));
                }
            }
        }
        return result;
    }

    private static List<Row> importResults(Context context, Map<String, Object> dataset, Map<String, Object> config) throws IOException {
        var files = map(dataset.get("files")); var mapping = map(dataset.get("columns"));
        var table = DelimitedData.readResult(context.project().resolve(text(files.get("results"))));
        int set = table.column(text(mapping.getOrDefault("feature_id", "feature_id"))), mask = table.column(text(mapping.getOrDefault("mask_id", "mask_id"))),
            test = table.column(text(mapping.getOrDefault("test", "test"))), probability = table.column(text(mapping.getOrDefault("p_value", "p_value")));
        List<Row> result = new ArrayList<>(); Set<String> seen = new LinkedHashSet<>();
        for (String[] row : table.rows()) {
            String feature = row[set], maskId = row[mask], method = row[test];
            if (feature.isBlank() || maskId.isBlank() || !strings(config.get("tests")).contains(method) || feature.chars().anyMatch(Character::isISOControl) || maskId.chars().anyMatch(Character::isISOControl))
                throw new IOException("Imported set results require nonblank identities and registered tests");
            double p = number(row[probability]);
            if (Double.isFinite(p) && (p < 0 || p > 1)) throw new IOException("Invalid imported set-test P value");
            String status = table.header().contains("status") ? row[table.column("status")] : Double.isFinite(p) ? "ok" : "not_estimable";
            if (!Set.of("ok", "tested", "complete", "single_cohort").contains(status)) p = Double.NaN;
            double beta = method.equals("burden") && table.header().contains("beta") ? number(row[table.column("beta")]) : Double.NaN;
            double se = method.equals("burden") && table.header().contains("se") ? number(row[table.column("se")]) : Double.NaN;
            boolean signed = Double.isFinite(beta) && Double.isFinite(se) && se > 0;
            String declaredType = table.header().contains("result_type") ? row[table.column("result_type")] : "auto";
            if (!Set.of("auto", "signed_burden", "unsigned_set").contains(declaredType) || declaredType.equals("signed_burden") && (!method.equals("burden") || !signed))
                throw new IOException("Declared signed set results require a burden test with finite beta and positive SE");
            if (declaredType.equals("unsigned_set")) signed = false;
            Row value = new Row("cohort:" + dataset.get("id"), feature, maskId, method, signed ? "signed_burden" : "unsigned_set", p,
                signed ? beta : Double.NaN, signed ? se : Double.NaN, signed ? beta > 0 ? "+" : beta < 0 ? "-" : "0" : "NA", status,
                table.header().contains("n_variants") ? row[table.column("n_variants")] : "NA", "1",
                table.header().contains("calibration") ? row[table.column("calibration")] : text(map(dataset.get("metadata")).getOrDefault("test_calibration", "source-provided; scientific appraisal required")), "imported_set_test",
                table.header().contains("weights") ? row[table.column("weights")] : "source-provided; not declared");
            if (!seen.add(value.key())) throw new IOException("Duplicate imported gene/mask/test row");
            result.add(value);
        }
        return result;
    }

    private static List<Row> combineImported(List<Row> rows, List<String> cohorts, String scope, Map<String, Object> config) {
        Map<String, List<Row>> groups = new TreeMap<>();
        for (Row row : rows) if (cohorts.contains(row.scope.replaceFirst("^cohort:", "")) && row.scope.startsWith("cohort:"))
            groups.computeIfAbsent(row.key(), ignored -> new ArrayList<>()).add(row);
        List<Row> result = new ArrayList<>();
        for (var group : groups.values()) {
            if (cohorts.size() == 1 && group.size() == 1) { result.add(group.get(0).singleScope(scope)); continue; }
            var valid = group.stream().filter(row -> Double.isFinite(row.p)).toList();
            double p = valid.size() < ((Number) config.get("minimum_cohorts")).intValue() ? Double.NaN
                : valid.size() == 1 ? valid.get(0).p : Acat.combine(valid.stream().mapToDouble(row -> row.p).toArray()).pValue();
            result.add(group.get(0).scope(scope, p, valid.size(), cohorts.size() == 1 ? "single_cohort_P_pass_through" : "reviewed_equal_weight_cohort_ACAT"));
        }
        return result;
    }
    static Map<String, Map<String, Double>> adjust(List<Row> rows, String method) {
        Map<String, Map<String, Double>> families = new TreeMap<>();
        for (Row row : rows) {
            var family = families.computeIfAbsent(row.scope, ignored -> new LinkedHashMap<>());
            if (Double.isFinite(row.p) && family.putIfAbsent(row.key(), row.p) != null) throw new IllegalArgumentException("Duplicate rare-set family identity: " + row.scope + " / " + row.key());
        }
        if (!method.equals("NONE")) families.replaceAll((scope, p) -> StudyCohortResults.adjust(p,method));
        return families;
    }
    static void writeResults(Path path, List<Row> rows, Map<String, Map<String, Double>> adjusted, String primaryTest, double alpha) throws IOException {
        List<String[]> output = new ArrayList<>();
        for (Row row : rows) {
            double q = adjusted.getOrDefault(row.scope, Map.of()).getOrDefault(row.key(), Double.NaN);
            output.add(new String[] {row.scope, row.set, row.mask, row.test, row.type, row.status, string(row.p), string(q),
                Boolean.toString(q < alpha), string(row.beta), string(row.se), row.direction, row.variants, row.cohorts, row.calibration, row.pooling,
                Boolean.toString(row.scope.equals("primary") && row.test.equals(primaryTest)), row.weights});
        }
        FollowupSupport.table(path, List.of("scope", "feature_id", "mask_id", "test", "result_type", "status", "p_value", "adjusted_p", "significant",
            "beta", "se", "direction", "n_variants", "n_cohorts", "calibration", "pooling", "is_primary_test", "weights"), output);
    }
    static void validation(Path path, List<Row> rows, Map<String, Map<String, Double>> adjusted, List<String> discovery,
            List<String> validation, Map<String, Object> policy, Map<String, Object> analysis, double alpha) throws IOException {
        Map<String, Row> selected = new TreeMap<>();
        for (Row row : rows) if (row.scope.equals("discovery") && adjusted.getOrDefault("discovery", Map.of()).getOrDefault(row.key(), 1.0) < alpha) selected.put(row.key(), row);
        String scope = text(map(analysis.get("replication")).getOrDefault("adjustment_scope", "all_results"));
        List<String[]> output = new ArrayList<>();
        for (String cohort : validation) {
            Map<String, Row> targets = new TreeMap<>();
            for (Row row : rows) if (row.scope.equals("cohort:" + cohort)) targets.put(row.key(), row);
            Map<String, Double> probabilities = new LinkedHashMap<>();
            targets.forEach((key, row) -> { if (Double.isFinite(row.p) && (scope.equals("all_results") || selected.containsKey(key))) probabilities.put(key, row.p); });
            String adjustment=text(map(analysis.get("multiple_testing")).getOrDefault("method","BH")).toUpperCase(java.util.Locale.ROOT);var corrected = StudyCohortResults.adjust(probabilities,adjustment);
            for (var entry : selected.entrySet()) {
                Row first = entry.getValue(), second = targets.get(entry.getKey()); double q = corrected.getOrDefault(entry.getKey(), Double.NaN);
                boolean directional = second != null && first.type.equals("signed_burden") && second.type.equals("signed_burden") && Double.isFinite(first.beta) && Double.isFinite(second.beta);
                boolean same = directional && Math.signum(first.beta) == Math.signum(second.beta);
                boolean signPass = !Boolean.TRUE.equals(policy.get("require_burden_same_sign")) || !first.test.equals("burden") || directional && same;
                boolean directionUnavailable = Boolean.TRUE.equals(policy.get("require_burden_same_sign")) && first.test.equals("burden") && !directional;
                output.add(new String[] {first.set, first.mask, first.test, String.join(",", discovery), cohort, string(first.p),
                    string(adjusted.get("discovery").get(first.key())), second == null ? "NA" : string(second.p), string(q),
                    Integer.toString(probabilities.size()), directional ? Boolean.toString(same) : "NA", directionUnavailable ? "NA" : Boolean.toString(q < alpha && signPass),
                    second == null ? "not_measured_or_unavailable" : directionUnavailable ? "direction_not_estimable" : Double.isFinite(second.p) ? "tested" : "not_estimable"});
            }
        }
        FollowupSupport.table(path, List.of("feature_id", "mask_id", "test", "discovery_datasets", "validation_dataset", "discovery_p", "discovery_adjusted_p",
            "validation_p", "validation_adjusted_p", "validation_testing_denominator", "same_sign", "replicated", "coverage"), output);
    }
    private static double number(String value) throws IOException {
        if (Set.of("", "NA", "NaN", ".").contains(value)) return Double.NaN;
        try { double n = Double.parseDouble(value); if (!Double.isFinite(n)) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IOException("Invalid numerical rare-set result: " + value, e); }
    }
    private static String string(double value) { return Double.isFinite(value) ? Double.toString(value) : "NA"; }
}
