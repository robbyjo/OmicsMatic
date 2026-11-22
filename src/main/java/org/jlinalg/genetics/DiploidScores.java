/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Streaming, supplied-weight scores for already oriented diploid allele dosages.
 *
 * <p>Each call supplies one variant across the same ordered samples. Dosages count
 * the allele to which the weight and reference frequency refer. No allele
 * harmonization, frequency estimation, weight training or population-risk
 * calibration is inferred. Missing values are represented only by {@code NaN}.
 * Accumulation uses compensated double arithmetic and rejects nonfinite
 * intermediate values. An accumulator is not thread safe.</p>
 */
public final class DiploidScores {
    private DiploidScores() { }

    /** Explicit handling of missing dosages. */
    public enum MissingPolicy {
        /** Reject a variant containing any missing dosage. */
        ERROR,
        /** Replace missing dosage with twice the supplied counted-allele frequency. */
        MEAN_IMPUTE,
        /** Omit each missing variant contribution and its two denominator alleles. */
        OMIT
    }

    /** Observed-data status, independent of whether a numerical score is finite. */
    public enum Status {
        /** All scored dosages were observed. */
        COMPLETE,
        /** At least one observed and one explicitly imputed dosage. */
        PARTIALLY_IMPUTED,
        /** Every dosage was explicitly imputed from supplied reference frequencies. */
        FULLY_IMPUTED,
        /** Some dosages were omitted; the average uses observed alleles only. */
        PARTIALLY_OBSERVED,
        /** Omission left no observed dosage; sum and average are unreportable NaNs. */
        NO_OBSERVED_DOSAGES
    }

    /** Explicit bounds on samples, accepted variants and sample-by-variant work. */
    public record Limits(int maximumSamples, long maximumVariants, long maximumCells) {
        /** Constructs positive, finite integer work limits. */
        public Limits {
            if (maximumSamples < 1 || maximumVariants < 1 || maximumCells < 1)
                throw new IllegalArgumentException("Score limits must be positive");
        }
        /** Defaults to one million samples, ten million variants and one billion cells. */
        public static Limits defaults() {
            return new Limits(1_000_000, 10_000_000, 1_000_000_000L);
        }
    }

    /**
     * One immutable result. Observed alleles and the average denominator are
     * separate: mean imputation includes imputed variants in the denominator.
     */
    public record SampleScore(double sum, double average, long inputVariants,
            long observedVariants, long imputedVariants, long observedAlleles,
            long denominatorAlleles, Status status) { }

    /** Creates a bounded streaming accumulator. */
    public static Accumulator accumulator(int samples, MissingPolicy policy) {
        return new Accumulator(samples, policy, Limits.defaults());
    }

    /** Creates a streaming accumulator with explicit work limits. */
    public static Accumulator accumulator(int samples, MissingPolicy policy, Limits limits) {
        return new Accumulator(samples, policy, limits);
    }

    /**
     * Counts the other diploid allele. A missing dosage stays missing; strand
     * complements, genome build and allele identities are not guessed.
     */
    public static double otherAlleleDosage(double dosage) {
        validateDosage(dosage);
        return Double.isNaN(dosage) ? Double.NaN : 2 - dosage;
    }

    private static void validateDosage(double value) {
        if (!Double.isNaN(value) && (!Double.isFinite(value) || value < 0 || value > 2))
            throw new IllegalArgumentException("Diploid dosage must be NaN or finite in [0,2]");
    }

    /** Mutable, variant-major accumulator using O(samples) memory. */
    public static final class Accumulator {
        private final MissingPolicy policy;
        private final Limits limits;
        private final double[] sums;
        private final double[] corrections;
        private final long[] observed;
        private long variants;

        private Accumulator(int samples, MissingPolicy policy, Limits limits) {
            this.policy = Objects.requireNonNull(policy, "Missing policy");
            this.limits = Objects.requireNonNull(limits, "Score limits");
            if (samples < 1 || samples > limits.maximumSamples())
                throw new IllegalArgumentException("Score sample count exceeds the positive declared limit");
            sums = new double[samples]; corrections = new double[samples]; observed = new long[samples];
        }

        /** Number of ordered samples. */
        public int sampleCount() { return sums.length; }
        /** Number of successfully accepted variants. */
        public long variantCount() { return variants; }
        /** Explicit missing-data policy. */
        public MissingPolicy missingPolicy() { return policy; }
        /** Declared resource bounds. */
        public Limits limits() { return limits; }

