/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jlinalg.workflow.NfcoreAtacseq121Mappings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class NfcoreAtacseq121Test {
    @Test void originalDesignReferences() throws Exception {
        byte[] raw;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/workflow/nfcore-atacseq121/original-designs.json")) {
            assertNotNull(input); raw = input.readAllBytes();
        }
        assertEquals("4c4e1af79d5f6a15a46894e60d6ce1c0ffc531985d12639864f4fc421f36701b", sha(raw));
        Map<?, ?> fixture = object(SimpleJson.parse(new String(raw, StandardCharsets.UTF_8)));
        assertEquals("1b3a832db5a53c92c2c89a4d6d79455e860461ad", fixture.get("source_commit"));
        List<?> cases = list(fixture.get("cases")); assertEquals(33, cases.size()); int valid = 0;
        for (Object value : cases) {
            var row = object(value); String design = text(row.get("design")), label = text(row.get("id"));
            if (Boolean.TRUE.equals(row.get("valid"))) {
                var result = NfcoreAtacseq121Mappings.read(new StringReader(design), NfcoreAtacseq121Mappings.Limits.defaults());
                assertEquals(row.get("checked"), result.checkedCsv(), label); valid++;
                if (label.equals("unbalanced")) { assertTrue(result.replicatesExist()); assertTrue(result.multipleGroups()); assertEquals(3, result.libraries().size()); }
                if (label.equals("source-empty-cell-collapse")) assertEquals("G_R1_T1", result.reads().get(0).sampleId());
                assertThrows(UnsupportedOperationException.class, () -> result.reads().clear());
            } else assertThrows(IOException.class, () -> NfcoreAtacseq121Mappings.read(new StringReader(design), NfcoreAtacseq121Mappings.Limits.defaults()), label);
        }
        assertEquals(23, valid);
    }
    @Test void wholeInputCapacityRefusalsAndUnsupportedReplicateSpelling() {
        String header = "group,replicate,fastq_1,fastq_2\n";
        assertThrows(IOException.class, () -> NfcoreAtacseq121Mappings.read(new StringReader(header+"G,1,/a.fq.gz,\nG,2,/b.fq.gz,\n"), new NfcoreAtacseq121Mappings.Limits(1, 1000)));
        assertThrows(IOException.class, () -> NfcoreAtacseq121Mappings.read(new StringReader(header+"G,1,/"+"a".repeat(1000)+".fq.gz,\n"), new NfcoreAtacseq121Mappings.Limits(10, 100)));
        assertThrows(IOException.class, () -> NfcoreAtacseq121Mappings.read(new StringReader(header+"G,١,/a.fq.gz,\n"), NfcoreAtacseq121Mappings.Limits.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new NfcoreAtacseq121Mappings.Limits(0, 100));
    }
    @Test void sourceEditionAndCompleteOriginalFileLock() throws Exception {
        var lock = NfcoreAtacseq121Request.sourceLock();
        assertEquals("nf-core/atacseq", lock.get("pipeline")); assertEquals("1.2.1", lock.get("version"));
        assertEquals(86, object(lock.get("files")).size());
        assertEquals("nfcore-atacseq-1.2.1", NfcoreAtacseq121Request.PROFILE);
        assertTrue(new String(NfcoreAtacseq121Request.resource("LICENSE-upstream.txt", 10000), StandardCharsets.UTF_8).contains("MIT License"));
    }
    @Test void rootRoutesAndUnsupportedEditionHaveClearStatus() {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var stream = new java.io.PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            for (String action : List.of("ATACSEQ-1.2.1", "ENGINE", "CAPTURED")) {
                bytes.reset(); assertEquals(0, JLinAlgCli.run(new String[]{"nfcore-tools", action, "--help"}, stream, stream), action);
                assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("SKIP3"), action);
                assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Required action:"), action);
            }
            bytes.reset(); assertEquals(2, JLinAlgCli.run(new String[]{"nfcore-tools", "ATACSEQ-99"}, stream, stream));
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("not inferred or substituted"));
        }
    }
    @Test void brokenHelpStreamDoesNotReportSuccess() {
        try (var broken = new java.io.PrintStream(new java.io.OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("forced closed output"); }
        })) {
            assertEquals(2, NfcoreToolsCli.run(new String[]{"--help"}, broken, broken));
            assertEquals(2, NfcoreToolsCli.run(new String[]{"ATACSEQ-1.2.1", "--help"}, broken, broken));
        }
    }
    @Test void taskPopulationMatchesCompleteOriginalExecution() throws Exception {
        byte[] raw;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/workflow/nfcore-atacseq121/original-complete-trace.json")) {
            assertNotNull(input); raw = input.readAllBytes();
        }
        assertEquals("04aefb1112ba6789feef2f32f7050ab9ef80d56e4a004b14b7d2e1a4f6807a8d", sha(raw));
        Map<?, ?> fixture = object(SimpleJson.parse(new String(raw, StandardCharsets.UTF_8)));
        var design = NfcoreAtacseq121Mappings.read(new StringReader(text(fixture.get("design"))), NfcoreAtacseq121Mappings.Limits.defaults());
        var reference = new java.util.TreeMap<String, Integer>();
        for (var row : object(fixture.get("process_counts")).entrySet()) reference.put(text(row.getKey()), (int)integer(row.getValue(), 1, 1000000));
        assertEquals(39, reference.size()); assertEquals(88, reference.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(reference, NfcoreAtacseq121Audit.expectedTasks(design, object(fixture.get("parameters"))));
    }
}
