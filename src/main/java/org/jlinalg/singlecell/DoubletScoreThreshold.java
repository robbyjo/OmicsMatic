/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.singlecell;

import java.util.Objects;

/**
 * Classifies caller-supplied scores with a strict numeric cutoff.
 *
 * <p>The HICAT convenience method applies the factual {@code score > 0.3}
 * rule reported in PMC10719114 (DOI 10.1038/s41586-023-06812-z). Score
 * calculation remains a separate operation. This independently written
 * selector contains no source-derived HICAT scoring implementation.</p>
 */
public final class DoubletScoreThreshold {
    /** Strict removal cutoff reported for the HICAT use in PMC10719114. */
    public static final double HICAT_109_REPORTED_CUTOFF = 0.3;

    /** Classification of one supplied score. */
    public enum Decision { RETAIN, REMOVE, UNKNOWN }

    /** Original zero-based row positions, in their original order. */
    public record Selection(int[] retainedRows, int[] removedRows, int[] unknownRows) {
        public Selection {
            retainedRows = retainedRows.clone();
            removedRows = removedRows.clone();
            unknownRows = unknownRows.clone();
        }

        @Override public int[] retainedRows() { return retainedRows.clone(); }
        @Override public int[] removedRows() { return removedRows.clone(); }
        @Override public int[] unknownRows() { return unknownRows.clone(); }
    }

    private DoubletScoreThreshold() { }

    /**
     * Applies numeric comparison without transforming the supplied value.
     * Equality retains the row. A NaN score or cutoff produces UNKNOWN;
     * infinities follow their ordinary numeric ordering.
     *
     * @param score supplied score
     * @param cutoff strict removal cutoff
     * @return the comparison decision
     */
    public static Decision classify(double score, double cutoff) {
        if (score > cutoff) return Decision.REMOVE;
        if (score <= cutoff) return Decision.RETAIN;
        return Decision.UNKNOWN;
    }

    /**
     * Selects row positions from a stable copy; input values remain unchanged.
     * Unknown rows are reported separately from retained and removed rows.
     *
     * @param scores supplied scores
     * @param cutoff strict removal cutoff
     * @return all row positions partitioned by decision
     */
    public static Selection select(double[] scores, double cutoff) {
        double[] values = Objects.requireNonNull(scores, "scores").clone();
        int retained = 0, removed = 0, unknown = 0;
        for (double value : values) {
            switch (classify(value, cutoff)) {
                case RETAIN -> retained++;
                case REMOVE -> removed++;
                case UNKNOWN -> unknown++;
            }
        }
        int[] kept = new int[retained], discarded = new int[removed], unresolved = new int[unknown];
        retained = removed = unknown = 0;
        for (int row = 0; row < values.length; row++) {
            switch (classify(values[row], cutoff)) {
                case RETAIN -> kept[retained++] = row;
                case REMOVE -> discarded[removed++] = row;
                case UNKNOWN -> unresolved[unknown++] = row;
            }
        }
        return new Selection(kept, discarded, unresolved);
    }

    /**
     * Applies the paper's strict 0.3 cutoff to already computed scores.
     *
     * @param scores caller-supplied doublet scores
     * @return retained, removed and unknown original row positions
     */
    public static Selection hicat109ReportedCutoff(double[] scores) {
        return select(scores, HICAT_109_REPORTED_CUTOFF);
    }
}
