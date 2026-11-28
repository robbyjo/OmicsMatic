/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Explicit source-edition routes and a separately scoped generic original-engine interface. */
public final class NfcoreToolsCli {
    private NfcoreToolsCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    static int run(String[] args, PrintStream output, PrintStream error) {
        if (args.length > 0 && java.util.Set.of("--source-profiles", "--inspect-source", "--export-source-setup").contains(args[0]))
            return NfcoreSourceToolsCli.run(args, output, error);
        if (args.length == 0 || args.length == 1 && args[0].equals("--help")) {
            output.writeBytes(("nfcore-tools RNASPLICE-1.0.4 --help\n"
                +"  Complete original FASTQ graph, typed new inputs and selected source-output contracts.\n"
                +"nfcore-tools ATACSEQ-1.2.1 --help\n"
                +"  Exact original ATAC source workflow, explicit local references and selected output contracts.\n"
                +"nfcore-tools CHIPSEQ-1.2.1 --help\n"
                +"  Exact original ChIP-seq1.2.1 workflow and five selected source-output checks.\n"
                +"nfcore-tools CHIPSEQ-2.0.0 --help\n"
                +"  Exact original ChIP-seq2.0.0 workflow and four selected source-output checks.\n"
                +"nfcore-tools ATACSEQ-AUDIT-1.2.1 --help\n"
                +"  Portable source-output component check; no workflow/biological admission.\n"
                +"nfcore-tools ENGINE --help\n"
                +"  Generic original Nextflow engine; caller-declared workflow/output contracts.\n"
                +"nfcore-tools CAPTURED --help\n"
                +"  Generic complete-capture engine with full primary configuration binding.\n"
                +"Generic engine completion alone does not qualify an nf-core family or paper-specific analysis.\n"
                +NfcoreAtacseq121Cli.ACTION+"\n"+NfcoreSourceToolsCli.help()+"\n").getBytes(StandardCharsets.UTF_8));
            return output.checkError() ? 2 : 0;
        }
        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        return switch (args[0]) {
            case "RNASPLICE-1.0.4" -> NfcoreRnasplice104ToolsCli.run(tail, output, error);
            case "ATACSEQ-1.2.1" -> NfcoreAtacseq121Cli.run(tail, output, error);
            case "CHIPSEQ-1.2.1" -> NfcoreChipseqToolsCli.run(tail, output, error);
            case "CHIPSEQ-2.0.0" -> NfcoreChipseq200ToolsCli.run(tail, output, error);
            case "ATACSEQ-AUDIT-1.2.1" -> NfcoreAtacseq121AuditCli.run(tail, output, error);
            case "ENGINE" -> NfcoreEngineToolsCli.run(tail, output, error);
            case "CAPTURED" -> NfcoreCapturedToolsCli.run(tail, output, error);
            default -> {
                error.writeBytes(("FAIL2 NO RESULT ADMITTED: choose an explicit nfcore-tools action. Required action: read nfcore-tools --help. Unlisted pipeline editions are not inferred or substituted.\n").getBytes(StandardCharsets.UTF_8));
                yield 2;
            }
        };
    }
}
