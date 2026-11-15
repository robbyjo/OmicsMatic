/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.glm.Glm;
import org.jlinalg.glm.GlmFamilies;
import org.jlinalg.glm.GlmOptions;
import org.jlinalg.inference.AssociationStatistics;
import org.jlinalg.mixed.RandomEffectTerm;
import org.jlinalg.mixed.SparseLinearMixedModel;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;
import org.jlinalg.reml.RemlOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MappedOmicsAssociationTest {
    @TempDir Path directory;
    private static final int N = 96;
    private final double[] age = new double[N], gene = new double[N], protein = new double[N],
        isoform = new double[N], outcome = new double[N], binary = new double[N],
        weight = new double[N], offset = new double[N];
    private String messages;

    private void fixture(boolean missing) throws Exception {
        Random random = new Random(20261115);
        double[] groups = IntStream.range(0, 16).mapToDouble(i -> random.nextGaussian()).toArray();
        for (int i = 0; i < N; i++) {
            age[i] = random.nextGaussian(); gene[i] = random.nextGaussian();
            protein[i] = random.nextGaussian(); isoform[i] = .7 * gene[i] + random.nextGaussian();
            outcome[i] = .4 * age[i] + .8 * gene[i] - .3 * protein[i]
                + .6 * isoform[i] + groups[i / 6] + random.nextGaussian();
            binary[i] = random.nextDouble() < 1 / (1 + Math.exp(-.2 * age[i] - .3 * gene[i] - .4 * isoform[i])) ? 1 : 0;
            weight[i] = 1 + i % 3; offset[i] = .05 * i;
        }
        if (missing) {
            gene[7] = Double.NaN; isoform[11] = Double.NaN;
            age[13] = Double.NaN; protein[19] = Double.NaN;
        }
        StringBuilder pheno = new StringBuilder("id\ty\tbinary\tage\tgroup\tw\to\n");
        for (int i = N - 1; i >= 0; i--)
            pheno.append("S").append(i).append('\t').append(outcome[i]).append('\t').append((int) binary[i])
                .append('\t').append(age[i]).append("\tG").append(i / 6).append('\t').append(weight[i])
                .append('\t').append(offset[i]).append('\n');
        Files.writeString(directory.resolve("pheno.tsv"), pheno);
        matrix("isoforms.tsv", "tx1", isoform, false, -1);
        matrix("genes.tsv", "g1", gene, true, missing ? 3 : -1);
        matrix("proteins.tsv", "p1", protein, true, -1);
        Files.writeString(directory.resolve("mapping.tsv"),
            "feature_id\tsource\tadjustment_id\ntx1\tgenes\tg1\ntx1\tproteins\tp1\n");
    }

    private void matrix(String file, String id, double[] values, boolean reverse, int absent) throws Exception {
        StringBuilder header = new StringBuilder("feature_id"), row = new StringBuilder(id);
        for (int j = 0; j < N; j++) {
            int i = reverse ? N - 1 - j : j;
            if (i == absent) continue;
            header.append("\tS").append(i); row.append('\t').append(values[i]);
        }
        Files.writeString(directory.resolve(file), header + "\n" + row + "\n");
    }

    private int run(String output, String formula, String... extra) {
        List<String> args = new ArrayList<>(List.of("--pheno", directory.resolve("pheno.tsv").toString(),
            "--id", "id", "--omics", directory.resolve("isoforms.tsv").toString(),
            "--omics-type", "generic", "--formula", formula,
            "--adjust-omics", "genes=" + directory.resolve("genes.tsv"),
            "--adjust-omics", "proteins=" + directory.resolve("proteins.tsv"),
            "--adjust-map", directory.resolve("mapping.tsv").toString(),
            "--out", directory.resolve(output).toString(), "--backend", "cpu", "--threads", "2"));
        args.addAll(List.of(extra));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int status = JLinAlgCli.run(args.toArray(String[]::new), new PrintStream(bytes), new PrintStream(bytes));
        messages = bytes.toString();
        return status;
    }

    private int[] retained(boolean missing) {
        return IntStream.range(0, N).filter(i -> !missing || i != 3)
            .filter(i -> Double.isFinite(age[i]) && Double.isFinite(gene[i])
                && Double.isFinite(protein[i]) && Double.isFinite(isoform[i])).toArray();
    }

    private double[] selected(double[] values, int[] rows) {
        return Arrays.stream(rows).mapToDouble(i -> values[i]).toArray();
    }

    private double[] design(int[] rows, boolean reverse, boolean transformed) {
        double[] x = new double[rows.length * 5];
        for (int j = 0; j < rows.length; j++) {
            int i = rows[j];
            x[j * 5] = 1; x[j * 5 + 1] = age[i];
            x[j * 5 + 2] = transformed ? Math.log(gene[i] + 10) : gene[i];
            x[j * 5 + 3] = protein[i]; x[j * 5 + 4] = reverse ? outcome[i] : isoform[i];
        }
        return x;
    }

    private void assertEstimate(String file, AssociationStatistics stats, int n) throws Exception {
        DelimitedData result = DelimitedData.read(directory.resolve(file));
        String[] row = result.rows().get(0);
        assertEquals("ok", row[result.column("status")], messages);
        assertEquals(n, Integer.parseInt(row[result.column("n_samples")]));
        assertEquals("genes:g1;proteins:p1", row[result.column("adjustment_features")]);
        assertEquals(stats.beta()[4], Double.parseDouble(row[result.column("beta")]), 1e-7);
        assertEquals(stats.standardErrors()[4], Double.parseDouble(row[result.column("standard_error")]), 1e-7);
        assertEquals(stats.pValues()[4], Double.parseDouble(row[result.column("p_value")]), 1e-7);
        assertEquals(row[result.column("p_value")], row[result.column("fdr_bh")]);
    }

    @Test void multiSourceCompleteCasesAlignByIdAndMatchOffsetOls() throws Exception {
        fixture(true);
        assertEquals(0, run("weighted.tsv", "y ~ age + offset(o) + <omics>"), messages);
        int[] rows = retained(true);
        assertEstimate("weighted.tsv", Ols.fit(selected(outcome, rows), design(rows, false, false),
            rows.length, 5, null, selected(offset, rows), OlsOptions.defaults(),
            BackendPolicy.CPU).associationStatistics(), rows.length);
        String manifest = Files.readString(directory.resolve("weighted.tsv.manifest.json"));
        assertTrue(manifest.contains("per-feature-complete-cases"));
        assertTrue(manifest.contains("adjustment_input_sha256"));
        try (var paths = Files.list(directory)) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("omics-adjustment-")));
        }
    }

    @Test void reverseDirectionMatchesFullOmicsResponseFit() throws Exception {
        fixture(true);
        assertEquals(0, run("reverse.tsv", "y ~ age + <omics>", "--omics-response"), messages);
        int[] rows = retained(true);
        assertEstimate("reverse.tsv", Ols.fit(selected(isoform, rows), design(rows, true, false),
            rows.length, 5, OlsOptions.defaults(), BackendPolicy.CPU).associationStatistics(), rows.length);
    }

    @Test void independentAdjustmentTransformIsApplied() throws Exception {
        fixture(false);
        assertEquals(0, run("transform.tsv", "y ~ age + <omics>",
            "--adjust-transform", "genes=log(offset=10)"), messages);
        int[] rows = retained(false);
        assertEstimate("transform.tsv", Ols.fit(outcome, design(rows, false, true), N, 5,
            OlsOptions.defaults(), BackendPolicy.CPU).associationStatistics(), N);
    }

    @Test void binaryPhenotypeUsesFullAlternativeGlm() throws Exception {
        fixture(true);
        assertEquals(0, run("binary.tsv", "binary ~ age + <omics>",
            "--family", "binomial", "--model", "glm"), messages);
        int[] rows = retained(true);
        assertEstimate("binary.tsv", Glm.fit(selected(binary, rows), design(rows, false, false),
            rows.length, 5, GlmFamilies.binomial(), null, null, GlmOptions.defaults(),
            BackendPolicy.CPU).associationStatistics(), rows.length);
    }

    @Test void randomEffectRowsFollowFeatureCompleteCaseMask() throws Exception {
        fixture(true);
        assertEquals(0, run("mixed.tsv", "y ~ age + <omics> + (1|group)", "--model", "lmm"), messages);
        int[] rows = retained(true);
        var group = RandomEffectTerm.randomIntercept("group", Arrays.stream(rows).mapToObj(i -> "G" + i / 6).toList());
        var fit = SparseLinearMixedModel.fit(selected(outcome, rows), design(rows, false, false),
            rows.length, 5, List.of(group), RemlOptions.defaults(), BackendPolicy.CPU);
        assertTrue(fit.converged());
        assertEstimate("mixed.tsv", fit.associationStatistics(), rows.length);
    }

    @Test void failuresRemainOrderedAndAreNeverSilentlyUnadjusted() throws Exception {
        fixture(false);
        String values = "\t" + String.join("\t", Arrays.stream(isoform).mapToObj(Double::toString).toList()) + "\n";
        Files.writeString(directory.resolve("isoforms.tsv"), "unmapped" + values + "absent" + values
            + "collinear" + values, java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(directory.resolve("mapping.tsv"), "absent\tgenes\tunknown\ncollinear\tgenes\tg1\n"
            + "collinear\tproteins\tcopy\n", java.nio.file.StandardOpenOption.APPEND);
        String reversed = String.join("\t", IntStream.range(0, N).mapToObj(i -> Double.toString(isoform[N - 1 - i])).toList());
        Files.writeString(directory.resolve("proteins.tsv"), "copy\t" + reversed + "\n", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(0, run("failures.tsv", "y ~ age + <omics>", "--block-size", "2"), messages);
        DelimitedData result = DelimitedData.read(directory.resolve("failures.tsv"));
        assertEquals(List.of("tx1", "unmapped", "absent", "collinear"), result.rows().stream().map(r -> r[result.column("id")]).toList());
        for (int i = 1; i < 4; i++) {
            String[] row = result.rows().get(i);
            assertEquals("failed", row[result.column("status")]);
            assertFalse(row[result.column("failure_reason")].isBlank());
            assertTrue(row[result.column("p_value")].isBlank());
            assertTrue(row[result.column("fdr_bh")].isBlank());
        }
    }

    @Test void entirelyFailedScanDoesNotPublishSuccess() throws Exception {
        fixture(false);
        Files.writeString(directory.resolve("mapping.tsv"), "feature_id\tsource\tadjustment_id\ntx1\tgenes\tmissing\ntx1\tproteins\tp1\n");
        assertNotEquals(0, run("all-fail.tsv", "y ~ age + <omics>"));
        assertTrue(messages.contains("all adjusted feature models failed"), messages);
        assertFalse(Files.exists(directory.resolve("all-fail.tsv")));
        assertTrue(Files.exists(directory.resolve("all-fail.tsv.partial")));
    }

    @Test void missingnessFromOtherFeaturesAdjustmentSourcesDoesNotShrinkTheModel() throws Exception {
        fixture(true);
        String second = Files.readAllLines(directory.resolve("isoforms.tsv")).get(1).replaceFirst("tx1", "tx2");
        Files.writeString(directory.resolve("isoforms.tsv"), second + "\n", java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(directory.resolve("mapping.tsv"),
            "feature_id\tsource\tadjustment_id\ntx1\tgenes\tg1\ntx2\tproteins\tp1\n");
        assertEquals(0, run("masks.tsv", "y ~ age + <omics>"), messages);
        DelimitedData result = DelimitedData.read(directory.resolve("masks.tsv"));
        assertEquals("92", result.rows().get(0)[result.column("n_samples")]);
        assertEquals("93", result.rows().get(1)[result.column("n_samples")]);
        assertEquals("genes:g1", result.rows().get(0)[result.column("adjustment_features")]);
        assertEquals("proteins:p1", result.rows().get(1)[result.column("adjustment_features")]);
    }

    @Test void rejectsDuplicateMappingsAndDuplicateSourceRows() throws Exception {
        fixture(false);
        Files.writeString(directory.resolve("mapping.tsv"), "tx1\tgenes\tg1\n", java.nio.file.StandardOpenOption.APPEND);
        assertNotEquals(0, run("duplicates.tsv", "y ~ age + <omics>"));
        assertTrue(messages.contains("duplicate adjustment mapping"), messages);
        fixture(false);
        String geneRow = Files.readAllLines(directory.resolve("genes.tsv")).get(1);
        Files.writeString(directory.resolve("genes.tsv"), geneRow + "\n", java.nio.file.StandardOpenOption.APPEND);
        assertNotEquals(0, run("duplicate-rows.tsv", "y ~ age + <omics>"));
        assertTrue(messages.contains("duplicate adjustment feature"), messages);
    }

    @Test void protectsAdjustmentInputsAndRejectsIncompleteOptions() throws Exception {
        fixture(false);
        String original = Files.readString(directory.resolve("genes.tsv"));
        assertEquals(2, run("genes.tsv", "y ~ age + <omics>", "--overwrite"));
        assertTrue(messages.contains("aliases an input"), messages);
        assertEquals(original, Files.readString(directory.resolve("genes.tsv")));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {
            "--adjust-omics", "genes=data.tsv"}).validateForRun());
        assertEquals(2, run("bad-source.tsv", "y ~ age + <omics>", "--adjust-transform", "unknown=zscore()"));
        assertTrue(messages.contains("unknown adjustment source"), messages);
    }

    @Test void dryRunChecksMappingsAndLeavesNoResultsOrCache() throws Exception {
        fixture(false);
        assertEquals(0, run("dry.tsv", "y ~ age + <omics>", "--dry-run"), messages);
        assertFalse(Files.exists(directory.resolve("dry.tsv")));
        try (var paths = Files.list(directory)) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("omics-adjustment-")));
        }
    }

    @Test void agreesWithIndependentRFullModelReferences() throws Exception {
        for (String file : List.of("phenotype.tsv", "isoforms.tsv", "genes.tsv", "proteins.tsv", "mapping.tsv"))
            try (var input = getClass().getResourceAsStream("/mapped-omics/" + file)) {
                assertNotNull(input);
                Files.copy(input, directory.resolve(file.equals("phenotype.tsv") ? "pheno.tsv" : file));
            }
        Path expectedPath = directory.resolve("expected.tsv");
        try (var input = getClass().getResourceAsStream("/mapped-omics/expected-r.tsv")) {
            assertNotNull(input); Files.copy(input, expectedPath);
        }
        DelimitedData expected = DelimitedData.read(expectedPath);
        for (String model : List.of("ols", "reverse", "binomial")) {
            String response = model.equals("binomial") ? "case" : "BMI";
            String[] extra = model.equals("binomial") ? new String[] {"--family", "binomial"}
                : model.equals("reverse") ? new String[] {"--omics-response"} : new String[0];
            assertEquals(0, run(model + ".tsv", response + " ~ age + <omics>", extra), messages);
            DelimitedData actual = DelimitedData.read(directory.resolve(model + ".tsv"));
            for (String[] row : expected.rows()) {
                if (!row[expected.column("model")].equals(model)) continue;
                String id = row[expected.column("feature_id")];
                String[] observed = actual.rows().stream().filter(r -> r[actual.column("id")].equals(id)).findFirst().orElseThrow();
                assertEquals("ok", observed[actual.column("status")]);
                assertEquals(row[expected.column("n")], observed[actual.column("n_samples")]);
                String[][] columns = {{"beta", "beta"}, {"se", "standard_error"},
                    {"statistic", "statistic"}, {"p", "p_value"}};
                for (String[] names : columns)
                    assertEquals(Double.parseDouble(row[expected.column(names[0])]),
                        Double.parseDouble(observed[actual.column(names[1])]), 2e-6, model + ":" + id + ":" + names[0]);
                if (!model.equals("binomial"))
                    assertEquals(Double.parseDouble(row[expected.column("df")]),
                        Double.parseDouble(observed[actual.column("df_denominator")]));
            }
        }
    }
}
