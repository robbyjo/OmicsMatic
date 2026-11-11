/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnprelateProfilesTest {
    private static final SnprelateKingRobust.Context WITHIN=SnprelateKingRobust.Context.WITHIN_FAMILY;
    private static final SnprelateKingRobust.Context BETWEEN=SnprelateKingRobust.Context.BETWEEN_OR_SINGLETONS;
    private static final SnprelateVariantQc.Layout SNP=SnprelateVariantQc.Layout.SNP_BY_SAMPLE;
    private static final SnprelateVariantQc.Layout SAMPLE=SnprelateVariantQc.Layout.SAMPLE_BY_SNP;
    private static SnprelateVariantQc.Statistics stats(int samples,int called,long copies) {
        return SnprelateVariantQc.statistics(new SnprelateVariantQc.Counts(samples,called,copies),SNP);
    }

    @Test void originalCountPopulationKeepsNegativeKinshipAndExactIbs0() {
        var e=SnprelateKingRobust.estimate(new KingRobustKinship.Counts(128,23,11,21,32),BETWEEN);
        assertEquals(-0x1.a2e8ba2e8ba30p-5,e.kinship());
        assertEquals(97,e.sumSquaredDifference());assertEquals(176,e.denominator());
        assertEquals(11.0/128,e.ibs0());assertEquals(e.rawKinship(),e.kinship());
        assertFalse(e.sourceNonfiniteNormalization());assertEquals(SnprelateKingRobust.Disposition.FINITE,e.disposition());
    }
    @Test void explicitFamilyContextChangesTheDenominator() {
        var c=new KingRobustKinship.Counts(10,2,1,3,0);
        var within=SnprelateKingRobust.estimate(c,WITHIN);var between=SnprelateKingRobust.estimate(c,BETWEEN);
        assertSame(c,within.counts());assertEquals(WITHIN,within.context());
        assertEquals(14,within.denominator());assertEquals(0,within.kinship());
        assertEquals(8,between.denominator());assertEquals(-0.375,between.kinship());
    }
    @Test void oneZeroHeterozygosityParticipantCanBeFiniteWithinFamily() {
        var c=new KingRobustKinship.Counts(4,0,0,4,0);
        var within=SnprelateKingRobust.estimate(c,WITHIN);var between=SnprelateKingRobust.estimate(c,BETWEEN);
        assertEquals(0,within.kinship());assertEquals(SnprelateKingRobust.Disposition.FINITE,within.disposition());
        assertEquals(Double.NEGATIVE_INFINITY,between.rawKinship());assertTrue(Double.isNaN(between.kinship()));
        assertEquals(SnprelateKingRobust.Disposition.ZERO_HETEROZYGOSITY_DENOMINATOR,between.disposition());
    }
    @Test void sourceNormalizesNegativeInfinityWithoutChangingIbs0() {
        var e=SnprelateKingRobust.estimate(new KingRobustKinship.Counts(4,0,4,0,0),BETWEEN);
        assertEquals(Double.NEGATIVE_INFINITY,e.rawKinship());assertTrue(Double.isNaN(e.kinship()));
        assertEquals(1,e.ibs0());assertTrue(e.sourceNonfiniteNormalization());
    }
    @Test void noJointCallsRemainDistinctFromCalledIdenticalHomozygotes() {
        var absent=SnprelateKingRobust.estimate(new KingRobustKinship.Counts(0,0,0,0,0),WITHIN);
        var hom=SnprelateKingRobust.estimate(new KingRobustKinship.Counts(4,0,0,0,0),WITHIN);
        assertTrue(Double.isNaN(absent.ibs0()));assertEquals(0,hom.ibs0());
        assertTrue(Double.isNaN(absent.kinship()));assertTrue(Double.isNaN(hom.kinship()));
        assertEquals(SnprelateKingRobust.Disposition.NO_JOINT_CALLS,absent.disposition());
        assertEquals(SnprelateKingRobust.Disposition.ZERO_HETEROZYGOSITY_DENOMINATOR,hom.disposition());
        assertEquals(0.5,SnprelateKingRobust.DIAGONAL_KINSHIP);assertEquals(0,SnprelateKingRobust.DIAGONAL_IBS0);
    }
    @Test void unsignedSourceCounterCapacityFailsBeforeWrappedResults() {
        var boundary=SnprelateKingRobust.estimate(new KingRobustKinship.Counts(1073741826,0,1073741823,3,0),WITHIN);
        assertEquals(0xffff_ffffL,boundary.sumSquaredDifference());
        assertThrows(IllegalArgumentException.class,()->SnprelateKingRobust.estimate(new KingRobustKinship.Counts(1073741824,0,1073741824,0,0),BETWEEN));
        var allHet=SnprelateKingRobust.estimate(new KingRobustKinship.Counts(Integer.MAX_VALUE,Integer.MAX_VALUE,0,0,0),WITHIN);
        assertEquals(8589934588.0,allHet.denominator());assertEquals(0.5,allHet.kinship());
    }
    @Test void pairCalculationRequiresCountsAndExplicitContext() {
        assertThrows(NullPointerException.class,()->SnprelateKingRobust.estimate(null,BETWEEN));
        assertThrows(NullPointerException.class,()->SnprelateKingRobust.estimate(new KingRobustKinship.Counts(1,1,0,0,0),null));
    }
    @Test void asymmetricCountsKeepSymmetricPairCoefficients() {
        var a=new KingRobustKinship.Counts(128,23,11,21,32);var b=new KingRobustKinship.Counts(128,23,11,32,21);
        for(var context:SnprelateKingRobust.Context.values()) {
            assertEquals(SnprelateKingRobust.estimate(a,context).kinship(),SnprelateKingRobust.estimate(b,context).kinship());
            assertEquals(SnprelateKingRobust.estimate(a,context).ibs0(),SnprelateKingRobust.estimate(b,context).ibs0());
        }
    }
    @Test void nativeCallsKeepMissingAndRejectInvalidCodes() {
        byte[] calls={0,1,2,-1};assertEquals(new SnprelateVariantQc.Counts(4,3,3),SnprelateVariantQc.count(calls));
        assertArrayEquals(new byte[]{0,1,2,-1},calls);
        assertThrows(IllegalArgumentException.class,()->SnprelateVariantQc.count(new byte[]{0,3}));
        assertThrows(IllegalArgumentException.class,()->SnprelateVariantQc.count(new byte[]{-2}));
        assertThrows(IllegalArgumentException.class,()->SnprelateVariantQc.count(new byte[0]));
        assertThrows(NullPointerException.class,()->SnprelateVariantQc.count(null));
    }
    @Test void frequencyUsesCompleteCalledPopulationWithoutImputation() {
        var e=stats(10,8,2);assertEquals(0.125,e.alleleFrequency());assertEquals(0.125,e.minorAlleleFrequency());assertEquals(0.2,e.missingRate());
        assertEquals(0.125,stats(10,8,14).minorAlleleFrequency());
    }
    @Test void layoutRoundingChangesAnExplicitNearBoundarySelection() {
        var c=new SnprelateVariantQc.Counts(10,9,4);
        var snp=SnprelateVariantQc.statistics(c,SNP);var sample=SnprelateVariantQc.statistics(c,SAMPLE);
        assertEquals(0x1.999999999999ap-4,snp.missingRate());assertEquals(0x1.9999999999998p-4,sample.missingRate());
        var f=new SnprelateVariantQc.Filters(false,Double.NaN,0.09999999999999999);
        assertEquals(SnprelateVariantQc.Decision.ABOVE_MISSING_RATE,SnprelateVariantQc.decide(snp,f));
        assertEquals(SnprelateVariantQc.Decision.SELECTED_AFTER_QC,SnprelateVariantQc.decide(sample,f));
    }
    @Test void defaults146AreExplicitAndRejectMoreThanOnePercentMissing() {
        var f=SnprelateVariantQc.Filters.defaults146();assertTrue(f.removeMonomorphic());assertTrue(Double.isNaN(f.minorAlleleFrequency()));assertEquals(0.01,f.missingRate());
        assertEquals(SnprelateVariantQc.Decision.SELECTED_AFTER_QC,SnprelateVariantQc.decide(stats(100,99,99),f));
        assertEquals(SnprelateVariantQc.Decision.ABOVE_MISSING_RATE,SnprelateVariantQc.decide(stats(100,98,98),f));
    }
    @Test void allMissingIsSelectedOnlyWhenSourceQcIsBypassed() {
        var s=stats(4,0,0);assertTrue(Double.isNaN(s.alleleFrequency()));assertTrue(Double.isNaN(s.minorAlleleFrequency()));assertEquals(1,s.missingRate());
        assertEquals(SnprelateVariantQc.Decision.SELECTED_QC_BYPASSED,SnprelateVariantQc.decide(s,SnprelateVariantQc.Filters.bypass()));
        for(var f:List.of(SnprelateVariantQc.Filters.defaults146(),new SnprelateVariantQc.Filters(false,-1,Double.NaN),new SnprelateVariantQc.Filters(false,Double.NaN,2)))
            assertEquals(SnprelateVariantQc.Decision.NONFINITE_FREQUENCY,SnprelateVariantQc.decide(s,f));
    }
    @Test void monomorphicRejectsBeforeFrequencyOrMissingnessThreshold() {
        assertEquals(SnprelateVariantQc.Decision.MONOMORPHIC,SnprelateVariantQc.decide(stats(10,9,0),new SnprelateVariantQc.Filters(true,0.4,0)));
        assertEquals(SnprelateVariantQc.Decision.SELECTED_AFTER_QC,SnprelateVariantQc.decide(stats(10,10,10),SnprelateVariantQc.Filters.defaults146()));
    }
    @Test void mafThresholdIncludesEqualityAndKeepsSourceRejectionOrder() {
        var s=stats(10,8,2);
        assertTrue(SnprelateVariantQc.selected(SnprelateVariantQc.decide(s,new SnprelateVariantQc.Filters(false,0.125,Double.NaN))));
        assertEquals(SnprelateVariantQc.Decision.BELOW_MAF,SnprelateVariantQc.decide(s,new SnprelateVariantQc.Filters(false,Math.nextUp(0.125),0)));
    }
    @Test void missingThresholdIncludesEquality() {
        var s=stats(10,9,4);
        assertTrue(SnprelateVariantQc.selected(SnprelateVariantQc.decide(s,new SnprelateVariantQc.Filters(false,Double.NaN,0.1))));
        assertFalse(SnprelateVariantQc.selected(SnprelateVariantQc.decide(s,new SnprelateVariantQc.Filters(false,Double.NaN,Math.nextDown(0.1)))));
    }
    @Test void infiniteCutoffsDisableThresholdsInEitherDirection() {
        for(double maf:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            for(double missing:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
                var f=new SnprelateVariantQc.Filters(false,maf,missing);assertFalse(f.active());
                assertEquals(SnprelateVariantQc.Decision.SELECTED_QC_BYPASSED,SnprelateVariantQc.decide(stats(4,0,0),f));
            }
    }
    @Test void finiteOutOfRangeCutoffsAreNotClampedOrReplaced() {
        assertEquals(SnprelateVariantQc.Decision.BELOW_MAF,SnprelateVariantQc.decide(stats(4,4,4),new SnprelateVariantQc.Filters(false,2,Double.NaN)));
        assertEquals(SnprelateVariantQc.Decision.ABOVE_MISSING_RATE,SnprelateVariantQc.decide(stats(4,4,4),new SnprelateVariantQc.Filters(false,-1,-1)));
        assertEquals(SnprelateVariantQc.Decision.SELECTED_AFTER_QC,SnprelateVariantQc.decide(stats(4,4,4),new SnprelateVariantQc.Filters(false,-1,2)));
    }
    @Test void dosageCountsRejectImpossiblePopulations() {
        assertThrows(IllegalArgumentException.class,()->new SnprelateVariantQc.Counts(0,0,0));
        assertThrows(IllegalArgumentException.class,()->new SnprelateVariantQc.Counts(4,-1,0));
        assertThrows(IllegalArgumentException.class,()->new SnprelateVariantQc.Counts(4,5,0));
        assertThrows(IllegalArgumentException.class,()->new SnprelateVariantQc.Counts(4,2,-1));
        assertThrows(IllegalArgumentException.class,()->new SnprelateVariantQc.Counts(4,2,5));
    }
    @Test void signedSourceAlleleAndDenominatorCapacityIsExplicit() {
        int called=Integer.MAX_VALUE/2;assertEquals(1,stats(Integer.MAX_VALUE,called,2L*called).alleleFrequency());
        assertThrows(IllegalArgumentException.class,()->stats(Integer.MAX_VALUE,called+1,0));
        assertThrows(IllegalArgumentException.class,()->stats(Integer.MAX_VALUE,called+1,2L*(called+1)));
    }
    @Test void statisticsCannotBeForgedOrTransferredAcrossLayouts() {
        var c=new SnprelateVariantQc.Counts(10,9,4);var s=SnprelateVariantQc.statistics(c,SNP);
        assertThrows(IllegalArgumentException.class,()->new SnprelateVariantQc.Statistics(c,SNP,0.5,s.minorAlleleFrequency(),s.missingRate()));
        assertThrows(IllegalArgumentException.class,()->new SnprelateVariantQc.Statistics(c,SAMPLE,s.alleleFrequency(),s.minorAlleleFrequency(),s.missingRate()));
    }
    @Test void statisticsAndSelectionRequireExplicitObjects() {
        assertThrows(NullPointerException.class,()->SnprelateVariantQc.statistics(null,SNP));
        assertThrows(NullPointerException.class,()->SnprelateVariantQc.statistics(new SnprelateVariantQc.Counts(1,1,0),null));
        assertThrows(NullPointerException.class,()->SnprelateVariantQc.decide(null,SnprelateVariantQc.Filters.bypass()));
        assertThrows(NullPointerException.class,()->SnprelateVariantQc.decide(stats(1,1,0),null));
        assertThrows(NullPointerException.class,()->SnprelateVariantQc.selected(null));
    }
    @Test void nativeCountKernelFeedsExplicitProfileWithoutChangingCalls() {
        var a=new KingRobustKinship.Accumulator(2,List.of(new KingRobustKinship.Pair(0,1)),3,100000);
        a.accept(new byte[]{1,0});a.accept(new byte[]{1,0});a.accept(new byte[]{-1,2});a.finish();
        assertEquals(new KingRobustKinship.Counts(2,0,0,2,0),a.estimate(0).counts());
        assertEquals(0,SnprelateKingRobust.estimate(a.estimate(0).counts(),WITHIN).kinship());
        assertTrue(Double.isNaN(SnprelateKingRobust.estimate(a.estimate(0).counts(),BETWEEN).kinship()));
    }
}
