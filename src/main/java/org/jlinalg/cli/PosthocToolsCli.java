/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.util.Arrays;

/** Explicit ordinary-mean and pooled-rank post-hoc models. */
public final class PosthocToolsCli {
    private PosthocToolsCli() { }
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        if(args.length==0||args.length==1&&args[0].equals("--help")) {
            int legacy=TukeyKramerCli.run(new String[]{"--help"},output,error);if(legacy!=0)return legacy;
            output.println("\nposthoc-tools dunn --request UTF8_JSON\n  Independent-sample pooled-midrank Dunn comparisons with explicit correction and contrast family. Use dunn --help for its request.\nNative Java analysis; no R or Prism runtime. These methods require their stated model and do not infer a paper's design or settings. Read each output's WARNINGS.md; SKIP3 skips unavailable prerequisites, FAIL2 admits no failed family.");output.flush();return output.checkError()?2:0;
        }
        if(args[0].equals("dunn"))return DunnComparisonsCli.run(Arrays.copyOfRange(args,1,args.length),output,error);
        return TukeyKramerCli.run(args,output,error);
    }
}
