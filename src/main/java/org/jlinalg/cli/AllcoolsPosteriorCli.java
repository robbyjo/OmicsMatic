/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

public final class AllcoolsPosteriorCli {
    private AllcoolsPosteriorCli() {}
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {emit(out,"allcools-posterior --profiles\n--example\n--request TYPED_REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURE\n\nNative ALLCools 1.0.8 prepared count posterior fractions and M values. Complete contiguous float64 feature axes per cell/type; explicit NumPy or xarray clipping.\n\n"+AllcoolsPosteriorBundle.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})) {emit(out,NextflowJson.json(map("schema_version",1,"engine","native","profile",AllcoolsPosteriorBundle.PROFILE,"source_behavior","ALLCools-1.0.8-float64","operations",List.of("posterior-fraction","m-value"),"representations",List.of("numpy","xarray"),"raw_ALLC_or_Zarr_reader",false,"whole_package_admitted",false)));return 0;}
            if(Arrays.equals(args,new String[]{"--example"})){emit(out,NextflowJson.json(example()));return 0;}
            if(args.length==2&&args[0].equals("--verify")){AllcoolsPosteriorBundle.verify(Path.of(args[1]),false,()->{});emit(out,"PASS_COMPLETE_NATIVE_ALLCOOLS_POSTERIOR_NUMERICAL_REPLAY");return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){var s=AllcoolsPosteriorRequest.read(Path.of(args[1]).toAbsolutePath().normalize(),true);Path root=AllcoolsPosteriorBundle.capture(Path.of(args[3]),s,()->{});emit(out,"COMPLETE_NATIVE_ALLCOOLS_PREPARED_POSTERIOR "+root+"\n"+AllcoolsPosteriorBundle.ACTION);return 0;}
            throw new IllegalArgumentException("Use allcools-posterior --help, --profiles, --example, --request JSON --out NEW, or --verify CAPTURE");
        }catch(AllcoolsPosteriorBundle.Unavailable e){emit(err,"SKIP3 DEPENDENT ALLCOOLS POSTERIOR SKIPPED: "+e.getMessage()+"\n"+AllcoolsPosteriorBundle.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE ALLCOOLS POSTERIOR: "+String.valueOf(e.getMessage())+"\n"+AllcoolsPosteriorBundle.ACTION);return 2;}
    }
    static Map<String,Object> example(){return map("schema_version",1,"engine","native","mode","prepared-methylation-posterior","source_behavior","ALLCools-1.0.8-float64","preparation","Replace with actual complete dataset, reference, regions and count preparation provenance. Dimensions and settings are explicit choices, not inferred paper defaults.","input",map("counts","/absolute/counts.tsv","format","literal-utf8-tsv"),"dimensions",map("cells",1,"mc_types",1,"features",5),"settings",map("representation","xarray","operation","posterior-fraction","normalize_per_cell",true,"clip_norm_value",10,"m_value_alpha",0.01,"variance_ddof",0,"reduction","contiguous-feature-axis-float64-nanmean-nanvar","count_domain","uint32-mc-le-cov"),"capacity",map("input_bytes",1073741824,"output_bytes",1073741824,"wall_millis",3600000),"process",null);}
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
