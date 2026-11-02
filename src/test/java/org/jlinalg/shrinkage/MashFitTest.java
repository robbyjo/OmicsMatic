/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class MashFitTest {
    private static final Runnable CHECK=()->{};
    private static final MashFit.Limits LIMITS=MashFit.Limits.defaults();
    private static final MashFit.Listener NONE=new MashFit.Listener(){};
    private static List<double[][]> matrices(double[][]... values){return java.util.Arrays.asList(values);}
    private static MashFit.Options options(MashFit.ResidualMode mode,int iterations,double tolerance){return new MashFit.Options(MashGaussian.Options.defaults(),new MashMixtureWeights.Options(100000,1e-10,false),mode,iterations,tolerance);}
    private static MashFit.Fit nullFit(MashFit.ResidualMode mode,int iterations,double tolerance,double[][] residual){
        return MashFit.fit(new double[][]{{1,0},{0,2},{-1,0},{0,-2}},new double[][]{{1,1},{1,1},{1,1},{1,1}},matrices(new double[][]{{0,0},{0,0}}),new double[]{1},residual,options(mode,iterations,tolerance),LIMITS,NONE,CHECK);
    }
    @Test void oneFeatureFitsWithoutPaddingOrAnOriginalWrapper(){
        var fit=MashFit.fit(new double[][]{{10,10}},new double[][]{{1,1}},matrices(new double[][]{{0,0},{0,0}},new double[][]{{100,100},{100,100}}),new double[]{10,1},new double[][]{{1,0},{0,1}},options(MashFit.ResidualMode.FIXED,30,1),LIMITS,NONE,CHECK).requireUsable();
        assertEquals(MashFit.Status.FIXED_RESIDUAL,fit.status());assertArrayEquals(new double[]{.9,.1},fit.proportions(),1e-12);
        var row=fit.model().evaluate(new double[]{10,10},new double[]{1,1},CHECK);assertArrayEquals(new double[]{2000./201,2000./201},row.mean(),1e-12);assertEquals(Math.sqrt(100./201),row.standardDeviation()[0],1e-12);
    }
    @Test void covarianceEmHasAnIndependentAnalyticNullModelSolution(){
        var fit=nullFit(MashFit.ResidualMode.EM_COVARIANCE,10,1e-10,new double[][]{{1,0},{0,1}}).requireUsable();
        assertEquals(.5,fit.residual()[0][0],1e-13);assertEquals(2,fit.residual()[1][1],1e-13);assertEquals(0,fit.residual()[0][1],1e-13);
        assertTrue(fit.status()==MashFit.Status.RESIDUAL_TOLERANCE||fit.status()==MashFit.Status.RESIDUAL_ROLLBACK);assertTrue(fit.proposals()>=1);
    }
    @Test void adHocLikelihoodDropReturnsThePrecedingModelAndKeepsTheProposal(){
        double a=Math.sqrt(8),b=Math.sqrt(2),c=Math.sqrt(6);var seen=new ArrayList<MashFit.Step>();
        var fit=MashFit.fit(new double[][]{{a,b},{-a,-b},{0,c},{0,-c}},new double[][]{{1,1},{1,1},{1,1},{1,1}},matrices(new double[][]{{0,0},{0,0}}),new double[]{1},new double[][]{{1,.3},{.3,1}},options(MashFit.ResidualMode.EM_CORRELATION,10,1e-12),LIMITS,
            new MashFit.Listener(){@Override public void step(MashFit.Step step,MashMixtureWeights.Fit weights){seen.add(step);}},CHECK).requireUsable();
        assertEquals(MashFit.Status.RESIDUAL_ROLLBACK,fit.status());assertEquals(.3,fit.residual()[0][1],1e-14);assertEquals(0,fit.acceptedProposals());assertEquals(1,fit.proposals());
        assertEquals(2,seen.size());assertFalse(seen.get(1).accepted());assertEquals(.5,seen.get(1).residual()[0][1],1e-13);assertTrue(seen.get(1).deltaLogLikelihood()<0);assertTrue(fit.notes().stream().anyMatch(s->s.startsWith("RESIDUAL_ROLLBACK:")));
    }
    @Test void residualIterationExhaustionIsNotACompletedFit(){
        var fit=nullFit(MashFit.ResidualMode.EM_COVARIANCE,1,0,new double[][]{{1,0},{0,1}});assertEquals(MashFit.Status.RESIDUAL_LIMIT,fit.status());assertFalse(fit.usable());assertThrows(IllegalStateException.class,fit::requireUsable);assertEquals(1,fit.acceptedProposals());assertEquals(2,fit.steps().size());
    }
    @Test void aVerifiedToleranceStopOnTheLastAllowedIterationIsRecognized(){
        var fit=nullFit(MashFit.ResidualMode.EM_COVARIANCE,1,1e6,new double[][]{{1,0},{0,1}}).requireUsable();assertEquals(MashFit.Status.RESIDUAL_TOLERANCE,fit.status());assertEquals(1,fit.proposals());assertEquals(.5,fit.residual()[0][0],1e-13);
    }
    @Test void nonconvergedWeightsRemainExplicitAndInspectable(){
        var options=new MashFit.Options(MashGaussian.Options.defaults(),new MashMixtureWeights.Options(1,1e-12,true),MashFit.ResidualMode.FIXED,30,1);
        var fit=MashFit.fit(new double[][]{{1}},new double[][]{{1}},matrices(new double[][]{{.1}},new double[][]{{.11}}),new double[]{1,1},new double[][]{{1}},options,LIMITS,NONE,CHECK);
        assertEquals(MashFit.Status.WEIGHTS_NONCONVERGED,fit.status());assertFalse(fit.weightFit().converged());assertThrows(IllegalStateException.class,fit::requireUsable);assertEquals(1,fit.steps().size());assertFalse(fit.steps().get(0).accepted());
    }
    @Test void nullSelectionUsesTheStrictAllConditionThresholdAndSampleDenominator(){
        double[][] bhat={{1,.5},{-1,-.5},{0,1},{2,0}},se={{1,1},{1,1},{1,1},{1,1}};
        var raw=MashFit.estimateNull(bhat,se,2,false,LIMITS,CHECK);assertEquals(List.of(0,1,2),raw.selectedRows());assertEquals(1,raw.covariance()[0][0],1e-14);assertEquals(.5,raw.covariance()[0][1],1e-14);assertEquals(7./12,raw.covariance()[1][1],1e-14);
        var correlation=MashFit.estimateNull(bhat,se,2,true,LIMITS,CHECK);assertEquals(1,correlation.covariance()[1][1]);assertEquals(.5/Math.sqrt(7./12),correlation.covariance()[0][1],1e-14);
    }
    @Test void undefinedNullCorrelationNeverGetsIdentityJitter(){
        assertThrows(IllegalArgumentException.class,()->MashFit.estimateNull(new double[][]{{.1,.2}},new double[][]{{1,1}},2,true,LIMITS,CHECK));
        assertThrows(ArithmeticException.class,()->MashFit.estimateNull(new double[][]{{0,0},{0,0}},new double[][]{{1,1},{1,1}},2,true,LIMITS,CHECK));
    }
    @Test void normalizedGridExpansionUsesSourceGridMajorOrderAndSquaredScales(){
        var basis=new MashGaussian.Basis(List.of("D","H"),matrices(new double[][]{{4,0},{0,1}},new double[][]{{9,9},{9,9}}));
        var expanded=MashFit.expand(basis,new double[]{.5,2},true,true,LIMITS,CHECK);assertEquals(List.of("null","D.1","H.1","D.2","H.2"),expanded.names());
        var u=expanded.covariances();assertEquals(.25,u.get(1)[0][0]);assertEquals(.0625,u.get(1)[1][1]);assertEquals(4,u.get(3)[0][0]);assertEquals(1,u.get(3)[1][1]);assertEquals(4,u.get(4)[0][1]);
    }
    @Test void unnormalizedExpansionPreservesAbsolutePriorUnitsAndDuplicateLabels(){
        var basis=new MashGaussian.Basis(List.of("same","same"),matrices(new double[][]{{4}},new double[][]{{9}}));var expanded=MashFit.expand(basis,new double[]{2},false,false,LIMITS,CHECK);
        assertEquals(List.of("same.1","same.1"),expanded.names());assertEquals(16,expanded.covariances().get(0)[0][0]);assertEquals(36,expanded.covariances().get(1)[0][0]);
    }
    @Test void unrepresentableOrUnorderedGridsAreNotGuessed(){
        var basis=new MashGaussian.Basis(List.of("x"),matrices(new double[][]{{1}}));for(double[] grid:new double[][]{{0},{1,1},{2,1},{Double.NaN},{1e200},{1e-200}})assertThrows(IllegalArgumentException.class,()->MashFit.expand(basis,grid,true,true,LIMITS,CHECK));
    }
    @Test void residualMeanAvoidsSubtractionOfIndistinguishableLargeEffects(){
        var model=MashGaussian.prepare(matrices(new double[][]{{1e200}}),new double[]{1},new double[][]{{1}},MashGaussian.Options.defaults(),MashGaussian.Limits.defaults(),CHECK);
        var value=model.evaluateWithResidual(new double[]{1e100},new double[]{1},CHECK);assertEquals(1e-100,value.standardizedResidualMean()[0],1e-113);assertEquals(1,value.posterior().covariance()[0][0],1e-12);
        assertEquals(1,MashFit.expectedNoise(new double[][]{{1e100}},new double[][]{{1}},model,LIMITS,CHECK)[0][0],1e-12);
    }
    @Test void stableResidualMeanMatchesOrdinaryConjugacyWithCorrelatedNoise(){
        var model=MashGaussian.prepare(matrices(new double[][]{{1,0},{0,1}}),new double[]{1},new double[][]{{1,.5},{.5,1}},MashGaussian.Options.defaults(),MashGaussian.Limits.defaults(),CHECK);
        var result=model.evaluateWithResidual(new double[]{1,2},new double[]{1,1},CHECK);assertArrayEquals(new double[]{11./15,16./15},result.standardizedResidualMean(),1e-14);
        var noise=MashFit.expectedNoise(new double[][]{{1,2}},new double[][]{{1,1}},model,LIMITS,CHECK);assertEquals(7./15+121./225,noise[0][0],1e-14);assertEquals(2./15+176./225,noise[0][1],1e-14);
    }
    @Test void returnedModelsStatesAndNoiseMeansOwnTheirArrays(){
        var fit=nullFit(MashFit.ResidualMode.FIXED,30,1,new double[][]{{1,0},{0,1}});fit.residual()[0][0]=99;fit.proportions()[0]=0;fit.steps().get(0).residual()[0][0]=99;
        assertEquals(1,fit.residual()[0][0]);assertEquals(1,fit.proportions()[0]);assertEquals(1,fit.steps().get(0).residual()[0][0]);
        var evaluation=fit.model().evaluateWithResidual(new double[]{1,2},new double[]{1,1},CHECK);evaluation.standardizedResidualMean()[0]=99;assertEquals(1,evaluation.standardizedResidualMean()[0]);
    }
    @Test void completeWorkBudgetsAndCancellationDoNotReturnPrefixes(){
        var tiny=new MashFit.Limits(10,LIMITS.gaussian(),LIMITS.weights(),100,4_194_304,1,1000);
        var failure=assertThrows(IllegalArgumentException.class,()->MashFit.fit(new double[][]{{1}},new double[][]{{1}},matrices(new double[][]{{1}}),new double[]{1},new double[][]{{1}},MashFit.Options.defaults(),tiny,NONE,CHECK));assertTrue(failure.getMessage().contains("Gaussian work"));
        assertThrows(CancellationException.class,()->MashFit.fit(new double[][]{{1}},new double[][]{{1}},matrices(new double[][]{{1}}),new double[]{1},new double[][]{{1}},MashFit.Options.defaults(),LIMITS,NONE,()->{throw new CancellationException("stop");}));
    }
    @Test void listenerFailureCannotBeConvertedIntoSuccessfulMissingEvidence(){
        assertThrows(IllegalStateException.class,()->MashFit.fit(new double[][]{{1}},new double[][]{{1}},matrices(new double[][]{{1}}),new double[]{1},new double[][]{{1}},MashFit.Options.defaults(),LIMITS,new MashFit.Listener(){@Override public void weights(double[][] residual,MashMixtureWeights.Fit fit){throw new IllegalStateException("evidence unavailable");}},CHECK));
    }
    @Test void unresolvedPriorScalesCannotBecomeFalseZeroEffectMass(){
        assertThrows(ArithmeticException.class,()->MashGaussian.prepare(matrices(new double[][]{{1e-300,0},{0,1e300}}),new double[]{1},new double[][]{{1,0},{0,1}},MashGaussian.Options.defaults(),MashGaussian.Limits.defaults(),CHECK));
    }
}
