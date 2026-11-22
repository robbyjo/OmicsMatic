/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeqtkMethodsTest {
    static Map<?, ?> reference() throws Exception {
        try (var stream = SeqtkMethodsTest.class.getResourceAsStream("/seqtk/reference.json")) {
            assertNotNull(stream); Object value = new org.yaml.snakeyaml.Yaml().load(stream);
            return (Map<?, ?>) value;
        }
    }
    private static int integer(Map<?, ?> map, String key) { return ((Number) map.get(key)).intValue(); }
    @Test void exactReservoirMembershipMatchesThreePinnedUpstreamVersionsAcrossTwistsAndLongSeeds() throws Exception {
        for (Object value : (List<?>) reference().get("sampling")) {
            Map<?, ?> row = (Map<?, ?>) value;
            if (!row.get("mode").equals("count")) continue;
            long[] expected = ((List<?>) row.get("indices")).stream().mapToLong(x -> ((Number) x).longValue()).toArray();
            assertArrayEquals(expected, SeqtkMethods.sampleIndices(integer(row, "records"), integer(row, "value"), ((Number) row.get("seed")).longValue()), row.toString());
        }
    }
    @Test void exactFractionalMembershipMatchesThreePinnedUpstreamVersions() throws Exception {
        for (Object value : (List<?>) reference().get("sampling")) {
            Map<?, ?> row = (Map<?, ?>) value;
            if (!row.get("mode").equals("fraction")) continue;
            var random = new SeqtkMethods.Random(((Number) row.get("seed")).longValue());
            double fraction = ((Number) row.get("value")).doubleValue();
            var actual = new java.util.ArrayList<Long>();
            for (long i = 0; i < integer(row, "records"); i++) if (random.nextDouble() < fraction) actual.add(i);
            assertEquals(((List<?>) row.get("indices")).stream().map(x -> ((Number) x).longValue()).toList(), actual);
        }
    }
    @Test void exactQualityAndFixedTrimOutputsMatch155ActualThreeVersionFixtures() throws Exception {
        for (Object value : (List<?>) reference().get("trimming")) {
            Map<?, ?> row = (Map<?, ?>) value, o = (Map<?, ?>) row.get("options");
            var options = new SeqtkMethods.TrimOptions(((Number) o.get("error_rate")).doubleValue(), integer(o,"minimum"), integer(o,"left"), integer(o,"right"), integer(o,"length"));
            var input = new FastqIO.Read("@original complete header", (String) row.get("sequence"), (String) row.get("quality"));
            var result = SeqtkMethods.trim(input, options);
            assertEquals(row.get("expected_sequence"), result.read().sequence(), row.toString());
            assertEquals(row.get("expected_quality"), result.read().quality(), row.toString());
            assertEquals(input.header(), result.read().header());
            assertEquals(input.sequence().substring(result.start(), result.end()), result.read().sequence());
        }
    }
    @Test void poorQualityMinimumLengthFallbackIsExplicitRatherThanAQualityPass() {
        var result = SeqtkMethods.trim(new FastqIO.Read("@poor", "A".repeat(80), "!".repeat(80)), new SeqtkMethods.TrimOptions(.05,30,0,0,0));
        assertTrue(result.minimumLengthFallback()); assertEquals(30,result.read().sequence().length()); assertEquals("!".repeat(30),result.read().quality());
    }
    @Test void terminalRepeatCoordinatesMatchBothTaggedUpstreamVersionsAndFourSettings() throws Exception {
        Map<?, ?> source = reference(), chromosomes = (Map<?, ?>) source.get("chromosomes");
        for (Object value : (List<?>) source.get("telomeres")) {
            Map<?, ?> row = (Map<?, ?>) value;
            var o = new SeqtkMethods.TeloOptions((String)row.get("motif"),integer(row,"penalty"),integer(row,"drop"),integer(row,"score"));
            var actual = new StringBuilder();
            for (var chromosome : chromosomes.entrySet()) {
                String sequence = (String) chromosome.getValue();
                var bases = new SeqtkMethods.Bases() {
                    @Override public long length() { return sequence.length(); }
                    @Override public char base(long index) { return sequence.charAt((int) index); }
                };
                for (var hit : SeqtkMethods.telomeres(bases,o)) actual.append(chromosome.getKey()).append('\t').append(hit.start()).append('\t').append(hit.end()).append('\t').append(sequence.length()).append('\n');
            }
            assertEquals(row.get("bed4"),actual.toString(),row.toString());
        }
    }
    @Test void validatesParametersAndEmptySampling() {
        assertArrayEquals(new long[0],SeqtkMethods.sampleIndices(0,100,11));
        assertArrayEquals(new long[0],SeqtkMethods.sampleIndices(100,0,11));
        assertArrayEquals(new long[]{0,1,2},SeqtkMethods.sampleIndices(3,10,11));
        assertThrows(IllegalArgumentException.class,()->SeqtkMethods.sampleIndices(-1,1,1));
        assertThrows(IllegalArgumentException.class,()->SeqtkMethods.sampleIndices(1,SeqtkMethods.MAX_SAMPLE_SIZE+1,1));
        assertThrows(IllegalArgumentException.class,()->new SeqtkMethods.TrimOptions(Double.NaN,30,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new SeqtkMethods.TeloOptions("N",1,2000,300));
        assertThrows(IllegalArgumentException.class,()->new SeqtkMethods.TeloOptions("A".repeat(32),1,2000,300));
        assertThrows(IllegalArgumentException.class,()->new SeqtkMethods.TeloOptions("CCCTAA",1,2000,0));
    }
}
