/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

class RfmixRangeTest {
    private String argument(double start, double end) throws IOException {
        return RfmixRange.argument(map("start_mbp", start, "end_mbp", end));
    }
    private String notice(int start, int end) {
        return "RFMIX v2.03-r0\nNOTICE: Analysis restricted to positions in range " + start + " to " + end + "\nInitial analysis - logl -1.0\n";
    }
    @Test void exponentMinusCannotBecomeTheRangeDelimiter() throws Exception {
        assertEquals("--analyze-range=0.0001-0.00025", argument(1e-4, 2.5e-4));
    }
    @Test void smallestPositiveDoubleRetainsItsRoundTripValueWithoutExponentSyntax() throws Exception {
        String value = argument(Double.MIN_VALUE, .00025);
        assertEquals(1, value.substring("--analyze-range=".length()).chars().filter(c -> c == '-').count());
        assertFalse(value.contains("E"));
        String token = value.substring("--analyze-range=".length(), value.lastIndexOf('-'));
        assertEquals(Double.doubleToRawLongBits(Double.MIN_VALUE), Double.doubleToRawLongBits(Double.parseDouble(token)));
    }
    @Test void negativeZeroIsAZeroCoordinateWithOneDelimiter() throws Exception {
        assertEquals("--analyze-range=0-0.5", argument(-0.0, .5));
    }
    @Test void printedSourceBpTruncationIsCheckedIndependently() throws Exception {
        var result = RfmixRange.auditSourceNotice(notice(100, 250), true, new int[]{100, 250});
        assertEquals(List.of(100, 250), result.get("source_internal_effective_range_bp"));
    }
    @Test void anIncorrectSourceRangeCannotPassEvenIfTheAvailableSnpPopulationIsIdentical() {
        assertThrows(IOException.class, () -> RfmixRange.auditSourceNotice(notice(1000000, 4000000), true, new int[]{100, 2000000}));
    }
    @Test void missingOrUnexpectedSourceRestrictionFails() {
        assertThrows(IOException.class, () -> RfmixRange.auditSourceNotice("no notice\n", true, new int[]{100, 250}));
        assertThrows(IOException.class, () -> RfmixRange.auditSourceNotice(notice(100, 250), false, new int[]{0, Integer.MAX_VALUE}));
    }
    @Test void duplicateOrMalformedSourceNoticesFail() {
        assertThrows(IOException.class, () -> RfmixRange.auditSourceNotice(notice(100, 250) + notice(100, 250), true, new int[]{100, 250}));
        assertThrows(IOException.class, () -> RfmixRange.auditSourceNotice(notice(100, 250).replace("100 to", "100.0 to"), true, new int[]{100, 250}));
    }
    @Test void sourceNoticeCannotOverflowOrContainTrailingRepairableText() {
        assertThrows(IOException.class, () -> RfmixRange.auditSourceNotice(notice(100, 250).replace("100 to", "2147483648 to"), true, new int[]{100, 250}));
        assertThrows(IOException.class, () -> RfmixRange.auditSourceNotice(notice(100, 250).replace("250\n", "250 extra\n"), true, new int[]{100, 250}));
    }
    @Test void unrestrictedPositiveVcfDomainDoesNotMisstateTheOriginalInternalDefault() throws Exception {
        var result = RfmixRange.auditSourceNotice("RFMIX\nInitial analysis - logl -1.0\n", false, new int[]{0, Integer.MAX_VALUE});
        assertEquals(List.of(Integer.MIN_VALUE, Integer.MAX_VALUE), result.get("source_internal_effective_range_bp"));
        assertEquals(List.of(0, Integer.MAX_VALUE), result.get("positive_VCF_selection_domain_bp"));
    }
    @Test void formatterRefusesInvalidOrUnrepresentableCoordinates() {
        for (double[] pair : new double[][]{{-1e-4, .5}, {1, .5}, {0, Double.NaN}, {0, 2148}, {0, Double.POSITIVE_INFINITY}})
            assertThrows(IOException.class, () -> argument(pair[0], pair[1]));
    }
}
