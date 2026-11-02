/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class MashGaussianTest {
    private static final Runnable CHECK=()->{};
    private static final MashGaussian.Options OPTIONS=MashGaussian.Options.defaults();
    private static final MashGaussian.Limits LIMITS=MashGaussian.Limits.defaults();
    private static List<double[][]> matrices(double[][]... u){return Arrays.asList(u);}
    private static MashGaussian.Posterior single(double effect,double se,double variance){
        return MashGaussian.prepare(matrices(new double[][]{{variance}}),new double[]{1},new double[][]{{1}},OPTIONS,LIMITS,CHECK).evaluate(new double[]{effect},new double[]{se},CHECK);
    }
    @Test void univariateGaussianConjugacy(){
        var r=single(2,3,4);assertEquals(8./13,r.mean()[0],1e-14);assertEquals(Math.sqrt(36./13),r.standardDeviation()[0],1e-14);
        assertEquals(-.5*(Math.log(2*Math.PI*13)+4./13),r.logLikelihood(),1e-14);
        assertEquals(0,r.zeroProbability()[0]);assertEquals(36./13,r.covariance()[0][0],1e-14);
    }
    @Test void nullEffectsAreZeroMassAndLfsrOne(){
        var r=single(-3,1,0);assertEquals(0,r.mean()[0]);assertEquals(0,r.standardDeviation()[0]);assertEquals(0,r.negativeProbability()[0]);assertEquals(1,r.zeroProbability()[0]);assertEquals(1,r.lfsr()[0]);
    }
    @Test void singularSharedMixtureHasAnalyticPosterior(){
        var r=MashGaussian.prepare(matrices(new double[][]{{0,0},{0,0}},new double[][]{{1,1},{1,1}}),new double[]{.2,.8},new double[][]{{1,0},{0,1}},OPTIONS,LIMITS,CHECK).evaluate(new double[]{1,2},new double[]{1,1},CHECK);
        double bf=Math.exp(1.5)/Math.sqrt(3),w=.8*bf/(.2+.8*bf),variance=w*(1./3+1)-w*w;
        assertArrayEquals(new double[]{w,w},r.mean(),1e-14);assertArrayEquals(new double[]{Math.sqrt(variance),Math.sqrt(variance)},r.standardDeviation(),1e-14);
        assertEquals(1-w,r.zeroProbability()[0],1e-14);assertEquals(variance,r.covariance()[0][1],1e-14);
        assertEquals(1-w+w*jdistlib.Normal.cumulative(-Math.sqrt(3),0,1,true,false),r.lfsr()[0],1e-14);
    }
    @Test void correlatedErrorsHaveAnalyticGaussianSolution(){
        var model=MashGaussian.prepare(matrices(new double[][]{{1,0},{0,1}}),new double[]{1},new double[][]{{1,.5},{.5,1}},OPTIONS,LIMITS,CHECK);
        var r=model.evaluate(new double[]{1,2},new double[]{1,1},CHECK);
        assertArrayEquals(new double[]{4./15,14./15},r.mean(),1e-14);
        assertEquals(7./15,r.covariance()[0][0],1e-14);assertEquals(2./15,r.covariance()[0][1],1e-14);
    }
    @Test void uninformativeSecondMeasurementCanStillHaveConditionalCorrelationInformation(){
        var r=MashGaussian.prepare(matrices(new double[][]{{1,0},{0,1}}),new double[]{1},new double[][]{{1,.5},{.5,1}},OPTIONS,LIMITS,CHECK).evaluate(new double[]{1,0},new double[]{1,1e10},CHECK);
        assertEquals(1/1.75,r.mean()[0],1e-12);assertEquals(.75/1.75,r.covariance()[0][0],1e-12);assertEquals(1,r.covariance()[1][1],1e-12);
        assertTrue(r.mean()[1]<0);assertTrue(r.mean()[1]>-1e-9);
    }
    @Test void alphaOneGivesExchangeableZScoresAndOriginalScaleOutput(){
        var options=new MashGaussian.Options(1,1e-10,1e-12,Math.ulp(1.),1e-7,true);
        var r=MashGaussian.prepare(matrices(new double[][]{{1}}),new double[]{1},new double[][]{{1}},options,LIMITS,CHECK).evaluate(new double[]{4},new double[]{2},CHECK);
        assertEquals(2,r.mean()[0],1e-14);assertEquals(Math.sqrt(2),r.standardDeviation()[0],1e-14);
        assertEquals(Math.log(2),r.scaledLogLikelihood()-r.logLikelihood(),1e-14);
    }
    @Test void strictPriorThresholdDoesNotAlterFullMixtureLikelihood(){
        var priors=matrices(new double[][]{{0}},new double[][]{{1}});double[] pi={1-1e-10,1e-10};
        var pruned=MashGaussian.prepare(priors,pi,new double[][]{{1}},OPTIONS,LIMITS,CHECK).evaluate(new double[]{2},new double[]{1},CHECK);
        var unpruned=MashGaussian.prepare(priors,pi,new double[][]{{1}},new MashGaussian.Options(0,0,1e-12,Math.ulp(1.),1e-7,true),LIMITS,CHECK).evaluate(new double[]{2},new double[]{1},CHECK);
        assertEquals(pruned.logLikelihood(),unpruned.logLikelihood());assertEquals(1,pruned.lfsr()[0]);assertEquals(0,pruned.posteriorWeights()[1]);assertTrue(unpruned.posteriorWeights()[1]>0);
    }
    @Test void canonicalConditionNamesAndFullSourceOrder(){
        var basis=MashGaussian.canonical(List.of("brain","blood"),LIMITS,CHECK);
        assertEquals(List.of("identity","brain","blood","equal_effects","simple_het_1","simple_het_2","simple_het_3"),basis.names());
        assertArrayEquals(new double[]{1,0},basis.covariances().get(1)[0]);assertArrayEquals(new double[]{0,0},basis.covariances().get(1)[1]);assertEquals(.75,basis.covariances().get(6)[0][1]);
    }
    @Test void completeCanonicalAllocationIsAdmittedBeforeConstruction(){
        assertThrows(IllegalArgumentException.class,()->MashGaussian.canonical(2048));
        assertThrows(IllegalArgumentException.class,()->MashGaussian.canonical(100,new MashGaussian.Limits(100,200,1_000_000_000,65536),CHECK));
        assertThrows(IllegalArgumentException.class,()->MashGaussian.canonical(List.of("a","a"),LIMITS,CHECK));
    }
    @Test void preparedModelAndResultsOwnTheirArrays(){
        double[][] u={{1,0},{0,1}},v={{1,0},{0,1}};double[] pi={1};
        var model=MashGaussian.prepare(matrices(u),pi,v,OPTIONS,LIMITS,CHECK);u[0][0]=99;v[0][0]=99;pi[0]=0;
        var r=model.evaluate(new double[]{2,4},new double[]{1,1},CHECK);r.mean()[0]=99;r.covariance()[0][0]=99;model.proportions()[0]=0;
        assertArrayEquals(new double[]{1,2},r.mean(),1e-14);assertEquals(.5,r.covariance()[0][0],1e-14);assertEquals(1,model.proportions()[0]);
    }
    @Test void structuralZeroRowRemainsPointMassInNonNullComponent(){
        var r=MashGaussian.prepare(matrices(new double[][]{{1,0},{0,0}}),new double[]{1},new double[][]{{1,.5},{.5,1}},OPTIONS,LIMITS,CHECK).evaluate(new double[]{1,2},new double[]{1,1},CHECK);
        assertEquals(0,r.mean()[1]);assertEquals(1,r.zeroProbability()[1]);assertEquals(1,r.lfsr()[1]);assertEquals(0,r.covariance()[1][0]);
    }
    @Test void invalidCovariancesAreRejectedWithoutJitter(){
        for(double[][] u:matrices(new double[][]{{1,2},{2,1}},new double[][]{{0,1},{1,1}},new double[][]{{1,.1},{.2,1}},new double[][]{{Double.NaN,0},{0,1}}))
            assertThrows(IllegalArgumentException.class,()->MashGaussian.prepare(matrices(u),new double[]{1},new double[][]{{1,0},{0,1}},OPTIONS,LIMITS,CHECK));
        assertThrows(RuntimeException.class,()->MashGaussian.prepare(matrices(new double[][]{{1,0},{0,1}}),new double[]{1},new double[][]{{1,1},{1,1}},OPTIONS,LIMITS,CHECK));
    }
    @Test void noEffectsOrMissingnessAreInvented(){
        var m=MashGaussian.prepare(matrices(new double[][]{{1}}),new double[]{1},new double[][]{{1}},OPTIONS,LIMITS,CHECK);
        for(double s:new double[]{0,-1,Math.ulp(1.),Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->m.evaluate(new double[]{1},new double[]{s},CHECK));
        assertThrows(IllegalArgumentException.class,()->m.evaluate(new double[]{Double.NaN},new double[]{1},CHECK));assertThrows(IllegalArgumentException.class,()->m.evaluate(new double[0],new double[0],CHECK));
    }
    @Test void ambiguousLikelihoodResolutionIsExplicitFailure(){
        assertThrows(ArithmeticException.class,()->single(1e100,1,1));
    }
    @Test void cancellationDoesNotReturnAComponentPrefix(){
        assertThrows(CancellationException.class,()->MashGaussian.canonical(3,LIMITS,()->{throw new CancellationException("stop");}));
        var model=MashGaussian.prepare(matrices(new double[][]{{1}}),new double[]{1},new double[][]{{1}},OPTIONS,LIMITS,CHECK);
        assertThrows(CancellationException.class,()->model.evaluate(new double[]{1},new double[]{1},()->{throw new CancellationException("stop");}));
    }
    @Test void disablingCovarianceRetainsMarginalResults(){
        var options=new MashGaussian.Options(0,1e-10,1e-12,Math.ulp(1.),1e-7,false);
        var r=MashGaussian.prepare(matrices(new double[][]{{1}}),new double[]{1},new double[][]{{1}},options,LIMITS,CHECK).evaluate(new double[]{2},new double[]{1},CHECK);
        assertNull(r.covariance());assertEquals(1,r.mean()[0],1e-14);assertEquals(Math.sqrt(.5),r.standardDeviation()[0],1e-14);
    }
}
