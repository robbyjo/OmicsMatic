/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.util.ArrayList;
import java.util.List;

/** All-pairs Tukey-Kramer comparisons for independent Gaussian, equal-variance group means. */
public final class TukeyKramer {
    /** Bounds the complete quadratic result family; comparisons are never truncated. */
    public static final long MAX_COMPARISONS = 1_000_000L;

    /** The position is the caller's declared group order; no labels are sorted. */
    public record Group(int position, int size, double mean) { }

    /** Difference and confidence limits are second-group mean minus first-group mean. */
    public record Comparison(int firstGroup, int secondGroup, double difference,
            double standardError, double studentizedRange, double adjustedPValue,
            double lowerConfidenceLimit, double upperConfidenceLimit) { }

    /** Simultaneous confidence coverage is for all pairs of all declared groups. */
    public record Result(List<Group> groups, double residualStandardDeviation,
            double residualDegreesOfFreedom, double confidenceLevel,
            double criticalStudentizedRange, List<Comparison> comparisons,
            String probabilityMethod, int positiveTailEvaluations) {
        public Result {
            groups = List.copyOf(groups);
            comparisons = List.copyOf(comparisons);
        }
    }

    private TukeyKramer() { }

    /** Ordinary one-way fit and all-pairs comparisons, using 95% simultaneous intervals. */
    public static Result compare(double[][] groups) { return compare(groups, 0.95); }

    /**
     * Fits the ordinary one-way model to complete, finite independent observations.
     * Each group needs at least one observation, pooled residual degrees of freedom
     * must be at least two, and pooled residual variation must be positive.
     * Missing values are rejected, never removed or imputed. Constant individual
     * groups and unequal sample sizes are supported when pooled variation is positive.
     */
    public static Result compare(double[][] groups, double confidenceLevel) {
        if (groups == null) throw new IllegalArgumentException("groups are required");
        validateFamily(groups.length, confidenceLevel);
        double[][] samples = new double[groups.length][];
        double[] means = new double[groups.length];
        int[] sizes = new int[groups.length];
        long observations = 0;
        for (int i = 0; i < groups.length; i++) {
            samples[i] = StatisticsSupport.sample(groups[i], 1, "group " + i);
            sizes[i] = samples[i].length;
            observations = Math.addExact(observations, sizes[i]);
            means[i] = StatisticsSupport.mean(samples[i]);
            if (!Double.isFinite(means[i])) throw new ArithmeticException("Group mean is not numerically finite");
        }
        double degrees = observations - (double) groups.length;
        if (degrees < 2) throw new IllegalArgumentException("Tukey distribution requires at least two pooled residual degrees of freedom");
        // Accumulate the residual SD directly, avoiding an unnecessary square of
        // large/small data and dividing before hypot can overflow its final norm.
        double divisor = Math.sqrt(degrees), residualSd = 0;
        for (int i = 0; i < samples.length; i++) for (double value : samples[i]) {
            double difference = value - means[i];
            if (!Double.isFinite(difference)) throw new ArithmeticException("Residual difference exceeds numeric capacity");
            residualSd = Math.hypot(residualSd, difference / divisor);
        }
        return compareMeans(means, sizes, residualSd, degrees, confidenceLevel);
    }

    /**
     * Compares independent mean estimates with explicitly supplied shared residual
     * SD and degrees of freedom. These must describe the same fitted population
     * and complete comparison family. This does not infer correlated marginal
     * means, a mixed/repeated-measures model or pair-specific Welch variances.
     */
    public static Result compareMeans(double[] means, int[] sizes, double residualSd,
            double residualDegreesOfFreedom, double confidenceLevel) {
        if (means == null || sizes == null || means.length != sizes.length)
            throw new IllegalArgumentException("Matching means and sample sizes are required");
        validateFamily(means.length, confidenceLevel);
        means = means.clone();
        sizes = sizes.clone();
        if (!(residualSd > 0) || !Double.isFinite(residualSd))
            throw new IllegalArgumentException("Pooled residual SD must be positive and finite; no degenerate result is admitted");
        if (!(residualDegreesOfFreedom >= 2) || !Double.isFinite(residualDegreesOfFreedom))
            throw new IllegalArgumentException("Pooled residual degrees of freedom must be finite and at least two");
        var groupRows = new ArrayList<Group>(means.length);
        for (int i = 0; i < means.length; i++) {
            if (!Double.isFinite(means[i]) || sizes[i] < 1)
                throw new IllegalArgumentException("Finite means and positive sample sizes are required");
            groupRows.add(new Group(i, sizes[i], means[i]));
        }
        var distribution = new StudentizedRangeTail(means.length, residualDegreesOfFreedom);
        double critical = distribution.critical(confidenceLevel);
        if (!(critical > 0) || !Double.isFinite(critical))
            throw new ArithmeticException("Studentized-range critical value is unresolved; no comparisons admitted");
        int pairs = Math.toIntExact((long) means.length * (means.length - 1) / 2);
        var comparisons = new ArrayList<Comparison>(pairs);
        for (int first = 0; first < means.length - 1; first++) {
            for (int second = first + 1; second < means.length; second++) {
                double difference = means[second] - means[first];
                double factor = Math.sqrt(0.5 / sizes[first] + 0.5 / sizes[second]);
                // Divide first so a representable subnormal SD is not rounded to
                // zero merely by forming the smaller Q-denominator product.
                double statistic = Math.abs(difference / residualSd) / factor;
                double pValue = distribution.probability(statistic);
                double standardError = residualSd * Math.sqrt(1.0 / sizes[first] + 1.0 / sizes[second]);
                double width = residualSd * (critical * factor);
                double lower = difference - width, upper = difference + width;
                if (!Double.isFinite(difference) || !Double.isFinite(standardError)
                        || !Double.isFinite(lower) || !Double.isFinite(upper)
                        || !Double.isFinite(statistic) || !Double.isFinite(pValue)
                        || pValue < 0 || pValue > 1)
                    throw new ArithmeticException("Unresolved or unrepresentable Tukey-Kramer comparison; no partial family admitted");
                comparisons.add(new Comparison(first, second, difference, standardError,
                    statistic, pValue, lower, upper));
            }
        }
        return new Result(groupRows, residualSd, residualDegreesOfFreedom,
            confidenceLevel, critical, comparisons, distribution.method(), distribution.refinements());
    }

    private static void validateFamily(int groups, double confidenceLevel) {
        if (groups < 2) throw new IllegalArgumentException("Declare at least two groups");
        if ((long) groups * (groups - 1) / 2 > MAX_COMPARISONS)
            throw new IllegalArgumentException("Complete comparison family exceeds one million pairs; groups are not truncated");
        if (!(confidenceLevel > 0 && confidenceLevel < 1) || !Double.isFinite(confidenceLevel))
            throw new IllegalArgumentException("Confidence level must be strictly between zero and one");
    }
}
