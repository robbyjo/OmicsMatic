/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Streaming independent expansion of the six-column original HOMER RNA format.
 * The selected profile reproduces analyzeRepeats.pl 5.1 -count exons: feature
 * breakpoints sort numerically, E-prefixed features include UTRs, and each part
 * ends one base before the next feature or at the transcript end. Source length
 * is end-start, rather than inclusive base count. Empty and one-base parts and
 * reference irregularities require explicit retention; no clipping occurs.
 * This parser does not quantify tags, assign gene annotations or run software.
 */
public final class HomerRnaTranscripts {
    private HomerRnaTranscripts() { }
    private static final Pattern IDENTIFIER = Pattern.compile("[^\\s\\p{Cntrl}:,]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CONTIG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Pattern TERMINAL_COPY = Pattern.compile("-HOMER[0-9]*$");

    /** Explicit handling of an original reference irregularity. */
    public enum Policy { ERROR, RETAIN_ORIGINAL }
    /** Every logical input line receives a retained disposition. */
    public enum Disposition { TRANSCRIPT, COMMENT, BLANK }

    /** Bounded streaming limits; the caller owns decoded-byte and heap budgets too. */
    public record Limits(int maximumRows, long maximumExonParts, int maximumFeaturesPerRow,
                         int maximumLineCharacters, long maximumDecodedCharacters) {
        public Limits {
            if (maximumRows < 1 || maximumRows > 10_000_000 || maximumExonParts < 1
                    || maximumExonParts > 100_000_000 || maximumFeaturesPerRow < 1
                    || maximumFeaturesPerRow > 100_000 || maximumLineCharacters < 1
                    || maximumLineCharacters > 4_000_000 || maximumDecodedCharacters < 1
                    || maximumDecodedCharacters > 1_000_000_000_000L) {
                throw new IllegalArgumentException("Invalid complete RNA reference streaming limits");
            }
        }
    }

    /** Original token order remains separate from its numerically sorted position. */
    public record Feature(int inputOrdinal, String code, int start1, int end1) {
        /** Whether analyzeRepeats.pl includes this token in -count exons. */
        public boolean isExon() { return code.startsWith("E"); }
    }

    /** One retained source exon part; effectiveLength may be zero or minus one. */
    public record Part(String id, int ordinal, Feature feature) {
        /** Original RPKM length contribution, without inclusive correction. */
        public long effectiveLength() { return (long)feature.end1() - feature.start1(); }
        /** Geometry accepted by the separate original-counter audit. */
        public HomerRnaCounts.Exon exon(char strand) {
            return new HomerRnaCounts.Exon(id, feature.start1(), feature.end1(), strand);
        }
    }

    /** Complete row identity, raw text and source-derived feature population. */
    public record Transcript(long lineNumber, String rawId, String executionId,
                             String condensedId, String contig, int start1, int end1,
                             char strand, List<Feature> features, List<Part> exons,
                             long sourceEffectiveLength, boolean missingReferenceContig,
                             boolean outsideReference, int emptyExonParts,
                             int oneBaseExonParts, String rawLine) {
        public Transcript {
            features = List.copyOf(features);
            exons = List.copyOf(exons);
        }
    }

    /** Complete resource counters; no population is truncated at a limit. */
    public record Summary(long lines, long decodedCharacters, long transcripts,
                          long comments, long blanks, long exonParts,
                          long missingReferenceRows, long outsideReferenceRows,
                          long emptyExonParts, long oneBaseExonParts,
                          long sourceEffectiveLength, int maximumExonsPerRow,
                          int distinctRawIds, int distinctCondensedIds) { }

    /** Transcript consumer; callers can spool rows without retaining the resource. */
    @FunctionalInterface public interface Sink { void accept(Transcript transcript) throws IOException; }
    /** Complete line/disposition ledger consumer, including comments and blank rows. */
    @FunctionalInterface public interface LineSink {
        void accept(long lineNumber, String rawLine, Disposition disposition) throws IOException;
    }
    /** Caller cancellation, deadline or immutable-source check. */
    @FunctionalInterface public interface Check { void run() throws IOException; }

    /**
     * Stream a complete six-column original RNA resource. Only plain integer
     * coordinates and explicit +/- or 0/1 strands are admitted by this profile.
     * IDs are bounded to 254 UTF8 bytes before repeated part-name allocation.
     * Generated execution-ID collisions fail rather than silently overwriting a
     * source hash entry. Original -HOMER suffixes remain visible and their default
     * condensed family is reported separately. Reader ownership stays with caller.
     * @param reader strict decoded input
     * @param referenceLengths exact declared dictionary, without inferred aliases
     * @param referencePolicy missing-contig or overrun handling
     * @param shortPartPolicy empty and one-base exon handling
     * @param limits complete-population bounds
     * @param sink streaming transcript consumer
     * @param lineSink complete input disposition consumer
     * @param check caller cancellation/deadline check
     * @return complete accepted resource summary
     * @throws IOException on input, limit, policy or consumer failure
     */
    public static Summary scan(Reader reader, Map<String, Long> referenceLengths,
                               Policy referencePolicy, Policy shortPartPolicy,
                               Limits limits, Sink sink, LineSink lineSink, Check check) throws IOException {
        if (reader == null || referenceLengths == null || referenceLengths.isEmpty()
                || referencePolicy == null || shortPartPolicy == null || limits == null
                || sink == null || lineSink == null || check == null) {
            throw new IllegalArgumentException("Require explicit reference, policies, consumers and limits");
        }
        var reference = new HashMap<String, Long>();
        for (var entry : referenceLengths.entrySet()) {
            if (entry.getKey() == null || !CONTIG.matcher(entry.getKey()).matches()
                    || entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 1_000_000_000) {
                throw new IllegalArgumentException("Invalid exact RNA reference dictionary");
            }
            reference.put(entry.getKey(), entry.getValue());
        }
        check.run();
        var input = new Lines(reader, limits, check);
        var copies = new HashMap<String, Integer>();
        var executionIds = new HashSet<String>();
        var condensedIds = new HashSet<String>();
        long transcripts = 0, comments = 0, blanks = 0, exonCount = 0;
        long missingRows = 0, outsideRows = 0, emptyParts = 0, oneBaseParts = 0, totalLength = 0;
        int maximumExons = 0;
        String raw;
        while ((raw = input.next()) != null) {
            check.run();
            if (input.lines > limits.maximumRows()) throw error(input.lines, "RNA reference row capacity exceeded");
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.isBlank()) {
                blanks++;
                lineSink.accept(input.lines, raw, Disposition.BLANK);
                continue;
            }
            if (line.stripLeading().startsWith("#")) {
                comments++;
                lineSink.accept(input.lines, raw, Disposition.COMMENT);
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length != 6) throw error(input.lines, "Require the six-column original RNA format");
            String rawId = fields[0], contig = fields[1];
            if (!IDENTIFIER.matcher(rawId).matches() || rawId.getBytes(StandardCharsets.UTF_8).length > 254
                    || !CONTIG.matcher(contig).matches()) {
                throw error(input.lines, "Unsupported RNA identity or contig; preserve explicit source identifiers");
            }
            int start = coordinate(fields[2], input.lines), end = coordinate(fields[3], input.lines);
            if (start < 1 || end < start) throw error(input.lines, "Invalid source transcript span");
            char strand = switch (fields[4]) {
                case "+", "0" -> '+';
                case "-", "1" -> '-';
                default -> throw error(input.lines, "Require explicit source RNA strand");
            };
            Long referenceLength = reference.get(contig);
            boolean missing = referenceLength == null;
            boolean outside = !missing && end > referenceLength;
            if ((missing || outside) && referencePolicy == Policy.ERROR) {
                throw error(input.lines, "Original RNA resource has a missing-reference contig or overrun; explicit retention required");
            }
            int copy = copies.getOrDefault(rawId, 0);
            String executionId = copy == 0 ? rawId : rawId + "-HOMER" + copy;
            copies.put(rawId, copy + 1);
            if (!executionIds.add(executionId)) {
                throw error(input.lines, "Generated original RNA execution-ID collision; complete population cannot be certified");
            }
            String condensedId = TERMINAL_COPY.matcher(executionId).replaceFirst("");
            condensedIds.add(condensedId);
            String[] tokens = fields[5].split(",", -1);
            if (tokens.length > limits.maximumFeaturesPerRow()) throw error(input.lines, "Per-transcript feature capacity exceeded");
            var breakpoints = new ArrayList<Feature>(tokens.length);
            for (int i = 0; i < tokens.length; i++) {
                if ((i & 1023) == 0) check.run();
                int colon = tokens[i].indexOf(':');
                if (colon < 1 || colon != tokens[i].lastIndexOf(':')
                        || !IDENTIFIER.matcher(tokens[i].substring(0, colon)).matches()) {
                    throw error(input.lines, "Malformed original RNA feature token");
                }
                int position = coordinate(tokens[i].substring(colon + 1), input.lines);
                if (position < start || position > end) {
                    throw error(input.lines, "Feature breakpoint lies outside its source transcript; no clipping inferred");
                }
                breakpoints.add(new Feature(i + 1, tokens[i].substring(0, colon), position, position));
            }
            // Both Java's list sort and the original Perl sort retain the input
            // order of equal numeric breakpoints. No label-based tie sort occurs.
            breakpoints.sort(Comparator.comparingInt(Feature::start1));
            var features = new ArrayList<Feature>(breakpoints.size());
            var exons = new ArrayList<Part>();
            long length = 0;
            int empty = 0, oneBase = 0;
            for (int i = 0; i < breakpoints.size(); i++) {
                var value = breakpoints.get(i);
                int stop = i + 1 < breakpoints.size() ? breakpoints.get(i + 1).start1() - 1 : end;
                var feature = new Feature(value.inputOrdinal(), value.code(), value.start1(), stop);
                features.add(feature);
                if (!feature.isExon()) continue;
                if (++exonCount > limits.maximumExonParts()) throw error(input.lines, "Complete RNA exon-part capacity exceeded");
                int ordinal = exons.size() + 1;
                var part = new Part(executionId + "--Part" + ordinal, ordinal, feature);
                exons.add(part);
                length = Math.addExact(length, part.effectiveLength());
                if (feature.end1() < feature.start1()) empty++;
                else if (feature.end1() == feature.start1()) oneBase++;
            }
            if (exons.isEmpty()) throw error(input.lines, "Transcript has no E-prefixed exon parts");
            if ((empty > 0 || oneBase > 0) && shortPartPolicy == Policy.ERROR) {
                throw error(input.lines, "Original RNA resource has empty or one-base exon parts; explicit retention required");
            }
            var transcript = new Transcript(input.lines, rawId, executionId, condensedId, contig,
                    start, end, strand, features, exons, length, missing, outside, empty, oneBase, raw);
            sink.accept(transcript);
            lineSink.accept(input.lines, raw, Disposition.TRANSCRIPT);
            transcripts++;
            if (missing) missingRows++;
            if (outside) outsideRows++;
            emptyParts += empty;
            oneBaseParts += oneBase;
            totalLength = Math.addExact(totalLength, length);
            maximumExons = Math.max(maximumExons, exons.size());
        }
        check.run();
        return new Summary(input.lines, input.characters, transcripts, comments, blanks, exonCount,
                missingRows, outsideRows, emptyParts, oneBaseParts, totalLength, maximumExons,
                copies.size(), condensedIds.size());
    }

