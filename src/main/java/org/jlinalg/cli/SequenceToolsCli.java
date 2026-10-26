/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.jlinalg.sequence.FastqIO;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed sequencing QC adapters; no shell command templates or implicit installation. */
final class SequenceToolsCli {
    private SequenceToolsCli() { }
    private static final Set<String> COMMON = Set.of("method", "out", "timeout");
    private static final Set<String> CUTADAPT = Set.of("python", "reads", "reads2", "adapter3", "adapter3-2",
        "minimum-length", "quality-cutoff", "overlap", "error-rate", "cores", "cut-left");
    private static final Set<String> FASTQC = Set.of("reads", "reads2", "fastqc-home", "java", "threads", "memory-mb");
    private static final Set<String> MULTIQC = Set.of("python", "reports");
    private static final Set<String> FIXED = Set.of("reads", "reads2", "first-base", "last-base", "minimum-length");

    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (Arrays.asList(arguments).contains("--help")) { output.println(help()); return 0; }
        try {
            Set<String> all = new HashSet<>(COMMON); all.addAll(CUTADAPT); all.addAll(FASTQC); all.addAll(MULTIQC); all.addAll(FIXED);
            Map<String, String> o = options(arguments, all);
            String method = required(o, "method");
            Set<String> applicable = switch (method) {
                case "cutadapt" -> CUTADAPT; case "fastqc" -> FASTQC; case "multiqc" -> MULTIQC; case "trim-fixed" -> FIXED;
                default -> throw new IllegalArgumentException("--method must be trim-fixed, cutadapt, fastqc or multiqc");
            };
            for (String key : o.keySet()) if (!COMMON.contains(key) && !applicable.contains(key))
                throw new IllegalArgumentException("--" + key + " does not apply to " + method);
            if (method.equals("trim-fixed")) return FixedTrimCli.run(o, output, error);
            final int[] exit = {0}; final boolean[] findings = {false};
            transaction(o, "sequence-qc-" + method, (directory, manifest) -> {
                Map<String, Object> session = new LinkedHashMap<>();
                session.put("method", method); session.put("installation_policy", "No implicit installation; use the explicit private-environment helpers where permitted");
                try {
                    switch (method) {
                        case "cutadapt" -> findings[0] = trim(o, directory, manifest, session);
                        case "fastqc" -> findings[0] = fastqc(o, directory, manifest, session);
                        case "multiqc" -> findings[0] = aggregate(o, directory, manifest, session);
                        default -> throw new AssertionError(method);
                    }
                    manifest.put("analysis_status", findings[0] ? "completed-with-qc-warnings" : "completed");
                    status(directory, findings[0] ? "completed-with-qc-warnings" : "completed");
                } catch (Exception failure) {
                    exit[0] = failure instanceof DependencyUnavailable ? 3 : 2;
                    String state = exit[0] == 3 ? "skipped-dependency-unavailable" : "failed";
                    manifest.put("analysis_status", state); manifest.put("error", failure.getMessage());
                    quarantine(directory);
                    status(directory, state);
                    Files.writeString(directory.resolve("WARNINGS.md"), "# " + (exit[0] == 3 ? "ANALYSIS SKIPPED - DEPENDENCY UNAVAILABLE" : "ANALYSIS FAILED")
                        + "\n\n" + failure.getMessage() + "\n\nRequired action: " + recovery(method)
                        + " Inspect the input contracts and version/analysis logs, correct the cause and rerun to a new output directory. Partial artifacts, if any, are quarantined under partial/. No successful results.tsv is published.\n");
                }
                Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if (exit[0] != 0) error.println((exit[0] == 3 ? "SKIPPED ANALYSIS" : "ANALYSIS FAILED") + ": see " + Path.of(o.get("out")).resolve("WARNINGS.md") + " for the error and required action.");
            else {
                output.println("Sequencing QC completed: " + o.get("out"));
                if (findings[0]) error.println("QC WARNINGS: see " + Path.of(o.get("out")).resolve("WARNINGS.md") + " and the preserved source/quality reports before downstream analysis.");
            }
            return exit[0];
        } catch (Exception failure) { error.println("ANALYSIS NOT STARTED: " + failure.getMessage()); return 2; }
    }

    private static boolean trim(Map<String, String> o, Path directory, Map<String, Object> manifest,
            Map<String, Object> session) throws Exception {
        String python = executable(required(o, "python"));
        probe(pythonCommand(python, "cutadapt", "--version"), "5.2", directory, "cutadapt", session);
        pythonEnvironment(python, directory, session);
        Path read1 = reads(o, "reads"), read2 = o.containsKey("reads2") ? reads(o, "reads2") : null;
        if (read2 == null && o.containsKey("adapter3-2")) throw new IllegalArgumentException("--adapter3-2 requires --reads2");
        String adapter1 = adapter(required(o, "adapter3"));
        String adapter2 = read2 == null ? null : adapter(required(o, "adapter3-2"));
        int minimum = integer(o, "minimum-length", 0, 0, 10_000_000);
        int quality = integer(o, "quality-cutoff", 0, 0, 93), overlap = integer(o, "overlap", 3, 1, 10000);
        int cores = integer(o, "cores", 1, 1, 64), left = integer(o, "cut-left", 0, 0, 10_000_000);
        double errorRate = number(o, "error-rate", 0.1);
        if (errorRate < 0 || errorRate > 1) throw new IllegalArgumentException("--error-rate must be in [0, 1]");
        FastqIO.Inspection input = inspect(read1, read2, session, "input");
        if (input.read1().reads() == 0) throw new IllegalArgumentException("Input FASTQ has no reads");
        inputHashes(manifest, Map.of("reads", read1));
        if (read2 != null) inputHashes(manifest, Map.of("reads2", read2));
        List<String> command = new ArrayList<>(pythonCommand(python, "cutadapt"));
        command.addAll(List.of("--cores", Integer.toString(cores), "--quality-base", "33", "--error-rate", Double.toString(errorRate),
            "--overlap", Integer.toString(overlap), "--minimum-length", Integer.toString(minimum), "--quality-cutoff", Integer.toString(quality),
            "--cut", Integer.toString(left), "-a", adapter1, "--json", directory.resolve("cutadapt.json").toString(),
            "-o", directory.resolve("trimmed_R1.fastq.gz").toString()));
        if (read2 != null) command.addAll(List.of("-A", adapter2, "--pair-filter", "any", "-U", Integer.toString(left),
            "-p", directory.resolve("trimmed_R2.fastq.gz").toString()));
        command.add(read1.toString()); if (read2 != null) command.add(read2.toString());
        execute(command, directory, "cutadapt.log", integer(o, "timeout", 3600, 1, 86400), session);
        Map<?, ?> report = jsonMap(directory.resolve("cutadapt.json"));
        if (!"5.2".equals(report.get("cutadapt_version"))) throw new IOException("Cutadapt JSON version mismatch");
        Map<?, ?> counts = map(report.get("read_counts"), "Cutadapt read_counts");
        if (count(counts.get("input")) != input.read1().reads()) throw new IOException("Cutadapt input read counts disagree with validated FASTQ");
        FastqIO.Inspection result = inspect(directory.resolve("trimmed_R1.fastq.gz"), read2 == null ? null : directory.resolve("trimmed_R2.fastq.gz"), session, "output");
        if (count(counts.get("output")) != result.read1().reads() || result.read1().reads() > input.read1().reads())
            throw new IOException("Cutadapt output counts disagree with validated FASTQ");
        for (FastqIO.Stats stats : read2 == null ? List.of(result.read1()) : List.of(result.read1(), result.read2()))
            if (stats.reads() > 0 && stats.minLength() < minimum) throw new IOException("Cutadapt output violates minimum length");
        session.put("scope", "Cutadapt 5.2 regular 3-prime adapters, 3-prime quality trimming, fixed 5-prime cut, indels enabled, Phred+33, minimum-length filtering; paired reads filtered together if either fails. No demultiplexing or linked/5-prime adapter workflow exposed.");
        session.put("effective_parameters", Map.of("adapter3", adapter1, "adapter3_2", adapter2 == null ? "not-paired" : adapter2,
            "minimum_length", minimum, "quality_cutoff", quality, "overlap", overlap, "error_rate", errorRate, "cut_left_both_mates", left, "cores", cores));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"input_reads_or_pairs", Long.toString(input.read1().reads())});
        rows.add(new String[]{"output_reads_or_pairs", Long.toString(result.read1().reads())});
        rows.add(new String[]{"read1_with_adapter", Long.toString(count(counts.get("read1_with_adapter")))});
        if (read2 != null) rows.add(new String[]{"read2_with_adapter", Long.toString(count(counts.get("read2_with_adapter")))});
        table(directory.resolve("results.tsv"), List.of("metric", "value"), rows);
        if (result.read1().reads() == 0) Files.writeString(directory.resolve("WARNINGS.md"), "# CUTADAPT RETAINED NO READS\n\n"
            + "All " + input.read1().reads() + " input reads/pairs were filtered. Output FASTQ files are empty; trimming completed, but downstream alignment or biological absence must not be inferred.\n\n"
            + "Required action: inspect cutadapt.json/cutadapt.log, adapter sequences, fixed cuts, quality and minimum-length settings, and the input library. Correct the cause and rerun to a new output directory before downstream analysis. Paired filtering removes both mates if either fails.\n");
        return result.read1().reads() == 0;
    }

    private static boolean fastqc(Map<String, String> o, Path directory, Map<String, Object> manifest,
            Map<String, Object> session) throws Exception {
        Path home = Path.of(required(o, "fastqc-home")).toAbsolutePath().normalize();
        List<Path> dependencies = List.of(home, home.resolve("htsjdk.jar"), home.resolve("commons-io-2.22.0.jar"),
            home.resolve("commons-compress-1.28.0.jar"), home.resolve("cisd-jhdf5.jar"));
        if (!Files.isRegularFile(home.resolve("uk/ac/babraham/FastQC/FastQCApplication.class"))) throw new DependencyUnavailable("FastQC compiled application is unavailable at " + home);
        for (Path path : dependencies.subList(1, dependencies.size())) if (!Files.isRegularFile(path)) throw new DependencyUnavailable("FastQC dependency is unavailable: " + path);
        String java = o.containsKey("java") ? executable(o.get("java")) : Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classpath = String.join(File.pathSeparator, dependencies.stream().map(Path::toString).toList());
        List<String> version = List.of(java, "-Djava.awt.headless=true", "-Dfastqc.show_version=true", "-cp", classpath, "uk.ac.babraham.FastQC.FastQCApplication");
        probe(version, "FastQC v0.13.0", directory, "fastqc", session);
        execute(List.of(java, "-version"), directory, "java-version.log", 60, session);
        List<String[]> dependencyRows = new ArrayList<>();
        try (var paths = Files.walk(home)) {
            List<Path> files = paths.filter(Files::isRegularFile).sorted().toList();
            if (files.size() > 2000) throw new DependencyUnavailable("Unexpectedly large FastQC distribution; use the pinned compiled release");
            for (Path path : files) dependencyRows.add(new String[]{home.relativize(path).toString(), hash(path)});
        }
        table(directory.resolve("dependency-files.tsv"), List.of("release_file", "sha256"), dependencyRows);
        session.put("distribution_manifest_sha256", hash(directory.resolve("dependency-files.tsv")));
        Path read1 = reads(o, "reads"), read2 = o.containsKey("reads2") ? reads(o, "reads2") : null;
        if (read2 != null && base(read1).equalsIgnoreCase(base(read2))) throw new IllegalArgumentException("FASTQ basenames must differ to avoid colliding FastQC output names");
        FastqIO.Inspection input = inspect(read1, read2, session, "input");
        if (input.read1().reads() == 0) throw new IllegalArgumentException("FastQC input contains no reads");
        inputHashes(manifest, Map.of("reads", read1)); if (read2 != null) inputHashes(manifest, Map.of("reads2", read2));
        int threads = integer(o, "threads", 1, 1, 64), memory = integer(o, "memory-mb", 512, 250, 65536);
        List<String> command = new ArrayList<>(List.of(java, "-Xmx" + memory + "m", "-XX:+ExitOnOutOfMemoryError", "-XX:ParallelGCThreads=1",
            "-Djava.awt.headless=true", "-Dfastqc.output_dir=" + directory, "-Dfastqc.threads=" + threads,
            "-Dfastqc.sequence_format=fastq", "-cp", classpath, "uk.ac.babraham.FastQC.FastQCApplication", read1.toString()));
        if (read2 != null) command.add(read2.toString());
        execute(command, directory, "fastqc.log", integer(o, "timeout", 3600, 1, 86400), session);
        List<String[]> rows = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        qcReport(directory, read1, input.read1().reads(), "read1", rows, flags);
        if (read2 != null) qcReport(directory, read2, input.read2().reads(), "read2", rows, flags);
        table(directory.resolve("results.tsv"), List.of("read", "module", "qc_status", "input_filename"), rows);
        session.put("scope", "FastQC 0.13.0 per-file QC on validated unwrapped Phred+33 FASTQ. Default upstream modules and limits; no filtering, automatic read removal, or inferred suitability for a downstream assay.");
        session.put("threads", threads); session.put("java_heap_mb", memory); session.put("qc_findings", flags);
        if (!flags.isEmpty()) Files.writeString(directory.resolve("WARNINGS.md"), "# FASTQC QUALITY FINDINGS\n\n"
            + String.join("\n", flags.stream().map(flag -> "- " + flag).toList())
            + "\n\nRequired action: inspect the preserved FastQC HTML reports and assay/library context before downstream analysis. WARN/FAIL flags are QC findings, not an execution failure or automatic grounds to discard data. No reads were removed by FastQC.\n");
        return !flags.isEmpty();
    }

    private static boolean aggregate(Map<String, String> o, Path directory, Map<String, Object> manifest,
            Map<String, Object> session) throws Exception {
        String python = executable(required(o, "python"));
        probe(pythonCommand(python, "multiqc", "--version"), "multiqc, version 1.35", directory, "multiqc", session);
        pythonEnvironment(python, directory, session);
        Path reports = Path.of(required(o, "reports")).toAbsolutePath().normalize();
        DelimitedData data = DelimitedData.read(reports);
        if (!data.header().equals(List.of("sample_id", "fastqc_zip", "cutadapt_json")) || data.rows().size() > 10000)
            throw new IllegalArgumentException("Reports TSV requires sample_id, fastqc_zip, cutadapt_json; at most 10000 rows; one or both report paths per row");
        Path staging = Files.createDirectory(directory.resolve("staged-reports"));
        Set<String> identities = new HashSet<>(); Set<Path> paths = new HashSet<>();
        Map<String, Long> expectedQc = new LinkedHashMap<>(), expectedTrim = new LinkedHashMap<>();
        Map<String, String> rename = new LinkedHashMap<>();
        List<String[]> sources = new ArrayList<>();
        List<String[]> sourceQc = new ArrayList<>(); List<String> flags = new ArrayList<>();
        long size = 0;
        for (String[] row : data.rows()) {
            String id = row[0];
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}") || !identities.add(id.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("sample_id must be unique ignoring case and use 1..100 ASCII letters/digits/underscore/dot/hyphen");
            if (row[1].isBlank() && row[2].isBlank()) throw new IllegalArgumentException("No QC reports for " + id);
            for (int column = 1; column <= 2; column++) if (!row[column].isBlank()) {
                Path source = reports.getParent().resolve(row[column]).normalize().toRealPath();
                if (!paths.add(source)) throw new IllegalArgumentException("A QC report cannot be reused under multiple sample IDs");
                size = Math.addExact(size, Files.size(source));
                if (Files.size(source) > 64L * 1024 * 1024 || size > 2L * 1024 * 1024 * 1024) throw new IllegalArgumentException("Reports exceed 64 MiB per file or 2 GiB total staging bound");
                String name = id + (column == 1 ? "_fastqc.zip" : ".cutadapt.json");
                if (column == 1) {
                    expectedQc.put(id, totalSequences(source));
                    List<String> filenames = zipText(source, "fastqc_data.txt").lines().filter(line -> line.startsWith("Filename\t")).map(line -> line.substring(9)).toList();
                    if (filenames.size() != 1 || filenames.get(0).isBlank()) throw new IOException("FastQC source omitted a unique input filename");
                    summaryFindings(source, filenames.get(0), id, sourceQc, flags);
                }
                else {
                    Map<?, ?> report = jsonMap(source);
                    if (!"Cutadapt report".equals(report.get("tag"))) throw new IllegalArgumentException("Not a Cutadapt JSON report: " + source);
                    Map<?, ?> counts = map(report.get("read_counts"), "Cutadapt read_counts");
                    long input = count(counts.get("input")), retained = count(counts.get("output"));
                    if (retained > input) throw new IOException("Cutadapt report retained more reads/pairs than input");
                    expectedTrim.put(id, input);
                    String finding = input == 0 ? "Cutadapt source processed no input reads/pairs" : retained == 0 ? "Cutadapt retained no reads/pairs; all input was filtered" : null;
                    sourceQc.add(new String[]{id, "Cutadapt retained reads/pairs", finding == null ? "PASS" : "WARN", source.getFileName().toString()});
                    if (finding != null) flags.add(id + ": WARN - " + finding);
                }
                Files.copy(source, staging.resolve(name)); rename.put(name, id);
                sources.add(new String[]{id, column == 1 ? "fastqc" : "cutadapt", source.toString(), name, hash(source)});
            }
        }
        inputHashes(manifest, Map.of("reports", reports));
        table(directory.resolve("report-sources.tsv"), List.of("sample_id", "module", "source", "staged_name", "sha256"), sources);
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("sample_names_replace", rename); configuration.put("sample_names_replace_exact", true);
        configuration.put("sample_names_replace_regex", false); configuration.put("sample_names_replace_complete", true);
        configuration.put("fn_clean_sample_names", false); configuration.put("use_filename_as_sample_name", true);
        configuration.put("prepend_dirs", false); configuration.put("ignore_samples", List.of());
        configuration.put("ai_summary", false); configuration.put("ai_summary_full", false);
        Path config = directory.resolve("multiqc-config.yaml");
        Files.writeString(config, new org.yaml.snakeyaml.Yaml().dump(configuration));
        List<String> command = new ArrayList<>(pythonCommand(python, "multiqc"));
        command.addAll(List.of(staging.toString(), "--outdir", directory.toString(), "--filename", "multiqc_report.html",
            "--config", config.toString(), "--no-version-check", "--no-megaqc-upload", "--no-ai", "--no-ansi",
            "--interactive", "--data-format", "json", "--require-logs"));
        if (!expectedQc.isEmpty()) command.addAll(List.of("--module", "fastqc"));
        if (!expectedTrim.isEmpty()) command.addAll(List.of("--module", "cutadapt"));
        execute(command, directory, "multiqc.log", integer(o, "timeout", 3600, 1, 86400), session);
        if (!Files.isRegularFile(directory.resolve("multiqc_report.html"))) throw new IOException("MultiQC omitted its HTML report");
        List<String[]> rows = new ArrayList<>();
        Path exported = directory.resolve("multiqc_report_data/multiqc_data.json");
        Map<?, ?> raw = map(jsonMap(exported).get("report_saved_raw_data"), "MultiQC raw exported data");
        if (!expectedQc.isEmpty()) validateAggregated(map(raw.get("multiqc_fastqc"), "MultiQC FastQC data"), expectedQc, "fastqc", rows);
        if (!expectedTrim.isEmpty()) validateAggregated(map(raw.get("multiqc_cutadapt"), "MultiQC Cutadapt data"), expectedTrim, "cutadapt", rows);
        table(directory.resolve("results.tsv"), List.of("sample_id", "module", "input_reads_or_pairs"), rows);
        table(directory.resolve("source-qc.tsv"), List.of("sample_id", "module", "qc_status", "input_filename"), sourceQc);
        session.put("scope", "MultiQC 1.35 aggregation of explicitly supplied FastQC ZIP and Cutadapt JSON reports; original reports copied and hashed; exact sample mapping and source read totals verified. No recursive discovery outside staged inputs, AI summary, version check or MegaQC upload.");
        session.put("configuration_sha256", hash(config)); session.put("report_source_manifest_sha256", hash(directory.resolve("report-sources.tsv")));
        session.put("source_qc_findings", flags);
        if (!flags.isEmpty()) Files.writeString(directory.resolve("WARNINGS.md"), "# MULTIQC SOURCE QC FINDINGS\n\n"
            + String.join("\n", flags.stream().map(flag -> "- " + flag).toList())
            + "\n\nRequired action: inspect multiqc_report.html, source-qc.tsv and the preserved reports listed in report-sources.tsv, with assay/library context, before downstream analysis. Aggregation succeeded; upstream WARN/FAIL and empty trimming outputs remain findings, not an execution failure or automatic instruction to discard data. No reads were filtered by MultiQC.\n");
        return !flags.isEmpty();
    }

    private static void validateAggregated(Map<?, ?> parsed, Map<String, Long> expected, String module, List<String[]> rows) throws Exception {
        if (!parsed.keySet().equals(expected.keySet())) throw new IOException("MultiQC changed/missed sample identities for " + module + ": " + parsed.keySet());
        for (var entry : expected.entrySet()) {
            Map<?, ?> metrics = map(parsed.get(entry.getKey()), module + " sample");
            Object value = module.equals("fastqc") ? metrics.get("Total Sequences") : metrics.get("r_processed");
            if (value == null && module.equals("cutadapt")) value = metrics.get("pairs_processed");
            if (count(value) != entry.getValue()) throw new IOException("MultiQC source read count mismatch for " + entry.getKey() + "/" + module);
            rows.add(new String[]{entry.getKey(), module, entry.getValue().toString()});
        }
    }

    private static Path reads(Map<String, String> o, String key) throws IOException {
        Path file = Path.of(required(o, key)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".+\\.(fastq|fq)(\\.gz)?"))
            throw new IOException("Require a regular .fastq/.fq file, optionally .gz: " + file);
        return file;
    }
    private static String adapter(String value) {
        if (!value.matches("[ACGTRYSWKMBDHVNUacgtryswkmbdhvnu]{3,10000}")) throw new IllegalArgumentException("Regular 3-prime adapter must contain 3..10000 IUPAC DNA/RNA bases; linked/anchored syntax is not exposed");
        return value.toUpperCase(Locale.ROOT);
    }
    private static FastqIO.Inspection inspect(Path first, Path second, Map<String, Object> session, String prefix) throws IOException {
        FastqIO.Inspection stats = FastqIO.inspect(first, second);
        session.put(prefix + "_read1", statsMap(stats.read1()));
        if (stats.read2() != null) session.put(prefix + "_read2", statsMap(stats.read2()));
        return stats;
    }
    private static Map<String, Object> statsMap(FastqIO.Stats stats) { return Map.of("reads", stats.reads(), "bases", stats.bases(), "min_length", stats.minLength(), "max_length", stats.maxLength()); }
    private static List<String> pythonCommand(String python, String module, String... arguments) {
        List<String> command = new ArrayList<>(List.of(python, "-X", "utf8", "-I", "-m", module)); command.addAll(List.of(arguments)); return command;
    }
    private static void pythonEnvironment(String python, Path directory, Map<String, Object> session) throws Exception {
        String code = "import sys,json,importlib.metadata as m,pathlib,os,hashlib; "
            + "packages={d.metadata['Name']:d.version for d in m.distributions() if d.metadata['Name']}; "
            + "paths=[pathlib.Path.home()/'.multiqc_config.yaml',pathlib.Path(os.environ.get('XDG_CONFIG_HOME',str(pathlib.Path.home()/'.config')))/'multiqc_config.yaml',pathlib.Path.cwd()/'multiqc_config.yaml']; "
            + "paths += [pathlib.Path(m.distribution('multiqc').locate_file('multiqc'))/'multiqc_config.yaml'] if 'multiqc' in {k.lower() for k in packages} else []; "
            + "configs={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}; "
            + "print(json.dumps({'python':sys.version,'executable':sys.executable,'packages':dict(sorted(packages.items())), 'automatic_multiqc_configs_sha256':configs},indent=2))";
        execute(List.of(python, "-X", "utf8", "-I", "-c", code), directory, "python-environment.json", 60, session);
        session.put("python_environment_sha256", hash(directory.resolve("python-environment.json")));
        session.put("ignored_multiqc_environment_keys", System.getenv().keySet().stream().filter(name -> name.startsWith("MULTIQC_")).sorted().toList());
    }
    private static void probe(List<String> command, String expected, Path directory, String name, Map<String, Object> session) throws Exception {
        try {
            execute(command, directory, name + "-version.log", 60, session);
            String actual = Files.readString(directory.resolve(name + "-version.log")).trim();
            if (!actual.equals(expected)) throw new DependencyUnavailable("Require " + expected + "; version probe returned: " + actual);
            session.put(name + "_version", actual);
        } catch (IOException failure) { throw new DependencyUnavailable("Cannot execute pinned " + name + ": " + failure.getMessage()); }
    }
    private static void execute(List<String> command, Path directory, String log, int timeout, Map<String, Object> session) throws Exception {
        session.put(log + "_command", List.copyOf(command));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve(log).toFile());
        builder.environment().keySet().removeIf(name -> name.startsWith("MULTIQC_"));
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); throw new IOException("Upstream process exceeded " + timeout + " seconds; see " + log); }
            session.put(log + "_exit_code", process.exitValue());
            if (process.exitValue() != 0) throw new IOException("Upstream process failed with status " + process.exitValue() + "; inspect " + log);
        } catch (InterruptedException failure) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); Thread.currentThread().interrupt(); throw failure; }
    }
    private static void inputHashes(Map<String, Object> manifest, Map<String, Path> files) throws IOException {
        Map<String, Object> hashes = new LinkedHashMap<>();
        if (manifest.get("input_sha256") instanceof Map<?, ?> existing) existing.forEach((key, value) -> hashes.put(key.toString(), value));
        for (var entry : files.entrySet()) hashes.put(entry.getKey(), hash(entry.getValue()));
        manifest.put("input_sha256", hashes);
    }
    private static void status(Path directory, String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + state + "\",\"detail\":\"See session.yaml, upstream logs and WARNINGS.md when present\"}\n"); }
    private static String base(Path reads) { return reads.getFileName().toString().replaceFirst("(?i)\\.(fastq|fq)(\\.gz)?$", ""); }
    private static Map<?, ?> jsonMap(Path path) throws IOException { return map(SimpleJson.parse(Files.readString(path)), path.toString()); }
    private static Map<?, ?> map(Object value, String label) throws IOException { if (!(value instanceof Map<?, ?> result)) throw new IOException("Invalid object: " + label); return result; }
    private static long count(Object value) throws IOException {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < 0 || number.doubleValue() != number.longValue()) throw new IOException("Invalid upstream read count: " + value);
        return number.longValue();
    }
    private static long totalSequences(Path report) throws IOException {
        String text = zipText(report, "fastqc_data.txt");
        var match = java.util.regex.Pattern.compile("(?m)^Total Sequences\\t(\\d+)\\s*$").matcher(text);
        if (!match.find()) throw new IOException("FastQC ZIP omitted Total Sequences: " + report);
        return Long.parseLong(match.group(1));
    }
    private static String zipText(Path report, String suffix) throws IOException {
        try (ZipFile zip = new ZipFile(report.toFile())) {
            var entries = zip.stream().filter(e -> e.getName().endsWith("/" + suffix)).toList();
            if (entries.size() != 1 || entries.get(0).getSize() < 0 || entries.get(0).getSize() > 32L * 1024 * 1024) throw new IOException("Invalid FastQC ZIP report member: " + suffix);
            try (var in = zip.getInputStream(entries.get(0))) {
                byte[] bytes = in.readNBytes(32 * 1024 * 1024 + 1);
                if (bytes.length > 32 * 1024 * 1024) throw new IOException("FastQC report member exceeds bound");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }
    static void qcReport(Path directory, Path reads, long expected, String label, List<String[]> rows, List<String> flags) throws IOException {
        List<Path> candidates;
        try (var files = Files.list(directory)) { candidates = files.filter(p -> p.getFileName().toString().endsWith("_fastqc.zip")).toList(); }
        Path report = null;
        for (Path candidate : candidates) if (zipText(candidate, "fastqc_data.txt").lines().anyMatch(line -> line.equals("Filename\t" + reads.getFileName()))) {
            if (report != null) throw new IOException("FastQC produced multiple reports for one input filename");
            report = candidate;
        }
        if (report == null || !Files.isRegularFile(report.resolveSibling(report.getFileName().toString().replaceFirst("\\.zip$", ".html"))) || totalSequences(report) != expected) throw new IOException("FastQC report missing or read count mismatch");
        String data = zipText(report, "fastqc_data.txt");
        if (!data.startsWith("##FastQC\t0.13.0")) throw new IOException("FastQC report version mismatch");
        if (!data.lines().anyMatch(line -> line.equals("Filename\t" + reads.getFileName()))) throw new IOException("FastQC report changed input filename");
        summaryFindings(report, reads.getFileName().toString(), label, rows, flags);
    }
    private static void summaryFindings(Path report, String filename, String label, List<String[]> rows, List<String> flags) throws IOException {
        int n = 0; Set<String> modules = new HashSet<>();
        for (String line : zipText(report, "summary.txt").lines().toList()) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 3 || !Set.of("PASS", "WARN", "FAIL").contains(fields[0]) || !fields[2].equals(filename)
                    || fields[1].isBlank() || !modules.add(fields[1])) throw new IOException("Invalid/duplicate FastQC module status/filename");
            rows.add(new String[]{label, fields[1], fields[0], fields[2]});
            if (!fields[0].equals("PASS")) flags.add(label + ": " + fields[0] + " - " + fields[1]);
            n++;
        }
        if (n == 0) throw new IOException("FastQC omitted module summaries");
    }
    private static void quarantine(Path directory) throws IOException {
        List<Path> outputs;
        try (var paths = Files.list(directory)) {
            outputs = paths.filter(path -> { String name = path.getFileName().toString(); return name.equals("results.tsv") || name.startsWith("trimmed_") || name.endsWith("_fastqc.zip") || name.endsWith("_fastqc.html") || name.equals("multiqc_report_data") || name.equals("multiqc_report.html"); }).toList();
        }
        if (!outputs.isEmpty()) {
            Path partial = Files.createDirectory(directory.resolve("partial"));
            for (Path source : outputs) {
                Path target = partial.resolve(source.getFileName()).normalize();
                if (!source.toAbsolutePath().normalize().startsWith(directory.toAbsolutePath().normalize()) || !target.toAbsolutePath().normalize().startsWith(directory.toAbsolutePath().normalize())) throw new IOException("Unsafe partial artifact path");
                Files.move(source, target);
            }
        }
    }
    private static String recovery(String method) { return switch (method) {
        case "fastqc" -> "Where permitted run python tools/literature/install_fastqc.py --allow-download, then supply --fastqc-home build/omics-fastqc/FastQC and a working Java runtime. Use the compiled FastQC 0.13.0 release and its dependency JARs.";
        default -> "Where permitted run python tools/literature/install_cell_dependencies.py --profile sequence --allow-install, then supply its private Python with --python (Cutadapt 5.2 / MultiQC 1.35). If installation is forbidden/unavailable, leave this analysis skipped and use an approved supported environment.";
    }; }
    private static final class DependencyUnavailable extends Exception {
        private static final long serialVersionUID = 1L;
        DependencyUnavailable(String message) { super(message); }
    }
    static String help() { return """
        sequence-qc --method trim-fixed|cutadapt|fastqc|multiqc --out NEW_DIR
        trim-fixed --reads INPUT.fastq[.gz] [--reads2 MATE.fastq[.gz]]
          [--first-base 1 --last-base 0 --minimum-length 1]. Native Java; no Python.
          One-based inclusive original-read positions; last-base 0 keeps through end.
          Run separately after adapter removal for post-adapter fixed clipping.
          Discard short/absent spans; paired reads are retained/filtered together.
        cutadapt --python PYTHON --reads R1.fastq[.gz] [--reads2 R2.fastq[.gz]]
          --adapter3 IUPAC [--adapter3-2 IUPAC for paired reads]
          [--minimum-length 0 --quality-cutoff 0 --overlap 3 --error-rate 0.1 --cores 1 --cut-left 0]
          Pinned Cutadapt 5.2; regular 3-prime adapters, quality trimming at 3-prime end, optional
          fixed 5-prime removal on both mates. Pair discarded if either fails minimum length.
        fastqc --fastqc-home COMPILED_RELEASE_DIR --reads R1.fastq[.gz] [--reads2 R2.fastq[.gz]]
          [--java JAVA --threads 1 --memory-mb 512]. FastQC 0.13.0, Java entry point (no Python).
          QC PASS/WARN/FAIL findings are preserved, with prominent warnings, not automatic filtering.
        multiqc --python PYTHON --reports reports.tsv
          TSV columns: sample_id, fastqc_zip, cutadapt_json. One or both paths per sample;
          paths relative to TSV or absolute. Distinct sample IDs ignoring case; ASCII letters,
          digits, underscore, dot or hyphen. Explicitly staged reports only (<=64 MiB each,
          <=2 GiB total, <=10000 samples); exact sample names/read counts checked after aggregation.
          MultiQC 1.35; AI summaries, version checks and MegaQC upload disabled.
        FASTQ contract: unwrapped four-line IUPAC DNA/RNA, Phred+33, <=10 million bases/read;
          paired identities/order must match after stripping /1 or /2. No encoding inference.
        Outputs preserve commands, effective settings, dependency versions, hashes and upstream logs.
        Missing/incompatible dependencies: status 3, skipped analysis and WARNINGS.md with recovery.
        Other failures: status 2 and retained diagnostics. Partial results are quarantined.
        No implicit installation. See tools/literature/install_cell_dependencies.py --profile sequence
          and install_fastqc.py; use explicit allow flags only where installation is permitted.
        """; }
}
