/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.reference.FastaSequenceIndex;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jlinalg.sequence.FastaIndexes;

/** Complete local CRAM FASTA/index and header-reference checks. No reference is fetched. */
final class RsemCramReference {
    private RsemCramReference() { }
    record Limits(int sequences, long bases, int lineCharacters, long nameCharacters, int indexBytes, long headerCharacters) {
        Limits {
            if (sequences < 1 || sequences > 1000000 || bases < 1 || bases > 1099511627776L || lineCharacters < 1 || lineCharacters > 16777216
                    || nameCharacters < 1 || nameCharacters > 1073741824 || indexBytes < 1 || indexBytes > 67108864
                    || headerCharacters < 1 || headerCharacters > 1073741824)
                throw new IllegalArgumentException("Require explicit complete CRAM reference/header capacities");
        }
        void heap() throws IOException {
            long estimate = (long) sequences * 2048 + nameCharacters * 4 + (long) lineCharacters * 8 + (long) indexBytes * 3 + headerCharacters * 4;
            if (estimate > Runtime.getRuntime().maxMemory() / 4 * 3)
                throw new IOException("Declared CRAM reference retained capacities exceed75%of this Java heap; provide a reviewed larger -Xmx or smaller complete limits, without dropping reference records");
        }
    }
    record Sequence(String name, long length, String md5, String sha256) { }
    record Reference(Path fasta, Path fai, Map<String,Sequence> sequences, RsemFileGraph.Snapshot files, Limits limits) {
        Reference { sequences = Collections.unmodifiableMap(new LinkedHashMap<>(sequences)); }
        void verify(Runnable check) throws IOException { files.verify(check); }
    }
    record HeaderTarget(String name, long headerLength, long fastaLength, String md5, String uri) { }
    record Header(List<HeaderTarget> targets, List<String> warnings, String sha256) {
        Header { targets = List.copyOf(targets); warnings = List.copyOf(warnings); }
    }

