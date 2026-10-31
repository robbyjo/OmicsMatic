/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Bounded streaming FASTA census; no chromosome-sized string or inferred identifier rewrite. */
final class RepeatFasta {
    private RepeatFasta() { }
    enum Encoding { PLAIN_UTF8, GZIP_UTF8 }
    record Limits(long inputBytes, long decodedCharacters, long residues, long perSequence,
            int sequences, int lineCharacters, int headerCharacters, long retainedBytes) {
        Limits {
            if (inputBytes < 1 || decodedCharacters < 1 || residues < 1 || perSequence < 1 || sequences < 1
                    || lineCharacters < 1 || headerCharacters < 1 || headerCharacters > lineCharacters
                    || retainedBytes < Math.addExact(65536L, Math.multiplyExact(16L, lineCharacters)))
                throw new IllegalArgumentException("Require positive FASTA capacities and retained space for the bounded line/decoder");
        }
    }
    record Sequence(int ordinal, String id, String header, long length, String sequenceSha256) { }
    record Census(List<Sequence> sequences, long residues, long decodedCharacters, long sourceBytes,
            String sourceSha256, long estimatedRetainedBytes) {
        Census { sequences = List.copyOf(sequences); }
    }
    static Census read(Path path, Encoding encoding, Limits limits, Runnable check) throws IOException {
        try (var cursor = open(path, encoding, limits, check)) {
            while (cursor.nextSequence()) while (cursor.base() >= 0) { /* complete read */ }
            return cursor.census();
        }
    }
    static Cursor open(Path path, Encoding encoding, Limits limits, Runnable check) throws IOException {
        return new Cursor(path, encoding, limits, check);
    }
    static final class Cursor implements AutoCloseable {
        private final Limits limits;
        private final Runnable check;
        private final Capped raw;
        private final MessageDigest sourceDigest = hasher();
        private final BufferedReader reader;
        private final ArrayList<Sequence> sequences = new ArrayList<>();
        private final HashSet<String> identifiers = new HashSet<>();
        private MessageDigest sequenceDigest;
        private String id, header, pendingHeader, body;
        private int bodyOffset, ordinal;
        private long length, residues, characters, retained;
        private boolean recordOpen, finished, closed;
        private Census completed;

