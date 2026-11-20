import org.jlinalg.enrichment.BroadGseaEngine;
import org.jlinalg.enrichment.BroadGseaStatistics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent complete-original-object comparisons, including every raw null ES. */
public final class CheckNativeEngine {
    private static long checks;
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]);
        List<BroadGseaEngine.GeneSet> sets = new ArrayList<>();
        for (String line : Files.readAllLines(input.resolve("sets.gmt"))) {
            String[] f = line.split("\t", -1); sets.add(new BroadGseaEngine.GeneSet(f[0], Arrays.asList(Arrays.copyOfRange(f, 2, f.length))));
        }
        List<String> rnk = Files.readAllLines(input.resolve("ranks.rnk")); String[] rankedGenes = new String[rnk.size()]; float[] rankedScores = new float[rnk.size()];
        for (int i = 0; i < rnk.size(); i++) { String[] f = rnk.get(i).split("\t"); rankedGenes[i] = f[0]; rankedScores[i] = Float.parseFloat(f[1]); }
        List<String> gct = Files.readAllLines(input.resolve("expression.gct")); String[] genes = new String[gct.size() - 3]; float[][] matrix = new float[genes.length][];
        for (int i = 0; i < genes.length; i++) { String[] f = gct.get(i + 3).split("\t"); genes[i] = f[0]; matrix[i] = values(f, 2); }
        String[] labels = Files.readAllLines(input.resolve("groups.cls")).get(2).split(" "); int[] classes = new int[labels.length];
        for (int i = 0; i < labels.length; i++) classes[i] = labels[i].equals("A") ? 0 : 1;
        var grouping = BroadGseaEngine.Grouping.fromLabels(classes);
        var rankOptions = new BroadGseaEngine.RankingOptions(BroadGseaEngine.Metric.SIGNAL_TO_NOISE, false, false, true, false, false, 0);
        for (int index = 1; index < args.length; index += 3) {
            String edition = args[index], mode = args[index + 1]; Path expected = Path.of(args[index + 2]);
            var settings = new BroadGseaEngine.Settings(edition, 10000, 12345L, 15, 100,
                    BroadGseaStatistics.Weight.WEIGHTED, new BroadGseaEngine.Limits(10_000_000L, 10_000_000L, 2_000_000_000L, 10000));
            BroadGseaEngine.Result result = switch (mode) {
                case "preranked" -> BroadGseaEngine.preranked(rankedGenes, rankedScores, sets, settings, null);
                case "expression-phenotype" -> BroadGseaEngine.expressionPhenotypeNull(genes, matrix, grouping, rankOptions,
                        BroadGseaEngine.Randomization.NO_BALANCE, sets, settings, null);
                case "expression-geneset" -> BroadGseaEngine.expressionGeneSetNull(genes, matrix, grouping, rankOptions, sets, settings, null);
                default -> throw new IllegalArgumentException("Unknown mode");
            };
            compare(result, expected);
            System.out.println("PASS_NATIVE_ENGINE_ORIGINAL_COMPLETE_NULLS\t" + edition + "\t" + mode + "\twork=" + result.workUsed());
        }
        System.out.println("PASS_NATIVE_ENGINE_EXACT_FIELDS\t" + checks);
    }
    private static void compare(BroadGseaEngine.Result actual, Path path) throws Exception {
        List<String> genes = new ArrayList<>(); List<Float> ranks = new ArrayList<>();
        Map<String, float[]> scores = new LinkedHashMap<>(), nulls = new LinkedHashMap<>(), profiles = new LinkedHashMap<>(), full = new LinkedHashMap<>();
        Map<String, int[]> hits = new LinkedHashMap<>(); Map<String, List<String>> members = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "RANK" -> { genes.add(f[2]); ranks.add(Float.parseFloat(f[3])); }
                case "SCORE" -> scores.put(f[1], values(f, 2));
                case "MEMBERS" -> members.put(f[1], Arrays.asList(Arrays.copyOfRange(f, 2, f.length)));
                case "NULL" -> nulls.put(f[1], values(f, 2));
                case "HIT_PROFILE" -> profiles.put(f[1], values(f, 2));
                case "FULL_PROFILE" -> full.put(f[1], values(f, 2));
                case "HITS" -> { int[] v = new int[f.length - 2]; for (int i = 0; i < v.length; i++) v[i] = Integer.parseInt(f[i + 2]); hits.put(f[1], v); }
                default -> { }
            }
        }
        if (!Arrays.equals(genes.toArray(String[]::new), actual.ranking().genes())) throw new AssertionError(path + " ranking identifiers");
        float[] actualRanks = actual.ranking().scores(); for (int i = 0; i < actualRanks.length; i++) equal(ranks.get(i), actualRanks[i], path + "/rank/" + i);
        if (!scores.keySet().stream().toList().equals(actual.sets().stream().map(BroadGseaEngine.SetResult::name).toList())) throw new AssertionError("Different family/order");
        int row = 0;
        for (var set : actual.sets()) {
            String label = path + "/" + set.name(); float[] expected = scores.get(set.name());
            if (!members.get(set.name()).equals(set.members())) throw new AssertionError(label + " member order");
            var score = set.observed(); equal(expected[0], score.es(), label + "/ES"); equal(expected[5], score.rankAtEs(), label + "/rankAtES");
            equal(expected[6], score.rankScoreAtEs(), label + "/rankScoreAtES");
            array(profiles.get(set.name()), score.hitProfile(), label + "/hitProfile"); array(full.get(set.name()), score.fullProfile(), label + "/fullProfile");
            if (!Arrays.equals(hits.get(set.name()), score.hits())) throw new AssertionError(label + " hit indices");
            array(nulls.get(set.name()), set.nullEs(), label + "/nullES");
            equal(expected[1], actual.statistics().nes()[row], label + "/NES"); equal(expected[2], actual.statistics().nominalP()[row], label + "/NP");
            equal(expected[3], actual.statistics().fdr()[row], label + "/FDR"); equal(expected[4], actual.statistics().fwer()[row], label + "/FWER"); row++;
        }
    }
    private static float[] values(String[] f, int start) { float[] v = new float[f.length - start]; for (int i = 0; i < v.length; i++) v[i] = Float.parseFloat(f[i + start]); return v; }
    private static void array(float[] expected, float[] actual, String label) { if (expected.length != actual.length) throw new AssertionError(label + " length"); for (int i = 0; i < actual.length; i++) equal(expected[i], actual[i], label + "/" + i); }
    private static void equal(float expected, float actual, String label) {
        checks++;
        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual)) throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
    }
}
