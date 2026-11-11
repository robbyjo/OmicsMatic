/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Explicit distinction between native prepared ordering and original-package inference. */
public final class MonocleToolsCli {
    private MonocleToolsCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream output,PrintStream error) {
        if(args.length==1&&args[0].equals("--help")) {
            emit(output,"monocle-tools ORDER --request ASCII_PATH.json\n"
                +"  Native Java Monocle3 prepared-graph ordering; explicit graph, coordinates, principal roots and source profile. No R runtime.\n"
                +"monocle-tools RUN --request ASCII_PATH.json\n"
                +"  Original Monocle3 1.4.27 raw-count trajectories, gene models and stock Moran tests with exact declared private R runtime.\n"
                +"Use ORDER --help or RUN --help for distinct closed request contracts. Read WARNINGS.md and require result_admitted=true.\n"
                +"SKIP3 skips unavailable original runtime/edition; FAIL2 admits no failed batch result. Install only where permitted; retry NEW output paths.\n"
                +"Development workflows. Canonical/released availability is recorded separately. No Monocle2, Cicero, py-monocle or whole-paper reproduction is implied.");return 0;
        }
        if(args.length>0&&args[0].equals("ORDER"))return Monocle3OrderCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("RUN"))return Monocle3WorkflowCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        emit(error,"FAIL2 NO RESULT ADMITTED: choose monocle-tools ORDER or RUN. REQUIRED ACTION: read --help, provide the corresponding typed request and NEW output paths.");return 2;
    }
    static void emit(PrintStream stream,String value){byte[] raw=(value+"\n").getBytes(StandardCharsets.UTF_8);stream.write(raw,0,raw.length);stream.flush();}
}
