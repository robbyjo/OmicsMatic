/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

/** A checked Cox fit could not certify a finite coefficient maximum. */
public final class CoxLikelihoodException extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    private final CoxLikelihoodExistence.Result likelihood;
    public CoxLikelihoodException(CoxLikelihoodExistence.Result likelihood){
        super(likelihood.status()+": "+likelihood.message());this.likelihood=likelihood;
    }
    public CoxLikelihoodExistence.Result likelihood(){return likelihood;}
}
