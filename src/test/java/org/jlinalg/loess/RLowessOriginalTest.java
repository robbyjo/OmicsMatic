/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.loess;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class RLowessOriginalTest {
    private static final String SHA = "f479ad9c66ca61e55a5b2ff047584bfcb268928bbc94195888dc1182be9b2876";
    @TestFactory Stream<DynamicTest> completeOriginalR362Curves() throws Exception {
        Map<String, String> files = archive(); List<String> names = files.get("cases.txt").lines().toList(); assertEquals(17, names.size());
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            List<String> input = files.get(name + "/input.tsv").lines().toList(), expected = files.get(name + "/expected.tsv").lines().toList();
            double[] x = new double[input.size() - 1], y = new double[x.length]; double scale = 0;
            for (int i = 0; i < x.length; i++) { String[] f = input.get(i + 1).split("\t"); x[i] = Double.parseDouble(f[0]); y[i] = Double.parseDouble(f[1]); scale = Math.max(scale, Math.abs(y[i])); }
            String[] settings = files.get(name + "/options.tsv").lines().toList().get(1).split("\t");
            var fit = RLowess.fit(x, y, new RLowess.Options(Double.parseDouble(settings[0]), Integer.parseInt(settings[1]), Double.parseDouble(settings[2])));
            double[] actualX = fit.x(), fitted = fit.fitted(); int[] indices = fit.originalIndices(); assertEquals(expected.size(), fitted.length + 1);
            for (int i = 0; i < fitted.length; i++) {
                String[] f = expected.get(i + 1).split("\t"); double original = Double.parseDouble(f[1]);
                assertEquals(Double.doubleToLongBits(Double.parseDouble(f[0])), Double.doubleToLongBits(actualX[i]), "source sorted coordinate " + i);
                assertEquals(Integer.parseInt(f[2]), indices[i], "source occurrence " + i);
                assertTrue(Double.isFinite(fitted[i]));
                if (original == 0 || fitted[i] == 0) assertEquals(original, fitted[i], 0.0, "source zero preserved");
                else assertEquals(original, fitted[i], Math.max(16 * Math.ulp(original), 2e-13 * scale), "source fitted point " + i);
            }
        }));
    }
    @Test void completeOperationsRefuseInvalidDataAndCapacities() {
        var options = new RLowess.Options(0.5, 3, 0); double[] x = {0, 1, 2}, y = {1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> RLowess.fit(new double[]{0, Double.NaN}, new double[]{1, 2}, options));
        assertThrows(IllegalArgumentException.class, () -> RLowess.fit(x, y, options, new RLowess.Limits(2, 10000, 1000), () -> false));
        assertThrows(IllegalArgumentException.class, () -> RLowess.fit(x, y, options, new RLowess.Limits(3, 1, 1000), () -> false));
        assertThrows(CancellationException.class, () -> RLowess.fit(x, y, options, RLowess.Limits.defaults(), () -> true));
        assertArrayEquals(new double[]{0, 1, 2}, x); assertArrayEquals(new double[]{1, 2, 3}, y);
    }
    @Test void immutableOutputKeepsOriginalOccurrenceMapping() {
        var result = RLowess.fit(new double[]{2, 1, 1}, new double[]{4, 2, 3});
        assertArrayEquals(new int[]{1, 2, 0}, result.originalIndices());
        double[] fitted = result.fitted(); Arrays.fill(fitted, 999); assertNotEquals(999, result.fitted()[0]);
    }
    private Map<String, String> archive() throws Exception {
        byte[] raw;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/loess/r362-lowess/reference.zip")) { assertNotNull(input); raw = input.readAllBytes(); }
        assertEquals(SHA, java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var result = new HashMap<String, String>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(raw))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) if (!entry.isDirectory()) {
                assertNull(result.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8)), "duplicate reference member");
            }
        }
        return result;
    }
}
