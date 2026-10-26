/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.nio.file.Files;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** Preserve an imported full-family adjustment across downstream consumers. */
final class StudySuppliedFamilies {
    private StudySuppliedFamilies() { }
    static Map<String, Double> adjusted(Path source, DelimitedData table,
            Map<String, Double> probabilities, Map<String, Double> nativeAdjustment) throws IOException {
        if (StudySuppliedResultPolicy.family(source).isEmpty()) return nativeAdjustment;
        if (!table.header().contains("adjusted_p")) throw new IOException("Validated supplied primary is missing adjusted_p");
        String identity = table.header().contains("enrichment_id")?"enrichment_id":table.header().contains("feature_id") ? "feature_id" : "term_id";
        Map<String, Double> result = new LinkedHashMap<>();
        for (String[] row : table.rows()) {
            String id = row[table.column(identity)];
            if (!probabilities.containsKey(id)) continue;
            double value;
            try { value = Double.parseDouble(row[table.column("adjusted_p")]); }
            catch (NumberFormatException invalid) { throw new IOException("Unavailable imported full-family adjusted_p for " + id); }
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IOException("Invalid imported full-family adjusted_p for " + id);
            if (result.putIfAbsent(id, value) != null) throw new IOException("Ambiguous imported full-family test identity: " + id);
        }
        if (!result.keySet().equals(probabilities.keySet())) throw new IOException("Imported family adjustment does not cover all eligible supplied tests");
        return result;
    }

