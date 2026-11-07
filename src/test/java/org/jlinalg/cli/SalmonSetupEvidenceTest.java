/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonSetupEvidenceTest {
    @TempDir Path temporary;
    private static final String RESOURCE = "/org/jlinalg/cli/salmon-setup/original-setup-reports.zip";
    private static final String FIXTURE_SHA = "1d517d227fa2f435471997f206f3dc73c23f6259a3804eb7cc8187782686ca92";
    private static final String EM = "health/health-em/";
    static Stream<String> reports() { return Stream.of("fresh-1.9.0", "recheck-1.0.0", "recheck-1.1.0", "recheck-1.5.2", "recheck-1.8.0", "recheck-1.9.0"); }

    @ParameterizedTest @MethodSource("reports")
    void completeOriginalReportsReplayWithoutOpeningProducerPaths(String name) throws Exception {
        Path report = fixture(name); var result = read(report);
        assertEquals(name.substring(name.lastIndexOf('-') + 1), result.version());
        assertEquals("linux-x86_64-salmon-" + result.version(), result.profile());
        assertEquals(name.startsWith("fresh"), result.installed());
        assertTrue(result.producerHome().startsWith("/var/tmp/omicsmatic-salmon-managed"));
        assertTrue(result.producerReport().startsWith("/mnt/d/projects/JLinAlg/"));
        assertFalse(result.scientificRuntimeAdmitted()); assertFalse(result.userAnalysisAdmitted());
        assertEquals(2, result.health().size()); assertEquals(80, result.health().stream().mapToInt(r -> r.table().rows().size()).sum());
        assertEquals(6000, result.health().stream().mapToLong(r -> r.replicates().values()).sum());
        assertEquals(List.of("IU"), result.health().get(0).reportedLibraryTypes());
        assertEquals(List.of("ISR"), result.health().get(1).reportedLibraryTypes());
        for (var health : result.health()) assertEquals(result.version().equals("1.0.0") ? List.of("index_seq_hash512", "index_name_hash512") : List.of(), health.unavailableMetadataBindings());
        assertTrue(result.bytes() > 4900000); assertTrue(result.nodes() >= 104);
        assertThrows(UnsupportedOperationException.class, () -> result.health().clear());
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETED.json", "manifest.json", "health/health-em/quant.sf", "health/health-vb/aux_info/bootstrap/bootstraps.gz"})
    void missingCompleteMembersNeverAdmitAPrefix(String member) throws Exception {
        Path report = fixture(); Files.delete(report.resolve(member)); assertThrows(IOException.class, () -> read(report));
    }
    @ParameterizedTest @ValueSource(strings={"file", "directory", "failure"})
    void extraFilesEmptyDirectoriesAndFailureMarkersAreRejected(String kind) throws Exception {
        Path report = fixture(); Path extra = report.resolve(kind.equals("failure") ? "FAILURE.json" : "unexpected");
        if (kind.equals("directory")) Files.createDirectory(extra); else Files.writeString(extra, "retained");
        assertThrows(IOException.class, () -> read(report));
        reseal(report); // Even a self-consistent failed report cannot masquerade as completed setup.
        assertThrows(IOException.class, () -> read(report));
    }
    @ParameterizedTest @ValueSource(strings={"command", "environment", "exit_code", "owned_live_before_cleanup", "owned_live_after_cleanup", "forced_cleanup"})
    void rehashedProcessContradictionsAreRejected(String field) throws Exception {
        Path report = fixture(); edit(report, "health-em.process.json", map -> {
            switch (field) {
                case "command" -> { var tokens = new ArrayList<Object>((List<?>) map.get(field)); tokens.set(tokens.size() - 1, "some-other-output"); map.put(field, tokens); }
                case "environment" -> { var environment = mutable(map.get(field)); environment.put("LD_PRELOAD", "/foreign/library.so"); map.put(field, environment); }
                case "exit_code" -> map.put(field, -11L);
                case "forced_cleanup" -> map.put(field, true);
                default -> map.put(field, List.of(123L));
            }
        }); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @ParameterizedTest @ValueSource(strings={"pid", "start_ticks", "process_group"})
    void invalidLaunchIdentitiesAreRejected(String field) throws Exception {
        Path report = fixture(); edit(report, "health-vb.launch.json", map -> map.put(field, 0L)); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @ParameterizedTest @ValueSource(strings={"profile", "version", "lock_sha256", "scientific_runtime_admitted", "user_analysis_admitted"})
    void rehashedUnknownEditionsAndAdmissionClaimsAreRejected(String field) throws Exception {
        Path report = fixture(); edit(report, "manifest.json", map -> map.put(field, field.endsWith("admitted") ? true : "unknown"));
        reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void unsupportedInstallerCannotSupplyASetupReceipt() throws Exception {
        Path report = fixture(); edit(report, "manifest.json", map -> map.put("installer", Map.of("bytes", 23402L, "sha256", "0".repeat(64)))); reseal(report);
        assertThrows(IOException.class, () -> read(report));
    }
    @Test void installationRequiresItsRecordedExplicitFlag() throws Exception {
        Path report = fixture("fresh-1.9.0"); edit(report, "request.json", map -> map.put("allow_install", false)); reseal(report);
        assertThrows(IOException.class, () -> read(report));
    }
    @ParameterizedTest @ValueSource(strings={"num_processed", "num_mapped", "num_bootstraps", "opt_type", "quant_errors"})
    void changedOriginalHealthCountersAndModelsAreRejectedAfterRehash(String field) throws Exception {
        Path report = fixture(); edit(report, EM + "aux_info/meta_info.json", map -> map.put(field, field.equals("opt_type") ? "vb" : field.equals("quant_errors") ? List.of("failure") : 1L));
        reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void requestedBiasCannotBeSilentlyDisabled() throws Exception {
        Path report = fixture(); edit(report, EM + "cmd_info.json", map -> map.remove("posBias")); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void allTargetTpmArithmeticIsCheckedAfterRehash() throws Exception {
        Path report = fixture(); Path table = report.resolve(EM + "quant.sf"); String raw = Files.readString(table);
        String[] lines = raw.split("\n"); String[] cells = lines[1].split("\t"); cells[3] = "999999.000000"; lines[1] = String.join("\t", cells);
        Files.writeString(table, String.join("\n", lines) + "\n"); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void aRehashedBootstrapPrefixStillFailsCompleteNativeDecoding() throws Exception {
        Path report = fixture(); Path samples = report.resolve(EM + "aux_info/bootstrap/bootstraps.gz"); byte[] bytes = Files.readAllBytes(samples);
        Files.write(samples, java.util.Arrays.copyOf(bytes, bytes.length - 1)); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void aRehashedHealthInputCannotReplaceTheIndependentResourcePin() throws Exception {
        Path report = fixture(); Path input = report.resolve("inputs/reads_1.fastq"); byte[] bytes = Files.readAllBytes(input); bytes[0] ^= 1;
        Files.write(input, bytes); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void privateLibrariesMustMatchTheExactUpstreamPayload() throws Exception {
        Path report = fixture(); edit(report, "manifest.json", map -> {
            var bindings = mutable(map.get("library_bindings")); var row = mutable(bindings.get("libz.so.1")); row.put("sha256", "0".repeat(64)); bindings.put("libz.so.1", row); map.put("library_bindings", bindings);
        }); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void libraryNamesCannotBeOmittedFromTheRawProbe() throws Exception {
        Path report = fixture(); Path stdout = report.resolve("libraries.stdout"); Files.writeString(stdout, Files.readString(stdout).lines().filter(line -> !line.contains("libz.so.1")).reduce("", (a, b) -> a + b + "\n"));
        reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void indexHashesMustAgreeWithBothOriginalQuantifications() throws Exception {
        Path report = fixture(); edit(report, "health/index/info.json", map -> map.put("SeqHash", "0".repeat(64))); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void unknownIndexKmerLengthCannotPassSetupReplay() throws Exception {
        Path report = fixture(); edit(report, "health/index/info.json", map -> map.put("k", 19L)); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void unavailableOriginal100HashLinksCannotBeInventedFromTheIndex() throws Exception {
        Path report = fixture("recheck-1.0.0"); Object hash = json(report.resolve("health/index/info.json")).get("SeqHash512");
        edit(report, EM + "aux_info/meta_info.json", map -> map.put("index_seq_hash512", hash)); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void recordedLibraryClassificationIsNotReplacedByObservedOrientation() throws Exception {
        Path report = fixture(); edit(report, EM + "aux_info/meta_info.json", map -> map.put("library_types", List.of("ISR"))); reseal(report);
        assertThrows(IOException.class, () -> read(report)); // Original expected_format is still IU; observed ISR is a different field.
    }
    @Test void missingProminentRequiredActionsCannotPassAfterRehash() throws Exception {
        Path report = fixture(); Files.writeString(report.resolve("WARNINGS.md"), "# all fine\n"); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @ParameterizedTest @ValueSource(strings={"nodes", "total", "file", "metadata"})
    void explicitCapacityFailureNeverReturnsSuccessfulPrefix(String dimension) throws Exception {
        Path report = fixture(); var limit = new SalmonSetupEvidence.Limits(dimension.equals("nodes") ? 3 : 4096, dimension.equals("total") ? 1000 : 2147483648L,
            dimension.equals("file") ? 1000 : 1073741824, dimension.equals("metadata") ? 30 : 8388608);
        assertThrows(IOException.class, () -> SalmonSetupEvidence.read(report, limit, () -> { }));
    }
    @Test void cancellationPropagatesWithoutAnAdmissionReceipt() throws Exception {
        Path report = fixture(); assertThrows(CancellationException.class, () -> SalmonSetupEvidence.read(report, SalmonSetupEvidence.Limits.defaults(), () -> { throw new CancellationException("owned cancellation"); }));
    }
    @Test void duplicateJsonKeysAreRejectedEvenWithAConsistentFileCensus() throws Exception {
        Path report = fixture(); Path process = report.resolve("version.process.json"); String raw = Files.readString(process);
        Files.writeString(process, raw.replaceFirst("\\{", "{\"exit_code\":0,")); reseal(report); assertThrows(IOException.class, () -> read(report));
    }
    @Test void noncanonicalProducerPathsAreNeverInterpretedOnTheVerifierHost() throws Exception {
        Path report = fixture(); edit(report, "manifest.json", map -> map.put("home", "/var/tmp/../foreign")); reseal(report); assertThrows(IOException.class, () -> read(report));
    }

    private SalmonSetupEvidence.VerifiedReport read(Path report) throws IOException { return SalmonSetupEvidence.read(report, SalmonSetupEvidence.Limits.defaults(), () -> { }); }
    private Path fixture() throws Exception { return fixture("recheck-1.8.0"); }
    private Path fixture(String name) throws Exception {
        byte[] raw; try (var input = getClass().getResourceAsStream(RESOURCE)) { assertNotNull(input); raw = input.readAllBytes(); }
        assertEquals(FIXTURE_SHA, NextflowFiles.sha(raw)); Path report = temporary.resolve(name).toAbsolutePath().normalize(); Files.createDirectory(report);
        try (var input = new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (!entry.getName().startsWith(name + "/")) continue;
                String relative = entry.getName().substring(name.length() + 1); if (entry.isDirectory()) relative = relative.substring(0, relative.length() - 1);
                NextflowFiles.member(relative); Path target = report.resolve(relative); assertTrue(target.normalize().startsWith(report));
                if (entry.isDirectory()) Files.createDirectories(target); else { Files.createDirectories(target.getParent()); Files.copy(input, target); }
            }
        }
        return report.toRealPath();
    }
    private static Map<String, Object> mutable(Object value) { assertInstanceOf(Map.class, value); var copy = new LinkedHashMap<String, Object>(); ((Map<?, ?>) value).forEach((k, v) -> copy.put((String) k, v)); return copy; }
    private static Map<String, Object> json(Path path) throws IOException { return mutable(SimpleJson.parse(Files.readString(path))); }
    private static void write(Path path, Object value) throws IOException { Files.writeString(path, NextflowJson.json(value) + "\n"); }
    private static void edit(Path root, String name, Consumer<Map<String, Object>> edit) throws IOException { Path path = root.resolve(name); var value = json(path); edit.accept(value); write(path, value); }
    private static Map<String, Object> pin(Path path) throws IOException { byte[] raw = Files.readAllBytes(path); return Map.of("bytes", (long) raw.length, "sha256", NextflowFiles.sha(raw)); }
    private static Map<String, Object> inventory(Path report) throws IOException {
        var result = new LinkedHashMap<String, Object>();
        try (var paths = Files.walk(report)) { for (Path path : paths.sorted().toList()) {
            if (path.equals(report)) continue; String name = report.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
            if (name.equals("manifest.json") || name.equals("COMPLETED.json")) continue;
            var row = new LinkedHashMap<String, Object>(); row.put("kind", Files.isDirectory(path) ? "directory" : "file"); row.put("mode", 0755L);
            if (!Files.isDirectory(path)) row.putAll(pin(path)); result.put(name, row);
        } } return result;
    }
    private static void reseal(Path report) throws IOException {
        var files = inventory(report); var health = json(report.resolve("health.json")); var audits = new ArrayList<Object>();
        for (Object original : (List<?>) health.get("audits")) {
            var row = mutable(original); var audit = mutable(row.get("audit")); String folder = "health/" + row.get("label") + "/";
            audit.put("meta_info", json(report.resolve(folder + "aux_info/meta_info.json"))); var descendants = new LinkedHashMap<String, Object>();
            files.forEach((name, value) -> { if (name.startsWith(folder)) descendants.put(name.substring(folder.length()), value); });
            audit.put("complete_original_result_files", descendants); row.put("audit", audit); audits.add(row);
        }
        health.put("audits", audits); write(report.resolve("health.json"), health); files = inventory(report);
        var manifest = json(report.resolve("manifest.json")); manifest.put("files", files); write(report.resolve("manifest.json"), manifest);
        var completed = json(report.resolve("COMPLETED.json")); completed.put("profile", manifest.get("profile")); completed.put("manifest", pin(report.resolve("manifest.json"))); write(report.resolve("COMPLETED.json"), completed);
    }
}
