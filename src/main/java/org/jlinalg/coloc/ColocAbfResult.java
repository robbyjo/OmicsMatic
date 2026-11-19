/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.coloc;

import java.util.List;

/** Source-compatible H0..H4 and per-variant ABFs, with explicit computational warnings. */
public record ColocAbfResult(List<Variant> variants, double[] hypothesisPosteriors,
        double[] effectivePriors, Double trait1StandardDeviation, Double trait2StandardDeviation,
        double[] trait1Weights, double[] trait2Weights, List<String> warnings,
        int trait1Variants, int trait2Variants, int positionKeyMismatches) {
    public record Variant(String id, Long position1, Long position2,
            double variance1, double z1, double shrinkage1, double logAbf1,
            double variance2, double z2, double shrinkage2, double logAbf2,
            double sourceConditionalSharedPosterior) { }
    public ColocAbfResult {
        variants = List.copyOf(variants);warnings = List.copyOf(warnings);
        hypothesisPosteriors = hypothesisPosteriors.clone();effectivePriors = effectivePriors.clone();
        trait1Weights = trait1Weights == null ? null : trait1Weights.clone();
        trait2Weights = trait2Weights == null ? null : trait2Weights.clone();
    }
    @Override public double[] hypothesisPosteriors() { return hypothesisPosteriors.clone(); }
    @Override public double[] effectivePriors() { return effectivePriors.clone(); }
    @Override public double[] trait1Weights() { return trait1Weights == null ? null : trait1Weights.clone(); }
    @Override public double[] trait2Weights() { return trait2Weights == null ? null : trait2Weights.clone(); }
}
