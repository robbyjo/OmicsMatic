/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.Objects;

/**
 * Native read operations matching the ordinary (non-RRBS, non-poly-A) final
 * filtering stage of Trim Galore 0.6.7 and 0.6.10. Inputs must already have
 * undergone any requested adapter and quality trimming. These methods do not
 * perform adapter detection, Cutadapt alignment, or a complete Trim Galore run.
 */
public final class TrimGaloreReads {
    private TrimGaloreReads() { }

    /** Explicit original-release semantics; 0.6.7 accepts integer N limits only. */
    public enum Release { V0_6_7, V0_6_10 }

    /** End whose first/last bases are retained by the original hardtrim mode. */
    public enum End { FIVE_PRIME, THREE_PRIME }

    /** A filter decision; EMPTY_N_FRACTION is the original zero-denominator guard. */
    public enum Decision { RETAINED, TOO_MANY_N, EMPTY_N_FRACTION, TOO_SHORT, TOO_LONG }

    /** Requested fixed cuts from the 5' and 3' ends, in that order; zero disables a cut. */
    public record Clips(int fivePrime, int threePrime) {
        public Clips {
            if (fivePrime < 0 || fivePrime > 99 || threePrime < 0 || threePrime > 99)
                throw new IllegalArgumentException("Fixed cuts must be zero (disabled) or 1..99 bases");
        }
    }

    /**
     * Common final filters. maximumLength=0 disables the upper bound;
     * maximumN=null disables N filtering. Otherwise maximumN is an integer
     * 0..100, or (only in 0.6.10) a fraction strictly between zero and one.
     * Only uppercase N is counted, as in the original releases.
     */
    public record Filters(Release release, int minimumLength, int maximumLength, Double maximumN) {
        public Filters {
            Objects.requireNonNull(release, "release");
            if (minimumLength < 1 || minimumLength > 10_000_000 || maximumLength < 0 || maximumLength > 10_000_000)
                throw new IllegalArgumentException("Minimum length must be 1..10000000; maximum length 0..10000000");
            if (maximumN != null && (!Double.isFinite(maximumN) || maximumN < 0 || maximumN > 100
                    || maximumN != Math.rint(maximumN) && !(release == Release.V0_6_10 && maximumN > 0 && maximumN < 1)))
                throw new IllegalArgumentException("N limit must be an integer 0..100, or a 0.6.10 fraction in (0,1)");
        }
        /** Whether the declared limit denotes a fraction rather than a count. */
        public boolean fractionalN() { return maximumN != null && maximumN > 0 && maximumN < 1; }
    }

    /**
     * Unpaired-read rescue. Null thresholds select the original defaults (35),
     * which can be smaller than the paired cutoff. Explicit thresholds must be
     * 15..100 and greater than the paired cutoff, as checked by paired().
     */
    public record Rescue(boolean enabled, Integer minimumRead1, Integer minimumRead2) {
        public Rescue {
            if (!enabled && (minimumRead1 != null || minimumRead2 != null))
                throw new IllegalArgumentException("Explicit orphan thresholds require rescue");
            if (minimumRead1 != null && (minimumRead1 < 15 || minimumRead1 > 100)
                    || minimumRead2 != null && (minimumRead2 < 15 || minimumRead2 > 100))
                throw new IllegalArgumentException("Explicit orphan thresholds must be 15..100");
        }
        /** Resolved first-mate cutoff, including the original default. */
        public int resolvedRead1() { return minimumRead1 == null ? 35 : minimumRead1; }
        /** Resolved second-mate cutoff, including the original default. */
        public int resolvedRead2() { return minimumRead2 == null ? 35 : minimumRead2; }
    }

    /** Fixed-cut result, including guards that left a requested cut unapplied. */
    public record Clipped(FastqIO.Read read, boolean skippedFivePrime, boolean skippedThreePrime) { }

    /** Clipped read and its final decision; rejected reads are retained here for audit. */
    public record Single(Clipped clipping, Decision decision) {
        /** The processed read, whether retained or rejected. */
        public FastqIO.Read clipped() { return clipping.read(); }
        /** Whether this read belongs in the emitted single-end stream. */
        public boolean retained() { return decision == Decision.RETAINED; }
    }

    /**
     * Clipped mates and the pair decision. Rescue streams are independent;
     * both can contain a mate when a default threshold is below the pair cutoff.
     */
    public record Pair(Clipped read1, Clipped read2, Decision decision, boolean rescueRead1, boolean rescueRead2) {
        /** Whether both mates belong in the validated paired streams. */
        public boolean retained() { return decision == Decision.RETAINED; }
    }

    /**
     * Retain the first or last count bases without a length filter, matching
     * --hardtrim5/--hardtrim3. This operation bypasses adapter/quality trimming.
     */
    public static FastqIO.Read hardtrim(FastqIO.Read read, int count, End end) {
        validate(read); Objects.requireNonNull(end, "end");
        if (count < 1 || count > 999) throw new IllegalArgumentException("Hardtrim count must be 1..999");
        int length = read.sequence().length();
        int start = end == End.FIVE_PRIME ? 0 : Math.max(0, length - count);
        int finish = end == End.FIVE_PRIME ? Math.min(length, count) : length;
        return span(read, start, finish);
    }

