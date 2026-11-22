/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jlinalg.genetics.GenomicRangeGeometry.Range;
import org.jlinalg.genetics.GenomicRangeGeometry.Sequence;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Actual original-R tables are the oracle; these tests do not recalculate them. */
class GenomicRangeGeometryReferenceTest {
    private static final String ROOT = "/org/jlinalg/genetics/genomicranges-geometry/1.64.0/";

    private static List<String[]> table(String name, String digest) throws Exception {
        byte[] bytes;
        try (var input = GenomicRangeGeometryReferenceTest.class.getResourceAsStream(ROOT + name)) {
            if (input == null) throw new IOException("Missing original reference " + name);
            bytes = input.readAllBytes();
        }
        assertEquals(digest, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), name);
        return new String(bytes, StandardCharsets.UTF_8).lines().skip(1).map(s -> s.split("\t", -1)).toList();
    }

    @TestFactory Stream<DynamicTest> agreesWithOriginal164PromotersAndBoundaryIndices() throws Exception {
        var cases = table("cases.tsv", "130d14fdd44042cf7e66c9551b2bf798a2225b7d1c5c408ebabd3ddbe0b6df1b");
        var rows = table("ranges.tsv", "3fb59f7b7680dd7e38dc9f74233b393318d1c0dd0ac659a294912894a5576e0a");
        var levels = table("sequences.tsv", "343437ff64660fbbe2423e79b795c2df767994ff8edca558e8c59bed9102a09d");
        assertEquals(17, cases.size()); assertEquals(4096, rows.size()); assertEquals(6, levels.size());
        Map<String, Sequence> sequences = new LinkedHashMap<>();
        for (String[] level : levels) {
            sequences.put(decode(level[0]), new Sequence(level[1].equals("NA") ? null : Integer.valueOf(level[1]),
                level[2].equals("NA") ? null : level[2].equals("TRUE")));
        }
        return cases.stream().map(c -> DynamicTest.dynamicTest("original-R-case-" + c[0], () -> {
            List<Range> inputs = new ArrayList<>(), expected = new ArrayList<>();
            List<Integer> outside = new ArrayList<>();
            for (String[] row : rows) if (row[0].equals(c[0])) {
                assertEquals(inputs.size() + 1, Integer.parseInt(row[1]));
                inputs.add(new Range(decode(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4]), row[5]));
                expected.add(new Range(decode(row[2]), Integer.parseInt(row[6]), Integer.parseInt(row[7]), row[5]));
                if (row[8].equals("TRUE")) outside.add(Integer.parseInt(row[1]));
                else assertEquals("FALSE", row[8]);
            }
            assertEquals(Integer.parseInt(c[3]), inputs.size());
            var actual = GenomicRangeGeometry.promoters(inputs, Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            assertEquals(expected, actual, "All original coordinates, literal labels, strands and order");
            assertEquals(Integer.parseInt(c[4]), outside.size());
            assertArrayEquals(outside.stream().mapToInt(Integer::intValue).toArray(),
                GenomicRangeGeometry.outOfBoundsOneBasedIndices(actual, sequences));
        }));
    }

    private static String decode(String hex) {
        return new String(HexFormat.of().parseHex(hex), StandardCharsets.UTF_8);
    }

    @Test void refusesReservedMissingIntegersNegativeWidthsAndUnknownStrands() {
        assertThrows(IllegalArgumentException.class, () -> new Range("chr1", Integer.MIN_VALUE, 0, "+"));
        assertThrows(IllegalArgumentException.class, () -> new Range("chr1", 2, 0, "+"));
        assertThrows(IllegalArgumentException.class, () -> new Range("chr1", 1, 1, "."));
        assertThrows(IllegalArgumentException.class, () -> new Range("", 1, 1, "+"));
        assertThrows(IllegalArgumentException.class, () -> new Sequence(-1, null));
    }

    @Test void refusesIntermediateOverflowEvenWhenFinalMathematicalEndpointWouldFit() {
        assertThrows(IllegalArgumentException.class, () -> GenomicRangeGeometry.promoters(
            List.of(new Range("chr1", Integer.MAX_VALUE, Integer.MAX_VALUE, "+")), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GenomicRangeGeometry.promoters(
            List.of(new Range("chr1", GenomicRangeGeometry.MINIMUM_R_INTEGER, GenomicRangeGeometry.MINIMUM_R_INTEGER, "-")), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GenomicRangeGeometry.promoters(
            List.of(new Range("chr1", GenomicRangeGeometry.MINIMUM_R_INTEGER, GenomicRangeGeometry.MINIMUM_R_INTEGER, "+")), 1, 0));
    }

    @Test void refusesMissingLiteralSequenceLevelsAndNonemptyNegativeDistances() {
        var row = new Range(" chr1 ", 1, 1, "*");
        assertThrows(IllegalArgumentException.class, () -> GenomicRangeGeometry.outOfBoundsOneBasedIndices(
            List.of(row), Map.of("chr1", new Sequence(10, false))));
        assertThrows(IllegalArgumentException.class, () -> GenomicRangeGeometry.promoters(List.of(row), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> GenomicRangeGeometry.promoters(List.of(row), 0, -1));
        assertEquals(0, new Range("chr1", 0, -1, "*").width());
    }

    @Test void outputsCannotBeMutated() {
        var actual = GenomicRangeGeometry.promoters(List.of(new Range("chr1", 100, 100, "+")), 1, 1);
        assertThrows(UnsupportedOperationException.class, () -> actual.clear());
    }
}
