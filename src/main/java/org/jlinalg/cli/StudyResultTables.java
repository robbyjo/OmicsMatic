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
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Derived testing families never rewrite an upstream operation's sealed tables. */
final class StudyResultTables {
    private StudyResultTables() { }

    static Map<String, Object> analysis(Context context) {
        for (Object value : list(context.study().get("hypotheses"))) {
            var hypothesis = map(value);
            if (hypothesis.get("id").equals(context.operation().get("hypothesis"))) return map(hypothesis.get("analysis"));
        }
        return Map.of();
    }

    static Map<String, Path> universe(Context context, Map<String, Path> tables, String folder) throws IOException {
        String policy = text(map(analysis(context).get("harmonization")).getOrDefault("common_universe", "none"));
        if (!policy.equals("intersection") || tables.isEmpty()) return tables;
        Set<String> common = null;
        Map<String, Object> sourceHashes = new LinkedHashMap<>();
        for (var entry : tables.entrySet()) {
            var table = DelimitedData.readResult(entry.getValue());
            int id = table.column("feature_id");
            Set<String> features = new LinkedHashSet<>();
            for (String[] row : table.rows()) features.add(row[id]);
            if (common == null) common = features; else common.retainAll(features);
            sourceHashes.put(entry.getKey(), FollowupSupport.hash(entry.getValue()));
        }
        Path derived = context.outputDirectory().resolve(folder);
        Files.createDirectories(derived);
        Map<String, Path> result = new LinkedHashMap<>();
        for (var entry : tables.entrySet()) {
            Path output = derived.resolve(entry.getKey() + ".tsv").normalize();
            if (!output.getParent().equals(derived)) throw new IOException("Unsafe dataset identity: " + entry.getKey());
            var table = DelimitedData.readResult(entry.getValue());
            int id = table.column("feature_id");
            Set<String> eligible = common;
            FollowupSupport.table(output, table.header(), table.rows().stream().filter(row -> eligible.contains(row[id])).toList());
            result.put(entry.getKey(), output);
        }
        write(derived.resolve("family.yaml"), Map.of("policy", policy, "datasets", List.copyOf(tables.keySet()),
            "common_features", common.size(), "source_sha256", sourceHashes,
            "interpretation", "Intersection within this declared analysis branch; no upstream table was modified. Each sensitivity branch has its own recorded eligible family."));
        return result;
    }

    static Map<String, Path> unfiltered(Context context) throws IOException {
        Map<String, Path> result = new LinkedHashMap<>();
        StudyPrimaryResults.unfiltered(context).forEach((id, source) -> result.put(id, source.path()));
        return result;
    }

    static Path artifact(Context context, String type, String name) throws IOException {
        List<Path> found = new ArrayList<>();
        for (Path directory : context.dependencyDirectories().values()) {
            if (!Files.isRegularFile(directory.resolve("manifest.yaml"))) continue;
            var manifest = StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));
            if (!type.equals(manifest.get("operation_type"))) continue;
            if (StudyPublicEvidence.text(manifest.get("hypothesis")).isBlank() || StudyPublicEvidence.text(manifest.get("operation_id")).isBlank()) throw new IOException("Scoped dependency producer needs hypothesis and operation_id");
            if (!java.util.Objects.equals(context.operation().get("hypothesis"), manifest.get("hypothesis")) || !"complete".equals(manifest.get("state"))) continue;
            var hashes = map(manifest.get("output_sha256"));
            if (!hashes.containsKey(name)) continue;
            Path artifact = StudyFindingInventory.verified(directory,name,StudyPublicEvidence.text(hashes.get(name)));
            if (!found.contains(artifact)) found.add(artifact);
        }
        if (found.size() > 1) throw new IOException("Ambiguous primary " + type + " artifact: " + name);
        return found.isEmpty() ? null : found.get(0);
    }

    private static void verify(Path path, Object expected) throws IOException {
        if (!Files.isRegularFile(path) || !FollowupSupport.hash(path).equals(expected))
            throw new IOException("Missing or changed dependency artifact: " + path);
    }
}
