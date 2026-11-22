/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Bounded-memory DNA FASTA scanning with explicit strand and coordinate policies. */
public final class DnaMotifScanner {
    private DnaMotifScanner() { }
    private static final Pattern GENOMIC = Pattern.compile("([^:]+):([0-9]+)-([0-9]+)");

    /**
     * Explicit scanning policies. Invalid ASCII letters can be rejected or converted
     * to N; U/u scores as T/t while preserving forward matched text, and
     * X/x/dot maps to N. Both alias counts are recorded separately.
     * @param bothStrands score both orientations
     * @param genomicCoordinates parse name:start-end, with one-based start
     * @param threshold inclusive p-value threshold in [0,1]
     * @param maximumHits fail rather than silently truncate above this count
     * @param maximumResidues maximum decompressed sequence residues
     * @param convertInvalidToN explicit permission for unknown printable ASCII residues
     */
    public record Options(boolean bothStrands, boolean genomicCoordinates, double threshold,
                          long maximumHits, long maximumResidues, boolean convertInvalidToN) {
        /** Validates limits. */
        public Options {
            if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1 || maximumHits < 1 || maximumResidues < 1)
                throw new IllegalArgumentException("Require threshold in [0,1] and positive hit/residue limits");
        }
    }
    /**
     * Sequence identity includes ordinal so duplicate names remain distinguishable.
     * @param ordinal one-based input record number
     * @param originalId complete first header token
     * @param name emitted name after optional genomic coordinate parsing
     * @param header complete UTF-8 FASTA header without greater-than sign
     * @param start one-based coordinate anchor
     * @param declaredEnd parsed end, or zero if absent
     * @param residues observed sequence length
     */
    public record Sequence(long ordinal, String originalId, String name, String header, long start, long declaredEnd, long residues) { }
    /**
     * One oriented site; reverse matches use uppercase reverse complements.
     * @param sequenceOrdinal one-based input record number
     * @param sequenceName emitted sequence identifier
     * @param start one-based inclusive left coordinate on input orientation
     * @param stop one-based inclusive right coordinate on input orientation
     * @param strand plus or minus
     * @param word oriented matched sequence
     * @param score lattice score/tail and separately labeled continuous score
     */
    public record Hit(long sequenceOrdinal, String sequenceName, long start, long stop, char strand, String word, DnaPssm.Site score) { }
    /**
     * Counts for one motif, including all windows before thresholding.
     * @param sequences FASTA records including short and empty records
     * @param residues sequence residues
     * @param windows candidate windows before ambiguity masking, per position
     * @param ambiguousWindows positions masked by any noncanonical base
     * @param scoredSites valid windows times selected strand count
     * @param hits emitted sites
     * @param uracilAliases U/u residues mapped to T/t
     * @param ambiguityAliases X/x/dot residues mapped to N
     * @param convertedInvalid invalid residues explicitly converted to N
     * @param coordinateSpanMismatches parsed end inconsistent with observed record length
     */
    public record Summary(long sequences, long residues, long windows, long ambiguousWindows, long scoredSites,
                          long hits, long uracilAliases, long ambiguityAliases, long convertedInvalid, long coordinateSpanMismatches) { }
    /**
     * Sequence-only inspection; no motif score or null probability is calculated.
     * @param sequences input records
     * @param residues all sequence residues
     * @param canonicalResidues A/C/G/T/U residues
     * @param uracilAliases U/u aliases
     * @param ambiguityAliases X/x/dot aliases
     * @param convertedInvalid explicitly permitted invalid ASCII conversions
     * @param coordinateSpanMismatches declared/observed length mismatches
     */
    public record Inspection(long sequences,long residues,long canonicalResidues,long uracilAliases,long ambiguityAliases,
                             long convertedInvalid,long coordinateSpanMismatches) { }
    /** A streaming site callback that may write to an audited output. */
    @FunctionalInterface public interface HitConsumer {
        /**
         * @param hit next accepted site
         * @throws IOException when output fails
         */
        void accept(Hit hit) throws IOException;
    }
    /** Callback at each sequence end, including empty records. */
    @FunctionalInterface public interface SequenceConsumer {
        /**
         * @param sequence completed metadata
         * @throws IOException when output fails
         */
        void accept(Sequence sequence) throws IOException;
    }

    /**
     * Scans plain or gzip-magic FASTA without retaining complete sequences or hits.
     * FASTA headers are bounded to 16 KiB; UTF-8 header identities are preserved.
     * Whitespace within sequence lines is ignored. Ambiguous DNA masks complete windows.
     * @param fasta regular FASTA source
     * @param model resolved position score model; scanner never modifies its background
     * @param options explicit scan policies and resource limits
     * @param hits accepted hit callback
     * @param sequences completed sequence callback
     * @return complete input/scoring counts
     * @throws IOException on malformed input, output failure or exceeded scan limits
     */
    public static Summary scan(Path fasta, DnaPssm model, Options options, HitConsumer hits, SequenceConsumer sequences) throws IOException {
        return read(fasta,new Scan(model,options,hits,sequences,false));
    }

    /**
     * Inspects FASTA without scoring or retaining sequences; identity and alphabet
     * policies match the scanner. It does not rewrite the original FASTA.
     * @param fasta plain or gzip-magic input
     * @param genomicCoordinates whether to parse genomic header anchors
     * @param maximumResidues explicit sequence residue limit
     * @param convertInvalidToN whether unknown printable ASCII may be interpreted as N
     * @param sequences completed input record callback
     * @return identity, residue and normalization counts, without statistical results
     * @throws IOException on malformed input or callback failure
     */
    public static Inspection inspect(Path fasta,boolean genomicCoordinates,long maximumResidues,boolean convertInvalidToN,SequenceConsumer sequences) throws IOException {
        var model=DnaPssm.build(new double[][]{{.25,.25,.25,.25}},new double[]{.25,.25,.25,.25},20,0);
        var result=read(fasta,new Scan(model,new Options(false,genomicCoordinates,0,1,maximumResidues,convertInvalidToN),h -> { },sequences,true));
        return new Inspection(result.sequences(),result.residues(),result.scoredSites(),result.uracilAliases(),result.ambiguityAliases(),result.convertedInvalid(),result.coordinateSpanMismatches());
    }
    private static Summary read(Path fasta,Scan scanner) throws IOException {
        try (var raw = new BufferedInputStream(Files.newInputStream(fasta))) {
            raw.mark(2); int a = raw.read(), b = raw.read(); raw.reset();
            try (InputStream input = new BufferedInputStream(a == 31 && b == 139 ? new GZIPInputStream(raw) : raw)) {
                return scanner.read(input);
            }
        }
    }
    private static final class Scan {
        final DnaPssm model; final Options options; final HitConsumer sink; final SequenceConsumer sequenceSink;
        final char[] ring; final long[] counts = new long[10]; final boolean inspection;
        Sequence sequence; long length; int filled, next, ambiguous;
        Scan(DnaPssm model, Options options, HitConsumer hits, SequenceConsumer sequences,boolean inspection) {
            this.model = java.util.Objects.requireNonNull(model); this.options = java.util.Objects.requireNonNull(options);
            this.sink = java.util.Objects.requireNonNull(hits); this.sequenceSink = java.util.Objects.requireNonNull(sequences); ring = new char[model.width()];
            this.inspection=inspection;
        }
        Summary read(InputStream in) throws IOException {
            boolean lineStart = true; int c;
            while ((c = in.read()) != -1) {
                if (c == '\n' || c == '\r') { lineStart = true; continue; }
                if (c == '>' && lineStart) { finish(); header(in); lineStart = true; continue; }
                if (c == ' ' || c == '\t' || c == '\f') continue;
                if (sequence == null) throw new IOException("FASTA residue before first header");
                lineStart = false; residue(c);
            }
            finish(); if (counts[0] == 0) throw new IOException("FASTA contains no records");
            return new Summary(counts[0],counts[1],counts[2],counts[3],counts[4],counts[5],counts[6],counts[7],counts[8],counts[9]);
        }
        void header(InputStream input) throws IOException {
            var bytes = new ByteArrayOutputStream(); int c;
            while ((c = input.read()) != -1 && c != '\n' && c != '\r') {
                if (bytes.size() >= 16384) throw new IOException("FASTA header exceeds 16 KiB");
                bytes.write(c);
            }
            String header = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            if (header.isBlank() || Character.isWhitespace(header.charAt(0)) || header.chars().anyMatch(x -> x < 32 && x != '\t' || x == 127))
                throw new IOException("Require a nonempty FASTA identifier without leading whitespace or control characters");
            String id = header.split("\\s+", 2)[0], name = id; long start = 1, end = 0;
            if (options.genomicCoordinates()) {
                var match = GENOMIC.matcher(id);
                if (match.matches()) {
                    try { start = Long.parseLong(match.group(2)); end = Long.parseLong(match.group(3)); }
                    catch (NumberFormatException failure) { throw new IOException("FASTA genomic coordinate exceeds signed 64-bit range", failure); }
                    if (start < 1 || end < start) throw new IOException("Require positive ascending one-based FASTA genomic coordinates");
                    name = match.group(1);
                }
            }
            counts[0] = Math.addExact(counts[0], 1); sequence = new Sequence(counts[0], id, name, header, start, end, 0);
            length = 0; filled = 0; next = 0; ambiguous = 0;
        }
        void finish() throws IOException {
            if (sequence == null) return;
            if (sequence.declaredEnd() != 0 && length != sequence.declaredEnd() - sequence.start() + 1) counts[9]++;
            sequenceSink.accept(new Sequence(sequence.ordinal(),sequence.originalId(),sequence.name(),sequence.header(),sequence.start(),sequence.declaredEnd(),length));
            sequence = null;
        }
        void residue(int raw) throws IOException {
            if (raw < 33 || raw > 126) throw new IOException("Require printable ASCII DNA residues; found byte " + raw);
            char base = (char)raw, upper = Character.toUpperCase(base);
            if (upper == 'U') { counts[6]++; }
            else if (upper == 'X' || base == '.') { base = 'N'; upper = 'N'; counts[7]++; }
            else if ("ACGTRYSWKMBDHVN".indexOf(upper) < 0) {
                if (!options.convertInvalidToN()) throw new IOException("Unknown DNA residue '" + base + "'; correct input or explicitly select --invalid-residues convert-n");
                base = 'N'; upper = 'N'; counts[8]++;
            }
            if (++counts[1] > options.maximumResidues()) throw new IOException("FASTA exceeds declared decompressed residue limit");
            length = Math.addExact(length, 1);
            if (filled == ring.length) { if (!canonical(ring[next])) ambiguous--; } else filled++;
            ring[next] = base; if (!canonical(base)) ambiguous++;
            next = (next + 1) % ring.length;
            if (filled < ring.length) return;
            counts[2]++; if (ambiguous != 0) { counts[3]++; return; }
            if (inspection) { counts[4]++;return; }
            char[] word = new char[ring.length]; for (int i = 0; i < word.length; i++) word[i] = ring[(next + i) % ring.length];
            emit(new String(word), '+');
            if (options.bothStrands()) {
                char[] reverse = new char[word.length]; for (int i = 0; i < word.length; i++) reverse[i] = complement(word[word.length - i - 1]);
                emit(new String(reverse), '-');
            }
        }
        void emit(String word, char strand) throws IOException {
            counts[4]++; var score = model.score(word.replace('U','T').replace('u','t')); if (score.pValue() > options.threshold()) return;
            if (++counts[5] > options.maximumHits()) throw new IOException("Motif exceeds declared maximum hits; no truncation is published. Use a stricter threshold or raise --maximum-hits explicitly");
            long stop = Math.addExact(sequence.start(), length - 1), start = stop - model.width() + 1;
            sink.accept(new Hit(sequence.ordinal(),sequence.name(),start,stop,strand,word,score));
        }
    }
    private static boolean canonical(char base) { return "ACGTUacgtu".indexOf(base) >= 0; }
    private static char complement(char base) { return switch (base) { case 'A','a' -> 'T'; case 'C','c' -> 'G'; case 'G','g' -> 'C'; case 'T','t','U','u' -> 'A'; default -> throw new AssertionError(base); }; }
}
