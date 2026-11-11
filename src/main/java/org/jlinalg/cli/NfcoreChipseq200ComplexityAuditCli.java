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
import org.jlinalg.workflow.NfcoreChipseq200Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete v2 technical-library duplication counters and full original Preseq3.1.2 curves. */
public final class NfcoreChipseq200ComplexityAuditCli {
    private NfcoreChipseq200ComplexityAuditCli() { }
    static final String ACTION = "Required action: source provider version is literally Picard 2.27.4-SNAPSHOT, not an authenticated official-release binary. The actual provider's five numerical methods and 20 original cases justify reuse of unchanged native arithmetic. Source optical clustering, duplicate marking and Preseq3.1.2 extrapolation remain upstream methods. Complete per-technical-LB counters, fractions, size/absence, every applicable ROI bin, duplicate-set histograms and all 10,000 default curve rows are checked. A .mLb.clN Preseq filename is the original label even though the source feeds its marked BAM; no input stage is inferred from that label. Original missing/nonfinite estimates, provider/locale warnings and failed attempts remain retained. FAIL2 admits no component result; inspect the named cause and WARNINGS.md, preserve originals and retry a NEW path after resolving it. No finite estimate, truncated curve, counter repair or biological finding is substituted. Complete the remaining v2 contracts before full-profile claims. Zero catalog-tool or biological admission. Cell Ranger remains excluded.";
    public static void main(String[] args) {
        Path out = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(System.out, "--engine-result COMPLETE_V2_ATTEMPT --out NEW_AUDIT\n" + ACTION); return; }
            var options = new LinkedHashMap<String, String>(); for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--engine-result", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require unique complete v2 complexity options"); if (options.size() != 2) throw new IOException("Declare complete v2 result and NEW audit explicitly");
            Path selected = Path.of(options.get("--engine-result")).toAbsolutePath().normalize(); out = Path.of(options.get("--out")).toAbsolutePath().normalize(); var verified = NfcoreEngineBundle.verify(selected, () -> { }); Path producer = Path.of(text(object(json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root"))); if (out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent()) || out.startsWith(selected) || selected.startsWith(out) || out.startsWith(verified.attempt()) || verified.attempt().startsWith(out) || producer.isAbsolute() && (out.startsWith(producer) || producer.startsWith(out))) throw new IOException("Require NEW canonical audit disjoint from original producer/result"); Files.createDirectory(out); owned = true;
            var data = new NfcoreChipseq200Files(verified); data.verifySource(); var mappings = NfcoreChipseq200Peaks.mappings(data); var parameters = data.files.parameters(); if (Boolean.TRUE.equals(parameters.get("skip_preseq"))) throw new IOException("Original request omits full Preseq branch; no complete curve contract admitted"); String aligner = parameters.containsKey("aligner") ? text(parameters.get("aligner")) : "bwa", center = parameters.containsKey("seq_center") && parameters.get("seq_center") != null && !text(parameters.get("seq_center")).isEmpty() ? text(parameters.get("seq_center")) : null;
            var reference = new LinkedHashMap<String, Long>(); for (var row : FastaIndexes.build(data.files.inputPath(text(parameters.get("fasta"))))) reference.put(row.getContig(), row.getSize()); var libraries = new LinkedHashMap<String, List<NfcoreChipseq200Mappings.Read>>(); for (var row : mappings.reads()) libraries.computeIfAbsent(row.libraryId(), ignored -> new ArrayList<>()).add(row); var reports = new ArrayList<Map<String, Object>>();
            for (var library : libraries.entrySet()) {
                String name = library.getKey(); Path bam = data.work(data.task("PICARD_MARKDUPLICATES", name), name + ".mLb.mkD.sorted.bam"), metrics = data.files.output(aligner + "/mergedLibrary/picard_metrics/" + name + ".mLb.mkD.sorted.MarkDuplicates.metrics.txt"), curve = data.files.output(aligner + "/mergedLibrary/preseq/" + name + ".mLb.clN.lc_extrap.txt"); data.task("PRESEQ_LCEXTRAP", name);
                var result = NfcoreChipseq200Complexity.audit(bam, metrics, curve, name, library.getValue(), reference, center); reports.add(result);
            }
            var after = NfcoreEngineBundle.verify(selected, () -> { }); if (!after.attempt().equals(verified.attempt()) || !after.manifest().equals(verified.manifest())) throw new IOException("Original result/latest changed during complexity audit"); var report = new LinkedHashMap<String, Object>(); report.put("status", "COMPLETE_ORIGINAL_CHIPSEQ200_COMPLEXITY_COMPONENT_ONLY"); report.put("original_engine_manifest", verified.manifest()); report.put("literal_picard_provider_version", "2.27.4-SNAPSHOT"); report.put("source_Preseq_version", "3.1.2"); report.put("libraries", reports); report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0); writeJson(out.resolve("audit.json"), report); Files.writeString(out.resolve("WARNINGS.md"), "# Original v2 complexity component only\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); writeJson(out.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("status", "complete-original-v2-complexity-component-only", "audit", pin(out.resolve("audit.json"), 67108864, () -> { }), "scientific_result_admitted", false, "nfcore_tool_credit", 0)); emit(System.out, "Complete original v2 library complexity component passed for " + reports.size() + " biological libraries.\n" + ACTION);
        } catch (Exception | LinkageError failure) { emit(System.err, "FAIL2 NO V2 COMPLEXITY COMPONENT: " + failure + "\n" + ACTION); if (owned && out != null) try { writeJson(out.resolve("FAILURE.json"), Map.of("status", "FAIL2", "error", failure.toString(), "required_action", ACTION)); Files.writeString(out.resolve("WARNINGS.md"), "# FAIL2\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); } catch (IOException diagnostic) { emit(System.err, "Retain the failed audit; diagnostic write failed: " + diagnostic); } System.exit(2); }
    }
    private static void emit(java.io.PrintStream output, String text) { output.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
