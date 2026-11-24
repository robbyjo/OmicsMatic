/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Native diploid scoring semantics of PRSice 2.3.5, source revision
 * 9dbf55e3f7f3ede2c1b1b6a334c676163e0cbe34. The source reference is
 * BinaryPlink::read_score and Genotype::{read_prs,standardize_prs,calculate_score}.
 * PRSice-2 is copyright Shing Wan Choi and Paul F. O'Reilly, GPL-3.0-or-later.
 *
 * <p>The caller supplies already selected, harmonized variants in scoring order.
 * This class performs no allele matching, filtering, LD estimation, clumping,
 * threshold search, regression, model selection or phenotype interpretation.
 * Genotypes count copies of target BIM A1 (0, 1, 2); -1 denotes a missing call.
 * A flipped variant uses the target BIM A2 as its effective allele. Founder and
 * regression masks must already reflect the intended sample selection.</p>
 *
 * <p>Non-additive missing/centering values deliberately retain the original
 * model-dependent formula; they can differ from a mean of encoded genotypes.
 * Check {@link Result#status()} before admitting scores. Numerical failures and
 * unavailable standardization are returned with their original nonfinite values.</p>
 */
public final class PrsiceHardCallScores {
    private PrsiceHardCallScores() { }

    public enum Model { ADDITIVE, DOMINANT, RECESSIVE, HETEROZYGOUS }
    public enum Missing { MEAN_IMPUTE, SET_ZERO, CENTER }
    public enum Scaling { SUM, AVERAGE, STANDARDIZE, CONTROL_STANDARDIZE }
    public enum Status { COMPLETE, NO_INFORMATIVE_VARIANTS, NONFINITE_SCORE, UNAVAILABLE_STANDARDIZATION }

    /** Immutable result; array accessors return defensive copies. */
    public static final class Result {
        private final Status status;
        private final double[] scores, sums, missingModelValues;
        private final long[] alleleCounts;
        private final boolean[] includedVariants;
        private final double standardizationMean, standardizationSd;
        private final int standardizationSamples;
        private final List<String> warnings;

        private Result(Status status, double[] scores, double[] sums, long[] counts,
                       double[] missingValues, boolean[] included, double mean,
                       double sd, int samples, List<String> warnings) {
            this.status = status;
            this.scores = scores.clone(); this.sums = sums.clone();
            this.alleleCounts = counts.clone(); this.missingModelValues = missingValues.clone();
            this.includedVariants = included.clone();
            this.standardizationMean = mean; this.standardizationSd = sd;
            this.standardizationSamples = samples; this.warnings = List.copyOf(warnings);
        }
        public Status status() { return status; }
        public double[] scores() { return scores.clone(); }
        public double[] rawSums() { return sums.clone(); }
        public long[] alleleCounts() { return alleleCounts.clone(); }
        public double[] missingModelValues() { return missingModelValues.clone(); }
        public boolean[] includedVariants() { return includedVariants.clone(); }
        /** NaN when standardization was not requested or was unavailable. */
        public double standardizationMean() { return standardizationMean; }
        /** Sample SD, with denominator n-1; NaN when unused or unavailable. */
        public double standardizationSd() { return standardizationSd; }
        public int standardizationSamples() { return standardizationSamples; }
        public List<String> warnings() { return warnings; }
    }

    /**
     * Scores a variants-by-samples hard-call matrix. All variants use diploid
     * encoding, including non-additive models. AVERAGE divides the accumulated
     * score by the accumulated allele count (two per contributing variant).
     * SET_ZERO omits missing calls from that denominator. The other missing
     * modes count two alleles even for missing calls. Standardization applies
     * to average scores, using eligible regression samples and optionally only
     * controls; the resulting transform is applied to every supplied sample.
     *
     * @throws IllegalArgumentException for malformed dimensions, invalid calls,
     *         nonfinite effects, absent founders or null options
     */
    public static Result score(int[][] targetA1Copies, double[] effects,
                               boolean[] flipped, boolean[] founders,
                               boolean[] regressionEligible, boolean[] controls,
                               Model model, Missing missing, Scaling scaling) {
        require(targetA1Copies != null && effects != null && flipped != null
                && founders != null && regressionEligible != null && controls != null
                && model != null && missing != null && scaling != null, "Null scoring input or option");
        int variants = targetA1Copies.length, samples = founders.length;
        require(variants > 0 && samples > 0 && effects.length == variants && flipped.length == variants,
                "Require nonempty variants/samples and one effect/orientation per variant");
        require(regressionEligible.length == samples && controls.length == samples,
                "Sample mask lengths differ");
        boolean hasFounder = false;
        for (boolean founder : founders) hasFounder |= founder;
        require(hasFounder, "At least one explicitly selected founder is required");
        // Validate every row before computing any result, including rows whose
        // founder genotypes will later make them uninformative.
        for (int v = 0; v < variants; v++) {
            require(targetA1Copies[v] != null && targetA1Copies[v].length == samples,
                    "Genotype row length differs at variant " + v);
            require(Double.isFinite(effects[v]), "Nonfinite effect at variant " + v);
            for (int g : targetA1Copies[v]) require(g >= -1 && g <= 2, "Calls must be -1, 0, 1 or 2");
        }
        double[] sums = new double[samples], missingValues = new double[variants];
        Arrays.fill(missingValues, Double.NaN);
        long[] counts = new long[samples]; boolean[] included = new boolean[variants];
        var warnings = new ArrayList<String>(); int includedCount = 0;
        if (model != Model.ADDITIVE && missing != Missing.SET_ZERO)
            warnings.add("PRSICE SOURCE CONVENTION: non-additive missing/centering values use the original model-weight formula; they are not a mean of encoded genotypes.");
        for (int v = 0; v < variants; v++) {
            double w0 = weight(model, flipped[v] ? 2 : 0);
            double w1 = weight(model, 1), w2 = weight(model, flipped[v] ? 0 : 2);
            int called = 0; double weightedFounderCount = 0;
            for (int i = 0; i < samples; i++) if (founders[i] && targetA1Copies[v][i] >= 0) {
                int g = targetA1Copies[v][i]; called++;
                // Upstream homcom counts target-A1 homozygotes while the
                // read_prs weight array addresses the complemented BED code.
                weightedFounderCount += g == 2 ? w0 : g == 1 ? w1 : w2;
            }
            if (called == 0) {
                warnings.add("ALL FOUNDER CALLS MISSING: variant " + v + " contributes no score or denominator; its missing-model value remains NaN.");
                continue;
            }
            included[v] = true; includedCount++;
            double value = 2.0 * (1.0 - weightedFounderCount / (2.0 * called));
            missingValues[v] = value;
            double adjustment = missing == Missing.CENTER ? 2.0 * effects[v] * (value / 2.0) : 0.0;
            double imputed = missing == Missing.MEAN_IMPUTE ? 2.0 * effects[v] * (value / 2.0) : 0.0;
            for (int i = 0; i < samples; i++) {
                int g = targetA1Copies[v][i];
                double contribution;
                if (g < 0) {
                    contribution = imputed;
                    if (missing != Missing.SET_ZERO) counts[i] += 2;
                } else {
                    double w = g == 0 ? w0 : g == 1 ? w1 : w2;
                    contribution = w * effects[v] - adjustment; counts[i] += 2;
                }
                // Upstream initializes the first retained contribution rather
                // than adding it to +0.0; preserve its signed zero as well.
                if (includedCount == 1) sums[i] = contribution;
                else sums[i] += contribution;
            }
        }
        double[] scores = new double[samples];
        for (int i = 0; i < samples; i++) scores[i] = counts[i] == 0 ? 0.0 : sums[i] / counts[i];
        boolean standardize = scaling == Scaling.STANDARDIZE || scaling == Scaling.CONTROL_STANDARDIZE;
        double mean = Double.NaN, sd = Double.NaN; int n = 0;
        if (standardize) {
            mean = 0; double m2 = 0;
            for (int i = 0; i < samples; i++) if (regressionEligible[i]
                    && (scaling != Scaling.CONTROL_STANDARDIZE || controls[i])) {
                double delta = scores[i] - mean, deltaN = delta / (++n);
                m2 += delta * deltaN * (n - 1); mean += deltaN;
            }
            sd = Math.sqrt(m2 / (n - 1.0));
            if (n == 0) { mean = Double.NaN; sd = Double.NaN; }
            for (int i = 0; i < samples; i++) scores[i] = (scores[i] - mean) / sd;
        } else if (scaling == Scaling.SUM) scores = sums.clone();
        Status status = Status.COMPLETE;
        if (includedCount == 0) {
            status = Status.NO_INFORMATIVE_VARIANTS;
            warnings.add("NO COMPLETE RESULT: every variant lacks called founder genotypes. Review the explicit founder mask and input calls.");
        } else if (standardize && (n < 2 || !Double.isFinite(sd) || !(sd > 0))) {
            status = Status.UNAVAILABLE_STANDARDIZATION;
            warnings.add("NO COMPLETE RESULT: standardization requires at least two selected samples and positive finite sample SD. Review the explicit regression/control masks or retain the failure; scores are not silently repaired.");
        } else {
            for (double value : scores) if (!Double.isFinite(value)) {
                status = Status.NONFINITE_SCORE;
                warnings.add("NO COMPLETE RESULT: a score is nonfinite. Preserve raw sums, review effects and input scale, and retry with an explicit justified input change.");
                break;
            }
        }
        return new Result(status, scores, sums, counts, missingValues, included, mean, sd, n, warnings);
    }

    private static double weight(Model model, int copies) {
        return switch (model) {
            case ADDITIVE -> copies;
            case DOMINANT -> copies == 0 ? 0 : 1;
            case RECESSIVE -> copies == 2 ? 1 : 0;
            case HETEROZYGOUS -> copies == 1 ? 1 : 0;
        };
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
