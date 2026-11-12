/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/** Complete numeric HOPS Newick topology with optional finite branch lengths.
 * Explicit lengths are retained as double bits; absent lengths remain absent.
 * No synthetic nodes, renaming, implicit root, comments or NHX interpretation.
 * This is a native parser for the documented numeric resource grammar, not a
 * replacement for every Forester format or the original descendant-selection code.
 */
public final class MaltExtractTaxonomy {
    public record Limits(long characters,int nodes,int depth) {
        public Limits {if(characters<1||nodes<1||depth<1)throw new IllegalArgumentException("Positive complete taxonomy capacities required");}
    }
    private final int[] ids,parents,depths,degrees;
    private final double[] lengths;
    private final boolean[] defined;
    private final Map<Integer,Integer> index;
    private MaltExtractTaxonomy(Builder b) {
        ids=Arrays.copyOf(b.ids,b.size);parents=Arrays.copyOf(b.parents,b.size);depths=Arrays.copyOf(b.depths,b.size);degrees=Arrays.copyOf(b.degrees,b.size);lengths=Arrays.copyOf(b.lengths,b.size);defined=Arrays.copyOf(b.defined,b.size);index=b.index;
    }
    public int size(){return ids.length;}
    public int root(){return ids[0];}
    public boolean contains(int id){return index.containsKey(id);}
    public int parent(int id){Integer n=index.get(id);return n==null||parents[n]<0?0:ids[parents[n]];}
    public int depth(int id){Integer n=index.get(id);if(n==null)throw new IllegalArgumentException("Unknown literal taxon");return depths[n];}
    public int childCount(int id){Integer n=index.get(id);if(n==null)throw new IllegalArgumentException("Unknown literal taxon");return degrees[n];}
    public boolean hasLength(int id){Integer n=index.get(id);if(n==null)throw new IllegalArgumentException("Unknown literal taxon");return defined[n];}
    public double length(int id){Integer n=index.get(id);if(n==null||!defined[n])throw new IllegalArgumentException("Taxon has no explicit branch length");return lengths[n];}
    public int[] ids(){return ids.clone();}
    public long explicitLengths(){long count=0;for(boolean value:defined)if(value)count++;return count;}
    public static MaltExtractTaxonomy read(Reader input,Limits limits)throws IOException {
        Objects.requireNonNull(input);Objects.requireNonNull(limits);Cursor c=new Cursor(input,limits.characters());Builder b=new Builder(limits.nodes());
        int[] stack=new int[limits.depth()];int depth=0;boolean nodeExpected=true,done=false;
        while(!done) {
            int token=c.peek();
            if(nodeExpected) {
                if(token!='('&&token!='-'&&(token<'0'||token>'9'))throw c.error("Expected complete numeric subtree");
                int node=b.add(depth==0?-1:stack[depth-1],depth);
                if(token=='('){c.take();if(depth==stack.length)throw c.error("Complete taxonomy depth exceeds capacity");stack[depth++]=node;}
                else {b.label(node,c.label());branch(c,b,node);nodeExpected=false;}
            }else if(token==',') {if(depth==0)throw c.error("Multiple roots");c.take();nodeExpected=true;
            }else if(token==')') {if(depth==0)throw c.error("Unexpected closing parenthesis");c.take();int node=stack[--depth];b.label(node,c.label());branch(c,b,node);
            }else if(token==';') {if(depth!=0)throw c.error("Unclosed numeric subtree");c.take();if(c.peek()!=-1)throw c.error("Trailing taxonomy content");done=true;
            }else throw c.error("Expected comma, closing parenthesis or final semicolon after numeric node and optional finite branch length");
        }
        return new MaltExtractTaxonomy(b);
    }
    private static void branch(Cursor c,Builder b,int node)throws IOException {
        if(c.peek()!=':')return;c.take();StringBuilder text=new StringBuilder();
        int token=c.peek();while(token!=-1&&token!=','&&token!=')'&&token!=';'&&!Character.isWhitespace(token)) {if(text.length()==128)throw c.error("Branch-length token exceeds capacity");text.append((char)c.raw());token=c.rawPeek();}
        String value=text.toString();if(!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw c.error("Explicit finite decimal branch length required");
        double length;try{length=Double.parseDouble(value);}catch(NumberFormatException failure){throw c.error("Invalid branch length");}if(!Double.isFinite(length))throw c.error("Nonfinite branch length");b.defined[node]=true;b.lengths[node]=length;
    }
    private static final class Builder {
        private int[] ids,parents,depths,degrees;private double[] lengths;private boolean[] defined;private int size;private final int maximum;private final Map<Integer,Integer> index=new HashMap<>();
        Builder(int maximum){this.maximum=maximum;int n=Math.min(4096,maximum);ids=new int[n];parents=new int[n];depths=new int[n];degrees=new int[n];lengths=new double[n];defined=new boolean[n];}
        int add(int parent,int depth)throws IOException {
            if(size==maximum)throw new IOException("Complete taxonomy node capacity exceeded");
            if(size==ids.length){int n=(int)Math.min((long)maximum,Math.max((long)size+1,2L*size));ids=Arrays.copyOf(ids,n);parents=Arrays.copyOf(parents,n);depths=Arrays.copyOf(depths,n);degrees=Arrays.copyOf(degrees,n);lengths=Arrays.copyOf(lengths,n);defined=Arrays.copyOf(defined,n);}
            int at=size++;parents[at]=parent;depths[at]=depth;if(parent>=0)degrees[parent]++;return at;
        }
        void label(int node,int id)throws IOException {if(index.putIfAbsent(id,node)!=null)throw new IOException("Repeated literal numeric taxonomy name: "+id);ids[node]=id;}
    }
    private static final class Cursor {
        private final Reader input;private final long maximum;private long characters;private int next=-2;
        Cursor(Reader input,long maximum){this.input=input;this.maximum=maximum;}
        int rawPeek()throws IOException {if(next==-2){next=input.read();if(next!=-1&&++characters>maximum)throw error("Complete taxonomy character capacity exceeded");}return next;}
        int raw()throws IOException {int value=rawPeek();next=-2;return value;}
        int peek()throws IOException {while(Character.isWhitespace(rawPeek()))raw();return rawPeek();}
        int take()throws IOException {int value=peek();next=-2;return value;}
        int label()throws IOException {
            StringBuilder value=new StringBuilder();int token=peek();if(token=='-'){value.append((char)take());token=rawPeek();}
            while(token>='0'&&token<='9'){if(value.length()==12)throw error("Numeric label exceeds integer capacity");value.append((char)raw());token=rawPeek();}
            String text=value.toString();int id;try{id=Integer.parseInt(text);}catch(NumberFormatException failure){throw error("Complete canonical integer taxon name required");}if(!Integer.toString(id).equals(text))throw error("Noncanonical numeric taxon name; do not silently rename it");return id;
        }
        IOException error(String text){return new IOException(text+" near character "+characters+"; retain the complete original tree and resolve its format or explicit capacity before retrying");}
    }
}
