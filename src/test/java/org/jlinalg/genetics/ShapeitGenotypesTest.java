/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ShapeitGenotypesTest {
    private static final Map<String, byte[]> CAPTURE = capture();
    private static Stream<String> cases() { return Stream.of("4.1.3-bcf", "4.1.3-vcf", "5-c34d-bcf", "5-c34d-vcf"); }

    @ParameterizedTest @MethodSource("cases")
    void everyInitialTargetCellAndAlleleCountMatchesTheUnmodifiedOriginalReader(String name) {
        var grouped = initial(name);
        assertEquals(4, grouped.size()); int count = 0;
        for (var rows : grouped.values()) {
            assertEquals(50, rows.size());
            var decoded = ShapeitGenotypes.decodeDiploidStorageRow(wire(rows), 50);
            long reference = 0, alternate = 0, missing = 0;
            for (int i = 0; i < decoded.size(); i++) {
                var cell = decoded.get(i); var original = rows.get(i);
                assertEquals(Integer.parseInt(original[4]), cell.packed(), name + "/" + original[0] + "/" + original[1]);
                if (cell.missing()) missing++;
                else { alternate += cell.dosage(); reference += 2 - cell.dosage(); }
                count++;
            }
            assertEquals(Long.parseLong(rows.get(0)[5]), reference);
            assertEquals(Long.parseLong(rows.get(0)[6]), alternate);
            assertEquals(Long.parseLong(rows.get(0)[7]), missing);
        }
        assertEquals(200, count);
    }

    @ParameterizedTest @MethodSource("cases")
    void everyActualOriginalCohortPhasingOutputPassesTheCompleteDiploidAudit(String name) throws Exception {
        var input = sourceVcf(); var output = output(name);
        var audit = new ShapeitGenotypes.Audit(input.samples(), output.samples(), 4, 200);
        var grouped = initial(name); int variant = 0;
        for (var rows : grouped.values()) {
            audit.accept(input.identities().get(variant), wire(rows), output.identities().get(variant), output.calls().get(variant)); variant++;
        }
        var report = audit.finish();
        assertEquals(4, report.rows()); assertEquals(200, report.calls());
        assertEquals(200, report.readerMissingCalls() + report.heterozygousCalls() + report.homozygousCalls());
        assertTrue(report.observedInputAllelesIgnoredInMissingCalls() > 0);
        assertTrue(report.missingAlleleReadAsReferenceCalls() > 0);
        assertTrue(report.vectorEndInputCalls() > 0);
        assertEquals(64, report.inputSha256().length()); assertEquals(64, report.outputSha256().length());
        assertThrows(IllegalStateException.class, audit::finish);
    }

    @Test void vcfTokensMatchEveryActuallyDecodedOriginalWireValueIncludingPartialMissingAndPadding() throws Exception {
        var source = sourceVcf(); var rows = initial("4.1.3-vcf");
        for (int variant = 0; variant < 4; variant++) {
            var tokens = source.calls().get(variant); var raw = rows.get(Integer.toString(variant));
            for (int sample = 0; sample < 50; sample++) {
                var cell = ShapeitGenotypes.parseVcf(tokens.get(sample));
                int second = cell.values().size() == 1 ? ShapeitGenotypes.VECTOR_END : cell.values().get(1);
                assertEquals(Integer.parseInt(raw.get(sample)[2]), cell.values().get(0));
                assertEquals(Integer.parseInt(raw.get(sample)[3]), second);
            }
        }
        assertEquals(ShapeitGenotypes.parseVcf("0|1"), ShapeitGenotypes.parseVcf("00|0001"));
    }

    @Test void wireOnlyFirstAllelePhaseBitsArePreservedInsteadOfReconstructedThroughVcf() {
        var calls = List.of(new ShapeitGenotypes.WireCall(List.of(1, 2)), ShapeitGenotypes.parseVcf(".|0"));
        var result = ShapeitGenotypes.decodeDiploidStorageRow(calls, 2);
        assertFalse(result.get(0).missing()); assertTrue(result.get(0).missingAlleleReadAsReference());
        assertTrue(result.get(1).missing()); assertFalse(result.get(1).missingAlleleReadAsReference());
        assertNotEquals(result.get(0).packed(), result.get(1).packed());
    }

    @Test void changedObservedGenotypePermanentlyInvalidatesTheAudit() throws Exception {
        String name = "5-c34d-bcf"; var source = sourceVcf(); var emitted = output(name);
        var rows = initial(name).get("0"); var calls = new ArrayList<>(emitted.calls().get(0));
        var decoded = ShapeitGenotypes.decodeDiploidStorageRow(wire(rows), 50);
        int selected = 0; while (decoded.get(selected).missing()) selected++;
        calls.set(selected, decoded.get(selected).dosage() == 0 ? "1|1" : "0|0");
        var audit = new ShapeitGenotypes.Audit(source.samples(), emitted.samples(), 4, 200);
        assertThrows(IllegalArgumentException.class, () -> audit.accept(source.identities().get(0), wire(rows), emitted.identities().get(0), calls));
        assertThrows(IllegalStateException.class, audit::finish);
        assertThrows(IllegalStateException.class, () -> audit.accept(source.identities().get(0), wire(rows), emitted.identities().get(0), emitted.calls().get(0)));
    }

    @Test void rowAndCellCapacityFailuresCannotPublishTruncatedSuccess() throws Exception {
        String name = "4.1.3-bcf"; var source = sourceVcf(); var emitted = output(name); var rows = initial(name);
        for (var audit : List.of(new ShapeitGenotypes.Audit(source.samples(), emitted.samples(), 3, 200),
                new ShapeitGenotypes.Audit(source.samples(), emitted.samples(), 4, 199))) {
            for (int variant = 0; variant < 3; variant++) audit.accept(source.identities().get(variant), wire(rows.get(Integer.toString(variant))), emitted.identities().get(variant), emitted.calls().get(variant));
            assertThrows(IllegalArgumentException.class, () -> audit.accept(source.identities().get(3), wire(rows.get("3")), emitted.identities().get(3), emitted.calls().get(3)));
            assertThrows(IllegalStateException.class, audit::finish);
        }
    }

    @Test void exactSampleAndVariantIdentityAreRequiredAndEmptyOutputCannotComplete() {
        assertThrows(IllegalArgumentException.class, () -> new ShapeitGenotypes.Audit(List.of("a", "b"), List.of("b", "a"), 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new ShapeitGenotypes.Audit(List.of("a", "a"), List.of("a", "a"), 1, 2));
        var audit = new ShapeitGenotypes.Audit(List.of("样本α"), List.of("样本α"), 1, 1);
        assertThrows(IllegalArgumentException.class, () -> audit.accept("20:1000:A:C", List.of(ShapeitGenotypes.parseVcf("0/1")), "20:1000:A:G", List.of("0|1")));
        assertThrows(IllegalStateException.class, audit::finish);
        var empty = new ShapeitGenotypes.Audit(List.of("a"), List.of("a"), 1, 1);
        assertThrows(IllegalStateException.class, empty::finish);
    }

    @Test void completedDiploidOutputMustBeObservedAndPhased() {
        for (String output : List.of("0/1", "./.", "0|.", "0", "1", ".")) {
            var audit = new ShapeitGenotypes.Audit(List.of("a"), List.of("a"), 1, 1);
            assertThrows(IllegalArgumentException.class, () -> audit.accept("v", List.of(ShapeitGenotypes.parseVcf("./.")), "v", List.of(output)));
            assertThrows(IllegalStateException.class, audit::finish);
        }
    }

    @Test void rawCellsAndRowsAreImmutableAndUnsupportedStorageIsRefusedExplicitly() {
        var values = new ArrayList<>(List.of(2, 4)); var call = new ShapeitGenotypes.WireCall(values); values.set(1, 2);
        assertEquals(List.of(2, 4), call.values()); assertThrows(UnsupportedOperationException.class, () -> call.values().add(2));
        var decoded = ShapeitGenotypes.decodeDiploidStorageRow(List.of(call), 1);
        assertThrows(UnsupportedOperationException.class, () -> decoded.clear());
        assertThrows(IllegalArgumentException.class, () -> ShapeitGenotypes.decodeDiploidStorageRow(List.of(ShapeitGenotypes.parseVcf("1")), 1));
        assertThrows(IllegalArgumentException.class, () -> ShapeitGenotypes.decodeDiploidStorageRow(List.of(call), 0));
        for (String token : List.of("", "0/1/0", "0|1/0", "-1/0", "2/0", "0/", "0||1", "A", "+1", "2147483648"))
            assertThrows(IllegalArgumentException.class, () -> ShapeitGenotypes.parseVcf(token));
        assertThrows(IllegalArgumentException.class, () -> new ShapeitGenotypes.WireCall(List.of(ShapeitGenotypes.VECTOR_END, 2)));
    }

    private record Vcf(List<String> samples, List<String> identities, List<List<String>> calls) { }
    private static Vcf sourceVcf() throws IOException {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(CAPTURE.get("inputs/wire.vcf.gz")))) {
            return vcf(new String(gzip.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    private static Vcf output(String name) { return vcf(new String(CAPTURE.get("captures/" + name + ".phased.vcf"), StandardCharsets.UTF_8)); }
    private static Vcf vcf(String text) {
        List<String> samples = null; var identities = new ArrayList<String>(); var calls = new ArrayList<List<String>>();
        for (String line : text.lines().toList()) {
            if (line.startsWith("#CHROM\t")) { var fields = line.split("\t"); samples = List.of(Arrays.copyOfRange(fields, 9, fields.length)); }
            else if (!line.startsWith("#")) {
                var fields = line.split("\t"); assertEquals(59, fields.length);
                identities.add(String.join(":", fields[0], fields[1], fields[2], fields[3], fields[4]));
                int gt = Arrays.asList(fields[8].split(":")).indexOf("GT"); assertTrue(gt >= 0);
                var row = new ArrayList<String>(); for (int i = 9; i < fields.length; i++) row.add(fields[i].split(":", -1)[gt]); calls.add(List.copyOf(row));
            }
        }
        assertNotNull(samples); assertEquals(4, identities.size());
        return new Vcf(samples, List.copyOf(identities), List.copyOf(calls));
    }
    private static Map<String, List<String[]>> initial(String name) {
        var grouped = new LinkedHashMap<String, List<String[]>>();
        new String(CAPTURE.get("captures/reader-" + name + ".tsv"), StandardCharsets.UTF_8).lines().skip(1).forEach(line -> {
            var fields = line.split("\t"); assertEquals(8, fields.length); grouped.computeIfAbsent(fields[0], k -> new ArrayList<>()).add(fields);
        });
        return grouped;
    }
    private static List<ShapeitGenotypes.WireCall> wire(List<String[]> rows) {
        return rows.stream().map(r -> new ShapeitGenotypes.WireCall(List.of(Integer.parseInt(r[2]), Integer.parseInt(r[3])))).toList();
    }
    private static Map<String, byte[]> capture() {
        try (var source = ShapeitGenotypesTest.class.getResourceAsStream("/org/jlinalg/genetics/shapeit-reference/initial-reader.zip")) {
            assertNotNull(source); var result = new LinkedHashMap<String, byte[]>(); int bytes = 0;
            try (var zip = new ZipInputStream(source)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertFalse(entry.isDirectory()); assertFalse(result.containsKey(entry.getName())); assertTrue(result.size() < 1024);
                    var out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
                    while ((n = zip.read(buffer)) >= 0) { bytes += n; assertTrue(bytes <= 4194304); out.write(buffer, 0, n); }
                    result.put(entry.getName(), out.toByteArray());
                }
            }
            var checks = new String(result.get("SHA256SUMS"), StandardCharsets.UTF_8).lines().toList();
            assertEquals(result.size() - 1, checks.size());
            for (String line : checks) {
                String name = line.substring(66); byte[] raw = result.get(name); assertNotNull(raw, name);
                assertEquals(line.substring(0, 64), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)), name);
            }
            return Map.copyOf(result);
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
}
