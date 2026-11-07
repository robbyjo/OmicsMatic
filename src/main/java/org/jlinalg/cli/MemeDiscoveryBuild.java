/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact official source profiles and complete original build/payload receipts, separate from live health. */
final class MemeDiscoveryBuild {
    private MemeDiscoveryBuild() { }
    static final String RESOURCE = "/org/jlinalg/omics/meme-discovery/source-profiles.json";
    static final NextflowFiles.Pin PROFILES = new NextflowFiles.Pin(385657, "5758184a7648122fda5f67ec9546feecf7d8390ea22ec638e7703207b3e92a4c");
    static final Set<String> BUILDERS = Set.of("7c791f202b66d754c5e33a842b7772af965485140560f8dbe0aaba8d81ec3fa6",
        "0fbfa0f01ffe5cd7fb8b23f0b8eb5e04336d807ef78496d1ba96563164dd87b7", "721975a90045c2ef168d6c1a1153a418933e5c25cfdd40141b130bfa6cadd988");
    record Context(String profile, String version, Path home, Map<String, NextflowFiles.Pin> installed,
                   Map<Path, NextflowFiles.Pin> evidence, Map<String, Object> receipt) {
        Context { installed = Map.copyOf(installed); evidence = Map.copyOf(evidence); receipt = Map.copyOf(receipt); }
        void verify(Runnable check) throws IOException {
            for (var e : evidence.entrySet()) require(e.getValue().equals(NextflowFiles.pin(e.getKey(), 268435456, check)), "Original MEME source/build receipt changed: " + e.getKey());
            var current = NextflowFiles.tree(home, 100000, 2147483648L, 268435456, check);
            require(current.keySet().equals(installed.keySet()), "Complete original MEME installed payload namespace changed");
            for (var e : current.entrySet()) require(e.getValue().kind().equals("file") && e.getValue().pin().equals(installed.get(e.getKey())), "Original installed MEME payload changed: " + e.getKey());
        }
        void capture(Path root, Runnable check) throws IOException {
            Files.createDirectory(root); var rows = new ArrayList<Map<String, Object>>(); int index = 0;
            for (var e : new TreeMap<>(evidence).entrySet()) {
                String name = String.format(java.util.Locale.ROOT, "%03d-%s", index++, e.getKey().getFileName()); Path target = root.resolve(name);
                byte[] raw = NextflowFiles.bytes(e.getKey(), Math.toIntExact(Math.min(268435456, e.getValue().bytes() + 1)));
                require(new NextflowFiles.Pin(raw.length, NextflowFiles.sha(raw)).equals(e.getValue()), "Original MEME evidence changed during capture");
                Files.write(target, raw); rows.add(Map.of("member", name, "producer_path", e.getKey().toString(), "identity", MemeRuntimeFiles.pin(e.getValue())));
            }
            MemeRuntimeFiles.write(root.resolve("BUILD.json"), Map.of("profile", profile, "version", version, "compiled_prefix", home.toString(),
                "source_profile", sourceProfile(profile), "build_completion", receipt, "files", rows,
                "scope", "Exact official archive/C-H population, supported build recipe and stable complete installed payload. This is provenance and integrity, not a signature or scientific admission."));
            verify(check);
        }
    }
    static Map<?, ?> sourceProfile(String profile) throws IOException {
        var all = NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(MemeRuntimeFiles.resource(RESOURCE, PROFILES))));
        require(all.keySet().equals(Set.copyOf(MemeDiscoveryRequest.PROFILES)), "Pinned original MEME profile population differs"); return NextflowFiles.object(all.get(profile));
    }
    static Context read(String profile, Path home, Path root, Runnable check) throws IOException {
        NextflowFiles.directory(root); NextflowFiles.directory(home); var evidence = new TreeMap<Path, NextflowFiles.Pin>();
        Path completion = root.resolve("COMPLETED.json"); include(completion, evidence, check);
        var report = NextflowFiles.object(NextflowFiles.json(completion, 2097152)); var spec = sourceProfile(profile);
        String version = NextflowFiles.text(spec.get("version")); require(report.get("version").equals(version), "Original MEME build edition differs from requested profile");
        boolean old = version.equals("4.9.1"), v559 = version.equals("5.5.9"), recovered = report.containsKey("original_build_steps");
        require(report.get("status").equals(v559 ? "built-original-Linux-MEME-Suite-reference-not-scientifically-admitted" : "built-original-historical-MEME-reference-not-scientifically-admitted"), "Require a fully completed original MEME build; partial/failed runtimes are unavailable");
        require(NextflowFiles.fingerprint(report.get(v559 ? "original_archive" : "archive"), 100000000).equals(NextflowFiles.fingerprint(spec.get("archive"), 100000000))
            && report.get("source_url").equals(spec.get("url")), "Original MEME official archive/source URL differs");
        require(report.get("original_C_and_H").equals(spec.get("scientific_sources"))
            && NextflowFiles.integer(report.get("unchanged_original_C_and_H"), 1, 10000) == NextflowFiles.object(spec.get("scientific_sources")).size(), "Complete original MEME scientific C/H source population differs");
        require(NextflowFiles.object(report.get("installed_symlinks")).isEmpty() && !report.containsKey("changed_original_C_and_H"), "Linked/changed original MEME payload or scientific source is unsupported");
        require(NextflowFiles.text(report.get("intended_use")).equals("educational-research-nonprofit; directly confirmed by user in this goal"), "Original MEME build must retain the directly confirmed educational/research/nonprofit use; no commercial permission is inferred");
        require(Boolean.FALSE.equals(report.get("runtime_admitted")) && Boolean.FALSE.equals(report.get(v559 ? "scientific_analysis_run" : "analysis_admitted")), "Build receipts cannot substitute for fresh runtime/scientific admission");
        require(NextflowFiles.text(report.get("prefix")).equals(home.toString()), "Original MEME prefix differs; compiled assets are not assumed relocatable");
        String area = NextflowFiles.text(report.get("linux_area")); TomtomRecipe.nativePath(area); MemeDiscoveryRequest.nativePath(home);
        if (old) require(report.get("edition").equals(profile.endsWith("PATCH2") ? "4.9.1-patch2" : "4.9.1-base"), "Unpatched and patch-2 MEME editions cannot alias");
        var builder = NextflowFiles.fingerprint(report.get(v559 ? "script_pin" : recovered ? "original_build_source" : "source"), 1048576);
        require(BUILDERS.contains(builder.sha256()), "Original MEME build recipe source is not one of the retained validated builders");
        Map<?, ?> original = report; Path logs = root;
        if (recovered) {
            var previous = NextflowFiles.object(report.get("failed_build")); Path failed = Path.of(NextflowFiles.text(previous.get("path")));
            require(failed.getFileName().toString().equals("FAILURE.json") && failed.toAbsolutePath().normalize().equals(failed)
                && failed.startsWith(root.getParent()) && !failed.startsWith(root), "Recovered MEME build parent must be a canonical sibling retained failure receipt");
            include(failed, evidence, check); require(evidence.get(failed).equals(NextflowFiles.fingerprint(previous, 2097152)), "Retained legacy build-parent receipt differs");
            original = NextflowFiles.object(NextflowFiles.json(failed, 2097152)); logs = failed.getParent();
            require(original.get("installed_files").equals(report.get("installed_files")) && original.get("original_C_and_H").equals(report.get("original_C_and_H")), "Recovered original MEME payload/source differs from its retained build");
            require(NextflowFiles.fingerprint(report.get("recovery_source"), 1048576).sha256().equals("7b10406e07beef7a7abdc4c28077fe34ec9b2e043066c471ccf8a3cee4dc1bc3"), "Legacy recovery recipe differs");
            include(root.resolve("RECOVERY-SOURCE.py"), evidence, check); require(evidence.get(root.resolve("RECOVERY-SOURCE.py")).equals(NextflowFiles.fingerprint(report.get("recovery_source"), 1048576)), "Retained legacy recovery source bytes differ");
            for (Object step : NextflowFiles.list(report.get("preparation_steps"))) step(NextflowFiles.object(step), root, evidence, check);
        }
        var steps = NextflowFiles.list(original.get("steps")); var scientific = new LinkedHashMap<String, Map<?, ?>>();
        for (Object value : steps) { var s = NextflowFiles.object(value); step(s, logs, evidence, check); scientific.put(NextflowFiles.text(s.get("name")), s); }
        require(scientific.keySet().containsAll(Set.of("configure", "make", "install")), "Missing original MEME configure/make/install steps");
        String python = v559 ? "/usr/bin/python3" : NextflowFiles.text(original.get("interpreter"));
        String sourceDirectory = area + (old ? "/meme_4.9.1" : "/meme-" + version);
        var configure = scientific.get("configure"); require(NextflowFiles.strings(configure.get("command")).equals(List.of("/bin/sh", "./configure", "--prefix=" + home,
            "--enable-serial", "--enable-build-libxml2", "--enable-build-libxslt", "--with-python=" + python)) && configure.get("cwd").equals(sourceDirectory), "Original MEME configure recipe differs");
        for (String name : List.of("make", "install")) {
            var s = scientific.get(name); var args = new ArrayList<>(List.of("/usr/bin/make", name.equals("make") ? "-j2" : "install"));
            if (old) args.add("CFLAGS=-O3 -fcommon -fgnu89-inline");
            require(NextflowFiles.strings(s.get("command")).equals(args) && s.get("cwd").equals(sourceDirectory), "Original MEME compiler/install recipe differs; no implicit source modification or compiler-flag substitution");
        }
        var installed = new TreeMap<String, NextflowFiles.Pin>(); long bytes = 0;
        for (var e : NextflowFiles.object(report.get("installed_files")).entrySet()) {
            String name = NextflowFiles.member(NextflowFiles.text(e.getKey())); var p = NextflowFiles.fingerprint(e.getValue(), 268435456); bytes = Math.addExact(bytes, p.bytes());
            require(bytes <= 2147483648L && installed.size() < 100000, "Complete original MEME payload exceeds capacity"); installed.put(name, p);
        }
        require(installed.containsKey("bin/meme") && installed.containsKey(old ? "bin/meme.bin" : "libexec/meme-" + version + "/meme_xml_to_html"), "Required original MEME executable/report helper is absent from its complete payload");
        require(containsPrefix(home.resolve(old ? "bin/meme.bin" : "bin/meme"), home.toString() + "/", check),
            "Original MEME binary does not embed the declared compiled prefix; rebuild into the intended prefix instead of editing/copying a receipt");
        var receipt = new LinkedHashMap<String, Object>(); for (var e : report.entrySet()) receipt.put(NextflowFiles.text(e.getKey()), e.getValue());
        var result = new Context(profile, version, home, installed, evidence, receipt); result.verify(check); return result;
    }
    private static void step(Map<?, ?> step, Path root, Map<Path, NextflowFiles.Pin> evidence, Runnable check) throws IOException {
        String name = NextflowFiles.text(step.get("name")); require(name.matches("[a-z0-9-]+") && NextflowFiles.integer(step.get("exit_code"), 0, 0) == 0 && Boolean.FALSE.equals(step.get("timed_out")), "Original MEME build/preparation step failed");
        Path log = root.resolve(name + ".log"); include(log, evidence, check); require(evidence.get(log).equals(NextflowFiles.fingerprint(step.get("log_pin"), 268435456)), "Complete original MEME build log differs: " + name);
    }
    private static void include(Path path, Map<Path, NextflowFiles.Pin> evidence, Runnable check) throws IOException { evidence.put(path, NextflowFiles.pin(path, 268435456, check)); }
    private static boolean containsPrefix(Path path, String prefix, Runnable check) throws IOException {
        byte[] wanted = prefix.getBytes(java.nio.charset.StandardCharsets.US_ASCII); int[] fallback = new int[wanted.length];
        for (int i = 1, j = 0; i < wanted.length; i++) {
            while (j > 0 && wanted[i] != wanted[j]) j = fallback[j - 1];
            if (wanted[i] == wanted[j]) fallback[i] = ++j;
        }
        int matched = 0;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] block = new byte[65536]; long bytes = 0;
            for (int n; (n = input.read(block)) >= 0;) {
                check.run(); bytes += n; require(bytes <= 268435456, "Complete MEME binary exceeds prefix-marker capacity");
                for (int i = 0; i < n; i++) {
                    while (matched > 0 && block[i] != wanted[matched]) matched = fallback[matched - 1];
                    if (block[i] == wanted[matched] && ++matched == wanted.length) return true;
                }
            }
        }
        return false;
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
