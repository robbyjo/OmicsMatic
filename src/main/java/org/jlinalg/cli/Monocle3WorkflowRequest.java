/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Typed, additive model terms and explicit trajectory options; no executable R input. */
final class Monocle3WorkflowRequest {
    private Monocle3WorkflowRequest() { }
    record Request(String version,Path counts,Path cells,Path genes,Map<String,Object> types,Path out,
                   Path rscript,List<Path> libraries,List<Map<String,Object>> analyses,long inputBytes,
                   int maximumEntries,int jsonBytes,long outputBytes,long nativeOperations,int timeout) {
        Map<String,Object> parameters(){return Map.of("version",version,"metadata_types",types,"analyses",analyses);}
        Map<String,Object> effective(){
            var m=new LinkedHashMap<String,Object>(parameters());m.put("schema_version",1);m.put("mode","RUN");
            m.put("counts",counts.toString());m.put("cell_metadata",cells.toString());m.put("gene_metadata",genes.toString());
            m.put("count_units","raw-integer-counts");m.put("out",out.toString());m.put("rscript",rscript.toString());
            m.put("r_libraries",libraries.stream().map(Path::toString).toList());m.put("maximum_input_bytes",inputBytes);
            m.put("maximum_entries",maximumEntries);m.put("maximum_json_bytes",jsonBytes);m.put("maximum_output_bytes",outputBytes);
            m.put("maximum_native_operations",nativeOperations);m.put("timeout_seconds",timeout);return m;
        }
    }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","mode","version","counts","cell_metadata","gene_metadata","count_units","metadata_types","out","rscript","r_libraries","analyses","maximum_input_bytes","maximum_entries","maximum_json_bytes","maximum_output_bytes","maximum_native_operations","timeout_seconds");
        integer(m.get("schema_version"),1,1);require(text(m.get("mode")).equals("RUN"),"Require mode RUN");
        String version=label(m.get("version"));require(version.matches("[0-9]+[.][0-9]+[.][0-9]+"),"Declare an exact original version; only 1.4.27 has an admitted runtime profile");
        require(text(m.get("count_units")).equals("raw-integer-counts"),"Declare raw-integer-counts; activity, TPM and transformed values are outside this workflow");
        var types=object(m.get("metadata_types"));require(types.size()<=128,"At most 128 metadata columns");
        for(var e:types.entrySet()){
            column(e.getKey());require(!Set.of("Size_Factor","pseudotime","cluster","partition","f_expression").contains(e.getKey()),"Reserved original model metadata column: "+e.getKey());
            var spec=object(e.getValue());allowed(spec,"type","levels");String type=text(spec.get("type"));
            require(Set.of("numeric","factor").contains(type),"Metadata type must be numeric or factor");
            if(type.equals("factor")){var levels=strings(spec.get("levels"),1024);require(!levels.isEmpty()&&new HashSet<>(levels).size()==levels.size(),"Declare unique factor levels in intended order");}
            else require(!spec.containsKey("levels"),"Numeric metadata cannot declare factor levels");
        }
        var libraries=new ArrayList<Path>();for(Object x:list(m.get("r_libraries"))){require(libraries.size()<64,"Too many libraries");libraries.add(resolve(parent,label(x)));}require(!libraries.isEmpty(),"Declare private R libraries explicitly");
        var analyses=new ArrayList<Map<String,Object>>();var ids=new HashSet<String>();
        for(Object raw:list(m.get("analyses"))){
            require(analyses.size()<32,"At most 32 analyses");var a=object(raw);allowed(a,"id","seed","trajectory","fit_models","graph_test");
            String id=text(a.get("id"));require(id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")&&ids.add(id.toLowerCase(Locale.ROOT)),"Unique portable analysis IDs required, including case folding");
            integer(a.get("seed"),1,2147483646);require(a.containsKey("trajectory")||a.containsKey("fit_models"),"Require trajectory and/or fit_models");
            if(a.containsKey("trajectory"))trajectory(object(a.get("trajectory")));
            if(a.containsKey("fit_models")){
                var f=object(a.get("fit_models"));allowed(f,"family","intercept","terms");
                require(Set.of("poisson","quasipoisson","negbinomial","gaussian","binomial","zipoisson","zinegbinomial").contains(text(f.get("family"))),"Unsupported original family; mixed effects require a separate typed contract");
                bool(f.get("intercept"));var terms=list(f.get("terms"));require(terms.size()<=64,"At most 64 additive terms");var seen=new HashSet<String>();
                for(Object x:terms){var term=object(x);allowed(term,"column","spline_df");String c=column(text(term.get("column")));
                    require(types.containsKey(c)||(c.equals("pseudotime")&&a.containsKey("trajectory")),"Undeclared model covariate: "+c);
                    require(seen.add(c),"Duplicate additive model column: "+c);
                    if(term.containsKey("spline_df")){integer(term.get("spline_df"),1,50);require(c.equals("pseudotime")||text(object(types.get(c)).get("type")).equals("numeric"),"Natural splines require numeric covariates");}
                }
                require(bool(f.get("intercept"))||!terms.isEmpty(),"An empty no-intercept model is unsupported");
            }
            if(a.containsKey("graph_test")){
                require(a.containsKey("trajectory"),"graph_test needs an explicit trajectory in this interface");var g=object(a.get("graph_test"));allowed(g,"neighbor_graph","k","alternative");
                require(Set.of("principal_graph","knn").contains(text(g.get("neighbor_graph"))),"Choose original graph_test neighbor graph");
                integer(g.get("k"),2,10000);require(Set.of("greater","less","two.sided").contains(text(g.get("alternative"))),"Invalid graph-test alternative");
            }
            analyses.add(Collections.unmodifiableMap(a));
        }
        require(!analyses.isEmpty(),"Require 1..32 analyses");
        return new Request(version,resolve(parent,label(m.get("counts"))),resolve(parent,label(m.get("cell_metadata"))),resolve(parent,label(m.get("gene_metadata"))),Collections.unmodifiableMap(types),
            resolve(parent,label(m.get("out"))),resolve(parent,label(m.get("rscript"))),List.copyOf(libraries),List.copyOf(analyses),
            number(m,"maximum_input_bytes",67108864,1,268435456),(int)number(m,"maximum_entries",1000000,1,10000000),
            (int)number(m,"maximum_json_bytes",67108864,1,268435456),number(m,"maximum_output_bytes",536870912,1,4294967296L),
            number(m,"maximum_native_operations",50000000,1,1000000000),(int)number(m,"timeout_seconds",600,1,86400));
    }
    private static void trajectory(Map<String,Object> t)throws IOException {
        allowed(t,"preprocess","umap","cluster","graph","root_cells","root_pr_nodes");
        require(t.containsKey("root_cells")^t.containsKey("root_pr_nodes"),"Declare exactly one root_cells or root_pr_nodes list");
        require(!strings(t.get(t.containsKey("root_cells")?"root_cells":"root_pr_nodes"),9999).isEmpty(),"Root list cannot be empty");
        var p=object(t.get("preprocess"));allowed(p,"method","num_dim","norm_method","pseudo_count","scaling");
        require(Set.of("PCA","LSI").contains(text(p.get("method"))),"Preprocessing method must be PCA or LSI");integer(p.get("num_dim"),2,1000);
        require(Set.of("log","size_only","none").contains(text(p.get("norm_method"))),"Unsupported normalization");finite(p.get("pseudo_count"),0,1000000);bool(p.get("scaling"));
        var u=object(t.get("umap"));allowed(u,"n_neighbors","min_dist","metric","fast_sgd");integer(u.get("n_neighbors"),2,10000);finite(u.get("min_dist"),0,1);
        require(Set.of("cosine","euclidean","manhattan").contains(text(u.get("metric"))),"Unsupported typed UMAP metric");bool(u.get("fast_sgd"));
        var c=object(t.get("cluster"));allowed(c,"method","k","resolution","partition_qval","weight");require(Set.of("leiden","louvain").contains(text(c.get("method"))),"Choose leiden or louvain");
        integer(c.get("k"),2,10000);finite(c.get("resolution"),0.000000000001,1000000);finite(c.get("partition_qval"),0,1);bool(c.get("weight"));
        var g=object(t.get("graph"));allowed(g,"use_partition","close_loop","ncenter","minimal_branch_len","prune_graph","maxiter");bool(g.get("use_partition"));bool(g.get("close_loop"));
        integer(g.get("ncenter"),2,9999);integer(g.get("minimal_branch_len"),1,1000000);bool(g.get("prune_graph"));integer(g.get("maxiter"),1,100000);
    }
    static Map<String,Object> object(Object x)throws IOException {return ChipSeekerAnnotationRequest.object(x);}
    static List<?> list(Object x)throws IOException {return NextflowFiles.list(x);}
    static String text(Object x)throws IOException {return NextflowFiles.text(x);}
    static String label(Object x)throws IOException {return ChipSeekerAnnotationRequest.label(x,32768);}
    static long integer(Object x,long min,long max)throws IOException {return NextflowFiles.integer(x,min,max);}
    static long number(Map<String,Object> m,String k,long d,long min,long max)throws IOException {return m.containsKey(k)?integer(m.get(k),min,max):d;}
    static boolean bool(Object x)throws IOException {require(x instanceof Boolean,"Require a JSON boolean");return (Boolean)x;}
    static double finite(Object x,double min,double max)throws IOException {String s=x instanceof NextflowParameters.NumberToken n?n.text():x instanceof Number?x.toString():"";double d;try{d=Double.parseDouble(s);}catch(NumberFormatException e){throw new IOException("Require numeric JSON value",e);}require(Double.isFinite(d)&&d>=min&&d<=max,"Numeric value outside declared domain");return d;}
    static String column(String s)throws IOException {require(s.matches("[A-Za-z][A-Za-z0-9_]{0,63}")&&!Set.of("TRUE","FALSE","NULL","NA","NaN","Inf","if","else","repeat","while","function","for","in","next","break").contains(s),"Metadata columns must be safe literal R names: "+s);return s;}
    static List<String> strings(Object x,int max)throws IOException {var out=new ArrayList<String>();for(Object v:list(x)){require(out.size()<max,"Excessive string population");out.add(label(v));}return List.copyOf(out);}
    static Path resolve(Path parent,String s){Path p=Path.of(s);return (p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static void allowed(Map<String,Object> m,String... keys)throws IOException {var set=Set.of(keys);for(String k:m.keySet())require(set.contains(k),"Unknown Monocle workflow field: "+k);}
    static void require(boolean b,String detail)throws IOException {if(!b)throw new IOException(detail);}
}
