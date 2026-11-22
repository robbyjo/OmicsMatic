/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Declared lead-to-known distance comparisons for locus novelty review.
 * This implements a supplied-reference rule, not FUMA's LD locus discovery or
 * its exact-position GWAS Catalog annotation. No catalog, trait equivalence,
 * coordinate conversion, allele match or historical default is inferred.
 */
public final class FumaLocusNovelty {
    private FumaLocusNovelty() { }
    /** Exact equality at the supplied radius is included or excluded explicitly. */
    public enum Boundary { INCLUSIVE, STRICT }
    /** Compare literal trait IDs, or explicitly consider every supplied known trait. */
    public enum TraitPolicy { EXACT, ALL_KNOWN }
    /** Absence is meaningful only relative to an explicitly covered reference window. */
    public enum Status { KNOWN_WITHIN_WINDOW, NOVEL_RELATIVE_TO_REFERENCE, UNASSESSABLE_REFERENCE_COVERAGE }
    /** One lead per locus and trait; chromosome and trait strings are exact identities. */
    public record Lead(String locus, String trait, String chromosome, int position) {
        public Lead {text(locus);text(trait);coordinate(chromosome,position);}
    }
    /** One reported association; repeated variant IDs and positions retain distinct source rows. */
    public record Known(String variant, String trait, String chromosome, int position) {
        public Known {text(variant);text(trait);coordinate(chromosome,position);}
    }
    /**
     * Investigator-declared completeness of the selected reference, one-based closed.
     * Under ALL_KNOWN, trait must be literal "*" and covers all selected traits.
     * This is a provenance assertion, not a biological completeness certification.
     */
    public record Coverage(String trait, String chromosome, int start, int end) {
        public Coverage {text(trait);coordinate(chromosome,start);if(end<start)throw new IllegalArgumentException("Reversed reference coverage interval");}
    }
    /** Identified catalog snapshot and exact assembly, retaining all supplied source rows. */
    public record Reference(String identity, String build, List<Known> variants, List<Coverage> coverage) {
        public Reference {text(identity);text(build);variants=List.copyOf(variants);coverage=List.copyOf(coverage);}
    }
    /** Bounds apply to combined input rows, complete emitted matches and measured index/query work. */
    public record Limits(int maximumInputRows, int maximumMatches, long maximumOperations) {
        public Limits {if(maximumInputRows<1||maximumMatches<0||maximumOperations<1)throw new IllegalArgumentException("Require positive input/work and nonnegative match budgets");}
        /** Conservative capacities; exceeding a bound rejects rather than truncates matches. */
        public static Limits defaults(){return new Limits(1_000_000,2_000_000,100_000_000L);}
    }
    /** Explicit positive radius and identity/matching choices; no build alias or trait mapping occurs. */
    public record Options(String build, int radiusBp, Boundary boundary, TraitPolicy traitPolicy, Limits limits) {
        public Options {text(build);if(radiusBp<1||boundary==null||traitPolicy==null||limits==null)throw new IllegalArgumentException("Declare positive radius, boundary, trait policy and limits");}
    }
    /** One complete nearby match; row numbers are one-based positions in the supplied lists. */
    public record Match(int leadRow, int knownRow, Lead lead, Known known, long distanceBp) { }
    /**
     * One summary per lead, in input order. Effective window bounds are inclusive
     * integer coordinates after applying the explicit boundary, clipped to [1, Integer.MAX_VALUE].
     * A known match is evidence even when the reference window is incompletely covered.
     */
    public record Summary(int leadRow, Lead lead, int windowStart, int windowEnd,
            boolean referenceWindowCovered, int knownMatches, Long nearestDistanceBp, Status status) { }
    /** Immutable complete summaries and matches; matches use lead order then coordinate/source-row order. */
    public record Result(List<Summary> summaries, List<Match> matches, List<String> warnings, long operations) {
        public Result {summaries=List.copyOf(summaries);matches=List.copyOf(matches);warnings=List.copyOf(warnings);}
    }
    private record Group(String trait,String chromosome) { }
    private record LeadKey(String trait,String locus) { }
    private record Indexed(int row,Known value) { }
    private record Span(int start,int end) { }
    private static final class Work {
        private final long maximum;
        private final Runnable cancellation;
        private long count;
        Work(long maximum,Runnable cancellation){this.maximum=maximum;this.cancellation=cancellation;}
        void tick(){cancellation.run();if(count>=maximum)throw new IllegalArgumentException("Locus novelty operation budget exceeded; no matches were truncated");count++;}
    }
    /**
     * Compare supplied leads with all selected nearby known rows and assess reference coverage.
     * Coverage intervals may overlap or abut; every discrete coordinate in a query
     * window must be covered before an empty match set can be called novel relative
     * to this reference. Missing reference coverage yields an unassessable summary.
     *
     * @param leads nonempty lead population; locus IDs are unique within each exact trait
     * @param reference identified, assembly-declared known associations and coverage assertions
     * @param options required radius, boundary, trait and capacity choices
     * @param cancellation cooperative check invoked during indexing, sorting and queries
     * @return complete row-numbered evidence and summaries; no partial result on failure
     */
    public static Result compare(List<Lead> leads,Reference reference,Options options,Runnable cancellation) {
        if(leads==null||leads.isEmpty()||reference==null||options==null||cancellation==null)throw new IllegalArgumentException("Require nonempty leads, reference, options and cancellation check");
        if(!options.build().equals(reference.build()))throw new IllegalArgumentException("Lead and known-reference builds differ; explicit coordinate harmonization is required");
        Limits limits=options.limits();long inputRows=(long)leads.size()+reference.variants().size()+reference.coverage().size();
        if(inputRows>limits.maximumInputRows())throw new IllegalArgumentException("Combined locus novelty input row budget exceeded");
        Work work=new Work(limits.maximumOperations(),cancellation);
        Map<Group,List<Indexed>> known=new HashMap<>();int row=0;
        for(Known variant:reference.variants()) {
            work.tick();known.computeIfAbsent(group(variant.trait(),variant.chromosome(),options.traitPolicy()),ignored->new ArrayList<>()).add(new Indexed(++row,variant));
        }
        Comparator<Indexed> order=(a,b)->{work.tick();int c=Integer.compare(a.value().position(),b.value().position());return c==0?Integer.compare(a.row(),b.row()):c;};
        for(List<Indexed> values:known.values())values.sort(order);
        Map<Group,List<Span>> coverage=new HashMap<>();
        for(Coverage interval:reference.coverage()) {
            work.tick();if(options.traitPolicy()==TraitPolicy.ALL_KNOWN&&!interval.trait().equals("*"))throw new IllegalArgumentException("ALL_KNOWN reference coverage must explicitly use trait *");
            coverage.computeIfAbsent(group(interval.trait(),interval.chromosome(),options.traitPolicy()),ignored->new ArrayList<>()).add(new Span(interval.start(),interval.end()));
        }
        for(var entry:coverage.entrySet()) {
            entry.getValue().sort((a,b)->{work.tick();int c=Integer.compare(a.start(),b.start());return c==0?Integer.compare(a.end(),b.end()):c;});
            var merged=new ArrayList<Span>();
            for(Span span:entry.getValue()) {
                work.tick();if(!merged.isEmpty()&&(long)span.start()<=1L+merged.get(merged.size()-1).end()) {
                    Span prior=merged.remove(merged.size()-1);merged.add(new Span(prior.start(),Math.max(prior.end(),span.end())));
                }else merged.add(span);
            }
            entry.setValue(merged);
        }
        var summaries=new ArrayList<Summary>();var matches=new ArrayList<Match>();var unique=new HashSet<LeadKey>();row=0;int unassessable=0;
        for(Lead lead:leads) {
            work.tick();if(lead==null)throw new IllegalArgumentException("Null lead row");
            if(!unique.add(new LeadKey(lead.trait(),lead.locus())))throw new IllegalArgumentException("Duplicate locus ID within the same trait");
            row++;long distance=options.radiusBp()-(options.boundary()==Boundary.STRICT?1L:0L);
            int start=(int)Math.max(1L,(long)lead.position()-distance),end=(int)Math.min(Integer.MAX_VALUE,(long)lead.position()+distance);
            Group key=group(lead.trait(),lead.chromosome(),options.traitPolicy());List<Indexed> values=known.getOrDefault(key,List.of());
            int first=lower(values,start,work),count=0;Long nearest=null;
            for(int i=first;i<values.size();i++) {
                work.tick();Indexed hit=values.get(i);if(hit.value().position()>end)break;
                if(matches.size()>=limits.maximumMatches())throw new IllegalArgumentException("Complete locus novelty match budget exceeded; no matches were truncated");
                long separation=Math.abs((long)lead.position()-hit.value().position());matches.add(new Match(row,hit.row(),lead,hit.value(),separation));count++;nearest=nearest==null?separation:Math.min(nearest,separation);
            }
            boolean covered=covered(coverage.getOrDefault(key,List.of()),start,end,work);
            Status status=count>0?Status.KNOWN_WITHIN_WINDOW:covered?Status.NOVEL_RELATIVE_TO_REFERENCE:Status.UNASSESSABLE_REFERENCE_COVERAGE;
            if(status==Status.UNASSESSABLE_REFERENCE_COVERAGE)unassessable++;
            summaries.add(new Summary(row,lead,start,end,covered,count,nearest,status));
        }
        var warnings=new ArrayList<String>();
        warnings.add("REFERENCE-RELATIVE NOVELTY: no nearby supplied match proves absence only within the explicitly covered catalog snapshot and declared trait policy. Required action: review catalog release, build, coverage assertions, radius/boundary and every match before interpreting novelty; no causal, allele, LD or historical FUMA equivalence is inferred.");
        if(unassessable>0)warnings.add("UNASSESSABLE REFERENCE COVERAGE: "+unassessable+" empty-match windows lack complete declared reference coverage. Required action: supply and document the required reference population/coverage or retain these rows as unassessable; never relabel them novel.");
        return new Result(summaries,matches,warnings,work.count);
    }
    private static Group group(String trait,String chromosome,TraitPolicy policy){return new Group(policy==TraitPolicy.ALL_KNOWN?"*":trait,chromosome);}
    private static int lower(List<Indexed> values,int start,Work work) {
        int left=0,right=values.size();while(left<right){work.tick();int middle=left+(right-left)/2;if(values.get(middle).value().position()<start)left=middle+1;else right=middle;}return left;
    }
    private static boolean covered(List<Span> spans,int start,int end,Work work) {
        int left=0,right=spans.size();while(left<right){work.tick();int middle=left+(right-left)/2;if(spans.get(middle).start()<=start)left=middle+1;else right=middle;}return left>0&&spans.get(left-1).end()>=end;
    }
    private static void coordinate(String chromosome,int position){text(chromosome);if(position<1)throw new IllegalArgumentException("Require positive one-based coordinates");}
    private static void text(String value){if(value==null||value.isBlank()||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require nonblank control-free exact identities");}
}
