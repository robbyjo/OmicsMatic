/*
 * Copyright (C) 2026 JLinAlg contributors
 * LDpred2 equations and Gibbs update derived from Florian Prive's bigsnpr 1.12.2,
 * R/LDpred2.R and src/ldpred2.cpp (CRAN, GPL-3).
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.jlinalg.genetics;

/**
 * Native LDpred2 infinitesimal estimation and one full LDpred2-grid Gibbs model.
 * Inputs must already share variant order, allele orientation, build and LD panel.
 * No summary-statistic harmonization, phenotype tuning or LDpred2-auto is inferred.
 */
public final class LdPred2 {
    private LdPred2() {}

    /** Residual tolerance applies to the original standardized linear system. */
    public record SolveOptions(double relativeTolerance, int maxIterations) {
        public SolveOptions {
            if (!Double.isFinite(relativeTolerance) || relativeTolerance <= 0 || relativeTolerance >= 1 || maxIterations < 1) {
                throw new IllegalArgumentException("Positive relative tolerance below 1 and positive iteration limit required");
            }
        }
        public static SolveOptions defaults() { return new SolveOptions(1e-10, 10000); }
    }

    public record Infinitesimal(double[] alleleEffects, int iterations, double relativeResidual) {
        public Infinitesimal { alleleEffects = alleleEffects.clone(); }
        @Override public double[] alleleEffects() { return alleleEffects.clone(); }
    }

    public record GridParameters(double heritability, double causalProportion, boolean sparse, int burnIn, int iterations) {
        public GridParameters {
            positive(heritability, "heritability");
            if (!Double.isFinite(causalProportion) || causalProportion <= 0 || causalProportion > 1
                    || burnIn < 0 || iterations < 1 || (long) burnIn + iterations > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("p in (0,1], nonnegative burn-in and positive bounded iterations required");
            }
        }
    }

    public record Gibbs(double[] alleleEffects, int completedIterations) {
        public Gibbs { alleleEffects = alleleEffects.clone(); }
        @Override public double[] alleleEffects() { return alleleEffects.clone(); }
    }

    /**
     * Receives a copy of standardized sampled effects after each retained sweep.
     * Samples are provisional until gibbs returns successfully: a later error
     * invalidates the model, including any previously delivered samples.
     */
    @FunctionalInterface public interface Samples { void accept(int retainedIteration, double[] standardizedEffects); }

    /** Original divergence condition; this exception admits no posterior result. */
    public static final class DivergenceException extends ArithmeticException {
        private static final long serialVersionUID = 1L;
        private final int sweep;
        public DivergenceException(int sweep) { super("LDpred2 Gibbs divergence at sweep " + sweep + "; no accepted posterior effects"); this.sweep = sweep; }
        public int sweep() { return sweep; }
    }

    private record Input(double[] scale, double[] betaHat, double[] sampleSizes) {}

    /** Solves (R + diag(m / (h2 * N))) b = beta / scale and returns b * scale. */
    public static Infinitesimal infinitesimal(LdCorrelation corr, double[] beta, double[] betaSe,
            double[] effectiveSampleSizes, double heritability, SolveOptions options) {
        positive(heritability, "heritability");
        if (options == null) throw new IllegalArgumentException("Explicit solver options required");
        Input input = input(corr, beta, betaSe, effectiveSampleSizes);
        int m = corr.size();
        double[] diagonal = new double[m], x = new double[m];
        for (int i = 0; i < m; i++) {
            diagonal[i] = m / (heritability * input.sampleSizes[i]);
            positive(diagonal[i], "finite positive LDpred2 diagonal regularization");
        }
        double rhsNorm = norm(input.betaHat);
        if (rhsNorm == 0) return new Infinitesimal(x, 0, 0);
        double[] rhs = new double[m], residual = new double[m], z = new double[m], direction = new double[m];
        for (int i = 0; i < m; i++) {
            rhs[i] = input.betaHat[i] / rhsNorm;
            residual[i] = rhs[i];
            direction[i] = z[i] = residual[i] / (1 + diagonal[i]);
        }
        double rho = dot(residual, z);
        for (long step = 1; step <= options.maxIterations; step++) {
            int iteration = (int) step;
            double[] product = corr.multiply(direction, diagonal);
            double curvature = dot(direction, product);
            positive(curvature, "positive finite conjugate-gradient curvature; LD matrix may be invalid");
            double alpha = rho / curvature;
            for (int i = 0; i < m; i++) { x[i] += alpha * direction[i]; residual[i] -= alpha * product[i]; }
            boolean restart = iteration % 50 == 0 || norm(residual) <= options.relativeTolerance;
            if (restart) {
                product = corr.multiply(x, diagonal);
                for (int i = 0; i < m; i++) residual[i] = rhs[i] - product[i];
                double relativeResidual = norm(residual);
                if (Double.isFinite(relativeResidual) && relativeResidual <= options.relativeTolerance) {
                    for (int i = 0; i < m; i++) { x[i] *= rhsNorm * input.scale[i]; finite(x[i], "allele effect"); }
                    return new Infinitesimal(x, iteration, relativeResidual);
                }
            }
            for (int i = 0; i < m; i++) z[i] = residual[i] / (1 + diagonal[i]);
            double nextRho = dot(residual, z);
            positive(nextRho, "positive finite conjugate-gradient residual");
            double ratio = nextRho / rho;
            for (int i = 0; i < m; i++) direction[i] = z[i] + (restart ? 0 : ratio * direction[i]);
            rho = nextRho;
        }
        throw new ArithmeticException("LDpred2-inf failed to converge in " + options.maxIterations + " iterations; no accepted effects");
    }

