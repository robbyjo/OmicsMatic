/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Streaming, source-aware admission of text Minimac compressed-reference blocks. */
public final class MinimacReferenceBlocks {
    private MinimacReferenceBlocks() { }

    /** Supported text encodings; binary MSAV needs an original complete Savvy export. */
    public enum Format { M3VCF_1_2, M3VCF_2_0, MVCF_3_0 }
    /** Legacy v1.2 names identify haplotype columns, without an inferred individual/copy relation. */
    public enum ColumnKind { HAPLOTYPE, SAMPLE }
    /** The caller must explicitly select its engine's block-boundary contract. */
    public enum BoundaryPolicy {
        /** Reject any repeated variant key, including shared block boundaries. */
        REJECT_DUPLICATES,
        /** Verify equal ordered haplotypes and retain the later row, as current Minimac does. */
        VERIFY_SHARED_KEEP_LATER,
        /** Also require every adjacent same-contig block to share its boundary, for legacy Minimac3. */
        REQUIRE_SHARED_KEEP_LATER
    }
    /** M3 interval endpoints name variant positions; MVCF END includes allele-string extent. */
    public record Range(int start, int end) { }
    /** One immutable physical block, with original labels and ordered mappings. */
    public record Block(long number, String contig, String coordinateField, String label,
            Optional<Range> declaredRange, int variants, int representatives, String formatField,
            List<List<Integer>> mappings, Map<String,String> info, List<String> flags) {
        public Block { mappings = mappings.stream().map(List::copyOf).toList(); info = Map.copyOf(info); flags = List.copyOf(flags); }
    }
    /** One physical variant row, including its exact identity and optional model parameters. */
    public record Row(Block block, int withinBlock, long sourceLine, int position, String id,
            String reference, String alternate, List<Integer> representativeAlleles,
            int alternateCopies, int totalCopies, OptionalDouble error, OptionalDouble recombination,
            OptionalDouble centimorgans, Map<String,String> info, List<String> flags) {
        public Row { representativeAlleles = List.copyOf(representativeAlleles); info = Map.copyOf(info); flags = List.copyOf(flags); }
        /**
         * Expand one original column's ordered haplotypes.
         * @param column zero-based original header-column index
         * @return one or two explicit alleles, without regrouping columns or inferring sex
         */
        public List<Integer> phasedAlleles(int column) {
            return block.mappings().get(column).stream().map(representativeAlleles::get).toList();
        }
    }
    /** Both boundary rows remain available even when only the later one enters the selected stream. */
    public record Boundary(Row earlier, Row later, boolean idChanged, boolean parametersChanged) { }
    /** Observed block extents are distinct from raw, sometimes absent, M3 declarations. */
    public record Extent(Block block, int firstPosition, int lastPosition, long maxAlleleStringExtent) { }
    /** Per-contig actual copy counts and unique selected-variant inventory. */
    public record Contig(String id, List<Integer> columnPloidies, long blocks, long selectedVariants) {
        public Contig { columnPloidies = List.copyOf(columnPloidies); }
    }
    /** All visitor outputs remain provisional until this complete-file summary is returned. */
    public record Summary(Format format, ColumnKind columnKind, List<String> columns,
            List<String> metadata, List<Contig> contigs, long blocks, long physicalRows,
            long selectedVariants, long sharedBoundaries, long disjointBoundaries,
            long selectedHaplotypeCells, long errorParameters, long recombinationParameters,
            long centimorganParameters, long nonDnaAlleleRows, boolean gzip, long crlfLines,
            boolean finalLineHasLf, String identityAndPhaseSha256, String orderedHaplotypeMatrixSha256,
            String float32ModelParametersSha256) {
        public Summary { columns = List.copyOf(columns); metadata = List.copyOf(metadata); contigs = List.copyOf(contigs); }
    }
    /** Streaming ledgers; callers must discard or quarantine them if any later row fails. */
    @FunctionalInterface
    public interface Visitor {
        /**
         * Receive a selected row after any earlier duplicate has been replaced explicitly.
         * @param row selected immutable row
         * @throws IOException for a downstream admission failure
         */
        void selected(Row row) throws IOException;
        /**
         * Receive every physical row, including both copies of a shared boundary.
         * @param row immutable physical row
         * @throws IOException for a downstream admission failure
         */
        default void physical(Row row) throws IOException { }
        /**
         * Record a verified boundary and any identity/parameter change; the later row is selected.
         * @param boundary both original rows with equal ordered haplotypes
         * @throws IOException for a downstream admission failure
         */
        default void boundary(Boundary boundary) throws IOException { }
        /**
         * Record actual block coordinates separately from original header text.
         * @param extent completed physical block
         * @throws IOException for a downstream admission failure
         */
        default void block(Extent extent) throws IOException { }
    }

