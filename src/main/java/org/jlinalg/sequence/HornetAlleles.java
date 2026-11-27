/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Derived from TheFraserLab/Hornet, Apache-2.0; see META-INF/licenses/Hornet-NOTICE.txt. */
package org.jlinalg.sequence;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.TextCigarCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Native allele-sequence component of Hornet e642ac0e. Coordinates are zero based;
 * sequences are in BAM alignment orientation, including for reverse-strand reads.
 * This is not the standalone WASP algorithm or a remapping/filtering pipeline.
 *
 * <p>The source's limits, counters, ordering, duplicate-position replacement,
 * joined-allele indel test and unsupported phased single-read mode are preserved.
 * Caller capacity failures throw; they never become biological read dispositions.
 */
public final class HornetAlleles {
    private HornetAlleles() { }

    public static final String REVISION = "e642ac0ee9ed6625ce415305b9ced4d686403c79";
    public static final int SOURCE_MAX_SEQUENCES = 32;
    public static final int NATIVE_MAX_VARIANTS = 1_000_000;
    public static final int NATIVE_MAX_READ_BASES = 1_000_000;
    public static final int NATIVE_MAX_GENERATED_SEQUENCES = 4096;
    public static final long NATIVE_MAX_SEQUENCE_CHARACTERS = 16_777_216;

    /** Raw source-format alleles, before the original ref+alt concatenation. */
    public record Variant(String chromosome, int position, String reference, String alternate) {
        public Variant {
            token(chromosome, "chromosome");
            if (position < 0) throw new IllegalArgumentException("Hornet position must be zero based and nonnegative");
            allele(reference); allele(alternate);
        }
    }

    /** Pysam-compatible read sequence and CIGAR; no reverse complementation here. */
    public record Read(String chromosome, int start, String sequence, String cigar, boolean unmapped) {
        public Read {
            token(chromosome, "chromosome");
            if (start < 0) throw new IllegalArgumentException("Hornet read start must be zero based and nonnegative");
            if (sequence == null || sequence.isEmpty() || sequence.length() > NATIVE_MAX_READ_BASES || !sequence.matches("[A-Z=]+"))
                throw new IllegalArgumentException("Require a nonempty uppercase BAM-orientation sequence");
            if (cigar == null || cigar.isEmpty()) throw new IllegalArgumentException("Require an explicit CIGAR");
            if (!unmapped) alignedPositions(start, sequence, cigar);
        }
    }

    /** Original sequence first, followed by swaps; an empty list means source discard. */
    public record Result(List<List<String>> sequences, Map<String,Long> dispositions) {
        public Result {
            sequences = sequences.stream().map(List::copyOf).toList();
            dispositions = Collections.unmodifiableMap(new TreeMap<>(dispositions));
        }
    }

    /** Immutable original-loader semantics: the last row at a position replaces earlier rows. */
    public static final class Variants {
        private final Map<String,Map<Integer,String>> chromosomes;

        public Variants(List<Variant> rows) {
            Objects.requireNonNull(rows, "variants");
            if (rows.size() > NATIVE_MAX_VARIANTS) throw new IllegalArgumentException("Native Hornet variant capacity exceeded; no result produced");
            var data = new LinkedHashMap<String,Map<Integer,String>>();
            for (Variant row : rows) {
                check();
                Objects.requireNonNull(row, "variant");
                data.computeIfAbsent(row.chromosome(), k -> new LinkedHashMap<>())
                        .put(row.position(), row.reference() + row.alternate());
            }
            var frozen = new LinkedHashMap<String,Map<Integer,String>>();
            data.forEach((chrom, values) -> frozen.put(chrom, Collections.unmodifiableMap(values)));
            chromosomes = Collections.unmodifiableMap(frozen);
        }

        private Map<Integer,String> chromosome(String name) {
            return chromosomes.getOrDefault(name, Map.of());
        }
    }

