/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.jlinalg.sequence.SalmonQuantification;

/** Complete query-grouped transcriptome SAM/BAM census; no genomic projection or alignment fitting. */
final class SalmonAlignments {
    private SalmonAlignments() { }
    record Limits(long encodedBytes, long decodedBytes, long records, long groups, int hitsPerGroup, int references,
                  int readLength, int lineCharacters, int metadataBytes, long nameBytes, long retainedBytes) {
        Limits { if (encodedBytes < 1 || decodedBytes < 1 || records < 1 || groups < 1 || hitsPerGroup < 1 || references < 1 || readLength < 1 || lineCharacters < 1 || metadataBytes < 1 || nameBytes < 1 || retainedBytes < 262144) throw new IllegalArgumentException("Positive complete Salmon alignment capacities are required"); }
    }
    record Context(Path source, NextflowFiles.Pin pin, SalmonQuantRecipe.Format format, SalmonQuantRecipe.Layout layout,
                   Limits limits, long records, long fragments, long mappedFragments, long unmappedFragments,
                   long secondaryRecords, long supplementaryRecords, long decodedBytes, long estimatedRetainedBytes) {
        void verify(Runnable check) throws IOException { require(pin.equals(NextflowFiles.pin(source, limits.encodedBytes(), check)), "Original Salmon transcriptome alignment changed"); }
    }
    static Context inspect(Path path, SalmonQuantRecipe.Format format, SalmonQuantRecipe.Layout layout, List<SalmonQuantification.Target> targets, Limits limits, Runnable check) throws IOException {
        NextflowFiles.Pin pin = NextflowFiles.pin(path, limits.encodedBytes(), check);
        require(!targets.isEmpty() && targets.size() <= limits.references(), "Complete Salmon alignment reference population exceeds capacity");
        Physical physical = format == SalmonQuantRecipe.Format.BAM ? bam(path, targets, limits, check) : sam(path, limits, check);
        long records = 0, groups = 0, mappedGroups = 0, secondary = 0, supplementary = 0, hits = 0, retained = 262144, nameBytes = 0;
        var seen = new HashSet<String>(); String current = null; boolean mapped = false, pendingPair = false; SAMRecord previousMate = null;
        try (var reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).enable(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX).open(path)) {
            require(format == SalmonQuantRecipe.Format.SAM ? reader.type() == SamReader.Type.SAM_TYPE : reader.type() == SamReader.Type.BAM_TYPE || reader.type() == SamReader.Type.BAM_CSI_TYPE, "Declared Salmon SAM/BAM format differs; CRAM and automatic format replacement are unsupported");
            var dictionary = reader.getFileHeader().getSequenceDictionary().getSequences();
            require(dictionary.size() == targets.size(), "Salmon transcriptome alignment dictionary omits or adds protected reference names");
            for (int i = 0; i < dictionary.size(); i++) require(dictionary.get(i).getSequenceName().equals(targets.get(i).name()) && dictionary.get(i).getSequenceLength() == targets.get(i).length(), "Salmon transcriptome dictionary name/order/length differs from the complete protected FASTA; no genomic projection is inferred");
            try (var iterator = reader.iterator()) {
                while (iterator.hasNext()) {
                    check.run(); SAMRecord row = iterator.next(); records = Math.addExact(records, 1); require(records <= limits.records(), "Complete Salmon alignment-record capacity exceeded");
                    String name = row.getReadName(); require(name != null && !name.isEmpty() && !name.equals("*") && name.length() <= limits.lineCharacters(), "Salmon alignment needs complete bounded query identities");
                    if (!name.equals(current)) {
                        require(!pendingPair, "Incomplete adjacent Salmon alignment mate pair at query-group boundary"); if (current != null && mapped) mappedGroups++;
                        require(seen.add(name), "Salmon transcriptome alignments must keep every query in one contiguous group; group explicitly into a NEW file");
                        groups = Math.addExact(groups, 1); require(groups <= limits.groups(), "Complete Salmon alignment-fragment capacity exceeded");
                        nameBytes = Math.addExact(nameBytes, name.getBytes(StandardCharsets.UTF_8).length); retained = Math.addExact(retained, Math.addExact(512, Math.multiplyExact(16L, name.length())));
                        require(nameBytes <= limits.nameBytes() && retained <= limits.retainedBytes(), "Complete Salmon query-identity metadata exceeds capacity");
                        current = name; mapped = false; hits = 0;
                    }
                    require(row.getReadPairedFlag() == (layout == SalmonQuantRecipe.Layout.PAIRED), "Salmon alignment paired flags differ from the declared library layout");
                    require(row.getReadLength() > 0 && row.getReadLength() <= limits.readLength(), "Salmon alignment contract requires complete bounded sequence fields on every record");
                    require(row.getBaseQualities().length == 0 || row.getBaseQualities().length == row.getReadLength(), "Salmon alignment sequence/quality lengths differ");
                    if (!row.getReadUnmappedFlag()) {
                        mapped = true; require(row.getReferenceIndex() >= 0 && row.getReferenceIndex() < targets.size() && row.getAlignmentStart() > 0
                            && row.getAlignmentEnd() >= row.getAlignmentStart() && row.getAlignmentEnd() <= targets.get(row.getReferenceIndex()).length(), "Mapped Salmon alignment lies outside the protected transcript reference");
                        require(row.getCigar().getCigarElements().stream().noneMatch(e -> e.getOperator() == CigarOperator.N), "Spliced/genomic CIGAR N is not a transcriptome alignment; supply explicitly projected transcriptome SAM/BAM");
                    }
                    if (row.isSecondaryAlignment()) secondary++; if (row.getSupplementaryAlignmentFlag()) supplementary++;
                    if (layout == SalmonQuantRecipe.Layout.PAIRED) {
                        require(row.getFirstOfPairFlag() != row.getSecondOfPairFlag(), "Salmon alignment mate flags are missing or contradictory");
                        if (!pendingPair) { require(row.getFirstOfPairFlag(), "Salmon alignment contract requires adjacent first-then-second records for every hit"); previousMate = row; pendingPair = true; }
                        else {
                            require(row.getSecondOfPairFlag() && previousMate != null && name.equals(previousMate.getReadName()) && row.isSecondaryAlignment() == previousMate.isSecondaryAlignment()
                                && row.getSupplementaryAlignmentFlag() == previousMate.getSupplementaryAlignmentFlag(), "Salmon adjacent alignment mate roles/status differ");
                            require(previousMate.getMateUnmappedFlag() == row.getReadUnmappedFlag() && row.getMateUnmappedFlag() == previousMate.getReadUnmappedFlag(), "Salmon mate unmapped flags are inconsistent");
                            if (!row.getReadUnmappedFlag() && !previousMate.getReadUnmappedFlag()) require(previousMate.getMateReferenceIndex().equals(row.getReferenceIndex()) && row.getMateReferenceIndex().equals(previousMate.getReferenceIndex())
                                && previousMate.getMateAlignmentStart() == row.getAlignmentStart() && row.getMateAlignmentStart() == previousMate.getAlignmentStart(), "Salmon mate reference/position fields are inconsistent");
                            pendingPair = false; previousMate = null; hits++;
                        }
                    } else hits++;
                    require(hits <= limits.hitsPerGroup(), "Complete Salmon alignment hits per query exceed capacity");
                }
            }
        } catch (htsjdk.samtools.SAMException malformed) { throw new IOException("Invalid complete Salmon SAM/BAM input; no result admitted", malformed); }
        require(!pendingPair && records > 0 && groups > 0 && records == physical.records(), "Incomplete/empty Salmon alignment population or physical-record census mismatch");
        if (mapped) mappedGroups++;
        var result = new Context(path, pin, format, layout, limits, records, groups, mappedGroups, groups - mappedGroups, secondary, supplementary, physical.bytes(), retained);
        result.verify(check); return result;
    }
    private record Physical(long records, long bytes) { }
    private static Physical sam(Path path, Limits limits, Runnable check) throws IOException {
        // Validate complete UTF-8, framing and capacities before HTSJDK can
        // allocate a large header/record. It later reads the unchanged source.
        var state = new SamPhysical(limits);
        try (var raw = Files.newInputStream(path); var input = new Bounded(raw, limits.decodedBytes(), check);
             var decoded = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            char[] buffer = new char[8192];
            for (int n = decoded.read(buffer); n >= 0; n = decoded.read(buffer)) {
                check.run(); for (int i = 0; i < n; i++) {
                    char c = buffer[i]; state.lineBytes += c < 128 ? 1 : c < 2048 ? 2 : Character.isHighSurrogate(c) ? 4 : Character.isLowSurrogate(c) ? 0 : 3;
                    if (c == '\n') state.end();
                    else { require(c >= 32 || c == '\t' || c == '\r', "Control-character Salmon SAM input"); state.characters++; require(state.characters <= limits.lineCharacters(), "Complete Salmon SAM line-character capacity exceeded"); if (state.prefix.length() < 4) state.prefix.append(c); }
                }
            }
            if (state.characters > 0) state.end(); return new Physical(state.records, input.count);
        }
    }
    private static final class SamPhysical {
        final Limits limits; final StringBuilder prefix = new StringBuilder(4); int characters; long lineBytes, headerBytes, records; boolean sawRecords;
        SamPhysical(Limits limits) { this.limits = limits; }
        void end() throws IOException {
            require(characters > 0, "Empty Salmon SAM physical line");
            boolean header = java.util.Set.of("@HD\t", "@SQ\t", "@RG\t", "@PG\t", "@CO\t").contains(prefix.toString());
            if (header) { require(!sawRecords, "Salmon SAM header follows alignment records"); headerBytes = Math.addExact(headerBytes, lineBytes); require(headerBytes <= limits.metadataBytes(), "Complete Salmon SAM header bytes exceed capacity"); }
            else { sawRecords = true; records++; require(records <= limits.records(), "Complete physical Salmon SAM record capacity exceeded"); }
            characters = 0; lineBytes = 0; prefix.setLength(0);
        }
    }
    private static Physical bam(Path path, List<SalmonQuantification.Target> targets, Limits limits, Runnable check) throws IOException {
        try (var raw = Files.newInputStream(path); var gzip = new RepeatGzip(raw, check); var input = new Bounded(gzip, limits.decodedBytes(), check)) {
            require(java.util.Arrays.equals(input.bytes(4), new byte[]{'B', 'A', 'M', 1}), "Declared BAM omits the complete BAM magic");
            int text = input.integer(); require(text >= 0 && text <= limits.metadataBytes(), "Complete BAM header-text capacity exceeded"); input.skipComplete(text);
            int references = input.integer(); require(references == targets.size() && references <= limits.references(), "Complete BAM reference dictionary population differs");
            long header = text;
            for (var target : targets) {
                int length = input.integer(); require(length > 1 && length <= limits.lineCharacters(), "Invalid/out-of-capacity BAM reference name");
                header = Math.addExact(header, length + 8L); require(header <= limits.metadataBytes(), "Complete BAM dictionary metadata exceeds capacity"); byte[] name = input.bytes(length);
                require(name[length - 1] == 0 && target.name().equals(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(name, 0, length - 1)).toString())
                    && input.integer() == target.length(), "Complete BAM transcript reference name/order/length differs");
            }
            long records = 0;
            for (int first = input.read(); first >= 0; first = input.read()) {
                int block = first | input.readRequired() << 8 | input.readRequired() << 16 | input.readRequired() << 24;
                require(block >= 32 && block <= Math.addExact(Math.multiplyExact(2L, limits.readLength()), limits.lineCharacters()), "Invalid/out-of-capacity complete BAM record bytes");
                byte[] core = input.bytes(32); int sequence = integer(core, 16), flagsCigar = integer(core, 12), nameLength = core[8] & 255;
                require(sequence > 0 && sequence <= limits.readLength() && nameLength > 0, "Invalid/out-of-capacity BAM sequence/name fields");
                long minimum = 32L + nameLength + 4L * (flagsCigar & 65535) + (sequence + 1L) / 2 + sequence;
                require(minimum <= block, "Truncated internal BAM variable fields"); input.skipComplete(block - 32L); records++;
                require(records <= limits.records(), "Complete physical BAM record capacity exceeded");
            }
            return new Physical(records, input.count);
        }
    }
    private static int integer(byte[] source, int offset) { return (source[offset] & 255) | (source[offset + 1] & 255) << 8 | (source[offset + 2] & 255) << 16 | source[offset + 3] << 24; }
    private static final class Bounded extends InputStream {
        final InputStream source; final long maximum; final Runnable check; long count;
        Bounded(InputStream source, long maximum, Runnable check) { this.source = source; this.maximum = maximum; this.check = check; }
        @Override public int read() throws IOException { check.run(); int value = source.read(); if (value >= 0) { count++; require(count <= maximum, "Complete decoded alignment bytes exceed capacity"); } return value; }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException { check.run(); int n = source.read(buffer, offset, length); if (n > 0) { count = Math.addExact(count, n); require(count <= maximum, "Complete decoded alignment bytes exceed capacity"); } return n; }
        int readRequired() throws IOException { int value = read(); if (value < 0) throw new java.io.EOFException("Truncated complete BAM framing"); return value; }
        int integer() throws IOException { return readRequired() | readRequired() << 8 | readRequired() << 16 | readRequired() << 24; }
        byte[] bytes(int n) throws IOException { byte[] result = readNBytes(n); require(result.length == n, "Truncated complete BAM field"); return result; }
        void skipComplete(long count) throws IOException { byte[] buffer = new byte[8192]; long remaining = count; while (remaining > 0) { int n = read(buffer, 0, (int) Math.min(buffer.length, remaining)); require(n > 0, "Truncated complete BAM body"); remaining -= n; } }
        @Override public void close() throws IOException { source.close(); }
    }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
