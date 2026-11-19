/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;
import org.jlinalg.singlecell.ScanpyPreprocessing;
import org.jlinalg.singlecell.ScanpyPreprocessing.*;

/** Artificial data demonstrating explicit source profiles; no biological inference. */
public final class ScanpyNativeExample {
    private ScanpyNativeExample() { }
    public static void main(String[] args) {
        if(args.length!=1||!List.of("1.9.3","1.13.0").contains(args[0])) {
            System.err.println("Usage: ScanpyNativeExample 1.9.3|1.13.0");System.exit(2);return;
        }
        Profile profile=args[0].equals("1.9.3")?Profile.SCANPY_1_9_3:Profile.SCANPY_1_13_0;
        Matrix counts=new Matrix(List.of("gene A","gene B","gene C","gene D"),List.of("cell 1","cell 2","cell 3"),
            new int[]{0,4,8,12},new int[]{0,1,2,3,0,1,2,3,0,1,2,3},new double[]{1,0,2,1,2,1,1,0,0,1,0,3});
        Limits limits=Limits.defaults();
        Result normalized=ScanpyPreprocessing.normalizeTotal(counts,Storage.DENSE,profile,new NormalizeOptions(10000.0,false,.05),limits,()->false);
        warnings(normalized);Matrix prepared=normalized.matrix();
        Result logged=ScanpyPreprocessing.log1p(prepared,Storage.DENSE,profile,10.0,limits,()->false);warnings(logged);
        Result scaled=ScanpyPreprocessing.scale(logged.matrix(),Storage.DENSE,profile,new ScaleOptions(true,1.0),limits,()->false);warnings(scaled);
        Matrix result=scaled.matrix();double[] values=result.values();
        System.out.println("profile="+profile+"; float64; target_sum=10000; log_base=10; centered_scale_cap=1");
        System.out.println("cell\tgene\tscaled_value");
        for(int c=0;c<result.cells().size();c++)for(int g=0;g<result.features().size();g++)
            System.out.println(result.cells().get(c)+"\t"+result.features().get(g)+"\t"+values[c*result.features().size()+g]);
        System.out.println("Synthetic demonstration only. Historical author settings and complete workflows are not reproduced.");
    }
    private static void warnings(Result result) {
        for(Warning w:result.warnings())System.err.println("WARNING "+w.code()+" ("+w.affected()+"): "+w.message()+" REQUIRED ACTION: "+w.requiredAction());
    }
}
