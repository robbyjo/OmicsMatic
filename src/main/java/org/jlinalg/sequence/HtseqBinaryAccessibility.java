/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.jlinalg.sequence.HtseqFeatureAssignment.Feature;
import org.jlinalg.sequence.HtseqFeatureAssignment.Interval;
import org.jlinalg.sequence.HtseqFeatureAssignment.Mode;

/**
 * Sparse binary peak-by-cell read presence: a coordinate is1 iff at least one
 * supplied read block overlaps the supplied feature. This implements the
 * explicit primitive described for P099, using checked HTSeq2.1.2 interval-set
 * geometry; the paper's exact custom script/edition/filters are not recovered.
 * Callers supply cell assignments, filtering, blocks, strand interpretation,
 * peak construction and row/column order. No gene-count ambiguity/NH policy,
 * pairing, barcode correction, UMI deduplication or implicit peak merging occurs.
 */
public final class HtseqBinaryAccessibility {
    private HtseqBinaryAccessibility() { }
    /** One already-prepared read and exact caller-assigned cell identity. */
    public record Read(String cellId,List<Interval> blocks) {
        public Read {
            identity(cellId);blocks=List.copyOf(blocks);
            for(Interval block:blocks)if(block.start()==block.end())throw new IllegalArgumentException("Binary accessibility requires nonempty supplied aligned blocks");
        }
    }
    public record Limits(int maximumPeaks,int maximumCells,long maximumReads,int maximumNonzero,
                         HtseqFeatureAssignment.Limits intervals) {
        public Limits {
            Objects.requireNonNull(intervals,"interval limits");
            if(maximumPeaks<0||maximumPeaks>2000000||maximumCells<0||maximumCells>1000000||maximumReads<0||maximumReads>1000000000000L||maximumNonzero<0||maximumNonzero>2000000)throw new IllegalArgumentException("Invalid binary accessibility logical capacities");
        }
        public static Limits defaults(){return new Limits(2000000,100000,1000000000000L,2000000,HtseqFeatureAssignment.Limits.defaults());}
    }
    public static final class Model {
        private final HtseqFeatureAssignment.Index index;
        private final List<String> peaks;
        private final Map<String,Integer> rows;
        private final Limits limits;
        private Model(HtseqFeatureAssignment.Index index,List<String> peaks,Map<String,Integer> rows,Limits limits){this.index=index;this.peaks=List.copyOf(peaks);this.rows=Map.copyOf(rows);this.limits=limits;}
        public List<String> peakIds(){return peaks;}
        public boolean stranded(){return index.stranded();}
    }
    /** Zero-based sparse coordinate with binary value1; no count weight is implied. */
    public record Entry(int peakRow,int cellColumn) { }
    /** Immutable matrix with explicit axes, including zero-read peaks/cells. */
    public static final class Matrix {
        private final List<String> peaks,cells;
        private final long[] pairs;
        private final long reads,overlapping;
        private Matrix(List<String> peaks,List<String> cells,long[] pairs,long reads,long overlapping){this.peaks=List.copyOf(peaks);this.cells=List.copyOf(cells);this.pairs=pairs.clone();this.reads=reads;this.overlapping=overlapping;}
        public List<String> peakIds(){return peaks;}
        public List<String> cellIds(){return cells;}
        public int nonzero(){return pairs.length;}
        public long processedReads(){return reads;}
        public long readsOverlappingAnyPeak(){return overlapping;}
        public long readsWithoutPeakOverlap(){return reads-overlapping;}
        public boolean accessible(int row,int column){
            Objects.checkIndex(row,peaks.size());Objects.checkIndex(column,cells.size());return Arrays.binarySearch(pairs,key(row,column))>=0;
        }
        /** Immutable row-major view; entries are decoded without a second dense/sparse allocation. */
        public List<Entry> entries(){return new AbstractList<>(){
            @Override public int size(){return pairs.length;}
            @Override public Entry get(int index){long pair=pairs[Objects.checkIndex(index,pairs.length)];return new Entry((int)(pair>>>32),(int)pair);}
        };}
    }
    public static Model model(List<Feature> features,List<String> peakIds,boolean stranded,Limits limits,BooleanSupplier cancelled){
        Objects.requireNonNull(features,"features");Objects.requireNonNull(peakIds,"peak IDs");Objects.requireNonNull(limits,"limits");check(cancelled);
        if(peakIds.size()>limits.maximumPeaks||features.size()>limits.intervals.maximumFeatures())throw new IllegalArgumentException("Binary peak-row/feature-record capacity exceeded");
        peakIds=List.copyOf(peakIds);features=List.copyOf(features);
        var rows=axis(peakIds,cancelled);var featureIds=new HashSet<String>();
        for(Feature feature:features){check(cancelled);featureIds.add(Objects.requireNonNull(feature,"feature").id());}
        if(!featureIds.equals(rows.keySet()))throw new IllegalArgumentException("Every explicit peak identity must have an interval, and every feature identity must appear on the declared peak axis");
        var index=HtseqFeatureAssignment.index(features,stranded,limits.intervals,cancelled);return new Model(index,peakIds,rows,limits);
    }
    public static Model model(List<Feature> features,List<String> peakIds,boolean stranded){return model(features,peakIds,stranded,Limits.defaults(),()->false);}
    public static Matrix presence(Model model,List<String> cells,Iterable<Read> reads,BooleanSupplier cancelled){
        Objects.requireNonNull(model,"model");Objects.requireNonNull(cells,"cells");Objects.requireNonNull(reads,"reads");check(cancelled);
        if(cells.size()>model.limits.maximumCells)throw new IllegalArgumentException("Binary cell-column capacity exceeded");
        cells=List.copyOf(cells);
        var columns=axis(cells,cancelled);var seen=new HashSet<Long>();long processed=0,overlapping=0;
        for(Read read:reads){
            check(cancelled);Objects.requireNonNull(read,"prepared read");if(processed>=model.limits.maximumReads)throw new IllegalArgumentException("Binary read capacity exceeded; no matrix admitted");processed++;
            Integer column=columns.get(read.cellId);if(column==null)throw new IllegalArgumentException("Prepared read cell is absent from the explicit cell axis: "+read.cellId);
            var assigned=model.index.assign(read.blocks,Mode.UNION,cancelled);
            if(!assigned.features().isEmpty())overlapping++;
            for(String feature:assigned.features()){
                check(cancelled);long pair=key(model.rows.get(feature),column);
                if(!seen.contains(pair)){
                    if(seen.size()>=model.limits.maximumNonzero)throw new IllegalArgumentException("Binary nonzero-coordinate capacity exceeded; no matrix admitted");seen.add(pair);
                }
            }
        }
        var sorted=new ArrayList<>(seen);sorted.sort((a,b)->{check(cancelled);return Long.compare(a,b);});long[] pairs=new long[sorted.size()];
        for(int i=0;i<pairs.length;i++){check(cancelled);pairs[i]=sorted.get(i);}
        check(cancelled);return new Matrix(model.peaks,cells,pairs,processed,overlapping);
    }
    public static Matrix presence(Model model,List<String> cells,Iterable<Read> reads){return presence(model,cells,reads,()->false);}
    private static Map<String,Integer> axis(List<String> ids,BooleanSupplier cancelled){
        var result=new HashMap<String,Integer>();for(int i=0;i<ids.size();i++){check(cancelled);String id=ids.get(i);identity(id);if(result.put(id,i)!=null)throw new IllegalArgumentException("Duplicate literal binary-matrix axis identity: "+id);}return result;
    }
    private static void identity(String value){Objects.requireNonNull(value,"matrix identity");if(value.isEmpty()||value.length()>4096)throw new IllegalArgumentException("Require a nonempty exact binary-matrix identity of at most4096UTF16 units");}
    private static long key(int row,int column){return ((long)row<<32)|(column&0xffffffffL);}
    private static void check(BooleanSupplier cancelled){Objects.requireNonNull(cancelled,"cancellation");if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Binary accessibility cancelled; no matrix admitted");}
}
