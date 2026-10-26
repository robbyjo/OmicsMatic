/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignatureScoresTest {
    private static final Path FIXTURES = Path.of("src/test/resources/signature-scores");

    @Test void agreesWith288PinnedUpstreamRFunctionOutputs() throws Exception {
        var rows = Files.readAllLines(FIXTURES.resolve("expression.tsv"));
        var genes = Arrays.asList(rows.get(0).split("\t")).subList(1, 41);
        Map<String, SignatureScores.Prepared> samples = new LinkedHashMap<>();
        for (String line : rows.subList(1, rows.size())) {
            String[] fields = line.split("\t");
            samples.put(fields[0], SignatureScores.prepare(Arrays.stream(fields).skip(1).mapToDouble(Double::parseDouble).toArray()));
        }
        Map<String, int[]> sets = new LinkedHashMap<>();
        for (String line : Files.readAllLines(FIXTURES.resolve("signatures.gmt"))) {
            String[] fields = line.split("\t");
            sets.put(fields[0], Arrays.stream(fields).skip(2).mapToInt(genes::indexOf).toArray());
        }
        var references = Files.readAllLines(FIXTURES.resolve("reference.tsv"));
        assertEquals(289, references.size());
        for (String line : references.subList(1, references.size())) {
            String[] fields = line.split("\t");
            var sample = samples.get(fields[1]); int[] signature = sets.get(fields[2]);
            int cutoff = Integer.parseInt(fields[3]);
            double score = switch (fields[0]) {
                case "ucell" -> sample.ucell(signature, cutoff);
                case "aucell" -> sample.aucell(signature, cutoff);
                default -> sample.ssgsea(signature, Double.parseDouble(fields[4]));
            };
            assertEquals(Double.parseDouble(fields[5]), score, 2e-12, line);
        }
    }

    @Test void rankingAndMissingContractsRejectInvalidResults() {
        assertArrayEquals(new double[]{1.5, 1.5, 3, 4}, SignatureScores.descendingRanks(new double[]{5, 5, 0, -1}));
        assertArrayEquals(new double[]{1, 2, 3, 4}, SignatureScores.uniqueRanks(new double[]{5, 5, 0, -1}));
        var sample = SignatureScores.prepare(new double[]{10, 9, 2, 0});
        assertEquals(1, sample.ucell(new int[]{0, 1}, 4));
        assertEquals(0, sample.ucell(new int[]{-1, -1}, 4));
        assertEquals(0, sample.aucell(new int[]{3}, 4));
        assertThrows(IllegalArgumentException.class, () -> SignatureScores.prepare(new double[]{1, Double.NaN}));
        assertThrows(IllegalArgumentException.class, () -> SignatureScores.ucell(new double[]{1, 1, 1}, new int[]{0}, 3));
        assertThrows(IllegalArgumentException.class, () -> SignatureScores.aucell(new double[]{1.5, 1.5, 3}, new int[]{0}, 3));
        assertThrows(IllegalArgumentException.class, () -> sample.ucell(new int[]{0, 0}, 4));
        assertThrows(IllegalArgumentException.class, () -> sample.ssgsea(new int[]{0, 1, 2, 3}, 0.25));
        assertThrows(IllegalArgumentException.class, () -> sample.ssgsea(new int[]{0}, -1));
    }
}
