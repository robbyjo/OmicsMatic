/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.genetics.PrsiceHardCallScores.*;
import org.junit.jupiter.api.Test;

class PrsiceHardCallScoresTest {
    private static final boolean[] THREE = {true, true, true};
    private Result simple(int[][] calls, double[] effects, Missing missing, Scaling scaling) {
        int n = calls[0].length;
        boolean[] all = new boolean[n]; java.util.Arrays.fill(all, true);
        return score(calls, effects, new boolean[calls.length], all, all, all,
                Model.ADDITIVE, missing, scaling);
    }

    @Test void additiveFounderImputationAndAlleleAverage() {
        int[][] g = {{0, 2, -1}, {1, 2, 0}};
        boolean[] founders = {true, true, false};
        var sum = score(g, new double[]{.5, -1}, new boolean[2], founders, THREE, THREE,
                Model.ADDITIVE, Missing.MEAN_IMPUTE, Scaling.SUM);
        var avg = score(g, new double[]{.5, -1}, new boolean[2], founders, THREE, THREE,
                Model.ADDITIVE, Missing.MEAN_IMPUTE, Scaling.AVERAGE);
        assertEquals(Status.COMPLETE, sum.status());
        assertArrayEquals(new double[]{-1, -1, .5}, sum.scores(), 1e-15);
        assertArrayEquals(new double[]{-.25, -.25, .125}, avg.scores(), 1e-15);
        assertArrayEquals(new long[]{4, 4, 4}, avg.alleleCounts());
    }

    @Test void orientationCountsEffectiveAllele() {
        var r = score(new int[][]{{0, 1, 2}}, new double[]{1}, new boolean[]{true},
                THREE, THREE, THREE, Model.ADDITIVE, Missing.SET_ZERO, Scaling.SUM);
        assertArrayEquals(new double[]{2, 1, 0}, r.scores());
    }

    @Test void setZeroOmitsMissingAllelesFromAverage() {
        var r = simple(new int[][]{{-1, 2, 0}, {2, 1, 2}}, new double[]{10, 1},
                Missing.SET_ZERO, Scaling.AVERAGE);
        assertArrayEquals(new long[]{2, 4, 4}, r.alleleCounts());
        assertArrayEquals(new double[]{1, 5.25, .5}, r.scores());
    }

    @Test void centerLeavesMissingContributionZeroButCountsItsAlleles() {
        var r = simple(new int[][]{{0, 1, 2, -1}}, new double[]{1}, Missing.CENTER, Scaling.SUM);
        assertArrayEquals(new double[]{-1, 0, 1, 0}, r.scores());
        assertArrayEquals(new long[]{2, 2, 2, 2}, r.alleleCounts());
    }

    @Test void dominantMissingRetainsSourceConvention() {
        boolean[] founders = {true, true, true, false};
        boolean[] all = {true, true, true, true};
        var r = score(new int[][]{{2, 2, 0, -1}}, new double[]{1}, new boolean[]{false},
                founders, all, all, Model.DOMINANT, Missing.MEAN_IMPUTE, Scaling.SUM);
        assertArrayEquals(new double[]{1, 1, 0, 5.0 / 3}, r.scores(), 1e-15);
        assertEquals(5.0 / 3, r.missingModelValues()[0], 1e-15);
        assertTrue(r.warnings().get(0).contains("SOURCE CONVENTION"));
    }

    @Test void heterozygousMissingCanExceedObservedModelRange() {
        var r = score(new int[][]{{0, 2, -1}}, new double[]{1}, new boolean[]{false},
                new boolean[]{true, true, false}, THREE, THREE,
                Model.HETEROZYGOUS, Missing.MEAN_IMPUTE, Scaling.SUM);
        assertArrayEquals(new double[]{0, 0, 2}, r.scores());
    }

    @Test void standardizationUsesSampleSdOfAverageScores() {
        var r = simple(new int[][]{{0, 1, 2}}, new double[]{1}, Missing.SET_ZERO, Scaling.STANDARDIZE);
        assertEquals(Status.COMPLETE, r.status());
        assertEquals(.5, r.standardizationMean()); assertEquals(.5, r.standardizationSd());
        assertEquals(3, r.standardizationSamples());
        assertArrayEquals(new double[]{-1, 0, 1}, r.scores());
    }

    @Test void controlStandardizationTransformsOtherSamplesToo() {
        var r = score(new int[][]{{0, 1, 2}}, new double[]{1}, new boolean[1], THREE,
                THREE, new boolean[]{true, true, false}, Model.ADDITIVE,
                Missing.SET_ZERO, Scaling.CONTROL_STANDARDIZE);
        assertEquals(2, r.standardizationSamples());
        assertEquals(.25, r.standardizationMean());
        assertArrayEquals(new double[]{-Math.sqrt(.5), Math.sqrt(.5), 3 * Math.sqrt(.5)}, r.scores(), 1e-15);
    }

    @Test void regressionMaskIsExplicitAndDoesNotDropScoreRows() {
        var r = score(new int[][]{{0, 1, 2}}, new double[]{1}, new boolean[1], THREE,
                new boolean[]{true, false, true}, THREE, Model.ADDITIVE,
                Missing.SET_ZERO, Scaling.STANDARDIZE);
        assertEquals(2, r.standardizationSamples()); assertEquals(3, r.scores().length);
        assertArrayEquals(new double[]{-Math.sqrt(.5), 0, Math.sqrt(.5)}, r.scores(), 1e-15);
    }

