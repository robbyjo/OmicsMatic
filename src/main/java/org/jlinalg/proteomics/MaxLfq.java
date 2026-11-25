/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source algorithm: iq 2.3.0, Thang V. Pham and Pham Huy Chau Long (BSD-3-Clause).
 * See META-INF/licenses/IQ-BSD-3-Clause.txt and the source-bound workflow guide.
 */
package org.jlinalg.proteomics;

import java.util.*;

/** Native iq 2.3.0 fast_MaxLFQ aggregation. Inputs are already normalized log2 intensities. */
public final class MaxLfq {
    private MaxLfq() { }
    private static final double LOG2 = StrictMath.log(2.0);
    private static final double SOURCE_EPSILON = 1e-20;

    /** Complete populations, numeric scans and multiply-add work are bounded; no truncation. */
    public record Limits(int samples, int ions, long cells, long pairComparisons,
                         long numericBytes, long work, int iterations) {
        public Limits {
            if (samples < 1 || samples == Integer.MAX_VALUE || ions < 1 || cells < 1
                    || pairComparisons < 1 || numericBytes < 1 || work < 1 || iterations < 1)
                throw new IllegalArgumentException("Invalid complete MaxLFQ capacities");
        }
    }
    public record Observation(String protein, String sample, String ion, double log2Intensity) {
        public Observation {
            for (String id : new String[]{protein, sample, ion})
                if (id == null || id.isEmpty() || id.codePoints().anyMatch(c -> Character.isISOControl(c) || c >= 0xd800 && c <= 0xdfff))
                    throw new IllegalArgumentException("Require literal nonempty MaxLFQ identifiers without control characters");
            if (!Double.isFinite(log2Intensity)) throw new IllegalArgumentException("fast_MaxLFQ requires finite normalized log2 values; no NA/Inf filtering is inferred");
        }
    }
    public record Result(double[] estimate, int[] component, String annotation, int iterations,
                         double relativeResidual, long observedCells, long ratios, long work) {
        public Result { estimate = estimate.clone(); component = component.clone(); }
        @Override public double[] estimate() { return estimate.clone(); }
        @Override public int[] component() { return component.clone(); }
    }
    public record Protein(String id, List<String> ions, Result result, long inputRows, long duplicateRows) {
        public Protein { ions = List.copyOf(ions); }
    }
    public record Table(List<String> samples, List<Protein> proteins, long inputRows, long duplicateRows, long work) {
        public Table { samples = List.copyOf(samples); proteins = List.copyOf(proteins); }
    }
    private static final class Budget {
        final Limits limits; final Runnable cancellation; long used;
        Budget(Limits limits, Runnable cancellation) { this.limits = Objects.requireNonNull(limits); this.cancellation = Objects.requireNonNull(cancellation); }
        void add(long amount) {
            cancellation.run();
            if (amount < 0 || amount > limits.work() - used) throw new IllegalArgumentException("Complete MaxLFQ work capacity exceeded; no result admitted");
            used += amount;
        }
    }

    /** First-observed protein/sample/ion order; duplicate cells sum in linear space as iq 2.3.0. */
    public static Table fast(List<Observation> observations, Limits limits, Runnable cancellation) {
        Objects.requireNonNull(observations); var budget = new Budget(limits, cancellation); budget.add(observations.size());
        if (observations.isEmpty()) throw new IllegalArgumentException("No MaxLFQ quantitative values");
        var samples = new LinkedHashMap<String,Integer>(); var groups = new LinkedHashMap<String,List<Observation>>();
        for (Observation row : observations) {
            Objects.requireNonNull(row); samples.computeIfAbsent(row.sample(), ignored -> samples.size());
            if (samples.size() > limits.samples()) throw new IllegalArgumentException("Complete MaxLFQ sample capacity exceeded");
            groups.computeIfAbsent(row.protein(), ignored -> new ArrayList<>()).add(row);
        }
        long retained = Math.addExact(Math.multiplyExact(Math.multiplyExact(12L, groups.size()), samples.size()), 256L * groups.size());
        if (retained >= limits.numericBytes()) throw new IllegalArgumentException("Complete retained MaxLFQ protein/sample result memory exceeds numeric_bytes");
        var working = new Limits(limits.samples(), limits.ions(), limits.cells(), limits.pairComparisons(), limits.numericBytes() - retained, limits.work(), limits.iterations());
        var proteins = new ArrayList<Protein>(); long duplicates = 0;
        for (var group : groups.entrySet()) {
            var ions = new LinkedHashMap<String,Integer>();
            for (Observation row : group.getValue()) ions.computeIfAbsent(row.ion(), ignored -> ions.size());
            preflight(ions.size(), samples.size(), working, true);
            double[][] matrix = new double[ions.size()][samples.size()]; int[][] count = new int[ions.size()][samples.size()];
            for (double[] row : matrix) Arrays.fill(row, Double.NaN);
            long repeated = 0;
            for (Observation row : group.getValue()) {
                budget.add(1); int i = ions.get(row.ion()), j = samples.get(row.sample());
                if (count[i][j] == 0) matrix[i][j] = row.log2Intensity();
                else if (count[i][j] == 1) { matrix[i][j] = StrictMath.pow(2, matrix[i][j]) + StrictMath.pow(2, row.log2Intensity()); repeated++; }
                else { matrix[i][j] += StrictMath.pow(2, row.log2Intensity()); repeated++; }
                count[i][j] = Math.addExact(count[i][j], 1);
            }
            for (int i = 0; i < ions.size(); i++) for (int j = 0; j < samples.size(); j++) if (count[i][j] > 1) {
                matrix[i][j] = StrictMath.log(matrix[i][j]) / LOG2;
                if (!Double.isFinite(matrix[i][j])) throw new ArithmeticException("Source duplicate-cell linear sum overflow/underflow; preserve inputs, no complete MaxLFQ result");
            }
            Result result = matrix(matrix, working, budget);
            proteins.add(new Protein(group.getKey(), List.copyOf(ions.keySet()), result, group.getValue().size(), repeated));
            duplicates = Math.addExact(duplicates, repeated);
        }
        return new Table(List.copyOf(samples.keySet()), proteins, observations.size(), duplicates, budget.used);
    }

