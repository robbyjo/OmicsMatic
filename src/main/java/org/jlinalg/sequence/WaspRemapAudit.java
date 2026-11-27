/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Filter rules derived from bmvdgeijn/WASP, Apache-2.0; see WASP-NOTICE.txt. */
package org.jlinalg.sequence;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Complete native read-retention audit of the original WASP 0.3.4 mapping filter. */
public final class WaspRemapAudit {
    private WaspRemapAudit() { }
    public static final String SOURCE_REVISION = "d3b8447fd7719fffa00b856fd1f27c845554693e";
    public record Limits(int records, long samCharacters) {
        public Limits { if (records < 1 || samCharacters < 1) throw new IllegalArgumentException("Explicit complete BAM census capacities required"); }
    }
    public record Report(Map<String,Long> originalFilterCounters, Map<String,Long> recordPopulations,
            Map<String,String> stageCanonicalRecordSha256, List<String> warnings) {
        public Report {
            originalFilterCounters = Collections.unmodifiableMap(new TreeMap<>(originalFilterCounters));
            recordPopulations = Collections.unmodifiableMap(new TreeMap<>(recordPopulations));
            stageCanonicalRecordSha256 = Collections.unmodifiableMap(new TreeMap<>(stageCanonicalRecordSha256));
            warnings = List.copyOf(warnings);
        }
    }
    private record Name(String original, int first, Integer second, int number, int total) { }
    private record Decision(List<SAMRecord> kept, Map<String,Long> counts, Set<String> keptNames) { }

    /**
     * Reproduce the original filter independently, compare all retained original
     * records, account for every input record, and check the final index population.
     * Final deduplication, if requested, must only remove complete original units.
     */
    public static Report audit(Path input, Path sortedInput, Path immediate, Path candidates, Path remapped,
            Path filtered, Path merged, Path finalBam, boolean paired, boolean deduplicated,
            Limits limits, Runnable check) throws IOException {
        long[] characters = {0};
        List<SAMRecord> original = read(input, limits, characters, check), sorted = read(sortedInput, limits, characters, check), keep = read(immediate, limits, characters, check),
                todo = read(candidates, limits, characters, check), mapped = read(remapped, limits, characters, check),
                actual = read(filtered, limits, characters, check), combined = read(merged, limits, characters, check),
                result = read(finalBam, limits, characters, check);
        var originalPopulation = population(original, check); var immediatePopulation = population(keep, check);
        require(originalPopulation.equals(population(sorted, check)), "Owned BAM sorting changed or omitted an original input record");
        var candidatePopulation = population(todo, check);
        require(subset(immediatePopulation, originalPopulation) && subset(candidatePopulation, originalPopulation), "WASP intermediate BAM changed or fabricated an original record");
        var assigned = plus(immediatePopulation, candidatePopulation);
        require(subset(assigned, originalPopulation), "Immediate-keep and remap populations overlap beyond original multiplicity");
        var grouped = groups(todo, paired, check);
        for (SAMRecord record : mapped) {
            check.run(); Name name = name(record.getReadName());
            require(grouped.containsKey(name.original), "Remapped record has no original WASP candidate: " + name.original);
        }
        Decision decision = filter(todo, mapped, check);
        require(population(decision.kept, check).equals(population(actual, check)), "Complete native WASP remap-filter decision differs from original retained BAM");
        // The original coordinate-only parser omits chromosome identity. Do not
        // repair its output: refuse admission if a retained read exposes that gap.
        var wrongChromosome = new HashSet<String>();
        for (SAMRecord record : mapped) {
            check.run(); Name name = name(record.getReadName());
            if (!decision.keptNames.contains(name.original)) continue;
            String chrom = grouped.get(name.original).get(0).getReferenceName();
            if (!record.getReferenceName().equals(chrom)) wrongChromosome.add(name.original);
        }
        require(wrongChromosome.isEmpty(), "Original WASP retained a coordinate match on a different chromosome; no complete result admitted. Preserve original outputs and investigate: " + wrongChromosome);
        var expectedMerged = plus(immediatePopulation, population(actual, check));
        require(expectedMerged.equals(population(combined, check)), "Merged WASP BAM differs from every immediate and filtered original record");
        var finalPopulation = population(result, check);
        require(deduplicated ? subset(finalPopulation, expectedMerged) : finalPopulation.equals(expectedMerged), "Final WASP BAM changed, added or unexpectedly removed records");
        groups(keep, paired, check); groups(actual, paired, check); groups(result, paired, check);
        if (deduplicated) verifyDuplicateGroups(combined, result, check);
        verifyIndex(finalBam, result, limits, check);
        var counts = new TreeMap<String,Long>();
        counts.put("input_records", (long)original.size()); counts.put("immediate_keep_records", (long)keep.size());
        counts.put("remap_candidate_records", (long)todo.size()); counts.put("omitted_during_original_find_records", (long)original.size() - keep.size() - todo.size());
        counts.put("remapped_alignment_records", (long)mapped.size()); counts.put("filtered_keep_records", (long)actual.size());
        counts.put("discarded_during_original_remap_filter_records", (long)todo.size() - actual.size());
        counts.put("merged_records", (long)combined.size()); counts.put("dedup_removed_records", (long)combined.size() - result.size()); counts.put("final_records", (long)result.size());
        var stageHashes = new TreeMap<String,String>();
        stageHashes.put("original_input",recordHash(original,check));stageHashes.put("sorted_input",recordHash(sorted,check));stageHashes.put("immediate_keep",recordHash(keep,check));stageHashes.put("candidates",recordHash(todo,check));stageHashes.put("remapped",recordHash(mapped,check));stageHashes.put("filtered_keep",recordHash(actual,check));stageHashes.put("merged",recordHash(combined,check));stageHashes.put("final",recordHash(result,check));
        return new Report(decision.counts, counts, stageHashes, List.of(
                "Original find/filter counters mix read and pair units; the independent complete BAM census above uses records throughout.",
                "Original WASP compares paired CIGARs as an unordered set and does not retain mate identity in that test; the native filter preserves that source rule.",
                "Original filter omits chromosome identity. A retained cross-chromosome coordinate coincidence fails this audit; original outputs remain unchanged.",
                "This audits original read retention and final BAM index populations; it does not establish author references, variant preparation or paper-level reproduction."));
    }

