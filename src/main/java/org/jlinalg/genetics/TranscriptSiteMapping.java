/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact-coordinate variant mapping to explicitly selected transcript TSS/TES sites.
 * Supplied transcript rows define the reference population. Gene interiors are
 * not implicitly included, and no annotation service, gene-model selection,
 * chromosome alias, assembly conversion or historical software default is inferred.
 */
public final class TranscriptSiteMapping {
    private TranscriptSiteMapping() { }

    /** Strand-oriented transcription start or end site. */
    public enum Site { TSS, TES }
    /** Whether exact equality at the declared radius is included. */
    public enum Boundary { INCLUSIVE, STRICT }
    /** Missing P values either reject the population or remain explicitly excluded. */
    public enum Missing { ERROR, EXCLUDE }
    /** One disposition for every original variant/context row. */
    public enum Status { MISSING_P_EXCLUDED, ABOVE_P_CUTOFF, MAPPED, UNMAPPED_IN_SUPPLIED_REFERENCE }

    /**
     * A reported variant association. Decimal P values are compared exactly with
     * the decimal cutoff, including positive values below binary64 range.
     * Null represents a missing P value. IDs are unique within a context; reuse
     * across contexts requires identical literal chromosome and position.
     */
    public record Variant(String context, String id, String chromosome, long position, BigDecimal p) {
        public Variant {
            text(context);text(id);coordinate(chromosome,position);
            if(p!=null&&(p.signum()<0||p.compareTo(BigDecimal.ONE)>0))throw new IllegalArgumentException("Require decimal P in [0,1] or explicit null");
        }
    }

    /**
     * One selected-resource transcript, with one-based closed coordinates and
     * explicit strand. Biotype is the declared GENE biotype, not transcript type.
     * All rows for one gene must agree on chromosome, strand and gene biotype.
     * Every distinct transcript ID is retained, including coincident sites.
     */
    public record Transcript(String gene, String transcript, String chromosome, long start, long end,
            String strand, String geneBiotype) {
        public Transcript {
            text(gene);text(transcript);coordinate(chromosome,start);text(geneBiotype);
            if(end<start||!("+".equals(strand)||"-".equals(strand)))throw new IllegalArgumentException("Require 1<=start<=end and explicit + or - transcript strand");
        }
        /** Strand-oriented transcription start, in supplied one-based coordinates. */
        public long tss(){return strand.equals("+")?start:end;}
        /** Strand-oriented transcription end, in supplied one-based coordinates. */
        public long tes(){return strand.equals("+")?end:start;}
    }

    /** Identified reference snapshot with exact assembly and ID namespaces. */
    public record Reference(String identity, String build, String geneNamespace, String transcriptNamespace,
            List<Transcript> transcripts) {
        public Reference {
            text(identity);text(build);text(geneNamespace);text(transcriptNamespace);
            transcripts=List.copyOf(transcripts);
        }
    }

    /** Complete input, emitted-hit and measured index/query/sort work bounds. */
    public record Limits(int maximumInputRows, int maximumHits, long maximumOperations) {
        public Limits {
            if(maximumInputRows<1||maximumHits<0||maximumOperations<1)throw new IllegalArgumentException("Require positive input/work and nonnegative hit budgets");
        }
        /** Conservative defaults; no result is truncated to fit a capacity. */
        public static Limits defaults(){return new Limits(1_000_000,2_000_000,100_000_000L);}
    }

    /** All mapping choices are explicit; P selection always uses P&lt;=cutoff. */
    public record Options(String build, BigDecimal pCutoff, long radiusBp, Boundary boundary,
            Set<Site> sites, Set<String> geneBiotypes, Missing missing, Limits limits) {
        public Options {
            text(build);
            if(pCutoff==null||pCutoff.signum()<0||pCutoff.compareTo(BigDecimal.ONE)>0||radiusBp<0
                    ||boundary==null||missing==null||limits==null)throw new IllegalArgumentException("Declare cutoff in [0,1], nonnegative radius and every policy/limit");
            sites=Set.copyOf(sites);geneBiotypes=Set.copyOf(geneBiotypes);
            if(sites.isEmpty()||geneBiotypes.isEmpty())throw new IllegalArgumentException("Declare nonempty site and gene-biotype selections");
            for(String value:geneBiotypes)text(value);
        }
    }

