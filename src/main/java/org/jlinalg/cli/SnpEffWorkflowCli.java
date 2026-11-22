/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jlinalg.genetics.SnpEffAnnotations;
import static org.jlinalg.cli.SnpEffWorkflowFiles.*;

/** Captured original SnpEff annotation, independent format audits and genuine replay. */
public final class SnpEffWorkflowCli {
    static final String NOTICE = """
        # Warnings and required actions

        SOURCE-BUILD PROFILE: catalog qualification is separate from command success.
        5.2 executes the checksum-pinned official upstream Java archive on literal
        DNA SNP/MNP/small-indel VCF. 3.1i-r354-source executes unmodified historical
        r354 sources identifying themselves as 3.1i (build 2012-12-12), compiled
        with javac21.0.4 to Java8 bytecode and selected external dependency classes
        from the official 3.1m archive. All SnpEff and bundled Flanagan classes
        were excluded from that dependency container and rebuilt from r354.
        This selected source build is NOT the paper authors' original binary;
        their SVN revision and binary checksum remain unknown. Never use 3.1m
        as a replacement for 3.1i. Legacy bundled Flanagan code has noncommercial
        terms and redistribution restrictions. This profile is for private
        academic noncommercial use; do not redistribute its runtime/evidence
        containing that runtime without checking all applicable permissions.

        Supply the exact permitted database and complete data directory. Genome
        builds, hg38kg, Ensembl releases and transcript sets are NOT interchangeable.
        Raw original config is retained. The effective config changes ONLY data_dir
        (legacy) or data.dir (modern) to the captured data directory; all other
        parsed properties remain unchanged. Original defaults remain original
        defaults; no paper parameters, ancestry labels or deleteriousness rules
        are inferred. up_down_stream is explicit. Original -noStats disables only
        summary reports, and -noLog disables usage reporting. Modern -noDownload
        prevents implicit downloads; legacy has no automatic annotation download.

        Every original ANN transcript/allele remains separate. MODERATE is not a
        deleterious-missense classification. Original5.2 reserializes QUAL through
        its parsed Java Double (for example60 becomes60.0); the native audit
        checks that exact representation. Both raw files remain unchanged.
        No most-severe transcript policy,
        pathogenicity score, gene-burden membership or whole-paper result is made.
        Legacy TXT is a distinct 24-column representation. Its original chromosome
        normalization, effect names, final Custom_interval_ID and misspelled
        Trancript_ID header remain unchanged. Native legacy checks cover format
        and diagnostics, not independent input/output consequence classification.

        Runtime, full reference directory, inputs, original/effective configs and
        consoles are retained. VERIFY checks exact directory membership, hashes
        and independently regenerated native tables; it does NOT execute SnpEff.
        REPLAY executes the captured original engine in a NEW directory and
        compares complete raw annotation, table and diagnostic bytes. Integrity
        is not authenticity or an independent biological validation. Directory
        bundles only; no archive is silently extracted or repaired.

        Runtime uses the current Java executable with explicit UTF-8/LF, UTC and
        English locale. heap_mb sets only the child JVM heap; native/JVM overhead
        are separate. Java option/classpath injection environment variables are
        removed from the original child process and that policy is recorded.
        The parent Java audit is separate. wall_ms bounds the child stage.
        max_bytes/max_files bound captured/final workspace inventories, not an OS
        disk quota. Annotation audit lines are limited to16 MiB characters. Do not
        truncate variants or reference models to fit; use a separately qualified
        profile when a limit is insufficient. This is not a filesystem/network sandbox.

        SKIP3: named required software/reference is missing or execution is
        forbidden; skip dependent analysis, supply the exact permitted prerequisite
        and retry NEW output. FAIL2: invalid input, contradiction, original error,
        timeout, audit or recording failure; admit no complete result. Read
        FAILURE.txt, WARNINGS.md, diagnostics.tsv and original.stderr. All partials
        and failed attempts remain. Original annotation ERROR tokens prevent
        completion. Nonempty original diagnostics are reported without replacing
        their meaning. Require COMPLETED.properties and successful VERIFY; use
        REPLAY for an actual original-engine repeat. The full250-paper goal remains open.
        """;
    private SnpEffWorkflowCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    /** Return 0, SKIP3 or FAIL2; retain failed output once it has been created. */
    public static int run(String[] args, PrintStream output, PrintStream error) {
        Path out = null;
        boolean owned = false;
        try {
            if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
                output.println("snpeff-tools run --request FILE --out NEW_DIRECTORY\nsnpeff-tools verify --bundle DIRECTORY --out NEW_DIRECTORY\nsnpeff-tools replay --bundle DIRECTORY --out NEW_DIRECTORY\n" + NOTICE);
                return 0;
            }
            if (args.length != 5 || !args[3].equals("--out")) throw new IllegalArgumentException("Use snpeff-tools --help");
            String action = args[0]; Path source = Path.of(args[2]);
            if (action.equals("run") && args[1].equals("--request")) {
                // Resolve and reject overlap before creating anything.
                Config cfg = config(read(source, true));
                List<Path> inputs = new ArrayList<>(); inputs.add(source.toRealPath());
                for (String key : List.of("jar", "config", "data", "variants")) inputs.add(input(source, cfg.get(key), key.equals("data"), key.equals("jar") || key.equals("data")));
                out = destination(Path.of(args[4]), inputs);
                Files.createDirectories(out.getParent()); Files.createDirectory(out); owned = true;
                execute(source, cfg, out, null, output); return 0;
            }
            if ((action.equals("verify") || action.equals("replay")) && args[1].equals("--bundle")) {
                Path bundle = source.toRealPath();
                out = destination(Path.of(args[4]), List.of(bundle));
                validate(bundle);
                Files.createDirectories(out.getParent()); Files.createDirectory(out); owned = true;
                if (action.equals("verify")) {
                    Files.writeString(out.resolve("WARNINGS.md"), NOTICE, StandardCharsets.UTF_8);
                    Config cfg = config(read(bundle.resolve("request.properties"), true));
                    SnpEffAnnotations.Report report = audit(bundle, cfg, out.resolve("annotations.tsv"), out.resolve("diagnostics.tsv"));
                    equivalent(bundle, out, List.of("annotations.tsv", "diagnostics.tsv"));
                    validateReport(bundle, report);
                    SnpEffWorkflowDiagnostics.write(out, report, NOTICE);
                    if (Files.isRegularFile(bundle.resolve("SOURCE-DIAGNOSTICS.tsv"))) equivalent(bundle, out, List.of("SOURCE-DIAGNOSTICS.tsv"));
                    Properties verified = new Properties(); verified.setProperty("status", "PASS_PORTABLE_INTEGRITY_AND_NATIVE_FORMAT_NO_ENGINE_EXECUTION");
                    verified.setProperty("source_manifest_sha256", pin(bundle.resolve("manifest.properties")).sha256());
                    verified.setProperty("annotation_rows", Long.toString(report.annotationRows()));
                    verified.setProperty("diagnostic_rows", Long.toString(report.diagnosticRows()));
                    write(out.resolve("VERIFIED.properties"), verified);
                    output.println("PASS SnpEff directory integrity and native format audit; original engine was not executed");
                } else {
                    Config cfg = config(read(bundle.resolve("request.properties"), true));
                    SnpEffAnnotations.Report report = audit(bundle, cfg, out.resolve("parent-audit.tsv"), out.resolve("parent-diagnostics.tsv"));
                    if (!pin(out.resolve("parent-audit.tsv")).equals(pin(bundle.resolve("annotations.tsv"))) || !pin(out.resolve("parent-diagnostics.tsv")).equals(pin(bundle.resolve("diagnostics.tsv")))) throw new IOException("Parent native audit differs from its stored tables");
                    validateReport(bundle, report);
                    execute(bundle.resolve("request.properties"), cfg, out, bundle, output);
                }
                return 0;
            }
            throw new IllegalArgumentException("Use snpeff-tools --help");
        } catch (Exception failure) {
            int status = failure instanceof Prerequisite || failure instanceof java.nio.file.AccessDeniedException ? 3 : 2;
            String message = (status == 3 ? "SKIP3: dependent analysis skipped. " : "FAIL2: no complete result admitted. ") + failure.getMessage()
                + "\nRetain original diagnostics and resolve the named cause; retry a NEW output directory.\n";
            if (owned && out != null && Files.isDirectory(out)) {
                try {
                    Files.deleteIfExists(out.resolve("COMPLETED.properties")); Files.deleteIfExists(out.resolve("VERIFIED.properties"));
                    Files.writeString(out.resolve("FAILURE.txt"), message + failure + "\n", StandardCharsets.UTF_8);
                    Path warnings = out.resolve("WARNINGS.md");
                    String existing = Files.isRegularFile(warnings) ? Files.readString(warnings, StandardCharsets.UTF_8) : NOTICE;
                    Files.writeString(warnings, "# Analysis failed or was skipped\n\n" + message + "\n" + existing, StandardCharsets.UTF_8);
                } catch (IOException recording) { message += "ERROR retaining failure: " + recording + "\n"; }
            }
            error.print(message);
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return status;
        }
    }

    private static void execute(Path request, Config cfg, Path out, Path parent, PrintStream output) throws IOException, InterruptedException {
        Path jar = input(request, cfg.get("jar"), false, true), data = input(request, cfg.get("data"), true, true);
        Path originalConfig = input(request, cfg.get("config"), false, false), variants = input(request, cfg.get("variants"), false, false);
        Files.writeString(out.resolve("WARNINGS.md"), NOTICE, StandardCharsets.UTF_8);
        if (!pin(jar).sha256().equals(JARS.get(cfg.get("edition")))) throw new IOException("Runtime SHA-256 contradicts selected edition; never substitute or refresh its pin");
        Path predictor = data.resolve(cfg.get("genome")).resolve("snpEffectPredictor.bin");
        if (!Files.isRegularFile(predictor)) throw new Prerequisite("Missing exact " + cfg.get("genome") + "/snpEffectPredictor.bin; obtain/build the permitted reference for this exact edition, assembly and transcript release");
        Map<String,Pin> reference = inventory(data, cfg.number("max_bytes"), cfg.number("max_files"));
        Files.createDirectories(out.resolve("inputs")); Files.createDirectory(out.resolve("runtime"));
        copy(jar, out.resolve("runtime/snpeff.jar")); copy(request, out.resolve("original-request.properties"));
        copy(originalConfig, out.resolve("inputs/original.config"));
        copy(variants, out.resolve("inputs/variants." + cfg.get("format")));
        copyTree(data, out.resolve("inputs/data"), reference);
        if (!reference.equals(inventory(data, cfg.number("max_bytes"), cfg.number("max_files")))) throw new IOException("Original reference changed during capture");
        Properties effective = read(out.resolve("inputs/original.config"), false);
        if (effective.getProperty(cfg.get("genome") + ".genome", "").isBlank()) throw new IOException("Configuration lacks the exact selected genome.genome declaration");
        effective.setProperty(cfg.legacy() ? "data_dir" : "data.dir", "data");
        write(out.resolve("inputs/effective.config"), effective);
        Properties captured = new Properties(); captured.putAll(cfg.properties());
        captured.setProperty("jar", "runtime/snpeff.jar"); captured.setProperty("config", "inputs/original.config"); captured.setProperty("data", "inputs/data"); captured.setProperty("variants", "inputs/variants." + cfg.get("format"));
        write(out.resolve("request.properties"), captured);
        if (parent != null) {
            Properties lineage = new Properties(); lineage.setProperty("parent_manifest_sha256", pin(parent.resolve("manifest.properties")).sha256());
            for (String name : List.of("original.stdout", "annotations.tsv", "diagnostics.tsv")) lineage.setProperty(name + ".expected_sha256", pin(parent.resolve(name)).sha256());
            write(out.resolve("PARENT.properties"), lineage);
        }
        Properties host = new Properties();
        for (String key : List.of("java.version", "java.runtime.version", "java.vendor", "os.name", "os.arch")) host.setProperty(key, System.getProperty(key));
        write(out.resolve("runtime/java.properties"), host);
        Map<String,Pin> sealedInputs = inventory(out.resolve("inputs"), cfg.number("max_bytes"), cfg.number("max_files"));
        inventory(out, cfg.number("max_bytes"), cfg.number("max_files"));
        process(out, cfg);
        if (!sealedInputs.equals(inventory(out.resolve("inputs"), cfg.number("max_bytes"), cfg.number("max_files")))) throw new IOException("Original engine changed captured scientific inputs/config/reference");
        SnpEffAnnotations.Report report = audit(out, cfg, out.resolve("annotations.tsv"), out.resolve("diagnostics.tsv"));
        SnpEffWorkflowDiagnostics.write(out, report, NOTICE);
        if (report.errorRows() != 0) throw new IOException("Original annotation has " + report.errorRows() + " ERROR rows; inspect diagnostics.tsv and original.stderr");
        if (parent != null) equivalent(parent, out, List.of("original.stdout", "annotations.tsv", "diagnostics.tsv"));
        if (!pin(out.resolve("runtime/snpeff.jar")).sha256().equals(JARS.get(cfg.get("edition")))) throw new IOException("Captured runtime changed during original execution");
        Properties completion = new Properties(); completion.setProperty("profile", PROFILE); completion.setProperty("edition", cfg.get("edition"));
        completion.setProperty("input_records", Long.toString(report.inputRecords())); completion.setProperty("annotation_rows", Long.toString(report.annotationRows())); completion.setProperty("diagnostic_rows", Long.toString(report.diagnosticRows()));
        completion.setProperty("original_engine_executed", "true"); completion.setProperty("genuine_replay", Boolean.toString(parent != null));
        completion.setProperty("observed_diagnostics_summary", "true");
        Map<String,Pin> members = inventory(out, cfg.number("max_bytes"), cfg.number("max_files"));
        write(out.resolve("manifest.properties"), pins(members));
        completion.setProperty("manifest_sha256", pin(out.resolve("manifest.properties")).sha256());
        write(out.resolve("COMPLETED.properties"), completion);
        inventory(out, cfg.number("max_bytes"), cfg.number("max_files"));
        output.println("PASS original SnpEff " + cfg.get("edition") + ": " + report.inputRecords() + " input records, " + report.annotationRows() + " annotation rows" + (parent == null ? "" : "; genuine replay bytes match"));
        output.println("WARNINGS: " + out.resolve("WARNINGS.md") + "; original diagnostics=" + report.diagnosticRows() + "; inspect diagnostics.tsv and original.stderr before dependent analysis");
    }

    private static void process(Path out, Config cfg) throws IOException, InterruptedException {
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java");
        if (!Files.isExecutable(java)) throw new Prerequisite("Current Java executable is unavailable/forbidden: " + java);
        List<String> command = new ArrayList<>(List.of(java.toString(), "-Xmx" + cfg.get("heap_mb") + "m", "-Dfile.encoding=UTF-8", "-Dline.separator=\n", "-Duser.timezone=UTC", "-Duser.language=en", "-Duser.country=US", "-jar", "runtime/snpeff.jar", "eff", "-noLog", "-c", "inputs/effective.config", "-ud", cfg.get("up_down_stream"), "-noStats"));
        if (cfg.legacy()) command.addAll(List.of("-i", "txt", "-o", "txt"));
        // Upstream Config.canonicalDir resolves relative paths against configDirPath.
        else command.addAll(List.of("-noDownload", "-dataDir", "data"));
        command.addAll(List.of(cfg.get("genome"), "inputs/variants." + cfg.get("format")));
        Properties started = new Properties(); started.setProperty("arguments", Integer.toString(command.size()));
        for (int i=0; i<command.size(); i++) started.setProperty("argument." + i, command.get(i));
        started.setProperty("java_executable_sha256", pin(java).sha256());
        started.setProperty("environment_policy", "remove-JAVA_TOOL_OPTIONS-JDK_JAVA_OPTIONS-_JAVA_OPTIONS-CLASSPATH");
        write(out.resolve("PROCESS-STARTED.properties"), started);
        Process child;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(out.toFile()).redirectInput(ProcessBuilder.Redirect.PIPE).redirectOutput(out.resolve("original.stdout").toFile()).redirectError(out.resolve("original.stderr").toFile());
            Set<String> removed = Set.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH");
            builder.environment().keySet().removeIf(key -> removed.contains(key.toUpperCase(Locale.ROOT)));
            child = builder.start();
        }
        catch (IOException forbidden) { throw new Prerequisite("Original Java process could not start: " + forbidden + "; use a permitted Java runtime and retry"); }
        long began = System.nanoTime();
        try {
            Properties pid = new Properties(); pid.setProperty("pid", Long.toString(child.pid()));
            write(out.resolve("PROCESS-PID.properties"), pid);
            child.getOutputStream().close();
            if (!child.waitFor(cfg.number("wall_ms"), TimeUnit.MILLISECONDS)) throw new IOException("Original SnpEff exceeded wall_ms; partial consoles retained");
            Properties finished = new Properties(); finished.setProperty("exit_code", Integer.toString(child.exitValue())); finished.setProperty("elapsed_ms", Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began))); finished.setProperty("pid", Long.toString(child.pid()));
            write(out.resolve("PROCESS-FINISHED.properties"), finished);
            if (child.exitValue() != 0) throw new IOException("Original SnpEff exit " + child.exitValue() + "; inspect original.stderr");
        } finally {
            if (child.isAlive()) {
                OwnedProcesses.descendants(child).forEach(ProcessHandle::destroyForcibly); child.destroyForcibly();
                if (!child.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Owned original process could not be reaped after failure");
            }
        }
    }
    private static SnpEffAnnotations.Report audit(Path bundle, Config cfg, Path table, Path messages) throws IOException {
        Path input = bundle.resolve("inputs/variants." + cfg.get("format")), annotated = bundle.resolve("original.stdout");
        return cfg.legacy() ? SnpEffAnnotations.auditTxt(input, annotated, table, messages) : SnpEffAnnotations.auditVcf(input, annotated, table, messages);
    }
    private static void equivalent(Path source, Path result, List<String> names) throws IOException {
        for (String name : names) if (!pin(source.resolve(name)).equals(pin(result.resolve(name)))) throw new IOException("Original replay/native audit differs: " + name + "; retain both attempts, no complete result admitted");
    }
    private static void validate(Path bundle) throws IOException {
        Properties completion = read(bundle.resolve("COMPLETED.properties"), true);
        if (!PROFILE.equals(completion.getProperty("profile"))) throw new IOException("Unknown completed profile");
        if (!pin(bundle.resolve("manifest.properties")).sha256().equals(completion.getProperty("manifest_sha256"))) throw new IOException("Manifest hash differs");
        Config cfg = config(read(bundle.resolve("request.properties"), true));
        Map<String,String> paths = Map.of("jar", "runtime/snpeff.jar", "config", "inputs/original.config", "data", "inputs/data", "variants", "inputs/variants." + cfg.get("format"));
        for (Map.Entry<String,String> path : paths.entrySet()) if (!path.getValue().equals(cfg.get(path.getKey()))) throw new IOException("Captured request references an external/noncanonical input");
        Map<String,Pin> members = new HashMap<>(inventory(bundle, cfg.number("max_bytes"), cfg.number("max_files")));
        members.remove("manifest.properties"); members.remove("COMPLETED.properties");
        if (!pins(members).equals(read(bundle.resolve("manifest.properties"), true))) throw new IOException("Bundle membership/content differs from the original manifest");
        if (!pin(bundle.resolve("runtime/snpeff.jar")).sha256().equals(JARS.get(cfg.get("edition")))) throw new IOException("Captured runtime is not the selected pinned original/source-built edition");
        Properties original = read(bundle.resolve("inputs/original.config"), false), effective = read(bundle.resolve("inputs/effective.config"), false);
        original.setProperty(cfg.legacy() ? "data_dir" : "data.dir", "data");
        if (!original.equals(effective)) throw new IOException("Effective configuration changed beyond declared captured data-directory remapping");
        if (!"true".equals(completion.getProperty("original_engine_executed")) || !cfg.get("edition").equals(completion.getProperty("edition"))) throw new IOException("Completion contradicts selected original execution");
        if ("true".equals(completion.getProperty("observed_diagnostics_summary")) && !Files.isRegularFile(bundle.resolve("SOURCE-DIAGNOSTICS.tsv"))) throw new IOException("Completed observed diagnostic summary is missing");
        Properties process = read(bundle.resolve("PROCESS-FINISHED.properties"), true);
        if (!"0".equals(process.getProperty("exit_code"))) throw new IOException("Completed original process did not exit successfully");
        String replay = completion.getProperty("genuine_replay");
        if (!("true".equals(replay) || "false".equals(replay)) || Boolean.parseBoolean(replay) != Files.isRegularFile(bundle.resolve("PARENT.properties"))) throw new IOException("Replay lineage contradicts completion");
        if (Boolean.parseBoolean(replay)) {
            Properties lineage = read(bundle.resolve("PARENT.properties"), true);
            for (String name : List.of("original.stdout", "annotations.tsv", "diagnostics.tsv")) if (!pin(bundle.resolve(name)).sha256().equals(lineage.getProperty(name + ".expected_sha256"))) throw new IOException("Original replay content contradicts retained parent expectation: " + name);
        }
    }
    private static void validateReport(Path bundle, SnpEffAnnotations.Report report) throws IOException {
        Properties completion = read(bundle.resolve("COMPLETED.properties"), true);
        if (report.errorRows() != 0 || !Long.toString(report.inputRecords()).equals(completion.getProperty("input_records")) || !Long.toString(report.annotationRows()).equals(completion.getProperty("annotation_rows")) || !Long.toString(report.diagnosticRows()).equals(completion.getProperty("diagnostic_rows"))) throw new IOException("Original annotation errors/counts contradict completion");
    }
}
