/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

/** Native audits of original Cicero-M3 1.3.9 distance penalties and signed reconciliation.
 * This class does not fit graphical-lasso models or infer biological interactions. */
public final class CiceroCoaccessibility {
    private CiceroCoaccessibility() { }
    public enum Kind { VALUE, NA_MIXED_SIGNS, NAN_EMPTY }
    public record Reconciled(Kind kind,double value) { }

    /** Original strict endpoint inclusion, not generic interval overlap. Coordinates are unconverted. */
    public static boolean inWindow(long start,long end,long windowStart,long windowEnd) {
        return (start>windowStart&&start<windowEnd)||(end>windowStart&&end<windowEnd);
    }
    /** Original get_rho_mat: replace nonfinite and negative penalties with zero. */
    public static double penalty(double distance,double parameter,double power) {
        if(!Double.isFinite(distance)||distance<0||!Double.isFinite(parameter)||parameter<0||!Double.isFinite(power)||power<=0||power>=1)
            throw new IllegalArgumentException("Require finite nonnegative distance/parameter and power in (0,1)");
        double value=(1-Math.pow(1000/distance,power))*parameter;
        return !Double.isFinite(value)||value<0?0:value;
    }
    /** R4.6.1 cov2cor inverse-diagonal square roots and multiplication order for an off-diagonal entry. Original diagonals are set to one. */
    public static double correlation(double covariance,double diagonalRow,double diagonalColumn) {
        if(!Double.isFinite(covariance)||!Double.isFinite(diagonalRow)||!Double.isFinite(diagonalColumn)||diagonalRow<=0||diagonalColumn<=0)
            throw new IllegalArgumentException("Require finite covariance and positive finite model diagonals");
        return (Math.sqrt(1/diagonalRow)*covariance)*Math.sqrt(1/diagonalColumn);
    }
    /** Original reconcile over finite correlation contributions. Mixed signs produce R NA, not zero.
     * Two compensated passes approximate R's extended-precision mean; raw R values remain authoritative. */
    public static Reconciled reconcile(double[] contributions) {
        if(contributions==null)throw new IllegalArgumentException("Missing contributions");
        boolean nonnegative=true,nonpositive=true;
        for(double value:contributions){if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite contributions are outside this audited domain");nonnegative&=value>=0;nonpositive&=value<=0;}
        if(contributions.length==0)return new Reconciled(Kind.NAN_EMPTY,Double.NaN);
        if(contributions.length==1)return new Reconciled(Kind.VALUE,contributions[0]);
        if(!nonnegative&&!nonpositive)return new Reconciled(Kind.NA_MIXED_SIGNS,Double.NaN);
        double sum=0,correction=0;
        for(double value:contributions){double y=value-correction,t=sum+y;correction=(t-sum)-y;sum=t;}
        double mean=sum/contributions.length;
        if(!Double.isFinite(mean))throw new IllegalArgumentException("Mean outside finite native audit domain");
        sum=0;correction=0;
        for(double value:contributions){double y=(value-mean)-correction,t=sum+y;correction=(t-sum)-y;sum=t;}
        mean+=sum/contributions.length;return new Reconciled(Kind.VALUE,mean);
    }
}
