/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignacLsiTest {
    @Test void sampleCellSdAndFeatureBasedReportedSdFollowIndependentArithmetic(){
        var result=SignacLsi.postprocess(new double[][]{{1,0},{0,1}},new double[]{3,2},5,true,null,()->false);
        assertArrayEquals(new double[]{1.5,1},result.standardDeviations());double z=1/Math.sqrt(2);
        assertArrayEquals(new double[]{z,-z},result.values()[0],1e-15);assertArrayEquals(new double[]{-z,z},result.values()[1],1e-15);
        assertTrue(result.warnings().isEmpty());
    }
    @Test void scalingDoesNotWeightRawUBySingularValuesAndClippingIsConditional(){
        double[][] u={{1,0},{0,1}};
        var unscaled=SignacLsi.postprocess(u,new double[]{90,1},1,false,0.5,()->false);
        assertArrayEquals(u[0],unscaled.values()[0]);assertArrayEquals(u[1],unscaled.values()[1]);assertArrayEquals(new double[]{90,1},unscaled.standardDeviations());
        assertTrue(unscaled.warnings().get(0).startsWith("CLIP_IGNORED"));
        var clipped=SignacLsi.postprocess(u,new double[]{90,1},1,true,0.5,()->false);
        assertArrayEquals(new double[]{0.5,-0.5},clipped.values()[0]);assertArrayEquals(new double[]{-0.5,0.5},clipped.values()[1]);
    }
    @Test void constantComponentsRetainSourceNanAndNameRecoveryAction(){
        var result=SignacLsi.postprocess(new double[][]{{0.5},{0.5},{0.5}},new double[]{1},2,true,1.0,()->false);
        for(double[] row:result.values())assertTrue(Double.isNaN(row[0]));
        assertTrue(result.warnings().get(0).contains("ZERO_COMPONENT_SD"));assertTrue(result.warnings().get(0).contains("Skip finite-only analysis"));
    }
    @Test void exactSparseSingularTripletsAndJointSignFlipsPassBothResiduals(){
        for(double sign:new double[]{1,-1}){
            var check=SignacLsi.diagnose(2,2,new int[]{0,1,2},new int[]{0,1},new double[]{3,2},new double[][]{{sign,0},{0,sign}},new double[][]{{sign,0},{0,sign}},new double[]{3,2},()->false);
            assertEquals(0,check.maximumRelativeLeftResidual());assertEquals(0,check.maximumRelativeRightResidual());assertEquals(0,check.maximumUOrthogonalityError());assertEquals(0,check.maximumVOrthogonalityError());
        }
    }
    @Test void degenerateSingularSubspacesDoNotRequireAnArbitraryVectorOrientation(){
        double z=1/Math.sqrt(2);double[][] q={{z,z},{-z,z}};
        var check=SignacLsi.diagnose(2,2,new int[]{0,1,2},new int[]{0,1},new double[]{2,2},q,q,new double[]{2,2},()->false);
        assertEquals(0,check.maximumRelativeLeftResidual());assertEquals(0,check.maximumRelativeRightResidual());assertTrue(check.maximumUOrthogonalityError()<1e-15);
    }
    @Test void corruptedSingularVectorsAreDetectedIndependently(){
        var check=SignacLsi.diagnose(2,2,new int[]{0,1,2},new int[]{0,1},new double[]{3,2},new double[][]{{1.01,0},{0,1}},new double[][]{{1,0},{0,1}},new double[]{3,2},()->false);
        assertTrue(check.maximumRelativeLeftResidual()>0.001);assertTrue(check.maximumRelativeRightResidual()>0.001);assertTrue(check.maximumUOrthogonalityError()>0.01);
    }
    @Test void returnedEmbeddingArraysAndSingularValueMetadataAreImmutable(){
        double[][] u={{1},{0}};double[] d={2};var result=SignacLsi.postprocess(u,d,2,false,null,()->false);u[0][0]=9;d[0]=9;result.values()[0][0]=90;result.standardDeviations()[0]=90;
        assertEquals(1,result.values()[0][0]);assertArrayEquals(new double[]{2},result.standardDeviations());assertThrows(UnsupportedOperationException.class,()->result.warnings().add("x"));
    }
    @Test void invalidDimensionsRaggedArraysSingularOrderAndCoordinatesFail(){
        assertThrows(IllegalArgumentException.class,()->SignacLsi.postprocess(new double[][]{{1}},new double[]{1},1,true,null,()->false));
        assertThrows(IllegalArgumentException.class,()->SignacLsi.postprocess(new double[][]{{1},{0,1}},new double[]{1},1,true,null,()->false));
        assertThrows(IllegalArgumentException.class,()->SignacLsi.postprocess(new double[][]{{1,0},{0,1}},new double[]{1,2},2,true,null,()->false));
        assertThrows(IllegalArgumentException.class,()->SignacLsi.postprocess(new double[][]{{Double.NaN},{0}},new double[]{1},2,true,null,()->false));
        assertThrows(IllegalArgumentException.class,()->SignacLsi.postprocess(new double[][]{{1},{0}},new double[]{-1},2,true,null,()->false));
        assertThrows(IllegalArgumentException.class,()->SignacLsi.diagnose(2,2,new int[]{0,2,1},new int[]{0},new double[]{1},new double[][]{{1},{0}},new double[][]{{1},{0}},new double[]{1},()->false));
        assertThrows(IllegalArgumentException.class,()->SignacLsi.diagnose(2,2,new int[]{0,1,1},new int[]{-1},new double[]{1},new double[][]{{1},{0}},new double[][]{{1},{0}},new double[]{1},()->false));
    }
    @ParameterizedTest @ValueSource(doubles={-1,0,Double.NaN,Double.POSITIVE_INFINITY})void invalidClippingBoundsFail(double clip){
        assertThrows(IllegalArgumentException.class,()->SignacLsi.postprocess(new double[][]{{1},{0}},new double[]{1},2,true,clip,()->false));
    }
    @Test void cancellationPreservesCallerInputs(){
        double[][] u={{1},{0}};assertThrows(CancellationException.class,()->SignacLsi.postprocess(u,new double[]{1},2,true,null,()->true));assertEquals(1,u[0][0]);
        assertThrows(CancellationException.class,()->SignacLsi.diagnose(2,2,new int[]{0,1,1},new int[]{0},new double[]{1},u,u,new double[]{1},()->true));
    }
}