    @Test void allFounderMissingIsUnadmittedEvenWithCalledChild() {
        var r = score(new int[][]{{-1, 2}}, new double[]{1}, new boolean[1],
                new boolean[]{true, false}, new boolean[]{true, true}, new boolean[]{true, true},
                Model.ADDITIVE, Missing.MEAN_IMPUTE, Scaling.SUM);
        assertEquals(Status.NO_INFORMATIVE_VARIANTS, r.status());
        assertArrayEquals(new boolean[]{false}, r.includedVariants());
        assertTrue(Double.isNaN(r.missingModelValues()[0]));
    }

    @Test void skippedVariantDoesNotAddDenominators() {
        var r = score(new int[][]{{-1, 2}, {1, 2}}, new double[]{100, 1}, new boolean[2],
                new boolean[]{true, false}, new boolean[]{true, true}, new boolean[]{true, true},
                Model.ADDITIVE, Missing.MEAN_IMPUTE, Scaling.AVERAGE);
        assertEquals(Status.COMPLETE, r.status());
        assertArrayEquals(new boolean[]{false, true}, r.includedVariants());
        assertArrayEquals(new long[]{2, 2}, r.alleleCounts());
        assertArrayEquals(new double[]{.5, 1}, r.scores());
    }

    @Test void zeroVarianceRemainsNonfiniteAndUnadmitted() {
        var r = simple(new int[][]{{1, 1, 1}}, new double[]{1}, Missing.SET_ZERO, Scaling.STANDARDIZE);
        assertEquals(Status.UNAVAILABLE_STANDARDIZATION, r.status());
        for (double value : r.scores()) assertTrue(Double.isNaN(value));
        assertArrayEquals(new double[]{1, 1, 1}, r.rawSums());
    }

    @Test void oneOrNoSelectedStandardizationSamplesAreUnavailable() {
        for (boolean[] mask : new boolean[][]{{true, false, false}, {false, false, false}}) {
            var r = score(new int[][]{{0, 1, 2}}, new double[]{1}, new boolean[1], THREE,
                    mask, THREE, Model.ADDITIVE, Missing.SET_ZERO, Scaling.STANDARDIZE);
            assertEquals(Status.UNAVAILABLE_STANDARDIZATION, r.status());
            for (double value : r.scores()) assertTrue(Double.isNaN(value));
        }
    }

    @Test void overflowIsRetainedAndUnadmitted() {
        var r = simple(new int[][]{{2, 1}}, new double[]{Double.MAX_VALUE}, Missing.SET_ZERO, Scaling.SUM);
        assertEquals(Status.NONFINITE_SCORE, r.status());
        assertEquals(Double.POSITIVE_INFINITY, r.rawSums()[0]);
    }

    @Test void malformedInputsFailBeforeScoring() {
        assertThrows(IllegalArgumentException.class, () -> simple(new int[][]{{3}}, new double[]{1}, Missing.SET_ZERO, Scaling.SUM));
        assertThrows(IllegalArgumentException.class, () -> simple(new int[][]{{-2}}, new double[]{1}, Missing.SET_ZERO, Scaling.SUM));
        assertThrows(IllegalArgumentException.class, () -> simple(new int[][]{{0}}, new double[]{Double.NaN}, Missing.SET_ZERO, Scaling.SUM));
        assertThrows(IllegalArgumentException.class, () -> simple(new int[][]{{0}, {0, 1}}, new double[]{1, 2}, Missing.SET_ZERO, Scaling.SUM));
        assertThrows(IllegalArgumentException.class, () -> score(new int[][]{{0}}, new double[]{1}, new boolean[1], new boolean[1], new boolean[]{true}, new boolean[]{true}, Model.ADDITIVE, Missing.SET_ZERO, Scaling.SUM));
    }

    @Test void inputAndResultArraysAreProtected() {
        int[][] input = {{0, 1, 2}}; double[] effects = {1};
        var r = simple(input, effects, Missing.SET_ZERO, Scaling.SUM);
        assertArrayEquals(new int[]{0, 1, 2}, input[0]); assertArrayEquals(new double[]{1}, effects);
        r.scores()[0] = 999; r.rawSums()[0] = 999; r.alleleCounts()[0] = 999;
        r.includedVariants()[0] = false; r.missingModelValues()[0] = 999;
        assertArrayEquals(new double[]{0, 1, 2}, r.scores());
        assertEquals(2, r.alleleCounts()[0]); assertTrue(r.includedVariants()[0]);
        assertEquals(1, r.missingModelValues()[0]);
        assertThrows(UnsupportedOperationException.class, () -> r.warnings().add("change"));
    }

    @Test void allMissingForOneSampleYieldsSourceZeroAverage() {
        var r = simple(new int[][]{{-1, 1}}, new double[]{1}, Missing.SET_ZERO, Scaling.AVERAGE);
        assertEquals(Status.COMPLETE, r.status());
        assertArrayEquals(new double[]{0, .5}, r.scores()); assertArrayEquals(new long[]{0, 2}, r.alleleCounts());
    }

    @Test void founderMaskChangesImputationWithoutUsingChildGenotype() {
        var r = score(new int[][]{{0, 2, -1}}, new double[]{1}, new boolean[1],
                new boolean[]{true, false, false}, THREE, THREE,
                Model.ADDITIVE, Missing.MEAN_IMPUTE, Scaling.SUM);
        assertArrayEquals(new double[]{0, 2, 0}, r.scores());
    }
}
