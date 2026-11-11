/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SnprelateAudit.map;

class GenesisRequestTest {
    @TempDir Path directory;
    private Map<String, Object> base() {
        return map("schema_version", 1, "version", "2.42.0", "mode", "null-model", "runtime_profile", "private/profile.json", "out", "new-attempt", "threads", 1, "layout", "snp-first",
            "inputs", map("phenotypes", "phenotypes.tsv", "covariances", List.of()),
            "options", map("outcome", "Y", "covariates", List.of("age"), "group_variable", null, "family", "gaussian", "two_stage", false,
                "normalisation", "all", "rescale", "residSD", "start", null, "tolerance", .0001, "max_iterations", 100, "EM_iterations", 0, "drop_zeros", true, "covariance_alignment", "phenotype-order"),
            "limits", map("input_bytes", 1000000, "output_bytes", 10000000, "output_files", 100, "samples", 100, "variants", 1000, "rows", 100000,
                "pair_variant_operations", 100000000, "working_bytes", 10000000, "wall_seconds", 60));
    }
    private GenesisRequest.Request parse(Map<String, Object> raw) throws IOException {
        return GenesisRequest.parse(NextflowParameters.parse(NextflowJson.json(raw), 1000000, 10000), directory);
    }
    private Map<String, Object> child(Map<String, Object> raw, String key) {
        var result = new LinkedHashMap<String, Object>(); for (var e : ((Map<?, ?>)raw.get(key)).entrySet()) result.put((String)e.getKey(), e.getValue()); raw.put(key, result); return result;
    }
    private Path inputs() throws IOException {
        Path root = directory.resolve("bundle"); Files.createDirectories(root.resolve("inputs"));
        Files.writeString(root.resolve("inputs/phenotypes.tsv"), "FID\tIID\tY\tage\n家族\tNaN\t3\t60\nF\tshared\t1\t40\n家族\tshared\t2\t50\n", StandardCharsets.UTF_8); return root;
    }
    private void covariance(Path root) throws IOException {
        Files.writeString(root.resolve("inputs/covariance-declared.samples.tsv"), "FID\tIID\n家族\tshared\nF\tshared\n家族\tNaN\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("inputs/covariance-declared.values.tsv"), "row\tcolumn\tvalue_hex\n1\t1\t1\n1\t2\t0.25\n1\t3\t0\n2\t1\t0.25\n2\t2\t2\n2\t3\t0\n3\t1\t0\n3\t2\t0\n3\t3\t3\n", StandardCharsets.UTF_8);
    }
    private void declaredCovariance(Map<String, Object> raw) {
        child(raw, "inputs").put("covariances", List.of(map("name", "declared", "values", "original.values.tsv", "samples", "original.samples.tsv")));
    }
    @Test void bindsExactVersionPathsTypedFamilyAndModel() throws Exception {
        var r = parse(base()); assertEquals("null-model", r.mode()); assertEquals("gaussian", r.model().family()); assertEquals(directory.resolve("phenotypes.tsv"), r.inputs().phenotypes());
        assertEquals(.0001, Double.parseDouble(GenesisRequest.parameters(r).get("tolerance"))); assertEquals("", GenesisRequest.parameters(r).get("covariance_names"));
    }
    @Test void refusesInferredVersionLayoutThreadsUnknownFlagsAndDuplicateJsonKeys() throws Exception {
        for (var pair : List.of(new Object[]{"version", "2.16.1"}, new Object[]{"mode", "PCA"}, new Object[]{"layout", "automatic"}, new Object[]{"threads", 2})) {
            var raw = base(); raw.put((String)pair[0], pair[1]); assertThrows(IOException.class, () -> parse(raw));
        }
        var raw = base(); raw.put("extra_flags", "--repair"); assertThrows(IOException.class, () -> parse(raw));
        String json = NextflowJson.json(base()); assertThrows(IOException.class, () -> NextflowParameters.parse("{\"mode\":\"null-model\"," + json.substring(1), 1000000, 10000));
    }
    @Test void refusesFormulaInjectionDuplicateColumnsNonfiniteToleranceAndNumericStrings() {
        for (var pair : List.of(new Object[]{"outcome", "Y+age"}, new Object[]{"covariates", List.of("age", "age")}, new Object[]{"tolerance", "NaN"}, new Object[]{"tolerance", "0.001"}, new Object[]{"family", "automatic"}, new Object[]{"max_iterations", 0})) {
            var raw = base(); child(raw, "options").put((String)pair[0], pair[1]); assertThrows(IOException.class, () -> parse(raw));
        }
    }
    @Test void preservesPhenotypeOrderUnicodeLiteralIdentifiersAndRepeatedIidAcrossFamilies() throws Exception {
        Path root = inputs(); var r = parse(base()); var d = GenesisInputs.read(root, r, () -> { });
        assertEquals(List.of(1, 2, 3), d.phenotypeAliases()); assertEquals("NaN", d.ids().get(0).iid()); assertEquals("家族", d.ids().get(0).fid());
        assertEquals(d.ids().get(1).iid(), d.ids().get(2).iid()); assertNotEquals(d.ids().get(1), d.ids().get(2));
        GenesisInputs.prepare(root, r, d, () -> { }); GenesisInputs.verifyTransport(root, r, d);
        assertTrue(Files.readString(root.resolve("transport/phenotypes.tsv")).startsWith("sample_alias\tY\tage\n1\t3\t60\n"));
    }
    @Test void derivedCovariancePermutationRequiresExplicitAlignmentAndPreservesOriginal() throws Exception {
        Path root = inputs(); covariance(root); var raw = base(); declaredCovariance(raw); var r = parse(raw); byte[] original = Files.readAllBytes(root.resolve("inputs/covariance-declared.values.tsv"));
        var d = GenesisInputs.read(root, r, () -> { }); assertEquals(List.of(3, 2, 1), d.covariances().get(0).aliases());
        GenesisInputs.prepare(root, r, d, () -> { }); GenesisInputs.verifyTransport(root, r, d);
        String transported = Files.readString(root.resolve("transport/covariance-declared.values.tsv")); assertTrue(transported.contains("1\t1\t0x1.8p1\n")); assertTrue(transported.contains("3\t3\t0x1.0p0\n"));
        assertArrayEquals(original, Files.readAllBytes(root.resolve("inputs/covariance-declared.values.tsv")));
        child(raw, "options").put("covariance_alignment", "require-order"); assertThrows(IOException.class, () -> GenesisInputs.read(root, parse(raw), () -> { }));
    }
    @Test void rejectsMissingNonfiniteDuplicateAndAsymmetricCovarianceWithoutCompletion() throws Exception {
        Path root = inputs(); covariance(root); var raw = base(); declaredCovariance(raw); Path values = root.resolve("inputs/covariance-declared.values.tsv"); String original = Files.readString(values);
        for (String broken : List.of(original.replace("3\t3\t3\n", ""), original.replace("1\t2\t0.25", "1\t2\tNaN"), original.replace("1\t2\t0.25", "1\t2\t0.2"), original.replace("1\t2\t0.25", "1\t1\t0.25"))) {
            Files.writeString(values, broken); assertThrows(IOException.class, () -> GenesisInputs.read(root, parse(raw), () -> { })); assertEquals(broken, Files.readString(values));
        }
    }
    @Test void rejectsDuplicateUnmappedParticipantsAndIncompletePhenotypes() throws Exception {
        Path root = inputs(); Path file = root.resolve("inputs/phenotypes.tsv"); String original = Files.readString(file);
        for (String broken : List.of(original.replace("F\tshared", "家族\tshared"), original.replace("\t3\t60", "\tNA\t60"), original.replace("\t1\t40", "\t1\tInf"), original.replace("家族\tNaN", "\tNaN"))) {
            Files.writeString(file, broken); assertThrows(IOException.class, () -> GenesisInputs.read(root, parse(base()), () -> { }));
        }
    }
    @Test void binaryAndPoissonProfilesRefuseInvalidOutcomeValues() throws Exception {
        Path root = inputs(); var raw = base(); child(raw, "options").put("family", "binomial"); assertThrows(IOException.class, () -> GenesisInputs.read(root, parse(raw), () -> { }));
        child(raw, "options").put("family", "poisson"); var d = GenesisInputs.read(root, parse(raw), () -> { }); assertEquals(3, d.ids().size());
        Path file = root.resolve("inputs/phenotypes.tsv"); Files.writeString(file, Files.readString(file).replace("\t3\t60", "\t-1\t60")); assertThrows(IOException.class, () -> GenesisInputs.read(root, parse(raw), () -> { }));
    }
    @Test void rejectsSavedTransportMutationWithoutRegeneration() throws Exception {
        Path root = inputs(); var r = parse(base()); var d = GenesisInputs.read(root, r, () -> { }); GenesisInputs.prepare(root, r, d, () -> { });
        Path file = root.resolve("transport/phenotypes.tsv"); String changed = Files.readString(file).replace("1\t3\t60", "1\t4\t60"); Files.writeString(file, changed);
        assertThrows(IOException.class, () -> GenesisInputs.verifyTransport(root, r, d)); assertEquals(changed, Files.readString(file));
    }
    @Test void readOnlyRunDoesNotAcceptAnExistingAttemptOrInferMissingRuntime() throws Exception {
        Path root = inputs(); var raw = base(); Path out = directory.resolve("new-attempt"); Files.createDirectory(out); Path request = directory.resolve("request.json"); Files.writeString(request, NextflowJson.json(raw));
        var stdout = new java.io.ByteArrayOutputStream(); var stderr = new java.io.ByteArrayOutputStream(); var printOut = new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8); var printErr = new java.io.PrintStream(stderr, true, StandardCharsets.UTF_8);
        assertEquals(2, GenesisToolsCli.run(new String[]{"--request", request.toString()}, printOut, printErr)); try (var members = Files.list(out)) { assertEquals(0, members.count()); }
        raw.put("out", "missing-runtime-attempt"); Files.writeString(request, NextflowJson.json(raw)); assertEquals(3, GenesisToolsCli.run(new String[]{"--request", request.toString()}, printOut, printErr));
        assertTrue(Files.readString(directory.resolve("missing-runtime-attempt/WARNINGS.md")).contains("SKIP3")); assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("REQUIRED ACTION")); assertTrue(Files.isDirectory(root));
    }
}
