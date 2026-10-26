/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Pinned original Perl scripts and read-only, fingerprinted host dependencies. */
final class TrimGaloreRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message, Exception cause) { super(message, cause); }
    }
    private final Path home, marker, python;
    private final Map<?, ?> metadata;
    private final Map<Path, String> protectedFiles = new LinkedHashMap<>();
    private final Map<Path, String> external = new LinkedHashMap<>();
    private final List<?> roots;
    private final Set<Path> fixed = new HashSet<>();
    private final String markerHash;

    TrimGaloreRuntime(Path supplied, boolean fastqc, Map<String, Object> session) throws Exception {
        home = supplied.toAbsolutePath().normalize(); marker = home.resolve(".jlinalg-trim-galore.json");
        try {
            if (!System.getProperty("os.name").startsWith("Windows")) throw new IOException("Prepared original Trim Galore profile requires Windows/Cygwin; a separately validated native-host profile is required");
            byte[] lock;
            try (var input = TrimGaloreRuntime.class.getResourceAsStream("/org/jlinalg/omics/trim_galore_runtime_lock.json")) {
                if (input == null) throw new IOException("Packaged original Trim Galore profile lock is absent");
                lock = input.readAllBytes();
            }
            Object parsed = SimpleJson.parse(new String(lock, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> pins)) throw new IOException("Invalid packaged Trim Galore lock");
            metadata = RegenieRuntime.object(marker);
            String lockHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(lock));
            if (!"windows-cygwin-perl-native-python".equals(metadata.get("profile")) || !lockHash.equals(metadata.get("lock_sha256"))
                    || !pins.get("cutadapt_packages").equals(metadata.get("package_versions"))) throw new IOException("Original source/dependency profile differs from the packaged lock");
            var expected = new LinkedHashMap<String, String>();
            if (!(pins.get("profiles") instanceof Map<?, ?> profiles)) throw new IOException("Original profile map absent");
            for (var profile : profiles.entrySet()) {
                if (!(profile.getValue() instanceof Map<?, ?> item) || !(item.get("files") instanceof Map<?, ?> files)) throw new IOException("Original profile files absent");
                for (var member : files.entrySet()) expected.put(profile.getKey() + "/" + member.getKey(), String.valueOf(member.getValue()));
            }
            if (!expected.equals(metadata.get("files"))) throw new IOException("Original source member set differs");
            var members = new HashSet<String>();
            try (var files = Files.walk(home)) { for (Path file : files.filter(Files::isRegularFile).toList()) members.add(home.relativize(file).toString().replace('\\', '/')); }
            var complete = new HashSet<>(expected.keySet()); complete.add(marker.getFileName().toString());
            if (!members.equals(complete)) throw new IOException("Original private runtime has extra or missing members");
            for (var entry : expected.entrySet()) protectedFiles.put(RegenieRuntime.contained(home, entry.getKey()), entry.getValue());
            markerHash = hash(marker); protectedFiles.put(marker, markerHash);
            if (!(metadata.get("external_files") instanceof Map<?, ?> files) || files.isEmpty() || files.size() > 50_000
                    || !(metadata.get("external_roots") instanceof List<?> declaredRoots) || !(metadata.get("external_fixed_files") instanceof List<?> fixedFiles))
                throw new IOException("External dependency inventory is absent or oversized");
            roots = declaredRoots;
            for (var entry : files.entrySet()) {
                Path path = Path.of(String.valueOf(entry.getKey())); String value = String.valueOf(entry.getValue());
                if (!path.isAbsolute() || !value.matches("[0-9a-f]{64}")) throw new IOException("Invalid external dependency path/hash");
                external.put(path.toAbsolutePath().normalize(), value);
            }
            for (Object file : fixedFiles) {
                Path path = Path.of(String.valueOf(file)).toAbsolutePath().normalize();
                if (!external.containsKey(path)) throw new IOException("Fixed dependency missing from sealed inventory"); fixed.add(path);
            }
            if (!(pins.get("wheels") instanceof Map<?, ?> wheels) || !(metadata.get("original_wheels") instanceof Map<?, ?> origins)
                    || !origins.keySet().equals(wheels.keySet()) || !(metadata.get("original_wheel_files") instanceof Map<?, ?> originals) || originals.isEmpty())
                throw new IOException("Original upstream wheel authentication is absent");
            for (var entry : wheels.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> pin) || !(origins.get(entry.getKey()) instanceof Map<?, ?> origin)
                        || !pin.get("sha256").equals(origin.get("archive_sha256"))) throw new IOException("Upstream wheel origin differs from the packaged pin");
            }
            for (var entry : originals.entrySet()) if (!String.valueOf(entry.getValue()).equals(external.get(Path.of(String.valueOf(entry.getKey())).toAbsolutePath().normalize())))
                throw new IOException("Authenticated original package file is absent from the runtime seal");
            python = Path.of(String.valueOf(metadata.get("python"))).toAbsolutePath().normalize();
            for (String name : List.of("python", "perl", "shell")) if (!external.containsKey(Path.of(String.valueOf(metadata.get(name))).toAbsolutePath().normalize()))
                throw new IOException("Declared executable is not sealed: " + name);
            if (fastqc && (!(metadata.get("fastqc") instanceof Map<?, ?> qc) || !"0.13.0".equals(qc.get("version"))))
                throw new IOException("Requested FastQC is absent from this prepared profile; prepare a new permitted profile with --fastqc-home and --java");
            if (metadata.get("fastqc") instanceof Map<?, ?> qc && (!(pins.get("fastqc_archive") instanceof Map<?, ?> pin) || !pin.get("sha256").equals(qc.get("archive_sha256"))))
                throw new IOException("Original FastQC distribution authentication differs from the packaged pin");
            protectedFiles.putAll(external); verify();
            session.put("dependency_home", home.toString()); session.put("dependency_marker_sha256", markerHash);
            session.put("sealed_dependency_files", external.size()); session.put("package_versions", metadata.get("package_versions"));
            session.put("perl_version", metadata.get("perl_version")); session.put("python_version", metadata.get("python_version"));
            session.put("runtime_boundary", "Existing referenced runtimes remain read-only; exact source/package/stdlib/native file inventories are checked before and after execution. Python has no site startup and ignores existing caches; Windows system DLLs remain the operating-system boundary.");
        } catch (IOException | IllegalArgumentException failure) {
            throw new Unavailable("Original Trim Galore dependency missing, unsupported or changed: " + failure.getMessage(), failure);
        }
    }

    private Set<Path> inventory() throws IOException {
        var result = new HashSet<Path>();
        for (Object item : roots) {
            if (!(item instanceof Map<?, ?> spec)) throw new IOException("Invalid dependency root");
            Path root = Path.of(String.valueOf(spec.get("path"))).toAbsolutePath().normalize();
            var excluded = new HashSet<String>(); excluded.add("__pycache__");
            if (spec.get("excluded_directories") instanceof List<?> list) for (Object name : list) excluded.add(String.valueOf(name));
            if (Files.isRegularFile(root)) result.add(root.toRealPath());
            else if (Files.isDirectory(root)) Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                    if (!directory.equals(root) && excluded.contains(directory.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                    if (Files.isSymbolicLink(directory)) throw new IOException("Unexpected dependency directory link: " + directory);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    String name = path.getFileName().toString();
                    if (!name.endsWith(".pyc") && !name.endsWith(".pyo")) {
                        if (Files.isSymbolicLink(path)) throw new IOException("Unexpected dependency file link: " + path);
                        if (attrs.isRegularFile()) result.add(path.toRealPath());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            else if (!Boolean.TRUE.equals(spec.get("allow_absent"))) throw new IOException("Dependency root disappeared: " + root);
        }
        result.addAll(fixed); return result;
    }

    void verify() throws IOException {
        if (!inventory().equals(external.keySet())) throw new IOException("External dependency member set changed; preserve the profile and prepare a new permitted environment");
        RegenieRuntime.verify(protectedFiles);
    }

    void execute(Path work, String release, String operation, boolean paired, boolean fastqc, int memory,
                 Map<String, Object> settings, int timeout, Map<String, Object> session) throws Exception {
        Path driver = work.resolve("driver.py");
        try (var input = TrimGaloreRuntime.class.getResourceAsStream("/org/jlinalg/omics/trim_galore_original.py")) {
            if (input == null) throw new IOException("Original-process driver resource unavailable"); Files.copy(input, driver);
        }
        var config = new LinkedHashMap<String, Object>();
        config.put("home", home.toString()); config.put("marker_sha256", markerHash); config.put("release", release); config.put("operation", operation);
        config.put("paired", paired); config.put("fastqc", fastqc); config.put("fastqc_memory_mb", memory); config.put("work_directory", work.toString()); config.put("settings", settings);
        Path configuration = work.resolve("configuration.json"); Files.writeString(configuration, RegenieRuntime.json(config) + "\n");
        var command = new ArrayList<>(List.of(python.toString(), "-I", "-S", "-B", "-X", "utf8", "-X", "pycache_prefix=" + work.resolve("ignored-python-cache"),
                driver.toString(), "--mode", "run", "--configuration", configuration.toString()));
        session.put("driver_command", command); session.put("driver_sha256", hash(driver)); session.put("configuration_sha256", hash(configuration));
        var builder = new ProcessBuilder(command).directory(work.toFile()).redirectOutput(work.resolve("driver.stdout").toFile()).redirectError(work.resolve("driver.stderr").toFile());
        for (String key : List.of("PERL5LIB", "PERLLIB", "PERL5OPT", "PYTHONPATH", "PYTHONHOME", "CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(key);
        Process child;
        try { child = builder.start(); } catch (IOException failure) { throw new Unavailable("Cannot launch the sealed original-process driver", failure); }
        try {
            if (!child.waitFor(timeout, TimeUnit.SECONDS)) {
                child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); child.waitFor(30, TimeUnit.SECONDS);
                throw new IOException("Original Trim Galore timed out; all attempt logs/partial streams are retained");
            }
        } catch (InterruptedException failure) { child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); Thread.currentThread().interrupt(); throw failure; }
        session.put("driver_exit_code", child.exitValue()); verify();
        if (child.exitValue() != 0) throw new IOException("Original Trim Galore/Cutadapt/FastQC process failed, exit " + child.exitValue() + "; inspect original/driver.stderr, original.stderr and transport-audit logs");
        Map<?, ?> result = RegenieRuntime.object(work.resolve("driver-run.json"));
        if (!"original-processes-completed-not-yet-java-audited".equals(result.get("status")) || ((Number) result.get("original_exit_code")).intValue() != 0)
            throw new IOException("Original process metadata lacks verified successful completion");
        session.put("original_command", result.get("original_command")); session.put("original_source_sha256", result.get("source_sha256"));
    }
}
