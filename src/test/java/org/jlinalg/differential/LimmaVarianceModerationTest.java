/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.differential;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Controls captured from original R namespaces after exact recovered-source guards. */
final class LimmaVarianceModerationTest {
    private static final List<Map<String,String>> INPUT = rows("inputs.tsv");
    private static final List<Map<String,String>> EXPECTED = rows("expected.tsv");
    private static final AtomicLong VALUES = new AtomicLong(), MAX_RELATIVE = new AtomicLong();

    @TestFactory Stream<DynamicTest> originalNamespacesAcrossVarianceAndBoundaryCases() {
        return java.util.stream.IntStream.range(0, INPUT.size()).mapToObj(i ->
            DynamicTest.dynamicTest(INPUT.get(i).get("case"), () -> check(INPUT.get(i), EXPECTED.get(i))));
    }
    private static void check(Map<String,String> in, Map<String,String> expected) {
        assertEquals(in.get("case"), expected.get("case"));
        assertEquals(in.get("operation"), expected.get("operation"));
        double[] x = vector(in.get("x")), df = vector(in.get("df"));
        if (expected.get("status").equals("ERROR")) {
            assertThrows(RuntimeException.class, () -> calculate(in, x, df)); return;
        }
        Object result = calculate(in, x, df); List<String> warnings = List.of();
        if (result instanceof LimmaVarianceModeration.Prior prior) {
            compare(vector(expected.get("scale")), new double[]{prior.scale()});
            compare(vector(expected.get("df2")), new double[]{prior.degreesOfFreedom()});
            warnings = prior.warnings();
        } else if (result instanceof LimmaVarianceModeration.Result squeezed) {
            compare(vector(expected.get("scale")), squeezed.priorVariances());
            compare(vector(expected.get("df2")), new double[]{squeezed.priorDegreesOfFreedom()});
            compare(vector(expected.get("values")), squeezed.posteriorVariances());
            warnings = squeezed.warnings();
        } else compare(vector(expected.get("values")), (double[])result);
        if (in.get("operation").equals("fit") || in.get("operation").equals("squeeze")) {
            for (String message : expected.get("warnings").split(" \\| ")) if (!message.isEmpty())
                assertTrue(warnings.contains(message), "Missing original warning: " + message);
        }
    }
    private static Object calculate(Map<String,String> in, double[] x, double[] df) {
        return switch (in.get("operation")) {
            case "fit" -> LimmaVarianceModeration.fitFDistribution(x, df);
            case "squeeze" -> LimmaVarianceModeration.squeezeLegacy(x, df);
            case "posterior" -> LimmaVarianceModeration.posterior(x, df,
                vector(in.get("prior")), vector(in.get("prior_df")));
            case "inverse" -> new double[]{LimmaVarianceModeration.inverseTrigamma(x[0])};
            case "logmdigamma" -> new double[]{LimmaVarianceModeration.logMinusDigamma(x[0])};
            default -> throw new IllegalArgumentException("Unknown fixture operation");
        };
    }
    @Test void actualOriginalCoverageIncludesFailuresWarningsAndDifferentSourceBranches() {
        assertEquals(372, INPUT.size()); assertEquals(INPUT.size(), EXPECTED.size());
        assertEquals(7, EXPECTED.stream().filter(r -> r.get("status").equals("ERROR")).count());
        assertEquals(36, EXPECTED.stream().filter(r -> !r.get("warnings").isEmpty()).count());
        assertEquals(162, INPUT.stream().filter(r -> r.get("operation").equals("fit")).count());
        assertEquals(162, INPUT.stream().filter(r -> r.get("operation").equals("squeeze")).count());
    }
    @Test void originalAllZeroOffsetAndInfinitePriorRemainExplicit() {
        var result = LimmaVarianceModeration.squeezeLegacy(new double[8], new double[]{6});
        assertTrue(result.finite()); assertEquals(Double.POSITIVE_INFINITY, result.priorDegreesOfFreedom());
        assertEquals(8, result.offset()); assertEquals(0, result.filtered());
        for (double v : result.posteriorVariances()) assertEquals(1e-5, v);
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("eBayes unreliable")));
    }
    @Test void sourceFilteringIsReportedAndNeverChangesTheCallerArrays() {
        double[] x = {1,Double.NaN,2,Double.POSITIVE_INFINITY,-1,8}, df = {7};
        double[] before = x.clone(); var result = LimmaVarianceModeration.fitFDistribution(x, df);
        assertArrayEquals(before, x); assertEquals(3, result.filtered()); assertEquals(3, result.informative());
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("excludes 3")));
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("change"));
    }
    @Test void returnedArraysDoNotExposeTheSavedValues() {
        double[] x = {1,2,3,4,5,6}; var result = LimmaVarianceModeration.squeezeLegacy(x, new double[]{7});
        double[] expected = result.posteriorVariances(); result.posteriorVariances()[0] = 99;
        result.priorVariances()[0] = 99; assertArrayEquals(expected, result.posteriorVariances());
        assertArrayEquals(new double[]{1,2,3,4,5,6}, x);
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("change"));
    }
    @Test void suppliedPosteriorHasIndependentCanonicalIdentities() {
        double[] x = {1,2,3,4};
        assertArrayEquals(x, LimmaVarianceModeration.posterior(x,new double[]{7},new double[]{2},new double[]{0}));
        assertArrayEquals(new double[]{2,2,2,2}, LimmaVarianceModeration.posterior(x,new double[]{7},new double[]{2},new double[]{Double.POSITIVE_INFINITY}));
        assertArrayEquals(new double[]{1.5,2,2.5,3}, LimmaVarianceModeration.posterior(x,new double[]{4},new double[]{2},new double[]{4}));
    }
    @Test void invalidOriginalOutcomesAreVisibleAndUnclamped() {
        var singleton = LimmaVarianceModeration.fitFDistribution(new double[]{-2},new double[0]);
        assertEquals(-2, singleton.scale()); assertFalse(singleton.finite());
        assertTrue(singleton.warnings().stream().anyMatch(s -> s.contains("NO COMPLETE RESULT")));
        var missing = LimmaVarianceModeration.fitFDistribution(new double[]{1,2,3},new double[]{0});
        assertTrue(Double.isNaN(missing.scale())); assertFalse(missing.finite());
        assertTrue(Double.isNaN(LimmaVarianceModeration.posterior(new double[]{0},new double[]{0},new double[]{0},new double[]{0})[0]));
    }
    @Test void partialRecyclingAndNaPriorDfAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> LimmaVarianceModeration.fitFDistribution(new double[]{1,2,3},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class, () -> LimmaVarianceModeration.squeezeLegacy(new double[]{1,2,3},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class, () -> LimmaVarianceModeration.posterior(new double[]{1,2,3},new double[]{1},new double[]{1,2},new double[]{2}));
        assertThrows(IllegalArgumentException.class, () -> LimmaVarianceModeration.posterior(new double[]{1,2},new double[]{1},new double[]{1},new double[]{Double.NaN}));
    }
    @AfterAll static void report() {
        System.out.println("LIMMA_VARIANCE_ORIGINAL_FIELDS=" + VALUES.get()
            + " MAX_RELATIVE=" + Double.longBitsToDouble(MAX_RELATIVE.get()));
    }
    private static void compare(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < actual.length; i++) {
            double e = expected[i], a = actual[i]; VALUES.incrementAndGet();
            if (!Double.isFinite(e) || e == 0) { assertEquals(e, a, "value at " + i); continue; }
            assertTrue(Double.isFinite(a), "Nonfinite actual at " + i);
            double relative = Math.abs((a - e) / e);
            MAX_RELATIVE.accumulateAndGet(Double.doubleToRawLongBits(relative), Math::max);
            assertTrue(Math.abs(a - e) <= Math.max(32 * Math.ulp(e), 5e-12 * Math.abs(e)),
                "Original " + e + ", native " + a + ", relative " + relative + " at " + i);
        }
    }
    private static double[] vector(String text) {
        if (text.equals("EMPTY")) return new double[0];
        return Arrays.stream(text.split(";", -1)).mapToDouble(s -> switch (s) {
            case "NA", "NaN" -> Double.NaN; case "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY; default -> Double.parseDouble(s);
        }).toArray();
    }
    private static List<Map<String,String>> rows(String name) {
        try (InputStream source = LimmaVarianceModerationTest.class.getResourceAsStream("limma-variance-3.68.5.zip")) {
            assertNotNull(source); try (ZipInputStream zip = new ZipInputStream(source)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) if (entry.getName().equals(name)) {
                    String[] lines = new String(zip.readAllBytes(), StandardCharsets.UTF_8).split("\\r?\\n");
                    String[] header = lines[0].split("\\t", -1); List<Map<String,String>> result = new ArrayList<>();
                    for (int i = 1; i < lines.length; i++) {
                        String[] fields = lines[i].split("\\t", -1); assertEquals(header.length, fields.length);
                        Map<String,String> row = new HashMap<>();
                        for (int j = 0; j < fields.length; j++) row.put(header[j], fields[j]); result.add(row);
                    }
                    return List.copyOf(result);
                }
            }
            throw new IOException("Missing fixture " + name);
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
}