    /**
     * Audit a plain/gzip/BGZF UTF-8 text reference using an explicit overlap policy.
     * @param source original text M3VCF or complete MVCF export
     * @param policy explicit source/consumer boundary policy
     * @return complete decoded-file inventory and separately scoped fingerprints
     * @throws IOException for malformed, unsupported or inconsistent input
     */
    public static Summary audit(Path source, BoundaryPolicy policy) throws IOException {
        return read(source, policy, row -> { });
    }

    /**
     * Audit physical blocks and stream selected rows, retaining boundary evidence.
     * Supports legacy M3VCF/1.2 raw-bit haplotype columns, M3VCFv2.0 diploid
     * columns/nonempty ALT-index deltas, and complete MVCFv3.0 (including the
     * upstream M3VCFv3.0 alias) exports. Binary MSAV is not decoded here.
     *
     * <p>Shared last/first rows must have equal keys and every ordered haplotype.
     * IDs and model parameters may differ and are reported; the later row is
     * selected only under the chosen keep-later policy. Other repeated keys fail.
     * Declared counts, mapping bounds, per-contig column ploidies, row order,
     * block extents, complete alleles, probabilities and optional AC/AN in MVCF
     * are checked. M3 columns are never paired from their names. Allele tokens
     * remain opaque single-ALT labels: no assembly, DNA normalization, structural
     * variant interpretation or sex is inferred. Non-DNA selected rows are counted.</p>
     *
     * <p>Fingerprints separately cover column identity/phase, ordered haplotype
     * matrix without labels/grouping/IDs, and binary32 model parameters. Matrix
     * equality alone does not prove sample identity. Model hashing preserves
     * missingness, uses the original engine's binary32 representation and merges
     * signed zero; original numeric text remains in the row INFO map.</p>
     *
     * @param source complete original text reference; never modified here
     * @param policy explicit duplicate/required-overlap contract
     * @param visitor provisional physical, selected, boundary and extent ledgers
     * @return successfully audited inventory; no summary is returned on failure
     * @throws IOException for invalid input, capacity bounds or a visitor failure
     */
    public static Summary read(Path source, BoundaryPolicy policy, Visitor visitor) throws IOException {
        if (policy == null || visitor == null) throw new IllegalArgumentException("Require explicit boundary policy and visitor");
        try (var input = new Lines(source)) {
            var metadata = new ArrayList<String>();
            var singleton = new HashMap<String,String>();
            String text; long headerCharacters = 0;
            while ((text = input.next()) != null && text.startsWith("##")) {
                headerCharacters = Math.addExact(headerCharacters, text.length());
                if (headerCharacters > 8388608 || metadata.size() >= 65536) throw input.error("Metadata exceeds supported bound");
                metadata.add(text);
                int equals = text.indexOf('=');
                if (equals < 3) throw input.error("Malformed reference metadata");
                String key = text.substring(2, equals), value = text.substring(equals + 1);
                if (Set.of("fileformat", "subfileformat", "version", "compression", "phasing", "n_blocks", "n_haps", "n_markers").contains(key)) {
                    if (singleton.putIfAbsent(key, value) != null) throw input.error("Repeated singleton metadata: " + key);
                }
            }
            Format format;
            if ("M3VCF".equals(singleton.get("fileformat")) && "1.2".equals(singleton.get("version"))) format = Format.M3VCF_1_2;
            else if ("M3VCFv2.0".equals(singleton.get("fileformat"))) format = Format.M3VCF_2_0;
            else if (Set.of("VCFv4.2", "VCFv4.3").contains(singleton.getOrDefault("fileformat", ""))
                    && Set.of("MVCFv3.0", "M3VCFv3.0").contains(singleton.getOrDefault("subfileformat", ""))
                    && "full".equals(singleton.get("phasing"))) format = Format.MVCF_3_0;
            else throw input.error("Require text M3VCF/1.2, M3VCFv2.0 or full-phased MVCFv3.0; binary MSAV needs a complete original export");
            if (format == Format.M3VCF_1_2 && !"block".equals(singleton.get("compression"))) throw input.error("Legacy M3VCF requires compression=block");
            if (text == null) throw input.error("Missing reference column header");
            String[] header = text.split("\t", -1);
            String[] fixed = {"#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT"};
            if (header.length < 10 || header.length > 1000009) throw input.error("Require 1..1000000 reference columns");
            for (int i = 0; i < fixed.length; i++) if (!header[i].equals(fixed[i])) throw input.error("Invalid reference column header");
            var columns = new ArrayList<String>(); var columnIds = new HashSet<String>();
            for (int i = 9; i < header.length; i++) {
                token(header[i], input);
                if (!columnIds.add(header[i])) throw input.error("Duplicate reference column ID");
                columns.add(header[i]);
            }
            ColumnKind kind = format == Format.M3VCF_1_2 ? ColumnKind.HAPLOTYPE : ColumnKind.SAMPLE;
            if (format == Format.M3VCF_1_2) for (String key : List.of("n_blocks", "n_haps", "n_markers")) required(singleton, key, input);
            long declaredBlocks = declared(singleton, "n_blocks", input), declaredHaps = declared(singleton, "n_haps", input), declaredMarkers = declared(singleton, "n_markers", input);
            var emitter = new Emitter(kind, columns, visitor);
            long blocks = 0, physicalRows = 0, shared = 0, disjoint = 0;
            String contig = null; int previousPosition = 0;
            var keysAtPosition = new HashSet<String>();
            Row pending = null;
            while ((text = input.next()) != null) {
                String[] f = text.split("\t", -1);
                if (f.length != header.length) throw input.error("Physical block width differs from original header");
                token(f[0], input); token(f[1], input); token(f[2], input); token(f[8], input);
                Info info = info(f[7], input);
                int count = integer(required(info.values, "VARIANTS", input), 1, 10000000, input);
                int reps = integer(required(info.values, "REPS", input), 1, 2000000, input);
                Optional<Range> range;
                if (format == Format.MVCF_3_0) {
                    if (!f[4].equals("<BLOCK>") || !f[8].equals("UHM")) throw input.error("Require an original MVCF UHM block");
                    range = Optional.of(new Range(integer(f[1], 1, Integer.MAX_VALUE, input), integer(required(info.values, "END", input), 1, Integer.MAX_VALUE, input)));
                } else {
                    range = range(f[1], input);
                    if (format == Format.M3VCF_1_2 && range.isEmpty()) throw input.error("Legacy v1.2 block requires start-end positions");
                    if (f[2].startsWith("<BLOCK:")) {
                        if (!f[2].endsWith(">")) throw input.error("Malformed block-index label");
                        Range indices = range(f[2].substring(7, f[2].length() - 1), 0, input).orElseThrow(() -> input.error("Malformed block-index interval"));
                        if ((long)indices.end - indices.start + 1 != count) throw input.error("Block-index span differs from VARIANTS");
                    }
                }
                if (range.isPresent() && range.get().start > range.get().end) throw input.error("Reversed block range");
                var mappings = new ArrayList<List<Integer>>(); var ploidies = new ArrayList<Integer>(); int[] multiplicities = new int[reps]; int total = 0;
                for (int i = 9; i < f.length; i++) {
                    String delimiter = format == Format.MVCF_3_0 ? "," : format == Format.M3VCF_2_0 ? "\\|" : "\u0000";
                    String[] values = f[i].split(delimiter, -1);
                    if (values.length < 1 || values.length > 2 || format == Format.M3VCF_1_2 && values.length != 1 || format == Format.M3VCF_2_0 && values.length != 2) throw input.error("Unsupported or missing column mapping/ploidy");
                    var mapping = new ArrayList<Integer>();
                    for (String value : values) { int index = integer(value, 0, reps - 1, input); mapping.add(index); multiplicities[index]++; total++; }
                    mappings.add(mapping); ploidies.add(values.length);
                }
                if (declaredHaps >= 0 && declaredHaps != total) throw input.error("n_haps differs from actual ordered copies");
                if (!f[0].equals(contig)) {
                    if (emitter.contigs.containsKey(f[0]) || emitter.contigs.size() >= 10000) throw input.error("Contig reappears or contig count exceeds bound");
                    contig = f[0]; previousPosition = 0; keysAtPosition.clear();
                    emitter.contigs.put(contig, new ContigCounts(ploidies));
                } else if (!emitter.contigs.get(contig).ploidies.equals(ploidies)) throw input.error("Column ploidy changes within contig; use explicit per-region preparation");
                emitter.contigs.get(contig).blocks++;
                Block block = new Block(++blocks, contig, f[1], f[2], range, count, reps, f[8], mappings, info.values, info.flags);
                int first = 0, last = 0; long alleleEnd = 0;
                for (int i = 0; i < count; i++) {
                    text = input.next();
                    if (text == null) throw input.error("Truncated compressed-reference block");
                    String[] v = text.split("\t", -1);
                    if (v.length != (format == Format.MVCF_3_0 ? 8 : 9) || !v[0].equals(contig)) throw input.error("Variant width/contig differs from block contract");
                    int position = integer(v[1], 1, Integer.MAX_VALUE, input);
                    token(v[2], input); allele(v[3], input); allele(v[4], input);
                    if (v[3].equals(v[4])) throw input.error("REF equals ALT");
                    if (position < previousPosition) throw input.error("Variant positions are not sorted");
                    if (i == 0) first = position;
                    last = position; alleleEnd = Math.max(alleleEnd, (long)position + Math.max(v[3].length(), v[4].length()) - 1);
                    Info vi = info(v[7], input);
                    List<Integer> alleles = decodeAlleles(format, format == Format.MVCF_3_0 ? required(vi.values, "UHA", input) : v[8], reps, input);
                    int alt = 0; for (int j = 0; j < reps; j++) alt += multiplicities[j] * alleles.get(j);
                    if (format == Format.MVCF_3_0 && (integer(required(vi.values, "AC", input), 0, total, input) != alt || integer(required(vi.values, "AN", input), 1, 2000000, input) != total)) throw input.error("MVCF AC/AN differ from expanded reference haplotypes");
                    OptionalDouble error = parameter(vi.values, "ERR", "Err", true, input);
                    OptionalDouble recombination = parameter(vi.values, "RECOM", "Recom", true, input);
                    OptionalDouble cm = format == Format.MVCF_3_0 ? parameter(vi.values, "CM", "CM", false, input) : OptionalDouble.empty();
                    Row row = new Row(block, i, input.line, position, v[2], v[3], v[4], alleles, alt, total, error, recombination, cm, vi.values, vi.flags);
                    boolean boundary = i == 0 && pending != null && pending.block.contig.equals(contig);
                    boolean same = pending != null && sameKey(pending, row);
                    String key = v[3] + "\u0000" + v[4];
                    if (position != previousPosition) keysAtPosition.clear();
                    if (same) {
                        if (!boundary || policy == BoundaryPolicy.REJECT_DUPLICATES) throw input.error("Repeated variant key is not an allowed shared block boundary");
                        if (!sameHaplotypes(pending, row)) throw input.error("Shared-boundary ordered haplotypes conflict");
                        visitor.boundary(new Boundary(pending, row, !pending.id.equals(row.id), !sameParameters(pending, row)));
                        shared++;
                    } else {
                        if (!keysAtPosition.add(key)) throw input.error("Repeated nonboundary variant key");
                        if (boundary) {
                            if (policy == BoundaryPolicy.REQUIRE_SHARED_KEEP_LATER) throw input.error("Consumer requires a shared boundary between same-contig blocks");
                            disjoint++;
                        }
                        if (pending != null) emitter.emit(pending);
                    }
                    previousPosition = position; pending = row; physicalRows++;
                    visitor.physical(row);
                }
                if (range.isPresent()) {
                    Range expected = range.get(); long actualEnd = format == Format.MVCF_3_0 ? alleleEnd : last;
                    if (first != expected.start || actualEnd != expected.end) throw input.error("Declared block extent differs from actual variant positions/allele strings");
                }
                visitor.block(new Extent(block, first, last, alleleEnd));
            }
            if (pending == null) throw input.error("Empty compressed reference");
            emitter.emit(pending);
            if (declaredBlocks >= 0 && declaredBlocks != blocks || declaredMarkers >= 0 && declaredMarkers != emitter.rows) throw input.error("Declared global block/unique-marker count differs from complete input");
            var contigs = new ArrayList<Contig>(); emitter.contigs.forEach((name, value) -> contigs.add(new Contig(name, value.ploidies, value.blocks, value.rows)));
            return new Summary(format, kind, columns, metadata, contigs, blocks, physicalRows, emitter.rows, shared, disjoint,
                    emitter.cells, emitter.errors, emitter.recombinations, emitter.cms, emitter.nonDna, input.gzip, input.crlf,
                    input.lastHadLf, hex(emitter.identity), hex(emitter.matrix), hex(emitter.model));
        } catch (ArithmeticException failure) { throw new IOException("Reference inventory exceeds exact integer capacity", failure); }
    }

