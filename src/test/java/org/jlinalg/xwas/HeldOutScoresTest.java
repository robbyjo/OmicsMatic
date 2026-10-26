/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
class HeldOutScoresTest {
    @Test void heldOutOutcomeAndFeatureChangesCannotAlterFrozenTrainingOrImputation() {
        double[][] x={{1},{2},{Double.NaN},{4},{100},{200},{300},{400}},cov=new double[8][1];double[] y={3,5,6,9,2,3,4,5};for(double[] row:cov)row[0]=1;
        var a=HeldOutScores.train(y,x,cov,new int[]{0,1,2,3},new int[0],new double[]{1},0,true,true);
        y[4]=1e12;x[4][0]=-1e12;var b=HeldOutScores.train(y,x,cov,new int[]{0,1,2,3},new int[0],new double[]{1},0,true,true);
        assertArrayEquals(a.frozen().model().weights(),b.frozen().model().weights(),0);assertArrayEquals(a.frozen().baselineWeights(),b.frozen().baselineWeights(),0);assertEquals(7.0/3,a.frozen().featureMeans()[0],1e-14);
        assertThrows(IllegalArgumentException.class,()->HeldOutScores.train(y,x,cov,new int[]{0,1,2,3},new int[0],new double[]{1},0,true,false));
    }
    @Test void independentTuningChangesSelectionButEvaluationNeverDoes() {
        double[][] x=new double[12][1],cov=new double[12][1];double[] y=new double[12];for(int i=0;i<12;i++){x[i][0]=i%4;cov[i][0]=1;y[i]=2*x[i][0]+1;}
        var a=HeldOutScores.train(y,x,cov,new int[]{0,1,2,3},new int[]{4,5,6,7},new double[]{10,.01},0,true,false);assertEquals(.01,a.selectedLambda());assertEquals(8,a.frozen().developmentRows());
        assertThrows(IllegalArgumentException.class,()->a.frozen().score(new double[][]{{Double.NaN}}));
        for(int i=8;i<12;i++)y[i]=1e10;var b=HeldOutScores.train(y,x,cov,new int[]{0,1,2,3},new int[]{4,5,6,7},new double[]{10,.01},0,true,false);assertEquals(a.tuningMse(),b.tuningMse());assertArrayEquals(a.frozen().model().weights(),b.frozen().model().weights(),0);
        assertThrows(IllegalArgumentException.class,()->HeldOutScores.train(y,x,cov,new int[]{0,1,2,3},new int[]{3,4,5,6},new double[]{1},0,true,false));
    }
    @Test void allGroupConstraintsAndUniqueIdentitiesAreCheckedBeforeFitting() {
        String[] ids={"a","b","c","d"},roles={"training","training","evaluation","evaluation"};HeldOutScores.partitions(ids,roles,Map.of("family",new String[]{"f1","f1","f2","f2"}));
        assertThrows(IllegalArgumentException.class,()->HeldOutScores.partitions(ids,roles,Map.of("family",new String[]{"f1","f2","f1","f3"})));
        assertThrows(IllegalArgumentException.class,()->HeldOutScores.partitions(new String[]{"a","b","a","d"},roles,Map.of()));
    }
    @Test void quantitativeMetricsUseFrozenBaselineAndRetainNegativeIncrementalR2() {
        double[] y={1,2,3,4},good={1,2,3,4},zero={0,0,0,0};var perfect=HeldOutScores.metrics(y,good,good,zero,false,false);assertEquals(0,perfect.get("rmse"));assertEquals(1,perfect.get("incremental_predictive_r2"));assertEquals(1,perfect.get("calibration_slope"),1e-14);
        var bad=HeldOutScores.metrics(y,new double[]{100,100,100,100},new double[]{100,100,100,100},zero,false,false);assertTrue(bad.get("incremental_predictive_r2")<0);
    }
    @Test void rawBinaryScoresDoNotAcquireProbabilityMetricsAndAucHandlesTies() {
        double[] y={0,0,1,1},scores={.1,.5,.5,.9},p={.2,.3,.7,.8},b={.5,.5,.5,.5};var raw=HeldOutScores.metrics(y,scores,scores,b,true,false);assertEquals(.875,raw.get("auc"));assertFalse(raw.containsKey("brier"));
        var probability=HeldOutScores.metrics(y,scores,p,b,true,true);assertEquals(1,probability.get("auc"));assertEquals(.065,probability.get("brier"),1e-14);assertEquals(.25,probability.get("baseline_brier"));assertThrows(IllegalArgumentException.class,()->HeldOutScores.metrics(y,scores,new double[]{2,3,4,5},b,true,true));
    }
    @Test void clusteredBootstrapIsDeterministicAndNeverPretendsRowsAreIndependent() {
        double[] y={1,1,2,2,3,3,4,4},p={1.1,1.1,1.9,1.9,2.9,2.9,4.1,4.1},b=new double[8];String[] groups={"a","a","b","b","c","c","d","d"};var a=HeldOutScores.intervals(y,p,p,b,groups,false,false,40,7);var again=HeldOutScores.intervals(y,p,p,b,groups,false,false,40,7);assertArrayEquals(a.get("rmse"),again.get("rmse"),0);assertEquals(40,a.get("rmse")[2]);assertEquals(40,a.get("rmse")[3]);assertThrows(IllegalArgumentException.class,()->HeldOutScores.intervals(y,p,p,b,new String[]{"a","a","a","a","a","a","a","a"},false,false,40,7));
    }
    @Test void perfectAndQuasiSeparatedClassificationHasNoFiniteCalibrationSlope() {
        double[] y={0,0,1,1},baseline={.5,.5,.5,.5};
        for(double[] p:new double[][]{{.1,.2,.8,.9},{.1,.5,.5,.9},{.9,.8,.2,.1}}){var metrics=HeldOutScores.metrics(y,p,p,baseline,true,true);assertFalse(metrics.containsKey("calibration_slope"));assertTrue(metrics.containsKey("brier"));assertTrue(metrics.containsKey("auc"));}
        var finite=HeldOutScores.metrics(y,new double[]{.1,.9,.2,.8},new double[]{.1,.9,.2,.8},baseline,true,true);assertEquals(0,finite.get("calibration_slope"),1e-8);
    }
}
