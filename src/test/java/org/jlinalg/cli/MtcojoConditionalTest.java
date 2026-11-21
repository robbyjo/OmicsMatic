/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipInputStream;
import org.jlinalg.genetics.MtcojoConditional;
import org.junit.jupiter.api.Test;

class MtcojoConditionalTest {
    @Test void nativePointCalculationsMatchIndependentOracleAndBoundAllOriginalExports() throws Exception {
        var files=new HashMap<String,byte[]>();try(var source=getClass().getResourceAsStream("/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip")) {
            assertNotNull(source);try(var zip=new ZipInputStream(source)){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())assertNull(files.put(entry.getName(),zip.readAllBytes()));}
        }
        var corpus=object(SimpleJson.parse(new String(files.get("CORPUS.json"),StandardCharsets.UTF_8)),"corpus");var input=ByteBuffer.wrap(files.get("inputs.bin")).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic=new byte[9];input.get(magic);assertEquals("JMTINPUT1",new String(magic,StandardCharsets.US_ASCII));int n=input.getInt(),maximum=input.getInt();assertEquals(10000,n);assertEquals(2,maximum);
        double[] y=new double[n],sy=new double[n],x1=new double[n],sx1=new double[n];double[][] x=new double[n][2],sx=new double[n][2];
        for(int i=0;i<n;i++){y[i]=input.getDouble();sy[i]=input.getDouble();for(int j=0;j<2;j++){x[i][j]=input.getDouble();sx[i][j]=input.getDouble();}x1[i]=x[i][0];sx1[i]=sx[i][0];}assertFalse(input.hasRemaining());
        int cases=0,sourceFields=0;
        for(Object item:list(corpus.get("cases"),"cases")) {
            var model=object(item,"model");String name=text(model,"name");int q=((Number)model.get("covariates")).intValue();double[] b=vector(model.get("causal_effects"));double[][] intercept=matrix(model.get("sampling_intercepts"));
            var result=q==1?MtcojoConditional.single(y,sy,x1,sx1,b[0],intercept[0][1]):MtcojoConditional.multiple(y,sy,x,sx,b,vector(model.get("genetic_variances")),matrix(model.get("genetic_correlations")),intercept);
            double[] expectedJoint=vector(model.get("independent_joint_causal_effects")),joint=result.jointCausalEffects();assertEquals(n,result.size());
            for(int j=0;j<q;j++)assertEquals(expectedJoint[j],joint[j],Math.abs(expectedJoint[j])*2e-13+32*Math.ulp(expectedJoint[j]),name+" joint effect");
            var reference=ByteBuffer.wrap(files.get(text(model,"reference"))).order(ByteOrder.LITTLE_ENDIAN);byte[] header=new byte[8];reference.get(header);assertEquals("JMTREF01",new String(header,StandardCharsets.US_ASCII));assertEquals(n,reference.getInt());
            for(int i=0;i<n;i++) {
                double pointB=reference.getDouble(),pointSE=reference.getDouble(),pointP=reference.getDouble(),bTolerance=reference.getDouble();double[] original=new double[3],lower=new double[3],upper=new double[3];
                for(int j=0;j<3;j++)original[j]=reference.getDouble();for(int j=0;j<3;j++)lower[j]=reference.getDouble();for(int j=0;j<3;j++)upper[j]=reference.getDouble();
                assertEquals(pointB,result.effect(i),bTolerance,name+" variant "+i+" independent beta");assertEquals(pointSE,result.standardError(i),Math.abs(pointSE)*1e-12+64*Math.ulp(pointSE),name+" independent SE");
                assertEquals(pointP,result.pValue(i),Math.abs(pointP)*2e-11+128*Math.ulp(pointP),name+" independent P");
                for(int j=0;j<3;j++){assertTrue(original[j]>=lower[j]&&original[j]<=upper[j],name+" source rounding-bound field "+j+" at "+i);sourceFields++;}
            }
            assertFalse(reference.hasRemaining());cases++;
        }
        assertEquals(10,cases);assertEquals(300000,sourceFields);
    }
    @Test void sourcePivotingUnconstrainedMatricesAndDefensiveResultsArePreserved() {
        double[][] r={{1,.9,.1},{.9,1,.5},{.1,.5,1}},intercept={{.2,0,0,0},{0,.7,0,0},{0,0,-2.5,0},{0,0,0,.4}};
        double[] marginal={.125,1.01,1.94/3},y={.5},sy={.1};double[][] x={{.05,.1,.15}},sx={{.01,.02,.03}};
        var result=MtcojoConditional.multiple(y,sy,x,sx,marginal,new double[]{4,1,9},r,intercept);
        assertArrayEquals(new double[]{.2,-.4,.7},result.jointCausalEffects(),1e-14);assertEquals(.425,result.effect(0),1e-14);assertEquals(Math.sqrt(.010509),result.standardError(0),1e-14);
        assertArrayEquals(new double[]{1,.9,.1},r[0]);double value=result.effect(0);y[0]=90;x[0][0]=80;marginal[0]=70;result.effects()[0]=60;result.jointCausalEffects()[0]=50;assertEquals(value,result.effect(0));assertEquals(.2,result.jointCausalEffects()[0],1e-14);
        var indefinite=MtcojoConditional.multiple(new double[]{1},new double[]{.1},new double[][]{{.2,.4}},new double[][]{{.1,.1}},new double[]{.3,-.2},new double[]{1,1},new double[][]{{1,1.5},{1.5,1}},new double[][]{{.2,0,0},{0,.1,0},{0,0,-2}});
        assertArrayEquals(new double[]{-.48,.52},indefinite.jointCausalEffects(),1e-14);assertEquals(.888,indefinite.effect(0),1e-14);assertEquals(Math.sqrt(.015008),indefinite.standardError(0),1e-14);
        var zero=MtcojoConditional.single(new double[]{0},new double[]{.1},new double[]{.2},new double[]{.3},0,-5);assertEquals(0,zero.effect(0));assertEquals(.1,zero.standardError(0));assertEquals(1,zero.pValue(0));
    }
    @Test void wholeInputRefusalsCoverNonfiniteVarianceSolverDimensionsCapacityAndCancellation() {
        assertThrows(IllegalArgumentException.class,()->MtcojoConditional.single(new double[]{Double.NaN},new double[]{1},new double[]{1},new double[]{1},1,0));
        assertThrows(IllegalArgumentException.class,()->MtcojoConditional.single(new double[]{1},new double[]{0},new double[]{1},new double[]{1},1,0));
        assertThrows(IllegalArgumentException.class,()->MtcojoConditional.single(new double[]{1},new double[]{1,2},new double[]{1},new double[]{1},1,0));
        assertThrows(ArithmeticException.class,()->MtcojoConditional.single(new double[]{1},new double[]{1},new double[]{1},new double[]{1},1,1));
        assertThrows(ArithmeticException.class,()->MtcojoConditional.single(new double[]{1},new double[]{1},new double[]{1},new double[]{1},1,2));
        assertThrows(ArithmeticException.class,()->MtcojoConditional.single(new double[]{1e200},new double[]{1},new double[]{1},new double[]{1},0,0));
        assertThrows(IllegalArgumentException.class,()->MtcojoConditional.single(new double[]{1},new double[]{1},new double[]{1},new double[]{1},1,0,new MtcojoConditional.Limits(1,1,1,1)));
        assertThrows(ArithmeticException.class,()->MtcojoConditional.multiple(new double[]{1},new double[]{1},new double[][]{{1,1}},new double[][]{{1,1}},new double[]{1,1},new double[]{1,1},new double[][]{{1,1},{1,1}},new double[][]{{1,0,0},{0,1,0},{0,0,1}}));
        assertThrows(IllegalArgumentException.class,()->MtcojoConditional.multiple(new double[]{1},new double[]{1},new double[][]{{1,1}},new double[][]{{1,1}},new double[]{1,1},new double[]{1,1},new double[][]{{1,.1},{.2,1}},new double[][]{{1,0,0},{0,1,0},{0,0,1}}));
        try{Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->MtcojoConditional.single(new double[]{1},new double[]{1},new double[]{1},new double[]{1},1,0));}finally{Thread.interrupted();}
    }
    private static double[] vector(Object value) throws IOException {var list=list(value,"vector");double[] result=new double[list.size()];for(int i=0;i<result.length;i++)result[i]=((Number)list.get(i)).doubleValue();return result;}
    private static double[][] matrix(Object value) throws IOException {var list=list(value,"matrix");double[][] result=new double[list.size()][];for(int i=0;i<result.length;i++)result[i]=vector(list.get(i));return result;}
}
