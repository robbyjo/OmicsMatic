/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jlinalg.sequence.SalmonInferentialReplicates;
import org.jlinalg.sequence.SalmonQuantification;

/**
 * Portable, complete replay of exact Salmon setup receipts. Producer Linux paths
 * remain text: this reader never opens a recorded runtime, helper or host library.
 * Hashes establish consistency, not authenticity or current-host admission.
 */
final class SalmonSetupEvidence {
    private SalmonSetupEvidence() { }
    static final String LOCK_SHA = "5e76aae9e640caf15ab995a314151c4d5503f4afddc112858fabdb9b1e9d8076";
    static final String HEALTH_SHA = "b99b4fd8dcbcb92bae0312293d3e7f73a549a2a81a045b8b1ac812ba98f72778";
    static final NextflowFiles.Pin INSTALLER = new NextflowFiles.Pin(23402, "b5f5179e42b43dbc3ded5529289de2d9716f312b4ac8d937ec6f248c7b524d0a");
    private static final String RESOURCE = "/org/jlinalg/omics/salmon-runtime-setup/";
    private static final List<String> PREFIX = List.of("/usr/bin/prlimit", "--as=8589934592", "--cpu=180", "--fsize=536870912", "--nofile=4096", "--core=0", "--");
    private static final Pattern NAMED = Pattern.compile("^(\\S+)\\s+=>\\s+(/\\S+)\\s+\\(0x[0-9a-fA-F]+\\)$");
    private static final Pattern LOADER = Pattern.compile("^(/\\S+)\\s+\\(0x[0-9a-fA-F]+\\)$");
    private static final Pattern VDSO = Pattern.compile("^linux-vdso\\.so\\.1\\s+\\(0x[0-9a-fA-F]+\\)$");

    record Limits(int nodes, long totalBytes, long fileBytes, int metadataBytes) {
        Limits {
            if (nodes < 1 || totalBytes < 1 || fileBytes < 1 || metadataBytes < 1 || metadataBytes == Integer.MAX_VALUE)
                throw new IllegalArgumentException("Positive complete Salmon setup capacities are required");
        }
        static Limits defaults() { return new Limits(4096, 2147483648L, 1073741824, 8388608); }
    }
    record Health(String label, List<String> reportedLibraryTypes, List<String> unavailableMetadataBindings, SalmonQuantification.Table table,
                  SalmonQuantification.Audit arithmetic, SalmonInferentialReplicates.Summary replicates) {
        Health { reportedLibraryTypes = List.copyOf(reportedLibraryTypes); unavailableMetadataBindings = List.copyOf(unavailableMetadataBindings); }
    }
    record VerifiedReport(String profile, String version, String producerHome, String producerReport,
                          boolean installed, int nodes, long bytes, NextflowFiles.Pin manifest,
                          List<Health> health) {
        VerifiedReport { health = List.copyOf(health); }
        boolean scientificRuntimeAdmitted() { return false; }
        boolean userAnalysisAdmitted() { return false; }
    }
    private record Node(String kind, NextflowFiles.Pin pin, int mode, String key, String modified) { }

