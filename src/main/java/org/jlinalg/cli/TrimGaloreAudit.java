/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.TrimGaloreReads;
import static org.jlinalg.cli.FollowupSupport.*;

/** Independent source-span and complete ordinary final-stream verification. */
final class TrimGaloreAudit {
    private TrimGaloreAudit() { }
    record Span(int first, boolean ambiguous) { }
    record Result(Map<String, Long> counts, List<String> warnings, List<Path> outputs, List<String> labels) { }

    /** Linear-time matching of sequence AND quality positions; ambiguity is retained. */
    static Span span(FastqIO.Read source, FastqIO.Read output) throws IOException {
        if (!source.header().equals(output.header())) throw new IOException("Original Cutadapt changed a complete read header");
        int size = output.sequence().length();
        if (size == 0) return new Span(0, !source.sequence().isEmpty());
        int[] prefix = new int[size];
        for (int i = 1, matched = 0; i < size; i++) {
            while (matched > 0 && !same(output, i, output, matched)) matched = prefix[matched - 1];
            if (same(output, i, output, matched)) matched++;
            prefix[i] = matched;
        }
        int first = -1;
        for (int i = 0, matched = 0; i < source.sequence().length(); i++) {
            while (matched > 0 && !same(source, i, output, matched)) matched = prefix[matched - 1];
            if (same(source, i, output, matched)) matched++;
            if (matched == size) {
                if (first >= 0) return new Span(first, true);
                first = i - size + 1; matched = prefix[matched - 1];
            }
        }
        if (first < 0) throw new IOException("Original Cutadapt output is not an unchanged contiguous source sequence/quality span");
        return new Span(first, false);
    }
    private static boolean same(FastqIO.Read a, int i, FastqIO.Read b, int j) {
        return a.sequence().charAt(i) == b.sequence().charAt(j) && a.quality().charAt(i) == b.quality().charAt(j);
    }