    /**
     * One original site hit; row numbers are one-based input positions.
     * Signed distance is positive toward the transcript's 3-prime direction.
     * At a one-base transcript TSS and TES remain two different site hits.
     */
    public record Hit(int variantRow, int transcriptRow, Variant variant, Transcript transcript, Site site,
            long sitePosition, long signedDistanceBp, long distanceBp) { }

    /** Every original variant row, including exclusions and eligible zero-hit rows. */
    public record VariantResult(int row, Variant variant, Status status, int siteHits, int transcripts,
            int genes, Long nearestHitDistanceBp) { }

    /**
     * Every original transcript row, including unselected biotypes and zero-hit
     * rows. Counts include every matching variant/context association separately.
     */
    public record TranscriptResult(int row, Transcript transcript, boolean selectedBiotype,
            long tssHits, long tesHits) { }

    /** Unique variant-row/gene mapping, retaining multiplicity in the separate hit ledger. */
    public record GeneResult(int variantRow, Variant variant, String gene, int transcripts, int siteHits,
            long nearestHitDistanceBp) { }

    /**
     * Complete immutable ledgers. Variants/transcripts retain input order; hits
     * use variant row, transcript row, then TSS before TES. Genes use variant
     * order then the first matching transcript row for that gene.
     */
    public record Result(List<VariantResult> variants, List<TranscriptResult> transcripts, List<Hit> hits,
            List<GeneResult> genes, List<String> warnings, long operations) {
        public Result {
            variants=List.copyOf(variants);transcripts=List.copyOf(transcripts);hits=List.copyOf(hits);
            genes=List.copyOf(genes);warnings=List.copyOf(warnings);
        }
    }

    private record Key(String context,String id) { }
    private record Location(String chromosome,long position) { }
    private record GeneDefinition(String chromosome,String strand,String biotype) { }
    private record Anchor(int row,Transcript transcript,Site site,long position) { }
    private static final class GeneCount {
        private final Set<Integer> rows=new HashSet<>();
        private int hits;
        private long nearest=Long.MAX_VALUE;
        void add(Hit hit){rows.add(hit.transcriptRow());hits++;nearest=Math.min(nearest,hit.distanceBp());}
    }
    private static final class Work {
        private final long maximum;
        private final Runnable check;
        private long count;
        Work(long maximum,Runnable check){this.maximum=maximum;this.check=check;}
        void tick(){check.run();if(count>=maximum)throw new IllegalArgumentException("Transcript-site operation budget exceeded; no output was truncated");count++;}
    }

