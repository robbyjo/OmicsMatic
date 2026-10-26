/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.embedding.Tsne;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native exact t-SNE behind cell-embed, with persistent analysis diagnostics. */
final class TsneEmbeddingCli {
    private TsneEmbeddingCli() { }

    static int run(Map<String, String> o, PrintStream output, PrintStream error) throws Exception {
        Set<String> allowed = Set.of("method", "matrix", "out", "perplexity", "iterations", "learning-rate",
            "early-exaggeration", "exaggeration-iterations", "seed", "min-gradient-norm");
        for (String key : o.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("--" + key + " does not apply to native t-SNE");
        required(o, "matrix");
        final int[] exit = {0};
        transaction(o, "cell-embed-tsne", (directory, manifest) -> {
            try {
                Path input = Path.of(o.get("matrix"));
                if (Files.size(input) > 64L * 1024 * 1024) throw new IllegalArgumentException("Native t-SNE matrix exceeds 64 MiB input limit");
                DelimitedData data = DelimitedData.read(input);
                if (!data.header().get(0).equals("obs_id") || data.header().size() < 2)
                    throw new IllegalArgumentException("Matrix needs obs_id and at least one numeric component");
                int n = data.rows().size(), dimensions = data.header().size() - 1;
                if (n < 3 || n > Tsne.MAX_OBSERVATIONS || dimensions > 10000 || (long) n * n * dimensions > 200_000_000L)
                    throw new IllegalArgumentException("Exact t-SNE requires 3..2000 observations, <=10000 features and N*N*features <=200 million; use a scalable upstream implementation for larger data");
                Set<String> identities = new HashSet<>();
                double[][] values = new double[n][dimensions];
                for (int i = 0; i < n; i++) {
                    String[] row = data.rows().get(i);
                    if (row[0].isBlank() || !identities.add(row[0])) throw new IllegalArgumentException("Blank or duplicate obs_id");
                    for (int d = 0; d < dimensions; d++) values[i][d] = finite(row[d + 1], "component");
                }
                int iterations = integer(o, "iterations", 1000, 1, 10000);
                Tsne.Options settings = new Tsne.Options(number(o, "perplexity", 30), iterations,
                    number(o, "learning-rate", 50), number(o, "early-exaggeration", 12),
                    integer(o, "exaggeration-iterations", Math.min(250, iterations / 4), 0, iterations - 1),
                    Long.parseLong(o.getOrDefault("seed", "1")), number(o, "min-gradient-norm", 1e-7));
                Tsne.Result result = Tsne.fit(values, settings);
                Map<String, Object> session = new LinkedHashMap<>();
                session.put("implementation", "JLinAlg native exact 2D t-SNE");
                session.put("java_runtime", System.getProperty("java.runtime.version"));
                session.put("observations", n); session.put("input_components", dimensions);
                session.put("perplexity", settings.perplexity()); session.put("seed", settings.seed());
                session.put("learning_rate", settings.learningRate()); session.put("gradient_factor", 4);
                session.put("initialization", "seeded java.util.Random Gaussian, sd=0.0001; centered");
                session.put("early_exaggeration", settings.earlyExaggeration());
                session.put("exaggeration_iterations", settings.exaggerationIterations());
                session.put("requested_iterations", settings.iterations()); session.put("min_gradient_norm", settings.minGradientNorm());
                session.put("performed_iterations", result.iterations()); session.put("optimizer_stop_reason", result.stopReason());
                session.put("initial_kl", result.initialKl()); session.put("final_kl", result.finalKl());
                session.put("max_conditional_entropy_error", result.maxEntropyError());
                session.put("scope", "Exact squared-Euclidean affinities and Student-t kernel; supplied components; no implicit preprocessing; quadratic limits; no upstream optimizer coordinate parity claimed");
                Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session));
                List<String[]> trace = result.diagnostics().stream().map(t -> new String[]{Integer.toString(t.iteration()),
                    Double.toString(t.klDivergence()), Double.toString(t.gradientNorm()), Boolean.toString(t.exaggerated())}).toList();
                table(directory.resolve("diagnostics.tsv"), List.of("iteration", "unexaggerated_kl", "gradient_norm", "exaggerated_gradient"), trace);
                if (!(result.finalKl() < result.initialKl()))
                    throw new IllegalArgumentException("Optimization did not improve KL divergence; increase iterations or adjust learning rate/perplexity and inspect diagnostics.tsv");
                List<String[]> rows = new ArrayList<>();
                double[][] coordinates = result.coordinates();
                for (int i = 0; i < n; i++) rows.add(new String[]{data.rows().get(i)[0], Double.toString(coordinates[i][0]), Double.toString(coordinates[i][1])});
                table(directory.resolve("results.tsv"), List.of("obs_id", "TSNE1", "TSNE2"), rows);
                manifest.put("analysis_status", "completed"); manifest.put("native_session", session);
                Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"completed\",\"detail\":\"Native exact t-SNE completed; see session.yaml and diagnostics.tsv\"}\n");
            } catch (Exception failure) {
                exit[0] = 2; manifest.put("analysis_status", "failed"); manifest.put("error", failure.getMessage());
                Files.deleteIfExists(directory.resolve("results.tsv"));
                Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"failed\",\"detail\":\"Native t-SNE failed; see WARNINGS.md for the error and required action\"}\n");
                Files.writeString(directory.resolve("WARNINGS.md"), "# ANALYSIS FAILED\n\n" + failure.getMessage()
                    + "\n\nRequired action: inspect obs_id uniqueness, finite numeric components, the exact-method size limits and perplexity (1 < perplexity <= N-1; tied nearest neighbors can impose a larger minimum). For optimizer failures, inspect diagnostics.tsv when available, increase iterations or reduce learning rate. Use a scalable upstream t-SNE for larger data. Correct the cause and rerun to a new output directory. No successful results.tsv is published.\n");
            }
        });
        if (exit[0] == 0) output.println("Native t-SNE completed: " + o.get("out"));
        else error.println("ANALYSIS FAILED: see " + Path.of(o.get("out")).resolve("WARNINGS.md") + " for the error and required action.");
        return exit[0];
    }
}
