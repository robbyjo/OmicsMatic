/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.SignatureScores;
import static org.jlinalg.cli.FollowupSupport.*;

/** Streaming sample-local signature scores; fixed measured feature universe. */
final class SignatureScoreCli {
    private SignatureScoreCli() { }

    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (Arrays.asList(arguments).contains("--help")) { output.println(help()); return 0; }
        try {
            Map<String, String> options = options(arguments, Set.of("matrix", "gene-sets", "method",
                "max-rank", "alpha", "missing", "out"));
            String method = required(options, "method");
            if (!Set.of("ucell", "aucell", "ssgsea").contains(method))
                throw new IllegalArgumentException("--method must be ucell, aucell or ssgsea");
            if (method.equals("ssgsea") && options.containsKey("max-rank")
                    || !method.equals("ssgsea") && options.containsKey("alpha"))
                throw new IllegalArgumentException("--alpha applies to ssgsea; --max-rank applies to ucell/aucell");
            String missing = options.getOrDefault("missing", "error");
            if (!Set.of("error", "skip", "impute").contains(missing)
                    || missing.equals("impute") && !method.equals("ucell"))
                throw new IllegalArgumentException("--missing supports error|skip; impute is UCell-only");
            transaction(options, "signature-score-" + method, (directory, manifest) -> {
                Path matrix = Path.of(required(options, "matrix"));
                Map<String, List<String>> sets = readSets(Path.of(required(options, "gene-sets")));
                try (var reader = Files.newBufferedReader(matrix);
                     var writer = Files.newBufferedWriter(directory.resolve("scores.tsv"))) {
                    String line = reader.readLine();
                    if (line == null) throw new IllegalArgumentException("Empty expression matrix");
                    List<String> header = DelimitedData.parse(line, '\t', 1, matrix);
                    if (header.size() < 3 || !header.get(0).equals("sample_id"))
                        throw new IllegalArgumentException("Matrix needs sample_id and at least two feature columns");
                    Map<String, Integer> features = new LinkedHashMap<>();
                    for (int i = 1; i < header.size(); i++) {
                        String name = header.get(i);
                        if (name.isBlank() || features.putIfAbsent(name, i - 1) != null)
                            throw new IllegalArgumentException("Blank or duplicate matrix feature: " + name);
                    }
                    int n = features.size();
                    if (n > 1_000_000 || sets.size() > 100_000)
                        throw new IllegalArgumentException("Feature/signature bounds exceeded");
                    Map<String, int[]> indices = new LinkedHashMap<>();
                    List<String[]> coverage = new ArrayList<>();
                    for (var entry : sets.entrySet()) {
                        List<Integer> found = new ArrayList<>();
                        int absent = 0;
                        for (String gene : entry.getValue()) {
                            if (method.equals("ucell") && (gene.endsWith("-") || gene.endsWith("+")))
                                throw new IllegalArgumentException("Signed UCell suffixes are unsupported; supply positive signatures");
                            Integer index = features.get(gene);
                            if (index == null) {
                                absent++;
                                if (missing.equals("error")) throw new IllegalArgumentException("Signature gene absent from measured universe: " + gene);
                                if (missing.equals("impute")) found.add(-1);
                            } else found.add(index);
                        }
                        if (found.isEmpty()) throw new IllegalArgumentException("Signature has no measured genes: " + entry.getKey());
                        indices.put(entry.getKey(), found.stream().mapToInt(Integer::intValue).toArray());
                        coverage.add(new String[]{entry.getKey(), Integer.toString(entry.getValue().size()),
                            Integer.toString(entry.getValue().size() - absent), Integer.toString(absent)});
                    }
                    int maxRank = method.equals("ssgsea") ? n : integer(options, "max-rank",
                        method.equals("ucell") ? Math.min(1500, n) : Math.max(2, (int) Math.ceil(0.05 * n)), 2, n);
                    double alpha = number(options, "alpha", 0.25);
                    List<String> columns = new ArrayList<>(List.of("sample_id"));
                    columns.addAll(indices.keySet());
                    row(writer, columns.toArray(String[]::new));
                    Set<String> samples = new HashSet<>();
                    int lines = 1;
                    while ((line = reader.readLine()) != null) {
                        List<String> fields = DelimitedData.parse(line, '\t', ++lines, matrix);
                        if (fields.size() != header.size()) throw new IllegalArgumentException("Matrix row width mismatch at " + lines);
                        if (fields.get(0).isBlank() || !samples.add(fields.get(0)))
                            throw new IllegalArgumentException("Blank or duplicate sample_id");
                        double[] values = new double[n];
                        for (int i = 0; i < n; i++) values[i] = finite(fields.get(i + 1), "expression");
                        var prepared = SignatureScores.prepare(values);
                        List<String> result = new ArrayList<>(List.of(fields.get(0)));
                        for (int[] signature : indices.values()) {
                            double score = switch (method) {
                                case "ucell" -> prepared.ucell(signature, maxRank);
                                case "aucell" -> prepared.aucell(signature, maxRank);
                                default -> prepared.ssgsea(signature, alpha);
                            };
                            result.add(Double.toString(score));
                        }
                        row(writer, result.toArray(String[]::new));
                        if (samples.size() > 10_000_000) throw new IllegalArgumentException("Sample bound exceeded");
                    }
                    if (samples.isEmpty()) throw new IllegalArgumentException("No samples in expression matrix");
                    table(directory.resolve("coverage.tsv"), List.of("signature", "declared_genes", "measured_genes", "missing_genes"), coverage);
                    manifest.put("samples", samples.size()); manifest.put("features", n);
                    manifest.put("signatures", sets.size()); manifest.put("missing_policy", missing);
                    manifest.put("max_rank", maxRank); manifest.put("alpha", alpha);
                    manifest.put("ties", method.equals("aucell") ? "input feature order; differs from R randomized ties"
                        : method.equals("ucell") ? "average descending ranks" : "average ascending ranks truncated to integers; input order breaks ranking ties");
                    manifest.put("scope", method.equals("ssgsea") ? "Raw ssGSEA integrated walk; no across-dataset normalization or kernel GSVA"
                        : method.equals("ucell") ? "Positive UCell signatures; normalized Mann-Whitney U; no negative signature suffixes or smoothing"
                        : "AUCell normalized recovery area; rank cutoff is exclusive; deterministic ties");
                    manifest.put("interpretation", "Descriptive per-sample scores; biological replicates remain the unit for population inference");
                }
            });
            output.println("Signature scores written to " + options.get("out")); return 0;
        } catch (Exception failure) { error.println("jlinalg: " + failure.getMessage()); return 2; }
    }

    private static Map<String, List<String>> readSets(Path path) throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        int total = 0;
        try (var reader = Files.newBufferedReader(path)) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split("\t", -1);
                if (fields.length < 3 || fields[0].isBlank()) throw new IllegalArgumentException("GMT needs name, description, genes");
                Set<String> seen = new HashSet<>();
                List<String> genes = new ArrayList<>();
                for (int i = 2; i < fields.length; i++) {
                    if (fields[i].isBlank() || !seen.add(fields[i])) throw new IllegalArgumentException("Blank or duplicate GMT gene");
                    genes.add(fields[i]);
                    if (++total > 10_000_000) throw new IllegalArgumentException("GMT gene bound exceeded");
                }
                if (result.putIfAbsent(fields[0], genes) != null) throw new IllegalArgumentException("Duplicate signature name");
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Empty GMT");
        return result;
    }

    static String help() {
        return """
            signature-score --method ucell|aucell|ssgsea --matrix expression.tsv --gene-sets signatures.gmt --out NEW_DIR
              [--missing error|skip|impute] [--max-rank N] [--alpha 0.25]
            Matrix: sample_id, then measured feature columns (tab separated, finite numeric values).
            GMT: signature name, description, then unique genes; exact feature ID matching.
            UCell: positive signatures, average ranks, cutoff <= features, signature size <= cutoff.
            AUCell: exclusive cutoff; ties resolve in input feature order, unlike R's random ties.
            ssGSEA: raw integrated-walk score; alpha in [0,10]; no across-dataset normalization.
            Missing genes default to error; skip omits them; UCell-only impute assigns cutoff rank.
            Output: scores.tsv, coverage.tsv, manifest.yaml with hashes and algorithm scope.
            """;
    }
}
