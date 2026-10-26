/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixedTrimCliTest {
    @TempDir Path directory;
    private String output;
    private int run(String name, String... extra) {
        var args = new java.util.ArrayList<>(List.of("sequence-qc", "--method", "trim-fixed", "--reads", directory.resolve("input.fastq").toString(), "--out", directory.resolve(name).toString(), "--no-config"));
        args.addAll(List.of(extra)); var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int status = JLinAlgCli.run(args.toArray(String[]::new), stream, stream); output = bytes.toString(); return status;
    }
    @Test void nativePostAdapterClippingPreservesHeadersQualityAndInputHashWithoutPython() throws Exception {
        Files.writeString(directory.resolve("input.fastq"), "@A original comment\nACGT\n+\n!#$%\n@B\nA\n+\nI\n");
        assertEquals(0, run("clip", "--first-base", "2"), output);
        try (var reader = new FastqIO.Reader(directory.resolve("clip/trimmed_R1.fastq.gz"))) {
            var read = reader.next(); assertNotNull(read); assertEquals("@A original comment", read.header());
            assertEquals("CGT", read.sequence()); assertEquals("#$%", read.quality()); assertNull(reader.next());
        }
        assertTrue(Files.readString(directory.resolve("clip/results.tsv")).contains("output_reads_or_pairs\t1"));
        assertTrue(Files.readString(directory.resolve("clip/manifest.yaml")).contains("input_sha256"));
    }
    @Test void pairedFilteringIsSynchronizedAndMismatchesQuarantinePartialOutputs() throws Exception {
        Path first = directory.resolve("input.fastq"), second = directory.resolve("mate.fastq");
        Files.writeString(first, "@A/1\nACGT\n+\nIIII\n@B/1\nACGT\n+\nIIII\n");
        Files.writeString(second, "@A/2\nA\n+\nI\n@B/2\nTGCA\n+\nIIII\n");
        assertEquals(0, run("paired", "--reads2", second.toString(), "--first-base", "2"), output);
        var stats = FastqIO.inspect(directory.resolve("paired/trimmed_R1.fastq.gz"), directory.resolve("paired/trimmed_R2.fastq.gz"));
        assertEquals(1, stats.read1().reads());
        try (var reader = new FastqIO.Reader(directory.resolve("paired/trimmed_R1.fastq.gz"))) { assertEquals("B", reader.next().pairId()); }
        Files.writeString(second, "@wrong/2\nTGCA\n+\nIIII\n");
        assertEquals(2, run("mismatch", "--reads2", second.toString(), "--first-base", "2"));
        assertFalse(Files.exists(directory.resolve("mismatch/results.tsv")));
        assertTrue(Files.isRegularFile(directory.resolve("mismatch/partial/trimmed_R1.fastq.gz")));
        assertTrue(Files.readString(directory.resolve("mismatch/WARNINGS.md")).contains("Required action"));
    }
    @Test void allFilteredOutputHasProminentWarningAndInvalidCoordinatesFailPersistently() throws Exception {
        Files.writeString(directory.resolve("input.fastq"), "@A\nAC\n+\nII\n");
        assertEquals(0, run("empty", "--first-base", "3"), output);
        assertTrue(output.contains("QC WARNINGS"));
        assertTrue(Files.readString(directory.resolve("empty/WARNINGS.md")).contains("NO READS RETAINED"));
        assertEquals(0, FastqIO.inspect(directory.resolve("empty/trimmed_R1.fastq.gz"), null).read1().reads());
        assertEquals(2, run("invalid", "--first-base", "4", "--last-base", "2"));
        assertFalse(Files.exists(directory.resolve("invalid/results.tsv")));
    }
}
