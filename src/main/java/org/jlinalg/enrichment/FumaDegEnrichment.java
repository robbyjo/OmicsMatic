/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Independent Java implementation of the inspected FUMA GENE2FUNC DEG-set test.
 * Inputs are already resolved gene IDs and supplied precomputed DEG memberships;
 * this does not generate DEGs, transform expression, fit MAGMA or contact FUMA.
 * The source intersects the background with the rows named exactly "all", but
 * keeps the full query size. Source multiplicity and outside-background draws
 * require explicit policies and are reported separately from probability values.
 */
public final class FumaDegEnrichment {
    private FumaDegEnrichment() { }

    /** Original output order and literal, unanchored category matching. */
    public enum Category {
        UP("DEG.up", ".up"), DOWN("DEG.down", ".down"), TWO_SIDED("DEG.twoside", ".twoside");
        private final String sourceName, marker;
        Category(String sourceName, String marker) { this.sourceName=sourceName;this.marker=marker; }
        /** Exact original Category column. */
        public String sourceName() { return sourceName; }
    }
    /** Reject source multiplicity or retain it with explicit diagnostics. */
    public enum Multiplicity { ERROR, RETAIN_SOURCE }
    /** Never silently intersect the original query with the expressed background. */
    public enum OutsideQuery { ERROR, RETAIN_SOURCE_SIZE }

    /** Source row; declared size is retained but the source recomputes test size. */
    public record DegSet(String name, long declaredSize, List<String> members) {
        public DegSet {
            FumaDegEnrichment.name(name);if(declaredSize<0||members==null)throw new IllegalArgumentException("Invalid DEG source row");
            members=List.copyOf(members);
        }
    }
    /** Caller-owned population/work bounds, without claims of a JVM heap quota. */
    public record Limits(int maximumGenes, int maximumRows, long maximumMembers) {
        public Limits {
            if(maximumGenes<1||maximumGenes>10000000||maximumRows<1||maximumRows>1000000
                    ||maximumMembers<1||maximumMembers>100000000)throw new IllegalArgumentException("Invalid DEG population capacities");
        }
    }
    /** Caller interruption, deadline or immutable-resource check. */
    @FunctionalInterface public interface Check { void run(); }

    /**
     * Complete source-order row. P and adjusted P follow direct double arithmetic;
     * stable native logs remain separate when double P underflows. A false
     * finitePopulationModel flag means source membership/multiplicity cannot
     * describe sampling unique genes from the declared universe.
     */
    public record Row(Category category, int sourceRow, String sourceName, String label,
                      int backgroundSize, int querySize, long declaredSize, int inputMembers,
                      int setSize, int duplicateSetMembers, int overlap, List<String> overlapGenes,
                      int familyTests, double pValue, double logPValue,
                      double adjustedPValue, double logAdjustedPValue,
                      boolean finitePopulationModel, boolean underflowedP) {
        public Row { overlapGenes=List.copyOf(overlapGenes); }
    }
    /** All three complete correction families and the exact retained input populations. */
    public record Result(List<Row> rows, List<String> expressedBackground,
                         List<String> queryOutsideExpressedBackground,
                         int duplicateQueryEntries, int duplicateBackgroundEntries,
                         long sourceRows, long sourceMembers, int allRows) {
        public Result {
            rows=List.copyOf(rows);expressedBackground=List.copyOf(expressedBackground);
            queryOutsideExpressedBackground=List.copyOf(queryOutsideExpressedBackground);
        }
    }
    private record Tested(int sourceRow, DegSet source, int size, int duplicates,
                          List<String> overlap, double p, double logP, boolean valid) { }

