/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Ordered numeric taxonomy for the May2018 MALT/MEGAN LCA address convention.
 * Address attribution: Daniel H. Huson, megan.algorithms.LCAAddressing (GPL3+),
 * megan-ce commit24e70bf6cd0a434ff40a62b98cf0b54d72954908.
 * Reads the complete numeric, unweighted tree grammar used by bundled ncbi.tre.
 * Other Newick dialects refuse explicitly. No names, ranks or optional
 * contaminant sentinel are inferred. The default -1,-2,-3 nodes are retained.
 */
public final class MaltTaxonomy {
    private final int[] ids,parents,degrees,ordinals,offsets,children;
    private final Map<Integer,Integer> indices;
    private MaltTaxonomy(Builder b) {
        ids=Arrays.copyOf(b.ids,b.size);parents=Arrays.copyOf(b.parents,b.size);
        degrees=Arrays.copyOf(b.degrees,b.size);ordinals=Arrays.copyOf(b.ordinals,b.size);
        indices=b.indices;offsets=new int[b.size];children=new int[b.size-1];
        int cursor=0;
        for(int i=0;i<b.size;i++){offsets[i]=cursor;cursor=Math.addExact(cursor,degrees[i]);}
        if(cursor!=children.length)throw new IllegalArgumentException("Taxonomy is not one complete tree");
        for(int i=1;i<b.size;i++)children[offsets[parents[i]]+ordinals[i]-1]=i;
    }
    /** Explicit capacities refuse an incomplete tree; they never truncate it. */
    public record Limits(long characters,int nodes,int depth) {
        public Limits {if(characters<1 || nodes<4 || depth<1)throw new IllegalArgumentException("Positive complete-tree capacities required");}
    }
    public static MaltTaxonomy read(Reader input,Limits limits)throws IOException {
        Objects.requireNonNull(input);Objects.requireNonNull(limits);
        Cursor c=new Cursor(input,limits.characters());Builder b=new Builder(limits.nodes());
        int[] stack=new int[limits.depth()];int depth=0;boolean expectNode=true,done=false;
        while(!done) {
            int token=c.peek();
            if(expectNode) {
                if(token!='(' && token!='-' && (token<'0' || token>'9'))throw c.error("Expected a complete numeric subtree");
                int node=b.add(depth==0?-1:stack[depth-1]);
                if(token=='(') {c.take();if(depth==stack.length)throw c.error("Complete taxonomy depth exceeds capacity");stack[depth++]=node;}
                else {b.label(node,c.integer());expectNode=false;}
            } else if(token==',') {
                if(depth==0)throw c.error("Multiple taxonomy roots");c.take();expectNode=true;
            } else if(token==')') {
                if(depth==0)throw c.error("Unexpected closing parenthesis");c.take();b.label(stack[--depth],c.integer());
            } else if(token==';') {
                if(depth!=0)throw c.error("Unclosed numeric subtree");c.take();if(c.peek()!=-1)throw c.error("Trailing taxonomy content");done=true;
            } else throw c.error("Require numeric node labels, commas, parentheses and final semicolon; other Newick dialects are unsupported");
        }
        for(int id:new int[]{-1,-2,-3})if(!b.indices.containsKey(id))b.label(b.add(0),id);
        return new MaltTaxonomy(b);
    }
    public int size(){return ids.length;}
    public boolean contains(int id){return indices.containsKey(id);}
    public int root(){return ids[0];}
    public int parent(int id){Integer n=indices.get(id);return n==null || parents[n]<0?0:ids[parents[n]];}
    public int childCount(int id){Integer n=indices.get(id);return n==null?0:degrees[n];}
    public int[] ids(){return ids.clone();}
    /** Null denotes an unknown taxon; the root address is the empty string. */
    public String address(int id) {
        Integer at=indices.get(id);if(at==null)return null;
        int length=0;
        for(int n=at;parents[n]>=0;n=parents[n])length+=degrees[parents[n]]<65535?1:2;
        char[] text=new char[length];int pos=length;
        for(int n=at;parents[n]>=0;n=parents[n]) {
            int ordinal=ordinals[n],parent=parents[n];
            if(degrees[parent]<65535)text[--pos]=(char)ordinal;
            else {text[--pos]=(char)((ordinal-1)/65534+1);text[--pos]=(char)((ordinal-1)%65534+1);}
        }
        return new String(text);
    }
    /** Zero preserves upstream's missing-address result, including an intermediate
     * one-character prefix at a parent using two-character child addresses. */
    public int taxonAt(String address) {
        if(address==null)return 0;int node=0;
        for(int pos=0;pos<address.length();) {
            int first=address.charAt(pos++);if(first==0 || first==65535)return 0;long ordinal=first;
            if(degrees[node]>=65535) {
                if(pos==address.length())return 0;int second=address.charAt(pos++);
                if(second==0 || second==65535)return 0;ordinal+=(long)(second-1)*65534;
            }
            if(ordinal>degrees[node])return 0;node=children[offsets[node]+(int)ordinal-1];
        }
        return ids[node];
    }
    private static final class Builder {
        private int[] ids,parents,degrees,ordinals;private int size;private final int limit;
        private final Map<Integer,Integer> indices=new HashMap<>();
        private Builder(int limit){this.limit=limit;int n=Math.min(limit,4096);ids=new int[n];parents=new int[n];degrees=new int[n];ordinals=new int[n];}
        private int add(int parent)throws IOException {
            if(size==limit)throw new IOException("Complete taxonomy node count exceeds capacity");
            if(size==ids.length){int n=(int)Math.min((long)limit,Math.max((long)size+1,2L*size));ids=Arrays.copyOf(ids,n);parents=Arrays.copyOf(parents,n);degrees=Arrays.copyOf(degrees,n);ordinals=Arrays.copyOf(ordinals,n);}
            int node=size++;parents[node]=parent;if(parent>=0)ordinals[node]=++degrees[parent];return node;
        }
        private void label(int node,int id)throws IOException {
            if(indices.putIfAbsent(id,node)!=null)throw new IOException("Duplicate taxonomy id: "+id);ids[node]=id;
        }
    }
    private static final class Cursor {
        private final Reader input;private final long limit;private long seen;private int next=-2;
        private Cursor(Reader input,long limit){this.input=input;this.limit=limit;}
        private int peek()throws IOException {
            while(true) {if(next==-2){next=input.read();if(next!=-1 && ++seen>limit)throw error("Complete taxonomy character count exceeds capacity");}
                if(next!=-1 && Character.isWhitespace(next)){next=-2;continue;}return next;}
        }
        private int take()throws IOException {int value=peek();next=-2;return value;}
        private int integer()throws IOException {
            boolean negative=peek()=='-';if(negative)take();long value=0;int digits=0;
            // Read contiguous digits. Whitespace may separate grammar tokens,
            // but must never silently concatenate separate numeric labels.
            while(true) {
                if(next==-2){next=input.read();if(next!=-1 && ++seen>limit)throw error("Complete taxonomy character count exceeds capacity");}
                if(next<'0' || next>'9')break;int digit=next-'0';next=-2;digits++;
                value=value*10+digit;if(value>(negative?2147483648L:Integer.MAX_VALUE))throw error("Taxonomy id exceeds signed32-bit source range");
            }
            if(digits==0)throw error("Missing numeric node label");return (int)(negative?-value:value);
        }
        private IOException error(String message){return new IOException(message+" near character "+seen);}
    }
}
