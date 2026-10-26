/* Copyright (C) 2026 JLinAlg contributors
 * Core algorithm attribution: FLASH 1.2.11, Copyright (C) 2012 Tanja Magoc
 * and Copyright (C) 2012, 2013, 2014 Eric Biggers.
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.Objects;
import java.util.Optional;

/**
 * Native FLASH 1.2.11 ungapped overlap scoring and quality consensus.
 * Inputs contain uppercase A/C/G/T/N and at most 10,000,000 bases per mate.
 * The second supplied mate is reverse-complemented internally. Unknown N
 * positions are excluded from the callable overlap; maximum overlap caps the
 * scoring denominator rather than excluding longer overlaps. Scores use the
 * original binary32 arithmetic and scan/tie order. Search is quadratic in the
 * worst case, with exact mismatch pruning and first-perfect early termination. This core does
 * not implement FASTQ header rewriting or the complete original application.
 */
public final class FlashReads {
    /** Largest supported length of either supplied short read. */
    public static final int MAXIMUM_READ_LENGTH = 10_000_000;

    private FlashReads() { }

    /** Relative order of the first mate and the reverse-complemented second mate. */
    public enum Orientation {
        /** The first mate begins before the reverse-complemented second mate. */
        INNIE,
        /** The reverse-complemented second mate begins before the first mate. */
        OUTIE
    }

    /**
     * Explicit original scoring and quality choices.
     * @param minimumOverlap positive minimum callable, non-N overlap
     * @param maximumOverlap positive scoring-denominator cap, at least the minimum
     * @param maximumMismatchDensity finite permitted binary32 mismatch density, 0..1
     * @param phredOffset declared input/output quality offset, 33 or 64
     * @param allowOuties whether the second orientation is also searched
     * @param capMismatchQualities whether to use the older capped consensus quality
     */
    public record Settings(int minimumOverlap, int maximumOverlap, float maximumMismatchDensity,
                           int phredOffset, boolean allowOuties, boolean capMismatchQualities) {
        /** Validates the supported parameter contract. */
        public Settings {
            if (minimumOverlap < 1 || maximumOverlap < minimumOverlap)
                throw new IllegalArgumentException("Require positive minimum overlap and maximum overlap >= minimum");
            if (!Float.isFinite(maximumMismatchDensity) || maximumMismatchDensity < 0 || maximumMismatchDensity > 1)
                throw new IllegalArgumentException("Require finite mismatch density 0..1");
            if (phredOffset != 33 && phredOffset != 64)
                throw new IllegalArgumentException("Explicit Phred offset must be 33 or 64");
        }
    }

    /**
     * A successful core merge, without inferred FASTQ identifier changes.
     * @param sequence merged uppercase DNA sequence
     * @param quality merged qualities in the declared original encoding
     * @param orientation chosen original scan orientation
     * @param overlapBegin zero-based overlap start in the first oriented read
     * @param physicalOverlap physical overlap including unknown bases
     * @param callableOverlap overlap after excluding positions containing N
     * @param mismatches mismatches among callable bases
     * @param mismatchDensity binary32 mismatch count divided by the capped denominator
     * @param mismatchQualityScore binary32 summed minimum mismatch qualities divided by that denominator
     */
    public record Merged(String sequence, String quality, Orientation orientation, int overlapBegin,
                         int physicalOverlap, int callableOverlap, int mismatches,
                         float mismatchDensity, float mismatchQualityScore) { }

    private record Read(String sequence, int[] quality, int called) { }
    private record Candidate(Read first, Read second, Orientation orientation, int begin, int physical,
                             int callable, int mismatches, float density, float qualityScore) { }

