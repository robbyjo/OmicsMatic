/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jlinalg.proteomics.MaxLfq;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class IqMaxLfqTest {
    static final MaxLfq.Limits LIMITS = new MaxLfq.Limits(1000, 1000, 1000000, 1000000, 67108864, 1000000000, 10000);
    static final Runnable CHECK = () -> { };
    @Test void matchesAll33UnmodifiedOriginalFastMaxlfqTablesAndAnnotations() throws Exception {
        Map<?,?> fixture;
        try (var stream = getClass().getResourceAsStream("/org/jlinalg/proteomics/iq-2.3.0-fast-maxlfq.json")) {
            assertNotNull(stream); fixture = object(SimpleJson.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
        int checked = 0;
        for (Object value : list(fixture.get("cases"))) {
            var c = object(value); var input = new ArrayList<MaxLfq.Observation>();
            for (Object raw : list(c.get("observations"))) { var r = object(raw); input.add(new MaxLfq.Observation(text(r.get("protein")), text(r.get("sample")), text(r.get("ion")), ((Number)r.get("log2")).doubleValue())); }
            var actual = MaxLfq.fast(input, LIMITS, CHECK);
            assertEquals(list(c.get("samples")), actual.samples(), c.get("name").toString());
            var expected = list(c.get("expected")); assertEquals(expected.size(), actual.proteins().size());
            for (int i = 0; i < expected.size(); i++) {
                var row = object(expected.get(i)); var p = actual.proteins().get(i); var numbers = list(row.get("estimate"));
                assertEquals(row.get("protein"), p.id()); assertEquals(row.get("annotation"), p.result().annotation()); assertEquals(numbers.size(), p.result().estimate().length);
                for (int j = 0; j < numbers.size(); j++) {
                    double n = p.result().estimate()[j];
                    if (numbers.get(j) == null) assertTrue(Double.isNaN(n));
                    else assertEquals(((Number)numbers.get(j)).doubleValue(), n, 2e-8, c.get("name")+":"+p.id()+":"+j);
                }
                assertTrue(p.result().relativeResidual() <= 1e-8);
            }
            assertEquals(input.size(), actual.inputRows()); checked++;
        }
        assertEquals(33, checked);
    }
    @Test void connectedPathUsesObservedCellMeanConstraintAndUnweightedPairMedians() {
        double nan = Double.NaN;
        var r = MaxLfq.matrix(new double[][]{{1,2,nan},{nan,3,4}}, LIMITS, CHECK);
        assertArrayEquals(new double[]{1.5,2.5,3.5}, r.estimate(), 1e-10);
        assertEquals(2,r.ratios()); assertEquals(4,r.observedCells()); assertEquals("",r.annotation());
    }
    @Test void disconnectedAndMissingSamplesKeepTheirSourceAnnotationAndNoZeroFill() {
        double nan=Double.NaN;
        var r=MaxLfq.matrix(new double[][]{{1,3,nan,nan,nan},{nan,nan,nan,10,14}},LIMITS,CHECK);
        assertArrayEquals(new double[]{1,3,nan,10,14},r.estimate(),1e-10);
        assertEquals("1;1;NA;3;3",r.annotation());
        var empty=MaxLfq.matrix(new double[][]{{nan,nan},{nan,nan}},LIMITS,CHECK);
        assertEquals("NA",empty.annotation());assertTrue(Arrays.stream(empty.estimate()).allMatch(Double::isNaN));
    }
    @Test void zeroAndNegativeLogQuantitiesAreValidAndDuplicatesSumInLinearSpace() {
        var r=MaxLfq.fast(List.of(new MaxLfq.Observation("P","A","i",0),new MaxLfq.Observation("P","A","i",0),new MaxLfq.Observation("P","B","i",-2)),LIMITS,CHECK);
        assertArrayEquals(new double[]{1,-2},r.proteins().get(0).result().estimate(),1e-12);assertEquals(1,r.duplicateRows());
    }
    @Test void inadequateIterationCapacityDoesNotReturnAConvenientBestIterate() {
        double n=Double.NaN;var limits=new MaxLfq.Limits(10,10,100,100,1000000,1000000,1);
        assertThrows(ArithmeticException.class,()->MaxLfq.matrix(new double[][]{{1,2,n,n},{n,4,8,n},{n,n,1,3}},limits,CHECK));
    }
    @Test void invalidNumericalInputsOverflowAndCompleteCapacityAreRefused() {
        assertThrows(IllegalArgumentException.class,()->new MaxLfq.Observation("P","A","i",Double.NaN));
        assertThrows(IllegalArgumentException.class,()->MaxLfq.matrix(new double[][]{{Double.POSITIVE_INFINITY}},LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MaxLfq.matrix(new double[][]{{1},{2,3}},LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MaxLfq.matrix(new double[][]{{1,2}},new MaxLfq.Limits(1,10,100,100,100000,100000,10),CHECK));
        assertThrows(ArithmeticException.class,()->MaxLfq.fast(List.of(new MaxLfq.Observation("P","A","i",1023),new MaxLfq.Observation("P","A","i",1023)),LIMITS,CHECK));
        assertThrows(IllegalArgumentException.class,()->MaxLfq.matrix(new double[][]{{1,2},{3,5}},new MaxLfq.Limits(10,10,100,100,100000,1,100),CHECK));
        assertThrows(IllegalStateException.class,()->MaxLfq.matrix(new double[][]{{1}},LIMITS,()->{throw new IllegalStateException("cancelled");}));
    }
    @Test void singleSampleUsesMedianAndResultsCannotMutateCapturedInputOrStoredArrays() {
        double[][] input={{-2},{0},{10}};var result=MaxLfq.matrix(input,LIMITS,CHECK);assertEquals(0,result.estimate()[0]);
        assertArrayEquals(new double[]{-2},input[0]);var estimate=result.estimate();estimate[0]=999;assertEquals(0,result.estimate()[0]);var components=result.component();components[0]=999;assertEquals(0,result.component()[0]);
    }
}
