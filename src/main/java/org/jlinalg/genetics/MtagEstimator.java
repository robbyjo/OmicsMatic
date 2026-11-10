/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import jdistlib.Normal;

/**
 * Native finite-input MTAG1.0.8 estimator on explicitly aligned Z/N matrices.
 * Formula attribution: Maghzian, Walters and Turley, original GPL3 MTAG,
 * commit9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc, functions
 * {@code mtag_analysis} and {@code gmm_omega}. JDistlib supplies normal tails.
 *
 * <p>This component requires caller-supplied, symmetric positive-definite
 * genetic and residual covariance matrices. It uses Cholesky solves rather
 * than explicit NumPy inverses. Singular/perfect-correlation matrices and
 * unassessable arithmetic are refused without jitter, clipping or repair.
 * Results are in the standardized scale used inside the original estimator;
 * allele-frequency rescaling, harmonization, QC, LDSC, covariance adjustment,
 * maximum-FDR calculation and complete MTAG execution remain separate.</p>
 */
public final class MtagEstimator {
    private MtagEstimator() { }
    public static final int MAX_TRAITS = 32;
    public static final int MAX_VARIANTS = 1_000_000;

    /** Logical capacities, checked before allocating estimator work arrays. */
    public record Limits(int variants, int traits, long workingBytes) {
        public Limits {
            if (variants < 1 || variants > MAX_VARIANTS || traits < 1 || traits > MAX_TRAITS || workingBytes < 1)
                throw new IllegalArgumentException("Declare positive capacities within1000000 variants and32 traits");
        }
    }

    /** Complete ordered results; all arrays and identity lists are defensive. */
    public record Result(List<String> variants, List<String> traits, double[][] standardizedBeta,
                         double[][] standardizedSe, double[][] factor, double[][] z, double[][] p) {
        public Result {
            variants = List.copyOf(variants); traits = List.copyOf(traits);
            standardizedBeta = copy(standardizedBeta); standardizedSe = copy(standardizedSe);
            factor = copy(factor); z = copy(z); p = copy(p);
        }
        @Override public double[][] standardizedBeta() { return copy(standardizedBeta); }
        @Override public double[][] standardizedSe() { return copy(standardizedSe); }
        @Override public double[][] factor() { return copy(factor); }
        @Override public double[][] z() { return copy(z); }
        @Override public double[][] p() { return copy(p); }
    }

    /** Estimate all supplied variants, retaining their literal order and identities. */
    public static Result estimate(List<String> variants, List<String> traits, double[][] z, double[][] n,
                                  double[][] omega, double[][] sigma, Limits limits) {
        int[] shape = validate(variants, traits, z, n, sigma, limits);
        int m = shape[0], t = shape[1]; symmetric(omega, t, "Omega");
        cholesky(omega, "Omega"); cholesky(sigma, "Sigma");
        double[][] beta = new double[m][t], se = new double[m][t], factor = new double[m][t];
        double[][] outZ = new double[m][t], outP = new double[m][t];
        double[][] a = new double[t][t]; double[] y = new double[t], b = new double[t], invSqrtN = new double[t];
        for (int row = 0; row < m; row++) {
            for (int j = 0; j < t; j++) {
                invSqrtN[j] = finite(1 / Math.sqrt(n[row][j]), "reciprocal sample-size scale");
                b[j] = finite(z[row][j] * invSqrtN[j], "scaled input effect");
            }
            for (int target = 0; target < t; target++) {
                double tau = omega[target][target];
                for (int j = 0; j < t; j++) {
                    y[j] = finite(omega[j][target] / tau, "genetic-covariance ratio");
                    for (int k = 0; k < t; k++)
                        a[j][k] = finite(omega[j][k] - omega[j][target] * omega[k][target] / tau
                            + sigma[j][k] * invSqrtN[j] * invSqrtN[k], "conditional covariance");
                }
                double[] w = solve(cholesky(a, "conditional covariance"), y);
                double denominator = dot(y, w);
                if (!(denominator > 0) || !Double.isFinite(denominator))
                    throw new ArithmeticException("Unassessable MTAG denominator; no result or repair");
                double estimate = dot(w, b) / denominator;
                double error = Math.sqrt(1 / denominator);
                if (!(error > 0) || !Double.isFinite(error))
                    throw new ArithmeticException("Unassessable MTAG standard error; no result or repair");
                double sum = 0;
                for (double value : w) sum += value;
                beta[row][target] = finite(estimate, "MTAG standardized beta"); se[row][target] = error;
                factor[row][target] = finite(sum / denominator, "MTAG factor");
                outZ[row][target] = finite(estimate / error, "MTAG Z");
                double probability = 2 * Normal.cumulative(-Math.abs(outZ[row][target]), 0, 1, true, false);
                if (!Double.isFinite(probability) || probability < 0 || probability > 1)
                    throw new ArithmeticException("Unassessable normal tail; no clipping or repair");
                outP[row][target] = probability;
            }
        }
        return new Result(variants, traits, beta, se, factor, outZ, outP);
    }

