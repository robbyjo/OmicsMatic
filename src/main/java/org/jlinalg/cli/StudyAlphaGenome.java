/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Official SDK bridge or explicit local AlphaGenome-score imports. */
final class StudyAlphaGenome {
    static final List<String> FIELDS = List.of("variant_id", "mode", "resource_version", "model_version", "scorer", "gene_id", "transcript_id", "ontology_curie", "track_id", "raw_score", "quantile_score", "interval", "source_record");
    @FunctionalInterface interface Transport { Map<String, Object> call(Context context, Path folder, Map<String, Object> request, Map<String, Object> config) throws IOException; }
    record Result(List<Map<String, Object>> scores, Map<String, String> statuses, List<Object> requests) { }
    private final Transport transport;
    StudyAlphaGenome() { this(StudyAlphaGenome::sdk); }
    StudyAlphaGenome(Transport transport) { this.transport = transport; }

    Result execute(Context context, Map<String, StudyVariantUniverse.Key> variants, Map<String, Object> config) throws IOException {
        List<Map<String, Object>> scores = new ArrayList<>(); Map<String, String> statuses = new LinkedHashMap<>(); List<Object> requests = new ArrayList<>();
        if (!Boolean.TRUE.equals(config.get("enabled"))) { variants.keySet().forEach(id -> statuses.put(id, "disabled_by_protocol")); return new Result(scores, statuses, requests); }
        String engine = text(config.get("engine"));
        if (engine.equals("import")) {
            String source = text(config.get("scores"));
            if (source.isBlank()) { variants.keySet().forEach(id -> statuses.put(id, "score_import_required")); return new Result(scores, statuses, requests); }
            Path file = context.project().resolve(source).normalize();
            if (!Files.isRegularFile(file)) { variants.keySet().forEach(id -> statuses.put(id, "score_import_unavailable")); return new Result(scores, statuses, requests); }
            var table = DelimitedData.readResult(file); for (String field : FIELDS) table.column(field);
            String hash = FollowupSupport.hash(file);
            var found = new LinkedHashSet<String>();
            for (String[] row : table.rows()) {
                String id = row[table.column("variant_id")]; if (!variants.containsKey(id)) continue;
                Map<String, Object> score = row(table, row); validate(score, variants);
                if (scores.size() >= ((Number) config.get("maximum_rows")).intValue()) throw new IOException("AlphaGenome import exceeds maximum_rows; increase the declared output budget");
                score.put("source_record", "import_sha256:" + hash); scores.add(score);
                if (!score.get("raw_score").equals("NA")) found.add(id);
            }
            variants.keySet().forEach(id -> statuses.put(id, found.contains(id) ? "imported" : "not_in_score_import"));
            requests.add(Map.of("mode", "import", "source_sha256", hash, "citation", config.getOrDefault("citation", "not_declared"), "imported_rows", scores.size()));
            return new Result(scores, statuses, requests);
        }
        String blocked = StudyPublicEvidence.blocked(context.study());
        if (!blocked.isBlank()) { variants.keySet().forEach(id -> statuses.put(id, blocked)); return new Result(scores, statuses, requests); }
        var cache = new StudyVariantBatchCache(); var provider = StudyVariantBatchCache.provider(context, config);
        int calls = 0, predictions = 0, batchNumber = 0;
        List<StudyVariantUniverse.Key> atlas = new ArrayList<>(), live = new ArrayList<>();
        for (var key : variants.values()) {
            if (!key.build().equals("GRCh38")) { statuses.put(key.id(), "unsupported_genome_build"); continue; }
            if (!key.chromosome().matches("([1-9]|1[0-9]|2[0-2]|X|Y)")) { statuses.put(key.id(), "unsupported_chromosome"); continue; }
            if (engine.equals("predict")) live.add(key);
            else if (!key.snp()) { if (engine.equals("atlas_then_predict")) live.add(key); else statuses.put(key.id(), "atlas_snp_only"); }
            else atlas.add(key);
        }
        for (String mode : List.of("atlas", "predict")) {
            List<StudyVariantUniverse.Key> queue = mode.equals("atlas") ? atlas : live;
            for (int index = 0; index < queue.size();) {
                int remaining = mode.equals("atlas") ? ((Number) config.get("maximum_queries")).intValue() - calls : ((Number) config.get("maximum_predictions")).intValue() - predictions;
                if (remaining <= 0) { for (var key : queue.subList(index, queue.size())) statuses.put(key.id(), mode + "_budget_exhausted"); break; }
                if (scores.size() >= ((Number) config.get("maximum_rows")).intValue()) { for (var key : queue.subList(index, queue.size())) statuses.put(key.id(), "output_budget_exceeded"); break; }
                var batch = queue.subList(index, Math.min(queue.size(), index + Math.min(16, remaining)));
                Path folder = context.outputDirectory().resolve("alphagenome/batch-" + String.format(java.util.Locale.ROOT, "%06d", ++batchNumber)); Files.createDirectories(folder);
                Map<String, Object> request = new LinkedHashMap<>(); request.put("schema_version", 1); request.put("mode", mode);
                for (String field : List.of("api_key_env", "resource_version", "model_version", "sequence_length", "timeout_seconds", "maximum_rows", "ontology_terms")) request.put(field, config.get(field));
                request.put("maximum_rows", ((Number) config.get("maximum_rows")).intValue() - scores.size());
                request.put("scorers", config.get(mode.equals("atlas") ? "scorers" : "prediction_scorers"));
                request.put("variants", batch.stream().map(key -> Map.of("variant_id", key.id(), "genome_build", key.build(), "chrom", key.chromosome(), "pos", key.position(), "ref", key.reference(), "alt", key.alternate())).toList());
                String identity = StudyVariantBatchCache.identity(provider, request);
                boolean reused = cache.reuse(context, identity, folder);
                if (!reused) Files.writeString(folder.resolve("request.json"), StudyAssistance.json(request));
                Map<String, Object> reply;
                try { reply = reused ? StudyYaml.loadArtifact(folder.resolve("result.yaml")) : transport.call(context, folder, request, config); }
                catch (IOException unavailable) { reply = Map.of("status", "transport_unavailable", "variants", List.of()); }
                if (!reused) write(folder.resolve("result.yaml"), reply);
                requests.add(Map.of("mode", mode, "variants", batch.size(), "reused", reused, "request_sha256", FollowupSupport.hash(folder.resolve("request.json")), "response", context.outputDirectory().relativize(folder.resolve("result.yaml")).toString().replace('\\', '/')));
                if (mode.equals("atlas")) calls += batch.size(); else predictions += batch.size();
                if (!"completed_with_dispositions".equals(reply.get("status"))) {
                    String status = text(reply.getOrDefault("status", "invalid_response")); batch.forEach(key -> statuses.put(key.id(), status));
                    for (var key : queue.subList(index + batch.size(), queue.size())) statuses.put(key.id(), "waiting_after_" + status);
                    break;
                }
                Set<String> expected = new LinkedHashSet<>(batch.stream().map(StudyVariantUniverse.Key::id).toList()); Set<String> returned = new LinkedHashSet<>();
                for (Object raw : list(reply.get("variants"))) {
                    var item = map(raw); String id = text(item.get("variant_id"));
                    if (!expected.contains(id) || !returned.add(id)) throw new IOException("Unexpected/duplicate AlphaGenome variant disposition");
                    String status = text(item.get("status"));
                    if (!Set.of("complete", "not_found", "no_matching_tracks", "partial_scorer_coverage", "output_budget_exceeded", "provider_or_serialization_error").contains(status)) throw new IOException("Unknown AlphaGenome variant disposition");
                    statuses.put(id, status);
                    if (mode.equals("atlas") && engine.equals("atlas_then_predict") && status.equals("not_found")) live.add(variants.get(id));
                }
                if (!returned.equals(expected)) throw new IOException("AlphaGenome disposition coverage differs from the requested batch");
                Path output = folder.resolve("scores.tsv");
                Map<String, Set<String>> measured = new LinkedHashMap<>();
                if (Files.isRegularFile(output)) {
                    var table = DelimitedData.readResult(output); for (String field : FIELDS) table.column(field);
                    for (String[] values : table.rows()) {
                        Map<String, Object> score = row(table, values); if (!expected.contains(text(score.get("variant_id")))) throw new IOException("AlphaGenome score belongs to an unrequested variant");
                        validate(score, variants);
                        if (!mode.equals(score.get("mode")) || !list(request.get("scorers")).contains(score.get("scorer"))) throw new IOException("AlphaGenome output mode/scorer differs from request");
                        String relative = text(score.get("source_record")); Path record = folder.resolve(relative).normalize();
                        if (relative.isBlank() || Path.of(relative).isAbsolute() || !record.startsWith(folder) || !Files.isRegularFile(record)) throw new IOException("AlphaGenome raw source record is missing or escapes its batch");
                        if (!score.get("raw_score").equals("NA")) measured.computeIfAbsent(text(score.get("variant_id")), ignored -> new LinkedHashSet<>()).add(text(score.get("scorer")));
                        if (scores.size() >= ((Number) config.get("maximum_rows")).intValue()) throw new IOException("AlphaGenome response exceeds declared output budget");
                        score.put("source_record", context.outputDirectory().relativize(folder).toString().replace('\\', '/') + "/" + relative);
                        scores.add(score);
                    }
                }
                for (String id : expected) if (statuses.get(id).equals("complete") && !measured.getOrDefault(id, Set.of()).equals(new LinkedHashSet<>(list(request.get("scorers"))))) statuses.put(id, "partial_scorer_coverage");
                if (!reused && expected.stream().allMatch(id -> Set.of("complete", "not_found", "no_matching_tracks").contains(statuses.get(id)))) StudyVariantBatchCache.seal(folder, identity);
                index += batch.size();
            }
        }
        return new Result(scores, statuses, requests);
    }
    private static Map<String, Object> row(DelimitedData table, String[] row) {
        var result = new LinkedHashMap<String, Object>(); for (String field : FIELDS) result.put(field, row[table.column(field)]); return result;
    }
    private static void validate(Map<String, Object> score, Map<String, StudyVariantUniverse.Key> variants) throws IOException {
        if (!variants.containsKey(text(score.get("variant_id")))) throw new IOException("Unknown AlphaGenome variant");
        if (text(score.get("scorer")).isBlank()) throw new IOException("AlphaGenome scorer missing");
        for (String field : List.of("resource_version", "model_version")) if (Set.of("", "NA", ".").contains(text(score.get(field)))) throw new IOException("AlphaGenome scores require resource/model versions");
        for (String field : List.of("raw_score", "quantile_score")) {
            String raw = text(score.get(field)); if (Set.of("NA", "", ".", "nan", "NaN").contains(raw)) { score.put(field, "NA"); continue; }
            double value; try { value = Double.parseDouble(raw); } catch (NumberFormatException invalid) { throw new IOException("AlphaGenome score must be numeric", invalid); }
            // Signed scorers transform empirical quantiles to [-1,1]; never erase their direction.
            if (!Double.isFinite(value) || field.equals("quantile_score") && (value < -1 || value > 1)) throw new IOException("Invalid AlphaGenome score/quantile");
        }
    }
    private static Map<String, Object> sdk(Context context, Path folder, Map<String, Object> request, Map<String, Object> config) throws IOException {
        String key = System.getenv(text(config.get("api_key_env")));
        if (key == null || key.isBlank()) return Map.of("status", "credential_unavailable", "variants", List.of());
        Path script = folder.resolve("alphagenome_bridge.py");
        try (var resource = StudyAlphaGenome.class.getResourceAsStream("alphagenome_bridge.py")) {
            if (resource == null) throw new IOException("Packaged AlphaGenome bridge is absent"); Files.copy(resource, script);
        }
        String executable = text(config.get("python")); Path local = context.project().resolve(executable).normalize(); if (Files.isRegularFile(local)) executable = local.toString();
        var process = new ProcessBuilder(executable, "-B", script.toAbsolutePath().toString(), "--request", folder.resolve("request.json").toAbsolutePath().toString(), "--out", folder.toAbsolutePath().toString())
            .directory(folder.toFile()).redirectErrorStream(true).redirectOutput(folder.resolve("console.txt").toFile()).start();
        try {
            if (!process.waitFor(((Number) config.get("timeout_seconds")).intValue(), TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS);
                return Map.of("status", "process_timeout", "variants", List.of());
            }
        } catch (InterruptedException interrupted) { process.destroyForcibly(); Thread.currentThread().interrupt(); throw new IOException("AlphaGenome bridge interrupted", interrupted); }
        finally {
            Path log = folder.resolve("console.txt"); if (Files.isRegularFile(log)) { String text = Files.readString(log); Files.writeString(log, text.replace(key, "[REDACTED]"), StandardCharsets.UTF_8); }
        }
        Path response = folder.resolve("response.json");
        if (!Files.isRegularFile(response)) return Map.of("status", "sdk_process_failed", "variants", List.of());
        return map(SimpleJson.parse(Files.readString(response)));
    }
}
