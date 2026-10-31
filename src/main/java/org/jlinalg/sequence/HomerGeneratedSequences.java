/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/** Native checks for original HOMER generated-background sequence populations. */
public final class HomerGeneratedSequences {
    private HomerGeneratedSequences() { }

    /**
     * Applies the new genomic selector's fixed N-fraction filter, which has no
     * old Perl five-base minimum or 100000-base maximum.
     * @param dna nonempty normalized A/C/G/T/N
     * @return original selector's retained/excess-N disposition
     */
    public static HomerGenomicSequences.Disposition newSelectionDisposition(String dna){
        long n=counts(dna)[1];return n/(double)dna.length()>.5||n==dna.length()?HomerGenomicSequences.Disposition.EXCESS_N:HomerGenomicSequences.Disposition.RETAINED;
    }

    /**
     * Computes full-precision GC for the new selector, without old six-decimal rounding.
     * @param dna nonempty normalized A/C/G/T/N with at least one canonical base
     * @return GC fraction of canonical bases
     */
    public static double newGc(String dna){long[] counts=counts(dna);if(counts[0]==0)throw new IllegalArgumentException("No canonical bases for new selector GC");return counts[2]/(double)counts[0];}

    private static long[] counts(String dna){
        if(dna==null||dna.isEmpty())throw new IllegalArgumentException("Require nonempty normalized DNA");long canonical=0,n=0,gc=0;
        for(int i=0;i<dna.length();i++){char c=dna.charAt(i);if(c=='N')n++;else if(c=='A'||c=='T')canonical++;else if(c=='C'||c=='G'){canonical++;gc++;}else throw new IllegalArgumentException("Require normalized A/C/G/T/N");}return new long[]{canonical,n,gc};
    }

    /**
     * Builds the original new selector's negative-GC rank thresholds.
     * Repeated thresholds fail exactly where the original reports an error.
     * @param gc full-precision canonical GC fractions of retained targets
     * @param bins requested bin count
     * @return negative-GC thresholds with the original final FLT_MAX sentinel
     */
    public static double[] newBinThresholds(double[] gc,int bins){
        if(gc==null||gc.length==0||bins<1||bins>100)throw new IllegalArgumentException("Require targets and1..100new selector bins");var values=new double[gc.length];
        for(int i=0;i<gc.length;i++){if(!Double.isFinite(gc[i])||gc[i]<0||gc[i]>1)throw new IllegalArgumentException("Invalid canonical GC fraction");values[i]=-Math.abs(gc[i]);}java.util.Arrays.sort(values);
        long per=(gc.length+(long)bins-1)/bins;var thresholds=new double[bins];
        for(int i=0;i<bins;i++){thresholds[i]=values[(int)Math.min(values.length-1,(i+1)*per)];if(i>0&&thresholds[i]==thresholds[i-1])throw new IllegalArgumentException("Original new selector has repeated GC thresholds;choose fewer bins explicitly");}thresholds[bins-1]=Float.MAX_VALUE;return thresholds;
    }

    /**
     * Assigns the final new-selector GC bin using its inclusive comparison.
     * @param gc full-precision canonical GC
     * @param thresholds thresholds returned by newBinThresholds
     * @return zero-based bin
     */
    public static int newBin(double gc,double[] thresholds){
        if(!Double.isFinite(gc)||gc<0||gc>1||thresholds==null||thresholds.length<1)throw new IllegalArgumentException("Invalid new selector bin input");
        for(int i=0;i<thresholds.length;i++)if(-gc<=thresholds[i])return i;throw new IllegalArgumentException("New selector thresholds omit a valid GC fraction");
    }

    /**
     * Counts canonical kmer windows using the original selector's little-endian
     * A/C/G/T column order and per-position or pooled denominators.
     * @param sequences normalized, nonempty A/C/G/T/N sequences
     * @param kmer window width from one through four
     * @param positional whether to use a separate denominator at each position
     * @param positions declared profile rows for positional counting
     * @return frequencies; positional trailing rows without windows are zero
     */
    public static double[][] newKmerProfile(java.util.List<String> sequences,int kmer,boolean positional,int positions){
        if(sequences==null||sequences.isEmpty()||kmer<1||kmer>4||positions<1||positions>100001)throw new IllegalArgumentException("Invalid bounded new selector profile input");
        int rows=positional?positions:1,columns=1<<(2*kmer);long cells=(long)rows*columns;
        if(cells*8>Runtime.getRuntime().maxMemory()/8)throw new IllegalArgumentException("Native new selector profile exceeds heap capacity");
        var result=new double[rows][columns];var totals=new long[rows];
        for(String dna:sequences){counts(dna);for(int p=0;p<=dna.length()-kmer;p++){
            int index=0;boolean good=true;for(int j=0;j<kmer;j++){int base="ACGT".indexOf(dna.charAt(p+j));if(base<0){good=false;break;}index|=base<<(2*j);}if(!good)continue;
            int row=positional?p:0;if(row>=rows)throw new IllegalArgumentException("Positional sequence windows exceed declared profile rows");result[row][index]++;totals[row]=Math.addExact(totals[row],1);
        }}
        for(int p=0;p<rows;p++)if(totals[p]>0)for(int j=0;j<columns;j++)result[p][j]/=totals[p];return result;
    }

