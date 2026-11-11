/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Native sparse-count RunTFIDF arithmetic and FindTopFeatures metadata/selection.
 * The supported input is nonnegative integer counts with each row and column
 * total at most 2^53-1. Coordinates must already be sorted and unique; stored
 * zeros are retained. This class neither constructs a ChromatinAssay nor runs
 * LSI, QC, motif analysis, differential accessibility, or peak-gene linkage.
 * It does not certify a Signac workflow or a historical package installation.
 */
public final class SignacNormalization {
    private SignacNormalization() { }
    private static final long MAX_EXACT = 9_007_199_254_740_991L;

    /** Explicit admission bounds, checked before private copies are allocated. */
    public record Limits(int features, int cells, int storedValues) {
        public Limits {
            if (features < 1 || cells < 1 || storedValues < 0
                    || features > Integer.MAX_VALUE - 8 || cells > Integer.MAX_VALUE - 8
                    || storedValues > Integer.MAX_VALUE - 8) {
                throw new IllegalArgumentException("Positive dimensions and nonnegative stored-value bounds required");
            }
        }
        public static Limits defaults() { return new Limits(5_000_000, 10_000_000, 100_000_000); }
    }

    /** Immutable compressed sparse columns, features by cells; zero-based indices. */
    public static final class Counts {
        private final List<String> features;
        private final List<String> cells;
        private final int[] pointers;
        private final int[] rows;
        private final double[] values;
        private final long[] featureTotals;
        private final long[] cellTotals;

        public Counts(List<String> features, List<String> cells, int[] pointers, int[] rows, double[] values) {
            this(features, cells, pointers, rows, values, Limits.defaults(), () -> false);
        }

        public Counts(List<String> features, List<String> cells, int[] pointers, int[] rows,
                      double[] values, Limits limits, BooleanSupplier cancelled) {
            Objects.requireNonNull(features); Objects.requireNonNull(cells); Objects.requireNonNull(pointers);
            Objects.requireNonNull(rows); Objects.requireNonNull(values); Objects.requireNonNull(limits);
            Poller poll = new Poller(cancelled); poll.check();
            if (features.isEmpty() || cells.isEmpty() || features.size() > limits.features()
                    || cells.size() > limits.cells() || values.length > limits.storedValues()
                    || pointers.length != cells.size() + 1 || rows.length != values.length) {
                throw new IllegalArgumentException("Sparse dimensions/entry count exceed the declared bounds or disagree");
            }
            this.features = identities(features, "Feature", poll);
            this.cells = identities(cells, "Cell", poll);
            this.pointers = pointers.clone(); this.rows = rows.clone(); this.values = values.clone();
            this.featureTotals = new long[features.size()]; this.cellTotals = new long[cells.size()];
            if (this.pointers[0] != 0 || this.pointers[cells.size()] != values.length) {
                throw new IllegalArgumentException("CSC pointers must start at zero and end at the stored-value count");
            }
            for (int cell = 0; cell < cells.size(); cell++) {
                poll.tick();
                int start = this.pointers[cell], end = this.pointers[cell + 1];
                if (start < 0 || end < start || end > this.values.length) {
                    throw new IllegalArgumentException("CSC pointers must be nondecreasing and within the stored arrays");
                }
                int previous = -1;
                for (int k = start; k < end; k++) {
                    poll.tick(); int row = this.rows[k]; double count = this.values[k];
                    if (row <= previous || row >= features.size()) {
                        throw new IllegalArgumentException("Each CSC column requires sorted unique feature indices in range");
                    }
                    if (!Double.isFinite(count) || count < 0 || count > MAX_EXACT || count != Math.rint(count)) {
                        throw new IllegalArgumentException("Require finite nonnegative integer counts at most 2^53-1");
                    }
                    long value = (long) count;
                    if (featureTotals[row] > MAX_EXACT - value || cellTotals[cell] > MAX_EXACT - value) {
                        throw new IllegalArgumentException("Feature and cell totals must each be at most 2^53-1");
                    }
                    featureTotals[row] += value; cellTotals[cell] += value; previous = row;
                }
            }
            poll.check();
        }
        public List<String> features() { return features; }
        public List<String> cells() { return cells; }
        public int[] columnPointers() { return pointers.clone(); }
        public int[] rowIndices() { return rows.clone(); }
        public double[] values() { return values.clone(); }
        public long[] featureTotals() { return featureTotals.clone(); }
        public long[] cellTotals() { return cellTotals.clone(); }
    }

    /** Immutable output with the same sparse coordinates, including explicitly stored zeros. */
    public static final class NormalizedMatrix {
        private final Counts counts;
        private final double[] values;
        private NormalizedMatrix(Counts counts, double[] values) { this.counts = counts; this.values = values; }
        public List<String> features() { return counts.features; }
        public List<String> cells() { return counts.cells; }
        public int[] columnPointers() { return counts.columnPointers(); }
        public int[] rowIndices() { return counts.rowIndices(); }
        public double[] values() { return values.clone(); }
    }

