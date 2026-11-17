/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jlinalg.cli.LdakWorkflowRequest.Config;
import org.jlinalg.genetics.LdakBedDataset;
import static org.jlinalg.cli.LdakWorkflowFiles.*;

/** Complete literal input capture and source-specific preflight; never rewrites scientific data. */
final class LdakWorkflowInputs {
    record SumherStats(long taggingRows, long summaryRows, int categories, long rawZeroStatistics, long rawTaggingsBelowOne,
            long referencePredictors, long regressionPredictors, long heritabilityPredictors) { }
    private LdakWorkflowInputs() { }
    static String canonical(String key) {
        return switch (key) {
            case "executable" -> "runtime/ldak5.2.linux";
            case "license" -> "runtime/LICENSE-UPSTREAM.txt";
            case "bfile" -> "inputs/genotypes";
            case "tagging" -> "inputs/reference.tagging";
            default -> "inputs/" + key;
        };
    }
    static List<Path> paths(Path request, Config c) throws IOException {
        var paths = new ArrayList<Path>(); paths.add(request.toRealPath());
        for (String key : c.inputKeys()) paths.add(input(request, c.get(key), key.equals("executable") || key.equals("license") || key.equals("tagging")));
        if (!c.sumher()) for (String extension : List.of("bed", "bim", "fam")) paths.add(input(request, c.get("bfile") + "." + extension, false));
        return List.copyOf(paths);
    }
    static Config capture(Path request, Config original, Path out) throws IOException {
        long bytes = 0;
        for (Path path : paths(request, original)) bytes = Math.addExact(bytes, Files.size(path));
        if (bytes > original.number("max_bytes")) throw new IOException("Complete runtime/input capture exceeds max_bytes; inputs are not truncated");
        Files.createDirectory(out.resolve("inputs")); Files.createDirectory(out.resolve("runtime"));
        copy(request, out.resolve("original-request.properties"));
        Properties captured = original.values();
        for (String key : original.inputKeys()) {
            copy(input(request, original.get(key), key.equals("executable") || key.equals("license") || key.equals("tagging")), out.resolve(canonical(key)));
            captured.setProperty(key, canonical(key));
        }
        if (!original.sumher()) {
            for (String extension : List.of("bed", "bim", "fam"))
                copy(input(request, original.get("bfile") + "." + extension, false), out.resolve("inputs/genotypes." + extension));
            captured.setProperty("bfile", canonical("bfile"));
        }
        if (!pin(out.resolve("runtime/ldak5.2.linux")).sha256().equals(BINARY_SHA)) throw new IOException("Original runtime SHA-256 differs from the selected 5.2 profile");
        if (!pin(out.resolve("runtime/LICENSE-UPSTREAM.txt")).sha256().equals(LICENSE_SHA)) throw new IOException("Original GPL notice does not match the selected upstream source");
        Config c = LdakWorkflowRequest.parse(captured);
        store(out.resolve("request.properties"), captured);
        preflight(out, c);
        return c;
    }
    static void capturedPaths(Config c) throws IOException {
        for (String key : c.inputKeys()) if (!c.get(key).equals(canonical(key))) throw new IOException("Noncanonical/external captured path: " + key);
        if (!c.sumher() && !c.get("bfile").equals(canonical("bfile"))) throw new IOException("Noncanonical captured BED prefix");
    }
    static void preflight(Path root, Config c) throws IOException {
        capturedPaths(c);
        if (c.sumher()) { sumher(root, c); return; }
        Set<String> selected = c.get("predictors").equals("-") ? null : ids(root.resolve("inputs/predictors"), c.integer("max_variants"));
        if (selected != null && selected.isEmpty()) throw new IOException("Empty declared predictor selection");
        try (var data = LdakBedDataset.open(root.resolve("inputs/genotypes"), selected, limits(c))) {
            long n = data.samples().size(), b = Math.min(c.integer("block_variants"), data.selectedVariants().size());
            long bytes = Math.multiplyExact(8L, Math.addExact(Math.multiplyExact(n, n), Math.multiplyExact(2 * n, b + 1)));
            if (bytes > c.number("max_numeric_bytes") || n * n > Integer.MAX_VALUE)
                throw new IOException("Native kinship audit capacity exceeded before original execution; no samples or variants are dropped");
            Map<String, Double> weights = weights(root, c);
            if (weights != null) {
                var admitted = new HashSet<String>(); for (var v : data.selectedVariants()) admitted.add(v.id());
                if (!admitted.containsAll(weights.keySet())) throw new IOException("Weight IDs lie outside the declared input selection; no identifiers are repaired or silently removed");
            }
        }
    }
    static LdakBedDataset.Limits limits(Config c) {
        return new LdakBedDataset.Limits(c.integer("max_samples"), c.integer("max_variants"), c.number("max_metadata_bytes"), c.integer("max_line_bytes"));
    }
    static Set<String> ids(Path path, int maximum) throws IOException {
        var ids = new LinkedHashSet<String>();
        try (var lines = new Lines(path)) {
            for (String line; (line = lines.next()) != null;) {
                if (line.isBlank()) continue;
                String[] fields = fields(line);
                if (fields.length != 1 || ids.size() >= maximum || !ids.add(fields[0])) throw new IOException("Require bounded, unique one-column predictor IDs: " + path);
            }
        }
        return Set.copyOf(ids);
    }
    static Map<String, Double> weights(Path root, Config c) throws IOException {
        if (c.get("weights").equals("-")) return null;
        var result = new HashMap<String, Double>();
        try (var lines = new Lines(root.resolve("inputs/weights"))) {
            for (String line; (line = lines.next()) != null;) {
                if (line.isBlank()) continue;
                String[] fields = fields(line);
                if (fields.length != 2 || result.size() >= c.integer("max_variants")) throw new IOException("Require bounded two-column predictor/weight input");
                double value = finite(fields[1], "predictor weight");
                if (value < 0 || result.putIfAbsent(fields[0], value) != null) throw new IOException("Negative or duplicate predictor weight");
            }
        }
        return Map.copyOf(result);
    }
    static SumherStats sumher(Path root, Config c) throws IOException {
        long tagging = 0, summary = 0, zeros = 0, floored = 0;
        int columns;
        boolean footer = false, finalFooter = false;
        long referencePredictors = 0, regressionPredictors = 0, heritabilityPredictors = 0;
        var contributionRows = new HashSet<String>();
        var identifiers = new HashSet<String>();
        try (var lines = new Lines(root.resolve("inputs/reference.tagging"))) {
            String line = lines.next();
            if (line == null) throw new IOException("Empty original tagging reference");
            String[] header = fields(line);
            if (header.length < 10 || !List.of(header).subList(0, 9).equals(List.of("Predictor", "A1", "A2", "Neighbours", "Tagging", "Weight", "MAF", "Categories", "Exp_Heritability")))
                throw new IOException("Require the uncompressed original tagging format, including all annotation columns and footer");
            columns = header.length;
            while ((line = lines.next()) != null) {
                if (line.isBlank()) continue;
                if (line.startsWith("The relative contribution of ")) {
                    if (finalFooter) throw new IOException("Contribution row after final tagging footer");
                    int marker = line.indexOf(" to each category ");
                    if (marker < 0 || !contributionRows.add(line.substring(0, marker))) throw new IOException("Malformed/duplicate tagging contribution row");
                    String[] contributions = fields(line.substring(marker + " to each category ".length()));
                    if (contributions.length != columns - 9) throw new IOException("Tagging contribution row omits model categories");
                    for (String value : contributions) finite(value, "tagging contribution");
                    footer = true; continue;
                }
                if (line.startsWith("There are ")) {
                    if (finalFooter) throw new IOException("Repeated tagging final footer");
                    String[] counts = fields(line);
                    if (counts.length != 9 + columns - 9 || !counts[0].equals("There") || !counts[1].equals("are")
                            || !counts[3].equals("reference") || !counts[5].equals("regression") || !counts[7].equals("heritability") || !counts[8].equals("predictors"))
                        throw new IOException("Unknown/incomplete original tagging population footer");
                    referencePredictors = population(counts[2]); regressionPredictors = population(counts[4]); heritabilityPredictors = population(counts[6]);
                    for (int i = 9; i < counts.length; i++) population(counts[i]);
                    footer = true; finalFooter = true; continue;
                }
                if (footer) throw new IOException("Predictor data after original tagging footer");
                String[] f = fields(line);
                if (f.length != columns || tagging >= c.number("max_predictors") || !identifiers.add(f[0])) throw new IOException("Tagging shape, duplicate ID or predictor limit differs");
                for (int i = 3; i < f.length; i++) finite(f[i], "tagging field");
                if (Double.parseDouble(f[4]) < 1) floored++;
                tagging++;
            }
        }
        if (tagging == 0 || !finalFooter || contributionRows.size() != columns - 9 || regressionPredictors != tagging
                || referencePredictors < regressionPredictors || heritabilityPredictors < 1 || heritabilityPredictors > referencePredictors)
            throw new IOException("Incomplete/inconsistent original tagging reference, regression, heritability or category populations");
        identifiers.clear();
        try (var lines = new Lines(root.resolve("inputs/summary"))) {
            String header = lines.next();
            if (header == null || !List.of(fields(header)).equals(List.of("Predictor", "A1", "A2", "Direction", "Stat", "n")))
                throw new IOException("This profile requires the six-column original SumHer summary format: Predictor A1 A2 Direction Stat n");
            for (String line; (line = lines.next()) != null;) {
                if (line.isBlank()) continue;
                String[] f = fields(line);
                if (f.length != 6 || summary >= c.number("max_predictors") || !identifiers.add(f[0])) throw new IOException("Summary shape, duplicate ID or predictor limit differs");
                double direction = finite(f[3], "summary direction"), stat = finite(f[4], "summary statistic"), n = finite(f[5], "summary sample size");
                if ((direction != -1 && direction != 1) || stat < 0 || !(n > 0)) throw new IOException("Invalid original prepared summary value; no sign/statistic/sample-size repair is allowed");
                if (stat == 0) zeros++;
                summary++;
            }
        }
        if (summary == 0) throw new IOException("No prepared summary rows");
        return new SumherStats(tagging, summary, columns - 9, zeros, floored, referencePredictors, regressionPredictors, heritabilityPredictors);
    }
    private static long population(String raw) throws IOException {
        try { long n = Long.parseLong(raw); if (n < 0) throw new NumberFormatException(); return n; }
        catch (NumberFormatException error) { throw new IOException("Invalid original tagging population count", error); }
    }
    static String[] fields(String line) throws IOException {
        if (line.indexOf('\0') >= 0 || line.indexOf('\ufeff') >= 0) throw new IOException("NUL/BOM in structured text");
        return line.trim().split("\\s+");
    }
    static double finite(String token, String label) throws IOException {
        try {
            double x = Double.parseDouble(token);
            if (!Double.isFinite(x)) throw new NumberFormatException();
            return x;
        } catch (NumberFormatException error) { throw new IOException("Nonfinite/invalid " + label + "; retain original data, do not repair the value", error); }
    }
}
