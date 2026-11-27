/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.qtl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatrixEqtlLinearTest {
    private static final double[][] X = {{0,1,2,0,1,2,0,1,2,0}};
    private static final double[][] Y = {{.4,1.3,1.8,.2,1.4,2.5,.5,.8,1.9,.7}};
    private static final double[][] NONE = new double[0][];
    private static MatrixEqtlLinear.Options options(double t, double c) { return new MatrixEqtlLinear.Options(t,c,100,2,100,100); }
    private static MatrixEqtlLinear.Result scan(double[][] x, double[][] y) { return MatrixEqtlLinear.scan(x,y,NONE,null,null,null,options(1,0),()->{}); }
    @Test void sourceRegressionAndIndependentClosedForm() {
        var a = scan(X,Y).transOrAll().associations().get(0);
        double mx=0,my=0;for(int i=0;i<10;i++){mx+=X[0][i]/10;my+=Y[0][i]/10;}
        double xx=0,xy=0;for(int i=0;i<10;i++){xx+=Math.pow(X[0][i]-mx,2);xy+=(X[0][i]-mx)*(Y[0][i]-my);}
        double beta=xy/xx, rss=0;for(int i=0;i<10;i++)rss+=Math.pow(Y[0][i]-my-beta*(X[0][i]-mx),2);
        double se=Math.sqrt(rss/8/xx);
        assertEquals(beta,a.beta(),2e-14);assertEquals(se,a.derivedSe(),2e-14);assertEquals(beta/se,a.statistic(),2e-13);
        assertEquals(a.pvalue(),a.fdr());
    }
    @Test void originalMissingRowMeanInsteadOfCompleteCase() {
        double[][] missing={{0,1,2,Double.NaN,1,2,0,1,2,0}};
        double[][] supplied={{0,1,2,1,1,2,0,1,2,0}}; // observed sum nine / nine = one
        var a=scan(missing,Y);var b=scan(supplied,Y);
        assertEquals(10,a.samples());assertEquals(1,a.imputedSnps());
        assertEquals(b.transOrAll().associations().get(0).beta(),a.transOrAll().associations().get(0).beta());
        assertTrue(Double.isNaN(missing[0][3]));
    }
    @Test void constantAndAllMissingRowsRemainInDenominator() {
        double[][] x={X[0],new double[10],{Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN}};
        var r=scan(x,Y);assertEquals(3,r.transOrAll().tests());assertEquals(3,r.transOrAll().associations().size());assertEquals(2,r.zeroSnps());assertEquals(10,r.imputedSnps());
        var a=r.transOrAll().associations().stream().filter(t->t.snp()==2).findFirst().orElseThrow();
        assertEquals(0,a.statistic());assertEquals(1,a.pvalue());assertTrue(Double.isNaN(a.derivedSe()));
        var real=r.transOrAll().associations().stream().filter(t->t.snp()==0).findFirst().orElseThrow();assertEquals(Math.min(1,3*real.pvalue()),real.fdr(),1e-14);
    }
    @Test void cisIncludesBothDistanceEndpointsAndReversedGeneInterval() {
        var gene=new MatrixEqtlLinear.Position("1",200,180);
        assertTrue(MatrixEqtlLinear.cis(new MatrixEqtlLinear.Position("1",80,80),gene,100));
        assertTrue(MatrixEqtlLinear.cis(new MatrixEqtlLinear.Position("1",300,300),gene,100));
        assertFalse(MatrixEqtlLinear.cis(new MatrixEqtlLinear.Position("1",301,301),gene,100));
        assertFalse(MatrixEqtlLinear.cis(new MatrixEqtlLinear.Position("chr1",200,200),gene,100));
    }
    @Test void separateCisAndTransTestingPopulations() {
        var p=new MatrixEqtlLinear.Position[]{new MatrixEqtlLinear.Position("1",100,100),new MatrixEqtlLinear.Position("2",100,100)};
        var gp=new MatrixEqtlLinear.Position[]{new MatrixEqtlLinear.Position("1",200,200)};
        var r=MatrixEqtlLinear.scan(new double[][]{X[0],X[0]},Y,NONE,p,gp,null,options(1,1),()->{});
        assertEquals(1,r.cis().tests());assertEquals(1,r.transOrAll().tests());assertEquals(2,r.evaluatedPairs());
        assertEquals(r.cis().associations().get(0).pvalue(),r.cis().associations().get(0).fdr());
    }
    @Test void covarianceIdentityEqualsOrdinaryFit() {
        double[][] cov=new double[10][10];for(int i=0;i<10;i++)cov[i][i]=1;
        var a=MatrixEqtlLinear.scan(X,Y,NONE,null,null,cov,options(1,0),()->{}).transOrAll().associations().get(0);
        assertEquals(scan(X,Y).transOrAll().associations().get(0).beta(),a.beta(),2e-14);
    }
    @Test void refusesNonpositiveAndAsymmetricCovariance() {
        double[][] cov=new double[10][10];assertThrows(IllegalArgumentException.class,()->MatrixEqtlLinear.scan(X,Y,NONE,null,null,cov,options(1,0),()->{}));
        for(int i=0;i<10;i++)cov[i][i]=1;cov[0][1]=.1;
        assertThrows(IllegalArgumentException.class,()->MatrixEqtlLinear.scan(X,Y,NONE,null,null,cov,options(1,0),()->{}));
    }
    @Test void sourceCollinearCovariateRejection() {
        assertThrows(IllegalArgumentException.class,()->MatrixEqtlLinear.scan(X,Y,new double[][]{new double[10]},null,null,null,options(1,0),()->{}));
    }
    @Test void completeCapacityAndCancellationNeverReturnPartialBatch() {
        assertThrows(IllegalArgumentException.class,()->MatrixEqtlLinear.scan(new double[][]{X[0],X[0]},Y,NONE,null,null,null,new MatrixEqtlLinear.Options(1,0,100,2,1,100),()->{}));
        assertThrows(IllegalArgumentException.class,()->MatrixEqtlLinear.scan(new double[][]{X[0],X[0]},Y,NONE,null,null,null,new MatrixEqtlLinear.Options(1,0,100,2,100,1),()->{}));
        assertThrows(IllegalStateException.class,()->MatrixEqtlLinear.scan(X,Y,NONE,null,null,null,options(1,0),()->{throw new IllegalStateException("cancelled");}));
    }
    @Test void rejectsInfinityAndSampleMismatch() {
        assertThrows(IllegalArgumentException.class,()->scan(new double[][]{{Double.POSITIVE_INFINITY,1,2,3}},Y));
        assertThrows(IllegalArgumentException.class,()->scan(new double[][]{{0,1,2,3}},Y));
    }
    @Test void thresholdSelectionDoesNotChangeTotalTestsOrBH() {
        var all=scan(new double[][]{X[0],new double[10]},Y);double cut=all.transOrAll().associations().get(0).pvalue()*1.1;
        var few=MatrixEqtlLinear.scan(new double[][]{X[0],new double[10]},Y,NONE,null,null,null,options(cut,0),()->{});
        assertEquals(2,few.transOrAll().tests());assertEquals(1,few.transOrAll().associations().size());
        assertEquals(all.transOrAll().associations().get(0).fdr(),few.transOrAll().associations().get(0).fdr(),2e-14);
    }
}
