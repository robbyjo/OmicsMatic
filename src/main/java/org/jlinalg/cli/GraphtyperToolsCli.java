/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Explicit original-edition joint calling and complete saved-input replay. */
public final class GraphtyperToolsCli {
    private GraphtyperToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})){emit(out,"graphtyper-tools --profiles\n--example binding|analysis|legacy-binding|legacy-analysis|v1.4-binding|v1.4-analysis\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nOriginal GraphTyper v1.4 (internally 1.3), v2.0-beta, v2.7.1 and v2.7.5 SNP/indel joint calling. Explicit complete cohort/reference/index capture, native read and VCF audits and fresh original calling replay. Native raw quality kernels are separate APIs, not the complete original HQ classifier. Legacy sorted merge is an explicit source operation distinct from original no-sort node orchestration. Linux x86_64 Java required for upstream execution. Commands install nothing. See docs/graphtyper-tools.md.\n\n"+GraphtyperRuntime.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(GraphtyperRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){Path root=GraphtyperRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});emit(err,GraphtyperRuntime.ACTION);emit(out,"COMPLETE_GRAPHTYPER_RUNTIME_BINDING_ONLY "+root);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){Path root=GraphtyperBundle.capture(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});warnings(root,err);emit(out,"COMPLETE_SCOPED_ORIGINAL_GRAPHTYPER_JOINT_CALLING "+root);return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){GraphtyperBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});warnings(Path.of(args[3]).toAbsolutePath().normalize(),err);emit(out,"PASS_COMPLETE_FRESH_GRAPHTYPER_JOINT_CALLING_REPLAY");return 0;}
            throw new IllegalArgumentException("Use graphtyper-tools --help");
        }catch(GraphtyperRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT GRAPHTYPER ANALYSIS SKIPPED: "+e.getMessage()+diagnostics(args)+"\n"+GraphtyperRuntime.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE GRAPHTYPER JOINT CALLING: "+e.getMessage()+diagnostics(args)+"\n"+GraphtyperRuntime.ACTION);return 2;}
    }
    private static void warnings(Path root,PrintStream err)throws IOException {for(String warning:NextflowFiles.strings(NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),16777216)).get("warnings")))emit(err,"WARNING GRAPHTYPER: "+warning);emit(err,GraphtyperRuntime.ACTION);}
    static Object example(String mode)throws IOException {
        requireMode(mode);boolean legacy=mode.startsWith("legacy-")||mode.startsWith("v1.4-"),v14=mode.startsWith("v1.4-");String id=v14?"graphtyper-v1.4-ebd4855d":legacy?"graphtyper-v2.0-beta-b9c603e8":"graphtyper-v2.7.5-ddc58a70";
        if(mode.endsWith("binding")){
            var programs=new LinkedHashMap<String,Object>();for(String name:legacy?List.of("python","graphtyper","samtools","tabix","bcftools","parallel"):List.of("python","graphtyper"))programs.put(name,"/absolute/private/bin/"+name);
            return map("schema_version",1,"profile",id,"source","/absolute/private/original-edition-source-view","pipeline",legacy?"/absolute/private/original-edition-pipeline-source-view":null,"programs",programs,"library_path",legacy?"/absolute/private/helper-library-directory":"","roots",List.of("/absolute/private/complete-selected-runtime-prefix"));
        }
        return map("schema_version",1,"profile",id,"runtime","/absolute/completed-graphtyper-runtime-binding","preparation","Replace this illustration with actual cohort membership and literal sample IDs, reference build, aligner and duplicate/coverage QC provenance, target-selection policy and any explicit input preparation. No author recipe is inferred.","reference","/absolute/actual-reference.fa","reference_index","/absolute/actual-reference.fa.fai","alignments",List.of(map("file","/absolute/actual-sample.bam","index","/absolute/actual-sample.bam.bai","format","bam")),"samples",List.of("actual.sample"),"regions",List.of(map("contig",v14?"chr20":"20","start",500,"end",9499)),"variant_sites",null,"variant_sites_index",null,"settings",map("mode","discovery","threads",1,"legacy_merge_policy",legacy?"original-slices-sorted":"not-applicable","legacy_slice_bases",legacy?4500:null,"legacy_pad_bases",legacy?200:null,"minimum_variant_support",legacy?4:null,"minimum_variant_support_ratio",legacy?0.18:null),"capacity",map("input_bytes",1000000000000L,"output_bytes",1000000000000L,"files",100000,"wall_millis",86400000,"alignment_records",10000000000L,"sam_characters",1000000000000L,"line_characters",1048576,"read_length",legacy?151:100000,"vcf_decoded_bytes",1000000000000L,"vcf_records",1000000000,"genotype_cells",1000000000000L,"vcf_header_lines",100000,"vcf_alleles",100000),"process",map("wall_millis",86400000,"address_space_bytes",34359738368L,"cpu_seconds",172800,"file_bytes",1000000000000L,"open_files",4096,"workspace_bytes",1000000000000L,"file_count",100000,"processes",128,"threads",256,"sampled_rss_bytes",25769803776L,"monitor_millis",100));
    }
    private static void requireMode(String mode)throws IOException {if(!Set.of("binding","analysis","legacy-binding","legacy-analysis","v1.4-binding","v1.4-analysis").contains(mode))throw new IOException("Choose an explicit binding/analysis example and edition; use --help");}
    private static String diagnostics(String[] args){if(args.length!=4)return"";try{return"\nAttempt output, when created: "+Path.of(args[3]).toAbsolutePath().normalize();}catch(RuntimeException e){return"\nCorrect the invalid output path before retrying.";}}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