    /** The original maxlfq_bit core: ion rows, sample columns, NaN cells explicitly missing. */
    public static Result matrix(double[][] input, Limits limits, Runnable cancellation) {
        return matrix(input, limits, new Budget(limits, cancellation));
    }
    private static void preflight(int m, int n, Limits limits, boolean duplicates) {
        long cells = Math.multiplyExact((long)m, n), pairs = Math.multiplyExact((long)n, n - 1L) / 2;
        long bytes = Math.addExact(Math.multiplyExact(cells, duplicates ? 20L : 16L),
            Math.addExact(Math.multiplyExact((long)n, 128L + 8L * ((n + 63L) / 64)), 8L * m + 64L * m));
        if (m < 1 || n < 1 || m > limits.ions() || n > limits.samples() || cells > limits.cells()
                || pairs > limits.pairComparisons() || bytes > limits.numericBytes())
            throw new IllegalArgumentException("Complete MaxLFQ matrix/pair/numeric-memory capacity exceeded; no truncation");
    }
    private static Result matrix(double[][] input, Limits limits, Budget budget) {
        Objects.requireNonNull(input); if (input.length == 0 || input[0] == null) throw new IllegalArgumentException("Nonempty rectangular MaxLFQ matrix required");
        int m = input.length, n = input[0].length; preflight(m, n, limits, false);
        double[][] x = new double[m][]; long observed = 0;
        for (int i = 0; i < m; i++) {
            if (input[i] == null || input[i].length != n) throw new IllegalArgumentException("Ragged MaxLFQ matrix");
            x[i] = input[i].clone(); budget.add(n);
            for (double value : x[i]) if (!Double.isNaN(value)) { if (!Double.isFinite(value)) throw new IllegalArgumentException("Infinite MaxLFQ cell; no silent repair"); observed++; }
        }
        if (m == 1) return new Result(x[0], new int[n], observed == 0 ? "NA" : "", 0, 0, observed, 0, budget.used);
        var graph = new BitSet[n]; for (int i = 0; i < n; i++) graph[i] = new BitSet(n);
        var degree = new int[n]; var b = new double[n + 1]; double[] medians = new double[m]; long ratios = 0;
        for (int i = 0; i < n - 1; i++) for (int j = i + 1; j < n; j++) {
            budget.add(m); int count = 0;
            for (int r = 0; r < m; r++) if (!Double.isNaN(x[r][i]) && !Double.isNaN(x[r][j])) {
                double difference = x[r][j] - x[r][i]; finite(difference); medians[count++] = difference;
            }
            if (count == 0) continue;
            double median = median(medians, count); graph[i].set(j); graph[j].set(i); degree[i]++; degree[j]++;
            b[i] -= median; b[j] += median; finite(b[i]); finite(b[j]); ratios++;
        }
        for (int j = 0; j < n; j++) { b[j] *= 2; finite(b[j]); }
        double[] estimate = new double[n + 1], residual = b.clone(), direction = b.clone(), product = new double[n + 1];
        double norm = dot(b, b), square = norm, threshold = SOURCE_EPSILON * norm; finite(norm); int iterations = 0;
        int sourceIterations = Math.toIntExact(Math.min(Integer.MAX_VALUE, 10L * (n + 1L))), bound = Math.min(limits.iterations(), sourceIterations);
        while (square > threshold && iterations < bound) {
            multiply(graph, degree, direction, product, budget); double pAp = dot(direction, product);
            if (!Double.isFinite(pAp) || pAp == 0) throw new ArithmeticException("MaxLFQ solver breakdown before convergence; no best-iterate result admitted");
            double alpha = square / pAp; finite(alpha); budget.add(10L * (n + 1));
            for (int i = 0; i <= n; i++) { estimate[i] += alpha * direction[i]; residual[i] -= alpha * product[i]; finite(estimate[i]); finite(residual[i]); }
            double next = dot(residual, residual); finite(next); iterations++;
            if (next <= threshold) { square = next; break; }
            double beta = next / square; finite(beta);
            for (int i = 0; i <= n; i++) direction[i] = residual[i] + beta * direction[i];
            square = next;
        }
        if (square > threshold) throw new ArithmeticException("MaxLFQ did not converge within the declared/source iteration limit; no result admitted");
        multiply(graph, degree, estimate, product, budget); double actual = 0;
        for (int i = 0; i <= n; i++) { double delta = product[i] - b[i]; actual += delta * delta; }
        finite(actual); double relative = norm == 0 ? Math.sqrt(actual) : Math.sqrt(actual / norm);
        if (relative > 1e-8) throw new ArithmeticException("MaxLFQ independently recomputed residual failed; no result admitted");
        int[] components = components(graph, budget); int count = Arrays.stream(components).max().orElseThrow() + 1;
        for (int c = 0; c < count; c++) {
            double sum = 0, sumEstimate = 0; int values = 0, size = 0, single = -1;
            for (int j = 0; j < n; j++) if (components[j] == c) {
                size++; single = j; sumEstimate += estimate[j];
                for (int i = 0; i < m; i++) if (!Double.isNaN(x[i][j])) { sum += x[i][j]; values++; }
            }
            finite(sum); finite(sumEstimate); budget.add((long)n * (m + 1));
            if (values == 0) { for (int j = 0; j < n; j++) if (components[j] == c) estimate[j] = Double.NaN; }
            else if (size > 1) { double mean = sum / values, meanEstimate = sumEstimate / size; for (int j = 0; j < n; j++) if (components[j] == c) { estimate[j] = estimate[j] - meanEstimate + mean; finite(estimate[j]); } }
            else { int k = 0; for (int i = 0; i < m; i++) if (!Double.isNaN(x[i][single])) medians[k++] = x[i][single]; estimate[single] = median(medians, k); }
        }
        double[] output = Arrays.copyOf(estimate, n); int first = -1; boolean multiple = false;
        for (int i = 0; i < n; i++) if (!Double.isNaN(output[i])) { if (first == -1) first = components[i]; else if (components[i] != first) multiple = true; }
        String annotation = first == -1 ? "NA" : "";
        if (multiple) { var text = new StringJoiner(";"); for (int i = 0; i < n; i++) text.add(Double.isNaN(output[i]) ? "NA" : Integer.toString(components[i] + 1)); annotation = text.toString(); }
        return new Result(output, components, annotation, iterations, relative, observed, ratios, budget.used);
    }
    private static int[] components(BitSet[] graph, Budget budget) {
        int[] labels = new int[graph.length], queue = new int[graph.length]; Arrays.fill(labels, -1); int group = 0;
        for (int i = 0; i < graph.length; i++) if (labels[i] == -1) {
            int head = 0, end = 0; queue[end++] = i; labels[i] = group;
            while (head < end) { int v = queue[head++]; budget.add(graph[v].cardinality() + 1L); for (int j = graph[v].nextSetBit(0); j >= 0; j = graph[v].nextSetBit(j + 1)) if (labels[j] == -1) { labels[j] = group; queue[end++] = j; } }
            group++;
        }
        return labels;
    }
    private static void multiply(BitSet[] graph, int[] degree, double[] x, double[] out, Budget budget) {
        double sum = 0; for (int i = 0; i < graph.length; i++) {
            budget.add(degree[i] + 1L); double value = x[i] * degree[i];
            for (int j = graph[i].nextSetBit(0); j >= 0; j = graph[i].nextSetBit(j + 1)) value -= x[j];
            out[i] = 2 * value + x[graph.length]; finite(out[i]); sum += x[i];
        }
        out[graph.length] = sum; finite(sum);
    }
    private static double dot(double[] a, double[] b) { double sum = 0; for (int i = 0; i < a.length; i++) sum += a[i] * b[i]; return sum; }
    private static double median(double[] values, int size) { Arrays.sort(values, 0, size); double v = (size & 1) == 1 ? values[size / 2] : (values[size / 2 - 1] + values[size / 2]) / 2; finite(v); return v; }
    private static void finite(double value) { if (!Double.isFinite(value)) throw new ArithmeticException("Nonfinite source MaxLFQ arithmetic; no complete result admitted"); }
}
