/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SalmonIndexAuditTest {
    @TempDir Path temporary;
    private static final String RESOURCE = "/org/jlinalg/cli/salmon-index-inputs/";
    static Stream<String> cases() { return Stream.of("1.0.0", "1.1.0", "1.5.2", "1.8.0", "1.9.0").flatMap(v -> Stream.of("mixed-default", "mixed-keep", "gencode-keep", "ambiguous-repeat-a", "ambiguous-repeat-b").map(c -> v + "/" + c)); }
    @ParameterizedTest @MethodSource("cases")
    void completeOriginalInputIndexAndDeclaredSourcePopulationRemainDistinct(String name) throws Exception {
        var fixture = fixture(name); var result = SalmonIndexAudit.read(fixture.request(), fixture.inputs(), fixture.index(), () -> { });
        Map<?, ?> expected = expected(name);
        assertEquals(NextflowFiles.integer(expected.get("raw_references"), 1, 100), result.ledger().inputs().size());
        assertEquals(expected.get("retained_names"), result.tables().references().stream().map(r -> r.name()).toList());
        assertEquals(expected.get("original_lengths"), result.tables().references().stream().map(r -> r.originalLength()).toList());
        assertEquals(expected.get("normalized_lengths"), result.tables().references().stream().map(r -> r.normalizedLength()).toList());
        assertEquals(NextflowFiles.integer(expected.get("positions"), 1, 100000), result.tables().positions());
        assertEquals(!Boolean.TRUE.equals(expected.get("source_population_discrepancy")), result.ledger().decoys().matchesDeclaredPopulation());
        var output = new ByteArrayOutputStream(); var pin = SalmonIndexAudit.ledger(result, output, 1000000, () -> { }); byte[] raw = output.toByteArray();
        assertEquals(new NextflowFiles.Pin(raw.length, NextflowFiles.sha(raw)), pin); assertEquals(result.ledger().inputs().size(), new String(raw, StandardCharsets.UTF_8).lines().count());
        if (name.contains("mixed-")) { assertTrue(result.ledger().inputs().stream().anyMatch(r -> r.input().indexName().equals("allA") && r.retained() == null)); assertEquals(List.of("shortAtK"), result.ledger().decoys().disagreements()); }
        if (name.contains("gencode-")) { assertEquals(List.of("ENST3.3"), result.ledger().decoys().disagreements()); assertTrue(new String(raw, StandardCharsets.UTF_8).contains("ENST1.1|GENE1")); }
    }
    @Test void differentCapturedRawSequenceLengthCannotBorrowAnIndexIdentity() throws Exception {
        var fixture = fixture("1.9.0/mixed-default"); Path raw = fixture.inputs().fasta(); Files.writeString(raw, Files.readString(raw).replace(">longA\n", ">longA\nA"));
        var changed = SalmonReferences.inspect(raw, fixture.request().encoding(), fixture.request().headers(), fixture.inputs().decoyFile(), fixture.request().limits().references(), () -> { });
        assertThrows(IOException.class, () -> SalmonIndexAudit.read(fixture.request(), changed, fixture.index(), () -> { }));
    }
    @Test void wrongKOrDuplicateMetadataCannotAdmitAnIndex() throws Exception {
        var fixture = fixture("1.9.0/mixed-default"); Path info = fixture.index().resolve("info.json");
        Files.writeString(info, Files.readString(info).replace("\"k\": 15", "\"k\": 17")); assertThrows(IOException.class, () -> SalmonIndexAudit.read(fixture.request(), fixture.inputs(), fixture.index(), () -> { }));
    }
    @Test void lateTableJunkAndLedgerCapacityNeverYieldACompletePrefix() throws Exception {
        var fixture = fixture("1.9.0/mixed-default"); var audit = SalmonIndexAudit.read(fixture.request(), fixture.inputs(), fixture.index(), () -> { });
        assertThrows(IOException.class, () -> SalmonIndexAudit.ledger(audit, OutputStream.nullOutputStream(), 10, () -> { }));
        Files.write(fixture.index().resolve("reflengths.bin"), new byte[]{0}, java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> SalmonIndexAudit.read(fixture.request(), fixture.inputs(), fixture.index(), () -> { }));
    }
    @Test void cancelledCompleteReadAndLedgerRemainUnadmitted() throws Exception {
        var fixture = fixture("1.9.0/mixed-default"); var audit = SalmonIndexAudit.read(fixture.request(), fixture.inputs(), fixture.index(), () -> { });
        assertThrows(CancellationException.class, () -> SalmonIndexAudit.read(fixture.request(), fixture.inputs(), fixture.index(), () -> { throw new CancellationException(); }));
        assertThrows(CancellationException.class, () -> SalmonIndexAudit.ledger(audit, OutputStream.nullOutputStream(), 1000000, () -> { throw new CancellationException(); }));
    }
    private record Fixture(SalmonIndexRecipe.Request request, SalmonReferences.Context inputs, Path index) { }
    private Fixture fixture(String name) throws Exception {
        Path area = Files.createTempDirectory(temporary, "original-").toAbsolutePath();
        try (var zip = new ZipInputStream(getClass().getResourceAsStream(RESOURCE + "original-index-inputs.zip"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.getName().startsWith(name + "/")) { zip.closeEntry(); continue; }
                String relative = NextflowFiles.member(entry.getName().substring(name.length() + 1)); Path file = area.resolve(relative); Files.createDirectories(file.getParent()); Files.write(file, zip.readAllBytes()); zip.closeEntry();
            }
        }
        String version = name.substring(0, name.indexOf('/')); Path decoys = Files.exists(area.resolve("inputs/decoys.txt")) ? area.resolve("inputs/decoys.txt") : null;
        Map<?, ?> process = NextflowFiles.object(NextflowFiles.json(area.resolve("index-process/process.json"), 1048576));
        List<?> command = NextflowFiles.list(process.get("command"));
        Path recipe = area.resolve("recipe.json"); Files.writeString(recipe, NextflowJson.json(SalmonIndexRecipeTest.request(area.resolve("inputs/reference.fa"), decoys, version, command.contains("--keepDuplicates"), command.contains("--gencode"))));
        var request = SalmonIndexRecipe.read(recipe, () -> { }); var inputs = SalmonReferences.inspect(request.fasta(), request.encoding(), request.headers(), decoys, request.limits().references(), () -> { }); return new Fixture(request, inputs, area.resolve("index"));
    }
    private Map<?, ?> expected(String name) throws Exception {
        Map<?, ?> proof; try (var input = getClass().getResourceAsStream(RESOURCE + "provenance.json")) { proof = NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(input.readAllBytes()))); }
        for (Object raw : NextflowFiles.list(proof.get("cases"))) { Map<?, ?> row = NextflowFiles.object(raw); if (name.equals(row.get("name"))) return row; } throw new AssertionError("Missing original case");
    }
}
