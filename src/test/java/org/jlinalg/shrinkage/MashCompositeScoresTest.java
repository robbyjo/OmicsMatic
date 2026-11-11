/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MashCompositeScoresTest {
    private static final Runnable CHECK=()->{};
    private static final MashSharing.Limits LIMITS=MashSharing.Limits.defaults();
    private static MashCompositeScores.Pattern pattern(int[] include,int[] exclude,MashCompositeScores.Mode mode){return new MashCompositeScores.Pattern("pattern",include,exclude,mode);}
    private static MashCompositeScores.Result evaluate(double[][] input,int c,MashCompositeScores.Pattern pattern,MashCompositeScores.Scale scale,MashCompositeScores.Missing missing){return MashCompositeScores.calculate(input,c,List.of(pattern),scale,missing,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,LIMITS,CHECK);}
    private static MashCompositeScores.Score score(double[] input,int[] include,int[] exclude,MashCompositeScores.Mode mode){return evaluate(new double[][]{input},input.length,pattern(include,exclude,mode),MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT).score(0,0);}
    @Test void exactArrangementUsesBothPresentAndAbsentFactors(){var s=score(new double[]{.8,.1},new int[]{0},new int[]{1},MashCompositeScores.Mode.ALL);assertEquals(.72,s.sourceScore(),1e-15);assertEquals(.8,s.sourceInclusion());assertEquals(.9,s.sourceExclusion());assertEquals(Math.log(.72),s.stableLogScore(),1e-15);}
    @Test void atLeastOneUsesComplementBeforeExclusionProduct(){var s=score(new double[]{.2,.3,.4},new int[]{0,1},new int[]{2},MashCompositeScores.Mode.AT_LEAST_ONE);assertEquals(.264,s.sourceScore(),1e-15);assertEquals(Math.log(.264),s.stableLogScore(),1e-15);}
    @Test void cancellationToZeroDoesNotLoseItsStableLogDiagnostic(){var s=score(new double[]{1e-100},new int[]{0},new int[0],MashCompositeScores.Mode.AT_LEAST_ONE);assertEquals(0,s.sourceScore());assertEquals(MashCompositeScores.State.SOURCE_ROUNDING_TO_ZERO,s.state());assertEquals(Math.log(1e-100),s.stableLogScore(),1e-12);assertTrue(s.numericalLoss());assertEquals(1,s.roundedMarginalOccurrences());}
    @Test void lfsrConversionKeepsLiteralDoubleComplementAndOriginalScaleLog(){var s=evaluate(new double[][]{{1e-100}},1,pattern(new int[0],new int[]{0},MashCompositeScores.Mode.ALL),MashCompositeScores.Scale.LFSR,MashCompositeScores.Missing.REJECT).score(0,0);assertEquals(0,s.sourceScore());assertEquals(MashCompositeScores.State.SOURCE_ROUNDING_TO_ZERO,s.state());assertEquals(Math.log(1e-100),s.stableLogScore(),1e-12);assertEquals(1,s.roundedMarginalOccurrences());}
    @Test void productUnderflowIsDifferentFromAnExactlyZeroProxy(){var s=score(new double[]{1e-200,1e-200},new int[]{0,1},new int[0],MashCompositeScores.Mode.ALL);assertEquals(MashCompositeScores.State.UNDERFLOW_TO_ZERO,s.state());assertEquals(2*Math.log(1e-200),s.stableLogScore(),1e-12);assertEquals(0,s.sourceScore());}
    @Test void exactZerosAndOnesRetainTheirEventAlgebra(){assertEquals(MashCompositeScores.State.EXACT_ZERO,score(new double[]{0},new int[]{0},new int[0],MashCompositeScores.Mode.ALL).state());assertEquals(1,score(new double[]{1},new int[]{0},new int[0],MashCompositeScores.Mode.AT_LEAST_ONE).sourceScore());assertEquals(0,score(new double[]{1},new int[0],new int[]{0},MashCompositeScores.Mode.ALL).sourceScore());}
    @Test void emptySelectorsAndZeroDimensionalMatricesMatchSourceIdentities(){
        double[][] empty=new double[3][0];var patterns=List.of(new MashCompositeScores.Pattern("all",new int[0],new int[0],MashCompositeScores.Mode.ALL),new MashCompositeScores.Pattern("any",new int[0],new int[0],MashCompositeScores.Mode.AT_LEAST_ONE));
        var r=MashCompositeScores.calculate(empty,0,patterns,MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,LIMITS,CHECK);assertEquals(1,r.score(2,0).sourceScore());assertEquals(0,r.score(2,1).sourceScore());
        assertEquals(0,evaluate(new double[0][],4,pattern(new int[]{0},new int[]{1},MashCompositeScores.Mode.ALL),MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT).scores().length);
    }
    @Test void missingSourceRuleRequiresExplicitOptInAndRetainsCoverage(){
        var p=pattern(new int[0],new int[]{0},MashCompositeScores.Mode.ALL);double[][] x={{Double.NaN}};assertThrows(IllegalArgumentException.class,()->evaluate(x,1,p,MashCompositeScores.Scale.LFSR,MashCompositeScores.Missing.REJECT));
        var r=evaluate(x,1,p,MashCompositeScores.Scale.LFSR,MashCompositeScores.Missing.SOURCE_ZERO_PROBABILITY);assertEquals(1,r.missingMatrixCells());assertEquals(1,r.score(0,0).missingSelectedOccurrences());assertEquals(1,r.score(0,0).sourceScore());assertTrue(Double.isNaN(x[0][0]));
    }
    @Test void repeatedAndOverlappingSelectorsRetainSourceProxyRatherThanClaimingLogicalConsistency(){var s=score(new double[]{.5},new int[]{0,0},new int[]{0},MashCompositeScores.Mode.ALL);assertEquals(.125,s.sourceScore());assertEquals(1,s.repeatedSelectorOccurrences());assertEquals(1,s.overlappingConditions());}
    @Test void paperThresholdsAreExplicitAndPreserveStrictVersusInclusive(){var s=score(new double[]{.05},new int[]{0},new int[0],MashCompositeScores.Mode.ALL);assertTrue(s.passes(.05,MashSharing.Boundary.INCLUSIVE));assertFalse(s.passes(.05,MashSharing.Boundary.STRICT));assertFalse(score(new double[]{.5},new int[]{0},new int[0],MashCompositeScores.Mode.ALL).passes(.5,MashSharing.Boundary.STRICT));}
    @Test void invalidInputsAndUnknownIndicesAreNotImputedOrRepaired(){assertThrows(IllegalArgumentException.class,()->score(new double[]{Double.POSITIVE_INFINITY},new int[]{0},new int[0],MashCompositeScores.Mode.ALL));assertThrows(IllegalArgumentException.class,()->score(new double[]{-.1},new int[]{0},new int[0],MashCompositeScores.Mode.ALL));assertThrows(IllegalArgumentException.class,()->score(new double[]{.5},new int[]{1},new int[0],MashCompositeScores.Mode.ALL));assertThrows(IllegalArgumentException.class,()->evaluate(new double[][]{{.5}},2,pattern(new int[]{0},new int[0],MashCompositeScores.Mode.ALL),MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT));}
    @Test void fullFamilyCapacitiesAndCancellationNeverReturnPrefixes(){
        var p=List.of(pattern(new int[]{0},new int[0],MashCompositeScores.Mode.ALL));var tiny=new MashSharing.Limits(10,10,10,1,65536);assertThrows(IllegalArgumentException.class,()->MashCompositeScores.calculate(new double[][]{{.5}},1,p,MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,tiny,CHECK));
        var calls=new AtomicInteger();assertThrows(CancellationException.class,()->MashCompositeScores.calculate(new double[][]{{.5}},1,p,MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,LIMITS,()->{if(calls.incrementAndGet()==3)throw new CancellationException("requested");}));
    }
    @Test void selectorsAndResultStorageAreDefensive(){int[] include={0};var p=pattern(include,new int[0],MashCompositeScores.Mode.ALL);include[0]=99;p.include()[0]=99;var r=evaluate(new double[][]{{.7}},1,p,MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.REJECT);r.scores()[0][0]=null;assertEquals(.7,r.score(0,0).sourceScore());}
    @Test void completeTwentyConditionPopulationFitsPortableHeap(){
        int rows=26000,c=20;double[][] lfsr=new double[rows][c];for(var row:lfsr)Arrays.fill(row,.01);int[] all=new int[c],rest=new int[c-1],en=new int[9],other=new int[11];for(int i=0;i<c;i++){all[i]=i;if(i>0)rest[i-1]=i;if(i<9)en[i]=i;else other[i-9]=i;}
        var patterns=List.of(new MashCompositeScores.Pattern("specific",new int[]{0},rest,MashCompositeScores.Mode.ALL),new MashCompositeScores.Pattern("any-EN",en,other,MashCompositeScores.Mode.AT_LEAST_ONE),new MashCompositeScores.Pattern("all",all,new int[0],MashCompositeScores.Mode.ALL));
        var r=MashCompositeScores.calculate(lfsr,c,patterns,MashCompositeScores.Scale.LFSR,MashCompositeScores.Missing.REJECT,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,LIMITS,CHECK);assertEquals(rows,r.scores().length);assertTrue(r.score(rows-1,2).passes(.8,MashSharing.Boundary.STRICT));assertTrue(r.score(rows-1,0).sourceScore()>0);assertEquals(MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,r.assumption());
    }
}
