/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrimGaloreReadsCliTest {
    @TempDir Path root;
    private String console;
    private int run(String name, String... extra) {
        var args = new ArrayList<>(List.of("trim-galore-reads", "--reads", root.resolve("reads.fastq").toString(), "--out", root.resolve(name).toString()));
        args.addAll(List.of(extra)); var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int code = JLinAlgCli.run(args.toArray(String[]::new), stream, stream); console = bytes.toString(); return code;
    }
    private static String record(String id, String sequence) { return "@" + id + " original comment\n" + sequence + "\n+\n" + "I".repeat(sequence.length()) + "\n"; }
    private static String[] finalizeOptions(String... extras) {
        var result = new ArrayList<>(List.of("--mode", "finalize", "--release", "0.6.10", "--input-stage", "post-cutadapt")); result.addAll(List.of(extras)); return result.toArray(String[]::new);
    }

    @Test void pairedNativeFinalizationPreservesIdsQualitiesAndDeclaresTheActualStage() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), "@A/1 original comment\nACGTA\n+\n!#$%&\n");
        Files.writeString(root.resolve("mate.fastq"), record("A/2", "TGCAT"));
        assertEquals(0, run("clipped", finalizeOptions("--reads2", root.resolve("mate.fastq").toString(), "--minimum-length", "1", "--clip-r1", "3", "--three-prime-clip-r1", "3")), console);
        try (var reader = new FastqIO.Reader(root.resolve("clipped/trimmed_R1.fastq.gz"))) {
            assertEquals(new FastqIO.Read("@A/1 original comment", "TA", "%&"), reader.next()); assertNull(reader.next());
        }
        assertEquals(1, FastqIO.inspect(root.resolve("clipped/trimmed_R1.fastq.gz"), root.resolve("clipped/trimmed_R2.fastq.gz")).read1().reads());
        String session = Files.readString(root.resolve("clipped/session.yaml")); assertTrue(session.contains("post-cutadapt")); assertTrue(session.contains("no adapter detection"));
        assertTrue(Files.readString(root.resolve("clipped/manifest.yaml")).contains("input_sha256"));
        assertTrue(Files.readString(root.resolve("clipped/WARNINGS.md")).contains("fixed cuts were skipped")); assertTrue(console.contains("QC WARNINGS"));
    }

    @Test void originalRescueOrderingAndDefaultsAreProminentInWarningsAndSeparateStreams() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), record("A/1", "A".repeat(10)) + record("B/1", "A".repeat(40)));
        Files.writeString(root.resolve("mate.fastq"), record("A/2", "C".repeat(100)) + record("B/2", "C".repeat(40)));
        assertEquals(0, run("rescue", finalizeOptions("--reads2", root.resolve("mate.fastq").toString(), "--minimum-length", "50", "--maximum-length", "60", "--retain-unpaired", "true")), console);
        assertEquals(0, FastqIO.inspect(root.resolve("rescue/trimmed_R1.fastq.gz"), root.resolve("rescue/trimmed_R2.fastq.gz")).read1().reads());
        assertEquals(1, FastqIO.inspect(root.resolve("rescue/unpaired_R1.fastq.gz"), null).read1().reads());
        assertEquals(2, FastqIO.inspect(root.resolve("rescue/unpaired_R2.fastq.gz"), null).read1().reads());
        String warnings = Files.readString(root.resolve("rescue/WARNINGS.md")); assertTrue(warnings.contains("below the declared paired minimum"));
        assertTrue(warnings.contains("rescued mates exceed maximum-length")); assertTrue(warnings.contains("Required action"));
        assertTrue(Files.readString(root.resolve("rescue/results.tsv")).contains("oversized_rescued_mates\t1"));
    }

    @Test void ordinarySingleFractionCaseAndLowercaseNFollowTheOriginalWithAnActionableWarning() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), record("A", "NACG") + record("B", "nnnn"));
        assertEquals(0, run("fraction", finalizeOptions("--minimum-length", "1", "--maximum-n", "0.5", "--gzip", "false")), console);
        try (var reader = new FastqIO.Reader(root.resolve("fraction/trimmed_R1.fastq"))) { assertEquals("B", reader.next().pairId()); assertNull(reader.next()); }
        assertTrue(Files.readString(root.resolve("fraction/WARNINGS.md")).contains("rejects every read with any uppercase N"));
        assertTrue(Files.readString(root.resolve("fraction/read-status.tsv")).contains("TOO_MANY_N"));
    }

    @Test void hardtrimKeepsLastBasesAndShortEmptyReadsButRejectsFilterOptions() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), "@A\nACGT\n+\n!#$%\n" + record("B", "A") + record("C", ""));
        assertEquals(0, run("hard", "--mode", "hardtrim3", "--bases", "2"), console);
        try (var reader = new FastqIO.Reader(root.resolve("hard/trimmed_R1.fastq.gz"))) {
            assertEquals(new FastqIO.Read("@A", "GT", "$%"), reader.next()); assertEquals("A", reader.next().sequence()); assertEquals("", reader.next().sequence()); assertNull(reader.next());
        }
        assertTrue(Files.readString(root.resolve("hard/WARNINGS.md")).contains("including empty reads"));
        assertEquals(2, run("invalid", "--mode", "hardtrim3", "--bases", "2", "--minimum-length", "1"));
        assertFalse(Files.exists(root.resolve("invalid/results.tsv"))); assertTrue(Files.readString(root.resolve("invalid/WARNINGS.md")).contains("Hardtrim bypasses"));
    }

    @Test void inapplicableOriginalReleaseStageAndRescueSettingsFailWithPersistedRecovery() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), record("A/1", "ACGT")); Files.writeString(root.resolve("mate.fastq"), record("A/2", "TGCA"));
        assertEquals(2, run("v67", "--mode", "finalize", "--release", "0.6.7", "--input-stage", "post-cutadapt", "--reads2", root.resolve("mate.fastq").toString(), "--maximum-length", "100"));
        assertTrue(Files.readString(root.resolve("v67/WARNINGS.md")).contains("0.6.7 rejects paired"));
        assertEquals(2, run("stage", "--mode", "finalize", "--release", "0.6.10", "--input-stage", "raw"));
        assertTrue(Files.readString(root.resolve("stage/WARNINGS.md")).contains("requires --input-stage post-cutadapt"));
        assertEquals(2, run("rescue-invalid", finalizeOptions("--reads2", root.resolve("mate.fastq").toString(), "--retain-unpaired", "true", "--minimum-length", "50", "--orphan-min1", "35")));
        assertTrue(Files.readString(root.resolve("rescue-invalid/WARNINGS.md")).contains("must exceed the paired minimum"));
        assertTrue(console.contains("READ PROCESSING FAILED"));
    }

    @Test void mismatchedPairQuarantinesStreamsAndPublishesNoSuccessfulResults() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), record("A/1", "ACGT") + record("B/1", "ACGT"));
        Files.writeString(root.resolve("mate.fastq"), record("A/2", "TGCA") + record("wrong/2", "TGCA"));
        assertEquals(2, run("mismatch", finalizeOptions("--reads2", root.resolve("mate.fastq").toString(), "--minimum-length", "1")));
        assertFalse(Files.exists(root.resolve("mismatch/results.tsv"))); assertFalse(Files.exists(root.resolve("mismatch/trimmed_R1.fastq.gz")));
        assertTrue(Files.isRegularFile(root.resolve("mismatch/partial/trimmed_R1.fastq.gz"))); assertTrue(Files.isRegularFile(root.resolve("mismatch/partial/read-status.tsv")));
        assertTrue(Files.readString(root.resolve("mismatch/analysis-status.json")).contains("failed"));
        assertTrue(Files.readString(root.resolve("mismatch/WARNINGS.md")).contains("new output directory"));
    }

    @Test void existingDestinationIsPreservedAndUnknownOptionsGiveProminentRecovery() throws Exception {
        Files.writeString(root.resolve("reads.fastq"), record("A", "ACGT")); Files.createDirectories(root.resolve("existing")); Files.writeString(root.resolve("existing/marker"), "preserve");
        assertEquals(2, run("existing", "--mode", "hardtrim5", "--bases", "2")); assertEquals("preserve", Files.readString(root.resolve("existing/marker")));
        assertTrue(console.contains("Output already exists"));
        assertEquals(2, run("unknown", "--mode", "hardtrim5", "--bases", "2", "--rrbs", "true"));
        assertTrue(console.contains("Unknown option: --rrbs")); assertTrue(console.contains("Required action"));
    }
}
