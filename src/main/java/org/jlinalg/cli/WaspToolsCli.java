/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Original standalone WASP mapping with explicit prerequisite skips and complete replay. */
public final class WaspToolsCli {
    private WaspToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {emit(out,"wasp-tools --profiles\n--example binding|analysis\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nOriginal standalone WASP0.3.4 mapping from an aligned BAM, with text or phased-HDF5 variants, optional original VCF conversion, original BWA0.7.17 remapping/filtering, native complete-read QC and final indexed original-read BAM. Linux x86_64 Java required; Windows can use WSL. This command does not install software. See docs/wasp-tools.md.\n\n"+WaspRuntime.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(WaspRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){Path root=WaspRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});emit(err,WaspRuntime.ACTION);emit(out,"COMPLETE_WASP_RUNTIME_BINDING_ONLY "+root);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){Path root=WaspBundle.capture(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});warnings(root,err);emit(out,"COMPLETE_WASP_MAPPING_FILTER_WORKFLOW "+root);return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){WaspRuntime.platform();WaspBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});warnings(Path.of(args[3]).toAbsolutePath().normalize(),err);emit(out,"PASS_COMPLETE_FRESH_WASP_MAPPING_REPLAY");return 0;}
            throw new IllegalArgumentException("Use wasp-tools --help");
        }catch(WaspRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT WASP ANALYSIS SKIPPED: "+e.getMessage()+diagnostics(args)+"\n"+WaspRuntime.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE WASP WORKFLOW: "+e.getMessage()+diagnostics(args)+"\n"+WaspRuntime.ACTION);return 2;}
    }
    private static void warnings(Path root,PrintStream err)throws IOException {var audit=NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),268435456));for(String text:NextflowFiles.strings(audit.get("warnings")))emit(err,"WARNING WASP: "+text);emit(err,WaspRuntime.ACTION);}
    static Object example(String mode)throws IOException {
        String profile="WASP-0.3.4-d3b8447f-BWA-0.7.17";
        if(mode.equals("binding"))return map("schema_version",1,"profile",profile,"wasp_source","/absolute/unmodified/WASP-d3b8447f","bwa_source","/absolute/unmodified/BWA-0.7.17","python","/absolute/private/venv/bin/python","bwa","/absolute/private/bin/bwa","samtools","/absolute/private/bin/samtools","snp2h5",null,"library_paths",map("python","","samtools","","snp2h5",""),"roots",List.of("/absolute/private/venv","/absolute/private/bin","/absolute/private/libraries"));
        if(!mode.equals("analysis"))throw new IOException("Choose binding or analysis");
        return map("schema_version",1,"profile",profile,"runtime","/absolute/completed-WASP-binding","preparation","Replace paths, read/variant domain, actual reference/build/mapping provenance and all resource capacities with the experiment. This synthetic illustration does not infer an author recipe.","bam","/absolute/aligned.bam","reference",map("path","/absolute/reference.fa","format","fasta","alt_contigs",null),"variants",map("mode","text","directory","/absolute/per-contig-SNP-gzip-files"),"settings",map("read_layout","paired-with-optional-single-reads","max_sequences",64,"max_snps",6,"threads",1,"mark_shorter_split_hits_secondary",true,"insert_size",map("mean",250,"standard_deviation",150,"maximum",null,"minimum",null),"additional_bwa_options",List.of(),"mapping_provenance","Supply the original alignment and the actual equivalence or documented approximation of these explicit remapping options. Same names/lengths do not prove identical references or mapper settings.","python_hash_seed",897,"duplicate_policy","none","duplicate_random_seed",897,"bam_index_format","bai","minimum_final_records",1,"hdf_contig_policy",null,"explicit_no_variant_contigs",List.of()),"capacity",map("input_bytes",1073741824,"output_bytes",1073741824,"files",20000,"wall_millis",1800000,"records",1000000,"variant_rows",1000000,"line_characters",16777216,"decoded_bytes",4294967296L,"native_sam_characters",1000000000,"hdf_cells",10000000000L),"process",map("wall_millis",900000,"address_space_bytes",17179869184L,"cpu_seconds",1800,"file_bytes",1073741824,"open_files",4096,"workspace_bytes",17179869184L,"file_count",20000,"processes",64,"threads",256,"sampled_rss_bytes",12884901888L,"monitor_millis",100));
    }
    private static String diagnostics(String[] args){if(args.length!=4)return"";try{return"\nAttempt output, when created: "+Path.of(args[3]).toAbsolutePath().normalize();}catch(RuntimeException e){return"\nCorrect the invalid output path before retrying.";}}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
