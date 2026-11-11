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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Exact original payload and current-host admission with supervised fresh synthetic health. */
final class StringTieRuntime {
    private StringTieRuntime() { }
    static final String ACTION = "Read WARNINGS.md, FAILURE.json and retained process stdout/stderr/ledgers. SKIP3 means a required permitted Linux runtime, helper, library or capability is unavailable: skip dependent analysis and complete permitted setup. FAIL2 means invalid or changed input/evidence, failed process/audit, cancellation or capacity failure: preserve the attempt, correct the cause and use NEW paths. No installation or download occurs during admission. Synthetic health alone does not admit a user fit or certify biological accuracy.";
    private static final String MARKER = ".omicsmatic-stringtie.json";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String reason) { super(reason); }
        Unavailable(String reason, Throwable cause) { super(reason, cause); }
    }
    record Limits(RepeatHostFiles.Limits host, RsemProcess.Limits process, StringTieSetupEvidence.Limits setup, int images) {
        Limits { Objects.requireNonNull(host); Objects.requireNonNull(process); Objects.requireNonNull(setup); if (images < 1 || images > 1024) throw new IllegalArgumentException("Invalid complete ELF image capacity"); }
        static Limits defaults() { return new Limits(new RepeatHostFiles.Limits(134217728, 536870912, 4096, 32, 8388608),
            new RsemProcess.Limits(60000, 1073741824, 60, 67108864, 256, 134217728, 1024, 8, 64, 536870912, 25), StringTieSetupEvidence.Limits.defaults(), 128); }
    }
    static final class Admitted {
        private final Context context;
        private final Path work;
        private final Map<String, Object> evidence;
        private Admitted(Context context, Path work, Map<String, Object> evidence) { this.context = context; this.work = work; this.evidence = Map.copyOf(evidence); }
        String profile() { return context.profile; }
        String version() { return context.version; }
        Path home() { return context.home; }
        Path work() { return work; }
        Map<String, String> environment() { return context.environment; }
        boolean userAnalysisAdmitted() { return false; }
        void verify(Runnable check) throws IOException { context.verify(check); require(evidence.equals(tree(work, context.limits.process, check)), "Complete StringTie admission evidence changed"); }
    }
    private static final List<String> HELPERS = List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/ldd", "/usr/bin/bash");
    private static final List<String> CONFIG = List.of("/etc/ld.so.cache", "/etc/ld.so.conf", "/etc/ld.so.preload", "/etc/nsswitch.conf", "/etc/hosts", "/etc/resolv.conf", "/etc/gai.conf", "/etc/host.conf", "/etc/localtime", "/etc/os-release", "/lib/ld-linux.so.2", "/lib64/ld-linux-x86-64.so.2", "/libx32/ld-linux-x32.so.2");
    private static final Set<Path> SYSTEM = Set.of(Path.of("/usr/lib"), Path.of("/usr/lib64"), Path.of("/usr/libx32"), Path.of("/lib"), Path.of("/lib64"), Path.of("/libx32"), Path.of("/usr/bin"), Path.of("/bin"), Path.of("/etc"), Path.of("/usr/share"), Path.of("/run/systemd/resolve"), Path.of("/run/resolvconf"), Path.of("/mnt/wsl"));

    static Admitted admit(String profile, Path home, Path setupReport, Path newWork, Limits limits, Runnable check) throws IOException {
        platform(); Objects.requireNonNull(limits); Objects.requireNonNull(check); check.run();
        path(home); path(setupReport); path(newWork);
        require(newWork.getParent().toRealPath().equals(newWork.getParent()) && !Files.exists(newWork, LinkOption.NOFOLLOW_LINKS), "StringTie admission requires a NEW canonical private Linux work path");
        require(!overlap(home, newWork) && !overlap(setupReport, newWork) && !overlap(home, setupReport), "StringTie runtime, setup evidence and work must be disjoint");
        boolean created = false;
        try {
            if (!newWork.getFileSystem().supportedFileAttributeViews().contains("posix")) throw new Unavailable("Private Linux filesystem modes are required; use a permitted native Linux directory");
            Files.createDirectory(newWork, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); created = true;
            require(NextflowFiles.mode(newWork) == 0700, "StringTie work mode0700 was not preserved");
            write(newWork.resolve("INCOMPLETE.json"), Map.of("status", "INCOMPLETE_STRINGTIE_NATIVE_ADMISSION", "user_analysis_admitted", false));
            for (String name : List.of("metadata", "inputs", "probes", "health")) Files.createDirectory(newWork.resolve(name));
            write(newWork.resolve("metadata/request.json"), Map.of("profile", profile, "home", home.toString(), "setup_report", setupReport.toString(), "installation_performed", false, "user_analysis_admitted", false));
            Context context = new Context(profile, home, setupReport, limits, check);
            context.discover(newWork, check);
            Map<String, Object> health = context.health(newWork, check);
            context.verify(check);
            write(newWork.resolve("metadata/host.json"), context.document());
            write(newWork.resolve("metadata/health.json"), health);
            Files.writeString(newWork.resolve("WARNINGS.md"), "# StringTie runtime health only\n\nOne tiny synthetic population was fitted with the exact selected original edition, with complete native BAM, GTF, table and normalization audits. Original paths and decimal values are retained. Gene-reduction residuals remain diagnostic; hidden fit denominators and biological accuracy are not recovered or certified. A user fit requires its own explicit input/output checks.\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Map<String, Object> files = new TreeMap<>(tree(newWork, limits.process, check)); files.remove("INCOMPLETE.json");
            write(newWork.resolve("manifest.json"), Map.of("schema_version", 1, "status", "NATIVE_RUNTIME_HEALTH_ONLY", "profile", profile, "version", context.version, "files", files, "user_analysis_admitted", false));
            write(newWork.resolve("COMPLETED.json"), Map.of("status", "COMPLETE_NATIVE_RUNTIME_HEALTH_ONLY", "manifest", MemeRuntimeFiles.pin(NextflowFiles.pin(newWork.resolve("manifest.json"), limits.setup.metadataBytes(), check)), "user_analysis_admitted", false));
            Map<String, Object> guarded = tree(newWork, limits.process, check);
            var completed = new TreeMap<>(guarded); completed.remove("INCOMPLETE.json");
            var result = new Admitted(context, newWork, completed);
            context.verify(check); require(guarded.equals(tree(newWork, limits.process, check)), "StringTie admission evidence changed before final commit");
            Files.delete(newWork.resolve("INCOMPLETE.json")); // Last filesystem commit: no success with a live guard.
            return result;
        } catch (RsemProcess.UnavailableDependency error) {
            var missing = new Unavailable("Required native StringTie process supervision capability is unavailable; skip dependent analysis", error); if (created) failure(newWork, missing); throw missing;
        } catch (AccessDeniedException error) {
            var missing = new Unavailable("Required StringTie runtime/host/work access is forbidden; skip dependent analysis and choose permitted paths", error); if (created) failure(newWork, missing); throw missing;
        } catch (IOException | RuntimeException | Error error) {
            if (created) failure(newWork, error); throw error;
        }
    }

    private static final class Context {
        final String profile, version;
        final Path home;
        final Limits limits;
        final StringTieSetupEvidence.Receipt setup;
        final Map<?, ?> spec;
        final Map<String, String> platform, environment = Map.of("PATH", "/usr/bin:/bin", "LANG", "C", "LC_ALL", "C", "TZ", "UTC", "OMP_NUM_THREADS", "1");
        final Map<String, Object> payload;
        final Set<Path> allowed = new HashSet<>(SYSTEM), roots = new HashSet<>(Set.of(Path.of("/etc/ld.so.conf.d"))), files = new TreeSet<>(), nodes = new TreeSet<>();
        final Map<String, RsemElf.Image> images = new TreeMap<>();
        final Map<String, RsemElf.Report> bindings = new TreeMap<>();
        final List<Path> processWork = new ArrayList<>();
        RepeatHostFiles.Snapshot host;
        Context(String profile, Path home, Path report, Limits limits, Runnable check) throws IOException {
            this.profile = profile; this.home = home; this.limits = limits; platform = platform(); spec = StringTieSetupEvidence.profile(profile); version = NextflowFiles.text(spec.get("version"));
            if (!Files.isDirectory(home) || !Files.isReadable(home)) throw new Unavailable("Exact managed StringTie runtime home is missing or forbidden; run permitted setup or skip dependent analysis");
            setup = StringTieSetupEvidence.read(report, limits.setup, check);
            require(setup.profile().equals(profile) && setup.producerHome().equals(home.toString()), "Setup receipt does not bind this exact managed StringTie home/edition");
            payload = payload(home, spec, setup.marker(), limits, check); allowed.add(home);
            for (String helper : HELPERS) {
                Path file = Path.of(helper); if (!Files.isRegularFile(file) || !Files.isReadable(file) || !Files.isExecutable(file)) throw new Unavailable("Required permitted StringTie native helper is unavailable: " + helper);
                files.add(file); files.add(file.toRealPath());
            }
            for (String name : CONFIG) files.add(Path.of(name)); files.add(Path.of("/bin/bash")); files.add(home.resolve("bin/stringtie")); nodes.addAll(roots);
            host = capture(check);
        }
        void discover(Path work, Runnable check) throws IOException {
            var pending = new TreeSet<Path>(); pending.add(home.resolve("bin/stringtie"));
            for (String helper : List.of("/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/bash")) pending.add(Path.of(helper).toRealPath());
            int ordinal = 0;
            while (!pending.isEmpty()) {
                Path file = pending.pollFirst().toRealPath(); if (images.containsKey(file.toString())) continue;
                require(images.size() < limits.images, "Complete StringTie ELF image capacity exceeded");
                RsemElf.Image image = RsemElf.image(file, limits.host.fileBytes(), check); require(image != null, "Non-ELF native StringTie image: " + file); images.put(file.toString(), image); files.add(file);
                for (Path directory : RsemElf.searchDirectories(file, image)) { allowed.add(directory); roots.add(directory); nodes.add(directory); }
                expand(check);
                Path probe = work.resolve("probes/libraries-" + ordinal++);
                var run = RsemProcess.inspectLibraries(file, environment, probe, limits.process, () -> verify(check), check, accounting(work)); processWork.add(probe);
                byte[] stdout = NextflowFiles.bytes(run.stdout(), 1048576), stderr = NextflowFiles.bytes(run.stderr(), 1048576);
                if ((NextflowFiles.utf8(stdout) + NextflowFiles.utf8(stderr)).contains("not found")) throw new Unavailable("Required StringTie ELF library is missing; inspect the retained library probe and complete permitted host setup");
                RsemElf.Report report = RsemElf.bindings(image, run.exitCode(), stdout, stderr); bindings.put(file.toString(), report);
                for (var binding : report.bindings()) {
                    Path reported = Path.of(binding.path()).normalize(); allowed(reported); Path real = reported.toRealPath(); allowed(real);
                    if (!Files.isReadable(real)) throw new Unavailable("Required StringTie library is unreadable: " + reported);
                    files.add(reported); files.add(real); if (!images.containsKey(real.toString())) pending.add(real);
                    if (binding.loader() && image.interpreter() != null) require(Path.of(image.interpreter()).toRealPath().equals(real), "StringTie ELF interpreter differs from reported loader");
                }
                expand(check);
            }
            require(images.containsKey(home.resolve("bin/stringtie").toString()), "Original StringTie executable was omitted from ELF discovery");
            var versionRun = run(List.of(home.resolve("bin/stringtie").toString(), "--version"), work.resolve("probes/version"), work, check);
            require(NextflowFiles.utf8(NextflowFiles.bytes(versionRun.stdout(), 4096)).strip().equals(version) && Files.size(versionRun.stderr()) == 0, "Exact original StringTie version receipt differs");
        }
        Map<String, Object> health(Path work, Runnable check) throws IOException {
            Map<?, ?> resources = NextflowFiles.object(StringTieSetupEvidence.lock().get("resources"));
            for (String name : List.of("reads.bam", "guide.gtf", "reference.sizes")) {
                String resource = "health-inputs/" + name; byte[] raw = MemeRuntimeFiles.resource(StringTieSetupExport.RESOURCE + resource, NextflowFiles.fingerprint(resources.get(resource), 1048576));
                Files.write(work.resolve("inputs").resolve(name), raw, StandardOpenOption.CREATE_NEW);
            }
            Map<String, Object> inputs = tree(work.resolve("inputs"), limits.process, check);
            Path fit = work.resolve("health/estimation");
            run(List.of(home.resolve("bin/stringtie").toString(), work.resolve("inputs/reads.bam").toString(), "-G", work.resolve("inputs/guide.gtf").toString(), "-e", "-A", "gene-abundance.tsv", "-B", "-p", "1", "-o", "transcripts.gtf"), fit, work, check);
            Map<String, Object> audit = StringTieHealth.audit(work.resolve("inputs"), fit, version, NextflowFiles.object(spec.get("health")), check);
            require(inputs.equals(tree(work.resolve("inputs"), limits.process, check)), "Protected StringTie synthetic inputs changed");
            return audit;
        }
        RsemProcess.Result run(List<String> command, Path destination, Path work, Runnable check) throws IOException {
            var result = RsemProcess.executeOwned(command, environment, destination, limits.process, () -> verify(check), check, accounting(work)); processWork.add(destination); return result;
        }
        List<Path> accounting(Path work) { var result = new ArrayList<>(List.of(work.resolve("metadata"), work.resolve("inputs"))); result.addAll(processWork); return List.copyOf(result); }
        RepeatHostFiles.Snapshot capture(Runnable check) throws IOException { return RepeatHostFiles.capture(roots.stream().sorted().toList(), List.copyOf(files), List.copyOf(nodes), roots, allowed, limits.host, check); }
        void expand(Runnable check) throws IOException { verifyKnown(check); var expanded = capture(check); verifyKnown(check); host = expanded; }
        void verifyKnown(Runnable check) throws IOException { RepeatHostFiles.verify(host, roots, allowed, limits.host, check); require(platform.equals(platform()), "StringTie host CPU/kernel/process identity changed"); }
        void verify(Runnable check) throws IOException { check.run(); setup.verify(limits.setup, check); require(payload.equals(payload(home, spec, setup.marker(), limits, check)), "Managed StringTie payload changed"); verifyKnown(check); }
        void allowed(Path path) throws IOException { if (allowed.stream().noneMatch(path::startsWith)) throw new Unavailable("Unreviewed StringTie library root; use a supported permitted host: " + path); }
        Map<String, Object> document() throws IOException {
            return Map.of("profile", profile, "version", version, "home", home.toString(), "platform", platform, "environment_without_owned_HOME_TMPDIR", environment,
                "complete_host_files", SimpleJson.parse(NextflowFiles.utf8(RsemJson.bytes(host))), "images", SimpleJson.parse(RsemJson.json(images)), "bindings", SimpleJson.parse(RsemJson.json(bindings)),
                "scope", "Declared ELF/helper dependency closure, loader configuration and exact selected payload on this current host. No whole-OS, arbitrary plugin, biological-accuracy or user-fit certificate.");
        }
    }
    private static Map<String, Object> payload(Path home, Map<?, ?> spec, NextflowFiles.Pin markerPin, Limits limits, Runnable check) throws IOException {
        Map<String, Object> actual = tree(home, limits.process, check);
        require(actual.keySet().equals(Set.of(".", "bin", "bin/stringtie", "LICENSE-StringTie.txt", MARKER)), "Managed StringTie payload has extra/missing namespace members");
        require(NextflowFiles.mode(home) == 0700 && NextflowFiles.mode(home.resolve("bin")) == 0700, "Managed StringTie root/bin require mode0700");
        for (var e : NextflowFiles.object(spec.get("installed_files")).entrySet()) {
            String name = NextflowFiles.member(NextflowFiles.text(e.getKey())); var identity = NextflowFiles.identity(home.resolve(name), limits.process.fileBytes(), check);
            require(identity.pin().equals(NextflowFiles.fingerprint(e.getValue(), limits.process.fileBytes())) && identity.links() == 1 && identity.mode() == (name.equals("bin/stringtie") ? 0755 : 0644), "Exact original StringTie payload bytes/mode/link count differs: " + name);
        }
        var marker = NextflowFiles.identity(home.resolve(MARKER), limits.setup.metadataBytes(), check);
        require(marker.pin().equals(markerPin) && marker.mode() == 0600 && marker.links() == 1, "Managed StringTie marker bytes/mode/link count differs from complete setup");
        Map<?, ?> m = NextflowFiles.object(NextflowFiles.json(home.resolve(MARKER), limits.setup.metadataBytes()));
        NextflowFiles.keys(m, "schema_version", "status", "profile", "version", "lock_sha256", "files", "scientific_runtime_admitted", "user_analysis_admitted");
        require(NextflowFiles.integer(m.get("schema_version"), 1, 1) == 1 && Objects.equals(m.get("status"), "EXACT_ORIGINAL_PAYLOAD_ONLY") && Objects.equals(m.get("version"), spec.get("version"))
            && Objects.equals(m.get("lock_sha256"), StringTieSetupExport.LOCK.sha256()) && Objects.equals(m.get("profile"), "linux-x86_64-stringtie-" + spec.get("version"))
            && Boolean.FALSE.equals(m.get("scientific_runtime_admitted")) && Boolean.FALSE.equals(m.get("user_analysis_admitted")), "Managed StringTie marker edition/scope differs");
        Map<?, ?> declared = NextflowFiles.object(m.get("files")), originals = NextflowFiles.object(spec.get("installed_files")); require(declared.keySet().equals(originals.keySet()), "Incomplete managed StringTie marker payload population");
        for (var entry : originals.entrySet()) require(NextflowFiles.fingerprint(declared.get(entry.getKey()), limits.process.fileBytes()).equals(NextflowFiles.fingerprint(entry.getValue(), limits.process.fileBytes())), "Managed marker payload pin differs");
        return actual;
    }
    static Map<String, Object> tree(Path root, RsemProcess.Limits limits, Runnable check) throws IOException { return MemeRuntimeFiles.tree(root, new MemeRuntimeFiles.Limits(limits.fileBytes(), limits.workspaceBytes(), limits.fileCount()), check); }
    private static Map<String, String> platform() throws IOException {
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT))) throw new Unavailable("StringTie runtime admission requires Linux x86_64 Java; on Windows use permitted Linux Java inside WSL or skip dependent analysis");
        try { return MemeNativeHost.read(); } catch (TomtomRuntime.Unavailable error) { throw new Unavailable("Required native Linux procfs/CPU identity is unavailable; use a supported permitted host", error); }
    }
    private static void failure(Path root, Throwable error) {
        try {
            for (String name : List.of("COMPLETED.json", "manifest.json", "WARNINGS.md")) if (Files.exists(root.resolve(name), LinkOption.NOFOLLOW_LINKS)) Files.move(root.resolve(name), root.resolve("UNADMITTED-" + name));
            if (!Files.exists(root.resolve("INCOMPLETE.json"))) write(root.resolve("INCOMPLETE.json"), Map.of("status", "FAILED_STRINGTIE_ADMISSION"));
            String state = error instanceof Unavailable ? "SKIP3_DEPENDENT_ANALYSIS_SKIPPED" : "FAIL2_NO_RUNTIME_OR_RESULT_ADMITTED";
            write(root.resolve("FAILURE.json"), Map.of("status", state, "error", error.toString(), "required_action", ACTION, "user_analysis_admitted", false));
            Files.writeString(root.resolve("WARNINGS.md"), "# StringTie " + state + "\n\n" + error + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException diagnostic) { error.addSuppressed(diagnostic); }
    }
    private static void path(Path path) throws IOException { require(path != null && path.isAbsolute() && path.normalize().equals(path) && path.getParent() != null && path.toString().chars().allMatch(c -> c >= 33 && c < 127), "StringTie runtime paths require absolute canonical ASCII without whitespace"); }
    private static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
    private static void write(Path file, Object object) throws IOException { NextflowFiles.writeJson(file, object); }
    private static void require(boolean condition, String reason) throws IOException { if (!condition) throw new IOException(reason); }
}
