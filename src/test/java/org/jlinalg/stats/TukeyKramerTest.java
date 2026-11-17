/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdistlib.T;
import org.junit.jupiter.api.Test;

class TukeyKramerTest {
    private record Fixture(double confidence, Map<Integer, List<Double>> groups) { }

    @Test void matches57IndependentBaseRComparisonsAcross10Fits() throws Exception {
        Map<String, Fixture> fixtures = new LinkedHashMap<>();
        try (var input = reader("inputs.tsv")) {
            assertEquals("case\tconfidence\tgroup\tvalue", input.readLine());
            for (String line; (line = input.readLine()) != null;) {
                String[] f = line.split("\t", -1);
                Fixture fixture = fixtures.computeIfAbsent(f[0], key -> new Fixture(Double.parseDouble(f[1]), new LinkedHashMap<>()));
                fixture.groups().computeIfAbsent(Integer.parseInt(f[2]), key -> new ArrayList<>()).add(Double.parseDouble(f[3]));
            }
        }
        assertEquals(10, fixtures.size());
        Map<String, String[]> exactTwoGroup = new LinkedHashMap<>();
        try (var input = reader("two-group-exact.tsv")) {
            input.readLine();
            for (String line; (line = input.readLine()) != null;) {
                String[] f = line.split("\t", -1); exactTwoGroup.put(f[0], f);
            }
        }
        assertEquals(2, exactTwoGroup.size());
        Map<String, TukeyKramer.Result> actual = new LinkedHashMap<>();
        for (var entry : fixtures.entrySet()) {
            Fixture fixture = entry.getValue();
            double[][] samples = fixture.groups().values().stream().map(values -> values.stream().mapToDouble(Double::doubleValue).toArray()).toArray(double[][]::new);
            actual.put(entry.getKey(), TukeyKramer.compare(samples, fixture.confidence()));
        }
        int checked = 0;
        try (var input = reader("expected.tsv")) {
            assertTrue(input.readLine().startsWith("case\tfirst\tsecond\tdifference\t"));
            for (String line; (line = input.readLine()) != null;) {
                String[] f = line.split("\t", -1);
                if (exactTwoGroup.containsKey(f[0])) f = exactTwoGroup.get(f[0]);
                var fit = actual.get(f[0]); int first = Integer.parseInt(f[1]), second = Integer.parseInt(f[2]);
                var comparison = fit.comparisons().stream().filter(row -> row.firstGroup() == first && row.secondGroup() == second).findFirst().orElseThrow();
                double[] values = {comparison.difference(), comparison.standardError(), comparison.studentizedRange(), comparison.adjustedPValue(), comparison.lowerConfidenceLimit(), comparison.upperConfidenceLimit(), fit.residualStandardDeviation(), fit.residualDegreesOfFreedom(), fit.criticalStudentizedRange()};
                for (int i = 0; i < values.length; i++) {
                    double expected = Double.parseDouble(f[i+3]);
                    assertEquals(expected, values[i], 2e-10 + 2e-10 * Math.abs(expected), f[0] + " pair " + first + "," + second + " field " + i);
                }
                checked++;
            }
        }
        assertEquals(57, checked);
        assertEquals(checked, actual.values().stream().mapToInt(fit -> fit.comparisons().size()).sum());
    }

    @Test void twoGroupComparisonAgreesWithTwoSidedPooledStudentT() {
        var fit = TukeyKramer.compare(new double[][]{{1, 4, 2, 6}, {2, 3, 5, 8, 7}});
        var pair = fit.comparisons().get(0);
        double p = 2 * T.cumulative(Math.abs(pair.difference() / pair.standardError()), fit.residualDegreesOfFreedom(), false, false);
        assertEquals(p, pair.adjustedPValue(), 2e-10);
        double critical = Math.sqrt(2) * T.quantile(0.975, fit.residualDegreesOfFreedom(), true, false);
        assertEquals(critical, fit.criticalStudentizedRange(), 2e-9);
    }