    private static Decision filter(List<SAMRecord> originals, List<SAMRecord> remapped, Runnable check) throws IOException {
        var observed = new HashMap<String,Integer>(); var keep = new HashSet<String>(); var bad = new HashSet<String>();
        var cigars = new HashMap<String,Set<String>>();
        for (SAMRecord read : remapped) {
            check.run(); Name n = name(read.getReadName());
            if (read.isSecondaryOrSupplementary()) { bad.add(n.original); continue; }
            cigars.computeIfAbsent(n.original, k -> new HashSet<>()).add(cigar(read));
            boolean correct;
            if (n.second != null) {
                if (!read.getReadPairedFlag() || !read.getProperPairFlag()) { bad.add(n.original); continue; }
                int pos = read.getAlignmentStart(), mate = read.getMateAlignmentStart();
                if (!(pos < mate || pos == mate && read.getFirstOfPairFlag() && !read.getSecondOfPairFlag())) continue;
                correct = n.first == pos && n.second == mate;
            } else correct = n.first == read.getAlignmentStart();
            if (correct) {
                int count = observed.merge(n.original, 1, Math::addExact);
                if (count == n.total) {
                    require(keep.add(n.original), "Original WASP repeated-complete-remap count error for " + n.original); observed.remove(n.original);
                }
            } else bad.add(n.original);
        }
        var counters = new TreeMap<String,Long>(); for (String key : List.of("keep", "bad", "discard", "not_present", "pair_missing", "cigar_mismatch", "cigar_missing")) counters.put(key, 0L);
        var pairs = new HashMap<String,SAMRecord>(); var result = new ArrayList<SAMRecord>(); var keptNames = new HashSet<String>();
        for (SAMRecord read : originals) {
            check.run(); String name = read.getReadName();
            if (bad.contains(name)) increment(counters, "bad");
            else if (keep.contains(name)) {
                Set<String> options = cigars.get(name);
                if (read.getReadPairedFlag()) {
                    SAMRecord previous = pairs.remove(name);
                    if (previous == null) { pairs.put(name, read); continue; }
                    String one = cigar(previous), two = cigar(read); boolean correct;
                    if (options.size() == 1) correct = java.util.Objects.equals(one, two) && options.contains(one);
                    else if (options.size() == 2) correct = !java.util.Objects.equals(one, two) && options.contains(one) && options.contains(two);
                    else { increment(counters, "cigar_missing"); increment(counters, "discard"); continue; }
                    if (correct) { result.add(previous); result.add(read); add(counters, "keep", 2); keptNames.add(name); }
                    else { increment(counters, "cigar_mismatch"); increment(counters, "discard"); }
                } else if (options.size() != 1) { increment(counters, "cigar_missing"); increment(counters, "discard"); }
                else if (options.contains(cigar(read))) { result.add(read); increment(counters, "keep"); keptNames.add(name); }
                else { increment(counters, "cigar_mismatch"); increment(counters, "discard"); }
            } else increment(counters, "not_present");
        }
        add(counters, "pair_missing", pairs.size()); add(counters, "discard", pairs.size());
        return new Decision(result, counters, keptNames);
    }