    static VerifiedReport read(Path report, Limits limits, Runnable check) throws IOException {
        Objects.requireNonNull(limits); Objects.requireNonNull(check); NextflowFiles.directory(report); check.run();
        Map<?, ?> lock = resource("salmon-runtime-lock.json", LOCK_SHA), healthLock = resource("health-lock.json", HEALTH_SHA);
        try (var input = SalmonSetupEvidence.class.getResourceAsStream(RESOURCE + "install_salmon.py")) {
            if (input == null) throw new IOException("Pinned Salmon installer resource is missing");
            byte[] raw = input.readNBytes(1000000);
            require(new NextflowFiles.Pin(raw.length, NextflowFiles.sha(raw)).equals(INSTALLER), "Pinned Salmon installer resource differs");
        }
        Map<String, Node> before = census(report, limits, check);
        Map<?, ?> completed = json(report, "COMPLETED.json", limits), manifest = json(report, "manifest.json", limits);
        NextflowFiles.keys(completed, "status", "profile", "manifest", "scientific_runtime_admitted", "user_analysis_admitted");
        equal(completed.get("status"), "COMPLETE_SETUP_ONLY", "Complete Salmon setup status"); noAdmission(completed);
        NextflowFiles.keys(manifest, "schema_version", "profile", "version", "home", "installed", "marker", "helpers", "library_bindings", "lock_sha256", "health_lock_sha256", "installer", "files", "scientific_runtime_admitted", "user_analysis_admitted");
        integer(manifest.get("schema_version"), 1); noAdmission(manifest);
        NextflowFiles.Pin manifestPin = NextflowFiles.fingerprint(completed.get("manifest"), limits.metadataBytes());
        require(manifestPin.equals(before.get("manifest.json").pin()), "Complete Salmon setup manifest hash differs");
        String profile = text(manifest.get("profile")), version = text(manifest.get("version")), home = linux(manifest.get("home"));
        equal(completed.get("profile"), profile, "Completed Salmon profile");
        Map<?, ?> profileLock = object(object(lock.get("profiles")).get(profile));
        equal(profileLock.get("version"), version, "Exact Salmon edition");
        require(profile.equals("linux-x86_64-salmon-" + version), "Salmon profile/edition binding differs");
        equal(manifest.get("lock_sha256"), LOCK_SHA, "Salmon runtime lock"); equal(manifest.get("health_lock_sha256"), HEALTH_SHA, "Salmon health lock");
        require(NextflowFiles.fingerprint(manifest.get("installer"), 1000000).equals(INSTALLER), "Unsupported Salmon setup producer");
        NextflowFiles.fingerprint(manifest.get("marker"), limits.metadataBytes()); // Declared producer-home pin, never a current-host check.
        boolean installed = bool(manifest.get("installed"));
        Map<?, ?> files = object(manifest.get("files")); inventory(files, before, Set.of("manifest.json", "COMPLETED.json"));
        Map<?, ?> request = json(report, "request.json", limits);
        NextflowFiles.keys(request, "profile", "home", "kit", "allow_install", "allow_download", "archive_dir", "installer", "lock_sha256", "health_lock_sha256", "user_analysis_admitted");
        for (String key : List.of("profile", "home", "installer", "lock_sha256", "health_lock_sha256")) equal(request.get(key), manifest.get(key), "Salmon request/manifest " + key);
        equal(request.get("user_analysis_admitted"), false, "Salmon request admission"); linux(request.get("kit"));
        require(!installed || bool(request.get("allow_install")), "Salmon install lacks explicit authorization");
        bool(request.get("allow_download")); if (request.get("archive_dir") != null) require(!text(request.get("archive_dir")).isEmpty(), "Empty recorded Salmon archive directory");
        namespace(before, profileLock, installed, bool(request.get("allow_download")));
        Map<?, ?> versionProcess = json(report, "version.process.json", limits);
        String producer = linux(object(versionProcess.get("environment")).get("HOME"));
        require(!overlap(home, producer), "Salmon home/report overlap");
        Map<String, String> environment = Map.of("PATH", "/usr/bin:/bin", "HOME", producer, "TMPDIR", producer,
            "LANG", "C", "LC_ALL", "C", "TZ", "UTC", "LD_LIBRARY_PATH", home + "/lib", "SALMON_NO_VERSION_CHECK", "1");
        process(report, "version", List.of(home + "/bin/salmon", "--version"), environment, limits);
        equal(utf8(report, "version.stdout", limits).strip(), "salmon " + version, "Original Salmon version output");
        equal(utf8(report, "version.stderr", limits), "", "Original Salmon version stderr");
        process(report, "libraries", List.of("/usr/bin/ldd", home + "/bin/salmon"), environment, limits);
        libraries(report, manifest, profileLock, home, limits);
        Map<?, ?> helpers = object(manifest.get("helpers"));
        require(helpers.keySet().equals(Set.of("/usr/bin/prlimit", "/usr/bin/ldd", "/usr/bin/bash", "/usr/bin/setsid")), "Complete Salmon helper population differs");
        for (Object value : helpers.values()) {
            Map<?, ?> row = object(value); NextflowFiles.keys(row, "real_path", "mode", "bytes", "sha256");
            linux(row.get("real_path")); NextflowFiles.fingerprint(row, limits.fileBytes());
            require((NextflowFiles.integer(row.get("mode"), 0, 0777) & 0111) != 0, "Salmon setup helper is not recorded executable");
        }
        for (var row : object(healthLock.get("files")).entrySet()) {
            String name = "inputs/" + NextflowFiles.member(text(row.getKey()));
            require(before.containsKey(name) && NextflowFiles.fingerprint(row.getValue(), limits.fileBytes()).equals(before.get(name).pin()), "Pinned complete Salmon health input differs: " + name);
        }
        var targets = new ArrayList<SalmonQuantification.Target>();
        for (Object value : NextflowFiles.list(healthLock.get("targets"))) {
            Map<?, ?> row = object(value); targets.add(new SalmonQuantification.Target(text(row.get("name")), NextflowFiles.integer(row.get("length"), 1, 0xffffffffL)));
        }
        Map<?, ?> health = json(report, "health.json", limits);
        NextflowFiles.keys(health, "status", "profile", "version", "audits", "scientific_runtime_admitted", "user_analysis_admitted"); noAdmission(health);
        equal(health.get("status"), "passed-two-original-optimizer-bias-and-bootstrap-health-controls", "Salmon health receipt status");
        equal(health.get("profile"), profile, "Salmon health profile"); equal(health.get("version"), version, "Salmon health edition");
        List<?> recordedAudits = NextflowFiles.list(health.get("audits")); require(recordedAudits.size() == 2, "Incomplete Salmon health fit population");
        var audits = new ArrayList<Health>(); int ordinal = 0;
        for (Object value : NextflowFiles.list(healthLock.get("commands"))) {
            check.run(); Map<?, ?> spec = object(value); String label = text(spec.get("label")); var command = new ArrayList<String>();
            for (String token : NextflowFiles.strings(spec.get("command"))) command.add(token.replace("@HOME@", home).replace("@REPORT@", producer));
            process(report, label, command, environment, limits);
            if (spec.containsKey("output")) {
                Map<?, ?> recorded = object(recordedAudits.get(ordinal++)); NextflowFiles.keys(recorded, "label", "audit"); equal(recorded.get("label"), label, "Salmon health audit order");
                audits.add(health(report, producer, version, label, spec, object(recorded.get("audit")), files, targets, limits, check));
            }
        }
        require(ordinal == 2, "Salmon health specifications are incomplete");
        String warnings = utf8(report, "WARNINGS.md", limits);
        require(warnings.startsWith("# Salmon setup only") && warnings.contains("user analysis not admitted") && warnings.contains("Required action:")
            && warnings.contains("SKIP3") && warnings.contains("FAIL2") && warnings.contains("below original burn-in") && warnings.contains("four-target"), "Prominent Salmon setup limitations/recovery are missing");
        check.run(); Map<String, Node> after = census(report, limits, check);
        if (!before.equals(after)) {
            for (String name : before.keySet()) if (!Objects.equals(before.get(name), after.get(name)))
                throw new IOException("Complete Salmon setup report changed during native verification: " + name + "; before=" + before.get(name) + "; after=" + after.get(name));
            throw new IOException("Complete Salmon setup namespace changed during native verification");
        }
        long bytes = 0; for (Node node : before.values()) if (node.pin() != null) bytes = Math.addExact(bytes, node.pin().bytes());
        return new VerifiedReport(profile, version, home, producer, installed, before.size(), bytes, manifestPin, audits);
    }

