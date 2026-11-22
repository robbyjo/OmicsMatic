/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

/** Scalar NB2/Poisson Pearson residuals with explicit variance floors and clipping.
 * This computes a residual from a supplied mean/dispersion, not a fitted or regularized SCT model.
 */
public final class NegativeBinomialResiduals {
    private NegativeBinomialResiduals() { }
    public static double pearson(double value,double mean,double theta,double minimumVariance,double lower,double upper) {
        if(!Double.isFinite(value)||value<0||!Double.isFinite(mean)||mean<=0||Double.isNaN(theta)||theta<=0||!Double.isFinite(minimumVariance)||minimumVariance<0||Double.isNaN(lower)||Double.isNaN(upper)||lower==Double.POSITIVE_INFINITY||upper==Double.NEGATIVE_INFINITY||lower>upper)throw new IllegalArgumentException("Require nonnegative finite observations, positive finite mean, positive theta (or +Inf), nonnegative variance floor and ordered clipping bounds");
        double variance=mean+mean*(mean/theta);if(!Double.isFinite(variance))throw new IllegalArgumentException("NB variance exceeds finite arithmetic");
        double residual=(value-mean)/Math.sqrt(Math.max(variance,minimumVariance));
        if(!Double.isFinite(residual))throw new IllegalArgumentException("Pearson residual exceeds finite arithmetic");
        return Math.max(lower,Math.min(upper,residual));
    }
    public static double logLinkMean(double[] coefficients,double[] design) {
        if(coefficients.length==0||coefficients.length!=design.length)throw new IllegalArgumentException("Matching nonempty coefficient/design vectors required");double eta=0;
        for(int i=0;i<coefficients.length;i++){if(!Double.isFinite(coefficients[i])||!Double.isFinite(design[i]))throw new IllegalArgumentException("Finite coefficient/design values required");eta=Math.fma(coefficients[i],design[i],eta);}
        double mean=Math.exp(eta);if(!Double.isFinite(mean)||mean<=0)throw new IllegalArgumentException("Log-link mean underflow/overflow");return mean;
    }
}
