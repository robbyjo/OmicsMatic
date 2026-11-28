/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaddLinearScoringTest {
    @Test void decisionFunctionUsesAllFeaturesAndIsNotLogisticProbability(){var m=new CaddLinearScoring(new double[]{2,-3},new double[]{2,4},-1);var p=m.predict(new float[]{4,8});assertEquals(-3,p.rawScore());assertEquals(2,p.features());assertTrue(p.lower()<=-3&&p.upper()>=-3);}
    @Test void sourceScalingRoundsToFloat32BeforeMultiplication(){double scale=3;var m=new CaddLinearScoring(new double[]{3},new double[]{scale},0);float[] out=new float[1];m.transform(new float[]{1},out);assertEquals(Float.floatToRawIntBits(1f/3f),Float.floatToRawIntBits(out[0]));assertEquals((double)(float)(1.0/3.0)*3,m.predict(new float[]{1}).rawScore());assertNotEquals(1,m.predict(new float[]{1}).rawScore());}
    @Test void coefficientAndScaleArraysAreDefensivelyCaptured(){double[] c={2},s={4};var m=new CaddLinearScoring(c,s,1);c[0]=100;s[0]=100;assertEquals(5,m.predict(new float[]{8}).rawScore());}
    @Test void allZeroFeaturesRetainTheTrainedIntercept(){assertEquals(-0.0012756284547776127,new CaddLinearScoring(new double[]{0.1,0.2},new double[]{0.3,0.4},-0.0012756284547776127).predict(new float[2]).rawScore());}
    @Test void negativeZeroAndSubnormalFeatureValuesSurviveScaling(){var m=new CaddLinearScoring(new double[]{1,1},new double[]{2,1},0);float[] result=new float[2];m.transform(new float[]{-0.0f,Float.MIN_VALUE},result);assertEquals(Float.floatToRawIntBits(-0f),Float.floatToRawIntBits(result[0]));assertEquals(Float.floatToRawIntBits(Float.MIN_VALUE),Float.floatToRawIntBits(result[1]));}
    @Test void scalingMayExplicitlyBeInPlace(){var m=new CaddLinearScoring(new double[]{1,1},new double[]{2,4},0);float[] values={4,8};m.transform(values,values);assertArrayEquals(new float[]{2,2},values);}
    @Test void cancellationRetainsAConservativeSourceOrderingEnvelope(){var m=new CaddLinearScoring(new double[]{1e16,1,-1e16},new double[]{1,1,1},0);var p=m.predict(new float[]{1,1,1});assertEquals(1,p.rawScore());assertTrue(p.lower()<=0&&p.upper()>=1);}
    @Test void missingFeaturesAreNotFilledOrSubsetted(){var m=new CaddLinearScoring(new double[]{1,2},new double[]{1,1},0);assertThrows(IllegalArgumentException.class,()->m.predict(new float[]{1}));assertThrows(IllegalArgumentException.class,()->m.transform(new float[]{1,2},new float[1]));}
    @Test void nonfiniteInputAndFloat32OverflowFailExplicitly(){var m=new CaddLinearScoring(new double[]{1},new double[]{1e-20},0);assertThrows(ArithmeticException.class,()->m.predict(new float[]{Float.NaN}));assertThrows(ArithmeticException.class,()->m.predict(new float[]{Float.POSITIVE_INFINITY}));assertThrows(ArithmeticException.class,()->m.predict(new float[]{Float.MAX_VALUE}));}
    @Test void invalidParametersAndWeightedOverflowCannotYieldAnAdmittedScore(){assertThrows(IllegalArgumentException.class,()->new CaddLinearScoring(new double[]{1},new double[]{0},0));assertThrows(IllegalArgumentException.class,()->new CaddLinearScoring(new double[]{Double.NaN},new double[]{1},0));assertThrows(IllegalArgumentException.class,()->new CaddLinearScoring(new double[]{1},new double[]{1},Double.NaN));assertThrows(ArithmeticException.class,()->new CaddLinearScoring(new double[]{Double.MAX_VALUE},new double[]{1},0).predict(new float[]{Float.MAX_VALUE}));}
}
