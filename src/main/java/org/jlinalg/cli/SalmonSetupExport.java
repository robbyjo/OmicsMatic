/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/** Byte-pinned standalone kit export; execution/download permissions remain explicit installer options. */
final class SalmonSetupExport {
    private SalmonSetupExport() { }
    static void export(Path supplied) throws IOException {
        Path root = supplied.toAbsolutePath().normalize();
        if (root.getParent() == null || !root.getParent().toRealPath().equals(root.getParent())) throw new IOException("Require a canonical existing parent and NEW Salmon setup export directory");
        Files.createDirectory(root);
        try {
            var files = new TreeMap<String, Object>();
            Map<String, NextflowFiles.Pin> required = Map.of(
                "salmon-runtime-lock.json", new NextflowFiles.Pin(20410, SalmonSetupEvidence.LOCK_SHA), "health-lock.json", new NextflowFiles.Pin(5652, SalmonSetupEvidence.HEALTH_SHA),
                "install_salmon.py", SalmonSetupEvidence.INSTALLER, "README.txt", new NextflowFiles.Pin(581, "1b0916e877f98cacdaf1ce0d35884293fbaf6be7a5af86906681811b282b8d49"),
                "LICENSE-Salmon.txt", new NextflowFiles.Pin(35147, "8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903"));
            for (var entry : required.entrySet()) capture(root, entry.getKey(), entry.getValue(), files);
            Map<?, ?> health = NextflowFiles.object(NextflowFiles.json(root.resolve("health-lock.json"), 65536)); Files.createDirectory(root.resolve("health-inputs"));
            Map<?, ?> inputs = NextflowFiles.object(health.get("files"));
            if (inputs.size() != 5) throw new IOException("Unexpected complete Salmon setup health input population");
            for (var entry : inputs.entrySet()) {
                String name = NextflowFiles.member(NextflowFiles.text(entry.getKey())); if (name.contains("/")) throw new IOException("Unexpected nested Salmon health input");
                capture(root, "health-inputs/" + name, NextflowFiles.fingerprint(entry.getValue(), 8388608), files);
            }
            String guide = """
                Salmon exact runtime setup

                This export is a standalone kit, not a completed installation or user analysis.
                Read LICENSE-Salmon.txt and the installer --help. On permitted Linux x86_64
                Python3.12+ (Linux Python inside WSL on Windows), run from this directory:

                python3 -I -B ./install_salmon.py --profile linux-x86_64-salmon-1.9.0 --home /var/tmp/salmon-home-NEW --report /var/tmp/salmon-setup-NEW --cache /var/tmp/salmon-cache --allow-install --allow-download

                Choose the exact requested profile: 1.0.0, 1.1.0, 1.5.2, 1.8.0 or 1.9.0.
                Use NEW private home/report paths. Installation and network acquisition have
                independent permission flags. For offline exact archives, add --archive-dir
                ABSOLUTE_ARCHIVES and omit --allow-download. To recheck a completed managed
                home, use that home and a NEW report without either permission flag.

                Require installer exit0 and report/COMPLETED.json. Read report/WARNINGS.md
                and all original process logs. Missing/forbidden installation, download,
                execution, helper or resource: SKIP3; skip dependent analysis. Correct only
                through permitted setup or leave that analysis skipped. Invalid/corrupt input,
                changed hashes, original failure, cancellation or capacity failure: FAIL2;
                preserve the failed report, correct the named cause and use NEW paths.

                Setup synthetic health is separate from native current-host admission and
                complete user-fit validation. It does not calibrate biological inference.
                Original small-reference positional-bias failures remain unresolved; never
                silently disable requested bias. Original1.0.0 has two unavailable quantification
                SHA512 links. Runtime rechecks do not overwrite historical reports.
                """;
            Files.writeString(root.resolve("RUN-SETUP.txt"), guide, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            files.put("RUN-SETUP.txt", SalmonIndexWorkflow.fingerprint(NextflowFiles.pin(root.resolve("RUN-SETUP.txt"), 65536, () -> { })));
            NextflowFiles.writeJson(root.resolve("export.json"), Map.of("schema_version", 1, "status", "EXPORTED_KIT_NOT_RUN", "files", files, "user_analysis_admitted", false));
        } catch (IOException | RuntimeException failure) {
            try { Files.writeString(root.resolve("FAILURE.txt"), "Salmon setup export FAIL2; no installation or analysis admitted.\n" + failure + "\nRequired action: preserve this directory, correct the named resource/write failure and export to a NEW path.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
            catch (IOException diagnostic) { failure.addSuppressed(diagnostic); } throw failure;
        }
    }
    private static void capture(Path root, String name, NextflowFiles.Pin expected, Map<String, Object> files) throws IOException {
        byte[] raw;
        try (var input = SalmonSetupExport.class.getResourceAsStream(SalmonRuntime.RESOURCE + name)) {
            if (input == null || expected.bytes() > 8388608) throw new IOException("Missing/oversized pinned Salmon setup resource: " + name);
            raw = input.readNBytes(Math.toIntExact(expected.bytes()) + 1);
        }
        if (!expected.equals(new NextflowFiles.Pin(raw.length, NextflowFiles.sha(raw)))) throw new IOException("Complete pinned Salmon setup resource differs: " + name);
        Files.write(root.resolve(name), raw, StandardOpenOption.CREATE_NEW); files.put(name, SalmonIndexWorkflow.fingerprint(expected));
    }
}
