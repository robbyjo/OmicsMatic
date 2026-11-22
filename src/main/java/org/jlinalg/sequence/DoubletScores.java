/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

/** Native scalar Scrublet Bayesian neighborhood score and propagated standard error.
 * Reference: Wolock et al., Cell Systems (2019), doi:10.1016/j.cels.2018.11.005;
 * independently validated against Scrublet 0.2.3. No neighbor graph or count preprocessing inferred.
 */
public final class DoubletScores {
    private DoubletScores() { }
    public record Score(double value,double standardError) { }
    public static Score score(int simulatedNeighbors,int neighbors,double simulatedObservedRatio,double expectedRate,double rateStandardDeviation){
        if(neighbors<1||simulatedNeighbors<0||simulatedNeighbors>neighbors||!Double.isFinite(simulatedObservedRatio)||simulatedObservedRatio<=0||!Double.isFinite(expectedRate)||expectedRate<=0||expectedRate>=1||!Double.isFinite(rateStandardDeviation)||rateStandardDeviation<0)throw new IllegalArgumentException("Invalid doublet neighborhood, simulation ratio, expected rate or rate uncertainty");
        double q=(simulatedNeighbors+1.0)/(neighbors+2.0),r=simulatedObservedRatio,rho=expectedRate;
        double denominator=(1-rho)*(1-q)+q*rho/r;
        double value=(q*rho/r)/denominator;
        double seQ=Math.sqrt(q*(1-q)/(neighbors+3.0));
        double error=(q*rho/r)/(denominator*denominator)*Math.hypot(seQ/q*(1-rho),rateStandardDeviation/rho*(1-q));
        if(!Double.isFinite(value)||!Double.isFinite(error))throw new IllegalArgumentException("Doublet score/uncertainty exceeded finite arithmetic");
        return new Score(value,error);
    }
    /** Upstream doublet calls use strict greater-than, preserving equality as a non-doublet. */
    public static boolean predicted(double score,double threshold){
        if(!Double.isFinite(score)||score<0||score>1||!Double.isFinite(threshold)||threshold<0||threshold>1)throw new IllegalArgumentException("Score and threshold must be finite in [0,1]");return score>threshold;
    }
}