    static String sensitivityIssue(Context context, Path primary, Map<String, Path> inputs) throws IOException {
        if (primary == null || !Files.isRegularFile(primary.getParent().resolve("supplied-analysis.yaml"))) return null;
        var metadata = StudyYaml.loadArtifact(primary.getParent().resolve("supplied-analysis.yaml"));
        var family = StudySuppliedResultPolicy.family(primary);
        if (!"complete".equals(family.get("coverage"))) return "Native sensitivity comparisons need the complete supplied primary feature universe, not a selected-only result table";
        var scope = StudyPublicEvidence.map(metadata.get("scope"));
        Set<String> declared = new LinkedHashSet<>(StudyPublicEvidence.list(scope.get("datasets")).stream().map(StudyPublicEvidence::text).toList());
        if (inputs.isEmpty() || !declared.equals(inputs.keySet())) return "Supply original per-cohort estimates and scope.datasets identifying exactly the sources of the external primary before native sensitivity comparisons";
        var analysis = StudyResultTables.analysis(context);
        var pooling = StudyPublicEvidence.map(analysis.get("pooling"));
        var sourceContract = StudyPublicEvidence.map(scope.get("effect_contract"));
        var registeredContract = StudyPublicEvidence.map(pooling.get("effect_contract"));
        for (String field : org.jlinalg.study.StudyPoolingPolicy.EFFECT_FIELDS)
            if (!(sourceContract.get(field) instanceof String value) || value.isBlank() || !value.equals(registeredContract.get(field)))
                return "Supplied primary and native sensitivity estimates need matching complete effect contracts; unresolved field: " + field;
        Set<String> cohorts = new LinkedHashSet<>();
        for (Object raw : StudyPublicEvidence.list(context.study().get("datasets"))) {
            var dataset = StudyPublicEvidence.map(raw);
            if (!inputs.containsKey(String.valueOf(dataset.get("id")))) continue;
            if (dataset.get("cohort") instanceof String name) cohorts.add(name);
            for (Object cohort : StudyPublicEvidence.list(dataset.get("cohorts"))) cohorts.add(String.valueOf(cohort));
        }
        if (!cohorts.equals(new LinkedHashSet<>(StudyPublicEvidence.list(scope.get("cohorts")).stream().map(StudyPublicEvidence::text).toList())))
            return "The supplied primary cohort scope differs from the native sensitivity cohort scope";
        var externalParameters = StudyPublicEvidence.map(StudyPublicEvidence.map(metadata.get("method")).get("parameters"));
        var settings = StudyPublicEvidence.map(StudyPublicEvidence.map(analysis.get("operations")).get("meta_analysis"));
        for (String field : java.util.List.of("model", "inference", "confidence_level", "min_cohorts"))
            if (!externalParameters.containsKey(field)) return "Declare supplied method.parameters." + field + " before comparing a native sensitivity fit against that external primary";
        try {
            var external = StudyMetaOptions.resolve(externalParameters); var registered = StudyMetaOptions.resolve(settings);
            if (external.method() != registered.method() || external.inferenceMethod() != registered.inferenceMethod()
                    || external.confidenceLevel() != registered.confidenceLevel()
                    || external.method() == org.jlinalg.meta.MetaAnalysisMethod.RANDOM_EFFECT && (!externalParameters.containsKey("tau_squared_estimator") || external.tauSquaredEstimator() != registered.tauSquaredEstimator()))
                return "External primary pooling and native sensitivity model, inference, confidence or heterogeneity estimator differ; register a compatible protocol";
        } catch (IllegalArgumentException invalid) { return "External sensitivity-comparison settings are unsupported: " + invalid.getMessage(); }
        if (!(externalParameters.get("min_cohorts") instanceof Number minimum) || minimum.doubleValue() != minimum.intValue()
                || !(settings.getOrDefault("min_cohorts", 2) instanceof Number registeredMinimum) || registeredMinimum.doubleValue() != registeredMinimum.intValue()
                || minimum.intValue() != registeredMinimum.intValue())
            return "External primary and native sensitivity min_cohorts must match";
        Map<String, Integer> contributing = new LinkedHashMap<>();
        for (Path input : inputs.values()) for (String id : eligibleIds(input, false)) contributing.merge(id, 1, Integer::sum);
        Set<String> expectedFeatures = new LinkedHashSet<>();
        contributing.forEach((id,count) -> { if(count>=minimum.intValue()) expectedFeatures.add(id); });
        if (!expectedFeatures.equals(eligibleIds(primary, true)))
            return "Supplied primary and native sensitivity estimates have different eligible feature universes; harmonize the registered scope without substituting a favorable subset";
        var inputHashes = StudyPublicEvidence.map(StudyPublicEvidence.map(metadata.get("provenance")).get("input_sha256"));
        for (var input : inputs.entrySet()) {
            Path original = input.getValue();
            for (Object raw : StudyPublicEvidence.list(context.study().get("datasets"))) {
                var dataset = StudyPublicEvidence.map(raw);
                if (!input.getKey().equals(dataset.get("id"))) continue;
                Object file = StudyPublicEvidence.map(dataset.get("files")).get("results");
                if (file instanceof String name) original = context.project().resolve(name);
            }
            if (!Files.isRegularFile(original) || !FollowupSupport.hash(original).equals(inputHashes.get(input.getKey())))
                return "External primary and native sensitivity input versions differ or lack provenance.input_sha256 for source " + input.getKey();
        }
        var dependence = StudyPublicEvidence.map(pooling.get("dependence"));
        if (!"independent".equals(dependence.getOrDefault("method", "independent"))) {
            Path matrix = context.project().resolve(String.valueOf(dependence.get("matrix")));
            if (!Files.isRegularFile(matrix) || !FollowupSupport.hash(matrix).equals(externalParameters.get("sampling_matrix_sha256")))
                return "Known-covariance sensitivity comparisons require the exact external sampling_matrix_sha256 and registered matrix";
        }
        return null;
    }
    private static Set<String> eligibleIds(Path path, boolean pooledOnly) throws IOException {
        var table=DelimitedData.readResult(path); var result=new LinkedHashSet<String>();
        for(String[] row:table.rows()) {
            if(table.header().contains("status") && !Set.of("ok","single_cohort","tested","complete").contains(row[table.column("status")])) continue;
            if(pooledOnly && table.header().contains("term") && !row[table.column("term")].equals("pooled")) continue;
            try {
                double beta=Double.parseDouble(row[table.column("beta")]), se=Double.parseDouble(row[table.column("se")]), p=Double.parseDouble(row[table.column("p_value")]);
                if(Double.isFinite(beta) && Double.isFinite(se) && se>0 && p>=0 && p<=1) result.add(row[table.column("feature_id")]);
            } catch(NumberFormatException ignored) { /* Unestimable source rows do not become an eligible comparison family. */ }
        }
        return result;
    }
}
