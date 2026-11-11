/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordCoordinateComparator;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.workflow.NfcoreChipseq121Frip;
import org.jlinalg.workflow.NfcoreChipseq200Mappings;

/** Full v2 BAM/reference and distinct technical LB/SM/ID namespace consistency. */
final class NfcoreChipseq200BamAudit {
    private NfcoreChipseq200BamAudit() { }
    static NfcoreChipseq121Frip.Summary inspect(Path bam, List<NfcoreChipseq200Mappings.Read> technical,
            Map<String, Long> reference, List<GenomicIntervals.Interval> peaks, String center, long maximumRecords) throws IOException {
        if (technical.isEmpty() || reference.isEmpty()) throw new IOException("Require the complete original library/read-group/reference mapping");
        var expected = new LinkedHashMap<String, NfcoreChipseq200Mappings.Read>(); for (var row : technical) if (expected.putIfAbsent(row.readGroupId(), row) != null) throw new IOException("Repeated expected original read-group ID");
        for (var peak : peaks) if (!reference.containsKey(peak.chromosome()) || peak.end() > reference.get(peak.chromosome())) throw new IOException("Peak/reference geometry differs; no contig alias inferred");
        var counter = new NfcoreChipseq121Frip.Counter(peaks, maximumRecords);
        try (var reader = SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(bam)) {
            if (reader.type() != SamReader.Type.BAM_TYPE || reader.getFileHeader().getSortOrder() != htsjdk.samtools.SAMFileHeader.SortOrder.coordinate) throw new IOException("Require original coordinate-sorted BAM; no CRAM/reference retrieval inferred");
            var observed = new LinkedHashMap<String, Long>(); for (var row : reader.getFileHeader().getSequenceDictionary().getSequences()) if (observed.putIfAbsent(row.getSequenceName(), (long) row.getSequenceLength()) != null) throw new IOException("Duplicate original BAM reference label"); if (!observed.equals(reference)) throw new IOException("Complete original BAM/reference dictionary differs");
            var groups = new HashSet<String>();
            for (var row : reader.getFileHeader().getReadGroups()) {
                var source = expected.get(row.getId()); if (source == null || !groups.add(row.getId()) || !source.readGroupSample().equals(row.getSample()) || !source.readGroupLibrary().equals(row.getLibrary()) || !"ILLUMINA".equals(row.getPlatform()) || !"1".equals(row.getPlatformUnit()) || !java.util.Objects.equals(center, row.getSequencingCenter())) throw new IOException("Original BAM RG ID/LB/SM/PL/PU/CN differs from complete v2 checked mapping: " + row.getId());
            }
            if (!groups.equals(expected.keySet())) throw new IOException("Original BAM omits technical read groups"); var order = new SAMRecordCoordinateComparator(); SAMRecord previous = null;
            for (var record : reader) {
                if (previous != null && order.fileOrderCompare(previous, record) > 0) throw new IOException("Complete original BAM is not in declared coordinate order"); previous = record;
                var group = record.getReadGroup(); if (group == null || !groups.contains(group.getId())) throw new IOException("Original record has no declared technical read group");
                if (record.getReadPairedFlag() == expected.get(group.getId()).singleEnd()) throw new IOException("Original BAM read mode differs from checked technical input");
                GenomicIntervals.Interval interval = null;
                if (!record.getReadUnmappedFlag()) { String chromosome = record.getContig(); Long length = reference.get(chromosome); long start = (long) record.getAlignmentStart() - 1, end = record.getAlignmentEnd(); if (length == null || start < 0 || end <= start || end > length) throw new IOException("Original mapped BAM outer span is outside exact reference"); interval = new GenomicIntervals.Interval(chromosome, start, end, record.getReadName(), record.getReadNegativeStrandFlag() ? "-" : "+"); }
                counter.accept(new NfcoreChipseq121Frip.Alignment(interval, record.getReadFailsVendorQualityCheckFlag()));
            }
            return counter.summary();
        } catch (htsjdk.samtools.SAMException | IllegalArgumentException malformed) { throw new IOException("Complete original v2 BAM is unassessable; flags/IDs were not repaired", malformed); }
    }
}
