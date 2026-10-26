/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.embedding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Exact two-dimensional t-SNE with Gaussian affinities and a Student-t kernel.
 *
 * <p>This is a bounded, quadratic implementation of van der Maaten and Hinton
 * (JMLR 2008, 9:2579-2605). Supply already prepared observations (typically PCA
 * scores); no feature scaling, PCA, batch correction or approximate neighbor
 * search is performed. A uniform global distance rescaling prevents overflow
 * without changing the calibrated affinities. Coordinates have no inferential
 * units and do not measure distances between populations.</p>
 */
public final class Tsne {
    /** Quadratic memory and computation are bounded deliberately. */
    public static final int MAX_OBSERVATIONS = 2000;
    private static final long MAX_DISTANCE_WORK = 200_000_000L;
    private static final double ENTROPY_TOLERANCE = 1e-7;

    private Tsne() { }

    /** Learning rate uses the gradient with its conventional factor of four. */
    public record Options(double perplexity, int iterations, double learningRate,
            double earlyExaggeration, int exaggerationIterations, long seed,
            double minGradientNorm) {
        public Options {
            if (!Double.isFinite(perplexity) || perplexity <= 1)
                throw new IllegalArgumentException("perplexity must be finite and > 1");
            if (iterations < 1 || iterations > 10000 || exaggerationIterations < 0
                    || exaggerationIterations >= iterations)
                throw new IllegalArgumentException("Require 1..10000 iterations and 0 <= exaggerationIterations < iterations");
            if (!Double.isFinite(learningRate) || learningRate <= 0 || learningRate > 100000)
                throw new IllegalArgumentException("learningRate must be in (0, 100000]");
            if (!Double.isFinite(earlyExaggeration) || earlyExaggeration < 1 || earlyExaggeration > 100)
                throw new IllegalArgumentException("earlyExaggeration must be in [1, 100]");
            if (!Double.isFinite(minGradientNorm) || minGradientNorm < 0)
                throw new IllegalArgumentException("minGradientNorm must be finite and nonnegative");
        }
        /** A reproducible random initialization, 1000 updates and 250 early updates. */
        public static Options defaults(double perplexity, long seed) {
            return new Options(perplexity, 1000, 50, 12, 250, seed, 1e-7);
        }
    }

    /** Actual unexaggerated KL and gradient norm at a sampled optimization state. */
    public record Diagnostic(int iteration, double klDivergence, double gradientNorm,
            boolean exaggerated) { }

    /** Returned coordinates are copied; diagnostics do not claim statistical convergence. */
    public record Result(double[][] coordinates, double initialKl, double finalKl,
            int iterations, String stopReason, double maxEntropyError,
            List<Diagnostic> diagnostics) {
        public Result {
            coordinates = copy(coordinates);
            diagnostics = List.copyOf(diagnostics);
        }
        @Override public double[][] coordinates() { return copy(coordinates); }
    }

    /** Compute the symmetric, unit-sum joint probability matrix (diagonal zero). */
    public static double[][] jointProbabilities(double[][] observations, double perplexity) {
        return affinities(observations, perplexity).probabilities();
    }

    /** Fit using exact forces, seeded random initialization, momentum and adaptive gains. */
    public static Result fit(double[][] observations, Options options) {
        Objects.requireNonNull(options, "options");
        Affinities affinity = affinities(observations, options.perplexity());
        double[][] p = affinity.probabilities();
        int n = p.length;
        double[][] y = new double[n][2], velocity = new double[n][2], gains = new double[n][2];
        double[][] gradient = new double[n][2], kernel = new double[n][n];
        Random random = new Random(options.seed());
        for (int i = 0; i < n; i++) for (int d = 0; d < 2; d++) {
            y[i][d] = random.nextGaussian() * 1e-4;
            gains[i][d] = 1;
        }
        center(y);
        double initialKl = evaluate(p, y, 1, gradient, kernel);
        List<Diagnostic> trace = new ArrayList<>();
        String stopReason = "iteration-limit";
        int updates = 0;
        for (int iteration = 0; iteration < options.iterations(); iteration++) {
            boolean early = iteration < options.exaggerationIterations();
            double kl = evaluate(p, y, early ? options.earlyExaggeration() : 1, gradient, kernel);
            double norm = norm(gradient);
            if (iteration % 50 == 0 || iteration == options.exaggerationIterations())
                trace.add(new Diagnostic(iteration, kl, norm, early));
            if (!early && norm <= options.minGradientNorm()) {
                stopReason = "gradient-threshold";
                break;
            }
            double momentum = early ? 0.5 : 0.8;
            for (int i = 0; i < n; i++) for (int d = 0; d < 2; d++) {
                gains[i][d] = Math.signum(gradient[i][d]) != Math.signum(velocity[i][d])
                    ? gains[i][d] + 0.2 : Math.max(0.01, gains[i][d] * 0.8);
                velocity[i][d] = momentum * velocity[i][d] - options.learningRate() * gains[i][d] * gradient[i][d];
                y[i][d] += velocity[i][d];
                if (!Double.isFinite(y[i][d]) || Math.abs(y[i][d]) > 1e100)
                    throw new ArithmeticException("t-SNE optimization diverged; reduce learning rate");
            }
            center(y);
            updates++;
        }
        double finalKl = evaluate(p, y, 1, gradient, kernel);
        trace.add(new Diagnostic(updates, finalKl, norm(gradient), false));
        return new Result(y, initialKl, finalKl, updates, stopReason, affinity.maxEntropyError(), trace);
    }

    private record Affinities(double[][] probabilities, double maxEntropyError) { }

