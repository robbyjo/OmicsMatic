/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.enrichment.GseapyRankedStatistics;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Source-anchored exact boundary examples, strict domain errors and immutable API results. */
public final class GseapyRankedContractMatrix {
    private GseapyRankedContractMatrix(){ }
    public static void main(String[] args)throws Exception{
        require(args.length==1&&!Files.exists(Path.of(args[0])),"Require NEW native contract receipt");
        var signed=GseapyRankedStatistics.rawRunning(new double[]{3,-2,1,4},new int[]{0,1});double[] expected={3,1,0.5,0};for(int i=0;i<4;i++)require(signed[i]==expected[i],"Literal signed phenotype curve was replaced by an absolute-weight curve");
        var weighted=GseapyRankedStatistics.walk(new double[]{3,2,1,4},new int[]{0,1});require(weighted.running()[0]==0.6000000000000001&&weighted.sparseScore()==1,"Independent exact small-count weighted walk differs");double original=weighted.running()[0];weighted.running()[0]=99;require(weighted.running()[0]==original,"Walk getter exposes internal storage");
        var zero=GseapyRankedStatistics.walk(new double[]{0,0,0},new int[]{1});for(double v:zero.running())require(Double.isNaN(v),"Zero-mass source walk was repaired");require(Double.isNaN(zero.singleSampleScore()),"Zero-mass single-sample diagnostic was repaired");
        var missing=GseapyRankedStatistics.fromNulls(new double[]{-2},new double[][]{{1,2}});require(missing.normalizedScores()[0]==-1&&missing.nominalP()[0]==1&&missing.fdr()[0]==0&&Double.isNaN(missing.familywiseP()[0]),"Missing-negative-sign source behavior changed");
        double[] es={1,-1};double[][] draws={{1,2},{-1,-2}};var copied=GseapyRankedStatistics.fromNulls(es,draws);double retained=copied.normalizedNulls()[0][0];es[0]=99;draws[0][0]=99;copied.normalizedNulls()[0][0]=100;require(copied.normalizedNulls()[0][0]==retained,"Caller/getter mutation changed the complete null family");
        double[] normalized=GseapyRankedStatistics.normalizeSingleSample(new double[]{1,1});require(normalized[0]==Double.POSITIVE_INFINITY&&normalized[1]==Double.POSITIVE_INFINITY,"Constant-family range-zero diagnostics changed");
        List<Runnable> invalid=List.of(()->GseapyRankedStatistics.walk(new double[0],new int[0]),()->GseapyRankedStatistics.walk(new double[]{1,Double.NaN},new int[]{0}),()->GseapyRankedStatistics.walk(new double[]{-1,2},new int[]{0}),()->GseapyRankedStatistics.walk(new double[]{1,2},new int[]{0,0}),()->GseapyRankedStatistics.walk(new double[]{1,2},new int[]{2}),()->GseapyRankedStatistics.rawRunning(new double[]{Double.POSITIVE_INFINITY,1},new int[]{1}),()->GseapyRankedStatistics.fromNulls(new double[0],new double[0][]),()->GseapyRankedStatistics.fromNulls(new double[]{1},new double[][]{{}}),()->GseapyRankedStatistics.fromNulls(new double[]{1,2},new double[][]{{1},{1,2}}),()->GseapyRankedStatistics.fromNulls(new double[]{Double.NaN},new double[][]{{1}}),()->GseapyRankedStatistics.fromNulls(new double[]{1},new double[][]{{Double.NaN}}),()->GseapyRankedStatistics.fromNulls(new double[]{0},new double[][]{{0,0}}),()->GseapyRankedStatistics.normalizeSingleSample(new double[0]));
        for(Runnable test:invalid){boolean rejected=false;try{test.run();}catch(IllegalArgumentException expectedError){rejected=true;}require(rejected,"Invalid/unsupported native domain was silently admitted");}
        NextflowFiles.writeJson(Path.of(args[0]),Map.of("status","passed-native-ranked-boundaries-domains-and-immutability","invalid_inputs_rejected",invalid.size(),"exact_signed_curve_positions",4,"missing_sign_FWER_NaN_retained",true,"zero_mass_nonfinite_retained",true,"independent_original_edge_execution",false));System.out.println("Passed literal ranked boundary/domain/immutability contracts");
    }
}
