/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import java.util.Locale;
import org.jlinalg.shrinkage.MashGaussian;
import org.jlinalg.shrinkage.MashMixtureWeights;

/** Synthetic fixed-covariance API demonstration; no paper analysis or covariance learning. */
public final class MashGaussianExample {
    private MashGaussianExample() { }
    public static void main(String[] args) {
        double[][] effects={{1,2},{-1,0},{0,0},{-2,-4}};
        double[][] errors={{1,1},{1,1},{1,1},{1,1}};
        var covariances=List.<double[][]>of(new double[][]{{0,0},{0,0}},
            new double[][]{{1,0},{0,1}},new double[][]{{1,1},{1,1}});
        double[][] residual={{1,0},{0,1}};
        var options=MashGaussian.Options.defaults();var limits=MashGaussian.Limits.defaults();
        var preliminary=MashGaussian.prepare(covariances,new double[]{.2,.4,.4},residual,options,limits,()->{});
        double[][] logLikelihoods=new double[effects.length][];
        for(int row=0;row<effects.length;row++)logLikelihoods[row]=preliminary.evaluate(effects[row],errors[row],()->{}).componentLogLikelihoods();
        // Every synthetic training row is included. A real analysis must explicitly
        // supply its representative training population, separately from strong hits.
        var fit=MashMixtureWeights.fit(logLikelihoods,new double[]{10,1,1},null,
            MashMixtureWeights.Options.defaults(),MashMixtureWeights.Limits.defaults(),()->{});
        for(String note:fit.numericalNotes())System.err.println("WARNING: "+note);
        fit.requireConverged();
        var model=MashGaussian.prepare(covariances,fit.proportions(),residual,options,limits,()->{});
        System.out.println("SYNTHETIC native fixed-mixture demonstration; no learned covariance or paper reproduction.");
        System.out.println("feature\tcondition\tposterior_mean\tposterior_sd\tzero_probability\tlfsr");
        for(int row=0;row<effects.length;row++){
            var result=model.evaluate(effects[row],errors[row],()->{});
            for(String note:result.numericalNotes())System.err.println("WARNING: "+note+" Required action: inspect the named numerical condition before using this result.");
            for(int condition=0;condition<model.conditions();condition++)System.out.printf(Locale.ROOT,
                "%d\t%d\t%.9g\t%.9g\t%.9g\t%.9g%n",row+1,condition+1,result.mean()[condition],result.standardDeviation()[condition],result.zeroProbability()[condition],result.lfsr()[condition]);
        }
    }
}
