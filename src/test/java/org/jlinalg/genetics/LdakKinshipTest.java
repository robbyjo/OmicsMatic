/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LdakKinshipTest {
    @TempDir Path root;
    private static byte[] resource(String name) throws IOException {
        try (var input = LdakKinshipTest.class.getResourceAsStream("/org/jlinalg/genetics/ldak52/" + name)) {
            if (input == null) throw new IOException("Missing original-runtime fixture " + name);
            return input.readAllBytes();
        }
    }
    private LdakBedDataset open() throws Exception {
        Path prefix = root.resolve("test");
        for (String suffix : new String[]{"bed", "bim", "fam"})
            Files.write(Path.of(prefix + "." + suffix), resource("test." + suffix));
        var ids = new HashSet<String>();
        for (String line : new String(resource("test.bim"), StandardCharsets.UTF_8).lines().toList())
            ids.add(line.split("\\s+")[1]);
        return LdakBedDataset.open(prefix, ids);
    }
    private Map<String, Double> weights() throws IOException {
        var result = new HashMap<String, Double>();
        for (String line : new String(resource("weights.txt"), StandardCharsets.UTF_8).lines().toList()) {
            String[] fields = line.split("\\s+");
            result.put(fields[0], Double.valueOf(fields[1]));
        }
        return result;
    }
    private static LdakKinship.Options options(double power, boolean hwe, int block) {
        return new LdakKinship.Options(power, hwe, block, 10_000_000);
    }

    @ParameterizedTest
    @CsvSource({"-1,YES", "-1,NO", "-0.25,YES", "-0.25,NO", "0,YES", "0,NO", "1,YES", "1,NO"})
    void agreesWithIndependentOriginalBinaryAcrossPowersAndStandardizations(String power, String hwe) throws Exception {
        try (var data = open()) {
            var result = LdakKinship.calculate(data, options(Double.parseDouble(power), hwe.equals("YES"), 3), weights());
            String folder = "power_" + power + "_hwe_" + hwe + "/";
            ByteBuffer original = ByteBuffer.wrap(resource(folder + "result.grm.bin")).order(ByteOrder.LITTLE_ENDIAN);
            double[] nativeMatrix = result.matrix();
            assertEquals(9, result.samples().size());
            assertEquals(7, result.predictorsUsed());
            assertEquals(12, result.predictors().size());
            for (int i = 0; i < 9; i++) for (int j = 0; j <= i; j++) {
                double expected = original.getFloat();
                assertEquals(expected, nativeMatrix[i * 9 + j], 2e-7 + 2e-7 * Math.abs(expected), "Original lower-triangle entry " + i + "," + j);
                assertEquals(nativeMatrix[i * 9 + j], nativeMatrix[j * 9 + i], 1e-14);
            }
            assertFalse(original.hasRemaining());
            double trace = 0;
            for (int i = 0; i < 9; i++) trace += nativeMatrix[i * 9 + i];
            assertEquals(9, trace, 2e-13);
            String adjust = new String(resource(folder + "result.grm.adjust"), StandardCharsets.UTF_8);
            String denominator = adjust.lines().filter(line -> line.startsWith("Denominator ")).findFirst().orElseThrow().split(" ")[1];
            assertEquals(Double.parseDouble(denominator), result.denominator(), 5.01e-5);
            Map<String, LdakKinship.Predictor> detailById = new HashMap<>();
            for (var p : result.predictors()) detailById.put(p.id(), p);
            for (String line : new String(resource(folder + "result.grm.details"), StandardCharsets.UTF_8).lines().skip(1).toList()) {
                String[] fields = line.split("\\s+");
                var p = detailById.get(fields[0]);
                assertEquals(LdakKinship.Status.USED, p.status());
                assertEquals(Double.parseDouble(fields[2]), p.mean(), 5.01e-7);
                assertEquals(Double.parseDouble(fields[3]), p.scaling(), 5.01e-7);
                assertEquals(Double.parseDouble(fields[4]), p.weight());
            }
        }
    }

    @Test void recordsZeroWeightMissingAndConstantPredictorsWithoutInventedImputationVariance() throws Exception {
        try (var data = open()) {
            var result = LdakKinship.calculate(data, options(-1, false, 2), weights());
            Map<String, LdakKinship.Predictor> byId = new HashMap<>();
            for (var p : result.predictors()) byId.put(p.id(), p);
            assertEquals(LdakKinship.Status.NO_CALLS, byId.get("all_missing").status());
            assertTrue(Double.isNaN(byId.get("all_missing").mean()));
            for (String id : new String[]{"monomorphic", "fixed_het", "one_call"})
                assertEquals(LdakKinship.Status.CONSTANT, byId.get(id).status());
            assertEquals(LdakKinship.Status.ZERO_WEIGHT, byId.get("zero_weight").status());
            assertEquals(5, byId.get("sparse").calledSamples());
            assertEquals(4.0 / 9, byId.get("sparse").imputedVariance(), 1e-15);
            assertEquals(8, result.denominator(), 2e-14);
        }
    }

    @Test void blockSizeAndCommonWeightScaleDoNotChangeTheModelAndResultsAreImmutable() throws Exception {
        try (var data = open()) {
            var weights = weights();
            var result = LdakKinship.calculate(data, options(-.25, true, 1), weights);
            var doubled = new HashMap<String, Double>();
            weights.forEach((id, value) -> doubled.put(id, value * 2));
            var other = LdakKinship.calculate(data, options(-.25, true, 1000), doubled);
            assertArrayEquals(result.matrix(), other.matrix(), 2e-14);
            assertEquals(result.denominator() * 2, other.denominator(), 2e-14);
            double[] copy = result.matrix();
            copy[0] = -999;
            assertTrue(result.matrix()[0] >= 0);
            assertEquals(result.matrix()[0], result.value(0, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> result.value(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> result.value(0, result.samples().size()));
            assertThrows(UnsupportedOperationException.class, () -> result.samples().clear());
            assertThrows(UnsupportedOperationException.class, () -> result.predictors().clear());
        }
    }

    @Test void refusesInvalidWeightsExhaustedPredictorsAndNumericBudget() throws Exception {
        try (var data = open()) {
            assertThrows(IllegalArgumentException.class, () -> LdakKinship.calculate(data, options(0, true, 2), Map.of("unknown", 1.0)));
            for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
                assertThrows(IllegalArgumentException.class, () -> LdakKinship.calculate(data, options(0, true, 2), Map.of("mixed", invalid)));
            assertThrows(IllegalArgumentException.class, () -> LdakKinship.calculate(data, options(0, true, 2), Map.of()));
            assertThrows(IllegalArgumentException.class, () -> LdakKinship.calculate(data, options(0, true, 2), Map.of("all_missing", 1.0)));
            assertThrows(IllegalArgumentException.class, () -> LdakKinship.calculate(data, new LdakKinship.Options(0, true, 2, 1), null));
            assertThrows(IllegalArgumentException.class, () -> LdakKinship.calculate(data, options(100_000, true, 2), null));
        }
        assertThrows(IllegalArgumentException.class, () -> options(Double.NaN, true, 1));
        assertThrows(IllegalArgumentException.class, () -> options(0, true, 0));
    }
}
