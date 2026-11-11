/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.PicardLibraryComplexity;
import org.jlinalg.workflow.NfcoreChipseq200Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete source MarkDuplicates counters/derived values and original Preseq output checks. */
final class NfcoreChipseq200Complexity {
    private NfcoreChipseq200Complexity() { }
    private static final List<String> COLUMNS = List.of("LIBRARY", "UNPAIRED_READS_EXAMINED", "READ_PAIRS_EXAMINED", "SECONDARY_OR_SUPPLEMENTARY_RDS", "UNMAPPED_READS", "UNPAIRED_READ_DUPLICATES", "READ_PAIR_DUPLICATES", "READ_PAIR_OPTICAL_DUPLICATES", "PERCENT_DUPLICATION", "ESTIMATED_LIBRARY_SIZE");
    private static final long MAX_RECORDS = 1000000000L;
    private static final long MAX_TABLE = 67108864;
    static Map<String, Object> audit(Path bam, Path metrics, Path preseq, String libraryId, List<NfcoreChipseq200Mappings.Read> technical, Map<String, Long> reference, String center) throws IOException {
        // Check all original v2 technical RG/LB/SM identities without altering records or flags.
        // The actual provider numerical methods match the retained original reference bytecode;
        // the shared native arithmetic remains unchanged. Optical classification stays upstream.
        NfcoreChipseq200BamAudit.inspect(bam, technical, reference, List.of(), center, MAX_RECORDS);
        var counters = new LinkedHashMap<String, long[]>(); long records = 0;
        try (var reader = SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(bam)) {
            var programs = reader.getFileHeader().getProgramRecords().stream().filter(p -> "MarkDuplicates".equals(p.getProgramName())).toList();
            if (programs.size() != 1 || !"Version:2.27.4-SNAPSHOT".equals(programs.get(0).getProgramVersion())) throw new IOException("Original marked-BAM literal provider version differs; neither Version: prefix nor SNAPSHOT suffix is stripped");
            for (var record : reader) {
                if (++records > MAX_RECORDS) throw new IOException("Complete marked BAM exceeds record capacity");
                var rg = record.getReadGroup(); String name = rg == null || rg.getLibrary() == null ? "Unknown Library" : rg.getLibrary();
                long[] c = counters.computeIfAbsent(name, key -> new long[6]);
                // Original AbstractMarkDuplicatesCommandLineProgram priority: unmapped before secondary.
                if (record.getReadUnmappedFlag()) c[3]++;
                else if (record.isSecondaryOrSupplementary()) c[2]++;
                else if (!record.getReadPairedFlag() || record.getMateUnmappedFlag()) c[0]++;
                else c[1]++;
                if (record.getDuplicateReadFlag() && !record.isSecondaryOrSupplementary() && !record.getReadUnmappedFlag()) {
                    if (!record.getReadPairedFlag() || record.getMateUnmappedFlag()) c[4]++;
                    else c[5]++;
                }
            }
        } catch (htsjdk.samtools.SAMException malformed) { throw new IOException("Complete original marked-BAM stream is unassessable", malformed); }
        if (counters.isEmpty()) throw new IOException("Original marked BAM has no records; library complexity is unassessable, with no estimate substituted");
        List<String> lines = boundedLines(metrics); int header = -1, hist = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("LIBRARY\t")) { if (header >= 0) throw new IOException("Multiple original duplication metric tables"); header = i; }
            if (lines.get(i).startsWith("BIN\t")) { if (hist >= 0) throw new IOException("Multiple original duplication histograms"); hist = i; }
        }
        if (header < 0 || !List.of(lines.get(header).split("\t", -1)).equals(COLUMNS)) throw new IOException("Original provider Picard2.27.4-SNAPSHOT duplication metric columns differ");
        var seen = new HashSet<String>(); var reports = new ArrayList<Map<String, Object>>(); var warnings = new ArrayList<String>();
        long allPairs = 0, uniquePairs = 0, opticalPairs = 0; PicardLibraryComplexity.Result sole = null;
        for (int i = header + 1; i < lines.size() && !lines.get(i).isEmpty() && !lines.get(i).startsWith("#"); i++) {
            String[] f = lines.get(i).split("\t", -1); if (f.length != 10 || !seen.add(f[0]) || !counters.containsKey(f[0])) throw new IOException("Original metric library rows are missing, duplicated or not present in the complete BAM");
            long[] c = counters.get(f[0]); long[] expected = {c[0], c[1] / 2, c[2], c[3], c[4], c[5] / 2};
            for (int field = 0; field < expected.length; field++) if (nonnegative(f[field + 1]) != expected[field]) throw new IOException("Original complete marked-BAM counter differs: " + COLUMNS.get(field + 1) + " for " + f[0]);
            long optical = nonnegative(f[7]);
            var derived = PicardLibraryComplexity.calculate(new PicardLibraryComplexity.Counts(c[0], c[1] / 2, c[4], c[5] / 2, optical));
            display(derived.duplicationFraction(), f[8], "Original duplication fraction");
            if (derived.estimatedLibrarySize().isPresent() ? f[9].isEmpty() || nonnegative(f[9]) != derived.estimatedLibrarySize().getAsLong() : !f[9].isEmpty()) throw new IOException("Original library-size estimate/absence differs");
            if (c[1] % 2 != 0 || c[5] % 2 != 0) warnings.add(f[0] + ": odd paired record counters; original Picard integer division by two is retained, without mate repair.");
            if (f[0].equals("Unknown Library")) warnings.add("Original BAM has no library label; Picard's literal Unknown Library group is retained.");
            if (!derived.estimatedLibrarySize().isPresent() || !derived.roiAssessable()) warnings.add(f[0] + ": library-size/ROI estimate is absent or unassessable; no positive finite estimate is substituted.");
            var row = new LinkedHashMap<String, Object>(); row.put("source_library", f[0]); row.put("unpaired_examined", c[0]); row.put("paired_examined", c[1] / 2); row.put("secondary_or_supplementary", c[2]); row.put("unmapped", c[3]); row.put("unpaired_duplicates", c[4]); row.put("paired_duplicates", c[5] / 2); row.put("source_optical_duplicates", optical); row.put("native_duplication_fraction", derived.duplicationFraction()); row.put("native_estimated_library_size", derived.estimatedLibrarySize().isPresent() ? derived.estimatedLibrarySize().getAsLong() : null); reports.add(row);
            allPairs = Math.addExact(allPairs, c[1] / 2); uniquePairs = Math.addExact(uniquePairs, c[1] / 2 - c[5] / 2); opticalPairs = Math.addExact(opticalPairs, optical); sole = derived;
        }
        if (!seen.equals(counters.keySet())) throw new IOException("Original duplication metrics omit marked-BAM libraries");
        int roiChecked = histogram(lines, hist, reports.size() == 1 ? sole : null, allPairs, uniquePairs, opticalPairs);
        var report = new LinkedHashMap<String, Object>(); report.put("sample_library", libraryId); report.put("complete_marked_BAM_records", records); report.put("metric_libraries", reports); report.put("complete_ROI_bins_checked", roiChecked);
        report.put("original_Preseq", preseq == null ? Map.of("source_branch_requested", false) : preseq(preseq)); report.put("warnings", warnings);
        report.put("optical_duplicate_identification_independently_recomputed", false); report.put("duplicate_marking_independently_recomputed", false); report.put("preseq_extrapolation_independently_recomputed", false); report.put("scientific_result_admitted", false); return report;
    }
    private static int histogram(List<String> lines, int from, PicardLibraryComplexity.Result derived, long pairs, long unique, long optical) throws IOException {
        if (from < 0) { if (pairs != 0) throw new IOException("Original complete pair duplication histogram is absent"); return 0; }
        List<String> header = List.of(lines.get(from).split("\t", -1));
        if (new HashSet<>(header).size() != header.size() || !Set.of("BIN", "CoverageMult", "all_sets", "optical_sets", "non_optical_sets").containsAll(header) || !header.containsAll(List.of("BIN", "all_sets", "non_optical_sets"))) throw new IOException("Original duplication histogram column family differs");
        int coverage = header.indexOf("CoverageMult"), all = header.indexOf("all_sets"), nonOptical = header.indexOf("non_optical_sets");
        boolean expectedRoi = derived != null && derived.estimatedLibrarySize().isPresent();
        if (expectedRoi != (coverage >= 0)) throw new IOException("Original source ROI applicability differs");
        double[] roi = expectedRoi ? derived.roi() : new double[0]; var bins = new HashSet<Long>(); long weighted = 0, sets = 0, weightedNonOptical = 0; int checked = 0;
        for (int i = from + 1; i < lines.size() && !lines.get(i).isEmpty(); i++) {
            String[] f = lines.get(i).split("\t", -1); if (f.length != header.size()) throw new IOException("Malformed original duplication histogram row");
            long bin = decimalInteger(f[0]); if (bin < 1 || !bins.add(bin)) throw new IOException("Ambiguous original histogram bin");
            long count = decimalInteger(f[all]), non = decimalInteger(f[nonOptical]); sets = Math.addExact(sets, count); weighted = Math.addExact(weighted, Math.multiplyExact(bin, count)); weightedNonOptical = Math.addExact(weightedNonOptical, Math.multiplyExact(bin, non));
            for (int j = 1; j < f.length; j++) if (j != coverage) decimalInteger(f[j]);
            if (expectedRoi && bin <= 100) { display(roi[(int) bin - 1], f[coverage], "Original ROI bin " + bin); checked++; }
            else if (coverage >= 0 && Double.parseDouble(f[coverage]) != 0) throw new IOException("Original histogram has ROI values outside source bins 1..100");
        }
        if (sets != unique || weighted != pairs || weightedNonOptical != pairs - optical) throw new IOException("Complete original duplicate-set histogram does not reconcile with marked-BAM pair counts and source optical counts");
        if (expectedRoi && checked != 100) throw new IOException("Incomplete original 100-bin ROI population"); return checked;
    }
    private static Map<String, Object> preseq(Path file) throws IOException {
        List<String> lines = boundedLines(file);
        if (lines.size() != 10001 || !lines.get(0).equals("TOTAL_READS\tEXPECTED_DISTINCT\tLOWER_0.95CI\tUPPER_0.95CI")) throw new IOException("Original full Preseq default 10,000-row curve schema/population differs; no tail truncation or smaller grid admitted");
        double previous = -1, previousExpected = -1;
        int grid = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split("\t", -1); if (f.length != 4) throw new IOException("Malformed original Preseq row");
            double[] v = new double[4];
            for (int j = 0; j < 4; j++) { v[j] = Double.parseDouble(f[j]); if (!Double.isFinite(v[j]) || v[j] < 0) throw new IOException("Unassessable original Preseq scalar retained without substitution: " + f[j]); }
            if (v[0] != grid++ * 1000000.0 || grid == 1 && (v[1] != 0 || v[2] != 0 || v[3] != 0)) throw new IOException("Original Preseq grid/zero-origin differs from source step=1e6, extrapolation<1e10 defaults");
            if (v[0] <= previous || v[1] < previousExpected || v[1] > v[0] || v[2] > v[1] || v[1] > v[3]) throw new IOException("Original Preseq estimates/grid/confidence bounds are inconsistent; no curve repair applied");
            previous = v[0]; previousExpected = v[1];
        }
        return Map.of("source_branch_requested", true, "complete_curve_rows", lines.size() - 1, "all_scalars_assessable", true, "original_last_total_reads", previous, "original_last_expected_distinct", previousExpected, "source_pin", pin(file, MAX_TABLE, () -> { }));
    }
    private static long nonnegative(String value) throws IOException { try { if (!value.matches("[0-9]+")) throw new NumberFormatException(); return Long.parseLong(value); } catch (NumberFormatException invalid) { throw new IOException("Original metric is not an exact nonnegative bounded integer: " + value, invalid); } }
    private static long decimalInteger(String value) throws IOException { try { if (value.length() > 128) throw new NumberFormatException(); long number = new java.math.BigDecimal(value).longValueExact(); if (number < 0) throw new NumberFormatException(); return number; } catch (NumberFormatException | ArithmeticException invalid) { throw new IOException("Original histogram is not an exact bounded integer: " + value, invalid); } }
    private static void display(double expected, String source, String label) throws IOException {
        double observed; try { observed = Double.parseDouble(source); } catch (NumberFormatException invalid) { throw new IOException(label + " is unassessable; original token retained", invalid); }
        if (!Double.isFinite(expected) || !Double.isFinite(observed) || Math.abs(expected - observed) > 0.0000005 + 8 * Math.ulp(expected)) throw new IOException(label + " differs at source six-decimal display precision");
    }
    private static List<String> boundedLines(Path file) throws IOException {
        byte[] raw = bytes(file, (int) MAX_TABLE); String value = utf8(raw);
        var lines = value.lines().toList(); if (lines.size() > 1000000) throw new IOException("Complete complexity table exceeds row capacity");
        for (String line : lines) if (line.length() > 1048576 || line.chars().anyMatch(c -> c < 32 && c != '\t')) throw new IOException("Oversized/control-containing original complexity row"); return lines;
    }
}
