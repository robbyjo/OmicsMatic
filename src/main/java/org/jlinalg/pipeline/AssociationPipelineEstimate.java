/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.pipeline;

/** Variant metadata, QC, and one coefficient-level association result. */
public record AssociationPipelineEstimate(
        VariantRecord variant,
        VariantStatistics variantStatistics,
        double beta,
        double standardError,
        double statistic,
        double degreesOfFreedom,
        double pValue,
        double log10PValue,
        double negativeLog10PValue,
        java.util.Map<String,String> inference) {
    public AssociationPipelineEstimate { inference=java.util.Map.copyOf(inference); }
    public AssociationPipelineEstimate(VariantRecord variant,VariantStatistics statistics,double beta,double se,double statistic,double df,double p,double logP,double minusLogP) {
        this(variant,statistics,beta,se,statistic,df,p,logP,minusLogP,java.util.Map.of());
    }
}
