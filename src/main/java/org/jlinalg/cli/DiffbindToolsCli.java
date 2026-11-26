/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

public final class DiffbindToolsCli {
    private DiffbindToolsCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})) {emit(out,"diffbind-tools --profiles\n--example analysis|binding\n--bind RUNTIME_REQUEST.json --out NEW_BINDING\n--request ANALYSIS_REQUEST.json --out NEW_CAPTURE\n--verify CAPTURE --scratch NEW_DIRECTORY\n\nBAM/peak counting, explicit normalization, additive source-factor designs, DESeq2/edgeR contrasts, complete reports, PCA and heatmap. Each BAM requires an index at BAM_PATH.bai. Linux x86_64 Java and a permitted original runtime are required.\n\n"+DiffbindBundle.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(DiffbindRuntime.profile()));return 0;}
            if(args.length==2&&args[0].equals("--example")){emit(out,NextflowJson.json(example(args[1])));return 0;}
            if(args.length==4&&args[0].equals("--bind")&&args[2].equals("--out")){emit(out,"COMPLETE_DIFFBIND_RUNTIME_BINDING_ONLY "+DiffbindRuntime.bind(Path.of(args[1]).toAbsolutePath().normalize(),Path.of(args[3]),()->{})+"\nNo scientific user analysis was performed. "+DiffbindBundle.ACTION);return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){
                var spec=DiffbindRequest.read(Path.of(args[1]).toAbsolutePath().normalize());Path root=DiffbindBundle.capture(Path.of(args[3]),spec,()->{});
                var audit=NextflowFiles.object(NextflowFiles.json(root.resolve("OUTPUT-AUDIT.json"),16777216));var warnings=NextflowFiles.list(audit.get("source_warnings"));
                for(Object item:warnings){var warning=NextflowFiles.object(item);emit(err,"WARNING DIFFBIND ORIGINAL SOURCE ["+NextflowFiles.text(warning.get("stage"))+"]: "+NextflowFiles.text(warning.get("message")));}
                emit(out,"COMPLETE_DIFFBIND_SCOPED_WORKFLOW "+root+"\nOriginal source warnings: "+warnings.size()+".\n"+DiffbindBundle.ACTION);return 0;
            }
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--scratch")){DiffbindBundle.verify(Path.of(args[1]),Path.of(args[3]),()->{});emit(out,"PASS_COMPLETE_DIFFBIND_BINARY_AND_NUMERICAL_REPLAY");return 0;}
            throw new IllegalArgumentException("Use diffbind-tools --help");
        }catch(DiffbindRuntime.Unavailable|NextflowProcess.Unavailable e){emit(err,"SKIP3 DEPENDENT DIFFBIND ANALYSIS SKIPPED: "+e.getMessage()+diagnostics(args)+"\n"+DiffbindBundle.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE DIFFBIND ANALYSIS: "+e.getMessage()+diagnostics(args)+"\n"+DiffbindBundle.ACTION);return 2;}
    }
    static Map<String,Object> example(String mode)throws IOException {
        if(mode.equals("binding"))return map("schema_version",1,"source","/absolute/unmodified/DiffBind-3.24.0","python","/usr/bin/python3","environment",map("PATH","/absolute/R-prefix/bin:/usr/bin:/bin","LD_LIBRARY_PATH","/absolute/R-prefix/lib","R_LIBS_USER","/absolute/R-library","R_LIBS_SITE","","R_MAKEVARS_USER","/absolute/config/empty","R_ENVIRON_USER","/absolute/config/empty","R_PROFILE_USER","/absolute/config/empty","JAVA_HOME","/absolute/java21"),"roots",List.of("/absolute/R-prefix","/absolute/R-library","/absolute/config","/usr/share/fonts","/etc/fonts"));
        if(!mode.equals("analysis"))throw new IOException("Choose analysis or binding");var samples=new ArrayList<Object>();for(int i=0;i<6;i++){String id=(i<3?"A":"B")+(i%3+1);samples.add(map("id",id,"attributes",map("Tissue",i%2==0?"X":"Y","Condition",i<3?"A":"B","Replicate",i%3+1),"bam_reads","/absolute/"+id+".bam","peaks","/absolute/"+id+".bed","bam_control",null,"control_id",null));}
        return map("schema_version",1,"runtime","/absolute/completed-runtime-binding","preparation","Replace synthetic example metadata and settings with the complete actual experiment, preparation and reference details. No paper formula, engine or defaults are inferred.","samples",samples,
            "settings",map("engine","DESeq2","seed",20261126,"threshold",.1,"use_pvalue",false,"mapq",20,"fragment_size",35,"min_overlap",2,"summits",-1,"count_filter",0,"remove_duplicates",false,"scale_controls",true,"subtract_controls",false,"min_count",0,"normalization","LIB","library_size","FULL","design",List.of("Tissue","Condition"),"contrast_factor","Condition","numerator","B","denominator","A","min_members",3,"test_fold",0),
            "capacity",map("input_bytes",1073741824,"output_bytes",4294967296L,"files",10000,"wall_millis",3600000,"bam_decoded_bytes",8589934592L,"bam_records",10000000,"bam_record_bytes",1048576),
            "process",map("wall_millis",1800000,"address_space_bytes",17179869184L,"cpu_seconds",1800,"file_bytes",1073741824,"open_files",4096,"workspace_bytes",8589934592L,"file_count",10000,"processes",32,"threads",128,"sampled_rss_bytes",8589934592L,"monitor_millis",100));
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    private static String diagnostics(String[] args){if(args.length!=4)return "";try{return "\nAttempt output (when created): "+Path.of(args[3]).toAbsolutePath().normalize();}catch(RuntimeException invalid){return "\nOutput path is invalid; correct it before retrying.";}}
}
