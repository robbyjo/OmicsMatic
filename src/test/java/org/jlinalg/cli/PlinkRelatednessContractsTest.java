/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlinkRelatednessContractsTest {
    @TempDir Path temporary;

    static Map<String, Object> request() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("input_bytes", 50_000_000L); limits.put("metadata_bytes", 5_000_000L);
        limits.put("working_bytes", 268_435_456L); limits.put("disk_bytes", 100_000_000L);
        limits.put("output_bytes", 50_000_000L); limits.put("files", 100);
        limits.put("samples", 100_000); limits.put("variants", 2_000_000);
        limits.put("pair_variant_operations", 1_000_000_000L); limits.put("pair_batch_size", 7);
        limits.put("line_bytes", 16384); limits.put("wall_seconds", 120);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("counts", true); settings.put("rel_check", false); settings.put("kinship_filter", null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", 1); result.put("mode", "RUN"); result.put("operation", "KING_TABLE");
        result.put("bfile", "input/cohort"); result.put("runtime_home", "private/runtime");
        result.put("out", "new-output"); result.put("threads", 2); result.put("memory_mib", 640);
        result.put("seed", 4_294_967_295L); result.put("settings", settings); result.put("limits", limits);
        return result;
    }

    @Test void keepsOperationsExplicitAndNeverForwardsArbitraryArguments() throws Exception {
        var raw = request();
        var table = PlinkRelatednessContracts.validate(raw);
        assertEquals("plink2", table.engine());
        assertTrue(PlinkRelatednessContracts.arguments(table).contains("counts"));
        raw.put("operation", "KING_MATRIX");
        assertThrows(IOException.class, () -> PlinkRelatednessContracts.validate(raw));
        raw.put("settings", Map.of("precision", "FLOAT32"));
        var matrix = PlinkRelatednessContracts.arguments(PlinkRelatednessContracts.validate(raw));
        assertTrue(matrix.contains("triangle")); assertTrue(matrix.contains("bin4"));
        raw.put("arguments", java.util.List.of("--arbitrary"));
        assertThrows(IOException.class, () -> PlinkRelatednessContracts.validate(raw));
    }

    @Test void rejectsLossyOrNonfiniteNumericalChoices() throws Exception {
        var raw = request();
        assertEquals(4_294_967_295L, PlinkRelatednessContracts.validate(raw).seed());
        for (Object value : new Object[] { 1.5, Double.NaN, Double.POSITIVE_INFINITY, "1", -1L }) {
            raw.put("seed", value);
            assertThrows(IOException.class, () -> PlinkRelatednessContracts.validate(raw));
        }
        var invalid = request();
        var limits = PlinkRelatednessSupport.object(invalid.get("limits"), "limits");
        limits.put("pair_variant_operations", 9_007_199_254_740_992L); invalid.put("limits", limits);
        assertThrows(IOException.class, () -> PlinkRelatednessContracts.validate(invalid));
    }

    @Test void rejectsDuplicateKeysInvalidUtf8AndExcessiveNesting() throws Exception {
        Path input = temporary.resolve("input.json");
        Files.writeString(input, "{\"mode\":\"RUN\",\"mode\":\"VERIFY\"}");
        assertThrows(IOException.class, () -> PlinkRelatednessSupport.read(input));
        Files.write(input, new byte[] { '{', '"', (byte) 0xc3, '"', ':', '1', '}' });
        assertThrows(IOException.class, () -> PlinkRelatednessSupport.read(input));
        Files.writeString(input, "{\"x\":" + "[".repeat(33) + "0" + "]".repeat(33) + "}");
        assertThrows(IOException.class, () -> PlinkRelatednessSupport.read(input));
        Files.writeString(input, "{\"x\":\"" + "[".repeat(64) + "\"}");
        assertEquals("[".repeat(64), PlinkRelatednessSupport.read(input).get("x"));
    }

    @Test void enforcesUtf8ByteCapacityAndRetainsPriorRows() throws Exception {
        Path table = temporary.resolve("partial.tsv");
        try (var writer = new PlinkRelatednessSupport.Table(table, false, 64,
                new PlinkRelatednessSupport.Clock(10))) {
            writer.line("header");
            assertThrows(IOException.class, () -> writer.line("\u03b1".repeat(33)));
        }
        assertEquals("header\n", Files.readString(table, StandardCharsets.UTF_8));
        try (var reader = new PlinkRelatednessSupport.Table(table, true, 64,
                new PlinkRelatednessSupport.Clock(10))) {
            assertThrows(IOException.class, () -> reader.line("different"));
        }
    }
}