    /**
     * Runs one original grid-row model. Averaged effects are Rao-Blackwellized
     * posterior means; sampled effects are different and remain standardized.
     * Each model requires its own explicit random stream. Caller handles grid
     * ordering, independent model streams and validation-set model selection.
     */
    public static Gibbs gibbs(LdCorrelation corr, double[] beta, double[] betaSe, double[] effectiveSampleSizes,
            GridParameters parameters, LdPred2Random random, Samples samples) {
        if (parameters == null || random == null) throw new IllegalArgumentException("Model parameters and random stream required");
        Input input = input(corr, beta, betaSe, effectiveSampleSizes);
        int m = corr.size();
        double[] current = new double[m], average = new double[m], dotprods = new double[m];
        double p = parameters.causalProportion;
        double perVariant = parameters.heritability / (m * p);
        positive(perVariant, "finite positive prior variance");
        double inverseOdds = (1 - p) / p;
        double gap0 = 2 * dot(input.betaHat, input.betaHat);
        for (int k = -parameters.burnIn; k < parameters.iterations; k++) {
            double gap = 0;
            for (int j = 0; j < m; j++) {
                double residualEffect = input.betaHat[j] - (dotprods[j] - current[j]);
                double c1 = perVariant * input.sampleSizes[j];
                positive(c1, "finite positive prior information");
                double c2 = 1 / (1 + 1 / c1), c3 = c2 * residualEffect, c4 = c2 / input.sampleSizes[j];
                positive(c4, "finite positive posterior variance");
                double postP = 1 / (1 + inverseOdds * Math.sqrt(1 + c1) * Math.exp(-c3 * c3 / c4 / 2));
                finite(postP, "posterior probability");
                double difference = -current[j];
                if (parameters.sparse && postP < p) {
                    current[j] = 0;
                } else {
                    double uniform = random.uniform();
                    if (!Double.isFinite(uniform) || uniform < 0 || uniform >= 1) throw new IllegalArgumentException("Uniform draw outside [0,1)");
                    if (postP > uniform) {
                        double normal = random.standardNormal(); finite(normal, "normal draw");
                        current[j] = c3 + Math.sqrt(c4) * normal;
                        finite(current[j], "sampled effect");
                        difference += current[j]; gap += current[j] * current[j];
                    } else current[j] = 0;
                    if (k >= 0) average[j] += c3 * postP;
                }
                if (difference != 0) corr.addColumn(j, difference, dotprods);
            }
            if (!Double.isFinite(gap) || gap > gap0) throw new DivergenceException(k + parameters.burnIn + 1);
            if (k >= 0 && samples != null) samples.accept(k, current.clone());
        }
        for (int j = 0; j < m; j++) {
            average[j] = average[j] / parameters.iterations * input.scale[j];
            finite(average[j], "posterior allele effect");
        }
        return new Gibbs(average, parameters.burnIn + parameters.iterations);
    }

    private static Input input(LdCorrelation corr, double[] beta, double[] betaSe, double[] sampleSizes) {
        if (corr == null || beta == null || betaSe == null || sampleSizes == null
                || beta.length != corr.size() || betaSe.length != corr.size() || sampleSizes.length != corr.size()) {
            throw new IllegalArgumentException("One effect, standard error and effective sample size per LD variant required");
        }
        int m = corr.size(); double[] scale = new double[m], betaHat = new double[m];
        double[] n = sampleSizes.clone();
        for (int i = 0; i < m; i++) {
            finite(beta[i], "effect"); positive(betaSe[i], "standard error"); positive(n[i], "effective sample size");
            scale[i] = Math.sqrt(n[i] * (betaSe[i] * betaSe[i]) + beta[i] * beta[i]);
            positive(scale[i], "finite positive LDpred2 scaling; no automatic numeric repair");
            betaHat[i] = beta[i] / scale[i];
        }
        return new Input(scale, betaHat, n);
    }
    private static double dot(double[] a, double[] b) { double s = 0; for (int i = 0; i < a.length; i++) s += a[i] * b[i]; return s; }
    private static double norm(double[] a) { double s = 0; for (double x : a) s = Math.hypot(s, x); return s; }
    private static void finite(double value, String name) { if (!Double.isFinite(value)) throw new ArithmeticException("Nonfinite " + name + "; no accepted effects"); }
    private static void positive(double value, String name) { if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Positive finite " + name + " required"); }
}
