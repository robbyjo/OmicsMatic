/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CancellationException;

/** Native Seurat 5.6 shared-neighbor Jaccard graph; no nearest-neighbor search. */
public final class SeuratSnn {
    private SeuratSnn() { }
    /** Directed row-major edges, including the source's diagonal when retained. */
    public record Edge(int row,int column,double weight) { }
    public record Limits(long maximumWork,int maximumEdges) {
        public Limits {if(maximumWork<1||maximumEdges<1)throw new IllegalArgumentException("Positive SNN capacities required");}
    }
    /**
     * Each row contains k distinct zero-based neighbors in the same n-cell
     * population. Rank order is immaterial. Self-neighbors are permitted.
     * Only positive overlaps are candidates, and pruning is inclusive (>=).
     */
    public static List<Edge> compute(int[][] neighbors,double prune,Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(neighbors);Objects.requireNonNull(limits);Objects.requireNonNull(cancelled);
        check(cancelled);int n=neighbors.length;
        if(n==0||neighbors[0]==null||neighbors[0].length==0)throw new IllegalArgumentException("Nonempty neighbor population required");
        int k=neighbors[0].length;if(k>n||!Double.isFinite(prune)||prune<0||prune>1)throw new IllegalArgumentException("Invalid k or prune threshold");
        // Copy before use so caller mutations cannot change an admitted graph.
        int[][] input=new int[n][];int[] degree=new int[n];long work=0;
        for(int row=0;row<n;row++) {
            check(cancelled);if(neighbors[row]==null||neighbors[row].length!=k)throw new IllegalArgumentException("Every row requires the same k");
            input[row]=neighbors[row].clone();Arrays.sort(input[row]);int previous=-1;
            for(int neighbor:input[row]){if(neighbor<0||neighbor>=n||neighbor==previous)throw new IllegalArgumentException("Neighbors must be distinct and in range");degree[neighbor]++;previous=neighbor;}
            work=add(work,k,limits.maximumWork());
        }
        for(int d:degree)work=add(work,(long)d*d,limits.maximumWork());
        int[][] inverted=new int[n][];for(int i=0;i<n;i++)inverted[i]=new int[degree[i]];Arrays.fill(degree,0);
        for(int row=0;row<n;row++){check(cancelled);for(int neighbor:input[row])inverted[neighbor][degree[neighbor]++]=row;}
        List<Edge> edges=new ArrayList<>();
        for(int row=0;row<n;row++) {
            check(cancelled);Map<Integer,Integer> overlaps=new TreeMap<>();
            for(int neighbor:input[row])for(int other:inverted[neighbor]){check(cancelled);overlaps.merge(other,1,Integer::sum);}
            for(var entry:overlaps.entrySet()) {
                int shared=entry.getValue();double weight=(double)shared/(k+(k-shared));
                if(weight>=prune){if(edges.size()>=limits.maximumEdges())throw new IllegalArgumentException("SNN maximumEdges exceeded; no complete graph");edges.add(new Edge(row,entry.getKey(),weight));}
            }
        }
        check(cancelled);return List.copyOf(edges);
    }
    private static long add(long value,long extra,long cap){if(extra>cap-value)throw new IllegalArgumentException("SNN maximumWork exceeded before graph construction");return value+extra;}
    private static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("SNN cancelled; no complete graph");}
}