        /**
         * Adds one variant. The weight and frequency describe the counted allele.
         * Frequency is mandatory for mean imputation, even if this row happens to
         * be complete; other policies permit NaN to mean not supplied. A supplied
         * frequency must lie in [0,1]. Every input and prospective accumulator
         * value is validated before any state changes, so a rejected row leaves
         * the accumulator unchanged. The input array is neither retained nor modified.
         */
        public void add(double[] countedAlleleDosages, double weight, double referenceFrequency) {
            Objects.requireNonNull(countedAlleleDosages, "Variant dosages");
            if (countedAlleleDosages.length != sums.length)
                throw new IllegalArgumentException("Variant dosage count does not match ordered samples");
            if (!Double.isFinite(weight)) throw new IllegalArgumentException("Score weight must be finite");
            if (Double.isNaN(referenceFrequency)) {
                if (policy == MissingPolicy.MEAN_IMPUTE)
                    throw new IllegalArgumentException("Mean imputation requires an explicit counted-allele reference frequency");
            } else if (!Double.isFinite(referenceFrequency) || referenceFrequency < 0 || referenceFrequency > 1) {
                throw new IllegalArgumentException("Supplied reference frequency must be finite in [0,1]");
            }
            long next;
            try {
                next = Math.addExact(variants, 1);
                if (next > limits.maximumVariants() || Math.multiplyExact(next, sums.length) > limits.maximumCells())
                    throw new IllegalArgumentException("Score variant/cell work limit exceeded");
                Math.multiplyExact(next, 2);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Score variant/cell/allele counter overflow", overflow);
            }
            // First pass guarantees a late invalid sample or arithmetic overflow
            // cannot partially update earlier samples in this row.
            for (int sample = 0; sample < sums.length; sample++) {
                double dosage = countedAlleleDosages[sample]; validateDosage(dosage);
                if (Double.isNaN(dosage) && policy == MissingPolicy.ERROR)
                    throw new IllegalArgumentException("Missing dosage at sample ordinal " + (sample + 1));
                double contribution = contribution(dosage, weight, referenceFrequency);
                double total = sums[sample] + contribution;
                double correction = corrections[sample] + (Math.abs(sums[sample]) >= Math.abs(contribution)
                    ? (sums[sample] - total) + contribution : (contribution - total) + sums[sample]);
                if (!Double.isFinite(contribution) || !Double.isFinite(total)
                        || !Double.isFinite(correction) || !Double.isFinite(total + correction))
                    throw new IllegalArgumentException("Nonfinite score accumulation at sample ordinal " + (sample + 1));
            }
            for (int sample = 0; sample < sums.length; sample++) {
                double dosage = countedAlleleDosages[sample];
                double value = contribution(dosage, weight, referenceFrequency);
                double total = sums[sample] + value;
                corrections[sample] += Math.abs(sums[sample]) >= Math.abs(value)
                    ? (sums[sample] - total) + value : (value - total) + sums[sample];
                sums[sample] = total;
                if (!Double.isNaN(dosage)) observed[sample]++;
            }
            variants = next;
        }

        private double contribution(double dosage, double weight, double frequency) {
            if (Double.isNaN(dosage))
                return policy == MissingPolicy.MEAN_IMPUTE ? (2 * frequency) * weight : 0;
            return dosage * weight;
        }

        /** Returns an immutable snapshot for one zero-based sample ordinal. */
        public SampleScore score(int sample) {
            Objects.checkIndex(sample, sums.length);
            if (variants == 0) throw new IllegalStateException("No score variants were accepted");
            long called = observed[sample], imputed = policy == MissingPolicy.MEAN_IMPUTE ? variants - called : 0;
            long denominator = 2 * (policy == MissingPolicy.OMIT ? called : variants);
            Status status = called == variants ? Status.COMPLETE
                : policy == MissingPolicy.MEAN_IMPUTE ? (called == 0 ? Status.FULLY_IMPUTED : Status.PARTIALLY_IMPUTED)
                : called == 0 ? Status.NO_OBSERVED_DOSAGES : Status.PARTIALLY_OBSERVED;
            double sum = denominator == 0 ? Double.NaN : sums[sample] + corrections[sample];
            return new SampleScore(sum, denominator == 0 ? Double.NaN : sum / denominator,
                variants, called, imputed, 2 * called, denominator, status);
        }

        /** Returns immutable snapshots in the original ordered-sample sequence. */
        public List<SampleScore> scores() {
            if (variants == 0) throw new IllegalStateException("No score variants were accepted");
            var result = new ArrayList<SampleScore>(sums.length);
            for (int sample = 0; sample < sums.length; sample++) result.add(score(sample));
            return List.copyOf(result);
        }
    }
}
