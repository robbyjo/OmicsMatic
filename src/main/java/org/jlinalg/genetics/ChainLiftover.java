/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import htsjdk.samtools.liftover.LiftOver;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.GenomicIntervals.Interval;

/** Basic Java chain mapping using the same HTSJDK engine as local SNP-instrument projection. */
public final class ChainLiftover {
    /** No single qualifying result is distinct from a confidently mapped interval. */
    public enum Status { MAPPED, NO_CHAIN, BELOW_MIN_MATCH, NOT_UNIQUELY_MAPPED }
    /** Original interval is always retained, including failures; diagnostics are human-readable upstream text. */
    public record Result(Interval source,Status status,Interval target,List<String> diagnostics) {
        public Result {diagnostics=List.copyOf(diagnostics);}
    }
    /** Chain header reference lengths and bounded structural-validation counts. */
    public record ChainSummary(long chains,long blocks,Map<String,Long> fromLengths,Map<String,Long> toLengths) {
        public ChainSummary {fromLengths=Map.copyOf(fromLengths);toLengths=Map.copyOf(toLengths);}
    }
    private final LiftOver engine;
    private final Map<String,Long> from;
    private final Map<String,Long> to;
    private final ChainSummary summary;

    /**
     * Read plain/gzip chain bytes, validate block geometry and require dictionaries covering every
     * chain header contig with its exact length. Chain t/from and q/to direction is never inferred.
     * HTSJDK's basic engine has signed-32-bit coordinate/chain-ID limits and rejects multiple hits.
     */
    public ChainLiftover(Path chain,Map<String,Long> from,Map<String,Long> to)throws IOException {
        this.from=reference(from);this.to=reference(to);summary=inspect(chain);
        match(summary.fromLengths(),this.from,"source/t");match(summary.toLengths(),this.to,"target/q");
        try(var input=open(chain)){engine=new LiftOver(input,chain.toString());}
        catch(RuntimeException failure){throw new IOException("HTSJDK rejected the chain: "+failure.getMessage(),failure);}
        // Every failed input and its native diagnostic text is returned to the caller, avoiding global
        // stderr logging per candidate while retaining the exact below-threshold counter semantics.
        engine.setShouldLogFailedIntervalsBelowThreshold(false);
    }
    /** Validated chain statistics; reference length agreement does not prove assembly identity. */
    public ChainSummary summary(){return summary;}

