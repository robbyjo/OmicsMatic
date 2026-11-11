/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Typed original CellPhoneDB methods, source inputs and explicit scientific choices. */
final class CellPhoneDbWorkflowRequest {
    private CellPhoneDbWorkflowRequest() { }
    record Input(String id,Path source,String format) { }
    record Request(String edition,Path runtime,Path out,List<Map<String,Object>> analyses,List<Input> inputs,
                   long inputBytes,long outputBytes,int maximumFiles,int timeout,long auditOperations,
                   int maximumCells,int maximumGenes,long maximumMatrixCells,long pythonHashSeed) {
        Map<String,Object> worker(Map<String,Path> captured)throws IOException {
            var result=new ArrayList<Map<String,Object>>();
            for(var original:analyses){var analysis=new LinkedHashMap<>(original);String id=text(analysis.get("id"));
                for(String name:List.of("counts","metadata","database","microenvironments","degs","active_tfs"))if(analysis.containsKey(name)){var value=new LinkedHashMap<>(object(analysis.get(name)));value.put("path",captured.get(id+"-"+name).toString());analysis.put(name,value);}result.add(analysis);
            }
            return Map.of("schema_version",1,"edition",edition,"analyses",result,"maximum_cells",maximumCells,"maximum_genes",maximumGenes,"maximum_matrix_cells",maximumMatrixCells,"python_hash_seed",pythonHashSeed);
        }
    }
    static Request parse(Map<String,Object> root,Path parent)throws IOException {
        allowed(root,"schema_version","mode","edition","runtime","out","analyses","maximum_input_bytes","maximum_output_bytes","maximum_files","timeout_seconds","maximum_audit_operations","maximum_cells","maximum_genes","maximum_matrix_cells","python_hash_seed");integer(root.get("schema_version"),1,1);require(text(root.get("mode")).equals("RUN"),"RUN is analysis only; runtime installation/export is a separate explicit invocation");String edition=label(root.get("edition"));var analyses=new ArrayList<Map<String,Object>>();var inputs=new ArrayList<Input>();var ids=new HashSet<String>();
        for(Object raw:list(root.get("analyses"))){var analysis=object(raw);allowed(analysis,"id","method","counts","counts_data","metadata","database","microenvironments","degs","active_tfs","threshold","separator","result_precision","score_interactions","threads","global_seed","statistical");String id=id(analysis.get("id")),method=text(analysis.get("method"));require(ids.add(id.toLowerCase(Locale.ROOT))&&analyses.size()<32,"Require 1..32 unique portable analysis IDs, including case folding");require(Set.of("simple","statistical","degs").contains(method),"Choose original simple, statistical or degs method");require(Set.of("ensembl","gene_name","hgnc_symbol").contains(text(analysis.get("counts_data"))),"Declare the original database identifier field explicitly");
            input(analysis,"counts",id,parent,inputs,Set.of("tsv","csv","h5ad"));input(analysis,"metadata",id,parent,inputs,Set.of("tsv","csv"));var database=object(analysis.get("database"));allowed(database,"path","edition","source","expected_sha256");label(database.get("edition"));label(database.get("source"));if(database.containsKey("expected_sha256"))require(text(database.get("expected_sha256")).matches("[0-9a-f]{64}"),"Expected database SHA256 must be lowercase hexadecimal");inputs.add(new Input(id+"-database",resolve(parent,label(database.get("path"))),"zip"));
            require(method.equals("degs")==analysis.containsKey("degs"),"Only DEG mode requires the supplied DEG file; differential expression is not fitted by this adapter");require(method.equals("statistical")==analysis.containsKey("statistical"),"Statistical options must be declared exactly for statistical mode");require(!method.equals("simple")||!analysis.containsKey("active_tfs"),"Original simple mode has no active-TF input");for(String name:List.of("microenvironments","degs","active_tfs"))if(analysis.containsKey(name))input(analysis,name,id,parent,inputs,Set.of("tsv","csv"));
            finite(analysis.get("threshold"),0,1);String separator=data(analysis.get("separator"));require(separator.codePointCount(0,separator.length())==1&&"|~;:@".contains(separator),"Choose one declared supported pair separator: | ~ ; : @");integer(analysis.get("result_precision"),-8,12);bool(analysis.get("score_interactions"));integer(analysis.get("threads"),1,64);integer(analysis.get("global_seed"),0,4294967295L);
            if(method.equals("statistical")){var options=object(analysis.get("statistical"));allowed(options,"iterations","debug_seed","pvalue","subsampling");integer(options.get("iterations"),1,1000000);integer(options.get("debug_seed"),-1,4294967295L);finite(options.get("pvalue"),0,1);var sampling=object(options.get("subsampling"));allowed(sampling,"enabled","log","pcs","cells","failure_policy");bool(sampling.get("enabled"));bool(sampling.get("log"));integer(sampling.get("pcs"),1,100000);require(sampling.containsKey("cells"),"Declare original subsampling cells explicitly, including null for the source default");if(sampling.get("cells")!=null)integer(sampling.get("cells"),1,10000000);require(Set.of("fail","retain-original-full-counts").contains(text(sampling.get("failure_policy"))),"Declare whether the source's failed-subsampling/full-count fallback is forbidden or explicitly retained");}
            analyses.add(Collections.unmodifiableMap(new LinkedHashMap<>(analysis)));
        }
        require(!analyses.isEmpty(),"Require at least one complete analysis");return new Request(edition,resolve(parent,label(root.get("runtime"))),resolve(parent,label(root.get("out"))),List.copyOf(analyses),List.copyOf(inputs),number(root,"maximum_input_bytes",536870912,1,17179869184L),number(root,"maximum_output_bytes",2147483648L,1024,1099511627776L),Math.toIntExact(number(root,"maximum_files",100000,1,1000000)),Math.toIntExact(number(root,"timeout_seconds",1200,1,86400)),number(root,"maximum_audit_operations",1000000000L,1,10000000000000L),Math.toIntExact(number(root,"maximum_cells",100000,1,10000000)),Math.toIntExact(number(root,"maximum_genes",100000,1,10000000)),number(root,"maximum_matrix_cells",50000000,1,1000000000),number(root,"python_hash_seed",0,0,4294967295L));
    }
    private static void input(Map<String,Object> analysis,String name,String id,Path parent,List<Input> inputs,Set<String> formats)throws IOException {var value=object(analysis.get(name));allowed(value,"path","format");String format=text(value.get("format"));require(formats.contains(format),"Unsupported declared "+name+" format; no suffix guessing or pickle execution");inputs.add(new Input(id+"-"+name,resolve(parent,label(value.get("path"))),format));}
    static String id(Object raw)throws IOException {String value=text(raw);require(value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")&&!Set.of(".","..").contains(value),"Require a portable unchanged analysis ID");return value;}
    static String data(Object raw)throws IOException {String value=text(raw);require(!value.isEmpty()&&value.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Empty/control/unpaired-surrogate data label; no normalization");return value;}
    private static void allowed(Map<String,Object> value,String... keys)throws IOException {var names=Set.of(keys);for(String name:value.keySet())require(names.contains(name),"Unknown CellPhoneDB workflow field: "+name);}
}
