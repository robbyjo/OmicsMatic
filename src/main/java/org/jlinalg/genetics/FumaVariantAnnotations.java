/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import jdistlib.HyperGeometric;

/**
 * Supplied-resource FUMA SNP annotation joins, nearest gene bodies, states and
 * reference-category statistics. No ANNOVAR consequence prediction, annotation
 * acquisition, LD estimation or complete hosted FUMA reconstruction is performed.
 */
public final class FumaVariantAnnotations {
    private FumaVariantAnnotations() { }

    /** One resource gene row. Only literal gene chromosome X maps to 23, as in SNPannot.R. */
    public record Gene(String id,String symbol,String chromosome,int start,int end) {
        public Gene {token(id);field(symbol);token(chromosome);if(start<1||end<start)throw new IllegalArgumentException("Require one-based closed gene coordinates");}
        /** Effective source chromosome; other labels are unchanged. */
        public String effectiveChromosome(){return chromosome.equals("X")?"23":chromosome;}
    }
    /** Candidate variant; NaN means unavailable GWAS P, not absence of association. */
    public record Variant(String uid,String chromosome,int position,double gwasP) {
        public Variant {token(uid);token(chromosome);if(position<1||!Double.isNaN(gwasP)&&(!Double.isFinite(gwasP)||gwasP<0||gwasP>1))throw new IllegalArgumentException("Invalid variant coordinate or GWAS P");}
    }
    /** Supplied annotation row. Nullable states represent NA; raw RDB labels are retained. */
    public record Annotation(String uid,double cadd,String rdb,List<Integer> states) {
        public Annotation {token(uid);field(rdb);if(!Double.isNaN(cadd)&&!Double.isFinite(cadd))throw new IllegalArgumentException("CADD must be supplied finite numeric or NA");states=Collections.unmodifiableList(new ArrayList<>(states));for(Integer state:states)if(state!=null&&(state<1||state>15))throw new IllegalArgumentException("Chromatin states must be integers1..15 or NA");}
    }
    /** Supplied ANNOVAR-derived row; missing gene/category are null and never predicted. */
    public record Consequence(String uid,String gene,String annotation) {
        public Consequence {token(uid);field(gene);field(annotation);}
    }
    /** An original LD row. Repeated rows count repeatedly; targets outside candidates remain valid. */
    public record Ld(String source,String target) {public Ld {token(source);token(target);}}
    /** A supplied reference-category row; its reported proportion is used independently of counts. */
    public record Reference(String annotation,long count,double proportion) {
        public Reference {token(annotation);if(count<0||!Double.isFinite(proportion)||proportion<0||proportion>1)throw new IllegalArgumentException("Invalid reference count/proportion");}
    }
    /** BED interval, zero-based half-open; no implicit chromosome conversion. */
    public record Region(String chromosome,int start,int end) {
        public Region {token(chromosome);if(start<0||end<=start)throw new IllegalArgumentException("Require nonempty zero-based half-open BED intervals");}
    }
    /** One selected track. Source regex filename-to-column conversion is explicit and audited. */
    public record Track(String id,List<Region> regions) {
        public Track {token(id);if(id.equals("NA")||id.indexOf(':')>=0)throw new IllegalArgumentException("Invalid selected BED resource ID");regions=List.copyOf(regions);}
        /** Source column label: slash replacement followed by the original unanchored regex. */
        public String column(){return id.replace('/','_').replaceFirst(".bed.gz","");}
        /** Source annot.bed dataset label. */
        public String dataset(){return id.replaceFirst(".bed.gz","");}
    }
    /** Bounds on each input family, all emitted evidence, work and Fisher support/population. */
    public record Limits(int maxRows,int maxEvidence,long maxOperations,long maxFisherStates,long maxReferencePopulation) {
        public Limits {if(maxRows<1||maxEvidence<1||maxOperations<1||maxFisherStates<1||maxReferencePopulation<1||maxReferencePopulation>Integer.MAX_VALUE)throw new IllegalArgumentException("Require positive bounded capacities");}
        /** Conservative development defaults; bounds do not reserve JVM heap. */
        public static Limits defaults(){return new Limits(1_000_000,2_000_000,100_000_000L,5_000_000L,100_000_000L);}
    }
    /** Nearest hit, retaining one-based original variant and gene resource row numbers. */
    public record Nearest(int variantRow,int geneRow,String uid,String gene,String symbol,int distance) { }
    /** Supplied row joined to first exact gene resource match, with category-count provenance. */
    public record ConsequenceResult(int sourceRow,Consequence source,String symbol,Variant variant,boolean firstVariantCategory,boolean referenceCategory) { }
    /** All original score/state rows, including unused and duplicate rows. */
    public record AnnotationDisposition(int sourceRow,String uid,Integer variantRow,boolean selected,String reason) { }
    /** Recounted original LD row totals; null means no source aggregate group, not zero. */
    public record IndependentCount(String uid,Long ldRows,Long gwasLdRows) { }
    /** An original query/subject BED overlap; duplicate intervals and output rows are retained. */
    public record BedHit(int variantRow,int regionRow,String uid,String track,String dataset,Region region) { }
    /** A selected track can have no original emitted column when its hit population is empty. */
    public record TrackResult(String id,String column,String dataset,long hits,boolean sourceColumnWritten) { }
    /** Every reference category, including zero counts and explicitly undefined enrichment. */
    public record Category(Reference reference,long count,double proportion,double enrichment,double fisherP,
            long otherSelected,long categoryUnselected,long otherUnselected) { }
    /** Candidate-order summary; null labels/states and NaN CADD retain missingness. */
    public record VariantResult(Variant variant,List<Nearest> nearest,String function,double cadd,String rdb,
            Integer minimumState,Integer commonState,Map<String,Integer> bedFlags) {
        public VariantResult {nearest=List.copyOf(nearest);bedFlags=Collections.unmodifiableMap(new LinkedHashMap<>(bedFlags));}
    }
    /** Complete immutable native result with all resource, population and scientific review warnings. */
    public record Result(List<VariantResult> variants,List<ConsequenceResult> consequences,List<IndependentCount> independent,
            List<AnnotationDisposition> annotationRows,List<Nearest> nearest,List<BedHit> bedHits,List<TrackResult> tracks,
            List<Category> categories,List<String> warnings) {
        public Result {variants=List.copyOf(variants);consequences=List.copyOf(consequences);independent=List.copyOf(independent);annotationRows=List.copyOf(annotationRows);nearest=List.copyOf(nearest);bedHits=List.copyOf(bedHits);tracks=List.copyOf(tracks);categories=List.copyOf(categories);warnings=List.copyOf(warnings);}
    }