    private static int coordinate(String text, long line) throws IOException {
        if (text.isEmpty() || text.length() > 10 || !text.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw error(line, "Require plain integer RNA coordinates");
        }
        try {
            long value = Long.parseLong(text);
            if (value > 1_000_000_000) throw error(line, "RNA coordinate exceeds supported source capacity");
            return (int)value;
        } catch (NumberFormatException ex) {
            throw error(line, "RNA coordinate exceeds supported integer capacity");
        }
    }

    private static IOException error(long line, String cause) {
        return new IOException("RNA resource line " + line + ": " + cause);
    }

    private static final class Lines {
        private final Reader reader;
        private final Limits limits;
        private final Check check;
        private final char[] buffer = new char[4096];
        private int offset, available;
        private long lines, characters;
        Lines(Reader reader, Limits limits, Check check) {
            this.reader = reader;
            this.limits = limits;
            this.check = check;
        }
        String next() throws IOException {
            if (available < 0) return null;
            var line = new StringBuilder();
            while (true) {
                if (offset == available) {
                    check.run();
                    available = reader.read(buffer);
                    offset = 0;
                    if (available < 0) {
                        if (line.isEmpty()) return null;
                        lines++;
                        return line.toString();
                    }
                    if (available == 0) continue;
                }
                char c = buffer[offset++];
                if (++characters > limits.maximumDecodedCharacters()) throw error(lines + 1, "Decoded RNA character capacity exceeded");
                if (c == '\n') {
                    lines++;
                    return line.toString();
                }
                if (line.length() >= limits.maximumLineCharacters()) throw error(lines + 1, "RNA logical line capacity exceeded");
                if (c == 0) throw error(lines + 1, "NUL is unsupported in an original RNA resource");
                line.append(c);
            }
        }
    }
}
