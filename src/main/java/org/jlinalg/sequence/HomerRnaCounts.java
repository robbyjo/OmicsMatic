/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Independent interval-prefix audit for original HOMER 5.1 RNA exon counts.
 * Tags are unshifted five-prime anchors, not fragment or splice-block overlaps.
 * Literal inclusive counts remain separate from the original equal-start break
 * behavior. Exact comparator ties retain a family of possible boundary owners;
 * no arbitrary Java tie order is presented as the original C++ order.
 * This class does not parse references, build tags, condense transcripts or run
 * analyzeRepeats.pl, and does not make a complete RNA adapter available.
 */
public final class HomerRnaCounts {
    private HomerRnaCounts() { }

    /** Strand selection relative to each exon, as in getPeakTags. */
    public enum Strand { BOTH, SAME, OPPOSITE }

    /**
     * One named original exon part on the caller's single chromosome. The source
     * can emit an empty part with end=start-1 at a repeated feature breakpoint;
     * this shape is retained explicitly, rather than swapped or clipped.
     */
    public record Exon(String id, int start1, int end1, char strand) {
        public Exon {
            if (id == null || id.isBlank() || id.chars().anyMatch(c -> Character.isWhitespace(c)
                    || Character.isISOControl(c)) || start1 < 1 || start1 > 1_000_000_000
                    || end1 < start1 - 1 || end1 > 1_000_000_000 || strand != '+' && strand != '-') {
                throw new IllegalArgumentException("Require a named inclusive exon and explicit strand");
            }
        }
    }

    /**
     * Literal count and the source count before assigning any tied start tag.
     * A non-null familyId refers to the one boundary family which may own tags.
     * Counts and boundary masses are exact half units, not numerical tolerances.
     */
    public record Count(Exon exon, long literalHalfUnits, long sourceInteriorHalfUnits,
                        String familyId, long possibleBoundaryHalfUnits) {
        /** Raw interior count before enforcing the complete boundary family. */
        public double interiorTags() { return sourceInteriorHalfUnits / 2.0; }
        /** Highest source-compatible raw count. */
        public double maximumSourceTags() {
            return Math.addExact(sourceInteriorHalfUnits, possibleBoundaryHalfUnits) / 2.0;
        }
    }

    /**
     * Exactly one member receives the boundary mass, unless the comparator-first
     * eligible part is empty. Empty parts are eligible only while an earlier
     * interval's prefix maximum end spans this start. Otherwise the source skips
     * them before its inner scan. discardedHalfUnits records this separately.
     */
    public record BoundaryFamily(String id, List<String> members, long halfUnits,
                                 long discardedHalfUnits) {
        public BoundaryFamily { members = List.copyOf(members); }
    }

    /** Complete exon population, with independent literal and source-compatible counts. */
    public record Counts(List<Count> exons, List<BoundaryFamily> boundaryFamilies) {
        public Counts {
            exons = List.copyOf(exons);
            boundaryFamilies = List.copyOf(boundaryFamilies);
        }
    }

    /** Caller deadline, cancellation or immutable-input check. */
    @FunctionalInterface public interface Check { void run() throws IOException; }

