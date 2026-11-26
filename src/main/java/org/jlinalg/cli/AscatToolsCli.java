/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

public final class AscatToolsCli {
    private AscatToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})){emit(out,"ascat-tools --profiles\n--example binding|matrices|hts|targeted\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nOriginal ASCAT2.5.2/R4.0.2 or ASCAT3.2.0/R4.6.2. Complete matrix cohorts, original correction/germline prediction, single/joint segmentation, purity/ploidy fitting, per-locus/segment results, QC and plots. Version3.2 adds original alleleCounter-based HTS and targeted normal-panel preparation. Runtime bindings do not install software. Execution requires Linux x86_64 Java; Windows can use WSL. See docs/ascat-tools.md.\n\n"+AscatRuntime.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(AscatRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){AscatRuntime.platform();emit(out,"COMPLETE_ASCAT_RUNTIME_BINDING_ONLY "+AscatRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{})+"\nNo scientific user analysis was performed. "+AscatRuntime.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){
                AscatRuntime.platform();var spec=AscatRequest.read(Path.of(args[1]).toAbsolutePath().normalize());Path root=AscatBundle.capture(Path.of(args[3]),spec,()->{});warnings(root,err);emit(out,"COMPLETE_ASCAT_SCOPED_WORKFLOW "+root+"\n"+AscatRuntime.ACTION);return 0;
            }
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){AscatRuntime.platform();AscatBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});warnings(Path.of(args[3]).toAbsolutePath().normalize(),err);emit(out,"PASS_COMPLETE_ASCAT_BINARY_AND_NUMERICAL_REPLAY");return 0;}
            throw new IllegalArgumentException("Use ascat-tools --help");
        }catch(AscatRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT ASCAT ANALYSIS SKIPPED: "+e.getMessage()+diagnostics(args)+"\n"+AscatRuntime.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE ASCAT ANALYSIS: "+e.getMessage()+diagnostics(args)+"\n"+AscatRuntime.ACTION);return 2;}
    }
    private static void warnings(Path root,PrintStream err)throws IOException {
        var audit=NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),134217728));var warnings=NextflowFiles.list(audit.get("source_warnings"));
        for(Object item:warnings){var warning=NextflowFiles.object(item);emit(err,"WARNING ASCAT ORIGINAL SOURCE ["+NextflowFiles.text(warning.get("stage"))+"]: "+NextflowFiles.text(warning.get("message")));}
        emit(err,"Original source warnings: "+warnings.size()+". Review all original process stdout/stderr and WARNINGS.md.");
    }
    static Map<String,Object> example(String mode)throws IOException {
        String profile="ASCAT-3.2.0-R4.6.2";
        if(mode.equals("binding"))return map("schema_version",1,"profile",profile,"source","/absolute/unmodified/ASCAT-v3.2.0","python","/usr/bin/python3","environment",map("PATH","/absolute/R-prefix/bin:/usr/bin:/bin","LD_LIBRARY_PATH","/absolute/R-prefix/lib","R_LIBS_USER","/absolute/R-library","R_LIBS_SITE","","R_MAKEVARS_USER","/absolute/config/empty","R_ENVIRON_USER","/absolute/config/empty","R_PROFILE_USER","/absolute/config/empty","JAVA_HOME","/absolute/java21"),"roots",List.of("/absolute/R-prefix","/absolute/R-library","/absolute/config","/usr/share/fonts","/etc/fonts"),"counter",null);
        if(!Set.of("matrices","hts","targeted").contains(mode))throw new IOException("Choose binding, matrices, hts or targeted");boolean sequencing=!mode.equals("matrices"),targeted=mode.equals("targeted");var chromosomes=new ArrayList<String>();for(int i=1;i<=22;i++)chromosomes.add(""+i);chromosomes.add("X");
        var references=new ArrayList<Object>();if(sequencing)for(String chromosome:chromosomes)references.add(map("chromosome",chromosome,"alleles","/absolute/reference/alleles_chr"+chromosome+".txt","loci",targeted?null:"/absolute/reference/loci_chr"+chromosome+".txt"));
        var panel=new ArrayList<Object>();if(targeted)for(int i=1;i<=20;i++)panel.add(map("patient_id","P"+i,"id","N"+i,"sex",i<=10?"XX":"XY","hts","/absolute/panel/N"+i+".bam"));
        var settings=map("seed",20261126,"chromosomes",chromosomes,"sex_chromosomes",List.of("X","Y"),"genome","hg38","is_targeted",targeted,"segmentation","aspcf","penalty",70,"gamma",1,"genotype_platform",null,"y_limit",5,"min_ploidy",1.5,"max_ploidy",5.5,"min_purity",.1,"max_purity",1.05,"minimum_goodness_of_fit",90,"manual_rho",null,"manual_psi",null,"joint_germline_policy",null,"sample_weights",null,"joint_algorithm",null,"joint_refine",null,"nthreads",sequencing?1:null,"min_count",sequencing?20:null,"panel_min_count",targeted?20:null,"is_chr_based",targeted?true:null,"min_base_qual",sequencing?20:null,"min_map_qual",sequencing?35:null,"required_flag",sequencing?3:null,"filtered_flag",sequencing?3852:null,"loci_bin_size",sequencing?500:null);
        return map("schema_version",1,"profile",profile,"runtime","/absolute/completed-runtime-binding","preparation","Replace all example paths, complete cohort/panel membership, build and model parameters with the actual experiment. Example values do not establish a paper recipe or universal QC cutoff.","mode",mode,"samples",List.of(map("id","T1","sex","XX","tumor",sequencing?"/absolute/T1.bam":null,"normal",sequencing?"/absolute/N1.bam":null)),"matrices",sequencing?null:map("tumor_logR","/absolute/Tumor_LogR.txt","tumor_BAF","/absolute/Tumor_BAF.txt","normal_logR","/absolute/Germline_LogR.txt","normal_BAF","/absolute/Germline_BAF.txt"),"references",references,"normal_panel",panel,"bed",targeted?"/absolute/capture.bed":null,"problem_loci",null,"reference_fasta",null,"correction",map("mode","none","gc",null,"replication",null),"settings",settings,"capacity",map("input_bytes",1073741824,"output_bytes",4294967296L,"files",20000,"wall_millis",14400000,"native_cells",100000000,"bam_decoded_bytes",8589934592L,"bam_records",100000000,"bam_record_bytes",1048576),"process",map("wall_millis",10800000,"address_space_bytes",17179869184L,"cpu_seconds",10800,"file_bytes",1073741824,"open_files",4096,"workspace_bytes",8589934592L,"file_count",20000,"processes",64,"threads",256,"sampled_rss_bytes",12884901888L,"monitor_millis",100));
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    private static String diagnostics(String[] args){if(args.length!=4)return "";try{return "\nAttempt output (when created): "+Path.of(args[3]).toAbsolutePath().normalize();}catch(RuntimeException e){return "\nCorrect the invalid output path before retrying.";}}
}
