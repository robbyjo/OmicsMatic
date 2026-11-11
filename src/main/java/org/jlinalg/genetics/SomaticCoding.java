/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.*;

/** Standard-code coding consequences and complete192-channel mutation opportunities.
 * Sequences are in transcript orientation; supplied genomic flanks need not be adjacent CDS bases.
 * This arithmetic does not fit dN/dS, call variants, build a reference or infer coverage.
 */
public final class SomaticCoding {
    private SomaticCoding() { }
    private static final String DNA="ACGT", IUPAC="ACGTRYSWKMBDHVN";
    // Standard NCBI code1 in lexical A,C,G,T codon order; X is an unresolved IUPAC codon.
    private static final String AMINO="KNKNTTTTRSRSIIMI"+"QHQHPPPPRRRRLLLL"+"EDEDAAAAGGGGVVVV"+"*Y*YSSSS*CWCLFLF";
    /** Consequence labels and matrix columns; stop loss has no selection-count column. */
    public enum Impact {
        SYNONYMOUS(0,"Synonymous"),MISSENSE(1,"Missense"),NONSENSE(2,"Nonsense"),ESSENTIAL_SPLICE(3,"Essential_Splice"),STOP_LOSS(-1,"Stop_loss");
        private final int column;private final String label;
        Impact(int column,String label){this.column=column;this.label=label;}
        public int column(){return column;}public String label(){return label;}
    }
    /** Closed1-based genomic interval. */
    public record Interval(long start,long end){public Interval{require(start>=1&&end>=start,"Require1-based start<=end");}public long length(){return Math.addExact(Math.subtractExact(end,start),1);}}
    /** Literal reference sequences, including per-position flanks and essential-splice positions. */
    public static final class Gene {
        private final String name,chromosome,cds,up,down,splice,spliceUp,spliceDown;
        private final int strand;private final List<Interval> exons;private final Map<Long,Integer> spliceIndex;private final long length;
        public Gene(String name,String chromosome,int strand,List<Interval> exons,String cds,String up,String down,
                    long[] splicePositions,String splice,String spliceUp,String spliceDown){
            this.name=literal(name);this.chromosome=literal(chromosome);require(strand==1||strand==-1,"Require strand+1 or-1");this.strand=strand;this.exons=List.copyOf(exons);require(!exons.isEmpty(),"Require complete CDS intervals");long total=0,previous=0;
            for(Interval exon:exons){require(exon.start()>previous,"CDS intervals must be sorted and disjoint");previous=exon.end();total=Math.addExact(total,exon.length());}
            this.cds=sequence(cds);this.up=sequence(up);this.down=sequence(down);require(total==cds.length()&&cds.length()%3==0&&cds.length()>0,"CDS length must match complete codons and intervals");require(up.length()==cds.length()&&down.length()==cds.length(),"Require every supplied genomic CDS flank");length=total;
            this.splice=sequence(splice);this.spliceUp=sequence(spliceUp);this.spliceDown=sequence(spliceDown);require(splicePositions.length==splice.length()&&splice.length()==spliceUp.length()&&splice.length()==spliceDown.length(),"Require complete splice positions/bases/flanks");var positions=new LinkedHashMap<Long,Integer>();for(int i=0;i<splicePositions.length;i++){require(splicePositions[i]>=1&&positions.put(splicePositions[i],i)==null,"Repeated/invalid essential splice position");}spliceIndex=Collections.unmodifiableMap(positions);
        }
        public String name(){return name;}public String chromosome(){return chromosome;}public int strand(){return strand;}public String cds(){return cds;}public List<Interval> exons(){return exons;}
        /** Zero-based transcript index, or-1 outside CDS. */
        public int codingIndex(long position){long offset=0;for(Interval exon:exons){if(position>=exon.start()&&position<=exon.end()){long index=Math.addExact(offset,position-exon.start());return Math.toIntExact(strand==1?index:length-1-index);}offset=Math.addExact(offset,exon.length());}return -1;}
        /** Complete raw opportunities. Ambiguous contexts are counted explicitly as unassigned. */
        public Opportunities opportunities(){return SomaticCoding.opportunities(cds,up,down,splice,spliceUp,spliceDown);}
        /** Annotate a canonical single-base variant; no identifier/allele/coordinate repair. */
        public Annotation annotate(String chromosome,long position,char reference,char alternate){
            require(this.chromosome.equals(chromosome),"Chromosome differs from the literal reference");require(DNA.indexOf(reference)>=0&&DNA.indexOf(alternate)>=0&&reference!=alternate,"Require distinct canonical reference/alternate bases");char ref=strand==1?reference:complement(reference),alt=strand==1?alternate:complement(alternate);Integer s=spliceIndex.get(position);int coding=-1;String before,after,codon=".",change=".",amino=".";Impact impact;char expected;
            if(s!=null){expected=splice.charAt(s);before=context(spliceUp.charAt(s),expected,spliceDown.charAt(s));after=context(spliceUp.charAt(s),alt,spliceDown.charAt(s));impact=Impact.ESSENTIAL_SPLICE;}
            else {coding=codingIndex(position);require(coding>=0,"Position is outside supplied CDS/essential splice sites");expected=cds.charAt(coding);before=context(up.charAt(coding),expected,down.charAt(coding));after=context(up.charAt(coding),alt,down.charAt(coding));String old=cds.substring(coding/3*3,coding/3*3+3);char[] next=old.toCharArray();next[coding%3]=alt;String modified=new String(next);impact=consequence(old,modified);codon=old+">"+modified;change=""+ref+(coding+1)+alt;amino=""+translate(old)+(coding/3+1)+translate(modified);}
            int channel=canonical(before)&&canonical(after)&&before.charAt(1)!=after.charAt(1)?channel(before,after.charAt(1)):-1;
            return new Annotation(name,position,coding,ref,alt,expected,ref!=expected,before,after,channel,impact,codon,change,amino);
        }
    }
    /** An annotation retains wrong reference and unresolved context without forcing an N-matrix entry. */
    public record Annotation(String gene,long position,int codingIndex,char referenceCoding,char alternateCoding,char expectedReference,
                             boolean wrongReference,String referenceContext,String alternateContext,int channel,Impact impact,
                             String codonChange,String nucleotideChange,String aminoAcidChange){
        public boolean counted(){return !wrongReference&&channel>=0&&impact.column()>=0;}
    }
    /** All192x4 counts plus counts of unassigned contexts and excluded stop-loss hypotheses. */
    public static final class Opportunities {
        private final long[][] counts;private final long unresolved,stopLoss;
        private Opportunities(long[][] counts,long unresolved,long stopLoss){this.counts=counts;this.unresolved=unresolved;this.stopLoss=stopLoss;}
        public long[][] counts(){return Arrays.stream(counts).map(long[]::clone).toArray(long[][]::new);}public long unresolvedContexts(){return unresolved;}public long stopLossChanges(){return stopLoss;}
        public long countedChanges(){long n=0;for(long[] row:counts)for(long value:row)n=Math.addExact(n,value);return n;}
    }
    /** Translate a standard-code codon, retaining unresolved IUPAC sequence as X. */
    public static char translate(String codon){sequence(codon);require(codon.length()==3,"Require a complete codon");return canonical(codon)?AMINO.charAt(index(codon)): 'X';}
    /** Compare complete codons without inventing a codon model or a selection estimate. */
    public static Impact consequence(String before,String after){char a=translate(before),b=translate(after);return a==b?Impact.SYNONYMOUS:b=='*'?Impact.NONSENSE:a=='*'?Impact.STOP_LOSS:Impact.MISSENSE;}
    public static char complement(char base){return switch(base){case 'A'->'T';case 'C'->'G';case 'G'->'C';case 'T'->'A';default->throw new IllegalArgumentException("Canonical complement required");};}
    /** Row0..191: lexical trinucleotides, then lexical non-reference alternate base. */
    public static int channel(String before,char alternate){require(before.length()==3&&canonical(before)&&DNA.indexOf(alternate)>=0&&alternate!=before.charAt(1),"Require canonical context and a distinct central alternate");int b=DNA.indexOf(before.charAt(1)),a=DNA.indexOf(alternate);return index(before)*3+a-(a>b?1:0);}
    public static String channelLabel(int row){require(row>=0&&row<192,"Require row0..191");int tri=row/3,rank=row%3;char a=DNA.charAt(tri/16),b=DNA.charAt(tri/4%4),c=DNA.charAt(tri%4);int old=DNA.indexOf(b),alternative=rank+(rank>=old?1:0);return context(a,b,c)+">"+context(a,DNA.charAt(alternative),c);}
    /** Standard-code unweighted opportunities; caller-supplied coverage adjustments remain separate. */
    public static Opportunities opportunities(String cds,String up,String down,String splice,String spliceUp,String spliceDown){
        sequence(cds);sequence(up);sequence(down);sequence(splice);sequence(spliceUp);sequence(spliceDown);require(canonical(cds)&&canonical(splice),"Opportunity counts require resolved CDS/splice bases; ambiguous flanks remain explicitly unassigned");require(!cds.isEmpty()&&cds.length()%3==0&&up.length()==cds.length()&&down.length()==cds.length(),"Require complete CDS codons/flanks");require(splice.length()==spliceUp.length()&&splice.length()==spliceDown.length(),"Require every splice flank");long[][] counts=new long[192][4];long missing=0,loss=0;
        for(int i=0;i<cds.length();i++){String old=cds.substring(i/3*3,i/3*3+3),before=context(up.charAt(i),cds.charAt(i),down.charAt(i));for(char alt:DNA.toCharArray()){if(alt==cds.charAt(i))continue;char[] modified=old.toCharArray();modified[i%3]=alt;Impact impact=consequence(old,new String(modified));if(impact==Impact.STOP_LOSS){loss++;continue;}if(!canonical(before)){missing++;continue;}counts[channel(before,alt)][impact.column()]++;}}
        for(int i=0;i<splice.length();i++){String before=context(spliceUp.charAt(i),splice.charAt(i),spliceDown.charAt(i));for(char alt:DNA.toCharArray()){if(alt==splice.charAt(i))continue;if(!canonical(before)){missing++;continue;}counts[channel(before,alt)][3]++;}}
        return new Opportunities(counts,missing,loss);
    }
    /** Multiply every supplied opportunity by its explicit per-channel/per-impact weight; no median/coverage is inferred. */
    public static double[][] weighted(long[][] counts,double[][] weights){require(counts.length==192&&weights.length==192,"Require192 channels");double[][] result=new double[192][4];for(int r=0;r<192;r++){require(counts[r].length==4&&weights[r].length==4,"Require four impacts per channel");for(int c=0;c<4;c++){require(counts[r][c]>=0&&Double.isFinite(weights[r][c])&&weights[r][c]>=0,"Require nonnegative counts/finite explicit weights");result[r][c]=counts[r][c]*weights[r][c];require(Double.isFinite(result[r][c]),"Weighted opportunity overflow");}}return result;}
    private static int index(String s){return DNA.indexOf(s.charAt(0))*16+DNA.indexOf(s.charAt(1))*4+DNA.indexOf(s.charAt(2));}
    private static String sequence(String s){Objects.requireNonNull(s,"Sequence required");for(int i=0;i<s.length();i++)require(IUPAC.indexOf(s.charAt(i))>=0,"Literal uppercase IUPAC DNA required");return s;}
    private static boolean canonical(String s){for(int i=0;i<s.length();i++)if(DNA.indexOf(s.charAt(i))<0)return false;return true;}
    private static String context(char a,char b,char c){return new String(new char[]{a,b,c});}
    private static String literal(String s){require(s!=null&&!s.isEmpty()&&s.chars().noneMatch(c->c==0||c=='\t'||c=='\r'||c=='\n'),"Require a literal nonempty identifier");return s;}
    private static void require(boolean test,String detail){if(!test)throw new IllegalArgumentException(detail);}
}
