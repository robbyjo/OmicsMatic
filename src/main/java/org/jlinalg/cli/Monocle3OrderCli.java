/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.Monocle3Pseudotime;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Native prepared-graph ordering; never estimates an embedding, graph or biological root. */
public final class Monocle3OrderCli {
    private Monocle3OrderCli() { }
    static final String SCOPE="Native Monocle3 prepared-graph ordering for original helper source profiles 1.0.0, 1.3.1 and 1.4.27. Each principal root selects the first nearest cell in input order; pseudotime is the minimum weighted undirected graph distance from those cells. Original naive Euclidean overflow/underflow ties and Inf are preserved. This does not learn an embedding/graph, infer biological roots, fit gene models or reproduce an entire package/paper. Logical byte/operation capacities are not an OS heap quota.";
    static final String ACTION="Read WARNINGS.md and analysis-status.json; require result_admitted=true before using results. Preserve the original input graph, coordinates, roots, weights and every failed attempt. FAIL2 admits no partial result. Correct the named input/domain/capacity cause and retry NEW paths; never repair IDs, rescale coordinates, replace Inf/NA or drop disconnected cells to force success. One-dimensional inputs with multiple principal-root selections are refused because the original helper fails in that domain. No R installation or portable VERIFY command is required/provided for ORDER.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        try {
            if(args.length==1&&args[0].equals("--help")) {
                MonocleToolsCli.emit(output,"monocle-tools ORDER --request ASCII_PATH.json\nRequire schema_version 1, mode ORDER, source_profile 1.0.0/1.3.1/1.4.27, prepared_graph UTF-8 JSON path and NEW out. Paths resolve against the request. Optional maximum_graph_bytes defaults to 64 MiB (maximum 256 MiB); maximum_native_operations defaults to 50 million.\nGraph keys: cell_ids, cell_coordinates, principal_ids, principal_coordinates, roots, graph_vertex_ids, edge_weight_mode and edges. Declare weighted with finite nonnegative edge weights, or unweighted with no weight fields (unit edges). Endpoints are zero-based graph vertex indices. Literal unique IDs and both axis orders are retained; roots may repeat. No expected-output fields or implicit graph/weight inference.\n"+SCOPE+"\n"+ACTION);return 0;
            }
            require(args.length==2&&args[0].equals("--request"),"Require --request UTF8_JSON or --help");
            Path requestPath=Path.of(args[1]).toAbsolutePath().normalize();var requestPin=NextflowFiles.pin(requestPath,1048576,()->{});byte[] raw=NextflowFiles.bytes(requestPath,1048576);
            require(requestPin.bytes()==raw.length&&requestPin.sha256().equals(NextflowFiles.sha(raw)),"Request changed during intake");
            var m=object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000));allowed(m,"schema_version","mode","source_profile","prepared_graph","out","maximum_graph_bytes","maximum_native_operations");
            integer(m.get("schema_version"),1,1);require(text(m.get("mode")).equals("ORDER"),"Require mode ORDER");String profile=text(m.get("source_profile"));require(Set.of("1.0.0","1.3.1","1.4.27").contains(profile),"Choose an exact admitted helper source profile: 1.0.0, 1.3.1 or 1.4.27");
            int bytes=(int)number(m,"maximum_graph_bytes",67108864,1,268435456);long operations=number(m,"maximum_native_operations",50000000,1,1000000000);
            Path graph=resolve(requestPath.getParent(),label(m.get("prepared_graph"))),out=resolve(requestPath.getParent(),label(m.get("out")));int[] exit={0};
            FollowupSupport.externalTransaction(Map.of("out",out.toString()),"native-monocle3-prepared-order-development",(stage,manifest)->{
                var warnings=new ArrayList<String>();warnings.add(SCOPE);var state=new LinkedHashMap<String,Object>();state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("result_admitted",false);
                try {
                    verify(requestPath,requestPin);Files.write(stage.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
                    var effective=new LinkedHashMap<String,Object>(m);effective.put("prepared_graph",graph.toString());effective.put("out",out.toString());effective.put("maximum_graph_bytes",bytes);effective.put("maximum_native_operations",operations);NextflowFiles.writeJson(stage.resolve("effective-request.json"),effective);
                    var graphPin=NextflowFiles.pin(graph,bytes,()->{});byte[] content=NextflowFiles.bytes(graph,bytes);require(content.length==graphPin.bytes()&&NextflowFiles.sha(content).equals(graphPin.sha256()),"Prepared graph changed during capture");Files.write(stage.resolve("prepared-graph.json"),content,StandardOpenOption.CREATE_NEW);
                    var g=object(NextflowParameters.parse(NextflowFiles.utf8(content),bytes,10000000));allowed(g,"cell_ids","cell_coordinates","principal_ids","principal_coordinates","roots","graph_vertex_ids","edge_weight_mode","edges");
                    String weights=text(g.get("edge_weight_mode"));require(Set.of("weighted","unweighted").contains(weights),"Declare edge_weight_mode weighted or unweighted");
                    var edges=new ArrayList<Monocle3Pseudotime.Edge>();for(Object value:list(g.get("edges"))){
                        require(edges.size()<5000000,"Graph edge capacity exceeded");var e=object(value);if(weights.equals("weighted"))allowed(e,"from","to","weight");else allowed(e,"from","to");
                        int from=(int)integer(e.get("from"),0,999999),to=(int)integer(e.get("to"),0,999999);double weight=weights.equals("weighted")?finite(e.get("weight"),0,Double.MAX_VALUE):1.0;edges.add(new Monocle3Pseudotime.Edge(from,to,weight));
                    }
                    var result=Monocle3Pseudotime.order(strings(g.get("cell_ids"),1000000),matrix(g.get("cell_coordinates"),1000000),strings(g.get("principal_ids"),9999),matrix(g.get("principal_coordinates"),9999),strings(g.get("roots"),9999),strings(g.get("graph_vertex_ids"),1000000),edges,operations);
                    warnings.addAll(result.warnings());verify(graph,graphPin);verify(requestPath,requestPin);Path results=Files.createDirectory(stage.resolve("results"));
                    values(results,"cell-pseudotime",result.cellIds(),result.pseudotime());values(results,"graph-pseudotime",result.graphVertexIds(),result.graphPseudotime());
                    var roots=new ArrayList<Map<String,Object>>();for(var root:result.selectedRoots())roots.add(Map.of("principal_point",root.principalPoint(),"zero_based_cell_index",root.zeroBasedCellIndex(),"cell_id",root.cellId(),"euclidean_distance_token",token(root.euclideanDistance()),"euclidean_distance_kind",kind(root.euclideanDistance())));
                    NextflowFiles.writeJson(results.resolve("selected-roots.json"),roots);
                    NextflowFiles.writeJson(stage.resolve("report.json"),Map.of("source_profile_declared",profile,"cells",result.cellIds().size(),"graph_vertices",result.graphVertexIds().size(),"roots",roots,"infinite_cells",result.infiniteCellCount(),"operations",result.operations(),"scope",SCOPE,"graph_refitted",false));
                    verify(graph,graphPin);verify(requestPath,requestPin);manifest.put("source_request_sha256",requestPin.sha256());manifest.put("prepared_graph_sha256",graphPin.sha256());
                    state.put("status","completed-with-warnings");state.put("result_admitted",true);
                } catch(Exception failure){exit[0]=2;state.put("cause",failure.toString());warnings.add("FAIL2: "+failure);}
                catch(OutOfMemoryError failure){exit[0]=2;state.put("cause","Java heap exhausted");warnings.add("FAIL2: Java heap exhausted; no partial result admitted. Use appropriate heap/capacities and NEW paths.");}
                if(exit[0]!=0){if(Files.exists(stage.resolve("results")))Files.move(stage.resolve("results"),stage.resolve("unadmitted-partial-results"));if(Files.exists(stage.resolve("report.json")))Files.move(stage.resolve("report.json"),stage.resolve("unadmitted-report.json"));}
                state.put("required_action",ACTION);state.put("source_profile_declared",profile);NextflowFiles.writeJson(stage.resolve("analysis-status.json"),state);
                Files.writeString(stage.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                manifest.put("analysis_status",state.get("status"));manifest.put("result_admitted",state.get("result_admitted"));manifest.put("scope",SCOPE);
            });
            MonocleToolsCli.emit(exit[0]==0?output:error,(exit[0]==0?"Native prepared Monocle3 ordering completed: ":"FAIL2 NO RESULT ADMITTED: ")+out);
            MonocleToolsCli.emit(error,"WARNINGS / REQUIRED ACTIONS: "+out.resolve("WARNINGS.md")+"\n"+ACTION);return exit[0];
        } catch(Exception failure){MonocleToolsCli.emit(error,"FAIL2 NO RESULT ADMITTED: "+failure+"\n"+ACTION);return 2;}
        catch(OutOfMemoryError failure){MonocleToolsCli.emit(error,"FAIL2 NO RESULT ADMITTED: Java heap exhausted. "+ACTION);return 2;}
    }
    private static void verify(Path path,NextflowFiles.Pin wanted)throws IOException {require(wanted.equals(NextflowFiles.pin(path,wanted.bytes(),()->{})),"Original request/prepared graph changed: "+path);}
    private static double[][] matrix(Object raw,int maximum)throws IOException {var rows=list(raw);require(rows.size()<=maximum,"Coordinate population capacity exceeded");double[][] out=new double[rows.size()][];for(int i=0;i<rows.size();i++){var row=list(rows.get(i));require(row.size()<=1024,"Coordinate dimension capacity exceeded");out[i]=new double[row.size()];for(int j=0;j<row.size();j++)out[i][j]=finite(row.get(j),-Double.MAX_VALUE,Double.MAX_VALUE);}return out;}
    private static String token(double d){return Double.isInfinite(d)?d>0?"Inf":"-Inf":Double.isNaN(d)?"NaN":Double.toString(d);}
    private static String kind(double d){return Double.isInfinite(d)?d>0?"positive-infinity":"negative-infinity":Double.isNaN(d)?"NaN":"value";}
    private static String quoted(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private static void values(Path directory,String name,List<String> ids,double[] values)throws IOException {
        var rows=new ArrayList<Map<String,Object>>();var tsv=new StringBuilder("one_based_ordinal\tid\tpseudotime_token\tpseudotime_kind\n");
        for(int i=0;i<values.length;i++){String token=token(values[i]),kind=kind(values[i]);rows.add(Map.of("one_based_ordinal",i+1,"id",ids.get(i),"pseudotime_token",token,"pseudotime_kind",kind));tsv.append(i+1).append('\t').append(quoted(ids.get(i))).append('\t').append(quoted(token)).append('\t').append(quoted(kind)).append('\n');}
        Files.writeString(directory.resolve(name+".tsv"),tsv,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);NextflowFiles.writeJson(directory.resolve(name+".json"),rows);
    }
}
