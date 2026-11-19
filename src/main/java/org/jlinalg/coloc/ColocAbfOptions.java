/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.coloc;

/** Requested ABF priors and optional source-order, nonnegative prior weights. */
public record ColocAbfOptions(double trait1Prior, double trait2Prior, double sharedPrior,
        double[] trait1Weights, double[] trait2Weights) {
    public ColocAbfOptions {
        for (double p : new double[] {trait1Prior, trait2Prior, sharedPrior})
            if (!(p > 0 && p < 1)) throw new IllegalArgumentException("priors must be in (0,1)");
        trait1Weights = copy(trait1Weights);
        trait2Weights = copy(trait2Weights);
    }
    public static ColocAbfOptions defaults() {
        return new ColocAbfOptions(1e-4, 1e-4, 1e-5, null, null);
    }
    private static double[] copy(double[] values) {
        if (values == null) return null;
        values = values.clone();
        for (double v : values) if (!Double.isFinite(v) || v < 0)
            throw new IllegalArgumentException("prior weights must be finite and nonnegative");
        return values;
    }
    @Override public double[] trait1Weights() { return trait1Weights == null ? null : trait1Weights.clone(); }
    @Override public double[] trait2Weights() { return trait2Weights == null ? null : trait2Weights.clone(); }
    double[] rawWeights1() { return trait1Weights; }
    double[] rawWeights2() { return trait2Weights; }
}
