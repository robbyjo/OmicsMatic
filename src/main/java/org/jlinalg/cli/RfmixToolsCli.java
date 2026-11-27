/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

public final class RfmixToolsCli {
    private RfmixToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})){emit(out,"rfmix-tools --profiles\n--example binding|analysis\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nOriginal RFMix v2.03-r0 local ancestry on explicit phased query/reference VCF, gzip VCF or indexed BCF, TAB sample map and genetic map. Native Java audits complete captured input and MSP/FB/Q output populations. Linux x86_64 Java required for inference; Windows can use WSL. Runtime binding does not install software. See docs/rfmix-tools.md.\n\n"+RfmixRuntime.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(RfmixRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){RfmixRuntime.platform();Path root=RfmixRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});emit(out,"COMPLETE_RFMIX_RUNTIME_BINDING_ONLY "+root+"\nNo scientific user analysis was performed. "+RfmixRuntime.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){RfmixRuntime.platform();var spec=RfmixRequest.read(Path.of(args[1]).toAbsolutePath().normalize());Path root=RfmixBundle.capture(Path.of(args[3]),spec,()->{});warnings(root,err);emit(out,"COMPLETE_RFMIX_MSP_FB_Q_WORKFLOW_SIS_UNQUALIFIED "+root+"\n"+RfmixRuntime.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){RfmixRuntime.platform();RfmixBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});warnings(Path.of(args[3]).toAbsolutePath().normalize(),err);emit(out,"PASS_COMPLETE_RFMIX_BINARY_AND_NUMERICAL_REPLAY");return 0;}
            throw new IllegalArgumentException("Use rfmix-tools --help");
        }catch(RfmixRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT RFMIX ANALYSIS SKIPPED: "+e.getMessage()+diagnostics(args)+"\n"+RfmixRuntime.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE RFMIX ANALYSIS: "+e.getMessage()+diagnostics(args)+"\n"+RfmixRuntime.ACTION);return 2;}
    }
    private static void warnings(Path root,PrintStream err)throws IOException{for(Object item:NextflowFiles.list(NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),134217728)).get("warnings")))emit(err,"WARNING RFMIX: "+NextflowFiles.text(item));emit(err,"Inspect every original process stdout/stderr and WARNINGS.md. SIS-dependent analysis is SKIPPED.");}
    static Map<String,Object> example(String mode)throws IOException{
        String profile="RFMIX-2.03-r0-09599c19";
        if(mode.equals("binding"))return map("schema_version",1,"profile",profile,"source","/absolute/unmodified/rfmix-09599c19","prefix","/absolute/private/rfmix-prefix","executable","/absolute/private/rfmix-prefix/bin/rfmix","python","/usr/bin/python3","helpers",map("gzip","/usr/bin/gzip","bcftools",null),"environment",map("PATH","/usr/bin:/bin","LD_LIBRARY_PATH",""),"bcftools_library_path","","roots",List.of("/absolute/private/rfmix-prefix"));
        if(!mode.equals("analysis"))throw new IOException("Choose binding or analysis");
        return map("schema_version",1,"profile",profile,"runtime","/absolute/completed-runtime-binding","preparation","Replace paths, complete cohort, actual phasing/build and model parameters with the experiment. Examples do not establish a paper recipe or universal defaults. Supply existing CSI indexes for BCF. Original input bytes are retained without genotype/allele repair.","query",map("path","/absolute/query.vcf","format","vcf","index",null),"reference",map("path","/absolute/reference.vcf","format","vcf","index",null),"sample_map",map("path","/absolute/sample-map.tsv","format","text"),"genetic_map","/absolute/genetic-map.tsv","settings",map("chromosome","22","rf_window",map("unit","snps","value",50),"crf_spacing",map("unit","snps","value",5),"crf_weight",map("mode","fixed","value",1),"generations",8,"em_iterations",0,"reanalyze_reference",false,"trees",100,"node_size",2,"max_missing",.05,"bootstrap_mode",1,"min_snps",10,"range",null,"threads",1,"seed",20261127),"capacity",map("input_bytes",1073741824,"output_bytes",1073741824,"files",20000,"wall_millis",14400000,"variants",1000000,"samples",10000,"populations",64,"native_cells",1000000000,"line_characters",16777216,"decoded_bytes",4294967296L),"process",map("wall_millis",10800000,"address_space_bytes",17179869184L,"cpu_seconds",10800,"file_bytes",1073741824,"open_files",4096,"workspace_bytes",8589934592L,"file_count",20000,"processes",64,"threads",256,"sampled_rss_bytes",12884901888L,"monitor_millis",100));
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    private static String diagnostics(String[] args){if(args.length!=4)return "";try{return "\nAttempt output (when created): "+Path.of(args[3]).toAbsolutePath().normalize();}catch(RuntimeException e){return "\nCorrect the invalid output path before retrying.";}}
}