    private static Health health(Path root, String producer, String version, String label, Map<?, ?> spec, Map<?, ?> recorded,
                                 Map<?, ?> files, List<SalmonQuantification.Target> targets, Limits limits, Runnable check) throws IOException {
        String folder = NextflowFiles.member(text(spec.get("output"))); Map<?, ?> expected = object(spec.get("expected"));
        Map<?, ?> meta = json(root, folder + "/aux_info/meta_info.json", limits), command = json(root, folder + "/cmd_info.json", limits);
        NextflowFiles.keys(recorded, "status", "targets", "bootstrap_values", "meta_info", "complete_original_result_files", "user_analysis_admitted");
        equal(recorded.get("status"), "passed-original-setup-health-shape-and-totals-only", "Original Salmon health audit");
        equal(recorded.get("user_analysis_admitted"), false, "Original Salmon health admission"); equal(recorded.get("meta_info"), meta, "Original Salmon metadata mirror");
        int bootstraps = (int) NextflowFiles.integer(expected.get("bootstraps"), 1, 1000); integer(recorded.get("targets"), targets.size()); integer(recorded.get("bootstrap_values"), (long) targets.size() * bootstraps);
        Map<String, Object> resultFiles = new TreeMap<>();
        for (var row : files.entrySet()) { String name = text(row.getKey()); if (name.startsWith(folder + "/")) resultFiles.put(name.substring(folder.length() + 1), row.getValue()); }
        equal(recorded.get("complete_original_result_files"), resultFiles, "Complete original Salmon health output population");
        equal(meta.get("salmon_version"), version, "Original Salmon health edition"); equal(meta.get("quant_errors"), List.of(), "Original Salmon quantification errors");
        equal(meta.get("samp_type"), "bootstrap", "Salmon inference kind"); equal(meta.get("opt_type"), expected.get("optimizer"), "Salmon optimizer");
        for (String[] pair : List.of(new String[]{"num_processed", "processed_fragments"}, new String[]{"num_mapped", "mapped_fragments"}, new String[]{"num_valid_targets", "valid_targets"}, new String[]{"num_decoy_targets", "decoy_targets"}, new String[]{"num_bootstraps", "bootstraps"}))
            equal(meta.get(pair[0]), expected.get(pair[1]), "Salmon complete counter " + pair[0]);
        integer(meta.get("num_libraries"), 1); equal(meta.get("mapping_type"), "mapping", "Salmon health input mode");
        equal(meta.get("seq_bias_correct"), true, "Salmon sequence bias"); equal(meta.get("gc_bias_correct"), true, "Salmon GC bias");
        List<String> libraryTypes = NextflowFiles.strings(meta.get("library_types"));
        require(libraryTypes.equals(List.of("ISR")) || label.equals("health-em") && libraryTypes.equals(List.of("IU")), "Unexpected Salmon declared library classification");
        String optimizerFlag = label.equals("health-em") ? "useEM" : "useVBOpt";
        Map<String, Object> expectedCommand = new LinkedHashMap<>();
        expectedCommand.put("salmon_version", version); expectedCommand.put("index", producer + "/health/index"); expectedCommand.put("libType", label.equals("health-em") ? "A" : "ISR");
        expectedCommand.put("mates1", producer + "/inputs/reads_1.fastq"); expectedCommand.put("mates2", producer + "/inputs/reads_2.fastq");
        for (String flag : List.of(optimizerFlag, "seqBias", "gcBias", "posBias")) expectedCommand.put(flag, List.of());
        expectedCommand.put("numBootstraps", Integer.toString(bootstraps)); expectedCommand.put("threads", "2"); expectedCommand.put("output", producer + "/" + folder); expectedCommand.put("auxDir", "aux_info");
        equal(command, expectedCommand, "Complete original Salmon recorded options");
        Map<?, ?> index = json(root, "health/index/info.json", limits), indexVersion = json(root, "health/index/versionInfo.json", limits);
        integer(index.get("k"), 31); integer(index.get("num_decoys"), 1); integer(index.get("first_decoy_index"), 40);
        equal(index.get("sampling_type"), "dense", "Salmon setup index sampling"); equal(index.get("have_ref_seq"), true, "Salmon setup reference retention");
        if (index.containsKey("keep_duplicates")) equal(index.get("keep_duplicates"), true, "Salmon setup duplicate retention");
        integer(indexVersion.get("indexVersion"), 5); integer(indexVersion.get("indexType"), 2); integer(indexVersion.get("auxKmerLength"), 31);
        if (indexVersion.containsKey("salmonVersion")) equal(indexVersion.get("salmonVersion"), version, "Salmon setup index edition");
        var unavailableBindings = new ArrayList<String>();
        for (String[] pair : List.of(new String[]{"SeqHash", "index_seq_hash"}, new String[]{"NameHash", "index_name_hash"}, new String[]{"SeqHash512", "index_seq_hash512"}, new String[]{"NameHash512", "index_name_hash512"}, new String[]{"DecoySeqHash", "index_decoy_seq_hash"}, new String[]{"DecoyNameHash", "index_decoy_name_hash"})) {
            String hash = text(index.get(pair[0])); require(hash.matches(pair[0].endsWith("512") ? "[a-f0-9]{128}" : "[a-f0-9]{64}"), "Invalid Salmon index content hash");
            if (version.equals("1.0.0") && pair[0].endsWith("512")) {
                // Original SalmonIndex.hpp leaves these members empty when loading a mapping index.
                // Preserve the missing link; never populate it from index/info.json.
                equal(meta.get(pair[1]), "", "Original 1.0.0 unavailable SHA-512 metadata binding"); unavailableBindings.add(pair[1]);
            } else equal(meta.get(pair[1]), hash, "Salmon index/quantification content binding");
        }
        Map<?, ?> formats = json(root, folder + "/lib_format_counts.json", limits);
        equal(formats.get("expected_format"), libraryTypes.get(0), "Original expected library format"); integer(formats.get("num_assigned_fragments"), 8520);
        integer(formats.get("ISR"), 8520); // Observed orientation is retained separately from auto's reported IU.
        SalmonQuantification.Table table = SalmonQuantification.read(new StringReader(utf8(root, folder + "/quant.sf", limits)), targets, 3, limits.metadataBytes(), 8192, check);
        SalmonQuantification.Audit arithmetic = SalmonQuantification.audit(table, 8520, check);
        Path samples = root.resolve(folder).resolve("aux_info/bootstrap");
        var replicates = SalmonInferentialReplicates.read(Files.newInputStream(samples.resolve("names.tsv.gz"), LinkOption.NOFOLLOW_LINKS),
            Files.newInputStream(samples.resolve("bootstraps.gz"), LinkOption.NOFOLLOW_LINKS), targets.stream().map(SalmonQuantification.Target::name).toList(), bootstraps, 6000,
            (sample, targetIndex, name, count) -> { }, check);
        return new Health(label, libraryTypes, unavailableBindings, table, arithmetic, replicates);
    }

