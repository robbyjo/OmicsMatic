/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.genetics.EagleGenotypes;

/** Managed original runtime admission with independent native scientific replay. */
final class EagleRuntime {
    private EagleRuntime() { }
    static final String MARKER = ".omicsmatic-eagle.json";
    private static final String HEALTH = "passed-original-cohort-reference-keep-impute-health";
    private record Lock(Map<?, ?> value, String sha256, String installerSha256) { }
    private static final Lock LOCK = load();

    /** Only dependency unavailability maps to SKIP3; integrity failures remain IO failures. */
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }
    record Fingerprint(long bytes, String sha256) { }
    record External(String realPath, Fingerprint fingerprint) { }
    record Context(Path home, String profile, String version, String markerSha256,
            Map<String, Fingerprint> files, Map<String, External> external) {
        Context { files = Map.copyOf(files); external = Map.copyOf(external); }
        void verify() throws IOException {
            try {
                if (!equals(open(home, profile))) throw new IOException("Eagle runtime admission changed during analysis");
            } catch (Unavailable changed) {
                throw new IOException("Admitted Eagle runtime became unavailable during analysis; no result is admitted", changed);
            }
        }
    }

    private static Lock load() {
        try {
            byte[] raw = resource("/org/jlinalg/omics/eagle-runtime-lock.json", 1048576);
            byte[] installer = resource("/org/jlinalg/omics/eagle-runtime-setup/install_eagle.py", 1048576);
            return new Lock(object(SimpleJson.parse(utf8(raw))), digest(raw), digest(installer));
        } catch (IOException | IllegalArgumentException invalid) { throw new ExceptionInInitializerError(invalid); }
    }

    static Context open(Path supplied, String profile) throws IOException {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(arch))
            throw new Unavailable("Original Eagle needs Linux x86_64 Java; on Windows use Linux Java inside WSL");
        Map<?, ?> profiles = object(LOCK.value.get("profiles"));
        if (!profiles.containsKey(profile)) throw new Unavailable("Requested Eagle profile is unsupported; no historical edition is substituted: " + profile);
        Path home = supplied.toAbsolutePath().normalize();
        if (!Files.exists(home, LinkOption.NOFOLLOW_LINKS)) throw new Unavailable("Missing private Eagle runtime home");
        requirePrivate(home);
        if (!Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Private Eagle home is not a directory");
        Path marker = home.resolve(MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) throw new Unavailable("Unmanaged Eagle home; preserve it and choose a new private home");
        try { return admit(home, profile, bounded(marker, 1048576)); }
        catch (AccessDeniedException forbidden) { throw new Unavailable("Runtime access is forbidden; skip dependent analysis", forbidden); }
        catch (IllegalArgumentException | IllegalStateException malformed) { throw new IOException("Invalid Eagle admission metadata", malformed); }
    }

    private static Context admit(Path home, String profile, byte[] raw) throws IOException {
        Map<?, ?> value = object(SimpleJson.parse(utf8(raw)));
        Map<?, ?> pin = object(object(LOCK.value.get("profiles")).get(profile));
        whole(value, "schema_version", 1, 1);
        for (var field : Map.of("tool", "Eagle", "profile", profile, "version", text(pin, "version"),
                "lock_sha256", LOCK.sha256, "installer_sha256", LOCK.installerSha256).entrySet())
            if (!field.getValue().equals(value.get(field.getKey()))) throw new IOException("Eagle profile/version/lock/installer differs: " + field.getKey());
        Map<?, ?> host = object(value.get("host"));
        if (!"Linux".equals(host.get("os")) || !"x86_64".equals(host.get("architecture"))
                || !System.getProperty("os.version").equals(host.get("kernel")))
            throw new Unavailable("Eagle admitted host/kernel differs; admit a new private home on this host");
        var expected = new TreeMap<String, Fingerprint>();
        add(expected, "", object(pin.get("members")), 100000000);
        add(expected, "health/input/", object(LOCK.value.get("health_fixtures")), 16777216);
        add(expected, "share/", object(LOCK.value.get("bundled_notices")), 1048576);
        Map<?, ?> health = object(value.get("health")), contracts = object(health.get("contracts"));
        Map<?, ?> lockedContracts = object(LOCK.value.get("health_contracts"));
        if (!HEALTH.equals(health.get("status")) || !LOCK.value.get("scope").equals(health.get("scope"))
                || !contracts.keySet().equals(lockedContracts.keySet())) throw new IOException("Actual four-run scientific health admission is missing");
        for (var entry : lockedContracts.entrySet()) {
            String label = entry.getKey().toString();
            if (!label.matches("(?:cohort|reference)-(?:keep|impute)")) throw new IOException("Unknown locked health role");
            Map<?, ?> record = object(contracts.get(label)), process = object(record.get("process"));
            if (!"completed".equals(process.get("status")) || !List.of().equals(process.get("owned_live_after_cleanup"))
                    || !LOCK.value.get("health_limits").equals(process.get("limits"))) throw new IOException("Scientific health process did not complete under the admitted limits");
            whole(process, "exit_code", 0, 0);
            expected.put("health/output/" + label + ".vcf", fingerprint(object(record.get("output")), 16777216));
        }
        if (!object(value.get("files")).keySet().equals(expected.keySet())) throw new IOException("Managed Eagle file inventory differs");
        Map<?, ?> recorded = object(value.get("files"));
        for (var entry : expected.entrySet()) {
            if (!entry.getValue().equals(fingerprint(object(recorded.get(entry.getKey())), 100000000))) throw new IOException("Managed Eagle member fingerprint differs: " + entry.getKey());
            verifyFile(home.resolve(entry.getKey()), entry.getValue(), true);
        }
        inventory(home, expected.keySet());
        if (!Files.isExecutable(home.resolve("bin/eagle"))) throw new Unavailable("Original Eagle execution is forbidden");
        Map<String, External> external = closure(home, object(value.get("closure")));
        for (var entry : lockedContracts.entrySet()) {
            String label = entry.getKey().toString();
            Map<?, ?> contract = object(entry.getValue()), record = object(contracts.get(label));
            health(home, contract, home.resolve("health/output/" + label + ".vcf"));
            compareAudit(object(record.get("audit")), contract);
        }
        // Bracket independent scientific replay with complete immutable-file hashes.
        for (var entry : expected.entrySet()) verifyFile(home.resolve(entry.getKey()), entry.getValue(), true);
        inventory(home, expected.keySet());
        if (!external.equals(closure(home, object(value.get("closure"))))) throw new IOException("Eagle external closure changed during admission");
        if (!digest(bounded(home.resolve(MARKER), 1048576)).equals(digest(raw))) throw new IOException("Eagle marker changed during admission");
        return new Context(home, profile, text(pin, "version"), digest(raw), expected, external);
    }

    static String health(Path home, Map<?, ?> contract, Path output) throws IOException {
        String mode = text(contract, "mode"), missing = text(contract, "missing");
        EagleGenotypes.Mode typedMode = switch (mode) {
            case "cohort" -> EagleGenotypes.Mode.COHORT;
            case "reference" -> EagleGenotypes.Mode.REFERENCE;
            default -> throw new IOException("Unknown Eagle health mode");
        };
        EagleGenotypes.Missing typedMissing = switch (missing) {
            case "keep" -> EagleGenotypes.Missing.KEEP;
            case "impute" -> EagleGenotypes.Missing.IMPUTE;
            default -> throw new IOException("Unknown Eagle health missing-call policy");
        };
        var limits = new EagleVcf.Limits(new MinimacVcf.Limits(16, 128, 2048, 16777216, 8388608,
                16384, 16777216, 128), 128, 1048576, 32, 2, 16384);
        Path source = home.resolve(mode.equals("cohort") ? "health/input/cohort-target.vcf" : "health/input/target.vcf.gz");
        MessageDigest hash = hasher(); frame(hash, "Eagle-health-contract-v1");
        int samples;
        try (var reader = EagleVcf.open(source, limits, () -> { })) {
            samples = reader.samples().size();
            for (String name : reader.samples()) frame(hash, name);
        }
        long[] missingCalls = {0};
        var result = EagleVcfAudit.compare(source, output, new EagleGenotypes.Options(typedMode, false, typedMissing, false), limits,
                row -> new EagleVcfAudit.Selection(EagleVcfAudit.Action.PHASED, "fixed diploid synthetic runtime health"),
                (before, after, selection) -> {
                    for (String part : List.of(before.contig(), Integer.toString(before.position()), before.id(), before.reference(), before.alternate())) frame(hash, part);
                    for (int i = 0; i < before.calls().size(); i++) {
                        var original = before.calls().get(i); var actual = after.calls().get(i);
                        if (original.missing()) missingCalls[0]++;
                        String token = original.missing() ? typedMissing == EagleGenotypes.Missing.KEEP ? "M:" + actual.raw() : "IMPUTED:2"
                                : "2:" + actual.alleles().stream().mapToInt(Integer::intValue).sum();
                        frame(hash, token);
                    }
                }, () -> { });
        String digest = HexFormat.of().formatHex(hash.digest());
        if (whole(contract, "samples", 1, 16) != samples || whole(contract, "variants", 1, 128) != result.input().variants()
                || whole(contract, "genotype_cells", 1, 2048) != result.input().calls()
                || whole(contract, "source_missing_cells", 1, 2048) != missingCalls[0]
                || !digest.equals(contract.get("normalized_sha256"))) throw new IOException("Independent native complete Eagle scientific health contract differs");
        return digest;
    }

    private static void compareAudit(Map<?, ?> actual, Map<?, ?> expected) throws IOException {
        if (!actual.keySet().equals(expected.keySet())) throw new IOException("Scientific health audit field inventory differs");
        for (String key : List.of("mode", "missing", "normalized_sha256"))
            if (!text(actual, key).equals(text(expected, key))) throw new IOException("Scientific health audit differs: " + key);
        for (String key : List.of("samples", "variants", "genotype_cells", "source_missing_cells")) {
            long required = whole(expected, key, 1, 2048); whole(actual, key, required, required);
        }
    }

    private static Map<String, External> closure(Path home, Map<?, ?> value) throws IOException {
        Map<?, ?> lock = object(LOCK.value.get("closure")), binaries = object(value.get("binaries"));
        Map<?, ?> requiredBinaries = object(lock.get("binaries")), files = object(value.get("external_files"));
        if (!binaries.keySet().equals(requiredBinaries.keySet()) || files.size() > 32) throw new IOException("Eagle external closure inventory differs");
        var requiredFiles = new HashSet<String>();
        for (Object helper : list(lock.get("helpers"))) requiredFiles.add(helper.toString());
        for (var entry : requiredBinaries.entrySet()) {
            Map<?, ?> bindings = object(binaries.get(entry.getKey()));
            if (!bindings.keySet().equals(new HashSet<>(list(entry.getValue())))) throw new IOException("Eagle external library bindings differ");
            for (var binding : bindings.entrySet()) {
                String name = binding.getKey().toString(), text = binding.getValue().toString(); Path path = Path.of(text);
                if (!path.isAbsolute() || !path.getFileName().toString().equals(name)) throw new IOException("Invalid external Eagle binding path");
                if (name.equals("libgomp.so.1")) {
                    if (!path.equals(home.resolve("lib/libgomp.so.1"))) throw new IOException("Eagle private OpenMP binding differs");
                } else requiredFiles.add(text);
            }
        }
        if (!files.keySet().equals(requiredFiles)) throw new IOException("Eagle external file inventory differs");
        var result = new TreeMap<String, External>();
        for (String name : requiredFiles) {
            Map<?, ?> expected = object(files.get(name)); Path path = Path.of(name);
            Fingerprint fingerprint = fingerprint(expected, 100000000);
            Path real = path.toRealPath();
            if (!real.toString().equals(text(expected, "real_path"))) throw new IOException("Eagle external library/tool target changed: " + name);
            verifyFile(real, fingerprint, false);
            result.put(name, new External(real.toString(), fingerprint));
        }
        for (Object helper : list(lock.get("helpers")))
            if (!Files.isExecutable(Path.of(helper.toString()))) throw new Unavailable("Runtime helper execution is forbidden: " + helper);
        return result;
    }

    private static void inventory(Path home, Set<String> files) throws IOException {
        var allowed = new HashSet<String>(); allowed.addAll(files); allowed.add(MARKER);
        var directories = new HashSet<String>(); directories.add("");
        for (String name : files) for (Path parent = Path.of(name).getParent(); parent != null; parent = parent.getParent()) directories.add(parent.toString().replace('\\', '/'));
        var seen = new HashSet<String>(); int[] count = {0};
        Files.walkFileTree(home, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                if (++count[0] > 128 || !directories.contains(home.relativize(path).toString().replace('\\', '/'))) throw new IOException("Unmanaged Eagle runtime directory");
                requirePrivate(path); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                String relative = home.relativize(path).toString().replace('\\', '/');
                if (++count[0] > 128 || !attributes.isRegularFile() || !allowed.contains(relative)) throw new IOException("Unmanaged/linked Eagle runtime member");
                requirePrivate(path); seen.add(relative); return FileVisitResult.CONTINUE;
            }
        });
        if (!seen.equals(allowed)) throw new IOException("Incomplete managed Eagle runtime inventory");
    }

    private static void add(Map<String, Fingerprint> target, String prefix, Map<?, ?> values, long maximum) throws IOException {
        for (var entry : values.entrySet()) {
            String name = prefix + entry.getKey();
            if (!name.matches("(?:bin|lib|share|health/input)/[A-Za-z0-9._-]+")) throw new IOException("Unsafe locked runtime member");
            target.put(name, fingerprint(object(entry.getValue()), maximum));
        }
    }
    private static Fingerprint fingerprint(Map<?, ?> value, long maximum) throws IOException {
        String hash = text(value, "sha256");
        if (!hash.matches("[0-9a-f]{64}")) throw new IOException("Invalid runtime SHA-256");
        return new Fingerprint(whole(value, "bytes", 1, maximum), hash);
    }
    private static void verifyFile(Path path, Fingerprint expected, boolean privatePath) throws IOException {
        if (privatePath) requirePrivate(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != expected.bytes
                || !FollowupSupport.hash(path).equals(expected.sha256)) throw new IOException("Missing or changed Eagle runtime member: " + path);
    }
    private static void requirePrivate(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().equals(path) || !path.toRealPath().equals(path)) throw new IOException("Linked/unnormalized Eagle runtime path: " + path);
    }
    private static Map<?, ?> object(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> result)) throw new IOException("Expected runtime admission JSON object"); return result;
    }
    private static List<?> list(Object value) throws IOException {
        if (!(value instanceof List<?> result)) throw new IOException("Expected runtime admission JSON list"); return result;
    }
    private static String text(Map<?, ?> value, String key) throws IOException {
        if (!(value.get(key) instanceof String result) || result.isBlank()) throw new IOException("Missing runtime admission text: " + key); return result;
    }
    private static long whole(Map<?, ?> value, String key, long minimum, long maximum) throws IOException {
        Object raw = value.get(key);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.longValue()
                || number.longValue() < minimum || number.longValue() > maximum) throw new IOException("Invalid runtime exact integer: " + key);
        return number.longValue();
    }
    private static byte[] bounded(Path path, int maximum) throws IOException {
        requirePrivate(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum) throw new IOException("Missing/oversized Eagle marker");
        try (var input = Files.newInputStream(path)) {
            byte[] raw = input.readNBytes(maximum + 1);
            if (raw.length > maximum) throw new IOException("Eagle marker exceeds byte bound"); return raw;
        }
    }
    private static byte[] resource(String name, int maximum) throws IOException {
        try (var input = EagleRuntime.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing Eagle bundled resource: " + name);
            byte[] raw = input.readNBytes(maximum + 1);
            if (raw.length > maximum) throw new IOException("Oversized Eagle bundled resource: " + name); return raw;
        }
    }
    private static String utf8(byte[] raw) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
    }
    private static MessageDigest hasher() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String digest(byte[] raw) { return HexFormat.of().formatHex(hasher().digest(raw)); }
    private static void frame(MessageDigest hash, String text) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8); hash.update(ByteBuffer.allocate(4).putInt(raw.length).array()); hash.update(raw);
    }
}
