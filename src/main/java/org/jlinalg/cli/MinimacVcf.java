/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Typed phased-VCF intake for Minimac; all visitor/staging data stays provisional until success. */
final class MinimacVcf {
    private MinimacVcf() { }
    enum Role { REFERENCE, TARGET }
    enum MissingPolicy { EXACT_PHASED, ALLOW_FULL_MISSING_WITH_DECLARED_PLOIDY }
    enum PhasePolicy { REQUIRE_PIPE, ALLOW_UNPHASED_HOMOZYGOTES }
    record Limits(int samples, long variants, long genotypeCells, long inputBytes,
            long decodedCharacters, int lineCharacters, long stagedBytes, int identityRows) {
        Limits {
            if (samples < 1 || samples > 1000000 || variants < 1 || variants > 1000000000L
                    || genotypeCells < 1 || genotypeCells > 1000000000000L
                    || inputBytes < 1 || inputBytes > 1000000000000L
                    || decodedCharacters < 1 || decodedCharacters > 1000000000000L || lineCharacters < 64
                    || lineCharacters > 16777216 || stagedBytes < 1 || stagedBytes > 1000000000000L || identityRows < 1)
                throw new IllegalArgumentException("Invalid explicit Minimac VCF resource limits");
        }
    }
    record Copy(String person, int personCopy) { }
    record Identity(Map<String, Map<String, List<Copy>>> contigs) {
        Identity {
            var frozen = new LinkedHashMap<String, Map<String, List<Copy>>>();
            contigs.forEach((c, columns) -> {
                var copy = new LinkedHashMap<String, List<Copy>>();
                columns.forEach((name, values) -> copy.put(name, List.copyOf(values)));
                frozen.put(c, java.util.Collections.unmodifiableMap(copy));
            });
            contigs = java.util.Collections.unmodifiableMap(frozen);
        }
        void header(List<String> columns) throws IOException {
            Set<String> wanted = new HashSet<>(columns);
            for (var entry : contigs.entrySet())
                if (!entry.getValue().keySet().equals(wanted))
                    throw new IOException("Identity ledger columns differ from exact VCF header on " + entry.getKey());
        }
        List<Copy> copies(String contig, String column) throws IOException {
            Map<String, List<Copy>> values = contigs.get(contig);
            if (values == null || !values.containsKey(column))
                throw new IOException("No explicit column/copy identity for " + contig + "/" + column);
            return values.get(column);
        }
        void completed(Set<String> observed) throws IOException {
            if (!contigs.keySet().equals(observed))
                throw new IOException("Identity ledger has missing/extra contigs relative to complete input");
        }
    }
    record Call(String original, String staged, List<Integer> alleles, boolean missingRepresentationChanged,
            boolean homozygousPhaseRepresentationChanged) {
        Call { alleles = List.copyOf(alleles); }
    }
    record Row(String contig, int position, String id, String reference, String alternate,
            List<Call> calls, long sourceLine) {
        Row { calls = List.copyOf(calls); }
        String key() { return contig + "\t" + position + "\t" + reference + "\t" + alternate; }
    }
    record Selection(boolean keep, String reason) {
        Selection {
            if (reason == null || reason.isBlank() || reason.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Require an explicit single-line selection reason");
        }
        static Selection retain() { return new Selection(true, "RETAINED"); }
    }
    @FunctionalInterface interface Selector { Selection select(Row row) throws IOException; }
    @FunctionalInterface interface Visitor { void row(Row row, Selection selection) throws IOException; }
    record Contig(String id, List<Integer> columnPloidies, long variants, long selectedVariants) {
        Contig { columnPloidies = List.copyOf(columnPloidies); }
    }
    record Inventory(long variants, long genotypeCells, long haplotypeCopies, long observedCopies,
            long missingCopies, long fullyMissingCalls, long partiallyMissingCalls,
            long missingRepresentationChanges, long homozygousPhaseRepresentationChanges,
            long stagedGtRepresentationChanges, String identityPhaseSha256, String orderedMatrixSha256) { }
    record Summary(List<String> columns, List<Contig> contigs, Inventory input, Inventory selected,
            long excludedVariants, boolean gzip, long crlfLines, boolean finalLineHasLf) {
        Summary { columns = List.copyOf(columns); contigs = List.copyOf(contigs); }
    }

