/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.coloc;

import java.util.Arrays;
import java.util.List;

/** Pairwise signal and variant-level posteriors from SuSiE colocalization. */
public final class ColocSusieResult {
    private final List<String> commonVariants;
    private final List<ColocSignalPair> signalPairs;
    private final double[] sharedVariantPosterior;
    private final ColocOptions options;
    private final int skippedSignalPairs;

    ColocSusieResult(
            List<String> commonVariants,
            List<ColocSignalPair> signalPairs,
            double[] sharedVariantPosterior,
            ColocOptions options,
            int skippedSignalPairs) {
        this.commonVariants = List.copyOf(commonVariants);
        this.signalPairs = List.copyOf(signalPairs);
        this.sharedVariantPosterior = sharedVariantPosterior.clone();
        this.options = options;
        this.skippedSignalPairs = skippedSignalPairs;
    }

    public List<String> commonVariants() { return commonVariants; }
    public List<ColocSignalPair> signalPairs() { return signalPairs; }

    /**
     * Row-major signal-pair by common-variant posterior conditional on H4.
     */
    public double[] sharedVariantPosterior() {
        return sharedVariantPosterior.clone();
    }

    /** Returns the H4-conditional variant posterior, or zeros if H4 has no support. */
    public double[] sharedVariantPosterior(int signalPair) {
        if (signalPair < 0 || signalPair >= signalPairs.size()) {
            throw new IndexOutOfBoundsException("signal pair index is invalid");
        }
        int start = signalPair * commonVariants.size();
        return Arrays.copyOfRange(sharedVariantPosterior,
            start, start + commonVariants.size());
    }

    public ColocOptions options() { return options; }
    public int skippedSignalPairs() { return skippedSignalPairs; }

    /** Whether the mathematical H4-conditional distribution has shared support. */
    public boolean isSharedVariantPosteriorDefined(int signalPair) {
        if (signalPair < 0 || signalPair >= signalPairs.size()) {
            throw new IndexOutOfBoundsException("signal pair index is invalid");
        }
        int start = signalPair * commonVariants.size();
        for (int j = 0; j < commonVariants.size(); j++) {
            if (sharedVariantPosterior[start + j] > 0.0) return true;
        }
        return false;
    }

    /** Scientific scope and undefined-result warnings; callers should display and retain them. */
    public List<String> warnings() {
        var warnings = new java.util.ArrayList<String>();
        warnings.add("NATIVE_STABLE_COLOC_VARIANT: H3 uses distinct-configuration "
            + "enumeration, which can retain support rounded to zero by coloc 6.0.3's "
            + "log-difference. This step does not validate association fits, LD, "
            + "credible-set selection or historical whole-package/paper parity.");
        if (options.rawTrait1PriorWeights() != null || options.rawTrait2PriorWeights() != null) {
            warnings.add("PRIOR_WEIGHTED_CONDITIONAL_SNP_POSTERIOR: this native "
                + "variant includes prior weights; coloc 6.0.3 returns an unweighted "
                + "conditional SNP column even when regional hypotheses use weights.");
        }
        if (commonVariants.size() == 1 && !signalPairs.isEmpty()) {
            warnings.add("SINGLE_VARIANT_NATIVE_MODEL: this result retains a "
                + "normalized hypothesis distribution. The audited coloc 6.0.3 "
                + "coloc.bf_bf single-variant path returned altered, nonnormalized hypotheses; "
                + "literal source-output parity is not claimed.");
        }
        if (signalPairs.isEmpty()) {
            warnings.add("NO_RETAINED_SIGNAL_PAIRS: no hypothesis posteriors are "
                + "reported; this is not evidence for H0 or absence of colocalization.");
        }
        for (int pair = 0; pair < signalPairs.size(); pair++) {
            if (!isSharedVariantPosteriorDefined(pair)) {
                warnings.add("UNDEFINED_CONDITIONAL_SNP_POSTERIOR_PAIR_" + (pair + 1)
                    + ": H4 has no shared support. The library's retained zero "
                    + "vector is an undefined-value marker, not a probability "
                    + "distribution; do not report it as a SNP posterior.");
            }
        }
        return List.copyOf(warnings);
    }
}
