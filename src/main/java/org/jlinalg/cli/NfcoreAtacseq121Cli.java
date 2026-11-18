/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Exact original ATAC1.2.1 execution, complete capture and portable output-contract verification. */
public final class NfcoreAtacseq121Cli {
    private NfcoreAtacseq121Cli() { }
    static final String ACTION = "Required action: read WARNINGS.md, the original engine log/trace and process ledgers. Require COMPLETED.json and successful --verify before downstream use. SKIP3 means an exact prerequisite or requested capability is missing/forbidden: the dependent analysis is skipped. Download/install separately only where permitted, or provide the exact local source/runtime/image. FAIL2 means invalid input, integrity, source-output, process, cleanup or capacity failure: no ATAC result is admitted. Preserve originals and every failed attempt. Resolve the named cause and prepare NEW paths when requests/inputs/capacities change. This profile runs original nf-core/atacseq1.2.1 with explicitly selected Nextflow21.04.1; an unreported paper engine or pipeline edition is not inferred. Complete source, task, sample-mapping and selected output checks do not independently reimplement numerical models or certify biological accuracy/whole-paper reproduction. Original NA/nonfinite values and warnings remain retained. Other nf-core pipelines remain separately scoped; Cell Ranger remains excluded. Hashes establish integrity, not authenticity.";
    // Pinned original OCI conversion, using the original image creation epoch.
    static final Pin IMAGE_PROFILE = new Pin(1722, "fc88312bde83e5a76aa33e415827b172ce50bc76c4e71a325a9f8304d76c3138");
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    static int run(String[] args, PrintStream output, PrintStream error) {
        Path root = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                emit(output, "nfcore-tools ATACSEQ-1.2.1 --request TYPED_REQUEST.json --home EXACT_NEXTFLOW21_HOME --setup-report CURRENT_HOST_REPORT --out NEW_RUN\n"
                    +"nfcore-tools ATACSEQ-1.2.1 --verify RUN_OR_PORTABLE_RESULT\n"
                    +"nfcore-tools ATACSEQ-1.2.1 --export-result COMPLETED_RUN --out NEW_PORTABLE_RESULT\n"
                    +"nfcore-tools ATACSEQ-1.2.1 --export-runtime-setup NEW_DIRECTORY\n"+ACTION); return output.checkError() ? 2 : 0;
            }
            if (args.length == 2 && args[0].equals("--export-runtime-setup")) {
                NextflowSetup.export(Path.of(args[1])); emit(output, "Exact managed Nextflow setup exported. No installation or analysis performed.\n"+ACTION); return output.checkError() ? 2 : 0;
            }
            if (args.length == 2 && args[0].equals("--verify")) {
                Path payload = Path.of(args[1]).toAbsolutePath().normalize(); var result = verify(payload);
                emit(output, "Original ATAC execution and selected output contracts verified; "+result.get("complete_published_files")+" complete published files. Portable verification does not rerun analysis or readmit the producer host.\n"+ACTION); return output.checkError() ? 2 : 0;
            }
            if (args.length == 4 && args[0].equals("--export-result") && args[2].equals("--out")) {
                export(Path.of(args[1]).toAbsolutePath().normalize(), Path.of(args[3]).toAbsolutePath().normalize());
                emit(output, "Portable original ATAC execution result exported and verified: "+args[3]+"\n"+ACTION); return output.checkError() ? 2 : 0;
            }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i+1 >= args.length || !Set.of("--request", "--home", "--setup-report", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i+1]) != null) throw new IOException("Require unique complete ATAC options; see --help");
            if (!options.containsKey("--request") || !options.containsKey("--out")) throw new IOException("Require a typed ATAC request and NEW output path");
            if (!System.getProperty("os.name").equals("Linux")) throw new NextflowProcess.Unavailable("Original ATAC execution requires Linux Java, including WSL. Portable --verify remains available on Windows; analysis skipped.");
            if (!options.containsKey("--home") || !options.containsKey("--setup-report")) throw new NextflowProcess.Unavailable("Exact managed Nextflow home and completed current host report are required. Complete permitted separate setup; analysis skipped.");
            root = Path.of(options.get("--out")).toAbsolutePath().normalize();
            if (!root.toString().matches("/[A-Za-z0-9_./-]+") || root.getParent() == null || !root.getParent().toRealPath().equals(root.getParent())) throw new NextflowProcess.Unavailable("Use a NEW canonical ASCII run path without whitespace/shell metacharacters. Original IDs are not repaired; analysis skipped.");
            Files.createDirectory(root); owned = true;
            Path implementation = Path.of(NfcoreAtacseq121Cli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(implementation) || !implementation.getFileName().toString().endsWith(".jar")) throw new NextflowProcess.Unavailable("Use the packaged self-contained ATAC executable. An unbound development classpath is outside this execution profile; analysis skipped.");
            Pin implementationPin = pin(implementation, 1000000000L, () -> { });
            Files.copy(implementation, root.resolve("adapter.jar"));
            if (!pin(root.resolve("adapter.jar"), 1000000000L, () -> { }).equals(implementationPin)) throw new IOException("ATAC implementation changed during complete capture");
            capture(Path.of(options.get("--request")).toAbsolutePath().normalize(), root.resolve("request.json"), 4194304);
            var original = NfcoreAtacseq121Request.read(root.resolve("request.json"));
            capture(original.recipe(), root.resolve("engine-recipe.json"), 4194304);
            capture(original.images(), root.resolve("images.json"), 1048576);
            var request = new NfcoreAtacseq121Request.Request(root.resolve("engine-recipe.json"), original.containerHome(), root.resolve("images.json"), original.cpus(), original.memoryBytes());
            var recipe = NextflowRecipe.read(request.recipe());
            Path runtime = Files.createDirectory(root.resolve("runtime-admission"));
            var containers = new NfcoreContainerSet(request.containerHome(), request.images(), runtime, recipe.process(), NfcoreAtacseq121Request.RESOURCE+"images.json", IMAGE_PROFILE);
            NfcoreAtacseq121Request.validate(request, root.resolve("engine"), containers.overlay(root.resolve("engine"), request.cpus(), request.memoryBytes()));
            containers.health(); containers.verify();
            var initial = new LinkedHashMap<Path, Pin>();
            for (String name : Set.of("request.json", "engine-recipe.json", "images.json")) initial.put(root.resolve(name), pin(root.resolve(name), 67108864, () -> { }));
            long[] previousCheck = {0};
            // Scientific execution is delegated to the existing complete-capture/owned-process engine.
            NfcoreCapturedWorkflow.create(root.resolve("engine"));
            NfcoreCapturedWorkflow.start(root.resolve("engine"), request.recipe(), Path.of(options.get("--home")).toAbsolutePath().normalize(), Path.of(options.get("--setup-report")).toAbsolutePath().normalize(), () -> {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Original ATAC execution interrupted");
                long now = System.nanoTime();
                if (now-previousCheck[0] > 5_000_000_000L) {
                    try {
                        containers.verify();
                        if (!pin(implementation, 1000000000L, () -> { }).equals(implementationPin)) throw new IOException("ATAC implementation changed during execution");
                        for (var row : initial.entrySet()) if (!pin(row.getKey(), 67108864, () -> { }).equals(row.getValue())) throw new IOException("Captured ATAC request/recipe/image declaration changed during execution");
                    } catch (IOException changed) { throw new UncheckedIOException(changed); }
                    previousCheck[0] = System.nanoTime();
                }
            });
            containers.verify(); var engine = NfcoreEngineBundle.verify(root.resolve("engine"), () -> { });
            var report = NfcoreAtacseq121Audit.read(engine);
            writeJson(root.resolve("analysis.json"), report);
            var sourceWarnings = new StringBuilder();
            for (String line : utf8(bytes(engine.attempt().resolve("nextflow.log"), 67108864)).lines().toList()) if (line.contains("] WARN ")) sourceWarnings.append(line).append('\n');
            String diagram = sourceWarnings.indexOf("install Graphviz") >= 0 ? "OPTIONAL WORKFLOW DIAGRAM SKIPPED: Graphviz dot is unavailable; the original engine did not render its SVG DAG. If the diagram is needed, install Graphviz only where permitted, declare --dot /absolute/dot in a NEW preparation and rerun to NEW paths. The original warning and available DOT file remain retained.\n" : "";
            Files.writeString(root.resolve("WARNINGS.md"), "# Original ATAC execution: warnings and required actions\n\n"+diagram+ACTION+"\n\nOriginal engine warning lines:\n\n"+sourceWarnings+"\n"+utf8(bytes(engine.attempt().resolve("WARNINGS.md"), 1048576)), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            var pins = new LinkedHashMap<String, Pin>();
            for (String name : Set.of("request.json", "engine-recipe.json", "images.json", "analysis.json", "WARNINGS.md")) pins.put(name, pin(root.resolve(name), 67108864, () -> { }));
            writeJson(root.resolve("manifest.json"), Map.of("pipeline_profile", NfcoreAtacseq121Request.PROFILE, "files", pins, "engine_manifest", engine.manifest(), "runtime_files", manifest(tree(runtime, 10000, 104857600, 67108864, () -> { })), "image_profile", IMAGE_PROFILE, "implementation", implementationPin));
            var marker = Map.of("status", "complete-original-atac-execution-and-selected-output-contracts", "manifest", pin(root.resolve("manifest.json"), 67108864, () -> { }), "whole_paper_reproduced", false);
            writeJson(root.resolve("CANDIDATE.json"), marker); verify(root, "CANDIDATE.json"); containers.verify();
            writeJson(root.resolve("COMPLETED.json"), marker);
            emit(output, "Original nf-core/atacseq1.2.1 execution and selected output contracts completed: "+root+"\n"+ACTION);
            emit(error, "WARNINGS AND REQUIRED ACTIONS: read "+root.resolve("WARNINGS.md")+" and original engine warnings before downstream use.");
            if (!diagram.isEmpty()) emit(error, diagram);
            if (output.checkError() || error.checkError()) throw new IOException("ATAC result status/warning output failed. Retain the complete candidate evidence; no successful adapter result is reported.");
            return 0;
        } catch (Exception | LinkageError failure) {
            int code = failure instanceof NextflowProcess.Unavailable || failure instanceof NextflowRuntime.Unavailable ? 3 : 2;
            String label = code == 3 ? "SKIP3 ANALYSIS SKIPPED" : "FAIL2 NO ATAC RESULT";
            emit(error, label+": "+failure+"\n"+ACTION);
            if (owned && root != null) try {
                writeJson(root.resolve("FAILURE.json"), Map.of("status", label, "error", failure.toString(), "required_action", ACTION));
                Path warning = root.resolve(Files.exists(root.resolve("WARNINGS.md")) ? "WARNINGS_FAILURE.md" : "WARNINGS.md");
                Files.writeString(warning, "# "+label+"\n\n"+failure+"\n\n"+ACTION+"\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { emit(error, "Required action: retain the run directory; diagnostic finalization failed: "+diagnostic); }
            return code;
        }
    }
    private static void capture(Path source, Path target, int maximum) throws IOException {
        Pin before = pin(source, maximum, () -> { }); Files.copy(source, target);
        if (!pin(target, maximum, () -> { }).equals(before) || !pin(source, maximum, () -> { }).equals(before)) throw new IOException("Original ATAC request changed during complete capture");
    }
    static Map<String, Object> verify(Path root) throws IOException {
        return verify(root, "COMPLETED.json");
    }
    private static Map<String, Object> verify(Path root, String marker) throws IOException {
        directory(root);
        if (Files.exists(root.resolve("FAILURE.json"))) throw new IOException("ATAC attempt has a retained failure; no completion fallback");
        Map<?, ?> complete = object(json(root.resolve(marker), 1048576));
        keys(complete, "status", "manifest", "whole_paper_reproduced");
        if (!"complete-original-atac-execution-and-selected-output-contracts".equals(complete.get("status")) || !Boolean.FALSE.equals(complete.get("whole_paper_reproduced")) || !pin(root.resolve("manifest.json"), 67108864, () -> { }).equals(fingerprint(complete.get("manifest"), 67108864))) throw new IOException("Original ATAC completion scope/manifest differs");
        Map<?, ?> manifest = object(json(root.resolve("manifest.json"), 67108864));
        keys(manifest, "pipeline_profile", "files", "engine_manifest", "runtime_files", "image_profile", "implementation");
        if (!NfcoreAtacseq121Request.PROFILE.equals(manifest.get("pipeline_profile"))) throw new IOException("Saved ATAC profile differs");
        if (!fingerprint(manifest.get("implementation"), 1000000000L).equals(pin(root.resolve("adapter.jar"), 1000000000L, () -> { }))) throw new IOException("Saved ATAC implementation differs");
        if (!fingerprint(manifest.get("image_profile"), 1048576).equals(IMAGE_PROFILE)) throw new IOException("Saved original ATAC image profile differs");
        var runtime = entries(manifest.get("runtime_files"), 10000, 67108864);
        var current = tree(root.resolve("runtime-admission"), 10000, 104857600, 67108864, () -> { });
        if (!runtime.keySet().equals(current.keySet())) throw new IOException("Saved ATAC container-health member census differs");
        for (var row : runtime.entrySet()) {
            Entry actual = current.get(row.getKey()), expected = row.getValue();
            if (!actual.kind().equals("file") || !expected.kind().equals("file") || !actual.pin().equals(expected.pin())) throw new IOException("Saved ATAC container health/process evidence differs: "+row.getKey());
        }
        Map<?, ?> health = object(json(root.resolve("runtime-admission/CONTAINER_HEALTH_COMPLETED.json"), 1048576));
        if (!"complete-source-image-namespace-health-only".equals(health.get("status")) || !Boolean.FALSE.equals(health.get("scientific_analysis_executed")) || list(health.get("images")).size() != 1) throw new IOException("Saved original ATAC container health scope differs");
        var imageProfile = object(SimpleJson.parse(utf8(NfcoreAtacseq121Request.resource("images.json", 1048576))));
        var expectedImages = object(imageProfile.get("images"));
        Map<?, ?> healthImage = object(list(health.get("images")).get(0)); String uri = text(healthImage.get("source_uri"));
        if (!expectedImages.containsKey(uri) || !fingerprint(healthImage.get("pin"), 5000000000L).equals(fingerprint(object(expectedImages.get(uri)).get("pin"), 5000000000L))) throw new IOException("Saved original ATAC image identity differs");
        for (String stage : Set.of("version-process", "image-001-health")) {
            Map<?, ?> ledger = object(json(root.resolve("runtime-admission").resolve(stage).resolve("process.json"), 67108864));
            if (!"completed-process-only".equals(ledger.get("status")) || integer(ledger.get("exit_code"), 0, 255) != 0 || !list(ledger.get("owned_live_after_cleanup")).isEmpty() || ledger.containsKey("error") || ledger.containsKey("cleanup_state") || ledger.containsKey("suppressed") && !list(ledger.get("suppressed")).isEmpty()) throw new IOException("Saved original ATAC prerequisite process did not complete cleanly: "+stage);
        }
        Map<?, ?> pins = object(manifest.get("files"));
        if (!pins.keySet().equals(Set.of("request.json", "engine-recipe.json", "images.json", "analysis.json", "WARNINGS.md"))) throw new IOException("Saved ATAC result metadata census differs");
        for (var row : pins.entrySet()) if (!pin(root.resolve(member(text(row.getKey()))), 67108864, () -> { }).equals(fingerprint(row.getValue(), 67108864))) throw new IOException("Saved ATAC result member changed: "+row.getKey());
        var engine = NfcoreEngineBundle.verify(root.resolve("engine"), () -> { });
        if (!engine.manifest().equals(fingerprint(manifest.get("engine_manifest"), 67108864))) throw new IOException("Saved ATAC engine/result binding differs");
        if (!pin(root.resolve("engine-recipe.json"), 4194304, () -> { }).equals(pin(engine.attempt().resolve("request.json"), 4194304, () -> { }))) throw new IOException("Saved ATAC recipe differs from completed engine recipe");
        var actual = NfcoreAtacseq121Audit.read(engine);
        if (!NextflowJson.json(actual).equals(NextflowJson.json(json(root.resolve("analysis.json"), 67108864)))) throw new IOException("Saved ATAC output-contract analysis differs from complete source objects");
        return actual;
    }
    private static void export(Path source, Path target) throws IOException {
        verify(source);
        if (target.startsWith(source) || source.startsWith(target) || target.getParent() == null || !target.getParent().toRealPath().equals(target.getParent())) throw new IOException("Use a disjoint NEW canonical portable result directory");
        Files.createDirectory(target);
        try {
            NfcoreChipseqBundle.copy(NfcoreEngineBundle.verify(source.resolve("engine"), () -> { }).attempt(), target.resolve("engine"));
            NfcoreChipseqBundle.copy(source.resolve("runtime-admission"), target.resolve("runtime-admission"));
            for (String name : Set.of("request.json", "engine-recipe.json", "images.json", "analysis.json", "WARNINGS.md", "manifest.json", "CANDIDATE.json", "adapter.jar")) {
                Path from = source.resolve(name), to = target.resolve(name); Pin before = pin(from, 1000000000L, () -> { });
                Files.copy(from, to);
                if (!pin(to, 1000000000L, () -> { }).equals(before) || !pin(from, 1000000000L, () -> { }).equals(before)) throw new IOException("Complete portable ATAC export changed: "+name);
            }
            verify(target, "CANDIDATE.json");
            Files.copy(source.resolve("COMPLETED.json"), target.resolve("COMPLETED.json")); verify(target);
        } catch (IOException failure) {
            writeJson(target.resolve("FAILURE.json"), Map.of("status", "FAIL2_PORTABLE_EXPORT_NO_RESULT", "error", failure.toString(), "required_action", ACTION));
            throw failure;
        }
    }
    private static void emit(PrintStream stream, String value) { stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8)); }
}