    /**
     * Annotate complete supplied inputs. Exact candidate IDs must be unique;
     * annotation rows match first, gene symbol joins match first, and all gene
     * rows remain eligible for nearest ties. Every requested population is either
     * complete or rejected; no rows/models are truncated to fit capacities.
     * @param variants candidate rows in requested output order
     * @param genes full gene resource, including explicitly retained repeated IDs
     * @param annotations supplied CADD/RDB/state rows; extra and duplicate IDs are audited
     * @param stateColumns ordered declared state labels corresponding to each annotation
     * @param consequences selected supplied consequences; unknown candidate IDs are rejected
     * @param ld original LD rows, including repeated/outside-target rows
     * @param independent ordered distinct independent IDs for source recounting
     * @param references ordered distinct reference-category counts and supplied proportions
     * @param tracks ordered selected BED tracks; zero-hit tracks have explicit omitted-column status
     * @param limits explicit input, evidence, operation and Fisher bounds
     * @param check cancellation/deadline callback; errors abort without returning a partial result
     * @return complete supplied-resource annotation and scientific audit ledgers
     */
    public static Result annotate(List<Variant> variants,List<Gene> genes,List<Annotation> annotations,List<String> stateColumns,
            List<Consequence> consequences,List<Ld> ld,List<String> independent,List<Reference> references,List<Track> tracks,
            Limits limits,Runnable check) {
        Objects.requireNonNull(check,"check");var work=new Work(limits,check);work.check();
        for(var input:List.of(variants,genes,annotations,consequences,ld,independent,references,tracks))if(input.size()>limits.maxRows())throw new IllegalArgumentException("Complete annotation input family exceeds maxRows");
        if(variants.isEmpty()||genes.isEmpty()||annotations.isEmpty()||consequences.isEmpty()||ld.isEmpty()||independent.isEmpty()||references.isEmpty())throw new IllegalArgumentException("Require nonempty candidates, genes, annotations, consequences, LD, independent IDs and reference categories; original empty aggregates do not define an admitted analysis");
        distinct(stateColumns,"state label");if(stateColumns.isEmpty())throw new IllegalArgumentException("Require explicit chromatin-state columns");for(String label:stateColumns)if(List.of("uniqID","CADD","RDB").contains(label))throw new IllegalArgumentException("State column conflicts with a score/identity role: "+label);
        var warnings=new ArrayList<String>();warnings.add("SUPPLIED SNP ANNOTATION ONLY: no ANNOVAR prediction, dbSNP/allele normalization, annotation download or LD estimation. Required action: verify exact assembly, namespaces, annotation releases, reference population and all row/fit/join ledgers before interpretation.");
        var byVariant=new LinkedHashMap<String,Integer>();for(int i=0;i<variants.size();i++){work.work(1);if(byVariant.putIfAbsent(variants.get(i).uid(),i)!=null)throw new IllegalArgumentException("Duplicate candidate UID: "+variants.get(i).uid());}
        var byGene=new LinkedHashMap<String,Integer>();int duplicateGenes=0;for(int i=0;i<genes.size();i++){work.work(1);if(byGene.putIfAbsent(genes.get(i).id(),i)!=null)duplicateGenes++;}
        if(duplicateGenes>0)warnings.add("Repeated gene resource IDs: "+duplicateGenes+" later rows. Symbol joins use the first exact match; every resource row remains in nearest-gene ties. Required action: inspect full row provenance; no deduplication is inferred.");
        var byAnnotation=new LinkedHashMap<String,Integer>();var dispositions=new ArrayList<AnnotationDisposition>();int duplicates=0,unused=0;
        for(int i=0;i<annotations.size();i++){work.work(1);var a=annotations.get(i);if(a.states().size()!=stateColumns.size())throw new IllegalArgumentException("Chromatin-state width differs from declared labels for "+a.uid());Integer candidate=byVariant.get(a.uid());boolean first=byAnnotation.putIfAbsent(a.uid(),i)==null,selected=first&&candidate!=null;if(!first)duplicates++;if(candidate==null)unused++;work.evidence(1);dispositions.add(new AnnotationDisposition(i+1,a.uid(),candidate==null?null:candidate+1,selected,candidate==null?"outside-candidates":first?"first-exact-match":"later-duplicate"));}
        if(duplicates>0||unused>0)warnings.add("Score/state resource has "+duplicates+" later duplicate and "+unused+" outside-candidate rows. First exact UID match is used. Required action: inspect annotation disposition/source rows; unused rows are not candidate tests.");
        for(var v:variants)if(!byAnnotation.containsKey(v.uid()))throw new IllegalArgumentException("Missing annotation row for candidate "+v.uid()+"; no score/state row is imputed");
        if(stateColumns.size()==1)warnings.add("SOURCE DIMENSION DIFFERENCE: original SNPannot.R drops a single state column before apply() and fails. Native code keeps the explicit one-column matrix. Required action: retain this declared shape difference; numeric state semantics are unchanged.");
        var referenceMap=new LinkedHashMap<String,Reference>();long population=0;double suppliedProportions=0;
        for(var r:references){work.work(1);if(referenceMap.putIfAbsent(r.annotation(),r)!=null)throw new IllegalArgumentException("Duplicate reference category: "+r.annotation());if(r.count()>limits.maxReferencePopulation()-population)throw new IllegalArgumentException("Complete reference population exceeds maxReferencePopulation at "+r.annotation());population+=r.count();suppliedProportions+=r.proportion();}
        if(population<1||population>limits.maxReferencePopulation())throw new IllegalArgumentException("Reference annotation population must be positive and within maxReferencePopulation; a zero background is not admitted inference");
        if(Math.abs(suppliedProportions-1)>1e-10)warnings.add("Supplied reference proportions sum to "+suppliedProportions+"; source enrichment uses each supplied proportion, not recomputed count fractions. Required action: verify the reference edition and denominator; values are not repaired.");
        var perVariant=new HashMap<String,LinkedHashSet<String>>();var counted=new HashSet<Pair>();var counts=new HashMap<String,Long>();var joined=new ArrayList<ConsequenceResult>();int outsideCategories=0;
        for(int i=0;i<consequences.size();i++){
            work.work(1);var c=consequences.get(i);Integer candidate=byVariant.get(c.uid());if(candidate==null)throw new IllegalArgumentException("Consequence references an unknown candidate UID: "+c.uid()+"; reject source rows whose coordinates/provenance would be missing");
            Integer gene=c.gene()==null?null:byGene.get(c.gene());String symbol=gene==null?c.gene():genes.get(gene).symbol();if(symbol==null)symbol=c.gene();
            perVariant.computeIfAbsent(c.uid(),k->new LinkedHashSet<>()).add(c.annotation()==null?"NA":c.annotation());boolean first=c.annotation()!=null&&counted.add(new Pair(c.uid(),c.annotation()));boolean inReference=c.annotation()!=null&&referenceMap.containsKey(c.annotation());if(first&&inReference)counts.merge(c.annotation(),1L,Long::sum);if(first&&!inReference)outsideCategories++;work.evidence(1);joined.add(new ConsequenceResult(i+1,c,symbol,variants.get(candidate),first,inReference));
        }
        if(outsideCategories>0)warnings.add("Reference-category table excludes "+outsideCategories+" unique variant/category pairs; missing categories also do not enter its denominator. Required action: inspect complete consequence rows and verify reference coverage; no absent category is invented.");
        long selected=counts.values().stream().mapToLong(Long::longValue).sum();var categories=new ArrayList<Category>();
        if(selected==0)warnings.add("No selected annotation categories occur in the reference table. Source proportions/enrichment are undefined, with degenerate Fisher P=1. Required action: review category coverage; this is not evidence of no functional enrichment.");
        for(var r:references){
            work.work(1);long c=counts.getOrDefault(r.annotation(),0L),otherSelected=selected-c,unselected=r.count()-c,otherUnselected=population-selected-unselected;if(unselected<0||otherUnselected<0)throw new IllegalArgumentException("Selected annotation-category counts are not a subset of reference population for "+r.annotation()+"; original Fisher table would contain negative cells");
            long low=Math.max(0,selected-(population-r.count())),high=Math.min(selected,r.count()),support=high-low+1;work.fisher(support);double p=fisher(c,r.count(),population,selected,low,support,work);double proportion=(double)c/selected,enrichment=proportion/r.proportion();work.evidence(1);categories.add(new Category(r,c,proportion,enrichment,p,otherSelected,unselected,otherUnselected));
        }
        warnings.add("ANNOTATION STATISTICS: counts deduplicate variant/category pairs, not unique variants across categories. Fisher tests are two-sided fixed-margin tests with no source multiple-testing correction. Required action: verify subset admissibility and overlapping annotation-category populations; supplied count tables do not prove per-variant reference membership.");
        if(categories.stream().anyMatch(c->c.fisherP()==0))warnings.add("FISHER DOUBLE UNDERFLOW: source-style double P is zero for at least one category. This is a numerical boundary, not an exact zero probability. Required action: preserve exact counts and source P; obtain a separately validated log-tail analysis if finite log probabilities are needed.");
        if(categories.stream().anyMatch(c->!Double.isFinite(c.enrichment())))warnings.add("UNDEFINED/INFINITE ENRICHMENT: zero supplied reference proportions and/or an empty selected-category denominator produce NA or Inf. Required action: inspect the supplied proportions and all selected/reference counts; these entries are not finite enrichment estimates and are not replaced by zero.");
        distinct(independent,"independent UID");var ldCounts=new HashMap<String,Long>();var gwasCounts=new HashMap<String,Long>();
        for(var edge:ld){work.work(1);ldCounts.merge(edge.source(),1L,Long::sum);Integer target=byVariant.get(edge.target());if(target!=null&&!Double.isNaN(variants.get(target).gwasP()))gwasCounts.merge(edge.source(),1L,Long::sum);}
        if(gwasCounts.isEmpty())throw new IllegalArgumentException("No LD rows target a candidate with a supplied GWAS P; original GWAS recount aggregate is empty");
        var independentRows=new ArrayList<IndependentCount>();for(String uid:independent){work.evidence(1);independentRows.add(new IndependentCount(uid,ldCounts.get(uid),gwasCounts.get(uid)));}
        warnings.add("LD RECOUNT: repeated original LD rows count repeatedly; targets absent from candidates do not count as GWAS-observed. An absent source group remains NA, not zero. Required action: inspect source LD rows and independent counts; no new LD or unique-variant denominator is estimated.");
        var geneRanges=new ArrayList<Range>();for(int i=0;i<genes.size();i++){work.work(1);var g=genes.get(i);geneRanges.add(new Range(i,g.effectiveChromosome(),g.start(),g.end()));}var geneIndex=new Index(geneRanges,work);var nearest=new ArrayList<Nearest>();var nearestByVariant=new ArrayList<List<Nearest>>();
        for(int i=0;i<variants.size();i++){work.work(1);var v=variants.get(i);var matches=geneIndex.nearest(v.chromosome(),v.position(),work);var current=new ArrayList<Nearest>();for(int gene:matches){var g=genes.get(gene);int distance=(int)Math.max(0,Math.max((long)g.start()-v.position()-1,(long)v.position()-g.end()-1));work.evidence(1);var hit=new Nearest(i+1,gene+1,v.uid(),g.id(),g.symbol(),distance);current.add(hit);nearest.add(hit);}nearestByVariant.add(current);}
        warnings.add("NEAREST GENE BODIES: exact-contig unstranded intervals, all minimum-gap ties in original gene-row order. Adjacent and overlapping genes have distance zero and can both be retained. Only literal gene chromosome X converts to23. Required action: inspect every tied resource row; proximity is not causal evidence or TSS mapping.");
        var flags=new ArrayList<Map<String,Integer>>();for(int i=0;i<variants.size();i++)flags.add(new LinkedHashMap<>());var bedHits=new ArrayList<BedHit>();var trackResults=new ArrayList<TrackResult>();var trackIds=new HashSet<String>();var columns=new HashSet<String>(List.of("uniqID","CADD","RDB"));columns.addAll(stateColumns);long bedRows=0;
        for(var track:tracks){
            work.work(1);if(!trackIds.add(track.id())||track.column().isEmpty()||!columns.add(track.column()))throw new IllegalArgumentException("Duplicate or colliding source BED track column: "+track.id());bedRows+=track.regions().size();if(bedRows>limits.maxRows())throw new IllegalArgumentException("Complete BED resource population exceeds maxRows");var ranges=new ArrayList<Range>();for(int i=0;i<track.regions().size();i++){work.work(1);var b=track.regions().get(i);ranges.add(new Range(i,b.chromosome(),b.start()+1,b.end()));}var index=new Index(ranges,work);var hitCandidates=new HashSet<Integer>();long before=bedHits.size();
            for(int i=0;i<variants.size();i++){var v=variants.get(i);for(int region:index.overlaps(v.chromosome(),v.position(),v.position(),work)){work.evidence(1);bedHits.add(new BedHit(i+1,region+1,v.uid(),track.id(),track.dataset(),track.regions().get(region)));hitCandidates.add(i);}}
            long n=bedHits.size()-before;trackResults.add(new TrackResult(track.id(),track.column(),track.dataset(),n,n>0));if(n>0)for(int i=0;i<variants.size();i++){work.work(1);flags.get(i).put(track.column(),hitCandidates.contains(i)?1:0);}
        }
        if(!tracks.isEmpty())warnings.add("BED TRACKS: zero-based half-open intervals become one-based closed; exact contig strings and source query/subject row order are retained. Duplicate hit intervals stay repeated. Source omits a zero-hit track column; native track status records it explicitly. Required action: inspect all selected tracks/hits and omitted-column statuses.");
        var results=new ArrayList<VariantResult>();for(int i=0;i<variants.size();i++){
            work.work(1);var v=variants.get(i);var a=annotations.get(byAnnotation.get(v.uid()));var frequency=new TreeMap<Integer,Integer>();Integer minimum=null;boolean missing=false;for(Integer state:a.states()){work.work(1);if(state==null)missing=true;else{minimum=minimum==null?state:Math.min(minimum,state);frequency.merge(state,1,Integer::sum);}}
            if(frequency.isEmpty())throw new IllegalArgumentException("All chromatin states are NA for "+v.uid()+"; original source mode is undefined and can fail output serialization");Integer common=null;int highest=0;for(var entry:frequency.entrySet())if(entry.getValue()>highest){highest=entry.getValue();common=entry.getKey();}var labels=perVariant.get(v.uid());String function=labels==null?null:String.join(":",labels);work.evidence(1);results.add(new VariantResult(v,nearestByVariant.get(i),function,a.cadd(),a.rdb()==null||a.rdb().isEmpty()?null:a.rdb(),missing?null:minimum,common,flags.get(i)));
        }
        if(results.stream().anyMatch(r->Double.isNaN(r.cadd())||r.rdb()==null||r.minimumState()==null))warnings.add("MISSING SUPPLIED ANNOTATIONS: scores and state minima retain NA. A single missing state makes the minimum NA; the mode excludes missing states and breaks numeric ties toward the smaller state. Required action: inspect the exact resource row and missingness; no scores or states are imputed.");
        if(results.stream().anyMatch(r->r.nearest().isEmpty()))warnings.add("MISSING NEAREST RESOURCE CONTIG: some candidates have no same-contig gene row and retain missing nearest-gene output. Required action: verify exact contig encoding and assembly coverage; missing resource coverage is not evidence that a candidate has no relevant gene.");
        work.check();return new Result(results,joined,independentRows,dispositions,nearest,bedHits,trackResults,categories,warnings);
    }

