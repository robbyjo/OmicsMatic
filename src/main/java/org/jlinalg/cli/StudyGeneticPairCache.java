/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Independent completed native pairs, with fresh operation-wide correction on resume. */
final class StudyGeneticPairCache {
    static final String RECEIPT = "native-pair-receipt.yaml";
    static final String CAVEAT = "A completed native genetic-evidence pair was reused from a prior run after verifying its scientific input identity and sealed outputs. Reuse is provenance continuity, not independent replication or new numerical execution. Current operation-wide coverage and multiple-testing correction are evaluated again.";
    record Request(String key, Map<String,Object> identity, String reason) { }
    private record Prior(Path directory, Path manifest, String relative, Map<String,Object> outputs) { }
    private final Map<String,List<Prior>> prior = new LinkedHashMap<>();
    private final List<Object> reused = new ArrayList<>(), invalidated = new ArrayList<>(), decisions = new ArrayList<>();
    private boolean indexed;
    private String software;
    private int executed;

    Request request(Context c, Map<String,Object> pair, StudyBioinformatics.Feature finding) throws IOException {
        if (finding == null) return new Request("", Map.of(), "current_feature_not_eligible");
        var inputs = new TreeMap<String,Object>();
        try {
            for (String role : List.of("exposure", "outcome", "ld")) {
                var source = map(pair.get(role));
                if (source.containsKey("prepared_path") || source.containsKey("gwas_catalog_accession"))
                    return new Request("", Map.of(), "automatic_source_not_pinned_for_pair_reuse");
                if (!source.containsKey("url") && !source.containsKey("file_role"))
                    return new Request("", Map.of(), "automatic_reference_not_pinned_for_pair_reuse");
                inputs.put(role, input(c, source));
                if (role.equals("ld")) {
                    var alleles = new LinkedHashMap<>(source);
                    alleles.remove("url"); alleles.put("file_role", required(source, "alleles_file_role"));
                    inputs.put("ld_alleles", input(c, alleles));
                }
            }
        } catch (IOException | IllegalArgumentException unavailable) {
            // A previously usable source cannot mask a currently missing input.
            return new Request("", Map.of(), "current_source_unavailable_or_not_pinned");
        }
        if (software == null) software = software();
        var identity = new LinkedHashMap<String,Object>();
        identity.put("version", 1); identity.put("software", software);
        identity.put("operation_type", c.operation().get("type"));
        identity.put("hypothesis", c.operation().get("hypothesis"));
        identity.put("pair", pair); identity.put("inputs", inputs);
        identity.put("eligible_feature", finding.document());
        var hypothesis = StudyBioinformatics.hypothesis(c);
        var authorization = new LinkedHashMap<String,Object>();
        authorization.put("outcome", text(hypothesis.get("outcome")));
        authorization.put("phenotype_terms", list(StudyBioinformatics.config(c).get("phenotype_terms")));
        authorization.put("related_phenotypes", list(map(StudyBioinformatics.config(c).get("colocalization")).get("related_phenotypes")));
        identity.put("trait_authorization", authorization);
        // MR family composition/adjustment are deliberately not cached with a fit.
        identity.put("inference", "mendelian_randomization".equals(c.operation().get("type"))
            ? Map.of("primary_estimator", StudyMrMultiplicity.policy(c).get("primary_estimator"))
            : "colocalization".equals(c.operation().get("type")) ? StudyColocalizationReview.policy(c) : Map.of());
        return new Request(fingerprint(identity), identity, "");
    }

    private static Map<String,Object> input(Context c, Map<String,Object> source) throws IOException {
        if (source.containsKey("url")) {
            String hash = required(source, "sha256").toLowerCase(java.util.Locale.ROOT);
            if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Unpinned source");
            return Map.of("type", "checksum_pinned_public_source", "sha256", hash);
        }
        String datasetId = required(source, "dataset_id"), role = required(source, "file_role");
        var dataset = StudyBioinformatics.datasets(c).stream().filter(d -> datasetId.equals(d.get("id"))).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Source is not an input dataset"));
        Path path = c.project().resolve(required(map(dataset.get("files")), role)).normalize();
        if (!Files.isRegularFile(path)) throw new IOException("Missing current source");
        return Map.of("type", "local_source", "dataset_id", datasetId, "file_role", role,
            "path", path.toRealPath().toString(), "sha256", FollowupSupport.hash(path));
    }

