/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Explicit original stages, thresholds, axes and input bindings; no executable scripts/formulas. */
final class LeafCutterWorkflowRequest {
    private LeafCutterWorkflowRequest() { }
    record Input(String id,Path source) { }
    record Request(String edition,Path runtime,Path out,List<Map<String,Object>> analyses,List<Input> inputs,
                   long inputBytes,long outputBytes,int maximumFiles,int timeout,long auditOperations) {
        Map<String,Object> worker(Map<String,Path> captured) throws IOException {
            var result=new ArrayList<Map<String,Object>>();
            for(var original:analyses){var c=new LinkedHashMap<String,Object>(original);String id=text(c.get("id"));
                if(c.containsKey("samples")){var samples=new ArrayList<Object>();int i=0;for(Object raw:list(c.get("samples"))){var sample=new LinkedHashMap<String,Object>(object(raw));sample.put("junctions",captured.get(id+"-junction-"+i++).toString());samples.add(sample);}c.put("samples",samples);}
                for(String key:List.of("counts","counts_ratio"))if(c.containsKey(key))c.put(key,captured.get(id+"-"+key).toString());
                if(c.containsKey("differential")){var d=new LinkedHashMap<String,Object>(object(c.get("differential")));d.put("groups",captured.get(id+"-groups").toString());if(d.containsKey("exons"))d.put("exons",captured.get(id+"-exons").toString());c.put("differential",d);}result.add(c);
            }
            return Map.of("schema_version",1,"edition",edition,"analyses",result);
        }
    }
    static Request parse(Map<String,Object> root,Path parent)throws IOException {
        allowed(root,"schema_version","mode","edition","runtime","out","analyses","maximum_input_bytes","maximum_output_bytes","maximum_files","timeout_seconds","maximum_audit_operations");integer(root.get("schema_version"),1,1);require(text(root.get("mode")).equals("RUN"),"Use mode RUN; setup/export is a separate explicit invocation");String edition=label(root.get("edition"));var analyses=new ArrayList<Map<String,Object>>();var inputs=new ArrayList<Input>();var ids=new HashSet<String>();
        for(Object raw:list(root.get("analyses"))){var c=object(raw);allowed(c,"id","kind","samples","cluster","counts_ratio","counts","phenotype","differential");String id=id(c.get("id")),kind=text(c.get("kind"));require(ids.add(id.toLowerCase(Locale.ROOT))&&analyses.size()<32,"Require 1..32 unique case IDs, including case folding");require(Set.of("cluster","phenotype","differential","pipeline").contains(kind),"Unknown LeafCutter stage kind");
            boolean cluster=kind.equals("cluster")||kind.equals("pipeline"),phenotype=kind.equals("phenotype")||kind.equals("pipeline")&&c.containsKey("phenotype"),differential=kind.equals("differential")||kind.equals("pipeline")&&c.containsKey("differential");require(!kind.equals("pipeline")||phenotype||differential,"Pipeline must request phenotype and/or differential stages");require(cluster==c.containsKey("samples")&&cluster==c.containsKey("cluster")&&phenotype==c.containsKey("phenotype")&&differential==c.containsKey("differential"),"Missing or unused stage fields are rejected");require(kind.equals("phenotype")==c.containsKey("counts_ratio")&&kind.equals("differential")==c.containsKey("counts"),"Declare exact standalone counts input; pipeline inputs come from its own clustering stage");
            if(cluster){var names=new HashSet<String>();int index=0;for(Object sampleRaw:list(c.get("samples"))){var sample=object(sampleRaw);allowed(sample,"sample_id","junctions");String name=id(sample.get("sample_id"));require(!name.contains(".junc")&&names.add(name.toLowerCase(Locale.ROOT))&&index<100000,"Require unique portable unchanged sample IDs; .junc filename collapsing is outside this profile");inputs.add(new Input(id+"-junction-"+index++,resolve(parent,label(sample.get("junctions")))));}require(index>0,"Complete sample family is empty");var o=object(c.get("cluster"));allowed(o,"maximum_intron_length","minimum_reads","minimum_ratio","check_chromosomes");integer(o.get("maximum_intron_length"),1,Long.MAX_VALUE);integer(o.get("minimum_reads"),0,Long.MAX_VALUE);finite(o.get("minimum_ratio"),0,1);bool(o.get("check_chromosomes"));}
            if(kind.equals("phenotype"))inputs.add(new Input(id+"-counts_ratio",resolve(parent,label(c.get("counts_ratio")))));
            if(kind.equals("differential"))inputs.add(new Input(id+"-counts",resolve(parent,label(c.get("counts")))));
            if(phenotype){var o=object(c.get("phenotype"));allowed(o,"pcs","seed","blacklist");integer(o.get("pcs"),0,1000);integer(o.get("seed"),1,2147483646);var seen=new HashSet<String>();for(Object value:list(o.get("blacklist")))require(seen.add(token(value)),"Literal blacklist values must be unique; never normalize chr prefixes");}
            if(differential){var o=object(c.get("differential"));allowed(o,"groups","exons","maximum_cluster_size","minimum_samples_per_intron","minimum_samples_per_group","minimum_coverage","cluster_timeout_seconds","threads","initialization","seed","sample_selection_policy");inputs.add(new Input(id+"-groups",resolve(parent,label(o.get("groups")))));if(o.containsKey("exons"))inputs.add(new Input(id+"-exons",resolve(parent,label(o.get("exons")))));integer(o.get("maximum_cluster_size"),2,1000000);integer(o.get("minimum_samples_per_intron"),1,1000000);integer(o.get("minimum_samples_per_group"),1,1000000);integer(o.get("minimum_coverage"),0,9007199254740992L);integer(o.get("cluster_timeout_seconds"),1,86400);integer(o.get("threads"),1,64);require(Set.of("smart","random").contains(text(o.get("initialization"))),"Choose original smart or random initialization");integer(o.get("seed"),1,2147483646);require(text(o.get("sample_selection_policy")).equals("groups-file-order"),"Explicitly acknowledge original group-file sample selection/order; omitted count samples remain in the input ledger");}
            analyses.add(c);
        }
        require(!analyses.isEmpty(),"Require at least one complete stage/pipeline");return new Request(edition,resolve(parent,label(root.get("runtime"))),resolve(parent,label(root.get("out"))),List.copyOf(analyses),List.copyOf(inputs),number(root,"maximum_input_bytes",67108864,1,1073741824),number(root,"maximum_output_bytes",536870912,1024,1099511627776L),Math.toIntExact(number(root,"maximum_files",100000,1,1000000)),Math.toIntExact(number(root,"timeout_seconds",1200,1,86400)),number(root,"maximum_audit_operations",100000000,1,10000000000L));
    }
    static String id(Object value)throws IOException {String s=text(value);require(s.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")&&!Set.of(".","..").contains(s),"Require a portable unchanged case/sample ID");return s;}
    static String token(Object value)throws IOException {String s=text(value);require(!s.isEmpty()&&s.codePoints().noneMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c<32||c==127||c=='#'||c=='\''||c=='\"'||c=='\\'),"Empty/whitespace/comment/quote/control data token is outside the original valid-input profile; no repair");return s;}
    private static void allowed(Map<String,Object> value,String... keys)throws IOException {var names=Set.of(keys);for(String name:value.keySet())require(names.contains(name),"Unknown LeafCutter workflow field: "+name);}
}
