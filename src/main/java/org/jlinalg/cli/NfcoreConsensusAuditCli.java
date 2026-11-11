/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.workflow.NfcoreChipseq121Consensus;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Native full-population consensus audit over an immutable original ChIP-seq1.2.1 result. */
public final class NfcoreConsensusAuditCli {
    private NfcoreConsensusAuditCli() { }
    private static final String ACTION = "Required action: this is a source-specific consensus component audit, with zero nf-core tool credit and no whole-pipeline scientific or biological admission. Preserve all original peaks, tokens, failures and coordinate conventions. FAIL2 means no consensus component result is admitted: correct the named cause and use a new audit path. Complete the original runtime/image and remaining scientific contracts before downstream claims. Hashes establish integrity, not execution authenticity. Cell Ranger remains excluded.";
    private static final String COORDINATES = "WARNING: Original ChIP-seq1.2.1 writes BED's zero-based Start directly to SAF. The native audit preserves this source convention and does not increment Start. Standard one-based SAF interpretation therefore differs by one base. Do not relabel or silently repair it; a coordinate conversion requires a separate declared analysis with original outputs retained.";
    public static void main(String[] args) {
        Path out = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                emit(System.out, "--engine-result COMPLETE_SESSION_OR_ATTEMPT --peak-type broadPeak|narrowPeak --min-replicates POSITIVE_SOURCE_VALUE --out NEW_AUDIT_DIRECTORY\n" + COORDINATES + "\n" + ACTION); return;
            }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 >= args.length || !Set.of("--engine-result", "--peak-type", "--min-replicates", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null)
                    throw new IOException("Require unique complete source-specific consensus audit options; see --help");
            }
            if (options.size() != 4 || !Set.of("broadPeak", "narrowPeak").contains(options.get("--peak-type"))) throw new IOException("Declare complete original result, exact peak kind and source replicate threshold explicitly");
            int minimum;
            try { if (!options.get("--min-replicates").matches("[0-9]+")) throw new NumberFormatException(); minimum = Integer.parseInt(options.get("--min-replicates")); if (minimum < 1) throw new NumberFormatException(); }
            catch (NumberFormatException invalid) { throw new IOException("Require a positive explicit source replicate threshold", invalid); }
            Path selected = Path.of(options.get("--engine-result")).toAbsolutePath().normalize();
            var verified = NfcoreEngineBundle.verify(selected, () -> { });
            out = Path.of(options.get("--out")).toAbsolutePath().normalize();
            if (out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent())) throw new IOException("Require a canonical existing parent and a new audit path");
            if (out.startsWith(selected) || selected.startsWith(out) || out.startsWith(verified.attempt()) || verified.attempt().startsWith(out)) throw new IOException("Consensus audit output and complete original attempt must be disjoint");
            String producer = text(object(json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root"));
            Path producerPath = Path.of(producer);
            if (producerPath.isAbsolute() && (out.startsWith(producerPath) || producerPath.startsWith(out))) throw new IOException("Consensus audit output and original producer session must be disjoint");
            Files.createDirectory(out); owned = true;
            var files = new NfcoreScientificFiles(verified); var source = files.verifyChipseq121Source(); var parameters = files.parameters();
            if (parameters.containsKey("min_reps_consensus")) {
                Object value = parameters.get("min_reps_consensus");
                if (!(value instanceof Number) || new java.math.BigDecimal(value.toString()).intValueExact() != minimum) throw new IOException("Explicit replicate threshold differs from the typed source parameter; no coercion inferred");
            }
            if (parameters.containsKey("skip_consensus_peaks") && !Boolean.FALSE.equals(parameters.get("skip_consensus_peaks"))) throw new IOException("Source request does not explicitly select an active consensus branch");
            String peakType = options.get("--peak-type"); boolean narrow = peakType.equals("narrowPeak");
            if (parameters.containsKey("narrow_peak") && !Boolean.valueOf(narrow).equals(parameters.get("narrow_peak"))) throw new IOException("Explicit peak kind differs from the typed source parameter");
            var reference = new LinkedHashMap<String, Long>();
            for (var entry : FastaIndexes.build(files.inputPath(text(parameters.get("fasta"))))) reference.put(entry.getContig(), entry.getSize());
            var mapping = NfcoreChipseq121Mappings.read(output(files, "pipeline_info/design_reads.csv"), output(files, "pipeline_info/design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults());
            var groups = new LinkedHashMap<String, ArrayList<NfcoreChipseq121Mappings.Control>>();
            for (var control : mapping.controls()) groups.computeIfAbsent(control.antibody(), key -> new ArrayList<>()).add(control);
            var reports = new ArrayList<Map<String, Object>>(); var warnings = new ArrayList<String>(); warnings.add(COORDINATES);
            for (var group : groups.entrySet()) {
                var flag = group.getValue().get(0);
                if (!flag.replicatesExist() && !flag.multipleGroups()) continue; // Exact original when condition, not a reduced test.
                var kind = narrow ? NfcoreChipseq121Consensus.Kind.NARROW : NfcoreChipseq121Consensus.Kind.BROAD;
                var limits = NfcoreChipseq121Consensus.Limits.defaults(); var inputs = new ArrayList<NfcoreChipseq121Consensus.PeakSet>();
                var inputPins = new LinkedHashMap<String, Object>();
                for (var control : group.getValue()) {
                    Path peakFile = output(files, "bwa/mergedLibrary/macs/" + peakType + "/" + control.sampleId() + "_peaks." + peakType);
                    var peaks = NfcoreChipseq121Consensus.read(peakFile, kind, limits);
                    for (var peak : peaks) {
                        Long length = reference.get(peak.contig()); if (length == null || peak.end() > length) throw new IOException("Original peak geometry differs from exact declared reference; no contig alias inferred");
                    }
                    inputs.add(new NfcoreChipseq121Consensus.PeakSet(control.sampleId(), peaks));
                    inputPins.put(control.sampleId(), pin(peakFile, 1000000000000L, () -> { }));
                }
                var plan = NfcoreChipseq121Consensus.compute(inputs, kind, minimum, limits);
                String prefix = "bwa/mergedLibrary/macs/" + peakType + "/consensus/" + group.getKey() + "/" + group.getKey() + ".consensus_peaks";
                try (var bool = Files.newBufferedReader(output(files, prefix + ".boolean.txt"), StandardCharsets.UTF_8);
                     var bed = Files.newBufferedReader(output(files, prefix + ".bed"), StandardCharsets.UTF_8);
                     var saf = Files.newBufferedReader(output(files, prefix + ".saf"), StandardCharsets.UTF_8);
                     var intersections = Files.newBufferedReader(output(files, prefix + ".boolean.intersect.txt"), StandardCharsets.UTF_8)) {
                    NfcoreChipseq121Consensus.auditBoolean(bool, plan, limits);
                    NfcoreChipseq121Consensus.auditIntervals(bed, saf, plan, limits);
                    NfcoreChipseq121Consensus.auditIntersections(intersections, plan, limits);
                }
                reports.add(Map.of("antibody", group.getKey(), "original_peak_pins", inputPins, "native_consensus", plan.document()));
                if (plan.rows().isEmpty()) warnings.add(group.getKey() + ": original replicate rule retains no consensus intervals. This is not evidence of biological absence.");
            }
            var after = NfcoreEngineBundle.verify(selected, () -> { });
            if (!after.attempt().equals(verified.attempt()) || !after.manifest().equals(verified.manifest())) throw new IOException("Complete original result/latest pointer changed during consensus audit");
            var report = new LinkedHashMap<String, Object>();
            report.put("status", "COMPLETE_NATIVE_ORIGINAL_CONSENSUS_COMPONENT_AUDIT"); report.put("original_engine_manifest", verified.manifest()); report.put("source", source);
            report.put("antibodies", reports); report.put("explicit_minimum_replicates", minimum); report.put("explicit_peak_type", peakType); report.put("warnings", warnings);
            report.put("nfcore_tool_credit", 0); report.put("scientific_result_admitted", false);
            writeJson(out.resolve("audit.json"), report);
            Files.writeString(out.resolve("WARNINGS.md"), "# Original consensus component only\n\n" + String.join("\n\n", warnings) + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writeJson(out.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("status", "COMPLETE_CONSENSUS_COMPONENT_AUDIT_ONLY", "audit", pin(out.resolve("audit.json"), 67108864, () -> { }), "nfcore_tool_credit", 0, "scientific_result_admitted", false));
            emit(System.out, "Complete native original-consensus comparison passed for " + reports.size() + " antibody groups.\n" + ACTION);
            for (String warning : warnings) emit(System.err, warning);
        } catch (Exception | LinkageError failed) {
            emit(System.err, "FAIL2 NO CONSENSUS COMPONENT RESULT: " + failed + "\n" + ACTION);
            if (owned && out != null) try {
                writeJson(out.resolve("FAILURE.json"), Map.of("status", "FAIL2_NO_CONSENSUS_COMPONENT_RESULT", "error", failed.toString(), "required_action", ACTION));
                Files.writeString(out.resolve("WARNINGS.md"), "# FAIL2 NO CONSENSUS COMPONENT RESULT\n\n" + failed + "\n\n" + COORDINATES + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { emit(System.err, "Required action: preserve the audit path; diagnostic write failed: " + diagnostic); }
            System.exit(2);
        }
    }
    private static Path output(NfcoreScientificFiles files, String name) throws IOException {
        try { return files.output(name); }
        catch (IOException invalid) { throw new IOException("Required complete original output is missing or inconsistent: " + name + " (" + invalid.getMessage() + "). Required action: confirm the exact peak kind, active original consensus branch and selected complete attempt; preserve existing outputs.", invalid); }
    }
    private static void emit(java.io.PrintStream out, String text) { out.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
