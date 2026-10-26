/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.hash;

/** Exact original private PLINK1/2 process boundary. Analysis never installs software. */
final class PlinkRuntime {
    private static final String MARKER = ".jlinalg-plink.json";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message, Exception cause) { super(message,cause); }
    }
    private final Path home;
    private final Path executable;
    private final String engine;
    private final Map<Path,String> hashes = new LinkedHashMap<>();
    private final Map<String,Object> session;

    PlinkRuntime(Path supplied, String engine, Path directory, Map<String,Object> session) throws Exception {
        this.home = supplied.toAbsolutePath().normalize(); this.engine = engine; this.session = session;
        if (!Set.of("plink1","plink2").contains(engine)) throw new IllegalArgumentException("Engine must be plink1 or plink2");
        this.executable = home.resolve(engine + "/" + (engine.equals("plink1") ? "plink.exe" : "plink2.exe"));
        try {
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                    || !Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))
                throw new IOException("Admitted original profile requires Windows x86_64");
            byte[] raw;
            try (var input = PlinkRuntime.class.getResourceAsStream("/org/jlinalg/omics/plink-runtime-lock.json")) {
                if (input == null) throw new IOException("Bundled PLINK runtime identity missing"); raw = input.readAllBytes();
            }
            Object parsed = SimpleJson.parse(new String(raw,StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?,?> lock)) throw new IOException("Invalid bundled PLINK identity");
            String lockSha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
            Path marker = contained(MARKER); var metadata = object(marker);
            if (!lockSha.equals(metadata.get("runtime_lock_sha256")) || !lock.get("profile").equals(metadata.get("profile"))
                    || !lock.get("files").equals(metadata.get("files"))) throw new IOException("Runtime marker differs from the bundled original profile");
            if (!(lock.get("files") instanceof Map<?,?> files) || files.size()!=3) throw new IOException("Original runtime file inventory differs");
            var observed = new HashSet<String>();
            try (var paths = Files.walk(home)) {
                var iterator=paths.iterator(); int entries=0;
                while (iterator.hasNext()) {
                    Path path=iterator.next(); if (++entries>7) throw new IOException("Runtime contains extra entries");
                    if (Files.isSymbolicLink(path)) throw new IOException("Linked runtime member is unsupported");
                    if (Files.isRegularFile(path) && !path.equals(marker)) observed.add(home.relativize(path).toString().replace('\\','/'));
                }
            }
            if (!observed.equals(files.keySet())) throw new IOException("Runtime has missing or extra files");
            for (var entry : files.entrySet()) {
                Path path = contained(String.valueOf(entry.getKey())); String expected = String.valueOf(entry.getValue());
                if (!expected.matches("[0-9a-f]{64}") || !expected.equals(hash(path))) throw new IOException("Runtime file changed: " + entry.getKey());
                hashes.put(path,expected);
            }
            hashes.put(marker,hash(marker));
            if (!(lock.get("engines") instanceof Map<?,?> engines) || !(engines.get(engine) instanceof Map<?,?> selected)) throw new IOException("Selected original engine is absent");
            session.put("runtime_home",home.toString()); session.put("runtime_profile",lock.get("profile")); session.put("runtime_lock_sha256",lockSha);
            session.put("runtime_marker_sha256",hash(marker)); session.put("runtime_executable_sha256",hash(executable)); session.put("upstream_engine",engine);
            execute(List.of("--version"),directory,"version",30);
            if (!Files.readString(directory.resolve("version.stdout"),StandardCharsets.UTF_8).strip().equals(selected.get("version_stdout"))
                    || Files.size(directory.resolve("version.stderr"))!=0) throw new IOException("Original version probe differs");
            session.put("upstream_version",selected.get("version_stdout"));
        } catch (IOException | IllegalArgumentException failure) {
            throw new Unavailable("Original PLINK dependency unavailable or changed: " + failure.getMessage(),failure);
        }
    }

    String engine() { return engine; }
    private Path contained(String name) throws IOException {
        Path path=home.resolve(name).normalize();
        if (!path.startsWith(home) || Files.isSymbolicLink(home) || !Files.isRegularFile(path)
                || Files.isSymbolicLink(path) || !path.toRealPath().startsWith(home.toRealPath())) throw new IOException("Missing, linked or escaping original runtime member: " + name);
        return path;
    }
    private static Map<?,?> object(Path path) throws IOException {
        if (Files.size(path)>65536) throw new IOException("Runtime marker exceeds 64 KiB");
        Object parsed=SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?,?> map)) throw new IOException("Runtime marker must be a JSON object"); return map;
    }
    void verify() throws Unavailable {
        try {
            var observed=new HashSet<Path>();
            try (var paths=Files.walk(home)) {
                var iterator=paths.iterator();int entries=0;
                while (iterator.hasNext()) {
                    Path path=iterator.next();if (++entries>7 || Files.isSymbolicLink(path)) throw new IOException("Original runtime acquired extra or linked entries");
                    if (Files.isRegularFile(path)) observed.add(path);
                }
            }
            if (!observed.equals(hashes.keySet())) throw new IOException("Original runtime file inventory changed during the operation");
            for (var entry : hashes.entrySet()) if (!entry.getValue().equals(hash(contained(home.relativize(entry.getKey()).toString()))))
                throw new IOException("Original runtime changed during the operation: " + entry.getKey());
        } catch (IOException failure) { throw new Unavailable(failure.getMessage(),failure); }
    }
    void execute(List<String> arguments, Path directory, String stage, int timeoutSeconds) throws Exception {
        if (!stage.matches("[A-Za-z0-9-]+") || timeoutSeconds<1 || timeoutSeconds>86400) throw new IllegalArgumentException("Invalid process stage/timeout");
        verify(); var command=new ArrayList<String>(); command.add(executable.toString()); command.addAll(arguments);
        session.put(stage+"_command",List.copyOf(command)); Path stdout=directory.resolve(stage+".stdout"), stderr=directory.resolve(stage+".stderr");
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        String system=System.getenv("SystemRoot"); if (system==null) throw new Unavailable("Windows SystemRoot is unavailable",null);
        builder.environment().put("PATH",Path.of(system,"System32").toString()); builder.environment().put("LC_ALL","C");
        builder.environment().put("OMP_NUM_THREADS","1");
        Process child;
        try { child=builder.start(); } catch (IOException failure) { throw new Unavailable("Cannot launch admitted original PLINK: "+failure.getMessage(),failure); }
        session.put(stage+"_owned_pid",child.pid());
        try {
            if (!child.waitFor(timeoutSeconds,TimeUnit.SECONDS)) { terminate(child); throw new IOException("Original "+engine+" "+stage+" exceeded --timeout-seconds; owned process terminated. Inspect retained logs and resource/model choices."); }
        } catch (InterruptedException failure) { try { terminate(child); } finally { Thread.currentThread().interrupt(); } throw failure; }
        session.put(stage+"_exit_code",child.exitValue()); session.put(stage+"_stdout_sha256",hash(stdout)); session.put(stage+"_stderr_sha256",hash(stderr)); verify();
        if (child.exitValue()!=0) throw new IOException("Original "+engine+" "+stage+" failed with exit "+child.exitValue()+"; inspect "+stage+".stdout/.stderr and the upstream log");
    }
    private static void terminate(Process process) throws IOException, InterruptedException {
        var descendants=process.descendants().toList(); for (var handle:descendants) if (handle.isAlive()) handle.destroyForcibly();
        process.destroyForcibly(); if (!process.waitFor(30,TimeUnit.SECONDS)) throw new IOException("Owned PLINK process did not terminate within 30 seconds");
    }
    static String setup() { return "Where permitted run python tools/literature/install_plink.py --home NEW_PRIVATE_HOME --allow-install; add --allow-download only for permitted missing downloads. Supply that sealed home using --home or JLINALG_PLINK_HOME. Missing, changed, unsupported or forbidden dependencies skip the original analysis with exit 3. Preserve diagnostics and changed homes; use a new path rather than re-sealing a changed binary. A version probe is not scientific validation."; }
}
