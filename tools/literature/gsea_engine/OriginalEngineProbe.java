// Reference composition in the original package to construct its protected template objects.
// No original source is patched and no project arithmetic is imported.
package edu.mit.broad.genome.objects;

import edu.mit.broad.genome.Headers;
import edu.mit.broad.genome.alg.DatasetMetrics;
import edu.mit.broad.genome.alg.Metric;
import edu.mit.broad.genome.alg.Metrics;
import edu.mit.broad.genome.alg.gsea.GeneSetCohort;
import edu.mit.broad.genome.alg.gsea.GeneSetScoringTable;
import edu.mit.broad.genome.alg.gsea.GeneSetScoringTables;
import edu.mit.broad.genome.alg.gsea.KSTests;
import edu.mit.broad.genome.alg.gsea.PValueCalculatorImpls;
import edu.mit.broad.genome.math.Matrix;
import edu.mit.broad.genome.math.Order;
import edu.mit.broad.genome.math.RandomSeedGenerators;
import edu.mit.broad.genome.math.SortMode;
import edu.mit.broad.genome.math.Vector;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentDb;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentResult;
import edu.mit.broad.genome.objects.esmatrix.db.EnrichmentScore;
import edu.mit.broad.genome.objects.strucs.DatasetTemplate;
import edu.mit.broad.genome.objects.strucs.TemplateRandomizerType;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Complete unchanged-source counterparts plus a non-mutating public-weight audit. */
public final class OriginalEngineProbe {
    private static final String[] METRICS = {"Signal2Noise", "tTest", "Diff_of_Classes", "Ratio_of_Classes", "log2_Ratio_of_Classes"};
    private static final String[] RANDOMIZERS = {"no_balance", "balance_class0", "balance_class1", "equalize_and_balance"};
    private static final String[] WEIGHTS = {"classic", "weighted", "weighted_p2", "weighted_p1.5"};

