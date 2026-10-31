/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Variant grouping/pairing and allele classification are derived from HTSlib 1.9
 * bcf_sr_sort.c and vcf.c. Copyright (C) 2017 Genome Research Ltd.,
 * author Petr Danecek; Copyright (C) 2012, 2013 Broad Institute;
 * Copyright (C) 2012-2017 Genome Research Ltd.; portions Copyright (C) 2014
 * Intel Corporation. The vcf.c author is Heng Li.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Bounded original Eagle 2.4.1 reference/target/exclusion pairing at one locus.
 * The original linked HTSlib 1.9 grouping, unsigned priority scores and stable
 * same-position output ordering are retained. REF/ALT comparison and the later
 * Eagle filters are separate operations; an HTS pair need not be phase eligible.
 * Callers must supply all records at one identical literal contig and position,
 * in original file order, with reference, target and exclusion roles fixed.
 * This class neither phases GT nor establishes assembly or reference eligibility.
 *
 * <p>Duplicate REF/ALT keys in one role are an explicit stronger refusal. They
 * require reviewed resolution before admission, rather than silently selecting
 * one record. Allele strings must be printable ASCII so C byte classification
 * and case-folding have an unambiguous source counterpart. Complete-file EOF,
 * genotype validation, map admission and immutable input integrity belong to
 * the surrounding workflow.</p>
 */
public final class EagleReferenceJoin {
    private EagleReferenceJoin() { }
    private static final int REF = 1, SNP = 2, INDEL = 4, OTHER = 8;
    private static final long EXACT = 0xffffffffL;

    /** Complete input, character and algorithmic work capacities for one locus. */
    public record Limits(int maximumRecords, long maximumCharacters, long maximumComparisons) {
        public Limits {
            if (maximumRecords < 1 || maximumRecords > 1_000_000 || maximumCharacters < 1 || maximumComparisons < 1)
                throw new IllegalArgumentException("Invalid complete Eagle locus capacities");
        }
        /** @return bounded defaults; excessive loci are refused, never truncated */
        public static Limits defaults() { return new Limits(10000, 4194304, 10000000); }
    }

