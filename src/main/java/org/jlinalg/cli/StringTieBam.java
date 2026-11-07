/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Complete physical/genomic BAM intake for StringTie; no filtering or abundance inference. */
final class StringTieBam {
    private StringTieBam() { }
    enum MissingNh { ERROR, ORIGINAL_ONE }
    enum MissingXs { ERROR, KEEP_UNRESOLVED }
    enum SortHeader { REQUIRE_COORDINATE, VERIFY_RECORDS }
    record Policies(MissingNh missingNh, MissingXs missingXs, SortHeader sortHeader) {
        Policies { Objects.requireNonNull(missingNh); Objects.requireNonNull(missingXs); Objects.requireNonNull(sortHeader); }
    }
    record Limits(long encodedBytes, long decodedBytes, long records, int headerBytes, int recordBytes,
                  int references, int readLength, int cigarOperations, int tags, int arrayItems) {
        Limits {
            if (encodedBytes < 1 || encodedBytes > 1099511627776L || decodedBytes < 1 || decodedBytes > 1099511627776L
                    || records < 1 || records > 1000000000 || headerBytes < 1 || headerBytes > 67108864
                    || recordBytes < 32 || recordBytes > 67108864 || references < 1 || references > 1000000
                    || readLength < 1 || readLength > 10000000 || cigarOperations < 1 || cigarOperations > 65535
                    || tags < 1 || tags > 4096 || arrayItems < 1 || arrayItems > 10000000)
                throw new IllegalArgumentException("Invalid complete StringTie BAM capacities");
        }
        static Limits defaults() { return new Limits(8589934592L, 34359738368L, 10000000, 16777216, 16777216, 100000, 1000000, 65535, 4096, 1000000); }
    }
    record Reference(int id, String name, int length) { }
    record Junction(int start1, int end1) { }
    record Row(long ordinal, String query, int flags, int referenceId, String reference, int start1, long end1,
               int mateReferenceId, int mateStart1, int templateLength, int mappingQuality, String cigar, int readLength, Integer reportedNh, int effectiveNh,
               Character xs, String xsTagKind, Character ts, String tsTagKind, boolean unresolvedSpliceStrand, long matchedBases, long deletedBases,
               long skippedBases, List<Junction> junctions) {
        Row { junctions = List.copyOf(junctions); }
    }
    @FunctionalInterface interface Sink { void record(Row row) throws IOException; }
    record Report(Path source, NextflowFiles.Identity identity, Limits limits, Policies policies,
                  List<Reference> dictionary, String headerText, long records, long mapped, long spliced,
                  long unresolvedSpliced, long missingNh, long zeroNh, long assumedNh, long nonCharacterXs, long nonCharacterTs, long missingSequenceUnmapped,
                  long matchedBases, long deletedBases, long skippedBases, long decodedBytes, long bgzfBlocks,
                  Map<Integer, Long> flagCounts) {
        Report { dictionary = List.copyOf(dictionary); flagCounts = Map.copyOf(flagCounts); }
        void verify(Runnable check) throws IOException { require(identity.equals(NextflowFiles.identity(source, limits.encodedBytes(), check)), "Original StringTie BAM changed after complete intake"); }
        Map<String, Object> document() {
            var out = new LinkedHashMap<String, Object>();
            out.put("source", source.toString()); out.put("source_pin", Map.of("bytes", identity.pin().bytes(), "sha256", identity.pin().sha256()));
            out.put("dictionary", dictionary.stream().map(r -> Map.of("id", r.id(), "name", r.name(), "length", r.length())).toList());
            out.put("original_header_text", headerText); out.put("records", records); out.put("mapped_records", mapped); out.put("unmapped_records", records - mapped);
            out.put("spliced_records", spliced); out.put("unresolved_spliced_records", unresolvedSpliced); out.put("missing_mapped_NH", missingNh); out.put("zero_mapped_NH", zeroNh); out.put("source_default_one_NH", assumedNh);
            out.put("non_A_XS_records", nonCharacterXs); out.put("non_A_ts_records", nonCharacterTs);
            out.put("unmapped_missing_sequence_records", missingSequenceUnmapped); out.put("CIGAR_M_EQ_X_reference_bases", matchedBases); out.put("deleted_reference_bases", deletedBases); out.put("skipped_reference_bases", skippedBases);
            out.put("decoded_BAM_bytes", decodedBytes); out.put("BGZF_blocks_including_EOF", bgzfBlocks);
            var flags = new TreeMap<String, Long>(); flagCounts.forEach((k, v) -> flags.put(Integer.toString(k), v)); out.put("raw_flag_histogram", flags);
            out.put("missing_NH_policy", policies.missingNh().name()); out.put("missing_XS_policy", policies.missingXs().name()); out.put("sort_header_policy", policies.sortHeader().name());
            out.put("source_filtering_performed", false); out.put("mate_reciprocity_certified", false); out.put("reference_assembly_authenticated", false); out.put("StringTie_usable_read_population_inferred", false);
            out.put("scope", "Complete raw alignment geometry and declared tags only. All flags and records remain retained; no uniqueness, primary-only, duplicate, QC or strand filter is inferred. ts tags remain separate from XS and require exact edition-specific interpretation. Missing/unknown XS is never filled from alignment orientation.");
            return out;
        }
    }

