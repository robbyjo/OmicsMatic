/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact operations on zero-based, half-open intervals in one declared assembly. */
public final class GenomicIntervals {
    private GenomicIntervals() { }
    private static final Comparator<Interval> ORDER = Comparator.comparing(Interval::chromosome)
        .thenComparingLong(Interval::start).thenComparingLong(Interval::end).thenComparing(Interval::id);

    /** A nonempty [start,end) interval; strand is +, - or unknown (.). */
    public record Interval(String chromosome, long start, long end, String id, String strand) {
        public Interval {
            if (chromosome == null || chromosome.isBlank() || id == null || id.isBlank()
                    || start < 0 || end <= start || !List.of("+", "-", ".").contains(strand))
                throw new IllegalArgumentException("Require chromosome, ID, 0 <= start < end and strand +|-|.");
        }
        /** Interval length in bases. */
        public long length() { return end - start; }
    }

    /** A merged interval retaining all contributing IDs in coordinate order. */
    public record Merged(Interval interval, List<String> members) {
        public Merged { members = List.copyOf(members); }
    }

    /** Immutable augmented interval index; overlap queries avoid all-pairs scans. */
    public static final class Index {
        private final Map<String, Node> chromosomes = new HashMap<>();

        /** Build a balanced interval tree for each exact chromosome label. */
        public Index(List<Interval> intervals) {
            Map<String, List<Interval>> grouped = new HashMap<>();
            for (Interval interval : intervals) grouped.computeIfAbsent(interval.chromosome(), key -> new ArrayList<>()).add(interval);
            for (var entry : grouped.entrySet()) {
                entry.getValue().sort(ORDER);
                chromosomes.put(entry.getKey(), build(entry.getValue(), 0, entry.getValue().size()));
            }
        }

        /** Overlapping intervals in coordinate order; touching endpoints do not overlap. */
        public List<Interval> overlaps(Interval query, boolean sameStrand) {
            if (sameStrand && query.strand().equals(".")) throw new IllegalArgumentException("Stranded query needs + or -");
            List<Interval> result = new ArrayList<>();
            search(chromosomes.get(query.chromosome()), query, sameStrand, result);
            return List.copyOf(result);
        }
    }

    private record Node(Interval interval, long maximumEnd, Node left, Node right) { }

    private static Node build(List<Interval> sorted, int first, int last) {
        if (first == last) return null;
        int middle = first + (last - first) / 2;
        Node left = build(sorted, first, middle), right = build(sorted, middle + 1, last);
        long maximum = sorted.get(middle).end();
        if (left != null) maximum = Math.max(maximum, left.maximumEnd());
        if (right != null) maximum = Math.max(maximum, right.maximumEnd());
        return new Node(sorted.get(middle), maximum, left, right);
    }

    private static void search(Node node, Interval query, boolean stranded, List<Interval> result) {
        if (node == null || node.maximumEnd() <= query.start()) return;
        search(node.left(), query, stranded, result);
        Interval interval = node.interval();
        if (interval.start() >= query.end()) return;
        if (interval.end() > query.start() && (!stranded || interval.strand().equals(query.strand()))) result.add(interval);
        search(node.right(), query, stranded, result);
    }

    /** Number of overlapping bases, or zero for different chromosomes/disjoint intervals. */
    public static long overlap(Interval first, Interval second) {
        if (!first.chromosome().equals(second.chromosome())) return 0;
        return Math.max(0, Math.min(first.end(), second.end()) - Math.max(first.start(), second.start()));
    }

    /** Merge overlaps, bookended features and optional gaps. */
    public static List<Merged> merge(List<Interval> intervals, long maximumGap, boolean sameStrand) {
        if (maximumGap < 0) throw new IllegalArgumentException("maximumGap must be nonnegative");
        Map<String, List<Interval>> groups = new HashMap<>();
        for (Interval interval : intervals) {
            if (sameStrand && interval.strand().equals(".")) throw new IllegalArgumentException("Stranded merge needs + or -");
            String key = interval.chromosome() + "\u0000" + (sameStrand ? interval.strand() : ".");
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(interval);
        }
        List<Merged> result = new ArrayList<>();
        for (List<Interval> group : groups.values()) {
            group.sort(ORDER);
            Interval current = null;
            List<String> members = new ArrayList<>();
            for (Interval interval : group) {
                if (current != null && (interval.start() <= current.end() || interval.start() - current.end() <= maximumGap)) {
                    current = new Interval(current.chromosome(), current.start(), Math.max(current.end(), interval.end()), current.id(), sameStrand ? current.strand() : ".");
                    members.add(interval.id());
                } else {
                    if (current != null) result.add(new Merged(current, members));
                    current = new Interval(interval.chromosome(), interval.start(), interval.end(), interval.id(), sameStrand ? interval.strand() : ".");
                    members = new ArrayList<>(List.of(interval.id()));
                }
            }
            if (current != null) result.add(new Merged(current, members));
        }
        result.sort(Comparator.comparing(Merged::interval, ORDER).thenComparing(m -> m.interval().strand()));
        return List.copyOf(result);
    }

    /** Uncovered pieces of a query interval after subtracting the union of overlaps. */
    public static List<Interval> subtract(Interval query, Index index, boolean sameStrand) {
        List<Interval> result = new ArrayList<>();
        long cursor = query.start();
        for (Interval mask : index.overlaps(query, sameStrand)) {
            if (mask.start() > cursor) result.add(new Interval(query.chromosome(), cursor, Math.min(query.end(), mask.start()), query.id(), query.strand()));
            cursor = Math.max(cursor, Math.min(query.end(), mask.end()));
            if (cursor == query.end()) break;
        }
        if (cursor < query.end()) result.add(new Interval(query.chromosome(), cursor, query.end(), query.id(), query.strand()));
        return List.copyOf(result);
    }

    /** Union coverage in bases; overlapping masks never double count a base. */
    public static long coveredBases(Interval query, Index index, boolean sameStrand) {
        long uncovered = 0;
        for (Interval piece : subtract(query, index, sameStrand)) uncovered += piece.length();
        return query.length() - uncovered;
    }
}
