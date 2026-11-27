/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Actual alignment/annotation preparation, original counting mode and complete-population capacities. */
final class VelocytoRequest {
    private VelocytoRequest() { }
    record Spec(Map<?,?> value,String profile,Path runtime,Map<?,?> settings,Map<?,?> capacity,
            long inputBytes,long outputBytes,int files,long wallMillis,NextflowProcess.Limits process) {
        boolean wide(){return settings.get("counter_engine").equals("original-counter-api-uint64");}
        boolean multimap()throws IOException{return bool(settings.get("multimap"));}
        String sample()throws IOException{return text(settings.get("sample_id"));}
    }
    static boolean bool(Object value)throws IOException{require(value instanceof Boolean,"Explicit velocyto Boolean required");return(Boolean)value;}
    static String scalar(Object raw)throws IOException{String value=text(raw);require(!value.isBlank()&&value.length()<=32768&&value.chars().noneMatch(c->c<32&&c!='\n'&&c!='\t'),"Explicit bounded preparation/provenance text required");return value;}
    static Spec parse(Object raw)throws IOException {
        var value=object(raw);keys(value,"schema_version","profile","runtime","preparation","bams","gtf","barcode_file","repeat_mask","settings","capacity","process");integer(value.get("schema_version"),1,1);String profile=text(value.get("profile"));VelocytoRuntime.profile(profile);Path runtime=VelocytoRuntime.absolute(value.get("runtime"));scalar(value.get("preparation"));
        var bams=list(value.get("bams"));require(!bams.isEmpty()&&bams.size()<=10000,"Supply the complete explicit aligned-BAM population");var paths=new HashSet<Path>();for(Object item:bams)require(paths.add(VelocytoRuntime.absolute(item)),"Duplicate alignment input role");require(paths.add(VelocytoRuntime.absolute(value.get("gtf"))),"GTF cannot alias an alignment input role");for(String key:List.of("barcode_file","repeat_mask"))if(value.get(key)!=null)require(paths.add(VelocytoRuntime.absolute(value.get(key))),"Original input roles must be distinct");
        var s=object(value.get("settings"));keys(s,"sample_id","counter_engine","logic","umi_tag","excluded_contigs","allowed_unannotated_contigs","multimap","output_dtype","samtools_threads","samtools_memory_mb","python_hash_seed","verbosity");require(text(s.get("sample_id")).matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}"),"Supply a literal sample ID without path separators, colon, whitespace or traversal");require(Set.of("original-cli-uint16-buffers","original-counter-api-uint64").contains(s.get("counter_engine")),"Explicit original CLI or original Counter API uint64 required; their integer behaviors differ");require(Set.of("Default","Permissive10X").contains(s.get("logic")),"Choose the source-used Default/Permissive10X droplet counting logic explicitly");require(Set.of("UB","MA").contains(s.get("umi_tag")),"Declare prepared UB tags or explicit BD Rhapsody MA-to-UB preparation");
        for(String key:List.of("excluded_contigs","allowed_unannotated_contigs")){var names=new HashSet<String>();for(String item:strings(s.get(key)))require(!item.isEmpty()&&item.length()<=32768&&item.chars().allMatch(c->c>32&&c<127)&&names.add(item),"Explicit unique literal contig names required");}
        bool(s.get("multimap"));require(Set.of("uint16","uint32","uint64").contains(s.get("output_dtype")),"Original output unsigned dtype required");if(s.get("counter_engine").equals("original-counter-api-uint64"))require(s.get("output_dtype").equals("uint64"),"Explicit wide Counter API requires uint64 stored count layers; no truncating storage substitution");integer(s.get("samtools_threads"),1,256);integer(s.get("samtools_memory_mb"),1,1048576);integer(s.get("python_hash_seed"),0,4294967295L);integer(s.get("verbosity"),1,3);
        var c=object(value.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","records","sam_characters","cells","genes","molecule_keys","gtf_rows","line_characters","matrix_entries","sparse_entries","decoded_matrix_bytes");long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000);int files=(int)integer(c.get("files"),128,1000000);integer(c.get("records"),1,1000000000000L);integer(c.get("sam_characters"),1,1000000000000L);integer(c.get("cells"),1,10000000);integer(c.get("genes"),1,10000000);integer(c.get("molecule_keys"),1,1000000000000L);integer(c.get("gtf_rows"),1,1000000000);integer(c.get("line_characters"),256,16777216);integer(c.get("matrix_entries"),1,1000000000000L);integer(c.get("sparse_entries"),1,1000000000000L);integer(c.get("decoded_matrix_bytes"),1,1000000000000L);
        var p=object(value.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");var process=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),128,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),1,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        return new Spec(value,profile,runtime,s,c,input,output,files,wall,process);
    }
}
