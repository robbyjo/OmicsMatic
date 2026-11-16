/*
 * Copyright (C) 2026 JLinAlg contributors
 * Equations follow FinucaneLab/pops, commit 76eb86cba10254490003c8f4dc7ff5ce492d3667.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.jlinalg.genetics;

import jdistlib.accelerator.ComputeBackend;

/** Numerical operations with PoPS's explicit, distinct projection conventions. */
final class PopsMath {
    private PopsMath() { }

    static double[][] copy(double[][] x, int rows, String name) {
        if (x == null || x.length != rows || rows == 0 || x[0] == null)
            throw new IllegalArgumentException(name + ": nonempty rectangular matrix required");
        int p = x[0].length;
        double[][] result = new double[rows][p];
        for (int i = 0; i < rows; i++) {
            if (x[i] == null || x[i].length != p) throw new IllegalArgumentException(name + ": ragged matrix");
            for (int j = 0; j < p; j++) { finite(x[i][j], name); result[i][j] = x[i][j]; }
        }
        return result;
    }

    static void finite(double x, String name) {
        if (!Double.isFinite(x)) throw new IllegalArgumentException(name + ": finite prepared inputs required; no imputation is performed");
    }

    static double variance(double[] x) {
        double mean = 0, value = 0;
        for (double v : x) mean += v;
        mean /= x.length;
        for (double v : x) value += (v - mean) * (v - mean);
        return value / x.length;
    }

    static double[][] intercept(int n) {
        double[][] x = new double[n][1];
        for (double[] row : x) row[0] = 1;
        return x;
    }

    static double[][] append(double[][] x, double[] column) {
        double[][] out = new double[x.length][x[0].length + 1];
        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], 0, out[i], 0, x[i].length);
            out[i][x[i].length] = column[i];
        }
        return out;
    }

    static double[][] withInterceptIfNeeded(double[][] x) {
        for (int j = 0; j < x[0].length; j++) {
            double[] col = new double[x.length];
            for (int i = 0; i < x.length; i++) col[i] = x[i][j];
            // numpy.isclose(var, 0): relative tolerance contributes zero.
            if (Math.abs(variance(col)) <= 1e-8) return x;
        }
        double[] ones = new double[x.length]; java.util.Arrays.fill(ones, 1);
        return append(x, ones);
    }

    static int[] indices(boolean[] mask, int n, String name) {
        if (mask == null || mask.length != n) throw new IllegalArgumentException(name + ": explicit gene mask required");
        int count = 0;
        for (boolean value : mask) if (value) count++;
        if (count == 0) throw new IllegalArgumentException(name + ": no genes selected");
        int[] result = new int[count];
        for (int i = 0, k = 0; i < n; i++) if (mask[i]) result[k++] = i;
        return result;
    }

    static double[][] rows(double[][] x, int[] indices) {
        double[][] out = new double[indices.length][];
        for (int i = 0; i < indices.length; i++) out[i] = x[indices[i]].clone();
        return out;
    }

    static double[][] column(double[] y, int[] indices) {
        double[][] out = new double[indices.length][1];
        for (int i = 0; i < indices.length; i++) out[i][0] = y[indices[i]];
        return out;
    }

    static double[] flatten(double[][] x) {
        double[] out = new double[Math.multiplyExact(x.length, x[0].length)];
        for (int i = 0; i < x.length; i++) System.arraycopy(x[i], 0, out, i * x[i].length, x[i].length);
        return out;
    }

    /** The initial target regression uses scipy.linalg.lstsq's default eps cutoff. */
    static double[] minimumNorm(double[][] x, double[] y, ComputeBackend backend) {
        int n = x.length, p = x[0].length;
        var svd = backend.dgesvd(flatten(x), n, p);
        int k = svd.components();
        double[] s = svd.singularValues(), u = svd.leftSingularVectors(), vt = svd.rightSingularVectorsTransposed();
        double[] beta = new double[p];
        for (int j = 0; j < k; j++) if (s[j] > Math.ulp(1.0) * s[0]) {
            double value = 0;
            for (int i = 0; i < n; i++) value += u[i * k + j] * y[i];
            value /= s[j];
            for (int c = 0; c < p; c++) beta[c] += vt[j * p + c] * value;
        }
        for (double b : beta) finite(b, "minimum-norm coefficients");
        return beta;
    }

    /** Marginal/training projections use inverse(V'V), without a pseudo-inverse. */
    static double[][] project(double[][] m, double[][] v, ComputeBackend backend) {
        int n = m.length, q = v[0].length, p = m[0].length;
        double[] gram = new double[q * q], moments = new double[q * p];
        for (int i = 0; i < n; i++) for (int j = 0; j < q; j++) {
            for (int c = 0; c < q; c++) gram[j * q + c] += v[i][j] * v[i][c];
            for (int c = 0; c < p; c++) moments[j * p + c] += v[i][j] * m[i][c];
        }
        double[] beta = backend.dgetrf(gram, q).solve(moments, p);
        double[][] result = new double[n][p];
        for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) {
            double value = m[i][j];
            for (int c = 0; c < q; c++) value -= v[i][c] * beta[c * p + j];
            finite(value, "projected values"); result[i][j] = value;
        }
        return result;
    }

    record Ridge(double[] coefficients, double alpha, double bestScore, double[] scores) { }

    /** PoPS RidgeCV: 25 log-spaced penalties, no intercept, negative LOO MSE. */
    static Ridge ridge(double[][] x, double[] y, ComputeBackend backend) {
        int n = x.length, p = x[0].length;
        var svd = backend.dgesvd(flatten(x), n, p);
        int k = svd.components();
        double[] s = svd.singularValues(), u = svd.leftSingularVectors(), vt = svd.rightSingularVectorsTransposed();
        double[] uy = new double[k];
        for (int j = 0; j < k; j++) for (int i = 0; i < n; i++) uy[j] += u[i * k + j] * y[i];
        double[] scores = new double[25], best = null;
        double bestScore = Double.NEGATIVE_INFINITY, bestAlpha = Double.NaN;
        for (int a = 0; a < 25; a++) {
            double alpha = Math.pow(10, -2 + 0.5 * a), score = 0;
            for (int i = 0; i < n; i++) {
                double c = y[i] / alpha, diagonal = 1 / alpha;
                for (int j = 0; j < k; j++) {
                    double w = 1 / (s[j] * s[j] + alpha) - 1 / alpha, value = u[i * k + j];
                    c += value * w * uy[j]; diagonal += value * value * w;
                }
                if (!(diagonal > 0) || !Double.isFinite(diagonal))
                    throw new ArithmeticException("PoPS RidgeCV inverse diagonal is unresolved; no fitted model");
                double error = c / diagonal; score -= error * error / n;
            }
            finite(score, "PoPS RidgeCV score"); scores[a] = score;
            // sklearn uses strict improvement, so equal scores retain the first alpha.
            if (score > bestScore) {
                bestScore = score; bestAlpha = alpha; best = new double[p];
                for (int j = 0; j < k; j++) {
                    double value = s[j] * uy[j] / (s[j] * s[j] + alpha);
                    for (int c = 0; c < p; c++) best[c] += vt[j * p + c] * value;
                }
            }
        }
        for (double b : best) finite(b, "PoPS RidgeCV coefficients");
        return new Ridge(best, bestAlpha, bestScore, scores);
    }
}
