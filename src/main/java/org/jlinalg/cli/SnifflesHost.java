/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Full Python/source/private-library census and inspected ELF dependency/search bindings. */
final class SnifflesHost {
    private SnifflesHost() { }
    private static final List<Path> HELPERS = List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/ldd", "/usr/bin/env", "/bin/bash", "/bin/sh", "/usr/bin/python3.12").stream().map(Path::of).toList();
    private static final List<Path> CONFIG = List.of("/etc/ld.so.cache", "/etc/ld.so.conf", "/etc/ld.so.preload", "/usr/lib/python312.zip").stream().map(Path::of).toList();
    private static final Set<Path> SYSTEM = Set.of(Path.of("/usr/lib"), Path.of("/usr/lib64"), Path.of("/lib"), Path.of("/lib64"), Path.of("/usr/bin"), Path.of("/bin"), Path.of("/etc"));
    private static final SnifflesHostFiles.Limits CAP = new SnifflesHostFiles.Limits(536870912, 17179869184L, 60000, 128, 67108864);
    private static final SnifflesProcess.Limits PROBE = new SnifflesProcess.Limits(60000, 2147483648L, 60, 1048576, 256, 67108864, 1024, 16, 128, 1073741824, 100);
    private static Set<Path> allowed(Path root) { var result = new java.util.HashSet<>(SYSTEM); result.add(root); return Set.copyOf(result); }
    static Map<String, Object> capture(Path root, Path work, SnifflesProcess.Check integrity) throws IOException {
        SnifflesRuntimeProfile.platform(); Files.createDirectory(work); var allowed = allowed(root);
        var files = new LinkedHashSet<Path>(HELPERS); files.addAll(CONFIG); files.add(root.resolve("python")); files.add(root.resolve("legacy-sniffles"));
        var initial = SnifflesHostFiles.capture(List.of(), new ArrayList<>(files), allowed, allowed, CAP, () -> {});
        var pending = new TreeSet<String>();
        for (var entry : initial.files().values()) if (entry.file() != null) pending.add(entry.file().realPath());
        var roots = new LinkedHashSet<Path>(); roots.add(root); roots.add(Path.of("/usr/lib/python3.12")); roots.add(Path.of("/etc/ld.so.conf.d"));
        for (Path area : List.of(root.resolve("site"), Path.of("/usr/lib/python3.12"))) try (var tree = Files.walk(area)) {
            for (Path path : tree.toList()) if (path.getFileName().toString().endsWith(".so") && Files.isRegularFile(path)
                    && !path.startsWith(root.resolve("site/pysam.libs"))) pending.add(path.toRealPath().toString());
        }
        var nodes = new LinkedHashSet<Path>(); var images = new TreeMap<String, SnifflesElf.Image>(); var bindings = new TreeMap<String, SnifflesElf.Report>(); var probed = new TreeSet<String>(); int probes = 0;
        while (!pending.isEmpty()) {
            integrity.verify(); String name = pending.pollFirst(); if (!probed.add(name)) continue;
            Path path = Path.of(name); var image = SnifflesElf.image(path, CAP.fileBytes(), () -> {}); if (image == null) continue;
            require(images.size() < 256, "ELF provider capacity exceeded"); images.put(name, image); files.add(path);
            var directories = new LinkedHashSet<Path>(SnifflesElf.searchDirectories(path, image));
            for (String standard : List.of("/usr/lib/x86_64-linux-gnu", "/usr/lib64", "/lib/x86_64-linux-gnu", "/lib64", "/usr/lib", "/lib")) directories.add(Path.of(standard));
            for (Path directory : directories) {
                require(allowed.stream().anyMatch(directory::startsWith), "ELF search directory outside declared runtime/system providers");
                nodes.add(directory); roots.add(directory.resolve("glibc-hwcaps"));
                for (String needed : image.neededNames()) { require(needed.matches("[A-Za-z0-9_.+\\-]{1,255}"), "Unsupported non-SONAME dependency"); files.add(directory.resolve(needed)); }
            }
            if (image.interpreter() != null) add(Path.of(image.interpreter()), files, new TreeSet<>(), allowed);
            var probe = SnifflesProcess.inspectLibraries(path, SnifflesRuntimeProfile.environment(), work.resolve("ldd-" + (++probes)), PROBE,
                () -> { integrity.verify(); SnifflesHostFiles.verify(initial, allowed, allowed, CAP, () -> {}); }, () -> {}, List.of());
            byte[] stdout = bytes(probe.stdout(), 1048576), stderr = bytes(probe.stderr(), 1048576);
            var report = SnifflesElf.bindings(image, probe.exitCode(), stdout, stderr); bindings.put(name, report);
            // ldd's complete entry-image report includes transitive bindings.
            // Bundled dependencies may rely on inherited RPATH: validate their
            // needed entries in THIS context, never as independent executables.
            for (var binding : report.bindings()) {
                Path provider = Path.of(binding.path()).toRealPath(); add(provider, files, new TreeSet<>(), allowed);
                var providerImage = SnifflesElf.image(provider, CAP.fileBytes(), () -> {});
                require(providerImage != null, "Library binding is not a complete ELF image");
                SnifflesElf.bindings(providerImage, probe.exitCode(), stdout, stderr);
                require(images.size() < 256 || images.containsKey(provider.toString()), "ELF provider capacity exceeded"); images.putIfAbsent(provider.toString(), providerImage);
                var providerDirectories = new LinkedHashSet<Path>(directories); providerDirectories.addAll(SnifflesElf.searchDirectories(provider, providerImage));
                for (Path directory : providerDirectories) {
                    require(allowed.stream().anyMatch(directory::startsWith), "Contextual ELF search directory outside declared providers");
                    nodes.add(directory); roots.add(directory.resolve("glibc-hwcaps"));
                    for (String needed : providerImage.neededNames()) {
                        require(needed.matches("[A-Za-z0-9_.+\\-]{1,255}"), "Unsupported contextual non-SONAME dependency"); files.add(directory.resolve(needed));
                    }
                }
            }
        }
        var complete = SnifflesHostFiles.capture(roots.stream().sorted().toList(), files.stream().sorted().toList(), nodes.stream().sorted().toList(), allowed, allowed, CAP, () -> {});
        SnifflesHostFiles.verify(initial, allowed, allowed, CAP, () -> {}); integrity.verify();
        var doc = new LinkedHashMap<String, Object>(); doc.put("files", snapshot(complete)); doc.put("images", images); doc.put("bindings", bindings);
        doc.put("platform", Map.of("os", System.getProperty("os.name"), "architecture", System.getProperty("os.arch"), "release", System.getProperty("os.version")));
        @SuppressWarnings("unchecked") var result = (Map<String, Object>) SimpleJson.parse(SnifflesWorkflowJson.encode(doc));
        write(work.resolve("host-binding.json"), result); return result;
    }
    private static Map<?, ?> snapshot(SnifflesHostFiles.Snapshot value) throws IOException {
        var parsed = new LinkedHashMap<>(object(SimpleJson.parse(utf8(SnifflesHostFiles.bytes(value, CAP))))); parsed.remove("physicalHashes"); return parsed;
    }
    private static void add(Path path, Set<Path> files, Set<String> pending, Set<Path> allowed) throws IOException {
        Path real = path.toRealPath(); require(Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) && allowed.stream().anyMatch(real::startsWith), "ELF binding outside declared regular providers"); files.add(path); pending.add(real.toString());
    }
    static void verifyFiles(Map<?, ?> host, Path root) throws IOException {
        var expected = object(host.get("files")); var allowed = allowed(root);
        var actual = SnifflesHostFiles.capture(paths(expected.get("requestedRoots")), paths(expected.get("requestedFiles")), paths(expected.get("requestedNodes")), allowed, allowed, CAP, () -> {});
        require(expected.equals(snapshot(actual)), "Complete bound Python/source/native/helper/library/search population changed; preserve this binding and prepare NEW paths");
    }
    private static List<Path> paths(Object value) throws IOException { var result = new ArrayList<Path>(); for (Object name : list(value)) result.add(Path.of(text(name))); return result; }
}
