/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Exact private upstream container runtime for source-specific read-only postfit inspection. */
final class NfcoreDeseq126Runtime {
    private static final String RESOURCE = "/org/jlinalg/omics/nfcore-deseq126/";
    private static final Pin INVENTORY = new Pin(60641, "ac8d0d9c991dba63e79b526f39ae95ec23d0923b2629bb362b3fee14b582d3ec");
    private static final Pin IMAGE = new Pin(1131143168L, "50bd3f475b82608dd305138dff30b4789973d930994a5f710a7e5dd8acbb1e38");
    private static final Pin PROFILE = new Pin(2047, "30e7e4e12feecf26f197ba8f3f17bfe1ac02b49ecec587ab269689b1991befa0");
    private final Path home, image, root, worker;
    private final Map<?, ?> inventory;
    private final Map<String, Pin> guards;
    private final Pin workerPin;
    private final NextflowProcess.Limits limits;

    NfcoreDeseq126Runtime(Path home, Path image, Path root, NextflowProcess.Limits limits) throws IOException {
        if (!System.getProperty("os.name").equals("Linux")) throw new NextflowProcess.Unavailable("Original DESeq2 postfit inspection requires Linux Java, the pinned private Apptainer runtime and original ChIP-seq1.2.1 image. Analysis skipped.");
        this.root = directory(root); this.home = home.toAbsolutePath().normalize(); this.image = image.toAbsolutePath().normalize(); this.limits = limits;
        if (!Files.exists(this.home, LinkOption.NOFOLLOW_LINKS) || !Files.exists(this.image, LinkOption.NOFOLLOW_LINKS)) throw new NextflowProcess.Unavailable("Pinned Apptainer home or original image is missing. Install/download the permitted exact prerequisites separately and retry a NEW audit directory. Analysis skipped.");
        for (String name : List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/bin/bash", "/usr/bin/realpath", "/usr/bin/uname"))
            if (!Files.isExecutable(Path.of(name))) throw new NextflowProcess.Unavailable("Required Linux executable is missing or forbidden: " + name + ". Install it if permitted; analysis skipped.");
        if (!System.getProperty("os.arch").equals("amd64") && !System.getProperty("os.arch").equals("x86_64")) throw new NextflowProcess.Unavailable("Pinned original image/runtime require x86_64 Linux. Analysis skipped.");
        directory(this.home);
        if (this.root.startsWith(this.home) || this.home.startsWith(this.root) || this.image.startsWith(this.root)) throw new IOException("Audit workspace, runtime and image must be disjoint");
        for (Path path : List.of(this.root, this.home, this.image)) if (path.toString().chars().anyMatch(c -> c < 32 || c == 127 || c == ':' || c == ',')) throw new IOException("Container path cannot be represented by exact bind arguments; no path escaping inferred");
        byte[] raw = resource("runtime-inventory.json", 1048576);
        if (!new Pin(raw.length, sha(raw)).equals(INVENTORY)) throw new IOException("Private runtime inventory resource differs");
        inventory = object(SimpleJson.parse(utf8(raw)));
        byte[] profileRaw = resource("worker-lock.json", 1048576);
        if (!new Pin(profileRaw.length, sha(profileRaw)).equals(PROFILE)) throw new IOException("Original postfit source-lock resource differs");
        Map<?, ?> profile = object(SimpleJson.parse(utf8(profileRaw)));
        keys(profile, "schema_version", "worker", "source_guards");
        if (integer(profile.get("schema_version"), 1, 1) != 1) throw new IOException("Original postfit source-lock schema differs");
        workerPin = fingerprint(profile.get("worker"), 1048576); guards = new LinkedHashMap<>();
        for (var entry : object(profile.get("source_guards")).entrySet()) guards.put(member(text(entry.getKey())), fingerprint(entry.getValue(), 1048576));
        if (guards.size() != 13) throw new IOException("Incomplete original DESeq2/genefilter source guards");
        worker = root.resolve("inspect.R"); byte[] script = resource("inspect.R", 1048576);
        if (!new Pin(script.length, sha(script)).equals(workerPin)) throw new IOException("Original postfit worker bytes differ from source lock");
        Files.write(worker, script, StandardOpenOption.CREATE_NEW);
        Files.createDirectory(root.resolve("container-cache")); Files.createDirectory(root.resolve("container-tmp"));
        verify();
    }

    void health() throws IOException {
        var version = NfcoreProcess.executeSession(List.of(home.resolve("bin/apptainer").toString(), "version"), environment(), root.resolve("version-process"), root, root, limits, this::verify, () -> { });
        if (!utf8(bytes(version.stdout(), 1024)).equals("1.5.5-1\n")) throw new IOException("Original Apptainer literal provider version differs; no build suffix stripped");
        try { NfcoreProcess.executeSession(container(List.of("/bin/true")), environment(), root.resolve("container-health"), root, root, limits, this::verify, () -> { }); }
        catch (IOException failure) {
            Path ledger = root.resolve("container-health/process.json"), stderr = root.resolve("container-health/process.stderr");
            // Only a cleanly retired, explicit OS namespace prohibition is an unavailable prerequisite.
            if (Files.exists(ledger) && Files.exists(stderr)) {
                Map<?, ?> record = object(json(ledger, 8388608)); String message = utf8(bytes(stderr, 1048576));
                if (record.containsKey("exit_code") && integer(record.get("exit_code"), 0, 255) != 0 && list(record.get("owned_live_after_cleanup")).isEmpty()
                        && (!record.containsKey("suppressed") || list(record.get("suppressed")).isEmpty()) && !record.containsKey("cleanup_state")
                        && text(record.get("error")).contains("Original Nextflow process failed, exit ")
                        && (message.contains("Failed to create user namespace") || message.contains("user namespace disabled") || message.contains("Unprivileged user namespace") && message.contains("not permitted")))
                    throw new NextflowProcess.Unavailable("Operating environment forbids the required original unprivileged container namespace. Ask the environment administrator for an allowed container runtime, then retry a NEW directory. Analysis skipped. Original error: " + message);
            }
            throw failure;
        }
    }

    Path inspect(Path originalModel, Path originalCounts, String bamSuffix, Path caseRoot) throws IOException {
        directory(caseRoot);
        if (!caseRoot.startsWith(root) || bamSuffix.isEmpty() || bamSuffix.chars().anyMatch(c -> c < 32 || c == 127)) throw new IOException("Unrepresentable exact original worker case");
        Path model = stage(originalModel, caseRoot.resolve("original.RData")), counts = stage(originalCounts, caseRoot.resolve("featureCounts.txt"));
        Pin modelPin = pin(model, 1000000000L, () -> { }), countsPin = pin(counts, 1000000000L, () -> { });
        Path result = caseRoot.resolve("worker");
        NextflowProcess.Check integrity = () -> {
            verify();
            if (!pin(model, 1000000000L, () -> { }).equals(modelPin) || !pin(counts, 1000000000L, () -> { }).equals(countsPin)
                    || !pin(originalModel, 1000000000L, () -> { }).equals(modelPin) || !pin(originalCounts, 1000000000L, () -> { }).equals(countsPin)) throw new IOException("Complete original/staged postfit inputs changed");
        };
        NfcoreProcess.executeSession(container(List.of("Rscript", "--vanilla", worker.toString(), model.toString(), counts.toString(), bamSuffix, result.toString())), environment(), caseRoot.resolve("process"), caseRoot, root, limits, integrity, () -> { });
        for (var entry : guards.entrySet()) if (!pin(result.resolve("source-guards").resolve(entry.getKey()), 1048576, () -> { }).equals(entry.getValue())) throw new IOException("Actual original R package function differs: " + entry.getKey());
        try (var paths = Files.list(result.resolve("source-guards"))) {
            if (!paths.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(guards.keySet())) throw new IOException("Actual source guard family differs");
        }
        if (!utf8(bytes(result.resolve("WORKER_COMPLETED.txt"), 1024)).equals("COMPLETE_ORIGINAL_DESEQ126_POSTFIT_EXPORT_ONLY\n")) throw new IOException("Original postfit worker did not complete");
        integrity.verify();
        return result;
    }

    private Path stage(Path source, Path target) throws IOException {
        Pin before = pin(source, 1000000000L, () -> { }); Files.copy(source, target);
        if (!pin(target, 1000000000L, () -> { }).equals(before) || !pin(source, 1000000000L, () -> { }).equals(before)) throw new IOException("Original worker input changed during complete staging"); return target;
    }
    void verify() throws IOException {
        directory(home);
        var seen = new HashSet<String>();
        Files.walkFileTree(home, new SimpleFileVisitor<>() {
            private String name(Path path) { return home.relativize(path).toString(); }
            private Map<?, ?> expected(Path path, String kind) throws IOException {
                String key = name(path); seen.add(key); Map<?, ?> entry = object(inventory.get(key));
                if (!kind.equals(entry.get("kind"))) throw new IOException("Original runtime member kind differs: " + key); return entry;
            }
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                if (!path.equals(home)) { var entry = expected(path, "directory"); keys(entry, "kind", "mode"); if (mode(path) != integer(entry.get("mode"), 0, 0777)) throw new IOException("Original runtime directory mode differs: " + name(path)); }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) {
                    var entry = expected(path, "symlink"); keys(entry, "kind", "target");
                    if (!Files.readSymbolicLink(path).toString().equals(text(entry.get("target"))) || !path.toRealPath().startsWith(home)) throw new IOException("Original runtime link differs or escapes: " + name(path));
                } else if (attrs.isRegularFile()) {
                    var entry = expected(path, "file"); keys(entry, "kind", "mode", "bytes", "sha256");
                    if (!pin(path, 1000000000L, () -> { }).equals(fingerprint(entry, 1000000000L)) || mode(path) != integer(entry.get("mode"), 0, 0777)) throw new IOException("Original runtime bytes/mode differ: " + name(path));
                } else throw new IOException("Nonregular original container runtime member");
                return FileVisitResult.CONTINUE;
            }
        });
        if (!seen.equals(inventory.keySet())) throw new IOException("Incomplete original runtime inventory");
        if (!pin(image, IMAGE.bytes(), () -> { }).equals(IMAGE) || !pin(worker, 1048576, () -> { }).equals(workerPin)) throw new IOException("Original image or postfit worker integrity differs");
    }
    private List<String> container(List<String> command) {
        var result = new ArrayList<>(List.of(home.resolve("bin/apptainer").toString(), "exec", "--userns", "--no-home", "--pid", "--cleanenv", "-B", root + ":" + root, image.toString())); result.addAll(command); return result;
    }
    private Map<String, String> environment() {
        return Map.of("PATH", "/usr/bin:/bin", "LANG", "C.UTF-8", "LC_ALL", "C.UTF-8", "APPTAINER_CACHEDIR", root.resolve("container-cache").toString(), "APPTAINER_TMPDIR", root.resolve("container-tmp").toString(), "APPTAINERENV_LC_ALL", "C.UTF-8", "APPTAINERENV_LANG", "C.UTF-8");
    }
    private static byte[] resource(String name, int max) throws IOException {
        try (var input = NfcoreDeseq126Runtime.class.getResourceAsStream(RESOURCE + name)) {
            if (input == null) throw new IOException("Missing original worker/runtime resource: " + name); byte[] raw = input.readNBytes(max + 1); if (raw.length > max) throw new IOException("Original worker resource exceeds capacity"); return raw;
        }
    }
}
