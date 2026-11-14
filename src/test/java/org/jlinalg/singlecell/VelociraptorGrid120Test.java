/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** All arithmetic fields from complete, frozen original R1.20.0 controls; no R runtime needed. */
class VelociraptorGrid120Test {
    private interface Floating { double at(int row, int column); }
    private interface Integral { int at(int row, int column); }

    private static Map<String, byte[]> originalReference() throws IOException, NoSuchAlgorithmException {
        byte[] archive;
        try (InputStream stream = VelociraptorGrid120Test.class.getResourceAsStream("/velociraptor-grid120-reference.zip")) {
            assertNotNull(stream, "Complete original R control archive must be packaged");
            archive = stream.readAllBytes();
        }
        assertEquals("364d1ab05db6cb3235094aa733eb95daf173e6881fa6614c33fd2983601448d8",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)),
                "Original source, full populations and diagnostics must remain byte-exact");
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                assertFalse(entry.isDirectory());
                assertNull(files.put(entry.getName(), input.readAllBytes()), "Duplicate original fixture entry");
            }
        }
        assertEquals(381, files.size());
        return files;
    }

    private static ByteBuffer bytes(Map<String, byte[]> files, String name, int expected) {
        byte[] payload = files.get(name);
        assertNotNull(payload, "Missing complete original field " + name);
        assertEquals(expected, payload.length, "Incomplete original field " + name);
        return ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static double[][] matrix(Map<String, byte[]> files, String name, int rows) {
        ByteBuffer input = bytes(files, name, Math.multiplyExact(rows, 16));
        double[][] values = new double[rows][2];
        for (double[] row : values) for (int column = 0; column < 2; column++) row[column] = input.getDouble();
        return values;
    }

    private static int floating(Map<String, byte[]> files, String name, int rows, int columns, Floating actual) {
        ByteBuffer expected = bytes(files, name, Math.multiplyExact(Math.multiplyExact(rows, columns), 8));
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double reference = expected.getDouble();
                double observed = actual.at(row, column);
                String position = name + "[" + row + "," + column + "]";
                if (Double.isNaN(reference)) assertTrue(Double.isNaN(observed), position + " NaN was replaced");
                else if (Double.isInfinite(reference)) assertEquals(reference, observed, position);
                else {
                    assertTrue(Double.isFinite(observed), position);
                    assertEquals(reference, observed, 1e-12 * Math.max(1.0, Math.abs(reference)), position);
                }
            }
        }
        return rows * columns;
    }

    private static int integral(Map<String, byte[]> files, String name, int rows, int columns, Integral actual) {
        ByteBuffer expected = bytes(files, name, Math.multiplyExact(Math.multiplyExact(rows, columns), 4));
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            assertEquals(expected.getInt(), actual.at(row, column), name + "[" + row + "," + column + "]");
        }
        return rows * columns;
    }

    @Test void all31CompleteOriginalRArithmeticSurfacesMatch() throws IOException, NoSuchAlgorithmException {
        Map<String, byte[]> files = originalReference();
        String[] cases = new String(files.get("cases.tsv"), StandardCharsets.UTF_8).split("\\R");
        assertEquals("id\tdirectory\tcells\tresolution\tscale\tbins\trequires_nonfinite_review", cases[0]);
        assertEquals(32, cases.length);
        int values = 0;
        for (int i = 1; i < cases.length; i++) {
            String[] fields = cases[i].split("\t", -1);
            assertEquals(7, fields.length);
            String prefix = "binary/" + fields[1] + "/";
            int cells = Integer.parseInt(fields[2]);
            int resolution = Integer.parseInt(fields[3]);
            boolean scale = Boolean.parseBoolean(fields[4]);
            int bins = Integer.parseInt(fields[5]);
            VelociraptorGrid120.Result result = VelociraptorGrid120.grid(
                    matrix(files, prefix + "coordinates.bin", cells),
                    matrix(files, prefix + "embedded.bin", cells), resolution, scale);
            assertEquals(cells, result.cells(), fields[0]);
            assertEquals(bins, result.bins(), fields[0]);
            assertEquals(Boolean.parseBoolean(fields[6]), result.requiresNonFiniteReview(), fields[0]);
            values += floating(files, prefix + "start.bin", bins, 2, result::start);
            values += floating(files, prefix + "end.bin", bins, 2, result::end);
            values += floating(files, prefix + "vectors.bin", bins, 2, result::vector);
            values += floating(files, prefix + "limits.bin", 2, 2, result::limit);
            values += floating(files, prefix + "delta.bin", 2, 1, (row, column) -> result.delta(row));
            values += integral(files, prefix + "categories.bin", cells, 2, result::category);
            values += integral(files, prefix + "groups.bin", cells, 1, (row, column) -> result.groupOneBased(row));
            values += integral(files, prefix + "group_labels.bin", bins, 1, (row, column) -> result.groupLabelOneBased(row));
            values += integral(files, prefix + "populations.bin", bins, 1, (row, column) -> result.population(row));
        }
        assertEquals(20331, values, "No case or returned arithmetic element may be omitted");
        String diagnostics = new String(files.get("binary-export/ORIGINAL-DIAGNOSTICS-REVIEW.json"), StandardCharsets.UTF_8);
        assertTrue(diagnostics.contains("stack imbalance"), "Retain original native R diagnostic");
        assertTrue(diagnostics.contains("\"raw_console_diagnostic_counts_complete\": false"));
    }

    @Test void unsupportedDomainsAndZeroMedianRemainExplicit() {
        double[][] coordinates = {{0, 0}, {1, 1}};
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> VelociraptorGrid120.grid(coordinates, new double[][]{{Double.NaN, 0}, {1, 1}}));
        assertTrue(missing.getMessage().contains("original velociraptor"));
        assertThrows(IllegalArgumentException.class,
                () -> VelociraptorGrid120.grid(new double[][]{{0, 0}, {0, 1}}, coordinates));
        assertThrows(IllegalArgumentException.class,
                () -> VelociraptorGrid120.grid(coordinates, coordinates, 0, true));
        VelociraptorGrid120.Result zero = VelociraptorGrid120.grid(coordinates, new double[2][2]);
        assertTrue(zero.requiresNonFiniteReview());
        for (int bin = 0; bin < zero.bins(); bin++) for (int axis = 0; axis < 2; axis++) {
            assertTrue(Double.isNaN(zero.vector(bin, axis)));
            assertTrue(Double.isNaN(zero.end(bin, axis)));
        }
    }

    @Test void returnedSnapshotOwnsItsValuesAndOriginalNoticeIsPackaged() throws IOException {
        double[][] coordinates = {{0, 0}, {1, 1}};
        double[][] vectors = {{2, 1}, {3, 4}};
        VelociraptorGrid120.Result result = VelociraptorGrid120.grid(coordinates, vectors, 40, false);
        coordinates[0][0] = 777;vectors[0][0] = 888;
        assertEquals(0, result.start(0, 0));
        assertEquals(2, result.vector(0, 0));
        try (InputStream notice = getClass().getResourceAsStream("/META-INF/LICENSE-velociraptor120.txt")) {
            assertNotNull(notice);
            String text = new String(notice.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("Copyright (c) 2020 Kevin Rue-Albrecht"));
            assertTrue(text.contains("Permission is hereby granted"));
        }
    }
}
