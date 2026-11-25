/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetaXcanMathTest {
    List<String[]> rows(String name)throws IOException {
        try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/metaxcan/"+name+".tsv")) {
            assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).map(s->s.split("\t",-1)).toList();
        }
    }
    double[] vector(String s){return s.equals("NA")?null:Arrays.stream(s.split(",")).mapToDouble(Double::parseDouble).toArray();}
    double d(String s){return Double.parseDouble(s);}
    void near(double expected,double actual) {
        if(expected==0)assertEquals(0,actual);
        else assertEquals(expected,actual,3e-9*Math.max(1e-300,Math.abs(expected)));
    }
    MetaXcanMath.Summary summary(String[] f){return MetaXcanMath.summary(vector(f[1]),vector(f[2]),vector(f[3]),vector(f[4]));}

    @Test void allFourteenOriginalSummaryCases()throws Exception {
        var rows=rows("summary");assertEquals(14,rows.size());
        for(var f:rows) {
            var fit=summary(f);near(d(f[5]),fit.zscore());
            if(f[6].equals("NA"))assertNull(fit.effectSize());else near(d(f[6]),fit.effectSize());
            near(d(f[7]),fit.variance());near(d(f[8]),fit.pvalue());
            if(fit.pvalue()>0)near(Math.log(fit.pvalue()),fit.logPvalue());
        }
    }
    @Test void allFourteenOriginalJointCases()throws Exception {
        var rows=rows("joint");assertEquals(14,rows.size());
        for(var f:rows) {
            var fit=MetaXcanMath.joint(vector(f[1]),vector(f[2]),new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.valueOf(f[3]),d(f[4])),f[5].equals("NA")?null:d(f[5]));
            near(d(f[6]),fit.statistic());near(d(f[7]),fit.pvalue());assertEquals(Integer.parseInt(f[8]),fit.components(),f[0]);
            near(d(f[9]),fit.maximumSingularValue());assertEquals(d(f[10]),fit.minimumSingularValue(),2e-12*Math.max(1,d(f[9])));
            near(d(f[11]),fit.minimumKeptSingularValue());near(d(f[12]),fit.traceMatrixInverse());near(d(f[13]),fit.absoluteCutoff());
            assertEquals(Integer.parseInt(f[14]),fit.bestTissueIndex());assertEquals(Integer.parseInt(f[15]),fit.worstTissueIndex());
            near(d(f[16]),fit.minimumZ());near(d(f[17]),fit.maximumZ());near(d(f[18]),fit.meanZ());
            if(f[19].equals("NA"))assertNull(fit.sdZ());else near(d(f[19]),fit.sdZ());
            assertEquals(Integer.parseInt(f[20]),fit.sourceStatus(),f[0]);
            if(fit.pvalue()>0)near(Math.log(fit.pvalue()),fit.logPvalue());else assertTrue(Double.isFinite(fit.logPvalue()));
        }
    }
    @Test void originalPhiControlsRetainUncalibratedValuesAndNegativePhiRule()throws Exception {
        Map<String,MetaXcanMath.Summary> summaries=new LinkedHashMap<>();for(var f:rows("summary"))summaries.put(f[0],summary(f));
        var rows=rows("calibration");assertEquals(4,rows.size());
        for(var f:rows) {
            var input=summaries.get(f[1]);var fit=MetaXcanMath.calibrate(input,d(f[2]),Long.parseLong(f[3]),d(f[4]));
            assertSame(input,fit.uncalibrated());near(d(f[5]),fit.zscore());near(d(f[6]),fit.pvalue());near(d(f[7]),fit.appliedPhi());
            assertEquals(d(f[2])<0,fit.negativePhiReplacedByZero());near(d(f[2]),fit.sourcePhi());
        }
    }
    @Test void sourceUsedIntersectionAndRawDosageVarianceAreNotNormalized() {
        // A caller-selected two-SNP intersection: this variance must not include
        // any omitted model SNP, and the beta is not z divided by sqrt(N).
        var fit=MetaXcanMath.summary(new double[]{1,2},new double[]{2,-1},new double[]{.3,-.2},new double[]{4,1,1,9});
        near(44,fit.variance());near(-2/Math.sqrt(44),fit.zscore());near(-2.4/44,fit.effectSize());
    }
    @Test void sourceCutoffKeepsFirstComponentAndThresholdIsNotGenericPcaRule() {
        var condition=new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.EIGEN_RATIO,100);
        var one=MetaXcanMath.joint(new double[]{2,1},new double[]{2,0,0,1},condition,null);assertEquals(1,one.components());near(2,one.statistic());
        var threshold=new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.VARIANCE_THRESHOLD,.05);
        var fit=MetaXcanMath.joint(new double[]{2,1,-1},new double[]{3,0,0,0,2,0,0,0,1},threshold,null);
        near(2,fit.absoluteCutoff());assertEquals(2,fit.components());
    }
    @Test void cutoffIsMeasuredBeforeRegularization() {
        var fit=MetaXcanMath.joint(new double[]{1,-2},new double[]{0,0,0,0},new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.EIGEN_RATIO,.01),.2);
        near(0,fit.absoluteCutoff());assertEquals(2,fit.components());near(25,fit.statistic());near(0,fit.traceMatrixInverse());
    }
    @Test void zeroVarianceRowsAndTinyPositiveTotalVarianceRemainSourceCompatible() {
        var zero=MetaXcanMath.summary(new double[]{1,2},new double[]{10,-3},null,new double[]{0,0,0,4});near(-3,zero.zscore());assertNull(zero.effectSize());
        var tiny=MetaXcanMath.summary(new double[]{1},new double[]{2},null,new double[]{1e-40});near(2,tiny.zscore());near(1e-40,tiny.variance());
    }
    @Test void emptyDimensionNonfiniteAndIndefiniteInputsAreRefused() {
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[0],new double[0],null,new double[0]));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[]{1},new double[]{Double.NaN},null,new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[]{1},new double[]{1},new double[]{Double.POSITIVE_INFINITY},new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[]{1,1},new double[]{1,2},null,new double[]{1,2,2,1}));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[]{1,1},new double[]{1,2},null,new double[]{1,.1,.2,1}));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[]{1},new double[]{1,2},null,new double[]{1}));
    }
    @Test void zeroPredictedVarianceNeverBecomesAnAdmittedNan() {
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[]{0},new double[]{2},null,new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.summary(new double[]{1},new double[]{2},null,new double[]{0}));
    }
    @Test void noFiniteInverseIsAdmittedForSelectedZeroSingularValue() {
        var cutoff=new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.EIGEN_RATIO,0);
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.joint(new double[]{1},new double[]{0},cutoff,null));
    }
    @Test void invalidCutoffsCalibrationAndRegularizationAreRefused() {
        assertThrows(IllegalArgumentException.class,()->new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.CONDITION_NUMBER,0));
        assertThrows(IllegalArgumentException.class,()->new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.VARIANCE_THRESHOLD,1.1));
        assertThrows(IllegalArgumentException.class,()->new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.EIGEN_RATIO,Double.NaN));
        var summary=MetaXcanMath.summary(new double[]{1},new double[]{2},null,new double[]{1});
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.calibrate(summary,Double.NaN,100,.2));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.calibrate(summary,.1,0,.2));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.calibrate(summary,1,100,-1));
        assertThrows(IllegalArgumentException.class,()->MetaXcanMath.joint(new double[]{1},new double[]{1},new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.EIGEN_RATIO,.1),-.01));
    }
    @Test void originalUnderflowStatusAndFiniteLogTailAreBothKept() {
        var fit=MetaXcanMath.joint(new double[]{120},new double[]{1},new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.CONDITION_NUMBER,30),null);
        assertEquals(0,fit.pvalue());assertEquals(-4,fit.sourceStatus());assertTrue(Double.isFinite(fit.logPvalue()));assertTrue(fit.logPvalue() < -7000);
    }
    @Test void callerArraysAreNeverModified() {
        double[] w={1,2},z={2,-1},beta={.3,-.2},cov={4,1,1,9};double[] wc=w.clone(),zc=z.clone(),bc=beta.clone(),cc=cov.clone();
        MetaXcanMath.summary(w,z,beta,cov);MetaXcanMath.joint(z,cov,new MetaXcanMath.Cutoff(MetaXcanMath.CutoffKind.CONDITION_NUMBER,30),.1);
        assertArrayEquals(wc,w);assertArrayEquals(zc,z);assertArrayEquals(bc,beta);assertArrayEquals(cc,cov);
    }
}