    /** Original role-specific row identity and declared allele strings. */
    public record Marker(long ordinal, String id, String reference, String alternate) {
        public Marker {
            if (ordinal < 1 || id == null || id.isEmpty() || id.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)))
                throw new IllegalArgumentException("Require a positive original row ordinal and exact marker ID");
            if ((long)id.length() + (reference == null ? 0 : reference.length()) + (alternate == null ? 0 : alternate.length()) > 16777216)
                throw new IllegalArgumentException("Original marker exceeds the supported 16 Mi-character VCF line capacity");
            allele(reference); if (reference.equals(".") || reference.indexOf(',') >= 0) throw new IllegalArgumentException("Invalid original REF declaration");
            allele(alternate);
            if (!alternate.equals(".")) {
                var seen = new HashSet<String>();
                String[] alternates = alternate.split(",", 65537);
                if (alternates.length > 65536) throw new IllegalArgumentException("Original alternate-allele capacity exceeded");
                for (String alt : alternates) {
                    allele(alt);
                    if (alt.equals(".") || alt.equals(reference) || !seen.add(alt)) throw new IllegalArgumentException("Invalid or duplicate original ALT declaration");
                }
            }
        }
        /** @return original alternate-allele count; a lone dot means none */
        public int alternateCount() { return alternate.equals(".") ? 0 : 1 + (int)alternate.chars().filter(c -> c == ',').count(); }
        private String key() {
            if (alternate.equals(".")) return reference + ">.";
            return String.join(",", Arrays.stream(alternate.split(",", -1)).map(alt -> reference + ">" + alt).toList());
        }
    }

    /** One synchronized HTS event; absent roles are null. */
    public record Event(Marker reference, Marker target, Marker exclusion) {
        public Event { if (reference == null && target == null && exclusion == null) throw new IllegalArgumentException("Empty original synchronized event"); }
    }
    /** Original synchronized events, including reference-only and exclusion-only records. */
    public record Result(List<Event> events, int inputRecords, int distinctVariants, long comparisons) {
        public Result { events = List.copyOf(events); }
    }
    /** Original Eagle filter, in its source execution order. */
    public enum Reason { EXCLUDED_BY_SITE, TARGET_ONLY, REFERENCE_ONLY, MULTIALLELIC_TARGET,
        MONOMORPHIC_REFERENCE, MULTIALLELIC_REFERENCE, ALLELE_MISMATCH, MATCHED }
    /** Expected target allele representation after original filtering or monomorphic expansion. */
    public record Decision(Reason reason, Marker expectedTarget, boolean referenceAltSwap, boolean monomorphicTargetExpanded) {
        public Decision { Objects.requireNonNull(reason, "reason"); }
        /** @return whether the original event participates in phasing */
        public boolean phased() { return reason == Reason.MATCHED; }
    }

    /**
     * Pair complete original same-position records in HTS reader order.
     * @param reference all reference records at this literal locus
     * @param target all target records at this literal locus
     * @param exclusion all optional exclusion records at this literal locus
     * @param allowReferenceAltSwap original --allowRefAltSwap; selects BOTH_REF rather than EXACT pairing
     * @param limits complete locus capacities
     * @return complete source-ordered synchronized events
     */
    public static Result join(List<Marker> reference, List<Marker> target, List<Marker> exclusion,
            boolean allowReferenceAltSwap, Limits limits) {
        return join(reference, target, exclusion, allowReferenceAltSwap, limits, () -> { });
    }

    /**
     * Pair complete same-position records with a caller cancellation check.
     * @param reference original reference-role order
     * @param target original target-role order
     * @param exclusion original exclusion-role order
     * @param allowReferenceAltSwap original source pairing flag
     * @param limits complete locus capacities
     * @param check caller cancellation or integrity check
     * @return complete source-ordered synchronized events
     */
    public static Result join(List<Marker> reference, List<Marker> target, List<Marker> exclusion,
            boolean allowReferenceAltSwap, Limits limits, Runnable check) {
        Objects.requireNonNull(limits, "limits"); Objects.requireNonNull(check, "check"); check.run();
        if ((long)reference.size() + target.size() + exclusion.size() > limits.maximumRecords())
            throw new IllegalArgumentException("Complete Eagle same-position record capacity exceeded");
        var roles = List.of(List.copyOf(reference), List.copyOf(target), List.copyOf(exclusion));
        var variants = new LinkedHashMap<String,Variant>(); var groups = new LinkedHashMap<String,Integer>();
        long characters = 0; int records = 0;
        for (int role = 0; role < roles.size(); role++) {
            var seen = new HashSet<String>(); var ordinals = new HashSet<Long>(); var group = new ArrayList<String>();
            for (Marker marker : roles.get(role)) {
                check.run(); Objects.requireNonNull(marker, "marker");
                if (records == limits.maximumRecords()) throw new IllegalArgumentException("Complete Eagle same-position record capacity exceeded");
                records++; long expanded = marker.alternateCount() == 0 ? marker.reference().length() + 2L
                        : marker.alternateCount() * (marker.reference().length() + 1L) + marker.alternate().length();
                long length = marker.id().length() + (long)marker.reference().length() + marker.alternate().length() + 2 * expanded + 1;
                if (length > limits.maximumCharacters() - characters) throw new IllegalArgumentException("Complete Eagle same-position character capacity exceeded");
                characters += length;
                String key = marker.key();
                if (!seen.add(key) || !ordinals.add(marker.ordinal())) throw new IllegalArgumentException("Duplicate allele key or original ordinal within an Eagle locus role; resolve it explicitly before admission");
                Variant variant = variants.get(key);
                if (variant == null) { variant = new Variant(key, marker); variants.put(key, variant); }
                variant.records[role] = marker; variant.count++; group.add(key);
            }
            if (!group.isEmpty()) {
                group.sort(String::compareTo); String groupKey = String.join(";", group);
                int bit = 1 << groups.computeIfAbsent(groupKey, ignored -> groups.size());
                for (String key : group) variants.get(key).mask |= bit;
            }
        }
        var sets = new ArrayList<VariantSet>();
        for (Variant variant : variants.values()) sets.add(new VariantSet(variant));
        var events = new ArrayList<Event>(); var work = new Work(limits.maximumComparisons(), check);
        while (!sets.isEmpty()) {
            check.run(); int maximum = 0;
            for (int i = 1; i < sets.size(); i++) { work.tick(); if (sets.get(maximum).count < sets.get(i).count) maximum = i; }
            int partner = -1; long best = 0;
            for (int i = 0; i < sets.size(); i++) {
                work.tick(); if ((sets.get(maximum).mask & sets.get(i).mask) != 0) continue;
                long score = score(sets.get(maximum), sets.get(i), allowReferenceAltSwap, work);
                if (score > best) { best = score; partner = i; }
            }
            if (partner != -1) {
                int low = Math.min(maximum, partner), high = Math.max(maximum, partner);
                var a = sets.get(low); var b = sets.remove(high); a.variants.addAll(b.variants); a.mask |= b.mask; a.count += b.count;
                continue;
            }
            Marker[] paired = new Marker[3];
            for (Variant variant : sets.remove(maximum).variants) for (int role = 0; role < paired.length; role++) if (variant.records[role] != null) {
                if (paired[role] != null) throw new IllegalArgumentException("Ambiguous source group would overwrite a role; explicit locus resolution required");
                paired[role] = variant.records[role];
            }
            events.add(new Event(paired[0], paired[1], paired[2]));
        }
        check.run(); return new Result(events, records, variants.size(), work.comparisons);
    }

    /**
     * Apply original Eagle filters after the HTS event has been selected.
     * Monomorphic target expansion occurs before a multiallelic-reference
     * rejection and may replace the target REF as well as ALT. An exclusion
     * event precedes every such transformation. This is source behavior, not
     * an assertion that the supplied reference is biologically suitable.
     * @param event admitted synchronized event
     * @param allowReferenceAltSwap original explicit SNP-swap flag
     * @return exact target representation and filter disposition
     */
    public static Decision decide(Event event, boolean allowReferenceAltSwap) {
        Objects.requireNonNull(event, "event"); Marker target = event.target(), reference = event.reference();
        if (event.exclusion() != null) return decision(Reason.EXCLUDED_BY_SITE, target);
        if (reference == null) return decision(Reason.TARGET_ONLY, target);
        if (target == null) return decision(Reason.REFERENCE_ONLY, null);
        if (target.alternateCount() > 1) return decision(Reason.MULTIALLELIC_TARGET, target);
        if (reference.alternateCount() == 0) return decision(Reason.MONOMORPHIC_REFERENCE, target);
        boolean expanded = target.alternateCount() == 0;
        Marker expected = expanded ? new Marker(target.ordinal(), target.id(), reference.reference(), reference.alternate()) : target;
        if (reference.alternateCount() > 1) return new Decision(Reason.MULTIALLELIC_REFERENCE, expected, false, expanded);
        if (expected.reference().equals(reference.reference()) && expected.alternate().equals(reference.alternate()))
            return new Decision(Reason.MATCHED, expected, false, expanded);
        if (allowReferenceAltSwap && sourceSnp(expected) && expected.reference().equals(reference.alternate()) && expected.alternate().equals(reference.reference()))
            return new Decision(Reason.MATCHED, expected, true, expanded);
        return new Decision(Reason.ALLELE_MISMATCH, expected, false, expanded);
    }

    /**
     * Original HTSlib 1.9 variant-type bits used before synchronized pairing.
     * @param marker original declared alleles
     * @return REF=0, SNP=1, MNP=2, INDEL=4, OTHER=8, BND=16, combined with bitwise OR
     */
    public static int sourceVariantTypes(Marker marker) {
        Objects.requireNonNull(marker, "marker"); int type = 0;
        if (!marker.alternate().equals(".")) for (String alt : marker.alternate().split(",", -1)) type |= classify(marker.reference(), alt);
        return type;
    }

    private static Decision decision(Reason reason, Marker target) { return new Decision(reason, target, false, false); }
    private static boolean sourceSnp(Marker marker) {
        if (!snpAllele(marker.reference())) return false;
        for (String alt : marker.alternate().split(",", -1)) if (!snpAllele(alt)) return false;
        return true;
    }
    private static boolean snpAllele(String allele) { return allele.length() == 1 || allele.startsWith("<X>") || allele.startsWith("<*>"); }
    private static int classify(String ref, String alt) {
        if (ref.length() == 1 && alt.length() == 1) return alt.equals(".") || ref.equals(alt) || alt.equals("X") || alt.equals("*") ? 0 : 1;
        if (alt.charAt(0) == '<') return alt.startsWith("<X>") || alt.startsWith("<*>") ? 0 : 8;
        int r = 0, a = 0;
        while (r < ref.length() && a < alt.length() && upper(ref.charAt(r)) == upper(alt.charAt(a))) { r++; a++; }
        if (a < alt.length() && r == ref.length()) return alt.charAt(a) == ']' || alt.charAt(a) == '[' ? 16 : 4;
        if (r < ref.length() && a == alt.length()) return 4;
        if (r == ref.length() && a == alt.length()) return 0;
        int re = ref.length() - 1, ae = alt.length() - 1;
        while (re > r && ae > a && upper(ref.charAt(re)) == upper(alt.charAt(ae))) { re--; ae--; }
        if (ae == a) return re == r ? 1 : upper(ref.charAt(re)) == upper(alt.charAt(ae)) ? 4 : 8;
        if (re == r) return upper(ref.charAt(re)) == upper(alt.charAt(ae)) ? 4 : 8;
        return re - r == ae - a ? 2 : 8;
    }
    private static char upper(char c) { return c >= 'a' && c <= 'z' ? (char)(c - 'a' + 'A') : c; }
    private static void allele(String value) {
        if (value == null || value.isEmpty() || value.chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("Require nonempty printable ASCII original alleles for source byte semantics");
    }
    private static final class Variant {
        final String key; final int type, alternates; final Marker[] records = new Marker[3];
        int count, mask;
        Variant(String key, Marker marker) {
            this.key = key; alternates = marker.alternateCount(); int raw = sourceVariantTypes(marker);
            type = raw == 0 ? REF : ((raw & 3) != 0 ? SNP : 0) | ((raw & 4) != 0 ? INDEL : 0) | ((raw & 8) != 0 ? OTHER : 0);
        }
    }
    private static final class VariantSet {
        final List<Variant> variants = new ArrayList<>(); int count, mask;
        VariantSet(Variant variant) { variants.add(variant); count = variant.count; mask = variant.mask; }
    }
    private static final class Work {
        final long maximum; final Runnable check; long comparisons;
        Work(long maximum, Runnable check) { this.maximum = maximum; this.check = check; }
        void tick() { check.run(); if (comparisons == maximum) throw new IllegalArgumentException("Complete Eagle same-position pairing work capacity exceeded"); comparisons++; }
        boolean equal(String a, String b, boolean fold) {
            tick(); if (a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++) { tick(); if ((fold ? upper(a.charAt(i)) : a.charAt(i)) != (fold ? upper(b.charAt(i)) : b.charAt(i))) return false; }
            return true;
        }
    }
    private static long score(VariantSet a, VariantSet b, boolean swap, Work work) {
        int minimum = Integer.MAX_VALUE;
        for (Variant x : a.variants) for (Variant y : b.variants) {
            work.tick();
            if (!swap) {
                if (x.type != y.type) continue;
                if (work.equal(x.key, y.key, false) || x.alternates == y.alternates && x.key.length() == y.key.length() && sameAlleleSet(x.key, y.key, true, work)) return EXACT;
                continue;
            }
            if (x.type == y.type && work.equal(x.key, y.key, false)) return EXACT;
            if ((x.type & y.type) != 0 && sameAlleleSet(x.key, y.key, false, work)) return EXACT;
            int priority = priority(x.type, y.type); if (priority == 0) return 0;
            minimum = Math.min(minimum, priority);
        }
        if (!swap) return 0;
        if (minimum == Integer.MAX_VALUE) throw new IllegalStateException("Empty original variant set");
        return (1L << (28 + minimum)) + a.count + b.count;
    }
    private static int priority(int a, int b) {
        int maximum = 0;
        for (int x : new int[]{REF, SNP, INDEL, OTHER}) if ((a & x) != 0) for (int y : new int[]{REF, SNP, INDEL, OTHER}) if ((b & y) != 0) {
            int value = x == y && (x == SNP || x == INDEL) ? 3 : (x == REF && (y == SNP || y == INDEL) || y == REF && (x == SNP || x == INDEL)) ? 2 : 0;
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }
    private static boolean sameAlleleSet(String a, String b, boolean all, Work work) {
        for (String x : a.split(",", -1)) {
            boolean found = false;
            for (String y : b.split(",", -1)) if (work.equal(x, y, true)) { found = true; break; }
            if (!all && found) return true;
            if (all && !found) return false;
        }
        return all;
    }
}