    /** Apply ordinary single-end post-Cutadapt clipping, N, minimum and maximum filters. */
    public static Single single(FastqIO.Read read, Clips clips, Filters filters) {
        Objects.requireNonNull(filters, "filters");
        Clipped clipped = clip(read, clips);
        Decision decision = nDecision(clipped.read(), filters, true);
        if (decision == Decision.RETAINED) decision = lengthDecision(clipped.read(), filters);
        return new Single(clipped, decision);
    }

    /**
     * Apply the original paired finalization order: clip; N-filter the pair;
     * minimum length with optional rescue; maximum length; retain the pair.
     * Header identity/order is checked before processing. No rescue is attempted
     * after N or maximum-length rejection. In the short-pair branch, the
     * original rescue test does not apply maximumLength to the rescued mate.
     */
    public static Pair paired(FastqIO.Read read1, FastqIO.Read read2, Clips clips1, Clips clips2,
                              Filters filters, Rescue rescue) {
        Objects.requireNonNull(filters, "filters"); Objects.requireNonNull(rescue, "rescue");
        validate(read1); validate(read2);
        if (!read1.pairId().equals(read2.pairId())) throw new IllegalArgumentException("Mates must have the same pair identity");
        if (filters.release() == Release.V0_6_7 && filters.maximumLength() > 0)
            throw new IllegalArgumentException("Original 0.6.7 rejects maximum-length filtering in paired mode");
        if (rescue.minimumRead1() != null && rescue.minimumRead1() <= filters.minimumLength()
                || rescue.minimumRead2() != null && rescue.minimumRead2() <= filters.minimumLength())
            throw new IllegalArgumentException("Explicit orphan thresholds must exceed the paired minimum");
        Clipped first = clip(read1, clips1), second = clip(read2, clips2);
        Decision decision = nDecision(first.read(), filters, false);
        if (decision == Decision.RETAINED) decision = nDecision(second.read(), filters, false);
        if (decision != Decision.RETAINED) return new Pair(first, second, decision, false, false);
        int length1 = first.read().sequence().length(), length2 = second.read().sequence().length();
        if (length1 < filters.minimumLength() || length2 < filters.minimumLength())
            return new Pair(first, second, Decision.TOO_SHORT,
                    rescue.enabled() && length1 >= rescue.resolvedRead1(),
                    rescue.enabled() && length2 >= rescue.resolvedRead2());
        if (filters.maximumLength() > 0 && (length1 > filters.maximumLength() || length2 > filters.maximumLength()))
            return new Pair(first, second, Decision.TOO_LONG, false, false);
        return new Pair(first, second, Decision.RETAINED, false, false);
    }

    private static Clipped clip(FastqIO.Read read, Clips clips) {
        validate(read); Objects.requireNonNull(clips, "clips");
        int start = 0, finish = read.sequence().length();
        boolean skipped5 = clips.fivePrime() > 0 && finish <= clips.fivePrime();
        if (clips.fivePrime() > 0 && !skipped5) start += clips.fivePrime();
        boolean skipped3 = clips.threePrime() > 0 && finish - start <= clips.threePrime();
        if (clips.threePrime() > 0 && !skipped3) finish -= clips.threePrime();
        return new Clipped(span(read, start, finish), skipped5, skipped3);
    }

    private static Decision nDecision(FastqIO.Read read, Filters filters, boolean ordinarySingle) {
        if (filters.maximumN() == null) return Decision.RETAINED;
        long count = read.sequence().chars().filter(c -> c == 'N').count();
        if (filters.fractionalN()) {
            if (read.sequence().isEmpty()) return Decision.EMPTY_N_FRACTION;
            if ((double) count / read.sequence().length() > filters.maximumN()) return Decision.TOO_MANY_N;
            // Original 0.6.10 ordinary SE code also performs the count comparison.
            // Its RRBS and paired branches use an else and do not do so.
            if (!ordinarySingle) return Decision.RETAINED;
        }
        return count > filters.maximumN() ? Decision.TOO_MANY_N : Decision.RETAINED;
    }

    private static Decision lengthDecision(FastqIO.Read read, Filters filters) {
        int length = read.sequence().length();
        if (length < filters.minimumLength()) return Decision.TOO_SHORT;
        if (filters.maximumLength() > 0 && length > filters.maximumLength()) return Decision.TOO_LONG;
        return Decision.RETAINED;
    }

    private static FastqIO.Read span(FastqIO.Read read, int start, int finish) {
        return new FastqIO.Read(read.header(), read.sequence().substring(start, finish), read.quality().substring(start, finish));
    }

    private static void validate(FastqIO.Read read) {
        Objects.requireNonNull(read, "read");
        if (read.header() == null || !read.header().startsWith("@") || read.header().length() < 2
                || Character.isWhitespace(read.header().charAt(1)) || read.header().indexOf('\n') >= 0 || read.header().indexOf('\r') >= 0
                || read.sequence() == null || read.quality() == null || read.sequence().length() != read.quality().length()
                || read.sequence().length() > 10_000_000
                || read.sequence().chars().anyMatch(c -> "ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(c) < 0)
                || read.quality().chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("Require a valid unwrapped IUPAC FASTQ record with equal sequence/quality lengths");
    }
}
