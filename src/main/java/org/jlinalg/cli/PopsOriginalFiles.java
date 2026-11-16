/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Explicit original-edition contracts; no method or edition substitution. */
final class PopsOriginalFiles {
    static final String PROFILE = "pops-original-v1";
    static final Set<String> REQUIRED = Set.of("profile", "edition", "task", "runtime", "genome_build", "feature_set_id", "target_source_id", "java_heap_mb", "python_address_space_mb", "wall_ms", "max_input_bytes", "max_work_bytes", "max_files");
    static final Map<String, String> CAPACITY = Map.of("batch_columns", "128", "max_prediction_genes", "500000", "max_target_genes", "100000", "max_features", "1000000", "max_selected_features", "10000", "max_header_bytes", "16777216", "max_identifier_chars", "2048");
    static final Map<String, String> FIT_DEFAULTS = Map.ofEntries(
        Map.entry("method", "ridge"), Map.entry("p_cutoff", "0.05"), Map.entry("max_selected_by_p", "none"), Map.entry("forward_steps", "none"), Map.entry("random_seed", "42"), Map.entry("save_matrix_files", "false"),
        Map.entry("projection_chromosomes", "all"), Map.entry("selection_chromosomes", "all"), Map.entry("training_chromosomes", "all"),
        Map.entry("projection_remove_hla", "true"), Map.entry("selection_remove_hla", "true"), Map.entry("training_remove_hla", "true"));
    record Config(Map<String, String> values) {
        Config { values = Collections.unmodifiableMap(new TreeMap<>(values)); }
        String get(String key) { return values.get(key); }
        int i(String key) { return Integer.parseInt(get(key)); }
        long n(String key) { return Long.parseLong(get(key)); }
        boolean b(String key) { return Boolean.parseBoolean(get(key)); }
        boolean legacy() { return get("edition").equals("0.1"); }
        boolean predicts() { return legacy() && !get("task").equals("select"); }
        PopsWorkflowFiles.Config capacities() { return new PopsWorkflowFiles.Config(values); }
        List<Integer> chromosomes() {
            List<Integer> result = new ArrayList<>();
            if (get("chromosomes").equals("all")) for (int c = 1; c <= 22; c++) result.add(c);
            else for (String text : get("chromosomes").split(",", -1)) result.add(Integer.valueOf(text));
            return List.copyOf(result);
        }
    }
    private PopsOriginalFiles() { }
    private static final class Unique extends Properties {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Object put(Object key, Object value) { if (containsKey(key)) throw new IllegalArgumentException("Duplicate original request property: " + key); return super.put(key, value); }
    }
    static Config request(byte[] raw) throws IOException {
        if (raw.length > 1048576) throw new IOException("Original request exceeds 1 MiB");
        Properties p = new Unique(); p.load(new java.io.StringReader(new String(raw, java.nio.charset.StandardCharsets.UTF_8))); return config(p);
    }
    static Config config(Properties input) throws IOException {
        Map<String, String> v = new TreeMap<>(CAPACITY); for (String key : input.stringPropertyNames()) v.put(key, input.getProperty(key));
        for (String key : REQUIRED) if (!v.containsKey(key)) throw new IllegalArgumentException("Required original PoPS request field: " + key);
        if (!PROFILE.equals(v.get("profile")) || !Set.of("0.1", "0.2").contains(v.get("edition"))) throw new IllegalArgumentException("Explicit original PoPS profile and edition 0.1 or 0.2 required");
        Set<String> allowed = new HashSet<>(REQUIRED); allowed.addAll(CAPACITY.keySet());
        if (v.get("edition").equals("0.1")) {
            if (!Set.of("select", "predict", "select-predict").contains(v.get("task"))) throw new IllegalArgumentException("PoPS 0.1 task must be select, predict or select-predict");
            allowed.addAll(Set.of("features_text", "magma_prefix")); require(v, "features_text", "magma_prefix");
            if (!v.get("task").equals("select")) { allowed.addAll(Set.of("gene_location", "control_features", "chromosomes")); require(v, "gene_location", "control_features"); v.putIfAbsent("chromosomes", "all"); }
            if (v.get("task").equals("predict")) { allowed.add("selected_features"); require(v, "selected_features"); }
        } else if (v.get("task").equals("munge")) {
            allowed.addAll(Set.of("gene_annotation", "feature_directory", "nan_policy", "chunk_columns")); require(v, "gene_annotation", "feature_directory", "nan_policy");
            if (!Set.of("raise", "ignore", "mean", "zero").contains(v.get("nan_policy"))) throw new IllegalArgumentException("Explicit source nan_policy must be raise, ignore, mean or zero");
            v.putIfAbsent("chunk_columns", "5000"); if (Integer.parseInt(v.get("chunk_columns")) < 1) throw new IllegalArgumentException("Positive chunk_columns required");
        } else if (v.get("task").equals("fit")) {
            allowed.addAll(FIT_DEFAULTS.keySet()); for (var e : FIT_DEFAULTS.entrySet()) v.putIfAbsent(e.getKey(), e.getValue());
            allowed.addAll(Set.of("gene_annotation", "feature_prefix", "feature_chunks", "target_type", "subset_features", "control_features")); require(v, "gene_annotation", "feature_prefix", "feature_chunks", "target_type");
            if (!Set.of("ridge", "linreg", "lasso").contains(v.get("method"))) throw new IllegalArgumentException("Original PoPS 0.2 method must be ridge, linreg or lasso");
            if (v.get("target_type").equals("magma")) { allowed.addAll(Set.of("magma_prefix", "use_magma_covariates", "use_magma_error_covariance")); require(v, "magma_prefix"); v.putIfAbsent("use_magma_covariates", "true"); v.putIfAbsent("use_magma_error_covariance", "true"); }
            else if (v.get("target_type").equals("custom")) { allowed.addAll(Set.of("scores", "score_covariates", "error_covariance")); require(v, "scores"); }
            else throw new IllegalArgumentException("target_type must be magma or custom");
            if (Integer.parseInt(v.get("feature_chunks")) < 1 || Integer.parseInt(v.get("feature_chunks")) > Integer.parseInt(v.get("max_files"))) throw new IllegalArgumentException("Invalid feature_chunks capacity");
            finite(v.get("p_cutoff"));
            for (String key : Set.of("max_selected_by_p", "forward_steps")) if (!v.get(key).equals("none") && Integer.parseInt(v.get(key)) < (key.equals("forward_steps") ? 1 : 0)) throw new IllegalArgumentException("Invalid source selection count: " + key);
            long seed = Long.parseLong(v.get("random_seed")); if (seed < 0 || seed > 4294967295L) throw new IllegalArgumentException("Original NumPy random_seed must be in 0..4294967295");
            for (String key : Set.of("save_matrix_files", "projection_remove_hla", "selection_remove_hla", "training_remove_hla", "use_magma_covariates", "use_magma_error_covariance")) if (v.containsKey(key) && !Set.of("true", "false").contains(v.get(key))) throw new IllegalArgumentException("Exact true/false required: " + key);
        } else throw new IllegalArgumentException("PoPS 0.2 task must be fit or munge");
        for (var entry : v.entrySet()) if (!allowed.contains(entry.getKey()) || entry.getValue().isEmpty() || entry.getValue().chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Unknown/empty/multiline original request field: " + entry.getKey());
        for (String key : Set.of("batch_columns", "max_prediction_genes", "max_target_genes", "max_features", "max_selected_features", "max_header_bytes", "max_identifier_chars", "java_heap_mb", "max_files")) if (Integer.parseInt(v.get(key)) < 1) throw new IllegalArgumentException("Positive integer capacity required: " + key);
        for (String key : Set.of("wall_ms", "max_input_bytes", "max_work_bytes", "python_address_space_mb")) if (Long.parseLong(v.get(key)) < 1) throw new IllegalArgumentException("Positive capacity required: " + key);
        Math.multiplyExact(Long.parseLong(v.get("python_address_space_mb")), 1048576L);
        Config c = new Config(v);
        if (c.predicts()) { var chromosomes = c.chromosomes(); if (chromosomes.isEmpty() || new HashSet<>(chromosomes).size() != chromosomes.size() || chromosomes.stream().anyMatch(i -> i < 1 || i > 22)) throw new IllegalArgumentException("Unique original 0.1 chromosomes must be in 1..22, or all"); }
        return c;
    }
    private static void require(Map<String, String> values, String... keys) { for (String key : keys) if (!values.containsKey(key)) throw new IllegalArgumentException("Required request field: " + key); }
    static Config loadConfig(Path file) throws IOException { return config(load(file)); }
    static void capacity(Path root, Config c) throws IOException { PopsWorkflowProcess.capacity(root, c.capacities()); }
    static Map<String, String> inventory(Path root, Config c) throws IOException { return PopsWorkflowFiles.inventory(root, c.capacities()); }
}
