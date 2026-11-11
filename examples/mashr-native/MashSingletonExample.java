/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import java.util.Locale;
import org.jlinalg.shrinkage.MashGaussian;
import org.jlinalg.shrinkage.MashMixtureWeights;

/** One real synthetic feature, no padding; native fixed-covariance fit and posterior. */
public final class MashSingletonExample {
    private MashSingletonExample() { }
    public static void main(String[] args) {
        var priors=List.<double[][]>of(new double[][]{{0,0},{0,0}},new double[][]{{100,100},{100,100}});
        double[][] residual={{1,0},{0,1}};double[] effect={10,10},se={1,1};
        var options=MashGaussian.Options.defaults();var limits=MashGaussian.Limits.defaults();
        var preliminary=MashGaussian.prepare(priors,new double[]{.5,.5},residual,options,limits,()->{});
        double[][] likelihoods={preliminary.evaluate(effect,se,()->{}).componentLogLikelihoods()};
        var fit=MashMixtureWeights.fit(likelihoods,new double[]{10,1},null,MashMixtureWeights.Options.defaults(),MashMixtureWeights.Limits.defaults(),()->{}).requireConverged();
        var result=MashGaussian.prepare(priors,fit.proportions(),residual,options,limits,()->{}).evaluate(effect,se,()->{});
        // At this signal strength, nonnull posterior mass differs from one by
        // less than1e-39. The analytic conditional mean/variance are2000/201 and100/201.
        if(Math.abs(fit.proportions()[0]-.9)>1e-12||Math.abs(fit.proportions()[1]-.1)>1e-12)throw new AssertionError("Singleton pseudocount fit differs");
        for(int c=0;c<2;c++)if(Math.abs(result.mean()[c]-2000./201)>1e-12||Math.abs(result.standardDeviation()[c]-Math.sqrt(100./201))>1e-12)throw new AssertionError("Singleton analytic posterior differs");
        for(String note:fit.numericalNotes())System.err.println("WARNING: "+note);
        for(String note:result.numericalNotes())System.err.println("WARNING: "+note);
        System.out.println("SYNTHETIC: one feature, two conditions; native API only. No original-wrapper or complete-workflow admission.");
        System.out.printf(Locale.ROOT,"fitted_null=%.9g fitted_nonnull=%.9g mean=%.12g sd=%.12g%n",fit.proportions()[0],fit.proportions()[1],result.mean()[0],result.standardDeviation()[0]);
    }
}
