/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PopsWorkflowNpyTest {
    @TempDir Path scratch;
    static Map<String, byte[]> fixture() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var raw = PopsWorkflowNpyTest.class.getResourceAsStream("/org/jlinalg/cli/pops-workflow-inputs.zip")) {
            assertNotNull(raw);
            try (var zip = new ZipInputStream(raw)) { for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) if (!e.isDirectory()) assertNull(files.put(e.getName(), zip.readAllBytes())); }
        }
        return files;
    }
    private Path file(Map<String, byte[]> files, String name) throws IOException { Path target = scratch.resolve(name); Files.createDirectories(target.getParent()); Files.write(target, files.get(name)); return target; }
    private static PopsWorkflowNpy read(Path path) throws IOException { return PopsWorkflowNpy.inspect(path, 1048576, 10000000); }
    @TestFactory List<DynamicTest> realNumpy1195MatricesAcrossTypesStorageOrdersAndFormatVersions() throws Exception {
        var files = fixture(); List<DynamicTest> tests = new ArrayList<>();
        for (String line : new String(files.get("npy/index.tsv"), StandardCharsets.UTF_8).lines().skip(1).toList()) {
            String[] names = line.split("\t"); tests.add(DynamicTest.dynamicTest(names[0], () -> {
                var array = read(file(files, "npy/" + names[0])); assertEquals(2, array.dimensions()); assertEquals(3, array.dimension(0)); assertEquals(3, array.dimension(1));
                double[][] expected = new String(files.get("npy/" + names[1]), StandardCharsets.UTF_8).lines().map(s -> Arrays.stream(s.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
                double[][] all = array.columns(0, 3), middle = array.columns(1, 1), square = array.square(new int[] {2, 0});
                for (int i = 0; i < 3; i++) { assertArrayEquals(expected[i], all[i], 0); assertEquals(expected[i][1], middle[i][0]); }
                assertArrayEquals(new double[] {expected[2][2], expected[2][0]}, square[0], 0); assertArrayEquals(new double[] {expected[0][2], expected[0][0]}, square[1], 0);
            }));
        }
        assertEquals(132, tests.size()); return tests;
    }
    @Test void vectorsStringsEmptyMatricesAndNonfiniteBitsArePreserved() throws Exception {
        var f = fixture(); assertArrayEquals(new double[] {0, new java.math.BigInteger("18446744073709551615").doubleValue(), new java.math.BigInteger("9223372036854779905").doubleValue()}, read(file(f, "npy/vector.npy")).vector(), 0);
        assertEquals("csr", read(file(f, "npy/scalar.npy")).textScalar()); assertEquals("csc", read(file(f, "npy/unicode.npy")).textScalar());
        assertEquals(0, read(file(f, "npy/empty.npy")).columns(0, 1).length);
        assertArrayEquals(new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}, read(file(f, "npy/nonfinite.npy")).columns(0, 3)[0]);
    }
    @Test void capacityTruncationTrailingBytesObjectDtypeAndExecutableHeadersAreRejected() throws Exception {
        var f = fixture(); Path path = file(f, "npy/f8-little-C-v1.npy");
        assertThrows(IOException.class, () -> PopsWorkflowNpy.inspect(path, 1, 100000)); assertThrows(IOException.class, () -> PopsWorkflowNpy.inspect(path, 100000, 1));
        byte[] raw = Files.readAllBytes(path); Files.write(path, Arrays.copyOf(raw, raw.length - 1)); assertThrows(IOException.class, () -> read(path));
        Files.write(path, Arrays.copyOf(raw, raw.length + 1)); assertThrows(IOException.class, () -> read(path));
        String encoded = new String(raw, StandardCharsets.ISO_8859_1);
        for (String changed : List.of(encoded.replace("<f8", "|O8"), encoded.replace("(3, 3)", "(3  3)"), encoded.replace("False", "eval "))) {
            assertNotEquals(encoded, changed); Files.write(path, changed.getBytes(StandardCharsets.ISO_8859_1)); assertThrows(IOException.class, () -> read(path));
        }
    }
}
