/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.TrimGaloreReads;
import static org.jlinalg.cli.FollowupSupport.*;

/** Actual original Trim Galore ordinary/hardtrim workflows with complete stream audit. */
final class TrimGaloreToolsCli {
    private TrimGaloreToolsCli() { }
    private static final Set<String> TRIM = Set.of("phred", "quality", "nextseq", "minimum-length", "maximum-length", "maximum-n", "overlap", "error-rate",
            "trim-n", "adapter-kind", "adapter1", "adapter2", "already-trimmed-limit", "clip-r1", "clip-r2", "three-prime-clip-r1", "three-prime-clip-r2", "retain-unpaired", "orphan-min1", "orphan-min2");
    private static final String RECOVERY = "Required action: correct the named cause and rerun to a new output directory. Current Windows Cp1252 JVM launchers can replace non-ANSI command-line path characters;put such paths/settings in a UTF8 JSON --options-file whose own CLI path is ASCII,or use ASCII-named staging copies and verify source hashes. Ordinary Cutadapt trimming additionally requires ASCII read headers/comments;use an explicit reversible ID mapping when needed,never silently truncate identities. For unavailable dependencies, where permitted run python tools/literature/install_trim_galore.py --allow-install --allow-download --perl C:/cygwin64/bin/perl.exe --python build/omics-sequence-tools-env/Scripts/python.exe, adding --fastqc-home build/omics-fastqc/FastQC --java PATH_TO_JAVA when QC is requested. Supply that profile with --home. Preserve changed/unmanaged profiles and failed attempts; choose a new private environment after correction. Skip affected analyses if installation is forbidden or unavailable. No analysis command installs software.";

