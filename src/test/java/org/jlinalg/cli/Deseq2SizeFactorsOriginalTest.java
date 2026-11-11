/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.sequence.Deseq2SizeFactors;
import org.jlinalg.sequence.Deseq2SizeFactors.Limits;
import org.jlinalg.sequence.Deseq2SizeFactors.Type;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Direct original-package controls include missing values and source failures. */
final class Deseq2SizeFactorsOriginalTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) {
        return (Map<String,Object>) value;
    }
    private static String scalar(Object value) { return ((List<?>)value).get(0).toString(); }
    private static double number(Object value) {
        return switch (value.toString()) {
            case "NA" -> Deseq2SizeFactors.MISSING;
            case "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(value.toString());
        };
    }
    private static void same(Object expected, double actual) {
        double target = number(expected);
        if (Deseq2SizeFactors.isMissing(target)) assertTrue(Deseq2SizeFactors.isMissing(actual), "Source NA kind");
        else if (Double.isNaN(target)) assertTrue(Double.isNaN(actual) && !Deseq2SizeFactors.isMissing(actual), "Source NaN kind");
        else if (Double.isInfinite(target) || target == 0) assertEquals(target, actual);
        else {
            assertTrue(Double.isFinite(actual), "Expected finite numeric value");
            assertEquals(target, actual, Math.abs(target)*2e-12, "Original function numeric comparison");
        }
    }
    @TestFactory Stream<DynamicTest> originalPackageControls() throws Exception {
        Map<String,Object> fixture;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/sequence/deseq2-size-factors/original.json")) {
            assertNotNull(input);
            fixture = object(SimpleJson.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        assertEquals("1.52.0", scalar(fixture.get("reference_version")));
        List<DynamicTest> tests = new ArrayList<>();
        for (Object item : (List<?>)fixture.get("cases")) {
            var c = object(item);
            tests.add(DynamicTest.dynamicTest(scalar(c.get("name")), () -> {
                var rows = (List<?>)c.get("counts");
                int[][] counts = new int[rows.size()][];
                for (int g = 0; g < counts.length; g++)
                    counts[g] = ((List<?>)rows.get(g)).stream().mapToInt(v -> ((Number)v).intValue()).toArray();
                double[] means = c.get("geo_means") == null ? null
                        : ((List<?>)c.get("geo_means")).stream().mapToDouble(Deseq2SizeFactorsOriginalTest::number).toArray();
                int[] controls = c.get("control_genes") == null ? null
                        : ((List<?>)c.get("control_genes")).stream().mapToInt(v -> ((Number)v).intValue()).toArray();
                Type type = Type.valueOf(scalar(c.get("type")).toUpperCase(java.util.Locale.ROOT));
                var expected = object(c.get("expected"));
                if ("error".equals(scalar(expected.get("status")))) {
                    var error = assertThrows(IllegalArgumentException.class,
                            () -> Deseq2SizeFactors.estimate(counts,type,means,controls,Limits.defaults(),()->false));
                    assertEquals(scalar(expected.get("error")), error.getMessage());
                    return;
                }
                var actual = Deseq2SizeFactors.estimate(counts,type,means,controls,Limits.defaults(),()->false);
                var factors = (List<?>)expected.get("factors");
                assertEquals(factors.size(), actual.factors().length);
                for (int s = 0; s < factors.size(); s++) same(factors.get(s), actual.factors()[s]);
                double[][] normalized = Deseq2SizeFactors.normalizedCounts(counts,actual,Limits.defaults(),()->false);
                var expectedRows = (List<?>)expected.get("normalized");
                assertEquals(counts.length, expectedRows.size());
                for (int g = 0; g < counts.length; g++)
                    for (int s = 0; s < factors.size(); s++) same(((List<?>)expectedRows.get(g)).get(s), normalized[g][s]);
                assertEquals(actual.usableForFit(), Stream.of(actual.factors()).flatMapToDouble(java.util.Arrays::stream)
                        .allMatch(f -> f > 0 && Double.isFinite(f)));
            }));
        }
        assertEquals(76, tests.size());
        return tests.stream();
    }
    @TestFactory Stream<DynamicTest> explicitAdmissionAndCapacityFailures() {
        int[][] counts = {{1,2},{3,4}};
        List<Runnable> invalid = List.of(
            () -> Deseq2SizeFactors.estimate(new int[0][],Type.RATIO),
            () -> Deseq2SizeFactors.estimate(new int[][]{{}},Type.RATIO),
            () -> Deseq2SizeFactors.estimate(new int[][]{{1},null},Type.RATIO),
            () -> Deseq2SizeFactors.estimate(new int[][]{{1},{1,2}},Type.RATIO),
            () -> Deseq2SizeFactors.estimate(new int[][]{{-1,1}},Type.RATIO),
            () -> Deseq2SizeFactors.estimate(counts,Type.RATIO,new double[]{1,-1},null,Limits.defaults(),()->false),
            () -> Deseq2SizeFactors.estimate(counts,Type.RATIO,null,new int[]{-1},Limits.defaults(),()->false),
            () -> Deseq2SizeFactors.estimate(counts,Type.RATIO,null,new int[]{2},Limits.defaults(),()->false),
            () -> Deseq2SizeFactors.estimate(counts,Type.RATIO,null,null,new Limits(3,100,10),()->false),
            () -> Deseq2SizeFactors.estimate(counts,Type.RATIO,null,null,new Limits(4,7,10),()->false),
            () -> Deseq2SizeFactors.estimate(counts,Type.RATIO,null,null,new Limits(4,100,1),()->false),
            () -> Deseq2SizeFactors.estimate(counts,Type.RATIO,null,new int[]{0,0,0},new Limits(4,100,2),()->false),
            () -> new Limits(0,1,1), () -> new Limits(1,0,1), () -> new Limits(1,1,0),
            () -> Deseq2SizeFactors.normalizedCounts(new int[][]{{1}},Deseq2SizeFactors.estimate(counts,Type.RATIO),Limits.defaults(),()->false),
            () -> Deseq2SizeFactors.normalizedCounts(counts,Deseq2SizeFactors.estimate(counts,Type.RATIO),new Limits(4,3,10),()->false)
        );
        List<DynamicTest> tests = new ArrayList<>();
        for (int i=0;i<invalid.size();i++) {
            var operation=invalid.get(i);
            tests.add(DynamicTest.dynamicTest("invalid-admission-"+i,
                    () -> assertThrows(IllegalArgumentException.class,operation::run)));
        }
        return tests.stream();
    }
    @Test void cancellationAndResultImmutability() {
        int[][] counts={{1,2},{3,4}};
        assertThrows(CancellationException.class,()->Deseq2SizeFactors.estimate(counts,Type.RATIO,null,null,Limits.defaults(),()->true));
        int[] checks={0};
        assertThrows(CancellationException.class,()->Deseq2SizeFactors.estimate(counts,Type.RATIO,null,null,Limits.defaults(),()->++checks[0]>8));
        Thread.currentThread().interrupt();
        try { assertThrows(CancellationException.class,()->Deseq2SizeFactors.estimate(counts,Type.RATIO)); }
        finally { Thread.interrupted(); }
        var result=Deseq2SizeFactors.estimate(counts,Type.RATIO);
        double original=result.factors()[0]; result.factors()[0]=999;
        result.logGeometricMeans()[0]=999; result.selectedPerSample()[0]=999;
        assertEquals(original,result.factors()[0]); assertNotEquals(999,result.logGeometricMeans()[0]);
        assertEquals(2,result.selectedPerSample()[0]); assertArrayEquals(new int[]{1,2},counts[0]);
        assertThrows(UnsupportedOperationException.class,()->result.warnings().add("change"));
        assertTrue(result.usableForFit());
        assertThrows(CancellationException.class,()->Deseq2SizeFactors.normalizedCounts(counts,result,Limits.defaults(),()->true));
    }
}
