package org.jlinalg.omics.contact;

import java.util.Arrays;
import java.util.Objects;

/**
 * Native translation of cooltools 0.5.1/0.5.4 find_peak_prominence.
 * NaN gaps, strict extrema, asymmetric range endpoints and tied global peaks
 * retain the selected source behavior. This is not a whole-package workflow.
 *
 * <p>Derived from cooltools/lib/peaks.py. Copyright (c) 2017 Cooltools developers.
 * MIT licence: see the accompanying COOLTOOLS-LICENSE.txt.</p>
 */
public final class CooltoolsPeakProminence {
    private CooltoolsPeakProminence() {}

    public record Result(int[] positions, double[] prominences) {
        public Result {
            positions = positions.clone();
            prominences = prominences.clone();
            if (positions.length != prominences.length) throw new IllegalArgumentException("Different peak axes");
        }
        @Override public int[] positions() { return positions.clone(); }
        @Override public double[] prominences() { return prominences.clone(); }
    }

    /** maxDistance=null selects the original full-array default; explicit distances must be positive. */
    public static Result find(double[] values, Integer maxDistance) {
        Objects.requireNonNull(values, "values");
        if (maxDistance != null && maxDistance < 1) throw new IllegalArgumentException("Positive maxDistance required");
        final int distance = maxDistance == null ? values.length : maxDistance;
        int[] finitePositions = new int[values.length];
        int count = 0;
        for (int i = 0; i < values.length; i++) if (!Double.isNaN(values[i])) finitePositions[count++] = i;
        int[] maxima = new int[count], minima = new int[count];
        int maxCount = 0, minCount = 0;
        for (int i = 1; i + 1 < count; i++) {
            int previous = finitePositions[i - 1], current = finitePositions[i], next = finitePositions[i + 1];
            if (values[previous] < values[current] && values[current] > values[next]) maxima[maxCount++] = current;
            if (values[previous] > values[current] && values[current] < values[next]) minima[minCount++] = current;
        }
        maxima = Arrays.copyOf(maxima, maxCount);
        double[] prominences = new double[maxCount];
        Arrays.fill(prominences, Double.NaN);
        boolean globalWasAssigned = false;
        for (int index = 0; index < maxCount; index++) {
            int position = maxima[index], left = -1, right = -1;
            for (int j = position - 1; j >= 0; j--) {
                if (values[j] > values[position] || position - j > distance) { left = j; break; }
            }
            for (int j = position + 1; j < values.length; j++) {
                if (values[j] > values[position] || j - position > distance) { right = j; break; }
            }
            double leftProminence = left < 0 ? Double.NaN : values[position] - nanMin(values, left, position);
            double rightProminence = right < 0 ? Double.NaN : values[position] - nanMin(values, position, right);
            prominences[index] = nanMin(leftProminence, rightProminence);
            // The source adjusts only the FIRST peak with neither a higher left
            // nor a higher right peak. Later tied maxima can remain NaN.
            if (left == -1 && right == -1 && !globalWasAssigned) {
                globalWasAssigned = true;
                double lowest = Double.NaN;
                boolean foundMinimum = false;
                for (int j = 0; j < minCount; j++) {
                    int candidate = minima[j];
                    if (candidate >= (long) position - distance && candidate < (long) position + distance) {
                        lowest = foundMinimum ? nanMin(lowest, values[candidate]) : values[candidate];
                        foundMinimum = true;
                    }
                }
                if (!foundMinimum) {
                    int from = (int) Math.max(0L, (long) position - distance);
                    int to = (int) Math.min(values.length, (long) position + distance);
                    lowest = nanMin(values, from, to);
                }
                prominences[index] = values[position] - lowest;
            }
        }
        return new Result(maxima, prominences);
    }

    private static double nanMin(double first, double second) {
        return Double.isNaN(first) ? second : Double.isNaN(second) ? first : Math.min(first, second);
    }

    private static double nanMin(double[] values, int from, int to) {
        double result = Double.NaN;
        for (int i = from; i < to; i++) result = nanMin(result, values[i]);
        return result;
    }
}
