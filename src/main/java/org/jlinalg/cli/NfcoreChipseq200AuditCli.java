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
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.GtfAnnotations;
import org.jlinalg.workflow.NfcoreChipseq200Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete immutable source/image/mapping/FASTQ/BAM/FRiP component; remaining v2 contracts stay open. */
public final class NfcoreChipseq200AuditCli {
    private NfcoreChipseq200AuditCli() { }
    private static final String ACTION = "Required action: this is a complete selected ChIP-seq2.0.0 component audit, with no nf-core catalog-tool or biological admission. Require the remaining original consensus, duplication/library-complexity, annotation/QC and DESeq2 QC contracts before a full profile result. Read audit.json and WARNINGS.md. FAIL2 admits no component result: retain complete original inputs/outputs/IDs, source missing values and failed diagnostics, resolve the named cause and use a NEW audit path. No file/ID/flag/coordinate repair, shortened curve, input subset or producer-path fallback is inferred. Runtime/image execution admission remains in the original source-control receipts. Hashes establish integrity, not author history, authenticity or biological accuracy. Cell Ranger remains excluded.";
    public static void main(String[] args) {
        Path out = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(System.out, "--engine-result COMPLETE_SESSION_OR_ATTEMPT --image-set ORIGINAL_CAPTURED_IMAGE_DECLARATION --out NEW_AUDIT\n" + ACTION); return; }
            var options = new LinkedHashMap<String, String>(); for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--engine-result", "--image-set", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require complete unique v2 audit options; see --help"); if (options.size() != 3) throw new IOException("Require explicit complete engine result, original image declaration and NEW output");
            Path selected = Path.of(options.get("--engine-result")).toAbsolutePath().normalize(); out = Path.of(options.get("--out")).toAbsolutePath().normalize(); var verified = NfcoreEngineBundle.verify(selected, () -> { }); String producer = text(object(json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root")); Path original = Path.of(producer);
            if (out.startsWith(selected) || selected.startsWith(out) || out.startsWith(verified.attempt()) || verified.attempt().startsWith(out) || original.isAbsolute() && (out.startsWith(original) || original.startsWith(out)) || out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent())) throw new IOException("Require a NEW canonical audit disjoint from original result/producer"); Files.createDirectory(out); owned = true;
            Path imageDeclaration = Path.of(options.get("--image-set")).toAbsolutePath().normalize(); Pin imageDeclarationPin = pin(imageDeclaration, 1048576, () -> { }); var data = new NfcoreChipseq200Files(verified); data.verifySource(); var images = NfcoreChipseq200ImageAudit.audit(data, imageDeclaration); var parameters = data.files.parameters();
            NfcoreChipseq200Mappings.Plan plan; try (var input = Files.newBufferedReader(data.files.inputPath(text(parameters.get("input"))), StandardCharsets.UTF_8)) { plan = NfcoreChipseq200Mappings.inspect(input, NfcoreChipseq200Mappings.Limits.defaults()); }
            try (var checked = Files.newBufferedReader(data.files.output("pipeline_info/samplesheet.valid.csv"), StandardCharsets.UTF_8)) { NfcoreChipseq200Mappings.auditChecked(checked, plan, NfcoreChipseq200Mappings.Limits.defaults()); }
            plan.requireLiteralIdentifiers(); plan.requireConsistentLibraryMetadata(); var reference = new LinkedHashMap<String, Long>(); for (var row : FastaIndexes.build(data.files.inputPath(text(parameters.get("fasta"))))) reference.put(row.getContig(), row.getSize()); var gtf = GtfAnnotations.inspect(data.files.inputPath(text(parameters.get("gtf"))), reference);
            var libraries = new LinkedHashMap<String, List<NfcoreChipseq200Mappings.Read>>(); var readReports = new ArrayList<Map<String, Object>>(); Path views = Files.createDirectory(out.resolve("named-inputs")); long[] copied = {0};
            for (var read : plan.reads()) {
                libraries.computeIfAbsent(read.libraryId(), ignored -> new ArrayList<>()).add(read); if (!read.singleEnd() && data.files.sameInputFile(read.read1(), read.read2())) throw new IOException("Original mates have identical physical captured input identity"); Path first = data.files.namedFastq(read.read1(), views, copied, 34359738368L), second = read.singleEnd() ? null : data.files.namedFastq(read.read2(), views, copied, 34359738368L); var full = FastqIO.inspect(first, second); readReports.add(Map.of("technical_read_group", read.id(), "biological_library", read.libraryId(), "read1_records", full.read1().reads(), "read1_bases", full.read1().bases(), "read2_records", second == null ? 0 : full.read2().reads(), "single_end", read.singleEnd()));
            }
            String aligner = parameters.containsKey("aligner") ? text(parameters.get("aligner")) : "bwa"; if (!Set.of("bwa", "bowtie2", "star", "chromap").contains(aligner)) throw new IOException("Unknown original aligner selection"); boolean narrow = Boolean.TRUE.equals(parameters.get("narrow_peak")); String type = narrow ? "narrowPeak" : "broadPeak", center = parameters.containsKey("seq_center") && parameters.get("seq_center") != null && !text(parameters.get("seq_center")).isEmpty() ? text(parameters.get("seq_center")) : null; var bamReports = new ArrayList<Map<String, Object>>();
            for (var entry : libraries.entrySet()) {
                String library = entry.getKey(), prefix = aligner + "/mergedLibrary/" + library + ".mLb.clN.sorted.bam"; boolean ip = !entry.getValue().get(0).antibody().isEmpty(); List<GenomicIntervals.Interval> peaks = List.of(); String peakPrefix = aligner + "/mergedLibrary/macs2/" + type + "/" + library;
                if (ip) peaks = peaks(data.files.output(peakPrefix + "_peaks." + type), narrow);
                var summary = NfcoreChipseq200BamAudit.inspect(data.files.output(prefix), entry.getValue(), reference, peaks, center, 1000000000L); flagstat(data.files.output(aligner + "/mergedLibrary/samtools_stats/" + library + ".mLb.clN.sorted.bam.flagstat"), summary);
                var counts = new LinkedHashMap<String, Object>(summary.document()); if (!ip) for (String name : List.of("frip", "assessment", "bedtools_count_sum_numerator", "mapped_records_with_any_peak")) counts.remove(name);
                var row = new LinkedHashMap<String, Object>(); row.put("library_id", library); row.put("technical_read_groups", entry.getValue().stream().map(NfcoreChipseq200Mappings.Read::id).toList()); row.put("full_BAM_pin", pin(data.files.output(prefix), 1000000000000L, () -> { })); row.put("native_counts", counts); row.put("source_FRiP_applicable", ip);
                if (ip) {
                    Path rawFrip = data.work(data.task("FRIP_SCORE", library), library + ".FRiP.txt"); byte[] body = bytes(rawFrip, 1048576); String[] source = utf8(body).strip().split("\t", -1); if (source.length != 2 || !source[0].equals(library) || summary.fraction().isEmpty()) throw new IOException("Source FRiP sample/scalar is absent or unassessable; no finite value substituted"); printed(source[1], summary.fraction().getAsDouble());
                    String qc = aligner + "/mergedLibrary/macs2/" + type + "/qc/" + library;
                    concatenation(data.files.inputMember(data.files.alias("workflow") + "/assets/multiqc/frip_score_header.txt"), body, data.files.output(qc + ".FRiP_mqc.tsv"));
                    concatenation(data.files.inputMember(data.files.alias("workflow") + "/assets/multiqc/peak_count_header.txt"), (library + "\t" + peaks.size() + "\n").getBytes(StandardCharsets.UTF_8), data.files.output(qc + ".peak_count_mqc.tsv"));
                    row.put("source_FRiP_token", source[1]); row.put("called_peak_rows", peaks.size()); row.put("complete_peak_count_and_FRiP_MultiQC_checked", true);
                }
                bamReports.add(row);
            }
            var after = NfcoreEngineBundle.verify(selected, () -> { }); if (!after.attempt().equals(verified.attempt()) || !after.manifest().equals(verified.manifest()) || !pin(imageDeclaration, 1048576, () -> { }).equals(imageDeclarationPin)) throw new IOException("Latest original result/image declaration changed during v2 audit");
            var report = new LinkedHashMap<String, Object>(); report.put("status", "COMPLETE_SELECTED_NATIVE_CHIPSEQ200_COMPONENT"); report.put("original_engine_manifest", verified.manifest()); report.put("source_image_declaration", imageDeclarationPin); report.put("source_files_checked", 203); report.put("task_image_binding", images); report.put("source_rows", plan.sourceRows().size()); report.put("original_checked_rows", plan.reads().size()); report.put("literal_scientific_identifiers_required", true); report.put("consistent_library_metadata_required", true); report.put("complete_FASTQs", readReports); report.put("reference_contigs", reference.size()); report.put("GTF_records", gtf.records()); report.put("complete_BAMs_and_FRiP", bamReports); report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0); writeJson(out.resolve("audit.json"), report); Files.writeString(out.resolve("WARNINGS.md"), "# Selected native v2 component only\n\n" + String.join("\n", plan.warnings()) + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); writeJson(out.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("status", "complete-v2-source-mapping-input-BAM-image-component-only", "audit", pin(out.resolve("audit.json"), 67108864, () -> { }), "scientific_result_admitted", false, "nfcore_tool_credit", 0)); emit(System.out, "Complete selected native ChIP-seq2.0.0 component passed.\n" + ACTION);
        } catch (Exception | LinkageError failure) { emit(System.err, "FAIL2 NO V2 COMPONENT RESULT: " + failure + "\n" + ACTION); if (owned && out != null) try { writeJson(out.resolve("FAILURE.json"), Map.of("status", "FAIL2", "error", failure.toString(), "required_action", ACTION)); Files.writeString(out.resolve("WARNINGS.md"), "# FAIL2\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); } catch (IOException diagnostic) { emit(System.err, "Retain the failed audit; diagnostic write failed: " + diagnostic); } System.exit(2); }
    }
    private static List<GenomicIntervals.Interval> peaks(Path file, boolean narrow) throws IOException {
        var result = new ArrayList<GenomicIntervals.Interval>(); for (String line : utf8(bytes(file, 67108864)).lines().toList()) { if (line.length() > 1048576 || result.size() >= 1000000) throw new IOException("Complete peak table exceeds component capacity"); String[] f = line.split("\t", -1); if (f.length != (narrow ? 10 : 9)) throw new IOException("Original MACS2 peak columns differ"); result.add(new GenomicIntervals.Interval(f[0], Long.parseLong(f[1]), Long.parseLong(f[2]), f[3], f[5])); } return List.copyOf(result);
    }
    private static void flagstat(Path file, org.jlinalg.workflow.NfcoreChipseq121Frip.Summary expected) throws IOException {
        boolean total = false, mapped = false; for (String line : utf8(bytes(file, 1048576)).lines().toList()) { var match = java.util.regex.Pattern.compile("^([0-9]+) \\+ ([0-9]+) (.*)$").matcher(line); if (!match.matches()) continue; long first = Long.parseLong(match.group(1)), second = Long.parseLong(match.group(2)); if (match.group(3).startsWith("in total ")) { if (total || Math.addExact(first, second) != expected.records()) throw new IOException("Complete BAM and original flagstat count differ"); total = true; } if (match.group(3).startsWith("mapped (")) { if (mapped || first != expected.qcPassingMapped() || second != expected.qcFailedMapped()) throw new IOException("Complete BAM QC-mapped flagstat partitions differ"); mapped = true; } } if (!total || !mapped) throw new IOException("Original complete flagstat lacks required fields");
    }
    private static void concatenation(Path header, byte[] body, Path source) throws IOException { byte[] first = bytes(header, 1048576), actual = bytes(source, 2097152), wanted = new byte[Math.addExact(first.length, body.length)]; System.arraycopy(first, 0, wanted, 0, first.length); System.arraycopy(body, 0, wanted, first.length, body.length); if (!java.util.Arrays.equals(actual, wanted)) throw new IOException("Complete source header/body MultiQC concatenation differs"); }
    private static void printed(String token, double expected) throws IOException {
        double observed = Double.parseDouble(token); if (!Double.isFinite(observed) || expected == 0 || observed == 0) { if (observed != expected) throw new IOException("Source FRiP finite/zero assessment differs"); return; }
        // Original awk default OFMT is %.6g: compare at half a unit of its final significant digit.
        double halfUnit = .500001 * Math.pow(10, Math.floor(Math.log10(Math.abs(expected))) - 5);
        if (Math.abs(observed - expected) > Math.max(halfUnit, 16 * Math.ulp(expected))) throw new IOException("Complete original BedTools2.30 FRiP differs at source display precision");
    }
    private static void emit(java.io.PrintStream output, String text) { output.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
