/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Deterministic components and bounded Bron-Kerbosch maximal-clique enumeration. */
public final class NetworkModules {
    private NetworkModules() { }
    public record Cliques(List<List<Integer>> members, boolean complete, String limit, long searchStates) {
        public Cliques { members=members.stream().map(List::copyOf).toList(); }
    }
    public static List<List<Integer>> components(List<BitSet> graph) {
        validate(graph);var result=new ArrayList<List<Integer>>();var unseen=new BitSet(graph.size());unseen.set(0,graph.size());
        while(!unseen.isEmpty()) {
            int first=unseen.nextSetBit(0);var queue=new ArrayDeque<Integer>();queue.add(first);unseen.clear(first);var members=new ArrayList<Integer>();
            while(!queue.isEmpty()) { int node=queue.remove();members.add(node);var next=(BitSet)graph.get(node).clone();next.and(unseen);for(int j=next.nextSetBit(0);j>=0;j=next.nextSetBit(j+1)){unseen.clear(j);queue.add(j);} }
            members.sort(Integer::compareTo);result.add(List.copyOf(members));
        }
        return List.copyOf(result);
    }
    public static Cliques maximalCliques(List<BitSet> graph,int minimumSize,int maximumCount,long maximumStates,int maximumDepth) {
        validate(graph);if(minimumSize<2||maximumCount<1||maximumStates<1||maximumDepth<2||maximumDepth>128)throw new IllegalArgumentException("Invalid clique search bounds");
        var search=new Search(graph,minimumSize,maximumCount,maximumStates,maximumDepth);var candidates=new BitSet(graph.size());candidates.set(0,graph.size());
        search.visit(new ArrayList<>(),candidates,new BitSet());return new Cliques(search.members,search.limit.isEmpty(),search.limit,search.states);
    }
    private static void validate(List<BitSet> graph) {
        if(graph.size()>100000)throw new IllegalArgumentException("Graph exceeds 100000 nodes");
        for(int i=0;i<graph.size();i++) {var links=graph.get(i);if(links==null||links.length()>graph.size()||links.get(i))throw new IllegalArgumentException("Require a simple graph without self loops");
            for(int j=links.nextSetBit(0);j>=0;j=links.nextSetBit(j+1))if(!graph.get(j).get(i))throw new IllegalArgumentException("Require an undirected graph"); }
    }
    private static final class Search {
        final List<BitSet> graph;final int minimum,count,depth;final long maximumStates;long states;String limit="";final List<List<Integer>> members=new ArrayList<>();
        Search(List<BitSet> graph,int minimum,int count,long states,int depth){this.graph=graph;this.minimum=minimum;this.count=count;this.maximumStates=states;this.depth=depth;}
        void visit(ArrayList<Integer> clique,BitSet candidates,BitSet excluded) {
            if(!limit.isEmpty())return;if(++states>maximumStates){limit="max_search_states";return;}
            if(candidates.isEmpty()&&excluded.isEmpty()) {if(clique.size()>=minimum){if(members.size()>=count){limit="max_cliques";return;}var copy=new ArrayList<>(clique);copy.sort(Integer::compareTo);members.add(copy);}return;}
            if(clique.size()+candidates.cardinality()<minimum)return;
            if(clique.size()>=depth){limit="max_clique_depth";return;}
            var union=(BitSet)candidates.clone();union.or(excluded);int pivot=-1,best=-1;
            for(int v=union.nextSetBit(0);v>=0;v=union.nextSetBit(v+1)){var overlap=(BitSet)graph.get(v).clone();overlap.and(candidates);if(overlap.cardinality()>best){pivot=v;best=overlap.cardinality();}}
            var explore=(BitSet)candidates.clone();if(pivot>=0)explore.andNot(graph.get(pivot));
            for(int v=explore.nextSetBit(0);v>=0;v=explore.nextSetBit(v+1)) {
                clique.add(v);var p=(BitSet)candidates.clone();p.and(graph.get(v));var x=(BitSet)excluded.clone();x.and(graph.get(v));visit(clique,p,x);clique.remove(clique.size()-1);
                candidates.clear(v);excluded.set(v);if(!limit.isEmpty())return;
            }
        }
    }
}
