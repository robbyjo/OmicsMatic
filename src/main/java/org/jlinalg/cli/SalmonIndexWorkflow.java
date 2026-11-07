/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Original supervised dense-index build and complete portable captured-reference verification. */
final class SalmonIndexWorkflow {
    private SalmonIndexWorkflow() { }
    static final String ACTION = "Required action: inspect WARNINGS.md, FAILURE.json when present, REFERENCE-LEDGER.jsonl and original index-process/process.stderr and process.stdout. "
        + "SKIP3 means an exact permitted runtime, helper or requested capability is unavailable: skip dependent analysis and complete only permitted setup. "
        + "FAIL2 means invalid input, changed evidence, process, capacity, cancellation or finalization failure: no result is admitted. Preserve the attempt and use NEW paths after correction. "
        + "An index is not an expression result. Short-reference/decoy disagreements remain explicit; no missing expression row is imputed. Portable verification checks complete captured bytes and contracts, "
        + "not the current producer host, graph mathematical equivalence, execution authenticity or biological accuracy. No installation/download occurs during index construction.";
    record Result(Path root, SalmonIndexRecipe.Request recipe, SalmonIndexAudit.Result audit, NextflowFiles.Pin manifest) { }

    static Result start(Path recipePath, Path home, Path setup, Path root, Runnable check) throws IOException {
        boolean fresh = false;
        try {
            create(root); fresh = true;
            var request = SalmonIndexRecipe.read(recipePath, check);
            require(!overlap(root, home) && !overlap(root, setup), "Salmon index work, runtime home and setup report must be disjoint");
            for (String name : List.of("inputs", "metadata", "index")) Files.createDirectory(root.resolve(name));
            var original = SalmonReferences.inspect(request.fasta(), request.encoding(), request.headers(), request.decoys(), request.limits().references(), check);
            Path capturedRecipe = root.resolve("metadata/recipe.json"), fasta = root.resolve("inputs/" + request.capturedFastaName());
            copy(recipePath, capturedRecipe, request.sourcePin(), check); copy(request.fasta(), fasta, original.fastaPin(), check);
            Path decoys = request.decoys() == null ? null : root.resolve("inputs/decoys.txt");
            if (decoys != null) copy(request.decoys(), decoys, original.decoyPin(), check);
            var captured = SalmonReferences.inspect(fasta, request.encoding(), request.headers(), decoys, request.limits().references(), check);
            require(original.census().equals(captured.census()) && original.references().equals(captured.references()), "Original/captured Salmon reference populations differ");
            request.verify(check); original.verify(check);
            var admitted = SalmonRuntime.admit(request.profile(), home, setup, root.resolve("runtime-admission"), SalmonRuntime.Limits.defaults(), check);
            var environment = new TreeMap<>(admitted.environment()); environment.put("OMP_NUM_THREADS", Integer.toString(request.threads()));
            var command = request.arguments(home.resolve("bin/salmon").toString(), fasta.toString(), decoys == null ? null : decoys.toString(), root.resolve("index").toString());
            var producer = new LinkedHashMap<String, Object>(); producer.put("schema_version", 1); producer.put("profile", request.profile()); producer.put("root", root.toString());
            producer.put("home", home.toString()); producer.put("command", command); producer.put("environment", environment); producer.put("user_expression_analysis_admitted", false);
            NextflowFiles.writeJson(root.resolve("metadata/producer.json"), producer);
            var protectedInputs = inventory(root.resolve("inputs"), request.limits().process(), check);
            var protectedMetadata = inventory(root.resolve("metadata"), request.limits().process(), check);
            RsemProcess.Check integrity = () -> {
                admitted.verify(check); request.verify(check); original.verify(check); captured.verify(check);
                require(protectedInputs.equals(inventory(root.resolve("inputs"), request.limits().process(), check)) && protectedMetadata.equals(inventory(root.resolve("metadata"), request.limits().process(), check)), "Captured Salmon input/recipe/producer evidence changed");
            };
            RsemProcess.executeOwned(command, environment, root.resolve("index-process"), request.limits().process(), integrity, check,
                List.of(root.resolve("inputs"), root.resolve("metadata"), root.resolve("index"), admitted.work()));
            integrity.verify();
            var audit = SalmonIndexAudit.read(request, captured, root.resolve("index"), check);
            NextflowFiles.writeJson(root.resolve("INDEX-AUDIT.json"), audit.summary());
            try (var output = Files.newOutputStream(root.resolve("REFERENCE-LEDGER.jsonl"), StandardOpenOption.CREATE_NEW)) {
                SalmonIndexAudit.ledger(audit, output, request.limits().process().fileBytes(), check);
            }
            Files.writeString(root.resolve("WARNINGS.md"), warnings(audit), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            integrity.verify(); var files = inventory(root, request.limits().process(), check);
            NextflowFiles.writeJson(root.resolve("manifest.json"), Map.of("schema_version", 1, "status", "COMPLETE_ORIGINAL_INDEX_ONLY", "profile", request.profile(), "files", files,
                "recipe", fingerprint(request.sourcePin()), "expression_analysis_admitted", false));
            NextflowFiles.Pin manifest = NextflowFiles.pin(root.resolve("manifest.json"), request.limits().metadataBytes(), check);
            NextflowFiles.writeJson(root.resolve("COMPLETED.json"), Map.of("status", "COMPLETE_ORIGINAL_INDEX_ONLY", "manifest", fingerprint(manifest), "expression_analysis_admitted", false));
            Result result = verify(root, check); integrity.verify();
            require(manifest.equals(result.manifest()), "Salmon index final manifest changed"); return result;
        } catch (IOException | RuntimeException failed) {
            IOException typed = failed instanceof SalmonRuntime.Unavailable unavailable ? unavailable
                : failed instanceof RsemProcess.UnavailableDependency || failed instanceof AccessDeniedException ? new SalmonRuntime.Unavailable("Permitted Salmon index dependency/capability unavailable: " + failed.getMessage(), failed)
                : failed instanceof IOException io ? io : new IOException("Salmon index cancelled or invalid; no result admitted", failed);
            if (fresh) failure(root, typed); throw typed;
        }
    }

    /** Portable verification opens captured members only; original producer paths remain text. */
    static Result verify(Path root, Runnable check) throws IOException {
        NextflowFiles.directory(root); check.run();
        require(!Files.exists(root.resolve("FAILURE.json"), LinkOption.NOFOLLOW_LINKS), "Failed Salmon index attempt cannot admit a passing prefix");
        Map<?, ?> completed = json(root.resolve("COMPLETED.json"), 8192), manifest = json(root.resolve("manifest.json"), 67108864);
        NextflowFiles.keys(completed, "status", "manifest", "expression_analysis_admitted");
        NextflowFiles.keys(manifest, "schema_version", "status", "profile", "files", "recipe", "expression_analysis_admitted");
        require("COMPLETE_ORIGINAL_INDEX_ONLY".equals(completed.get("status")) && "COMPLETE_ORIGINAL_INDEX_ONLY".equals(manifest.get("status"))
            && Boolean.FALSE.equals(completed.get("expression_analysis_admitted")) && Boolean.FALSE.equals(manifest.get("expression_analysis_admitted")), "Salmon index completion status is not complete index-only evidence");
        NextflowFiles.integer(manifest.get("schema_version"), 1, 1);
        var manifestPin = NextflowFiles.pin(root.resolve("manifest.json"), 67108864, check);
        require(manifestPin.equals(NextflowFiles.fingerprint(completed.get("manifest"), 67108864)), "Salmon index manifest pin differs");
        var request = SalmonIndexRecipe.readCaptured(root.resolve("metadata/recipe.json"), check);
        require(request.profile().equals(manifest.get("profile")) && request.sourcePin().equals(NextflowFiles.fingerprint(manifest.get("recipe"), 2097152)), "Captured Salmon index recipe/profile binding differs");
        require(manifestPin.bytes() <= request.limits().metadataBytes(), "Complete Salmon index manifest exceeds declared metadata capacity");
        var before = inventory(root, request.limits().process(), check); var content = new TreeMap<>(before); content.remove("manifest.json"); content.remove("COMPLETED.json");
        require(equivalentInventory(content, NextflowFiles.object(manifest.get("files"))), "Complete Salmon index bundle namespace/content differs");
        Path fasta = root.resolve("inputs/" + request.capturedFastaName()), decoys = request.decoySource() == null ? null : root.resolve("inputs/decoys.txt");
        var inputs = SalmonReferences.inspect(fasta, request.encoding(), request.headers(), decoys, request.limits().references(), check);
        var audit = SalmonIndexAudit.read(request, inputs, root.resolve("index"), check);
        require(NextflowJson.json(audit.summary()).equals(NextflowJson.json(json(root.resolve("INDEX-AUDIT.json"), request.limits().metadataBytes()))), "Recomputed complete Salmon index audit differs");
        var ledger = SalmonIndexAudit.ledger(audit, OutputStream.nullOutputStream(), request.limits().process().fileBytes(), check);
        require(ledger.equals(NextflowFiles.pin(root.resolve("REFERENCE-LEDGER.jsonl"), request.limits().process().fileBytes(), check)), "Recomputed complete Salmon input/source-reference ledger differs");
        require(warnings(audit).equals(NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("WARNINGS.md"), 1048576))), "Salmon index warnings were changed/removed");
        verifyProcess(root, request);
        require(before.equals(inventory(root, request.limits().process(), check)), "Salmon index bundle changed during complete portable verification");
        return new Result(root, request, audit, manifestPin);
    }
    private static void verifyProcess(Path root, SalmonIndexRecipe.Request request) throws IOException {
        Map<?, ?> producer = json(root.resolve("metadata/producer.json"), request.limits().metadataBytes());
        NextflowFiles.keys(producer, "schema_version", "profile", "root", "home", "command", "environment", "user_expression_analysis_admitted");
        NextflowFiles.integer(producer.get("schema_version"), 1, 1);
        require(request.profile().equals(producer.get("profile")) && Boolean.FALSE.equals(producer.get("user_expression_analysis_admitted")), "Salmon index producer profile/status differs");
        String base = NextflowFiles.text(producer.get("root")), home = NextflowFiles.text(producer.get("home")); SalmonIndexRecipe.nativePath(base); SalmonIndexRecipe.nativePath(home);
        var command = request.arguments(home + "/bin/salmon", base + "/inputs/" + request.capturedFastaName(), request.decoySource() == null ? null : base + "/inputs/decoys.txt", base + "/index");
        require(command.equals(NextflowFiles.list(producer.get("command"))), "Captured Salmon original index command differs from the complete typed recipe");
        var environment = Map.of("PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C", "TZ", "UTC", "LD_LIBRARY_PATH", home + "/lib", "SALMON_NO_VERSION_CHECK", "1", "OMP_NUM_THREADS", Integer.toString(request.threads()));
        require(environment.equals(NextflowFiles.object(producer.get("environment"))), "Captured Salmon index environment differs");
        Map<?, ?> process = json(root.resolve("index-process/process.json"), request.limits().metadataBytes());
        require("completed-process-only".equals(process.get("status")) && NextflowFiles.integer(process.get("exit_code"), 0, 0) == 0
            && NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty(), "Original Salmon index process is not complete with terminal owned workers");
        var limits = request.limits().process();
        require(NextflowJson.json(SimpleJson.parse(RsemJson.json(limits))).equals(NextflowJson.json(process.get("limits"))), "Original Salmon process capacities differ from the captured recipe");
        var wrapped = new ArrayList<>(List.of("/usr/bin/setsid", "/usr/bin/prlimit", "--as=" + limits.addressSpaceBytes(), "--cpu=" + limits.cpuSeconds(), "--fsize=" + limits.fileBytes(), "--nofile=" + limits.openFiles(), "--core=0", "--")); wrapped.addAll(command);
        require(wrapped.equals(NextflowFiles.list(process.get("command"))), "Salmon original supervised process invocation differs");
        Map<?, ?> runtime = json(root.resolve("runtime-admission/manifest.json"), request.limits().metadataBytes());
        require("NATIVE_RUNTIME_HEALTH_ONLY".equals(runtime.get("status")) && request.profile().equals(runtime.get("profile")) && request.version().equals(runtime.get("version"))
            && home.equals(runtime.get("home")) && Boolean.FALSE.equals(runtime.get("user_analysis_admitted")), "Captured Salmon native admission profile/home/status differs");
        Map<?, ?> runtimeComplete = json(root.resolve("runtime-admission/COMPLETED.json"), 8192);
        require("COMPLETE_NATIVE_RUNTIME_HEALTH_ONLY".equals(runtimeComplete.get("status")) && Boolean.FALSE.equals(runtimeComplete.get("user_analysis_admitted"))
            && NextflowFiles.pin(root.resolve("runtime-admission/manifest.json"), request.limits().metadataBytes(), () -> { }).equals(NextflowFiles.fingerprint(runtimeComplete.get("manifest"), request.limits().metadataBytes())), "Captured Salmon native admission completion differs");
    }
    static Map<String, Object> inventory(Path root, RsemProcess.Limits limits, Runnable check) throws IOException {
        NextflowFiles.directory(root); var result = new TreeMap<String, Object>(); long bytes = 0;
        try (var paths = Files.walk(root)) {
            var iterator = paths.iterator(); while (iterator.hasNext()) {
                Path path = iterator.next(); check.run(); if (path.equals(root)) continue;
                require(result.size() < limits.fileCount() && !Files.isSymbolicLink(path) && path.toRealPath().equals(path), "Linked/noncanonical/out-of-capacity Salmon bundle member");
                String name = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"); NextflowFiles.member(name);
                var row = new TreeMap<String, Object>(); boolean directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
                row.put("kind", directory ? "directory" : "file"); row.put("mode", (long) NextflowFiles.mode(path));
                if (!directory) { var pin = NextflowFiles.pin(path, limits.fileBytes(), check); bytes = Math.addExact(bytes, pin.bytes()); require(bytes <= limits.workspaceBytes(), "Complete Salmon bundle byte capacity exceeded"); row.putAll(fingerprint(pin)); }
                result.put(name, Map.copyOf(row));
            }
        }
        return Map.copyOf(result);
    }
    private static boolean equivalentInventory(Map<String, Object> current, Map<?, ?> declared) throws IOException {
        if (!current.keySet().equals(declared.keySet())) return false;
        for (var entry : current.entrySet()) {
            Map<?, ?> actual = NextflowFiles.object(entry.getValue()), saved = NextflowFiles.object(declared.get(entry.getKey()));
            String kind = NextflowFiles.text(saved.get("kind"));
            if (kind.equals("directory")) NextflowFiles.keys(saved, "kind", "mode"); else if (kind.equals("file")) NextflowFiles.keys(saved, "kind", "mode", "bytes", "sha256"); else return false;
            NextflowFiles.integer(saved.get("mode"), -1, 0777);
            if (!kind.equals(actual.get("kind"))) return false;
            if (kind.equals("file") && !NextflowFiles.fingerprint(saved, 1000000000000L).equals(NextflowFiles.fingerprint(actual, 1000000000000L))) return false;
        }
        return true;
    }
    static void create(Path root) throws IOException {
        if (!System.getProperty("os.name").equals("Linux")) throw new SalmonRuntime.Unavailable("Original Salmon index construction requires Linux x86_64 Java; use permitted Linux Java inside WSL on Windows");
        SalmonIndexRecipe.nativePath(root.toString());
        require(root.getParent() != null && root.getParent().toRealPath().equals(root.getParent()) && !Files.exists(root, LinkOption.NOFOLLOW_LINKS), "Require a NEW Salmon work directory under a canonical existing parent");
        if (!root.getFileSystem().supportedFileAttributeViews().contains("posix")) throw new SalmonRuntime.Unavailable("Salmon work needs a permitted filesystem preserving Linux private modes");
        Files.createDirectory(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        require(NextflowFiles.mode(root) == 0700, "Salmon work did not preserve mode0700");
    }
    static void copy(Path source, Path destination, NextflowFiles.Pin expected, Runnable check) throws IOException {
        long size = 0;
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS); var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[65536]; for (int n = input.read(buffer); n >= 0; n = input.read(buffer)) { check.run(); size = Math.addExact(size, n); require(size <= expected.bytes(), "Protected Salmon input grew during capture"); output.write(buffer, 0, n); }
        }
        require(size == expected.bytes() && expected.equals(NextflowFiles.pin(source, expected.bytes(), check)) && expected.equals(NextflowFiles.pin(destination, expected.bytes(), check)), "Protected Salmon input changed during complete capture");
    }
    static Map<String, Object> fingerprint(NextflowFiles.Pin pin) { return Map.of("bytes", pin.bytes(), "sha256", pin.sha256()); }
    private static Map<?, ?> json(Path path, int maximum) throws IOException { return NextflowFiles.object(NextflowFiles.json(path, maximum)); }
    private static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
    private static String warnings(SalmonIndexAudit.Result audit) {
        return "# Salmon index only — no expression analysis admitted\n\nWARNING: Original preprocessing may replace ambiguous bases, clip poly-A tails and remove references; inspect every row in REFERENCE-LEDGER.jsonl. Duplicate-cluster output alone is not an exclusion list.\n\n"
            + "WARNING: " + audit.ledger().notRetained() + " raw references were not retained; reasons require separate original evidence. " + audit.ledger().decoys().disagreements().size()
            + " retained reference assignments differ between declared decoys and the original runtime. No missing transcript is treated as biological zero.\n\n" + ACTION + "\n";
    }
    private static void failure(Path root, IOException failure) {
        try { Files.deleteIfExists(root.resolve("COMPLETED.json")); } catch (IOException revoke) { failure.addSuppressed(revoke); }
        int code = failure instanceof SalmonRuntime.Unavailable ? 3 : 2; String status = code == 3 ? "SKIP3 — ANALYSIS SKIPPED" : "FAIL2 — NO RESULT ADMITTED";
        try {
            NextflowFiles.writeJson(root.resolve("FAILURE.json"), Map.of("status", status, "exit_code", code, "error", failure.toString(), "required_action", ACTION));
            Path warnings = root.resolve("WARNINGS.md"); if (Files.exists(warnings)) Files.move(warnings, root.resolve("WARNINGS-before-failure.md"));
            Files.writeString(warnings, "# Salmon " + status + "\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
    }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
