/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.SpectralAnnotationCompute.map;

/** Native explicit fragment annotation preparation; downstream MS analysis remains separate. */
public final class SpectralAnnotationCli {
    private SpectralAnnotationCli() { }
    public static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==0||Arrays.equals(args,new String[]{"--help"})){emit(out,"spectral-library-annotations --profiles\n--example\n--request TYPED_REQUEST.json --out NEW_DIRECTORY\n--verify COMPLETE_CAPTURE\n\nNative literal bN^Z/yN^Z annotation expansion and complete source-cell replay. No raw-MS analysis is admitted. See docs/literature/SPECTRAL_LIBRARY_ANNOTATIONS.md.\n\n"+SpectralAnnotationBundle.ACTION);return 0;}
            if(Arrays.equals(args,new String[]{"--profiles"})){emit(out,NextflowJson.json(map("schema_version",1,"engine","native","annotation_syntax","by-ordinal-caret-charge-v1","appended_columns",SpectralAnnotationCompute.ADDED,"original_runtime_executed",false,"raw_MS_analysis_admitted",false)));return 0;}
            if(Arrays.equals(args,new String[]{"--example"})){emit(out,NextflowJson.json(example()));return 0;}
            if(args.length==2&&args[0].equals("--verify")){SpectralAnnotationBundle.verify(Path.of(args[1]),false,()->{});emit(out,"PASS_COMPLETE_SPECTRAL_ANNOTATION_SOURCE_CELL_REPLAY");return 0;}
            if(args.length==4&&args[0].equals("--request")&&args[2].equals("--out")){var s=SpectralAnnotationRequest.read(Path.of(args[1]).toAbsolutePath().normalize(),true);var root=SpectralAnnotationBundle.capture(Path.of(args[3]),s,()->{});emit(out,"COMPLETE_NATIVE_SPECTRAL_ANNOTATION_PREPARATION "+root+"\n"+SpectralAnnotationBundle.ACTION);return 0;}
            throw new IllegalArgumentException("Use spectral-library-annotations --help, --profiles, --example, --request JSON --out NEW, or --verify CAPTURE");
        }catch(SpectralAnnotationBundle.Unavailable e){emit(err,"SKIP3 DEPENDENT SPECTRAL PREPARATION SKIPPED: "+e.getMessage()+"\n"+SpectralAnnotationBundle.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError e){emit(err,"FAIL2 NO COMPLETE SPECTRAL PREPARATION: "+String.valueOf(e.getMessage()).replace("Nextflow","spectral preparation")+"\n"+SpectralAnnotationBundle.ACTION);return 2;}
    }
    static Map<String,Object> example(){return map("schema_version",1,"engine","native","mode","expand-explicit-fragment-annotations","annotation_syntax","by-ordinal-caret-charge-v1","preparation","Replace with actual source-library and annotation provenance; no author settings are inferred","input",map("file","/absolute/library.tsv","annotation_column","Annotation","format","literal-utf8-tsv"),"capacity",map("rows",2000000,"input_bytes",536870912,"output_bytes",1073741824,"wall_millis",3600000,"fragment_charge",16),"process",null);}
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
