/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Derived from limma 3.68.5 fitFDist/squeezeVar (Gordon Smyth and Belinda
 * Phipson, GPL >= 2) and statmod 1.5.2 logmdigamma (Gordon Smyth, GPL-2 | GPL-3).
 * See docs/literature/LIMMA_VARIANCE.md for exact sources and supported scope.
 */
package org.jlinalg.differential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jdistlib.math.PolyGamma;

/**
 * Source-bound variance components for limma 3.68.5, without an R subprocess.
 * Implements untrended fitFDist, explicitly legacy nonrobust squeezeVar and
 * posterior variances from supplied hyperparameters. This is not eBayes,
 * voom, a fitted differential model, or the modern unequal-df estimator.
 * Original missing/infinite outcomes remain visible. Callers must check
 * {@code finite()} and warnings before admitting a complete analysis.
 */
public final class LimmaVarianceModeration {
    private LimmaVarianceModeration() { }

    /** fitFDist's scalar scale and denominator df; positive infinity is valid df. */
    public record Prior(double scale, double degreesOfFreedom, int informative,
            int filtered, int offset, List<String> warnings) {
        public Prior { warnings = List.copyOf(warnings); }
        public boolean finite() {
            return Double.isFinite(scale) && scale >= 0
                && !Double.isNaN(degreesOfFreedom) && degreesOfFreedom >= 0;
        }
    }

    /** Arrays and warnings are immutable snapshots; df.prior is scalar here. */
    public record Result(double[] priorVariances, double priorDegreesOfFreedom,
            double[] posteriorVariances, int informative, int filtered, int offset,
            List<String> warnings) {
        public Result {
            priorVariances = priorVariances.clone();
            posteriorVariances = posteriorVariances.clone(); warnings = List.copyOf(warnings);
        }
        @Override public double[] priorVariances() { return priorVariances.clone(); }
        @Override public double[] posteriorVariances() { return posteriorVariances.clone(); }
        public boolean finite() {
            return !Double.isNaN(priorDegreesOfFreedom) && priorDegreesOfFreedom >= 0
                && Arrays.stream(priorVariances).allMatch(v -> Double.isFinite(v) && v >= 0)
                && Arrays.stream(posteriorVariances).allMatch(v -> Double.isFinite(v) && v >= 0);
        }
    }

