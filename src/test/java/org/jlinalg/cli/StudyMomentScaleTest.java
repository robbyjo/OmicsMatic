/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StudyMomentScaleTest {
    @Test void sourceEffectCorrelationAndRegressionAreUnitInvariantWithoutOverflowOrUnderflow(){
        for(double unit:new double[]{1,1e100,1e-100}){
            var moment=new StudyCliExecutor.Moment();double[] x={1,2,4},y={4,5,9};for(int i=0;i<x.length;i++)moment.add(x[i]*unit,y[i]*unit,false,false);
            assertEquals(.989743318610787,moment.correlation(),1e-13);assertEquals(1.7142857142857142,moment.slope(),1e-13);assertEquals(2*unit,moment.intercept(),Math.abs(unit)*1e-12);
        }
    }
    @Test void constantAndReversedSeriesRetainTheirDefinedCorrelationSemantics(){
        var constant=new StudyCliExecutor.Moment();var reversed=new StudyCliExecutor.Moment();for(int i=0;i<10;i++){constant.add(1,i,false,false);reversed.add(1e100*i,-1e100*i,false,false);}assertTrue(Double.isNaN(constant.correlation()));assertEquals(-1,reversed.correlation(),1e-14);assertEquals(-1,reversed.slope(),1e-14);
    }
}
