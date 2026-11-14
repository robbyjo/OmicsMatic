/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Arithmetic and classification follow Chris McGinnis's DoubletFinder 2.0.6,
 * original commit 1b244d8f0d54b4b1cb4365639931bbb16f01e1cd (CC0).
 */
package org.jlinalg.singlecell;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Native DoubletFinder annotation arithmetic, pK metric tables and reuse-pANN calls.
 * These operations do not generate artificial cells, preprocess counts, run PCA,
 * fit a neighbor graph or infer a paper's unreported settings. Use the original
 * R workflow for those operations and the original plots.
 * <p>String labels remain literal. A null annotation represents R NA; the string
 * "NA" is an ordinary label. Missing annotations remain in the denominator of
 * the original {@code modelHomotypic} calculation. Missing pK values form an
 * output row whose equality subset is empty, as in original R.
 * <p>Finite arithmetic uses high precision accumulation before conversion to
 * binary64. It is compared numerically with original R, whose accumulation can
 * use platform extended precision; bit equality is not claimed. Nonfinite metric
 * values are retained and flagged. A table with no finite original plot range
 * is refused, because original {@code find.pK} cannot return that table.
 */
public final class DoubletFinder206 {
    private static final MathContext ACCUMULATION = new MathContext(80, RoundingMode.HALF_EVEN);

    private DoubletFinder206() { }

    /** Full typed row of the original find.pK table; meanAuc is absent unless the table has AUC. */
    public record PkMetric(int paramId, String pK, Double meanAuc,
                           double meanBc, double varianceBc, double bcMetric) { }

    /** Immutable table in first-seen pK order, including nonfinite rows. */
    public record PkTable(boolean hasAuc, List<PkMetric> rows, boolean requiresNonFiniteReview) {
        public PkTable {
            rows = List.copyOf(rows);
        }

        /** Original column names and order. */
        public List<String> columnNames() {
            return hasAuc
                    ? List.of("ParamID", "pK", "MeanAUC", "MeanBC", "VarBC", "BCmetric")
                    : List.of("ParamID", "pK", "MeanBC", "VarBC", "BCmetric");
        }
    }

