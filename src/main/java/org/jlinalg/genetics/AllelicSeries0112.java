/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Source-bound to insitro AllelicSeries 0.1.1.2 (BSD-3-Clause).
 * Original copyright holder: insitro, 2022. See tools/literature/allelicseries0112.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import jdistlib.Gamma;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.SingularValueDecomposition;

/**
 * Native summary-statistic burden methods from the unchanged 0.1.1.2 sources.
 * The complete original COAST/COASTSS namespace is a separate upstream runtime;
 * this class does not substitute a generic SKAT implementation for its SKAT-O.
 * These development methods require comparison against the original R reference
 * before catalog admission. The paper's literal edition is 1.1.2, not 0.1.1.2.
 */
public final class AllelicSeries0112 {
    private AllelicSeries0112() { }

    /** Returned effects, complete covariance/score, and the original scalar-zero edge. */
    public record BurdenResult(double pValue, double[] beta, double[] standardError,
            double[] score, double[][] covariance, int degreesOfFreedom,
            boolean sourceScalarZero, List<String> warnings) {
        public BurdenResult {
            beta = beta == null ? null : beta.clone();
            standardError = standardError == null ? null : standardError.clone();
            score = score == null ? null : score.clone();
            covariance = covariance == null ? null : copy(covariance);
            warnings = List.copyOf(warnings);
        }
        @Override public double[] beta() { return beta == null ? null : beta.clone(); }
        @Override public double[] standardError() {
            return standardError == null ? null : standardError.clone();
        }
        @Override public double[] score() { return score == null ? null : score.clone(); }
        @Override public double[][] covariance() {
            return covariance == null ? null : copy(covariance);
        }
    }

    /** The original GenomicControl scalar operation, including its early returns. */
    public static double genomicControl(double lambda, double pValue, double df) {
        if (lambda == 1.0 || Double.isNaN(pValue)) return pValue;
        if (Double.isNaN(lambda)) throw new IllegalArgumentException(
            "Original R if-condition is missing for NaN lambda and a nonmissing p-value");
        double chi2 = Gamma.quantile(pValue, df / 2.0, 2.0, false, false);
        return Gamma.cumulative(chi2 / lambda, df / 2.0, 2.0, false, false);
    }

