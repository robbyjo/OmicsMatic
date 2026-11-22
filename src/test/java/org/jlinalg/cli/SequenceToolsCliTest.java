/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

class SequenceToolsCliTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path directory;
    private static final String ADAPTER = "AGATCGGAAGAGC", MATE_ADAPTER = "CTGTCTCTTATA";
    private String output;

    private int run(String method, String name, String... options) {
        List<String> args = new ArrayList<>(List.of("sequence-qc", "--method", method, "--out", directory.resolve(name).toString(), "--no-config"));
        args.addAll(List.of(options));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int exit = JLinAlgCli.run(args.toArray(String[]::new), stream, stream); output = bytes.toString(); return exit;
    }
    private String python() {
        String value = System.getenv("JLINALG_SEQUENCE_PYTHON"); assumeTrue(value != null, "Optional real sequencing adapter tests: set JLINALG_SEQUENCE_PYTHON"); return value;
    }
    private String fastqcHome() {
        String value = System.getenv("JLINALG_FASTQC_HOME"); assumeTrue(value != null, "Optional real FastQC tests: set JLINALG_FASTQC_HOME"); return value;
    }
    private static String record(String id, String sequence, String quality) { return "@" + id + "\n" + sequence + "\n+\n" + quality + "\n"; }
    private Path singleFixture() throws Exception {
        Path reads = directory.resolve("single.fastq");
        String[] sequences = {"CGTACCGTTA" + ADAPTER, "TGCCTACGTA", ADAPTER, "CGTACCGTTAAGATCG", "GGCCTTACCATTTT"};
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < sequences.length; i++) data.append(record("read" + i, sequences[i], i == 4 ? "IIIIIIIIII!!!!" : "I".repeat(sequences[i].length())));
        Files.writeString(reads, data); return reads;
    }
    @Test void unavailableDependenciesSkipWithPersistentActionsAndNoSuccessfulResults() throws Exception {
        assertEquals(3, run("cutadapt", "missing-python", "--python", directory.resolve("absent-python").toString()), output);
        assertTrue(output.contains("SKIPPED ANALYSIS"));
        assertTrue(Files.exists(directory.resolve("missing-python/WARNINGS.md")));
        assertFalse(Files.exists(directory.resolve("missing-python/results.tsv")));
        assertEquals(3, run("fastqc", "missing-fastqc", "--fastqc-home", directory.resolve("absent-fastqc").toString()), output);
        assertTrue(Files.readString(directory.resolve("missing-fastqc/WARNINGS.md")).contains("Required action"));
    }
    @Test void realCutadaptTrimsExactAndPartialAdaptersQualityTailsAndFiltersShortReads() throws Exception {
        String python = python(); Path reads = singleFixture();
        assertEquals(0, run("cutadapt", "single", "--python", python, "--reads", reads.toString(), "--adapter3", ADAPTER,
            "--minimum-length", "6", "--quality-cutoff", "20", "--error-rate", "0"), output);
        List<String> sequences = new ArrayList<>(), ids = new ArrayList<>();
        try (var result = new FastqIO.Reader(directory.resolve("single/trimmed_R1.fastq.gz"))) {
            FastqIO.Read read; while ((read = result.next()) != null) { sequences.add(read.sequence()); ids.add(read.pairId()); }
        }
        assertEquals(List.of("CGTACCGTTA", "TGCCTACGTA", "CGTACCGTTA", "GGCCTTACCA"), sequences);
        assertEquals(List.of("read0", "read1", "read3", "read4"), ids);
        assertTrue(Files.readString(directory.resolve("single/results.tsv")).contains("output_reads_or_pairs\t4"));
        assertTrue(Files.readString(directory.resolve("single/session.yaml")).contains("5.2"));
    }
    @Test void realPairedCutadaptKeepsPairsTogetherAndAppliesFixedCutToBothMates() throws Exception {
        String python = python(); Path first = directory.resolve("R1.fastq"), second = directory.resolve("R2.fastq");
        String[] a = {"ACGTACGTAC" + ADAPTER, "AC" + ADAPTER, "TGCCTACGTA"};
        String[] b = {"TTGCAACGTC" + MATE_ADAPTER, "TTGCAACGTC" + MATE_ADAPTER, "GGCCTTACCA"};
        StringBuilder x = new StringBuilder(), y = new StringBuilder();
        for (int i = 0; i < 3; i++) { x.append(record("read" + i + "/1", a[i], "I".repeat(a[i].length()))); y.append(record("read" + i + "/2", b[i], "I".repeat(b[i].length()))); }
        Files.writeString(first, x); Files.writeString(second, y);
        assertEquals(0, run("cutadapt", "paired", "--python", python, "--reads", first.toString(), "--reads2", second.toString(),
            "--adapter3", ADAPTER, "--adapter3-2", MATE_ADAPTER, "--minimum-length", "6", "--cut-left", "1", "--error-rate", "0"), output);
        assertEquals(2, FastqIO.inspect(directory.resolve("paired/trimmed_R1.fastq.gz"), directory.resolve("paired/trimmed_R2.fastq.gz")).read1().reads());
        try (var r1 = new FastqIO.Reader(directory.resolve("paired/trimmed_R1.fastq.gz")); var r2 = new FastqIO.Reader(directory.resolve("paired/trimmed_R2.fastq.gz"))) {
            assertEquals("CGTACGTAC", r1.next().sequence()); assertEquals("TGCAACGTC", r2.next().sequence());
            assertEquals("read2", r1.next().pairId()); assertEquals("read2", r2.next().pairId());
        }
    }
    @Test void realFastqcFlagsPoorQualityWithoutReportingExecutionFailure() throws Exception {
        String home = fastqcHome(); Path reads = directory.resolve("poor.fastq");
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < 40; i++) data.append(record("read" + i, "A".repeat(50), "!".repeat(50)));
        Files.writeString(reads, data);
        assertEquals(0, run("fastqc", "qc", "--fastqc-home", home, "--reads", reads.toString()), output);
        assertTrue(output.contains("QC WARNINGS"));
        assertTrue(Files.isRegularFile(directory.resolve("qc/poor_fastqc.zip")));
        assertTrue(Files.isRegularFile(directory.resolve("qc/poor_fastqc.html")));
        assertTrue(Files.readString(directory.resolve("qc/results.tsv")).contains("FAIL"));
        assertTrue(Files.readString(directory.resolve("qc/manifest.yaml")).contains("completed-with-qc-warnings"));
    }
    @Test void realMultiqcAggregatesNamedReportsAndChecksSourceReadCounts() throws Exception {
        String python = python(), home = fastqcHome(); Path reads = singleFixture();
        assertEquals(0, run("cutadapt", "trim", "--python", python, "--reads", reads.toString(), "--adapter3", ADAPTER), output);
        assertEquals(0, run("fastqc", "qc", "--fastqc-home", home, "--reads", reads.toString()), output);
        Path other = directory.resolve("other/single.fastq"); Files.createDirectories(other.getParent());
        StringBuilder additional = new StringBuilder();
        for (int i = 0; i < 3; i++) additional.append(record("other" + i, "ACGTTGCA", "IIIIIIII"));
        Files.writeString(other, additional);
        assertEquals(0, run("cutadapt", "trim2", "--python", python, "--reads", other.toString(), "--adapter3", ADAPTER), output);
        assertEquals(0, run("fastqc", "qc2", "--fastqc-home", home, "--reads", other.toString()), output);
        Path manifest = directory.resolve("reports.tsv");
        Files.writeString(manifest, "sample_id\tfastqc_zip\tcutadapt_json\npatient_001\tqc/single_fastqc.zip\ttrim/cutadapt.json\npatient_002\tqc2/single_fastqc.zip\ttrim2/cutadapt.json\n");
        assertEquals(0, run("multiqc", "aggregate", "--python", python, "--reports", manifest.toString()), output + " output=" + directory);
        assertEquals(4, DelimitedData.read(directory.resolve("aggregate/results.tsv")).rows().size());
        assertTrue(Files.readString(directory.resolve("aggregate/results.tsv")).contains("patient_001\tfastqc\t5"));
        assertTrue(Files.readString(directory.resolve("aggregate/results.tsv")).contains("patient_001\tcutadapt\t5"));
        assertTrue(Files.readString(directory.resolve("aggregate/results.tsv")).contains("patient_002\tfastqc\t3"));
        assertTrue(Files.readString(directory.resolve("aggregate/results.tsv")).contains("patient_002\tcutadapt\t3"));
        assertTrue(Files.isRegularFile(directory.resolve("aggregate/multiqc_report.html")));
        assertTrue(output.contains("QC WARNINGS"));
        assertTrue(Files.readString(directory.resolve("aggregate/WARNINGS.md")).contains("patient_001"));
        assertTrue(Files.readString(directory.resolve("aggregate/source-qc.tsv")).contains("FAIL"));
    }
    @Test void realEmptyCutadaptAndFailedFastqcRemainProminentAfterMultiqcAggregation() throws Exception {
        String python = python(), home = fastqcHome(); Path reads = directory.resolve("poor.fastq");
        StringBuilder text = new StringBuilder();
        for (int i=0;i<40;i++) text.append(record("read"+i,"A".repeat(50),"!".repeat(50)));
        Files.writeString(reads,text);
        assertEquals(0,run("cutadapt","empty-trim","--python",python,"--reads",reads.toString(),"--adapter3",ADAPTER,"--minimum-length","100"),output);
        assertTrue(output.contains("QC WARNINGS"));
        assertTrue(Files.readString(directory.resolve("empty-trim/WARNINGS.md")).contains("All 40 input reads/pairs were filtered"));
        assertEquals(0,FastqIO.inspect(directory.resolve("empty-trim/trimmed_R1.fastq.gz"),null).read1().reads());
        assertEquals(0,run("fastqc","poor-qc","--fastqc-home",home,"--reads",reads.toString()),output);
        Path reports=directory.resolve("warnings.tsv");
        Files.writeString(reports,"sample_id\tfastqc_zip\tcutadapt_json\nwarned_sample\tpoor-qc/poor_fastqc.zip\tempty-trim/cutadapt.json\n");
        assertEquals(0,run("multiqc","warned","--python",python,"--reports",reports.toString()),output);
        assertTrue(output.contains("QC WARNINGS"));
        String warnings=Files.readString(directory.resolve("warned/WARNINGS.md"));
        assertTrue(warnings.contains("warned_sample: FAIL"));assertTrue(warnings.contains("Cutadapt retained no reads/pairs"));
        assertTrue(warnings.contains("Required action"));
        assertTrue(Files.readString(directory.resolve("warned/analysis-status.json")).contains("completed-with-qc-warnings"));
        assertEquals(2,DelimitedData.read(directory.resolve("warned/results.tsv")).rows().size());
    }
    @Test void malformedInputFailsWithPersistentWarningBeforeUpstreamAnalysis() throws Exception {
        String python = python(); Path reads = directory.resolve("bad.fastq");
        Files.writeString(reads, "@A\nACGT\n+\nIII\n");
        assertEquals(2, run("cutadapt", "invalid", "--python", python, "--reads", reads.toString(), "--adapter3", ADAPTER), output);
        assertTrue(Files.readString(directory.resolve("invalid/WARNINGS.md")).contains("equal sequence/quality lengths"));
        assertFalse(Files.exists(directory.resolve("invalid/trimmed_R1.fastq.gz")));
    }
}
