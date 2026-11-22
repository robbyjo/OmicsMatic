/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Explicit R Harmony engine; preserves its own pinned contract rather than replacing a Python attempt. */
final class HarmonyRCli {
    private HarmonyRCli() { }
    static int run(Map<String, String> o, PrintStream output, PrintStream error) throws Exception {
        Set<String> allowed = Set.of("method", "engine", "matrix", "metadata", "batch", "out", "rscript", "r-library",
            "theta", "lambda", "sigma", "clusters", "iterations", "early-stop", "seed", "timeout");
        for (String key : o.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("--" + key + " does not apply to R Harmony");
        for (String key : List.of("matrix", "metadata", "batch", "rscript", "r-library")) required(o, key);
        final int[] exit = {0}; final boolean[] warnings = {false};
        transaction(o, "cell-embed-harmony-r", (directory, manifest) -> {
            try {
                Path matrix = Path.of(o.get("matrix")).toAbsolutePath().normalize(), metadata = Path.of(o.get("metadata")).toAbsolutePath().normalize();
                if (!Files.isRegularFile(matrix) || !Files.isRegularFile(metadata)) throw new IllegalArgumentException("Matrix/metadata file is unavailable");
                manifest.put("metadata_sha256", hash(metadata)); manifest.put("engine", "R harmony 2.0.5");
                double theta = number(o, "theta", 2), lambda = number(o, "lambda", 1), sigma = number(o, "sigma", 0.1);
                if (theta < 0 || theta > 100 || lambda <= 0 || lambda > 1000 || sigma <= 0 || sigma > 10) throw new IllegalArgumentException("Require theta in [0,100], lambda in (0,1000], sigma in (0,10]");
                int iterations = integer(o, "iterations", 10, 1, 1000), seed = integer(o, "seed", 1, 0, Integer.MAX_VALUE);
                int timeout = integer(o, "timeout", 3600, 1, 86400);
                boolean early = bool(o, "early-stop", true);
                Path script = directory.resolve("harmony_r.R");
                try (var source = HarmonyRCli.class.getResourceAsStream("/org/jlinalg/omics/harmony_r.R")) {
                    if (source == null) throw new IOException("Bundled R Harmony adapter is unavailable"); Files.copy(source, script);
                }
                List<String> command = new ArrayList<>(List.of(executable(o.get("rscript")), "--vanilla", script.toAbsolutePath().toString(),
                    "--matrix", matrix.toString(), "--metadata", metadata.toString(), "--batch", o.get("batch"),
                    "--r-library", Path.of(o.get("r-library")).toAbsolutePath().normalize().toString(), "--theta", Double.toString(theta),
                    "--lambda", Double.toString(lambda), "--sigma", Double.toString(sigma), "--iterations", Integer.toString(iterations),
                    "--early-stop", Boolean.toString(early), "--seed", Integer.toString(seed)));
                if (o.containsKey("clusters")) command.addAll(List.of("--clusters", Integer.toString(integer(o, "clusters", 2, 2, 1000))));
                manifest.put("command", command); manifest.put("adapter_sha256", hash(script));
                manifest.put("effective_explicit_options", Map.of("theta", theta, "lambda", lambda, "sigma", sigma, "seed", seed,
                    "requested_iterations", iterations, "early_stop", early, "ncores", 1));
                Process process;
                try {
                    ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("external.log").toFile());
                    builder.environment().put("LC_ALL", "C"); builder.environment().put("LANGUAGE", "en");
                    process = builder.start();
                } catch (IOException failure) { exit[0] = 3; throw new IOException("Rscript cannot be started: " + failure.getMessage(), failure); }
                boolean finished;
                try { finished = process.waitFor(timeout, TimeUnit.SECONDS); }
                catch (InterruptedException failure) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); Thread.currentThread().interrupt(); throw failure; }
                if (!finished) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); throw new IOException("R Harmony timed out; inspect external.log"); }
                manifest.put("process_exit_code", process.exitValue());
                if (!Files.isRegularFile(directory.resolve("r-analysis-status.tsv"))) { exit[0] = process.exitValue() == 3 ? 3 : 2; throw new IOException("R Harmony omitted its status protocol; inspect external.log"); }
                DelimitedData status = DelimitedData.read(directory.resolve("r-analysis-status.tsv"));
                if (!status.header().equals(List.of("status", "detail", "required_action")) || status.rows().size() != 1) throw new IOException("Invalid R Harmony status protocol");
                String state = status.rows().get(0)[0]; manifest.put("analysis_detail", status.rows().get(0)[1]);
                manifest.put("required_action", status.rows().get(0)[2]);
                if (!Set.of("completed", "completed-with-qc-warnings", "failed", "skipped-dependency-unavailable").contains(state)) throw new IOException("Unrecognized R Harmony status");
                if (state.startsWith("completed") && process.exitValue() == 0) {
                    DelimitedData original = DelimitedData.read(matrix), corrected = DelimitedData.read(directory.resolve("results.tsv"));
                    if (corrected.header().size() != original.header().size() || !corrected.header().get(0).equals("obs_id")
                            || !original.rows().stream().map(r -> r[0]).toList().equals(corrected.rows().stream().map(r -> r[0]).toList())) throw new IOException("Harmony changed observation identities/order or dimensions");
                    if (new HashSet<>(corrected.rows().stream().map(r -> r[0]).toList()).size() != corrected.rows().size()) throw new IOException("Duplicated corrected observations");
                    for (String[] row : corrected.rows()) for (int d = 1; d < row.length; d++) finite(row[d], "Harmony output");
                    for (String file : List.of("r-session.txt", "installed-packages.tsv", "effective-settings.tsv", "algorithm-options.R.txt", "batch-centroids.tsv")) if (!Files.isRegularFile(directory.resolve(file))) throw new IOException("R Harmony omitted provenance: " + file);
                    manifest.put("analysis_status", state); warnings[0] = !state.equals("completed"); writeStatus(directory, state);
                } else {
                    exit[0] = state.startsWith("skipped") ? 3 : 2;
                    throw new IOException(status.rows().get(0)[1]);
                }
            } catch (Exception failure) {
                if (exit[0] == 0) exit[0] = 2;
                String state = exit[0] == 3 ? "skipped-dependency-unavailable" : "failed";
                manifest.put("analysis_status", state); manifest.put("error", failure.getMessage());
                if (Files.exists(directory.resolve("results.tsv"))) Files.move(directory.resolve("results.tsv"), directory.resolve("java-validation-partial-results.tsv"));
                writeStatus(directory, state);
                Files.writeString(directory.resolve("WARNINGS.md"), "# " + (exit[0] == 3 ? "ANALYSIS SKIPPED - R HARMONY UNAVAILABLE" : "ANALYSIS FAILED")
                    + "\n\n" + failure.getMessage() + "\n\nRequired action: in an approved environment run Rscript --vanilla tools/literature/install_harmony_dependencies.R build/omics-harmony-r build/literature-review/harmony-r-dependency allow, then supply the resulting --rscript and --r-library. Inspect external.log, unique observation identities, finite nonzero PCA rows, the matching metadata universe and at least two batches. Correct the cause and rerun to a new output directory. Python harmonypy remains a separate engine. No successful results.tsv is published.\n");
            }
        });
        if (exit[0] != 0) error.println((exit[0] == 3 ? "SKIPPED ANALYSIS" : "ANALYSIS FAILED") + ": see " + Path.of(o.get("out")).resolve("WARNINGS.md") + " for the cause and required action.");
        else { output.println("R Harmony completed: " + o.get("out")); if (warnings[0]) error.println("ANALYSIS WARNINGS: inspect " + Path.of(o.get("out")).resolve("WARNINGS.md")); }
        return exit[0];
    }
    private static void writeStatus(Path directory, String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + state + "\",\"detail\":\"See R status table, external.log and WARNINGS.md when present\"}\n"); }
}
