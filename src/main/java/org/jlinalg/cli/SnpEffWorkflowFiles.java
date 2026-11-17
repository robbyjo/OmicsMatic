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

/** Strict original-runtime identity, request and captured-directory operations. */
final class SnpEffWorkflowFiles {
    static final String PROFILE = "original-snpeff-annotation-v1";
    static final String MODERN = "5.2";
    static final String LEGACY = "3.1i-r354-source";
    static final Map<String,String> JARS = Map.of(
        MODERN, "3d630f73332cff635f64301d06e8cc3c12889c794903b8b9be3a82227e47a291",
        LEGACY, "d29e6257ee81795f895c8c1f94c10f9be00a616ce30cc1a662b36498d7ac6b27");
    static final Set<String> KEYS = Set.of("edition", "jar", "config", "data", "genome", "variants",
        "format", "up_down_stream", "heap_mb", "wall_ms", "max_bytes", "max_files");
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
    record Config(Properties properties) {
        Config { Properties copy = new Properties(); copy.putAll(properties); properties = copy; }
        String get(String key) { return properties.getProperty(key); }
        long number(String key) { return Long.parseLong(get(key)); }
        boolean legacy() { return get("edition").equals(LEGACY); }
    }
    record Pin(long bytes, String sha256) { }
    private SnpEffWorkflowFiles() { }

    static Properties read(Path path, boolean unique) throws IOException {
        Properties result = unique ? new UniqueProperties() : new Properties();
        // The official 5.2 config is 19,096,108 bytes; do not reject that source profile.
        if (Files.size(path) > 64L * 1024 * 1024) throw new IOException("Properties file exceeds the documented 64 MiB config/manifest limit");
        try (InputStream stream = Files.newInputStream(path)) { result.load(stream); }
        return result;
    }
    static void write(Path path, Properties value) throws IOException {
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            value.store(stream, "OmicsMatic original SnpEff workflow; preserve original evidence");
        }
    }
    static Config config(Properties properties) {
        if (!properties.stringPropertyNames().equals(KEYS))
            throw new IllegalArgumentException("Request must contain exactly: " + new TreeSet<>(KEYS));
        for (String key : KEYS) if (properties.getProperty(key).isBlank())
            throw new IllegalArgumentException("Blank request property: " + key);
        String edition = properties.getProperty("edition");
        if (!JARS.containsKey(edition)) throw new IllegalArgumentException("Edition must be 5.2 or 3.1i-r354-source; never substitute 3.1m");
        if (!properties.getProperty("format").equals(edition.equals(LEGACY) ? "txt" : "vcf"))
            throw new IllegalArgumentException("The 5.2 profile consumes/emits VCF; 3.1i-r354-source consumes/emits its distinct legacy TXT format");
        if (!properties.getProperty("genome").matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}"))
            throw new IllegalArgumentException("Use an exact portable SnpEff database identifier");
        number(properties, "up_down_stream", 0, 1000000);
        number(properties, "heap_mb", 64, 262144);
        number(properties, "wall_ms", 1, 86400000);
        number(properties, "max_bytes", 1, 1L << 40);
        number(properties, "max_files", 1, 1000000);
        return new Config(properties);
    }
    private static void number(Properties properties, String key, long min, long max) {
        String value = properties.getProperty(key);
        try {
            long n = Long.parseLong(value);
            if (!value.matches("0|[1-9][0-9]*") || n < min || n > max) throw new NumberFormatException();
        } catch (NumberFormatException error) { throw new IllegalArgumentException(key + " must be an integer from " + min + " through " + max, error); }
    }
    static Path input(Path request, String value, boolean directory, boolean prerequisite) throws IOException {
        Path path = request.toAbsolutePath().getParent().resolve(value).normalize();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (prerequisite) throw new Prerequisite("Missing required runtime/reference: " + path + "; supply the exact permitted edition/database and retry a NEW output directory");
            throw new IOException("Missing required scientific input: " + path);
        }
        if (Files.isSymbolicLink(path) || (directory ? !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) : !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("Require a regular " + (directory ? "directory" : "file") + ": " + path);
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
}
