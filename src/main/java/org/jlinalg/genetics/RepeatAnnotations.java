/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Source-preserving RepeatMasker/UCSC annotations and exact interval operations. */
public final class RepeatAnnotations {
    private RepeatAnnotations() { }

    /** Explicit layouts; an optional UCSC bin or missing OUT ID is never inferred. */
    public enum Format { OUT_WITH_ID, OUT_WITHOUT_ID, UCSC_RMSK_WITH_BIN, UCSC_RMSK_NO_BIN }
    /** Replacement of annotated bases; bases outside the union remain unchanged. */
    public enum Mask { SOFT, N, X }

    /** Text/row/line and conservative retained-storage capacities, checked without truncation. */
    public record Limits(long characters, long rows, int lineCharacters, long retainedBytes) {
        public Limits {
            if (characters < 1 || rows < 1 || lineCharacters < 1 || retainedBytes < 1)
                throw new IllegalArgumentException("Repeat annotation capacities must be positive");
        }
    }

    /** Ordinary OUT consensus coordinates, zero-based half-open, with source-inferred length. */
    public record Consensus(long start, long end, long length) {
        public Consensus {
            if (start < 0 || end <= start || length < end)
                throw new IllegalArgumentException("Require ordinary bounded consensus coordinates");
        }
    }

    /** All raw columns survive; UCSC signed consensus slots are intentionally not normalized. */
    public record Hit(String source, long sourceLine, Format format, long score,
            BigDecimal divergencePercent, BigDecimal deletionPercent, BigDecimal insertionPercent,
            String query, long start, long end, String strand, String repeatName,
            String repeatClass, String repeatFamily, String insertionId, boolean markedOverlap,
            Long queryLengthHint, Consensus consensus, List<String> rawFields) {
        public Hit {
            text(source, "source"); text(query, "query"); text(repeatName, "repeat name");
            text(repeatClass, "repeat class"); text(repeatFamily, "repeat family");
            Objects.requireNonNull(format); Objects.requireNonNull(divergencePercent);
            Objects.requireNonNull(deletionPercent); Objects.requireNonNull(insertionPercent);
            if (sourceLine < 1 || score < 0 || start < 0 || end <= start
                    || divergencePercent.signum() < 0 || deletionPercent.signum() < 0
                    || insertionPercent.signum() < 0 || !List.of("+", "-").contains(strand)
                    || queryLengthHint != null && queryLengthHint < end)
                throw new IllegalArgumentException("Invalid repeat annotation population or coordinates");
            if (insertionId != null) text(insertionId, "insertion ID");
            rawFields = List.copyOf(rawFields);
            if (isUcsc(format) && consensus != null)
                throw new IllegalArgumentException("UCSC consensus slots require separate source-specific certification");
        }
    }

    /** Complete-to-EOF structural read; this does not certify that an upstream analysis finished. */
    public record Table(List<Hit> hits, long sourceCharacters, long sourceLines,
            long headerOrCommentLines, long estimatedRetainedBytes, boolean noHitsMessage) {
        public Table { hits = List.copyOf(hits); }
    }
    /** Exact union and the original source rows contributing positive overlap. */
    public record Coverage(long coveredBases, long denominatorBases, List<Hit> contributors) {
        public Coverage { contributors = List.copyOf(contributors); }
    }
    /** Covered positions and changed characters differ when bases were already masked. */
    public record Masked(String sequence, long coveredBases, long changedCharacters) { }

