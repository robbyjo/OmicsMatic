/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed original Signac/MACS requests; no arbitrary command or shell-argument field. */
final class SignacPeaksContracts {
    private SignacPeaksContracts() { }
    static final String ENGINE="original-MACS2-Signac-peaks-development";
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","out","source_profile","macs2_version","macs2_home","genome_build","effective_genome_size","sources","cell_groups","combine_peaks","name","format","broad","extsize","shift","qvalue","pvalue","keep_duplicates","call_summits","no_lambda","broad_cutoff","min_length","max_gap","fold_enrichment_cutoff","small_local","large_local","bedgraph","SPMR","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_peak_rows","maximum_groups","maximum_cells","maximum_fragment_records","maximum_decoded_bytes","maximum_memory_bytes","maximum_sampled_rss_bytes","maximum_cpu_seconds","maximum_processes","maximum_threads","maximum_files"));
        if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version1");var r=new LinkedHashMap<String,Object>(raw);
        String mode=choice(raw,"mode","",Set.of("POOLED","GROUPED"));r.put("mode",mode);
        for(String name:List.of("out","macs2_home","genome_build"))text(raw,name);
        r.put("source_profile",choice(raw,"source_profile","",Set.of("Signac-1.9.0","Signac-1.14.0")));r.put("macs2_version",choice(raw,"macs2_version","",Set.of("2.2.7.1","2.2.9","2.2.9.1")));
        if(!raw.containsKey("effective_genome_size"))throw new IOException("Declare effective_genome_size; no assembly-specific value is inferred");r.put("effective_genome_size",real(raw,"effective_genome_size",0,0,1e15,false));identity(text(raw,"genome_build"));
        var sources=list(raw.get("sources"),"sources");if(sources.isEmpty()||sources.size()>256)throw new IOException("Require1..256 fragment sources");List<Map<String,Object>> inputs=new ArrayList<>();
        for(Object item:sources){var s=object(item,"source");keys(s,Set.of("fragments","map"));text(s,"fragments");if(s.containsKey("map")){text(s,"map");if(mode.equals("POOLED"))throw new IOException("POOLED consumes all fragment rows; maps are unused and refused");}inputs.add(s);}r.put("sources",inputs);
        if(mode.equals("GROUPED")){text(raw,"cell_groups");r.put("combine_peaks",bool(raw,"combine_peaks",true));if(raw.containsKey("name"))throw new IOException("Grouped names come from declared cell groups; name is unused");}
        else {if(raw.containsKey("cell_groups")||raw.containsKey("combine_peaks"))throw new IOException("POOLED refuses unused group options");String name=raw.containsKey("name")?text(raw,"name"):"macs2";identity(name);r.put("name",name);}
        String format=choice(raw,"format","BED",Set.of("BED","BEDPE"));r.put("format",format);boolean broad=bool(raw,"broad",false);r.put("broad",broad);
        if(format.equals("BED")){long ext=integer(raw,"extsize",200,1,1_000_000);if(!raw.containsKey("shift")&&ext%2!=0)throw new IOException("Odd extsize gives a fractional original default shift, rejected by MACS; provide an explicit integer shift rather than silently rounding");r.put("extsize",ext);r.put("shift",integer(raw,"shift",-ext/2,-1_000_000,1_000_000));}
        else if(raw.containsKey("extsize")||raw.containsKey("shift"))throw new IOException("BEDPE uses real fragment intervals; extsize/shift are unused");
        if(raw.containsKey("qvalue")&&raw.containsKey("pvalue"))throw new IOException("Choose qvalue or pvalue, not both");for(String key:List.of("qvalue","pvalue","broad_cutoff"))if(raw.containsKey(key))r.put(key,real(raw,key,0,0,1,false));
        if(raw.containsKey("broad_cutoff")&&!broad)throw new IOException("broad_cutoff requires broad mode");
        if(raw.containsKey("keep_duplicates")){Object v=raw.get("keep_duplicates");if(!(v instanceof String s&&Set.of("all","auto").contains(s)))v=integer(raw,"keep_duplicates",0,1,Integer.MAX_VALUE);r.put("keep_duplicates",v);}
        for(String key:List.of("call_summits","no_lambda","bedgraph","SPMR"))r.put(key,bool(raw,key,false));
        if(broad&&Boolean.TRUE.equals(r.get("call_summits")))throw new IOException("Broad mode and call_summits are incompatible");if(Boolean.TRUE.equals(r.get("SPMR"))&&!Boolean.TRUE.equals(r.get("bedgraph")))throw new IOException("SPMR requires bedgraph output");
        for(String key:List.of("min_length","max_gap","small_local","large_local"))if(raw.containsKey(key))r.put(key,integer(raw,key,0,key.equals("max_gap")?0:1,Integer.MAX_VALUE));
        if(raw.containsKey("fold_enrichment_cutoff"))r.put("fold_enrichment_cutoff",real(raw,"fold_enrichment_cutoff",0,0,1e15,false));
        for(var e:Map.of("timeout_seconds",3600L,"maximum_groups",128L,"maximum_cells",2_000_000L,"maximum_peak_rows",2_000_000L,"maximum_processes",16L,"maximum_threads",64L,"maximum_files",10000L).entrySet())r.put(e.getKey(),integer(raw,e.getKey(),e.getValue(),1,switch(e.getKey()){case "timeout_seconds"->86400;case "maximum_groups"->512;case "maximum_cells","maximum_peak_rows"->2_000_000;case "maximum_processes"->4096;case "maximum_threads"->65536;default->100000;}));
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",1_000_000_000L,1,1_000_000_000_000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2_000_000_000L,1,1_000_000_000_000L));
        r.put("maximum_fragment_records",integer(raw,"maximum_fragment_records",1_000_000_000L,1,1_000_000_000_000L));r.put("maximum_decoded_bytes",integer(raw,"maximum_decoded_bytes",250_000_000_000L,1,1_000_000_000_000L));
        r.put("maximum_memory_bytes",integer(raw,"maximum_memory_bytes",4_294_967_296L,67_108_864L,1_099_511_627_776L));r.put("maximum_sampled_rss_bytes",integer(raw,"maximum_sampled_rss_bytes",2_147_483_648L,1,1_099_511_627_776L));r.put("maximum_cpu_seconds",integer(raw,"maximum_cpu_seconds",3600,1,604800));return r;
    }
    static long value(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
    static List<String> arguments(Map<String,Object> r,List<String> files,String prefix,String out)throws IOException {
        List<String> args=new ArrayList<>(List.of("callpeak","-t"));args.addAll(files);args.addAll(List.of("-g",r.get("effective_genome_size").toString(),"-f",text(r,"format")));
        if(Boolean.TRUE.equals(r.get("broad")))args.add("--broad");if(r.get("format").equals("BED"))args.addAll(List.of("--nomodel","--extsize",r.get("extsize").toString(),"--shift",r.get("shift").toString()));args.addAll(List.of("-n",prefix,"--outdir",out));
        Map<String,String> options=new LinkedHashMap<>();options.put("qvalue","-q");options.put("pvalue","-p");options.put("keep_duplicates","--keep-dup");options.put("broad_cutoff","--broad-cutoff");options.put("min_length","--min-length");options.put("max_gap","--max-gap");options.put("small_local","--slocal");options.put("large_local","--llocal");options.put("fold_enrichment_cutoff","--fe-cutoff");
        for(var e:options.entrySet())if(r.containsKey(e.getKey()))args.addAll(List.of(e.getValue(),r.get(e.getKey()).toString()));for(var e:List.of(Map.entry("call_summits","--call-summits"),Map.entry("no_lambda","--nolambda"),Map.entry("bedgraph","-B"),Map.entry("SPMR","--SPMR")))if(Boolean.TRUE.equals(r.get(e.getKey())))args.add(e.getValue());return List.copyOf(args);
    }
}
