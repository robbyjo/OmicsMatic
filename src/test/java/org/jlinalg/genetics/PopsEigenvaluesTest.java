/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class PopsEigenvaluesTest {
    @Test void originalNumpySpectraAcrossScalesRanksAndIndefiniteMatrices() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (var raw = getClass().getResourceAsStream("pops-eigenvalues-numpy1195.zip")) {
            assertNotNull(raw);
            try (var zip = new ZipInputStream(raw)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    assertNull(files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        int count = 0;
        for (String key : files.keySet()) if (key.endsWith(".matrix.tsv")) {
            double[][] matrix = files.get(key).lines().map(s -> Arrays.stream(s.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
            String name = key.substring(0, key.length() - ".matrix.tsv".length());
            double[] expected = files.get(name + ".eigenvalues.tsv").lines().mapToDouble(Double::parseDouble).toArray();
            double[] actual = PopsEigenvalues.values(matrix); assertEquals(expected.length, actual.length);
            double scale = 0; for (double[] row : matrix) for (double value : row) scale = Math.max(scale, Math.abs(value));
            for (int i = 0; i < actual.length; i++) {
                if (scale == 0) assertEquals(expected[i], actual[i], 0, name);
                else assertEquals(expected[i] / scale, actual[i] / scale, 1e-10 * (1 + Math.abs(expected[i] / scale)), name + "/" + i);
            }
            count++;
        }
        assertEquals(22, count);
    }
}
