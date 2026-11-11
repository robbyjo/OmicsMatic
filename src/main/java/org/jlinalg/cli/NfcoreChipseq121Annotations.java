/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.HomerPeakAnnotations;
import org.jlinalg.workflow.NfcoreChipseq121Consensus;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete supplied-GTF HOMER gene-ID/TSS and original aggregate-QC output checks.
 * Source feature labels remain upstream results. Gene-ID lookup retains the last original
 * transcript-output row per contig/gene; raw transcript rows are not merged or discarded.
 */
final class NfcoreChipseq121Annotations {
    private NfcoreChipseq121Annotations() { }
    record Peak(String id, String contig, int start0, int end0, String strand, double score) { }
    record Annotation(List<String> header, Map<String, List<String>> rows, Map<String, Long> features,
                      long tiedAssignments, long missingGtfContigs, long peaks) { }
    record GeneTssLookup(HomerPeakAnnotations.Index index, int rawTranscriptRows, int activeGeneRows, int overwrittenRows) { }
    private static final List<String> FIELDS = List.of("Chr", "Start", "End", "Strand", "Peak Score", "Focus Ratio/Region Size", "Annotation", "Detailed Annotation", "Distance to TSS", "Nearest PromoterID", "Entrez ID", "Nearest Unigene", "Nearest Refseq", "Nearest Ensembl", "Gene Name", "Gene Alias", "Gene Description", "Gene Type");

