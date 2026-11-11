/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Original source execution, pinned runtime admission and complete selected source output contracts. */
final class NfcoreChipseqWorkflow {
    private NfcoreChipseqWorkflow() { }
    static Path run(Path root, Path suppliedRequest, Path engineHome, Path setupReport, boolean resume) throws Exception {
        if (!System.getProperty("os.name").equals("Linux")) throw new NextflowProcess.Unavailable("Original nf-core execution requires Linux Java, including WSL. Portable saved-result verification is available on Windows. Analysis skipped.");
        if (!root.isAbsolute() || !root.normalize().equals(root) || root.toString().chars().anyMatch(c -> c > 127 || Character.isWhitespace(c) || c < 32 || c == 127 || c == ',')) throw new NextflowProcess.Unavailable("Use a NEW canonical ASCII run path without whitespace/comma. Original scientific identifiers are retained. Analysis skipped.");
        if (!resume) {
            if (root.getParent() == null || !root.getParent().toRealPath().equals(root.getParent())) throw new IOException("Require canonical existing parent and new adapter run"); Files.createDirectory(root);
            capture(suppliedRequest, root.resolve("request.json"), 4194304); var original = NfcoreChipseqRequest.read(root.resolve("request.json")); capture(original.recipe(), root.resolve("engine-recipe.json"), 4194304);
            writeJson(root.resolve("init.json"), Map.of("pipeline_profile", NfcoreChipseqRequest.PROFILE, "request", pin(root.resolve("request.json"), 4194304, () -> { }), "recipe", pin(root.resolve("engine-recipe.json"), 4194304, () -> { })));
            Files.createDirectory(root.resolve("attempts")); Files.createDirectory(root.resolve("results"));
        }
        directory(root);
        try (var channel = FileChannel.open(root.resolve(".adapter.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock(); if (lock == null) throw new IOException("Adapter run is already owned by an active attempt");
            try (lock) { return locked(root, engineHome, setupReport, resume); }
        }
    }
    private static Path locked(Path root, Path engineHome, Path setupReport, boolean resume) throws Exception {
        Map<?, ?> init = object(json(root.resolve("init.json"), 1048576)); if (!NfcoreChipseqRequest.PROFILE.equals(init.get("pipeline_profile")) || !fingerprint(init.get("request"), 4194304).equals(pin(root.resolve("request.json"), 4194304, () -> { })) || !fingerprint(init.get("recipe"), 4194304).equals(pin(root.resolve("engine-recipe.json"), 4194304, () -> { }))) throw new IOException("Captured adapter request/recipe changed");
        var original = NfcoreChipseqRequest.read(root.resolve("request.json")); var request = new NfcoreChipseqRequest.Request(root.resolve("engine-recipe.json"), original.containerHome(), original.image(), original.peakType(), original.readMode(), original.minimumReplicates(), original.executorCpus(), original.executorMemoryBytes(), original.raw());
        int number = 1;
        if (Files.exists(root.resolve("latest.json"))) {
            Map<?, ?> previous = object(json(root.resolve("latest.json"), 1048576)); if (!resume || "RUNNING".equals(previous.get("status"))) throw new IOException("Explicit resume requires a known terminal adapter attempt; preserve live/indeterminate runs and inspect their owned-process evidence"); number = Math.addExact(Integer.parseInt(text(previous.get("attempt"))), 1);
        } else if (resume) throw new IOException("No terminal adapter attempt exists to resume");
        if (number > 999999) throw new IOException("Adapter attempt capacity exceeded"); String id = String.format(java.util.Locale.ROOT, "%06d", number); Path attempt = Files.createDirectory(root.resolve("attempts").resolve(id)); latest(root, id, "RUNNING");
        try {
            // Resumes use the unchanged original captured engine request/cache; no recapture from changed producers.
            var recipe = resume ? NextflowRecipe.read(root.resolve("engine-recipe.json")) : NfcoreChipseqRequest.validate(request, root.resolve("engine"));
            var managed = NextflowRuntime.open(engineHome, recipe.profile(), setupReport, () -> { });
            Path runtimeRoot = Files.createDirectory(attempt.resolve("runtime-admission")); var runtime = new NfcoreDeseq126Runtime(request.containerHome(), request.image(), runtimeRoot, recipe.process()); runtime.health();
            Path ownJar = executable(); Pin jarPin = pin(ownJar, 1000000000L, () -> { });
            NextflowProcess.Check integrity = () -> { runtime.verify(); managed.verify(() -> { }); if (!pin(ownJar, 1000000000L, () -> { }).equals(jarPin) || !fingerprint(init.get("request"), 4194304).equals(pin(root.resolve("request.json"), 4194304, () -> { })) || !fingerprint(init.get("recipe"), 4194304).equals(pin(root.resolve("engine-recipe.json"), 4194304, () -> { }))) throw new IOException("Adapter implementation/request integrity changed during original execution"); };
            var command = javaCommand(ownJar, "org.jlinalg.cli.NfcoreCapturedToolsCli", recipe.heapMib(), recipe.cpus());
            if (resume) command.addAll(List.of("--resume", root.resolve("engine").toString())); else command.addAll(List.of("--recipe", root.resolve("engine-recipe.json").toString(), "--out", root.resolve("engine").toString()));
            command.addAll(List.of("--home", engineHome.toString(), "--setup-report", setupReport.toString()));
            Path launch = attempt.resolve("engine-process");
            try { execute(command, launch, root, recipe.process(), integrity); }
            catch (IOException failure) {
                if (Files.exists(launch.resolve("process.json"))) { Map<?, ?> ledger = object(json(launch.resolve("process.json"), 67108864)); if (ledger.containsKey("exit_code") && integer(ledger.get("exit_code"), 0, 255) == 3 && list(ledger.get("owned_live_after_cleanup")).isEmpty() && (!ledger.containsKey("suppressed") || list(ledger.get("suppressed")).isEmpty())) throw new NextflowProcess.Unavailable("Original engine reported a missing/forbidden prerequisite. Analysis skipped; inspect retained engine-process/process.stderr and the engine failure report. " + failure); }
                throw failure;
            }
            var engine = NfcoreEngineBundle.verify(root.resolve("engine"), () -> { }); var files = new NfcoreScientificFiles(engine); files.verifyChipseq121Source();
            if (!utf8(bytes(files.inputMember(files.alias("workflow") + "/runtime/host.config"), 1048576)).equals(NfcoreChipseqRequest.overlay(request, root.resolve("engine")))) throw new IOException("Captured source runtime binding differs from the typed adapter request");
            Path audits = Files.createDirectory(attempt.resolve("audits")); var records = new ArrayList<Map<String, Object>>();
            component(records, "reads-and-alignments", "NfcoreChipseq121AuditCli", List.of("--peak-type", request.peakType(), "--read-mode", request.readMode()), ownJar, audits, attempt, root, recipe, integrity);
            component(records, "consensus", "NfcoreConsensusAuditCli", List.of("--peak-type", request.peakType(), "--min-replicates", Integer.toString(request.minimumReplicates())), ownJar, audits, attempt, root, recipe, integrity);
            component(records, "complexity", "NfcoreComplexityAuditCli", List.of(), ownJar, audits, attempt, root, recipe, integrity);
            component(records, "annotation-QC", "NfcoreAnnotationAuditCli", List.of("--peak-type", request.peakType(), "--min-replicates", Integer.toString(request.minimumReplicates())), ownJar, audits, attempt, root, recipe, integrity);
            var mappings = NfcoreChipseq121Mappings.read(files.output("pipeline_info/design_reads.csv"), files.output("pipeline_info/design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults());
            if (mappings.controls().stream().anyMatch(row -> row.replicatesExist() && row.multipleGroups())) component(records, "differential", "NfcoreDeseq126AuditCli", List.of("--peak-type", request.peakType(), "--min-replicates", Integer.toString(request.minimumReplicates()), "--read-mode", request.readMode(), "--container-home", request.containerHome().toString(), "--image", request.image().toString()), ownJar, audits, attempt, root, recipe, integrity);
            else { Path skipped = Files.createDirectory(audits.resolve("differential")); writeJson(skipped.resolve("NOT_REQUESTED.json"), Map.of("reason", "original-replicates-and-multiple-groups-condition-false", "negative_finding", false, "engine_manifest", engine.manifest())); records.add(Map.of("component", "differential", "status", "source-not-requested", "negative_finding", false)); }
            integrity.verify(); var after = NfcoreEngineBundle.verify(root.resolve("engine"), () -> { }); if (!after.attempt().equals(engine.attempt()) || !after.manifest().equals(engine.manifest())) throw new IOException("Latest original engine result changed during selected source output audits");
            Path payload = Files.createDirectory(root.resolve("results").resolve(id)); NfcoreChipseqBundle.copy(engine.attempt(), payload.resolve("engine")); NfcoreChipseqBundle.copy(audits, payload.resolve("audits")); capture(root.resolve("request.json"), payload.resolve("request.json"), 4194304); capture(root.resolve("engine-recipe.json"), payload.resolve("engine-recipe.json"), 4194304); capture(ownJar, payload.resolve("adapter.jar"), 1000000000L);
            Path processes = Files.createDirectory(payload.resolve("processes"));
            NfcoreChipseqBundle.copy(runtimeRoot, processes.resolve("runtime-admission"));
            for (String stage : List.of("engine", "reads-and-alignments", "consensus", "complexity", "annotation-QC", "differential"))
                if (Files.exists(attempt.resolve(stage + "-process"))) NfcoreChipseqBundle.copy(attempt.resolve(stage + "-process"), processes.resolve(stage));
            var report = new LinkedHashMap<String, Object>(); report.put("pipeline_profile", NfcoreChipseqRequest.PROFILE); report.put("status", "complete-declared-original-source-workflow"); report.put("engine_manifest", engine.manifest()); report.put("complete_published_files", engine.outputFiles()); report.put("complete_published_bytes", engine.outputBytes()); report.put("source_components", records); report.put("implementation", jarPin); report.put("original_image", pin(request.image(), 2000000000L, () -> { })); report.put("engine_profile", recipe.profile()); report.put("author_engine_inferred", false); report.put("biological_accuracy_certified", false); report.put("whole_paper_reproduced", false); report.put("independent_whole_model_reimplementation", false);
            var warnings = new StringBuilder("# Original nf-core declared workflow result\n\n").append(NfcoreChipseqToolsCli.ACTION).append("\n\n").append(utf8(bytes(engine.attempt().resolve("WARNINGS.md"), 1048576)));
            for (String name : List.of("reads-and-alignments", "consensus", "complexity", "annotation-QC", "differential")) if (Files.exists(audits.resolve(name).resolve("WARNINGS.md"))) warnings.append("\n\n## ").append(name).append("\n\n").append(utf8(bytes(audits.resolve(name).resolve("WARNINGS.md"), 16777216)));
            Files.writeString(payload.resolve("WARNINGS.md"), warnings, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); NfcoreChipseqBundle.finish(payload, report); integrity.verify(); latest(root, id, "COMPLETED"); return payload;
        } catch (Exception | LinkageError failure) {
            int code = failure instanceof NextflowProcess.Unavailable || failure instanceof NextflowRuntime.Unavailable ? 3 : 2; String status = code == 3 ? "SKIP3_ANALYSIS_SKIPPED" : "FAIL2_NO_RESULT";
            writeJson(attempt.resolve("FAILURE.json"), Map.of("status", status, "error", failure.toString(), "required_action", NfcoreChipseqToolsCli.ACTION)); Files.writeString(attempt.resolve("WARNINGS.md"), "# " + status + "\n\n" + failure + "\n\n" + NfcoreChipseqToolsCli.ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); latest(root, id, code == 3 ? "SKIP3" : "FAIL2"); throw failure;
        }
    }
    private static void component(List<Map<String, Object>> records, String name, String main, List<String> options, Path jar, Path audits, Path attempt, Path root, NextflowRecipe.Request recipe, NextflowProcess.Check integrity) throws IOException {
        var command = javaCommand(jar, "org.jlinalg.cli." + main, recipe.heapMib(), recipe.cpus()); command.addAll(List.of("--engine-result", root.resolve("engine").toString(), "--out", audits.resolve(name).toString())); command.addAll(options);
        execute(command, attempt.resolve(name + "-process"), root, recipe.process(), integrity); Map<?, ?> completed = object(json(audits.resolve(name).resolve("COMPONENT_AUDIT_COMPLETED.json"), 1048576)); if (!fingerprint(completed.get("audit"), 67108864).equals(pin(audits.resolve(name).resolve("audit.json"), 67108864, () -> { }))) throw new IOException("Completed component audit changed: " + name);
        records.add(Map.of("component", name, "status", "complete-source-consistency-contract", "audit", pin(audits.resolve(name).resolve("audit.json"), 67108864, () -> { })));
    }
    private static void execute(List<String> command, Path record, Path root, NextflowProcess.Limits limits, NextflowProcess.Check integrity) throws IOException {
        try { NfcoreProcess.executeSession(command, Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "LC_ALL", "C.UTF-8", "TZ", "UTC"), record, root, root, limits, integrity, () -> { if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("nf-core interrupted"); }); }
        catch (IOException failure) { throw new IOException("Stage " + record.getFileName() + " failed. Inspect retained " + record.resolve("process.stderr") + ", " + record.resolve("process.stdout") + " and " + record.resolve("process.json") + ". Cause: " + failure, failure); }
    }
    private static ArrayList<String> javaCommand(Path jar, String main, int heapMib, int cpus) { return new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Xmx" + heapMib + "m", "-XX:ActiveProcessorCount=" + cpus, "-cp", jar.toString(), main)); }
    private static Path executable() throws Exception { Path path = Path.of(NfcoreChipseqWorkflow.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize(); if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) throw new NextflowProcess.Unavailable("Use the packaged self-contained nf-core development JAR; analysis skipped for an unbound development classpath"); return path; }
    private static void capture(Path source, Path target, long maximum) throws IOException { Pin before = pin(source, maximum, () -> { }); Files.copy(source, target); if (!pin(target, maximum, () -> { }).equals(before) || !pin(source, maximum, () -> { }).equals(before)) throw new IOException("Complete adapter input changed during capture"); }
    private static void latest(Path root, String id, String status) throws IOException { Path next = root.resolve("latest.new.json"); writeJson(next, Map.of("attempt", id, "status", status)); Files.move(next, root.resolve("latest.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
}
