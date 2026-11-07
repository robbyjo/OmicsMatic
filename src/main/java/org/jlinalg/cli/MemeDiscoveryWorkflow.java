/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Original MEME discovery execution and complete portable scientific result bundles. */
final class MemeDiscoveryWorkflow {
    private MemeDiscoveryWorkflow() { }
    static final String ACTION = "SKIP3: a missing, forbidden or unsupported required runtime, dependency or input capability means skip the affected analysis; install only where permitted. FAIL2: invalid input, changed source/runtime/host, capacity, process, cancellation, capture or finalization failure admits no result. Preserve this attempt and its native worker, read FAILURE.json, WARNINGS.md and the named process.stdout/process.stderr/process.json, correct the reported cause and retry with a NEW output directory. Never use a component's completion as overall analysis admission. Portable verification reads captured files only; it never opens producer paths or authorizes execution on another host. Hashes provide integrity, not authentication or biological accuracy.";
    record Result(Path root, MemeDiscoveryRequest.Request request, MemeDiscoveryOutput.Audit output, NextflowFiles.Pin manifest) { }

    static Result run(Path recipe, Path home, Path buildEvidence, Path out, Runnable check, RsemProcess.Check operationCheck) throws IOException {
        require(!overlap(out, home) && !overlap(out, buildEvidence), "MEME result output must be outside the original installation and source-build evidence; no installation or evidence directory was changed");
        TomtomBundle.create(out);
        Path worker = null; String stage = "request";
        try {
            MemeRuntimeFiles.write(out.resolve("INCOMPLETE.json"), Map.of("status", "INCOMPLETE_NO_RESULT_ADMITTED", "analysis_admitted", false));
            Files.writeString(out.resolve("WARNINGS.md"), "# MEME discovery: analysis incomplete\n\nNO RESULT IS ADMITTED until overall COMPLETED.json exists, INCOMPLETE.json and FAILURE.json are absent, and portable VERIFY succeeds.\n\nREQUIRED ACTION: " + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            operationCheck.verify();
            var original = MemeDiscoveryRequest.read(recipe, false, check);
            if (!System.getProperty("os.name").equals("Linux")) throw new MemeDiscoveryRuntime.Unavailable("Original MEME discovery execution requires permitted Linux x86_64 Java. Windows supports portable VERIFY; skip this execution.");
            stage = "private-input-capture";
            worker = Files.createTempDirectory(Path.of("/var/tmp"), "omicsmatic-meme-discovery-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
            MemeDiscoveryRequest.nativePath(worker);
            Files.createDirectory(worker.resolve("prepared"));
            TomtomBundle.copy(recipe, worker.resolve("prepared/request.json"), original.sourcePin(), check);
            var request = MemeDiscoveryRequest.read(worker.resolve("prepared/request.json"), false, check);
            var inputs = MemeDiscoveryInputs.capture(worker.resolve("prepared/inputs"), request, check);
            require(!overlap(worker, home) && !overlap(worker, buildEvidence) && !overlap(worker, out), "Original MEME worker, runtime, build evidence and destination must be separate trees");
            original.verify(check); operationCheck.verify();
            stage = "runtime-dependency-and-version-admission";
            var runtime = MemeDiscoveryRuntime.prepare(request.profile(), home, buildEvidence, worker.resolve("runtime"), check);
            operationCheck.verify();
            stage = "fresh-scientific-and-full-report-health";
            MemeDiscoveryHealth.run(runtime, check);
            MemeRuntimeFiles.write(runtime.root.resolve("metadata/host.json"), runtime.document());
            original.verify(check); inputs.verify(true, check); operationCheck.verify();
            stage = "original-user-discovery";
            Path fit = worker.resolve("fit");
            var command = request.arguments(home, inputs.primary(), inputs.backgroundFile(), fit.resolve("result"), inputs.population().records().size());
            var fitAccounted = List.of(worker.resolve("prepared"), runtime.root);
            runtime.execute(request, inputs, fit, fitAccounted, check);
            original.verify(check); operationCheck.verify();
            stage = "independent-complete-output-audit";
            var output = MemeDiscoveryOutput.read(fit.resolve("result"), request, inputs, command, check);
            Path render = worker.resolve("render");
            var renderCommand = runtime.htmlArguments(fit.resolve("result/meme.xml"), render.resolve("replayed.html"));
            stage = "original-HTML-replay";
            runtime.nativeRun(renderCommand, render, request.processLimits(), () -> {
                original.verify(check); inputs.verify(true, check); output.verify(request, check); operationCheck.verify();
            }, List.of(worker.resolve("prepared"), runtime.root, fit), check);
            MemeDiscoveryOutput.replay(output, render.resolve("replayed.html"), request, check);
            inputs.verify(true, check); output.verify(request, check); original.verify(check); runtime.verify(check); operationCheck.verify();
            stage = "complete-native-worker-capture";
            var limits = MemeDiscoveryInputs.treeLimits(request);
            var before = MemeRuntimeFiles.tree(worker, limits, check);
            TomtomBundle.copyTree(worker, out.resolve("native"), before, limits, check);
            MemeRuntimeFiles.write(out.resolve("NATIVE-CAPTURE.json"), Map.of("schema_version", 1, "producer_root", worker.toString(), "files", before,
                "native_worker_retained_at_capture", true, "scope", "Complete original worker namespace, bytes and producer modes. Receiver permissions remain independent; portable checks compare bytes and node kinds."));
            var producer = Map.<String, Object>of("schema_version", 1, "profile", request.profile(), "worker", worker.toString(), "home", home.toString(),
                "fit_command", command, "render_command", renderCommand, "native_worker_retained", true,
                "source_recipe", MemeRuntimeFiles.pin(original.sourcePin()), "source_recipe_path", recipe.toString());
            MemeRuntimeFiles.write(out.resolve("producer.json"), producer);
            MemeRuntimeFiles.write(out.resolve("OUTPUT-AUDIT.json"), output.document());
            Files.writeString(out.resolve("WARNINGS.md"), warnings(output, Files.size(fit.resolve("process.stderr")) > 0 || Files.size(render.resolve("process.stderr")) > 0), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            var inventory = new TreeMap<>(MemeRuntimeFiles.tree(out, limits, check)); inventory.remove("INCOMPLETE.json");
            var manifest = Map.<String, Object>of("schema_version", 1, "status", "COMPLETE_CAPTURED_ORIGINAL_MEME_DISCOVERY", "profile", request.profile(),
                "recipe", MemeRuntimeFiles.pin(request.sourcePin()), "files", TomtomBundle.portable(inventory), "analysis_admitted", true,
                "biological_accuracy_certified", false, "scope", "Guarded exact-edition original MEME fit and complete declared output family, independent XML site/scoring/diagram checks, original-renderer HTML replay and portable raw-input/scientific-output replay. Source/build/host receipts retain producer provenance; they are not a signature or a new-host execution ticket.");
            MemeRuntimeFiles.write(out.resolve("manifest.json"), manifest);
            stage = "provisional-portable-verification";
            verifyContent(out, true, check);
            original.verify(check); runtime.verify(check); inputs.verify(true, check); output.verify(request, check); operationCheck.verify();
            require(before.equals(MemeRuntimeFiles.tree(worker, limits, check)), "Original complete MEME worker changed after evidence capture");
            stage = "overall-completion";
            Files.delete(out.resolve("INCOMPLETE.json"));
            var manifestPin = NextflowFiles.pin(out.resolve("manifest.json"), 67108864, check);
            MemeRuntimeFiles.write(out.resolve("COMPLETED.json"), Map.of("schema_version", 1, "status", "COMPLETE_ORIGINAL_MEME_DISCOVERY_ANALYSIS", "analysis_admitted", true, "manifest", MemeRuntimeFiles.pin(manifestPin)));
            return verify(out, check);
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            IOException failure = error instanceof IOException io ? io : new IOException("MEME discovery cancelled or invalid: " + error.getMessage(), error);
            failure(out, worker, stage, failure); throw failure;
        }
    }

    static Result verify(Path root, Runnable check) throws IOException { return verifyContent(root, false, check); }
    private static Result verifyContent(Path root, boolean provisional, Runnable check) throws IOException {
        NextflowFiles.directory(root); check.run();
        require(!Files.exists(root.resolve("FAILURE.json"), LinkOption.NOFOLLOW_LINKS), "Overall MEME discovery retains a failure or skip; no result is admitted");
        if (!provisional) {
            require(!Files.exists(root.resolve("INCOMPLETE.json"), LinkOption.NOFOLLOW_LINKS), "Overall MEME discovery remains incomplete");
            var complete = json(root.resolve("COMPLETED.json"), 65536);
            NextflowFiles.keys(complete, "schema_version", "status", "analysis_admitted", "manifest");
            NextflowFiles.integer(complete.get("schema_version"), 1, 1);
            require("COMPLETE_ORIGINAL_MEME_DISCOVERY_ANALYSIS".equals(complete.get("status")) && Boolean.TRUE.equals(complete.get("analysis_admitted")), "Missing overall MEME discovery completion");
            require(NextflowFiles.pin(root.resolve("manifest.json"), 67108864, check).equals(NextflowFiles.fingerprint(complete.get("manifest"), 67108864)), "Overall MEME manifest identity differs");
        }
        var manifestPin = NextflowFiles.pin(root.resolve("manifest.json"), 67108864, check);
        var manifest = json(root.resolve("manifest.json"), 67108864);
        var request = MemeDiscoveryRequest.read(root.resolve("native/prepared/request.json"), true, check);
        require("COMPLETE_CAPTURED_ORIGINAL_MEME_DISCOVERY".equals(manifest.get("status")) && Boolean.TRUE.equals(manifest.get("analysis_admitted"))
            && Boolean.FALSE.equals(manifest.get("biological_accuracy_certified")) && request.profile().equals(manifest.get("profile"))
            && request.sourcePin().equals(NextflowFiles.fingerprint(manifest.get("recipe"), 2097152)), "Captured MEME overall status/profile/recipe differs");
        verifyInventory(root, manifest.get("files"), request, provisional, check);
        var producer = json(root.resolve("producer.json"), 2097152);
        NextflowFiles.keys(producer, "schema_version", "profile", "worker", "home", "fit_command", "render_command", "native_worker_retained", "source_recipe", "source_recipe_path");
        NextflowFiles.integer(producer.get("schema_version"), 1, 1);
        String worker = NextflowFiles.text(producer.get("worker")), home = NextflowFiles.text(producer.get("home"));
        TomtomRecipe.nativePath(worker); TomtomRecipe.nativePath(home);
        require(!overlap(worker, home) && request.profile().equals(producer.get("profile")) && Boolean.TRUE.equals(producer.get("native_worker_retained"))
            && request.sourcePin().equals(NextflowFiles.fingerprint(producer.get("source_recipe"), 2097152)), "Captured MEME producer/recipe mapping differs");
        var capture = json(root.resolve("NATIVE-CAPTURE.json"), 67108864);
        require(worker.equals(capture.get("producer_root")) && Boolean.TRUE.equals(capture.get("native_worker_retained_at_capture"))
            && NextflowJson.json(TomtomBundle.portable(TomtomBundle.recorded(capture.get("files")))).equals(NextflowJson.json(TomtomBundle.portable(MemeRuntimeFiles.tree(root.resolve("native"), MemeDiscoveryInputs.treeLimits(request), check)))), "Complete captured original MEME worker differs from its producer namespace and byte ledger");
        var inputs = MemeDiscoveryInputs.open(root.resolve("native/prepared/inputs"), request, check);
        var command = request.argumentsRecorded(home, worker + "/prepared/inputs/primary.fa", worker + "/prepared/inputs/background.bg", worker + "/fit/result", inputs.population().records().size());
        var render = renderer(request, home, worker + "/fit/result/meme.xml", worker + "/render/replayed.html");
        require(command.equals(NextflowFiles.strings(producer.get("fit_command"))) && render.equals(NextflowFiles.strings(producer.get("render_command"))), "Complete captured MEME command differs from typed recipe");
        var prepared = List.of(worker + "/prepared", worker + "/runtime");
        TomtomProcessReceipt.verify(root.resolve("native/fit"), command, request.processLimits(), prepared, check);
        TomtomProcessReceipt.verify(root.resolve("native/render"), render, request.processLimits(), List.of(worker + "/prepared", worker + "/runtime", worker + "/fit"), check);
        var output = MemeDiscoveryOutput.read(root.resolve("native/fit/result"), request, inputs, command, check);
        MemeDiscoveryOutput.replay(output, root.resolve("native/render/replayed.html"), request, check);
        require(NextflowJson.json(output.document()).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"), 16777216))), "Complete captured MEME output audit differs");
        verifyRuntime(root.resolve("native/runtime"), worker + "/runtime", home, request, check);
        require(NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("WARNINGS.md"), 1048576)).equals(warnings(output,
            Files.size(root.resolve("native/fit/process.stderr")) > 0 || Files.size(root.resolve("native/render/process.stderr")) > 0)), "Captured MEME warnings or required actions were changed or suppressed");
        verifyInventory(root, manifest.get("files"), request, provisional, check);
        require(manifestPin.equals(NextflowFiles.pin(root.resolve("manifest.json"), 67108864, check)), "Complete MEME manifest changed during verification");
        return new Result(root, request, output, manifestPin);
    }

    private static void verifyRuntime(Path root, String producerRoot, String home, MemeDiscoveryRequest.Request request, Runnable check) throws IOException {
        var host = json(root.resolve("metadata/host.json"), 67108864);
        var build = json(root.resolve("metadata/build/BUILD.json"), 16777216);
        require(Boolean.TRUE.equals(host.get("fresh_scientific_health_passed")) && request.profile().equals(host.get("profile")) && request.version().equals(host.get("version"))
            && home.equals(host.get("home")) && request.profile().equals(build.get("profile")) && request.version().equals(build.get("version")) && home.equals(build.get("compiled_prefix")), "Captured MEME runtime/scientific-health/profile binding differs");
        require(NextflowJson.json(build.get("source_profile")).equals(NextflowJson.json(MemeDiscoveryBuild.sourceProfile(request.profile()))), "Captured original MEME source population differs from the exact pinned edition");
        for (Object item : NextflowFiles.list(build.get("files"))) {
            var row = NextflowFiles.object(item); String member = NextflowFiles.member(NextflowFiles.text(row.get("member")));
            require(!member.contains("/") && NextflowFiles.pin(root.resolve("metadata/build").resolve(member), 268435456, check).equals(NextflowFiles.fingerprint(row.get("identity"), 268435456)), "Captured original MEME build evidence member differs");
        }
        var recipe = MemeDiscoveryRequest.read(root.resolve("health/request.json"), true, check);
        var expected = MemeDiscoveryHealth.recipe(request.profile(), producerRoot + "/health/original-primary.fa", producerRoot + "/health/original-background.bg");
        require(NextflowJson.json(expected).equals(NextflowJson.json(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(recipe.source(), 2097152)), 2097152, 10000))), "Fresh MEME health input/settings contract differs");
        require(MemeDiscoveryHealth.PRIMARY.equals(NextflowFiles.pin(root.resolve("health/original-primary.fa"), 1048576, check))
            && MemeDiscoveryHealth.BACKGROUND.equals(NextflowFiles.pin(root.resolve("health/original-background.bg"), 1048576, check)), "Captured MEME known health raw inputs differ");
        var inputs = MemeDiscoveryInputs.open(root.resolve("health/inputs"), recipe, check);
        var command = recipe.argumentsRecorded(home, producerRoot + "/health/inputs/primary.fa", producerRoot + "/health/inputs/background.bg", producerRoot + "/probes/health-fit/result", inputs.population().records().size());
        var output = MemeDiscoveryOutput.read(root.resolve("probes/health-fit/result"), recipe, inputs, command, check);
        MemeDiscoveryOutput.replay(output, root.resolve("probes/health-report/replayed.html"), recipe, check);
        var reference = NextflowFiles.object(NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(MemeRuntimeFiles.resource(MemeDiscoveryHealth.RESOURCE + "health-reference.json", MemeDiscoveryHealth.REFERENCE)))).get(request.profile()));
        String projection = NextflowJson.json(MemeDiscoveryOutput.projection(output.scientific()));
        require(projection.equals(NextflowJson.json(reference.get("scientific_projection"))), "Complete captured MEME scientific health differs from pinned original controls");
        var receipt = json(root.resolve("metadata/health.json"), 16777216);
        require(request.profile().equals(receipt.get("profile")) && NextflowFiles.fingerprint(receipt.get("reference_resource"), 2097152).equals(MemeDiscoveryHealth.REFERENCE)
            && NextflowJson.json(output.document()).equals(NextflowJson.json(receipt.get("audit")))
            && NextflowFiles.sha(projection.getBytes(StandardCharsets.UTF_8)).equals(receipt.get("complete_scientific_projection_sha256")), "Captured MEME health receipt differs from complete independent replay");
        var healthProcess = json(root.resolve("probes/health-fit/process.json"), 16777216);
        var accounted = NextflowFiles.strings(healthProcess.get("accounted_roots"));
        require(accounted.size() >= 2 && accounted.get(0).equals(producerRoot + "/metadata") && accounted.get(1).equals(producerRoot + "/health"), "Captured MEME health accounting roots differ");
        var probeNames = new java.util.TreeSet<String>();
        try (var paths = Files.list(root.resolve("probes"))) {
            for (Path path : paths.toList()) {
                require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS), "Unexpected non-directory MEME runtime probe");
                String name = path.getFileName().toString();
                if (!name.equals("health-fit") && !name.equals("health-report")) probeNames.add(producerRoot + "/probes/" + name);
            }
        }
        require(accounted.size() == probeNames.size() + 2 && new java.util.HashSet<>(accounted.subList(2, accounted.size())).equals(probeNames), "Captured complete MEME health probe-accounting population differs");
        TomtomProcessReceipt.verify(root.resolve("probes/health-fit"), command, recipe.processLimits(), accounted, check);
        var reportAccounted = new ArrayList<>(accounted); reportAccounted.add(producerRoot + "/probes/health-fit");
        TomtomProcessReceipt.verify(root.resolve("probes/health-report"), renderer(recipe, home, producerRoot + "/probes/health-fit/result/meme.xml", producerRoot + "/probes/health-report/replayed.html"), recipe.processLimits(), reportAccounted, check);
    }
    private static List<String> renderer(MemeDiscoveryRequest.Request request, String home, String xml, String html) {
        return request.legacy() ? List.of(home + "/bin/meme-xml-html", "-xml", xml, "-xsl", home + "/etc/meme-to-html.xsl", "-html", html)
            : List.of(home + "/libexec/meme-" + request.version() + "/meme_xml_to_html", xml, html);
    }
    private static void verifyInventory(Path root, Object expected, MemeDiscoveryRequest.Request request, boolean provisional, Runnable check) throws IOException {
        var actual = new TreeMap<>(MemeRuntimeFiles.tree(root, MemeDiscoveryInputs.treeLimits(request), check));
        require(actual.remove("manifest.json") != null && actual.remove(provisional ? "INCOMPLETE.json" : "COMPLETED.json") != null, "Missing complete MEME terminal namespace");
        require(NextflowJson.json(TomtomBundle.portable(actual)).equals(NextflowJson.json(expected)), "Complete captured MEME namespace, node kinds or file bytes differ");
    }
    private static String warnings(MemeDiscoveryOutput.Audit output, boolean stderr) {
        var text = new StringBuilder("# MEME discovery warnings and required actions\n\nWARNING: This is the explicitly configured original classic-DNA MEME analysis. Author settings, whole MEME-ChIP pipelines, biological validity and whole-paper reproduction are not inferred. Source/build/host receipts describe the producer; copied bundles do not authorize execution on another host.\n\n");
        for (String warning : output.warnings()) text.append("WARNING: ").append(warning).append("\n\n");
        if (stderr) text.append("WARNING: Original fitting or report generation emitted stderr. Read both complete process.stderr files; exit0 does not clear their warnings.\n\n");
        text.append("REQUIRED ACTION: ").append(ACTION).append('\n'); return text.toString();
    }
    private static void failure(Path out, Path worker, String stage, IOException error) {
        try {
            for (String name : List.of("COMPLETED.json", "manifest.json")) if (Files.exists(out.resolve(name), LinkOption.NOFOLLOW_LINKS)) Files.move(out.resolve(name), out.resolve("UNADMITTED-" + name));
            boolean skip = error instanceof MemeDiscoveryRuntime.Unavailable || error instanceof MemeDiscoveryInputs.Unsupported;
            MemeRuntimeFiles.write(out.resolve("FAILURE.json"), Map.of("status", skip ? "SKIP3_ANALYSIS_SKIPPED" : "FAIL2_NO_RESULT_ADMITTED", "analysis_admitted", false,
                "stage", stage, "error", error.toString(), "retained_native_worker", worker == null ? "not-created" : worker.toString(), "required_action", ACTION));
            Files.writeString(out.resolve("WARNINGS.md"), "\n\n# " + (skip ? "SKIP3: ANALYSIS SKIPPED" : "FAIL2: NO RESULT ADMITTED") + "\n\nStage: " + stage + "\n\n" + error + "\n\nRetained native worker: " + worker + "\n\nREQUIRED ACTION: " + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException diagnostic) { error.addSuppressed(diagnostic); }
    }
    private static Map<?, ?> json(Path path, int maximum) throws IOException { return NextflowFiles.object(NextflowFiles.json(path, maximum)); }
    private static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
    private static boolean overlap(String a, String b) { return a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/"); }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
