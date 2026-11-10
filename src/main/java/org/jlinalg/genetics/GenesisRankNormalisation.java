/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import jdistlib.Normal;

/**
 * Native finite-input implementation of GENESIS2.42.0 {@code .rankNorm}:
 * {@code qnorm((rank(x) - 0.5) / length(x))}, with average ranks for ties.
 * Algorithm attribution: GENESIS authors, GPL-3.0; normal quantiles use the
 * project's separately pinned JDistlib dependency. No model is fitted and no
 * residual variance, group, imputation or rescaling is inferred.
 *
 * <p>Input order is retained. Nonfinite residuals are outside this profile,
 * even when R's default missing-value ranking would assign them a rank.
 * Explicit groups apply the same original rank transform independently to
 * each caller-supplied group. This does not reproduce the complete
 * {@code nullModelInvNorm} fitting/rescaling procedure.</p>
 */
public final class GenesisRankNormalisation {
    private GenesisRankNormalisation() { }
    public static final int MAX_SAMPLES = 1_000_000;

    /** Logical capacities, checked before rank/sort allocation; not OS memory quotas. */
    public record Limits(int samples, long workingBytes) {
        public Limits {
            if (samples < 1 || samples > MAX_SAMPLES || workingBytes < 1)
                throw new IllegalArgumentException("Declare positive capacities; maximum sample ceiling is1000000");
        }
    }

    /** Defensive ordered ranks, scores and per-row group sizes. */
    public record Result(double[] averageRanks, double[] scores, int[] groupSizes, List<String> groupOrder) {
        public Result {
            averageRanks = averageRanks.clone(); scores = scores.clone(); groupSizes = groupSizes.clone(); groupOrder = List.copyOf(groupOrder);
            if (averageRanks.length != scores.length || scores.length != groupSizes.length)
                throw new IllegalArgumentException("Rank/score/group row counts differ");
        }
        @Override public double[] averageRanks() { return averageRanks.clone(); }
        @Override public double[] scores() { return scores.clone(); }
        @Override public int[] groupSizes() { return groupSizes.clone(); }
    }

    /** Rank all supplied finite values together; an explicitly empty vector remains empty. */
    public static Result normalize(double[] residuals, Limits limits) {
        return calculate(residuals, null, limits);
    }

    /** Rank within nonempty literal group labels; group order records first appearance. */
    public static Result normalizeByGroup(double[] residuals, List<String> groupLabels, Limits limits) {
        Objects.requireNonNull(groupLabels, "Explicit group labels");
        if (residuals == null || groupLabels.size() != residuals.length)
            throw new IllegalArgumentException("Supply exactly one group label per original residual");
        return calculate(residuals, List.copyOf(groupLabels), limits);
    }

    private static Result calculate(double[] residuals, List<String> groups, Limits limits) {
        Objects.requireNonNull(residuals, "Original residuals"); Objects.requireNonNull(limits, "Explicit logical limits");
        int n = residuals.length;
        if (n > limits.samples() || 65536L + 512L * n > limits.workingBytes())
            throw new IllegalArgumentException("Complete native ranking capacity exceeded before allocating sort storage");
        for (double value : residuals) if (!Double.isFinite(value))
            throw new IllegalArgumentException("Nonfinite original residual is outside the finite GENESIS rank profile; preserve it and skip dependent native transformation without repair");
        var members = new LinkedHashMap<String, List<Integer>>();
        if (groups == null) members.put("", new ArrayList<>());
        for (int i = 0; i < n; i++) {
            String group = groups == null ? "" : groups.get(i);
            if (groups != null) {
                if (group.isEmpty() || group.chars().anyMatch(c -> c < 32 || c == 127))
                    throw new IllegalArgumentException("Explicit nonempty literal group labels required; no missing-group assignment");
                for (int j = 0; j < group.length(); j++) if (Character.isSurrogate(group.charAt(j))) {
                    if (!Character.isHighSurrogate(group.charAt(j)) || j + 1 == group.length() || !Character.isLowSurrogate(group.charAt(++j)))
                        throw new IllegalArgumentException("Invalid Unicode group label");
                }
            }
            members.computeIfAbsent(group, ignored -> new ArrayList<>()).add(i);
        }
        double[] ranks = new double[n], scores = new double[n]; int[] sizes = new int[n];
        for (List<Integer> member : members.values()) {
            Integer[] order = member.toArray(Integer[]::new); Arrays.sort(order, Comparator.comparingDouble(i -> residuals[i]));
            for (int lo = 0; lo < order.length;) {
                int hi = lo + 1;
                // R's numeric ties include the two signed zeros. Double.compare
                // orders them adjacently; numeric equality merges the whole tie.
                while (hi < order.length && residuals[order[hi]] == residuals[order[lo]]) hi++;
                double rank = (lo + 1.0 + hi) / 2.0, probability = (rank - 0.5) / order.length;
                double score = Normal.quantile(probability, 0, 1, true, false);
                if (!Double.isFinite(score)) throw new ArithmeticException("Unassessable native normal quantile; no repair");
                for (int k = lo; k < hi; k++) { ranks[order[k]] = rank; scores[order[k]] = score; sizes[order[k]] = order.length; }
                lo = hi;
            }
        }
        return new Result(ranks, scores, sizes, groups == null ? List.of() : List.copyOf(members.keySet()));
    }
}
