/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.mr;

import java.util.Arrays;
import jdistlib.ChiSquare;

/**
 * Single-instrument SMR delta variance and a supplied-instrument HEIDI
 * ratio-deviation kernel. Reference alignment, instrument selection, LD pruning,
 * multi-SNP fitting and the original HEIDI tail approximation are separate.
 */
public final class SmrStatistics {
    private SmrStatistics() { }

    /** A finite ratio estimate; a finite log tail may remain when P underflows. */
    public record Ratio(double effect, double standardError, double statistic,
                        double pValue, double logPValue) { }

    /** Independent exposure/outcome estimates: both supplied standard errors enter the variance. */
    public static Ratio ratio(double exposureEffect, double exposureStandardError,
                              double outcomeEffect, double outcomeStandardError) {
        return ratio(exposureEffect, exposureStandardError, outcomeEffect, outcomeStandardError, 0);
    }

    /**
     * Delta-method ratio with an explicitly supplied estimate-error correlation.
     * This method does not estimate sample overlap or validate its biological meaning.
     */
    public static Ratio ratio(double exposureEffect, double exposureStandardError,
                              double outcomeEffect, double outcomeStandardError,
                              double estimateErrorCorrelation) {
        effect(exposureEffect, exposureStandardError);
        require(Double.isFinite(outcomeEffect) && Double.isFinite(outcomeStandardError)
            && outcomeStandardError > 0, "Require a finite outcome effect and positive finite SE");
        require(Double.isFinite(estimateErrorCorrelation) && Math.abs(estimateErrorCorrelation) <= 1,
            "Declare an estimate-error correlation in [-1,1]");
        double estimate = outcomeEffect / exposureEffect;
        double outcomeTerm = outcomeStandardError / exposureEffect;
        double exposureTerm = estimate * (exposureStandardError / exposureEffect);
        double variance = outcomeTerm * outcomeTerm + exposureTerm * exposureTerm
            - 2 * estimateErrorCorrelation * outcomeTerm * exposureTerm;
        require(Double.isFinite(estimate) && Double.isFinite(variance) && variance > 0,
            "SMR ratio/variance is unrepresentable or nonpositive; no result can be reported");
        double se = Math.sqrt(variance);
        double statistic = estimate * estimate / variance;
        require(Double.isFinite(statistic), "SMR statistic exceeds the finite native domain");
        double p = ChiSquare.cumulative(statistic, 1, false, false);
        double logP = ChiSquare.cumulative(statistic, 1, false, true);
        require(Double.isFinite(p) && p >= 0 && p <= 1 && Double.isFinite(logP) && logP <= 0,
            "SMR probability is outside the finite native domain");
        return new Ratio(estimate, se, statistic, p, logP);
    }

    /** Immutable supplied-instrument kernel; no HEIDI P value or independent source refit is implied. */
    public static final class DeviationKernel {
        private final int topIndex;
        private final double[] ratios;
        private final double[][] ratioCovariance;
        private final double[][] deviationCovariance;
        private final double[][] deviationCorrelation;
        private final double[] squaredStandardizedDeviations;
        private final double statistic;

        private DeviationKernel(int topIndex, double[] ratios, double[][] covariance,
                                double[][] deviations, double[][] correlation,
                                double[] squares, double statistic) {
            this.topIndex = topIndex;
            this.ratios = ratios.clone();
            ratioCovariance = copy(covariance);
            deviationCovariance = copy(deviations);
            deviationCorrelation = copy(correlation);
            squaredStandardizedDeviations = squares.clone();
            this.statistic = statistic;
        }
        public int topIndex() { return topIndex; }
        public double[] ratios() { return ratios.clone(); }
        public double[][] ratioCovariance() { return copy(ratioCovariance); }
        public double[][] deviationCovariance() { return copy(deviationCovariance); }
        public double[][] deviationCorrelation() { return copy(deviationCorrelation); }
        public double[] squaredStandardizedDeviations() { return squaredStandardizedDeviations.clone(); }
        public double statistic() { return statistic; }
    }

