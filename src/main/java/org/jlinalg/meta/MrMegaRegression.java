/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import jdistlib.ChiSquare;

/**
 * Native weighted regression statistics used by MR-MEGA.
 *
 * <p>This is an independently implemented mathematical kernel, not the complete
 * MR-MEGA file workflow. It consumes explicit, already aligned cohort effects,
 * standard errors and ancestry coordinates, in the supplied order. It performs
 * no allele, frequency, chromosome, study-selection, MDS or genomic-control
 * inference. In particular, covariance is scaled by residual sum of squares
 * divided by residual degrees of freedom, as in the original MR-MEGA regression;
 * ordinary known-variance fixed-effect meta-regression is not a replacement.
 *
 * <p>P-values here use stable chi-square survival functions. They are not the
 * original implementation's long-double subtraction approximation. Original
 * reference values must remain separately labeled, particularly for very small
 * probabilities. A zero test statistic retains the original undefined-test
 * convention (NaN), rather than silently supplying a probability of one.
 * Singular/nonfinite inputs are refused; no source-undefined result is repaired.
 */
public final class MrMegaRegression {
    private MrMegaRegression() { }

    /** Complete full-precision regression values; arrays are defensive copies. */
    public record Result(int cohorts, int ancestryAxes, double[] coefficients,
            double[] standardErrors, double[][] covariance, double[] fitted,
            double[] residuals, double weightedMean, double centeredTotalSquares,
            double zeroTotalSquares, double residualSquares, double residualScale,
            double associationChiSquare, double ancestryChiSquare,
            double residualChiSquare, int associationDegrees, int ancestryDegrees,
            int residualDegrees, double stableAssociationP,
            double stableAncestryP, double stableResidualP, double logBayesFactor) {
        public Result {
            coefficients = coefficients.clone(); standardErrors = standardErrors.clone();
            covariance = copy(covariance); fitted = fitted.clone(); residuals = residuals.clone();
        }
        @Override public double[] coefficients() { return coefficients.clone(); }
        @Override public double[] standardErrors() { return standardErrors.clone(); }
        @Override public double[][] covariance() { return copy(covariance); }
        @Override public double[] fitted() { return fitted.clone(); }
        @Override public double[] residuals() { return residuals.clone(); }
    }

