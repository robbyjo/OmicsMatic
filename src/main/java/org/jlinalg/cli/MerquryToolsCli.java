/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

public final class MerquryToolsCli {
    private MerquryToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {
                emit(out,"merqury-tools --profiles\n--example evaluate|count-kmers|build-hapmers|binding\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nComplete ordinary k-mer counting, assembly QV/completeness/copy spectra/position tracks, optional source false duplication, and parental hap-mer construction. Explicit native-effective-depth and palette profile is separate from original editions. Linux x86_64 execution and verification require a prepared permitted runtime.\n\n"+MerquryBundle.ACTION);return 0;
            }
            if(Arrays.equals(args,new String[]{"--profiles"})) {emit(out,NextflowJson.json(map("editions",MerquryProfile.profiles(),"source_modes",List.of("original","native-boundary-palette-v1"),"native_mode_edition","1.4.1","legacy_tdf_inspection","complete-original-IGV-datasets-and-deterministic-coverage","whole_package_admitted",false)));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){Path result=MerquryRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});emit(out,"COMPLETE_MERQURY_RUNTIME_BINDING_ONLY "+result+"\nNo user analysis was performed. "+MerquryBundle.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){var spec=MerquryRequest.read(Path.of(args[1]).toAbsolutePath().normalize());Path root=MerquryBundle.capture(Path.of(args[3]),spec,()->{});emit(out,"COMPLETE_MERQURY_SCOPED_WORKFLOW "+root+"\nWARNING: inspect source_mode, Inf/NaN, complete spectra and all original logs. "+MerquryBundle.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){MerquryBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});emit(out,"PASS_COMPLETE_MERQURY_BINARY_AND_NUMERICAL_REPLAY");return 0;}
            throw new IllegalArgumentException("Use merqury-tools --help");
        }catch(MerquryRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT MERQURY ANALYSIS SKIPPED: "+e.getMessage()+"\n"+MerquryBundle.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE MERQURY ANALYSIS: "+e.getMessage()+"\n"+MerquryBundle.ACTION);return 2;}
    }
    static Map<String,Object> example(String mode)throws IOException {
        if(mode.equals("binding"))return map("schema_version",1,"edition","1.4.1","source_mode","native-boundary-palette-v1","source","/absolute/unmodified/merqury-1.4.1","python","/usr/bin/python3","environment",map("PATH","/absolute/meryl-1.4.1/bin:/absolute/igv:/absolute/samtools/bin:/absolute/R-prefix/bin:/absolute/bedtools/bin:/usr/bin:/bin","LD_LIBRARY_PATH","/absolute/private/runtime/lib","R_LIBS_USER","/absolute/private/R-library","JAVA_HOME","/absolute/java21"),"roots",List.of("/absolute/R-prefix","/absolute/igv","/absolute/private/runtime","/usr/lib/python3.12","/usr/share/fonts","/etc/fonts"));
        if(!Set.of("evaluate","count-kmers","build-hapmers").contains(mode))throw new IOException("Choose evaluate, count-kmers, build-hapmers or binding");
        List<Object> inputs=mode.equals("evaluate")?List.of(map("role","reads","path","/absolute/reads.meryl","kind","meryl"),map("role","assembly1","path","/absolute/assembly.fasta","kind","fasta")):mode.equals("count-kmers")?List.of(map("role","reads","path","/absolute/reads.fastq.gz","kind","fastx")):List.of(map("role","mat","path","/absolute/mat.meryl","kind","meryl"),map("role","pat","path","/absolute/pat.meryl","kind","meryl"),map("role","child","path","/absolute/child.meryl","kind","meryl"));
        return map("schema_version",1,"edition","1.4.1","mode",mode,"preparation","Replace with actual complete read/assembly/parent preparation, reference, ordinary k-mer domain and scientific parameter choices; paper defaults are not inferred.","runtime","/absolute/completed-runtime-binding","inputs",inputs,"settings",map("k",21,"threads",2,"count_memory_gib",mode.equals("count-kmers")?4:null,"filter_parents",mode.equals("build-hapmers")?true:null,"false_duplications",false),"capacity",map("input_bytes",1073741824,"decoded_bytes",1073741824,"bases",100000000,"records",1000000,"record_bases",10000000,"line_characters",10000000,"distinct_kmers",10000000,"output_bytes",4294967296L,"files",100000,"wall_millis",3600000),"process",map("wall_millis",1800000,"address_space_bytes",8589934592L,"cpu_seconds",1800,"file_bytes",1073741824,"open_files",64000,"workspace_bytes",5368709120L,"file_count",100000,"processes",64,"threads",256,"sampled_rss_bytes",4294967296L,"monitor_millis",100));
    }
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