    static Identity identity(Path source, Limits limits) throws IOException {
        regular(source, limits.inputBytes);
        var indexed = new LinkedHashMap<String, Map<String, Map<Integer, Copy>>>();
        var physicalCopies = new HashSet<String>();
        long rows = 0;
        try (var lines = new Lines(source, limits)) {
            if (!"contig\tcolumn_id\tcolumn_copy\tperson_id\tperson_copy".equals(lines.next()))
                throw lines.error("Identity TSV header must be contig,column_id,column_copy,person_id,person_copy");
            String text;
            while ((text = lines.next()) != null) {
                String[] f = text.split("\t", -1);
                if (f.length != 5) throw lines.error("Identity row needs exactly five fields");
                contigToken(f[0], lines); token(f[1], lines); token(f[3], lines);
                int columnCopy = integer(f[2], 1, 2, lines), personCopy = integer(f[4], 1, 2, lines);
                if (++rows > limits.identityRows) throw lines.error("Identity ledger row bound exceeded");
                String physical = f[0] + "\0" + f[3] + "\0" + personCopy;
                if (!physicalCopies.add(physical)) throw lines.error("The same declared person/copy is assigned to multiple engine columns");
                var columns = indexed.computeIfAbsent(f[0], ignored -> new LinkedHashMap<>());
                var copies = columns.computeIfAbsent(f[1], ignored -> new LinkedHashMap<>());
                if (copies.putIfAbsent(columnCopy, new Copy(f[3], personCopy)) != null)
                    throw lines.error("Duplicate column/copy identity");
                if (columns.size() > limits.samples || indexed.size() > 10000)
                    throw lines.error("Identity sample/contig bound exceeded");
            }
        }
        if (indexed.isEmpty()) throw new IOException("Empty Minimac identity ledger");
        var result = new LinkedHashMap<String, Map<String, List<Copy>>>();
        for (var c : indexed.entrySet()) {
            var columns = new LinkedHashMap<String, List<Copy>>();
            for (var column : c.getValue().entrySet()) {
                Map<Integer, Copy> copies = column.getValue();
                if (!copies.containsKey(1)) throw new IOException("Column copy2 requires its explicit copy1");
                var values = new ArrayList<Copy>(); values.add(copies.get(1));
                if (copies.containsKey(2)) values.add(copies.get(2));
                columns.put(column.getKey(), values);
            }
            result.put(c.getKey(), columns);
        }
        return new Identity(result);
    }

