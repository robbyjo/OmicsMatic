/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.pipeline;

/** One transcript, methylation, protein, or other feature association. */
public record OmicsAssociationEstimate(
        String featureId,
        double beta,
        double standardError,
        double statistic,
        double degreesOfFreedom,
        double pValue,
        double log10PValue,
        double negativeLog10PValue,
        int samples,
        java.util.List<String> adjustmentFeatures) {
    public OmicsAssociationEstimate {
        adjustmentFeatures = java.util.List.copyOf(adjustmentFeatures);
    }
    public OmicsAssociationEstimate(String featureId, double beta,
            double standardError, double statistic, double degreesOfFreedom,
            double pValue, double log10PValue, double negativeLog10PValue) {
        this(featureId, beta, standardError, statistic, degreesOfFreedom,
            pValue, log10PValue, negativeLog10PValue, -1, java.util.List.of());
    }
}
