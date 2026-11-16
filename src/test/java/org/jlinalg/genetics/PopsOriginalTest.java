/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class PopsOriginalTest {
    private static Map<String, byte[]> fixture() throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        try (var raw = PopsOriginalTest.class.getResourceAsStream("pops-original-0.2.zip")) {
            assertNotNull(raw, "actual original PoPS control archive");
            try (var zip = new ZipInputStream(raw)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertFalse(entry.isDirectory());
                    assertNull(files.put(entry.getName(), zip.readAllBytes()), "duplicate fixture member");
                }
            }
        }
        try (var raw = PopsOriginalTest.class.getResourceAsStream("pops-original-ridge-cv.zip")) {
            assertNotNull(raw, "actual original RidgeCV observation archive");
            try (var zip = new ZipInputStream(raw)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    assertNull(files.put(entry.getName(), zip.readAllBytes()), "duplicate CV fixture member");
            }
        }
        return files;
    }

    private static String text(Map<String, byte[]> files, String key) {
        assertTrue(files.containsKey(key), key); return new String(files.get(key), StandardCharsets.UTF_8);
    }
    private static double number(String value) {
        return switch (value) { case "", "nan", "NaN" -> Double.NaN; case "inf" -> Double.POSITIVE_INFINITY;
            case "-inf" -> Double.NEGATIVE_INFINITY; default -> Double.parseDouble(value); };
    }
    private static double[][] matrix(String text) {
        return text.lines().filter(s -> !s.isBlank()).map(s -> Arrays.stream(s.split("\t", -1)).mapToDouble(PopsOriginalTest::number).toArray()).toArray(double[][]::new);
    }
    private static int[] indices(String text) {
        return text.equals("none") ? null : Arrays.stream(text.split(",")).mapToInt(Integer::parseInt).toArray();
    }
    private static Integer optional(String text) { return text.equals("none") ? null : Integer.valueOf(text); }
    private static void close(double expected, double actual, String context) {
        if (Double.isNaN(expected)) assertTrue(Double.isNaN(actual), context);
        else if (!Double.isFinite(expected)) assertEquals(expected, actual, context);
        else assertEquals(expected, actual, 5e-8 * (1 + Math.abs(expected)), context);
    }

    @TestFactory List<DynamicTest> originalCliCases() throws Exception {
        var files = fixture(); var tests = new ArrayList<DynamicTest>();
        for (String key : files.keySet()) if (key.endsWith("/params.properties")) {
            String prefix = key.substring(0, key.length() - "params.properties".length());
            tests.add(DynamicTest.dynamicTest(prefix.substring(0, prefix.length() - 1), () -> checkCase(files, prefix)));
        }
        assertEquals(13, tests.size()); return tests;
    }

    private static void checkCase(Map<String, byte[]> files, String prefix) throws Exception {
        Properties config = new Properties(); config.load(new StringReader(text(files, prefix + "params.properties")));
        double[][] rawY = matrix(text(files, prefix + "target.tsv")), x = matrix(text(files, prefix + "features.tsv"));
        double[] y = Arrays.stream(rawY).mapToDouble(v -> v[0]).toArray();
        double[][] rawMasks = matrix(text(files, prefix + "masks.tsv"));
        boolean[][] masks = new boolean[3][y.length];
        for (int i = 0; i < y.length; i++) for (int j = 0; j < 3; j++) masks[j][i] = rawMasks[i][j] == 1;
        String[][] annotation = text(files, prefix + "annotation.tsv").lines().skip(1).map(s -> s.split("\t")).toArray(String[][]::new);
        double[][] covariates = null;
        if (Boolean.parseBoolean(config.getProperty("magma"))) {
            double[][] meta = matrix(text(files, prefix + "magma-metadata.tsv"));
            covariates = Pops.magmaCovariates(Arrays.stream(meta).mapToDouble(v -> v[0]).toArray(),
                Arrays.stream(meta).mapToDouble(v -> v[1]).toArray(), Arrays.stream(meta).mapToDouble(v -> v[2]).toArray());
        } else if (files.containsKey(prefix + "covariates.tsv")) covariates = matrix(text(files, prefix + "covariates.tsv"));
        PopsCovariance covariance = null;
        if (Boolean.parseBoolean(config.getProperty("covariance"))) {
            double[][] dense = matrix(text(files, prefix + "covariance.tsv"));
            Map<String, List<Integer>> groups = new LinkedHashMap<>();
            for (int i = 0; i < annotation.length; i++) groups.computeIfAbsent(annotation[i][1], k -> new ArrayList<>()).add(i);
            var blocks = new ArrayList<PopsCovariance.Block>();
            for (var group : groups.values()) {
                int[] rows = group.stream().mapToInt(Integer::intValue).toArray(); double[][] values = new double[rows.length][rows.length];
                for (int i = 0; i < rows.length; i++) for (int j = 0; j < rows.length; j++) values[i][j] = dense[rows[i]][rows[j]];
                blocks.add(new PopsCovariance.Block(rows, values));
            }
            covariance = new PopsCovariance(y.length, blocks);
            if (Boolean.parseBoolean(config.getProperty("magma"))) {
                try (var context = BackendContext.select(BackendPolicy.CPU)) { covariance = covariance.regularizeMagma(y, context.backend()).covariance(); }
            }
        }
        int[] controlIndices = indices(config.getProperty("controls")); boolean[] controls = null;
        if (controlIndices != null) { controls = new boolean[x[0].length]; for (int i : controlIndices) controls[i] = true; }
        var selection = new Pops.Selection(Double.valueOf(config.getProperty("cutoff")), optional(config.getProperty("maximum")),
            indices(config.getProperty("subset")), controls, optional(config.getProperty("forward")));
        var method = config.getProperty("method").equals("ridge") ? Pops.Method.RIDGE : Pops.Method.MINIMUM_NORM_OLS;
        final double[][] cov = covariates; final PopsCovariance error = covariance;
        if (Boolean.parseBoolean(config.getProperty("expect_failure"))) {
            assertThrows(RuntimeException.class, () -> Pops.fit(y, x, cov, error, masks[0], masks[1], masks[2], selection, method, BackendPolicy.CPU));
            assertFalse(files.containsKey(prefix + "original.preds"), "original must also admit no prediction output");
            return;
        }
        var actual = Pops.fit(y, x, cov, error, masks[0], masks[1], masks[2], selection, method, BackendPolicy.CPU);
        String[][] predicted = text(files, prefix + "original.preds").lines().map(s -> s.split("\t", -1)).toArray(String[][]::new);
        List<String> predictionColumns = Arrays.asList(predicted[0]);
        assertEquals(y.length + 1, predicted.length);
        double[] predictions = actual.predictions(), projected = actual.projectedTarget();
        for (int i = 0; i < y.length; i++) {
            assertEquals(annotation[i][0], predicted[i + 1][0]);
            close(number(predicted[i + 1][predictionColumns.indexOf("PoPS_Score")]), predictions[i], prefix + "prediction " + i);
            if (predictionColumns.contains("Y_proj")) close(number(predicted[i + 1][predictionColumns.indexOf("Y_proj")]), projected[i], prefix + "Y projection " + i);
            for (int j = 1; j < 3; j++) {
                String column = j == 1 ? "feature_selection_gene" : "training_gene";
                assertEquals(masks[j][i], Boolean.parseBoolean(predicted[i + 1][predictionColumns.indexOf(column)]), column);
            }
        }
        String[][] marginalRows = text(files, prefix + "original.marginals").lines().map(s -> s.split("\t", -1)).toArray(String[][]::new);
        List<String> marginalColumns = Arrays.asList(marginalRows[0]);
        boolean[] selected = new boolean[x[0].length]; for (int j : actual.selectedFeatures()) selected[j] = true;
        Pops.Marginal[] marginals = actual.marginals(); int[] ranks = actual.forwardSelectionRanks();
        for (int j = 0; j < marginals.length; j++) {
            assertEquals(String.format("F%03d", j), marginalRows[j + 1][0]);
            double[] values = {marginals[j].beta(), marginals[j].standardError(), marginals[j].pValue(), marginals[j].rSquared()};
            for (int k = 0; k < 4; k++) close(number(marginalRows[j + 1][k + 1]), values[k], prefix + "marginal " + j + "/" + k);
            assertEquals(Boolean.parseBoolean(marginalRows[j + 1][marginalColumns.indexOf("selected")]), selected[j], prefix + "selected " + j);
            if (marginalColumns.contains("selection_rank")) {
                double rank = number(marginalRows[j + 1][marginalColumns.indexOf("selection_rank")]);
                assertEquals(Double.isNaN(rank) ? 0 : (int) rank, ranks[j], prefix + "FSS rank " + j);
            }
        }
        Map<String, String> coefficients = new LinkedHashMap<>();
        text(files, prefix + "original.coefs").lines().skip(1).forEach(line -> { String[] fields = line.split("\t", -1); coefficients.put(fields[0], fields[1]); });
        for (int j = 0; j < x[0].length; j++) {
            String name = String.format("F%03d", j); assertEquals(selected[j], coefficients.containsKey(name), name);
            close(selected[j] ? number(coefficients.get(name)) : 0, actual.coefficients()[j], prefix + "coefficient " + j);
        }
        if (method == Pops.Method.RIDGE) {
            assertEquals(number(coefficients.get("SELECTED_CV_ALPHA")), actual.selectedAlpha(), prefix + "CV alpha");
            close(number(coefficients.get("BEST_CV_SCORE")), actual.bestCvScore(), prefix + "best CV score");
            double[] expectedScores = text(files, prefix + "original.cv.tsv").lines().mapToDouble(PopsOriginalTest::number).toArray();
            assertEquals(25, expectedScores.length);
            double[] actualScores = actual.cvScores();
            for (int i = 0; i < 25; i++) close(expectedScores[i], actualScores[i], prefix + "LOO CV score " + i);
        }
        assertArrayEquals(actual.predictions(), actual.predict(x), 0, "prediction uses original feature values");
    }

    @Test void allFiftyOriginalPandasAndNumpySortsIncludeTiesAndNan() throws Exception {
        var files = fixture(); int count = 0;
        for (String key : files.keySet()) if (key.startsWith("sort/") && key.endsWith(".values")) {
            double[] values = text(files, key).lines().filter(s -> !s.isBlank()).mapToDouble(PopsOriginalTest::number).toArray();
            int[] expected = text(files, key.substring(0, key.length() - 7) + ".order").lines().filter(s -> !s.isBlank()).mapToInt(Integer::parseInt).toArray();
            assertArrayEquals(expected, PopsOrder.argsort(values), key); count++;
        }
        assertEquals(50, count);
    }

    @Test void hlaBoundariesRequireTheOriginalChromosomeString() {
        assertArrayEquals(new boolean[] {true, false, false, false, true, true}, Pops.geneMask(
            new String[] {"6", "6", "6", "6", "6", "chr6"},
            new long[] {19999999, 20000000, 30000000, 40000000, 40000001, 30000000}, Set.of("6", "chr6"), true));
    }

    @Test void covarianceIsImmutableAndRequiresACompleteSymmetricPartition() {
        int[] rows = {0, 1}; double[][] values = {{1, .2}, {.2, 1}};
        var block = new PopsCovariance.Block(rows, values); var covariance = new PopsCovariance(2, List.of(block));
        rows[0] = 1; values[0][1] = 9; block.indices()[0] = 1; block.values()[0][1] = 9;
        assertArrayEquals(new int[] {0, 1}, block.indices()); assertEquals(.2, block.values()[0][1]);
        assertEquals(2, covariance.size());
        assertThrows(IllegalArgumentException.class, () -> new PopsCovariance(3, List.of(block)));
        assertThrows(IllegalArgumentException.class, () -> new PopsCovariance(2, List.of(block, block)));
        assertThrows(IllegalArgumentException.class, () -> new PopsCovariance.Block(new int[] {0, 1}, new double[][] {{1, .2}, {.3, 1}}));
    }
}
