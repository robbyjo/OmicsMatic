/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.singlecell.Monocle100Weights;
import org.jlinalg.singlecell.Monocle100Weights.PrincipalEdge;
import static org.jlinalg.cli.Monocle100NeighborsFiles.*;
import static org.jlinalg.cli.Monocle100NeighborsContracts.*;

/** R-free full-population projections and exact native selection/weight checks. */
final class Monocle100NeighborsAudit {
    private Monocle100NeighborsAudit() { }
    record Result(Monocle100Weights.Result weights,List<String> cells,Map<String,Object> report) { }
    static List<String> labels(Object value) throws IOException {
        var result=new ArrayList<String>();var seen=new HashSet<String>();for(Object item:list(value)){String id=label(item);require(seen.add(id),"Duplicate complete source axis label");result.add(id);}require(!result.isEmpty(),"Empty complete source axis");return List.copyOf(result);
    }
    static int[] integers(Object value,int maximum) throws IOException {
        var raw=list(value);int[] result=new int[raw.size()];for(int index=0;index<result.length;index++)result[index]=(int)integer(raw.get(index),0,maximum);return result;
    }
    static int[][] matrix(Object value,int maximum) throws IOException {
        var raw=list(value);int[][] result=new int[raw.size()][];for(int index=0;index<result.length;index++)result[index]=integers(raw.get(index),maximum);return result;
    }
    static List<?> namedRows(Object value,int count) throws IOException {
        var map=object(value);require(map.size()==count,"Different complete original neighbor-row population");var rows=new ArrayList<Object>();int index=1;
        for(var row:map.entrySet()){require(row.getKey().equals(Integer.toString(index++)),"Changed original named neighbor-row order");rows.add(row.getValue());}return rows;
    }
    static Result audit(Path root,Map<String,Object> parameters,Caps caps) throws IOException {
        Path worker=root.resolve("worker");var report=read(worker.resolve("report.json"),caps.jsonBytes());
        require("PASS_ORIGINAL_RANN_NEIGHBORS".equals(report.get("status"))&&PROFILE.equals(report.get("profile"))&&"4.3.3".equals(report.get("R"))&&"x86_64-conda-linux-gnu".equals(report.get("platform")),"Different original runtime report/profile");
        for(String key:List.of("engine","neighbor_graph","reduction_slot"))require(parameters.get(key).equals(report.get(key)),"Changed original semantic field: "+key);
        for(String key:List.of("seed","k"))require(integer(parameters.get(key),0,2147483647)==integer(report.get(key),0,2147483647),"Changed original numerical setting: "+key);
        require(Boolean.TRUE.equals(report.get("input_projection_unchanged"))&&Boolean.TRUE.equals(report.get("RNG_unchanged"))&&Boolean.FALSE.equals(report.get("scientific_test_admitted")),"Original input/RNG/test admission scope differs");
        boolean original=parameters.get("engine").equals("original");require(Boolean.valueOf(original).equals(report.get("original_calculateLW_executed"))&&Boolean.valueOf(original).equals(report.get("original_listw_roundtrip_identical")),"Original/native engine was silently substituted");
        var profile=object(NextflowParameters.parse(NextflowFiles.utf8(resource("PROFILE.json")),1_048_576,100_000));require(object(profile.get("guards")).equals(object(report.get("guards"))),"Original function guard population differs");
        var before=object(report.get("modules_before"));var after=object(report.get("modules_after"));
        require(!before.isEmpty()&&after.keySet().containsAll(before.keySet()),"Missing complete original native-module identities");
        for(var row:before.entrySet()) {
            var a=object(row.getValue());var b=object(after.get(row.getKey()));require(a.equals(b),"Original native-module identity changed");
            keys(a,Set.of("path","bytes","sha256"),Set.of("path","bytes","sha256"));label(a.get("path"));integer(a.get("bytes"),0,536870912);require(text(a.get("sha256")).matches("[a-f0-9]{64}"),"Invalid native-module fingerprint");
        }
        var projection=read(worker.resolve("input-projection.json"),caps.jsonBytes());List<String> cells=labels(projection.get("cell_ids")),genes=labels(projection.get("gene_ids"));int n=cells.size();
        require(n<=caps.cells()&&integer(report.get("source_cds_cells"),1,1000000)==n&&integer(report.get("source_cds_genes"),1,Integer.MAX_VALUE)==genes.size(),"Changed complete source CDS population");
        require(parameters.get("reduction_slot").equals(projection.get("reduction_slot")),"Changed literal embedding slot; no UMAT/UMAP rename is inferred");
        int[] dimensions=integers(projection.get("count_dimensions"),Integer.MAX_VALUE);require(dimensions.length==2&&dimensions[0]==genes.size()&&dimensions[1]==n,"Changed source count dimensions");
        var input=read(worker.resolve("prepared-neighbors.json"),caps.jsonBytes());require(Monocle100WeightsCli.PROFILE.equals(input.get("source_profile"))&&parameters.get("neighbor_graph").equals(input.get("neighbor_graph"))&&cells.equals(labels(input.get("cell_ids"))),"Changed complete native source projection");integer(input.get("schema_version"),1,1);label(input.get("ranking_provenance"));
        var nearest=read(worker.resolve("original-nearest.json"),caps.jsonBytes());keys(nearest,Set.of("indices_zero_based","distance_tokens"),Set.of("indices_zero_based","distance_tokens"));
        int[][] ranking=matrix(input.get("original_ranking_zero_based"),n-1);int[][] originalRanking=matrix(nearest.get("indices_zero_based"),n-1);
        require(ranking.length==n&&java.util.Arrays.deepEquals(ranking,originalRanking),"Original RANN/native ranking differs");
        int columns=Math.min((int)integer(parameters.get("k"),1,1000000)+1,n);require((long)n*columns<=caps.ranks(),"Complete original ranking capacity exceeded");
        var coordinates=list(projection.get("coordinates"));var distances=list(nearest.get("distance_tokens"));require(coordinates.size()==n&&distances.size()==n,"Changed original coordinate/distance population");
        int dims=list(coordinates.get(0)).size();require(dims>0,"Empty selected embedding dimensions");long distanceOperations=Math.multiplyExact((long)n*columns,dims);
        require(distanceOperations<caps.nativeOperations(),"Complete selected-distance audit exceeds native operation capacity; no sampled audit is admitted");
        double[][] values=new double[n][dims];
        for(int index=0;index<n;index++){var row=list(coordinates.get(index));require(row.size()==dims,"Ragged complete embedding");for(int column=0;column<dims;column++)values[index][column]=finite(row.get(column));}
        double maximumDifference=0;
        for(int from=0;from<n;from++) {
            require(ranking[from].length==columns,"Changed full original ranking width");var row=list(distances.get(from));require(row.size()==columns,"Changed full original distance width");
            for(int column=0;column<columns;column++) {
                int to=ranking[from][column];double sum=0;for(int dimension=0;dimension<dims;dimension++){double difference=values[from][dimension]-values[to][dimension];sum+=difference*difference;}
                double got=Math.sqrt(sum),expected=finite(row.get(column)),difference=Math.abs(got-expected);require(expected>=0&&Double.isFinite(got)&&difference<=2e-12*Math.max(1,Math.abs(expected)),"Original selected Euclidean distance projection differs");maximumDifference=Math.max(maximumDifference,difference);
            }
        }
        String graph=text(parameters.get("neighbor_graph"));Monocle100Weights.Result weights;
        var inputNames=new HashSet<>(Set.of("schema_version","source_profile","neighbor_graph","ranking_provenance","cell_ids","original_ranking_zero_based"));
        var projectionNames=new HashSet<>(Set.of("cell_ids","gene_ids","count_dimensions","reduction_slot","coordinates"));
        if(graph.equals("principal_graph")) {
            inputNames.addAll(Set.of("principal_ids","cell_to_principal_zero_based","principal_edges_zero_based","principal_graph_provenance"));projectionNames.addAll(Set.of("principal_ids","principal_coordinates","selected_membership_zero_based","native_principal_edges_zero_based","raw_projection_rows","raw_graph_vertex_ids"));
            List<String> points=labels(input.get("principal_ids"));require(points.equals(labels(projection.get("principal_ids"))),"Changed principal-point source order");label(input.get("principal_graph_provenance"));
            int[] memberships=integers(input.get("cell_to_principal_zero_based"),points.size()-1);require(java.util.Arrays.equals(memberships,integers(projection.get("selected_membership_zero_based"),points.size()-1)),"Changed selected principal memberships");
            int[][] edges=matrix(input.get("principal_edges_zero_based"),points.size()-1);require(java.util.Arrays.deepEquals(edges,matrix(projection.get("native_principal_edges_zero_based"),points.size()-1)),"Changed principal-edge source projection");
            var principalEdges=new ArrayList<PrincipalEdge>();for(int[] edge:edges){require(edge.length==2,"Invalid complete principal edge");principalEdges.add(new PrincipalEdge(edge[0],edge[1]));}
            List<String> rawVertices=labels(projection.get("raw_graph_vertex_ids"));require(rawVertices.containsAll(points),"Principal point is absent from complete source graph");require(labels(projection.get("raw_projection_rows")).containsAll(cells),"Source closest-vertex projection omits cells");
            var pointRows=list(projection.get("principal_coordinates"));require(!pointRows.isEmpty(),"Missing complete principal coordinates");for(Object row:pointRows)require(list(row).size()==points.size(),"Changed complete principal coordinate dimensions");
            weights=Monocle100Weights.principalGraph(ranking,memberships,points.size(),principalEdges,caps.nativeOperations()-distanceOperations);
        }else weights=Monocle100Weights.knn(ranking,caps.nativeOperations()-distanceOperations);
        keys(input,inputNames,inputNames);keys(projection,projectionNames,projectionNames);
        if(original) {
            var source=read(worker.resolve("original-listw.json"),caps.jsonBytes());keys(source,Set.of("style","region_ids","classes","neighbors_zero_based","weights_exact_tokens"),Set.of("style","region_ids","classes","neighbors_zero_based","weights_exact_tokens"));
            require("W".equals(source.get("style"))&&cells.equals(labels(source.get("region_ids")))&&list(source.get("classes")).equals(List.of("listw","nb")),"Original complete listw style/classes/region IDs differ");
            var rows=namedRows(source.get("neighbors_zero_based"),n);var tokens=list(source.get("weights_exact_tokens"));require(tokens.size()==n,"Changed complete original weight-row count");int index=0;
            for(int from=0;from<n;from++) {
                int[] neighbors=integers(rows.get(from),n-1);var expected=list(tokens.get(from));require(neighbors.length==weights.neighbors().get(from).size()&&neighbors.length==expected.size(),"Native/original neighbor population differs");
                for(int column=0;column<neighbors.length;column++) {
                    require(neighbors[column]==weights.neighbors().get(from).get(column),"Native/original neighbor ordering differs");var arc=weights.arcs().get(index++);
                    require(Double.doubleToRawLongBits(arc.weight())==Double.doubleToRawLongBits(finite(expected.get(column))),"Original/native binary64 weight differs");
                }
            }
            require(index==weights.arcs().size(),"Changed complete original arc population");
        }else require(!Files.exists(worker.resolve("original-listw.json"))&&!Files.exists(worker.resolve("original-listw.rds")),"Java engine cannot claim an original calculateLW return");
        var summary=new LinkedHashMap<String,Object>();summary.put("engine",parameters.get("engine"));summary.put("cells",n);summary.put("genes_in_source_cds",genes.size());summary.put("arcs",weights.arcs().size());summary.put("isolated_rows",weights.isolatedRows());summary.put("self_links",weights.selfLinks());summary.put("complete_selected_distance_fields",(long)n*columns);summary.put("selected_distance_absolute_relative_tolerance",2e-12);summary.put("maximum_selected_distance_absolute_difference",maximumDifference);summary.put("native_logical_operations",distanceOperations+weights.operations());summary.put("original_weights_binary64_identical",original);summary.put("nearest_neighbor_optimality_refit",false);summary.put("RDS_reparsed_by_Java",false);summary.put("scientific_test_admitted",false);
        return new Result(weights,cells,summary);
    }
    static void export(Path root,Result result) throws IOException {
        Path out=Files.createDirectory(root.resolve("results"));var arcs=new ArrayList<Map<String,Object>>();var table=new StringBuilder("from_cell\tto_cell\tweight\n");
        for(var arc:result.weights().arcs()){arcs.add(Map.of("from",arc.from(),"to",arc.to(),"weight",arc.weight()));table.append(result.cells().get(arc.from())).append('\t').append(result.cells().get(arc.to())).append('\t').append(arc.weight()).append('\n');}
        json(out.resolve("weights.json"),arcs);write(out.resolve("weights.tsv"),table.toString());json(out.resolve("audit.json"),result.report());
    }
}
