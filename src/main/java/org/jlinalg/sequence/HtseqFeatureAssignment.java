/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Native interval-set assignment corresponding to HTSeq GenomicArrayOfSets and
 * the documented union/intersection modes. Coordinates are exact nonnegative
 * zero-based, half-open longs. This API consumes already-derived aligned blocks;
 * it does not parse SAM/GTF, pair reads, infer strands, filter flags/MAPQ/NH,
 * resolve multi-alignments, sample assignments, or reproduce custom paper code.
 *
 * <p>Source reference: HTSeq 2.1.2, HTSeq/__init__.py (GenomicArrayOfSets),
 * src/HTSeq/_HTSeq.pyx (interval and step behavior), and the official htseq-count
 * mode definitions at https://htseq.readthedocs.io/en/latest/htseqcount.html.
 * Original step output is retained separately for comparison. This is native
 * Java code with an immutable interval tree, not an embedded Python execution.
 */
public final class HtseqFeatureAssignment {
    private HtseqFeatureAssignment() { }

    /** Feature resolution over every supplied aligned block. */
    public enum Mode { UNION, INTERSECTION_STRICT, INTERSECTION_NONEMPTY }
    /** Classification before any caller-specific counting/nonunique policy. */
    public enum Outcome { NO_FEATURE, UNIQUE, AMBIGUOUS }

    /** Exact chromosome/strand identity; no alias or coordinate conversion. */
    public record Interval(String chromosome, long start, long end, char strand) {
        public Interval {
            Objects.requireNonNull(chromosome, "chromosome");
            if (chromosome.isEmpty()) throw new IllegalArgumentException("Empty chromosome identity");
            if (start < 0 || end < start) throw new IllegalArgumentException("Require nonnegative half-open interval start <= end");
            if (strand != '+' && strand != '-' && strand != '.') throw new IllegalArgumentException("Require strand +, - or .");
        }
    }

    /** Repeated intervals and repeated feature IDs contribute set membership. */
    public record Feature(Interval interval, String id) {
        public Feature {
            Objects.requireNonNull(interval, "interval");
            Objects.requireNonNull(id, "feature ID");
        }
    }

    /** Maximal constant-membership span, including empty intergenic spans. */
    public record Step(Interval interval, List<String> features) {
        public Step {
            Objects.requireNonNull(interval, "interval");
            features = List.copyOf(features);
        }
    }

    /** Immutable source set, in Unicode code-point order for deterministic transport. */
    public record Assignment(Mode mode, List<String> features) {
        public Assignment {
            Objects.requireNonNull(mode, "mode");
            features = List.copyOf(features);
        }
        public Outcome outcome() {
            return features.isEmpty() ? Outcome.NO_FEATURE : features.size() == 1 ? Outcome.UNIQUE : Outcome.AMBIGUOUS;
        }
    }

    /**
     * Explicit capacity guards, never truncation. Match/step limits apply to a
     * complete steps call or complete multi-block assignment. Limits bound
     * logical populations, not JVM resident memory. Zero-match/zero-label limits
     * can admit empty queries while refusing an overlap.
     */
    public record Limits(int maximumFeatures, int maximumBlocks, int maximumMatches,
                         int maximumSteps, int maximumLabelsPerStep) {
        public Limits {
            if (maximumFeatures < 0 || maximumFeatures > 2_000_000
                    || maximumBlocks < 0 || maximumBlocks > 1_000_000
                    || maximumMatches < 0 || maximumMatches > 2_000_000
                    || maximumSteps < 1 || maximumSteps > 4_000_001
                    || maximumLabelsPerStep < 0 || maximumLabelsPerStep > 2_000_000) {
                throw new IllegalArgumentException("Invalid native HTSeq interval capacity");
            }
        }
        public static Limits defaults() { return new Limits(2_000_000, 100_000, 2_000_000, 4_000_001, 2_000_000); }
    }

    private record Key(String chromosome, char strand) { }
    private static final class Node {
        final Feature feature;
        final Node left, right;
        final long maximumEnd;
        Node(Feature feature, Node left, Node right) {
            this.feature = feature; this.left = left; this.right = right;
            maximumEnd = Math.max(feature.interval.end,
                    Math.max(left == null ? 0 : left.maximumEnd, right == null ? 0 : right.maximumEnd));
        }
    }

