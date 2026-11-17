/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import jdistlib.Normal;
import jdistlib.accelerator.CpuComputeBackend;
import jdistlib.accelerator.MatrixTranspose;
import org.jlinalg.cli.LdakWorkflowRequest.Config;
import org.jlinalg.genetics.LdakBedDataset;
import org.jlinalg.genetics.LdakKinship;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;
import static org.jlinalg.cli.LdakWorkflowFiles.*;

/** Captured original LDAK 5.2 SumHer and genotype/kinship/REML workflows. */
public final class LdakWorkflowCli {
    static final String NOTICE = """
        # Original LDAK 5.2 profile: scope and required actions

        SOURCE-BUILD PROFILE: command success and catalog qualification are separate.
        This profile executes the checksum-pinned original Linux x86_64 LDAK5.2
        binary, with supplied literal inputs and explicit model/deployment choices.
        It does not install software during analysis. Prepare the permitted runtime
        with tools/literature/ldak_original/setup.py. The original GPL notice and
        executable are captured. The host Linux kernel and Java runtime are external
        prerequisites; host/version metadata and matching method bytecode are recorded.

        SumHer requires the complete uncompressed original tagging reference and
        six-column prepared summary format. A reference/model/ancestry label is not
        inferred from a filename. Supplied effective sample sizes and prevalence/
        ascertainment must describe the actual analysis; binary-trait liability
        conversion does not infer population prevalence. No BLD-LDAK reference or
        author settings are invented. Missing/forbidden references mean SKIP3.

        Kinship/REML uses exact FID/IID pairs and preserves all FAM fields literally,
        including NA sex tokens. Optional original thinning is followed by an audited
        retained/lost/trivial handoff. Without thinning, caller-selected predictors
        and weights define the model. A unit-weight unthinned GRM is not LDAK-Thin.
        Alpha values and the selection rule are explicit. The maximum-likelihood
        selection uses original PRINTED alternative likelihoods, retaining the first
        grid position on a printed tie, only with common reported null/sample counts.
        It is not an inferred paper grid or author selection policy.

        Original warnings, omissions and in-memory substitutions remain visible.
        upstream_adjustments=forbid refuses completion after any reported replacement;
        allow-recorded preserves and reports original behavior without rewriting input.
        Original SumHer can replace zero statistics with1e-6 and floor low tagging
        values at1. Ambiguous-allele exclusion and check_sums are separate explicit
        choices. Raw estimates, original categories and every result-family file
        remain available. Original unconstrained negative estimates are not clamped.

        VERIFY requires matching method code, exact directory membership/hashes,
        complete original process records and regenerated derived audits. It never
        executes LDAK. Native Java independently recomputes every captured kinship
        entry and checks float32 output with 2e-7 absolute plus2e-7 relative tolerance.
        It verifies thinning set handoff, not the independent thinning algorithm.
        SumHer/REML audits check original convergence, estimates, input counts and
        declared liability conversion; they do not independently refit these models.
        REPLAY actually executes the captured original pipeline in NEW output and
        requires all original scientific output and native-audit bytes to agree.
        Integrity and replay do not establish producer authenticity or biological truth.

        Original stages use one MKL/OMP thread and LC_ALL=C; loader injection and
        inherited MKL/OMP controls are removed. This 5.2 binary has no supported
        seed option in the selected interface; none is fabricated. Replay tests
        the selected workflows, not universal determinism of every LDAK feature.
        wall_ms bounds each original stage. Workspace monitoring/capture limits
        are not an OS disk/RSS quota and can overshoot between polls. Numeric and
        metadata admission bounds refuse rather than truncate samples/predictors.
        Retain sufficient parent JVM heap for native audits and metadata.

        SKIP3: exact required runtime/reference or permitted Linux execution is
        unavailable/forbidden; skip dependent analysis. Supply the named permitted
        prerequisite and retry NEW output. FAIL2: invalid input, original error,
        nonconvergence, timeout, audit, recording or replay discrepancy; no complete
        result. Read FAILURE.txt, WARNINGS.md, DIAGNOSTICS.tsv and both original
        consoles. All failed attempts remain. Do not repair bundles, refresh hashes
        or change editions to force a pass. Require COMPLETED.properties and VERIFY;
        preserve the matching application artifact. This profile does not establish
        author-binary identity, whole-paper reproduction or full250-goal completion.
        """;
    private LdakWorkflowCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream output, PrintStream error) {
        Path owned = null; Config active = null;
        try {
            if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
                output.println("ldak-tools run --request FILE --out NEW_DIRECTORY\nldak-tools verify --bundle DIRECTORY --out NEW_DIRECTORY\nldak-tools replay --bundle DIRECTORY --out NEW_DIRECTORY\n" + NOTICE);
                output.flush(); return output.checkError() ? 2 : 0;
            }
            if (args.length != 5 || !args[3].equals("--out")) throw new IllegalArgumentException("Use ldak-tools --help");
            Path source = Path.of(args[2]).toAbsolutePath().normalize(); String action = args[0];
            if (action.equals("run") && args[1].equals("--request")) {
                Config c = LdakWorkflowRequest.parse(load(source)); active = c;
                Path out = destination(Path.of(args[4]), LdakWorkflowInputs.paths(source, c));
                Files.createDirectories(out.getParent()); Files.createDirectory(out); owned = out;
                execute(source, c, out, null, output, error);
            } else if ((action.equals("verify") || action.equals("replay")) && args[1].equals("--bundle")) {
                source = source.toRealPath();
                Path out = destination(Path.of(args[4]), List.of(source));
                Config c = validate(source); active = c;
                Files.createDirectories(out.getParent()); Files.createDirectory(out); owned = out;
                if (action.equals("verify")) {
                    var report = LdakWorkflowResults.audit(source, c, out);
                    derivedEqual(source, out);
                    requireReport(source, report);
                    Files.writeString(out.resolve("WARNINGS.md"), report.diagnostics().warningsText(NOTICE), StandardCharsets.UTF_8);
                    Properties verified = new Properties();
                    verified.setProperty("status", "PASS_DIRECTORY_INTEGRITY_AND_NATIVE_AUDIT_WITHOUT_ORIGINAL_EXECUTION");
                    verified.setProperty("source_manifest_sha256", pin(source.resolve("manifest.properties")).sha256());
                    verified.setProperty("compared_kinship_entries", Long.toString(report.comparedKinshipEntries()));
                    store(out.resolve("VERIFIED.properties"), verified);
                    output.println("PASS LDAK integrity and native audit; original engine was not executed. WARNINGS: " + out.resolve("WARNINGS.md"));
                } else {
                    Path parentAudit = out.resolve("parent-audit"); Files.createDirectory(parentAudit);
                    var parentReport = LdakWorkflowResults.audit(source, c, parentAudit);
                    derivedEqual(source, parentAudit); requireReport(source, parentReport);
                    execute(source.resolve("request.properties"), c, out, source, output, error);
                }
            } else throw new IllegalArgumentException("Use ldak-tools --help");
            output.flush(); error.flush();
            if (output.checkError() || error.checkError()) throw new IOException("Console recording failed; no complete result");
            return 0;
        } catch (Exception failure) {
            int status = failure instanceof Prerequisite ? 3 : 2;
            String message = (status == 3 ? "SKIP3: dependent analysis skipped. " : "FAIL2: no complete result admitted. ") + failure.getMessage()
                + "\nPreserve all inputs, partials and diagnostics. Resolve the named cause and retry NEW output.\n";
            if (owned != null) {
                try {
                    Files.deleteIfExists(owned.resolve("COMPLETED.properties")); Files.deleteIfExists(owned.resolve("VERIFIED.properties"));
                    String warnings = NOTICE;
                    if (active != null) {
                        var diagnostics = LdakWorkflowDiagnostics.collect(owned, active);
                        Files.writeString(owned.resolve("DIAGNOSTICS.tsv"), diagnostics.table(), StandardCharsets.UTF_8);
                        warnings = diagnostics.warningsText(NOTICE);
                    }
                    Files.writeString(owned.resolve("WARNINGS.md"), "# Failed or skipped analysis\n\n" + message + "\n" + warnings, StandardCharsets.UTF_8);
                    Files.writeString(owned.resolve(status == 3 ? "SKIPPED.txt" : "FAILURE.txt"), message + failure + "\n", StandardCharsets.UTF_8);
                } catch (IOException recording) { message += "ERROR retaining diagnostics: " + recording + "\n"; }
            }
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            error.print(message); error.flush(); return status;
        }
    }
    private static void execute(Path request, Config original, Path out, Path parent, PrintStream output, PrintStream error) throws IOException, InterruptedException {
        Files.writeString(out.resolve("WARNINGS.md"), NOTICE, StandardCharsets.UTF_8);
        LdakWorkflowProcess.prerequisite(original);
        Config c = LdakWorkflowInputs.capture(request, original, out);
        Files.createDirectory(out.resolve("stages"));
        store(out.resolve("implementation.properties"), implementation());
        Properties host = new Properties();
        for (String key : List.of("java.version", "java.runtime.version", "java.vendor", "os.name", "os.version", "os.arch")) host.setProperty(key, System.getProperty(key));
        store(out.resolve("HOST.properties"), host);
        if (parent != null) {
            copy(parent.resolve("scientific.properties"), out.resolve("PARENT-SCIENCE.properties"));
            Properties lineage = new Properties();
            lineage.setProperty("parent_manifest_sha256", pin(parent.resolve("manifest.properties")).sha256());
            lineage.setProperty("expected_science_manifest_sha256", pin(out.resolve("PARENT-SCIENCE.properties")).sha256());
            store(out.resolve("PARENT.properties"), lineage);
        }
        Map<String, Pin> inputPins = inventory(out.resolve("inputs"), c.number("max_bytes"), c.number("max_files"));
        Map<String, Pin> runtimePins = inventory(out.resolve("runtime"), c.number("max_bytes"), c.number("max_files"));
        inventory(out, c.number("max_bytes"), c.number("max_files"));
        error.println("WARNING: selected original LDAK5.2 profile. Inspect " + out.resolve("WARNINGS.md") + "; no paper parameters or biological result are inferred.");
        for (var stage : LdakWorkflowProcess.plan(c)) {
            LdakWorkflowProcess.execute(out, c, stage);
            var diagnostics = LdakWorkflowDiagnostics.collect(out, c);
            Files.writeString(out.resolve("WARNINGS.md"), diagnostics.warningsText(NOTICE), StandardCharsets.UTF_8);
            LdakWorkflowDiagnostics.requireAdmissible(c, diagnostics);
            LdakWorkflowProcess.validateStage(out, c, stage);
        }
        if (!inputPins.equals(inventory(out.resolve("inputs"), c.number("max_bytes"), c.number("max_files")))
                || !runtimePins.equals(inventory(out.resolve("runtime"), c.number("max_bytes"), c.number("max_files")))) throw new IOException("Captured runtime or scientific inputs changed during original execution");
        var report = LdakWorkflowResults.audit(out, c, out);
        Files.writeString(out.resolve("WARNINGS.md"), report.diagnostics().warningsText(NOTICE), StandardCharsets.UTF_8);
        Properties science = pins(science(out, c));
        store(out.resolve("scientific.properties"), science);
        if (parent != null && !science.equals(load(out.resolve("PARENT-SCIENCE.properties")))) throw new IOException("Genuine original replay differs in complete scientific output or native audits; retain both attempts");
        if (!implementation().equals(load(out.resolve("implementation.properties")))) throw new IOException("Method implementation changed during execution");
        Map<String, Pin> contents = inventory(out, c.number("max_bytes"), c.number("max_files"));
        store(out.resolve("manifest.properties"), pins(contents));
        Properties completed = new Properties();
        completed.setProperty("profile", PROFILE); completed.setProperty("mode", c.get("mode"));
        completed.setProperty("original_engine_executed", "true"); completed.setProperty("genuine_replay", Boolean.toString(parent != null));
        completed.setProperty("manifest_sha256", pin(out.resolve("manifest.properties")).sha256());
        reportFields(completed, report);
        store(out.resolve("COMPLETED.properties"), completed);
        inventory(out, c.number("max_bytes"), c.number("max_files"));
        output.println("PASS captured original LDAK5.2 " + c.get("mode") + "; analyses=" + report.analyses() + "; native kinship entries=" + report.comparedKinshipEntries() + (parent == null ? "" : "; genuine replay matches"));
        output.println("WARNINGS: " + out.resolve("WARNINGS.md") + "; original warnings=" + report.diagnostics().warnings() + "; zero statistics replaced=" + report.diagnostics().zeroStatisticsReplaced() + "; taggings floored=" + report.diagnostics().taggingsFloored());
    }
    static Config validate(Path root) throws IOException {
        if (Files.exists(root.resolve("FAILURE.txt")) || Files.exists(root.resolve("SKIPPED.txt"))) throw new IOException("Failed/skipped output is not a complete bundle");
        Properties completed = load(root.resolve("COMPLETED.properties"));
        if (!PROFILE.equals(completed.getProperty("profile")) || !"true".equals(completed.getProperty("original_engine_executed"))) throw new IOException("Unknown/incomplete original LDAK profile");
        if (!pin(root.resolve("manifest.properties")).sha256().equals(completed.getProperty("manifest_sha256"))) throw new IOException("Bundle manifest hash differs");
        if (!implementation().equals(load(root.resolve("implementation.properties")))) throw new IOException("Matching native/workflow method code is required; use the preserved reader artifact, never refresh its recorded hashes");
        Config c = LdakWorkflowRequest.parse(load(root.resolve("request.properties")));
        if (!c.get("execution").equals("allow") || !c.get("mode").equals(completed.getProperty("mode"))) throw new IOException("Completion contradicts declared original execution/model");
        LdakWorkflowInputs.capturedPaths(c);
        Map<String, Pin> contents = new TreeMap<>(inventory(root, c.number("max_bytes"), c.number("max_files")));
        contents.remove("manifest.properties"); contents.remove("COMPLETED.properties");
        if (!pins(contents).equals(load(root.resolve("manifest.properties")))) throw new IOException("Complete directory membership/content differs from captured manifest");
        if (!BINARY_SHA.equals(pin(root.resolve("runtime/ldak5.2.linux")).sha256()) || !LICENSE_SHA.equals(pin(root.resolve("runtime/LICENSE-UPSTREAM.txt")).sha256())) throw new IOException("Captured original software/license identity differs");
        if (!pins(science(root, c)).equals(load(root.resolve("scientific.properties")))) throw new IOException("Scientific output inventory differs");
        boolean replay = "true".equals(completed.getProperty("genuine_replay"));
        if (!(replay || "false".equals(completed.getProperty("genuine_replay"))) || replay != Files.isRegularFile(root.resolve("PARENT.properties")) || replay != Files.isRegularFile(root.resolve("PARENT-SCIENCE.properties"))) throw new IOException("Replay lineage/membership contradicts completion");
        if (replay) {
            Properties parent = load(root.resolve("PARENT.properties"));
            if (!pin(root.resolve("PARENT-SCIENCE.properties")).sha256().equals(parent.getProperty("expected_science_manifest_sha256")) || !load(root.resolve("PARENT-SCIENCE.properties")).equals(load(root.resolve("scientific.properties")))) throw new IOException("Replay scientific results contradict captured parent expectations");
            if (!parent.getProperty("parent_manifest_sha256", "").matches("[0-9a-f]{64}")) throw new IOException("Missing parent manifest identity");
        }
        return c;
    }
    private static Map<String, Pin> science(Path root, Config c) throws IOException {
        Map<String, Pin> all = inventory(root, c.number("max_bytes"), c.number("max_files")), result = new TreeMap<>();
        all.forEach((name, identity) -> {
            String base = Path.of(name).getFileName().toString();
            if (!name.endsWith("/") && ((name.startsWith("stages/") && base.startsWith("result.") && !base.endsWith(".progress"))
                    || name.startsWith("native/") || name.equals("summary.tsv") || name.equals("DIAGNOSTICS.tsv"))) result.put(name, identity);
        });
        return result;
    }
    private static void derivedEqual(Path parent, Path fresh) throws IOException {
        List<String> names = new ArrayList<>(List.of("summary.tsv", "DIAGNOSTICS.tsv"));
        try (var files = Files.walk(parent.resolve("native"))) {
            files.filter(Files::isRegularFile).forEach(path -> names.add(parent.relativize(path).toString()));
        }
        for (String name : names) if (!pin(parent.resolve(name)).equals(pin(fresh.resolve(name)))) throw new IOException("Regenerated native audit differs: " + name);
        Map<String, Pin> old = inventory(parent.resolve("native"), Long.MAX_VALUE, Long.MAX_VALUE), current = inventory(fresh.resolve("native"), Long.MAX_VALUE, Long.MAX_VALUE);
        if (!old.equals(current)) throw new IOException("Regenerated native report membership differs");
    }
    private static void reportFields(Properties values, LdakWorkflowResults.Report report) {
        values.setProperty("analyses", Long.toString(report.analyses())); values.setProperty("estimated_components", Long.toString(report.estimatedComponents()));
        values.setProperty("compared_kinship_entries", Long.toString(report.comparedKinshipEntries()));
        values.setProperty("original_warnings", Long.toString(report.diagnostics().warnings())); values.setProperty("original_errors", Long.toString(report.diagnostics().errors()));
        values.setProperty("zero_statistics_replaced", Long.toString(report.diagnostics().zeroStatisticsReplaced())); values.setProperty("taggings_floored", Long.toString(report.diagnostics().taggingsFloored()));
    }
    private static void requireReport(Path root, LdakWorkflowResults.Report report) throws IOException {
        Properties expected = new Properties(); reportFields(expected, report); Properties actual = load(root.resolve("COMPLETED.properties"));
        for (String key : expected.stringPropertyNames()) if (!expected.getProperty(key).equals(actual.getProperty(key))) throw new IOException("Completion/native result count differs: " + key);
    }
    private static Properties implementation() throws IOException {
        var types = new TreeSet<Class<?>>((a, b) -> a.getName().compareTo(b.getName()));
        for (Class<?> type : List.of(LdakWorkflowCli.class, LdakWorkflowFiles.class, LdakWorkflowRequest.class, LdakWorkflowProcess.class,
                LdakWorkflowInputs.class, LdakWorkflowResults.class, LdakWorkflowDiagnostics.class, LdakKinship.class, LdakBedDataset.class,
                BackendContext.class, BackendPolicy.class, CpuComputeBackend.class, MatrixTranspose.class, Normal.class)) collect(type, types);
        Properties result = new Properties();
        for (Class<?> type : types) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                if (stream == null) throw new IOException("Missing method bytecode " + type.getName());
                MessageDigest digest;
                try { digest = MessageDigest.getInstance("SHA-256"); }
                catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
                byte[] buffer = new byte[16384]; for (int n; (n = stream.read(buffer)) != -1;) digest.update(buffer, 0, n);
                result.setProperty(type.getName(), HexFormat.of().formatHex(digest.digest()));
            }
        }
        return result;
    }
    private static void collect(Class<?> type, Set<Class<?>> into) { if (into.add(type)) for (Class<?> nested : type.getDeclaredClasses()) collect(nested, into); }
}
