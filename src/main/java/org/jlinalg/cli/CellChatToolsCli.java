/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.util.Arrays;

/** Dispatches explicit original CellChat workflows without changing their semantics. */
public final class CellChatToolsCli {
    private CellChatToolsCli() { }

    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }

    public static int run(String[] args, PrintStream output, PrintStream error) {
        if (args.length == 0 || args.length == 1 && args[0].equals("--help")) {
            output.println("""
                cellchat-tools FAMILY --request JSON | --verify BUNDLE | --help
                  inference  Original 1.0.0 or 2.1.2 condition inference; explicit source profile
                  legacy     Original 1.1.3 two-condition inference, similarity, rankNet and DEG
                  network    Original 1.1.3 embedding/clustering on a complete verified legacy parent
                  strength   Original 2.2.0 pooled/per-sample incoming/outgoing strength comparisons
                  workflow   Original 2.2.0 pooled/per-sample inference and directed-edge diagnostics

                Choose the family and scientific settings explicitly. No edition, UMAP engine,
                parallel strategy, database species or difference direction is inferred.
                Analysis installs nothing. Missing/forbidden exact runtimes mean SKIP3: skip
                dependent analysis. FAIL2 admits no new result; preserve the attempt and correct
                the named cause in NEW paths. Require exit0, result_admitted=true, successful
                --verify and review of WARNINGS.md, provenance and original output.
                Original invalid p-fields, fallback zeros, SNN neighbor anomalies, parallel RNG
                warnings and UMAP variability remain visible. Successful transport is neither
                biological validity nor historical paper reproduction. Read CELLCHAT_INTEGRATION.md.
                Resource-only database exports and native Java APIs are documented there separately.
                """
            );
            return 0;
        }
        String[] forwarded = Arrays.copyOfRange(args, 1, args.length);
        return switch (args[0]) {
            case "inference" -> CellChatInferenceCli.run(forwarded, output, error);
            case "legacy" -> CellChatLegacyCli.run(forwarded, output, error);
            case "network" -> CellChatNetworkCli.run(forwarded, output, error);
            case "strength" -> CellChatStrengthCli.run(forwarded, output, error);
            case "workflow" -> CellChatWorkflowCli.run(forwarded, output, error);
            default -> {
                error.println("FAIL2 NO CELLCHAT RESULT ADMITTED: Unknown explicit workflow family: " + args[0]
                    + "\nRequired action: use cellchat-tools --help, select the documented family and retry NEW output paths."
                    + " No analysis or installation was performed.");
                yield 2;
            }
        };
    }
}
