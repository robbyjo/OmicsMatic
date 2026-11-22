/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Column-wise quantile normalization of finite double matrices.
 * Each column is an independent sample over the same number of observations.
 * Rank means form the default target; tied input values receive the average of
 * all target ranks occupied by that tie. Semantics follow the finite float64,
 * ndarray axis=1 subset of qnorm0.8.0; see META-INF/NOTICE-qnorm.txt.
 * This is not inverse-normal scoring or a raw-count model.
 */
public final class QuantileNormalization {
    private QuantileNormalization() { }

    /**
     * Normalized values in original row/column order and the sorted rank target.
     * Arrays are defensively copied on construction and access.
     * @param normalized normalized matrix
     * @param target sorted rank target
     */
    public record Result(double[][] normalized, double[] target) {
        /**
         * Copies the supplied result arrays.
         * @param normalized normalized matrix
         * @param target sorted rank target
         */
        public Result { normalized = copy(normalized); target = target.clone(); }
        @Override public double[][] normalized() { return copy(normalized); }
        @Override public double[] target() { return target.clone(); }
    }

    /**
     * Learns rank means from all columns and normalizes each column.
     * @param matrix nonempty rectangular finite matrix; remains unchanged
     * @return normalized values and fitted rank target
     */
    public static Result normalizeColumns(double[][] matrix) {
        return normalizeColumns(matrix, null);
    }

    /**
     * Normalizes columns against a supplied target or fitted rank means.
     * The target is sorted on a private copy and must have one value per row.
     * Nonfinite inputs and nonfinite rank/tie sums fail explicitly. Arithmetic
     * uses double precision; bitwise NumPy/Numba equivalence is not promised.
     * @param matrix nonempty rectangular finite matrix; remains unchanged
     * @param suppliedTarget finite target, or null to fit rank means
     * @return normalized values in original order and sorted target
     */
    public static Result normalizeColumns(double[][] matrix, double[] suppliedTarget) {
        int columns = validate(matrix);
        int rows = matrix.length;
        double[] target;
        if (suppliedTarget == null) {
            target = new double[rows];
            double[] values = new double[rows];
            for (int column = 0; column < columns; column++) {
                for (int row = 0; row < rows; row++) values[row] = matrix[row][column];
                Arrays.sort(values);
                for (int row = 0; row < rows; row++) target[row] = finite(target[row] + values[row], "Rank sum exceeds finite range; rescale input values");
            }
            for (int row = 0; row < rows; row++) target[row] /= columns;
        } else {
            if (suppliedTarget.length != rows) throw new IllegalArgumentException("Target length must equal matrix rows");
            target = suppliedTarget.clone();
            for (double value : target) finite(value, "Target values must be finite");
            Arrays.sort(target);
        }
        double[][] normalized = new double[rows][columns];
        Integer[] order = new Integer[rows];
        for (int column = 0; column < columns; column++) {
            for (int row = 0; row < rows; row++) order[row] = row;
            final int selected = column;
            Arrays.sort(order, Comparator.comparingDouble(row -> matrix[row][selected]));
            int start = 0;
            while (start < rows) {
                int end = start + 1;
                while (end < rows && matrix[order[start]][column] == matrix[order[end]][column]) end++;
                double sum = 0;
                for (int rank = start; rank < end; rank++) sum = finite(sum + target[rank], "Tie-rank sum exceeds finite range; rescale the target");
                double value = sum / (end - start);
                for (int rank = start; rank < end; rank++) normalized[order[rank]][column] = value;
                start = end;
            }
        }
        return new Result(normalized, target);
    }

    private static int validate(double[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null || matrix[0].length == 0)
            throw new IllegalArgumentException("A nonempty rectangular matrix is required");
        int columns = matrix[0].length;
        for (double[] row : matrix) {
            if (row == null || row.length != columns) throw new IllegalArgumentException("Matrix must be rectangular");
            for (double value : row) finite(value, "Matrix values must be finite; missing/NaN/infinite values require explicit preprocessing");
        }
        return columns;
    }
    private static double finite(double value, String message) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(message);
        return value;
    }
    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int row = 0; row < matrix.length; row++) result[row] = matrix[row].clone();
        return result;
    }
}
