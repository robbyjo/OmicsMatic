/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.network.Wgcna1721.*;

class Wgcna1721Test {
    private RankResult rank(double[][] x,double[] weights,Method method){return rankPvalue(x,weights,Ties.AVERAGE,Missing.KEEP,method);}
    @Test void rankExtremeUsesUnmultipliedMinimumAndSeparateSourceScaleTail(){
        var r=rank(new double[][]{{1,1,1,1},{2,2,2,2},{3,3,3,3}},null,Method.ALL);assertEquals(6,r.columns().size());assertEquals(.010460667668897, r.values()[0][0],1e-13);assertEquals(r.values()[0][1],r.values()[0][0]);assertEquals(2*r.values()[0][4],r.values()[0][3],1e-15);assertEquals(1,r.values()[1][3],1e-15);
    }
    @Test void sourceUsesMaximumTiedRankRatherThanNumberOfRows(){
        var r=rank(new double[][]{{1,2,3,4},{1,2,3,4},{1,2,3,4}},null,Method.ALL);assertTrue(r.values()[0][0]>.95);assertEquals(r.values()[0][1],r.values()[0][2]);assertEquals(1,r.values()[0][3]);assertEquals(.5,r.values()[0][4]);
    }
    @Test void allMissingRowsRemainUnassessable(){var r=rank(new double[][]{{Double.NaN,Double.NaN},{1,2},{3,4}},null,Method.ALL);for(double p:r.values()[0])assertTrue(Double.isNaN(p));}
    @Test void zeroWeightSumPreservesOriginalMissingResults(){var r=rank(new double[][]{{1,2},{3,4}},new double[]{0,0},Method.ALL);for(double[] row:r.values())for(double p:row)assertTrue(Double.isNaN(p));}
    @Test void weightsNormalizeWithoutChangingResult(){double[][] x={{1,6,3},{2,5,6},{4,7,2}};var a=rank(x,new double[]{1,2,7},Method.ALL);var b=rank(x,new double[]{.1,.2,.7},Method.ALL);for(int i=0;i<3;i++)assertArrayEquals(a.values()[i],b.values()[i],1e-14);}
    @Test void infinitiesHaveOrdinalRankRatherThanBeingOmitted(){var r=rank(new double[][]{{Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY},{0,1},{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY}},null,Method.RANK);assertTrue(r.values()[0][1]<.1);assertTrue(r.values()[2][2]<.1);}
    @Test void smallModulesUseZeroWithinConnectivity(){var k=intramodularConnectivity(new double[][]{{99,.3,.8},{.3,99,.4},{.8,.4,99}},new String[]{"a","a","b"},false);assertEquals(0,k[0][1]);assertEquals(1.1,k[0][0],1e-14);assertEquals(-1.1,k[0][3],1e-14);}
    @Test void connectivityUsesColumnSumsAndOmitsOnlyMissingOffDiagonal(){var k=intramodularConnectivity(new double[][]{{99,1,2},{3,99,Double.NaN},{4,5,99}},new String[]{"a","a","a"},false);assertArrayEquals(new double[]{7,7,0,7},k[0]);assertArrayEquals(new double[]{6,6,0,6},k[1]);assertArrayEquals(new double[]{2,2,0,2},k[2]);}
    @Test void scalingLeavesSmallModuleZeroAndOutsideDifferenceMissing(){var k=intramodularConnectivity(new double[][]{{1,2,3},{4,1,5},{6,7,1}},new String[]{"a","a","b"},true);for(double[] row:k){assertEquals(0,row[1]);assertTrue(Double.isNaN(row[2]));assertTrue(Double.isNaN(row[3]));}}
    @Test void zeroModuleMaximumRetainsMissingScaledWithin(){var k=intramodularConnectivity(new double[3][3],new String[]{"a","a","a"},true);for(double[] row:k)assertTrue(Double.isNaN(row[1]));}
    @Test void refusesMalformedWeightsAndMatrices(){assertThrows(IllegalArgumentException.class,()->rank(new double[][]{{1},{2}},new double[]{-1},Method.RANK));assertThrows(IllegalArgumentException.class,()->rank(new double[][]{{1},{2,3}},null,Method.RANK));assertThrows(IllegalArgumentException.class,()->intramodularConnectivity(new double[][]{{1},{2}},new String[]{"a","a"},false));}
}
