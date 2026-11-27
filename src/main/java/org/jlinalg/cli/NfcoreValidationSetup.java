/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.zip.ZipInputStream;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit separate local installation of the exact original Apache2 plugin archive. */
final class NfcoreValidationSetup {
    private NfcoreValidationSetup() { }
    static Path install(Path supplied, Path target) throws IOException {
        byte[] raw = bytes(supplied.toAbsolutePath().normalize(), 30000000);
        String expected;
        try (var stream = NfcoreValidationSetup.class.getResourceAsStream("/org/jlinalg/omics/nfcore-validation/PLUGIN-PROFILE.json")) {
            if (stream == null) throw new IOException("Missing original plugin profile");
            NfcoreValidationPlugin.files(); expected = text(object(SimpleJson.parse(utf8(stream.readNBytes(1048577)))).get("archive_sha256"));
        }
        if (!sha(raw).equals(expected)) throw new IOException("Local plugin archive differs from the complete original nf-validation1.1.3 release SHA-256; download the exact release named in PLUGIN-PROFILE.json");
        if (!target.isAbsolute() || !target.normalize().equals(target) || target.getParent() == null || !target.getParent().toRealPath().equals(target.getParent())) throw new IOException("Use a new setup directory with a canonical existing parent");
        Files.createDirectory(target);
        try {
            Path plugins = Files.createDirectory(target.resolve("plugins")); Path root = Files.createDirectory(plugins.resolve("nf-validation-1.1.3"));
            var expectedFiles = NfcoreValidationPlugin.files(); var expectedDirs = NfcoreValidationPlugin.directories(expectedFiles.keySet());
            long total = 0; int count = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(raw))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > 1000) throw new IOException("Original plugin archive entry capacity exceeded");
                    String name = entry.getName(); if (entry.isDirectory()) name = name.substring(0, name.length() - 1);
                    member(name); Path file = root.resolve(name);
                    if (entry.isDirectory()) {
                        if (!expectedDirs.contains("nf-validation-1.1.3/" + name)) throw new IOException("Unapproved original plugin directory: " + name);
                        Files.createDirectories(file);
                    } else {
                        if (!expectedFiles.containsKey("nf-validation-1.1.3/" + name)) throw new IOException("Unapproved original plugin file: " + name);
                        byte[] content = zip.readNBytes(10000001); total = Math.addExact(total, content.length);
                        if (content.length > 10000000 || total > 30000000) throw new IOException("Complete plugin expansion capacity exceeded");
                        Files.createDirectories(file.getParent());
                        // Physical archive order replaces its earlier manifest.
                        Files.write(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                    zip.closeEntry();
                }
            }
            NfcoreValidationPlugin.verifyWorking(plugins, () -> { });
            writeJson(target.resolve("COMPLETED.json"), Map.of("status", "complete-private-plugin-setup-only", "plugin", "nf-validation", "version", "1.1.3", "license", "Apache-2.0", "original_archive", Map.of("bytes", raw.length, "sha256", sha(raw)), "physical_archive_entries", count, "analysis_executed", false));
            return plugins;
        } catch (IOException | RuntimeException failure) {
            try {
                writeJson(target.resolve("FAILURE.json"), Map.of("status", "FAIL2-no-installation-admitted", "error", failure.toString(), "required_action", "Preserve this partial setup and the original archive. Correct the named cause, then use a NEW setup directory; do not use partial plugin files for analysis."));
                Files.writeString(target.resolve("WARNINGS.md"), "# FAIL2 - plugin setup failed\n\n" + failure + "\n\nPreserve this partial setup; correct the cause and use a NEW setup directory. Dependent analysis must be skipped.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
            throw failure;
        }
    }
}
