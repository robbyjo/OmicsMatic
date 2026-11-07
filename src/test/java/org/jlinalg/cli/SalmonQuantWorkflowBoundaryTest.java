/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SalmonQuantWorkflowBoundaryTest {
    @TempDir Path temporary;
    @Test void missingCompleteFamilyCannotBecomeAUserResult() throws Exception { assertThrows(IOException.class, () -> SalmonQuantWorkflow.verify(temporary.toAbsolutePath(), () -> { })); }
    @Test void failureMarkerPreventsAnyPassingPrefixAdmission() throws Exception {
        NextflowFiles.writeJson(temporary.resolve("FAILURE.json"), Map.of("status", "FAIL2"));
        NextflowFiles.writeJson(temporary.resolve("COMPLETED.json"), Map.of("status", "COMPLETE_DECLARED_RETAINED_POPULATION"));
        assertThrows(IOException.class, () -> SalmonQuantWorkflow.verify(temporary.toAbsolutePath(), () -> { }));
    }
    @Test void portableCancellationMakesNoChangesAndReturnsNoResult() throws Exception {
        assertThrows(CancellationException.class, () -> SalmonQuantWorkflow.verify(temporary.toAbsolutePath(), () -> { throw new CancellationException(); }));
        try (var paths = Files.list(temporary)) { assertEquals(0, paths.count()); }
    }
    @Test void executionPlatformAndInvalidRecipeHaveDistinctUnavailableOrFailureBoundaries() throws Exception {
        Path recipe = temporary.resolve("invalid.json").toAbsolutePath(), root = temporary.resolve("new-attempt").toAbsolutePath(); Files.writeString(recipe, "{}\n");
        IOException failed = assertThrows(IOException.class, () -> SalmonQuantWorkflow.start(recipe, temporary.resolve("missing-home").toAbsolutePath(), temporary.resolve("missing-setup").toAbsolutePath(), root, () -> { }));
        if (System.getProperty("os.name").equals("Linux")) {
            assertFalse(failed instanceof SalmonRuntime.Unavailable); assertTrue(Files.isRegularFile(root.resolve("FAILURE.json"))); assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("FAIL2")); assertFalse(Files.exists(root.resolve("COMPLETED.json")));
        } else { assertInstanceOf(SalmonRuntime.Unavailable.class, failed); assertFalse(Files.exists(root)); }
    }
    @Test void unexpectedGuardErrorCannotLeaveAnAdmittedPrefix() throws Exception {
        Path recipe = temporary.resolve("interrupted.json").toAbsolutePath(), root = temporary.resolve("error-attempt").toAbsolutePath(); Files.writeString(recipe, "{}\n");
        IOException failed = assertThrows(IOException.class, () -> SalmonQuantWorkflow.start(recipe, temporary.resolve("home").toAbsolutePath(), temporary.resolve("setup").toAbsolutePath(), root, () -> { throw new AssertionError("Controlled unexpected admission error"); }));
        if (System.getProperty("os.name").equals("Linux")) {
            assertInstanceOf(AssertionError.class, failed.getCause()); assertTrue(Files.isRegularFile(root.resolve("FAILURE.json"))); assertFalse(Files.exists(root.resolve("COMPLETED.json")));
        } else assertInstanceOf(SalmonRuntime.Unavailable.class, failed);
    }
}
