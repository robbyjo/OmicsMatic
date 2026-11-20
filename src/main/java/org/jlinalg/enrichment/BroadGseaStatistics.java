/*
 * Source-defined arithmetic derived from GSEA Desktop, Copyright (c) 2003-2024
 * Broad Institute, Inc., Massachusetts Institute of Technology, and Regents of
 * the University of California. All rights reserved.
 * BSD-style notice: META-INF/licenses/GSEA-DESKTOP-LICENSE.txt.
 */
package org.jlinalg.enrichment;

import java.util.Arrays;
import java.util.Objects;

/**
 * Native arithmetic for prepared Broad GSEA scores and original null ES vectors.
 * This class does not perform expression ranking, permutations, identifier
 * mapping, filtering, sorting of input genes, or gene-set collection retrieval.
 * Preserve the supplied rank and member order: the original weights sum in
 * float, the walk accumulates in double, and stored scores are float.
 *
 * <p>These are the Broad source definitions, including strict nominal/FWER
 * comparisons and per-permutation-column empirical FDR. They are distinct from
 * fgsea statistics and from BH/BY adjustments. The caller must retain inputs,
 * null-model identity and diagnostics; arithmetic alone does not qualify a
 * workflow or a complete scientific result.</p>
 */
public final class BroadGseaStatistics {
    private BroadGseaStatistics() { }

    public enum Weight { CLASSIC, WEIGHTED, WEIGHTED_P2, WEIGHTED_P1_5 }

    public record Score(float es, int rankAtEs, float rankScoreAtEs,
                        int[] hits, float[] hitProfile, float[] fullProfile) {
        public Score {
            hits = hits.clone();
            hitProfile = hitProfile.clone();
            fullProfile = fullProfile.clone();
        }
        @Override public int[] hits() { return hits.clone(); }
        @Override public float[] hitProfile() { return hitProfile.clone(); }
        @Override public float[] fullProfile() { return fullProfile.clone(); }
    }

    public record Family(float[] nes, float[] nominalP, float[] fdr, float[] fwer,
                         float[][] normalizedNull) {
        public Family {
            nes = nes.clone(); nominalP = nominalP.clone(); fdr = fdr.clone(); fwer = fwer.clone();
            normalizedNull = copy(normalizedNull);
        }
        @Override public float[] nes() { return nes.clone(); }
        @Override public float[] nominalP() { return nominalP.clone(); }
        @Override public float[] fdr() { return fdr.clone(); }
        @Override public float[] fwer() { return fwer.clone(); }
        @Override public float[][] normalizedNull() { return copy(normalizedNull); }
    }

    /**
     * Original max-deviation walk. Members are zero-based indices in their
     * explicitly supplied member order, not necessarily sorted. A complete
     * nonempty proper subset is required. Cases where upstream substitutes
     * nonfinite weights/powers are refused rather than silently repaired.
     */
    public static Score score(float[] orderedScores, int[] memberOrder, Weight weight) {
        Objects.requireNonNull(orderedScores, "orderedScores");
        Objects.requireNonNull(memberOrder, "memberOrder");
        Objects.requireNonNull(weight, "weight");
        int n = orderedScores.length, k = memberOrder.length;
        require(k > 0 && k < n, "Gene set must be a nonempty proper subset of the ranked universe");
        for (int i = 0; i < n; i++) finite(orderedScores[i], "rank score " + i);
        boolean[] member = new boolean[n];
        float total = 0;
        for (int index : memberOrder) {
            require(index >= 0 && index < n, "Member index outside ranked universe");
            require(!member[index], "Duplicate member index");
            member[index] = true;
            if (weight != Weight.CLASSIC) total += denominatorWeight(orderedScores[index], weight);
        }
        if (weight != Weight.CLASSIC && (!Float.isFinite(total) || total <= 0))
            throw numerical("Zero/nonfinite source weight denominator; no fallback or rescaling applied");
        float miss = 1.0f / ((float) n - (float) k);
        float[] full = new float[n], hitProfile = new float[k];
        int[] hits = new int[k];
        double running = 0;
        float es = 0, rankScore = 0;
        int lastHit = -1, rank = 0, hitCount = 0;
        for (int r = 0; r < n; r++) {
            boolean last = r == n - 1;
            if (!member[r] && !last) continue;
            int gap = r - lastHit - 1;
            if (gap > 0) {
                double trail = running;
                for (int j = lastHit + 1; j < r; j++) {
                    trail -= (double) miss;
                    full[j] = (float) trail;
                }
                running -= gap * (double) miss;
                if (Math.abs(es) < Math.abs(running)) {
                    es = (float) running; rank = r - 1; rankScore = orderedScores[r - 1];
                }
            }
            if (last && !member[r]) {
                running -= (double) miss;
            } else {
                lastHit = r;
                float hit = weight == Weight.CLASSIC ? 1.0f / (float) k
                        : numeratorWeight(orderedScores[r], weight) / total;
                finite(hit, "computed hit weight at rank " + r);
                running += (double) hit;
                hits[hitCount] = r;
                hitProfile[hitCount++] = (float) running;
            }
            full[r] = (float) running;
            if (Math.abs(es) < Math.abs(running)) {
                es = (float) running; rank = r; rankScore = orderedScores[r];
            }
        }
        return new Score(es, rank, rankScore, hits, hitProfile, full);
    }