    /**
     * Finds the original-convention best overlap and returns its consensus.
     * Empty reads are accepted but cannot produce a positive-length overlap.
     * @param firstSequence raw first-mate sequence
     * @param firstQuality raw first-mate qualities
     * @param secondSequence raw second-mate sequence, before reverse complement
     * @param secondQuality raw second-mate qualities, before reversal
     * @param settings explicit supported original choices
     * @return a merge, or an empty optional when no overlap satisfies the cutoff
     */
    public static Optional<Merged> merge(String firstSequence, String firstQuality,
                                         String secondSequence, String secondQuality, Settings settings) {
        Objects.requireNonNull(settings, "settings");
        Read first = read(firstSequence, firstQuality, settings.phredOffset());
        Read rawSecond = read(secondSequence, secondQuality, settings.phredOffset());
        if (Math.min(first.called(), rawSecond.called()) < settings.minimumOverlap()) return Optional.empty();
        int length = rawSecond.sequence().length();
        var reverse = new StringBuilder(length); int[] reversedQuality = new int[length];
        for (int i = length - 1; i >= 0; i--) {
            reverse.append(switch (rawSecond.sequence().charAt(i)) {
                case 'A' -> 'T'; case 'C' -> 'G'; case 'G' -> 'C'; case 'T' -> 'A'; case 'N' -> 'N';
                default -> throw new AssertionError("Validated DNA changed");
            });
            reversedQuality[length - i - 1] = rawSecond.quality()[i];
        }
        Read second = new Read(reverse.toString(), reversedQuality, rawSecond.called()); Candidate best = null;
        float bestDensity = settings.maximumMismatchDensity() + 1.0f, bestQuality = 0.0f;
        search:
        for (int pass = 0; pass < (settings.allowOuties() ? 2 : 1); pass++) {
            Read a = pass == 0 ? first : second, b = pass == 0 ? second : first;
            int start = Math.max(0, a.sequence().length() - b.sequence().length());
            int end = a.sequence().length() - settings.minimumOverlap() + 1;
            for (int begin = start; begin < end; begin++) {
                int physical = a.sequence().length() - begin, callable = physical, mismatches = 0, totalQuality = 0;
                float upperDenominator = Math.min(physical, settings.maximumOverlap());
                boolean pruned = false;
                for (int j = 0; j < physical; j++) {
                    char x = a.sequence().charAt(begin + j), y = b.sequence().charAt(j);
                    if (x == 'N' || y == 'N') callable--;
                    else if (x != y) {
                        mismatches++; totalQuality += Math.min(a.quality()[begin + j], b.quality()[j]);
                        // Further mismatches or excluded N positions cannot reduce this lower bound.
                        if (mismatches / upperDenominator > Math.min(bestDensity, settings.maximumMismatchDensity())) { pruned = true; break; }
                    }
                }
                if (pruned || callable < settings.minimumOverlap()) continue;
                float denominator = Math.min(callable, settings.maximumOverlap());
                float density = mismatches / denominator, qualityScore = totalQuality / denominator;
                if (density <= bestDensity && (density < bestDensity || qualityScore < bestQuality)) {
                    bestDensity = density; bestQuality = qualityScore;
                    best = new Candidate(a, b, pass == 0 ? Orientation.INNIE : Orientation.OUTIE,
                            begin, physical, callable, mismatches, density, qualityScore);
                    // Zero mismatches and zero mismatch quality are globally minimal.
                    // Original scan-order ties keep this first perfect candidate.
                    if (density == 0.0f) break search;
                }
            }
        }
        if (best == null || bestDensity > settings.maximumMismatchDensity()) return Optional.empty();
        Read a = best.first(), b = best.second();
        var sequence = new StringBuilder(a.sequence().length() + b.sequence().length() - best.physical());
        var quality = new StringBuilder(sequence.capacity());
        for (int i = 0; i < best.begin(); i++) { sequence.append(a.sequence().charAt(i)); quality.append((char) (a.quality()[i] + settings.phredOffset())); }
        for (int j = 0; j < best.physical(); j++) {
            char x = a.sequence().charAt(best.begin() + j), y = b.sequence().charAt(j);
            int q1 = a.quality()[best.begin() + j], q2 = b.quality()[j], consensus;
            if (x == y) { sequence.append(x); consensus = Math.max(q1, q2); }
            else {
                sequence.append(q1 > q2 ? x : q1 < q2 ? y : y == 'N' ? x : y);
                consensus = settings.capMismatchQualities() ? Math.min(Math.min(q1, q2), 2) : Math.max(Math.abs(q1 - q2), 2);
            }
            quality.append((char) (consensus + settings.phredOffset()));
        }
        for (int j = best.physical(); j < b.sequence().length(); j++) { sequence.append(b.sequence().charAt(j)); quality.append((char) (b.quality()[j] + settings.phredOffset())); }
        return Optional.of(new Merged(sequence.toString(), quality.toString(), best.orientation(), best.begin(),
                best.physical(), best.callable(), best.mismatches(), best.density(), best.qualityScore()));
    }

    private static Read read(String sequence, String quality, int offset) {
        Objects.requireNonNull(sequence, "sequence"); Objects.requireNonNull(quality, "quality");
        if (sequence.length() != quality.length() || sequence.length() > MAXIMUM_READ_LENGTH)
            throw new IllegalArgumentException("Require equal sequence/quality lengths and at most 10,000,000 bases per mate");
        int[] scores = new int[quality.length()]; int called = 0;
        for (int i = 0; i < sequence.length(); i++) {
            if ("ACGTN".indexOf(sequence.charAt(i)) < 0)
                throw new IllegalArgumentException("Native FLASH core requires uppercase A/C/G/T/N; normalize explicitly when appropriate");
            char q = quality.charAt(i);
            if (q < offset || q > 126) throw new IllegalArgumentException("Quality is outside the declared Phred encoding at base " + (i + 1));
            scores[i] = q - offset;
            if (sequence.charAt(i) != 'N') called++;
        }
        return new Read(sequence, scores, called);
    }
}
