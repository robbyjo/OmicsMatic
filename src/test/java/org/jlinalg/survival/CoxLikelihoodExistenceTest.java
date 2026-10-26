/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoxLikelihoodExistenceTest {
    private CoxSurvivalData response(double[] time,boolean[] event){return CoxSurvivalData.rightCensored(time,event);}
    @Test void finiteUnivariateMaximumHasAnExactPositiveBalance(){
        var r=CoxLikelihoodExistence.analyze(response(new double[]{1,2,3},new boolean[]{true,true,false}),new double[][]{{0},{1},{0}});
        assertTrue(r.finiteMaximum(),r.message());assertEquals(1,r.numericalRank());assertEquals(2,r.contrasts());assertEquals(1,r.basisCorrections().size());
    }
    @Test void infiniteBinaryEstimateHasAnExactSeparatingDirection(){
        var r=CoxLikelihoodExistence.analyze(response(new double[]{1,2,3},new boolean[]{true,true,false}),new double[][]{{0},{0},{1}});
        assertEquals(CoxLikelihoodExistence.Status.MONOTONE_LIKELIHOOD,r.status(),r.message());assertTrue(r.direction()[0]<0);
    }
    @Test void jointSeparationIsFoundWhenNeitherCovariateAloneSeparates(){
        var y=response(new double[]{1,2,3,4},new boolean[]{true,true,true,false});double[][] x={{2,1},{0,2},{1,0},{-1,1}};
        var r=CoxLikelihoodExistence.analyze(y,x);assertEquals(CoxLikelihoodExistence.Status.MONOTONE_LIKELIHOOD,r.status(),r.message());assertTrue(r.direction()[0]>0 && r.direction()[1]>0);
        assertTrue(CoxLikelihoodExistence.analyze(y,new double[][]{{2},{0},{1},{-1}}).finiteMaximum());
        assertTrue(CoxLikelihoodExistence.analyze(y,new double[][]{{1},{2},{0},{1}}).finiteMaximum());
    }
    @Test void tiedFailuresConstrainEachOtherAndEntryAndStrataUseActualRiskSets(){
        assertTrue(CoxLikelihoodExistence.analyze(response(new double[]{1,1,2},new boolean[]{true,true,false}),new double[][]{{0},{1},{10}}).finiteMaximum());
        var y=new CoxSurvivalData(new double[]{0,1},new double[]{1,2},new boolean[]{true,false},null);
        assertEquals(CoxLikelihoodExistence.Status.UNRESOLVED,CoxLikelihoodExistence.analyze(y,new double[][]{{0},{1}}).status());
        y=CoxSurvivalData.rightCensored(new double[]{1,2,1,2},new boolean[]{true,false,true,false},new int[]{0,0,1,1});
        assertTrue(CoxLikelihoodExistence.analyze(y,new double[][]{{1},{0},{100},{101}}).finiteMaximum());
    }
    @Test void rankAndResourceLimitsNeverCertifyFiniteEstimates(){
        var y=response(new double[]{1,2,3},new boolean[]{true,true,false});
        assertEquals(CoxLikelihoodExistence.Status.UNRESOLVED,CoxLikelihoodExistence.analyze(y,new double[][]{{0,0},{1,2},{0,0}}).status());
        var o=new CoxLikelihoodExistence.Options(1,100000,50,64,16384,1e-10);
        var r=CoxLikelihoodExistence.analyze(y,new double[][]{{0},{1},{0}},o);assertEquals(CoxLikelihoodExistence.Status.UNRESOLVED,r.status());assertTrue(r.message().contains("pair-visit"));
        o=new CoxLikelihoodExistence.Options(1000,1,50,64,16384,1e-10);assertFalse(CoxLikelihoodExistence.analyze(y,new double[][]{{0},{1},{0}},o).finiteMaximum());
    }
    @Test void zeroCoefficientNullAndDefensiveCertificateCopiesAreSupported(){
        var y=response(new double[]{1,2},new boolean[]{true,false});var r=CoxLikelihoodExistence.analyze(y,new double[2][0]);assertTrue(r.finiteMaximum());
        r=CoxLikelihoodExistence.analyze(y,new double[][]{{1},{0}});var direction=r.direction();direction[0]=0;assertNotEquals(0,r.direction()[0]);
        assertThrows(IllegalArgumentException.class,()->CoxLikelihoodExistence.analyze(y,new double[][]{{Double.NaN},{1}}));
    }
    @Test void checkedFitRejectsAnInfiniteEstimateEvenWhenOrdinaryNewtonReportsConvergence(){
        var y=response(new double[]{1,2,3,4,5,6},new boolean[]{true,true,true,false,false,false});double[][] x={{0},{0},{0},{1},{1},{1}};
        var ordinary=CoxRegression.fit(y,x,null,CoxOptions.defaults(),org.jlinalg.compute.BackendPolicy.CPU);assertTrue(ordinary.converged());
        var error=assertThrows(CoxLikelihoodException.class,()->CoxRegression.fitChecked(y,x,null,CoxOptions.defaults(),org.jlinalg.compute.BackendPolicy.CPU,CoxLikelihoodExistence.Options.defaults()));
        assertEquals(CoxLikelihoodExistence.Status.MONOTONE_LIKELIHOOD,error.likelihood().status());
    }
    @Test void eightyIndependentHighsLinearProgramsAgreeAcrossCensoringTiesEntryAndStrata()throws Exception{
        Path root=Path.of("src/test/resources/cox-likelihood-existence");var rows=new LinkedHashMap<String,List<String[]>>();
        for(String line:Files.readAllLines(root.resolve("inputs.tsv")).subList(1,Files.readAllLines(root.resolve("inputs.tsv")).size())){String[] fields=line.split("\t");rows.computeIfAbsent(fields[0],k->new ArrayList<>()).add(fields);}
        int count=0;for(String line:Files.readAllLines(root.resolve("expected.tsv")).subList(1,81)){
            String[] expected=line.split("\t");var data=rows.get(expected[0]);int n=data.size(),p=Integer.parseInt(expected[1]);double[] entry=new double[n],time=new double[n];boolean[] event=new boolean[n];int[] strata=new int[n];double[][] x=new double[n][p];
            for(int i=0;i<n;i++){var f=data.get(i);entry[i]=Double.parseDouble(f[2]);time[i]=Double.parseDouble(f[3]);event[i]=f[4].equals("1");strata[i]=Integer.parseInt(f[5]);for(int j=0;j<p;j++)x[i][j]=Double.parseDouble(f[6+j]);}
            var response=new CoxSurvivalData(entry,time,event,strata);
            var r=CoxLikelihoodExistence.analyze(response,x);assertEquals(expected[2],r.status().name(),expected[0]+": "+r.message());
            var adaptive=CoxAdaptiveLikelihood.analyze(response,x);assertEquals(expected[2],adaptive.status().name(),expected[0]+": "+adaptive.message());count++;
        }assertEquals(80,count);
    }
}
