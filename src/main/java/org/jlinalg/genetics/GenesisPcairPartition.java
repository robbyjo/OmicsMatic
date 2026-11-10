/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Native finite-matrix profile of GENESIS 2.42.0 {@code pcairPartition}.
 * Source behavior is attributed to the GENESIS authors, GPL-3.0. The profile
 * follows its matrix host on Windows R 4.6.1, including matrix-dimension
 * failures, strict cutoffs, fixed initial divergence/kinship priorities and
 * the original forced-set degree bookkeeping. It does not run PC-AiR PCA,
 * PC-Relate, an association model or an independent-set optimization.
 *
 * <p>Every original matrix must be nonempty, finite, symmetric and have unique, caller-aligned
 * row/column IDs. Unsupported values/identities are refused, never repaired.
 * Literal NA/NaN strings and input order are retained. The caller supplies
 * both cutoffs explicitly; no paper settings are inferred.</p>
 */
public final class GenesisPcairPartition {
    private GenesisPcairPartition() { }
    /** Hard size ceiling of this development profile. */
    public static final int MAX_SAMPLES = 5000;

    /** Immutable, caller-labelled symmetric matrix; construction validates and copies it. */
    public record Matrix(List<String> ids, double[][] values) {
        public Matrix {
            ids = identities(ids, "matrix IDs");
            Objects.requireNonNull(values, "matrix values");
            int n = ids.size();
            if (n == 0 || n > MAX_SAMPLES || values.length != n || new HashSet<>(ids).size() != n)
                throw new IllegalArgumentException("Finite original matrix profile requires nonempty unique IDs, matching dimensions and at most5000 samples; explicit selection may subsequently be empty");
            for (double[] row : values)
                if (row == null || row.length != n)
                    throw new IllegalArgumentException("Complete square matrix required");
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                if (!Double.isFinite(values[i][j]))
                    throw new IllegalArgumentException("Nonfinite GENESIS matrix value: preserve the source finding; do not fill or admit dependent native partition analysis");
                if (values[i][j] != values[j][i])
                    throw new IllegalArgumentException("Symmetric finite-matrix profile required; no symmetrization is performed");
            }
            values = copy(values);
        }
        @Override public double[][] values() { return copy(values); }
    }

    /** Explicit source cutoffs, optional forced unrelated IDs and optional selection. */
    public record Options(double kinshipThreshold, double divergenceThreshold,
                          List<String> forcedUnrelated, List<String> sampleInclude) {
        public Options {
            if (!Double.isFinite(kinshipThreshold) || !Double.isFinite(divergenceThreshold))
                throw new IllegalArgumentException("Explicit finite kinship/divergence cutoffs required");
            if (forcedUnrelated != null) forcedUnrelated = identities(forcedUnrelated, "forced IDs");
            if (sampleInclude != null) sampleInclude = identities(sampleInclude, "included IDs");
        }
    }

    /** Logical partition budgets, applied after immutable matrix construction. */
    public record Limits(int samples, long pairOperations, long workingBytes) {
        public Limits {
            if (samples < 1 || samples > MAX_SAMPLES || pairOperations < 1 || workingBytes < 1)
                throw new IllegalArgumentException("Positive explicit limits required; sample ceiling is5000");
        }
    }

    /** Ordered source sets, source warnings and explicit source-profile restrictions. */
    public record Result(List<String> related, List<String> unrelated, List<String> warnings,
                         int selectedSamples, int iterations, long unrelatedPairsAboveThreshold,
                         long estimatedPairOperations, long estimatedWorkingBytes) {
        public Result {
            related = List.copyOf(related); unrelated = List.copyOf(unrelated); warnings = List.copyOf(warnings);
        }
        /** False when source forcing or matrix simplification leaves a related pair in the unrelated set. */
        public boolean unrelatedSetPassesDeclaredKinshipCutoff() { return unrelatedPairsAboveThreshold == 0; }
    }

    /** Preserved original matrix-host refusal; preceding source warnings remain available. */
    public static final class SourceFailure extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String sourceCode;
        private final ArrayList<String> warnings;
        SourceFailure(String sourceCode, String message, List<String> warnings) {
            super(message); this.sourceCode = sourceCode; this.warnings = new ArrayList<>(warnings);
        }
        public String sourceCode() { return sourceCode; }
        public List<String> warnings() { return List.copyOf(warnings); }
    }

    /** Partition complete prepared matrices without fitting PCA, changing IDs or editing values. */
    public static Result partition(Matrix kinship, Matrix divergence, Options options, Limits limits) {
        Objects.requireNonNull(kinship); Objects.requireNonNull(options); Objects.requireNonNull(limits);
        int nk = kinship.ids.size(), nd = divergence == null ? 0 : divergence.ids.size();
        long cells = (long)nk * nk + (long)nd * nd;
        long operations = 8 * cells + 4L * nk * nk;
        long working = 16 * cells + ((long)nk * nk + 7) / 8 + 2048L * (nk + nd + 1);
        if (nk > limits.samples || nd > limits.samples || operations > limits.pairOperations || working > limits.workingBytes)
            throw new IllegalArgumentException("Native GENESIS partition capacity exceeded before partitioning; increase a justified explicit budget or keep this analysis unadmitted");
        List<String> warnings = new ArrayList<>();
        Set<String> kinIds = new HashSet<>(kinship.ids), divIds = divergence == null ? null : new HashSet<>(divergence.ids);
        Set<String> include = options.sampleInclude == null ? null : new HashSet<>(options.sampleInclude);
        if (include != null && !kinIds.containsAll(include)) warnings.add("some samples in sample.include are not in kinobj; they will not be included");
        if (divergence != null) {
            if (include != null && !divIds.containsAll(include)) warnings.add("some samples in sample.include are not in divobj; they will not be included");
            if (!kinIds.equals(divIds)) warnings.add("kinobj and divobj contain non-matching samples; only partitioning those present in both objects");
        }
        Set<String> common = new HashSet<>(kinIds); if (divergence != null) common.retainAll(divIds);
        List<String> forced = options.forcedUnrelated;
        if (forced != null) {
            if (!common.containsAll(forced)) warnings.add("some samples in unrel.set are not in kinobj or divobj; they will not be included");
            List<String> filtered = new ArrayList<>();
            for (String id : forced) if (common.contains(id) && (include == null || include.contains(id))) filtered.add(id);
            forced = filtered.isEmpty() ? null : filtered;
        }
        List<String> selected = new ArrayList<>(); List<Integer> inputRows = new ArrayList<>();
        for (int i = 0; i < nk; i++) {
            String id = kinship.ids.get(i);
            if (common.contains(id) && (include == null || include.contains(id))) { selected.add(id); inputRows.add(i); }
        }
        int n = selected.size();
        if (n == 1) throw new SourceFailure("MATRIX_DIMENSION_DROP", "Original GENESIS2.42.0 matrix host: dim(X) must have a positive length; retain this refusal rather than adding a drop=FALSE repair", warnings);
        if (n == 0) return result(kinship, inputRows, selected, List.of(), List.of(), warnings, 0, options, operations, working);
        BitSet[] relatives = new BitSet[n]; int[] degree = new int[n];
        int minimum = Integer.MAX_VALUE, maximum = 0;
        for (int j = 0; j < n; j++) {
            relatives[j] = new BitSet(n);
            for (int i = 0; i < n; i++) if (kinship.values[inputRows.get(i)][inputRows.get(j)] > options.kinshipThreshold) relatives[j].set(i);
            int size = relatives[j].cardinality(); minimum = Math.min(minimum, size); maximum = Math.max(maximum, size);
        }
        if (minimum == maximum && maximum == n)
            throw new SourceFailure("ALL_SAMPLES_RELATED", "Original GENESIS2.42.0 refuses an all-related matrix population at the declared kinship threshold", warnings);
        // Base apply simplifies length-one results to a vector; the source then
        // returns before forced-set processing. This is intentional source parity.
        if (minimum == maximum && maximum == 1)
            return result(kinship, inputRows, selected, List.of(), selected, warnings, 0, options, operations, working);
        List<String> unrelated = new ArrayList<>(), related = new ArrayList<>(); boolean[] active = new boolean[n];
        for (int j = 0; j < n; j++) {
            relatives[j].clear(j); degree[j] = relatives[j].cardinality(); active[j] = degree[j] > 0;
            if (!active[j]) unrelated.add(selected.get(j));
        }
        double[] kinshipSum = new double[n]; int[] divergent = new int[n];
        Map<String, Integer> divIndex = divergence == null ? Map.of() : index(divergence.ids);
        for (int j = 0; j < n; j++) if (active[j]) {
            Sum64 sum = new Sum64();
            for (int i = 0; i < n; i++) if (active[i]) {
                double value = kinship.values[inputRows.get(i)][inputRows.get(j)];
                if (value > options.kinshipThreshold) sum.add(value);
            }
            kinshipSum[j] = sum.value();
            if (divergence != null) for (int i = 0; i < n; i++)
                if (divergence.values[divIndex.get(selected.get(i))][divIndex.get(selected.get(j))] < options.divergenceThreshold && !relatives[j].get(i)) divergent[j]++;
        }
        if (forced != null) {
            Set<String> forcedIds = new HashSet<>(forced); LinkedHashSet<String> combined = new LinkedHashSet<>(unrelated); combined.addAll(forced); unrelated = new ArrayList<>(combined);
            BitSet forcedRows = new BitSet(n); for (int i = 0; i < n; i++) if (forcedIds.contains(selected.get(i))) forcedRows.set(i);
            for (int j = 0; j < n; j++) if (active[j] && !forcedRows.get(j) && relatives[j].intersects(forcedRows)) related.add(selected.get(j));
            Set<String> removed = new HashSet<>(related); removed.addAll(forcedIds);
            for (int j = 0; j < n; j++) if (removed.contains(selected.get(j))) active[j] = false;
            // The original does not remove these IDs from stored relative lists
            // or decrement their neighbours. Do not silently repair that choice.
        }
        int iterations = 0;
        while (true) {
            int chosen = -1;
            for (int j = 0; j < n; j++) if (active[j] && (chosen < 0 || degree[j] > degree[chosen]
                    || (degree[j] == degree[chosen] && (divergent[j] < divergent[chosen]
                    || (divergent[j] == divergent[chosen] && kinshipSum[j] < kinshipSum[chosen]))))) chosen = j;
            if (chosen < 0) break;
            iterations++; related.add(selected.get(chosen)); active[chosen] = false;
            for (int j = 0; j < n; j++) if (active[j]) {
                if (relatives[j].get(chosen)) degree[j]--;
                if (degree[j] == 0) { unrelated.add(selected.get(j)); active[j] = false; }
            }
        }
        return result(kinship, inputRows, selected, related, unrelated, warnings, iterations, options, operations, working);
    }

    private static Result result(Matrix kinship, List<Integer> rows, List<String> selected, List<String> related,
                                 List<String> unrelated, List<String> warnings, int iterations, Options options, long operations, long working) {
        if (related.size() + unrelated.size() != selected.size()) throw new IllegalStateException("Partition population differs; admit no native result");
        Set<String> seen = new HashSet<>(related);
        for (String id : unrelated) if (!seen.add(id)) throw new IllegalStateException("Partition duplicates an identity; admit no native result");
        if (!seen.equals(new HashSet<>(selected))) throw new IllegalStateException("Partition IDs differ; admit no native result");
        Map<String, Integer> positions = index(selected); long violations = 0;
        for (int i = 0; i < unrelated.size(); i++) for (int j = i + 1; j < unrelated.size(); j++)
            if (kinship.values[rows.get(positions.get(unrelated.get(i)))][rows.get(positions.get(unrelated.get(j)))] > options.kinshipThreshold) violations++;
        return new Result(related, unrelated, warnings, selected.size(), iterations, violations, operations, working);
    }

    private static List<String> identities(List<String> input, String field) {
        Objects.requireNonNull(input, field);
        for (String id : input) {
            if (id == null || id.isEmpty() || id.indexOf('\0') >= 0)
                throw new IllegalArgumentException(field + " must contain explicit nonempty R-compatible Unicode IDs without NUL; no renaming is performed");
            for (int i = 0; i < id.length(); i++) {
                char character = id.charAt(i);
                if (Character.isHighSurrogate(character)) {
                    if (i + 1 >= id.length() || !Character.isLowSurrogate(id.charAt(++i)))
                        throw new IllegalArgumentException(field + " contains invalid Unicode; no replacement characters are inserted");
                } else if (Character.isLowSurrogate(character))
                    throw new IllegalArgumentException(field + " contains invalid Unicode; no replacement characters are inserted");
            }
        }
        return List.copyOf(input);
    }
    private static double[][] copy(double[][] matrix) { double[][] result = new double[matrix.length][]; for (int i = 0; i < matrix.length; i++) result[i] = matrix[i].clone(); return result; }
    private static Map<String, Integer> index(List<String> ids) { Map<String, Integer> result = new HashMap<>(); for (int i = 0; i < ids.size(); i++) result.put(ids.get(i), i); return result; }

    /**
     * R4.6.1 Windows long-double64-significand sum, with its explicit DBL_MAX
     * clamp. Source profile: R Core's src/main/summary.c rsum and Defn.h
     * LDOUBLE, R-4-6-1 tag; the observed runtime has longdouble.digits=64.
     * Arithmetic is rounded after each binary64 operand enters the64-bit
     * significand accumulator, then clamped/converted exactly as in that profile.
     */
    static final class Sum64 {
        private BigInteger significand = BigInteger.ZERO;
        private int exponent;
        void add(double value) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite scalar sum profile required");
            if (value == 0) return;
            long bits = Double.doubleToRawLongBits(value), fraction = bits & 0x000fffffffffffffL;
            int binaryExponent = (int)((bits >>> 52) & 0x7ff);
            BigInteger other = BigInteger.valueOf(binaryExponent == 0 ? fraction : fraction | (1L << 52));
            if (bits < 0) other = other.negate();
            int otherExponent = binaryExponent == 0 ? -1074 : binaryExponent - 1075;
            if (significand.signum() == 0) { significand = other; exponent = otherExponent; return; }
            int shared = Math.min(exponent, otherExponent);
            BigInteger sum = significand.shiftLeft(exponent - shared).add(other.shiftLeft(otherExponent - shared));
            int shift = Math.max(0, sum.abs().bitLength() - 64); BigInteger absolute = sum.abs(), rounded = absolute.shiftRight(shift);
            if (shift > 0 && absolute.testBit(shift - 1) && (absolute.getLowestSetBit() < shift - 1 || rounded.testBit(0))) rounded = rounded.add(BigInteger.ONE);
            exponent = shared + shift;
            if (rounded.bitLength() > 64) { rounded = rounded.shiftRight(1); exponent++; }
            significand = sum.signum() < 0 ? rounded.negate() : rounded;
        }
        double value() {
            if (significand.signum() == 0) return 0.0;
            BigInteger maximum = BigInteger.ONE.shiftLeft(53).subtract(BigInteger.ONE);
            int shared = Math.min(exponent, 971);
            if (significand.abs().shiftLeft(exponent - shared).compareTo(maximum.shiftLeft(971 - shared)) > 0)
                return significand.signum() < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            return Math.scalb(significand.doubleValue(), exponent);
        }
    }
}
