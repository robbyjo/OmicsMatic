/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle131ModelRequest.*;

/** Literal prepared-input request for original Batchelor1.29.0 fastMNN. */
final class FastMnn129Request {
    private FastMnn129Request() { }
    record Request(Path matrix,Path cells,Path features,Path out,Path rscript,Path source,List<Path> libraries,
            String format,String units,String reconstructionAudit,List<String> batches,Map<String,Object> parameters,boolean execute,
            long inputBytes,long outputBytes,int entries,int jsonBytes,long operations,int seconds,long addressSpace,long rss) { }

    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","version","matrix","cells","features","format","input_units","input_provenance","analysis_population","batch_order","seed","parameters","reconstruction_audit","execution_policy","out","rscript","r_libraries","source_root","maximum_input_bytes","maximum_output_bytes","maximum_entries","maximum_json_bytes","maximum_native_operations","timeout_seconds","maximum_address_space_bytes","maximum_sampled_rss_bytes");
        require(integer(m.get("schema_version"),1,1)==1&&text(m.get("version")).equals("1.29.0"),"Require schema1 and explicit original Batchelor1.29.0 source-candidate version; paper patch is unproven");
        String format=text(m.get("format")),units=text(m.get("input_units"));
        require(Set.of("TSV","MATRIX_MARKET").contains(format),"Require complete TSV or MATRIX_MARKET input");
        require(Set.of("prepared-log-expression","prepared-asinh-protein").contains(units),"Declare prepared-log-expression or prepared-asinh-protein; raw counts and implicit transformations are not supported");
        label(m.get("input_provenance"));label(m.get("analysis_population"));
        List<String> batches=labels(m.get("batch_order"),1024);require(batches.size()>=2,"Require explicit order of at least two nonempty batches");
        var given=object(m.get("parameters"));allowed(given,"k","prop_k","cosine_normalization","ndist","dimensions","deferred","weights","get_variance","merge_order","auto_merge","min_batch_skip","subset_features","correct_all","restrict_cells","pca_method","neighbor_method","workers");
        var p=new LinkedHashMap<String,Object>();p.put("seed",integer(m.get("seed"),0,2147483647L));
        p.put("k",number(given,"k",20,1,1000000));
        Object prop=given.get("prop_k");if(prop!=null)require(finite(prop)>0&&finite(prop)<1,"prop_k must be in (0,1)");p.put("prop_k",prop==null?null:finite(prop));
        p.put("cosine_normalization",given.containsKey("cosine_normalization")?bool(given.get("cosine_normalization")):true);
        double ndist=given.containsKey("ndist")?finite(given.get("ndist")):3;require(ndist>0,"ndist must be positive");p.put("ndist",ndist);
        Object dimensions=given.containsKey("dimensions")?given.get("dimensions"):Long.valueOf(50);p.put("dimensions",dimensions==null?null:integer(dimensions,1,1000000));
        Object deferred=Boolean.TRUE;if(given.containsKey("deferred"))deferred=given.get("deferred")==null?null:Boolean.valueOf(bool(given.get("deferred")));p.put("deferred",deferred);
        Object weights=given.get("weights");
        if(weights==null||weights instanceof Boolean)p.put("weights",weights);
        else if(weights instanceof List<?> values){var numeric=new ArrayList<Double>();for(Object v:values)numeric.add(finite(v));require(numeric.size()==batches.size(),"Numeric weights must contain one literal value per declared batch");p.put("weights",numeric);}
        else{var specification=object(weights);require(specification.size()==1,"Use exactly one explicit weights representation");allowed(specification,"tree","batch_weights");
            if(specification.containsKey("tree")){var leaves=new ArrayList<Integer>();Object tree=weightTree(specification.get("tree"),batches.size(),leaves,0);var expected=new HashSet<Integer>();for(int i=1;i<=batches.size();i++)expected.add(i);require(leaves.size()==batches.size()&&new HashSet<>(leaves).equals(expected),"Weight tree must contain every one-based batch exactly once");p.put("weights",Map.of("tree",tree));}
            else{var supplied=object(specification.get("batch_weights"));require(supplied.keySet().equals(new HashSet<>(batches)),"Named weights must match every declared batch exactly");var ordered=new LinkedHashMap<String,Object>();for(String batch:batches)ordered.put(batch,finite(supplied.get(batch)));p.put("weights",Map.of("batch_weights",ordered));}
        }
        p.put("get_variance",given.containsKey("get_variance")?bool(given.get("get_variance")):false);
        Object merge=given.get("merge_order");if(merge==null)p.put("merge_order",null);else{var leaves=new ArrayList<Integer>();Object order=merge(merge,batches.size(),leaves,0);var expected=new HashSet<Integer>();for(int i=1;i<=batches.size();i++)expected.add(i);require(leaves.size()==batches.size()&&new HashSet<>(leaves).equals(expected),"merge_order must contain every one-based batch exactly once");p.put("merge_order",order);}
        p.put("auto_merge",given.containsKey("auto_merge")?bool(given.get("auto_merge")):false);
        double skip=given.containsKey("min_batch_skip")?finite(given.get("min_batch_skip")):0;require(skip>=0,"min_batch_skip must be nonnegative");p.put("min_batch_skip",skip);
        for(String key:List.of("subset_features","restrict_cells"))p.put(key,given.get(key)==null?null:labels(given.get(key),1000000));
        p.put("correct_all",given.containsKey("correct_all")?bool(given.get("correct_all")):false);
        String pca=given.containsKey("pca_method")?text(given.get("pca_method")):"IRLBA",nn=given.containsKey("neighbor_method")?text(given.get("neighbor_method")):"KMKNN";
        require(Set.of("IRLBA","EXACT").contains(pca),"Require explicit supported IRLBA or EXACT PCA backend");require(Set.of("KMKNN","EXHAUSTIVE").contains(nn),"Require supported KMKNN or EXHAUSTIVE neighbor backend");p.put("pca_method",pca);p.put("neighbor_method",nn);p.put("workers",number(given,"workers",1,1,64));
        String reconstruction=m.containsKey("reconstruction_audit")?text(m.get("reconstruction_audit")):"FACTORS";require(Set.of("FACTORS","ALL_VALUES").contains(reconstruction),"Require FACTORS or ALL_VALUES reconstruction_audit");
        String policy=text(m.get("execution_policy"));require(Set.of("ALLOW","FORBID").contains(policy),"Require ALLOW or FORBID execution_policy");
        var libraries=new ArrayList<Path>();for(Object value:list(m.get("r_libraries")))libraries.add(path(parent,value));require(!libraries.isEmpty()&&libraries.size()<=32&&new HashSet<>(libraries).size()==libraries.size(),"Require unique declared R libraries");
        return new Request(path(parent,m.get("matrix")),path(parent,m.get("cells")),path(parent,m.get("features")),path(parent,m.get("out")),path(parent,m.get("rscript")),path(parent,m.get("source_root")),List.copyOf(libraries),format,units,reconstruction,batches,Collections.unmodifiableMap(p),policy.equals("ALLOW"),number(m,"maximum_input_bytes",268435456,1,137438953472L),number(m,"maximum_output_bytes",1073741824,1,137438953472L),(int)number(m,"maximum_entries",10000000,1,1000000000),(int)number(m,"maximum_json_bytes",67108864,1,268435456),number(m,"maximum_native_operations",500000000,1,1000000000000L),(int)number(m,"timeout_seconds",900,1,604800),number(m,"maximum_address_space_bytes",17179869184L,268435456,1099511627776L),number(m,"maximum_sampled_rss_bytes",8589934592L,16777216,1099511627776L));
    }

    private static Object merge(Object value,int n,List<Integer> leaves,int depth)throws IOException {
        require(depth<=32,"Merge tree depth exceeds capacity");
        if(value instanceof List<?> values){require(!values.isEmpty(),"Empty merge node");boolean nested=values.stream().anyMatch(v->v instanceof List<?>);require(!nested||values.size()==2,"Hierarchical merge nodes must be binary");var result=new ArrayList<Object>();for(Object child:values)result.add(merge(child,n,leaves,depth+1));return result;}
        int index=(int)integer(value,1,n);leaves.add(index);return index;
    }
    private static Object weightTree(Object value,int n,List<Integer> leaves,int depth)throws IOException {
        require(depth<=32,"Weight tree depth exceeds capacity");if(value instanceof List<?> children){require(!children.isEmpty(),"Empty weight-tree node");var result=new ArrayList<Object>();for(Object child:children)result.add(weightTree(child,n,leaves,depth+1));return result;}int index=(int)integer(value,1,n);leaves.add(index);return index;
    }
}
