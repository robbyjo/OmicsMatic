/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class Acat091Test {
    @Test void zeroWeightEndpointsRetainSourceSemantics(){var result=Acat091.combine(new double[]{0,.1},new double[]{0,1});assertFalse(result.admitted());assertTrue(Double.isNaN(result.columns().get(0).rawPValue()));assertEquals(List.of(Acat091.ZERO_WARNING),result.sourceWarnings());assertThrows(Acat091.Failure.class,()->Acat091.combine(new double[]{0,1},new double[]{1,0}));}
    @Test void matrixWarningsAreGlobalAndOrderIsPreserved(){double[][] p={{0,1,.1},{.3,.2,.8}};var result=Acat091.combineMatrix(p,null);assertEquals(3,result.columns().size());assertEquals(List.of(Acat091.ZERO_WARNING,Acat091.ONE_WARNING),result.sourceWarnings());assertEquals(0,result.columns().get(0).pValue());assertEquals(1,result.columns().get(1).pValue());assertEquals(.3,p[1][0]);}
    @Test void extendedMeanAvoidsAnIntermediateDoubleSumOverflow(){var result=Acat091.combine(new double[]{1e-308,1e-308,1e-308,1e-308});assertTrue(result.admitted());assertTrue(result.columns().get(0).pValue()>0);}
    @Test void sourceOverflowIsRetainedWithoutAdmission(){var result=Acat091.combine(new double[]{.01,.8},new double[]{1e308,1e308});assertEquals(.5,result.columns().get(0).rawPValue());assertFalse(result.admitted());assertThrows(ArithmeticException.class,result.columns().get(0)::pValue);}
    @Test void inputShapeAndFiniteIntakeAreExplicit(){assertThrows(Acat091.Failure.class,()->Acat091.combineMatrix(new double[][]{{.1},{.2,.3}},null));assertThrows(Acat091.Failure.class,()->Acat091.combineMatrix(new double[][]{{.1,.2},{.2,.3}},new double[][]{{1},{1}}));assertThrows(Acat091.Failure.class,()->Acat091.combine(new double[]{Double.NaN}));assertThrows(Acat091.Failure.class,()->Acat091.combine(new double[]{.1},new double[]{Double.POSITIVE_INFINITY}));}
    @Test void legacyStableCombinerRetainsItsContract(){assertEquals(.1,Acat.combine(new double[]{0,.1},new double[]{0,1}).pValue(),1e-15);assertTrue(Double.isFinite(Acat.combine(new double[]{.01,.8},new double[]{1e308,1e308}).pValue()));}
}