    /** Parse one declared data row, retaining labels, IDs and all original column text. */
    public static Hit parseRow(String line, String source, long sourceLine, Format format) {
        Objects.requireNonNull(line); Objects.requireNonNull(format);
        if (line.indexOf('\0') >= 0 || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Annotation row contains a control or line separator");
        if (isUcsc(format)) return parseUcsc(line, source, sourceLine, format);
        String[] f = line.strip().split("\\s+");
        int expected = format == Format.OUT_WITH_ID ? 15 : 14;
        boolean marked = f.length == expected + 1 && f[expected].equals("*");
        if (f.length != expected && !marked)
            throw new IllegalArgumentException("Require the declared OUT columns and optional final * marker");
        long begin = integer(f[5]), end = integer(f[6]), remaining = parentheses(f[7]);
        if (begin < 1 || end < begin || remaining < 0)
            throw new IllegalArgumentException("OUT query requires one-based inclusive bounds and nonnegative remaining bases");
        String strand = switch (f[8]) { case "+" -> "+"; case "C" -> "-";
            default -> throw new IllegalArgumentException("OUT strand must be + or C"); };
        int slash = f[10].indexOf('/');
        String type = slash < 0 ? f[10] : f[10].substring(0, slash);
        String family = slash < 0 ? f[10] : f[10].substring(slash + 1);
        long refBegin, refEnd = integer(f[12]), refRemaining;
        if (strand.equals("+")) { refBegin = integer(f[11]); refRemaining = parentheses(f[13]); }
        else { refBegin = integer(f[13]); refRemaining = parentheses(f[11]); }
        Consensus consensus = null;
        long length = Math.addExact(refEnd, refRemaining);
        if (refBegin >= 1 && refEnd >= refBegin && refRemaining >= 0)
            consensus = new Consensus(refBegin - 1, refEnd, length);
        String id = format == Format.OUT_WITH_ID ? f[14] : null;
        if (id != null && integer(id) < 0) throw new IllegalArgumentException("OUT insertion ID must be nonnegative");
        return new Hit(source, sourceLine, format, nonnegative(f[0]), decimal(f[1]), decimal(f[2]),
                decimal(f[3]), f[4], begin - 1, end, strand, f[9], type, family, id, marked,
                Math.addExact(end, remaining), consensus, Arrays.asList(f));
    }

    private static Hit parseUcsc(String line, String source, long sourceLine, Format format) {
        String[] f = line.split("\t", -1); int offset = format == Format.UCSC_RMSK_WITH_BIN ? 1 : 0;
        if (f.length != 16 + offset) throw new IllegalArgumentException("Require exactly the declared 16/17 tab-separated UCSC rmsk fields");
        if (offset == 1) unsigned32(f[0]);
        long score = unsigned32(f[offset]), start = unsigned32(f[offset + 5]), end = unsigned32(f[offset + 6]);
        long queryLeft = signed32(f[offset + 7]); signed32(f[offset + 12]);
        unsigned32(f[offset + 13]); signed32(f[offset + 14]);
        Long queryLength = queryLeft <= 0 ? Math.addExact(end, -queryLeft) : null;
        String id = f[offset + 15].isEmpty() ? null : f[offset + 15];
        return new Hit(source, sourceLine, format, score,
                BigDecimal.valueOf(unsigned32(f[offset + 1]), 1),
                BigDecimal.valueOf(unsigned32(f[offset + 2]), 1),
                BigDecimal.valueOf(unsigned32(f[offset + 3]), 1), f[offset + 4], start, end,
                f[offset + 8], f[offset + 9], f[offset + 10], f[offset + 11], id, false,
                queryLength, null, Arrays.asList(f));
    }

    /** Read without taking ownership of the caller's reader; unknown content and excess capacity fail. */
    public static Table read(Reader supplied, String source, Format format, Limits limits) throws IOException {
        text(source, "source"); Objects.requireNonNull(format); Objects.requireNonNull(limits);
        Reader reader = supplied instanceof BufferedReader ? supplied : new BufferedReader(supplied);
        long characters = 0, lines = 0, headers = 0, retained = 0; boolean noHits = false;
        var hits = new ArrayList<Hit>(); var line = new StringBuilder();
        while (true) {
            int c = reader.read();
            if (c != -1 && ++characters > limits.characters()) throw new IOException("Repeat annotation source character capacity exceeded");
            if (c != -1 && c != '\n') {
                if (line.length() >= limits.lineCharacters()) throw new IOException("Repeat annotation line capacity exceeded");
                line.append((char)c); continue;
            }
            if (c == -1 && line.isEmpty()) break;
            lines++; String raw = line.toString(); line.setLength(0);
            if (raw.endsWith("\r")) raw = raw.substring(0, raw.length() - 1);
            String stripped = raw.strip();
            if (!stripped.isEmpty()) {
                boolean header = isUcsc(format) ? ucscHeader(stripped, format)
                        : stripped.startsWith("SW ") || stripped.startsWith("bit ") || stripped.startsWith("score ");
                if (header) headers++;
                else if (!isUcsc(format) && stripped.startsWith("There were no repetitive sequences detected")) {
                    if (!hits.isEmpty() || noHits) throw new IOException("Contradictory repeat no-hit message");
                    noHits = true;
                } else {
                    if (noHits) throw new IOException("Data follows a repeat no-hit message");
                    if (hits.size() >= limits.rows()) throw new IOException("Repeat annotation row capacity exceeded");
                    retained = Math.addExact(retained, Math.addExact(1536L, Math.multiplyExact(4L, raw.length())));
                    if (retained > limits.retainedBytes()) throw new IOException("Repeat annotation estimated retained-storage capacity exceeded");
                    try { hits.add(parseRow(raw, source, lines, format)); }
                    catch (IllegalArgumentException | ArithmeticException failure) {
                        throw new IOException(source + ":" + lines + ": " + failure.getMessage(), failure);
                    }
                }
            }
            if (c == -1) break;
        }
        return new Table(hits, characters, lines, headers, retained, noHits);
    }

    /** Exact union in a nonempty query region; callers explicitly select classes/overlap markers. */
    public static Coverage coverage(List<Hit> hits, String query, long start, long end) {
        text(query, "query"); if (start < 0 || end <= start) throw new IllegalArgumentException("Require a nonempty half-open query region");
        var contributors = new ArrayList<Hit>(); var spans = new ArrayList<long[]>();
        for (Hit hit : hits) if (hit.query().equals(query)) {
            long first = Math.max(start, hit.start()), last = Math.min(end, hit.end());
            if (first < last) { contributors.add(hit); spans.add(new long[]{first, last}); }
        }
        return new Coverage(union(spans), end - start, contributors);
    }

    /** Union the consensus fragments of one explicitly supplied insertion group. */
    public static Coverage consensusCoverage(List<Hit> fragments) {
        if (fragments.isEmpty()) throw new IllegalArgumentException("Consensus coverage needs a supplied insertion group");
        Hit first = fragments.get(0); Consensus reference = first.consensus();
        if (reference == null) throw new IllegalArgumentException("Consensus coordinates are unresolved; supply certified ordinary OUT annotations");
        var spans = new ArrayList<long[]>();
        for (Hit hit : fragments) {
            if (hit.consensus() == null || hit.consensus().length() != reference.length()
                    || !hit.source().equals(first.source()) || !hit.query().equals(first.query())
                    || !hit.repeatName().equals(first.repeatName()) || !hit.repeatClass().equals(first.repeatClass())
                    || !hit.repeatFamily().equals(first.repeatFamily()) || !hit.strand().equals(first.strand())
                    || !Objects.equals(hit.insertionId(), first.insertionId())
                    || fragments.size() > 1 && hit.insertionId() == null)
                throw new IllegalArgumentException("Require one explicit, compatible insertion ID/query/name/class/strand/consensus group");
            spans.add(new long[]{hit.consensus().start(), hit.consensus().end()});
        }
        return new Coverage(union(spans), reference.length(), fragments);
    }

    /** Exact rational cutoff, including an explicit strict or inclusive boundary. */
    public static boolean meetsFraction(long covered, long total, long numerator, long denominator, boolean inclusive) {
        if (covered < 0 || total <= 0 || covered > total || numerator < 0 || denominator <= 0 || numerator > denominator)
            throw new IllegalArgumentException("Invalid covered population or fractional cutoff");
        int comparison = BigInteger.valueOf(covered).multiply(BigInteger.valueOf(denominator))
                .compareTo(BigInteger.valueOf(total).multiply(BigInteger.valueOf(numerator)));
        return inclusive ? comparison >= 0 : comparison > 0;
    }

    /** Mask an admitted IUPAC DNA string; exact query labels and original nonmasked case are retained. */
    public static Masked mask(String sequence, String query, List<Hit> hits, Mask mode) {
        Objects.requireNonNull(mode); Objects.requireNonNull(sequence); text(query, "query");
        for (int i = 0; i < sequence.length(); i++) {
            char c = sequence.charAt(i); char upper = c >= 'a' && c <= 'z' ? (char)(c - 32) : c;
            if ("ACGTURYKMSWBDHVNX".indexOf(upper) < 0) throw new IllegalArgumentException("Mask input needs explicit IUPAC DNA without gaps or normalization");
        }
        char[] output = sequence.toCharArray(); long changed = 0;
        for (Hit hit : hits) if (hit.query().equals(query)) {
            if (hit.end() > sequence.length()) throw new IllegalArgumentException("Repeat annotation exceeds exact query sequence length");
            for (int i = (int)hit.start(); i < (int)hit.end(); i++) {
                char replacement = mode == Mask.N ? 'N' : mode == Mask.X ? 'X'
                        : output[i] >= 'A' && output[i] <= 'Z' ? (char)(output[i] + 32) : output[i];
                if (output[i] != replacement) { output[i] = replacement; changed++; }
            }
        }
        long covered = sequence.isEmpty() ? 0 : coverage(hits, query, 0, sequence.length()).coveredBases();
        return new Masked(new String(output), covered, changed);
    }

    private static long union(List<long[]> spans) {
        spans.sort(Comparator.comparingLong((long[] v) -> v[0]).thenComparingLong(v -> v[1]));
        long total = 0, first = -1, last = -1;
        for (long[] span : spans) {
            if (first < 0) { first = span[0]; last = span[1]; }
            else if (span[0] <= last) last = Math.max(last, span[1]);
            else { total = Math.addExact(total, last - first); first = span[0]; last = span[1]; }
        }
        return first < 0 ? 0 : Math.addExact(total, last - first);
    }
    private static boolean isUcsc(Format format) { return format == Format.UCSC_RMSK_WITH_BIN || format == Format.UCSC_RMSK_NO_BIN; }
    private static boolean ucscHeader(String line, Format format) throws IOException {
        String content = line.startsWith("#") ? line.substring(1).stripLeading() : line;
        if (content.startsWith("bin\t") || content.startsWith("swScore\t")
                || content.startsWith("bin ") || content.startsWith("swScore ")) {
            var expected = new ArrayList<>(List.of("swScore", "milliDiv", "milliDel", "milliIns", "genoName",
                    "genoStart", "genoEnd", "genoLeft", "strand", "repName", "repClass", "repFamily",
                    "repStart", "repEnd", "repLeft", "id"));
            if (format == Format.UCSC_RMSK_WITH_BIN) expected.add(0, "bin");
            if (!Arrays.asList(content.split("\\s+")).equals(expected))
                throw new IOException("UCSC header differs from the explicitly declared rmsk column order");
            return true;
        }
        return line.startsWith("#");
    }
    private static void text(String value, String name) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Require a nonempty repeat " + name);
    }
    private static long integer(String value) {
        if (!value.matches("-?[0-9]+")) throw new IllegalArgumentException("Require an integer repeat field: " + value);
        return Long.parseLong(value);
    }
    private static long nonnegative(String value) {
        long number = integer(value); if (number < 0) throw new IllegalArgumentException("Negative repeat field: " + value); return number;
    }
    private static long unsigned32(String value) {
        long number = nonnegative(value); if (number > 4294967295L) throw new IllegalArgumentException("UCSC unsigned32 capacity exceeded"); return number;
    }
    private static long signed32(String value) {
        long number = integer(value); if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalArgumentException("UCSC signed32 capacity exceeded"); return number;
    }
    private static long parentheses(String value) {
        if (value.length() < 3 || value.charAt(0) != '(' || value.charAt(value.length() - 1) != ')')
            throw new IllegalArgumentException("Require an explicitly parenthesized remaining-bases field");
        return integer(value.substring(1, value.length() - 1));
    }
    private static BigDecimal decimal(String value) {
        if (value.length() > 128) throw new IllegalArgumentException("Repeat decimal field capacity exceeded");
        BigDecimal number = new BigDecimal(value);
        if (number.signum() < 0 || Math.abs((long)number.scale()) > 1000 || !Double.isFinite(number.doubleValue()))
            throw new IllegalArgumentException("Require a finite nonnegative repeat percentage");
        return number;
    }
}