    static int run(String[] arguments, PrintStream out, PrintStream err) {
        if (arguments.length == 1 && arguments[0].equals("--help")) { out.println(help()); return 0; }
        try {
            var allowed = new java.util.HashSet<>(TRIM); allowed.addAll(Set.of("home", "release", "mode", "reads", "reads2", "out", "bases", "fastqc", "fastqc-memory-mb", "gzip", "timeout", "options-file"));
            var parsed = parse(arguments, allowed); var options = parsed.values(); required(options, "out"); int[] code = {0}; boolean[] warned = {false};
            externalTransaction(options, "trim-galore-original", (directory, manifest) -> {
                var session = new LinkedHashMap<String, Object>(); var warnings = new ArrayList<String>();
                session.put("engine", "original-unmodified-perl-and-cutadapt");
                session.put("scope", "Original0.6.7/0.6.10 ordinary adapter/quality trimming, pair finalization, optional original FastQC0.13, or separate hardtrim crop. Native Java verifies every Cutadapt source span and final paired/single/orphan emission. No RRBS,explicit poly-A,UMI,clock,multicore,arbitrary shell arguments,Rust rewrite,historical Cutadapt/old FastQC or whole-paper parity.");
                try {
                    verifyOptions(parsed);
                    if (parsed.source() != null) {
                        Path snapshot = directory.resolve("input-options.json"); Files.copy(parsed.source(), snapshot);
                        if (!hash(snapshot).equals(parsed.sha256())) throw new IOException("UTF8 options file changed while snapshotting");
                        manifest.put("options_file_sha256", parsed.sha256());
                    }
                    String release = required(options, "release"), operation = options.getOrDefault("mode", "trim");
                    if (!Set.of("0.6.7", "0.6.10").contains(release)) throw new IllegalArgumentException("--release must explicitly select 0.6.7 or 0.6.10; no historical-version substitution");
                    if (!Set.of("trim", "hardtrim5", "hardtrim3").contains(operation)) throw new IllegalArgumentException("--mode requires trim, hardtrim5 or hardtrim3");
                    boolean trim = operation.equals("trim"), paired = options.containsKey("reads2"), fastqc = bool(options, "fastqc", false);
                    if (!fastqc && options.containsKey("fastqc-memory-mb")) throw new IllegalArgumentException("--fastqc-memory-mb requires --fastqc true");
                    if (!trim && (fastqc || options.keySet().stream().anyMatch(TRIM::contains))) throw new IllegalArgumentException("Original hardtrim exits before adapter/quality/filter/FastQC processing; remove those options");
                    if (trim && options.containsKey("bases")) throw new IllegalArgumentException("--bases applies only to original hardtrim5/hardtrim3");
                    var settings = new LinkedHashMap<String, Object>();
                    if (!trim) { required(options, "bases"); settings.put("bases", integer(options, "bases", 0, 1, 999)); }
                    else settings.putAll(settings(options, release, paired));
                    session.put("release", release); session.put("operation", operation); session.put("paired", paired); session.put("explicit_settings", settings); session.put("fastqc_requested", fastqc);
                    Path first = source(options, "reads"), second = paired ? source(options, "reads2") : null;
                    if (paired && Files.isSameFile(first, second)) throw new IllegalArgumentException("Mates must be different files");
                    var sources = new LinkedHashMap<Path, String>(); sources.put(first, hash(first)); if (paired) sources.put(second, hash(second));
                    manifest.put("input_sha256", sources.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)));
                    Path original = Files.createDirectory(directory.resolve("original"));
                    long input = stage(first, second, original, trim ? ((Number) settings.get("phred")).intValue() : 33, trim);
                    session.put("input_reads_or_pairs", input);
                    var runtime = new TrimGaloreRuntime(Path.of(required(options, "home")), fastqc, session);
                    runtime.execute(original, release, operation, paired, fastqc, integer(options, "fastqc-memory-mb", 512, 250, 65536), settings,
                            integer(options, "timeout", 3600, 1, 86400), session);
                    var audit = TrimGaloreAudit.verify(directory, release, operation, paired, settings, session); warnings.addAll(audit.warnings());
                    session.put("native_audit_counts", audit.counts());
                    if (fastqc) {
                        var rows = new ArrayList<String[]>(); var findings = new ArrayList<String>();
                        SequenceToolsCli.qcReport(original, audit.outputs().get(0), audit.counts().get("retained_reads_or_pairs"), "read1", rows, findings);
                        if (paired) SequenceToolsCli.qcReport(original, audit.outputs().get(1), audit.counts().get("retained_reads_or_pairs"), "read2", rows, findings);
                        table(directory.resolve("fastqc-results.tsv"), List.of("read", "module", "qc_status", "input_filename"), rows);
                        if (!findings.isEmpty()) warnings.add("Original FastQC module findings: " + String.join("; ", findings) + ". Inspect its preserved HTML/ZIP with assay/library context before downstream analysis; QC findings are not automatic instructions to discard reads.");
                        var diagnostics = new LinkedHashSet<String>();
                        try (var files = Files.list(original.resolve("transport-audit"))) {
                            for (Path log : files.filter(p -> p.getFileName().toString().matches("fastqc-[0-9]{3}\\.stderr-raw")).toList())
                                for (String line : Files.readAllLines(log)) if (line.matches("(?i).*(?:using phred|could this file be|warning|failed|exception).*")) diagnostics.add(line);
                        }
                        if (!diagnostics.isEmpty()) warnings.add("Original FastQC diagnostics: " + String.join("; ", diagnostics) + ". Review the source quality-encoding assumptions and original QC report. The adapter preserves these original diagnostics and does not change the declared trim encoding or repair QC values.");
                        session.put("fastqc_findings", findings); session.put("fastqc_diagnostics", diagnostics);
                    }
                    runtime.verify();
                    verifyOptions(parsed);
                    for (var source : sources.entrySet()) if (!hash(source.getKey()).equals(source.getValue())) throw new IOException("Input changed during original trimming: " + source.getKey());
                    boolean gzip = bool(options, "gzip", true);
                    for (int index = 0; index < audit.outputs().size(); index++) {
                        Path destination = directory.resolve(audit.labels().get(index) + (gzip ? ".fastq.gz" : ".fastq"));
                        if (gzip) try (var output = new GZIPOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) { Files.copy(audit.outputs().get(index), output); }
                        else Files.copy(audit.outputs().get(index), destination);
                    }
                    var rows = new ArrayList<String[]>(); for (var entry : audit.counts().entrySet()) rows.add(new String[]{entry.getKey(), entry.getValue().toString()});
                    table(directory.resolve("results.tsv"), List.of("metric", "native_verified_value"), rows);
                    warned[0] = !warnings.isEmpty(); String state = warned[0] ? "completed-with-qc-warnings" : "completed";
                    manifest.put("analysis_status", state); status(directory, state);
                    if (warned[0]) Files.writeString(directory.resolve("WARNINGS.md"), "# ORIGINAL TRIM GALORE / QC WARNINGS\n\n" + String.join("\n\n", warnings) + "\n\nRequired action: inspect the named original reports, native read-status.tsv and separate orphan streams before downstream analysis. Correct unsuitable settings explicitly in a new run; no downstream analysis was started.\n");
                } catch (Exception failure) {
                    boolean unavailable = failure instanceof TrimGaloreRuntime.Unavailable; code[0] = unavailable ? 3 : 2;
                    String state = unavailable ? "skipped-dependency" : "failed"; manifest.put("analysis_status", state); manifest.put("error", failure.toString());
                    try (var files = Files.list(directory)) {
                        for (Path path : files.toList()) if (Files.isDirectory(path) && path.getFileName().toString().equals("original")
                                || path.getFileName().toString().matches("(?:trimmed|unpaired)_R[12]\\.fastq(?:\\.gz)?|results\\.tsv|fastqc-results\\.tsv|read-status\\.tsv")) {
                            Files.createDirectories(directory.resolve("partial")); Files.move(path, directory.resolve("partial").resolve(path.getFileName()));
                        }
                    }
                    status(directory, state);
                    Files.writeString(directory.resolve("WARNINGS.md"), (unavailable ? "# ANALYSIS SKIPPED - ORIGINAL TRIM GALORE DEPENDENCY UNAVAILABLE\n\n" : "# ORIGINAL TRIM GALORE ANALYSIS FAILED\n\n") + failure
                            + "\n\n" + RECOVERY + "\n\nOriginal logs, partial streams and native audit prefixes are retained under partial/. No successful results.tsv is published.\n");
                } finally { Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session)); }
            });
            if (code[0] == 0) { out.println("Original Trim Galore workflow and native stream audit completed: " + options.get("out")); if (warned[0]) err.println("QC WARNINGS: read " + Path.of(options.get("out")).resolve("WARNINGS.md") + " before downstream analysis."); }
            else err.println((code[0] == 3 ? "ANALYSIS SKIPPED" : "ANALYSIS FAILED") + ": see " + Path.of(options.get("out")).resolve("WARNINGS.md") + " for the error and required action.");
            return code[0];
        } catch (Exception failure) { err.println("ORIGINAL TRIM GALORE FAILED: " + failure.getMessage() + ". " + RECOVERY); return 2; }
    }

    private static Map<String, Object> settings(Map<String, String> options, String release, boolean paired) {
        var result = new LinkedHashMap<String, Object>();
        int phred = Integer.parseInt(required(options, "phred"));
        if (phred != 33 && phred != 64) throw new IllegalArgumentException("--phred must explicitly be33or64"); result.put("phred", phred);
        if (options.containsKey("quality") && options.containsKey("nextseq")) throw new IllegalArgumentException("--quality and --nextseq are mutually exclusive in the original program");
        if (options.containsKey("nextseq")) {
            if (phred == 64) throw new IllegalArgumentException("Combined original --nextseq/Phred64 behavior is unvalidated; no encoding or algorithm substitution is made");
            result.put("nextseq", integer(options, "nextseq", 20, 1, 199));
        } else if (options.containsKey("quality")) result.put("quality", integer(options, "quality", 20, 0, 199));
        for (String key : List.of("minimum-length", "maximum-length")) if (options.containsKey(key)) result.put(key.replace('-', '_'), integer(options, key, 20, key.equals("maximum-length") ? 0 : 1, 10_000_000));
        if (options.containsKey("maximum-n")) {
            double maximum = number(options, "maximum-n", 0);
            new TrimGaloreReads.Filters(release.equals("0.6.7") ? TrimGaloreReads.Release.V0_6_7 : TrimGaloreReads.Release.V0_6_10, 1, 0, maximum);
            result.put("maximum_n", maximum == Math.rint(maximum) ? (Object) (int) maximum : maximum);
        }
        if (paired && release.equals("0.6.7") && options.containsKey("maximum-length") && integer(options, "maximum-length", 0, 0, 10_000_000) > 0)
            throw new IllegalArgumentException("Original0.6.7 rejects paired maximum length; keep the declared version and valid options");
        if (options.containsKey("overlap")) result.put("overlap", integer(options, "overlap", 1, 1, 1000));
        if (options.containsKey("error-rate")) { double value = number(options, "error-rate", 0.1); if (value < 0 || value > 1) throw new IllegalArgumentException("--error-rate must be0..1"); result.put("error_rate", value); }
        if (options.containsKey("trim-n")) result.put("trim_n", bool(options, "trim-n", false));
        String kind = options.getOrDefault("adapter-kind", "auto");
        if (!Set.of("auto", "custom", "none", "illumina", "nextera", "small-rna", "stranded-illumina").contains(kind)) throw new IllegalArgumentException("Unsupported --adapter-kind");
        if (kind.equals("stranded-illumina") && release.equals("0.6.7")) throw new IllegalArgumentException("Original0.6.7 lacks stranded-illumina preset");
        result.put("adapter_kind", kind);
        if (kind.equals("custom")) result.put("adapter1", adapter(required(options, "adapter1")));
        else if (options.containsKey("adapter1")) throw new IllegalArgumentException("--adapter1 requires --adapter-kind custom");
        if (options.containsKey("adapter2")) { if (!paired || kind.equals("none")) throw new IllegalArgumentException("--adapter2 requires paired adapter trimming"); result.put("adapter2", adapter(options.get("adapter2"))); }
        if (options.containsKey("already-trimmed-limit")) { if (!kind.equals("auto")) throw new IllegalArgumentException("--already-trimmed-limit requires --adapter-kind auto"); result.put("already_trimmed_limit", integer(options, "already-trimmed-limit", 0, 0, 1_000_000)); }
        if (!paired && List.of("clip-r2", "three-prime-clip-r2", "retain-unpaired", "orphan-min1", "orphan-min2").stream().anyMatch(options::containsKey))
            throw new IllegalArgumentException("Mate2/rescue options require --reads2");
        for (String key : List.of("clip-r1", "clip-r2", "three-prime-clip-r1", "three-prime-clip-r2")) if (options.containsKey(key)) {
            int value = integer(options, key, 0, 0, 99); if (value > 0) result.put(key.replace('-', '_'), value);
        }
        boolean rescue = bool(options, "retain-unpaired", false); if (rescue) result.put("retain_unpaired", true);
        for (String key : List.of("orphan-min1", "orphan-min2")) if (options.containsKey(key)) {
            if (!rescue) throw new IllegalArgumentException("Explicit orphan thresholds require --retain-unpaired true");
            int value = integer(options, key, 35, 15, 100);
            if (options.containsKey("minimum-length") && value <= integer(options, "minimum-length", 20, 1, 10_000_000)) throw new IllegalArgumentException("Explicit orphan thresholds must exceed paired minimum");
            result.put(key.replace('-', '_'), value);
        }
        return result;
    }

    private static String adapter(String value) { if (!value.matches("[ACGTNacgtn]{1,1000}")) throw new IllegalArgumentException("Adapter must be literal DNA1..1000bases;no file,shell or Cutadapt-expression syntax"); return value; }
    private record Parsed(Map<String, String> values, Path source, String sha256) { }
    private static Parsed parse(String[] arguments, Set<String> allowed) throws Exception {
        var direct = options(arguments, allowed);
        if (!direct.containsKey("options-file")) return new Parsed(direct, null, null);
        Path file = Path.of(direct.get("options-file")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || Files.size(file) > 1_048_576) throw new IOException("Require a UTF8 JSON options object of at most1MiB");
        byte[] bytes = Files.readAllBytes(file);
        Object value = SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        if (!(value instanceof Map<?, ?> fields)) throw new IllegalArgumentException("--options-file requires a JSON object of known option names and string values");
        var effective = new LinkedHashMap<String, String>();
        for (var entry : fields.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!allowed.contains(key) || key.equals("options-file") || !(entry.getValue() instanceof String text))
                throw new IllegalArgumentException("Unknown,recursive or non-string option in --options-file: " + key);
            effective.put(key, text);
        }
        effective.putAll(direct);
        String checksum = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        return new Parsed(effective, file, checksum);
    }
    private static void verifyOptions(Parsed options) throws IOException {
        if (options.source() != null && !hash(options.source()).equals(options.sha256())) throw new IOException("UTF8 options file changed during the original workflow;use a stable recipe and rerun");
    }
    private static Path source(Map<String, String> options, String key) throws IOException { Path path = Path.of(required(options, key)).toAbsolutePath().normalize(); if (!Files.isRegularFile(path)) throw new IOException("Input FASTQ file unavailable: " + path); return path; }
    private static long stage(Path first, Path second, Path original, int phred, boolean ordinary) throws Exception {
        long count = 0;
        try (var a = new FastqIO.Reader(first); var b = second == null ? null : new FastqIO.Reader(second);
             var x = new FastqIO.Writer(original.resolve("reads1.fastq")); var y = second == null ? null : new FastqIO.Writer(original.resolve("reads2.fastq"))) {
            FastqIO.Read read;
            while ((read = a.next()) != null) {
                count++; FastqIO.Read mate = b == null ? null : b.next();
                if (b != null && (mate == null || !read.pairId().equals(mate.pairId()))) throw new IOException("FASTQ mate identity/order/count mismatch at record " + count);
                if (ordinary && (read.header().chars().anyMatch(c -> c > 127) || mate != null && mate.header().chars().anyMatch(c -> c > 127)))
                    throw new IOException("Original Cutadapt ordinary trimming rejects non-ASCII read headers/comments at record " + count + ". Supply an explicit reversible ASCII-ID mapping before analysis;no identifiers were silently changed");
                if (phred == 64 && (read.quality().chars().anyMatch(c -> c < 64) || mate != null && mate.quality().chars().anyMatch(c -> c < 64))) throw new IOException("Declared Phred64 input contains quality characters below ASCII64");
                x.write(read); if (y != null) y.write(mate);
            }
            if (b != null && b.next() != null) throw new IOException("FASTQ mate has extra records");
        }
        if (count == 0) throw new IOException("Input FASTQ has no reads"); return count;
    }
    private static void status(Path directory, String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + state + "\"}\n"); }
    static String help() { return """
        trim-galore-tools --home PRIVATE_PROFILE --release 0.6.7|0.6.10 --reads R1.fq[.gz]
          [--reads2 R2.fq[.gz]] --mode trim --phred 33|64 --out NEW_DIR
          [--quality INT | --nextseq INT] [--minimum-length INT] [--maximum-length INT]
          [--maximum-n COUNT_OR_FRACTION] [--overlap INT] [--error-rate 0..1]
          [--trim-n true|false] [--adapter-kind auto|custom|none|illumina|nextera|small-rna|stranded-illumina]
          [--adapter1 DNA] [--adapter2 DNA] [--already-trimmed-limit INT]
          [--clip-r1 0..99] [--clip-r2 0..99] [--three-prime-clip-r1 0..99]
          [--three-prime-clip-r2 0..99] [--retain-unpaired true|false]
          [--orphan-min1 INT] [--orphan-min2 INT] [--fastqc true|false]
          [--fastqc-memory-mb 512] [--gzip true|false] [--timeout 3600]
        trim-galore-tools --home PRIVATE_PROFILE --release 0.6.7|0.6.10
          --mode hardtrim5|hardtrim3 --bases 1..999 --reads R1.fq[.gz]
          [--reads2 R2.fq[.gz]] --out NEW_DIR [--gzip true|false] [--timeout 3600]
        [--options-file UTF8_OPTIONS.json] reads a strict object of known option names
        and STRING values;explicit CLI values override it. File snapshot/hash retained.
        Use this with an ASCII options-file path for Unicode data paths on Windows.
        Actual unmodified original Perl plus Cutadapt5.2, single core.
        Ordinary trimming requires ASCII read headers/comments,matching original
        Cutadapt's input contract. Hardtrim preserves UTF8 headers. On current
        Windows Cp1252 launchers use ASCII path arguments/staging copies;verify hashes.
        Explicit Phred encoding;omitted adapter/quality/length options retain ORIGINAL defaults
        and auto-detection. Small-RNA default min18 can differ from ordinary min20.
        Hardtrim keeps first/last N and exits before other processing; no extra filters.
        Exact headers, source spans, qualities, paired/single/orphan outputs, raw reports,
        dependency fingerprints and original FastQC diagnostics/flags are retained.
        Native audit preserves the original clipping guards, N and rescue edge cases.
        Original0.6.7 rejects paired maximum length and fractional N; stranded-illumina
        is0.6.10only. Combined NextSeq/Phred64 is unvalidated and rejected explicitly.
        No RRBS,explicit poly-A,clock/UMI,multicore,arbitrary shell/FastQC arguments,
        Rust rewrite,historical Cutadapt/old FastQC or complete-paper parity.
        Missing/changed/forbidden dependencies: SKIP exit3 with exact recovery.
        Input/execution/audit failure: exit2,original partial data/logs retained and
        no successful results.tsv. QC findings remain prominent before downstream use.
        """; }
}