    /** Read-only index; independent calls can execute concurrently. */
    public static final class Index {
        private final Map<Key, Node> roots;
        private final boolean stranded;
        private final Limits limits;
        private final int featureRecords;
        private Index(Map<Key, Node> roots, boolean stranded, Limits limits, int featureRecords) {
            this.roots = Map.copyOf(roots); this.stranded = stranded;
            this.limits = limits; this.featureRecords = featureRecords;
        }
        public boolean stranded() { return stranded; }
        /** Number of admitted nonempty feature intervals, including duplicates. */
        public int featureRecords() { return featureRecords; }

        public List<Step> steps(Interval query, BooleanSupplier cancelled) {
            return steps(query, cancelled, new Budget(limits));
        }
        public List<Step> steps(Interval query) { return steps(query, () -> false); }

        public Assignment assign(List<Interval> blocks, Mode mode, BooleanSupplier cancelled) {
            Objects.requireNonNull(blocks, "aligned blocks");
            Objects.requireNonNull(mode, "mode");
            check(cancelled);
            if (blocks.size() > limits.maximumBlocks) throw new IllegalArgumentException("Aligned-block capacity exceeded; no assignment admitted");
            var budget = new Budget(limits);
            TreeSet<String> result = null;
            for (Interval block : blocks) {
                check(cancelled);
                for (Step step : steps(block, cancelled, budget)) {
                    if (mode == Mode.INTERSECTION_NONEMPTY && step.features.isEmpty()) continue;
                    if (result == null) {
                        result = new TreeSet<>(CODE_POINTS); result.addAll(step.features);
                    } else if (mode == Mode.UNION) result.addAll(step.features);
                    else result.retainAll(step.features);
                    if (result.size() > limits.maximumLabelsPerStep) throw new IllegalArgumentException("Assignment-label capacity exceeded; no assignment admitted");
                }
            }
            return new Assignment(mode, result == null ? List.of() : List.copyOf(result));
        }
        public Assignment assign(List<Interval> blocks, Mode mode) { return assign(blocks, mode, () -> false); }

        private List<Step> steps(Interval query, BooleanSupplier cancelled, Budget budget) {
            Objects.requireNonNull(query, "query interval");
            check(cancelled);
            if (stranded && query.strand == '.') throw new IllegalArgumentException("A stranded index requires query strand + or -");
            if (query.start == query.end) throw new IllegalArgumentException("HTSeq set-array queries require nonempty intervals");
            Key key = new Key(query.chromosome, stranded ? query.strand : '.');
            var events = new TreeMap<Long, TreeMap<String, Integer>>();
            events.put(query.start, new TreeMap<>(CODE_POINTS));
            events.put(query.end, new TreeMap<>(CODE_POINTS));
            overlaps(roots.get(key), query, events, cancelled, budget);
            var active = new TreeMap<String, Integer>(CODE_POINTS);
            var steps = new ArrayList<Step>();
            Long previous = null;
            for (var event : events.entrySet()) {
                check(cancelled);
                long position = event.getKey();
                if (previous != null && previous < position) {
                    budget.step();
                    var labels = List.copyOf(active.keySet());
                    Interval interval = new Interval(query.chromosome, previous, position, key.strand);
                    if (!steps.isEmpty() && steps.get(steps.size()-1).features.equals(labels)) {
                        Step old = steps.get(steps.size()-1);
                        steps.set(steps.size()-1, new Step(new Interval(query.chromosome, old.interval.start, position, key.strand), labels));
                    } else steps.add(new Step(interval, labels));
                }
                for (var change : event.getValue().entrySet()) {
                    check(cancelled);
                    int value = active.getOrDefault(change.getKey(), 0) + change.getValue();
                    if (value < 0) throw new IllegalStateException("Internal interval membership underflow");
                    if (value == 0) active.remove(change.getKey()); else active.put(change.getKey(), value);
                }
                if (active.size() > limits.maximumLabelsPerStep) throw new IllegalArgumentException("Step-label capacity exceeded; no assignment admitted");
                previous = position;
            }
            if (!active.isEmpty()) throw new IllegalStateException("Unclosed interval membership");
            return List.copyOf(steps);
        }
    }

