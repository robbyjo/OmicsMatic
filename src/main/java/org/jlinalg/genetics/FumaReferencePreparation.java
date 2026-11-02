/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Bounded autosomal diploid hardcall preparation for a declared local FUMA
 * reference. No ancestry, assembly, founder status or historical panel is inferred.
 * Sink output is provisional until the entire invocation returns successfully.
 */
public final class FumaReferencePreparation {
    private FumaReferencePreparation() { }

    /** Complete family limits, including marginal self checks and eligible pairs. */
    public record Limits(int maximumVariants,long maximumPairs,long maximumWordWork,long maximumOwnedBytes) {
        public Limits {
            if(maximumVariants<1||maximumPairs<0||maximumWordWork<1||maximumOwnedBytes<65536)
                throw new IllegalArgumentException("Invalid reference preparation capacities");
        }
        public static Limits defaults(){return new Limits(100_000,2_000_000,100_000_000,268_435_456);}
    }
    /** Rank windows apply AFTER the recorded MAF/constant-call filtering. */
    public record Options(double minimumMaf,double ldFloor,FumaLocusDefinition.LdWindow window) {
        public Options {
            if(!Double.isFinite(minimumMaf)||minimumMaf<0||minimumMaf>.5||!Double.isFinite(ldFloor)||ldFloor<0||ldFloor>1||window==null)
                throw new IllegalArgumentException("Invalid MAF, sparse LD floor or explicit window");
        }
    }
    /** Reads an original SNP-major BED row without retaining or changing it. */
    @FunctionalInterface public interface Rows { byte[] read(PlinkBedDataset.Variant variant) throws IOException; }
    /** Canonical selected calls include ALL requested variants, even excluded rows. */
    public interface Sink {
        void capture(PlinkBedDataset.Variant variant,byte[] selectedPackedCalls) throws IOException;
        default void assessment(Assessment assessment) throws IOException { }
        void pair(PlinkBedDataset.Variant first,PlinkBedDataset.Variant second,HardcallLd.Pair estimate,boolean stored) throws IOException;
    }
    public enum Disposition { RETAINED, NO_OBSERVED_CALLS, INSUFFICIENT_OBSERVED_CALLS, ZERO_MAF, CONSTANT_CALLS, BELOW_MINIMUM_MAF }
    /** A marginal decision is kept separately from paired-call LD eligibility. */
    public record Assessment(PlinkBedDataset.Variant variant,HardcallLd.Frequencies frequencies,
                             HardcallLd.Status selfStatus,Disposition disposition) { }
    /** Complete accounting; no undefined pair is admitted as a sparse omission. */
    public record Result(List<Assessment> assessments,List<FumaLocusDefinition.ReferenceVariant> variants,
                         long assessedPairs,long storedPairs,long belowFloorPairs,long wordWork,
                         int peakCachedRows,long estimatedOwnedBytes) {
        public Result {assessments=List.copyOf(assessments);variants=List.copyOf(variants);}
    }
    private record Kept(PlinkBedDataset.Variant variant,HardcallLd.Frequencies frequencies,byte[] selectedHash) { }
    private record Cached(int rank,Kept kept,HardcallLd.Calls calls) { }

