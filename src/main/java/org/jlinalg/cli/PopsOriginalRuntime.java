/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Executors;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Complete adopted software membership and byte checks, with original source pins. */
final class PopsOriginalRuntime {
    static final Map<String, String> SOURCES = Map.of(
        "0.1/pops.feature_selection.py", "833c2569dff7e13f0f987534d52849d99270992fbdb46045dbc5dfb4a2b2d4bc",
        "0.1/pops.predict_scores.py", "5b1f7399151863a7aa756c6cd8b367b620067cf89e164703d54392e4f475fb75",
        "0.1/LICENSE", "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986",
        "0.2/pops.py", "35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc",
        "0.2/munge_feature_directory.py", "3935b638693b4a72add556a1be9bc4e70d08e55305076a23f5cc08c2675c3102",
        "0.2/LICENSE", "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986");
    static final List<String> RECORDS = List.of("runtime.properties", "runtime-files.tsv", "SOURCE.json", "READY.json", "health.stdout", "HEALTH-PROCESS.json", "ELF-DEPENDENCIES.json", "pixi.toml", "pixi.lock");
    record Runtime(Path root, Path environment, Path sources, Path python, int entries) { }
    private record Entry(String root, String kind, String name, String target, String sha, Path path) { }
    private PopsOriginalRuntime() { }
    static Runtime check(PopsOriginalFiles.Config c, boolean full) throws Exception {
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch"))) throw new Prerequisite("Original PoPS requires an adopted Linux x86-64 runtime. Run Java inside permitted Linux/WSL, or skip dependent analysis. No edition/method fallback runs.");
        for (String name : List.of("/usr/bin/prlimit", "/usr/bin/setpriv")) if (!Files.isExecutable(Path.of(name))) throw new Prerequisite("Required Linux execution helper unavailable: " + name + ". Install permitted util-linux helpers or skip this analysis.");
        Path root = Path.of(c.get("runtime"));
        if (!Files.isRegularFile(root.resolve("runtime.properties")) || !Files.isRegularFile(root.resolve("READY.json"))) throw new Prerequisite("Completed original PoPS runtime adoption unavailable: " + root + ". Install/adopt a separate permitted runtime with tools/literature/pops_original, or skip dependent analysis. No installation runs during analysis.");
        root = root.toRealPath(); Properties p = load(root.resolve("runtime.properties"));
        if (!"original-pops-runtime-v1".equals(p.getProperty("profile")) || !"linux-x86_64".equals(p.getProperty("platform"))) throw new IOException("Selected original PoPS runtime profile differs");
        Path environment = Path.of(p.getProperty("environment")), sources = Path.of(p.getProperty("sources")), python = Path.of(p.getProperty("python"));
        if (!Files.isDirectory(environment) || !Files.isDirectory(sources) || !Files.isExecutable(python)) throw new Prerequisite("Original interpreter/environment/source tree is missing. Restore the exact adopted permitted runtime or skip dependent analysis.");
        for (var entry : Map.of("runtime-files.tsv", "manifest_sha256", "SOURCE.json", "source_record_sha256", "health.stdout", "health_sha256", "ELF-DEPENDENCIES.json", "elf_dependencies_sha256").entrySet()) {
            if (!Files.isRegularFile(root.resolve(entry.getKey()))) throw new Prerequisite("Adopted original runtime record missing: " + entry.getKey() + ". Restore the exact permitted setup or skip dependent analysis.");
            if (!sha(root.resolve(entry.getKey())).equals(p.getProperty(entry.getValue()))) throw new IOException("Adopted runtime metadata changed: " + entry.getKey() + ". Retain this record and install/adopt a separate replacement; do not refresh old fingerprints.");
        }
        Object parsed = SimpleJson.parse(Files.readString(root.resolve("READY.json")));
        if (!(parsed instanceof Map<?, ?> ready) || !"READY_ADOPTED_ORIGINAL_POPS_RUNTIME_NOT_ANALYSIS".equals(ready.get("status")) || !p.getProperty("manifest_sha256").equals(ready.get("manifest_sha256"))) throw new IOException("Complete original runtime adoption receipt differs");
        Map<String, String> versions = Map.of("python", "3.8.20", "numpy", "1.19.5", "pandas", "1.0.5", "scipy", "1.5.2", "sklearn", "0.24.2", "statsmodels", "0.12.2");
        if (!(ready.get("versions") instanceof Map<?, ?> actual) || !versions.equals(actual)) throw new IOException("Exact selected original dependency versions required; no version substitution");
        Map<String, String> roots = Map.of("env", environment.toRealPath().toString(), "source", sources.toRealPath().toString());
        if (!roots.equals(ready.get("roots")) || !python.getParent().getParent().toRealPath().equals(environment.toRealPath()) || !python.toRealPath().startsWith(environment.toRealPath())) throw new IOException("Adopted interpreter/root bindings differ; install/adopt a separate matching runtime instead of rebinding saved metadata");
        if (!(ready.get("files") instanceof Number number) || number.longValue() < 1 || number.longValue() > 500000) throw new IOException("Adopted entry count invalid");
        for (var entry : SOURCES.entrySet()) { if (!Files.isRegularFile(sources.resolve(entry.getKey()))) throw new Prerequisite("Selected original source missing: " + entry.getKey() + ". Restore the exact adopted permitted source or skip analysis."); if (!sha(sources.resolve(entry.getKey())).equals(entry.getValue())) throw new IOException("Selected original source bytes differ: " + entry.getKey()); }
        Runtime result = new Runtime(root, environment.toRealPath(), sources.toRealPath(), python, number.intValue());
        if (full) full(result); return result;
    }
    private static void full(Runtime runtime) throws Exception {
        List<Entry> entries = new ArrayList<>(); Set<String> expected = new HashSet<>(); Path manifest = runtime.root.resolve("runtime-files.tsv");
        if (Files.size(manifest) > 67108864) throw new IOException("Runtime manifest exceeds supported bounded metadata size");
        try (var in = Files.newBufferedReader(manifest)) {
            if (!"root\tkind\tpath\ttarget\tsha256".equals(in.readLine())) throw new IOException("Runtime manifest schema differs");
            for (String line; (line = in.readLine()) != null;) {
                String[] row = line.split("\t", -1); if (line.length() > 16384 || row.length != 5 || entries.size() >= 500000) throw new IOException("Invalid runtime manifest row/capacity");
                Path path;
                if (row[0].equals("system")) { path = Path.of(row[2]); if (!path.isAbsolute()) throw new IOException("Absolute recorded system dependency required"); }
                else {
                    Path base = row[0].equals("env") ? runtime.environment : row[0].equals("source") ? runtime.sources : null;
                    if (base == null) throw new IOException("Unknown runtime root"); Path relative = Path.of(row[2]); path = base.resolve(relative).normalize();
                    if (relative.isAbsolute() || !path.startsWith(base) || path.equals(base)) throw new IOException("Unqualified relative runtime path");
                }
                if (!Set.of("file", "link", "directory").contains(row[1]) || !expected.add(row[0] + "\t" + row[2])) throw new IOException("Unknown kind or duplicate runtime manifest entry");
                entries.add(new Entry(row[0], row[1], row[2], row[3], row[4], path));
            }
        }
        if (entries.size() != runtime.entries) throw new IOException("Adopted complete entry count differs");
        Set<String> actual = new HashSet<>();
        for (var root : Map.of("env", runtime.environment, "source", runtime.sources).entrySet()) try (var paths = Files.walk(root.getValue())) {
            var iterator = paths.iterator(); while (iterator.hasNext()) { Path path = iterator.next(); if (!path.equals(root.getValue())) actual.add(root.getKey() + "\t" + root.getValue().relativize(path).toString().replace('\\', '/')); if (actual.size() > 500000) throw new IOException("Runtime entry capacity exceeded"); }
        }
        for (Entry entry : entries) if (entry.root.equals("system")) actual.add(entry.root + "\t" + entry.name);
        Set<String> missing = new HashSet<>(expected); missing.removeAll(actual);
        if (!missing.isEmpty()) throw new Prerequisite("Adopted runtime entries are missing: " + missing.stream().sorted().limit(5).toList() + ". Restore the exact permitted runtime or skip dependent analysis; do not refresh fingerprints.");
        if (!actual.equals(expected)) throw new IOException("Complete adopted runtime membership differs; added unreviewed files are not accepted");
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<Void>> jobs = new ArrayList<>();
            for (Entry entry : entries) jobs.add(pool.submit(() -> {
                if (!Files.exists(entry.path, LinkOption.NOFOLLOW_LINKS)) throw new Prerequisite("Adopted runtime entry missing: " + entry.path + ". Restore the exact permitted runtime or skip this analysis.");
                BasicFileAttributes attributes = Files.readAttributes(entry.path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (entry.kind.equals("link")) {
                    if (!attributes.isSymbolicLink() || !Files.readSymbolicLink(entry.path).toString().equals(entry.target)) throw new IOException("Runtime link differs: " + entry.path);
                    if (!entry.sha.equals("-") && (!Files.isRegularFile(entry.path) || !sha(entry.path).equals(entry.sha))) throw new IOException("Linked runtime content differs: " + entry.path);
                } else if (entry.kind.equals("directory")) { if (!attributes.isDirectory() || !entry.sha.equals("-") || !entry.target.equals("-")) throw new IOException("Runtime directory differs: " + entry.path); }
                else if (!attributes.isRegularFile() || !sha(entry.path).equals(entry.sha) || !entry.target.equals("-")) throw new IOException("Runtime file bytes differ: " + entry.path);
                return null;
            }));
            for (var job : jobs) try { job.get(); } catch (java.util.concurrent.ExecutionException failure) { if (failure.getCause() instanceof Exception cause) throw cause; throw new IOException("Runtime fingerprinting failed", failure.getCause()); }
        } finally { pool.shutdownNow(); }
    }
    static Map<String, String> identity(Runtime runtime) throws IOException {
        Map<String, String> result = new TreeMap<>(); for (String name : RECORDS) result.put(name, sha(runtime.root.resolve(name)));
        for (String path : List.of("/usr/bin/prlimit", "/usr/bin/setpriv")) result.put("helper:" + path, sha(Path.of(path))); return result;
    }
    static void capture(Path root, Runtime runtime) throws IOException {
        Files.createDirectory(root.resolve("runtime-records")); for (String name : RECORDS) copy(runtime.root.resolve(name), root.resolve("runtime-records").resolve(name), 67108864);
        Files.createDirectory(root.resolve("upstream")); for (String version : List.of("0.1", "0.2")) Files.createDirectory(root.resolve("upstream").resolve(version));
        for (var entry : SOURCES.entrySet()) { Path target = root.resolve("upstream").resolve(entry.getKey()); copy(runtime.sources.resolve(entry.getKey()), target, 1048576); if (!sha(target).equals(entry.getValue())) throw new IOException("Captured original source differs"); }
    }
    static void sourcesUnchanged(Path root) throws IOException { for (var entry : SOURCES.entrySet()) if (!sha(root.resolve("upstream").resolve(entry.getKey())).equals(entry.getValue())) throw new IOException("Captured original source changed: " + entry.getKey()); }
}
