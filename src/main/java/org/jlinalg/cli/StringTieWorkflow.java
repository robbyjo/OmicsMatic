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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Typed original fitting and complete portable captured verification, with guarded finalization. */
final class StringTieWorkflow {
    private StringTieWorkflow() { }
    static final String ACTION = "Read WARNINGS.md, metadata/OUTPUT-AUDIT.json and fit/process.stderr plus fit/process.json. SKIP3: a required permitted runtime, helper, library or capability is unavailable; skip dependent analysis until exact permitted setup succeeds. FAIL2: invalid/changed inputs, failed process/audit, cancellation or capacity failure; no result is admitted. Preserve all attempts, correct the named cause and use NEW output paths. Verification rechecks complete captured inputs and outputs without opening recorded producer paths; hashes prove integrity, not authenticity or biological accuracy. Cell Ranger remains excluded.";
    record Result(Path root, String mode, long transcripts) { }
    static Result start(Path recipe, Path home, Path setupReport, Path root, Runnable check) throws IOException {
        boolean created = false; String stage = "request";
        try {
            if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch").toLowerCase(java.util.Locale.ROOT))) throw new StringTieRuntime.Unavailable("Original StringTie fitting requires Linux x86_64 Java; use permitted Linux Java inside WSL on Windows, or skip dependent analysis. Captured --verify works on Windows.");
            var recipeIdentity = NextflowFiles.identity(recipe, 2097152, check); var request = StringTieRecipe.read(recipe, false, check);
            require(!overlap(root, home) && !overlap(root, setupReport), "StringTie output must be disjoint from the runtime and original setup report");
            var originals = new LinkedHashMap<Path, NextflowFiles.Identity>(); originals.put(recipe, recipeIdentity);
            var members = StringTieInputs.members(request); long bytes = recipeIdentity.pin().bytes();
            for (String source : members.values()) {
                Path file = Path.of(source); require(!overlap(root, file), "StringTie output aliases an input"); var identity = NextflowFiles.identity(file, request.capacity("input_bytes"), check); originals.put(file, identity); bytes = Math.addExact(bytes, identity.pin().bytes());
            }
            require(bytes <= request.capacity("input_bytes"), "Complete combined StringTie input bytes exceed the explicit capacity");
            var originalSetup = StringTieSetupEvidence.read(setupReport, StringTieSetupEvidence.Limits.defaults(), check);
            BigWigFiles.create(root); created = true;
            write(root.resolve("INCOMPLETE.json"), Map.of("status", "STRINGTIE_WORKFLOW_IN_PROGRESS", "user_analysis_admitted", false), request);
            Files.createDirectory(root.resolve("metadata")); Files.createDirectory(root.resolve("inputs")); Files.createDirectory(root.resolve("setup-report"));
            BigWigFiles.copy(recipe, root.resolve("metadata/recipe.json"), recipeIdentity.pin(), check);
            var sourcePins = new TreeMap<String, Object>();
            for (var entry : members.entrySet()) { Path file = Path.of(entry.getValue()); var identity = originals.get(file); BigWigFiles.copy(file, root.resolve(entry.getKey()), identity.pin(), check); sourcePins.put(entry.getKey(), Map.of("producer_source", file.toString(), "pin", MemeRuntimeFiles.pin(identity.pin()))); }
            for (String name : originalSetup.inventory().keySet()) if (!name.equals(".")) { Path source = setupReport.resolve(name); BigWigFiles.copy(source, root.resolve("setup-report").resolve(name), NextflowFiles.pin(source, 1048576, check), check); }
            originalSetup.verify(StringTieSetupEvidence.Limits.defaults(), check);
            String producer = StringTieRecipe.opaque(root.toString()); String producerHome = StringTieRecipe.opaque(home.toString());
            write(root.resolve("metadata/request.json"), Map.of("schema_version", 1, "producer_root", producer, "producer_home", producerHome, "producer_recipe", recipe.toString(), "producer_setup_report", setupReport.toString(), "recipe", MemeRuntimeFiles.pin(recipeIdentity.pin()), "mode", request.mode().name(), "profile", request.profile()), request);
            write(root.resolve("metadata/source-pins.json"), sourcePins, request); write(root.resolve("metadata/policy.json"), Map.of("limits", request.limits(), "parameters", request.parameters(), "threads", request.threads(), "unset_parameters", "exact-original-edition-defaults"), request);
            stage = "complete captured input audit";
            var data = StringTieInputs.audit(root, request, producer, check); write(root.resolve("metadata/INPUT-AUDIT.json"), data.audit(), request);
            stage = "current-host exact runtime and fresh health admission";
            var runtime = StringTieRuntime.admit(request.profile(), home, root.resolve("setup-report"), root.resolve("runtime"), StringTieRuntime.Limits.defaults(), check);
            Map<String, Object> protectedInputs = tree(root.resolve("inputs"), request, check), protectedMetadata = tree(root.resolve("metadata"), request, check);
            RsemProcess.Check integrity = () -> { verifyOriginals(originals, originalSetup, check); runtime.verify(check); require(protectedInputs.equals(tree(root.resolve("inputs"), request, check)) && protectedMetadata.equals(tree(root.resolve("metadata"), request, check)), "Prepared StringTie inputs/metadata changed"); };
            stage = "supervised original " + request.mode().name();
            RsemProcess.executeOwned(request.arguments(home.resolve("bin/stringtie").toString(), producer), runtime.environment(), root.resolve("fit"), StringTieRecipe.process(request.limits()), integrity, check,
                List.of(root.resolve("metadata"), root.resolve("inputs"), root.resolve("setup-report"), root.resolve("runtime")));
            integrity.verify(); stage = "complete native fit output audit";
            write(root.resolve("metadata/OUTPUT-AUDIT.json"), StringTieOutput.audit(root.resolve("fit"), request, data, check), request);
            Files.writeString(root.resolve("WARNINGS.md"), warnings(request), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            var files = new TreeMap<>(tree(root, request, check)); files.remove("INCOMPLETE.json");
            require(total(files) <= request.capacity("workspace_bytes") - 16777216, "Complete StringTie evidence leaves insufficient16MiB finalization reserve; preserve attempt and explicitly review workspace capacity");
            stage = "complete captured verification and finalization";
            write(root.resolve("manifest.json"), Map.of("schema_version", 1, "status", "COMPLETE_STRINGTIE_CAPTURED_FIT", "profile", request.profile(), "mode", request.mode().name(), "files", files, "user_analysis_admitted", true, "biological_accuracy_certified", false), request);
            write(root.resolve("COMPLETED.json"), Map.of("status", "COMPLETE_STRINGTIE_CAPTURED_FIT", "manifest", MemeRuntimeFiles.pin(NextflowFiles.pin(root.resolve("manifest.json"), request.capacity("metadata_bytes"), check)), "user_analysis_admitted", true), request);
            var result = verify(root, true, check); var guarded = tree(root, request, check); verifyOriginals(originals, originalSetup, check); runtime.verify(check);
            require(guarded.equals(tree(root, request, check)), "StringTie complete result changed before final commit"); Files.delete(root.resolve("INCOMPLETE.json")); return result;
        } catch (RsemProcess.UnavailableDependency | AccessDeniedException error) {
            var unavailable = new StringTieRuntime.Unavailable("Required StringTie execution/access capability is unavailable at " + stage + "; skip dependent analysis", error); if (created) failure(root, stage, unavailable); throw unavailable;
        } catch (IOException | RuntimeException | Error error) { if (created) failure(root, stage, error); throw error; }
    }
    static Result verify(Path root, Runnable check) throws IOException { return verify(root, false, check); }
    private static Result verify(Path root, boolean precommit, Runnable check) throws IOException {
        var request = StringTieRecipe.read(root.resolve("metadata/recipe.json"), true, check); int maximum = Math.toIntExact(request.capacity("metadata_bytes"));
        Map<?, ?> completion = json(root.resolve("COMPLETED.json"), maximum), manifest = json(root.resolve("manifest.json"), maximum);
        NextflowFiles.keys(completion, "status", "manifest", "user_analysis_admitted"); NextflowFiles.keys(manifest, "schema_version", "status", "profile", "mode", "files", "user_analysis_admitted", "biological_accuracy_certified");
        require("COMPLETE_STRINGTIE_CAPTURED_FIT".equals(completion.get("status")) && Boolean.TRUE.equals(completion.get("user_analysis_admitted")) && "COMPLETE_STRINGTIE_CAPTURED_FIT".equals(manifest.get("status")) && Boolean.TRUE.equals(manifest.get("user_analysis_admitted")) && Boolean.FALSE.equals(manifest.get("biological_accuracy_certified")), "StringTie completion/schema/scope differs");
        NextflowFiles.integer(manifest.get("schema_version"), 1, 1); require(request.profile().equals(manifest.get("profile")) && request.mode().name().equals(manifest.get("mode")), "Captured StringTie edition/mode differs");
        var manifestPin = NextflowFiles.pin(root.resolve("manifest.json"), maximum, check); require(manifestPin.equals(NextflowFiles.fingerprint(completion.get("manifest"), maximum)), "Captured StringTie manifest hash differs");
        inventory(root, request, manifest.get("files"), precommit, check);
        Map<?, ?> metadata = json(root.resolve("metadata/request.json"), maximum); NextflowFiles.keys(metadata, "schema_version", "producer_root", "producer_home", "producer_recipe", "producer_setup_report", "recipe", "mode", "profile");
        NextflowFiles.integer(metadata.get("schema_version"), 1, 1); String producer = StringTieRecipe.opaque(NextflowFiles.text(metadata.get("producer_root"))), home = StringTieRecipe.opaque(NextflowFiles.text(metadata.get("producer_home")));
        StringTieRecipe.opaque(NextflowFiles.text(metadata.get("producer_recipe"))); StringTieRecipe.opaque(NextflowFiles.text(metadata.get("producer_setup_report")));
        require(request.profile().equals(metadata.get("profile")) && request.mode().name().equals(metadata.get("mode")) && NextflowFiles.pin(root.resolve("metadata/recipe.json"), 2097152, check).equals(NextflowFiles.fingerprint(metadata.get("recipe"), 2097152)), "Captured original StringTie recipe declaration differs");
        Map<?, ?> pins = json(root.resolve("metadata/source-pins.json"), maximum); var members = StringTieInputs.members(request); require(pins.keySet().equals(members.keySet()), "Captured input population differs"); long bytes = NextflowFiles.pin(root.resolve("metadata/recipe.json"), 2097152, check).bytes();
        for (var e : members.entrySet()) {
            Map<?, ?> row = NextflowFiles.object(pins.get(e.getKey())); NextflowFiles.keys(row, "producer_source", "pin"); require(e.getValue().equals(row.get("producer_source")), "Captured source provenance role differs");
            var pin = NextflowFiles.pin(root.resolve(e.getKey()), request.capacity("input_bytes"), check); require(pin.equals(NextflowFiles.fingerprint(row.get("pin"), request.capacity("input_bytes"))), "Complete captured input hash differs"); bytes = Math.addExact(bytes, pin.bytes());
        }
        require(bytes <= request.capacity("input_bytes"), "Complete combined captured input bytes exceed capacity");
        require(same(Map.of("limits", request.limits(), "parameters", request.parameters(), "threads", request.threads(), "unset_parameters", "exact-original-edition-defaults"), json(root.resolve("metadata/policy.json"), maximum)), "Captured complete StringTie policy differs");
        var setup = StringTieSetupEvidence.read(root.resolve("setup-report"), StringTieSetupEvidence.Limits.defaults(), check); require(request.profile().equals(setup.profile()) && home.equals(setup.producerHome()), "Captured setup profile/home differs");
        StringTieCapturedRuntime.verify(root.resolve("runtime"), request, producer, home, check);
        var data = StringTieInputs.audit(root, request, producer, check); require(same(data.audit(), json(root.resolve("metadata/INPUT-AUDIT.json"), maximum)), "Complete captured native input audit differs");
        var output = StringTieOutput.audit(root.resolve("fit"), request, data, check); require(same(output, json(root.resolve("metadata/OUTPUT-AUDIT.json"), maximum)), "Complete captured native output audit differs");
        verifyProcess(root, request, producer, home, check);
        require(NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("WARNINGS.md"), maximum)).equals(warnings(request)), "Complete StringTie warnings/recovery instructions differ");
        inventory(root, request, manifest.get("files"), precommit, check); require(manifestPin.equals(NextflowFiles.pin(root.resolve("manifest.json"), maximum, check)), "StringTie manifest changed during verification");
        return new Result(root, request.mode().name(), NextflowFiles.integer(output.get("transcripts"), 0, request.capacity("gtf_transcripts")));
    }
    private static void verifyProcess(Path root, StringTieRecipe.Request request, String producer, String home, Runnable check) throws IOException {
        int maximum = Math.toIntExact(request.capacity("metadata_bytes")); Map<?, ?> process = json(root.resolve("fit/process.json"), maximum), launch = json(root.resolve("fit/launch.json"), maximum);
        var limits = StringTieRecipe.process(request.limits()); var expected = new ArrayList<>(List.of("/usr/bin/setsid", "/usr/bin/prlimit", "--as=" + limits.addressSpaceBytes(), "--cpu=" + limits.cpuSeconds(), "--fsize=" + limits.fileBytes(), "--nofile=" + limits.openFiles(), "--core=0", "--")); expected.addAll(request.arguments(home + "/bin/stringtie", producer));
        require(same(expected, process.get("command")) && same(expected, launch.get("command")) && "completed-process-only".equals(process.get("status")) && "strict-zero-exit-process".equals(process.get("process_purpose")) && NextflowFiles.integer(process.get("exit_code"), 0, 0) == 0 && NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty(), "Captured original StringTie fit command/exit/cleanup differs");
        require(same(SimpleJson.parse(RsemJson.json(limits)), process.get("limits")) && same(List.of(0), process.get("accepted_process_exit_codes")), "Captured original StringTie fit resource/exit policy differs"); check.run();
    }
    private static void inventory(Path root, StringTieRecipe.Request request, Object expected, boolean precommit, Runnable check) throws IOException {
        var actual = new TreeMap<>(tree(root, request, check)); var names = new HashSet<>(Set.of(".", "inputs", "metadata", "setup-report", "runtime", "fit", "WARNINGS.md", "manifest.json", "COMPLETED.json", "metadata/recipe.json", "metadata/request.json", "metadata/source-pins.json", "metadata/policy.json", "metadata/INPUT-AUDIT.json", "metadata/OUTPUT-AUDIT.json"));
        names.addAll(StringTieInputs.members(request).keySet());
        for (String n : List.of("process.json", "launch.json", "process.stdout", "process.stderr", "WARNINGS.md", "transcripts.gtf")) names.add("fit/" + n);
        if (request.mode() == StringTieRecipe.Mode.ESTIMATE) for (String n : StringTieHealth.TABLES) names.add("fit/" + n);
        for (String n : actual.keySet()) if (n.startsWith("runtime/") || n.startsWith("setup-report/")) names.add(n);
        if (precommit) names.add("INCOMPLETE.json"); require(actual.keySet().equals(names), "StringTie result has extra/missing files, directories, partial guards or failure markers");
        actual.remove("manifest.json"); actual.remove("COMPLETED.json"); if (precommit) actual.remove("INCOMPLETE.json");
        require(same(BigWigFiles.portable(actual), BigWigFiles.portable(expected)), "Complete captured StringTie namespace, file types or bytes differ");
    }
    private static String warnings(StringTieRecipe.Request request) {
        return "# StringTie warnings and required actions\n\nWARNING: Exact original " + request.version() + " " + request.mode() + "; declared build=" + request.genomeBuild() + ". Original input bytes, coordinates and output decimals are retained. No aliases, sorting, clipping, read filtering, correction of paper flags or missing-value zero filling occurs. Build labels do not authenticate an assembly.\n\n"
            + "WARNING: Unset thresholds use this exact original edition's defaults. Settings are explicit in metadata/recipe.json and metadata/policy.json. Original edition differences and unknown paper settings remain unresolved; this is not complete paper replication. Strand=" + request.strand() + ". Alignment orientation and ts do not fill XS. NH/XS/sort policies=" + (request.bamPolicies() == null ? "not applicable" : request.bamPolicies()) + ". Mate reciprocity and source usable-read inference are not certified.\n\n"
            + "WARNING: Estimation population=" + (request.population() == null ? "not applicable" : request.population()) + ". RETAIN_OMISSIONS preserves explicit missing transcripts without invented zeros. Gene-reduction residuals are diagnostic, not forced equalities. Native denominator bounds are conservative constraints; hidden normalization quantities and biological accuracy are not recovered or certified. Assembly/merge outputs are structurally audited; transcript discovery or merging is performed by the exact original runtime. Empty output is explicit and is not biological absence.\n\n"
            + "WARNING: Inspect every original warning in fit/process.stderr, fit/WARNINGS.md and runtime process logs. Source GTF comments, table precision and all supplied records remain captured. No counts are imported from a different quantifier. SAM, CRAM, CG long-CIGAR transport and adjacent guide-exon normalization need separate admission; supply supported complete inputs rather than silently converting them.\n\n"
            + "WARNING: Complete parsing and process/workspace limits are retained; they do not guarantee available JVM heap. Aggregate process RSS is sampled. Copied-file modes are not certificates of producer permissions. Portable verification reads captured sources and results only and does not reexecute the original fit or attest the current live producer host.\n\n" + ACTION + "\n";
    }
    private static void verifyOriginals(Map<Path, NextflowFiles.Identity> originals, StringTieSetupEvidence.Receipt setup, Runnable check) throws IOException { for (var e : originals.entrySet()) require(e.getValue().equals(NextflowFiles.identity(e.getKey(), e.getValue().pin().bytes(), check)), "Original StringTie input changed: " + e.getKey()); setup.verify(StringTieSetupEvidence.Limits.defaults(), check); }
    private static Map<String, Object> tree(Path root, StringTieRecipe.Request request, Runnable check) throws IOException { return MemeRuntimeFiles.tree(root, new MemeRuntimeFiles.Limits(request.capacity("file_bytes"), request.capacity("workspace_bytes"), Math.toIntExact(request.capacity("files"))), check); }
    private static long total(Map<String, Object> files) throws IOException { long bytes = 0; for (Object value : files.values()) { Map<?, ?> row = NextflowFiles.object(value); if (row.get("kind").equals("file")) bytes = Math.addExact(bytes, NextflowFiles.fingerprint(row, 1099511627776L).bytes()); } return bytes; }
    private static void write(Path file, Object value, StringTieRecipe.Request request) throws IOException { byte[] raw = (NextflowJson.json(value) + "\n").getBytes(StandardCharsets.UTF_8); require(raw.length <= request.capacity("metadata_bytes"), "Complete StringTie metadata exceeds declared capacity"); Files.write(file, raw, StandardOpenOption.CREATE_NEW); }
    private static Map<?, ?> json(Path file, int maximum) throws IOException { return NextflowFiles.object(NextflowFiles.json(file, maximum)); }
    static boolean same(Object a, Object b) throws IOException { return SimpleJson.parse(NextflowJson.json(a)).equals(SimpleJson.parse(NextflowJson.json(b))); }
    private static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
    private static void failure(Path root, String stage, Throwable error) {
        try {
            for (String name : List.of("manifest.json", "COMPLETED.json")) if (Files.exists(root.resolve(name), LinkOption.NOFOLLOW_LINKS)) Files.move(root.resolve(name), root.resolve("UNADMITTED-" + name));
            if (!Files.exists(root.resolve("INCOMPLETE.json"))) NextflowFiles.writeJson(root.resolve("INCOMPLETE.json"), Map.of("status", "FAILED_STRINGTIE_FIT"));
            boolean skip = error instanceof StringTieRuntime.Unavailable;
            NextflowFiles.writeJson(root.resolve("FAILURE.json"), Map.of("status", skip ? "SKIP3_DEPENDENT_ANALYSIS_SKIPPED" : "FAIL2_NO_RESULT_ADMITTED", "stage", stage, "error", error.toString(), "user_analysis_admitted", false, "required_action", ACTION));
            Files.writeString(root.resolve("WARNINGS.md"), "\n\n# " + (skip ? "SKIP3 — ANALYSIS SKIPPED" : "FAIL2 — NO RESULT ADMITTED") + "\n\nStage: " + stage + "\n\n" + error + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException diagnostic) { error.addSuppressed(diagnostic); }
    }
    private static void require(boolean value, String reason) throws IOException { if (!value) throw new IOException(reason); }
}
