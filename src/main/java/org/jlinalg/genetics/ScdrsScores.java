/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import jdistlib.Normal;

/**
 * Native numerical components of scDRS 1.0.0--1.0.2, attributed to Zhang and Hou
 * (MIT), scdrs/method.py, commit 7f8f8d5754e1e3057916cd3fbe720b96f90c1c1b.
 * These methods consume explicitly aligned, finite inputs. They do not select
 * matched control genes, fit LOESS, regress covariates, read H5AD, or construct
 * a neighbor graph. Those steps require the separately tested upstream workflow.
 * No nonfinite result is admitted, and no variance floor is added beyond the
 * original raw-score formula's explicit 0.01 gene-variance term.
 */
public final class ScdrsScores {
    private ScdrsScores() { }

    public enum Weighting { UNIFORM, VS, INV_STD }

    /** Logical work-array limits, not an operating-system memory quota. */
    public record Limits(int cells, int controls, long workingBytes) {
        public Limits {
            if (cells < 2 || cells > 1_000_000 || controls < 2 || controls > 10_000 || workingBytes < 1)
                throw new IllegalArgumentException("Require 2..1000000 cells, 2..10000 controls and positive workingBytes");
        }
    }

    public record RawScore(double[] scores, double[] normalizedWeights, double independentVariance) {
        public RawScore { scores = scores.clone(); normalizedWeights = normalizedWeights.clone(); }
        @Override public double[] scores() { return scores.clone(); }
        @Override public double[] normalizedWeights() { return normalizedWeights.clone(); }
    }

    /** Double-precision intermediates before upstream's float32 table conversion. */
    public record Background(double[] trait, double[][] controls) {
        public Background { trait = trait.clone(); controls = copy(controls); }
        @Override public double[] trait() { return trait.clone(); }
        @Override public double[][] controls() { return copy(controls); }
    }

    /** Source-compatible float32 columns, with literal cell order retained. */
    public record CellScores(List<String> cells, List<String> columns, float[][] values) {
        public CellScores { cells = List.copyOf(cells); columns = List.copyOf(columns); values = copy(values); }
        @Override public float[][] values() { return copy(values); }
    }

    public record MonteCarlo(double statistic, double p, double z) { }

