/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import org.jlinalg.singlecell.Monocle3Pseudotime;
import static org.jlinalg.cli.Monocle131ModelRequest.*;

/** Complete supplied-graph comparison for the separately qualified original 1.3.7 pipeline. */
final class Monocle137GraphAudit {
    private Monocle137GraphAudit() { }
    static Map<String,Object> audit(Map<String,Object> g,long operations)throws IOException {
        allowed(g,"cell_ids","cell_coordinates","principal_ids","principal_coordinates","roots","graph_vertex_ids","edge_weight_mode","edges","expected_cell_pseudotime","expected_graph_pseudotime","expected_root_indices");
        String mode=text(g.get("edge_weight_mode"));require(Set.of("weighted","unweighted").contains(mode),"Declare graph weight mode");var edges=new ArrayList<Monocle3Pseudotime.Edge>();
        for(Object x:list(g.get("edges"))){var e=object(x);require(e.keySet().equals(mode.equals("weighted")?Set.of("from","to","weight"):Set.of("from","to")),"Complete edge fields differ from declared weight mode");double weight=mode.equals("weighted")?finite(e.get("weight")):1;require(weight>=0,"Negative projected-graph weight");edges.add(new Monocle3Pseudotime.Edge((int)integer(e.get("from"),0,999999),(int)integer(e.get("to"),0,999999),weight));}
        var r=Monocle3Pseudotime.order(NextflowFiles.strings(g.get("cell_ids")),matrix(g.get("cell_coordinates")),NextflowFiles.strings(g.get("principal_ids")),matrix(g.get("principal_coordinates")),NextflowFiles.strings(g.get("roots")),NextflowFiles.strings(g.get("graph_vertex_ids")),edges,operations);
        compare(r.pseudotime(),list(g.get("expected_cell_pseudotime")));compare(r.graphPseudotime(),list(g.get("expected_graph_pseudotime")));var expected=list(g.get("expected_root_indices"));require(expected.size()==r.selectedRoots().size(),"Complete declared root population differs");
        var roots=new ArrayList<Map<String,Object>>();for(int i=0;i<expected.size();i++){var root=r.selectedRoots().get(i);require(integer(expected.get(i),0,999999)==root.zeroBasedCellIndex(),"Original first-nearest-cell root index differs");roots.add(Map.of("principal_point",root.principalPoint(),"zero_based_cell_index",root.zeroBasedCellIndex(),"cell_id",root.cellId(),"euclidean_distance_token",Double.toString(root.euclideanDistance())));}
        return Map.of("status","PASS_COMPLETE_NATIVE_MONOCLE137_GRAPH_ORDERING_COMPARISON","cells",r.cellIds().size(),"graph_vertices",r.graphVertexIds().size(),"edges",edges.size(),"roots",roots,"infinite_cells",r.infiniteCellCount(),"operations",r.operations(),"source_profile","1.3.7","producer_authenticated",false);
    }
    static double[][] matrix(Object value)throws IOException {
        var rows=list(value);require(rows.size()<=1000000,"Complete coordinate row capacity exceeded");double[][] matrix=new double[rows.size()][];
        for(int i=0;i<matrix.length;i++){var row=list(rows.get(i));require(row.size()<=1024,"Coordinate dimension capacity exceeded");matrix[i]=new double[row.size()];for(int j=0;j<matrix[i].length;j++)matrix[i][j]=finite(row.get(j));}return matrix;
    }
    private static void compare(double[] actual,List<?> expected)throws IOException {
        require(actual.length==expected.size(),"Complete original/native pseudotime population differs");for(int i=0;i<actual.length;i++){
            String token=text(expected.get(i));double value;try{value=token.equals("Inf")?Double.POSITIVE_INFINITY:token.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(token);}catch(NumberFormatException e){throw new IOException("Invalid original pseudotime token: "+token,e);}
            require(!Double.isNaN(value)&&!Double.isNaN(actual[i]),"NA/NaN pseudotime is unadmitted");if(!Double.isFinite(value)||!Double.isFinite(actual[i]))require(value==actual[i],"Original/native infinity differs at "+i);else require(Math.abs(value-actual[i])<=1e-12+1e-11*Math.abs(value),"Original/native finite pseudotime differs at "+i);
        }
    }
}
