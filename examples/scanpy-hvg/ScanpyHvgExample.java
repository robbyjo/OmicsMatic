/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;
import org.jlinalg.singlecell.ScanpyHighlyVariableGenes;
import org.jlinalg.singlecell.ScanpyHighlyVariableGenes.*;

/** Synthetic arithmetic example, not a recipe inferred from a paper. */
public final class ScanpyHvgExample {
    private ScanpyHvgExample() { }
    public static void main(String[] args) {
        int cells=15,genes=60;List<String> features=new ArrayList<>(),ids=new ArrayList<>();
        for(int g=0;g<genes;g++)features.add("gene-"+g);
        int[] pointers=new int[cells+1],rows=new int[cells*genes];double[] values=new double[rows.length];
        for(int c=0;c<cells;c++){
            ids.add("cell-"+c);pointers[c]=c*genes;
            for(int g=0;g<genes;g++){
                int k=c*genes+g;rows[k]=g;
                double counts=1+((c+1)*(g%13+1)%17)+g*.031;
                values[k]=Math.log1p(counts); // Explicit supplied log expression; no hidden normalization.
            }
        }
        pointers[cells]=values.length;
        Matrix input=new Matrix(features,ids,pointers,rows,values);
        var options=new Options(Flavor.SEURAT,15,10,.0125,3,.5,Double.POSITIVE_INFINITY,null,null,Export.DATAFRAME);
        var result=ScanpyHighlyVariableGenes.select(input,Storage.DENSE,Profile.SCANPY_1_13_0,
            options,null,Limits.defaults(),()->false);
        for(var warning:result.warnings())System.err.println(warning.code()+": "+warning.message()+" "+warning.requiredAction());
        if(!result.usable())throw new ArithmeticException("No complete selection; preserve diagnostics and inspect warnings");
        System.out.println("Selected "+result.selectedFeatures().size()+" of "+genes+" genes");
        for(String id:result.selectedFeatures())System.out.println(id);
    }
}