    /**
     * Test supplied DEG sets with one-sided enrichment and separate Bonferroni
     * corrections for up, down and two-sided families, as the original caller.
     * Empty and duplicate source rows remain in the requested family multiplicity.
     * Matching uses literal category substrings, not a repaired suffix grammar.
     * @param query resolved source query, before expression-background filtering
     * @param background resolved user background, before its expression intersection
     * @param sets complete source DEG table, including at least one row named all
     * @param multiplicity explicit duplicate handling for query/background/set members
     * @param outside explicit handling of query genes outside expressed background
     * @param limits complete input-population bounds
     * @param check caller cancellation/deadline check
     * @return complete immutable result, without a significance-selection filter
     */
    public static Result test(List<String> query, List<String> background, List<DegSet> sets,
                              Multiplicity multiplicity, OutsideQuery outside, Limits limits, Check check) {
        if(query==null||background==null||sets==null||multiplicity==null||outside==null||limits==null||check==null)
            throw new IllegalArgumentException("Require complete DEG inputs, policies and bounds");
        if(query.isEmpty()||background.isEmpty()||query.size()>limits.maximumGenes||background.size()>limits.maximumGenes
                ||sets.isEmpty()||sets.size()>limits.maximumRows)throw new IllegalArgumentException("DEG query/background/source row capacity or empty population");
        check.run();var q=List.copyOf(query);var b=List.copyOf(background);var source=List.copyOf(sets);
        int duplicateQuery=duplicates(q),duplicateBackground=duplicates(b);
        if(multiplicity==Multiplicity.ERROR&&(duplicateQuery>0||duplicateBackground>0))throw new IllegalArgumentException("Repeated resolved DEG query/background gene; review source multiplicity policy");
        Set<String> expressed=new HashSet<>();long members=0;int allRows=0;
        for(DegSet row:source) {
            check.run();members=Math.addExact(members,row.members.size());if(members>limits.maximumMembers)throw new IllegalArgumentException("Complete DEG membership capacity exceeded");
            int i=0;for(String gene:row.members){id(gene);if((++i&4095)==0)check.run();if(row.name.equals("all"))expressed.add(gene);}
            if(row.name.equals("all"))allRows++;
        }
        if(allRows==0)throw new IllegalArgumentException("DEG table lacks the exact source all row; do not invent the expressed background");
        var retainedBackground=new ArrayList<String>();for(String gene:b)if(expressed.contains(gene))retainedBackground.add(gene);
        Set<String> universe=new HashSet<>(retainedBackground);var outsideGenes=new ArrayList<String>();for(String gene:q)if(!universe.contains(gene))outsideGenes.add(gene);
        if(outside==OutsideQuery.ERROR&&!outsideGenes.isEmpty())throw new IllegalArgumentException("DEG source query includes genes outside expressed background; explicitly retain source query size or correct inputs");
        int N=retainedBackground.size(),m=q.size();if(N<1||m>N)throw new IllegalArgumentException("Original DEG draw size exceeds expressed background or background is empty; source fallback is not a valid test");
        var families=new EnumMap<Category,List<Tested>>(Category.class);for(Category category:Category.values())families.put(category,new ArrayList<>());
        int ordinal=0;
        for(DegSet row:source) {
            check.run();ordinal++;var retained=new ArrayList<String>();Set<String> unique=new HashSet<>();
            for(String gene:row.members)if(universe.contains(gene)){retained.add(gene);unique.add(gene);}
            int n=retained.size(),repeated=n-unique.size();
            if(multiplicity==Multiplicity.ERROR&&repeated>0)throw new IllegalArgumentException("Repeated DEG source member in row "+ordinal+": "+row.name);
            if(n>N)throw new IllegalArgumentException("Original DEG set count exceeds expressed background in row "+ordinal);
            var overlap=new ArrayList<String>();for(String gene:q)if(unique.contains(gene))overlap.add(gene);int x=overlap.size();
            double p=x==0?1:jdistlib.HyperGeometric.cumulative(x-1.0,n,N-n,m,false,false);
            double logP=x==0?0:jdistlib.HyperGeometric.cumulative(x-1.0,n,N-n,m,false,true);
            if(!Double.isFinite(p)||p<0||p>1||Double.isNaN(logP)||logP>0)throw new ArithmeticException("Invalid original DEG tail in source row "+ordinal);
            boolean valid=duplicateQuery==0&&duplicateBackground==0&&repeated==0&&outsideGenes.isEmpty()&&x<=n;
            var tested=new Tested(ordinal,row,n,repeated,List.copyOf(overlap),p,logP,valid);
            for(Category category:Category.values())if(row.name.contains(category.marker))families.get(category).add(tested);
        }
        var output=new ArrayList<Row>();
        for(Category category:Category.values()) {
            List<Tested> family=families.get(category);int count=family.size();
            for(Tested row:family) {
                check.run();double adjusted=Math.min(1,row.p*count),logAdjusted=Math.min(0,row.logP+Math.log(count));
                output.add(new Row(category,row.sourceRow,row.source.name,label(row.source.name),N,m,row.source.declaredSize,
                        row.source.members.size(),row.size,row.duplicates,row.overlap.size(),row.overlap,count,row.p,row.logP,
                        adjusted,logAdjusted,row.valid,row.p==0&&Double.isFinite(row.logP)));
            }
        }
        check.run();return new Result(output,retainedBackground,outsideGenes,duplicateQuery,duplicateBackground,source.size(),members,allRows);
    }
    private static int duplicates(List<String> genes) {
        Set<String> seen=new HashSet<>();int count=0;for(String gene:genes){id(gene);if(!seen.add(gene))count++;}return count;
    }
    private static void id(String value) {
        if(value==null||value.isBlank()||value.length()>1024||value.indexOf(':')>=0
                ||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))
            throw new IllegalArgumentException("Require exact resolved DEG gene IDs without separators or controls");
    }
    private static void name(String value) {
        if(value==null||value.isBlank()||value.length()>4096||value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Require bounded DEG set labels without controls");
    }
    private static String label(String value) {
        int dot=value.lastIndexOf('.');if(dot==value.length()-1)dot=value.lastIndexOf('.',dot-1);
        return dot>0?value.substring(0,dot):value;
    }
}
