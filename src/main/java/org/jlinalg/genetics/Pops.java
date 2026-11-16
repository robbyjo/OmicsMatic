/*
 * Copyright (C) 2026 JLinAlg contributors
 * Algorithm translated from FinucaneLab/pops, GPL-3.0,
 * commit 76eb86cba10254490003c8f4dc7ff5ce492d3667, pops.py.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdistlib.ChiSquare;
import jdistlib.accelerator.ComputeBackend;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/**
 * Native PoPS v0.2 regression on finite, already aligned gene/feature matrices.
 * Projection, marginal selection and training gene sets are separate explicit
 * masks. A caller must explicitly apply MAGMA covariance regularization when
 * appropriate; custom-score covariance is never adjusted automatically.
 * MAGMA execution, file munging, locus ranking and LassoCV are separate work.
 */
public final class Pops {
    private Pops() { }

    public enum Method { RIDGE, MINIMUM_NORM_OLS }

    /**
     * Null cutoff means no p-value filter. Controls still lose NaN marginal
     * features. A subset limits controls too. Forward steps override all
     * other selection settings, matching the original source.
     */
    public record Selection(Double pValueCutoff, Integer maximumFeatures, int[] subset,
                            boolean[] controls, Integer forwardSteps) {
        public Selection {
            if (pValueCutoff != null) PopsMath.finite(pValueCutoff, "feature p-value cutoff");
            if (maximumFeatures != null && maximumFeatures < 0)
                throw new IllegalArgumentException("Nonnegative feature maximum required");
            if (forwardSteps != null && forwardSteps < 1)
                throw new IllegalArgumentException("Positive forward selection step count required");
            subset = subset == null ? null : subset.clone();
            controls = controls == null ? null : controls.clone();
        }
        @Override public int[] subset() { return subset == null ? null : subset.clone(); }
        @Override public boolean[] controls() { return controls == null ? null : controls.clone(); }
        public static Selection defaults() { return new Selection(.05, null, null, null, null); }
    }

    /** Original population-MSE marginal estimates; constant columns retain NaN. */
    public record Marginal(double beta, double standardError, double pValue, double rSquared) { }

    /** Immutable fitted values and all marginal/selection diagnostics in original feature order. */
    public record Result(double[] coefficients, double[] predictions, double[] projectedTarget,
                         Marginal[] marginals, int[] selectedFeatures, int[] forwardSelectionRanks,
                         Method method, double selectedAlpha, double bestCvScore, double[] cvScores,
                         String backend) {
        public Result {
            coefficients = coefficients.clone(); predictions = predictions.clone(); projectedTarget = projectedTarget.clone();
            marginals = marginals.clone(); selectedFeatures = selectedFeatures.clone(); forwardSelectionRanks = forwardSelectionRanks.clone();
            cvScores = cvScores.clone();
        }
        @Override public double[] coefficients() { return coefficients.clone(); }
        @Override public double[] predictions() { return predictions.clone(); }
        @Override public double[] projectedTarget() { return projectedTarget.clone(); }
        @Override public Marginal[] marginals() { return marginals.clone(); }
        @Override public int[] selectedFeatures() { return selectedFeatures.clone(); }
        @Override public int[] forwardSelectionRanks() { return forwardSelectionRanks.clone(); }
        @Override public double[] cvScores() { return cvScores.clone(); }

        /** Predicts from original, unprojected features, with no added intercept or score rescaling. */
        public double[] predict(double[][] originalFeatures) {
            if (originalFeatures == null || originalFeatures.length == 0) throw new IllegalArgumentException("Feature rows required");
            double[][] x = PopsMath.copy(originalFeatures, originalFeatures.length, "PoPS prediction features");
            if (x[0].length != coefficients.length) throw new IllegalArgumentException("Prediction feature order/dimension differs from fitted features");
            return predictSelected(x, coefficients, selectedFeatures);
        }
    }

    /** Source HLA region is chromosome string "6", inclusive TSS 20 to 40 Mb. */
    public static boolean[] geneMask(String[] chromosomes, long[] tss, java.util.Set<String> useChromosomes, boolean removeHla) {
        if (chromosomes == null || tss == null || tss.length != chromosomes.length || useChromosomes == null)
            throw new IllegalArgumentException("Aligned chromosome/TSS arrays and explicit chromosome set required");
        boolean[] mask = new boolean[chromosomes.length];
        for (int i = 0; i < mask.length; i++) {
            if (chromosomes[i] == null || tss[i] < 0) throw new IllegalArgumentException("Explicit chromosome and nonnegative TSS required");
            mask[i] = useChromosomes.contains(chromosomes[i]) && !(removeHla && chromosomes[i].equals("6") && tss[i] >= 20000000 && tss[i] <= 40000000);
        }
        return mask;
    }

