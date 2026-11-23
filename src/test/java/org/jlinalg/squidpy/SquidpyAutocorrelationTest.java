/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.squidpy;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class SquidpyAutocorrelationTest {
    private static final int[] ROWS = {0,1,3,5,6};
    private static final int[] COLS = {1,0,2,1,3,2};
    private static final double[] WEIGHTS = {1,1,1,1,1,1};
    private static final double[] VALUES = {1,2,3,4};

    private static Map<String,byte[]> fixture() throws IOException {
        Map<String,byte[]> files = new HashMap<>();
        try (var source = SquidpyAutocorrelationTest.class.getResourceAsStream(
                "/org/jlinalg/squidpy/autocorrelation-original.zip")) {
            assertNotNull(source);
            try (var zip = new ZipInputStream(source)) {
                for (var entry=zip.getNextEntry(); entry!=null; entry=zip.getNextEntry())
                    if (!entry.isDirectory()) assertNull(files.put(entry.getName(),zip.readAllBytes()));
            }
        }
        return files;
    }
    private static double number(Object value) {
        return Double.parseDouble(value instanceof GprofilerJson.Numeric n ? n.token() : (String)value);
    }
    private static double[] doubles(Object value) {
        return ((List<?>)value).stream().mapToDouble(SquidpyAutocorrelationTest::number).toArray();
    }
    private static int[] integers(Object value) {
        return ((List<?>)value).stream().mapToInt(x -> (int)number(x)).toArray();
    }
    private static void close(double expected, double actual, String field) {
        if (Double.isNaN(expected)) assertTrue(Double.isNaN(actual),field);
        else assertEquals(expected,actual,3e-14+3e-12*Math.abs(expected),field);
    }
    static Stream<Arguments> originalCases() throws IOException {
        var files=fixture();
        var input=(Map<?,?>)GprofilerJson.read(files.get("inputs.json"));
        Map<String,Map<?,?>> graphs=new HashMap<>();
        for (Object object:(List<?>)input.get("graphs")) {
            var graph=(Map<?,?>)object; graphs.put((String)graph.get("name"),graph);
        }
        List<Arguments> cases=new ArrayList<>();
        for (String version:List.of("1.1.2","1.2.2","1.8.4")) {
            var edition=SquidpyAutocorrelation.Edition.valueOf("SQUIDPY_"+version.replace('.','_'));
            var original=(List<?>)GprofilerJson.read(files.get(version+"/ORIGINAL-CASES.json"));
            assertEquals(80,original.size());
            for (Object object:original) {
                var call=(Map<?,?>)object;var graph=graphs.get(call.get("graph"));
                if (call.get("status").equals("ORIGINAL_RETURNED")) {
                    assertEquals("float64",call.get("expression_dtype"));
                    for (Object row:(List<?>)call.get("rows")) {
                        var expected=(Map<?,?>)row;
                        cases.add(Arguments.of(version+" "+call.get("graph")+" "+expected.get("feature")+" "+call.get("mode")+" normalized="+call.get("row_l1_normalized")+" two="+call.get("two_tailed"),edition,graph,call,expected));
                    }
                } else {
                    assertEquals("ORIGINAL_RAISED",call.get("status"));
                    for (Object feature:((Map<?,?>)graph.get("features")).keySet())
                        cases.add(Arguments.of(version+" "+call.get("graph")+" original-error "+feature+" "+call.get("mode")+" normalized="+call.get("row_l1_normalized")+" two="+call.get("two_tailed"),edition,graph,call,Map.of("feature",feature)));
                }
            }
        }
        assertEquals(720,cases.size());
        return cases.stream();
    }

    @ParameterizedTest(name="{0}") @MethodSource("originalCases")
    void originalPublicApiRowsRetainEditionStatisticsAndExplicitDegenerateStates(
            String context, SquidpyAutocorrelation.Edition edition,
            Map<?,?> graph, Map<?,?> call, Map<?,?> expected) {
        var values=doubles(((Map<?,?>)graph.get("features")).get(expected.get("feature")));
        boolean normalized=(Boolean)call.get("row_l1_normalized");
        var mode=SquidpyAutocorrelation.Statistic.valueOf(((String)call.get("mode")).toUpperCase(Locale.ROOT));
        var result=SquidpyAutocorrelation.compute(edition,mode,values,integers(graph.get("row_pointers")),
                integers(graph.get("columns")),doubles(graph.get("weights")),normalized,(Boolean)call.get("two_tailed"));
        assertEquals(edition,result.edition()); assertEquals(mode,result.statistic());
        assertEquals(normalized,result.rowL1Normalized());
        assertEquals(Boolean.TRUE.equals(call.get("two_tailed")),result.twoTailed());
        assertEquals(mode==SquidpyAutocorrelation.Statistic.MORAN ? -1.0/(values.length-1) : 1.0,result.expected());
        assertFalse(result.condition().isBlank());
        if (call.get("status").equals("ORIGINAL_RAISED")) {
            assertEquals(SquidpyAutocorrelation.Status.UNDEFINED_STATISTIC,result.status(),context);
            assertFalse(Double.isFinite(result.score())); assertTrue(Double.isNaN(result.normalityPValue()));
            return;
        }
        close(number(expected.get("score")),result.score(),context+" score");
        close(number(expected.get("variance")),result.normalityVariance(),context+" variance");
        if (!(Boolean)expected.get("point_statistic_finite")) {
            assertEquals(SquidpyAutocorrelation.Status.UNDEFINED_STATISTIC,result.status());
            assertTrue(Double.isNaN(result.normalityPValue()));
        } else if (graph.get("name").equals("complete-zero-normality-variance")
                || graph.get("name").equals("tiny-weight-square-underflow") && !normalized) {
            assertTrue(Set.of(SquidpyAutocorrelation.Status.UNDEFINED_ANALYTIC_VARIANCE,
                    SquidpyAutocorrelation.Status.UNRESOLVED_ANALYTIC_VARIANCE).contains(result.status()),context);
            assertTrue(Double.isNaN(result.normalityPValue()),context+" original undefined/unstable probability is never a reference estimate");
        } else {
            assertEquals(SquidpyAutocorrelation.Status.COMPLETE,result.status(),context);
            close(number(expected.get("p_value")),result.normalityPValue(),context+" probability");
            if (number(expected.get("p_value"))==0.) assertEquals(0.,result.normalityPValue(),context+" source CDF cancellation");
        }
    }

    @Test void originalReferenceMembersMatchTheirFrozenProvenance() throws IOException, NoSuchAlgorithmException {
        var files=fixture();var provenance=(Map<?,?>)GprofilerJson.read(files.get("PROVENANCE.json"));
        var identities=(Map<?,?>)provenance.get("files");assertEquals(10,identities.size());assertEquals(11,files.size());
        for (var item:identities.entrySet()) {
            byte[] raw=files.get(item.getKey());assertNotNull(raw);var identity=(Map<?,?>)item.getValue();
            assertEquals(number(identity.get("bytes")),raw.length);
            assertEquals(identity.get("sha256"),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        }
        assertEquals(Boolean.TRUE,provenance.get("synthetic_inputs_only"));
        assertEquals(Boolean.FALSE,provenance.get("native_implementation_used_to_generate_expected"));
    }

    @Test void pathPointStatisticsHaveIndependentHandCalculatedValues() {
        var moran=compute(VALUES,ROWS,COLS,WEIGHTS,false,false,SquidpyAutocorrelation.Statistic.MORAN);
        var geary=compute(VALUES,ROWS,COLS,WEIGHTS,false,false,SquidpyAutocorrelation.Statistic.GEARY);
        assertEquals(1./3,moran.score(),1e-15);assertEquals(.3,geary.score(),1e-15);
    }
    @Test void fullGraphRoundingCannotProduceAnAdmittedAnalyticProbability() {
        int[] rows={0,3,6,9,12},columns={1,2,3,0,2,3,0,1,3,0,1,2};double[] weights=new double[12];Arrays.fill(weights,1.);
        for (var edition:SquidpyAutocorrelation.Edition.values())
            for (var mode:SquidpyAutocorrelation.Statistic.values())for (boolean normalized:new boolean[]{false,true}) {
                var result=SquidpyAutocorrelation.compute(edition,mode,VALUES,rows,columns,weights,normalized,false);
                assertNotEquals(SquidpyAutocorrelation.Status.COMPLETE,result.status());
                assertTrue(Double.isFinite(result.score())); assertTrue(Double.isNaN(result.normalityPValue()));
            }
    }
    @Test void historicGearyVarianceIsAnExplicitDifferentProfile() {
        var old=SquidpyAutocorrelation.compute(SquidpyAutocorrelation.Edition.SQUIDPY_1_2_2,SquidpyAutocorrelation.Statistic.GEARY,VALUES,ROWS,COLS,WEIGHTS,false,false);
        var now=compute(VALUES,ROWS,COLS,WEIGHTS,false,false,SquidpyAutocorrelation.Statistic.GEARY);
        assertEquals(old.score(),now.score());assertNotEquals(old.normalityVariance(),now.normalityVariance());
        assertTrue(old.condition().contains("Historical"));
    }
    @Test void inputArraysRemainUnchangedIncludingNormalizedWeights() {
        var values=VALUES.clone();var rows=ROWS.clone();var cols=COLS.clone();var weights=WEIGHTS.clone();
        compute(values,rows,cols,weights,true,true,SquidpyAutocorrelation.Statistic.MORAN);
        assertArrayEquals(VALUES,values);assertArrayEquals(ROWS,rows);assertArrayEquals(COLS,cols);assertArrayEquals(WEIGHTS,weights);
    }
    @Test void noEdgesAndConstantFeatureReturnExplicitUndefinedStatus() {
        for (var mode:SquidpyAutocorrelation.Statistic.values()) {
            var empty=compute(VALUES,new int[5],new int[0],new double[0],true,false,mode);
            assertEquals(SquidpyAutocorrelation.Status.UNDEFINED_STATISTIC,empty.status());
            assertTrue(Double.isNaN(empty.normalityPValue()));
            var flat=compute(new double[]{3,3,3,3},ROWS,COLS,WEIGHTS,true,false,mode);
            assertEquals(SquidpyAutocorrelation.Status.UNDEFINED_STATISTIC,flat.status());
            assertTrue(Double.isNaN(flat.normalityPValue()));
        }
    }
    @Test void malformedCsrAndNonfiniteValuesAreRefusedWithoutRepair() {
        for (int[] rows:List.of(new int[]{0,1,3,5},new int[]{1,1,3,5,6},new int[]{0,1,3,5,5},
                new int[]{0,2,1,5,6},new int[]{0,-1,3,5,6},new int[]{0,1,7,5,6}))
            assertThrows(IllegalArgumentException.class,()->compute(VALUES,rows,COLS,WEIGHTS,false,false,SquidpyAutocorrelation.Statistic.MORAN));
        for (int[] columns:List.of(new int[]{4,0,2,1,3,2},new int[]{-1,0,2,1,3,2},
                new int[]{1,0,0,1,3,2},new int[]{1,2,0,1,3,2}))
            assertThrows(IllegalArgumentException.class,()->compute(VALUES,ROWS,columns,WEIGHTS,false,false,SquidpyAutocorrelation.Statistic.MORAN));
        for (double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            var weights=WEIGHTS.clone();weights[0]=bad;
            assertThrows(IllegalArgumentException.class,()->compute(VALUES,ROWS,COLS,weights,false,false,SquidpyAutocorrelation.Statistic.MORAN));
            var values=VALUES.clone();values[0]=bad;
            assertThrows(IllegalArgumentException.class,()->compute(values,ROWS,COLS,WEIGHTS,false,false,SquidpyAutocorrelation.Statistic.MORAN));
        }
    }
    @Test void capacityDimensionsAndAbsentDeclarationsFailExplicitly() {
        assertThrows(IllegalArgumentException.class,()->compute(new double[2],new int[3],new int[0],new double[0],false,false,SquidpyAutocorrelation.Statistic.MORAN));
        assertThrows(IllegalArgumentException.class,()->compute(new double[100001],new int[100002],new int[0],new double[0],false,false,SquidpyAutocorrelation.Statistic.MORAN));
        assertThrows(IllegalArgumentException.class,()->compute(VALUES,ROWS,COLS,new double[0],false,false,SquidpyAutocorrelation.Statistic.MORAN));
        assertThrows(IllegalArgumentException.class,()->compute(VALUES,ROWS,COLS,WEIGHTS,false,false,null));
        assertThrows(IllegalArgumentException.class,()->compute(null,ROWS,COLS,WEIGHTS,false,false,SquidpyAutocorrelation.Statistic.MORAN));
        assertThrows(IllegalArgumentException.class,()->SquidpyAutocorrelation.compute(null,SquidpyAutocorrelation.Statistic.MORAN,VALUES,ROWS,COLS,WEIGHTS,false,false));
    }
    private static SquidpyAutocorrelation.Result compute(double[] x,int[] rows,int[] cols,double[] w,
            boolean normalized,boolean two,SquidpyAutocorrelation.Statistic mode) {
        return SquidpyAutocorrelation.compute(SquidpyAutocorrelation.Edition.SQUIDPY_1_8_4,mode,x,rows,cols,w,normalized,two);
    }
}
