/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GtfAnnotationsTest {
    @TempDir Path directory;
    private String row(String contig, String feature, String coordinates, String strand, String attributes) {
        return contig + "\ttest\t" + feature + "\t" + coordinates + "\t.\t" + strand + "\t.\t" + attributes + "\n";
    }
    @Test void streamsExactCoordinatesCountsAndUnknownStrandWithoutConflatingParents() throws Exception {
        Path file = directory.resolve("annotation.gtf");
        Files.writeString(file, "# exact reference annotation\n" + row("chr1", "gene", "1\t900", "+", "gene_id \"g1\";")
            + row("chr1", "exon", "1\t100", "+", "gene_id \"g1\"; transcript_id \"t1\";")
            + row("chr1", "exon", "201\t300", "+", "transcript_id \"t1\"; gene_id \"g1\";")
            + row("chr1", "exon", "501\t600", ".", "gene_id \"g2\"; transcript_id \"t2\";"));
        var summary = GtfAnnotations.inspect(file, Map.of("chr1", 1000L));
        assertEquals(4, summary.records()); assertEquals(3, summary.exons()); assertEquals(2, summary.genes());
        assertEquals(2, summary.transcripts()); assertEquals(1, summary.unknownStrandExons());
        assertEquals(java.util.Set.of("g1", "g2"), summary.geneIds());
        assertThrows(UnsupportedOperationException.class, () -> summary.geneIds().add("mutated"));
    }
    @Test void rejectsWrongReferenceBoundsAndNonGtfParentContracts() throws Exception {
        Path file = directory.resolve("bad.gtf");
        for (String line : new String[]{row("1", "exon", "1\t10", "+", "gene_id \"g\"; transcript_id \"t\";"),
            row("chr1", "exon", "0\t10", "+", "gene_id \"g\"; transcript_id \"t\";"),
            row("chr1", "exon", "1\t1001", "+", "gene_id \"g\"; transcript_id \"t\";"),
            row("chr1", "exon", "1\t10", "+", "ID=e;Parent=t"),
            row("chr1", "exon", "1\t10", "+", "gene_id \"g\";"),
            row("chr1", "exon", "1\t10", "?", "gene_id \"g\"; transcript_id \"t\";")}) {
            Files.writeString(file, line); assertThrows(java.io.IOException.class, () -> GtfAnnotations.inspect(file, Map.of("chr1", 1000L)));
        }
    }
    @Test void rejectsAbsentExonsAndDuplicateParentAttributes() throws Exception {
        Path file = directory.resolve("empty.gtf"); Files.writeString(file, "# no exons\n");
        assertThrows(java.io.IOException.class, () -> GtfAnnotations.inspect(file, Map.of("chr1", 1000L)));
        Files.writeString(file, row("chr1", "exon", "1\t10", "+", "gene_id \"g\";gene_id \"other\";transcript_id \"t\";"));
        assertThrows(java.io.IOException.class, () -> GtfAnnotations.inspect(file, Map.of("chr1", 1000L)));
        Files.writeString(file, row("chr1", "exon", "1\t10", "+", "gene_id \"g\"; transcript_id \"t\"; gene_id \"other\";"));
        assertThrows(java.io.IOException.class, () -> GtfAnnotations.inspect(file, Map.of("chr1", 1000L)));
    }
}
