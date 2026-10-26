/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Pinned RNA/long-read process boundary; analysis commands never install software. */
final class RnaToolRuntime {
    static final Map<String,String> VERSIONS = Map.of("featurecounts","2.1.1","minimap2","2.31-r1302","hisat2","2.2.3");
    private static final Map<String,String> ARCHIVES = Map.of(
        "featurecounts","16f04778c1ade1bfca0c594016527a7e73eaecd1b6724f4eace00c674921ab3e",
        "minimap2","bff334a0e4512644e2f3e29944aeec408f49450f4f74dc39fe89e5273869255b",
        "hisat2","b53107422e5b44ebea4b20b1a77bb9e240d6b92d654fcd7e6a6ab5d1aae86c45");
    private final Path home;
    private final String profile;
    private final Map<Path,String> hashes = new LinkedHashMap<>();
    private final Map<String,Object> session;
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message, Exception cause) { super(message,cause); }
    }
    RnaToolRuntime(Path home, String profile, Path directory, Map<String,Object> session) throws Exception {
        this.home = home.toAbsolutePath().normalize(); this.profile = profile; this.session = session;
        try {
            if (!VERSIONS.containsKey(profile)) throw new IllegalArgumentException("Unknown RNA runtime");
            Path marker = this.home.resolve(".jlinalg-rna-aligner.json");
            if (Files.exists(marker) || RegenieRuntime.windows()) {
                var metadata = RegenieRuntime.object(marker);
                if (!java.util.Objects.equals(metadata.get("identity"),ARCHIVES.get(profile)+":windows-x86_64:build-protocol-1")
                        || !(metadata.get("files") instanceof Map<?,?> files) || files.isEmpty() || !RegenieRuntime.windows()) throw new IOException("Managed runtime identity/platform differs from this adapter");
                for (var entry : files.entrySet()) {
                    Path path = RegenieRuntime.contained(this.home,String.valueOf(entry.getKey())); String expected = String.valueOf(entry.getValue());
                    if (!expected.matches("[0-9a-f]{64}") || !Files.isRegularFile(path) || !expected.equals(hash(path))) throw new IOException("Managed runtime file changed: "+path);
                    hashes.put(path,expected);
                }
                hashes.put(marker,hash(marker)); session.put("runtime_marker_sha256",hash(marker));
                if (metadata.get("provenance") instanceof Map<?,?> provenance) {
                    session.put("runtime_build_provenance",provenance);
                    if (provenance.get("compiler_warning_count") instanceof Number n && n.longValue()>0) Files.writeString(directory.resolve("runtime-build-warning.log"),"WARNING: Pinned upstream build retained "+n.longValue()+" compiler warnings. Required action: review the installer build report before extending the tested operations/input scope. Build success and version probes alone are not scientific validation.\n");
                }
                if (!hashes.containsKey(Path.of(binary(mainBinary())))) throw new IOException("Managed marker omits runtime program");
                if (!profile.equals("featurecounts") && !hashes.containsKey(this.home.resolve("bin/cygpath.exe"))) throw new IOException("Managed Cygwin runtime omits its path converter");
            } else {
                // Explicit native host installation: pin and report programs/shared libraries in bin/.
                if (!Files.isDirectory(this.home.resolve("bin"))) throw new IOException("Native runtime requires HOME/bin");
                try (var paths = Files.list(this.home.resolve("bin"))) {
                    for (Path p : paths.filter(Files::isRegularFile).toList()) hashes.put(p,hash(p));
                }
                session.put("runtime_profile","explicit-native-installation; upstream version and local file hashes checked; host libraries not bundled");
            }
            String program = binary(mainBinary());
            session.put("executable_sha256",hash(Path.of(program)));
            execute(List.of(program,profile.equals("featurecounts")?"-v":"--version"),directory,"version",null,30);
            String text = Files.readString(directory.resolve("version.log")).replace("\r\n","\n");
            boolean valid = switch (profile) {
                case "featurecounts" -> text.strip().equals("featureCounts v2.1.1");
                case "minimap2" -> text.strip().equals("2.31-r1302");
                default -> text.lines().anyMatch(line -> line.endsWith(" version 2.2.3"));
            };
            if (!valid) throw new IOException("Runtime version differs from "+VERSIONS.get(profile));
            session.put("engine",profile); session.put("engine_version",VERSIONS.get(profile));
        } catch (IOException|IllegalArgumentException failure) {
            throw new Unavailable(profile+" unavailable or changed: "+failure.getMessage(),failure);
        }
    }
    private String mainBinary() { return profile.equals("featurecounts")?"featureCounts":profile.equals("hisat2")?"hisat2-align-s":"minimap2"; }
    String binary(String name) throws IOException {
        Path path = home.resolve("bin/"+name+(RegenieRuntime.windows()?".exe":""));
        if (!Files.isRegularFile(path)) throw new IOException("Required runtime program is absent: "+path);
        return path.toString();
    }
    String checkedBinary(String name,Path directory) throws Exception {
        try {
            String value=binary(name);
            if (profile.equals("hisat2")&&!name.equals(mainBinary())) {
                execute(List.of(value,"--version"),directory,name+"-version",null,30);
                if (Files.readString(directory.resolve(name+"-version.log")).lines().noneMatch(line->line.endsWith(" version 2.2.3"))) throw new IOException("HISAT2 component version differs from 2.2.3: "+name);
            }
            return value;
        } catch (IOException failure) { throw new Unavailable("Required RNA runtime component unavailable: "+failure.getMessage(),failure); }
    }
    String path(Path path, Path directory) throws Exception {
        if (!RegenieRuntime.windows() || profile.equals("featurecounts")) return path.toAbsolutePath().normalize().toString();
        String name = "path-"+((Number)session.getOrDefault("path_conversions",0)).intValue();
        session.put("path_conversions",((Number)session.getOrDefault("path_conversions",0)).intValue()+1);
        execute(List.of(home.resolve("bin/cygpath.exe").toString(),"-u","-a",path.toAbsolutePath().normalize().toString()),directory,name,null,30);
        String result = Files.readString(directory.resolve(name+".log")).strip();
        if (!result.startsWith("/") || result.contains("\n")) throw new IOException("Path converter did not return one absolute POSIX path");
        return result;
    }
    void execute(List<String> command, Path directory, String stage, Path stdout, int timeout) throws Exception {
        session.put(stage+"_command",List.copyOf(command));
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectError(directory.resolve(stage+".log").toFile());
        if (stdout == null) builder.redirectErrorStream(true).redirectOutput(directory.resolve(stage+".log").toFile()); else builder.redirectOutput(stdout.toFile());
        var env = builder.environment(); env.put("LC_ALL","C"); env.put("OMP_NUM_THREADS","1");
        env.remove("HISAT2_INDEXES"); env.remove("HISAT2_HOME"); env.remove("BOWTIE2_INDEXES");
        if (RegenieRuntime.windows()) {
            String system = System.getenv("SystemRoot"); if (system == null) throw new IOException("Windows SystemRoot unavailable");
            env.put("PATH",home.resolve("bin")+java.io.File.pathSeparator+Path.of(system,"System32")+java.io.File.pathSeparator+system);
        }
        Process child = builder.start();
        try {
            if (!child.waitFor(timeout,TimeUnit.SECONDS)) {
                child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); child.waitFor(30,TimeUnit.SECONDS);
                throw new IOException("Upstream "+stage+" timed out; inspect "+stage+".log");
            }
            session.put(stage+"_exit_code",child.exitValue());
            if (child.exitValue()!=0) throw new IOException("Upstream "+stage+" exited "+child.exitValue()+"; inspect "+stage+".log");
        } catch (InterruptedException failure) {
            child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); Thread.currentThread().interrupt(); throw failure;
        }
    }
    void verify() throws IOException { RegenieRuntime.verify(hashes); }
    static Path file(String value) throws IOException {
        Path p = Path.of(value).toAbsolutePath().normalize(); if (!Files.isRegularFile(p)) throw new IOException("Input file does not exist: "+p); return p;
    }
    static Map<String,Long> dictionary(Path reference) throws IOException {
        var map = new LinkedHashMap<String,Long>();
        for (var s : org.jlinalg.sequence.FastaIndexes.build(reference)) {
            if (s.getSize()>Integer.MAX_VALUE) throw new IOException("Reference sequence exceeds signed-32-bit SAM coordinates");
            map.put(s.getContig(),s.getSize());
        }
        return map;
    }
    static void quarantine(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) if (!path.getFileName().toString().endsWith(".log") && !path.getFileName().toString().equals("partial")) {
                Files.createDirectories(directory.resolve("partial")); Files.move(path,directory.resolve("partial").resolve(path.getFileName()));
            }
        }
    }
    static String setup(String profile) {
        return "Where permitted run python tools/literature/install_rna_aligners.py --profile "+profile+" --allow-download"+(profile.equals("featurecounts")?"":" --allow-source-build --cygwin-root C:/cygwin64")
            +" and supply the resulting runtime HOME. On native Linux/macOS, supply HOME/bin containing upstream "+VERSIONS.get(profile)+". If installation is forbidden, skip the dependent analysis until an administrator supplies the program. Preserve failed logs and existing runtimes.";
    }
    static List<String> warnings(Path directory) throws IOException {
        var result = new ArrayList<String>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".log")).toList()) try (var lines = Files.lines(path)) {
                lines.filter(line -> line.matches("(?i).*(?:\\bwarning\\b|\\[W::).*" )).limit(100).forEach(line -> result.add(path.getFileName()+": "+line));
            }
        }
        return result;
    }
}
