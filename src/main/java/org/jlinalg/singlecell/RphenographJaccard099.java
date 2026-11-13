/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.Arrays;

/**
 * Complete coefficient calculation for the integer-neighbor domain of Hao Chen's
 * Rphenograph 0.99.1 {@code src/jaccard_coeff.cpp} (Artistic-2.0).
 * <p>Retains event order, duplicate/self neighbor events, half weights and the
 * original trailing zero rows. This method performs neither nearest-neighbor
 * search nor Louvain clustering. The reported P040 version 0.1.1 is not inferred.
 */
public final class RphenographJaccard099 {
    private RphenographJaccard099() { }

    /** Exact three-column returned matrix, using integer storage for its first two columns. */
    public static final class Coefficients {
        private final int inputRows;
        private final int neighborColumns;
        private final int positiveRows;
        private final int[] from;
        private final int[] to;
        private final double[] weights;

        private Coefficients(int inputRows, int neighborColumns, int positiveRows,
                int[] from, int[] to, double[] weights) {
            this.inputRows = inputRows;
            this.neighborColumns = neighborColumns;
            this.positiveRows = positiveRows;
            this.from = from;
            this.to = to;
            this.weights = weights;
        }
        public int inputRows() { return inputRows; }
        public int neighborColumns() { return neighborColumns; }
        public int rawRows() { return weights.length; }
        public int positiveRows() { return positiveRows; }
        public int zeroPaddedRows() { return weights.length - positiveRows; }
        /** Source row index, one-based for positive events and zero for padded rows. */
        public int fromOneBased(int row) { return from[row]; }
        /** Source neighbor index, one-based for positive events and zero for padded rows. */
        public int toOneBased(int row) { return to[row]; }
        public double weight(int row) { return weights[row]; }
    }

    /**
     * Computes every row of the original returned matrix.
     * @param rows complete number of input rows, including zero
     * @param columns complete neighbor-column count, including zero
     * @param rowMajorOneBasedNeighbors row-major indices in [1, rows]; duplicate
     *        and self indices are permitted, as in the source helper
     * @param maximumIntersectionComparisons declared upper bound on merge
     *        comparisons, conservatively counted as 2*rows*columns*columns;
     *        sorting and output storage are separate resource costs
     * @return complete raw matrix, including its original trailing zero rows
     * @throws IllegalArgumentException for invalid shape/index or exceeded capacity
     */
    public static Coefficients compute(int rows, int columns,
            int[] rowMajorOneBasedNeighbors, long maximumIntersectionComparisons) {
        if (rows < 0 || columns < 0 || rowMajorOneBasedNeighbors == null
                || maximumIntersectionComparisons < 0) {
            throw new IllegalArgumentException("Nonnegative shape/capacity and complete integer neighbors required");
        }
        long rawCount = (long) rows * columns;
        if (rawCount > Integer.MAX_VALUE || rowMajorOneBasedNeighbors.length != rawCount) {
            throw new IllegalArgumentException("Complete shape must match storage and the original int row-product domain");
        }
        final long comparisons;
        try {
            comparisons = Math.multiplyExact(Math.multiplyExact(rawCount, columns), 2L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Complete intersection capacity overflow; no prefix is computed", exception);
        }
        if (comparisons > maximumIntersectionComparisons) {
            throw new IllegalArgumentException("Complete intersection capacity exceeded; no prefix is computed");
        }
        int size = (int) rawCount;
        int[] neighbors = rowMajorOneBasedNeighbors.clone();
        for (int index : neighbors) {
            if (index < 1 || index > rows) {
                throw new IllegalArgumentException("Every neighbor must be an exact one-based input-row index");
            }
        }
        int[] from = new int[size];
        int[] to = new int[size];
        double[] weights = new double[size];
        if (size == 0) {
            return new Coefficients(rows, columns, 0, from, to, weights);
        }
        int[][] sets = new int[rows][];
        for (int i = 0; i < rows; i++) {
            int[] values = Arrays.copyOfRange(neighbors, i * columns, (i + 1) * columns);
            Arrays.sort(values);
            int count = 0;
            for (int value : values) {
                if (count == 0 || value != values[count - 1]) {
                    values[count++] = value;
                }
            }
            sets[i] = count == values.length ? values : Arrays.copyOf(values, count);
        }
        int output = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int target = neighbors[i * columns + j];
                int overlap = intersectionSize(sets[i], sets[target - 1]);
                if (overlap > 0) {
                    from[output] = i + 1;
                    to[output] = target;
                    weights[output] = overlap / (2.0 * columns - overlap) / 2;
                    output++;
                }
            }
        }
        return new Coefficients(rows, columns, output, from, to, weights);
    }

    /** Convenience input; an empty outer array represents a zero-by-zero matrix. */
    public static Coefficients compute(int[][] oneBasedNeighbors,
            long maximumIntersectionComparisons) {
        if (oneBasedNeighbors == null) {
            throw new IllegalArgumentException("Complete neighbor matrix required");
        }
        int rows = oneBasedNeighbors.length;
        int columns = rows == 0 ? 0 : requireRow(oneBasedNeighbors[0], -1).length;
        long count = (long) rows * columns;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Complete neighbor storage exceeds original int product domain");
        }
        int[] flat = new int[(int) count];
        for (int i = 0; i < rows; i++) {
            int[] row = requireRow(oneBasedNeighbors[i], columns);
            System.arraycopy(row, 0, flat, i * columns, columns);
        }
        return compute(rows, columns, flat, maximumIntersectionComparisons);
    }

    private static int[] requireRow(int[] row, int columns) {
        if (row == null || (columns >= 0 && row.length != columns)) {
            throw new IllegalArgumentException("Complete rectangular neighbor matrix required");
        }
        return row;
    }

    private static int intersectionSize(int[] left, int[] right) {
        int i = 0, j = 0, count = 0;
        while (i < left.length && j < right.length) {
            if (left[i] < right[j]) {
                i++;
            } else if (left[i] > right[j]) {
                j++;
            } else {
                count++;
                i++;
                j++;
            }
        }
        return count;
    }
}