    /** Six source MAGMA controls: NPARAM, log NPARAM, NPARAM/NSNPS and inverse MAC, with logs. */
    public static double[][] magmaCovariates(double[] nsnps, double[] nparam, double[] mac) {
        if (nsnps == null || nparam == null || mac == null || nsnps.length == 0 || nparam.length != nsnps.length || mac.length != nsnps.length)
            throw new IllegalArgumentException("Aligned positive MAGMA metadata required");
        double[][] controls = new double[nsnps.length][6];
        for (int i = 0; i < nsnps.length; i++) {
            if (!(nsnps[i] > 0) || !(nparam[i] > 0) || !(mac[i] > 0))
                throw new IllegalArgumentException("Positive NSNPS, NPARAM and MAC required; no repair is performed");
            double density = nparam[i] / nsnps[i], inverseMac = 1 / mac[i];
            controls[i] = new double[] {nparam[i], Math.log(nparam[i]), density, Math.log(density), inverseMac, Math.log(inverseMac)};
            for (double value : controls[i]) PopsMath.finite(value, "MAGMA control");
        }
        return controls;
    }

    /**
     * Fits the original projection, feature selection, GLS transform and joint
     * regression. All matrices use the target row order; every feature is
     * identified by its original column ordinal. This API does not align IDs.
     */
    public static Result fit(double[] target, double[][] features, double[][] covariates,
                             PopsCovariance covariance, boolean[] projectionMask, boolean[] selectionMask,
                             boolean[] trainingMask, Selection selection, Method method, BackendPolicy policy) {
        if (target == null || target.length < 2 || selection == null || method == null || policy == null)
            throw new IllegalArgumentException("Target, selection settings, method and backend policy required");
        int n = target.length;
        double[] y = target.clone();
        for (double value : y) PopsMath.finite(value, "PoPS target");
        double[][] x = PopsMath.copy(features, n, "PoPS features");
        int p = x[0].length;
        if (p == 0) throw new IllegalArgumentException("PoPS requires at least one feature");
        double[][] cov = covariates == null ? null : PopsMath.copy(covariates, n, "PoPS target covariates");
        if (covariance != null && covariance.size() != n) throw new IllegalArgumentException("Covariance target dimension differs");
        int[] selectRows = PopsMath.indices(selectionMask, n, "feature selection"), trainRows = PopsMath.indices(trainingMask, n, "training");
        int[] projectionRows = cov == null ? null : PopsMath.indices(projectionMask, n, "target projection");
        validateSelection(selection, p);
        try (var context = BackendContext.select(policy)) {
            ComputeBackend backend = context.backend();
            if (cov != null) y = projectTarget(y, cov, covariance, projectionRows, backend);
            Marginal[] marginals = marginals(y, x, null, covariance, selectRows, backend);
            int[] ranks = new int[p];
            boolean[] selected;
            if (selection.forwardSteps != null) {
                selected = new boolean[p];
                double[][] stepCov = PopsMath.intercept(n);
                for (int step = 1; step <= selection.forwardSteps; step++) {
                    Marginal[] current = marginals(y, x, stepCov, covariance, selectRows, backend);
                    int[] remaining = new int[p - step + 1]; double[] pValues = new double[remaining.length];
                    for (int j = 0, k = 0; j < p; j++) if (!selected[j]) { remaining[k] = j; pValues[k++] = current[j].pValue; }
                    int top = remaining[PopsOrder.argsort(pValues)[0]];
                    selected[top] = true; ranks[top] = step;
                    double[] added = new double[n]; for (int i = 0; i < n; i++) added[i] = x[i][top];
                    stepCov = PopsMath.append(stepCov, added);
                }
            } else selected = select(marginals, selection);
            int count = 0; for (boolean value : selected) if (value) count++;
            if (count == 0) throw new IllegalArgumentException("PoPS selected no features; no fitted model; review the explicit feature settings");
            int[] columns = new int[count]; for (int j = 0, k = 0; j < p; j++) if (selected[j]) columns[k++] = j;
            double[][] trainX = new double[trainRows.length][count];
            for (int i = 0; i < trainRows.length; i++) for (int j = 0; j < count; j++) trainX[i][j] = x[trainRows[i]][columns[j]];
            double[][] trainY = PopsMath.column(y, trainRows), intercept = PopsMath.intercept(trainRows.length);
            if (covariance != null) {
                trainX = covariance.whiten(trainRows, trainX, backend); trainY = covariance.whiten(trainRows, trainY, backend);
                intercept = covariance.whiten(trainRows, intercept, backend);
            }
            trainX = PopsMath.project(trainX, intercept, backend); trainY = PopsMath.project(trainY, intercept, backend);
            double[] response = new double[trainY.length]; for (int i = 0; i < response.length; i++) response[i] = trainY[i][0];
            double[] coefficients, cvScores = new double[0]; double alpha = Double.NaN, bestScore = Double.NaN;
            if (method == Method.RIDGE) {
                var ridge = PopsMath.ridge(trainX, response, backend); coefficients = ridge.coefficients();
                alpha = ridge.alpha(); bestScore = ridge.bestScore(); cvScores = ridge.scores();
            } else coefficients = PopsMath.minimumNorm(trainX, response, backend);
            double[] fullCoefficients = new double[p]; for (int j = 0; j < count; j++) fullCoefficients[columns[j]] = coefficients[j];
            double[] predictions = predictSelected(x, fullCoefficients, columns);
            return new Result(fullCoefficients, predictions, y, marginals, columns, ranks, method, alpha, bestScore, cvScores, backend.id());
        }
    }

