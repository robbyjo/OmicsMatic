/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Algorithms: hakyimlab/MetaXcan, MIT. Original notice is retained with the
 * metaxcan reference fixtures. Source identities are documented there. */
package org.jlinalg.genetics;

import java.util.Arrays;
import java.util.Objects;
import jdistlib.Normal;
import jdistlib.ChiSquare;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/**
 * Java arithmetic for original MetaXcan S-PrediXcan and S-MulTiXcan.
 * Arrays must already be intersected, allele-aligned and ordered by the source
 * input contract. This class does not read PredictDB, train expression models,
 * impute GWAS SNPs, select tissue models or establish sample/variant identity.
 * Missing GWAS betas are represented by a null vector, never by zero betas.
 * Invalid/nonfinite calculations throw instead of returning an admitted NaN.
 */
public final class MetaXcanMath {
    private MetaXcanMath() { }

    public record Summary(double zscore, Double effectSize, double variance,
                          double pvalue, double logPvalue) { }
    public record Calibrated(Summary uncalibrated, double zscore, double pvalue,
                             double logPvalue, double sourcePhi, double appliedPhi,
                             double denominator, boolean negativePhiReplacedByZero) { }
    public enum CutoffKind { EIGEN_RATIO, TRACE_RATIO, VARIANCE_THRESHOLD, CONDITION_NUMBER }
    public record Cutoff(CutoffKind kind, double value) {
        public Cutoff {
            Objects.requireNonNull(kind,"MetaXcan cutoff kind");
            require(Double.isFinite(value) && value>=0,"Cutoff must be finite and nonnegative");
            if(kind==CutoffKind.CONDITION_NUMBER)require(value>0,"Condition number must be positive");
            if(kind==CutoffKind.VARIANCE_THRESHOLD)require(value<=1,"Variance threshold must lie in [0,1]");
        }
    }
    /** Status -4 retains the original chi-square tail underflow diagnostic. */
    public record Joint(double statistic, double pvalue, double logPvalue, int components,
                        double maximumSingularValue, double minimumSingularValue,
                        double minimumKeptSingularValue, double traceMatrixInverse,
                        double absoluteCutoff, int bestTissueIndex, int worstTissueIndex,
                        double bestTissuePvalue, double worstTissuePvalue,
                        double minimumZ, double maximumZ, double meanZ, Double sdZ,
                        int sourceStatus) { }

    /**
     * Uses the covariance of the USED SNP intersection. Its denominator is not
     * the variance of all SNPs in the original prediction model. SNP covariance
     * is in raw-dosage units; no correlation or weight normalization is applied.
     * Zero-variance covariance rows are accepted when the total model variance
     * is positive, as in the original association calculation.
     */
    public static Summary summary(double[] weights,double[] zscores,double[] betas,double[] covariance) {
        int n=vector(weights,"Weights");
        require(vector(zscores,"GWAS z-scores")==n,"Weights and z-score axes differ");
        if(betas!=null)require(vector(betas,"GWAS betas")==n,"Weights and beta axes differ");
        GeneticCovarianceValidation.requirePositiveSemidefinite(covariance,n);
        double variance=0,numerator=0,effect=0;
        for(int j=0;j<n;j++) {
            double product=0;
            for(int i=0;i<n;i++)product+=weights[i]*covariance[i*n+j];
            variance+=product*weights[j];
            double diagonal=covariance[j*n+j];
            numerator+=weights[j]*zscores[j]*Math.sqrt(diagonal);
            if(betas!=null)effect+=weights[j]*betas[j]*diagonal;
        }
        require(Double.isFinite(variance)&&variance>0,"Predicted expression variance must be finite and positive; no result admitted");
        double z=numerator/Math.sqrt(variance);finite(z,"Summary z-score");
        Double beta=betas==null?null:effect/variance;
        if(beta!=null)finite(beta,"Summary effect size");
        return new Summary(z,beta,variance,normalP(z),normalLogP(z));
    }

    /**
     * Original release-tag v0.8.1 phi correction. The source replaces negative
     * phi by zero. Uncalibrated values and that replacement are retained.
     * Effect size and predicted variance are unchanged by the source correction.
     * No calibration is automatically applied to a historical release.
     */
    public static Calibrated calibrate(Summary original,double phi,long gwasSamples,double heritability) {
        Objects.requireNonNull(original,"Uncalibrated summary");
        finite(original.zscore(),"Uncalibrated z-score");finite(phi,"Phi");finite(heritability,"GWAS heritability");
        require(gwasSamples>0,"GWAS sample size must be positive");
        double applied=phi<0?0:phi;
        double denominator=1+(applied*gwasSamples)*heritability;
        require(Double.isFinite(denominator)&&denominator>0,"Phi calibration denominator must be finite and positive; no result admitted");
        double z=original.zscore()/Math.sqrt(denominator);finite(z,"Calibrated z-score");
        return new Calibrated(original,z,normalP(z),normalLogP(z),phi,applied,denominator,phi<0);
    }

