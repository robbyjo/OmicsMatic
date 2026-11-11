/*
 * Adapted from R 3.6.2 stats/src/lowess.c and stats/R/lowess.R.
 * Copyright (C) 1996 Robert Gentleman and Ross Ihaka
 * Copyright (C) 1999-2016 The R Core Team
 * Copyright (C) 2026 JLinAlg contributors (Java port, admission and capacities)
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Original source permits GPL version 2 or any later version.
 * Source: https://cran.r-project.org/src/base/R-3/R-3.6.2.tar.gz
 */
package org.jlinalg.loess;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Native source-specific LOWESS: local linear tricube fits, delta interpolation
 * and bisquare robustness iterations. The finite-pair API never drops points,
 * rescales coordinates, changes span or substitutes the separate loess algorithm.
 */
public final class RLowess {
    private RLowess() { }
    public record Options(double span, int robustnessIterations, double delta) {
        public Options {
            if (!Double.isFinite(span) || span <= 0 || robustnessIterations < 0 || robustnessIterations == Integer.MAX_VALUE
                    || !Double.isFinite(delta) || delta < 0)
                throw new IllegalArgumentException("LOWESS requires finite span>0, bounded nonnegative iterations and finite delta>=0");
        }
    }
    /** Actual local arithmetic visits are counted; tied coordinates may extend a neighborhood. */
    public record Limits(int observations, long localVisits, long temporaryEntries) {
        public Limits {
            if (observations < 1 || observations > 10000000 || localVisits < 1 || temporaryEntries < 1)
                throw new IllegalArgumentException("Positive bounded LOWESS capacities are required");
        }
        public static Limits defaults() { return new Limits(1000000, 100000000, 12000000); }
    }
    /** R's sorted x/fitted output, with zero-based original indices retained for explicit remapping. */
    public record Result(double[] x, double[] fitted, int[] originalIndices) {
        public Result { x = x.clone(); fitted = fitted.clone(); originalIndices = originalIndices.clone(); }
        @Override public double[] x() { return x.clone(); }
        @Override public double[] fitted() { return fitted.clone(); }
        @Override public int[] originalIndices() { return originalIndices.clone(); }
    }
    public static Result fit(double[] x, double[] y) {
        validatePairs(x, y);
        double minimum = x[0], maximum = x[0];
        for (double value : x) { minimum = Math.min(minimum, value); maximum = Math.max(maximum, value); }
        return fit(x, y, new Options(2.0 / 3, 3, 0.01 * (maximum - minimum)), Limits.defaults(), () -> false);
    }
    public static Result fit(double[] x, double[] y, Options options) {
        return fit(x, y, options, Limits.defaults(), () -> false);
    }
    /** Caller must not mutate input arrays during the call. No result is returned after cancellation,
     * capacity refusal or unassessable numeric arithmetic; input arrays remain unchanged.
     */
    public static Result fit(double[] sourceX, double[] sourceY, Options options, Limits limits, BooleanSupplier cancelled) {
        Objects.requireNonNull(options); Objects.requireNonNull(limits); Objects.requireNonNull(cancelled); check(cancelled);
        validatePairs(sourceX, sourceY); int n = sourceX.length;
        if (n > limits.observations() || 11L * n > limits.temporaryEntries()) throw new IllegalArgumentException("Complete LOWESS observation/temporary-vector capacity exceeded");
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        // R order keeps ties in source order, including numerically equal -0 and +0.
        Arrays.sort(order, (a, b) -> sourceX[a] < sourceX[b] ? -1 : sourceX[a] > sourceX[b] ? 1 : Integer.compare(a, b));
        check(cancelled);
        double[] x = new double[n], y = new double[n], fitted = new double[n]; int[] indices = new int[n];
        for (int i = 0; i < n; i++) { x[i] = sourceX[order[i]]; y[i] = sourceY[order[i]]; indices[i] = order[i]; }
        if (n == 1) { fitted[0] = y[0]; check(cancelled); return new Result(x, fitted, indices); }
        double spanCount = options.span() * n + 1e-7;
        if (!Double.isFinite(spanCount) || spanCount > Integer.MAX_VALUE) throw new IllegalArgumentException("LOWESS span count exceeds the original C integer representation; no clamping or replacement performed");
        if (!Double.isFinite(x[n - 1] - x[0])) throw new ArithmeticException("LOWESS coordinate range overflows; no coordinate rescaling performed");
        int neighborhood = Math.max(2, Math.min(n, (int) spanCount));
        var budget = new Budget(limits.localVisits(), cancelled);
        double[] robustness = new double[n], scratch = new double[n];
        for (long iteration = 1; iteration <= (long) options.robustnessIterations() + 1; iteration++) {
            check(cancelled); int left = 0, right = neighborhood - 1, last = -1, current = 0;
            for (;;) {
                budget.visit();
                if (right < n - 1 && x[current] - x[left] > x[right + 1] - x[current]) { left++; right++; continue; }
                if (!lowest(x, y, current, fitted, left, right, scratch, iteration > 1, robustness, budget)) fitted[current] = y[current];
                if (last < current - 1) {
                    double denominator = x[current] - x[last];
                    for (int j = last + 1; j < current; j++) {
                        budget.visit(); double alpha = (x[j] - x[last]) / denominator;
                        fitted[j] = alpha * fitted[current] + (1 - alpha) * fitted[last];
                    }
                }
                last = current; double cut = x[last] + options.delta();
                for (current = last + 1; current < n; current++) {
                    budget.visit(); if (x[current] > cut) break;
                    if (x[current] == x[last]) { fitted[current] = fitted[last]; last = current; }
                }
                current = Math.max(last + 1, current - 1);
                if (last >= n - 1) break;
            }
            double scale = 0;
            for (int i = 0; i < n; i++) {
                budget.visit(); scratch[i] = y[i] - fitted[i];
                if (!Double.isFinite(scratch[i]) || !Double.isFinite(fitted[i])) throw new ArithmeticException("Original LOWESS arithmetic is unassessable for these finite pairs; no fitted value replaced");
                scale += Math.abs(scratch[i]);
            }
            scale /= n;
            if (iteration > options.robustnessIterations()) break;
            for (int i = 0; i < n; i++) { budget.visit(); robustness[i] = Math.abs(scratch[i]); }
            Arrays.sort(robustness); check(cancelled);
            int middle = n / 2;
            double cmad = n % 2 == 0 ? 3 * (robustness[middle] + robustness[n - middle - 1]) : 6 * robustness[middle];
            if (cmad < 1e-7 * scale) break; // Source's effectively-zero scale termination.
            double c9 = 0.999 * cmad, c1 = 0.001 * cmad;
            for (int i = 0; i < n; i++) {
                budget.visit(); double residual = Math.abs(scratch[i]);
                if (residual <= c1) robustness[i] = 1;
                else if (residual <= c9) robustness[i] = square(1 - square(residual / cmad));
                else robustness[i] = 0;
            }
        }
        check(cancelled); return new Result(x, fitted, indices);
    }
    private static boolean lowest(double[] x, double[] y, int point, double[] fitted, int left, int right,
                                  double[] weights, boolean robust, double[] robustness, Budget budget) {
        int n = x.length; double center = x[point], range = x[n - 1] - x[0];
        double h = Math.max(center - x[left], x[right] - center), h9 = 0.999 * h, h1 = 0.001 * h;
        double sum = 0; int j = left;
        while (j < n) {
            budget.visit(); weights[j] = 0; double distance = Math.abs(x[j] - center);
            if (distance <= h9) {
                weights[j] = distance <= h1 ? 1 : cube(1 - cube(distance / h));
                if (robust) weights[j] *= robustness[j]; sum += weights[j];
            } else if (x[j] > center) break;
            j++;
        }
        int last = j - 1;
        if (sum <= 0) return false; // Source copies the current original y when all local weights are zero.
        for (j = left; j <= last; j++) { budget.visit(); weights[j] /= sum; }
        if (h > 0) {
            double weightedCenter = 0;
            for (j = left; j <= last; j++) { budget.visit(); weightedCenter += weights[j] * x[j]; }
            double slope = center - weightedCenter, variance = 0;
            for (j = left; j <= last; j++) { budget.visit(); variance += weights[j] * square(x[j] - weightedCenter); }
            if (Math.sqrt(variance) > 0.001 * range) {
                slope /= variance;
                for (j = left; j <= last; j++) { budget.visit(); weights[j] *= slope * (x[j] - weightedCenter) + 1; }
            }
        }
        fitted[point] = 0;
        for (j = left; j <= last; j++) { budget.visit(); fitted[point] += weights[j] * y[j]; }
        return true;
    }
    private static double square(double value) { return value * value; }
    private static double cube(double value) { return value * value * value; }
    private static void validatePairs(double[] x, double[] y) {
        if (x == null || y == null || x.length == 0 || x.length != y.length) throw new IllegalArgumentException("LOWESS requires complete nonempty equally sized pairs");
        for (int i = 0; i < x.length; i++) if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) throw new IllegalArgumentException("LOWESS finite-pair API refuses missing/nonfinite observations without dropping or replacing them");
    }
    private static void check(BooleanSupplier cancelled) { if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException("LOWESS cancelled; no result returned"); }
    private static final class Budget {
        private final long maximum; private final BooleanSupplier cancelled; private long visits;
        Budget(long maximum, BooleanSupplier cancelled) { this.maximum = maximum; this.cancelled = cancelled; }
        void visit() {
            if (visits >= maximum) throw new IllegalArgumentException("Complete LOWESS local-arithmetic capacity exceeded; no prefix or reduced-span result returned");
            if ((visits++ & 1023) == 0) check(cancelled);
        }
    }
}