    private static final class Prefix {
        private final int[] positions;
        private final long[] mass;
        Prefix(List<HomerCoverage.Tag> tags, char strand) {
            int size = 0;
            for (var tag : tags) if (strand == '*' || tag.strand() == strand) size++;
            positions = new int[size];
            mass = new long[size + 1];
            int i = 0;
            for (var tag : tags) {
                if (strand != '*' && tag.strand() != strand) continue;
                positions[i] = tag.position1();
                mass[i + 1] = Math.addExact(mass[i], (long)(tag.weight() * 2.0));
                i++;
            }
        }
        int lower(int value) {
            int lo = 0, hi = positions.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (positions[mid] < value) lo = mid + 1; else hi = mid;
            }
            return lo;
        }
        long range(int start, int end) {
            if (end < start) return 0;
            return mass[lower(end + 1)] - mass[lower(start)];
        }
    }

    /**
     * Count all overlapping exon parts without copying the source's tag-by-exon
     * scan. Prefix queries establish literal interior and boundary populations.
     * Comparator-minimal compatible intervals own start tags; exact geometry ties
     * retain all candidate owners and require a complete-family audit.
     * @param tags complete position-ordered serialized half-unit tags; may be empty
     * @param exons complete exon parts for one chromosome, in any order
     * @param strand relative strand selection
     * @param referenceLength exact dictionary length
     * @param maximumTags memory admission limit, at most five million
     * @param maximumExons memory admission limit, at most one million
     * @param check caller cancellation/deadline check
     * @return complete counts and boundary ownership families
     * @throws IOException if caller check or supported exact-mass capacity fails
     */
    public static Counts count(List<HomerCoverage.Tag> tags, List<Exon> exons, Strand strand,
                               long referenceLength, int maximumTags, int maximumExons,
                               Check check) throws IOException {
        if (tags == null || exons == null || strand == null || check == null
                || referenceLength < 1 || referenceLength > 1_000_000_000
                || maximumTags < 1 || maximumTags > 5_000_000 || tags.size() > maximumTags
                || maximumExons < 1 || maximumExons > 1_000_000 || exons.size() > maximumExons) {
            throw new IllegalArgumentException("Require bounded complete tags, exons and reference");
        }
        check.run();
        long half = 0;
        int previous = 0;
        for (int i = 0; i < tags.size(); i++) {
            if ((i & 4095) == 0) check.run();
            var tag = tags.get(i);
            double mass = tag.weight() * 2.0;
            if (tag.position1() < previous || tag.position1() > referenceLength || Math.rint(mass) != mass) {
                throw new IllegalArgumentException("Require ordered in-reference half-unit tag weights");
            }
            previous = tag.position1();
            half = Math.addExact(half, (long)mass);
            if (half > (1L << 52)) throw new IOException("Half-unit mass exceeds exact double audit capacity");
        }
        var ids = new HashSet<String>();
        var sorted = new ArrayList<Exon>(exons.size());
        for (int i = 0; i < exons.size(); i++) {
            if ((i & 4095) == 0) check.run();
            var exon = exons.get(i);
            if (exon == null || exon.start1() > referenceLength || exon.end1() > referenceLength
                    || !ids.add(exon.id())) {
                throw new IllegalArgumentException("Require unique exon-part identities within the reference");
            }
            sorted.add(exon);
        }
        sorted.sort(Comparator.comparingInt(Exon::start1).thenComparingInt(Exon::end1)
                .thenComparingInt(e -> e.strand() == '+' ? 0 : 1));
        var both = new Prefix(tags, '*');
        var plus = new Prefix(tags, '+');
        var minus = new Prefix(tags, '-');
        var familyByMember = new HashMap<String, BoundaryFamily>();
        var families = new ArrayList<BoundaryFamily>();
        int start = 0;
        int previousMaximumEnd = 0;
        while (start < sorted.size()) {
            check.run();
            int end = start + 1;
            while (end < sorted.size() && sorted.get(end).start1() == sorted.get(start).start1()) end++;
            boolean eligibleEmpty = previousMaximumEnd >= sorted.get(start).start1();
            if (strand == Strand.BOTH) {
                family(sorted, start, end, '*', eligibleEmpty, both, families, familyByMember);
            } else {
                family(sorted, start, end, '+', eligibleEmpty, strand == Strand.SAME ? plus : minus, families, familyByMember);
                family(sorted, start, end, '-', eligibleEmpty, strand == Strand.SAME ? minus : plus, families, familyByMember);
            }
            for (int i = start; i < end; i++) previousMaximumEnd = Math.max(previousMaximumEnd, sorted.get(i).end1());
            start = end;
        }
        var result = new ArrayList<Count>(exons.size());
        for (int i = 0; i < exons.size(); i++) {
            if ((i & 4095) == 0) check.run();
            var exon = exons.get(i);
            var prefix = strand == Strand.BOTH ? both
                    : (exon.strand() == '+') == (strand == Strand.SAME) ? plus : minus;
            long literal = prefix.range(exon.start1(), exon.end1());
            long interior = prefix.range(exon.start1() + 1, exon.end1());
            var family = familyByMember.get(exon.id());
            result.add(new Count(exon, literal, interior, family == null ? null : family.id(),
                    family == null ? 0 : family.halfUnits() - family.discardedHalfUnits()));
        }
        check.run();
        return new Counts(result, families);
    }

    private static void family(List<Exon> exons, int start, int end, char wanted, boolean eligibleEmpty,
                               Prefix prefix, List<BoundaryFamily> families,
                               Map<String, BoundaryFamily> byMember) {
        Exon first = null;
        var members = new ArrayList<String>();
        for (int i = start; i < end; i++) {
            var exon = exons.get(i);
            if (wanted != '*' && exon.strand() != wanted) continue;
            if (!eligibleEmpty && exon.end1() < exon.start1()) continue;
            if (first == null) first = exon;
            if (exon.end1() != first.end1() || exon.strand() != first.strand()) break;
            members.add(exon.id());
        }
        if (first == null) return;
        long half = prefix.range(first.start1(), first.start1());
        var value = new BoundaryFamily(first.start1() + ":" + wanted, members, half,
                first.end1() < first.start1() ? half : 0);
        families.add(value);
        for (String id : members) byMember.put(id, value);
    }

    /**
     * Audit a complete original raw count table, including correlated boundary
     * ownership. Independent per-row bounds alone would allow fabricated split
     * counts or several winners and are insufficient.
     * @param counts complete independent count population
     * @param original original exon ID to raw count, before transcript condensation
     */
    public static void audit(Counts counts, Map<String, Double> original) {
        if (counts == null || original == null || counts.exons().size() != original.size()) {
            throw new IllegalArgumentException("Require the complete original exon count population");
        }
        var residual = new HashMap<String, Long>();
        for (var count : counts.exons()) {
            Double raw = original.get(count.exon().id());
            if (raw == null || !Double.isFinite(raw) || raw < 0 || raw > (1L << 51)
                    || Math.rint(raw * 2.0) != raw * 2.0) {
                throw new IllegalArgumentException("Missing, nonfinite or non-half-unit original exon count");
            }
            long extra = (long)(raw * 2.0) - count.sourceInteriorHalfUnits();
            if (extra != 0 && extra != count.possibleBoundaryHalfUnits()) {
                throw new IllegalArgumentException("Original exon count is outside the complete boundary population");
            }
            residual.put(count.exon().id(), extra);
        }
        for (var family : counts.boundaryFamilies()) {
            long sum = 0;
            for (String id : family.members()) sum = Math.addExact(sum, residual.get(id));
            if (sum != family.halfUnits() - family.discardedHalfUnits()) {
                throw new IllegalArgumentException("Boundary family does not assign its mass to exactly one owner");
            }
        }
    }

    /**
     * Original double-precision -rpkm arithmetic for a valid effective length.
     * The wrapper's effective length uses sum(end-start) and may be averaged
     * across condensed copies. It is not an inclusive length or union length.
     * Zero-length source fallback is deliberately rejected rather than labeled
     * RPKM. The original output header labels this value FPKM.
     * @param raw original raw transcript count after the selected condensation
     * @param wholeDirectoryMass original tagInfo mass, not the exon matrix sum
     * @param sourceEffectiveLength explicit source transcript length of at least one
     * @return value in the original arithmetic order
     */
    public static double rpkm(double raw, double wholeDirectoryMass, double sourceEffectiveLength) {
        if (!Double.isFinite(raw) || raw < 0 || !Double.isFinite(wholeDirectoryMass)
                || wholeDirectoryMass <= 0 || !Double.isFinite(sourceEffectiveLength)
                || sourceEffectiveLength < 1) {
            throw new IllegalArgumentException("Require finite counts, whole-directory mass and positive original length");
        }
        double value = raw * (1_000_000.0 / wholeDirectoryMass);
        value *= 1_000.0 / sourceEffectiveLength;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite original RPKM arithmetic");
        return value;
    }

    /** Original three-decimal printing, without an intermediate decimal rounding. */
    public static String printed(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Require finite nonnegative count");
        return new BigDecimal(value).setScale(3, RoundingMode.HALF_EVEN).toPlainString();
    }
}
