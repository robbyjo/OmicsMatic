/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Arrays;

/**
 * Immutable, full compressed sparse column LD correlation matrix.
 * Entries are supplied in variant order; this class never matches, reorders,
 * symmetrizes, thresholds or repairs an LD panel. Both triangles are required.
 */
public final class LdCorrelation {
    private final int size;
    private final int[] pointers;
    private final int[] rows;
    private final double[] values;

    private LdCorrelation(int size, int[] pointers, int[] rows, double[] values) {
        this.size = size;
        this.pointers = pointers.clone();
        this.rows = rows.clone();
        this.values = values.clone();
    }

    /** Validates finite, exactly symmetric correlations and a unit diagonal. */
    public static LdCorrelation ofCsc(int size, int[] pointers, int[] rows, double[] values) {
        if (size < 1 || size == Integer.MAX_VALUE || pointers == null || rows == null || values == null
                || pointers.length != size + 1 || rows.length != values.length
                || pointers[0] != 0 || pointers[size] != rows.length) {
            throw new IllegalArgumentException("Complete nonempty full CSC LD matrix required");
        }
        for (int col = 0; col < size; col++) {
            if (pointers[col] < 0 || pointers[col + 1] < pointers[col] || pointers[col + 1] > rows.length) {
                throw new IllegalArgumentException("Invalid CSC column pointers");
            }
            int previous = -1;
            boolean diagonal = false;
            for (int k = pointers[col]; k < pointers[col + 1]; k++) {
                int row = rows[k];
                double value = values[k];
                if (row <= previous || row >= size || !Double.isFinite(value) || Math.abs(value) > 1) {
                    throw new IllegalArgumentException("LD rows must be sorted and unique; correlations must be finite in [-1,1]");
                }
                if (row == col) {
                    if (value != 1.0) throw new IllegalArgumentException("Every LD diagonal entry must be exactly 1");
                    diagonal = true;
                }
                previous = row;
            }
            if (!diagonal) throw new IllegalArgumentException("Missing unit LD diagonal");
        }
        // Validate after all pointer/row bounds, without reading an unchecked column.
        for (int col = 0; col < size; col++) {
            for (int k = pointers[col]; k < pointers[col + 1]; k++) {
                int row = rows[k];
                int inverse = Arrays.binarySearch(rows, pointers[row], pointers[row + 1], col);
                if (inverse < 0 || values[k] != values[inverse]) {
                    throw new IllegalArgumentException("Both LD triangles must agree exactly; no implicit symmetrization");
                }
            }
        }
        return new LdCorrelation(size, pointers, rows, values);
    }

    public int size() { return size; }
    public int nonzeroCount() { return values.length; }

    void addColumn(int col, double coefficient, double[] result) {
        for (int k = pointers[col]; k < pointers[col + 1]; k++) {
            result[rows[k]] += coefficient * values[k];
        }
    }

    double[] multiply(double[] vector, double[] extraDiagonal) {
        double[] result = new double[size];
        for (int col = 0; col < size; col++) {
            addColumn(col, vector[col], result);
            result[col] += extraDiagonal[col] * vector[col];
        }
        return result;
    }
}