    /**
     * Stream a complete phased VCF and optionally stage explicitly selected GT-only BGZF rows.
     * The caller owns a NEW private output directory and must quarantine all partials on failure.
     * Original INFO, QUAL, FILTER, FORMAT metadata and non-GT payload are not forwarded: this
     * deliberate GT-only staging contract keeps original inputs/snapshots separately unchanged.
     * Selection is never implicit; excluded rows still enter input inventories and the visitor.
     */
    static Summary read(Path source, Role role, Identity identity, MissingPolicy missing, PhasePolicy phase, Limits limits,
            Path bgzfOutput, Selector selector, Visitor visitor) throws IOException {
        if (role == null || identity == null || missing == null || phase == null || selector == null || visitor == null)
            throw new IllegalArgumentException("Require explicit role, identity, missing policy, selector and visitor");
        regular(source, limits.inputBytes);
        try (var lines = new Lines(source, limits)) {
            String text, fileformat = null;
            int metaCount = 0; long metadataCharacters = 0;
            while ((text = lines.next()) != null && text.startsWith("##")) {
                if (++metaCount > 65536 || (metadataCharacters += text.length()) > 8388608)
                    throw lines.error("VCF metadata bound exceeded");
                if (text.startsWith("##fileformat=")) {
                    if (fileformat != null) throw lines.error("Duplicate VCF fileformat");
                    fileformat = text.substring(13);
                }
            }
            if (!Set.of("VCFv4.1", "VCFv4.2", "VCFv4.3").contains(fileformat == null ? "" : fileformat))
                throw lines.error("Require explicit supported text VCF fileformat");
            if (text == null) throw lines.error("Missing VCF column header");
            String[] h = text.split("\t", -1);
            String[] fixed = {"#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT"};
            if (h.length < 10 || h.length - 9 > limits.samples) throw lines.error("VCF sample count exceeds explicit bound");
            for (int i = 0; i < fixed.length; i++) if (!h[i].equals(fixed[i])) throw lines.error("Invalid fixed VCF header");
            var columns = new ArrayList<String>(); var names = new HashSet<String>();
            for (int i = 9; i < h.length; i++) {
                token(h[i], lines);
                if (!names.add(h[i])) throw lines.error("Duplicate VCF column ID");
                columns.add(h[i]);
            }
            identity.header(columns);
            var all = new Counts(columns); var selected = new Counts(columns);
            var contigs = new LinkedHashMap<String, MutableContig>();
            var keysAtPosition = new HashSet<String>();
            String currentContig = null; int previousPosition = 0;
            try (var staged = bgzfOutput == null ? null : writer(bgzfOutput, limits.stagedBytes)) {
                if (staged != null) {
                    staged.write("##fileformat=VCFv4.2\n##phasing=full\n");
                    for (String contig : identity.contigs.keySet()) staged.write("##contig=<ID=" + contig + ">\n");
                    staged.write("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Explicit declared phased genotype\">\n");
                    staged.write(String.join("\t", h)); staged.write('\n');
                }
                while ((text = lines.next()) != null) {
                    String[] f = text.split("\t", -1);
                    if (f.length != h.length) throw lines.error("VCF row width differs from sample header");
                    contigToken(f[0], lines);
                    int position = integer(f[1], 1, Integer.MAX_VALUE, lines);
                    token(f[2], lines); allele(f[3], lines); allele(f[4], lines);
                    if (f[3].equals(f[4])) throw lines.error("VCF REF equals ALT");
                    if (!f[0].equals(currentContig)) {
                        if (contigs.containsKey(f[0]) || contigs.size() >= 10000)
                            throw lines.error("VCF contig reappears or exceeds supported bound");
                        var ploidies = new ArrayList<Integer>();
                        for (String column : columns) ploidies.add(identity.copies(f[0], column).size());
                        contigs.put(f[0], new MutableContig(ploidies));
                        currentContig = f[0]; previousPosition = 0; keysAtPosition.clear();
                    }
                    if (position < previousPosition) throw lines.error("VCF positions are not sorted");
                    if (position != previousPosition) keysAtPosition.clear();
                    if (!keysAtPosition.add(f[3] + "\0" + f[4])) throw lines.error("Duplicate VCF variant key");
                    previousPosition = position;
                    String[] format = f[8].split(":", -1); int gt = -1; var tags = new HashSet<String>();
                    for (int i = 0; i < format.length; i++) {
                        token(format[i], lines);
                        if (!tags.add(format[i])) throw lines.error("Duplicate VCF FORMAT field");
                        if (format[i].equals("GT")) gt = i;
                    }
                    if (gt < 0) throw lines.error("VCF GT field is required; no GL/PL or dosage inference");
                    var calls = new ArrayList<Call>(columns.size());
                    for (int i = 0; i < columns.size(); i++) {
                        String[] fields = f[i + 9].split(":", -1);
                        if (fields.length > format.length || gt >= fields.length)
                            throw lines.error("Missing GT or excessive sample FORMAT fields");
                        calls.add(call(fields[gt], identity.copies(f[0], columns.get(i)).size(), role, missing, phase, lines));
                    }
                    Row row = new Row(f[0], position, f[2], f[3], f[4], calls, lines.line);
                    Selection decision = selector.select(row);
                    if (decision == null) throw lines.error("Selector returned no explicit decision");
                    if (role == Role.REFERENCE && !decision.keep)
                        throw lines.error("Reference-panel marker filtering is not a VCF intake operation");
                    all.add(row);
                    if (all.variants > limits.variants || all.cells > limits.genotypeCells)
                        throw lines.error("VCF variant/genotype-cell bound exceeded");
                    MutableContig count = contigs.get(f[0]); count.variants++;
                    if (decision.keep) {
                        selected.add(row); count.selected++;
                        if (staged != null) {
                            // QUAL/FILTER/INFO are intentionally omitted under this GT-only contract.
                            staged.write(row.contig + "\t" + row.position + "\t" + row.id + "\t"
                                    + row.reference + "\t" + row.alternate + "\t.\t.\t.\tGT");
                            for (Call call : row.calls) { staged.write('\t'); staged.write(call.staged); }
                            staged.write('\n');
                        }
                    }
                    visitor.row(row, decision);
                }
            }
            if (all.variants == 0) throw lines.error("Empty phased VCF");
            identity.completed(contigs.keySet());
            var resultContigs = new ArrayList<Contig>();
            contigs.forEach((name, c) -> resultContigs.add(new Contig(name, c.ploidies, c.variants, c.selected)));
            return new Summary(columns, resultContigs, all.finish(), selected.finish(), all.variants - selected.variants,
                    lines.gzip, lines.crlf, lines.finalLf);
        } catch (ArithmeticException failure) { throw new IOException("VCF inventory exceeds exact integer capacity", failure); }
        catch (RuntimeIOException failure) {
            Throwable cause = failure;
            for (int i = 0; i < 16 && cause.getCause() != null; i++) cause = cause.getCause();
            throw new IOException("Minimac GT-only BGZF staging failed: " + cause.getMessage()
                    + "; provisional outputs must be quarantined", failure);
        }
    }

