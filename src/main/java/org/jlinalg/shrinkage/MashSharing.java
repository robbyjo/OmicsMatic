/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Summary conventions follow mashr0.2.79 (BSD-3-Clause).
 * See /org/jlinalg/omics/mashr-BSD-NOTICE.txt. */
package org.jlinalg.shrinkage;

import java.util.Arrays;
import java.util.Objects;

/** Posterior-mean sharing and strict significance summaries; no refitting or missingness inference. */
public final class MashSharing {
    private MashSharing() { }
    public enum Transform { IDENTITY, ABSOLUTE }
    public enum Boundary { STRICT, INCLUSIVE }
    public enum Status { DEFINED, EMPTY_SELECTED_POPULATION, UNDEFINED_SELECTED_RATIO }
    /** Bounds cover the entire input and requested result, never a prefix. */
    public record Limits(int rows,int conditions,long inputCells,long work,long ownedBytes) {
        public Limits {if(rows<1||conditions<1||conditions>2048||inputCells<1||work<1||ownedBytes<65536)throw new IllegalArgumentException("Invalid mash summary capacities");}
        public static Limits defaults(){return new Limits(1_000_000,256,32_000_000,1_000_000_000,134_217_728);}
    }
    /** Row indices are zero-based, stable by minimum significance, matching source tie order. */
    public record Significance(int[] selectedRows,int[] significantConditions,double[] minimumSignificance) {
        public Significance {selectedRows=selectedRows.clone();significantConditions=significantConditions.clone();minimumSignificance=minimumSignificance.clone();}
        @Override public int[] selectedRows(){return selectedRows.clone();}
        @Override public int[] significantConditions(){return significantConditions.clone();}
        @Override public double[] minimumSignificance(){return minimumSignificance.clone();}
    }
    /** Undefined ratios remain in the denominator, as source mean(...), without na.rm. */
    public record Cell(int selectedRows,int sharedRows,int undefinedRatios) {
        public Cell {if(selectedRows<0||sharedRows<0||undefinedRatios<0||undefinedRatios>selectedRows||sharedRows>selectedRows-undefinedRatios)throw new IllegalArgumentException("Invalid complete sharing counts");}
        public Status status(){return selectedRows==0?Status.EMPTY_SELECTED_POPULATION:undefinedRatios>0?Status.UNDEFINED_SELECTED_RATIO:Status.DEFINED;}
        public double fraction(){return status()==Status.DEFINED?sharedRows/(double)selectedRows:Double.NaN;}
    }
    public record Pairwise(Cell[][] cells) {
        public Pairwise {cells=copy(cells);}
        @Override public Cell[][] cells(){return copy(cells);}
        public Cell cell(int first,int second){return cells[first][second];}
    }

    /** Source get_significant_results/get_n_significant_conditions use STRICT. */
    public static Significance significance(double[][] signal,int conditions,int[] selectedConditions,double threshold,Boundary boundary,Limits limits,Runnable check){
        Objects.requireNonNull(selectedConditions);Objects.requireNonNull(boundary);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();probability(threshold,"significance threshold");
        int rows=Objects.requireNonNull(signal).length;long cells=Math.addExact(Math.multiplyExact((long)rows,conditions),selectedConditions.length);
        admit(rows,conditions,cells,Math.addExact(Math.multiplyExact(rows+1L,selectedConditions.length),Math.multiplyExact((long)rows,64)),65536+64L*rows,limits);
        if(selectedConditions.length==0)throw new IllegalArgumentException("Declare at least one selected condition");
        for(int c:selectedConditions)if(c<0||c>=conditions)throw new IllegalArgumentException("Selected condition outside complete matrix");
        validate(signal,rows,conditions,true,check);
        int[] counts=new int[rows],selected=new int[rows],scratch=new int[rows];double[] minimum=new double[rows];int size=0;
        for(int r=0;r<rows;r++){
            check.run();double min=Double.POSITIVE_INFINITY;
            for(int c:selectedConditions){double p=signal[r][c];min=Math.min(min,p);if(below(p,threshold,boundary))counts[r]++;}
            minimum[r]=min;if(below(min,threshold,boundary))selected[size++]=r;
        }
        // Primitive stable merge sort avoids a boxed object for every feature.
        for(long width=1;width<size;width*=2)for(int start=0;start<size;start=(int)Math.min((long)size,start+2*width)){
            check.run();int mid=(int)Math.min((long)size,start+width),end=(int)Math.min((long)size,start+2*width),a=start,b=mid,out=start;
            while(a<mid||b<end)scratch[out++]=b>=end||a<mid&&minimum[selected[a]]<=minimum[selected[b]]?selected[a++]:selected[b++];
            System.arraycopy(scratch,start,selected,start,end-start);
        }
        check.run();return new Significance(Arrays.copyOf(selected,size),counts,minimum);
    }