    /** Exact-symmetry check and all eigenvalues strictly above tau, as isPD. */
    public static boolean isPositiveDefinite(double[][] matrix, boolean forceSymmetry,
            double tau) {
        double[][] x = square(matrix);
        if (forceSymmetry) {
            for (int i = 0; i < x.length; i++) {
                for (int j = 0; j <= i; j++) {
                    double value = 0.5 * (matrix[i][j] + matrix[j][i]);
                    x[i][j] = value;
                    x[j][i] = value;
                }
            }
        }
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < i; j++) if (x[i][j] != x[j][i]) return false;
        }
        finiteMatrix(x);
        double[] eigen = new EigenDecomposition(new Array2DRowRealMatrix(x, false))
            .getRealEigenvalues();
        for (double value : eigen) if (!(value > tau)) return false;
        return true;
    }

    /** BaselineSS: one coefficient per declared annotation category, df=categories. */
    public static BurdenResult baseline(int[] annotation, double[] beta, double[] se,
            double[][] ld, int categories) {
        return lowLevel(annotation, beta, se, ld, categories, null);
    }

    /** SumCountSS: weighted category score and its one-degree-of-freedom test. */
    public static BurdenResult sumCount(int[] annotation, double[] beta, double[] se,
            double[][] ld, double[] weights) {
        if (weights == null || weights.length == 0) throw new IllegalArgumentException(
            "Declare at least one annotation category weight");
        return lowLevel(annotation, beta, se, ld, weights.length, weights.clone());
    }

    /**
     * ASBTSS checked-input behavior with the source defaults supplied explicitly.
     * Method is exactly "none" or "sum". A zero in annotation shifts all labels by
     * one, with a retained warning. Missing LD assumes identity with a warning.
     * A non-PD matrix gets precisely one eps diagonal addition; it is not repaired
     * by repeated additions, nearest-PD projection, or covariance symmetrization.
     */
    public static BurdenResult burden(int[] annotation, double[] beta, double[] se,
            double[][] ld, double[] weights, String method, double eps, double lambda) {
        if (annotation == null || weights == null || weights.length == 0) {
            throw new IllegalArgumentException("Require annotations and category weights");
        }
        int[] anno = annotation.clone();
        List<String> warnings = new ArrayList<>();
        boolean zero = false;
        for (int value : anno) if (value == 0) zero = true;
        if (zero) {
            warnings.add("Zero is present in the annotation vector. Annotations will be reindexed to start at one.");
            for (int i = 0; i < anno.length; i++) anno[i] = Math.addExact(anno[i], 1);
        }
        int n = dimensions(anno, beta, se, weights.length);
        boolean[] present = new boolean[weights.length];
        for (int value : anno) present[value - 1] = true;
        for (boolean value : present) {
            if (!value) {
                warnings.add("Annotation categories are present to which no variants are assigned.");
                break;
            }
        }
        if (lambda < 1.0) warnings.add(
            "The inflation factor labmda should be >= 1. Values < 1 will be reset to 1.");
        double[][] matrix;
        if (ld == null) {
            warnings.add("If LD is not provided, an identity matrix is assumed. This may not be accurate in cases where the LD is appreciable.");
            matrix = new double[n][n];
            for (int i = 0; i < n; i++) matrix[i][i] = 1.0;
        } else {
            matrix = square(ld);
            if (matrix.length != n) throw new IllegalArgumentException("LD dimensions differ from the variants");
            if (!isPositiveDefinite(matrix, false, 1e-8)) {
                warnings.add("LD has very small or negative eigenvalues. Epsilon will be added to the diagonal.");
                for (int i = 0; i < n; i++) matrix[i][i] += eps;
            }
        }
        for (double value : beta) if (Double.isNaN(value)) throw new IllegalArgumentException(
            "Input data should not contain missing values.");
        for (double value : se) if (Double.isNaN(value)) throw new IllegalArgumentException(
            "Input data should not contain missing values.");
        BurdenResult result;
        if ("none".equals(method)) result = baseline(anno, beta, se, matrix, weights.length);
        else if ("sum".equals(method)) result = sumCount(anno, beta, se, matrix, weights);
        else throw new IllegalArgumentException("Select method from among 'none' or 'sum'.");
        if (result.sourceScalarZero()) throw new ArithmeticException(
            "Original low-level result is scalar zero: ASBTSS cannot extract $pval; no result admitted");
        // Source ASBTSS uses genomic-control df=1 even for the category baseline.
        return new BurdenResult(genomicControl(Math.max(1.0, lambda), result.pValue(), 1.0),
            result.beta(), result.standardError(), result.score(), result.covariance(),
            result.degreesOfFreedom(), false, warnings);
    }

    private static BurdenResult lowLevel(int[] annotation, double[] beta, double[] se,
            double[][] ld, int categories, double[] weights) {
        int n = dimensions(annotation, beta, se, categories);
        double[][] matrix = square(ld);
        if (matrix.length != n) throw new IllegalArgumentException("LD dimensions differ from the variants");
        int positive = 0;
        for (double value : se) if (value > 0.0) positive++;
        if (positive == 0) return new BurdenResult(0.0, null, null, null, null,
            weights == null ? categories : 1, true, List.of());
        if (positive != n) throw new ArithmeticException(
            "Original positive-SE filtering leaves annotation/score dimensions inconsistent; no result admitted");
        double[] u = new double[categories];
        double[][] cov = new double[categories][categories];
        // Retain the literal annotation-matrix product, including zero * Inf.
        // Direct group accumulation loses the original NaN propagation.
        for (int category = 0; category < categories; category++) {
            for (int i = 0; i < n; i++) u[category] +=
                (annotation[i] - 1 == category ? 1.0 : 0.0) * (beta[i] / (se[i] * se[i]));
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                cov[annotation[i] - 1][annotation[j] - 1] +=
                    (1.0 / se[i]) * matrix[i][j] * (1.0 / se[j]);
            }
        }
        if (weights != null) {
            double weightedU = 0.0;
            double weightedCov = 0.0;
            // SumCountSS multiplies weights' * D' before multiplying eta.
            // It therefore has different Inf propagation from BaselineSS.
            for (int i = 0; i < n; i++) weightedU +=
                weights[annotation[i] - 1] * (beta[i] / (se[i] * se[i]));
            for (int i = 0; i < categories; i++) {
                for (int j = 0; j < categories; j++) {
                    weightedCov += weights[i] * cov[i][j] * weights[j];
                }
            }
            u = new double[] {weightedU};
            cov = new double[][] {{weightedCov}};
        }
        finiteMatrix(cov);
        double[][] inverse = pseudoInverse(cov);
        double[] effects = new double[u.length];
        double[] errors = new double[u.length];
        for (int i = 0; i < u.length; i++) {
            for (int j = 0; j < u.length; j++) effects[i] += inverse[i][j] * u[j];
            errors[i] = Math.sqrt(inverse[i][i]);
        }
        double statistic = 0.0;
        for (int i = 0; i < u.length; i++) statistic += u[i] * effects[i];
        return new BurdenResult(Gamma.cumulative(statistic, u.length / 2.0, 2.0,
            false, false), effects, errors, u, cov, u.length, false, List.of());
    }

    private static double[][] pseudoInverse(double[][] matrix) {
        SingularValueDecomposition decomposition = new SingularValueDecomposition(
            new Array2DRowRealMatrix(matrix, false));
        double[] singular = decomposition.getSingularValues();
        double[][] u = decomposition.getU().getData();
        double[][] v = decomposition.getV().getData();
        int n = matrix.length;
        double[][] result = new double[n][n];
        // Armadillo's default pinv threshold, rather than Commons Math solver rank.
        double tolerance = n * singular[0] * Math.ulp(1.0);
        for (int k = 0; k < n; k++) {
            if (singular[k] > tolerance) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) result[i][j] +=
                        (v[i][k] / singular[k]) * u[j][k];
                }
            }
        }
        return result;
    }

    private static int dimensions(int[] anno, double[] beta, double[] se, int categories) {
        if (anno == null || beta == null || se == null || anno.length == 0
                || beta.length != anno.length || se.length != anno.length || categories < 1) {
            throw new IllegalArgumentException("Require equal, nonempty variant vectors and positive category count");
        }
        for (int value : anno) if (value < 1 || value > categories) throw new IllegalArgumentException(
            "The annotations should take values in 1:length(weights).");
        return anno.length;
    }
    private static double[][] square(double[][] matrix) {
        if (matrix == null || matrix.length == 0) throw new IllegalArgumentException(
            "Require a nonempty square matrix");
        for (double[] row : matrix) if (row == null || row.length != matrix.length) {
            throw new IllegalArgumentException("Require a square matrix");
        }
        return copy(matrix);
    }
    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) result[i] = matrix[i].clone();
        return result;
    }
    private static void finiteMatrix(double[][] matrix) {
        for (double[] row : matrix) for (double value : row) if (!Double.isFinite(value)) {
            throw new ArithmeticException("Nonfinite matrix cannot be decomposed; no result admitted");
        }
    }
}
