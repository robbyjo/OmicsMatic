/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonIndexRecipeTest {
    @TempDir Path temporary;
    static Map<String, Object> request(Path fasta, Path decoys, String version, boolean keep, boolean gencode) {
        var raw = new LinkedHashMap<String, Object>(); raw.put("schema_version", 1); raw.put("profile", "linux-x86_64-salmon-" + version);
        raw.put("fasta", fasta.toString()); raw.put("encoding", "PLAIN_UTF8"); raw.put("headers", gencode ? "GENCODE_FIRST_PIPE" : "SOURCE_WHITESPACE_TOKEN");
        raw.put("decoys", decoys == null ? null : decoys.toString()); raw.put("k", 15); raw.put("threads", 2); raw.put("keep_duplicates", keep);
        raw.put("keep_fixed_fasta", !version.equals("1.0.0")); raw.put("poly_a", "SOURCE_DEFAULT"); raw.put("limits", Map.of()); return raw;
    }
    @Test void originalArgumentsKeepEveryDeclaredChoiceWithoutOpaqueFlags() throws Exception {
        var raw = base(); raw.put("headers", "GENCODE_FIRST_PIPE"); raw.put("decoys", temporary.resolve("decoys.txt").toString()); raw.put("keep_duplicates", true); raw.put("poly_a", "DISABLE_CLIPPING");
        var recipe = read(raw); assertEquals(List.of("/managed/bin/salmon", "--no-version-check", "index", "-t", "/owned/input.fa", "-i", "/owned/index", "-k", "15", "-p", "2", "-d", "/owned/decoys.txt", "--gencode", "--keepDuplicates", "--keepFixedFasta", "--no-clip"), recipe.arguments("/managed/bin/salmon", "/owned/input.fa", "/owned/decoys.txt", "/owned/index"));
    }
    @ParameterizedTest @ValueSource(strings={"1.0.0", "1.1.0", "1.5.2", "1.8.0", "1.9.0"})
    void exactSupportedEditionsDoNotInferMissingOlderMetadata(String version) throws Exception {
        var raw = request(temporary.resolve("fa"), null, version, false, false); var recipe = read(raw);
        assertEquals(version, recipe.version()); assertEquals(!version.equals("1.0.0"), recipe.arguments("/h/bin/salmon", "/w/fa", null, "/w/i").contains("--keepFixedFasta"));
    }
    @ParameterizedTest @ValueSource(strings={"1.0.0", "1.1.0", "1.5.2"})
    void unavailableNoClipCannotBeSilentlyOmitted(String version) throws Exception {
        var raw = request(temporary.resolve("fa"), null, version, false, false); raw.put("poly_a", "DISABLE_CLIPPING"); assertThrows(SalmonRuntime.Unavailable.class, () -> read(raw));
    }
    @Test void unavailableFixedFastaCannotBeSilentlyOmitted() throws Exception {
        var raw = request(temporary.resolve("fa"), null, "1.0.0", false, false); raw.put("keep_fixed_fasta", true); assertThrows(SalmonRuntime.Unavailable.class, () -> read(raw));
    }
    @ParameterizedTest @ValueSource(strings={"salmon-1", "linux-x86_64-salmon-1.10.0", "linux-x86_64-salmon-1.9"})
    void unknownAndPartialEditionsSkipInsteadOfSelectingFallback(String profile) throws Exception { var raw = base(); raw.put("profile", profile); assertThrows(SalmonRuntime.Unavailable.class, () -> read(raw)); }
    @ParameterizedTest @ValueSource(strings={"extra_flags", "sparse", "retain_decoys", "seed"})
    void unknownRequestedSettingIsNotDiscarded(String key) throws Exception { var raw = base(); raw.put(key, "unsupported"); assertThrows(IOException.class, () -> read(raw)); }
    @Test void wrongTypesEvenKAndFractionalThreadsAreRejected() throws Exception {
        for (var change : List.of(Map.of("k", 16), Map.of("threads", 1.5), Map.of("keep_duplicates", "false"), Map.of("schema_version", 2))) {
            var raw = base(); raw.putAll(change); assertThrows(IOException.class, () -> read(raw));
        }
    }
    @Test void unknownOrImpossibleCapacitiesFailBeforeOriginalExecution() throws Exception {
        for (var limits : List.of(Map.of("input_btyes", 10), Map.of("line_characters", 2), Map.of("retained_bytes", 262144), Map.of("positions", 0))) {
            var raw = base(); raw.put("limits", limits); assertThrows(Exception.class, () -> read(raw));
        }
    }
    @Test void pathsAreNeverShellFragmentsOrRelativeSourceLookups() throws Exception {
        var raw = base(); raw.put("fasta", "relative.fa"); assertThrows(IOException.class, () -> read(raw)); var request = read(base());
        for (String path : List.of("/tmp/a b", "/tmp/../index", "/tmp/./index", "/tmp/x;echo", "/tmp/x\n")) assertThrows(IOException.class, () -> request.arguments("/h/salmon", path, null, "/w/index"));
        assertThrows(IOException.class, () -> request.arguments("/h/salmon", "/w/fa", "/w/unrequested-decoys", "/w/i"));
    }
    @Test void changedCompleteRecipeIsRejected() throws Exception { var request = read(base()); Files.writeString(request.source(), "{}\n"); assertThrows(IOException.class, () -> request.verify(() -> { })); }
    @Test void cancellationDoesNotProduceARecipe() throws Exception {
        var request = read(base()); assertThrows(CancellationException.class, () -> SalmonIndexRecipe.read(request.source(), () -> { throw new CancellationException(); }));
    }
    @Test void portableRecipePreservesOriginalLinuxPathsAsTextWithoutOpeningThem() throws Exception {
        var raw = base(); raw.put("fasta", "/no-such-producer/café data/reference.fa"); raw.put("decoys", "/no-such-producer/name:with:colons/decoys.txt");
        Path source = temporary.resolve("captured.json").toAbsolutePath(); Files.writeString(source, NextflowJson.json(raw)); var result = SalmonIndexRecipe.readCaptured(source, () -> { });
        assertEquals(raw.get("fasta"), result.fastaSource()); assertEquals(raw.get("decoys"), result.decoySource());
    }
    private Map<String, Object> base() { return request(temporary.resolve("fa"), null, "1.9.0", false, false); }
    private SalmonIndexRecipe.Request read(Map<String, Object> raw) throws Exception {
        Path file = Files.createTempFile(temporary, "recipe-", ".json").toAbsolutePath(); Files.writeString(file, NextflowJson.json(raw)); return SalmonIndexRecipe.read(file, () -> { });
    }
}