    private static Affinities affinities(double[][] x, double perplexity) {
        Objects.requireNonNull(x, "observations");
        int n = x.length;
        if (n < 3 || n > MAX_OBSERVATIONS)
            throw new IllegalArgumentException("Exact t-SNE requires 3.." + MAX_OBSERVATIONS + " observations; use a scalable upstream implementation for larger data");
        if (!Double.isFinite(perplexity) || perplexity <= 1 || perplexity > n - 1)
            throw new IllegalArgumentException("Require 1 < perplexity <= observations - 1");
        if (x[0] == null || x[0].length == 0 || x[0].length > 10000
                || (long) n * n * x[0].length > MAX_DISTANCE_WORK)
            throw new IllegalArgumentException("Exact t-SNE exceeds feature/distance work limit (N*N*features <= 200 million)");
        int dimensions = x[0].length;
        double scale = 0;
        for (double[] row : x) {
            if (row == null || row.length != dimensions) throw new IllegalArgumentException("Ragged observation matrix");
            for (double value : row) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite observation");
                scale = Math.max(scale, Math.abs(value));
            }
        }
        if (scale == 0) throw new IllegalArgumentException("All observations coincide; t-SNE requires variation");
        double[][] p = new double[n][n];
        double maximum = 0;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            double distance = 0;
            for (int d = 0; d < dimensions; d++) {
                double delta = x[i][d] / scale - x[j][d] / scale;
                distance += delta * delta;
            }
            p[i][j] = distance; p[j][i] = distance;
            maximum = Math.max(maximum, distance);
        }
        if (maximum == 0) throw new IllegalArgumentException("All observations coincide numerically; t-SNE requires variation");
        double error = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) p[i][j] /= maximum;
            error = Math.max(error, calibrate(p[i], i, perplexity));
        }
        double sum = 0;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            double value = (p[i][j] + p[j][i]) / (2.0 * n);
            p[i][j] = value; p[j][i] = value;
            sum += 2 * value;
        }
        for (double[] row : p) for (int j = 0; j < n; j++) row[j] /= sum;
        return new Affinities(p, error);
    }

    private static double calibrate(double[] distances, int self, double perplexity) {
        int n = distances.length;
        double target = Math.log(perplexity), minimum = Double.POSITIVE_INFINITY;
        for (int j = 0; j < n; j++) if (j != self) minimum = Math.min(minimum, distances[j]);
        int ties = 0;
        for (int j = 0; j < n; j++) if (j != self && distances[j] == minimum) ties++;
        if (perplexity + 1e-12 < ties)
            throw new IllegalArgumentException("Perplexity is unattainable at observation " + self + ": " + ties + " equally nearest neighbors; increase perplexity or resolve duplicate/tied observations");
        if (Math.abs(target - Math.log(n - 1.0)) <= ENTROPY_TOLERANCE) {
            Arrays.fill(distances, 1.0 / (n - 1)); distances[self] = 0; return 0;
        }
        double[] weights = new double[n];
        double low = -700, high = 700, error = Double.POSITIVE_INFINITY, sum = 0;
        for (int iteration = 0; iteration < 100; iteration++) {
            double logBeta = (low + high) / 2, beta = Math.exp(logBeta), weighted = 0;
            sum = 0;
            for (int j = 0; j < n; j++) {
                double delta = j == self ? 0 : Math.max(0, distances[j] - minimum);
                double exponent = beta * delta;
                weights[j] = j == self ? 0 : Math.exp(-exponent);
                sum += weights[j];
                // Avoid Infinity*0 if representationally extreme distances occur.
                if (weights[j] > 0) weighted += exponent * weights[j];
            }
            double entropy = Math.log(sum) + weighted / sum;
            error = Math.abs(entropy - target);
            if (error <= ENTROPY_TOLERANCE) break;
            if (entropy > target) low = logBeta; else high = logBeta;
        }
        if (error > ENTROPY_TOLERANCE)
            throw new IllegalArgumentException("Could not calibrate perplexity at observation " + self + "; inspect tied/ill-scaled data or change perplexity");
        for (int j = 0; j < n; j++) distances[j] = weights[j] / sum;
        return error;
    }

    // Package visibility permits independent KL/finite-difference gradient tests.
    static double evaluate(double[][] p, double[][] y, double exaggeration,
            double[][] gradient, double[][] kernel) {
        int n = p.length;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            Arrays.fill(gradient[i], 0);
            for (int j = i + 1; j < n; j++) {
                double dx = y[i][0] - y[j][0], dy = y[i][1] - y[j][1];
                double weight = 1 / (1 + dx * dx + dy * dy);
                kernel[i][j] = weight;
                sum += 2 * weight;
            }
        }
        if (!(sum > 0) || !Double.isFinite(sum)) throw new ArithmeticException("Degenerate t-SNE embedding");
        double kl = 0;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            double q = kernel[i][j] / sum;
            if (p[i][j] > 0) kl += 2 * p[i][j] * (Math.log(p[i][j]) - Math.log(q));
            double factor = 4 * (exaggeration * p[i][j] - q) * kernel[i][j];
            for (int d = 0; d < 2; d++) {
                double force = factor * (y[i][d] - y[j][d]);
                gradient[i][d] += force; gradient[j][d] -= force;
            }
        }
        if (!Double.isFinite(kl)) throw new ArithmeticException("Nonfinite t-SNE objective");
        return kl;
    }

    private static void center(double[][] y) {
        for (int d = 0; d < 2; d++) {
            double mean = 0;
            for (double[] row : y) mean += row[d] / y.length;
            for (double[] row : y) row[d] -= mean;
        }
    }
    private static double norm(double[][] gradient) {
        double squared = 0;
        for (double[] row : gradient) for (double value : row) squared += value * value;
        return Math.sqrt(squared);
    }
    private static double[][] copy(double[][] values) {
        return Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);
    }
}