    /**
     * Original fitFDist(x, df1, covariate=NULL). Degrees of freedom must be a
     * scalar or one per variance; partial R recycling is refused. For n=0/1,
     * original early returns precede df validation. Original filtering and
     * median-relative offsets are exposed through counters and warnings.
     */
    public static Prior fitFDistribution(double[] variances, double[] degreesOfFreedom) {
        Objects.requireNonNull(variances, "variances"); Objects.requireNonNull(degreesOfFreedom, "df");
        int n = variances.length;
        if (n == 0) return new Prior(Double.NaN, Double.NaN, 0, 0, 0,
            List.of("FAIL2: NO COMPLETE RESULT: original fitFDist returns NA for empty x"));
        if (n == 1) return prior(variances[0], 0, 1, 0, 0, new ArrayList<>());
        shape(degreesOfFreedom, n, "df");
        List<String> warnings = new ArrayList<>();
        if (degreesOfFreedom.length == 1
                && (!Double.isFinite(degreesOfFreedom[0]) || degreesOfFreedom[0] <= 1e-15))
            return new Prior(Double.NaN, Double.NaN, 0, n, 0,
                List.of("FAIL2: NO COMPLETE RESULT: original fitFDist returns NA for invalid scalar df"));
        double[] x = new double[n], df = new double[n]; int count = 0;
        for (int i = 0; i < n; i++) {
            double d = at(degreesOfFreedom, i), v = variances[i];
            if (Double.isFinite(d) && d > 1e-15 && Double.isFinite(v) && v > -1e-15) {
                x[count] = v; df[count++] = d;
            }
        }
        int filtered = n - count;
        if (filtered > 0) warnings.add("Original fitFDist excludes " + filtered
            + " nonfinite/negative variances or nonfinite/small df values from prior estimation");
        if (count == 0) throw new IllegalArgumentException(
            "FAIL2: NO COMPLETE RESULT: original fitFDist cannot estimate with no informative values");
        if (count == 1) return prior(x[0], 0, 1, filtered, 0, warnings);
        x = Arrays.copyOf(x, count); df = Arrays.copyOf(df, count);
        for (int i = 0; i < count; i++) x[i] = Math.max(x[i], 0);
        double[] sorted = x.clone(); Arrays.sort(sorted);
        double median = (count & 1) == 1 ? sorted[count / 2]
            : (sorted[count / 2 - 1] / 2 + sorted[count / 2] / 2);
        if (median == 0) {
            warnings.add("More than half of residual variances are exactly zero: eBayes unreliable");
            median = 1;
        } else if (Arrays.stream(x).anyMatch(v -> v == 0)) {
            warnings.add("Zero sample variances detected, have been offset away from zero");
        }
        double floor = 1e-5 * median; int offset = 0;
        double[] e = new double[count], tri = new double[count];
        for (int i = 0; i < count; i++) {
            if (x[i] < floor) { x[i] = floor; offset++; }
            e[i] = Math.log(x[i]) + logMinusDigamma(df[i] / 2);
            tri[i] = PolyGamma.trigamma(df[i] / 2);
        }
        double average = mean(e); double[] squared = new double[count];
        for (int i = 0; i < count; i++) squared[i] = (e[i] - average) * (e[i] - average);
        double excess = sum(squared) / (count - 1) - mean(tri);
        double d2 = Double.POSITIVE_INFINITY, scale;
        if (excess > 0) {
            d2 = 2 * inverseTrigamma(excess);
            scale = Math.exp(average - logMinusDigamma(d2 / 2));
        } else scale = mean(x);
        return prior(scale, d2, count, filtered, offset, warnings);
    }

    /**
     * Explicit squeezeVar(..., legacy=TRUE, robust=FALSE, covariate=NULL).
     * This method never substitutes legacy estimation for the modern default
     * on unequal df. Its name requires callers to select legacy explicitly.
     */
    public static Result squeezeLegacy(double[] variances, double[] degreesOfFreedom) {
        Objects.requireNonNull(variances, "variances"); Objects.requireNonNull(degreesOfFreedom, "df");
        int n = variances.length;
        if (n == 0) throw new IllegalArgumentException("var is empty");
        if (n < 3) return new Result(variances, 0, variances, n, 0, 0,
            Arrays.stream(variances).allMatch(v -> Double.isFinite(v) && v >= 0) ? List.of()
                : List.of("FAIL2: NO COMPLETE RESULT: original short-input return contains invalid variances"));
        shape(degreesOfFreedom, n, "df"); double[] x = variances.clone();
        if (degreesOfFreedom.length > 1)
            for (int i = 0; i < n; i++) if (degreesOfFreedom[i] == 0) x[i] = 0;
        Prior p = fitFDistribution(x, degreesOfFreedom);
        if (Double.isNaN(p.degreesOfFreedom())) throw new IllegalArgumentException("Could not estimate prior df");
        double[] result = posterior(x, degreesOfFreedom, new double[]{p.scale()}, new double[]{p.degreesOfFreedom()});
        List<String> warnings = new ArrayList<>(p.warnings());
        if (Arrays.stream(result).anyMatch(v -> !Double.isFinite(v) || v < 0))
            warnings.add("FAIL2: NO COMPLETE RESULT: original posterior contains nonfinite/negative variances");
        return new Result(new double[]{p.scale()}, p.degreesOfFreedom(), result,
            p.informative(), p.filtered(), p.offset(), warnings);
    }

