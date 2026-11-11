/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Source-compatible arithmetic from RSEM WriteResults.h (RSEM authors, GPL-3.0).
 * https://github.com/deweylab/RSEM/blob/e4dda70e90fb5eb9b831306f1c381f8bbf71ef0e/WriteResults.h */
package org.jlinalg.sequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Native RSEM effective-length, expression and ordinary EM gene aggregation.
 * These operations consume explicitly supplied model quantities; they do not
 * fit an alignment model, infer theta from rounded counts, or reproduce Gibbs
 * posterior estimates. RSEM's noise component, mappability correction, minimum
 * effective length and zero-expression gene fallback are retained.
 */
public final class RsemAbundance {
    /** RSEM utils.h arithmetic threshold, distinct from its effective-length cutoff. */
    public static final double EPSILON = 1e-300;
    /** Expected effective lengths below one are set to zero by original RSEM. */
    public static final double MINIMUM_EFFECTIVE_LENGTH = 1.0;

    private RsemAbundance() { }

    /** Exact releases whose original expression arithmetic is admitted here. */
    public enum Release {
        /** Historical release before the zero-aligned-read fix. */ V1_2_28,
        /** Historical release before the zero-aligned-read fix. */ V1_3_0,
        /** Historical release before the zero-aligned-read fix. */ V1_3_1,
        /** Release introducing the zero-aligned-read expression fallback. */ V1_3_2,
        /** Current original release with the zero-aligned-read fallback. */ V1_3_3
    }

    /**
     * Exact transcript identity and pre-tail length.
     * @param transcriptId exact transcript identifier
     * @param geneId exact gene identifier
     * @param length reference transcript bases, excluding an added poly-A tail
     * @param polyATail explicitly added tail length
     */
    public record Reference(String transcriptId, String geneId, int length, int polyATail) {
        /**
         * Preserves identifiers and rejects lengths beyond the admitted original integer arithmetic.
         * @param transcriptId exact transcript identifier
         * @param geneId exact gene identifier
         * @param length pre-tail length
         * @param polyATail added tail bases
         */
        public Reference {
            identifier(transcriptId); identifier(geneId);
            if (length < 1 || polyATail < 0 || (long) length + polyATail >= Integer.MAX_VALUE)
                throw new IllegalArgumentException("Reference length/tail exceeds admitted RSEM integer arithmetic");
        }
    }

    /**
     * An explicit fragment-length probability mass function, without renormalization.
     * @param minimum smallest represented fragment length, inclusive
     * @param probabilities probabilities in increasing length order, including zeros
     */
    public record FragmentLengths(int minimum, double[] probabilities) {
        /**
         * Copies the complete finite, normalized distribution.
         * @param minimum inclusive lower length
         * @param probabilities probability mass function
         */
        public FragmentLengths {
            Objects.requireNonNull(probabilities, "probabilities");
            probabilities = probabilities.clone();
            if (minimum < 1 || probabilities.length == 0
                    || (long) minimum + probabilities.length - 1 >= Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid fragment-length support");
            double sum = 0;
            for (double p : probabilities) {
                if (!Double.isFinite(p) || p < 0 || p > 1)
                    throw new IllegalArgumentException("Fragment probabilities must be finite and between zero and one");
                sum += p;
            }
            if (Math.abs(sum - 1) > 1e-8)
                throw new IllegalArgumentException("A complete normalized fragment-length distribution is required");
        }
        /** @return a defensive copy of the exact supplied probabilities */
        @Override public double[] probabilities() { return probabilities.clone(); }
    }

    /**
     * Unrounded source expression quantities in transcript order.
     * @param tpm transcript TPM values
     * @param fpkm transcript FPKM values
     */
    public record Expression(double[] tpm, double[] fpkm) {
        /**
         * Copies complete equal-sized, nonnegative finite arrays.
         * @param tpm TPM
         * @param fpkm FPKM
         */
        public Expression {
            Objects.requireNonNull(tpm, "tpm"); Objects.requireNonNull(fpkm, "fpkm");
            tpm = tpm.clone(); fpkm = fpkm.clone();
            if (tpm.length == 0 || tpm.length != fpkm.length)
                throw new IllegalArgumentException("Complete equal-sized expression arrays are required");
            nonnegative(tpm, "TPM"); nonnegative(fpkm, "FPKM");
        }
        /** @return a defensive copy of unrounded TPM */
        @Override public double[] tpm() { return tpm.clone(); }
        /** @return a defensive copy of unrounded FPKM */
        @Override public double[] fpkm() { return fpkm.clone(); }
    }

