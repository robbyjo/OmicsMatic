/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Streaming raw FASTQ quality metrics; no sampling, trimming or inferred QC thresholds. */
public final class FastqQualityMetrics {
    private FastqQualityMetrics() { }
    /** Cooperative caller-owned interruption/time-budget check. */
    @FunctionalInterface public interface Check { void verify() throws IOException; }
    /** One unbinned one-based position. Percentiles use the inverse empirical CDF. */
    public record Position(int base,long observations,double mean,int q10,int q25,int median,int q75,int q90,
            long a,long c,long g,long t,long n,long other) { }
    /** Whole-file counts and exact raw histograms; empty files retain empty distributions. */
    public record Report(long reads,long bases,int minLength,int maxLength,List<Position> positions,
            Map<Integer,Long> lengths,Map<Integer,Long> meanQualities,Map<Integer,Long> gcPercent) {
        public Report {positions=List.copyOf(positions);lengths=Map.copyOf(lengths);meanQualities=Map.copyOf(meanQualities);gcPercent=Map.copyOf(gcPercent);}
    }
    /** Inspect every record. maximumPositions bounds memory independently of the number of reads. */
    public static Report inspect(Path input,int maximumPositions)throws IOException {
        return inspect(input,maximumPositions,()->{if(Thread.currentThread().isInterrupted())throw new IOException("FASTQ quality inspection interrupted");});
    }
    /** Inspect every record with an additional cooperative caller-owned budget. */
    public static Report inspect(Path input,int maximumPositions,Check check)throws IOException {
        if(maximumPositions<1||maximumPositions>1000000)throw new IllegalArgumentException("maximumPositions must be in1..1000000");
        var qualities=new ArrayList<long[]>();var composition=new ArrayList<long[]>();
        var lengths=new TreeMap<Integer,Long>();var means=new TreeMap<Integer,Long>();var gc=new TreeMap<Integer,Long>();
        long reads=0,bases=0;int min=Integer.MAX_VALUE,max=0;
        try(var reader=new FastqIO.Reader(input)) {
            FastqIO.Read read;
            while((read=reader.next())!=null) {
                check.verify();
                int length=read.sequence().length();if(length>maximumPositions)throw new IOException("Read length exceeds explicit maximum-positions; no partial QC result");
                reads=Math.addExact(reads,1);bases=Math.addExact(bases,length);min=Math.min(min,length);max=Math.max(max,length);
                lengths.merge(length,1L,Math::addExact);long sum=0;int canonical=0,gcBases=0;
                while(qualities.size()<length){qualities.add(new long[94]);composition.add(new long[6]);}
                for(int i=0;i<length;i++) {
                    int q=read.quality().charAt(i)-33;sum+=q;qualities.get(i)[q]=Math.addExact(qualities.get(i)[q],1);
                    int category=switch(Character.toUpperCase(read.sequence().charAt(i))){case 'A'->0;case 'C'->1;case 'G'->2;case 'T'->3;case 'N'->4;default->5;};
                    composition.get(i)[category]=Math.addExact(composition.get(i)[category],1);
                    if(category<4)canonical++;if(category==1||category==2)gcBases++;
                }
                if(length>0)means.merge((int)(sum/length),1L,Math::addExact);
                if(canonical>0)gc.merge((int)Math.round(100.0*gcBases/canonical),1L,Math::addExact);
            }
        }
        var positions=new ArrayList<Position>();
        for(int i=0;i<qualities.size();i++) {
            check.verify();
            long[] histogram=qualities.get(i),counts=composition.get(i);long total=0;double sum=0;
            for(int q=0;q<histogram.length;q++){total=Math.addExact(total,histogram[q]);sum+=q*(double)histogram[q];}
            positions.add(new Position(i+1,total,sum/total,percentile(histogram,total,10),percentile(histogram,total,25),percentile(histogram,total,50),percentile(histogram,total,75),percentile(histogram,total,90),counts[0],counts[1],counts[2],counts[3],counts[4],counts[5]));
        }
        return new Report(reads,bases,reads==0?0:min,max,positions,lengths,means,gc);
    }
    private static int percentile(long[] histogram,long count,int percent) {
        long cumulative=0;for(int q=0;q<histogram.length;q++){cumulative+=histogram[q];if(cumulative/(double)count>=percent/100.0)return q;}
        throw new AssertionError("Empty populated position");
    }
}
