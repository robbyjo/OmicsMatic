/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class MashMixtureWeightsTest {
    private static final Runnable CHECK=()->{};
    private static final MashMixtureWeights.Options OPTIONS=new MashMixtureWeights.Options(100000,1e-10,true);
    private static final MashMixtureWeights.Limits LIMITS=MashMixtureWeights.Limits.defaults();
    private static double[][] disjoint(){return new double[][]{{0,Double.NEGATIVE_INFINITY},{0,Double.NEGATIVE_INFINITY},{Double.NEGATIVE_INFINITY,0}};}
    @Test void disjointSupportHasExactMaximumLikelihoodProportions(){
        var fit=MashMixtureWeights.fit(disjoint(),new double[]{1,1},null,OPTIONS,LIMITS,CHECK).requireConverged();
        assertArrayEquals(new double[]{2./3,1./3},fit.proportions(),1e-14);assertEquals(2*Math.log(2./3)+Math.log(1./3),fit.logLikelihood(),1e-14);assertTrue(fit.relativeDualGap()<=1e-10);
    }
    @Test void nullBiasedPriorHasAnalyticPseudoCountUpdate(){
        var fit=MashMixtureWeights.fit(disjoint(),new double[]{10,1},null,OPTIONS,LIMITS,CHECK).requireConverged();
        assertArrayEquals(new double[]{11./12,1./12},fit.proportions(),1e-14);assertEquals(11*Math.log(11./12)+Math.log(1./12),fit.relativeObjective(),1e-14);
    }
    @Test void coincidentLikelihoodsLeaveUniformPriorWeightsUnidentified(){
        var fit=MashMixtureWeights.fit(new double[][]{{2,2},{-3,-3}},new double[]{1,1},new double[]{.1,.9},OPTIONS,LIMITS,CHECK).requireConverged();
        assertArrayEquals(new double[]{.1,.9},fit.proportions(),1e-14);assertEquals(0,fit.iterations());assertEquals(-1,fit.logLikelihood(),1e-14);
    }
    @Test void finiteComponentBoundaryConvergesWithDualCertificate(){
        var fit=MashMixtureWeights.fit(new double[][]{{0,-1},{0,-2}},new double[]{1,1},null,OPTIONS,LIMITS,CHECK).requireConverged();
        assertEquals(1,fit.proportions()[0],2e-10);assertTrue(fit.relativeDualGap()<=1e-10);assertEquals(0,fit.logLikelihood(),2e-10);
    }
    @Test void hugeCommonRowOffsetsCannotMaskWeightConvergence(){
        var first=MashMixtureWeights.fit(new double[][]{{0,-2},{-3,0}},new double[]{2,3},null,OPTIONS,LIMITS,CHECK).requireConverged();
        var shifted=MashMixtureWeights.fit(new double[][]{{1e12,1e12-2},{-1e12-3,-1e12}},new double[]{2,3},null,OPTIONS,LIMITS,CHECK).requireConverged();
        assertArrayEquals(first.proportions(),shifted.proportions(),1e-14);assertEquals(first.relativeObjective(),shifted.relativeObjective(),1e-14);assertEquals(first.relativeDualGap(),shifted.relativeDualGap(),1e-14);
    }
    @Test void overlappingSupportAgreesWithIndependentOneDimensionalSearch(){
        double[][] ll={{Math.log(.8),Math.log(.2)},{Math.log(.6),Math.log(.4)},{Math.log(.1),Math.log(.9)}};
        var fit=MashMixtureWeights.fit(ll,new double[]{1,1},null,OPTIONS,LIMITS,CHECK).requireConverged();
        // The derivative sums (a-b)/(p*a+(1-p)*b); bisection is independent of EM.
        double low=0,high=1;for(int iteration=0;iteration<100;iteration++){double p=(low+high)/2,derivative=0;for(double[] row:ll){double a=Math.exp(row[0]),b=Math.exp(row[1]);derivative+=(a-b)/(p*a+(1-p)*b);}if(derivative>0)low=p;else high=p;}
        assertEquals((low+high)/2,fit.proportions()[0],1e-8);
    }
    @Test void iterationLimitRemainsNonconvergedAndCannotBeAdmitted(){
        var fit=MashMixtureWeights.fit(new double[][]{{0,-.01},{0,-.02}},new double[]{1,1},null,new MashMixtureWeights.Options(1,1e-12,true),LIMITS,CHECK);
        assertFalse(fit.converged());assertEquals(1,fit.iterations());assertEquals(2,fit.history().size());assertThrows(IllegalStateException.class,fit::requireConverged);assertTrue(fit.numericalNotes().stream().anyMatch(s->s.startsWith("NONCONVERGED:")));
    }
    @Test void completeHistoryIsMonotoneAndInputsAndResultsAreOwned(){
        double[][] ll={{0,-1},{-2,0}};double[] prior={2,3},initial={.8,.2};var fit=MashMixtureWeights.fit(ll,prior,initial,OPTIONS,LIMITS,CHECK).requireConverged();
        ll[0][0]=99;prior[0]=99;initial[0]=99;double[] value=fit.proportions();value[0]=99;assertTrue(fit.proportions()[0]<1);
        double previous=Double.NEGATIVE_INFINITY;for(var point:fit.history()){assertTrue(point.relativeObjective()>=previous-1e-12);previous=point.relativeObjective();}
    }
    @Test void unsupportedPriorsAndSilentExclusionAreRejected(){
        for(double[] prior:new double[][]{{.5,1},{Double.NaN,1},{Double.POSITIVE_INFINITY,1}})assertThrows(IllegalArgumentException.class,()->MashMixtureWeights.fit(disjoint(),prior,null,OPTIONS,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MashMixtureWeights.fit(disjoint(),new double[]{1,1},new double[]{0,1},OPTIONS,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MashMixtureWeights.fit(new double[][]{{Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY}},new double[]{1,1},null,OPTIONS,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MashMixtureWeights.fit(new double[][]{{Double.NaN,0}},new double[]{1,1},null,OPTIONS,LIMITS,CHECK));
    }
    @Test void noPrefixOnAllocationWorkOrCancellationFailure(){
        assertThrows(IllegalArgumentException.class,()->MashMixtureWeights.fit(disjoint(),new double[]{1,1},null,OPTIONS,new MashMixtureWeights.Limits(2,2,4,1000,65536),CHECK));
        assertThrows(IllegalArgumentException.class,()->MashMixtureWeights.fit(disjoint(),new double[]{1,1},null,OPTIONS,new MashMixtureWeights.Limits(3,2,6,1,1_000_000),CHECK));
        assertThrows(CancellationException.class,()->MashMixtureWeights.fit(disjoint(),new double[]{1,1},null,OPTIONS,LIMITS,()->{throw new CancellationException("stop");}));
    }
}
