/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LdPred2Test {
    private static LdCorrelation two(double r) {
        return LdCorrelation.ofCsc(2, new int[]{0,2,4}, new int[]{0,1,0,1}, new double[]{1,r,r,1});
    }
    private static LdCorrelation identity(int n) {
        int[] p=new int[n+1],r=new int[n];double[] v=new double[n];
        for(int i=0;i<n;i++){p[i]=i;r[i]=i;v[i]=1;}p[n]=n;
        return LdCorrelation.ofCsc(n,p,r,v);
    }
    private static final LdPred2Random ZERO_NORMAL = new LdPred2Random() {
        public double uniform(){return 0.5;}
        public double standardNormal(){return 0;}
    };

    @Test void infinitesimalMatchesIndependentTwoByTwoInverseWithUnequalSampleSizes() {
        double[] beta={0.2,-0.15},se={0.07,0.04},n={900,1400};double h2=0.35,r=-0.3;
        double s0=Math.sqrt(n[0]*se[0]*se[0]+beta[0]*beta[0]);
        double s1=Math.sqrt(n[1]*se[1]*se[1]+beta[1]*beta[1]);
        double a=1+2/(h2*n[0]),d=1+2/(h2*n[1]),det=a*d-r*r;
        double[] expected={(d*beta[0]/s0-r*beta[1]/s1)/det*s0,(a*beta[1]/s1-r*beta[0]/s0)/det*s1};
        var result=LdPred2.infinitesimal(two(r),beta,se,n,h2,LdPred2.SolveOptions.defaults());
        assertArrayEquals(expected,result.alleleEffects(),1e-13);
        assertTrue(result.relativeResidual()<=1e-10);
        assertArrayEquals(new double[]{0.2,-0.15},beta);
    }

    @Test void diagonalLdHasAnalyticShrinkageAndHeritabilityMayExceedOne() {
        double[] beta={0.1,-0.2,0},se={0.01,0.02,0.03},n={300,700,400};
        var result=LdPred2.infinitesimal(identity(3),beta,se,n,1.4,LdPred2.SolveOptions.defaults());
        for(int i=0;i<3;i++)assertEquals(beta[i]/(1+3/(1.4*n[i])),result.alleleEffects()[i],1e-14);
        double[] effects=result.alleleEffects();effects[0]=100;assertNotEquals(100,result.alleleEffects()[0]);
    }

    @Test void zeroInfinitesimalRightHandSideRequiresNoIterations() {
        var result=LdPred2.infinitesimal(identity(2),new double[2],new double[]{1,1},new double[]{100,200},0.3,LdPred2.SolveOptions.defaults());
        assertArrayEquals(new double[2],result.alleleEffects());assertEquals(0,result.iterations());
    }

    @Test void insufficientIterationsNeverReturnAnApproximateAcceptedFit() {
        assertThrows(ArithmeticException.class,()->LdPred2.infinitesimal(two(0.7),new double[]{0.1,-0.4},new double[]{0.1,0.2},new double[]{100,500},0.3,new LdPred2.SolveOptions(1e-14,1)));
    }

    @Test void invalidSummaryStatisticsAndOverflowAreRejected() {
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->LdPred2.infinitesimal(identity(1),new double[]{0.2},new double[]{bad},new double[]{100},0.1,LdPred2.SolveOptions.defaults()));
            assertThrows(IllegalArgumentException.class,()->LdPred2.infinitesimal(identity(1),new double[]{0.2},new double[]{0.1},new double[]{bad},0.1,LdPred2.SolveOptions.defaults()));
        }
        assertThrows(IllegalArgumentException.class,()->LdPred2.infinitesimal(identity(1),new double[]{1},new double[]{Double.MAX_VALUE},new double[]{100},0.1,LdPred2.SolveOptions.defaults()));
    }

    @Test void invalidLdIsNotSilentlySymmetrizedOrDeduplicated() {
        assertThrows(IllegalArgumentException.class,()->LdCorrelation.ofCsc(2,new int[]{0,2,3},new int[]{0,1,1},new double[]{1,0.1,1}));
        assertThrows(IllegalArgumentException.class,()->LdCorrelation.ofCsc(1,new int[]{0,1},new int[]{0},new double[]{0.99}));
        assertThrows(IllegalArgumentException.class,()->LdCorrelation.ofCsc(1,new int[]{0,2},new int[]{0,0},new double[]{1,1}));
        assertThrows(IllegalArgumentException.class,()->LdCorrelation.ofCsc(2,new int[]{0,3,2},new int[]{0,1},new double[]{1,1}));
        assertThrows(IllegalArgumentException.class,()->two(Double.NaN));
    }

    @Test void deterministicConditionalMeansConvergeToIndependentInfinitesimalSolution() {
        double[] beta={0.2,-0.1},se={0.01,0.02},n={1000,1500};
        var exact=LdPred2.infinitesimal(two(0.25),beta,se,n,0.3,LdPred2.SolveOptions.defaults());
        var gibbs=LdPred2.gibbs(two(0.25),beta,se,n,new LdPred2.GridParameters(0.3,1,false,150,100),ZERO_NORMAL,null);
        assertArrayEquals(exact.alleleEffects(),gibbs.alleleEffects(),1e-12);assertEquals(250,gibbs.completedIterations());
    }

    @Test void sparseBranchCanSetWeakVariantsToZeroWithoutConsumingRandomDraws() {
        LdPred2Random forbidden=new LdPred2Random(){public double uniform(){throw new AssertionError("unexpected uniform");}public double standardNormal(){throw new AssertionError("unexpected normal");}};
        List<double[]> samples=new ArrayList<>();
        var result=LdPred2.gibbs(identity(1),new double[]{0},new double[]{0.1},new double[]{100},new LdPred2.GridParameters(0.2,0.5,true,2,3),forbidden,(k,b)->samples.add(b));
        assertArrayEquals(new double[1],result.alleleEffects());assertEquals(3,samples.size());
    }

    @Test void divergenceThrowsInsteadOfReturningAcceptedNonfiniteEffects() {
        LdPred2Random noisy=new LdPred2Random(){public double uniform(){return 0;}public double standardNormal(){return 1;}};
        assertThrows(LdPred2.DivergenceException.class,()->LdPred2.gibbs(identity(1),new double[]{0},new double[]{0.1},new double[]{100},new LdPred2.GridParameters(0.2,1,false,0,2),noisy,null));
    }

    @Test void actualRUniformNormalAndMixedStreamsMatchIncludingAllSeedStates() throws Exception {
        record Row(String kind,int index,String value){}
        List<Row> rows=new ArrayList<>();
        try(var stream=getClass().getResourceAsStream("ldpred2-rng.tsv")) {
            assertNotNull(stream);
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                reader.readLine();for(String line;(line=reader.readLine())!=null;) {String[] c=line.split("\t");rows.add(new Row(c[0],Integer.parseInt(c[1]),c[2]));}
            }
        }
        int[] seed=rows.stream().filter(r->r.kind.equals("seed_before")).mapToInt(r->Integer.parseInt(r.value)).toArray();
        LdPred2Random.RStream random=new LdPred2Random.RStream(seed);
        for(Row row:rows) {
            if(row.kind.equals("uniform")||row.kind.equals("mixed_uniform"))assertEquals(Double.parseDouble(row.value),random.uniform(),0.0,row.kind+row.index);
            else if(row.kind.equals("normal")||row.kind.equals("mixed_normal"))assertEquals(Double.parseDouble(row.value),random.standardNormal(),3e-14,row.kind+row.index);
            else if(row.kind.startsWith("seed_after"))assertEquals(Integer.parseInt(row.value),random.state()[row.index],row.kind+row.index);
        }
    }
}
