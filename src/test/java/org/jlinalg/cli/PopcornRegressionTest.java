/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.jlinalg.compute.*;
import org.jlinalg.genetics.PopcornRegression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Expectations captured from unmodified original fit_pg, not a second WLS implementation. */
class PopcornRegressionTest {
    static List<Object[]> originalCases() {
        var cases=new ArrayList<Object[]>();
        for(String name:List.of("variable-N-unequal-M","unit-N-over-M","large-N","negative-cross","out-of-range","negative-one","negative-both","zero-cross","constant-cross","masked-deletions","small-population"))
            for(String version:List.of("1.0","1.1"))cases.add(new Object[]{name,version});
        return cases;
    }
    private Map<?,?> fixture(String name)throws Exception {
        try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/popcorn/"+name+".json")) {
            assertNotNull(in);return object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8)));
        }
    }
    private List<PopcornRegression.Row> rows(Map<?,?> fixture)throws Exception {
        var result=new ArrayList<PopcornRegression.Row>();
        for(Object item:list(fixture.get("rows"))) {var r=list(item);result.add(new PopcornRegression.Row(d(r.get(0)),d(r.get(1)),d(r.get(2)),d(r.get(3)),d(r.get(4)),d(r.get(5)),d(r.get(6))));}
        return result;
    }
    private static double d(Object value){return value instanceof Number n?n.doubleValue():Double.parseDouble((String)value);}
    private void vector(Object expected,double[] actual)throws Exception {
        var e=list(expected);assertEquals(e.size(),actual.length);
        for(int i=0;i<actual.length;i++)close(d(e.get(i)),actual[i]);
    }
    private void matrix(Object expected,double[][] actual)throws Exception {
        var e=list(expected);assertEquals(e.size(),actual.length);for(int i=0;i<actual.length;i++)vector(e.get(i),actual[i]);
    }
    private void close(double expected,double actual) {
        if(!Double.isFinite(expected)){assertEquals(expected,actual);return;}
        assertEquals(expected,actual,Math.max(1e-10,Math.abs(expected)*5e-9));
    }
    @ParameterizedTest(name="original {0} v{1}") @MethodSource("originalCases")
    void originalRegressionAndJackknife(String name,String version)throws Exception {
        var f=fixture(name);var expected=object(object(f.get("versions")).get(version));
        var v=version.equals("1.0")?PopcornRegression.Version.CLI_1_0_3C21C41F:PopcornRegression.Version.CLI_1_1_20A29E8C;
        try(var context=BackendContext.select(BackendPolicy.CPU)) {
            var rows=rows(f);int m=((Number)f.get("M")).intValue();
            if(expected.get("status").equals("ORIGINAL_ERROR")){assertThrows(PopcornRegression.SourceFailure.class,()->PopcornRegression.fit(rows,m,v,true,context.backend(),()->{}));return;}
            var actual=PopcornRegression.fit(rows,m,v,true,context.backend(),()->{});var e=object(expected.get("result"));
            vector(e.get("values"),actual.point().values());vector(e.get("SE"),actual.standardErrors());matrix(e.get("delete_values"),actual.deleteValues());matrix(e.get("source_pseudo_values"),actual.sourcePseudoValues());matrix(e.get("covariance"),actual.covariance());
            var blocks=list(e.get("blocks"));assertEquals(blocks.size(),actual.blocks().size());
            for(int b=0;b<blocks.size();b++){var pair=list(blocks.get(b));assertEquals(((Number)pair.get(0)).intValue(),actual.blocks().get(b).fromInclusive());assertEquals(((Number)pair.get(1)).intValue(),actual.blocks().get(b).toExclusive());}
            for(int j=0;j<3;j++) {
                double se=d(list(e.get("SE")).get(j));
                // Near-zero jackknife error gives unstable huge Z under roundoff.
                // Compare the actual estimates/deletions/covariance/SE above;
                // finite-precision-noise Z is deliberately not an oracle claim.
                if(se>1e-10||!Double.isFinite(se)){close(d(list(e.get("Z")).get(j)),actual.sourceZ()[j]);close(d(list(e.get("P")).get(j)),actual.sourceP()[j]);}
            }
        }
    }
    @Test void sourceBlockBoundariesIncludeRemainders() {
        for(int n:new int[]{7,37,2199,2200,40001,90000}){
            var blocks=PopcornRegression.blocks(n);assertEquals((int)Math.min(200,Math.max(10,n/200.0)),blocks.size());int cursor=0;
            for(var block:blocks){assertEquals(cursor,block.fromInclusive());cursor=block.toExclusive();}assertEquals(n,cursor);
        }
    }
    @Test void cancellationAndInputContracts()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new PopcornRegression.Row(0,1,1,1,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new PopcornRegression.Row(1,1,Double.NaN,1,1,1,1));
        try(var context=BackendContext.select(BackendPolicy.CPU)){
            var rows=rows(fixture("variable-N-unequal-M"));
            assertThrows(IllegalArgumentException.class,()->PopcornRegression.fit(rows,1,PopcornRegression.Version.CLI_1_1_20A29E8C,true,context.backend(),()->{}));
            assertThrows(java.util.concurrent.CancellationException.class,()->PopcornRegression.fit(rows,103,PopcornRegression.Version.CLI_1_1_20A29E8C,true,context.backend(),()->{throw new java.util.concurrent.CancellationException();}));
        }
    }
    @Test void noJackknifeDoesNotInventUncertaintyAndResultsAreImmutable()throws Exception {
        var f=fixture("variable-N-unequal-M");
        try(var context=BackendContext.select(BackendPolicy.CPU)){
            var result=PopcornRegression.fit(rows(f),103,PopcornRegression.Version.CLI_1_1_20A29E8C,false,context.backend(),()->{});
            assertTrue(result.blocks().isEmpty());for(double se:result.standardErrors())assertTrue(Double.isNaN(se));
            double[] p=result.sourceP();p[0]=42;assertTrue(Double.isNaN(result.sourceP()[0]));
        }
    }
}
