/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScviLikelihoodTest {
    @Test void freshOriginalFloat64SourceKernelFixturesMatch() throws Exception {
        var stream = getClass().getResourceAsStream("scvi-default-kernels.tsv");assertNotNull(stream);
        int rows=0;
        try (var in = new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
            assertEquals("source_version\tcount\tmu\ttheta\tpi\toriginal_float64_nb\toriginal_float64_zinb",in.readLine());
            String line;
            while ((line=in.readLine())!=null) {
                String[] f=line.split("\t",-1);assertEquals(7,f.length);
                long count=Long.parseLong(f[1]);double mu=Double.parseDouble(f[2]),theta=Double.parseDouble(f[3]),pi=Double.parseDouble(f[4]);
                assertEquals(Double.parseDouble(f[5]),ScviLikelihood.logNegativeBinomial(count,mu,theta),2e-10,line);
                assertEquals(Double.parseDouble(f[6]),ScviLikelihood.logZeroInflatedNegativeBinomial(count,mu,theta,pi),2e-10,line);rows++;
            }
        }
        assertEquals(192,rows);
    }
    @Test void additiveEpsilonPseudomassIsNotReplacedByExactDistributionZeros() {
        assertTrue(Double.isFinite(ScviLikelihood.logNegativeBinomial(1,0,1)));
        assertTrue(Double.isFinite(ScviLikelihood.logZeroInflatedNegativeBinomial(1,0,1,0)));
        assertEquals(0,ScviLikelihood.logNegativeBinomial(0,0,1));
        assertEquals(0,ScviLikelihood.logZeroInflatedNegativeBinomial(0,0,1,1000));
    }
    @Test void invalidCountsAndNonfiniteOrOutOfDomainParametersFailExplicitly() {
        for(long count:new long[]{-1,9_007_199_254_740_992L,Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,()->ScviLikelihood.logNegativeBinomial(count,1,1));
            assertThrows(IllegalArgumentException.class,()->ScviLikelihood.logZeroInflatedNegativeBinomial(count,1,1,0));
        }
        for(double mu:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class,()->ScviLikelihood.logNegativeBinomial(0,mu,1));
        for(double theta:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class,()->ScviLikelihood.logNegativeBinomial(0,1,theta));
        for(double pi:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class,()->ScviLikelihood.logZeroInflatedNegativeBinomial(0,1,1,pi));
    }
    @Test void finiteInputsDoNotHideOverflowOrMaskedNonfiniteGammaBranches() {
        assertThrows(IllegalArgumentException.class,()->ScviLikelihood.logNegativeBinomial(0,Double.MAX_VALUE,Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->ScviLikelihood.logZeroInflatedNegativeBinomial(0,0,1e308,0));
    }
    @Test void largeCountCancellationIsReportedRatherThanCalledAccurateSourceParity() {
        var typical=ScviLikelihood.auditNegativeBinomial(17,3.5,5);
        assertFalse(typical.cancellationLimited());assertEquals(ScviLikelihood.logNegativeBinomial(17,3.5,5),typical.logLikelihood());
        var extreme=ScviLikelihood.auditZeroInflatedNegativeBinomial(9_007_199_254_740_991L,1e9,.1,21);
        assertTrue(extreme.cancellationLimited());assertTrue(extreme.empiricalRoundingEnvelope()>1e-6);
    }
}
