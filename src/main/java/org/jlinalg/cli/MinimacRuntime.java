/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** Pinned original Minimac profiles, host closure and bounded owned processes. */
final class MinimacRuntime {
    private MinimacRuntime() { }

    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }

    private record Lock(Map<?, ?> value, String hash) { }
    private static final Lock LOCK = load();
    private static final String MARKER = ".jlinalg-minimac.json";
    private static final String HEALTH = "passed-original-imputation-and-reference-health";

    record External(String realPath, long bytes, String sha256) { }
    record Context(Path home, String profile, String family, String version, String style,
            boolean emulated, boolean privateGomp, boolean inspector, String markerHash,
            Map<String, String> files, Map<String, External> external) {
        Context { files = Map.copyOf(files); external = Map.copyOf(external); }
        void verify() throws IOException {
            try {
                Context current = open(home, profile);
                if (!equals(current)) throw new IOException("Minimac runtime admission changed during analysis");
            } catch (Unavailable failure) {
                throw new IOException("Minimac runtime changed during analysis; no result is admitted", failure);
            }
        }
    }

    private static Lock load() {
        try (var stream = MinimacRuntime.class.getResourceAsStream("/org/jlinalg/omics/minimac-runtime-lock.json")) {
            if (stream == null) throw new IOException("Missing bundled Minimac runtime lock");
            byte[] bytes = stream.readNBytes(1048577);
            if (bytes.length > 1048576) throw new IOException("Oversized Minimac runtime lock");
            return new Lock(object(SimpleJson.parse(utf8(bytes))), digest(bytes));
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    static Context open(Path supplied, String profile) throws Unavailable {
        try {
            String architecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
            if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(architecture)) {
                throw new IOException("Original Minimac runtimes require Linux x86_64; on Windows use Linux Java inside WSL");
            }
            Map<?, ?> pin = object(object(LOCK.value.get("profiles")).get(profile));
            Path home = supplied.toAbsolutePath().normalize();
            if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS) || !home.toRealPath().equals(home)) throw new IOException("Missing or linked private runtime home");
            Path marker = home.resolve(MARKER);
            byte[] raw = bounded(marker, 1048576);
            String markerHash = digest(raw);
            Map<?, ?> meta = object(SimpleJson.parse(utf8(raw)));
            if (whole(meta, "schema_version", 1, 1) != 1 || !profile.equals(meta.get("profile"))
                    || !pin.get("family").equals(meta.get("family")) || !pin.get("version").equals(meta.get("version"))
                    || !LOCK.hash.equals(meta.get("lock_sha256"))) throw new IOException("Requested Minimac family/profile/version or lock differs; no historical version is substituted");
            Map<?, ?> host = object(meta.get("host"));
            if (!"Linux".equals(host.get("os")) || !"x86_64".equals(host.get("architecture"))
                    || !System.getProperty("os.version").equals(host.get("kernel"))) throw new IOException("Admitted runtime host/kernel differs; admit a new private home on this host");
            Map<?, ?> health = object(meta.get("health")), output = object(health.get("original_output")), expectedHealth = object(pin.get("health"));
            if (!HEALTH.equals(health.get("status")) || !pin.get("family").equals(health.get("family"))
                    || !pin.get("version").equals(health.get("version")) || !pin.get("style").equals(health.get("style"))
                    || !expectedHealth.get("output_rows_sha256").equals(output.get("output_rows_sha256"))
                    || whole(output, "variants", 120, 120) != 120 || whole(output, "samples", 6, 6) != 6
                    || whole(output, "genotype_cells", 720, 720) != 720 || whole(output, "heldout_genotype_cells", 534, 534) != 534
                    || !Boolean.TRUE.equals(output.get("known_phased_calls_matched"))) {
                throw new IOException("Actual same-version imputation health is missing or inconsistent; help output is insufficient");
            }
            if (Boolean.TRUE.equals(pin.get("inspector"))) {
                Map<?, ?> reference = object(health.get("reference_export"));
                if (whole(reference, "variants", 120, 120) != 120 || whole(reference, "haplotype_copies", 5760, 5760) != 5760
                        || !Boolean.TRUE.equals(reference.get("original_phased_calls_matched"))) throw new IOException("Actual original reference-export health is missing or inconsistent");
            }
            Map<?, ?> members = object(pin.get("members")), recorded = object(meta.get("files"));
            if (!members.keySet().equals(recorded.keySet())) throw new IOException("Admitted runtime member inventory differs");
            var files = new TreeMap<String, String>();
            for (var entry : members.entrySet()) {
                String name = entry.getKey().toString();
                if (!name.matches("(?:bin|lib|share)/[A-Za-z0-9._-]+")) throw new IOException("Unsafe locked runtime member path");
                Map<?, ?> expected = object(entry.getValue());
                Path path = home.resolve(name);
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path)
                        || Files.size(path) != whole(expected, "bytes", 1, 100000000)) throw new IOException("Runtime member path/size differs: " + name);
                String hash = FollowupSupport.hash(path);
                if (!hash.equals(expected.get("sha256")) || !hash.equals(recorded.get(name))) throw new IOException("Runtime member changed: " + name);
                if (name.startsWith("bin/") && !Files.isExecutable(path)) throw new IOException("Original executable lacks execute permission: " + name);
                files.put(name, hash);
            }
            var found = new HashSet<String>();
            int count = 0;
            try (var walk = Files.walk(home)) {
                for (var iterator = walk.iterator(); iterator.hasNext();) {
                    Path path = iterator.next();
                    if (++count > 64 || Files.isSymbolicLink(path)) throw new IOException("Unexpected or linked private runtime family");
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) found.add(home.relativize(path).toString().replace('\\', '/'));
                    else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Nonregular runtime member");
                }
            }
            var expectedFiles = new HashSet<>(files.keySet());
            expectedFiles.add(MARKER);
            if (!found.equals(expectedFiles)) throw new IOException("Private runtime contains extra or missing files");
            Map<?, ?> closure = object(meta.get("closure")), external = object(closure.get("external_files"));
            if (external.isEmpty() || external.size() > 32 || !external.containsKey("/usr/bin/ldd")) throw new IOException("Missing or oversized host runtime closure");
            Set<String> required = requiredExternal(text(pin, "style"));
            var suppliedNames = new HashSet<String>();
            for (Object name : external.keySet()) suppliedNames.add(Path.of(name.toString()).getFileName().toString());
            if (!suppliedNames.equals(required) || suppliedNames.size() != external.size()) throw new IOException("Host runtime closure is incomplete or differs from the pinned binary family");
            Map<?, ?> binaries = object(closure.get("binaries"));
            var expectedBinaries = new HashSet<String>();
            for (Object name : members.keySet()) if (name.toString().startsWith("bin/")) expectedBinaries.add(name.toString());
            if (!binaries.keySet().equals(expectedBinaries)) throw new IOException("Runtime binary/library inventory is incomplete");
            boolean expectedStatic = Set.of("modern", "legacy102").contains(pin.get("style"));
            var requiredLibraries = new HashSet<>(required); requiredLibraries.remove("ldd");
            var requiredPaths = new HashSet<>(external.keySet()); requiredPaths.remove("/usr/bin/ldd");
            for (Object value : binaries.values()) {
                Map<?, ?> binary = object(value);
                if (!Boolean.valueOf(expectedStatic).equals(binary.get("statically_linked")) || !(binary.get("external_libraries") instanceof List<?> paths)) throw new IOException("Original binary static/dynamic binding differs");
                var names = new HashSet<String>();
                for (Object path : paths) names.add(Path.of(path.toString()).getFileName().toString());
                if (!names.equals(requiredLibraries) || paths.size() != names.size() || !new HashSet<>(paths).equals(requiredPaths)) throw new IOException("Original binary host-library bindings differ");
            }
            var externalFiles = new TreeMap<String, External>();
            for (var entry : external.entrySet()) {
                Path path = Path.of(entry.getKey().toString());
                Map<?, ?> expected = object(entry.getValue());
                long bytes = whole(expected, "bytes", 1, 100000000);
                if (!path.isAbsolute() || !Files.isRegularFile(path)) throw new IOException("Missing external runtime file: " + path);
                Path real = path.toRealPath();
                String hash = FollowupSupport.hash(real);
                if (!real.toString().equals(expected.get("real_path")) || Files.size(real) != bytes
                        || !hash.equals(expected.get("sha256"))) throw new IOException("Changed host runtime library/tool: " + path);
                externalFiles.put(path.toString(), new External(real.toString(), bytes, hash));
            }
            if (!markerHash.equals(FollowupSupport.hash(marker))) throw new IOException("Runtime marker changed during admission");
            return new Context(home, profile, text(pin, "family"), text(pin, "version"), text(pin, "style"),
                    Boolean.TRUE.equals(pin.get("emulated")), Boolean.TRUE.equals(pin.get("private_gomp")),
                    Boolean.TRUE.equals(pin.get("inspector")), markerHash, files, externalFiles);
        } catch (Exception failure) {
            throw new Unavailable("Original Minimac dependency unavailable: " + failure.getMessage(), failure);
        }
    }

    static void provenance(Context runtime, Map<String, Object> session) {
        session.put("runtime_home", runtime.home.toString());
        session.put("runtime_profile", runtime.profile);
        session.put("runtime_family", runtime.family);
        session.put("runtime_version", runtime.version);
        session.put("runtime_marker_sha256", runtime.markerHash);
        session.put("runtime_lock_sha256", LOCK.hash);
        session.put("runtime_files", runtime.files);
        var external = new TreeMap<String, Object>();
        runtime.external.forEach((name, value) -> external.put(name, Map.of("real_path", value.realPath, "bytes", value.bytes, "sha256", value.sha256)));
        session.put("external_runtime_files", external);
        session.put("legacy_emulation", runtime.emulated);
        session.put("private_GOMP", runtime.privateGomp);
    }

    static int execute(Context runtime, boolean exporter, List<String> arguments, Path work, String label,
            int threads, int timeoutSeconds, long byteBound, Map<String, Object> session) throws Exception {
        if (!label.matches("[a-z][a-z0-9-]{0,63}") || threads < 1 || timeoutSeconds < 1 || byteBound < 1) throw new IOException("Invalid bounded Minimac process contract");
        work = work.toAbsolutePath().normalize();
        if (!Files.isDirectory(work, LinkOption.NOFOLLOW_LINKS) || !work.toRealPath().equals(work)) throw new IOException("Missing or linked owned Minimac work directory");
        runtime.verify();
        if (exporter && !runtime.inspector) throw new IOException("This explicit historical profile has no MSAV exporter; use an admitted current profile for conversion/inspection");
        var command = new ArrayList<String>();
        if (!exporter && runtime.emulated) command.addAll(List.of(runtime.home.resolve("bin/qemu-x86_64-static").toString(), "-cpu", "qemu64"));
        command.add(runtime.home.resolve(exporter ? "bin/sav" : runtime.family.equals("Minimac3") ? "bin/Minimac3" : "bin/minimac4").toString());
        if (!exporter && !runtime.style.equals("modern")) command.add("--noPhoneHome");
        command.addAll(arguments);
        Path stdout = work.resolve(label + ".stdout"), stderr = work.resolve(label + ".stderr");
        if (Files.exists(stdout, LinkOption.NOFOLLOW_LINKS) || Files.exists(stderr, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Process diagnostics already exist; select a new stage");
        Path home = work.resolve(label + "-home"), temp = work.resolve(label + "-tmp");
        Files.createDirectory(home); Files.createDirectory(temp);
        ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        var env = builder.environment();
        env.clear(); env.put("PATH", "/usr/bin:/bin"); env.put("HOME", home.toString()); env.put("TMPDIR", temp.toString());
        env.put("LANG", "C"); env.put("LC_ALL", "C");
        for (String name : List.of("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS")) env.put(name, Integer.toString(threads));
        if (runtime.privateGomp) env.put("LD_LIBRARY_PATH", runtime.home.resolve("lib").toString());
        var call = new LinkedHashMap<String, Object>();
        call.put("command", command); call.put("timeout_seconds", timeoutSeconds); call.put("byte_bound", byteBound);
        call.put("stdout", stdout.toString()); call.put("stderr", stderr.toString()); session.put("process_" + label, call);
        long started = System.nanoTime();
        Process child;
        try { child = builder.start(); }
        catch (IOException failure) { call.put("launch_error", failure.toString()); throw new Unavailable("Verified original Minimac executable could not start on this host/mount", failure); }
        call.put("pid", child.pid());
        var descendants = new LinkedHashSet<ProcessHandle>();
        boolean interrupted = false;
        try {
            long nextMarkerCheck = started;
            for (;;) {
                child.descendants().forEach(descendants::add);
                boolean done = child.waitFor(100, TimeUnit.MILLISECONDS);
                bound(work, byteBound);
                if (System.nanoTime() >= nextMarkerCheck) {
                    if (!runtime.markerHash.equals(FollowupSupport.hash(runtime.home.resolve(MARKER)))) throw new IOException("Minimac runtime marker changed during execution");
                    nextMarkerCheck = System.nanoTime() + 1000000000L;
                }
                if (done) { runtime.verify(); call.put("exit_code", child.exitValue()); return child.exitValue(); }
                if (System.nanoTime() - started > timeoutSeconds * 1000000000L) {
                    call.put("timed_out", true); throw new IOException("Original Minimac exceeded timeout; no partial result is admitted");
                }
            }
        } catch (InterruptedException failure) { interrupted = true; throw failure; }
        finally {
            call.put("seconds", (System.nanoTime() - started) / 1e9);
            if (interrupted) Thread.interrupted();
            try {
                child.descendants().forEach(descendants::add);
                for (ProcessHandle handle : descendants) if (handle.isAlive()) handle.destroyForcibly();
                if (child.isAlive()) child.destroyForcibly();
                if (!child.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Owned Minimac process did not terminate: PID " + child.pid());
                boolean alive = child.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive);
                call.put("alive_after_cleanup", alive); call.put("owned_descendants_seen", descendants.stream().map(ProcessHandle::pid).toList());
                if (alive) throw new IOException("Owned Minimac descendant remains alive; inspect recorded PIDs");
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
    }

    private static void bound(Path root, long byteBound) throws IOException {
        final long[] bytes = {0}; final int[] files = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                if (++files[0] > 4096 || Files.isSymbolicLink(path)) throw new IOException("Minimac staged file-count/link bound exceeded");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || ++files[0] > 4096) throw new IOException("Minimac output contains a linked/nonregular member or excessive files");
                bytes[0] = Math.addExact(bytes[0], attributes.size());
                if (bytes[0] > byteBound) throw new IOException("Minimac staged/output byte bound exceeded");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException failure) throws IOException {
                if (failure instanceof NoSuchFileException) return FileVisitResult.CONTINUE;
                throw failure;
            }
        });
    }

    static String recovery() {
        return "Required action: use Linux x86_64 Java and an admitted original Minimac home with an explicit family/version profile. On Windows use Linux Java inside WSL. Where permitted run python3 tools/literature/install_minimac.py --profile linux-x86_64-minimac4-4.1.6 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install; add --allow-download only where permitted, or supply verified offline archives. Historical profiles keep their own original binaries, notices and explicit QEMU/private-GOMP dependencies. Legacy fitting disables phone-home. Missing, forbidden, unsupported or changed dependencies must SKIP the affected analysis with their cause; preserve existing homes and failed outputs, correct the cause and rerun to new paths. Analysis never installs or substitutes versions. Native reference/dosage/CSI APIs need no original runtime, but they do not perform imputation. Cell Ranger remains excluded.";
    }

    private static Map<?, ?> object(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> map)) throw new IOException("Expected a JSON object in runtime admission");
        return map;
    }
    private static Set<String> requiredExternal(String style) {
        var required = new HashSet<String>(); required.add("ldd");
        if (Set.of("legacy100", "min3").contains(style)) required.addAll(List.of("libz.so.1", "libstdc++.so.6", "libm.so.6", "libgcc_s.so.1", "libc.so.6", "ld-linux-x86-64.so.2"));
        if (style.equals("legacy100")) required.add("libpthread.so.0");
        return Set.copyOf(required);
    }
    private static String text(Map<?, ?> map, String key) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof String result) || result.isBlank()) throw new IOException("Missing runtime admission text: " + key);
        return result;
    }
    private static long whole(Map<?, ?> map, String key, long minimum, long maximum) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.longValue()
                || number.longValue() < minimum || number.longValue() > maximum) throw new IOException("Invalid exact runtime admission integer: " + key);
        return number.longValue();
    }
    private static byte[] bounded(Path path, int maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum) throw new IOException("Missing, linked or oversized runtime marker");
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new IOException("Runtime marker exceeds byte bound");
            return bytes;
        }
    }
    private static String utf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