    /**
     * Original .squeezeVar with supplied hyperparameters. Every argument has
     * length one or n. NA prior df are refused as required by original source;
     * other nonfinite arithmetic remains visible in the returned array.
     */
    public static double[] posterior(double[] variances, double[] df,
            double[] priorVariances, double[] priorDf) {
        Objects.requireNonNull(variances, "variances"); int n = variances.length;
        if (n == 0) throw new IllegalArgumentException("Require at least one variance");
        shape(df, n, "df"); shape(priorVariances, n, "prior variances"); shape(priorDf, n, "prior df");
        for (double d : priorDf) if (Double.isNaN(d)) throw new IllegalArgumentException("NA prior df not allowed");
        double maximum = Arrays.stream(priorDf).max().orElseThrow(); double[] out = new double[n];
        if (Double.isFinite(maximum)) {
            for (int i = 0; i < n; i++) out[i] = (at(df, i) * variances[i]
                + at(priorDf, i) * at(priorVariances, i)) / (at(df, i) + at(priorDf, i));
            return out;
        }
        double minimum = Arrays.stream(priorDf).min().orElseThrow();
        for (int i = 0; i < n; i++) out[i] = at(priorVariances, i);
        if (minimum > 1e100) return out;
        for (int i = 0; i < n; i++) if (Double.isFinite(at(priorDf, i)))
            out[i] = (at(df, i) * variances[i] + at(priorDf, i) * out[i]) / (at(df, i) + at(priorDf, i));
        return out;
    }

    /** Original statmod 1.5.2 logmdigamma for real input, including its branches. */
    public static double logMinusDigamma(double z) {
        if (Double.isNaN(z) || z <= 0) return Double.NaN;
        if (z < 5) return Math.log(z / (z + 5)) + logMinusDigamma(z + 5)
            + 1 / z + 1 / (z + 1) + 1 / (z + 2) + 1 / (z + 3) + 1 / (z + 4);
        double x = 1 / (z * z);
        double tail = x * (-1.0 / 12 + x * (1.0 / 120 + x * (-1.0 / 252
            + x * (1.0 / 240 + x * (-1.0 / 132 + x * (691.0 / 32760
            + x * (-1.0 / 12 + 3617 * x / 8160)))))));
        return 1 / (2 * z) - tail;
    }

    /** Original scalar trigammaInverse; nonconvergence is an explicit failure. */
    public static double inverseTrigamma(double x) {
        if (Double.isNaN(x) || x < 0) return Double.NaN;
        if (x > 1e7) return 1 / Math.sqrt(x);
        if (x < 1e-6) return 1 / x;
        double y = .5 + 1 / x;
        for (int iteration = 1; iteration <= 51; iteration++) {
            double tri = PolyGamma.trigamma(y);
            double difference = tri * (1 - tri / x) / PolyGamma.tetragamma(y);
            y += difference;
            if (-difference / y < 1e-8) return y;
        }
        throw new ArithmeticException("FAIL2: NO COMPLETE RESULT: trigammaInverse iteration limit exceeded");
    }

    private static Prior prior(double scale, double df, int n, int filtered, int offset, List<String> warnings) {
        if (!Double.isFinite(scale) || scale < 0 || Double.isNaN(df) || df < 0)
            warnings.add("FAIL2: NO COMPLETE RESULT: original prior has invalid scale or df");
        return new Prior(scale, df, n, filtered, offset, warnings);
    }
    private static void shape(double[] x, int n, String name) {
        Objects.requireNonNull(x, name);
        if (x.length != 1 && x.length != n)
            throw new IllegalArgumentException(name + " must have length one or number of variances; partial recycling is refused");
    }
    private static double at(double[] x, int i) { return x.length == 1 ? x[0] : x[i]; }
    private static double sum(double[] values) {
        double total = 0, correction = 0;
        for (double v : values) {
            double next = total + v;
            correction += Math.abs(total) >= Math.abs(v) ? (total - next) + v : (v - next) + total;
            total = next;
        }
        return total + correction;
    }
    private static double mean(double[] values) {
        double[] scaled = new double[values.length];
        for (int i = 0; i < values.length; i++) scaled[i] = values[i] / values.length;
        return sum(scaled);
    }
}
