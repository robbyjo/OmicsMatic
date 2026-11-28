/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal VEP requests; resource editions, transcript modes and applied options are explicit. */
final class VepToolsRequest {
    private VepToolsRequest() { }
    static final String PROFILE="original-VEP105.0-API105-linux64";
    static final List<String> BOOLEANS=List.of("canonical","mane","biotype","symbol","protein","numbers","ccds","domains","hgvs","hgvsg","variant_class","check_existing","af","af_1kg","af_gnomad","max_af","regulatory","gencode_basic","tsl","appris","uniprot","minimal","no_intergenic","coding_only","total_length","check_ref");
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","backend","out","annotated_vcf","original_vcf","source_summary_mode","profile","input_vcf","runtime","reference","analyses","custom_annotations","plugins","threads","buffer_size","perl_hash_seed","timeout_seconds","maximum_input_bytes","maximum_resource_bytes","maximum_output_bytes","maximum_output_files","maximum_decoded_characters","maximum_line_characters","maximum_variants","maximum_annotations"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version1 and mode RUN");
        var r=new LinkedHashMap<String,Object>(raw);text(raw,"out");String backend=choice(raw,"backend","",Set.of("NATIVE","ORIGINAL"));
        var used=new HashSet<>(Set.of("schema_version","mode","backend","out","maximum_input_bytes","maximum_output_bytes","maximum_output_files","maximum_decoded_characters","maximum_line_characters","maximum_variants","maximum_annotations"));
        if(backend.equals("NATIVE")){text(raw,"annotated_vcf");used.addAll(Set.of("annotated_vcf","original_vcf","source_summary_mode"));if(raw.containsKey("original_vcf"))text(raw,"original_vcf");r.put("source_summary_mode",bool(raw,"source_summary_mode",false));}
        else {
            used.addAll(Set.of("profile","input_vcf","runtime","reference","analyses","custom_annotations","plugins","threads","buffer_size","perl_hash_seed","timeout_seconds","maximum_resource_bytes"));
            text(raw,"profile");if(!PROFILE.equals(raw.get("profile")))throw new VepToolsRuntime.Unavailable("Exact original profile unavailable; use the separately qualified "+PROFILE+" or retain skip");text(raw,"input_vcf");
            if(raw.containsKey("plugins")&&!list(raw.get("plugins"),"plugins").isEmpty())throw new VepToolsRuntime.Unavailable("This core VEP profile does not execute arbitrary plugins. Use a separately qualified exact plugin workflow and prerequisites; plugins are never silently omitted");
            var runtime=object(raw.get("runtime"),"runtime");keys(runtime,Set.of("environment","bioperl","helpers"));for(String key:List.of("environment","bioperl","helpers"))text(runtime,key);r.put("runtime",runtime);
            var reference=object(raw.get("reference"),"reference");String kind=choice(reference,"kind","",Set.of("human-cache105-GRCh38-ensembl","gtf","gff"));
            if(kind.equals("human-cache105-GRCh38-ensembl")){keys(reference,Set.of("kind","bundle","trust"));text(reference,"bundle");if(!text(reference,"trust").equals("verified-original-ensembl-resource-bytes"))throw new IOException("Original cache deserialization requires the exact byte-verified original Ensembl resource profile");}
            else {keys(reference,Set.of("kind","annotation","index_mode","fasta","fai","species","assembly"));for(String key:List.of("annotation","fasta","fai","species","assembly"))text(reference,key);if(!text(reference,"index_mode").equals("generate-from-captured-BGZF"))throw new IOException("Explicit index_mode generate-from-captured-BGZF required; original indices are not overwritten or assumed to match");for(String key:List.of("species","assembly"))if(!text(reference,key).matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}"))throw new IOException("Require explicit portable species/assembly labels; no aliasing inferred");}
            r.put("reference",reference);
            var analyses=new ArrayList<Map<String,Object>>();Set<String> ids=new HashSet<>();for(Object item:list(raw.get("analyses"),"analyses")){
                var a=object(item,"analysis");keys(a,Set.of("id","options"));String id=text(a,"id");if(!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")||!ids.add(id))throw new IOException("Require unique portable analysis IDs");var options=options(object(a.get("options"),"options"),kind);analyses.add(Map.of("id",id,"options",options));
            }
            if(analyses.isEmpty()||analyses.size()>16)throw new IOException("Require1..16 explicit complete analyses");r.put("analyses",analyses);
            var custom=new ArrayList<Map<String,Object>>();Set<String> names=new HashSet<>();for(Object item:list(raw.getOrDefault("custom_annotations",List.of()),"custom_annotations")){
                var c=object(item,"custom annotation");keys(c,Set.of("name","file","index_mode","format","type","coordinates","fields"));String name=text(c,"name");if(!name.matches("[A-Za-z][A-Za-z0-9_]{0,63}")||!names.add(name))throw new IOException("Require unique portable custom annotation names");text(c,"file");String format=choice(c,"format","",Set.of("vcf","bed","bigwig"));var itemCopy=new LinkedHashMap<String,Object>(c);itemCopy.put("type",choice(c,"type","exact",Set.of("exact","overlap")));itemCopy.put("coordinates",bool(c,"coordinates",false));
                if(format.equals("bigwig")){if(c.containsKey("index_mode"))throw new IOException("BigWig does not use a tabix index");}else if(!text(c,"index_mode").equals("generate-from-captured-BGZF"))throw new IOException("Custom BGZF annotations require explicit index generation from captured exact data");
                var fields=stringsOrEmpty(c.getOrDefault("fields",List.of()));if(!format.equals("vcf")&&!fields.isEmpty())throw new IOException("Custom INFO fields require VCF format");for(String field:fields)if(!field.matches("[A-Za-z_][A-Za-z0-9_.]*"))throw new IOException("Require literal portable custom VCF INFO field names");itemCopy.put("fields",fields);custom.add(itemCopy);
            }
            if(custom.size()>32)throw new IOException("Custom annotation capacity exceeded");r.put("custom_annotations",custom);r.put("threads",integer(raw,"threads",1,1,64));r.put("buffer_size",integer(raw,"buffer_size",5000,1,100000));
            String seed=raw.containsKey("perl_hash_seed")?text(raw,"perl_hash_seed"):"0";if(!seed.matches("[0-9a-fA-F]{1,32}"))throw new IOException("Require explicit hexadecimal Perl hash seed");r.put("perl_hash_seed",seed);r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,604800));r.put("maximum_resource_bytes",integer(raw,"maximum_resource_bytes",256000000000L,1,2000000000000L));
        }
        for(String key:raw.keySet())if(!used.contains(key))throw new IOException("Unused option for selected VEP backend: "+key);
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",8589934592L,1,137438953472L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",17179869184L,1,2199023255552L));r.put("maximum_output_files",integer(raw,"maximum_output_files",100000,1,1000000));r.put("maximum_decoded_characters",integer(raw,"maximum_decoded_characters",34359738368L,1,2199023255552L));r.put("maximum_line_characters",integer(raw,"maximum_line_characters",8388608,256,33554432));r.put("maximum_variants",integer(raw,"maximum_variants",10000000,1,100000000));r.put("maximum_annotations",integer(raw,"maximum_annotations",100000000,1,1000000000));return r;
    }
    static Map<String,Object> options(Map<String,Object> raw,String referenceKind)throws IOException {
        Set<String> allowed=new HashSet<>(BOOLEANS);allowed.addAll(Set.of("transcript_mode","pick_order","distance","sift","polyphen","max_sv_size"));keys(raw,allowed);var result=new LinkedHashMap<String,Object>();for(String key:BOOLEANS)if(raw.containsKey(key)){if(!bool(raw,key,false))throw new IOException("VEP option "+key+" is an explicit presence flag: provide true or omit it to retain original defaults; false is not silently ignored");result.put(key,true);}
        String mode=choice(raw,"transcript_mode","all",Set.of("all","pick","pick_allele","pick_allele_gene","per_gene","flag_pick","flag_pick_allele","flag_pick_allele_gene","most_severe","summary"));result.put("transcript_mode",mode);
        if(mode.equals("most_severe")||mode.equals("summary"))for(String key:List.of("biotype","no_intergenic","protein","symbol","coding_only","ccds","mane","canonical","numbers","domains","tsl","appris","uniprot"))if(Boolean.TRUE.equals(result.get(key)))throw new IOException("Original "+mode+" is incompatible with "+key);
        result.put("sift",choice(raw,"sift","none",Set.of("none","b","s","p")));result.put("polyphen",choice(raw,"polyphen","none",Set.of("none","b","s","p")));
        if((mode.equals("most_severe")||mode.equals("summary"))&&(!result.get("sift").equals("none")||!result.get("polyphen").equals("none")))throw new IOException("Original consequence-only summary mode does not retain SIFT/PolyPhen");
        if(raw.containsKey("pick_order")){if(!mode.contains("pick"))throw new IOException("pick_order requires explicit pick/flag_pick mode");var order=strings(raw.get("pick_order"),"pick_order",1,10);if(!Set.of("mane","canonical","appris","tsl","biotype","ccds","rank","length","ensembl","refseq").containsAll(order))throw new IOException("Unsupported original pick order item");result.put("pick_order",order);}
        result.put("distance",integer(raw,"distance",5000,0,100000000));result.put("max_sv_size",integer(raw,"max_sv_size",10000000,1,2000000000));
        if(!referenceKind.startsWith("human-cache")&&(Boolean.TRUE.equals(result.get("regulatory"))||Boolean.TRUE.equals(result.get("check_existing"))||!result.get("sift").equals("none")||!result.get("polyphen").equals("none")||List.of("af","af_1kg","af_gnomad","max_af").stream().anyMatch(k->Boolean.TRUE.equals(result.get(k)))))throw new IOException("Original prepared GTF/GFF does not supply cache-only variant/frequency/regulatory/SIFT/PolyPhen annotations; no empty lookup substituted");
        return result;
    }
    private static List<String> stringsOrEmpty(Object value)throws IOException {List<?> source=list(value,"fields");if(source.isEmpty())return List.of();return strings(value,"fields",1,128);}
    static long number(Map<String,Object> r,String key){return((Number)r.get(key)).longValue();}
}
