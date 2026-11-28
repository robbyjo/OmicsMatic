/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Objects;

/**
 * Full dense CADD1.6 linear decision function after its original feature imputation.
 * The original models use a non-centering, in-place float32 StandardScaler and
 * float64 logistic-regression coefficients. A decision score is not a probability.
 * Model parameters, complete feature construction, and assembly must be bound by
 * the caller. This class does not annotate raw variants or infer missing features.
 */
public final class CaddLinearScoring {
    private final double[] coefficients,scales;
    private final double intercept;
    private static final double UNIT_ROUNDOFF=0x1.0p-53;

    public CaddLinearScoring(double[] coefficients,double[] scales,double intercept){
        Objects.requireNonNull(coefficients);Objects.requireNonNull(scales);
        if(coefficients.length==0||coefficients.length!=scales.length||coefficients.length>1_000_000)throw new IllegalArgumentException("Require the complete equally sized coefficient and scale arrays");
        if(!Double.isFinite(intercept))throw new IllegalArgumentException("CADD intercept must be finite");
        this.coefficients=coefficients.clone();this.scales=scales.clone();this.intercept=intercept;
        for(int i=0;i<coefficients.length;i++)if(!Double.isFinite(coefficients[i])||!Double.isFinite(scales[i])||scales[i]<=0)throw new IllegalArgumentException("Invalid complete CADD coefficient/positive scale at feature "+(i+1));
    }
    public int features(){return coefficients.length;}
    /** A numerical envelope for ordinary IEEE754 source dot-product orderings, not decimal serialization. */
    public record Prediction(double rawScore,double floatingPointEnvelope,double absoluteProducts,int features){
        public double lower(){return Math.nextDown(rawScore-floatingPointEnvelope);}
        public double upper(){return Math.nextUp(rawScore+floatingPointEnvelope);}
    }
    /** Preserve the original float32 rounding after float64 division; never center or repair a value. */
    public void transform(float[] original,float[] destination){
        if(original==null||destination==null||original.length!=features()||destination.length!=features())throw new IllegalArgumentException("Require every original feature in exact model order");
        for(int i=0;i<original.length;i++){
            if(!Float.isFinite(original[i]))throw new ArithmeticException("Nonfinite CADD input at feature "+(i+1)+"; no imputed zero or complete prediction");
            float value=(float)(((double)original[i])/scales[i]);
            if(!Float.isFinite(value))throw new ArithmeticException("Original float32 scaling overflow at feature "+(i+1)+"; no complete prediction");
            destination[i]=value;
        }
    }
    /**
     * Evaluate all features with compensated FP64 accumulation. BLAS ordering can
     * change final bits; retain the envelope and validate downstream rounding or
     * calibration before claiming equality to an original reported CADD score.
     */
    public Prediction predict(float[] original){
        if(original==null||original.length!=features())throw new IllegalArgumentException("Require every original feature in exact model order");
        double sum=0,correction=0,absolute=0;
        for(int i=0;i<original.length;i++){
            if(!Float.isFinite(original[i]))throw new ArithmeticException("Nonfinite CADD input at feature "+(i+1)+"; no complete prediction");
            float scaled=(float)(((double)original[i])/scales[i]);
            if(!Float.isFinite(scaled))throw new ArithmeticException("Original float32 scaling overflow at feature "+(i+1)+"; no complete prediction");
            double product=((double)scaled)*coefficients[i];
            if(!Double.isFinite(product))throw new ArithmeticException("CADD weighted feature overflow at "+(i+1));
            double next=sum+product;
            if(!Double.isFinite(next))throw new ArithmeticException("CADD accumulation overflow at feature "+(i+1));
            correction+=Math.abs(sum)>=Math.abs(product)?(sum-next)+product:(product-next)+sum;
            sum=next;absolute+=Math.abs(product);
            if(!Double.isFinite(correction)||!Double.isFinite(absolute))throw new ArithmeticException("CADD complete numerical envelope overflow at feature "+(i+1));
        }
        double value=(sum+correction)+intercept;
        if(!Double.isFinite(value))throw new ArithmeticException("Nonfinite CADD decision score; no complete prediction");
        double operations=4.0*features()+16.0,nu=operations*UNIT_ROUNDOFF;
        // Include rounded products, compensated/native and ordinary source sums,
        // intercept addition and underflow. Rounded-up arithmetic keeps the
        // intentionally conservative error allowance from rounding inward.
        double magnitude=Math.nextUp(absolute+Math.abs(intercept));
        double envelope=Math.nextUp((nu/(1.0-nu))*magnitude+8.0*Math.ulp(value)+operations*Double.MIN_VALUE);
        if(!Double.isFinite(envelope))throw new ArithmeticException("Unbounded CADD numerical uncertainty; no complete prediction");
        return new Prediction(value,envelope,absolute,features());
    }
}
