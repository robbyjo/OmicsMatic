/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import java.io.IOException;
import java.nio.file.Path;

/** Native indexed uncompressed FASTA access using an index built from the actual bytes. */
public final class IndexedSequences implements AutoCloseable {
    private final FastaSequenceIndex index;
    private final IndexedFastaSequenceFile reader;
    public IndexedSequences(Path fasta) throws IOException {
        index = FastaIndexes.build(fasta);
        reader = new IndexedFastaSequenceFile(fasta, index);
    }
    public FastaSequenceIndex index() { return index; }
    public SeqtkMethods.Bases sequence(String name) throws IOException {
        if (!index.hasIndexEntry(name)) throw new IOException("Reference FASTA has no sequence named " + name);
        long length = index.getIndexEntry(name).getSize();
        return new SeqtkMethods.Bases() {
            private long offset = -1;
            private byte[] chunk;
            @Override public long length() { return length; }
            @Override public char base(long position) throws IOException {
                if (position < 0 || position >= length) throw new IOException("Sequence coordinate out of bounds");
                if (chunk == null || position < offset || position >= offset + chunk.length) {
                    offset = position / 65536 * 65536;
                    try { chunk = reader.getSubsequenceAt(name, offset + 1, Math.min(length, offset + 65536)).getBases(); }
                    catch (RuntimeException failure) { throw new IOException("Cannot read indexed FASTA: " + failure.getMessage(), failure); }
                }
                return (char) (chunk[(int) (position - offset)] & 255);
            }
        };
    }
    @Override public void close() throws IOException { reader.close(); }
}
