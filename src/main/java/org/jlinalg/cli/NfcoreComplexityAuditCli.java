/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete native marked-BAM/duplication output consistency component, with original curve retention. */
public final class NfcoreComplexityAuditCli {
    private NfcoreComplexityAuditCli() { }
    private static final String ACTION = "Required action: this is a source-specific library-complexity component, with no nf-core tool, whole-pipeline or biological admission. It counts the full original marked BAM and checks Picard 2.23.1 derived formulas and complete published histograms. It does not independently mark duplicates, identify optical duplicates or recompute Preseq extrapolation/CI. Retain all raw counts, original absent/nonfinite estimates, coordinate conventions, failed attempts and warnings. FAIL2 means no component result: inspect the named count, integrity, output or capacity discrepancy, correct its cause and retry a NEW directory. No producer work paths or substitute tools are used. Hashes establish integrity, not execution authenticity.";
    public static void main(String[] args) {
        Path out = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(System.out, "--engine-result COMPLETE_ORIGINAL_SESSION_OR_ATTEMPT --out NEW_AUDIT_DIRECTORY\n" + ACTION); return; }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--engine-result", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require unique complete complexity-audit options; see --help");
            if (options.size() != 2) throw new IOException("Declare complete original result and new audit directory explicitly");
            Path selected = Path.of(options.get("--engine-result")).toAbsolutePath().normalize();
            out = Path.of(options.get("--out")).toAbsolutePath().normalize();
            if (out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent()) || out.startsWith(selected) || selected.startsWith(out)) throw new IOException("Require canonical existing parent and disjoint NEW audit directory");
            var verified = NfcoreEngineBundle.verify(selected, () -> { });
            var files = new NfcoreScientificFiles(verified); var source = files.verifyChipseq121Source(); var parameters = files.parameters(); var working = NfcoreChipseq121Complexity.working(verified);
            Path producer = Path.of(working.producer());
            if (out.startsWith(verified.attempt()) || verified.attempt().startsWith(out) || producer.isAbsolute() && (out.startsWith(producer) || producer.startsWith(out))) throw new IOException("Complexity output, original producer and verified attempt must be disjoint");
            Files.createDirectory(out); owned = true;
            var reference = new LinkedHashMap<String, Long>(); for (var entry : FastaIndexes.build(files.inputPath(text(parameters.get("fasta"))))) reference.put(entry.getContig(), entry.getSize());
            var mapping = NfcoreChipseq121Mappings.read(files.output("pipeline_info/design_reads.csv"), files.output("pipeline_info/design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults());
            var ids = new HashSet<String>(); for (var library : mapping.libraries()) ids.add(library.id());
            if (!ids.equals(working.mergedTasks().keySet())) throw new IOException("Complete original MERGED_BAM task set differs from all checked libraries");
            boolean skipPreseq = false;
            if (parameters.containsKey("skip_preseq")) { if (!(parameters.get("skip_preseq") instanceof Boolean flag)) throw new IOException("Original skip_preseq parameter is not typed boolean"); skipPreseq = flag; }
            var reports = new ArrayList<Map<String, Object>>(); var warnings = new ArrayList<String>();
            warnings.add("WARNING: Optical duplicate counts remain original-engine classifications. The native audit verifies their count bounds and use in the source library-size formula; it does not independently infer optical read clusters.");
            warnings.add("WARNING: Preseq estimates and confidence intervals remain original-engine outputs. Complete finite/grid/order/bound consistency is checked; passing this component does not independently validate the extrapolation model or biological library complexity.");
            for (var library : mapping.libraries()) {
                Path bam = NfcoreChipseq121Complexity.mergedBam(working, library.id());
                Path metrics = files.output("bwa/mergedLibrary/picard_metrics/" + library.id() + ".mLb.mkD.MarkDuplicates.metrics.txt");
                Path preseq = skipPreseq ? null : files.output("bwa/mergedLibrary/preseq/" + library.id() + ".ccurve.txt");
                var row = NfcoreChipseq121Complexity.audit(bam, metrics, preseq, library, reference); row.put("complete_marked_BAM_pin", pin(bam, 1000000000000L, () -> { })); row.put("original_metric_pin", pin(metrics, 67108864, () -> { })); reports.add(row);
                for (Object warning : list(row.get("warnings"))) warnings.add(text(warning));
            }
            var after = NfcoreEngineBundle.verify(selected, () -> { });
            if (!verified.attempt().equals(after.attempt()) || !verified.manifest().equals(after.manifest())) throw new IOException("Original result/latest pointer changed during complete complexity audit");
            var report = new LinkedHashMap<String, Object>(); report.put("status", "COMPLETE_NATIVE_PICARD2231_COMPLEXITY_COMPONENT_ONLY"); report.put("original_engine_manifest", verified.manifest()); report.put("source", source); report.put("complete_libraries", reports); report.put("source_skip_preseq", skipPreseq); report.put("warnings", warnings); report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0);
            writeJson(out.resolve("audit.json"), report); Files.writeString(out.resolve("WARNINGS.md"), "# Library-complexity component only\n\n" + String.join("\n\n", warnings) + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writeJson(out.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("status", "COMPLETE_NATIVE_COMPLEXITY_COMPONENT_ONLY", "audit", pin(out.resolve("audit.json"), 67108864, () -> { }), "scientific_result_admitted", false, "nfcore_tool_credit", 0));
            emit(System.out, "Complete original marked-BAM/duplication consistency checks passed for " + reports.size() + " libraries.\n" + ACTION); for (String warning : warnings) emit(System.err, warning);
        } catch (Exception | LinkageError failure) {
            emit(System.err, "FAIL2 NO COMPLEXITY COMPONENT RESULT: " + failure + "\n" + ACTION);
            if (owned && out != null) try {
                writeJson(out.resolve("FAILURE.json"), Map.of("status", "FAIL2_NO_COMPONENT_RESULT", "error", failure.toString(), "required_action", ACTION));
                Files.writeString(out.resolve("WARNINGS.md"), "# FAIL2 NO COMPLEXITY COMPONENT RESULT\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { emit(System.err, "Required action: preserve the audit path; diagnostic write failed: " + diagnostic); }
            System.exit(2);
        }
    }
    private static void emit(java.io.PrintStream output, String text) { output.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
