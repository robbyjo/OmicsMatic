/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * geNorm reference-gene stability, stepwise selection and normalization factors.
 *
 * <p>Implements the equations of Vandesompele et al. (2002),
 * DOI 10.1186/gb-2002-3-7-research0034. Samples are rows and candidate reference
 * genes are columns. Quantities are explicitly transformed to log2 quantities;
 * Cq/Ct values are not quantities and are never implicitly converted. In this
 * log-space profile, normalization factors are arithmetic means of log2
 * quantities. This avoids a product of linear quantities. It is not a replica
 * of the finite-precision product used by NormqPCR's {@code log=FALSE} branch.</p>
 *
 * <p>All inputs must be finite and complete. No imputation, efficiency estimate,
 * reference-gene independence test or automatic 0.15 decision is performed.
 * The last two genes share the best rank. An exact tie for the largest M removes
 * the first remaining input column, matching NormqPCR 1.58.0's matrix method
 * with {@code log=TRUE, minNrHKs=2}. Near ties can depend on floating-point
 * rounding; the full M trace is returned. This is an independent Java method,
 * not the proprietary geNorm application or an implementation of NormFinder.</p>
 */
public final class GeNorm {
    private static final double LOG_TWO = Math.log(2.0);
    /** Deliberate limit for candidate-gene pair calculations. */
    public static final long MAX_PAIR_WORK = 200_000_000L;
    /** Candidate references, not target genes. */
    public static final int MAX_REFERENCES = 512;
    /** Deliberate sample limit; no missing samples are silently dropped. */
    public static final int MAX_SAMPLES = 100_000;

    /** The caller must select an input representation. Neither value means Cq. */
    public enum Scale { QUANTITY, LOG2_QUANTITY }

    /** One complete selection step; indices refer to original input columns. */
    public record Step(int[] columns, double[] stabilityM, double meanM,
            int removedColumn, double variationToNext) {
        public Step { columns = columns.clone(); stabilityM = stabilityM.clone(); }
        @Override public int[] columns() { return columns.clone(); }
        @Override public double[] stabilityM() { return stabilityM.clone(); }
    }

    /**
     * Order runs from most to least stable. The first two share rank one and
     * retain their relative input order. The two-gene final step has no next
     * factor, represented by NaN in {@link Step#variationToNext()}.
     */
    public record Result(int[] order, List<Step> steps) {
        public Result { order = order.clone(); steps = List.copyOf(steps); }
        @Override public int[] order() { return order.clone(); }
    }

    private GeNorm() { }

    /**
     * Evaluate all stepwise M and V values, stopping at the inseparable pair.
     * With two candidates only the final M step is defined; unlike the
     * NormqPCR selectHKs interface, no three-candidate requirement is imposed.
     *
     * @param values complete samples-by-reference matrix, at least 2 by 2
     * @param scale explicit input representation
     * @return original column indices and all diagnostics
     */
    public static Result select(double[][] values, Scale scale) {
        double[][] logs = log2(values, scale);
        int samples = logs.length, genes = logs[0].length;
        if (genes < 2 || genes > MAX_REFERENCES || (long) samples * genes * genes > MAX_PAIR_WORK)
            throw new IllegalArgumentException("Require 2..512 reference genes and samples*genes*genes <= " + MAX_PAIR_WORK);
        double[][] pairSd = new double[genes][genes];
        for (int j = 0; j < genes; j++) for (int k = j + 1; k < genes; k++) {
            double[] differences = new double[samples];
            for (int s = 0; s < samples; s++) differences[s] = finite(logs[s][j] - logs[s][k]);
            pairSd[j][k] = pairSd[k][j] = standardDeviation(differences);
        }
        int[] active = new int[genes], order = new int[genes];
        for (int j = 0; j < genes; j++) active[j] = j;
        List<Step> steps = new ArrayList<>();
        for (int count = genes; count >= 2; count--) {
            double[] m = new double[count];
            for (int j = 0; j < count; j++) {
                double[] sd = new double[count - 1]; int at = 0;
                for (int k = 0; k < count; k++) if (k != j) sd[at++] = pairSd[active[j]][active[k]];
                m[j] = mean(sd);
            }
            if (count == 2) {
                order[0] = active[0]; order[1] = active[1];
                steps.add(new Step(active, m, mean(m), -1, Double.NaN));
            } else {
                int worst = 0;
                for (int j = 1; j < count; j++) if (m[j] > m[worst]) worst = j;
                int[] next = new int[count - 1];
                for (int j = 0, at = 0; j < count; j++) if (j != worst) next[at++] = active[j];
                double[] oldFactor = factors(logs, active), newFactor = factors(logs, next);
                double[] differences = new double[samples];
                for (int s = 0; s < samples; s++) differences[s] = finite(newFactor[s] - oldFactor[s]);
                order[count - 1] = active[worst];
                steps.add(new Step(active, m, mean(m), active[worst], standardDeviation(differences)));
                active = next;
            }
        }
        return new Result(order, steps);
    }

