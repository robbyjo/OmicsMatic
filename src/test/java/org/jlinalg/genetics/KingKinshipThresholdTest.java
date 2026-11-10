/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.genetics.KingKinshipThreshold.*;

final class KingKinshipThresholdTest {
    private static final KingRobustKinship.Counts EXACT=new KingRobustKinship.Counts(1200,625,200,229,0);
    @Test void exactPaperBoundaryDoesNotInheritBinaryRounding() {
        var value=evaluate(EXACT,Formula.BETWEEN_MINIMUM,"0.0884");
        assertEquals(221,value.numerator());assertEquals(2500,value.denominator());assertEquals(Comparison.EQUAL,value.comparison());
        assertTrue(value.passes(Rule.GREATER_THAN_OR_EQUAL));assertFalse(value.passes(Rule.GREATER_THAN));
        assertTrue(0.5-(4*200.0+229)/(4*625)<0.0884,"This control must expose the floating-point boundary difference");
    }
    @Test void nearbyDecimalCutoffsRemainDistinct() {
        assertEquals(Comparison.ABOVE,evaluate(EXACT,Formula.BETWEEN_MINIMUM,"0.0883999999999999999999999999999999").comparison());
        assertEquals(Comparison.BELOW,evaluate(EXACT,Formula.BETWEEN_MINIMUM,"0.0884000000000000000000000000000001").comparison());
    }
    @Test void exactEquivalentDecimalSpellingsAgree() {
        for(String cutoff:new String[]{".0884","0.0884000","8.84e-2","884e-4"})
            assertEquals(Comparison.EQUAL,evaluate(EXACT,Formula.BETWEEN_MINIMUM,cutoff).comparison());
    }
    @Test void withinAndBetweenContextsAreDistinct() {
        var counts=new KingRobustKinship.Counts(2500,625,202,1000,250);
        assertEquals(Comparison.EQUAL,evaluate(counts,Formula.WITHIN_AVERAGE,"0.0884").comparison());
        assertEquals(Comparison.BELOW,evaluate(counts,Formula.BETWEEN_MINIMUM,"0.0884").comparison());
    }
    @Test void duplicateBoundaryIsInclusiveOnlyWhenChosen() {
        var value=evaluate(new KingRobustKinship.Counts(100,60,0,0,0),Formula.BETWEEN_MINIMUM,"0.5");
        assertEquals(Comparison.EQUAL,value.comparison());assertFalse(value.passes(Rule.GREATER_THAN));assertTrue(value.passes(Rule.GREATER_THAN_OR_EQUAL));
    }
    @Test void negativeFiniteRatioRemainsBelowZero() {
        var value=evaluate(new KingRobustKinship.Counts(100,10,80,0,0),Formula.BETWEEN_MINIMUM,"0");
        assertEquals(-300,value.numerator());assertEquals(40,value.denominator());assertEquals(Comparison.BELOW,value.comparison());
    }
    @Test void absentJointCallsCannotBecomeAnUnrelatedEdge() {
        for(Formula formula:Formula.values()) {
            var value=evaluate(new KingRobustKinship.Counts(0,0,0,0,0),formula,"0.0884");
            assertEquals(Comparison.NO_JOINT_CALLS,value.comparison());
            for(Rule rule:Rule.values())assertThrows(IllegalStateException.class,()->value.passes(rule));
        }
    }
    @Test void zeroDenominatorIsNotSourceZeroEvidence() {
        var counts=new KingRobustKinship.Counts(100,0,10,20,0);
        var between=evaluate(counts,Formula.BETWEEN_MINIMUM,"0.0884");
        assertEquals(Comparison.ZERO_HETEROZYGOSITY,between.comparison());assertThrows(IllegalStateException.class,()->between.passes(Rule.GREATER_THAN));
        assertEquals(Comparison.BELOW,evaluate(counts,Formula.WITHIN_AVERAGE,"0.0884").comparison());
    }
    @Test void fullVariantCapacityDoesNotOverflowNumerator() {
        var value=evaluate(new KingRobustKinship.Counts(Integer.MAX_VALUE,1,Integer.MAX_VALUE-1L,0,0),Formula.BETWEEN_MINIMUM,"0");
        assertEquals(-8589934582L,value.numerator());assertEquals(4,value.denominator());assertEquals(Comparison.BELOW,value.comparison());
    }
    @Test void invalidOrUnboundedCutoffsFailExplicitly() {
        for(String cutoff:new String[]{""," 0.1","0.1 ","NaN","Infinity","-0.1","0.5001","1e-1000000","0."+"1".repeat(1100)})
            assertThrows(IllegalArgumentException.class,()->evaluate(EXACT,Formula.BETWEEN_MINIMUM,cutoff),cutoff);
        assertThrows(NullPointerException.class,()->evaluate(EXACT,null,"0.1"));
        assertThrows(NullPointerException.class,()->evaluate(null,Formula.BETWEEN_MINIMUM,"0.1"));
    }
}
