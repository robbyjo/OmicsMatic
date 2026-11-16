/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Original PoPS 0.1/0.2 programs with immutable captures and genuine recomputation. */
public final class PopsOriginalCli {
    static final String NOTICE = """
        # Original PoPS warnings and required actions

        DEVELOPMENT: selected original GPL PoPS source, not an author binary or
        paper environment. Edition is mandatory. PoPS 0.1 uses the original
        lower Cholesky of inverse covariance, marginal statsmodels OLS and
        chromosome-held-out RidgeCV with Pearson scoring. PoPS 0.2 has its own
        covariance transformation, feature selection and ridge/OLS/LassoCV.
        No edition or method is silently substituted. Native ridge/OLS is a
        separate explicit pops-tools profile, never an automatic fallback.

        Original execution requires a separately installed and adopted Linux
        x86-64 Python runtime. Run Java inside Linux/WSL. No installation or
        download occurs during analysis. Missing/forbidden prerequisites:
        SKIP3, dependent analysis skipped. Supply the named permitted setup or
        leave it skipped. Other failures: FAIL2, no accepted complete result.

        Source warnings and failures are retained. Explicit ConvergenceWarning
        means FAIL2 even when source coefficients are finite. No iteration limit,
        random seed, identifier, NaN, infinity or covariance is silently repaired.
        A missing-value policy is required explicitly for original feature munging.
        Preparation/selection completion does not establish a fitted model.
        Empty 0.1 selection can still yield a control-only model. The selected
        original 0.1 source fails with a single selected feature; that failure
        is preserved. Its selected CV alpha/score are not exported by the source.

        Genome build, target-source and feature-set labels are caller declarations.
        They are not verified biological provenance. Whole-paper reproduction,
        biological validation and new catalog credit are not established here.
        Build current source; released v0.4.0 lacks this command.

        java_heap_mb bounds the owned worker heap. python_address_space_mb is a
        separate per-Python-process Linux address-space limit. wall_ms monitors
        the worker, including runtime checking and source execution. Work bytes
        and entries are monitored, not OS disk quotas. This is not a filesystem
        or network sandbox or an executable-authenticity claim. Increase permitted
        capacities without thinning genes/features or splitting dependencies.

        Only root COMPLETED.properties admits the complete requested task.
        WORKER_DONE alone does not. --verify checks complete bundle membership
        and bytes, then actually reruns the original source and compares the full
        scientific result and binding families in NEW output. Use the original
        matching artifact/profile and adopted runtime. Do not refresh fingerprints
        to force a pass. Hashes provide integrity, not authenticity/signature.
        Retain all raw outputs, runtime-logs, worker consoles and FAILURE.txt.
        Correct the named cause and retry NEW output; unresolved source numerical
        failures must remain unadmitted. Cancel normally so owned children stop.
        Hard-killing a Windows parent has no process-job guarantee.
        """;
    private PopsOriginalCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream err) {
        Path owned = null;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                out.println("Original PoPS: --request FILE --out NEW_DIRECTORY\nGenuine replay: --verify BUNDLE --out NEW_DIRECTORY\nprofile=pops-original-v1; edition=0.1|0.2.\n0.1 tasks: select, predict, select-predict. 0.2 tasks: fit, munge.\nRequired common fields: " + new TreeSet<>(PopsOriginalFiles.REQUIRED)
                    + "\n0.1: features_text,magma_prefix; prediction also gene_location,control_features,chromosomes; standalone predict also selected_features.\n0.2 fit: gene_annotation,feature_prefix,feature_chunks,target_type; magma_prefix or scores; optional custom covariates/covariance, selection/mask settings, method=ridge|linreg|lasso,random_seed=42,save_matrix_files=false.\n0.2 munge: gene_annotation,feature_directory,nan_policy=raise|ignore|mean|zero; chunk_columns=5000.\nSee POPS_ORIGINAL_WORKFLOWS.md.\n" + NOTICE); out.flush(); return out.checkError() ? 2 : 0;
            }
            Map<String, String> options = new TreeMap<>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 == args.length || !args[i].startsWith("--") || options.putIfAbsent(args[i].substring(2), args[i + 1]) != null) throw new IllegalArgumentException("Use unique option/value pairs; see --help");
            boolean replay = options.containsKey("verify"); if (!options.keySet().equals(replay ? Set.of("verify", "out") : Set.of("request", "out"))) throw new IllegalArgumentException("Use --request FILE --out NEW or --verify BUNDLE --out NEW");
            for (String value : options.values()) if (value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Control characters in workflow paths are not supported");
            Path destination = realFuture(Path.of(options.get("out")).toAbsolutePath().normalize());
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Output exists; preserve it and choose a NEW directory");
            Path old = replay ? Path.of(options.get("verify")).toRealPath() : null; overlap(destination, old, "original bundle");
            PopsOriginalFiles.Config c; byte[] request; Path requestDirectory;
            if (replay) { c = checkBundle(old); PopsOriginalCode.check(old); request = Files.readAllBytes(old.resolve("request.properties")); requestDirectory = old; }
            else {
                Path file = Path.of(options.get("request")).toRealPath(); if (Files.size(file) > 1048576) throw new IOException("Request exceeds 1 MiB"); request = Files.readAllBytes(file); c = PopsOriginalFiles.request(request); requestDirectory = file.getParent();
                Map<String, String> values = new TreeMap<>(c.values()); values.put("runtime", resolve(requestDirectory, c.get("runtime")).toString()); c = PopsOriginalFiles.config(asProperties(values));
            }
            // Check protected read-only software roots before creating any output directories.
            var runtime = PopsOriginalRuntime.check(c, false);
            for (Path root : List.of(runtime.root(), runtime.environment(), runtime.sources())) overlap(destination, root, "adopted runtime/source tree");
            if (!replay && c.get("task").equals("munge")) overlap(destination, resolve(requestDirectory, c.get("feature_directory")).toRealPath(), "source feature directory");
            if (destination.getParent() == null) throw new IOException("Output parent required"); Files.createDirectories(destination.getParent()); Files.createDirectory(destination); owned = destination.toRealPath();
            write(owned.resolve("WARNINGS.md"), NOTICE); Files.createDirectory(owned.resolve("inputs"));
            err.println("WARNING: selected original PoPS " + c.get("edition") + " task=" + c.get("task") + ". Read " + owned.resolve("WARNINGS.md") + ". No author environment or biological validation is established."); err.flush(); if (err.checkError()) throw new IOException("Warning recording failed");
            Files.write(owned.resolve("request.properties"), request, StandardOpenOption.CREATE_NEW);
            if (replay) {
                for (var entry : manifest(old.resolve("INPUTS.tsv")).entrySet()) {
                    Path source = member(old.resolve("inputs"), entry.getKey()), target = member(owned.resolve("inputs"), entry.getKey());
                    if (entry.getKey().endsWith("/")) Files.createDirectory(target); else copy(source, target, c.n("max_input_bytes"));
                }
                write(owned.resolve("CAPTURE.tsv"), "source_bundle\tmanifest_sha256\n" + old + "\t" + sha(old.resolve("FILES.tsv")) + "\n");
            } else c = PopsOriginalInputs.capture(requestDirectory, c, owned);
            properties(owned.resolve("effective.properties"), c.values()); Map<String, String> code = PopsOriginalCode.current(); write(owned.resolve("code.tsv"), PopsWorkflowCode.render(code));
            Map<String, String> identity = PopsOriginalRuntime.identity(runtime);
            if (replay && !Files.readString(old.resolve("runtime-identity.tsv")).equals(PopsWorkflowCode.render(identity))) throw new IOException("Adopted original runtime identity differs; use the original matching runtime, never refreshed fingerprints");
            write(owned.resolve("runtime-identity.tsv"), PopsWorkflowCode.render(identity)); PopsOriginalRuntime.capture(owned, runtime); PopsOriginalInputs.seal(owned, c); PopsOriginalInputs.check(owned, c);
            PopsOriginalFiles.capacity(owned, c); PopsOriginalProcess.execute(owned, c); Map<String, String> summary = PopsOriginalResults.audit(owned, c);
            checkWorkerReceipt(owned, c);
            PopsOriginalInputs.check(owned, c); PopsOriginalRuntime.sourcesUnchanged(owned); PopsOriginalCode.check(owned);
            if (!identity.equals(PopsOriginalRuntime.identity(runtime))) throw new IOException("Runtime metadata/helper identity changed before admission");
            if (replay && !science(old, c).equals(science(owned, c))) throw new IOException("Genuine original-source refit changed complete scientific results/bindings. Retain both bundles; do not repair discrepancies or refresh fingerprints.");
            Map<?, ?> report = PopsOriginalResults.report(owned, c); String extra = warnings(owned, c, report, err);
            write(owned.resolve("OUTCOME.txt"), "Completed requested original PoPS " + c.get("edition") + " task=" + c.get("task") + ", fitted_models=" + summary.get("models") + ", prediction_genes=" + summary.get("prediction_genes") + ".\nAll outputs remain provisional until root COMPLETED.properties exists. Preparation/selection completion is not a fitted model.\n" + extra);
            err.flush(); if (err.checkError()) throw new IOException("Source warning recording failed");
            PopsOriginalFiles.capacity(owned, c); manifest(owned.resolve("FILES.tsv"), PopsOriginalFiles.inventory(owned, c));
            Map<String, String> marker = new TreeMap<>(summary); marker.put("kind", replay ? "genuine-original-recomputation" : "original-task"); marker.put("manifest_sha256", sha(owned.resolve("FILES.tsv"))); marker.put("runtime_identity_sha256", sha(owned.resolve("runtime-identity.tsv"))); marker.put("parent_manifest_sha256", replay ? sha(old.resolve("FILES.tsv")) : "none");
            properties(owned.resolve("COMPLETED.properties"), marker); PopsOriginalFiles.capacity(owned, c);
            out.println("PASS " + PopsOriginalFiles.PROFILE + " edition=" + c.get("edition") + " task=" + c.get("task") + " models=" + summary.get("models") + " " + owned); out.flush(); if (out.checkError()) throw new IOException("Success recording failed; completion invalidated"); return 0;
        } catch (Throwable error) {
            int exit = error instanceof Prerequisite || error instanceof LinkageError ? 3 : 2;
            if (owned != null) try {
                Path marker = owned.resolve("COMPLETED.properties"); if (Files.exists(marker)) Files.move(marker, owned.resolve("INVALIDATED-" + UUID.randomUUID() + "-COMPLETED.properties"));
                write(owned.resolve("FAILURE.txt"), error + "\n" + NOTICE); Files.writeString(owned.resolve("WARNINGS.md"), "\n# " + (exit == 3 ? "SKIP3: dependent analysis skipped" : "FAIL2: no accepted complete result") + "\n\n" + error + "\nRetain raw source outputs and correct the named cause; retry NEW output.\n", StandardOpenOption.APPEND);
            } catch (Throwable recording) { err.println("FAIL2_RECORDING_UNAVAILABLE " + recording); exit = 2; }
            err.println((exit == 3 ? "SKIP3_ORIGINAL_POPS_ANALYSIS_SKIPPED " : "FAIL2_ORIGINAL_POPS_UNADMITTED ") + error + "\n" + NOTICE); err.flush(); return exit;
        }
    }
    private static Path realFuture(Path path) throws IOException { if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return path.toRealPath(); Path parent = path.getParent(); if (parent == null) throw new IOException("Resolvable output parent required"); return realFuture(parent).resolve(path.getFileName()); }
    private static void overlap(Path output, Path protectedPath, String role) throws IOException { if (protectedPath != null && (output.startsWith(protectedPath) || protectedPath.startsWith(output))) throw new IOException("Output must be outside the protected " + role); }
    private static Path member(Path base, String name) throws IOException { Path relative = Path.of(name), result = base.resolve(relative).normalize(); if (relative.isAbsolute() || result.equals(base) || !result.startsWith(base) || name.contains("\\")) throw new IOException("Unqualified captured input member"); return result; }
    private static Map<String, String> science(Path root, PopsOriginalFiles.Config c) throws IOException { Map<String, String> result = new TreeMap<>(); for (String family : List.of("results", "audit")) for (var e : PopsOriginalFiles.inventory(root.resolve(family), c).entrySet()) result.put(family + "/" + e.getKey(), e.getValue()); return result; }
    private static PopsOriginalFiles.Config checkBundle(Path root) throws IOException {
        var c = PopsOriginalFiles.loadConfig(root.resolve("effective.properties")); var marker = load(root.resolve("COMPLETED.properties"));
        if (!PopsOriginalFiles.PROFILE.equals(marker.getProperty("profile")) || !sha(root.resolve("FILES.tsv")).equals(marker.getProperty("manifest_sha256")) || !sha(root.resolve("runtime-identity.tsv")).equals(marker.getProperty("runtime_identity_sha256"))) throw new IOException("Complete matching original bundle and manifest required");
        Map<String, String> actual = PopsOriginalFiles.inventory(root, c); actual.remove("FILES.tsv"); actual.remove("COMPLETED.properties"); if (!actual.equals(manifest(root.resolve("FILES.tsv")))) throw new IOException("Complete original bundle bytes/membership differ; no refit admitted");
        PopsOriginalInputs.check(root, c); Map<String, String> summary = PopsOriginalResults.audit(root, c);
        Set<String> keys = new HashSet<>(summary.keySet()); keys.addAll(Set.of("kind", "manifest_sha256", "runtime_identity_sha256", "parent_manifest_sha256"));
        if (!marker.stringPropertyNames().equals(keys)) throw new IOException("Original completion declaration fields differ from the complete task contract");
        for (var e : summary.entrySet()) if (!e.getValue().equals(marker.getProperty(e.getKey()))) throw new IOException("Original completion declaration differs from audited results: " + e.getKey());
        String kind = marker.getProperty("kind"), parent = marker.getProperty("parent_manifest_sha256");
        if (kind.equals("original-task")) { if (!parent.equals("none")) throw new IOException("An original task cannot declare a replay parent"); }
        else if (kind.equals("genuine-original-recomputation")) {
            if (!parent.matches("[0-9a-f]{64}")) throw new IOException("Original replay requires a complete parent manifest identity");
            List<String> capture = Files.readAllLines(root.resolve("CAPTURE.tsv"));
            if (capture.size() != 2 || !capture.get(0).equals("source_bundle\tmanifest_sha256")) throw new IOException("Original replay parent capture receipt differs");
            String[] fields = capture.get(1).split("\t", -1); if (fields.length != 2 || fields[0].isEmpty() || !parent.equals(fields[1])) throw new IOException("Original replay parent differs from the captured parent identity");
        } else throw new IOException("Original completion kind is not an admitted task or genuine recomputation");
        checkWorkerReceipt(root, c); return c;
    }
    private static void checkWorkerReceipt(Path root, PopsOriginalFiles.Config c) throws IOException {
        var worker = PopsWorkflowResults.strings(load(root.resolve("WORKER_DONE.properties")));
        if (!worker.equals(Map.of("profile", PopsOriginalFiles.PROFILE, "edition", c.get("edition"), "task", c.get("task"), "source_jobs", Integer.toString(PopsOriginalCommands.jobs(root, c).size()), "explicit_convergence_warnings", "0"))) throw new IOException("Original worker receipt differs");
    }
    private static String warnings(Path root, PopsOriginalFiles.Config c, Map<?, ?> report, PrintStream err) throws IOException {
        StringBuilder text = new StringBuilder();
        List<String> lines = Files.readAllLines(root.resolve("results/warnings.tsv")); if (lines.isEmpty() || !lines.get(0).equals("code\tcount\taction")) throw new IOException("Source warning schema differs");
        for (String line : lines.subList(1, lines.size())) { String[] fields = line.split("\t", -1); if (fields.length != 3 || Integer.parseInt(fields[1]) < 1) throw new IOException("Invalid source warning table"); text.append("WARNING ").append(fields[0]).append('=').append(fields[1]).append(": ").append(fields[2]).append('\n'); }
        if (Boolean.TRUE.equals(report.get("control_only"))) text.append("WARNING: original 0.1 returned a control-only model; no newly selected feature is represented. Inspect the selected list and scientific interpretation.\n");
        if (Boolean.TRUE.equals(report.get("preparation_only"))) {
            long nan = ((Number) report.get("remaining_nan_values")).longValue(), infinite = ((Number) report.get("remaining_infinite_values")).longValue();
            if (nan + infinite > 0) text.append("WARNING: prepared data retain ").append(nan).append(" NaNs and ").append(infinite).append(" infinities under explicit nan_policy=").append(c.get("nan_policy")).append(". No model was fitted. Inspect inputs/policy before any dependent analysis; native finite-input fitting cannot consume nonfinite features.\n");
        }
        if (!text.isEmpty()) { Files.writeString(root.resolve("WARNINGS.md"), "\n# Task-specific warnings\n\n" + text, StandardOpenOption.APPEND); err.print(text); } return text.toString();
    }
}
