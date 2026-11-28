/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics.contact;

import java.io.*;
import java.util.*;

/** Complete HiC-Pro valid-pair projection and independent raw Cooler pixel accounting.
 * Coordinates are source one-based positions; bins/pixels use zero-based IDs.
 * Original read IDs, contig names, pair order and source missing-weight markers are retained.
 */
public final class HicProContactAudit {
    private HicProContactAudit() { }
    public record Limits(long maximumPairs,int maximumBins,int maximumPixels,int maximumLineCharacters,long maximumDecodedCharacters) {
        public Limits {if(maximumPairs<1||maximumBins<1||maximumPixels<1||maximumLineCharacters<128||maximumDecodedCharacters<1)throw new IllegalArgumentException("Invalid complete contact-audit capacity");}
    }
    public record Pair(String readId,String firstChromosome,long firstPosition,String secondChromosome,long secondPosition,String firstStrand,String secondStrand) {
        public String projected(){return String.join("\t",readId,firstChromosome,Long.toString(firstPosition),secondChromosome,Long.toString(secondPosition),firstStrand,secondStrand);}
    }
    public record Chromosome(String name,long length,int firstBin,int bins) { }
    public record Matrix(long binSize,List<Chromosome> chromosomes,int bins,Map<Long,Long> pixels,long pairs,long cisPairs,long transPairs) {
        public Matrix {chromosomes=List.copyOf(chromosomes);pixels=Map.copyOf(pixels);}
    }
    public record Result(long pairs,long cisPairs,long transPairs,List<Matrix> matrices) {public Result{matrices=List.copyOf(matrices);}}
    public record Verified(int bins,int missingWeightBins,int pixels,long countSum) { }

    /** Exact source awk field projection for complete, valid HiC-Pro records (at least seven fields). */
    public static Pair project(String line)throws IOException {
        if(line==null||line.isEmpty())throw new IOException("Missing original valid-pair record");
        String[] f=line.replaceFirst("^[ \\t]+","").replaceFirst("[ \\t]+$","").split("[ \\t]+",-1);
        if(f.length<7||!atom(f[0])||!atom(f[1])||!atom(f[4])||!Set.of("+","-").contains(f[3])||!Set.of("+","-").contains(f[6]))throw new IOException("Invalid source valid-pair fields/strands; no identifier or coordinate repair");
        return new Pair(f[0],f[1],positive(f[2]),f[4],positive(f[5]),f[3],f[6]);
    }
    private static boolean atom(String s){return !s.isEmpty()&&s.chars().noneMatch(c->Character.isWhitespace(c)||c<32||c==127);}
    private static long positive(String s)throws IOException {
        if(!s.matches("[1-9][0-9]*"))throw new IOException("Require literal positive one-based source positions");
        try{return Long.parseLong(s);}catch(NumberFormatException e){throw new IOException("Source position overflow",e);}
    }
    private static long nonnegative(String s)throws IOException {
        if(!s.matches("0|[1-9][0-9]*"))throw new IOException("Require a complete nonnegative integer");
        try{return Long.parseLong(s);}catch(NumberFormatException e){throw new IOException("Source count/index overflow",e);}
    }
    /** Read every source pair and every projected row in the same order. No row is sampled or dropped.
     * The optional complete FASTQ-ID set verifies membership; duplicate source IDs remain permitted.
     */
    public static Result inspect(Reader validPairs,Reader projectedPairs,Map<String,Long> references,List<Long> binSizes,Set<String> completeReadIds,Limits limits)throws IOException {
        Objects.requireNonNull(limits);if(references.isEmpty()||binSizes.isEmpty()||new HashSet<>(binSizes).size()!=binSizes.size())throw new IllegalArgumentException("Declare complete references and distinct bin sizes");
        var builders=new ArrayList<Builder>();for(long size:binSizes)builders.add(new Builder(references,size,limits));long pairs=0,cis=0,trans=0;
        try(var original=new Lines(validPairs,limits);var projected=new Lines(projectedPairs,limits)){
            String line;while((line=original.next())!=null){
                if(++pairs>limits.maximumPairs())throw new IOException("Complete valid-pair row capacity exceeded; no truncation");Pair p=project(line);String row=projected.next();
                if(!p.projected().equals(row))throw new IOException("Original HiC-Pro projection/ID/position/strand/order differs at pair "+pairs);
                if(completeReadIds!=null&&!completeReadIds.contains(p.readId()))throw new IOException("Original valid pair has no member in the complete captured FASTQ cohort: "+p.readId());
                if(p.firstChromosome().equals(p.secondChromosome()))cis++;else trans++;
                for(var b:builders)b.add(p);
            }
            if(projected.next()!=null)throw new IOException("Extra projected pair rows; no partial cohort admitted");
        }
        var matrices=new ArrayList<Matrix>();for(var b:builders)matrices.add(new Matrix(b.size,b.chromosomes,b.bins,b.pixels,pairs,cis,trans));return new Result(pairs,cis,trans,matrices);
    }
    private static final class Builder {
        final long size;final Limits limits;final List<Chromosome> chromosomes=new ArrayList<>();final Map<String,Chromosome> byName=new HashMap<>();final Map<Long,Long> pixels=new HashMap<>();int bins;
        Builder(Map<String,Long> reference,long size,Limits limits){
            if(size<1)throw new IllegalArgumentException("Require positive source bin size");this.size=size;this.limits=limits;
            for(var row:reference.entrySet()){
                if(!atom(row.getKey())||row.getValue()==null||row.getValue()<1)throw new IllegalArgumentException("Invalid complete literal reference sequence");
                long count=1+(row.getValue()-1)/size;if(count>limits.maximumBins()-bins)throw new IllegalArgumentException("Complete reference-bin capacity exceeded");
                var chr=new Chromosome(row.getKey(),row.getValue(),bins,(int)count);chromosomes.add(chr);byName.put(row.getKey(),chr);bins=Math.addExact(bins,(int)count);
            }
        }
        int bin(String name,long pos)throws IOException {var chr=byName.get(name);if(chr==null||pos<1||pos>chr.length())throw new IOException("Original pair is outside the exact literal reference: "+name+":"+pos);return Math.addExact(chr.firstBin(),Math.toIntExact((pos-1)/size));}
        void add(Pair p)throws IOException {
            int a=bin(p.firstChromosome(),p.firstPosition()),b=bin(p.secondChromosome(),p.secondPosition());long key=key(Math.min(a,b),Math.max(a,b));
            if(!pixels.containsKey(key)&&pixels.size()>=limits.maximumPixels())throw new IOException("Complete raw contact-pixel capacity exceeded; no truncation");pixels.merge(key,1L,Math::addExact);
        }
    }
    public static long key(int first,int second){if(first<0||second<first)throw new IllegalArgumentException("Require ordered nonnegative pixel IDs");return((long)first<<32)|(second&0xffffffffL);}

