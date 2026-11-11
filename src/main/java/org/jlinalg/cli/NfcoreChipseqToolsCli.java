/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

/** Typed original nf-core/chipseq1.2.1 execution and portable complete-result verification. */
public final class NfcoreChipseqToolsCli {
    private NfcoreChipseqToolsCli() { }
    static final String ACTION = "Required action: read the latest attempt's WARNINGS.md, engine log/trace, process ledgers and complete source-component reports. Require a COMPLETED result and successful --verify before downstream use. SKIP3 means a missing/forbidden exact prerequisite or unsupported requested profile/capability: affected analysis skipped; install/download only where permitted through separate setup, or provide the admitted local runtime/source/image. FAIL2 means invalid/inconsistent input, changed integrity, process/cleanup/capacity failure or unassessable source output: no adapter result is admitted. Preserve originals, every failed attempt and all source missing/nonfinite values; resolve the named cause and use NEW paths for changed requests/inputs/capacities. --resume uses the immutable captured request/input/cache, with no fallback across indeterminate runs. This source-specific adapter executes original programs and checks complete selected output contracts; source feature classifications, optical clustering, Preseq extrapolation and negative-binomial fitting remain upstream methods. Biological accuracy, independent model reimplementation, whole-paper reproduction and unreported author settings are not certified. Hashes establish integrity, not authenticity. Other nf-core families/versions remain pending. Cell Ranger remains excluded.";
    public static void main(String[] args) {
        Path root = null; boolean fresh = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(System.out, "--request TYPED_REQUEST.json --home EXACT_NEXTFLOW21_HOME --setup-report CURRENT_HOST_REPORT --out NEW_RUN\n--resume ORIGINAL_RUN --home EXACT_NEXTFLOW21_HOME --setup-report CURRENT_HOST_REPORT\n--verify RUN_OR_PORTABLE_RESULT\n--export-runtime-setup NEW_DIRECTORY\n" + ACTION); return; }
            if (args.length == 2 && args[0].equals("--export-runtime-setup")) { NextflowSetup.export(Path.of(args[1])); emit(System.out, "Separate exact Nextflow setup exported. No installation or analysis performed.\n" + ACTION); return; }
            if (args.length == 2 && args[0].equals("--verify")) { var result = NfcoreChipseqBundle.verify(Path.of(args[1])); emit(System.out, "Complete original source-workflow result verified: " + result.payload() + "; manifest " + result.manifest().sha256() + ". Portable verification does not rerun analysis or readmit the producer host.\n" + ACTION); return; }
            var options = new LinkedHashMap<String, String>(); for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--request", "--home", "--setup-report", "--out", "--resume").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new java.io.IOException("Require complete unique typed options; see --help");
            boolean resume = options.containsKey("--resume"); if (resume ? options.containsKey("--request") || options.containsKey("--out") : !options.containsKey("--request") || !options.containsKey("--out")) throw new java.io.IOException("Use a new typed request/output, or explicit resume of the immutable saved run");
            if (!options.containsKey("--home") || !options.containsKey("--setup-report")) throw new NextflowProcess.Unavailable("Exact managed Nextflow home and current completed host setup report are required. Analysis skipped.");
            root = Path.of(options.get(resume ? "--resume" : "--out")).toAbsolutePath().normalize(); fresh = !resume && !Files.exists(root);
            Path result = NfcoreChipseqWorkflow.run(root, resume ? null : Path.of(options.get("--request")).toAbsolutePath().normalize(), Path.of(options.get("--home")).toAbsolutePath().normalize(), Path.of(options.get("--setup-report")).toAbsolutePath().normalize(), resume);
            emit(System.out, "Complete declared original nf-core/chipseq1.2.1 workflow and selected source-output contracts completed: " + result + "\n" + ACTION);
            emit(System.err, "WARNINGS AND REQUIRED ACTIONS: read " + result.resolve("WARNINGS.md") + ". Original annotation, coordinate, fit-fallback, runtime-mode and package warnings remain retained.");
        } catch (Exception | LinkageError failure) {
            int code = failure instanceof NextflowProcess.Unavailable || failure instanceof NextflowRuntime.Unavailable ? 3 : 2; String label = code == 3 ? "SKIP3 ANALYSIS SKIPPED" : "FAIL2 NO RESULT";
            emit(System.err, label + ": " + failure + "\n" + ACTION);
            if (fresh && root != null && Files.isDirectory(root) && !Files.exists(root.resolve("latest.json"))) try { NextflowFiles.writeJson(root.resolve("FAILURE.json"), Map.of("status", label, "error", failure.toString(), "required_action", ACTION)); Files.writeString(root.resolve("WARNINGS.md"), "# " + label + "\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); } catch (java.io.IOException diagnostic) { emit(System.err, "Required action: retain the run directory; diagnostic write failed: " + diagnostic); }
            System.exit(code);
        }
    }
    private static void emit(java.io.PrintStream output, String text) { output.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
