/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class DiploidScoresOriginalReferenceTest {
    private List<String[]> rows(String name, String digest) throws Exception {
        byte[] packed;
        try (var in = getClass().getResourceAsStream("/org/jlinalg/genetics/plink-scores/" + name)) {
            assertNotNull(in); packed = in.readAllBytes();
        }
        assertEquals(digest, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed)));
        try (var in = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(packed)), StandardCharsets.UTF_8))) {
            return in.lines().map(line -> line.split("\t", -1)).toList();
        }
    }

    @Test void actualOriginalScoreFieldsMatchAcrossBothReleasesAndMissingPolicies() throws Exception {
        var samples = rows("samples.tsv.gz", "f792ed4cb3e52c664a53db2b348f2117d6e5837435cef52b492ce4bc9b1490bf");
        var input = rows("variants.tsv.gz", "76f76dc1759a66186ae18b20d702e1567f9d2e1de8823d192aa4848f4c4e0f18");
        var reference = rows("reference.tsv.gz", "522a6a84c554cf87de2d6fad65dc32d6c353e4030d9cfa4cb5357024fb405945");
        assertEquals(98, samples.size()); assertEquals(17, input.size()); assertEquals(389, reference.size());
        var mean = DiploidScores.accumulator(97, DiploidScores.MissingPolicy.MEAN_IMPUTE);
        var omit = DiploidScores.accumulator(97, DiploidScores.MissingPolicy.OMIT);
        double absoluteContributionBound = 0;
        for (int variant = 1; variant < input.size(); variant++) {
            String[] row = input.get(variant); assertEquals(100, row.length);
            double weight = Double.parseDouble(row[1]), frequency = Double.parseDouble(row[2]);
            double[] dosages = new double[97];
            for (int sample = 0; sample < dosages.length; sample++)
                dosages[sample] = row[sample + 3].equals("NA") ? Double.NaN : Double.parseDouble(row[sample + 3]);
            mean.add(dosages, weight, frequency); omit.add(dosages, weight, frequency);
            absoluteContributionBound += 2 * Math.abs(weight);
        }
        var models = Map.of("mean", mean, "omit", omit); int sums = 0, averages = 0;
        var seen = new HashSet<String>();
        for (int index = 1; index < reference.size(); index++) {
            String[] row = reference.get(index); assertEquals(8, row.length);
            int sample = Integer.parseInt(row[2]); assertTrue(sample >= 0 && sample < 97);
            assertEquals(samples.get(sample + 1)[0], row[3]); assertEquals(samples.get(sample + 1)[1], row[4]);
            assertTrue(seen.add(row[0] + ":" + row[1] + ":" + sample));
            var result = models.get(row[1]).score(sample);
            assertEquals(Long.parseLong(row[7]), result.observedAlleles());
            assertEquals(row[1].equals("mean") ? 32 : result.observedAlleles(), result.denominatorAlleles());
            if (!row[5].isEmpty()) { displayed(row[5], result.sum(), absoluteContributionBound, row[0] + "/sum/" + sample); sums++; }
            if (!row[6].isEmpty()) { displayed(row[6], result.average(), absoluteContributionBound / result.denominatorAlleles(), row[0] + "/average/" + sample); averages++; }
        }
        assertEquals(388, seen.size()); assertEquals(291, sums); assertEquals(291, averages);
    }

    private static void displayed(String literal, double actual, double arithmeticBound, String label) {
        double expected = Double.parseDouble(literal);
        // Original score tables print six significant digits. The small extra
        // allowance is a bounded floating-point summation error in signed
        // scores; this is not a probability comparison or an absolute P floor.
        double quantum = expected == 0 ? 0 : Math.pow(10, Math.floor(Math.log10(Math.abs(expected))) - 5);
        double arithmetic = 32 * Math.ulp(arithmeticBound);
        assertEquals(expected, actual, quantum / 2 + arithmetic, label);
    }
}
