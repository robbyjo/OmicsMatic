package org.jlinalg.omics.contact;

import java.util.Arrays;
import java.util.Objects;

/**
 * Prepared upper-triangle diamond statistics from cooltools 0.5.1/0.5.4.
 * Missing sparse pixels contribute zero; NaN weights exclude good-pixel counts
 * and balanced sums while raw sums retain the original stored counts.
 * No Cooler reader, bin balancing or whole-package workflow is implied.
 *
 * <p>Derived from cooltools/api/insulation.py. Copyright (c) 2017 Cooltools
 * developers. MIT licence: see accompanying COOLTOOLS-LICENSE.txt.</p>
 */
public final class CooltoolsDiamond {
    private CooltoolsDiamond() {}

    public record Pixel(int bin1, int bin2, double count) {}

    public record Result(double[] score, double[] nValidPixels, double[] sumBalanced, double[] sumCounts) {
        public Result {
            score = score.clone(); nValidPixels = nValidPixels.clone();
            sumBalanced = sumBalanced.clone(); sumCounts = sumCounts.clone();
            if (score.length != nValidPixels.length || score.length != sumBalanced.length || score.length != sumCounts.length)
                throw new IllegalArgumentException("Different bin axes");
        }
        @Override public double[] score() { return score.clone(); }
        @Override public double[] nValidPixels() { return nValidPixels.clone(); }
        @Override public double[] sumBalanced() { return sumBalanced.clone(); }
        @Override public double[] sumCounts() { return sumCounts.clone(); }
    }

    /** Exact integer-valued denominator; zero balancing weights remain good bins. */
    public static double[] goodPixelCounts(boolean[] badBins, int window, int ignoreDiagonals) {
        Objects.requireNonNull(badBins, "badBins"); checkParameters(window, ignoreDiagonals);
        int size = badBins.length;
        long[] prefix = new long[size + 1];
        for (int i = 0; i < size; i++) prefix[i + 1] = prefix[i] + (badBins[i] ? 0 : 1);
        double[] counts = new double[size];
        for (int center = 0; center < size; center++) {
            int first = (int) Math.max(0L, (long) center - window + 1);
            int end = (int) Math.min(size, (long) center + window);
            long count = 0;
            for (int left = first; left <= center; left++) {
                if (badBins[left]) continue;
                int start = (int) Math.min(end, Math.max(center, (long) left + ignoreDiagonals));
                count = Math.addExact(count, prefix[end] - prefix[start]);
            }
            if (count > 9_007_199_254_740_992L) throw new ArithmeticException("Good-pixel count exceeds exact float64 range");
            counts[center] = count;
        }
        return counts;
    }

    /**
     * weights=null explicitly requests unweighted source behavior. Otherwise
     * weights must be nonnegative finite values or literal NaN bad-bin markers.
     * Stored records may arrive in any order, with finite counts >=0. Repeated
     * coordinates contribute each supplied count, as in the source operator;
     * this method does not reorder, drop or consolidate input records.
     * Accumulation order can differ from NumPy chunking; finite comparisons need
     * a declared floating-point tolerance rather than a bit-identity claim.
     */
    public static Result calculate(int size, double[] weights, Iterable<Pixel> pixels,
                                   int window, int ignoreDiagonals, boolean normByMedian) {
        if (size < 0) throw new IllegalArgumentException("Negative bin count");
        checkParameters(window, ignoreDiagonals); Objects.requireNonNull(pixels, "pixels");
        if (weights != null && weights.length != size) throw new IllegalArgumentException("Different weight axis");
        boolean[] bad = new boolean[size];
        for (int i = 0; weights != null && i < size; i++) {
            bad[i] = Double.isNaN(weights[i]);
            if (!bad[i] && (!Double.isFinite(weights[i]) || weights[i] < 0))
                throw new IllegalArgumentException("Weight must be nonnegative finite or NaN at bin " + i);
        }
        double[] goodPixels = goodPixelCounts(bad, window, ignoreDiagonals);
        double[] raw = new double[size], balanced = new double[size];
        for (Pixel pixel : pixels) {
            Objects.requireNonNull(pixel, "pixel");
            int left = pixel.bin1(), right = pixel.bin2(); double value = pixel.count();
            if (left < 0 || right < left || right >= size || !Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException("Invalid upper-triangle pixel: " + pixel);
            long separation = (long) right - left;
            if (separation < ignoreDiagonals || separation > 2L * (window - 1)) continue;
            int from = (int) Math.max(left, (long) right - window + 1);
            int to = (int) Math.min(right, (long) left + window - 1);
            boolean validBalanced = weights != null && !bad[left] && !bad[right];
            double balancedValue = validBalanced ? value * weights[left] * weights[right] : 0;
            if (!Double.isFinite(balancedValue)) throw new ArithmeticException("Balanced pixel overflow: " + pixel);
            for (int center = from; center <= to; center++) {
                raw[center] += value;
                if (validBalanced) balanced[center] += balancedValue;
                if (!Double.isFinite(raw[center]) || !Double.isFinite(balanced[center]))
                    throw new ArithmeticException("Diamond sum overflow at bin " + center);
            }
        }
        double[] score = new double[size];
        for (int i = 0; i < size; i++) score[i] = (weights == null ? raw[i] : balanced[i]) / goodPixels[i];
        if (normByMedian) {
            double median = nanMedian(score);
            for (int i = 0; i < size; i++) score[i] /= median;
        }
        return new Result(score, goodPixels, balanced, raw);
    }

    /** Original region transform: zero and nonfinite log2 values become NaN. */
    public static double[] log2Track(double[] score) {
        double[] result = Objects.requireNonNull(score, "score").clone();
        for (int i = 0; i < result.length; i++) {
            double value = result[i] == 0 ? Double.NaN : Math.log(result[i]) / Math.log(2);
            result[i] = Double.isFinite(value) ? value : Double.NaN;
        }
        return result;
    }

    private static void checkParameters(int window, int ignoreDiagonals) {
        if (window < 1 || ignoreDiagonals < 0) throw new IllegalArgumentException("Positive window and nonnegative ignoreDiagonals required");
    }

    private static double nanMedian(double[] values) {
        double[] present = Arrays.stream(values).filter(value -> !Double.isNaN(value)).toArray();
        if (present.length == 0) return Double.NaN;
        Arrays.sort(present); int middle = present.length / 2;
        if (present.length % 2 != 0) return present[middle];
        double lower = present[middle - 1], upper = present[middle], sum = lower + upper;
        if (Double.isFinite(lower) && Double.isFinite(upper) && !Double.isFinite(sum))
            throw new ArithmeticException("Finite central scores overflow during median addition; no complete insulation result");
        return sum / 2;
    }
}
