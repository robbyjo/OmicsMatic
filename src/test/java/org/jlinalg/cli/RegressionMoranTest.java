/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.jlinalg.spatial.RegressionMoran;
import org.jlinalg.spatial.RegressionMoran.Arc;
import org.jlinalg.spatial.RegressionMoran.Alternative;
import org.junit.jupiter.api.Test;

class RegressionMoranTest {
    static Map<?,?> reference()throws Exception {
        try(var resource=RegressionMoranTest.class.getResourceAsStream("/org/jlinalg/spatial/regression-moran/original-spdep-1.4-1.json.gz")) {
            assertNotNull(resource);
            byte[] raw=resource.readNBytes(838092);assertEquals(838091,raw.length);assertEquals("35b1222ef726ee67331e5786d291efc224033ff5d92ec725bc042584ffb5c871",NextflowFiles.sha(raw));
            try(var gzip=new GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
                return NextflowFiles.object(NextflowParameters.parse(new String(gzip.readNBytes(8*1024*1024),StandardCharsets.UTF_8),8*1024*1024,2_000_000));
            }
        }
    }
    static double number(Object x)throws IOException {while(x instanceof List<?> list){assertEquals(1,list.size());x=list.get(0);}return Monocle133GraphRequest.finite(x);}
    static double[] vector(Object value)throws IOException {var raw=NextflowFiles.list(value);double[] result=new double[raw.size()];for(int i=0;i<result.length;i++)result[i]=number(raw.get(i));return result;}
    static double[][] matrix(Object value)throws IOException {var raw=NextflowFiles.list(value);double[][] result=new double[raw.size()][];for(int i=0;i<result.length;i++)result[i]=vector(raw.get(i));return result;}
    static List<Arc> arcs(Object value)throws IOException {var result=new ArrayList<Arc>();for(Object item:NextflowFiles.list(value)){var row=NextflowFiles.object(item);result.add(new Arc((int)number(row.get("from")),(int)number(row.get("to")),number(row.get("weight"))));}return result;}
    static Alternative alternative(Object value) {return switch(value.toString()){case "greater"->Alternative.GREATER;case "less"->Alternative.LESS;case "two.sided"->Alternative.TWO_SIDED;default->throw new IllegalArgumentException();};}

    @Test void matches108IndependentOriginalSpdepTestsAcross36GraphDesignAlternativeCases()throws Exception {
        var source=reference();assertEquals(Boolean.TRUE,source.get("official_source_body_identical"));int cases=0,genes=0,zeros=0;
        for(Object item:NextflowFiles.list(source.get("cases"))) {
            var c=NextflowFiles.object(item);var prepared=RegressionMoran.prepare(matrix(c.get("design")),arcs(c.get("weights")),(Boolean)c.get("zero_policy"));
            assertEquals(number(c.get("observations")),prepared.observations());assertEquals(number(c.get("rank")),prepared.rank());assertEquals(number(c.get("effective_n")),prepared.effectiveObservations());assertEquals(number(c.get("outgoing_isolates")),prepared.outgoingIsolates());assertEquals(number(c.get("weight_sum")),prepared.weightSum(),1e-10);
            for(Object g:NextflowFiles.list(c.get("genes"))) {
                var gene=NextflowFiles.object(g);var expected=NextflowFiles.object(gene.get("expected"));var result=prepared.test(vector(gene.get("response")),alternative(c.get("alternative")));
                double[] actual={result.moranI(),result.expectation(),result.variance(),result.statistic(),result.pValue(),result.logPValue()};String[] fields={"moran_I","expectation","variance","statistic","p_value","log_p_value"};
                for(int i=0;i<fields.length;i++)assertEquals(number(expected.get(fields[i])),actual[i],1e-10+1e-11*Math.abs(number(expected.get(fields[i]))),c.get("id")+" "+gene.get("id")+" "+fields[i]);
                assertEquals(number(expected.get("p_value"))==0,result.pValue()==0,"original/native underflow classification");
                if(result.pValue()==0){zeros++;assertTrue(Double.isFinite(result.logPValue()));assertTrue(result.logPValue()<-745);}
                genes++;
            }
            cases++;
        }
        assertEquals(36,cases);assertEquals(108,genes);assertTrue(zeros>=4);
    }

