/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class PopsChunkedTest {
    private static Map<String, String> fixture() throws Exception {
        Map<String, String> files = new TreeMap<>();
        try (var raw = PopsChunkedTest.class.getResourceAsStream("pops-original-0.2.zip")) {
            assertNotNull(raw);
            try (var zip = new ZipInputStream(raw)) {
                for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
    private static double[][] matrix(String text) { return text.lines().map(s -> Arrays.stream(s.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new); }
    private static int[] ordinals(String text) { return text.equals("none") ? null : Arrays.stream(text.split(",")).mapToInt(Integer::parseInt).toArray(); }
    private static Integer optional(String text) { return text.equals("none") ? null : Integer.valueOf(text); }
    private static double value(String text) { return text.isEmpty() || text.equals("nan") ? Double.NaN : Double.parseDouble(text); }

    private record Data(double[] target, double[][] x, double[][] covariates, PopsCovariance covariance,
                        boolean[][] masks, Pops.Selection selection, Pops.Method method, boolean failure) { }

    private static Data data(Map<String, String> f, String prefix) throws Exception {
        Properties p = new Properties(); p.load(new StringReader(f.get(prefix + "params.properties")));
        double[] target = Arrays.stream(matrix(f.get(prefix + "target.tsv"))).mapToDouble(v -> v[0]).toArray();
        double[][] x = matrix(f.get(prefix + "features.tsv")), rawMask = matrix(f.get(prefix + "masks.tsv"));
        boolean[][] masks = new boolean[3][target.length]; for (int i = 0; i < target.length; i++) for (int j = 0; j < 3; j++) masks[j][i] = rawMask[i][j] == 1;
        double[][] cov = null;
        if (Boolean.parseBoolean(p.getProperty("magma"))) {
            double[][] m = matrix(f.get(prefix + "magma-metadata.tsv"));
            cov = Pops.magmaCovariates(Arrays.stream(m).mapToDouble(v -> v[0]).toArray(), Arrays.stream(m).mapToDouble(v -> v[1]).toArray(), Arrays.stream(m).mapToDouble(v -> v[2]).toArray());
        } else if (f.containsKey(prefix + "covariates.tsv")) cov = matrix(f.get(prefix + "covariates.tsv"));
        PopsCovariance covariance = null;
        if (Boolean.parseBoolean(p.getProperty("covariance"))) {
            double[][] dense = matrix(f.get(prefix + "covariance.tsv")); List<PopsCovariance.Block> blocks = new ArrayList<>();
            var annotation = f.get(prefix + "annotation.tsv").lines().skip(1).map(s -> s.split("\t")).toList();
            Map<String, List<Integer>> groups = new LinkedHashMap<>();
            for (int i = 0; i < annotation.size(); i++) groups.computeIfAbsent(annotation.get(i)[1], k -> new ArrayList<>()).add(i);
            for (var group : groups.values()) {
                int[] rows = group.stream().mapToInt(Integer::intValue).toArray(); double[][] values = new double[rows.length][rows.length];
                for (int i = 0; i < rows.length; i++) for (int j = 0; j < rows.length; j++) values[i][j] = dense[rows[i]][rows[j]];
                blocks.add(new PopsCovariance.Block(rows, values));
            }
            covariance = new PopsCovariance(target.length, blocks);
            if (Boolean.parseBoolean(p.getProperty("magma"))) try (var context = BackendContext.select(BackendPolicy.CPU)) { covariance = covariance.regularizeMagma(target, context.backend()).covariance(); }
        }
        int[] controlIndices = ordinals(p.getProperty("controls")); boolean[] controls = null;
        if (controlIndices != null) { controls = new boolean[x[0].length]; for (int i : controlIndices) controls[i] = true; }
        return new Data(target, x, cov, covariance, masks,
            new Pops.Selection(Double.valueOf(p.getProperty("cutoff")), optional(p.getProperty("maximum")), ordinals(p.getProperty("subset")), controls, optional(p.getProperty("forward"))),
            p.getProperty("method").equals("ridge") ? Pops.Method.RIDGE : Pops.Method.MINIMUM_NORM_OLS, Boolean.parseBoolean(p.getProperty("expect_failure")));
    }

    private static PopsChunked.Features source(double[][] x, int width) {
        return new PopsChunked.Features() {
            public int rows() { return x.length; }
            public int columns() { return x[0].length; }
            public int batches() { return (columns() + width - 1) / width; }
            public PopsChunked.Batch read(int b) {
                int first = b * width, count = Math.min(width, columns() - first); double[][] result = new double[rows()][count];
                for (int i = 0; i < rows(); i++) System.arraycopy(x[i], first, result[i], 0, count);
                return new PopsChunked.Batch(first, result);
            }
        };
    }

    private static Pops.Result fit(Data d, PopsChunked.Features source, int[] rows, Pops.Selection selection, int capacity) throws Exception {
        return PopsChunked.fit(d.target, source, rows, d.covariates, d.covariance, d.masks[0], d.masks[1], d.masks[2], selection, d.method, BackendPolicy.CPU, capacity);
    }
    private static int[] identity(int n) { int[] rows = new int[n]; for (int i = 0; i < n; i++) rows[i] = i; return rows; }

    @TestFactory List<DynamicTest> originalCasesAcrossThreeBatchWidths() throws Exception {
        var f = fixture(); List<DynamicTest> tests = new ArrayList<>();
        for (String key : f.keySet()) if (key.endsWith("/params.properties")) {
            String prefix = key.substring(0, key.length() - "params.properties".length());
            for (int width : new int[] {1, 3, 64}) tests.add(DynamicTest.dynamicTest(prefix + "batch-" + width, () -> {
                Data d = data(f, prefix);
                if (d.failure) {
                    Exception error = assertThrows(Exception.class, () -> fit(d, source(d.x, width), identity(d.target.length), d.selection, 1000));
                    assertTrue(error.getMessage().toLowerCase(Locale.ROOT).matches(".*(feature|positive|definite).*"), error.toString()); return;
                }
                var actual = fit(d, source(d.x, width), identity(d.target.length), d.selection, 1000);
                var baseline = Pops.fit(d.target, d.x, d.covariates, d.covariance, d.masks[0], d.masks[1], d.masks[2], d.selection, d.method, BackendPolicy.CPU);
                assertArrayEquals(baseline.selectedFeatures(), actual.selectedFeatures()); assertArrayEquals(baseline.forwardSelectionRanks(), actual.forwardSelectionRanks());
                assertArrayEquals(baseline.predictions(), actual.predictions(), 1e-10); assertArrayEquals(baseline.coefficients(), actual.coefficients(), 1e-10);
                assertArrayEquals(baseline.projectedTarget(), actual.projectedTarget(), 1e-10); assertArrayEquals(baseline.cvScores(), actual.cvScores(), 1e-9);
                String[][] expected = f.get(prefix + "original.preds").lines().map(s -> s.split("\t", -1)).toArray(String[][]::new);
                int score = Arrays.asList(expected[0]).indexOf("PoPS_Score");
                for (int i = 0; i < actual.predictions().length; i++) assertEquals(value(expected[i + 1][score]), actual.predictions()[i], 5e-8);
                Pops.Marginal[] a = actual.marginals(), b = baseline.marginals();
                for (int i = 0; i < a.length; i++) {
                    assertEquals(b[i].beta(), a[i].beta(), 1e-10); assertEquals(b[i].standardError(), a[i].standardError(), 1e-10);
                    assertEquals(b[i].pValue(), a[i].pValue(), 1e-10); assertEquals(b[i].rSquared(), a[i].rSquared(), 1e-10);
                }
            }));
        }
        assertEquals(39, tests.size()); return tests;
    }

    @Test void explicitPermutationAndAdditionalPredictionRowsMatchTheOriginalArrayApi() throws Exception {
        Data d = data(fixture(), "ridge-custom-gls/"); int n = d.target.length, p = d.x[0].length;
        double[][] all = new double[n + 2][p]; int[] mapping = new int[n];
        for (int i = 0; i < n; i++) { mapping[i] = n - i; all[mapping[i]] = d.x[i].clone(); }
        Arrays.fill(all[0], .25); Arrays.fill(all[n + 1], -.5);
        var actual = fit(d, source(all, 2), mapping, d.selection, 1000);
        var baseline = Pops.fit(d.target, d.x, d.covariates, d.covariance, d.masks[0], d.masks[1], d.masks[2], d.selection, d.method, BackendPolicy.CPU);
        assertArrayEquals(baseline.predict(all), actual.predictions(), 1e-10);
        mapping[0] = mapping[1]; assertThrows(IllegalArgumentException.class, () -> fit(d, source(all, 2), mapping, d.selection, 1000));
    }

    @Test void changedBatchIsRejectedBeforeAResultIsReturned() throws Exception {
        Data d = data(fixture(), "ridge-plain/"); var delegate = source(d.x, d.x[0].length);
        PopsChunked.Features changing = new PopsChunked.Features() {
            int reads;
            public int rows() { return delegate.rows(); } public int columns() { return delegate.columns(); } public int batches() { return 1; }
            public PopsChunked.Batch read(int b) throws IOException { var batch = delegate.read(b); if (++reads > 1) batch.values()[0][0] += .01; return batch; }
        };
        IOException error = assertThrows(IOException.class, () -> fit(d, changing, identity(d.target.length), d.selection, 1000));
        assertTrue(error.getMessage().contains("changed between"));
    }

    @Test void capacityFailureDoesNotTruncateTheSelectedModel() throws Exception {
        Data d = data(fixture(), "ridge-plain/"); var all = new Pops.Selection(1.01, null, null, null, null);
        var error = assertThrows(IllegalArgumentException.class, () -> fit(d, source(d.x, 3), identity(d.target.length), all, 1));
        assertTrue(error.getMessage().contains("capacity"));
    }

    @Test void aGapInTheColumnPartitionIsRejected() throws Exception {
        Data d = data(fixture(), "ridge-plain/"); var delegate = source(d.x, 3);
        PopsChunked.Features gap = new PopsChunked.Features() {
            public int rows() { return delegate.rows(); } public int columns() { return delegate.columns(); } public int batches() { return delegate.batches(); }
            public PopsChunked.Batch read(int b) throws IOException { var batch = delegate.read(b); return new PopsChunked.Batch(batch.firstColumn() + 1, batch.values()); }
        };
        assertThrows(IOException.class, () -> fit(d, gap, identity(d.target.length), d.selection, 1000));
    }
}