    /** Verify every exported HDF5 bin and raw integer pixel against all source valid pairs.
     * NaN weight markers stay explicit; no weight is filled and no contact is repaired.
     */
    public static Verified verify(Matrix expected,Reader completeBins,Reader completePixels,Limits limits)throws IOException {
        int bins=0,missing=0,pixels=0;long sum=0,previous=-1;
        try(var rows=new Lines(completeBins,limits)){
            if(!"bin_id\tchrom\tstart\tend\tweight".equals(rows.next()))throw new IOException("Complete bin-export schema differs");
            for(var chr:expected.chromosomes())for(int i=0;i<chr.bins();i++){
                String line=rows.next();if(line==null)throw new IOException("Missing complete reference bins");String[] f=line.split("\t",-1);long start=Math.multiplyExact((long)i,expected.binSize()),end=Math.min(chr.length(),Math.addExact(start,expected.binSize()));
                if(f.length!=5||nonnegative(f[0])!=bins||!f[1].equals(chr.name())||nonnegative(f[2])!=start||nonnegative(f[3])!=end)throw new IOException("Exact source contig/bin order/coordinates differ at bin "+bins);
                double weight;try{weight=Double.parseDouble(f[4]);}catch(NumberFormatException e){throw new IOException("Invalid original weight token",e);}
                if(Double.isNaN(weight)){if(!f[4].equals("NaN"))throw new IOException("Missing-weight export marker differs");missing++;}else if(!Double.isFinite(weight)||weight<0)throw new IOException("Unassessable original nonfinite/negative weight; no replacement admitted");bins++;
            }
            if(rows.next()!=null)throw new IOException("Extra original reference-bin records");
        }
        try(var rows=new Lines(completePixels,limits)){
            if(!"bin1_id\tbin2_id\tcount".equals(rows.next()))throw new IOException("Complete raw pixel-export schema differs");String line;
            while((line=rows.next())!=null){
                if(++pixels>limits.maximumPixels())throw new IOException("Complete original pixel capacity exceeded");String[] f=line.split("\t",-1);if(f.length!=3)throw new IOException("Raw pixel width differs");long a=nonnegative(f[0]),b=nonnegative(f[1]),count=nonnegative(f[2]);if(a>b||b>=expected.bins())throw new IOException("Original pixel IDs outside exact ordered reference bins");long key=key((int)a,(int)b);if(key<=previous)throw new IOException("Duplicate/unordered original pixels; no sorting or consolidation admitted");previous=key;Long wanted=expected.pixels().get(key);if(wanted==null||wanted!=count)throw new IOException("Original raw contact count differs from complete native pair reconstruction at "+a+","+b);sum=Math.addExact(sum,count);
            }
        }
        if(bins!=expected.bins()||pixels!=expected.pixels().size()||sum!=expected.pairs())throw new IOException("Complete source pair/bin/pixel population or count mass differs");return new Verified(bins,missing,pixels,sum);
    }
    private static final class Lines implements AutoCloseable {
        final Reader input;final Limits limits;long decoded;
        Lines(Reader input,Limits limits){this.input=input instanceof BufferedReader?input:new BufferedReader(input,65536);this.limits=limits;}
        String next()throws IOException {
            var s=new StringBuilder();int c;
            while((c=input.read())!=-1){if(++decoded>limits.maximumDecodedCharacters())throw new IOException("Complete decoded contact-input capacity exceeded");if(c=='\n')return s.toString();if(c=='\r')throw new IOException("Require unchanged original LF-delimited contact exports");if(s.length()>=limits.maximumLineCharacters())throw new IOException("Complete contact-input line capacity exceeded");s.append((char)c);}
            return s.length()==0?null:s.toString();
        }
        public void close()throws IOException{input.close();}
    }
}
