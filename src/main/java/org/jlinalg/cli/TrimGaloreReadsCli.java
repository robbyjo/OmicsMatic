/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.TrimGaloreReads;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native, explicitly bounded Trim Galore read operations; no external runtime. */
final class TrimGaloreReadsCli {
    private TrimGaloreReadsCli() { }
    private static final Set<String> FILTER_OPTIONS = Set.of("release", "input-stage", "clip-r1", "clip-r2", "three-prime-clip-r1",
            "three-prime-clip-r2", "minimum-length", "maximum-length", "maximum-n", "retain-unpaired", "orphan-min1", "orphan-min2");
    private static final List<String> OUTPUTS = List.of("trimmed_R1.fastq.gz", "trimmed_R2.fastq.gz", "unpaired_R1.fastq.gz", "unpaired_R2.fastq.gz",
            "trimmed_R1.fastq", "trimmed_R2.fastq", "unpaired_R1.fastq", "unpaired_R2.fastq", "results.tsv", "read-status.tsv");

    static int run(String[] arguments, PrintStream out, PrintStream err) {
        if (arguments.length == 1 && arguments[0].equals("--help")) { out.println(help()); return 0; }
        try {
            var allowed = new java.util.HashSet<>(FILTER_OPTIONS);
            allowed.addAll(Set.of("mode", "reads", "reads2", "bases", "gzip", "out"));
            var options = options(arguments, allowed);
            required(options, "out");
            int[] code = {0}; boolean[] warned = {false};
            transaction(options, "trim-galore-native-reads", (directory, manifest) -> {
                var session = new LinkedHashMap<String, Object>(); var warnings = new ArrayList<String>();
                session.put("engine", "native-java");
                session.put("scope", "Original ordinary non-RRBS/non-poly-A final read operations or separate hardtrim crop; no adapter detection, Cutadapt alignment, quality trimming, RRBS, poly-A processing or complete original-tool parity. Four-line FASTQ; canonical plus lines; original headers, base case and quality characters preserved.");
                try {
                    String mode = required(options, "mode");
                    if (!Set.of("finalize", "hardtrim5", "hardtrim3").contains(mode)) throw new IllegalArgumentException("--mode must be finalize, hardtrim5 or hardtrim3");
                    boolean finalize = mode.equals("finalize");
                    if (!finalize && options.keySet().stream().anyMatch(FILTER_OPTIONS::contains))
                        throw new IllegalArgumentException("Hardtrim bypasses all final filters; remove release, input-stage, clip, length, N and rescue options");
                    if (finalize && options.containsKey("bases")) throw new IllegalArgumentException("--bases applies only to hardtrim5/hardtrim3");
                    Path first = Path.of(required(options, "reads")).toAbsolutePath().normalize();
                    Path second = options.containsKey("reads2") ? Path.of(options.get("reads2")).toAbsolutePath().normalize() : null;
                    if (second != null && Files.isSameFile(first, second)) throw new IllegalArgumentException("Mates must be different input files");
                    if (second == null && List.of("clip-r2", "three-prime-clip-r2", "retain-unpaired", "orphan-min1", "orphan-min2").stream().anyMatch(options::containsKey))
                        throw new IllegalArgumentException("Mate-2 and rescue options require --reads2");
                    TrimGaloreReads.Filters filters = null;
                    var clips1 = new TrimGaloreReads.Clips(0, 0); var clips2 = clips1;
                    var rescue = new TrimGaloreReads.Rescue(false, null, null);
                    int bases = 0;
                    if (finalize) {
                        if (!required(options, "input-stage").equals("post-cutadapt"))
                            throw new IllegalArgumentException("Finalization requires --input-stage post-cutadapt; first supply the requested adapter/quality-trimmed FASTQ, not raw reads or already-finalized Trim Galore output");
                        String release = required(options, "release");
                        if (!Set.of("0.6.7", "0.6.10").contains(release)) throw new IllegalArgumentException("--release must be 0.6.7 or 0.6.10; no historical-version substitution");
                        filters = new TrimGaloreReads.Filters(release.equals("0.6.7") ? TrimGaloreReads.Release.V0_6_7 : TrimGaloreReads.Release.V0_6_10,
                                integer(options, "minimum-length", 20, 1, 10_000_000), integer(options, "maximum-length", 0, 0, 10_000_000),
                                options.containsKey("maximum-n") ? number(options, "maximum-n", 0) : null);
                        clips1 = new TrimGaloreReads.Clips(integer(options, "clip-r1", 0, 0, 99), integer(options, "three-prime-clip-r1", 0, 0, 99));
                        clips2 = new TrimGaloreReads.Clips(integer(options, "clip-r2", 0, 0, 99), integer(options, "three-prime-clip-r2", 0, 0, 99));
                        rescue = new TrimGaloreReads.Rescue(bool(options, "retain-unpaired", false),
                                options.containsKey("orphan-min1") ? integer(options, "orphan-min1", 35, 15, 100) : null,
                                options.containsKey("orphan-min2") ? integer(options, "orphan-min2", 35, 15, 100) : null);
                        if (second != null && filters.release() == TrimGaloreReads.Release.V0_6_7 && filters.maximumLength() > 0)
                            throw new IllegalArgumentException("Original 0.6.7 rejects paired maximum-length filtering");
                        if (rescue.minimumRead1() != null && rescue.minimumRead1() <= filters.minimumLength()
                                || rescue.minimumRead2() != null && rescue.minimumRead2() <= filters.minimumLength())
                            throw new IllegalArgumentException("Explicit orphan thresholds must exceed the paired minimum");
                        if (second == null && filters.fractionalN()) warnings.add("Original 0.6.10 ordinary single-end fractional N filtering also compares the uppercase-N count to the fraction; this rejects every read with any uppercase N. Required action: use an explicit integer N cutoff if that behavior is unsuitable; the native command preserves the declared release behavior.");
                        if (rescue.enabled() && (rescue.resolvedRead1() < filters.minimumLength() || rescue.resolvedRead2() < filters.minimumLength()))
                            warnings.add("Original default orphan length is 35, below the declared paired minimum. Both rejected mates can enter separate orphan streams. Required action: review these streams and select explicit valid thresholds or disable rescue before downstream analysis.");
                        session.put("release", release); session.put("input_stage", "post-cutadapt");
                        session.put("minimum_length", filters.minimumLength()); session.put("maximum_length", filters.maximumLength());
                        session.put("maximum_n", filters.maximumN()); session.put("uppercase_n_only", true);
                        session.put("clip_r1_5prime", clips1.fivePrime()); session.put("clip_r1_3prime", clips1.threePrime());
                        session.put("clip_r2_5prime", clips2.fivePrime()); session.put("clip_r2_3prime", clips2.threePrime());
                        session.put("retain_unpaired", rescue.enabled());
                        if (rescue.enabled()) { session.put("resolved_orphan_min1", rescue.resolvedRead1()); session.put("resolved_orphan_min2", rescue.resolvedRead2()); }
                    } else {
                        required(options, "bases"); bases = integer(options, "bases", 0, 1, 999); session.put("retained_bases", bases);
                    }
                    session.put("mode", mode); session.put("paired", second != null);
                    var sources = new LinkedHashMap<String, String>(); sources.put(first.toString(), hash(first));
                    if (second != null) sources.put(second.toString(), hash(second)); manifest.put("input_sha256", sources);
                    boolean gzip = bool(options, "gzip", true); String extension = gzip ? ".fastq.gz" : ".fastq";
                    long units = 0, retained = 0, orphan1 = 0, orphan2 = 0, inputBases = 0, outputBases = 0, guardedCuts = 0, oversizedRescues = 0, shortHardtrim = 0;
                    var decisions = new EnumMap<TrimGaloreReads.Decision, Long>(TrimGaloreReads.Decision.class);
                    try (var r1 = new FastqIO.Reader(first); var r2 = second == null ? null : new FastqIO.Reader(second);
                         var w1 = new FastqIO.Writer(directory.resolve("trimmed_R1" + extension));
                         var w2 = second == null ? null : new FastqIO.Writer(directory.resolve("trimmed_R2" + extension));
                         var u1 = !rescue.enabled() ? null : new FastqIO.Writer(directory.resolve("unpaired_R1" + extension));
                         var u2 = !rescue.enabled() ? null : new FastqIO.Writer(directory.resolve("unpaired_R2" + extension));
                         var ledger = Files.newBufferedWriter(directory.resolve("read-status.tsv"))) {
                        ledger.write("ordinal\tread1_header_json\tread2_header_json\tdecision\tpaired_or_single_retained\trescue_read1\trescue_read2\tread1_length\tread2_length\n");
                        FastqIO.Read a;
                        while ((a = r1.next()) != null) {
                            units = Math.addExact(units, 1); FastqIO.Read b = r2 == null ? null : r2.next();
                            if (r2 != null && (b == null || !a.pairId().equals(b.pairId()))) throw new IllegalArgumentException("FASTQ pair identity/order/count mismatch at record " + units);
                            inputBases = Math.addExact(inputBases, a.sequence().length() + (b == null ? 0L : b.sequence().length()));
                            FastqIO.Read processed1, processed2 = null; boolean keep, rescue1 = false, rescue2 = false;
                            TrimGaloreReads.Decision decision;
                            if (!finalize) {
                                var end = mode.equals("hardtrim5") ? TrimGaloreReads.End.FIVE_PRIME : TrimGaloreReads.End.THREE_PRIME;
                                processed1 = TrimGaloreReads.hardtrim(a, bases, end);
                                if (b != null) processed2 = TrimGaloreReads.hardtrim(b, bases, end);
                                shortHardtrim += (a.sequence().length() < bases ? 1 : 0) + (b != null && b.sequence().length() < bases ? 1 : 0);
                                decision = TrimGaloreReads.Decision.RETAINED; keep = true;
                            } else if (b == null) {
                                var result = TrimGaloreReads.single(a, clips1, filters); processed1 = result.clipped(); keep = result.retained(); decision = result.decision();
                                guardedCuts += (result.clipping().skippedFivePrime() ? 1 : 0) + (result.clipping().skippedThreePrime() ? 1 : 0);
                            } else {
                                var result = TrimGaloreReads.paired(a, b, clips1, clips2, filters, rescue);
                                processed1 = result.read1().read(); processed2 = result.read2().read(); keep = result.retained(); decision = result.decision();
                                rescue1 = result.rescueRead1(); rescue2 = result.rescueRead2();
                                for (var clipped : List.of(result.read1(), result.read2())) guardedCuts += (clipped.skippedFivePrime() ? 1 : 0) + (clipped.skippedThreePrime() ? 1 : 0);
                            }
                            decisions.merge(decision, 1L, Math::addExact);
                            if (keep) { w1.write(processed1); outputBases = Math.addExact(outputBases, processed1.sequence().length()); retained++; }
                            if (keep && w2 != null) { w2.write(processed2); outputBases = Math.addExact(outputBases, processed2.sequence().length()); }
                            if (rescue1) { u1.write(processed1); orphan1++; outputBases = Math.addExact(outputBases, processed1.sequence().length()); }
                            if (rescue2) { u2.write(processed2); orphan2++; outputBases = Math.addExact(outputBases, processed2.sequence().length()); }
                            if (finalize && filters.maximumLength() > 0) oversizedRescues += (rescue1 && processed1.sequence().length() > filters.maximumLength() ? 1 : 0)
                                    + (rescue2 && processed2.sequence().length() > filters.maximumLength() ? 1 : 0);
                            ledger.write(units + "\t" + RegenieRuntime.json(a.header()) + "\t" + (b == null ? "NA" : RegenieRuntime.json(b.header())) + "\t" + decision
                                    + "\t" + keep + "\t" + rescue1 + "\t" + rescue2 + "\t" + processed1.sequence().length() + "\t" + (processed2 == null ? "NA" : processed2.sequence().length()) + "\n");
                        }
                        if (r2 != null && r2.next() != null) throw new IllegalArgumentException("FASTQ mate has extra records");
                    }
                    if (units == 0) throw new IllegalArgumentException("Input FASTQ has no reads");
                    for (var source : sources.entrySet()) if (!hash(Path.of(source.getKey())).equals(source.getValue())) throw new IllegalArgumentException("Input changed during read processing: " + source.getKey());
                    if (guardedCuts > 0) warnings.add(guardedCuts + " requested fixed cuts were skipped because the current read was no longer than the cut. Required action: review read-status.tsv and original input lengths before interpreting fixed-position removal; this is the original length guard.");
                    if (oversizedRescues > 0) warnings.add(oversizedRescues + " rescued mates exceed maximum-length because original rescue precedes the maximum check. Required action: review/filter orphan streams explicitly before downstream analysis.");
                    if (shortHardtrim > 0) warnings.add(shortHardtrim + " input reads were shorter than --bases and were retained at their original length, including empty reads. Required action: apply an explicit separate minimum-length stage if required downstream.");
                    if (retained == 0) warnings.add("No validated paired/single reads were retained. Required action: review declared stage, clipping, N and length settings and any separate orphan streams before downstream analysis; no downstream analysis was run.");
                    var counts = new LinkedHashMap<String, Long>(); counts.put("input_reads_or_pairs", units); counts.put("retained_reads_or_pairs", retained);
                    counts.put("rejected_reads_or_pairs", units - retained); counts.put("orphan_read1", orphan1); counts.put("orphan_read2", orphan2);
                    counts.put("input_bases_both_mates", inputBases); counts.put("output_bases_all_streams", outputBases); counts.put("guarded_fixed_cuts", guardedCuts);
                    counts.put("oversized_rescued_mates", oversizedRescues); counts.put("short_hardtrim_reads", shortHardtrim);
                    for (var decision : decisions.entrySet()) counts.put("decision_" + decision.getKey(), decision.getValue());
                    var rows = new ArrayList<String[]>(); for (var entry : counts.entrySet()) rows.add(new String[]{entry.getKey(), entry.getValue().toString()});
                    table(directory.resolve("results.tsv"), List.of("metric", "value"), rows); session.put("counts", counts);
                    warned[0] = !warnings.isEmpty(); String state = warned[0] ? "completed-with-qc-warnings" : "completed";
                    manifest.put("analysis_status", state); status(directory, state);
                    if (warned[0]) Files.writeString(directory.resolve("WARNINGS.md"), "# READ PROCESSING WARNINGS\n\n" + String.join("\n\n", warnings) + "\n");
                } catch (Exception failure) {
                    code[0] = 2; manifest.put("analysis_status", "failed"); manifest.put("error", failure.toString());
                    for (String name : OUTPUTS) if (Files.exists(directory.resolve(name))) { Files.createDirectories(directory.resolve("partial")); Files.move(directory.resolve(name), directory.resolve("partial").resolve(name)); }
                    status(directory, "failed");
                    Files.writeString(directory.resolve("WARNINGS.md"), "# READ PROCESSING FAILED\n\n" + failure + "\n\nRequired action: supply valid synchronized unwrapped FASTQ and correct the reported mode, release, stage or parameter error. Finalization requires actual post-Cutadapt input; hardtrim is a separate crop. Rerun to a new output directory after correcting the cause. Partial streams are retained under partial/; no successful results.tsv is published.\n");
                } finally { Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session)); }
            });
            if (code[0] != 0) err.println("READ PROCESSING FAILED: see " + Path.of(options.get("out")).resolve("WARNINGS.md") + " for the error and required action.");
            else { out.println("Native Trim Galore read operations completed: " + options.get("out")); if (warned[0]) err.println("QC WARNINGS: see " + Path.of(options.get("out")).resolve("WARNINGS.md") + " before downstream analysis."); }
            return code[0];
        } catch (Exception failure) {
            err.println("READ PROCESSING FAILED: " + failure.getMessage() + ". Required action: review trim-galore-reads --help, correct the options and choose a new output directory."); return 2;
        }
    }

    private static void status(Path directory, String value) throws Exception { Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + value + "\"}\n"); }
    static String help() { return """
        trim-galore-reads --mode finalize --release 0.6.7|0.6.10 --input-stage post-cutadapt
          --reads R1.fq[.gz] [--reads2 R2.fq[.gz]] --out NEW_DIR
          [--clip-r1 0..99] [--clip-r2 0..99] [--three-prime-clip-r1 0..99]
          [--three-prime-clip-r2 0..99] [--minimum-length 20] [--maximum-length 0]
          [--maximum-n COUNT_OR_FRACTION] [--retain-unpaired true|false]
          [--orphan-min1 INT] [--orphan-min2 INT] [--gzip true|false]
        trim-galore-reads --mode hardtrim5|hardtrim3 --bases 1..999 --reads R1.fq[.gz]
          [--reads2 R2.fq[.gz]] --out NEW_DIR [--gzip true|false]
        Native Java only. Finalize requires adapter/quality-trimmed input from the
        Cutadapt phase. It does not perform adapter alignment or quality trimming.
        Ordinary non-RRBS/non-poly-A subset only. No complete-tool parity claim.
        Fixed cuts run 5' then 3'; each cut is skipped when current length <= cut.
        Uppercase N only. Integer limit 0..100; fractional limit (0,1) only in 0.6.10.
        Original0.6.10 ordinary SE also checks N count against a fractional limit.
        Paired order: N filtering, short-pair rescue, maximum length, pair retention.
        Original0.6.7 rejects paired maximum length. Default orphan cutoffs35 can
        fall below paired minimum; explicit cutoffs15..100 must exceed that minimum.
        Rescue bypasses maximum-length checking in the original short-pair branch.
        Hardtrim5 keeps FIRST N; hardtrim3 keeps LAST N, preserving shorter reads.
        Hardtrim accepts no finalization/filter options and performs no min filter.
        Results, per-record decisions, source hashes and warnings are retained.
        Malformed/mismatched input fails with exit2 and quarantined partial streams.
        """; }
}
