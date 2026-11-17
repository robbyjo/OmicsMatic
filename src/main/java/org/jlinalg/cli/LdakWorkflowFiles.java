/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Capture and directory-integrity helpers for the original LDAK workflow profile. */
final class LdakWorkflowFiles {
    static final String PROFILE = "original-ldak52-captured-workflow-v1";
    static final String BINARY_SHA = "af969a0e07c51d5b0cdb364d815406a518150cb16e0993c1f93fb7f3fe455245";
    static final String LICENSE_SHA = "a1c2c9c0ab366c35da4515531bb402ce058a776ba034eb3d4c162ece06f9be84";
    record Pin(long bytes, String sha256) { }
    static final class Prerequisite extends IOException {
        private static final long serialVersionUID = 1L;
        Prerequisite(String message) { super(message); }
    }
    private static final class UniqueProperties extends Properties {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) throw new IllegalArgumentException("Duplicate property: " + key);
            return super.put(key, value);
        }
    }
    private LdakWorkflowFiles() { }
    static Properties load(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw new IOException("Require a regular properties file: " + path);
        if (Files.size(path) > 64L * 1024 * 1024) throw new IOException("Properties file exceeds the 64 MiB profile limit");
        Properties result = new UniqueProperties();
        try (InputStream input = Files.newInputStream(path)) { result.load(input); }
        return result;
    }
    static void store(Path path, Properties values) throws IOException {
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            values.store(stream, "OmicsMatic original LDAK workflow; preserve original evidence");
        }
    }
    static Path input(Path request, String value, boolean prerequisite) throws IOException {
        Path path = request.toAbsolutePath().getParent().resolve(value).normalize();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (prerequisite) throw new Prerequisite("Missing required original runtime/license/reference: " + path);
            throw new IOException("Missing scientific input: " + path);
        }
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Require a regular source file: " + path);
        return path.toRealPath();
    }
    static Path destination(Path requested, Iterable<Path> inputs) throws IOException {
        Path absolute = requested.toAbsolutePath().normalize();
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Output already exists; retain it and select a NEW path");
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Output requires a parent directory");
        Path existing = parent;
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        Path resolved = existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        for (Path input : inputs) if (resolved.startsWith(input) || input.startsWith(resolved))
            throw new IOException("Output overlaps an input/runtime/reference: " + input);
        return resolved;
    }
    static Pin pin(Path path) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        long size = 0;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int n; (n = input.read(buffer)) != -1;) { digest.update(buffer, 0, n); size = Math.addExact(size, n); }
        }
        return new Pin(size, HexFormat.of().formatHex(digest.digest()));
    }
    static Map<String,Pin> inventory(Path root, long maxBytes, long maxFiles) throws IOException {
        Map<String,Pin> result = new TreeMap<>();
        long[] total = {0, 0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(path) || !attrs.isDirectory()) throw new IOException("Linked or special reference directory: " + path);
                if (++total[1] > maxFiles) throw new IOException("Reference/workspace entry budget exceeded");
                String name = root.relativize(path).toString().replace('\\', '/');
                if (!name.isEmpty()) result.put(name + "/", new Pin(0, "directory"));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || Files.isSymbolicLink(path)) throw new IOException("Linked/special file: " + path);
                if (++total[1] > maxFiles || Math.addExact(total[0], attrs.size()) > maxBytes) throw new IOException("Reference/workspace budget exceeded");
                Pin pin = pin(path); total[0] = Math.addExact(total[0], pin.bytes());
                if (total[0] > maxBytes) throw new IOException("Reference/workspace byte budget exceeded");
                result.put(root.relativize(path).toString().replace('\\', '/'), pin);
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }
    static void copyTree(Path source, Path target, Map<String,Pin> expected) throws IOException {
        Files.createDirectories(target);
        for (Map.Entry<String,Pin> entry : expected.entrySet()) {
            Path from = source.resolve(entry.getKey()), to = target.resolve(entry.getKey());
            if (entry.getKey().endsWith("/")) { Files.createDirectories(to); continue; }
            Files.createDirectories(to.getParent()); copy(from, to);
            if (!entry.getValue().equals(pin(to))) throw new IOException("Reference changed while capturing: " + from);
        }
    }
    static void copy(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Source must be a regular file: " + source);
        Pin before = pin(source); Files.copy(source, target);
        if (!before.equals(pin(target)) || !before.equals(pin(source))) throw new IOException("Source changed during capture: " + source);
    }
    static String encoded(String name) { return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8)); }
    static Properties pins(Map<String,Pin> members) {
        Properties result = new Properties();
        members.forEach((name, pin) -> { result.setProperty("sha256." + encoded(name), pin.sha256()); result.setProperty("bytes." + encoded(name), Long.toString(pin.bytes())); });
        return result;
    }
    /** Bounded UTF-8 lines; original files themselves remain byte-for-byte intact. */
    static final class Lines implements AutoCloseable {
        private final java.io.BufferedReader reader;
        Lines(Path path) throws IOException { reader = Files.newBufferedReader(path, StandardCharsets.UTF_8); }
        String next() throws IOException {
            StringBuilder line = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1 && ch != '\n') {
                if (line.length() >= 16 * 1024 * 1024) throw new IOException("Text line exceeds the 16 MiB character profile limit");
                line.append((char) ch);
            }
            if (ch == -1 && line.isEmpty()) return null;
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
            return line.toString();
        }
        @Override public void close() throws IOException { reader.close(); }
    }
}
