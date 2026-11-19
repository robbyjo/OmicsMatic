/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.coloc;

import java.util.HashSet;
import java.util.List;

/** Immutable summary statistics for single-causal-variant ABF colocalization. */
public record ColocAbfInput(
        TraitType type, List<String> variants, double[] beta, double[] variance,
        double[] pValues, double[] frequencies, Double sampleSize,
        Double traitStandardDeviation, Double caseFraction, long[] positions) {
    public enum TraitType { QUANTITATIVE, CASE_CONTROL }
    public static final int MAX_VARIANTS = 1_000_000;

    public ColocAbfInput {
        if (type == null || variants == null || variants.isEmpty()
                || variants.size() > MAX_VARIANTS)
            throw new IllegalArgumentException("require a trait type and 1..1000000 variants");
        if (variants.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("variant IDs must not be null");
        variants = List.copyOf(variants);
        var seen = new HashSet<String>();
        for (String id : variants) {
            if (id == null || id.isBlank() || !seen.add(id))
                throw new IllegalArgumentException("variant IDs must be nonblank, unique and exact");
            for (int i = 0; i < id.length(); i++) {
                char c = id.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    if (++i == id.length() || !Character.isLowSurrogate(id.charAt(i)))
                        throw new IllegalArgumentException("variant ID contains an unpaired surrogate");
                } else if (Character.isLowSurrogate(c)) {
                    throw new IllegalArgumentException("variant ID contains an unpaired surrogate");
                }
            }
        }
        int n = variants.size();
        beta = vector(beta, n, "beta", false);
        variance = vector(variance, n, "variance", true);
        pValues = vector(pValues, n, "pValues", true);
        frequencies = vector(frequencies, n, "frequencies", true);
        if (pValues != null) for (double p : pValues)
            if (p > 1) throw new IllegalArgumentException("P values must be in (0,1]; no floors are applied");
        // Upstream's MAF contract is (0,1), not (0,0.5]. Never flip supplied frequencies.
        if (frequencies != null) for (double f : frequencies)
            if (f >= 1) throw new IllegalArgumentException("frequencies must be in (0,1)");
        if ((beta == null) != (variance == null))
            throw new IllegalArgumentException("supply beta and variance together");
        positive(sampleSize, "sample size");
        positive(traitStandardDeviation, "trait standard deviation");
        if (caseFraction != null && !(Double.isFinite(caseFraction) && caseFraction > 0 && caseFraction < 1))
            throw new IllegalArgumentException("case fraction must be finite and in (0,1)");
        if (beta == null && (pValues == null || frequencies == null || sampleSize == null
                || (type == TraitType.CASE_CONTROL && caseFraction == null)))
            throw new IllegalArgumentException("P-value mode requires P, frequencies, scalar N and case fraction for case-control");
        if (beta != null && type == TraitType.QUANTITATIVE && traitStandardDeviation == null
                && (frequencies == null || sampleSize == null))
            throw new IllegalArgumentException("quantitative estimates require sdY or frequencies and scalar N");
        if (positions != null) {
            if (positions.length != n) throw new IllegalArgumentException("position count differs");
            positions = positions.clone();
            for (long p : positions) if (p < 0)
                throw new IllegalArgumentException("positions must be nonnegative integers");
        }
    }

    private static double[] vector(double[] x, int n, String name, boolean positive) {
        if (x == null) return null;
        if (x.length != n) throw new IllegalArgumentException(name + " length differs");
        x = x.clone();
        for (double v : x) if (!Double.isFinite(v) || (positive && !(v > 0)))
            throw new IllegalArgumentException(name + " has a nonfinite or invalid value");
        return x;
    }
    private static void positive(Double value, String name) {
        if (value != null && !(Double.isFinite(value) && value > 0))
            throw new IllegalArgumentException(name + " must be finite and positive");
    }
    public boolean usesEffectEstimates() { return beta != null; }
    @Override public double[] beta() { return beta == null ? null : beta.clone(); }
    @Override public double[] variance() { return variance == null ? null : variance.clone(); }
    @Override public double[] pValues() { return pValues == null ? null : pValues.clone(); }
    @Override public double[] frequencies() { return frequencies == null ? null : frequencies.clone(); }
    @Override public long[] positions() { return positions == null ? null : positions.clone(); }
    double[] rawBeta() { return beta; }
    double[] rawVariance() { return variance; }
    double[] rawPValues() { return pValues; }
    double[] rawFrequencies() { return frequencies; }
    long[] rawPositions() { return positions; }
}
