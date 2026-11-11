/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Prepared, unstranded-peak subset of ChIPseeker's nearest-feature component.
 * Feature ranges/strands and their original order are supplied by the caller.
 * This does not load TxDb, annotate genomic feature classes or run ChIPseeker.
 * Source-profile comparison and admission are separate from this implementation.
 */
public final class ChipSeekerNearestFeatures {
    private ChipSeekerNearestFeatures() { }
    /** Source behavior outside the admitted native contract; retain the original warning/result. */
    public static final class UnsupportedInput extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public UnsupportedInput(String message){super(message);}
    }

    /** Original component overlap choice; ALL still gives TSS overlaps final priority. */
    public enum Overlap { TSS, ALL }
    /** All inputs remain in the ledger, including source-dropped and dummy-boundary rows. */
    public enum Disposition { MATCHED, NO_FEATURE, BOUNDARY_DUMMY }
    /** Unstranded, one-based inclusive prepared peak; no contig alias conversion. */
    public record Peak(String contig, int start1, int end1) {
        public Peak {
            coordinates(contig,start1,end1);
            if(end1==Integer.MAX_VALUE)throw new UnsupportedInput("Original GenomicRanges start+width overflows for a peak ending at 2147483647. Preserve its original warnings and dropped rows; skip native parity analysis for this input, without clipping or silently repairing coordinates");
        }
    }
    /** Prepared gene/transcript range in original order; only explicit + or - strands. */
    public record Feature(String contig, int start1, int end1, char strand) {
        public Feature {
            coordinates(contig,start1,end1);
            if (strand!='+' && strand!='-') throw new IllegalArgumentException("Require explicit feature + or - strand; wildcard source features need a separate contract");
            if((strand=='+'?start1:end1)==Integer.MAX_VALUE)throw new UnsupportedInput("Original GenomicRanges start+width overflows for a resized feature TSS at 2147483647. Preserve its original warnings and dropped rows; skip native parity analysis without silently repairing coordinates");
        }
        /** Source resize(width=1) anchor. */
        public int tss1() { return strand=='+'?start1:end1; }
    }
    /** Both directional flags retain the source's ignoreUpstream precedence. */
    public record Options(boolean ignoreOverlap, boolean ignoreUpstream, boolean ignoreDownstream, Overlap overlap) {
        public Options { Objects.requireNonNull(overlap); }
        public static Options defaults() { return new Options(false,false,false,Overlap.TSS); }
    }
    /** Logical capacities; not OS quotas or Java heap guarantees. */
    public record Limits(int maximumPeaks, int maximumFeatures, long maximumOperations) {
        public Limits {
            if (maximumPeaks<1 || maximumPeaks>1_000_000 || maximumFeatures<1 || maximumFeatures>1_000_000
                || maximumOperations<1 || maximumOperations>10_000_000_000L) throw new IllegalArgumentException("Invalid complete nearest-feature capacities");
        }
        public static Limits defaults() { return new Limits(1_000_000,1_000_000,50_000_000); }
    }
    /**
     * inputIndex is zero-based. sourceFeatureIndex is the original one-based
     * ordinal, or N+1 for the source dummy, or zero for a source-dropped row.
     * Missing distances stay NaN; the source boundary dummy stays positive Inf.
     */
    public record Row(int inputIndex, Disposition disposition, int sourceFeatureIndex, double distanceToTss) { }
    public record Result(List<Row> rows, long operations, List<String> warnings) {
        public Result { rows=List.copyOf(rows);warnings=List.copyOf(warnings); }
        /** Original retained population, in input order; dummy boundaries remain explicit. */
        public List<Row> sourceRetained() { return rows.stream().filter(r->r.disposition()!=Disposition.NO_FEATURE).toList(); }
    }
    private record Positioned(int coordinate, int ordinal) { }
    private static final class Budget {
        final Limits limits; final Runnable check; long operations;
        Budget(Limits limits,Runnable check) { this.limits=limits;this.check=check; }
        void add() {
            check.run(); if (++operations>limits.maximumOperations()) throw new IllegalArgumentException("Complete native nearest-feature operation capacity exceeded; no partial result is admitted");
        }
    }
    private static final class Index {
        final List<Positioned> sorted; final int base; final int[] minimumOrdinal;
        Index(List<Positioned> supplied,Budget budget,boolean queriesIndexed) {
            sorted=new ArrayList<>(supplied);
            sorted.sort((a,b)->{budget.add();int c=Integer.compare(a.coordinate(),b.coordinate());return c!=0?c:Integer.compare(a.ordinal(),b.ordinal());});
            int size=1;while(size<sorted.size())size*=2;base=size;
            minimumOrdinal=queriesIndexed?new int[2*base]:null;
            if(queriesIndexed){
                java.util.Arrays.fill(minimumOrdinal,Integer.MAX_VALUE);
                for(int i=0;i<sorted.size();i++){budget.add();minimumOrdinal[base+i]=sorted.get(i).ordinal();}
                for(int i=base-1;i>0;i--){budget.add();minimumOrdinal[i]=Math.min(minimumOrdinal[2*i],minimumOrdinal[2*i+1]);}
            }
        }
        int lower(int coordinate,boolean inclusive,Budget budget) {
            int lo=0,hi=sorted.size();
            while(lo<hi){budget.add();int mid=(lo+hi)>>>1;
                if(sorted.get(mid).coordinate()<coordinate || !inclusive && sorted.get(mid).coordinate()==coordinate)lo=mid+1;else hi=mid;}
            return lo;
        }
        int firstOverlap(int from,int to,Budget budget){
            if(minimumOrdinal==null)return sorted.get(from).ordinal();
            int result=Integer.MAX_VALUE;
            for(int left=from+base,right=to+base;left<right;left>>=1,right>>=1){budget.add();
                if((left&1)!=0)result=Math.min(result,minimumOrdinal[left++]);
                if((right&1)!=0)result=Math.min(result,minimumOrdinal[--right]);}
            return result;
        }
    }

    public static Result annotate(List<Peak> peaks,List<Feature> features,Options options) {
        return annotate(peaks,features,options,Limits.defaults(),()->{});
    }
    /**
     * Computes strict-flank choices, source ties, optional body overlaps and final
     * TSS-overlap priority. Only unstranded peaks/sameStrand=FALSE are supported.
     * Every source-dropped query receives a NO_FEATURE ledger row.
     */
    public static Result annotate(List<Peak> peaks,List<Feature> features,Options options,Limits limits,Runnable cancellation) {
        Objects.requireNonNull(peaks);Objects.requireNonNull(features);Objects.requireNonNull(options);Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);
        if(peaks.isEmpty())throw new IllegalArgumentException("The audited original nearest-feature editions refuse an empty query; no empty successful population is inferred");
        if(peaks.size()>limits.maximumPeaks() || features.size()>limits.maximumFeatures())throw new IllegalArgumentException("Complete native nearest-feature population exceeds capacity");
        peaks=List.copyOf(peaks);features=List.copyOf(features);var budget=new Budget(limits,cancellation);
        var positions=new HashMap<String,List<Positioned>>();
        for(int i=0;i<features.size();i++){budget.add();var f=features.get(i);positions.computeIfAbsent(f.contig(),key->new ArrayList<>()).add(new Positioned(f.tss1(),i));}
        var queryCounts=new HashMap<String,Integer>();for(var p:peaks){budget.add();queryCounts.merge(p.contig(),1,Integer::sum);}
        var indexes=new HashMap<String,Index>();for(var item:positions.entrySet())if(queryCounts.containsKey(item.getKey()))
            indexes.put(item.getKey(),new Index(item.getValue(),budget,queryCounts.get(item.getKey())<item.getValue().size()));
        var bodyOrder=new HashMap<String,List<Integer>>();
        if(!options.ignoreOverlap() && options.overlap()==Overlap.ALL){
            for(int i=0;i<features.size();i++){budget.add();bodyOrder.computeIfAbsent(features.get(i).contig(),key->new ArrayList<>()).add(i);}
            final List<Feature> orderedFeatures=features;
            for(var item:bodyOrder.entrySet())if(queryCounts.getOrDefault(item.getKey(),0)>=item.getValue().size())
                item.getValue().sort((a,b)->{budget.add();var fa=orderedFeatures.get(a);var fb=orderedFeatures.get(b);
                    int c=Integer.compare(fa.start1(),fb.start1());if(c==0)c=Integer.compare(fb.end1(),fa.end1());return c!=0?c:Integer.compare(a,b);});
        }
        var rows=new ArrayList<Row>();int missing=0,dummies=0;
        for(int i=0;i<peaks.size();i++){
            budget.add();var p=peaks.get(i);var index=indexes.get(p.contig());int selected=-1;double distance=Double.NaN;
            if(index!=null){
                int begin=index.lower(p.start1(),true,budget),end=index.lower(p.end1(),false,budget);
                int left=begin==0?-1:index.sorted.get(begin-1).ordinal();
                int right=end==index.sorted.size()?-1:index.sorted.get(end).ordinal();
                if(left!=-1 || right!=-1){
                    double ld=left==-1?Double.POSITIVE_INFINITY:signed(p.start1(),features.get(left));
                    double rd=right==-1?Double.POSITIVE_INFINITY:signed(p.end1(),features.get(right));
                    boolean chooseRight=options.ignoreUpstream() || !options.ignoreDownstream() && Math.abs(rd)<Math.abs(ld);
                    selected=chooseRight?right:left;distance=chooseRight?rd:ld;
                    if(selected==-1)selected=features.size(); // Original dummy ordinal N+1 with positive Inf.
                }
                if(!options.ignoreOverlap()){
                    if(options.overlap()==Overlap.ALL){
                        for(int j:bodyOrder.get(p.contig())){
                            budget.add();var f=features.get(j);
                            if(queryCounts.get(p.contig())>=index.sorted.size() && f.start1()>p.end1())break;
                            if(p.contig().equals(f.contig()) && p.start1()<=f.end1() && f.start1()<=p.end1()){
                                selected=j;long start=(long)p.start1()-f.tss1(),finish=(long)p.end1()-f.tss1();
                                distance=(Math.abs(start)<=Math.abs(finish)?start:finish)*(f.strand()=='+'?1:-1);break;
                            }
                        }
                    }
                    if(begin<end){selected=index.firstOverlap(begin,end,budget);distance=0;}
                }
            }
            Disposition status=selected<0?Disposition.NO_FEATURE:selected==features.size()?Disposition.BOUNDARY_DUMMY:Disposition.MATCHED;
            if(status==Disposition.NO_FEATURE)missing++;if(status==Disposition.BOUNDARY_DUMMY)dummies++;
            rows.add(new Row(i,status,selected+1,distance));
        }
        var warnings=new ArrayList<String>();
        warnings.add("Prepared unstranded-peak/sameStrand=FALSE nearest-feature component only; the caller supplies ordered gene/transcript ranges and + or - strands. No TxDb extraction, genomic annotation classes, gene symbols, plotting or whole-package/paper parity is supplied.");
        warnings.add("Original strict-flank duplicates, left choice on absolute-distance ties and final TSS-overlap priority even with ALL are preserved. Per contig, reference NCList indexes queries when their count is smaller than the feature count, giving first original overlapping feature order; otherwise it indexes features in start-ascending/end-descending order with original ordinals for identical ranges. Complete query/feature populations and dependency versions are part of this contract; subsetting or reordering can change an arbitrary overlap choice.");
        if(missing>0)warnings.add(missing+" queries have no source-retained feature. They remain in this disposition ledger; do not silently drop them or infer biological absence.");
        if(dummies>0)warnings.add(dummies+" forced-direction choices retain the original N+1 boundary dummy and positive Inf distance. A dummy is not a gene annotation; preserve it and resolve the explicit direction choice before downstream gene use.");
        cancellation.run();return new Result(rows,budget.operations,warnings);
    }
    private static long signed(int coordinate,Feature feature){return ((long)coordinate-feature.tss1())*(feature.strand()=='+'?1:-1);}
    private static void coordinates(String contig,int start,int end){
        Objects.requireNonNull(contig);
        if(contig.isEmpty() || contig.equals("chrNA") || start<1 || end<start)throw new IllegalArgumentException("Require exact nonempty contigs, positive one-based inclusive 32-bit coordinates and no reserved source chrNA collision");
    }
}
