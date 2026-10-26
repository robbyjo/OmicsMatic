/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class TrimGaloreReadsTest {
    private static final TrimGaloreReads.Clips NONE = new TrimGaloreReads.Clips(0, 0);
    private static final TrimGaloreReads.Release V10 = TrimGaloreReads.Release.V0_6_10;
    private static FastqIO.Read read(String sequence, int mate) {
        return new FastqIO.Read("@A/" + mate + " original comment", sequence, "I".repeat(sequence.length()));
    }
    private static Integer optionalInteger(String value) { return value.equals("~") ? null : Integer.valueOf(value); }
    private static void emission(String[] row, int first, FastqIO.Read read, boolean emitted) {
        assertEquals(!row[first].equals("~"), emitted, row[0] + "/" + row[1] + "/" + row[14] + "/" + first);
        if (emitted) { assertEquals(row[first], read.sequence()); assertEquals(row[first + 1], read.quality()); }
    }

    @Test void everyFrozenReadEmissionMatchesTheUnmodifiedOriginalPrograms() throws Exception {
        int compared = 0;
        var cases = new HashSet<String>();
        try (var input = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/trim-galore/reference.tsv")), StandardCharsets.UTF_8))) {
            assertEquals(29, input.readLine().split("\t", -1).length);
            String line;
            while ((line = input.readLine()) != null) {
                String[] row = line.split("\t", -1); assertEquals(29, row.length);
                cases.add(row[1] + "/" + row[0]);
                var first = new FastqIO.Read(row[14], row[15], row[16]);
                if (row[2].startsWith("hard")) {
                    var result = TrimGaloreReads.hardtrim(first, Integer.parseInt(row[13]),
                            row[2].equals("hard5") ? TrimGaloreReads.End.FIVE_PRIME : TrimGaloreReads.End.THREE_PRIME);
                    emission(row, 21, result, true); assertEquals(first.header(), result.header());
                } else {
                    var filters = new TrimGaloreReads.Filters(row[1].equals("0.6.7") ? TrimGaloreReads.Release.V0_6_7 : V10,
                            Integer.parseInt(row[7]), Integer.parseInt(row[8]), row[9].equals("~") ? null : Double.valueOf(row[9]));
                    var clips1 = new TrimGaloreReads.Clips(Integer.parseInt(row[3]), Integer.parseInt(row[4]));
                    if (row[2].equals("single")) {
                        var result = TrimGaloreReads.single(first, clips1, filters);
                        emission(row, 21, result.clipped(), result.retained());
                        assertEquals(first.header(), result.clipped().header());
                    } else {
                        var second = new FastqIO.Read(row[17], row[18], row[19]);
                        var result = TrimGaloreReads.paired(first, second, clips1,
                                new TrimGaloreReads.Clips(Integer.parseInt(row[5]), Integer.parseInt(row[6])), filters,
                                new TrimGaloreReads.Rescue(row[10].equals("1"), optionalInteger(row[11]), optionalInteger(row[12])));
                        assertEquals(row[20].equals("1"), result.retained());
                        emission(row, 21, result.read1().read(), result.retained());
                        emission(row, 23, result.read2().read(), result.retained());
                        emission(row, 25, result.read1().read(), result.rescueRead1());
                        emission(row, 27, result.read2().read(), result.rescueRead2());
                        assertEquals(first.header(), result.read1().read().header());
                        assertEquals(second.header(), result.read2().read().header());
                    }
                }
                compared++;
            }
        }
        assertEquals(1692, compared); assertEquals(38, cases.size());
    }

    @Test void fixedCutsUseTheCurrentLengthGuardAndPreserveQualityPositions() {
        var input = new FastqIO.Read("@A comment", "ACGTA", "!#$%&");
        var filters = new TrimGaloreReads.Filters(V10, 1, 0, null);
        var result = TrimGaloreReads.single(input, new TrimGaloreReads.Clips(3, 3), filters);
        assertEquals("TA", result.clipped().sequence()); assertEquals("%&", result.clipped().quality());
        assertFalse(result.clipping().skippedFivePrime()); assertTrue(result.clipping().skippedThreePrime());
        result = TrimGaloreReads.single(input, new TrimGaloreReads.Clips(5, 6), filters);
        assertEquals(input, result.clipped()); assertTrue(result.clipping().skippedFivePrime()); assertTrue(result.clipping().skippedThreePrime());
    }

    @Test void ordinarySingleFractionLimitPreservesTheOriginalAdditionalCountTest() {
        var filters = new TrimGaloreReads.Filters(V10, 1, 0, 0.5);
        assertEquals(TrimGaloreReads.Decision.TOO_MANY_N, TrimGaloreReads.single(read("NACG", 1), NONE, filters).decision());
        assertTrue(TrimGaloreReads.paired(read("NACG", 1), read("ACGT", 2), NONE, NONE, filters,
                new TrimGaloreReads.Rescue(false, null, null)).retained());
        assertTrue(TrimGaloreReads.single(read("nnnn", 1), NONE, filters).retained());
        assertEquals(TrimGaloreReads.Decision.EMPTY_N_FRACTION, TrimGaloreReads.single(read("", 1), NONE, filters).decision());
    }

    @Test void pairRescueRunsOnlyAfterShortLengthAndBeforeMaximumLength() {
        var rescue = new TrimGaloreReads.Rescue(true, null, null);
        var result = TrimGaloreReads.paired(read("A".repeat(10), 1), read("C".repeat(100), 2), NONE, NONE,
                new TrimGaloreReads.Filters(V10, 20, 50, null), rescue);
        assertEquals(TrimGaloreReads.Decision.TOO_SHORT, result.decision());
        assertFalse(result.rescueRead1()); assertTrue(result.rescueRead2());
        result = TrimGaloreReads.paired(read("A".repeat(40), 1), read("C".repeat(40), 2), NONE, NONE,
                new TrimGaloreReads.Filters(V10, 50, 0, null), rescue);
        assertFalse(result.retained()); assertTrue(result.rescueRead1()); assertTrue(result.rescueRead2());
        result = TrimGaloreReads.paired(read("A".repeat(60), 1), read("C".repeat(60), 2), NONE, NONE,
                new TrimGaloreReads.Filters(V10, 20, 50, null), rescue);
        assertEquals(TrimGaloreReads.Decision.TOO_LONG, result.decision()); assertFalse(result.rescueRead1()); assertFalse(result.rescueRead2());
        result = TrimGaloreReads.paired(read("N".repeat(10), 1), read("C".repeat(100), 2), NONE, NONE,
                new TrimGaloreReads.Filters(V10, 20, 50, 0.0), rescue);
        assertEquals(TrimGaloreReads.Decision.TOO_MANY_N, result.decision()); assertFalse(result.rescueRead2());
    }

    @Test void hardtrimKeepsTheDeclaredEndWithoutFilteringShortOrEmptyReads() {
        var input = new FastqIO.Read("@A", "ACGT", "!#$%");
        assertEquals(new FastqIO.Read("@A", "AC", "!#"), TrimGaloreReads.hardtrim(input, 2, TrimGaloreReads.End.FIVE_PRIME));
        assertEquals(new FastqIO.Read("@A", "GT", "$%"), TrimGaloreReads.hardtrim(input, 2, TrimGaloreReads.End.THREE_PRIME));
        assertEquals(input, TrimGaloreReads.hardtrim(input, 50, TrimGaloreReads.End.THREE_PRIME));
        assertEquals(read("", 1), TrimGaloreReads.hardtrim(read("", 1), 50, TrimGaloreReads.End.FIVE_PRIME));
    }

    @Test void invalidOrReleaseInapplicableParametersAndMalformedReadsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TrimGaloreReads.Filters(TrimGaloreReads.Release.V0_6_7, 1, 0, 0.5));
        for (double limit : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 1.5, 101})
            assertThrows(IllegalArgumentException.class, () -> new TrimGaloreReads.Filters(V10, 1, 0, limit));
        assertThrows(IllegalArgumentException.class, () -> new TrimGaloreReads.Clips(100, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrimGaloreReads.Clips(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new TrimGaloreReads.Rescue(false, 35, null));
        assertThrows(IllegalArgumentException.class, () -> TrimGaloreReads.paired(read("ACGT", 1), read("ACGT", 2), NONE, NONE,
                new TrimGaloreReads.Filters(TrimGaloreReads.Release.V0_6_7, 1, 50, null), new TrimGaloreReads.Rescue(false, null, null)));
        assertThrows(IllegalArgumentException.class, () -> TrimGaloreReads.paired(read("ACGT", 1), read("ACGT", 2), NONE, NONE,
                new TrimGaloreReads.Filters(V10, 50, 0, null), new TrimGaloreReads.Rescue(true, 35, null)));
        var filters = new TrimGaloreReads.Filters(V10, 1, 0, null);
        assertThrows(IllegalArgumentException.class, () -> TrimGaloreReads.single(new FastqIO.Read("A", "AC", "II"), NONE, filters));
        assertThrows(IllegalArgumentException.class, () -> TrimGaloreReads.single(new FastqIO.Read("@A", "AX", "II"), NONE, filters));
        assertThrows(IllegalArgumentException.class, () -> TrimGaloreReads.single(new FastqIO.Read("@A", "AC", "I"), NONE, filters));
        assertThrows(IllegalArgumentException.class, () -> TrimGaloreReads.hardtrim(read("AC", 1), 0, TrimGaloreReads.End.FIVE_PRIME));
        assertThrows(IllegalArgumentException.class, () -> TrimGaloreReads.paired(read("AC", 1), new FastqIO.Read("@B/2", "AC", "II"), NONE, NONE,
                filters, new TrimGaloreReads.Rescue(false, null, null)));
    }
}
