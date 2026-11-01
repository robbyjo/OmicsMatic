/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Complete unrooted bootstrap split counts and printed-support audits.
 * Every replicate has the exact declared taxon set. An edge split counts once
 * per replicate, including when a degree-two root represents it twice. No
 * collection result is returned before EOF and successful close. This counts
 * supplied trees; it does not generate replicates or estimate SH-aLRT support.
 */
public final class BootstrapSupport {
    private BootstrapSupport() { }

    /** Membership limits bound both per-tree splits and the complete split table. */
    public record Limits(NewickReader.Limits collection, long perTreeMemberships,
                         long distinctSplitMemberships) {
        public Limits {
            Objects.requireNonNull(collection);
            if(perTreeMemberships<1 || distinctSplitMemberships<1)
                throw new IllegalArgumentException("Bootstrap split capacities must be positive");
        }
        public static Limits defaults() {
            return new Limits(NewickReader.Limits.defaults(),100_000_000,100_000_000);
        }
    }

    /** Immutable exact counts; zero-count splits are looked up with occurrences(). */
    public record Counts(Set<String> taxa, long replicates, Map<Set<String>,Long> splits) {
        public Counts {
            taxa=Collections.unmodifiableSet(new TreeSet<>(taxa));
            if(taxa.isEmpty() || replicates<1)throw new IllegalArgumentException("Require taxa and positive replicate count");
            var copy=new LinkedHashMap<Set<String>,Long>();
            String first=taxa.iterator().next();
            for(var entry:splits.entrySet()) {
                Set<String> side=Collections.unmodifiableSet(new TreeSet<>(entry.getKey()));
                int other=taxa.size()-side.size();long count=Objects.requireNonNull(entry.getValue());
                if(!taxa.containsAll(side) || side.size()<2 || other<2 || side.size()>other
                        || side.size()==other&&!side.contains(first) || count<1 || count>replicates)
                    throw new IllegalArgumentException("Invalid canonical bootstrap split/count");
                copy.put(side,count);
            }
            splits=Collections.unmodifiableMap(copy);
        }
        public long occurrences(Set<String> canonicalSplit) { return splits.getOrDefault(canonicalSplit,0L); }
    }

    /** Exact source label and counts; agreement uses its printed decimal precision. */
    public record Comparison(int childNode, Set<String> split, List<String> reportedColumns,
                             int bootstrapColumn, long occurrences, long replicates,
                             boolean agreesAtPrintedPrecision) {
        public Comparison { split=Set.copyOf(split);reportedColumns=List.copyOf(reportedColumns); }
        public double percentage() { return 100.0*occurrences/replicates; }
    }

    /** Takes ownership of source; no prefix or partially closed collection is returned. */
    public static Counts count(Reader source, Set<String> expectedTaxa, long expectedReplicates,
                               boolean requireMetricLengths, Limits limits) throws IOException {
        Objects.requireNonNull(limits);Objects.requireNonNull(source);
        // Validate before taking ownership so a rejected call cannot imply admission.
        Set<String> taxa=Set.copyOf(expectedTaxa);
        if(taxa.isEmpty() || expectedReplicates<1 || expectedReplicates>limits.collection().trees())
            throw new IllegalArgumentException("Require a positive complete replicate count within collection capacity");
        var collection=new NewickReader(source,limits.collection());
        var counts=new LinkedHashMap<Set<String>,Long>();long n=0,memberships=0;
        try(collection) {
            NewickTree tree;
            while((tree=collection.nextTree())!=null) {
                if(!tree.taxa().equals(taxa))throw new IOException("Bootstrap replicate "+(n+1)+" has a different exact taxon set");
                if(requireMetricLengths)tree.requireMetricLengths();
                if(++n>expectedReplicates)throw new IOException("More bootstrap replicates than declared; no collection result is admitted");
                for(Set<String> split:tree.bipartitions(limits.perTreeMemberships())) {
                    Long previous=counts.get(split);
                    if(previous==null) {
                        memberships=Math.addExact(memberships,split.size());
                        if(memberships>limits.distinctSplitMemberships())
                            throw new IOException("Complete bootstrap distinct-split membership capacity exceeded");
                        counts.put(split,1L);
                    } else counts.put(split,Math.addExact(previous,1));
                }
            }
            if(n!=expectedReplicates)throw new IOException("Bootstrap replicate count differs: expected "+expectedReplicates+", found "+n);
        } catch(IllegalArgumentException|ArithmeticException invalid) {
            throw new IOException("Invalid or over-capacity complete bootstrap collection",invalid);
        }
        if(!collection.isComplete())throw new IOException("Bootstrap collection did not reach complete EOF and successful close");
        return new Counts(taxa,n,counts);
    }

    private static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?");
    private static BigDecimal support(String text) {
        if(text.length()>64 || !DECIMAL.matcher(text).matches())
            throw new IllegalArgumentException("Require a finite decimal branch-support percentage");
        BigDecimal value=new BigDecimal(text);
        if(Math.abs((long)value.scale())>18 || value.compareTo(BigDecimal.ZERO)<0
                || value.compareTo(BigDecimal.valueOf(100))>0)
            throw new IllegalArgumentException("Branch-support percentage/precision is outside the admitted range");
        return value;
    }

    /**
     * Check every nontrivial reference edge, retaining duplicate root edges.
     * Column count and bootstrap column are explicit; all other columns must be
     * finite percentages but are not claimed to be reproduced by split counts.
     * Missing/extra/non-numeric labels fail. Mismatches remain explicit records;
     * the caller must require agreement before admitting its scientific result.
     */
    public static List<Comparison> compare(NewickTree reference, Counts counts,
                                           int columns, int bootstrapColumn, long memberships) {
        Objects.requireNonNull(reference);Objects.requireNonNull(counts);
        if(columns<1 || columns>8 || bootstrapColumn<0 || bootstrapColumn>=columns)
            throw new IllegalArgumentException("Require declared support columns and bootstrap column");
        if(!reference.taxa().equals(counts.taxa()))throw new IllegalArgumentException("Reference and bootstrap taxon sets differ");
        var result=new ArrayList<Comparison>();
        for(var entry:reference.edgeBipartitions(memberships).entrySet()) {
            int child=entry.getKey();String label=reference.nodes().get(child).label();
            if(label==null)throw new IllegalArgumentException("Missing support label on reference edge to node "+child);
            List<String> values=List.of(label.split("/",-1));
            if(values.size()!=columns)throw new IllegalArgumentException("Reference support-column count differs at node "+child);
            for(String value:values)support(value);
            String token=values.get(bootstrapColumn);BigDecimal printed=support(token);
            long observed=counts.occurrences(entry.getValue());
            // Compare the exact rational count against the source rounding bin.
            BigDecimal difference=printed.multiply(BigDecimal.valueOf(counts.replicates()))
                    .subtract(BigDecimal.valueOf(observed).multiply(BigDecimal.valueOf(100))).abs();
            BigDecimal halfBin=printed.ulp().abs().multiply(BigDecimal.valueOf(counts.replicates())).divide(BigDecimal.valueOf(2));
            result.add(new Comparison(child,entry.getValue(),values,bootstrapColumn,observed,counts.replicates(),difference.compareTo(halfBin)<=0));
        }
        return List.copyOf(result);
    }
}
