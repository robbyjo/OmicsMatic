/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Complete byte capture with original filename/compression and directory-order semantics. */
final class PopsOriginalInputs {
    private record Capture(Path source, Path destination) { }
    private PopsOriginalInputs() { }
    static PopsOriginalFiles.Config capture(Path requestDirectory, PopsOriginalFiles.Config c, Path root) throws IOException {
        Map<String, String> values = new TreeMap<>(c.values()); List<Capture> captures = new ArrayList<>();
        values.put("runtime", resolve(requestDirectory, c.get("runtime")).toString());
        if (c.legacy()) {
            file(requestDirectory, c, root, values, captures, "features_text", "features.tsv"); prefix(requestDirectory, c, root, values, captures, "magma_prefix", "magma", List.of(".genes.out", ".genes.raw"));
            if (c.predicts()) { file(requestDirectory, c, root, values, captures, "gene_location", "gene-location.tsv"); file(requestDirectory, c, root, values, captures, "control_features", "control.features"); }
            if (c.get("task").equals("predict")) file(requestDirectory, c, root, values, captures, "selected_features", "selected.features");
        } else {
            file(requestDirectory, c, root, values, captures, "gene_annotation", "gene-annotation.tsv");
            if (c.get("task").equals("munge")) {
                Path directory = resolve(requestDirectory, c.get("feature_directory")); if (!Files.isDirectory(directory)) throw new IOException("Original feature directory unavailable: " + directory);
                Path target = root.resolve("inputs/raw-features"); Files.createDirectory(target);
                try (var paths = Files.list(directory)) {
                    for (Path path : paths.toList()) {
                        if (path.getFileName().toString().startsWith(".")) continue; // Original Python glob('*') excludes these.
                        if (path.getFileName().toString().chars().anyMatch(Character::isISOControl)) throw new IOException("Control characters in source directory member names are not supported");
                        if (!Files.isRegularFile(path)) throw new IOException("Original munger would not accept a non-file visible directory member: " + path);
                        captures.add(new Capture(path, target.resolve(path.getFileName().toString())));
                    }
                }
                values.put("feature_directory", "inputs/raw-features");
            } else {
                List<String> suffixes = new ArrayList<>(); suffixes.add(".rows.txt"); for (int i = 0; i < c.i("feature_chunks"); i++) { suffixes.add(".cols." + i + ".txt"); suffixes.add(".mat." + i + ".npy"); }
                prefix(requestDirectory, c, root, values, captures, "feature_prefix", "features", suffixes);
                if (c.get("target_type").equals("magma")) prefix(requestDirectory, c, root, values, captures, "magma_prefix", "magma", List.of(".genes.out", ".genes.raw"));
                else { file(requestDirectory, c, root, values, captures, "scores", "scores.tsv"); for (String key : List.of("score_covariates", "error_covariance")) if (c.get(key) != null) file(requestDirectory, c, root, values, captures, key, key); }
                for (String key : List.of("subset_features", "control_features")) if (c.get(key) != null) file(requestDirectory, c, root, values, captures, key, key + ".txt");
            }
        }
        if ((long) captures.size() + 70 > c.i("max_files")) throw new IOException("Complete source inputs and runtime records exceed max_files; increase capacity without dropping files");
        long bytes = 0; Set<Path> destinations = new HashSet<>(); StringBuilder log = new StringBuilder("source\tcaptured\tbytes\tsha256\n");
        for (Capture capture : captures) {
            if (!destinations.add(capture.destination)) throw new IOException("Captured filename collision; no input was renamed or overwritten");
            bytes = Math.addExact(bytes, Files.size(capture.source)); if (bytes > c.n("max_input_bytes")) throw new IOException("Complete source inputs exceed max_input_bytes");
            copy(capture.source, capture.destination, c.n("max_input_bytes"));
            log.append(capture.source).append('\t').append(root.relativize(capture.destination).toString().replace('\\', '/')).append('\t').append(Files.size(capture.destination)).append('\t').append(sha(capture.destination)).append('\n');
        }
        for (Capture capture : captures) if (!sha(capture.source).equals(sha(capture.destination))) throw new IOException("Source input changed during multi-file capture: " + capture.source);
        write(root.resolve("CAPTURE.tsv"), log.toString()); return PopsOriginalFiles.config(asProperties(values));
    }
    private static void file(Path base, PopsOriginalFiles.Config c, Path root, Map<String, String> values, List<Capture> files, String key, String name) {
        Path source = resolve(base, c.get(key)); String lower = source.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String suffix : List.of(".gz", ".bz2", ".xz", ".zip")) if (lower.endsWith(suffix)) { name += suffix; break; }
        String relative = "inputs/" + name; files.add(new Capture(source, root.resolve(relative))); values.put(key, relative);
    }
    private static void prefix(Path base, PopsOriginalFiles.Config c, Path root, Map<String, String> values, List<Capture> files, String key, String name, List<String> suffixes) {
        Path prefix = resolve(base, c.get(key)); for (String suffix : suffixes) files.add(new Capture(Path.of(prefix + suffix), root.resolve("inputs/" + name + suffix))); values.put(key, "inputs/" + name);
    }
    static void seal(Path root, PopsOriginalFiles.Config c) throws IOException { manifest(root.resolve("INPUTS.tsv"), PopsOriginalFiles.inventory(root.resolve("inputs"), c)); }
    static void check(Path root, PopsOriginalFiles.Config c) throws IOException {
        Map<String, String> current = PopsOriginalFiles.inventory(root.resolve("inputs"), c); long total = 0;
        for (var entry : current.entrySet()) if (!entry.getKey().endsWith("/")) { total = Math.addExact(total, Long.parseLong(entry.getValue().split("\t", -1)[0])); if (total > c.n("max_input_bytes")) throw new IOException("Complete captured inputs exceed max_input_bytes"); }
        if (!current.equals(manifest(root.resolve("INPUTS.tsv")))) throw new IOException("Captured original PoPS inputs changed; no accepted result");
    }
    static Path input(Path root, String value) throws IOException {
        Path path = resolve(root, value), inputs = root.resolve("inputs").toRealPath(); if (!path.startsWith(inputs) || !path.toRealPath().startsWith(inputs)) throw new IOException("Original workflow inputs must use the captured input tree"); return path;
    }
}
