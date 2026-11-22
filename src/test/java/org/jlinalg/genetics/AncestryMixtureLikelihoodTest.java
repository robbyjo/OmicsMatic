package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AncestryMixtureLikelihoodTest {
    @Test void singleComponentDiploidEstimateUsesOnlyObservedAlleles() {
        var result=AncestryMixtureLikelihood.estimateOneComponent(new double[]{0,1,2,Double.NaN},2);
        assertEquals(3,result.observedSamples());assertEquals(3,result.countedAlleles());assertEquals(6,result.observedAlleles());assertEquals(.5,result.frequency());assertEquals(-6*Math.log(2),result.logLikelihood(),1e-14);
    }
    @Test void genuineHaploidAndDoubledDiploidModelsHaveDistinctLikelihoods() {
        double[] calls={0,0,2};var h=AncestryMixtureLikelihood.estimateOneComponent(calls,1);var d=AncestryMixtureLikelihood.estimateOneComponent(calls,2);
        assertEquals(1.0/3,h.frequency());assertEquals(h.frequency(),d.frequency());assertEquals(2*h.logLikelihood(),d.logLikelihood(),1e-14);assertEquals(3,h.observedAlleles());assertEquals(6,d.observedAlleles());
    }
    @Test void monomorphicBoundaryLikelihoodsRemainFinite() {
        assertEquals(0,AncestryMixtureLikelihood.estimateOneComponent(new double[]{0,0},2).logLikelihood());assertEquals(0,AncestryMixtureLikelihood.estimateOneComponent(new double[]{2,2},1).logLikelihood());
    }
    @Test void allMissingFractionalAndHaploidHeterozygousCallsFail() {
        assertThrows(IllegalArgumentException.class,()->AncestryMixtureLikelihood.estimateOneComponent(new double[]{Double.NaN},2));
        for(double x:new double[]{.5,Double.POSITIVE_INFINITY,-1,3})assertThrows(IllegalArgumentException.class,()->AncestryMixtureLikelihood.estimateOneComponent(new double[]{x},2));
        assertThrows(IllegalArgumentException.class,()->AncestryMixtureLikelihood.estimateOneComponent(new double[]{1},1));assertThrows(IllegalArgumentException.class,()->AncestryMixtureLikelihood.estimateOneComponent(new double[]{0},4));
    }
    @Test void streamingOneComponentMatchesClosedFormEstimate() {
        double[] calls={0,1,2,0};var fit=AncestryMixtureLikelihood.estimateOneComponent(calls,2);var audit=new AncestryMixtureLikelihood.Evaluator(new double[]{1,1,1,1},4,1,0,false);audit.addMarker(calls,new double[]{fit.frequency()},2,0);var result=audit.summary(0,.1);assertEquals(fit.logLikelihood(),result.logLikelihood(),1e-14);assertTrue(result.containsLikelihood(fit.logLikelihood(),0));
    }
    @Test void componentPermutationsPreserveTheModel() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{.2,.8,.7,.3},2,2,0,false);var b=new AncestryMixtureLikelihood.Evaluator(new double[]{.8,.2,.3,.7},2,2,0,false);a.addMarker(new double[]{0,2},new double[]{.1,.9},2,0);b.addMarker(new double[]{0,2},new double[]{.9,.1},2,0);assertEquals(a.summary(0,.1).logLikelihood(),b.summary(0,.1).logLikelihood(),1e-14);
    }
    @Test void alleleComplementationRequiresComplementingBothCallsAndP() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{.2,.8,.7,.3},2,2,0,false);var b=new AncestryMixtureLikelihood.Evaluator(new double[]{.2,.8,.7,.3},2,2,0,false);a.addMarker(new double[]{0,2},new double[]{.1,.9},2,0);b.addMarker(new double[]{2,0},new double[]{.9,.1},2,0);assertEquals(a.summary(0,.1).logLikelihood(),b.summary(0,.1).logLikelihood(),1e-14);
    }
    @Test void missingCallsDoNotContributeObservedCountsOrLikelihood() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{1,1,1},3,1,0,false);a.addMarker(new double[]{Double.NaN,1,Double.NaN},new double[]{.25},2,0);var result=a.summary(0,.1);assertEquals(1,result.markers());assertEquals(1,result.observedCells());assertEquals(2,result.missingCells());assertEquals(2,result.observedAlleles());assertEquals(1,result.countedAlleles());assertEquals(Math.log(.25)+Math.log(.75),result.logLikelihood(),1e-14);
    }
    @Test void impossibleMixtureRowsAndInvalidParametersAreRejected() {
        for(double[] q:new double[][]{{.2,.2},{-.1,1.1},{Double.NaN,1}})assertThrows(IllegalArgumentException.class,()->new AncestryMixtureLikelihood.Evaluator(q,1,2,0,false));
        assertThrows(IllegalArgumentException.class,()->new AncestryMixtureLikelihood.Evaluator(new double[]{1},2,1,0,false));assertThrows(IllegalArgumentException.class,()->new AncestryMixtureLikelihood.Evaluator(new double[]{1},1,1,.1,false));
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{1},1,1,0,false);assertThrows(IllegalArgumentException.class,()->a.addMarker(new double[]{1},new double[]{Double.NaN},2,0));assertThrows(IllegalStateException.class,()->a.summary(0,.1));
    }
    @Test void printedZeroDoesNotPretendThatLikelihoodPrecisionIsFinite() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{1},1,1,.0000005,false);a.addMarker(new double[]{1},new double[]{0},2,.0000005);var result=a.summary(0,.1);assertEquals(Double.NEGATIVE_INFINITY,result.logLikelihood());assertEquals(Double.NEGATIVE_INFINITY,result.likelihoodLower());assertEquals(1,result.precisionUnboundedCells());assertTrue(Double.isFinite(result.likelihoodUpper()));assertTrue(result.containsLikelihood(Math.log(1e-8)+Math.log1p(-1e-8),0));
    }
    @Test void penaltyAndUnpenalizedLikelihoodRemainSeparate() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{.2,.8},1,2,0,false);a.addMarker(new double[]{1},new double[]{.3,.6},2,0);var result=a.summary(2,.1);double penalty=2*(Math.log1p(.2/.1)+Math.log1p(.8/.1))/Math.log1p(1/.1);assertEquals(result.logLikelihood()-penalty,result.objective(),1e-14);assertTrue(result.objectiveLower()<=result.objective());assertTrue(result.objectiveUpper()>=result.objective());
    }
    @Test void tinyPenaltyScaleDoesNotOverflowRatios() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{.2,.8},1,2,0,false);a.addMarker(new double[]{1},new double[]{.3,.6},2,0);assertTrue(Double.isFinite(a.summary(2,Double.MIN_VALUE).objective()));assertThrows(IllegalArgumentException.class,()->a.summary(1,0));assertThrows(ArithmeticException.class,()->a.summary(Double.MAX_VALUE,.1));
    }
    @Test void binomialCoefficientIsAnExplicitOption() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{1},1,1,0,false);var b=new AncestryMixtureLikelihood.Evaluator(new double[]{1},1,1,0,true);a.addMarker(new double[]{1},new double[]{.25},2,0);b.addMarker(new double[]{1},new double[]{.25},2,0);assertEquals(Math.log(2),b.summary(0,.1).logLikelihood()-a.summary(0,.1).logLikelihood(),1e-14);
    }
    @Test void invalidMarkerCannotPartiallyChangeTheAccumulator() {
        var a=new AncestryMixtureLikelihood.Evaluator(new double[]{1,1},2,1,0,false);assertThrows(IllegalArgumentException.class,()->a.addMarker(new double[]{1,3},new double[]{.3},2,0));a.addMarker(new double[]{0,2},new double[]{.3},2,0);assertEquals(1,a.summary(0,.1).markers());assertEquals(2,a.summary(0,.1).observedCells());
    }
    @Test void qIsDefensivelyCopiedAndMixedPloidyIsExplicit() {
        double[] q={1,1};var a=new AncestryMixtureLikelihood.Evaluator(q,2,1,0,false);q[0]=0;a.addMarker(new double[]{2,1},new double[]{.25},new int[]{1,2},0);var result=a.summary(0,.1);assertEquals(3,result.observedAlleles());assertEquals(2,result.countedAlleles());assertEquals(2*Math.log(.25)+Math.log(.75),result.logLikelihood(),1e-14);
    }
    @Test void printedParameterIntervalsContainIndependentUnroundedModelValues() {
        Random rng=new Random(472311);
        for(int trial=0;trial<200;trial++) {
            double a=.05+.9*rng.nextDouble(),b=.05+.9*rng.nextDouble(),c=.05+.9*rng.nextDouble();double[] q={a,1-a},p={b,c};int call=rng.nextInt(3);double actualP=a*b+(1-a)*c;double expected=(call==0?0:call*Math.log(actualP))+(call==2?0:(2-call)*Math.log1p(-actualP));
            double[] roundedQ={Math.rint(q[0]*1e6)/1e6,Math.rint(q[1]*1e6)/1e6},roundedP={Math.rint(p[0]*1e6)/1e6,Math.rint(p[1]*1e6)/1e6};var evaluator=new AncestryMixtureLikelihood.Evaluator(roundedQ,1,2,.0000005,false);evaluator.addMarker(new double[]{call},roundedP,2,.0000005);assertTrue(evaluator.summary(0,.1).containsLikelihood(expected,0));
        }
    }
}
