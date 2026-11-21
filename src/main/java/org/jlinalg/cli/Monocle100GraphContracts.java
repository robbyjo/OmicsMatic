/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.cli.Monocle100NeighborsContracts.Caps;
import org.jlinalg.cli.Monocle100NeighborsContracts.RuntimeConfig;
import static org.jlinalg.cli.Monocle100NeighborsFiles.*;

/** Complete original prepared-CDS graph operations; source names and settings stay explicit. */
final class Monocle100GraphContracts {
    private Monocle100GraphContracts() { }
    static final String GRAPH_PROFILE="monocle100-original-graph-r43-v1";
    static final Set<String> COMMON=Set.of("schema_version","source_cds","runtime","operation","reduction_slot","seed","dataset_provenance","out");
    static final Set<String> SCIENTIFIC=Set.of("learn_graph_control","convergence_policy","use_partition","close_loop","root_pr_nodes","root_cells","graph_provenance");
    static final Set<String> CONTROLS=Set.of("euclidean_distance_ratio","geodesic_distance_ratio","minimal_branch_len","orthogonal_proj_tip","prune_graph","scale","ncenter","rann.k","maxiter","eps","L1.gamma","L1.sigma");
    record Request(Path source,Path cds,Path runtime,Path out,Map<String,Object> parameters,Caps caps) { }
    static Caps caps(Map<String,Object> value) throws IOException {
        var selected=new LinkedHashMap<String,Object>(value);selected.put("maximum_rank_entries",16000000);return Monocle100NeighborsContracts.caps(selected);
    }
    static boolean learns(Map<String,Object> value) {return !value.get("operation").equals("ORDER");}
    static boolean orders(Map<String,Object> value) {return !value.get("operation").equals("LEARN");}
    static Map<String,Object> sourceParameters(Map<String,Object> raw) throws IOException {
        var allowed=new HashSet<>(COMMON);allowed.addAll(SCIENTIFIC);allowed.addAll(Monocle100NeighborsContracts.CAP_NAMES);allowed.remove("maximum_rank_entries");keys(raw,allowed,COMMON);
        for(String name:List.of("source_cds","runtime","out"))label(raw.get(name));return parameters(raw);
    }
    static Map<String,Object> parameters(Map<String,Object> raw) throws IOException {
        integer(raw.get("schema_version"),1,1);String operation=label(raw.get("operation"));require(Set.of("LEARN","ORDER","RUN").contains(operation),"Choose operation LEARN, ORDER or RUN explicitly");
        require("UMAP".equals(raw.get("reduction_slot")),"Original1.0.0 learn_graph/order_cells require literal UMAP; P021 UMAT is not silently renamed");
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("profile",GRAPH_PROFILE);result.put("operation",operation);result.put("reduction_slot","UMAP");result.put("seed",integer(raw.get("seed"),0,2147483647));result.put("dataset_provenance",label(raw.get("dataset_provenance")));
        if(!operation.equals("ORDER")) {
            for(String name:List.of("use_partition","close_loop")){require(raw.get(name) instanceof Boolean,"Declare boolean "+name);result.put(name,raw.get(name));}
            String policy=label(raw.get("convergence_policy"));require(Set.of("require","retain-original").contains(policy),"Declare convergence_policy require or retain-original");result.put("convergence_policy",policy);
            var controls=object(raw.get("learn_graph_control"));keys(controls,CONTROLS,Set.of());var checked=new LinkedHashMap<String,Object>();
            for(var row:controls.entrySet()) {
                String name=row.getKey();Object value=row.getValue();
                if(Set.of("orthogonal_proj_tip","prune_graph","scale").contains(name)){require(value instanceof Boolean,"Require boolean source graph control: "+name);checked.put(name,value);}
                else if(value==null){require(Set.of("ncenter","rann.k").contains(name),"NULL is supported only for explicit ncenter/rann.k source handling");checked.put(name,null);}
                else if(Set.of("ncenter","rann.k","maxiter").contains(name)){long number=integer(value,1,name.equals("ncenter")?9999:1000000);checked.put(name,number);}
                else{double number=finite(value);require(number>=0&&(!Set.of("eps","L1.sigma").contains(name)||number>0),"Require nonnegative source control, positive eps/L1.sigma: "+name);checked.put(name,number);}
            }
            result.put("learn_graph_control",checked);
            require(!raw.containsKey("graph_provenance"),"Learning operations produce their own graph; do not supply an ignored graph_provenance");
        }else {
            for(String name:List.of("learn_graph_control","use_partition","close_loop","convergence_policy"))require(!raw.containsKey(name),"ORDER does not refit graphs and accepts no ignored learning settings");result.put("graph_provenance",label(raw.get("graph_provenance")));
        }
        if(!operation.equals("LEARN")) {
            require(raw.containsKey("root_pr_nodes")^raw.containsKey("root_cells"),"ORDER/RUN require exactly one explicit principal-node or cell-root list; no interactive or inferred root");String name=raw.containsKey("root_pr_nodes")?"root_pr_nodes":"root_cells";var roots=new ArrayList<String>();
            for(Object value:list(raw.get(name))){require(roots.size()<9999,"Root capacity exceeded");roots.add(label(value));}require(!roots.isEmpty(),"Declare nonempty explicit roots");result.put(name,List.copyOf(roots));
        }else require(!raw.containsKey("root_pr_nodes")&&!raw.containsKey("root_cells"),"LEARN accepts no ignored roots and admits no pseudotime");
        var capacity=caps(raw).values();capacity.remove("maximum_rank_entries");result.putAll(capacity);return result;
    }
    static Request request(Path path) throws IOException {
        Path source=regular(path);var raw=read(source,1048576);var selected=sourceParameters(raw);Path cds=regular(resolve(source.getParent(),raw.get("source_cds"))),runtime=regular(resolve(source.getParent(),raw.get("runtime")));Path out=destination(resolve(source.getParent(),raw.get("out")),List.of(source,cds,runtime));return new Request(source,cds,runtime,out,selected,caps(raw));
    }
    static Map<String,Object> savedParameters(Path root) throws IOException {
        var raw=read(root.resolve("effective-request.json"),1048576);var selected=parameters(raw);require(GRAPH_PROFILE.equals(raw.get("profile"))&&raw.keySet().equals(selected.keySet()),"Unknown, missing or foreign saved graph setting");return selected;
    }
    static RuntimeConfig runtime(Path path) throws IOException {
        Path source=regular(path);var raw=read(source,1048576);var required=Set.of("schema_version","profile","python","rscript","r_home","native_library_path","r_libraries");keys(raw,required,required);integer(raw.get("schema_version"),1,1);require(GRAPH_PROFILE.equals(raw.get("profile")),"Select the exact original1.0.0 graph runtime profile");var libs=new ArrayList<Path>();for(Object value:list(raw.get("r_libraries")))libs.add(resolve(source.getParent(),value));require(!libs.isEmpty()&&libs.size()<=16,"Declare complete ordered original graph R libraries");return new RuntimeConfig(source,resolve(source.getParent(),raw.get("python")),resolve(source.getParent(),raw.get("rscript")),resolve(source.getParent(),raw.get("r_home")),resolve(source.getParent(),raw.get("native_library_path")),List.copyOf(libs));
    }
}