    /**
     * Original no-overlap HEIDI covariance convention, including its finite-strength
     * correction and explicit 1e-8 diagonal addition to the deviation covariance.
     * Inputs are already selected and aligned. No pruning or overlap inference occurs.
     */
    public static DeviationKernel deviationKernel(double[] exposureEffects, double[] exposureSEs,
                                                  double[] outcomeEffects, double[] outcomeSEs,
                                                  double[][] ld, int topIndex) {
        require(exposureEffects != null && exposureEffects.length >= 2 && exposureEffects.length <= 500,
            "Supply 2..500 already selected instruments");
        int n = exposureEffects.length;
        require(exposureSEs != null && outcomeEffects != null && outcomeSEs != null
            && exposureSEs.length == n && outcomeEffects.length == n && outcomeSEs.length == n
            && ld != null && ld.length == n && topIndex >= 0 && topIndex < n,
            "Complete aligned vectors, LD and an explicit top index are required");
        double[] b = new double[n];
        double[] inverseZ = new double[n];
        double[] outcomeScale = new double[n];
        for (int i = 0; i < n; i++) {
            effect(exposureEffects[i], exposureSEs[i]);
            require(Double.isFinite(outcomeEffects[i]) && Double.isFinite(outcomeSEs[i]) && outcomeSEs[i] > 0,
                "Require finite outcome effects and positive finite SEs");
            require(ld[i] != null && ld[i].length == n, "Complete square LD matrix required");
            b[i] = outcomeEffects[i] / exposureEffects[i];
            inverseZ[i] = exposureSEs[i] / exposureEffects[i];
            outcomeScale[i] = outcomeSEs[i] / exposureEffects[i];
            require(Double.isFinite(b[i]) && Double.isFinite(inverseZ[i]) && Double.isFinite(outcomeScale[i]),
                "Unrepresentable native HEIDI ratio scale");
        }
        double[][] covariance = new double[n][n];
        for (int i = 0; i < n; i++) {
            require(Double.isFinite(ld[i][i]) && Math.abs(ld[i][i] - 1) <= 1e-10,
                "LD diagonal must equal one within declared floating-point tolerance");
            for (int j = 0; j < n; j++) {
                double r = ld[i][j];
                require(Double.isFinite(r) && Math.abs(r) <= 1 + 1e-10 && Math.abs(r - ld[j][i]) <= 1e-12,
                    "LD must be finite, symmetric and within its correlation bounds");
                double inverseProduct = inverseZ[i] * inverseZ[j];
                covariance[i][j] = r * (outcomeScale[i] * outcomeScale[j]
                    + b[i] * b[j] * inverseProduct) - b[i] * b[j] * inverseProduct * inverseProduct;
                require(Double.isFinite(covariance[i][j]), "Unrepresentable native HEIDI covariance");
            }
        }
        int[] others = new int[n - 1];
        for (int i = 0, k = 0; i < n; i++) if (i != topIndex) others[k++] = i;
        double[][] deviations = new double[n - 1][n - 1];
        double[][] correlation = new double[n - 1][n - 1];
        double[] squares = new double[n - 1];
        double sum = 0;
        for (int i = 0; i < others.length; i++) {
            int ii = others[i];
            for (int j = 0; j < others.length; j++) {
                int jj = others[j];
                deviations[i][j] = covariance[topIndex][topIndex] + covariance[ii][jj]
                    - covariance[topIndex][ii] - covariance[topIndex][jj] + (i == j ? 1e-8 : 0);
                require(Double.isFinite(deviations[i][j]), "Unrepresentable native deviation covariance");
            }
            require(deviations[i][i] > 0, "Nonpositive HEIDI deviation variance; no clamping or replacement");
            double difference = b[topIndex] - b[ii];
            squares[i] = difference * difference / deviations[i][i];
            require(Double.isFinite(squares[i]), "Unrepresentable standardized HEIDI deviation");
            sum += squares[i];
        }
        for (int i = 0; i < others.length; i++) for (int j = 0; j < others.length; j++) {
            double product = deviations[i][i] * deviations[j][j];
            require(Double.isFinite(product) && product > 0, "Unrepresentable deviation variance product");
            correlation[i][j] = deviations[i][j] / Math.sqrt(product);
            require(Double.isFinite(correlation[i][j]), "Unrepresentable native deviation correlation");
        }
        require(Double.isFinite(sum), "Unrepresentable native HEIDI statistic");
        return new DeviationKernel(topIndex, b, covariance, deviations, correlation, squares, sum);
    }

    private static void effect(double beta, double se) {
        require(Double.isFinite(beta) && beta != 0 && Double.isFinite(se) && se > 0,
            "Require a nonzero finite exposure effect and positive finite SE");
    }
    private static double[][] copy(double[][] matrix) {
        return Arrays.stream(matrix).map(double[]::clone).toArray(double[][]::new);
    }
    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalArgumentException(detail);
    }
}
