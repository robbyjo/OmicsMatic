/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ShapeitBcfTest {
    private static final Map<String,byte[]> CAPTURE = capture();
    @TempDir Path temporary;
    static Stream<String> cases() {
        return Stream.of("initial", "initial-raw", "cohort4", "cohort4-raw", "cohort5", "cohort5-raw",
                "wide", "wide-raw", "v44", "v44-raw", "v45", "v45-raw");
    }
    private static ShapeitBcf.Limits limits() {
        var d = ShapeitBcf.Limits.defaults();
        return new ShapeitBcf.Limits(d.samples(), d.records(), d.genotypeCells(), d.inputBytes(), d.decodedBytes(),
                d.retainedBytes(), d.headerLines(), d.headerCharacters(), d.lineCharacters(), 30000, d.formatFields(), d.genotypeWidth(), d.valuesPerRecord());
    }
    private Path fixture(String name) throws IOException {
        Path path = temporary.resolve(name + ".bcf"); Files.write(path, CAPTURE.get(name + ".bcf")); return path;
    }
    private record Parsed(ShapeitBcf.HeaderView header, List<ShapeitBcf.Row> rows, ShapeitBcf.Report report) { }
    private Parsed read(String name) throws IOException {
        var rows = new ArrayList<ShapeitBcf.Row>(); ShapeitBcf.HeaderView[] header = new ShapeitBcf.HeaderView[1];
        var report = ShapeitBcf.read(fixture(name), limits(), new ShapeitBcf.Visitor() {
            @Override public void header(ShapeitBcf.HeaderView value) { header[0] = value; }
            @Override public void row(ShapeitBcf.Row value) { rows.add(value); }
        }, () -> {});
        return new Parsed(header[0], List.copyOf(rows), report);
    }

    @ParameterizedTest @MethodSource("cases")
    void everyRawPayloadAndNormalizedGetterMatchesIndependentHtslib(String name) throws Exception {
        var parsed = read(name); var report = parsed.report(); int samples = -1, records = -1, checks = 0;
        var formatOrder = new LinkedHashMap<Integer,List<String>>(); long genotypeValues = 0, firstBits = 0, ends = 0, typedMissing = 0;
        for (String line : new String(CAPTURE.get(name + ".oracle.tsv"), StandardCharsets.UTF_8).lines().toList()) {
            var f = line.split("\t", -1);
            switch (f[0]) {
                case "H" -> { samples = Integer.parseInt(f[1]); assertEquals(samples, parsed.header().samples().size()); }
                case "V" -> assertTrue(parsed.header().rawText().startsWith("##fileformat=" + f[1] + "\n"));
                case "S" -> assertEquals(f[2], parsed.header().samples().get(Integer.parseInt(f[1])));
                case "E" -> records = Integer.parseInt(f[1]);
                default -> {
                    int index = Integer.parseInt(f[1]); var row = parsed.rows().get(index); checks++;
                    switch (f[0]) {
                        case "R" -> {
                            assertEquals(f[2], row.contig()); assertEquals(Integer.parseInt(f[3]), row.position());
                            assertEquals(Integer.parseInt(f[4]), row.referenceLength()); assertEquals(f[5], row.id());
                            assertEquals(f[6], row.reference()); assertEquals(f[7], String.join(",", row.alternates()));
                            assertEquals(Integer.parseUnsignedInt(f[8], 16), row.qualityBits());
                        }
                        case "T" -> {
                            var value = row.format(f[2]); assertNotNull(value); assertEquals(Integer.parseInt(f[3]), value.type());
                            assertEquals(Integer.parseInt(f[4]), value.width()); formatOrder.computeIfAbsent(index, k -> new ArrayList<>()).add(f[2]);
                        }
                        case "B" -> {
                            var value = f[2].equals("F") ? row.format(f[3]) : row.info().get(f[3]); assertNotNull(value);
                            assertEquals(Integer.parseInt(f[4]), value.type()); assertEquals(Integer.parseInt(f[5]), value.width());
                            assertEquals(Integer.parseInt(f[6]), value.cells()); assertArrayEquals(HexFormat.of().parseHex(f[7]), value.payload(), name + "/" + index + "/" + f[3]);
                        }
                        case "G" -> {
                            var value = row.format(f[2]); int[] expected = numbers(f[3], false);
                            assertArrayEquals(expected, value.integers(), name + "/" + index + "/" + f[2]);
                            if (f[2].equals("GT")) {
                                genotypeValues += expected.length;
                                for (int i = 0; i < expected.length; i++) {
                                    int code = expected[i];
                                    if (code == ShapeitBcf.VECTOR_END) ends++;
                                    else if (code == ShapeitBcf.MISSING) typedMissing++;
                                    else if (i % value.width() == 0 && (code & 1) != 0) firstBits++;
                                }
                            }
                        }
                        case "F" -> assertArrayEquals(numbers(f[3], true), row.format(f[2]).floatBits());
                        case "I" -> assertArrayEquals(numbers(f[3], false), row.info().get(f[2]).cellIntegers(0));
                        case "P" -> assertArrayEquals(numbers(f[3], true), row.info().get(f[2]).cellFloatBits(0));
                        default -> fail("Unknown independent oracle record: " + f[0]);
                    }
                }
            }
        }
        assertTrue(checks > 20); assertEquals(records, parsed.rows().size()); assertEquals(records, report.records());
        assertEquals(samples, report.samples()); assertEquals((long)records * samples, report.genotypeCells());
        for (int i = 0; i < records; i++) assertEquals(formatOrder.get(i), parsed.rows().get(i).formats().stream().map(ShapeitBcf.Field::name).toList());
        assertEquals(genotypeValues, report.genotypeValues()); assertEquals(firstBits, report.firstAllelePhaseBits());
        assertEquals(ends, report.vectorEnds()); assertEquals(typedMissing, report.typedMissingGtValues()); assertEquals(0, report.rowsWithoutGt());
        assertEquals(!name.endsWith("-raw"), report.bgzf()); assertTrue(report.complete());
        assertEquals(hash(CAPTURE.get(name + ".bcf")), report.sourceSha256());
        if (name.startsWith("wide") || name.startsWith("v4")) {
            assertEquals(List.of(1, 2, 3, 1), parsed.rows().stream().map(r -> r.format("GT").type()).toList());
            assertEquals(17000, parsed.rows().get(2).alternates().size()); assertEquals(3, parsed.rows().get(3).format("GT").width());
            assertTrue(report.typedMissingGtValues() > 0); assertEquals("样本_α", parsed.header().samples().get(0));
        }
    }

    @Test void compressedAndRawTransportHaveIdenticalCompleteGtWireHashes() throws Exception {
        for (String name : List.of("initial", "cohort4", "cohort5", "wide", "v44", "v45")) {
            var compressed = read(name).report(); var raw = read(name + "-raw").report();
            assertEquals(compressed.gtWireSha256(), raw.gtWireSha256()); assertEquals(compressed.genotypeValues(), raw.genotypeValues());
        }
    }

    @Test void infoPaddingAndMissingMarkersStayPhysicalWhileGetterCardinalityMatchesHtslib() throws Exception {
        var parsed = read("wide"); var first = parsed.rows().get(0);
        assertArrayEquals(new int[]{2, ShapeitBcf.VECTOR_END, ShapeitBcf.VECTOR_END}, first.info().get("AC").integers());
        assertArrayEquals(new int[]{2}, first.info().get("AC").cellIntegers(0));
        assertArrayEquals(new int[]{0, ShapeitBcf.MISSING, ShapeitBcf.VECTOR_END}, first.info().get("VI").integers());
        assertArrayEquals(new int[]{0, ShapeitBcf.MISSING}, first.info().get("VI").cellIntegers(0));
        assertArrayEquals(new int[]{0x3e800000, 0x7f800001, 0x7f800002}, first.info().get("VP").floatBits());
        assertArrayEquals(new int[]{0x3e800000, 0x7f800001}, first.info().get("VP").cellFloatBits(0));
    }

    @Test void fieldsRowsAndHeadersCannotBeMutatedThroughCallbackResults() throws Exception {
        var parsed = read("wide"); var row = parsed.rows().get(0); var gt = row.format("GT");
        int original = gt.integers()[0]; byte[] bytes = gt.payload(); bytes[0] ^= 1;
        int[] values = gt.integers(); values[0] = 12345;
        assertEquals(original, gt.integers()[0]);
        assertThrows(UnsupportedOperationException.class, () -> parsed.header().samples().add("changed"));
        assertThrows(UnsupportedOperationException.class, () -> row.formats().clear());
        assertThrows(UnsupportedOperationException.class, () -> row.info().clear());
        assertThrows(UnsupportedOperationException.class, () -> row.alternates().clear());
    }

    @Test void wideValidDataRequiresExplicitCapacityRatherThanDroppedAlleles() throws Exception {
        Path source = fixture("wide");
        IOException refused = assertThrows(IOException.class, () -> ShapeitBcf.read(source, ShapeitBcf.Limits.defaults(), row -> {}, () -> {}));
        assertTrue(refused.getMessage().contains("ALT capacity"));
        assertTrue(ShapeitBcf.read(source, limits(), row -> {}, () -> {}).complete());
    }

    @Test void sourceRecordCellValueAndHeapBoundsFailInsteadOfTruncatingSuccess() throws Exception {
        var d = limits(); Path source = fixture("initial");
        var invalid = List.of(
            new ShapeitBcf.Limits(d.samples(),3,d.genotypeCells(),d.inputBytes(),d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),d.genotypeWidth(),d.valuesPerRecord()),
            new ShapeitBcf.Limits(d.samples(),d.records(),199,d.inputBytes(),d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),d.genotypeWidth(),d.valuesPerRecord()),
            new ShapeitBcf.Limits(d.samples(),d.records(),d.genotypeCells(),Files.size(source)-1,d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),d.genotypeWidth(),d.valuesPerRecord()),
            new ShapeitBcf.Limits(d.samples(),d.records(),d.genotypeCells(),d.inputBytes(),20,d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),d.genotypeWidth(),d.valuesPerRecord()),
            new ShapeitBcf.Limits(d.samples(),d.records(),d.genotypeCells(),d.inputBytes(),d.decodedBytes(),32,d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),d.genotypeWidth(),d.valuesPerRecord()),
            new ShapeitBcf.Limits(49,d.records(),d.genotypeCells(),d.inputBytes(),d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),d.genotypeWidth(),d.valuesPerRecord()),
            new ShapeitBcf.Limits(d.samples(),d.records(),d.genotypeCells(),d.inputBytes(),d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),1,d.valuesPerRecord()),
            new ShapeitBcf.Limits(d.samples(),d.records(),d.genotypeCells(),d.inputBytes(),d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),d.alternateAlleles(),d.formatFields(),d.genotypeWidth(),1));
        for (var limited : invalid) assertThrows(IOException.class, () -> ShapeitBcf.read(source, limited, row -> {}, () -> {}));
    }

    @Test void partialBodiesBadCrcMissingEofAndTrailingBytesCannotReturnACompleteReport() throws Exception {
        byte[] raw = CAPTURE.get("initial-raw.bcf"), compressed = CAPTURE.get("initial.bcf");
        var bad = new LinkedHashMap<String,byte[]>();
        bad.put("short-magic", Arrays.copyOf(raw, 3)); bad.put("short-body", Arrays.copyOf(raw, raw.length-1));
        bad.put("no-eof", Arrays.copyOf(compressed, compressed.length-28));
        byte[] crc = compressed.clone(); int block = ((crc[16]&255) | (crc[17]&255)<<8) + 1; crc[block-8] ^= 1; bad.put("crc", crc);
        byte[] tail = Arrays.copyOf(compressed, compressed.length+1); bad.put("tail", tail);
        for (var entry : bad.entrySet()) {
            Path path = temporary.resolve(entry.getKey()+".bcf"); Files.write(path, entry.getValue());
            assertThrows(IOException.class, () -> ShapeitBcf.read(path, limits(), row -> {}, () -> {}), entry.getKey());
        }
    }

    @Test void reservedGtValuesAndUnsupportedBcfEditionsAreNotAccepted() throws Exception {
        byte[] raw = CAPTURE.get("wide-raw.bcf").clone();
        // Independent HTSlib B/0/F/GT payload: GT need not be the final FORMAT field.
        byte[] genotype = HexFormat.of().parseHex("0301000004030581");
        int offset = -1;
        for (int i = 0; i <= raw.length - genotype.length; i++) {
            if (Arrays.mismatch(raw, i, i + genotype.length, genotype, 0, genotype.length) == -1) {
                assertEquals(-1, offset, "The oracle GT payload must identify a unique mutation site"); offset = i;
            }
        }
        assertTrue(offset >= 0); raw[offset + genotype.length - 1] = (byte)0x82;
        Path reserved = temporary.resolve("reserved.bcf"); Files.write(reserved, raw);
        IOException invalid = assertThrows(IOException.class, () -> ShapeitBcf.read(reserved, limits(), row -> {}, () -> {}));
        assertTrue(invalid.getMessage().contains("Reserved BCF integer encoding"));
        raw = CAPTURE.get("initial-raw.bcf").clone(); raw[4] = 1;
        Path old = temporary.resolve("old.bcf"); Files.write(old, raw);
        assertThrows(ShapeitBcf.Unavailable.class, () -> ShapeitBcf.read(old, limits(), row -> {}, () -> {}));
    }

    @Test void changedSourceAndCallbackFailureNeverReturnSuccess() throws Exception {
        Path source = fixture("initial-raw"); boolean[] changed = {false};
        IOException mutation = assertThrows(IOException.class, () -> ShapeitBcf.read(source, limits(), row -> {
            if (!changed[0]) { byte[] bytes = Files.readAllBytes(source); bytes[bytes.length-1] ^= 1; Files.write(source, bytes); changed[0] = true; }
        }, () -> {}));
        assertTrue(changed[0]); assertTrue(mutation.getMessage().contains("changed"));
        Path other = fixture("cohort4-raw");
        IOException error = assertThrows(IOException.class, () -> ShapeitBcf.read(other, limits(), row -> {throw new IOException("stop audited intake");}, () -> {}));
        assertEquals("stop audited intake", error.getMessage());
    }

    @Test void sitesOnlySourceIsParsedWithoutInventingSampleGenotypes() throws Exception {
        Path source = temporary.resolve("sites.bcf");
        try (var stream = getClass().getResourceAsStream("/org/jlinalg/cli/eagle-bcf/sites-bgzf.bcf")) {assertNotNull(stream);Files.write(source, stream.readAllBytes());}
        var report = ShapeitBcf.read(source, limits(), row -> assertNull(row.format("GT")), () -> {});
        assertEquals(0, report.samples()); assertEquals(0, report.genotypeCells()); assertEquals(report.records(), report.rowsWithoutGt()); assertTrue(report.complete());
    }

    private static int[] numbers(String text, boolean hex) {
        if (text.isEmpty()) return new int[0];
        return Arrays.stream(text.split(",")).mapToInt(s -> hex ? Integer.parseUnsignedInt(s,16) : Integer.parseInt(s)).toArray();
    }
    private static String hash(byte[] raw) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private static Map<String,byte[]> capture() {
        try (var stream = ShapeitBcfTest.class.getResourceAsStream("/org/jlinalg/cli/shapeit-bcf/raw-oracles-3.zip")) {
            assertNotNull(stream); var map = new LinkedHashMap<String,byte[]>(); int bytes = 0;
            try (var zip = new ZipInputStream(stream)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertTrue(map.size() < 1024); assertFalse(entry.isDirectory()); assertFalse(map.containsKey(entry.getName()));
                    var output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
                    while ((n = zip.read(buffer)) >= 0) {bytes += n; assertTrue(bytes <= 8388608); output.write(buffer,0,n);}
                    map.put(entry.getName(),output.toByteArray());
                }
            }
            var checks = new String(map.get("SHA256SUMS"), StandardCharsets.UTF_8).lines().toList(); assertEquals(map.size()-1,checks.size());
            for (String line : checks) assertEquals(line.substring(0,64),hash(map.get(line.substring(66))),line.substring(66));
            return Map.copyOf(map);
        } catch (Exception invalid) {throw new ExceptionInInitializerError(invalid);}
    }
}
