/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.workflow.NfcoreChipseq121Consensus;

/** Complete original annotation and aggregate-QC control, or a declared derivative. */
public final class NfcoreAnnotationControl {
    private NfcoreAnnotationControl() { }
    public static void main(String[] args) throws Exception {
        try {
            Path root = Path.of(args[0]); var reference = new LinkedHashMap<String, Long>(); for (var entry : FastaIndexes.build(root.resolve("genome.fa"))) reference.put(entry.getContig(), entry.getSize());
            var gtf = NfcoreChipseq121Annotations.gtf(root.resolve("genes.gtf"), reference);
            var settings = new java.util.Properties(); if (Files.exists(root.resolve("case.properties"))) try (var input = Files.newBufferedReader(root.resolve("case.properties"))) { settings.load(input); }
            String consensusRequested = settings.getProperty("include-consensus", "true"); if (!java.util.Set.of("true", "false").contains(consensusRequested)) throw new IllegalArgumentException("Require an explicit synthetic control branch declaration");
            var allPeaks = new LinkedHashMap<String, List<NfcoreChipseq121Consensus.Peak>>(); var annotations = new LinkedHashMap<String, NfcoreChipseq121Annotations.Annotation>(); var peakSets = new ArrayList<NfcoreChipseq121Consensus.PeakSet>(); long total = 0;
            for (String sample : List.of("SPT5_T0_R1", "SPT5_T0_R2", "SPT5_T15_R1", "SPT5_T15_R2")) {
                var peaks = NfcoreChipseq121Consensus.read(root.resolve(sample + "_peaks.broadPeak"), NfcoreChipseq121Consensus.Kind.BROAD, NfcoreChipseq121Consensus.Limits.defaults());
                var annotation = NfcoreChipseq121Annotations.audit(root.resolve(sample + "_peaks.annotatePeaks.txt"), NfcoreChipseq121Annotations.macs(peaks), gtf); annotations.put(sample, annotation); allPeaks.put(sample, peaks); peakSets.add(new NfcoreChipseq121Consensus.PeakSet(sample, peaks)); total += annotation.peaks();
            }
            if (consensusRequested.equals("true")) {
                var plan = NfcoreChipseq121Consensus.compute(peakSets, NfcoreChipseq121Consensus.Kind.BROAD, 1, NfcoreChipseq121Consensus.Limits.defaults());
                var consensus = NfcoreChipseq121Annotations.audit(root.resolve("consensus.annotatePeaks.txt"), NfcoreChipseq121Annotations.consensus(plan.rows()), gtf); total += consensus.peaks();
                NfcoreChipseq121Annotations.combined(root.resolve("consensus.boolean.txt"), root.resolve("consensus.boolean.annotatePeaks.txt"), consensus, plan.rows());
            }
            var report = new LinkedHashMap<String, Object>(); report.put("complete_TSS_geometry_and_gene_metadata_rows", total); report.put("GTF_exons", gtf.exons()); report.put("GTF_transcript_TSSs", gtf.transcripts().size()); report.put("gene_ID_mode_merges_transcripts", false);
            report.put("consensus_requested", consensusRequested.equals("true")); report.put("source_dot_strand_exons_assigned_plus", gtf.plusAssignedExons());
            var lookup = NfcoreChipseq121Annotations.geneLookup(gtf); report.put("active_gene_TSS_rows", lookup.activeGeneRows()); report.put("overwritten_transcript_TSS_rows_retained", lookup.overwrittenRows());
            report.put("feature_QC", NfcoreChipseq121Annotations.featureQc(root.resolve("macs_annotatePeaks.summary.txt"), annotations)); report.put("peak_QC", NfcoreChipseq121Annotations.peakQc(root.resolve("macs_peak.summary.txt"), allPeaks));
            if (Files.exists(root.resolve("peak_annotation_header.txt"))) NfcoreChipseq121Annotations.multiqc(root.resolve("macs_annotatePeaks.summary.txt"), root.resolve("macs_annotatePeaks.summary_mqc.tsv"), root.resolve("peak_annotation_header.txt"));
            report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0); NextflowFiles.writeJson(Path.of(args[1]), report); System.out.println("PASS_COMPLETE_SOURCE_ANNOTATION_QC_COMPONENT");
        } catch (Exception failure) { System.err.println("FAIL2 NO ANNOTATION/QC COMPONENT RESULT: " + failure + "\nRequired action: preserve complete original GTF, peaks, annotations, tables and failed diagnostics. Resolve the named source identity/geometry/TSS/statistic discrepancy and retry a NEW path. No gene merge, missing-value substitution or coordinate repair is inferred."); System.exit(2); }
    }
}
