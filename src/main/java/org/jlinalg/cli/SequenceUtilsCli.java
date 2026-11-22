/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.IndexedSequences;
import org.jlinalg.sequence.SeqtkMethods;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native read-depth/length simulations, indexed extraction and terminal-repeat scoring. */
final class SequenceUtilsCli {
    private SequenceUtilsCli() { }
    private static final Set<String> COMMON = Set.of("method", "out");
    private static final Set<String> READS = Set.of("reads", "reads2");
    private static final Set<String> SAMPLE = Set.of("fraction", "count", "seed");
    private static final Set<String> TRIM = Set.of("error-rate", "minimum-retained-length", "trim-left", "trim-right", "length");
    private static final Set<String> TELO = Set.of("motif", "penalty", "maximum-drop", "minimum-score");

    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (Arrays.asList(arguments).contains("--help")) { output.println(help()); return 0; }
        try {
            var all = new HashSet<>(COMMON);
            for (var set : List.of(READS, SAMPLE, TRIM, TELO, Set.of("reference", "regions"))) all.addAll(set);
            Map<String, String> o = options(arguments, all); String method = required(o, "method");
            var applicable = new HashSet<>(COMMON);
            switch (method) {
                case "sample" -> { applicable.addAll(READS); applicable.addAll(SAMPLE); }
                case "trimfq" -> { applicable.addAll(READS); applicable.addAll(TRIM); }
                case "subseq" -> applicable.addAll(Set.of("reference", "regions"));
                case "telo" -> { applicable.add("reference"); applicable.addAll(TELO); }
                default -> throw new IllegalArgumentException("--method must be sample, trimfq, subseq or telo");
            }
            for (String key : o.keySet()) if (!applicable.contains(key)) throw new IllegalArgumentException("--" + key + " does not apply to " + method);
            int[] exit = {0}; boolean[] warnings = {false};
            externalTransaction(o, "native-sequence-" + method, (directory, manifest) -> {
                var session = new LinkedHashMap<String, Object>(); var findings = new ArrayList<String>();
                session.put("method", method); session.put("engine", "Native Java; seqtk algorithm subset / HTSJDK 5.0.0");
                session.put("parameters", o); session.put("installation_required", false);
                try {
                    if (method.equals("sample") || method.equals("trimfq")) reads(o, method, directory, manifest, session, findings);
                    else reference(o, method, directory, manifest, session, findings);
                    warnings[0] = !findings.isEmpty();
                    manifest.put("analysis_status", warnings[0] ? "completed-with-warnings" : "completed");
                    if (warnings[0]) Files.writeString(directory.resolve("WARNINGS.md"), "# NATIVE SEQUENCE WARNINGS\n\n"
                        + String.join("\n", findings.stream().map(x -> "- " + x).toList())
                        + "\n\nRequired action: inspect input identity, sampling/trimming settings, read lengths, reference orientation and scores before downstream analysis. Empty output is not a successful biological absence result. No downstream alignment or interpretation is inferred.\n");
                } catch (Exception failure) {
                    exit[0] = 2; manifest.put("analysis_status", "failed"); manifest.put("error", String.valueOf(failure.getMessage()));
                    try (var files = Files.list(directory)) {
                        for (Path path : files.toList()) {
                            Files.createDirectories(directory.resolve("partial")); Files.move(path, directory.resolve("partial").resolve(path.getFileName()));
                        }
                    }
                    Files.writeString(directory.resolve("WARNINGS.md"), "# NATIVE SEQUENCE ANALYSIS FAILED\n\n" + failure.getMessage()
                        + "\n\nRequired action: correct the named FASTQ pair/quality, parameter, reference or BED3 coordinate error and rerun to a new output directory. This native workflow needs no external executable. Reference operations require uncompressed, consistently wrapped FASTA. Inputs and existing indexes were not altered; partial artifacts are retained under partial/. No successful results.tsv is published.\n");
                }
                Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + manifest.get("analysis_status") + "\"}\n");
                session.put("warnings", findings); Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if (exit[0] != 0) error.println("NATIVE SEQUENCE ANALYSIS FAILED: see " + Path.of(o.get("out"), "WARNINGS.md") + " for the cause and required action.");
            else {
                output.println("Native sequence operation completed: " + o.get("out"));
                if (warnings[0]) error.println("SEQUENCE WARNINGS: see " + Path.of(o.get("out"), "WARNINGS.md"));
            }
            return exit[0];
        } catch (Exception failure) { error.println("ANALYSIS NOT STARTED: " + failure.getMessage()); return 2; }
    }

