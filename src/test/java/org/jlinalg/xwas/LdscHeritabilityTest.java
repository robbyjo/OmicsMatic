/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LdscHeritabilityTest {
    private static List<String[]> rows(String name)throws IOException {
        try(var in=LdscHeritabilityTest.class.getResourceAsStream("/org/jlinalg/xwas/ldsc-hsq/"+name)){
            assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).map(s->s.split("\t",-1)).toList();
        }
    }
    private static double n(String text){return Double.parseDouble(text);}
    private static int i(String text){return Integer.parseInt(text);}
    private static Double optional(String text){return text.equals("None")?null:n(text);}
    @Test void unchangedOriginalHsqOneStepFixedAndTwoStepReferences()throws Exception {
        var inputs=new LinkedHashMap<String,List<double[]>>();
        for(String[] row:rows("inputs.tsv"))inputs.computeIfAbsent(row[0],k->new ArrayList<>()).add(new double[]{n(row[2]),n(row[3]),n(row[4]),n(row[5])});
        var results=new HashMap<String,LdscHeritability.Result>();
        for(String[] row:rows("models.tsv")) {
            var values=inputs.get(row[0]);double[][] x=new double[4][values.size()];
            for(int k=0;k<values.size();k++)for(int j=0;j<4;j++)x[j][k]=values.get(k)[j];
            results.put(row[0],LdscHeritability.fit(x[0],x[1],x[2],x[3],n(row[1]),i(row[2]),optional(row[3]),optional(row[4])));
        }
        assertEquals(72,results.size());int checked=0;
        for(String[] row:rows("expected.tsv")) {
            var r=results.get(row[0]);int a=i(row[2]),b=i(row[3]);
            Double actual=switch(row[1]){
                case "h2"->r.h2();case "h2_se"->r.h2StandardError();case "intercept"->r.intercept();case "intercept_se"->r.interceptStandardError();
                case "mean_chi"->r.meanChiSquare();case "lambda_gc"->r.lambdaGc();case "ratio"->r.ratio();case "ratio_se"->r.ratioStandardError();
                case "coefficient"->r.coefficient();case "coefficient_se"->r.coefficientStandardError();case "delete"->r.deleteValues()[a][b];
                case "covariance"->r.covariance()[a][b];case "weight"->r.finalWeights()[a];case "separator"->(double)r.fullSeparators()[a];
                case "step1_separator"->(double)r.step1Separators()[a];case "weight_h2"->r.weightUpdates().get(a).estimatedH2();case "weight_intercept"->r.weightUpdates().get(a).intercept();
                default->throw new AssertionError("Unknown reference field");};
            if(row[4].equals("NA")||row[4].equals("None"))assertNull(actual,row[0]+" "+row[1]);
            else {double expected=n(row[4]);assertNotNull(actual);assertEquals(expected,actual,2e-9*Math.max(Math.abs(expected),Math.abs(actual))+2e-13,row[0]+" "+row[1]+" "+a+","+b);}
            checked++;
        }
        assertTrue(checked>10000);
    }
    @Test void rejectsIncompleteAndConflictingInputs() {
        double[] y={1,2,3,4},ld={1,2,3,4},w={1,1,1,1},n={100,100,100,100};
        assertThrows(IllegalArgumentException.class,()->LdscHeritability.fit(y,ld,w,n,1000,2,1.0,30.0));
        assertThrows(IllegalArgumentException.class,()->LdscHeritability.fit(y,ld,w,n,1000,5,null,null));
        assertThrows(IllegalArgumentException.class,()->LdscHeritability.fit(y,ld,w,n,1000,2,null,.5));
        assertThrows(IllegalArgumentException.class,()->LdscHeritability.fit(new double[]{1,2,Double.NaN,4},ld,w,n,1000,2,null,null));
        assertThrows(ArithmeticException.class,()->LdscHeritability.fit(y,new double[]{1,1,1,1},w,n,1000,2,null,null));
    }
    @Test void noFinalHeritabilityClampingAndCopiesAreIndependent() {
        var r=LdscHeritability.fit(new double[]{1.8,1.65,1.5,1.42,1.31,1.2},new double[]{1,2,3,4,5,6},new double[]{0,1,2,3,4,5},new double[]{100,100,100,100,100,100},1000,3,null,null);
        assertTrue(r.h2()<0);assertTrue(r.weightUpdates().stream().allMatch(w->w.weightingH2()>=0&&w.weightingH2()<=1));
        double[] weights=r.finalWeights();weights[0]=-1;assertTrue(r.finalWeights()[0]>0);
        int[] boundaries=r.fullSeparators();boundaries[0]=99;assertEquals(0,r.fullSeparators()[0]);
        assertThrows(UnsupportedOperationException.class,()->r.weightUpdates().clear());
    }
}
