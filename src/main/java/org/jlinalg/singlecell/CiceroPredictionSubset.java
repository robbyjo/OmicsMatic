/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.*;

/** Accessibility-only reuse of supplied predictions; all intervals are zero-based half-open.
 * No gene-expression, score, correlation, fitting or genome-alias rule is inferred. */
public final class CiceroPredictionSubset {
    private CiceroPredictionSubset() { }

    public record Interval(String chromosome, long start, long end) {
        public Interval {
            literal(chromosome);
            if (start < 0 || end <= start) throw new IllegalArgumentException("Require a nonempty zero-based half-open interval");
        }
    }
    public record Peak(String id, Interval interval) {
        public Peak { literal(id); Objects.requireNonNull(interval, "interval"); }
    }
    /** Witness is the first qualifying peak in supplied order, independent of tree layout. */
    public record Hit(boolean accessible, String firstWitness, int matchingPeaks) { }

    public static final class Budget {
        private final long maximum;
        private long used;
        public Budget(long maximum) {
            if (maximum < 1) throw new IllegalArgumentException("Require positive operation budget");
            this.maximum = maximum;
        }
        public long used() { return used; }
        private void use() {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Accessibility audit interrupted");
            if (used == maximum) throw new IllegalStateException("Accessibility operation budget exceeded");
            used++;
        }
    }
    private record Entry(Peak peak, int ordinal) { }
    private static final class Node {
        final Entry entry;
        final Node left, right;
        final long maximumEnd;
        Node(List<Entry> rows, int start, int end) {
            int middle = (start + end) >>> 1;
            entry = rows.get(middle);
            left = start < middle ? new Node(rows, start, middle) : null;
            right = middle + 1 < end ? new Node(rows, middle + 1, end) : null;
            maximumEnd = Math.max(entry.peak.interval.end, Math.max(left == null ? 0 : left.maximumEnd, right == null ? 0 : right.maximumEnd));
        }
    }
    public static final class Index {
        private final Map<String, Node> chromosomes;
        public Index(List<Peak> peaks) {
            Objects.requireNonNull(peaks, "peaks");
            var grouped = new HashMap<String, List<Entry>>();
            var names = new HashSet<String>();
            for (int i = 0; i < peaks.size(); i++) {
                Peak peak = Objects.requireNonNull(peaks.get(i), "peak");
                if (!names.add(peak.id)) throw new IllegalArgumentException("Duplicate accessible peak ID");
                grouped.computeIfAbsent(peak.interval.chromosome, k -> new ArrayList<>()).add(new Entry(peak, i));
            }
            var built = new HashMap<String, Node>();
            for (var row : grouped.entrySet()) {
                row.getValue().sort(Comparator.comparingLong((Entry e) -> e.peak.interval.start).thenComparingInt(Entry::ordinal));
                built.put(row.getKey(), new Node(row.getValue(), 0, row.getValue().size()));
            }
            chromosomes = Map.copyOf(built);
        }
        public Hit match(Interval interval, long minimumOverlap, Budget budget) {
            Objects.requireNonNull(interval, "interval"); Objects.requireNonNull(budget, "budget");
            if (minimumOverlap < 1) throw new IllegalArgumentException("Require positive minimum overlap in bases");
            var found = new Match();
            search(chromosomes.get(interval.chromosome), interval, minimumOverlap, budget, found);
            return new Hit(found.count > 0, found.first == null ? "" : found.first.peak.id, found.count);
        }
    }
    private static final class Match { Entry first; int count; }
    private static void search(Node node, Interval query, long minimum, Budget budget, Match found) {
        budget.use();
        if (node == null || node.maximumEnd <= query.start || query.end - query.start < minimum) return;
        if (node.left != null) search(node.left, query, minimum, budget, found);
        Interval peak = node.entry.peak.interval;
        if (peak.start < query.end && peak.end > query.start
                && Math.min(peak.end, query.end) - Math.max(peak.start, query.start) >= minimum) {
            found.count++;
            if (found.first == null || node.entry.ordinal < found.first.ordinal) found.first = node.entry;
        }
        if (node.right != null && peak.start < query.end) search(node.right, query, minimum, budget, found);
    }
    private static void literal(String value) {
        if (value == null || value.isEmpty() || value.chars().anyMatch(c -> c == 0 || c == '\t' || c == '\r' || c == '\n'))
            throw new IllegalArgumentException("Require literal nonempty identifier without record delimiters");
    }
}
