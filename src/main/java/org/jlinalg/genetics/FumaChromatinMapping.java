/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Supplied chromatin interactions, with explicit source pooling and coordinate policies. */
public final class FumaChromatinMapping {
    private FumaChromatinMapping() { }
    /** SOURCE_GLOBAL reproduces original cross-row SNP pooling; PER_INTERACTION also requires each row's listed rsID. */
    public enum Membership { SOURCE_GLOBAL, PER_INTERACTION }
    /** Original closed bounds (zero lower sentinel allowed), or BED bounds converted to positive closed positions. */
    public enum Coordinates { SOURCE_CLOSED, BED }
    /** Literal region identity is retained for exact promoter lookup; coordinate fields never replace that key. */
    public static final class Region {
        private final String label;
        private final int chromosome,start,end;
        /** Parse numeric chromosome:start-end, preserving the literal text including leading zeros. */
        public Region(String text) {
            if(text==null)throw new IllegalArgumentException("Missing chromatin region");
            var match=Pattern.compile("([0-9]+):([0-9]+)-([0-9]+)").matcher(text);
            if(!match.matches())throw new IllegalArgumentException("Require explicit numeric chromatin region CHR:START-END: "+text);
            label=text;chromosome=Integer.parseInt(match.group(1));start=Integer.parseInt(match.group(2));end=Integer.parseInt(match.group(3));
            if(chromosome<1||chromosome>23||end<start)throw new IllegalArgumentException("Chromatin regions require chromosome1..23 and0<=start<=end");
        }
        /** Exact source identity. */ public String label(){return label;}
        /** Human chromosome code, with X represented by23. */ public int chromosome(){return chromosome;}
        /** Supplied lower bound, before the explicit coordinate policy. */ public int start(){return start;}
        /** Supplied upper bound. */ public int end(){return end;}
    }
    /** One identified interaction; an empty gene list represents source NA. Tissue labels may contain spaces. */
    public record Interaction(String id,Region region1,Region region2,List<String> genes,List<String> rsIds,String tissue) {
        public Interaction {token(id);if(region1==null||region2==null)throw new IllegalArgumentException("Require both interaction regions");genes=ids(genes);rsIds=ids(rsIds);if(tissue==null||tissue.isBlank()||tissue.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require a nonempty control-free interaction tissue label");}
    }
    /** Supplied promoter region-to-gene assignments; repeated exact keys are unioned without inventing interval matches. */
    public record Promoter(Region region,List<String> genes) {
        public Promoter {if(region==null)throw new IllegalArgumentException("Require promoter region");genes=ids(genes);}
    }
    /** Null enhancer set disables enhancer filtering. Gene/promoter and annotation acquisition remain upstream. */
    public record Inputs(List<Interaction> interactions,FumaVariantGeneMapping.Filter filter,Set<String> enhancerUids,
            boolean promoterFilter,List<Promoter> promoters,Membership membership,Coordinates coordinates,int maxOverlapHits) {
        public Inputs {
            interactions=List.copyOf(interactions);promoters=List.copyOf(promoters);
            if(filter==null||membership==null||coordinates==null||maxOverlapHits<1)throw new IllegalArgumentException("Require explicit chromatin filter, membership, coordinates and positive overlap budget");
            if(enhancerUids!=null)enhancerUids=Set.copyOf(ids(new ArrayList<>(enhancerUids)));
            if(!promoterFilter&&!promoters.isEmpty())throw new IllegalArgumentException("Promoter rows supplied while promoter filtering is disabled");
            var seen=new HashSet<String>();for(var row:interactions){if(!seen.add(row.id()))throw new IllegalArgumentException("Duplicate interaction ID: "+row.id());if(coordinates==Coordinates.BED&&(row.region1().start()==row.region1().end()||row.region2().start()==row.region2().end()))throw new IllegalArgumentException("BED interaction intervals must have positive width");}
            if(coordinates==Coordinates.BED)for(var row:promoters)if(row.region().start()==row.region().end())throw new IllegalArgumentException("BED promoter intervals must have positive width");
        }
    }
    /** Actual candidate/gene edge and its exact interaction provenance; source pooled edges may be unlisted in that row. */
    public record Evidence(String interactionId,String uid,String gene,String tissue,boolean listedInInteraction) { }
    /** Initial overlap, final overlap, declaration and eligibility are deliberately separate. */
    public record InteractionDisposition(Interaction interaction,List<String> effectiveGenes,List<String> eligibleGenes,
            List<String> admittingUids,List<String> finalOverlapUids,boolean retained,String status) {
        public InteractionDisposition {effectiveGenes=List.copyOf(effectiveGenes);eligibleGenes=List.copyOf(eligibleGenes);admittingUids=List.copyOf(admittingUids);finalOverlapUids=List.copyOf(finalOverlapUids);}
    }
    /** A pool flag is not evidence of a mapped gene; in the source it can survive rejection of every interaction. */
    public record CandidateDisposition(String uid,boolean filteredPool,boolean retainedPoolFlag,boolean mapped) { }
    /** Gene declaration/tissues use admitted rows; the variant list includes only actual eligible overlap edges. */
    public record GeneMapping(String gene,List<String> variantUids,List<String> tissues,int evidenceRows) {
        public GeneMapping {variantUids=List.copyOf(variantUids);tissues=List.copyOf(tissues);}
    }
    /** Immutable stage output, retained independently of combined positional/eQTL summaries. */
    public record Result(List<Evidence> evidence,List<InteractionDisposition> interactions,List<CandidateDisposition> candidates,List<GeneMapping> genes,List<String> warnings,long overlapHits) {
        public Result {evidence=List.copyOf(evidence);interactions=List.copyOf(interactions);candidates=List.copyOf(candidates);genes=List.copyOf(genes);warnings=List.copyOf(warnings);}
        /** Disabled stage with no chromatin evidence or flags. */ public static Result disabled(){return new Result(List.of(),List.of(),List.of(),List.of(),List.of(),0);}
    }
    // Called after FumaVariantGeneMapping has validated resource uniqueness, candidate/annotation alignment and gene eligibility.
    static Result map(List<FumaVariantGeneMapping.Gene> eligible,List<FumaVariantGeneMapping.Variant> candidates,
            Map<String,FumaVariantGeneMapping.Annotation> annotations,Inputs input,int maxEvidence) {
        if(maxEvidence<0)throw new IllegalArgumentException("Negative remaining chromatin evidence budget");
        var geneIds=new HashSet<String>();eligible.forEach(g->geneIds.add(g.id()));var listed=new LinkedHashSet<String>();input.interactions().forEach(i->listed.addAll(i.rsIds()));
        var filtered=new LinkedHashMap<String,FumaVariantGeneMapping.Variant>();
        for(var candidate:candidates)if(listed.contains(candidate.rsId())&&(input.enhancerUids()==null||input.enhancerUids().contains(candidate.uid()))&&FumaVariantGeneMapping.passesAnnotation(annotations.get(candidate.uid()),input.filter()))filtered.put(candidate.uid(),candidate);
        // Even a zero-candidate analysis checks named annotation columns instead of silently ignoring an invalid filter.
        for(var annotation:annotations.values())FumaVariantGeneMapping.validateColumns(annotation,input.filter());
        var windows=new ArrayList<GeneWindowMapping.Gene>();
        for(var row:input.interactions())if(row.region1().end()>0){var r=row.region1();int low=input.coordinates()==Coordinates.BED?Math.addExact(r.start(),1):Math.max(1,r.start());windows.add(new GeneWindowMapping.Gene(row.id(),Integer.toString(r.chromosome()),low,r.end(),"."));}
        var variants=filtered.values().stream().map(v->new GeneWindowMapping.Variant(v.uid(),Integer.toString(v.chromosome()),v.position())).toList();
        var overlaps=new HashMap<String,List<String>>();long hitCount=0;
        for(var assignment:GeneWindowMapping.assign(variants,windows,0,0,true,input.maxOverlapHits())){var hit=assignment.variants().stream().map(GeneWindowMapping.Variant::id).toList();overlaps.put(assignment.gene().id(),hit);hitCount+=hit.size();}
        var promoters=new LinkedHashMap<String,LinkedHashSet<String>>();for(var row:input.promoters())promoters.computeIfAbsent(row.region().label(),k->new LinkedHashSet<>()).addAll(row.genes());
        record Row(Interaction interaction,List<String> effective,List<String> genes,List<String> initial,Set<String> listedRsIds,String reason) { }
        var rows=new ArrayList<Row>();var finalRs=new LinkedHashSet<String>();int retainedRows=0;long declaredGeneMemberships=0;
        for(var row:input.interactions()) {
            var own=new HashSet<>(row.rsIds());var initial=overlaps.getOrDefault(row.id(),List.of());if(input.membership()==Membership.PER_INTERACTION)initial=initial.stream().filter(uid->own.contains(filtered.get(uid).rsId())).toList();
            List<String> effective=input.promoterFilter()?new ArrayList<>(promoters.getOrDefault(row.region2().label(),new LinkedHashSet<>())):row.genes();var eligibleGenes=effective.stream().filter(geneIds::contains).toList();
            String reason=initial.isEmpty()?"excluded-no-filtered-overlap":input.promoterFilter()&&!promoters.containsKey(row.region2().label())?"excluded-no-exact-promoter-key":effective.isEmpty()?"excluded-no-gene-list":"retained";
            if(reason.equals("retained")){retainedRows++;finalRs.addAll(row.rsIds());declaredGeneMemberships+=eligibleGenes.size();if(declaredGeneMemberships>maxEvidence)throw new IllegalArgumentException("Chromatin declared-gene membership budget exceeded");}
            rows.add(new Row(row,effective,eligibleGenes,initial,own,reason));
        }
        var finalPool=new LinkedHashSet<String>();for(var v:filtered.values())if(finalRs.contains(v.rsId())||retainedRows==0&&input.membership()==Membership.SOURCE_GLOBAL)finalPool.add(v.uid());
        var evidence=new ArrayList<Evidence>();var dispositions=new ArrayList<InteractionDisposition>();var geneUids=new LinkedHashMap<String,LinkedHashSet<String>>();var geneTissues=new LinkedHashMap<String,LinkedHashSet<String>>();var geneCounts=new HashMap<String,Integer>();var mapped=new HashSet<String>();long unlistedEdges=0;int unsupported=0;
        for(var row:rows) {
            boolean retained=row.reason().equals("retained");var finalHits=retained?row.initial().stream().filter(finalPool::contains).toList():List.<String>of();String reason=row.reason();
            if(retained){
                if(finalHits.isEmpty()){reason="retained-without-final-overlap";unsupported++;}else if(row.genes().isEmpty())reason="retained-no-eligible-gene";
                for(String gene:row.genes()){
                    geneUids.computeIfAbsent(gene,k->new LinkedHashSet<>()).addAll(finalHits);geneTissues.computeIfAbsent(gene,k->new LinkedHashSet<>()).add(row.interaction().tissue());
                    for(String uid:finalHits){if(evidence.size()>=maxEvidence)throw new IllegalArgumentException("Chromatin mapping evidence budget exceeded");boolean own=row.listedRsIds().contains(filtered.get(uid).rsId());evidence.add(new Evidence(row.interaction().id(),uid,gene,row.interaction().tissue(),own));geneCounts.merge(gene,1,Integer::sum);mapped.add(uid);if(!own)unlistedEdges++;}
                }
            }
            dispositions.add(new InteractionDisposition(row.interaction(),row.effective(),row.genes(),row.initial(),finalHits,retained,reason));
        }
        var genes=new ArrayList<GeneMapping>();for(var e:geneUids.entrySet())genes.add(new GeneMapping(e.getKey(),new ArrayList<>(e.getValue()),new ArrayList<>(geneTissues.get(e.getKey())),geneCounts.getOrDefault(e.getKey(),0)));
        var flags=candidates.stream().map(v->new CandidateDisposition(v.uid(),filtered.containsKey(v.uid()),finalPool.contains(v.uid()),mapped.contains(v.uid()))).toList();var warnings=new ArrayList<String>();
        warnings.add("CHROMATIN SUPPLIED-RESOURCE POLICY: "+input.membership()+", "+input.coordinates()+". Required action: review declared row rsIDs, both literal region keys, annotation/enhancer filters and supplied promoter gene assignments. SOURCE_GLOBAL pools candidates across rows and can flag candidates without gene links; PER_INTERACTION additionally requires the row's own rsID. No interaction discovery, FDR testing, enhancer/promoter annotation or gene assignment from region2 coordinates is inferred.");
        if(unlistedEdges>0)warnings.add("CROSS-ROW SNP POOLING: "+unlistedEdges+" eligible gene edges use a variant not listed in that interaction. Required action: inspect listed_in_interaction and both overlap ledgers; confirm the original global-pool policy or explicitly choose per-interaction membership.");
        if(unsupported>0)warnings.add("RETAINED WITHOUT FINAL SUPPORT: "+unsupported+" admitted interaction rows lost all overlapping candidates when the final listed-SNP pool was restricted. Source gene declarations/tissues remain, but no overlap/GWAS/LD evidence is fabricated. Required action: inspect these rows and do not report declarations as observed variant-to-gene links.");
        long flaggedOnly=flags.stream().filter(c->c.retainedPoolFlag()&&!c.mapped()).count();if(flaggedOnly>0)warnings.add("POOL FLAGS WITHOUT GENE LINKS: "+flaggedOnly+" candidates have a retained pool flag without eligible gene evidence. Original source-global flags can survive rejection of every row. Required action: use chromatin_mapped and chromatin-evidence.tsv for actual links; retain the separate source-like pool flags for audit.");
        var knownRs=new HashSet<String>();candidates.forEach(c->knownRs.add(c.rsId()));long unknown=listed.stream().filter(id->!knownRs.contains(id)).count();if(unknown>0)warnings.add("UNAVAILABLE LISTED SNPs: "+unknown+" distinct listed rsIDs are absent from candidates. Required action: confirm candidate coverage, rsID aliases and assembly; no missing variant is synthesized.");
        return new Result(evidence,dispositions,flags,genes,warnings,hitCount);
    }
    private static void token(String v){if(!GeneWindowMapping.token(v))throw new IllegalArgumentException("Require nonempty whitespace-free chromatin resource tokens");}
    private static List<String> ids(List<String> input){var seen=new LinkedHashSet<String>();for(String id:input){token(id);if(!seen.add(id))throw new IllegalArgumentException("Duplicate chromatin list ID: "+id);}return List.copyOf(seen);}
}
