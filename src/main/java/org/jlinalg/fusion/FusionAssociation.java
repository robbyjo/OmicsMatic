/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * Source behavior: gusevlab/fusion_twas, commit 9346c1222bffbb34499fa7a8e23c1b701b55cb05,
 * FUSION.assoc_test.R. See META-INF/licenses/FUSION-GPL-3.0.txt and FUSION-NOTICE.txt.
 */
package org.jlinalg.fusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jlinalg.xwas.PredictedOmics;
import org.jlinalg.xwas.SummaryMath;

/**
 * FUSION allele flags and prepared, single-selected-model association.
 * Weights act on standardized genotypes; they are NOT raw-dosage weights.
 * The caller supplies matched SNP order and reference correlation LD. This
 * component does not load RDat/BED files, select/train a predictive model,
 * perform permutation/COLOC, or reproduce a complete FUSION script.
 */
public final class FusionAssociation {
    private FusionAssociation() { }
    /** The source's fixed regularizer, not an adjustable statistical repair. */
    public static final double IMPUTATION_RIDGE = 0.1;
    /** Dense-matrix capacity; use the upstream adapter for larger prepared loci. */
    public static final int MAX_VARIANTS = 4096;

    /** R logical values; NA is distinct from FALSE. */
    public enum Logical { TRUE, FALSE, NA }
    /** Original source flags; keep does not assert allele compatibility. */
    public record AlleleFlags(Logical keep, Logical flip) { }
    /** No-result states retain original missing/nonfinite outputs without flooring. */
    public enum Status {
        COMPLETE, ZERO_SELECTED_WEIGHTS, NO_OBSERVED_Z, TOO_MANY_MISSING_Z,
        LOW_IMPUTATION_QUALITY, NONFINITE_IMPUTATION_QUALITY,
        NONPOSITIVE_PREDICTED_VARIANCE, NONFINITE_ASSOCIATION
    }
    public record Options(double maximumMissingFraction, double minimumMeanImputationR2, boolean top1) {
        public Options {
            probabilityCut(maximumMissingFraction, "maximum missing fraction");
            probabilityCut(minimumMeanImputationR2, "minimum mean imputation R2");
        }
        public static Options defaults() { return new Options(0.5, 0.7, false); }
    }
    /**
     * Each array is a defensive copy. NaN selected weights are zeroed exactly as
     * in the source; original positions are explicitly reported. NaN GWAS Z
     * values denote missing source statistics and may be imputed. No other
     * nonfinite input is accepted, and a nonfinite score is never COMPLETE.
     */
    public record Result(Status status, double z, double pValue, double logPValue,
                         double predictedVariance, double[] completedZ, double[] imputationR2,
                         double meanR2AtNonzeroWeights, double[] effectiveWeights,
                         int[] missingWeightsZeroed, int missingZCount, List<String> warnings) {
        public Result {
            completedZ = completedZ.clone(); imputationR2 = imputationR2.clone();
            effectiveWeights = effectiveWeights.clone(); missingWeightsZeroed = missingWeightsZeroed.clone();
            warnings = List.copyOf(warnings);
        }
        @Override public double[] completedZ() { return completedZ.clone(); }
        @Override public double[] imputationR2() { return imputationR2.clone(); }
        @Override public double[] effectiveWeights() { return effectiveWeights.clone(); }
        @Override public int[] missingWeightsZeroed() { return missingWeightsZeroed.clone(); }
        public boolean complete() { return status == Status.COMPLETE; }
    }

    /**
     * Translation of the original allele.qc logical expressions, including
     * uppercase conversion and R NA logic. Null denotes R NA. In particular,
     * the source does not reject every unmatched reference allele pair or
     * identical allele pair. These are source flags, not a stricter harmonizer.
     */
    public static AlleleFlags alleleQc(String a1, String a2, String ref1, String ref2) {
        a1 = upper(a1); a2 = upper(a2); ref1 = upper(ref1); ref2 = upper(ref2);
        Logical ambiguous = or(or(and(eq(a1,"A"),eq(a2,"T")),and(eq(a1,"T"),eq(a2,"A"))),
                               or(and(eq(a1,"C"),eq(a2,"G")),and(eq(a1,"G"),eq(a2,"C"))));
        Logical keep = not(ambiguous);
        if (a1 != null && !base(a1) || a2 != null && !base(a2)) keep = Logical.FALSE;
        Logical flip = or(and(eq(a1,ref2),eq(a2,ref1)),
                          and(eq(a1,complement(ref2)),eq(a2,complement(ref1))));
        return new AlleleFlags(keep, flip);
    }