    boolean restore(Context c, Request request, Path destination, Map<String,Object> result) throws IOException {
        if (request.key().isEmpty()) {
            decisions.add(Map.of("pair_id", result.get("pair_id"), "disposition", request.reason()));
            return false;
        }
        if (!c.resumeCompletedWork()) {
            decisions.add(Map.of("pair_id", result.get("pair_id"), "key", request.key(), "disposition", "fresh_execution_requested"));
            return false;
        }
        if (!indexed) index(c);
        for (Prior old : prior.getOrDefault(request.key(), List.of())) {
            Map<String,Object> receipt, saved;
            var files = new LinkedHashMap<String,Path>();
            try {
                Path receiptPath = StudyFindingInventory.verified(old.directory(), old.relative(), text(old.outputs().get(old.relative())));
                receipt = StudyYaml.loadArtifact(receiptPath);
                if (!request.key().equals(receipt.get("key")) || !request.key().equals(fingerprint(receipt.get("identity"))))
                    throw new IOException("Native pair identity disagrees with the current request");
                saved = map(receipt.get("result"));
                if (!Set.of("complete", "not_estimable").contains(text(saved.get("status")))
                        || !text(result.get("pair_id")).equals(saved.get("pair_id"))
                        || !text(result.get("feature_id")).equals(saved.get("feature_id")))
                    throw new IOException("Native pair is incomplete or has a different identity");
                String prefix = old.relative().substring(0, old.relative().length() - RECEIPT.length());
                var hashes = map(receipt.get("output_sha256"));
                if (hashes.isEmpty()) throw new IOException("Native pair has no sealed execution artifacts");
                var expected = new java.util.TreeSet<String>();
                for (var hash : hashes.entrySet()) {
                    String name = hash.getKey();
                    Path relative = Path.of(name);
                    if (relative.isAbsolute() || !relative.normalize().equals(relative) || name.contains("\\")
                            || name.equals(RECEIPT) || name.startsWith("..")) throw new IOException("Unsafe native pair output");
                    String full = prefix + name; expected.add(full);
                    if (!hash.getValue().equals(old.outputs().get(full))) throw new IOException("Native pair receipt disagrees with operation seal");
                    files.put(name, StudyFindingInventory.verified(old.directory(), full, text(hash.getValue())));
                }
                var declared = new java.util.TreeSet<String>();
                for (String name : old.outputs().keySet()) if (name.startsWith(prefix) && !name.equals(old.relative())) declared.add(name);
                if (!expected.equals(declared)) throw new IOException("Native pair receipt omits operation-sealed artifacts");
                files.put(RECEIPT, receiptPath);
            } catch (IOException | RuntimeException changed) {
                invalidated.add(Map.of("key", request.key(), "source", relative(c, old.directory().resolve(old.relative())),
                    "reason", changed.getClass().getSimpleName(), "disposition", "retain_invalid_derived_outputs_and_recompute_current_inputs"));
                continue;
            }
            // Verify the entire set before copying any file into the new pair.
            for (var file : files.entrySet()) {
                Path target = destination.resolve(file.getKey()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Native pair output escapes destination");
                Files.createDirectories(target.getParent()); Files.copy(file.getValue(), target);
                String expected = file.getKey().equals(RECEIPT) ? text(old.outputs().get(old.relative()))
                    : text(map(receipt.get("output_sha256")).get(file.getKey()));
                if (!FollowupSupport.hash(target).equals(expected))
                    throw new IOException("Native pair changed while copying");
            }
            result.clear(); result.putAll(saved);
            var provenance = Map.<String,Object>of("key", request.key(), "source_receipt", relative(c, old.directory().resolve(old.relative())),
                "source_receipt_sha256", FollowupSupport.hash(old.directory().resolve(old.relative())),
                "source_manifest_sha256", FollowupSupport.hash(old.manifest()), "required_manuscript_caveat", CAVEAT);
            result.put("native_pair_reuse", provenance); result.put("reused_native_result", true);
            result.put("native_pair_attempted_this_run", false); result.put("execution_origin", "native_prior_run");
            reused.add(Map.of("pair_id", result.get("pair_id"), "feature_id", result.get("feature_id"), "provenance", provenance));
            return true;
        }
        decisions.add(Map.of("pair_id", result.get("pair_id"), "key", request.key(), "disposition", "no_verified_completed_pair"));
        return false;
    }

    void executed() { executed++; }

    void seal(Context c, Map<String,Object> pair, StudyBioinformatics.Feature finding, Request request,
            Path directory, Map<String,Object> result) throws IOException {
        if (request.key().isEmpty() || !Set.of("complete", "not_estimable").contains(text(result.get("status")))) return;
        if (!request.key().equals(request(c, pair, finding).key())) {
            decisions.add(Map.of("pair_id", result.get("pair_id"), "disposition", "inputs_changed_during_execution_no_pair_receipt"));
            return;
        }
        var snapshots = Map.of("exposure", "exposure-source.tsv.source.yaml", "outcome", "outcome-source.tsv.source.yaml",
            "ld", "ld-source.tsv.source.yaml", "ld_alleles", "ld-alleles.tsv.source.yaml");
        for (var source : map(request.identity().get("inputs")).entrySet()) {
            Path metadata = directory.resolve(snapshots.get(source.getKey()));
            if (!Files.isRegularFile(metadata) || !map(source.getValue()).get("sha256").equals(StudyYaml.loadArtifact(metadata).get("sha256")))
                throw new IOException("Acquired genetic source snapshot differs from the registered pair input identity");
            String name = snapshots.get(source.getKey()).replace(".source.yaml", "");
            var copies = List.of(directory.resolve(name), directory.resolve(name.replace(".tsv", ".csv"))).stream().filter(Files::isRegularFile).toList();
            if (copies.size() != 1 || !map(source.getValue()).get("sha256").equals(FollowupSupport.hash(copies.get(0))))
                throw new IOException("Acquired genetic source bytes differ from the registered pair input identity");
        }
        var hashes = new TreeMap<String,Object>();
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList())
                hashes.put(directory.relativize(file).toString().replace('\\','/'), FollowupSupport.hash(file));
        }
        if (!hashes.isEmpty()) write(directory.resolve(RECEIPT), Map.of("key", request.key(), "identity", request.identity(),
            "result", result, "output_sha256", hashes));
    }

    Map<String,Object> summary(Context c) {
        return Map.of("resume_requested", c.resumeCompletedWork(), "reused_pairs", reused,
            "reused_pair_count", reused.size(), "new_pair_attempt_count", executed,
            "invalidated_derived_pairs", invalidated, "decisions", decisions,
            "scope", "Completed native pairs with current local or checksum-pinned sources and explicit LD/alleles. Automatic unversioned source/reference acquisition is executed normally. Waiting pairs are never reused. Family correction and coverage are recomputed.");
    }

    private void index(Context c) throws IOException {
        indexed = true; Path runs = c.project().resolve("runs");
        if (!Files.isDirectory(runs)) return;
        try (var paths = Files.walk(runs)) {
            for (Path manifestPath : paths.filter(p -> p.getFileName().toString().equals("manifest.yaml")).sorted().toList()) {
                Path directory = manifestPath.getParent();
                if (directory.startsWith(c.outputDirectory()) || directory.getFileName().toString().startsWith(".operation-")) continue;
                try {
                    var manifest = StudyYaml.loadArtifact(manifestPath);
                    if (!c.operation().get("type").equals(manifest.get("operation_type"))
                            || !c.operation().get("hypothesis").equals(manifest.get("hypothesis"))
                            || !Set.of("complete", "waiting_for_input").contains(text(manifest.get("state")))) continue;
                    var outputs = map(manifest.get("output_sha256"));
                    for (String name : outputs.keySet()) if (name.endsWith("/" + RECEIPT)) {
                        try {
                            var receipt = StudyYaml.loadArtifact(StudyFindingInventory.verified(directory, name, text(outputs.get(name))));
                            String key = text(receipt.get("key"));
                            if (key.matches("[a-f0-9]{64}")) prior.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Prior(directory, manifestPath, name, outputs));
                        } catch (IOException | RuntimeException invalid) {
                            invalidated.add(Map.of("source", relative(c, directory.resolve(name)), "disposition", "invalid_prior_pair_receipt_retained"));
                        }
                    }
                } catch (IOException | RuntimeException invalid) {
                    invalidated.add(Map.of("source", relative(c, manifestPath), "disposition", "unreadable_prior_manifest_retained"));
                }
            }
        }
    }

    private static String relative(Context c, Path path) { return c.project().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\','/'); }
    private static String required(Map<String,Object> values, String field) {
        String value = text(values.get(field));
        if (value.isBlank()) throw new IllegalArgumentException("Missing pair source " + field);
        return value;
    }
    private static String fingerprint(Object value) { return StudyFindingInventory.hash(StudyAssistance.json(ordered(value))); }
    private static Object ordered(Object value) {
        if (value instanceof Map<?,?> values) { var sorted = new TreeMap<String,Object>(); values.forEach((k,v) -> sorted.put(String.valueOf(k), ordered(v))); return sorted; }
        if (value instanceof List<?> values) return values.stream().map(StudyGeneticPairCache::ordered).toList();
        return value;
    }
    private static String software() throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (Class<?> type : List.of(StudyGeneticPairCache.class, jdistlib.Normal.class)) {
                Path location = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (Files.isRegularFile(location)) digest.update(FollowupSupport.hash(location).getBytes(StandardCharsets.UTF_8));
                else try (var paths = Files.walk(location)) {
                    for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                        digest.update(location.relativize(file).toString().getBytes(StandardCharsets.UTF_8)); digest.update(Files.readAllBytes(file));
                    }
                }
            }
            for (String property : List.of("java.version", "java.vendor", "java.vm.name", "os.arch"))
                digest.update(System.getProperty(property, "unknown").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.net.URISyntaxException | NoSuchAlgorithmException invalid) { throw new IOException("Cannot identify native genetic pair implementation", invalid); }
    }
}
