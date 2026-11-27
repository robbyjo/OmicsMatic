/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.jlinalg.cli.NextflowFiles.*;

/** Closed original plugin payload; no registry access, auto-update or version inference. */
final class NfcoreValidationPlugin {
    private NfcoreValidationPlugin() { }
    static final String ENGINE = "linux-x86_64-nextflow-24.04.4";
    private static final Pin PROFILE = new Pin(10150, "5eea2ed079971981577455ef85e5ad33560dbf65f9dfead02884ebc89cff5d98");

    static Map<String, Pin> files() throws IOException {
        byte[] raw;
        try (var stream = NfcoreValidationPlugin.class.getResourceAsStream("/org/jlinalg/omics/nfcore-validation/PLUGIN-PROFILE.json")) {
            if (stream == null) throw new IOException("Missing pinned nf-validation profile");
            raw = stream.readNBytes(1048577);
        }
        if (!PROFILE.equals(new Pin(raw.length, sha(raw)))) throw new IOException("Pinned nf-validation source profile changed");
        var result = new TreeMap<String, Pin>();
        for (var row : object(object(SimpleJson.parse(utf8(raw))).get("files")).entrySet())
            result.put(member(text(row.getKey())), fingerprint(row.getValue(), 10000000));
        return Map.copyOf(result);
    }

    static void validate(Map<?, ?> recipe, NextflowCapture.Snapshot capture) throws IOException {
        if (!ENGINE.equals(recipe.get("engine_profile"))) throw new NextflowRuntime.Unavailable("The pinned nf-validation bridge requires explicitly selected Nextflow24.04.4; use that managed engine and skip dependent analysis until available");
        validateResource(capture, "plugins", files());
    }

    static void validateResource(NextflowCapture.Snapshot capture, String resource, Map<String, Pin> expected) throws IOException {
        validateResource(capture, resource, expected, Set.of());
    }

    static void validateResource(NextflowCapture.Snapshot capture, String resource, Map<String, Pin> expected, Set<String> extraDirectories) throws IOException {
        String alias = capture.aliases().get(resource);
        if (alias == null) throw new NextflowRuntime.Unavailable("Missing captured " + resource + " resource: complete the exact separate permitted setup, then capture its complete payload; analysis skipped");
        var observed = new TreeMap<String, NextflowCapture.Item>();
        for (var root : capture.roots()) for (var row : root.items().entrySet()) {
            String name = root.key() + "/" + root.leaf() + (row.getKey().isEmpty() ? "" : "/" + row.getKey());
            if (name.equals(alias)) observed.put("", row.getValue());
            else if (name.startsWith(alias + "/")) observed.put(name.substring(alias.length() + 1), row.getValue());
        }
        validateItems(observed, expected, extraDirectories);
    }

    static void validateWorkspace(NextflowCapture.Snapshot working, NextflowCapture.Snapshot capture) throws IOException {
        validateWorkspace(working, capture, "plugins", files());
    }

    static void validateWorkspace(NextflowCapture.Snapshot working, NextflowCapture.Snapshot capture, String resource, Map<String, Pin> expected) throws IOException {
        validateWorkspace(working, capture, resource, expected, Set.of());
    }

    static void validateWorkspace(NextflowCapture.Snapshot working, NextflowCapture.Snapshot capture, String resource, Map<String, Pin> expected, Set<String> extraDirectories) throws IOException {
        if (working.roots().size() != 1) throw new IOException("Expected one complete captured working tree");
        String prefix = "resources/" + capture.aliases().get(resource);
        var observed = new TreeMap<String, NextflowCapture.Item>();
        for (var row : working.roots().get(0).items().entrySet()) {
            if (row.getKey().equals(prefix)) observed.put("", row.getValue());
            else if (row.getKey().startsWith(prefix + "/")) observed.put(row.getKey().substring(prefix.length() + 1), row.getValue());
        }
        validateItems(observed, expected, extraDirectories);
    }

    static void verifyWorking(Path plugins, Runnable check) throws IOException {
        verifyWorking(plugins, files(), check);
    }

    static void verifyWorking(Path plugins, Map<String, Pin> expected, Runnable check) throws IOException {
        verifyWorking(plugins, expected, Set.of(), check);
    }

    static void verifyWorking(Path plugins, Map<String, Pin> expected, Set<String> extraDirectories, Runnable check) throws IOException {
        directory(plugins);
        var observed = new TreeMap<String, Pin>(); var directories = new TreeSet<String>(); directories.add("");
        try (var paths = Files.walk(plugins)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                check.run(); Path path = iterator.next(); String name = plugins.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) throw new IOException("Plugin links are not admitted: " + name);
                if (Files.isDirectory(path)) directories.add(name);
                else if (Files.isRegularFile(path)) observed.put(name, pin(path, 10000000, check));
                else throw new IOException("Nonregular plugin member: " + name);
            }
        }
        var expectedDirectories = new TreeSet<>(directories(expected.keySet())); expectedDirectories.addAll(extraDirectories);
        if (!observed.equals(expected) || !directories.equals(expectedDirectories)) throw new IOException("Complete original runtime resource bytes or member census changed: " + plugins);
    }

    static void validateItems(Map<String, NextflowCapture.Item> observed) throws IOException {
        validateItems(observed, files());
    }

    static void validateItems(Map<String, NextflowCapture.Item> observed, Map<String, Pin> expected) throws IOException {
        validateItems(observed, expected, Set.of());
    }

    static void validateItems(Map<String, NextflowCapture.Item> observed, Map<String, Pin> expected, Set<String> extraDirectories) throws IOException {
        var got = new TreeMap<String, Pin>(); var dirs = new TreeSet<String>();
        for (var row : observed.entrySet()) {
            var item = row.getValue();
            if (item.kind().equals("directory")) dirs.add(row.getKey());
            else if (item.kind().equals("file") && item.original() != null) got.put(row.getKey(), item.original().pin());
            else throw new IOException("nf-validation payload contains an unapproved link or special member");
        }
        var expectedDirectories = new TreeSet<>(directories(expected.keySet())); expectedDirectories.addAll(extraDirectories);
        if (!got.equals(expected) || !dirs.equals(expectedDirectories)) throw new IOException("Require every exact original runtime file and declared directory, without additions or changed members");
    }

    static Set<String> directories(Set<String> files) {
        var result = new TreeSet<String>(); result.add("");
        for (String file : files) { int slash = file.lastIndexOf('/'); while (slash >= 0) { result.add(file.substring(0, slash)); slash = file.lastIndexOf('/', slash - 1); } }
        return Set.copyOf(result);
    }
}
