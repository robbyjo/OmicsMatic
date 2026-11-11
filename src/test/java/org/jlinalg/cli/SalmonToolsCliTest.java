/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonToolsCliTest {
    @TempDir Path temporary;
    private record Result(int code, String out, String err) { }
    @Test void helpStatesSeparateCompleteModesLibraryAndRecoveryRules() {
        var result = run("--help"); assertEquals(0, result.code()); assertTrue(result.out().contains("--index-recipe")); assertTrue(result.out().contains("transcriptome SAM/BAM")); assertTrue(result.out().contains("fragment-length mean and SD")); assertTrue(result.out().contains("SKIP3")); assertTrue(result.out().contains("FAIL2")); assertEquals("", result.err());
    }
    @Test void exactProfilesDoNotCollapseToMajorOrLatestVersion() throws Exception { var result = run("--runtime-profiles"); assertEquals(0, result.code()); assertEquals(SalmonIndexRecipe.profiles(), SimpleJson.parse(result.out().strip())); }
    @ParameterizedTest @ValueSource(strings={"--extra-flags", "--resume", "--allow-download", "--single-cell", "--verify"})
    void unknownIncompleteOrAnalysisInstallOptionsHaveProminentFailure(String option) {
        var result = run(option); assertEquals(2, result.code()); assertTrue(result.err().contains("Salmon FAIL2 — NO RESULT ADMITTED")); assertTrue(result.err().contains("Required action:"));
    }
    @Test void missingExactRuntimeSkipsAnalysisWithNamedSetupRecovery() {
        Path root = temporary.resolve("out").toAbsolutePath(); var result = run("--recipe", temporary.resolve("recipe.json").toString(), "--out", root.toString()); assertEquals(3, result.code()); assertTrue(result.err().contains("SKIP3 — ANALYSIS SKIPPED")); assertTrue(result.err().contains("--home")); assertTrue(result.err().contains("RUN-SETUP.txt")); assertFalse(Files.exists(root));
    }
    @Test void duplicateOrConflictingModesCannotSilentlySelectOneRequest() {
        var result = run("--recipe", "a", "--recipe", "b", "--out", "c"); assertEquals(2, result.code()); result = run("--recipe", "a", "--index-recipe", "b", "--out", "c"); assertEquals(2, result.code());
    }
    @Test void verifyMissingCompleteFamilyDoesNotReportSuccess() { assertEquals(2, run("--verify", temporary.toString()).code()); assertEquals(2, run("--verify-index", temporary.toString()).code()); }
    @Test void setupExportIsPortableAndNeverReportsInstallationOrFitCompletion() {
        Path out = temporary.resolve("kit"); var result = run("--export-runtime-setup", out.toString()); assertEquals(0, result.code()); assertTrue(result.out().contains("User analysis admitted=false")); assertTrue(Files.isRegularFile(out.resolve("export.json"))); assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void existingExportReportsFailureAndPreservesExistingContent() throws Exception {
        Files.writeString(temporary.resolve("keep.txt"), "original"); var result = run("--export-runtime-setup", temporary.toString()); assertEquals(2, result.code()); assertEquals("original", Files.readString(temporary.resolve("keep.txt"))); assertTrue(result.err().contains("FAIL2"));
    }
    private static Result run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream(); int code;
        try (var stdout = new PrintStream(out, true, StandardCharsets.UTF_8); var stderr = new PrintStream(err, true, StandardCharsets.UTF_8)) { code = SalmonToolsCli.run(args, stdout, stderr); }
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
}
