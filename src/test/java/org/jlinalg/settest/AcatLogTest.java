/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AcatLogTest {
    @Test void agreesWithExistingProbabilityImplementationForRepresentableTails(){for(double[] p:new double[][]{{.01,.2,.6},{1e-20,.8,.1},{.1,.9},{.99,.999,.8},{.5}}){double[] logs=java.util.Arrays.stream(p).map(Math::log).toArray();assertEquals(Acat.combine(p).pValue(),Acat.combineLog(logs).pValue(),2e-14);}}
    @Test void oneComponentIsTheIdentityEvenWhenItsPValueUnderflows(){for(double logP:new double[]{-.01,-.5,-10,-1000,-1e6})assertEquals(logP,Acat.combineLog(new double[]{logP}).log10PValue()*Math.log(10),Math.max(1e-12,Math.abs(logP)*1e-14));}
    @Test void extremeEqualAndUnequalTailsStayFinite(){assertEquals(-1000,Acat.combineLog(new double[]{-1000,-1000}).log10PValue()*Math.log(10),1e-12);assertEquals(-1002+Math.log(2)-Math.log1p(Math.exp(-2)),Acat.combineLog(new double[]{-1000,-1002}).log10PValue()*Math.log(10),2e-12);}
    @Test void invalidLogProbabilitiesAreRejected(){assertThrows(IllegalArgumentException.class,()->Acat.combineLog(new double[0]));for(double x:new double[]{1,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->Acat.combineLog(new double[]{x}));}
}
