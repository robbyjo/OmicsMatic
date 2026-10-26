/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalTransactionTest {
    @TempDir Path dir;
    @Test void publicationRaceRetainsDiagnosticBundleAndNeverOverwritesConcurrentDestination() throws Exception {
        Path out=dir.resolve("result");
        IOException failure=assertThrows(IOException.class,()->FollowupSupport.externalTransaction(Map.of("out",out.toString()),"fixture",(staging,manifest)->{
            Files.writeString(staging.resolve("results.tsv"),"metric\tvalue\nrecords\t1\n");manifest.put("analysis_status","completed");
            Files.createDirectory(out);Files.writeString(out.resolve("user-data"),"concurrent work");
        }));
        assertTrue(failure.getMessage().contains("OUTPUT PUBLICATION FAILED"));assertTrue(failure.getMessage().contains("Required action"));
        assertEquals("concurrent work",Files.readString(out.resolve("user-data")));assertFalse(Files.exists(out.resolve("results.tsv")));
        Path retained;
        try(var paths=Files.list(dir)){retained=paths.filter(p->p.getFileName().toString().startsWith(".jlinalg-followup-")).findFirst().orElseThrow();}
        assertTrue(Files.readString(retained.resolve("WARNINGS.md")).contains("No successful result bundle was published"));
        assertTrue(Files.readString(retained.resolve("manifest.yaml")).contains("failed-publication"));
        assertTrue(Files.readString(retained.resolve("analysis-status.json")).contains("failed-publication"));
    }
}
