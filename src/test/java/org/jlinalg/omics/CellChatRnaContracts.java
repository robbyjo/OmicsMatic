/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics;

import java.util.List;
import java.util.Map;

/** Scientific input/refusal contracts, executable from the isolated artifact. */
public final class CellChatRnaContracts {
    private CellChatRnaContracts() { }
    private static int refusals;
    private static final CellChatRna.Parameters PARAMETERS=new CellChatRna.Parameters(CellChatRna.Average.TRI_MEAN,0.1,0.5,1,false);
    private static final List<CellChatRna.Interaction> PAIRS=List.of(new CellChatRna.Interaction("LR","L","R","","","",""));
    private static void refuse(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { refusals++; return; }
        throw new IllegalStateException("Invalid scientific input admitted");
    }
    private static CellChatRna.Result run(String[] names,double[][] data,int[] groups,int count,
            List<CellChatRna.Interaction> pairs,Map<String,List<String>> complex,int[][] permutations) {
        return CellChatRna.infer(CellChatRna.Profile.CELLCHAT_2_2_0_RNA,names,data,groups,count,pairs,complex,Map.of(),PARAMETERS,permutations);
    }
    public static void main(String[] args) {
        String[] names={"L","R"}; double[][] data={{0,1,2,4},{4,2,1,0}}; int[] groups={1,0,1,0};
        refuse(()->run(names,new double[][]{{0,-1,2,4},{4,2,1,0}},groups,2,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(names,new double[][]{{0,Double.NaN,2,4},{4,2,1,0}},groups,2,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(names,new double[][]{{0,Double.POSITIVE_INFINITY,2,4},{4,2,1,0}},groups,2,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(names,new double[][]{{0,0,0,0},{0,0,0,0}},groups,2,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(new String[]{"L","L"},data,groups,2,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(names,data,new int[]{0,0,0,0},2,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(names,data,new int[]{0,0,-1,1},2,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(names,data,groups,Integer.MAX_VALUE,PAIRS,Map.of(),new int[0][]));
        refuse(()->run(names,data,groups,2,PAIRS,Map.of(),new int[][]{{0,0,2,3}}));
        refuse(()->run(names,data,groups,2,PAIRS,Map.of(),new int[][]{{0,1,2,4}}));
        refuse(()->run(names,data,groups,2,PAIRS,Map.of(),new int[][]{{0,1}}));
        var complexPair=List.of(new CellChatRna.Interaction("CR","C","R","","","",""));
        refuse(()->run(names,data,groups,2,complexPair,Map.of("C",List.of("L","missing")),new int[0][]));
        refuse(()->run(names,data,groups,2,complexPair,Map.of(),new int[0][]));
        refuse(()->run(names,data,groups,2,List.of(PAIRS.get(0),PAIRS.get(0)),Map.of(),new int[0][]));
        refuse(()->CellChatRna.infer(CellChatRna.Profile.CELLCHAT_1_0_0,names,data,groups,2,complexPair,Map.of("C",List.of("L")),Map.of(),PARAMETERS,new int[0][]));
        refuse(()->new CellChatRna.Parameters(CellChatRna.Average.TRI_MEAN,0.1,0,1,false));
        refuse(()->new CellChatRna.Parameters(CellChatRna.Average.TRI_MEAN,0.6,0.5,1,false));
        refuse(()->CellChatRna.infer(CellChatRna.Profile.CELLCHAT_1_1_3_FAST,names,data,groups,2,PAIRS,Map.of(),Map.of(),new CellChatRna.Parameters(CellChatRna.Average.THRESHOLDED_MEAN,0.1,0.5,1,false),new int[0][]));
        if (CellChatRna.average(new double[]{0,0,2,2},CellChatRna.Average.THRESHOLDED_MEAN,0.5)!=1
                || CellChatRna.average(new double[]{0,0,2,2},CellChatRna.Average.THRESHOLDED_MEAN,0.50001)!=0)
            throw new IllegalStateException("Threshold boundary changed");
        if (CellChatRna.average(new double[]{0,1,2,100},CellChatRna.Average.TRUNCATED_MEAN,0.25)!=1.5)
            throw new IllegalStateException("Trimming behavior changed");
        if (refusals!=18) throw new IllegalStateException("Missing refusal contracts");
        System.out.println("PASS18 scientific refusals and two exact averaging boundaries");
    }
}