    static HomerPeakAnnotations.Summary gtf(Path path, Map<String, Long> reference) throws IOException {
        try (var input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            var result = HomerPeakAnnotations.readGtf(input, reference, true, 10000000, 1000000, 1048576);
            for (var transcript : result.transcripts()) if (transcript.gene().contains("-HOMER")) throw new IOException("Gene ID uses HOMER's reserved collapsed-ID delimiter; original primary and metadata namespaces are ambiguous: " + transcript.gene());
            return result;
        }
    }
    static List<Peak> macs(List<NfcoreChipseq121Consensus.Peak> source) throws IOException {
        var result = new ArrayList<Peak>();
        for (var peak : source) {
            String[] f = peak.sourceLine().split("\t", -1);
            if (!Set.of("+", "-", ".").contains(f[5])) throw new IOException("Unsupported original BED strand; no strand repair inferred");
            result.add(new Peak(peak.name(), peak.contig(), coordinate(peak.start()), coordinate(peak.end()), f[5].equals("-") ? "-" : "+", number(f[4])));
        }
        return List.copyOf(result);
    }
    static List<Peak> consensus(List<NfcoreChipseq121Consensus.Row> source) throws IOException {
        // The locked source emits literal score 0 in the consensus BED; num_peaks stays in the Boolean table.
        var result = new ArrayList<Peak>(); for (var row : source) result.add(new Peak(row.id(), row.contig(), coordinate(row.start()), coordinate(row.end()), "+", 0)); return List.copyOf(result);
    }
    static GeneTssLookup geneLookup(HomerPeakAnnotations.Summary source) {
        // parseGTF emits sorted transcript IDs with gene-ID labels; annotateRelativePosition's
        // original position reader overwrites the same contig/raw-ID key with its last row.
        var transcripts = new ArrayList<>(source.transcripts()); transcripts.sort((a, b) -> compareText(a.id(), b.id()));
        var active = new LinkedHashMap<String, HomerPeakAnnotations.Transcript>();
        for (var transcript : transcripts) active.put(transcript.contig() + "\t" + transcript.gene(), transcript);
        var effective = new HomerPeakAnnotations.Summary(source.records(), source.exons(), source.plusAssignedExons(), source.noncontiguousSourceBlocks(), List.copyOf(active.values()));
        return new GeneTssLookup(new HomerPeakAnnotations.Index(effective), transcripts.size(), active.size(), transcripts.size() - active.size());
    }
    static Annotation audit(Path file, List<Peak> peaks, HomerPeakAnnotations.Summary gtf) throws IOException {
        Table original = table(file); if (original.header().size() != 19 || !original.header().get(0).startsWith("PeakID (cmd=annotatePeaks.pl ") || !original.header().subList(1, 19).equals(FIELDS)) throw new IOException("Original complete HOMER 4.11 annotation column family differs");
        var expected = new LinkedHashMap<String, Peak>(); for (var peak : peaks) if (expected.putIfAbsent(peak.id(), peak) != null) throw new IOException("Duplicate original peak IDs; no HOMER ID suffix is inferred");
        if (original.rows().size() != peaks.size()) throw new IOException("Complete original annotation peak population differs");
        var index = geneLookup(gtf).index(); var seen = new LinkedHashMap<String, List<String>>(); var features = new LinkedHashMap<String, Long>(); long ties = 0, absent = 0;
        for (var row : original.rows()) {
            Peak peak = expected.get(row.get(0)); if (peak == null || seen.putIfAbsent(peak.id(), row) != null) throw new IOException("Original annotation has unknown, duplicate or missing peak IDs");
            if (!List.of(peak.contig(), Integer.toString(Math.addExact(peak.start0(), 1)), Integer.toString(peak.end0()), peak.strand()).equals(row.subList(1, 5))) throw new IOException("Original complete HOMER peak identity/geometry/strand differs: " + peak.id());
            equal(peak.score(), number(row.get(5)), "Original peak score " + peak.id());
            if (!row.get(6).equals("NA")) throw new IOException("Unexpected original BED focus value; no focus statistic inferred");
            var nearest = index.nearest(peak.contig(), peak.start0() + 1, peak.end0());
            if (nearest.candidates().isEmpty()) {
                if (!row.get(9).equals("NA") || !row.get(10).equals("NA")) throw new IOException("Original annotation fabricates a nearest TSS on a contig without source exon TSSs"); absent++;
            } else {
                long distance = signed(row.get(9)); boolean matches = false;
                for (var candidate : nearest.candidates()) if (candidate.transcript().gene().equals(row.get(10)) && candidate.signedDistance() == distance && candidate.transcript().metadataGeneId().equals(row.get(11))) matches = true;
                if (!matches) throw new IOException("Original gene-ID nearest-TSS/distance/metadata differs from the source last-row-per-contig/gene lookup: " + peak.id());
                if (nearest.candidates().stream().map(c -> c.transcript().gene() + "\t" + c.signedDistance()).distinct().count() > 1) ties++;
            }
            // The original plot script changes a separate plotting copy only: missing distance -> Unassigned.
            String feature = row.get(9).equals("NA") ? "Unassigned" : firstFeature(row.get(7));
            features.merge(feature, 1L, Math::addExact);
        }
        if (!seen.keySet().equals(expected.keySet())) throw new IOException("Original annotation omits late peaks");
        return new Annotation(original.header(), Map.copyOf(seen), Map.copyOf(features), ties, absent, peaks.size());
    }
    static void combined(Path bool, Path combined, Annotation annotation, List<NfcoreChipseq121Consensus.Row> intervals) throws IOException {
        Table prefix = table(bool), actual = table(combined); var header = new ArrayList<>(prefix.header()); header.addAll(annotation.header().subList(6, 19));
        if (!actual.header().equals(header) || prefix.rows().size() != intervals.size() || actual.rows().size() != intervals.size()) throw new IOException("Original combined Boolean/annotation column or row population differs");
        for (int i = 0; i < intervals.size(); i++) {
            var expected = new ArrayList<>(prefix.rows().get(i)); var values = annotation.rows().get(intervals.get(i).id()); if (values == null) throw new IOException("Original combined annotation lacks source consensus identity"); expected.addAll(values.subList(6, 19));
            if (!actual.rows().get(i).equals(expected)) throw new IOException("Original complete Boolean/annotation row, sort order or original fields differ at " + intervals.get(i).id());
        }
    }
    static Map<String, Object> featureQc(Path summary, Map<String, Annotation> annotations) throws IOException {
        Table table = table(summary); var union = new HashSet<String>(); for (var value : annotations.values()) union.addAll(value.features().keySet());
        if (table.header().isEmpty() || !table.header().get(0).equals("sample") || !new HashSet<>(table.header().subList(1, table.header().size())).equals(union) || table.rows().size() != annotations.size()) throw new IOException("Original complete feature-QC sample/column population differs");
        var seen = new HashSet<String>();
        for (var row : table.rows()) {
            var source = annotations.get(row.get(0)); if (source == null || !seen.add(row.get(0))) throw new IOException("Original feature-QC sample identity is missing/duplicated");
            for (int j = 1; j < table.header().size(); j++) {
                Long expected = source.features().get(table.header().get(j)); String actual = row.get(j);
                if (expected == null ? !actual.equals("NA") : nonnegative(actual) != expected) throw new IOException("Original feature-QC count or missing category differs; no absent category is changed to zero");
            }
        }
        return Map.of("samples", annotations.size(), "source_feature_columns", table.header().subList(1, table.header().size()), "all_feature_counts_checked", true, "source_NA_categories_retained", true);
    }
    static void multiqc(Path summary, Path published, Path sourceHeader) throws IOException {
        byte[] header = bytes(sourceHeader, 1048576), body = bytes(summary, 67108864), actual = bytes(published, 67108864);
        byte[] expected = new byte[Math.addExact(header.length, body.length)]; System.arraycopy(header, 0, expected, 0, header.length); System.arraycopy(body, 0, expected, header.length, body.length);
        if (!Arrays.equals(expected, actual)) throw new IOException("Original MultiQC annotation summary differs from source-header plus complete summary concatenation");
    }
    static Map<String, Object> peakQc(Path summary, Map<String, List<NfcoreChipseq121Consensus.Peak>> peaks) throws IOException {
        Table table = table(summary); if (!table.header().equals(List.of("Min.", "1st Qu.", "Median", "Mean", "3rd Qu.", "Max.", "num_peaks", "measure", "sample")) || table.rows().size() != peaks.size() * 4) throw new IOException("Original complete MACS QC columns/row population differs");
        var seen = new HashSet<String>();
        for (var row : table.rows()) {
            List<NfcoreChipseq121Consensus.Peak> source = peaks.get(row.get(8)); String measure = row.get(7);
            if (source == null || !Set.of("fold", "-log10(qvalue)", "-log10(pvalue)", "length").contains(measure) || !seen.add(row.get(8) + "\t" + measure) || nonnegative(row.get(6)) != source.size() || source.isEmpty()) throw new IOException("Original MACS-QC measure/sample/count identity differs or is unassessable");
            double[] values = new double[source.size()]; double sum = 0, correction = 0;
            for (int i = 0; i < values.length; i++) {
                var peak = source.get(i); double value = switch (measure) { case "fold" -> peak.fold(); case "-log10(qvalue)" -> peak.negativeLogQ(); case "-log10(pvalue)" -> peak.negativeLogP(); default -> peak.end() - peak.start(); };
                values[i] = value; double next = sum + value; correction += Math.abs(sum) >= Math.abs(value) ? (sum - next) + value : (value - next) + sum; sum = next;
            }
            Arrays.sort(values); double[] stats = {values[0], quantile(values, .25), quantile(values, .5), (sum + correction) / values.length, quantile(values, .75), values[values.length - 1]};
            for (int j = 0; j < stats.length; j++) equal(stats[j], number(row.get(j)), "Original complete MACS-QC " + row.get(8) + " " + measure + " " + table.header().get(j));
        }
        return Map.of("samples", peaks.size(), "summary_rows", table.rows().size(), "statistics_per_measure", 6, "source_BED_width_is_end_minus_start", true);
    }
    private static double quantile(double[] values, double q) { double index = (values.length - 1) * q; int low = (int) Math.floor(index), high = (int) Math.ceil(index); double fraction = index - low; return low == high ? values[low] : (1 - fraction) * values[low] + fraction * values[high]; }
    private static int compareText(String first, String second) { var a = first.codePoints().iterator(); var b = second.codePoints().iterator(); while (a.hasNext() && b.hasNext()) { int value = Integer.compare(a.nextInt(), b.nextInt()); if (value != 0) return value; } return Boolean.compare(a.hasNext(), b.hasNext()); }
    private static String firstFeature(String value) throws IOException { int space = value.indexOf(' '); String result = space < 0 ? value : value.substring(0, space); if (result.isEmpty() || result.equals("NA")) throw new IOException("Original assigned feature label is unassessable"); return result; }
    private record Table(List<String> header, List<List<String>> rows) { }
    private static Table table(Path file) throws IOException {
        String text = utf8(bytes(file, 67108864)); var lines = text.lines().toList(); if (lines.isEmpty() || lines.size() > 1000001) throw new IOException("Missing or oversized complete annotation/QC table");
        for (String line : lines) if (line.length() > 1048576 || line.chars().anyMatch(c -> c < 32 && c != '\t')) throw new IOException("Oversized/control-containing original annotation/QC row or header");
        var rows = new ArrayList<List<String>>(); List<String> header = List.of(lines.get(0).split("\t", -1));
        if (new HashSet<>(header).size() != header.size() || header.stream().anyMatch(String::isEmpty)) throw new IOException("Ambiguous original annotation/QC columns");
        for (String line : lines.subList(1, lines.size())) { var fields = List.of(line.split("\t", -1)); if (fields.size() != header.size()) throw new IOException("Original annotation/QC field count differs"); rows.add(fields); }
        return new Table(header, List.copyOf(rows));
    }
    private static int coordinate(long value) throws IOException { if (value < 0 || value > 1000000000) throw new IOException("Original HOMER coordinate exceeds explicit native integer-work capacity"); return (int) value; }
    private static long signed(String value) throws IOException { try { if (!value.matches("-?[0-9]+")) throw new NumberFormatException(); return Long.parseLong(value); } catch (NumberFormatException invalid) { throw new IOException("Unassessable original exact TSS distance: " + value, invalid); } }
    private static long nonnegative(String value) throws IOException { long result = signed(value); if (result < 0) throw new IOException("Original count is negative"); return result; }
    private static double number(String value) throws IOException { try { if (!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new NumberFormatException(); double result = Double.parseDouble(value); if (!Double.isFinite(result)) throw new NumberFormatException(); return result; } catch (NumberFormatException invalid) { throw new IOException("Unassessable original finite annotation/QC value retained: " + value, invalid); } }
    private static void equal(double expected, double actual, String label) throws IOException { if (!Double.isFinite(actual) || (expected == 0 || actual == 0 ? expected != actual : Math.abs(expected - actual) > Math.max(16 * Math.ulp(expected), 2e-12 * Math.abs(expected)))) throw new IOException(label + " differs: " + expected + " vs " + actual); }
}