    /**
     * Source get_pairwise_sharing: select the union with lfsr &lt; threshold;
     * evaluate factor &lt; FUN(mean_i)/FUN(mean_j) &lt; 1/factor and mirror the
     * upper triangle. factor=0 and factor=1 retain literal IEEE division and
     * strict inequalities, including zero denominators and overflow. This is a
     * posterior-mean summary, not a posterior probability of a joint event.
     */
    public static Pairwise pairwise(double[][] means,double[][] lfsr,int conditions,double factor,double threshold,Transform transform,Limits limits,Runnable check){
        Objects.requireNonNull(means);Objects.requireNonNull(lfsr);Objects.requireNonNull(transform);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        probability(factor,"sharing factor");probability(threshold,"lfsr threshold");int rows=means.length;
        long pairs=Math.multiplyExact((long)conditions,conditions+1L)/2,cells=Math.multiplyExact(2L*rows,conditions);
        admit(rows,conditions,cells,Math.addExact(cells,Math.multiplyExact((long)rows,pairs)),Math.addExact(65536,Math.multiplyExact(96L,Math.multiplyExact((long)conditions,conditions))),limits);
        validate(means,rows,conditions,false,check);validate(lfsr,rows,conditions,true,check);Cell[][] result=new Cell[conditions][conditions];double reciprocal=1/factor;
        for(int i=0;i<conditions;i++)for(int j=i;j<conditions;j++){
            check.run();int selected=0,shared=0,undefined=0;
            for(int r=0;r<rows;r++){
                check.run();if(!(lfsr[r][i]<threshold||lfsr[r][j]<threshold))continue;selected++;
                double a=means[r][i],b=means[r][j];if(transform==Transform.ABSOLUTE){a=Math.abs(a);b=Math.abs(b);}double ratio=a/b;
                if(Double.isNaN(ratio))undefined++;else if(ratio>factor&&ratio<reciprocal)shared++;
            }
            result[i][j]=result[j][i]=new Cell(selected,shared,undefined);
        }
        check.run();return new Pairwise(result);
    }

    static boolean below(double p,double threshold,Boundary boundary){return boundary==Boundary.STRICT?p<threshold:p<=threshold;}
    static void probability(double value,String name){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException(name+" must be a finite probability");}
    static void validate(double[][] matrix,int rows,int conditions,boolean probabilities,Runnable check){
        if(matrix.length!=rows)throw new IllegalArgumentException("Complete posterior row counts differ");
        for(double[] row:matrix){check.run();if(row==null||row.length!=conditions)throw new IllegalArgumentException("Complete posterior matrix is ragged or has wrong condition count");for(double value:row){if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite input is not an inferred missing value");if(probabilities)probability(value,"signal probability");}}
    }
    static void admit(int rows,int conditions,long cells,long work,long bytes,Limits limits){
        if(rows<0||rows>limits.rows()||conditions<1||conditions>limits.conditions()||cells<0||cells>limits.inputCells()||work<0||work>limits.work()||bytes>limits.ownedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete mash summary exceeds declared dimensions/work/storage or half the Java heap; no prefix is returned");
    }
    private static Cell[][] copy(Cell[][] source){Cell[][] result=new Cell[source.length][];for(int i=0;i<source.length;i++)result[i]=source[i].clone();return result;}
}