    public enum Method {
        LOG_TF_IDF(1), TF_LOG_IDF(2), LOG_TF_LOG_IDF(3), RAW_COUNT_IDF(4);
        private final int number;
        Method(int number) { this.number = number; }
        public int number() { return number; }
    }

    /** A numerical warning and the action needed before interpreting downstream results. */
    public record Warning(String code, String message, String requiredAction) { }
    public record Normalization(NormalizedMatrix matrix, Method method, double scaleFactor,
                                boolean suppliedIdf, double[] effectiveIdf, List<Warning> warnings) {
        public Normalization { effectiveIdf = effectiveIdf.clone(); warnings = List.copyOf(warnings); }
        @Override public double[] effectiveIdf() { return effectiveIdf.clone(); }
    }

    /**
     * Runs the four source formulas. Computed IDF is cells / feature total
     * counts, not cells / nonzero cells. Supplied IDF is already on the desired
     * scale and is never logged again. Scale affects methods 1 and 3 only.
     * NaN stored results become zero as in the source; infinity is retained and
     * explicitly warned about, never clipped. Fractional counts, nonpositive or
     * nonfinite IDF, and nonpositive or nonfinite scale are outside this API's domain.
     */
    public static Normalization runTfidf(Counts counts, Method method, double scaleFactor,
                                         double[] suppliedIdf, BooleanSupplier cancelled) {
        Objects.requireNonNull(counts); Objects.requireNonNull(method);
        Poller poll = new Poller(cancelled); poll.check();
        if (!Double.isFinite(scaleFactor) || scaleFactor <= 0) {
            throw new IllegalArgumentException("TF-IDF scale must be finite and positive");
        }
        if (suppliedIdf != null && suppliedIdf.length != counts.features.size()) {
            throw new IllegalArgumentException("Supplied IDF length differs from the feature count");
        }
        List<Warning> warnings = new ArrayList<>();
        boolean zeroCell = false, zeroFeature = false;
        for (long total : counts.cellTotals) { poll.tick(); zeroCell |= total == 0; }
        double[] idf = new double[counts.features.size()];
        for (int row = 0; row < idf.length; row++) {
            poll.tick();
            if (suppliedIdf != null) {
                double value = suppliedIdf[row];
                if (!Double.isFinite(value) || value <= 0) {
                    throw new IllegalArgumentException("Supplied IDF values must be finite and positive");
                }
                idf[row] = value;
            } else {
                zeroFeature |= counts.featureTotals[row] == 0;
                double value = (double) counts.cells.size() / counts.featureTotals[row];
                idf[row] = method == Method.TF_LOG_IDF || method == Method.LOG_TF_LOG_IDF
                        ? Math.log(1 + value) : value;
            }
        }
        if (zeroCell) warnings.add(new Warning("ZERO_CELL_COUNTS", "Some cells contain 0 total counts",
                "Review empty-cell QC; a zero normalized column does not make the cell suitable for LSI or downstream analysis."));
        if (zeroFeature) warnings.add(new Warning("ZERO_FEATURE_COUNTS", "Some features contain 0 total counts",
                "Review empty-feature filtering; computed IDF is infinite for these features and source NaN-to-zero handling is retained."));
        double[] result = new double[counts.values.length]; boolean infinite = false;
        for (int cell = 0; cell < counts.cells.size(); cell++) {
            poll.tick(); double reciprocal = 1.0 / counts.cellTotals[cell];
            for (int k = counts.pointers[cell]; k < counts.pointers[cell + 1]; k++) {
                poll.tick();
                double tf = method == Method.RAW_COUNT_IDF ? counts.values[k] : counts.values[k] * reciprocal;
                if (method == Method.LOG_TF_LOG_IDF) tf = Math.log1p(tf * scaleFactor);
                double value = idf[counts.rows[k]] * tf;
                if (method == Method.LOG_TF_IDF) value = Math.log1p(value * scaleFactor);
                result[k] = Double.isNaN(value) ? 0 : value;
                infinite |= Double.isInfinite(result[k]);
            }
        }
        if (infinite) warnings.add(new Warning("NONFINITE_NORMALIZED_VALUES", "Infinite normalized stored values were retained",
                "Skip downstream analysis requiring finite values. Inspect scale and supplied IDF and rerun with justified finite parameters; do not silently clip values."));
        poll.check();
        return new Normalization(new NormalizedMatrix(counts, result), method, scaleFactor, suppliedIdf != null, idf, warnings);
    }

    public static Normalization runTfidf(Counts counts, Method method, double scaleFactor, double[] suppliedIdf) {
        return runTfidf(counts, method, scaleFactor, suppliedIdf, () -> false);
    }

