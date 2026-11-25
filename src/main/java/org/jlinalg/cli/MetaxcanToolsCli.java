/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.MetaxcanSummary.map;

/** Native summary TWAS using captured PredictDB, GWAS and original-format matrices. */
final class MetaxcanToolsCli {
    private MetaxcanToolsCli(){ }
    static int run(String[] args,PrintStream out,PrintStream err) {
        Path completed=null;
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})){emit(out,help());return out.checkError()?2:0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(map("profile",MetaxcanBundle.PROFILE,"native_modes",List.of("summary","summary-joint"),"source_editions",map("tag-0.7.3","72e8c94edf31a4b261d29a7acf9a659804510e46","commit-0.7.4","b133eb36039a557d1476ead50fc6147734c998de","tag-0.8.1","964f1fdb5bf9585585690e85bb0eca7b67663ddb"),"tag_0_8_1_internal_version","0.7.5","model_variant_key","rsid","model_training_implemented",false,"original_execution_qualified",false,"whole_paper_reproduced",false)));return out.checkError()?2:0;}
            if(args.length==2&&args[0].equals("--example")){if(!Set.of("summary","summary-joint").contains(args[1]))throw new IOException("Select --example summary or summary-joint");emit(out,NextflowJson.json(example(args[1])));return out.checkError()?2:0;}
            if(args.length==2&&args[0].equals("--verify")){MetaxcanBundle.verify(Path.of(args[1]),false,()->{});emit(out,"PASS complete captured bytes, input/output populations, native numeric recomputation and exact producer.\n"+MetaxcanBundle.ACTION);return out.checkError()?2:0;}
            var options=new HashMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--request","--out").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require complete unique --request TYPED.json --out NEW_DIRECTORY; see --help");
            if(!options.keySet().equals(Set.of("--request","--out")))throw new IOException("Require --request TYPED.json --out NEW_DIRECTORY");
            completed=MetaxcanBundle.start(Path.of(options.get("--request")),Path.of(options.get("--out")));emit(out,"Complete native MetaXcan captured contract: "+completed+"\n"+MetaxcanBundle.ACTION);emit(err,"WARNINGS AND REQUIRED ACTION: read "+completed.resolve("WARNINGS.md")+", OUTPUT-AUDIT.json and every input/gene disposition. Check the explicit calibration choice before interpreting results. Full original-tool and whole-paper reproduction remain unfinished.");
            if(out.checkError()||err.checkError()){MetaxcanBundle.failure(completed,"REPORT_RESULT_TO_CALLER",new IOException("Result-report stream failed"));return 2;}return 0;
        }catch(Exception|LinkageError|OutOfMemoryError e){int code=MetaxcanBundle.unavailable(e)?3:2;emit(err,(code==3?"SKIP3 DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 NO COMPLETE RESULT: ")+e+"\n"+(e instanceof OutOfMemoryError?"Required action: provision sufficient -Xmx heap or reduce the declared complete workload; preserve the failed attempt and use a new result path.\n":"")+MetaxcanBundle.ACTION);return err.checkError()?2:code;}
    }
    static Map<String,Object> example(String mode) {
        var data=map("models",List.of(map("name","TissueA","file","/absolute/TissueA.db"),map("name","TissueB","file","/absolute/TissueB.db")),"gwas",map("file","/absolute/gwas.tsv.gz","compression","gzip","separator","tab","columns",map("snp","SNP","effect_allele","EA","non_effect_allele","NEA","zscore","Z","beta","BETA","pvalue",null,"se",null,"odds_ratio",null,"beta_sign",null)),"covariance",map("file","/absolute/covariance.tsv.gz","compression","gzip"));
        if(mode.equals("summary-joint"))data.put("model_product",map("file","/absolute/model-product.tsv.gz","compression","gzip"));
        return map("schema_version",1,"engine","native","edition","tag-0.8.1","mode",mode,"genome_build","REPLACE_WITH_PROVIDER_ASSEMBLY_LABEL","data",data,"settings",map("calibration",map("mode","source-uncalibrated"),"joint_cutoff",mode.equals("summary-joint")?map("kind","condition-number","value",30):null,"regularization",null,"keep_non_rsid",false,"pvalue_policy","source-default-divergent-floor"),"capacity",map("models",64,"genes",100000,"variants",10000000,"samples",1,"input_bytes",50000000000L,"decoded_bytes",200000000000L,"output_bytes",50000000000L,"numeric_bytes",16000000000L,"multiply_adds",1000000000000L,"wall_millis",86400000),"process",null);
    }
    private static String help(){return "metaxcan-tools --profiles\n--example summary|summary-joint\n--request TYPED_REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURED_RESULT\n\nNative Java S-PrediXcan and S-MulTiXcan summary arithmetic, original PredictDB rsid schema, explicit GWAS columns/allele alignment, raw covariance and optional model-product input. Windows/Linux; SQLite JDBC's native library requires a supported platform and permitted executable temporary directory. Analysis performs no download or install. Choose an explicit source edition, calibration policy, matrix/cutoff provider and capacities. An example is a template, not recovered author settings or a memory sizing guarantee. Original individual workflows and model training remain under qualification.\n\nExact producing JAR required for saved numeric recomputation. Read docs/literature/METAXCAN_WORKFLOWS.md for copyable examples and source limitations.\n\n"+MetaxcanBundle.ACTION;}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
