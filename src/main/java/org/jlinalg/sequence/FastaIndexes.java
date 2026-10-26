/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.FastaSequenceIndexCreator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native Java FASTA byte-offset indexes, including actual LF/CRLF line widths. */
public final class FastaIndexes {
    private FastaIndexes() { }
    public static FastaSequenceIndex build(Path reference) throws IOException {
        if (!Files.isRegularFile(reference)) throw new IOException("Reference FASTA does not exist: " + reference);
        try (var stream = Files.newInputStream(reference)) {
            int first = stream.read(), second = stream.read();
            if (first == 0x1f && second == 0x8b) throw new IOException("Native index validation requires uncompressed FASTA; compressed FASTA needs a separate .gzi-aware workflow");
        }
        try {
            FastaSequenceIndex index = FastaSequenceIndexCreator.buildFromFasta(reference);
            if (index.size() == 0) throw new IOException("Reference FASTA has no indexed sequences");
            for (var entry : index) if (entry.getSize() <= 0) throw new IOException("Reference sequence is empty: " + entry.getContig());
            return index;
        } catch (RuntimeException failure) { throw new IOException("Invalid FASTA structure: " + failure.getMessage(), failure); }
    }
    public static void validate(Path reference, Path fai) throws IOException {
        FastaSequenceIndex actual = build(reference), supplied;
        try { supplied = new FastaSequenceIndex(fai); }
        catch (RuntimeException failure) { throw new IOException("Invalid FASTA index: " + fai + ": " + failure.getMessage(), failure); }
        if (!actual.equals(supplied)) throw new IOException("FASTA index does not match the actual sequence lengths, byte offsets or LF/CRLF line widths. Regenerate .fai from this exact FASTA using hts-tools --method fasta-index, preserve the old index, and place the verified new index beside the reference.");
    }
}
