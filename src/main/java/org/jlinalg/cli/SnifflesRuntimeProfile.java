/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Closed known original source and dependency populations, checked before Python starts. */
final class SnifflesRuntimeProfile {
    private SnifflesRuntimeProfile() { }
    static final String RESOURCE = "/org/jlinalg/omics/sniffles-execution/";
    static final String ACTION = "SKIP3: skip dependent Sniffles calling until the exact permitted Linux x86_64 Python, original sources, libraries and native caller are exported and bound in NEW paths. Analysis installs nothing. If installation/copying is forbidden, retain the skip. Read the named error and WARNINGS.md; where permitted fix it and prepare a NEW runtime/binding. FAIL2 admits no case, including earlier provisional cases: retain all original logs and partial outputs, fix the named data/runtime/resource problem and retry a NEW output. Never refresh an old scientific binding's hashes.";
    static byte[] resource(String name) throws IOException {
        try (var input = SnifflesRuntimeProfile.class.getResourceAsStream(RESOURCE + name)) {
            require(input != null, "Missing packaged Sniffles runtime resource: " + name);
            byte[] result = input.readNBytes(8388609); require(result.length <= 8388608, "Packaged resource exceeds capacity"); return result;
        }
    }
    static Map<?, ?> profile() throws IOException { return object(SimpleJson.parse(utf8(resource("profiles.json")))); }
    static void platform() throws IOException {
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch")))
            throw new SnifflesProcess.UnavailableDependency("Original calling requires Linux x86_64 Java, including WSL. " + ACTION);
        for (String name : new String[]{"/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/ldd"})
            if (!Files.isExecutable(Path.of(name))) throw new SnifflesProcess.UnavailableDependency("Permitted native helper missing/forbidden: " + name);
        Path preload = Path.of("/etc/ld.so.preload");
        if (Files.exists(preload) && Files.size(preload) > 0) throw new SnifflesProcess.UnavailableDependency("Nonempty system ld.so.preload is outside this runtime profile");
    }
    static Map<String, Object> expectedRuntime(Map<?, ?> profile) throws IOException {
        var expected = new TreeMap<String, Object>();
        expected.put("python", profile.get("python")); expected.put("legacy-sniffles", profile.get("legacy_binary"));
        for (var entry : object(profile.get("private_site_members")).entrySet()) expected.put("site/" + text(entry.getKey()), entry.getValue());
        for (var edition : object(profile.get("editions")).entrySet())
            for (var file : object(object(edition.getValue()).get("members")).entrySet())
                expected.put("sources/" + text(edition.getKey()) + "/" + text(file.getKey()), file.getValue());
        return expected;
    }
    static void verify(Path root, Runnable check) throws IOException {
        platform();
        try {
            canonical(root); originalPath(root); require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS), "Original private runtime root missing");
            var profile = profile(); verifyTree(root, expectedRuntime(profile), false, check);
            require(Files.isExecutable(root.resolve("python")) && Files.isExecutable(root.resolve("legacy-sniffles")), "Original Python/native caller execution is forbidden");
            Path stdlib = Path.of(text(profile.get("stdlib_root")));
            verifyTree(stdlib, object(profile.get("stdlib_members")), true, check);
            require(!Files.exists(Path.of(text(profile.get("stdlib_zip"))), LinkOption.NOFOLLOW_LINKS), "Unexpected Python standard-library ZIP could shadow bound source");
            require(fingerprint(profile.get("python"), 33554432).equals(pin(Path.of(text(profile.get("python_executable"))), 33554432, check)), "Known base Python executable changed");
        } catch (SnifflesProcess.UnavailableDependency unavailable) { throw unavailable; }
        catch (IOException failure) { throw new SnifflesProcess.UnavailableDependency("Exact pre-Python runtime guard failed: " + failure + ". " + ACTION); }
    }
    private static void verifyTree(Path root, Map<?, ?> expected, boolean ignoreBytecode, Runnable check) throws IOException {
        canonical(root); var found = new java.util.HashSet<String>();
        int[] nodes = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                check.run(); require(!attrs.isSymbolicLink(), "Linked source directory is outside this profile");
                require(++nodes[0] <= 20000, "Pre-Python source-directory capacity exceeded");
                if (ignoreBytecode && path.getFileName().toString().equals("__pycache__")) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                check.run(); String name = root.relativize(path).toString().replace('\\', '/');
                require(++nodes[0] <= 20000, "Pre-Python source-file capacity exceeded");
                require(expected.containsKey(name) && found.add(name), "Unexpected executable/source member: " + path);
                var wanted = object(expected.get(name));
                if (wanted.containsKey("link")) {
                    require(attrs.isSymbolicLink() && Files.readSymbolicLink(path).toString().equals(text(wanted.get("link")))
                            && path.toRealPath().toString().equals(text(wanted.get("resolved"))), "Original standard-library link changed");
                    require(fingerprint(Map.of("bytes", wanted.get("bytes"), "sha256", wanted.get("sha256")), 536870912)
                            .equals(pin(path.toRealPath(), 536870912, check)), "Linked standard-library content changed");
                } else {
                    require(attrs.isRegularFile() && fingerprint(wanted, 536870912).equals(pin(path, 536870912, check)), "Original source/library file changed: " + path);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        require(found.equals(expected.keySet()), "Original complete source/library population missing files: " + root);
    }
    static Map<String, String> environment() {
        return Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "LC_ALL", "C.UTF-8", "TZ", "UTC",
                "PYTHONHOME", "/usr", "PYTHONHASHSEED", "0", "OMP_NUM_THREADS", "1", "OPENBLAS_NUM_THREADS", "1", "MKL_NUM_THREADS", "1");
    }
}