    private static void process(Path report, String label, List<String> command, Map<String, String> environment, Limits limits) throws IOException {
        var expected = new ArrayList<>(PREFIX); expected.addAll(command);
        Map<?, ?> process = json(report, label + ".process.json", limits), launch = json(report, label + ".launch.json", limits);
        NextflowFiles.keys(process, "command", "environment", "exit_code", "elapsed_seconds", "error", "owned_live_before_cleanup", "forced_cleanup", "owned_live_after_cleanup", "status");
        NextflowFiles.keys(launch, "pid", "start_ticks", "process_group", "command");
        equal(process.get("command"), expected, "Salmon supervised command " + label); equal(launch.get("command"), expected, "Salmon launch command " + label);
        equal(process.get("environment"), environment, "Salmon clean process environment " + label); integer(process.get("exit_code"), 0);
        require(process.get("elapsed_seconds") instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0 && n.doubleValue() <= 360, "Invalid Salmon process timing");
        equal(process.get("error"), null, "Salmon process error"); equal(process.get("status"), "completed-process-only", "Salmon process completion");
        equal(process.get("owned_live_before_cleanup"), List.of(), "Salmon unfinished owned workers"); equal(process.get("owned_live_after_cleanup"), List.of(), "Salmon remaining owned workers"); equal(process.get("forced_cleanup"), false, "Salmon forced cleanup");
        long pid = NextflowFiles.integer(launch.get("pid"), 1, Integer.MAX_VALUE); integer(launch.get("process_group"), pid); NextflowFiles.integer(launch.get("start_ticks"), 1, Long.MAX_VALUE);
        utf8(report, label + ".stdout", limits); utf8(report, label + ".stderr", limits);
    }

