import edu.mit.broad.genome.alg.gsea.GeneSetCohort;
import edu.mit.broad.genome.alg.gsea.GeneSetScoringTable;
import edu.mit.broad.genome.alg.gsea.GeneSetScoringTables;
import edu.mit.broad.genome.alg.gsea.KSCore;
import edu.mit.broad.genome.alg.gsea.Norms;
import edu.mit.broad.genome.alg.gsea.PValueCalculatorImpls;
import edu.mit.broad.genome.math.Vector;
import edu.mit.broad.genome.objects.DefaultRankedList;
import edu.mit.broad.genome.objects.GeneSet;
import edu.mit.broad.genome.objects.RankedList;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentResult;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentScore;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentScoreImpl;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;

/** Original-library reference cases; deliberately never calls project arithmetic. */
public final class OriginalPrimitiveProbe {
    private static String floats(float[] values) {
        StringBuilder s = new StringBuilder();
        for (float x : values) { if (!s.isEmpty()) s.append(','); s.append(Float.toString(x)); }
        return s.toString();
    }
    private static String vector(Vector values) {
        float[] data = new float[values.getSize()];
        for (int i = 0; i < data.length; i++) data[i] = values.getElement(i);
        return floats(data);
    }
    private static String ints(int[] values) {
        StringBuilder s = new StringBuilder();
        for (int x : values) { if (!s.isEmpty()) s.append(','); s.append(x); }
        return s.toString();
    }
    private static String matrix(float[][] values) {
        StringBuilder s = new StringBuilder();
        for (float[] x : values) { if (!s.isEmpty()) s.append(';'); s.append(floats(x)); }
        return s.toString();
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("NEW output TSV path");
        edu.mit.broad.xbench.core.api.Application.registerHandler(new xtools.api.XToolsApplication());
        Random random = new Random(934035L);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[0]), StandardCharsets.UTF_8))) {
            out.println("kind\tcase\tmode\tfields");
            for (int c = 0; c < 320; c++) {
                int n = 5 + random.nextInt(80), k = 1 + random.nextInt(n - 1);
                float[] ranking = new float[n]; List<String> names = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    names.add("G" + String.format(java.util.Locale.ROOT, "%03d", i));
                    ranking[i] = switch (c % 8) {
                        case 0 -> (float) (random.nextGaussian() * 3);
                        case 1 -> random.nextFloat() * 5 + .001f;
                        case 2 -> -random.nextFloat() * 5 - .001f;
                        case 3 -> (float) (random.nextInt(5) - 2);
                        case 4 -> 0;
                        case 5 -> i % 2 == 0 ? Float.MAX_VALUE : -Float.MAX_VALUE;
                        case 6 -> Float.MIN_VALUE * (i % 4);
                        default -> (i - n / 2.0f) / n;
                    };
                }
                List<Integer> shuffled = new ArrayList<>();
                for (int i = 0; i < n; i++) shuffled.add(i);
                java.util.Collections.shuffle(shuffled, random);
                String[] memberNames = new String[k];
                for (int i = 0; i < k; i++) memberNames[i] = names.get(shuffled.get(i));
                GeneSet set = new GeneSet("SET" + c, memberNames);
                int[] members = new int[k];
                for (int i = 0; i < k; i++) members[i] = names.indexOf(set.getMember(i));
                RankedList rl = new DefaultRankedList("CASE" + c, names, new Vector(ranking, true));
                GeneSetScoringTable[] tables = {new GeneSetScoringTables.Classic(), new GeneSetScoringTables.Weighted(),
                        new GeneSetScoringTables.WeightedSquared(), new GeneSetScoringTables.WeightedOnePointFive()};
                String[] modes = {"CLASSIC", "WEIGHTED", "WEIGHTED_P2", "WEIGHTED_P1_5"};
                for (int mode = 0; mode < tables.length; mode++) {
                    GeneSetCohort cohort = new GeneSetCohort.Generator(tables[mode], 1, n - 1)
                            .createGeneSetCohort(rl, new GeneSet[]{set}, true);
                    EnrichmentScore score = new KSCore().calculateKSScore(cohort, true)[0];
                    float[] originalHitWeights = new float[k];
                    for (int j = 0; j < k; j++) originalHitWeights[j] = (float) cohort.getHitPoints(0, set.getMember(j));
                    out.println("WALK\t" + c + "\t" + modes[mode] + "\t" + floats(ranking) + "\t" + ints(members)
                            + "\t" + score.getES() + "\t" + score.getRankAtES() + "\t" + score.getRankScoreAtES()
                            + "\t" + ints(score.getHitIndices()) + "\t" + vector(score.getESProfile())
                            + "\t" + vector(score.getESProfile_point_by_point_opt()) + "\t" + floats(originalHitWeights));
                }
            }
            for (int c = 0; c < 240; c++) {
                int m = 1 + random.nextInt(10), p = 1 + random.nextInt(90);
                float[] observed = new float[m]; float[][] nulls = new float[m][p];
                for (int r = 0; r < m; r++) {
                    observed[r] = switch (c % 8) {
                        case 0 -> .4f;
                        case 1 -> -.4f;
                        case 2 -> r % 2 == 0 ? .5f : -.5f;
                        case 3 -> 0;
                        default -> (random.nextInt(15) - 7) / 8f;
                    };
                    for (int j = 0; j < p; j++) nulls[r][j] = switch (c % 8) {
                        case 0 -> .1f + random.nextFloat() * .9f;
                        case 1 -> -.1f - random.nextFloat() * .9f;
                        case 4 -> 0;
                        case 5 -> (random.nextInt(3) - 1) / 2f;
                        default -> (random.nextInt(15) - 7) / 8f;
                    };
                }
                EnrichmentResult[] raw = new EnrichmentResult[m];
                RankedList rl = new DefaultRankedList("FAMILY" + c, List.of("G0", "G1"), new Vector(new float[]{1, -1}, true));
                for (int r = 0; r < m; r++) {
                    EnrichmentScore score = new EnrichmentScoreImpl(observed[r], 0, 1, Float.NaN, Float.NaN,
                            Float.NaN, Float.NaN, 1, new int[]{0}, new Vector(new float[]{observed[r]}, true), null);
                    raw[r] = new EnrichmentResult(rl, null, new GeneSet("SET" + r, new String[]{"G0"}), null,
                            score, new Vector(nulls[r], true), null);
                }
                String input = "FAMILY\t" + c + "\t" + floats(observed) + "\t" + matrix(nulls);
                try {
                    EnrichmentResult[] results = new PValueCalculatorImpls.GseaImpl("meandiv").calcNPValuesAndFDR(raw);
                    float[] nes = new float[m], np = new float[m], fdr = new float[m], fwer = new float[m];
                    float[][] normalized = new float[m][]; boolean finite = true;
                    for (int r = 0; r < m; r++) {
                        EnrichmentScore s = results[r].getScore(); nes[r] = s.getNES(); np[r] = s.getNP();
                        fdr[r] = s.getFDR(); fwer[r] = s.getFWER();
                        Vector v = Norms.createNorm("meandiv", observed[r], new Vector(nulls[r], true)).getRandomNorm();
                        normalized[r] = new float[v.getSize()];
                        for (int j = 0; j < v.getSize(); j++) normalized[r][j] = v.getElement(j);
                        finite &= Float.isFinite(nes[r]) && Float.isFinite(np[r]) && Float.isFinite(fdr[r]) && Float.isFinite(fwer[r]);
                        for (float x : normalized[r]) finite &= Float.isFinite(x);
                    }
                    out.println(input + "\t" + (finite ? "FINITE" : "NONFINITE") + "\t" + floats(nes) + "\t" + floats(np)
                            + "\t" + floats(fdr) + "\t" + floats(fwer) + "\t" + matrix(normalized));
                } catch (Exception error) {
                    out.println(input + "\tORIGINAL_ERROR\t" + Base64.getEncoder().encodeToString(error.toString().getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
        System.out.println("EXPORTED_ORIGINAL_PRIMITIVES\twalks=1280\tfamilies=240");
    }
}