    public static Index index(List<Feature> features, boolean stranded, Limits limits, BooleanSupplier cancelled) {
        Objects.requireNonNull(features, "features"); Objects.requireNonNull(limits, "limits");
        check(cancelled);
        if (features.size() > limits.maximumFeatures) throw new IllegalArgumentException("Feature-record capacity exceeded; no index admitted");
        Map<Key, List<Feature>> groups = new HashMap<>();
        for (Feature feature : features) {
            check(cancelled); Objects.requireNonNull(feature, "feature");
            Interval interval = feature.interval;
            if (stranded && interval.strand == '.') throw new IllegalArgumentException("A stranded index requires feature strand + or -");
            if (interval.start == interval.end) throw new IllegalArgumentException("HTSeq set-array feature insertion requires a nonempty interval");
            groups.computeIfAbsent(new Key(interval.chromosome, stranded ? interval.strand : '.'), ignored -> new ArrayList<>()).add(feature);
        }
        Map<Key, Node> roots = new HashMap<>();
        for (var group : groups.entrySet()) {
            check(cancelled);
            Feature[] sorted = group.getValue().toArray(Feature[]::new);
            Arrays.sort(sorted, (a,b) -> {
                check(cancelled);
                int start = Long.compare(a.interval.start, b.interval.start);
                return start != 0 ? start : Long.compare(a.interval.end, b.interval.end);
            });
            roots.put(group.getKey(), tree(sorted, 0, sorted.length, cancelled));
        }
        return new Index(roots, stranded, limits, features.size());
    }
    public static Index index(List<Feature> features, boolean stranded) {
        return index(features, stranded, Limits.defaults(), () -> false);
    }

    private static Node tree(Feature[] sorted, int start, int end, BooleanSupplier cancelled) {
        check(cancelled);
        if (start == end) return null;
        int middle = start + (end-start)/2;
        return new Node(sorted[middle], tree(sorted, start, middle, cancelled), tree(sorted, middle+1, end, cancelled));
    }
    private static void overlaps(Node node, Interval query, TreeMap<Long, TreeMap<String, Integer>> events,
                                 BooleanSupplier cancelled, Budget budget) {
        check(cancelled);
        if (node == null || node.maximumEnd <= query.start) return;
        overlaps(node.left, query, events, cancelled, budget);
        Interval feature = node.feature.interval;
        if (feature.start < query.end) {
            if (feature.end > query.start) {
                budget.match();
                event(events, Math.max(feature.start, query.start), node.feature.id, 1);
                event(events, Math.min(feature.end, query.end), node.feature.id, -1);
            }
            overlaps(node.right, query, events, cancelled, budget);
        }
    }
    private static void event(TreeMap<Long, TreeMap<String, Integer>> events, long position, String id, int value) {
        events.computeIfAbsent(position, ignored -> new TreeMap<>(CODE_POINTS)).merge(id, value, Integer::sum);
    }
    private static void check(BooleanSupplier cancelled) {
        Objects.requireNonNull(cancelled, "cancellation");
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException("Native HTSeq interval assignment cancelled; no result admitted");
    }
    private static final class Budget {
        final Limits limits;
        int matches, steps;
        Budget(Limits limits) { this.limits = limits; }
        void match() { if (++matches > limits.maximumMatches) throw new IllegalArgumentException("Overlap-match capacity exceeded; no assignment admitted"); }
        void step() { if (++steps > limits.maximumSteps) throw new IllegalArgumentException("Interval-event-span capacity exceeded; no assignment admitted"); }
    }
    private static final Comparator<String> CODE_POINTS = (a,b) -> {
        int i=0,j=0;
        while (i<a.length() && j<b.length()) {
            int x=a.codePointAt(i),y=b.codePointAt(j);
            if (x!=y) return Integer.compare(x,y);
            i+=Character.charCount(x);j+=Character.charCount(y);
        }
        return Integer.compare(a.length()-i,b.length()-j);
    };
}
