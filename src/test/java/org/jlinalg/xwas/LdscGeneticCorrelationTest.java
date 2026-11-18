/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LdscGeneticCorrelationTest {
    private static List<String[]> rows(String name)throws IOException {
        try(var in=LdscGeneticCorrelationTest.class.getResourceAsStream("/org/jlinalg/xwas/ldsc-rg/"+name)){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).map(x->x.split("\t",-1)).toList();}
    }
    private static double n(String x){return Double.parseDouble(x);}
    private static Double opt(String x){return x.equals("None")?null:n(x);}
    private static int i(String x){return Integer.parseInt(x);}
    @Test void matchesOriginalRgHsqAndGencovAcrossAllDeclaredSourceStatuses()throws Exception {
        var input=new LinkedHashMap<String,List<double[]>>();for(String[] row:rows("inputs.tsv"))input.computeIfAbsent(row[0],k->new ArrayList<>()).add(Arrays.stream(row).skip(2).mapToDouble(Double::parseDouble).toArray());
        var results=new LinkedHashMap<String,LdscGeneticCorrelation.Result>();
        for(String[] row:rows("models.tsv")) {
            var values=input.get(row[0]);double[][] x=new double[6][values.size()];for(int k=0;k<values.size();k++)for(int j=0;j<6;j++)x[j][k]=values.get(k)[j];
            results.put(row[0],LdscGeneticCorrelation.fit(x[0],x[1],x[2],x[3],x[4],x[5],n(row[1]),i(row[2]),opt(row[3]),opt(row[4]),opt(row[5]),opt(row[6])));
        }
        assertEquals(72,results.size());int fields=0;
        for(String[] row:rows("expected.tsv")) {
            var result=results.get(row[0]);int component=i(row[2]),a=i(row[3]),b=i(row[4]);Double actual;
            if(component==3) {
                actual=switch(row[1]){case "rg_rg_ratio"->result.correlation();case "rg_rg_se"->result.correlationStandardError();case "rg_rg_jknife"->result.biasCorrectedCorrelation();case "rg_z"->result.correlationTest()==null?null:result.correlationTest().z();case "rg_p"->result.correlationTest()==null?null:result.correlationTest().p();case "rg__negative_hsq"->result.correlationStatus().equals("NONPOSITIVE_HERITABILITY")?1.:null;default->throw new AssertionError(row[1]);};
                if(row[1].equals("rg__negative_hsq")&&row[5].equals("True")){assertEquals(1.,actual);continue;}
            } else if(component<2) {
                var h=component==0?result.trait1():result.trait2();actual=switch(row[1]){
                    case "total"->h.h2();case "se"->h.h2StandardError();case "coefficient"->h.coefficient();case "coefficient_se"->h.coefficientStandardError();case "intercept"->h.intercept();case "intercept_se"->h.interceptStandardError();case "mean_chi"->h.meanChiSquare();case "lambda_gc"->h.lambdaGc();case "delete"->h.deleteValues()[a][b];case "weight"->h.finalWeights()[a];case "boundary"->(double)h.fullSeparators()[a];case "step1_boundary"->(double)h.step1Separators()[a];default->throw new AssertionError(row[1]);};
            } else {
                var g=result.geneticCovariance();actual=switch(row[1]){
                    case "total"->g.covariance();case "se"->g.standardError();case "coefficient"->g.coefficient();case "coefficient_se"->g.coefficientStandardError();case "intercept"->g.intercept();case "intercept_se"->g.interceptStandardError();case "mean_chi","lambda_gc"->null;case "mean_product"->g.meanZProduct();case "p"->g.normalTest().p();case "z"->g.normalTest().z();case "delete"->g.deleteValues()[a][b];case "weight"->g.finalWeights()[a];case "boundary"->(double)g.fullSeparators()[a];case "step1_boundary"->(double)g.step1Separators()[a];case "gcov_weight_estimate"->g.weightUpdates().get(a).estimatedCovariance();case "gcov_weight_intercept"->g.weightUpdates().get(a).intercept();default->throw new AssertionError(row[1]);};
            }
            if(Set.of("None","NA").contains(row[5]))assertNull(actual,row[0]+" "+row[1]);
            else if(row[5].equals("NaN")){assertNotNull(actual);assertTrue(Double.isNaN(actual),row[0]+" "+row[1]);}
            else {double expected=n(row[5]);assertNotNull(actual);assertEquals(expected,actual,3e-9*Math.max(Math.abs(expected),Math.abs(actual))+3e-13,row[0]+" "+row[1]+" "+component+":"+a+","+b);}
            fields++;
        }
        assertTrue(fields>50000);
        assertTrue(results.values().stream().anyMatch(r->r.correlationStatus().equals("NONPOSITIVE_HERITABILITY")));
        assertTrue(results.values().stream().anyMatch(r->r.correlationStatus().equals("NONFINITE_DELETE_UNCERTAINTY")));
        assertTrue(results.values().stream().anyMatch(r->r.finiteCorrelationInference()&&r.negativeTrait1Deletes()>0&&r.negativeTrait2Deletes()>0));
    }
    @Test void sourceZeroStandardErrorConventionRetainsPositiveInfinity() {
        var test=LdscGeneticCorrelation.normalTest(-1,0);assertEquals(Double.POSITIVE_INFINITY,test.z());assertEquals(0,test.p());assertEquals(Double.NEGATIVE_INFINITY,test.logP());
    }
    @Test void rejectsIncompletePopulationAndFixedTwoStepConflict() {
        double[] z={1,2,3,4},ld={1,2,3,4},w={1,2,3,4},n={100,100,100,100};
        assertThrows(IllegalArgumentException.class,()->LdscGeneticCorrelation.fit(z,z,ld,w,n,n,1000,2,1.,null,null,36.));
        assertThrows(IllegalArgumentException.class,()->LdscGeneticCorrelation.fit(z,new double[]{1},ld,w,n,n,1000,2,null,null,null,null));
        assertThrows(ArithmeticException.class,()->LdscGeneticCorrelation.fit(new double[]{1,2,Double.NaN,4},z,ld,w,n,n,1000,2,null,null,null,null));
    }
}