    private static void libraries(Path report, Map<?, ?> manifest, Map<?, ?> profile, String home, Limits limits) throws IOException {
        equal(utf8(report, "libraries.stderr", limits), "", "Salmon library probe stderr"); Map<?, ?> declared = object(manifest.get("library_bindings"));
        var paths = new TreeMap<String, String>(); boolean vdso = false;
        for (String raw : utf8(report, "libraries.stdout", limits).strip().split("\n", -1)) {
            String line = raw.strip();
            if (VDSO.matcher(line).matches()) { require(!vdso, "Duplicate Salmon virtual DSO"); vdso = true; continue; }
            var named = NAMED.matcher(line); var loader = LOADER.matcher(line); String name, path;
            if (named.matches()) { name = named.group(1); path = named.group(2); }
            else if (loader.matches()) { name = "@loader"; path = loader.group(1); }
            else throw new IOException("Unresolved complete Salmon library report row");
            require(paths.put(name, path) == null, "Duplicate Salmon library binding");
        }
        require(vdso && paths.containsKey("@loader") && paths.keySet().equals(declared.keySet()), "Complete Salmon library binding population differs");
        Map<?, ?> members = object(profile.get("members"));
        for (var binding : paths.entrySet()) {
            Map<?, ?> row = object(declared.get(binding.getKey())); NextflowFiles.keys(row, "reported_path", "real_path", "mode", "bytes", "sha256");
            equal(row.get("reported_path"), binding.getValue(), "Salmon reported library path"); String real = linux(row.get("real_path"));
            NextflowFiles.Pin pin = NextflowFiles.fingerprint(row, limits.fileBytes()); NextflowFiles.integer(row.get("mode"), 0, 0777);
            if (real.startsWith(home + "/")) {
                Map<?, ?> member = object(members.get(real.substring(home.length() + 1)));
                require(pin.equals(NextflowFiles.fingerprint(member, limits.fileBytes())), "Private Salmon library differs from exact release"); equal(row.get("mode"), member.get("mode"), "Private Salmon library mode");
            }
        }
    }

