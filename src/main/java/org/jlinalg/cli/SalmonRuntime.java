/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.sequence.SalmonQuantification;

/** Native exact-runtime/current-host admission with fresh original synthetic health; no installation. */
final class SalmonRuntime {
    private SalmonRuntime() { }
    static final String RESOURCE = "/org/jlinalg/omics/salmon-runtime-setup/";
    private static final String MARKER = ".omicsmatic-salmon.json";
    static final String ACTION = "Required action: inspect WARNINGS.md, FAILURE.json when present, the complete native host/health inventory and every retained process stdout/stderr/ledger. "
        + "SKIP3 means a permitted exact runtime, helper, host resource or capability is unavailable: skip dependent analysis and complete only permitted explicit setup. "
        + "FAIL2 means invalid input, changed evidence, process, numerical, capacity, cancellation or finalization failure: no result is admitted. Preserve partials and use NEW paths after correction. "
        + "No installation or download occurs during native admission. Synthetic runtime health does not admit a user fit or certify biological accuracy. Cell Ranger remains excluded.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }
    record Limits(RepeatHostFiles.Limits host, RsemProcess.Limits process, SalmonSetupEvidence.Limits setup, int images) {
        Limits { Objects.requireNonNull(host); Objects.requireNonNull(process); Objects.requireNonNull(setup); if (images < 1 || images > 1024) throw new IllegalArgumentException("Positive bounded complete ELF image capacity is required"); }
        static Limits defaults() {
            return new Limits(new RepeatHostFiles.Limits(1073741824, 3221225472L, 4096, 16, 8388608),
                new RsemProcess.Limits(300000, 8589934592L, 180, 536870912, 4096, 268435456, 4096, 16, 256, 6442450944L, 25),
                SalmonSetupEvidence.Limits.defaults(), 128);
        }
    }
    /** Constructed only after complete payload, host and fresh original health checks pass. */
    static final class Admitted {
        private final Context context;
        private final Path work;
        private final Map<String, Object> workTree;
        private final List<SalmonHealth.Receipt> health;
        private Admitted(Context context, Path work, Map<String, Object> workTree, List<SalmonHealth.Receipt> health) {
            this.context = context; this.work = work; this.workTree = Map.copyOf(workTree); this.health = List.copyOf(health);
        }
        String profile() { return context.profile; }
        String version() { return context.version; }
        Path home() { return context.home; }
        Path work() { return work; }
        List<SalmonHealth.Receipt> health() { return health; }
        boolean userAnalysisAdmitted() { return false; }
        Map<String, String> environment() { return context.environment; }
        void verify(Runnable check) throws IOException {
            context.verify(check); require(workTree.equals(tree(work, context.limits.process, check)), "Completed native Salmon admission evidence changed");
        }
    }
    private static final List<String> HELPERS = List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/ldd", "/usr/bin/bash");
    private static final List<String> CONFIG = List.of("/etc/ld.so.cache", "/etc/ld.so.conf", "/etc/ld.so.preload", "/etc/nsswitch.conf", "/etc/hosts", "/etc/resolv.conf", "/etc/gai.conf", "/etc/host.conf", "/etc/localtime", "/etc/os-release",
        "/lib/ld-linux.so.2", "/lib64/ld-linux-x86-64.so.2", "/libx32/ld-linux-x32.so.2");
    private static final Set<Path> SYSTEM_TARGETS = Set.of(Path.of("/usr/lib"), Path.of("/usr/lib64"), Path.of("/usr/libx32"), Path.of("/lib"), Path.of("/lib64"),
        Path.of("/libx32"), Path.of("/usr/bin"), Path.of("/etc"), Path.of("/usr/share"), Path.of("/run/systemd/resolve"), Path.of("/run/resolvconf"), Path.of("/mnt/wsl"));