    /**
     * Fits an intercept plus all supplied ancestry columns. Effects/SEs must
     * already reflect the desired source input precision and any explicitly
     * requested upstream genomic control. Inputs are never mutated or reordered.
     */
    public static Result fit(double[] effects, double[] standardErrors, double[][] ancestry) {
        require(effects != null && standardErrors != null && ancestry != null,
            "Effects, standard errors and explicit ancestry rows are required");
        int n = effects.length;
        require(n > 0 && standardErrors.length == n && ancestry.length == n,
            "Cohort populations must match exactly");
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            require(Double.isFinite(standardErrors[i]) && standardErrors[i] > 0,
                "Standard errors must be finite and strictly positive");
            weights[i] = Math.pow(1.0 / standardErrors[i], 2);
        }
        return fitWeighted(effects, weights, ancestry);
    }

    /**
     * Fits supplied positive weights directly, matching the original regression
     * kernel's input boundary. This also supports explicitly prepared original
     * file-workflow weights without pretending that its float32 storage and
     * reciprocal arithmetic are equivalent to double-precision SE conversion.
     */
    public static Result fitWeighted(double[] effects, double[] weights, double[][] ancestry) {
        require(effects != null && weights != null && ancestry != null,
            "Effects, weights and explicit ancestry rows are required");
        int n = effects.length;
        require(n > 0 && weights.length == n && ancestry.length == n,
            "Cohort populations must match exactly");
        require(ancestry[0] != null, "Ancestry rows must be explicit, including zero-column rows");
        int axes = ancestry[0].length, k = axes + 1;
        require(n >= axes + 3, "MR-MEGA admission needs at least ancestry-axis count plus three cohorts; retain a SmallCohortCount disposition in the calling workflow");
        require(axes <= 1024 && n <= 100000 && (long)n * k <= 20000000,
            "Explicit regression capacity exceeded");
        double[][] x = new double[n][k]; double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            require(Double.isFinite(effects[i]), "Effects must be finite; no cohort is dropped");
            require(ancestry[i] != null && ancestry[i].length == axes, "Ancestry rows must have identical widths");
            x[i][0] = 1;
            for (int j = 0; j < axes; j++) {
                require(Double.isFinite(ancestry[i][j]), "Ancestry values must be finite"); x[i][j + 1] = ancestry[i][j];
            }
            w[i] = weights[i];
            require(Double.isFinite(w[i]) && w[i] > 0, "Reciprocal-variance weight overflow or underflow");
        }
        double[][] information = new double[k][k]; double[] rhs = new double[k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++)
                for (int row = 0; row < n; row++) information[i][j] += w[row] * x[row][i] * x[row][j];
            for (int row = 0; row < n; row++) rhs[i] += w[row] * x[row][i] * effects[row];
        }
        double[][] factor = cholesky(information); double[] beta = solve(factor, rhs);
        double[][] inverse = new double[k][k];
        for (int col = 0; col < k; col++) {
            double[] unit = new double[k]; unit[col] = 1; double[] solved = solve(factor, unit);
            for (int row = 0; row < k; row++) inverse[row][col] = solved[row];
        }
        double weightedSum = 0, weightSum = 0;
        for (int row = 0; row < n; row++) { weightedSum += w[row] * effects[row]; weightSum += w[row]; }
        double mean = weightedSum / weightSum, tss = 0, tss0 = 0, rss = 0;
        double[] fitted = new double[n], residuals = new double[n];
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < k; col++) fitted[row] += beta[col] * x[row][col];
            residuals[row] = fitted[row] - effects[row]; double centered = effects[row] - mean;
            tss += w[row] * centered * centered; tss0 += w[row] * effects[row] * effects[row];
            rss += w[row] * residuals[row] * residuals[row];
        }
        int residualDf = n - k; double scale = rss / residualDf; double[] errors = new double[k];
        double[][] covariance = new double[k][k];
        for (int row = 0; row < k; row++) {
            for (int col = 0; col < k; col++) covariance[row][col] = inverse[row][col] * scale;
            errors[row] = Math.sqrt(covariance[row][row]);
        }
        double association = Math.abs(tss0 - rss), ancestryQ = Math.abs(tss - rss), residualQ = Math.abs(rss);
        for (double value : new double[]{mean,tss,tss0,rss,scale,association,ancestryQ,residualQ})
            require(Double.isFinite(value), "Regression arithmetic exceeded finite capacity; no partial result admitted");
        for (int i = 0; i < k; i++) require(Double.isFinite(beta[i]) && Double.isFinite(errors[i]), "Coefficient or covariance arithmetic exceeded finite capacity");
        return new Result(n, axes, beta, errors, covariance, fitted, residuals, mean, tss, tss0, rss, scale,
            association, ancestryQ, residualQ, k, axes, residualDf, stableP(association, k), stableP(ancestryQ, axes),
            stableP(residualQ, residualDf), (association - k * Math.log(n)) / 2);
    }

    private static double stableP(double statistic, int degrees) {
        return statistic > 0 && degrees > 0 ? ChiSquare.cumulative(statistic, degrees, false, false) : Double.NaN;
    }

    private static double[][] cholesky(double[][] a) {
        int n = a.length; double[][] l = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++) {
            double value = a[i][j]; for (int h = 0; h < j; h++) value -= l[i][h] * l[j][h];
            if (i == j) {
                double resolution = 64.0 * n * Math.ulp(Math.abs(a[i][i]));
                require(Double.isFinite(value) && value > resolution,
                    "Singular weighted ancestry design or insufficient numerical pivot resolution; original unchecked inversion is not repaired");
                l[i][j] = Math.sqrt(value);
            } else { l[i][j] = value / l[j][j]; require(Double.isFinite(l[i][j]), "Weighted factor overflow"); }
        }
        return l;
    }

    private static double[] solve(double[][] l, double[] rhs) {
        int n = rhs.length; double[] z = rhs.clone();
        for (int i = 0; i < n; i++) { for (int j = 0; j < i; j++) z[i] -= l[i][j] * z[j]; z[i] /= l[i][i]; }
        for (int i = n - 1; i >= 0; i--) { for (int j = i + 1; j < n; j++) z[i] -= l[j][i] * z[j]; z[i] /= l[i][i]; }
        return z;
    }

    private static double[][] copy(double[][] values) {
        double[][] result = new double[values.length][];
        for (int i = 0; i < values.length; i++) result[i] = values[i].clone(); return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