    /**
     * Excludes unobserved, MAF-zero, constant-call and below-minimum-MAF variants
     * explicitly. Any undefined eligible pair FAILS the complete request; it is
     * never silently removed, imputed or assigned zero. Only chromosomes 1..22
     * and positive int-range positions are admitted.
     */
    public static Result prepare(List<PlinkBedDataset.Variant> input,HardcallLd.Frame frame,Rows rows,
            Options options,Limits limits,Sink sink,Runnable check) throws IOException {
        Objects.requireNonNull(input);Objects.requireNonNull(frame);Objects.requireNonNull(rows);
        Objects.requireNonNull(options);Objects.requireNonNull(limits);Objects.requireNonNull(sink);Objects.requireNonNull(check);check.run();
        if(frame.selectedSamples()<2||input.isEmpty()||input.size()>limits.maximumVariants())
            throw new IllegalArgumentException("Reference preparation requires at least two selected samples and an admitted nonempty variant family");
        input=List.copyOf(input);
        long words=(frame.selectedSamples()+63L)/64,perPair=Math.multiplyExact(11,words);
        long work=Math.multiplyExact(perPair,input.size());admitWork(work,limits);
        // Conservative owned-storage estimate includes the caller's admitted
        // sample/variant metadata, retained decisions/hashes, and transient rows.
        long base=Math.addExact(65536,Math.addExact(Math.multiplyExact(1024L,frame.totalSamples()),Math.multiplyExact(2048L,input.size())));
        base=Math.addExact(base,Math.multiplyExact(2,(frame.totalSamples()+3L)/4));
        long rowBytes=Math.addExact(256,Math.multiplyExact(24,words));
        admitBytes(Math.addExact(base,Math.multiplyExact(2,rowBytes)),limits);
        var ids=new HashSet<String>();var coordinates=new HashSet<String>();long previous=-1;
        for(var v:input){
            check.run();int chr=chromosome(v);position(v);
            if(!v.resolvedAlleles()||v.ordinal()<=previous||!ids.add(v.id())||!coordinates.add(chr+":"+v.position()))
                throw new IllegalArgumentException("Reference variants require unique IDs/coordinates, resolved alleles and strictly increasing input ordinals: "+v.id());
            previous=v.ordinal();
            // Use the locus type's allele/token admission before provisional output.
            reference(v,0);
        }
        List<Assessment> assessments=new ArrayList<>();List<Kept> kept=new ArrayList<>();
        for(var v:input){
            check.run();var calls=HardcallLd.encode(frame,rows.read(v),check);var frequencies=calls.frequencies();
            var self=HardcallLd.pair(calls,calls,check);Disposition disposition;
            if(frequencies.called()==0)disposition=Disposition.NO_OBSERVED_CALLS;
            else if(frequencies.called()<2)disposition=Disposition.INSUFFICIENT_OBSERVED_CALLS;
            else if(frequencies.minorAlleleFrequency()==0)disposition=Disposition.ZERO_MAF;
            else if(self.status()!=HardcallLd.Status.DEFINED)disposition=Disposition.CONSTANT_CALLS;
            else if(frequencies.minorAlleleFrequency()<options.minimumMaf())disposition=Disposition.BELOW_MINIMUM_MAF;
            else disposition=Disposition.RETAINED;
            byte[] packed=calls.packedCalls();sink.capture(v,packed.clone());
            var assessment=new Assessment(v,frequencies,self.status(),disposition);assessments.add(assessment);sink.assessment(assessment);
            if(disposition==Disposition.RETAINED)kept.add(new Kept(v,frequencies,sha(packed)));
        }
        if(kept.isEmpty())throw new IllegalArgumentException("No nonconstant observed variant remains after the declared MAF filter; inspect the complete variant audit");
        kept.sort(Comparator.comparingInt((Kept k)->chromosome(k.variant())).thenComparingLong(k->k.variant().position()));
        int left=0,peak=1;long planned=0;
        for(int right=0;right<kept.size();right++){
            check.run();while(left<right&&!eligible(kept.get(left),kept.get(right),right-left,options.window()))left++;
            planned=Math.addExact(planned,right-left);peak=Math.max(peak,right-left+1);
        }
        if(planned>limits.maximumPairs())throw new IllegalArgumentException("Complete LD window family exceeds --max-pairs; no prefix result is admitted");
        work=Math.addExact(work,Math.multiplyExact(perPair,planned));admitWork(work,limits);
        long estimated=Math.addExact(base,Math.multiplyExact(rowBytes,Math.addExact(2L,peak)));admitBytes(estimated,limits);
        var cache=new ArrayDeque<Cached>();long assessed=0,stored=0,below=0;
        for(int rank=0;rank<kept.size();rank++){
            check.run();var current=kept.get(rank);
            while(!cache.isEmpty()&&!eligible(cache.getFirst().kept(),current,rank-cache.getFirst().rank(),options.window()))cache.removeFirst();
            var calls=HardcallLd.encode(frame,rows.read(current.variant()),check);
            if(!Arrays.equals(sha(calls.packedCalls()),current.selectedHash()))throw new IOException("Selected genotype calls changed between reference passes: "+current.variant().id());
            for(var prior:cache){
                check.run();var pair=HardcallLd.pair(prior.calls(),calls,check);boolean retain=pair.status()==HardcallLd.Status.DEFINED&&pair.rSquared()>=options.ldFloor();
                sink.pair(prior.kept().variant(),current.variant(),pair,retain);
                if(pair.status()!=HardcallLd.Status.DEFINED)throw new IllegalArgumentException("Undefined eligible LD pair "+prior.kept().variant().id()+" / "+current.variant().id()+": "+pair.status()+" with "+pair.pairedCalls()+" paired calls; resolve missingness/sample/variant eligibility explicitly and rerun. Undefined LD is not below-floor LD");
                assessed++;if(retain)stored++;else below++;
            }
            cache.addLast(new Cached(rank,current,calls));
        }
        check.run();if(assessed!=planned||stored+below!=assessed)throw new IllegalStateException("Incomplete LD family accounting");
        return new Result(assessments,kept.stream().map(k->reference(k.variant(),k.frequencies().minorAlleleFrequency())).toList(),assessed,stored,below,work,peak,estimated);
    }
    private static boolean eligible(Kept a,Kept b,int gap,FumaLocusDefinition.LdWindow w){return chromosome(a.variant())==chromosome(b.variant())&&b.variant().position()-a.variant().position()<=w.maximumDistanceBp()&&gap<=w.maximumIndexGap();}
    private static int chromosome(PlinkBedDataset.Variant v){int c;try{c=Integer.parseInt(v.chromosome());}catch(NumberFormatException e){throw new IllegalArgumentException("Explicit numeric autosomal chromosome 1..22 required: "+v.id(),e);}if(c<1||c>22||!Integer.toString(c).equals(v.chromosome()))throw new IllegalArgumentException("Autosomal diploid preparation supports canonical chromosomes 1..22 only: "+v.id());return c;}
    private static int position(PlinkBedDataset.Variant v){if(v.position()<1||v.position()>Integer.MAX_VALUE)throw new IllegalArgumentException("Positive int-range reference coordinate required: "+v.id());return (int)v.position();}
    private static FumaLocusDefinition.ReferenceVariant reference(PlinkBedDataset.Variant v,double maf){return new FumaLocusDefinition.ReferenceVariant(chromosome(v),position(v),v.allele1(),v.allele2(),v.id(),maf);}
    private static byte[] sha(byte[] raw){try{return MessageDigest.getInstance("SHA-256").digest(raw);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void admitWork(long work,Limits l){if(work>l.maximumWordWork())throw new IllegalArgumentException("Complete reference LD word-work exceeds --max-word-work; no prefix result is admitted");}
    private static void admitBytes(long bytes,Limits l){if(bytes>l.maximumOwnedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete reference storage estimate exceeds --max-owned-bytes or half the current heap; select fewer inputs or explicitly raise the permitted budget/heap");}
}