    /** Source get_read_seqs. Phased single-read mode raises, including for a read with no SNPs. */
    public static Result single(Read read, Variants variants, boolean phased) {
        Objects.requireNonNull(read); Objects.requireNonNull(variants);
        if (phased) throw new UnsupportedOperationException("Hornet phased single-read mode is not implemented in the original source");
        var counts = new LinkedHashMap<String,Long>();
        var sequences = new ArrayList<String>(); sequences.add(read.sequence());
        var alleles = variants.chromosome(read.chromosome());
        // Unlike paired mode, the original single-read function does not inspect the flag.
        var aligned = read.cigar().equals("*") && read.unmapped() ? Map.<Integer,Integer>of()
                : alignedPositions(read.start(), read.sequence(), read.cigar());
        for (var pair : aligned.entrySet()) {
            check();
            String alternatives = alleles.get(pair.getKey());
            if (indel(alternatives)) { increment(counts, "toss_indel"); return result(counts, List.of()); }
            // The original single-read check occurs before processing each aligned base,
            // including bases without variants. A final-base sixth SNP can therefore yield 64.
            if (sequences.size() > SOURCE_MAX_SEQUENCES) {
                increment(counts, "toss_manysnps"); return result(counts, List.of());
            }
            if (alternatives == null) continue;
            increment(counts, "total_snps");
            int position = pair.getValue(); char original = read.sequence().charAt(position);
            if (alternatives.indexOf(original) >= 0) {
                increment(counts, "ref_match");
                for (char allele : alternatives.toCharArray()) {
                    if (allele == original) continue;
                    // Copy separately for EACH alternate character, as in list(seqs) in the source.
                    for (String sequence : List.copyOf(sequences)) {
                        capacity(sequences.size() + 1L, read.sequence().length());
                        sequences.add(replace(sequence, position, allele));
                    }
                }
            } else increment(counts, "no_match");
        }
        increment(counts, sequences.size() == 1 ? "no_snps" : "has_snps");
        return result(counts, sequences);
    }

    /** Source get_dual_read_seqs; overlapping mates share substitutions. */
    public static Result paired(Read first, Read second, Variants variants, boolean phased) {
        Objects.requireNonNull(first); Objects.requireNonNull(second); Objects.requireNonNull(variants);
        var counts = new LinkedHashMap<String,Long>();
        if (first.unmapped() || second.unmapped()) {
            increment(counts, "unmapped read"); return pairResult(counts, List.of(), List.of());
        }
        if (!first.chromosome().equals(second.chromosome()))
            throw new IllegalArgumentException("Cross-chromosome mates are outside this native component's admitted domain; no Hornet result produced");
        var alleles = variants.chromosome(first.chromosome());
        var sites = new LinkedHashMap<Integer,String>();
        var positions = new LinkedHashMap<Integer,int[]>();
        for (int mate = 0; mate < 2; mate++) {
            Read read = mate == 0 ? first : second;
            for (var pair : alignedPositions(read.start(), read.sequence(), read.cigar()).entrySet()) {
                check();
                String alternatives = alleles.get(pair.getKey());
                if (indel(alternatives)) {
                    increment(counts, "toss_indel"); return pairResult(counts, List.of(), List.of());
                }
                if (alternatives != null) {
                    sites.put(pair.getKey(), alternatives);
                    positions.computeIfAbsent(pair.getKey(), p -> new int[]{-1, -1})[mate] = pair.getValue();
                }
            }
        }
        long product = 1;
        for (String value : sites.values()) {
            product *= value.length();
            if (product > SOURCE_MAX_SEQUENCES) {
                increment(counts, "toss_manysnps"); return pairResult(counts, List.of(), List.of());
            }
        }
        if (sites.isEmpty()) {
            increment(counts, "no_snps");
            return pairResult(counts, List.of(first.sequence()), List.of(second.sequence()));
        }
        // This test in the original inspects only the first encountered variant.
        if (sites.values().iterator().next().length() > 2) {
            increment(counts, "toss_manysnps"); return pairResult(counts, List.of(), List.of());
        }
        if (phased) return phased(first, second, sites, positions, counts);
        var one = new ArrayList<String>(); one.add(first.sequence());
        var two = new ArrayList<String>(); two.add(second.sequence());
        for (var site : sites.entrySet()) {
            check();
            increment(counts, "total_snps");
            int[] pos = positions.get(site.getKey());
            if (pos[0] >= 0 && pos[1] >= 0 && first.sequence().charAt(pos[0]) != second.sequence().charAt(pos[1])) {
                increment(counts, "toss_anomalous_phase"); return pairResult(counts, List.of(), List.of());
            }
            char original = pos[0] >= 0 ? first.sequence().charAt(pos[0]) : second.sequence().charAt(pos[1]);
            increment(counts, site.getValue().indexOf(original) >= 0 ? "ref_match" : "no_match");
            var addedOne = new ArrayList<String>(); var addedTwo = new ArrayList<String>();
            for (char alternate : site.getValue().toCharArray()) {
                if (alternate == original) continue;
                for (int index = 0; index < one.size(); index++) {
                    capacity(one.size() + addedOne.size() + 1L, (long)first.sequence().length() + second.sequence().length());
                    addedOne.add(pos[0] < 0 ? one.get(index) : replace(one.get(index), pos[0], alternate));
                    addedTwo.add(pos[1] < 0 ? two.get(index) : replace(two.get(index), pos[1], alternate));
                }
            }
            one.addAll(addedOne); two.addAll(addedTwo);
        }
        increment(counts, one.size() == 1 ? "no_snps" : "has_snps");
        return pairResult(counts, one, two);
    }