    /**
     * Reconstructs the original old preparser's extractor output on an LF FASTA
     * staged at 80 residues per line. This deliberately preserves upstream left
     * padding/body extension and right truncation rather than repairing them.
     *
     * @param genome case-preserving indexed reference bases
     * @param start1 raw one-based inclusive start, which may be nonpositive
     * @param end1 raw one-based inclusive end
     * @param strand plus or minus orientation
     * @param maskLowercase whether lowercase bases become N
     * @param maximumOutput maximum allowed produced residues
     * @return original normalized DNA, or null when no sequence is produced
     * @throws IOException if the declared/output bounds or reference read fails
     */
    public static String oldPreparsedExtraction(SeqtkMethods.Bases genome,long start1,long end1,
                                                char strand,boolean maskLowercase,int maximumOutput)throws IOException {
        if(genome==null||genome.length()<1||genome.length()>Integer.MAX_VALUE||end1<start1||start1< -100001||end1>Integer.MAX_VALUE||end1-start1>=100001||maximumOutput<1||strand!='+'&&strand!='-')throw new IOException("Invalid bounded original preparsed extraction");
        long length=genome.length();
        if(start1-1>=length)return null;
        long loaded=end1<length?Math.min(length,Math.multiplyExact(Math.floorDiv(end1,80)+1,80)):length;
        if(loaded<1)throw new IOException("Original preparsed extraction requires a nonnegative window center");
        long span=(end1<length?end1:length)-start1+1,prefix=Math.max(0,1-start1),index=Math.max(0,start1-1);
        long body=loaded>=index+span?span:0,tail=Math.max(0,index+span-loaded),output=Math.addExact(prefix,Math.addExact(body,tail));
        if(output>maximumOutput||output>Integer.MAX_VALUE)throw new IOException("Original preparsed extraction exceeds output capacity");
        var result=new StringBuilder((int)output);result.append("N".repeat((int)prefix));
        for(long i=0;i<body;i++){
            char base=genome.base(index+i);
            if(base>='a'&&base<='z')base=maskLowercase?'N':(char)(base-32);
            if(base<'A'||base>'Z')throw new IOException("Original preparsed reference requires alphabetic sequence");
            result.append(base=='A'||base=='C'||base=='G'||base=='T'?base:'N');
        }
        result.append("N".repeat((int)tail));
        if(strand=='-'){
            var reverse=new StringBuilder(result.length());
            for(int i=result.length()-1;i>=0;i--)reverse.append(switch(result.charAt(i)){case 'A'->'T';case 'C'->'G';case 'G'->'C';case 'T'->'A';default->'N';});
            return reverse.toString();
        }
        return result.toString();
    }

    /**
     * Computes deterministic per-bin quotas used before the old unseeded draw.
     * Unsupported bins are omitted; unmet quota carries toward smaller bins.
     *
     * @param targets native target counts by original integer bin
     * @param available native eligible pool counts by original integer bin
     * @param requestedBackground requested background count after target subtraction
     * @return selected count for each supported bin, in descending bin order
     */
    public static Map<Integer,Long> oldSelectionQuotas(Map<Integer,Long> targets,
            Map<Integer,Long> available,long requestedBackground){
        if(targets==null||available==null||requestedBackground<1||requestedBackground>1000000000L)throw new IllegalArgumentException("Require bounded target/pool counts and a positive background count");
        var bins=new TreeMap<Integer,Long>(java.util.Comparator.reverseOrder());long total=0;
        for(var entry:targets.entrySet()){
            int bin=entry.getKey();long count=entry.getValue(),pool=available.getOrDefault(bin,0L);
            if(bin<0||bin>11||count<0||count>1000000000L||pool<0||pool>1000000000L)throw new IllegalArgumentException("Invalid original bin/count");
            if(count>0&&pool>0){bins.put(bin,count);total=Math.addExact(total,count);}
        }
        if(total>1000000000L)throw new IllegalArgumentException("Original target count exceeds capacity");
        for(var entry:available.entrySet())if(entry.getKey()<0||entry.getKey()>11||entry.getValue()<0||entry.getValue()>1000000000L)throw new IllegalArgumentException("Invalid original pool bin/count");
        var result=new TreeMap<Integer,Long>(java.util.Comparator.reverseOrder());long leftover=0;
        for(var entry:bins.entrySet()){
            long quota=Math.addExact((long)Math.floor((double)entry.getValue()/total*requestedBackground+0.49999999),leftover);
            long selected=Math.min(quota,available.get(entry.getKey()));leftover=quota-selected;result.put(entry.getKey(),selected);
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
