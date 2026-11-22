/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.FixedReadTrimmer;
import static org.jlinalg.cli.FollowupSupport.*;

/** Dependency-free post-adapter fixed-base clipping, including synchronized paired filtering. */
final class FixedTrimCli {
    private FixedTrimCli() { }
    static int run(Map<String, String> o, PrintStream output, PrintStream error) throws Exception {
        if (o.containsKey("timeout")) throw new IllegalArgumentException("--timeout applies to upstream processes, not native fixed trimming");
        final int[] exit = {0}; final boolean[] empty = {false};
        transaction(o, "sequence-qc-trim-fixed", (directory, manifest) -> {
            try {
                Path first = Path.of(required(o, "reads")).toAbsolutePath().normalize();
                Path second = o.containsKey("reads2") ? Path.of(o.get("reads2")).toAbsolutePath().normalize() : null;
                if (second != null && Files.isSameFile(first, second)) throw new IllegalArgumentException("FASTQ mates must be different files");
                var settings = new FixedReadTrimmer.Options(integer(o, "first-base", 1, 1, 10_000_000),
                    integer(o, "last-base", 0, 0, 10_000_000), integer(o, "minimum-length", 1, 1, 10_000_000));
                Map<String, String> hashes = new LinkedHashMap<>(); hashes.put("reads", hash(first));
                if (second != null) hashes.put("reads2", hash(second)); manifest.put("input_sha256", hashes);
                long input = 0, retained = 0, inputBases = 0, outputBases = 0;
                try (var r1 = new FastqIO.Reader(first); var r2 = second == null ? null : new FastqIO.Reader(second);
                     var w1 = new FastqIO.Writer(directory.resolve("trimmed_R1.fastq.gz"));
                     var w2 = second == null ? null : new FastqIO.Writer(directory.resolve("trimmed_R2.fastq.gz"))) {
                    FastqIO.Read a;
                    while ((a = r1.next()) != null) {
                        input++; inputBases = Math.addExact(inputBases, a.sequence().length());
                        FastqIO.Read b = r2 == null ? null : r2.next();
                        if (r2 != null && (b == null || !a.pairId().equals(b.pairId()))) throw new IllegalArgumentException("FASTQ pair identity/order/count mismatch at record " + input);
                        if (b != null) inputBases = Math.addExact(inputBases, b.sequence().length());
                        FastqIO.Read clippedA = FixedReadTrimmer.clip(a, settings);
                        FastqIO.Read clippedB = b == null ? null : FixedReadTrimmer.clip(b, settings);
                        if (clippedA == null || r2 != null && clippedB == null) continue;
                        w1.write(clippedA); outputBases = Math.addExact(outputBases, clippedA.sequence().length());
                        if (w2 != null) { w2.write(clippedB); outputBases = Math.addExact(outputBases, clippedB.sequence().length()); }
                        retained++;
                    }
                    if (r2 != null && r2.next() != null) throw new IllegalArgumentException("FASTQ mate has extra records");
                }
                if (input == 0) throw new IllegalArgumentException("Input FASTQ has no reads");
                table(directory.resolve("results.tsv"), List.of("metric", "value"), List.of(
                    new String[]{"input_reads_or_pairs", Long.toString(input)}, new String[]{"output_reads_or_pairs", Long.toString(retained)},
                    new String[]{"discarded_reads_or_pairs", Long.toString(input - retained)},
                    new String[]{"input_bases_both_mates", Long.toString(inputBases)}, new String[]{"output_bases_both_mates", Long.toString(outputBases)}));
                Map<String, Object> session = new LinkedHashMap<>();
                session.put("implementation", "JLinAlg native fixed-span clipping"); session.put("first_base_inclusive", settings.firstBase());
                session.put("last_base_inclusive_zero_means_end", settings.lastBase()); session.put("minimum_retained_length", settings.minimumLength());
                session.put("paired", second != null); session.put("paired_filter", "Discard the pair if either retained span is absent or below minimum length");
                session.put("scope", "One-based inclusive fixed span on the supplied FASTQ; independent stage suitable after adapter removal; canonical plus lines, original headers and clipped qualities; no adapter detection or full FASTX-Toolkit parity claim");
                Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session));
                empty[0] = retained == 0;
                manifest.put("analysis_status", empty[0] ? "completed-with-qc-warnings" : "completed");
                status(directory, empty[0] ? "completed-with-qc-warnings" : "completed");
                if (empty[0]) Files.writeString(directory.resolve("WARNINGS.md"), "# NO READS RETAINED\n\nFixed clipping removed every read/pair.\n\nRequired action: inspect the requested original-read span and minimum length before downstream analysis. Review the processing order and input read lengths. The empty outputs and count summary are retained; no downstream analysis has been run.\n");
            } catch (Exception failure) {
                exit[0] = 2; manifest.put("analysis_status", "failed"); manifest.put("error", failure.getMessage());
                Path partial = directory.resolve("partial");
                for (String name : List.of("trimmed_R1.fastq.gz", "trimmed_R2.fastq.gz", "results.tsv")) if (Files.exists(directory.resolve(name))) {
                    Files.createDirectories(partial); Files.move(directory.resolve(name), partial.resolve(name));
                }
                status(directory, "failed");
                Files.writeString(directory.resolve("WARNINGS.md"), "# ANALYSIS FAILED\n\n" + failure.getMessage()
                    + "\n\nRequired action: supply valid unwrapped Phred+33 FASTQ, matching paired identities/order, first-base >=1, last-base 0 or >=first-base, and a positive minimum length. Correct the cause and rerun to a new output directory. Partial outputs are quarantined under partial/; no successful results.tsv is published.\n");
            }
        });
        if (exit[0] == 0) { output.println("Native fixed trimming completed: " + o.get("out")); if (empty[0]) error.println("QC WARNINGS: no reads retained; see " + Path.of(o.get("out")).resolve("WARNINGS.md")); }
        else error.println("ANALYSIS FAILED: see " + Path.of(o.get("out")).resolve("WARNINGS.md") + " for the error and required action.");
        return exit[0];
    }
    private static void status(Path directory, String state) throws Exception { Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + state + "\",\"detail\":\"See session.yaml and WARNINGS.md when present\"}\n"); }
}
