/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell.scrattch;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ScrattchBigcat005Test {
    private static final String FIXTURE_SHA = "de1f616016df3e6b98b0907f32a00dd375b58686c0b4974eac46dbb1c61f42a1";

    @Test
    void comparesEveryOriginalDenseReturnBitNameDimensionAndFailure() throws Exception {
        byte[] archive;
        try (var in = getClass().getResourceAsStream("/scrattchbigcat005-native-marker-reference.zip")) {
            assertNotNull(in); archive = in.readAllBytes();
        }
        assertEquals(FIXTURE_SHA, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)));
        Map<String, byte[]> files = new HashMap<>();
        var portableNames = new HashSet<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                assertFalse(entry.isDirectory());
                assertTrue(portableNames.add(entry.getName().toLowerCase(Locale.ROOT)), "Case-insensitive fixture collision");
                assertNull(files.put(entry.getName(), zip.readAllBytes()), "Duplicate fixture member");
            }
        }
        int cases = 0, returned = 0, errors = 0, numbers = 0;
        Map<String, Map<Integer, String>> groupTypes = new HashMap<>();
        for (String line : text(files, "marker-group-types.tsv").lines().skip(1).toList()) {
            String[] fields = line.split("\t", -1); assertEquals(3, fields.length);
            assertTrue(fields[2].equals("NULL") || fields[2].equals("CHARACTER"));
            assertNull(groupTypes.computeIfAbsent(fields[0], k -> new HashMap<>())
                    .put(Integer.parseInt(fields[1]), fields[2]));
        }
        for (String line : text(files, "cases.tsv").lines().skip(1).toList()) {
            String[] fields = line.split("\t", -1);
            assertEquals(12, fields.length);
            String id = fields[0], prefix = id + "/";
            int rows = Integer.parseInt(fields[1]), columns = Integer.parseInt(fields[2]);
            boolean dimnames = fields[3].equals("1"), defaults = fields[5].equals("1");
            int groupCount = Integer.parseInt(fields[4]);
            double[] input = numbers(files, prefix + "input.bin", rows * columns);
            double[][] matrix = matrix(input, rows, columns);
            String[] rowNames = names(files, prefix + "input-rows.tsv");
            String[] columnNames = names(files, prefix + "input-columns.tsv");
            var means = new ScrattchBigcat005.DenseMatrix(matrix, columns, rowNames, columnNames, dimnames);
            double threshold = numbers(files, prefix + "threshold.bin", 1)[0];
            List<List<String>> groups = new ArrayList<>();
            for (int i = 0; i < groupCount; i++) groups.add(new ArrayList<>());
            for (String marker : text(files, prefix + "markers.tsv").lines().toList()) {
                String[] pair = marker.split("\t", -1);
                assertEquals(2, pair.length); groups.get(Integer.parseInt(pair[0])).add(decode(pair[1]));
            }
            String[][] markerGroups = groups.stream().map(g -> g.toArray(String[]::new)).toArray(String[][]::new);
            Map<Integer, String> types = groupTypes.getOrDefault(id, Map.of());
            assertEquals(groupCount, types.size(), id);
            for (int i = 0; i < markerGroups.length; i++) {
                assertNotNull(types.get(i), id);
                if (types.get(i).equals("NULL")) { assertEquals(0, markerGroups[i].length); markerGroups[i] = null; }
            }
            assertEquals(0, Integer.parseInt(fields[10]), "Original warnings must remain accounted for: " + id);
            assertEquals(0, Integer.parseInt(fields[11]), "Original messages must remain accounted for: " + id);
            assertTrue(text(files, prefix + "warnings.tsv").isEmpty());
            assertTrue(text(files, prefix + "messages.tsv").isEmpty());
            cases++;
            if (fields[6].equals("ERROR")) {
                var error = assertThrows(ScrattchBigcat005.OriginalMarkerException.class,
                        () -> {
                            if (defaults) ScrattchBigcat005.findDoubletByMarker(means);
                            else ScrattchBigcat005.findDoubletByMarker(means, markerGroups, threshold);
                        }, id);
                assertEquals(decode(text(files, prefix + "error.tsv").strip()), error.getMessage(), id);
                errors++;
            } else {
                assertEquals("RETURN", fields[6]);
                var result = defaults ? ScrattchBigcat005.findDoubletByMarker(means)
                        : ScrattchBigcat005.findDoubletByMarker(means, markerGroups, threshold);
                var actual = result.matrix();
                int outRows = Integer.parseInt(fields[7]), outColumns = Integer.parseInt(fields[8]);
                assertEquals(outRows, actual.values().length, id);
                assertEquals(outColumns, actual.columns(), id);
                assertEquals(fields[9].equals("1"), actual.dimnamesPresent(), id);
                assertArrayEquals(names(files, prefix + "output-rows.tsv"), actual.rowNames(), id);
                assertArrayEquals(names(files, prefix + "output-columns.tsv"), actual.columnNames(), id);
                double[] expected = numbers(files, prefix + "output.bin", outRows * outColumns);
                boolean nonfinite = false;
                for (int r = 0; r < outRows; r++) for (int c = 0; c < outColumns; c++) {
                    double wanted = expected[r * outColumns + c];
                    assertEquals(Double.doubleToRawLongBits(wanted), Double.doubleToRawLongBits(actual.values()[r][c]),
                            id + " [" + r + "," + c + "] includes NA, NaN, infinity and signed-zero payloads");
                    nonfinite |= !Double.isFinite(wanted); numbers++;
                }
                assertEquals(nonfinite, result.requiresNonFiniteReview(), id);
                assertNotSame(matrix, actual.values());
                returned++;
            }
            for (int r = 0; r < rows; r++) for (int c = 0; c < columns; c++)
                assertEquals(Double.doubleToRawLongBits(input[r * columns + c]), Double.doubleToRawLongBits(matrix[r][c]), id);
        }
        assertEquals(753, cases);
        assertEquals(303, returned); assertEquals(450, errors); assertEquals(7168, numbers);
        System.out.println("ORIGINAL_BIGCAT005_MARKER_COMPARISONS cases=" + cases + " returns=" + returned
                + " errors=" + errors + " numeric_bit_comparisons=" + numbers);
    }

    @Test
    void rejectsMalformedJavaMatricesAndMissingRequiredArguments() {
        assertThrows(IllegalArgumentException.class, () -> new ScrattchBigcat005.DenseMatrix(new double[][]{{1},{1,2}}, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ScrattchBigcat005.DenseMatrix(new double[0][], -1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ScrattchBigcat005.DenseMatrix(new double[][]{{1}}, 1, new String[0], null));
        assertThrows(IllegalArgumentException.class, () -> new ScrattchBigcat005.DenseMatrix(new double[][]{{1}}, 1, new String[]{"g1"}, null, false));
        assertThrows(NullPointerException.class, () -> ScrattchBigcat005.findDoubletByMarker(null));
        var matrix = new ScrattchBigcat005.DenseMatrix(new double[][]{{1}}, 1, null, null);
        assertThrows(NullPointerException.class, () -> ScrattchBigcat005.findDoubletByMarker(matrix, null, 3.5));
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
        return lines.stream().skip(1).map(ScrattchBigcat005Test::decode).toArray(String[]::new);
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
