/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.jlinalg.genetics.GenomicIntervals.Interval;
import org.junit.jupiter.api.Test;

class GenomicIntervalsTest {
    private Interval interval(String id, long first, long last) { return new Interval("chr1", first, last, id, "+"); }

    @Test void agreesWithPublishedBedtoolsIntersectionExampleAndHalfOpenEndpoints() {
        var first = interval("A", 10, 20);
        var second = interval("B", 15, 20);
        assertEquals(5, GenomicIntervals.overlap(first, second));
        assertEquals(0, GenomicIntervals.overlap(first, interval("touching", 20, 30)));
        assertEquals(0, GenomicIntervals.overlap(first, new Interval("1", 15, 20, "other-label", "+")));
        assertEquals(List.of(second), new GenomicIntervals.Index(List.of(second, interval("touching", 20, 30))).overlaps(first, false));
    }

    @Test void indexedOverlapsMatchIndependentBruteForceForNestedDuplicatedAndStrandedIntervals() {
        Random random = new Random(731);
        List<Interval> intervals = new ArrayList<>();
        intervals.add(interval("long", 0, 2000));
        for (int i = 0; i < 1000; i++) {
            int first = random.nextInt(1000), last = first + 1 + random.nextInt(100);
            intervals.add(new Interval(i % 5 == 0 ? "chr2" : "chr1", first, last, "I" + i, i % 2 == 0 ? "+" : "-"));
        }
        var index = new GenomicIntervals.Index(intervals);
        for (int i = 0; i < 200; i++) {
            Interval query = interval("Q", random.nextInt(1100), 1200);
            for (boolean stranded : List.of(false, true)) {
                List<String> expected = intervals.stream().filter(x -> x.chromosome().equals(query.chromosome())
                    && x.start() < query.end() && x.end() > query.start() && (!stranded || x.strand().equals(query.strand())))
                    .map(Interval::id).sorted().toList();
                assertEquals(expected, index.overlaps(query, stranded).stream().map(Interval::id).sorted().toList());
            }
        }
    }

    @Test void coverageIsUnionAndSubtractionRetainsOnlyUncoveredBases() {
        Interval query = interval("Q", 0, 100);
        var masks = List.of(interval("a", 10, 40), interval("b", 20, 60), interval("c", 60, 80));
        var index = new GenomicIntervals.Index(masks);
        assertEquals(70, GenomicIntervals.coveredBases(query, index, false));
        assertEquals(List.of(interval("Q", 0, 10), interval("Q", 80, 100)), GenomicIntervals.subtract(query, index, false));
        var merged = GenomicIntervals.merge(masks, 0, false);
        assertEquals(1, merged.size()); assertEquals(10, merged.get(0).interval().start()); assertEquals(80, merged.get(0).interval().end());
        assertEquals(List.of("a", "b", "c"), merged.get(0).members());
        assertEquals(2, GenomicIntervals.merge(List.of(interval("a", 0, 10), interval("b", 11, 20)), 0, false).size());
        assertEquals(1, GenomicIntervals.merge(List.of(interval("a", 0, 10), interval("b", 11, 20)), 1, false).size());
        assertEquals(Long.MAX_VALUE - 1, GenomicIntervals.overlap(interval("large", 0, Long.MAX_VALUE), interval("large2", 1, Long.MAX_VALUE)));
    }

    @Test void invalidCoordinatesAndUnknownStrandsFail() {
        assertThrows(IllegalArgumentException.class, () -> interval("bad", 5, 5));
        assertThrows(IllegalArgumentException.class, () -> interval("bad", -1, 3));
        var unknown = new Interval("chr1", 0, 10, "unknown", ".");
        assertThrows(IllegalArgumentException.class, () -> new GenomicIntervals.Index(List.of()).overlaps(unknown, true));
        assertThrows(IllegalArgumentException.class, () -> GenomicIntervals.merge(List.of(unknown), 0, true));
    }
}
