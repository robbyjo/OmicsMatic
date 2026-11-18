/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DunnettReferenceTest {
    private static List<String[]> rows(String file)throws IOException {
        try(var in=DunnettReferenceTest.class.getResourceAsStream("/org/jlinalg/stats/dunnett-fit/"+file)) {
            assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).map(s->s.split("\t",-1)).toList();
        }
    }
    private static int integer(String x){return Integer.parseInt(x);}
    private static double number(String x){return Double.parseDouble(x);}
    private static void close(String expected,double actual){double value=number(expected);assertEquals(value,actual,2e-11*Math.max(Math.abs(value),Math.abs(actual))+64*Math.ulp(value));}
    @Test void matchesSixtyFourOriginalBaseRLmFitsAcrossScalesAndUnbalancedDesigns()throws Exception {
        var population=new TreeMap<Integer,TreeMap<Integer,List<Double>>>();var controls=new HashMap<Integer,Integer>();
        for(String[] row:rows("inputs.tsv")){int id=integer(row[0]);controls.put(id,integer(row[1]));population.computeIfAbsent(id,k->new TreeMap<>()).computeIfAbsent(integer(row[2]),k->new ArrayList<>()).add(number(row[3]));}
        var results=new TreeMap<Integer,DunnettModel.Result>();
        for(var entry:population.entrySet()){double[][] values=entry.getValue().values().stream().map(v->v.stream().mapToDouble(Double::doubleValue).toArray()).toArray(double[][]::new);results.put(entry.getKey(),DunnettModel.fit(values,controls.get(entry.getKey())));}
        assertEquals(64,results.size());int contrasts=0;
        for(String[] row:rows("expected.tsv")) {
            var result=results.get(integer(row[0]));int treatment=integer(row[1]);var actual=result.contrasts().stream().filter(c->c.treatment()==treatment).findFirst().orElseThrow();
            assertEquals(integer(row[2]),result.control());assertEquals(integer(row[3]),result.residualDegrees());close(row[4],result.residualSd());close(row[5],actual.difference());close(row[6],actual.standardError());close(row[7],actual.t());close(row[8],actual.unadjustedP());contrasts++;
        }
        assertTrue(contrasts>250);
        for(String[] row:rows("correlation.tsv"))close(row[3],results.get(integer(row[0])).correlation()[integer(row[1])][integer(row[2])]);
    }
}