    /**
     * Source meandiv normalization, sign-separated nominal P, empirical FDR and
     * FWER from a complete tested family. Null rows correspond to sets, columns
     * to permutations. Column identity must be retained even for gene-set nulls.
     * The capacity limit is explicit and never changes the requested family.
     * Unused absent sign tails are allowed; a nonfinite required statistic is not.
     */
    public static Family meandiv(float[] observedEs, float[][] nullEs, long maximumCells) {
        Objects.requireNonNull(observedEs, "observedEs");
        Objects.requireNonNull(nullEs, "nullEs");
        int m = observedEs.length;
        require(m > 0 && nullEs.length == m, "Provide the complete nonempty tested family");
        require(maximumCells > 0, "Explicit maximumCells must be positive");
        require(nullEs[0] != null && nullEs[0].length > 0, "Null vectors must contain permutations");
        int p = nullEs[0].length;
        require((long) m * p <= maximumCells, "Family exceeds explicit maximumCells; no rows/permutations removed");
        float[] nes = new float[m], np = new float[m], fdr = new float[m], fwer = new float[m];
        float[][] normalized = new float[m][p];
        for (int r = 0; r < m; r++) {
            finite(observedEs[r], "observed ES " + r);
            // The source FdrMap adds an ES=0 set in both sign passes and refuses a duplicate entry.
            require(observedEs[r] != 0, "Zero observed ES has no admitted original FDR result");
            require(nullEs[r] != null && nullEs[r].length == p, "Different null permutation counts");
            int pos = 0, neg = 0, nominalTail = 0, nominalSize = 0;
            double posSum = 0, negSum = 0;
            for (float value : nullEs[r]) {
                finite(value, "null ES in row " + r);
                if (value >= 0) { posSum += value; pos++; }
                if (value <= 0) { negSum += value; neg++; }
                if (observedEs[r] >= 0 ? value >= 0 : value <= 0) {
                    nominalSize++;
                    if (observedEs[r] >= 0 ? value > observedEs[r] : value < observedEs[r]) nominalTail++;
                }
            }
            float posMean = (float) (posSum / pos), negMean = (float) (negSum / neg);
            nes[r] = observedEs[r] / (observedEs[r] >= 0 ? posMean : Math.abs(negMean));
            finite(nes[r], "required normalized ES in row " + r);
            np[r] = (float) ((double) nominalTail / nominalSize);
            finite(np[r], "required nominal P in row " + r);
            for (int c = 0; c < p; c++) {
                float value = nullEs[r][c];
                normalized[r][c] = value / (value >= 0 ? posMean : Math.abs(negMean));
                finite(normalized[r][c], "required normalized null ES at " + r + "," + c);
            }
        }
        Integer[] ascending = new Integer[m], descending = new Integer[m];
        for (int r = 0; r < m; r++) ascending[r] = descending[r] = r;
        Arrays.sort(ascending, (a, b) -> Float.compare(nes[a], nes[b]));
        Arrays.sort(descending, (a, b) -> Float.compare(nes[b], nes[a]));
        int[] ascRank = new int[m], descRank = new int[m];
        int positiveSets = 0, negativeSets = 0;
        for (int r = 0; r < m; r++) {
            ascRank[ascending[r]] = descRank[descending[r]] = r;
            if (nes[r] >= 0) positiveSets++;
            if (nes[r] <= 0) negativeSets++;
        }
        float[] sums = new float[m], positive = new float[m], negative = new float[m];
        int[] columns = new int[m], extreme = new int[m];
        for (int c = 0; c < p; c++) {
            int pos = 0, neg = 0;
            float maximum = Float.NEGATIVE_INFINITY, minimum = Float.POSITIVE_INFINITY;
            for (int r = 0; r < m; r++) {
                float value = normalized[r][c];
                maximum = Math.max(maximum, value); minimum = Math.min(minimum, value);
                if (value >= 0) positive[pos++] = value;
                if (value <= 0) negative[neg++] = value;
            }
            Arrays.sort(positive, 0, pos); Arrays.sort(negative, 0, neg);
            for (int r = 0; r < m; r++) {
                boolean plus = observedEs[r] >= 0;
                int size = plus ? pos : neg;
                if (size > 0) {
                    int count = plus ? pos - lower(positive, pos, nes[r]) : upper(negative, neg, nes[r]);
                    sums[r] += (float) count / (float) size;
                    columns[r]++;
                }
                if (nes[r] >= 0 ? maximum > nes[r] : minimum < nes[r]) extreme[r]++;
            }
        }
        for (int r = 0; r < m; r++) {
            float numerator = sums[r] / columns[r];
            float denominator = observedEs[r] >= 0 ? (float) (descRank[r] + 1) / positiveSets
                    : (float) (ascRank[r] + 1) / negativeSets;
            float value = numerator / denominator;
            boolean sameSign = observedEs[r] > 0 && nes[r] > 0 || observedEs[r] < 0 && nes[r] < 0;
            fdr[r] = !sameSign || value > 1 ? 1 : value;
            fwer[r] = (float) ((double) extreme[r] / p);
            finite(fdr[r], "required empirical FDR in row " + r);
            finite(fwer[r], "required FWER in row " + r);
        }
        return new Family(nes, np, fdr, fwer, normalized);
    }