    public record Feature(String id, int originalIndex, long count, double percentile) { }
    public enum CutoffKind { ALL, COUNT, PERCENTILE, KEEP_EXISTING }
    /** Percentiles use the 0..100 scale, e.g. percentile(5) corresponds to Signac q5. */
    public record Cutoff(CutoffKind kind, double value) {
        public Cutoff {
            Objects.requireNonNull(kind);
            if (!Double.isFinite(value) || value < 0 || (kind == CutoffKind.PERCENTILE && value > 100)
                    || ((kind == CutoffKind.ALL || kind == CutoffKind.KEEP_EXISTING) && value != 0)) {
                throw new IllegalArgumentException("Require a nonnegative finite count or a percentile in 0..100");
            }
        }
        public static Cutoff all() { return new Cutoff(CutoffKind.ALL, 0); }
        public static Cutoff count(double count) { return new Cutoff(CutoffKind.COUNT, count); }
        public static Cutoff percentile(double percentile) { return new Cutoff(CutoffKind.PERCENTILE, percentile); }
        public static Cutoff keepExisting() { return new Cutoff(CutoffKind.KEEP_EXISTING, 0); }
    }
    public record TopFeatures(List<Feature> metadata, List<String> selected) {
        public TopFeatures { metadata = List.copyOf(metadata); selected = List.copyOf(selected); }
    }

    /**
     * Matrix-method metadata includes every feature, descending count with stable
     * ties. Percentile is ECDF(count), including ties. No cutoff is applied here:
     * the upstream default matrix method similarly ignores min.cutoff.
     */
    public static List<Feature> featureMetadata(Counts counts, BooleanSupplier cancelled) {
        Objects.requireNonNull(counts); Poller poll = new Poller(cancelled); poll.check();
        Integer[] order = new Integer[counts.features.size()];
        for (int row = 0; row < order.length; row++) { poll.tick(); order[row] = row; }
        Arrays.sort(order, (left, right) -> { poll.tick(); return Long.compare(counts.featureTotals[right], counts.featureTotals[left]); });
        List<Feature> result = new ArrayList<>(order.length);
        for (int first = 0; first < order.length;) {
            poll.tick(); int end = first + 1;
            while (end < order.length && counts.featureTotals[order[end]] == counts.featureTotals[order[first]]) { poll.tick(); end++; }
            double percentile = (double) (order.length - first) / order.length;
            for (int i = first; i < end; i++) { poll.tick(); int row = order[i]; result.add(new Feature(counts.features.get(row), row, counts.featureTotals[row], percentile)); }
            first = end;
        }
        poll.check(); return List.copyOf(result);
    }

    /** Assay-wrapper selection semantics, without creating or modifying an R/Seurat assay. */
    public static TopFeatures findTopFeatures(Counts counts, Cutoff cutoff, List<String> existing, BooleanSupplier cancelled) {
        Objects.requireNonNull(cutoff); Poller poll = new Poller(cancelled); poll.check();
        List<Feature> metadata = featureMetadata(counts, cancelled); List<String> selected = new ArrayList<>();
        if (cutoff.kind() == CutoffKind.KEEP_EXISTING) {
            Objects.requireNonNull(existing, "Existing variable features required for KEEP_EXISTING");
            HashSet<String> allowed = new HashSet<>(counts.features), seen = new HashSet<>();
            for (String id : existing) {
                poll.tick();
                if (!allowed.contains(id) || !seen.add(id)) throw new IllegalArgumentException("Existing variable features must be unique known feature IDs");
                selected.add(id);
            }
        } else {
            for (Feature feature : metadata) {
                poll.tick();
                if (cutoff.kind() == CutoffKind.ALL
                        || (cutoff.kind() == CutoffKind.COUNT && feature.count() > cutoff.value())
                        || (cutoff.kind() == CutoffKind.PERCENTILE && feature.percentile() > cutoff.value() / 100)) {
                    selected.add(feature.id());
                }
            }
        }
        poll.check(); return new TopFeatures(metadata, selected);
    }

    public static TopFeatures findTopFeatures(Counts counts, Cutoff cutoff, List<String> existing) {
        return findTopFeatures(counts, cutoff, existing, () -> false);
    }

    private static List<String> identities(List<String> input, String kind, Poller poll) {
        HashSet<String> seen = new HashSet<>();
        for (String id : input) {
            poll.tick();
            if (id == null || id.isBlank() || id.length() > 4096 || id.indexOf('\t') >= 0
                    || id.indexOf('\n') >= 0 || id.indexOf('\r') >= 0 || id.indexOf('\0') >= 0 || !seen.add(id)) {
                throw new IllegalArgumentException(kind + " identities must be nonblank, unique, at most 4096 characters and contain no tab, newline, carriage return or NUL");
            }
        }
        return List.copyOf(input);
    }

    private static final class Poller {
        private final BooleanSupplier cancelled;
        private int operations;
        private Poller(BooleanSupplier cancelled) { this.cancelled = Objects.requireNonNull(cancelled); }
        private void tick() { if ((++operations & 4095) == 0) check(); }
        private void check() {
            if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
                throw new CancellationException("Signac native normalization cancelled; no external process or output was created");
            }
        }
    }
}