    private static void reads(Map<String, String> o, String method, Path dir, Map<String, Object> manifest,
            Map<String, Object> session, List<String> findings) throws Exception {
        Path first = file(required(o, "reads")), second = o.containsKey("reads2") ? file(o.get("reads2")) : null;
        var inputs = new LinkedHashMap<String, String>(); inputs.put(first.toString(), hash(first));
        if (second != null) { if (Files.isSameFile(first, second)) throw new IOException("FASTQ mates must be different files"); inputs.put(second.toString(), hash(second)); }
        manifest.put("input_sha256", inputs);
        long seed = 11; double fraction = 1; long[] selected = null; long expected = -1;
        SeqtkMethods.TrimOptions trimming = null;
        if (method.equals("sample")) {
            if (o.containsKey("fraction") == o.containsKey("count")) throw new IllegalArgumentException("Specify exactly one of --fraction or --count");
            seed = Long.parseLong(o.getOrDefault("seed", "11"));
            if (seed < 0) throw new IllegalArgumentException("seed must be a nonnegative signed 64-bit integer");
            if (o.containsKey("count")) {
                int count = integer(o, "count", 0, 0, SeqtkMethods.MAX_SAMPLE_SIZE);
                expected = FastqIO.inspect(first, second).read1().reads();
                selected = SeqtkMethods.sampleIndices(expected, count, seed);
                session.put("sample_count_requested", count); session.put("sample_count_effective", selected.length);
                if (count > expected) findings.add("Requested sample count exceeds input; all available reads/pairs were retained without replacement.");
            } else {
                fraction = number(o, "fraction", 1);
                if (fraction < 0 || fraction > 1) throw new IllegalArgumentException("fraction must be in [0,1]");
                session.put("fraction", fraction);
            }
            session.put("seed", seed); session.put("random_generator", "seqtk MT19937-64, one 53-bit draw per original record/pair");
            session.put("sample_order", "input order, count membership matches seqtk sample -2; fractional 1 explicitly retains all records");
        } else {
            trimming = new SeqtkMethods.TrimOptions(number(o, "error-rate", .05), integer(o, "minimum-retained-length", 30, 0, 10_000_000),
                integer(o, "trim-left", 0, 0, 10_000_000), integer(o, "trim-right", 0, 0, 10_000_000), integer(o, "length", 0, 0, 10_000_000));
            session.put("effective_trimming", Map.of("error_rate", trimming.errorRate(), "minimum_retained_length", trimming.minimumRetainedLength(),
                "trim_left", trimming.left(), "trim_right", trimming.right(), "length_cap", trimming.length()));
            session.put("trimming_scope", "seqtk trimfq Mott/error-probability scoring and best-Phred-window minimum-length fallback; no read filtering. Positive fixed cuts/length disable quality trimming. Short reads are retained unchanged by quality mode; no padding.");
        }
        SeqtkMethods.Random random = new SeqtkMethods.Random(seed);
        long input = 0, kept = 0, bases1 = 0, bases2 = 0, empty = 0, fallback = 0; int selection = 0;
        try (var r1 = new FastqIO.Reader(first); var r2 = second == null ? null : new FastqIO.Reader(second);
            var w1 = new FastqIO.Writer(dir.resolve("reads_R1.fastq.gz")); var w2 = second == null ? null : new FastqIO.Writer(dir.resolve("reads_R2.fastq.gz"))) {
            FastqIO.Read a;
            while ((a = r1.next()) != null) {
                FastqIO.Read b = r2 == null ? null : r2.next();
                if (r2 != null && (b == null || !a.pairId().equals(b.pairId()))) throw new IOException("FASTQ pair count/identity/order mismatch at record " + (input + 1));
                if (input >= SeqtkMethods.MAX_RECORDS) throw new IOException("Record count exceeds exact sampling-index range");
                boolean keep = !method.equals("sample") || (selected == null ? random.nextDouble() < fraction
                    : selection < selected.length && selected[selection] == input);
                input++;
                if (!keep) continue;
                if (selected != null) selection++;
                if (trimming != null) {
                    var trimmed = SeqtkMethods.trim(a, trimming); a = trimmed.read(); if (trimmed.minimumLengthFallback()) fallback++;
                    if (b != null) { trimmed = SeqtkMethods.trim(b, trimming); b = trimmed.read(); if (trimmed.minimumLengthFallback()) fallback++; }
                }
                w1.write(a); if (w2 != null) w2.write(b);
                kept++; bases1 = Math.addExact(bases1, a.sequence().length()); if (b != null) bases2 = Math.addExact(bases2, b.sequence().length());
                if (a.sequence().isEmpty() || b != null && b.sequence().isEmpty()) empty++;
            }
            if (r2 != null && r2.next() != null) throw new IOException("FASTQ mate has extra records");
        }
        if (selected != null && (input != expected || kept != selected.length)) throw new IOException("FASTQ changed between sampling passes");
        for (var entry : inputs.entrySet()) if (!entry.getValue().equals(hash(Path.of(entry.getKey())))) throw new IOException("FASTQ input changed during analysis");
        if (kept == 0) findings.add("No reads/pairs retained; check input size and requested sampling fraction/count.");
        if (empty > 0) findings.add(empty + " retained records/pairs contain a zero-length mate after trimming; choose explicit downstream handling before alignment.");
        if (fallback > 0) findings.add(fallback + " individual reads used the minimum-length fallback, which can retain poor-quality bases. Inspect quality reports; retained length is not a quality-filter pass.");
        if (trimming != null && trimming.left() == 0 && trimming.right() == 0 && trimming.length() == 0)
            session.put("quality_caution", "The minimum-length fallback may retain poor-quality bases; this is trimming, not a quality filter.");
        table(dir.resolve("results.tsv"), List.of("metric", "value"), List.of(new String[]{"input_reads_or_pairs", Long.toString(input)},
            new String[]{"output_reads_or_pairs", Long.toString(kept)}, new String[]{"output_read1_bases", Long.toString(bases1)},
            new String[]{"output_read2_bases", Long.toString(bases2)}, new String[]{"zero_length_records_or_pairs", Long.toString(empty)}));
        session.put("minimum_length_fallback_individual_reads", fallback);
    }

