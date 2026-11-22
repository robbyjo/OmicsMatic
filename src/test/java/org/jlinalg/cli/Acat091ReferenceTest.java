/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jlinalg.settest.Acat091;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Real unchanged source calls,not a reimplementation of the ACAT formula. */
final class Acat091ReferenceTest {
    @TestFactory Stream<DynamicTest> originalCheckedMatrixCalls()throws IOException {
        String name="/org/jlinalg/settest/acat091/original-cases.json";Object parsed;
        try(var input=getClass().getResourceAsStream(name)){assertNotNull(input);parsed=SimpleJson.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        List<?> cases=(List<?>)parsed;assertEquals(274,cases.size());var tests=new ArrayList<DynamicTest>();
        for(Object item:cases){Map<?,?> c=(Map<?,?>)item;tests.add(DynamicTest.dynamicTest(c.get("id").toString(),()->check(c)));}
        return tests.stream();
    }
    private static void check(Map<?,?> c) {
        var original=(Map<?,?>)c.get("original");var p=matrix(c.get("p"));var w=c.get("w")==null?null:matrix(c.get("w"));
        boolean returned=(Boolean)original.get("returned");List<?> expectedWarnings=(List<?>)original.get("warnings"),values=(List<?>)original.get("values");
        Acat091.Result result;
        try{result=Acat091.combineMatrix(p,w);}
        catch(Acat091.Failure error){assertEquals(expectedWarnings,error.sourceWarnings());assertTrue(!returned||values.stream().allMatch(v->v.equals("NaN")||v.equals("NA")),"Unexpected refusal of a finite original return");return;}
        assertTrue(returned,"Original errors cannot admit native results");assertEquals(expectedWarnings,result.sourceWarnings());assertEquals(values.size(),result.columns().size());
        for(int i=0;i<values.size();i++) {
            double expected=number(values.get(i)),actual=result.columns().get(i).rawPValue();
            if(Double.isNaN(expected)){assertTrue(Double.isNaN(actual));assertFalse(result.columns().get(i).admitted());assertThrows(ArithmeticException.class,result.columns().get(i)::pValue);}
            else if(expected==0)assertEquals(0,actual);
            else {assertTrue(actual>0,"A positive source tail must not become zero");double tolerance=Math.max(2*Math.ulp(expected),Math.abs(expected)*5e-13);assertEquals(expected,actual,tolerance);}
        }
        if(List.of("zero-weight-zero","huge-weight-sum","all-smallest").contains(c.get("id")))assertFalse(result.admitted());
    }
    private static double[][] matrix(Object input) {
        List<?> rows=(List<?>)input;double[][] result=new double[rows.size()][];
        for(int i=0;i<result.length;i++){List<?> row=(List<?>)rows.get(i);result[i]=new double[row.size()];for(int j=0;j<result[i].length;j++)result[i][j]=number(row.get(j));}
        return result;
    }
    private static double number(Object value){if(value instanceof Number n)return n.doubleValue();return switch(value.toString()){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value.toString());};}
}
