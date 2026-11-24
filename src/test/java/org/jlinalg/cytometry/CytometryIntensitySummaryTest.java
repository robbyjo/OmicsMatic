/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cytometry.CytometryIntensitySummary.Statistic.*;
import static org.jlinalg.cytometry.CytometryIntensitySummary.Status.*;

class CytometryIntensitySummaryTest {
    @Test void explicitUnbinnedMomentsQuantilesAndMedianAbsoluteDeviation() {
        double[] x={4,1,2,3};var r=CytometryIntensitySummary.summarize(x);assertEquals(4,r.count());assertEquals(0,r.nonpositiveValues());assertEquals(2.5,r.statistics().get(MEAN).value());assertEquals(2.5,r.statistics().get(MEDIAN).value());assertEquals(Math.sqrt(5.0/3),r.statistics().get(SAMPLE_SD).value(),1e-15);assertEquals(1,r.statistics().get(MAD).value());assertEquals(1.0239,r.statistics().get(CENTRAL_68_26_HALF_WIDTH).value(),1e-15);assertEquals(Math.pow(24,.25),r.statistics().get(GEOMETRIC_MEAN).value(),1e-15);assertArrayEquals(new double[]{4,1,2,3},x);
    }
    @Test void zerosNegativesAndUndefinedRatiosRemainExplicit() {
        var r=CytometryIntensitySummary.summarize(new double[]{-2,0,2});assertEquals(2,r.nonpositiveValues());assertEquals(0,r.statistics().get(MEDIAN).value());assertEquals(ZERO_DENOMINATOR,r.statistics().get(CV_PERCENT).status());assertEquals(ZERO_DENOMINATOR,r.statistics().get(MAD_PERCENT).status());assertEquals(NONPOSITIVE_VALUES,r.statistics().get(GEOMETRIC_MEAN).status());assertNull(r.statistics().get(GEOMETRIC_MEAN).value());var negative=CytometryIntensitySummary.summarize(new double[]{-4,-2});assertTrue(negative.statistics().get(CV_PERCENT).value()<0);
    }
    @Test void emptyAndSingleEventPopulationsNeverReceiveInventedZeros() {
        var empty=CytometryIntensitySummary.summarize(new double[0]);for(var value:empty.statistics().values()){assertNull(value.value());assertEquals(EMPTY_POPULATION,value.status());}
        var one=CytometryIntensitySummary.summarize(new double[]{7});assertEquals(7,one.statistics().get(MEDIAN).value());assertEquals(INSUFFICIENT_COUNT,one.statistics().get(SAMPLE_SD).status());assertEquals(INSUFFICIENT_COUNT,one.statistics().get(CV_PERCENT).status());assertEquals(0,one.statistics().get(MAD).value());
    }
    @Test void nonfiniteValuesAreRefusedEvenWhenTheyWouldNotChangeAMedian() {
        for(double v:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){assertThrows(IllegalArgumentException.class,()->CytometryIntensitySummary.summarize(new double[]{1,2,3,v}));assertThrows(IllegalArgumentException.class,()->CytometryIntensitySummary.quantileType7(new double[]{1,v},.5));}
    }
    @Test void quantileConventionAndEndpointsAreExactAndCallerInputIsPreserved() {
        double[] x={20,0,10};assertEquals(5,CytometryIntensitySummary.quantileType7(x,.25));assertEquals(0,CytometryIntensitySummary.quantileType7(x,0));assertEquals(20,CytometryIntensitySummary.quantileType7(x,1));assertThrows(IllegalArgumentException.class,()->CytometryIntensitySummary.quantileType7(x,-Math.ulp(1.0)));assertArrayEquals(new double[]{20,0,10},x);
    }
    @Test void finiteExtremeMeansDoNotOverflowAnAvoidableSumOrDifference() {
        double max=Double.MAX_VALUE;assertEquals(max,CytometryIntensitySummary.summarize(new double[]{max,max}).statistics().get(MEAN).value());var r=CytometryIntensitySummary.summarize(new double[]{-max,max});assertEquals(0,r.statistics().get(MEDIAN).value());assertEquals(0,r.statistics().get(MEAN).value());assertEquals(NUMERIC_OVERFLOW,r.statistics().get(SAMPLE_SD).status());assertEquals(1,CytometryIntensitySummary.summarize(new double[]{1e308,3,-1e308}).statistics().get(MEAN).value());
    }
    @Test void sampleSpreadUsesSmallResidualsAroundALargeOffset() {
        double a=1e150,step=Math.ulp(a);var r=CytometryIntensitySummary.summarize(new double[]{a-step,a,a+step});assertEquals(step,r.statistics().get(SAMPLE_SD).value(),step*1e-14);assertEquals(step,r.statistics().get(MAD).value());assertThrows(UnsupportedOperationException.class,()->r.statistics().clear());
    }
}
