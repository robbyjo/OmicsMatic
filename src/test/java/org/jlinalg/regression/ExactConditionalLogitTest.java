/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.regression;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.stats.Alternative;
import org.junit.jupiter.api.Test;

class ExactConditionalLogitTest {
    private static final Path FIXTURES=Path.of("src/test/resources/exact-conditional-logit");
    @Test void allFiniteScoreMassesAndTailsMatchIndependentRationalEnumeration()throws Exception {
        var reference=Files.readAllLines(FIXTURES.resolve("reference.tsv"));var support=Files.readAllLines(FIXTURES.resolve("support.tsv"));assertEquals(8,reference.size());
        for(String line:reference.subList(1,reference.size())) {
            String[] expected=line.split("\t");var input=Files.readAllLines(FIXTURES.resolve(expected[0]+".tsv"));int n=input.size()-1;double[] y=new double[n],g=new double[n],o=new double[n];int[] s=new int[n];
            for(int i=0;i<n;i++){String[] row=input.get(i+1).split("\t");y[i]=Double.parseDouble(row[0]);g[i]=row[1].equals("NA")?Double.NaN:Double.parseDouble(row[1]);s[i]=Integer.parseInt(row[2]);o[i]=Double.parseDouble(row[3]);}
            var result=ExactConditionalLogit.test(y,g,s,o,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());assertTrue(result.estimable(),expected[0]+" "+result.reason());
            assertEquals(Long.parseLong(expected[1]),result.distribution().observedScore());near(expected[2],result.pValue());near(expected[3],result.logPValue());near(expected[6],result.nullMean());near(expected[7],result.nullVariance());
            assertEquals(Integer.parseInt(expected[8]),result.observations());assertEquals(Integer.parseInt(expected[9]),result.cases());assertEquals(Integer.parseInt(expected[10]),result.strata());assertEquals(Integer.parseInt(expected[11]),result.informativeStrata());
            near(expected[4],ExactConditionalLogit.test(y,g,s,o,Alternative.LESS,ExactConditionalLogit.Options.defaults()).pValue());near(expected[5],ExactConditionalLogit.test(y,g,s,o,Alternative.GREATER,ExactConditionalLogit.Options.defaults()).pValue());
            double[] logs=result.distribution().logProbabilities();int checked=0;
            for(String entry:support.subList(1,support.size())){String[] cells=entry.split("\t");if(!cells[0].equals(expected[0]))continue;int index=(int)(Long.parseLong(cells[1])-result.distribution().minimumScore());near(cells[3],logs[index]);checked++;}
            int finite=0;for(double value:logs)if(Double.isFinite(value))finite++;assertEquals(checked,finite);
        }
    }
    @Test void binaryCaseAgreesWithIndependentRFisherReference() {
        double[] y=new double[24],g=new double[24];for(int i=0;i<10;i++)y[i]=1;g[0]=1;for(int i=10;i<21;i++)g[i]=1;
        var result=ExactConditionalLogit.test(y,g,null,null,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());assertTrue(result.estimable());assertEquals(.0027594561852200836,result.pValue(),1e-14);
    }
    @Test void underflowingTailKeepsARepresentableNaturalLogP() {
        double[] y=new double[2000],g=new double[2000];for(int i=0;i<1000;i++)y[i]=g[i]=1;
        var result=ExactConditionalLogit.test(y,g,null,null,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());assertTrue(result.estimable());assertEquals(0,result.pValue());assertTrue(result.logPValue() < -1000);assertTrue(Double.isFinite(result.logPValue()));
        double logChoose=0;for(int i=1;i<=1000;i++)logChoose+=Math.log(1000.0+i)-Math.log(i);assertEquals(Math.log(2)-logChoose,result.logPValue(),1e-9);
    }
    @Test void budgetsAndNoninformativeMarginsDoNotProducePassingPValues() {
        double[] y={0,1,0,1},g={0,1,2,0};
        for(var options:new ExactConditionalLogit.Options[]{new ExactConditionalLogit.Options(1,10000,1e-7),new ExactConditionalLogit.Options(1000,1,1e-7)}) {
            var result=ExactConditionalLogit.test(y,g,null,null,Alternative.TWO_SIDED,options);assertEquals("budget_exceeded",result.status());assertTrue(Double.isNaN(result.pValue()));assertNull(result.distribution());
        }
        var pure=ExactConditionalLogit.test(y,g,new int[]{0,1,0,1},null,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());assertEquals("no_information",pure.status());assertTrue(Double.isNaN(pure.pValue()));
        assertThrows(IllegalArgumentException.class,()->ExactConditionalLogit.test(y,new double[]{0,.999999999,2,0},null,null,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults()));
    }
    @Test void alleleAndOutcomeRecodingKeepTwoSidedProbabilitiesAndReverseScoreDirection() {
        double[] y={0,1,0,1,1,0,0,1},g={0,0,1,1,2,2,0,2},o={.1,-.3,.4,0,.2,-.6,0,.8},flip=new double[8],complement=new double[8],negative=new double[8];
        for(int i=0;i<8;i++){flip[i]=2-g[i];complement[i]=1-y[i];negative[i]=-o[i];}
        var a=ExactConditionalLogit.test(y,g,null,o,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());var b=ExactConditionalLogit.test(y,flip,null,o,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());var c=ExactConditionalLogit.test(complement,g,null,negative,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());
        assertEquals(a.pValue(),b.pValue(),1e-12);assertEquals(a.pValue(),c.pValue(),1e-12);assertEquals(a.centeredScore(),-b.centeredScore(),1e-12);assertEquals(a.centeredScore(),-c.centeredScore(),1e-12);
    }
    @Test void extremeKnownOffsetsFailExplicitlyWhenFiniteMassCannotBeNormalized() {
        double[] y={1,1,1,1,0,0,0,0},g={0,1,0,1,0,1,2,2},o={0,-1e300,-1e300,-1e300,-1e300,-1e300,-1e300,-1e300};
        var result=ExactConditionalLogit.test(y,g,null,o,Alternative.TWO_SIDED,ExactConditionalLogit.Options.defaults());assertEquals("numerical_unavailable",result.status());assertTrue(Double.isNaN(result.pValue()));
    }
    private static void near(String expected,double actual){double value=Double.parseDouble(expected);assertEquals(value,actual,Math.max(1e-12,Math.abs(value)*1e-11));}
}
