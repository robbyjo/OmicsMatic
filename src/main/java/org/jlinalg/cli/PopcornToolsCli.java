/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Original two-population LD covariance scores and genetic correlation with native WLS verification. */
public final class PopcornToolsCli {
    private PopcornToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})){emit(out,"popcorn-tools --profiles\n--example binding|compute|fit|compute-fit\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nOriginal Popcorn1.0/1.1 two-population score computation and fitting, explicit genetic-effect/genetic-impact correlation, original regression or MLE, optional source block jackknife and native Java complete aligned-row WLS checks. Linux x86_64 Java required for upstream execution; Windows can use WSL. Native numerical API: org.jlinalg.genetics.PopcornRegression. Commands install nothing. See docs/popcorn-tools.md.\n\n"+PopcornRuntime.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(PopcornRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){Path root=PopcornRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});emit(err,PopcornRuntime.ACTION);emit(out,"COMPLETE_POPCORN_RUNTIME_BINDING_ONLY "+root);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){Path root=PopcornBundle.capture(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});warnings(root,err);emit(out,"COMPLETE_SCOPED_ORIGINAL_POPCORN_WORKFLOW "+root);return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){PopcornBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});warnings(Path.of(args[3]).toAbsolutePath().normalize(),err);emit(out,"PASS_COMPLETE_FRESH_POPCORN_REPLAY");return 0;}
            throw new IllegalArgumentException("Use popcorn-tools --help");
        }catch(PopcornRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT POPCORN ANALYSIS SKIPPED: "+e.getMessage()+diagnostics(args)+"\n"+PopcornRuntime.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE POPCORN WORKFLOW: "+e.getMessage()+diagnostics(args)+"\n"+PopcornRuntime.ACTION);return 2;}
    }
    private static void warnings(Path root,PrintStream err)throws IOException {var audit=NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),16777216));for(String warning:NextflowFiles.strings(audit.get("warnings")))emit(err,"WARNING POPCORN: "+warning);emit(err,PopcornRuntime.ACTION);}
    static Object example(String mode)throws IOException {
        String profile="Popcorn-1.0-3c21c41f";
        if(mode.equals("binding"))return map("schema_version",1,"profile",profile,"source","/absolute/unmodified/Popcorn-1.0-source-view","python","/absolute/private/venv/bin/python","library_path","","roots",List.of("/absolute/private/venv"));
        if(!Set.of("compute","fit","compute-fit").contains(mode))throw new IOException("Choose binding, compute, fit or compute-fit");boolean compute=!mode.equals("fit"),fit=!mode.equals("compute");
        return map("schema_version",1,"profile",profile,"runtime","/absolute/completed-Popcorn-runtime-binding","mode",mode,"preparation","Replace this illustration with actual phenotype/sample QC, panel ancestry/build, chromosome preparation and summary-statistic provenance. No paper recipe is inferred.","score_provenance",map("correlation","genetic-impact","description",compute?"Original scores will be computed from the explicitly supplied reference panels and settings.":"Supply the actual source version, settings, ancestry, reference build and evidence that these scores are genetic-impact scores."),"references",compute?List.of("/absolute/reference-population1","/absolute/reference-population2"):List.of(),"score_file",compute?null:"/absolute/original-two-population-scores.tsv","summaries",fit?List.of("/absolute/trait1-summary.tsv","/absolute/trait2-summary.tsv"):List.of(),"extract",null,"options",map("correlation","genetic-impact","maf",.05,"window_size",1000,"window_type","KBP","read_snps",1000,"store_snps",10000,"use_bias",false,"h2weight",false,"from_bp",null,"to_bp",null,"estimator","original-regression","intercept",true,"jackknife",true,"verbosity",1),"capacity",map("input_bytes",1073741824,"output_bytes",2147483648L,"files",20000,"wall_millis",1800000,"rows",10000000,"line_characters",1048576,"variants",10000000,"samples",100000,"genotype_cells",10000000000L),"process",map("wall_millis",900000,"address_space_bytes",17179869184L,"cpu_seconds",1800,"file_bytes",1073741824,"open_files",4096,"workspace_bytes",17179869184L,"file_count",20000,"processes",64,"threads",256,"sampled_rss_bytes",12884901888L,"monitor_millis",100));
    }
    private static String diagnostics(String[] args){if(args.length!=4)return"";try{return"\nAttempt output, when created: "+Path.of(args[3]).toAbsolutePath().normalize();}catch(RuntimeException e){return"\nCorrect the output path before retrying.";}}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
