/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.concurrent.CancellationException;
import jdistlib.ChiSquare;

/**
 * Native prepared-parameter conditional GWAS equations from GCTA1.94.1 mtCOJO.
 * This component does not estimate causal effects, LD scores, heritability,
 * overlap or genetic correlation. It does not transform effect scales.
 * Covariate matrices are variant-by-covariate. Input order is preserved.
 * All inputs are frozen before calculation; result arrays are defensive copies.
 * A failed row, factorization, capacity or cancellation refuses the whole result.
 *
 * <p>The multiple-covariate source equation uses D^-1 R^-1 D b, where D is the
 * covariate genetic standard-deviation diagonal and R has unit diagonal.
 * Its sampling-covariance diagonal is one, independently of the original
 * univariate LDSC intercepts. Only off-diagonal sampling intercepts enter that
 * matrix. Signed/indefinite estimated correlation matrices are not projected
 * or clamped. The source conditions on point causal effects; their estimated
 * standard errors are not added to the per-variant conditional variance.
 */
public final class MtcojoConditional {
    private MtcojoConditional() { }
    public record Limits(int maximumVariants,int maximumCovariates,long maximumCells,long maximumWork) {
        public Limits {
            if(maximumVariants<1||maximumCovariates<1||maximumCovariates>10000||maximumCells<1||maximumWork<1)
                throw new IllegalArgumentException("Require positive native mtCOJO limits and at most 10,000 covariates");
        }
        public static Limits defaults(){return new Limits(1_000_000,128,5_000_000,2_000_000_000);}
    }
    public static final class Result {
        private final double[] effects,standardErrors,pValues,jointCausalEffects;
        private Result(double[] effects,double[] standardErrors,double[] pValues,double[] jointCausalEffects) {
            this.effects=effects;this.standardErrors=standardErrors;this.pValues=pValues;this.jointCausalEffects=jointCausalEffects;
        }
        public int size(){return effects.length;}
        public double effect(int variant){return effects[variant];}
        public double standardError(int variant){return standardErrors[variant];}
        public double pValue(int variant){return pValues[variant];}
        public double[] effects(){return effects.clone();}
        public double[] standardErrors(){return standardErrors.clone();}
        public double[] pValues(){return pValues.clone();}
        public double[] jointCausalEffects(){return jointCausalEffects.clone();}
    }
    public static Result single(double[] targetEffects,double[] targetSE,double[] covariateEffects,double[] covariateSE,
            double causalEffect,double samplingIntercept) {
        return single(targetEffects,targetSE,covariateEffects,covariateSE,causalEffect,samplingIntercept,Limits.defaults());
    }
    public static Result single(double[] targetEffects,double[] targetSE,double[] covariateEffects,double[] covariateSE,
            double causalEffect,double samplingIntercept,Limits limits) {
        if(targetEffects==null)throw new IllegalArgumentException("Target effects are required");int n=targetEffects.length;capacity(n,1,limits);
        double[] y=vector(targetEffects,n,false,"target effects"),sy=vector(targetSE,n,true,"target SE"),x=vector(covariateEffects,n,false,"covariate effects"),sx=vector(covariateSE,n,true,"covariate SE");
        finite(causalEffect,"causal effect");finite(samplingIntercept,"sampling intercept");
        double[] effect=new double[n],se=new double[n],p=new double[n];
        for(int i=0;i<n;i++) {
            cancellation();double b=y[i]-x[i]*causalEffect;
            double vx=causalEffect*causalEffect*sx[i]*sx[i],cross=causalEffect*samplingIntercept*sx[i]*sy[i];
            inference(i,b,sy[i]*sy[i]+vx-2*cross,effect,se,p);
        }
        cancellation();return new Result(effect,se,p,new double[]{causalEffect});
    }
    public static Result multiple(double[] targetEffects,double[] targetSE,double[][] covariateEffects,double[][] covariateSE,
            double[] marginalCausalEffects,double[] covariateGeneticVariances,double[][] covariateGeneticCorrelations,
            double[][] samplingIntercepts) {
        return multiple(targetEffects,targetSE,covariateEffects,covariateSE,marginalCausalEffects,covariateGeneticVariances,covariateGeneticCorrelations,samplingIntercepts,Limits.defaults());
    }
    public static Result multiple(double[] targetEffects,double[] targetSE,double[][] covariateEffects,double[][] covariateSE,
            double[] marginalCausalEffects,double[] covariateGeneticVariances,double[][] covariateGeneticCorrelations,
            double[][] samplingIntercepts,Limits limits) {
        if(targetEffects==null||marginalCausalEffects==null)throw new IllegalArgumentException("Target and marginal causal effects are required");
        int n=targetEffects.length,q=marginalCausalEffects.length;if(q<2)throw new IllegalArgumentException("Use single for one covariate; the source multiple-covariate equation requires at least two");capacity(n,q,limits);
        double[] y=vector(targetEffects,n,false,"target effects"),sy=vector(targetSE,n,true,"target SE"),bxy=vector(marginalCausalEffects,q,false,"marginal causal effects"),genetic=vector(covariateGeneticVariances,q,true,"covariate genetic variances");
        double[][] x=matrix(covariateEffects,n,q,false,"covariate effects"),sx=matrix(covariateSE,n,q,true,"covariate SE"),r=matrix(covariateGeneticCorrelations,q,q,false,"genetic correlations"),intercept=matrix(samplingIntercepts,q+1,q+1,false,"sampling intercepts");
        symmetric(r,"genetic correlations");symmetric(intercept,"sampling intercepts");
        double[] d=new double[q],rhs=new double[q];
        for(int j=0;j<q;j++){if(r[j][j]!=1)throw new IllegalArgumentException("Prepared genetic correlations require unit diagonal; the source rg printout instead stores h2 on its diagonal");d[j]=StrictMath.sqrt(genetic[j]);rhs[j]=d[j]*bxy[j];numeric(rhs[j],"scaled causal effect overflow");}
        double[] joint=ldlt(r,rhs);for(int j=0;j<q;j++){joint[j]/=d[j];numeric(joint[j],"joint causal effect overflow");}
        double[] effect=new double[n],se=new double[n],p=new double[n];
        for(int i=0;i<n;i++) {
            cancellation();double adjustment=0,cross=0,vx=0;
            for(int j=0;j<q;j++) {
                adjustment+=x[i][j]*joint[j];cross+=sx[i][j]*sy[i]*joint[j]*intercept[0][j+1];
                for(int k=0;k<q;k++)vx+=joint[j]*sx[i][j]*(j==k?1:intercept[j+1][k+1])*sx[i][k]*joint[k];
            }
            inference(i,y[i]-adjustment,sy[i]*sy[i]+vx-2*cross,effect,se,p);
        }
        cancellation();return new Result(effect,se,p,joint);
    }
    private static void inference(int i,double effect,double variance,double[] effects,double[] se,double[] p) {
        numeric(effect,"conditional effect overflow");if(!Double.isFinite(variance)||variance<=0)throw new ArithmeticException("No complete native mtCOJO result: conditional variance is undefined at variant index "+i);
        double error=StrictMath.sqrt(variance),statistic=effect*effect/error/error;
        numeric(statistic,"conditional test statistic overflow");double probability=ChiSquare.cumulative(statistic,1,false,false);
        if(!Double.isFinite(probability)||probability<0||probability>1)throw new ArithmeticException("No complete native mtCOJO result: undefined conditional P value");
        effects[i]=effect;se[i]=error;p[i]=probability;
    }
    /** Symmetric one-by-one diagonal pivoting, as in the source LDLT profile; no alternate solver. */
    private static double[] ldlt(double[][] a,double[] rhs) {
        int n=rhs.length;double[][] l=new double[n][n];double[] d=new double[n];int[] permutation=new int[n];for(int i=0;i<n;i++){l[i][i]=1;permutation[i]=i;}
        for(int k=0;k<n;k++) {
            cancellation();int pivot=k;for(int i=k+1;i<n;i++)if(Math.abs(a[i][i])>Math.abs(a[pivot][pivot]))pivot=i;
            if(pivot!=k) {
                double[] row=a[k];a[k]=a[pivot];a[pivot]=row;for(int i=0;i<n;i++){double value=a[i][k];a[i][k]=a[i][pivot];a[i][pivot]=value;}
                for(int j=0;j<k;j++){double value=l[k][j];l[k][j]=l[pivot][j];l[pivot][j]=value;}int value=permutation[k];permutation[k]=permutation[pivot];permutation[pivot]=value;
            }
            d[k]=a[k][k];if(!Double.isFinite(d[k])||d[k]==0)throw new ArithmeticException("No complete native mtCOJO result: genetic correlation matrix has no unique source-profile LDLT solution");
            for(int i=k+1;i<n;i++){l[i][k]=a[i][k]/d[k];numeric(l[i][k],"LDLT multiplier overflow");}
            for(int i=k+1;i<n;i++)for(int j=i;j<n;j++){double value=a[i][j]-l[i][k]*d[k]*l[j][k];numeric(value,"LDLT update overflow");a[i][j]=value;a[j][i]=value;}
        }
        double[] result=new double[n],permuted=new double[n];
        for(int i=0;i<n;i++){double value=rhs[permutation[i]];for(int j=0;j<i;j++)value-=l[i][j]*permuted[j];permuted[i]=value;numeric(value,"LDLT forward solve overflow");}
        for(int i=0;i<n;i++){permuted[i]/=d[i];numeric(permuted[i],"LDLT diagonal solve overflow");}
        for(int i=n-1;i>=0;i--){for(int j=i+1;j<n;j++)permuted[i]-=l[j][i]*permuted[j];numeric(permuted[i],"LDLT backward solve overflow");result[permutation[i]]=permuted[i];}
        return result;
    }
    private static void capacity(int n,int q,Limits limits) {
        if(limits==null||n<1||n>limits.maximumVariants||q<1||q>limits.maximumCovariates)throw new IllegalArgumentException("Native mtCOJO dimensions exceed declared limits");
        try {
            long cells=Math.addExact(Math.addExact(Math.multiplyExact(4L*n,q),8L*n),Math.addExact(Math.multiplyExact(4L*q,q),8L*q));
            long work=Math.addExact(Math.multiplyExact((long)n,Math.multiplyExact((long)q,q)),Math.multiplyExact((long)q,Math.multiplyExact((long)q,q)));
            if(cells>limits.maximumCells||work>limits.maximumWork)throw new IllegalArgumentException("Native mtCOJO cell/work capacity exceeded before allocation");
        }catch(ArithmeticException e){throw new IllegalArgumentException("Native mtCOJO dimension arithmetic overflow",e);}
        cancellation();
    }
    private static double[] vector(double[] values,int n,boolean positive,String label) {
        if(values==null||values.length!=n)throw new IllegalArgumentException("Wrong native mtCOJO vector dimension: "+label);double[] copy=values.clone();
        for(double value:copy){finite(value,label);if(positive&&value<=0)throw new IllegalArgumentException("Require positive "+label);}return copy;
    }
    private static double[][] matrix(double[][] values,int rows,int columns,boolean positive,String label) {
        if(values==null||values.length!=rows)throw new IllegalArgumentException("Wrong native mtCOJO matrix dimension: "+label);double[][] copy=values.clone();
        for(int i=0;i<rows;i++)copy[i]=vector(copy[i],columns,positive,label);return copy;
    }
    private static void symmetric(double[][] values,String label) {
        for(int i=0;i<values.length;i++)for(int j=0;j<i;j++)if(values[i][j]!=values[j][i])throw new IllegalArgumentException("Require literal symmetric "+label+"; no symmetrization");
    }
    private static void finite(double value,String label){if(!Double.isFinite(value))throw new IllegalArgumentException("Require finite "+label);}
    private static void numeric(double value,String cause){if(!Double.isFinite(value))throw new ArithmeticException("No complete native mtCOJO result: "+cause);}
    private static void cancellation(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Native mtCOJO cancelled; no partial result admitted");}
}
