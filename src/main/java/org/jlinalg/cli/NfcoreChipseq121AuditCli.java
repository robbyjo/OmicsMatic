/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
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
import org.jlinalg.workflow.NfcoreChipseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Native source-specific component audit over complete immutable original workflow objects. */
public final class NfcoreChipseq121AuditCli {
    private NfcoreChipseq121AuditCli() { }
    private static final String ACTION = "Required action: this is a native component audit, with no nf-core tool or biological admission. Require the complete original pipeline/runtime/scientific contracts before downstream claims. Preserve original outputs, IDs, flags, failures and source numeric tokens. FAIL2 means the named contract is incomplete or inconsistent; no component result admitted. Correct the named cause and use a new audit path. Hashes establish integrity, not authenticity. Cell Ranger remains excluded.";
    public static void main(String[] args) {
        Path out = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                emit(System.out, "--engine-result COMPLETE_SESSION_OR_ATTEMPT --peak-type broadPeak|narrowPeak --read-mode paired|single --out NEW_AUDIT_DIRECTORY [--named-view-bytes LIMIT] [--max-bam-records LIMIT]\n" + ACTION); return;
            }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2)
                if (i + 1 >= args.length || !Set.of("--engine-result", "--peak-type", "--read-mode", "--out", "--named-view-bytes", "--max-bam-records").contains(args[i])
                        || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require unique complete native audit options; see --help");
            if (!options.keySet().containsAll(Set.of("--engine-result", "--peak-type", "--read-mode", "--out")) || !Set.of("broadPeak", "narrowPeak").contains(options.get("--peak-type")) || !Set.of("paired", "single").contains(options.get("--read-mode")))
                throw new IOException("Declare the exact peak type and complete original workflow result explicitly");
            out = Path.of(options.get("--out")).toAbsolutePath().normalize();
            if (out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent())) throw new IOException("Require a canonical parent for a new native audit");
            var verified = NfcoreEngineBundle.verify(Path.of(options.get("--engine-result")), () -> { });
            Path selected = Path.of(options.get("--engine-result")).toAbsolutePath().normalize();
            if (out.startsWith(selected) || selected.startsWith(out) || out.startsWith(verified.attempt()) || verified.attempt().startsWith(out))
                throw new IOException("Native audit outputs and the complete original result must be disjoint");
            String producer = text(object(json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root"));
            Path producerPath = Path.of(producer);
            if (producerPath.isAbsolute() && (out.startsWith(producerPath) || producerPath.startsWith(out)))
                throw new IOException("Native audit outputs and the original producer session must be disjoint");
            long viewLimit = capacity(options.getOrDefault("--named-view-bytes", "34359738368"), 1000000000000L);
            long recordLimit = capacity(options.getOrDefault("--max-bam-records", "1000000000"), 1000000000L);
            Files.createDirectory(out); owned = true;
            var files = new NfcoreScientificFiles(verified); var source = files.verifyChipseq121Source();
            var parameters = files.parameters(); String peakType = options.get("--peak-type");
            Path fasta = files.inputPath(text(parameters.get("fasta"))), gtf = files.inputPath(text(parameters.get("gtf")));
            var reference = new LinkedHashMap<String, Long>();
            for (var entry : FastaIndexes.build(fasta)) reference.put(entry.getContig(), entry.getSize());
            var annotation = GtfAnnotations.inspect(gtf, reference);
            var mappings = NfcoreChipseq121Mappings.read(files.output("pipeline_info/design_reads.csv"), files.output("pipeline_info/design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults());
            var readReports = new ArrayList<Map<String, Object>>(); Path views = Files.createDirectory(out.resolve("named-inputs")); long[] copied = {0};
            boolean single = options.get("--read-mode").equals("single");
            if (parameters.containsKey("single_end") && (!(parameters.get("single_end") instanceof Boolean selectedSingle) || selectedSingle != single))
                throw new IOException("Explicit read mode differs from the typed source parameter; no coercion inferred");
            for (var read : mappings.reads()) {
                Path first = files.namedFastq(read.read1(), views, copied, viewLimit);
                if (!single && read.read2().isEmpty()) throw new IOException("Paired source recipe has an absent second checked read; no mate inferred");
                if (!single && files.sameInputFile(read.read1(), read.read2())) throw new IOException("Original checked mates refer to the same captured file identity");
                Path second = single ? null : files.namedFastq(read.read2(), views, copied, viewLimit);
                var stats = FastqIO.inspect(first, second);
                readReports.add(Map.of("sample_id", read.sampleId(), "read1_records", stats.read1().reads(), "read1_bases", stats.read1().bases(),
                    "read2_records", second == null ? 0 : stats.read2().reads(), "single_end_selected", single, "original_read2_field", read.read2()));
            }
            var ips = new LinkedHashMap<String, NfcoreChipseq121Mappings.Control>(); for (var control : mappings.controls()) ips.put(control.sampleId(), control);
            var bamReports = new ArrayList<Map<String, Object>>(); var warnings = new ArrayList<String>();
            for (var library : mappings.libraries()) {
                String prefix = "bwa/mergedLibrary/" + library.id() + ".mLb.clN.sorted.bam";
                Path bam = files.output(prefix); boolean ip = ips.containsKey(library.id()); List<GenomicIntervals.Interval> peaks = List.of();
                String peakPrefix = "bwa/mergedLibrary/macs/" + peakType + "/" + library.id() + "_peaks";
                if (ip) peaks = peaks(files.output(peakPrefix + "." + peakType), peakType);
                var checked = NfcoreChipseq121BamAudit.inspect(bam, library, reference, peaks, recordLimit);
                var row = new LinkedHashMap<String, Object>(); row.put("library_id", library.id()); row.put("read_groups", checked.readGroupCount());
                row.put("bam_pin", pin(bam, 1000000000000L, () -> { }));
                var counts = new LinkedHashMap<String, Object>(checked.frip().document());
                if (!ip) { counts.remove("frip"); counts.remove("assessment"); counts.remove("bedtools_count_sum_numerator"); counts.remove("mapped_records_with_any_peak"); }
                row.put("native_counts", counts); row.put("source_frip_applicable", ip);
                flagstat(files.output("bwa/mergedLibrary/samtools_stats/" + library.id() + ".mLb.clN.sorted.bam.flagstat"), checked.frip());
                if (ip) {
                    String token = fripToken(files.output("bwa/mergedLibrary/macs/" + peakType + "/qc/" + library.id() + "_peaks.FRiP_mqc.tsv"), library.id());
                    row.put("source_frip_token", token); row.put("called_peak_rows", peaks.size());
                    if (checked.frip().fraction().isEmpty()) warnings.add(library.id() + ": FRiP is unassessable because the original QC-passing mapped denominator is zero; original token retained: " + token);
                    else compareScalar(token, checked.frip().fraction().getAsDouble());
                    if (peaks.isEmpty()) warnings.add(library.id() + ": no called peaks; this is not evidence of biological absence.");
                }
                bamReports.add(row);
            }
            var after = NfcoreEngineBundle.verify(selected, () -> { });
            if (!after.attempt().equals(verified.attempt()) || !after.manifest().equals(verified.manifest()))
                throw new IOException("Selected original result/latest pointer changed during native audit; no fallback to an earlier success");
            var report = new LinkedHashMap<String, Object>(); report.put("status", "COMPLETE_NATIVE_CHIPSEQ121_COMPONENT_AUDIT"); report.put("original_engine_manifest", verified.manifest());
            report.put("source", source); report.put("reference_lengths", reference); report.put("GTF_records", annotation.records()); report.put("GTF_exons", annotation.exons());
            report.put("checked_mappings", mappings.document()); report.put("complete_fastq_streams", readReports); report.put("BAM_and_FRiP", bamReports);
            report.put("warnings", warnings); report.put("scientific_result_admitted", false); report.put("nfcore_tool_credit", 0);
            report.put("capacities", Map.of("named_view_bytes", viewLimit, "maximum_BAM_records_per_library", recordLimit));
            report.put("remaining_contracts", "Original managed source/runtime/image admission and complete peak, differential, library-complexity, annotation and other QC scientific output contracts remain required.");
            writeJson(out.resolve("audit.json"), report);
            Files.writeString(out.resolve("WARNINGS.md"), "# Native ChIP-seq component audit only\n\n" + String.join("\n", warnings) + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writeJson(out.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("status", "COMPLETE_COMPONENT_AUDIT_ONLY", "audit", pin(out.resolve("audit.json"), 67108864, () -> { }), "scientific_result_admitted", false, "nfcore_tool_credit", 0));
            emit(System.out, "Native original-source, complete FASTQ/GTF/reference, BAM/sample and FRiP component audit completed.\n" + ACTION);
            for (String warning : warnings) emit(System.err, "WARNING: " + warning);
        } catch (Exception | LinkageError failed) {
            emit(System.err, "FAIL2 native ChIP-seq component audit: " + failed + "\n" + ACTION);
            if (owned && out != null) try {
                writeJson(out.resolve("FAILURE.json"), Map.of("status", "FAIL2_NO_COMPONENT_RESULT", "error", failed.toString(), "required_action", ACTION));
                Files.writeString(out.resolve("WARNINGS.md"), "# FAIL2 no component result admitted\n\n" + failed + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { emit(System.err, "Required action: preserve the audit directory; diagnostic write failed: " + diagnostic); }
            System.exit(2);
        }
    }
    private static long capacity(String text, long maximum) throws IOException {
        try { if (!text.matches("[0-9]+")) throw new NumberFormatException(); long value = Long.parseLong(text);
            if (value < 1 || value > maximum) throw new NumberFormatException(); return value;
        } catch (NumberFormatException invalid) { throw new IOException("Require a positive explicit audit capacity within supported bounds", invalid); }
    }
    private static List<GenomicIntervals.Interval> peaks(Path file, String kind) throws IOException {
        var result = new ArrayList<GenomicIntervals.Interval>();
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line; while ((line = input.readLine()) != null) {
                if (line.length() > 1048576 || result.size() >= 10000000) throw new IOException("Complete peak table exceeds declared component capacity");
                String[] f = line.split("\t", -1); if (f.length != (kind.equals("broadPeak") ? 9 : 10)) throw new IOException("Original MACS2 peak field count differs");
                try { result.add(new GenomicIntervals.Interval(f[0], Long.parseLong(f[1]), Long.parseLong(f[2]), f[3], f[5])); }
                catch (IllegalArgumentException invalid) { throw new IOException("Invalid original MACS2 interval; coordinates/IDs were not repaired", invalid); }
            }
        }
        return List.copyOf(result);
    }
    private static void flagstat(Path file, org.jlinalg.workflow.NfcoreChipseq121Frip.Summary counts) throws IOException {
        int total = 0, mapped = 0;
        for (String line : utf8(bytes(file, 1048576)).split("\n")) {
            var m = java.util.regex.Pattern.compile("^([0-9]+) \\+ ([0-9]+) (.*)$").matcher(line);
            if (!m.matches()) continue; long a = Long.parseLong(m.group(1)), b = Long.parseLong(m.group(2));
            if (m.group(3).startsWith("in total ")) { if (Math.addExact(a, b) != counts.records()) throw new IOException("BAM physical count and original flagstat total differ"); total++; }
            if (m.group(3).startsWith("mapped (")) { if (a != counts.qcPassingMapped() || b != counts.qcFailedMapped()) throw new IOException("BAM mapped QC partitions and original flagstat differ"); mapped++; }
        }
        if (total != 1 || mapped != 1) throw new IOException("Original SAMtools1.10 flagstat total/mapped family differs");
    }
    private static String fripToken(Path file, String library) throws IOException {
        String result = null;
        for (String line : utf8(bytes(file, 1048576)).split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            if (f.length != 2 || !f[0].equals(library) || result != null) throw new IOException("Original FRiP scalar sample/row identity differs");
            result = f[1];
        }
        if (result == null) throw new IOException("Original FRiP scalar is missing"); return result;
    }
    private static void compareScalar(String original, double expected) throws IOException {
        double observed; try { observed = Double.parseDouble(original); } catch (NumberFormatException invalid) { throw new IOException("Original FRiP token is unassessable and retained unchanged: " + original, invalid); }
        if (!Double.isFinite(observed)) throw new IOException("Original nonfinite FRiP token retained without replacement: " + original);
        double tolerance = expected == 0 ? 0 : 0.5 * Math.pow(10, Math.floor(Math.log10(Math.abs(expected))) - 5) + 2 * Math.ulp(expected);
        if (Math.abs(observed - expected) > tolerance) throw new IOException("Original FRiP differs from native counts beyond source six-significant-digit display precision");
    }
    private static void emit(java.io.PrintStream out, String text) { out.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