    /**
     * Map an entire declared association population to the selected reference
     * sites. The index includes only selected gene biotypes and requested sites.
     * Every transcript and variant receives a disposition. Missing P handling
     * and resource coverage never imply biological absence or gene causality.
     *
     * @param variants nonempty complete original association population
     * @param reference nonempty identified transcript snapshot, never altered
     * @param options exact assembly, decimal cutoff, radius, site, biotype and capacity choices
     * @param cancellation cooperative deadline/cancellation check during all measured work
     * @return complete ledgers; invalid inputs or exceeded limits return no partial result
     */
    public static Result map(List<Variant> variants,Reference reference,Options options,Runnable cancellation) {
        if(variants==null||reference==null||options==null||cancellation==null||variants.isEmpty()||reference.transcripts().isEmpty())throw new IllegalArgumentException("Require nonempty variants/reference and explicit options/cancellation");
        if(!options.build().equals(reference.build()))throw new IllegalArgumentException("Variant and transcript-reference builds differ; harmonize coordinates explicitly");
        if((long)variants.size()+reference.transcripts().size()>options.limits().maximumInputRows())throw new IllegalArgumentException("Combined transcript-site input row budget exceeded");
        Work work=new Work(options.limits().maximumOperations(),cancellation);
        var contexts=new HashSet<Key>();var locations=new HashMap<String,Location>();
        int missing=0,zeros=0,eligible=0;
        for(Variant variant:variants) {
            work.tick();if(variant==null)throw new IllegalArgumentException("Null variant row");
            if(!contexts.add(new Key(variant.context(),variant.id())))throw new IllegalArgumentException("Duplicate variant/context: "+variant.id()+" / "+variant.context());
            var location=new Location(variant.chromosome(),variant.position());var old=locations.putIfAbsent(variant.id(),location);
            if(old!=null&&!old.equals(location))throw new IllegalArgumentException("Variant ID has conflicting coordinates across contexts: "+variant.id());
            if(variant.p()==null){missing++;if(options.missing()==Missing.ERROR)throw new IllegalArgumentException("Missing variant P requires explicit EXCLUDE: "+variant.id());}
            else {if(variant.p().signum()==0)zeros++;if(variant.p().compareTo(options.pCutoff())<=0)eligible++;}
        }
        var identifiers=new HashSet<String>();var definitions=new HashMap<String,GeneDefinition>();
        var byChromosome=new HashMap<String,List<Anchor>>();
        boolean[] selected=new boolean[reference.transcripts().size()];long[] tssHits=new long[selected.length],tesHits=new long[selected.length];
        int selectedCount=0;
        for(int row=0;row<reference.transcripts().size();row++) {
            work.tick();Transcript transcript=reference.transcripts().get(row);
            if(!identifiers.add(transcript.transcript()))throw new IllegalArgumentException("Duplicate transcript ID: "+transcript.transcript());
            var definition=new GeneDefinition(transcript.chromosome(),transcript.strand(),transcript.geneBiotype());var old=definitions.putIfAbsent(transcript.gene(),definition);
            if(old!=null&&!old.equals(definition))throw new IllegalArgumentException("Gene has conflicting chromosome, strand or gene biotype: "+transcript.gene());
            selected[row]=options.geneBiotypes().contains(transcript.geneBiotype());
            if(!selected[row])continue;selectedCount++;
            List<Anchor> anchors=byChromosome.computeIfAbsent(transcript.chromosome(),ignored->new ArrayList<>());
            for(Site site:Site.values())if(options.sites().contains(site)){work.tick();anchors.add(new Anchor(row+1,transcript,site,site==Site.TSS?transcript.tss():transcript.tes()));}
        }
        Comparator<Anchor> positionOrder=(a,b)->{work.tick();int c=Long.compare(a.position(),b.position());if(c==0)c=Integer.compare(a.row(),b.row());return c==0?a.site().compareTo(b.site()):c;};
        for(List<Anchor> anchors:byChromosome.values())anchors.sort(positionOrder);
        Comparator<Anchor> sourceOrder=(a,b)->{work.tick();int c=Integer.compare(a.row(),b.row());return c==0?a.site().compareTo(b.site()):c;};
        var hits=new ArrayList<Hit>();var geneResults=new ArrayList<GeneResult>();var variantResults=new ArrayList<VariantResult>();
        for(int row=0;row<variants.size();row++) {
            work.tick();Variant variant=variants.get(row);Status status;
            if(variant.p()==null)status=Status.MISSING_P_EXCLUDED;
            else if(variant.p().compareTo(options.pCutoff())>0)status=Status.ABOVE_P_CUTOFF;
            else status=Status.UNMAPPED_IN_SUPPLIED_REFERENCE;
            int before=hits.size();var geneCounts=new LinkedHashMap<String,GeneCount>();var transcriptRows=new HashSet<Integer>();Long nearest=null;
            if(status==Status.UNMAPPED_IN_SUPPLIED_REFERENCE&&!(options.boundary()==Boundary.STRICT&&options.radiusBp()==0)) {
                long radius=options.radiusBp()-(options.boundary()==Boundary.STRICT?1:0);
                long low=variant.position()>radius?variant.position()-radius:1;
                long high=variant.position()>Long.MAX_VALUE-radius?Long.MAX_VALUE:variant.position()+radius;
                List<Anchor> anchors=byChromosome.getOrDefault(variant.chromosome(),List.of());
                int left=lowerBound(anchors,low,work),right=upperBound(anchors,high,work);
                // Bound the COMPLETE hit set before allocating its source-order copy.
                if((long)hits.size()+right-left>options.limits().maximumHits())throw new IllegalArgumentException("Complete transcript-site hit budget exceeded; no output was truncated");
                var matching=new ArrayList<Anchor>(right-left);
                for(int index=left;index<right;index++){work.tick();matching.add(anchors.get(index));}
                matching.sort(sourceOrder);
                for(Anchor anchor:matching) {
                    work.tick();long delta=variant.position()-anchor.position();long distance=Math.abs(delta);
                    var hit=new Hit(row+1,anchor.row(),variant,anchor.transcript(),anchor.site(),anchor.position(),anchor.transcript().strand().equals("+")?delta:-delta,distance);
                    hits.add(hit);transcriptRows.add(anchor.row());geneCounts.computeIfAbsent(anchor.transcript().gene(),ignored->new GeneCount()).add(hit);
                    if(anchor.site()==Site.TSS)tssHits[anchor.row()-1]++;else tesHits[anchor.row()-1]++;
                    nearest=nearest==null?distance:Math.min(nearest,distance);
                }
                if(hits.size()>before)status=Status.MAPPED;
            }
            for(Map.Entry<String,GeneCount> entry:geneCounts.entrySet()) {
                work.tick();GeneCount count=entry.getValue();geneResults.add(new GeneResult(row+1,variant,entry.getKey(),count.rows.size(),count.hits,count.nearest));
            }
            variantResults.add(new VariantResult(row+1,variant,status,hits.size()-before,transcriptRows.size(),geneCounts.size(),nearest));
        }
        var transcriptResults=new ArrayList<TranscriptResult>();
        for(int row=0;row<selected.length;row++){work.tick();transcriptResults.add(new TranscriptResult(row+1,reference.transcripts().get(row),selected[row],tssHits[row],tesHits[row]));}
        var warnings=new ArrayList<String>();
        warnings.add("SUPPLIED TRANSCRIPT SITES: mapping is relative to the exact supplied assembly, transcript population and gene biotypes. Required action: verify the reference identity, namespaces, one-based coordinates, strand, site/radius/boundary choices and every exclusion/hit ledger before interpretation; no gene causality or complete FAVOR/FUMA reproduction is certified.");
        if(missing>0)warnings.add("MISSING P EXCLUSIONS: "+missing+" rows retained without mapping. Required action: inspect missing-P dispositions; exclusions are not evidence of absent association.");
        if(zeros>0)warnings.add("REPORTED ZERO P: "+zeros+" rows use the supplied exact zero; underflow cannot be resolved from zero alone. Required action: retain original statistics and use original positive decimal P values when available.");
        if(selectedCount==0)warnings.add("NO SELECTED TRANSCRIPTS: all reference rows are excluded by the declared gene-biotype selection. Required action: verify the resource and selected biotypes; empty mappings do not prove biological absence.");
        if(eligible==0)warnings.add("NO ELIGIBLE ASSOCIATIONS: no available P passes the declared inclusive cutoff. Required action: review every P/missingness disposition and the declared analysis population.");
        return new Result(variantResults,transcriptResults,hits,geneResults,warnings,work.count);
    }

    private static int lowerBound(List<Anchor> values,long position,Work work) {
        int left=0,right=values.size();while(left<right){work.tick();int middle=(left+right)>>>1;if(values.get(middle).position()<position)left=middle+1;else right=middle;}return left;
    }
    private static int upperBound(List<Anchor> values,long position,Work work) {
        int left=0,right=values.size();while(left<right){work.tick();int middle=(left+right)>>>1;if(values.get(middle).position()<=position)left=middle+1;else right=middle;}return left;
    }
    private static void coordinate(String chromosome,long position){text(chromosome);if(position<1)throw new IllegalArgumentException("Require a positive one-based coordinate");}
    private static void text(String value){if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException("Require a bounded nonempty identity without whitespace/control characters");}
}
