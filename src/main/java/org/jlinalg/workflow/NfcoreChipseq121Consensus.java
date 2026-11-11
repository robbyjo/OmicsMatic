/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Native original ChIP-seq1.2.1 consensus: C/C.UTF-8 sort, bookended merge,
 * then the original macs2_merged_expand.py group/replicate filter.
 * Original coordinates, peak occurrences and per-sample value order are retained.
 */
public final class NfcoreChipseq121Consensus {
    private NfcoreChipseq121Consensus() { }
    public enum Kind { BROAD, NARROW }
    public record Limits(int peaks, int samples, int lineCharacters, long decodedCharacters) {
        public Limits {
            if (peaks < 1 || peaks > 10000000 || samples < 1 || samples > 100000
                    || lineCharacters < 1 || lineCharacters > 2000000 || decodedCharacters < 1 || decodedCharacters > 2000000000L)
                throw new IllegalArgumentException("Require bounded positive complete-consensus capacities");
        }
        public static Limits defaults() { return new Limits(1000000, 10000, 1048576, 67108864); }
    }
    /** Raw source fields stay available; only fields actually used by the source computation are interpreted. */
    public record Peak(String contig, long start, long end, String name, double fold,
                       double negativeLogP, double negativeLogQ, Long summit, String sourceLine) {
        public Peak {
            literal(contig); literal(name); Objects.requireNonNull(sourceLine);
            if (start < 0 || end <= start || !Double.isFinite(fold) || !Double.isFinite(negativeLogP) || !Double.isFinite(negativeLogQ))
                throw new IllegalArgumentException("Require nonempty peak geometry and finite original values; no nonfinite value is repaired");
        }
        /** Exact original name.split('_')[:-2] relation. */
        public String sample() {
            int last = name.lastIndexOf('_'); if (last < 0) return "";
            int prior = name.lastIndexOf('_', last - 1); return prior < 0 ? "" : name.substring(0, prior);
        }
    }
    public record PeakSet(String sample, List<Peak> peaks) {
        public PeakSet {
            literal(sample); peaks = List.copyOf(peaks);
            for (Peak peak : peaks) if (!peak.sample().equals(sample))
                throw new IllegalArgumentException("Peak-name sample differs from its declared original file; no identifier inferred or repaired");
        }
    }
    public record Row(String contig, long start, long end, String id, int sourcePeakCount,
                      Map<String, List<Peak>> qualifyingPeaks) {
        public Row {
            var copy = new LinkedHashMap<String, List<Peak>>(); qualifyingPeaks.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            qualifyingPeaks = Collections.unmodifiableMap(copy);
        }
    }
    public record Combination(List<String> samples, long count) {
        public Combination { samples = List.copyOf(samples); }
    }
    public record Plan(Kind kind, List<String> samples, int minimumReplicates, int sourcePeaks,
                       int mergedBeforeFiltering, List<Row> rows, List<Combination> intersections) {
        public Plan { samples = List.copyOf(samples); rows = List.copyOf(rows); intersections = List.copyOf(intersections); }
        public Map<String, Object> document() {
            return Map.of("kind", kind.name(), "samples", samples, "minimum_replicates_per_source_group", minimumReplicates,
                "source_peak_occurrences", sourcePeaks, "merged_before_filtering", mergedBeforeFiltering,
                "retained_consensus_intervals", rows.size(), "membership_combinations", intersections.size(),
                "merge_before_group_filter", true, "bookended_intervals_merge", true,
                "SAF_start_is_unchanged_source_BED_start", true);
        }
    }
    /** Source sort has C/C.UTF-8 collation; Python sorts sample names by Unicode scalar value. */
    private static int compareText(String first, String second) {
        var a = first.codePoints().iterator(); var b = second.codePoints().iterator();
        while (a.hasNext() && b.hasNext()) { int c = Integer.compare(a.nextInt(), b.nextInt()); if (c != 0) return c; }
        return Boolean.compare(a.hasNext(), b.hasNext());
    }
    private static int compareSamples(List<String> a, List<String> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) { int c = compareText(a.get(i), b.get(i)); if (c != 0) return c; }
        return Integer.compare(a.size(), b.size());
    }
    /** Strict complete input; caller owns Reader. Empty individual peak sets are retained. */
    public static List<Peak> read(Reader source, Kind kind, Limits limits) throws IOException {
        Objects.requireNonNull(kind); var input = new Lines(source, limits); var peaks = new ArrayList<Peak>();
        for (String line; (line = input.next()) != null;) {
            if (peaks.size() >= limits.peaks()) throw new IOException("Complete original peak population exceeds declared capacity");
            String[] f = line.split("\t", -1);
            if (f.length != (kind == Kind.BROAD ? 9 : 10)) throw new IOException("Require the exact original broadPeak/narrowPeak field count");
            try {
                Peak peak = new Peak(f[0], integer(f[1]), integer(f[2]), f[3], finite(f[6]), finite(f[7]), finite(f[8]),
                    kind == Kind.NARROW ? signedInteger(f[9]) : null, line);
                peaks.add(peak);
            } catch (IllegalArgumentException invalid) { throw new IOException("Invalid complete source peak; values retained without repair: " + invalid.getMessage(), invalid); }
        }
        return List.copyOf(peaks);
    }
    public static List<Peak> read(Path source, Kind kind, Limits limits) throws IOException {
        try (var input = Files.newBufferedReader(source, StandardCharsets.UTF_8)) { return read(input, kind, limits); }
    }
    /** Compute the complete consensus. The caller explicitly selects original C/C.UTF-8 ordering. */
    public static Plan compute(List<PeakSet> inputs, Kind kind, int minimumReplicates, Limits limits) throws IOException {
        Objects.requireNonNull(kind); Objects.requireNonNull(limits);
        if (inputs.isEmpty() || inputs.size() > limits.samples() || minimumReplicates < 1)
            throw new IOException("Require complete declared peak sets and a positive original replicate threshold");
        var samples = new ArrayList<String>(); var all = new ArrayList<Peak>(); var seen = new HashSet<String>(); long characters = 0;
        for (PeakSet set : inputs) {
            if (!seen.add(set.sample())) throw new IOException("Duplicate declared sample would create ambiguous original output columns");
            samples.add(set.sample());
            for (Peak peak : set.peaks()) {
                if ((peak.summit() != null) != (kind == Kind.NARROW)) throw new IOException("Peak kind differs from complete declared recipe");
                if (peak.sourceLine().length() > limits.lineCharacters() || (characters = Math.addExact(characters, peak.sourceLine().length() + 1L)) > limits.decodedCharacters()
                        || all.size() >= limits.peaks()) throw new IOException("Complete consensus input capacity exceeded; no prefix returned");
                all.add(peak);
            }
        }
        samples.sort(NfcoreChipseq121Consensus::compareText);
        // GNU sort's final whole-line tie break is essential for the order of collapsed values.
        all.sort(Comparator.comparing(Peak::contig, NfcoreChipseq121Consensus::compareText)
            .thenComparingLong(Peak::start).thenComparing(Peak::sourceLine, NfcoreChipseq121Consensus::compareText));
        var rows = new ArrayList<Row>(); var frequencies = new LinkedHashMap<List<String>, Long>(); int merged = 0;
        for (int from = 0; from < all.size();) {
            Peak first = all.get(from); long end = first.end(); int to = from + 1;
            while (to < all.size() && first.contig().equals(all.get(to).contig()) && all.get(to).start() <= end) {
                end = Math.max(end, all.get(to).end()); to++;
            }
            merged++;
            var groupSamples = new LinkedHashMap<String, HashSet<String>>();
            for (int i = from; i < to; i++) {
                String sample = all.get(i).sample(); int split = sample.lastIndexOf('_'); String group = split < 0 ? "" : sample.substring(0, split);
                groupSamples.computeIfAbsent(group, key -> new HashSet<>()).add(sample);
            }
            var qualified = new HashSet<String>();
            for (var group : groupSamples.values()) if (group.size() >= minimumReplicates) qualified.addAll(group);
            if (!qualified.isEmpty()) {
                var selected = new LinkedHashMap<String, List<Peak>>();
                for (String sample : samples) if (qualified.contains(sample)) selected.put(sample, new ArrayList<>());
                for (int i = from; i < to; i++) if (qualified.contains(all.get(i).sample())) selected.get(all.get(i).sample()).add(all.get(i));
                rows.add(new Row(first.contig(), first.start(), end, "Interval_" + (rows.size() + 1), to - from, selected));
                frequencies.merge(List.copyOf(selected.keySet()), 1L, Long::sum);
            }
            from = to;
        }
        var combinations = new ArrayList<Combination>(); frequencies.forEach((k, v) -> combinations.add(new Combination(k, v)));
        combinations.sort((a, b) -> { int c = Long.compare(b.count(), a.count()); return c != 0 ? c : compareSamples(b.samples(), a.samples()); });
        return new Plan(kind, samples, minimumReplicates, all.size(), merged, rows, combinations);
    }
    /** Compare every original boolean row and value to the native plan; raw source numeric spellings are not replaced. */
    public static void auditBoolean(Reader source, Plan plan, Limits limits) throws IOException {
        var input = new Lines(source, limits); var header = new ArrayList<>(List.of("chr", "start", "end", "interval_id", "num_peaks", "num_samples"));
        var suffixes = new ArrayList<>(List.of("bool", "fc", "qval", "pval", "start", "end")); if (plan.kind() == Kind.NARROW) suffixes.add("summit");
        for (String suffix : suffixes) for (String sample : plan.samples()) header.add(sample + "." + suffix);
        if (!String.join("\t", header).equals(input.next())) throw new IOException("Original consensus boolean header/sample order differs");
        for (Row row : plan.rows()) {
            String[] fields = fields(input.next(), header.size());
            if (!fields[0].equals(row.contig()) || signedInteger(fields[1]) != row.start() || signedInteger(fields[2]) != row.end() || !fields[3].equals(row.id())
                    || signedInteger(fields[4]) != row.sourcePeakCount() || signedInteger(fields[5]) != row.qualifyingPeaks().size())
                throw new IOException("Original consensus geometry, interval ID or complete occurrence/membership count differs");
            int column = 6;
            for (String suffix : suffixes) for (String sample : plan.samples()) {
                var peaks = row.qualifyingPeaks().get(sample); String value = fields[column++];
                if (suffix.equals("bool")) { if (!value.equals(peaks == null ? "FALSE" : "TRUE")) throw new IOException("Original consensus sample membership differs"); }
                else if (peaks == null) { if (!value.equals("NA")) throw new IOException("Original absent-sample token differs; no value imputed"); }
                else {
                    String[] values = value.split(";", -1); if (values.length != peaks.size()) throw new IOException("Original collapsed occurrence count differs");
                    for (int i = 0; i < values.length; i++) {
                        Peak peak = peaks.get(i);
                        switch (suffix) {
                            case "fc", "qval", "pval" -> {
                                double expected = suffix.equals("fc") ? peak.fold() : suffix.equals("qval") ? peak.negativeLogQ() : peak.negativeLogP();
                                if (Double.doubleToLongBits(finite(values[i])) != Double.doubleToLongBits(expected)) throw new IOException("Original per-sample peak value/order differs");
                            }
                            case "start", "end", "summit" -> {
                                long expected = suffix.equals("start") ? peak.start() : suffix.equals("end") ? peak.end() : peak.summit();
                                if (signedInteger(values[i]) != expected) throw new IOException("Original per-sample coordinate/summit order differs");
                            }
                            default -> throw new IOException("Unsupported original consensus field");
                        }
                    }
                }
            }
        }
        if (input.next() != null) throw new IOException("Original consensus has extra intervals");
    }
    /** Validate the source awk BED/SAF projections. SAF start is deliberately not incremented. */
    public static void auditIntervals(Reader bed, Reader saf, Plan plan, Limits limits) throws IOException {
        var b = new Lines(bed, limits); var s = new Lines(saf, limits);
        if (!"GeneID\tChr\tStart\tEnd\tStrand".equals(s.next())) throw new IOException("Original consensus SAF header differs");
        for (Row row : plan.rows()) {
            String expectedBed = String.join("\t", row.contig(), Long.toString(row.start()), Long.toString(row.end()), row.id(), "0", "+");
            String expectedSaf = String.join("\t", row.id(), row.contig(), Long.toString(row.start()), Long.toString(row.end()), "+");
            if (!expectedBed.equals(b.next()) || !expectedSaf.equals(s.next())) throw new IOException("Original consensus BED/SAF projection differs; source coordinates were not repaired");
        }
        if (b.next() != null || s.next() != null) throw new IOException("Extra original BED/SAF rows");
    }
    public static void auditIntersections(Reader source, Plan plan, Limits limits) throws IOException {
        var input = new Lines(source, limits);
        for (Combination combination : plan.intersections()) {
            String expected = String.join("&", combination.samples()) + "\t" + combination.count();
            if (!expected.equals(input.next())) throw new IOException("Original intersection frequency or descending frequency/sample tuple order differs");
        }
        if (input.next() != null) throw new IOException("Extra original sample intersections");
    }
    private static String[] fields(String row, int count) throws IOException {
        if (row == null) throw new IOException("Missing original consensus row"); String[] fields = row.split("\t", -1);
        if (fields.length != count) throw new IOException("Original consensus field count differs"); return fields;
    }
    private static long integer(String value) throws IOException {
        if (!value.matches("[0-9]+")) throw new IOException("Require an unchanged nonnegative source coordinate"); return signedInteger(value);
    }
    private static long signedInteger(String value) throws IOException {
        try { if (!value.matches("-?[0-9]+")) throw new NumberFormatException(); return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IOException("Original coordinate/count exceeds exact supported integer representation", invalid); }
    }
    private static double finite(String value) throws IOException {
        try {
            if (!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new NumberFormatException();
            double parsed = Double.parseDouble(value); if (!Double.isFinite(parsed)) throw new NumberFormatException(); return parsed;
        } catch (NumberFormatException invalid) { throw new IOException("Original peak value is nonfinite or unassessable; retain its token without replacement: " + value, invalid); }
    }
    private static void literal(String value) {
        if (value == null || value.isEmpty() || value.indexOf(',') >= 0 || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c) || c >= 0xd800 && c <= 0xdfff))
            throw new IllegalArgumentException("Require unchanged unambiguous original peak/sample fields");
    }
    private static final class Lines {
        private final Reader input; private final Limits limits; private long characters;
        Lines(Reader source, Limits limits) { input = source instanceof BufferedReader ? source : new BufferedReader(source); this.limits = Objects.requireNonNull(limits); }
        private int read() throws IOException { int c = input.read(); if (c >= 0 && ++characters > limits.decodedCharacters()) throw new IOException("Complete consensus metadata character capacity exceeded"); return c; }
        String next() throws IOException {
            var value = new StringBuilder(); int c;
            while ((c = read()) >= 0 && c != '\n') {
                if (c == '\r') { int after = read(); if (after >= 0 && after != '\n') throw new IOException("Embedded original table carriage return"); break; }
                if (value.length() >= limits.lineCharacters() || c < 32 && c != '\t') throw new IOException("Oversized/control-containing original consensus line");
                value.append((char) c);
            }
            return c < 0 && value.isEmpty() ? null : value.toString();
        }
    }
}
