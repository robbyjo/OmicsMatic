/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.workflow.NfcoreChipseq121Frip;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;

/** Complete original-library BAM identity and FRiP audit; no alignment filtering or tag substitution. */
final class NfcoreChipseq121BamAudit {
    private NfcoreChipseq121BamAudit() { }
    record Result(String libraryId, long readGroupCount, NfcoreChipseq121Frip.Summary frip) { }
    static Result inspect(Path bam, NfcoreChipseq121Mappings.Library library, Map<String, Long> referenceLengths,
                          List<GenomicIntervals.Interval> peaks, long maximumRecords) throws IOException {
        if (referenceLengths.isEmpty()) throw new IOException("Require the exact declared reference dictionary");
        for (var peak : peaks) {
            Long length = referenceLengths.get(peak.chromosome());
            if (length == null || peak.end() > length)
                throw new IOException("Peak coordinates/reference labels differ from the declared assembly; no aliases inferred");
        }
        var expectedGroups = new HashSet<String>();
        for (var technical : library.technicalSamples()) expectedGroups.add(technical.sampleId());
        var counter = new NfcoreChipseq121Frip.Counter(peaks, maximumRecords);
        try (SamReader reader = SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS)
                .validationStringency(ValidationStringency.STRICT).open(bam)) {
            if (reader.type() != SamReader.Type.BAM_TYPE) throw new IOException("This original pipeline audit requires BAM, with no automatic CRAM reference retrieval");
            var observedReferences = new java.util.LinkedHashMap<String, Long>();
            for (var sequence : reader.getFileHeader().getSequenceDictionary().getSequences())
                if (observedReferences.putIfAbsent(sequence.getSequenceName(), (long) sequence.getSequenceLength()) != null)
                    throw new IOException("Duplicate BAM reference label");
            if (!observedReferences.equals(referenceLengths)) throw new IOException("BAM and declared reference labels/lengths differ; no aliases inferred");
            Set<String> groups = new HashSet<>();
            for (var group : reader.getFileHeader().getReadGroups()) {
                if (!expectedGroups.contains(group.getId()) || !library.id().equals(group.getSample()) || !groups.add(group.getId()))
                    throw new IOException("Original BAM read-group/sample identities disagree with the complete checked mapping");
            }
            if (!groups.equals(expectedGroups)) throw new IOException("Original BAM header omits a declared technical read group");
            for (var record : reader) {
                var group = record.getReadGroup();
                if (group == null || !groups.contains(group.getId())) throw new IOException("Alignment lacks its declared original technical read group");
                GenomicIntervals.Interval interval = null;
                if (!record.getReadUnmappedFlag()) {
                    String contig = record.getContig(); Long length = referenceLengths.get(contig);
                    long start0 = (long) record.getAlignmentStart() - 1, end0 = record.getAlignmentEnd();
                    if (length == null || start0 < 0 || end0 <= start0 || end0 > length)
                        throw new IOException("Mapped BAM outer span is outside the exact declared reference");
                    interval = new GenomicIntervals.Interval(contig, start0, end0, record.getReadName(), record.getReadNegativeStrandFlag() ? "-" : "+");
                }
                counter.accept(new NfcoreChipseq121Frip.Alignment(interval, record.getReadFailsVendorQualityCheckFlag()));
            }
            return new Result(library.id(), groups.size(), counter.summary());
        } catch (htsjdk.samtools.SAMException | IllegalArgumentException malformed) {
            throw new IOException("Complete original ChIP-seq BAM audit failed: " + malformed.getMessage(), malformed);
        }
    }
}
