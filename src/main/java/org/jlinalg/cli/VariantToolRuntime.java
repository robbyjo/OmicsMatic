/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Application-managed, private installations; never alters the system Python. */
final class VariantToolRuntime {
    private VariantToolRuntime() { }
    static final String MARKER = ".jlinalg-variant-tool.json";
    static final String REGISTRATION = "https://www.openbioinformatics.org/annovar/annovar_download_form.php";
    private static final String LOCK = "variant-tool-runtime-lock.json";
    record Runtime(Path home, Path executable, String markerSha256, Map<String,Object> metadata) { }

    static Path defaultHome(String tool) {
        requireTool(tool);
        return Path.of(System.getProperty("user.home"), ".jlinalg", "tools", tool + (tool.equals("alphagenome") ? "-0.10.0" : ""));
    }
    static String defaultPython() { return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "python" : "python3"; }
    private static void requireTool(String tool) {
        if (!Set.of("alphagenome", "annovar").contains(tool)) throw new IllegalArgumentException("Tool must be alphagenome or annovar");
    }
    private static byte[] resource(String name) throws IOException {
        try (var input = VariantToolRuntime.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Packaged variant-tool installer resource is missing: " + name);
            return input.readAllBytes();
        }
    }
    static String lockHash() throws IOException { return StudyFindingInventory.hash(new String(resource(LOCK), StandardCharsets.UTF_8)); }

