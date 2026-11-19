/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.*;
import org.jlinalg.sequence.SeuratPreprocessing;
import org.jlinalg.sequence.SeuratPreprocessing.*;

/** Synthetic native matrix transformations; no R, model fitting or inferred cell labels. */
public final class SeuratPreprocessingExample {
    public static void main(String[] args) {
        boolean failedDemo=args.length==1&&args[0].equals("--zero-total-demo");
        if(args.length>0&&!failedDemo){System.err.println("Use no arguments, or --zero-total-demo (expected exit 2).");System.exit(2);}
        Matrix input=failedDemo
            ?new Matrix(List.of("geneA"),List.of("zero-total","positive"),new int[]{0,1,2},new int[]{0,0},new double[]{0,1})
            :new Matrix(List.of("geneA","geneB","geneC"),List.of("cell1","cell2","cell3","cell4"),
                new int[]{0,2,4,7,9},new int[]{0,1,0,2,0,1,2,0,1},new double[]{1,3,3,5,2,2,2,7,1});
        NormalizationResult normalized=SeuratPreprocessing.logNormalize(input,10000,Limits.defaults(),()->false);
        Matrix prepared;
        try{prepared=normalized.matrix();}
        catch(ArithmeticException failure){
            System.err.println("FAIL2: "+failure.getMessage());warnings(normalized.warnings());
            System.err.println("Retained diagnostic stored values: "+Arrays.toString(normalized.diagnosticValues()));System.exit(2);return;
        }
        warnings(normalized.warnings());
        ScaleResult scaled=SeuratPreprocessing.scaleData(prepared,List.of("geneC","geneA"),ScaleOptions.defaults(),Limits.defaults(),()->false);
        DenseMatrix result;
        try{result=scaled.matrix();}
        catch(ArithmeticException failure){System.err.println("FAIL2: "+failure.getMessage());warnings(scaled.warnings());System.exit(2);return;}
        warnings(scaled.warnings());
        System.out.println("Profile: "+SeuratPreprocessing.PROFILE);
        System.out.println("Synthetic normalized/scaled values; no significance test or cell annotation.");
        System.out.println("feature\t"+String.join("\t",result.cells()));
        for(int r=0;r<result.features().size();r++) {
            System.out.print(result.features().get(r));
            for(int c=0;c<result.cells().size();c++)System.out.printf(Locale.ROOT,"\t%.17g",result.value(r,c));
            System.out.println();
        }
    }
    private static void warnings(List<Warning> warnings) {
        for(Warning warning:warnings)System.err.println("WARNING "+warning.code()+" ("+warning.affected()+"): "+warning.message()+" ACTION: "+warning.requiredAction());
    }
}
