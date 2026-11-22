/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Native genomic sequence and GC/CpG population operations for HOMER audits.
 * Coordinates are one-based inclusive. Resizing follows the original Perl
 * midpoint rule, including its size-plus-one inclusive width. Extraction
 * requires an interval wholly within the supplied exact contig; no assembly,
 * contig alias, boundary padding or clipping is inferred.
 */
public final class HomerGenomicSequences {
    private HomerGenomicSequences() { }
    private static final double[] GC = {.2,.25,.3,.35,.4,.45,.5,.6,.7,.8};
    private static final double[] CPG = {.01,.02,.03,.04,.05,.06,.08,.10,.12,.14,.16};

    /** Exact one-based inclusive interval on a stated strand. */
    public record Interval(long start1, long end1, char strand) {
        /** Require positive ordered coordinates and an explicit plus/minus strand. */
        public Interval {
            if (start1 < 1 || end1 < start1 || end1 > 1_000_000_000L || strand != '+' && strand != '-')
                throw new IllegalArgumentException("Require positive bounded inclusive coordinates and explicit strand");
        }
        /** Number of bases, including both endpoints. */
        public long length() { return end1 - start1 + 1; }
    }

    /**
     * Resize around the original half-base midpoint, with a strand-relative
     * displacement. Size N produces N+1 bases. Reject an out-of-bound result
     * before extraction; the original left-overhang behavior is not repaired.
     * @param interval original interval
     * @param size original numeric size argument, positive
     * @param move strand-relative midpoint displacement
     * @return original floor-rounded inclusive interval
     */
    public static Interval resize(Interval interval, int size, int move) {
        if (interval == null || size < 1 || size > 100_000_000)
            throw new IllegalArgumentException("Require interval and positive bounded original size");
        long twiceCenter = interval.start1 + interval.end1 + (interval.strand == '+' ? 2L * move : -2L * move);
        return new Interval(Math.floorDiv(twiceCenter - size, 2), Math.floorDiv(twiceCenter + size, 2), interval.strand);
    }

    /**
     * Extract and normalize A/C/G/T/N from indexed bases without loading a
     * whole chromosome. Lowercase A/C/G/T are promoted unless maskLower is
     * explicit; all other letters become N. Minus intervals are reverse
     * complemented. Digits/control characters fail to avoid conservation-file
     * interpretation by the original extractor.
     * @param bases exact contig, retaining source case
     * @param interval wholly contained one-based inclusive interval
     * @param maskLower whether lowercase source bases become N
     * @param maximumLength explicit output allocation bound
     * @return normalized, strand-oriented DNA
     * @throws IOException on invalid reference characters, bounds or I/O
     */
    public static String extract(SeqtkMethods.Bases bases, Interval interval, boolean maskLower, int maximumLength) throws IOException {
        if (bases == null || interval == null || maximumLength < 1 || interval.end1 > bases.length()
                || interval.length() > maximumLength || interval.length() > Integer.MAX_VALUE)
            throw new IOException("Genomic extraction exceeds supplied exact contig or explicit sequence capacity; review coordinates/resize");
        char[] result = new char[(int) interval.length()];
        for (int i = 0; i < result.length; i++) {
            char raw = bases.base(interval.start1 - 1 + i);
            if (raw < 'A' || raw > 'Z' && raw < 'a' || raw > 'z')
                throw new IOException("Non-letter genomic reference base; no conservation-file or whitespace interpretation is admitted");
            char base = maskLower && raw >= 'a' ? 'N' : Character.toUpperCase(raw);
            if (base != 'A' && base != 'C' && base != 'G' && base != 'T') base = 'N';
            if (interval.strand == '-') result[result.length - 1 - i] = complement(base);
            else result[i] = base;
        }
        return new String(result);
    }

    private static char complement(char base) {
        return switch (base) {case 'A' -> 'T'; case 'C' -> 'G'; case 'G' -> 'C'; case 'T' -> 'A'; default -> 'N';};
    }

    /** Original poor-sequence disposition, retaining the reason for every row. */
    public enum Disposition { RETAINED, SHORTER_THAN_FIVE, LONGER_THAN_100000, EXCESS_N }

    /**
     * Classify already normalized DNA using the original strict greater-than
     * N-fraction test and original arithmetic 1-(canonical/length).
     * @param sequence normalized uppercase A/C/G/T/N
     * @param maximumN explicit allowed N fraction in [0,1]
     * @return reason for retaining or excluding the sequence
     */
    public static Disposition disposition(String sequence, double maximumN) {
        requireDna(sequence);
        if (!Double.isFinite(maximumN) || maximumN < 0 || maximumN > 1)
            throw new IllegalArgumentException("Require finite N fraction in [0,1]");
        if (sequence.length() < 5) return Disposition.SHORTER_THAN_FIVE;
        if (sequence.length() > 100000) return Disposition.LONGER_THAN_100000;
        long canonical = sequence.chars().filter(c -> c != 'N').count();
        return 1.0 - canonical / (double) sequence.length() > maximumN ? Disposition.EXCESS_N : Disposition.RETAINED;
    }

