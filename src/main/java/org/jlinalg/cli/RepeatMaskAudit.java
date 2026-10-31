/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jlinalg.genetics.RepeatAnnotations;

/** Complete streaming mask equality with exact query joins and an explicit row population. */
final class RepeatMaskAudit {
    private RepeatMaskAudit() { }
    enum Choice { ALL, EXACT }
    enum MarkedOverlap { INCLUDE, EXCLUDE }
    record Selection(Choice classes, Set<String> classNames, Choice names, Set<String> repeatNames,
            MarkedOverlap markedOverlap) {
        Selection {
            Objects.requireNonNull(classes); Objects.requireNonNull(names); Objects.requireNonNull(markedOverlap);
            classNames = Set.copyOf(classNames); repeatNames = Set.copyOf(repeatNames);
            if ((classes == Choice.ALL) != classNames.isEmpty() || (names == Choice.ALL) != repeatNames.isEmpty())
                throw new IllegalArgumentException("ALL uses an empty label set; EXACT requires explicit labels");
            for (String value : classNames) label(value);
            for (String value : repeatNames) label(value);
        }
        static Selection all() { return new Selection(Choice.ALL, Set.of(), Choice.ALL, Set.of(), MarkedOverlap.INCLUDE); }
        boolean accepts(RepeatAnnotations.Hit hit) {
            return (classes == Choice.ALL || classNames.contains(hit.repeatClass()))
                    && (names == Choice.ALL || repeatNames.contains(hit.repeatName()))
                    && (markedOverlap == MarkedOverlap.INCLUDE || !hit.markedOverlap());
        }
    }
    record Sequence(String query, long length, long coveredBases, long changedCharacters) { }
    record Report(RepeatFasta.Census original, RepeatFasta.Census masked, long annotationRows,
            long selectedRows, long unionIntervals, long comparedBases, long coveredBases,
            long changedCharacters, long estimatedIndexBytes, List<Sequence> sequences) {
        Report { sequences = List.copyOf(sequences); }
    }
    private record Span(long start, long end) { }
    private record Index(Map<String, List<Span>> spans, long selected, long intervals, long bytes) { }
    record Validation(long annotationRows, long selectedRows, long unionIntervals, long estimatedIndexBytes) { }

    static Validation validate(List<RepeatAnnotations.Hit> hits, Selection selection, RepeatFasta.Census fasta,
            long indexBytes, Runnable check) throws IOException {
        if (indexBytes < 1) throw new IllegalArgumentException("Require a positive annotation-index capacity");
        Index result = index(hits, selection, fasta, indexBytes, check);
        return new Validation(hits.size(), result.selected(), result.intervals(), result.bytes());
    }

