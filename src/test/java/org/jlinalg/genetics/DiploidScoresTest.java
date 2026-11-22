/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.jlinalg.genetics.DiploidScores.MissingPolicy;
import org.jlinalg.genetics.DiploidScores.Status;

class DiploidScoresTest {
    @Test void completeWeightsProduceSumsAndAlleleAverages() {
        var a = DiploidScores.accumulator(3, MissingPolicy.ERROR);
        a.add(new double[] {0,1,2}, 2, Double.NaN);
        a.add(new double[] {2,1,0}, -.5, Double.NaN);
        assertEquals(-1, a.score(0).sum()); assertEquals(1.5, a.score(1).sum()); assertEquals(4, a.score(2).sum());
        assertEquals(.375, a.score(1).average()); assertEquals(4, a.score(1).denominatorAlleles());
        assertEquals(4, a.score(1).observedAlleles()); assertEquals(Status.COMPLETE, a.score(1).status());
    }
    @Test void imputationUsesSuppliedFrequencyAndKeepsObservedDenominatorSeparate() {
        var a = DiploidScores.accumulator(2, MissingPolicy.MEAN_IMPUTE);
        a.add(new double[] {Double.NaN,2}, 2, .25); a.add(new double[] {1,Double.NaN}, -.5, .8);
        assertEquals(.5, a.score(0).sum()); assertEquals(.125, a.score(0).average());
        assertEquals(3.2, a.score(1).sum(), 1e-15); assertEquals(.8, a.score(1).average(), 1e-15);
        assertEquals(2, a.score(0).observedAlleles()); assertEquals(4, a.score(0).denominatorAlleles());
        assertEquals(1, a.score(0).imputedVariants()); assertEquals(Status.PARTIALLY_IMPUTED, a.score(0).status());
    }
    @Test void omissionUsesOnlyObservedAlleles() {
        var a = DiploidScores.accumulator(2, MissingPolicy.OMIT);
        a.add(new double[] {Double.NaN,2}, 2, Double.NaN); a.add(new double[] {1,Double.NaN}, -.5, Double.NaN);
        assertEquals(-.5, a.score(0).sum()); assertEquals(-.25, a.score(0).average());
        assertEquals(4, a.score(1).sum()); assertEquals(2, a.score(1).average());
        assertEquals(0, a.score(0).imputedVariants()); assertEquals(2, a.score(0).denominatorAlleles());
        assertEquals(Status.PARTIALLY_OBSERVED, a.score(0).status());
    }
    @Test void allMissingOmissionIsUnreportableRatherThanZero() {
        var a = DiploidScores.accumulator(1, MissingPolicy.OMIT); a.add(new double[] {Double.NaN}, 0, Double.NaN);
        assertTrue(Double.isNaN(a.score(0).sum())); assertTrue(Double.isNaN(a.score(0).average()));
        assertEquals(0, a.score(0).denominatorAlleles()); assertEquals(Status.NO_OBSERVED_DOSAGES, a.score(0).status());
    }
    @Test void fullyImputedStatusRemainsVisible() {
        var a = DiploidScores.accumulator(1, MissingPolicy.MEAN_IMPUTE); a.add(new double[] {Double.NaN}, 3, .5);
        assertEquals(3, a.score(0).sum()); assertEquals(1.5, a.score(0).average());
        assertEquals(0, a.score(0).observedAlleles()); assertEquals(1, a.score(0).imputedVariants()); assertEquals(Status.FULLY_IMPUTED, a.score(0).status());
    }
    @Test void missingErrorDoesNotPartiallyMutateEarlierSamples() {
        var a = DiploidScores.accumulator(2, MissingPolicy.ERROR); a.add(new double[] {1,2}, 1, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {2,Double.NaN}, 10, Double.NaN));
        assertEquals(1, a.variantCount()); assertEquals(1, a.score(0).sum()); assertEquals(2, a.score(1).sum());
    }
    @Test void invalidLateDosageAndInfinityLeaveStateUnchanged() {
        var a = DiploidScores.accumulator(2, MissingPolicy.OMIT); a.add(new double[] {1,2}, 1, Double.NaN);
        for (double bad : new double[] {-1,2.00000001,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {2,bad}, 100, Double.NaN));
        assertEquals(1, a.variantCount()); assertEquals(1, a.score(0).sum()); assertEquals(2, a.score(1).sum());
    }
    @Test void explicitFrequencyValidationDoesNotEstimateFromTargetSamples() {
        var a = DiploidScores.accumulator(1, MissingPolicy.MEAN_IMPUTE);
        for (double bad : new double[] {Double.NaN,-.01,1.01,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {1}, 1, bad));
        assertEquals(0, a.variantCount());
        a.add(new double[] {Double.NaN}, 1, 0); a.add(new double[] {Double.NaN}, 1, 1);
        assertEquals(2, a.score(0).sum());
    }
    @Test void optionalSuppliedFrequencyStillMustBeValid() {
        var a = DiploidScores.accumulator(1, MissingPolicy.OMIT);
        assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {1}, 1, -.1));
        assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {1}, 1, Double.NEGATIVE_INFINITY));
    }
    @Test void nonfiniteWeightsAndWrongDimensionsAreRejected() {
        var a = DiploidScores.accumulator(2, MissingPolicy.ERROR);
        for (double weight : new double[] {Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {1,1}, weight, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {1}, 1, Double.NaN));
        assertThrows(NullPointerException.class, () -> a.add(null, 1, Double.NaN)); assertEquals(0, a.variantCount());
    }
    @Test void arithmeticOverflowRejectsTheWholeVariant() {
        var a = DiploidScores.accumulator(2, MissingPolicy.ERROR); a.add(new double[] {1,1}, 1, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {0,2}, Double.MAX_VALUE, Double.NaN));
        assertEquals(1, a.variantCount()); assertEquals(1, a.score(0).sum()); assertEquals(1, a.score(1).sum());
    }
    @Test void compensatedSumRetainsSmallContributionAcrossCancellation() {
        var a = DiploidScores.accumulator(1, MissingPolicy.ERROR);
        a.add(new double[] {1}, 1e16, Double.NaN); a.add(new double[] {1}, 1, Double.NaN); a.add(new double[] {1}, -1e16, Double.NaN);
        assertEquals(1, a.score(0).sum());
    }
    @Test void workLimitsAreCheckedBeforeMutation() {
        var a = DiploidScores.accumulator(2, MissingPolicy.ERROR, new DiploidScores.Limits(2,10,2));
        a.add(new double[] {1,1}, 1, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {1,1}, 1, Double.NaN)); assertEquals(1, a.variantCount());
        assertThrows(IllegalArgumentException.class, () -> DiploidScores.accumulator(3, MissingPolicy.ERROR, new DiploidScores.Limits(2,1,10)));
        assertThrows(IllegalArgumentException.class, () -> new DiploidScores.Limits(0,1,1));
    }
    @Test void variantLimitIsIndependentOfSampleCellLimit() {
        var a = DiploidScores.accumulator(1, MissingPolicy.ERROR, new DiploidScores.Limits(1,1,100)); a.add(new double[] {1}, 1, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> a.add(new double[] {1}, 1, Double.NaN)); assertEquals(1, a.score(0).sum());
    }
    @Test void snapshotsAndInputsAreNotMutableAliases() {
        var a = DiploidScores.accumulator(1, MissingPolicy.ERROR); double[] input = {1}; a.add(input, 1, Double.NaN); input[0] = 2;
        var before = a.scores(); assertThrows(UnsupportedOperationException.class, () -> before.clear());
        a.add(new double[] {2}, 2, Double.NaN); assertEquals(1, before.get(0).sum()); assertEquals(5, a.score(0).sum());
    }
    @Test void emptyModelsAndSampleBoundsAreExplicit() {
        var a = DiploidScores.accumulator(1, MissingPolicy.ERROR);
        assertThrows(IllegalStateException.class, a::scores); assertThrows(IllegalStateException.class, () -> a.score(0));
        assertThrows(IllegalArgumentException.class, () -> DiploidScores.accumulator(0, MissingPolicy.ERROR));
        assertThrows(NullPointerException.class, () -> DiploidScores.accumulator(1, null));
        a.add(new double[] {0}, 1, Double.NaN); assertThrows(IndexOutOfBoundsException.class, () -> a.score(-1)); assertThrows(IndexOutOfBoundsException.class, () -> a.score(1));
    }
    @Test void oppositeAlleleMappingPreservesMissingAndRejectsInvalidDosage() {
        assertEquals(2, DiploidScores.otherAlleleDosage(0)); assertEquals(.25, DiploidScores.otherAlleleDosage(1.75));
        assertTrue(Double.isNaN(DiploidScores.otherAlleleDosage(Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> DiploidScores.otherAlleleDosage(Double.POSITIVE_INFINITY));
    }
}
