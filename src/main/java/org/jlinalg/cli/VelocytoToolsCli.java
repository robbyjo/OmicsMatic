/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Source-bound original counting, explicit native preparation and complete native layer export. */
public final class VelocytoToolsCli {
    private VelocytoToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})){emit(out,"velocyto-tools --profiles\n--example binding|analysis|analysis-uint64\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nOriginal velocyto0.17.17 CB/UB droplet counting from aligned BAMs and actual GTF, explicit MA-to-UB/antibody-contig preparation, complete original Loom/native count audits and exact S+U MatrixMarket export. The default CLI uses uint16 internal buffers even with uint32 output. Explicit original Counter API uint64 is a different counting-integer mode. No RNA velocities, scVelo or author recipe are inferred. Linux x86_64 Java required for upstream execution; native BAM/matrix APIs also run on Windows. Commands install nothing. See docs/velocyto-tools.md.\n\n"+VelocytoRuntime.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(VelocytoRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){Path root=VelocytoRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});emit(err,VelocytoRuntime.ACTION);emit(out,"COMPLETE_VELOCYTO_RUNTIME_BINDING_ONLY "+root);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){Path root=VelocytoBundle.capture(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});warnings(root,err);emit(out,"COMPLETE_SCOPED_ORIGINAL_VELOCYTO_COUNT_WORKFLOW "+root);return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){VelocytoBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});warnings(Path.of(args[3]).toAbsolutePath().normalize(),err);emit(out,"PASS_COMPLETE_FRESH_VELOCYTO_COUNT_REPLAY");return 0;}
            throw new IllegalArgumentException("Use velocyto-tools --help");
        }catch(VelocytoRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT VELOCYTO ANALYSIS SKIPPED: "+e.getMessage()+diagnostics(args)+"\n"+VelocytoRuntime.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE VELOCYTO COUNT WORKFLOW: "+e.getMessage()+diagnostics(args)+"\n"+VelocytoRuntime.ACTION);return 2;}
    }
    private static void warnings(Path root,PrintStream err)throws IOException {var audit=NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),16777216));for(String warning:NextflowFiles.strings(audit.get("warnings")))emit(err,"WARNING VELOCYTO: "+warning);emit(err,VelocytoRuntime.ACTION);}
    static Object example(String mode)throws IOException {
        String profile="velocyto-0.17.17-pypi-1ad65fc5-samtools-1.19.2";
        if(mode.equals("binding"))return map("schema_version",1,"profile",profile,"source","/absolute/private/original-source","python","/absolute/private/venv/bin/python","samtools","/absolute/private/samtools-helper/usr/bin/samtools","library_paths",map("python","","samtools","/absolute/private/samtools-helper/usr/lib/x86_64-linux-gnu"),"roots",List.of("/absolute/private/complete-velocyto-prefix"));
        if(!Set.of("analysis","analysis-uint64").contains(mode))throw new IOException("Choose binding, analysis or analysis-uint64");boolean wide=mode.equals("analysis-uint64");
        return map("schema_version",1,"profile",profile,"runtime","/absolute/completed-velocyto-runtime-binding","preparation","Replace this illustration with actual sample identity, reference/annotation build, CB/UB chemistry and alignment/QC provenance. Optional MA preparation and antibody contig exclusion must describe the actual input. No author recipe is inferred.","bams",List.of("/absolute/sample-coordinate-sorted.bam"),"gtf","/absolute/actual-compatible-annotation.gtf","barcode_file","/absolute/actual-barcodes.tsv","repeat_mask",null,"settings",map("sample_id","sample","counter_engine",wide?"original-counter-api-uint64":"original-cli-uint16-buffers","logic","Default","umi_tag","UB","excluded_contigs",List.of(),"allowed_unannotated_contigs",List.of(),"multimap",false,"output_dtype",wide?"uint64":"uint32","samtools_threads",1,"samtools_memory_mb",64,"python_hash_seed",899,"verbosity",3),"capacity",map("input_bytes",1073741824,"output_bytes",2147483648L,"files",20000,"wall_millis",1800000,"records",10000000,"sam_characters",10000000000L,"cells",100000,"genes",100000,"molecule_keys",10000000,"gtf_rows",10000000,"line_characters",1048576,"matrix_entries",1000000000,"sparse_entries",100000000,"decoded_matrix_bytes",28000000000L),"process",map("wall_millis",900000,"address_space_bytes",17179869184L,"cpu_seconds",1800,"file_bytes",1073741824,"open_files",4096,"workspace_bytes",17179869184L,"file_count",20000,"processes",64,"threads",256,"sampled_rss_bytes",12884901888L,"monitor_millis",100));
    }
    private static String diagnostics(String[] args){if(args.length!=4)return"";try{return"\nAttempt output, when created: "+Path.of(args[3]).toAbsolutePath().normalize();}catch(RuntimeException e){return"\nCorrect the invalid output path before retrying.";}}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
