/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete source-declared private image set and exact permitted container runtime. No installation. */
final class NfcoreContainerSet {
    private static final Pin INVENTORY = new Pin(60641, "ac8d0d9c991dba63e79b526f39ae95ec23d0923b2629bb362b3fee14b582d3ec");
    private static final Pin PROFILE = new Pin(17144, "08c51df1b7b00636f4889d5607e155930b0fa34c5ebb8bf1a8baaad1ff536c58");
    private final Path home, root;
    private final Map<?, ?> inventory, profile;
    private final Map<String, Path> images = new LinkedHashMap<>();
    private final Map<String, Pin> pins = new LinkedHashMap<>();
    private final NextflowProcess.Limits limits;

    NfcoreContainerSet(Path home, Path declaration, Path root, NextflowProcess.Limits limits) throws IOException {
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch"))) throw new NextflowProcess.Unavailable("Original source-declared images require x86_64 Linux Java and permitted unprivileged containers. Analysis skipped.");
        this.home = home; this.root = directory(root); this.limits = limits;
        if (!Files.exists(home, LinkOption.NOFOLLOW_LINKS)) throw new NextflowProcess.Unavailable("Exact admitted private Apptainer home is missing. Complete permitted setup separately; dependent analysis skipped.");
        directory(home);
        if (root.startsWith(home) || home.startsWith(root)) throw new IOException("Container runtime and owned workspace must be disjoint");
        for (String name : List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/bin/bash", "/usr/bin/realpath", "/usr/bin/uname")) if (!Files.isExecutable(Path.of(name))) throw new NextflowProcess.Unavailable("Required Linux helper is missing/forbidden: " + name + "; dependent analysis skipped.");
        byte[] raw = resource("/org/jlinalg/omics/nfcore-deseq126/runtime-inventory.json", 1048576); if (!new Pin(raw.length, sha(raw)).equals(INVENTORY)) throw new IOException("Pinned container runtime inventory differs"); inventory = object(SimpleJson.parse(utf8(raw)));
        raw = resource("/org/jlinalg/omics/nfcore-chipseq200/images.json", 1048576); if (!new Pin(raw.length, sha(raw)).equals(PROFILE)) throw new IOException("Pinned ChIP-seq2.0.0 module/image profile differs"); profile = object(SimpleJson.parse(utf8(raw)));
        Map<?, ?> expected = object(profile.get("images")), supplied = object(json(declaration, 1048576)); keys(supplied, "schema_version", "images"); integer(supplied.get("schema_version"), 1, 1); Map<?, ?> paths = object(supplied.get("images"));
        if (!expected.keySet().containsAll(paths.keySet())) throw new IOException("Image declaration contains an unknown source URI");
        if (!paths.keySet().equals(expected.keySet())) throw new NextflowProcess.Unavailable("Incomplete exact source-declared image set. Supply every named image after permitted separate setup; dependent analysis skipped.");
        for (var row : expected.entrySet()) {
            String uri = text(row.getKey()); Path path = Path.of(text(paths.get(uri)));
            if (!path.isAbsolute() || !path.normalize().equals(path) || path.toString().chars().anyMatch(c -> c < 32 || c == 127 || c == ':' || c == ',')) throw new IOException("Declare canonical absolute image paths representable by the original container bind syntax");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new NextflowProcess.Unavailable("Required source image is missing: " + uri + " at " + path + ". Download/install separately only where permitted; dependent analysis skipped.");
            if (path.startsWith(root)) throw new IOException("Private original image must be disjoint from owned runtime work");
            images.put(uri, path); pins.put(uri, fingerprint(object(row.getValue()).get("pin"), 5000000000L));
        }
        Files.createDirectory(root.resolve("container-cache")); Files.createDirectory(root.resolve("container-tmp")); verify();
    }

    void verify() throws IOException { verifyRuntime(); for (String uri : images.keySet()) verifyImage(uri); }
    private void verifyImage(String uri) throws IOException { if (!pin(images.get(uri), 5000000000L, () -> { }).equals(pins.get(uri))) throw new IOException("Complete original image bytes changed: " + uri); }
    private void verifyRuntime() throws IOException {
        directory(home); var seen = new HashSet<String>();
        Files.walkFileTree(home, new SimpleFileVisitor<>() {
            private Map<?, ?> expected(Path path, String kind) throws IOException { String name = home.relativize(path).toString(); seen.add(name); Map<?, ?> row = object(inventory.get(name)); if (!kind.equals(row.get("kind"))) throw new IOException("Original runtime member kind differs: " + name); return row; }
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException { if (!path.equals(home)) { Map<?, ?> row = expected(path, "directory"); keys(row, "kind", "mode"); if (mode(path) != integer(row.get("mode"), 0, 0777)) throw new IOException("Original runtime directory mode differs: " + path); } return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) { Map<?, ?> row = expected(path, "symlink"); keys(row, "kind", "target"); if (!Files.readSymbolicLink(path).toString().equals(text(row.get("target"))) || !path.toRealPath().startsWith(home)) throw new IOException("Original runtime link differs/escapes: " + path); }
                else if (attrs.isRegularFile()) { Map<?, ?> row = expected(path, "file"); keys(row, "kind", "mode", "bytes", "sha256"); if (mode(path) != integer(row.get("mode"), 0, 0777) || !pin(path, 1000000000L, () -> { }).equals(fingerprint(row, 1000000000L))) throw new IOException("Original runtime byte/mode integrity differs: " + path); }
                else throw new IOException("Nonregular original container runtime member: " + path);
                return FileVisitResult.CONTINUE;
            }
        });
        if (!seen.equals(inventory.keySet())) throw new IOException("Incomplete original runtime inventory");
    }

