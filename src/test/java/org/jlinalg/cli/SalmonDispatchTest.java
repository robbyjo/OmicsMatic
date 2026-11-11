/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SalmonDispatchTest {
    @Test void rootRouteReturnsExactSameProfilesAsStandaloneCommand() throws Exception {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        try (var stdout = new PrintStream(out, true, StandardCharsets.UTF_8); var stderr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            assertEquals(0, JLinAlgCli.run(new String[]{"salmon-tools", "--runtime-profiles"}, stdout, stderr));
        }
        assertEquals(SalmonIndexRecipe.profiles(), SimpleJson.parse(out.toString(StandardCharsets.UTF_8).strip())); assertEquals("", err.toString(StandardCharsets.UTF_8));
    }
    @Test void rootRoutePreservesProminentUnavailableRuntimeRecovery() {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        try (var stdout = new PrintStream(out, true, StandardCharsets.UTF_8); var stderr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            assertEquals(3, JLinAlgCli.run(new String[]{"salmon-tools", "--recipe", "not-opened.json", "--out", "not-created"}, stdout, stderr));
        }
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3 — ANALYSIS SKIPPED")); assertTrue(err.toString(StandardCharsets.UTF_8).contains("RUN-SETUP.txt"));
    }
    @Test void existingRootHelpListsTypedSalmonCommand() {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        try (var stdout = new PrintStream(out, true, StandardCharsets.UTF_8); var stderr = new PrintStream(err, true, StandardCharsets.UTF_8)) { assertEquals(0, JLinAlgCli.run(new String[]{"--help"}, stdout, stderr)); }
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("salmon-tools --help"));
    }
}
