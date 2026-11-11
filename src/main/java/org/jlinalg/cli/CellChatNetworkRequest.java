/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.CellChatLegacyRequest.*;

/** Explicit original1.1.3 embedding/clustering on a complete parent workflow. */
final class CellChatNetworkRequest {
    private CellChatNetworkRequest() { }
    record Limits(long inputBytes,long outputBytes,long wallSeconds,int features,int files,long runtimeBytes) { }
    record Request(Path out,Path runtimeProfile,Path parentBundle,Map<String,Object> analysis,Limits limits) { }
    static Request parse(Object value,Path base)throws IOException {
        Map<?,?> root=object(value);keys(root,"schema_version","out","runtime_profile","parent_bundle","analysis","limits");integer(root.get("schema_version"),1,1);
        Map<?,?> l=object(root.get("limits"));keys(l,"input_bytes","output_bytes","wall_seconds","max_features","max_files","runtime_bytes");
        Limits limits=new Limits(integer(l.get("input_bytes"),1,4294967296L),integer(l.get("output_bytes"),1024,4294967296L),integer(l.get("wall_seconds"),1,86400),(int)integer(l.get("max_features"),4,10000),(int)integer(l.get("max_files"),100,500000),integer(l.get("runtime_bytes"),1,1099511627776L));
        Map<?,?> a=object(root.get("analysis"));keys(a,"schema_version","source_label","mode","types","embedding","clustering");integer(a.get("schema_version"),1,1);equal(a,"source_label","1.1.3");equal(a,"mode","network-embedding-clustering");List<String> types=names(a.get("types"),1,2);require(Set.of("functional","structural").containsAll(types),"Unknown original similarity type");
        Map<?,?> e=object(a.get("embedding"));keys(e,"umap_method","n_neighbors","min_dist","seed","pathway_remove");equal(e,"umap_method","umap-learn");if(e.get("n_neighbors")!=null)integer(e.get("n_neighbors"),2,10000);number(e.get("min_dist"),0,1,false);integer(e.get("seed"),0,Integer.MAX_VALUE);if(e.get("pathway_remove")!=null)names(e.get("pathway_remove"),0,10000);
        Map<?,?> c=object(a.get("clustering"));keys(c,"method","k","parallel","workers","plot_eigengap","plot_embedding");equal(c,"method","kmeans");if(c.get("k")!=null)integer(c.get("k"),1,10000);bool(c.get("parallel"));integer(c.get("workers"),1,16);bool(c.get("plot_eigengap"));bool(c.get("plot_embedding"));
        var analysis=new LinkedHashMap<String,Object>();for(var entry:a.entrySet())analysis.put(text(entry.getKey()),entry.getValue());
        return new Request(path(base,root.get("out")),path(base,root.get("runtime_profile")),path(base,root.get("parent_bundle")),analysis,limits);
    }
}
