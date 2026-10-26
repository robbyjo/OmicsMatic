/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.pipeline;

/** Structured per-variant fitting failure from a long-running scan. */
public record AssociationPipelineFailure(
        String variantId, String exceptionType, String message,
        VariantRecord variant,VariantStatistics statistics,java.util.Map<String,String> inference) {
    public AssociationPipelineFailure { inference=java.util.Map.copyOf(inference); }
    public AssociationPipelineFailure(String variantId,String exceptionType,String message) {
        this(variantId,exceptionType,message,null,null,java.util.Map.of());
    }
}