    static Admitted admit(String profile, Path home, Path setupReport, Path newWork, Limits limits, Runnable check) throws IOException {
        platform(); Objects.requireNonNull(limits); Objects.requireNonNull(check); check.run();
        privatePath(home); privatePath(newWork); NextflowFiles.directory(setupReport);
        require(newWork.getParent() != null && newWork.getParent().toRealPath().equals(newWork.getParent()) && !Files.exists(newWork, LinkOption.NOFOLLOW_LINKS), "Native Salmon admission requires a NEW canonical work directory");
        require(!overlap(home, newWork) && !overlap(setupReport, newWork) && !overlap(home, setupReport), "Native Salmon home, setup report and work must be disjoint");
        boolean fresh = false;
        try {
            if (!newWork.getFileSystem().supportedFileAttributeViews().contains("posix")) throw new Unavailable("Salmon native admission requires a filesystem preserving private Linux modes");
            Files.createDirectory(newWork, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); fresh = true;
            if (NextflowFiles.mode(newWork) != 0700) throw new Unavailable("Native Salmon work filesystem did not preserve mode0700; choose a permitted private Linux filesystem");
            for (String name : List.of("metadata", "probes", "inputs", "health")) Files.createDirectory(newWork.resolve(name));
            write(newWork.resolve("metadata/request.json"), Map.of("profile", profile, "home", home.toString(), "setup_report", setupReport.toString(), "native_installation_performed", false, "user_analysis_admitted", false));
            Context context = new Context(profile, home, setupReport, limits, check);
            context.discover(newWork, check);
            List<SalmonHealth.Receipt> health = context.health(newWork, check);
            context.verify(check);
            write(newWork.resolve("metadata/host.json"), context.document());
            write(newWork.resolve("metadata/health.json"), Map.of("status", "passed-fresh-native-supervised-original-synthetic-health", "fits", health.stream().map(SalmonHealth.Receipt::document).toList(),
                "below_burn_in", true, "original_four_target_position_bias_failures_unresolved", true, "biological_accuracy_certified", false, "user_analysis_admitted", false));
            Files.writeString(newWork.resolve("WARNINGS.md"), "# Salmon runtime health only — no user fit admitted\n\n"
                + "Two fresh fits reuse one tiny40-target synthetic population below original burn-in thresholds. Retain original warnings and observed-mapping effective-length fallback. "
                + "Separate original four-target positional-bias crashes remain unresolved; never remove requested bias correction silently. "
                + "Original1.0.0 lacks two quantification SHA-512 links; preserve them as unavailable. Automatic reported IU and observed ISR counts remain distinct.\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Map<String, Object> files = tree(newWork, limits.process, check);
            write(newWork.resolve("manifest.json"), Map.of("schema_version", 1, "status", "NATIVE_RUNTIME_HEALTH_ONLY", "profile", profile, "version", context.version,
                "home", home.toString(), "files", files, "user_analysis_admitted", false));
            write(newWork.resolve("COMPLETED.json"), Map.of("status", "COMPLETE_NATIVE_RUNTIME_HEALTH_ONLY", "manifest", pinObject(NextflowFiles.pin(newWork.resolve("manifest.json"), limits.setup.metadataBytes(), check)), "user_analysis_admitted", false));
            Map<String, Object> completedTree = tree(newWork, limits.process, check); context.verify(check);
            require(completedTree.equals(tree(newWork, limits.process, check)), "Native Salmon evidence changed during finalization");
            return new Admitted(context, newWork, completedTree, health);
        } catch (IOException | RuntimeException failure) {
            IOException typed = failure instanceof Unavailable u ? u : failure instanceof RsemProcess.UnavailableDependency || failure instanceof AccessDeniedException
                ? new Unavailable("Required permitted native Salmon dependency/capability is unavailable: " + failure.getMessage(), failure)
                : failure instanceof IOException io ? io : new IOException("Native Salmon admission cancelled or invalid; no runtime/result admitted", failure);
            if (fresh) failure(newWork, typed); throw typed;
        }
    }

    private static final class Context {
        final String profile, version;
        final Path home, report;
        final Limits limits;
        final Map<?, ?> profileLock, setupManifest, healthLock;
        final Map<String, Object> payload, setupTree;
        final Map<String, String> environment, platform;
        final Set<Path> allowed;
        final Set<Path> treeTargets;
        final TreeSet<Path> files = new TreeSet<>(), nodes = new TreeSet<>();
        final Map<String, RsemElf.Image> images = new TreeMap<>();
        final Map<String, RsemElf.Report> bindings = new TreeMap<>();
        final List<Path> processWork = new ArrayList<>();
        RepeatHostFiles.Snapshot host;
        Context(String requested, Path suppliedHome, Path setupReport, Limits limits, Runnable check) throws IOException {
            this.profile = requested; home = suppliedHome; report = setupReport; this.limits = limits; platform = platform();
            Map<?, ?> lock = resourceJson("salmon-runtime-lock.json", SalmonSetupEvidence.LOCK_SHA); healthLock = resourceJson("health-lock.json", SalmonSetupEvidence.HEALTH_SHA);
            profileLock = object(object(lock.get("profiles")).get(requested)); version = text(profileLock.get("version"));
            if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS)) throw new Unavailable("Exact managed Salmon home is absent; complete permitted explicit setup first");
            NextflowFiles.directory(home); require((NextflowFiles.mode(home) & 077) == 0 && (NextflowFiles.mode(home) & 0500) == 0500, "Salmon runtime home must retain private readable/executable owner modes");
            var setup = SalmonSetupEvidence.read(report, limits.setup, check);
            require(setup.profile().equals(requested) && setup.producerHome().equals(home.toString()), "Salmon setup profile/home does not bind the requested runtime");
            setupManifest = SalmonHealth.json(report.resolve("manifest.json"));
            require(setup.manifest().equals(NextflowFiles.pin(report.resolve("manifest.json"), limits.setup.metadataBytes(), check)), "Salmon setup manifest changed after portable audit");
            payload = payload(home, profileLock, limits.process, check);
            if (!Files.isReadable(home.resolve("bin/salmon")) || !Files.isExecutable(home.resolve("bin/salmon"))) throw new Unavailable("Exact original Salmon runtime is unreadable or execution is forbidden; complete permitted setup or skip dependent analysis");
            require(NextflowFiles.fingerprint(setupManifest.get("marker"), limits.setup.metadataBytes()).equals(NextflowFiles.pin(home.resolve(MARKER), limits.setup.metadataBytes(), check)), "Managed Salmon home marker changed since setup");
            Map<?, ?> marker = SalmonHealth.json(home.resolve(MARKER));
            require(Objects.equals(marker.get("profile"), requested) && Objects.equals(marker.get("version"), version) && Objects.equals(marker.get("lock_sha256"), SalmonSetupEvidence.LOCK_SHA)
                && Objects.equals(marker.get("status"), "ORIGINAL_PAYLOAD_ONLY") && Boolean.FALSE.equals(marker.get("scientific_runtime_admitted")) && Boolean.FALSE.equals(marker.get("user_analysis_admitted")), "Managed Salmon marker profile/status differs");
            require(Boolean.FALSE.equals(setupManifest.get("user_analysis_admitted")), "Setup cannot admit a user fit");
            setupTree = tree(report, limits.process, check);
            environment = Map.of("PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C", "TZ", "UTC", "LD_LIBRARY_PATH", home.resolve("lib").toString(), "SALMON_NO_VERSION_CHECK", "1", "OMP_NUM_THREADS", "2");
            var allowedPaths = new HashSet<>(SYSTEM_TARGETS); allowedPaths.add(home); allowed = allowedPaths;
            treeTargets = new HashSet<>(Set.of(home, Path.of("/etc/ld.so.conf.d")));
            for (String helper : HELPERS) {
                Path path = Path.of(helper); if (!Files.isRegularFile(path) || !Files.isReadable(path) || !Files.isExecutable(path)) throw new Unavailable("Required existing native Salmon helper is missing or forbidden: " + helper);
                Map<?, ?> prior = object(object(setupManifest.get("helpers")).get(helper)); Path real = path.toRealPath();
                require(real.toString().equals(text(prior.get("real_path"))) && NextflowFiles.pin(real, limits.host.fileBytes(), check).equals(NextflowFiles.fingerprint(prior, limits.host.fileBytes()))
                    && NextflowFiles.mode(real) == NextflowFiles.integer(prior.get("mode"), 0, 0777), "Salmon setup/current helper binding differs: " + helper);
                files.add(path); files.add(real);
            }
            for (String name : CONFIG) files.add(Path.of(name)); files.add(Path.of("/bin/bash"));
            files.add(home.resolve("bin/salmon")); nodes.add(Path.of("/etc/ld.so.conf.d"));
            host = capture(check);
        }
        void discover(Path work, Runnable check) throws IOException {
            var pending = new TreeSet<Path>(); pending.add(home.resolve("bin/salmon"));
            for (String helper : List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/bash")) pending.add(Path.of(helper).toRealPath());
            Map<String, Object> initialPayload = payload;
            for (int ordinal = 0; !pending.isEmpty(); ordinal++) {
                Path imagePath = pending.pollFirst().toRealPath(); if (images.containsKey(imagePath.toString())) continue;
                require(images.size() < limits.images, "Complete native Salmon ELF image capacity exceeded");
                RsemElf.Image image = RsemElf.image(imagePath, limits.host.fileBytes(), check); require(image != null, "Non-ELF image in native Salmon dependency closure: " + imagePath);
                images.put(imagePath.toString(), image); files.add(imagePath);
                for (Path directory : RsemElf.searchDirectories(imagePath, image)) {
                    // Exact consumed ELF search roots are dependencies even when absent.
                    // Capture their complete bounded tree, so a later added library or
                    // hardware-capability subdirectory cannot evade verification.
                    treeTargets.add(directory); allowed.add(directory); nodes.add(directory);
                }
                expand(check);
                Path probe = work.resolve("probes/libraries-" + ordinal);
                RsemProcess.Result result = RsemProcess.inspectLibraries(imagePath, environment, probe, limits.process,
                    () -> { require(initialPayload.equals(payload(home, profileLock, limits.process, check)), "Salmon payload changed during library discovery"); verifyKnown(check); }, check, accounting(work));
                processWork.add(probe);
                byte[] out = NextflowFiles.bytes(result.stdout(), 1048576), err = NextflowFiles.bytes(result.stderr(), 1048576);
                if ((NextflowFiles.utf8(out) + NextflowFiles.utf8(err)).contains("not found")) throw new Unavailable("Required Salmon ELF library is unavailable; inspect the retained library probe and complete permitted host setup");
                RsemElf.Report report = RsemElf.bindings(image, result.exitCode(), out, err); bindings.put(imagePath.toString(), report);
                if (imagePath.equals(home.resolve("bin/salmon"))) require(image.interpreter() != null && report.virtualDso() && !report.noLibraryBindings(), "Original Salmon executable lacks its expected dynamic loader/bindings");
                for (RsemElf.Binding binding : report.bindings()) {
                    Path reported = Path.of(binding.path()).normalize(); allowed(reported); Path real = reported.toRealPath(); allowed(real);
                    if (!Files.isReadable(real)) throw new Unavailable("Required reported Salmon library is unreadable: " + reported);
                    files.add(reported); files.add(real);
                    RsemElf.Image child = RsemElf.image(real, limits.host.fileBytes(), check); require(child != null, "Reported Salmon library is not ELF: " + reported);
                    if (!images.containsKey(real.toString())) pending.add(real);
                    if (binding.loader() && image.interpreter() != null) require(Path.of(image.interpreter()).toRealPath().equals(real), "ELF interpreter and reported loader differ");
                }
                expand(check);
            }
            require(images.containsKey(home.resolve("bin/salmon").toString()), "Original Salmon executable was omitted from dependency discovery");
            compareSetupBindings(check); verify(check);
            var versionRun = run(List.of(home.resolve("bin/salmon").toString(), "--version"), "version", work, check);
            require(SalmonHealth.utf8(versionRun.stdout()).strip().equals("salmon " + version) && Files.size(versionRun.stderr()) == 0, "Original exact Salmon version receipt differs");
        }
        List<SalmonHealth.Receipt> health(Path work, Runnable check) throws IOException {
            for (var row : object(healthLock.get("files")).entrySet()) {
                String name = NextflowFiles.member(text(row.getKey())); NextflowFiles.Pin expected = NextflowFiles.fingerprint(row.getValue(), 8388608);
                byte[] raw = resource("health-inputs/" + name, expected); Files.write(work.resolve("inputs").resolve(name), raw, StandardOpenOption.CREATE_NEW);
            }
            Map<String, Object> inputs = tree(work.resolve("inputs"), limits.process, check);
            var targets = new ArrayList<SalmonQuantification.Target>();
            for (Object value : NextflowFiles.list(healthLock.get("targets"))) { Map<?, ?> row = object(value); targets.add(new SalmonQuantification.Target(text(row.get("name")), NextflowFiles.integer(row.get("length"), 1, 0xffffffffL))); }
            var receipts = new ArrayList<SalmonHealth.Receipt>();
            for (Object value : NextflowFiles.list(healthLock.get("commands"))) {
                Map<?, ?> spec = object(value); String label = text(spec.get("label")); var command = new ArrayList<String>();
                for (String token : NextflowFiles.strings(spec.get("command"))) command.add(token.replace("@HOME@", home.toString()).replace("@REPORT@", work.toString()));
                require(inputs.equals(tree(work.resolve("inputs"), limits.process, check)), "Protected native Salmon health inputs changed");
                run(command, label, work, check);
                if (spec.containsKey("output")) receipts.add(SalmonHealth.audit(work, version, spec, targets, check));
            }
            require(receipts.size() == 2 && receipts.stream().mapToLong(r -> r.replicates().values()).sum() == 6000, "Complete fresh Salmon health population differs");
            require(inputs.equals(tree(work.resolve("inputs"), limits.process, check)), "Native Salmon health inputs changed during execution");
            return List.copyOf(receipts);
        }
        RsemProcess.Result run(List<String> command, String label, Path work, Runnable check) throws IOException {
            Path probe = work.resolve("probes").resolve(label); RsemProcess.Result result = RsemProcess.executeOwned(command, environment, probe, limits.process, () -> verify(check), check, accounting(work)); processWork.add(probe); return result;
        }
        List<Path> accounting(Path work) { var result = new ArrayList<>(List.of(work.resolve("metadata"), work.resolve("inputs"), work.resolve("health"))); result.addAll(processWork); return List.copyOf(result); }
        RepeatHostFiles.Snapshot capture(Runnable check) throws IOException {
            return RepeatHostFiles.capture(treeTargets.stream().sorted().toList(), List.copyOf(files), List.copyOf(nodes), treeTargets, allowed, limits.host, check);
        }
        void expand(Runnable check) throws IOException {
            verifyKnown(check); RepeatHostFiles.Snapshot expanded = capture(check); verifyKnown(check); host = expanded;
        }
        void verifyKnown(Runnable check) throws IOException { RepeatHostFiles.verify(host, treeTargets, allowed, limits.host, check); require(platform.equals(platform()), "Native Salmon host CPU/kernel/process identity changed"); }
        void verify(Runnable check) throws IOException {
            check.run(); require(payload.equals(payload(home, profileLock, limits.process, check)), "Exact Salmon payload bytes/modes/namespace changed");
            require(setupTree.equals(tree(report, limits.process, check)), "Completed Salmon setup evidence changed"); verifyKnown(check);
        }
        void allowed(Path path) throws IOException { if (allowed.stream().noneMatch(path::startsWith)) throw new Unavailable("Unreviewed Salmon ELF search/binding root; preserve diagnostics and use a supported permitted host: " + path); }
        void compareSetupBindings(Runnable check) throws IOException {
            Map<?, ?> expected = object(setupManifest.get("library_bindings")); RsemElf.Report original = bindings.get(home.resolve("bin/salmon").toString());
            var names = new HashSet<String>();
            for (RsemElf.Binding binding : original.bindings()) {
                String name = binding.loader() ? "@loader" : binding.name(); names.add(name); Map<?, ?> row = object(expected.get(name));
                Path real = Path.of(binding.path()).toRealPath();
                require(Objects.equals(row.get("reported_path"), binding.path()) && Objects.equals(row.get("real_path"), real.toString())
                    && NextflowFiles.pin(real, limits.host.fileBytes(), check).equals(NextflowFiles.fingerprint(row, limits.host.fileBytes()))
                    && NextflowFiles.mode(real) == NextflowFiles.integer(row.get("mode"), 0, 0777), "Salmon setup/current ELF binding differs: " + name);
            }
            require(names.equals(expected.keySet()), "Complete setup/current Salmon library population differs");
        }
        Map<String, Object> document() throws IOException {
            return Map.of("profile", profile, "version", version, "home", home.toString(), "platform", platform, "environment_without_owned_HOME_TMPDIR", environment,
                "complete_host_files", SimpleJson.parse(new String(RsemJson.bytes(host), StandardCharsets.UTF_8)),
                "images", SimpleJson.parse(RsemJson.json(images)), "bindings", SimpleJson.parse(RsemJson.json(bindings)),
                "scope", "Complete exact payload; current declared ELF/helper dependency closure and loader configuration. Clean native process environment. No arbitrary plugin/network/whole-index or biological certificate.");
        }
    }

