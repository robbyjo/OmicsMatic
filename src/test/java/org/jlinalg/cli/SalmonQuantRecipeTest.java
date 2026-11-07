/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonQuantRecipeTest {
    @TempDir Path temporary;
    private Map<String, Object> base() {
        var raw = new LinkedHashMap<String, Object>(); raw.put("schema_version", 1); raw.put("profile", "linux-x86_64-salmon-1.9.0"); raw.put("mode", "PAIRED_FASTQ"); raw.put("index_bundle", temporary.resolve("index").toString());
        raw.put("reads", Map.of("first", temporary.resolve("r1.fastq").toString(), "second", temporary.resolve("r2.fastq").toString(), "alphabet", "IUPAC_DNA_RNA_OR_X", "pairing", "TERMINAL_MATE_SUFFIX"));
        raw.put("alignments", null); raw.put("reference", null); raw.put("library_type", "ISR"); raw.put("optimizer", "EM"); raw.put("sequence_bias", true); raw.put("gc_bias", true); raw.put("position_bias", true);
        raw.put("bootstraps", 50); raw.put("minimum_assigned_fragments", 10); raw.put("fragment_prior", null); raw.put("threads", 2); raw.put("population", "REQUIRE_DECLARED_NONDECOYS"); raw.put("limits", Map.of()); return raw;
    }
    @Test void allMappingModelAndBiasChoicesAreExplicitOriginalArguments() throws Exception {
        var request = read(base()); assertEquals(List.of("/h/bin/salmon", "--no-version-check", "quant", "-i", "/w/index", "-l", "ISR", "-1", "/w/r1.fastq", "-2", "/w/r2.fastq", "--validateMappings", "--useEM", "--seqBias", "--gcBias", "--posBias", "--numBootstraps", "50", "--minAssignedFrags", "10", "-p", "2", "-o", "/w/output"), request.arguments("/h/bin/salmon", "/w/index", "/w/r1.fastq", "/w/r2.fastq", null, null, "/w/output"));
    }
    @Test void singleEndNeedsFragmentPriorRatherThanReadLengthGuess() throws Exception {
        var raw = base(); raw.put("mode", "SINGLE_FASTQ"); raw.put("library_type", "SR"); var reads = new LinkedHashMap<Object, Object>(NextflowFiles.object(raw.get("reads"))); reads.put("second", null); raw.put("reads", reads);
        assertThrows(IOException.class, () -> read(raw)); raw.put("fragment_prior", Map.of("mean", 250, "sd", 25)); var request = read(raw);
        var command = request.arguments("/h/bin/salmon", "/w/index", "/w/read.fastq", null, null, null, "/w/output"); assertTrue(command.contains("-r")); assertTrue(command.contains("--fldMean")); assertFalse(command.contains("-1"));
    }
    @Test void transcriptomeAlignmentModeUsesItsFastaAndCannotDiscardMappingReads() throws Exception {
        var raw = alignments(); raw.put("reads", base().get("reads")); assertThrows(IOException.class, () -> read(raw)); raw.put("reads", null); var request = read(raw);
        var command = request.arguments("/h/bin/salmon", null, null, null, "/w/alignment.bam", "/w/transcripts.fa", "/w/output");
        assertTrue(command.contains("-a")); assertTrue(command.contains("-t")); assertFalse(command.contains("-i")); assertFalse(command.contains("--validateMappings"));
    }
    @Test void sourceRuntimePopulationMustBeExplicitAndIsNotAnAlignmentPolicy() throws Exception {
        var raw = base(); raw.put("population", "ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER"); assertEquals(SalmonQuantRecipe.Population.ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER, read(raw).population());
        raw = alignments(); raw.put("population", "ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER"); var invalid = raw; assertThrows(IOException.class, () -> read(invalid));
    }
    @ParameterizedTest @ValueSource(strings={"U", "SF", "SR", "SAF Pattern", "paired-end reverse stranded"})
    void pairedLibraryRequiresActualOriginalPairedCode(String value) throws Exception { var raw = base(); raw.put("library_type", value); assertThrows(IOException.class, () -> read(raw)); }
    @Test void vbZeroBootstrapAndDisabledBiasArePreservedWithoutInventedOutputs() throws Exception {
        var raw = base(); raw.put("optimizer", "VARIATIONAL_BAYES"); raw.put("bootstraps", 0); raw.put("sequence_bias", false); raw.put("gc_bias", false); raw.put("position_bias", false); var request = read(raw);
        var command = request.arguments("/h/salmon", "/w/i", "/w/a", "/w/b", null, null, "/w/o"); assertTrue(command.contains("--useVBOpt")); assertFalse(command.contains("--numBootstraps")); assertFalse(command.contains("--posBias"));
    }
    @ParameterizedTest @ValueSource(strings={"extra_flags", "gibbs_samples", "single_cell", "infer_library_type", "fpkm"})
    void noOpaqueOrUnimplementedRequestCanBeDiscarded(String key) throws Exception { var raw = base(); raw.put(key, true); assertThrows(IOException.class, () -> read(raw)); }
    @Test void unknownEditionDoesNotSelectFallback() throws Exception { var raw = base(); raw.put("profile", "linux-x86_64-salmon-1.9"); assertThrows(SalmonRuntime.Unavailable.class, () -> read(raw)); }
    @Test void integerCounterCapacitiesAndOperatingSystemLimitsAreCheckedBeforeExecution() throws Exception {
        for (var change : List.of(Map.of("bootstraps", -1), Map.of("bootstraps", 1.5), Map.of("minimum_assigned_fragments", 0), Map.of("limits", Map.of("poll_millis", 1001)), Map.of("limits", Map.of("references", 100000000000L)), Map.of("limits", Map.of("unknown", 1)))) {
            var raw = base(); raw.putAll(change); assertThrows(IOException.class, () -> read(raw));
        }
    }
    @Test void nonpositiveFragmentPriorFailsWithoutOriginalRun() throws Exception { var raw = base(); raw.put("fragment_prior", Map.of("mean", 250, "sd", 0)); assertThrows(IOException.class, () -> read(raw)); }
    @Test void wrongPreparedInputRolesCannotBecomeAnOriginalCommand() throws Exception {
        var request = read(base()); assertThrows(IOException.class, () -> request.arguments("/h/salmon", "/w/i", "/w/a", null, null, null, "/w/o"));
        assertThrows(IOException.class, () -> request.arguments("/h/salmon", "/w/i", "/w/a", "/w/b", "/w/unrequested.sam", null, "/w/o"));
    }
    @Test void completeChangedRecipeIsRejectedAndPortableProducerPathsRemainText() throws Exception {
        var raw = alignments(); raw.put("alignments", Map.of("path", "/missing producer/name:alignment.bam", "format", "BAM", "layout", "PAIRED")); raw.put("reference", Map.of("path", "/missing producer/transcripts.fa", "encoding", "PLAIN_UTF8", "headers", "SOURCE_WHITESPACE_TOKEN"));
        Path path = write(raw); var result = SalmonQuantRecipe.readCaptured(path, () -> { }); assertEquals("/missing producer/name:alignment.bam", result.alignments().path()); Files.writeString(path, "{}\n"); assertThrows(IOException.class, () -> result.verify(() -> { }));
    }
    private Map<String, Object> alignments() { var raw = base(); raw.put("mode", "TRANSCRIPTOME_ALIGNMENTS"); raw.put("index_bundle", null); raw.put("reads", null); raw.put("alignments", Map.of("path", temporary.resolve("alignments.bam").toString(), "format", "BAM", "layout", "PAIRED")); raw.put("reference", Map.of("path", temporary.resolve("transcripts.fa").toString(), "encoding", "PLAIN_UTF8", "headers", "SOURCE_WHITESPACE_TOKEN")); return raw; }
    private Path write(Map<String, Object> raw) throws IOException { Path path = Files.createTempFile(temporary, "quant-", ".json").toAbsolutePath(); Files.writeString(path, NextflowJson.json(raw)); return path; }
    private SalmonQuantRecipe.Request read(Map<String, Object> raw) throws IOException { return SalmonQuantRecipe.read(write(raw), () -> { }); }
}
