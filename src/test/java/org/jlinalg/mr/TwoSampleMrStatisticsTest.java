/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mr;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jlinalg.mr.TwoSampleMrStatistics.Kind;
import org.jlinalg.mr.TwoSampleMrStatistics.Method;
import org.jlinalg.mr.TwoSampleMrStatistics.Result;
import org.jlinalg.mr.TwoSampleMrStatistics.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class TwoSampleMrStatisticsTest {
    private static final Fixture FIXTURE = Fixture.read();

    static Stream<Arguments> originals() {
        return FIXTURE.cases.keySet().stream().flatMap(id -> FIXTURE.methods.stream()
            .map(method -> Arguments.of(id, method)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("originals")
    void matchesUnmodifiedOriginalSource(String id, String method) {
        Data data = FIXTURE.cases.get(id); String key = id + "|" + method;
        double[] x = data.vector(0), y = data.vector(1), sx = data.vector(2), sy = data.vector(3);
        double[] originalX = x.clone(), originalY = y.clone();
        if (method.equals("mr_wald_ratio") && x.length == 0) {
            assertEquals("EMPTY", FIXTURE.statistics.get(key).get("b").kind);
            assertThrows(IllegalArgumentException.class,
                () -> TwoSampleMrStatistics.evaluate(Method.MR_WALD_RATIO, x, y, sx, sy));
            return;
        }
        if (FIXTURE.errors.containsKey(key)) {
            assertThrows(IllegalArgumentException.class,
                () -> evaluate(method, data, x, y, sx, sy));
            return;
        }
        Result actual = evaluate(method, data, x, y, sx, sy);
        Map<String, Expected> expected = FIXTURE.statistics.get(key);
        assertEquals(expected.keySet(), actual.statistics().keySet(), key + " field population");
        expected.forEach((field, value) -> compare(value, actual.statistics().get(field), key + " " + field));
        assertArrayEquals(originalX, x); assertArrayEquals(originalY, y);
    }

    private static Result evaluate(String method, Data data, double[] x, double[] y, double[] sx, double[] sy) {
        if (method.equals("mr_weighted_median") || method.equals("mr_simple_median")) {
            Draws draws = data.draws.getOrDefault(method, new Draws(data.bootstrap, x.length));
            return TwoSampleMrStatistics.medianFromDraws(method.equals("mr_weighted_median"),
                x, y, sx, sy, draws.x, draws.y);
        }
        return TwoSampleMrStatistics.evaluate(Method.valueOf(method.toUpperCase(java.util.Locale.ROOT)), x, y, sx, sy);
    }

    private static void compare(Expected expected, Value actual, String label) {
        assertEquals(Kind.valueOf(expected.kind), actual.kind(), label + " kind");
        if (actual.kind() == Kind.FINITE) {
            double tolerance = Math.max(64 * Math.ulp(expected.value), Math.abs(expected.value) * 2e-9);
            assertEquals(expected.value, actual.value(), tolerance, label);
        }
    }

    @Test void weightedPointMatchesOriginalIncludingExposureUncertainty() {
        for (var entry : FIXTURE.cases.entrySet()) {
            Data d = entry.getValue(); String key = entry.getKey() + "|mr_weighted_median";
            if (FIXTURE.errors.containsKey(key)) continue;
            compare(FIXTURE.statistics.get(key).get("b"), TwoSampleMrStatistics.weightedMedianPoint(
                d.vector(0), d.vector(1), d.vector(2), d.vector(3)), key + " point");
        }
    }
    @Test void tInferenceAndUncorrectedMreRemainDistinctFromLegacyRules() {
        Data data = FIXTURE.cases.get("underdispersion");
        Result corrected = TwoSampleMrStatistics.evaluate(Method.MR_IVW, data.vector(0), data.vector(1), data.vector(2), data.vector(3));
        Result uncorrected = TwoSampleMrStatistics.evaluate(Method.MR_IVW_MRE, data.vector(0), data.vector(1), data.vector(2), data.vector(3));
        assertTrue(uncorrected.statistics().get("se").value() < corrected.statistics().get("se").value());
        Data over = FIXTURE.cases.get("overdispersion");
        List<HarmonizedInstrument> instruments = new ArrayList<>();
        for (int i = 0; i < over.rows.size(); i++) {
            double[] row = over.rows.get(i);
            instruments.add(new HarmonizedInstrument("v" + i, "A", "C", row[0], row[2], row[1], row[3], .2, .2, false, false));
        }
        double old = MendelianRandomization.egger(instruments, .95).slope().pValue();
        double current = TwoSampleMrStatistics.evaluate(Method.MR_EGGER_REGRESSION,
            over.vector(0), over.vector(1), over.vector(2), over.vector(3)).statistics().get("pval").value();
        assertTrue(Math.abs(old - current) > 1e-5, "legacy normal and source Student t rules must not be conflated");
    }
    @Test void sourceUndefinedZeroResidualDivisionIsNotRepaired() {
        Data d = FIXTURE.cases.get("zero_outcome");
        Result result = TwoSampleMrStatistics.evaluate(Method.MR_IVW_FE, d.vector(0), d.vector(1), d.vector(2), d.vector(3));
        assertEquals(Kind.NAN, result.statistics().get("se").kind());
        assertTrue(result.warnings().stream().anyMatch(s -> s.startsWith("NO_COMPLETE_RESULT:")));
    }
    @Test void precisionWarningRetainsExactFitStatistics() {
        Data d = FIXTURE.cases.get("perfect");
        Result result = TwoSampleMrStatistics.evaluate(Method.MR_UWR, d.vector(0), d.vector(1), d.vector(2), d.vector(3));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("Essentially perfect fit")));
        compare(FIXTURE.statistics.get("perfect|mr_uwr").get("Q"), result.statistics().get("Q"), "perfect-fit retained Q");
    }
    @Test void missingValuesAndInvalidStandardErrorsAreRefusedWithoutOmission() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> TwoSampleMrStatistics.evaluate(Method.MR_IVW,
                new double[] {.1, bad, .3}, new double[] {.2, .3, .4}, new double[] {.1, .1, .1}, new double[] {.1, .1, .1}));
        }
        assertThrows(IllegalArgumentException.class, () -> TwoSampleMrStatistics.evaluate(Method.MR_IVW,
            new double[] {.1, .2}, new double[] {.2, .3}, new double[] {.1, .1}, new double[] {.1, 0}));
    }
    @Test void scalarKindsAndResultContainersAreOwned() {
        assertNotEquals(Value.na().kind(), Value.of(Double.NaN).kind());
        assertEquals(Kind.POSITIVE_INFINITY, Value.of(Double.POSITIVE_INFINITY).kind());
        assertThrows(IllegalArgumentException.class, () -> new Value(Kind.FINITE, Double.NaN));
        Result result = TwoSampleMrStatistics.evaluate(Method.MR_WALD_RATIO,
            new double[] {.1}, new double[] {.2}, new double[] {.01}, new double[] {.02});
        assertThrows(UnsupportedOperationException.class, () -> result.statistics().clear());
    }
    @Test void bootstrapDimensionsAreNeverRepaired() {
        Data d = FIXTURE.cases.get("n3");
        assertThrows(IllegalArgumentException.class, () -> TwoSampleMrStatistics.medianFromDraws(true,
            d.vector(0), d.vector(1), d.vector(2), d.vector(3), new double[][] {{1, 2}}, new double[][] {{1, 2}}));
    }
    @Test void sourceCollinearityRetainsUnavailableFieldsAndWarning() {
        Data d = FIXTURE.cases.get("collinear_egger");
        Result result = TwoSampleMrStatistics.evaluate(Method.MR_EGGER_REGRESSION,
            d.vector(0), d.vector(1), d.vector(2), d.vector(3));
        assertTrue(result.statistics().values().stream().allMatch(v -> v.kind() == Kind.NA));
        assertFalse(result.warnings().isEmpty());
    }

    private record Expected(String kind, double value) { }
    private static final class Draws {
        final double[][] x, y;
        Draws(int replicates, int variants) { x = new double[replicates][variants]; y = new double[replicates][variants]; }
    }
    private static final class Data {
        final int bootstrap; final List<double[]> rows = new ArrayList<>(); final Map<String, Draws> draws = new LinkedHashMap<>();
        Data(int bootstrap) { this.bootstrap = bootstrap; }
        double[] vector(int column) { return rows.stream().mapToDouble(r -> r[column]).toArray(); }
    }
    private static final class Fixture {
        final Map<String, Data> cases = new LinkedHashMap<>();
        final Map<String, Map<String, Expected>> statistics = new LinkedHashMap<>();
        final Map<String, String> errors = new LinkedHashMap<>();
        final List<String> methods = List.of("mr_wald_ratio", "mr_meta_fixed_simple", "mr_ivw", "mr_ivw_fe", "mr_ivw_mre", "mr_uwr", "mr_egger_regression", "mr_sign", "mr_weighted_median", "mr_simple_median");
        static Fixture read() {
            try (InputStream resource = TwoSampleMrStatisticsTest.class.getResourceAsStream("twosamplemr-0.7.14-native.zip")) {
                if (resource == null) throw new IOException("missing original fixture");
                Map<String, List<String[]>> files = new LinkedHashMap<>();
                try (ZipInputStream zip = new ZipInputStream(resource)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        String text = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                        files.put(entry.getName(), text.lines().skip(1).map(line -> line.split("\t", -1)).toList());
                    }
                }
                Fixture fixture = new Fixture();
                for (String[] row : files.get("cases.tsv")) fixture.cases.put(row[0], new Data(Integer.parseInt(row[1])));
                for (String[] row : files.get("input.tsv")) fixture.cases.get(row[0]).rows.add(
                    Arrays.stream(row).skip(2).mapToDouble(Double::parseDouble).toArray());
                for (String[] row : files.get("statistics.tsv")) fixture.statistics.computeIfAbsent(row[0] + "|" + row[1], k -> new LinkedHashMap<>())
                    .put(row[2], new Expected(row[3], row[3].equals("FINITE") ? Double.parseDouble(row[4]) : Double.NaN));
                for (String[] row : files.get("errors.tsv")) fixture.errors.put(row[0] + "|" + row[1], row[2]);
                for (String[] row : files.get("draws.tsv")) {
                    Data data = fixture.cases.get(row[0]); Draws draws = data.draws.computeIfAbsent(row[1], k -> new Draws(data.bootstrap, data.rows.size()));
                    int b = Integer.parseInt(row[2]) - 1, j = Integer.parseInt(row[3]) - 1;
                    draws.x[b][j] = Double.parseDouble(row[4]); draws.y[b][j] = Double.parseDouble(row[5]);
                }
                return fixture;
            } catch (IOException error) { throw new IllegalStateException("original fixture could not be read", error); }
        }
    }
}
