/*
 * Derived numerical methods: Copyright (c) 2009 The Broad Institute, MIT License.
 * API and checks: Copyright (C) 2026 JLinAlg contributors, GPL-3.0-or-later.
 * See /org/jlinalg/omics/picard2231/NOTICE.txt and MIT-Picard.txt.
 */
package org.jlinalg.sequence;

import java.util.OptionalLong;

/** Source-specific Picard 2.23.1 duplication fractions and library-complexity estimates.
 * This does not identify duplicates, infer optical duplicates, or run Preseq.
 */
public final class PicardLibraryComplexity {
    private PicardLibraryComplexity() { }

    /** Complete source counts after Picard divides paired record counters by two. */
    public record Counts(long unpairedExamined, long pairedExamined, long unpairedDuplicates,
                         long pairedDuplicates, long opticalPairedDuplicates) {
        public Counts {
            if (unpairedExamined < 0 || pairedExamined < 0 || unpairedDuplicates < 0 || pairedDuplicates < 0
                    || opticalPairedDuplicates < 0 || unpairedDuplicates > unpairedExamined
                    || pairedDuplicates > pairedExamined || opticalPairedDuplicates > pairedDuplicates)
                throw new IllegalArgumentException("Require complete nonnegative source counts and optical <= paired duplicates <= examined");
            // Refuse integer capacity overflow instead of changing Picard's arithmetic order.
            Math.addExact(unpairedExamined, Math.multiplyExact(pairedExamined, 2));
            Math.addExact(unpairedDuplicates, Math.multiplyExact(pairedDuplicates, 2));
        }
    }
    public record Result(double duplicationFraction, OptionalLong estimatedLibrarySize, double[] roi) {
        public Result { roi = roi.clone(); }
        @Override public double[] roi() { return roi.clone(); }
        /** Source zero-size estimates can produce NaN ROI; they are retained, not repaired. */
        public boolean roiAssessable() {
            if (roi.length != 100) return false;
            for (double value : roi) if (!Double.isFinite(value)) return false;
            return true;
        }
    }
    public static Result calculate(Counts counts) {
        double fraction = counts.unpairedExamined() + counts.pairedExamined() != 0
            ? (counts.unpairedDuplicates() + counts.pairedDuplicates() * 2) / (double) (counts.unpairedExamined() + counts.pairedExamined() * 2)
            : 0.0;
        OptionalLong size = estimateLibrarySize(counts.pairedExamined() - counts.opticalPairedDuplicates(), counts.pairedExamined() - counts.pairedDuplicates());
        double[] roi = size.isPresent() ? new double[100] : new double[0];
        for (int i = 0; i < roi.length; i++) roi[i] = estimateRoi(size.getAsLong(), i + 1.0, counts.pairedExamined(), counts.pairedExamined() - counts.pairedDuplicates());
        return new Result(fraction, size, roi);
    }
    /** Exact source bisection and long conversion. No-duplicate/empty inputs have no estimate.
     * For zero unique pairs the source returns zero; no finite positive estimate is invented.
     */
    public static OptionalLong estimateLibrarySize(long readPairs, long uniqueReadPairs) {
        if (readPairs < 0 || uniqueReadPairs < 0 || uniqueReadPairs > readPairs)
            throw new IllegalArgumentException("Require 0 <= unique pairs <= non-optical read pairs");
        long duplicates = readPairs - uniqueReadPairs;
        if (readPairs > 0 && duplicates > 0) {
            double low = 1.0, high = 100.0;
            if (uniqueReadPairs >= readPairs || f(low * uniqueReadPairs, uniqueReadPairs, readPairs) < 0)
                throw new IllegalStateException("Invalid values for pairs and unique pairs: " + readPairs + ", " + uniqueReadPairs);
            while (f(high * uniqueReadPairs, uniqueReadPairs, readPairs) > 0) high *= 10.0;
            for (int i = 0; i < 40; i++) {
                double midpoint = (low + high) / 2.0, value = f(midpoint * uniqueReadPairs, uniqueReadPairs, readPairs);
                if (value == 0) break;
                else if (value > 0) low = midpoint;
                else if (value < 0) high = midpoint;
            }
            return OptionalLong.of((long) (uniqueReadPairs * (low + high) / 2.0));
        }
        return OptionalLong.empty();
    }
    private static double f(double size, double unique, double pairs) { return unique / size - 1 + Math.exp(-pairs / size); }
    /** Original source expression, including its zero-denominator and overflow-to-infinity behavior. */
    public static double estimateRoi(long estimatedLibrarySize, double multiplier, long pairs, long uniquePairs) {
        if (estimatedLibrarySize < 0 || !Double.isFinite(multiplier) || multiplier < 0 || pairs < 0 || uniquePairs < 0 || uniquePairs > pairs)
            throw new IllegalArgumentException("Require nonnegative original ROI inputs and a finite nonnegative multiplier");
        return estimatedLibrarySize * (1 - Math.exp(-(multiplier * pairs) / estimatedLibrarySize)) / uniquePairs;
    }
}
