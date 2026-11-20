/* Source-defined GSEA arithmetic/permutations derived from GSEA Desktop.
 * Original copyright and BSD-style terms: META-INF/licenses/GSEA-DESKTOP-LICENSE.txt.
 */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Native source-core GSEA on prepared literal identifiers and finite float data.
 * Performs ranking, original randomization, score walks and meandiv empirical
 * statistics. No executable, identifier mapping, expression transformation,
 * collection retrieval or vendor report generation is invoked.
 */
public final class BroadGseaEngine {
    private BroadGseaEngine() { }

    public enum Metric { SIGNAL_TO_NOISE, T_TEST, DIFFERENCE, RATIO, LOG2_RATIO }
    public enum Randomization { NO_BALANCE, BALANCE_CLASS0, BALANCE_CLASS1, EQUALIZE_AND_BALANCE }

    public record Limits(long maximumDenseCells, long maximumNullCells, long maximumWork,
                         int maximumPermutations) {
        public Limits {
            require(maximumDenseCells > 0 && maximumNullCells > 0 && maximumWork > 0
                    && maximumPermutations > 0, "Declare positive capacity limits");
        }
    }
    public record Settings(String edition, int permutations, long seed, int minimumSize,
                           int maximumSize, BroadGseaStatistics.Weight weight, Limits limits) {
        public Settings {
            require(Set.of("4.3.2", "4.3.3", "4.4.0").contains(edition), "Declare a supported exact source edition");
            Objects.requireNonNull(weight, "weight"); Objects.requireNonNull(limits, "limits");
            require(permutations > 0 && permutations <= limits.maximumPermutations,
                    "Permutation count exceeds the declared supported capacity");
            require(minimumSize >= 0 && maximumSize >= minimumSize, "Invalid original set-size limits");
        }
    }
    public record RankingOptions(Metric metric, boolean median, boolean biasedVariance,
                                 boolean fixLowVariance, boolean absoluteSort, boolean ascending,
                                 int classOfInterest) {
        public RankingOptions {
            Objects.requireNonNull(metric, "metric");
            require(classOfInterest == 0 || classOfInterest == 1, "Declare class of interest 0 or 1");
        }
    }
    public record GeneSet(String name, List<String> members) {
        public GeneSet {
            require(name != null && !name.isEmpty(), "Set name must be nonempty");
            members = List.copyOf(members);
            require(new HashSet<>(members).size() == members.size(), "Duplicate set member; no automatic deduplication");
            require(members.stream().allMatch(x -> x != null && !x.isEmpty()), "Empty/null set member");
        }
    }
    /** Member positions preserve their within-class order, including constrained permutations. */
    public record Grouping(int[] class0, int[] class1) {
        public Grouping { class0 = class0.clone(); class1 = class1.clone(); }
        @Override public int[] class0() { return class0.clone(); }
        @Override public int[] class1() { return class1.clone(); }
        public static Grouping fromLabels(int[] labels) {
            int zero = 0;
            for (int label : labels) { require(label == 0 || label == 1, "Class label must be 0 or 1"); if (label == 0) zero++; }
            int[] a = new int[zero], b = new int[labels.length - zero]; int i = 0, j = 0;
            for (int p = 0; p < labels.length; p++) if (labels[p] == 0) a[i++] = p; else b[j++] = p;
            return new Grouping(a, b);
        }
    }
    public record Ranked(String[] genes, float[] scores, int[] sourceRows, double[] metricValues) {
        public Ranked {
            genes = genes.clone(); scores = scores.clone(); sourceRows = sourceRows.clone(); metricValues = metricValues.clone();
            require(genes.length == scores.length && scores.length == sourceRows.length && scores.length == metricValues.length,
                    "Ranked fields have different lengths");
        }
        @Override public String[] genes() { return genes.clone(); }
        @Override public float[] scores() { return scores.clone(); }
        @Override public int[] sourceRows() { return sourceRows.clone(); }
        @Override public double[] metricValues() { return metricValues.clone(); }
    }
    public record Disposition(String name, int inputMembers, List<String> matchedMembers,
                              List<String> absentMembers, String status, boolean sourceSizeFilteringDisabled) {
        public Disposition { matchedMembers = List.copyOf(matchedMembers); absentMembers = List.copyOf(absentMembers); }
    }
    public record SetResult(String name, List<String> members, BroadGseaStatistics.Score observed,
                            float[] nullEs) {
        public SetResult { members = List.copyOf(members); nullEs = nullEs.clone(); }
        @Override public float[] nullEs() { return nullEs.clone(); }
    }
    public record Result(String edition, String nullModel, Ranked ranking, List<SetResult> sets,
                         List<Disposition> dispositions, BroadGseaStatistics.Family statistics, long workUsed) {
        public Result { sets = List.copyOf(sets); dispositions = List.copyOf(dispositions); }
    }
    /** Callers may persist events/partials; a callback failure aborts rather than admitting a result. */
    public interface Progress {
        default void disposition(Disposition value) { }
        default void ranking(int permutation, Ranked value) { }
        default void observed(int set, String name, BroadGseaStatistics.Score value) { }
        default void nullValue(int set, String name, int permutation, float value) { }
    }
    private static final Progress NO_PROGRESS = new Progress() { };
    /** Original inputs/partial callbacks remain owned by the caller; this carries the failing location. */
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String phase, set;
        private final int permutation;
        Failure(String phase, String set, int permutation, RuntimeException cause) {
            super("GSEA " + phase + " failed" + (set == null ? "" : " for set '" + set + "'")
                    + (permutation < 0 ? "" : " at permutation " + (permutation + 1) + " (index " + permutation + ")")
                    + ": " + cause.getMessage(), cause);
            this.phase = phase; this.set = set; this.permutation = permutation;
        }
        public String phase() { return phase; }
        public String set() { return set; }
        public int permutation() { return permutation; }
    }
    private static final class Budget {
        private final long maximum;
        private long used;
        Budget(long maximum) { this.maximum = maximum; }
        void charge(long amount) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("GSEA interrupted; no complete result");
            require(amount >= 0 && amount <= maximum - used, "GSEA exceeds declared maximumWork; no truncation applied");
            used += amount;
        }
    }

    /** Scores are already in the explicitly chosen rank order; this method never sorts them. */
    public static Result preranked(String[] genes, float[] scores, List<GeneSet> sets,
                                    Settings settings, Progress progress) {
        validateGenes(genes); require(genes.length == scores.length, "Rank identifiers/scores differ");
        for (float score : scores) finite(score, "input rank score");
        int[] rows = new int[genes.length]; double[] metric = new double[genes.length];
        for (int i = 0; i < rows.length; i++) { rows[i] = i; metric[i] = scores[i]; }
        Ranked ranked = new Ranked(genes, scores, rows, metric);
        Budget budget = new Budget(settings.limits.maximumWork);
        return geneSetNull(ranked, sets, settings, progress == null ? NO_PROGRESS : progress, budget);
    }

    public static Result expressionGeneSetNull(String[] genes, float[][] expression, Grouping grouping,
                                               RankingOptions options, List<GeneSet> sets,
                                               Settings settings, Progress progress) {
        Budget budget = new Budget(settings.limits.maximumWork);
        validateExpression(genes, expression, grouping, options, settings.limits);
        Ranked ranked = rank(genes, expression, grouping, options, budget);
        return geneSetNull(ranked, sets, settings, progress == null ? NO_PROGRESS : progress, budget);
    }

    public static Result expressionPhenotypeNull(String[] genes, float[][] expression, Grouping grouping,
                                                 RankingOptions options, Randomization randomization,
                                                 List<GeneSet> sets, Settings settings, Progress progress) {
        Objects.requireNonNull(randomization, "randomization");
        Progress observer = progress == null ? NO_PROGRESS : progress;
        Budget budget = new Budget(settings.limits.maximumWork);
        validateExpression(genes, expression, grouping, options, settings.limits);
        Ranked real = rank(genes, expression, grouping, options, budget);
        observer.ranking(-1, real);
        Prepared prepared = prepare(real, sets, settings, observer, budget);
        BroadGseaStatistics.Score[] observed = observed(real, prepared.sets, settings, observer, budget);
        float[][] nulls = new float[prepared.sets.size()][settings.permutations];
        Random random = new Random(settings.seed);
        for (int p = 0; p < settings.permutations; p++) {
            Grouping permuted;
            Ranked ranked;
            try {
                permuted = randomGrouping(grouping, expression[0].length, randomization, random, budget);
                ranked = rank(genes, expression, permuted, options, budget);
            } catch (RuntimeException failure) { throw new Failure("phenotype_ranking", null, p, failure); }
            observer.ranking(p, ranked);
            Map<String, Integer> positions = positions(ranked.genes);
            for (int g = 0; g < prepared.sets.size(); g++) {
                GeneSet set = prepared.sets.get(g);
                float es;
                try { es = scoreOnly(ranked.scores, indices(set.members, positions), settings.weight, budget); }
                catch (RuntimeException failure) { throw new Failure("phenotype_null_score", set.name, p, failure); }
                nulls[g][p] = es; observer.nullValue(g, set.name, p, es);
            }
        }
        return finish("phenotype", real, prepared, observed, nulls, settings, budget);
    }

    /** Ranking only, useful for independent source comparisons and explicit preparation. */
    public static Ranked rankExpression(String[] genes, float[][] expression, Grouping grouping,
                                         RankingOptions options, Limits limits) {
        validateExpression(genes, expression, grouping, options, limits);
        return rank(genes, expression, grouping, options, new Budget(limits.maximumWork));
    }

    private static Ranked rank(String[] genes, float[][] matrix, Grouping grouping,
                                RankingOptions options, Budget budget) {
        int n = genes.length;
        budget.charge((long) n * matrix[0].length + (long) n * (4 + log2Ceiling(n)));
        double[] values = new double[n]; Integer[] order = new Integer[n];
        for (int r = 0; r < n; r++) {
            values[r] = metric(matrix[r], grouping, options);
            finite(values[r], "nonfinite original metric for feature '" + genes[r] + "' (row index " + r + ")");
            finite((float) values[r], "nonfinite source-float metric for feature '" + genes[r] + "' (row index " + r + ")");
            order[r] = r;
        }
        Arrays.sort(order, (a, b) -> {
            double x = options.absoluteSort ? Math.abs(values[a]) : values[a];
            double y = options.absoluteSort ? Math.abs(values[b]) : values[b];
            // The original comparator keeps +/-0 tied and preserves input order.
            if (x < y) return options.ascending ? -1 : 1;
            if (x > y) return options.ascending ? 1 : -1;
            return 0;
        });
        String[] sorted = new String[n]; float[] scores = new float[n]; int[] sourceRows = new int[n]; double[] raw = new double[n];
        for (int r = 0; r < n; r++) {
            int row = order[r]; sorted[r] = genes[row]; scores[r] = (float) values[row]; sourceRows[r] = row; raw[r] = values[row];
        }
        return new Ranked(sorted, scores, sourceRows, raw);
    }

    private static double metric(float[] row, Grouping grouping, RankingOptions options) {
        int[] x = options.classOfInterest == 0 ? grouping.class0 : grouping.class1;
        int[] y = options.classOfInterest == 0 ? grouping.class1 : grouping.class0;
        double mx = mean(row, x), my = mean(row, y);
        double cx = options.median ? median(row, x) : mx, cy = options.median ? median(row, y) : my;
        return switch (options.metric) {
            case DIFFERENCE -> cx - cy;
            case RATIO -> cx / cy;
            case LOG2_RATIO -> Math.log(cx / cy) / Math.log(2.0);
            case SIGNAL_TO_NOISE -> (cx - cy) / (standardDeviation(row, x, mx, options)
                    + standardDeviation(row, y, my, options));
            case T_TEST -> {
                double vx = variance(row, x, mx, options), vy = variance(row, y, my, options);
                double denominator = options.biasedVariance
                        ? Math.sqrt(vx / (x.length - 1) + vy / (y.length - 1))
                        : Math.sqrt(vx / x.length + vy / y.length);
                yield (cx - cy) / denominator;
            }
        };
    }
    private static double mean(float[] row, int[] indices) {
        double sum = 0; for (int i : indices) sum += row[i]; return sum / indices.length;
    }
    private static double median(float[] row, int[] indices) {
        float[] values = new float[indices.length]; for (int i = 0; i < values.length; i++) values[i] = row[indices[i]];
        Arrays.sort(values); int a = (values.length - 1) / 2, b = values.length / 2;
        return a == b ? values[a] : (values[a] + values[b]) / 2;
    }
    private static double rawVariance(float[] row, int[] indices, double mean, boolean biased) {
        int divisor = indices.length - (biased ? 0 : 1);
        if (divisor <= 0) return 0;
        double sum = 0;
        for (int i : indices) { double delta = row[i] - mean; sum += delta * delta; }
        return sum / divisor;
    }
    private static double standardDeviation(float[] row, int[] indices, double mean, RankingOptions options) {
        double value = Math.sqrt(rawVariance(row, indices, mean, options.biasedVariance));
        if (options.fixLowVariance) value = Math.max(value, Math.abs(mean) <= 1e-9 ? .20 : .20 * Math.abs(mean));
        return value;
    }
    private static double variance(float[] row, int[] indices, double mean, RankingOptions options) {
        if (!options.fixLowVariance) return rawVariance(row, indices, mean, options.biasedVariance);
        double sd = standardDeviation(row, indices, mean, options); return sd * sd;
    }

    private static Result geneSetNull(Ranked ranked, List<GeneSet> sets, Settings settings,
                                       Progress progress, Budget budget) {
        progress.ranking(-1, ranked);
        Prepared prepared = prepare(ranked, sets, settings, progress, budget);
        BroadGseaStatistics.Score[] observed = observed(ranked, prepared.sets, settings, progress, budget);
        float[][] nulls = new float[prepared.sets.size()][settings.permutations];
        Random random = new Random(settings.seed);
        Map<String, Integer> positions = positions(ranked.genes);
        for (int g = 0; g < prepared.sets.size(); g++) {
            GeneSet set = prepared.sets.get(g);
            for (int p = 0; p < settings.permutations; p++) {
                int[] selected = sample(set.members.size(), ranked.genes.length, true, random, budget);
                // Original GeneSetGenerators inserts names into a default HashSet before constructing the set.
                Set<String> hashOrder = new HashSet<>();
                for (int index : selected) hashOrder.add(ranked.genes[index]);
                int[] memberOrder = new int[hashOrder.size()]; int i = 0;
                for (String name : hashOrder) memberOrder[i++] = positions.get(name);
                float es;
                try { es = scoreOnly(ranked.scores, memberOrder, settings.weight, budget); }
                catch (RuntimeException failure) { throw new Failure("gene_set_null_score", set.name, p, failure); }
                nulls[g][p] = es; progress.nullValue(g, set.name, p, es);
            }
        }
        return finish("gene_set", ranked, prepared, observed, nulls, settings, budget);
    }
    private record Prepared(List<GeneSet> sets, List<Disposition> dispositions) { }
    private static Prepared prepare(Ranked ranked, List<GeneSet> originals, Settings settings, Progress progress, Budget budget) {
        budget.charge(ranked.genes.length);
        Map<String, Integer> positions = positions(ranked.genes);
        List<GeneSet> included = new ArrayList<>(); List<Disposition> dispositions = new ArrayList<>(); Set<String> names = new HashSet<>();
        boolean disabled = settings.minimumSize == settings.maximumSize;
        for (GeneSet set : originals) {
            budget.charge(1L + set.members.size());
            require(names.add(set.name), "Duplicate set name; no automatic selection");
            List<String> matched = new ArrayList<>(), absent = new ArrayList<>();
            for (String member : set.members) (positions.containsKey(member) ? matched : absent).add(member);
            String status;
            if (disabled) {
                status = set.members.isEmpty() ? "zero_members" : "included_size_filter_disabled";
                require(absent.isEmpty() || set.members.isEmpty(), "Equal source size limits disable membership pruning; absent members cannot be silently removed: " + set.name);
            } else if (set.members.size() < settings.minimumSize) status = "input_below_minimum";
            else if (matched.size() < settings.minimumSize) status = "matched_below_minimum";
            else if (matched.size() > settings.maximumSize) status = "matched_above_maximum";
            else if (matched.isEmpty()) status = "zero_members";
            else status = "included";
            Disposition disposition = new Disposition(set.name, set.members.size(), matched, absent, status, disabled);
            dispositions.add(disposition); progress.disposition(disposition);
            if (status.startsWith("included")) included.add(new GeneSet(set.name, disabled ? set.members : matched));
        }
        require(!included.isEmpty(), "After source pruning, no nonempty gene sets remain");
        require((long) included.size() * settings.permutations <= settings.limits.maximumNullCells, "Null family exceeds explicit cell capacity");
        require((long) included.size() * ranked.genes.length <= settings.limits.maximumDenseCells, "Observed score profiles exceed explicit dense capacity");
        return new Prepared(List.copyOf(included), List.copyOf(dispositions));
    }
    private static BroadGseaStatistics.Score[] observed(Ranked ranked, List<GeneSet> sets, Settings settings,
                                                       Progress progress, Budget budget) {
        Map<String, Integer> positions = positions(ranked.genes);
        BroadGseaStatistics.Score[] scores = new BroadGseaStatistics.Score[sets.size()];
        for (int g = 0; g < scores.length; g++) {
            GeneSet set = sets.get(g); int[] indices = indices(set.members, positions);
            budget.charge((long) ranked.genes.length + indices.length);
            try {
                scores[g] = indices.length == ranked.genes.length ? fullSet(ranked.scores, indices, settings.weight)
                        : BroadGseaStatistics.score(ranked.scores, indices, settings.weight);
            } catch (RuntimeException failure) { throw new Failure("observed_score", set.name, -1, failure); }
            progress.observed(g, set.name, scores[g]);
        }
        return scores;
    }
    private static Result finish(String model, Ranked ranked, Prepared prepared, BroadGseaStatistics.Score[] observed,
                                 float[][] nulls, Settings settings, Budget budget) {
        float[] es = new float[observed.length]; List<SetResult> results = new ArrayList<>();
        for (int g = 0; g < es.length; g++) {
            es[g] = observed[g].es(); GeneSet set = prepared.sets.get(g);
            results.add(new SetResult(set.name, set.members, observed[g], nulls[g]));
        }
        budget.charge((long) es.length * settings.permutations * (5L + log2Ceiling(es.length)));
        BroadGseaStatistics.Family statistics;
        try { statistics = BroadGseaStatistics.meandiv(es, nulls, settings.limits.maximumNullCells); }
        catch (RuntimeException failure) { throw new Failure("empirical_statistics", null, -1, failure); }
        return new Result(settings.edition, model, ranked, results, prepared.dispositions, statistics, budget.used);
    }

    /** Gap-only max deviation, with exactly the same float weights/double updates as the deep original walk. */
    private static float scoreOnly(float[] scores, int[] members, BroadGseaStatistics.Weight weight, Budget budget) {
        budget.charge((long) members.length * (3L + log2Ceiling(members.length)));
        float total = totalWeight(scores, members, weight);
        int[] sorted = members.clone(); Arrays.sort(sorted);
        float miss = 1.0f / ((float) scores.length - (float) members.length);
        double running = 0; float es = 0; int last = -1;
        for (int r : sorted) {
            int gap = r - last - 1;
            if (gap > 0) {
                running -= gap * (double) miss;
                if (Math.abs(es) < Math.abs(running)) es = (float) running;
            }
            running += hit(scores[r], members.length, total, weight);
            if (Math.abs(es) < Math.abs(running)) es = (float) running;
            last = r;
        }
        if (last != scores.length - 1) {
            int gap = scores.length - last - 2;
            if (gap > 0) {
                running -= gap * (double) miss;
                if (Math.abs(es) < Math.abs(running)) es = (float) running;
            }
            running -= (double) miss;
            if (Math.abs(es) < Math.abs(running)) es = (float) running;
        }
        finite(es, "computed null ES"); return es;
    }
    private static BroadGseaStatistics.Score fullSet(float[] scores, int[] members, BroadGseaStatistics.Weight weight) {
        float total = totalWeight(scores, members, weight); double running = 0; float es = 0, at = 0; int rank = 0;
        int[] hits = new int[scores.length]; float[] profile = new float[scores.length];
        for (int r = 0; r < scores.length; r++) {
            running += hit(scores[r], members.length, total, weight); profile[r] = (float) running; hits[r] = r;
            if (Math.abs(es) < Math.abs(running)) { es = (float) running; rank = r; at = scores[r]; }
        }
        return new BroadGseaStatistics.Score(es, rank, at, hits, profile, profile);
    }
    private static float totalWeight(float[] scores, int[] members, BroadGseaStatistics.Weight weight) {
        if (weight == BroadGseaStatistics.Weight.CLASSIC) return members.length;
        float sum = 0;
        for (int index : members) {
            float value = scores[index];
            float powered = switch (weight) {
                case CLASSIC -> 1;
                case WEIGHTED -> Math.abs(value);
                case WEIGHTED_P2 -> value * value;
                case WEIGHTED_P1_5 -> (float) Math.pow(Math.abs(value), 1.5);
            };
            finite(powered, "source would replace nonfinite powered weight"); sum += powered;
        }
        require(sum > 0 && Float.isFinite(sum), "Zero/nonfinite source weight denominator; no repair"); return sum;
    }
    private static float hit(float score, int size, float total, BroadGseaStatistics.Weight weight) {
        float value = switch (weight) {
            case CLASSIC -> 1.0f / size;
            case WEIGHTED -> Math.abs(score) / total;
            case WEIGHTED_P2 -> (score * score) / total;
            case WEIGHTED_P1_5 -> (float) Math.pow(score, 1.5) / total;
        };
        finite(value, "source would replace invalid hit/power; no repair"); return value;
    }

    private static Grouping randomGrouping(Grouping original, int width, Randomization mode, Random random, Budget budget) {
        if (mode == Randomization.NO_BALANCE) {
            int[] permutation = sample(width, width, false, random, budget);
            int[] originalLabels = new int[width]; for (int i : original.class1) originalLabels[i] = 1;
            int[] labels = new int[width]; for (int i = 0; i < width; i++) labels[i] = originalLabels[permutation[i]];
            return Grouping.fromLabels(labels);
        }
        int from1 = mode == Randomization.BALANCE_CLASS1 ? original.class1.length / 2
                : mode == Randomization.EQUALIZE_AND_BALANCE ? original.class0.length / 2
                : original.class0.length - original.class0.length / 2;
        int from0 = original.class0.length - from1;
        require(from0 >= 0 && from0 <= original.class0.length && from1 >= 0 && from1 <= original.class1.length,
                "Original constrained randomization has impossible class counts; no correction");
        int[] a = sample(original.class0.length, original.class0.length, false, random, budget);
        int[] b = sample(original.class1.length, original.class1.length, false, random, budget);
        int[] zero = new int[original.class0.length], one = new int[original.class1.length]; int x = 0, y = 0;
        for (int i = 0; i < a.length; i++) if (i < from0) zero[x++] = original.class0[a[i]]; else one[y++] = original.class0[a[i]];
        for (int i = 0; i < b.length; i++) if (i < from1) zero[x++] = original.class1[b[i]]; else one[y++] = original.class1[b[i]];
        return new Grouping(zero, one);
    }
    private static int[] sample(int count, int population, boolean fullShortcut, Random random, Budget budget) {
        int[] result = new int[count];
        if (fullShortcut && count == population) {
            for (int i = 0; i < count; i++) result[i] = i; return result;
        }
        Set<Integer> seen = new HashSet<>(); int n = 0;
        while (n < count) {
            budget.charge(1); int value = random.nextInt(population);
            if (seen.add(value)) result[n++] = value;
        }
        return result;
    }
    private static void validateExpression(String[] genes, float[][] expression, Grouping grouping,
                                            RankingOptions options, Limits limits) {
        validateGenes(genes); Objects.requireNonNull(options, "options"); Objects.requireNonNull(grouping, "grouping");
        require(expression.length == genes.length && expression.length > 0, "Expression identifiers/rows differ");
        require(expression[0] != null && expression[0].length > 0, "Expression matrix has no samples");
        int width = expression[0].length;
        require((long) genes.length * width <= limits.maximumDenseCells, "Expression exceeds explicit dense capacity");
        boolean[] seen = new boolean[width];
        for (int[] indices : new int[][]{grouping.class0, grouping.class1}) {
            int minimum = options.metric == Metric.SIGNAL_TO_NOISE || options.metric == Metric.T_TEST ? 3 : 1;
            require(indices.length >= minimum, "Too few samples for original ranking metric");
            for (int index : indices) { require(index >= 0 && index < width && !seen[index], "Invalid/repeated sample membership"); seen[index] = true; }
        }
        for (boolean value : seen) require(value, "Unassigned samples require explicit preparation; no automatic extraction");
        for (int r = 0; r < expression.length; r++) {
            float[] row = expression[r];
            require(row != null && row.length == width, "Ragged expression matrix");
            for (int c = 0; c < row.length; c++) finite(row[c], "nonfinite prepared expression for feature '" + genes[r] + "' at column index " + c);
        }
    }
    private static void validateGenes(String[] genes) {
        require(genes.length > 0, "Empty ranked universe"); Set<String> seen = new HashSet<>();
        for (String gene : genes) require(gene != null && !gene.isEmpty() && seen.add(gene), "Missing/duplicate literal feature identifier; no automatic repair");
    }
    private static Map<String, Integer> positions(String[] genes) {
        Map<String, Integer> result = new HashMap<>(); for (int i = 0; i < genes.length; i++) result.put(genes[i], i); return result;
    }
    private static int[] indices(List<String> names, Map<String, Integer> positions) {
        int[] result = new int[names.size()];
        for (int i = 0; i < result.length; i++) { Integer index = positions.get(names.get(i)); require(index != null, "Set member absent after source preparation"); result[i] = index; }
        return result;
    }
    private static int log2Ceiling(int value) { return value <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(value - 1); }
    private static void finite(double value, String message) { if (!Double.isFinite(value)) throw new ArithmeticException(message); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
