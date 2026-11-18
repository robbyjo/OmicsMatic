/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FcsReaderTest {
    @TempDir Path temporary;
    private record Control(String id, String charset, String policy, String expected,
                           long events, int parameters, String digest, byte[] file) { }
    private List<Control> controls() throws Exception {
        byte[] raw;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/cytometry/storage-controls.tsv")) {
            assertNotNull(input); raw = input.readAllBytes();
        }
        assertEquals("688e49a0d5273c30905b5711f9bb02b226c641e75ab418e6c3e9500292fcbb10", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        return new String(raw, StandardCharsets.UTF_8).lines().filter(line -> !line.startsWith("#")).map(line -> {
            String[] c = line.split("\t", -1); assertEquals(8, c.length);
            return new Control(c[0], c[1], c[2], c[3], Long.parseLong(c[4]), Integer.parseInt(c[5]), c[6], Base64.getDecoder().decode(c[7]));
        }).toList();
    }
    private Control control(String id) throws Exception { return controls().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(); }
    private Path write(Control c) throws IOException { return Files.write(temporary.resolve(c.id()+".fcs"), c.file(), StandardOpenOption.CREATE_NEW); }
    private FcsReader open(Control c, Path path) throws IOException {
        return FcsReader.open(path, FcsReader.Limits.defaults(), Charset.forName(c.charset()), FcsReader.TextPolicy.valueOf(c.policy()));
    }
    @Test void completeIndependentStoredValuesAndExplicitRefusals() throws Exception {
        List<Control> controls = controls(); assertEquals(25, controls.size()); int completed = 0, refused = 0;
        for (Control c : controls) {
            Path path = write(c);
            if (!c.expected().equals("PASS")) {
                IOException failure = assertThrows(IOException.class, () -> open(c, path), c.id());
                assertEquals(c.expected().equals("SKIP3"), failure instanceof FcsReader.UnsupportedFormatException, c.id()); refused++; continue;
            }
            try (FcsReader reader = open(c, path)) {
                assertEquals(c.events(), reader.info().events(), c.id()); assertEquals(c.parameters(), reader.info().parameters().size(), c.id());
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); ByteBuffer bits = ByteBuffer.allocate(8); double[] event = new double[c.parameters()];
                while (reader.readEvent(event)) for (double value : event) { bits.clear(); bits.putLong(Double.doubleToLongBits(value)); digest.update(bits.array()); }
                assertEquals(c.digest(), HexFormat.of().formatHex(digest.digest()), c.id());
                assertEquals(c.events(), reader.summary().eventsRead());
                if (c.id().equals("integer8-mask")) assertEquals(2, reader.summary().integerValuesWithUnusedBits());
                if (c.id().equals("nonfinite")) assertEquals(3, reader.summary().nonfiniteValues());
                assertArrayEquals(c.file(), Files.readAllBytes(path)); completed++;
            }
        }
        assertEquals(15, completed); assertEquals(10, refused);
    }
    @Test void metadataRetainsCaseDollarEscapesAndUnicodeAndIsImmutable() throws Exception {
        Control c = control("unicode-and-dollar");
        try (FcsReader reader = open(c, write(c))) {
            assertEquals("β|$FSC", reader.info().parameters().get(0).name());
            assertEquals("β|$FSC", reader.keyword("$p1N")); assertEquals("value$with|delimiter", reader.keyword("vendorkey"));
            assertEquals("VendorKey", reader.info().keywords().stream().filter(k -> k.name().equals("VendorKey")).findFirst().orElseThrow().name());
            assertNull(reader.info().parameters().get(0).label()); assertNull(reader.keyword("ABSENT"));
            assertThrows(UnsupportedOperationException.class, () -> reader.info().parameters().clear());
            assertThrows(UnsupportedOperationException.class, () -> reader.info().keywords().clear());
            assertThrows(UnsupportedOperationException.class, () -> reader.info().warnings().clear());
        }
    }
    @Test void incompleteStreamWrongBufferAndClosedStreamNeverReportCompletion() throws Exception {
        Control c = control("float-little"); FcsReader reader = open(c, write(c));
        try {
            assertThrows(IOException.class, reader::summary);
            assertThrows(IllegalArgumentException.class, () -> reader.readEvent(new double[1]));
            assertTrue(reader.readEvent(new double[2])); assertThrows(IOException.class, reader::summary);
        } finally { reader.close(); }
        assertThrows(IOException.class, () -> reader.readEvent(new double[2])); assertThrows(IOException.class, reader::summary); reader.close();
    }
    @Test void completeInputCapacitiesRefuseBeforeDeliveringAnyEvents() throws Exception {
        Control c = control("float-little"); Path p = write(c); var standard = FcsReader.Limits.defaults();
        for (var limits : List.of(new FcsReader.Limits(c.file().length-1L, 2, 2, 10000), new FcsReader.Limits(10000, 1, 2, 10000),
                new FcsReader.Limits(10000, 2, 1, 10000), new FcsReader.Limits(10000, 2, 2, 2))) {
            assertThrows(IOException.class, () -> FcsReader.open(p, limits, StandardCharsets.UTF_8));
        }
        assertEquals(100_000_000, standard.events());
        assertThrows(IllegalArgumentException.class, () -> new FcsReader.Limits(0, 1, 1, 1));
    }
    @Test void encodingsAndSpacePaddingRequireTheirExplicitPolicies() throws Exception {
        Control legacy = control("legacy-latin1"); Path legacyPath = write(legacy);
        assertThrows(IOException.class, () -> FcsReader.open(legacyPath, FcsReader.Limits.defaults(), StandardCharsets.UTF_8));
        try (FcsReader reader = open(legacy, legacyPath)) { assertEquals("café", reader.info().parameters().get(0).name()); }
        Control padding = control("explicit-padding"); Path padded = write(padding);
        assertThrows(IOException.class, () -> FcsReader.open(padded, FcsReader.Limits.defaults(), StandardCharsets.UTF_8));
        try (FcsReader reader = open(padding, padded)) { assertTrue(reader.info().warnings().stream().anyMatch(w -> w.contains("19 trailing ASCII space"))); }
        Control invalid31 = control("bad-utf8"); Path invalid = write(invalid31);
        assertThrows(IOException.class, () -> FcsReader.open(invalid, FcsReader.Limits.defaults(), StandardCharsets.ISO_8859_1));
    }
    @Test void changedFileLengthPreventsCompleteSummary() throws Exception {
        Control c = control("float-little"); Path path = write(c);
        try (FcsReader reader = open(c, path)) {
            double[] event = new double[2]; assertTrue(reader.readEvent(event));
            Files.write(path, new byte[]{0}, StandardOpenOption.APPEND); assertTrue(reader.readEvent(event));
            assertThrows(IOException.class, reader::summary); assertThrows(IOException.class, () -> reader.readEvent(event));
        }
    }
}
