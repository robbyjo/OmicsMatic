/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle131ModelRequest.*;

/** Explicit candidate edition; never substitutes it for the paper's version 0.1.1. */
final class Rphenograph099Request {
    private Rphenograph099Request() { }
    record Request(Path matrix,Path out,Path rscript,Path source,List<Path> libraries,
            int k,int seed,String units,String provenance,String population,boolean execute,
            long inputBytes,long outputBytes,int entries,int jsonBytes,long operations,int seconds,long addressSpace,long rss) { }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","version","matrix","input_units","input_provenance","analysis_population","k","seed","execution_policy","out","rscript","r_libraries","source_root","maximum_input_bytes","maximum_output_bytes","maximum_entries","maximum_json_bytes","maximum_native_operations","timeout_seconds","maximum_address_space_bytes","maximum_sampled_rss_bytes");
        integer(m.get("schema_version"),1,1);require(text(m.get("version")).equals("0.99.1"),"Require explicit recovered candidate version 0.99.1; reported Rphenograph 0.1.1 is unresolved and is never substituted");
        String policy=text(m.get("execution_policy"));require(Set.of("ALLOW","FORBID").contains(policy),"Require ALLOW or FORBID execution_policy");
        var libraries=new ArrayList<Path>();for(Object v:list(m.get("r_libraries")))libraries.add(path(parent,v));require(!libraries.isEmpty()&&libraries.size()<=16&&new HashSet<>(libraries).size()==libraries.size(),"Require unique declared R libraries");
        return new Request(path(parent,m.get("matrix")),path(parent,m.get("out")),path(parent,m.get("rscript")),path(parent,m.get("source_root")),List.copyOf(libraries),(int)integer(m.get("k"),1,1000000),(int)integer(m.get("seed"),0,2147483647),label(m.get("input_units")),label(m.get("input_provenance")),label(m.get("analysis_population")),policy.equals("ALLOW"),number(m,"maximum_input_bytes",268435456,1,137438953472L),number(m,"maximum_output_bytes",1073741824,1,137438953472L),(int)number(m,"maximum_entries",10000000,1,1000000000),(int)number(m,"maximum_json_bytes",67108864,1,268435456),number(m,"maximum_native_operations",500000000,1,1000000000000L),(int)number(m,"timeout_seconds",900,1,604800),number(m,"maximum_address_space_bytes",17179869184L,268435456,1099511627776L),number(m,"maximum_sampled_rss_bytes",8589934592L,16777216,1099511627776L));
    }
}
