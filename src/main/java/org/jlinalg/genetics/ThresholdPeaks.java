/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Threshold and plateau-summit conventions follow MACS3 (c) 2025 macs3 project team,
 * BSD-3-Clause. Original notice: META-INF/LICENSE-MACS3.txt. */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Thresholds a supplied score track; does not estimate a statistical background or significance. */
public final class ThresholdPeaks {
    private ThresholdPeaks() { }
    /** Precision applies to both input values and the cutoff before comparison/coalescing. */
    public enum Precision { FLOAT32, FLOAT64 }
    /** Missing reference bases are either explicitly zero-filled or rejected. */
    public enum Missing { ZERO, ERROR }
    /** A half-open peak and its absolute summit coordinate, maximum score and qualifying base count. */
    public record Peak(String chromosome,long start,long end,long summit,double height,long qualifyingBases) {
        public Peak {
            if(chromosome==null||chromosome.isBlank()||start<0||end<=start||summit<start||summit>=end||!Double.isFinite(height)||qualifyingBases<1||qualifyingBases>end-start)throw new IllegalArgumentException("Invalid threshold peak");
        }
    }
    /** Immutable output plus the effective numeric threshold and explicit zero-fill/rounding diagnostics. */
    public record Result(List<Peak> peaks,double effectiveCutoff,long roundedSegments,long zeroFilledBases) {
        public Result {peaks=List.copyOf(peaks);}
    }

    /**
     * Merge qualifying runs across gaps at most maxGap, then retain spans at least minLength.
     * Adjacent equal values coalesce before selecting the lower median of maximum-plateau centers.
     * The domain is every declared reference base, including absent contigs; no gaps are inferred away.
     * FLOAT32 positive-cutoff geometry/summits are checked against actual MACS3 bdgpeakcall runs.
     * For zero/signed thresholds the mathematical maximum is used even after a zero-valued plateau;
     * this intentionally avoids the upstream zero-as-uninitialized summit convention.
     */
    public static Result call(Map<String,Long> reference,List<BedGraphSignals.Segment> segments,
            double cutoff,long minLength,long maxGap,Precision precision,Missing missing) {
        if(reference==null||reference.isEmpty()||segments==null||precision==null||missing==null||minLength<1||maxGap<0)throw new IllegalArgumentException("Require reference, segments, precision, missing policy, positive minimum length and nonnegative maximum gap");
        double threshold=convert(cutoff,precision);var groups=new TreeMap<String,List<BedGraphSignals.Segment>>();
        for(var entry:reference.entrySet()){
            if(entry.getKey()==null||entry.getKey().isBlank()||entry.getKey().chars().anyMatch(Character::isISOControl)||entry.getValue()==null||entry.getValue()<1)throw new IllegalArgumentException("Reference names/lengths must be nonempty/positive without control characters");
            groups.put(entry.getKey(),new ArrayList<>());
        }
        for(var segment:segments){
            var chromosome=groups.get(segment.chromosome());
            if(chromosome==null||segment.end()>reference.get(segment.chromosome()))throw new IllegalArgumentException("Signal contig/bounds differ from the exact reference");
            chromosome.add(segment);
        }
        var peaks=new ArrayList<Peak>();long zeros=0,rounded=0;
        for(var entry:groups.entrySet()){
            var input=entry.getValue();input.sort(Comparator.comparingLong(BedGraphSignals.Segment::start).thenComparingLong(BedGraphSignals.Segment::end));
            var scan=new Scan(entry.getKey(),threshold,minLength,maxGap,peaks);long position=0;
            for(var segment:input){
                if(segment.start()<position)throw new IllegalArgumentException("Overlapping bedGraph segments are ambiguous; supply nonoverlapping scores");
                if(segment.start()>position){
                    if(missing==Missing.ERROR)throw new IllegalArgumentException("Unrepresented reference bases; provide complete signal or explicitly choose --missing zero");
                    zeros=Math.addExact(zeros,segment.start()-position);scan.accept(position,segment.start(),0);
                }
                double value=convert(segment.value(),precision);if(value!=segment.value())rounded++;
                scan.accept(segment.start(),segment.end(),value);position=segment.end();
            }
            long length=reference.get(entry.getKey());
            if(position<length){
                if(missing==Missing.ERROR)throw new IllegalArgumentException("Unrepresented reference bases, including absent contigs/trailing sequence; complete signal or explicitly choose --missing zero");
                zeros=Math.addExact(zeros,length-position);scan.accept(position,length,0);
            }
            scan.finish();
        }
        return new Result(peaks,threshold,rounded,zeros);
    }
    private static double convert(double value,Precision precision){
        if(!Double.isFinite(value))throw new IllegalArgumentException("Signal/cutoff must be finite");
        double result=precision==Precision.FLOAT32?(double)(float)value:value;
        if(!Double.isFinite(result))throw new IllegalArgumentException("Signal/cutoff exceeds finite float32 range; choose actual compatible units or explicitly use float64");
        return result;
    }
    private static final class Scan {
        private final String chromosome;
        private final double cutoff;
        private final long minLength,maxGap;
        private final List<Peak> result;
        private final List<Long> centers=new ArrayList<>();
        private boolean pending,active;
        private long runStart,runEnd,start,end,qualifying;
        private double runValue,height;
        Scan(String chromosome,double cutoff,long minLength,long maxGap,List<Peak> result){this.chromosome=chromosome;this.cutoff=cutoff;this.minLength=minLength;this.maxGap=maxGap;this.result=result;}
        void accept(long from,long to,double value){
            if(pending&&runEnd==from&&runValue==value){runEnd=to;return;}
            if(pending)consume();runStart=from;runEnd=to;runValue=value;pending=true;
        }
        void consume(){
            if(runValue<cutoff)return;
            if(active&&runStart-end>maxGap)close();
            if(!active){active=true;start=runStart;qualifying=0;height=Double.NEGATIVE_INFINITY;centers.clear();}
            end=runEnd;qualifying=Math.addExact(qualifying,runEnd-runStart);
            long center=runStart+(runEnd-runStart)/2;
            if(runValue>height){height=runValue;centers.clear();centers.add(center);}else if(runValue==height)centers.add(center);
        }
        void close(){
            if(active&&end-start>=minLength)result.add(new Peak(chromosome,start,end,centers.get((centers.size()-1)/2),height,qualifying));
            active=false;
        }
        void finish(){if(pending)consume();close();}
    }
}
