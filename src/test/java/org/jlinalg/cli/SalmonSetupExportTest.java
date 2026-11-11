/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SalmonSetupExportTest {
    @TempDir Path temporary;
    @Test void completeStandaloneKitPreservesEveryPinnedResourceAndSeparatePermissions() throws Exception {
        Path out = temporary.resolve("kit").toAbsolutePath(); SalmonSetupExport.export(out); Map<?, ?> exported = NextflowFiles.object(NextflowFiles.json(out.resolve("export.json"), 100000));
        assertEquals("EXPORTED_KIT_NOT_RUN", exported.get("status")); assertEquals(false, exported.get("user_analysis_admitted")); var files = NextflowFiles.object(exported.get("files")); assertEquals(11, files.size());
        for (var row : files.entrySet()) { String name = NextflowFiles.member(NextflowFiles.text(row.getKey())); assertEquals(NextflowFiles.fingerprint(row.getValue(), 10000000), NextflowFiles.pin(out.resolve(name), 10000000, () -> { })); }
        assertEquals(SalmonSetupEvidence.INSTALLER, NextflowFiles.pin(out.resolve("install_salmon.py"), 1000000, () -> { }));
        assertEquals(SalmonSetupEvidence.LOCK_SHA, NextflowFiles.pin(out.resolve("salmon-runtime-lock.json"), 1000000, () -> { }).sha256());
        assertEquals(SalmonSetupEvidence.HEALTH_SHA, NextflowFiles.pin(out.resolve("health-lock.json"), 1000000, () -> { }).sha256());
        String guide = Files.readString(out.resolve("RUN-SETUP.txt")); assertTrue(guide.contains("Python3.12+")); assertTrue(guide.contains("--allow-install --allow-download")); assertTrue(guide.contains("--archive-dir")); assertTrue(guide.contains("SKIP3")); assertTrue(guide.contains("FAIL2"));
        assertFalse(Files.exists(out.resolve("COMPLETED.json"))); assertFalse(Files.exists(out.resolve(".omicsmatic-salmon.json")));
        try (var all = Files.walk(out)) { assertEquals(13, all.filter(p -> !p.equals(out)).count()); }
    }
    @Test void existingDestinationAndUnrelatedFilesArePreserved() throws Exception {
        Path out = Files.createDirectory(temporary.resolve("existing")).toAbsolutePath(); Files.writeString(out.resolve("keep.txt"), "original"); assertThrows(IOException.class, () -> SalmonSetupExport.export(out)); assertEquals("original", Files.readString(out.resolve("keep.txt"))); assertFalse(Files.exists(out.resolve("FAILURE.txt")));
    }
    @Test void missingParentDoesNotCreateAnImplicitDirectoryTree() throws Exception { Path out = temporary.resolve("absent/kit").toAbsolutePath(); assertThrows(IOException.class, () -> SalmonSetupExport.export(out)); assertFalse(Files.exists(temporary.resolve("absent"))); }
    @Test void portableExportSupportsUnicodeAndSpacesWithoutExecutingAnything() throws Exception {
        Path out = temporary.resolve("kit café data").toAbsolutePath(); SalmonSetupExport.export(out); assertEquals(2163230, Files.size(out.resolve("health-inputs/reads_1.fastq"))); assertTrue(Files.isRegularFile(out.resolve("LICENSE-Salmon.txt")));
    }
}
