/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Complete portable setup receipts; producer paths remain opaque and no runtime is admitted here. */
final class StringTieSetupEvidence {
    private StringTieSetupEvidence() { }
    record Limits(int metadataBytes, long totalBytes, int members) {
        Limits { if (metadataBytes < 1024 || metadataBytes > 2097152 || totalBytes < metadataBytes || totalBytes > 134217728 || members < 4 || members > 1000) throw new IllegalArgumentException("Invalid complete StringTie setup-evidence capacities"); }
        static Limits defaults() { return new Limits(1048576, 8388608, 64); }
    }
    record Receipt(Path root, String profile, String version, String producerHome, String producerReport,
                   NextflowFiles.Pin manifest, NextflowFiles.Pin marker, Map<String, Object> inventory) {
        Receipt { inventory = Map.copyOf(inventory); }
        void verify(Limits limits, Runnable check) throws IOException {
            require(inventory.equals(tree(root, limits, check)) && manifest.equals(NextflowFiles.pin(root.resolve("manifest.json"), limits.metadataBytes(), check)), "Complete StringTie setup evidence changed");
        }
    }
    static Map<?, ?> lock() throws IOException {
        byte[] raw = MemeRuntimeFiles.resource(StringTieSetupExport.RESOURCE + "stringtie-runtime-lock.json", StringTieSetupExport.LOCK);
        Map<?, ?> lock = object(SimpleJson.parse(NextflowFiles.utf8(raw)));
        require(NextflowFiles.integer(lock.get("schema_version"), 1, 1) == 1 && object(lock.get("profiles")).size() == 7
                && Boolean.FALSE.equals(lock.get("native_runtime_admitted")) && Boolean.FALSE.equals(lock.get("user_analysis_admitted")), "Invalid original StringTie setup lock scope");
        return lock;
    }
    static Map<?, ?> profile(String name) throws IOException {
        Map<?, ?> profiles = object(lock().get("profiles"));
        require(profiles.containsKey(name), "Unknown exact original StringTie runtime profile; no edition substitution"); return object(profiles.get(name));
    }
    static Receipt read(Path root, Limits limits, Runnable check) throws IOException {
        Objects.requireNonNull(limits); Objects.requireNonNull(check); Map<String, Object> before = tree(root, limits, check);
        Map<?, ?> complete = json(root.resolve("COMPLETED.json"), limits);
        NextflowFiles.keys(complete, "status", "manifest", "scientific_runtime_admitted", "user_analysis_admitted");
        require(text(complete.get("status")).equals("COMPLETE_SETUP_ONLY_NOT_ADMISSION") && noAdmission(complete), "StringTie setup completion cannot admit a scientific runtime or user analysis");
        NextflowFiles.Pin manifestPin = NextflowFiles.pin(root.resolve("manifest.json"), limits.metadataBytes(), check);
        require(manifestPin.equals(NextflowFiles.fingerprint(complete.get("manifest"), limits.metadataBytes())), "StringTie setup manifest completion hash differs");
        Map<?, ?> manifest = json(root.resolve("manifest.json"), limits);
        NextflowFiles.keys(manifest, "schema_version", "status", "profile", "version", "home", "payload", "files", "downloads", "scientific_runtime_admitted", "user_analysis_admitted");
        require(NextflowFiles.integer(manifest.get("schema_version"), 1, 1) == 1 && noAdmission(manifest), "Invalid StringTie setup manifest schema/scope");
        String profile = text(manifest.get("profile")), version = text(manifest.get("version")), home = linuxPath(manifest.get("home"));
        Map<?, ?> spec = profile(profile); require(version.equals(text(spec.get("version"))), "Exact StringTie setup edition differs from selected profile");
        String status = text(manifest.get("status")); require(Set.of("INSTALLED_EXACT_PAYLOAD_ONLY", "RECHECKED_EXACT_PAYLOAD_ONLY").contains(status), "Unknown StringTie setup operation");
        Map<?, ?> declared = object(manifest.get("files")); var expected = new HashSet<>(Set.of("request.json", "WARNINGS.md"));
        if (status.equals("INSTALLED_EXACT_PAYLOAD_ONLY")) expected.add("staging.json");
        require(declared.keySet().equals(expected), "Incomplete/extra StringTie setup report file population");
        for (var e : declared.entrySet()) {
            String name = NextflowFiles.member(text(e.getKey())); require(!name.contains("/"), "Nested setup report member is not admitted");
            require(NextflowFiles.fingerprint(e.getValue(), limits.metadataBytes()).equals(NextflowFiles.pin(root.resolve(name), limits.metadataBytes(), check)), "Complete StringTie setup report file hash differs: " + name);
        }
        var namespace = new HashSet<>(expected); namespace.addAll(Set.of(".", "manifest.json", "COMPLETED.json"));
        require(before.keySet().equals(namespace), "StringTie setup report has extra/missing files, partial guards or failure markers");
        for (var e : before.entrySet()) require(text(object(e.getValue()).get("kind")).equals(e.getKey().equals(".") ? "directory" : "file"), "Invalid setup evidence member type");
        Map<?, ?> payload = object(manifest.get("payload")); NextflowFiles.keys(payload, "files", "marker");
        Map<?, ?> files = object(payload.get("files")), originals = object(spec.get("installed_files")); require(files.keySet().equals(originals.keySet()), "Exact original StringTie payload namespace differs");
        for (var e : originals.entrySet()) require(NextflowFiles.fingerprint(files.get(e.getKey()), 20000000).equals(NextflowFiles.fingerprint(e.getValue(), 20000000)), "Setup declared payload differs from exact original profile");
        NextflowFiles.Pin marker = NextflowFiles.fingerprint(payload.get("marker"), limits.metadataBytes()); require(marker.bytes() > 0, "Missing protected StringTie managed marker declaration");
        Map<?, ?> request = json(root.resolve("request.json"), limits);
        NextflowFiles.keys(request, "profile", "version", "home", "report", "cache", "kit", "allow_install", "allow_download", "installer", "lock", "scientific_runtime_admitted", "user_analysis_admitted");
        require(noAdmission(request) && text(request.get("profile")).equals(profile) && text(request.get("version")).equals(version) && linuxPath(request.get("home")).equals(home), "Setup request profile/home/scope differs");
        String report = linuxPath(request.get("report")); linuxPath(request.get("cache")); linuxPath(request.get("kit"));
        require(request.get("allow_install") instanceof Boolean && request.get("allow_download") instanceof Boolean, "Setup permission flags must remain explicit booleans");
        require(NextflowFiles.fingerprint(request.get("installer"), limits.metadataBytes()).equals(StringTieSetupExport.INSTALLER)
                && NextflowFiles.fingerprint(request.get("lock"), limits.metadataBytes()).equals(StringTieSetupExport.LOCK), "Setup did not bind the exact pinned installer and original-runtime lock");
        if (status.equals("INSTALLED_EXACT_PAYLOAD_ONLY")) {
            require(Boolean.TRUE.equals(request.get("allow_install")), "Setup installed without declared permission");
            Map<?, ?> staging = json(root.resolve("staging.json"), limits); NextflowFiles.keys(staging, "path", "status");
            linuxPath(staging.get("path")); require(text(staging.get("status")).equals("PRESERVE_IF_FAILED"), "Invalid setup staging disposition");
        }
        var downloads = NextflowFiles.list(manifest.get("downloads")); var acquired = new HashSet<String>();
        for (Object raw : downloads) {
            require(Boolean.TRUE.equals(request.get("allow_download")) && status.equals("INSTALLED_EXACT_PAYLOAD_ONLY"), "Downloaded setup archive lacks declared acquisition permission");
            Map<?, ?> item = object(raw); NextflowFiles.keys(item, "kind", "url", "cache", "bytes", "sha256"); String kind = text(item.get("kind"));
            require(Set.of("binary", "source").contains(kind) && acquired.add(kind), "Unexpected/duplicate setup download role");
            Map<?, ?> asset = object(object(spec.get("assets")).get(kind));
            require(text(item.get("url")).equals(text(asset.get("url"))) && NextflowFiles.fingerprint(item, 30000000).equals(NextflowFiles.fingerprint(asset, 30000000)), "Setup archive acquisition differs from exact official source lock");
            linuxPath(item.get("cache"));
        }
        require(before.equals(tree(root, limits, check)), "StringTie setup evidence changed during complete portable verification");
        return new Receipt(root, profile, version, home, report, manifestPin, marker, before);
    }
    static Map<String, Object> tree(Path root, Limits limits, Runnable check) throws IOException {
        return MemeRuntimeFiles.tree(root, new MemeRuntimeFiles.Limits(limits.metadataBytes(), limits.totalBytes(), limits.members()), check);
    }
    private static boolean noAdmission(Map<?, ?> row) { return Boolean.FALSE.equals(row.get("scientific_runtime_admitted")) && Boolean.FALSE.equals(row.get("user_analysis_admitted")); }
    private static Map<?, ?> json(Path file, Limits limits) throws IOException { return object(NextflowFiles.json(file, limits.metadataBytes())); }
    private static Map<?, ?> object(Object value) throws IOException { return NextflowFiles.object(value); }
    private static String text(Object value) throws IOException { return NextflowFiles.text(value); }
    private static String linuxPath(Object value) throws IOException {
        String path = text(value); require(path.startsWith("/") && path.length() > 1 && path.chars().allMatch(c -> c > 32 && c < 127) && path.indexOf('\\') < 0, "Invalid opaque original Linux setup path");
        for (String part : path.substring(1).split("/", -1)) require(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "Noncanonical opaque original setup path"); return path;
    }
    private static void require(boolean value, String reason) throws IOException { if (!value) throw new IOException(reason); }
}
