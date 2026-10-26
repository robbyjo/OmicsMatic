/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native/upstream cell representations, retaining skipped/failed attempts and actions. */
final class CellEmbeddingCli {
    private CellEmbeddingCli() { }
    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (Arrays.asList(arguments).contains("--help")) { output.println(help()); return 0; }
        try {
            Map<String, String> o = options(arguments, Set.of("method", "matrix", "nodes", "graph", "python", "out", "metadata", "batch", "neighbors", "components", "resolution", "min-dist", "seed", "timeout", "perplexity", "iterations", "learning-rate", "early-exaggeration", "exaggeration-iterations", "min-gradient-norm", "engine", "rscript", "r-library", "theta", "lambda", "sigma", "clusters", "early-stop"));
            String method = required(o, "method");
            boolean suppliedGraph = o.containsKey("nodes") || o.containsKey("graph");
            if (suppliedGraph && (!Set.of("leiden", "louvain").contains(method) || o.containsKey("matrix") || o.containsKey("neighbors")))
                throw new IllegalArgumentException("--nodes and --graph require Leiden/Louvain and cannot be combined with --matrix or --neighbors");
            if (o.containsKey("engine") && !method.equals("harmony")) throw new IllegalArgumentException("--engine applies only to Harmony");
            if (method.equals("harmony") && o.getOrDefault("engine", "python").equals("r")) return HarmonyRCli.run(o, output, error);
            if (o.containsKey("engine") && !o.get("engine").equals("python")) throw new IllegalArgumentException("Harmony engine must be python or r");
            if (o.keySet().stream().anyMatch(Set.of("rscript", "r-library", "theta", "lambda", "sigma", "clusters", "early-stop")::contains)) throw new IllegalArgumentException("R Harmony options require --method harmony --engine r");
            if (method.equals("tsne")) return TsneEmbeddingCli.run(o, output, error);
            if (!Set.of("umap", "leiden", "louvain", "harmony").contains(method)) throw new IllegalArgumentException("Unknown cell embedding method");
            if (o.keySet().stream().anyMatch(Set.of("perplexity", "iterations", "learning-rate", "early-exaggeration", "exaggeration-iterations", "min-gradient-norm")::contains))
                throw new IllegalArgumentException("t-SNE optimization options apply only to --method tsne");
            required(o, "python");
            Path input = Path.of(required(o, suppliedGraph ? "nodes" : "matrix")).toAbsolutePath().normalize();
            if (!Files.isRegularFile(input)) throw new IOException("Input does not exist: " + input);
            Path graph = suppliedGraph ? Path.of(required(o, "graph")).toAbsolutePath().normalize() : null;
            if (graph != null && !Files.isRegularFile(graph)) throw new IOException("Graph does not exist: " + graph);
            int timeout = integer(o, "timeout", 3600, 1, 86400);
            if (method.equals("harmony")) {
                required(o, "metadata"); required(o, "batch");
            } else if (o.containsKey("metadata") || o.containsKey("batch")) throw new IllegalArgumentException("metadata/batch apply only to Harmony");
            if (!method.equals("umap") && (o.containsKey("components") || o.containsKey("min-dist"))
                    || !Set.of("leiden", "louvain").contains(method) && o.containsKey("resolution"))
                throw new IllegalArgumentException("Option does not apply to the requested method");
            final int[] exit = {0};
            transaction(o, "cell-embed-" + method, (directory, manifest) -> {
                Path script = directory.resolve("cell_embedding.py");
                try (InputStream source = CellEmbeddingCli.class.getResourceAsStream("/org/jlinalg/omics/cell_embedding.py")) {
                    if (source == null) throw new IOException("Missing bundled cell adapter");
                    Files.copy(source, script);
                }
                List<String> command = new ArrayList<>(List.of(executable(o.get("python")), "-X", "utf8", "-I", script.toAbsolutePath().toString(),
                    "--method", method, suppliedGraph ? "--nodes" : "--matrix", input.toString()));
                if (graph != null) { command.add("--graph"); command.add(graph.toString()); }
                for (String key : List.of("metadata", "batch", "neighbors", "components", "resolution", "min-dist", "seed")) if (o.containsKey(key)) {
                    command.add("--" + key);
                    command.add(key.equals("metadata") ? Path.of(o.get(key)).toAbsolutePath().toString() : o.get(key));
                }
                manifest.put("command", command); manifest.put("adapter_sha256", hash(script));
                if (suppliedGraph) { manifest.put("nodes_sha256", hash(input)); manifest.put("graph_sha256", hash(graph)); }
                if (o.containsKey("metadata")) manifest.put("metadata_sha256", hash(Path.of(o.get("metadata"))));
                Path python = Path.of(o.get("python"));
                if (Files.isRegularFile(python)) manifest.put("python_executable_sha256", hash(python));
                String processFailure = null;
                try { process(command, directory, timeout); }
                catch (IOException failure) { processFailure = failure.getMessage(); }
                Path statusFile = directory.resolve("analysis-status.json");
                if (!Files.isRegularFile(statusFile)) {
                    manifest.put("analysis_status", "failed"); manifest.put("error", processFailure == null ? "Adapter omitted status protocol" : processFailure);
                    Files.writeString(directory.resolve("WARNINGS.md"), "# ANALYSIS FAILED\n\n" + manifest.get("error")
                        + "\n\nRequired action: supply a working Python and the pinned optional packages; inspect external.log and rerun to a new output directory. No successful analysis is published.\n");
                    exit[0] = 2; return;
                }
                Object parsed = SimpleJson.parse(Files.readString(statusFile));
                if (!(parsed instanceof Map<?, ?> status)) throw new IOException("Invalid adapter status protocol");
                String state = String.valueOf(status.get("status"));
                manifest.put("analysis_status", state); manifest.put("analysis_detail", status.get("detail"));
                manifest.put("required_action", status.get("required_action"));
                if (state.equals("completed") && processFailure == null) {
                    try {
                        DelimitedData matrix = DelimitedData.read(input), results = DelimitedData.read(directory.resolve("results.tsv"));
                        if (!results.header().get(0).equals("obs_id") || !results.rows().stream().map(r -> r[0]).toList().equals(matrix.rows().stream().map(r -> r[0]).toList()))
                            throw new IOException("Adapter changed observation identities/order");
                        for (String[] row : results.rows()) for (int i = 1; i < row.length; i++) finite(row[i], "adapter output");
                        if (!Files.isRegularFile(directory.resolve("session.json"))) throw new IOException("Adapter omitted dependency versions");
                    } catch (IOException | RuntimeException failure) {
                        exit[0] = 2; manifest.put("analysis_status", "failed-output-validation");
                        manifest.put("error", failure.getMessage());
                        Files.deleteIfExists(directory.resolve("results.tsv"));
                        Files.writeString(directory.resolve("WARNINGS.md"), "# ANALYSIS FAILED\n\nUpstream output validation failed: " + failure.getMessage()
                            + "\n\nRequired action: inspect external.log and the pinned upstream package versions, correct the adapter/input mismatch and rerun to a new output directory. No successful analysis result is published.\n");
                    }
                } else {
                    exit[0] = state.startsWith("skipped") ? 3 : 2;
                    Files.deleteIfExists(directory.resolve("results.tsv"));
                    if (processFailure != null) manifest.put("process_error", processFailure);
                    if (!Files.exists(directory.resolve("WARNINGS.md"))) Files.writeString(directory.resolve("WARNINGS.md"),
                        "# " + state.toUpperCase(java.util.Locale.ROOT) + "\n\n" + status.get("detail") + "\n\nRequired action: " + status.get("required_action") + "\n");
                }
            });
            if (exit[0] == 0) output.println("Cell representation completed: " + o.get("out"));
            else error.println((exit[0] == 3 ? "SKIPPED ANALYSIS" : "ANALYSIS FAILED") + ": see " + Path.of(o.get("out")).resolve("WARNINGS.md") + " for the error and required action.");
            return exit[0];
        } catch (Exception failure) { error.println("jlinalg: " + failure.getMessage()); return 2; }
    }
    static String help() { return """
        cell-embed --method tsne|umap|leiden|louvain|harmony --matrix pca.tsv --out NEW_DIR
        Matrix: obs_id, then supplied PCA component columns. Exact IDs and finite values required.
        t-SNE: native Java, no --python. [--perplexity 30 --iterations 1000 --seed 1
          --learning-rate 50 --early-exaggeration 12 --exaggeration-iterations 250 --min-gradient-norm 1e-7]
          Exact 2D optimization: 3..2000 observations, N*N*features <=200 million.
          Perplexity must be >1 and <=N-1, and attainable given tied nearest distances.
          Default early updates: min(250, iterations/4). diagnostics.tsv and session.yaml record KL and stopping.
        Upstream methods below require --python PYTHON:
        UMAP: [--neighbors 15 --components 2 --min-dist 0.1 --seed 1]
        Leiden/Louvain: [--neighbors 15 --resolution 1 --seed 1]; weighted undirected union kNN graph.
          Or replace --matrix with --nodes nodes.tsv --graph edges.tsv (no --neighbors).
          Nodes: one obs_id column, all nodes including isolates. Edges: source,target,weight TSV;
          positive finite weights, undirected, no self-loops or duplicate/reversed edges.
          At least one edge; <=100000 nodes, <=1000000 edges, <=128 MiB per input table.
          Supplied weights are used directly; graph.tsv records canonical node-order edges.
        Harmony: --metadata cells.tsv --batch COLUMN; >=2 batches, same obs_id universe.
          Default --engine python uses harmonypy 2.1.0. Explicit --engine r uses R harmony 2.0.5:
          --rscript RSCRIPT --r-library PRIVATE_R_LIBRARY (no --python)
          [--theta 2 --lambda 1 --sigma 0.1 --clusters AUTO --iterations 10 --early-stop true --seed 1]
          Default R clusters=min(100,max(2,round(N/30))); ncores=1; fixed positive lambda.
          R limits: 4..100000 observations, <=256 components, N*components <=5 million,
          N*clusters <=10 million, <=128 MiB per table. R settings/session and batch centroids retained.
        Upstream: [--timeout 3600]. Pinned versions and full scope are recorded in session.json.
        Install only in an allowed environment: python tools/literature/install_cell_dependencies.py --allow-install
        Then supply build/omics-cell-tools-env/Scripts/python.exe (Windows) or bin/python (POSIX).
        Missing/incompatible dependencies skip the analysis, preserve WARNINGS.md and return status 3.
        Other failures preserve diagnostics and return 2. A skipped attempt has no successful results.tsv.
        Exact graph construction is bounded to 200 million distance work units; UMAP <=100000 observations.
        These are exploratory representations, not population-level inference or automatic cell annotation.
        """; }
}
