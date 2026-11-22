/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

/**
 * FUMA-style two-stage LD selection and consistent genomic-risk-locus construction.
 * Reference MAF/LD is supplied explicitly; this class does not estimate LD, infer
 * ancestry/build, annotate variants, run MAGMA or reproduce the hosted service.
 * Candidate selection follows the inspected getLD backend. Risk loci use complete
 * interval components, correcting its observed backward-merge membership defects.
 */
public final class FumaLocusDefinition {
    private FumaLocusDefinition() { }
    /** Equal-P choices are either refused or resolved by ascending coordinate. */
    public enum Ties { ERROR, POSITION }
    /** Source code uses strict distance; parts of the tutorial label it inclusive. */
    public enum MergeBoundary { SOURCE_STRICT, INCLUSIVE }
    /** Inclusive, one-based genomic interval, used for GWAS eligibility or MHC exclusion. */
    public record Interval(int chromosome, int start, int end) {
        public Interval { coordinate(chromosome,start);if (end<start) throw new IllegalArgumentException("Reversed genomic interval"); }
        /** True when the supplied coordinate belongs to this interval. */
        public boolean contains(int chr,int position) {return chromosome==chr && position>=start && position<=end;}
    }
    /** One standardized GWAS row, with its supplied effect-allele orientation. */
    public record Gwas(int chromosome, int position, String nonEffectAllele,
            String effectAllele, String rsId, double pValue) {
        public Gwas {coordinate(chromosome,position);alleles(nonEffectAllele,effectAllele);token(rsId);probability(pValue);if (pValue==0) pValue=0;}
        /** Canonical chromosome:position:alphabetically-sorted-alleles identity. */
        public String uid() {return key(chromosome,position,nonEffectAllele,effectAllele);}
    }
    /** Reference A1/A2 orientation; non-GWAS candidates are displayed with A1 as effect. */
    public record ReferenceVariant(int chromosome, int position, String a1, String a2,
            String rsId, double maf) {
        public ReferenceVariant {coordinate(chromosome,position);alleles(a1,a2);token(rsId);if (!Double.isFinite(maf)||maf<0||maf>.5) throw new IllegalArgumentException("Reference MAF must be in [0,.5]");}
        /** Canonical chromosome/position/allele identity. */
        public String uid() {return key(chromosome,position,a1,a2);}
    }
    /** One undirected within-chromosome LD pair; self LD is implicitly one. */
    public record LdPair(int chromosome, int first, int second, double rSquared) {
        public LdPair {coordinate(chromosome,first);coordinate(chromosome,second);probability(rSquared);if (first==second) throw new IllegalArgumentException("Omit self LD pairs; self LD is implicit");}
    }
    private record Position(int chromosome,int position) implements Comparable<Position> {
        @Override public int compareTo(Position other) {int c=Integer.compare(chromosome,other.chromosome);return c!=0?c:Integer.compare(position,other.position);}
    }
    private record PairKey(int chromosome,int first,int second) { }
    /**
     * Explicit reference identity and sparse-LD contract. Unlisted pairs must have
     * r-squared strictly below storedMinimumR2. A zero floor requires every pair.
     * One reference variant per coordinate is required because the original backend
     * addresses LD by position; ambiguous multiallelic positions must be resolved
     * explicitly rather than silently assigned the same LD value.
     */
    public static final class Reference {
        private final Map<Position,ReferenceVariant> byPosition;
        private final Map<String,ReferenceVariant> byUid;
        private final Map<Position,Map<Integer,Double>> neighbors;
        private final double storedMinimumR2;
        private final String identity;
        /** Build a reference without changing or guessing its LD/MAF definition. */
        public Reference(String identity,List<ReferenceVariant> variants,List<LdPair> pairs,double storedMinimumR2) {
            if (identity==null||identity.isBlank()||variants==null||variants.isEmpty()||pairs==null) throw new IllegalArgumentException("Reference identity and variants are required");
            probability(storedMinimumR2);this.identity=identity;this.storedMinimumR2=storedMinimumR2;byPosition=new TreeMap<>();byUid=new HashMap<>();neighbors=new HashMap<>();Map<Integer,Long> counts=new HashMap<>();
            for (ReferenceVariant v:variants) {
                Position position=new Position(v.chromosome(),v.position());if (byPosition.putIfAbsent(position,v)!=null) throw new IllegalArgumentException("Ambiguous reference coordinate: "+v.chromosome()+":"+v.position()+"; original LD lookup is positional, so resolve multiallelic records explicitly");
                byUid.put(v.uid(),v);counts.merge(v.chromosome(),1L,Long::sum);neighbors.put(position,new TreeMap<>());
            }
            Map<PairKey,Double> unique=new HashMap<>();
            for (LdPair p:pairs) {
                Position first=new Position(p.chromosome(),p.first()),second=new Position(p.chromosome(),p.second());
                if (!byPosition.containsKey(first)||!byPosition.containsKey(second)) throw new IllegalArgumentException("LD pair coordinate is absent from the supplied reference");
                PairKey key=new PairKey(p.chromosome(),Math.min(p.first(),p.second()),Math.max(p.first(),p.second()));Double prior=unique.putIfAbsent(key,p.rSquared());
                if (prior!=null && Double.compare(prior,p.rSquared())!=0) throw new IllegalArgumentException("Conflicting LD pair values");
                neighbors.get(first).put(p.second(),p.rSquared());neighbors.get(second).put(p.first(),p.rSquared());
            }
            if (storedMinimumR2==0) {long expected=0;for (long n:counts.values()) expected=Math.addExact(expected,n*(n-1)/2);if (unique.size()!=expected) throw new IllegalArgumentException("Zero LD floor requires complete within-chromosome pair coverage");}
        }
        /** Declared reference identity, including release, population and assembly. */
        public String identity() {return identity;}
        /** All omitted pairs are declared strictly below this threshold. */
        public double storedMinimumR2() {return storedMinimumR2;}
        /** Number of supplied reference variants. */
        public int variantCount() {return byUid.size();}
        /** Stored pair value, implicit self value, or empty for an unrecorded pair. */
        public OptionalDouble storedRSquared(String firstUid,String secondUid) {
            ReferenceVariant first=byUid.get(firstUid),second=byUid.get(secondUid);
            if (first==null||second==null) throw new IllegalArgumentException("Unknown reference UID");
            if (first.chromosome()!=second.chromosome()) return OptionalDouble.empty();
            if (firstUid.equals(secondUid)) return OptionalDouble.of(1);
            Double value=neighbors.get(new Position(first.chromosome(),first.position())).get(second.position());return value==null?OptionalDouble.empty():OptionalDouble.of(value);
        }
        private List<ReferenceVariant> linked(Gwas seed,double threshold,Work work) {
            Position position=new Position(seed.chromosome(),seed.position());Map<Integer,Double> links=new TreeMap<>(neighbors.get(position));links.put(seed.position(),1.0);List<ReferenceVariant> result=new ArrayList<>();
            for (var e:links.entrySet()) {work.add();if (e.getValue()>=threshold) result.add(byPosition.get(new Position(seed.chromosome(),e.getKey())));}
            return result;
        }
        private double r2(int chromosome,int first,int second) {return first==second?1:neighbors.get(new Position(chromosome,first)).getOrDefault(second,0.0);}
    }
    /** Explicit protocol; no historical defaults, resource selection or implicit liftover. */
    public record Options(double leadP,double candidateP,double minimumMaf,double independentR2,
            double leadR2,long mergeDistanceBp,boolean includeReferenceVariants,
            Interval excludedMhc,List<Interval> regions,List<String> predefinedUids,
            boolean addIndependentVariants,Ties ties,MergeBoundary mergeBoundary,long maximumWork) {
        public Options {
            probability(leadP);probability(candidateP);probability(independentR2);probability(leadR2);
            if (leadP<=0||candidateP<leadP||!Double.isFinite(minimumMaf)||minimumMaf<0||minimumMaf>.5||mergeDistanceBp<0||ties==null||mergeBoundary==null||maximumWork<1) throw new IllegalArgumentException("Invalid locus protocol; candidate P must be at least lead P");
            regions=List.copyOf(regions);predefinedUids=List.copyOf(predefinedUids);if (new HashSet<>(predefinedUids).size()!=predefinedUids.size()) throw new IllegalArgumentException("Duplicate predefined variant");
        }
    }
    /** One candidate, preserving whether it belongs to the filtered GWAS. */
    public record Candidate(ReferenceVariant reference,Gwas gwas,Gwas originalInput,
            String bestIndependentUid,double maximumR2,int locus) {
        /** Canonical reference identity. */
        public String uid() {return reference.uid();}
    }
    /** Complete candidate block and the legacy newly-added count, reported separately. */
    public record Independent(Gwas variant,List<String> candidateUids,int gwasCandidates,
            int sourceNewCandidates,int sourceNewGwasCandidates,boolean predefined,int processingOrder) {
        public Independent {candidateUids=List.copyOf(candidateUids);}
    }
    /** A lead SNP and its direct second-threshold neighbors; memberships can overlap. */
    public record Lead(Gwas variant,List<String> independentUids) {
        public Lead {independentUids=List.copyOf(independentUids);}
    }
    /** Full candidate union for a consistent interval component, not a partial backward merge. */
    public record Locus(int index,int chromosome,int start,int end,Gwas top,
            List<String> independentUids,List<String> leadUids,List<String> candidateUids,int gwasCandidates) {
        public Locus {independentUids=List.copyOf(independentUids);leadUids=List.copyOf(leadUids);candidateUids=List.copyOf(candidateUids);}
    }
    /** Disposition of each supplied GWAS row, including rows later reused as reference-only. */
    public record Disposition(String uid,String status) { }
    /** Full deterministic method output with explicit scientific/recovery notices. */
    public record Result(List<Candidate> candidates,List<Independent> independent,List<Lead> leads,
            List<Locus> loci,List<Disposition> dispositions,List<String> warnings,long work) {
        public Result {candidates=List.copyOf(candidates);independent=List.copyOf(independent);leads=List.copyOf(leads);loci=List.copyOf(loci);dispositions=List.copyOf(dispositions);warnings=List.copyOf(warnings);}
    }
    private static final Comparator<Gwas> COORDINATE=Comparator.comparingInt(Gwas::chromosome).thenComparingInt(Gwas::position).thenComparing(Gwas::uid);
    private static final Comparator<Gwas> P_ORDER=Comparator.comparingDouble(Gwas::pValue).thenComparing(COORDINATE);
    private static final class Work {
        private final long limit;private long count;
        Work(long limit) {this.limit=limit;}
        void add() {if (++count>limit) throw new IllegalArgumentException("Locus work budget exceeded; increase maximumWork explicitly or use a smaller declared input");}
    }
    /**
     * Select candidates/independent variants, then leads and complete interval loci.
     * GWAS/lead P comparisons are strict, MAF/LD comparisons inclusive. Predefined
     * seeds bypass their own P cutoff, but not reference/MAF/MHC/region eligibility.
     * Reference-only candidates may extend outside supplied GWAS regions, as in
     * the backend; this is recorded rather than described as a hard region bound.
     */
    public static Result define(List<Gwas> input,Reference reference,Options options) {
        if (input==null||input.isEmpty()||reference==null||options==null) throw new IllegalArgumentException("GWAS, reference and options are required");
        if (input.size()>options.maximumWork()) throw new IllegalArgumentException("Locus work budget is smaller than the GWAS row count");
        if (options.independentR2()<reference.storedMinimumR2 || options.leadR2()<reference.storedMinimumR2) throw new IllegalArgumentException("Stored LD floor exceeds a requested LD threshold; supply adequate pair coverage");
        Work work=new Work(options.maximumWork());Map<String,Gwas> original=new LinkedHashMap<>(),eligible=new LinkedHashMap<>();Map<Position,Gwas> eligiblePositions=new HashMap<>();Set<Position> occupiedPositions=new HashSet<>();Set<String> rsIds=new HashSet<>();Map<String,String> status=new LinkedHashMap<>();List<String> warnings=new ArrayList<>();List<Gwas> sorted=new ArrayList<>(input);sorted.sort(COORDINATE);
        for (Gwas g:sorted) {
            work.add();if (original.putIfAbsent(g.uid(),g)!=null||!rsIds.add(g.rsId())) throw new IllegalArgumentException("GWAS variant/rsID identities must be unique");
            if (options.excludedMhc()!=null&&options.excludedMhc().contains(g.chromosome(),g.position())) {status.put(g.uid(),"excluded-MHC");continue;}
            if (!options.regions().isEmpty()&&!inRegions(g,options.regions(),work)) {status.put(g.uid(),"outside-predefined-GWAS-regions");continue;}
            Position pos=new Position(g.chromosome(),g.position());occupiedPositions.add(pos);ReferenceVariant ref=reference.byUid.get(g.uid());
            if (ref==null) {status.put(g.uid(),"absent-or-allele-mismatched-reference");continue;}
            if (ref.maf()<options.minimumMaf()) {status.put(g.uid(),"below-reference-MAF");continue;}
            if (eligiblePositions.putIfAbsent(pos,g)!=null) throw new IllegalArgumentException("Ambiguous eligible GWAS position");eligible.put(g.uid(),g);status.put(g.uid(),"not-selected");
        }
        for (String uid:options.predefinedUids()) if (!original.containsKey(uid)) throw new IllegalArgumentException("Predefined UID is absent from input GWAS: "+uid);
        List<Independent> processing=new ArrayList<>();Map<String,Candidate> candidates=new LinkedHashMap<>();
        List<Gwas> seeds=new ArrayList<>();for (String uid:options.predefinedUids()) {Gwas seed=eligible.get(uid);if (seed!=null) seeds.add(seed);else warnings.add("PREDEFINED SEED SKIPPED: "+uid+" failed the explicit MHC/region/reference/MAF eligibility; inspect the GWAS disposition ledger.");}
        boolean useAutomatic=options.predefinedUids().isEmpty()||options.addIndependentVariants();
        List<Gwas> automatic=useAutomatic?eligible.values().stream().filter(g->g.pValue()<options.leadP()).sorted(P_ORDER).toList():List.of();
        for (Gwas seed:seeds) add(seed,true,reference,options,original,eligible,occupiedPositions,candidates,processing,work);
        for (int i=0;i<automatic.size();i++) {
            Gwas seed=automatic.get(i);if (candidates.containsKey(seed.uid())) continue;
            if (options.ties()==Ties.ERROR) rejectUnclaimedTie(automatic,i,candidates.keySet(),"first-stage independent selection",work);
            add(seed,false,reference,options,original,eligible,occupiedPositions,candidates,processing,work);
        }
        List<Independent> independent=new ArrayList<>(processing);independent.sort(Comparator.comparing(Independent::variant,COORDINATE));Map<String,Independent> byUid=new HashMap<>();Map<Position,Independent> byPosition=new HashMap<>();
        for (Independent ind:independent) {byUid.put(ind.variant().uid(),ind);byPosition.put(new Position(ind.variant().chromosome(),ind.variant().position()),ind);status.put(ind.variant().uid(),"independent-significant");}
        List<Gwas> leadOrder=independent.stream().map(Independent::variant).sorted(P_ORDER).toList();
        if (options.ties()==Ties.POSITION) {
            long tiedAutomatic=ties(automatic),tiedLeads=ties(leadOrder);
            if (tiedAutomatic>0||tiedLeads>0) warnings.add("COORDINATE P-TIE POLICY: "+tiedAutomatic+" repeated first-stage P values and "+tiedLeads+" repeated second-stage P values use ascending coordinate. Legacy NumPy quicksort does not guarantee that tie order and can select different index IDs. Review the explicit tie policy; a selected index is not uniquely determined by equal P values.");
        }
        Set<String> checked=new HashSet<>();List<Lead> leads=new ArrayList<>();
        for (int i=0;i<leadOrder.size();i++) {
            Gwas seed=leadOrder.get(i);
            if (checked.contains(seed.uid())) continue;List<String> members=new ArrayList<>();members.add(seed.uid());
            if (options.ties()==Ties.ERROR) rejectUnclaimedTie(leadOrder,i,checked,"second-stage lead selection",work);
            Map<Integer,Double> neighbors=reference.neighbors.get(new Position(seed.chromosome(),seed.position()));
            for (var entry:neighbors.entrySet()) {work.add();Independent member=byPosition.get(new Position(seed.chromosome(),entry.getKey()));if (entry.getValue()>=options.leadR2()&&member!=null) {checked.add(member.variant().uid());members.add(member.variant().uid());}}
            leads.add(new Lead(seed,members));
        }
        leads.sort(Comparator.comparing(Lead::variant,COORDINATE));List<Locus> loci=loci(leads,byUid,candidates,options,work);Map<String,Integer> locusFor=new HashMap<>();for (Locus locus:loci) for (String uid:locus.independentUids()) if (locusFor.put(uid,locus.index())!=null) throw new ArithmeticException("Independent variant assigned twice");
        if (!locusFor.keySet().equals(byUid.keySet())) throw new ArithmeticException("Incomplete independent-variant locus coverage");
        List<Candidate> finalCandidates=new ArrayList<>();long reclassified=0,zeroP=0,shared=0;
        for (Candidate candidate:candidates.values()) {
            Integer locus=locusFor.get(candidate.bestIndependentUid());if (locus==null) throw new ArithmeticException("Candidate has no valid independent/locus association");
            finalCandidates.add(new Candidate(candidate.reference(),candidate.gwas(),candidate.originalInput(),candidate.bestIndependentUid(),candidate.maximumR2(),locus));
            if (candidate.gwas()!=null && !byUid.containsKey(candidate.uid())) status.put(candidate.uid(),"LD-candidate");
            if (candidate.gwas()==null&&candidate.originalInput()!=null) reclassified++;
        }
        finalCandidates.sort(Comparator.comparingInt((Candidate c)->c.reference().chromosome()).thenComparingInt(c->c.reference().position()));
        for (Gwas g:input) if (g.pValue()==0) zeroP++;
        for (Independent ind:independent) if (ind.sourceNewCandidates()!=ind.candidateUids().size()) shared++;
        if (reclassified>0) warnings.add("REGION FILTER RECLASSIFICATION: "+reclassified+" rows from the original GWAS were outside supplied GWAS regions and re-entered as reference-only candidates with no canonical GWAS P, as in getLD. Region eligibility is not a hard locus boundary. Inspect original-input versus filtered-GWAS roles.");
        long forced=processing.stream().filter(Independent::predefined).count(),forcedNotSignificant=processing.stream().filter(i->i.predefined()&&i.variant().pValue()>=options.leadP()).count();
        if (forced>0) warnings.add("PREDEFINED SEED BYPASS: "+forced+" admitted predefined seeds bypass their own P cutoff and automatic first-stage independence selection; "+forcedNotSignificant+" do not meet the strict lead-P threshold. Review their predefined flags, P values and mutual LD; do not describe every forced seed as a newly discovered genome-wide significant independent signal.");
        if (zeroP>0) warnings.add("ZERO GWAS P: "+zeroP+" inputs have P=0; rounded or underflowed zeros do not retain their relative evidence. Review ties and original precision before interpreting the chosen index variant.");
        long boundaryRows=input.stream().filter(g->Math.abs(g.pValue()-options.leadP())<=4*Math.ulp(options.leadP())||Math.abs(g.pValue()-options.candidateP())<=4*Math.ulp(options.candidateP())).count();
        if (boundaryRows>0) warnings.add("STRICT P BOUNDARY: "+boundaryRows+" GWAS rows lie within four double ULPs of a strict lead/candidate cutoff. Native full-precision parsing can differ from legacy pandas input rounding. Review the original decimal P, parsed double and cutoff before accepting a boundary decision.");
        long unmatched=status.values().stream().filter("absent-or-allele-mismatched-reference"::equals).count();
        if (unmatched>0) warnings.add("GWAS/REFERENCE MISMATCH: "+unmatched+" eligible-region GWAS variants were absent from or allele-incompatible with the declared reference. Review their exact UID/build/alleles and the disposition ledger; a completed subset does not establish reference coverage of the full GWAS.");
        long monomorphic=finalCandidates.stream().filter(c->c.reference().maf()==0).count();
        if (monomorphic>0) warnings.add("ZERO-MAF REFERENCE CANDIDATES: "+monomorphic+" candidates have MAF=0 and passed the source's inclusive MAF cutoff. Self LD is set to one by the method; this does not establish a defined polymorphic LD estimate. Review the reference and selected MAF threshold.");
        if (options.excludedMhc()!=null) {
            Interval mhc=options.excludedMhc();long spanning=loci.stream().filter(l->l.chromosome()==mhc.chromosome()&&l.start()<=mhc.end()&&l.end()>=mhc.start()).count();
            if (spanning>0) warnings.add("MHC FILTER IS NOT INTERVAL CLIPPING: "+spanning+" locus bounds cross the excluded MHC interval although candidate variants in that interval were excluded. Bounds describe the outer candidate positions; do not interpret every intervening base or gene as included evidence.");
        }
        if (shared>0) warnings.add("LEGACY INCREMENTAL COUNTS: "+shared+" independent blocks share candidates already added by an earlier block. Original nSNPs/nGWASSNPs count newly added candidates; native complete-block counts and original-new counts are reported separately.");
        warnings.add("CONSISTENT LOCUS COMPONENTS: native loci merge all overlapping/nearby lead-block intervals transitively and preserve complete memberships. The pinned original backend failed backward-bridge invariants on controlled examples. This is an explicit corrected locus-construction rule, not a claim of identical legacy locus output. Review the declared merge boundary and retained numerical findings.");
        List<Disposition> dispositions=new ArrayList<>();for (var e:status.entrySet()) dispositions.add(new Disposition(e.getKey(),e.getValue()));
        return new Result(finalCandidates,independent,leads,loci,dispositions,warnings,work.count);
    }
    private static boolean inRegions(Gwas g,List<Interval> regions,Work work) {
        for (Interval region:regions) {work.add();if (region.contains(g.chromosome(),g.position())) return true;}return false;
    }
    private static void add(Gwas seed,boolean predefined,Reference reference,Options o,Map<String,Gwas> original,
            Map<String,Gwas> eligible,Set<Position> occupied,Map<String,Candidate> candidates,List<Independent> independent,Work work) {
        List<String> members=new ArrayList<>();int gwasCount=0,newCount=0,newGwas=0;
        for (ReferenceVariant ref:reference.linked(seed,o.independentR2(),work)) {
            if (ref.maf()<o.minimumMaf() || o.excludedMhc()!=null&&o.excludedMhc().contains(ref.chromosome(),ref.position())) continue;
            Gwas gwas=eligible.get(ref.uid());
            if (gwas==null && occupied.contains(new Position(ref.chromosome(),ref.position()))) continue;
            if (gwas!=null && !(predefined&&ref.uid().equals(seed.uid())) && gwas.pValue()>=o.candidateP()) continue;
            if (gwas==null&&!o.includeReferenceVariants()) continue;
            members.add(ref.uid());if (gwas!=null) gwasCount++;Candidate prior=candidates.get(ref.uid());double r2=reference.r2(seed.chromosome(),seed.position(),ref.position());
            if (prior==null) {newCount++;if (gwas!=null) newGwas++;}
            if (prior==null || r2>prior.maximumR2()) candidates.put(ref.uid(),new Candidate(ref,gwas,original.get(ref.uid()),seed.uid(),r2,0));
        }
        if (!members.contains(seed.uid())) throw new IllegalArgumentException("Independent seed did not enter its own candidate block; check P/reference/MAF protocol");
        independent.add(new Independent(seed,members,gwasCount,newCount,newGwas,predefined,independent.size()));
    }
    private record Block(Lead lead,int start,int end) { }
    private static List<Locus> loci(List<Lead> leads,Map<String,Independent> byUid,Map<String,Candidate> candidates,Options o,Work work) {
        List<Block> blocks=new ArrayList<>();
        for (Lead lead:leads) {
            int lo=Integer.MAX_VALUE,hi=0;
            for (String uid:lead.independentUids()) for (String candidate:byUid.get(uid).candidateUids()) {work.add();int p=candidates.get(candidate).reference().position();lo=Math.min(lo,p);hi=Math.max(hi,p);}
            blocks.add(new Block(lead,lo,hi));
        }
        blocks.sort(Comparator.comparingInt((Block b)->b.lead().variant().chromosome()).thenComparingInt(Block::start).thenComparingInt(Block::end));List<Locus> result=new ArrayList<>();List<Block> component=new ArrayList<>();int chr=-1,end=-1;
        for (Block block:blocks) {
            work.add();long gap=(long)block.start()-end;boolean close=block.start()<=end || (o.mergeBoundary()==MergeBoundary.SOURCE_STRICT?gap<o.mergeDistanceBp():gap<=o.mergeDistanceBp());
            if (!component.isEmpty()&&(block.lead().variant().chromosome()!=chr||!close)) {result.add(locus(result.size()+1,component,byUid,candidates));component.clear();end=-1;}
            component.add(block);chr=block.lead().variant().chromosome();end=Math.max(end,block.end());
        }
        if (!component.isEmpty()) result.add(locus(result.size()+1,component,byUid,candidates));return result;
    }
    private static Locus locus(int index,List<Block> component,Map<String,Independent> independent,Map<String,Candidate> candidates) {
        List<Lead> leads=component.stream().map(Block::lead).sorted(Comparator.comparing(Lead::variant,COORDINATE)).toList();Set<String> inds=new LinkedHashSet<>(),members=new LinkedHashSet<>();List<String> leadUids=new ArrayList<>();
        for (Lead lead:leads) {leadUids.add(lead.variant().uid());inds.addAll(lead.independentUids());}
        for (String uid:inds) members.addAll(independent.get(uid).candidateUids());List<String> sortedMembers=members.stream().sorted(Comparator.comparingInt(u->candidates.get(u).reference().position())).toList();Gwas top=leads.stream().map(Lead::variant).min(P_ORDER).orElseThrow();int lo=Integer.MAX_VALUE,hi=0,nGwas=0;
        for (String uid:sortedMembers) {Candidate c=candidates.get(uid);lo=Math.min(lo,c.reference().position());hi=Math.max(hi,c.reference().position());if (c.gwas()!=null) nGwas++;}
        return new Locus(index,top.chromosome(),lo,hi,top,new ArrayList<>(inds),leadUids,sortedMembers,nGwas);
    }
    private static void rejectUnclaimedTie(List<Gwas> rows,int index,Set<String> claimed,String phase,Work work) {
        Gwas seed=rows.get(index);
        for (int j=index+1;j<rows.size();j++) {
            Gwas next=rows.get(j);if (next.chromosome()!=seed.chromosome()||Double.compare(next.pValue(),seed.pValue())!=0) break;
            work.add();if (!claimed.contains(next.uid())) throw new IllegalArgumentException("Equal unclaimed P-values in "+phase+"; choose an explicit coordinate tie policy or supply justified higher-precision input");
        }
    }
    private static long ties(List<Gwas> rows) {
        Set<String> seen=new HashSet<>();long repeated=0;for (Gwas g:rows) if (!seen.add(g.chromosome()+":"+Double.toHexString(g.pValue()))) repeated++;return repeated;
    }
    private static String key(int chr,int pos,String a,String b) {return chr+":"+pos+":"+(a.compareTo(b)<0?a+":"+b:b+":"+a);}
    private static void alleles(String a,String b) {token(a);token(b);if (a.equals(b)) throw new IllegalArgumentException("Variant alleles must differ");}
    private static void coordinate(int chr,int pos) {if (chr<1||pos<1) throw new IllegalArgumentException("Positive numeric chromosome and one-based position required");}
    private static void probability(double p) {if (!Double.isFinite(p)||p<0||p>1) throw new IllegalArgumentException("Probability/LD must be in [0,1]");}
    private static void token(String value) {if (value==null||value.isBlank()||value.chars().anyMatch(c->Character.isWhitespace(c)||c==':'||c==';')) throw new IllegalArgumentException("Invalid variant token");}
}
