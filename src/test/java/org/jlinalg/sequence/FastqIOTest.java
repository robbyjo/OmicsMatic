/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastqIOTest {
    @TempDir Path directory;
    @Test void streamingPlainAndGzipPairsPreserveIdsAndCountsIncludingEmptyReads() throws Exception {
        Path first = directory.resolve("R1.fastq"), second = directory.resolve("R2.fastq.gz");
        Files.writeString(first, "@A/1 comment\nACGTRY\n+A/1\nIIIIII\n@B/1\n\n+\n\n");
        try (var out = new GZIPOutputStream(Files.newOutputStream(second))) {
            out.write("@A/2 different comment\nNNacgu\n+\n!!!!!!\n@B/2\n\n+\n\n".getBytes(StandardCharsets.UTF_8));
        }
        var result = FastqIO.inspect(first, second);
        assertEquals(new FastqIO.Stats(2, 6, 0, 6), result.read1());
        assertEquals(result.read1(), result.read2());
        try (var reader = new FastqIO.Reader(first)) {
            var read = reader.next(); assertNotNull(read); assertEquals("A", read.pairId());
            assertEquals("@A/1 comment", read.header()); assertEquals("ACGTRY", read.sequence());
        }
    }
    @Test void mismatchedAndDuplicatedMatesAreRejected() throws Exception {
        Path first = directory.resolve("R1.fastq"), second = directory.resolve("R2.fastq");
        Files.writeString(first, "@A/1\nACGT\n+\nIIII\n");
        Files.writeString(second, "@B/2\nACGT\n+\nIIII\n");
        assertThrows(IOException.class, () -> FastqIO.inspect(first, second));
        assertThrows(IOException.class, () -> FastqIO.inspect(first, first));
        Files.writeString(second, "");
        assertThrows(IOException.class, () -> FastqIO.inspect(first, second));
    }
    @Test void malformedWrappedAndInvalidEncodingRecordsAreRejected() throws Exception {
        Path input = directory.resolve("reads.fastq");
        for (String text : new String[]{"@A\nACGT\n+\nIII\n", "@A\nAC-G\n+\nIIII\n", "@A\nACGT\n+B\nIIII\n",
                "@A\nAC\nGT\n+\nIIII\n", "@A\nACGT\n+\nI II\n", "@\nACGT\n+\nIIII\n"}) {
            Files.writeString(input, text); assertThrows(IOException.class, () -> FastqIO.inspect(input, null), text);
        }
        Files.write(input, new byte[]{(byte) 0xff});
        assertThrows(IOException.class, () -> FastqIO.inspect(input, null));
    }
}
