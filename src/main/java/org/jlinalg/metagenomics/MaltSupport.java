/* Copyright (C) 2018 Daniel H. Huson; Copyright (C) 2026 JLinAlg contributors;
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Native May2018 MEGAN MinSupportFilter relocation for MALT assignments.
 * Source: husonlab/megan-ce commit24e70bf6cd0a434ff40a62b98cf0b54d72954908,
 * Daniel H. Huson's MinSupportFilter (GPL3+). Child-order float addition,
 * disabled nodes and negative-subtree pruning retain original behavior.
 * This method accepts an already determined integer minimum support; it does
 * not infer a percentage denominator, parse RMA6 or align sequences.
 */
public final class MaltSupport {
    private final int[] ids,degree,offset,children,order;
    private final int root;

    /** Build an immutable reusable topology; no change to the input taxonomy. */
    public MaltSupport(MaltTaxonomy tree) {
        Objects.requireNonNull(tree);ids=tree.ids();degree=new int[ids.length];offset=new int[ids.length];
        Map<Integer,Integer> indices=new HashMap<>();for(int i=0;i<ids.length;i++)indices.put(ids[i],i);
        root=indices.get(tree.root());int count=0;
        for(int i=0;i<ids.length;i++){degree[i]=tree.childCount(ids[i]);offset[i]=count;count=Math.addExact(count,degree[i]);}
        if(count!=ids.length-1)throw new IllegalArgumentException("Require one complete ordered taxonomy");
        children=new int[count];Arrays.fill(children,-1);
        for(int i=0;i<ids.length;i++)if(i!=root) {
            int parentId=tree.parent(ids[i]);Integer parent=indices.get(parentId);
            if(parent==null)throw new IllegalArgumentException("Missing complete taxonomy parent");
            String prefix=tree.address(parentId),address=tree.address(ids[i]);int length=degree[parent]<65535?1:2;
            if(address==null || !address.startsWith(prefix) || address.length()!=prefix.length()+length)throw new IllegalArgumentException("Inconsistent ordered taxonomy address");
            int ordinal=address.charAt(prefix.length());if(length==2)ordinal+=(address.charAt(prefix.length()+1)-1)*65534;
            if(ordinal<1 || ordinal>degree[parent] || children[offset[parent]+ordinal-1]!=-1)throw new IllegalArgumentException("Inconsistent original child ordinal");
            children[offset[parent]+ordinal-1]=i;
        }
        int[] stack=new int[ids.length],visited=new int[ids.length];int size=1,used=0;stack[0]=root;
        while(size>0) {
            int node=stack[--size];if(ids[node]<0)continue;visited[used++]=node;
            for(int child=degree[node]-1;child>=0;child--)stack[size++]=children[offset[node]+child];
        }
        order=Arrays.copyOf(visited,used);
    }

    /** Return only relocated IDs; unchanged assignments have no map entry.
     * As in the source API, absent/null weights are zero, weights outside the
     * reachable nonnegative tree are ignored, and float nonfinite comparisons
     * are retained. This is a relocation calculation, not biological admission.
     */
    public Map<Integer,Integer> relocate(Map<Integer,Float> weights,int minimumSupport,Set<Integer> disabled) {
        Objects.requireNonNull(weights);Objects.requireNonNull(disabled);
        float[] totals=new float[ids.length];int[] head=new int[ids.length],tail=new int[ids.length],next=new int[ids.length];
        Arrays.fill(head,-1);Arrays.fill(tail,-1);Arrays.fill(next,-1);var result=new TreeMap<Integer,Integer>();
        for(int index=order.length-1;index>=0;index--) {
            int node=order[index],first=-1,last=-1;float below=0;
            for(int child=0;child<degree[node];child++) {
                int at=children[offset[node]+child];below+=totals[at];
                if(head[at]>=0){if(first<0)first=head[at];else next[last]=head[at];last=tail[at];}
            }
            Float supplied=weights.get(ids[node]);float weight=supplied==null?0f:supplied;float combined=below+weight;totals[node]=combined;
            if(combined>=minimumSupport && !disabled.contains(ids[node])) {
                for(int orphan=first;orphan>=0;orphan=next[orphan])result.put(ids[orphan],ids[node]);
            } else {
                if(weight>0){if(first<0)first=node;else next[last]=node;last=node;}
                head[node]=first;tail[node]=last;
            }
        }
        for(int orphan=head[root];orphan>=0;orphan=next[orphan])result.put(ids[orphan],-2);
        return Collections.unmodifiableMap(result);
    }
}
