package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HomerEnrichmentTest {
    @Test void hypergeometricMatchesEnumeratedSmallPopulation() {
        var result=HomerEnrichment.test(new HomerEnrichment.Counts(4,3,6,1),HomerEnrichment.Statistic.HYPERGEOMETRIC);
        // C(4,3)*C(6,1)+C(4,4)*C(6,0), divided by C(10,4).
        assertEquals(StrictMath.log(25.0/210),result.logP(),1e-13);
    }
    @Test void binomialUsesOneSidedPresenceTailRatherThanMotifSiteCounts() {
        var result=HomerEnrichment.test(new HomerEnrichment.Counts(4,3,6,1),HomerEnrichment.Statistic.BINOMIAL);
        assertEquals(StrictMath.log(4*StrictMath.pow(1.0/6,3)*5.0/6+StrictMath.pow(1.0/6,4)),result.logP(),1e-13);
    }
    @Test void zeroBackgroundHitsUseOriginalOneSequenceFloor() {
        var result=HomerEnrichment.test(new HomerEnrichment.Counts(5,5,10,0),HomerEnrichment.Statistic.BINOMIAL);
        assertEquals(0,result.observedBackgroundRate());assertEquals(.1,result.binomialBackgroundRate());
        assertEquals(5*StrictMath.log(.1),result.logP(),1e-13);
    }
    @Test void AllBackgroundHitsUseOriginalOneSequenceCeiling() {
        var result=HomerEnrichment.test(new HomerEnrichment.Counts(5,5,10,10),HomerEnrichment.Statistic.BINOMIAL);
        assertEquals(1,result.observedBackgroundRate());assertEquals(.9,result.binomialBackgroundRate());
        assertEquals(5*StrictMath.log(.9),result.logP(),1e-13);
    }
    @Test void zeroTargetHitsHaveUnitUpperTailAndDisplayOne() {
        for(var statistic:HomerEnrichment.Statistic.values()) {
            var result=HomerEnrichment.test(new HomerEnrichment.Counts(5,0,10,10),statistic);
            assertEquals(0,result.logP());assertEquals(0,result.originalDisplayExponent());
        }
    }
    @Test void enrichmentRatioHasOneSequencePseudocountInBothRoles() {
        var result=HomerEnrichment.test(new HomerEnrichment.Counts(4,0,8,0),HomerEnrichment.Statistic.HYPERGEOMETRIC);
        assertEquals(1,result.log2Enrichment(),1e-15);
    }
    @Test void originalDisplayedPIsCoarseTruncationRatherThanAnExactProbability() {
        var result=HomerEnrichment.test(new HomerEnrichment.Counts(40,40,40,1),HomerEnrichment.Statistic.HYPERGEOMETRIC);
        assertEquals(-49.318, result.logP(),.0005);assertEquals(-21,result.originalDisplayExponent());
        assertEquals(StrictMath.log(40)/StrictMath.log(2),result.log2Enrichment(),1e-14);
    }
    @Test void extremeTailRetainsFiniteLogProbabilityAfterProbabilityUnderflow() {
        var result=HomerEnrichment.test(new HomerEnrichment.Counts(10000,10000,10000,0),HomerEnrichment.Statistic.BINOMIAL);
        assertTrue(Double.isFinite(result.logP()));assertEquals(0,StrictMath.exp(result.logP()));
        assertEquals(10000*StrictMath.log(.0001),result.logP(),1e-8);
    }
    @Test void roleOverlapIsNotSilentlyDeduplicatedOrRepaired() {
        var counts=new HomerEnrichment.Counts(12,9,12,9);
        var result=HomerEnrichment.test(counts,HomerEnrichment.Statistic.HYPERGEOMETRIC);
        assertSame(counts,result.counts());assertEquals(0,result.log2Enrichment(),1e-15);
    }
    @Test void impossibleOrDegenerateCountsFailExplicitly() {
        for(int[] row:new int[][]{{0,0,2,0},{1,0,1,0},{1,2,2,0},{1,-1,2,0},{1,0,2,3},{1,0,2,-1},{Integer.MAX_VALUE,1,2,1}})
            assertThrows(IllegalArgumentException.class,()->new HomerEnrichment.Counts(row[0],row[1],row[2],row[3]));
        assertThrows(IllegalArgumentException.class,()->HomerEnrichment.test(null,HomerEnrichment.Statistic.BINOMIAL));
        assertThrows(IllegalArgumentException.class,()->HomerEnrichment.test(new HomerEnrichment.Counts(1,0,2,0),null));
    }
}
