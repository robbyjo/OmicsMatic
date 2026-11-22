/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import htsjdk.samtools.SamReaderFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlignmentToolsCliTest {
    @TempDir Path directory;
    String output;
    private Path sam(boolean invalid) throws Exception {
        Path input = directory.resolve(invalid ? "invalid.sam" : "input.sam");
        Files.writeString(input, "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg1\tSM:sample1\tLB:lib1\tPL:ILLUMINA\n"
            + record("INST:1:FC:1:1101:100:100", 200, "I", invalid)
            + record("INST:1:FC:1:1101:200:200", 100, "I", false)
            + record("INST:1:FC:1:1101:300:300", 100, "H", false)
            + "unmapped\t4\t*\t0\t0\t*\t*\t0\t0\tAAAAAAAAAAAAAAAAAAAA\tIIIIIIIIIIIIIIIIIIII\tRG:Z:rg1\n");
        return input;
    }
    private String record(String id, int position, String quality, boolean invalid) {
        return id + "\t0\tchr1\t" + (invalid ? 995 : position) + "\t60\t20M\t*\t0\t0\t" + "A".repeat(20) + "\t" + quality.repeat(20) + "\tRG:Z:rg1\tNM:i:0\n";
    }
    private int run(String method, Path input, String jar, String name, String... extra) throws Exception {
        var args = new ArrayList<>(List.of("alignment-tools", "--method", method, "--input", input.toString(), "--picard", jar,
            "--out", directory.resolve(name).toString(), "--memory-mb", "512", "--no-config"));
        args.addAll(List.of(extra));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int status = JLinAlgCli.run(args.toArray(String[]::new), stream, stream); output = bytes.toString();
        Path warnings = directory.resolve(name + "/WARNINGS.md");
        if (status != 0 && Files.exists(warnings)) output += Files.readString(warnings);
        Path version = directory.resolve(name + "/version.log");
        if (status == 3 && Files.exists(version)) output += Files.readString(version);
        return status;
    }
    @Test void unavailableOrUnvalidatedPicardSkipsWithProminentRecoveryAndNoResults() throws Exception {
        Path input = sam(false), wrong = directory.resolve("wrong.jar"); Files.writeString(wrong, "not the pinned application");
        for (String name : List.of("missing", "wrong")) {
            assertEquals(3, run("sort", input, directory.resolve(name + ".jar").toString(), name), output);
            assertTrue(output.contains("SKIPPED ANALYSIS"));
            assertTrue(Files.readString(directory.resolve(name + "/WARNINGS.md")).contains("install_picard.py --allow-download"));
            assertFalse(Files.exists(directory.resolve(name + "/results.tsv")));
        }
    }
    @Test void inapplicableOptionsAndInvalidTimeoutDoNotStartAnyProcess() throws Exception {
        Path input = sam(false);
        assertEquals(2, run("validate", input, "missing.jar", "timeout", "--timeout", "0"));
        assertFalse(Files.exists(directory.resolve("timeout")));
        assertEquals(2, run("validate", input, "missing.jar", "extra", "--remove-duplicates", "true"));
        assertFalse(Files.exists(directory.resolve("extra")));
    }
    @Test void realPicardSortDuplicateMarkRemoveAndAlignmentMetricsHaveKnownResults() throws Exception {
        String jar = System.getenv("JLINALG_PICARD_JAR"); assumeTrue(jar != null, "Set JLINALG_PICARD_JAR to the pinned official JAR");
        Path input = sam(false);
        assertEquals(0, run("sort", input, jar, "sort", "--create-index", "true"), output);
        Path sorted = directory.resolve("sort/alignment.bam");
        assertTrue(Files.isRegularFile(directory.resolve("sort/alignment.bai")));
        var positions = new ArrayList<Integer>();
        try (var reader = SamReaderFactory.makeDefault().open(sorted)) { reader.forEach(r -> positions.add(r.getAlignmentStart())); }
        assertEquals(List.of(100, 100, 200, 0), positions);
        assertEquals(0, run("sort", input, jar, "queryname", "--sort-order", "queryname"), output);
        var names = new ArrayList<String>();
        try (var reader = SamReaderFactory.makeDefault().open(directory.resolve("queryname/alignment.bam"))) {
            reader.forEach(r -> names.add(r.getReadName()));
        }
        assertEquals(List.of("INST:1:FC:1:1101:100:100", "INST:1:FC:1:1101:200:200", "INST:1:FC:1:1101:300:300", "unmapped"), names);
        assertEquals(0, run("mark-duplicates", sorted, jar, "mark"), output);
        long duplicates = 0, count = 0;
        try (var reader = SamReaderFactory.makeDefault().open(directory.resolve("mark/alignment.bam"))) {
            for (var r : reader) { count++; if (r.getDuplicateReadFlag()) duplicates++; }
        }
        assertEquals(4, count); assertEquals(1, duplicates);
        assertTrue(Files.readString(directory.resolve("mark/duplication_metrics.txt")).contains("UNPAIRED_READ_DUPLICATES"));
        assertEquals(0, run("mark-duplicates", sorted, jar, "remove", "--remove-duplicates", "true"), output);
        count = 0;
        try (var reader = SamReaderFactory.makeDefault().open(directory.resolve("remove/alignment.bam"))) {
            for (var r : reader) { count++; assertFalse(r.getDuplicateReadFlag()); }
        }
        assertEquals(3, count);
        assertEquals(0, run("validate", sorted, jar, "validate"), output);
        assertTrue(Files.readString(directory.resolve("validate/validation.txt")).contains("No errors found"));
        Path reference = directory.resolve("reference.fa");
        Files.writeString(reference, ">chr1\n" + "A".repeat(1000) + "\n");
        Files.writeString(directory.resolve("reference.fa.fai"), "chr1\t1000\t6\t1000\t1001\n");
        Files.writeString(directory.resolve("reference.dict"), "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n");
        assertEquals(0, run("alignment-summary", sorted, jar, "metrics", "--reference", reference.toString()), output);
        List<String> lines = Files.readAllLines(directory.resolve("metrics/alignment_metrics.txt"));
        int header = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith("CATEGORY\t")) header = i;
        assertTrue(header >= 0); var columns = List.of(lines.get(header).split("\t"));
        String[] values = lines.get(header + 1).split("\t", -1);
        assertEquals("UNPAIRED", values[columns.indexOf("CATEGORY")]);
        assertEquals("4", values[columns.indexOf("TOTAL_READS")]);
        assertEquals("3", values[columns.indexOf("PF_READS_ALIGNED")]);
        assertFalse(Files.exists(directory.resolve("metrics/partial")));
    }
    @Test void realValidationErrorsAreFindingsAndUnsortedDuplicateInputIsAnExecutionFailure() throws Exception {
        String jar = System.getenv("JLINALG_PICARD_JAR"); assumeTrue(jar != null, "Set JLINALG_PICARD_JAR");
        assertEquals(4, run("validate", sam(true), jar, "invalid"), output);
        assertTrue(Files.readString(directory.resolve("invalid/validation.txt")).contains("CIGAR_MAPS_OFF_REFERENCE"));
        assertTrue(Files.readString(directory.resolve("invalid/WARNINGS.md")).contains("Required action"));
        assertTrue(Files.isRegularFile(directory.resolve("invalid/results.tsv")));
        assertTrue(Files.readString(directory.resolve("invalid/analysis-status.json")).contains("completed-with-validation-errors"));
        assertEquals(2, run("mark-duplicates", sam(false), jar, "unsorted"), output);
        assertTrue(output.contains("ANALYSIS FAILED"));
        assertFalse(Files.exists(directory.resolve("unsorted/results.tsv")));
    }
}
