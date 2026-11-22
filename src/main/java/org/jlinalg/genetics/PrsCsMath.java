/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Objects;
import jdistlib.Normal;

/**
 * Native arithmetic and explicit allele/sampling contracts for original PRS-CS.
 *
 * <p>This helper does not fit a continuous-shrinkage model or infer ancestry,
 * genotype frequencies, study sample size or phenotype scale. Its allele rules
 * reproduce the original parser's branch order, including its strand-ambiguous
 * behavior. Callers must explicitly decide whether to admit palindromic SNPs and
 * strand complements. Posterior inference remains a separate upstream operation.</p>
 */
public final class PrsCsMath {
    private PrsCsMath() { }

    /** Original PRS-CS's minimum binary64 two-sided P value. */
    public static final double UPSTREAM_MINIMUM_P = 1e-323;
    private static final long MAXIMUM_EXACT_SAMPLE_SIZE = 9_007_199_254_740_991L;

    /** Explicit handling of P values below the original parser's floor. */
    public enum PValuePolicy {
        /** Reject rather than silently changing a supplied P value. */
        ERROR,
        /** Use the original floor; callers must retain an actionable warning. */
        ORIGINAL_FLOOR_WITH_WARNING
    }

    /** Ordered, distinct, uppercase single-base DNA alleles. */
    public record Alleles(char first, char second) {
        public Alleles {
            if (!dna(first) || !dna(second) || first == second)
                throw new IllegalArgumentException("Require distinct uppercase A/C/G/T alleles");
        }

        /** Whether the unordered pair is A/T or C/G. */
        public boolean palindromic() { return complement(first) == second; }
        private Alleles reverse() { return new Alleles(second, first); }
        private Alleles complemented() { return new Alleles(complement(first), complement(second)); }
    }

    /**
     * Original-parser orientation relative to the target BIM's first allele.
     * Signs are zero for an unmatched triple, otherwise +1 or -1. Complement
     * flags describe the selected original branch; they do not resolve strand
     * ambiguity. The reference sign also controls reference-frequency inversion
     * and the signs applied to the LD matrix.
     */
    public record Alignment(boolean matched, int summarySign, int referenceSign,
            boolean summaryComplement, boolean referenceComplement, boolean palindromic) {
        public Alignment {
            if (matched ? Math.abs(summarySign) != 1 || Math.abs(referenceSign) != 1
                    : summarySign != 0 || referenceSign != 0 || summaryComplement || referenceComplement)
                throw new IllegalArgumentException("Inconsistent PRS-CS alignment result");
        }
    }

    /**
     * Match one reference/target/summary allele triple using original branch order.
     * In particular, the summary parser considers a strand complement before
     * reversal, whereas the reference parser considers reversal first. A/T and
     * C/G variants can therefore differ from a naive sign-flipping rule.
     */
    public static Alignment align(Alleles reference, Alleles target, Alleles summary) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(summary, "summary");
        int referenceSign;
        boolean referenceComplement;
        if (target.equals(reference)) {
            referenceSign = 1; referenceComplement = false;
        } else if (target.equals(reference.reverse())) {
            referenceSign = -1; referenceComplement = false;
        } else if (target.equals(reference.complemented())) {
            referenceSign = 1; referenceComplement = true;
        } else if (target.equals(reference.complemented().reverse())) {
            referenceSign = -1; referenceComplement = true;
        } else return new Alignment(false, 0, 0, false, false, target.palindromic());