    @Test void completeFamilyAffectsAdjustmentAndIntervalsEvenForTheSamePair() {
        var two = TukeyKramer.compareMeans(new double[]{0, 1}, new int[]{5, 5}, 1, 20, .95);
        var three = TukeyKramer.compareMeans(new double[]{0, 1, 2}, new int[]{5, 5, 5}, 1, 20, .95);
        assertTrue(three.comparisons().get(0).adjustedPValue() > two.comparisons().get(0).adjustedPValue());
        assertTrue(three.criticalStudentizedRange() > two.criticalStudentizedRange());
    }

    @Test void resultsAreImmutableAndInputsAreNotMutatedOrRetained() {
        double[] means = {4, 1, 2}; int[] sizes = {5, 7, 3};
        var result = TukeyKramer.compareMeans(means, sizes, 1.5, 12, .95);
        means[0] = 1000; sizes[0] = 100;
        assertEquals(4, result.groups().get(0).mean()); assertEquals(5, result.groups().get(0).size());
        assertThrows(UnsupportedOperationException.class, () -> result.groups().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.comparisons().clear());
        double[][] data = {{1, 2, 3}, {4, 5, 6}};
        TukeyKramer.compare(data); assertArrayEquals(new double[]{1, 2, 3}, data[0]);
    }

    @Test void translationAndPositiveScalingPreserveInferencesAndTransformIntervals() {
        double[][] x = {{1, 2, 4}, {4, 5, 6}, {8, 7, 9, 10}};
        double[][] y = new double[x.length][];
        for (int i = 0; i < x.length; i++) { y[i] = x[i].clone(); for (int j = 0; j < y[i].length; j++) y[i][j] = 100 + 7*y[i][j]; }
        var a = TukeyKramer.compare(x); var b = TukeyKramer.compare(y);
        for (int i = 0; i < a.comparisons().size(); i++) {
            var first = a.comparisons().get(i); var second = b.comparisons().get(i);
            assertEquals(first.adjustedPValue(), second.adjustedPValue(), 2e-12);
            assertEquals(7*first.difference(), second.difference(), 2e-12);
            assertEquals(7*first.lowerConfidenceLimit(), second.lowerConfidenceLimit(), 2e-10);
            assertEquals(7*first.upperConfidenceLimit(), second.upperConfidenceLimit(), 2e-10);
        }
    }

    @Test void rejectsMissingValuesAndDegenerateOrUnsupportedResidualFits() {
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compare(new double[][]{{1, Double.NaN}, {2, 3}}));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compare(new double[][]{{1, Double.POSITIVE_INFINITY}, {2, 3}}));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compare(new double[][]{{1, 1}, {3, 3}}));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compare(new double[][]{{1}, {2, 3}}));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compare(new double[][]{{}, {2, 3, 4}}));
    }

    @Test void rejectsInvalidSummaryInputsAndUnrepresentableOutput() {
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compareMeans(new double[]{1, 2}, new int[]{3}, 1, 4, .95));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compareMeans(new double[]{1, 2}, new int[]{3, 0}, 1, 4, .95));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compareMeans(new double[]{1, 2}, new int[]{3, 3}, 1, Double.NaN, .95));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compareMeans(new double[]{1, 2}, new int[]{3, 3}, Double.POSITIVE_INFINITY, 4, .95));
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compareMeans(new double[]{1, 2}, new int[]{3, 3}, 1, 4, 1));
        assertThrows(ArithmeticException.class, () -> TukeyKramer.compareMeans(new double[]{-Double.MAX_VALUE, Double.MAX_VALUE}, new int[]{3, 3}, 1, 4, .95));
    }

    @Test void refusesAnOversizedCompleteFamilyBeforeProducingAPrefix() {
        assertThrows(IllegalArgumentException.class, () -> TukeyKramer.compareMeans(new double[1415], new int[1415], 1, 100, .95));
    }

    private static BufferedReader reader(String name) {
        var input = TukeyKramerTest.class.getResourceAsStream("/org/jlinalg/stats/tukey/" + name);
        assertNotNull(input); return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
}
