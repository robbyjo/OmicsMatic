/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

class PopsOriginalContractTest {
    @TempDir Path scratch;
    private Map<String, String> common() {
        return new TreeMap<>(Map.ofEntries(Map.entry("profile", "pops-original-v1"), Map.entry("edition", "0.2"), Map.entry("task", "munge"), Map.entry("runtime", "missing-runtime"), Map.entry("genome_build", "synthetic"), Map.entry("feature_set_id", "synthetic"), Map.entry("target_source_id", "not-biological"), Map.entry("java_heap_mb", "256"), Map.entry("python_address_space_mb", "1024"), Map.entry("wall_ms", "60000"), Map.entry("max_input_bytes", "1048576"), Map.entry("max_work_bytes", "8388608"), Map.entry("max_files", "1000"), Map.entry("gene_annotation", "annotation.tsv"), Map.entry("feature_directory", "features"), Map.entry("nan_policy", "raise")));
    }
    private PopsOriginalFiles.Config config(Map<String, String> values) throws Exception { Properties p = new Properties(); p.putAll(values); return PopsOriginalFiles.config(p); }
    private Path request(Map<String, String> values, String name) throws Exception {
        StringBuilder text = new StringBuilder(); values.forEach((k, v) -> text.append(k).append('=').append(v).append('\n'));
        Path path = scratch.resolve(name); Files.writeString(path, text); return path;
    }
    private record Run(int exit, String out, String err) { }
    private Run run(String... args) throws Exception {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream(); int exit;
        try (var so = new PrintStream(out, true, StandardCharsets.UTF_8); var se = new PrintStream(err, true, StandardCharsets.UTF_8)) { exit = PopsOriginalCli.run(args, so, se); }
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    @TestFactory List<DynamicTest> invalidRequestsAreRejectedBeforeRuntimeOrOutputCreation() {
        Map<String, String> invalid = Map.ofEntries(Map.entry("profile", "pops-native-v1"), Map.entry("edition", "latest"), Map.entry("task", "predict"), Map.entry("nan_policy", "repair"), Map.entry("chunk_columns", "0"), Map.entry("java_heap_mb", "0"), Map.entry("python_address_space_mb", "9223372036854775807"), Map.entry("wall_ms", "0"), Map.entry("max_input_bytes", "0"), Map.entry("max_work_bytes", "0"), Map.entry("max_files", "0"), Map.entry("max_features", "0"), Map.entry("max_identifier_chars", "0"), Map.entry("feature_set_id", "label\ncontinued"), Map.entry("method", "lasso"));
        List<DynamicTest> result = new ArrayList<>();
        invalid.forEach((key, value) -> result.add(DynamicTest.dynamicTest(key, () -> {
            Map<String, String> values = common(); values.put(key, value); Path req = request(values, key + ".properties"), out = scratch.resolve(key + "-out");
            Run r = run("--request", req.toString(), "--out", out.toString()); assertEquals(2, r.exit, r.err); assertFalse(Files.exists(out)); assertTrue(r.err.contains("FAIL2_ORIGINAL_POPS_UNADMITTED"));
        })));
        return result;
    }
    @Test void missingPermittedRuntimeIsSkippedWithoutCreatingOutput() throws Exception {
        Path req = request(common(), "missing.properties"), out = scratch.resolve("uncreated"); Run r = run("--request", req.toString(), "--out", out.toString());
        assertEquals(3, r.exit, r.err); assertTrue(r.err.contains("SKIP3_ORIGINAL_POPS_ANALYSIS_SKIPPED")); assertTrue(r.err.contains("skip")); assertFalse(Files.exists(out));
    }
    @Test void existingOutputAndMalformedBundleArePreserved() throws Exception {
        Path root = scratch.resolve("old"); Files.createDirectory(root); Path sentinel = root.resolve("retained"); Files.writeString(sentinel, "retained");
        Run a = run("--request", "unavailable", "--out", root.toString()); assertEquals(2, a.exit); try (var paths = Files.list(root)) { assertEquals(List.of(sentinel), paths.toList()); } assertEquals("retained", Files.readString(sentinel));
        Path fresh = scratch.resolve("fresh"); Run b = run("--verify", root.toString(), "--out", fresh.toString()); assertEquals(2, b.exit); assertFalse(Files.exists(fresh)); assertEquals("retained", Files.readString(sentinel));
    }
    @Test void duplicatePropertiesAndOmittedMissingValuePolicyHaveNoFallback() throws Exception {
        Map<String, String> values = common(); Path req = request(values, "duplicate.properties"); Files.writeString(req, Files.readString(req) + "edition=0.2\n");
        assertEquals(2, run("--request", req.toString(), "--out", scratch.resolve("a").toString()).exit);
        values.remove("nan_policy"); assertThrows(IllegalArgumentException.class, () -> config(values));
    }
    @Test void legacyTaskContractsPreserveSingleEmptyAndExplicitChromosomeInputs() throws Exception {
        Map<String, String> values = common(); for (String key : List.of("gene_annotation", "feature_directory", "nan_policy")) values.remove(key);
        values.putAll(Map.of("edition", "0.1", "task", "predict", "features_text", "features.tsv", "magma_prefix", "magma", "gene_location", "locations.tsv", "control_features", "controls.txt", "selected_features", "empty-is-allowed.txt", "chromosomes", "22,1"));
        assertEquals(List.of(22, 1), config(values).chromosomes());
        for (String bad : List.of("0", "23", "1,1", "1,", "none")) { values.put("chromosomes", bad); assertThrows(IllegalArgumentException.class, () -> config(values)); }
        values.put("chromosomes", "all"); assertEquals(22, config(values).chromosomes().size());
        values.put("task", "select-predict"); assertThrows(IllegalArgumentException.class, () -> config(values)); // Cannot silently ignore a supplied selection list.
    }
    @Test void featureCapturePreservesCompressedNamesAllVisibleFilesAndBytes() throws Exception {
        Path inputs = scratch.resolve("inputs-source"); Files.createDirectory(inputs); Path raw = inputs.resolve("raw"); Files.createDirectory(raw);
        try (var out = new GZIPOutputStream(Files.newOutputStream(inputs.resolve("annotation.tsv.gz")))) { out.write("ENSGID\nSYN_A\n".getBytes(StandardCharsets.UTF_8)); }
        Files.writeString(raw.resolve("b.tsv"), "ENSGID\tB\nSYN_A\t3\n"); Files.writeString(raw.resolve("a.tsv"), "ENSGID\tA\nSYN_A\t2\n"); Files.writeString(raw.resolve(".hidden"), "not an original glob member");
        var values = common(); values.put("gene_annotation", "annotation.tsv.gz"); values.put("feature_directory", "raw"); Path root = scratch.resolve("capture"); Files.createDirectories(root.resolve("inputs"));
        var c = PopsOriginalInputs.capture(inputs, config(values), root); PopsOriginalInputs.seal(root, c); PopsOriginalInputs.check(root, c);
        assertEquals("inputs/gene-annotation.tsv.gz", c.get("gene_annotation")); assertArrayEquals(Files.readAllBytes(inputs.resolve("annotation.tsv.gz")), Files.readAllBytes(root.resolve(c.get("gene_annotation"))));
        try (var paths = Files.list(root.resolve("inputs/raw-features"))) { assertEquals(Set.of("a.tsv", "b.tsv"), new HashSet<>(paths.map(p -> p.getFileName().toString()).toList())); }
        Files.writeString(root.resolve("inputs/raw-features/a.tsv"), "changed"); assertThrows(java.io.IOException.class, () -> PopsOriginalInputs.check(root, c));
        assertEquals("ENSGID\tA\nSYN_A\t2\n", Files.readString(raw.resolve("a.tsv")));
    }
    @Test void originalHelpIsAvailableThroughThePackagedDispatcher() throws Exception {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        try (var so = new PrintStream(out, true, StandardCharsets.UTF_8); var se = new PrintStream(err, true, StandardCharsets.UTF_8)) { assertEquals(0, JLinAlgCli.run(new String[]{"pops-original-tools", "--help"}, so, se)); }
        String help = out.toString(StandardCharsets.UTF_8); assertTrue(help.contains("DEVELOPMENT")); assertTrue(help.contains("ConvergenceWarning")); assertTrue(help.contains("SKIP3")); assertTrue(help.contains("POPS_ORIGINAL_WORKFLOWS.md"));
    }
    private Path containerFixture(String name, Map<String, String> markerOverrides) throws Exception {
        Path root = scratch.resolve(name); Files.createDirectories(root.resolve("inputs/raw-features")); Files.createDirectory(root.resolve("results")); Files.createDirectory(root.resolve("audit"));
        var values = common(); values.put("gene_annotation", "inputs/annotation.tsv"); values.put("feature_directory", "inputs/raw-features"); var c = config(values);
        Files.writeString(root.resolve("inputs/annotation.tsv"), "ENSGID\nSYN_A\n"); properties(root.resolve("effective.properties"), c.values()); PopsOriginalInputs.seal(root, c);
        Files.writeString(root.resolve("results/features.rows.txt"), "SYN_A\n"); Files.writeString(root.resolve("results/warnings.tsv"), "code\tcount\taction\n");
        properties(root.resolve("results/declarations.properties"), Map.of("profile", PopsOriginalFiles.PROFILE, "edition", "0.2", "task", "munge", "genome_build", "synthetic", "feature_set_id", "synthetic", "target_source_id", "not-biological", "original_source_modified", "false", "author_environment_reproduced", "false"));
        Files.writeString(root.resolve("audit/summary.json"), """
            {"profile":"original-pops-audit-v1","edition":"0.2","task":"munge",
             "numerical_model_source_modified":false,"models":[],"preparation_only":true,
             "nan_policy":"raise","prepared_genes":1,"prepared_features":0,"prepared_chunks":0,
             "remaining_nan_values":0,"remaining_infinite_values":0,"result_members":["features.rows.txt"]}
            """);
        Files.writeString(root.resolve("runtime-identity.tsv"), "fixture identity; no actual runtime\n");
        if ("genuine-original-recomputation".equals(markerOverrides.get("kind"))) Files.writeString(root.resolve("CAPTURE.tsv"), "source_bundle\tmanifest_sha256\n/retained/synthetic-parent\t" + "0".repeat(64) + "\n");
        properties(root.resolve("WORKER_DONE.properties"), Map.of("profile", PopsOriginalFiles.PROFILE, "edition", "0.2", "task", "munge", "source_jobs", "1", "explicit_convergence_warnings", "0"));
        var marker = new TreeMap<>(PopsOriginalResults.audit(root, c)); marker.put("kind", "original-task"); marker.put("runtime_identity_sha256", sha(root.resolve("runtime-identity.tsv"))); marker.put("parent_manifest_sha256", "none");
        manifest(root.resolve("FILES.tsv"), PopsOriginalFiles.inventory(root, c)); marker.put("manifest_sha256", sha(root.resolve("FILES.tsv"))); marker.putAll(markerOverrides); properties(root.resolve("COMPLETED.properties"), marker);
        return root;
    }
    private PopsOriginalFiles.Config checkContainer(Path root) throws Exception {
        var method = PopsOriginalCli.class.getDeclaredMethod("checkBundle", Path.class); method.setAccessible(true);
        try { return (PopsOriginalFiles.Config) method.invoke(null, root); }
        catch (java.lang.reflect.InvocationTargetException error) { if (error.getCause() instanceof Exception cause) throw cause; throw error; }
    }
    @Test void validPreparationContainerDeclaresNoFittedModel() throws Exception {
        Path root = containerFixture("valid-container", Map.of()); assertEquals("munge", checkContainer(root).get("task"));
    }
    @Test void replayParentMustMatchTheCapturedManifestIdentity() throws Exception {
        Path good = containerFixture("replay-good", Map.of("kind", "genuine-original-recomputation", "parent_manifest_sha256", "0".repeat(64))); assertEquals("munge", checkContainer(good).get("task"));
        Path bad = containerFixture("replay-wrong-parent", Map.of("kind", "genuine-original-recomputation", "parent_manifest_sha256", "1".repeat(64))); assertThrows(java.io.IOException.class, () -> checkContainer(bad));
    }
    @TestFactory List<DynamicTest> completionFieldsMustMatchTheAuditedTask() {
        var wrong = Map.of("edition", "0.1", "task", "fit", "models", "1", "prediction_genes", "1", "model_features", "1", "fit_performed", "true", "kind", "worker-only", "parent_manifest_sha256", "unverified-parent", "unexpected", "ignored-field");
        List<DynamicTest> result = new ArrayList<>(); wrong.forEach((key, value) -> result.add(DynamicTest.dynamicTest(key, () -> {
            Path root = containerFixture("wrong-" + key, Map.of(key, value)); assertThrows(java.io.IOException.class, () -> checkContainer(root), "Changed root declaration must be rejected before recomputation: " + key);
        }))); return result;
    }
}