    private static Result phased(Read first, Read second, Map<Integer,String> sites,
            Map<Integer,int[]> positions, Map<String,Long> counts) {
        String[] original = {first.sequence(), second.sequence()};
        var swapped = new char[][]{first.sequence().toCharArray(), second.sequence().toCharArray()};
        for (int reference : new TreeMap<>(sites).keySet()) {
            check();
            int[] pos = positions.get(reference); String alleles = sites.get(reference);
            for (int mate = 0; mate < 2; mate++) {
                if (pos[mate] < 0) continue;
                int isAlt = alleles.indexOf(original[mate].charAt(pos[mate]));
                if (isAlt == -1) {
                    // The source evaluates Counter['non_refalt_base'] WITHOUT += 1.
                    // Python Counter lookup leaves the key absent; do not invent a count.
                    return pairResult(counts, List.of(), List.of());
                }
                int index = 1 - isAlt;
                if (index < 0) index += alleles.length(); // Python negative indexing, unchanged.
                swapped[mate][pos[mate]] = alleles.charAt(index);
            }
            if (pos[0] >= 0 && pos[1] >= 0 && original[0].charAt(pos[0]) != original[1].charAt(pos[1])) {
                increment(counts, "toss_anomalous_phase"); return pairResult(counts, List.of(), List.of());
            }
        }
        increment(counts, "has_snps");
        return pairResult(counts, List.of(original[0], new String(swapped[0])), List.of(original[1], new String(swapped[1])));
    }

    /** Source reverse_complement: only uppercase A/T/C/G are complemented. */
    public static String reverseComplement(String sequence) {
        Objects.requireNonNull(sequence);
        var result = new StringBuilder(sequence.length());
        for (int i = sequence.length() - 1; i >= 0; i--) result.append(switch (sequence.charAt(i)) {
            case 'A' -> 'T'; case 'T' -> 'A'; case 'C' -> 'G'; case 'G' -> 'C'; default -> sequence.charAt(i);
        });
        return result.toString();
    }

    private static boolean indel(String alleles) {
        // Original get_indels iterates CHARACTERS of the concatenated string.
        // Consequently its len(i)>1 branch never detects a multi-base allele.
        return alleles != null && alleles.indexOf('-') >= 0;
    }

    private static LinkedHashMap<Integer,Integer> alignedPositions(int start, String sequence, String cigar) {
        var result = new LinkedHashMap<Integer,Integer>();
        var decoded = TextCigarCodec.decode(cigar);
        if (decoded.isEmpty()) throw new IllegalArgumentException("Mapped Hornet read requires a nonempty CIGAR");
        int query = 0, reference = start;
        for (CigarElement element : decoded.getCigarElements()) {
            int count = element.getLength(); CigarOperator operator = element.getOperator();
            if (count <= 0) throw new IllegalArgumentException("Hornet CIGAR has a nonpositive operation");
            if (operator == CigarOperator.P) throw new IllegalArgumentException("Padded CIGAR is outside the admitted native Hornet domain; original pysam 0.21 padding can fail or shift query positions");
            if (operator.consumesReadBases() && operator.consumesReferenceBases()) {
                for (int i = 0; i < count; i++) {
                    if ((i & 1023) == 0) check();
                    if ((long)query + i >= sequence.length()) throw new IllegalArgumentException("CIGAR exceeds Hornet read sequence");
                    result.put(Math.addExact(reference, i), query + i);
                }
            }
            if (operator.consumesReadBases()) query = Math.addExact(query, count);
            if (operator.consumesReferenceBases()) reference = Math.addExact(reference, count);
        }
        if (query != sequence.length()) throw new IllegalArgumentException("CIGAR/query length mismatch; no Hornet sequence generated");
        return result;
    }

    private static String replace(String sequence, int position, char allele) {
        return sequence.substring(0, position) + allele + sequence.substring(position + 1);
    }
    private static Result result(Map<String,Long> counts, List<String> sequences) {
        return new Result(List.of(sequences), counts);
    }
    private static Result pairResult(Map<String,Long> counts, List<String> one, List<String> two) {
        return new Result(List.of(one, two), counts);
    }
    private static void increment(Map<String,Long> counts, String key) { counts.merge(key, 1L, Math::addExact); }
    private static void capacity(long sequences, long bases) {
        if (sequences > NATIVE_MAX_GENERATED_SEQUENCES || sequences * bases > NATIVE_MAX_SEQUENCE_CHARACTERS)
            throw new IllegalArgumentException("Native Hornet generation capacity exceeded; no biological disposition or complete result produced");
    }
    private static void check() {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Hornet sequence generation cancelled; no complete result produced");
    }
    private static void token(String value, String label) {
        if (value == null || value.isEmpty() || value.chars().anyMatch(c -> c <= 32 || c >= 127))
            throw new IllegalArgumentException("Require a nonempty ASCII Hornet " + label);
    }
    private static void allele(String value) { token(value, "allele"); }
}
