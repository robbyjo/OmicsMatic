package org.jlinalg.enrichment;

import org.jlinalg.enrichment.BroadGseaEngine;
import org.jlinalg.enrichment.BroadGseaStatistics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares native models with complete unchanged-source option/boundary fixtures. */
class BroadGseaEngineTest {
    private static long fields;
    private static int admitted, refused;
    @org.junit.jupiter.api.Test void matchesOriginalOptionAndBoundaryCorpus() throws Exception {
        fields = 0; admitted = 0; refused = 0;
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var stream = getClass().getResourceAsStream("broad-gsea-engine-originals.zip")) {
            org.junit.jupiter.api.Assertions.assertNotNull(stream);
            try (var zip = new java.util.zip.ZipInputStream(stream)) {
                for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                    org.junit.jupiter.api.Assertions.assertNull(files.put(e.getName(), zip.readAllBytes()));
                }
            }
        }
        String manifest = new String(files.remove("SHA256SUMS"), java.nio.charset.StandardCharsets.UTF_8);
        int entries = 0;
        for (String line : manifest.lines().toList()) {
            String[] p = line.split("  ", 2);
            byte[] bytes = files.get(p[1]); org.junit.jupiter.api.Assertions.assertNotNull(bytes, p[1]);
            org.junit.jupiter.api.Assertions.assertEquals(p[0], java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes)), p[1]); entries++;
        }
        org.junit.jupiter.api.Assertions.assertEquals(624, entries);
        org.junit.jupiter.api.Assertions.assertEquals(entries, files.size());
        for (var e : files.entrySet()) check(new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8).lines().toList(), e.getKey());
        org.junit.jupiter.api.Assertions.assertEquals(423, admitted);
        org.junit.jupiter.api.Assertions.assertEquals(201, refused);
        org.junit.jupiter.api.Assertions.assertEquals(200469, fields);
    }
    private static void check(List<String> lines, String path) throws Exception {
        List<String[]> rows = lines.stream().map(line -> line.split("\t", -1)).toList();
        String[] config = rows.stream().filter(r -> r[0].equals("CONFIG")).findFirst().orElseThrow();
        boolean expectedAdmit = rows.stream().filter(r -> r[0].equals("NATIVE_POLICY")).findFirst().orElseThrow()[1].equals("ADMIT");
        List<String[]> data = rows.stream().filter(r -> r[0].equals("DATA")).toList();
        String[] genes = data.stream().map(r -> r[1]).toArray(String[]::new); float[][] matrix = data.stream().map(r -> floats(r, 2)).toArray(float[][]::new);
        int[] zero = indices(rows.stream().filter(r -> r[0].equals("GROUP0")).findFirst().orElseThrow(), 1);
        int[] one = indices(rows.stream().filter(r -> r[0].equals("GROUP1")).findFirst().orElseThrow(), 1);
        try {
            List<BroadGseaEngine.GeneSet> sets = new ArrayList<>();
            for (String[] r : rows) if (r[0].equals("INPUT_SET")) sets.add(new BroadGseaEngine.GeneSet(r[1], Arrays.asList(Arrays.copyOfRange(r, 2, r.length))));
            var weight = switch (config[12]) { case "classic" -> BroadGseaStatistics.Weight.CLASSIC; case "weighted" -> BroadGseaStatistics.Weight.WEIGHTED;
                case "weighted_p2" -> BroadGseaStatistics.Weight.WEIGHTED_P2; case "weighted_p1.5" -> BroadGseaStatistics.Weight.WEIGHTED_P1_5; default -> throw new AssertionError(); };
            var metric = switch (config[4]) { case "Signal2Noise" -> BroadGseaEngine.Metric.SIGNAL_TO_NOISE; case "tTest" -> BroadGseaEngine.Metric.T_TEST;
                case "Diff_of_Classes" -> BroadGseaEngine.Metric.DIFFERENCE; case "Ratio_of_Classes" -> BroadGseaEngine.Metric.RATIO;
                case "log2_Ratio_of_Classes" -> BroadGseaEngine.Metric.LOG2_RATIO; default -> throw new AssertionError(); };
            var randomizer = switch (config[11]) { case "no_balance" -> BroadGseaEngine.Randomization.NO_BALANCE; case "balance_class0" -> BroadGseaEngine.Randomization.BALANCE_CLASS0;
                case "balance_class1" -> BroadGseaEngine.Randomization.BALANCE_CLASS1; case "equalize_and_balance" -> BroadGseaEngine.Randomization.EQUALIZE_AND_BALANCE; default -> throw new AssertionError(); };
            var options = new BroadGseaEngine.RankingOptions(metric, Boolean.parseBoolean(config[5]), Boolean.parseBoolean(config[6]),
                    Boolean.parseBoolean(config[7]), Boolean.parseBoolean(config[8]), Boolean.parseBoolean(config[9]), Integer.parseInt(config[10]));
            var settings = new BroadGseaEngine.Settings(config[3], Integer.parseInt(config[15]), Long.parseLong(config[16]),
                    Integer.parseInt(config[13]), Integer.parseInt(config[14]), weight, new BroadGseaEngine.Limits(1_000_000L, 1_000_000L, 1_000_000_000L, 10000));
            BroadGseaEngine.Grouping grouping = new BroadGseaEngine.Grouping(zero, one);
            BroadGseaEngine.Result actual;
            if (config[2].equals("preranked")) {
                List<String[]> ranking = rows.stream().filter(r -> r[0].equals("PRERANK")).toList();
                String[] ids = ranking.stream().map(r -> r[1]).toArray(String[]::new); float[] values = new float[ids.length];
                for (int i = 0; i < values.length; i++) values[i] = Float.parseFloat(ranking.get(i)[2]);
                actual = BroadGseaEngine.preranked(ids, values, sets, settings, null);
            } else if (config[2].equals("phenotype")) {
                actual = BroadGseaEngine.expressionPhenotypeNull(genes, matrix, grouping, options, randomizer, sets, settings, null);
            } else {
                actual = BroadGseaEngine.expressionGeneSetNull(genes, matrix, grouping, options, sets, settings, null);
            }
            if (!expectedAdmit) throw new AssertionError(path + " should refuse original failure/substitution or strict prepared-input boundary");
            compare(actual, rows, path); admitted++;
        } catch (IllegalArgumentException | ArithmeticException | BroadGseaEngine.Failure error) {
            if (expectedAdmit) throw new AssertionError(path + " refused a source-admitted complete result", error);
            refused++;
        }
    }
    private static void compare(BroadGseaEngine.Result actual, List<String[]> rows, String label) {
        List<String[]> ranks = rows.stream().filter(r -> r[0].equals("RANK")).toList();
        String[] names = actual.ranking().genes(); float[] scores = actual.ranking().scores(); double[] raw = actual.ranking().metricValues();
        if (ranks.size() != names.length) throw new AssertionError(label + " rank size");
        for (int i = 0; i < names.length; i++) {
            if (!ranks.get(i)[2].equals(names[i])) throw new AssertionError(label + "/rankName/" + i + " expected=" + ranks.get(i)[2] + " actual=" + names[i]);
            equal(Float.parseFloat(ranks.get(i)[3]), scores[i], label + "/rankScore/" + i);
            double expected = Double.parseDouble(ranks.get(i)[4]); fields++;
            if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(raw[i])) throw new AssertionError(label + "/rawMetric/" + i + " expected=" + expected + " actual=" + raw[i]);
        }
        Map<String, String[]> scoreRows = new LinkedHashMap<>(), memberRows = new LinkedHashMap<>(), nullRows = new LinkedHashMap<>(), hits = new LinkedHashMap<>(), profile = new LinkedHashMap<>(), full = new LinkedHashMap<>();
        for (String[] r : rows) {
            switch (r[0]) { case "SCORE" -> scoreRows.put(r[1], r); case "MEMBERS" -> memberRows.put(r[1], r); case "NULL" -> nullRows.put(r[1], r);
                case "HITS" -> hits.put(r[1], r); case "HIT_PROFILE" -> profile.put(r[1], r); case "FULL_PROFILE" -> full.put(r[1], r); default -> { } }
        }
        if (!actual.sets().stream().map(BroadGseaEngine.SetResult::name).toList().equals(scoreRows.keySet().stream().toList())) throw new AssertionError(label + " different complete family/order");
        int g = 0;
        for (var set : actual.sets()) {
            String context = label + "/" + set.name(); String[] member = memberRows.get(set.name());
            if (!set.members().equals(Arrays.asList(Arrays.copyOfRange(member, 2, member.length)))) throw new AssertionError(context + " member order");
            float[] expected = floats(scoreRows.get(set.name()), 2); var observed = set.observed();
            equal(expected[0], observed.es(), context + "/ES"); equal(expected[5], observed.rankAtEs(), context + "/rankAtES");
            equal(expected[6], observed.rankScoreAtEs(), context + "/rankScoreAtES");
            equal(expected[1], actual.statistics().nes()[g], context + "/NES"); equal(expected[2], actual.statistics().nominalP()[g], context + "/NP");
            equal(expected[3], actual.statistics().fdr()[g], context + "/FDR"); equal(expected[4], actual.statistics().fwer()[g], context + "/FWER");
            array(floats(nullRows.get(set.name()), 2), set.nullEs(), context + "/nullES");
            array(floats(profile.get(set.name()), 2), observed.hitProfile(), context + "/hitProfile"); array(floats(full.get(set.name()), 2), observed.fullProfile(), context + "/fullProfile");
            if (!Arrays.equals(indices(hits.get(set.name()), 2), observed.hits())) throw new AssertionError(context + " hits"); g++;
        }
    }
    private static int[] indices(String[] f, int start) { int[] a = new int[f.length - start]; for (int i = 0; i < a.length; i++) a[i] = Integer.parseInt(f[i + start]); return a; }
    private static float[] floats(String[] f, int start) { float[] a = new float[f.length - start]; for (int i = 0; i < a.length; i++) a[i] = Float.parseFloat(f[i + start]); return a; }
    private static void array(float[] expected, float[] actual, String label) { if (expected.length != actual.length) throw new AssertionError(label + " length"); for (int i = 0; i < actual.length; i++) equal(expected[i], actual[i], label + "/" + i); }
    private static void equal(float expected, float actual, String label) { fields++; if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual)) throw new AssertionError(label + " expected=" + expected + " actual=" + actual); }
}
