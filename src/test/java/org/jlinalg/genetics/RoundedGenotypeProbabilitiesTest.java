/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoundedGenotypeProbabilitiesTest {
    private static void contains(RoundedGenotypeProbabilities.Range r,double x) {
        assertTrue(r.lower()<=x&&x<=r.upper(),()->r+" omits "+x);
    }
    @Test void sumConstraintTightensDosageWithoutRenormalizing() {
        var b=RoundedGenotypeProbabilities.bounds("0.125","0.250","0.625",3);
        assertEquals(1.499,b.allele1Dosage().lower(),Math.ulp(1.499));
        assertEquals(1.501,b.allele1Dosage().upper(),Math.ulp(1.501));
        contains(b.posteriorVariance(),.5);
        var rounded=RoundedGenotypeProbabilities.bounds("0.333","0.333","0.333",3);
        contains(rounded.allele1Dosage(),1);contains(rounded.posteriorVariance(),2.0/3);
    }
    @Test void edgeInteriorVarianceMaximumIsIncluded() {
        var b=RoundedGenotypeProbabilities.bounds("0.5","0.0","0.5",1);
        assertEquals(1,b.posteriorVariance().upper());assertEquals(.9475,b.posteriorVariance().lower(),2e-16);
        contains(b.allele1Dosage(),1);contains(b.maximumProbability(),.5);
    }
    @Test void certaintyAccountsForProbabilityCaps() {
        var b=RoundedGenotypeProbabilities.bounds("0.0","0.5","0.5",1);
        assertEquals(.475,b.maximumProbability().lower(),1e-16);assertEquals(.55,b.maximumProbability().upper(),1e-16);
    }
    @Test void impossibleMassMissingCodesAndInvalidTokensAreNotRepaired() {
        for(String[] p:new String[][]{{"0","0","0"},{".334",".334",".334"},{"NaN","0","1"},{"-.001","0","1"},{"1.001","0","0"},{".1234",".2",".6766"}})
            assertThrows(IllegalArgumentException.class,()->RoundedGenotypeProbabilities.bounds(p[0],p[1],p[2],3));
        assertThrows(IllegalArgumentException.class,()->RoundedGenotypeProbabilities.bounds("0","0","1",16));
    }
    @Test void zeroAndOneAreBoundsRatherThanInventedExactPosteriors() {
        var b=RoundedGenotypeProbabilities.bounds("1","0","0",3);
        assertEquals(0,b.allele1Dosage().lower());assertEquals(.001,b.allele1Dosage().upper(),1e-18);
        assertEquals(0,b.posteriorVariance().lower());contains(b.posteriorVariance(),.001999);
    }
    @Test void thousandsOfIndependentExactDistributionsRemainEnclosed() {
        var random=new Random(831);
        for(int i=0;i<3000;i++) {
            int a=random.nextInt(10001),b=random.nextInt(10001-a),c=10000-a-b;
            BigDecimal p0=BigDecimal.valueOf(a,4),p1=BigDecimal.valueOf(b,4),p2=BigDecimal.valueOf(c,4);
            var bounds=RoundedGenotypeProbabilities.bounds(p0.setScale(3,RoundingMode.HALF_EVEN).toPlainString(),p1.setScale(3,RoundingMode.HALF_EVEN).toPlainString(),p2.setScale(3,RoundingMode.HALF_EVEN).toPlainString(),3);
            BigDecimal dose=p1.add(p2.multiply(BigDecimal.valueOf(2)));
            BigDecimal variance=p1.add(p2.multiply(BigDecimal.valueOf(4))).subtract(dose.multiply(dose));
            contains(bounds.allele1Dosage(),dose.doubleValue());contains(bounds.posteriorVariance(),variance.doubleValue());contains(bounds.maximumProbability(),p0.max(p1).max(p2).doubleValue());
        }
    }
    @Test void subunitZeroCensoringRequiresADifferentExplicitContract() {
        assertThrows(IllegalArgumentException.class,()->RoundedGenotypeProbabilities.bounds(".998","0","0",3));
        var b=RoundedGenotypeProbabilities.bounds(".998","0","0",3,RoundedGenotypeProbabilities.Quantization.NEAREST_WITH_SUBUNIT_ZERO_CENSORING);
        contains(b.allele1Dosage(),.0027);contains(b.posteriorVariance(),.0045-.0027*.0027);
        assertThrows(IllegalArgumentException.class,()->RoundedGenotypeProbabilities.bounds(".900","0","0",3,RoundedGenotypeProbabilities.Quantization.NEAREST_WITH_SUBUNIT_ZERO_CENSORING));
    }
    @Test void censoredNonzeroMinimumIsOneOutputUnit() {
        var b=RoundedGenotypeProbabilities.bounds(".001",".499",".500",3,RoundedGenotypeProbabilities.Quantization.NEAREST_WITH_SUBUNIT_ZERO_CENSORING);
        assertEquals(.001,b.probabilities().get(0).lower(),1e-18);
        assertThrows(IllegalArgumentException.class,()->RoundedGenotypeProbabilities.bounds("0","0","1",3,null));
    }
    @Test void independentCensoredRoundedDistributionsRemainEnclosed() {
        var random=new Random(5401);var unit=new BigDecimal(".001");
        for(int i=0;i<3000;i++) {
            int a=random.nextInt(10001),b=random.nextInt(10001-a),c=10000-a-b;
            BigDecimal[] p={BigDecimal.valueOf(a,4),BigDecimal.valueOf(b,4),BigDecimal.valueOf(c,4)};String[] token=new String[3];
            for(int j=0;j<3;j++)token[j]=p[j].compareTo(unit)<0?"0":p[j].setScale(3,RoundingMode.HALF_EVEN).toPlainString();
            var result=RoundedGenotypeProbabilities.bounds(token[0],token[1],token[2],3,RoundedGenotypeProbabilities.Quantization.NEAREST_WITH_SUBUNIT_ZERO_CENSORING);
            var dose=p[1].add(p[2].multiply(BigDecimal.valueOf(2)));var variance=p[1].add(p[2].multiply(BigDecimal.valueOf(4))).subtract(dose.multiply(dose));
            contains(result.allele1Dosage(),dose.doubleValue());contains(result.posteriorVariance(),variance.doubleValue());contains(result.maximumProbability(),p[0].max(p[1]).max(p[2]).doubleValue());
        }
    }
}
