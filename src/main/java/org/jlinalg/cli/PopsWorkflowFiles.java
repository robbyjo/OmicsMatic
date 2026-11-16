/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Typed requests, exact input capture and deterministic file accounting. */
final class PopsWorkflowFiles {
    static final String PROFILE = "pops-native-prepared-v1";
    static final Set<String> PATH_KEYS = Set.of("gene_annotation", "feature_prefix", "magma_prefix", "scores", "score_covariates", "error_covariance", "subset_features", "control_features");
    static final Map<String, String> DEFAULTS = Map.ofEntries(
        Map.entry("batch_columns", "128"), Map.entry("max_prediction_genes", "500000"), Map.entry("max_target_genes", "100000"),
        Map.entry("max_features", "1000000"), Map.entry("max_selected_features", "10000"), Map.entry("max_covariance_entries", "100000000"),
        Map.entry("max_header_bytes", "1048576"), Map.entry("max_identifier_chars", "2048"),
        Map.entry("projection_chromosomes", "all"), Map.entry("selection_chromosomes", "all"), Map.entry("training_chromosomes", "all"),
        Map.entry("projection_remove_hla", "true"), Map.entry("selection_remove_hla", "true"), Map.entry("training_remove_hla", "true"),
        Map.entry("use_magma_covariates", "true"), Map.entry("use_magma_error_covariance", "true"), Map.entry("p_cutoff", "0.05"),
        Map.entry("max_selected_by_p", "none"), Map.entry("forward_steps", "none"));
    static final Set<String> REQUIRED = Set.of("profile", "edition", "method", "target_type", "gene_annotation", "feature_prefix", "feature_chunks",
        "genome_build", "feature_set_id", "target_source_id", "java_heap_mb", "wall_ms", "max_input_bytes", "max_work_bytes", "max_files");
    static final class Prerequisite extends IOException {
        private static final long serialVersionUID = 1L;
        Prerequisite(String message) { super(message); }
    }
    private static final class Unique extends Properties {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Object put(Object key, Object value) { if (containsKey(key)) throw new IllegalArgumentException("Duplicate request property: " + key); return super.put(key, value); }
    }
    record Config(Map<String, String> values) {
        Config { values = Collections.unmodifiableMap(new TreeMap<>(values)); }
        String get(String key) { return values.get(key); }
        int integer(String key) { return Integer.parseInt(get(key)); }
        long number(String key) { return Long.parseLong(get(key)); }
        boolean bool(String key) { return Boolean.parseBoolean(get(key)); }
        boolean magma() { return get("target_type").equals("magma"); }
    }
    private PopsWorkflowFiles() { }

    static Config config(Properties properties) throws IOException {
        Set<String> permitted = new HashSet<>(REQUIRED); permitted.addAll(DEFAULTS.keySet()); permitted.addAll(PATH_KEYS);
        Map<String, String> values = new TreeMap<>(DEFAULTS);
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (!permitted.contains(key)) throw new IllegalArgumentException("Unknown PoPS request field: " + key);
            if (value.isEmpty() || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Nonempty single-line request value required: " + key);
            values.put(key, value);
        }
        for (String key : REQUIRED) if (!values.containsKey(key)) throw new IllegalArgumentException("Required request field: " + key);
        if (!PROFILE.equals(values.get("profile")) || !values.get("edition").equals("0.2")) throw new IllegalArgumentException("This workflow implements the explicit native PoPS 0.2 prepared-input profile. Do not substitute it for PoPS 0.1.");
        if (!Set.of("ridge", "linreg").contains(values.get("method"))) throw new IllegalArgumentException("Native method must be ridge or linreg; LassoCV requires its original execution adapter");
        if (!Set.of("magma", "custom").contains(values.get("target_type"))) throw new IllegalArgumentException("target_type must be magma or custom");
        if (values.get("target_type").equals("magma")) {
            if (!values.containsKey("magma_prefix")) throw new IllegalArgumentException("magma_prefix required");
            for (String key : List.of("scores", "score_covariates", "error_covariance")) if (values.containsKey(key)) throw new IllegalArgumentException("Ambiguous custom input in MAGMA request: " + key);
        } else {
            if (!values.containsKey("scores") || values.containsKey("magma_prefix")) throw new IllegalArgumentException("Custom request requires scores and excludes magma_prefix");
            for (String key : List.of("use_magma_covariates", "use_magma_error_covariance")) if (properties.containsKey(key)) throw new IllegalArgumentException("MAGMA setting in custom request: " + key);
            values.remove("use_magma_covariates"); values.remove("use_magma_error_covariance");
        }
        for (String key : List.of("feature_chunks", "batch_columns", "max_prediction_genes", "max_target_genes", "max_features", "max_selected_features", "max_header_bytes", "max_identifier_chars", "java_heap_mb", "max_files"))
            if (Integer.parseInt(values.get(key)) < 1) throw new IllegalArgumentException("Positive bounded integer required: " + key);
        for (String key : List.of("max_input_bytes", "max_work_bytes", "wall_ms", "max_covariance_entries")) if (Long.parseLong(values.get(key)) < 1) throw new IllegalArgumentException("Positive capacity required: " + key);
        if (Integer.parseInt(values.get("feature_chunks")) > Integer.parseInt(values.get("max_files")) || Integer.parseInt(values.get("batch_columns")) > Integer.parseInt(values.get("max_features"))) throw new IllegalArgumentException("Feature chunk/batch capacity is inconsistent");
        for (String key : List.of("projection_remove_hla", "selection_remove_hla", "training_remove_hla", "use_magma_covariates", "use_magma_error_covariance"))
            if (values.containsKey(key) && !Set.of("true", "false").contains(values.get(key))) throw new IllegalArgumentException("Exact true/false required: " + key);
        if (!values.get("p_cutoff").equals("none")) finite(values.get("p_cutoff"));
        if (!values.get("max_selected_by_p").equals("none") && Integer.parseInt(values.get("max_selected_by_p")) < 0) throw new IllegalArgumentException("Nonnegative p-selection maximum required");
        if (!values.get("forward_steps").equals("none") && Integer.parseInt(values.get("forward_steps")) < 1) throw new IllegalArgumentException("Positive forward_steps required");
        return new Config(values);
    }

