/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Bounded strict text-to-float64 component inputs with exact string identity and explicit conversion dispositions. */
final class PalantirWorkflowInputs {
    private PalantirWorkflowInputs() { }
    record Matrix(List<String> cells,List<String> columns,double[][] values,long underflows) {
        Map<String,Object> json(){return Map.of("schema_version",1,"cells",cells,"columns",columns,"values",values,"numeric_dtype","float64","nonzero_tokens_underflowed_to_zero",underflows);}
    }
    static Matrix read(PalantirWorkflowRequest.Input specification,PalantirWorkflowRequest.Request request)throws IOException {
        try(var input=new CellPhoneDbWorkflowInputs.Rows(specification.source(),specification.format().equals("csv")?',':'\t',request.inputBytes())){
            var header=input.next();require(header!=null&&header.size()>=2&&header.size()-1<=request.maximumComponents()&&header.get(0).equals("cell_id"),"Require literal cell_id header and complete declared numeric component columns");var columns=new ArrayList<String>();var names=new HashSet<String>();for(String name:header.subList(1,header.size())){PalantirWorkflowRequest.data(name);require(names.add(name),"Duplicate component column name; no repair");columns.add(name);}var cells=new ArrayList<String>();var seen=new HashSet<String>();var values=new ArrayList<double[]>();long underflows=0;
            for(List<String> row;(row=input.next())!=null;){
                require(row.size()==header.size()&&cells.size()<request.maximumCells()&&(long)(cells.size()+1)*columns.size()<=request.maximumMatrixCells(),"Ragged/blank/oversized complete component matrix; no rows are dropped");String id=PalantirWorkflowRequest.data(row.get(0));require(seen.add(id),"Duplicate cell ID in trajectory input; no deduplication or positional matching");double[] numeric=new double[columns.size()];
                for(int j=0;j<numeric.length;j++){String token=row.get(j+1);require(token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require an unchanged finite decimal coordinate at cell "+id+", component "+columns.get(j)+"; no whitespace/missing/NaN/Inf repair");try{numeric[j]=Double.parseDouble(token);}catch(NumberFormatException failure){throw new IOException("Invalid float64 coordinate at cell "+id+", component "+columns.get(j),failure);}require(Double.isFinite(numeric[j]),"Coordinate overflows float64 at cell "+id+", component "+columns.get(j));if(numeric[j]==0&&new BigDecimal(token).signum()!=0)underflows++;}
                cells.add(id);values.add(numeric);
            }
            require(!cells.isEmpty(),"Empty original component input");require(underflows==0,"Nonzero decimal coordinates underflow to float64 zero; no analysis admitted. Preserve original tokens and supply an explicit representable component matrix");return new Matrix(List.copyOf(cells),List.copyOf(columns),values.toArray(double[][]::new),underflows);
        }
    }
    static void choices(Map<String,Object> analysis,Matrix input,PalantirWorkflowRequest.Request request)throws IOException {
        var ids=new HashSet<>(input.cells());if(analysis.containsKey("diffusion")){var d=object(analysis.get("diffusion"));require(integer(d.get("knn"),1,100000)<input.cells().size(),"Diffusion knn must be smaller than the supplied cell family; no source neighbor-count adjustment is admitted");require(integer(d.get("n_components"),1,4096)<=request.maximumComponents(),"Requested diffusion component capacity exceeded");if(d.get("n_eigs")!=null)require(integer(d.get("n_eigs"),2,4096)<=integer(d.get("n_components"),1,4096),"Explicit n_eigs exceeds requested diffusion components");}
        if(analysis.containsKey("trajectory")){var t=object(analysis.get("trajectory"));require(integer(t.get("knn"),1,100000)<input.cells().size(),"Trajectory knn must be smaller than the supplied cell family");for(String id:PalantirWorkflowRequest.identities(t.get("early_cells"),64))require(ids.contains(id),"Requested early cell absent from unchanged input: "+id);if(t.get("terminal_states")!=null)for(Object raw:list(t.get("terminal_states"))){String id=PalantirWorkflowRequest.data(object(raw).get("cell"));require(ids.contains(id),"Requested terminal cell absent from unchanged input: "+id+"; no partial matching or dropping");}var waypoints=object(t.get("waypoints"));if(waypoints.containsKey("cells"))for(String id:PalantirWorkflowRequest.identities(waypoints.get("cells"),request.maximumWaypoints()))require(ids.contains(id),"Explicit waypoint cell absent from unchanged input: "+id);}
    }
    static Matrix frame(CellPhoneDbWorkflowData.Frame frame,int maximumCells,int maximumComponents)throws IOException {
        require(frame.rows()<=maximumCells&&frame.columns().size()<=maximumComponents,"Complete observed component capacities exceeded");var cells=new ArrayList<String>();var columns=new ArrayList<String>();for(Object id:frame.index())cells.add(text(id));for(Object id:frame.columns())columns.add(CellPhoneDbWorkflowData.identity(id));double[][] values=new double[cells.size()][columns.size()];for(int i=0;i<values.length;i++)for(int j=0;j<columns.size();j++)values[i][j]=CellPhoneDbWorkflowData.numeric(frame.value(i,j));return new Matrix(List.copyOf(cells),List.copyOf(columns),values,0);
    }
}
