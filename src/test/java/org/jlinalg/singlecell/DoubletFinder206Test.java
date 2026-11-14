/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Complete native-scope outputs from 388 original R executions, with full original RDS retained. */
class DoubletFinder206Test {
    private static Map<String, byte[]> originalFiles() throws Exception {
        byte[] archive;
        try (InputStream stream = DoubletFinder206Test.class.getResourceAsStream("/doubletfinder206-native-reference.zip")) {
            assertNotNull(stream);
            archive = stream.readAllBytes();
        }
        assertEquals("78b631e06d71cfdba68ad1828d51cc9c552415cde47b80fb9fea4c224d97d157",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)));
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                assertFalse(entry.isDirectory());
                assertNull(files.put(entry.getName(), input.readAllBytes()), entry.getName());
            }
        }
        assertEquals(7623, files.size());
        return files;
    }

    private static List<String> lines(Map<String, byte[]> files, String path) {
        byte[] raw = files.get(path);
        assertNotNull(raw, path);
        return new String(raw, StandardCharsets.UTF_8).lines().toList();
    }

    private static String label(String value) {
        return value.equals("!NA") ? null
                : new String(HexFormat.of().parseHex(value), StandardCharsets.UTF_8);
    }

    private static double number(String value) {
        return switch (value) {
            case "NA", "NaN" -> Double.NaN;
            case "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(value);
        };
    }

    private static void numeric(double expected, double actual, double tolerance, String position) {
        if (Double.isNaN(expected)) assertTrue(Double.isNaN(actual), position);
        else if (Double.isInfinite(expected)) assertEquals(expected, actual, position);
        else {
            assertTrue(Double.isFinite(actual), position);
            assertEquals(expected, actual, tolerance, position);
        }
    }

    @Test void everyOriginalNativeScopeValueAndOriginalErrorAgrees() throws Exception {
        Map<String, byte[]> files = originalFiles();
        List<String> cases = lines(files, "references/cases.tsv");
        assertEquals("kind\tname\tauc\tn\texpected\tstatus", cases.get(0));
        int successful = 0;
        int originalErrors = 0;
        int compared = 0;
        for (String line : cases.subList(1, cases.size())) {
            String[] fields = line.split("\t", -1);
            String kind = fields[0];
            String name = fields[1];
            String prefix = "references/" + name + "/";
            boolean hasAuc = fields[2].equals("1");
            int population = Integer.parseInt(fields[3]);
            int expectedDoublets = Integer.parseInt(fields[4]);
            boolean error = fields[5].equals("ERROR");
            assertNotNull(files.get(prefix + "complete-original-return-and-conditions.rds"));
            if (kind.equals("homotypic")) {
                String[] annotations = lines(files, prefix + "annotations.tsv").stream()
                        .map(DoubletFinder206Test::label).toArray(String[]::new);
                assertEquals(population, annotations.length, name);
                double expected = ByteBuffer.wrap(files.get(prefix + "value.bin"))
                        .order(ByteOrder.LITTLE_ENDIAN).getDouble();
                numeric(expected, DoubletFinder206.modelHomotypic(annotations), 1e-14, name);
                compared++;
            } else if (kind.equals("pk")) {
                List<String> input = lines(files, prefix + "sweep.tsv");
                assertEquals(population, input.size(), name);
                String[] pK = new String[population];
                double[] bc = new double[population];
                double[] auc = hasAuc ? new double[population] : null;
                for (int i = 0; i < population; i++) {
                    String[] row = input.get(i).split("\t", -1);
                    pK[i] = label(row[0]);
                    bc[i] = number(row[1]);
                    if (hasAuc) auc[i] = number(row[2]);
                }
                if (error) {
                    assertThrows(IllegalArgumentException.class, () -> DoubletFinder206.findPk(pK, bc, auc), name);
                    originalErrors++;
                    continue;
                }
                DoubletFinder206.PkTable actual = DoubletFinder206.findPk(pK, bc, auc);
                List<String> table = lines(files, prefix + "table.tsv");
                assertEquals(String.join("\t", actual.columnNames()), table.get(0), name);
                assertEquals(table.size() - 1, actual.rows().size(), name);
                boolean nonFinite = false;
                for (int i = 1; i < table.size(); i++) {
                    String[] row = table.get(i).split("\t", -1);
                    DoubletFinder206.PkMetric value = actual.rows().get(i - 1);
                    assertEquals(Integer.parseInt(row[0]), value.paramId(), name);
                    assertEquals(label(row[1]), value.pK(), name);
                    if (!hasAuc) assertNull(value.meanAuc(), name);
                    double[] values = hasAuc
                            ? new double[]{value.meanAuc(), value.meanBc(), value.varianceBc(), value.bcMetric()}
                            : new double[]{value.meanBc(), value.varianceBc(), value.bcMetric()};
                    for (int j = 0; j < values.length; j++) {
                        double expected = number(row[j + 2]);
                        numeric(expected, values[j], 1e-12 * Math.max(Math.abs(expected), 1e-300),
                                name + " row=" + i + " field=" + j);
                        nonFinite |= !Double.isFinite(values[j]);
                        compared++;
                    }
                }
                assertEquals(nonFinite, actual.requiresNonFiniteReview(), name);
            } else {
                assertEquals("classify", kind);
                double[] scores = lines(files, prefix + "scores.tsv").stream()
                        .mapToDouble(DoubletFinder206Test::number).toArray();
                assertEquals(population, scores.length, name);
                String[] expected = lines(files, prefix + "classifications.tsv").toArray(String[]::new);
                assertArrayEquals(expected, DoubletFinder206.classifyExistingPann(scores, expectedDoublets), name);
                assertNotNull(files.get(prefix + "complete-original-Seurat-return.rds"));
                compared += population;
            }
            successful++;
        }
        assertEquals(388, cases.size() - 1);
        assertEquals(378, successful);
        assertEquals(10, originalErrors);
        assertEquals(3535, compared);
    }

    @Test void declaredInputDomainAndImmutableOutputAreEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> DoubletFinder206.classifyExistingPann(new double[]{.5}, 2));
        assertThrows(IllegalArgumentException.class,
                () -> DoubletFinder206.classifyExistingPann(new double[]{}, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DoubletFinder206.findPk(new String[]{"x"}, new double[]{.2}, new double[]{}));
        String[] labels = {"x", "x"};
        double[] values = {.2, .6};
        DoubletFinder206.PkTable result = DoubletFinder206.findPk(labels, values, null);
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertArrayEquals(new String[]{"x", "x"}, labels);
        assertTrue(Arrays.equals(new double[]{.2, .6}, values));
    }
}
