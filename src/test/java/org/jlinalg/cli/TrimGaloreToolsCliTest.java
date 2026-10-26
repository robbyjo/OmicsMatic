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
import java.util.LinkedHashMap;
import java.util.List;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

class TrimGaloreToolsCliTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path root;
    private String console;
    private String home() { String value = System.getenv("JLINALG_TRIM_GALORE_HOME"); assumeTrue(value != null && Files.isDirectory(Path.of(value)), "Set the permitted original JLINALG_TRIM_GALORE_HOME"); return value; }
    private int run(String name, String release, String home, String... extra) {
        var arguments = new ArrayList<>(List.of("trim-galore-tools", "--home", home, "--release", release, "--reads", root.resolve("reads.fastq").toString(), "--out", root.resolve(name).toString()));
        arguments.addAll(List.of(extra)); var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int code = JLinAlgCli.run(arguments.toArray(String[]::new), stream, stream); console = bytes.toString();
        Path warning = root.resolve(name).resolve("WARNINGS.md");
        if (code != 0 && Files.isRegularFile(warning)) try { console += Files.readString(warning); } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        return code;
    }
    private static String record(String id, String sequence) { return "@" + id + " original comment\n" + sequence + "\n+\n" + "I".repeat(sequence.length()) + "\n"; }
    private static List<FastqIO.Read> reads(Path path) throws Exception { var result = new ArrayList<FastqIO.Read>(); try (var reader = new FastqIO.Reader(path)) { FastqIO.Read read; while ((read = reader.next()) != null) result.add(read); } return result; }

    @Test void original067TrimsActualAdaptersQualityAndRunsOriginalFastqcWithSpacePaths() throws Exception {
        String runtime = home();
        Files.writeString(root.resolve("reads.fastq"), record("exact", "ACGTACGTAGATCGGAAGAGC") + record("clean", "GCTAGCTAGC") + record("short", "AC") + "@poor original comment\nACGTACGT\n+\nIIII!!!!\n");
        assertEquals(0, run("original with spaces", "0.6.7", runtime, "--phred", "33", "--quality", "20", "--minimum-length", "3", "--overlap", "3", "--adapter-kind", "custom", "--adapter1", "AGATCGGAAGAGC", "--fastqc", "true"), console);
        Path out = root.resolve("original with spaces"); var observed = reads(out.resolve("trimmed_R1.fastq.gz"));
        assertEquals(List.of("ACGTACGT", "GCTAGCTAGC", "ACGT"), observed.stream().map(FastqIO.Read::sequence).toList());
        assertEquals(List.of("exact", "clean", "poor"), observed.stream().map(FastqIO.Read::pairId).toList());
        assertTrue(Files.readString(out.resolve("session.yaml")).contains("original-unmodified-perl-and-cutadapt"));
        assertTrue(Files.readString(out.resolve("original/driver-run.json")).contains("original_exit_code"));
        assertTrue(Files.isRegularFile(out.resolve("original/reads1_trimmed_fastqc.zip")));
        assertTrue(Files.readString(out.resolve("fastqc-results.tsv")).contains("qc_status"));
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("FastQC")); assertTrue(console.contains("QC WARNINGS"));
        // Corrupt the retained original stream only in this private test fixture.
        Path original = out.resolve("original/reads1_trimmed.fq"); Files.writeString(original, Files.readString(original).replace("@exact original comment", "@changed original comment"));
        var settings = new LinkedHashMap<String, Object>(); settings.put("phred", 33); settings.put("minimum_length", 3);
        assertThrows(java.io.IOException.class, () -> TrimGaloreAudit.verify(out, "0.6.7", "trim", false, settings, new LinkedHashMap<>()));
    }

    @Test void originalCutadaptIncompleteAdapterWarningIsPromotedWithoutChangingRetainedReads() throws Exception {
        String runtime = home(), sequence = "CTGACTGCTACGTGTCACAGACGTGATGAA", adapter = "AGATCGGAAGAGC";
        var input = new StringBuilder();
        for (int index = 0; index < 32; index++) input.append(record("warning" + index, sequence + adapter));
        Files.writeString(root.resolve("reads.fastq"), input);
        assertEquals(0, run("original-warning", "0.6.10", runtime, "--phred", "33", "--minimum-length", "20", "--adapter-kind", "custom", "--adapter1", adapter), console);
        Path output = root.resolve("original-warning"); var observed = reads(output.resolve("trimmed_R1.fastq.gz"));
        assertEquals(32, observed.size()); assertTrue(observed.stream().allMatch(read -> read.sequence().equals(sequence)));
        assertTrue(Files.readString(output.resolve("original/original.stderr")).contains("adapter sequences may be incomplete"));
        String warning = Files.readString(output.resolve("WARNINGS.md"));
        assertTrue(warning.contains("adapter sequences may be incomplete"));
        assertTrue(warning.contains("Ignore this warning when trimming primers"));
        assertTrue(warning.contains("Required action")); assertTrue(console.contains("QC WARNINGS"));
        assertTrue(Files.readString(output.resolve("analysis-status.json")).contains("completed-with-qc-warnings"));
        assertTrue(Files.readString(output.resolve("session.yaml")).contains("original_warning_blocks"));
    }

    @Test void original610PairedWgbsCutsKeepActualOrphansIncludingOriginalOversizedRescue() throws Exception {
        String runtime = home(); String adapter1 = "AGATCGGAAGAGC", adapter2 = "CTGTCTCTTATA";
        Files.writeString(root.resolve("reads.fastq"), record("A/1", "A".repeat(70) + adapter1) + record("B/1", "A".repeat(20) + adapter1) + record("C/1", "A".repeat(130) + adapter1));
        Files.writeString(root.resolve("mates.fastq"), record("A/2", "C".repeat(70) + adapter2) + record("B/2", "C".repeat(70) + adapter2) + record("C/2", "C".repeat(10) + adapter2));
        assertEquals(0, run("paired", "0.6.10", runtime, "--reads2", root.resolve("mates.fastq").toString(), "--phred", "33", "--quality", "20", "--minimum-length", "20", "--maximum-length", "60", "--overlap", "3", "--adapter-kind", "custom", "--adapter1", adapter1, "--adapter2", adapter2,
                "--clip-r2", "15", "--three-prime-clip-r1", "15", "--retain-unpaired", "true", "--maximum-n", "0"), console);
        Path out = root.resolve("paired");
        assertEquals(List.of("A".repeat(55)), reads(out.resolve("trimmed_R1.fastq.gz")).stream().map(FastqIO.Read::sequence).toList());
        assertEquals(List.of("C".repeat(55)), reads(out.resolve("trimmed_R2.fastq.gz")).stream().map(FastqIO.Read::sequence).toList());
        assertEquals(115, reads(out.resolve("unpaired_R1.fastq.gz")).get(0).sequence().length());
        assertEquals("B", reads(out.resolve("unpaired_R2.fastq.gz")).get(0).pairId());
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("rescued mates exceed maximum length"));
        assertTrue(Files.readString(out.resolve("results.tsv")).contains("oversized_rescued_mates\t1"));
        assertEquals(4, Files.readAllLines(out.resolve("read-status.tsv")).size());
    }

    @Test void originalAutoSmallRnaResolvesItsOwnEighteenBaseDefault() throws Exception {
        String runtime = home(); Files.writeString(root.resolve("reads.fastq"), record("adapter", "C".repeat(25) + "TGGAATTCTCGG") + record("nineteen", "C".repeat(19)) + record("seventeen", "C".repeat(17)));
        assertEquals(0, run("auto", "0.6.10", runtime, "--phred", "33"), console);
        assertEquals(List.of(25, 19), reads(root.resolve("auto/trimmed_R1.fastq.gz")).stream().map(r -> r.sequence().length()).toList());
        assertTrue(Files.readString(root.resolve("auto/session.yaml")).contains("resolved_original_minimum_length: 18"));
    }

    @Test void actualPhred64QualityAndDistinctNextseqTrimPreserveOriginalQualityBytes() throws Exception {
        String runtime = home(); Files.writeString(root.resolve("reads.fastq"), "@A original comment\nACGTACGT\n+\nhhhh@@@@\n");
        assertEquals(0, run("phred64", "0.6.7", runtime, "--phred", "64", "--quality", "20", "--minimum-length", "1", "--adapter-kind", "none"), console);
        assertEquals(new FastqIO.Read("@A original comment", "ACGT", "hhhh"), reads(root.resolve("phred64/trimmed_R1.fastq.gz")).get(0));
        Files.writeString(root.resolve("reads.fastq"), record("G", "ACGTACGTGGGG"));
        assertEquals(0, run("nextseq", "0.6.10", runtime, "--phred", "33", "--nextseq", "20", "--minimum-length", "1", "--adapter-kind", "none"), console);
        assertEquals("ACGTACGT", reads(root.resolve("nextseq/trimmed_R1.fastq.gz")).get(0).sequence());
    }

    @Test void originalHardtrimKeepsShortAndEmptyReadsWithoutQualityOrLengthFiltering() throws Exception {
        String runtime = home(); Files.writeString(root.resolve("reads.fastq"), record("long", "ACGTACGTAC") + record("short", "AC") + record("empty", ""));
        assertEquals(0, run("hard", "0.6.7", runtime, "--mode", "hardtrim3", "--bases", "5"), console);
        assertEquals(List.of("CGTAC", "AC", ""), reads(root.resolve("hard/trimmed_R1.fastq.gz")).stream().map(FastqIO.Read::sequence).toList());
        assertTrue(Files.isRegularFile(root.resolve("hard/original/reads1.5bp_3prime.fq")));
        assertEquals(2, run("invalid-hard", "0.6.10", runtime, "--mode", "hardtrim3", "--bases", "5", "--minimum-length", "1"));
    }

    @Test void missingProfileSkipsProminentlyAndInvalidInputsFailWithOriginalPartialData() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), record("A/1", "ACGT"));
        assertEquals(3, run("missing", "0.6.10", root.resolve("absent-profile").toString(), "--phred", "33"));
        assertTrue(console.contains("ANALYSIS SKIPPED")); assertFalse(Files.exists(root.resolve("missing/results.tsv")));
        assertTrue(Files.isRegularFile(root.resolve("missing/partial/original/reads1.fastq")));
        assertTrue(Files.readString(root.resolve("missing/WARNINGS.md")).contains("--allow-install --allow-download"));
        Files.writeString(root.resolve("mates.fastq"), record("wrong/2", "TGCA"));
        assertEquals(2, run("mismatch", "0.6.10", "unused-profile", "--phred", "33", "--reads2", root.resolve("mates.fastq").toString()));
        assertTrue(Files.readString(root.resolve("mismatch/WARNINGS.md")).contains("identity/order/count"));
        assertEquals(2, run("invalid-N", "0.6.7", "unused-profile", "--phred", "33", "--maximum-n", "0.5"));
        assertTrue(Files.readString(root.resolve("invalid-N/WARNINGS.md")).contains("N limit"));
    }

    @Test void ordinaryAsciiContractFailsBeforeExecutionWhileOriginalHardtrimPreservesUtf8Headers() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), record("μ-read", "ACGTACGT"));
        assertEquals(2, run("unicode-ordinary", "0.6.10", "unused-profile", "--phred", "33"));
        String warning = Files.readString(root.resolve("unicode-ordinary/WARNINGS.md"));
        assertTrue(warning.contains("rejects non-ASCII read headers/comments")); assertTrue(warning.contains("explicit reversible ASCII-ID mapping"));
        assertFalse(Files.exists(root.resolve("unicode-ordinary/results.tsv")));
        assertEquals(0, run("unicode-hard", "0.6.10", home(), "--mode", "hardtrim5", "--bases", "4"), console);
        assertEquals(new FastqIO.Read("@μ-read original comment", "ACGT", "IIII"), reads(root.resolve("unicode-hard/trimmed_R1.fastq.gz")).get(0));
    }

    @Test void utf8OptionsFilePreservesUnicodeDataPathsAndExplicitCliOverridesWithoutRenamingReads() throws Exception {
        String runtime = home(); Path input = root.resolve("μ-input.fastq"); Files.writeString(input, record("A", "ACGTACGT"));
        Path options = root.resolve("options.json"); var fields = new LinkedHashMap<String, String>();
        fields.put("home", runtime); fields.put("release", "0.6.10"); fields.put("reads", input.toString()); fields.put("phred", "33");
        fields.put("minimum-length", "10"); fields.put("adapter-kind", "none"); fields.put("out", root.resolve("unicode-path-result").toString());
        Files.writeString(options, RegenieRuntime.json(fields));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        assertEquals(0, JLinAlgCli.run(new String[]{"trim-galore-tools", "--options-file", options.toString(), "--minimum-length", "1"}, stream, stream), bytes.toString());
        assertEquals(List.of("ACGTACGT"), reads(root.resolve("unicode-path-result/trimmed_R1.fastq.gz")).stream().map(FastqIO.Read::sequence).toList());
        assertEquals(Files.readString(options), Files.readString(root.resolve("unicode-path-result/input-options.json")));
        assertTrue(Files.readString(root.resolve("unicode-path-result/manifest.yaml")).contains(FollowupSupport.hash(options)));
        fields.put("shell", "untrusted command"); Files.writeString(options, RegenieRuntime.json(fields)); bytes.reset();
        assertEquals(2, JLinAlgCli.run(new String[]{"trim-galore-tools", "--options-file", options.toString()}, stream, stream));
        assertTrue(bytes.toString().contains("Unknown,recursive or non-string option"));
    }
}
