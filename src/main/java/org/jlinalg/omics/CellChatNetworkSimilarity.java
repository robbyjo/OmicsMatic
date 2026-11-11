/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics;

import java.util.BitSet;

/** Prepared directed-network Jaccard and original CellChat buildSNN smoothing.
 * Graphs share identical ordered node axes. Caller-supplied nearest-neighbor
 * ranks preserve upstream tie choices; this class does not infer FNN ranks or
 * implement structural network distance, manifold learning or clustering. */
public final class CellChatNetworkSimilarity {
    private CellChatNetworkSimilarity() { }
    public static double[][] functional(double[][][] graphs,long maximumCells) {
        require(graphs!=null && graphs.length>0 && graphs[0]!=null && graphs[0].length>0,"Complete prepared graphs required");
        int n=graphs.length,g=graphs[0].length;long edges=(long)g*g;
        require(edges<=Integer.MAX_VALUE && edges*n+(long)n*n<=maximumCells,"Complete similarity exceeds declared cell capacity");
        BitSet[] bits=new BitSet[n];
        for(int i=0;i<n;i++) {
            require(graphs[i]!=null && graphs[i].length==g,"Prepared graph node axes differ");bits[i]=new BitSet((int)edges);
            for(int s=0;s<g;s++) {
                require(graphs[i][s]!=null && graphs[i][s].length==g,"Prepared graph must be square");
                for(int t=0;t<g;t++) { double value=graphs[i][s][t];require(Double.isFinite(value)&&value>=0,"Graph scores must be finite and nonnegative");if(value>0)bits[i].set(s*g+t); }
            }
        }
        double[][] result=new double[n][n];
        for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
            BitSet intersection=(BitSet)bits[i].clone();intersection.and(bits[j]);BitSet union=(BitSet)bits[i].clone();union.or(bits[j]);
            result[i][j]=i==j?1:union.isEmpty()?0:(double)intersection.cardinality()/union.cardinality();
        }
        return result;
    }
    /** Each row begins with its own zero-based index. Duplicate ranks are
     * retained with multiplicity, as in original Eigen setFromTriplets; FNN
     * ties can repeat self after buildSNN prepends it. Values above one and
     * nonfinite values are retained. prune=1/15; diagonal is fixed to one. */
    public static double[][] sharedNearestNeighbors(int[][] ranks,long maximumCells) {
        require(ranks!=null && ranks.length>0 && ranks[0]!=null && ranks[0].length>0,"Complete explicit nearest-neighbor ranks required");
        int n=ranks.length,k=ranks[0].length;require(k<=n && (long)n*n+(long)n*k<=maximumCells,"Complete SNN exceeds declared capacity");
        int[][] neighbors=new int[n][n];
        for(int i=0;i<n;i++) {
            require(ranks[i]!=null && ranks[i].length==k && ranks[i][0]==i,"Every complete rank row must start with its own index");
            for(int neighbor:ranks[i]) {require(neighbor>=0 && neighbor<n,"Out-of-range neighbor");neighbors[i][neighbor]++;}
        }
        double[][] result=new double[n][n];
        for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
            long shared=0;for(int at=0;at<n;at++)shared+=(long)neighbors[i][at]*neighbors[j][at];double value=shared/(k+(k-(double)shared));
            result[i][j]=i==j?1:value<1.0/15?0:value;
        }
        return result;
    }
    private static void require(boolean value,String cause) {if(!value)throw new IllegalArgumentException(cause);}
}
