/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.jlinalg.sequence.HtseqFeatureAssignment.Feature;
import org.jlinalg.sequence.HtseqFeatureAssignment.Interval;
import org.jlinalg.sequence.HtseqFeatureAssignment.Mode;

/**
 * Native HTSeq 2.1.2 classification and deterministic counting of explicitly
 * prepared read units. SAM/GTF parsing, CIGAR interpretation and pairing must
 * already have been performed and are not independently reproduced by this API.
 * The reference is HTSeq/scripts/count_features/count_features_per_file.py and
 * reads_stats.py. Original source filter order and missing-NH short circuit are
 * retained, including summary categories that can overlap under ALL/FRACTION.
 */
public final class HtseqReadCounter {
    private HtseqReadCounter() { }

    public enum Layout { SINGLE, PAIRED }
    public enum Strand { YES, NO, REVERSE }
    /** Random feature allocation remains an upstream operation. */
    public enum Nonunique { NONE, ALL, FRACTION }
    public enum FlagPolicy { IGNORE, SCORE }
    public enum State {
        NOT_ALIGNED, IGNORED_SECONDARY, IGNORED_SUPPLEMENTARY, ALIGNMENT_NOT_UNIQUE,
        LOW_QUALITY, NO_FEATURE, AMBIGUOUS, ASSIGNED
    }
    public enum NhCheck {
        NOT_CHECKED, MISSING_TAG, FIRST_MATE_MISSING_TAG_ABORTED,
        SECOND_MATE_MISSING_TAG, AT_MOST_ONE, NONUNIQUE
    }

    /** A parsed alignment with original MAPQ/NH/flag values and only M/= /X blocks. */
    public record Alignment(boolean aligned, boolean secondary, boolean supplementary,
                            int mapq, Integer nh, List<Interval> blocks) {
        public Alignment {
            if (mapq<0 || mapq>255) throw new IllegalArgumentException("Require original SAM MAPQ integer in0..255");
            blocks=List.copyOf(blocks);
            if (!aligned && !blocks.isEmpty()) throw new IllegalArgumentException("Unaligned prepared record must not contain aligned blocks");
            for (Interval block:blocks) {
                if (block.start()==block.end()) throw new IllegalArgumentException("Prepared matched blocks must be nonempty");
                if (block.strand()!='+' && block.strand()!='-') throw new IllegalArgumentException("Prepared SAM matched blocks require + or - strand");
            }
        }
    }

    /** For PAIRED, first/second retain mate identity and either may be absent. */
    public record Unit(String id, Layout layout, Alignment first, Alignment second) {
        public Unit {
            Objects.requireNonNull(id,"read-unit identity");Objects.requireNonNull(layout,"layout");
            if (layout==Layout.SINGLE && (first==null || second!=null)) throw new IllegalArgumentException("Single-end unit requires exactly one first alignment");
        }
    }

    /** All scientific choices are explicit; defaults refer only to source2.1.2. */
    public record Config(Layout layout, Strand stranded, Mode overlap, Nonunique nonunique,
                         int minimumMapq, FlagPolicy secondary, FlagPolicy supplementary) {
        public Config {
            Objects.requireNonNull(layout,"layout");Objects.requireNonNull(stranded,"strand rule");
            Objects.requireNonNull(overlap,"overlap rule");Objects.requireNonNull(nonunique,"nonunique rule");
            Objects.requireNonNull(secondary,"secondary rule");Objects.requireNonNull(supplementary,"supplementary rule");
            if (minimumMapq<0 || minimumMapq>255) throw new IllegalArgumentException("Require minimum MAPQ in0..255");
        }
        public static Config source212Defaults(Layout layout) {
            return new Config(layout,Strand.YES,Mode.UNION,Nonunique.NONE,10,FlagPolicy.IGNORE,FlagPolicy.IGNORE);
        }
    }

    /** Contains zero-count feature identities and the exact annotation chromosome set. */
    public static final class Model {
        private final HtseqFeatureAssignment.Index index;
        private final List<String> featureIds;
        private final Set<String> chromosomes;
        private Model(HtseqFeatureAssignment.Index index,List<String> featureIds,Set<String> chromosomes) {
            this.index=index;this.featureIds=List.copyOf(featureIds);this.chromosomes=Set.copyOf(chromosomes);
        }
        public List<String> featureIds() { return featureIds; }
        public Set<String> chromosomes() { return chromosomes; }
    }