    /**
     * An ordinary EM isoform result before the original two-decimal serialization.
     * @param reference exact transcript metadata
     * @param effectiveLength expected effective length
     * @param expectedCount original expected fragment count
     * @param tpm TPM
     * @param fpkm FPKM
     * @param isoformPercent percent of gene TPM, not percent of gene expected counts
     */
    public record Isoform(Reference reference, double effectiveLength, double expectedCount,
                          double tpm, double fpkm, double isoformPercent) { }

    /**
     * An ordinary EM gene result before the original two-decimal serialization.
     * @param geneId exact gene identifier
     * @param transcriptIds original contiguous group order
     * @param length TPM-weighted pre-tail length, or the unweighted mean for a zero-expression gene
     * @param effectiveLength similarly weighted effective length
     * @param expectedCount sum of original expected counts
     * @param tpm sum of isoform TPM
     * @param fpkm sum of isoform FPKM
     */
    public record Gene(String geneId, List<String> transcriptIds, double length,
                       double effectiveLength, double expectedCount, double tpm, double fpkm) {
        /**
         * Copies the identifier list.
         * @param geneId exact gene identifier
         * @param transcriptIds ordered isoform identifiers
         * @param length weighted length
         * @param effectiveLength weighted effective length
         * @param expectedCount expected counts
         * @param tpm TPM
         * @param fpkm FPKM
         */
        public Gene { transcriptIds = List.copyOf(transcriptIds); }
    }

    /**
     * Complete ordinary EM populations, without filtering or rounding.
     * @param isoforms all original isoforms in original order
     * @param genes all contiguous original groups in original order
     */
    public record Result(List<Isoform> isoforms, List<Gene> genes) {
        /**
         * Copies both populations.
         * @param isoforms complete isoform population
         * @param genes complete gene population
         */
        public Result { isoforms = List.copyOf(isoforms); genes = List.copyOf(genes); }
    }

