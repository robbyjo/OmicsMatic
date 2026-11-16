/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdistlib.accelerator.ComputeBackend;

/** Reuses exact chromosome Cholesky factors across PoPS feature batches. */
final class PopsBlockWhitening {
    private record Row(int position, double diagonal, int[] previousPositions, double[] coefficients) { }
    private final int size;
    private final List<Row> rows;

    private PopsBlockWhitening(int size, List<Row> rows) { this.size = size; this.rows = List.copyOf(rows); }

    static PopsBlockWhitening prepare(PopsCovariance covariance, int[] selected, ComputeBackend backend) {
        List<Row> factors = new ArrayList<>();
        for (var block : covariance.blocks()) {
            int[] global = block.indices(), local = new int[covariance.size()]; Arrays.fill(local, -1);
            for (int i = 0; i < global.length; i++) local[global[i]] = i;
            int count = 0; for (int row : selected) if (local[row] >= 0) count++;
            if (count == 0) continue;
            int[] original = new int[count], positions = new int[count];
            for (int i = 0, k = 0; i < selected.length; i++) if (local[selected[i]] >= 0) { original[k] = local[selected[i]]; positions[k++] = i; }
            double[][] values = block.values(); double[] matrix = new double[Math.multiplyExact(count, count)];
            for (int i = 0; i < count; i++) for (int j = 0; j < count; j++) matrix[i * count + j] = values[original[i]][original[j]];
            double[] lower = backend.dpotrf(matrix, count).lower();
            for (int i = 0; i < count; i++) {
                int nonzero = 0; for (int j = 0; j < i; j++) if (lower[i * count + j] != 0) nonzero++;
                int[] previous = new int[nonzero]; double[] coefficients = new double[nonzero];
                for (int j = 0, k = 0; j < i; j++) if (lower[i * count + j] != 0) { previous[k] = positions[j]; coefficients[k++] = lower[i * count + j]; }
                factors.add(new Row(positions[i], lower[i * count + i], previous, coefficients));
            }
        }
        if (factors.size() != selected.length) throw new IllegalArgumentException("Whitening partition omits selected target rows");
        return new PopsBlockWhitening(selected.length, factors);
    }

    double[][] apply(double[][] data) {
        if (data.length != size) throw new IllegalArgumentException("Whitening row count differs");
        double[][] result = new double[size][data[0].length];
        for (Row row : rows) {
            double[] dest = result[row.position]; System.arraycopy(data[row.position], 0, dest, 0, dest.length);
            // Only exact zeros are skipped. All nonzero terms retain their
            // original predecessor order, including arbitrarily small terms.
            for (int k = 0; k < row.coefficients.length; k++) {
                double coefficient = row.coefficients[k]; double[] previous = result[row.previousPositions[k]];
                for (int j = 0; j < dest.length; j++) dest[j] -= coefficient * previous[j];
            }
            for (int j = 0; j < dest.length; j++) { dest[j] /= row.diagonal; PopsMath.finite(dest[j], "PoPS whitened value"); }
        }
        return result;
    }
}
