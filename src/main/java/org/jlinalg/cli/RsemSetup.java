/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Explicit compiler declarations and receipt-pinned reopening; opening is not scientific admission. */
final class RsemSetup {
    private RsemSetup() { }
    static RsemBuild.Tools tools(Path supplied) throws IOException {
        Path file = supplied.toAbsolutePath().normalize(); return tools(file, AdmixtureInputs.raw(file, 2097152));
    }
    static RsemBuild.Tools tools(Path file, byte[] raw) throws IOException {
        var m = RepeatBundle.object(raw);
        RsemRecipe.keys(m, Set.of("cc", "cxx", "make", "ar", "ranlib", "roots", "includes", "libraries"));
        return new RsemBuild.Tools(path(file, m, "cc"), path(file, m, "cxx"), path(file, m, "make"), path(file, m, "ar"), path(file, m, "ranlib"),
                RsemRecipe.paths(file, m.get("roots")), RsemRecipe.paths(file, m.get("includes")), RsemRecipe.paths(file, m.get("libraries")));
    }
    private static Path path(Path file, Map<?,?> m, String key) throws IOException { return RsemRecipe.resolve(file, RsemRecipe.text(m, key, false)); }
    static RsemBuild.Context open(String version, Path home, String expectedReceipt, List<Path> libraries, Path manifest, Runnable check) throws IOException {
        RsemPayload.originalPath(home);
        if (!Files.isDirectory(home)) throw new RsemProcess.UnavailableDependency("Required RSEM source build is missing or unavailable: " + home + ". Run permitted rsem-tools --method setup or supply an existing verified build; skip dependent fitting until it is available.");
        if (!home.toRealPath().equals(home) || !expectedReceipt.matches("[0-9a-f]{64}")) throw new IOException("Require a canonical source build and explicit observed receipt SHA256");
        byte[] raw = AdmixtureInputs.raw(home.resolve("build.json"), 67108864);
        if (!AdmixtureInputs.digest(raw).equals(expectedReceipt)) throw new IOException("RSEM source-build receipt changed; preserve the old receipt and review a NEW setup");
        var record = RepeatBundle.object(raw); var profile = RsemPayload.profiles().get(version);
        if (profile == null || !"COMPLETED_EXACT_ORIGINAL_SOURCE_BUILD".equals(record.get("status")) || !RsemPayload.LOCK.equals(record.get("source_profile_lock_sha256"))) throw new IOException("Unreviewed RSEM source build or source-profile lock");
        var sourceProfile = RsemRecipe.object(record.get("profile")); var compiler = RsemRecipe.object(record.get("tools"));
        if (!profile.commit().equals(sourceProfile.get("commit")) || !profile.archiveSha256().equals(sourceProfile.get("archiveSha256")) || !libraries.stream().map(Path::toString).toList().equals(compiler.get("libraries"))) throw new IOException("RSEM source identity or declared library directories differ from the observed build receipt");
        Path source = home.resolve("RSEM-" + profile.commit()), decoder = home.resolve("outputs/omicsmatic-rsem-decode");
        var graph = RsemFileGraph.capture(List.of(source), List.of(decoder, home.resolve("inputs/decoder.cpp"), home.resolve("inputs/original-source.tar.gz")), RsemBuild.Limits.defaults().compiler(), manifest, check);
        if (!graph.sha256().equals(RsemRecipe.object(record.get("source_graph")).get("sha256"))) throw new IOException("Complete RSEM compiled-source graph differs from its observed build receipt");
        var evidence = new TreeMap<String,String>();
        for (var row : RsemRecipe.object(record.get("evidence")).entrySet()) {
            if (!(row.getKey() instanceof String name) || !(row.getValue() instanceof String sha) || name.startsWith("/") || name.contains("\\") || Arrays.stream(name.split("/", -1)).anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")) || !sha.matches("[0-9a-f]{64}") || !home.resolve(name).toRealPath().equals(home.resolve(name))) throw new IOException("Unreviewed RSEM source-build evidence path");
            evidence.put(name, sha);
        }
        var result = new RsemBuild.Context(home, profile, source, decoder, libraries, expectedReceipt, graph, evidence); result.verify(check); return result;
    }
}
