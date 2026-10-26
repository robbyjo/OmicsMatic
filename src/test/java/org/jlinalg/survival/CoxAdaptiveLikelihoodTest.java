/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class CoxAdaptiveLikelihoodTest {
    @Test void aFiniteLargeCohortUsesASufficientSubsetInsteadOfEnumeratingAllRiskPairs(){
        int n=30000;double[] time=new double[n];boolean[] event=new boolean[n];double[][] x=new double[n][2];for(int i=0;i<n;i++){time[i]=i+1;event[i]=i%3!=0;x[i][0]=Math.sin(i*.53);x[i][1]=Math.cos(i*.31);}
        var r=CoxAdaptiveLikelihood.analyze(CoxSurvivalData.rightCensored(time,event),x);assertTrue(r.finiteMaximum(),r.message());assertTrue(r.supportingRows().size()<n);assertTrue(r.proof().pairVisits()<100000);assertEquals(2,r.proof().numericalRank());
    }
    @Test void aLargeMonotoneModelIsVerifiedOverEveryOriginalRiskSet(){
        int n=10000;double[] time=new double[n];boolean[] event=new boolean[n];double[][] x=new double[n][1];for(int i=0;i<n;i++){time[i]=i+1;event[i]=i<n/2;x[i][0]=-time[i];}
        var r=CoxAdaptiveLikelihood.analyze(CoxSurvivalData.rightCensored(time,event),x);assertEquals(CoxLikelihoodExistence.Status.MONOTONE_LIKELIHOOD,r.status(),r.message());assertTrue(r.fullDirectionVerified());assertEquals(n/2,r.fullRiskQueries());assertTrue(r.proof().pairVisits()<100000);
    }
    @Test void aDirectionFromAnUnrepresentativeSubsetCannotInvalidateTheFullModel(){
        int n=200;double[] time=new double[n];boolean[] event=new boolean[n];double[][] x=new double[n][1];for(int i=0;i<n;i++){time[i]=i+1;event[i]=i<n/2;x[i][0]=-time[i];}x[50][0]=-60;
        var y=CoxSurvivalData.rightCensored(time,event);var check=CoxAdaptiveLikelihood.verifyDirection(y,x,List.of(new CoxLikelihoodExistence.ExactCoefficient(0,"1","1")),CoxLikelihoodExistence.Options.defaults());assertFalse(check.verified());
        var r=CoxAdaptiveLikelihood.analyze(y,x);assertTrue(r.finiteMaximum(),r.message());assertTrue(r.attempts()>1);
    }
    @Test void entryBoundariesTiedFailuresAndStrataAreCheckedExactly(){
        var y=new CoxSurvivalData(new double[]{0,1,0,0},new double[]{1,3,2,3},new boolean[]{true,false,true,false},new int[]{0,0,1,1});double[][] x={{0},{100},{1},{0}};
        var check=CoxAdaptiveLikelihood.verifyDirection(y,x,List.of(new CoxLikelihoodExistence.ExactCoefficient(0,"1","1")),CoxLikelihoodExistence.Options.defaults());assertTrue(check.verified(),check.message());
        y=CoxSurvivalData.rightCensored(new double[]{1,1,2},new boolean[]{true,true,false});assertFalse(CoxAdaptiveLikelihood.verifyDirection(y,new double[][]{{0},{1},{-1}},List.of(new CoxLikelihoodExistence.ExactCoefficient(0,"1","1")),CoxLikelihoodExistence.Options.defaults()).verified());
    }
    @Test void workAndDimensionBoundsRemainUnresolvedRatherThanFinite(){
        var y=CoxSurvivalData.rightCensored(new double[]{1,2,3},new boolean[]{true,true,false});var r=CoxAdaptiveLikelihood.analyze(y,new double[][]{{0},{1},{0}},new CoxLikelihoodExistence.Options(10000,1,100,64,16384,1e-10));assertEquals(CoxLikelihoodExistence.Status.UNRESOLVED,r.status());assertTrue(r.proof().work()<=1);
        assertTrue(CoxAdaptiveLikelihood.analyze(y,new double[3][0]).finiteMaximum());
    }
}
