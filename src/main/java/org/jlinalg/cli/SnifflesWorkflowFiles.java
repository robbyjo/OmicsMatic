/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded stable files and strict typed metadata; hashes establish integrity, not authenticity. */
final class SnifflesWorkflowFiles {
    private SnifflesWorkflowFiles() { }
    record Pin(long bytes, String sha256) { }
    static void require(boolean condition, String detail) throws IOException { if (!condition) throw new IOException(detail); }
    static Path canonical(Path value) throws IOException {
        require(value.isAbsolute() && value.normalize().equals(value) && value.equals(value.toRealPath()), "Require canonical absolute path: " + value);
        return value;
    }
    static Path regular(Path value) throws IOException {
        canonical(value); require(Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS), "Require original regular file: " + value); return value;
    }
    static void originalPath(Path value) throws IOException {
        require(value.isAbsolute() && value.normalize().equals(value) && value.toString().matches("/[A-Za-z0-9_./+\\-]+"),
                "Linux ELF inspection requires normalized absolute runtime paths with letters, digits, underscore, dot, slash, plus or minus");
    }
    private static BasicFileAttributes stamp(Path value) throws IOException {
        return Files.readAttributes(value, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static void same(BasicFileAttributes before, BasicFileAttributes after, long bytes) throws IOException {
        require(before.isRegularFile() && after.isRegularFile() && before.size() == bytes && after.size() == bytes
                && before.lastModifiedTime().equals(after.lastModifiedTime()) && Objects.equals(before.fileKey(), after.fileKey()), "File changed during complete read");
    }
    static Pin pin(Path value, long maximum, Runnable check) throws IOException {
        regular(value); var before = stamp(value); require(before.size() <= maximum, "Complete file exceeds byte capacity: " + value);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
        long bytes = 0;
        try (var input = Files.newInputStream(value, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[1048576];
            for (int read; (read = input.read(buffer)) != -1;) { require(!Thread.currentThread().isInterrupted(), "Complete file read interrupted"); check.run(); bytes = Math.addExact(bytes, read); require(bytes <= maximum, "Complete file byte capacity exceeded"); digest.update(buffer, 0, read); }
        }
        same(before, stamp(value), bytes); return new Pin(bytes, HexFormat.of().formatHex(digest.digest()));
    }
    static String hash(Path value, long maximum, Runnable check) throws IOException { return pin(value, maximum, check).sha256(); }
    static Pin fingerprint(byte[] raw) {
        try { return new Pin(raw.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }
    static byte[] bytes(Path value, int maximum) throws IOException {
        regular(value); var before = stamp(value); require(before.size() <= maximum, "Metadata exceeds byte capacity: " + value);
        byte[] raw; try (var input = Files.newInputStream(value, LinkOption.NOFOLLOW_LINKS)) { raw = input.readNBytes(maximum + 1); }
        require(raw.length <= maximum, "Metadata byte capacity exceeded"); same(before, stamp(value), raw.length); return raw;
    }
    static String utf8(byte[] raw) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
    }
    static Map<?, ?> object(Object value) throws IOException { require(value instanceof Map<?, ?>, "Expected metadata object"); return (Map<?, ?>) value; }
    static List<?> list(Object value) throws IOException { require(value instanceof List<?>, "Expected metadata list"); return (List<?>) value; }
    static String text(Object value) throws IOException { require(value instanceof String, "Expected metadata text"); return (String) value; }
    static boolean bool(Object value) throws IOException { require(value instanceof Boolean, "Expected explicit Boolean"); return (Boolean) value; }
    static long integer(Object value, long minimum, long maximum) throws IOException {
        require(value instanceof Long || value instanceof Integer, "Expected exact scalar integer"); long result = ((Number) value).longValue();
        require(result >= minimum && result <= maximum, "Integer is outside declared bounds"); return result;
    }
    static Pin fingerprint(Object value, long maximum) throws IOException {
        var map = object(value); require(map.keySet().equals(Set.of("bytes", "sha256")), "Require complete bytes/sha256 pin");
        String digest = text(map.get("sha256")); require(digest.matches("[a-f0-9]{64}"), "Invalid SHA-256");
        return new Pin(integer(map.get("bytes"), 0, maximum), digest);
    }
    static Map<?, ?> read(Path value, int maximum) throws IOException { return object(SimpleJson.parse(utf8(bytes(value, maximum)))); }
    static void write(Path value, Object object) throws IOException { Files.write(value, SnifflesWorkflowJson.bytes(object), StandardOpenOption.CREATE_NEW); }
    static Path member(String name) throws IOException {
        require(!name.isEmpty() && !name.contains("\\") && !name.contains(":"), "Invalid relative member");
        Path value = Path.of(name); require(!value.isAbsolute() && value.normalize().equals(value) && !value.startsWith("..") && !name.equals("."), "Relative member escapes its root");
        return value;
    }
}
