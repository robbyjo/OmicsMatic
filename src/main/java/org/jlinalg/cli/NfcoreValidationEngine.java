/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import static org.jlinalg.cli.NextflowFiles.*;

/** Exact original standard bootstrap, capsule and explicit dependency closure. */
final class NfcoreValidationEngine {
    private NfcoreValidationEngine() { }
    private static final Pin PROFILE = new Pin(26718, "7a49fd6de4b58588f16f9505aed2cb74bf0fbee48b5e50af76d2f374370d43af");
    static Map<?, ?> profile() throws IOException {
        byte[] raw;
        try (var stream = NfcoreValidationEngine.class.getResourceAsStream("/org/jlinalg/omics/nfcore-validation/ENGINE-PROFILE.json")) {
            if (stream == null) throw new IOException("Missing standard Nextflow24.04.4 profile"); raw = stream.readNBytes(1048577);
        }
        if (!PROFILE.equals(new Pin(raw.length, sha(raw)))) throw new IOException("Standard Nextflow24.04.4 dependency profile changed");
        return object(SimpleJson.parse(utf8(raw)));
    }
    static Map<String, Pin> files() throws IOException {
        var result = new TreeMap<String, Pin>();
        for (var row : object(profile().get("files")).entrySet()) result.put(member(text(row.getKey())), fingerprint(row.getValue(), 30000000));
        return Map.copyOf(result);
    }
    private static final java.util.Set<String> LOCK_DIRECTORY = java.util.Set.of("repository/.locks");
    static void validate(NextflowCapture.Snapshot capture) throws IOException { NfcoreValidationPlugin.validateResource(capture, "engine", files(), LOCK_DIRECTORY); }
    static void validateWorkspace(NextflowCapture.Snapshot working, NextflowCapture.Snapshot capture) throws IOException { NfcoreValidationPlugin.validateWorkspace(working, capture, "engine", files(), LOCK_DIRECTORY); }
    static void verifyWorking(Path engine, Runnable check) throws IOException { NfcoreValidationPlugin.verifyWorking(engine, files(), LOCK_DIRECTORY, check); }

    static Map<String, String> environment(String producer, String engine) {
        return Map.of("CAPSULE_CACHE_DIR", producer + "/workspace/execution/capsule", "CAPSULE_LOCAL_REPO", engine + "/repository", "NXF_BIN", engine + "/nextflow-24.04.4-one.jar");
    }

    static Path install(Path bootstrap, Path capsule, Path home, Path report, Path target) throws IOException {
        var managed = NextflowRuntime.open(home, NfcoreValidationPlugin.ENGINE, report, () -> { }); var wanted = files();
        if (!pin(bootstrap, 100000, () -> { }).equals(wanted.get("nextflow")) || !pin(capsule, 30000000, () -> { }).equals(wanted.get("nextflow-24.04.4-one.jar"))) throw new IOException("Supply the exact original24.04.4 bootstrap and one capsule named by ENGINE-PROFILE.json");
        if (!target.isAbsolute() || !target.normalize().equals(target) || target.getParent() == null || !target.getParent().toRealPath().equals(target.getParent())) throw new IOException("Use a NEW engine setup with a canonical existing parent");
        Files.createDirectory(target);
        try {
            Path engine = Files.createDirectory(target.resolve("engine")); Files.copy(bootstrap, engine.resolve("nextflow")); Files.copy(capsule, engine.resolve("nextflow-24.04.4-one.jar"));
            try (var archive = new ZipFile(home.resolve("nextflow").toFile())) {
                for (Object value : list(profile().get("dependencies"))) {
                    Map<?, ?> row = object(value); String path = member(text(row.get("path"))), source = member(text(row.get("source_member")));
                    var entry = archive.getEntry(source); if (entry == null) throw new IOException("Original managed distribution lacks required dependency: " + source);
                    byte[] raw; try (var stream = archive.getInputStream(entry)) { raw = stream.readNBytes(30000001); }
                    if (!new Pin(raw.length, sha(raw)).equals(wanted.get(path))) throw new IOException("Unchanged original dependency hash differs: " + source);
                    Path file = engine.resolve(path); Files.createDirectories(file.getParent()); Files.write(file, raw, java.nio.file.StandardOpenOption.CREATE_NEW);
                }
            }
            Files.createDirectory(engine.resolve("repository/.locks"));verifyWorking(engine, () -> { }); managed.verify(() -> { });
            writeJson(target.resolve("COMPLETED.json"), Map.of("status", "complete-standard-engine-setup-only", "version", "24.04.4", "packaging", "one", "files", wanted, "analysis_executed", false));
            return engine;
        } catch (IOException | RuntimeException failure) {
            try {
                writeJson(target.resolve("FAILURE.json"), Map.of("status", "FAIL2-no-setup-admitted", "error", failure.toString(), "required_action", "Preserve this partial setup; correct the cause and use NEW paths. Skip dependent analysis until exact setup succeeds."));
                Files.writeString(target.resolve("WARNINGS.md"), "# FAIL2 - engine setup failed\n\n" + failure + "\n\nSkip dependent analysis. Preserve this setup, correct the cause and use NEW paths.\n", java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
            throw failure;
        }
    }
}
