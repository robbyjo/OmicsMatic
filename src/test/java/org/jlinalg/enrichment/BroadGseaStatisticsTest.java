package org.jlinalg.enrichment;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class BroadGseaStatisticsTest {
    @Test void matchesUnmodifiedOriginalFloatObjectsAndRefusesSourceRepairBranches() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var stream = getClass().getResourceAsStream("broad-gsea-originals.zip")) {
            assertNotNull(stream, "Original source-counterpart fixture");
            try (var zip = new ZipInputStream(stream)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertNull(files.put(entry.getName(), zip.readAllBytes()), "Duplicate original fixture member");
                }
            }
        }
        int manifestCount = 0;
        for (String line : text(files, "SHA256SUMS").lines().toList()) {
            String[] fields = line.split("  ", 2);
            assertEquals(fields[0], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(files.get(fields[1]))), fields[1]);
            manifestCount++;
        }
        assertEquals(manifestCount + 1, files.size());
        int walks = 0, families = 0, walkRefusals = 0, familyRefusals = 0, workflows = 0;
        for (var entry : files.entrySet()) {
            if (entry.getKey().endsWith("/primitives.tsv")) {
                for (String line : new String(entry.getValue(), StandardCharsets.UTF_8).lines().toList()) {
                    String[] f = line.split("\t", -1); String label = entry.getKey() + "/" + (f.length > 1 ? f[1] : "header");
                    if (f[0].equals("WALK")) {
                        float[] weights = floats(f[11]); double mass = 0;
                        for (float weight : weights) mass += weight;
                        if (!Double.isFinite(mass) || Math.abs(mass - 1) > 5e-6) {
                            assertThrows(ArithmeticException.class, () -> BroadGseaStatistics.score(floats(f[3]), ints(f[4]), BroadGseaStatistics.Weight.valueOf(f[2])), label);
                            walkRefusals++;
                        } else {
                            var score = BroadGseaStatistics.score(floats(f[3]), ints(f[4]), BroadGseaStatistics.Weight.valueOf(f[2]));
                            bits(Float.parseFloat(f[5]), score.es(), label);
                            assertEquals(Integer.parseInt(f[6]), score.rankAtEs(), label);
                            bits(Float.parseFloat(f[7]), score.rankScoreAtEs(), label);
                            assertArrayEquals(ints(f[8]), score.hits(), label);
                            bits(floats(f[9]), score.hitProfile(), label);
                            bits(floats(f[10]), score.fullProfile(), label); walks++;
                        }
                    } else if (f[0].equals("FAMILY")) {
                        if (!f[4].equals("FINITE")) {
                            RuntimeException error = assertThrows(RuntimeException.class,
                                    () -> BroadGseaStatistics.meandiv(floats(f[2]), matrix(f[3]), 1_000_000L), label);
                            assertTrue(error instanceof IllegalArgumentException || error instanceof ArithmeticException, label);
                            familyRefusals++;
                        } else {
                            var family = BroadGseaStatistics.meandiv(floats(f[2]), matrix(f[3]), 1_000_000L);
                            bits(floats(f[5]), family.nes(), label); bits(floats(f[6]), family.nominalP(), label);
                            bits(floats(f[7]), family.fdr(), label); bits(floats(f[8]), family.fwer(), label);
                            float[][] expected = matrix(f[9]), actual = family.normalizedNull();
                            assertEquals(expected.length, actual.length, label);
                            for (int r = 0; r < actual.length; r++) bits(expected[r], actual[r], label);
                            families++;
                        }
                    }
                }
            } else if (entry.getKey().endsWith("/preranked.tsv") || entry.getKey().endsWith("/expression-phenotype.tsv")
                    || entry.getKey().endsWith("/expression-geneset.tsv")) {
                checkWorkflow(new String(entry.getValue(), StandardCharsets.UTF_8), entry.getKey()); workflows++;
            }
        }
        assertEquals(2415, walks); assertEquals(444, families); assertEquals(1425, walkRefusals);
        assertEquals(276, familyRefusals); assertEquals(9, workflows);
    }

    @Test void enforcesExplicitCapacityAndPreparedInputContractsWithoutMutation() {
        float[] observed = {.4f}; float[][] random = {{.2f, .8f}};
        assertThrows(IllegalArgumentException.class, () -> BroadGseaStatistics.meandiv(observed, random, 1));
        assertArrayEquals(new float[]{.4f}, observed); assertArrayEquals(new float[]{.2f, .8f}, random[0]);
        assertThrows(IllegalArgumentException.class, () -> BroadGseaStatistics.score(new float[]{1, -1}, new int[]{0, 0}, BroadGseaStatistics.Weight.WEIGHTED));
        assertThrows(IllegalArgumentException.class, () -> BroadGseaStatistics.score(new float[]{1, -1}, new int[]{2}, BroadGseaStatistics.Weight.WEIGHTED));
        assertThrows(ArithmeticException.class, () -> BroadGseaStatistics.score(new float[]{Float.NaN, -1}, new int[]{0}, BroadGseaStatistics.Weight.WEIGHTED));
        var family = BroadGseaStatistics.meandiv(observed, random, 2);
        float original = family.nes()[0]; family.nes()[0] = 999; family.normalizedNull()[0][0] = 999;
        bits(original, family.nes()[0], "Result arrays are copied");
        assertNotEquals(999, family.normalizedNull()[0][0]);
    }

    private static void checkWorkflow(String input, String label) {
        List<String> genes = new ArrayList<>(); List<Float> values = new ArrayList<>();
        Map<String, float[]> scores = new LinkedHashMap<>(), nulls = new LinkedHashMap<>(), hits = new LinkedHashMap<>(), profiles = new LinkedHashMap<>(), full = new LinkedHashMap<>();
        Map<String, String[]> members = new LinkedHashMap<>();
        for (String line : input.lines().toList()) {
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "RANK" -> { genes.add(f[2]); values.add(Float.parseFloat(f[3])); }
                case "MEMBERS" -> members.put(f[1], Arrays.copyOfRange(f, 2, f.length));
                case "SCORE" -> scores.put(f[1], tabValues(f));
                case "NULL" -> nulls.put(f[1], tabValues(f));
                case "HITS" -> hits.put(f[1], tabValues(f));
                case "HIT_PROFILE" -> profiles.put(f[1], tabValues(f));
                case "FULL_PROFILE" -> full.put(f[1], tabValues(f));
                default -> { }
            }
        }
        float[] ranks = new float[values.size()], observed = new float[scores.size()]; float[][] random = new float[scores.size()][];
        for (int i = 0; i < ranks.length; i++) ranks[i] = values.get(i);
        int r = 0;
        for (String set : scores.keySet()) {
            int[] memberIndices = Arrays.stream(members.get(set)).mapToInt(genes::indexOf).toArray();
            var actual = BroadGseaStatistics.score(ranks, memberIndices, BroadGseaStatistics.Weight.WEIGHTED); float[] expected = scores.get(set);
            bits(expected[0], actual.es(), label + "/" + set); assertEquals((int) expected[5], actual.rankAtEs(), label);
            bits(expected[6], actual.rankScoreAtEs(), label); bits(profiles.get(set), actual.hitProfile(), label); bits(full.get(set), actual.fullProfile(), label);
            int[] indices = actual.hits(); assertEquals(hits.get(set).length, indices.length);
            for (int i = 0; i < indices.length; i++) assertEquals((int) hits.get(set)[i], indices[i], label);
            observed[r] = expected[0]; random[r] = nulls.get(set); r++;
        }
        var family = BroadGseaStatistics.meandiv(observed, random, 1_000_000L); r = 0;
        for (String set : scores.keySet()) {
            float[] expected = scores.get(set);
            bits(expected[1], family.nes()[r], label); bits(expected[2], family.nominalP()[r], label);
            bits(expected[3], family.fdr()[r], label); bits(expected[4], family.fwer()[r], label); r++;
        }
    }
    private static String text(Map<String, byte[]> files, String path) { return new String(files.get(path), StandardCharsets.UTF_8); }
    private static float[] floats(String text) {
        String[] fields = text.split(",", -1); float[] values = new float[fields.length];
        for (int i = 0; i < values.length; i++) values[i] = Float.parseFloat(fields[i]);
        return values;
    }
    private static int[] ints(String text) { return Arrays.stream(text.split(",", -1)).mapToInt(Integer::parseInt).toArray(); }
    private static float[][] matrix(String text) { return Arrays.stream(text.split(";", -1)).map(BroadGseaStatisticsTest::floats).toArray(float[][]::new); }
    private static float[] tabValues(String[] f) { float[] a = new float[f.length - 2]; for (int i = 0; i < a.length; i++) a[i] = Float.parseFloat(f[i + 2]); return a; }
    private static void bits(float expected, float actual, String label) { assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(actual), label); }
    private static void bits(float[] expected, float[] actual, String label) { assertEquals(expected.length, actual.length, label); for (int i = 0; i < actual.length; i++) bits(expected[i], actual[i], label + "/" + i); }
}
