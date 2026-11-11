/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScdrsScoresTest {
    private static final ScdrsScores.Limits LIMITS = new ScdrsScores.Limits(200, 1000, 20_000_000);
    private static final double[] RAW = {0, 1, 4, -2};
    private static final double[][] CTRL = {{0, 4, 2}, {2, 0, 1}, {3, 1, 7}, {-1, 2, 0}};
    @Test void empiricalTailIsInclusiveAndHasPlusOneCorrection() {
        assertArrayEquals(new double[]{1, .8, .4, .2}, ScdrsScores.empiricalP(new double[]{-1, 1, 3, 5}, new double[]{0, 1, 1, 4}));
    }
    @Test void zeroScoresReceiveDistinctSourceMinimumAssignments() {
        var r = ScdrsScores.correctBackground(RAW, CTRL, new double[]{1, 2, .5}, LIMITS);
        double minimum = Double.POSITIVE_INFINITY;
        for (double[] row : r.controls()) for (double v : row) minimum = Math.min(minimum, v);
        assertEquals(minimum - .001, r.trait()[0]);
        assertEquals(minimum, r.controls()[0][0]);
        assertTrue(Double.isFinite(r.trait()[3]));
    }
    @Test void rawWeightsRetainTheSourceVarianceOffsetAndSignedWeights() {
        var r = ScdrsScores.rawScore(new double[][]{{2, 10}, {4, 0}}, new int[]{0, 1}, new double[]{1, -1},
            new double[]{1, 4}, new double[]{0, .03}, ScdrsScores.Weighting.VS, 10000);
        assertArrayEquals(new double[]{2, -1}, r.normalizedWeights());
        assertArrayEquals(new double[]{-6, 8}, r.scores()); assertEquals(8, r.independentVariance());
    }
    @Test void bhUsesExactlyTheSuppliedFamilyAndKeepsInputOrder() {
        assertArrayEquals(new double[]{.04, .02, .04, .9}, ScdrsScores.benjaminiHochberg(new double[]{.03, .005, .03, .9}), 1e-15);
        assertArrayEquals(new double[]{.005}, ScdrsScores.benjaminiHochberg(new double[]{.005}));
    }
    @Test void rejectsUnassessableArithmeticWithoutVarianceRepair() {
        assertThrows(ArithmeticException.class, () -> ScdrsScores.correctBackground(RAW, new double[][]{{1, 1}, {2, 2}, {3, 3}, {4, 4}}, new double[]{1, 1}, LIMITS));
        assertThrows(ArithmeticException.class, () -> ScdrsScores.correctBackground(RAW, CTRL, new double[]{1, 0, 1}, LIMITS));
        assertThrows(ArithmeticException.class, () -> ScdrsScores.rawScore(new double[][]{{1, 2}, {3, 4}}, new int[]{0, 1}, new double[]{1, -1}, new double[]{1, 1}, new double[]{1, 1}, ScdrsScores.Weighting.UNIFORM, 10000));
        assertThrows(ArithmeticException.class, () -> ScdrsScores.empiricalP(new double[]{Double.NaN}, new double[]{1}));
    }
    @Test void rejectsRaggedShapesDuplicateCellsAndUndeclaredCapacity() {
        assertThrows(IllegalArgumentException.class, () -> ScdrsScores.correctBackground(RAW, new double[][]{{1, 2}, {1}, {1, 2}, {1, 2}}, new double[]{1, 1}, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ScdrsScores.score(List.of("x", "x", "y", "z"), RAW, CTRL, new double[]{1, 1, 1}, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ScdrsScores.correctBackground(RAW, CTRL, new double[]{1, 1, 1}, new ScdrsScores.Limits(4, 3, 100)));
    }
    @Test void resultIdentityAndArraysAreDefensive() {
        var result = ScdrsScores.score(List.of("家族", "🧬", "cell-b", "cell-a"), RAW, CTRL, new double[]{1, 2, .5}, LIMITS);
        float original = result.values()[0][0]; result.values()[0][0] = 999;
        assertEquals(original, result.values()[0][0]); assertEquals("家族", result.cells().get(0));
        assertThrows(UnsupportedOperationException.class, () -> result.cells().add("q"));
    }
    @Test void constantCorrelationIsNotReportedAsZeroAssociation() {
        assertThrows(ArithmeticException.class, () -> ScdrsScores.correlation(new double[]{1, 1, 1, 1}, RAW, CTRL, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ScdrsScores.benjaminiHochberg(new double[]{1.01}));
    }
}