    /**
     * Compute log2 normalization factors for explicitly selected columns.
     * The selected reference columns must be distinct; at least two are needed.
     *
     * @param values samples by genes
     * @param scale explicit input representation
     * @param columns original zero-based selected reference columns
     * @return one log2 normalization factor per sample
     */
    public static double[] log2Factors(double[][] values, Scale scale, int[] columns) {
        double[][] logs = log2(values, scale);
        Objects.requireNonNull(columns, "columns");
        int[] selected = columns.clone(); boolean[] seen = new boolean[logs[0].length];
        if (selected.length < 2) throw new IllegalArgumentException("Select at least two reference genes");
        for (int column : selected) {
            if (column < 0 || column >= seen.length || seen[column])
                throw new IllegalArgumentException("Reference columns must be distinct and in range");
            seen[column] = true;
        }
        return factors(logs, selected);
    }

    /**
     * Normalize target quantities, returning log2 normalized quantities.
     * Target rows must correspond exactly to the factor sample order. This
     * method does not identify samples or infer calibrators or fold changes.
     *
     * @param targets samples by targets
     * @param scale explicit target representation
     * @param log2Factors one finite log2 factor per sample
     * @return log2 target quantity minus the supplied log2 factor
     */
    public static double[][] normalizeLog2(double[][] targets, Scale scale, double[] log2Factors) {
        double[][] logs = log2(targets, scale);
        double[] factors = Objects.requireNonNull(log2Factors, "log2Factors").clone();
        if (factors.length != logs.length) throw new IllegalArgumentException("Target and factor sample counts differ");
        for (int s = 0; s < logs.length; s++) {
            finite(factors[s]);
            for (int g = 0; g < logs[s].length; g++) logs[s][g] = finite(logs[s][g] - factors[s]);
        }
        return logs;
    }

    private static double[][] log2(double[][] values, Scale scale) {
        Objects.requireNonNull(values, "values"); Objects.requireNonNull(scale, "scale");
        if (values.length < 2 || values.length > MAX_SAMPLES || values[0] == null || values[0].length == 0)
            throw new IllegalArgumentException("Require 2..100000 samples and at least one column");
        int genes = values[0].length;
        if ((long) genes * values.length > 10_000_000L)
            throw new IllegalArgumentException("Matrix exceeds ten million entries");
        double[][] logs = new double[values.length][genes];
        for (int s = 0; s < logs.length; s++) {
            if (values[s] == null || values[s].length != genes) throw new IllegalArgumentException("Ragged matrix");
            for (int g = 0; g < genes; g++) {
                double v = values[s][g];
                if (!Double.isFinite(v) || (scale == Scale.QUANTITY && v <= 0))
                    throw new IllegalArgumentException("Use complete finite log2 quantities or strictly positive finite quantities; Cq/Ct is not inferred");
                logs[s][g] = scale == Scale.QUANTITY ? finite(Math.log(v) / LOG_TWO) : v;
            }
        }
        return logs;
    }

    private static double[] factors(double[][] logs, int[] columns) {
        double[] factors = new double[logs.length], row = new double[columns.length];
        for (int s = 0; s < logs.length; s++) {
            for (int j = 0; j < columns.length; j++) row[j] = logs[s][columns[j]];
            factors[s] = mean(row);
        }
        return factors;
    }

    private static double standardDeviation(double[] values) {
        double average = mean(values), total = 0, correction = 0;
        for (double value : values) {
            double delta = finite(value - average);
            double term = finite(delta * delta) - correction;
            double next = finite(total + term); correction = (next - total) - term; total = next;
        }
        return finite(Math.sqrt(total / (values.length - 1)));
    }

    private static double mean(double[] values) {
        double total = 0, correction = 0;
        for (double value : values) {
            double term = value - correction, next = finite(total + term);
            correction = (next - total) - term; total = next;
        }
        return finite(total / values.length);
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Non-finite geNorm arithmetic; no complete result is admitted");
        return value;
    }
}
