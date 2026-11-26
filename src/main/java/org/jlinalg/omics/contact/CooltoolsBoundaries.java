package org.jlinalg.omics.contact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prepared single-window boundary strengths from cooltools 0.5.1/0.5.4.
 * The valid-pixel maximum spans all regions; distance masking preserves the
 * original implicit edge distances. Arrays use the caller's row axis; the
 * separate sourceOutputRowOrder preserves the selected API's output ordering.
 * Fixed strength thresholds use the source API's inclusive comparison.
 *
 * <p>Derived from cooltools/api/insulation.py and lib/numutils.py.
 * Copyright (c) 2017 Cooltools developers. MIT licence: see the accompanying
 * COOLTOOLS-LICENSE.txt. Cooler IO, automatic histogram thresholds, compartment
 * eigenvectors, dots and whole author pipelines are separate.</p>
 */
public final class CooltoolsBoundaries {
    private CooltoolsBoundaries() {}

    public record Bin(String region, long start, double log2Insulation, double nValidPixels, boolean badBin) {}

    public record Result(double[] strength, boolean[] isBoundary, int[] distanceToBad,
                         boolean[] eligible, double minimumValidPixels, int[] sourceOutputRowOrder) {
        public Result {
            strength = strength.clone(); isBoundary = isBoundary.clone();
            distanceToBad = distanceToBad.clone(); eligible = eligible.clone(); sourceOutputRowOrder = sourceOutputRowOrder.clone();
            if (strength.length != isBoundary.length || strength.length != distanceToBad.length
                || strength.length != eligible.length || strength.length != sourceOutputRowOrder.length)
                throw new IllegalArgumentException("Different boundary axes");
        }
        @Override public double[] strength() { return strength.clone(); }
        @Override public boolean[] isBoundary() { return isBoundary.clone(); }
        @Override public int[] distanceToBad() { return distanceToBad.clone(); }
        @Override public boolean[] eligible() { return eligible.clone(); }
        @Override public int[] sourceOutputRowOrder() { return sourceOutputRowOrder.clone(); }
    }

    /** Original default min-side distance, including the implicit positions -1 and N at the edges. */
    public static int[] distanceToBad(boolean[] mask) {
        Objects.requireNonNull(mask, "mask");
        int[] distance = new int[mask.length];
        int previous = -1;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) previous = i;
            distance[i] = i - previous;
        }
        int next = mask.length;
        for (int i = mask.length - 1; i >= 0; i--) {
            if (mask[i]) next = i;
            distance[i] = Math.min(distance[i], next - i);
        }
        return distance;
    }

    /** Explicit bounded numeric profile; NaN scores remain declared missing scores. */
    public static Result call(List<Bin> bins, double minFractionValid, int minDistanceBad, double strengthThreshold) {
        Objects.requireNonNull(bins, "bins");
        if (bins.isEmpty()) throw new IllegalArgumentException("Nonempty bin table required");
        if (!Double.isFinite(minFractionValid) || minFractionValid < 0 || minFractionValid > 1
            || minDistanceBad < 0 || !Double.isFinite(strengthThreshold) || strengthThreshold < 0)
            throw new IllegalArgumentException("Explicit valid fraction, distance and numeric strength threshold required");
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        double maximumValid = 0;
        for (int i = 0; i < bins.size(); i++) {
            Bin bin = Objects.requireNonNull(bins.get(i), "bin");
            if (bin.region() == null || bin.region().isEmpty() || bin.start() < 0
                || (!Double.isNaN(bin.log2Insulation()) && !Double.isFinite(bin.log2Insulation()))
                || !Double.isFinite(bin.nValidPixels()) || bin.nValidPixels() < 0)
                throw new IllegalArgumentException("Invalid prepared bin at input row " + i);
            maximumValid = Math.max(maximumValid, bin.nValidPixels());
            groups.computeIfAbsent(bin.region(), ignored -> new ArrayList<>()).add(i);
        }
        double minimum = maximumValid * minFractionValid;
        int[] sourceOrder = new int[bins.size()];
        if (minDistanceBad == 0) {
            for (int i = 0; i < sourceOrder.length; i++) sourceOrder[i] = i;
        } else {
            // Before saving sorting_order, the source concatenates groupby
            // results in sorted region-name order when distance masking is on.
            List<String> names = new ArrayList<>(groups.keySet());
            names.sort(CooltoolsBoundaries::compareCodePoints);
            int offset = 0;
            for (String name : names) for (int row : groups.get(name)) sourceOrder[offset++] = row;
        }
        double[] strength = new double[bins.size()]; Arrays.fill(strength, Double.NaN);
        boolean[] boundary = new boolean[bins.size()], eligible = new boolean[bins.size()];
        int[] distances = new int[bins.size()];
        for (List<Integer> indices : groups.values()) {
            boolean[] bad = new boolean[indices.size()];
            for (int i = 0; i < indices.size(); i++) {
                bad[i] = bins.get(indices.get(i)).badBin();
            }
            // The original calculates distances in each group's INPUT order,
            // then sorts by start for peak calling. Preserve that distinction.
            int[] distance = distanceToBad(bad);
            for (int i = 0; i < indices.size(); i++) distances[indices.get(i)] = distance[i];
            indices.sort(Comparator.comparingLong(index -> bins.get(index).start()));
            for (int i = 0; i < indices.size(); i++) {
                Bin bin = bins.get(indices.get(i));
                if (i > 0 && bins.get(indices.get(i - 1)).start() == bin.start())
                    throw new IllegalArgumentException("Duplicate region/start bin: " + bin.region() + ":" + bin.start());
            }
            double[] selected = new double[indices.size()];
            int[] originalRows = new int[indices.size()]; int size = 0;
            for (int i = 0; i < indices.size(); i++) {
                int original = indices.get(i); Bin bin = bins.get(original);
                if (bin.nValidPixels() >= minimum && (minDistanceBad == 0 || distances[original] >= minDistanceBad)) {
                    eligible[original] = true; selected[size] = -bin.log2Insulation(); originalRows[size++] = original;
                }
            }
            var peaks = CooltoolsPeakProminence.find(Arrays.copyOf(selected, size), null);
            int[] positions = peaks.positions(); double[] prominences = peaks.prominences();
            for (int i = 0; i < positions.length; i++) strength[originalRows[positions[i]]] = prominences[i];
        }
        for (int i = 0; i < bins.size(); i++) boundary[i] = strength[i] >= strengthThreshold;
        return new Result(strength, boundary, distances, eligible, minimum, sourceOrder);
    }

    private static int compareCodePoints(String left, String right) {
        int i = 0, j = 0;
        while (i < left.length() && j < right.length()) {
            int first = left.codePointAt(i), second = right.codePointAt(j);
            if (first != second) return Integer.compare(first, second);
            i += Character.charCount(first); j += Character.charCount(second);
        }
        return Integer.compare(left.length() - i, right.length() - j);
    }
}
