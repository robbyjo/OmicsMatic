/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.sequence.FlashFastqIO;
import org.jlinalg.sequence.FlashReads;
import static org.jlinalg.cli.FollowupSupport.*;

/** Audited streaming native FLASH read merging; no external executable required. */
final class FlashReadsCli {
    private FlashReadsCli() { }
    private static final Set<String> ALLOWED = Set.of("reads", "reads2", "interleaved", "out", "phred-offset", "min-overlap",
            "max-overlap", "max-mismatch-density", "allow-outies", "cap-mismatch-quals", "pair-policy", "gzip", "options-file",
            "read-length", "fragment-length", "fragment-length-sd");
    private static final List<String> PRODUCTS = List.of("merged.fastq", "merged.fastq.gz", "unmerged_R1.fastq", "unmerged_R1.fastq.gz",
            "unmerged_R2.fastq", "unmerged_R2.fastq.gz", "read-status.tsv", "length-histogram.tsv", "results.tsv");
    private static final String RECOVERY = "Required action: correct the named cause, verify synchronized unwrapped FASTQ and the declared Phred encoding, and rerun to a new output directory. Native FLASH needs no installation. For Unicode data/output paths on Windows launchers that replace non-ANSI arguments, use an ASCII-named UTF-8 JSON --options-file. Failed partial streams are retained under partial/ and must not be used as successful results.";

