/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.Monocle100NeighborsFiles.*;

/** Explicit engine, prepared-object interpretation and complete execution capacities. */
final class Monocle100NeighborsContracts {
    private Monocle100NeighborsContracts() { }
    static final Set<String> CAP_NAMES=Set.of("maximum_cds_bytes","maximum_json_bytes","maximum_output_bytes","maximum_files","maximum_cells","maximum_rank_entries","maximum_native_operations","timeout_seconds","maximum_address_space_bytes","maximum_sampled_rss_bytes");
    record Caps(long cdsBytes,int jsonBytes,long outputBytes,int files,int cells,long ranks,long nativeOperations,int timeout,long addressSpace,long sampledRss) {
        long maximumFile() { return Math.max(cdsBytes,Math.max(jsonBytes,outputBytes)); }
        Map<String,Object> values() {
            var result=new LinkedHashMap<String,Object>();result.put("maximum_cds_bytes",cdsBytes);result.put("maximum_json_bytes",jsonBytes);result.put("maximum_output_bytes",outputBytes);result.put("maximum_files",files);result.put("maximum_cells",cells);result.put("maximum_rank_entries",ranks);result.put("maximum_native_operations",nativeOperations);result.put("timeout_seconds",timeout);result.put("maximum_address_space_bytes",addressSpace);result.put("maximum_sampled_rss_bytes",sampledRss);return result;
        }
    }
    record RuntimeConfig(Path source,Path python,Path rscript,Path rHome,Path nativeLibraries,List<Path> libraries) {
        Map<String,Object> values() { return Map.of("schema_version",1,"profile",PROFILE,"python",python.toString(),"rscript",rscript.toString(),"r_home",rHome.toString(),"native_library_path",nativeLibraries.toString(),"r_libraries",libraries.stream().map(Path::toString).toList()); }
    }
    record Request(Path source,Path cds,Path runtime,Path out,Map<String,Object> parameters,Caps caps) { }
    static Caps caps(Map<String,Object> value) throws IOException {
        return new Caps(number(value,"maximum_cds_bytes",2_147_483_648L,1,34_359_738_368L),(int)number(value,"maximum_json_bytes",67_108_864L,1_048_576,268_435_456),number(value,"maximum_output_bytes",2_147_483_648L,1024,1_099_511_627_776L),(int)number(value,"maximum_files",10000,100,1000000),(int)number(value,"maximum_cells",1000000,1,1000000),number(value,"maximum_rank_entries",16000000,1,16000000),number(value,"maximum_native_operations",50000000,1,2000000000),(int)number(value,"timeout_seconds",600,1,86400),number(value,"maximum_address_space_bytes",8589934592L,536870912,1099511627776L),number(value,"maximum_sampled_rss_bytes",8589934592L,268435456,1099511627776L));
    }
    private static long number(Map<String,Object> value,String key,long fallback,long low,long high) throws IOException { return value.containsKey(key)?integer(value.get(key),low,high):fallback; }
    static Map<String,Object> parameters(Map<String,Object> value) throws IOException {
        integer(value.get("schema_version"),1,1);String engine=label(value.get("engine")),graph=label(value.get("neighbor_graph"));
        require(Set.of("original","java").contains(engine),"Choose engine=original or java explicitly; no fallback");require(Set.of("knn","principal_graph").contains(graph),"Choose neighbor_graph=knn or principal_graph explicitly");
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("profile",PROFILE);result.put("engine",engine);result.put("neighbor_graph",graph);result.put("k",integer(value.get("k"),1,1000000));result.put("reduction_slot",label(value.get("reduction_slot")));result.put("seed",integer(value.get("seed"),0,2147483647));result.put("dataset_provenance",label(value.get("dataset_provenance")));result.putAll(caps(value).values());return result;
    }
    static Request request(Path path) throws IOException {
        Path source=regular(path);var raw=read(source,1_048_576);Map<String,Object> selected=sourceParameters(raw);
        Path cds=regular(resolve(source.getParent(),raw.get("source_cds"))),runtime=regular(resolve(source.getParent(),raw.get("runtime")));Path out=destination(resolve(source.getParent(),raw.get("out")),List.of(source,cds,runtime));return new Request(source,cds,runtime,out,selected,caps(raw));
    }
    static Map<String,Object> sourceParameters(Map<String,Object> raw) throws IOException {
        var required=Set.of("schema_version","source_cds","runtime","engine","neighbor_graph","k","reduction_slot","seed","dataset_provenance","out");var allowed=new java.util.HashSet<>(required);allowed.addAll(CAP_NAMES);keys(raw,allowed,required);
        for(String name:List.of("source_cds","runtime","out"))label(raw.get(name));return parameters(raw);
    }
    static Map<String,Object> savedParameters(Path root) throws IOException {
        var raw=read(root.resolve("effective-request.json"),1_048_576);var required=new java.util.HashSet<>(Set.of("schema_version","profile","engine","neighbor_graph","k","reduction_slot","seed","dataset_provenance"));required.addAll(CAP_NAMES);keys(raw,required,required);require(PROFILE.equals(raw.get("profile")),"Foreign original-neighbor profile");return parameters(raw);
    }
    static RuntimeConfig runtime(Path path) throws IOException {
        Path source=regular(path);var raw=read(source,1_048_576);var names=Set.of("schema_version","profile","python","rscript","r_home","native_library_path","r_libraries");keys(raw,names,names);integer(raw.get("schema_version"),1,1);require(PROFILE.equals(raw.get("profile")),"Select the exact original-neighbor runtime profile");
        var libraries=new ArrayList<Path>();for(Object value:list(raw.get("r_libraries")))libraries.add(resolve(source.getParent(),value));require(!libraries.isEmpty()&&libraries.size()<=16,"Declare the complete ordered R library path");
        return new RuntimeConfig(source,resolve(source.getParent(),raw.get("python")),resolve(source.getParent(),raw.get("rscript")),resolve(source.getParent(),raw.get("r_home")),resolve(source.getParent(),raw.get("native_library_path")),List.copyOf(libraries));
    }
}
