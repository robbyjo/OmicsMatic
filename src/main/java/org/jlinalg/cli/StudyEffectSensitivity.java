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
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;

/** Unfiltered effect-QC sensitivity, isolated from primary result discovery. */
final class StudyEffectSensitivity {
    Result execute(Context context, StudyCliExecutor executor, Map<String, Path> filtered) throws IOException {
        Path folder = context.outputDirectory().resolve("effect-qc-unfiltered");
        Files.createDirectories(folder);
        Map<String, Path> snapshots = StudyResultTables.unfiltered(context);
        Set<String> expected = new LinkedHashSet<>(list(context.operation().get("datasets")).stream().map(Object::toString).toList());
        if (expected.isEmpty()) expected.addAll(filtered.keySet());
        Set<String> missing = new LinkedHashSet<>(expected); missing.removeAll(snapshots.keySet());
        if (snapshots.isEmpty() || !missing.isEmpty()) {
            var need = Map.<String, Object>of("status", "waiting_for_input", "missing_datasets", List.copyOf(missing),
                "reason", "Every selected dataset needs a verified complete producer with a pre-effect-QC snapshot. No partial cohort substitution is allowed.");
            write(folder.resolve("decision.yaml"), need);
            return new Result(State.WAITING_FOR_INPUT, "Unfiltered sensitivity needs complete pre-effect-QC estimates",
                List.of("effect-qc-unfiltered/decision.yaml"), need);
        }
        Map<String, Path> unfiltered = StudyResultTables.universe(child(context, folder, Map.of()), snapshots, "family-inputs");
        Map<Path, String> originalHashes = new LinkedHashMap<>();
        for (Path path : snapshots.values()) originalHashes.put(path, FollowupSupport.hash(path));
        for (Path path : filtered.values()) originalHashes.put(path, FollowupSupport.hash(path));
        Path primaryMeta = StudyPrimaryResults.metaBaseline(context);
        Path primaryReplication = StudyResultTables.artifact(context, "discovery_replication", "replication.tsv");
        for (Path path : new Path[] {primaryMeta, primaryReplication}) if (path != null) originalHashes.put(path, FollowupSupport.hash(path));
        Map<String, Object> analysis = StudyResultTables.analysis(context);
        Map<String, Object> testing = map(analysis.get("multiple_testing"));
        String method = text(testing.getOrDefault("method", "BH")).toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("BH", "BY", "NONE").contains(method)) throw new IOException("Effect-QC sensitivity requires declared BH, BY or NONE testing");
        double alpha = ((Number) testing.getOrDefault("alpha", 0.05)).doubleValue();
        List<String[]> comparisons = new ArrayList<>();
        List<Object> families = new ArrayList<>();
        for (String id : expected) {
            if (!filtered.containsKey(id)) throw new IOException("Missing primary cohort table: " + id);
            families.add(compare(id, filtered.get(id), unfiltered.get(id), method, alpha, comparisons));
        }
        executor.effectComparison(child(context, folder.resolve("effect-comparison"), Map.of()), unfiltered);
        Map<String, Object> operations = map(analysis.get("operations"));
        if (primaryMeta != null) {
            Path meta = folder.resolve("meta");
            Result result = executor.meta(child(context, meta, Map.of("configuration", map(operations.get("meta_analysis")))), unfiltered);
            if (result.state() != State.COMPLETE) return unresolved(context, folder, result);
            families.add(compare("all_cohort_meta", primaryMeta, meta.resolve("meta-analysis.tsv"), method, alpha, comparisons));
        }
        Map<String, Object> roles = Map.of();
        for (Object value : list(context.study().get("hypotheses"))) {
            var hypothesis = map(value);
            if (hypothesis.get("id").equals(context.operation().get("hypothesis"))) roles = map(hypothesis.get("inputs"));
        }
        if (primaryReplication != null) {
            List<String> discovery = list(roles.get("discovery")).stream().map(Object::toString).toList();
            Path pooled = null;
            if (discovery.size() > 1) {
                Path pool = folder.resolve("discovery");
                Map<String, Object> poolOptions = map(operations.get("discovery_meta_analysis"));
                if (poolOptions.isEmpty()) poolOptions = map(operations.get("meta_analysis"));
                Context discoveryContext = child(context, pool, Map.of("datasets", discovery, "configuration", poolOptions));
                Map<String, Path> discoveryInputs = new LinkedHashMap<>();
                for (String id : discovery) discoveryInputs.put(id, snapshots.get(id));
                discoveryInputs = StudyResultTables.universe(discoveryContext, discoveryInputs, "family-inputs");
                Result result = executor.discoveryMeta(discoveryContext, discoveryInputs);
                if (result.state() != State.COMPLETE) return unresolved(context, folder, result);
                pooled = pool.resolve("discovery-results.tsv");
            }
            Path replication = folder.resolve("replication");
            Result result = executor.replication(child(context, replication, Map.of("roles", roles, "pooled_discovery", false)), unfiltered, pooled);
            if (result.state() != State.COMPLETE) return unresolved(context, folder, result);
            compareReplication(primaryReplication, replication.resolve("replication.tsv"), folder.resolve("replication-sensitivity.tsv"));
        }
        FollowupSupport.table(folder.resolve("feature-sensitivity.tsv"), List.of("family", "feature_id", "filtered_present", "unfiltered_present",
            "filtered_beta", "unfiltered_beta", "filtered_p", "unfiltered_p", "filtered_adjusted_p", "unfiltered_adjusted_p",
            "filtered_significant", "unfiltered_significant", "same_direction"), comparisons);
        for (var entry : originalHashes.entrySet()) if (!entry.getValue().equals(FollowupSupport.hash(entry.getKey())))
            throw new IOException("Primary or unfiltered source changed during sensitivity: " + entry.getKey());
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("status", "complete"); decision.put("primary_unchanged", true);
        decision.put("families", families); decision.put("adjustment", method); decision.put("alpha", alpha);
        decision.put("scope", "Disable only effect-outlier screening; preserve numeric/status eligibility, sample/genotype QC, models and cohort roles. Recompute each branch's declared common universe and full testing family.");
        decision.put("primary_meta_repeated", primaryMeta != null); decision.put("validation_repeated", primaryReplication != null);
        decision.put("manuscript_disposition", "Sensitivity evidence only. Unfiltered findings never replace or enter primary enrichment, MR or literature selection automatically.");
        decision.put("method_report", StudyMethods.record("Unfiltered effect-outlier sensitivity", Map.of("testing_method", method,
            "alpha", alpha, "datasets", List.copyOf(expected), "common_universe", map(analysis.get("harmonization")).getOrDefault("common_universe", "none")),
            List.of(), "Audit the influence of a heuristic effect filter while retaining primary identity.", List.of("sensitivity analysis", "effect-size QC")));
        write(folder.resolve("decision.yaml"), decision);
        List<String> outputs;
        try (var files = Files.walk(folder)) {
            outputs = files.filter(Files::isRegularFile).map(context.outputDirectory()::relativize)
                .map(path -> path.toString().replace('\\', '/')).sorted().toList();
        }
        return new Result(State.COMPLETE, "Completed isolated unfiltered effect-QC sensitivity", outputs, decision);
    }

    private static Context child(Context parent, Path output, Map<String, Object> options) throws IOException {
        Files.createDirectories(output);
        var operation = new LinkedHashMap<>(parent.operation()); operation.putAll(options);
        return new Context(parent.project(), output, operation, parent.study(), parent.plan(), parent.dependencyDirectories());
    }

    private record Estimate(double beta, double p) { }
    private static Map<String, Estimate> estimates(Path path) throws IOException {
        var table = DelimitedData.readResult(path); Map<String, Estimate> result = new LinkedHashMap<>();
        for (String[] row : table.rows()) {
            if (table.header().contains("status") && !Set.of("ok", "single_cohort").contains(row[table.column("status")])) continue;
            if (table.header().contains("term") && !"pooled".equals(row[table.column("term")])) continue;
            double p;
            try { p = Double.parseDouble(row[table.column("p_value")]); } catch (NumberFormatException ignored) { p = Double.NaN; }
            result.put(row[table.column("feature_id")], new Estimate(Double.parseDouble(row[table.column("beta")]), p));
        }
        return result;
    }
    private static Map<String, Double> adjusted(Map<String, Estimate> estimates, String method) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        estimates.forEach((id, row) -> { if (Double.isFinite(row.p())) probabilities.put(id, row.p()); });
        return StudyCohortResults.adjust(probabilities,method);
    }
    private static Map<String, Object> compare(String family, Path primary, Path sensitivity, String method, double alpha, List<String[]> output) throws IOException {
        var filtered = estimates(primary); var unfiltered = estimates(sensitivity);
        var firstQ = adjusted(filtered, method); var secondQ = adjusted(unfiltered, method);
        Set<String> union = new java.util.TreeSet<>(filtered.keySet()); union.addAll(unfiltered.keySet());
        for (String id : union) {
            Estimate first = filtered.get(id), second = unfiltered.get(id);
            output.add(new String[] {family, id, Boolean.toString(first != null), Boolean.toString(second != null),
                first == null ? "NA" : number(first.beta()), second == null ? "NA" : number(second.beta()),
                first == null ? "NA" : number(first.p()), second == null ? "NA" : number(second.p()),
                number(firstQ.getOrDefault(id, Double.NaN)), number(secondQ.getOrDefault(id, Double.NaN)),
                Boolean.toString(firstQ.getOrDefault(id, 1.0) < alpha), Boolean.toString(secondQ.getOrDefault(id, 1.0) < alpha),
                first == null || second == null ? "NA" : Boolean.toString(Math.signum(first.beta()) == Math.signum(second.beta()))});
        }
        return Map.of("family", family, "filtered_estimable", filtered.size(), "unfiltered_estimable", unfiltered.size(),
            "filtered_testing_denominator", firstQ.size(), "unfiltered_testing_denominator", secondQ.size(),
            "filtered_significant", firstQ.values().stream().filter(q -> q < alpha).count(), "unfiltered_significant", secondQ.values().stream().filter(q -> q < alpha).count());
    }
    private static String number(double value) { return Double.isFinite(value) ? Double.toString(value) : "NA"; }
    private static Result unresolved(Context context, Path folder, Result child) throws IOException {
        var details = new LinkedHashMap<>(child.details()); details.put("reason", "Unfiltered branch prerequisites are unresolved; primary results are unchanged");
        write(folder.resolve("decision.yaml"), details); List<String> outputs;
        try (var paths = Files.walk(folder)) { outputs = paths.filter(Files::isRegularFile).map(context.outputDirectory()::relativize).map(path -> path.toString().replace('\\', '/')).sorted().toList(); }
        return new Result(State.WAITING_FOR_INPUT, "Unfiltered sensitivity needs compatible effects or valid sampling covariance", outputs, details);
    }

    private static void compareReplication(Path primary, Path sensitivity, Path output) throws IOException {
        var first = replication(primary); var second = replication(sensitivity);
        Set<String> keys = new java.util.TreeSet<>(first.keySet()); keys.addAll(second.keySet());
        List<String[]> rows = new ArrayList<>();
        for (String key : keys) {
            var a = first.getOrDefault(key, List.of("NA", "NA", "NA"));
            var b = second.getOrDefault(key, List.of("NA", "NA", "NA"));
            String[] identity = key.split("\t", -1);
            rows.add(new String[] {identity[0], identity[1], identity[2], a.get(0), b.get(0), a.get(1), b.get(1), a.get(2), b.get(2)});
        }
        FollowupSupport.table(output, List.of("feature_id", "discovery_dataset", "replication_dataset", "filtered_discovery_selected",
            "unfiltered_discovery_selected", "filtered_validation_adjusted_p", "unfiltered_validation_adjusted_p", "filtered_replicated", "unfiltered_replicated"), rows);
    }
    private static Map<String, List<String>> replication(Path path) throws IOException {
        var table = DelimitedData.readResult(path); Map<String, List<String>> result = new LinkedHashMap<>();
        for (String[] row : table.rows()) result.put(row[table.column("feature_id")] + "\t" + row[table.column("discovery_dataset")] + "\t" + row[table.column("replication_dataset")],
            List.of(row[table.column("discovery_selected")], row[table.column("replication_adjusted_p")], row[table.column("replicated")]));
        return result;
    }
}
