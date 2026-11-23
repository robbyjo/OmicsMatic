/*
 * Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source behavior: EIG v7.2.1, 4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1.
 * Original EIG copyright (c) 2006-2016 Broad Institute, Inc. and Harvard Medical
 * School; BSD-3-Clause notice is retained in org/jlinalg/omics/smartpca-4f7e9f0/.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jdistlib.accelerator.ComputeBackend;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/**
 * Exact-matrix smartpca preparation and ordinary/least-squares projection for
 * an explicitly prepared autosomal genotype panel. This component performs no
 * LD filtering, outlier removal, shrinkage, FST, ancestry assignment or file
 * conversion. It does not substitute for those separate source operations.
 */
public final class SmartpcaPrepared {
    private SmartpcaPrepared() { }

    /** Original projection branches without shrinkmode or autoshrink. */
    public enum Projection { ORTHOGONAL, LEAST_SQUARES }
    /** Native descriptions of source row exclusion; not upstream log literals. */
    public enum MarkerStatus { USED, ALL_BASIS_CALLS_MISSING, NO_MINOR_ALLELE,
        BELOW_MINIMUM_ALLELE_COUNT, TOO_MANY_BASIS_CALLS_MISSING }
    /** Finite coordinates are calculations, not biological ancestry assertions. */
    public enum ScoreStatus { COMPUTED, INSUFFICIENT_NONMISSING_MARKERS }

    /** Explicit complete-matrix work and storage bounds; no automatic subsetting. */
    public record Limits(long maximumProducts, long maximumRetainedBytes) {
        public Limits {
            require(maximumProducts > 0 && maximumRetainedBytes > 0, "Positive work/storage limits required");
        }
        public static Limits defaults() { return new Limits(2_000_000_000L, 1_073_741_824L); }
    }

    /**
     * usenorm/altnormstyle follow executable C, rather than its reversed nearby
     * explanatory comment: altnormstyle YES uses p=mean/2; NO uses pseudocounts
     * only in the normalizing denominator. Centering always uses the observed mean.
     */
    public record Options(int components, boolean useNorm, boolean alternateNormStyle,
            int minimumAlleleCount, int maximumMissing, Projection projection, Limits limits) {
        public Options {
            require(components > 0 && minimumAlleleCount >= 1 && maximumMissing >= 0,
                "Positive components/minimum allele count and nonnegative missing limit required");
            Objects.requireNonNull(projection, "projection"); Objects.requireNonNull(limits, "limits");
        }
        /** Source normalization settings; outlier/shrinkage choices are outside this component. */
        public static Options prepared(int components, Projection projection) {
            return new Options(components, true, true, 1, 9_999_999, projection, Limits.defaults());
        }
    }

    /** Per-marker counts refer only to the explicit fitting basis. */
    public record Marker(int ordinal, int observed, int missing, long referenceAlleles,
            long otherAlleles, double mean, double multiplier, MarkerStatus status) { }

    /** Owns defensive copies; eigenvalues include signed numerical residual values. */
    public record Result(List<Marker> markers, int[] basisIndices, double[][] basisGram,
            double covarianceScale, double[] eigenvalues, double[][] eigenvectors,
            double[][] scores, List<ScoreStatus> scoreStatus) {
        public Result {
            markers = List.copyOf(markers); basisIndices = basisIndices.clone();
            basisGram = copy(basisGram); eigenvalues = eigenvalues.clone();
            eigenvectors = copy(eigenvectors); scores = copy(scores); scoreStatus = List.copyOf(scoreStatus);
        }
        @Override public int[] basisIndices() { return basisIndices.clone(); }
        @Override public double[][] basisGram() { return copy(basisGram); }
        @Override public double[] eigenvalues() { return eigenvalues.clone(); }
        @Override public double[][] eigenvectors() { return copy(eigenvectors); }
        @Override public double[][] scores() { return copy(scores); }
    }

    /**
     * Fit ordered marker-by-sample hard calls 0/1/2, with 9 for missing, using
     * the supplied ordered, unique sample indices as the fitting population.
     * The remaining samples are projected without contributing allele frequencies.
     */
    public static Result fit(byte[][] markerBySample, int[] basisIndices, Options options) {
        try (BackendContext context = BackendContext.select(BackendPolicy.CPU)) {
            return fit(markerBySample, basisIndices, options, context.backend());
        }
    }

