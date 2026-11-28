/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed complete-input original SpliceAI requests; model, reference and all options are explicit. */
final class SpliceAiToolsRequest {
    private SpliceAiToolsRequest(){ }
    static final String PROFILE="original-SpliceAI1.3-CPU-linux64";
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","backend","out","annotated_vcf","original_vcf","profile","input_vcf","runtime","reference","annotation","analyses","threads","timeout_seconds","maximum_input_bytes","maximum_resource_bytes","maximum_output_bytes","maximum_output_files","maximum_decoded_characters","maximum_line_characters","maximum_variants","maximum_annotations"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version1 and mode RUN");var r=new LinkedHashMap<String,Object>(raw);text(raw,"out");String backend=choice(raw,"backend","",Set.of("NATIVE","ORIGINAL"));var used=new HashSet<>(Set.of("schema_version","mode","backend","out","maximum_input_bytes","maximum_output_bytes","maximum_output_files","maximum_decoded_characters","maximum_line_characters","maximum_variants","maximum_annotations"));
        if(backend.equals("NATIVE")){text(raw,"annotated_vcf");used.addAll(Set.of("annotated_vcf","original_vcf"));if(raw.containsKey("original_vcf"))text(raw,"original_vcf");}
        else{
            used.addAll(Set.of("profile","input_vcf","runtime","reference","annotation","analyses","threads","timeout_seconds","maximum_resource_bytes"));if(!text(raw,"profile").equals(PROFILE))throw new SpliceAiToolsRuntime.Unavailable("Exact requested original SpliceAI profile unavailable; retain skip or supply the separately qualified "+PROFILE);text(raw,"input_vcf");text(raw,"runtime");
            var reference=object(raw.get("reference"),"reference");keys(reference,Set.of("fasta","fai","fasta_sha256","fai_sha256","assembly"));for(String key:List.of("fasta","fai","assembly"))text(reference,key);for(String key:List.of("fasta_sha256","fai_sha256"))if(!text(reference,key).matches("[0-9a-f]{64}"))throw new IOException("Require explicit full reference/index SHA256");r.put("reference",reference);
            var annotation=object(raw.get("annotation"),"annotation");String kind=choice(annotation,"kind","",Set.of("bundled-GENCODE24lift37","bundled-GENCODE24","provided"));if(kind.equals("provided")){keys(annotation,Set.of("kind","file","id"));text(annotation,"file");text(annotation,"id");}else{keys(annotation,Set.of("kind"));String expected=kind.equals("bundled-GENCODE24lift37")?"GRCh37":"GRCh38";if(!text(reference,"assembly").equals(expected))throw new IOException("Bundled annotation is "+expected+"; no assembly or GENCODE substitution");}r.put("annotation",annotation);
            var analyses=new ArrayList<Map<String,Object>>();Set<String> ids=new HashSet<>();for(Object item:list(raw.get("analyses"),"analyses")){var a=object(item,"analysis");keys(a,Set.of("id","distance","mask"));String id=text(a,"id");if(!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")||!ids.add(id))throw new IOException("Require unique portable analysis IDs");if(!a.containsKey("distance")||!a.containsKey("mask"))throw new IOException("Every complete analysis requires explicit original distance and mask");long distance=integer(a,"distance",0,0,100000);if(distance>4999)throw new SpliceAiToolsRuntime.Unavailable("Original1.3 accepts distance0..4999; the requested larger-window analysis requires a separately qualified edition and remains skipped, without clamping");analyses.add(Map.of("id",id,"distance",distance,"mask",integer(a,"mask",0,0,1)));}
            if(analyses.isEmpty()||analyses.size()>16)throw new IOException("Require1..16 explicit complete analyses");r.put("analyses",analyses);r.put("threads",integer(raw,"threads",4,1,64));r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,604800));r.put("maximum_resource_bytes",integer(raw,"maximum_resource_bytes",32000000000L,1,2000000000000L));
        }
        for(String key:raw.keySet())if(!used.contains(key))throw new IOException("Unused SpliceAI option for selected backend: "+key);
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",8589934592L,1,137438953472L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",17179869184L,1,2199023255552L));r.put("maximum_output_files",integer(raw,"maximum_output_files",100000,1,1000000));r.put("maximum_decoded_characters",integer(raw,"maximum_decoded_characters",34359738368L,1,2199023255552L));r.put("maximum_line_characters",integer(raw,"maximum_line_characters",8388608,256,33554432));r.put("maximum_variants",integer(raw,"maximum_variants",10000000,1,100000000));r.put("maximum_annotations",integer(raw,"maximum_annotations",100000000,1,1000000000));return r;
    }
    static long number(Map<String,Object> r,String key){return((Number)r.get(key)).longValue();}
}
