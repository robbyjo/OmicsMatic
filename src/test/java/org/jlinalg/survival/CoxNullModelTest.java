/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

import static org.junit.jupiter.api.Assertions.*;
import org.jlinalg.compute.BackendPolicy;
import org.junit.jupiter.api.Test;

class CoxNullModelTest {
    @Test void unadjustedNullUsesExactRightCensoredRiskSetsAndNoArtificialIntercept(){
        var data=CoxSurvivalData.rightCensored(new double[]{1,2,3,4},new boolean[]{true,false,true,true});
        var fit=CoxRegression.fit(data,new double[4][0],null,CoxOptions.defaults(),BackendPolicy.CPU);
        assertTrue(fit.converged());assertEquals(0,fit.iterations());assertEquals(0,fit.beta().length);assertEquals(-Math.log(4)-Math.log(2),fit.logPartialLikelihood(),1e-14);
        assertEquals(1./4+1./2+1.,fit.baselineHazard().get(2).cumulativeHazard(),1e-14);
    }
    @Test void delayedEntryStrataAndOffsetsUseTheDeclaredTieMethod(){
        var data=new CoxSurvivalData(new double[]{0,.5,1,0,1},new double[]{2,2,3,1,3},new boolean[]{true,true,false,true,false},new int[]{0,0,0,1,1});
        double[] offsets={Math.log(2),0,0,0,0};
        var efron=CoxRegression.fit(data,new double[5][0],offsets,CoxOptions.defaults(),BackendPolicy.CPU);
        var breslow=CoxRegression.fit(data,new double[5][0],offsets,CoxOptions.defaults().withTies(CoxTies.BRESLOW),BackendPolicy.CPU);
        // At t=2: weighted risk=4, tied death risk=3. Separate t=1 stratum has only its event at risk.
        assertEquals(Math.log(2)-Math.log(4)-Math.log(2.5),efron.logPartialLikelihood(),1e-13);
        assertEquals(Math.log(2)-2*Math.log(4),breslow.logPartialLikelihood(),1e-13);
        assertEquals(2,efron.baselineHazard().size());
    }
    @Test void malformedZeroColumnRowsAndOffsetsAreRejected(){
        var data=CoxSurvivalData.rightCensored(new double[]{1,2},new boolean[]{true,false});
        assertThrows(IllegalArgumentException.class,()->CoxRegression.fit(data,new double[][]{{},{1}},null,CoxOptions.defaults(),BackendPolicy.CPU));
        assertThrows(IllegalArgumentException.class,()->CoxRegression.fit(data,new double[2][0],new double[]{0,Double.NaN},CoxOptions.defaults(),BackendPolicy.CPU));
        assertThrows(IllegalArgumentException.class,()->CoxRegression.fit(data,new double[2][0],new double[1],CoxOptions.defaults(),BackendPolicy.CPU));
    }
    @Test void anExplicitZeroInformationRidgeDoesNotAddAnUndeclaredFallbackPenalty(){
        try(var c=org.jlinalg.compute.BackendContext.select(BackendPolicy.CPU)){
            assertThrows(IllegalArgumentException.class,()->CoxMath.solvePositive(c.backend(),new double[]{0},1,new double[]{0},0));
            assertEquals(0,CoxMath.solvePositive(c.backend(),new double[]{0},1,new double[]{0},1e-10)[0]);
        }
    }
}