    /**
     * Original sum of squared annotation frequencies. Empty and all-missing
     * annotation vectors return zero. Null labels are excluded from the table,
     * but included in {@code annotations.length}; no label is trimmed or repaired.
     */
    public static double modelHomotypic(String[] annotations) {
        Objects.requireNonNull(annotations, "annotations");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String annotation : annotations) {
            if (annotation != null) counts.merge(annotation, 1, Math::addExact);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int count : counts.values()) {
            double frequency = (double) count / annotations.length;
            // R squares the binary64 frequency before summing the table.
            double square = frequency * frequency;
            sum = sum.add(new BigDecimal(square));
        }
        return sum.doubleValue();
    }

    /**
     * Original find.pK metric table for caller-supplied character pK labels,
     * BCreal and optional AUC. No pK, expected count or rate is chosen. The
     * variance follows {@code sd(BCreal)^2}, including the square-root/resquare
     * rounding step. Java NaN represents an unavailable R numeric value, without
     * a claim to preserve R's distinct NA/NaN payload bits.
     * @param auc null for the original five-column branch
     * @throws IllegalArgumentException if dimensions differ, finite accumulation
     * overflows or the original plotting step has no finite range
     */
    public static PkTable findPk(String[] pK, double[] bcReal, double[] auc) {
        Objects.requireNonNull(pK, "pK");
        Objects.requireNonNull(bcReal, "bcReal");
        if (pK.length == 0 || pK.length != bcReal.length || (auc != null && auc.length != pK.length)) {
            throw new IllegalArgumentException("Nonempty, equally sized original sweep columns required");
        }
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < pK.length; i++) groups.computeIfAbsent(pK[i], key -> new ArrayList<>()).add(i);
        List<PkMetric> rows = new ArrayList<>();
        boolean nonFinite = false;
        boolean finiteMetric = false;
        boolean finiteAuc = auc == null;
        int id = 0;
        for (Map.Entry<String, List<Integer>> group : groups.entrySet()) {
            // which(pK == NA) has no entries, rather than matching missing pK.
            List<Integer> indices = group.getKey() == null ? List.of() : group.getValue();
            double mean = mean(bcReal, indices);
            double variance = variance(bcReal, indices);
            double sd = Math.sqrt(variance);
            double originalVariance = sd * sd;
            double metric = mean / originalVariance;
            Double meanAuc = auc == null ? null : mean(auc, indices);
            rows.add(new PkMetric(++id, group.getKey(), meanAuc, mean, originalVariance, metric));
            nonFinite |= !Double.isFinite(mean) || !Double.isFinite(originalVariance) || !Double.isFinite(metric)
                    || (meanAuc != null && !Double.isFinite(meanAuc));
            finiteMetric |= Double.isFinite(metric);
            finiteAuc |= meanAuc != null && Double.isFinite(meanAuc);
        }
        if (!finiteMetric || !finiteAuc) {
            throw new IllegalArgumentException("Original find.pK plotting has no finite range; no complete table admitted");
        }
        return new PkTable(auc != null, rows, nonFinite);
    }

    private static double mean(double[] values, List<Integer> indices) {
        if (indices.isEmpty()) return Double.NaN;
        BigDecimal sum = BigDecimal.ZERO;
        double nonFiniteSum = 0;
        boolean hasNonFinite = false;
        for (int index : indices) {
            double value = values[index];
            if (Double.isFinite(value)) sum = sum.add(new BigDecimal(value));
            else {
                hasNonFinite = true;
                nonFiniteSum += value;
            }
        }
        if (hasNonFinite) return nonFiniteSum / indices.size();
        double result = sum.divide(BigDecimal.valueOf(indices.size()), ACCUMULATION).doubleValue();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Finite mean exceeded binary64 range");
        return result;
    }

    private static double variance(double[] values, List<Integer> indices) {
        if (indices.size() < 2) return Double.NaN;
        BigDecimal sum = BigDecimal.ZERO;
        for (int index : indices) {
            if (!Double.isFinite(values[index])) return Double.NaN;
            sum = sum.add(new BigDecimal(values[index]));
        }
        BigDecimal center = sum.divide(BigDecimal.valueOf(indices.size()), ACCUMULATION);
        BigDecimal squares = BigDecimal.ZERO;
        for (int index : indices) {
            BigDecimal delta = new BigDecimal(values[index]).subtract(center);
            squares = squares.add(delta.multiply(delta));
        }
        double result = squares.divide(BigDecimal.valueOf(indices.size() - 1L), ACCUMULATION).doubleValue();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Finite variance exceeded binary64 range");
        return result;
    }

    /**
     * Original reuse.pANN classification in supplied row order. Ties retain
     * input order, missing scores sort last, and signed zero scores tie. The
     * original R expression {@code [1:nExp]} selects the first score even when
     * nExp is zero; this behavior is retained explicitly. No rate or rounding
     * policy is inferred. The supported domain is a nonempty score vector and
     * {@code 0 <= expectedDoublets <= scores.length}.
     */
    public static String[] classifyExistingPann(double[] scores, int expectedDoublets) {
        Objects.requireNonNull(scores, "scores");
        if (scores.length == 0 || expectedDoublets < 0 || expectedDoublets > scores.length) {
            throw new IllegalArgumentException("Nonempty scores and explicit expected count in [0, cells] required");
        }
        Integer[] order = new Integer[scores.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            double x = scores[a];
            double y = scores[b];
            if (Double.isNaN(x)) return Double.isNaN(y) ? 0 : 1;
            if (Double.isNaN(y)) return -1;
            return x > y ? -1 : x < y ? 1 : 0;
        });
        String[] classifications = new String[scores.length];
        Arrays.fill(classifications, "Singlet");
        for (int i = 0; i < Math.max(1, expectedDoublets); i++) classifications[order[i]] = "Doublet";
        return classifications;
    }
}
