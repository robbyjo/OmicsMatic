package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RsemToolsCliTest {
    @TempDir Path temporary;
    record Answer(int code, String output, String error) { }
    private Answer run(String... args) throws Exception { var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream(); int code = RsemToolsCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8)); return new Answer(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8)); }
    private Path recipe(Path build) throws Exception {
        Path p = temporary.resolve("fit.json"); Files.write(p, RsemJson.bytes(Map.ofEntries(Map.entry("schema_version", 1), Map.entry("source_version", "1.3.3"), Map.entry("build_home", build.toString()), Map.entry("build_receipt_sha256", "a".repeat(64)), Map.entry("library_directories", List.of()), Map.entry("reference_prefix", "reference/sample"), Map.entry("alignment", "input.sam"), Map.entry("read_type", 3), Map.entry("read_type_tag", ""), Map.entry("settings", Map.of("seed", 1618, "worker_threads", 1, "strand", "NONE", "posterior", "NONE", "estimate_read_start", false, "append_names", false))))); return p;
    }
    private Path compiler(Path root) throws Exception { Path p = temporary.resolve("compiler.json"); Files.write(p, RsemJson.bytes(Map.of("cc", root.resolve("bin/gcc"), "cxx", root.resolve("bin/g++"), "make", root.resolve("bin/make"), "ar", root.resolve("bin/ar"), "ranlib", root.resolve("bin/ranlib"), "roots", List.of(root), "includes", List.of(), "libraries", List.of()))); return p; }
    @Test void helpIsRoutedFromThePublicMainCommand() throws Exception {
        var output = new ByteArrayOutputStream(); int code = JLinAlgCli.run(new String[]{"rsem-tools", "--help"}, new PrintStream(output), new PrintStream(new ByteArrayOutputStream())); assertEquals(0, code); assertTrue(output.toString(StandardCharsets.UTF_8).contains("--method setup")); assertTrue(output.toString(StandardCharsets.UTF_8).contains("3=skipped"));
    }
    @Test void limitsAreCompleteMachineReadableAndHaveNoRuntimeRequirement() throws Exception { var result = run("--method", "limits"); assertEquals(0, result.code(), result.error()); var json = RepeatBundle.object(result.output().getBytes(StandardCharsets.UTF_8)); assertEquals(java.util.Set.of("inputs", "audit", "fit"), json.keySet()); assertTrue(result.error().isEmpty()); }
    @Test void missingRuntimeOrPlatformIsProminentlySkippedWithRecovery() throws Exception {
        Path out = temporary.resolve("missing-runtime"); var result = run("--method", "fit", "--recipe", recipe(temporary.resolve("missing-build")).toString(), "--out", out.toString()); assertEquals(3, result.code()); assertTrue(result.error().contains("SKIPPED")); assertTrue(result.error().contains("Required action")); assertFalse(Files.exists(out.resolve("COMPLETED.json"))); assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("skip")); assertEquals(3L, RepeatBundle.object(out.resolve("analysis-status.json"), 65536).get("exit_code"));
    }
    @Test void absentInstallationPermissionSkipsWithoutCreatingBuild() throws Exception {
        Path out = temporary.resolve("setup-skipped"); var result = run("--method", "setup", "--version", "1.3.3", "--compiler-spec", compiler(temporary.resolve("compiler")).toString(), "--out", out.toString()); assertEquals(3, result.code()); assertFalse(Files.exists(out.resolve("build"))); assertTrue(Files.exists(out.resolve("WARNINGS.md"))); assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void existingOutputAndItsContentsArePreserved() throws Exception {
        Path out = Files.createDirectory(temporary.resolve("existing")); Files.writeString(out.resolve("keep.txt"), "original"); var result = run("--method", "fit", "--recipe", "unused.json", "--out", out.toString()); assertEquals(2, result.code()); assertEquals("original", Files.readString(out.resolve("keep.txt"))); try (var files = Files.list(out)) { assertEquals(1, files.count()); }
    }
    @Test void outputCannotMutateAnExistingBuildOrCompilerTree() throws Exception {
        Path build = Files.createDirectory(temporary.resolve("build")); Path nested = build.resolve("new"); var result = run("--method", "fit", "--recipe", recipe(build).toString(), "--out", nested.toString()); assertEquals(2, result.code()); assertFalse(Files.exists(nested));
        Path prefix = Files.createDirectory(temporary.resolve("compiler")); nested = prefix.resolve("new"); result = run("--method", "setup", "--version", "1.3.3", "--compiler-spec", compiler(prefix).toString(), "--out", nested.toString(), "--allow-install"); assertEquals(2, result.code()); assertFalse(Files.exists(nested));
    }
    @Test void unknownDuplicateAndUnusedInstallOptionsAreRefusedBeforeCreatingOutput() throws Exception {
        Path out = temporary.resolve("unused"); assertEquals(2, run("--method", "limits", "--ignored", "1").code()); assertEquals(2, run("--method", "limits", "--method", "limits").code()); assertEquals(2, run("--method", "fit", "--recipe", "unused.json", "--out", out.toString(), "--allow-install").code()); assertFalse(Files.exists(out));
    }
    @Test void compilerParsingUsesTheExactProtectedBytesAndRejectsUnknownFields() throws Exception {
        Path source = compiler(temporary.resolve("compiler")); byte[] raw = Files.readAllBytes(source); Files.writeString(source, "{}"); var tools = RsemSetup.tools(source, raw); assertEquals(temporary.resolve("compiler/bin/gcc"), tools.cc()); assertThrows(Exception.class, () -> RsemSetup.tools(source, "{\"ignored\":1}".getBytes(StandardCharsets.UTF_8)));
    }
}
