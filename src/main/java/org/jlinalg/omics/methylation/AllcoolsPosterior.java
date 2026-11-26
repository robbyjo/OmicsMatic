package org.jlinalg.omics.methylation;

import java.util.Objects;

/**
 * Complete feature-axis posterior fractions and M values from ALLCools 1.0.8.
 * Derived from ALLCools/mcds/{utilities,mcds}.py, Copyright (c) 2019-2022
 * Hanqing Liu (MIT; accompanying ALLCOOLS-LICENSE.txt).
 * Contiguous float64 reductions follow NumPy 1.23.5's pairwise ordering
 * (NumPy developers, BSD-3-Clause; accompanying NUMPY-LICENSE.txt).
 * This class does not construct a dataset, cluster cells or test DMRs.
 */
public final class AllcoolsPosterior {
    private AllcoolsPosterior() {}
    public enum Representation { NUMPY, XARRAY }
    public enum Operation { POSTERIOR_FRACTION, M_VALUE }
    public record Settings(Representation representation, Operation operation,
                           boolean normalizePerCell, Double clip, double mValueAlpha) {
        public Settings {
            Objects.requireNonNull(representation); Objects.requireNonNull(operation);
            if (clip != null && (!Double.isFinite(clip) || clip < 0))
                throw new IllegalArgumentException("Clip must be null or finite and nonnegative");
            if (!Double.isFinite(mValueAlpha) || mValueAlpha < 0)
                throw new IllegalArgumentException("M-value alpha must be finite and nonnegative");
            if (operation == Operation.M_VALUE && clip != null)
                throw new IllegalArgumentException("M values require clip:null; source does not clip M values");
            if (operation == Operation.M_VALUE && representation != Representation.XARRAY)
                throw new IllegalArgumentException("MCDS M values require representation:xarray");
        }
    }
    public record Result(double mean, double variance, double alpha, double beta,
                         double priorMean, double normalizationDivisor,
                         int coveredFeatures, int zeroCoverageFeatures,
                         int preclipNaNsReplaced, int clippedFeatures,
                         double[] posterior, double[] values) {
        public Result { posterior=posterior.clone(); values=values.clone(); }
        @Override public double[] posterior() { return posterior.clone(); }
        @Override public double[] values() { return values.clone(); }
    }
    /** Each call consumes ALL features for one cell and one methylation type. */
    public static Result calculate(long[] mc, long[] cov, Settings settings, Runnable check) {
        Objects.requireNonNull(mc); Objects.requireNonNull(cov);
        Objects.requireNonNull(settings); Objects.requireNonNull(check); check.run();
        if (mc.length == 0 || mc.length != cov.length)
            throw new IllegalArgumentException("Require nonempty, identical complete feature axes");
        int n=mc.length, covered=0, replaced=0, clipped=0;
        double[] raw=new double[n];
        for (int i=0;i<n;i++) {
            if ((i & 4095)==0) check.run();
            if (mc[i]<0 || cov[i]<mc[i] || cov[i]>4294967295L)
                throw new IllegalArgumentException("Require 0 <= mc <= cov <= uint32 maximum; no count repair");
            if (cov[i]!=0) covered++;
            raw[i]=(double)mc[i]/cov[i];
        }
        double mean=nanSum(raw,0,n,check)/covered;
        double[] squares=new double[n];
        for (int i=0;i<n;i++) { if ((i & 4095)==0) check.run(); double d=raw[i]-mean; squares[i]=d*d; }
        double variance=nanSum(squares,0,n,check)/covered;
        double alpha=(1-mean)*(mean*mean)/variance-mean;
        double beta=alpha*(1/mean-1), prior=alpha/(alpha+beta);
        double[] posterior=new double[n], values=new double[n];
        for (int i=0;i<n;i++) {
            if ((i & 4095)==0) check.run();
            posterior[i]=(mc[i]+alpha)/(cov[i]+alpha+beta);
            values[i]=settings.operation()==Operation.POSTERIOR_FRACTION ? posterior[i]
                : StrictMath.log((posterior[i]+settings.mValueAlpha())/(1-posterior[i]+settings.mValueAlpha()))/StrictMath.log(2);
        }
        double divisor=1;
        if (settings.normalizePerCell()) {
            if (settings.operation()==Operation.POSTERIOR_FRACTION) divisor=prior;
            else {
                int present=0; double magnitude=0;
                for (double v:values) { if (!Double.isNaN(v)) present++; if (Double.isFinite(v)) magnitude=Math.max(magnitude,Math.abs(v)); }
                divisor=nanSum(values,0,n,check)/present;
                double minimum=32*Math.ulp(magnitude)/1e-11;
                if (present>0 && Double.isFinite(divisor) && Math.abs(divisor)<=minimum)
                    throw new ArithmeticException("M-value normalization mean is too close to zero for the declared numerical admission budget: mean="+divisor+", required abs(mean)>"+minimum
                        +". No normalized M values admitted. Review the original source; explicitly choose normalize_per_cell:false for unnormalized M values if appropriate.");
            }
            for (int i=0;i<n;i++) {
                if ((i & 4095)==0) check.run();
                values[i]/=divisor;
                if (settings.operation()==Operation.POSTERIOR_FRACTION && settings.clip()!=null) {
                    double before=values[i], limit=settings.clip();
                    // These predicates intentionally differ for NaN, exactly as upstream.
                    boolean replace=settings.representation()==Representation.NUMPY ? before>limit : !(before<limit);
                    if (replace) { values[i]=limit; if (Double.isNaN(before)) replaced++; else if (before>limit) clipped++; }
                }
            }
        }
        check.run();
        return new Result(mean,variance,alpha,beta,prior,divisor,covered,n-covered,replaced,clipped,posterior,values);
    }
    // NaN slots contribute zero in-place; do not compact the array before reduction.
    private static double present(double value) { return Double.isNaN(value)?0:value; }
    private static double nanSum(double[] values,int first,int length,Runnable check) {
        check.run();
        if (length<8) { double s=-0.0; for(int i=first;i<first+length;i++)s+=present(values[i]); return s; }
        if (length>128) { int left=(length/2)/8*8; return nanSum(values,first,left,check)+nanSum(values,first+left,length-left,check); }
        double[] partial=new double[8];for(int j=0;j<8;j++)partial[j]=present(values[first+j]);
        int end=length-length%8;
        for(int i=8;i<end;i+=8)for(int j=0;j<8;j++)partial[j]+=present(values[first+i+j]);
        double sum=((partial[0]+partial[1])+(partial[2]+partial[3]))+((partial[4]+partial[5])+(partial[6]+partial[7]));
        for(int i=end;i<length;i++)sum+=present(values[first+i]);return sum;
    }
}