    static Report inspect(Path path, Map<String, Long> suppliedReference, Policies policies, Limits limits, Sink sink, Runnable check) throws IOException {
        Objects.requireNonNull(policies); Objects.requireNonNull(limits); Objects.requireNonNull(sink); Objects.requireNonNull(check);
        var reference = Map.copyOf(suppliedReference); require(!reference.isEmpty() && reference.size() <= limits.references(), "Require the complete bounded genomic chromosome-size dictionary");
        for (var e : reference.entrySet()) require(!e.getKey().isEmpty() && e.getValue() > 0 && e.getValue() <= Integer.MAX_VALUE, "StringTie BAM reference requires positive signed32 lengths without aliases");
        var identity = NextflowFiles.identity(path, limits.encodedBytes(), check);
        Physical physical = physical(path, reference, limits, check);
        long count = 0, mapped = 0, spliced = 0, unresolved = 0, missingNh = 0, zeroNh = 0, assumed = 0, nonCharacterXs = 0, nonCharacterTs = 0, missingSequence = 0;
        long matched = 0, deleted = 0, skipped = 0; int lastReference = -1, lastStart = -1; boolean unplaced = false;
        var flags = new TreeMap<Integer, Long>();
        try (var reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT)
                .enable(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX).open(path)) {
            require(reader.type() == SamReader.Type.BAM_TYPE || reader.type() == SamReader.Type.BAM_CSI_TYPE, "Require actual BAM; no SAM/CRAM or format replacement is inferred");
            require(policies.sortHeader() != SortHeader.REQUIRE_COORDINATE || reader.getFileHeader().getSortOrder() == SAMFileHeader.SortOrder.coordinate,
                    "Require declared coordinate sort or explicit VERIFY_RECORDS policy; no sorting occurs");
            var dictionary = reader.getFileHeader().getSequenceDictionary().getSequences();
            require(dictionary.size() == physical.dictionary().size(), "Decoded and physical BAM dictionaries differ");
            for (int i = 0; i < dictionary.size(); i++) require(dictionary.get(i).getSequenceName().equals(physical.dictionary().get(i).name())
                    && dictionary.get(i).getSequenceLength() == physical.dictionary().get(i).length(), "Decoded BAM dictionary order/name/length differs from physical dictionary");
            try (var rows = reader.iterator()) {
                while (rows.hasNext()) {
                    check.run(); var row = rows.next(); count++; require(count <= limits.records(), "Complete BAM record capacity exceeded");
                    var errors = row.isValid(); require(errors == null || errors.isEmpty(), "Strict BAM record validation failed at ordinal " + count);
                    row.getAttributes(); // Decode all bounded auxiliary fields, including those not used by this workflow.
                    int ref = row.getReferenceIndex(), start = row.getAlignmentStart();
                    if (ref >= 0) {
                        require(!unplaced && ref < dictionary.size() && start >= 0 && (ref > lastReference || ref == lastReference && start >= lastStart), "BAM record order is not coordinate sorted");
                        lastReference = ref; lastStart = start;
                    } else { require(ref == -1 && row.getReadUnmappedFlag(), "Unplaced BAM record has invalid mapped identity"); unplaced = true; }
                    flags.merge(row.getFlags(), 1L, Long::sum);
                    Integer nh = null; Object rawNh = row.getAttribute("NH");
                    if (rawNh != null) {
                        require(rawNh instanceof Integer || rawNh instanceof Long, "NH must be a positive original integer tag");
                        long number = ((Number)rawNh).longValue(); require(number >= 0 && number <= Integer.MAX_VALUE, "NH is outside the nonnegative signed32 source domain"); nh = (int)number;
                    }
                    Object rawXs = row.getAttribute("XS"), rawTs = row.getAttribute("ts");
                    Character xs = rawXs instanceof Character c ? c : null, ts = rawTs instanceof Character c ? c : null;
                    if (rawXs != null && xs == null) nonCharacterXs++;
                    if (rawTs != null && ts == null) nonCharacterTs++;
                    var junctions = new ArrayList<Junction>(); long end = 0, m = 0, d = 0, n = 0; int effectiveNh = 0; boolean unresolvedRow = false;
                    if (!row.getReadUnmappedFlag()) {
                        mapped++; require(ref >= 0 && start > 0 && row.getReadLength() > 0 && row.getReadLength() <= limits.readLength(), "Mapped BAM requires complete bounded sequence and positive reference coordinates");
                        require(row.getCigarLength() > 0 && row.getCigarLength() <= limits.cigarOperations(), "Complete original CIGAR operation capacity exceeded");
                        long position = start, readBases = 0;
                        for (var element : row.getCigar().getCigarElements()) {
                            int length = element.getLength(); require(length > 0, "Zero-length CIGAR operation"); CigarOperator operator = element.getOperator();
                            if (operator.consumesReadBases()) readBases = Math.addExact(readBases, length);
                            if (operator == CigarOperator.M || operator == CigarOperator.EQ || operator == CigarOperator.X) m = Math.addExact(m, length);
                            if (operator == CigarOperator.D) d = Math.addExact(d, length);
                            if (operator == CigarOperator.N) {
                                require(position <= Integer.MAX_VALUE && position + length - 1 <= Integer.MAX_VALUE, "Splice coordinates overflow signed32");
                                junctions.add(new Junction((int)position, (int)(position + length - 1))); n = Math.addExact(n, length);
                            }
                            if (operator.consumesReferenceBases()) position = Math.addExact(position, length);
                        }
                        end = position - 1; require(readBases == row.getReadLength() && m > 0 && end >= start && end <= physical.dictionary().get(ref).length(), "BAM CIGAR/sequence/reference geometry differs or exceeds the exact reference");
                        if (nh == null || nh == 0) {
                            if (nh == null) missingNh++; else zeroNh++;
                            require(policies.missingNh() == MissingNh.ORIGINAL_ONE, "Mapped NH is absent/zero; choose explicit original-one policy or provide authentic original NH tags"); assumed++; effectiveNh = 1;
                        }
                        else effectiveNh = nh;
                        if (!junctions.isEmpty()) {
                            spliced++; unresolvedRow = xs == null || xs != '+' && xs != '-';
                            if (unresolvedRow) { unresolved++; require(policies.missingXs() == MissingXs.KEEP_UNRESOLVED, "Spliced XS is missing/unknown; retain unresolved explicitly or supply authentic source strand tags"); }
                        }
                    } else if (row.getReadLength() == 0) missingSequence++;
                    matched = Math.addExact(matched, m); deleted = Math.addExact(deleted, d); skipped = Math.addExact(skipped, n);
                    sink.record(new Row(count, row.getReadName(), row.getFlags(), ref, row.getReferenceName(), start, end,
                            row.getMateReferenceIndex(), row.getMateAlignmentStart(), row.getInferredInsertSize(), row.getMappingQuality(), row.getCigarString(), row.getReadLength(), nh, effectiveNh, xs, tagKind(rawXs), ts, tagKind(rawTs), unresolvedRow, m, d, n, junctions));
                }
            }
        } catch (htsjdk.samtools.SAMException | IllegalArgumentException malformed) {
            throw new IOException("Invalid complete original StringTie BAM; no intake result admitted", malformed);
        }
        require(count == physical.records(), "Physical and decoded complete BAM record populations differ");
        var result = new Report(path, identity, limits, policies, physical.dictionary(), physical.header(), count, mapped, spliced, unresolved,
                missingNh, zeroNh, assumed, nonCharacterXs, nonCharacterTs, missingSequence, matched, deleted, skipped, physical.decodedBytes(), physical.blocks(), flags);
        result.verify(check); return result;
    }
    private static String tagKind(Object value) {
        if (value == null) return "ABSENT";
        if (value instanceof Character) return "A";
        if (value instanceof String) return "Z";
        if (value instanceof Integer || value instanceof Long) return "INTEGER";
        if (value instanceof Float || value instanceof Double) return "FLOAT";
        return "OTHER_RETAINED_IN_RAW_BAM";
    }
    private record Physical(List<Reference> dictionary, String header, long records, long decodedBytes, long blocks) { }
    private static Physical physical(Path path, Map<String, Long> reference, Limits limits, Runnable check) throws IOException {
        try (var input = new Blocks(path, limits.decodedBytes(), check)) {
            require(Arrays.equals(input.bytes(4), new byte[]{'B','A','M',1}), "Require complete BAM magic");
            int headerBytes = input.integer(); require(headerBytes >= 0 && headerBytes <= limits.headerBytes(), "Complete BAM header capacity exceeded");
            String header = NextflowFiles.utf8(input.bytes(headerBytes)); require(header.indexOf(0) < 0, "NUL in BAM header text");
            int count = input.integer(); require(count == reference.size() && count <= limits.references(), "Physical BAM reference population differs from the supplied complete dictionary");
            var dictionary = new ArrayList<Reference>(); var names = new HashSet<String>(); long metadata = headerBytes;
            for (int i = 0; i < count; i++) {
                int length = input.integer(); require(length > 1 && length <= limits.headerBytes(), "Invalid complete BAM reference-name length");
                metadata = Math.addExact(metadata, length + 8L); require(metadata <= limits.headerBytes(), "Complete BAM dictionary metadata capacity exceeded");
                byte[] raw = input.bytes(length); require(raw[length - 1] == 0, "BAM reference name lacks NUL terminator");
                String name = NextflowFiles.utf8(Arrays.copyOf(raw, length - 1)); require(name.indexOf(0) < 0 && names.add(name), "Duplicate/embedded-NUL BAM reference identity");
                int size = input.integer(); require(size > 0 && reference.get(name) != null && reference.get(name) == size, "BAM reference name/length differs; no chromosome aliases or clipping");
                dictionary.add(new Reference(i, name, size));
            }
            headerDictionary(header, dictionary);
            long records = 0;
            for (int first = input.read(); first >= 0; first = input.read()) {
                check.run(); int length = first | input.required() << 8 | input.required() << 16 | input.required() << 24;
                require(length >= 32 && length <= limits.recordBytes(), "Complete BAM record byte capacity exceeded");
                byte[] body = input.bytes(length); int read = integer(body, 16), cigar = integer(body, 12) & 65535, name = body[8] & 255;
                require(read >= 0 && read <= limits.readLength() && cigar <= limits.cigarOperations() && name > 0, "Complete BAM core read/CIGAR/name capacity exceeded");
                long end = 32L + name + 4L * cigar + (read + 1L) / 2 + read; require(end <= length, "Truncated internal BAM variable fields");
                require(body[32 + name - 1] == 0, "BAM query identity lacks terminal NUL");
                for (int i = 32; i < 32 + name - 1; i++) require(body[i] >= 33 && body[i] <= 126, "Invalid original BAM query-name byte");
                auxiliary(body, (int)end, limits); records++; require(records <= limits.records(), "Complete physical BAM record capacity exceeded");
            }
            return new Physical(List.copyOf(dictionary), header, records, input.decoded, input.blocks);
        }
    }
    private static void headerDictionary(String header, List<Reference> dictionary) throws IOException {
        int index = 0;
        for (String line : header.split("\n", -1)) if (line.startsWith("@SQ\t")) {
            String name = null, length = null;
            for (String field : line.replaceFirst("\r$", "").split("\t")) {
                if (field.startsWith("SN:")) { require(name == null, "Duplicate header SN field"); name = field.substring(3); }
                if (field.startsWith("LN:")) { require(length == null, "Duplicate header LN field"); length = field.substring(3); }
            }
            require(index < dictionary.size() && name != null && length != null && length.matches("[1-9][0-9]{0,9}"), "Incomplete/extra BAM text dictionary row");
            long size = Long.parseLong(length); var row = dictionary.get(index++);
            require(name.equals(row.name()) && size == row.length(), "BAM text and binary reference dictionaries disagree");
        }
        require(index == 0 || index == dictionary.size(), "Partial BAM text reference dictionary");
    }
    private static void auxiliary(byte[] body, int offset, Limits limits) throws IOException {
        var seen = new HashSet<String>();
        while (offset < body.length) {
            require(body.length - offset >= 3 && seen.size() < limits.tags(), "Truncated/excessive complete BAM auxiliary population");
            int a = body[offset++] & 255, b = body[offset++] & 255, type = body[offset++] & 255;
            require(letter(a) && (letter(b) || b >= '0' && b <= '9'), "Invalid BAM auxiliary tag identity");
            String tag = "" + (char)a + (char)b; require(seen.add(tag), "Duplicate BAM auxiliary tag: " + tag);
            require(!tag.equals("CG"), "Long-CIGAR CG transport requires separate original-edition admission; no placeholder replacement is inferred");
            int bytes;
            if (type == 'Z' || type == 'H') {
                int start = offset;
                while (offset < body.length && body[offset] != 0) { int c = body[offset++] & 255; require(c >= 32 && c <= 126 && (type != 'H' || Character.digit((char)c, 16) >= 0), "Invalid original BAM auxiliary text/hex byte"); }
                require(offset < body.length && (type != 'H' || (offset - start) % 2 == 0), "Unterminated/odd-hex BAM auxiliary field"); offset++; continue;
            } else if (type == 'B') {
                require(body.length - offset >= 5, "Truncated BAM auxiliary array header"); int subtype = body[offset++] & 255, count = integer(body, offset); offset += 4;
                require(count >= 0 && count <= limits.arrayItems(), "Complete BAM auxiliary array capacity exceeded");
                int width = switch (subtype) { case 'c','C' -> 1; case 's','S' -> 2; case 'i','I','f' -> 4; default -> 0; };
                require(width > 0, "Unsupported original BAM array subtype"); long total = (long)width * count; require(total <= body.length - offset, "Truncated BAM auxiliary array body"); offset += (int)total; continue;
            } else bytes = switch (type) { case 'A','c','C' -> 1; case 's','S' -> 2; case 'i','I','f' -> 4; case 'd' -> 8; default -> 0; };
            require(bytes > 0 && bytes <= body.length - offset, "Invalid/truncated BAM auxiliary scalar");
            if (type == 'A') require(body[offset] >= 33 && body[offset] <= 126, "Invalid BAM character tag byte");
            offset += bytes;
        }
    }
    private static boolean letter(int c) { return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'; }
    private static int integer(byte[] b, int i) { return b[i] & 255 | (b[i+1] & 255) << 8 | (b[i+2] & 255) << 16 | b[i+3] << 24; }
    private static final byte[] EOF = java.util.HexFormat.of().parseHex("1f8b08040000000000ff0600424302001b0003000000000000000000");
    private static final class Blocks extends InputStream {
        private final RandomAccessFile source; private final long maximum; private final Runnable check;
        private byte[] data = new byte[0]; private int position; private boolean terminal; private long decoded, blocks;
        Blocks(Path path, long maximum, Runnable check) throws IOException { source = new RandomAccessFile(path.toFile(), "r"); this.maximum = maximum; this.check = check; }
        @Override public int read() throws IOException { if (!availableBlock()) return -1; decoded++; require(decoded <= maximum, "Complete decoded BAM byte capacity exceeded"); return data[position++] & 255; }
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, target.length); if (length == 0) return 0; if (!availableBlock()) return -1;
            int count = Math.min(length, data.length - position); decoded = Math.addExact(decoded, count); require(decoded <= maximum, "Complete decoded BAM byte capacity exceeded");
            System.arraycopy(data, position, target, offset, count); position += count; return count;
        }
        private boolean availableBlock() throws IOException {
            check.run(); if (position < data.length) return true; if (terminal) return false;
            long start = source.getFilePointer(); require(start < source.length(), "BAM lacks a canonical final BGZF EOF block");
            byte[] head = new byte[12]; source.readFully(head);
            require(head[0] == 31 && head[1] == (byte)139 && head[2] == 8 && head[3] == 4, "Require complete ordinary BGZF/FEXTRA BAM blocks");
            int extraLength = head[10] & 255 | (head[11] & 255) << 8; require(extraLength >= 6, "Missing BGZF block-size subfield");
            byte[] extra = new byte[extraLength]; source.readFully(extra); int size = -1;
            for (int i = 0; i < extra.length;) {
                require(extra.length - i >= 4, "Truncated BGZF extra subfield"); int n = extra[i+2] & 255 | (extra[i+3] & 255) << 8;
                require(n <= extra.length - i - 4, "BGZF extra subfield exceeds its header");
                if (extra[i] == 'B' && extra[i+1] == 'C') { require(n == 2 && size == -1, "Duplicate/invalid BGZF BC subfield"); size = (extra[i+4] & 255 | (extra[i+5] & 255) << 8) + 1; }
                i += 4 + n;
            }
            int header = 12 + extraLength; require(size >= header + 8 && size <= 65536 && size <= source.length() - start, "Invalid/truncated complete BGZF block size");
            byte[] remainder = new byte[size - header]; source.readFully(remainder); blocks++;
            long expectedSize = Integer.toUnsignedLong(StringTieBam.integer(remainder, remainder.length - 4));
            require(expectedSize <= 65536 && expectedSize <= maximum - decoded, "Complete expanded BGZF/BAM capacity exceeded");
            if (expectedSize == 0) {
                byte[] all = new byte[size]; System.arraycopy(head, 0, all, 0, head.length); System.arraycopy(extra, 0, all, head.length, extra.length); System.arraycopy(remainder, 0, all, header, remainder.length);
                require(Arrays.equals(all, EOF) && source.getFilePointer() == source.length(), "Require one canonical final BGZF EOF and no trailing bytes"); terminal = true; return false;
            }
            var inflater = new Inflater(true);
            try {
                inflater.setInput(remainder, 0, remainder.length - 8); byte[] expanded = new byte[(int)expectedSize + 1]; int count = 0;
                while (!inflater.finished() && count < expanded.length) { int n = inflater.inflate(expanded, count, expanded.length - count); require(n > 0 || inflater.finished(), "Incomplete/nonprogressing raw BGZF deflate stream"); count += n; }
                require(inflater.finished() && inflater.getRemaining() == 0 && count == expectedSize, "BGZF raw deflate/expanded-size mismatch");
                var crc = new CRC32(); crc.update(expanded, 0, count); require(crc.getValue() == Integer.toUnsignedLong(StringTieBam.integer(remainder, remainder.length - 8)), "BGZF CRC32 mismatch");
                data = Arrays.copyOf(expanded, count); position = 0; return true;
            } catch (DataFormatException invalid) { throw new IOException("Invalid BGZF deflate body", invalid); }
            finally { inflater.end(); }
        }
        int required() throws IOException { int n = read(); if (n < 0) throw new EOFException("Truncated complete BAM field"); return n; }
        int integer() throws IOException { return required() | required() << 8 | required() << 16 | required() << 24; }
        byte[] bytes(int length) throws IOException { byte[] bytes = readNBytes(length); require(bytes.length == length, "Truncated complete BAM field body"); return bytes; }
        @Override public void close() throws IOException { source.close(); }
    }
    private static void require(boolean condition, String reason) throws IOException { if (!condition) throw new IOException(reason); }
}