    /** Counts and original six-decimal frequencies; reported length excludes N. */
    public record Frequency(long canonicalBases, long canonicalAdjacentPairs, long cgPairs,
                            double cpg, double gc, double ag, double ac, int gcBin, int cpgBin) { }

    /**
     * Compute original homerTools freq denominators, then the six-decimal text
     * values consumed by freq2group.pl. Only adjacent canonical pairs enter the
     * CpG denominator. Bins use strict less-than boundaries; equality belongs
     * to the next bin. An all-N record has no original frequency row.
     * @param sequence normalized uppercase A/C/G/T/N
     * @return frequencies and bin identities
     */
    public static Frequency frequency(String sequence) {
        requireDna(sequence);
        long n = 0, pairs = 0, cg = 0, gc = 0, ag = 0, ac = 0;
        for (int i = 0; i < sequence.length(); i++) {
            char b = sequence.charAt(i);
            if (b == 'N') continue;
            n++;
            if (b == 'C' || b == 'G') gc++;
            if (b == 'A' || b == 'G') ag++;
            if (b == 'A' || b == 'C') ac++;
            if (i + 1 < sequence.length() && sequence.charAt(i + 1) != 'N') {
                pairs++;
                if (b == 'C' && sequence.charAt(i + 1) == 'G') cg++;
            }
        }
        if (n == 0) throw new IllegalArgumentException("All-N/empty sequences have no original frequency row");
        double cpgValue = rounded(cg / Math.max(.01, pairs), 6), gcValue = rounded(gc / (double) n, 6);
        return new Frequency(n, pairs, cg, cpgValue, gcValue, rounded(ag / (double) n, 6),
                rounded(ac / (double) n, 6), bin(gcValue, GC), bin(cpgValue, CPG));
    }

    private static int bin(double value, double[] limits) {
        int i = 0; while (i < limits.length && value >= limits[i]) i++; return i;
    }
    private static double rounded(double value, int digits) {
        return new java.math.BigDecimal(value).setScale(digits, java.math.RoundingMode.HALF_EVEN).doubleValue();
    }
    private static void requireDna(String sequence) {
        if (sequence == null || sequence.chars().anyMatch(c -> c != 'A' && c != 'C' && c != 'G' && c != 'T' && c != 'N'))
            throw new IllegalArgumentException("Require explicitly normalized uppercase A/C/G/T/N");
    }

    /** A unique staged sequence in an explicit target/background frequency bin. */
    public record Member(String alias, boolean target, int bin, double initialWeight) {
        /** Require a unique nonempty alias, valid frequency bin and positive weight. */
        public Member {
            if (alias == null || alias.isEmpty() || alias.chars().anyMatch(Character::isWhitespace)
                    || bin < 0 || bin > 11 || !Double.isFinite(initialWeight) || initialWeight <= 0)
                throw new IllegalArgumentException("Require nonempty alias, valid bin and finite positive weight");
        }
    }
    /** Original five-decimal weight, or zero with an explicit unsupported-bin disposition. */
    public record Weighted(Member member, double weight, boolean retained) { }

    /**
     * Match assignGeneWeights.pl: discard bins without at least unit target
     * and background weight, recompute supported totals, and allocate background
     * weight to match the supported target distribution. Each input row remains
     * in the returned ledger; unsupported bins receive retained=false.
     * @param members unique aliases and chosen GC or CpG bins
     * @return complete population ledger in input order
     */
    public static List<Weighted> weightBins(List<Member> members) {
        if (members == null || members.isEmpty()) throw new IllegalArgumentException("Require nonempty population");
        Map<Integer,double[]> bins = new TreeMap<>(); var aliases = new HashSet<String>();
        for (Member m : members) {
            if (m == null || !aliases.add(m.alias)) throw new IllegalArgumentException("Staged aliases must be unique");
            double[] sums = bins.computeIfAbsent(m.bin, k -> new double[2]);
            sums[m.target ? 0 : 1] += m.initialWeight;
            if (!Double.isFinite(sums[0] + sums[1])) throw new IllegalArgumentException("Population weight overflow");
        }
        double targets = 0, background = 0;
        for (double[] sums : bins.values()) if (sums[0] >= 1 && sums[1] >= 1) {targets += sums[0]; background += sums[1];}
        if (!Double.isFinite(targets + background)) throw new IllegalArgumentException("Population weight overflow");
        var result = new ArrayList<Weighted>(members.size());
        for (Member m : members) {
            double[] sums = bins.get(m.bin); boolean retain = sums[0] >= 1 && sums[1] >= 1;
            double weight = retain ? rounded(m.target ? m.initialWeight : sums[0] / targets * background / sums[1] * m.initialWeight, 5) : 0;
            result.add(new Weighted(m, weight, retain));
        }
        return List.copyOf(result);
    }
}