    private static void normalized(Path raw, String expected) throws Exception {
        if (hash(raw).equals(expected)) return;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = new BufferedInputStream(Files.newInputStream(raw))) {
            int next; boolean carriage = false;
            while ((next = input.read()) >= 0) {
                if (carriage && next != '\n') digest.update((byte) '\r');
                if (next != '\r') digest.update((byte) next);
                carriage = next == '\r';
            }
            if (carriage) digest.update((byte) '\r');
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) throw new IOException("Cutadapt transport changed bytes beyond stdout CRLF normalization");
    }

    private static Map<String, Path> phases(Path original) throws Exception {
        var result = new HashMap<String, Path>();
        try (var paths = Files.list(original.resolve("transport-audit"))) {
            for (Path record : paths.filter(p -> p.getFileName().toString().matches("cutadapt-[0-9]{3}\\.json")).sorted().toList()) {
                Map<?, ?> data = RegenieRuntime.object(record);
                if (((Number) data.get("exit_code")).intValue() != 0 || data.get("error") != null || !(data.get("arguments") instanceof List<?> arguments)
                        || !(data.get("files") instanceof Map<?, ?> files)) throw new IOException("Original Cutadapt invocation failed or lacks raw evidence");
                for (var file : files.entrySet()) {
                    Path path = RegenieRuntime.contained(record.getParent(), String.valueOf(file.getKey()));
                    if (!hash(path).equals(file.getValue())) throw new IOException("Original Cutadapt evidence changed: " + path);
                }
                String stem = record.getFileName().toString().replaceFirst("\\.json$", "");
                Map<?, ?> modules = RegenieRuntime.object(record.resolveSibling(stem + ".modules.json"));
                if (!(modules.get("unexpected_module_files") instanceof List<?> unexpected) || !unexpected.isEmpty()) throw new IOException("Original Cutadapt imported unsealed code");
                Path output = record.resolveSibling(stem + ".stdout-lf");
                normalized(record.resolveSibling(stem + ".stdout-raw"), hash(output));
                if (arguments.contains("--version")) {
                    if (!Files.readString(output).strip().equals("5.2")) throw new IOException("Actual original Cutadapt version differs");
                    continue;
                }
                String name = String.valueOf(arguments.get(arguments.size() - 1));
                if (!List.of("reads1.fastq", "reads2.fastq").contains(name) || result.put(name, output) != null)
                    throw new IOException("Unexpected or repeated ordinary Cutadapt phase");
            }
        }
        return result;
    }

    private static long reported(Path report, String regex) throws IOException {
        var match = Pattern.compile(regex, Pattern.MULTILINE).matcher(Files.readString(report));
        if (!match.find()) throw new IOException("Original report omits required counter/setting: " + report);
        long value = Long.parseLong(match.group(1).replace(",", ""));
        if (match.find()) throw new IOException("Original report repeats a required counter/setting: " + report);
        return value;
    }

    private static void emission(FastqIO.Reader stream, FastqIO.Read expected, boolean present, String label, long ordinal) throws IOException {
        if (!present) return;
        FastqIO.Read actual = stream.next();
        if (!expected.equals(actual)) throw new IOException("Original " + label + " stream differs from native finalization at input ordinal " + ordinal);
    }

    static Result verify(Path directory, String release, String operation, boolean paired, Map<String, Object> settings,
                         Map<String, Object> session) throws Exception {
        Path original = directory.resolve("original"); boolean trim = operation.equals("trim");
        var phases = phases(original);
        if (trim && !phases.keySet().equals(paired ? java.util.Set.of("reads1.fastq", "reads2.fastq") : java.util.Set.of("reads1.fastq"))
                || !trim && !phases.isEmpty()) throw new IOException("Actual original Cutadapt phases differ from the declared operation");
        int minimum = 0;
        if (trim) {
            String label = paired ? "^Minimum required sequence length for both reads before a sequence pair gets removed:\\s*(\\d+) bp"
                    : "^Minimum required sequence length before a sequence gets removed:\\s*(\\d+) bp";
            minimum = Math.toIntExact(reported(original.resolve("reads1.fastq_trimming_report.txt"), label));
            if (paired && reported(original.resolve("reads2.fastq_trimming_report.txt"), label) != minimum)
                throw new IOException("Original mates resolved different minimum lengths");
            if (settings.containsKey("minimum_length") && ((Number) settings.get("minimum_length")).intValue() != minimum) throw new IOException("Original minimum length differs from the declared explicit value");
        }
        session.put("resolved_original_minimum_length", trim ? minimum : "not-applicable-hardtrim");
        var version = release.equals("0.6.7") ? TrimGaloreReads.Release.V0_6_7 : TrimGaloreReads.Release.V0_6_10;
        var filters = trim ? new TrimGaloreReads.Filters(version, minimum, integer(settings, "maximum_length", 0),
                settings.get("maximum_n") instanceof Number n ? n.doubleValue() : null) : null;
        var clips1 = new TrimGaloreReads.Clips(integer(settings, "clip_r1", 0), integer(settings, "three_prime_clip_r1", 0));
        var clips2 = new TrimGaloreReads.Clips(integer(settings, "clip_r2", 0), integer(settings, "three_prime_clip_r2", 0));
        boolean rescue = Boolean.TRUE.equals(settings.get("retain_unpaired"));
        var rescueOptions = new TrimGaloreReads.Rescue(rescue, optional(settings, "orphan_min1"), optional(settings, "orphan_min2"));
        String hard = trim ? "" : "." + integer(settings, "bases", 0) + "bp_" + (operation.equals("hardtrim5") ? "5prime" : "3prime") + ".fq";
        Path output1 = original.resolve(trim ? paired ? "reads1_val_1.fq" : "reads1_trimmed.fq" : "reads1" + hard);
        Path output2 = paired ? original.resolve(trim ? "reads2_val_2.fq" : "reads2" + hard) : null;
        var counts = new LinkedHashMap<String, Long>(); var decisions = new EnumMap<TrimGaloreReads.Decision, Long>(TrimGaloreReads.Decision.class);
        long units = 0, retained = 0, rescued1 = 0, rescued2 = 0, inputBases = 0, outputBases = 0, ambiguous = 0, guarded = 0, oversized = 0;
        try (var in1 = new FastqIO.Reader(original.resolve("reads1.fastq")); var in2 = paired ? new FastqIO.Reader(original.resolve("reads2.fastq")) : null;
             var phase1 = trim ? new FastqIO.Reader(phases.get("reads1.fastq")) : null; var phase2 = trim && paired ? new FastqIO.Reader(phases.get("reads2.fastq")) : null;
             var out1 = new FastqIO.Reader(output1); var out2 = paired ? new FastqIO.Reader(output2) : null;
             var orphan1 = rescue ? new FastqIO.Reader(original.resolve("reads1_unpaired_1.fq")) : null;
             var orphan2 = rescue ? new FastqIO.Reader(original.resolve("reads2_unpaired_2.fq")) : null;
             var ledger = Files.newBufferedWriter(directory.resolve("read-status.tsv"))) {
            ledger.write("ordinal\tread1_header_json\tread2_header_json\tdecision\tretained\trescue1\trescue2\tread1_phase_span_first_zero_based\tread2_phase_span_first_zero_based\tphase_span_ambiguous\n");
            FastqIO.Read a;
            while ((a = in1.next()) != null) {
                units = Math.addExact(units, 1); FastqIO.Read b = paired ? in2.next() : null;
                if (paired && (b == null || !a.pairId().equals(b.pairId()))) throw new IOException("Staged paired source identities/count/order changed");
                inputBases = Math.addExact(inputBases, a.sequence().length() + (b == null ? 0L : b.sequence().length()));
                FastqIO.Read x = a, y = b; Span span1 = new Span(0, false), span2 = new Span(0, false);
                if (trim) {
                    x = phase1.next(); y = paired ? phase2.next() : null;
                    if (x == null || paired && y == null) throw new IOException("Original Cutadapt omitted input observations");
                    span1 = span(a, x); if (paired) span2 = span(b, y);
                    ambiguous += (span1.ambiguous() ? 1 : 0) + (paired && span2.ambiguous() ? 1 : 0);
                }
                boolean keep, save1 = false, save2 = false; TrimGaloreReads.Decision decision;
                if (!trim) {
                    var end = operation.equals("hardtrim5") ? TrimGaloreReads.End.FIVE_PRIME : TrimGaloreReads.End.THREE_PRIME;
                    x = TrimGaloreReads.hardtrim(x, integer(settings, "bases", 0), end); if (paired) y = TrimGaloreReads.hardtrim(y, integer(settings, "bases", 0), end);
                    keep = true; decision = TrimGaloreReads.Decision.RETAINED;
                } else if (!paired) {
                    var result = TrimGaloreReads.single(x, clips1, filters); x = result.clipped(); keep = result.retained(); decision = result.decision();
                    guarded += (result.clipping().skippedFivePrime() ? 1 : 0) + (result.clipping().skippedThreePrime() ? 1 : 0);
                } else {
                    var result = TrimGaloreReads.paired(x, y, clips1, clips2, filters, rescueOptions); x = result.read1().read(); y = result.read2().read();
                    keep = result.retained(); save1 = result.rescueRead1(); save2 = result.rescueRead2(); decision = result.decision();
                    for (var clipping : List.of(result.read1(), result.read2())) guarded += (clipping.skippedFivePrime() ? 1 : 0) + (clipping.skippedThreePrime() ? 1 : 0);
                }
                emission(out1, x, keep, "read1", units); if (paired) emission(out2, y, keep, "read2", units);
                emission(orphan1, x, save1, "orphan1", units); emission(orphan2, y, save2, "orphan2", units);
                if (keep) { retained++; outputBases = Math.addExact(outputBases, x.sequence().length() + (paired ? y.sequence().length() : 0L)); }
                if (save1) { rescued1++; outputBases = Math.addExact(outputBases, x.sequence().length()); }
                if (save2) { rescued2++; outputBases = Math.addExact(outputBases, y.sequence().length()); }
                if (trim && filters.maximumLength() > 0) oversized += (save1 && x.sequence().length() > filters.maximumLength() ? 1 : 0) + (save2 && y.sequence().length() > filters.maximumLength() ? 1 : 0);
                decisions.merge(decision, 1L, Math::addExact);
                ledger.write(units + "\t" + RegenieRuntime.json(a.header()) + "\t" + (paired ? RegenieRuntime.json(b.header()) : "NA") + "\t" + decision + "\t" + keep + "\t" + save1 + "\t" + save2
                        + "\t" + (span1.ambiguous() ? "NA" : span1.first()) + "\t" + (!paired || span2.ambiguous() ? "NA" : span2.first()) + "\t" + (span1.ambiguous() || paired && span2.ambiguous()) + "\n");
            }
            if (units == 0 || in2 != null && in2.next() != null || phase1 != null && phase1.next() != null || phase2 != null && phase2.next() != null || out1.next() != null || out2 != null && out2.next() != null
                    || orphan1 != null && orphan1.next() != null || orphan2 != null && orphan2.next() != null) throw new IOException("Empty input or extra source/phase/final/orphan observations after complete audit");
        }
        if (trim) for (int mate = 1; mate <= (paired ? 2 : 1); mate++) {
            Path phase = phases.get("reads" + mate + ".fastq"); Path errors = phase.resolveSibling(phase.getFileName().toString().replace(".stdout-lf", ".stderr-raw"));
            if (reported(errors, "^Total reads processed:\\s*([0-9,]+)") != units
                    || reported(original.resolve("reads" + mate + ".fastq_trimming_report.txt"), "^([0-9,]+) sequences processed in total") != units)
                throw new IOException("Original Cutadapt/Trim Galore total differs from every audited input record");
        }
        counts.put("input_reads_or_pairs", units); counts.put("retained_reads_or_pairs", retained); counts.put("rejected_reads_or_pairs", units - retained);
        counts.put("orphan_read1", rescued1); counts.put("orphan_read2", rescued2); counts.put("input_bases_both_mates", inputBases); counts.put("output_bases_all_streams", outputBases);
        counts.put("ambiguous_phase_source_spans", ambiguous); counts.put("guarded_fixed_cuts", guarded); counts.put("oversized_rescued_mates", oversized);
        for (var item : decisions.entrySet()) counts.put("decision_" + item.getKey(), item.getValue());
        var warnings = new ArrayList<String>();
        if (retained == 0) warnings.add("No validated paired/single reads remain. Review source parameters, read-status.tsv and separate orphan streams before downstream analysis.");
        if (guarded > 0) warnings.add(guarded + " fixed cuts were guarded because current length <= requested cut; those cuts were not applied by the original program.");
        if (ambiguous > 0) warnings.add(ambiguous + " Cutadapt source spans are ambiguous because identical sequence/quality spans repeat (or an empty read remains). Emitted content is fully verified; exact source start is NA rather than an inferred trim coordinate.");
        if (oversized > 0) warnings.add(oversized + " rescued mates exceed maximum length because original short-pair rescue precedes that filter; review/filter the separate orphan streams explicitly.");
        if (trim && !paired && filters.fractionalN()) warnings.add("Original0.6.10 ordinary single-end fractional-N filtering also compares uppercase-N count with that fraction; every read containing any uppercase N is rejected. Choose an explicit integer cutoff if that behavior is unsuitable.");
        if (rescue && (rescueOptions.resolvedRead1() < minimum || rescueOptions.resolvedRead2() < minimum)) warnings.add("Original default orphan cutoff35 is below the paired cutoff; both short rejected mates can enter separate orphan streams. Review the streams or use explicitly valid thresholds.");
        String originalLog = Files.readString(original.resolve("original.stderr"));
        if (originalLog.contains("Unable to auto-detect") || originalLog.contains("inconclusive auto-detection")) warnings.add("Original adapter auto-detection was inconclusive; inspect the retained report and actual chosen adapter. Supply an explicit assay adapter when appropriate.");
        var diagnostics = new LinkedHashSet<String>();
        var matches = Pattern.compile("(?im)^\\h*WARN(?:ING)?\\b[^\\r\\n]*(?:\\R\\h+[^\\r\\n]+)*").matcher(originalLog);
        while (matches.find()) diagnostics.add(matches.group().strip());
        session.put("original_warning_blocks", diagnostics);
        if (!diagnostics.isEmpty()) warnings.add("Original Trim Galore/Cutadapt warning diagnostics from original/original.stderr:\n\n"
                + String.join("\n\n", diagnostics) + "\n\nRequired action: inspect these original diagnostics and the preserved trimming report with the actual assay/adapter context before downstream analysis. Select a corrected adapter or other settings explicitly in a new run when needed; original warnings do not automatically discard reads or change the trimming algorithm.");
        var outputs = new ArrayList<Path>(); var labels = new ArrayList<String>(); outputs.add(output1); labels.add("trimmed_R1");
        if (paired) { outputs.add(output2); labels.add("trimmed_R2"); }
        if (rescue) { outputs.add(original.resolve("reads1_unpaired_1.fq")); labels.add("unpaired_R1"); outputs.add(original.resolve("reads2_unpaired_2.fq")); labels.add("unpaired_R2"); }
        return new Result(counts, warnings, outputs, labels);
    }

    private static int integer(Map<String, Object> values, String key, int fallback) { return values.get(key) instanceof Number n ? n.intValue() : fallback; }
    private static Integer optional(Map<String, Object> values, String key) { return values.get(key) instanceof Number n ? n.intValue() : null; }
}
