/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.regression;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FirthLogisticTest {
    @Test void fullAndConstrainedFitsMatchIndependentUpstreamLogistf()throws Exception {
        Path root=Path.of("src/test/resources/firth-logistic");var references=Files.readAllLines(root.resolve("reference.tsv"));
        assertEquals(10,references.size());
        for(String line:references.subList(1,references.size())) {
            String[] expected=line.split("\t");String name=expected[0];int p=Integer.parseInt(expected[1]);
            var input=Files.readAllLines(root.resolve(name+".tsv"));int n=input.size()-1;double[] y=new double[n],offset=new double[n];double[][] x=new double[n][p];
            for(int i=0;i<n;i++){String[] cells=input.get(i+1).split("\t");y[i]=Double.parseDouble(cells[0]);offset[i]=Double.parseDouble(cells[1]);for(int j=0;j<p;j++)x[i][j]=Double.parseDouble(cells[j+2]);}
            var fit=FirthLogistic.test(y,x,offset,p-1,Double.parseDouble(expected[9]),FirthLogistic.Options.defaults());
            assertTrue(fit.converged(),name+": "+fit.full().reason()+" / "+fit.constrained().reason());
            near(expected[2],fit.beta(),2e-6,name+" beta");near(expected[3],fit.standardError(),2e-6,name+" SE");
            near(expected[4],fit.full().penalizedLogLikelihood(),1e-9,name+" full PLL");near(expected[5],fit.constrained().penalizedLogLikelihood(),1e-9,name+" null PLL");
            near(expected[6],fit.likelihoodRatio(),2e-8,name+" PLR");near(expected[7],fit.pValue(),2e-7,name+" P");near(expected[8],fit.logPValue(),2e-8,name+" log P");
        }
    }
    @Test void binaryPredictorHasAnalyticHalfCountCorrectionAndSameDesignNull() {
        double[] y=new double[100];double[][] x=new double[100][2];
        for(int i=0;i<100;i++){x[i][0]=1;x[i][1]=i<20?1:0;y[i]=i<18||i>=20&&i<25?1:0;}
        var fit=FirthLogistic.test(y,x,null,1,0,FirthLogistic.Options.defaults());assertTrue(fit.converged());
        assertEquals(Math.log(18.5/2.5)-Math.log(5.5/75.5),fit.beta(),1e-7);
        assertEquals(Math.log(24.0/78),fit.constrained().coefficients()[0],1e-7);
        assertEquals(0,fit.constrained().coefficients()[1]);
        assertTrue(Math.abs(fit.beta()/fit.standardError()-Math.sqrt(fit.likelihoodRatio()))>.1,"PLR must not be relabelled a Wald test");
    }
    @Test void nonconvergenceDoesNotYieldAnAssociationPValue() {
        double[] y={0,0,0,1,1,1};double[][] x={{1,0},{1,0},{1,0},{1,1},{1,1},{1,1}};
        var fit=FirthLogistic.test(y,x,null,1,0,new FirthLogistic.Options(1,5,1e-8,5));
        assertFalse(fit.converged());assertTrue(Double.isNaN(fit.pValue()));assertEquals("iteration_limit",fit.full().reason());
    }
    @Test void underflowingPValuesKeepTheirFiniteLogTail() {
        double[] y=new double[2000];double[][] x=new double[2000][2];for(int i=0;i<2000;i++){x[i][0]=1;x[i][1]=i<1000?1:0;y[i]=x[i][1];}
        var fit=FirthLogistic.test(y,x,null,1,0,FirthLogistic.Options.defaults());assertTrue(fit.converged());assertEquals(0,fit.pValue());assertTrue(Double.isFinite(fit.logPValue()));assertTrue(fit.logPValue() < -1000);
    }
    @Test void invalidAndRankDeficientInputsAreRejectedWithoutRidgeRepair() {
        assertThrows(IllegalArgumentException.class,()->FirthLogistic.fit(new double[]{0,.5,1},new double[][]{{1},{1},{1}},null,FirthLogistic.Options.defaults()));
        assertThrows(IllegalArgumentException.class,()->FirthLogistic.fit(new double[]{0,0,0},new double[][]{{1},{1},{1}},null,FirthLogistic.Options.defaults()));
        assertThrows(ArithmeticException.class,()->FirthLogistic.fit(new double[]{0,1,0},new double[][]{{1,1},{1,1},{1,1}},null,FirthLogistic.Options.defaults()));
    }
    private static void near(String expected,double actual,double relative,String label){double value=Double.parseDouble(expected);assertEquals(value,actual,Math.max(1e-10,relative*Math.abs(value)),label);}
}
