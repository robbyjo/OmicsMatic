/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jlinalg.association.AssociationBatchResult;
import org.jlinalg.association.AssociationEngineOptions;
import org.junit.jupiter.api.Test;

class FastCoxAssociationTest {
    @Test void scoreExportAndOneStepScansAgreeAndRespectSourceUnits(){
        int n=48;double[] time=new double[n];boolean[] event=new boolean[n];double[][] x=new double[n][1],g=new double[n][2];double[][] variants=new double[2][n];
        for(int i=0;i<n;i++){time[i]=1+(i*17%53);event[i]=i%4!=0;x[i][0]=Math.sin(i*.3);g[i][0]=Math.cos(i*.7);g[i][1]=(i*7%19)-9;for(int j=0;j<2;j++)variants[j][i]=g[i][j];}
        var y=CoxSurvivalData.rightCensored(time,event);var options=AssociationEngineOptions.defaults();var prepared=FastCoxAssociation.prepare(y,x,null,CoxOptions.defaults(),options);var score=prepared.score(variants);var scan=prepared.scan(g,List.of("a","b"),options,CoxScoreVariance.MODEL_BASED);
        for(int j=0;j<2;j++){double v=score.information()[j*2+j];assertEquals(score.scores()[j]/v,scan.beta()[j],1e-12);assertEquals(1/Math.sqrt(v),scan.standardErrors()[j],1e-12);}
        assertEquals(score.information()[1],score.information()[2],0);
        for(double unit:new double[]{1e-100,1e100,-1e-100,-1e100}){
            double[][] sx=new double[n][1],sg=new double[n][2],sv=new double[2][n];for(int i=0;i<n;i++){sx[i][0]=x[i][0]*unit;for(int j=0;j<2;j++){sg[i][j]=g[i][j]*unit;sv[j][i]=sg[i][j];}}
            var scaled=FastCoxAssociation.prepare(y,sx,null,CoxOptions.defaults(),options);var r=scaled.scan(sg,List.of("a","b"),options,CoxScoreVariance.MODEL_BASED);var ss=scaled.score(sv);assertTrue(r.successful());
            for(int j=0;j<2;j++){assertEquals(scan.beta()[j],r.beta()[j]*unit,1e-9);assertEquals(scan.standardErrors()[j],r.standardErrors()[j]*Math.abs(unit),1e-9);assertEquals(score.scores()[j],ss.scores()[j]/unit,1e-9);for(int k=0;k<2;k++)assertEquals(score.information()[j*2+k],ss.information()[j*2+k]/unit/unit,1e-8);}
        }
    }
    @Test void anUnrepresentablePhysicalScoreVarianceIsNotExportedAsZero(){
        var y=CoxSurvivalData.rightCensored(new double[]{1,2,3,4},new boolean[]{true,true,true,false});var prepared=FastCoxAssociation.prepare(y,new double[4][0],null,CoxOptions.defaults(),AssociationEngineOptions.defaults());
        assertThrows(IllegalArgumentException.class,()->prepared.score(new double[][]{{0,1e-200,0,1e-200}}));
    }
    @Test void unadjustedScansHaveAGenuineZeroCoefficientCertifiedNull(){
        var y=CoxSurvivalData.rightCensored(new double[]{1,2,3,4,5,6},new boolean[]{true,false,true,true,false,true});var options=AssociationEngineOptions.defaults();
        var scan=FastCoxAssociation.prepare(y,new double[6][0],null,CoxOptions.defaults(),options);assertEquals(0,scan.nullModel().beta().length);assertTrue(scan.nullLikelihood().finiteMaximum());
        var r=scan.scan(new double[][]{{0},{1},{2},{1},{0},{2}},List.of("g"),options,CoxScoreVariance.MODEL_BASED);assertTrue(r.successful());assertTrue(Double.isFinite(r.pValues()[0]));
    }
    @Test void monotoneOrBudgetUncertifiedNuisanceModelsCannotPrepareAScan(){
        var y=CoxSurvivalData.rightCensored(new double[]{1,2,3,4,5,6},new boolean[]{true,true,true,false,false,false});double[][] x={{0},{0},{0},{1},{1},{1}};
        var error=assertThrows(CoxLikelihoodException.class,()->FastCoxAssociation.prepare(y,x,null,CoxOptions.defaults(),AssociationEngineOptions.defaults()));assertEquals(CoxLikelihoodExistence.Status.MONOTONE_LIKELIHOOD,error.likelihood().status());
        var bounds=new CoxLikelihoodExistence.Options(10000,1,50,64,16384,1e-10);assertThrows(CoxLikelihoodException.class,()->FastCoxAssociation.prepare(y,x,null,CoxOptions.defaults(),AssociationEngineOptions.defaults(),null,null,bounds));
    }
    @Test void nonbinaryOrNonfiniteEventCodesAreRejectedRatherThanSilentlyCensored(){
        for(double code:new double[]{2,-1,.5,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->CoxSurvivalData.eventIndicators(new double[]{0,code,1}));
        assertArrayEquals(new boolean[]{false,true},CoxSurvivalData.eventIndicators(new double[]{0,1}));
    }
    @Test
    void preparedScoreMatchesDirectEfficientInformation() {
        double[] time = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        boolean[] event = {
            true, false, true, true, false, true,
            true, false, true, true, false, true
        };
        double[][] covariates = new double[time.length][1];
        double[][] predictors = new double[time.length][2];
        for (int row = 0; row < time.length; row++) {
            covariates[row][0] = (row % 3) - 1.0;
            predictors[row][0] = ((row * 5) % 11) - 5.0;
            predictors[row][1] = row % 4;
        }
        CoxSurvivalData survival = CoxSurvivalData.rightCensored(time, event);
        AssociationEngineOptions options = AssociationEngineOptions.defaults()
            .withParallelism(2).withChunkSize(2);
        FastCoxAssociation prepared = FastCoxAssociation.prepare(survival,
            covariates, null, CoxOptions.defaults(), options,
            List.of("a", "a", "b", "b", "c", "c",
                "d", "d", "e", "e", "f", "f"),
            identity(time.length));

        AssociationBatchResult model = prepared.scan(predictors,
            List.of("g1", "g2"), options, CoxScoreVariance.MODEL_BASED);
        AssociationBatchResult robust = prepared.scan(predictors,
            List.of("g1", "g2"), options, CoxScoreVariance.CLUSTER_ROBUST);
        AssociationBatchResult related = prepared.scan(predictors,
            List.of("g1", "g2"), options, CoxScoreVariance.RELATEDNESS);

        assertTrue(model.successful());
        assertEquals(2, model.size());
        for (int index = 0; index < 2; index++) {
            assertTrue(Double.isFinite(model.beta()[index]));
            assertTrue(model.standardErrors()[index] > 0.0);
            assertTrue(robust.standardErrors()[index] > 0.0);
            assertTrue(related.standardErrors()[index] > 0.0);
            assertEquals(related.beta()[index], model.beta()[index], 0.0);
        }
        assertEquals(prepared.nullModel().backend(), prepared.backend());
    }

    private static double[] identity(int dimension) {
        double[] result = new double[dimension * dimension];
        for (int index = 0; index < dimension; index++)
            result[index * dimension + index] = 1.0;
        return result;
    }
}
