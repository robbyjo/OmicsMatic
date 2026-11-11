/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Bounded LF/UTF-8 readers for the original Linux intermediate-file formats. */
final class RsemText implements AutoCloseable {
    private final Path path;
    private final BasicFileAttributes initial;
    private final MessageDigest digest;
    private final Counter counter;
    private final BufferedReader reader;
    private final int lineLimit;
    private long lineNumber;
    private boolean eof;
    private RsemEvidence evidence;
    private String[] tokens = new String[0];
    private int tokenIndex;

    RsemText(Path source, long maxBytes, int maxLineChars) throws IOException {
        if (maxBytes < 1 || maxLineChars < 1) throw new IllegalArgumentException("Positive text limits required");
        if (Files.isSymbolicLink(source)) throw new IOException("RSEM source must be a regular file, not a final symlink: " + source);
        path = source.toRealPath(); initial = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!initial.isRegularFile() || initial.size() > maxBytes) throw new IOException("RSEM source is not regular or exceeds byte capacity: " + source);
        try {digest = MessageDigest.getInstance("SHA-256");}
        catch (NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
        counter = new Counter(Files.newInputStream(path), maxBytes);
        reader = new BufferedReader(new InputStreamReader(new DigestInputStream(counter, digest),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
        lineLimit = maxLineChars;
    }

    String line() throws IOException {
        if (eof) return null;
        StringBuilder text = new StringBuilder();
        for (;;) {
            int ch = reader.read();
            if (ch < 0) {
                eof = true;
                if (text.length() == 0) return null;
                lineNumber++; return text.toString();
            }
            if (ch == '\n') {lineNumber++; return text.toString();}
            if (ch == '\r') throw error("Original internal files require LF; CR/CRLF conversion must not silently alter identifiers");
            if (ch == 0 || (ch < 32 && ch != '\t')) throw error("Unexpected control byte in original internal text");
            if (text.length() >= lineLimit) throw error("Line exceeds declared character capacity");
            text.append((char) ch);
        }
    }

    String requiredLine(String role) throws IOException {
        String value = line(); if (value == null) throw error("Truncated " + role); return value;
    }

    String token(String role) throws IOException {
        for (;;) {
            if (tokenIndex < tokens.length) {
                String value = tokens[tokenIndex++];
                if (value.length() > 512) throw error("Numeric token exceeds capacity: " + role);
                return value;
            }
            String value = line(); if (value == null) throw error("Truncated " + role);
            value = strip(value);
            if (!value.isEmpty()) {tokens = value.split("[ \\t]+", -1); tokenIndex = 0;}
        }
    }

    String optionalToken() throws IOException {
        for (;;) {
            if (tokenIndex < tokens.length) {
                String value = tokens[tokenIndex++];
                if (value.length() > 512) throw error("Numeric token exceeds capacity");
                return value;
            }
            String value = line(); if (value == null) return null;
            value = strip(value);
            if (!value.isEmpty()) {tokens = value.split("[ \\t]+", -1); tokenIndex = 0;}
        }
    }

    int integer(String role, int minimum, int maximum) throws IOException {
        return (int) integer(token(role), role, minimum, maximum);
    }

    long integer(String word, String role, long minimum, long maximum) throws IOException {
        if (!word.matches("[0-9]+")) throw error("Noninteger " + role);
        try {
            long value = Long.parseLong(word);
            if (value < minimum || value > maximum) throw error("Out-of-range " + role);
            return value;
        } catch (NumberFormatException e) {throw error("Integer overflow in " + role);}
    }

    void endTokens() throws IOException {
        if (optionalToken() != null) throw error("Unexpected trailing data");
    }

    void endLines() throws IOException {
        String value;
        while ((value = line()) != null) if (!strip(value).isEmpty()) throw error("Unexpected trailing data");
    }

    RsemEvidence evidence() throws IOException {
        if (!eof) throw error("Evidence requested before complete source consumption");
        if (evidence == null) {
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile() || after.size() != initial.size() || counter.count != initial.size()
                    || !after.lastModifiedTime().equals(initial.lastModifiedTime()) || !Objects.equals(after.fileKey(), initial.fileKey()))
                throw error("Source identity, size or modification time changed while reading");
            evidence = new RsemEvidence(path.toString(), HexFormat.of().formatHex(digest.digest()), counter.count);
        }
        return evidence;
    }

    IOException error(String message) {return new IOException(path + ":" + (lineNumber + 1) + ": " + message);}
    static String strip(String text) {
        int first = 0, end = text.length();
        while (first < end && (text.charAt(first) == ' ' || text.charAt(first) == '\t')) first++;
        while (end > first && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) end--;
        return text.substring(first, end);
    }
    static String[] words(String text) {String value = strip(text); return value.isEmpty() ? new String[0] : value.split("[ \\t]+", -1);}
    @Override public void close() throws IOException {reader.close();}

    private static final class Counter extends FilterInputStream {
        private final long maximum;
        private long count;
        Counter(java.io.InputStream input, long maximum) {super(input); this.maximum = maximum;}
        private void add(int amount) throws IOException {if (amount > 0) {count += amount; if (count > maximum) throw new IOException("RSEM source grew beyond byte capacity");}}
        @Override public int read() throws IOException {int value = in.read(); if (value >= 0) add(1); return value;}
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {int amount = in.read(bytes, offset, length); add(amount); return amount;}
    }
}
