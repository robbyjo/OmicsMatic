/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Derived scientific semantics: Squidpy, Copyright (c) 2021 Theis Lab, BSD-3-Clause.
 * Original notices are included under META-INF/licenses/SQUIDPY-*.txt. */
package org.jlinalg.squidpy;

import java.util.Arrays;
import jdistlib.Normal;

/**
 * Original Squidpy float64 Moran/Geary point statistics and normality moments.
 * Requires a prepared square CSR graph with sorted unique columns in each row.
 * Native bounds are 100,000 observations and 1,000,000 stored graph entries.
 * This component does not build a spatial graph, draw permutations, select genes,
 * adjust multiple tests, reproduce a whole workflow or repair input values.
 */
public final class SquidpyAutocorrelation {
    private SquidpyAutocorrelation() { }

    /** Historical Geary normality variance is retained explicitly by edition. */
    public enum Edition { SQUIDPY_1_1_2, SQUIDPY_1_2_2, SQUIDPY_1_8_4 }
    public enum Statistic { MORAN, GEARY }
    public enum Status { COMPLETE, UNDEFINED_STATISTIC, UNDEFINED_ANALYTIC_VARIANCE }

    /** Undefined analytic fields are not probabilities and must not be admitted as estimates. */
    public record Result(Edition edition, Statistic statistic, double score,
            double expected, double normalityVariance, double normalityPValue,
            boolean rowL1Normalized, boolean twoTailed, Status status, String condition) { }

    /**
     * Computes one feature without changing any input array. L1 normalization
     * divides by the sum of absolute row weights, including for signed graphs.
     * Squidpy 1.1.2/1.2.2 reuse Moran's variance for Geary; 1.8.4 uses the corrected
     * Geary formula. No newer formula is substituted into an older edition.
     * Source one-sided positive-tail subtraction (1 - normal CDF) is retained,
     * including rounded-to-zero p-values. For degenerate statistics/variance the
     * API returns an explicit undefined status; it never emulates indeterminate
     * memory cells from an original numpy.empty probability array.
     */
    public static Result compute(Edition edition, Statistic statistic, double[] values,
            int[] rowPointers, int[] columns, double[] suppliedWeights,
            boolean rowL1Normalize, boolean twoTailed) {
        if (edition == null || statistic == null || values == null || rowPointers == null
                || columns == null || suppliedWeights == null)
            throw new IllegalArgumentException("Declare edition, statistic, values and CSR arrays");
        int n = values.length;
        if (n < 3 || n > 100_000 || columns.length > 1_000_000
                || suppliedWeights.length != columns.length || rowPointers.length != n + 1
                || rowPointers[0] != 0 || rowPointers[n] != columns.length)
            throw new IllegalArgumentException("Invalid CSR dimensions or bounded native capacity exceeded");
        for (double value : values)
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite feature input; no repair");
        double[] weights = suppliedWeights.clone();
        for (int i = 0; i < n; i++) {
            if (rowPointers[i] < 0 || rowPointers[i] > rowPointers[i + 1]
                    || rowPointers[i + 1] > columns.length)
                throw new IllegalArgumentException("Invalid CSR row pointers");
            int previous = -1;
            double norm = 0;
            for (int k = rowPointers[i]; k < rowPointers[i + 1]; k++) {
                if (columns[k] <= previous || columns[k] >= n || !Double.isFinite(weights[k]))
                    throw new IllegalArgumentException("Require finite CSR weights and sorted unique in-range columns");
                previous = columns[k];
                norm += Math.abs(weights[k]);
            }
            if (rowL1Normalize && norm != 0)
                for (int k = rowPointers[i]; k < rowPointers[i + 1]; k++) weights[k] /= norm;
        }
        double expected = statistic == Statistic.MORAN ? -1.0 / (n - 1) : 1.0;
        double mean = 0;
        for (double value : values) mean += value;
        mean /= n;
        double[] centered = new double[n];
        double sumSquares = 0;
        for (int i = 0; i < n; i++) {
            centered[i] = values[i] - mean;
            sumSquares += centered[i] * centered[i];
        }
        double[] rowSums = new double[n], columnSums = new double[n];
        double s0 = 0, s1 = 0, numerator = 0;
        for (int i = 0; i < n; i++) {
            for (int k = rowPointers[i]; k < rowPointers[i + 1]; k++) {
                int j = columns[k];
                double w = weights[k];
                s0 += w;
                rowSums[i] += w;
                columnSums[j] += w;
                int reverse = Arrays.binarySearch(columns, rowPointers[j], rowPointers[j + 1], i);
                double transposed = reverse >= 0 ? weights[reverse] : 0;
                s1 += w * w + w * transposed;
                if (statistic == Statistic.MORAN) numerator += w * centered[i] * centered[j];
                else {
                    double difference = values[i] - values[j];
                    numerator += w * difference * difference;
                }
            }
        }
        double score = statistic == Statistic.MORAN
                ? n * numerator / (s0 * sumSquares)
                : (n - 1.0) * numerator / (2 * s0 * sumSquares);
        double s2 = 0;
        for (int i = 0; i < n; i++) {
            double total = rowSums[i] + columnSums[i];
            s2 += total * total;
        }
        double s02 = s0 * s0;
        double variance;
        if (statistic == Statistic.GEARY && edition == Edition.SQUIDPY_1_8_4)
            variance = ((2 * s1 + s2) * (n - 1.0) - 4 * s02) / (2 * (n + 1.0) * s02);
        else
            variance = ((double) n * n * s1 - n * s2 + 3 * s02)
                    / ((n - 1.0) * (n + 1.0) * s02) - Math.pow(1.0 / (n - 1), 2);
        Status status;
        double probability;
        String condition;
        if (!Double.isFinite(score)) {
            status = Status.UNDEFINED_STATISTIC;
            probability = Double.NaN;
            condition = "Original point statistic is undefined/nonfinite; no statistic or analytic probability admitted";
        } else if (!(variance > 0) || !Double.isFinite(variance)) {
            status = Status.UNDEFINED_ANALYTIC_VARIANCE;
            probability = Double.NaN;
            condition = "Nonpositive/nonfinite analytic variance; point statistic retained, no analytic probability admitted";
        } else {
            double z = (score - expected) / Math.sqrt(variance);
            probability = z > 0 ? 1 - Normal.cumulative(z, 0, 1, true, false)
                    : Normal.cumulative(z, 0, 1, true, false);
            if (twoTailed) probability *= 2;
            status = Status.COMPLETE;
            condition = statistic == Statistic.GEARY && edition != Edition.SQUIDPY_1_8_4
                    ? "Historical Squidpy Geary variance convention retained; no current-formula substitution"
                    : "Original declared-edition point statistic and normality convention";
        }
        return new Result(edition, statistic, score, expected, variance, probability,
                rowL1Normalize, twoTailed, status, condition);
    }
}
