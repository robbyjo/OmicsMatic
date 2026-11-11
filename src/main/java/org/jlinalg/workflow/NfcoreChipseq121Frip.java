/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.jlinalg.genetics.GenomicIntervals;

/** Original ChIP-seq1.2.1 FRiP arithmetic: BEDTools2.29.2 -bed -c -f0.20,
 * divided by the QC-passing mapped count selected from SAMtools1.10 flagstat.
 * Overlapping peak records contribute separately, exactly as intersect -c.
 */
public final class NfcoreChipseq121Frip {
    private NfcoreChipseq121Frip() { }
    /** Null interval explicitly denotes an unmapped record; no flag/ID inference is performed. */
    public record Alignment(GenomicIntervals.Interval interval, boolean vendorQcFailed) { }
    public record Summary(long records, long mapped, long qcPassingMapped, long peakIntersections,
                          long mappedRecordsInPeaks, long qcFailedMapped) {
        public OptionalDouble fraction() { return qcPassingMapped == 0 ? OptionalDouble.empty()
            : OptionalDouble.of((double) peakIntersections / qcPassingMapped); }
        public Map<String, Object> document() {
            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("alignment_records", records); result.put("mapped_records", mapped);
            result.put("qc_passing_mapped_denominator", qcPassingMapped);
            result.put("qc_failed_mapped_records", qcFailedMapped);
            result.put("bedtools_count_sum_numerator", peakIntersections);
            result.put("mapped_records_with_any_peak", mappedRecordsInPeaks);
            result.put("frip", fraction().isPresent() ? fraction().getAsDouble() : null);
            result.put("assessment", qcPassingMapped == 0 ? "UNASSESSABLE_NO_QC_PASSING_MAPPED_DENOMINATOR" : "DEFINED_SOURCE_RATIO");
            result.put("overlapping_peak_rows_collapsed", false); result.put("fraction_clamped_to_one", false);
            return java.util.Collections.unmodifiableMap(result);
        }
    }
    /** Streaming counter; the caller retains all source records and supplies complete physical alignment geometry. */
    public static final class Counter {
        private final GenomicIntervals.Index peaks;
        private final long maximumRecords;
        private long records, mapped, qcPassingMapped, intersections, inPeaks, qcFailedMapped;
        public Counter(List<GenomicIntervals.Interval> peaks, long maximumRecords) {
            if (maximumRecords < 1 || maximumRecords > 1000000000L) throw new IllegalArgumentException("Require bounded positive alignment capacity");
            this.peaks = new GenomicIntervals.Index(peaks); this.maximumRecords = maximumRecords;
        }
        /** Original unstranded outer-span rule; omitted -F leaves its original threshold at zero. */
        public void accept(Alignment alignment) throws IOException {
            if (++records > maximumRecords) throw new IOException("Complete FRiP alignment capacity exceeded");
            if (alignment.interval() == null) return;
            mapped++;
            if (alignment.vendorQcFailed()) qcFailedMapped++; else qcPassingMapped++;
            long matches = 0;
            for (GenomicIntervals.Interval peak : peaks.overlaps(alignment.interval(), false)) {
                long overlap = GenomicIntervals.overlap(alignment.interval(), peak);
                float a = (float) overlap / (float) alignment.interval().length();
                if (a >= 0.20f) matches++;
            }
            intersections = Math.addExact(intersections, matches);
            if (matches > 0) inPeaks++;
        }
        public Summary summary() { return new Summary(records, mapped, qcPassingMapped, intersections, inPeaks, qcFailedMapped); }
    }
}