    private static void verifyDuplicateGroups(List<SAMRecord> input, List<SAMRecord> result, Runnable check) throws IOException {
        // Independent invariant for the admitted original rmdup_pe domain: every
        // input is a primary, proper, same-chromosome pair, and each ordered pair
        // of left/right mapped starts retains exactly one complete original pair.
        var originals = groups(input, true, check); var kept = groups(result, true, check);
        var expected = new HashSet<String>(); var actual = new HashSet<String>();
        for (var entry : originals.entrySet()) {
            check.run(); var pair = entry.getValue();
            if (pair.size() == 1 && !pair.get(0).getReadPairedFlag()) continue; // Original rmdup_pe discards single reads.
            for (SAMRecord read : pair) require(!read.getReadUnmappedFlag() && !read.getMateUnmappedFlag() && read.getProperPairFlag() && !read.isSecondaryOrSupplementary()
                    && read.getReferenceIndex().equals(read.getMateReferenceIndex()), "Original paired dedup input is outside the independently audited primary/proper/same-chromosome domain");
            expected.add(duplicateKey(pair));
        }
        for (var entry : kept.entrySet()) {
            require(entry.getValue().size() == 2, "Original paired dedup unexpectedly retained a single read");
            require(actual.add(duplicateKey(entry.getValue())), "Original paired dedup retained multiple pairs at the same source positions");
        }
        require(actual.equals(expected), "Original paired dedup omitted a complete source-position group");
    }
    private static String duplicateKey(List<SAMRecord> pair) {
        SAMRecord one = pair.get(0), two = pair.get(1); int left = Math.min(one.getAlignmentStart(), two.getAlignmentStart()), right = Math.max(one.getAlignmentStart(), two.getAlignmentStart());
        return one.getReferenceName() + ":" + left + ":" + right;
    }
    private static Map<String,List<SAMRecord>> groups(List<SAMRecord> records, boolean paired, Runnable check) throws IOException {
        var result = new LinkedHashMap<String,List<SAMRecord>>();
        for (SAMRecord read : records) { check.run(); result.computeIfAbsent(read.getReadName(), n -> new ArrayList<>()).add(read); }
        for (var entry : result.entrySet()) {
            var rows = entry.getValue();
            boolean unitPaired = rows.get(0).getReadPairedFlag();
            require(rows.size() == (unitPaired ? 2 : 1) && (paired || !unitPaired), "Ambiguous/missing/duplicate or wrongly declared original WASP read unit: " + entry.getKey());
            for (SAMRecord read : rows) require(read.getReadPairedFlag() == unitPaired, "Conflicting BAM pairing flags within an original WASP unit");
            if (unitPaired) require(rows.get(0).getFirstOfPairFlag() != rows.get(1).getFirstOfPairFlag()
                    && rows.get(0).getSecondOfPairFlag() != rows.get(1).getSecondOfPairFlag(), "Original WASP pair lacks complementary mate flags");
        }
        return result;
    }
    private static List<SAMRecord> read(Path path, Limits limits, long[] characters, Runnable check) throws IOException {
        var result = new ArrayList<SAMRecord>();
        try (var input = SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(path)) {
            for (SAMRecord read : input) {
                check.run(); require(result.size() < limits.records, "Complete BAM record census capacity exceeded"); characters[0] = Math.addExact(characters[0], canonical(read).length());
                require(characters[0] <= limits.samCharacters, "Combined complete BAM SAM-character census capacity exceeded"); result.add(read);
            }
        } catch (RuntimeException e) { throw new IOException("Invalid or unreadable complete WASP BAM: " + path, e); }
        return result;
    }
    private static void verifyIndex(Path path, List<SAMRecord> records, Limits limits, Runnable check) throws IOException {
        var indexed = new ArrayList<SAMRecord>();
        try (var reader = SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(path)) {
            require(reader.hasIndex(), "Final WASP BAM index missing");
            for (var sequence : reader.getFileHeader().getSequenceDictionary().getSequences()) {
                check.run(); try (var rows = reader.query(sequence.getSequenceName(), 1, sequence.getSequenceLength(), false)) {
                    while (rows.hasNext()) { check.run(); require(indexed.size() < limits.records, "Indexed BAM census capacity exceeded"); indexed.add(rows.next()); }
                }
            }
        } catch (RuntimeException e) { throw new IOException("Invalid final WASP BAM index", e); }
        require(population(records, check).equals(population(indexed, check)), "Final WASP index does not retrieve the complete final BAM record population");
    }
    private static Name name(String name) throws IOException {
        String[] fields = name.split("\\.", -1); require(fields.length >= 4, "Malformed original WASP remap name");
        String original = String.join(".", java.util.Arrays.copyOf(fields, fields.length - 3));
        try {
            String[] coordinates = fields[fields.length - 3].split("-", -1); require(coordinates.length == 1 || coordinates.length == 2, "Malformed WASP source coordinates");
            int first = Integer.parseInt(coordinates[0]), number = Integer.parseInt(fields[fields.length - 2]), total = Integer.parseInt(fields[fields.length - 1]);
            Integer second = coordinates.length == 2 ? Integer.valueOf(coordinates[1]) : null;
            require(!original.isEmpty() && first > 0 && (second == null || second > 0) && number >= 1 && number <= total, "Invalid WASP source name values");
            return new Name(original, first, second, number, total);
        } catch (NumberFormatException e) { throw new IOException("Malformed numeric WASP source name", e); }
    }
    private static Map<String,Integer> population(List<SAMRecord> records, Runnable check) {
        var counts = new HashMap<String,Integer>(); for (SAMRecord record : records) { check.run(); counts.merge(canonical(record), 1, Math::addExact); } return counts;
    }
    private static Map<String,Integer> plus(Map<String,Integer> one, Map<String,Integer> two) {
        var sum = new HashMap<>(one); two.forEach((key, value) -> sum.merge(key, value, Math::addExact)); return sum;
    }
    private static boolean subset(Map<String,Integer> one, Map<String,Integer> two) { for (var entry : one.entrySet()) if (entry.getValue() > two.getOrDefault(entry.getKey(), 0)) return false; return true; }
    private static String cigar(SAMRecord record) { return record.getCigarString().equals("*") ? null : record.getCigarString(); }
    private static String canonical(SAMRecord record) { String line=record.getSAMString();return line.endsWith("\n")?line.substring(0,line.length()-1):line; }
    private static String recordHash(List<SAMRecord> records,Runnable check) {
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(SAMRecord record:records){check.run();byte[] raw=canonical(record).getBytes(java.nio.charset.StandardCharsets.UTF_8);long length=raw.length;for(int shift=56;shift>=0;shift-=8)digest.update((byte)(length>>>shift));digest.update(raw);}
            return java.util.HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static void increment(Map<String,Long> values, String name) { add(values, name, 1); }
    private static void add(Map<String,Long> values, String name, long value) { values.merge(name, value, Math::addExact); }
    private static void require(boolean valid, String message) throws IOException { if (!valid) throw new IOException(message); }
}
