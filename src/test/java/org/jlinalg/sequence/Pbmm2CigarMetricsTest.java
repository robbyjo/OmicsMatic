/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Pbmm2CigarMetricsTest {
    @Test void preservesOriginalPublishedBaseCounts() {
        var r = Pbmm2CigarMetrics.compute("5=1X2D3I");
        assertEquals(5, r.matchBases()); assertEquals(1, r.mismatchBases());
        assertEquals(2, r.deletionBases()); assertEquals(3, r.insertionBases());
        assertEquals(9, r.alignedQueryBases()); assertEquals(6, r.editDistance());
    }

    @Test void countsOriginalEventsRatherThanBasesOrMergedOperations() {
        var r = Pbmm2CigarMetrics.compute("1X2=2X2D2I1D1=1X1=1X");
        assertEquals(2, r.deletionEvents()); assertEquals(1, r.insertionEvents());
        assertEquals(3, r.matchEvents()); assertEquals(4, r.mismatchEvents());
        assertEquals(2, Pbmm2CigarMetrics.compute("10=2I3I10=").insertionEvents());
    }

    @Test void retainsDistinctOrdinaryGapCompressedAndQuerySpanConcordance() {
        var deletion = Pbmm2CigarMetrics.compute("100=10D100=");
        var insertion = Pbmm2CigarMetrics.compute("100=10I100=");
        assertEquals(95.2381, deletion.identity(), 0.0001);
        assertEquals(99.50249, deletion.gapCompressedIdentity(), 0.00001);
        assertEquals(deletion.gapCompressedIdentity(), insertion.gapCompressedIdentity());
        assertEquals(95.0, deletion.concordance());
        assertEquals(100 * (1.0 - 10.0 / 210), insertion.concordance());
    }

    @Test void keepsSourceMConventionAndExcludesIntronsClipsAndPadding() {
        assertEquals(Pbmm2CigarMetrics.compute("100=2X3I4D"),
                Pbmm2CigarMetrics.compute("7H9S100M1000N2X3I4D2P5S"));
    }

    @Test void preservesUnassessableArithmeticRatherThanFillingOrInventingIdentity() {
        for (String cigar : new String[]{"", "100S", "10H1000N2P"}) {
            var r = Pbmm2CigarMetrics.compute(cigar);
            assertTrue(Double.isNaN(r.identity())); assertTrue(Double.isNaN(r.gapCompressedIdentity()));
            assertTrue(Double.isNaN(r.concordance())); assertFalse(r.assessable());
        }
    }

    @Test void retainsTheSourceClampAtZeroConcordance() {
        var r = Pbmm2CigarMetrics.compute("1=100D");
        assertEquals(0.0, r.concordance()); assertTrue(r.assessable());
    }

    @Test void retainsFloatTagRounding() {
        var r = Pbmm2CigarMetrics.compute("100=10D100=");
        assertEquals(Float.floatToIntBits((float) (100.0 * 200 / 210)), Float.floatToIntBits(r.identityTag()));
        assertEquals(Float.floatToIntBits((float) (100.0 * 200 / 201)), Float.floatToIntBits(r.gapCompressedIdentityTag()));
    }

    @Test void refusesUnknownOperationsMalformedCountsAndCounterOverflow() {
        for (String cigar : new String[]{"*", "=", "10", "-1=", "1B", "1.5=", "1= 2X"})
            assertThrows(IllegalArgumentException.class, () -> Pbmm2CigarMetrics.compute(cigar));
        for (String cigar : new String[]{"2147483648=", "2147483647=1I", "2147483647D1X"})
            assertThrows(ArithmeticException.class, () -> Pbmm2CigarMetrics.compute(cigar));
    }
}
