/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DunnettModelTest {
    @Test void originalThomsonShortExampleAndSharedControlCovariance() {
        var result=DunnettModel.fit(new double[][]{{2.9,3,2.5,2.6,3.2},{3.8,2.7,4,2.4},{2.8,3.4,3.7,2.2,2}},0);
        assertEquals(14,result.observations());assertEquals(11,result.residualDegrees());
        assertEquals(2.84,result.groups().get(0).mean(),1e-14);
        assertEquals(.385,result.contrasts().get(0).difference(),1e-14);
        assertEquals(.90874545,result.contrasts().get(0).t(),1e-8);
        assertEquals(-.05007117,result.contrasts().get(1).t(),1e-8);
        assertEquals(Math.sqrt(2.0/9),result.correlation()[0][1],1e-15);
    }
    @Test void preservesTreatmentAxisWithMiddleControlAndSingletonTreatment() {
        var r=DunnettModel.fit(new double[][]{{8},{1,2,3},{4,6}},1);
        assertEquals(3,r.residualDegrees());assertEquals(Math.sqrt(4.0/3),r.residualSd(),1e-15);
        assertEquals(0,r.contrasts().get(0).treatment());assertEquals(2,r.contrasts().get(1).treatment());
        assertEquals(6,r.contrasts().get(0).difference());assertEquals(3,r.contrasts().get(1).difference());
        assertEquals(Math.sqrt(.25*.4),r.correlation()[0][1],1e-15);
    }
    @Test void onlyOneResidualDegreeIsSupportedAndAgreesWithCauchyTail() {
        var r=DunnettModel.fit(new double[][]{{0,2},{3}},0);var c=r.contrasts().get(0);
        assertEquals(1,r.residualDegrees());assertEquals(2,c.difference());
        assertEquals(1-2*Math.atan(Math.abs(c.t()))/Math.PI,c.unadjustedP(),1e-15);
    }
    @Test void immutableCopiesAndScaleEquivariance() {
        double[][] x={{1,2,3},{4,5,7},{2,9}};var a=DunnettModel.fit(x,2);
        double[][] correlation=a.correlation();correlation[0][1]=-99;
        assertTrue(a.correlation()[0][1]>0);assertThrows(UnsupportedOperationException.class,()->a.groups().clear());
        for(double[] row:x)for(int i=0;i<row.length;i++)row[i]*=1e140;
        var b=DunnettModel.fit(x,2);assertEquals(a.residualSd(),b.residualSd()/1e140,1e-14);
        for(int i=0;i<a.contrasts().size();i++)assertEquals(a.contrasts().get(i).t(),b.contrasts().get(i).t(),1e-14);
    }
    @Test void rejectsIncompleteDegenerateOrUnrepresentableModels() {
        assertThrows(IllegalArgumentException.class,()->DunnettModel.fit(new double[][]{{1},{2}},0));
        assertThrows(IllegalArgumentException.class,()->DunnettModel.fit(new double[][]{{1,2},{}},0));
        assertThrows(IllegalArgumentException.class,()->DunnettModel.fit(new double[][]{{1,Double.NaN},{2,3}},0));
        assertThrows(ArithmeticException.class,()->DunnettModel.fit(new double[][]{{1,1},{9,9}},0));
        assertThrows(ArithmeticException.class,()->DunnettModel.fit(new double[][]{{-Double.MAX_VALUE,-Double.MAX_VALUE},{Double.MAX_VALUE,Double.MAX_VALUE/2}},0));
        assertThrows(IllegalArgumentException.class,()->DunnettModel.fit(new double[][]{{1,2},{3,4}},2));
    }
    @Test void includesEqualMeansWithoutInventingSignificance() {
        var r=DunnettModel.fit(new double[][]{{1,2,3},{0,2,4},{2}},0);
        for(var c:r.contrasts()){assertEquals(0,c.t());assertEquals(1,c.unadjustedP());assertEquals(0,c.logUnadjustedP());}
    }
    @Test void finiteVeryLargeResidualSdDoesNotNeedAnOverflowingVariance() {
        var r=DunnettModel.fit(new double[][]{{-Double.MAX_VALUE,Double.MAX_VALUE},{1,2}},0);
        assertTrue(Double.isFinite(r.residualSd()));assertTrue(r.residualSd()>1e308);
    }
}