    private static Map<String, Object> payload(Path home, Map<?, ?> profile, RsemProcess.Limits limits, Runnable check) throws IOException {
        var actual = new TreeMap<>(tree(home, new RsemProcess.Limits(limits.wallMillis(), limits.addressSpaceBytes(), limits.cpuSeconds(), 1073741824,
            limits.openFiles(), 2147483648L, limits.fileCount(), limits.processes(), limits.threads(), limits.sampledRssBytes(), limits.monitorMillis()), check));
        require(actual.remove(MARKER) != null, "Managed Salmon marker is missing"); var expected = new TreeMap<String, Object>();
        for (var entry : object(profile.get("members")).entrySet()) {
            Map<?, ?> original = object(entry.getValue()); var row = new LinkedHashMap<String, Object>();
            for (String key : List.of("kind", "mode", "bytes", "sha256")) if (original.containsKey(key)) row.put(key, original.get(key));
            expected.put(text(entry.getKey()), row);
        }
        require(expected.equals(actual), "Complete exact Salmon runtime payload namespace, bytes, modes or node types differ"); return Map.copyOf(actual);
    }
    static Map<String, Object> tree(Path root, RsemProcess.Limits limits, Runnable check) throws IOException {
        NextflowFiles.directory(root); var result = new TreeMap<String, Object>(); long bytes = 0;
        try (var paths = Files.walk(root)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                check.run(); if (path.equals(root)) continue; require(result.size() < limits.fileCount(), "Complete Salmon workspace node capacity exceeded");
                require(path.toRealPath().equals(path) && !Files.isSymbolicLink(path), "Linked Salmon workspace member");
                String name = NextflowFiles.member(root.relativize(path).toString()); var row = new TreeMap<String, Object>();
                boolean directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS); row.put("kind", directory ? "directory" : "file"); row.put("mode", (long) NextflowFiles.mode(path));
                if (!directory) { NextflowFiles.Pin pin = NextflowFiles.pin(path, limits.fileBytes(), check); bytes = Math.addExact(bytes, pin.bytes()); require(bytes <= limits.workspaceBytes(), "Complete Salmon workspace byte capacity exceeded"); row.putAll(pinObject(pin)); }
                result.put(name, Map.copyOf(row));
            }
        }
        return Map.copyOf(result);
    }
    private static Map<String, String> platform() throws IOException {
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))
            throw new Unavailable("Native Salmon runtime admission requires Linux x86_64 Java; use Linux Java inside WSL on Windows");
        var result = new TreeMap<String, String>(); result.put("os", "Linux"); result.put("architecture", "x86_64"); result.put("kernel", System.getProperty("os.version"));
        String status, cpu;
        try { status = proc(Path.of("/proc/self/status"), 65536); cpu = proc(Path.of("/proc/cpuinfo"), 4194304); }
        catch (IOException missing) { throw new Unavailable("Required readable Linux procfs host identity is unavailable", missing); }
        for (String raw : status.split("\n")) for (String name : List.of("Uid:", "Gid:", "Groups:", "Cpus_allowed_list:")) if (raw.startsWith(name)) {
            String value = raw.substring(name.length()).strip().replaceAll("[ \\t]+", " "); require(result.putIfAbsent(name, value) == null, "Duplicate native Salmon host identity field");
        }
        require(result.keySet().containsAll(List.of("Uid:", "Gid:", "Groups:", "Cpus_allowed_list:")), "Incomplete native Salmon host process identity");
        var lines = new TreeSet<String>();
        for (String raw : cpu.split("\n")) { int colon = raw.indexOf(':'); if (colon < 0) continue; String name = raw.substring(0, colon).strip();
            if (Set.of("vendor_id", "cpu family", "model", "model name", "stepping", "microcode", "flags").contains(name)) lines.add(name + "=" + raw.substring(colon + 1).strip().replaceAll("[ \\t]+", " "));
        }
        require(lines.stream().anyMatch(s -> s.startsWith("flags=")) && lines.stream().anyMatch(s -> s.startsWith("model name=")), "Incomplete native Salmon CPU identity/features");
        result.put("cpu_profile_sha256", NextflowFiles.sha(String.join("\n", lines).getBytes(StandardCharsets.UTF_8))); return Map.copyOf(result);
    }
    private static String proc(Path path, int capacity) throws IOException {
        // procfs reports zero st_size; read through EOF with an independent capacity.
        try (var input = Files.newInputStream(path)) { byte[] raw = input.readNBytes(capacity + 1); require(raw.length <= capacity, "Complete native Salmon procfs identity exceeds capacity"); return NextflowFiles.utf8(raw); }
    }
    private static Map<?, ?> resourceJson(String name, String sha) throws IOException {
        try (var input = SalmonRuntime.class.getResourceAsStream(RESOURCE + name)) { if (input == null) throw new IOException("Missing Salmon resource: " + name); byte[] raw = input.readNBytes(1000000); require(NextflowFiles.sha(raw).equals(sha), "Pinned Salmon resource differs: " + name); return object(SimpleJson.parse(NextflowFiles.utf8(raw))); }
    }
    private static byte[] resource(String name, NextflowFiles.Pin pin) throws IOException {
        try (var input = SalmonRuntime.class.getResourceAsStream(RESOURCE + name)) { if (input == null) throw new IOException("Missing protected Salmon health resource: " + name); byte[] raw = input.readNBytes(Math.toIntExact(pin.bytes() + 1)); require(raw.length == pin.bytes() && NextflowFiles.sha(raw).equals(pin.sha256()), "Protected Salmon health resource differs: " + name); return raw; }
    }
    private static void failure(Path root, IOException error) {
        try {
            if (Files.exists(root.resolve("COMPLETED.json"))) Files.move(root.resolve("COMPLETED.json"), root.resolve("COMPLETED-REVOKED.json"));
            if (Files.exists(root.resolve("WARNINGS.md"))) Files.move(root.resolve("WARNINGS.md"), root.resolve("WARNINGS-PREFINAL.md"));
            String state = error instanceof Unavailable ? "SKIP3 — DEPENDENT ANALYSIS SKIPPED" : "FAIL2 — NO RUNTIME/RESULT ADMITTED";
            write(root.resolve("FAILURE.json"), Map.of("status", state, "error", error.toString(), "required_action", ACTION, "user_analysis_admitted", false));
            Files.writeString(root.resolve("WARNINGS.md"), "# Salmon " + state + "\n\n" + error + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException diagnostic) { error.addSuppressed(diagnostic); }
    }
    private static void privatePath(Path path) throws IOException {
        require(path != null && path.isAbsolute() && path.normalize().equals(path) && path.getParent() != null
            && path.toString().chars().allMatch(c -> c >= 33 && c < 127), "Salmon original runtime/work paths require canonical absolute ASCII text without whitespace");
    }
    private static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
    private static Map<String, Object> pinObject(NextflowFiles.Pin pin) { return Map.of("bytes", pin.bytes(), "sha256", pin.sha256()); }
    private static void write(Path path, Object value) throws IOException { Files.writeString(path, NextflowJson.json(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
    private static Map<?, ?> object(Object value) throws IOException { return NextflowFiles.object(value); }
    private static String text(Object value) throws IOException { return NextflowFiles.text(value); }
    private static void require(boolean test, String message) throws IOException { if (!test) throw new IOException(message); }
}
