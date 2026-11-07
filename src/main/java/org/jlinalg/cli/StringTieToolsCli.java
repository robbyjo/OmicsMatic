/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

/** Typed original StringTie assembly, estimation, merge and portable captured verification. */
public final class StringTieToolsCli {
    private StringTieToolsCli() { }
    /** Run an explicit request; downloads and installation are separate permitted setup operations.
     * @param args typed command options
     */
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    static int run(String[] args, PrintStream out, PrintStream err) {
        Runnable check = () -> { if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("StringTie interrupted"); };
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(out, help()); return 0; }
            if (args.length == 1 && args[0].equals("--runtime-profiles")) { emit(out, NextflowJson.json(NextflowFiles.object(StringTieSetupEvidence.lock().get("profiles")).keySet().stream().map(Object::toString).sorted().toList())); return 0; }
            if (args.length == 2 && args[0].equals("--export-runtime-setup")) { StringTieSetupExport.export(absolute(args[1])); emit(out, "Exported StringTie setup kit. Read RUN-SETUP.txt; no installation or analysis performed."); return 0; }
            if (args.length == 2 && args[0].equals("--verify")) { report(StringTieWorkflow.verify(absolute(args[1]), check), true, out, err); return 0; }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--recipe", "--home", "--setup-report", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require unique typed StringTie value options; use --help");
            if (!options.containsKey("--recipe") || !options.containsKey("--out")) throw new IOException("Require --recipe REQUEST.json and --out NEW_DIRECTORY");
            if (!options.containsKey("--home") || !options.containsKey("--setup-report")) throw new StringTieRuntime.Unavailable("Exact managed --home and complete --setup-report are required; export the setup kit, complete permitted setup or skip this dependent analysis");
            report(StringTieWorkflow.start(absolute(options.get("--recipe")), absolute(options.get("--home")), absolute(options.get("--setup-report")), absolute(options.get("--out")), check), false, out, err); return 0;
        } catch (Exception error) {
            int code = error instanceof StringTieRuntime.Unavailable ? 3 : 2; emit(err, "StringTie " + (code == 3 ? "SKIP3 — ANALYSIS SKIPPED" : "FAIL2 — NO RESULT ADMITTED") + ": " + error + "\n" + StringTieWorkflow.ACTION); return code;
        }
    }
    private static void report(StringTieWorkflow.Result result, boolean verified, PrintStream out, PrintStream err) throws IOException {
        emit(out, (verified ? "Verified" : "Completed") + " original StringTie " + result.mode() + ": " + result.transcripts() + " transcripts; " + result.root());
        emit(err, "StringTie WARNING: Read " + result.root().resolve("WARNINGS.md") + " and original fit/runtime process logs. Native audits do not certify biological accuracy.\n" + StringTieWorkflow.ACTION);
    }
    private static Path absolute(String value) { return Path.of(value).toAbsolutePath().normalize(); }
    private static void emit(PrintStream stream, String value) { stream.writeBytes((value + "\n").getBytes(StandardCharsets.UTF_8)); }
    static String help() { return """
        StringTieToolsCli --recipe REQUEST.json --home EXACT_HOME --setup-report COMPLETE_SETUP --out NEW_DIRECTORY
        StringTieToolsCli --verify CAPTURED_ANALYSIS
        StringTieToolsCli --export-runtime-setup NEW_DIRECTORY
        StringTieToolsCli --runtime-profiles | --help
        Exact Linux x86_64 editions: 1.3.0, 1.3.6, 2.1.2, 2.1.7, 2.2.1, 3.0.0, 3.0.3.
        Typed modes: ASSEMBLE, ESTIMATE, MERGE. Execution requires Linux Java.
        Complete captured verification and setup export also work on Windows.
        Assembly/estimation use complete coordinate-sorted genomic BGZF BAM.
        Estimation requires a complete guide plus explicit omission policy.
        Merge uses a complete explicit GTF list; no BAM or alignment-strand override.
        Every mode requires exact chromosome sizes and a declared genome-build label.
        No opaque flags, inferred paper settings, filtering or input conversion.
        """ + StringTieWorkflow.ACTION; }
}
