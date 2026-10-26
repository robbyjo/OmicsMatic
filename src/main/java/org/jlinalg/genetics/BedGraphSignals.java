/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact interval geometry and length-weighted summaries of a piecewise-constant genomic signal. */
public final class BedGraphSignals {
    private BedGraphSignals() { }

    /** Nonempty zero-based half-open interval with one finite signal value. */
    public record Segment(String chromosome,long start,long end,double value) {
        public Segment {
            if(chromosome==null||chromosome.isBlank()||start<0||end<=start||!Double.isFinite(value))throw new IllegalArgumentException("Require chromosome, 0 <= start < end and finite signal");
        }
    }
    /** Integral and coverage of a queried reference interval; missing positions have no measured value. */
    public record Summary(long bases,long coveredBases,double integral) {
        public Summary {if(bases<1||coveredBases<0||coveredBases>bases||!Double.isFinite(integral))throw new IllegalArgumentException("Invalid signal summary");}
        /** Length-weighted mean treating unrepresented bases as explicit zeros. */
        public double meanIncludingZeros(){return integral/bases;}
        /** Length-weighted mean over represented bases; NaN denotes no covered bases. */
        public double meanOverCovered(){return coveredBases==0?Double.NaN:integral/coveredBases;}
    }

    /** Immutable reference-bound track. Segments may be unordered, but overlaps are rejected. */
    public static final class Track {
        private final Map<String,Long> reference;
        private final Map<String,Chromosome> chromosomes=new HashMap<>();
        private final long referenceBases;
        private final boolean nonnegative;

        /** Construct a track without chromosome-name guessing, overlap averaging or implicit coordinate conversion. */
        public Track(Map<String,Long> referenceLengths,List<Segment> segments) {
            if(referenceLengths.isEmpty())throw new IllegalArgumentException("Reference dictionary is empty");
            reference=Map.copyOf(referenceLengths);long total=0;boolean positive=true;
            for(var entry:reference.entrySet()){
                if(entry.getKey().isBlank()||entry.getValue()<1)throw new IllegalArgumentException("Reference names/lengths must be nonempty/positive");
                total=Math.addExact(total,entry.getValue());
            }
            referenceBases=total;var groups=new HashMap<String,List<Segment>>();
            for(Segment segment:segments){
                Long length=reference.get(segment.chromosome());if(length==null||segment.end()>length)throw new IllegalArgumentException("Signal contig/bounds differ from the exact reference");
                groups.computeIfAbsent(segment.chromosome(),ignored->new ArrayList<>()).add(segment);positive&=segment.value()>=0;
            }
            nonnegative=positive;
            for(var entry:groups.entrySet())chromosomes.put(entry.getKey(),new Chromosome(entry.getValue()));
        }
        /** Whether all represented signal values are nonnegative, including an empty track. */
        public boolean isNonnegative(){return nonnegative;}
        /** Query any nonempty reference interval. Touching endpoints have zero overlap. */
        public Summary summarize(String chromosome,long start,long end) {
            Long length=reference.get(chromosome);
            if(length==null||start<0||end<=start||end>length)throw new IllegalArgumentException("Query contig/bounds differ from the exact reference");
            Chromosome values=chromosomes.get(chromosome);return values==null?new Summary(end-start,0,0):values.summarize(start,end);
        }
        /** Summary over every base in the explicitly supplied reference dictionary, including absent contigs. */
        public Summary genomeSummary(){
            long covered=0;var integral=new Sum();
            for(var chromosome:chromosomes.values()){covered=Math.addExact(covered,chromosome.covered[chromosome.size]);integral.add(chromosome.tree[1]);integral.add(chromosome.errors[1]);}
            return new Summary(referenceBases,covered,integral.value());
        }
    }

    private static final class Chromosome {
        private final int size,power;
        private final long[] starts,ends,covered;
        private final double[] values,tree,errors;
        Chromosome(List<Segment> input) {
            input.sort(Comparator.comparingLong(Segment::start).thenComparingLong(Segment::end));size=input.size();
            if(size>10_000_000)throw new IllegalArgumentException("Signal track exceeds ten million segments on one contig");
            starts=new long[size];ends=new long[size];values=new double[size];covered=new long[size+1];
            int capacity=1;while(capacity<size)capacity=Math.multiplyExact(capacity,2);power=capacity;tree=new double[2*power];errors=new double[2*power];
            for(int i=0;i<size;i++){
                Segment segment=input.get(i);starts[i]=segment.start();ends[i]=segment.end();values[i]=segment.value();
                if(i>0&&starts[i]<ends[i-1])throw new IllegalArgumentException("Overlapping bedGraph segments are ambiguous; do not silently sum or average them");
                covered[i+1]=Math.addExact(covered[i],ends[i]-starts[i]);tree[power+i]=finite(values[i]*(ends[i]-starts[i]));
            }
            for(int i=power-1;i>0;i--){var sum=new Sum();sum.add(tree[2*i]);sum.add(errors[2*i]);sum.add(tree[2*i+1]);sum.add(errors[2*i+1]);tree[i]=sum.sum;errors[i]=sum.correction;}
        }
        Summary summarize(long start,long end) {
            int first=firstGreater(ends,start),last=firstAtLeast(starts,end);
            if(first>=last)return new Summary(end-start,0,0);
            if(last==first+1){long n=Math.min(end,ends[first])-Math.max(start,starts[first]);return new Summary(end-start,n,finite(values[first]*n));}
            long left=ends[first]-Math.max(start,starts[first]),right=Math.min(end,ends[last-1])-starts[last-1];
            long represented=Math.addExact(Math.addExact(left,right),covered[last-1]-covered[first+1]);
            var sum=new Sum();sum.add(finite(values[first]*left));sum.add(finite(values[last-1]*right));
            int lo=power+first+1,hi=power+last-1;
            while(lo<hi){if((lo&1)!=0){sum.add(tree[lo]);sum.add(errors[lo++]);}if((hi&1)!=0){--hi;sum.add(tree[hi]);sum.add(errors[hi]);}lo>>=1;hi>>=1;}
            return new Summary(end-start,represented,sum.value());
        }
    }
    private static int firstGreater(long[] values,long target){int lo=0,hi=values.length;while(lo<hi){int mid=(lo+hi)>>>1;if(values[mid]<=target)lo=mid+1;else hi=mid;}return lo;}
    private static int firstAtLeast(long[] values,long target){int lo=0,hi=values.length;while(lo<hi){int mid=(lo+hi)>>>1;if(values[mid]<target)lo=mid+1;else hi=mid;}return lo;}
    /** Neumaier summation, used on disjoint tree nodes rather than subtracting large global prefixes. */
    private static final class Sum {
        private double sum,correction;
        void add(double value){double next=finite(sum+value);correction=finite(correction+(Math.abs(sum)>=Math.abs(value)?(sum-next)+value:(value-next)+sum));sum=next;}
        double value(){return finite(sum+correction);}
    }
    private static double finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Signal integral exceeds finite double range");return value;}
}
