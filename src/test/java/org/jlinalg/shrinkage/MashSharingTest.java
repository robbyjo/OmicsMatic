/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MashSharingTest {
    private static final Runnable CHECK=()->{};
    private static final MashSharing.Limits LIMITS=MashSharing.Limits.defaults();
    @Test void unionDenominatorStrictBoundariesAndUpperTriangleMirroring(){
        var result=MashSharing.pairwise(new double[][]{{1,1},{1,2},{2,1},{-1,-1}},new double[][]{{.01,.8},{.8,.01},{.05,.05},{.02,.02}},2,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK);
        assertEquals(new MashSharing.Cell(3,2,0),result.cell(0,1));assertEquals(2./3,result.cell(0,1).fraction());assertEquals(result.cell(0,1),result.cell(1,0));
    }
    @Test void emptyAndUndefinedSelectedRatiosAreDifferentRetainedStates(){
        var result=MashSharing.pairwise(new double[][]{{0,0}},new double[][]{{0,.8}},2,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK);
        assertEquals(MashSharing.Status.UNDEFINED_SELECTED_RATIO,result.cell(0,1).status());assertEquals(1,result.cell(0,1).selectedRows());assertTrue(Double.isNaN(result.cell(0,1).fraction()));
        assertEquals(MashSharing.Status.EMPTY_SELECTED_POPULATION,result.cell(1,1).status());
    }
    @Test void factorZeroRetainsSourceDivisionInsteadOfOnlyComparingSigns(){
        var result=MashSharing.pairwise(new double[][]{{1,0},{Double.MAX_VALUE,Double.MIN_VALUE},{-1,-1}},new double[][]{{0,0},{0,0},{0,0}},2,0,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK);
        assertEquals(new MashSharing.Cell(3,1,0),result.cell(0,1));
    }
    @Test void absoluteMagnitudeIsExplicitAndFactorOneHasNoDefinedSharedValues(){
        double[][] means={{-1,1}},sig={{0,0}};
        assertEquals(0,MashSharing.pairwise(means,sig,2,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK).cell(0,1).fraction());
        assertEquals(1,MashSharing.pairwise(means,sig,2,.5,.05,MashSharing.Transform.ABSOLUTE,LIMITS,CHECK).cell(0,1).fraction());
        assertEquals(0,MashSharing.pairwise(means,sig,2,1,.05,MashSharing.Transform.ABSOLUTE,LIMITS,CHECK).cell(0,0).fraction());
    }
    @Test void significanceIsStableAndExplicitInclusiveCountsRetainBoundary(){
        double[][] p={{.01,.2},{.01,.1},{.05,.05},{.02,.4}};
        var strict=MashSharing.significance(p,2,new int[]{0,1},.05,MashSharing.Boundary.STRICT,LIMITS,CHECK);
        assertArrayEquals(new int[]{0,1,3},strict.selectedRows());assertArrayEquals(new int[]{1,1,0,1},strict.significantConditions());
        var inclusive=MashSharing.significance(p,2,new int[]{0,1},.05,MashSharing.Boundary.INCLUSIVE,LIMITS,CHECK);assertArrayEquals(new int[]{1,1,2,1},inclusive.significantConditions());
    }
    @Test void repeatedSelectedConditionsFollowSourceCountSemantics(){
        assertArrayEquals(new int[]{2},MashSharing.significance(new double[][]{{.01,.5}},2,new int[]{0,0,1},.05,MashSharing.Boundary.STRICT,LIMITS,CHECK).significantConditions());
    }
    @Test void explicitEmptyFeaturePopulationPreservesConditionDimension(){
        var result=MashSharing.pairwise(new double[0][],new double[0][],3,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK);assertEquals(3,result.cells().length);assertEquals(MashSharing.Status.EMPTY_SELECTED_POPULATION,result.cell(2,2).status());
        assertEquals(0,MashSharing.significance(new double[0][],3,new int[]{1},.05,MashSharing.Boundary.STRICT,LIMITS,CHECK).selectedRows().length);
    }
    @Test void invalidNonfiniteMissingOrRaggedInputIsNeverImputed(){
        assertThrows(IllegalArgumentException.class,()->MashSharing.pairwise(new double[][]{{Double.NaN}},new double[][]{{0}},1,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MashSharing.pairwise(new double[][]{{1,2}},new double[][]{{0}},2,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MashSharing.significance(new double[][]{{1.01}},1,new int[]{0},.05,MashSharing.Boundary.STRICT,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MashSharing.significance(new double[][]{{0}},1,new int[]{1},.05,MashSharing.Boundary.STRICT,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->new MashSharing.Cell(1,Integer.MAX_VALUE,Integer.MAX_VALUE));
    }
    @Test void completeCapacityAndCancellationFailWithoutAnOutputPrefix(){
        var tiny=new MashSharing.Limits(5,5,10,1,65536);
        assertThrows(IllegalArgumentException.class,()->MashSharing.pairwise(new double[][]{{1,1}},new double[][]{{0,0}},2,.5,.05,MashSharing.Transform.IDENTITY,tiny,CHECK));
        var calls=new AtomicInteger();assertThrows(CancellationException.class,()->MashSharing.significance(new double[][]{{0},{.1}},1,new int[]{0},.05,MashSharing.Boundary.STRICT,LIMITS,()->{if(calls.incrementAndGet()==3)throw new CancellationException("requested");}));
    }
    @Test void returnedArraysDoNotExposeMutableResultStorage(){
        var r=MashSharing.significance(new double[][]{{.01}},1,new int[]{0},.05,MashSharing.Boundary.STRICT,LIMITS,CHECK);r.selectedRows()[0]=99;r.minimumSignificance()[0]=1;r.significantConditions()[0]=99;assertEquals(0,r.selectedRows()[0]);assertEquals(.01,r.minimumSignificance()[0]);assertEquals(1,r.significantConditions()[0]);
        var p=MashSharing.pairwise(new double[][]{{1}},new double[][]{{0}},1,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK);p.cells()[0][0]=null;assertNotNull(p.cell(0,0));
    }
    @Test void signProductsKeepStrictScoreAndNominalGatesSeparate(){
        var r=MashSignScores.calculate(new double[][]{{.1,0},{0,0},{0,0}},new double[][]{{.001,.001},{.05,.001},{.001,.001}},2,List.of(new int[]{0,1}),.05,2,.9,LIMITS,CHECK);
        assertEquals(.9,r.score(0,0).product());assertFalse(r.score(0,0).exceedsThreshold());assertTrue(r.score(0,0).eligible());
        assertFalse(r.score(1,0).eligible());assertEquals(1,r.score(1,0).nominallySignificant());assertFalse(r.score(1,0).exceedsThreshold());assertTrue(r.score(2,0).exceedsThreshold());
    }
    @Test void underflowAndExactZeroAreDistinguishedWithRetainedLogScores(){
        double[][] p={new double[200],new double[200]},nominal={new double[200],new double[200]};Arrays.fill(p[0],.9999);Arrays.fill(p[1],1);
        int[] group=new int[200];for(int i=0;i<200;i++)group[i]=i;
        var r=MashSignScores.calculate(p,nominal,200,List.of(group),.05,2,0,LIMITS,CHECK);
        assertEquals(0,r.score(0,0).product());assertTrue(r.score(0,0).underflowToZero());assertTrue(Double.isFinite(r.score(0,0).logProduct()));assertFalse(r.score(0,0).exceedsThreshold());
        assertFalse(r.score(1,0).underflowToZero());assertEquals(Double.NEGATIVE_INFINITY,r.score(1,0).logProduct());
    }
    @Test void groupedScoresRejectDuplicateAndMissingInputsAndReturnDefensiveStorage(){
        double[][] p={{.01,.01}};
        assertThrows(IllegalArgumentException.class,()->MashSignScores.calculate(p,p,2,List.of(new int[]{0,0}),.05,2,.9,LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MashSignScores.calculate(p,new double[][]{{Double.NaN,.1}},2,List.of(new int[]{0,1}),.05,2,.9,LIMITS,CHECK));
        var r=MashSignScores.calculate(p,p,2,List.of(new int[]{0,1}),.05,2,.9,LIMITS,CHECK);r.scores()[0][0]=null;assertNotNull(r.score(0,0));
    }
    @Test void completeTwentyConditionPaperSizedPopulationFitsPortableHeap(){
        int n=26000,c=20;double[][] sig=new double[n][c],nominal=new double[n][c],means=new double[n][c];for(int i=0;i<n;i++){Arrays.fill(sig[i],.001);Arrays.fill(nominal[i],.001);Arrays.fill(means[i],1);}
        var scores=MashSignScores.calculate(sig,nominal,c,List.of(new int[]{0,1,2,3,4,5,6,7,8},new int[]{9,10,11,12,13,14,15},new int[]{16,17,18,19}),.05,2,.9,LIMITS,CHECK);
        assertEquals(n,scores.scores().length);assertTrue(scores.score(n-1,2).exceedsThreshold());assertEquals(4,scores.score(n-1,2).nominallySignificant());
        var shared=MashSharing.pairwise(means,sig,c,.5,.05,MashSharing.Transform.IDENTITY,LIMITS,CHECK);assertEquals(n,shared.cell(0,c-1).selectedRows());assertEquals(1,shared.cell(0,c-1).fraction());
    }
}
