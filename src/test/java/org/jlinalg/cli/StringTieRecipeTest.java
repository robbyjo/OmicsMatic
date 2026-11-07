/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class StringTieRecipeTest {
    @TempDir Path temporary;
    private int sequence;
    @ParameterizedTest @EnumSource(StringTieRecipe.Mode.class)
    void allModesHaveExplicitSourceRolesAndStableArguments(StringTieRecipe.Mode mode)throws Exception {
        var document = recipe(mode); var request = read(document); var command = request.arguments("/exact/bin/stringtie", "/captured");
        assertEquals("3.0.3", request.version()); assertEquals(mode, request.mode()); assertEquals(List.of("-p", "1", "-o", "transcripts.gtf"), command.subList(command.size() - (mode == StringTieRecipe.Mode.MERGE ? 5 : 4), command.size() - (mode == StringTieRecipe.Mode.MERGE ? 1 : 0)));
        assertEquals(command, request.arguments("/exact/bin/stringtie", "/captured"));
        assertEquals(mode == StringTieRecipe.Mode.MERGE, command.contains("--merge")); assertEquals(mode == StringTieRecipe.Mode.ESTIMATE, command.contains("-B"));
        assertEquals(mode == StringTieRecipe.Mode.ESTIMATE, command.contains("-e")); assertEquals(mode != StringTieRecipe.Mode.MERGE, StringTieInputs.members(request).containsKey("inputs/reads.bam"));
    }
    @ParameterizedTest @ValueSource(strings={"mode", "bam_policies", "population", "limits", "minfpkm", "threshold-string", "fraction", "emptymerge", "extra", "unreported"})
    void invalidOrInferredSettingsNeverProduceACommand(String variant)throws Exception {
        var document = recipe(variant.equals("emptymerge") ? StringTieRecipe.Mode.MERGE : StringTieRecipe.Mode.ASSEMBLE); var parameters = parameters();
        switch (variant) {
            case "mode" -> document.put("mode", "QUANTIFY");
            case "bam_policies" -> document.put("bam_policies", null);
            case "population" -> document.put("population", "COMPLETE");
            case "limits" -> document.put("limits", Map.of("silent_filter", 1));
            case "minfpkm" -> { parameters.put("minimum_FPKM", 1); document.put("parameters", parameters); }
            case "threshold-string" -> { parameters.put("minimum_coverage", "-rf"); document.put("parameters", parameters); }
            case "fraction" -> { parameters.put("minimum_isoform_fraction", 2); document.put("parameters", parameters); }
            case "emptymerge" -> document.put("merge_inputs", List.of());
            case "extra" -> document.put("flags", List.of("-rf"));
            case "unreported" -> document.put("profile", "linux-x86_64-stringtie-latest");
            default -> throw new AssertionError();
        }
        assertThrows(IOException.class, () -> read(document));
    }
    @Test void historicalUnsupportedStrandIsSkippedRatherThanReplaced()throws Exception {
        var document = recipe(StringTieRecipe.Mode.ASSEMBLE); document.put("profile", "linux-x86_64-stringtie-1.3.0"); document.put("strand", "FR_FIRSTSTRAND");
        assertThrows(StringTieRuntime.Unavailable.class, () -> read(document));
        document.put("profile", "linux-x86_64-stringtie-3.0.0"); var p = parameters(); p.put("trimming", "DISABLE"); p.put("minimum_length", 150); p.put("minimum_isoform_fraction", new java.math.BigDecimal("0.3")); document.put("parameters", p);
        var command = read(document).arguments("/exact/bin/stringtie", "/captured"); assertTrue(command.containsAll(List.of("--rf", "-t", "-m", "150", "-f", "0.3"))); assertFalse(command.contains("-rf"));
    }
    @Test void exactThresholdTokensAndSmallRequestedCapacitiesRemainExplicit()throws Exception {
        var document = recipe(StringTieRecipe.Mode.MERGE); var p = parameters(); p.put("minimum_FPKM", new java.math.BigDecimal("0.000000001")); document.put("parameters", p); document.put("limits", Map.of("processes", 1));
        var request = read(document); assertEquals("0.000000001", request.parameters().get("minimum_FPKM")); assertEquals(1, request.capacity("processes"));
        assertEquals(1, StringTieRecipe.process(request.limits()).processes()); assertNull(request.parameters().get("minimum_coverage"));
    }
    @Test void cliMakesMissingDependenciesAndBadRequestsProminent()throws Exception {
        var bytes = new ByteArrayOutputStream(); var errors = new ByteArrayOutputStream(); var out = new PrintStream(bytes, true, StandardCharsets.UTF_8); var err = new PrintStream(errors, true, StandardCharsets.UTF_8);
        assertEquals(0, StringTieToolsCli.run(new String[]{"--help"}, out, err)); assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("MERGE"));
        assertEquals(0, StringTieToolsCli.run(new String[]{"--runtime-profiles"}, out, err));
        assertEquals(3, StringTieToolsCli.run(new String[]{"--recipe", "not-opened.json", "--out", temporary.resolve("out").toString()}, out, err));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("SKIP3")); assertTrue(errors.toString(StandardCharsets.UTF_8).contains("skip dependent analysis")); assertFalse(Files.exists(temporary.resolve("out")));
        assertEquals(2, StringTieToolsCli.run(new String[]{"--flags", "-rf"}, out, err)); assertTrue(errors.toString(StandardCharsets.UTF_8).contains("FAIL2"));
    }
    @Test void blankReferenceRowsAreNotSilentlyDropped()throws Exception {
        var request = read(recipe(StringTieRecipe.Mode.MERGE)); Path root = temporary.resolve("input-root"); Files.createDirectories(root.resolve("inputs")); Files.writeString(root.resolve("inputs/reference.sizes"), "chrA\t12000\n\nchrB\t10000\n");
        assertThrows(IOException.class, () -> StringTieInputs.audit(root, request, "/opaque", () -> { }));
    }
    @Test void cancellationAndUnsafeProducerPathsFailExplicitly()throws Exception {
        Path source = temporary.resolve("cancel.json"); NextflowFiles.writeJson(source, recipe(StringTieRecipe.Mode.MERGE));
        assertThrows(CancellationException.class, () -> StringTieRecipe.read(source, true, () -> { throw new CancellationException("controlled"); }));
        for (String value : List.of("relative", "/tmp/../input", "/tmp//input", "/tmp/input\nflags")) assertThrows(IOException.class, () -> StringTieRecipe.opaque(value));
    }
    private StringTieRecipe.Request read(Map<String, Object> value)throws Exception { Path source = temporary.resolve("recipe-" + sequence++ + ".json"); NextflowFiles.writeJson(source, value); return StringTieRecipe.read(source, true, () -> { }); }
    static Map<String, Object> recipe(StringTieRecipe.Mode mode) {
        var value = new LinkedHashMap<String, Object>(); value.put("schema_version", 1); value.put("profile", "linux-x86_64-stringtie-3.0.3"); value.put("mode", mode.name()); value.put("bam", mode == StringTieRecipe.Mode.MERGE ? null : "/original/reads.bam"); value.put("reference_sizes", "/original/reference.sizes");
        value.put("guide", mode == StringTieRecipe.Mode.ESTIMATE ? "/original/guide.gtf" : null); value.put("merge_inputs", mode == StringTieRecipe.Mode.MERGE ? List.of("/original/assembly.gtf") : List.of()); value.put("genome_build", "synthetic-not-human"); value.put("strand", "UNSTRANDED");
        value.put("bam_policies", mode == StringTieRecipe.Mode.MERGE ? null : Map.of("missing_NH", "ERROR", "missing_XS", "ERROR", "sort_header", "REQUIRE_COORDINATE")); value.put("population", mode == StringTieRecipe.Mode.ESTIMATE ? "COMPLETE" : null); value.put("threads", 1); value.put("parameters", parameters()); value.put("limits", Map.of()); return value;
    }
    static Map<String, Object> parameters() { var p = new LinkedHashMap<String, Object>(); p.put("trimming", "ORIGINAL_DEFAULT"); for (String n : List.of("minimum_length", "minimum_isoform_fraction", "minimum_coverage", "minimum_FPKM", "minimum_TPM")) p.put(n, null); return p; }
}
