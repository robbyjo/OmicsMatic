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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenomicIntervalCliTest {
    @TempDir Path directory;
    String output;
    private int run(String method, String... extra) {
        var args = new ArrayList<>(List.of("intervals", "--method", method, "--input", directory.resolve("a.tsv").toString(),
            "--genome-build", "GRCh38", "--out", directory.resolve(method).toString(), "--no-config"));
        args.addAll(List.of(extra));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int status = JLinAlgCli.run(args.toArray(String[]::new), stream, stream);
        output = bytes.toString(); return status;
    }
    @Test void fourOperationsPublishCoordinateAndInputProvenance() throws Exception {
        Files.writeString(directory.resolve("a.tsv"), "chrom\tstart\tend\tid\nchr1\t0\t100\tquery\n");
        Path mask = directory.resolve("b.tsv");
        Files.writeString(mask, "chrom\tstart\tend\tid\nchr1\t10\t40\ta\nchr1\t20\t60\tb\n");
        for (String method : List.of("intersect", "coverage", "subtract")) {
            assertEquals(0, run(method, "--mask", mask.toString()), output);
            assertTrue(Files.readString(directory.resolve(method).resolve("manifest.yaml")).contains("mask_sha256"));
            assertTrue(Files.readString(directory.resolve(method).resolve("manifest.yaml")).contains("zero-based-half-open"));
        }
        assertEquals(2, DelimitedData.read(directory.resolve("intersect/intervals.tsv")).rows().size());
        assertEquals("50", DelimitedData.read(directory.resolve("coverage/intervals.tsv")).rows().get(0)[4]);
        assertEquals(2, DelimitedData.read(directory.resolve("subtract/intervals.tsv")).rows().size());
        assertEquals(0, run("merge"), output);
    }
    @Test void invalidInputsAndInapplicableOptionsDoNotPublish() throws Exception {
        Files.writeString(directory.resolve("a.tsv"), "chrom\tstart\tend\tid\nchr1\t10\t10\tinvalid\n");
        assertEquals(2, run("merge")); assertFalse(Files.exists(directory.resolve("merge")));
        Files.writeString(directory.resolve("a.tsv"), "chrom\tstart\tend\tid\nchr1\t0\t10\ta\n");
        assertEquals(2, run("merge", "--same-strand", "true"));
        assertEquals(2, run("merge", "--min-fraction-a", "0.5"));
        assertEquals(2, run("coverage"));
    }
}
