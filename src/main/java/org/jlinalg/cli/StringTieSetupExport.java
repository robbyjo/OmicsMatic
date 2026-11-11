/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/** Portable exact setup-kit export only; neither installation nor scientific execution occurs here. */
public final class StringTieSetupExport {
    private StringTieSetupExport() { }
    static final String RESOURCE = "/org/jlinalg/omics/stringtie-runtime-setup/";
    static final NextflowFiles.Pin LOCK = new NextflowFiles.Pin(28867, "b2697399b8cd7e716131a54a6e75ed2b1641eb68e137c373bca52736d0200e3d");
    static final NextflowFiles.Pin INSTALLER = new NextflowFiles.Pin(16074, "93562e7ecb14aee62431bbf0ac28392937953917b849fdbd75a2b0476f61fbbb");
    /**
     * Export a standalone kit from the current development JAR.
     * @param arguments exactly --out NEW_DIRECTORY
     */
    public static void main(String[] arguments) { System.exit(run(arguments, System.out, System.err)); }
    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (arguments.length == 1 && arguments[0].equals("--help")) {
            output.println("java -cp DEVELOPMENT_JAR org.jlinalg.cli.StringTieSetupExport --out NEW_DIRECTORY\nExport only; read RUN-SETUP.txt. No installation/runtime/user analysis is admitted. Linux x86_64 permitted setup is a separate action."); return 0;
        }
        try {
            if (arguments.length != 2 || !arguments[0].equals("--out")) throw new IOException("Require exactly --out NEW_DIRECTORY; use --help");
            Path root = Path.of(arguments[1]).toAbsolutePath().normalize(); export(root);
            output.println("Exported exact StringTie setup kit: " + root + "\nRead RUN-SETUP.txt and verify export.json. No setup, runtime or user analysis has run."); return 0;
        } catch (AccessDeniedException unavailable) { error.println("SKIP3: StringTie setup export access unavailable; skip dependent setup. Choose a permitted writable NEW directory. " + unavailable); return 3;
        } catch (IOException | RuntimeException failure) { error.println("FAIL2: no StringTie setup export admitted. Preserve partials, read FAILURE.txt when present, correct the cause and use NEW paths. " + failure); return 2; }
    }
    static Path export(Path root) throws IOException {
        if (root.toString().chars().anyMatch(c -> c < 33 || c > 126)) throw new IOException("Use canonical absolute ASCII setup-kit paths without whitespace");
        BigWigFiles.create(root);
        try {
            Files.writeString(root.resolve("INCOMPLETE.txt"), "Setup export in progress; no installation or analysis admitted.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            var files = new TreeMap<String, Object>();
            byte[] lock = capture(root, "stringtie-runtime-lock.json", LOCK, files);
            var document = NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(lock)));
            if (NextflowFiles.integer(document.get("schema_version"), 1, 1) != 1 || NextflowFiles.object(document.get("profiles")).size() != 7) throw new IOException("Unexpected complete exact StringTie setup profile population");
            Files.createDirectory(root.resolve("licenses")); Files.createDirectory(root.resolve("health-inputs"));
            var resources = NextflowFiles.object(document.get("resources"));
            if (resources.size() != 10) throw new IOException("Unexpected complete StringTie license/health resource population");
            for (var entry : resources.entrySet()) {
                String name = NextflowFiles.member(NextflowFiles.text(entry.getKey()));
                if (!(name.matches("licenses/[0-9]+\\.[0-9]+\\.[0-9]+\\.txt") || name.matches("health-inputs/(reads\\.bam|guide\\.gtf|reference\\.sizes)"))) throw new IOException("Unexpected exact StringTie resource member");
                capture(root, name, NextflowFiles.fingerprint(entry.getValue(), 1048576), files);
            }
            capture(root, "install_stringtie.py", INSTALLER, files);
            capture(root, "README.txt", new NextflowFiles.Pin(596, "46d199e9ff311db8afa60175893374c0671d753157562fb7261b530873f47434"), files);
            String guide = """
                StringTie exact runtime setup — export only

                This is a standalone kit, not an installed/admitted runtime or user fit.
                Require export.json, every recorded file hash, no INCOMPLETE.txt and no
                FAILURE.txt. Read the exact version's licenses/<version>.txt.

                On permitted Linux x86_64 Python (Linux Python inside WSL on Windows):

                python3 -I -B ./install_stringtie.py --profile linux-x86_64-stringtie-3.0.3 --home /var/tmp/stringtie-home-NEW --report /var/tmp/stringtie-setup-NEW --cache /var/tmp/stringtie-cache --allow-install --allow-download

                Choose the exact profile:1.3.0,1.3.6,2.1.2,2.1.7,2.2.1,3.0.0 or3.0.3.
                Never select a current edition for an unreported paper run. Use canonical
                absolute ASCII paths without whitespace and NEW private home/report paths
                on a native Linux filesystem. Installation and download permissions are
                separate flags; use them only where permitted. For offline setup, add
                --archive-dir ABSOLUTE_ARCHIVES and omit --allow-download. Valid managed
                homes can be rechecked into a NEW report without either permission flag.

                Missing/forbidden installation, acquisition, access or filesystem capability:
                SKIP3; skip dependent analysis and provide the named exact requirement only
                through permitted setup. Corrupt input/changed hashes/unmanaged home or
                finalization failure:FAIL2; no result admitted. Preserve all partials and
                original reports, correct the cause and retry into NEW paths.

                Require installer exit0, report/COMPLETED.json and no INCOMPLETE.json.
                Read report/WARNINGS.md, FAILURE.json when present and all original logs.
                Setup verifies only exact original executable/license bytes. It performs
                no scientific runtime admission or user analysis. Separate current-host
                health, typed fit and complete output verification remain required.
                Old0.3.7 releases lack this exporter. Cell Ranger remains excluded.
                """;
            Files.writeString(root.resolve("RUN-SETUP.txt"), guide, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            files.put("RUN-SETUP.txt", BigWigFiles.pin(NextflowFiles.pin(root.resolve("RUN-SETUP.txt"), 65536, () -> {})));
            NextflowFiles.writeJson(root.resolve("export.json"), Map.of("schema_version", 1, "status", "EXPORTED_KIT_NOT_EXECUTED", "files", files,
                    "installation_performed", false, "scientific_runtime_admitted", false, "user_analysis_admitted", false));
            for (var entry : files.entrySet()) if (!NextflowFiles.fingerprint(entry.getValue(), 1048576).equals(NextflowFiles.pin(root.resolve(entry.getKey()), 1048576, () -> {}))) throw new IOException("Exported StringTie kit changed during finalization");
            Files.delete(root.resolve("INCOMPLETE.txt")); return root;
        } catch (IOException | RuntimeException | Error failure) {
            try {
                if (Files.exists(root.resolve("export.json"))) Files.move(root.resolve("export.json"), root.resolve("UNADMITTED-export.json"));
                Files.writeString(root.resolve("FAILURE.txt"), "FAIL2: no setup export admitted. Preserve this directory, correct the named cause and export into NEW paths.\n" + failure + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); } throw failure;
        }
    }
    private static byte[] capture(Path root, String name, NextflowFiles.Pin expected, Map<String, Object> files) throws IOException {
        byte[] raw = MemeRuntimeFiles.resource(RESOURCE + name, expected); Files.write(root.resolve(name), raw, StandardOpenOption.CREATE_NEW);
        files.put(name, BigWigFiles.pin(expected)); return raw;
    }
}
