/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Native prepared-autosomal-hard-call counterparts of GCTA 1.94.1 GRM algorithms
 * 0 and 1. Source: GCTA commit 6da64e9d8be838d01c34efb974e69c72e861f4c2,
 * Geno::getGenoDouble_bed and GRM::calculate_GRM_blas/deduce_GRM (GPLv3,
 * Jian Yang, Zhili Zheng and contributors).
 *
 * <p>Inputs are caller-prepared diploid counts of the declared allele, with NaN
 * denoting missing calls. There is no genotype-file intake, chromosome/sex
 * weighting, dominance coding, allele update, external-frequency override,
 * sample filtering or additional MAF/missingness filtering. Monomorphic and
 * entirely missing markers have explicit source dispositions. Every sample pair
 * must have at least one jointly observed retained marker; the original zero
 * fallback for empty pairs is refused as no complete estimate.</p>
 *
 * <p>Missing entries contribute zero to the centered numerator, with a separate
 * jointly called denominator for each pair. Algorithm 1 retains the source's
 * sequential float variance-weight sum and its two float output operations.
 * Matrices are not forced positive semidefinite: pairwise missingness can
 * produce a genuinely indefinite source GRM.</p>
 */
public final class GctaHardcallGrm {
    public static final String SOURCE_PROFILE = "GCTA-1.94.1-autosomal-hardcalls";
    private static final int SOURCE_BLOCK_MARKERS = 128;

    public enum Algorithm { SOURCE_0, SOURCE_1 }
    public enum Disposition { INCLUDED, MONOMORPHIC, NO_CALLED_ALLELES }

    /** Ordered composite identity; identical IIDs in distinct families remain distinct. */
    public record SampleId(String familyId, String individualId) {
        public SampleId { identifier(familyId); identifier(individualId); }
    }

    /** Original input position and explicit marker disposition; undefined means remain null. */
    public record MarkerInfo(String id, int calledSamples, Double suppliedAlleleMean,
            Double hweVariance, Disposition disposition) { }

    /** Logical input/output/work bounds; these are not an operating-system memory limit. */
    public record Limits(long maximumInputCells, long maximumSamplePairs,
            long maximumMultiplyAdds) {
        public Limits {
            require(maximumInputCells > 0 && maximumSamplePairs > 0
                && maximumMultiplyAdds > 0, "positive explicit GRM capacities are required");
        }
        public static Limits defaults() {
            return new Limits(5_000_000L, 2_000_000L, 2_000_000_000L);
        }
    }

    @FunctionalInterface
    public interface Progress {
        void completedBlock(int rawMarkersProcessed, int totalRawMarkers,
                int includedMarkersProcessed);
    }

    public static final class Result {
        private final List<SampleId> samples;
        private final List<MarkerInfo> markers;
        private final Algorithm algorithm;
        private final float[] lowerTriangle;
        private final int[] pairCounts;
        private final int includedMarkers;
        private final float sourceOutputWeight;

        private Result(List<SampleId> samples, List<MarkerInfo> markers,
                Algorithm algorithm, float[] lowerTriangle, int[] pairCounts,
                int includedMarkers, float sourceOutputWeight) {
            this.samples = List.copyOf(samples);
            this.markers = List.copyOf(markers);
            this.algorithm = algorithm;
            this.lowerTriangle = lowerTriangle;
            this.pairCounts = pairCounts;
            this.includedMarkers = includedMarkers;
            this.sourceOutputWeight = sourceOutputWeight;
        }
        public List<SampleId> samples() { return samples; }
        public List<MarkerInfo> markers() { return markers; }
        public Algorithm algorithm() { return algorithm; }
        public int includedMarkers() { return includedMarkers; }
        public float sourceOutputWeight() { return sourceOutputWeight; }
        public float[] lowerTriangle() { return lowerTriangle.clone(); }
        /** Exact integer counts; the original .grm.N.bin format exports them as float32. */
        public int[] pairCounts() { return pairCounts.clone(); }
        public float relationship(int first, int second) {
            return lowerTriangle[pairIndex(first, second, samples.size())];
        }
        public int jointlyCalledMarkers(int first, int second) {
            return pairCounts[pairIndex(first, second, samples.size())];
        }
    }

    private GctaHardcallGrm() { }

    public static Result compute(List<SampleId> samples, List<String> markerIds,
            double[][] variantBySample, Algorithm algorithm, Limits limits) {
        return compute(samples, markerIds, variantBySample, algorithm, limits,
            (processed, total, included) -> { });
    }

