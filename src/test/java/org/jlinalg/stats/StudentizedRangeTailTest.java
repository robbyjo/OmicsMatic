/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StudentizedRangeTailTest {
    @Test void matchesIndependentQuadpackAndExactStudentTTailsIncludingSubnormals() throws Exception {
        var resource = getClass().getResourceAsStream("/org/jlinalg/stats/tukey/tail-expected.tsv");
        assertNotNull(resource);
        int checked = 0;
        try (var input = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            assertTrue(input.readLine().startsWith("groups\tdf\tq\tpositive_tail\tlegacy_ptukey"));
            for (String line; (line = input.readLine()) != null;) {
                String[] f = line.split("\t", -1);
                var distribution = new StudentizedRangeTail(Integer.parseInt(f[0]), Double.parseDouble(f[1]));
                double expected = Double.parseDouble(f[3]);
                double actual = distribution.probability(Double.parseDouble(f[2]));
                if (expected == 0) assertEquals(0, actual);
                else assertEquals(expected, actual, Math.max(8*Double.MIN_VALUE, expected*5e-8), line);
                checked++;
            }
        }
        assertEquals(10, checked);
    }

    @Test void positiveTailIsMonotoneAndWithinExactPairAndUnionBounds() {
        var family = new StudentizedRangeTail(5, 30);
        var pair = new StudentizedRangeTail(2, 30);
        double previous = 1;
        for (double q : new double[]{0, 1, 4, 8, 12, 20, 100, 1e6}) {
            double p = family.probability(q), lower = pair.probability(q);
            assertTrue(p <= previous, "monotonic q=" + q);
            assertTrue(p >= lower*(1-1e-8), "lower q=" + q);
            assertTrue(p <= Math.min(1, 10*lower)*(1+1e-8), "upper q=" + q);
            previous = p;
        }
        assertTrue(family.refinements() > 0);
    }

    @Test void veryHighConfidenceCriticalValueActuallyInvertsTheUpperTail() {
        var distribution = new StudentizedRangeTail(3, 4);
        double confidence = 1-1e-9;
        double q = distribution.critical(confidence);
        assertEquals(1-confidence, distribution.probability(q), (1-confidence)*5e-7);
        assertTrue(distribution.refinements() > 0);
    }
}