    public record Decision(String unitId, State state, NhCheck nhCheck, boolean alignmentNotUnique,
                           boolean unknownChromosome, List<String> features, Map<String,Double> allocations) {
        public Decision {
            features=List.copyOf(features);
            allocations=Collections.unmodifiableMap(new LinkedHashMap<>(allocations));
        }
    }

    /** Source counters are not a disjoint partition when nonunique is ALL/FRACTION. */
    public record Statistics(long noFeature,long ambiguous,long tooLowAQual,long notAligned,long alignmentNotUnique,
                             long ignoredSecondary,long ignoredSupplementary,long assignedUnique,long unknownChromosome) { }
    public record Counts(List<String> featureIds,Map<String,Double> counts,Statistics statistics,long processedUnits) {
        public Counts {
            featureIds=List.copyOf(featureIds);
            counts=Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        }
        public boolean emptyInput() { return processedUnits==0; }
    }

    public static Model prepare(List<Feature> features,Config config,HtseqFeatureAssignment.Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(config,"config");check(cancelled);
        var index=HtseqFeatureAssignment.index(features,config.stranded!=Strand.NO,limits,cancelled);
        var ids=new TreeSet<String>(CODE_POINTS);var chromosomes=new HashSet<String>();
        for (Feature feature:features) {check(cancelled);ids.add(feature.id());chromosomes.add(feature.interval().chromosome());}
        return new Model(index,List.copyOf(ids),chromosomes);
    }
    public static Model prepare(List<Feature> features,Config config) {
        return prepare(features,config,HtseqFeatureAssignment.Limits.defaults(),()->false);
    }

    public static Decision assess(Model model,Unit unit,Config config,BooleanSupplier cancelled) {
        Objects.requireNonNull(model,"model");Objects.requireNonNull(unit,"unit");Objects.requireNonNull(config,"config");
        check(cancelled);
        if (unit.layout!=config.layout) throw new IllegalArgumentException("Prepared unit layout differs from declared counting layout");
        if (model.index.stranded()!=(config.stranded!=Strand.NO)) throw new IllegalArgumentException("Feature index strand domain differs from counting configuration");
        Alignment a=unit.first,b=unit.second;
        if ((a==null || !a.aligned) && (b==null || !b.aligned)) return empty(unit,State.NOT_ALIGNED,NhCheck.NOT_CHECKED,false,false);
        if (config.secondary==FlagPolicy.IGNORE && (a!=null && a.secondary || b!=null && b.secondary)) return empty(unit,State.IGNORED_SECONDARY,NhCheck.NOT_CHECKED,false,false);
        if (config.supplementary==FlagPolicy.IGNORE && (a!=null && a.supplementary || b!=null && b.supplementary)) return empty(unit,State.IGNORED_SUPPLEMENTARY,NhCheck.NOT_CHECKED,false,false);
        NhCheck nh=nh(unit);
        boolean nonunique=nh==NhCheck.NONUNIQUE;
        if (nonunique && config.nonunique==Nonunique.NONE) return empty(unit,State.ALIGNMENT_NOT_UNIQUE,nh,true,false);
        if (a!=null && a.mapq<config.minimumMapq || b!=null && b.mapq<config.minimumMapq) return empty(unit,State.LOW_QUALITY,nh,nonunique,false);
        var blocks=new ArrayList<Interval>();
        append(blocks,a,config.stranded==Strand.REVERSE,cancelled);
        if (unit.layout==Layout.PAIRED) append(blocks,b,config.stranded!=Strand.REVERSE,cancelled);
        // Source UnknownChrom aborts the entire unit, including a valid mate.
        for (Interval block:blocks) {
            check(cancelled);
            if (!model.chromosomes.contains(block.chromosome())) return empty(unit,State.NO_FEATURE,nh,nonunique,true);
        }
        var assigned=model.index.assign(blocks,config.overlap,cancelled).features();
        if (assigned.isEmpty()) return empty(unit,State.NO_FEATURE,nh,nonunique,false);
        State state=assigned.size()==1?State.ASSIGNED:State.AMBIGUOUS;
        Map<String,Double> allocations=new LinkedHashMap<>();
        if (config.nonunique!=Nonunique.NONE || assigned.size()==1) {
            double increment=config.nonunique==Nonunique.FRACTION?1.0/assigned.size():1.0;
            for (String feature:assigned) allocations.put(feature,increment);
        }
        return new Decision(unit.id,state,nh,nonunique,false,assigned,allocations);
    }
    public static Decision assess(Model model,Unit unit,Config config) { return assess(model,unit,config,()->false); }