    static Properties load(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 1048576) throw new IOException("Regular request/receipt no larger than 1 MiB required: " + file);
        Unique properties = new Unique(); properties.load(new StringReader(Files.readString(file, StandardCharsets.UTF_8))); return properties;
    }
    static Properties asProperties(Map<String, String> values) { Properties p = new Properties(); p.putAll(values); return p; }
    static void properties(Path file, Map<String, String> values) throws IOException {
        StringBuilder text = new StringBuilder("# Explicit PoPS workflow record\n");
        for (var entry : new TreeMap<>(values).entrySet()) text.append(escape(entry.getKey())).append('=').append(escape(entry.getValue())).append('\n');
        write(file, text.toString());
    }
    private static String escape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\"); case '\n' -> result.append("\\n"); case '\r' -> result.append("\\r"); case '\t' -> result.append("\\t");
                case ' ', '=', ':', '#', '!' -> result.append('\\').append(c);
                default -> result.append(c);
            }
        }
        return result.toString();
    }
    static void write(Path file, String text) throws IOException { Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
    static String sha(Path file) throws IOException {
        MessageDigest digest = digest();
        try (var input = Files.newInputStream(file)) { byte[] buffer = new byte[1048576]; for (int n; (n = input.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    static String bytesSha(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    static void copy(Path source, Path target, long limit) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > limit) throw new IOException("Input unavailable or exceeds capacity: " + source);
        try (var in = Files.newInputStream(source); var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[1048576]; long total = 0;
            for (int n; (n = in.read(buffer)) >= 0;) { total = Math.addExact(total, n); if (total > limit) throw new IOException("Input grew beyond capacity during capture"); if (n > 0) output.write(buffer, 0, n); }
        }
        if (!sha(source).equals(sha(target))) throw new IOException("Input changed during capture: " + source);
    }
    static Path resolve(Path base, String value) { Path p = Path.of(value); return (p.isAbsolute() ? p : base.resolve(p)).toAbsolutePath().normalize(); }
    static double finite(String text) throws IOException { try { double v = Double.parseDouble(text); if (!Double.isFinite(v)) throw new NumberFormatException(); return v; } catch (NumberFormatException e) { throw new IOException("Finite numeric input required: " + text, e); } }
    static String identifier(String text, int maximum) throws IOException {
        if (text.isEmpty() || text.length() > maximum || text.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) throw new IOException("Nonempty literal identifier without whitespace/control characters required: " + text);
        return text;
    }
    static Map<String, String> inventory(Path root, Config config) throws IOException {
        Map<String, String> result = new TreeMap<>(); long bytes = 0; int count = 0;
        try (var stream = Files.walk(root)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next(); if (++count > config.integer("max_files")) throw new IOException("Work entry count exceeds max_files");
                if (Files.isSymbolicLink(path)) throw new IOException("Captured work aliases are not qualified: " + path);
                if (path.equals(root)) continue;
                String name = root.relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) result.put(name + "/", "0\t-");
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    long size = Files.size(path); bytes = Math.addExact(bytes, size); if (bytes > config.number("max_work_bytes")) throw new IOException("Work bytes exceed max_work_bytes");
                    result.put(name, size + "\t" + sha(path));
                } else throw new IOException("Unqualified work entry: " + path);
            }
        }
        return result;
    }
    static void manifest(Path file, Map<String, String> entries) throws IOException {
        StringBuilder text = new StringBuilder("path\tbytes\tsha256\n");
        for (var entry : entries.entrySet()) text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n'); write(file, text.toString());
    }
    static Map<String, String> manifest(Path file) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (var reader = Files.newBufferedReader(file)) {
            if (!"path\tbytes\tsha256".equals(reader.readLine())) throw new IOException("Manifest header differs");
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 3 || entries.putIfAbsent(fields[0], fields[1] + "\t" + fields[2]) != null) throw new IOException("Invalid or duplicate manifest member");
            }
        }
        return entries;
    }
}
