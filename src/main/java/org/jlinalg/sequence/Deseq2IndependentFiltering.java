/*
 * Default-method port referenced to DESeq2 1.26.0 pvalueAdjustment
 * (Michael Love, Wolfgang Huber, Simon Anders; LGPL >= 3),
 * genefilter filtered_p (Artistic-2.0), and R 3.6.2 quantile/p.adjust
 * (The R Core Team; GPL version 2 or later).
 * Copyright (C) 2026 JLinAlg contributors (Java implementation)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.sequence;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.jlinalg.loess.RLowess;

/** Native DESeq2 1.26.0 default independent filtering: base-mean Type7 quantiles,
 * BH families, strict rejection counts, original LOWESS and filter-index choice.
 * Model fitting, p-value calculation and alternative filters/adjustment methods
 * remain separate operations. Rows and source NA/NaN distinctions are retained.
 */
public final class Deseq2IndependentFiltering {
    private Deseq2IndependentFiltering() { }
    public record Limits(int hypotheses, long familyVisits, long temporaryEntries, RLowess.Limits lowess) {
        public Limits {
            if (hypotheses < 1 || hypotheses > 10000000 || familyVisits < 1 || temporaryEntries < 1 || lowess == null)
                throw new IllegalArgumentException("Positive bounded independent-filter capacities are required");
        }
        public static Limits defaults() { return new Limits(1000000, 100000000, 13000000, RLowess.Limits.defaults()); }
    }
    /** selectedIndex is zero-based; adjusted values stay in the original hypothesis order. */
    public record Result(double alpha, double[] theta, double[] cutoffs, int[] rejections, double[] smoothed,
                         int selectedIndex, OptionalDouble selectionThreshold, double[] adjusted) {
        public Result { theta = theta.clone(); cutoffs = cutoffs.clone(); rejections = rejections.clone(); smoothed = smoothed.clone(); adjusted = adjusted.clone(); }
        @Override public double[] theta() { return theta.clone(); }
        @Override public double[] cutoffs() { return cutoffs.clone(); }
        @Override public int[] rejections() { return rejections.clone(); }
        @Override public double[] smoothed() { return smoothed.clone(); }
        @Override public double[] adjusted() { return adjusted.clone(); }
        public double selectedTheta() { return theta[selectedIndex]; }
        public double selectedCutoff() { return cutoffs[selectedIndex]; }
    }
    public static Result filter(double[] baseMeans, double[] pValues, double alpha) {
        return filter(baseMeans, pValues, alpha, Limits.defaults(), () -> false);
    }
    /** Finite nonnegative base means and valid p-values/NA/NaN are required. Caller must not mutate inputs during a call.
     * No result is returned after capacity refusal or cancellation.
     */
    public static Result filter(double[] baseMeans, double[] pValues, double alpha, Limits limits, BooleanSupplier cancelled) {
        Objects.requireNonNull(limits); Objects.requireNonNull(cancelled); check(cancelled); validate(baseMeans, pValues);
        int n = baseMeans.length;
        if (!Double.isFinite(alpha) || alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Independent filtering requires an explicit alpha in (0,1)");
        if (n > limits.hypotheses() || 12L * n + 500 > limits.temporaryEntries() || 103L * n > limits.familyVisits())
            throw new IllegalArgumentException("Complete independent-filter observation/work/temporary capacity exceeded");
        double[] sorted = baseMeans.clone(); Arrays.sort(sorted); check(cancelled);
        int zeros = 0; for (double value : baseMeans) if (value == 0) zeros++;
        double lower = (double) zeros / n, upper = lower < 0.95 ? 0.95 : 1;
        double[] theta = new double[50], cutoffs = new double[50]; int[] rejections = new int[50];
        double step = (upper - lower) / 49;
        for (int i = 0; i < theta.length; i++) {
            check(cancelled); theta[i] = i == 49 ? upper : i == 0 ? lower : lower + i * step;
            cutoffs[i] = quantile7Sorted(sorted, theta[i]);
            double[] adjusted = adjustAtCutoffUnchecked(baseMeans, pValues, cutoffs[i], cancelled);
            for (double value : adjusted) if (!Double.isNaN(value) && value < alpha) rejections[i]++;
        }
        double[] counts = new double[50]; for (int i = 0; i < counts.length; i++) counts[i] = rejections[i];
        var curve = RLowess.fit(theta, counts, new RLowess.Options(1.0 / 5, 3, 0.01 * (theta[49] - theta[0])), limits.lowess(), cancelled);
        double[] smoothed = curve.fitted(); int maximum = 0; for (int count : rejections) maximum = Math.max(maximum, count);
        int selected = 0; OptionalDouble selectionThreshold = OptionalDouble.empty();
        if (maximum > 10) {
            double sum = 0, correction = 0, maximumSmooth = Double.NEGATIVE_INFINITY; int positive = 0;
            for (int i = 0; i < smoothed.length; i++) {
                maximumSmooth = Math.max(maximumSmooth, smoothed[i]);
                if (rejections[i] > 0) {
                    double residual = rejections[i] - smoothed[i], square = residual * residual, next = sum + square;
                    correction += Math.abs(sum) >= Math.abs(square) ? (sum - next) + square : (square - next) + sum;
                    sum = next; positive++;
                }
            }
            double threshold = maximumSmooth - Math.sqrt((sum + correction) / positive); selectionThreshold = OptionalDouble.of(threshold);
            for (int i = 0; i < rejections.length; i++) if (rejections[i] > threshold) { selected = i; break; }
        }
        double[] adjusted = adjustAtCutoffUnchecked(baseMeans, pValues, cutoffs[selected], cancelled);
        check(cancelled); return new Result(alpha, theta, cutoffs, rejections, smoothed, selected, selectionThreshold, adjusted);
    }
    /** Type7 interpolation with the R3.6.2 expression order; input is not modified. */
    public static double quantile7(double[] values, double probability) {
        if (values == null || values.length == 0 || !Double.isFinite(probability) || probability < 0 || probability > 1)
            throw new IllegalArgumentException("Require a nonempty finite population and probability in [0,1]");
        double[] sorted = values.clone(); for (double value : sorted) if (!Double.isFinite(value)) throw new IllegalArgumentException("Type7 finite-value API does not omit missing/nonfinite values");
        Arrays.sort(sorted); return quantile7Sorted(sorted, probability);
    }
    private static double quantile7Sorted(double[] sorted, double probability) {
        double index = 1 + (sorted.length - 1) * probability, low = Math.floor(index);
        int left = (int) low - 1, right = (int) Math.ceil(index) - 1; double value = sorted[left];
        if (index > low && sorted[right] != value) { double fraction = index - low; value = (1 - fraction) * value + fraction * sorted[right]; }
        return value;
    }
    /** Source filtered_p uses baseMean>=cutoff; excluded rows are source NA.
     * Included NA and NaN p-values preserve their original payloads. The default
     * p.adjust family size excludes both, as in R's lazy default n=length(p).
     */
    public static double[] adjustAtCutoff(double[] baseMeans, double[] pValues, double cutoff) {
        validate(baseMeans, pValues);
        if (!Double.isFinite(cutoff)) throw new IllegalArgumentException("Require a finite explicit filter cutoff");
        return adjustAtCutoffUnchecked(baseMeans, pValues, cutoff, () -> false);
    }
    private static double[] adjustAtCutoffUnchecked(double[] baseMeans, double[] pValues, double cutoff, BooleanSupplier cancelled) {
        double[] result = new double[pValues.length]; Arrays.fill(result, Deseq2SizeFactors.MISSING);
        Integer[] order = new Integer[pValues.length]; int count = 0;
        for (int i = 0; i < pValues.length; i++) {
            if ((i & 1023) == 0) check(cancelled);
            if (baseMeans[i] >= cutoff) {
                if (Double.isNaN(pValues[i])) result[i] = pValues[i];
                else order[count++] = i;
            }
        }
        if (count == 0) return result;
        Arrays.sort(order, 0, count, (a, b) -> pValues[a] > pValues[b] ? -1 : pValues[a] < pValues[b] ? 1 : Integer.compare(a, b));
        check(cancelled); double running = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            if ((i & 1023) == 0) check(cancelled);
            int index = order[i];
            // R evaluates n/rank before multiplication. Reordering this arithmetic can change strict alpha-boundary decisions.
            double candidate = (double) count / (count - i) * pValues[index];
            running = Math.min(running, candidate); result[index] = Math.min(1, running);
        }
        return result;
    }
    private static void validate(double[] baseMeans, double[] pValues) {
        if (baseMeans == null || pValues == null || baseMeans.length == 0 || baseMeans.length != pValues.length)
            throw new IllegalArgumentException("Require complete equal nonempty base-mean and p-value populations");
        for (int i = 0; i < baseMeans.length; i++) {
            if (!Double.isFinite(baseMeans[i]) || baseMeans[i] < 0) throw new IllegalArgumentException("Base means must be finite and nonnegative; none are dropped or replaced");
            if (!Double.isNaN(pValues[i]) && (!Double.isFinite(pValues[i]) || pValues[i] < 0 || pValues[i] > 1))
                throw new IllegalArgumentException("P-values must be in [0,1] or original NA/NaN; none are repaired");
        }
    }
    private static void check(BooleanSupplier cancelled) { if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException("Independent filtering cancelled; no result returned"); }
}
