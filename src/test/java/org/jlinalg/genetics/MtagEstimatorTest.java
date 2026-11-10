/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MtagEstimatorTest {
    private static final MtagEstimator.Limits LIMITS = new MtagEstimator.Limits(100, 10, 1_000_000);
    private static final List<String> V = List.of("rsB", "rsA"), T = List.of("trait");

    @Test void oneTraitReducesToOriginalStandardizedGwasIncludingResidualInflation() {
        var result = MtagEstimator.estimate(V, T, new double[][]{{2}, {-3}}, new double[][]{{100}, {400}},
            new double[][]{{.005}}, new double[][]{{1.44}}, LIMITS);
        assertEquals(V, result.variants()); assertEquals(.2, result.standardizedBeta()[0][0], 1e-15);
        assertEquals(-.15, result.standardizedBeta()[1][0], 1e-15);
        assertEquals(.12, result.standardizedSe()[0][0], 1e-15); assertEquals(.06, result.standardizedSe()[1][0], 1e-15);
        assertEquals(1, result.factor()[0][0], 1e-15); assertEquals(2 / 1.2, result.z()[0][0], 1e-14);
    }
    @Test void uncorrelatedTraitsRetainEachTraitsOwnZWithoutTraitBorrowing() {
        var result = MtagEstimator.estimate(List.of("v"), List.of("B", "A"), new double[][]{{2, -3}}, new double[][]{{100, 400}},
            new double[][]{{.005, 0}, {0, .002}}, new double[][]{{1, 0}, {0, 1}}, LIMITS);
        assertArrayEquals(new double[]{.2, -.15}, result.standardizedBeta()[0], 1e-15);
        assertArrayEquals(new double[]{.1, .05}, result.standardizedSe()[0], 1e-15);
        assertArrayEquals(new double[]{2, -3}, result.z()[0], 1e-14);
    }
    @Test void gmmKeepsNegativeOriginalVarianceWithoutAdjustment() {
        var actual = MtagEstimator.unadjustedGmmOmega(List.of("v"), T, new double[][]{{0}}, new double[][]{{100}}, new double[][]{{1}}, LIMITS);
        assertEquals(-.01, actual[0][0]);
        assertThrows(ArithmeticException.class, () -> MtagEstimator.estimate(List.of("v"), T, new double[][]{{0}}, new double[][]{{100}}, actual, new double[][]{{1}}, LIMITS));
    }
    @Test void rejectsSingularPerfectCorrelationInsteadOfAddingJitter() {
        assertThrows(ArithmeticException.class, () -> MtagEstimator.estimate(List.of("v"), List.of("A", "B"), new double[][]{{2, 3}}, new double[][]{{100, 200}},
            new double[][]{{1, 1}, {1, 1}}, new double[][]{{1, 0}, {0, 1}}, LIMITS));
    }
    @Test void rejectsNonfiniteDataNonpositiveSamplesAsymmetricCovarianceAndDuplicateIds() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(List.of("v"), T, new double[][]{{value}}, new double[][]{{100}}, new double[][]{{1}}, new double[][]{{1}}, LIMITS));
        for (double value : new double[]{0, -1})
            assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(List.of("v"), T, new double[][]{{1}}, new double[][]{{value}}, new double[][]{{1}}, new double[][]{{1}}, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(List.of("v"), List.of("A", "B"), new double[][]{{1, 2}}, new double[][]{{100, 100}},
            new double[][]{{1, .1}, {.2, 1}}, new double[][]{{1, 0}, {0, 1}}, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(List.of("v", "v"), T, new double[][]{{1}, {2}}, new double[][]{{100}, {100}}, new double[][]{{1}}, new double[][]{{1}}, LIMITS));
    }
    @Test void literalUnicodeIdsAndDefensiveCopiesPreserveCallerState() {
        double[][] z = {{2}, {3}}, n = {{100}, {200}}, omega = {{.005}}, sigma = {{1}};
        var result = MtagEstimator.estimate(List.of("家族", "🧬"), T, z, n, omega, sigma, LIMITS);
        double first = result.standardizedBeta()[0][0];
        z[0][0] = 99; omega[0][0] = 99; result.standardizedBeta()[0][0] = 99;
        assertEquals(first, result.standardizedBeta()[0][0]); assertEquals(List.of("家族", "🧬"), result.variants());
        assertThrows(UnsupportedOperationException.class, () -> result.variants().add("x"));
    }
    @Test void rejectsIncompleteDimensionsInvalidIdsAndLogicalCapacities() {
        assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(V, T, new double[][]{{1}}, new double[][]{{100}}, new double[][]{{1}}, new double[][]{{1}}, LIMITS));
        for (String id : List.of("", "v\n", "\ud800"))
            assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(List.of(id), T, new double[][]{{1}}, new double[][]{{100}}, new double[][]{{1}}, new double[][]{{1}}, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(V, T, new double[][]{{1}, {2}}, new double[][]{{100}, {100}}, new double[][]{{1}}, new double[][]{{1}}, new MtagEstimator.Limits(1, 1, 1_000_000)));
    }
    @Test void logicalMemoryPreflightIncludesRowObjectsAndIdentityStorage() {
        // Two one-trait rows need65536 + 512*2 + 128*2 + 256 bytes in
        // the conservative logical allocation model. The former scalar-only
        // estimate omitted the per-row allowance and would admit this budget.
        assertThrows(IllegalArgumentException.class, () -> MtagEstimator.estimate(V, T,
            new double[][]{{1}, {2}}, new double[][]{{100}, {100}}, new double[][]{{1}}, new double[][]{{1}},
            new MtagEstimator.Limits(2, 1, 67000)));
        var result = MtagEstimator.estimate(V, T, new double[][]{{1}, {2}}, new double[][]{{100}, {100}},
            new double[][]{{1}}, new double[][]{{1}}, new MtagEstimator.Limits(2, 1, 67072));
        assertEquals(2, result.standardizedBeta().length);
    }
}
