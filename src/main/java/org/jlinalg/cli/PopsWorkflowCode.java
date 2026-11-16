/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Complete loaded code families, including anonymous classes and shaded dependencies. */
final class PopsWorkflowCode {
    private PopsWorkflowCode() { }
    static Map<String, String> current() throws Exception {
        Map<String, String> result = new TreeMap<>();
        scan(PopsWorkflowCode.class, List.of("org/jlinalg/cli/PopsWorkflow"), result);
        scan(org.jlinalg.genetics.PopsChunked.class, List.of("org/jlinalg/genetics/Pops"), result);
        scan(org.jlinalg.compute.BackendContext.class, List.of("org/jlinalg/compute/"), result);
        scan(jdistlib.Normal.class, List.of("jdistlib/"), result);
        return result;
    }
    private static void scan(Class<?> type, List<String> prefixes, Map<String, String> result) throws Exception {
        Path location = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        int found = 0;
        if (Files.isRegularFile(location)) {
            try (var zip = new ZipFile(location.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement(); String name = entry.getName();
                    if (matches(name, prefixes)) { try (var in = zip.getInputStream(entry)) { put(result, name, bytesSha(in.readAllBytes())); } found++; }
                }
            }
        } else if (Files.isDirectory(location)) {
            for (String prefix : prefixes) {
                Path base = location.resolve(prefix.substring(0, prefix.lastIndexOf('/') + 1));
                if (!Files.isDirectory(base)) continue;
                try (var paths = Files.walk(base)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        String name = location.relativize(path).toString().replace('\\', '/');
                        if (matches(name, prefixes)) { put(result, name, sha(path)); found++; }
                    }
                }
            }
        }
        if (found == 0) throw new Prerequisite("Required implementation class bytes unavailable: " + type.getName() + ". Build the complete artifact with its numerical dependency, then retry a NEW output directory or skip this analysis.");
    }
    private static boolean matches(String name, List<String> prefixes) { return name.endsWith(".class") && prefixes.stream().anyMatch(name::startsWith); }
    private static void put(Map<String, String> result, String name, String hash) throws IOException {
        String prior = result.putIfAbsent(name, hash); if (prior != null && !prior.equals(hash)) throw new IOException("Conflicting loaded class identity: " + name);
    }
    static String render(Map<String, String> entries) {
        StringBuilder out = new StringBuilder("member\tsha256\n");
        for (var entry : new TreeMap<>(entries).entrySet()) out.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        return out.toString();
    }
    static void check(Path root) throws Exception {
        if (!Files.readString(root.resolve("code.tsv")).equals(render(current()))) throw new IOException("PoPS implementation differs from the captured code. Use the matching original artifact/profile; do not refresh fingerprints to force a pass.");
    }
}
