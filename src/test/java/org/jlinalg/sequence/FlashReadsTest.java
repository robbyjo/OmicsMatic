/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlashReadsTest {
    @TempDir Path directory;
    private static FlashReads.Settings defaults() { return new FlashReads.Settings(10, 65, 0.25f, 33, false, false); }

    @Test void all5171ActualOriginalReferencesPreservePartitionConsensusAndQuality() throws Exception {
        byte[] packed;
        try (var stream = getClass().getResourceAsStream("flash/original-1.2.11-cases.tsv.gz")) {
            assertNotNull(stream); packed = stream.readAllBytes();
        }
        assertEquals("055efc337996fed42ad2db3a2a5c03abc070d28675363d7869acd134c74daa3d",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed)));
        int cases = 0, merged = 0;
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new java.io.ByteArrayInputStream(packed)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.split("\t", -1); assertEquals(13, f.length);
                var settings = new FlashReads.Settings(Integer.parseInt(f[5]), Integer.parseInt(f[6]), Float.parseFloat(f[7]),
                        Integer.parseInt(f[8]), Boolean.parseBoolean(f[9]), Boolean.parseBoolean(f[10]));
                var result = FlashReads.merge(f[1], f[2], f[3], f[4], settings); cases++;
                if (f[11].equals("-")) assertTrue(result.isEmpty(), f[0]);
                else {
                    assertTrue(result.isPresent(), f[0]); var value = result.orElseThrow(); merged++;
                    assertEquals(f[11], value.sequence(), f[0]); assertEquals(f[12], value.quality(), f[0]);
                }
            }
        }
        assertEquals(5171, cases); assertTrue(merged > 1700);
    }

    @Test void unknownPositionsAreExcludedAndMinimumCountsCallableBases() {
        var value = FlashReads.merge("ACNNGT", "IIIIII", "ACNNGT", "IIIIII", new FlashReads.Settings(4, 4, 0, 33, true, false)).orElseThrow();
        assertEquals(6, value.physicalOverlap()); assertEquals(4, value.callableOverlap()); assertEquals(0, value.mismatches());
        assertTrue(FlashReads.merge("ACNNGT", "IIIIII", "ACNNGT", "IIIIII", new FlashReads.Settings(5, 5, 0, 33, true, false)).isEmpty());
        assertTrue(FlashReads.merge("NNNN", "IIII", "NNNN", "IIII", new FlashReads.Settings(1, 1, 1, 33, true, false)).isEmpty());
    }

    @Test void firstPerfectOverlapScalesBeyondTheFormerDraftLimit() {
        String sequence = "ACGT".repeat(25_000), qualities = "I".repeat(sequence.length());
        var result = FlashReads.merge(sequence, qualities, sequence, qualities, defaults()).orElseThrow();
        assertEquals(sequence, result.sequence()); assertEquals(100_000, result.physicalOverlap()); assertEquals(FlashReads.Orientation.INNIE, result.orientation());
        assertEquals(qualities, result.quality());
    }

    @Test void invalidEncodingBasesLengthsAndSettingsFailBeforeSearch() {
        assertThrows(IllegalArgumentException.class, () -> new FlashReads.Settings(0, 65, .25f, 33, false, false));
        assertThrows(IllegalArgumentException.class, () -> new FlashReads.Settings(10, 9, .25f, 33, false, false));
        assertThrows(IllegalArgumentException.class, () -> new FlashReads.Settings(10, 65, Float.NaN, 33, false, false));
        assertThrows(IllegalArgumentException.class, () -> new FlashReads.Settings(10, 65, .25f, 34, false, false));
        assertThrows(IllegalArgumentException.class, () -> FlashReads.merge("a", "I", "T", "I", defaults()));
        assertThrows(IllegalArgumentException.class, () -> FlashReads.merge("A", "", "T", "I", defaults()));
        assertThrows(IllegalArgumentException.class, () -> FlashReads.merge("A", "!", "T", "I", new FlashReads.Settings(1, 1, 1, 64, false, false)));
        assertTrue(FlashReads.merge("", "", "", "", defaults()).isEmpty());
    }

    @Test void fullHeaderAndBarcodeRulesKeepOriginalSurprisesVisible() {
        assertEquals("@id", FlashFastqIO.mergedHeader("@id/1 comment"));
        assertEquals("@id#BC comment", FlashFastqIO.mergedHeader("@id/1#BC comment"));
        assertEquals("@id#BC", FlashFastqIO.mergedHeader("@id#BC/1"));
        assertEquals("@id comment path", FlashFastqIO.mergedHeader("@id comment path/file"));
        assertEquals("@id comment", FlashFastqIO.mergedHeader("@id comment"));
        assertEquals("@id", FlashFastqIO.mergedHeader("@id/汉#barcode"));
        assertEquals("id#BC", FlashFastqIO.pairId("@id/1#BC comment"));
        assertEquals("id#BC", FlashFastqIO.pairId("@id/2#BC comment"));
    }

    @Test void cleanupRetainsOriginalHeaderAndCountsEveryTransformation() throws Exception {
        Path path = directory.resolve("input.fastq");
        Files.writeString(path, "@id/1#BC comment  \t\nacGTnRU?. \t\n+different\nIIIIIIIII \t\n");
        try (var reader = new FlashFastqIO.Reader(path, 33)) {
            var read = reader.next(); assertEquals("@id/1#BC comment  \t", read.originalHeader()); assertEquals("@id/1#BC comment", read.header());
            assertEquals("ACGTNNNNN", read.sequence()); assertEquals(2, read.caseChanges()); assertEquals(5, read.unknownChanges());
            assertEquals(3, read.trimmedHeader()); assertEquals(2, read.trimmedSequence()); assertEquals(2, read.trimmedQuality()); assertTrue(read.ignoredPlusIdentity()); assertNull(reader.next());
        }
    }

    @Test void malformedFastqAndInvalidUtf8AreRejected() throws Exception {
        String[] bad = {"@id\nAC GT\n+\nIIIII\n", "@id\nAC\n+\nI\n", "@id\nAC\n", "@id\nAC\nwrong\nII\n", "@id\nAC\n+\n!!\n", "@ \nAC\n+\nII\n"};
        for (int i = 0; i < bad.length; i++) {
            Path path = directory.resolve("bad" + i); Files.writeString(path, bad[i]);
            try (var reader = new FlashFastqIO.Reader(path, 64)) { assertThrows(java.io.IOException.class, reader::next); }
        }
        Path invalid = directory.resolve("utf8"); Files.write(invalid, new byte[]{'@', (byte) 0xff, '\n'});
        try (var reader = new FlashFastqIO.Reader(invalid, 33)) { assertThrows(java.io.IOException.class, reader::next); }
    }

    @Test void gzipIsDetectedByMagicAndWriterRetainsUtf8AndHighQualities() throws Exception {
        Path gz = directory.resolve("stream.gz");
        try (var writer = new FlashFastqIO.Writer(gz, 64)) { writer.write("@汉/1 comment", "ACGTN", "@I~II"); }
        Path renamed = directory.resolve("without-extension"); Files.move(gz, renamed);
        try (var reader = new FlashFastqIO.Reader(renamed, 64)) { var read = reader.next(); assertEquals("@汉/1 comment", read.header()); assertEquals("@I~II", read.quality()); assertNull(reader.next()); }
    }
}
