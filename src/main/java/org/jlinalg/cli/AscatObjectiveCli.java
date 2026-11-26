/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Portable native ASCAT numerical components, with no tumor model admission. */
public final class AscatObjectiveCli {
    private AscatObjectiveCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {
                emit(out,"ascat-objective --example\n--request REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURE\n\nNative ASCAT 2.5.2/3.2.0 segment runs and complete explicit purity/ploidy distance grid. Supply already aligned and segmented autosomal logR/BAF; JSON null marks a missing value. This command does not fit a tumor model. See docs/ascat-objective.md.\n\n"+AscatObjectiveBundle.ACTION);return 0;
            }
            if(Arrays.equals(args,new String[]{"--example"})){emit(out,NextflowJson.json(example()));return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")) {
                Path root=AscatObjectiveBundle.capture(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});
                emit(out,"COMPLETE_NATIVE_ASCAT_OBJECTIVE_COMPONENTS "+root+"\n"+AscatObjectiveBundle.ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")) {
                AscatObjectiveBundle.verify(Path.of(args[1]),false,()->{});emit(out,"PASS_COMPLETE_NATIVE_ASCAT_OBJECTIVE_REPLAY\n"+AscatObjectiveBundle.ACTION);return 0;
            }
            throw new IllegalArgumentException("Use ascat-objective --help");
        }catch(AscatObjectiveBundle.Unavailable e){emit(err,"SKIP3 DEPENDENT ASCAT OBJECTIVE SKIPPED: "+e.getMessage()+"\n"+AscatObjectiveBundle.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE ASCAT OBJECTIVE: "+e.getMessage()+"\n"+AscatObjectiveBundle.ACTION);return 2;}
    }
    static Object example() {
        return map("schema_version",1,"profile",AscatObjectiveBundle.PROFILE,
                "input_domain","aligned-segmented-autosomal-logR-BAF",
                "preparation","Synthetic illustration only. Replace with the actual alignment, autosome selection, segmentation, platform and reference provenance. No paper settings are inferred.",
                "loci",List.of(List.of(-.55,1),List.of(-.55,1),List.of(0,.5),List.of(0,.5),List.of(.32,.75)),
                "gamma",.55,"psi",List.of(1,1.5,2,2.5,3,4,5,6),"rho",List.of(.1,.2,.4,.6,.8,1,1.05),
                "capacity",map("rows",1000000,"cells",1000000,"segment_cell_operations",100000000,"output_bytes",134217728,"wall_millis",1800000));
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
