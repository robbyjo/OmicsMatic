/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import jdistlib.ChiSquare;
import jdistlib.Normal;

/** Independent-cohort, sample-size-weighted signed-Z meta-analysis.
 *
 * <p>Weights are caller-declared positive effective sample sizes, not inverse
 * standard errors. Input statistics must already describe the same tested
 * allele and hypothesis in mutually independent cohorts. This class neither
 * harmonizes alleles nor estimates overlap, genomic control or effective N.
 * It implements the weighted-Z mathematics, not the METAL command interpreter.
 * Input arrays must remain immutable for the duration of the call.
 */
public final class SampleSizeWeightedMetaAnalysis {
    private static final double LOG_TWO=Math.log(2.0);

    public record Limits(int maximumStudies,long maximumEstimatedBytes) {
        public Limits {
            require(maximumStudies>0&&maximumEstimatedBytes>0,"Positive study and estimated-byte limits required");
        }
        public static Limits defaults(){return new Limits(1_000_000,128L<<20);}
    }

    /** No beta/SE in original trait units is inferred from a combined Z.
     * Heterogeneity P/logP/I2 are null for a single participating cohort.
     * The I2 percentage uses its ordinary nonnegative convention.
     * A zero P with a finite log P is retained as numerical underflow.
     */
    public record Result(int studies,double totalWeight,double z,double pValue,
            double logPValue,boolean pValueUnderflow,double heterogeneityQ,
            int heterogeneityDegreesOfFreedom,Double heterogeneityPValue,
            Double heterogeneityLogPValue,boolean heterogeneityPValueUnderflow,
            Double iSquaredPercent,double[] studyZ,double[] squareRootFractionWeights,
            double[] zContributions,double[] heterogeneityContributions,
            long estimatedRetainedBytes) {
        public Result {
            studyZ=studyZ.clone();squareRootFractionWeights=squareRootFractionWeights.clone();
            zContributions=zContributions.clone();heterogeneityContributions=heterogeneityContributions.clone();
        }
        @Override public double[] studyZ(){return studyZ.clone();}
        @Override public double[] squareRootFractionWeights(){return squareRootFractionWeights.clone();}
        @Override public double[] zContributions(){return zContributions.clone();}
        @Override public double[] heterogeneityContributions(){return heterogeneityContributions.clone();}
        public boolean heterogeneityDefined(){return heterogeneityDegreesOfFreedom>0;}
    }

