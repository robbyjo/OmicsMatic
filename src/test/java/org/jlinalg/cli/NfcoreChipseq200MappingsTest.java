/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.workflow.NfcoreChipseq200Mappings;
import org.junit.jupiter.api.Test;
import static org.jlinalg.cli.NextflowFiles.*;

class NfcoreChipseq200MappingsTest {
    private static byte[] resource(String name) throws IOException { try (var stream = NfcoreChipseq200MappingsTest.class.getResourceAsStream("/org/jlinalg/workflow/nfcore-chipseq200/" + name)) { assertNotNull(stream); return stream.readAllBytes(); } }
    @Test void everyCompleteOriginalCheckerCaseAndLiteralPolicy() throws Exception {
        var files = new LinkedHashMap<String,byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(resource("reference.zip")))) { for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) { assertFalse(entry.isDirectory()); assertNull(files.put(entry.getName(), zip.readAllBytes())); } }
        Map<?,?> source = object(SimpleJson.parse(utf8(resource("SOURCE.json")))); Map<?,?> pins = object(source.get("reference_files")); assertEquals(pins.keySet(),files.keySet());
        for (var entry : files.entrySet()) assertEquals(fingerprint(pins.get(entry.getKey()),10000000),new Pin(entry.getValue().length,sha(entry.getValue())),entry.getKey());
        int positives = 0, refusals = 0;
        for (Object value : list(object(source.get("source")).get("cases"))) {
            Map<?,?> row = object(value); String name = text(row.get("name")); var input = new StringReader(utf8(files.get(name + "/input.csv"))); var limits = NfcoreChipseq200Mappings.Limits.defaults();
            if (integer(row.get("exit_code"),0,255) != 0) { assertThrows(IOException.class,() -> NfcoreChipseq200Mappings.inspect(input,limits),name); refusals++; continue; }
            var plan = NfcoreChipseq200Mappings.inspect(input,limits); byte[] original = files.get(name + "/checked.csv"); assertArrayEquals(original,plan.sourceCheckedCsv().getBytes(java.nio.charset.StandardCharsets.UTF_8),name); NfcoreChipseq200Mappings.auditChecked(new StringReader(utf8(original)),plan,limits);
            if (Boolean.TRUE.equals(row.get("literal_scientific_identifiers"))) assertDoesNotThrow(plan::requireLiteralIdentifiers,name); else assertThrows(IOException.class,plan::requireLiteralIdentifiers,name);
            if (Boolean.TRUE.equals(row.get("consistent_library_metadata"))) assertDoesNotThrow(plan::requireConsistentLibraryMetadata,name); else assertThrows(IOException.class,plan::requireConsistentLibraryMetadata,name);
            for (var read : plan.reads()) { assertEquals(read.id(),read.readGroupId()); assertEquals(read.id(),read.readGroupLibrary()); assertEquals(read.libraryId(),read.readGroupSample()); }
            positives++;
        }
        assertEquals(12,positives); assertEquals(15,refusals);
    }
}
