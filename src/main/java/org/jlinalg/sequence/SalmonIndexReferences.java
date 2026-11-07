/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Complete reference-table decoder for original Linux x86_64 Salmon 1.0.0,
 * 1.1.0, 1.5.2, 1.8.0 and 1.9.0 Pufferfish indexes. The admitted Cereal ABI is
 * little-endian uint64 vector/string sizes and uint32 lengths/position fields.
 * This checks the complete three files, not the other graph/index structures,
 * a raw FASTA relationship, reference/decoy selection or process provenance.
 * See {@code META-INF/NOTICE-salmon-index.txt}.
 */
public final class SalmonIndexReferences {
    private SalmonIndexReferences() { }

    /**
     * Explicit complete-population capacities. Retained bytes are a conservative
     * metadata estimate, not a JVM heap-size guarantee. Positions are streamed.
     * @param references maximum complete reference count
     * @param positions maximum complete serialized contig-position count
     * @param nameBytes maximum UTF-8 bytes per exact name
     * @param totalNameBytes maximum UTF-8 bytes over all names
     * @param inputBytes maximum combined consumed bytes of the three files
     * @param retainedBytes maximum estimated retained metadata bytes
     */
    public record Limits(int references, long positions, int nameBytes, long totalNameBytes, long inputBytes, long retainedBytes) {
        /** Requires positive finite capacities and the fixed bounded-reader reservation. */
        public Limits {
            if (references < 1 || positions < 1 || nameBytes < 1 || nameBytes == Integer.MAX_VALUE || totalNameBytes < 1 || inputBytes < 1 || retainedBytes < 262144)
                throw new IllegalArgumentException("Positive complete Salmon reference/position/name/byte capacities are required");
        }
    }
    /**
     * One exact full reference, including references absent from contig IDs.
     * @param ordinal zero-based full reference index
     * @param name exact original UTF-8 identity
     * @param originalLength original complete length before source preprocessing
     * @param normalizedLength retained indexed length after source preprocessing
     * @param contigReferenceId original contig-table reference ID, or -1 when no such ID exists
     */
    public record Reference(int ordinal, String name, long originalLength, long normalizedLength, int contigReferenceId) { }
    /**
     * Complete file receipt, returned only after physical end of file.
     * @param bytes complete consumed byte count
     * @param sha256 SHA-256 of the exact complete original file bytes
     */
    public record FileReceipt(long bytes, String sha256) { }
    /**
     * Complete population; retained references without contig IDs are not absent
     * references and are not inferred to be decoys or biological zero expression.
     * @param references exact full population in source order
     * @param contigReferences number of reference-ID extension entries
     * @param positions complete number of consumed contig positions
     * @param originalNameBytes total original name bytes
     * @param estimatedRetainedBytes conservative metadata reservation
     * @param contigTable complete ctable.bin receipt
     * @param originalLengths complete complete_ref_lens.bin receipt
     * @param normalizedLengths complete reflengths.bin receipt
     */
    public record Summary(List<Reference> references, int contigReferences, long positions, long originalNameBytes, long estimatedRetainedBytes,
                          FileReceipt contigTable, FileReceipt originalLengths, FileReceipt normalizedLengths) {
        /** Copies the complete reference population. */
        public Summary { references = List.copyOf(references); }
    }
    /** Staged receiver; callbacks can precede a later integrity error. */
    @FunctionalInterface public interface PositionConsumer {
        /**
         * Receives an unmodified source position and its source extension-map projection.
         * @param ordinal zero-based position-entry ordinal
         * @param contigReferenceId serialized uint32 reference ID
         * @param fullReferenceId projected full-reference ordinal
         * @param name exact full reference name
         * @param start zero-based source start with the orientation bit removed
         * @param forward whether the original high orientation bit is set
         * @throws IOException when staged output cannot be retained
         */
        void accept(long ordinal, int contigReferenceId, int fullReferenceId, String name, long start, boolean forward) throws IOException;
    }

