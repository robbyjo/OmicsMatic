/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Independent supplied-resource positional/eQTL/chromatin portion of FUMA's SNP2GENE mapping.
 * This class does not obtain annotations, discover eQTLs, align alleles, estimate LD,
 * discover chromatin interactions, or reproduce the complete hosted pipeline.
 */
public final class FumaVariantGeneMapping {
    private FumaVariantGeneMapping() { }
    /** Chromatin-state aggregation; MODE resolves equal frequencies to the smaller numeric state. */
    public enum States { ANY, MODE, ALL }
    /** Binary annotation aggregation; MAJORITY requires strictly more than half of the selected tracks. */
    public enum Binary { ANY, MAJORITY, ALL }
    /** Explicit source endpoint-based MHC exclusion, or complete interval overlap exclusion. */
    public enum Mhc { KEEP, SOURCE_ENDPOINTS, FULL_OVERLAP }
    /** Identified, one-based closed gene record. */
    public record Gene(String id,String symbol,int chromosome,int start,int end,String strand,String biotype) {
        public Gene {token(id);token(symbol);token(biotype);validateChromosome(chromosome);if(start<1||end<start||!Set.of("+","-",".").contains(strand))throw new IllegalArgumentException("Invalid gene coordinates or strand");}
    }
    /** Candidate GWAS/reference variant, with nullable scientific numbers represented only by NaN. */
    public record Variant(String uid,String rsId,int chromosome,int position,double gwasP,double cadd,double rdb,String locus,List<String> independentRsIds) {
        public Variant {token(uid);token(rsId);token(locus);validateChromosome(chromosome);if(position<1||Integer.parseInt(locus)<1)throw new IllegalArgumentException("Variant position/locus must be positive integers");probability(gwasP);score(cadd);score(rdb);independentRsIds=distinct(independentRsIds);}
    }
    /** Separate supplied annotation scores, selected numeric states, and binary functional tracks. */
    public record Annotation(String uid,double cadd,double rdb,Map<String,Double> states,Map<String,Double> binary) {
        public Annotation {token(uid);score(cadd);score(rdb);states=values(states,false);binary=values(binary,true);}
    }
    /** A supplied variant/gene consequence; patterns use regex search, as in the original script. */
    public record Consequence(String uid,String gene,String annotation) {
        public Consequence {token(uid);token(gene);token(annotation);}
    }
    /** A supplied association row; P/FDR/direction are retained without allele alignment or re-estimation. */
    public record Eqtl(String uid,String gene,String database,String tissue,double p,double fdr,String alignedDirection) {
        public Eqtl {token(uid);token(gene);token(database);Pattern.compile(database);token(tissue);probability(p);probability(fdr);if(alignedDirection==null||!alignedDirection.isEmpty()&&!Set.of("+","-").contains(alignedDirection))throw new IllegalArgumentException("Aligned direction must be +, - or empty; alignment is upstream");}
    }
    /** Explicit selected annotation columns; no positional-column or substring 'all' inference. */
    public record Filter(double minCadd,double maxRdb,List<String> stateColumns,States stateMethod,int maxState,List<String> binaryColumns,Binary binaryMethod) {
        public Filter {if(!Double.isFinite(minCadd)||minCadd<0)throw new IllegalArgumentException("Minimum CADD must be finite and nonnegative; zero disables it");score(maxRdb);stateColumns=distinct(stateColumns);binaryColumns=distinct(binaryColumns);if(stateMethod==null||binaryMethod==null||maxState<1||maxState>15)throw new IllegalArgumentException("Require state method, binary method and state maximum 1..15");}
    }
    /** Native additional association filtering; NaN cutoffs leave upstream P/FDR selections intact. */
    public record EqtlSelection(Set<String> tissues,double maxP,double maxFdr) {
        public EqtlSelection {tissues=Set.copyOf(distinct(new ArrayList<>(tissues)));probability(maxP);probability(maxFdr);}
    }
    /** Explicit module choices, annotation identity, MHC policy, and a bound on emitted evidence rows. */
    public record Options(String annotationId,Set<String> biotypes,Mhc mhc,Integer mhcStart,Integer mhcEnd,
            boolean positional,Integer windowBases,String consequenceRegex,Filter positionalFilter,
            boolean eqtl,Filter eqtlFilter,EqtlSelection eqtlSelection,int maxEvidence) {
        public Options {
            token(annotationId);biotypes=Set.copyOf(distinct(new ArrayList<>(biotypes)));
            if(mhc==null||positionalFilter==null||eqtlFilter==null||eqtlSelection==null||maxEvidence<1)throw new IllegalArgumentException("Require explicit filters, MHC policy and positive evidence budget; enabled modules are checked with chromatin inputs at mapping time");
            if((mhcStart==null)!=(mhcEnd==null)||mhcStart!=null&&(mhcStart<1||mhcEnd<mhcStart))throw new IllegalArgumentException("MHC extension needs valid start and end");
            if(mhc==Mhc.KEEP&&mhcStart!=null)throw new IllegalArgumentException("MHC extension cannot accompany KEEP");
            if(windowBases!=null&&(windowBases<0||consequenceRegex!=null)||positional&&windowBases==null&&(consequenceRegex==null||consequenceRegex.isEmpty()))throw new IllegalArgumentException("Positional mapping requires a nonnegative window or an explicit consequence regex");
            if(consequenceRegex!=null)Pattern.compile(consequenceRegex);
        }
    }
    /** Every admitted positional row, including distinct consequences for the same variant/gene pair. */
    public record PositionEvidence(String uid,String gene,String consequence) { }
    /** Method-specific and combined, unique-variant gene summaries. Missing numeric summaries remain NaN. */
    public record GeneResult(Gene gene,int positionalVariants,int positionalEvidenceRows,double maxCadd,
            int eqtlVariants,double minEqtlP,double minEqtlFdr,List<String> tissues,String direction,
            double minGwasP,List<String> independentRsIds,List<String> loci) {
        public GeneResult {tissues=List.copyOf(tissues);independentRsIds=List.copyOf(independentRsIds);loci=List.copyOf(loci);}
    }
    /** Auditable candidate filter/mapping flags. Passing a filter alone is not gene mapping. */
    public record VariantResult(Variant variant,boolean positionalFilter,boolean positionalMapped,boolean eqtlFilter,boolean eqtlMapped) { }
    /** Immutable bounded result; all warnings need review before interpretation. */
    public record Result(List<GeneResult> genes,List<PositionEvidence> positional,List<Eqtl> eqtls,List<VariantResult> variants,List<String> excludedGenes,List<String> warnings,FumaChromatinMapping.Result chromatin) {
        public Result {genes=List.copyOf(genes);positional=List.copyOf(positional);eqtls=List.copyOf(eqtls);variants=List.copyOf(variants);excludedGenes=List.copyOf(excludedGenes);warnings=List.copyOf(warnings);if(chromatin==null)throw new IllegalArgumentException("Require explicit chromatin result, including disabled stage");}
        /** Backward-compatible construction of a positional/eQTL result without chromatin mapping. */
        public Result(List<GeneResult> genes,List<PositionEvidence> positional,List<Eqtl> eqtls,List<VariantResult> variants,List<String> excludedGenes,List<String> warnings){this(genes,positional,eqtls,variants,excludedGenes,warnings,FumaChromatinMapping.Result.disabled());}
    }
    /** Map supplied evidence. Every enabled module requires explicit, internally consistent resources. */
    public static Result map(List<Gene> genes,List<Variant> variants,List<Annotation> annotations,List<Consequence> consequences,List<Eqtl> eqtls,Options options) {
        return map(genes,variants,annotations,consequences,eqtls,options,null);
    }
    /** Map supplied evidence, optionally adding typed chromatin inputs; null disables that stage.
     * @param genes complete supplied gene resource
     * @param variants supplied candidate variants with GWAS/LD provenance
     * @param annotations exactly one annotation per candidate
     * @param consequences supplied consequence rows
     * @param eqtls supplied, previously selected and aligned eQTL associations
     * @param options explicit module/filter/gene/resource choices
     * @param chromatin supplied chromatin interactions and policies, or null to disable
     * @return combined gene summaries and separate evidence/disposition ledgers
     */
    public static Result map(List<Gene> genes,List<Variant> variants,List<Annotation> annotations,List<Consequence> consequences,List<Eqtl> eqtls,Options options,FumaChromatinMapping.Inputs chromatin) {
        if(!options.positional()&&!options.eqtl()&&chromatin==null)throw new IllegalArgumentException("Enable at least one mapping module");
        unique(genes,Gene::id,"gene");var byVariant=unique(variants,Variant::uid,"variant");var byAnnotation=unique(annotations,Annotation::uid,"annotation");
        if(genes.isEmpty()||variants.isEmpty())throw new IllegalArgumentException("Require nonempty gene and candidate resources");
        if(!byAnnotation.keySet().equals(byVariant.keySet()))throw new IllegalArgumentException("Annotation IDs must match the candidate IDs exactly; supply explicit NaN scores for missing annotations");
        for(var a:annotations)for(var filter:List.of(options.positionalFilter(),options.eqtlFilter())) {
            if(!a.states().keySet().containsAll(filter.stateColumns())||!a.binary().keySet().containsAll(filter.binaryColumns()))throw new IllegalArgumentException("Missing explicitly selected annotation column for "+a.uid());
        }
        for(var c:consequences)if(!byVariant.containsKey(c.uid()))throw new IllegalArgumentException("Consequence references an unknown candidate: "+c.uid());
        for(var e:eqtls)if(!byVariant.containsKey(e.uid()))throw new IllegalArgumentException("eQTL references an unknown candidate: "+e.uid());
        var warnings=new ArrayList<String>();warnings.add(chromatin==null?"SUPPLIED-RESOURCE POSITIONAL/eQTL MODULE: review annotation identity, assembly, candidate/LD scope and prior eQTL selection. No annotation download, eQTL discovery/allele alignment, chromatin interactions, gene scores, MAGMA or full FUMA workflow is performed.":"SUPPLIED-RESOURCE POSITIONAL/eQTL/CHROMATIN MODULE: review annotation identity, assembly, candidate/LD scope, upstream eQTL alignment and interaction/promoter resources. No annotation download, eQTL/interaction discovery, allele alignment, gene scores, MAGMA or full FUMA workflow is performed.");
        var eligible=new LinkedHashMap<String,Gene>();for(var g:genes)if(options.biotypes().isEmpty()||options.biotypes().contains(g.biotype()))eligible.put(g.id(),g);
        int mhcStart=0,mhcEnd=0;
        if(options.mhc()!=Mhc.KEEP) {
            var start=eligible.values().stream().filter(g->g.symbol().equals("MOG")).toList();var end=eligible.values().stream().filter(g->g.symbol().equals("COL11A2")).toList();
            if(start.size()!=1||end.size()!=1||start.get(0).chromosome()!=6||end.get(0).chromosome()!=6)throw new IllegalArgumentException("MHC exclusion requires unique chromosome-6 MOG/COL11A2 anchors after biotype filtering");
            mhcStart=start.get(0).start();mhcEnd=end.get(0).end();if(mhcEnd<mhcStart)throw new IllegalArgumentException("Reversed MHC anchors");
            if(options.mhcStart()!=null){mhcStart=Math.min(mhcStart,options.mhcStart());mhcEnd=Math.max(mhcEnd,options.mhcEnd());}
            final int low=mhcStart,high=mhcEnd;
            eligible.values().removeIf(g->g.chromosome()==6&&(options.mhc()==Mhc.FULL_OVERLAP?g.start()<=high&&g.end()>=low:g.start()>=low&&g.start()<=high||g.end()>=low&&g.end()<=high));
            warnings.add("MHC GENE POLICY "+options.mhc()+": chromosome6 closed interval "+mhcStart+".."+mhcEnd+" uses MOG start and COL11A2 end after biotype filtering. SOURCE_ENDPOINTS retains genes spanning the entire interval; FULL_OVERLAP is an explicit extension. Review excludedGenes; this is separate from candidate MHC and GENE2FUNC policies.");
        }
        var excluded=new ArrayList<String>();for(var g:genes)if(!eligible.containsKey(g.id()))excluded.add(g.id());
        var posPass=new LinkedHashSet<String>();var eqtlPass=new LinkedHashSet<String>();
        for(var v:variants) {
            var a=byAnnotation.get(v.uid());
            if(options.positional()&&passes(v.cadd(),v.rdb(),a,options.positionalFilter()))posPass.add(v.uid());
            if(options.eqtl()&&passes(a.cadd(),a.rdb(),a,options.eqtlFilter()))eqtlPass.add(v.uid());
        }
        var positional=new LinkedHashSet<PositionEvidence>();
        if(options.positional()) {
            if(options.windowBases()!=null) {
                var vs=variants.stream().filter(v->posPass.contains(v.uid())).map(v->new GeneWindowMapping.Variant(v.uid(),Integer.toString(v.chromosome()),v.position())).toList();
                var gs=eligible.values().stream().map(g->new GeneWindowMapping.Gene(g.id(),Integer.toString(g.chromosome()),g.start(),g.end(),g.strand())).toList();
                // Positive candidate coordinates make clipping at zero membership-equivalent to a negative lower window.
                for(var assignment:GeneWindowMapping.assign(vs,gs,options.windowBases(),options.windowBases(),true,options.maxEvidence()))for(var v:assignment.variants()) {
                    positional.add(new PositionEvidence(v.id(),assignment.gene().id(),"window"));budget(positional.size(),0,options.maxEvidence());
                }
            } else {
                var pattern=Pattern.compile(options.consequenceRegex());
                for(var c:consequences)if(eligible.containsKey(c.gene())&&posPass.contains(c.uid())&&pattern.matcher(c.annotation()).find()) {
                    positional.add(new PositionEvidence(c.uid(),c.gene(),c.annotation()));budget(positional.size(),0,options.maxEvidence());
                }
                warnings.add("CONSEQUENCE REGEX: selection uses regex search, not exact annotation-label matching. Distinct consequence rows for one pair remain visible; positionalVariants counts unique candidates while positionalEvidenceRows records source-like row counts. Review both before reporting counts.");
            }
        }
        var admitted=new ArrayList<Eqtl>();
        if(options.eqtl()) {
            var selection=options.eqtlSelection();
            for(var e:eqtls)if(eligible.containsKey(e.gene())&&eqtlPass.contains(e.uid())&&(selection.tissues().isEmpty()||selection.tissues().contains(e.tissue()))&&cut(e.p(),selection.maxP())&&cut(e.fdr(),selection.maxFdr())) {
                admitted.add(e);budget(positional.size(),admitted.size(),options.maxEvidence());
            }
            warnings.add("eQTL SELECTION AND DIRECTION: original geneMap.R reads but does not apply eQTL significance/tissue settings; its eqtl.txt is upstream-selected. Native explicit P/FDR/tissue cuts are additional inclusive filters, and NaN cuts preserve supplied selection. Aligned directions must already be on the intended GWAS allele; no harmonization is inferred. Review upstream provenance and every retained association.");
        }
        var posByGene=new LinkedHashMap<String,List<PositionEvidence>>();for(var p:positional)posByGene.computeIfAbsent(p.gene(),k->new ArrayList<>()).add(p);
        var eqByGene=new LinkedHashMap<String,List<Eqtl>>();for(var e:admitted)eqByGene.computeIfAbsent(e.gene(),k->new ArrayList<>()).add(e);
        var ci=chromatin==null?FumaChromatinMapping.Result.disabled():FumaChromatinMapping.map(new ArrayList<>(eligible.values()),variants,byAnnotation,chromatin,options.maxEvidence()-positional.size()-admitted.size());warnings.addAll(ci.warnings());
        var ciByGene=new LinkedHashMap<String,FumaChromatinMapping.GeneMapping>();for(var gene:ci.genes())ciByGene.put(gene.gene(),gene);
        var sorted=eligible.values().stream().filter(g->posByGene.containsKey(g.id())||eqByGene.containsKey(g.id())||ciByGene.containsKey(g.id())).sorted(Comparator.comparingInt(Gene::chromosome).thenComparingInt(Gene::start)).toList();
        var output=new ArrayList<GeneResult>();var mappedPos=new HashSet<String>();var mappedEq=new HashSet<String>();
        for(var g:sorted) {
            var pv=new LinkedHashSet<String>();var ev=new LinkedHashSet<String>();double maxCadd=Double.NaN,minP=Double.NaN,minQ=Double.NaN;
            var tissues=new LinkedHashSet<String>();var directions=new TreeMap<String,Integer>();
            var positionRows=posByGene.getOrDefault(g.id(),List.of());
            for(var p:positionRows){pv.add(p.uid());maxCadd=max(maxCadd,byAnnotation.get(p.uid()).cadd());}
            for(var e:eqByGene.getOrDefault(g.id(),List.of())) {ev.add(e.uid());minP=min(minP,e.p());minQ=min(minQ,e.fdr());String tissue=e.tissue().replaceFirst(".txt.gz","");tissues.add(Pattern.compile(e.database()).matcher(tissue).find()?tissue:e.database()+"/"+tissue);if(!e.alignedDirection().isEmpty())directions.merge(e.alignedDirection(),1,Integer::sum);}
            String direction="";int count=0;for(var d:directions.entrySet())if(d.getValue()>count){direction=d.getKey();count=d.getValue();}
            mappedPos.addAll(pv);mappedEq.addAll(ev);var union=new LinkedHashSet<>(pv);union.addAll(ev);double gwas=Double.NaN;var independent=new LinkedHashSet<String>();var loci=new TreeSet<String>(Comparator.comparingInt(Integer::parseInt));
            if(ciByGene.containsKey(g.id()))union.addAll(ciByGene.get(g.id()).variantUids());
            for(String uid:union){var v=byVariant.get(uid);gwas=min(gwas,v.gwasP());independent.addAll(v.independentRsIds());loci.add(v.locus());}
            output.add(new GeneResult(g,pv.size(),positionRows.size(),maxCadd,ev.size(),minP,minQ,new ArrayList<>(tissues),direction,gwas,new ArrayList<>(independent),new ArrayList<>(loci)));
        }
        if(variants.stream().anyMatch(v->Double.compare(v.cadd(),byAnnotation.get(v.uid()).cadd())!=0||Double.compare(v.rdb(),byAnnotation.get(v.uid()).rdb())!=0))warnings.add("DISTINCT SCORE SOURCES: positional CADD/RegulomeDB filters use candidate-table scores; eQTL filters and positional maximum CADD use annotation-table scores, matching geneMap.R. Review mismatched scores and resource versions; no table is silently substituted.");
        if(output.isEmpty())warnings.add("NO MAPPED GENES: inspect candidate filters, gene exclusions, supplied evidence and upstream significance selections; an empty result does not establish absence of biological association.");
        var variantResults=variants.stream().map(v->new VariantResult(v,posPass.contains(v.uid()),mappedPos.contains(v.uid()),eqtlPass.contains(v.uid()),mappedEq.contains(v.uid()))).toList();
        return new Result(output,new ArrayList<>(positional),admitted,variantResults,excluded,warnings,ci);
    }
    static void validateColumns(Annotation annotation,Filter filter){if(!annotation.states().keySet().containsAll(filter.stateColumns())||!annotation.binary().keySet().containsAll(filter.binaryColumns()))throw new IllegalArgumentException("Missing explicitly selected annotation column for "+annotation.uid());}
    static boolean passesAnnotation(Annotation annotation,Filter filter){validateColumns(annotation,filter);return passes(annotation.cadd(),annotation.rdb(),annotation,filter);}
    private static boolean passes(double cadd,double rdb,Annotation a,Filter f) {
        if(f.minCadd()>0&&!(cadd>=f.minCadd())||!Double.isNaN(f.maxRdb())&&!(rdb<=f.maxRdb()))return false;
        if(!f.stateColumns().isEmpty()) {
            double state;
            if(f.stateMethod()==States.MODE){var counts=new TreeMap<Double,Integer>();for(String name:f.stateColumns()){double v=a.states().get(name);if(!Double.isNaN(v))counts.merge(v,1,Integer::sum);}state=Double.NaN;int count=0;for(var entry:counts.entrySet())if(entry.getValue()>count){state=entry.getKey();count=entry.getValue();}}
            else {state=f.stateMethod()==States.ANY?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY;for(String name:f.stateColumns())state=f.stateMethod()==States.ANY?Math.min(state,a.states().get(name)):Math.max(state,a.states().get(name));}
            if(!(state<=f.maxState()))return false;
        }
        if(!f.binaryColumns().isEmpty()) {
            double sum=0;for(String name:f.binaryColumns())sum+=a.binary().get(name);
            if(!(switch(f.binaryMethod()){case ANY->sum>0;case MAJORITY->sum/f.binaryColumns().size()>.5;case ALL->sum==f.binaryColumns().size();}))return false;
        }
        return true;
    }
    private static boolean cut(double value,double threshold){return Double.isNaN(threshold)||value<=threshold;}
    private static double min(double a,double b){return Double.isNaN(a)?b:Double.isNaN(b)?a:Math.min(a,b);}
    private static double max(double a,double b){return Double.isNaN(a)?b:Double.isNaN(b)?a:Math.max(a,b);}
    private static void budget(int a,int b,int maximum){if((long)a+b>maximum)throw new IllegalArgumentException("Mapping evidence budget exceeded; increase the explicit bound or reduce resources");}
    private static void token(String text){if(!GeneWindowMapping.token(text))throw new IllegalArgumentException("Require nonempty whitespace-free resource tokens");}
    private static void validateChromosome(int chr){if(chr<1||chr>23)throw new IllegalArgumentException("Supported explicit chromosomes are 1..23 (X=23); no assembly conversion is inferred");}
    private static void score(double v){if(!Double.isNaN(v)&&(!Double.isFinite(v)||v<0))throw new IllegalArgumentException("Scores require nonnegative finite values or NaN for missing");}
    private static void probability(double v){score(v);if(v>1)throw new IllegalArgumentException("Probability must be in [0,1] or NaN for missing");}
    private static List<String> distinct(List<String> values){var result=new LinkedHashSet<String>();for(String v:values){token(v);if(!result.add(v))throw new IllegalArgumentException("Duplicate token: "+v);}return List.copyOf(result);}
    private static Map<String,Double> values(Map<String,Double> input,boolean binary){var out=new LinkedHashMap<String,Double>();for(var e:input.entrySet()){token(e.getKey());double v=e.getValue();score(v);if(!Double.isNaN(v)&&(binary?v!=0&&v!=1:v!=Math.rint(v)||v<1||v>15))throw new IllegalArgumentException("Require "+(binary?"binary 0/1":"integer state 1..15")+" or NaN: "+e.getKey());out.put(e.getKey(),v);}return Map.copyOf(out);}
    private static <T> Map<String,T> unique(List<T> rows,java.util.function.Function<T,String> id,String kind){var result=new LinkedHashMap<String,T>();for(T row:rows)if(result.putIfAbsent(id.apply(row),row)!=null)throw new IllegalArgumentException("Duplicate "+kind+" ID: "+id.apply(row));return result;}
}
