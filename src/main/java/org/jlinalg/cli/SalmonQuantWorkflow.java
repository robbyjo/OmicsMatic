/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Supervised original bulk quantification with complete captured inputs, output audit and portable replay. */
final class SalmonQuantWorkflow {
    private SalmonQuantWorkflow() { }
    static final String ACTION = "Required action: inspect WARNINGS.md, FAILURE.json when present, metadata/input-audit.json, OUTPUT-AUDIT.json and quant-process/process.stderr/process.stdout plus original quant/logs. "
        + "Require COMPLETED.json and successful complete verification before downstream use. SKIP3 means an exact permitted runtime, helper, resource or requested capability is unavailable: skip dependent analysis and complete only permitted explicit setup. "
        + "FAIL2 means invalid input, changed evidence, process, numerical, capacity, cancellation or finalization failure: no result is admitted. Preserve every partial attempt, correct the named cause and use NEW paths. "
        + "For an explicitly selected original-runtime population, inspect index-bundle/REFERENCE-LEDGER.jsonl: declared-retained coverage can remain false. Never relabel omitted transcripts as declared decoys or impute zero rows. "
        + "TPM is relative abundance and estimated fragment counts can be fractional; neither is raw integer read counts. Original fallback/model warnings remain material. "
        + "Portable verification checks complete captured bytes, typed options, reference/read populations, output contracts and recorded original completion; it does not readmit the producer host, authenticate execution or certify biological accuracy. "
        + "Metadata memory estimates are not JVM heap guarantees. No installation/download occurs during fitting. Cell Ranger remains excluded.";
    record Result(Path root, SalmonQuantRecipe.Request recipe, SalmonQuantInputs.Context inputs, SalmonQuantAudit.Result output, NextflowFiles.Pin manifest) { }

