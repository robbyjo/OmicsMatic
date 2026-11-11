/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Strict bounded text inputs; exact row/coordinate identity and no alignment or numeric repair. */
final class ScfatesWorkflowInputs {
    private ScfatesWorkflowInputs() { }
    record Matrix(String role,List<String> ids,List<String> columns,double[][] values) {
        Map<String,Object> json(){return Map.of("schema_version",1,"role",role,"ids",ids,"columns",columns,"values",values,"numeric_dtype","float64");}
    }
    static Matrix read(ScfatesWorkflowRequest.Input specification,Path captured,ScfatesWorkflowRequest.Request request)throws IOException {
        boolean nodes=specification.role().equals("initialization");try(var input=new CellPhoneDbWorkflowInputs.Rows(captured,specification.format().equals("csv")?',':'\t',request.inputBytes())){
            var header=input.next();require(header!=null&&header.size()>=2&&header.size()-1<=request.maximumComponents()&&header.get(0).equals(nodes?"node_id":"cell_id"),"Require literal "+(nodes?"node_id":"cell_id")+" header and complete numeric coordinate columns");var columns=new ArrayList<String>();var names=new HashSet<String>();for(String name:header.subList(1,header.size())){ScfatesWorkflowRequest.data(name);require(names.add(name),"Duplicate coordinate label; no repair");columns.add(name);}var ids=new ArrayList<String>();var seen=new HashSet<String>();var values=new ArrayList<double[]>();long underflow=0;
            for(List<String> row;(row=input.next())!=null;){
                require(row.size()==header.size()&&ids.size()<(nodes?request.maximumNodes():request.maximumCells())&&(long)(ids.size()+1)*columns.size()<=request.maximumMatrixCells(),"Ragged/blank/oversized complete input; no rows are dropped");String id=ScfatesWorkflowRequest.data(row.get(0));require(seen.add(id),"Duplicate input ID; no deduplication or positional repair");double[] numeric=new double[columns.size()];for(int j=0;j<numeric.length;j++){String token=row.get(j+1);require(token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require unchanged finite decimal at "+id+", "+columns.get(j)+"; no whitespace/missing/NaN/Inf repair");try{numeric[j]=Double.parseDouble(token);}catch(NumberFormatException e){throw new IOException("Invalid float64 value at "+id,e);}require(Double.isFinite(numeric[j]),"Input overflows float64 at "+id);if(numeric[j]==0&&new BigDecimal(token).signum()!=0)underflow++;}ids.add(id);values.add(numeric);
            }
            require(!ids.isEmpty()&&underflow==0,"Empty input or nonzero tokens underflowing to float64 zero; preserve originals and supply explicit representable values");return new Matrix(specification.role(),List.copyOf(ids),List.copyOf(columns),values.toArray(double[][]::new));
        }
    }
    static void choices(Map<String,Object> analysis,Map<String,Matrix> matrices,ScfatesWorkflowRequest.Request request)throws IOException {
        Matrix x=matrices.get("coordinates");require(x!=null&&x.ids().size()>=2,"At least two explicit cells required");int k=Math.toIntExact(integer(object(analysis.get("tree")).get("nodes"),2,request.maximumNodes()));
        for(var entry:matrices.entrySet()){
            String role=entry.getKey();Matrix a=entry.getValue();if(role.equals("weights"))require(x.ids().equals(a.ids())&&x.columns().equals(a.columns()),"Weight cells and coordinate labels/order must match exactly; no joining or reordering");if(role.equals("initialization")){require(a.ids().size()>=2&&x.columns().equals(a.columns()),"Initial nodes require matching coordinate labels/order");k=a.ids().size();}if(role.equals("root-values"))require(x.ids().equals(a.ids())&&a.columns().size()==1,"Root observations require exactly one value per unchanged cell in input order");
        }
        require((long)k*k<=request.maximumMatrixCells()&&(long)x.ids().size()*k<=request.maximumMatrixCells()&&(long)x.ids().size()*integer(object(analysis.get("pseudotime")).get("n_map"),1,1000)<=request.maximumMatrixCells(),"Complete node-system/cell-assignment/mapping capacity exceeded; no partial fit requested");
        if(text(object(analysis.get("root")).get("mode")).equals("obs"))require(matrices.containsKey("root-values"),"Declared observation root values unavailable");
    }
}