    public static Result compute(List<SampleId> samples, List<String> markerIds,
            double[][] variantBySample, Algorithm algorithm, Limits limits,
            Progress progress) {
        require(samples != null && !samples.isEmpty() && markerIds != null
            && !markerIds.isEmpty() && variantBySample != null
            && variantBySample.length == markerIds.size() && algorithm != null
            && limits != null && progress != null,
            "ordered samples, markers, hard calls, source algorithm and capacities are required");
        int n = samples.size(), m = markerIds.size();
        long pairs = (long) n * (n + 1L) / 2;
        require((long) m * n <= limits.maximumInputCells(), "maximumInputCells exceeded");
        require(pairs <= limits.maximumSamplePairs() && pairs <= Integer.MAX_VALUE,
            "maximumSamplePairs or Java array capacity exceeded");
        long work;
        try { work = Math.multiplyExact((long) m, pairs); }
        catch (ArithmeticException error) {
            throw new IllegalArgumentException("native GRM work capacity overflow", error);
        }
        require(work <= limits.maximumMultiplyAdds(), "maximumMultiplyAdds exceeded");
        for (SampleId id : samples) require(id != null, "null composite sample identity");
        var names = new HashSet<String>();
        for (String id : markerIds) {
            identifier(id);
            require(names.add(id), "duplicate marker identity: " + id);
        }
        List<SampleId> sampleCopy = List.copyOf(samples);
        List<String> markerCopy = List.copyOf(markerIds);
        require(new HashSet<>(sampleCopy).size() == sampleCopy.size(),
            "duplicate composite family/individual identity");

        byte[][] calls = new byte[m][];
        var markers = new ArrayList<MarkerInfo>(m);
        int included = 0;
        float varianceWeightSum = 0.0f;
        for (int v = 0; v < m; v++) {
            interrupted();
            double[] row = variantBySample[v];
            require(row != null && row.length == n,
                "marker dimensions differ: " + markerCopy.get(v));
            calls[v] = new byte[n];
            int count = 0;
            long sum = 0;
            for (int i = 0; i < n; i++) {
                double value = row[i];
                if (Double.isNaN(value)) { calls[v][i] = 3; continue; }
                require(value == 0.0 || value == 1.0 || value == 2.0,
                    "expected hard call 0/1/2 or declared missing at marker "
                        + markerCopy.get(v) + ", sample " + i);
                calls[v][i] = (byte) value;
                sum += (int) value;
                count++;
            }
            if (count == 0) {
                markers.add(new MarkerInfo(markerCopy.get(v), 0, null, null,
                    Disposition.NO_CALLED_ALLELES));
                continue;
            }
            double af = (0.5 * sum) / count;
            double mean = 2.0 * af;
            double variance = mean * (1.0 - af);
            if (variance < 1.0e-50) {
                markers.add(new MarkerInfo(markerCopy.get(v), count, mean, variance,
                    Disposition.MONOMORPHIC));
                continue;
            }
            markers.add(new MarkerInfo(markerCopy.get(v), count, mean, variance,
                Disposition.INCLUDED));
            // C++ float += double: promote the old sum, then round each addition.
            varianceWeightSum = (float) ((double) varianceWeightSum + variance);
            included++;
        }
        require(included > 0, "no source-valid polymorphic markers; no complete GRM");
        float weight = algorithm == Algorithm.SOURCE_0 ? 1.0f
            : (float) (1.0 / (varianceWeightSum / included));
        require(Float.isFinite(weight) && weight > 0.0f,
            "source output normalization is undefined; no complete GRM");
        double[] numerator = new double[(int) pairs];
        int[] counts = new int[(int) pairs];
        int processedIncluded = 0;
        for (int start = 0; start < m;) {
            interrupted();
            int end = (int) Math.min((long) start + SOURCE_BLOCK_MARKERS, m);
            var values = new ArrayList<double[]>(end - start);
            var masks = new ArrayList<byte[]>(end - start);
            for (int v = start; v < end; v++) {
                MarkerInfo info = markers.get(v);
                if (info.disposition() != Disposition.INCLUDED) continue;
                double[] z = new double[n];
                double scale = algorithm == Algorithm.SOURCE_0
                    ? Math.sqrt(1.0 / info.hweVariance()) : 1.0;
                for (int i = 0; i < n; i++) {
                    if (calls[v][i] != 3)
                        z[i] = (calls[v][i] - info.suppliedAlleleMean()) * scale;
                }
                values.add(z);
                masks.add(calls[v]);
            }
            int index = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++, index++) {
                    double sum = 0.0;
                    int called = 0;
                    for (int v = 0; v < values.size(); v++) {
                        if (masks.get(v)[i] != 3 && masks.get(v)[j] != 3) {
                            sum += values.get(v)[i] * values.get(v)[j];
                            called++;
                        }
                    }
                    numerator[index] += sum;
                    counts[index] += called;
                }
            }
            processedIncluded += values.size();
            progress.completedBlock(end, m, processedIncluded);
            start = end;
        }
        float[] matrix = new float[(int) pairs];
        int index = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++, index++) {
            require(counts[index] > 0, "no jointly called retained markers for sample pair "
                + i + "," + j + "; original zero fallback refused; no complete GRM");
            // Preserve the cast before multiplication and the float32 product.
            matrix[index] = (float) (numerator[index] / counts[index]) * weight;
            require(Float.isFinite(matrix[index]),
                "nonfinite source GRM value at sample pair " + i + "," + j);
        }
        return new Result(sampleCopy, markers, algorithm, matrix, counts, included, weight);
    }

    private static int pairIndex(int first, int second, int samples) {
        if (first < 0 || second < 0 || first >= samples || second >= samples)
            throw new IndexOutOfBoundsException("sample index outside ordered GRM");
        int row = Math.max(first, second), column = Math.min(first, second);
        return (int) ((long) row * (row + 1L) / 2 + column);
    }

    private static void identifier(String value) {
        require(value != null && !value.isEmpty(), "nonempty literal identity required");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            require(c > 0x20 && c != 0x7f, "ASCII whitespace/control is not a GCTA identity");
            if (Character.isHighSurrogate(c)) {
                require(i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1)),
                    "malformed UTF-16 identity");
                i++;
            } else require(!Character.isLowSurrogate(c), "malformed UTF-16 identity");
        }
    }

    private static void interrupted() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("native GCTA GRM interrupted; no complete result");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
