/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class FixedReadTrimmerTest {
    @Test void eightyOutputsAgreeWithIndependentActualCutadaptClipping() throws Exception {
        Map<String, FastqIO.Read> reads = new HashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/fixed-trim/input.fastq")), StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) {
                String sequence = reader.readLine(); reader.readLine(); String quality = reader.readLine();
                var read = new FastqIO.Read(line, sequence, quality); reads.put(read.pairId(), read);
            }
        }
        int compared = 0;
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/fixed-trim/reference.tsv")), StandardCharsets.UTF_8))) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split("\t", -1);
                var clipped = FixedReadTrimmer.clip(reads.get(row[4]), new FixedReadTrimmer.Options(Integer.parseInt(row[1]), Integer.parseInt(row[2]), Integer.parseInt(row[3])));
                if (row[5].equals("-")) assertNull(clipped, row[0] + "/" + row[4]);
                else { assertNotNull(clipped); assertEquals(row[5], clipped.sequence()); assertEquals(row[6], clipped.quality()); assertEquals(reads.get(row[4]).header(), clipped.header()); }
                compared++;
            }
        }
        assertEquals(80, compared);
    }
    @Test void oneBasedInclusiveCoordinatesClampToEndAndFilterAbsentSpans() {
        var read = new FastqIO.Read("@A original comment", "ACGT", "!#$%");
        assertEquals("CGT", FixedReadTrimmer.clip(read, new FixedReadTrimmer.Options(2, 0, 1)).sequence());
        assertEquals("CG", FixedReadTrimmer.clip(read, new FixedReadTrimmer.Options(2, 3, 1)).sequence());
        assertEquals("T", FixedReadTrimmer.clip(read, new FixedReadTrimmer.Options(4, 100, 1)).sequence());
        assertNull(FixedReadTrimmer.clip(read, new FixedReadTrimmer.Options(5, 0, 1)));
        assertNull(FixedReadTrimmer.clip(read, new FixedReadTrimmer.Options(2, 3, 3)));
    }
    @Test void invalidCoordinatesAndMismatchedQualityAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FixedReadTrimmer.Options(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FixedReadTrimmer.Options(3, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new FixedReadTrimmer.Options(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> FixedReadTrimmer.clip(new FastqIO.Read("@A", "AC", "I"), new FixedReadTrimmer.Options(1, 0, 1)));
    }
}
