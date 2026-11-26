/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics.methylation;

import java.util.Objects;

/**
 * ALLCools 1.0.8 region count saturation and hypo/hyper-score quantification.
 * Derived from ALLCools/count_matrix/dataset.py, Copyright (c) 2019-2022
 * Hanqing Liu (MIT; accompanying ALLCOOLS-LICENSE.txt).
 * Each call consumes one complete region axis for ONE cell and methylation type.
 * Region construction, ALLC parsing, pattern aggregation and Zarr IO are separate.
 */
public final class AllcoolsRegionQuantifier {
    public static final long UINT32_MAX = 4294967295L;
    private AllcoolsRegionQuantifier() { }

    /** Probabilities and cutoffs follow NumPy 1.23.5's float16 array arithmetic. */
    public record Result(long sumMc, long sumCov, double pooledProbability,
                         long[] countsMc, long[] countsCov,
                         double[] hypo, double[] hyper,
                         int saturatedMc, int saturatedCov, int zeroCoverage) {
        public Result {
            countsMc=countsMc.clone(); countsCov=countsCov.clone();
            hypo=hypo.clone(); hyper=hyper.clone();
        }
        @Override public long[] countsMc(){return countsMc.clone();}
        @Override public long[] countsCov(){return countsCov.clone();}
        @Override public double[] hypo(){return hypo.clone();}
        @Override public double[] hyper(){return hyper.clone();}
    }

    /**
     * Raw nonnegative int64 counts are used for scoring, BEFORE uint32 saturation.
     * Summation overflow is refused rather than reproducing NumPy int64 wraparound.
     * Cutoffs must be finite and in [0,1]; both quantifiers are returned explicitly.
     */
    public static Result calculate(long[] mc,long[] cov,double hypoCutoff,double hyperCutoff,Runnable check) {
        Objects.requireNonNull(mc);Objects.requireNonNull(cov);Objects.requireNonNull(check);check.run();
        if(mc.length==0||mc.length!=cov.length)throw new IllegalArgumentException("Require nonempty identical complete region axes");
        cutoff(hypoCutoff);cutoff(hyperCutoff);
        long sumMc=0,sumCov=0;int saturatedMc=0,saturatedCov=0,zero=0;
        long[] outMc=new long[mc.length],outCov=new long[mc.length];
        for(int i=0;i<mc.length;i++) {
            if((i&1023)==0)check.run();
            if(mc[i]<0||cov[i]<mc[i])throw new IllegalArgumentException("Require raw 0 <= mc <= cov <= int64 maximum; counts are not repaired");
            sumMc=Math.addExact(sumMc,mc[i]);sumCov=Math.addExact(sumCov,cov[i]);
            outMc[i]=Math.min(mc[i],UINT32_MAX);outCov[i]=Math.min(cov[i],UINT32_MAX);
            if(mc[i]>UINT32_MAX)saturatedMc++;if(cov[i]>UINT32_MAX)saturatedCov++;if(cov[i]==0)zero++;
        }
        double p=(double)sumMc/((double)sumCov+0.000001);
        double lowCut=half(hypoCutoff),highCut=half(hyperCutoff);
        double[] hypo=new double[mc.length],hyper=new double[mc.length];
        for(int i=0;i<mc.length;i++) {
            if((i&1023)==0)check.run();
            // Original _bin_sf returns zero whenever cov <= mc, including 0/0.
            double sf=cov[i]>mc[i]?jdistlib.Binomial.cumulative(mc[i],cov[i],p,false,false):0;
            if(!Double.isFinite(sf)||sf<0||sf>1)throw new ArithmeticException("Binomial strict survival probability is outside [0,1]");
            double h=half(sf),reverse=half(1-h);
            hypo[i]=h<lowCut?0:h;hyper[i]=reverse<highCut?0:reverse;
        }
        check.run();return new Result(sumMc,sumCov,p,outMc,outCov,hypo,hyper,saturatedMc,saturatedCov,zero);
    }
    private static void cutoff(double x) {
        if(!Double.isFinite(x)||x<0||x>1)throw new IllegalArgumentException("Declare a finite score cutoff in [0,1]");
    }
    /** Exact round-to-nearest, ties-to-even binary16 conversion on [0,1]. */
    public static double half(double x) {
        if(!Double.isFinite(x)||x<0||x>1)throw new IllegalArgumentException("Binary16 probability must be finite and in [0,1]");
        double step=x<0x1.0p-14?0x1.0p-24:Math.scalb(1.0,Math.getExponent(x)-10);
        return Math.rint(x/step)*step;
    }
}