    private static void reference(Map<String, String> o, String method, Path dir, Map<String, Object> manifest,
            Map<String, Object> session, List<String> findings) throws Exception {
        Path fasta = file(required(o, "reference")); String before = hash(fasta); manifest.put("reference_sha256", before);
        try (var reference = new IndexedSequences(fasta)) {
            reference.index().write(dir.resolve("reference.fai"));
            if (method.equals("telo")) {
                var parameters = new SeqtkMethods.TeloOptions(o.getOrDefault("motif", "CCCTAA"), integer(o, "penalty", 1, 0, 10_000_000),
                    integer(o, "maximum-drop", 2000, 0, Integer.MAX_VALUE), integer(o, "minimum-score", 300, 1, Integer.MAX_VALUE));
                session.put("effective_telo", Map.of("motif", parameters.motif(), "penalty", parameters.penalty(), "maximum_drop", parameters.maximumDrop(), "minimum_score", parameters.minimumScore()));
                long total = 0, terminal = 0, segments = 0;
                try (var bed = Files.newBufferedWriter(dir.resolve("telomeres.bed"), StandardCharsets.UTF_8);
                    var results = Files.newBufferedWriter(dir.resolve("results.tsv"), StandardCharsets.UTF_8)) {
                    results.write("chrom\tstart\tend\tsequence_length\tterminal_end\tscore\n");
                    for (var entry : reference.index()) {
                        total = Math.addExact(total, entry.getSize());
                        for (var hit : SeqtkMethods.telomeres(reference.sequence(entry.getContig()), parameters)) {
                            String coordinates = entry.getContig() + "\t" + hit.start() + "\t" + hit.end();
                            bed.write(coordinates + "\n"); results.write(coordinates + "\t" + entry.getSize() + "\t" + hit.terminalEnd() + "\t" + hit.score() + "\n");
                            terminal = Math.addExact(terminal, hit.end() - hit.start()); segments++;
                        }
                    }
                }
                session.put("input_bases", total); session.put("terminal_repeat_bases", terminal);
                session.put("scope", "Terminal motif-rotation scoring only, anchored to supplied sequence orientation. No de novo motif inference, internal-repeat scan, biological chromosome-end guarantee or assembly editing.");
                if (segments == 0) findings.add("No terminal motif segment met the score/drop criteria. This does not establish biological absence or assembly completeness.");
            } else {
                Path regions = file(required(o, "regions")); String regionHash = hash(regions); manifest.put("regions_sha256", regionHash);
                if (Files.size(regions) > 64L * 1024 * 1024) throw new IOException("BED3 input exceeds 64 MiB");
                long rows = 0, bases = 0;
                try (var lines = Files.newBufferedReader(regions, StandardCharsets.UTF_8);
                    var output = Files.newBufferedWriter(dir.resolve("sequences.fasta"), StandardCharsets.UTF_8);
                    var results = Files.newBufferedWriter(dir.resolve("results.tsv"), StandardCharsets.UTF_8)) {
                    results.write("sequence_id\tchrom\tstart\tend\tlength\n"); String line;
                    while ((line = lines.readLine()) != null) {
                        if (line.isBlank() || line.startsWith("#")) continue;
                        if (++rows > 1_000_000) throw new IOException("At most one million BED3 regions are supported");
                        String[] cells = line.split("\t", -1);
                        if (cells.length != 3 || cells[0].isBlank()) throw new IOException("Require headerless BED3: chrom, zero-based start, exclusive end");
                        long start = Long.parseLong(cells[1]), end = Long.parseLong(cells[2]);
                        var sequence = reference.sequence(cells[0]);
                        if (start < 0 || end <= start || end > sequence.length()) throw new IOException("BED3 region is empty or outside the exact reference: " + line);
                        String id = cells[0] + ":" + (start + 1) + "-" + end;
                        output.write(">" + id + "\n");
                        for (long i = start; i < end; i++) {
                            char base = sequence.base(i);
                            if ("ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(base) < 0) throw new IOException("Non-IUPAC nucleotide in extracted reference interval");
                            output.write(base); if ((i - start + 1) % 60 == 0) output.write('\n');
                        }
                        if ((end - start) % 60 != 0) output.write('\n');
                        results.write(id + "\t" + cells[0] + "\t" + start + "\t" + end + "\t" + (end - start) + "\n");
                        bases = Math.addExact(bases, end - start);
                    }
                }
                if (!regionHash.equals(hash(regions))) throw new IOException("BED3 regions changed during extraction");
                session.put("regions", rows); session.put("extracted_bases", bases);
                session.put("scope", "Headerless BED3, zero-based half-open coordinates, forward reference sequence, strict bounds, request order and 60-base FASTA lines. Labels display one-based inclusive positions. No inferred strand, gap insertion, assembly editing or automatic clipping of out-of-range intervals.");
                if (rows == 0) findings.add("BED3 had no regions; no sequences were extracted.");
            }
        }
        if (!before.equals(hash(fasta))) throw new IOException("Reference FASTA changed during analysis");
    }
    private static Path file(String text) throws IOException {
        Path path = Path.of(text).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException("Input file does not exist: " + path); return path;
    }
    static String help() { return """
        sequence-tools --method sample|trimfq|subseq|telo --out NEW_DIRECTORY
        Native Java; no external runtime or installation required.
        sample --reads R1.fastq[.gz] [--reads2 R2.fastq[.gz]] (--fraction 0..1 | --count 0..50000000)
          [--seed 11]. MT19937-64 as seqtk; paired IDs/order validated and one draw per pair.
          Count uses two passes, input-order output (seqtk sample -2 membership), 8 bytes/index
          plus sorting memory. Requests above input size retain all; fraction 1 means all.
        trimfq --reads R1.fastq[.gz] [--reads2 R2.fastq[.gz]]
          [--error-rate 0.05 --minimum-retained-length 30 --trim-left 0 --trim-right 0 --length 0].
          Mott scoring plus seqtk minimum-length fallback; no read filtering/padding.
          Any positive fixed cut or length disables quality trimming. --length 50 retains
          at most the first 50 bases after cuts. Matched qualities and both mates preserved.
        subseq --reference UNCOMPRESSED.fa --regions HEADERLESS_BED3
          Strict zero-based half-open intervals, forward reference, request order. FASTA labels
          use one-based inclusive coordinates; 60-base output lines. Bounds errors fail.
        telo --reference UNCOMPRESSED.fa [--motif CCCTAA --penalty 1 --maximum-drop 2000 --minimum-score 300]
          seqtk terminal motif-rotation scoring; both oriented ends, zero-based half-open BED.
          Motif length 1..31 canonical bases; positive score. No assembly completeness inference.
        FASTQ: strict four-line IUPAC/Phred+33, plain/gzip, <=10 million bases per read.
        FASTA: indexed from actual LF/CRLF bytes, source/old indexes preserved, chunked access.
        Empty/no-hit output warns prominently. Invalid inputs return 2 with retained diagnostics,
          quarantined partial outputs and WARNINGS.md recovery. Existing destinations preserved.
        """; }
}
