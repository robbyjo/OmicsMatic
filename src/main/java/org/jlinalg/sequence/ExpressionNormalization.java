/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

/** Explicit length-based count normalization; no dispersion, transcript inference or DE model. */
public final class ExpressionNormalization {
    private ExpressionNormalization() { }
    /** Gene TPM from counts and genomic union lengths. The supplied feature universe defines the denominator. */
    public static double[] tpm(double[] counts, long[] lengths) {
        validate(counts, lengths);
        double[] rates = new double[counts.length]; double sum = 0;
        for (int i = 0; i < rates.length; i++) { rates[i] = counts[i] / lengths[i]; sum += rates[i]; }
        if (!Double.isFinite(sum) || sum <= 0) throw new IllegalArgumentException("TPM requires a positive finite total length-normalized count");
        for (int i = 0; i < rates.length; i++) rates[i] = rates[i] / sum * 1_000_000;
        return rates;
    }
    /** RPKM, or FPKM when the caller supplies fragment counts and a fragment denominator. */
    public static double[] perKilobaseMillion(double[] counts, long[] lengths, double libraryUnits) {
        validate(counts, lengths);
        if (!Double.isFinite(libraryUnits) || libraryUnits <= 0) throw new IllegalArgumentException("An explicit positive finite library-unit denominator is required");
        double[] output = new double[counts.length];
        for (int i = 0; i < output.length; i++) {
            output[i] = counts[i] / lengths[i] / libraryUnits * 1_000_000_000;
            if (!Double.isFinite(output[i])) throw new IllegalArgumentException("Normalized value overflow");
        }
        return output;
    }
    private static void validate(double[] counts, long[] lengths) {
        if (counts.length == 0 || counts.length != lengths.length) throw new IllegalArgumentException("Counts and lengths must have the same positive size");
        for (int i = 0; i < counts.length; i++) if (!Double.isFinite(counts[i]) || counts[i] < 0 || lengths[i] <= 0) throw new IllegalArgumentException("Require finite nonnegative counts and positive lengths");
    }
}