    private static Call call(String original, int ploidy, Role role, MissingPolicy policy, PhasePolicy phase, Lines lines) throws IOException {
        String[] values;
        boolean represented = false, homozygous = false;
        if (original.equals(".") && ploidy == 2
                && role == Role.TARGET && policy == MissingPolicy.ALLOW_FULL_MISSING_WITH_DECLARED_PLOIDY) {
            values = new String[]{".", "."}; represented = true;
        } else if (original.equals("./.") && ploidy == 2
                && role == Role.TARGET && policy == MissingPolicy.ALLOW_FULL_MISSING_WITH_DECLARED_PLOIDY) {
            values = new String[]{".", "."}; represented = true;
        } else {
            if (original.indexOf('/') >= 0) {
                values = original.split("/", -1);
                if (phase != PhasePolicy.ALLOW_UNPHASED_HOMOZYGOTES || values.length != 2
                        || !values[0].equals(values[1]) || !Set.of("0", "1").contains(values[0]))
                    throw lines.error("Unphased nonmissing GT requires explicit homozygote policy; heterozygous/partially missing phase is never inferred");
                homozygous = true;
            } else values = original.split("\\|", -1);
        }
        if (values.length != ploidy) throw lines.error("GT ploidy differs from explicit identity ledger");
        var alleles = new ArrayList<Integer>();
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value.equals(".")) {
                if (role == Role.REFERENCE) throw lines.error("Reference GT has a missing haplotype copy");
                alleles.add(-1);
            } else {
                int allele = integer(value, 0, 1, lines); alleles.add(allele);
                values[i] = Integer.toString(allele);
            }
        }
        String staged = String.join("|", values);
        return new Call(original, staged, alleles, represented, homozygous);
    }
    private static BufferedWriter writer(Path destination, long bound) throws IOException {
        OutputStream file = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            return new OwnedWriter(new OutputStreamWriter(
                    new BlockCompressedOutputStream(new BoundedOutput(file, bound), (Path)null), StandardCharsets.UTF_8), file);
        } catch (RuntimeException failure) {
            try { file.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }
    /** HTSJDK can throw before its close reaches the raw stream; always close our owned file. */
    private static final class OwnedWriter extends BufferedWriter {
        final OutputStream file;
        OwnedWriter(java.io.Writer writer, OutputStream file) { super(writer); this.file = file; }
        @Override public void close() throws IOException {
            Throwable failure = null;
            try { super.close(); }
            catch (IOException | RuntimeException caught) { failure = caught; throw caught; }
            finally {
                try { file.close(); }
                catch (IOException closeFailure) {
                    if (failure != null) failure.addSuppressed(closeFailure); else throw closeFailure;
                }
            }
        }
    }
    private static final class BoundedOutput extends OutputStream {
        final OutputStream output; final long maximum; long bytes;
        BoundedOutput(OutputStream output, long maximum) { this.output = output; this.maximum = maximum; }
        private void count(int value) throws IOException {
            bytes = Math.addExact(bytes, value);
            if (bytes > maximum) throw new IOException("Staged BGZF byte bound exceeded");
        }
        @Override public void write(int value) throws IOException { count(1); output.write(value); }
        @Override public void write(byte[] data, int start, int length) throws IOException { count(length); output.write(data, start, length); }
        @Override public void flush() throws IOException { output.flush(); }
        @Override public void close() throws IOException { output.close(); }
    }
    private static final class MutableContig {
        final List<Integer> ploidies; long variants, selected;
        MutableContig(List<Integer> ploidies) { this.ploidies = List.copyOf(ploidies); }
    }
    private static final class Counts {
        final MessageDigest identity = digest(), matrix = digest();
        long variants, cells, copies, observed, missing, full, partial, representations, homozygotes, stagedChanges;
        Counts(List<String> columns) {
            update(identity, "Minimac-identity-phase-v1\nSAMPLE\n");
            for (String column : columns) update(identity, column + "\n");
            update(matrix, "Minimac-ordered-haplotypes-v1\n");
        }
        void add(Row row) {
            String key = row.key() + "\t"; update(identity, key + row.id + "\t"); update(matrix, key);
            variants++; cells = Math.addExact(cells, row.calls.size());
            for (Call call : row.calls) {
                int absent = 0;
                for (int allele : call.alleles) {
                    byte value = (byte)(allele < 0 ? '.' : '0' + allele);
                    identity.update(value); matrix.update(value); copies++;
                    if (allele < 0) { missing++; absent++; } else observed++;
                }
                if (absent == call.alleles.size()) full++; else if (absent > 0) partial++;
                if (call.missingRepresentationChanged) representations++;
                if (call.homozygousPhaseRepresentationChanged) homozygotes++;
                if (!call.original.equals(call.staged)) stagedChanges++;
                identity.update((byte)'\t');
            }
            identity.update((byte)'\n'); matrix.update((byte)'\n');
        }
        Inventory finish() { return new Inventory(variants, cells, copies, observed, missing, full, partial, representations, homozygotes, stagedChanges,
                HexFormat.of().formatHex(identity.digest()), HexFormat.of().formatHex(matrix.digest())); }
    }
    static void regular(Path path, long bound) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS) || !absolute.toRealPath().equals(absolute)
                || Files.size(absolute) > bound) throw new IOException("Missing, linked or oversized Minimac input: " + path);
    }
    private static void update(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.UTF_8)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static int integer(String value, int min, int max, Lines lines) throws IOException {
        try {
            if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9'))
                throw lines.error("Require an exact unsigned integer");
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw lines.error("VCF/identity integer out of range");
            return number;
        } catch (NumberFormatException failure) { throw lines.error("VCF/identity integer exceeds supported capacity"); }
    }
    private static void token(String value, Lines lines) throws IOException {
        if (value.isEmpty() || value.length() > 8192
                || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
            throw lines.error("Invalid VCF/identity token");
    }
    private static void contigToken(String value, Lines lines) throws IOException {
        token(value, lines);
        if (value.indexOf(',') >= 0 || value.indexOf('<') >= 0 || value.indexOf('>') >= 0
                || value.indexOf('"') >= 0 || value.indexOf('=') >= 0 || value.indexOf('\\') >= 0)
            throw lines.error("Contig cannot be represented safely in staged VCF metadata");
    }
    private static void allele(String value, Lines lines) throws IOException {
        if (!value.matches("[ACGTNacgtn]+")) throw lines.error("Require complete literal single-ALT DNA alleles; no normalization is inferred");
    }
    static final class Lines implements AutoCloseable {
        final Reader reader; final boolean gzip; final Limits limits;
        final char[] buffer = new char[8192]; int index, size; long line, characters, crlf; boolean finalLf;
        Lines(Path path, Limits limits) throws IOException {
            this.limits = limits;
            var raw = new BufferedInputStream(Files.newInputStream(path)); boolean good = false;
            try {
                raw.mark(2); int first = raw.read(), second = raw.read(); raw.reset();
                gzip = first == 31 && second == 139;
                reader = new InputStreamReader(gzip ? new GZIPInputStream(raw) : raw,
                        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
                good = true;
            } finally { if (!good) raw.close(); }
        }
        String next() throws IOException {
            if (size < 0) return null;
            var value = new StringBuilder(); boolean lf = false;
            for (;;) {
                if (index == size) {
                    try { size = reader.read(buffer); }
                    catch (IOException failure) { throw new IOException("Minimac input line " + (line + 1) + ": invalid UTF-8/compressed stream", failure); }
                    index = 0; if (size < 0) break; if (size == 0) continue;
                }
                char c = buffer[index++];
                if (++characters > limits.decodedCharacters)
                    throw new IOException("Minimac input line " + (line + 1) + ": decoded-character bound exceeded");
                if (c == '\n') { lf = true; break; }
                if (value.length() >= limits.lineCharacters || Character.isISOControl(c) && c != '\r' && c != '\t')
                    throw new IOException("Minimac input line " + (line + 1) + ": control character or line bound exceeded");
                value.append(c);
            }
            if (!lf && value.isEmpty()) return null;
            line++; finalLf = lf;
            if (!value.isEmpty() && value.charAt(value.length() - 1) == '\r') {
                value.setLength(value.length() - 1); if (lf) crlf++;
            }
            if (value.indexOf("\r") >= 0) throw error("Embedded carriage return");
            return value.toString();
        }
        IOException error(String message) { return new IOException("Minimac input line " + line + ": " + message); }
        @Override public void close() throws IOException { reader.close(); }
    }
}

