/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Captured-input native PoPS 0.2 analysis and genuine complete-result recomputation. */
public final class PopsWorkflowCli {
    static final String NOTICE = """
        # Warnings and required actions

        DEVELOPMENT: native PoPS 0.2 prepared inputs, ridge or minimum-norm OLS,
        portable Java CPU. This is not PoPS 0.1 or LassoCV. No paper reproduction
        or catalog integration credit follows from a completed software control.
        Build current source; the released v0.4.0 download lacks this workflow.

        Gene IDs are literal. Genome build, feature-set and target-source labels
        are caller declarations, not validated biological provenance. No ID repair,
        imputation, covariance repair, feature thinning or score scaling is done.
        MAGMA covariance alone receives the source's uniform diagonal adjustment.
        HLA removal follows chromosome string 6 and inclusive TSS 20-40 Mb.
        The source ignores covariance between chromosomes; inspect warnings.tsv.
        NaN marginal diagnostics are retained. An accepted prediction/coefficient
        must be finite. A gene without a target retains NA target fields.

        java_heap_mb bounds the worker JVM heap, not the parent or total process
        memory. wall_ms and work bytes/entries are monitored, not OS disk quotas.
        Feature batching does not bound covariance or selected-training storage.
        Cancel normally to stop owned children. Hard-killing a Windows parent
        has no process-job guarantee; stop its recorded worker if necessary.

        Missing or forbidden prerequisites: SKIP3, dependent analysis skipped.
        Supply the named permitted Java 17+ prerequisite or skip this analysis.
        No runtime is installed during analysis. Other failures: FAIL2, no accepted
        result. Retain inputs, consoles and FAILURE.txt; resolve the named cause
        and retry NEW output. Never drop genes/features to satisfy capacity.

        Only the root COMPLETED.properties admits this bundle. WORKER_DONE alone
        does not. --verify hashes the full source bundle, then actually refits from
        captured inputs into NEW output and compares all scientific result bytes.
        Use the matching original artifact/profile; do not refresh saved hashes
        to force a pass. Hashes establish integrity, not authenticity or signature.
        """;
    private PopsWorkflowCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream err) {
        Path owned = null;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                out.println("PoPS native prepared workflow: --request FILE --out NEW_DIRECTORY\nGenuine replay: --verify BUNDLE --out NEW_DIRECTORY\nRequired properties: " + new java.util.TreeSet<>(REQUIRED)
                    + "\nCustom targets: scores=TSV with ENSGID and Score, optional score_covariates and error_covariance.\nMAGMA targets: magma_prefix for .genes.out and .genes.raw.\nFeatures: feature_prefix for .rows.txt, .cols.i.txt and .mat.i.npy, feature_chunks.\nSee POPS_WORKFLOWS.md for the complete request and exact limits.\n" + NOTICE);
                out.flush(); return out.checkError() ? 2 : 0;
            }
            Map<String, String> options = new TreeMap<>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 == args.length || !args[i].startsWith("--") || options.putIfAbsent(args[i].substring(2), args[i + 1]) != null) throw new IllegalArgumentException("Use unique option/value pairs; see --help");
            boolean replay = options.containsKey("verify");
            if (!options.keySet().equals(replay ? Set.of("verify", "out") : Set.of("request", "out"))) throw new IllegalArgumentException("Use --request FILE --out NEW or --verify BUNDLE --out NEW");
            Path old = replay ? Path.of(options.get("verify")).toRealPath() : null;
            Path destination = Path.of(options.get("out")).toAbsolutePath().normalize();
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Output exists; preserve it and choose a NEW directory");
            if (destination.getParent() == null) throw new IOException("Output parent required");
            overlap(destination, old); Files.createDirectories(destination.getParent());
            destination = destination.getParent().toRealPath().resolve(destination.getFileName()); overlap(destination, old);
            Files.createDirectory(destination); owned = destination.toRealPath();
            write(owned.resolve("WARNINGS.md"), NOTICE); Files.createDirectory(owned.resolve("inputs"));
            err.println("WARNING: native PoPS 0.2 development workflow. Read " + owned.resolve("WARNINGS.md") + ". Labels are caller declarations; no paper reproduction is established.");
            err.flush(); if (err.checkError()) throw new IOException("Warning recording failed");
            Map<String, String> code = PopsWorkflowCode.current(); Config c;
            if (replay) {
                c = checkBundle(old); PopsWorkflowCode.check(old);
                for (String name : manifest(old.resolve("INPUTS.tsv")).keySet()) {
                    Path source = within(old.resolve("inputs"), name), target = within(owned.resolve("inputs"), name);
                    if (!source.getParent().equals(old.resolve("inputs"))) throw new IOException("Prepared input manifest requires flat captured files");
                    copy(source, target, c.number("max_input_bytes"));
                }
                copy(old.resolve("request.properties"), owned.resolve("request.properties"), 1048576);
                write(owned.resolve("CAPTURE.tsv"), "source_bundle\tmanifest_sha256\n" + old + "\t" + sha(old.resolve("FILES.tsv")) + "\n");
            } else {
                Path request = Path.of(options.get("request")).toRealPath(); copy(request, owned.resolve("request.properties"), 1048576);
                Config original = config(load(owned.resolve("request.properties"))); c = PopsWorkflowInputs.capture(request.getParent(), original, owned);
            }
            properties(owned.resolve("effective.properties"), c.values()); write(owned.resolve("code.tsv"), PopsWorkflowCode.render(code));
            Map<String, String> inputs = inventory(owned.resolve("inputs"), c); checkInputCapacity(inputs, c); manifest(owned.resolve("INPUTS.tsv"), inputs);
            PopsWorkflowProcess.capacity(owned, c); PopsWorkflowProcess.execute(owned, c);
            Map<String, String> model = PopsWorkflowResults.audit(owned, c);
            inputsUnchanged(owned, c); if (!code.equals(PopsWorkflowCode.current())) throw new IOException("Implementation changed during computation");
            if (replay && !inventory(old.resolve("results"), c).equals(inventory(owned.resolve("results"), c))) throw new IOException("Genuine refit produced different complete scientific results. Retain both bundles; do not repair mismatches or refresh fingerprints.");
            Map<String, String> diagnostics = PopsWorkflowResults.strings(load(owned.resolve("results/diagnostics.properties")));
            StringBuilder warnings = new StringBuilder();
            for (var entry : PopsWorkflowResults.warnings(diagnostics).entrySet()) {
                String message = "WARNING " + entry.getKey() + "=" + diagnostics.get(entry.getKey()) + ": " + entry.getValue();
                warnings.append(message).append('\n'); err.println(message);
            }
            if (!warnings.isEmpty()) Files.writeString(owned.resolve("WARNINGS.md"), "\n# Analysis-specific warnings\n\n" + warnings, java.nio.file.StandardOpenOption.APPEND);
            write(owned.resolve("OUTCOME.txt"), "Accepted native PoPS 0.2 model; " + model.get("selected_features") + " selected features, " + model.get("prediction_genes") + " prediction genes.\nRead results/warnings.tsv and WARNINGS.md. All files remain provisional until the root COMPLETED.properties exists.\n" + warnings);
            err.flush(); if (err.checkError()) throw new IOException("Analysis warning recording failed");
            PopsWorkflowProcess.capacity(owned, c); manifest(owned.resolve("FILES.tsv"), inventory(owned, c));
            properties(owned.resolve("COMPLETED.properties"), Map.of("profile", PROFILE, "kind", replay ? "genuine-recomputation" : "analysis", "manifest_sha256", sha(owned.resolve("FILES.tsv")), "parent_manifest_sha256", replay ? sha(old.resolve("FILES.tsv")) : "none", "prediction_genes", model.get("prediction_genes"), "selected_features", model.get("selected_features")));
            PopsWorkflowProcess.capacity(owned, c);
            out.println("PASS " + PROFILE + " predictions=" + model.get("prediction_genes") + " selected_features=" + model.get("selected_features") + " " + owned);
            out.flush(); if (out.checkError()) throw new IOException("Success output recording failed; completion invalidated");
            return 0;
        } catch (Throwable error) {
            int exit = error instanceof Prerequisite || error instanceof LinkageError ? 3 : 2;
            if (owned != null) try {
                Path marker = owned.resolve("COMPLETED.properties"); if (Files.exists(marker)) Files.move(marker, owned.resolve("INVALIDATED-" + UUID.randomUUID() + "-COMPLETED.properties"));
                write(owned.resolve("FAILURE.txt"), error + "\n" + NOTICE);
                Files.writeString(owned.resolve("WARNINGS.md"), "\n# " + (exit == 3 ? "SKIP3: dependent analysis skipped" : "FAIL2: no accepted analysis") + "\n\n" + error + "\nRetain this bundle; resolve the named cause and retry NEW output.\n", java.nio.file.StandardOpenOption.APPEND);
            } catch (Throwable recording) { err.println("FAIL2_RECORDING_UNAVAILABLE " + recording); exit = 2; }
            err.println((exit == 3 ? "SKIP3_POPS_ANALYSIS_SKIPPED " : "FAIL2_POPS_UNADMITTED ") + error + "\n" + NOTICE); err.flush(); return exit;
        }
    }
    private static void overlap(Path output, Path old) throws IOException { if (old != null && (output.startsWith(old) || old.startsWith(output))) throw new IOException("Replay output must be outside the original bundle"); }
    private static Path within(Path root, String name) throws IOException {
        Path member = Path.of(name); Path result = root.resolve(member).normalize();
        if (member.isAbsolute() || !result.startsWith(root) || name.contains("\\") || name.contains("\t") || name.contains("\n")) throw new IOException("Unsafe captured input manifest member"); return result;
    }
    private static void checkInputCapacity(Map<String, String> entries, Config c) throws IOException {
        long total = 0; for (var entry : entries.entrySet()) {
            if (entry.getKey().endsWith("/")) throw new IOException("Prepared input capture must contain flat files");
            total = Math.addExact(total, Long.parseLong(entry.getValue().split("\t", -1)[0]));
            if (total > c.number("max_input_bytes")) throw new IOException("Complete captured inputs exceed max_input_bytes");
        }
    }
    static void inputsUnchanged(Path root, Config c) throws IOException {
        Map<String, String> current = inventory(root.resolve("inputs"), c); checkInputCapacity(current, c);
        if (!manifest(root.resolve("INPUTS.tsv")).equals(current)) throw new IOException("Captured inputs changed; no result admitted");
    }
    static Config checkBundle(Path root) throws IOException {
        Config c = config(load(root.resolve("effective.properties"))); var marker = PopsWorkflowResults.strings(load(root.resolve("COMPLETED.properties")));
        if (!marker.keySet().equals(Set.of("profile", "kind", "manifest_sha256", "parent_manifest_sha256", "prediction_genes", "selected_features")) || !PROFILE.equals(marker.get("profile")) || !Set.of("analysis", "genuine-recomputation").contains(marker.get("kind")) || !sha(root.resolve("FILES.tsv")).equals(marker.get("manifest_sha256"))) throw new IOException("Complete matching profile and root manifest required");
        Map<String, String> actual = inventory(root, c); actual.remove("FILES.tsv"); actual.remove("COMPLETED.properties");
        if (!manifest(root.resolve("FILES.tsv")).equals(actual)) throw new IOException("Source bundle bytes or complete membership differ; no replay admitted");
        inputsUnchanged(root, c); return c;
    }
}
