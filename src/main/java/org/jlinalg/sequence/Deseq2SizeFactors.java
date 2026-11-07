/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Native median-location subset of DESeq2 estimateSizeFactorsForMatrix, referenced
 * to DESeq2 1.52.0. Rows are genes and columns are samples. Raw counts must be
 * nonnegative integers; quantifier estimates are never rounded. No dispersion,
 * negative-binomial fit, hypothesis test, VST, rlog or shrinkage is performed.
 * The caller must not mutate input arrays during a call.
 */
public final class Deseq2SizeFactors {
    private Deseq2SizeFactors() { }
    /** Source missing numeric value; distinguish with isMissing, not equality. */
    public static final double MISSING = Double.longBitsToDouble(0x7ff80000000007a2L);
    /** Supported low-level estimators; DESeq2's iterative estimator is separate. */
    public enum Type { RATIO, POSCOUNTS }
    /** Declared matrix, arithmetic-visit and temporary-vector capacities. */
    public record Limits(long matrixEntries, long work, int scratchEntries) {
        public Limits {
            if (matrixEntries < 1 || work < 1 || scratchEntries < 1)
                throw new IllegalArgumentException("Positive normalization capacities are required");
        }
        public static Limits defaults() { return new Limits(20_000_000, 100_000_000, 2_000_000); }
    }
    /** Immutable factors, row log geometric means, selection counts and warnings. */
    public record Result(double[] factors, double[] logGeometricMeans, int[] selectedPerSample,
                         boolean suppliedGeometricMeans, List<String> warnings) {
        public Result {
            factors = factors.clone(); logGeometricMeans = logGeometricMeans.clone();
            selectedPerSample = selectedPerSample.clone(); warnings = List.copyOf(warnings);
        }
        @Override public double[] factors() { return factors.clone(); }
        @Override public double[] logGeometricMeans() { return logGeometricMeans.clone(); }
        @Override public int[] selectedPerSample() { return selectedPerSample.clone(); }
        /** Whether factors meet the positive finite prerequisite for a later fit. */
        public boolean usableForFit() {
            for (double factor : factors) if (!(factor > 0) || !Double.isFinite(factor)) return false;
            return true;
        }
    }
    public static boolean isMissing(double value) {
        return Double.isNaN(value) && (Double.doubleToRawLongBits(value) & 0x0007ffffffffffffL) == 0x7a2L;
    }
    public static Result estimate(int[][] counts, Type type) {
        return estimate(counts, type, null, null, Limits.defaults(), () -> false);
    }
    /**
     * A null control vector selects every gene; otherwise indices are zero-based,
     * ordered, and may repeat, as with valid positive numeric R indices. An empty
     * control vector yields source missing factors. Supplied geometric means must
     * match rows; zero/infinity and NaN exclude rows. All-infinite log means cause
     * the source error, whereas an all-NaN vector yields missing factors.
     * A negative mean is rejected at input admission.
     * Supplied means trigger geometric centering of factors, even for poscounts.
     * With computed poscounts means, log positive counts are divided by ALL samples.
     */
    public static Result estimate(int[][] counts, Type type, double[] geometricMeans,
                                  int[] controlGenes, Limits limits, BooleanSupplier cancelled) {
        Objects.requireNonNull(counts); Objects.requireNonNull(type);
        Objects.requireNonNull(limits); Objects.requireNonNull(cancelled); check(cancelled);
        int samples = dimensions(counts, limits, cancelled), genes = counts.length;
        int selected = controlGenes == null ? genes : controlGenes.length;
        if (genes > limits.scratchEntries || selected > limits.scratchEntries)
            throw new IllegalArgumentException("Normalization temporary-vector capacity exceeded");
        long visits = (long) genes * samples + (long) selected * samples;
        if (visits > limits.work)
            throw new IllegalArgumentException("Normalization arithmetic-visit capacity exceeded");
        if (controlGenes != null) for (int gene : controlGenes) {
            check(cancelled);
            if (gene < 0 || gene >= genes)
                throw new IllegalArgumentException("Control gene index outside supplied gene population");
        }
        boolean supplied = geometricMeans != null;
        if (supplied && geometricMeans.length != genes)
            throw new IllegalArgumentException("geoMeans should be as long as the number of rows of counts");
        double[] logs = new double[genes];
        boolean anyFinite = false, anyNaN = false;
        for (int g = 0; g < genes; g++) {
            check(cancelled);
            if (supplied) {
                if (geometricMeans[g] < 0)
                    throw new IllegalArgumentException("Geometric means must be nonnegative; no repair performed");
                logs[g] = Math.log(geometricMeans[g]);
            } else {
                double sum = 0, correction = 0; boolean zero = false, positive = false;
                for (int s = 0; s < samples; s++) {
                    if ((s & 4095) == 0) check(cancelled);
                    int count = counts[g][s];
                    if (count == 0) { zero = true; continue; }
                    positive = true;
                    double value = Math.log(count), next = sum + value;
                    correction += Math.abs(sum) >= Math.abs(value) ? (sum-next)+value : (value-next)+sum;
                    sum = next;
                }
                logs[g] = !positive || (type == Type.RATIO && zero) ? Double.NEGATIVE_INFINITY
                        : (sum + correction) / samples;
            }
            anyFinite |= Double.isFinite(logs[g]); anyNaN |= Double.isNaN(logs[g]);
        }
        if (!anyFinite && !anyNaN) {
            throw new IllegalArgumentException("every gene contains at least one zero, cannot compute log geometric means");
        }
        double[] factors = new double[samples], ratios = new double[selected];
        int[] selectedPerSample = new int[samples];
        boolean missing = false;
        for (int s = 0; s < samples; s++) {
            check(cancelled); int size = 0;
            for (int k = 0; k < selected; k++) {
                if ((k & 4095) == 0) check(cancelled);
                int g = controlGenes == null ? k : controlGenes[k];
                if (Double.isFinite(logs[g]) && counts[g][s] > 0)
                    ratios[size++] = Math.log(counts[g][s]) - logs[g];
            }
            selectedPerSample[s] = size;
            if (size == 0) { factors[s] = MISSING; missing = true; continue; }
            Arrays.sort(ratios, 0, size); check(cancelled);
            double median = (size & 1) == 1 ? ratios[size / 2]
                    : (ratios[size / 2 - 1] + ratios[size / 2]) / 2;
            factors[s] = Math.exp(median);
        }
        if (supplied) {
            if (missing) Arrays.fill(factors, MISSING);
            else {
                double center = Math.exp(meanLogs(factors));
                for (int s = 0; s < samples; s++) factors[s] /= center;
            }
        }
        List<String> warnings = new ArrayList<>();
        if (missing) warnings.add("UNDEFINED_SIZE_FACTOR: no eligible positive count for a sample; source NA retained. Supplied geometric means propagate NA to all factors. Review zeros and control selection before a downstream fit.");
        for (double factor : factors) if (!isMissing(factor) && (!(factor > 0) || !Double.isFinite(factor))) {
            warnings.add("NONFINITE_OR_ZERO_SIZE_FACTOR: source overflow, underflow or indeterminate centering retained. Do not fit a differential model with these factors; review supplied geometric means."); break;
        }
        check(cancelled);
        return new Result(factors, logs, selectedPerSample, supplied, warnings);
    }
    /** Divide by factors without replacing undefined/zero/nonfinite source values. */
    public static double[][] normalizedCounts(int[][] counts, Result result, Limits limits,
                                              BooleanSupplier cancelled) {
        Objects.requireNonNull(result); Objects.requireNonNull(limits); Objects.requireNonNull(cancelled);
        int samples = dimensions(counts, limits, cancelled);
        if (samples != result.factors.length || counts.length != result.logGeometricMeans.length)
            throw new IllegalArgumentException("Count and normalization-result dimensions differ");
        if ((long) counts.length * samples > limits.work)
            throw new IllegalArgumentException("Normalization arithmetic-visit capacity exceeded");
        double[][] values = new double[counts.length][samples];
        for (int g = 0; g < counts.length; g++) {
            check(cancelled);
            for (int s = 0; s < samples; s++) {
                if ((s & 4095) == 0) check(cancelled);
                values[g][s] = isMissing(result.factors[s]) ? MISSING : counts[g][s] / result.factors[s];
            }
        }
        return values;
    }
    private static int dimensions(int[][] counts, Limits limits, BooleanSupplier cancelled) {
        Objects.requireNonNull(counts); check(cancelled);
        if (counts.length == 0 || counts[0] == null || counts[0].length == 0)
            throw new IllegalArgumentException("Require nonempty gene-by-sample raw counts");
        int samples = counts[0].length;
        if ((long) counts.length * samples > limits.matrixEntries)
            throw new IllegalArgumentException("Normalization matrix-entry capacity exceeded");
        for (int[] row : counts) {
            check(cancelled);
            if (row == null || row.length != samples) throw new IllegalArgumentException("Ragged counts matrix");
            for (int s = 0; s < samples; s++) {
                if ((s & 4095) == 0) check(cancelled);
                if (row[s] < 0) throw new IllegalArgumentException("Nonnegative integer raw counts required");
            }
        }
        return samples;
    }
    private static double meanLogs(double[] factors) {
        double sum = 0, correction = 0; boolean positiveInfinity = false, negativeInfinity = false;
        for (double factor : factors) {
            double value = Math.log(factor);
            if (Double.isNaN(value)) return Double.NaN;
            if (value == Double.POSITIVE_INFINITY) { positiveInfinity = true; continue; }
            if (value == Double.NEGATIVE_INFINITY) { negativeInfinity = true; continue; }
            double next = sum + value;
            correction += Math.abs(sum) >= Math.abs(value) ? (sum-next)+value : (value-next)+sum;
            sum = next;
        }
        if (positiveInfinity && negativeInfinity) return Double.NaN;
        if (positiveInfinity) return Double.POSITIVE_INFINITY;
        if (negativeInfinity) return Double.NEGATIVE_INFINITY;
        return (sum + correction) / factors.length;
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Native DESeq2 size-factor calculation cancelled");
    }
}
