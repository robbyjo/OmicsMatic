/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.util.Arrays;

/** Explicit routing between prepared native nearest mapping and original TxDb annotation. */
public final class ChipSeekerToolsCli {
    private ChipSeekerToolsCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream output,PrintStream error){
        if(args.length==1&&args[0].equals("--help")){
            output.println("chipseeker-tools NEAREST --request ASCII_PATH.json\n"
                +"  Native Java prepared unstranded peaks plus ordered +/- feature ranges. No R installation.\n"
                +"chipseeker-tools ANNOTATE --request ASCII_PATH.json\n"
                +"  Original exact-edition ChIPseeker annotatePeak on explicit peaks and TxDb SQLite; genomic labels remain upstream.\n"
                +"Use NEAREST --help or ANNOTATE --help for each distinct typed request contract.\n"
                +"No genome, TxDb, software edition or enhancer rule is inferred. Read WARNINGS.md and require result_admitted=true.\n"
                +"SKIP3 skips an unavailable dependency; FAIL2 admits no result. Install only where permitted and retry into NEW paths.\n"
                +"These are development workflows; canonical/released JAR availability is recorded separately.");return 0;
        }
        if(args.length>0&&args[0].equals("NEAREST"))return ChipSeekerNearestCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        if(args.length>0&&args[0].equals("ANNOTATE"))return ChipSeekerAnnotationCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        error.println("FAIL2 NO RESULT ADMITTED: choose chipseeker-tools NEAREST or ANNOTATE. Required action: read --help, provide the corresponding typed request and use NEW output paths.");return 2;
    }
}
