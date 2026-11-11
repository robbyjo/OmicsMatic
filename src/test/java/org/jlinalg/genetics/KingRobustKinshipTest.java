/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class KingRobustKinshipTest {
    private static final KingRobustKinship.Pair PAIR = new KingRobustKinship.Pair(0, 1);

    @Test void completeOriginalPopulationsMatchCountsAndBinaryCoefficientsAcrossWordBoundaries() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        try (var resource = getClass().getResourceAsStream("native-king-original-reference.zip")) {
            assertNotNull(resource);
            try (var zip = new ZipInputStream(resource)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    assertNull(files.put(entry.getName(), zip.readAllBytes()));
            }
        }
        int compared = 0;
        for (int variants : new int[] {1, 2, 63, 64, 65, 127, 128, 129}) {
            String home = "variants-"+variants+"/";
            var fam = new String(files.get(home+"cohort.fam"), StandardCharsets.UTF_8).lines()
                .map(line -> line.split("\\s+")).toList();
            var pairs = new ArrayList<KingRobustKinship.Pair>();
            for (int first = 1; first < fam.size(); first++)
                for (int second = 0; second < first; second++) pairs.add(new KingRobustKinship.Pair(first, second));
            var counter = new KingRobustKinship.Accumulator(fam.size(), pairs, variants, 1000000);
            byte[] bed = files.get(home+"cohort.bed");
            assertArrayEquals(new byte[] {0x6c, 0x1b, 1}, java.util.Arrays.copyOf(bed, 3));
            int width = (fam.size()+3)/4;
            assertEquals(3+width*variants, bed.length);
            for (int variant = 0; variant < variants; variant++)
                counter.acceptPackedBed(java.util.Arrays.copyOfRange(bed, 3+variant*width, 3+(variant+1)*width));
            counter.finish();
            var table = new String(files.get(home+"original.kin0"), StandardCharsets.UTF_8).lines().skip(1).toList();
            var binary = ByteBuffer.wrap(files.get(home+"original.king.bin")).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(pairs.size(), table.size()); assertEquals(pairs.size()*8, binary.remaining());
            for (int i = 0; i < pairs.size(); i++) {
                var result = counter.estimate(i); var c = result.counts(); var p = counter.pair(i);
                String[] source = table.get(i).split("\t", -1);
                assertArrayEquals(new String[] {fam.get(p.first())[0], fam.get(p.first())[1],
                    fam.get(p.second())[0], fam.get(p.second())[1]}, java.util.Arrays.copyOf(source, 4));
                assertArrayEquals(new long[] {Long.parseLong(source[4]), Long.parseLong(source[5]), Long.parseLong(source[6]),
                    Long.parseLong(source[7]), Long.parseLong(source[8])}, new long[] {c.jointlyCalled(), c.bothHeterozygous(),
                    c.oppositeHomozygotes(), c.firstHetSecondHom(), c.firstHomSecondHet()});
                double expected = binary.getDouble();
                if (Double.isNaN(expected)) assertTrue(Double.isNaN(result.coefficient()));
                else assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(result.coefficient()));
                assertEquals(Double.isFinite(expected), result.disposition() == KingRobustKinship.Disposition.FINITE);
                compared++;
            }
        }
        assertEquals(960, compared);
    }

    @Test void minimumHeterozygosityEstimatorPreservesNegativeValues() {
        // Counts from the original129-marker s2/s0 pair; not the sum-heterozygosity formula.
        var result = KingRobustKinship.betweenFamily(new KingRobustKinship.Counts(128, 23, 11, 21, 32));
        assertEquals(-0x1.a2e8ba2e8ba30p-5, result.coefficient());
        assertEquals(44, result.counts().smallerHeterozygoteCount());
        assertEquals(53, result.counts().ibs1()); assertEquals(64, result.counts().ibs2());
        assertEquals(41, result.counts().sameHomozygotes());
        assertEquals(KingRobustKinship.Disposition.FINITE, result.disposition());
    }

    @Test void noInformationRetainsNaNAndNegativeInfinityWithDistinctDispositions() {
        var noCalls = KingRobustKinship.betweenFamily(new KingRobustKinship.Counts(0, 0, 0, 0, 0));
        assertTrue(Double.isNaN(noCalls.coefficient()));
        assertEquals(KingRobustKinship.Disposition.NO_JOINT_CALLS, noCalls.disposition());
        var noHets = KingRobustKinship.betweenFamily(new KingRobustKinship.Counts(4, 0, 0, 0, 0));
        assertTrue(Double.isNaN(noHets.coefficient()));
        assertEquals(KingRobustKinship.Disposition.ZERO_SMALLER_HETEROZYGOSITY, noHets.disposition());
        var opposite = KingRobustKinship.betweenFamily(new KingRobustKinship.Counts(4, 0, 4, 0, 0));
        assertEquals(Double.NEGATIVE_INFINITY, opposite.coefficient());
        assertEquals(KingRobustKinship.Disposition.ZERO_SMALLER_HETEROZYGOSITY, opposite.disposition());
    }

    @Test void countsRejectInvalidOrOverlappingPopulations() {
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Counts(-1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Counts(10, 6, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Counts(2147483648L, 0, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> KingRobustKinship.betweenFamily(null));
    }

    @Test void invalidCallsAndCapacitiesCannotPartiallyAddAVariant() {
        var counter = new KingRobustKinship.Accumulator(2, List.of(PAIR), 1, 100000);
        assertThrows(IllegalArgumentException.class, () -> counter.accept(new byte[] {1, 3}));
        assertThrows(IllegalArgumentException.class, () -> counter.accept(new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> counter.acceptPackedBed(new byte[0]));
        assertEquals(0, counter.variantCount());
        counter.accept(new byte[] {1, 1});
        assertThrows(IllegalArgumentException.class, () -> counter.accept(new byte[] {0, 0}));
        assertEquals(1, counter.variantCount());
        assertThrows(IllegalStateException.class, () -> counter.estimate(0));
        counter.finish(); counter.finish();
        assertEquals(new KingRobustKinship.Counts(1, 1, 0, 0, 0), counter.estimate(0).counts());
        assertEquals(0.5, counter.estimate(0).coefficient());
        assertThrows(IllegalStateException.class, () -> counter.accept(new byte[] {1, 1}));
    }

    @Test void pairOrderAndCallerOwnedArraysRemainExplicit() {
        var pairs = new ArrayList<>(List.of(PAIR));
        var first = new KingRobustKinship.Accumulator(2, pairs, 10, 100000);
        var swapped = new KingRobustKinship.Accumulator(2, List.of(new KingRobustKinship.Pair(1, 0)), 10, 100000);
        pairs.clear();
        byte[] row = {1, 0};
        first.accept(row); swapped.accept(row); row[0] = 0;
        first.finish(); swapped.finish();
        assertEquals(PAIR, first.pair(0));
        assertEquals(1, first.estimate(0).counts().firstHetSecondHom());
        assertEquals(1, swapped.estimate(0).counts().firstHomSecondHet());
        assertEquals(first.estimate(0).coefficient(), swapped.estimate(0).coefficient());
    }

    @Test void pairAndWorkingStorageContractsRejectSilentDeduplicationOrHugeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Pair(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Pair(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Accumulator(2,
            List.of(PAIR, new KingRobustKinship.Pair(1, 0)), 2, 100000));
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Accumulator(2,
            List.of(new KingRobustKinship.Pair(0, 2)), 2, 100000));
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Accumulator(2, List.of(PAIR), 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new KingRobustKinship.Accumulator(Integer.MAX_VALUE,
            List.of(PAIR), 1, 100000));
    }

    @Test void packedPaddingOutsideTheDeclaredPopulationDoesNotCreateParticipants() {
        var first = new KingRobustKinship.Accumulator(2, List.of(PAIR), 1, 100000);
        var second = new KingRobustKinship.Accumulator(2, List.of(PAIR), 1, 100000);
        first.acceptPackedBed(new byte[] {0x0a});
        second.acceptPackedBed(new byte[] {(byte) 0xfa});
        first.finish(); second.finish();
        assertEquals(first.estimate(0).counts(), second.estimate(0).counts());
        assertEquals(0.5, second.estimate(0).coefficient());
    }

    @Test void sourceCountCapacityDoesNotOverflowTheFourTimesIbsNumerator() {
        var result = KingRobustKinship.betweenFamily(new KingRobustKinship.Counts(Integer.MAX_VALUE, 1,
            Integer.MAX_VALUE-1L, 0, 0));
        assertEquals(0.5-(Integer.MAX_VALUE-1L), result.coefficient());
        assertTrue(Double.isFinite(result.coefficient()));
    }
}