    static Reference audit(RsemInputs.Prepared input, Limits limits, Runnable check) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(limits); Objects.requireNonNull(check);
        limits.heap(); input.verify(check);
        if (input.cramFasta() == null) throw new RsemProcess.UnavailableDependency("Supply the exact local CRAM FASTA and .fai; dependent CRAM decoding/fitting must remain skipped while unavailable");
        Path fasta = input.cramFasta(), fai = fasta.resolveSibling(fasta.getFileName() + ".fai");
        var graphLimits = new RsemFileGraph.Limits(input.limits().fileBytes(), input.limits().totalBytes(), 256, 4, 4194304);
        var before = RsemFileGraph.capture(List.of(), List.of(fasta, fai), graphLimits, null, check);
        var sequences = scanFasta(fasta, input.sourcePins().get(input.spec().cramFasta()), limits, check);
        byte[] index = AdmixtureInputs.raw(fai, limits.indexBytes());
        var expectedIndex = input.sourcePins().get(input.spec().cramFasta().resolveSibling(input.spec().cramFasta().getFileName() + ".fai"));
        if (expectedIndex == null || index.length != expectedIndex.bytes() || !AdmixtureInputs.digest(index).equals(expectedIndex.sha256()))
            throw new IOException("CRAM index bytes consumed differ from the exact protected copy");
        // Bound the population before HTSJDK constructs its retained index map.
        validateIndexLines(index, limits, check);
        FastaSequenceIndex supplied;
        try { supplied = new FastaSequenceIndex(new ByteArrayInputStream(index)); }
        catch (RuntimeException invalid) { throw new IOException("Invalid complete CRAM FASTA index", invalid); }
        check.run();
        FastaSequenceIndex actual = FastaIndexes.build(fasta);
        check.run();
        if (!actual.equals(supplied)) throw new IOException("CRAM FASTA index differs from actual sequence lengths, byte offsets or LF/CRLF widths. Preserve the old index and regenerate from this exact FASTA using hts-tools --method fasta-index, then retry to NEW paths.");
        if (actual.size() != sequences.size()) throw new IOException("Complete FASTA sequence and index populations disagree");
        for (var entry : actual) {
            var sequence = sequences.get(entry.getContig());
            if (sequence == null || sequence.length() != entry.getSize()) throw new IOException("FASTA identity/length differs between complete byte-bound parsing and index construction: " + entry.getContig());
        }
        before.verify(check); input.verify(check);
        return new Reference(fasta, fai, sequences, before, limits);
    }

    /** Read an original header-only process output; preserve UR as evidence, never follow it. */
    static Header header(Path source, Reference reference, Runnable check) throws IOException {
        Objects.requireNonNull(reference); reference.limits().heap(); reference.verify(check);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || !source.toRealPath().equals(source)) throw new IOException("Require a canonical regular original header output");
        String expected = RepeatInputs.hash(source, reference.limits().headerCharacters() * 4, check);
        var targets = new ArrayList<HeaderTarget>(); var names = new java.util.HashSet<String>(); var warnings = new ArrayList<String>();
        MessageDigest digest = digest("SHA-256");
        try (var raw = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
             var input = reader(new DigestInputStream(raw, digest))) {
            var lines = new Lines(input, reference.limits().lineCharacters(), reference.limits().headerCharacters(), check);
            String line;
            while ((line = lines.next()) != null) {
                if (!line.startsWith("@")) throw new IOException("Header-only output contains a non-header record");
                if (!line.startsWith("@SQ\t")) continue;
                var fields = new LinkedHashMap<String,String>();
                int start = 4;
                while (start <= line.length()) {
                    int end = line.indexOf('\t', start); if (end < 0) end = line.length();
                    if (end - start < 4 || line.charAt(start + 2) != ':') throw new IOException("Malformed original CRAM @SQ field");
                    String tag = line.substring(start, start + 2);
                    if (List.of("SN", "LN", "M5", "UR").contains(tag) && fields.putIfAbsent(tag, line.substring(start + 3, end)) != null)
                        throw new IOException("Duplicate original CRAM reference-lookup header field: " + tag);
                    if (end == line.length()) break;
                    start = end + 1;
                }
                String name = fields.get("SN"), length = fields.get("LN"), md5 = fields.getOrDefault("M5", ""), uri = fields.getOrDefault("UR", "");
                if (name == null || name.isEmpty() || !names.add(name) || targets.size() >= reference.limits().sequences())
                    throw new IOException("Missing, duplicate or over-capacity CRAM header reference");
                long declared;
                try { if (length == null || !length.matches("[0-9]+")) throw new NumberFormatException(); declared = Long.parseLong(length); if (declared < 1 || declared > 4294967295L) throw new NumberFormatException(); }
                catch (NumberFormatException invalid) { throw new IOException("Invalid CRAM @SQ reference length: " + name, invalid); }
                var sequence = reference.sequences().get(name);
                if (sequence == null) throw new IOException("Explicit local FASTA/index lacks CRAM header reference: " + name + ". No URI/cache fallback is admitted.");
                if (!md5.isEmpty() && (!md5.matches("[0-9a-fA-F]{32}") || !md5.equalsIgnoreCase(sequence.md5())))
                    throw new IOException("CRAM header M5 does not match the complete explicit local FASTA sequence: " + name + ". No URI/cache fallback is admitted.");
                if (md5.isEmpty()) warnings.add("MISSING_HEADER_M5:" + name + ": retain caller-declared reference provenance; URI is not used to supply it");
                if (declared != sequence.length()) warnings.add("HEADER_FASTA_LENGTH_DISAGREEMENT:" + name + ":header=" + declared + ":fasta=" + sequence.length() + "; preserve effective per-record decoder lengths and resolve blocking native findings before fitting");
                targets.add(new HeaderTarget(name, declared, sequence.length(), sequence.md5(), uri));
            }
        }
        String consumed = HexFormat.of().formatHex(digest.digest());
        if (!consumed.equals(expected) || !expected.equals(RepeatInputs.hash(source, reference.limits().headerCharacters() * 4, check)))
            throw new IOException("Original CRAM header bytes changed during complete lookup auditing");
        reference.verify(check);
        return new Header(targets, warnings, consumed);
    }

    private static Map<String,Sequence> scanFasta(Path fasta, RsemInputs.Pin expected, Limits limits, Runnable check) throws IOException {
        if (expected == null) throw new IOException("CRAM FASTA lacks its protected source byte pin");
        var sequences = new LinkedHashMap<String,Sequence>(); MessageDigest rawDigest = digest("SHA-256");
        String name = null; long length = 0, bases = 0, names = 0; MessageDigest md5 = null, sha = null;
        try (var raw = Files.newInputStream(fasta, LinkOption.NOFOLLOW_LINKS);
             var reader = reader(new DigestInputStream(raw, rawDigest))) {
            var lines = new Lines(reader, limits.lineCharacters(), Math.min(1099511627776L, expected.bytes()), check); String line;
            while ((line = lines.next()) != null) {
                if (line.startsWith(">")) {
                    if (name != null) put(sequences, name, length, md5, sha);
                    int begin = 1; while (begin < line.length() && space(line.charAt(begin))) begin++;
                    int end = begin; while (end < line.length() && !space(line.charAt(end))) end++;
                    name = line.substring(begin, end); names += name.length();
                    if (name.isEmpty() || sequences.containsKey(name) || sequences.size() >= limits.sequences() || names > limits.nameCharacters())
                        throw new IOException("Empty, duplicate or over-capacity FASTA identity");
                    length = 0; md5 = digest("MD5"); sha = digest("SHA-256");
                } else {
                    int used = 0; byte[] normalized = new byte[line.length()];
                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i); if (space(c)) continue;
                        if (c >= 'a' && c <= 'z') c = (char)(c - 'a' + 'A');
                        if (c < 'A' || c > 'Z' || name == null) throw new IOException("Nonletter reference sequence or data before its FASTA header");
                        normalized[used++] = (byte)c;
                    }
                    if (used > 0) {
                        length += used; bases += used;
                        if (bases > limits.bases()) throw new IOException("Complete CRAM FASTA base capacity exceeded");
                        md5.update(normalized, 0, used); sha.update(normalized, 0, used);
                    }
                }
            }
            if (name != null) put(sequences, name, length, md5, sha);
        }
        if (sequences.isEmpty() || !HexFormat.of().formatHex(rawDigest.digest()).equals(expected.sha256()))
            throw new IOException("Complete CRAM FASTA bytes consumed differ from the protected source");
        return sequences;
    }
    private static void put(Map<String,Sequence> result, String name, long length, MessageDigest md5, MessageDigest sha) throws IOException {
        if (length < 1 || result.putIfAbsent(name, new Sequence(name, length, HexFormat.of().formatHex(md5.digest()), HexFormat.of().formatHex(sha.digest()))) != null)
            throw new IOException("Empty or duplicate complete FASTA sequence: " + name);
    }
    private static void validateIndexLines(byte[] bytes, Limits limits, Runnable check) throws IOException {
        try (var reader = reader(new ByteArrayInputStream(bytes))) {
            var lines = new Lines(reader, limits.lineCharacters(), limits.indexBytes(), check); int count = 0; long names = 0; String line;
            var seen = new java.util.HashSet<String>();
            while ((line = lines.next()) != null) {
                String[] fields = line.split("\t", 6);
                if (fields.length != 5 || fields[0].isEmpty() || !seen.add(fields[0]) || ++count > limits.sequences() || (names += fields[0].length()) > limits.nameCharacters())
                    throw new IOException("Invalid or over-capacity complete FASTA index population");
                for (int i = 1; i < fields.length; i++) try {
                    if (!fields[i].matches("[0-9]+") || Long.parseLong(fields[i]) < (i == 2 ? 0 : 1)) throw new NumberFormatException();
                } catch (NumberFormatException invalid) { throw new IOException("Invalid FASTA index integer", invalid); }
            }
            if (count == 0) throw new IOException("Empty complete FASTA index");
        }
    }
    private static BufferedReader reader(java.io.InputStream input) {
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }
    private static boolean space(char c) { return c == ' ' || c >= '\t' && c <= '\r'; }
    private static MessageDigest digest(String name) { try { return MessageDigest.getInstance(name); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    private static final class Lines {
        private final BufferedReader reader; private final int lineLimit; private final long totalLimit; private final Runnable check; private long total;
        Lines(BufferedReader reader, int lineLimit, long totalLimit, Runnable check) { this.reader = reader; this.lineLimit = lineLimit; this.totalLimit = totalLimit; this.check = check; }
        String next() throws IOException {
            check.run(); var text = new StringBuilder(); int c;
            while ((c = reader.read()) != -1) {
                if (++total > totalLimit) throw new IOException("Complete reference/header text capacity exceeded");
                if ((total & 8191) == 0) check.run();
                if (c == '\n') { if (!text.isEmpty() && text.charAt(text.length() - 1) == '\r') text.setLength(text.length() - 1); return text.toString(); }
                if (text.length() >= lineLimit) throw new IOException("Complete reference/header line capacity exceeded");
                text.append((char)c);
            }
            return text.isEmpty() ? null : text.toString();
        }
    }
}