    private static final class AuditTable implements GeneSetScoringTable {
        final GeneSetScoringTable source;
        final java.util.Set<String> conditions = new LinkedHashSet<>();
        AuditTable(GeneSetScoringTable source) { this.source = source; }
        public String getName() { return source.getName(); }
        public float getHitScore(String name) { return source.getHitScore(name); }
        public float getMissScore(String name) { return source.getMissScore(name); }
        public RankedList getRankedList() { return source.getRankedList(); }
        public GeneSetScoringTable createTable(GeneSet set, RankedList ranked, RankedList real) {
            GeneSetScoringTable actual = source.createTable(set, ranked, real);
            double mass = 0;
            for (String member : set.getMembers()) {
                if (ranked.getRank(member) < 0) { conditions.add("SOURCE_MEMBER_ABSENT_WITHOUT_PRUNING"); continue; }
                float score = ranked.getScore(member), hit = actual.getHitScore(member);
                if (!Float.isFinite(score)) conditions.add("SOURCE_NONFINITE_RANK_REPLACEMENT");
                if (getName().equals("weighted_p1.5") && !Float.isFinite((float) Math.pow(score, 1.5)))
                    conditions.add("SOURCE_NONFINITE_P15_HIT_REPLACEMENT");
                if (getName().equals("weighted_p2") && !Float.isFinite(score * score))
                    conditions.add("SOURCE_NONFINITE_P2_WEIGHT_REPLACEMENT");
                mass += hit;
            }
            if (!Double.isFinite(mass) || Math.abs(mass - 1) > 5e-6) conditions.add("SOURCE_UNDEFINED_OR_SUBSTITUTED_HIT_MASS");
            return actual; // Forward the exact original table without wrapping or changing its calculations.
        }
    }
    private record Outcome(EnrichmentDb db, EnrichmentResult[] results, Throwable error) { }
    private static GeneSetScoringTable scoring(int index) {
        return switch (index) {
            case 0 -> new GeneSetScoringTables.Classic();
            case 1 -> new GeneSetScoringTables.Weighted();
            case 2 -> new GeneSetScoringTables.WeightedSquared();
            case 3 -> new GeneSetScoringTables.WeightedOnePointFive();
            default -> throw new IllegalArgumentException();
        };
    }
    private static Template template(int columns, int[] zero, int[] one, int coi) {
        TemplateImpl template = new TemplateImpl("classes");
        TemplateImpl.ClassImpl a = new TemplateImpl.ClassImpl("A"), b = new TemplateImpl.ClassImpl("B");
        boolean[] isOne = new boolean[columns]; for (int i : one) isOne[i] = true;
        Template.Item[] items = new Template.Item[columns];
        for (int i = 0; i < columns; i++) { items[i] = TemplateImpl.ItemImpl.createItem(isOne[i] ? "B" : "A", i); template.add(items[i]); }
        for (int i : zero) a.add(items[i]); for (int i : one) b.add(items[i]);
        template.add(a); template.add(b); template.setClassOfInterestIndex(coi); template.setAux(false); template.setContinuous(false); template.makeImmutable();
        return template;
    }
    private static Outcome execute(boolean prerank, boolean phenotype, RankedList ranking, Dataset data, Template template,
                                   GeneSet[] sets, GeneSetScoringTable table, int minimum, int maximum,
                                   int permutations, long seed, Metric metric, SortMode sort, Order order,
                                   TemplateRandomizerType randomizer, Map<String, Boolean> options) {
        try {
            KSTests tests = new KSTests(System.out);
            GeneSetCohort.Generator generator = new GeneSetCohort.Generator(table, minimum, maximum);
            EnrichmentDb db = prerank ? tests.executeGsea(ranking, sets, permutations, new RandomSeedGenerators.Custom(seed), null, generator)
                    : tests.executeGsea(new DatasetTemplate(data, template), sets, permutations, metric, sort, order,
                            new RandomSeedGenerators.Custom(seed), randomizer, options, generator, phenotype, 1, null);
            EnrichmentResult[] results = new PValueCalculatorImpls.GseaImpl("meandiv").calcNPValuesAndFDR(db.getResults());
            return new Outcome(db, results, null);
        } catch (Exception error) { return new Outcome(null, null, error); }
    }
    private static String vector(Vector vector) {
        if (vector == null) return "";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < vector.getSize(); i++) { if (i > 0) s.append('\t'); s.append(vector.getElement(i)); }
        return s.toString();
    }
    private static String signature(Outcome outcome) {
        if (outcome.error != null) return "ERROR:" + outcome.error.getClass().getName() + ":" + outcome.error.getMessage();
        StringBuilder s = new StringBuilder(); RankedList ranked = outcome.db.getRankedList();
        for (int i = 0; i < ranked.getSize(); i++) s.append(ranked.getRankName(i)).append(':').append(ranked.getScore(i)).append(';');
        for (EnrichmentResult result : outcome.results) {
            EnrichmentScore score = result.getScore();
            s.append(result.getGeneSetName()).append(':').append(score.getES()).append(',').append(score.getNES()).append(',')
                    .append(score.getNP()).append(',').append(score.getFDR()).append(',').append(score.getFWER()).append(',')
                    .append(score.getRankAtES()).append(',').append(score.getRankScoreAtES()).append(';')
                    .append(score.getNumHits()).append(';').append(result.getGeneSet().getMembers()).append(';')
                    .append(Arrays.toString(score.getHitIndices())).append(';')
                    .append(vector(result.getRndESS())).append(';').append(vector(score.getESProfile())).append(';')
                    .append(vector(score.getESProfile_point_by_point_opt())).append(';');
        }
        return s.toString();
    }
    private static String base64(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("NEW output directory and exact edition");
        Path out = Path.of(args[0]); Files.createDirectory(out);
        edu.mit.broad.xbench.core.api.Application.registerHandler(new xtools.api.XToolsApplication());
        Random sourceData = new Random(923784L);
        int finite = 0, rejected = 0, sourceErrors = 0;
        for (int c = 0; c < 208; c++) {
            int rows = 33 + c % 32, n0 = 3 + c % 6, n1 = 3 + (c / 3) % 7, columns = n0 + n1;
            int metricIndex = c % 5, weightIndex = (c / 5) % 4, variant = (c / 20) % 5;
            boolean prerank = c >= 160 && c < 192, phenotype = variant != 0 && !prerank;
            boolean median = (c & 1) != 0, biased = (c & 2) != 0, fix = (c & 4) == 0, absolute = (c & 8) != 0, ascending = (c & 16) != 0;
            int coi = (c & 32) == 0 ? 0 : 1, randomizerIndex = Math.max(0, variant - 1);
            int minimum = 3, maximum = 24, permutations = 31;
            long seed = -32987L + c * 981723L;
            if (c == 200) { n0 = 3; n1 = 11; columns = 14; phenotype = true; randomizerIndex = 2; metricIndex = 0; weightIndex = 1; fix = true; }
            if (c == 193) { metricIndex = 0; weightIndex = 1; fix = true; }
            if (c == 196) { prerank = true; phenotype = false; weightIndex = 1; }
            if (c == 204) { n0 = n1 = 8; columns = 16; metricIndex = 3; weightIndex = 1; median = false; biased = false; fix = true; absolute = false; ascending = false; coi = 0; phenotype = true; randomizerIndex = 0; }
            if (c == 205) { metricIndex = 2; weightIndex = 0; median = false; phenotype = true; randomizerIndex = 1; }
            if (c == 201 || c == 202) { minimum = maximum = 3; weightIndex = 1; }
            if (c >= 192) { maximum = minimum == maximum ? maximum : rows; }
            String[] genes = new String[rows], samples = new String[columns]; float[][] matrix = new float[rows][columns];
            for (int j = 0; j < columns; j++) samples[j] = "S" + j;
            for (int r = 0; r < rows; r++) {
                if (c == 196) {
                    StringBuilder name = new StringBuilder(); for (int bit = 0; bit < 7; bit++) name.append((r & 1 << bit) == 0 ? "Aa" : "BB"); genes[r] = name.toString();
                } else genes[r] = "G" + String.format(java.util.Locale.ROOT, "%03d", r);
                for (int j = 0; j < columns; j++) {
                    double value = 5 + .07 * (r % 7) + (j < n0 ? .5 : -.5) * (rows / 2.0 - r) * .022
                            + sourceData.nextDouble() * .23 + (r % 3) * j * .001;
                    if (c % 19 == 0 && r < 3) value = 0;
                    if (c == 193) value = (r % 3 == 0 ? 1 : -1) * 1e-11 + j * 1e-13;
                    if (c == 194) value = r % 2 == 0 ? Float.MAX_VALUE : -Float.MAX_VALUE;
                    if (c == 195 && r == 0 && j < 2) value = Double.NaN;
                    if (c == 197) value = (r % 5 == 0 ? -1 : 1) * value;
                    if (c == 198 && r < 6) value = 0;
                    if (c == 204) value = 1e7 + (j == 0 ? r % 5 : 0);
                    if (c == 205) {
                        if (j < n0) value = j == 0 ? 1e20f : j == 1 ? -1e20f : (r % 5 + 1);
                        else value = j == n0 ? 1e20f : j == columns - 1 ? -1e20f : (r % 3 + 1);
                    }
                    matrix[r][j] = (float) value;
                }
            }
            int[] zero = new int[n0], one = new int[n1];
            for (int j = 0; j < n0; j++) zero[j] = j; for (int j = 0; j < n1; j++) one[j] = j + n0;
            if (c % 17 == 0 || c == 205) { reverse(zero); reverse(one); }
            Template template = template(columns, zero, one, coi);
            Matrix originalMatrix = new Matrix(rows, columns);
            for (int r = 0; r < rows; r++) for (int j = 0; j < columns; j++) originalMatrix.setElement(r, j, matrix[r][j]);
            Dataset data = new DefaultDataset("CASE" + c, originalMatrix, genes, samples, null);
            float[] supplied = new float[rows];
            for (int r = 0; r < rows; r++) supplied[r] = (float) ((rows / 2.0 - r) * .07 + .00013 * (r % 3));
            if (c >= 176 && prerank) for (int r = 0; r < rows; r++) supplied[r] = Math.abs(supplied[r]) + .01f;
            RankedList ranking = new DefaultRankedList("prepared", Arrays.asList(genes), new Vector(supplied, true));
            List<String[]> rawSets = new ArrayList<>();
            rawSets.add(Arrays.copyOfRange(genes, 0, 7)); rawSets.add(Arrays.copyOfRange(genes, rows - 9, rows));
            rawSets.add(Arrays.copyOfRange(genes, rows / 2 - 4, rows / 2 + 4));
            rawSets.add(new String[]{genes[0], genes[3], "ABSENT", genes[11], genes[17], genes[21]});
            rawSets.add(new String[]{genes[1]}); rawSets.add(new String[0]);
            if (c >= 192) rawSets.add(genes.clone());
            if (c == 202) rawSets.set(3, new String[]{genes[0], genes[3], genes[11], genes[17], genes[21]});
            if (c == 203) rawSets.set(0, new String[]{genes[0], genes[1], genes[1], genes[2], genes[3], genes[4]});
            GeneSet[] sets = new GeneSet[rawSets.size()];
            for (int g = 0; g < sets.length; g++) sets[g] = new GeneSet("SET" + g, rawSets.get(g));
            Metric metric = Metrics.lookupMetric(METRICS[metricIndex]);
            SortMode sort = absolute ? SortMode.ABSOLUTE : SortMode.REAL; Order order = ascending ? Order.ASCENDING : Order.DESCENDING;
            Map<String, Boolean> options = Map.of(Headers.USE_MEDIAN, median, Headers.USE_BIASED, biased, Headers.FIX_LOW, fix);
            TemplateRandomizerType randomizer = TemplateRandomizerType.lookupRandomizerType(RANDOMIZERS[randomizerIndex]);
            Outcome stock = execute(prerank, phenotype, ranking, data, template, sets, scoring(weightIndex), minimum, maximum,
                    permutations, seed, metric, sort, order, randomizer, options);
            AuditTable audit = new AuditTable(scoring(weightIndex));
            Outcome audited = execute(prerank, phenotype, ranking, data, template, sets, audit, minimum, maximum,
                    permutations, seed, metric, sort, order, randomizer, options);
            if (stock.error == null && !signature(stock).equals(signature(audited))) throw new AssertionError("Weight observer changed stock output in case " + c);
            boolean inputFinite = true;
            for (float[] row : matrix) for (float value : row) inputFinite &= Float.isFinite(value);
            if (!prerank && !inputFinite) audit.conditions.add("PREPARED_INPUT_NONFINITE_REFUSED");
            if (c == 203) audit.conditions.add("PREPARED_DUPLICATE_SET_MEMBER_REFUSED");
            if (phenotype && randomizerIndex == 2 && n0 - n1 / 2 < 0) audit.conditions.add("ORIGINAL_CONSTRAINED_COUNTS_NEGATIVE_REFUSED");
            boolean completeFinite = stock.error == null;
            if (stock.error == null) {
                for (int r = 0; r < stock.db.getRankedList().getSize(); r++) completeFinite &= Float.isFinite(stock.db.getRankedList().getScore(r));
                for (EnrichmentResult result : stock.results) {
                    EnrichmentScore s = result.getScore();
                    completeFinite &= Float.isFinite(s.getES()) && Float.isFinite(s.getNES()) && Float.isFinite(s.getNP()) && Float.isFinite(s.getFDR()) && Float.isFinite(s.getFWER());
                    for (int p = 0; p < result.getRndESS().getSize(); p++) completeFinite &= Float.isFinite(result.getRndESS().getElement(p));
                }
            }
            boolean nativeAdmit = completeFinite && audit.conditions.isEmpty();
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(out.resolve(String.format(java.util.Locale.ROOT, "case-%03d.tsv", c)), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW))) {
                writer.println("CONFIG\t" + c + "\t" + (prerank ? "preranked" : phenotype ? "phenotype" : "gene_set") + "\t" + args[1]
                        + "\t" + METRICS[metricIndex] + "\t" + median + "\t" + biased + "\t" + fix + "\t" + absolute + "\t" + ascending
                        + "\t" + coi + "\t" + RANDOMIZERS[randomizerIndex] + "\t" + WEIGHTS[weightIndex] + "\t" + minimum + "\t" + maximum + "\t" + permutations + "\t" + seed);
                writer.println("NATIVE_POLICY\t" + (nativeAdmit ? "ADMIT" : "REFUSE"));
                writer.println("SOURCE_PARITY\t" + (stock.error == null ? "EXACT_EXPORTED_STOCK_AND_AUDITED_OBJECTS" : "STOCK_FAILED"));
                for (String condition : audit.conditions) writer.println("CONDITION\t" + base64(condition));
                for (int r = 0; r < rows; r++) {
                    writer.print("DATA\t" + genes[r]); for (float value : matrix[r]) writer.print("\t" + value); writer.println();
                    writer.println("PRERANK\t" + genes[r] + "\t" + supplied[r]);
                }
                writer.print("GROUP0"); for (int value : zero) writer.print("\t" + value); writer.println();
                writer.print("GROUP1"); for (int value : one) writer.print("\t" + value); writer.println();
                for (int g = 0; g < rawSets.size(); g++) { writer.print("INPUT_SET\tSET" + g); for (String member : rawSets.get(g)) writer.print("\t" + member); writer.println(); }
                if (stock.error != null) {
                    writer.println("RESULT_STATUS\tSOURCE_ERROR"); writer.println("ERROR\t" + base64(stock.error.toString())); sourceErrors++;
                } else {
                    writer.println("RESULT_STATUS\t" + (completeFinite ? "COMPLETE_FINITE" : "COMPLETE_NONFINITE"));
                    for (String warning : stock.db.getWarnings()) writer.println("WARNING\t" + base64(warning));
                    RankedList ranked = stock.db.getRankedList();
                    for (int r = 0; r < ranked.getSize(); r++) {
                        String gene = ranked.getRankName(r);
                        double rawMetric = prerank ? ranked.getScore(r) : metric.getScore(data.getRow(data.getRowIndex(gene)), template, options);
                        writer.println("RANK\t" + r + "\t" + gene + "\t" + ranked.getScore(r) + "\t" + Double.toString(rawMetric));
                    }
                    for (EnrichmentResult result : stock.results) writeResult(writer, result);
                }
            }
            if (nativeAdmit) finite++; else rejected++;
            if (c % 25 == 0) System.out.println("EXPORTED_ENGINE_CASE\t" + args[1] + "\t" + c);
        }
        Files.writeString(out.resolve("COUNTS.tsv"), "source_edition\tadmitted\trefused\tsource_errors\n" + args[1] + "\t" + finite + "\t" + rejected + "\t" + sourceErrors + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("EXPORTED_ORIGINAL_ENGINE_CASES\t" + args[1] + "\tadmitted=" + finite + "\trefused=" + rejected + "\tsource_errors=" + sourceErrors);
    }
    private static void reverse(int[] values) { for (int a = 0, b = values.length - 1; a < b; a++, b--) { int x = values[a]; values[a] = values[b]; values[b] = x; } }
    private static void writeResult(PrintWriter writer, EnrichmentResult result) {
        String name = result.getGeneSetName(); EnrichmentScore score = result.getScore();
        writer.println("SCORE\t" + name + "\t" + score.getES() + "\t" + score.getNES() + "\t" + score.getNP() + "\t" + score.getFDR() + "\t" + score.getFWER()
                + "\t" + score.getRankAtES() + "\t" + score.getRankScoreAtES());
        writer.print("MEMBERS\t" + name); for (String member : result.getGeneSet().getMembers()) writer.print("\t" + member); writer.println();
        writer.println("NULL\t" + name + "\t" + vector(result.getRndESS()));
        writer.println("HIT_PROFILE\t" + name + "\t" + vector(score.getESProfile()));
        writer.println("FULL_PROFILE\t" + name + "\t" + vector(score.getESProfile_point_by_point_opt()));
        writer.print("HITS\t" + name); for (int hit : score.getHitIndices()) writer.print("\t" + hit); writer.println();
    }
}
