/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnpEffWorkflowCliTest {
    @TempDir Path temporary;
    @Test void dispatcherExplainsEditionAndFailureBoundaries() {
        var buffer = new ByteArrayOutputStream();
        try (var stream = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            assertEquals(0, JLinAlgCli.run(new String[]{"snpeff-tools", "--help"}, stream, stream));
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        for (String expected : new String[]{"3.1i-r354-source", "SKIP3", "FAIL2", "REPLAY", "noncommercial", "no new catalog credit"}) assertTrue(text.contains(expected), expected);
    }
    @Test void existingDestinationIsNeverMutatedEvenOnFailure() throws Exception {
        Path bundle = temporary.resolve("invalid-bundle"); Files.createDirectory(bundle);
        Path out = temporary.resolve("existing"); Files.createDirectory(out);
        Files.writeString(out.resolve("COMPLETED.properties"), "user evidence\n");
        var buffer = new ByteArrayOutputStream();
        try (var stream = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            assertEquals(2, SnpEffWorkflowCli.run(new String[]{"verify", "--bundle", bundle.toString(), "--out", out.toString()}, stream, stream));
        }
        assertEquals("user evidence\n", Files.readString(out.resolve("COMPLETED.properties")));
        assertFalse(Files.exists(out.resolve("FAILURE.txt")));
    }
    @Test void missingOriginalRuntimeSkipsDependentAnalysisWithNamedRecovery() throws Exception {
        Path request = temporary.resolve("request.properties");
        Files.writeString(request, "edition=5.2\njar=absent.jar\nconfig=missing.config\ndata=missing-data\ngenome=hg38kg\nvariants=missing.vcf\nformat=vcf\nup_down_stream=100\nheap_mb=512\nwall_ms=30000\nmax_bytes=268435456\nmax_files=1000\n");
        var buffer = new ByteArrayOutputStream();
        try (var stream = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            assertEquals(3, SnpEffWorkflowCli.run(new String[]{"run", "--request", request.toString(), "--out", temporary.resolve("new-output").toString()}, stream, stream));
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("absent.jar")); assertTrue(text.contains("dependent analysis skipped")); assertTrue(text.contains("NEW output"));
        assertFalse(Files.exists(temporary.resolve("new-output")));
    }
    @Test void officialDistributionSizedConfigurationIsAccepted() throws Exception {
        Path large = temporary.resolve("official-size.config");
        try (var writer = Files.newBufferedWriter(large)) {
            String comment = "#" + "configuration documentation ".repeat(40) + "\n";
            for (int i=0; i<18000; i++) writer.write(comment);
            writer.write("tutorial.genome=Independent reference\n");
        }
        assertTrue(Files.size(large) > 19096108);
        assertEquals("Independent reference", SnpEffWorkflowFiles.read(large, false).getProperty("tutorial.genome"));
    }
}
