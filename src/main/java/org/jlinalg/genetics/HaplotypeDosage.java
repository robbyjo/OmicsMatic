/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Moments of supplied haploid ALT dosages for independent haploid or diploid copies.
 *
 * <p>This class does not phase or impute genotypes. Each input is an explicit
 * posterior probability of the ALT allele on one already identified haplotype.
 * The diploid probability calculation assumes independence of those two copies.
 * No missing-value imputation, normalization, allele flip or ploidy inference is
 * performed. In particular, genotype probabilities do not identify haplotype
 * phase and cannot be used to reconstruct these inputs.</p>
 */
public final class HaplotypeDosage {
    private HaplotypeDosage() { }

    /**
     * Supplied-copy posterior moments and calls, in the original haplotype order.
     *
     * @param genotypeProbabilities probabilities of 0 through ploidy ALT copies
     * @param dosage expected number of ALT copies
     * @param variance posterior variance of the number of ALT copies
     * @param phasedCalls separate copy calls, with probability 0.5 assigned ALT
     */
    public record Posterior(List<Double> genotypeProbabilities, double dosage,
                            double variance, List<Integer> phasedCalls) {
        public Posterior {
            genotypeProbabilities = List.copyOf(genotypeProbabilities);
            phasedCalls = List.copyOf(phasedCalls);
        }
        /** Number of supplied haplotype copies. */
        public int ploidy() { return phasedCalls.size(); }
    }

    /**
     * Ploidy-weighted cohort descriptors of supplied haplotype probabilities.
     *
     * <p>The R-squared descriptor is population variance of haploid ALT dosages
     * divided by AF(1-AF). It is not empirical correlation with true genotypes,
     * and it is not variance of diploid sample dosages. It is undefined at AF 0
     * or 1. The mathematically bounded descriptor is clamped to [0,1] only for
     * floating-point roundoff. Stable double-precision accumulation can differ
     * from an upstream tool's binary32 sum/squared-sum implementation.</p>
     *
     * @param samples number of input sample rows
     * @param haplotypes total explicit copies, including mixed haploid/diploid rows
     * @param expectedAltCopies expected ALT count across all supplied copies
     * @param alleleFrequency ploidy-weighted ALT frequency
     * @param minorAlleleFrequency minimum of AF and 1-AF
     * @param meanPosteriorVariance mean sample posterior variance, without ploidy reweighting
     * @param meanHaplotypeCallScore mean max(p,1-p) across all copies
     * @param estimatedHaplotypeR2 haploid variance ratio, empty at AF 0 or 1
     */
    public record Summary(int samples, long haplotypes, double expectedAltCopies,
                          double alleleFrequency, double minorAlleleFrequency,
                          double meanPosteriorVariance, double meanHaplotypeCallScore,
                          OptionalDouble estimatedHaplotypeR2) {
        /**
         * The conventional Minimac R2 value of zero for a monomorphic output.
         * Use {@link #estimatedHaplotypeR2()} to retain mathematical undefinedness.
         */
        public double minimacR2Convention() { return estimatedHaplotypeR2.orElse(0); }
    }

    /**
     * Calculate exact double-precision Bernoulli-copy posterior moments.
     *
     * <p>Each copy is called ALT at p &gt;= 0.5, matching current Minimac's
     * per-copy calling convention. These calls need not maximize the unphased
     * genotype probability. Both probabilities and phased calls are retained.</p>
     *
     * @param altProbabilities one or two finite probabilities in [0,1]
     * @return immutable moments and supplied-order copy calls
     * @throws IllegalArgumentException for absent, missing, out-of-range or unsupported ploidy inputs
     */
    public static Posterior posterior(double... altProbabilities) {
        validate(altProbabilities);
        double first = altProbabilities[0];
        int firstCall = first >= .5 ? 1 : 0;
        if (altProbabilities.length == 1) {
            return new Posterior(List.of(1-first, first), first,
                    first*(1-first), List.of(firstCall));
        }
        double second = altProbabilities[1];
        return new Posterior(List.of((1-first)*(1-second),
                first*(1-second)+second*(1-first), first*second), first+second,
                first*(1-first)+second*(1-second),
                List.of(firstCall, second >= .5 ? 1 : 0));
    }

    /**
     * Summarize already supplied copy probabilities with explicit row ploidies.
     *
     * @param sampleAltProbabilities nonempty sample rows, each containing one or two probabilities
     * @return stable cohort descriptors; no scientific quality threshold is applied
     * @throws IllegalArgumentException for missing rows or invalid copy probabilities
     */
    public static Summary summarize(double[][] sampleAltProbabilities) {
        if (sampleAltProbabilities == null || sampleAltProbabilities.length == 0) {
            throw new IllegalArgumentException("A nonempty cohort with explicit sample ploidies is required");
        }
        long n = 0;
        double mean = 0, m2 = 0;
        var alt = new Sum();
        var variance = new Sum();
        var callScore = new Sum();
        for (double[] sample : sampleAltProbabilities) {
            validate(sample);
            for (double probability : sample) {
                n++;
                double difference = probability-mean;
                mean += difference/n;
                m2 += difference*(probability-mean);
                alt.add(probability);
                variance.add(probability*(1-probability));
                callScore.add(Math.max(probability, 1-probability));
            }
        }
        double af = alt.value/n;
        OptionalDouble r2 = af > 0 && af < 1
                ? OptionalDouble.of(Math.max(0, Math.min(1, (m2/n)/(af*(1-af)))))
                : OptionalDouble.empty();
        return new Summary(sampleAltProbabilities.length, n, alt.value, af,
                Math.min(af, 1-af), variance.value/sampleAltProbabilities.length,
                callScore.value/n, r2);
    }

    private static void validate(double[] probabilities) {
        if (probabilities == null || probabilities.length < 1 || probabilities.length > 2) {
            throw new IllegalArgumentException("Declare one or two haplotype ALT probabilities per sample");
        }
        for (double probability : probabilities) {
            if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
                throw new IllegalArgumentException("Haplotype ALT probabilities must be finite in [0,1]; handle missing data explicitly");
            }
        }
    }

    private static final class Sum {
        double value, correction;
        void add(double next) {
            double adjusted = next-correction;
            double total = value+adjusted;
            correction = (total-value)-adjusted;
            value = total;
        }
    }
}
