/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Complete prepared UMAP population and caller-declared original graph controls. */
final class Monocle133GraphRequest {
    private Monocle133GraphRequest() { }
    record Request(Map<String,Object> parameters,Path counts,Path cells,Path genes,Path out,
                   Path rscript,List<Path> libraries,Path source,boolean execute,int bytes,
                   long outputBytes,long operations,int seconds) { }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","mode","version","count_units","embedding_slot","counts","cells","genes","out","rscript","r_libraries","source_root","execution_policy","cluster_levels","partition_levels","seed","use_partition","close_loop","ncenter","minimal_branch_len","root_pr_nodes","root_cells","require_terminal_roots","expected_principal_graph_sha256","maximum_input_bytes","maximum_output_bytes","maximum_native_operations","timeout_seconds");
        integer(m.get("schema_version"),1,1);String mode=text(m.get("mode"));require(Set.of("LEARN","RUN").contains(mode),"Choose LEARN or RUN");
        require(text(m.get("version")).equals("1.3.3"),"This runtime requires original Monocle3 1.3.3; no version substitution");
        require(text(m.get("count_units")).equals("raw-integer-counts"),"Require raw-integer-counts; activity, TPM and transformed values need separate workflows");
        require(text(m.get("embedding_slot")).equals("UMAP"),"Original 1.3.3 graph learning requires the caller-prepared UMAP slot");
        var cluster=labels(m.get("cluster_levels"),1000000);var partition=labels(m.get("partition_levels"),1000000);
        for(int i=0;i<partition.size();i++)require(partition.get(i).equals(Integer.toString(i+1)),"Original projection requires partition levels 1..K in order; labels are never silently recoded");
        integer(m.get("seed"),1,2147483646);integer(m.get("ncenter"),2,9999);integer(m.get("minimal_branch_len"),1,1000000);
        bool(m.get("use_partition"));bool(m.get("close_loop"));bool(m.get("require_terminal_roots"));
        if(mode.equals("RUN")){
            require(m.containsKey("root_pr_nodes")^m.containsKey("root_cells"),"RUN requires exactly one caller-declared root list");
            var roots=list(m.get(m.containsKey("root_pr_nodes")?"root_pr_nodes":"root_cells"));require(!roots.isEmpty()&&roots.size()<=9999,"Require 1..9999 explicit roots");for(Object root:roots)labels(List.of(root),1);
        }else require(!m.containsKey("root_pr_nodes")&&!m.containsKey("root_cells"),"LEARN accepts no roots and admits no pseudotime");
        if(m.containsKey("expected_principal_graph_sha256"))require(text(m.get("expected_principal_graph_sha256")).matches("[a-f0-9]{64}"),"Expected graph fingerprint must be lowercase SHA256");
        String policy=text(m.get("execution_policy"));require(Set.of("ALLOW","FORBID").contains(policy),"Declare execution_policy ALLOW or FORBID");
        var libs=new ArrayList<Path>();for(Object x:list(m.get("r_libraries"))){require(libs.size()<16,"Too many R libraries");libs.add(path(parent,x));}require(!libs.isEmpty(),"Declare private R libraries explicitly");
        var p=new LinkedHashMap<String,Object>();for(String k:List.of("schema_version","mode","version","count_units","embedding_slot","cluster_levels","partition_levels","seed","use_partition","close_loop","ncenter","minimal_branch_len","require_terminal_roots","root_pr_nodes","root_cells"))if(m.containsKey(k))p.put(k,m.get(k));
        require(!cluster.isEmpty(),"Declare cluster levels");
        return new Request(Collections.unmodifiableMap(p),path(parent,m.get("counts")),path(parent,m.get("cells")),path(parent,m.get("genes")),path(parent,m.get("out")),path(parent,m.get("rscript")),List.copyOf(libs),path(parent,m.get("source_root")),policy.equals("ALLOW"),
            (int)number(m,"maximum_input_bytes",67108864,1,268435456),number(m,"maximum_output_bytes",1073741824,1,8589934592L),number(m,"maximum_native_operations",50000000,1,1000000000),(int)number(m,"timeout_seconds",900,1,86400));
    }
    static Map<String,Object> object(Object x)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:NextflowFiles.object(x).entrySet())result.put(text(e.getKey()),e.getValue());return result;}
    static List<?> list(Object x)throws IOException{return NextflowFiles.list(x);}
    static String text(Object x)throws IOException{return NextflowFiles.text(x);}
    static long integer(Object x,long min,long max)throws IOException{return NextflowFiles.integer(x,min,max);}
    static boolean bool(Object x)throws IOException{require(x instanceof Boolean,"Require a JSON boolean");return (Boolean)x;}
    static long number(Map<String,Object> m,String k,long d,long min,long max)throws IOException{return m.containsKey(k)?integer(m.get(k),min,max):d;}
    static List<String> labels(Object x,int max)throws IOException {
        var result=new ArrayList<String>();var seen=new HashSet<String>();for(Object o:list(x)){String s=text(o);require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127)&&seen.add(s),"Require unique literal nonempty labels without control characters");require(result.size()<max,"Complete label capacity exceeded");result.add(s);}require(!result.isEmpty(),"Empty label list");return List.copyOf(result);
    }
    static void allowed(Map<String,Object> m,String... keys)throws IOException{var allowed=Set.of(keys);for(String k:m.keySet())require(allowed.contains(k),"Unknown field: "+k);}
    static Path path(Path parent,Object x)throws IOException{String s=text(x);require(!s.isBlank()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Invalid path");Path p=Path.of(s);return (p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static void require(boolean ok,String cause)throws IOException{if(!ok)throw new IOException(cause);}
    static double finite(Object x)throws IOException{String s=x instanceof NextflowParameters.NumberToken n?n.text():x instanceof Number?x.toString():"";double d;try{d=Double.parseDouble(s);}catch(NumberFormatException e){throw new IOException("Require numeric JSON value",e);}require(Double.isFinite(d),"Nonfinite coordinate/weight");return d;}
}
