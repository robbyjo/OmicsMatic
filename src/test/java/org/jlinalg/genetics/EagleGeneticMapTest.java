/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.EagleGeneticMap.Limits;
import org.jlinalg.genetics.EagleGeneticMap.Outside;
import org.jlinalg.genetics.EagleGeneticMap.Region;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EagleGeneticMapTest {
    private static final String TWO = "1 100 9999 0.2\n1 200 -9999 0.3\n1 400 0 0.9\n2 100 7 2\n2 300 8 3\n";
    private static EagleGeneticMap map(String rows, int chr, Outside outside) throws IOException {
        return EagleGeneticMap.read(new StringReader(EagleGeneticMap.HEADER + "\n" + rows), chr, outside, Limits.defaults());
    }
    private static void refuse(String rows) { assertThrows(IOException.class, () -> map(rows, 1, Outside.SOURCE_BOUNDARIES)); }

    @Test void interpolationUsesCentimorgansAndReturnsMorgans() throws Exception {
        var m = map(TWO, 1, Outside.REQUIRE_BRACKET);
        assertEquals(0.0025, m.estimate(150).morgans(), 1e-16);
        assertEquals(0.006, m.estimate(300).morgans(), 1e-16);
        assertEquals(Region.BETWEEN_ROWS, m.estimate(150).region());
    }
    @Test void sourceRateColumnDoesNotControlTheSlope() throws Exception {
        var m = map(TWO, 1, Outside.REQUIRE_BRACKET);
        var other = map(TWO.replace("9999", "0").replace("-0", "12345"), 1, Outside.REQUIRE_BRACKET);
        for (int p : new int[]{100, 150, 200, 299, 400}) assertEquals(m.estimate(p), other.estimate(p));
    }
    @Test void exactRowsHaveExplicitDisposition() throws Exception {
        var m = map(TWO, 1, Outside.REQUIRE_BRACKET);
        assertEquals(Region.EXACT_ROW, m.estimate(100).region());
        assertEquals(Region.EXACT_ROW, m.estimate(200).region());
        assertEquals(0.003, m.estimate(200).morgans(), 1e-16);
    }
    @Test void nonfinalChromosomeHasOriginalConstantRightTail() throws Exception {
        var m = map(TWO, 1, Outside.SOURCE_BOUNDARIES);
        assertEquals(0.009, m.estimate(450).morgans(), 1e-16);
        assertEquals(0.009, m.estimate(Integer.MAX_VALUE).morgans(), 1e-16);
        assertEquals(Region.AFTER_LAST_ROW, m.estimate(450).region());
    }
    @Test void finalChromosomeHasOriginalOneCmPerMbTail() throws Exception {
        var m = map(TWO, 2, Outside.SOURCE_BOUNDARIES);
        assertEquals(0.0300005, m.estimate(350).morgans(), 2e-15);
        assertEquals(Region.AFTER_LAST_ROW, m.estimate(350).region());
        assertThrows(IllegalArgumentException.class, () -> m.estimate(1_000_000_300));
    }
    @Test void leftExtrapolationUsesFirstTwoRowsAndMayBeNegative() throws Exception {
        var m = map("1 100 1 0\n1 200 1 10\n", 1, Outside.SOURCE_BOUNDARIES);
        assertEquals(-0.099, m.estimate(1).morgans(), 1e-15);
        assertEquals(Region.BEFORE_FIRST_ROW, m.estimate(1).region());
    }
    @Test void bracketPolicyRefusesBothTailsWithoutClamping() throws Exception {
        var m = map(TWO, 1, Outside.REQUIRE_BRACKET);
        assertThrows(IllegalArgumentException.class, () -> m.estimate(99));
        assertThrows(IllegalArgumentException.class, () -> m.estimate(401));
        assertThrows(IllegalArgumentException.class, () -> m.estimate(0));
    }
    @Test void completeInventoryIncludesUnselectedChromosomesAndBlankLines() throws Exception {
        String body = "\n" + TWO + " \t\n";
        var m = map(body, 1, Outside.REQUIRE_BRACKET);
        assertEquals(5, m.summary().rows()); assertEquals(3, m.summary().selectedRows());
        assertEquals(2, m.summary().chromosomes()); assertEquals(2, m.summary().finalChromosome());
        assertEquals(8, m.summary().lines()); assertEquals(EagleGeneticMap.HEADER.length() + 1L + body.length(), m.summary().characters());
        assertEquals(12 * 32, m.summary().retainedArrayBytes());
    }
    @Test void finalLineMayOmitLfAndBodyMayUseAsciiWhitespace() throws Exception {
        var m = map("1\t100\t1\t0.2\r\n1 200 2 0.3", 1, Outside.REQUIRE_BRACKET);
        assertEquals(2, m.summary().rows()); assertEquals(0.0025, m.estimate(150).morgans(), 1e-16);
    }
    @Test void exactOriginalHeaderIsRequiredIncludingCrBehavior() {
        for (String header : new String[]{"", "#" + EagleGeneticMap.HEADER, EagleGeneticMap.HEADER + "\r", " " + EagleGeneticMap.HEADER})
            assertThrows(IOException.class, () -> EagleGeneticMap.read(new StringReader(header + "\n" + TWO), 1, Outside.REQUIRE_BRACKET, Limits.defaults()));
    }
    @Test void malformedTrailingRecordIsNotAcceptedAsSuccessfulPrefix() {
        refuse("1 100 1 0\n1 200 1 1\nBROKEN\n");
        refuse("1 100 1 0\n1 200 1\n");
        refuse("1 100 1 0\n1 200 1 1 trailing\n");
    }
    @Test void selectedChromosomeMustExistWithAtLeastTwoOriginalRows() {
        assertThrows(IOException.class, () -> map(TWO, 3, Outside.SOURCE_BOUNDARIES));
        refuse("1 100 1 0.2\n2 100 1 0\n2 200 1 0.2\n");
    }
    @Test void chromosomeReappearanceIsRejectedAcrossCompleteInput() {
        refuse("1 100 1 0\n1 200 1 1\n2 100 1 0\n1 300 1 2\n");
    }
    @Test void uniqueChromosomeSectionOrderIsPreservedWithoutNumericalResorting() throws Exception {
        var m = map("2 100 1 .2\n2 200 1 .4\n1 100 1 .1\n1 200 1 .2\n", 2, Outside.SOURCE_BOUNDARIES);
        assertEquals(1, m.summary().finalChromosome()); assertEquals(2, m.summary().maximumStoredChromosome());
        assertEquals(.003, m.estimate(150).morgans(), 1e-16);
        assertThrows(IllegalArgumentException.class, () -> m.estimate(200));
    }
    @Test void sourceSentinelCanHaveHigherNumericChromosomeAndThenAConstantTail() throws Exception {
        var m = map("2 100 1 .2\n2 200 1 .4\n1 100 1 .1\n1 200 1 .2\n", 1, Outside.SOURCE_BOUNDARIES);
        assertEquals(.0020005, m.estimate(250).morgans(), 2e-15);
        assertEquals(10.002, m.estimate(1_000_000_200).morgans(), 2e-14);
        assertEquals(10.002, m.estimate(Integer.MAX_VALUE).morgans(), 2e-14);
    }
    @Test void duplicatedAndDecreasingPositionsAreRejected() {
        refuse("1 100 1 0\n1 100 2 1\n");
        refuse("1 200 1 0\n1 100 2 1\n");
    }
    @Test void decreasingGeneticPositionsAreRejected() {
        refuse("1 100 1 0.3\n1 200 1 0.2\n");
    }
    @Test void signedGeneticCoordinatesAreRetainedWithoutOriginShifting() throws Exception {
        var m = map("1 100 1 -3\n1 200 1 -1\n", 1, Outside.REQUIRE_BRACKET);
        assertEquals(-0.02, m.estimate(150).morgans(), 1e-16);
    }
    @Test void nonfiniteNondecimalAndNonzeroUnderflowNumbersAreRejected() {
        for (String value : new String[]{"NaN", "Infinity", "1e999", "1e-999", "0x1.0p0", "1f"}) {
            refuse("1 100 " + value + " 0\n1 200 1 1\n");
            refuse("1 100 1 0\n1 200 1 " + value + "\n");
        }
    }
    @Test void genuinelyZeroMantissaDoesNotBecomeAnUnderflowError() throws Exception {
        var m = map("1 100 0e-999 0e-999\n1 200 -0e-999 1\n", 1, Outside.REQUIRE_BRACKET);
        assertEquals(.005, m.estimate(150).morgans(), 1e-16);
    }
    @Test void originalDerivedRateOverflowCannotProduceAdmittedInfinity() { refuse("1 100 1 0\n1 101 1 1e308\n"); }
    @Test void finalSentinelOverflowIsRejectedBeforeInterpolation() { refuse("1 1500000000 1 0\n1 1500000001 1 1\n"); }
    @Test void decimalIntCapacityAndPositiveRowsAfterTheAnchorAreRequired() {
        for (String value : new String[]{"1.5", "2147483648", "0x10"}) refuse("1 " + value + " 1 0\n1 200 1 1\n");
        refuse("1 -100 1 0\n1 0 1 1\n");
    }
    @Test void originalLinearMapZeroAnchorAndExplicitIntegerSignsAreSupported() throws Exception {
        var m = map("+1 +0 +1 +0\n+1 +1000000000 +1 +1000\n", 1, Outside.REQUIRE_BRACKET);
        assertEquals(0, m.firstPosition());
        assertEquals(.5, m.estimate(50_000_000).morgans(), 1e-15);
        assertEquals(10, m.estimate(1_000_000_000).morgans(), 1e-14);
        assertThrows(IllegalArgumentException.class, () -> m.estimate(0));
    }
    @Test void signedFirstAnchorRetainsItsOriginalCoordinateAndSlope() throws Exception {
        var m = map("1 -100 1 -1\n1 100 1 1\n", 1, Outside.REQUIRE_BRACKET);
        assertEquals(-100, m.firstPosition()); assertEquals(.005, m.estimate(50).morgans(), 1e-16);
    }
    @Test void originalSignedIntIntervalOverflowIsRefusedWithoutClamping() {
        refuse("1 -2147483648 1 0\n1 100 1 1\n");
    }
    @Test void completeAndSelectedRowAndChromosomeLimitsAreIndependent() {
        String text = EagleGeneticMap.HEADER + "\n" + TWO;
        for (Limits limits : new Limits[]{new Limits(4, 10, 100, 1000, 10), new Limits(10, 2, 100, 1000, 10), new Limits(10, 10, 100, 1000, 1)})
            assertThrows(IOException.class, () -> EagleGeneticMap.read(new StringReader(text), 1, Outside.REQUIRE_BRACKET, limits));
    }
    @Test void exactDecodedCharacterCapacityPassesAndOneLessFails() throws Exception {
        String text = EagleGeneticMap.HEADER + "\n" + TWO;
        var exact = new Limits(10, 10, 100, text.length(), 10);
        assertEquals(5, EagleGeneticMap.read(new StringReader(text), 1, Outside.REQUIRE_BRACKET, exact).summary().rows());
        assertThrows(IOException.class, () -> EagleGeneticMap.read(new StringReader(text), 1, Outside.REQUIRE_BRACKET,
                new Limits(10, 10, 100, text.length() - 1L, 10)));
    }
    @Test void physicalLineLimitIsEnforcedBeforeUnboundedTokenAllocation() {
        String text = EagleGeneticMap.HEADER + "\n1 100 " + "0".repeat(1000) + " 0\n1 200 1 1\n";
        assertThrows(IOException.class, () -> EagleGeneticMap.read(new StringReader(text), 1, Outside.REQUIRE_BRACKET,
                new Limits(10, 10, 100, 10000, 10)));
    }
    @Test void callerOwnsTheReaderAndCheckFailurePreventsAdmission() throws Exception {
        class Tracked extends StringReader { boolean closed; Tracked(String value) { super(value); } @Override public void close() { closed = true; super.close(); } }
        var reader = new Tracked(EagleGeneticMap.HEADER + "\n" + TWO);
        EagleGeneticMap.read(reader, 1, Outside.REQUIRE_BRACKET, Limits.defaults()); assertFalse(reader.closed);
        var count = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> EagleGeneticMap.read(new StringReader(EagleGeneticMap.HEADER + "\n" + TWO),
                1, Outside.REQUIRE_BRACKET, Limits.defaults(), () -> { if (count.incrementAndGet() == 2) throw new IllegalStateException("cancelled at final admission"); }));
    }
    @Test void growthRetainsEverySelectedPointAndReportsActualArrayCapacity() throws Exception {
        var rows = new StringBuilder(); for (int p = 1; p <= 100; p++) rows.append("1 ").append(p).append(" 1 ").append(p).append('\n');
        var m = map(rows.toString(), 1, Outside.REQUIRE_BRACKET);
        assertEquals(100, m.summary().selectedRows()); assertEquals(12 * 128, m.summary().retainedArrayBytes());
        assertEquals(0.5, m.estimate(50).morgans(), 1e-15);
    }
}