    static Result start(Path recipe, Path home, Path setup, Path root, Runnable check) throws IOException {
        return start(recipe, home, setup, null, root, check);
    }
    /** Controlled batch seam: the native gate's admitted object is freshly rechecked before every fit. */
    static Result startWithAdmitted(Path recipe, SalmonRuntime.Admitted admitted, Path root, Runnable check) throws IOException {
        return start(recipe, admitted.home(), null, admitted, root, check);
    }
    private static Result start(Path recipe, Path home, Path setup, SalmonRuntime.Admitted supplied, Path root, Runnable check) throws IOException {
        boolean fresh = false;
        try {
            SalmonIndexWorkflow.create(root); fresh = true; Files.createDirectory(root.resolve("metadata"));
            var request = SalmonQuantRecipe.read(recipe, check); var process = SalmonQuantRecipe.process(request.capacities());
            require(!overlap(root, home) && (setup == null || !overlap(root, setup)), "Salmon fit work, runtime and setup report must be disjoint");
            SalmonIndexWorkflow.copy(recipe, root.resolve("metadata/recipe.json"), request.sourcePin(), check);
            var captured = SalmonQuantInputs.capture(root, request, check); var inputs = captured.context();
            NextflowFiles.writeJson(root.resolve("metadata/input-audit.json"), inputs.document());
            Files.writeString(root.resolve("WARNINGS.md"), warnings(request, inputs), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            inputs.requireSupportedPopulation(request); preflight(request, inputs);
            SalmonRuntime.Admitted admitted = supplied == null ? SalmonRuntime.admit(request.profile(), home, setup, root.resolve("runtime-admission"), SalmonRuntime.Limits.defaults(), check) : supplied;
            require(request.profile().equals(admitted.profile()), "Current admitted Salmon runtime differs from the exact fit profile"); admitted.verify(check);
            if (supplied != null) {
                require(!overlap(root, admitted.work()), "Supplied Salmon admission evidence and fit work must be disjoint");
                var original = SalmonIndexWorkflow.inventory(admitted.work(), process, check); copyTree(admitted.work(), root.resolve("runtime-admission"), original, check);
                admitted.verify(check); require(original.equals(SalmonIndexWorkflow.inventory(admitted.work(), process, check)), "Native Salmon admission evidence changed during complete capture");
            }
            var environment = new TreeMap<>(admitted.environment()); environment.put("OMP_NUM_THREADS", Integer.toString(request.threads()));
            var command = arguments(request, root.toString(), home.toString());
            var producer = new LinkedHashMap<String, Object>(); producer.put("schema_version", 1); producer.put("profile", request.profile()); producer.put("root", root.toString()); producer.put("home", home.toString());
            producer.put("runtime_producer_root", admitted.work().toString()); producer.put("command", command); producer.put("environment", environment);
            NextflowFiles.writeJson(root.resolve("metadata/producer.json"), producer);
            var metadata = SalmonIndexWorkflow.inventory(root.resolve("metadata"), process, check); var runtime = SalmonIndexWorkflow.inventory(root.resolve("runtime-admission"), process, check);
            var warningPin = NextflowFiles.pin(root.resolve("WARNINGS.md"), 1048576, check);
            RsemProcess.Check integrity = () -> {
                admitted.verify(check); request.verify(check); captured.verify(request, check);
                require(metadata.equals(SalmonIndexWorkflow.inventory(root.resolve("metadata"), process, check)) && runtime.equals(SalmonIndexWorkflow.inventory(root.resolve("runtime-admission"), process, check))
                    && warningPin.equals(NextflowFiles.pin(root.resolve("WARNINGS.md"), 1048576, check)), "Protected Salmon recipe/producer/admission/warnings changed");
            };
            Files.createDirectory(root.resolve("quant")); var scopes = new ArrayList<>(List.of(root.resolve("metadata"), root.resolve("inputs"), root.resolve("runtime-admission"), root.resolve("quant")));
            if (inputs.index() != null) scopes.add(root.resolve("index-bundle"));
            RsemProcess.executeOwned(command, environment, root.resolve("quant-process"), process, integrity, check, scopes);
            integrity.verify(); var beforeOutput = SalmonIndexWorkflow.inventory(root.resolve("quant"), process, check);
            var output = SalmonQuantAudit.audit(root.resolve("quant"), specification(request, inputs, command), check);
            require(output.arithmetic().expectedAssignedFragments() >= request.minimumAssignedFragments(), "Original Salmon assigned-fragment mass is below the explicitly requested minimum");
            require(beforeOutput.equals(SalmonIndexWorkflow.inventory(root.resolve("quant"), process, check)), "Complete original Salmon output changed during audit");
            NextflowFiles.writeJson(root.resolve("OUTPUT-AUDIT.json"), output.document()); integrity.verify();
            String status = status(inputs); var files = SalmonIndexWorkflow.inventory(root, process, check);
            NextflowFiles.writeJson(root.resolve("manifest.json"), Map.of("schema_version", 1, "status", status, "profile", request.profile(), "recipe", SalmonIndexWorkflow.fingerprint(request.sourcePin()), "files", files,
                "declared_retained_population_admitted", inputs.declaredRetainedPopulationMatchesSource(), "selected_population_analysis_admitted", true, "biological_accuracy_certified", false));
            var manifest = NextflowFiles.pin(root.resolve("manifest.json"), request.capacity("metadata_bytes"), check);
            NextflowFiles.writeJson(root.resolve("COMPLETED.json"), Map.of("status", status, "manifest", SalmonIndexWorkflow.fingerprint(manifest), "declared_retained_population_admitted", inputs.declaredRetainedPopulationMatchesSource(), "selected_population_analysis_admitted", true));
            var result = verify(root, check); integrity.verify(); require(manifest.equals(result.manifest()), "Salmon completed fit manifest changed during finalization"); return result;
        } catch (IOException | RuntimeException | Error failed) {
            IOException typed = failed instanceof SalmonRuntime.Unavailable unavailable ? unavailable
                : failed instanceof RsemProcess.UnavailableDependency || failed instanceof AccessDeniedException ? new SalmonRuntime.Unavailable("Required permitted Salmon fitting dependency/capability is unavailable: " + failed.getMessage(), failed)
                : failed instanceof IOException io ? io : new IOException("Salmon fit cancelled or invalid; no result admitted", failed);
            if (fresh) failure(root, typed);
            if (failed instanceof ThreadDeath stopped) throw stopped;
            throw typed;
        }
    }

    static Result verify(Path root, Runnable check) throws IOException {
        NextflowFiles.directory(root); check.run(); require(!Files.exists(root.resolve("FAILURE.json"), LinkOption.NOFOLLOW_LINKS), "Failed Salmon fit cannot admit a passing output prefix");
        Map<?, ?> complete = json(root.resolve("COMPLETED.json"), 8192), manifest = json(root.resolve("manifest.json"), 67108864);
        NextflowFiles.keys(complete, "status", "manifest", "declared_retained_population_admitted", "selected_population_analysis_admitted");
        NextflowFiles.keys(manifest, "schema_version", "status", "profile", "recipe", "files", "declared_retained_population_admitted", "selected_population_analysis_admitted", "biological_accuracy_certified");
        NextflowFiles.integer(manifest.get("schema_version"), 1, 1); var manifestPin = NextflowFiles.pin(root.resolve("manifest.json"), 67108864, check);
        require(manifestPin.equals(NextflowFiles.fingerprint(complete.get("manifest"), 67108864)), "Complete Salmon fit manifest pin differs");
        var request = SalmonQuantRecipe.readCaptured(root.resolve("metadata/recipe.json"), check); var process = SalmonQuantRecipe.process(request.capacities());
        require(manifestPin.bytes() <= request.capacity("metadata_bytes") && request.profile().equals(manifest.get("profile")) && request.sourcePin().equals(NextflowFiles.fingerprint(manifest.get("recipe"), 2097152)), "Complete Salmon fit recipe/profile/metadata binding differs");
        var before = SalmonIndexWorkflow.inventory(root, process, check); var content = new TreeMap<>(before); content.remove("manifest.json"); content.remove("COMPLETED.json");
        require(equivalent(content, NextflowFiles.object(manifest.get("files"))), "Complete Salmon fit namespace/content differs");
        var inputs = SalmonQuantInputs.readCaptured(root, request, check);
        try { inputs.requireSupportedPopulation(request); } catch (SalmonRuntime.Unavailable unsupported) { throw new IOException("Completed Salmon fit violates its saved supported population contract", unsupported); }
        preflight(request, inputs); String status = status(inputs);
        require(status.equals(complete.get("status")) && status.equals(manifest.get("status")) && Boolean.TRUE.equals(complete.get("selected_population_analysis_admitted")) && Boolean.TRUE.equals(manifest.get("selected_population_analysis_admitted"))
            && Boolean.valueOf(inputs.declaredRetainedPopulationMatchesSource()).equals(complete.get("declared_retained_population_admitted")) && Boolean.valueOf(inputs.declaredRetainedPopulationMatchesSource()).equals(manifest.get("declared_retained_population_admitted"))
            && Boolean.FALSE.equals(manifest.get("biological_accuracy_certified")), "Salmon selected/declared-population completion status differs");
        require(NextflowJson.json(inputs.document()).equals(NextflowJson.json(json(root.resolve("metadata/input-audit.json"), (int) request.capacity("metadata_bytes")))), "Recomputed complete Salmon input population differs");
        require(warnings(request, inputs).equals(NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("WARNINGS.md"), 1048576))), "Required Salmon warnings changed or disappeared");
        Map<?, ?> producer = json(root.resolve("metadata/producer.json"), (int) request.capacity("metadata_bytes"));
        NextflowFiles.keys(producer, "schema_version", "profile", "root", "home", "runtime_producer_root", "command", "environment"); NextflowFiles.integer(producer.get("schema_version"), 1, 1);
        String base = NextflowFiles.text(producer.get("root")), home = NextflowFiles.text(producer.get("home")), runtimeRoot = NextflowFiles.text(producer.get("runtime_producer_root"));
        SalmonIndexRecipe.nativePath(base); SalmonIndexRecipe.nativePath(home); SalmonIndexRecipe.nativePath(runtimeRoot);
        var command = arguments(request, base, home); require(request.profile().equals(producer.get("profile")) && command.equals(NextflowFiles.list(producer.get("command"))), "Complete captured Salmon producer command/profile differs");
        var environment = Map.of("PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C", "TZ", "UTC", "LD_LIBRARY_PATH", home + "/lib", "SALMON_NO_VERSION_CHECK", "1", "OMP_NUM_THREADS", Integer.toString(request.threads()));
        require(environment.equals(NextflowFiles.object(producer.get("environment"))), "Complete captured Salmon fitting environment differs");
        process(root, request, command, home, check);
        var output = SalmonQuantAudit.audit(root.resolve("quant"), specification(request, inputs, command), check);
        require(output.arithmetic().expectedAssignedFragments() >= request.minimumAssignedFragments(), "Completed Salmon fit violates the requested assigned-fragment minimum");
        require(NextflowJson.json(output.document()).equals(NextflowJson.json(SalmonMetadata.read(root.resolve("OUTPUT-AUDIT.json"), (int) request.capacity("metadata_bytes"), 1000000))), "Recomputed complete Salmon output audit differs");
        require(before.equals(SalmonIndexWorkflow.inventory(root, process, check)), "Complete Salmon fit changed during portable replay");
        return new Result(root, request, inputs, output, manifestPin);
    }
    private static void process(Path root, SalmonQuantRecipe.Request request, List<String> command, String home, Runnable check) throws IOException {
        Map<?, ?> process = json(root.resolve("quant-process/process.json"), (int) request.capacity("metadata_bytes")); var limits = SalmonQuantRecipe.process(request.capacities());
        require("completed-process-only".equals(process.get("status")) && NextflowFiles.integer(process.get("exit_code"), 0, 0) == 0 && NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty(), "Original Salmon fit was not completed with terminal owned workers");
        var wrapped = new ArrayList<>(List.of("/usr/bin/setsid", "/usr/bin/prlimit", "--as=" + limits.addressSpaceBytes(), "--cpu=" + limits.cpuSeconds(), "--fsize=" + limits.fileBytes(), "--nofile=" + limits.openFiles(), "--core=0", "--")); wrapped.addAll(command);
        require(wrapped.equals(NextflowFiles.list(process.get("command"))) && NextflowJson.json(SimpleJson.parse(RsemJson.json(limits))).equals(NextflowJson.json(process.get("limits"))), "Recorded Salmon process command/capacities differ");
        Map<?, ?> runtime = json(root.resolve("runtime-admission/manifest.json"), (int) request.capacity("metadata_bytes")), complete = json(root.resolve("runtime-admission/COMPLETED.json"), 8192);
        require("NATIVE_RUNTIME_HEALTH_ONLY".equals(runtime.get("status")) && request.profile().equals(runtime.get("profile")) && request.version().equals(runtime.get("version")) && home.equals(runtime.get("home"))
            && Boolean.FALSE.equals(runtime.get("user_analysis_admitted")) && "COMPLETE_NATIVE_RUNTIME_HEALTH_ONLY".equals(complete.get("status")) && Boolean.FALSE.equals(complete.get("user_analysis_admitted"))
            && NextflowFiles.pin(root.resolve("runtime-admission/manifest.json"), request.capacity("metadata_bytes"), check).equals(NextflowFiles.fingerprint(complete.get("manifest"), request.capacity("metadata_bytes"))), "Captured native Salmon runtime admission profile/completion differs");
    }
    private static void preflight(SalmonQuantRecipe.Request request, SalmonQuantInputs.Context inputs) throws IOException {
        long estimate = Math.addExact(262144, Math.multiplyExact(8192L, inputs.targets().size()));
        for (var target : inputs.targets()) estimate = Math.addExact(estimate, Math.multiplyExact(16L, target.name().length()));
        require(Math.addExact(estimate, ((Number) inputs.document().get("estimated_input_metadata_bytes")).longValue()) <= request.capacity("retained_bytes"), "Combined complete Salmon input/output metadata capacity exceeded before fitting");
        require(Math.multiplyExact((long) request.bootstraps(), inputs.targets().size()) <= request.capacity("bootstrap_values"), "Complete requested Salmon bootstrap population exceeds capacity before fitting");
    }
    private static SalmonQuantAudit.Specification specification(SalmonQuantRecipe.Request request, SalmonQuantInputs.Context inputs, List<String> command) throws IOException {
        Map<?, ?> index = inputs.index() == null ? null : SalmonMetadata.read(inputs.root().resolve("index-bundle/index/info.json"), (int) request.capacity("metadata_bytes"), 1000000);
        long remaining = request.capacity("retained_bytes") - ((Number) inputs.document().get("estimated_input_metadata_bytes")).longValue();
        return new SalmonQuantAudit.Specification(request.version(), request.mode() == SalmonQuantRecipe.Mode.TRANSCRIPTOME_ALIGNMENTS, request.paired(), request.libraryType(), request.optimizer(), request.sequenceBias(), request.gcBias(), request.bootstraps(),
            inputs.processedFragments(), inputs.sourceDecoys(), inputs.targets(), SalmonQuantAudit.command(command, request.version()), index, (int) request.capacity("metadata_bytes"), request.capacity("table_characters"), (int) request.capacity("line_characters"), request.capacity("bootstrap_values"), remaining);
    }
    private static List<String> arguments(SalmonQuantRecipe.Request request, String root, String home) throws IOException {
        boolean alignment = request.mode() == SalmonQuantRecipe.Mode.TRANSCRIPTOME_ALIGNMENTS;
        return request.arguments(home + "/bin/salmon", alignment ? null : root + "/index-bundle/index", alignment ? null : root + "/inputs/read1.fastq", request.mode() == SalmonQuantRecipe.Mode.PAIRED_FASTQ ? root + "/inputs/read2.fastq" : null,
            alignment ? root + "/inputs/" + SalmonQuantInputs.alignmentName(request) : null, alignment ? root + "/inputs/" + SalmonQuantInputs.referenceName(request) : null, root + "/quant");
    }
    private static String status(SalmonQuantInputs.Context inputs) { return inputs.declaredRetainedPopulationMatchesSource() ? "COMPLETE_DECLARED_RETAINED_POPULATION" : "COMPLETE_EXPLICIT_SOURCE_RUNTIME_POPULATION"; }
    private static String warnings(SalmonQuantRecipe.Request request, SalmonQuantInputs.Context inputs) {
        return "# Salmon bulk quantification warnings\n\nWARNING: Selected population policy: " + request.population() + ". Declared-retained population matches source: " + inputs.declaredRetainedPopulationMatchesSource()
            + ". Inspect metadata/input-audit.json and the complete reference ledger before interpreting exclusions.\n\nWARNING: Preserve original process stderr/stdout and quant/logs, including bias-model/burn-in/effective-length fallbacks. Runtime synthetic health does not calibrate this user fit. "
            + "Automatic reported library classes and observed orientation counts remain separate. Original1.0.0 mapping lacks two SHA512 metadata links.\n\nWARNING: "
            + (inputs.reads() == null ? "Transcriptome alignments retain the complete exact dictionary and query groups; no genomic projection or biological mate certificate is inferred." : inputs.reads().interpretation().get("quality_strings") + "; mate identity rule verified=" + inputs.reads().mateIdentityRuleVerified() + "; biological mate identity is not certified.")
            + "\n\n" + ACTION + "\n";
    }
    private static void copyTree(Path source, Path destination, Map<String, Object> inventory, Runnable check) throws IOException {
        Files.createDirectory(destination); for (var entry : new TreeMap<>(inventory).entrySet()) { String name = NextflowFiles.member(entry.getKey()); var row = NextflowFiles.object(entry.getValue());
            if (row.get("kind").equals("directory")) Files.createDirectory(destination.resolve(name)); else SalmonIndexWorkflow.copy(source.resolve(name), destination.resolve(name), NextflowFiles.fingerprint(row, 1000000000000L), check); }
    }
    private static boolean equivalent(Map<String, Object> current, Map<?, ?> declared) throws IOException {
        if (!current.keySet().equals(declared.keySet())) return false;
        for (var entry : current.entrySet()) { Map<?, ?> actual = NextflowFiles.object(entry.getValue()), saved = NextflowFiles.object(declared.get(entry.getKey())); String kind = NextflowFiles.text(saved.get("kind"));
            if (kind.equals("file")) NextflowFiles.keys(saved, "kind", "mode", "bytes", "sha256"); else if (kind.equals("directory")) NextflowFiles.keys(saved, "kind", "mode"); else return false;
            NextflowFiles.integer(saved.get("mode"), -1, 0777); if (!kind.equals(actual.get("kind"))) return false;
            if (kind.equals("file") && !NextflowFiles.fingerprint(saved, 1000000000000L).equals(NextflowFiles.fingerprint(actual, 1000000000000L))) return false;
        } return true;
    }
    private static void failure(Path root, IOException failure) {
        try { Files.deleteIfExists(root.resolve("COMPLETED.json")); } catch (IOException revoke) { failure.addSuppressed(revoke); }
        int code = failure instanceof SalmonRuntime.Unavailable ? 3 : 2; String status = code == 3 ? "SKIP3 — ANALYSIS SKIPPED" : "FAIL2 — NO RESULT ADMITTED";
        try { NextflowFiles.writeJson(root.resolve("FAILURE.json"), Map.of("status", status, "exit_code", code, "error", failure.toString(), "required_action", ACTION));
            Path warnings = root.resolve("WARNINGS.md"); if (Files.exists(warnings)) Files.move(warnings, root.resolve("WARNINGS-before-failure.md"));
            Files.writeString(warnings, "# Salmon " + status + "\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
    }
    private static Map<?, ?> json(Path path, int maximum) throws IOException { return NextflowFiles.object(NextflowFiles.json(path, maximum)); }
    private static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
