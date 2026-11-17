/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Explicitly selected native ordering and exact-edition original-package workflows. */
public final class MonocleToolsCli {
    private MonocleToolsCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream output,PrintStream error) {
        if(args.length==1&&args[0].equals("--help")) {
            emit(output,"monocle-tools ORDER --request ASCII_PATH.json\n"
                +"  Native Java Monocle3 prepared-graph ordering; explicit graph, coordinates, principal roots and source profile. No R runtime.\n"
                +"monocle-tools MORAN-LM --request UTF8_PATH.json\n"
                +"  Native covariate-aware linear-model residual Moran tests on complete prepared responses, explicit numeric design and directed weights. No R runtime.\n"
                +"monocle-tools RUN --request ASCII_PATH.json\n"
                +"  Original Monocle3 1.4.27 raw-count trajectories, gene models and stock Moran tests with exact declared private R runtime.\n"
                +"monocle-tools MODELS-1.3.1 --request UTF8_PATH.json\n"
                +"  Original Monocle3 1.3.1 prepared gene models, original coefficient/adjustment semantics and optional native Pearson filtering.\n"
                +"monocle-tools GRAPH-1.3.3 --request UTF8_PATH.json\n"
                +"  Original Monocle3 1.3.3 graph learning on a caller-prepared UMAP, with optional explicitly rooted ordering and native distance audit.\n"
                +"monocle-tools ALIGN-1.3.7 --request UTF8_PATH.json\n"
                +"  Original Monocle3 1.3.7 preprocessing, Batchelor 1.20.0 alignment, UMAP, graph learning and optional rooted ordering.\n"
                +"monocle-tools DIFFERENTIAL-2.26.0 --request ASCII_PATH.json\n"
                +"  Original Monocle2 2.26.0 typed differentialGeneTest and native complete-OK-family BH audit.\n"
                +"Use ACTION --help for each distinct closed request contract. The named editions require their exact separate Linux R/dependency profiles. No automatic edition substitution or installation.\n"
                +"Read WARNINGS.md and analysis-status.json; require result_admitted=true for inferential/pseudotime results. LEARN-only graphs/pipelines have their own explicit admission flags and admit no pseudotime.\n"
                +"SKIP3 skips unavailable original runtime/edition; FAIL2 admits no failed batch result. Install only where permitted; retry NEW output paths.\n"
                +"Source-build development workflows; released JAR availability is recorded separately. Cicero, py-monocle, modified paper graph tests and whole-paper reproduction remain separate.");return output.checkError()?2:0;
        }
        if(args.length>0&&args[0].equals("ORDER"))return Monocle3OrderCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("MORAN-LM"))return RegressionMoranCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("RUN"))return Monocle3WorkflowCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("MODELS-1.3.1"))return Monocle131ModelCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("GRAPH-1.3.3"))return Monocle133GraphCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("ALIGN-1.3.7"))return Monocle137WorkflowCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("DIFFERENTIAL-2.26.0"))return Monocle2DifferentialCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        emit(error,"FAIL2 NO RESULT ADMITTED: choose an explicit monocle-tools action. REQUIRED ACTION: read monocle-tools --help, provide the corresponding typed request and NEW output paths. Unlisted editions are not substituted.");return 2;
    }
    static void emit(PrintStream stream,String value){byte[] raw=(value+"\n").getBytes(StandardCharsets.UTF_8);stream.write(raw,0,raw.length);stream.flush();}
}
