/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.assembly.MerquryBoundary;

/** Internal explicit-native-profile helper; its output is a boundary operation, not a ploidy table. */
public final class MerquryBoundaryCli {
    private MerquryBoundaryCli() { }
    public static void main(String[] args) {
        int code=run(args,System.out,System.err);if(code!=0)System.exit(code);
    }
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length!=1)throw new IOException("Supply exactly one complete original histogram path");Path input=Path.of(args[0]);
            if(!Files.isReadable(input)){err.println("SKIP3 NATIVE MERQURY BOUNDARY SKIPPED: histogram unavailable. Required action: restore the complete permitted input and retry a NEW analysis path.");return 3;}
            if(Files.size(input)>1073741824L)throw new IOException("Complete native histogram exceeds one-GiB helper capacity");
            var bins=new ArrayList<MerquryBoundary.Bin>();
            try(var reader=Files.newBufferedReader(input,StandardCharsets.US_ASCII)) {
                for(String line=reader.readLine();line!=null;line=reader.readLine()) {
                    if(line.length()>1024||bins.size()>=10000000)throw new IOException("Complete histogram row/population capacity exceeded");
                    String[] f=line.trim().split("\\s+",-1);
                    if(f.length!=2||!f[0].matches("[1-9][0-9]{0,9}")||!f[1].matches("[1-9][0-9]{0,18}"))throw new IOException("Positive literal int32 depth and int64 distinct count required");
                    bins.add(new MerquryBoundary.Bin(Integer.parseInt(f[0]),Long.parseLong(f[1])));
                }
            }
            var result=MerquryBoundary.calculate(bins,()->{if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Native boundary cancelled");});
            // Complete validation precedes all stdout. filt.sh uses the last column of row two.
            out.println("operation\tfirst_valley_found\teffective_minimum_depth");out.println("native-effective-minimum-depth\t"+result.firstValleyFound()+"\t"+result.effectiveMinimumDepth());
            if(!result.firstValleyFound())err.println("WARNING: no strict slope-sign valley; the source-defined boundary is zero. Inspect the complete histogram. No full ploidy table was inferred.");
            return 0;
        }catch(IOException|RuntimeException|OutOfMemoryError e){err.println("FAIL2 NO NATIVE MERQURY BOUNDARY: "+e.getMessage()+". Required action: preserve the attempt, correct the named histogram, domain, capacity or cancellation problem, then retry NEW paths. No complete ploidy table is produced by this operation.");return 2;}
    }
}
