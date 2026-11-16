/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.cli.PopsWorkflowFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class PopsWorkflowCliTest {
    @TempDir Path scratch;
    private record Run(int exit, String out, String err) { }
    private Run run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int exit = PopsWorkflowCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    private Path request(String name, String suffix, Map<String, String> overrides) throws Exception {
        Path directory = scratch.resolve(suffix); Files.createDirectory(directory); var all = PopsWorkflowNpyTest.fixture(); String prefix = name + "/";
        for (var entry : all.entrySet()) if (entry.getKey().startsWith(prefix)) Files.write(directory.resolve(entry.getKey().substring(prefix.length())), entry.getValue());
        Properties original = load(directory.resolve("params.properties")); Map<String, String> c = new TreeMap<>();
        c.putAll(Map.of("profile", PROFILE, "edition", "0.2", "method", original.getProperty("method"), "target_type", original.getProperty("magma").equals("true") ? "magma" : "custom", "gene_annotation", "annotation.tsv", "feature_prefix", "features", "feature_chunks", "1"));
        c.putAll(Map.of("genome_build", "synthetic-control", "feature_set_id", "synthetic-original-reference", "target_source_id", name, "java_heap_mb", "512", "wall_ms", "120000", "max_input_bytes", "10000000", "max_work_bytes", "100000000", "max_files", "500", "batch_columns", "3"));
        c.put("p_cutoff", original.getProperty("cutoff")); c.put("max_selected_by_p", original.getProperty("maximum")); c.put("forward_steps", original.getProperty("forward"));
        if (c.get("target_type").equals("magma")) c.put("magma_prefix", "magma");
        else {
            c.put("scores", "scores.tsv"); if (Files.exists(directory.resolve("score-covariates.tsv"))) c.put("score_covariates", "score-covariates.tsv");
            if (Files.exists(directory.resolve("covariance.npy"))) c.put("error_covariance", "covariance.npy");
            else if (Files.exists(directory.resolve("covariance.npz"))) c.put("error_covariance", "covariance.npz");
            else if (Files.exists(directory.resolve("error-covariance.npz"))) c.put("error_covariance", "error-covariance.npz");
        }
        if (Files.exists(directory.resolve("subset.txt"))) c.put("subset_features", "subset.txt"); if (Files.exists(directory.resolve("controls.txt"))) c.put("control_features", "controls.txt");
        if (Files.readString(directory.resolve("PROCESS.json")).contains("--project_out_covariates_chromosomes")) { c.put("projection_chromosomes", "1,6"); c.put("selection_chromosomes", "1,2"); c.put("training_chromosomes", "2,6"); }
        c.putAll(overrides); Path request = directory.resolve("request.properties"); properties(request, c); return request;
    }
    private static List<String[]> rows(Path path) throws IOException { return Files.readAllLines(path).stream().map(s -> s.split("\t", -1)).toList(); }
    private static double value(String x) { return x.isEmpty() || x.equals("nan") ? Double.NaN : Double.parseDouble(x); }
    private static void number(double expected, double actual) { if (Double.isNaN(expected)) assertTrue(Double.isNaN(actual)); else assertEquals(expected, actual, 5e-8 * Math.max(1, Math.abs(expected))); }
    private static void compareOriginal(Path source, Path bundle) throws Exception {
        var original = rows(source.resolve("original.preds")); var actual = rows(bundle.resolve("results/predictions.tsv"));
        assertEquals(original.size(), actual.size()); int id = Arrays.asList(original.get(0)).indexOf("ENSGID"), score = Arrays.asList(original.get(0)).indexOf("PoPS_Score");
        for (int i = 1; i < original.size(); i++) { assertEquals(original.get(i)[id], actual.get(i)[0]); number(value(original.get(i)[score]), value(actual.get(i)[1])); }
        var om = rows(source.resolve("original.marginals")); var am = rows(bundle.resolve("results/marginals.tsv")); assertEquals(om.size(), am.size());
        for (int i = 1; i < om.size(); i++) { assertEquals(om.get(i)[0], am.get(i)[0]); for (int j = 1; j <= 4; j++) number(value(om.get(i)[j]), value(am.get(i)[j])); assertEquals(om.get(i)[5].equals("True"), am.get(i)[5].equals("1")); }
        Map<String, Double> old = new TreeMap<>(); for (String[] row : rows(source.resolve("original.coefs"))) if (row[0].startsWith("F")) old.put(row[0], value(row[1]));
        for (String[] row : rows(bundle.resolve("results/coefficients.tsv")).subList(1, am.size())) number(old.getOrDefault(row[0], 0.0), value(row[1]));
    }
    @TestFactory List<DynamicTest> completeOriginalCasesAndFormatControlsWithGenuineReplay() throws Exception {
        var files = PopsWorkflowNpyTest.fixture(); Object parsed = SimpleJson.parse(new String(files.get("CASES.json"), StandardCharsets.UTF_8)); assertInstanceOf(List.class, parsed);
        List<?> names = (List<?>) parsed; assertEquals(19, names.size()); List<DynamicTest> tests = new ArrayList<>();
        for (Object item : names) {
            String name = (String) item; tests.add(DynamicTest.dynamicTest(name, () -> {
                Path req = request(name, name, Map.of()); Path bundle = scratch.resolve(name + "-analysis"); Run result = run("--request", req.toString(), "--out", bundle.toString());
                if (name.startsWith("failure-")) { assertEquals(2, result.exit, result.err); assertFalse(Files.exists(bundle.resolve("COMPLETED.properties"))); assertTrue(Files.isRegularFile(bundle.resolve("FAILURE.txt"))); return; }
                assertEquals(0, result.exit, result.err); assertTrue(Files.isRegularFile(bundle.resolve("COMPLETED.properties"))); compareOriginal(req.getParent(), bundle);
                Files.writeString(req.getParent().resolve("annotation.tsv"), "Original input deliberately unavailable after capture\n");
                Path replay = scratch.resolve(name + "-replay"); Run verification = run("--verify", bundle.toString(), "--out", replay.toString()); assertEquals(0, verification.exit, verification.err);
                assertEquals(inventory(bundle.resolve("results"), config(load(bundle.resolve("effective.properties")))), inventory(replay.resolve("results"), config(load(replay.resolve("effective.properties")))));
                assertTrue(Files.isRegularFile(replay.resolve("worker-command.txt"))); assertEquals("0", load(replay.resolve("worker-process.properties")).getProperty("exit"));
                if (name.equals("format-cross-chromosome")) { assertTrue(result.err.contains("custom_covariance_cross_chromosome_nonfinite_ignored=1")); assertTrue(Files.readString(bundle.resolve("WARNINGS.md")).contains("Analysis-specific warnings")); }
            }));
        }
        return tests;
    }
    @Test void forwardSelectionIgnoresAbsentSubsetAndControlNames() throws Exception {
        Path req = request("ridge-fss-overrides", "forward", Map.of()); Files.writeString(req.getParent().resolve("subset.txt"), "ABSENT\n"); Files.writeString(req.getParent().resolve("controls.txt"), "ABSENT\n");
        Path bundle = scratch.resolve("out"); Run result = run("--request", req.toString(), "--out", bundle.toString()); assertEquals(0, result.exit, result.err); compareOriginal(req.getParent(), bundle);
        assertTrue(result.err.contains("feature_selection_files_ignored_by_forward_selection=2"));
    }
    @Test void timeoutCapacityAndExistingOutputAreUnadmittedAndPreserved() throws Exception {
        Path timeout = request("ridge-plain", "timeout-inputs", Map.of("wall_ms", "1")); Path a = scratch.resolve("timeout"); Run t = run("--request", timeout.toString(), "--out", a.toString());
        assertEquals(2, t.exit); assertFalse(Files.exists(a.resolve("COMPLETED.properties"))); assertTrue(t.err.contains("wall_ms"));
        var config = config(load(timeout)); var before = inventory(a, config); Run again = run("--request", timeout.toString(), "--out", a.toString()); assertEquals(2, again.exit); assertEquals(before, inventory(a, config));
        Path capacity = request("ridge-wide", "capacity-inputs", Map.of("max_selected_features", "1")); Path b = scratch.resolve("capacity"); Run cap = run("--request", capacity.toString(), "--out", b.toString()); assertEquals(2, cap.exit, cap.err); assertFalse(Files.exists(b.resolve("COMPLETED.properties"))); assertTrue(cap.err.contains("Selected features exceed declared capacity"), cap.err);
    }
    @Test void missingJavaIsSkip3WithRequiredAction() throws Exception {
        Path req = request("ridge-plain", "inputs", Map.of()); Path bundle = scratch.resolve("out"); String original = System.getProperty("java.home"); Run result;
        try { System.setProperty("java.home", scratch.resolve("no-java").toString()); result = run("--request", req.toString(), "--out", bundle.toString()); }
        finally { System.setProperty("java.home", original); }
        assertEquals(3, result.exit, result.err); assertTrue(result.err.contains("SKIP3")); assertTrue(result.err.contains("permitted Java")); assertFalse(Files.exists(bundle.resolve("worker-command.txt"))); assertTrue(Files.readString(bundle.resolve("WARNINGS.md")).contains("dependent analysis skipped"));
    }
    @Test void changedMembershipIsRejectedAndResealedWrongScoresRequireAGenuineFailedRefit() throws Exception {
        Path req = request("ridge-plain", "inputs", Map.of()); Path bundle = scratch.resolve("bundle"); assertEquals(0, run("--request", req.toString(), "--out", bundle.toString()).exit);
        Path added = scratch.resolve("added"); clone(bundle, added); Files.writeString(added.resolve("unexpected.txt"), "unqualified extra\n"); Path a = scratch.resolve("rejected-extra"); assertEquals(2, run("--verify", added.toString(), "--out", a.toString()).exit); assertFalse(Files.exists(a.resolve("worker-command.txt")));
        Path wrong = scratch.resolve("wrong"); clone(bundle, wrong); Path predictions = wrong.resolve("results/predictions.tsv"); var rows = new ArrayList<>(Files.readAllLines(predictions)); String[] fields = rows.get(1).split("\t", -1); fields[1] = Double.toString(Double.parseDouble(fields[1]) + 100); rows.set(1, String.join("\t", fields)); Files.writeString(predictions, String.join("\n", rows) + "\n");
        var c = config(load(wrong.resolve("effective.properties"))); Map<String, String> entries = inventory(wrong, c); entries.remove("FILES.tsv"); entries.remove("COMPLETED.properties");
        StringBuilder text = new StringBuilder("path\tbytes\tsha256\n"); for (var e : entries.entrySet()) text.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        String previous = sha(wrong.resolve("FILES.tsv")); Files.writeString(wrong.resolve("FILES.tsv"), text); Path marker = wrong.resolve("COMPLETED.properties"); Files.writeString(marker, Files.readString(marker).replace(previous, sha(wrong.resolve("FILES.tsv"))));
        Path b = scratch.resolve("genuine-mismatch"); Run verification = run("--verify", wrong.toString(), "--out", b.toString()); assertEquals(2, verification.exit, verification.err); assertTrue(verification.err.contains("Genuine refit")); assertEquals("0", load(b.resolve("worker-process.properties")).getProperty("exit")); assertFalse(Files.exists(b.resolve("COMPLETED.properties")));
    }
    @Test void outputRecordingFailureInvalidatesAnOtherwiseSuccessfulModel() throws Exception {
        Path req = request("ridge-plain", "inputs", Map.of()); Path bundle = scratch.resolve("out");
        var failed = new PrintStream(new java.io.OutputStream() { @Override public void write(int value) throws IOException { throw new IOException("deliberate output-recording control"); } });
        var err = new ByteArrayOutputStream(); int exit = PopsWorkflowCli.run(new String[] {"--request", req.toString(), "--out", bundle.toString()}, failed, new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(2, exit, err.toString(StandardCharsets.UTF_8)); assertFalse(Files.exists(bundle.resolve("COMPLETED.properties"))); assertTrue(Files.readString(bundle.resolve("FAILURE.txt")).contains("Success output recording failed"));
        try (var paths = Files.list(bundle)) { assertTrue(paths.anyMatch(p -> p.getFileName().toString().startsWith("INVALIDATED-"))); }
    }
    @Test void normalDispatchReachesTheExplicitProfileAndRejectsOtherEditions() throws Exception {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        assertEquals(0, JLinAlgCli.run(new String[] {"pops-tools", "--help"}, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8)));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains(PROFILE) || out.toString(StandardCharsets.UTF_8).contains("PoPS native prepared workflow"));
        Path req = request("ridge-plain", "wrong-edition-inputs", Map.of("edition", "0.1")); Path bundle = scratch.resolve("wrong-edition");
        Run result = run("--request", req.toString(), "--out", bundle.toString()); assertEquals(2, result.exit); assertTrue(result.err.contains("Do not substitute")); assertFalse(Files.exists(bundle.resolve("worker-command.txt")));
    }
    private static void clone(Path from, Path to) throws IOException {
        try (var paths = Files.walk(from)) { for (Path p : paths.toList()) { Path out = to.resolve(from.relativize(p)); if (Files.isDirectory(p)) Files.createDirectory(out); else Files.copy(p, out); } }
    }
}
