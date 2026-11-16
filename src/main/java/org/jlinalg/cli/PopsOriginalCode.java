/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Pins the unchanged shared helpers and complete original-adapter code families. */
final class PopsOriginalCode {
    static final String AUDIT = "/org/jlinalg/omics/pops-original-v1/audit.py";
    private PopsOriginalCode() { }
    static Map<String, String> current() throws Exception {
        Map<String, String> result = new TreeMap<>(PopsWorkflowCode.current());
        Path location = Path.of(PopsOriginalCode.class.getProtectionDomain().getCodeSource().getLocation().toURI()); int found = 0;
        if (Files.isRegularFile(location)) try (var zip = new ZipFile(location.toFile())) {
            var entries = zip.entries(); while (entries.hasMoreElements()) { var entry = entries.nextElement(); if (matches(entry.getName())) { try (var in = zip.getInputStream(entry)) { result.put(entry.getName(), bytesSha(in.readAllBytes())); } found++; } }
        } else if (Files.isDirectory(location)) try (var paths = Files.walk(location.resolve("org/jlinalg/cli"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) { String name = location.relativize(path).toString().replace('\\', '/'); if (matches(name)) { result.put(name, sha(path)); found++; } }
        }
        if (found == 0) throw new Prerequisite("Original PoPS adapter classes unavailable; build the complete current artifact or skip analysis");
        try (var in = PopsOriginalCode.class.getResourceAsStream(AUDIT)) { if (in == null) throw new Prerequisite("Packaged original PoPS result auditor unavailable; build current source or skip dependent analysis"); result.put("Python/audit.py", bytesSha(in.readAllBytes())); }
        return result;
    }
    private static boolean matches(String name) { return name.endsWith(".class") && (name.startsWith("org/jlinalg/cli/PopsOriginal") || name.startsWith("org/jlinalg/cli/SimpleJson")); }
    static void check(Path root) throws Exception { if (!Files.readString(root.resolve("code.tsv")).equals(PopsWorkflowCode.render(current()))) throw new IOException("Original adapter implementation differs; use the matching artifact/profile, not refreshed fingerprints"); }
}