    // R's central fixed-margin minimum-likelihood tail; no odds-ratio/CI fitting.
    private static double fisher(long observed,long category,long population,long selected,long low,long support,Work work) {
        double[] probabilities=new double[Math.toIntExact(support)];double maximum=Double.NEGATIVE_INFINITY;
        for(int i=0;i<probabilities.length;i++){if((i&4095)==0)work.check();double log=HyperGeometric.density(low+i,category,population-category,selected,true);if(Double.isNaN(log)||log==Double.POSITIVE_INFINITY)throw new ArithmeticException("Invalid central hypergeometric log density");probabilities[i]=log;maximum=Math.max(maximum,log);}
        if(!Double.isFinite(maximum))throw new ArithmeticException("No finite central Fisher support");double total=0,correction=0;
        for(int i=0;i<probabilities.length;i++){if((i&4095)==0)work.check();double weight=Math.exp(probabilities[i]-maximum);probabilities[i]=weight;double y=weight-correction,next=total+y;correction=(next-total)-y;total=next;}
        double threshold=probabilities[Math.toIntExact(observed-low)]/total*(1+1e-7),tail=0;correction=0;
        for(int i=0;i<probabilities.length;i++){if((i&4095)==0)work.check();double p=probabilities[i]/total;if(p<=threshold){double y=p-correction,next=tail+y;correction=(next-tail)-y;tail=next;}}
        return Math.min(1,tail);
    }
    private record Pair(String uid,String annotation) { }
    private record Range(int row,String chromosome,int start,int end) { }
    private record Node(Range range,int maximumEnd,Node left,Node right) { }
    private static final class Index {
        private final Map<String,List<Range>> starts=new HashMap<>(),ends=new HashMap<>();
        private final Map<String,Node> roots=new HashMap<>();
        Index(List<Range> ranges,Work work){for(var r:ranges){work.work(1);starts.computeIfAbsent(r.chromosome(),k->new ArrayList<>()).add(r);}for(var e:starts.entrySet()){e.getValue().sort((a,b)->{work.work(1);int c=Integer.compare(a.start(),b.start());return c==0?Integer.compare(a.row(),b.row()):c;});var end=new ArrayList<>(e.getValue());end.sort((a,b)->{work.work(1);int c=Integer.compare(a.end(),b.end());return c==0?Integer.compare(a.row(),b.row()):c;});ends.put(e.getKey(),end);roots.put(e.getKey(),build(e.getValue(),0,e.getValue().size(),work));}}
        private static Node build(List<Range> rows,int first,int last,Work work){if(first==last)return null;work.work(1);int mid=first+(last-first)/2;var left=build(rows,first,mid,work);var right=build(rows,mid+1,last,work);int maximum=rows.get(mid).end();if(left!=null)maximum=Math.max(maximum,left.maximumEnd());if(right!=null)maximum=Math.max(maximum,right.maximumEnd());return new Node(rows.get(mid),maximum,left,right);}
        List<Integer> overlaps(String chromosome,long start,long end,Work work){var result=new ArrayList<Integer>();search(roots.get(chromosome),start,end,result,work);result.sort((a,b)->{work.work(1);return Integer.compare(a,b);});return result;}
        private static void search(Node n,long start,long end,List<Integer> result,Work work){if(n==null)return;work.work(1);if(n.maximumEnd()<start)return;search(n.left(),start,end,result,work);if(n.range().start()>end)return;if(n.range().end()>=start){work.temporary(result.size()+1);result.add(n.range().row());}search(n.right(),start,end,result,work);}
        List<Integer> nearest(String chromosome,int position,Work work){var hits=overlaps(chromosome,(long)position-1,(long)position+1,work);if(!hits.isEmpty()||!starts.containsKey(chromosome))return hits;var s=starts.get(chromosome);var e=ends.get(chromosome);int left=upper(e,position,false,work)-1,right=upper(s,position,true,work);long dl=left<0?Long.MAX_VALUE:(long)position-e.get(left).end()-1,dr=right==s.size()?Long.MAX_VALUE:(long)s.get(right).start()-position-1;long distance=Math.min(dl,dr);var result=new ArrayList<Integer>();if(dl==distance){int boundary=e.get(left).end();while(left>=0&&e.get(left).end()==boundary){work.work(1);work.temporary(result.size()+1);result.add(e.get(left--).row());}}if(dr==distance){int boundary=s.get(right).start();while(right<s.size()&&s.get(right).start()==boundary){work.work(1);work.temporary(result.size()+1);result.add(s.get(right++).row());}}result.sort((a,b)->{work.work(1);return Integer.compare(a,b);});return result;}
        private static int upper(List<Range> list,int position,boolean start,Work work){int lo=0,hi=list.size();while(lo<hi){work.work(1);int mid=lo+(hi-lo)/2;int value=start?list.get(mid).start():list.get(mid).end();if(value<=position)lo=mid+1;else hi=mid;}return lo;}
    }
    private static final class Work {
        final Limits limits;final Runnable callback;long operations,evidence,fisher;
        Work(Limits limits,Runnable callback){this.limits=Objects.requireNonNull(limits,"limits");this.callback=callback;}
        void check(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("SNP annotation interrupted");callback.run();}
        void work(long n){if(n>limits.maxOperations()-operations)throw new IllegalArgumentException("Complete SNP annotation work exceeds maxOperations");long before=operations;operations+=n;if((before>>>12)!=(operations>>>12))check();}
        void evidence(long n){if(n>limits.maxEvidence()-evidence)throw new IllegalArgumentException("Complete SNP annotation evidence exceeds maxEvidence; no rows are truncated");evidence+=n;}
        void temporary(int n){if(n>limits.maxEvidence()-evidence)throw new IllegalArgumentException("Complete interval-hit population exceeds remaining maxEvidence");}
        void fisher(long n){if(n>limits.maxFisherStates()-fisher)throw new IllegalArgumentException("Complete Fisher support exceeds maxFisherStates");fisher+=n;work(Math.multiplyExact(n,4));check();}
    }
    private static void distinct(List<String> values,String label){var seen=new HashSet<String>();for(String value:values){token(value);if(!seen.add(value))throw new IllegalArgumentException("Duplicate "+label+": "+value);}}
    private static void token(String value){if(value==null||value.isEmpty()||value.codePoints().anyMatch(Character::isWhitespace)||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require a nonempty token without whitespace/control characters");}
    private static void field(String value){if(value!=null&&(value.indexOf('\t')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0))throw new IllegalArgumentException("Invalid annotation field");}
}