    /**
     * Original real-valued S-MulTiXcan capped pseudoinverse. The caller supplies
     * the exact model-product matrix in the z-score tissue order. This method
     * does not normalize that matrix. The cutoff is calculated BEFORE optional
     * diagonal regularization, as upstream does. The first singular component
     * is always retained, including when the cutoff exceeds the largest value.
     */
    public static Joint joint(double[] zscores,double[] modelProduct,Cutoff cutoff,Double regularization) {
        int n=vector(zscores,"Tissue z-scores");Objects.requireNonNull(cutoff,"Explicit source cutoff");
        GeneticCovarianceValidation.requirePositiveSemidefinite(modelProduct,n);
        if(regularization!=null)require(Double.isFinite(regularization)&&regularization>=0,"Regularization must be finite and nonnegative");
        double[] a=modelProduct.clone();
        try(var context=BackendContext.select(BackendPolicy.CPU)) {
            var backend=context.backend();
            double absolute=cutoff(modelProduct,n,cutoff,backend.dsyev(modelProduct.clone(),n).eigenvalues());
            if(regularization!=null)for(int i=0;i<n;i++)a[i*n+i]+=regularization;
            for(double v:a)finite(v,"Regularized model product");
            var svd=backend.dgesvd(a,n,n);
            double[] s=svd.singularValues(),u=svd.leftSingularVectors(),vt=svd.rightSingularVectorsTransposed();
            require(s.length==n&&svd.components()==n,"Incomplete source SVD; no result admitted");
            double[] inverse=new double[n*n];int kept=0;
            for(int k=0;k<n;k++) {
                finite(s[k],"Singular value");
                require(s[k]>=0&&(k==0||s[k]<=s[k-1]),"Invalid SVD order; no result admitted");
                if(k!=0&&s[k]<absolute)continue;
                require(s[k]>0&&Double.isFinite(1/s[k]),"Selected zero or unresolved singular component; preserve source failure without an inverse");
                kept++;
                for(int i=0;i<n;i++)for(int j=0;j<n;j++)inverse[i*n+j]+=vt[k*n+i]*u[j*n+k]/s[k];
            }
            double statistic=0,trace=0;
            for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                finite(inverse[i*n+j],"Capped inverse");
                statistic+=zscores[i]*inverse[i*n+j]*zscores[j];
                trace+=modelProduct[i*n+j]*inverse[j*n+i];
            }
            require(Double.isFinite(statistic)&&statistic>=0,"Invalid joint statistic; no result admitted");finite(trace,"Source trace");
            int best=0,worst=0;double min=zscores[0],max=min,mean=0;
            for(int i=0;i<n;i++) {
                if(Math.abs(zscores[i])>Math.abs(zscores[best]))best=i;
                if(Math.abs(zscores[i])<Math.abs(zscores[worst]))worst=i;
                min=Math.min(min,zscores[i]);max=Math.max(max,zscores[i]);mean+=zscores[i];
            }
            mean/=n;finite(mean,"Mean tissue z-score");
            Double sd=null;
            if(n>1) {double ss=0;for(double z:zscores)ss+=(z-mean)*(z-mean);sd=Math.sqrt(ss/(n-1));finite(sd,"SD of tissue z-scores");}
            double p=ChiSquare.cumulative(statistic,kept,false,false),log=ChiSquare.cumulative(statistic,kept,false,true);
            finite(p,"Joint p-value");
            return new Joint(statistic,p,log,kept,s[0],s[n-1],s[kept-1],trace,absolute,best,worst,
                    normalP(zscores[best]),normalP(zscores[worst]),min,max,mean,sd,p==0?-4:0);
        }
    }

    private static double cutoff(double[] matrix,int n,Cutoff option,double[] eigenvalues) {
        double v=option.value();
        if(option.kind()==CutoffKind.CONDITION_NUMBER)v=1/v;
        if(v==0)return 0;
        double[] eigen=eigenvalues.clone();Arrays.sort(eigen);
        double result;
        if(option.kind()==CutoffKind.EIGEN_RATIO||option.kind()==CutoffKind.CONDITION_NUMBER)result=v*eigen[n-1];
        else if(option.kind()==CutoffKind.TRACE_RATIO) {double trace=0;for(int i=0;i<n;i++)trace+=matrix[i*n+i];result=v*trace;}
        else {
            // Preserve the source's strict cumulative comparison and previous
            // eigenvalue. This differs from a generic explained-variance cutoff.
            double total=0;for(int i=n-1;i>=0;i--)total+=eigen[i];
            double objective=total*(1-v),sum=0,last=eigen[n-1];
            for(int i=n-1;i>=0;i--) {sum+=eigen[i];if(sum>objective)break;last=eigen[i];}
            result=last;
        }
        require(Double.isFinite(result)&&result>=0,"Invalid absolute source cutoff; no result admitted");return result;
    }
    private static int vector(double[] a,String label) {
        require(a!=null&&a.length>0,label+" must contain at least one finite value");
        for(double v:a)finite(v,label);return a.length;
    }
    private static double normalP(double z){return 2*Normal.cumulative(-Math.abs(z),0,1,true,false);}
    private static double normalLogP(double z){return Math.log(2)+Normal.cumulative(-Math.abs(z),0,1,true,true);}
    private static void finite(double v,String label){require(Double.isFinite(v),label+" must be finite; no result admitted");}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}