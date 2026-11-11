/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class Deseq2IndependentFilteringOriginalTest {
    private static final String SHA = "d369ccc9cf4b464c05a10218e308f3ca616cf440e2c99ed75d94c0c946202781";
    @TestFactory Stream<DynamicTest> allOriginalFamiliesAndSelectionDecisions() throws Exception {
        Map<String, String> files = archive(); List<String> names = files.get("cases.txt").lines().toList(); assertEquals(12, names.size());
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            List<String> input = files.get(name + "/input.tsv").lines().toList(); int n = input.size() - 1; double[] means = new double[n], p = new double[n];
            for (int i = 0; i < n; i++) { String[] f = input.get(i + 1).split("\t"); means[i] = number(f[0]); p[i] = number(f[1]); }
            var result = Deseq2IndependentFiltering.filter(means, p, Double.parseDouble(files.get(name + "/alpha.txt").trim()));
            List<String> curve = files.get(name + "/curve.tsv").lines().toList(); assertEquals(51, curve.size());
            double[] theta = result.theta(), cutoff = result.cutoffs(), smooth = result.smoothed(); int[] rejected = result.rejections();
            for (int column = 0; column < 50; column++) {
                String[] f = curve.get(column + 1).split("\t");
                close(number(f[0]), theta[column]); close(number(f[1]), cutoff[column]); assertEquals(Integer.parseInt(f[2]), rejected[column]); close(number(f[3]), smooth[column]);
            }
            String[] choice = files.get(name + "/selected.tsv").lines().toList().get(1).split("\t");
            assertEquals(Integer.parseInt(choice[0]), result.selectedIndex()); close(number(choice[1]), result.selectedTheta()); close(number(choice[2]), result.selectedCutoff());
            assertEquals(!choice[3].equals("NOT_CALCULATED"), result.selectionThreshold().isPresent());
            if (result.selectionThreshold().isPresent()) close(number(choice[3]), result.selectionThreshold().getAsDouble());
            List<String> expected = files.get(name + "/adjusted.tsv").lines().toList(); assertEquals(n + 1, expected.size()); double[] adjusted = result.adjusted();
            for (int i = 0; i < n; i++) close(number(expected.get(i + 1)), adjusted[i]);
            List<String> matrix = files.get(name + "/all-families.tsv").lines().toList(); assertEquals(n + 1, matrix.size()); double[][] all = new double[n][50];
            for (int i = 0; i < n; i++) { String[] f = matrix.get(i + 1).split("\t", -1); assertEquals(50, f.length); for (int j = 0; j < 50; j++) all[i][j] = number(f[j]); }
            for (int j = 0; j < 50; j++) {
                double[] family = Deseq2IndependentFiltering.adjustAtCutoff(means, p, cutoff[j]);
                for (int i = 0; i < n; i++) close(all[i][j], family[i]);
            }
        }));
    }
    @Test void missingKindsAndOutputIsolationRemainExplicit() {
        double[] means = {1, 2, 3}, p = {0.1, Deseq2SizeFactors.MISSING, Double.NaN};
        var result = Deseq2IndependentFiltering.filter(means, p, 0.1); double[] adjusted = result.adjusted();
        assertTrue(Deseq2SizeFactors.isMissing(adjusted[1])); assertTrue(Double.isNaN(adjusted[2])); assertFalse(Deseq2SizeFactors.isMissing(adjusted[2]));
        adjusted[0] = 999; assertNotEquals(999, result.adjusted()[0]); assertArrayEquals(new double[]{1, 2, 3}, means);
        assertThrows(IllegalArgumentException.class, () -> Deseq2IndependentFiltering.filter(means, new double[]{0.1}, 0.1));
        assertThrows(IllegalArgumentException.class, () -> Deseq2IndependentFiltering.filter(new double[]{1}, new double[]{Double.POSITIVE_INFINITY}, 0.1));
        assertThrows(CancellationException.class, () -> Deseq2IndependentFiltering.filter(means, p, 0.1, Deseq2IndependentFiltering.Limits.defaults(), () -> true));
    }
    private static double number(String value) { return value.equals("NA") ? Deseq2SizeFactors.MISSING : value.equals("NaN") ? Double.NaN : Double.parseDouble(value); }
    private static void close(double expected, double actual) {
        if (Double.isNaN(expected)) { assertTrue(Double.isNaN(actual)); assertEquals(Deseq2SizeFactors.isMissing(expected), Deseq2SizeFactors.isMissing(actual)); }
        else {
            assertTrue(Double.isFinite(actual));
            if (expected == 0 || actual == 0) assertEquals(expected, actual, 0.0, "source zero preserved");
            else assertEquals(expected, actual, Math.max(16 * Math.ulp(expected), 4e-12 * Math.abs(expected)));
        }
    }
    private Map<String, String> archive() throws Exception {
        byte[] raw;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/sequence/deseq126-filter/reference.zip")) { assertNotNull(input); raw = input.readAllBytes(); }
        assertEquals(SHA, java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var result = new HashMap<String, String>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(raw))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) if (!entry.isDirectory()) assertNull(result.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        return result;
    }
}
