/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-specific ChIP-seq2.0.0 samplesheet inspection. Original input is never rewritten.
 * Mirrors the locked upstream MIT check_samplesheet.py contract, including its explicit
 * normalization, ignored extra cells, Python scalar sorting and technical-run suffixes.
 */
public final class NfcoreChipseq200Mappings {
    private NfcoreChipseq200Mappings() { }
    private static final List<String> INPUT = List.of("sample", "fastq_1", "fastq_2", "antibody", "control");
    private static final String OUTPUT = "sample,single_end,fastq_1,fastq_2,antibody,control";

    public record Limits(int rows, int lineCharacters, long totalCharacters) {
        public Limits { if (rows < 1 || lineCharacters < 1 || totalCharacters < 1) throw new IllegalArgumentException("Require positive complete samplesheet capacities"); }
        public static Limits defaults() { return new Limits(100000, 1048576, 67108864); }
    }
    /** Every source cell is retained, including additional cells the original script ignores. */
    public record SourceRow(int line, String rawLine, List<String> cells, List<String> effectiveCells) {
        public SourceRow { cells = List.copyOf(cells); effectiveCells = List.copyOf(effectiveCells); }
    }
    public record Normalization(int line, String field, String originalCell, String sourceValue, boolean scientificIdentifier) { }
    public record Read(String id, String libraryId, boolean singleEnd, String read1, String read2,
                       String antibody, String controlLibraryId, int sourceLine) {
        /** Original @RG ID and LB are technical-run IDs; SM is the biological library ID. */
        public String readGroupId() { return id; }
        public String readGroupLibrary() { return id; }
        public String readGroupSample() { return libraryId; }
    }
    public record Plan(List<String> inputHeader, List<SourceRow> sourceRows, List<Read> reads,
                       List<Normalization> normalizations, List<String> warnings) {
        public Plan { inputHeader = List.copyOf(inputHeader); sourceRows = List.copyOf(sourceRows); reads = List.copyOf(reads); normalizations = List.copyOf(normalizations); warnings = List.copyOf(warnings); }
        /** Explicit analysis policy; inspecting original behavior alone does not authorize ID changes. */
        public void requireLiteralIdentifiers() throws IOException {
            for (var change : normalizations) if (change.scientificIdentifier()) throw new IOException("Original source would change scientific identifier at line " + change.line() + ", field " + change.field() + ". Original cell and effective value remain in the normalization ledger; no input was repaired.");
        }
        /** The upstream checker permits discordant metadata across technical runs; do not pool it. */
        public void requireConsistentLibraryMetadata() throws IOException {
            var groups = new LinkedHashMap<String, List<String>>();
            for (var row : reads) { var values = List.of(row.antibody(), row.controlLibraryId()); var previous = groups.putIfAbsent(row.libraryId(), values); if (previous != null && !previous.equals(values)) throw new IOException("Original checker permits conflicting antibody/control metadata across technical runs for " + row.libraryId() + "; no unique downstream library graph is admitted"); }
        }
        /** Exact source-emitted cells/order; this does not modify or replace the original input. */
        public String sourceCheckedCsv() {
            var result = new StringBuilder(OUTPUT).append('\n');
            for (var row : reads) result.append(String.join(",", row.id(), row.singleEnd() ? "1" : "0", row.read1(), row.read2(), row.antibody(), row.controlLibraryId())).append('\n');
            return result.toString();
        }
    }
    private record Entry(SourceRow original, String sample, boolean single, String first, String second, String antibody, String control) {
        List<String> duplicateKey() { return List.of(single ? "1" : "0", first, second, antibody, control); }
    }
    public static Plan inspect(Reader input, Limits limits) throws IOException {
        var lines = new Lines(input, limits); String first = lines.next(); if (first == null) throw new IOException("Original samplesheet header is absent");
        unicode(first); if (first.startsWith("\ufeff")) first = first.substring(1); // Original Python UTF-8-sig decoder removes one initial BOM.
        var header = new ArrayList<String>(); for (String cell : strip(first).split(",", -1)) header.add(quotes(cell));
        if (header.size() < INPUT.size() || !header.subList(0, INPUT.size()).equals(INPUT)) throw new IOException("Original ChIP-seq2.0.0 samplesheet header differs");
        var sourceRows = new ArrayList<SourceRow>(); var normalizations = new ArrayList<Normalization>(); var warnings = new ArrayList<String>(); var samples = new LinkedHashMap<String, List<Entry>>();
        if (header.size() > INPUT.size()) warnings.add("Original source ignores extra header columns; all original cells remain retained.");
        String line; int number = 1;
        while ((line = lines.next()) != null) {
            if (sourceRows.size() >= limits.rows()) throw new IOException("Complete samplesheet exceeds row capacity"); number++; unicode(line);
            List<String> raw = List.of(line.split(",", -1)); String[] split = strip(line).split(",", -1); var cells = new ArrayList<String>(); for (String cell : split) cells.add(quotes(strip(cell)));
            if (cells.size() < INPUT.size() || cells.stream().filter(c -> !c.isEmpty()).count() < 2) throw new IOException("Original samplesheet has too few cells/populated columns at line " + number);
            String sample = cells.get(0), r1 = cells.get(1), r2 = cells.get(2), antibody = cells.get(3), control = cells.get(4);
            sample = sample.replace(' ', '_'); antibody = antibody.replace(' ', '_'); control = control.replace(' ', '_');
            if (sample.isEmpty()) throw new IOException("Original sample identifier is absent at line " + number);
            for (String read : List.of(r1, r2)) if (!read.isEmpty() && (read.indexOf(' ') >= 0 || !read.endsWith(".fastq.gz") && !read.endsWith(".fq.gz"))) throw new IOException("Original FastQ path contains spaces or has an unsupported literal extension at line " + number);
            if (antibody.isEmpty() != control.isEmpty()) throw new IOException("Original antibody/control must both be specified at line " + number);
            if (r1.isEmpty()) throw new IOException("Invalid original single/paired input combination at line " + number);
            var effective = new ArrayList<>(cells); effective.set(0, sample); effective.set(3, antibody); effective.set(4, control);
            var source = new SourceRow(number, line, raw, effective); sourceRows.add(source);
            for (int i = 0; i < INPUT.size(); i++) if (!raw.get(i).equals(effective.get(i))) normalizations.add(new Normalization(number, INPUT.get(i), raw.get(i), effective.get(i), i == 0 || i == 3 || i == 4));
            if (cells.size() > INPUT.size()) warnings.add("Line " + number + ": original source ignores " + (cells.size() - INPUT.size()) + " additional cells; retained in sourceRows.");
            var entry = new Entry(source, sample, r2.isEmpty(), r1, r2, antibody, control); var group = samples.computeIfAbsent(sample, ignored -> new ArrayList<>());
            for (var earlier : group) if (earlier.duplicateKey().equals(entry.duplicateKey())) throw new IOException("Original samplesheet contains duplicate rows after source normalization, line " + number);
            group.add(entry);
        }
        if (samples.isEmpty()) throw new IOException("Original samplesheet contains no entries");
        var ordered = new ArrayList<>(samples.keySet()); ordered.sort(NfcoreChipseq200Mappings::compareText); var reads = new ArrayList<Read>();
        for (String sample : ordered) {
            var group = samples.get(sample); boolean single = group.get(0).single(); int index = 0;
            for (var entry : group) {
                if (entry.single() != single) throw new IOException("Original technical runs mix single/paired input for " + sample);
                if (!entry.control().isEmpty() && !samples.containsKey(entry.control())) throw new IOException("Original control identifier does not match a provided normalized sample: " + entry.control());
                reads.add(new Read(sample + "_T" + ++index, sample, single, entry.first(), entry.second(), entry.antibody(), entry.control(), entry.original().line()));
            }
        }
        if (!normalizations.isEmpty()) warnings.add("Original source applies cell/identifier normalization; inspect every original/effective ledger entry. Input bytes have not been rewritten. Analysis requiring literal scientific IDs must call requireLiteralIdentifiers().");
        return new Plan(header, sourceRows, reads, normalizations, warnings);
    }
    public static void auditChecked(Reader originalChecked, Plan expected, Limits limits) throws IOException {
        if (expected.reads().size() > limits.rows()) throw new IOException("Complete checked samplesheet exceeds row capacity");
        var lines = new Lines(originalChecked, limits); if (!OUTPUT.equals(lines.next())) throw new IOException("Original checked samplesheet header differs");
        for (var row : expected.reads()) {
            String actual = lines.next(); String wanted = String.join(",", row.id(), row.singleEnd() ? "1" : "0", row.read1(), row.read2(), row.antibody(), row.controlLibraryId());
            if (!wanted.equals(actual)) throw new IOException("Complete original checked samplesheet row/order/technical ID differs at " + row.id());
        }
        if (lines.next() != null) throw new IOException("Original checked samplesheet has unknown extra rows");
    }
    private static int compareText(String a, String b) { var first = a.codePoints().iterator(); var second = b.codePoints().iterator(); while (first.hasNext() && second.hasNext()) { int value = Integer.compare(first.nextInt(), second.nextInt()); if (value != 0) return value; } return Boolean.compare(first.hasNext(), second.hasNext()); }
    private static String quotes(String value) { int start = 0, end = value.length(); while (start < end && value.charAt(start) == '"') start++; while (end > start && value.charAt(end - 1) == '"') end--; return value.substring(start, end); }
    private static String strip(String value) { int start = 0, end = value.length(); while (start < end && space(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start)); while (end > start && space(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end)); return value.substring(start, end); }
    private static boolean space(int c) { return c >= 9 && c <= 13 || c >= 28 && c <= 32 || c == 133 || c == 160 || c == 5760 || c >= 8192 && c <= 8202 || c == 8232 || c == 8233 || c == 8239 || c == 8287 || c == 12288; }
    private static void unicode(String value) throws IOException { for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (Character.isHighSurrogate(c)) { if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IOException("Unpaired source Unicode surrogate"); } else if (Character.isLowSurrogate(c)) throw new IOException("Unpaired source Unicode surrogate"); } }
    private static final class Lines {
        private final Reader source; private final Limits limits; private int pending = -1; private long count;
        Lines(Reader source, Limits limits) { this.source = source instanceof java.io.BufferedReader ? source : new java.io.BufferedReader(source); this.limits = limits; }
        private int character() throws IOException { if (pending >= 0) { int c = pending; pending = -1; return c; } int c = source.read(); if (c >= 0 && ++count > limits.totalCharacters()) throw new IOException("Complete samplesheet exceeds character capacity"); return c; }
        String next() throws IOException { var line = new StringBuilder(); boolean seen = false; for (;;) { int c = character(); if (c < 0) return seen ? line.toString() : null; seen = true; if (c == '\n') return line.toString(); if (c == '\r') { int next = character(); if (next >= 0 && next != '\n') pending = next; return line.toString(); } if (line.length() >= limits.lineCharacters()) throw new IOException("Complete samplesheet exceeds line capacity"); line.append((char) c); } }
    }
}
