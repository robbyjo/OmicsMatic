/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.Monocle131ModelRequest.*;

/** Independent full-population low-rank reconstruction check; it does not refit MNN. */
final class FastMnn129Audit {
    private FastMnn129Audit() { }
    static Map<String,Object> audit(Map<String,Object> m,long budget)throws IOException {
        require(text(m.get("status")).equals("PASS_ORIGINAL_FASTMNN129_COMPLETE_PREPARED_BATCH_CORRECTION"),"Require original complete FastMNN result schema");
        require(bool(m.get("complete_required_model_roundtrip"))&&!bool(m.get("transformation_applied"))&&!bool(m.get("paper_patch_recovered"))&&!bool(m.get("whole_goal_complete")),"Unsupported result/transform/paper claims");
        var cells=labels(m.get("cell_ids"),1000000);var features=labels(m.get("feature_ids"),1000000);var batches=NextflowFiles.strings(m.get("batches"));require(cells.size()>=2&&batches.size()==cells.size(),"Incomplete cell/batch population");
        double[][] corrected=matrix(m.get("corrected")),rotation=matrix(m.get("rotation")),reconstructed=matrix(m.get("reconstructed"));
        require(corrected.length==cells.size()&&rotation.length==features.size()&&reconstructed.length==features.size(),"Incomplete complete-matrix population");
        int dimensions=corrected[0].length;require(dimensions>0,"Empty corrected coordinates");
        for(double[] row:corrected)require(row.length==dimensions,"Ragged corrected coordinates");for(double[] row:rotation)require(row.length==dimensions,"Ragged rotation");for(double[] row:reconstructed)require(row.length==cells.size(),"Ragged reconstructed expression");
        long operations=Math.multiplyExact(Math.multiplyExact((long)cells.size(),features.size()),dimensions);require(operations<=budget,"Complete native reconstruction operation capacity exceeded; no prefix comparison");
        double maximum=0;
        for(int f=0;f<features.size();f++)for(int c=0;c<cells.size();c++){
            double sum=0,compensation=0,magnitude=0;
            for(int d=0;d<dimensions;d++){double product=rotation[f][d]*corrected[c][d];require(Double.isFinite(product),"Nonfinite native reconstruction product");double adjusted=product-compensation,next=sum+adjusted;compensation=(next-sum)-adjusted;sum=next;magnitude+=Math.abs(product);}
            double difference=Math.abs(sum-reconstructed[f][c]);double bound=1e-11+64*Math.ulp(1d)*dimensions*magnitude;
            require(Double.isFinite(sum)&&Double.isFinite(magnitude)&&difference<=bound,"Complete reconstructed value differs from original rotation/corrected crossproduct at feature "+f+", cell "+c);maximum=Math.max(maximum,difference);
        }
        var result=new LinkedHashMap<String,Object>();result.put("status","PASS_COMPLETE_FASTMNN_LOW_RANK_CONSISTENCY_ONLY");result.put("complete_cells",cells.size());result.put("complete_features",features.size());result.put("dimensions",dimensions);result.put("native_multiply_adds",operations);result.put("maximum_absolute_difference",maximum);result.put("bound","1e-11 +64*ulp(1)*dimensions*sum(abs(products)); compensated Java dot products");result.put("original_fitting_repeated",false);result.put("producer_authenticated",false);result.put("original_analysis_admitted",false);result.put("whole_goal_complete",false);return result;
    }
    static double[][] matrix(Object value)throws IOException {
        var rows=list(value);require(!rows.isEmpty(),"Empty complete matrix");double[][] result=new double[rows.size()][];
        for(int i=0;i<rows.size();i++){var row=list(rows.get(i));result[i]=new double[row.size()];for(int j=0;j<row.size();j++){String s=text(row.get(j));require(s.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"),"Invalid/nonfinite original numeric matrix token");try{result[i][j]=Double.parseDouble(s);}catch(NumberFormatException e){throw new IOException("Original numeric capacity exceeded",e);}require(Double.isFinite(result[i][j]),"Nonfinite original matrix value");}}return result;
    }
}
