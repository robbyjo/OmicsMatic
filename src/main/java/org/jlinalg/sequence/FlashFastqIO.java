/* Copyright (C) 2026 JLinAlg contributors
 * Original cleanup/header conventions: FLASH 1.2.11, Tanja Magoc and Eric Biggers.
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Streaming four-line FASTQ with FLASH cleanup and merged-header conventions. */
public final class FlashFastqIO {
    private FlashFastqIO() { }

    /**
     * A normalized input read, with counts of the original cleanup operations.
     * @param originalHeader complete original header before trailing ASCII whitespace removal
     * @param header cleaned header, including the initial at sign
     * @param sequence uppercase A/C/G/T/N sequence
     * @param quality trimmed qualities in the declared encoding
     * @param caseChanges lowercase canonical bases converted to uppercase
     * @param unknownChanges characters other than A/C/G/T/N converted to N, including lowercase n
     * @param trimmedHeader trailing ASCII whitespace removed from the header
     * @param trimmedSequence trailing ASCII whitespace removed from the sequence
     * @param trimmedQuality trailing ASCII whitespace removed from the quality line
     * @param ignoredPlusIdentity whether a nonempty repeated plus identifier differed from the header
     */
    public record Read(String originalHeader, String header, String sequence, String quality,
                       int caseChanges, int unknownChanges, int trimmedHeader, int trimmedSequence,
                       int trimmedQuality, boolean ignoredPlusIdentity) {
        /** First header token with /1 or /2 removed, including before a # barcode. */
        public String pairId() { return FlashFastqIO.pairId(header); }
    }

    /**
     * Original FLASH combined tag: use the first full header and remove its last
     * slash suffix, retaining a barcode beginning immediately after /X#.
     * This can remove comments containing a slash; callers must record the change.
     * @param header cleaned full first header, including the initial at sign
     * @return original-convention merged header
     */
    public static String mergedHeader(String header) {
        validHeader(header);
        int slash = header.lastIndexOf('/');
        if (slash < 0) return header;
        return header.substring(0, slash) + (slash + 2 < header.length()
                && header.charAt(slash + 1) < 128 && header.charAt(slash + 2) == '#'
                ? header.substring(slash + 2) : "");
    }

    /**
     * Pair identity used by the optional stricter input check, not by original FLASH.
     * @param header complete cleaned header
     * @return first whitespace-delimited token with its mate suffix removed
     */
    public static String pairId(String header) {
        validHeader(header);
        int end = 1;
        while (end < header.length() && !asciiSpace(header.charAt(end))) end++;
        return header.substring(1, end).replaceFirst("/[12](?=#|$)", "");
    }

