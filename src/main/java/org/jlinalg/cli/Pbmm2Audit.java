/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import htsjdk.samtools.util.BlockCompressedInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.Pbmm2CigarMetrics;
import static org.jlinalg.cli.NextflowFiles.*;

/** Full output BAM traversal and original CIGAR/tag contracts, without reference-fit claims. */
final class Pbmm2Audit {
    private Pbmm2Audit() { }
    static Map<String, Object> run(Path output, Pbmm2Request.Request request) throws IOException {
        var inventory = Pbmm2Capture.census(output, request.process().workspaceBytes(), request.process().fileBytes(), request.process().fileCount());
        if (request.operation().equals("INDEX")) {
            Path index = output.resolve("result.mmi");
            if (!inventory.files().containsKey("result.mmi") || Files.size(index) < 4) throw new IOException("Original complete reference index was not produced");
            byte[] magic = new byte[4]; try (var in = Files.newInputStream(index)) { if (in.read(magic) != 4) throw new IOException("Truncated original reference index"); }
            if (!Arrays.equals(magic, new byte[]{'M', 'M', 'I', 2})) throw new IOException("Original minimap2 index magic differs");
            return Map.of("operation", "INDEX", "index", inventory.files().get("result.mmi"), "reference_alignment_accuracy_certified", false);
        }
        var files = new TreeMap<String, Object>(); long total = 0, mapped = 0, unmapped = 0, tags = 0;
        for (String name : inventory.files().keySet()) {
            if (!name.endsWith(".bam")) continue;
            Path path = output.resolve(name);
            if (BlockCompressedInputStream.checkTermination(path) != BlockCompressedInputStream.FileTermination.HAS_TERMINATOR_BLOCK) throw new IOException("Missing complete original BAM terminator: " + name);
            long fileTotal = 0, fileMapped = 0, fileUnmapped = 0, fileTags = 0;
            // Literal SAM fields remain available. We check the named contracts below rather than asking
            // HTSJDK to reinterpret original PacBio conventions or rewrite identifiers/tags.
            try (var bam = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(path)) {
                if (bam.type() != SamReader.Type.BAM_TYPE) throw new IOException("Expected literal BAM output: " + name);
                var programs = bam.getFileHeader().getProgramRecords();
                if (programs.isEmpty()) throw new IOException("Missing original pbmm2 output provider");
                var provider = programs.get(programs.size() - 1);
                if (!"pbmm2".equals(provider.getProgramName()) || !(request.version() + " (commit v" + request.version() + ")").equals(provider.getProgramVersion())) throw new IOException("Literal original output provider differs: " + name);
                for (var record : bam) {
                    total = Math.addExact(total, 1); fileTotal++;
                    if (total > request.records()) throw new IOException("Whole output record capacity exceeded; no BAM subset");
                    if (record.getReadUnmappedFlag()) { unmapped++; fileUnmapped++; continue; }
                    var metric = Pbmm2CigarMetrics.compute(record.getCigarString());
                    if (!metric.assessable()) throw new IOException("Unassessable mapped CIGAR at " + record.getReadName());
                    Object nm = record.getAttribute("NM");
                    if (!(nm instanceof Byte || nm instanceof Short || nm instanceof Integer || nm instanceof Long) || ((Number) nm).longValue() != metric.editDistance()) throw new IOException("Original NM/CIGAR differs at " + record.getReadName());
                    if (record.getReferenceIndex() == null || record.getReferenceIndex() < 0 || record.getReferenceIndex() >= bam.getFileHeader().getSequenceDictionary().size()) throw new IOException("Mapped reference index differs at " + record.getReadName());
                    var reference = bam.getFileHeader().getSequenceDictionary().getSequence(record.getReferenceIndex());
                    if (record.getAlignmentStart() < 1 || record.getAlignmentEnd() > reference.getSequenceLength() || record.getAlignmentEnd() < record.getAlignmentStart()) throw new IOException("Mapped reference geometry differs at " + record.getReadName());
                    if (record.getReadLength() != 0 && record.getCigar().getReadLength() != record.getReadLength()) throw new IOException("Literal CIGAR/read length differs at " + record.getReadName());
                    for (String tag : List.of("mi", "mg", "mc")) {
                        Object value = record.getAttribute(tag); if (value == null) continue;
                        float expected = tag.equals("mi") ? metric.identityTag() : tag.equals("mg") ? metric.gapCompressedIdentityTag() : metric.concordanceTag();
                        if (!(value instanceof Float actual) || Float.floatToRawIntBits(actual) != Float.floatToRawIntBits(expected)) throw new IOException("Original " + tag + "/CIGAR float bits differ at " + record.getReadName());
                        tags++; fileTags++;
                    }
                    mapped++; fileMapped++;
                }
            } catch (RuntimeException invalid) { throw new IOException("Complete original BAM cannot be assessed: " + name, invalid); }
            files.put(name, Map.of("records", fileTotal, "mapped", fileMapped, "unmapped", fileUnmapped, "compared_float_tags", fileTags));
        }
        if (files.isEmpty()) throw new IOException("Original ALIGN produced no assessable BAM output; no alignment result admitted");
        return Map.of("operation", "ALIGN", "files", files, "records", total, "mapped", mapped, "unmapped", unmapped, "compared_float_tags", tags,
                "empty_or_no_mapped_records", mapped == 0, "reference_alignment_accuracy_certified", false, "all_nonmetric_tags_independently_certified", false);
    }
}
