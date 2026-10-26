/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordCoordinateComparator;
import htsjdk.samtools.SAMRecordQueryNameComparator;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed local SAM/BAM operations using the hash-pinned official Picard application. */
final class AlignmentToolsCli {
    private AlignmentToolsCli() { }
    static final String VERSION = "3.5.0";
    static final String JAR_SHA256 = "b7d97861c3a54ba5a421f5a317f38382f955803862d30ef4aca2bcdc54943631";
    private static final Set<String> COMMON = Set.of("method", "input", "picard", "java", "out", "timeout", "memory-mb", "records-in-ram");
    private static final Map<String, Set<String>> SPECIFIC = Map.of(
        "sort", Set.of("sort-order", "create-index"), "mark-duplicates", Set.of("remove-duplicates", "create-index", "optical-distance"),
        "validate", Set.of(), "alignment-summary", Set.of("reference"));

    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (Arrays.asList(arguments).contains("--help")) { output.println(help()); return 0; }
        try {
            var allowed = new java.util.HashSet<>(COMMON); SPECIFIC.values().forEach(allowed::addAll);
            Map<String, String> o = options(arguments, allowed);
            String method = required(o, "method");
            if (!SPECIFIC.containsKey(method)) throw new IllegalArgumentException("Unknown alignment method: " + method);
            for (String key : o.keySet()) if (!COMMON.contains(key) && !SPECIFIC.get(method).contains(key))
                throw new IllegalArgumentException("--" + key + " does not apply to " + method);
            int timeout = integer(o, "timeout", 3600, 1, 86400);
            int memory = integer(o, "memory-mb", 2048, 128, 65536);
            int records = integer(o, "records-in-ram", 100000, 1000, 1000000);
            String sort = o.getOrDefault("sort-order", "coordinate");
            if (!Set.of("coordinate", "queryname").contains(sort)) throw new IllegalArgumentException("sort-order must be coordinate or queryname");
            boolean index = bool(o, "create-index", false), remove = bool(o, "remove-duplicates", false);
            if (index && method.equals("sort") && !sort.equals("coordinate")) throw new IllegalArgumentException("Index creation requires coordinate sort");
            int optical = integer(o, "optical-distance", 100, 0, 1000000);
            required(o, "input"); required(o, "picard");
            if (method.equals("alignment-summary")) required(o, "reference");
            int[] exit = {0};
            transaction(o, "alignment-tools-" + method, (directory, manifest) -> {
                Map<String, Object> session = new LinkedHashMap<>();
                session.put("method", method); session.put("picard_version", VERSION);
                session.put("scope", "Local SAM/BAM only; strict validation, explicit operations; no CRAM, implicit reference download or arbitrary Picard arguments");
                try {
                    Path input = localFile(o.get("input"), "Input");
                    String name = input.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".sam") && !name.endsWith(".bam")) throw new IOException("Input must be local .sam or .bam; CRAM requires a separate reference contract");
                    String inputHash = hash(input); manifest.put("alignment_input_sha256", inputHash);
                    Path jar = Path.of(o.get("picard")).toAbsolutePath().normalize();
                    if (!Files.isRegularFile(jar)) throw new DependencyUnavailable("Picard JAR is unavailable: " + jar);
                    String jarHash = hash(jar); manifest.put("picard_jar_sha256", jarHash);
                    if (!jarHash.equals(JAR_SHA256)) throw new DependencyUnavailable("Picard JAR differs from the validated official " + VERSION + " release SHA-256");
                    String java = executable(o.getOrDefault("java", Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString()));
                    List<String> base = List.of(java, "-Xmx" + memory + "m", "-Djava.io.tmpdir=" + directory.resolve("tmp"), "-jar", jar.toString());
                    Files.createDirectory(directory.resolve("tmp"));
                    var version = new ArrayList<>(base); version.add("SortSam"); version.add("--version");
                    try {
                        // Barclay's informational --version path returns 1 for this release.
                        int versionExit = execute(version, directory, "version.log", 60, session);
                        if ((versionExit != 0 && versionExit != 1)
                                || !Files.readString(directory.resolve("version.log")).strip().equals("Version:" + VERSION))
                            throw new IOException("The Java runtime could not load pinned Picard " + VERSION);
                    } catch (IOException failure) { throw new DependencyUnavailable("Picard needs a working Java 17+ runtime: " + failure.getMessage()); }
                    session.put("java_runtime", java); session.put("memory_mb", memory);
                    var command = new ArrayList<>(base);
                    command.add(switch (method) { case "sort" -> "SortSam"; case "mark-duplicates" -> "MarkDuplicates";
                        case "validate" -> "ValidateSamFile"; default -> "CollectAlignmentSummaryMetrics"; });
                    command.add("INPUT=" + input); command.add("TMP_DIR=" + directory.resolve("tmp"));
                    command.add("USE_JDK_DEFLATER=true"); command.add("USE_JDK_INFLATER=true");
                    Path result = directory.resolve(method.equals("validate") ? "validation.txt" : method.equals("alignment-summary") ? "alignment_metrics.txt" : "alignment.bam");
                    command.add("OUTPUT=" + result);
                    Counts before = null;
                    if (method.equals("validate")) {
                        command.add("MODE=SUMMARY"); command.add("IGNORE_WARNINGS=false");
                        command.add("SKIP_MATE_VALIDATION=false"); command.add("INDEX_VALIDATION_STRINGENCY=EXHAUSTIVE");
                    } else {
                        before = inspect(input, false); session.put("input_counts", before.map());
                        command.add("VALIDATION_STRINGENCY=STRICT"); command.add("MAX_RECORDS_IN_RAM=" + records);
                        if (method.equals("sort")) command.add("SORT_ORDER=" + sort);
                        if (method.equals("sort") || method.equals("mark-duplicates")) command.add("CREATE_INDEX=" + index);
                        if (method.equals("mark-duplicates")) {
                            command.add("METRICS_FILE=" + directory.resolve("duplication_metrics.txt"));
                            command.add("REMOVE_DUPLICATES=" + remove); command.add("OPTICAL_DUPLICATE_PIXEL_DISTANCE=" + optical);
                            command.add("ASSUME_SORT_ORDER=coordinate");
                            // Verify actual order before making this assertion to Picard.
                            inspect(input, true);
                        }
                        if (method.equals("alignment-summary")) {
                            Path reference = localFile(o.get("reference"), "Reference FASTA");
                            manifest.put("reference_sha256", hash(reference)); command.add("REFERENCE_SEQUENCE=" + reference);
                            for (Path sidecar : List.of(Path.of(reference + ".fai"), dictionary(reference))) {
                                if (!Files.isRegularFile(sidecar)) throw new IOException("Reference requires local .fai and .dict sidecars: " + sidecar);
                                manifest.put(sidecar.toString() + "_sha256", hash(sidecar));
                            }
                            org.jlinalg.sequence.FastaIndexes.validate(reference, Path.of(reference + ".fai"));
                        }
                    }
                    int upstream = execute(command, directory, "external.log", timeout, session);
                    if (method.equals("validate")) {
                        if (upstream < 0 || upstream > 3 || !Files.isRegularFile(result)) throw new IOException("ValidateSamFile failed to complete; exit " + upstream);
                        String report = Files.readString(result);
                        if (!(report.contains("No errors found") || report.contains("## HISTOGRAM"))) throw new IOException("ValidateSamFile omitted its summary protocol");
                        session.put("validation_return_code", upstream);
                        if (upstream != 0) {
                            boolean hasErrors = upstream >= 2;
                            exit[0] = hasErrors ? 4 : 0;
                            String state = hasErrors ? "completed-with-validation-errors" : "completed-with-validation-warnings";
                            manifest.put("analysis_status", state);
                            Files.writeString(directory.resolve("WARNINGS.md"), "# " + (hasErrors ? "ALIGNMENT VALIDATION ERRORS" : "ALIGNMENT VALIDATION WARNINGS")
                                + "\n\nPicard completed validation and reported findings (upstream status " + upstream + ").\n\nRequired action: read validation.txt and correct its named SAM/BAM errors or warnings before downstream analysis. Rerun validation to a new output directory. CLI status 4 means reported validation errors; it is distinct from execution failure (2) and unavailable dependency (3).\n");
                        }
                    } else {
                        if (upstream != 0) throw new IOException("Picard execution failed with status " + upstream + "; inspect external.log");
                        if (!Files.isRegularFile(result) || Files.size(result) == 0) throw new IOException("Picard omitted its output");
                        if (method.equals("sort") || method.equals("mark-duplicates")) {
                            Counts after = inspect(result, method.equals("mark-duplicates") || sort.equals("coordinate"));
                            if (method.equals("sort") && sort.equals("queryname")) verifyQuerynameOrder(result);
                            session.put("output_counts", after.map());
                            if ((!remove && before.total != after.total) || after.total > before.total || (remove && after.duplicates != 0))
                                throw new IOException("Picard changed record counts outside the requested duplicate removal");
                            if (method.equals("mark-duplicates")) verifyMetrics(directory.resolve("duplication_metrics.txt"), "UNPAIRED_READ_DUPLICATES");
                            if (index && !Files.isRegularFile(directory.resolve("alignment.bai"))) throw new IOException("Picard omitted the requested BAM index");
                        } else verifyMetrics(result, "PF_READS_ALIGNED");
                    }
                    if (!hash(input).equals(inputHash)) throw new IOException("Input changed during the analysis; results cannot be trusted");
                    List<String> upstreamWarnings;
                    try (var lines = Files.lines(directory.resolve("external.log"))) {
                        upstreamWarnings = lines.filter(line -> line.matches(".*\\bWARN(?:ING)?\\b.*")).limit(100).toList();
                    }
                    if (!upstreamWarnings.isEmpty()) {
                        session.put("upstream_warnings_first_100", upstreamWarnings);
                        if (!manifest.containsKey("analysis_status")) manifest.put("analysis_status", "completed-with-upstream-warnings");
                        Files.writeString(directory.resolve("WARNINGS.md"), "\n# UPSTREAM PICARD WARNINGS\n\n"
                            + String.join("\n", upstreamWarnings) + "\n\nRequired action: inspect all warnings in external.log and the resulting metrics before downstream use. For optical-name warnings, confirm the instrument's read naming and pixel-distance setting; library duplicate marking and optical duplicate counts have different requirements.\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }
                    if (!manifest.containsKey("analysis_status")) manifest.put("analysis_status", "completed");
                    table(directory.resolve("results.tsv"), List.of("metric", "value"), List.<String[]>of(
                        new String[]{"method", method}, new String[]{"picard_version", VERSION}, new String[]{"upstream_exit_code", Integer.toString(upstream)}));
                } catch (Exception failure) {
                    exit[0] = failure instanceof DependencyUnavailable ? 3 : 2;
                    manifest.put("analysis_status", exit[0] == 3 ? "skipped-dependency-unavailable" : "failed");
                    manifest.put("error", String.valueOf(failure.getMessage()));
                    quarantine(directory);
                    Files.writeString(directory.resolve("WARNINGS.md"), "# " + (exit[0] == 3 ? "ANALYSIS SKIPPED - PICARD UNAVAILABLE" : "ANALYSIS FAILED")
                        + "\n\n" + failure.getMessage() + "\n\nRequired action: " + (exit[0] == 3
                            ? "Where downloads are allowed, run python tools/literature/install_picard.py --allow-download; supply its picard.jar with --picard and a Java 17+ runtime with --java. If installation is forbidden, skip this analysis until an administrator provides the validated runtime."
                            : "Inspect external.log and the documented SAM/BAM, sort and reference contracts. Correct the cause and rerun to a new output directory.")
                        + " Partial outputs are retained under partial/. No successful results.tsv is published.\n");
                }
                Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + manifest.get("analysis_status") + "\"}\n");
                Files.writeString(directory.resolve("session.yaml"), new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if (exit[0] == 2 || exit[0] == 3) error.println((exit[0] == 3 ? "SKIPPED ANALYSIS" : "ANALYSIS FAILED") + ": see " + Path.of(o.get("out")).resolve("WARNINGS.md") + " for the cause and required action.");
            else {
                output.println("Alignment operation completed: " + o.get("out"));
                if (Files.exists(Path.of(o.get("out"), "WARNINGS.md"))) error.println("ALIGNMENT VALIDATION FINDINGS: see " + Path.of(o.get("out"), "WARNINGS.md"));
            }
            return exit[0];
        } catch (Exception failure) { error.println("jlinalg: " + failure.getMessage()); return 2; }
    }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"); }
    private static Path localFile(String value, String label) throws IOException {
        Path file = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) throw new IOException(label + " does not exist: " + file);
        return file;
    }
    private static Path dictionary(Path fasta) {
        String name = fasta.getFileName().toString(); int dot = name.lastIndexOf('.');
        return fasta.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".dict");
    }
    private record Counts(long total, long primary, long duplicates, long mapped) {
        Map<String, Long> map() { return Map.of("records", total, "primary", primary, "duplicate_flags", duplicates, "mapped", mapped); }
    }
    private static Counts inspect(Path file, boolean coordinate) throws IOException {
        long total = 0, primary = 0, duplicate = 0, mapped = 0;
        try (var reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(file)) {
            if (coordinate && reader.getFileHeader().getSortOrder() != SAMFileHeader.SortOrder.coordinate)
                throw new IOException("Coordinate-sorted header is required");
            SAMRecord previous = null; var comparator = new SAMRecordCoordinateComparator();
            for (SAMRecord record : reader) {
                if (coordinate && previous != null && comparator.fileOrderCompare(previous, record) > 0) throw new IOException("Records are not actually coordinate sorted");
                previous = record; total++;
                if (!record.isSecondaryOrSupplementary()) primary++;
                if (record.getDuplicateReadFlag()) duplicate++;
                if (!record.getReadUnmappedFlag()) mapped++;
            }
        }
        return new Counts(total, primary, duplicate, mapped);
    }
    private static void verifyMetrics(Path file, String column) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 64 * 1024 * 1024) throw new IOException("Missing or excessive metrics output");
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i + 1 < lines.size(); i++) {
            List<String> columns = Arrays.asList(lines.get(i).split("\t", -1));
            if (columns.contains(column) && lines.get(i + 1).split("\t", -1).length == columns.size()) return;
        }
        throw new IOException("Picard metrics omit the expected table: " + column);
    }
    private static void verifyQuerynameOrder(Path file) throws IOException {
        try (var reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(file)) {
            if (reader.getFileHeader().getSortOrder() != SAMFileHeader.SortOrder.queryname) throw new IOException("Queryname sort header omitted");
            SAMRecord previous = null; var comparator = new SAMRecordQueryNameComparator();
            for (SAMRecord record : reader) {
                if (previous != null && comparator.fileOrderCompare(previous, record) > 0) throw new IOException("Records are not actually queryname sorted");
                previous = record;
            }
        }
    }
    private static int execute(List<String> command, Path directory, String log, int timeout, Map<String, Object> session) throws Exception {
        session.put(log + "_command", List.copyOf(command));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve(log).toFile());
        List<String> suppressed = List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS").stream().filter(builder.environment()::containsKey).toList();
        suppressed.forEach(builder.environment()::remove); session.put("suppressed_java_environment_keys", suppressed);
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); throw new IOException("Picard exceeded timeout; inspect " + log); }
            session.put(log + "_exit_code", process.exitValue()); return process.exitValue();
        } catch (InterruptedException failure) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); Thread.currentThread().interrupt(); throw failure; }
    }
    private static void quarantine(Path directory) throws IOException {
        for (String name : List.of("results.tsv", "alignment.bam", "alignment.bai", "duplication_metrics.txt", "alignment_metrics.txt", "validation.txt")) {
            Path file = directory.resolve(name);
            if (Files.exists(file)) { Files.createDirectories(directory.resolve("partial")); Files.move(file, directory.resolve("partial").resolve(name)); }
        }
    }
    private static final class DependencyUnavailable extends IOException { private static final long serialVersionUID=1L; DependencyUnavailable(String message) { super(message); } }
    static String help() { return """
        alignment-tools --method sort|mark-duplicates|validate|alignment-summary
          --input LOCAL.sam|LOCAL.bam --picard PICARD.jar --out NEW_DIR
          [--java JAVA17_PLUS --memory-mb 2048 --records-in-ram 100000 --timeout 3600]
        sort: [--sort-order coordinate|queryname --create-index false]
        mark-duplicates: actual coordinate-sorted input required; marks duplicates by default.
          [--remove-duplicates false --create-index false --optical-distance 100]
          Optical distance depends on instrument geometry; explicitly choose 2500 for relevant patterned flowcells.
          Upstream default read-name parsing is used; inspect logs/metrics for optical-name warnings.
        validate: full mate/index checks, SUMMARY mode, warnings retained. CLI 4 means validation
          found errors; CLI 0 can include validation warnings. Read validation.txt and WARNINGS.md.
        alignment-summary: --reference LOCAL.fa with matching LOCAL.fa.fai and LOCAL.dict required.
        Official Picard 3.5.0 JAR and SHA-256 are pinned. No implicit installation or reference download.
        Setup where permitted: python tools/literature/install_picard.py --allow-download
          then --picard build/omics-picard/picard.jar. Missing/incorrect dependency: skip status 3;
          other failure: 2, actionable WARNINGS.md, quarantined partial outputs, no results.tsv.
        SAM/BAM only; no CRAM, RNA metrics, read-group changes or whole-Picard parity claim.
        """; }
}
