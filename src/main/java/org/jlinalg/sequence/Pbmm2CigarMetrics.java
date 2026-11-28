/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

/**
 * Original pbcopper CIGAR base/event arithmetic and pbmm2 concordance.
 *
 * <p>The public pbcopper 2.3.0 CigarOpsCalculator is the algorithm reference.
 * Later bundled dependency editions require independent original-binary checks;
 * this class does not establish their complete source equivalence. As in that
 * reference, {@code M} contributes matches. This convention does not determine
 * nucleotide identity from the reference sequence. Introns, clips and padding
 * contribute no edit-distance bases. Adjacent operations remain distinct events.
 * Empty/unaligned arithmetic preserves NaN; callers must reject an unassessable
 * mapped result rather than fill missing values. No CIGAR is repaired or merged.
 */
public final class Pbmm2CigarMetrics {
    private Pbmm2CigarMetrics() { }

    public record Metrics(int matchBases, int mismatchBases, int insertionBases,
                          int deletionBases, int matchEvents, int mismatchEvents,
                          int insertionEvents, int deletionEvents, int alignedQueryBases,
                          int editDistance, double identity, double gapCompressedIdentity,
                          double concordance) {
        public boolean assessable() {
            return alignedQueryBases > 0 && Double.isFinite(identity)
                    && Double.isFinite(gapCompressedIdentity) && Double.isFinite(concordance);
        }
        public float identityTag() { return (float) identity; }
        public float gapCompressedIdentityTag() { return (float) gapCompressedIdentity; }
        public float concordanceTag() { return (float) concordance; }
    }

    /**
     * Compute metrics from the unchanged operation sequence. The aligned query
     * span is {@code match + mismatch + insertion}, excluding clips and introns.
     * This matches the original mapped-record span for the qualified controls;
     * it does not certify arbitrary BAM clipping/coordinate metadata.
     *
     * @throws IllegalArgumentException for malformed/unknown operations
     * @throws ArithmeticException if source signed-32-bit counters overflow
     */
    public static Metrics compute(String cigar) {
        if (cigar == null) throw new IllegalArgumentException("CIGAR is required");
        int match = 0, mismatch = 0, insertion = 0, deletion = 0;
        int matchEvents = 0, mismatchEvents = 0, insertionEvents = 0, deletionEvents = 0;
        for (int offset = 0; offset < cigar.length();) {
            if (cigar.charAt(offset) < '0' || cigar.charAt(offset) > '9')
                throw new IllegalArgumentException("CIGAR operation needs its original count at " + offset);
            int count = 0;
            do {
                count = Math.addExact(Math.multiplyExact(count, 10), cigar.charAt(offset++) - '0');
            } while (offset < cigar.length() && cigar.charAt(offset) >= '0' && cigar.charAt(offset) <= '9');
            if (offset == cigar.length()) throw new IllegalArgumentException("CIGAR count has no operation");
            switch (cigar.charAt(offset++)) {
                case 'M', '=' -> { match = Math.addExact(match, count); matchEvents = Math.incrementExact(matchEvents); }
                case 'X' -> { mismatch = Math.addExact(mismatch, count); mismatchEvents = Math.incrementExact(mismatchEvents); }
                case 'I' -> { insertion = Math.addExact(insertion, count); insertionEvents = Math.incrementExact(insertionEvents); }
                case 'D' -> { deletion = Math.addExact(deletion, count); deletionEvents = Math.incrementExact(deletionEvents); }
                case 'N', 'S', 'H', 'P' -> { /* Original operation is deliberately excluded from these counters. */ }
                default -> throw new IllegalArgumentException("Unknown original CIGAR operation");
            }
        }
        int aligned = Math.addExact(Math.addExact(match, insertion), mismatch);
        int edits = Math.addExact(Math.addExact(deletion, insertion), mismatch);
        int identityDenominator = Math.addExact(Math.addExact(match, mismatch), Math.addExact(deletion, insertion));
        int gapDenominator = Math.addExact(Math.addExact(match, mismatch), Math.addExact(deletionEvents, insertionEvents));
        double identity = 100.0 * match / identityDenominator;
        double gapIdentity = 100.0 * match / gapDenominator;
        double concordance = Math.max(0.0, Math.min(100.0, 100 * (1.0 - 1.0 * edits / aligned)));
        return new Metrics(match, mismatch, insertion, deletion,
                matchEvents, mismatchEvents, insertionEvents, deletionEvents,
                aligned, edits, identity, gapIdentity, concordance);
    }
}
