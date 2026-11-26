/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

public final class AllcoolsRegionCli {
    private AllcoolsRegionCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {
                emit(out,"allcools-region-quantify --profiles\n--example\n--request TYPED_REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURE\n\nNative ALLCools 1.0.8 prepared region count saturation and hypo/hyper scores. Full raw counts are retained. Count output saturates at uint32; scores use uncapped counts. Zero-coverage regions have hyper-score 1.\n\n"+AllcoolsRegionBundle.ACTION);return 0;
            }
            if(Arrays.equals(args,new String[]{"--profiles"})) {
                emit(out,NextflowJson.json(map("schema_version",1,"engine","native","profile",AllcoolsRegionBundle.PROFILE,"source_behavior","ALLCools-1.0.8-uint32-binary16","quantifiers",List.of("count","hypo-score","hyper-score"),"raw_ALLC_or_Zarr_reader",false,"whole_package_admitted",false)));return 0;
            }
            if(Arrays.equals(args,new String[]{"--example"})){emit(out,NextflowJson.json(example()));return 0;}
            if(args.length==2&&args[0].equals("--verify")){AllcoolsRegionBundle.verify(Path.of(args[1]),false,()->{});emit(out,"PASS_COMPLETE_NATIVE_ALLCOOLS_REGION_NUMERICAL_REPLAY");return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")) {
                var spec=AllcoolsRegionRequest.read(Path.of(args[1]).toAbsolutePath().normalize(),true);
                Path root=AllcoolsRegionBundle.capture(Path.of(args[3]),spec,()->{});
                var audit=NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),1048576));
                emit(out,"COMPLETE_NATIVE_ALLCOOLS_REGION_QUANTIFICATION "+root+"\nWARNING: zero_coverage_hyper_one_rows="+audit.get("zero_coverage_hyper_one_rows")+", saturated_mc_rows="+audit.get("saturated_mc_rows")+", saturated_cov_rows="+audit.get("saturated_cov_rows")+"\n"+AllcoolsRegionBundle.ACTION);return 0;
            }
            throw new IllegalArgumentException("Use allcools-region-quantify --help, --profiles, --example, --request JSON --out NEW, or --verify CAPTURE");
        }catch(AllcoolsRegionBundle.Unavailable e){emit(err,"SKIP3 DEPENDENT ALLCOOLS REGION QUANTIFICATION SKIPPED: "+e.getMessage()+"\n"+AllcoolsRegionBundle.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE ALLCOOLS REGION QUANTIFICATION: "+e.getMessage()+"\n"+AllcoolsRegionBundle.ACTION);return 2;}
    }
    static Map<String,Object> example() {
        return map("schema_version",1,"engine","native","mode","prepared-region-quantification","source_behavior","ALLCools-1.0.8-uint32-binary16","preparation","Replace with actual complete cell/type/region aggregation, reference and ALLC preparation provenance. Declare all settings; no paper defaults are inferred.","input",map("counts","/absolute/raw-region-counts.tsv","format","literal-utf8-tsv"),"dimensions",map("cells",1,"mc_types",1,"features",5),"settings",map("hypo_cutoff",.9,"hyper_cutoff",.9,"survival","P(X>mc)","probability_epsilon",.000001,"count_output","saturate-uint32","score_output","numpy-1.23.5-binary16","count_domain","raw-nonnegative-int64-no-sum-overflow"),"capacity",map("input_bytes",1073741824,"output_bytes",1073741824,"wall_millis",3600000),"process",null);
    }
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
