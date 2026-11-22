/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scalar promoter and boundary geometry for explicitly represented genomic ranges.
 * Coordinates are signed, nonmissing R integers with one-based closed endpoints;
 * zero-width ranges have {@code end == start - 1}. This class does not implement
 * R S4 containers, metadata coercion, chromosome aliases, sequence extraction or
 * liftover. It never trims or wraps a promoter. Original GenomicRanges source
 * by Aboyoun, Pages and Lawrence defines the reference behavior; the original
 * package is distributed under Artistic-2.0.
 */
public final class GenomicRangeGeometry {
    private GenomicRangeGeometry() { }
    /** R reserves the most negative 32-bit integer for NA. */
    public static final int MINIMUM_R_INTEGER = Integer.MIN_VALUE + 1;

    /** A range with literal chromosome and source strand (+,-,*). */
    public record Range(String chromosome, int start, int end, String strand) {
        public Range {
            Objects.requireNonNull(chromosome, "chromosome");
            if (chromosome.isEmpty() || start == Integer.MIN_VALUE || end == Integer.MIN_VALUE
                    || !List.of("+", "-", "*").contains(strand)) {
                throw new IllegalArgumentException("Require a literal chromosome, nonmissing R integers and strand +,-,*");
            }
            long width = (long) end - start + 1;
            if (width < 0 || width > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Range width must be a nonnegative R integer");
            }
        }
        /** Width, including the supported zero-width case. */
        public int width() { return (int) ((long) end - start + 1); }
    }

    /** Null length/circularity represent source NA, not zero or FALSE. */
    public record Sequence(Integer length, Boolean circular) {
        public Sequence {
            if (length != null && length < 0) {
                throw new IllegalArgumentException("Sequence length must be nonnegative or unknown");
            }
        }
    }

    /**
     * Return promoter geometry in input order using scalar upstream/downstream
     * arguments. '*' uses the plus-strand rule. Empty input retains the source
     * early return, including when unused scalar distances are negative.
     * Unsupported integer overflow refuses the operation rather than repairing
     * an NA endpoint. Names and metadata stay the caller's responsibility.
     * @param input ordered ranges
     * @param upstream nonnegative upstream bases for nonempty input
     * @param downstream nonnegative downstream bases for nonempty input
     * @return immutable transformed ranges, without trimming or wrapping
     */
    public static List<Range> promoters(List<Range> input, int upstream, int downstream) {
        Objects.requireNonNull(input, "input");
        if (input.isEmpty()) return List.of();
        if (upstream < 0 || downstream < 0) {
            throw new IllegalArgumentException("Promoter distances must be nonnegative integers");
        }
        var output = new ArrayList<Range>(input.size());
        for (Range range : input) {
            Objects.requireNonNull(range, "range");
            int start, end;
            if (range.strand().equals("-")) {
                end = integer((long) range.end() + upstream);
                start = integer((long) integer((long) range.end() - downstream) + 1);
            } else {
                start = integer((long) range.start() - upstream);
                end = integer((long) integer((long) range.start() + downstream) - 1);
            }
            output.add(new Range(range.chromosome(), start, end, range.strand()));
        }
        return List.copyOf(output);
    }

    /**
     * Return one-based indices of ranges outside known noncircular bounds, in
     * input order. A TRUE circular flag or unknown length disables that level's
     * bounds, while unknown circularity with known length still has bounds.
     * This matches get_out_of_bound_index's geometry; it does not filter rows.
     * @param input ordered ranges
     * @param sequences literal sequence-level metadata, including every used label
     * @return original one-based row indices
     */
    public static int[] outOfBoundsOneBasedIndices(List<Range> input, Map<String, Sequence> sequences) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(sequences, "sequences");
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < input.size(); i++) {
            Range range = Objects.requireNonNull(input.get(i), "range");
            Sequence sequence = sequences.get(range.chromosome());
            if (sequence == null) throw new IllegalArgumentException("Missing literal sequence level: " + range.chromosome());
            if (sequence.length() != null && !Boolean.TRUE.equals(sequence.circular())
                    && (range.start() < 1 || range.end() > sequence.length())) indices.add(i + 1);
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int integer(long value) {
        if (value < MINIMUM_R_INTEGER || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Promoter intermediate exceeds nonmissing R integer capacity; no endpoint repaired");
        }
        return (int) value;
    }
}
