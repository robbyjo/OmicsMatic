/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell.scrattch;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ScrattchMapping02Test {
    private static final String FIXTURE_SHA =
            "cba3f762715b7e14d01905abdd591054fd5ac61a5d84895631c4a40df9a1a8fc";

    @Test
    void comparesEveryOriginalDenseMatrixReturnWarningErrorAndName() throws Exception {
        byte[] archive;
        try (var in = getClass().getResourceAsStream("/scrattchmapping02-native-reference.zip")) {
            assertNotNull(in); archive = in.readAllBytes();
        }
        assertEquals(FIXTURE_SHA, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)));
        Map<String, byte[]> files = new HashMap<>();
        var portableNames = new HashSet<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                assertFalse(entry.isDirectory());
                assertTrue(portableNames.add(entry.getName().toLowerCase(Locale.ROOT)), "Case-insensitive fixture collision");
                assertNull(files.put(entry.getName(), zip.readAllBytes()), "Duplicate fixture entry");
            }
        }
        int cases = 0, successful = 0, errors = 0, warnings = 0, values = 0;
        for (String line : text(files, "cases.tsv").lines().skip(1).toList()) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\t", -1);
            String prefix = fields[0] + "/";
            int rows = Integer.parseInt(fields[1]), columns = Integer.parseInt(fields[2]);
            String by = decode(fields[3]);
            double[] input = numbers(files, prefix + "input.bin", rows * columns);
            double[][] matrix = matrix(input, rows, columns);
            String[] rowNames = names(files, prefix + "row-names.tsv");
            String[] columnNames = names(files, prefix + "column-names.tsv");
            cases++;
            if (fields[4].equals("ERROR")) {
                var error = assertThrows(ScrattchMapping02.OriginalNormalizationException.class,
                        () -> ScrattchMapping02.l2norm(matrix, columns, rowNames, columnNames, by), fields[0]);
                assertEquals(decode(text(files, prefix + "error.tsv").strip()), error.getMessage());
                errors++;
            } else {
                var result = ScrattchMapping02.l2norm(matrix, columns, rowNames, columnNames, by);
                assertEquals(rows, result.values().length, fields[0]);
                assertEquals(columns, result.columns(), fields[0]);
                assertArrayEquals(rowNames, result.rowNames());
                assertArrayEquals(columnNames, result.columnNames());
                if (rowNames != null) assertNotSame(rowNames, result.rowNames());
                if (columnNames != null) assertNotSame(columnNames, result.columnNames());
                List<String> expectedWarnings = text(files, prefix + "warnings.tsv").lines().map(ScrattchMapping02Test::decode).toList();
                assertEquals(expectedWarnings, result.warnings(), fields[0]);
                assertEquals(Integer.parseInt(fields[5]), result.warnings().size());
                assertEquals(0, Integer.parseInt(fields[6]));
                warnings += result.warnings().size();
                double[] expected = numbers(files, prefix + "result.bin", rows * columns);
                boolean nonfinite = false;
                for (int r = 0; r < rows; r++) {
                    assertEquals(columns, result.values()[r].length);
                    for (int c = 0; c < columns; c++) {
                        double wanted = expected[r * columns + c], actual = result.values()[r][c];
                        if (Double.isNaN(wanted)) assertTrue(Double.isNaN(actual), fields[0]);
                        else if (Double.isInfinite(wanted)) assertEquals(wanted, actual, fields[0]);
                        else if (wanted == 0) assertEquals(Double.doubleToRawLongBits(wanted), Double.doubleToRawLongBits(actual), fields[0]);
                        else assertEquals(wanted, actual, 1e-14 * Math.max(Math.abs(wanted), 1e-300), fields[0]);
                        nonfinite |= !Double.isFinite(wanted); values++;
                    }
                }
                assertEquals(nonfinite, result.requiresNonFiniteReview(), fields[0]);
                successful++;
            }
            // No operation, including an original error, may mutate caller data.
            for (int r = 0; r < rows; r++) for (int c = 0; c < columns; c++)
                assertEquals(Double.doubleToRawLongBits(input[r * columns + c]), Double.doubleToRawLongBits(matrix[r][c]));
        }
        assertEquals(318, cases); assertEquals(303, successful); assertEquals(15, errors); assertEquals(52, warnings);
        System.out.println("ORIGINAL_SCRATTCH02_L2_COMPARISONS cases=" + cases + " values=" + values
                + " warnings=" + warnings + " original_errors=" + errors);
    }

    @Test
    void refusesMalformedJavaMatricesWithoutNormalizingOrRepairingThem() {
        assertThrows(IllegalArgumentException.class, () -> ScrattchMapping02.l2norm(new double[][]{{1}, {1, 2}}, 1, "row"));
        assertThrows(IllegalArgumentException.class, () -> ScrattchMapping02.l2norm(new double[0][], -1, "column"));
        assertThrows(IllegalArgumentException.class, () -> ScrattchMapping02.l2norm(new double[][]{{1}}, 1, new String[0], null, "row"));
        assertThrows(NullPointerException.class, () -> ScrattchMapping02.l2norm(new double[][]{{1}}, 1, null));
    }

    private static String text(Map<String, byte[]> files, String name) {
        byte[] raw = files.get(name); assertNotNull(raw, name); return new String(raw, StandardCharsets.UTF_8);
    }
    private static String decode(String hex) {
        return hex.equals("!NA") ? null : new String(HexFormat.of().parseHex(hex), StandardCharsets.UTF_8);
    }
    private static String[] names(Map<String, byte[]> files, String name) {
        List<String> lines = text(files, name).lines().toList();
        if (lines.get(0).equals("ABSENT")) return null;
        assertEquals("PRESENT", lines.get(0));
        return lines.stream().skip(1).map(ScrattchMapping02Test::decode).toArray(String[]::new);
    }
    private static double[] numbers(Map<String, byte[]> files, String name, int count) {
        byte[] raw = files.get(name); assertNotNull(raw, name); assertEquals(8 * count, raw.length);
        var bytes = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN); double[] values = new double[count];
        for (int i = 0; i < count; i++) values[i] = bytes.getDouble();
        return values;
    }
    private static double[][] matrix(double[] values, int rows, int columns) {
        double[][] result = new double[rows][columns];
        for (int r = 0; r < rows; r++) System.arraycopy(values, r * columns, result[r], 0, columns);
        return result;
    }
}
