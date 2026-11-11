/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.*;
import org.jlinalg.genetics.ShapeitGenotypes;

/**
 * Original c34d4db common-reader state for complete ordinary BCF/VCF GT rows.
 * Derived from genotype_reader_reading.cpp; the original MIT notices are retained
 * in META-INF/licenses/SHAPEIT5-MIT.txt and SHAPEIT5-XCF-MIT.txt.
 * No binary decoder, haploid/pedigree adjustment or phasing is provided here.
 */
final class ShapeitCommonCalls {
    private ShapeitCommonCalls() { }
    record Limits(int samplesPerPanel,long retainedBytes) {
        Limits {if(samplesPerPanel<1||retainedBytes<1)throw new IllegalArgumentException("Require positive complete common-reader capacities");}
        static Limits defaults(){return new Limits(1000000,536870912L);}
    }
    static final class Header {
        private final List<String> target,reference,scaffold;
        private final int[] scaffoldToTarget;
        private final long duplicateTargetNames,duplicateScaffoldNames,unmatchedScaffoldNames;
        private final Limits limits;
        Header(List<String> target,List<String> reference,List<String> scaffold,Limits limits){
            Objects.requireNonNull(limits);checkSize(target,limits);checkSize(reference,limits);checkSize(scaffold,limits);
            if(target.isEmpty())throw new IllegalArgumentException("An empty target population cannot enter the original common reader");
            memory(Math.multiplyExact(512L,(long)target.size()+reference.size()+scaffold.size()),limits);
            this.target=List.copyOf(target);this.reference=List.copyOf(reference);this.scaffold=List.copyOf(scaffold);this.limits=limits;
            var names=new HashMap<String,Integer>();long duplicates=0,scafDuplicates=0,unmatched=0;
            for(int i=0;i<this.target.size();i++)if(names.putIfAbsent(this.target.get(i),i)!=null)duplicates++;
            scaffoldToTarget=new int[this.scaffold.size()];var seen=new HashSet<String>();
            for(int i=0;i<this.scaffold.size();i++){String name=this.scaffold.get(i);scaffoldToTarget[i]=names.getOrDefault(name,-1);if(scaffoldToTarget[i]<0)unmatched++;if(!seen.add(name))scafDuplicates++;}
            duplicateTargetNames=duplicates;duplicateScaffoldNames=scafDuplicates;unmatchedScaffoldNames=unmatched;
        }
        List<String> target(){return target;}
        List<String> reference(){return reference;}
        List<String> scaffold(){return scaffold;}
        long duplicateTargetNames(){return duplicateTargetNames;}
        long duplicateScaffoldNames(){return duplicateScaffoldNames;}
        long unmatchedScaffoldNames(){return unmatchedScaffoldNames;}
    }
    enum ScaffoldReason { APPLIED, UNKNOWN_SAMPLE, NOT_HETEROZYGOUS, LITERAL_MISSING, UNPHASED, TARGET_NOT_HETEROZYGOUS }
    enum Risk {
        TARGET_PHASE_MARKED_MISSING_READ_AS_REFERENCE,TARGET_VECTOR_END,
        REFERENCE_PHASE_MARKED_MISSING_READ_AS_REFERENCE,REFERENCE_VECTOR_END,
        SCAFFOLD_PHASE_MARKED_MISSING,SCAFFOLD_VECTOR_END,
        DUPLICATE_TARGET_SAMPLE,DUPLICATE_SCAFFOLD_SAMPLE,UNMATCHED_SCAFFOLD_SAMPLE,
        OBSERVED_ALLELES_IGNORED_IN_TARGET_MISSING_CALL
    }
    record Target(ShapeitGenotypes.WireCall raw,int initialPacked,int scaffoldPacked) { }
    record Scaffold(ShapeitGenotypes.WireCall raw,int mappedTarget,ScaffoldReason reason) { }
    record Counts(long homozygousReference,long heterozygous,long homozygousAlternate,long missing,long scaffoldApplied,
                  long referenceAlleles0,long referenceAlleles1,long cref,long calt,long cmis,long ignoredObservedAllelesInMissingCalls) { }
    record Row(List<Target> target,List<ShapeitGenotypes.WireCall> reference,List<Boolean> referenceHaplotypes,
               List<Scaffold> scaffold,Counts counts,Set<Risk> risks) {
        Row {target=List.copyOf(target);reference=List.copyOf(reference);referenceHaplotypes=List.copyOf(referenceHaplotypes);scaffold=List.copyOf(scaffold);risks=Collections.unmodifiableSet(risks.isEmpty()?EnumSet.noneOf(Risk.class):EnumSet.copyOf(risks));}
    }
    /** Null scaffold row means the original synchronized stream has no biallelic scaffold record. */
    static Row decode(Header header,List<ShapeitGenotypes.WireCall> target,List<ShapeitGenotypes.WireCall> reference,List<ShapeitGenotypes.WireCall> scaffold){
        Objects.requireNonNull(header);Objects.requireNonNull(target);Objects.requireNonNull(reference);
        if(target.size()!=header.target.size()||reference.size()!=header.reference.size()||scaffold!=null&&scaffold.size()!=header.scaffold.size())throw new IllegalArgumentException("Complete original target/reference/scaffold sample dimensions differ");
        memory(Math.addExact(Math.multiplyExact(320L,target.size()),Math.addExact(Math.multiplyExact(96L,reference.size()),Math.multiplyExact(192L,scaffold==null?0:scaffold.size()))),header.limits);
        var risks=EnumSet.noneOf(Risk.class);
        if(header.duplicateTargetNames!=0)risks.add(Risk.DUPLICATE_TARGET_SAMPLE);
        if(header.duplicateScaffoldNames!=0)risks.add(Risk.DUPLICATE_SCAFFOLD_SAMPLE);
        if(header.unmatchedScaffoldNames!=0)risks.add(Risk.UNMATCHED_SCAFFOLD_SAMPLE);
        List<ShapeitGenotypes.Cell> initial=ShapeitGenotypes.decodeDiploidStorageRow(target,header.limits.samplesPerPanel());
        int[] packed=new int[target.size()];long[] genotypes=new long[5];long cref=0,calt=0,cmis=0,ignored=0;
        for(int i=0;i<initial.size();i++){
            var cell=initial.get(i);packed[i]=cell.packed();
            if(cell.missing()){genotypes[3]++;cmis++;ignored+=cell.source().observedAlleles();}
            else{genotypes[cell.dosage()]++;cref+=2-cell.dosage();calt+=cell.dosage();}
            if(cell.missingAlleleReadAsReference())risks.add(Risk.TARGET_PHASE_MARKED_MISSING_READ_AS_REFERENCE);
            if(cell.vectorEndInSecondSlot())risks.add(Risk.TARGET_VECTOR_END);
        }
        if(ignored!=0)risks.add(Risk.OBSERVED_ALLELES_IGNORED_IN_TARGET_MISSING_CALL);
        requireStorage(reference,"reference");var refHaplotypes=new ArrayList<Boolean>();long ref0=0,ref1=0;
        for(var raw:reference){
            int g0=slot(raw,0),g1=slot(raw,1);
            if(g0==0||g1==0)throw new IllegalArgumentException("REFERENCE_LITERAL_MISSING: original common reader rejects literal-zero missing reference GT; supply complete phased reference genotypes");
            if((g1&1)==0)throw new IllegalArgumentException("REFERENCE_UNPHASED: original common reader requires the second reference allele to carry a phase bit");
            boolean a0=alt(g0),a1=alt(g1);refHaplotypes.add(a0);refHaplotypes.add(a1);
            ref1+=(a0?1:0)+(a1?1:0);ref0+=(!a0?1:0)+(!a1?1:0);
            if(raw.missingAlleles()!=0)risks.add(Risk.REFERENCE_PHASE_MARKED_MISSING_READ_AS_REFERENCE);
            if(raw.ploidy()==1)risks.add(Risk.REFERENCE_VECTOR_END);
        }
        cref+=ref0;calt+=ref1;
        var scaf=new ArrayList<Scaffold>();
        if(scaffold!=null){
            requireStorage(scaffold,"scaffold");
            for(int i=0;i<scaffold.size();i++){
                var raw=scaffold.get(i);int mapped=header.scaffoldToTarget[i],g0=slot(raw,0),g1=slot(raw,1);
                if(raw.missingAlleles()!=0&&(g0==1||g1==1))risks.add(Risk.SCAFFOLD_PHASE_MARKED_MISSING);
                if(raw.ploidy()==1)risks.add(Risk.SCAFFOLD_VECTOR_END);
                ScaffoldReason reason;
                boolean a0=alt(g0),a1=alt(g1);
                if(mapped<0)reason=ScaffoldReason.UNKNOWN_SAMPLE;
                else if(a0==a1)reason=ScaffoldReason.NOT_HETEROZYGOUS;
                else if(g0==0||g1==0)reason=ScaffoldReason.LITERAL_MISSING;
                else if(((g0|g1)&1)==0)reason=ScaffoldReason.UNPHASED;
                else if((packed[mapped]&3)!=2)reason=ScaffoldReason.TARGET_NOT_HETEROZYGOUS;
                else{reason=ScaffoldReason.APPLIED;packed[mapped]=3|(a0?4:0)|(a1?8:0);genotypes[4]++;}
                scaf.add(new Scaffold(raw,mapped,reason));
            }
        }
        var cells=new ArrayList<Target>();for(int i=0;i<target.size();i++)cells.add(new Target(target.get(i),initial.get(i).packed(),packed[i]));
        return new Row(cells,reference,refHaplotypes,scaf,new Counts(genotypes[0],genotypes[1],genotypes[2],genotypes[3],genotypes[4],ref0,ref1,cref,calt,cmis,ignored),risks);
    }
    private static int slot(ShapeitGenotypes.WireCall call,int index){return index<call.values().size()?call.values().get(index):ShapeitGenotypes.VECTOR_END;}
    private static boolean alt(int wire){return (wire>>1)-1==1;}
    private static void requireStorage(List<ShapeitGenotypes.WireCall> calls,String role){if(!calls.isEmpty()&&calls.stream().noneMatch(call->Objects.requireNonNull(call).values().size()==2))throw new IllegalArgumentException("All-"+role+" haploid buffer lacks a verified diploid storage frame; dependent fitting requires a separate haploid contract");}
    private static void checkSize(List<String> names,Limits limits){Objects.requireNonNull(names);if(names.size()>limits.samplesPerPanel())throw new IllegalArgumentException("Complete common-reader sample capacity exceeded; raise the limit without dropping samples");}
    private static void memory(long estimate,Limits limits){if(estimate>limits.retainedBytes()||estimate>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete common-reader row/header exceeds retained capacity or Java heap; raise both without truncating cells");}
}
