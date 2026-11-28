/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class Pbmm2WorkflowTest {
    @TempDir Path temporary;
    static Map<String, Object> request() {
        var r = new LinkedHashMap<String, Object>(); r.put("schema_version", 1); r.put("edition", "1.17.0"); r.put("operation", "ALIGN");
        r.put("runtime_home", "/private/pbmm2"); r.put("container_home", "/private/apptainer"); r.put("image", "/private/carrier.sif");
        r.put("resources", List.of(Map.of("id", "whole", "path", "/data/original root"))); r.put("working_directory", "/data/original root");
        r.put("reference", "/data/original root/reference.fa"); r.put("reads", "/data/original root/reads.fofn"); r.put("output_kind", "BAM"); r.put("preset", "ISOSEQ"); r.put("threads", 2); r.put("logging", "INFO"); r.put("parameters", Map.of());
        r.put("maximum_input_bytes", 1000000000L); r.put("maximum_file_bytes", 1000000000L); r.put("maximum_files", 10000); r.put("maximum_records", 10000);
        var limits = new LinkedHashMap<String, Object>(); limits.put("wall_millis", 120000); limits.put("address_space_bytes", 4294967296L); limits.put("cpu_seconds", 120); limits.put("file_bytes", 1000000000L); limits.put("open_files", 256); limits.put("workspace_bytes", 5000000000L); limits.put("file_count", 10000); limits.put("processes", 64); limits.put("threads", 256); limits.put("sampled_rss_bytes", 4294967296L); limits.put("monitor_millis", 50); r.put("process_limits", limits); return r;
    }
    @Test void literalNativeLocatorsAndScientificNumericLexemesSurvive() throws Exception {
        var r = request(); r.put("parameters", Map.of("min-id-perc", new NextflowParameters.NumberToken("9.500e1"), "sort", true, "rg", "@RG\tID:sample+A\tSM:sample+A"));
        var parsed = Pbmm2Request.parse(r); var argv = Pbmm2Workflow.command("/owned/session", parsed, false);
        assertTrue(argv.contains("9.500e1")); assertTrue(argv.contains("@RG\tID:sample+A\tSM:sample+A")); assertTrue(argv.contains("/data/original root/reads.fofn"));
        assertTrue(argv.contains("/owned/session/staged/whole:/data/original root:rw")); assertTrue(argv.contains("/owned/session/private-tmp:/tmp:rw"));
        assertEquals("ISOSEQ", argv.get(argv.indexOf("--preset") + 1));
    }
    @Test void sourceAliasesCannotDuplicateOrOverrideOwnedScienceBindings() throws Exception {
        var r = request(); r.put("parameters", Map.of("x", 90, "min-id-perc", 95)); assertThrows(IOException.class, () -> Pbmm2Request.parse(r));
        r.put("parameters", Map.of("report-json", "/outside/report")); assertThrows(IOException.class, () -> Pbmm2Request.parse(r));
        r.put("parameters", Map.of("invented-model", 1)); assertThrows(NextflowProcess.Unavailable.class, () -> Pbmm2Request.parse(r));
    }
    @Test void incompleteRootsAndUnavailableEditionAreExplicit() {
        var r = request(); r.put("reads", "/outside/reads.bam"); assertThrows(IOException.class, () -> Pbmm2Request.parse(r));
        var unsupported = request(); unsupported.put("edition", "1.14.0"); assertThrows(NextflowProcess.Unavailable.class, () -> Pbmm2Request.parse(unsupported));
        var overlapping = request(); overlapping.put("resources", List.of(Map.of("id", "a", "path", "/data"), Map.of("id", "b", "path", "/data/nested"))); assertThrows(IOException.class, () -> Pbmm2Request.parse(overlapping));
    }
    @Test void wholeInputCapturePreservesEmptyDirectoriesAndRejectsChangedOriginals() throws Exception {
        Path source = Files.createDirectory(temporary.resolve("source")), copy = temporary.resolve("copy"); Files.createDirectory(source.resolve("empty")); Files.writeString(source.resolve("literal+id"), "whole-input");
        var captured = Pbmm2Capture.copyWhole(source, copy, 1024, 1024, 2); assertTrue(captured.directories().contains("empty")); assertEquals(captured, Pbmm2Capture.census(copy, 1024, 1024, 2));
        assertThrows(IOException.class, () -> Pbmm2Capture.copyWhole(source, temporary.resolve("too-small"), 2, 1024, 2));
        Files.writeString(copy.resolve("literal+id"), "changed"); assertNotEquals(captured, Pbmm2Capture.census(copy, 1024, 1024, 2)); assertEquals("whole-input", Files.readString(source.resolve("literal+id")));
    }
    void bam(Path path, boolean corrupt) {
        var header = new SAMFileHeader(); header.addSequence(new SAMSequenceRecord("chr+literal", 1000));
        var program = new SAMProgramRecord("pbmm2"); program.setProgramName("pbmm2"); program.setProgramVersion("1.17.0 (commit v1.17.0)"); header.addProgramRecord(program);
        var record = new SAMRecord(header); record.setReadName("movie/17/ccs"); record.setReadString("ACGTAC"); record.setBaseQualityString("IIIIII"); record.setReferenceName("chr+literal"); record.setAlignmentStart(11); record.setCigarString("5=1X"); record.setMappingQuality(60); record.setAttribute("NM", 1); record.setAttribute("mi", corrupt ? 99f : (float)(500.0 / 6));
        try (var writer = new SAMFileWriterFactory().makeBAMWriter(header, true, path.toFile())) { writer.addAlignment(record); }
    }
    @Test void completeBamArithmeticRefusesAlteredFloatEvenWithValidContainer() throws Exception {
        var r = Pbmm2Request.parse(request()); Path out = Files.createDirectory(temporary.resolve("bam")); bam(out.resolve("result.bam"), false);
        assertEquals(1L, Pbmm2Audit.run(out, r).get("mapped"));
        Path bad = Files.createDirectory(temporary.resolve("bad")); bam(bad.resolve("result.bam"), true); assertThrows(IOException.class, () -> Pbmm2Audit.run(bad, r));
    }
    @Test void truncatedBamAndFakeReferenceIndexCannotBeAdmitted() throws Exception {
        Path out = Files.createDirectory(temporary.resolve("truncated")); bam(out.resolve("result.bam"), false); byte[] bytes = Files.readAllBytes(out.resolve("result.bam")); Files.write(out.resolve("result.bam"), Arrays.copyOf(bytes, bytes.length - 28));
        assertThrows(IOException.class, () -> Pbmm2Audit.run(out, Pbmm2Request.parse(request())));
        var index = request(); index.put("operation", "INDEX"); index.put("reads", null); index.put("output_kind", "MMI"); Path wrong = Files.createDirectory(temporary.resolve("wrong-index")); Files.writeString(wrong.resolve("result.mmi"), "fake-index"); assertThrows(IOException.class, () -> Pbmm2Audit.run(wrong, Pbmm2Request.parse(index)));
    }
}
