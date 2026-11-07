/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

/** Typed original Salmon index and bulk quantification command. */
public final class SalmonToolsCli {
    private SalmonToolsCli() { }
    /** Runs the standalone command; exact runtime setup and user fitting are separate operations.
     * @param args typed command options
     */
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    static int run(String[] args, PrintStream out, PrintStream err) {
        Path root = null;
        Runnable check = () -> { if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Salmon interrupted"); };
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(out, help()); return 0; }
            if (args.length == 1 && args[0].equals("--runtime-profiles")) { emit(out, NextflowJson.json(SalmonIndexRecipe.profiles())); return 0; }
            if (args.length == 2 && args[0].equals("--export-runtime-setup")) { SalmonSetupExport.export(Path.of(args[1])); emit(out, "Exported standalone Salmon setup kit. Follow RUN-SETUP.txt for permitted installation or recheck. User analysis admitted=false."); return 0; }
            if (args.length == 2 && args[0].equals("--verify-index")) {
                var result = SalmonIndexWorkflow.verify(absolute(args[1]), check); emit(out, "Verified complete Salmon index: " + result.audit().ledger().retained() + " retained references. Expression analysis admitted=false.\n" + SalmonIndexWorkflow.ACTION); warnings(result.root(), err); return 0;
            }
            if (args.length == 2 && args[0].equals("--verify")) { report(SalmonQuantWorkflow.verify(absolute(args[1]), check), true, out, err); return 0; }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--index-recipe", "--recipe", "--home", "--setup-report", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null)
                throw new IOException("Require unique typed Salmon value options; use --help");
            boolean index = options.containsKey("--index-recipe");
            if (index == options.containsKey("--recipe") || !options.containsKey("--out")) throw new IOException("Require exactly one --index-recipe or --recipe, plus --out NEW_DIRECTORY");
            root = absolute(options.get("--out"));
            if (!options.containsKey("--home") || !options.containsKey("--setup-report")) throw new SalmonRuntime.Unavailable("Exact managed --home and completed --setup-report are required. Export --export-runtime-setup, follow RUN-SETUP.txt using only permitted installation/download flags, and skip dependent analysis until setup succeeds");
            Path home = absolute(options.get("--home")), setup = absolute(options.get("--setup-report")), recipe = absolute(options.get(index ? "--index-recipe" : "--recipe"));
            if (index) {
                var result = SalmonIndexWorkflow.start(recipe, home, setup, root, check); emit(out, "Completed original Salmon index: " + result.audit().ledger().retained() + " retained references; " + result.root() + ". Expression analysis admitted=false.\n" + SalmonIndexWorkflow.ACTION); warnings(result.root(), err);
            } else report(SalmonQuantWorkflow.start(recipe, home, setup, root, check), false, out, err);
            return 0;
        } catch (Exception failure) {
            int code = failure instanceof SalmonRuntime.Unavailable ? 3 : 2; String label = code == 3 ? "SKIP3 — ANALYSIS SKIPPED" : "FAIL2 — NO RESULT ADMITTED";
            String detail = failure.toString(); if (detail.length() > 16384) detail = detail.substring(0, 16384) + " [bounded console diagnostic; preserve full raw logs]";
            emit(err, "Salmon " + label + ": " + detail + "\n" + SalmonQuantWorkflow.ACTION);
            if (root != null && Files.exists(root)) emit(err, "Attempt evidence: " + root + "; inspect WARNINGS.md, FAILURE.json and all original logs. Preserve this attempt.");
            return code;
        }
    }
    private static void report(SalmonQuantWorkflow.Result result, boolean verified, PrintStream out, PrintStream err) throws IOException {
        emit(out, (verified ? "Verified" : "Completed") + " Salmon quantification for " + result.output().table().rows().size() + " targets; declared retained coverage=" + result.inputs().declaredRetainedPopulationMatchesSource() + "; " + result.root() + ".\n" + SalmonQuantWorkflow.ACTION);
        if (!result.inputs().declaredRetainedPopulationMatchesSource()) emit(err, "Salmon WARNING: Only the explicitly selected original-runtime population completed. Declared retained coverage=false. Inspect index-bundle/REFERENCE-LEDGER.jsonl; missing declared transcripts are neither relabeled decoys nor imputed zeros.");
        warnings(result.root(), err);
    }
    private static void warnings(Path root, PrintStream err) throws IOException { for (String line : NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("WARNINGS.md"), 1048576)).lines().toList()) if (line.startsWith("WARNING:")) emit(err, "Salmon " + line); }
    private static Path absolute(String value) { return Path.of(value).toAbsolutePath().normalize(); }
    private static void emit(PrintStream stream, String value) { stream.writeBytes((value + "\n").getBytes(StandardCharsets.UTF_8)); }
    static String help() {
        return """
            salmon-tools --index-recipe INDEX.json --home EXACT_HOME --setup-report COMPLETED_SETUP --out NEW_INDEX_BUNDLE
            salmon-tools --recipe QUANT.json --home EXACT_HOME --setup-report COMPLETED_SETUP --out NEW_ANALYSIS
            salmon-tools --verify-index INDEX_BUNDLE
            salmon-tools --verify ANALYSIS
            salmon-tools --export-runtime-setup NEW_DIRECTORY
            salmon-tools --runtime-profiles | --help
            Exact original Linux x86_64 editions: 1.0.0, 1.1.0, 1.5.2, 1.8.0, 1.9.0.
            Supply a typed JSON recipe with complete input/reference/library/model choices.
            Mapping uses a complete verified index bundle from the same exact edition.
            Paired/single FASTQ and query-grouped transcriptome SAM/BAM are separate modes.
            Genomic spliced alignments require explicit external transcriptome projection.
            Single-end reads require an explicit fragment-length mean and SD.
            Execution requires Linux Java (Linux Java inside WSL on Windows).
            Complete captured verification and setup export also work on Windows.
            """ + SalmonQuantWorkflow.ACTION;
    }
}
