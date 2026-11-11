/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Protected bytes and bounded UTF-8 decoding for scvi-tools inputs. */
final class ScviText {
    private ScviText() { }
    enum Encoding { PLAIN_UTF8, GZIP_UTF8 }
    record Source(Path path, Encoding encoding) {
        Source { java.util.Objects.requireNonNull(path); java.util.Objects.requireNonNull(encoding); }
    }
    record Snapshot(Path original, Path copy, Encoding encoding, long bytes, String sha256) {
        void verify(long maximum, Runnable check) throws IOException {
            if (!hash(original, maximum, check).equals(sha256) || !hash(copy, maximum, check).equals(sha256))
                throw new IOException("scVI input source/protected copy changed: " + original);
        }
    }
    static Path ordinary(Path path) throws IOException {
        Path p = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || !p.toRealPath().equals(p))
            throw new IOException("Require a canonical ordinary scVI input file: " + p);
        return p;
    }
    static Snapshot copy(Source source, Path destination, long maximum, Runnable check) throws IOException {
        Path original = ordinary(source.path()); String before = hash(original, maximum, check);
        long count = 0;
        try (var in = Files.newInputStream(original); var out = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) >= 0) { check.run(); count = Math.addExact(count, n); if (count > maximum) throw new IOException("Encoded scVI input exceeds its capacity"); out.write(buffer, 0, n); }
        }
        Snapshot result = new Snapshot(original, destination, source.encoding(), count, before); result.verify(maximum, check); return result;
    }
    static String hash(Path path, long maximum, Runnable check) throws IOException {
        ordinary(path); long count = 0;
        try (var input = new DigestInputStream(Files.newInputStream(path), digest())) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) >= 0) { check.run(); count = Math.addExact(count, n); if (count > maximum) throw new IOException("scVI protected file exceeds hash capacity: " + path); }
            return HexFormat.of().formatHex(input.getMessageDigest().digest());
        }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static final class DecodedBudget {
        private final long maximum; private long bytes;
        DecodedBudget(long maximum) { if (maximum < 1) throw new IllegalArgumentException("Positive decoded byte capacity required"); this.maximum = maximum; }
        long bytes() { return bytes; }
        void add(long n) throws IOException { bytes = Math.addExact(bytes, n); if (bytes > maximum) throw new IOException("Complete decoded scVI inputs exceed capacity"); }
    }
    static final class Lines implements AutoCloseable {
        private final Reader reader; private final int maximum; private final Runnable check;
        Lines(Source source, DecodedBudget budget, int maximum, Runnable check) throws IOException {
            this.maximum = maximum; this.check = check; InputStream raw = Files.newInputStream(source.path());
            try {
                InputStream decoded = source.encoding() == Encoding.GZIP_UTF8 ? new RepeatGzip(raw, check) : raw;
                InputStream counted = new FilterInputStream(decoded) {
                    @Override public int read() throws IOException { int value = in.read(); if (value >= 0) budget.add(1); return value; }
                    @Override public int read(byte[] b, int off, int len) throws IOException { int n = in.read(b, off, len); if (n > 0) budget.add(n); return n; }
                };
                reader = new BufferedReader(new InputStreamReader(counted, StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)), 8192);
            } catch (RuntimeException | Error failure) { raw.close(); throw failure; }
        }
        String next() throws IOException {
            check.run(); var line = new StringBuilder(Math.min(maximum, 256)); int c;
            while ((c = reader.read()) >= 0) {
                if (c == '\n') break;
                if (line.length() >= maximum) throw new IOException("scVI text line exceeds declared character capacity");
                line.append((char)c);
            }
            if (c < 0 && line.isEmpty()) return null;
            if (!line.isEmpty() && line.charAt(line.length()-1) == '\r') line.setLength(line.length()-1);
            if (line.indexOf("\r") >= 0 || line.indexOf("\u0000") >= 0) throw new IOException("Embedded CR/NUL in scVI input text");
            return line.toString();
        }
        @Override public void close() throws IOException { reader.close(); }
    }
}