    static Runtime open(String tool, Path supplied) throws IOException {
        requireTool(tool);
        Path home = supplied.toAbsolutePath().normalize();
        if (!Files.isDirectory(home) || Files.isSymbolicLink(home) || !home.toRealPath().equals(home)) throw new IOException("Tool home is missing or linked: " + home);
        Path marker = home.resolve(MARKER);
        if (!Files.isRegularFile(marker) || Files.size(marker) > 32000000) throw new IOException("No complete managed " + tool + " installation at " + home);
        String markerHash = FollowupSupport.hash(marker);
        var metadata = StudyPublicEvidence.map(SimpleJson.parse(Files.readString(marker)));
        if (!(metadata.get("schema_version") instanceof Number version) || version.doubleValue() != 1 || !"variant-tools-v1".equals(metadata.get("profile")) || !tool.equals(metadata.get("tool")) || !lockHash().equals(metadata.get("lock_sha256")))
            throw new IOException("Managed tool installation does not match this JLinAlg profile");
        var expected = StudyPublicEvidence.map(metadata.get("files_sha256")); var links = StudyPublicEvidence.map(metadata.get("symlinks"));
        if (expected.isEmpty() || expected.size() > 250000 || links.size() > 100) throw new IOException("Invalid managed-tool file inventory");
        var actual = new LinkedHashSet<String>(); var actualLinks = new LinkedHashSet<String>();
        try (var paths = Files.walk(home)) {
            for (Path path : paths.toList()) {
                String name = home.relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path)) {
                    if (!path.toRealPath().startsWith(home) || !Files.readSymbolicLink(path).toString().equals(links.get(name))) throw new IOException("Managed tool link changed: " + name);
                    actualLinks.add(name);
                } else if (Files.isRegularFile(path) && !path.equals(marker)) {
                    if (!FollowupSupport.hash(path).equals(expected.get(name))) throw new IOException("Managed tool file changed or was added: " + name);
                    actual.add(name);
                }
            }
        }
        if (!actual.equals(expected.keySet()) || !actualLinks.equals(links.keySet()) || !markerHash.equals(FollowupSupport.hash(marker)))
            throw new IOException("Managed tool installation changed or has missing files");
        String member = StudyPublicEvidence.text(metadata.get("executable")); Path executable = home.resolve(member).normalize();
        if (member.isBlank() || !executable.startsWith(home) || !Files.isRegularFile(executable) || !expected.containsKey(home.relativize(executable).toString().replace('\\', '/')))
            throw new IOException("Managed tool executable is missing from its inventory");
        if (tool.equals("alphagenome") && !"0.10.0".equals(metadata.get("version"))) throw new IOException("Unexpected managed AlphaGenome version");
        return new Runtime(home, executable, markerHash, Map.copyOf(metadata));
    }

    static Runtime install(String tool, Path supplied, String python, Map<String,String> options, boolean downloads, PrintStream progress) throws IOException {
        requireTool(tool);
        Path home = supplied.toAbsolutePath().normalize();
        if (Files.exists(home.resolve(MARKER))) return open(tool, home);
        boolean localArchive = tool.equals("annovar") && options.containsKey("source-file");
        if (!downloads && !localArchive) throw new IOException("Missing " + tool + "; software downloads are disabled. Install it once with variant-tools install or supply a managed home");
        if (tool.equals("annovar")) {
            if (!FollowupSupport.bool(options, "accept-license", false)) throw new IOException("ANNOVAR requires licence acknowledgement and registration: " + REGISTRATION);
            if (options.containsKey("source-file") == options.containsKey("source-url-env")) throw new IOException("Supply a registered ANNOVAR --source-file or --source-url-env. Register at " + REGISTRATION);
            if (options.containsKey("source-url-env") && !options.get("source-url-env").matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IOException("source-url-env must name an environment variable");
        }
        if (Files.exists(home)) throw new IOException("Incomplete or unmanaged tool home already exists; retain it and choose a new home: " + home);
        Files.createDirectories(home.getParent()); Files.createDirectory(home);
        Path script = home.resolve("variant_tool_setup.py"), lock = home.resolve(LOCK);
        Files.write(script, resource("variant_tool_setup.py")); Files.write(lock, resource(LOCK));
        var command = new ArrayList<>(List.of(python, "-I", "-B", script.toString(), "--tool", tool, "--home", home.toString(), "--lock", lock.toString()));
        for (String field : List.of("source-file", "source-url-env", "expected-sha256")) if (options.containsKey(field)) {
            String value = options.get(field);
            if (field.equals("source-file")) value = Path.of(value).toAbsolutePath().normalize().toString();
            if (field.equals("expected-sha256") && !value.matches("[a-fA-F0-9]{64}")) throw new IOException("expected-sha256 must be a SHA-256 digest");
            command.add("--" + field); command.add(value);
        }
        if (tool.equals("annovar")) command.add("--accept-license");
        if (progress != null) progress.println("Installing " + tool + " in " + home + "; download and setup logs are retained there.");
        Path console = home.getParent().resolve(home.getFileName() + ".setup-console.txt");
        if (Files.exists(console)) throw new IOException("Setup console already exists; choose a new private home");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(console.toFile()).start();
        try {
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                throw new IOException("Tool installation timed out; retain its home and setup log, then choose a new home");
            }
        } catch (InterruptedException interrupted) {
            process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); Thread.currentThread().interrupt();
            throw new IOException("Tool installation interrupted; partial files retained", interrupted);
        }
        if (process.exitValue() != 0) throw new IOException("Tool installation failed; inspect " + console + " and setup-failure.json in the retained home");
        return open(tool, home);
    }

    static Map<String,Object> describe(String tool, Path home) throws IOException {
        var result = new LinkedHashMap<String,Object>(); result.put("tool", tool); result.put("home", home.toAbsolutePath().normalize().toString());
        if (!Files.exists(home.resolve(MARKER))) {
            result.put("status", tool.equals("annovar") ? "registration_or_registered_archive_required" : "not_installed");
            if (tool.equals("annovar")) result.put("registration_url", REGISTRATION);
        } else {
            var runtime = open(tool, home); result.put("status", "installed_and_verified"); result.put("executable", runtime.executable().toString());
            result.put("version", runtime.metadata().get("version")); result.put("marker_sha256", runtime.markerSha256()); result.put("software_scope", runtime.metadata().get("software_scope"));
        }
        return result;
    }
}
