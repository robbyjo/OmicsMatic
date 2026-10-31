/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
final class RepeatTestRecipes {
    private RepeatTestRecipes() { }
    static Map<String,Object> nativeRequest() {
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("operation","MASK");m.put("mode","ANNOTATION_REUSE");m.put("profile",RepeatRecipe.NATIVE);m.put("resource_root",".");m.put("assembly","synthetic-build");
        m.put("input_review",new LinkedHashMap<>(Map.of("organism","synthetic DNA","genome_provenance","controlled fixture","repeat_library_provenance","not used for native import","annotation_provenance","synthetic supplied annotations","coordinate_policy","declared source format to zero-based half-open","paper_settings_status","no paper reconstruction")));
        m.put("query",source("query.fa","PLAIN_UTF8"));m.put("annotations",new LinkedHashMap<>(Map.of("path","hits.out","encoding","PLAIN_UTF8","format","OUT_WITH_ID","completion_evidence","EXTERNAL_NOT_CERTIFIED")));
        m.put("staging_policy","EXACT_SOURCE");m.put("mask","N");m.put("selection",new LinkedHashMap<>(Map.of("classes","ALL","class_names",List.of(),"names","ALL","repeat_names",List.of(),"marked_overlap","INCLUDE")));
        m.put("limits",new LinkedHashMap<>(Map.ofEntries(Map.entry("preset","bounded-repeat-v1"),Map.entry("input_bytes",1048576),Map.entry("fasta_characters",1048576),Map.entry("fasta_residues",1000000),Map.entry("fasta_per_sequence",1000000),Map.entry("fasta_sequences",100),Map.entry("fasta_line_characters",1024),Map.entry("fasta_header_characters",512),Map.entry("fasta_retained_bytes",131072),Map.entry("annotation_characters",1048576),Map.entry("annotation_rows",1000),Map.entry("annotation_line_characters",1024),Map.entry("annotation_retained_bytes",4194304),Map.entry("index_retained_bytes",2097152),Map.entry("retained_bytes",16777216),Map.entry("workspace_bytes",2097152),Map.entry("file_count",64))));return m;
    }
    static Map<String,Object> searchRequest(String mode) {
        var m=nativeRequest();m.put("operation","SEARCH");m.put("mode",mode);m.put("profile",RepeatRecipe.HISTORICAL);m.remove("annotations");m.remove("selection");
        m.put("parameters",new LinkedHashMap<>(Map.of("speed","SENSITIVE","parallel_batches",1,"rna","EXCLUDE","low_complexity","EXCLUDE")));m.put("process_limits",new LinkedHashMap<>(Map.of("preset","bounded-process-v1")));
        if(mode.equals("CUSTOM_LIBRARY"))m.put("library",source("library.fa","PLAIN_UTF8"));else{m.put("species","Mus musculus");map(m,"parameters").put("low_complexity","INCLUDE");}return m;
    }
    static Map<String,Object> source(String path,String encoding){return new LinkedHashMap<>(Map.of("path",path,"encoding",encoding));}
    @SuppressWarnings("unchecked") static Map<String,Object> map(Map<String,Object> m,String key){return (Map<String,Object>)m.get(key);}
    static Path write(Path root,Map<String,Object> request)throws IOException{Path path=root.resolve("recipe.json");Files.writeString(path,RegenieRuntime.json(request));return path;}
    static void inputs(Path root)throws IOException{Files.writeString(root.resolve("query.fa"),">q description\nACGTACGT\n>zero\nNNAC\n");Files.writeString(root.resolve("hits.out"),"50 0 0 0 q 2 5 (3) + REP DNA/hAT 1 4 (0) 1\n");Files.writeString(root.resolve("library.fa"),">REP#DNA/hAT\nACGTACGT\n");Files.writeString(root.resolve("masked.fa"),">q description\nANNNNCGT\n>zero\nNNAC\n");}
}