    /**
     * Map a nonempty zero-based half-open interval with explicit minimum aligned-base fraction.
     * No split mappings, SNP allele transformation, score/metadata propagation or assembly guessing.
     * Calls are serialized because HTSJDK's exact below-threshold diagnostic counter is mutable.
     */
    public synchronized Result map(Interval query,double minMatch) {
        if(!Double.isFinite(minMatch)||minMatch<=0||minMatch>1)throw new IllegalArgumentException("minMatch must be in (0,1]");
        Long length=from.get(query.chromosome());if(length==null||query.end()>length||query.end()>Integer.MAX_VALUE)throw new IllegalArgumentException("Source interval is outside its exact dictionary/32-bit bounds: "+query.id());
        var original=new htsjdk.samtools.util.Interval(query.chromosome(),Math.toIntExact(query.start()+1),Math.toIntExact(query.end()),query.strand().equals("-"),query.id());
        long before=engine.getFailedIntervalsBelowThreshold();var mapped=engine.liftOver(original,minMatch);
        if(mapped==null){
            var candidates=engine.diagnosticLiftover(original);var details=candidates.stream().map(Object::toString).toList();
            Status status=candidates.isEmpty()?Status.NO_CHAIN:engine.getFailedIntervalsBelowThreshold()>before?Status.BELOW_MIN_MATCH:Status.NOT_UNIQUELY_MAPPED;
            // The final category includes ambiguous hits and all-gap chain overlaps. Do not parse
            // diagnostic toString/float percentages to invent an exact ambiguity classifier.
            return new Result(query,status,null,details);
        }
        Long targetLength=to.get(mapped.getContig());if(targetLength==null||mapped.getStart()<1||mapped.getEnd()>targetLength)throw new IllegalStateException("Mapped interval exceeds the verified target dictionary");
        String strand=query.strand().equals(".")?".":mapped.isNegativeStrand()?"-":"+";
        return new Result(query,Status.MAPPED,new Interval(mapped.getContig(),mapped.getStart()-1L,mapped.getEnd(),query.id(),strand),List.of());
    }
    /** Validate canonical chain headers, all block/gap sums, unique IDs and memory-work bounds. */
    public static ChainSummary inspect(Path chain)throws IOException {
        if(!Files.isRegularFile(chain)||Files.size(chain)>1024L*1024*1024)throw new IOException("Missing chain or chain exceeds 1 GiB encoded input limit");
        var from=new LinkedHashMap<String,Long>();var to=new LinkedHashMap<String,Long>();var ids=new HashSet<Long>();
        long chains=0,blocks=0,characters=0,fromPosition=0,toPosition=0,fromEnd=0,toEnd=0,lineNumber=0;boolean active=false;
        try(var reader=new BufferedReader(new InputStreamReader(open(chain),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){
            lineNumber++;characters+=line.length()+1L;if(characters>512L*1024*1024)throw new IOException("Decoded chain exceeds 512 MiB character-work limit");
            if(line.isBlank()||line.startsWith("#"))continue;String[] f=line.strip().split("\\s+");
            try{
                if(f[0].equals("chain")){
                    if(active||f.length!=13)throw new IOException("Incomplete chain or invalid header width at line "+lineNumber);
                    double score=Double.parseDouble(f[1]);if(!Double.isFinite(score)||score<0)throw new IOException("Invalid chain score");
                    long fromSize=positive(f[3]),toSize=positive(f[8]);if(!f[4].equals("+")||!List.of("+","-").contains(f[9]))throw new IOException("Chain source strand must be + and target strand + or -");
                    fromPosition=nonnegative(f[5]);fromEnd=positive(f[6]);toPosition=nonnegative(f[10]);toEnd=positive(f[11]);
                    if(fromPosition>=fromEnd||fromEnd>fromSize||toPosition>=toEnd||toEnd>toSize)throw new IOException("Chain bounds differ from declared sequence sizes");
                    long id=nonnegative(f[12]);if(!ids.add(id))throw new IOException("Duplicate chain ID: "+id);
                    length(from,f[2],fromSize);length(to,f[7],toSize);active=true;if(++chains>1_000_000)throw new IOException("Chain exceeds one million chain headers");
                }else{
                    if(!active||f.length!=1&&f.length!=3)throw new IOException("Expected a chain block with one or three integer fields at line "+lineNumber);
                    long size=positive(f[0]);fromPosition+=size;toPosition+=size;if(++blocks>10_000_000)throw new IOException("Chain exceeds ten million alignment blocks");
                    if(f.length==1){if(fromPosition!=fromEnd||toPosition!=toEnd)throw new IOException("Final chain block does not reach both header endpoints");active=false;}
                    else{fromPosition+=nonnegative(f[1]);toPosition+=nonnegative(f[2]);if(fromPosition>=fromEnd||toPosition>=toEnd)throw new IOException("Nonterminal chain block/gap reaches or exceeds a header endpoint");}
                }
            }catch(IllegalArgumentException failure){throw new IOException("Invalid chain numeric value at line "+lineNumber+": "+failure.getMessage(),failure);}
        }}
        if(active||chains==0)throw new IOException("Empty or unterminated chain file");return new ChainSummary(chains,blocks,from,to);
    }
    private static InputStream open(Path path)throws IOException {
        var input=new BufferedInputStream(Files.newInputStream(path));input.mark(2);int first=input.read(),second=input.read();input.reset();
        try{return first==0x1f&&second==0x8b?new GZIPInputStream(input):input;}catch(IOException failure){input.close();throw failure;}
    }
    private static long nonnegative(String token){long value=Long.parseLong(token);if(value<0||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Chain integers require [0,2147483647]");return value;}
    private static long positive(String token){long value=nonnegative(token);if(value==0)throw new IllegalArgumentException("Positive chain size required");return value;}
    private static void length(Map<String,Long> lengths,String name,long size)throws IOException {Long previous=lengths.putIfAbsent(name,size);if(previous!=null&&previous!=size)throw new IOException("Chain contig has conflicting sizes: "+name);}
    private static Map<String,Long> reference(Map<String,Long> values){if(values==null||values.isEmpty())throw new IllegalArgumentException("Explicit nonempty source/target dictionaries required");var copy=Map.copyOf(values);for(var e:copy.entrySet())if(e.getKey().isBlank()||e.getValue()<1||e.getValue()>Integer.MAX_VALUE)throw new IllegalArgumentException("Reference labels/lengths must be nonempty and within signed-32-bit bounds");return copy;}
    private static void match(Map<String,Long> chain,Map<String,Long> dictionary,String side)throws IOException {for(var e:chain.entrySet())if(!e.getValue().equals(dictionary.get(e.getKey())))throw new IOException("Chain "+side+" contig missing or length differs: "+e.getKey()+"; supply a full exact dictionary matching this chain direction");}
}
