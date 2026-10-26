/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Sample-local rank signature scores with explicit ranking contracts.
 *
 * <p>UCell implements the normalized Mann-Whitney formula used by UCell 2.7.6+
 * for positive signatures. AUCell implements the normalized recovery AUC for
 * a supplied unique ranking. ssGSEA implements the unnormalized weighted
 * integrated walk, with GSVA's integer average-rank weights. These methods do
 * not fit a model, assign cell identities, or test population-level effects.</p>
 */
public final class SignatureScores {
    private SignatureScores() { }

    /** Descending expression ranks; tied values receive their average rank. */
    public static double[] descendingRanks(double[] values) {
        Integer[] order = order(values);
        double[] ranks = new double[values.length];
        for (int start = 0; start < order.length;) {
            int end = start + 1;
            while (end < order.length && values[order[end]] == values[order[start]]) end++;
            double rank = (start + 1.0 + end) / 2.0;
            for (int i = start; i < end; i++) ranks[order[i]] = rank;
            start = end;
        }
        return ranks;
    }

    /**
     * Unique descending ranks, breaking ties in input feature order.
     * AUCell's R random tie-breaking is deliberately not emulated.
     */
    public static double[] uniqueRanks(double[] values) {
        Integer[] order = order(values);
        double[] ranks = new double[values.length];
        for (int i = 0; i < order.length; i++) ranks[order[i]] = i + 1.0;
        return ranks;
    }

    /**
     * UCell positive-signature score; -1 indices are absent genes imputed at
     * maxRank. Indices must be distinct, except for the -1 missing sentinel.
     * The caller chooses whether absent genes are imputed, omitted, or rejected.
     */
    public static double ucell(double[] descendingRanks, int[] signature, int maxRank) {
        validateRanks(descendingRanks, false);
        return ucellUnchecked(descendingRanks, signature, maxRank);
    }

    private static double ucellUnchecked(double[] descendingRanks, int[] signature, int maxRank) {
        validateSignature(signature, descendingRanks.length, true);
        if (maxRank < 2 || maxRank > descendingRanks.length || signature.length > maxRank)
            throw new IllegalArgumentException("UCell requires 2 <= maxRank <= features and signature size <= maxRank");
        if (signature.length == 0) throw new IllegalArgumentException("Empty signature");
        double sum = 0;
        for (int index : signature) sum += index < 0 ? maxRank : Math.min(maxRank, descendingRanks[index]);
        double minimum = signature.length * (signature.length + 1.0) / 2.0;
        double denominator = signature.length * (double) maxRank - minimum;
        return 1.0 - (sum - minimum) / denominator;
    }

    /** AUCell normalized recovery AUC; ranks must be a permutation of 1..N. */
    public static double aucell(double[] uniqueRanks, int[] signature, int maxRank) {
        validateRanks(uniqueRanks, true);
        return aucellUnchecked(uniqueRanks, signature, maxRank);
    }

    private static double aucellUnchecked(double[] uniqueRanks, int[] signature, int maxRank) {
        validateSignature(signature, uniqueRanks.length, false);
        if (maxRank < 2 || maxRank > uniqueRanks.length)
            throw new IllegalArgumentException("AUCell requires 2 <= maxRank <= features");
        if (signature.length == 0) throw new IllegalArgumentException("Empty signature");
        int attainable = Math.min(signature.length, maxRank - 1);
        double maximum = attainable * (double) maxRank - attainable * (attainable + 1.0) / 2.0;
        double area = 0;
        for (int index : signature) if (uniqueRanks[index] < maxRank) area += maxRank - uniqueRanks[index];
        return area / maximum;
    }

    /**
     * Unnormalized ssGSEA; average ascending ranks are truncated to integers
     * before weighting, matching GSVA's dense-matrix scoring contract.
     */
    public static double ssgsea(double[] values, int[] signature, double alpha) {
        return prepare(values).ssgsea(signature, alpha);
    }

    /** Rank once, then score many signatures without repeatedly sorting features. */
    public static Prepared prepare(double[] values) { return new Prepared(values); }

    /** Immutable sample-local rankings, shared by a collection of signatures. */
    public static final class Prepared {
        private final double[] average;
        private final double[] unique;

        private Prepared(double[] values) {
            Integer[] order = order(values);
            average = new double[values.length];
            unique = new double[values.length];
            for (int start = 0; start < order.length;) {
                int end = start + 1;
                while (end < order.length && values[order[end]] == values[order[start]]) end++;
                double rank = (start + 1.0 + end) / 2.0;
                for (int i = start; i < end; i++) {
                    average[order[i]] = rank;
                    unique[order[i]] = i + 1.0;
                }
                start = end;
            }
        }

        /** UCell on this sample's average ranks. */
        public double ucell(int[] signature, int maxRank) { return ucellUnchecked(average, signature, maxRank); }

        /** AUCell on this sample's deterministic unique ranks. */
        public double aucell(int[] signature, int maxRank) { return aucellUnchecked(unique, signature, maxRank); }

        /** Raw ssGSEA on this sample's shared ranks. */
        public double ssgsea(int[] signature, double alpha) {
            validateSignature(signature, average.length, false);
            if (signature.length == 0 || signature.length == average.length)
                throw new IllegalArgumentException("ssGSEA requires both signature and background genes");
            if (!Double.isFinite(alpha) || alpha < 0 || alpha > 10)
                throw new IllegalArgumentException("alpha must be finite and in [0,10]");
            double weightedPosition = 0, totalWeight = 0, positions = 0;
            int n = average.length;
            for (int index : signature) {
                int ascendingRank = (int) (n + 1.0 - average[index]);
                double weight = Math.pow(ascendingRank, alpha);
                double reversedPosition = n - unique[index] + 1.0;
                weightedPosition += weight * reversedPosition;
                totalWeight += weight;
                positions += reversedPosition;
            }
            double background = (n * (n + 1.0) / 2.0 - positions) / (n - signature.length);
            return weightedPosition / totalWeight - background;
        }
    }

    private static Integer[] order(double[] values) {
        if (values.length < 2) throw new IllegalArgumentException("At least two features are required");
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) throw new IllegalArgumentException("Nonfinite expression value");
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int comparison = Double.compare(values[b], values[a]);
            return comparison != 0 ? comparison : Integer.compare(a, b);
        });
        return order;
    }

    private static void validateRanks(double[] ranks, boolean unique) {
        if (ranks.length < 2) throw new IllegalArgumentException("At least two features are required");
        Set<Double> seen = unique ? new HashSet<>() : null;
        for (double rank : ranks) {
            if (!Double.isFinite(rank) || rank < 1 || rank > ranks.length)
                throw new IllegalArgumentException("Ranks must lie in [1, features]");
            if (unique && (rank != Math.rint(rank) || !seen.add(rank)))
                throw new IllegalArgumentException("AUCell requires unique integer ranks");
        }
        if (!unique) {
            double[] sorted = ranks.clone();
            Arrays.sort(sorted);
            for (int start = 0; start < sorted.length;) {
                int end = start + 1;
                while (end < sorted.length && sorted[end] == sorted[start]) end++;
                if (sorted[start] != (start + 1.0 + end) / 2.0)
                    throw new IllegalArgumentException("Invalid average-rank vector");
                start = end;
            }
        }
    }

    private static void validateSignature(int[] indices, int features, boolean missing) {
        Set<Integer> seen = new HashSet<>();
        for (int index : indices) {
            if (index < (missing ? -1 : 0) || index >= features || index >= 0 && !seen.add(index))
                throw new IllegalArgumentException("Signature has duplicate or invalid feature indices");
        }
    }
}