        int summarySign;
        boolean summaryComplement;
        if (target.equals(summary)) {
            summarySign = 1; summaryComplement = false;
        } else if (target.equals(summary.complemented())) {
            summarySign = 1; summaryComplement = true;
        } else if (target.equals(summary.reverse())) {
            summarySign = -1; summaryComplement = false;
        } else if (target.equals(summary.complemented().reverse())) {
            summarySign = -1; summaryComplement = true;
        } else return new Alignment(false, 0, 0, false, false, target.palindromic());
        return new Alignment(true, summarySign, referenceSign,
                summaryComplement, referenceComplement, target.palindromic());
    }

    /** Standardized marginal effect from an explicitly supplied effect and SE. */
    public static double fromBetaSe(double beta, double standardError, long sampleSize, int sign) {
        finite(beta, "effect");
        if (!Double.isFinite(standardError) || standardError <= 0)
            throw new IllegalArgumentException("Require a finite positive effect standard error");
        orientation(sign);
        return finite(sign * beta / standardError / sampleRoot(sampleSize), "standardized marginal effect");
    }

    /** Standardized marginal effect from OR and the SE of log(OR), never SE(OR). */
    public static double fromOddsRatioSe(double oddsRatio, double logOddsStandardError,
            long sampleSize, int sign) {
        return fromBetaSe(logOdds(oddsRatio), logOddsStandardError, sampleSize, sign);
    }

    /**
     * Standardized marginal effect from effect direction and a two-sided P value.
     * The magnitude of beta is unused, as in original PRS-CS. No phenotype or
     * liability-scale conversion is inferred.
     */
    public static double fromBetaP(double beta, double p, long sampleSize, int sign, PValuePolicy policy) {
        finite(beta, "effect");
        orientation(sign);
        Objects.requireNonNull(policy, "P-value policy");
        if (!Double.isFinite(p) || p < 0 || p > 1)
            throw new IllegalArgumentException("Require a finite two-sided P value in [0,1]");
        if (p < UPSTREAM_MINIMUM_P) {
            if (policy == PValuePolicy.ERROR)
                throw new IllegalArgumentException("P is below original PRS-CS's 1e-323 floor; supply BETA/SE or explicitly admit the original floor with a warning");
            p = UPSTREAM_MINIMUM_P;
        }
        double magnitude = Math.abs(Normal.quantile(p / 2, 0, 1, true, false));
        return finite(sign * Math.signum(beta) * magnitude / sampleRoot(sampleSize), "standardized marginal effect");
    }

    /** Use OR direction and a two-sided P value; the OR magnitude is not used. */
    public static double fromOddsRatioP(double oddsRatio, double p, long sampleSize,
            int sign, PValuePolicy policy) {
        return fromBetaP(logOdds(oddsRatio), p, sampleSize, sign, policy);
    }

    /** Reference effect-allele frequency after the original reference sign choice. */
    public static double effectAlleleFrequency(double referenceFirstAlleleFrequency, Alignment alignment) {
        Objects.requireNonNull(alignment, "alignment");
        if (!alignment.matched()) throw new IllegalArgumentException("An unmatched SNP has no aligned reference frequency");
        frequency(referenceFirstAlleleFrequency);
        return frequency(alignment.referenceSign() == 1
                ? referenceFirstAlleleFrequency : 1 - referenceFirstAlleleFrequency);
    }

    /** Convert a standardized-genotype posterior effect to per-allele units. */
    public static double perAllele(double standardizedEffect, double effectAlleleFrequency) {
        return finite(finite(standardizedEffect, "standardized effect") / genotypeStandardDeviation(effectAlleleFrequency), "per-allele effect");
    }

    /** Convert a per-allele effect to standardized-genotype units. */
    public static double standardized(double perAlleleEffect, double effectAlleleFrequency) {
        return finite(finite(perAlleleEffect, "per-allele effect") * genotypeStandardDeviation(effectAlleleFrequency), "standardized effect");
    }

    /** Diploid reference standard deviation; this is not estimated from targets. */
    public static double genotypeStandardDeviation(double effectAlleleFrequency) {
        double p = frequency(effectAlleleFrequency);
        double result = Math.sqrt(2 * p * (1 - p));
        if (result == 0) throw new IllegalArgumentException("Reference genotype variance underflows");
        return result;
    }

    /**
     * Validate the original retained-iteration allocation and return its size.
     * Current source divides by floor((iterations-burnin)/thin); historical
     * 1.0.0/1.1.0 source instead uses the unrounded quotient. All retain only
     * iterations divisible by thin. This common contract requires an integral
     * quotient equal to the actual retained count. It prevents mis-scaled means
     * and current-source draw-array overruns without changing upstream code.
     */
    public static int retainedSamples(int iterations, int burnin, int thin) {
        if (iterations < 1 || burnin < 0 || burnin >= iterations || thin < 1)
            throw new IllegalArgumentException("Require iterations > burnin >= 0 and thin > 0");
        int allocated = (iterations - burnin) / thin;
        int retained = iterations / thin - burnin / thin;
        if (allocated < 1 || allocated != retained || (iterations - burnin) % thin != 0)
            throw new IllegalArgumentException("Original PRS-CS thinning allocation differs from retained iterations or is empty; choose an explicit consistent iterations/burnin/thin schedule");
        return retained;
    }

    private static double sampleRoot(long n) {
        if (n < 1 || n > MAXIMUM_EXACT_SAMPLE_SIZE)
            throw new IllegalArgumentException("Supply a positive exact integer GWAS sample size no larger than 2^53-1");
        return Math.sqrt(n);
    }
    private static double logOdds(double oddsRatio) {
        if (!Double.isFinite(oddsRatio) || oddsRatio <= 0)
            throw new IllegalArgumentException("Require a finite positive odds ratio");
        return Math.log(oddsRatio);
    }
    private static double frequency(double p) {
        if (!Double.isFinite(p) || p <= 0 || p >= 1)
            throw new IllegalArgumentException("Supply a finite reference effect-allele frequency strictly between 0 and 1");
        return p;
    }
    private static void orientation(int sign) {
        if (Math.abs(sign) != 1) throw new IllegalArgumentException("Alignment sign must be +1 or -1");
    }
    private static double finite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite " + label);
        return value;
    }
    private static boolean dna(char c) { return c == 'A' || c == 'C' || c == 'G' || c == 'T'; }
    private static char complement(char c) {
        return switch (c) { case 'A' -> 'T'; case 'T' -> 'A'; case 'C' -> 'G'; case 'G' -> 'C';
            default -> throw new IllegalArgumentException("Unresolved DNA allele"); };
    }
}