    private static int lower(float[] values, int size, float target) {
        int left = 0, right = size;
        while (left < right) { int mid = (left + right) >>> 1;
            if (values[mid] < target) left = mid + 1; else right = mid; }
        return left;
    }
    private static int upper(float[] values, int size, float target) {
        int left = 0, right = size;
        while (left < right) { int mid = (left + right) >>> 1;
            if (values[mid] <= target) left = mid + 1; else right = mid; }
        return left;
    }
    private static float denominatorWeight(float value, Weight weight) {
        float result = switch (weight) {
            case CLASSIC -> 1;
            case WEIGHTED -> Math.abs(value);
            case WEIGHTED_P2 -> value * value;
            case WEIGHTED_P1_5 -> (float) Math.pow(Math.abs(value), 1.5);
        };
        if (!Float.isFinite(result)) throw numerical("Source would replace a nonfinite powered weight; refused");
        return result;
    }
    private static float numeratorWeight(float value, Weight weight) {
        // The original 1.5 numerator uses pow(value, 1.5), not pow(abs(value), 1.5).
        float result = weight == Weight.WEIGHTED_P1_5 ? (float) Math.pow(value, 1.5)
                : denominatorWeight(value, weight);
        if (!Float.isFinite(result)) throw numerical("Source would replace a nonfinite hit/power with 1e-6; refused");
        return result;
    }
    private static float[][] copy(float[][] values) {
        float[][] result = new float[values.length][];
        for (int i = 0; i < values.length; i++) result[i] = values[i].clone();
        return result;
    }
    private static void finite(float value, String what) {
        if (!Float.isFinite(value)) throw numerical("Nonfinite " + what + "; no repair applied");
    }
    private static ArithmeticException numerical(String message) { return new ArithmeticException(message); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