    public static Counts count(Model model,Iterable<Unit> units,Config config,long maximumUnits,BooleanSupplier cancelled) {
        Objects.requireNonNull(model,"model");Objects.requireNonNull(units,"units");Objects.requireNonNull(config,"config");
        if (maximumUnits<1 || maximumUnits>1_000_000_000_000L) throw new IllegalArgumentException("Counting-unit bound must be1..1e12");
        if (model.index.stranded()!=(config.stranded!=Strand.NO)) throw new IllegalArgumentException("Feature index strand domain differs from counting configuration");
        check(cancelled);
        Map<String,Double> counts=new LinkedHashMap<>();for (String feature:model.featureIds)counts.put(feature,0.0);
        long processed=0,empty=0,ambiguous=0,lowqual=0,notaligned=0,nonunique=0,secondary=0,supplementary=0,unique=0,unknown=0;
        for (Unit unit:units) {
            check(cancelled);
            if (processed==maximumUnits) throw new IllegalArgumentException("Counting-unit capacity exceeded; no count result admitted");
            processed++;
            Decision decision=assess(model,unit,config,cancelled);
            if (decision.alignmentNotUnique) nonunique++;
            if (decision.unknownChromosome) unknown++;
            switch(decision.state) {
                case NO_FEATURE -> empty++;
                case AMBIGUOUS -> ambiguous++;
                case LOW_QUALITY -> lowqual++;
                case NOT_ALIGNED -> notaligned++;
                case IGNORED_SECONDARY -> secondary++;
                case IGNORED_SUPPLEMENTARY -> supplementary++;
                case ASSIGNED -> unique++;
                case ALIGNMENT_NOT_UNIQUE -> { }
            }
            for (var allocation:decision.allocations.entrySet()) {
                check(cancelled);
                Double previous=counts.get(allocation.getKey());
                if (previous==null) throw new IllegalStateException("Assigned feature missing from annotation identities");
                counts.put(allocation.getKey(),previous+allocation.getValue());
            }
        }
        return new Counts(model.featureIds,counts,new Statistics(empty,ambiguous,lowqual,notaligned,nonunique,secondary,supplementary,unique,unknown),processed);
    }
    public static Counts count(Model model,Iterable<Unit> units,Config config,long maximumUnits) { return count(model,units,config,maximumUnits,()->false); }

    private static NhCheck nh(Unit unit) {
        if (unit.layout==Layout.SINGLE) return unit.first.nh==null?NhCheck.MISSING_TAG:unit.first.nh>1?NhCheck.NONUNIQUE:NhCheck.AT_MOST_ONE;
        if (unit.first!=null) {
            if (unit.first.nh==null) return NhCheck.FIRST_MATE_MISSING_TAG_ABORTED;
            if (unit.first.nh>1) return NhCheck.NONUNIQUE;
        }
        if (unit.second!=null) {
            if (unit.second.nh==null) return NhCheck.SECOND_MATE_MISSING_TAG;
            if (unit.second.nh>1) return NhCheck.NONUNIQUE;
        }
        return NhCheck.AT_MOST_ONE;
    }
    private static Decision empty(Unit unit,State state,NhCheck nh,boolean nonunique,boolean unknown) {
        return new Decision(unit.id,state,nh,nonunique,unknown,List.of(),Map.of());
    }
    private static void append(List<Interval> blocks,Alignment read,boolean invert,BooleanSupplier cancelled) {
        if (read==null || !read.aligned) return;
        for (Interval block:read.blocks) {
            check(cancelled);
            blocks.add(invert?new Interval(block.chromosome(),block.start(),block.end(),block.strand()=='+'?'-':'+'):block);
        }
    }
    private static void check(BooleanSupplier cancelled) {
        Objects.requireNonNull(cancelled,"cancellation");
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException("Native HTSeq counting cancelled; no result admitted");
    }
    private static final Comparator<String> CODE_POINTS=(a,b)->{
        int i=0,j=0;
        while(i<a.length() && j<b.length()) {
            int x=a.codePointAt(i),y=b.codePointAt(j);
            if(x!=y)return Integer.compare(x,y);
            i+=Character.charCount(x);j+=Character.charCount(y);
        }
        return Integer.compare(a.length()-i,b.length()-j);
    };
}
