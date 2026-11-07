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
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.SalmonQuantification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonHealthTest {
    @TempDir Path temporary;
    record Control(Path root, String producer, String version, Map<?, ?> health, List<SalmonQuantification.Target> targets) { }
    static Stream<String> reports() { return Stream.of("fresh-1.9.0", "recheck-1.0.0", "recheck-1.1.0", "recheck-1.5.2", "recheck-1.8.0", "recheck-1.9.0"); }
    @ParameterizedTest @MethodSource("reports") void bothCompleteOriginalFitsPassTheFreshHealthAuditor(String report) throws Exception {
        Control control = fixture(report); long count = 0;
        for (Object item : NextflowFiles.list(control.health().get("commands"))) {
            Map<?, ?> spec = NextflowFiles.object(item); if (!spec.containsKey("output")) continue;
            var result = SalmonHealth.audit(control.root(), control.producer(), control.version(), spec, control.targets(), () -> { });
            assertEquals(40, result.table().rows().size()); assertEquals(8520, result.arithmetic().expectedAssignedFragments());
            assertEquals(control.version().equals("1.0.0") ? 2 : 0, result.unavailableBindings().size()); count += result.replicates().values();
            assertFalse((Boolean) result.document().get("user_analysis_admitted"));
        }
        assertEquals(6000, count);
    }
    @ParameterizedTest @ValueSource(strings={"num_mapped", "num_valid_targets", "num_bootstraps", "opt_type", "quant_errors", "seq_bias_correct", "gc_bias_correct"})
    void completeHealthMetadataCannotHideCounterModelOrBiasChanges(String field) throws Exception {
        Control control = fixture("recheck-1.8.0"); Path path = control.root().resolve("health/health-em/aux_info/meta_info.json"); var map = mutable(SalmonHealth.json(path));
        map.put(field, field.endsWith("correct") ? false : field.equals("opt_type") ? "vb" : field.equals("quant_errors") ? List.of("failure") : 1L); write(path, map);
        assertThrows(IOException.class, () -> auditEm(control));
    }
    @ParameterizedTest @ValueSource(strings={"posBias", "useEM", "libType", "index"})
    void originalHealthOptionsAreBoundCompletely(String field) throws Exception {
        Control control = fixture("recheck-1.8.0"); Path path = control.root().resolve("health/health-em/cmd_info.json"); var map = mutable(SalmonHealth.json(path)); map.remove(field); write(path, map);
        assertThrows(IOException.class, () -> auditEm(control));
    }
    @Test void mismatchedProducerRootsFailEvenWhenCopiedFilesAreComplete() throws Exception {
        Control control = fixture("recheck-1.8.0"); assertThrows(IOException.class, () -> SalmonHealth.audit(control.root(), "/some/other/producer", control.version(), em(control), control.targets(), () -> { }));
    }
    @Test void completeBootstrapStreamIsRequired() throws Exception {
        Control control = fixture("recheck-1.8.0"); Path path = control.root().resolve("health/health-em/aux_info/bootstrap/bootstraps.gz"); byte[] bytes = Files.readAllBytes(path); Files.write(path, java.util.Arrays.copyOf(bytes, bytes.length - 1));
        assertThrows(IOException.class, () -> auditEm(control));
    }
    @Test void missingOriginal100HashLinksAreNotInvented() throws Exception {
        Control control = fixture("recheck-1.0.0"); Path path = control.root().resolve("health/health-em/aux_info/meta_info.json"); var map = mutable(SalmonHealth.json(path)); map.put("index_seq_hash512", "0".repeat(128)); write(path, map);
        assertThrows(IOException.class, () -> auditEm(control));
    }
    @Test void cancellationCannotYieldAHealthReceipt() throws Exception {
        Control control = fixture("recheck-1.8.0"); assertThrows(CancellationException.class, () -> SalmonHealth.audit(control.root(), control.producer(), control.version(), em(control), control.targets(), () -> { throw new CancellationException(); }));
    }
    @Test void nativeRuntimeUnavailableBoundaryNeverCreatesAnAnalysis() throws Exception {
        Control control = fixture("recheck-1.8.0"); Path work = temporary.resolve("native-admission").toAbsolutePath(); Path absent = Path.of("/var/tmp/absent-omicsmatic-salmon-" + java.util.UUID.randomUUID());
        var error = assertThrows(SalmonRuntime.Unavailable.class, () -> SalmonRuntime.admit("linux-x86_64-salmon-1.8.0", absent, control.root(), work, SalmonRuntime.Limits.defaults(), () -> { }));
        assertNotNull(error.getMessage());
        if (System.getProperty("os.name").equals("Linux")) { assertTrue(Files.readString(work.resolve("WARNINGS.md")).contains("SKIP3")); assertFalse(Files.exists(work.resolve("COMPLETED.json"))); }
        else assertFalse(Files.exists(work));
        assertFalse(Files.exists(absent));
    }
    @Test void nativeRuntimeRequiresBoundedImageCapacity() {
        var defaults = SalmonRuntime.Limits.defaults(); assertThrows(IllegalArgumentException.class, () -> new SalmonRuntime.Limits(defaults.host(), defaults.process(), defaults.setup(), 0));
    }
    private SalmonHealth.Receipt auditEm(Control control) throws IOException { return SalmonHealth.audit(control.root(), control.producer(), control.version(), em(control), control.targets(), () -> { }); }
    private static Map<?, ?> em(Control control) throws IOException { return NextflowFiles.object(NextflowFiles.list(control.health().get("commands")).get(1)); }
    private static LinkedHashMap<String, Object> mutable(Map<?, ?> source) { var result = new LinkedHashMap<String, Object>(); source.forEach((k, v) -> result.put((String) k, v)); return result; }
    private static void write(Path path, Object data) throws IOException { Files.writeString(path, NextflowJson.json(data) + "\n"); }
    private Control fixture(String name) throws Exception {
        Path root = temporary.resolve(name); Files.createDirectory(root); byte[] raw;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/cli/salmon-setup/original-setup-reports.zip")) { assertNotNull(input); raw = input.readAllBytes(); }
        assertEquals("1d517d227fa2f435471997f206f3dc73c23f6259a3804eb7cc8187782686ca92", NextflowFiles.sha(raw));
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.getName().startsWith(name + "/")) continue; String member = entry.getName().substring(name.length() + 1);
                if (entry.isDirectory()) member = member.substring(0, member.length() - 1); NextflowFiles.member(member); Path path = root.resolve(member);
                if (entry.isDirectory()) Files.createDirectories(path); else { Files.createDirectories(path.getParent()); Files.copy(zip, path); }
            }
        }
        Map<?, ?> manifest = SalmonHealth.json(root.resolve("manifest.json")); Map<?, ?> environment = NextflowFiles.object(SalmonHealth.json(root.resolve("version.process.json")).get("environment")); Map<?, ?> health;
        try (var input = getClass().getResourceAsStream(SalmonRuntime.RESOURCE + "health-lock.json")) { assertNotNull(input); health = NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(input.readAllBytes()))); }
        var targets = new ArrayList<SalmonQuantification.Target>();
        for (Object value : NextflowFiles.list(health.get("targets"))) { Map<?, ?> row = NextflowFiles.object(value); targets.add(new SalmonQuantification.Target((String) row.get("name"), ((Number) row.get("length")).longValue())); }
        return new Control(root.toRealPath(), (String) environment.get("HOME"), (String) manifest.get("version"), health, List.copyOf(targets));
    }
}
