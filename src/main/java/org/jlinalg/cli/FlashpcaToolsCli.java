/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Original edition-specific genotype PCA and complete native numerical audits. */
public final class FlashpcaToolsCli {
    private FlashpcaToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {
                out.println("flashpca-tools --profiles\n--example binding|pca|legacy\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_REPLAY\n\nOriginal unchanged-source FlashPCA1.2.6/2.0 additive genotype PCA from the full supplied PLINK BED/BIM/FAM cohort. Native Java complete genotype, source scaling, eigenvector residual, PC, SNP loading and trace/PVE audits. No surrogate PCA solver or ancestry assignment. Original execution/replay requires Linux x86_64 Java; Windows can use WSL. The native numerical audit also runs on Windows. No original paper patch or whole-paper reproduction is inferred. See docs/flashpca-tools.md.\n\n"+FlashpcaRuntime.ACTION);return 0;
            }
            if(Arrays.equals(args,new String[]{"--profiles"})){out.println(NextflowJson.json(FlashpcaRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--example")){out.println(NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){Path path=FlashpcaRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});out.println("COMPLETE_FLASHPCA_RUNTIME_BINDING_ONLY "+path);err.println(FlashpcaRuntime.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){Path path=FlashpcaBundle.capture(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{});out.println("COMPLETE_SCOPED_ORIGINAL_FLASHPCA_PCA "+path);err.println(FlashpcaRuntime.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){FlashpcaBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});out.println("PASS_COMPLETE_FRESH_ORIGINAL_FLASHPCA_REPLAY");err.println(FlashpcaRuntime.ACTION);return 0;}
            throw new IllegalArgumentException("Use flashpca-tools --help");
        }catch(FlashpcaRuntime.Unavailable|NextflowProcess.Unavailable e){err.println("SKIP3 DEPENDENT FLASHPCA ANALYSIS SKIPPED: "+e.getMessage()+"\n"+FlashpcaRuntime.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){err.println("FAIL2 NO COMPLETE FLASHPCA PCA: "+e.getMessage()+"\n"+FlashpcaRuntime.ACTION);return 2;}
    }
    static Object example(String mode)throws IOException {
        String id="FlashPCA-2.0-b45400ea";
        if(mode.equals("binding"))return map("schema_version",1,"profile",id,"source","/absolute/pristine-tag-source-view","python","/usr/bin/python3","binary","/absolute/tested-original-source-build/flashpca","library_path","/absolute/private/boost-libraries","roots",List.of("/absolute/tested-original-source-build"));
        if(!Set.of("pca","legacy").contains(mode))throw new IOException("Choose binding, pca or legacy");boolean legacy=mode.equals("legacy");
        return map("schema_version",1,"profile",legacy?"FlashPCA-1.2.6-35a6bbee":id,"runtime","/absolute/completed-edition-specific-binding","bed_prefix","/absolute/prepared-genotypes/data","population","Declare the exact complete sample and variant population, genome build and relatedness selection.","preparation","Describe actual sample/SNP QC, MAF/HWE/missingness/LD/region filters and any random rare-variant sampling. No filter is inferred or applied here.","execution_policy","ALLOW",
            "settings",map("components",20,"seed",42,"iterations",2000,"tolerance",1e-9,"standardization",legacy?"BINOM":"BINOM2","divisor",legacy?"N1":"P","block_snps",legacy?null:128,"legacy_extra_dimensions",legacy?0:null,"legacy_force_transpose",legacy?false:null,"legacy_method",legacy?"EIGEN":null,"legacy_memory",legacy?"HIGH":null,"audit_tolerance",legacy?1e-4:1e-5),
            "capacity",map("input_bytes",1073741824,"output_bytes",1073741824,"files",1024,"wall_millis",1800000,"samples",100000,"variants",1000000,"metadata_bytes",268435456,"retained_bytes",1073741824,"products",2000000000L,"line_bytes",65536),
            "process",map("wall_millis",900000,"address_space_bytes",8589934592L,"cpu_seconds",1800,"file_bytes",1073741824,"open_files",1024,"workspace_bytes",2147483648L,"file_count",1024,"processes",16,"threads",64,"sampled_rss_bytes",6442450944L,"monitor_millis",100));
    }
}