    static Report compare(Path original, RepeatFasta.Encoding originalEncoding, Path masked,
            RepeatFasta.Encoding maskedEncoding, List<RepeatAnnotations.Hit> hits, Selection selection,
            RepeatAnnotations.Mask mode, RepeatFasta.Limits limits, long indexBytes, Runnable check) throws IOException {
        Objects.requireNonNull(selection); Objects.requireNonNull(mode); Objects.requireNonNull(check);
        if (indexBytes < 1) throw new IllegalArgumentException("Require a positive annotation-index capacity");
        RepeatFasta.Census before = RepeatFasta.read(original, originalEncoding, limits, check);
        Index index = index(hits, selection, before, indexBytes, check);
        var sequences = new ArrayList<Sequence>(); long total = 0, covered = 0, changed = 0;
        try (var source = RepeatFasta.open(original, originalEncoding, limits, check);
             var observed = RepeatFasta.open(masked, maskedEncoding, limits, check)) {
            while (source.nextSequence()) {
                if (!observed.nextSequence() || !source.id().equals(observed.id()) || !source.header().equals(observed.header()))
                    throw new IOException("Masked FASTA header/query population differs at ordinal " + source.ordinal());
                List<Span> spans = index.spans().getOrDefault(source.id(), List.of());
                int interval = 0; long position = 0, queryCovered = 0, queryChanged = 0; int value;
                while ((value = source.base()) >= 0) {
                    if ((position & 65535) == 0) check.run();
                    while (interval < spans.size() && position >= spans.get(interval).end()) interval++;
                    boolean selected = interval < spans.size() && position >= spans.get(interval).start();
                    int expected = value;
                    if (selected) {
                        queryCovered++;
                        expected = mode == RepeatAnnotations.Mask.N ? 'N' : mode == RepeatAnnotations.Mask.X ? 'X'
                                : value >= 'A' && value <= 'Z' ? value + 32 : value;
                    }
                    int actual = observed.base();
                    if (actual != expected) throw new IOException("Complete native mask differs at " + source.id() + ":" + position
                            + " (zero-based): expected " + (char)expected + ", observed " + (actual < 0 ? "EOF" : Character.toString((char)actual)));
                    if (expected != value) queryChanged++;
                    position++;
                }
                if (observed.base() >= 0) throw new IOException("Masked FASTA sequence has extra bases: " + source.id());
                sequences.add(new Sequence(source.id(), position, queryCovered, queryChanged));
                total = Math.addExact(total, position); covered = Math.addExact(covered, queryCovered); changed = Math.addExact(changed, queryChanged);
            }
            if (observed.nextSequence()) throw new IOException("Masked FASTA has extra query records");
            RepeatFasta.Census after = source.census(), mask = observed.census();
            if (!before.equals(after)) throw new IOException("Original FASTA changed between complete census and mask audit");
            return new Report(after, mask, hits.size(), index.selected(), index.intervals(), total, covered, changed, index.bytes(), sequences);
        }
    }
    /** Write a NEW native mask, then independently reread and audit its complete population. */
    static Report write(Path original, RepeatFasta.Encoding encoding, Path suppliedOutput,
            List<RepeatAnnotations.Hit> hits, Selection selection, RepeatAnnotations.Mask mode,
            RepeatFasta.Limits limits, long indexBytes, long outputBytes, Runnable check) throws IOException {
        Objects.requireNonNull(mode); Objects.requireNonNull(check); Objects.requireNonNull(selection);
        if (indexBytes < 1 || outputBytes < 1) throw new IllegalArgumentException("Require positive native mask output/index capacities");
        Path output = suppliedOutput.toAbsolutePath().normalize();
        if (output.getParent() == null || !output.getParent().toRealPath().equals(output.getParent()))
            throw new IOException("Native mask requires a canonical existing parent and a NEW output file");
        RepeatFasta.Census before = RepeatFasta.read(original, encoding, limits, check);
        generate(original, encoding, output, hits, selection, mode, limits, indexBytes, outputBytes, before, check);
        var auditLimits = new RepeatFasta.Limits(Math.max(limits.inputBytes(), outputBytes),
                Math.max(limits.decodedCharacters(), outputBytes), limits.residues(), limits.perSequence(),
                limits.sequences(), limits.lineCharacters(), limits.headerCharacters(), limits.retainedBytes());
        Report report = compare(original, encoding, output, RepeatFasta.Encoding.PLAIN_UTF8,
                hits, selection, mode, auditLimits, indexBytes, check);
        if (!report.original().equals(before)
                || !RepeatInputs.hash(original, limits.inputBytes(), check).equals(before.sourceSha256())
                || !RepeatInputs.hash(output, outputBytes, check).equals(report.masked().sourceSha256()))
            throw new IOException("Native mask input/output changed across complete generation and audit");
        return report;
    }
    private static void generate(Path original, RepeatFasta.Encoding encoding, Path output,
            List<RepeatAnnotations.Hit> hits, Selection selection, RepeatAnnotations.Mask mode,
            RepeatFasta.Limits limits, long indexBytes, long outputBytes, RepeatFasta.Census before,
            Runnable check) throws IOException {
        Index index = index(hits, selection, before, indexBytes, check);
        try (var source = RepeatFasta.open(original, encoding, limits, check);
             var stream = new FilterOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                 private long bytes;
                 private void used(int count) throws IOException {
                     check.run(); bytes = Math.addExact(bytes, count);
                     if (bytes > outputBytes) throw new IOException("Native mask complete-output byte capacity exceeded;retain the partial file and retry to a NEW path");
                 }
                 @Override public void write(int value) throws IOException { used(1); out.write(value); }
                 @Override public void write(byte[] values, int offset, int length) throws IOException { used(length); out.write(values, offset, length); }
             };
             var writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            while (source.nextSequence()) {
                writer.write('>'); writer.write(source.header()); writer.write('\n');
                List<Span> spans = index.spans().getOrDefault(source.id(), List.of());
                int interval = 0, column = 0, value; long position = 0;
                while ((value = source.base()) >= 0) {
                    if ((position & 65535) == 0) check.run();
                    while (interval < spans.size() && position >= spans.get(interval).end()) interval++;
                    if (interval < spans.size() && position >= spans.get(interval).start())
                        value = mode == RepeatAnnotations.Mask.N ? 'N' : mode == RepeatAnnotations.Mask.X ? 'X'
                                : value >= 'A' && value <= 'Z' ? value + 32 : value;
                    writer.write(value); position++;
                    if (++column == Math.min(60, limits.lineCharacters())) { writer.write('\n'); column = 0; }
                }
                if (column != 0) writer.write('\n');
            }
            if (!before.equals(source.census())) throw new IOException("Source FASTA changed during complete native mask generation");
        }
    }
    private static Index index(List<RepeatAnnotations.Hit> hits, Selection selection, RepeatFasta.Census fasta,
            long maximum, Runnable check) throws IOException {
        long bytes;
        try { bytes = Math.addExact(65536L, Math.addExact(Math.multiplyExact(128L, hits.size()), Math.multiplyExact(512L, fasta.sequences().size()))); }
        catch (ArithmeticException overflow) { throw new IOException("Annotation-index storage estimate overflow", overflow); }
        if (bytes > maximum) throw new IOException("Estimated repeat interval-index capacity exceeded");
        var queries = new LinkedHashMap<String, RepeatFasta.Sequence>();
        for (var sequence : fasta.sequences()) queries.put(sequence.id(), sequence);
        var groups = new LinkedHashMap<String, List<Span>>(); long selected = 0;
        for (var hit : hits) {
            check.run(); var query = queries.get(hit.query());
            // Bounds and identity apply even to rows excluded by the explicit class/name filter.
            if (query == null || hit.end() > query.length()
                    || hit.queryLengthHint() != null && hit.queryLengthHint() != query.length())
                throw new IOException("Repeat annotation has an unknown query, excess coordinate or inconsistent source length hint: " + hit.query());
            if (selection.accepts(hit)) {
                groups.computeIfAbsent(hit.query(), unused -> new ArrayList<>()).add(new Span(hit.start(), hit.end())); selected++;
            }
        }
        var merged = new LinkedHashMap<String, List<Span>>(); long intervals = 0;
        for (var entry : groups.entrySet()) {
            List<Span> values = entry.getValue();
            values.sort(Comparator.comparingLong(Span::start).thenComparingLong(Span::end));
            var union = new ArrayList<Span>(); long start = -1, end = -1;
            for (Span span : values) {
                check.run();
                if (start < 0) { start = span.start(); end = span.end(); }
                else if (span.start() <= end) end = Math.max(end, span.end());
                else { union.add(new Span(start, end)); start = span.start(); end = span.end(); }
            }
            union.add(new Span(start, end)); intervals = Math.addExact(intervals, union.size());
            merged.put(entry.getKey(), List.copyOf(union));
        }
        return new Index(Map.copyOf(merged), selected, intervals, bytes);
    }
    private static void label(String value) {
        if (value.isBlank() || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Require a nonempty exact repeat class/name label without controls");
    }
}
