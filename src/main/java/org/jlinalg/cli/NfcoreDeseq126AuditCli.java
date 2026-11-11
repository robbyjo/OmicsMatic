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

/** Original postfit execution plus complete native output consistency checks, without model refitting. */
public final class NfcoreDeseq126AuditCli {
    private NfcoreDeseq126AuditCli() { }
    private static final String ACTION = "Required action: inspect WARNINGS.md, audit.json, every process/process.stderr and the original warning/session files. This is a DESeq2 postfit component audit: no negative-binomial refit, independent rlog/vst recomputation, whole-pipeline, biological or paper admission; zero nf-core tool credit. Preserve originals and failed attempts. SKIP3 means a missing/forbidden prerequisite: install the exact permitted runtime/image separately, or use an allowed Linux environment, then retry a NEW output path. FAIL2 means no component result: inspect the named integrity, input, process, capacity or numeric discrepancy; correct its cause and retry a NEW path. Hashes establish integrity, not execution authenticity.";
    private static final String COORDINATES = "WARNING: Original ChIP-seq1.2.1 copies BED zero-based Start unchanged into SAF; featureCounts treats SAF positions inclusively. This audit preserves that convention and Length=End-Start+1. Any coordinate conversion requires a separate declared analysis with originals retained.";
    private static final Set<String> REQUIRED = Set.of("--engine-result", "--peak-type", "--min-replicates", "--read-mode", "--container-home", "--image", "--out");
    private static final Set<String> CAPACITIES = Set.of("--wall-seconds", "--address-space-bytes", "--cpu-seconds", "--file-bytes", "--open-files", "--workspace-bytes", "--files", "--processes", "--threads", "--sampled-rss-bytes", "--poll-ms");
    public static void main(String[] args) {
        Path out = null; boolean owned = false; int code = 2;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                emit(System.out, "--engine-result COMPLETE_SESSION_OR_ATTEMPT --peak-type broadPeak|narrowPeak --min-replicates POSITIVE_SOURCE_VALUE --read-mode paired|single --container-home PINNED_APPTAINER_HOME --image ORIGINAL_CHIPSEQ121_SIF --out NEW_DIRECTORY\nOptional explicit capacities: " + CAPACITIES + "\n" + COORDINATES + "\n" + ACTION); return;
            }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !(REQUIRED.contains(args[i]) || CAPACITIES.contains(args[i])) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require unique complete audit options; see --help");
            if (!options.keySet().containsAll(REQUIRED) || !Set.of("broadPeak", "narrowPeak").contains(options.get("--peak-type")) || !Set.of("paired", "single").contains(options.get("--read-mode"))) throw new IOException("Declare all original source and runtime options explicitly; see --help");
            int minimum = (int) positive(options.get("--min-replicates"), 1000000); boolean single = options.get("--read-mode").equals("single");
            var limits = limits(options);
            Path selected = Path.of(options.get("--engine-result")).toAbsolutePath().normalize();
            out = Path.of(options.get("--out")).toAbsolutePath().normalize();
            if (out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent())) throw new IOException("Require a canonical existing parent and a new audit directory");
            if (out.startsWith(selected) || selected.startsWith(out)) throw new IOException("Audit output and original workflow result must be disjoint");
            Files.createDirectory(out); owned = true;
            var runtime = new NfcoreDeseq126Runtime(Path.of(options.get("--container-home")), Path.of(options.get("--image")), out, limits);
            runtime.health();
            var verified = NfcoreEngineBundle.verify(selected, () -> { });
            Path producer = Path.of(text(object(json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root")));
            if (out.startsWith(verified.attempt()) || verified.attempt().startsWith(out) || producer.isAbsolute() && (out.startsWith(producer) || producer.startsWith(out))) throw new IOException("Audit, verified attempt and original producer session must be disjoint");
            var files = new NfcoreScientificFiles(verified); var source = files.verifyChipseq121Source(); var parameters = files.parameters();
            if (parameters.containsKey("min_reps_consensus") && integer(parameters.get("min_reps_consensus"), 1, 1000000) != minimum) throw new IOException("Declared replicate threshold differs from the typed original parameter");
            if (parameters.containsKey("single_end") && !Boolean.valueOf(single).equals(parameters.get("single_end"))) throw new IOException("Declared read mode differs from the typed original parameter");
            if (parameters.containsKey("narrow_peak") && !Boolean.valueOf(options.get("--peak-type").equals("narrowPeak")).equals(parameters.get("narrow_peak"))) throw new IOException("Declared peak kind differs from the typed original parameter");
            for (String flag : List.of("skip_consensus_peaks", "skip_diff_analysis")) if (parameters.containsKey(flag) && !Boolean.FALSE.equals(parameters.get(flag))) throw new IOException("Original request does not select an active differential branch: " + flag);
            var reference = new LinkedHashMap<String, Long>(); for (var entry : FastaIndexes.build(files.inputPath(text(parameters.get("fasta"))))) reference.put(entry.getContig(), entry.getSize());
            var mapping = NfcoreChipseq121Mappings.read(files.output("pipeline_info/design_reads.csv"), files.output("pipeline_info/design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults());
            var groups = new LinkedHashMap<String, ArrayList<NfcoreChipseq121Mappings.Control>>(); for (var control : mapping.controls()) groups.computeIfAbsent(control.antibody(), key -> new ArrayList<>()).add(control);
            var reports = new ArrayList<Map<String, Object>>(); var warnings = new ArrayList<String>(); warnings.add(COORDINATES);
            var skippedBranches = new ArrayList<String>();
            for (var group : groups.entrySet()) {
                var branch = group.getValue().get(0);
                if (!(branch.replicatesExist() && branch.multipleGroups())) { skippedBranches.add(group.getKey()); continue; }
                String antibody = member(group.getKey()); if (antibody.contains("/")) throw new IOException("Original antibody is not one path component; no alias inferred");
                String peakType = options.get("--peak-type"); var kind = peakType.equals("narrowPeak") ? NfcoreChipseq121Consensus.Kind.NARROW : NfcoreChipseq121Consensus.Kind.BROAD;
                var peakInputs = new ArrayList<NfcoreChipseq121Consensus.PeakSet>(); var libraries = new ArrayList<String>();
                for (var control : group.getValue()) {
                    var peaks = NfcoreChipseq121Consensus.read(files.output("bwa/mergedLibrary/macs/" + peakType + "/" + control.sampleId() + "_peaks." + peakType), kind, NfcoreChipseq121Consensus.Limits.defaults());
                    for (var peak : peaks) { Long length = reference.get(peak.contig()); if (length == null || peak.end() > length) throw new IOException("Original full peak geometry differs from the declared reference"); }
                    peakInputs.add(new NfcoreChipseq121Consensus.PeakSet(control.sampleId(), peaks)); libraries.add(control.sampleId());
                }
                var plan = NfcoreChipseq121Consensus.compute(peakInputs, kind, minimum, NfcoreChipseq121Consensus.Limits.defaults());
                String prefix = "bwa/mergedLibrary/macs/" + peakType + "/consensus/" + antibody + "/" + antibody + ".consensus_peaks";
                try (var bool = Files.newBufferedReader(files.output(prefix + ".boolean.txt"), StandardCharsets.UTF_8);
                     var bed = Files.newBufferedReader(files.output(prefix + ".bed"), StandardCharsets.UTF_8);
                     var saf = Files.newBufferedReader(files.output(prefix + ".saf"), StandardCharsets.UTF_8);
                     var intersections = Files.newBufferedReader(files.output(prefix + ".boolean.intersect.txt"), StandardCharsets.UTF_8)) {
                    var bounds = NfcoreChipseq121Consensus.Limits.defaults(); NfcoreChipseq121Consensus.auditBoolean(bool, plan, bounds); NfcoreChipseq121Consensus.auditIntervals(bed, saf, plan, bounds); NfcoreChipseq121Consensus.auditIntersections(intersections, plan, bounds);
                }
                String directory = prefix.substring(0, prefix.lastIndexOf('/') + 1) + "deseq2", modelPrefix = antibody + ".consensus_peaks";
                Path countFile = files.output(prefix + ".featureCounts.txt"), model = files.output(directory + "/" + modelPrefix + ".dds.rld.RData");
                Path caseRoot = Files.createDirectory(out.resolve(String.format(java.util.Locale.ROOT, "antibody-%06d", reports.size() + 1)));
                String suffix = single ? ".mLb.clN.sorted.bam" : ".mLb.clN.bam";
                Path worker = runtime.inspect(model, countFile, suffix, caseRoot);
                var checked = NfcoreDeseq126Audit.audit(countFile, plan.rows(), libraries, suffix, worker, directory, modelPrefix, files::output);
                checked.put("antibody", antibody); checked.put("original_model_pin", pin(model, 1000000000L, () -> { })); checked.put("complete_featureCounts_pin", pin(countFile, 1000000000L, () -> { })); reports.add(checked);
                for (Object warning : list(checked.get("warnings"))) warnings.add(antibody + ": " + text(warning));
                String originalWarnings = utf8(bytes(worker.resolve("ORIGINAL_WARNINGS.txt"), 1048576)); if (!originalWarnings.isBlank()) warnings.add(antibody + " original postfit warnings:\n" + originalWarnings);
                String diagnostics = utf8(bytes(caseRoot.resolve("process/process.stderr"), 16777216));
                if (diagnostics.contains("Warning") || diagnostics.contains("warning")) warnings.add(antibody + ": original runtime reports warnings in " + out.relativize(caseRoot.resolve("process/process.stderr")) + "; review the retained complete stderr and sessionInfo.txt.\n" + diagnostics);
            }
            if (reports.isEmpty()) throw new IOException("No antibody satisfies the original replicatesExist && multipleGroups differential condition; no audit population substituted");
            runtime.verify(); var after = NfcoreEngineBundle.verify(selected, () -> { });
            if (!verified.attempt().equals(after.attempt()) || !verified.manifest().equals(after.manifest())) throw new IOException("Selected complete result/latest pointer changed during original postfit audit");
            var report = new LinkedHashMap<String, Object>(); report.put("status", "COMPLETE_ORIGINAL_DESEQ126_POSTFIT_COMPONENT_AUDIT_ONLY"); report.put("original_engine_manifest", verified.manifest()); report.put("source", source); report.put("antibodies", reports); report.put("source_ineligible_antibodies", skippedBranches); report.put("warnings", warnings); report.put("process_capacities", limits); report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0);
            writeJson(out.resolve("audit.json"), report);
            Files.writeString(out.resolve("WARNINGS.md"), "# Postfit component only: no scientific result admitted\n\n" + String.join("\n\n", warnings) + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writeJson(out.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("status", "COMPLETE_ORIGINAL_POSTFIT_COMPONENT_ONLY", "audit", pin(out.resolve("audit.json"), 67108864, () -> { }), "scientific_result_admitted", false, "nfcore_tool_credit", 0));
            emit(System.out, "Complete original postfit and native output consistency checks passed for " + reports.size() + " antibody groups.\n" + ACTION); for (String warning : warnings) emit(System.err, warning); return;
        } catch (Exception | LinkageError failure) {
            if (failure instanceof NextflowProcess.Unavailable) code = 3;
            String status = code == 3 ? "SKIP3 ANALYSIS SKIPPED" : "FAIL2 NO COMPONENT RESULT";
            emit(System.err, status + ": " + failure + "\n" + ACTION);
            if (owned && out != null) try {
                writeJson(out.resolve("FAILURE.json"), Map.of("status", code == 3 ? "SKIP3_ANALYSIS_SKIPPED" : "FAIL2_NO_COMPONENT_RESULT", "error", failure.toString(), "required_action", ACTION));
                Files.writeString(out.resolve("WARNINGS.md"), "# " + status + "\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { emit(System.err, "Required action: retain the audit path; diagnostic write failed: " + diagnostic); }
        }
        System.exit(code);
    }
    private static long positive(String value, long maximum) throws IOException { try { if (!value.matches("[0-9]+")) throw new NumberFormatException(); long result = Long.parseLong(value); if (result < 1 || result > maximum) throw new NumberFormatException(); return result; } catch (NumberFormatException invalid) { throw new IOException("Require a positive exact capacity within supported bounds", invalid); } }
    private static long option(Map<String, String> options, String name, String fallback, long maximum) throws IOException { return positive(options.getOrDefault(name, fallback), maximum); }
    private static NextflowProcess.Limits limits(Map<String, String> o) throws IOException {
        return new NextflowProcess.Limits(option(o, "--wall-seconds", "1800", 604800) * 1000, option(o, "--address-space-bytes", "8589934592", 1099511627776L), option(o, "--cpu-seconds", "1800", 604800), option(o, "--file-bytes", "1000000000", 1000000000000L), (int) option(o, "--open-files", "1024", 1048576), option(o, "--workspace-bytes", "17179869184", 1000000000000L), (int) option(o, "--files", "100000", 1000000), (int) option(o, "--processes", "128", 4096), (int) option(o, "--threads", "256", 1048576), option(o, "--sampled-rss-bytes", "2147483648", 1099511627776L), (int) option(o, "--poll-ms", "25", 1000));
    }
    private static void emit(java.io.PrintStream output, String text) { output.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
