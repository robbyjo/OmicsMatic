/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.ContactInsulationCompute.map;

/** Explicit native prepared contact-map insulation; omitted upstream operations stay separate. */
public final class ContactInsulationCli {
    private ContactInsulationCli() {}
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {
                emit(out,"contact-insulation --profiles\n--example\n--request TYPED_REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURE\n\nNative prepared sparse-map insulation and fixed numeric boundary strengths. Explicit settings, complete inputs and original numeric/missing-value behavior. Raw Cooler preparation and whole upstream operations remain separate.\n\n"+ContactInsulationBundle.ACTION);return 0;
            }
            if(Arrays.equals(args,new String[]{"--profiles"})) {
                emit(out,NextflowJson.json(map("schema_version",1,"engine","native","profile",ContactInsulationBundle.PROFILE,"source_behavior","cooltools-0.5.1-0.5.4-common-insulation","normalization","region-nanmedian","threshold_comparison",">=","automatic_thresholds",false,"raw_Cooler_reader",false,"original_runtime_executed",false,"whole_package_admitted",false)));return 0;
            }
            if(Arrays.equals(args,new String[]{"--example"})){emit(out,NextflowJson.json(example()));return 0;}
            if(args.length==2&&args[0].equals("--verify")) {
                ContactInsulationBundle.verify(Path.of(args[1]),false,()->{});emit(out,"PASS_COMPLETE_NATIVE_CONTACT_INSULATION_NUMERICAL_REPLAY");return 0;
            }
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")) {
                var spec=ContactInsulationRequest.read(Path.of(args[1]).toAbsolutePath().normalize(),true);
                Path root=ContactInsulationBundle.capture(Path.of(args[3]),spec,()->{});
                emit(out,"COMPLETE_NATIVE_PREPARED_CIS_INSULATION_FIXED_BOUNDARIES "+root+"\n"+ContactInsulationBundle.ACTION);return 0;
            }
            throw new IllegalArgumentException("Use contact-insulation --help, --profiles, --example, --request JSON --out NEW, or --verify CAPTURE");
        }catch(ContactInsulationBundle.Unavailable e) {
            emit(err,"SKIP3 DEPENDENT CONTACT INSULATION SKIPPED: "+e.getMessage()+"\n"+ContactInsulationBundle.ACTION);return 3;
        }catch(IOException|RuntimeException|OutOfMemoryError e) {
            emit(err,"FAIL2 NO COMPLETE CONTACT INSULATION: "+String.valueOf(e.getMessage())+"\n"+ContactInsulationBundle.ACTION);return 2;
        }
    }
    static Map<String,Object> example() {
        return map("schema_version",1,"engine","native","mode","prepared-cis-insulation-fixed-boundaries","source_behavior","cooltools-0.5.1-0.5.4-common-insulation",
            "preparation","Replace with actual complete input, reference, preparation and balancing provenance. These example settings are user choices, not inferred paper settings.",
            "input",map("bins","/absolute/bins.tsv","pixels","/absolute/pixels.tsv","format","literal-utf8-tsv"),
            "settings",map("bin_width",10000,"windows_bp",List.of(30000,100000),"ignore_diagonals",2,"min_distance_bad",0,"min_fraction_valid",0.66,"strength_threshold",0.1,"threshold_comparison",">=","weights","supplied-weight-column","normalization","region-nanmedian"),
            "capacity",map("bins",1000000,"pixel_rows",10000000,"input_bytes",1073741824,"output_bytes",1073741824,"wall_millis",3600000),"process",null);
    }
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