    /**
     * Original unadjusted GMM Omega: mean((Z_i Z_j - Sigma_ij)/sqrt(N_i N_j)).
     * All provided rows participate. Caller must explicitly exclude any strand
     * ambiguous or otherwise ineligible variants beforehand. Negative entries
     * remain unchanged; this method performs no positive-definiteness adjustment.
     */
    public static double[][] unadjustedGmmOmega(List<String> variants, List<String> traits, double[][] z,
                                               double[][] n, double[][] sigma, Limits limits) {
        int[] shape = validate(variants, traits, z, n, sigma, limits);
        int m = shape[0], t = shape[1]; cholesky(sigma, "Sigma");
        double[][] omega = new double[t][t];
        for (int j = 0; j < t; j++) for (int k = 0; k <= j; k++) {
            double sum = 0;
            for (int row = 0; row < m; row++) {
                double scale = Math.sqrt(finite(n[row][j] * n[row][k], "GMM sample-size product"));
                if (!(scale > 0)) throw new ArithmeticException("GMM sample-size scale underflow; no repair");
                double term = finite((z[row][j] * z[row][k] - sigma[j][k]) / scale, "GMM term");
                sum = finite(sum + term, "GMM accumulation");
            }
            omega[j][k] = omega[k][j] = finite(sum / m, "unadjusted GMM Omega");
        }
        return omega;
    }

    private static int[] validate(List<String> variants, List<String> traits, double[][] z, double[][] n,
                                   double[][] sigma, Limits limits) {
        Objects.requireNonNull(limits, "Explicit capacities");
        Objects.requireNonNull(variants, "Explicit variant IDs"); Objects.requireNonNull(traits, "Explicit trait IDs");
        int m = variants.size(), t = traits.size();
        if (m < 1 || t < 1 || m > limits.variants() || t > limits.traits()
                || 65536L + 512L * m + 128L * m * t + 256L * t * t > limits.workingBytes())
            throw new IllegalArgumentException("Complete MTAG logical capacity exceeded before estimator allocation");
        identities(variants); identities(traits); matrix(z, m, t, "Z"); matrix(n, m, t, "N");
        for (double[] row : n) for (double value : row) if (!(value > 0))
            throw new IllegalArgumentException("Every original effective sample size must be positive; no imputation");
        symmetric(sigma, t, "Sigma"); return new int[]{m, t};
    }

    private static void identities(List<String> ids) {
        var seen = new HashSet<String>();
        for (String id : ids) {
            if (id == null || id.isEmpty() || !seen.add(id) || id.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Nonempty unique literal IDs without control characters required; no repair");
            for (int j = 0; j < id.length(); j++) if (Character.isSurrogate(id.charAt(j))) {
                if (!Character.isHighSurrogate(id.charAt(j)) || j + 1 == id.length() || !Character.isLowSurrogate(id.charAt(++j)))
                    throw new IllegalArgumentException("Invalid original Unicode identity; no repair");
            }
        }
    }
    private static void matrix(double[][] x, int rows, int cols, String name) {
        if (x == null || x.length != rows) throw new IllegalArgumentException(name + " row count differs");
        for (double[] row : x) {
            if (row == null || row.length != cols) throw new IllegalArgumentException(name + " complete dimensions differ");
            for (double value : row) if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " contains nonfinite original data; no repair");
        }
    }
    private static void symmetric(double[][] x, int size, String name) {
        matrix(x, size, size, name);
        for (int i = 0; i < size; i++) for (int j = 0; j < i; j++) if (x[i][j] != x[j][i])
            throw new IllegalArgumentException(name + " must be explicitly symmetric; no averaging");
    }
    private static double[][] cholesky(double[][] a, String name) {
        int size = a.length; double[][] l = new double[size][size];
        for (int i = 0; i < size; i++) for (int j = 0; j <= i; j++) {
            double value = a[i][j];
            for (int k = 0; k < j; k++) value -= l[i][k] * l[j][k];
            if (i == j) {
                if (!(value > 0) || !Double.isFinite(value)) throw new ArithmeticException(name + " is outside the positive-definite native profile; no jitter or repair");
                l[i][j] = Math.sqrt(value);
            } else l[i][j] = finite(value / l[j][j], name + " Cholesky factor");
        }
        return l;
    }
    private static double[] solve(double[][] l, double[] y) {
        int size = y.length; double[] w = y.clone();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < i; j++) w[i] -= l[i][j] * w[j];
            w[i] = finite(w[i] / l[i][i], "forward solve");
        }
        for (int i = size - 1; i >= 0; i--) {
            for (int j = i + 1; j < size; j++) w[i] -= l[j][i] * w[j];
            w[i] = finite(w[i] / l[i][i], "backward solve");
        }
        return w;
    }
    private static double dot(double[] a, double[] b) {
        double result = 0;
        for (int i = 0; i < a.length; i++) result += a[i] * b[i];
        return finite(result, "dot product");
    }
    private static double finite(double x, String what) {
        if (!Double.isFinite(x)) throw new ArithmeticException("Unassessable " + what + "; no result or repair");
        return x;
    }
    private static double[][] copy(double[][] x) {
        double[][] result = new double[x.length][];
        for (int i = 0; i < x.length; i++) result[i] = x[i].clone();
        return result;
    }
}
