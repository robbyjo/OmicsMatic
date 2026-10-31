/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

/**
 * Fixed-threshold, unweighted sequence-presence statistics for the original
 * HOMER known-motif core. Background generation, sequence normalization,
 * threshold optimization and discovery are separate methods.
 */
public final class HomerEnrichment {
    private HomerEnrichment() { }

    /** Explicit one-sided enrichment statistic. */
    public enum Statistic { HYPERGEOMETRIC, BINOMIAL }

    /** Number of sequences and sequences with at least one motif hit in each role. */
    public record Counts(int targets, int targetWithMotif, int background, int backgroundWithMotif) {
        /**
         * Require complete valid sequence counts. At least two background
         * sequences are needed for HOMER's binomial floor/ceiling convention.
         */
        public Counts {
            if (targets < 1 || background < 2 || (long) targets + background > Integer.MAX_VALUE
                    || targetWithMotif < 0 || targetWithMotif > targets
                    || backgroundWithMotif < 0 || backgroundWithMotif > background) {
                throw new IllegalArgumentException("Require targets>=1, background>=2 and valid sequence-presence counts within the integer population bound");
            }
        }
    }

    /**
     * Full native log upper-tail, original background-rate convention and
     * one-sequence pseudocount log2 enrichment. No multiple-testing adjustment.
     * The original coarse displayed exponent truncates logP/2.3026 toward zero.
     */
    public record Result(Counts counts, Statistic statistic, double logP,
            double observedBackgroundRate, double binomialBackgroundRate,
            double log2Enrichment, int originalDisplayExponent) { }

    /**
     * Compute an unweighted zero-or-one-occurrence-per-sequence statistic.
     * Hypergeometric populations combine the supplied target and background
     * roles without assuming they are disjoint biological samples. Binomial
     * background rates are clamped to [1/B,(B-1)/B], matching the original core.
     * The caller must review any role overlap; this function never deduplicates.
     *
     * @param counts complete sequence-presence counts
     * @param statistic explicitly selected upper-tail test
     * @return finite native log probability and original display conventions
     */
    public static Result test(Counts counts, Statistic statistic) {
        if (counts == null || statistic == null) throw new IllegalArgumentException("Counts and statistic are required");
        double observed = (double) counts.backgroundWithMotif / counts.background;
        double rate = Math.max(1.0 / counts.background, Math.min((counts.background - 1.0) / counts.background, observed));
        double logP;
        if (statistic == Statistic.HYPERGEOMETRIC) {
            int present = counts.targetWithMotif + counts.backgroundWithMotif;
            logP = jdistlib.HyperGeometric.cumulative(counts.targetWithMotif - 1.0,
                    counts.targets, counts.background, present, false, true);
        } else {
            logP = jdistlib.Binomial.cumulative(counts.targetWithMotif - 1.0, counts.targets, rate, false, true);
        }
        if (!Double.isFinite(logP) || logP > 1e-12) throw new ArithmeticException("Invalid finite enrichment log-tail");
        logP = Math.min(0, logP);
        double ratio = (Math.max(1, counts.targetWithMotif) / (double) counts.targets)
                / (Math.max(1, counts.backgroundWithMotif) / (double) counts.background);
        double log2 = StrictMath.log(ratio) / StrictMath.log(2);
        double exponent = logP / 2.3026;
        if (exponent < Integer.MIN_VALUE) throw new ArithmeticException("Original coarse exponent exceeds signed integer capacity");
        return new Result(counts, statistic, logP, observed, rate, log2, (int) exponent);
    }
}
