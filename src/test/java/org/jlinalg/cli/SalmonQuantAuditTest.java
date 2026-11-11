/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.SalmonQuantification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonQuantAuditTest {
    @TempDir Path temporary;
    private static final String RESOURCE = "/org/jlinalg/cli/salmon-output-contracts/";
    static Stream<String> cases() throws Exception { return NextflowFiles.list(proof().get("cases")).stream().map(raw -> { try { return NextflowFiles.text(NextflowFiles.object(raw).get("name")); } catch (IOException e) { throw new java.io.UncheckedIOException(e); } }); }
    @ParameterizedTest @MethodSource("cases")
    void allOriginalOutputFamiliesBindWholeInputsOptionsCountersAndInferentialPopulation(String name) throws Exception {
        var fixture = fixture(name); var result = SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { });
        assertEquals(40, result.table().rows().size()); assertEquals(8520, result.arithmetic().expectedAssignedFragments());
        assertEquals(fixture.spec().bootstraps() * 40L, result.replicates() == null ? 0 : result.replicates().values());
        assertEquals(false, result.document().get("user_analysis_admitted")); assertEquals(false, result.document().get("original_process_completion_certified_by_this_reader"));
        assertEquals(name.startsWith("1.0.0/") && !fixture.spec().alignmentMode() ? 2 : 0, NextflowFiles.list(result.document().get("unavailable_original_metadata_bindings")).size());
        assertEquals(false, result.document().get("position_bias_has_independent_meta_flag"));
    }
    @Test void commandReconstructionDoesNotIgnoreUnknownOrRepeatedFlags() throws Exception {
        var fixture = fixture("1.9.0/alignment-em-3"); var argv = new ArrayList<>(NextflowFiles.strings(expected("1.9.0/alignment-em-3").get("argv"))); argv.add("--unknown");
        assertThrows(IOException.class, () -> SalmonQuantAudit.command(argv, "1.9.0")); argv.remove(argv.size() - 1); argv.add("--useEM"); assertThrows(IOException.class, () -> SalmonQuantAudit.command(argv, "1.9.0"));
        assertNotNull(fixture.spec());
    }
    @ParameterizedTest @ValueSource(strings={"num_processed", "num_valid_targets", "num_decoy_targets", "num_bootstraps", "num_libraries"})
    void changedOriginalCounterFailsDespiteValidTable(String field) throws Exception {
        var fixture = fixture("1.9.0/paired-em-bias-50"); Path meta = fixture.output().resolve("aux_info/meta_info.json"); var value = new java.util.LinkedHashMap<Object, Object>(NextflowFiles.object(NextflowFiles.json(meta, 1000000)));
        value.put(field, ((Number) value.get(field)).longValue() + 1); Files.writeString(meta, NextflowJson.json(value)); assertThrows(IOException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { }));
    }
    @Test void mappingAssignedMassComesFromSeparateCounterNotRoundedTableSum() throws Exception {
        var fixture = fixture("1.9.0/paired-em-bias-50"); Path format = fixture.output().resolve("lib_format_counts.json"); var value = new java.util.LinkedHashMap<Object, Object>(NextflowFiles.object(NextflowFiles.json(format, 1000000)));
        value.put("num_assigned_fragments", 8519); Files.writeString(format, NextflowJson.json(value)); assertThrows(IOException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { }));
    }
    @Test void oldestUnavailableSha512CannotBeFilledFromIndex() throws Exception {
        var fixture = fixture("1.0.0/paired-em-bias-50"); Path meta = fixture.output().resolve("aux_info/meta_info.json"); var value = new java.util.LinkedHashMap<Object, Object>(NextflowFiles.object(NextflowFiles.json(meta, 1000000)));
        value.put("index_seq_hash512", fixture.spec().index().get("SeqHash512")); Files.writeString(meta, NextflowJson.json(value)); assertThrows(IOException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { }));
    }
    @Test void changedOptionsOrReportedBiasCannotPassAnOtherwiseCompleteFamily() throws Exception {
        var fixture = fixture("1.9.0/paired-em-bias-50"); Path command = fixture.output().resolve("cmd_info.json"); var value = new java.util.LinkedHashMap<Object, Object>(NextflowFiles.object(NextflowFiles.json(command, 1000000)));
        value.put("ignored_extra_flag", List.of()); Files.writeString(command, NextflowJson.json(value)); assertThrows(IOException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { }));
    }
    @Test void damagedInferentialTailDoesNotAdmitCompleteTablePrefix() throws Exception {
        var fixture = fixture("1.9.0/alignment-em-3"); Files.write(fixture.output().resolve("aux_info/bootstrap/bootstraps.gz"), new byte[]{1}, java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { }));
    }
    @Test void extraOrTruncatedQuantificationRowsInvalidateWholeFamily() throws Exception {
        var fixture = fixture("1.9.0/alignment-em-3"); Path quant = fixture.output().resolve("quant.sf"); Files.writeString(quant, "unexpected\t1\t1.000\t0.000000\t0.000\n", java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { }));
    }
    @Test void zeroBootstrapCannotAdmitUnrequestedInferentialFamily() throws Exception {
        var fixture = fixture("1.9.0/paired-vb-no-replicates"); Files.createDirectory(fixture.output().resolve("aux_info/bootstrap")); assertThrows(IOException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { }));
    }
    @Test void cancellationDoesNotAdmitOriginalValidPrefix() throws Exception {
        var fixture = fixture("1.9.0/alignment-em-3"); assertThrows(CancellationException.class, () -> SalmonQuantAudit.audit(fixture.output(), fixture.spec(), () -> { throw new CancellationException(); }));
    }
    private record Fixture(Path output, SalmonQuantAudit.Specification spec) { }
    private Fixture fixture(String name) throws Exception {
        Path area = Files.createTempDirectory(temporary, "output-").toAbsolutePath(); Map<?, ?> expected = expected(name);
        try (var zip = new ZipInputStream(getClass().getResourceAsStream(RESOURCE + "original-output-contracts.zip"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                boolean include = entry.getName().equals("transcripts.fa") || entry.getName().equals(expected.get("version") + "/index-info.json") || entry.getName().startsWith(name + "/");
                if (include) { NextflowFiles.member(entry.getName()); Path target = area.resolve(entry.getName()); Files.createDirectories(target.getParent()); Files.write(target, zip.readAllBytes()); } zip.closeEntry();
            }
        }
        String version = NextflowFiles.text(expected.get("version")); List<String> argv = NextflowFiles.strings(expected.get("argv"));
        var fasta = RepeatFasta.read(area.resolve("transcripts.fa"), RepeatFasta.Encoding.PLAIN_UTF8, new RepeatFasta.Limits(1000000, 1000000, 1000000, 10000, 100, 10000, 1000, 4000000), () -> { });
        var targets = fasta.sequences().stream().map(r -> new SalmonQuantification.Target(r.id(), r.length())).toList(); boolean alignment = Boolean.TRUE.equals(expected.get("alignment"));
        Map<?, ?> index = alignment ? null : SalmonMetadata.read(area.resolve(version + "/index-info.json"), 1000000, 10000);
        var spec = new SalmonQuantAudit.Specification(version, alignment, Boolean.TRUE.equals(expected.get("paired")), argv.get(argv.indexOf("-l") + 1), argv.contains("--useEM") ? SalmonQuantRecipe.Optimizer.EM : SalmonQuantRecipe.Optimizer.VARIATIONAL_BAYES,
            argv.contains("--seqBias"), argv.contains("--gcBias"), (int) NextflowFiles.integer(expected.get("bootstraps"), 0, 1000), NextflowFiles.integer(expected.get("processed_fragments"), 1, 1000000), alignment ? 0 : 1,
            targets, SalmonQuantAudit.command(argv, version), index, 1000000, 1000000, 16384, 100000, 10000000);
        return new Fixture(area.resolve(name), spec);
    }
    private static Map<?, ?> proof() throws Exception { try (var input = SalmonQuantAuditTest.class.getResourceAsStream(RESOURCE + "provenance.json")) { return NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(input.readAllBytes()))); } }
    private static Map<?, ?> expected(String name) throws Exception { for (Object raw : NextflowFiles.list(proof().get("cases"))) { Map<?, ?> row = NextflowFiles.object(raw); if (name.equals(row.get("name"))) return row; } throw new AssertionError("Missing original family"); }
}