    static int run(String[] arguments, PrintStream out, PrintStream err) {
        if (arguments.length == 1 && arguments[0].equals("--help")) { out.println(help()); return 0; }
        try {
            Parsed parsed = parse(arguments); var options = parsed.values(); required(options, "out");
            int[] code = {0}; boolean[] warned = {false};
            externalTransaction(options, "flash-native-reads", (directory, manifest) -> {
                var session = new LinkedHashMap<String, Object>(); var warnings = new ArrayList<String>();
                session.put("engine", "native-java"); session.put("original_convention", "FLASH 1.2.11");
                session.put("scope", "Ungapped innie/optional outie scoring, binary32 ties, N exclusion, quality consensus, original sequence cleanup and first-header rewriting. Four-line plain/gzip paired or interleaved FASTQ; UTF-8 headers, ASCII bases/qualities. Strict pair identifiers by default; original positional pairing is explicit. No wrapped/tab input, external compressor, threading or original text-report parity. Worst-case quadratic overlap search; exact mismatch pruning and first-perfect stopping.");
                try {
                    Path first = source(options, "reads"); boolean interleaved = bool(options, "interleaved", false);
                    Path second = options.containsKey("reads2") ? source(options, "reads2") : null;
                    if (interleaved == (second != null)) throw new IllegalArgumentException("Supply either --reads2 or --interleaved true");
                    if (second != null && Files.isSameFile(first, second)) throw new IllegalArgumentException("Mates must be different input files");
                    required(options, "phred-offset"); int phred = integer(options, "phred-offset", 33, 33, 64);
                    String pairPolicy = options.getOrDefault("pair-policy", "strict");
                    if (!Set.of("strict", "positional").contains(pairPolicy)) throw new IllegalArgumentException("--pair-policy must be strict or positional");
                    int minimum = integer(options, "min-overlap", 10, 1, FlashReads.MAXIMUM_READ_LENGTH);
                    var derived = List.of("read-length", "fragment-length", "fragment-length-sd");
                    long specified = derived.stream().filter(options::containsKey).count();
                    int readLength = integer(options, "read-length", 100, 1, FlashReads.MAXIMUM_READ_LENGTH);
                    int fragment = integer(options, "fragment-length", 180, 1, 2 * FlashReads.MAXIMUM_READ_LENGTH);
                    int sd = integer(options, "fragment-length-sd", 18, 1, FlashReads.MAXIMUM_READ_LENGTH);
                    int maximum = options.containsKey("max-overlap") ? integer(options, "max-overlap", 65, 1, Integer.MAX_VALUE)
                            : (int) (2.0 * readLength - fragment + 2.5 * sd);
                    if (specified > 0 && options.containsKey("max-overlap")) warnings.add("Explicit --max-overlap overrides all fragment/read length settings. Required action: confirm the resolved scoring cap in session.yaml; the other length settings have no effect.");
                    else if (specified > 0 && specified < 3) warnings.add("Only some fragment/read length settings were supplied; original defaults fill the remainder. Required action: confirm all resolved values in session.yaml or supply --max-overlap explicitly.");
                    var settings = new FlashReads.Settings(minimum, maximum, (float) number(options, "max-mismatch-density", 0.25), phred,
                            bool(options, "allow-outies", false), bool(options, "cap-mismatch-quals", false));
                    session.put("minimum_callable_overlap", minimum); session.put("maximum_scoring_overlap", maximum);
                    session.put("maximum_mismatch_density_binary32", settings.maximumMismatchDensity()); session.put("phred_offset", phred);
                    session.put("allow_outies", settings.allowOuties()); session.put("cap_mismatch_qualities", settings.capMismatchQualities());
                    session.put("pair_policy", pairPolicy); session.put("interleaved", interleaved);
                    session.put("resolved_read_length", readLength); session.put("resolved_fragment_length", fragment); session.put("resolved_fragment_length_sd", sd);
                    var sources = new LinkedHashMap<String, String>(); sources.put(first.toString(), hash(first));
                    if (second != null) sources.put(second.toString(), hash(second));
                    if (parsed.source() != null) {
                        if (!hash(parsed.source()).equals(parsed.sha256())) throw new IOException("Options file changed before processing");
                        sources.put(parsed.source().toString(), parsed.sha256());
                        Files.copy(parsed.source(), directory.resolve("options-input.json"));
                    }
                    manifest.put("input_sha256", sources);
                    String extension = bool(options, "gzip", true) ? ".fastq.gz" : ".fastq";
                    long pairs = 0, merged = 0, outies = 0, inputBases = 0, outputBases = 0, changedHeaders = 0, pairMismatches = 0;
                    long caseChanges = 0, unknownChanges = 0, headerTrim = 0, sequenceTrim = 0, qualityTrim = 0, ignoredPlus = 0;
                    var histograms = new TreeMap<Integer, long[]>();
                    try (var r1 = new FlashFastqIO.Reader(first, phred); var r2 = second == null ? null : new FlashFastqIO.Reader(second, phred);
                         var mergedOutput = new FlashFastqIO.Writer(directory.resolve("merged" + extension), phred);
                         var u1 = new FlashFastqIO.Writer(directory.resolve("unmerged_R1" + extension), phred);
                         var u2 = new FlashFastqIO.Writer(directory.resolve("unmerged_R2" + extension), phred);
                         var ledger = Files.newBufferedWriter(directory.resolve("read-status.tsv"))) {
                        ledger.write("ordinal\tread1_original_header_json\tread2_original_header_json\tmerged_header_json\tdecision\tread1_length\tread2_length\toverlap_begin\tphysical_overlap\tcallable_overlap\tmismatches\tmismatch_density_binary32\tmismatch_quality_score_binary32\tcase_changes\tunknown_changes\theader_whitespace_trimmed\tsequence_whitespace_trimmed\tquality_whitespace_trimmed\tignored_plus_identity_count\tpair_identity_mismatch\n");
                        FlashFastqIO.Read a;
                        while ((a = r1.next()) != null) {
                            pairs = Math.addExact(pairs, 1); FlashFastqIO.Read b = interleaved ? r1.next() : r2.next();
                            if (b == null) throw new IllegalArgumentException("Missing second mate at pair " + pairs);
                            boolean mismatch = !a.pairId().equals(b.pairId());
                            if (mismatch && pairPolicy.equals("strict")) throw new IllegalArgumentException("FASTQ pair identity/order mismatch at pair " + pairs + "; confirm inputs, or explicitly select positional pairing only after verifying the order");
                            if (mismatch) pairMismatches++;
                            long pairCase = (long) a.caseChanges() + b.caseChanges(), pairUnknown = (long) a.unknownChanges() + b.unknownChanges();
                            long pairHeaderTrim = (long) a.trimmedHeader() + b.trimmedHeader(), pairSequenceTrim = (long) a.trimmedSequence() + b.trimmedSequence();
                            long pairQualityTrim = (long) a.trimmedQuality() + b.trimmedQuality();
                            int pairPlus = (a.ignoredPlusIdentity() ? 1 : 0) + (b.ignoredPlusIdentity() ? 1 : 0);
                            caseChanges = Math.addExact(caseChanges, pairCase); unknownChanges = Math.addExact(unknownChanges, pairUnknown);
                            headerTrim = Math.addExact(headerTrim, pairHeaderTrim); sequenceTrim = Math.addExact(sequenceTrim, pairSequenceTrim); qualityTrim = Math.addExact(qualityTrim, pairQualityTrim); ignoredPlus = Math.addExact(ignoredPlus, pairPlus);
                            inputBases = Math.addExact(inputBases, (long) a.sequence().length() + b.sequence().length());
                            var result = FlashReads.merge(a.sequence(), a.quality(), b.sequence(), b.quality(), settings);
                            String mergedHeader = "NA", decision = "UNMERGED", scores = "NA\tNA\tNA\tNA\tNA\tNA";
                            if (result.isPresent()) {
                                var value = result.get(); String header = FlashFastqIO.mergedHeader(a.header());
                                mergedOutput.write(header, value.sequence(), value.quality()); merged++;
                                if (!header.equals(a.header())) changedHeaders++;
                                boolean outie = value.orientation() == FlashReads.Orientation.OUTIE;
                                if (outie) outies++;
                                histograms.computeIfAbsent(value.sequence().length(), key -> new long[2])[outie ? 1 : 0]++;
                                outputBases = Math.addExact(outputBases, value.sequence().length());
                                mergedHeader = RegenieRuntime.json(header); decision = value.orientation().name();
                                scores = value.overlapBegin() + "\t" + value.physicalOverlap() + "\t" + value.callableOverlap() + "\t" + value.mismatches() + "\t" + value.mismatchDensity() + "\t" + value.mismatchQualityScore();
                            } else {
                                u1.write(a.header(), a.sequence(), a.quality()); u2.write(b.header(), b.sequence(), b.quality());
                                outputBases = Math.addExact(outputBases, (long) a.sequence().length() + b.sequence().length());
                            }
                            ledger.write(pairs + "\t" + RegenieRuntime.json(a.originalHeader()) + "\t" + RegenieRuntime.json(b.originalHeader()) + "\t" + mergedHeader + "\t" + decision
                                    + "\t" + a.sequence().length() + "\t" + b.sequence().length() + "\t" + scores + "\t" + pairCase + "\t" + pairUnknown + "\t" + pairHeaderTrim + "\t" + pairSequenceTrim + "\t" + pairQualityTrim + "\t" + pairPlus + "\t" + mismatch + "\n");
                        }
                        if (r2 != null && r2.next() != null) throw new IllegalArgumentException("Second FASTQ mate has extra records");
                    }
                    if (pairs == 0) throw new IllegalArgumentException("Input FASTQ has no read pairs");
                    for (var source : sources.entrySet()) if (!hash(Path.of(source.getKey())).equals(source.getValue())) throw new IOException("Input changed during processing: " + source.getKey());
                    if (caseChanges + unknownChanges > 0) warnings.add("FLASH normalized " + caseChanges + " lowercase A/C/G/T bases and converted " + unknownChanges + " other ASCII characters to N. Required action: inspect per-pair counts in read-status.tsv, confirm this cleanup is appropriate, and retain original inputs for provenance.");
                    if (headerTrim + sequenceTrim + qualityTrim > 0) warnings.add("Original FLASH trailing ASCII whitespace cleanup affected headers, sequences or qualities. Required action: review the separate trim counts in results.tsv/read-status.tsv and verify source lengths before downstream analysis.");
                    if (changedHeaders > 0) warnings.add("Original FLASH changed " + changedHeaders + " merged first-read headers at their last slash, including possible comments, while retaining /X# barcodes. Required action: use the JSON-escaped original and merged header mapping in read-status.tsv for downstream joins; do not assume comments or mate suffixes survive.");
                    if (ignoredPlus > 0) warnings.add("Original FLASH ignored " + ignoredPlus + " differing repeated plus identifiers. Required action: verify read identity against the original headers and ledger before downstream analysis.");
                    if (pairPolicy.equals("positional")) warnings.add("Positional pairing was explicitly selected, as in original FLASH; " + pairMismatches + " pairs had different normalized identifiers. Required action: independently verify input mate order and counts before using results; prefer strict pairing when identities are available.");
                    if (merged == 0) warnings.add("No read pairs merged. Required action: review input orientation, declared encoding, minimum callable overlap and scoring cap/density; inspect both unmerged streams before downstream analysis.");
                    var counts = new LinkedHashMap<String, Long>();
                    counts.put("input_pairs", pairs); counts.put("merged_pairs", merged); counts.put("innie_merged", merged - outies); counts.put("outie_merged", outies);
                    counts.put("unmerged_pairs", pairs - merged); counts.put("input_bases_cleaned_both_mates", inputBases); counts.put("output_bases_all_streams", outputBases);
                    counts.put("case_changes", caseChanges); counts.put("unknown_changes", unknownChanges); counts.put("header_whitespace_trimmed", headerTrim);
                    counts.put("sequence_whitespace_trimmed", sequenceTrim); counts.put("quality_whitespace_trimmed", qualityTrim); counts.put("merged_header_changes", changedHeaders);
                    counts.put("ignored_plus_identity_count", ignoredPlus); counts.put("pair_identity_mismatches", pairMismatches);
                    var rows = new ArrayList<String[]>(); counts.forEach((key, value) -> rows.add(new String[]{key, value.toString()}));
                    table(directory.resolve("results.tsv"), List.of("metric", "value"), rows); session.put("counts", counts);
                    var lengths = new ArrayList<String[]>(); histograms.forEach((length, n) -> lengths.add(new String[]{length.toString(), Long.toString(n[0] + n[1]), Long.toString(n[0]), Long.toString(n[1])}));
                    table(directory.resolve("length-histogram.tsv"), List.of("length", "merged_pairs", "innie_pairs", "outie_pairs"), lengths);
                    warned[0] = !warnings.isEmpty(); String state = warned[0] ? "completed-with-qc-warnings" : "completed";
                    manifest.put("analysis_status", state); status(directory, state);
                    if (warned[0]) Files.writeString(directory.resolve("WARNINGS.md"), "# FLASH READ MERGING WARNINGS\n\n" + String.join("\n\n", warnings) + "\n");
                } catch (Exception failure) {
                    code[0] = 2; manifest.put("analysis_status", "failed"); manifest.put("error", failure.toString()); session.put("error", failure.toString());
                    for (String name : PRODUCTS) if (Files.exists(directory.resolve(name))) { Files.createDirectories(directory.resolve("partial")); Files.move(directory.resolve(name), directory.resolve("partial").resolve(name)); }
                    status(directory, "failed"); Files.writeString(directory.resolve("WARNINGS.md"), "# FLASH READ MERGING FAILED\n\n" + failure + "\n\n" + RECOVERY + " No successful results.tsv is published.\n");
                } finally { Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session)); }
            });
            if (code[0] != 0) err.println("FLASH READ MERGING FAILED: see " + Path.of(options.get("out")).resolve("WARNINGS.md") + " for the error and required action.");
            else { out.println("Native FLASH read merging completed: " + options.get("out")); if (warned[0]) err.println("QC WARNINGS: see " + Path.of(options.get("out")).resolve("WARNINGS.md") + " before downstream analysis."); }
            return code[0];
        } catch (Exception failure) { err.println("FLASH READ MERGING FAILED: " + failure.getMessage() + ". " + RECOVERY + " Review flash-reads --help."); return 2; }
    }

    private record Parsed(Map<String, String> values, Path source, String sha256) { }
    private static Parsed parse(String[] arguments) throws Exception {
        var direct = options(arguments, ALLOWED);
        if (!direct.containsKey("options-file")) return new Parsed(direct, null, null);
        Path file = Path.of(direct.get("options-file")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || Files.size(file) > 1_048_576) throw new IOException("Require a UTF-8 JSON options object of at most 1 MiB");
        byte[] bytes = Files.readAllBytes(file);
        Object value = SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        if (!(value instanceof Map<?, ?> fields)) throw new IllegalArgumentException("Options file must be a JSON object of known option names and string values");
        var effective = new LinkedHashMap<String, String>();
        for (var entry : fields.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!ALLOWED.contains(key) || key.equals("options-file") || !(entry.getValue() instanceof String text))
                throw new IllegalArgumentException("Unknown, recursive or non-string options-file entry: " + key);
            effective.put(key, text);
        }
        effective.putAll(direct);
        return new Parsed(effective, file, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    private static Path source(Map<String, String> options, String key) throws IOException {
        Path path = Path.of(required(options, key)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException("Input FASTQ file unavailable: " + path);
        return path;
    }
    private static void status(Path directory, String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + state + "\"}\n"); }
    static String help() { return """
        flash-reads --reads R1.fq[.gz] --reads2 R2.fq[.gz] --phred-offset 33|64 --out NEW_DIR
          [--min-overlap 10] [--max-overlap 65] [--max-mismatch-density 0.25]
          [--allow-outies false] [--cap-mismatch-quals false] [--pair-policy strict|positional]
          [--gzip true] [--options-file UTF8_OPTIONS.json]
        Interleaved input: replace --reads2 with --interleaved true.
        Native Java, original FLASH 1.2.11 scoring/consensus; no installation needed.
        Phred encoding is required, never guessed. Minimum overlap counts non-N bases;
        maximum overlap CAPS THE SCORE DENOMINATOR, not physical overlap eligibility.
        Optional --read-length 100 --fragment-length 180 --fragment-length-sd 18 derive
        max overlap by truncating 2*read-length-fragment-length+2.5*sd; explicit max wins.
        Original cleanup uppercases A/C/G/T and converts all other ASCII bases to N;
        trailing ASCII whitespace is stripped. Original merged tags remove the last
        slash suffix in the full first header, retaining /X# barcodes. Every original
        header and change count is retained in read-status.tsv; review QC WARNINGS.
        Four-line plain/gzip FASTQ, UTF-8 headers, printable ASCII qualities; no wrapped
        or tab-delimited input. At most 10 million bases per mate, worst-case quadratic
        search. Strict identifiers by default; original positional pairing is explicit.
        Merged and both unmerged streams, scores, length/orientation counts and hashes
        are published together. Exit 0 on success (warnings may require review), 2 on
        invalid input/failure with actionable WARNINGS.md and quarantined partial data.
        For Windows Unicode paths use an ASCII-named JSON recipe of option:string values;
        command-line values override the recipe. No automatic trimming or downstream mapping.
        """; }
}
