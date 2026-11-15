/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

final class DoubletScoreThresholdTest {
    @Test
    void completeIndependentR423ComparisonsAndOriginalRowSelections() throws Exception {
        Map<String,String> fixture = new LinkedHashMap<>();
        try (var raw = getClass().getResourceAsStream("doublet-score-threshold-r423.zip")) {
            assertNotNull(raw, "Complete independent R oracle resource");
            try (var zip = new ZipInputStream(raw)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    fixture.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        Map<String,List<Double>> values = new LinkedHashMap<>();
        var classifications = fixture.get("classifications.tsv").lines().toList();
        assertEquals(17697, classifications.size() - 1);
        for (String line : classifications.subList(1, classifications.size())) {
            String[] columns = line.split("\t", -1);
            double value = decode(columns[1]);
            assertEquals(columns[2], DoubletScoreThreshold.classify(value, decode(columns[0])).name(), line);
            values.computeIfAbsent(columns[0], ignored -> new ArrayList<>()).add(value);
        }
        var selections = fixture.get("selections.tsv").lines().toList();
        assertEquals(17, selections.size() - 1);
        for (String line : selections.subList(1, selections.size())) {
            String[] columns = line.split("\t", -1);
            double[] input = values.get(columns[0]).stream().mapToDouble(Double::doubleValue).toArray();
            var actual = DoubletScoreThreshold.select(input, decode(columns[0]));
            assertArrayEquals(rows(columns[1]), actual.retainedRows(), columns[0]);
            assertArrayEquals(rows(columns[2]), actual.removedRows(), columns[0]);
            assertArrayEquals(rows(columns[3]), actual.unknownRows(), columns[0]);
        }
    }

    private static double decode(String bits) {
        return Double.longBitsToDouble(Long.parseUnsignedLong(bits, 16));
    }
    private static int[] rows(String column) {
        return column.isEmpty() ? new int[0] : Arrays.stream(column.split(",")).mapToInt(Integer::parseInt).toArray();
    }
}