    private static double[] projectTarget(double[] y, double[][] covariates, PopsCovariance covariance, int[] rows, ComputeBackend backend) {
        double[][] cov = PopsMath.withInterceptIfNeeded(covariates);
        double[][] trainX = PopsMath.rows(cov, rows), trainY = PopsMath.column(y, rows);
        if (covariance != null) { trainX = covariance.whiten(rows, trainX, backend); trainY = covariance.whiten(rows, trainY, backend); }
        double[] response = new double[rows.length]; for (int i = 0; i < rows.length; i++) response[i] = trainY[i][0];
        double[] beta = PopsMath.minimumNorm(trainX, response, backend), result = y.clone();
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < beta.length; j++) result[i] -= cov[i][j] * beta[j];
            PopsMath.finite(result[i], "projected target score");
        }
        return result;
    }

    private static Marginal[] marginals(double[] y, double[][] x, double[][] covariates, PopsCovariance covariance, int[] rows, ComputeBackend backend) {
        double[][] cov = covariates == null ? PopsMath.intercept(y.length) : PopsMath.withInterceptIfNeeded(covariates);
        double[][] subCov = PopsMath.rows(cov, rows), subX = PopsMath.rows(x, rows), subY = PopsMath.column(y, rows);
        if (covariance != null) {
            subCov = covariance.whiten(rows, subCov, backend); subX = covariance.whiten(rows, subX, backend); subY = covariance.whiten(rows, subY, backend);
        }
        subX = PopsMath.project(subX, subCov, backend); subY = PopsMath.project(subY, subCov, backend);
        double[] response = new double[rows.length]; for (int i = 0; i < rows.length; i++) response[i] = subY[i][0];
        double variance = PopsMath.variance(response); Marginal[] result = new Marginal[x[0].length];
        for (int j = 0; j < result.length; j++) {
            double sumSquares = 0, cross = 0;
            for (int i = 0; i < rows.length; i++) { sumSquares += subX[i][j] * subX[i][j]; cross += subY[i][0] * subX[i][j]; }
            if (Math.abs(sumSquares) <= 1e-8) { result[j] = new Marginal(Double.NaN, Double.NaN, Double.NaN, Double.NaN); continue; }
            double beta = cross / sumSquares, mse = 0;
            for (int i = 0; i < rows.length; i++) { double residual = subY[i][0] - beta * subX[i][j]; mse += residual * residual; }
            mse /= rows.length;
            double se = Math.sqrt(mse / sumSquares), z = beta / se;
            result[j] = new Marginal(beta, se, ChiSquare.cumulative(z * z, 1, false, false), 1 - mse / variance);
        }
        return result;
    }

    private static void validateSelection(Selection selection, int p) {
        if (selection.forwardSteps != null) {
            if (selection.forwardSteps > p) throw new IllegalArgumentException("Forward steps exceed feature count");
            return;
        }
        if (selection.controls != null && selection.controls.length != p) throw new IllegalArgumentException("Control feature mask dimension differs");
        if (selection.subset != null) {
            boolean[] seen = new boolean[p];
            for (int j : selection.subset) {
                if (j < 0 || j >= p || seen[j]) throw new IllegalArgumentException("Feature subset has duplicate or out-of-range ordinals");
                seen[j] = true;
            }
        }
    }

    private static boolean[] select(Marginal[] marginals, Selection selection) {
        int p = marginals.length;
        int[] subset = selection.subset;
        if (subset == null) { subset = new int[p]; for (int j = 0; j < p; j++) subset[j] = j; }
        boolean[] selected = new boolean[p]; List<Integer> candidates = new ArrayList<>();
        for (int j : subset) {
            if (selection.controls != null && selection.controls[j]) selected[j] = true;
            else if (selection.pValueCutoff == null || marginals[j].pValue < selection.pValueCutoff) candidates.add(j);
        }
        if (selection.maximumFeatures != null) {
            double[] pValues = new double[candidates.size()]; for (int i = 0; i < pValues.length; i++) pValues[i] = marginals[candidates.get(i)].pValue;
            int[] order = PopsOrder.argsort(pValues);
            for (int i = 0; i < Math.min(order.length, selection.maximumFeatures); i++) selected[candidates.get(order[i])] = true;
        } else for (int j : candidates) selected[j] = true;
        for (int j = 0; j < p; j++) selected[j] &= !Double.isNaN(marginals[j].pValue);
        return selected;
    }

    private static double[] predictSelected(double[][] x, double[] coefficients, int[] selected) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            for (int j : selected) result[i] += x[i][j] * coefficients[j];
            PopsMath.finite(result[i], "PoPS prediction");
        }
        return result;
    }
}
