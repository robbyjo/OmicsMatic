/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Reuse only complete batches whose receipt and every raw output remain sealed. */
final class StudyVariantBatchCache {
    private record Saved(Path directory, String prefix, Path receipt, Map<String, Object> outputs, Map<String, Object> batch) { }
    private final Map<String, Saved> saved = new LinkedHashMap<>();
    private boolean indexed;
    StudyVariantBatchCache() { }
    static Map<String, Object> provider(Context context, Map<String, Object> configuration) throws IOException {
        var scientificConfig = new LinkedHashMap<>(configuration);
        for (String field : List.of("maximum_rows", "maximum_queries", "maximum_predictions", "maximum_variants_per_batch", "timeout_seconds", "api_key_env")) scientificConfig.remove(field);
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (String field : List.of("annotations", "scores", "executable", "python", "perl", "database", "cache", "fasta")) if (configuration.containsKey(field)) {
            Path path = context.project().resolve(text(configuration.get(field))).normalize();
            if (Files.isRegularFile(path)) inputs.put(field, FollowupSupport.hash(path));
            else if (Files.isDirectory(path)) try (var children = Files.walk(path)) {
                var hashes = new LinkedHashMap<String, Object>();
                for (Path file : children.filter(Files::isRegularFile).sorted().toList()) hashes.put(path.relativize(file).toString().replace('\\', '/'), FollowupSupport.hash(file));
                inputs.put(field, hashes);
            }
        }
        try (var script = StudyVariantBatchCache.class.getResourceAsStream("alphagenome_bridge.py")) {
            if (script != null) inputs.put("packaged_bridge", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(script.readAllBytes())));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return Map.of("provider", scientificConfig, "local_inputs", inputs);
    }
    static String identity(Map<String, Object> provider, Map<String, Object> request) {
        var scientificRequest = new LinkedHashMap<>(request);
        for (String field : List.of("maximum_rows", "maximum_queries", "maximum_predictions", "maximum_variants_per_batch", "timeout_seconds", "api_key_env")) scientificRequest.remove(field);
        return StudyFindingInventory.hash(StudyAssistance.json(Map.of("version", 1, "request", scientificRequest, "provider", provider.get("provider"), "local_inputs", provider.get("local_inputs"))));
    }
    static void seal(Path folder, String identity) throws IOException {
        var hashes = new LinkedHashMap<String, Object>();
        try (var files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) if (!file.getFileName().toString().equals("batch-receipt.yaml"))
                hashes.put(folder.relativize(file).toString().replace('\\', '/'), FollowupSupport.hash(file));
        }
        write(folder.resolve("batch-receipt.yaml"), Map.of("identity", identity, "output_sha256", hashes));
    }
    boolean reuse(Context context, String identity, Path destination) throws IOException {
        if (!indexed) index(context);
        Saved prior = saved.get(identity); if (prior == null) return false;
        for (var hash : prior.batch.entrySet()) {
            String relative = hash.getKey(); String archived = prior.prefix + relative;
            if (!hash.getValue().equals(prior.outputs.get(archived))) throw new IOException("Batch receipt disagrees with its operation seal");
            Path source = StudyFindingInventory.verified(prior.directory, archived, text(hash.getValue()));
            Path target = destination.resolve(relative).normalize();
            if (Path.of(relative).isAbsolute() || !target.startsWith(destination)) throw new IOException("Batch output escapes destination");
            Files.createDirectories(target.getParent()); Files.copy(source, target);
        }
        Files.copy(prior.receipt, destination.resolve("batch-receipt.yaml")); return true;
    }
    private void index(Context context) throws IOException {
        indexed = true; Path runs = context.project().resolve("runs"); if (!Files.isDirectory(runs)) return;
        try (var paths = Files.walk(runs)) {
            for (Path manifestPath : paths.filter(path -> path.getFileName().toString().equals("manifest.yaml")).sorted().toList()) {
                if (manifestPath.startsWith(context.outputDirectory())) continue;
                var manifest = StudyYaml.loadArtifact(manifestPath);
                if (!Set.of("variant_annotation", "variant_scoring", "protein_structure", "protein_structure_assessment").contains(text(manifest.get("operation_type")))) continue;
                var outputs = map(manifest.get("output_sha256"));
                for (String name : outputs.keySet()) if (name.endsWith("/batch-receipt.yaml")) {
                    Path receipt = StudyFindingInventory.verified(manifestPath.getParent(), name, text(outputs.get(name)));
                    var receiptData = StudyYaml.loadArtifact(receipt);
                    String prefix = name.substring(0, name.length() - "batch-receipt.yaml".length());
                    saved.putIfAbsent(text(receiptData.get("identity")), new Saved(manifestPath.getParent(), prefix, receipt, outputs, map(receiptData.get("output_sha256"))));
                }
            }
        }
    }
}
