/*
 * Copyright (C) 2026 JLinAlg contributors
 * PoPS covariance equations: FinucaneLab/pops 76eb86cba10254490003c8f4dc7ff5ce492d3667.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.jlinalg.genetics;

import java.util.List;
import jdistlib.accelerator.ComputeBackend;

/** Immutable chromosome-block covariance in the target-score row order. */
public final class PopsCovariance {
    private final int size;
    private final List<Block> blocks;

    /** A full symmetric block; indices are zero-based target-score row ordinals. */
    public record Block(int[] indices, double[][] values) {
        public Block {
            if (indices == null || indices.length == 0) throw new IllegalArgumentException("Nonempty covariance block required");
            indices = indices.clone(); values = PopsMath.copy(values, indices.length, "PoPS covariance");
            if (values[0].length != indices.length) throw new IllegalArgumentException("Square covariance block required");
            for (int i = 0; i < indices.length; i++) for (int j = 0; j < i; j++)
                if (values[i][j] != values[j][i]) throw new IllegalArgumentException("Symmetric covariance required; no repair is performed");
        }
        @Override public int[] indices() { return indices.clone(); }
        @Override public double[][] values() { return PopsMath.copy(values, values.length, "covariance"); }
    }

    /** Every target row must occur exactly once. No between-block entries are inferred. */
    public PopsCovariance(int size, List<Block> blocks) {
        if (size < 1 || blocks == null || blocks.isEmpty()) throw new IllegalArgumentException("Complete covariance partition required");
        this.size = size; this.blocks = List.copyOf(blocks);
        boolean[] seen = new boolean[size];
        for (Block block : this.blocks) for (int i : block.indices) {
            if (i < 0 || i >= size || seen[i]) throw new IllegalArgumentException("Covariance row duplicated or out of range");
            seen[i] = true;
        }
        for (boolean value : seen) if (!value) throw new IllegalArgumentException("Covariance partition omits a target row");
    }

    public int size() { return size; }
    public List<Block> blocks() { return blocks; }

    /** Diagnostics for the uniform MAGMA covariance adjustment in the selected PoPS source. */
    public record Regularized(PopsCovariance covariance, double diagonalShift, double minimumEigenvalue, double targetVariance) { }

    /** Explicit MAGMA-only adjustment; custom target-score covariances must be used as supplied. */
    public Regularized regularizeMagma(double[] target, ComputeBackend backend) {
        if (target == null || target.length != size || backend == null) throw new IllegalArgumentException("Aligned target and backend required");
        for (double value : target) PopsMath.finite(value, "PoPS target");
        double minimum = 0;
        for (Block block : blocks) for (double eigenvalue : PopsEigenvalues.values(block.values)) {
            PopsMath.finite(eigenvalue, "covariance eigenvalue"); minimum = Math.min(minimum, eigenvalue);
        }
        double variance = PopsMath.variance(target), shift = Math.abs(Math.min(minimum, 0)) + .05 + .9 * Math.max(0, variance - 1);
        PopsMath.finite(shift, "PoPS covariance shift");
        var adjusted = new java.util.ArrayList<Block>();
        for (Block block : blocks) {
            double[][] values = block.values();
            for (int i = 0; i < values.length; i++) values[i][i] += shift;
            adjusted.add(new Block(block.indices, values));
        }
        return new Regularized(new PopsCovariance(size, adjusted), shift, minimum, variance);
    }

    double[][] whiten(int[] rows, double[][] data, ComputeBackend backend) {
        int[] positions = new int[size]; java.util.Arrays.fill(positions, -1);
        for (int i = 0; i < rows.length; i++) positions[rows[i]] = i;
        double[][] output = new double[data.length][data[0].length];
        for (Block block : blocks) {
            int count = 0;
            for (int i : block.indices) if (positions[i] >= 0) count++;
            if (count == 0) continue;
            int[] original = new int[count], current = new int[count];
            for (int i = 0, k = 0; i < block.indices.length; i++) if (positions[block.indices[i]] >= 0) {
                original[k] = i; current[k++] = positions[block.indices[i]];
            }
            // PoPS extracts each block in the selected target order, not an annotation sort.
            for (int i = 1; i < count; i++) for (int j = i; j > 0 && current[j] < current[j - 1]; j--) {
                int t = current[j]; current[j] = current[j - 1]; current[j - 1] = t;
                t = original[j]; original[j] = original[j - 1]; original[j - 1] = t;
            }
            double[] covariance = new double[count * count];
            for (int i = 0; i < count; i++) for (int j = 0; j < count; j++)
                covariance[i * count + j] = block.values[original[i]][original[j]];
            double[] lower = backend.dpotrf(covariance, count).lower();
            for (int i = 0; i < count; i++) for (int c = 0; c < data[0].length; c++) {
                double value = data[current[i]][c];
                for (int j = 0; j < i; j++) value -= lower[i * count + j] * output[current[j]][c];
                output[current[i]][c] = value / lower[i * count + i];
                PopsMath.finite(output[current[i]][c], "PoPS whitened values");
            }
        }
        return output;
    }
}