    /**
     * Applies the original CDF/first-moment effective-length formula, including
     * tail positions and the strict below-one cutoff. No gene union length is used.
     * @param references complete ordered reference population
     * @param fragments explicit complete learned or specified fragment-length distribution
     * @return effective lengths in exactly the supplied transcript order
     */
    public static double[] effectiveLengths(List<Reference> references, FragmentLengths fragments) {
        Objects.requireNonNull(fragments, "fragments");
        List<Reference> refs = references(references);
        double[] pdf = fragments.probabilities();
        double[] cdf = new double[pdf.length + 1], moment = new double[pdf.length + 1];
        int lower = fragments.minimum() - 1, upper = lower + pdf.length;
        for (int i = 1; i <= pdf.length; i++) {
            cdf[i] = cdf[i - 1] + pdf[i - 1];
            moment[i] = moment[i - 1] + pdf[i - 1] * (lower + i);
        }
        double[] result = new double[refs.size()];
        for (int i = 0; i < result.length; i++) {
            Reference ref = refs.get(i); int total = ref.length() + ref.polyATail();
            int p1 = Math.max(Math.min(ref.polyATail() + 1, upper) - lower, 0);
            int p2 = Math.max(Math.min(total, upper) - lower, 0);
            if (p2 == 0) continue;
            double value = ref.length() * cdf[p1]
                    + ((cdf[p2] - cdf[p1]) * (total + 1) - (moment[p2] - moment[p1]));
            if (!Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("Original effective-length arithmetic is negative or nonfinite");
            result[i] = value < MINIMUM_EFFECTIVE_LENGTH ? 0 : value;
        }
        return result;
    }

    /**
     * Applies original mappability correction and renormalizes including noise.
     * @param thetaWithNoise normalized unpolished theta with noise at index zero
     * @param effectiveLengths effective transcript lengths, without a noise entry
     * @param mappabilityWithNoise original model weights with exactly one for noise
     * @return polished theta with noise at index zero; inputs are never modified
     */
    public static double[] polishTheta(double[] thetaWithNoise, double[] effectiveLengths,
                                       double[] mappabilityWithNoise) {
        double[] theta = theta(thetaWithNoise, effectiveLengths);
        Objects.requireNonNull(mappabilityWithNoise, "mappabilityWithNoise");
        double[] mw = mappabilityWithNoise.clone();
        if (mw.length != theta.length || mw[0] != 1)
            throw new IllegalArgumentException("Complete mappability weights and noise weight one are required");
        for (double value : mw) if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Invalid mappability weight");
        double sum = 0;
        for (int i = 0; i < theta.length; i++) {
            if (i > 0 && (mw[i] < EPSILON || effectiveLengths[i - 1] < EPSILON)) theta[i] = 0;
            else { theta[i] /= mw[i]; sum += theta[i]; }
        }
        if (!Double.isFinite(sum) || sum < EPSILON)
            throw new IllegalArgumentException("Original theta polishing has no positive finite normalizer");
        for (int i = 0; i < theta.length; i++) theta[i] /= sum;
        return theta;
    }

    /**
     * Applies original expression arithmetic to polished theta. Signal fractions
     * exclude noise. Small denominators use the original one-valued fallback;
     * an all-noise library therefore has zero TPM and FPKM.
     * @param thetaWithNoise normalized polished theta including noise
     * @param effectiveLengths effective transcript lengths, without noise
     * @return unrounded expression values
     */
    public static Expression expression(double[] thetaWithNoise, double[] effectiveLengths) {
        return expression(thetaWithNoise, effectiveLengths, Release.V1_3_3);
    }

    /**
     * Applies release-specific original expression behavior. Releases through
     * 1.3.1 fail when the signal normalizer is below EPSILON; the 1.3.2 fallback
     * must not be silently substituted for those historical requests.
     * @param thetaWithNoise normalized polished theta including noise
     * @param effectiveLengths effective transcript lengths, without noise
     * @param release exact original expression release
     * @return unrounded expression values for that release
     */
    public static Expression expression(double[] thetaWithNoise, double[] effectiveLengths, Release release) {
        Objects.requireNonNull(release, "release");
        boolean zeroLibraryFix = release == Release.V1_3_2 || release == Release.V1_3_3;
        double[] theta = theta(thetaWithNoise, effectiveLengths);
        double[] fraction = new double[effectiveLengths.length], tpm = new double[fraction.length], fpkm = new double[fraction.length];
        double denominator = 0;
        for (int i = 0; i < fraction.length; i++) if (effectiveLengths[i] >= EPSILON) {
            fraction[i] = theta[i + 1]; denominator += fraction[i];
        }
        if (denominator < EPSILON) {
            if (!zeroLibraryFix) throw new IllegalArgumentException("Original RSEM through 1.3.1 fails: No alignable reads?!");
            denominator = 1;
        }
        for (int i = 0; i < fraction.length; i++) fraction[i] /= denominator;
        double totalFpkm = 0;
        for (int i = 0; i < fraction.length; i++) {
            if (effectiveLengths[i] >= EPSILON) fpkm[i] = fraction[i] * 1e9 / effectiveLengths[i];
            totalFpkm += fpkm[i];
        }
        if (zeroLibraryFix && totalFpkm < EPSILON) totalFpkm = 1;
        for (int i = 0; i < fraction.length; i++) tpm[i] = fpkm[i] / totalFpkm * 1e6;
        return new Expression(tpm, fpkm);
    }

    /**
     * Aggregates complete ordinary EM results with original within-gene TPM
     * weighting and the source zero-expression fallback. Expected counts are
     * supplied independently; they are not reconstructed from theta or rounded TPM.
     * @param references exact original transcript/group order
     * @param effectiveLengths unrounded effective lengths
     * @param expectedCounts unrounded original expected counts
     * @param expression unrounded original or native-audited expression
     * @return complete isoform and gene populations
     */
    public static Result aggregate(List<Reference> references, double[] effectiveLengths,
                                    double[] expectedCounts, Expression expression) {
        List<Reference> refs = references(references); Objects.requireNonNull(expression, "expression");
        lengths(effectiveLengths); Objects.requireNonNull(expectedCounts, "expectedCounts");
        double[] counts = expectedCounts.clone(), tpm = expression.tpm(), fpkm = expression.fpkm();
        if (refs.size() != effectiveLengths.length || refs.size() != counts.length || refs.size() != tpm.length)
            throw new IllegalArgumentException("Every original reference transcript must have all model quantities");
        nonnegative(counts, "expected count");
        List<Isoform> isoforms = new ArrayList<>(); List<Gene> genes = new ArrayList<>();
        Set<String> completed = new HashSet<>();
        for (int begin = 0; begin < refs.size();) {
            String gene = refs.get(begin).geneId(); int end = begin + 1;
            if (!completed.add(gene)) throw new IllegalArgumentException("Original gene groups must remain contiguous");
            while (end < refs.size() && refs.get(end).geneId().equals(gene)) end++;
            double sumCounts = 0, sumTpm = 0, sumFpkm = 0;
            for (int j = begin; j < end; j++) {sumCounts += counts[j]; sumTpm += tpm[j]; sumFpkm += fpkm[j];}
            if (!Double.isFinite(sumCounts) || !Double.isFinite(sumTpm) || !Double.isFinite(sumFpkm))
                throw new IllegalArgumentException("Original gene aggregate overflow");
            double length = 0, effective = 0; List<String> ids = new ArrayList<>();
            for (int j = begin; j < end; j++) {
                double isoFraction = sumTpm > EPSILON ? tpm[j] / sumTpm : 0;
                double weight = sumTpm < EPSILON ? 1.0 / (end - begin) : isoFraction;
                length += refs.get(j).length() * weight; effective += effectiveLengths[j] * weight;
                ids.add(refs.get(j).transcriptId());
                isoforms.add(new Isoform(refs.get(j), effectiveLengths[j], counts[j], tpm[j], fpkm[j], isoFraction * 100));
            }
            genes.add(new Gene(gene, ids, length, effective, sumCounts, sumTpm, sumFpkm)); begin = end;
        }
        return new Result(isoforms, genes);
    }

    private static List<Reference> references(List<Reference> input) {
        List<Reference> refs = List.copyOf(input);
        if (refs.isEmpty()) throw new IllegalArgumentException("A complete nonempty reference population is required");
        Set<String> ids = new HashSet<>();
        for (Reference ref : refs) if (!ids.add(ref.transcriptId()))
            throw new IllegalArgumentException("Duplicate exact transcript identifier: " + ref.transcriptId());
        return refs;
    }
    private static void identifier(String id) {
        if (id == null || id.isBlank() || id.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("A nonempty exact identifier without control characters is required");
    }
    private static void nonnegative(double[] values, String name) {
        for (double value : values) if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Nonfinite or negative " + name);
    }
    private static void lengths(double[] values) {
        Objects.requireNonNull(values, "effectiveLengths");
        if (values.length == 0) throw new IllegalArgumentException("Complete effective lengths are required");
        for (double value : values) if (!Double.isFinite(value) || value < 0 || (value > 0 && value < 1))
            throw new IllegalArgumentException("RSEM effective lengths must be zero or at least one");
    }
    private static double[] theta(double[] input, double[] effective) {
        lengths(effective); Objects.requireNonNull(input, "thetaWithNoise"); double[] values = input.clone();
        if (values.length != effective.length + 1) throw new IllegalArgumentException("Theta must include exactly one noise component");
        nonnegative(values, "theta"); double sum = 0;
        for (double value : values) {if (value > 1) throw new IllegalArgumentException("Theta exceeds one"); sum += value;}
        if (Math.abs(sum - 1) > 1e-8) throw new IllegalArgumentException("A normalized original theta vector is required");
        return values;
    }
}
