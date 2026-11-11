/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native independent original RegionStats audit of closed unstranded ranges in an uncompressed FASTA. */
public final class SignacReferenceStats {
    private SignacReferenceStats() { }
    public static final List<String> DINUCLEOTIDES=List.of("AA","AC","AG","AT","CA","CC","CG","CT","GA","GC","GG","GT","TA","TC","TG","TT");
    public record Limits(long bytes,long residues,int sequences,int lineBases,int peakWidth,int peaks,long queriedBases) {
        public Limits {if(bytes<1||residues<1||sequences<1||sequences>2_000_000||lineBases<1||peakWidth<1||peakWidth>10_000_000||peaks<1||peaks>2_000_000||queriedBases<1)throw new IllegalArgumentException("Invalid reference/RegionStats capacities");}
        public static Limits defaults(){return new Limits(20_000_000_000L,20_000_000_000L,200_000,10_000_000,1_000_000,2_000_000,100_000_000L);}
    }
    public record Inspection(Map<String,Long> lengths,long residues,long ambiguousResidues,long bytes) {
        public Inspection {lengths=Collections.unmodifiableMap(new LinkedHashMap<>(lengths));}
    }
    public record Row(String peak,boolean chromosomePresent,int sequenceLength,double gcPercent,long[] dinucleotides) {
        public Row {dinucleotides=dinucleotides.clone();if(dinucleotides.length!=16)throw new IllegalArgumentException("Require all sixteen original dinucleotide counts");}
        @Override public long[] dinucleotides(){return dinucleotides.clone();}
    }
    public record Result(Inspection reference,List<Row> rows,long queriedBases,List<String> warnings) {
        public Result {rows=List.copyOf(rows);warnings=List.copyOf(warnings);}
    }
    /** Validate every FASTA residue/header before native indexing; no aliases or base repair are applied. */
    public static Inspection inspect(Path path,Limits limits,BooleanSupplier cancelled)throws IOException{
        Objects.requireNonNull(limits);Objects.requireNonNull(cancelled);if(!Files.isRegularFile(path)||Files.size(path)>limits.bytes)throw new IOException("Reference FASTA unavailable or exceeds its byte capacity");
        LinkedHashMap<String,Long> lengths=new LinkedHashMap<>();long bytes=0,residues=0,ambiguous=0,currentLength=0,lineBases=0;String current=null;boolean lineStart=true,header=false,cr=false;ByteArrayOutputStream text=new ByteArrayOutputStream();
        try(var in=new BufferedInputStream(Files.newInputStream(path))){int c;
            while((c=in.read())>=0){if((++bytes&65535)==0)SignacFragments.check(cancelled);if(bytes==1&&c==31)throw new IOException("Compressed reference requires a separate gzi-aware workflow; provide a preserved uncompressed FASTA copy");if(bytes>limits.bytes)throw new IOException("Reference grew beyond its byte capacity");
                if(cr){if(c!='\n')throw new IOException("Embedded CR in FASTA; require LF or CRLF lines");cr=false;lineStart=true;lineBases=0;continue;}
                if(c=='\r'||c=='\n'){
                    if(header){current=header(text.toByteArray());text.reset();if(lengths.containsKey(current))throw new IOException("Duplicate FASTA chromosome ID: "+current);lengths.put(current,0L);if(lengths.size()>limits.sequences)throw new IOException("FASTA sequence count exceeds capacity");currentLength=0;header=false;}
                    cr=c=='\r';lineStart=true;lineBases=0;continue;
                }
                if(lineStart&&c=='>'){
                    if(current!=null){if(currentLength==0)throw new IOException("Empty FASTA sequence: "+current);lengths.put(current,currentLength);}header=true;lineStart=false;continue;
                }
                if(header){if(text.size()>=8192)throw new IOException("FASTA header exceeds8192bytes");text.write(c);continue;}
                if(current==null)throw new IOException("FASTA residues before the first header");lineStart=false;
                int upper=c>='a'&&c<='z'?c-32:c;if("ACGTRYSWKMBDHVN-+".indexOf(upper)<0)throw new IOException("Unsupported FASTA residue byte "+c+"; no conversion to N was performed");
                if(++lineBases>limits.lineBases||++residues>limits.residues)throw new IOException("Reference line/residue capacity exceeded");currentLength++;if(base(upper)<0)ambiguous++;
            }
        }
        if(cr)throw new IOException("FASTA ends with a bare CR; require LF or CRLF lines");if(header){current=header(text.toByteArray());if(lengths.containsKey(current))throw new IOException("Duplicate FASTA chromosome ID: "+current);throw new IOException("Empty FASTA sequence: "+current);}
        if(current==null||currentLength==0||residues==0)throw new IOException("Reference has no nonempty FASTA sequences");lengths.put(current,currentLength);SignacFragments.check(cancelled);return new Inspection(lengths,residues,ambiguous,bytes);
    }
    /**
     * Compute original literal CG/width and adjacent canonical dinucleotide counts.
     * Missing chromosomes retain NA-equivalent metadata and the declared width; out-of-range queries fail.
     * @param fasta captured uncompressed reference; original source/index need not be modified
     * @param suppliedIndex optional captured R-generated .fai to validate against actual bytes
     * @param peaks ordered one-based closed ranges
     * @param limits reference and query capacities
     * @param cancelled cancellation/deadline callback
     * @return all ordered metadata rows, including explicit missing chromosomes and warnings
     */
    public static Result calculate(Path fasta,Path suppliedIndex,List<SignacFragments.Region> peaks,Limits limits,BooleanSupplier cancelled)throws IOException{
        if(peaks==null||peaks.size()>limits.peaks)throw new IllegalArgumentException("Require an ordered bounded peak population");peaks=List.copyOf(peaks);Inspection reference=inspect(fasta,limits,cancelled);FastaSequenceIndex index=FastaIndexes.build(fasta);if(index.size()!=reference.lengths.size())throw new IOException("Native FASTA index sequence population differs");
        for(var entry:index)if(!Objects.equals(reference.lengths.get(entry.getContig()),entry.getSize()))throw new IOException("Native FASTA index chromosome length differs");
        if(suppliedIndex!=null&&!index.equals(new FastaSequenceIndex(suppliedIndex)))throw new IOException("Captured .fai does not match actual reference offsets, lengths or LF/CRLF widths");
        List<Row> rows=new ArrayList<>();long queried=0;int missing=0;
        try(var reader=new IndexedFastaSequenceFile(fasta,index)){
            for(var peak:peaks){SignacFragments.check(cancelled);int width=peak.end()-peak.start()+1;if(width>limits.peakWidth)throw new IOException("Peak width exceeds explicit sequence-query capacity");Long length=reference.lengths.get(peak.chromosome());
                if(length==null){missing++;rows.add(new Row(peak.name(),false,width,Double.NaN,new long[16]));continue;}
                if(peak.end()>length)throw new IOException("Peak exceeds captured reference chromosome: "+peak.name());if(queried>limits.queriedBases-width)throw new IOException("Combined queried bases exceed explicit capacity");queried+=width;
                byte[] sequence=reader.getSubsequenceAt(peak.chromosome(),peak.start(),peak.end()).getBases();if(sequence.length!=width)throw new IOException("Reference subsequence width differs");long[] dinuc=new long[16];long gc=0;int previous=-1;
                for(int i=0;i<sequence.length;i++){if((i&65535)==0)SignacFragments.check(cancelled);int c=sequence[i]&255;if(c>='a'&&c<='z')c-=32;int b=base(c);if(b==1||b==2)gc++;if(previous>=0&&b>=0)dinuc[previous*4+b]++;previous=b;}
                rows.add(new Row(peak.name(),true,width,(double)gc/width*100,dinuc));
            }
        }catch(IllegalArgumentException error){throw new IOException("Reference range/index extraction failed",error);}
        List<String> warnings=new ArrayList<>();if(missing>0)warnings.add("MISSING_REFERENCE_CHROMOSOMES: "+missing+" peak rows retain source NA sequence statistics and their declared lengths. Review reference assembly/contig names and any source scanner placeholder behavior; no missing chromosome was silently filled with sequence.");
        warnings.add("SOURCE_REGION_STATS: GC is literal C/G count divided by the full declared peak width, including ambiguity positions; dinucleotides count adjacent canonical bases only. No Tn5 shift, trimming or ambiguity expansion was applied.");return new Result(reference,rows,queried,warnings);
    }
    private static int base(int c){return switch(c){case 'A'->0;case 'C'->1;case 'G'->2;case 'T'->3;default->-1;};}
    private static String header(byte[] bytes)throws IOException{
        String value;try{value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException error){throw new IOException("Malformed UTF-8 FASTA header",error);}
        if(value.isBlank()||Character.isWhitespace(value.charAt(0))||value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\t'))throw new IOException("Invalid FASTA header");String id=value.split("[ \t]",2)[0];SignacFragments.identity(id);if(id.chars().anyMatch(c->c>127||Character.isWhitespace(c)))throw new IOException("This reference workflow requires ASCII chromosome IDs; no aliasing was performed");return id;
    }
}
