/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.IqCompute.map;

/** Source-built native MaxLFQ entry point; setup and raw mass-spectrometry analysis remain separate. */
public final class IqToolsCli {
    private IqToolsCli() { }
    public static int run(String[] arguments,PrintStream out,PrintStream err) {
        try {
            if(arguments.length==0||Arrays.equals(arguments,new String[]{"--help"})){emit(out,help());return 0;}
            if(Arrays.equals(arguments,new String[]{"--profiles"})){emit(out,NextflowJson.json(map("schema_version",1,"engine","native","edition","iq-2.3.0","mode","fast-maxlfq","units","already-normalized-log2","analysis_time_installation",false,"whole_package_qualified",false)));return 0;}
            if(Arrays.equals(arguments,new String[]{"--example"})){emit(out,NextflowJson.json(example()));return 0;}
            if(arguments.length==2&&arguments[0].equals("--verify")){IqBundle.verify(Path.of(arguments[1]),false,()->{});emit(out,"PASS_COMPLETE_NATIVE_IQ_RECOMPUTATION");return 0;}
            if(arguments.length==4&&arguments[0].equals("--request")&&arguments[2].equals("--out")){var spec=IqRequest.read(Path.of(arguments[1]).toAbsolutePath().normalize(),true);var root=IqBundle.capture(Path.of(arguments[3]),spec,()->{});emit(out,"COMPLETE_NATIVE_IQ_CAPTURE "+root+"\n"+IqBundle.ACTION);return 0;}
            throw new IllegalArgumentException("Use iq-tools --help, --profiles, --example, --request JSON --out NEW, or --verify CAPTURE");
        }catch(IqBundle.Unavailable error){emit(err,"SKIP3 DEPENDENT IQ ANALYSIS SKIPPED: "+error.getMessage()+"\n"+IqBundle.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError error){emit(err,"FAIL2 NO COMPLETE IQ RESULT: "+String.valueOf(error.getMessage()).replace("Nextflow","iq").replace("MetaXcan","iq")+"\n"+IqBundle.ACTION);return 2;}
    }
    static Map<String,Object> example(){return map("schema_version",1,"engine","native","edition","iq-2.3.0","mode","fast-maxlfq","units","already-normalized-log2","preparation","Replace with your actual ion filtering and log2 normalization provenance; no author settings are inferred","input",map("file","/absolute/prepared-ions.tsv","compression","none","separator","tab","columns",map("protein","protein","sample","sample","ion","ion","quant","normalized_log2")),"capacity",map("rows",1000000,"proteins",100000,"samples",10000,"ions_per_protein",10000,"cells_per_protein",10000000,"pairs_per_protein",100000000,"numeric_bytes",2147483648L,"work",10000000000L,"iterations",100000,"input_bytes",536870912,"decoded_bytes",1073741824,"output_bytes",1073741824,"wall_millis",3600000),"process",null);}
    static String help(){return "iq-tools --profiles\n--example\n--request TYPED_REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURE\n\nNative Java iq2.3.0 fast_MaxLFQ protein aggregation from already normalized log2 ion quantities. Complete literal input capture, duplicate-cell linear sums, source connected-component annotations, convergence checks and exact-producer saved numerical replay. Windows/Linux; no R installation or network during analysis. This source-built command does not add itself to older release downloads. Examples are templates and not recovered author settings. See docs/literature/IQ_MAXLFQ_WORKFLOWS.md.\n\n"+IqBundle.ACTION;}
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
