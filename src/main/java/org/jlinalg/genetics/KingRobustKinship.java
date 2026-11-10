/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Native diploid hard-call counts and the PLINK2 between-family KING-robust estimate.
 *
 * <p>The estimator is {@code 0.5 - (4 * IBS0 + HET1_HOM2 + HET2_HOM1)
 * / (4 * min(HET1, HET2))}, using only variants called in both participants.
 * It follows the minimum-heterozygosity between-family calculation documented
 * in PLINK2's original {@code plink2_matrix_calc.cc}. It is distinct from a
 * frequency-based GRM, PLINK IBD PI_HAT, within-family KING, segment inference
 * and an unrelated-sample selection algorithm. Pedigree labels do not change
 * this explicitly selected estimator.</p>
 *
 * <p>Callers must supply aligned biallelic diploid autosomal hard calls after
 * choosing their variant/sample population. This class performs no chromosome
 * selection, LD pruning, imputation, allele-frequency estimation, genotype QC
 * or sample removal. Negative finite estimates are preserved. Zero information
 * produces the original arithmetic's NaN or negative infinity with an explicit
 * disposition; these values are not valid relationship estimates.</p>
 *
 * <p>The streaming accumulator transposes up to64 variants into per-sample bit
 * masks and counts caller-specified pairs with {@link Long#bitCount(long)}.
 * Inputs and identifiers remain caller-owned. Instances are not thread-safe.</p>
 */
public final class KingRobustKinship {
    private KingRobustKinship() { }

    /** Original missing hard-call code for the byte-vector API. */
    public static final byte MISSING = -1;

    /** Ordered participant indices; order determines the two asymmetric count columns. */
    public record Pair(int first, int second) {
        /** Require distinct nonnegative indices; the accumulator checks their upper bounds. */
        public Pair {
            if (first < 0 || second < 0 || first == second)
                throw new IllegalArgumentException("Kinship pairs require distinct nonnegative participant indices");
        }
    }

    /** Disjoint jointly called genotype categories, with no missing-call imputation. */
    public record Counts(long jointlyCalled, long bothHeterozygous, long oppositeHomozygotes,
            long firstHetSecondHom, long firstHomSecondHet) {
        /** Validate the source's32-bit variant population and disjoint count arithmetic. */
        public Counts {
            for (long value : new long[] {jointlyCalled, bothHeterozygous,
                    oppositeHomozygotes, firstHetSecondHom, firstHomSecondHet}) {
                if (value < 0 || value > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Kinship counts must be in0..2147483647");
            }
            if (bothHeterozygous + oppositeHomozygotes + firstHetSecondHom + firstHomSecondHet > jointlyCalled)
                throw new IllegalArgumentException("Disjoint genotype counts exceed the jointly called population");
        }

        /** Heterozygotes in the first participant, restricted to joint calls. */
        public long firstHeterozygotes() { return bothHeterozygous + firstHetSecondHom; }
        /** Heterozygotes in the second participant, restricted to joint calls. */
        public long secondHeterozygotes() { return bothHeterozygous + firstHomSecondHet; }
        /** The original between-family denominator's heterozygote count. */
        public long smallerHeterozygoteCount() {
            return bothHeterozygous + Math.min(firstHetSecondHom, firstHomSecondHet);
        }
        /** Joint calls sharing exactly one allele. */
        public long ibs1() { return firstHetSecondHom + firstHomSecondHet; }
        /** Joint calls sharing both alleles, including both-heterozygote calls. */
        public long ibs2() { return jointlyCalled - oppositeHomozygotes - ibs1(); }
        /** Joint calls where both participants have the same homozygous genotype. */
        public long sameHomozygotes() { return ibs2() - bothHeterozygous; }
    }

    /** A finite estimate, no joint calls, or a zero smaller-heterozygosity denominator. */
    public enum Disposition {
        /** Finite arithmetic result; this does not certify biological relationship inference. */
        FINITE,
        /** No variants were called in both participants. */
        NO_JOINT_CALLS,
        /** The smaller jointly called heterozygote count is zero; retain raw NaN/-Infinity. */
        ZERO_SMALLER_HETEROZYGOSITY
    }

    /** Immutable estimator result with the complete count population and its disposition. */
    public static final class Estimate {
        private final Counts counts;
        private final double coefficient;
        private final Disposition disposition;

        private Estimate(Counts counts, double coefficient, Disposition disposition) {
            this.counts = counts;
            this.coefficient = coefficient;
            this.disposition = disposition;
        }
        /** Exact jointly called genotype counts used by the estimate. */
        public Counts counts() { return counts; }
        /** Raw source-formula result, including negative, NaN and negative-infinite values. */
        public double coefficient() { return coefficient; }
        /** Explicit information/finite-result disposition. */
        public Disposition disposition() { return disposition; }
    }

    /**
     * Evaluate the explicit between-family source formula without clipping or denominator repair.
     *
     * @param counts complete disjoint jointly called genotype counts
     * @return raw coefficient and information disposition
     */
    public static Estimate betweenFamily(Counts counts) {
        Objects.requireNonNull(counts, "Joint genotype counts");
        long smaller = counts.smallerHeterozygoteCount();
        long numerator = 4 * counts.oppositeHomozygotes()
            + counts.firstHetSecondHom() + counts.firstHomSecondHet();
        double coefficient = 0.5 - ((double) numerator / (double) (4 * smaller));
        Disposition disposition = counts.jointlyCalled() == 0 ? Disposition.NO_JOINT_CALLS
            : smaller == 0 ? Disposition.ZERO_SMALLER_HETEROZYGOSITY : Disposition.FINITE;
        return new Estimate(counts, coefficient, disposition);
    }

    /**
     * Streaming bit-mask counter for an explicit ordered set of unique participant pairs.
     * Memory/work capacities are logical estimates, not enforced host RAM quotas.
     */
    public static final class Accumulator {
        private final int samples;
        private final int maximumVariants;
        private final long estimatedWorkingBytes;
        private final int[] first;
        private final int[] second;
        private final long[] joint;
        private final long[] bothHet;
        private final long[] opposite;
        private final long[] hetHom;
        private final long[] homHet;
        private final long[] heterozygotes;
        private final long[] homozygote0;
        private final long[] homozygote2;
        private int variants;
        private int buffered;
        private boolean finished;

        /**
         * Create a counter without retaining the caller's pair list or genotype vectors.
         * Duplicate unordered pairs are rejected rather than silently deduplicated.
         *
         * @param samples declared number of participants, at least2
         * @param pairs explicit ordered pairs with indices below {@code samples}
         * @param maximumVariants positive maximum number of supplied variant rows
         * @param maximumWorkingBytes permitted estimated working storage
         */
        public Accumulator(int samples, List<Pair> pairs, int maximumVariants, long maximumWorkingBytes) {
            Objects.requireNonNull(pairs, "Participant pairs");
            if (samples < 2 || pairs.isEmpty() || maximumVariants < 1 || maximumWorkingBytes < 1)
                throw new IllegalArgumentException("Declare at least2 participants, at least1 pair and positive capacities");
            long estimated = Math.addExact(4096L,
                Math.addExact(Math.multiplyExact(32L, samples), Math.multiplyExact(160L, pairs.size())));
            if (estimated > maximumWorkingBytes)
                throw new IllegalArgumentException("Estimated native kinship working-storage capacity exceeded");
            this.samples = samples;
            this.maximumVariants = maximumVariants;
            this.estimatedWorkingBytes = estimated;
            first = new int[pairs.size()]; second = new int[pairs.size()];
            var unique = new HashSet<Long>();
            int i = 0;
            for (Pair pair : pairs) {
                Objects.requireNonNull(pair, "Participant pair");
                if (pair.first() >= samples || pair.second() >= samples)
                    throw new IllegalArgumentException("Participant pair exceeds the declared sample population");
                long key = ((long) Math.min(pair.first(), pair.second()) << 32)
                    | (Math.max(pair.first(), pair.second()) & 0xffffffffL);
                if (!unique.add(key)) throw new IllegalArgumentException("Duplicate unordered participant pair");
                first[i] = pair.first(); second[i] = pair.second(); i++;
            }
            joint = new long[first.length]; bothHet = new long[first.length]; opposite = new long[first.length];
            hetHom = new long[first.length]; homHet = new long[first.length];
            heterozygotes = new long[samples]; homozygote0 = new long[samples]; homozygote2 = new long[samples];
        }

        /** Number of participant pairs in the declared order. */
        public int pairCount() { return first.length; }
        /** Number of supplied variant rows, including rows containing missing calls. */
        public int variantCount() { return variants; }
        /** Estimated storage bound checked before allocation, including constructor bookkeeping. */
        public long estimatedWorkingBytes() { return estimatedWorkingBytes; }
        /** The declared ordered pair at an index; returns an immutable value. */
        public Pair pair(int index) { return new Pair(first[index], second[index]); }

        /**
         * Add one aligned variant with0/1/2 allele-copy hard calls and-1 missing.
         * An invalid vector is rejected before changing any counter or buffered mask.
         *
         * @param calls one hard call per declared participant
         */
        public void accept(byte[] calls) {
            Objects.requireNonNull(calls, "Aligned hard calls");
            admission();
            if (calls.length != samples) throw new IllegalArgumentException("Hard-call vector does not match the sample population");
            for (byte call : calls) if (call < MISSING || call > 2)
                throw new IllegalArgumentException("Require0/1/2 hard calls or the explicit-1 missing code");
            long bit = 1L << buffered;
            for (int i = 0; i < samples; i++) {
                switch (calls[i]) {
                    case 0 -> homozygote0[i] |= bit;
                    case 1 -> heterozygotes[i] |= bit;
                    case 2 -> homozygote2[i] |= bit;
                    default -> { /* Explicit missing call contributes to no category. */ }
                }
            }
            added();
        }

        /**
         * Add one variant-major PLINK BED row:00/10/11 are hard calls,01 is missing.
         * Only the declared participants are read; unused high padding bits are outside
         * that population and are ignored without changing the supplied byte array.
         * No BIM/FAM/chromosome/file admission is implied by this low-level method.
         *
         * @param packed original two-bit row with exactlyceil(samples/4) bytes
         */
        public void acceptPackedBed(byte[] packed) {
            Objects.requireNonNull(packed, "Packed BED row");
            admission();
            if (packed.length != (samples + 3L) / 4)
                throw new IllegalArgumentException("Packed BED row does not match the sample population");
            long bit = 1L << buffered;
            for (int i = 0; i < samples; i++) {
                int code = ((packed[i / 4] & 0xff) >>> (2 * (i % 4))) & 3;
                switch (code) {
                    case 0 -> homozygote2[i] |= bit;
                    case 2 -> heterozygotes[i] |= bit;
                    case 3 -> homozygote0[i] |= bit;
                    default -> { /* BED01 is missing. */ }
                }
            }
            added();
        }

        private void admission() {
            if (finished) throw new IllegalStateException("Native kinship counter is already finished");
            if (variants == maximumVariants)
                throw new IllegalArgumentException("Native kinship variant-row capacity exceeded");
        }

        private void added() {
            variants++; buffered++;
            if (buffered == 64) flush();
        }

        private void flush() {
            if (buffered == 0) return;
            for (int i = 0; i < first.length; i++) {
                int a = first[i], b = second[i];
                long ha = heterozygotes[a], hb = heterozygotes[b];
                long aa = homozygote0[a], ab = homozygote0[b];
                long ba = homozygote2[a], bb = homozygote2[b];
                joint[i] += Long.bitCount((ha | aa | ba) & (hb | ab | bb));
                bothHet[i] += Long.bitCount(ha & hb);
                opposite[i] += Long.bitCount((aa & bb) | (ba & ab));
                hetHom[i] += Long.bitCount(ha & (ab | bb));
                homHet[i] += Long.bitCount((aa | ba) & hb);
            }
            Arrays.fill(heterozygotes, 0); Arrays.fill(homozygote0, 0); Arrays.fill(homozygote2, 0);
            buffered = 0;
        }

        /** Flush the last partial block and seal the counter; repeated calls are harmless. */
        public void finish() {
            if (!finished) { flush(); finished = true; }
        }

        /**
         * Read a complete estimate after {@link #finish()}, without allocating a result matrix.
         *
         * @param index declared participant-pair index
         * @return exact counts, raw source-formula coefficient and disposition
         */
        public Estimate estimate(int index) {
            if (!finished) throw new IllegalStateException("Finish the native kinship counter before reading complete estimates");
            return betweenFamily(new Counts(joint[index], bothHet[index], opposite[index], hetHom[index], homHet[index]));
        }
    }
}
