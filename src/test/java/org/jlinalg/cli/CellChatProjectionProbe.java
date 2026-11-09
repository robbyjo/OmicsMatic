/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.Arrays;
import org.jlinalg.omics.CellChatProjection;

/** Development executable: API integrity, complete-network paths and refusal behavior. */
public final class CellChatProjectionProbe {
    private CellChatProjectionProbe() { }
    private static int checks;
    public static void main(String[] args) {
        String[] genes={"C","A","outside"},network={"A","B","C"};
        double[][] e={{0},{8},{3}},a={{1,2,0},{0,1,4},{3,0,1}};
        var l=CellChatProjection.Limits.defaults();
        var result=CellChatProjection.project(genes,e,network,a,.5,CellChatProjection.Normalization.ROWS,l);
        check(result.expression()[0][0]>0,"Unmeasured intermediate gene was lost");
        check(result.expression()[2][0]==3,"Unmapped expression changed");
        check(result.overlappingGenes()==2 && result.networkOnlyGenes()==1,"Wrong complete-network census");
        check(Arrays.deepEquals(e,new double[][]{{0},{8},{3}}) && Arrays.deepEquals(a,new double[][]{{1,2,0},{0,1,4},{3,0,1}}),"Caller matrices mutated");
        double original=result.expression()[0][0];double[][] copy=result.expression();copy[0][0]=123;
        check(result.expression()[0][0]==original,"Result aliases external arrays");
        refused(()->CellChatProjection.project(genes,e,network,a,0,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,network,a,1,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,network,a,Double.NaN,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,network,a,.5,null,l));
        refused(()->CellChatProjection.project(new String[]{"C","C","outside"},e,network,a,.5,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,new String[]{"A","A","C"},a,.5,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,network,new double[][]{{0,0,0},{0,1,4},{3,0,1}},.5,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,network,new double[][]{{0,2,0},{0,1,4},{0,0,1}},.5,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,new double[][]{{0},{-1},{3}},network,a,.5,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,network,new double[][]{{1,-1,0},{0,1,4},{3,0,1}},.5,CellChatProjection.Normalization.ROWS,l));
        refused(()->CellChatProjection.project(genes,e,network,a,.5,CellChatProjection.Normalization.ROWS,new CellChatProjection.Limits(2,3,1,99999,99999)));
        refused(()->CellChatProjection.project(genes,e,network,a,.5,CellChatProjection.Normalization.ROWS,new CellChatProjection.Limits(3,3,1,1,99999)));
        refused(()->CellChatProjection.project(genes,e,network,a,.5,CellChatProjection.Normalization.ROWS,new CellChatProjection.Limits(3,3,1,99999,1)));
        Thread.currentThread().interrupt();
        try { refused(()->CellChatProjection.project(genes,e,network,a,.5,CellChatProjection.Normalization.ROWS,l)); }
        finally { Thread.interrupted(); }
        System.out.println("PASS_NATIVE_PROJECTION_API "+checks);
    }
    private static void refused(Runnable action) { boolean refused=false;try { action.run(); }catch(IllegalArgumentException|IllegalStateException expected) { refused=true; }check(refused,"Expected complete refusal"); }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message);checks++; }
}
