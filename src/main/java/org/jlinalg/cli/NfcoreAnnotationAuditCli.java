/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.workflow.NfcoreChipseq121Consensus;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Source-specific complete original HOMER supplied-GTF annotation and aggregate-QC auditor. */
public final class NfcoreAnnotationAuditCli {
    private NfcoreAnnotationAuditCli() { }
    private static final String ACTION = "Required action: this is an original annotation/QC consistency component, with no biological or independent feature-classification admission. Gene-ID mode retains raw transcript TSS rows and uses the original last-row-per-contig/gene lookup; transcripts are not merged into gene spans. Raw missing annotations remain unchanged. The original plotting script's separate NA-distance -> Unassigned copy and absent-category NA cells are retained. FAIL2 admits no component result: inspect the named source, identity, geometry, TSS or QC discrepancy, preserve all originals/failures and retry a NEW output path after resolving its cause. No identifier, coordinate, missing value or threshold is repaired.";
    public static void main(String[] args) {
        Path out = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(System.out, "--engine-result COMPLETE_ORIGINAL_SESSION_OR_ATTEMPT --peak-type broadPeak|narrowPeak --min-replicates SOURCE_VALUE --out NEW_DIRECTORY\n" + ACTION); return; }
            var options = new LinkedHashMap<String, String>(); for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--engine-result", "--peak-type", "--min-replicates", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require complete unique original annotation/QC options");
            if (options.size() != 4 || !Set.of("broadPeak", "narrowPeak").contains(options.get("--peak-type"))) throw new IOException("Declare exact source peak kind, minimum replicate threshold and complete original result");
            int minimum = Integer.parseInt(options.get("--min-replicates")); if (minimum < 1) throw new IOException("Require a positive original replicate threshold");
            Path selected = Path.of(options.get("--engine-result")).toAbsolutePath().normalize(); out = Path.of(options.get("--out")).toAbsolutePath().normalize();
            if (out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent()) || out.startsWith(selected) || selected.startsWith(out)) throw new IOException("Require a canonical existing parent and new disjoint output");
            var verified = NfcoreEngineBundle.verify(selected, () -> { }); var files = new NfcoreScientificFiles(verified); var source = files.verifyChipseq121Source(); var parameters = files.parameters();
            Path producer = Path.of(text(object(json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root")));
            if (out.startsWith(verified.attempt()) || verified.attempt().startsWith(out) || producer.isAbsolute() && (out.startsWith(producer) || producer.startsWith(out))) throw new IOException("Annotation output, original producer and verified attempt must be disjoint");
            Files.createDirectory(out); owned = true;
            for (String flag : List.of("skip_peak_annotation", "skip_peak_qc", "skip_consensus_peaks")) if (parameters.containsKey(flag) && !Boolean.FALSE.equals(parameters.get(flag))) throw new IOException("Original request does not select the full annotation/QC branch: " + flag);
            if (parameters.containsKey("min_reps_consensus") && integer(parameters.get("min_reps_consensus"), 1, 1000000) != minimum) throw new IOException("Declared minimum differs from the original typed parameter");
            String kind = options.get("--peak-type"); var peakKind = kind.equals("narrowPeak") ? NfcoreChipseq121Consensus.Kind.NARROW : NfcoreChipseq121Consensus.Kind.BROAD;
            if (parameters.containsKey("narrow_peak") && !Boolean.valueOf(kind.equals("narrowPeak")).equals(parameters.get("narrow_peak"))) throw new IOException("Declared peak kind differs from the original typed parameter");
            var reference = new LinkedHashMap<String, Long>(); for (var entry : FastaIndexes.build(files.inputPath(text(parameters.get("fasta"))))) reference.put(entry.getContig(), entry.getSize());
            var gtf = NfcoreChipseq121Annotations.gtf(files.inputPath(text(parameters.get("gtf"))), reference);
            var mapping = NfcoreChipseq121Mappings.read(files.output("pipeline_info/design_reads.csv"), files.output("pipeline_info/design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults());
            var raw = new LinkedHashMap<String, List<NfcoreChipseq121Consensus.Peak>>(); var annotated = new LinkedHashMap<String, NfcoreChipseq121Annotations.Annotation>(); var groups = new LinkedHashMap<String, ArrayList<NfcoreChipseq121Mappings.Control>>(); var details = new ArrayList<Map<String, Object>>(); long total = 0, ties = 0, missing = 0;
            String base = "bwa/mergedLibrary/macs/" + kind + "/";
            for (var control : mapping.controls()) {
                var peaks = NfcoreChipseq121Consensus.read(files.output(base + control.sampleId() + "_peaks." + kind), peakKind, NfcoreChipseq121Consensus.Limits.defaults());
                var result = NfcoreChipseq121Annotations.audit(files.output(base + control.sampleId() + "_peaks.annotatePeaks.txt"), NfcoreChipseq121Annotations.macs(peaks), gtf);
                raw.put(control.sampleId(), peaks); annotated.put(control.sampleId(), result); groups.computeIfAbsent(control.antibody(), key -> new ArrayList<>()).add(control); total += result.peaks(); ties += result.tiedAssignments(); missing += result.missingGtfContigs();
                details.add(Map.of("IP_library", control.sampleId(), "complete_annotation_rows", result.peaks(), "native_compatible_gene_TSS_ties", result.tiedAssignments(), "missing_GTF_contig_rows", result.missingGtfContigs()));
            }
            for (var group : groups.entrySet()) {
                var flags = group.getValue().get(0); if (!flags.replicatesExist() && !flags.multipleGroups()) continue;
                var sets = new ArrayList<NfcoreChipseq121Consensus.PeakSet>(); for (var control : group.getValue()) sets.add(new NfcoreChipseq121Consensus.PeakSet(control.sampleId(), raw.get(control.sampleId())));
                var plan = NfcoreChipseq121Consensus.compute(sets, peakKind, minimum, NfcoreChipseq121Consensus.Limits.defaults()); String prefix = base + "consensus/" + group.getKey() + "/" + group.getKey() + ".consensus_peaks";
                try (var bool = Files.newBufferedReader(files.output(prefix + ".boolean.txt"), StandardCharsets.UTF_8); var bed = Files.newBufferedReader(files.output(prefix + ".bed"), StandardCharsets.UTF_8); var saf = Files.newBufferedReader(files.output(prefix + ".saf"), StandardCharsets.UTF_8)) { NfcoreChipseq121Consensus.auditBoolean(bool, plan, NfcoreChipseq121Consensus.Limits.defaults()); NfcoreChipseq121Consensus.auditIntervals(bed, saf, plan, NfcoreChipseq121Consensus.Limits.defaults()); }
                var result = NfcoreChipseq121Annotations.audit(files.output(prefix + ".annotatePeaks.txt"), NfcoreChipseq121Annotations.consensus(plan.rows()), gtf);
                NfcoreChipseq121Annotations.combined(files.output(prefix + ".boolean.txt"), files.output(prefix + ".boolean.annotatePeaks.txt"), result, plan.rows()); total += result.peaks(); ties += result.tiedAssignments(); missing += result.missingGtfContigs();
                details.add(Map.of("antibody", group.getKey(), "complete_consensus_annotation_rows", result.peaks(), "native_compatible_gene_TSS_ties", result.tiedAssignments(), "missing_GTF_contig_rows", result.missingGtfContigs()));
            }
            var featureQc = NfcoreChipseq121Annotations.featureQc(files.output(base + "qc/macs_annotatePeaks.summary.txt"), annotated); var peakQc = NfcoreChipseq121Annotations.peakQc(files.output(base + "qc/macs_peak.summary.txt"), raw);
            NfcoreChipseq121Annotations.multiqc(files.output(base + "qc/macs_annotatePeaks.summary.txt"), files.output(base + "qc/macs_annotatePeaks.summary_mqc.tsv"), files.inputMember(files.alias("workflow") + "/assets/multiqc/peak_annotation_header.txt"));
            var after = NfcoreEngineBundle.verify(selected, () -> { }); if (!verified.attempt().equals(after.attempt()) || !verified.manifest().equals(after.manifest())) throw new IOException("Selected complete original result changed during annotation/QC audit");
            var warnings = new ArrayList<String>(); warnings.add("Original HOMER feature labels and metadata remain upstream results; native checks cover complete identities/geometry, compatible nearest gene-TSS assignments and all published aggregate counts/statistics. Feature-priority classification and PDF rendering are not independently recomputed.");
            var lookup = NfcoreChipseq121Annotations.geneLookup(gtf); if (lookup.overwrittenRows() > 0) warnings.add("Original HOMER gene-ID lookup overwrites " + lookup.overwrittenRows() + " earlier transcript-TSS rows after the source transcript-ID sort. All raw GTF/TSS identities remain retained. The original last row per contig/gene selects the active TSS; feature-label genes and nearest-TSS genes can differ.");
            if (gtf.plusAssignedExons() > 0) warnings.add("Original dot-to-plus strand convention is retained for " + gtf.plusAssignedExons() + " GTF exons; the raw GTF is unchanged."); if (missing > 0) warnings.add(missing + " annotation rows lack native GTF-contig TSS candidates. Raw NA values remain unchanged; only the source plotting copy uses Unassigned."); if (ties > 0) warnings.add(ties + " rows have multiple compatible nearest gene-TSS assignments. The original choice is retained; no unique assignment is invented.");
            var report = new LinkedHashMap<String, Object>(); report.put("status", "COMPLETE_ORIGINAL_HOMER411_ANNOTATION_QC_COMPONENT_ONLY"); report.put("original_engine_manifest", verified.manifest()); report.put("source", source); report.put("GTF_exons", gtf.exons()); report.put("GTF_transcript_TSSs", gtf.transcripts().size()); report.put("active_gene_TSS_rows", lookup.activeGeneRows()); report.put("overwritten_transcript_TSS_rows_retained", lookup.overwrittenRows()); report.put("complete_annotation_rows", total); report.put("annotations", details); report.put("feature_QC", featureQc); report.put("peak_QC", peakQc); report.put("warnings", warnings); report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0);
            writeJson(out.resolve("audit.json"), report); Files.writeString(out.resolve("WARNINGS.md"), "# Original annotation and QC consistency component\n\n" + String.join("\n\n", warnings) + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); writeJson(out.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("status", "COMPLETE_ANNOTATION_QC_COMPONENT_ONLY", "audit", pin(out.resolve("audit.json"), 67108864, () -> { }), "scientific_result_admitted", false, "nfcore_tool_credit", 0)); emit(System.out, "Complete original annotation/QC checks passed: " + total + " rows.\n" + ACTION); for (String warning : warnings) emit(System.err, warning);
        } catch (Exception | LinkageError failure) {
            emit(System.err, "FAIL2 NO ANNOTATION/QC COMPONENT RESULT: " + failure + "\n" + ACTION);
            if (owned && out != null) try { writeJson(out.resolve("FAILURE.json"), Map.of("status", "FAIL2_NO_COMPONENT_RESULT", "error", failure.toString(), "required_action", ACTION)); Files.writeString(out.resolve("WARNINGS.md"), "# FAIL2 no annotation/QC component result\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); } catch (IOException diagnostic) { emit(System.err, "Required action: retain audit path; diagnostic write failed: " + diagnostic); }
            System.exit(2);
        }
    }
    private static void emit(java.io.PrintStream output, String text) { output.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