        Cursor(Path supplied, Encoding encoding, Limits limits, Runnable check) throws IOException {
            this.limits = Objects.requireNonNull(limits); this.check = Objects.requireNonNull(check);
            Objects.requireNonNull(encoding); Path path = supplied.toAbsolutePath().normalize();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path)
                    || Files.size(path) > limits.inputBytes()) throw new IOException("Require a canonical regular FASTA within the source-byte capacity");
            retained = Math.addExact(65536L, Math.multiplyExact(16L, limits.lineCharacters()));
            raw = new Capped(Files.newInputStream(path), limits.inputBytes(), check);
            var hashed = new DigestInputStream(raw, sourceDigest);
            InputStream decoded = encoding == Encoding.GZIP_UTF8 ? new RepeatGzip(hashed, check) : hashed;
            reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
        }
        boolean nextSequence() throws IOException {
            available(); check.run();
            if (recordOpen) throw new IOException("Read the complete FASTA record before advancing");
            if (finished) return false;
            String text = pendingHeader; pendingHeader = null;
            while (text == null) {
                String line = line();
                if (line == null) {
                    if (ordinal == 0) throw new IOException("FASTA has no records");
                    finished = true; return false;
                }
                if (line.isEmpty()) continue;
                if (!line.startsWith(">")) throw new IOException("FASTA residues precede the first header");
                text = line.substring(1);
            }
            if (++ordinal > limits.sequences()) throw new IOException("FASTA sequence-count capacity exceeded");
            if (text.isEmpty() || text.length() > limits.headerCharacters() || Character.isWhitespace(text.charAt(0)))
                throw new IOException("Require a bounded nonempty FASTA header without leading whitespace");
            for (int i = 0; i < text.length(); i++) if (Character.isISOControl(text.charAt(i)) && text.charAt(i) != '\t')
                throw new IOException("Control in FASTA header");
            int stop = 0; while (stop < text.length() && text.charAt(stop) != ' ' && text.charAt(stop) != '\t') stop++;
            String identifier = text.substring(0, stop);
            if (identifier.isEmpty() || identifier.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)))
                throw new IOException("Invalid exact FASTA identifier token");
            retained = add(retained, add(512L, 16L * text.length(), "header storage"), "metadata storage");
            if (retained > limits.retainedBytes()) throw new IOException("FASTA estimated retained metadata capacity exceeded");
            if (!identifiers.add(identifier)) throw new IOException("Duplicate exact FASTA identifier: " + identifier);
            id = identifier; header = text; length = 0; body = null; bodyOffset = 0;
            sequenceDigest = hasher(); recordOpen = true; return true;
        }
        int base() throws IOException {
            available();
            if (!recordOpen) throw new IOException("No open FASTA sequence");
            while (body == null || bodyOffset == body.length()) {
                String text = line();
                if (text == null || text.startsWith(">")) {
                    if (text != null) pendingHeader = text.substring(1);
                    if (length == 0) throw new IOException("Empty FASTA sequence: " + id);
                    sequences.add(new Sequence(ordinal, id, header, length, HexFormat.of().formatHex(sequenceDigest.digest())));
                    recordOpen = false; return -1;
                }
                if (text.isEmpty()) continue;
                body = text; bodyOffset = 0;
            }
            char value = body.charAt(bodyOffset++); char upper = value >= 'a' && value <= 'z' ? (char)(value - 32) : value;
            if ("ACGTURYKMSWBDHVNX".indexOf(upper) < 0)
                throw new IOException("FASTA requires IUPAC DNA without gaps or implicit whitespace removal: " + id);
            length = add(length, 1, "sequence length"); residues = add(residues, 1, "total residues");
            if (length > limits.perSequence() || residues > limits.residues()) throw new IOException("FASTA per-sequence/total residue capacity exceeded");
            sequenceDigest.update((byte)value); return value;
        }
        String id() { return id; }
        String header() { return header; }
        long position() { return length; }
        int ordinal() { return ordinal; }
        Census census() throws IOException {
            if (!finished) throw new IOException("A FASTA census requires complete physical/decoded EOF");
            if (completed == null) completed = new Census(sequences, residues, characters, raw.count,
                    HexFormat.of().formatHex(sourceDigest.digest()), retained);
            return completed;
        }
        private String line() throws IOException {
            check.run(); var line = new StringBuilder(); int value;
            while ((value = reader.read()) >= 0) {
                characters = add(characters, 1, "decoded characters");
                if (characters > limits.decodedCharacters()) throw new IOException("FASTA decoded-character capacity exceeded");
                if (value == '\n') break;
                if (line.length() >= limits.lineCharacters()) throw new IOException("FASTA line-character capacity exceeded");
                line.append((char)value);
                if ((line.length() & 4095) == 0) check.run();
            }
            if (value < 0 && line.isEmpty()) return null;
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
            if (line.indexOf("\r") >= 0) throw new IOException("Embedded carriage return in FASTA");
            return line.toString();
        }
        private void available() throws IOException { if (closed) throw new IOException("FASTA cursor is closed"); }
        @Override public void close() throws IOException { if (!closed) { closed = true; reader.close(); } }
    }
    private static final class Capped extends FilterInputStream {
        private final long maximum;
        private final Runnable check;
        private long count;
        Capped(InputStream input, long maximum, Runnable check) { super(input); this.maximum = maximum; this.check = check; }
        private void used(int n) throws IOException {
            check.run(); if (n > 0) { count = add(count, n, "source bytes"); if (count > maximum) throw new IOException("FASTA source-byte capacity exceeded"); }
        }
        @Override public int read() throws IOException { int value = in.read(); used(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { int n = in.read(bytes, offset, length); used(n); return n; }
    }
    private static long add(long a, long b, String what) throws IOException {
        try { return Math.addExact(a, b); } catch (ArithmeticException overflow) { throw new IOException("FASTA " + what + " overflow", overflow); }
    }
    private static MessageDigest hasher() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
