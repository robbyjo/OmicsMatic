/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed scientific settings for the original, unsplit RNA workflow. */
final class SeuratWorkflowContracts {
    private SeuratWorkflowContracts() { }
    static final String PROFILE="Seurat-5.6.0-R4.6.1-RNA-workflow";
    static final List<String> INPUTS=List.of("counts","features","cells","metadata");
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","out","counts","features","cells","metadata","regressors","normalization","variable_features","scale_factor","scale_center","scale_scale","scale_max","sct_cells","sct_min_cells","pca","neighbors","clustering","umap","tsne","seed","rscript","r_libraries","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_features","maximum_cells","maximum_entries","maximum_dense_entries","maximum_work","maximum_graph_edges"));
        if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");
        Map<String,Object> r=new LinkedHashMap<>();r.put("schema_version",1);r.put("mode",choice(raw,"mode","RUN",Set.of("RUN")));
        r.put("profile",choice(raw,"profile",PROFILE,Set.of(PROFILE)));
        for(String k:List.of("out","counts","features","cells","rscript"))r.put(k,text(raw,k));
        r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));
        List<Map<String,Object>> regressors=new ArrayList<>();Set<String> names=new HashSet<>();
        for(Object item:list(raw.getOrDefault("regressors",List.of()),"regressors")) {
            var c=object(item,"regressor");keys(c,Set.of("name","kind","levels"));String name=text(c,"name");identity(name);
            if(name.equals("cell")||!names.add(name)||names.size()>32)throw new IOException("Duplicate/reserved/excessive regressor name");
            String kind=choice(c,"kind","numeric",Set.of("numeric","factor"));var v=new LinkedHashMap<String,Object>();v.put("name",name);v.put("kind",kind);
            if(kind.equals("factor"))v.put("levels",strings(c.get("levels"),"factor levels",2,256));else if(c.containsKey("levels"))throw new IOException("Numeric regressor does not use levels");regressors.add(v);
        }
        r.put("regressors",regressors);if(raw.containsKey("metadata"))r.put("metadata",text(raw,"metadata"));
        if(!regressors.isEmpty()&&!raw.containsKey("metadata"))throw new IOException("Regression requires exact ordered metadata");
        r.put("normalization",choice(raw,"normalization","LogNormalize",Set.of("LogNormalize","SCT-v2")));
        r.put("variable_features",integer(raw,"variable_features",2000,2,100000));
        if(r.get("normalization").equals("LogNormalize")) {
            for(String k:List.of("sct_cells","sct_min_cells"))if(raw.containsKey(k))throw new IOException(k+" is only used for SCT-v2");
            r.put("scale_factor",real(raw,"scale_factor",10000,0,Integer.MAX_VALUE,false));
            if(((Number)r.get("scale_factor")).doubleValue()!=Math.rint(((Number)r.get("scale_factor")).doubleValue()))throw new IOException("LogNormalize scale_factor must be integral; no silent source integer truncation");
            r.put("scale_center",bool(raw,"scale_center",true));r.put("scale_scale",bool(raw,"scale_scale",true));r.put("scale_max",real(raw,"scale_max",10,0,1e10,false));
        }else {
            for(String k:List.of("scale_factor","scale_center","scale_scale","scale_max"))if(raw.containsKey(k))throw new IOException(k+" is not an SCT-v2 option");
            r.put("sct_cells",integer(raw,"sct_cells",5000,2,1000000));r.put("sct_min_cells",integer(raw,"sct_min_cells",5,1,1000000));
        }
        var a=block(raw,"pca",Set.of("solver","components","weight_by_variance","tolerance","max_iterations"));var p=new LinkedHashMap<String,Object>();
        p.put("solver",choice(a,"solver","source_default",Set.of("source_default","irlba","exact")));p.put("components",integer(a,"components",50,2,1000));p.put("weight_by_variance",bool(a,"weight_by_variance",true));
        if(p.get("solver").equals("irlba")){p.put("tolerance",real(a,"tolerance",1e-5,0,.1,false));p.put("max_iterations",integer(a,"max_iterations",1000,1,100000));}
        else if(a.containsKey("tolerance")||a.containsKey("max_iterations"))throw new IOException("IRLBA options require solver irlba");r.put("pca",p);
        a=block(raw,"neighbors",Set.of("dimensions","k","method","prune","trees","metric","epsilon"));var n=new LinkedHashMap<String,Object>();
        n.put("dimensions",dimensions(a.getOrDefault("dimensions",java.util.stream.IntStream.rangeClosed(1,Math.min(30,((Number)p.get("components")).intValue())).boxed().toList()),((Number)p.get("components")).intValue()));
        n.put("k",integer(a,"k",20,1,10000));n.put("method",choice(a,"method","annoy",Set.of("annoy","rann")));n.put("prune",real(a,"prune",1.0/15,0,1,true));
        if(n.get("method").equals("annoy")){if(a.containsKey("epsilon"))throw new IOException("epsilon is only used for RANN");n.put("trees",integer(a,"trees",50,1,10000));n.put("metric",choice(a,"metric","euclidean",Set.of("euclidean","cosine","manhattan","hamming")));}
        else {if(a.containsKey("trees")||a.containsKey("metric"))throw new IOException("trees/metric are only used for Annoy; RANN uses Euclidean");n.put("epsilon",real(a,"epsilon",0,0,1,true));}r.put("neighbors",n);
        a=block(raw,"clustering",Set.of("algorithm","resolutions","starts","iterations","group_singletons","leiden_backend","objective"));var c=new LinkedHashMap<String,Object>();
        c.put("algorithm",integer(a,"algorithm",1,1,4));List<Double> resolutions=new ArrayList<>();Set<Double> unique=new HashSet<>();
        for(Object value:list(a.getOrDefault("resolutions",List.of(.8)),"resolutions")){double x=real(Map.of("value",value),"value",0,0,100,false);if(!unique.add(x)||unique.size()>64)throw new IOException("Repeated/excessive resolutions");resolutions.add(x);}if(resolutions.isEmpty())throw new IOException("Require clustering resolutions");c.put("resolutions",resolutions);
        c.put("iterations",integer(a,"iterations",10,1,10000));c.put("group_singletons",bool(a,"group_singletons",true));
        if(((Number)c.get("algorithm")).intValue()==4){if(a.containsKey("starts"))throw new IOException("Leiden does not use starts");c.put("leiden_backend",choice(a,"leiden_backend","igraph",Set.of("igraph","leidenbase")));c.put("objective",choice(a,"objective","modularity",Set.of("modularity","CPM")));}
        else {for(String k:List.of("leiden_backend","objective"))if(a.containsKey(k))throw new IOException(k+" requires Leiden algorithm 4");c.put("starts",integer(a,"starts",10,1,10000));}r.put("clustering",c);
        a=block(raw,"umap",Set.of("enabled","neighbors","epochs","metric","min_distance","spread"));var u=new LinkedHashMap<String,Object>();u.put("enabled",bool(a,"enabled",true));
        if(Boolean.TRUE.equals(u.get("enabled"))){u.put("neighbors",integer(a,"neighbors",30,2,10000));u.put("epochs",integer(a,"epochs",500,1,100000));u.put("metric",choice(a,"metric","cosine",Set.of("cosine","euclidean","manhattan")));u.put("min_distance",real(a,"min_distance",.3,0,1e4,true));u.put("spread",real(a,"spread",1,0,1e4,false));if(((Number)u.get("min_distance")).doubleValue()>((Number)u.get("spread")).doubleValue())throw new IOException("UMAP min_distance exceeds spread");}
        else if(a.size()>1)throw new IOException("Disabled UMAP cannot have unused options");r.put("umap",u);
        a=block(raw,"tsne",Set.of("enabled","perplexity","iterations","theta"));var t=new LinkedHashMap<String,Object>();t.put("enabled",bool(a,"enabled",true));
        if(Boolean.TRUE.equals(t.get("enabled"))){t.put("perplexity",real(a,"perplexity",30,0,10000,false));t.put("iterations",integer(a,"iterations",1000,250,100000));t.put("theta",real(a,"theta",.5,0,1,true));}
        else if(a.size()>1)throw new IOException("Disabled t-SNE cannot have unused options");r.put("tsne",t);
        r.put("seed",integer(raw,"seed",42,0,Integer.MAX_VALUE));
        for(var entry:Map.of("timeout_seconds",3600L,"maximum_input_bytes",512000000L,"maximum_output_bytes",2000000000L,"maximum_features",100000L,"maximum_cells",100000L,"maximum_entries",20000000L,"maximum_dense_entries",20000000L,"maximum_work",2000000000L,"maximum_graph_edges",20000000L).entrySet()) {
            long max=switch(entry.getKey()){case "timeout_seconds"->86400;case "maximum_features","maximum_cells"->1000000;case "maximum_entries","maximum_graph_edges"->20000000;case "maximum_dense_entries"->200000000;default->40000000000L;};r.put(entry.getKey(),integer(raw,entry.getKey(),entry.getValue(),1,max));
        }
        return r;
    }
    private static Map<String,Object> block(Map<String,Object> raw,String key,Set<String> allowed)throws IOException {var b=object(raw.getOrDefault(key,Map.of()),key);keys(b,allowed);return b;}
    private static List<Long> dimensions(Object raw,int max)throws IOException {List<Long> dims=new ArrayList<>();long previous=0;for(Object o:list(raw,"dimensions")){long x=integer(Map.of("value",o),"value",0,1,max);if(x<=previous)throw new IOException("PCA dimensions must be strictly increasing");dims.add(x);previous=x;}if(dims.isEmpty())throw new IOException("Require PCA dimensions");return dims;}
}