    /** Full prepared-model path, retaining the source's missingness/filter ordering. */
    public static Result testPrepared(double[] summaryZ, double[] selectedWeights, double[] ld, Options options) {
        if (summaryZ == null || selectedWeights == null || options == null)
            throw new IllegalArgumentException("Prepared Z, selected weights and explicit options are required");
        int n = summaryZ.length;
        if (n < 1 || n > MAX_VARIANTS || selectedWeights.length != n)
            throw new IllegalArgumentException("Prepared variant dimensions/capacity are invalid (maximum " + MAX_VARIANTS + ")");
        PredictedOmics.correlation(ld, n);
        double[] z = summaryZ.clone(), weights = selectedWeights.clone(), r2 = new double[n];
        java.util.Arrays.fill(r2, 1);
        var changed = new ArrayList<Integer>(); var warnings = new ArrayList<String>();
        int missing = 0, nonzero = 0;
        for (int i = 0; i < n; i++) {
            if (Double.isInfinite(z[i]) || Double.isInfinite(weights[i]))
                throw new IllegalArgumentException("Infinite prepared Z/weights are refused; retain the source input");
            if (Double.isNaN(z[i])) missing++;
            if (Double.isNaN(weights[i])) { weights[i] = 0; changed.add(i); }
            if (weights[i] != 0) nonzero++;
        }
        int[] normalized = changed.stream().mapToInt(Integer::intValue).toArray();
        if (normalized.length != 0)
            warnings.add("Original FUSION replaces NA/NaN weights with zero; original zero-based positions are retained in missingWeightsZeroed");
        if (nonzero == 0) return result(Status.ZERO_SELECTED_WEIGHTS, Double.NaN, Double.NaN, z, r2, Double.NaN, weights, normalized, missing, warnings);
        if (options.top1()) {
            int best = 0;
            for (int i = 1; i < n; i++) if (weights[i] * weights[i] > weights[best] * weights[best]) best = i;
            for (int i = 0; i < n; i++) if (i != best) weights[i] = 0;
        }
        double meanR2 = 1;
        if (missing != 0) {
            if (missing == n) return result(Status.NO_OBSERVED_Z, Double.NaN, Double.NaN, z, r2, Double.NaN, weights, normalized, missing, warnings);
            if ((double)missing / n > options.maximumMissingFraction())
                return result(Status.TOO_MANY_MISSING_Z, Double.NaN, Double.NaN, z, r2, Double.NaN, weights, normalized, missing, warnings);
            int[] observed = new int[n - missing], absent = new int[missing];
            for (int i = 0, a = 0, b = 0; i < n; i++) {
                if (Double.isNaN(z[i])) absent[a++] = i; else observed[b++] = i;
            }
            int m = observed.length;
            double[] rawObservedLd = new double[m*m], regularized = new double[m*m];
            for (int i = 0; i < m; i++) for (int j = 0; j < m; j++) {
                rawObservedLd[i*m+j] = ld[observed[i]*n+observed[j]];
                regularized[i*m+j] = rawObservedLd[i*m+j] + (i == j ? IMPUTATION_RIDGE : 0);
            }
            double[] inverse = SummaryMath.inverse(regularized, m);
            for (int at : absent) {
                double[] coefficients = new double[m];
                for (int j = 0; j < m; j++) for (int k = 0; k < m; k++)
                    coefficients[j] += ld[at*n+observed[k]] * inverse[k*m+j];
                double predictedZ = 0;
                for (int j = 0; j < m; j++) predictedZ += coefficients[j] * z[observed[j]];
                r2[at] = SummaryMath.dot(coefficients, SummaryMath.multiply(rawObservedLd, coefficients));
                z[at] = predictedZ / Math.sqrt(r2[at]);
            }
            double sum = 0; int count = 0;
            for (int i = 0; i < n; i++) {
                if (!Double.isFinite(r2[i])) return result(Status.NONFINITE_IMPUTATION_QUALITY, Double.NaN, Double.NaN, z, r2, Double.NaN, weights, normalized, missing, warnings);
                if (weights[i] != 0) { sum += r2[i]; count++; }
            }
            meanR2 = sum / count;
            if (meanR2 < options.minimumMeanImputationR2())
                return result(Status.LOW_IMPUTATION_QUALITY, Double.NaN, Double.NaN, z, r2, meanR2, weights, normalized, missing, warnings);
        }
        double numerator = SummaryMath.dot(weights, z), variance = SummaryMath.dot(weights, SummaryMath.multiply(ld, weights));
        if (!(variance > 0)) return result(Status.NONPOSITIVE_PREDICTED_VARIANCE, Double.NaN, variance, z, r2, meanR2, weights, normalized, missing, warnings);
        double score = numerator / Math.sqrt(variance);
        if (!Double.isFinite(score) || !Double.isFinite(variance))
            return result(Status.NONFINITE_ASSOCIATION, score, variance, z, r2, meanR2, weights, normalized, missing, warnings);
        return result(Status.COMPLETE, score, variance, z, r2, meanR2, weights, normalized, missing, warnings);
    }

    private static Result result(Status status, double score, double variance, double[] z, double[] r2, double mean,
                                 double[] weights, int[] normalized, int missing, List<String> warnings) {
        if (status != Status.COMPLETE) warnings.add("No complete prepared-model result: " + status + "; retain original inputs and diagnostic arrays");
        double p = status == Status.COMPLETE ? SummaryMath.p(score) : Double.NaN;
        double logP = status == Status.COMPLETE ? SummaryMath.logP(score) : Double.NaN;
        return new Result(status, score, p, logP, variance, z, r2, mean, weights, normalized, missing, warnings);
    }
    private static void probabilityCut(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(name + " must be finite in [0,1]");
    }
    private static String upper(String s) { return s == null ? null : s.toUpperCase(Locale.ROOT); }
    private static boolean base(String s) { return s.equals("A") || s.equals("C") || s.equals("G") || s.equals("T"); }
    private static String complement(String s) {
        if (s == null) return null;
        return switch (s) { case "A" -> "T"; case "T" -> "A"; case "C" -> "G"; case "G" -> "C"; default -> s; };
    }
    private static Logical eq(String a, String b) { return a == null || b == null ? Logical.NA : a.equals(b) ? Logical.TRUE : Logical.FALSE; }
    private static Logical not(Logical x) { return x == Logical.NA ? x : x == Logical.TRUE ? Logical.FALSE : Logical.TRUE; }
    private static Logical and(Logical a, Logical b) { return a == Logical.FALSE || b == Logical.FALSE ? Logical.FALSE : a == Logical.NA || b == Logical.NA ? Logical.NA : Logical.TRUE; }
    private static Logical or(Logical a, Logical b) { return a == Logical.TRUE || b == Logical.TRUE ? Logical.TRUE : a == Logical.NA || b == Logical.NA ? Logical.NA : Logical.FALSE; }
}