    /**
     * Reads all three complete original files and every serialized position.
     * Names are unique and strictly UTF-8. Both length vectors cover the exact
     * full-name population. Reference extensions are monotone and project each
     * contig reference to an in-range full identity; position starts must lie
     * within its normalized reference. Orientation is preserved separately.
     * No contig-length/end, graph topology or raw-reference equivalence is claimed.
     *
     * <p>All supplied streams are closed on success or failure. Stage callbacks
     * until the complete summary succeeds; a valid prefix is never a result.
     * No architecture/endianness or shortened population is guessed from data.
     * @param contigTable complete original ctable.bin
     * @param originalLengths complete original complete_ref_lens.bin
     * @param normalizedLengths complete original reflengths.bin
     * @param limits explicit whole-population capacities
     * @param consumer staged receiver for every position
     * @param cancellation cancellation check, whose exception propagates
     * @return complete reference/position/file receipts
     * @throws IOException for any malformed, incomplete or out-of-capacity population
     */
    public static Summary read(InputStream contigTable, InputStream originalLengths, InputStream normalizedLengths,
                               Limits limits, PositionConsumer consumer, Runnable cancellation) throws IOException {
        try (InputStream ownedTable = contigTable; InputStream ownedOriginal = originalLengths; InputStream ownedNormalized = normalizedLengths) {
            Objects.requireNonNull(ownedTable); Objects.requireNonNull(ownedOriginal); Objects.requireNonNull(ownedNormalized);
            Objects.requireNonNull(limits); Objects.requireNonNull(consumer); Objects.requireNonNull(cancellation);
            var budget = new Budget(limits, cancellation);
            var table = new Original(ownedTable, budget); var original = new Original(ownedOriginal, budget); var normalized = new Original(ownedNormalized, budget);
            int count = (int) table.size(1, limits.references(), "reference population");
            long retained = add(262144, multiply(512, count)); require(retained <= limits.retainedBytes(), "Salmon reference metadata estimate exceeds capacity");
            var names = new ArrayList<String>(); var unique = new HashSet<String>(); long nameBytes = 0;
            for (int i = 0; i < count; i++) {
                cancellation.run(); int size = (int) table.size(1, limits.nameBytes(), "name byte population");
                nameBytes = add(nameBytes, size); require(nameBytes <= limits.totalNameBytes(), "Complete Salmon reference names exceed byte capacity");
                retained = add(retained, multiply(16, size)); require(retained <= limits.retainedBytes(), "Salmon reference/name metadata estimate exceeds capacity");
                String name = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(table.bytes(size))).toString();
                require(name.chars().noneMatch(c -> c < 32 || c == 127) && unique.add(name), "Empty/duplicate/control-character Salmon reference name"); names.add(name);
            }
            long[] originalSize = lengths(original, count), normalizedSize = lengths(normalized, count);
            for (int i = 0; i < count; i++) require(normalizedSize[i] <= originalSize[i], "Salmon normalized length exceeds the original complete length");
            int indexed = (int) table.size(0, count, "reference-extension population"); int[] fullIds = new int[indexed], reverse = new int[count]; java.util.Arrays.fill(reverse, -1);
            long previous = 0;
            for (int i = 0; i < indexed; i++) {
                cancellation.run(); long extension = table.uint32(), projected = add(i, extension);
                require(extension >= previous && projected < count, "Nonmonotone or out-of-range Salmon reference extension");
                fullIds[i] = (int) projected; reverse[(int) projected] = i; previous = extension;
            }
            long positions = table.size(0, limits.positions(), "contig-position population");
            for (long i = 0; i < positions; i++) {
                cancellation.run(); long id = table.uint32(), packed = table.uint32(); require(id < indexed, "Out-of-range Salmon contig reference ID");
                int full = fullIds[(int) id]; long start = packed & 0x7fffffffL;
                require(start < normalizedSize[full], "Salmon contig start lies outside its normalized reference");
                consumer.accept(i, (int) id, full, names.get(full), start, (packed & 0x80000000L) != 0);
            }
            table.end(); original.end(); normalized.end(); cancellation.run();
            var references = new ArrayList<Reference>();
            for (int i = 0; i < count; i++) references.add(new Reference(i, names.get(i), originalSize[i], normalizedSize[i], reverse[i]));
            return new Summary(references, indexed, positions, nameBytes, retained, table.receipt(), original.receipt(), normalized.receipt());
        }
    }
    private static long[] lengths(Original input, int count) throws IOException {
        require(input.size(0, count, "reference-length population") == count, "Complete Salmon name/length populations differ"); long[] result = new long[count];
        for (int i = 0; i < count; i++) { input.budget.check.run(); result[i] = input.uint32(); require(result[i] > 0, "Zero original/normalized Salmon reference length"); } return result;
    }
    private static final class Budget {
        final Limits limits; final Runnable check; long bytes;
        Budget(Limits limits, Runnable check) { this.limits = limits; this.check = check; }
        void used(int count) throws IOException { bytes = add(bytes, count); require(bytes <= limits.inputBytes(), "Complete Salmon index-reference input exceeds byte capacity"); }
    }
    private static final class Original {
        final BufferedInputStream source; final Budget budget; final MessageDigest digest; long consumed; boolean complete;
        Original(InputStream source, Budget budget) {
            this.source = new BufferedInputStream(source, 65536); this.budget = budget;
            try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
        byte[] bytes(int size) throws IOException {
            budget.check.run(); budget.used(size); byte[] raw = source.readNBytes(size); if (raw.length != size) throw new EOFException("Incomplete Salmon reference-table field");
            consumed = add(consumed, size); digest.update(raw); return raw;
        }
        long uint32() throws IOException { byte[] raw = bytes(4); long value = 0; for (int i = 0; i < 4; i++) value |= (raw[i] & 255L) << (8 * i); return value; }
        long size(long minimum, long maximum, String name) throws IOException {
            byte[] raw = bytes(8); require((raw[7] & 128) == 0, "Unsigned Salmon " + name + " exceeds signed capacity"); long value = 0;
            for (int i = 0; i < 8; i++) value |= (raw[i] & 255L) << (8 * i);
            require(value >= minimum && value <= maximum, "Complete Salmon " + name + " exceeds its declared range/capacity"); return value;
        }
        void end() throws IOException { budget.check.run(); require(source.read() == -1, "Extra Salmon reference-table data; no prefix is complete"); complete = true; }
        FileReceipt receipt() { if (!complete) throw new IllegalStateException("Incomplete Salmon index-reference file"); return new FileReceipt(consumed, HexFormat.of().formatHex(digest.digest())); }
    }
    private static long add(long a, long b) throws IOException { try { return Math.addExact(a, b); } catch (ArithmeticException overflow) { throw new IOException("Salmon reference population/byte capacity overflow", overflow); } }
    private static long multiply(long a, long b) throws IOException { try { return Math.multiplyExact(a, b); } catch (ArithmeticException overflow) { throw new IOException("Salmon reference metadata capacity overflow", overflow); } }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