    /**
     * Weighted raw score on an already normalized and covariate-corrected matrix.
     * Gene slots and weights are explicit and used in their supplied order;
     * duplicate slots are retained, as in upstream _compute_raw_score.
     * var and varTech describe those slots, rather than all matrix columns.
     */
    public static RawScore rawScore(double[][] expression, int[] geneSlots, double[] geneWeights,
                                     double[] variance, double[] technicalVariance, Weighting weighting,
                                     long workingBytes) {
        Objects.requireNonNull(weighting); Objects.requireNonNull(expression);
        int n = expression.length, g = Objects.requireNonNull(geneSlots).length;
        if (n < 2 || n > 1_000_000 || g < 1 || g > 100_000 || workingBytes < 1)
            throw new IllegalArgumentException("Declare finite nonempty bounded expression inputs");
        if (16L * n + 32L * g > workingBytes)
            throw new IllegalArgumentException("Raw-score logical work capacity exceeded");
        vector(geneWeights, g, "gene weights"); vector(variance, g, "gene variances");
        vector(technicalVariance, g, "technical variances");
        int width = Objects.requireNonNull(expression[0]).length;
        double[] weights = new double[g];
        for (int j = 0; j < g; j++) {
            if (geneSlots[j] < 0 || geneSlots[j] >= width || variance[j] < 0 || technicalVariance[j] < 0)
                throw new IllegalArgumentException("Invalid gene slot or negative variance");
            double base = switch (weighting) {
                case UNIFORM -> 1;
                case VS -> 1 / Math.sqrt(technicalVariance[j] + 1e-2);
                case INV_STD -> 1 / Math.sqrt(variance[j] + 1e-2);
            };
            weights[j] = finite(base * geneWeights[j], "raw-score weight");
        }
        double total = sum(weights);
        if (total == 0 || !Double.isFinite(total))
            throw new ArithmeticException("Unassessable weight sum; no result or repair");
        double independentVariance = 0;
        for (int j = 0; j < g; j++) {
            weights[j] = finite(weights[j] / total, "normalized weight");
            independentVariance += variance[j] * weights[j] * weights[j];
        }
        finite(independentVariance, "independent variance");
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            if (expression[i] == null || expression[i].length != width)
                throw new IllegalArgumentException("Ragged expression matrix");
            double value = 0;
            for (int j = 0; j < g; j++) value += finite(expression[i][geneSlots[j]], "selected expression") * weights[j];
            scores[i] = finite(value, "raw score");
        }
        return new RawScore(scores, weights, independentVariance);
    }

    /**
     * Original two gene-set alignments and intervening cell-wise population-SD
     * standardization. Exact zero raw scores receive upstream's final minimum
     * assignments: trait=min-0.001, controls=min. Negative raw values stay intact.
     */
    public static Background correctBackground(double[] raw, double[][] controls, double[] varianceRatios,
                                                 Limits limits) {
        int[] shape = shape(raw, controls, limits);
        int n = shape[0], k = shape[1]; vector(varianceRatios, k, "variance ratios");
        double[] scale = new double[k], colMean = new double[k];
        for (int j = 0; j < k; j++) {
            if (!(varianceRatios[j] > 0)) throw new ArithmeticException("Control variance ratio must be positive");
            scale[j] = Math.sqrt(varianceRatios[j]);
            // NumPy's strided axis=0 reduction traverses rows in order.
            for (int i = 0; i < n; i++) colMean[j] += controls[i][j];
            colMean[j] = finite(colMean[j] / n, "control mean");
        }
        double mean = mean(raw);
        double[] trait = new double[n]; double[][] corrected = new double[n][k];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) corrected[i][j] = finite((controls[i][j] - colMean[j]) / scale[j], "first alignment");
            double cellMean = mean(corrected[i]), sd = populationSd(corrected[i], cellMean);
            if (!(sd > 0)) throw new ArithmeticException("Zero cell-wise control SD; no result or repair");
            trait[i] = finite((raw[i] - mean - cellMean) / sd, "cell-wise trait standardization");
            for (int j = 0; j < k; j++) corrected[i][j] = finite((corrected[i][j] - cellMean) / sd, "cell-wise control standardization");
        }
        mean = mean(trait); Arrays.fill(colMean, 0);
        for (int j = 0; j < k; j++) {
            for (int i = 0; i < n; i++) colMean[j] += corrected[i][j];
            colMean[j] = finite(colMean[j] / n, "second control mean");
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            trait[i] = finite(trait[i] - mean, "second trait alignment"); minimum = Math.min(minimum, trait[i]);
            for (int j = 0; j < k; j++) {
                corrected[i][j] = finite(corrected[i][j] - colMean[j], "second control alignment");
                minimum = Math.min(minimum, corrected[i][j]);
            }
        }
        for (int i = 0; i < n; i++) {
            if (raw[i] == 0) trait[i] = finite(minimum - 1e-3, "zero trait score assignment");
            for (int j = 0; j < k; j++) if (controls[i][j] == 0) corrected[i][j] = minimum;
        }
        return new Background(trait, corrected);
    }

    /** Normalize and produce six source columns plus all normalized controls. */
    public static CellScores score(List<String> cells, double[] raw, double[][] controls,
                                    double[] varianceRatios, Limits limits) {
        int[] size = shape(raw, controls, limits);
        identities(cells, size[0]);
        Background background = correctBackground(raw, controls, varianceRatios, limits);
        double[] trait = background.trait; double[][] ctrl = background.controls;
        double[] pool = new double[size[0] * size[1]];
        for (int i = 0; i < size[0]; i++) System.arraycopy(ctrl[i], 0, pool, i * size[1], size[1]);
        double[] p = empiricalP(trait, pool);
        List<String> names = new java.util.ArrayList<>(List.of("raw_score", "norm_score", "mc_pval", "pval", "nlog10_pval", "zscore"));
        for (int j = 0; j < size[1]; j++) names.add("ctrl_norm_score_" + j);
        float[][] result = new float[size[0]][6 + size[1]];
        for (int i = 0; i < size[0]; i++) {
            int count = 0;
            for (int j = 0; j < size[1]; j++) if (ctrl[i][j] >= trait[i]) count++;
            double z = -Normal.quantile(p[i], 0, 1, true, false);
            z = Math.max(-10, Math.min(10, z)); // Explicit upstream clipping, including p=1.
            double[] values = {raw[i], trait[i], (count + 1.0) / (size[1] + 1.0), p[i], -Math.log10(p[i]), z};
            for (int j = 0; j < 6; j++) result[i][j] = float32(values[j]);
            for (int j = 0; j < size[1]; j++) result[i][6 + j] = float32(ctrl[i][j]);
        }
        return new CellScores(cells, names, result);
    }

    /** Inclusive upper-tail empirical P=(1+number(null>=observed))/(1+N). */
    public static double[] empiricalP(double[] observed, double[] nullScores) {
        vector(observed, Objects.requireNonNull(observed).length, "observed scores");
        vector(nullScores, Objects.requireNonNull(nullScores).length, "null scores");
        if (nullScores.length == 0 || nullScores.length > 50_000_000 || observed.length > 1_000_000)
            throw new IllegalArgumentException("Nonempty bounded null distribution required");
        double[] sorted = nullScores.clone(); Arrays.sort(sorted); double[] result = new double[observed.length];
        for (int i = 0; i < observed.length; i++) {
            int low = 0, high = sorted.length;
            while (low < high) { int mid = (low + high) >>> 1; if (sorted[mid] < observed[i]) low = mid + 1; else high = mid; }
            result[i] = (sorted.length - low + 1.0) / (sorted.length + 1.0);
        }
        return result;
    }

    /** Original group association: 95th percentile, inclusive control tail, population control SD. */
    public static MonteCarlo groupAssociation(double[] trait, double[][] controls, Limits limits) {
        int[] size = shape(trait, controls, limits);
        double value = quantile95(trait), stats[] = new double[size[1]], column[] = new double[size[0]];
        for (int j = 0; j < size[1]; j++) {
            for (int i = 0; i < size[0]; i++) column[i] = controls[i][j];
            stats[j] = quantile95(column);
        }
        return monteCarlo(value, stats);
    }

    /** Original downstream correlation test: signed Pearson correlation and inclusive upper tail. */
    public static MonteCarlo correlation(double[] annotation, double[] trait, double[][] controls, Limits limits) {
        int[] size = shape(trait, controls, limits); vector(annotation, size[0], "annotation");
        double value = pearson(annotation, trait), stats[] = new double[size[1]], column[] = new double[size[0]];
        for (int j = 0; j < size[1]; j++) {
            for (int i = 0; i < size[0]; i++) column[i] = controls[i][j];
            stats[j] = pearson(annotation, column);
        }
        return monteCarlo(value, stats);
    }

    /** BH adjusted P for exactly one explicitly supplied correction family. */
    public static double[] benjaminiHochberg(double[] p) {
        vector(p, Objects.requireNonNull(p).length, "P values");
        if (p.length < 1 || p.length > 1_000_000) throw new IllegalArgumentException("Bounded nonempty correction family required");
        Integer[] order = new Integer[p.length];
        for (int i = 0; i < p.length; i++) { if (p[i] < 0 || p[i] > 1) throw new IllegalArgumentException("P outside [0,1]"); order[i] = i; }
        Arrays.sort(order, (a, b) -> Double.compare(p[a], p[b]));
        double[] result = new double[p.length]; double previous = 1;
        for (int i = p.length - 1; i >= 0; i--) { previous = Math.min(previous, p[order[i]] * p.length / (i + 1.0)); result[order[i]] = previous; }
        return result;
    }

    private static MonteCarlo monteCarlo(double value, double[] controls) {
        double mean = mean(controls), sd = populationSd(controls, mean);
        if (!(sd > 0)) throw new ArithmeticException("Zero control-statistic SD; no complete result");
        int count = 0; for (double v : controls) if (v >= value) count++;
        return new MonteCarlo(value, (count + 1.0) / (controls.length + 1.0), finite((value - mean) / sd, "Monte Carlo Z"));
    }

    private static double quantile95(double[] x) {
        double[] sorted = x.clone(); Arrays.sort(sorted);
        double index = (sorted.length - 1) * 0.95; int low = (int) index, high = Math.min(low + 1, sorted.length - 1);
        double fraction = index - low, difference = sorted[high] - sorted[low];
        // NumPy _lerp selects the numerically stable endpoint at fraction>=0.5.
        return finite(fraction >= 0.5 ? sorted[high] - difference * (1 - fraction) : sorted[low] + difference * fraction, "quantile");
    }

    private static double pearson(double[] x, double[] y) {
        double xm = mean(x), ym = mean(y), xx = 0, yy = 0, xy = 0;
        for (int i = 0; i < x.length; i++) { double a = x[i] - xm, b = y[i] - ym; xx += a * a; yy += b * b; xy += a * b; }
        if (!(xx > 0) || !(yy > 0)) throw new ArithmeticException("Constant correlation input; no result");
        return finite(Math.max(-1, Math.min(1, (xy / Math.sqrt(xx)) / Math.sqrt(yy))), "Pearson correlation");
    }

    private static int[] shape(double[] raw, double[][] controls, Limits limits) {
        Objects.requireNonNull(limits); Objects.requireNonNull(raw); Objects.requireNonNull(controls);
        int n = raw.length;
        if (n < 2 || n > limits.cells || controls.length != n || controls[0] == null)
            throw new IllegalArgumentException("Cell count exceeds declared capacity or matrix is unaligned");
        int k = controls[0].length;
        long elements = (long) n * k;
        if (k < 2 || k > limits.controls || elements > 50_000_000 || 64L * elements + 256L * n + 64L * k > limits.workingBytes)
            throw new IllegalArgumentException("Control count or logical work-array capacity exceeded");
        vector(raw, n, "trait scores");
        for (double[] row : controls) vector(row, k, "control scores");
        return new int[]{n, k};
    }

    private static void identities(List<String> cells, int n) {
        Objects.requireNonNull(cells);
        if (cells.size() != n) throw new IllegalArgumentException("Cell identities unaligned");
        HashSet<String> seen = new HashSet<>();
        for (String cell : cells) if (cell == null || cell.isEmpty() || !seen.add(cell))
            throw new IllegalArgumentException("Unique literal nonempty cell identities required");
    }
    private static void vector(double[] x, int n, String name) {
        if (x == null || x.length != n) throw new IllegalArgumentException(name + " shape mismatch");
        for (double v : x) finite(v, name);
    }
    private static double finite(double x, String name) {
        if (!Double.isFinite(x)) throw new ArithmeticException("Nonfinite " + name + "; no result or repair");
        return x;
    }
    private static float float32(double x) { float value = (float) finite(x, "output"); if (!Float.isFinite(value)) throw new ArithmeticException("Unrepresentable float32 output"); return value; }
    private static double mean(double[] x) { return finite(sum(x) / x.length, "mean"); }
    private static double populationSd(double[] x, double mean) {
        double[] squared = new double[x.length]; for (int i = 0; i < x.length; i++) { double v = x[i] - mean; squared[i] = v * v; }
        return finite(Math.sqrt(sum(squared) / x.length), "population standard deviation");
    }
    // NumPy 1.23's contiguous float64 pairwise reduction (block size128).
    private static double sum(double[] x) { return sum(x, 0, x.length); }
    private static double sum(double[] x, int start, int n) {
        if (n < 8) { double value = -0.0; for (int i = 0; i < n; i++) value += x[start + i]; return value; }
        if (n <= 128) {
            double a = x[start], b = x[start + 1], c = x[start + 2], d = x[start + 3];
            double e = x[start + 4], f = x[start + 5], g = x[start + 6], h = x[start + 7];
            int i = 8;
            for (; i < n - n % 8; i += 8) { a += x[start + i]; b += x[start + i + 1]; c += x[start + i + 2]; d += x[start + i + 3]; e += x[start + i + 4]; f += x[start + i + 5]; g += x[start + i + 6]; h += x[start + i + 7]; }
            double value = ((a + b) + (c + d)) + ((e + f) + (g + h));
            for (; i < n; i++) value += x[start + i]; return value;
        }
        int half = n / 2; half -= half % 8; return sum(x, start, half) + sum(x, start + half, n - half);
    }
    private static double[][] copy(double[][] x) { double[][] y = new double[x.length][]; for (int i = 0; i < x.length; i++) y[i] = x[i].clone(); return y; }
    private static float[][] copy(float[][] x) { float[][] y = new float[x.length][]; for (int i = 0; i < x.length; i++) y[i] = x[i].clone(); return y; }
}