    private static Map<String, Node> census(Path root, Limits limits, Runnable check) throws IOException {
        var result = new TreeMap<String, Node>(); long[] bytes = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            private void add(Path path, BasicFileAttributes attrs, boolean directory) throws IOException {
                check.run(); if (path.equals(root)) return;
                require(result.size() < limits.nodes(), "Complete Salmon setup node capacity exceeded");
                String name = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"); NextflowFiles.member(name);
                require(!attrs.isSymbolicLink() && (directory || attrs.isRegularFile()) && path.toRealPath().equals(path), "Linked/special Salmon setup member");
                NextflowFiles.Pin pin = directory ? null : NextflowFiles.pin(path, limits.fileBytes(), check);
                if (pin != null) { bytes[0] = Math.addExact(bytes[0], pin.bytes()); require(bytes[0] <= limits.totalBytes(), "Complete Salmon setup byte capacity exceeded"); }
                // Windows can defer directory modification times after extraction. Full directory
                // names/types/modes/keys and every regular-file identity/hash are still compared.
                result.put(name, new Node(directory ? "directory" : "file", pin, NextflowFiles.mode(path), String.valueOf(attrs.fileKey()), directory ? null : attrs.lastModifiedTime().toString()));
            }
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException { add(path, attrs, true); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException { add(path, attrs, false); return FileVisitResult.CONTINUE; }
        });
        return Map.copyOf(result);
    }
    private static void namespace(Map<String, Node> actual, Map<?, ?> profile, boolean installed, boolean download) throws IOException {
        var top = new HashSet<>(Set.of("COMPLETED.json", "manifest.json", "request.json", "health.json", "WARNINGS.md", "health", "inputs"));
        for (String label : List.of("version", "libraries", "health-index", "health-em", "health-vb"))
            for (String suffix : List.of(".launch.json", ".process.json", ".stdout", ".stderr")) top.add(label + suffix);
        Map<?, ?> archive = object(profile.get("archive")); String archiveName = "downloads/" + text(archive.get("filename"));
        for (var entry : actual.entrySet()) {
            String name = entry.getKey();
            if (top.contains(name) || name.startsWith("health/index/") || name.startsWith("health/health-em/") || name.startsWith("health/health-vb/")
                || Set.of("health/index", "health/health-em", "health/health-vb").contains(name)
                || Set.of("inputs/gentrome.fa", "inputs/transcripts.fa", "inputs/decoys.txt", "inputs/reads_1.fastq", "inputs/reads_2.fastq").contains(name)) continue;
            require(installed && download && (name.equals("downloads") || name.equals(archiveName)), "Unexpected Salmon setup member: " + name);
        }
        if (actual.containsKey("downloads")) require(actual.containsKey(archiveName)
            && NextflowFiles.fingerprint(archive, Long.MAX_VALUE).equals(actual.get(archiveName).pin()), "Complete downloaded Salmon archive differs");
    }
    private static void inventory(Map<?, ?> expected, Map<String, Node> actual, Set<String> excluded) throws IOException {
        var names = new HashSet<Object>(expected.keySet()); names.addAll(excluded); require(names.equals(actual.keySet()), "Complete Salmon setup namespace differs");
        for (var entry : expected.entrySet()) {
            String name = NextflowFiles.member(text(entry.getKey())); require(!excluded.contains(name), "Salmon setup manifest is self-referential");
            Map<?, ?> row = object(entry.getValue()); Node node = actual.get(name); equal(row.get("kind"), node.kind(), "Salmon setup member type " + name);
            NextflowFiles.integer(row.get("mode"), 0, 0777); // Recorded Linux modes; copied Windows files do not assert them.
            if (node.kind().equals("directory")) NextflowFiles.keys(row, "kind", "mode");
            else { NextflowFiles.keys(row, "kind", "mode", "bytes", "sha256"); require(node.pin().equals(NextflowFiles.fingerprint(row, Long.MAX_VALUE)), "Complete Salmon setup file differs: " + name); }
        }
    }
    private static Map<?, ?> resource(String name, String sha) throws IOException {
        try (var input = SalmonSetupEvidence.class.getResourceAsStream(RESOURCE + name)) {
            if (input == null) throw new IOException("Missing pinned Salmon setup resource: " + name);
            byte[] raw = input.readNBytes(1000000); require(NextflowFiles.sha(raw).equals(sha), "Pinned Salmon setup resource differs: " + name);
            return object(SimpleJson.parse(NextflowFiles.utf8(raw)));
        }
    }
    private static Map<?, ?> json(Path root, String name, Limits limits) throws IOException { return object(SimpleJson.parse(utf8(root, name, limits))); }
    private static String utf8(Path root, String name, Limits limits) throws IOException { return NextflowFiles.utf8(NextflowFiles.bytes(root.resolve(NextflowFiles.member(name)), limits.metadataBytes())); }
    private static Map<?, ?> object(Object value) throws IOException { return NextflowFiles.object(value); }
    private static String text(Object value) throws IOException { return NextflowFiles.text(value); }
    private static void equal(Object actual, Object expected, String label) throws IOException { require(Objects.equals(actual, expected), label + " differs"); }
    private static void integer(Object value, long expected) throws IOException { require(NextflowFiles.integer(value, Long.MIN_VALUE, Long.MAX_VALUE) == expected, "Exact Salmon integer differs"); }
    private static boolean bool(Object value) throws IOException { if (!(value instanceof Boolean result)) throw new IOException("Expected Salmon Boolean"); return result; }
    private static void noAdmission(Map<?, ?> value) throws IOException { equal(value.get("scientific_runtime_admitted"), false, "Salmon scientific runtime admission"); equal(value.get("user_analysis_admitted"), false, "Salmon user analysis admission"); }
    private static boolean overlap(String a, String b) { return a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/"); }
    private static String linux(Object value) throws IOException {
        String path = text(value); require(path.startsWith("/") && path.length() > 1 && !path.endsWith("/") && !path.contains("//")
            && path.chars().allMatch(c -> c >= 33 && c < 127), "Expected canonical producer Linux path text");
        for (String part : path.substring(1).split("/")) require(!part.equals(".") && !part.equals(".."), "Noncanonical producer Linux path text"); return path;
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
