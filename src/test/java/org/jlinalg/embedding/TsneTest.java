/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.embedding;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TsneTest {
    private static double[][] fixture(String name) throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                TsneTest.class.getResourceAsStream("/tsne/" + name + ".tsv")), StandardCharsets.UTF_8))) {
            return reader.lines().map(line -> Arrays.stream(line.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
        }
    }
    private static void close(double[][] expected, double[][] actual, double tolerance) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertArrayEquals(expected[i], actual[i], tolerance, "row " + i);
    }
    @Test void exactAffinitiesAgreeWithIndependentSklearnReference() throws Exception {
        double[][] p = Tsne.jointProbabilities(fixture("observations"), 4.5);
        close(fixture("probabilities"), p, 2e-6);
        double sum = 0;
        for (int i = 0; i < p.length; i++) for (int j = 0; j < p.length; j++) {
            assertEquals(p[i][j], p[j][i]); assertTrue(p[i][j] >= 0);
            if (i == j) assertEquals(0, p[i][j]);
            sum += p[i][j];
        }
        assertEquals(1, sum, 2e-15);
    }
    @Test void klAndGradientAgreeWithIndependentReferenceAndFiniteDifferences() throws Exception {
        double[][] p = fixture("probabilities"), y = fixture("coordinates");
        int n = p.length;
        double[][] gradient = new double[n][2], kernel = new double[n][n];
        double kl = Tsne.evaluate(p, y, 1, gradient, kernel);
        // Stored scikit-learn reference is also recorded with dependency hashes in provenance.json.
        String provenance;
        try (var in = Objects.requireNonNull(TsneTest.class.getResourceAsStream("/tsne/provenance.json"))) {
            provenance = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var matcher = java.util.regex.Pattern.compile("\\\"kl_divergence\\\": ([^,]+),").matcher(provenance);
        assertTrue(matcher.find());
        assertEquals(Double.parseDouble(matcher.group(1)), kl, 2e-12);
        close(fixture("gradient"), gradient, 2e-12);
        double[][] scratch = new double[n][2];
        for (int i = 0; i < n; i++) for (int d = 0; d < 2; d++) {
            double original = y[i][d], h = 1e-5;
            y[i][d] = original + h;
            double plus = Tsne.evaluate(p, y, 1, scratch, kernel);
            y[i][d] = original - h;
            double minus = Tsne.evaluate(p, y, 1, scratch, kernel);
            y[i][d] = original;
            assertEquals((plus - minus) / (2 * h), gradient[i][d], 2e-10);
        }
    }
    @Test void seededOptimizationImprovesKlAndPreservesKnownNeighborhoods() {
        Random random = new Random(3807);
        double[][] x = new double[90][6];
        for (int i = 0; i < x.length; i++) for (int d = 0; d < 6; d++)
            x[i][d] = (i / 30) * 15 + random.nextGaussian();
        var options = new Tsne.Options(10, 750, 50, 12, 200, 17, 1e-8);
        Tsne.Result first = Tsne.fit(x, options), second = Tsne.fit(x, options);
        double[][] y = first.coordinates();
        close(y, second.coordinates(), 0);
        assertTrue(first.finalKl() < first.initialKl() * 0.5, first.toString());
        assertTrue(first.maxEntropyError() <= 1e-7);
        assertTrue(first.iterations() > 200 && first.iterations() <= 750);
        int matching = 0;
        for (int i = 0; i < y.length; i++) {
            final int cell = i;
            Integer[] others = java.util.stream.IntStream.range(0, y.length).filter(j -> j != cell).boxed()
                .sorted(Comparator.comparingDouble(j -> distance(y[cell], y[j]))).toArray(Integer[]::new);
            for (int k = 0; k < 6; k++) if (others[k] / 30 == i / 30) matching++;
        }
        assertTrue(matching >= 90 * 6 * 0.98, "Separated known groups should retain local neighbors");
        for (int d = 0; d < 2; d++) {
            double sum = 0; for (double[] row : y) sum += row[d];
            assertEquals(0, sum / y.length, 1e-12);
        }
        double saved = first.coordinates()[0][0]; y[0][0] = 1e90;
        assertEquals(saved, first.coordinates()[0][0]);
    }
    private static double distance(double[] a, double[] b) {
        return Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2);
    }
    @Test void affinitiesAreInvariantToTranslationAndUniformScaleIncludingExtremeUnits() throws Exception {
        double[][] x = fixture("observations"), shifted = new double[x.length][4], huge = new double[x.length][4];
        for (int i = 0; i < x.length; i++) for (int d = 0; d < 4; d++) {
            shifted[i][d] = 17 + x[i][d] * 3;
            huge[i][d] = x[i][d] * 1e299;
        }
        double[][] p = Tsne.jointProbabilities(x, 4.5);
        close(p, Tsne.jointProbabilities(shifted, 4.5), 2e-12);
        close(p, Tsne.jointProbabilities(huge, 4.5), 2e-12);
    }
    @Test void maximumPerplexityIsUniformButImpossibleTiesAreRejected() {
        double[][] x = {{0}, {0}, {0}, {0}, {1}};
        assertThrows(IllegalArgumentException.class, () -> Tsne.jointProbabilities(x, 2));
        double[][] p = Tsne.jointProbabilities(x, 4);
        for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) assertEquals(i == j ? 0 : 0.05, p[i][j], 1e-16);
    }
    @Test void invalidInputsAndUnboundedWorkAreRejected() {
        double[][] x = {{0}, {1}, {2}};
        for (double perplexity : new double[]{0, 1, 3, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> Tsne.jointProbabilities(x, perplexity));
        assertThrows(IllegalArgumentException.class, () -> Tsne.jointProbabilities(new double[][]{{0}, {0}, {0}}, 2));
        assertThrows(IllegalArgumentException.class, () -> Tsne.jointProbabilities(new double[][]{{0}, {1, 2}, {3}}, 2));
        assertThrows(IllegalArgumentException.class, () -> Tsne.jointProbabilities(new double[][]{{0}, {Double.NaN}, {3}}, 2));
        assertThrows(IllegalArgumentException.class, () -> Tsne.jointProbabilities(new double[2001][1], 30));
        assertThrows(IllegalArgumentException.class, () -> Tsne.jointProbabilities(new double[2000][51], 30));
        assertThrows(IllegalArgumentException.class, () -> new Tsne.Options(10, 50, 50, 12, 50, 1, 1e-7));
        assertThrows(IllegalArgumentException.class, () -> new Tsne.Options(10, 1000, Double.NaN, 12, 250, 1, 1e-7));
    }
}
