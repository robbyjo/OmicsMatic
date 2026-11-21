/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.singlecell.Monocle3Pseudotime;
import org.jlinalg.cli.Monocle100NeighborsContracts.Caps;
import static org.jlinalg.cli.Monocle100NeighborsFiles.*;
import static org.jlinalg.cli.Monocle100GraphFiles.*;
import static org.jlinalg.cli.Monocle100GraphContracts.GRAPH_PROFILE;

/** R-free complete source projections and independent Java geodesic ordering comparison. */
final class Monocle100GraphAudit {
    private Monocle100GraphAudit() { }
    record Result(Map<String,Object> summary,List<String> cells,Monocle3Pseudotime.Result ordering) { }
    private static double[][] coordinates(Object value,int rows,int dimensions) throws IOException {
        var raw=list(value);require(raw.size()==rows,"Complete coordinate row population differs");double[][] result=new double[rows][];int width=dimensions;
        for(int i=0;i<rows;i++){var columns=list(raw.get(i));if(width<0)width=columns.size();require(width>0&&width<=1024&&columns.size()==width,"Complete coordinate dimensions differ");result[i]=new double[width];for(int j=0;j<width;j++)result[i][j]=finite(columns.get(j));}return result;
    }
    private static void tokens(double[] values,Object raw) throws IOException {
        var wanted=list(raw);require(values.length==wanted.size(),"Complete original pseudotime population differs");
        for(int i=0;i<values.length;i++) {
            String token=text(wanted.get(i));double expected=token.equals("Inf")?Double.POSITIVE_INFINITY:token.equals("-Inf")?Double.NEGATIVE_INFINITY:finite(token);
            if(!Double.isFinite(expected)||!Double.isFinite(values[i]))require(expected==values[i],"Original/native infinite pseudotime differs");else require(Math.abs(values[i]-expected)<=1e-12+1e-11*Math.abs(expected),"Original/native finite pseudotime differs");
        }
    }
    private static boolean convergence(Path root,Map<String,Object> parameters,Caps caps,Map<String,Object> report) throws IOException {
        var saved=read(root.resolve("worker/convergence.json"),caps.jsonBytes());require(NextflowJson.json(saved).equals(NextflowJson.json(report.get("convergence"))),"Saved/report convergence fields differ");boolean learns=Monocle100GraphContracts.learns(parameters);require(Boolean.valueOf(learns).equals(saved.get("applicable")),"Convergence applicability differs");
        if(!learns){require("caller-prepared-graph-not-refitted".equals(saved.get("policy"))&&saved.get("all_converged")==null&&list(saved.get("partitions")).isEmpty(),"ORDER cannot claim a new convergence result");return false;}
        require(parameters.get("convergence_policy").equals(saved.get("policy")),"Changed graph convergence policy");var parts=list(saved.get("partitions"));require(!parts.isEmpty(),"Missing complete graph objective histories");boolean all=true;double eps=1e-5;var controls=object(parameters.get("learn_graph_control"));if(controls.containsKey("eps"))eps=finite(controls.get("eps"));
        for(Object raw:parts){var part=object(raw);var values=list(part.get("objective_tokens"));require(!values.isEmpty()&&integer(part.get("iterations"),1,1000000)==values.size(),"Changed original iteration population");for(Object token:values)finite(token);require(Double.doubleToLongBits(finite(part.get("eps_token")))==Double.doubleToLongBits(eps),"Changed original convergence tolerance");double relative=Double.NaN;if(values.size()>1){double previous=finite(values.get(values.size()-2));relative=Math.abs(previous-finite(values.get(values.size()-1)))/Math.abs(previous);}boolean success=Double.isFinite(relative)&&relative<eps;require(Boolean.valueOf(success).equals(part.get("converged")),"Original objective convergence classification differs");if(Double.isFinite(relative))require(Math.abs(finite(part.get("last_relative_change"))-relative)<=2e-15*Math.max(1,Math.abs(relative)),"Original convergence relative change differs");else require(part.get("last_relative_change")==null,"Nonfinite original relative change was repaired");all&=success;}
        if(!list(saved.get("kmeans_nonzero_ifault_messages")).isEmpty())all=false;require(Boolean.valueOf(all).equals(saved.get("all_converged")),"Complete convergence status differs");require(all||parameters.get("convergence_policy").equals("retain-original"),"Unconverged graph cannot be admitted under require policy");return all;
    }
    static Result audit(Path root,Map<String,Object> parameters,Caps caps) throws IOException {
        var report=read(root.resolve("worker/report.json"),caps.jsonBytes());boolean learns=Monocle100GraphContracts.learns(parameters),orders=Monocle100GraphContracts.orders(parameters);
        require("PASS_ORIGINAL100_COMPLETE_GRAPH_OPERATION".equals(report.get("status"))&&GRAPH_PROFILE.equals(report.get("profile"))&&"4.3.3".equals(report.get("R"))&&"x86_64-conda-linux-gnu".equals(report.get("platform")),"Foreign/incomplete original graph runtime report");require(parameters.get("operation").equals(report.get("operation"))&&integer(parameters.get("seed"),0,2147483647)==integer(report.get("seed"),0,2147483647),"Original graph operation/seed differs");
        for(String key:List.of("input_invariants_preserved","output_RDS_scientific_roundtrip_identical"))require(Boolean.TRUE.equals(report.get(key)),"Incomplete original scientific state: "+key);
        require(Boolean.FALSE.equals(report.get("scientific_test_admitted"))&&Boolean.FALSE.equals(report.get("root_direction_biologically_verified"))&&Boolean.valueOf(learns).equals(report.get("graph_learned_by_this_run"))&&Boolean.valueOf(orders).equals(report.get("pseudotime_computed")),"Different original graph/ordering/inference scope");
        require(list(graphProfile().get("guarded_functions")).equals(list(report.get("guarded_functions"))),"Original graph function guard population differs");
        for(String prefix:List.of("modules","math")){var before=object(report.get(prefix+"_before"));var after=object(report.get(prefix+"_after"));require(!before.isEmpty()&&after.keySet().containsAll(before.keySet()),"Missing selected native/math library identities");for(var row:before.entrySet())require(row.getValue().equals(after.get(row.getKey())),"Original native/math module identity changed");}
        boolean converged=convergence(root,parameters,caps,report);var input=read(root.resolve("worker/input-projection.json"),caps.jsonBytes());List<String> cells=graphLabels(input.get("cell_ids")),genes=graphLabels(input.get("gene_ids"));int n=cells.size();require(n<=caps.cells()&&integer(report.get("cells"),1,1000000)==n&&integer(report.get("genes"),1,Integer.MAX_VALUE)==genes.size(),"Complete original source population differs");
        require("UMAP".equals(input.get("reduction_slot")),"Literal UMAP projection differs; no UMAT rename is inferred");double[][] cellCoordinates=coordinates(input.get("coordinates"),n,-1);var countDims=list(input.get("count_dimensions"));require(countDims.size()==2&&integer(countDims.get(0),1,Integer.MAX_VALUE)==genes.size()&&integer(countDims.get(1),1,1000000)==n,"Complete count dimensions differ");
        require(text(input.get("invariants_sha256")).matches("[a-f0-9]{64}")&&input.get("invariants_sha256").equals(report.get("input_invariants_sha256"))&&text(report.get("output_scientific_state_sha256")).matches("[a-f0-9]{64}"),"Saved scientific state hash projection differs");
        for(String prefix:List.of("cluster","partition")){List<String> levels=graphLabels(input.get(prefix+"_levels"));var values=list(input.get(prefix+"_values"));require(values.size()==n,"Complete source factor population differs");for(Object value:values)require(levels.contains(label(value)),"Source factor value is outside its literal levels");}
        var graph=read(root.resolve("worker/principal-graph.json"),caps.jsonBytes());List<String> points=graphLabels(graph.get("principal_ids")),vertices=graphLabels(graph.get("graph_vertex_ids"));require(new java.util.HashSet<>(points).equals(new java.util.HashSet<>(vertices))&&integer(report.get("principal_points"),1,1000000)==points.size(),"Complete original principal axes differ");double[][] principalCoordinates=coordinates(graph.get("principal_coordinates"),points.size(),cellCoordinates[0].length);
        String graphMode=text(graph.get("edge_weight_mode"));require(Set.of("weighted","unweighted").contains(graphMode),"Declare original principal weight mode");var principalEdges=list(graph.get("edges"));long unusualWeights=0;
        for(Object raw:principalEdges){var edge=object(raw);var edgeNames=graphMode.equals("weighted")?Set.of("from","to","weight"):Set.of("from","to");keys(edge,edgeNames,edgeNames);integer(edge.get("from"),0,points.size()-1);integer(edge.get("to"),0,points.size()-1);if(graphMode.equals("weighted")){String token=text(edge.get("weight"));if(Set.of("NA","NaN","Inf","-Inf").contains(token))unusualWeights++;else if(finite(token)<0)unusualWeights++;}}
        Monocle3Pseudotime.Result result=null;
        if(orders){
            var prepared=read(root.resolve("worker/prepared-ordering.json"),caps.jsonBytes());require(cells.equals(graphLabels(prepared.get("cell_ids")))&&points.equals(graphLabels(prepared.get("principal_ids"))),"Original ordering/source axes differ");require(java.util.Arrays.deepEquals(cellCoordinates,coordinates(prepared.get("cell_coordinates"),n,cellCoordinates[0].length))&&java.util.Arrays.deepEquals(principalCoordinates,coordinates(prepared.get("principal_coordinates"),points.size(),cellCoordinates[0].length)),"Original ordering/source coordinates differ");
            var roots=new ArrayList<String>();for(Object value:list(prepared.get("roots")))roots.add(label(value));require(!roots.isEmpty(),"Original ordering returned no explicit roots");var expectedRoots=new ArrayList<String>();
            if(parameters.containsKey("root_pr_nodes"))for(Object value:list(parameters.get("root_pr_nodes")))expectedRoots.add(label(value));
            else{var closest=list(prepared.get("closest_vertex_source_labels"));require(closest.size()==n,"Complete source closest-vertex axis differs");var unique=new LinkedHashSet<String>();for(Object value:list(parameters.get("root_cells"))){int index=cells.indexOf(label(value));require(index>=0,"Declared root cell is absent");unique.add("Y_"+label(closest.get(index)));}expectedRoots.addAll(unique);}
            require(roots.equals(expectedRoots),"Original root mapping differs from explicit request/source projection");List<String> projectedVertices=graphLabels(prepared.get("graph_vertex_ids"));String mode=text(prepared.get("edge_weight_mode"));require(Set.of("weighted","unweighted").contains(mode),"Declare projected graph edge weight mode");var edges=new ArrayList<Monocle3Pseudotime.Edge>();
            for(Object raw:list(prepared.get("edges"))){var edge=object(raw);var names=mode.equals("weighted")?Set.of("from","to","weight"):Set.of("from","to");keys(edge,names,names);int from=(int)integer(edge.get("from"),0,projectedVertices.size()-1),to=(int)integer(edge.get("to"),0,projectedVertices.size()-1);double weight=mode.equals("weighted")?finite(edge.get("weight")):1;require(weight>=0,"Negative projected graph edge weight");edges.add(new Monocle3Pseudotime.Edge(from,to,weight));}
            result=Monocle3Pseudotime.order(cells,cellCoordinates,points,principalCoordinates,roots,projectedVertices,edges,caps.nativeOperations());tokens(result.pseudotime(),prepared.get("expected_cell_pseudotime"));tokens(result.graphPseudotime(),prepared.get("expected_graph_pseudotime"));var nearest=list(prepared.get("expected_root_indices"));require(nearest.size()==result.selectedRoots().size(),"Complete source root-nearest population differs");for(int i=0;i<nearest.size();i++)require(integer(nearest.get(i),0,n-1)==result.selectedRoots().get(i).zeroBasedCellIndex(),"Original first-nearest-cell root differs");
        }else require(!Files.exists(root.resolve("worker/prepared-ordering.json"))&&!Files.exists(root.resolve("worker/original-ordering.rds")),"LEARN cannot claim ordering or pseudotime");
        var summary=new LinkedHashMap<String,Object>();summary.put("operation",parameters.get("operation"));summary.put("cells",n);summary.put("genes_in_source_cds",genes.size());summary.put("principal_points",points.size());summary.put("principal_edges",principalEdges.size());summary.put("principal_weight_metadata_unusual_tokens",unusualWeights);summary.put("graph_learned_by_this_run",learns);summary.put("pseudotime_computed",orders);summary.put("objective_convergence_verified",converged);summary.put("native_ordering_operations",result==null?0:result.operations());summary.put("infinite_cells",result==null?0:result.infiniteCellCount());summary.put("RDS_reparsed_by_Java",false);summary.put("graph_learning_refit_by_VERIFY",false);summary.put("root_direction_biologically_verified",false);summary.put("scientific_test_admitted",false);return new Result(summary,cells,result);
    }
    static String table(Result value) {
        var out=new StringBuilder("cell_id\tpseudotime\n");double[] times=value.ordering().pseudotime();for(int i=0;i<times.length;i++)out.append(value.cells().get(i)).append('\t').append(times[i]==Double.POSITIVE_INFINITY?"Inf":Double.toString(times[i])).append('\n');return out.toString();
    }
    static String principalTable(Path root,int capacity) throws IOException {
        var graph=read(root.resolve("worker/principal-graph.json"),capacity);List<String> points=graphLabels(graph.get("principal_ids"));var coordinates=list(graph.get("principal_coordinates"));int[] degree=new int[points.size()];
        for(Object raw:list(graph.get("edges"))){var edge=object(raw);degree[(int)integer(edge.get("from"),0,points.size()-1)]++;degree[(int)integer(edge.get("to"),0,points.size()-1)]++;}
        int dimensions=list(coordinates.get(0)).size();var table=new StringBuilder("principal_id\tdegree");for(int dimension=0;dimension<dimensions;dimension++)table.append("\tUMAP_").append(dimension+1);table.append('\n');
        for(int i=0;i<points.size();i++){table.append(points.get(i)).append('\t').append(degree[i]);for(Object token:list(coordinates.get(i)))table.append('\t').append(text(token));table.append('\n');}return table.toString();
    }
    static void export(Path root,Result result) throws IOException {
        Path out=Files.createDirectory(root.resolve("results"));json(out.resolve("audit.json"),result.summary());Files.copy(root.resolve("worker/principal-graph.json"),out.resolve("principal-graph.json"));
        Files.writeString(out.resolve("principal-nodes.tsv"),principalTable(root,268435456),StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
        if(result.ordering()!=null)Files.writeString(out.resolve("pseudotime.tsv"),table(result),StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