    private static List<Arc> ring(int n) {var result=new ArrayList<Arc>();for(int i=0;i<n;i++){result.add(new Arc(i,(i+1)%n,1));result.add(new Arc(i,(i+n-1)%n,1));}return result;}
    private static double[][] design(int n) {double[][] x=new double[n][2];for(int i=0;i<n;i++){x[i][0]=1;x[i][1]=i/(double)n;}return x;}
    private static double[] response(int n) {double[] y=new double[n];for(int i=0;i<n;i++)y[i]=Math.sin(i*.3)+i*.02;return y;}

    @Test void changingUnitsAndModelBasisPreservesTheSameResidualTest() {
        int n=40;var x=design(n);var y=response(n);var edges=ring(n);var a=RegressionMoran.prepare(x,edges,false).test(y,Alternative.GREATER);
        double[][] transformed=new double[n][2];double[] shifted=new double[n];
        for(int i=0;i<n;i++){transformed[i][0]=x[i][0]+2*x[i][1];transformed[i][1]=1e4*x[i][1];shifted[i]=-7*y[i]+3+5*x[i][1];}
        var scaledEdges=edges.stream().map(e->new Arc(e.from(),e.to(),7*e.weight())).toList();
        var b=RegressionMoran.prepare(transformed,scaledEdges,false).test(shifted,Alternative.GREATER);
        assertEquals(a.moranI(),b.moranI(),1e-12);assertEquals(a.expectation(),b.expectation(),1e-12);assertEquals(a.variance(),b.variance(),1e-12);assertEquals(a.logPValue(),b.logPValue(),1e-11);
    }
    @Test void preparedObjectsOwnTheirInputsAndResponsesAreUnchanged() {
        var x=design(30);var edges=new ArrayList<>(ring(30));var y=response(30);double[] original=y.clone();var prepared=RegressionMoran.prepare(x,edges,false);var a=prepared.test(y,Alternative.GREATER);
        x[0][0]=Double.NaN;edges.clear();var b=prepared.test(y,Alternative.GREATER);assertEquals(a,b);assertArrayEquals(original,y);
        assertEquals(1,prepared.test(y,Alternative.GREATER).pValue()+prepared.test(y,Alternative.LESS).pValue(),1e-15);
    }
    @Test void refusesRankDeficiencyAndNumericallyUndefinedResidualVariation() {
        double[][] bad=new double[20][2];for(double[] row:bad)Arrays.fill(row,1);
        assertThrows(IllegalArgumentException.class,()->RegressionMoran.prepare(bad,ring(20),false));
        var x=design(20);var prepared=RegressionMoran.prepare(x,ring(20),false);double[] exact=new double[20];for(int i=0;i<20;i++)exact[i]=3+5*x[i][1];
        assertThrows(ArithmeticException.class,()->prepared.test(exact,Alternative.GREATER));assertThrows(ArithmeticException.class,()->prepared.test(new double[20],Alternative.GREATER));
        var complete=new ArrayList<Arc>();for(int i=0;i<20;i++)for(int j=0;j<20;j++)if(i!=j)complete.add(new Arc(i,j,1));
        assertThrows(ArithmeticException.class,()->RegressionMoran.prepare(x,complete,false));
    }
    @Test void refusesDuplicateSelfNonpositiveAndUnknownGraphEntries() {
        var x=design(20);
        for(Arc invalid:List.of(new Arc(0,0,1),new Arc(0,20,1),new Arc(-1,2,1),new Arc(0,1,0),new Arc(0,1,-1),new Arc(0,1,Double.NaN))) {
            var edges=new ArrayList<>(ring(20));edges.add(invalid);assertThrows(IllegalArgumentException.class,()->RegressionMoran.prepare(x,edges,false));
        }
        var duplicate=new ArrayList<>(ring(20));duplicate.add(duplicate.get(0));assertThrows(IllegalArgumentException.class,()->RegressionMoran.prepare(x,duplicate,false));
    }
    @Test void refusesMissingValuesUnsupportedDimensionsAndCapacityBeforeReturningResults() {
        var x=design(20);x[3][1]=Double.NaN;assertThrows(IllegalArgumentException.class,()->RegressionMoran.prepare(x,ring(20),false));
        assertThrows(IllegalArgumentException.class,()->RegressionMoran.prepare(design(20),ring(20),false,1e-7,1));
        var prepared=RegressionMoran.prepare(design(20),ring(20),false);var y=response(20);y[5]=Double.NaN;assertThrows(IllegalArgumentException.class,()->prepared.test(y,Alternative.GREATER));
    }
}
