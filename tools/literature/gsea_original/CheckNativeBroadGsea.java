import org.jlinalg.enrichment.BroadGseaStatistics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Check native arithmetic against full-precision exports from the unchanged original API. */
public final class CheckNativeBroadGsea {
    private static long comparisons;
    public static void main(String[] args) throws Exception {
        for (String name : args) check(Path.of(name));
        System.out.println("PASS_NATIVE_BROAD_GSEA_ORIGINAL_OBJECT_COMPARISONS\t" + comparisons);
    }
    private static void check(Path path) throws Exception {
        List<String> genes = new ArrayList<>(); List<Float> values = new ArrayList<>();
        Map<String, float[]> scores = new LinkedHashMap<>(), nulls = new LinkedHashMap<>(), hits = new LinkedHashMap<>(), profiles = new LinkedHashMap<>(), full = new LinkedHashMap<>();
        Map<String, String[]> members = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String[] fields = line.split("\t", -1);
            switch (fields[0]) {
                case "RANK" -> { genes.add(fields[2]); values.add(Float.parseFloat(fields[3])); }
                case "MEMBERS" -> members.put(fields[1], java.util.Arrays.copyOfRange(fields, 2, fields.length));
                case "SCORE" -> scores.put(fields[1], parse(fields));
                case "NULL" -> nulls.put(fields[1], parse(fields));
                case "HITS" -> hits.put(fields[1], parse(fields));
                case "HIT_PROFILE" -> profiles.put(fields[1], parse(fields));
                case "FULL_PROFILE" -> full.put(fields[1], parse(fields));
                default -> { }
            }
        }
        float[] ranking = new float[values.size()];
        for (int i = 0; i < ranking.length; i++) ranking[i] = values.get(i);
        float[] observed = new float[scores.size()]; float[][] random = new float[scores.size()][];
        int row = 0;
        for (String set : scores.keySet()) {
            String[] names = members.get(set); int[] indices = new int[names.length];
            for (int i = 0; i < names.length; i++) { indices[i] = genes.indexOf(names[i]); if (indices[i] < 0) throw new AssertionError("Missing member"); }
            var result = BroadGseaStatistics.score(ranking, indices, BroadGseaStatistics.Weight.WEIGHTED);
            float[] expected = scores.get(set);
            equal(expected[0], result.es(), path + "/" + set + "/ES");
            equal(expected[5], result.rankAtEs(), path + "/" + set + "/rank");
            equal(expected[6], result.rankScoreAtEs(), path + "/" + set + "/rankScore");
            array(profiles.get(set), result.hitProfile(), path + "/" + set + "/hitProfile");
            array(full.get(set), result.fullProfile(), path + "/" + set + "/fullProfile");
            int[] actualHits = result.hits();
            for (int i = 0; i < actualHits.length; i++) equal(hits.get(set)[i], actualHits[i], set + "/hit");
            observed[row] = expected[0]; random[row] = nulls.get(set); row++;
        }
        var family = BroadGseaStatistics.meandiv(observed, random, 10_000_000L);
        row = 0;
        for (String set : scores.keySet()) {
            float[] expected = scores.get(set);
            equal(expected[1], family.nes()[row], path + "/" + set + "/NES");
            equal(expected[2], family.nominalP()[row], path + "/" + set + "/NP");
            equal(expected[3], family.fdr()[row], path + "/" + set + "/FDR");
            equal(expected[4], family.fwer()[row], path + "/" + set + "/FWER");
            row++;
        }
        System.out.println("PASS_FULL_PRECISION_REFERENCE\t" + path + "\tsets=" + scores.size());
    }
    private static float[] parse(String[] values) {
        float[] result = new float[values.length - 2];
        for (int i = 0; i < result.length; i++) result[i] = Float.parseFloat(values[i + 2]);
        return result;
    }
    private static void array(float[] expected, float[] actual, String label) {
        if (expected.length != actual.length) throw new AssertionError(label + " length");
        for (int i = 0; i < actual.length; i++) equal(expected[i], actual[i], label + "/" + i);
    }
    private static void equal(float expected, float actual, String label) {
        comparisons++;
        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual))
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual
                    + " expectedBits=" + Integer.toHexString(Float.floatToIntBits(expected))
                    + " actualBits=" + Integer.toHexString(Float.floatToIntBits(actual)));
    }
}
