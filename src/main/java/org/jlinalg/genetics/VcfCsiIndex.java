/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import htsjdk.samtools.Bin;
import htsjdk.samtools.BinningIndexContent;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.index.tabix.TabixIndexCreator;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.variant.vcf.VCFCodec;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;

/** Native CSI serialization of HTSJDK's coordinate-sorted BGZF/VCF index. */
public final class VcfCsiIndex {
    private VcfCsiIndex() { }

    /** Index inventory; this is not genotype or allele validation. */
    public record Summary(int contigs, int bins, long chunks) { }

    /**
     * Create a new BGZF-compressed CSI with VCF auxiliary fields.
     *
     * <p>Uses the established HTSJDK tabix interval/chunk construction and its
     * linear offsets, serialized as CSI min_shift=14/depth=5. Coordinates must
     * fit the tabix range (through 2^29 bases). A larger genome coordinate is
     * rejected by the underlying index builder; nothing is truncated. This
     * method never overwrites an existing index or modifies the input.</p>
     *
     * @param bgzfVcf coordinate-sorted BGZF VCF
     * @param newIndex new output filename, conventionally input.vcf.gz.csi
     * @return serialized nonempty-contig, bin and chunk counts
     * @throws IOException for invalid input or an unwritable/existing destination
     */
    public static Summary create(Path bgzfVcf, Path newIndex) throws IOException {
        if (!Files.isRegularFile(bgzfVcf) || Files.exists(newIndex)) {
            throw new IOException("Require an existing BGZF VCF and a NEW CSI output");
        }
        try (var input = new BufferedInputStream(Files.newInputStream(bgzfVcf))) {
            if (!BlockCompressedInputStream.isValidFile(input)) throw new IOException("Require BGZF, not plain VCF or ordinary gzip");
        }
        if (BlockCompressedInputStream.checkTermination(bgzfVcf)
                != BlockCompressedInputStream.FileTermination.HAS_TERMINATOR_BLOCK) {
            throw new IOException("Require a complete BGZF file with its EOF terminator");
        }
        long size = Files.size(bgzfVcf);
        if (size >= (1L << 48)) throw new IOException("BGZF exceeds virtual file-offset capacity");
        long dataEnd = (size - BlockCompressedStreamConstants.EMPTY_GZIP_BLOCK.length) << 16;
        final TabixIndex tabix;
        try (var input = new BlockCompressedInputStream(bgzfVcf.toFile())) {
            var codec = new VCFCodec();
            var source = codec.makeIndexableSourceFromStream(input);
            var lines = (LineIterator)source;
            codec.readHeader(lines);
            var creator = new TabixIndexCreator(TabixFormat.VCF);
            var seen = new HashSet<String>();
            String contig = null;
            int previous = 0;
            while (!codec.isDone(lines)) {
                long position = source.getPosition();
                var feature = codec.decodeLoc(lines);
                if (feature == null) continue;
                if (feature.getStart() < 1 || feature.getEnd() < feature.getStart()
                        || feature.getEnd() > 536870912) throw new IOException("VCF coordinate outside 1..2^29");
                if (!feature.getContig().equals(contig)) {
                    if (!seen.add(feature.getContig())) throw new IOException("VCF contig appears in disjoint sections");
                    contig = feature.getContig(); previous = 0;
                }
                if (feature.getStart() < previous) throw new IOException("VCF coordinates are not sorted");
                previous = feature.getStart();
                creator.addFeature(feature, position);
            }
            // Stop before the empty BGZF EOF block. Including it in the final
            // chunk causes an observed read failure in original Minimac/Savvy.
            tabix = (TabixIndex)creator.finalizeIndex(dataEnd);
        } catch (RuntimeException failure) {
            throw new IOException("Cannot index VCF within sorted tabix coordinate bounds", failure);
        }
        if (tabix.getSequenceNames().isEmpty()) throw new IOException("Cannot index an empty VCF");
        var names = new ByteArrayOutputStream();
        for (String name : tabix.getSequenceNames()) {
            if (name.isEmpty() || name.codePoints().anyMatch(Character::isISOControl)) {
                throw new IOException("Invalid CSI contig identifier");
            }
            names.writeBytes(name.getBytes(StandardCharsets.UTF_8));
            names.write(0);
            if (names.size() > 16777216) throw new IOException("CSI contig-name bytes exceed bound");
        }
        int bins = 0;
        long chunks = 0;
        Path temporary = Files.createTempFile(newIndex.toAbsolutePath().getParent(), ".vcf-csi-", ".tmp");
        try {
        try (var out = new LittleEndianOutputStream(new BlockCompressedOutputStream(
                Files.newOutputStream(temporary, StandardOpenOption.WRITE), (Path)null))) {
            out.write(new byte[]{'C','S','I',1});
            out.writeInt(14);
            out.writeInt(5);
            out.writeInt(Math.addExact(28, names.size()));
            var format = tabix.getFormatSpec();
            out.writeInt(format.flags);
            out.writeInt(format.sequenceColumn);
            out.writeInt(format.startPositionColumn);
            out.writeInt(format.endPositionColumn);
            out.writeInt(format.metaCharacter);
            out.writeInt(format.numHeaderLinesToSkip);
            out.writeInt(names.size());
            out.write(names.toByteArray());
            out.writeInt(tabix.getSequenceNames().size());
            BinningIndexContent[] references = tabix.getIndices();
            if (references.length != tabix.getSequenceNames().size()) {
                throw new IOException("HTSJDK reference/index inventory differs");
            }
            for (BinningIndexContent reference : references) {
                var dataBins = new ArrayList<Bin>();
                if (reference != null) for (Bin bin : reference.getBins()) {
                    if (bin != null && bin.containsChunks()) dataBins.add(bin);
                }
                out.writeInt(dataBins.size());
                for (Bin bin : dataBins) {
                    int id = bin.getBinNumber();
                    if (id < 0 || id >= 37449) throw new IOException("Unexpected CSI data-bin number");
                    int level = 0;
                    for (int k = 1; k <= 5; k++) if (id >= ((1 << (3*k))-1)/7) level = k;
                    int firstBin = ((1 << (3*level))-1)/7;
                    int start = (id-firstBin) << (14+3*(5-level));
                    long offset = reference.getLinearIndex().getMinimumOffset(start+1);
                    out.writeInt(id);
                    out.writeLong(offset);
                    out.writeInt(bin.getChunkList().size());
                    for (var chunk : bin.getChunkList()) {
                        out.writeLong(chunk.getChunkStart());
                        out.writeLong(chunk.getChunkEnd());
                        chunks++;
                    }
                    bins++;
                }
            }
            out.writeLong(0); // Optional n_no_coor; a VCF has no unplaced alignment records.
        }
        Files.move(temporary, newIndex);
        return new Summary(tabix.getSequenceNames().size(), bins, chunks);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