    /** Uses an explicit eigensolver backend; no approximation or ridge repair. */
    public static Result fit(byte[][] genotypes, int[] selected, Options options, ComputeBackend backend) {
        Objects.requireNonNull(genotypes, "genotypes"); Objects.requireNonNull(selected, "basisIndices");
        Objects.requireNonNull(options, "options"); Objects.requireNonNull(backend, "backend");
        require(genotypes.length > 0 && genotypes[0] != null && genotypes[0].length > 0, "Nonempty genotype matrix required");
        int m = genotypes.length, samples = genotypes[0].length, n = selected.length, k = options.components();
        require(n >= 2 && k < n && k <= m, "Require at least two basis samples and components below basis size, no greater than marker count");
        admit(m, samples, n, k, options.limits());
        int[] basis = selected.clone(); boolean[] seen = new boolean[samples];
        for (int index : basis) {
            require(index >= 0 && index < samples && !seen[index], "Invalid or duplicate fitting sample index"); seen[index] = true;
        }
        for (byte[] row : genotypes) {
            require(row != null && row.length == samples, "Ragged genotype matrix");
            for (byte g : row) require(g == 0 || g == 1 || g == 2 || g == 9, "Hard calls must be 0,1,2 or missing 9; no dosage coercion");
        }
        var markers = new ArrayList<Marker>(); double[][] z = new double[m][n];
        double[] gram = new double[n * n];
        for (int s = 0; s < m; s++) {
            interrupted(); int observed = 0; long sum = 0;
            for (int index : basis) if (genotypes[s][index] != 9) { observed++; sum += genotypes[s][index]; }
            int missing = n - observed; long other = 2L * observed - sum;
            double mean = observed == 0 ? 0 : (double) sum / observed;
            double multiplier = observed == 0 ? 0 : 1;
            if (options.useNorm() && observed > 0) {
                double p = options.alternateNormStyle() ? 0.5 * mean : (sum + 1.0) / (2.0 * observed + 2.0);
                double variance = p * (1 - p); if (variance > 0) multiplier = 1 / Math.sqrt(variance);
            }
            long minor = Math.min(sum, other); MarkerStatus status;
            if (observed == 0) status = MarkerStatus.ALL_BASIS_CALLS_MISSING;
            else if (minor == 0) status = MarkerStatus.NO_MINOR_ALLELE;
            else if (minor < options.minimumAlleleCount()) status = MarkerStatus.BELOW_MINIMUM_ALLELE_COUNT;
            else if (missing > options.maximumMissing()) status = MarkerStatus.TOO_MANY_BASIS_CALLS_MISSING;
            else status = MarkerStatus.USED;
            markers.add(new Marker(s, observed, missing, sum, other, mean, multiplier, status));
            if (status != MarkerStatus.USED) continue;
            for (int i = 0; i < n; i++) z[s][i] = genotypes[s][basis[i]] == 9 ? 0 : (genotypes[s][basis[i]] - mean) * multiplier;
            for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++) gram[i * n + j] += z[s][i] * z[s][j];
        }
        double trace = 0; for (int i = 0; i < n; i++) trace += gram[i * n + i];
        double scale = trace / (n - 1); require(scale > 0 && Double.isFinite(scale), "Zero/nonfinite source covariance trace; no PCA result");
        double reciprocalScale = 1 / scale; double[][] gramResult = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++) {
            double value = gram[i * n + j] * reciprocalScale; finite(value, "covariance");
            gramResult[i][j] = gramResult[j][i] = gram[i * n + j] = gram[j * n + i] = value;
        }
        interrupted(); var decomposition = backend.dsyev(gram, n);
        double[] values = decomposition.eigenvalues(), vectors = decomposition.eigenvectors();
        require(values.length == n && vectors.length == n * n, "Incomplete eigensolver return");
        Integer[] order = new Integer[n]; for (int i = 0; i < n; i++) { finite(values[i], "eigenvalue"); order[i] = i; }
        Arrays.sort(order, (a, b) -> Double.compare(values[b], values[a]));
        double[] lambda = new double[n]; double[][] eigenvectors = new double[n][k];
        for (int c = 0; c < n; c++) lambda[c] = values[order[c]];
        for (int c = 0; c < k; c++) {
            require(lambda[c] > 0, "Requested nonpositive principal axis; no repaired eigenvalue");
            for (int i = 0; i < n; i++) { eigenvectors[i][c] = vectors[i * n + order[c]]; finite(eigenvectors[i][c], "eigenvector"); }
        }
        double[][] weights = new double[m][k];
        for (int s = 0; s < m; s++) for (int c = 0; c < k; c++) for (int i = 0; i < n; i++) weights[s][c] += eigenvectors[i][c] * z[s][i];
        double[] fxscale = new double[k];
        for (int i = 0; i < n; i++) for (int c = 0; c < k; c++) {
            double value = dot(genotypes, basis[i], markers, weights, c); fxscale[c] += value * value;
        }
        for (int c = 0; c < k; c++) { require(fxscale[c] > 0 && Double.isFinite(fxscale[c]), "Zero/nonfinite source projection scale"); fxscale[c] = 1 / Math.sqrt(fxscale[c]); }
        double[][] orthogonal = new double[samples][k], scores = new double[samples][k];
        var statuses = new ArrayList<ScoreStatus>();
        for (int sample = 0; sample < samples; sample++) {
            interrupted(); statuses.add(ScoreStatus.COMPUTED);
            for (int c = 0; c < k; c++) orthogonal[sample][c] = fxscale[c] * dot(genotypes, sample, markers, weights, c);
            if (options.projection() == Projection.ORTHOGONAL) { scores[sample] = orthogonal[sample].clone(); continue; }
            double[] normal = new double[k * k], rhs = new double[k]; int observed = 0;
            for (int s = 0; s < m; s++) {
                if (genotypes[s][sample] == 9) continue; observed++;
                double y = projectValue(genotypes[s][sample], markers.get(s));
                for (int a = 0; a < k; a++) {
                    double x = fxscale[a] * weights[s][a]; rhs[a] += x * y;
                    for (int b = 0; b < k; b++) normal[a * k + b] += x * (fxscale[b] * weights[s][b]);
                }
            }
            if (observed <= k) { statuses.set(sample, ScoreStatus.INSUFFICIENT_NONMISSING_MARKERS); continue; }
            scores[sample] = solve(normal, rhs, k, backend);
        }
        if (options.projection() == Projection.LEAST_SQUARES) for (int c = 0; c < k; c++) {
            double numerator = 0, denominator = 0;
            for (int sample : basis) { numerator += scores[sample][c] * orthogonal[sample][c]; denominator += scores[sample][c] * scores[sample][c]; }
            require(denominator > 0 && Double.isFinite(denominator), "Undefined source least-squares score rescaling");
            double correction = numerator / denominator; finite(correction, "least-squares score rescaling");
            for (int sample = 0; sample < samples; sample++) scores[sample][c] *= correction;
        }
        for (int sample = 0; sample < samples; sample++) for (int c = 0; c < k; c++) {
            if (statuses.get(sample) != ScoreStatus.COMPUTED) scores[sample][c] = Double.NaN;
            else finite(scores[sample][c], "projected coordinate");
        }
        return new Result(markers, basis, gramResult, scale, lambda, eigenvectors, scores, statuses);
    }

    private static double[] solve(double[] normal, double[] rhs, int n, ComputeBackend backend) {
        var e = backend.dsyev(normal, n); double[] values = e.eigenvalues(), vectors = e.eigenvectors(), answer = new double[n];
        for (int c = 0; c < n; c++) {
            require(values[c] > 0 && Double.isFinite(values[c]), "Singular/nonfinite least-squares projection; no ridge or pseudoinverse substitution");
            double value = 0; for (int i = 0; i < n; i++) value += vectors[i * n + c] * rhs[i];
            value /= values[c];
            for (int i = 0; i < n; i++) answer[i] += vectors[i * n + c] * value;
        }
        for (double value : answer) finite(value, "least-squares coefficient"); return answer;
    }
    private static double dot(byte[][] data, int sample, List<Marker> markers, double[][] weights, int component) {
        double value = 0;
        for (int s = 0; s < data.length; s++) value += projectValue(data[s][sample], markers.get(s)) * weights[s][component];
        return value;
    }
    private static double projectValue(byte g, Marker marker) {
        if (g == 9 || marker.status() != MarkerStatus.USED) return 0;
        // Preserve fixxrow's multiplication before centering, distinct from fvadjust.
        return g * marker.multiplier() - marker.mean() * marker.multiplier();
    }
    private static void admit(int m, int samples, int n, int k, Limits limits) {
        try {
            long nn = Math.multiplyExact((long)n, n), mn = Math.multiplyExact((long)m, n), mk = Math.multiplyExact((long)m, k);
            long bytes = Math.addExact(Math.multiplyExact(nn, 80), Math.addExact(Math.multiplyExact(mn, 16),
                Math.addExact(Math.multiplyExact(mk, 24), Math.addExact(Math.multiplyExact((long)samples * k, 80), Math.multiplyExact((long)m, 192)))));
            long products = Math.addExact(Math.multiplyExact(2L * m, nn), Math.addExact(Math.multiplyExact(8L * n, nn), Math.multiplyExact(4L * samples, Math.multiplyExact(mk, k))));
            require(nn <= Integer.MAX_VALUE && bytes <= limits.maximumRetainedBytes() && products <= limits.maximumProducts(),
                "Complete smartpca matrix exceeds explicit work/storage limits; supply sufficient resources and reviewed limits, without automatic subsetting");
        } catch (ArithmeticException overflow) { throw new IllegalArgumentException("Complete smartpca dimensions exceed representable work/storage bounds", overflow); }
    }
    private static void finite(double value, String label) { require(Double.isFinite(value), "Nonfinite " + label + "; no complete prepared result"); }
    private static void interrupted() { if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Smartpca prepared calculation interrupted; no complete result"); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static double[][] copy(double[][] source) { return Arrays.stream(source).map(double[]::clone).toArray(double[][]::new); }
}