    private static boolean sameKey(Row a, Row b) { return a.block.contig.equals(b.block.contig) && a.position == b.position && a.reference.equals(b.reference) && a.alternate.equals(b.alternate); }
    private static boolean sameParameters(Row a, Row b) { return a.error.equals(b.error) && a.recombination.equals(b.recombination) && a.centimorgans.equals(b.centimorgans); }
    private static boolean sameHaplotypes(Row a, Row b) {
        if (a.totalCopies != b.totalCopies || a.block.mappings.size() != b.block.mappings.size()) return false;
        for (int i = 0; i < a.block.mappings.size(); i++) {
            var am = a.block.mappings.get(i); var bm = b.block.mappings.get(i);
            if (am.size() != bm.size()) return false;
            for (int j = 0; j < am.size(); j++) if (!a.representativeAlleles.get(am.get(j)).equals(b.representativeAlleles.get(bm.get(j)))) return false;
        }
        return true;
    }
    private static List<Integer> decodeAlleles(Format format, String text, int reps, Lines input) throws IOException {
        var result = new ArrayList<Integer>(reps);
        if (format == Format.M3VCF_1_2) {
            if (text.length() != reps) throw input.error("Raw M3 haplotype-bit length differs from REPS");
            for (int i = 0; i < reps; i++) { char c = text.charAt(i); if (c != '0' && c != '1') throw input.error("Require complete 0/1 reference bits"); result.add(c - '0'); }
        } else if (format == Format.MVCF_3_0) {
            String[] values = text.split(",", -1);
            if (values.length != reps) throw input.error("UHA length differs from REPS");
            for (String value : values) result.add(integer(value, 0, 1, input));
        } else {
            if (text.isEmpty() || text.equals(".") || text.equals("-")) throw input.error("M3v2 needs a nonempty ALT-index delta list; missing lists must not become an invented ALT copy");
            for (int i = 0; i < reps; i++) result.add(0);
            String[] values = text.split(",", -1); long previous = 0;
            for (int i = 0; i < values.length; i++) {
                int delta = integer(values[i], i == 0 ? 0 : 1, reps - 1, input);
                previous = Math.addExact(previous, delta);
                if (previous >= reps) throw input.error("Cumulative ALT-index delta exceeds REPS; upstream conversion is unsafe");
                result.set((int)previous, 1);
            }
        }
        return result;
    }
    private record Info(Map<String,String> values, List<String> flags) { }
    private static Info info(String text, Lines input) throws IOException {
        var values = new LinkedHashMap<String,String>(); var flags = new ArrayList<String>(); var keys = new HashSet<String>();
        if (text.equals(".")) return new Info(values, flags);
        for (String entry : text.split(";", -1)) {
            int equals = entry.indexOf('='); String key = equals < 0 ? entry : entry.substring(0, equals);
            token(key, input);
            if (!keys.add(key)) throw input.error("Repeated INFO key/flag");
            if (equals < 0) flags.add(key);
            else { if (equals == entry.length() - 1 || entry.substring(equals + 1).codePoints().anyMatch(Character::isISOControl)) throw input.error("Empty or control-containing INFO value"); values.put(key, entry.substring(equals + 1)); }
        }
        return new Info(values, flags);
    }
    private static OptionalDouble parameter(Map<String,String> values, String upper, String mixed, boolean probability, Lines input) throws IOException {
        if (!upper.equals(mixed) && values.containsKey(upper) && values.containsKey(mixed)) throw input.error("Ambiguous model-parameter aliases");
        String text = values.containsKey(upper) ? values.get(upper) : values.get(mixed);
        if (text == null || text.equals(".")) return OptionalDouble.empty();
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value) || probability && (value < 0 || value > 1)) throw input.error("Nonfinite or out-of-range " + upper);
            if (!Float.isFinite((float)value)) throw input.error("Model parameter exceeds original binary32 capacity");
            return OptionalDouble.of(value);
        } catch (NumberFormatException failure) { throw input.error("Invalid " + upper + " number"); }
    }
    private static Optional<Range> range(String text, Lines input) throws IOException { return range(text, 1, input); }
    private static Optional<Range> range(String text, int minimum, Lines input) throws IOException {
        if (!text.matches("[0-9]+-[0-9]+")) return Optional.empty();
        String[] fields = text.split("-", -1);
        int start = integer(fields[0], minimum, Integer.MAX_VALUE, input), end = integer(fields[1], minimum, Integer.MAX_VALUE, input);
        if (end < start) throw input.error("Reversed block/index interval");
        return Optional.of(new Range(start, end));
    }
    private static int integer(String text, int minimum, int maximum, Lines input) throws IOException {
        try {
            if (text.isEmpty() || !text.chars().allMatch(c -> c >= '0' && c <= '9')) throw input.error("Require an exact unsigned integer");
            int value = Integer.parseInt(text);
            if (value < minimum || value > maximum) throw input.error("Reference integer exceeds declared bounds");
            return value;
        } catch (NumberFormatException failure) { throw input.error("Reference integer exceeds supported capacity"); }
    }
    private static long declared(Map<String,String> values, String key, Lines input) throws IOException {
        String text = values.get(key);
        if (text == null) return -1;
        try {
            if (text.isEmpty() || !text.chars().allMatch(c -> c >= '0' && c <= '9')) throw input.error("Invalid " + key);
            long value = Long.parseLong(text); if (value < 1) throw input.error("Require positive " + key); return value;
        } catch (NumberFormatException failure) { throw input.error("Declared inventory exceeds exact capacity"); }
    }
    private static String required(Map<String,String> values, String key, Lines input) throws IOException {
        String value = values.get(key); if (value == null) throw input.error("Missing " + key); return value;
    }
    private static void token(String value, Lines input) throws IOException {
        if (value.isEmpty() || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) throw input.error("Invalid reference token/identifier");
    }
    private static void allele(String value, Lines input) throws IOException {
        token(value, input); if (value.equals(".") || value.contains(",")) throw input.error("Require a complete single ALT/reference allele token");
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    private static void update(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.UTF_8)); }
    private static String hex(MessageDigest digest) { return HexFormat.of().formatHex(digest.digest()); }
    private static final class ContigCounts {
        final List<Integer> ploidies; long blocks, rows;
        ContigCounts(List<Integer> ploidies) { this.ploidies = List.copyOf(ploidies); }
    }
    private static final class Emitter {
        final Visitor visitor; final MessageDigest identity = digest(), matrix = digest(), model = digest();
        final ByteBuffer parameterBytes = ByteBuffer.allocate(5);
        final Map<String,ContigCounts> contigs = new LinkedHashMap<>();
        long rows, cells, errors, recombinations, cms, nonDna;
        Emitter(ColumnKind kind, List<String> columns, Visitor visitor) {
            this.visitor = visitor; update(identity, "Minimac-identity-phase-v1\n" + kind + "\n");
            for (String column : columns) update(identity, column + "\n");
            update(matrix, "Minimac-ordered-haplotypes-v1\n"); update(model, "Minimac-float32-model-v1\n");
        }
        void emit(Row row) throws IOException {
            String key = row.block.contig + "\t" + row.position + "\t" + row.reference + "\t" + row.alternate + "\t";
            update(identity, key + row.id + "\t"); update(matrix, key); update(model, key);
            for (List<Integer> mapping : row.block.mappings) {
                for (int representative : mapping) { byte value = (byte)('0' + row.representativeAlleles.get(representative)); identity.update(value); matrix.update(value); }
                identity.update((byte)'\t');
            }
            identity.update((byte)'\n'); matrix.update((byte)'\n');
            for (OptionalDouble parameter : List.of(row.error, row.recombination, row.centimorgans)) {
                parameterBytes.clear(); parameterBytes.put((byte)(parameter.isPresent() ? 1 : 0));
                float value = parameter.isPresent() ? (float)parameter.getAsDouble() : 0;
                parameterBytes.putInt(Float.floatToIntBits(value == 0 ? 0 : value)); model.update(parameterBytes.array());
            }
            model.update((byte)'\n'); rows++; cells = Math.addExact(cells, row.totalCopies);
            if (row.error.isPresent()) errors++; if (row.recombination.isPresent()) recombinations++; if (row.centimorgans.isPresent()) cms++;
            if (!row.reference.matches("[ACGTNacgtn]+") || !row.alternate.matches("[ACGTNacgtn]+")) nonDna++;
            contigs.get(row.block.contig).rows++; visitor.selected(row);
        }
    }
    private static final class Lines implements AutoCloseable {
        final Reader reader; final boolean gzip; final char[] buffer = new char[8192];
        int index, size; long line, crlf; boolean lastHadLf;
        Lines(Path source) throws IOException {
            var raw = new BufferedInputStream(Files.newInputStream(source)); boolean good = false;
            try {
                raw.mark(2); int first = raw.read(), second = raw.read(); raw.reset(); gzip = first == 31 && second == 139;
                reader = new InputStreamReader(gzip ? new GZIPInputStream(raw) : raw,
                        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
                good = true;
            } finally { if (!good) raw.close(); }
        }
        String next() throws IOException {
            if (size < 0) return null;
            var text = new StringBuilder(); boolean lf = false;
            for (;;) {
                if (index == size) {
                    try { size = reader.read(buffer); }
                    catch (IOException failure) { throw new IOException("Compressed reference line " + (line + 1) + ": invalid UTF-8/compressed stream", failure); }
                    index = 0; if (size == -1) break; if (size == 0) continue;
                }
                char value = buffer[index++];
                if (value == '\n') { lf = true; break; }
                if (Character.isISOControl(value) && value != '\t' && value != '\r' || text.length() >= 16777216) throw new IOException("Compressed reference line " + (line + 1) + ": control character or reference line exceeds 16 MiB character bound");
                text.append(value);
            }
            if (!lf && text.isEmpty()) return null;
            line++; lastHadLf = lf;
            if (!text.isEmpty() && text.charAt(text.length()-1) == '\r') { text.setLength(text.length()-1); if (lf) crlf++; }
            if (text.indexOf("\r") >= 0) throw error("Embedded carriage return in reference text");
            return text.toString();
        }
        IOException error(String message) { return new IOException("Compressed reference line " + line + ": " + message); }
        @Override public void close() throws IOException { reader.close(); }
    }
}
