/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.jar.JarFile;

/** Checks the complete original JDistlib class population in a standalone or bundled JAR. */
final class MonocleMathIdentity {
    private MonocleMathIdentity() { }
    private static final long MAX_JAR_BYTES = 512L * 1024 * 1024;
    private static final String PROFILE = "/org/jlinalg/omics/monocle-commands/jdistlib-0.10.2-classes.json";

    static Map<String, Object> verify() throws IOException {
        byte[] raw;
        try (var input = MonocleMathIdentity.class.getResourceAsStream(PROFILE)) {
            if (input == null) throw new IOException("Missing packaged original JDistlib class profile");
            raw = input.readNBytes(1048577);
        }
        var profile = NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw), 1048576, 100000));
        var original = NextflowFiles.object(profile.get("original_archive"));
        require(Monocle131ModelCli.MATH_SHA.equals(NextflowFiles.text(original.get("sha256"))), "Unexpected original JDistlib archive identity");
        var expected = NextflowFiles.object(profile.get("classes"));
        require(expected.size() == 933, "Incomplete original JDistlib class profile");
        Path source;
        try {
            source = definingJar(Class.forName("jdistlib.Normal", false, MonocleMathIdentity.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError failure) {
            throw new IOException("Missing or unloadable original JDistlib numerical classes", failure);
        }
        var before = NextflowFiles.pin(source, MAX_JAR_BYTES, () -> { });
        try (var jar = new JarFile(source.toFile(), false)) {
            var found = new HashSet<String>();
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("META-INF/versions/") && name.contains("/jdistlib/"))
                    throw new IOException("Version-dependent JDistlib class overrides are unsupported");
                if (name.startsWith("jdistlib/") && name.endsWith(".class"))
                    require(found.add(name), "Duplicate JDistlib defining class: " + name);
            }
            require(found.equals(expected.keySet()), "Complete original JDistlib class population differs");
            for (var member : expected.entrySet()) {
                String name = NextflowFiles.text(member.getKey());
                var wanted = NextflowFiles.fingerprint(member.getValue(), 8388608);
                var entry = jar.getJarEntry(name);
                require(entry.getSize() == wanted.bytes(), "Original JDistlib class size differs: " + name);
                byte[] bytes;
                try (var input = jar.getInputStream(entry)) { bytes = input.readNBytes(8388609); }
                require(wanted.equals(new NextflowFiles.Pin(bytes.length, NextflowFiles.sha(bytes))), "Original JDistlib class bytes differ: " + name);
                // No class initializers run. Also reject class-path shadowing by a
                // second artifact, including anonymous/helper dependency classes.
                try {
                    String binaryName = name.substring(0, name.length() - 6).replace('/', '.');
                    Path defining = definingJar(Class.forName(binaryName, false, MonocleMathIdentity.class.getClassLoader()));
                    require(defining.equals(source), "JDistlib class loaded from a different artifact: " + binaryName);
                } catch (ClassNotFoundException | LinkageError failure) {
                    throw new IOException("Original JDistlib class cannot be loaded: " + name, failure);
                }
            }
        }
        require(before.equals(NextflowFiles.pin(source, MAX_JAR_BYTES, () -> { })), "Numerical defining artifact changed during inspection");
        boolean standalone = before.sha256().equals(Monocle131ModelCli.MATH_SHA);
        return Map.of("path", source.toString(), "pin", before, "packaging", standalone ? "original-standalone" : "bundled-original-classes",
            "original_archive_sha256", Monocle131ModelCli.MATH_SHA, "verified_classes", expected.size(),
            "class_profile_sha256", NextflowFiles.sha(raw), "producer_authenticated", false);
    }

    private static void require(boolean condition, String cause) throws IOException {
        if (!condition) throw new IOException(cause);
    }

    private static Path definingJar(Class<?> type) throws IOException {
        var domain = type.getProtectionDomain();
        if (domain == null || domain.getCodeSource() == null || !domain.getCodeSource().getLocation().getProtocol().equals("file"))
            throw new IOException("Require a local defining JAR for " + type.getName());
        try {
            Path path = Path.of(domain.getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Require a regular defining numerical JAR: " + path);
            return path.toRealPath();
        } catch (URISyntaxException failure) { throw new IOException("Invalid numerical code-source URI", failure); }
    }
}