    void health() throws IOException {
        verify(); var version = NfcoreProcess.executeSession(List.of(home.resolve("bin/apptainer").toString(), "version"), environment(), root.resolve("version-process"), root, root, limits, this::verifyRuntime, () -> { });
        if (!utf8(bytes(version.stdout(), 1024)).equals("1.5.5-1\n")) throw new IOException("Literal original Apptainer provider version differs; no suffix stripped");
        var results = new ArrayList<Map<String, Object>>(); int i = 0;
        for (String uri : images.keySet()) {
            Path record = root.resolve(String.format(java.util.Locale.ROOT, "image-%03d-health", ++i)); var command = new ArrayList<>(List.of(home.resolve("bin/apptainer").toString(), "exec", "--userns", "--no-home", "--pid", "--cleanenv", "-B", root + ":" + root, images.get(uri).toString(), "/bin/true"));
            try { NfcoreProcess.executeSession(command, environment(), record, root, root, limits, () -> { verifyRuntime(); verifyImage(uri); }, () -> { }); }
            catch (IOException failure) {
                if (Files.exists(record.resolve("process.json")) && Files.exists(record.resolve("process.stderr"))) {
                    Map<?, ?> ledger = object(json(record.resolve("process.json"), 67108864)); String error = utf8(bytes(record.resolve("process.stderr"), 1048576));
                    if (ledger.containsKey("exit_code") && integer(ledger.get("exit_code"), 0, 255) != 0 && list(ledger.get("owned_live_after_cleanup")).isEmpty() && !ledger.containsKey("cleanup_state") && (!ledger.containsKey("suppressed") || list(ledger.get("suppressed")).isEmpty()) && text(ledger.get("error")).contains("Original Nextflow process failed, exit ") && (error.contains("Failed to create user namespace") || error.contains("user namespace disabled") || error.contains("Unprivileged user namespace") && error.contains("not permitted"))) throw new NextflowProcess.Unavailable("Operating environment forbids the required unprivileged namespace for " + uri + ". Use an allowed runtime/environment; dependent analysis skipped. Retained stderr: " + record.resolve("process.stderr"));
                }
                throw new IOException("Source image health failed for " + uri + "; inspect " + record.resolve("process.stderr") + " and process.json. " + failure, failure);
            }
            results.add(Map.of("source_uri", uri, "image", images.get(uri).toString(), "pin", pins.get(uri), "process", pin(record.resolve("process.json"), 67108864, () -> { })));
        }
        verify(); writeJson(root.resolve("CONTAINER_HEALTH_COMPLETED.json"), Map.of("status", "complete-source-image-namespace-health-only", "images", results, "scientific_analysis_executed", false, "biological_accuracy_certified", false));
    }

    String overlay(Path engineRoot, int cpus, long memoryBytes) throws IOException {
        if (cpus < 1 || cpus > 1024 || memoryBytes < 16777216 || memoryBytes > 1099511627776L) throw new IOException("Invalid explicit local executor capacities");
        var result = new StringBuilder("process.executor = 'local'\nexecutor.cpus = ").append(cpus).append("\nexecutor.memory = '").append(memoryBytes).append(" B'\nsingularity.cacheDir = ").append(quote(engineRoot.resolve("workspace/execution/container-cache").toString())).append("\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\nprocess {\n");
        var seen = new HashSet<String>();
        for (var row : object(profile.get("images")).entrySet()) for (String name : strings(object(row.getValue()).get("process_names"))) { if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || !seen.add(name)) throw new IOException("Ambiguous original process/alias image binding"); result.append("  withName: ").append(quote(name)).append(" { container = ").append(quote(images.get(text(row.getKey())).toString())).append(" }\n"); }
        return result.append("}\n").toString();
    }
    Map<String, String> environment() { return Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "LC_ALL", "C.UTF-8", "TZ", "UTC", "APPTAINER_CACHEDIR", root.resolve("container-cache").toString(), "APPTAINER_TMPDIR", root.resolve("container-tmp").toString(), "APPTAINERENV_LC_ALL", "C.UTF-8", "APPTAINERENV_LANG", "C.UTF-8"); }
    private static String quote(String value) { return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"; }
    private static byte[] resource(String name, int maximum) throws IOException { try (var input = NfcoreContainerSet.class.getResourceAsStream(name)) { if (input == null) throw new IOException("Missing pinned container resource: " + name); byte[] raw = input.readNBytes(maximum + 1); if (raw.length > maximum) throw new IOException("Pinned container resource exceeds capacity"); return raw; } }
}
