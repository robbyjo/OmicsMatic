/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Native chromVAR addGCBias sequence formula: (C+G)/(A+C+G+T).
 * Ambiguous bases and gaps are excluded from both numerator and denominator.
 * All-ambiguous and empty sequences retain source NaN; no imputation occurs.
 * Reference ranges are one-based, closed and unstranded. This class performs
 * neither background sampling nor a whole-package addGCBias workflow.
 */
public final class ChromVarGcBias {
    private ChromVarGcBias() { }

    /** Immutable counts and the original fraction for one sequence. */
    public record Bias(long canonicalBases,long gcBases,long ambiguousBases,double fraction) { }
    /** Original range identity and its sequence bias. */
    public record Row(String peak,Bias bias) { }
    /** Reference inspection and rows in the supplied order, without filtering. */
    public record Result(SignacReferenceStats.Inspection reference,List<Row> rows,long queriedBases,List<String> warnings) {
        public Result {rows=List.copyOf(rows);warnings=List.copyOf(warnings);}
    }

    /** Count a DNA IUPAC sequence, accepting case without changing base identity. */
    public static Bias count(byte[] sequence,BooleanSupplier cancelled) {
        Objects.requireNonNull(sequence);Objects.requireNonNull(cancelled);
        SignacFragments.check(cancelled);long canonical=0,gc=0,ambiguous=0;
        for(int i=0;i<sequence.length;i++){
            if((i&65535)==0)SignacFragments.check(cancelled);
            int base=sequence[i]&255;if(base>='a'&&base<='z')base-=32;
            switch(base){
                case 'C','G' -> {canonical++;gc++;}
                case 'A','T' -> canonical++;
                case 'R','Y','S','W','K','M','B','D','H','V','N','-','+' -> ambiguous++;
                default -> throw new IllegalArgumentException("Unsupported DNA residue byte "+(sequence[i]&255)+"; no repair was performed");
            }
        }
        SignacFragments.check(cancelled);
        return new Bias(canonical,gc,ambiguous,(double)gc/canonical);
    }

    /**
     * Inspect and index an uncompressed FASTA in memory, then compute exact bias.
     * The optional supplied .fai is checked against actual byte offsets. Neither
     * the source reference nor its index is written. Missing/out-of-bounds ranges
     * fail instead of silently changing the peak population.
     */
    public static Result calculate(Path fasta,Path suppliedIndex,List<SignacFragments.Region> peaks,
                                   SignacReferenceStats.Limits limits,BooleanSupplier cancelled)throws IOException {
        Objects.requireNonNull(limits);Objects.requireNonNull(cancelled);Objects.requireNonNull(peaks);
        SignacFragments.check(cancelled);
        if(peaks.isEmpty()||peaks.size()>limits.peaks())throw new IllegalArgumentException("Require an ordered, nonempty bounded peak population");
        peaks=List.copyOf(peaks);Set<String> identities=new HashSet<>();
        for(var peak:peaks)if(!identities.add(peak.name()))throw new IllegalArgumentException("Duplicate peak identity: "+peak.name());
        var reference=SignacReferenceStats.inspect(fasta,limits,cancelled);
        FastaSequenceIndex index=FastaIndexes.build(fasta);
        if(index.size()!=reference.lengths().size())throw new IOException("FASTA index sequence population differs");
        for(var entry:index)if(!Objects.equals(reference.lengths().get(entry.getContig()),entry.getSize()))throw new IOException("FASTA index chromosome length differs");
        if(suppliedIndex!=null&&!index.equals(new FastaSequenceIndex(suppliedIndex)))throw new IOException("Supplied .fai differs from actual reference byte offsets/lengths");
        List<Row> rows=new ArrayList<>();long queried=0;boolean undefined=false;
        try(var reader=new IndexedFastaSequenceFile(fasta,index)){
            for(var peak:peaks){
                SignacFragments.check(cancelled);long width=(long)peak.end()-peak.start()+1;
                if(width>limits.peakWidth()||width>limits.queriedBases()-queried)throw new IOException("GC-bias reference query exceeds its declared width/base capacity");
                Long length=reference.lengths().get(peak.chromosome());
                if(length==null)throw new IOException("Missing reference chromosome: "+peak.chromosome()+"; no aliases or row removal were applied");
                if(peak.end()>length)throw new IOException("Peak extends beyond reference: "+peak.name());
                byte[] sequence=reader.getSubsequenceAt(peak.chromosome(),peak.start(),peak.end()).getBases();
                if(sequence.length!=width)throw new IOException("Reference subsequence width differs");
                Bias bias=count(sequence,cancelled);rows.add(new Row(peak.name(),bias));queried+=width;
                undefined|=Double.isNaN(bias.fraction());
            }
        }catch(IllegalArgumentException error){throw new IOException("Reference extraction/sequence validation failed",error);}
        List<String> warnings=new ArrayList<>();
        warnings.add("CHROMVAR_CANONICAL_GC: bias=(C+G)/(A+C+G+T); ambiguous bases and gaps are excluded. Signac RegionStats GC percent divided by 100 is not equivalent when ambiguity is present.");
        if(undefined)warnings.add("UNDEFINED_GC_BIAS: a peak contains no canonical A/C/G/T bases. Source NaN was retained; downstream sampling may fail. Review the reference/ranges and record any explicit population changes before retrying.");
        return new Result(reference,rows,queried,warnings);
    }
}
