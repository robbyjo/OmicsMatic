/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Typed native input checks and original Eagle phasing with stable output paths. */
final class EagleToolsCli {
    private EagleToolsCli() { }
    private static final Set<String> ALLOWED = Set.of("recipe", "home", "out", "options-file");
    private static final String RECOVERY = "Required action: inspect WARNINGS.md, analysis-status.json, session.json and retained source/preparation/process ledgers. A completed bundle requires COMPLETED.json and its matching manifest hash. Missing/forbidden runtime or unavailable capability requires SKIP3; input, integrity, execution and capacity failures require FAIL2. Preserve failed/provisional directories and rerun to a NEW output. Analysis never installs or substitutes editions. For permitted setup export the helper with eagle-tools --export-runtime-setup NEW_DIRECTORY, then run its install_eagle.py with separate --allow-install and --allow-download permissions. Linux x86_64 Python3.11+ and Linux Java are required for original phasing; on Windows use WSL. Cell Ranger remains excluded.";

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && args[0].equals("--help")) { emit(out, help()); return 0; }
        Path directory = null; boolean ownsDirectory = false;
        try {
            if (args.length == 2 && args[0].equals("--export-runtime-setup")) { export(Path.of(args[1])); emit(out, "Eagle setup helper exported: " + args[1]); return 0; }
            Map<String, String> options = options(args); String destination = required(options, "out"); required(options, "recipe");
            directory = Path.of(destination).toAbsolutePath().normalize();
            if (directory.getParent() == null) throw new IOException("Require a new Eagle output below a real parent");
            Files.createDirectories(directory.getParent());
            if (!directory.getParent().toRealPath().equals(directory.getParent())) throw new IOException("Eagle output parent must be canonical without linked ancestors");
            if (options.containsKey("home")) {
                Path home = Path.of(options.get("home")).toAbsolutePath().normalize();
                if (Files.exists(home)) home = home.toRealPath();
                if (directory.startsWith(home) || home.startsWith(directory)) throw new IOException("Eagle output and sealed runtime must be separate");
            }
            Files.createDirectory(directory); // Stable final path; never move absolute audit references.
            ownsDirectory = true;
            var session = new LinkedHashMap<String, Object>(); var warnings = new ArrayList<String>(); int code = 0;
            session.put("tool", "Eagle"); session.put("requested_options", options); session.put("started_at", Instant.now().toString());
            session.put("scope", "Original pinned Eagle2.4.1 VCF/BCF2.2 cohort/reference phasing or native input checks; protected native legacy-header BCF decoding, explicit POS input subset, policies and resources. No biological phase accuracy, historical/server or whole-paper equivalence claim.");
            EagleRecipe.Spec spec = null; EaglePhasing.Input input = null; EagleRuntime.Context runtime = null; EaglePhasing.Result result = null;
            try {
                verifyOptions(options);
                byte[] request = AdmixtureInputs.raw(Path.of(options.get("recipe")), 2097152);
                Files.write(directory.resolve("recipe-request.json"), request, StandardOpenOption.CREATE_NEW);
                session.put("recipe_request_sha256", AdmixtureInputs.digest(request));
                spec = EagleRecipe.read(Path.of(options.get("recipe"))); session.put("recipe", spec);
                if (spec.operation().equals("phase")) {
                    if (!options.containsKey("home")) throw new EagleRuntime.Unavailable("No admitted original Eagle --home supplied");
                    runtime = EagleRuntime.open(Path.of(options.get("home")), spec.profile()); session.put("runtime", runtime);
                } else if (options.containsKey("home")) throw new IOException("Native check does not consume --home; remove the unused option");
                input = EaglePhasing.prepare(spec, directory.resolve("prepared"), () -> { }); warnings.addAll(input.warnings());
                write(directory.resolve("input-audit.json"), input);
                result = EaglePhasing.run(input, runtime, () -> { }); warnings.clear(); warnings.addAll(result.warnings());
                write(directory.resolve("results.json"), result); input.verify(() -> { }); spec.verify(); verifyOptions(options);
                if (runtime != null) runtime.verify();
                session.put("analysis_status", "completed-" + spec.operation() + "-with-review-warnings");
            } catch (EagleRuntime.Unavailable | EagleRecipe.UnavailableCapability unavailable) {
                code = 3; session.put("analysis_status", "SKIP3-unavailable-dependency-or-capability"); session.put("error", unavailable.toString());
                warnings.add(0, "ANALYSIS SKIPPED: " + unavailable.getMessage() + ". " + RECOVERY);
            } catch (Exception failure) {
                code = 2; session.put("analysis_status", "FAIL2-no-result-admitted"); session.put("error", failure.toString());
                warnings.add(0, "ANALYSIS FAILED: " + failure.getMessage() + ". " + RECOVERY);
            }
            session.put("exit_code", code); session.put("warnings", warnings); session.put("finished_at", Instant.now().toString());
            Files.writeString(directory.resolve("WARNINGS.md"), "# Eagle " + (code == 0 ? "WARNINGS REQUIRE REVIEW" : code == 3 ? "SKIP3 ANALYSIS SKIPPED" : "FAIL2 ANALYSIS FAILED")
                    + "\n\n" + String.join("\n\n", warnings) + "\n\n" + RECOVERY + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            write(directory.resolve("session.json"), session);
            write(directory.resolve("analysis-status.json"), Map.of("status", session.get("analysis_status"), "exit_code", code,
                    "requires_completion_marker", true, "scientific_output_audit_passed", code == 0 && spec != null && spec.operation().equals("phase")));
            if (code == 0) { input.verify(() -> { }); spec.verify(); verifyOptions(options); if (runtime != null) runtime.verify(); }
            var files = inventory(directory);
            var manifest = new LinkedHashMap<String, Object>(); manifest.put("schema_version", 1); manifest.put("tool", "Eagle"); manifest.put("analysis_status", session.get("analysis_status"));
            manifest.put("exit_code", code); manifest.put("output_sha256", files); manifest.put("version", JLinAlgCli.version());
            byte[] manifestRaw = json(manifest);
            byte[] completed = json(Map.of("manifest_sha256", AdmixtureInputs.digest(manifestRaw), "scientific_result_admitted", code == 0 && spec != null && spec.operation().equals("phase"),
                    "input_check_completed", code == 0 && spec != null && spec.operation().equals("check")));
            if (code == 0) {
                long bytes = 0; for (String name : files.keySet()) bytes = Math.addExact(bytes, Files.size(directory.resolve(name)));
                if (Math.addExact(bytes, manifestRaw.length + (long)completed.length) > spec.workspaceBytes()) throw new IOException("Eagle complete bundle including final metadata exceeds workspace capacity");
            }
            Files.write(directory.resolve("manifest.json"), manifestRaw, StandardOpenOption.CREATE_NEW);
            if (code == 0) Files.write(directory.resolve("COMPLETED.json"), completed, StandardOpenOption.CREATE_NEW);
            emit(err, (code == 0 ? "ANALYSIS WARNINGS: " : code == 3 ? "SKIP3 ANALYSIS SKIPPED: " : "FAIL2 ANALYSIS FAILED: ") + directory.resolve("WARNINGS.md"));
            emit(out, "Eagle " + session.get("analysis_status") + ": " + directory); return code;
        } catch (Exception failure) {
            emit(err, "Eagle FAIL2: " + failure + "\n" + RECOVERY);
            if (ownsDirectory && directory != null && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                // Never overwrite an existing output or its diagnostics.
                try { Files.writeString(directory.resolve("FINALIZATION_FAILED.md"), "# Eagle FAIL2 incomplete bundle\n\n" + failure + "\n\n" + RECOVERY + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
                catch (IOException retained) { emit(err, "Diagnostic could not be written: " + retained); }
            }
            return 2;
        }
    }

    private static Map<String, String> options(String[] args) throws IOException {
        var direct = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length || !args[i].startsWith("--") || !ALLOWED.contains(args[i].substring(2)) || direct.putIfAbsent(args[i].substring(2), args[i+1]) != null)
                throw new IOException("Require unique supported Eagle --key value options");
        }
        if (!direct.containsKey("options-file")) return direct;
        byte[] raw = AdmixtureInputs.raw(Path.of(direct.get("options-file")), 1048576); Map<?, ?> source = AdmixtureInputs.decode(raw); var result = new LinkedHashMap<String, String>();
        for (var entry : source.entrySet()) {
            String key = entry.getKey().toString();
            if (!ALLOWED.contains(key) || key.equals("options-file") || !(entry.getValue() instanceof String text)) throw new IOException("Unknown, recursive or non-string Eagle CLI option");
            result.put(key, text);
        }
        result.putAll(direct); result.put("options-sha256", AdmixtureInputs.digest(raw)); return result;
    }
    private static void verifyOptions(Map<String, String> options) throws IOException {
        if (options.containsKey("options-file") && !AdmixtureInputs.digest(AdmixtureInputs.raw(Path.of(options.get("options-file")), 1048576)).equals(options.get("options-sha256"))) throw new IOException("Eagle CLI options file changed");
    }
    private static String required(Map<String, String> options, String key) throws IOException { String value = options.get(key); if (value == null || value.isBlank()) throw new IOException("Missing Eagle --" + key); return value; }
    private static Map<String, String> inventory(Path directory) throws IOException {
        var result = new TreeMap<String, String>(); int[] count = {0};
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException { if (++count[0] > 1000000) throw new IOException("Eagle bundle file-count capacity exceeded"); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                if (++count[0] > 1000000 || !attributes.isRegularFile()) throw new IOException("Eagle bundle contains linked/nonregular output or excessive files");
                result.put(directory.relativize(path).toString().replace('\\', '/'), FollowupSupport.hash(path)); return FileVisitResult.CONTINUE;
            }
        }); return result;
    }
    private static byte[] json(Object value) throws IOException { return (EagleJson.json(value) + "\n").getBytes(StandardCharsets.UTF_8); }
    private static void write(Path path, Object value) throws IOException { Files.write(path, json(value), StandardOpenOption.CREATE_NEW); }
    private static void emit(PrintStream stream, String text) { stream.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
    private static void export(Path supplied) throws IOException {
        Path root = supplied.toAbsolutePath().normalize();
        if (root.getParent() == null || !root.getParent().toRealPath().equals(root.getParent())) throw new IOException("Setup export requires a canonical existing parent and new directory");
        Files.createDirectory(root);
        var names = new ArrayList<>(List.of("install_eagle.py", "eagle-runtime-lock.json"));
        for (String name : List.of("cohort-target.vcf", "target.vcf.gz", "target.vcf.gz.csi", "reference.vcf.gz", "reference.vcf.gz.csi", "genetic-map.txt")) names.add("eagle-runtime/health/" + name);
        for (String name : List.of("LICENSE-HTSlib.txt", "LICENSE-Boost.txt", "LICENSE-OpenBLAS.txt")) names.add("eagle-runtime/notices/" + name);
        for (String name : names) {
            Path target = root.resolve(name); Files.createDirectories(target.getParent());
            try (var stream = EagleToolsCli.class.getResourceAsStream("/org/jlinalg/omics/eagle-runtime-setup/" + name)) {
                if (stream == null) throw new IOException("Missing bundled Eagle setup resource: " + name);
                byte[] raw = stream.readNBytes(1048577); if (raw.length > 1048576) throw new IOException("Oversized Eagle setup resource"); Files.write(target, raw, StandardOpenOption.CREATE_NEW);
            }
        }
        Files.writeString(root.resolve("README.txt"), "Private Eagle 2.4.1 setup helper; no runtime binary included.\nRun with Linux x86_64 Python3.11+: python3 install_eagle.py --profile linux-x86_64-eagle-2.4.1 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install\nAdd --allow-download only where permitted, or supply --archive-dir OFFLINE_ARCHIVES. Existing ldd/prlimit/setsid/libzstd are needed; no system packages are installed. Read the generated WARNINGS.md and Required action on every run.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    static String help() { return """
        eagle-tools --recipe TYPED_RECIPE.json --out NEW_DIRECTORY [--home ADMITTED_PRIVATE_HOME]
          [--options-file UTF8_STRING_OPTIONS.json]
        eagle-tools --export-runtime-setup NEW_DIRECTORY
        Native check or original cohort/reference phase; explicit schema1 JSON,
        policies, region and parameters. VCF/plain/gzip/BGZF or native BCF2.2
        with legacy VCF4.0-4.3 headers and protected source/index provenance.
        Original phase requires Linux x86_64 Java and the admitted Eagle2.4.1 profile.
        Analysis never installs, substitutes editions or accepts arbitrary engine flags.
        Read WARNINGS.md. Require COMPLETED.json plus its matching manifest hash;
        check has no phased result. SKIP3 unavailable dependency/capability; FAIL2 invalid
        input, integrity, execution or resource capacity. Retain all failed/provisional files.
        """ + RECOVERY; }
}
