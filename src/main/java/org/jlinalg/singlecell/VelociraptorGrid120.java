/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Arithmetic follows velociraptor 1.20.0 R/gridVectors.R, authored by Aaron Lun.
 * Original package: Copyright (c) 2020 Kevin Rue-Albrecht, MIT.
 * Original notice: META-INF/LICENSE-velociraptor120.txt.
 */
package org.jlinalg.singlecell;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native two-dimensional grid summarization of supplied velocity vectors.
 * This computes no RNA velocities, embedding, latent time or author settings.
 * <p>The native domain has finite two-column inputs, nondegenerate finite axis
 * ranges, positive integer resolution and nonoverflowing group sums/lengths.
 * Unsupported inputs refuse the whole calculation; use the original R workflow
 * for other domains. Every supplied row participates in the original first-seen
 * group ordering and first-matching-cell labels. Original truncation retains
 * any terminal-bin category.
 * <p>Scaling follows the original multiplication/division order. Zero median
 * vector length can produce NaN or infinity; these values are returned with
 * {@link Result#requiresNonFiniteReview()} and are never replaced with zero.
 */
public final class VelociraptorGrid120 {
    private VelociraptorGrid120() { }

    /** Complete arithmetic intermediates, with zero-based accessors and one-based group IDs. */
    public static final class Result {
        private final int resolution;
        private final boolean scaled;
        private final double[][] start;
        private final double[][] end;
        private final double[][] vectors;
        private final double[][] limits;
        private final double[] delta;
        private final int[][] categories;
        private final int[] groups;
        private final int[] binIndices;
        private final int[] labels;
        private final int[] populations;
        private final boolean nonFinite;

        private Result(int resolution, boolean scaled, double[][] start, double[][] end,
                double[][] vectors, double[][] limits, double[] delta, int[][] categories,
                int[] groups, int[] binIndices, int[] labels, int[] populations, boolean nonFinite) {
            this.resolution = resolution;
            this.scaled = scaled;
            this.start = start;
            this.end = end;
            this.vectors = vectors;
            this.limits = limits;
            this.delta = delta;
            this.categories = categories;
            this.groups = groups;
            this.binIndices = binIndices;
            this.labels = labels;
            this.populations = populations;
            this.nonFinite = nonFinite;
        }
        public int resolution() { return resolution; }
        public boolean scaled() { return scaled; }
        public int cells() { return groups.length; }
        public int bins() { return start.length; }
        public int category(int cell, int axis) { return categories[cell][axis]; }
        /** Original selfmatch label: the first matching cell's one-based position. */
        public int groupOneBased(int cell) { return groups[cell]; }
        /** Zero-based occupied-bin row containing the supplied cell. */
        public int binOfCell(int cell) { return binIndices[cell]; }
        public int groupLabelOneBased(int bin) { return labels[bin]; }
        public int population(int bin) { return populations[bin]; }
        public double start(int bin, int axis) { return start[bin][axis]; }
        public double end(int bin, int axis) { return end[bin][axis]; }
        public double vector(int bin, int axis) { return vectors[bin][axis]; }
        public double limit(int bound, int axis) { return limits[bound][axis]; }
        public double delta(int axis) { return delta[axis]; }
        public boolean requiresNonFiniteReview() { return nonFinite; }
    }

    private record Bin(int index, int firstCellOneBased) { }

    /** Use the original integer resolution 40 and median-length scaling. */
    public static Result grid(double[][] coordinates, double[][] embedded) {
        return grid(coordinates, embedded, 40, true);
    }

    /**
     * Partition complete supplied coordinates and average vectors in occupied bins.
     * @throws IllegalArgumentException if the complete input is outside the native domain
     */
    public static Result grid(double[][] coordinates, double[][] embedded, int resolution, boolean scale) {
        if (coordinates == null || embedded == null || coordinates.length == 0
                || coordinates.length != embedded.length || resolution < 1) {
            throw unavailable("Require matching nonempty matrices and positive integer resolution");
        }
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int cell = 0; cell < coordinates.length; cell++) {
            if (coordinates[cell] == null || embedded[cell] == null
                    || coordinates[cell].length != 2 || embedded[cell].length != 2) {
                throw unavailable("Every coordinate/vector row must have exactly two columns");
            }
            for (int axis = 0; axis < 2; axis++) {
                double value = coordinates[cell][axis];
                if (!Double.isFinite(value) || !Double.isFinite(embedded[cell][axis])) {
                    throw unavailable("Nonfinite input is outside the native domain; retain every original row/value");
                }
                if (value < low[axis]) low[axis] = value;
                if (value > high[axis]) high[axis] = value;
            }
        }
        double[] delta = new double[2];
        for (int axis = 0; axis < 2; axis++) {
            delta[axis] = (high[axis] - low[axis]) / resolution;
            if (!(delta[axis] > 0) || !Double.isFinite(delta[axis])) {
                throw unavailable("Degenerate, underflowing or overflowing grid-axis range");
            }
        }
        int[][] categories = new int[coordinates.length][2];
        int[] groups = new int[coordinates.length];
        int[] binIndices = new int[coordinates.length];
        Map<Long, Bin> occupied = new LinkedHashMap<>();
        for (int cell = 0; cell < coordinates.length; cell++) {
            for (int axis = 0; axis < 2; axis++) {
                double value = (coordinates[cell][axis] - low[axis]) / delta[axis];
                if (!Double.isFinite(value) || value < 0 || value >= 2147483648.0) {
                    throw unavailable("Grid category is outside the original finite integer domain");
                }
                categories[cell][axis] = (int) value;
            }
            long key = ((long) categories[cell][0] << 32) | (categories[cell][1] & 0xffffffffL);
            Bin group = occupied.get(key);
            if (group == null) {
                group = new Bin(occupied.size(), cell + 1);
                occupied.put(key, group);
            }
            groups[cell] = group.firstCellOneBased();
            binIndices[cell] = group.index();
        }
        double[][] start = new double[occupied.size()][2];
        double[][] vectors = new double[occupied.size()][2];
        int[] populations = new int[occupied.size()];
        int[] labels = new int[occupied.size()];
        for (Bin bin : occupied.values()) labels[bin.index()] = bin.firstCellOneBased();
        for (int cell = 0; cell < coordinates.length; cell++) {
            int group = binIndices[cell];
            populations[group]++;
            for (int axis = 0; axis < 2; axis++) {
                start[group][axis] += coordinates[cell][axis];
                vectors[group][axis] += embedded[cell][axis];
                if (!Double.isFinite(start[group][axis]) || !Double.isFinite(vectors[group][axis])) {
                    throw unavailable("Group summation overflow; no partial grid is returned");
                }
            }
        }
        for (int group = 0; group < start.length; group++) {
            for (int axis = 0; axis < 2; axis++) {
                start[group][axis] /= populations[group];
                vectors[group][axis] /= populations[group];
            }
        }
        if (scale) {
            double targetSquared = delta[0] * delta[0] + delta[1] * delta[1];
            if (!Double.isFinite(targetSquared)) throw unavailable("Grid-spacing norm overflow");
            double target = Math.sqrt(targetSquared);
            double[] lengths = new double[vectors.length];
            for (int group = 0; group < vectors.length; group++) {
                double squared = vectors[group][0] * vectors[group][0] + vectors[group][1] * vectors[group][1];
                if (!Double.isFinite(squared)) throw unavailable("Mean-vector norm overflow");
                lengths[group] = Math.sqrt(squared);
            }
            Arrays.sort(lengths);
            int middle = lengths.length / 2;
            double median = lengths.length % 2 == 1 ? lengths[middle]
                    : (lengths[middle - 1] + lengths[middle]) / 2.0;
            for (int group = 0; group < vectors.length; group++) {
                for (int axis = 0; axis < 2; axis++) {
                    vectors[group][axis] = ((vectors[group][axis] * target) / median) / 2.0;
                }
            }
        }
        boolean nonFinite = false;
        double[][] end = new double[start.length][2];
        for (int group = 0; group < start.length; group++) {
            for (int axis = 0; axis < 2; axis++) {
                end[group][axis] = start[group][axis] + vectors[group][axis];
                nonFinite |= !Double.isFinite(end[group][axis]) || !Double.isFinite(vectors[group][axis]);
            }
        }
        return new Result(resolution, scale, start, end, vectors, new double[][]{low, high},
                delta, categories, groups, binIndices, labels, populations, nonFinite);
    }

    private static IllegalArgumentException unavailable(String reason) {
        return new IllegalArgumentException(reason
                + ". Use the original velociraptor 1.20.0 R workflow for unsupported domains; do not drop or repair values.");
    }
}
