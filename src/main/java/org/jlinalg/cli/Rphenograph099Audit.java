/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.RphenographJaccard099;
import static org.jlinalg.cli.Monocle131ModelRequest.*;

/** Complete coefficient and graph projection checks; no nearest-neighbor or Louvain substitution. */
final class Rphenograph099Audit {
    private Rphenograph099Audit() { }
    static Map<String,Object> audit(Map<String,Object> m,Path folder,long operations,long bytes)throws IOException {
        integer(m.get("schema_version"),1,1);require(text(m.get("version")).equals("0.99.1")&&text(m.get("source_commit")).equals("0298487f0ee13aac55eb77d19992f6bd878ba2fc"),"Original edition/source identity differs");
        require(bool(m.get("complete_model_roundtrip"))&&!bool(m.get("paper_package_identity_recovered")),"Unsupported original model/paper identity scope");
        var cells=labels(m.get("cell_ids"),10000000);var features=labels(m.get("feature_ids"),1000000);int n=cells.size(),k=(int)integer(m.get("k"),1,1000000);require(k<=n-2,"Original k must be <= cells-2");
        long count=(long)n*k;require(count<=Integer.MAX_VALUE&&2*count*k<=operations,"Complete native coefficient capacity exceeded");
        var pins=new LinkedHashMap<Path,NextflowFiles.Pin>();long total=0;for(String name:List.of("neighbors-full.tsv","coefficients-raw.tsv","graph-vertices.tsv","graph-edges.tsv","cell-assignments.tsv")){Path p=folder.resolve(name);var pin=NextflowFiles.pin(p,bytes,()->{});total=Math.addExact(total,pin.bytes());require(total<=bytes,"Complete audit table byte capacity exceeded");pins.put(p,pin);}
        int[] used=new int[(int)count];long self=0;
        try(var r=Files.newBufferedReader(folder.resolve("neighbors-full.tsv"),StandardCharsets.UTF_8)){
            StringBuilder h=new StringBuilder("cell_id");for(int j=1;j<=k+1;j++)h.append("\tneighbor_").append(j);require(h.toString().equals(r.readLine()),"Original full-neighbor header differs");
            for(int i=0;i<n;i++){String[] f=row(r.readLine(),k+2);require(f[0].equals(cells.get(i)),"Original neighbor cell order differs");for(int j=1;j<=k+1;j++){int index=index(f[j],n);if(j>1){used[i*k+j-2]=index;if(index==i+1)self++;}}}require(r.readLine()==null,"Excess original neighbor rows");
        }
        var nativeWeights=RphenographJaccard099.compute(n,k,used,operations);
        try(var r=Files.newBufferedReader(folder.resolve("coefficients-raw.tsv"),StandardCharsets.UTF_8)){
            require("from\tto\tweight".equals(r.readLine()),"Original coefficient header differs");for(int i=0;i<nativeWeights.rawRows();i++){var f=row(r.readLine(),3);require(indexOrZero(f[0],n)==nativeWeights.fromOneBased(i)&&indexOrZero(f[1],n)==nativeWeights.toOneBased(i)&&bits(f[2])==Double.doubleToLongBits(nativeWeights.weight(i)),"Complete original/native coefficient differs at row "+(i+1));}require(r.readLine()==null,"Excess original coefficient rows");
        }
        // graph.data.frame obtains vertex order from all 'from' endpoints followed by all 'to' endpoints.
        var order=new LinkedHashSet<Integer>();for(int i=0;i<nativeWeights.positiveRows();i++)order.add(nativeWeights.fromOneBased(i));for(int i=0;i<nativeWeights.positiveRows();i++)order.add(nativeWeights.toOneBased(i));var vertices=new ArrayList<>(order);var position=new HashMap<Integer,Integer>();var names=new HashMap<Integer,String>();var membership=new HashMap<Integer,Integer>();
        try(var r=Files.newBufferedReader(folder.resolve("graph-vertices.tsv"),StandardCharsets.UTF_8)){
            require("vertex_number\tvertex_name\tsource_cell_row\tcell_id\tmembership".equals(r.readLine()),"Original vertex header differs");for(int i=0;i<vertices.size();i++){var f=row(r.readLine(),5);int source=vertices.get(i);require(index(f[0],vertices.size())==i+1&&index(f[1],n)==source&&index(f[2],n)==source&&f[3].equals(cells.get(source-1)),"Original vertex order/name/literal cell projection differs");names.put(source,f[1]);position.put(source,i+1);membership.put(source,index(f[4],Math.max(1,vertices.size())));}require(r.readLine()==null,"Excess original vertices");
        }
        try(var r=Files.newBufferedReader(folder.resolve("graph-edges.tsv"),StandardCharsets.UTF_8)){
            require("from\tto\tweight".equals(r.readLine()),"Original edge header differs");for(int i=0;i<nativeWeights.positiveRows();i++){var f=row(r.readLine(),3);int a=nativeWeights.fromOneBased(i),b=nativeWeights.toOneBased(i);if(position.get(a)>position.get(b)){int swap=a;a=b;b=swap;}require(f[0].equals(names.get(a))&&f[1].equals(names.get(b))&&bits(f[2])==Double.doubleToLongBits(nativeWeights.weight(i)),"Original complete multigraph edge/order/half-weight differs");}require(r.readLine()==null,"Excess original graph edges");
        }
        try(var r=Files.newBufferedReader(folder.resolve("cell-assignments.tsv"),StandardCharsets.UTF_8)){
            require("cell_id\tvertex_number\tmembership".equals(r.readLine()),"Complete cell-assignment header differs");for(int i=0;i<n;i++){var f=row(r.readLine(),3);require(f[0].equals(cells.get(i)),"Complete assignment cell order differs");Integer p=position.get(i+1);if(p==null)require(f[1].equals("NA")&&f[2].equals("NA"),"Original omitted vertex acquired an invented assignment");else require(index(f[1],vertices.size())==p&&index(f[2],vertices.size())==membership.get(i+1),"Original vertex membership differs");}require(r.readLine()==null,"Excess complete assignments");
        }
        require(integer(m.get("graph_vertices"),0,n)==vertices.size()&&integer(m.get("graph_edges"),0,count)==nativeWeights.positiveRows()&&!bool(m.get("directed")),"Original graph population/direction differs");
        String modularity=text(m.get("modularity"));double q;try{q=Double.parseDouble(modularity);}catch(NumberFormatException e){throw new IOException("Invalid original modularity token",e);}boolean reportable=!vertices.isEmpty()&&Double.isFinite(q);require(bool(m.get("reportable_clustering"))==reportable,"False clustering reportability claim");
        for(var e:pins.entrySet())require(NextflowFiles.pin(e.getKey(),bytes,()->{}).equals(e.getValue()),"Audit table changed during complete read");
        var a=new LinkedHashMap<String,Object>();a.put("status","PASS_COMPLETE_RPHENOGRAPH099_COEFFICIENT_AND_MULTIGRAPH_CONSISTENCY");a.put("complete_cells",n);a.put("complete_features",features.size());a.put("raw_coefficient_rows",nativeWeights.rawRows());a.put("positive_half_weight_events",nativeWeights.positiveRows());a.put("trailing_zero_rows",nativeWeights.zeroPaddedRows());a.put("retained_self_neighbor_events",self);a.put("graph_vertices",vertices.size());a.put("unassigned_input_cells",n-vertices.size());a.put("reportable_clustering",reportable);a.put("original_modularity_token",modularity);a.put("nearest_neighbor_search_repeated",false);a.put("Louvain_clustering_repeated",false);a.put("RDS_reloaded",false);a.put("producer_authenticated",false);a.put("original_analysis_admitted",false);a.put("paper_package_identity_recovered",false);a.put("whole_goal_complete",false);return a;
    }
    private static String[] row(String s,int n)throws IOException{require(s!=null,"Incomplete original output population");String[] f=s.split("\t",-1);require(f.length==n,"Complete output column population differs");return f;}
    private static int index(String s,int n)throws IOException{int i=indexOrZero(s,n);require(i>0,"Require a positive exact source row index");return i;}
    private static int indexOrZero(String s,int n)throws IOException{double x=Rphenograph099Inputs.numeric(s);require(x>=0&&x<=n&&x==Math.rint(x),"Require an exact original integer index");return (int)x;}
    private static long bits(String s)throws IOException{return Double.doubleToLongBits(Rphenograph099Inputs.numeric(s));}
}
