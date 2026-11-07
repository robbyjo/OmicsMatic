/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SalmonQuantInputsTest {
    @TempDir Path temporary;
    @Test void completeOriginalAlignmentInputsAreCapturedUnchangedAndReplayed() throws Exception {
        var fixture = fixture(Map.of()); var captured = SalmonQuantInputs.capture(fixture.root(), fixture.request(), () -> { });
        assertEquals(40, captured.context().targets().size()); assertEquals(24240, captured.context().alignments().records()); assertEquals(8520, captured.context().processedFragments());
        assertEquals(2, captured.originals().size()); assertTrue(captured.context().declaredRetainedPopulationMatchesSource()); captured.context().requireSupportedPopulation(fixture.request()); captured.verify(fixture.request(), () -> { });
        var restored = SalmonQuantInputs.readCaptured(fixture.root(), fixture.request(), () -> { }); assertEquals(captured.context().document(), restored.document());
        assertEquals(NextflowFiles.pin(fixture.request().reference() == null ? null : Path.of(fixture.request().reference().path()), 1000000, () -> { }), restored.reference().fastaPin());
    }
    @Test void portableCapturedReplayDoesNotOpenOriginalProducerSources() throws Exception {
        var fixture = fixture(Map.of()); var captured = SalmonQuantInputs.capture(fixture.root(), fixture.request(), () -> { });
        Path fasta = Path.of(fixture.request().reference().path()), sam = Path.of(fixture.request().alignments().path()); Files.move(fasta, fasta.resolveSibling("saved-reference.fa")); Files.move(sam, sam.resolveSibling("saved-alignment.sam"));
        assertEquals(8520, SalmonQuantInputs.readCaptured(fixture.root(), fixture.request(), () -> { }).processedFragments()); assertThrows(IOException.class, () -> captured.verify(fixture.request(), () -> { }));
    }
    @Test void changingCapturedSourceOrAddingAnEmptyMemberFailsProtectedContext() throws Exception {
        var fixture = fixture(Map.of()); var captured = SalmonQuantInputs.capture(fixture.root(), fixture.request(), () -> { });
        Files.createDirectory(fixture.root().resolve("inputs/unlisted-empty")); assertThrows(IOException.class, () -> captured.context().verify(fixture.request(), () -> { }));
    }
    @Test void changingOriginalReferenceAfterCaptureIsNotASilentInputUpdate() throws Exception {
        var fixture = fixture(Map.of()); var captured = SalmonQuantInputs.capture(fixture.root(), fixture.request(), () -> { });
        Path source = Path.of(fixture.request().reference().path()); Files.writeString(source, Files.readString(source).replaceFirst("AC", "TG"));
        assertThrows(IOException.class, () -> captured.verify(fixture.request(), () -> { }));
    }
    @Test void combinedFastaAndSamByteCapacityIsEnforced() throws Exception {
        var fixture = fixture(Map.of("input_bytes", 6000000)); assertThrows(IOException.class, () -> SalmonQuantInputs.capture(fixture.root(), fixture.request(), () -> { }));
    }
    @Test void cancellationProducesNoCapturedInputReceipt() throws Exception {
        var fixture = fixture(Map.of()); assertThrows(CancellationException.class, () -> SalmonQuantInputs.capture(fixture.root(), fixture.request(), () -> { throw new CancellationException(); }));
    }
    private record Fixture(Path root, SalmonQuantRecipe.Request request) { }
    private Fixture fixture(Map<String, Object> limits) throws Exception {
        Path source = Files.createTempDirectory(temporary, "original-").toAbsolutePath(), root = Files.createTempDirectory(temporary, "captured-").toAbsolutePath();
        try (var zip = new ZipInputStream(getClass().getResourceAsStream("/org/jlinalg/cli/salmon-alignment-inputs/original-alignment-inputs.zip"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) { NextflowFiles.member(entry.getName()); Files.write(source.resolve(entry.getName()), zip.readAllBytes()); zip.closeEntry(); }
        }
        var raw = new LinkedHashMap<String, Object>(); raw.put("schema_version", 1); raw.put("profile", "linux-x86_64-salmon-1.9.0"); raw.put("mode", "TRANSCRIPTOME_ALIGNMENTS"); raw.put("index_bundle", null); raw.put("reads", null);
        raw.put("alignments", Map.of("path", source.resolve("alignments.sam").toString(), "format", "SAM", "layout", "PAIRED")); raw.put("reference", Map.of("path", source.resolve("transcripts.fa").toString(), "encoding", "PLAIN_UTF8", "headers", "SOURCE_WHITESPACE_TOKEN"));
        raw.put("library_type", "ISR"); raw.put("optimizer", "EM"); raw.put("sequence_bias", false); raw.put("gc_bias", false); raw.put("position_bias", false); raw.put("bootstraps", 3); raw.put("minimum_assigned_fragments", 10);
        raw.put("fragment_prior", null); raw.put("threads", 2); raw.put("population", "REQUIRE_DECLARED_NONDECOYS"); raw.put("limits", limits);
        Path recipe = source.resolve("recipe.json"); Files.writeString(recipe, NextflowJson.json(raw)); return new Fixture(root, SalmonQuantRecipe.read(recipe, () -> { }));
    }
}