    private static boolean asciiSpace(char c) { return c == ' ' || c >= '\t' && c <= '\r'; }
    private static String trimEnd(String value) {
        int end = value.length();
        while (end > 0 && asciiSpace(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
    private static void validHeader(String header) {
        if (header == null || !header.startsWith("@") || header.length() < 2
                || Character.isWhitespace(header.charAt(1))
                || header.chars().anyMatch(c -> c < 32 && c != '\t' || c == 127))
            throw new IllegalArgumentException("Require a nonempty @ FASTQ identifier and no header control characters except tab");
    }

    /** Plain or magic-detected gzip reader. UTF-8 headers and ASCII sequence/qualities. */
    public static final class Reader implements AutoCloseable {
        private final BufferedReader input;
        private final Path file;
        private final int phredOffset;
        private long ordinal;

        /**
         * Opens and validates a declared Phred encoding without inferring it.
         * @param file plain or gzip FASTQ
         * @param phredOffset 33 or 64
         * @throws IOException if the stream cannot be opened
         */
        public Reader(Path file, int phredOffset) throws IOException {
            if (phredOffset != 33 && phredOffset != 64) throw new IllegalArgumentException("Phred offset must be 33 or 64");
            this.file = file; this.phredOffset = phredOffset;
            var raw = new PushbackInputStream(Files.newInputStream(file), 2);
            try {
                byte[] prefix = raw.readNBytes(2); raw.unread(prefix);
                InputStream decoded = prefix.length == 2 && (prefix[0] & 255) == 31 && (prefix[1] & 255) == 139
                        ? new GZIPInputStream(raw) : raw;
                input = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
            } catch (IOException | RuntimeException failure) { raw.close(); throw failure; }
        }

        /**
         * Reads one record and applies the original sequence cleanup rules.
         * Noncanonical ASCII bases become N; internal ASCII whitespace is invalid.
         * Original FLASH ignores repeated plus identifiers; this reader counts mismatches.
         * @return next read, or null at a record boundary at end of stream
         * @throws IOException on malformed/truncated input or undeclared quality encoding
         */
        public Read next() throws IOException {
            String originalHeader = input.readLine();
            if (originalHeader == null) return null;
            ordinal++;
            String rawSequence = input.readLine(), plus = input.readLine(), rawQuality = input.readLine();
            if (rawSequence == null || plus == null || rawQuality == null || !plus.startsWith("+"))
                throw invalid("Require complete unwrapped four-line FASTQ records and a + line");
            String header = trimEnd(originalHeader), sequence = trimEnd(rawSequence), quality = trimEnd(rawQuality);
            try { validHeader(header); } catch (IllegalArgumentException failure) { throw invalid(failure.getMessage()); }
            if (sequence.length() != quality.length() || sequence.length() > FlashReads.MAXIMUM_READ_LENGTH)
                throw invalid("Require equal cleaned sequence/quality lengths and at most 10,000,000 bases per mate");
            var normalized = new StringBuilder(sequence.length()); int caseChanges = 0, unknownChanges = 0;
            for (int i = 0; i < sequence.length(); i++) {
                char base = sequence.charAt(i), q = quality.charAt(i);
                if (base > 127 || asciiSpace(base)) throw invalid("Sequence must be ASCII with no internal whitespace");
                char clean = switch (base) {
                    case 'A', 'a' -> 'A'; case 'C', 'c' -> 'C'; case 'G', 'g' -> 'G'; case 'T', 't' -> 'T'; default -> 'N';
                };
                if (base == 'a' || base == 'c' || base == 'g' || base == 't') caseChanges++;
                else if (base != clean) unknownChanges++;
                if (q < phredOffset || q > 126) throw invalid("Quality outside declared printable ASCII Phred+" + phredOffset + " at base " + (i + 1));
                normalized.append(clean);
            }
            String repeated = trimEnd(plus.substring(1));
            return new Read(originalHeader, header, normalized.toString(), quality, caseChanges, unknownChanges,
                    originalHeader.length() - header.length(), rawSequence.length() - sequence.length(), rawQuality.length() - quality.length(),
                    !repeated.isEmpty() && !repeated.equals(header.substring(1)));
        }
        private IOException invalid(String reason) { return new IOException(reason + " at FASTQ record " + ordinal + " in " + file); }
        @Override public void close() throws IOException { input.close(); }
    }

    /** Canonical four-line output with cleaned headers and declared qualities. */
    public static final class Writer implements AutoCloseable {
        private final BufferedWriter output;
        private final int phredOffset;
        /**
         * Opens a new output, using gzip when its name ends in .gz.
         * @param file new output path
         * @param phredOffset declared quality offset, 33 or 64
         * @throws IOException if a new output cannot be created
         */
        public Writer(Path file, int phredOffset) throws IOException {
            if (phredOffset != 33 && phredOffset != 64) throw new IllegalArgumentException("Phred offset must be 33 or 64");
            this.phredOffset = phredOffset;
            var raw = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                var encoded = file.toString().toLowerCase(Locale.ROOT).endsWith(".gz") ? new GZIPOutputStream(raw) : raw;
                output = new BufferedWriter(new OutputStreamWriter(encoded, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException failure) { raw.close(); throw failure; }
        }
        /**
         * Writes one normalized read, allowing a merged sequence up to twice a mate's bound.
         * @param header complete cleaned header
         * @param sequence normalized A/C/G/T/N sequence
         * @param quality declared quality encoding
         * @throws IOException on a write error
         */
        public void write(String header, String sequence, String quality) throws IOException {
            validHeader(header);
            if (sequence.length() != quality.length() || sequence.length() > 2 * FlashReads.MAXIMUM_READ_LENGTH
                    || sequence.chars().anyMatch(c -> "ACGTN".indexOf(c) < 0)
                    || quality.chars().anyMatch(c -> c < phredOffset || c > 126))
                throw new IllegalArgumentException("Invalid normalized FLASH output record");
            output.write(header); output.write('\n'); output.write(sequence); output.write("\n+\n"); output.write(quality); output.write('\n');
        }
        @Override public void close() throws IOException { output.close(); }
    }
}
