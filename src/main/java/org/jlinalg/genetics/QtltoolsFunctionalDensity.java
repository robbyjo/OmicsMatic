/* Copyright (C) 2015 Olivier Delaneau, Halit Ongen, Emmanouil T. Dermitzakis
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Java adaptation of QTLtools 1.3.1 mode_fdensity; modified 21 November 2026.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * QTLtools 1.3.1 fdensity on valid supplied BED intervals. Each output bin counts
 * QTL rows with any annotation overlap. Multiple annotations at one QTL count
 * once. QTL positions use BED start plus one; their end is not the anchor.
 * Only minus strand reverses relative coordinates; dot follows original plus
 * handling. The original small-tree input-order guard is preserved. This is
 * annotation density, not enrichment significance or QTL mapping.
 */
public final class QtltoolsFunctionalDensity {
    /** Explicit allocation ceiling; refusal never changes the requested bin width. */
    public static final int MAXIMUM_BINS = 1_000_000;

    private QtltoolsFunctionalDensity() { }

    /** Inclusive relative bin endpoints and the number of QTL rows with a hit. */
    public record Bin(int start, int end, int qtlCount) { }

    /** Immutable result; mapped annotations share a literal QTL chromosome label. */
    public record Result(List<Bin> bins, int qtlRows, int annotationRows,
                         int chromosomeMappedAnnotationRows, long candidateOverlaps,
                         int sourceOrderGuardedQtlRows) {
        public Result { bins = List.copyOf(bins); }
    }

    private record Range(int first, int last) { }

    /**
     * Compute original bin presences with interval indexing and per-QTL unions.
     * Inputs use zero-based, nonempty half-open BED intervals in one declared
     * assembly. Duplicate QTL rows remain separate. Coordinate/window arithmetic
     * must stay in the original signed-int domain; malformed/overflowing inputs,
     * no shared chromosomes and excessive allocation are refused explicitly.
     * The final bin may extend beyond the window when its width does not divide
     * twice the window; candidate selection still uses the original closed window.
     * For fewer than 64 annotations on a chromosome, QTLtools skips a query whose
     * right endpoint precedes the first input annotation's start. This behavior
     * is input-order dependent and is preserved without sorting the supplied rows.
     *
     * @param qtls declared QTL rows, including literal strand
     * @param annotations declared annotation rows; their strand and ID are unused
     * @param window positive radius in bases
     * @param binWidth positive bin width in bases
     * @param maximumBins explicit permitted allocation, at most MAXIMUM_BINS
     * @return original bin counts and declared input/candidate counts
     */
    public static Result calculate(List<GenomicIntervals.Interval> qtls,
                                   List<GenomicIntervals.Interval> annotations,
                                   int window, int binWidth, int maximumBins) {
        if (qtls == null || annotations == null || qtls.isEmpty() || annotations.isEmpty()
                || window <= 0 || binWidth <= 0 || maximumBins <= 0 || maximumBins > MAXIMUM_BINS)
            throw new IllegalArgumentException("Require nonempty QTL/annotation rows, positive window/bin and explicit bin capacity <= " + MAXIMUM_BINS);
        long number = (2L * window + binWidth - 1L) / binWidth;
        long afterLastBin = -1L * window + number * binWidth;
        if (number > maximumBins || afterLastBin > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Requested bins exceed capacity or original signed-int arithmetic; no bins were changed");
        int count = Math.toIntExact(number);
        var chromosomes = new HashSet<String>();
        for (var qtl : qtls) {
            validCoordinate(qtl);
            if (qtl.start() + 1L + window > Integer.MAX_VALUE)
                throw new IllegalArgumentException("QTL window exceeds original signed-int coordinates");
            chromosomes.add(qtl.chromosome());
        }
        int mapped = 0;
        var firstStarts = new HashMap<String,Long>();
        var annotationCounts = new HashMap<String,Integer>();
        for (var annotation : annotations) {
            validCoordinate(annotation);
            firstStarts.putIfAbsent(annotation.chromosome(),annotation.start()+1L);
            annotationCounts.merge(annotation.chromosome(),1,Integer::sum);
            if (chromosomes.contains(annotation.chromosome())) mapped++;
        }
        if (mapped == 0)
            throw new IllegalArgumentException("No annotation shares an exact QTL chromosome label");
        var index = new GenomicIntervals.Index(annotations);
        long[] differences = new long[count + 1];
        long candidates = 0;
        int guarded = 0;
        for (var qtl : qtls) {
            long position = qtl.start() + 1L;
            Long firstStart = firstStarts.get(qtl.chromosome());
            if (firstStart != null && annotationCounts.get(qtl.chromosome()) < 64
                    && position + window < firstStart) {
                guarded++;
                continue;
            }
            var query = new GenomicIntervals.Interval(qtl.chromosome(),
                Math.max(0L, position - window - 1L), position + window, "fdensity-window", ".");
            var overlaps = index.overlaps(query, false);
            candidates = Math.addExact(candidates, overlaps.size());
            var ranges = new ArrayList<Range>(overlaps.size());
            for (var annotation : overlaps) {
                long start = annotation.start() + 1L - position;
                long end = annotation.end() - position;
                if (qtl.strand().equals("-")) {
                    long previous = start;
                    start = -end;
                    end = -previous;
                }
                long first = Math.max(0L, Math.floorDiv(start + window, binWidth));
                long last = Math.min(count - 1L, Math.floorDiv(end + window, binWidth));
                if (first <= last) ranges.add(new Range(Math.toIntExact(first), Math.toIntExact(last)));
            }
            ranges.sort(Comparator.comparingInt(Range::first).thenComparingInt(Range::last));
            int first = -1, last = -1;
            for (var range : ranges) {
                if (first < 0) { first = range.first(); last = range.last(); }
                else if (range.first() <= last + 1) last = Math.max(last, range.last());
                else {
                    differences[first]++; differences[last + 1]--;
                    first = range.first(); last = range.last();
                }
            }
            if (first >= 0) { differences[first]++; differences[last + 1]--; }
        }
        var bins = new ArrayList<Bin>(count);
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += differences[i];
            long start = -1L * window + (long) i * binWidth;
            if (total < 0 || total > qtls.size()) throw new IllegalStateException("Invalid QTL presence count");
            bins.add(new Bin(Math.toIntExact(start), Math.toIntExact(start + binWidth - 1L), Math.toIntExact(total)));
        }
        return new Result(bins, qtls.size(), annotations.size(), mapped, candidates, guarded);
    }

    private static void validCoordinate(GenomicIntervals.Interval interval) {
        if (Objects.isNull(interval) || interval.end() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Require valid BED coordinates in the original signed-int range");
    }
}