    private SampleSizeWeightedMetaAnalysis(){ }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalArgumentException(why);}
    private static double finite(double value){
        if(!Double.isFinite(value))throw new ArithmeticException("Unrepresentable weighted-Z intermediate/result; no studies or terms removed");
        return value;
    }
    private static long admit(int count,Limits limits){
        require(limits!=null&&count>0&&count<=limits.maximumStudies(),"Nonempty complete cohort population within declared capacity required");
        long bytes=Math.addExact(8192L,Math.multiplyExact(128L,count));
        require(bytes<=limits.maximumEstimatedBytes(),"Complete weighted-Z working/result arrays exceed estimated-byte capacity; no truncation");
        return bytes;
    }
    private static final class Sum {
        double value,correction;
        void add(double term){double adjusted=finite(term-correction),next=finite(value+adjusted);correction=finite((next-value)-adjusted);value=next;}
    }

    public static Result fitSignedZ(double[] z,double[] weights){return fitSignedZ(z,weights,Limits.defaults());}

    /** Uses Z = sum_i sqrt(N_i/sum N) Z_i, retaining every declared cohort.
     * Heterogeneity is sum_i (Z_i-sqrt(N_i/sum N) Z)^2 under the common
     * standardized-effect model. No missingness, filtering or sign is inferred.
     */
    public static Result fitSignedZ(double[] signedZ,double[] weights,Limits limits){
        require(signedZ!=null&&weights!=null&&signedZ.length==weights.length,"Matching complete Z and weight arrays required");
        int n=signedZ.length;long bytes=admit(n,limits);double[] z=signedZ.clone(),w=weights.clone();
        Sum total=new Sum();for(int i=0;i<n;i++){require(Double.isFinite(z[i])&&Double.isFinite(w[i])&&w[i]>0,"Every cohort needs a finite signed Z and positive finite effective sample size; no omission/imputation");total.add(w[i]);}
        double root=Math.sqrt(total.value);double[] fraction=new double[n],contribution=new double[n],heterogeneity=new double[n];Sum combined=new Sum();
        for(int i=0;i<n;i++){
            // Taking square roots before dividing avoids premature N_i/sum(N)
            // underflow when a small cohort still has a representable Z term.
            fraction[i]=finite(Math.sqrt(w[i])/root);contribution[i]=finite(fraction[i]*z[i]);combined.add(contribution[i]);
        }
        double result=finite(combined.value);double logP=finite(LOG_TWO+Normal.cumulative(Math.abs(result),0,1,false,true));
        require(logP<=0,"Invalid normal-tail probability; no P-value clamping");double p=Math.exp(logP);
        Sum q=new Sum();for(int i=0;i<n;i++){double residual=finite(z[i]-finite(fraction[i]*result));heterogeneity[i]=finite(residual*residual);q.add(heterogeneity[i]);}
        int df=n-1;Double qP=null,qLogP=null,i2=null;
        if(df>0){qLogP=finite(ChiSquare.cumulative(q.value,df,false,true));require(qLogP<=0,"Invalid heterogeneity probability; no P-value clamping");qP=Math.exp(qLogP);i2=q.value>df?100.0*(1.0-df/q.value):0.0;}
        return new Result(n,total.value,result,p,logP,p==0.0,q.value,df,qP,qLogP,qP!=null&&qP==0.0,i2,z,fraction,contribution,heterogeneity,bytes);
    }

    public static Result fitPValues(double[] twoSidedP,int[] directions,double[] weights){return fitPValues(twoSidedP,directions,weights,Limits.defaults());}

    /** Directions must explicitly be -1 or +1. Exact input P=0 is refused:
     * supply its finite natural log through fitLogPValues if it underflowed.
     * P/2 is evaluated in log space rather than underflowing before inversion.
     */
    public static Result fitPValues(double[] twoSidedP,int[] directions,double[] weights,Limits limits){
        require(twoSidedP!=null&&directions!=null&&weights!=null&&twoSidedP.length==directions.length&&weights.length==directions.length,"Matching complete P/direction/weight arrays required");admit(twoSidedP.length,limits);
        double[] logs=new double[twoSidedP.length];for(int i=0;i<logs.length;i++){double p=twoSidedP[i];require(Double.isFinite(p)&&p>0&&p<=1,"Two-sided P must be in (0,1]; no replacement of zeros, missing or invalid values");logs[i]=Math.log(p);}
        return fitLogPValues(logs,directions,weights,limits);
    }

    public static Result fitLogPValues(double[] naturalLogTwoSidedP,int[] directions,double[] weights){return fitLogPValues(naturalLogTwoSidedP,directions,weights,Limits.defaults());}

    /** Finite natural logarithms of two-sided P in (0,1], not -log10(P).
     * The caller must declare the correct log base before using this method.
     */
    public static Result fitLogPValues(double[] naturalLogTwoSidedP,int[] directions,double[] weights,Limits limits){
        require(naturalLogTwoSidedP!=null&&directions!=null&&weights!=null&&naturalLogTwoSidedP.length==directions.length&&weights.length==directions.length,"Matching complete logP/direction/weight arrays required");admit(directions.length,limits);
        double[] z=new double[directions.length];for(int i=0;i<z.length;i++){
            double logP=naturalLogTwoSidedP[i];require(Double.isFinite(logP)&&logP<=0&&(directions[i]==-1||directions[i]==1),"Finite natural logP<=0 and explicit -1/+1 directions required; no sign inference");
            z[i]=finite(directions[i]*Normal.quantile(finite(logP-LOG_TWO),0,1,false,true));
        }
        return fitSignedZ(z,weights,limits);
    }
}
