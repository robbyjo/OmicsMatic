/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Complete streaming decoder for bulk inferential counts emitted by original
 * Linux x86_64 Salmon 1.0.0, 1.1.0, 1.5.2, 1.8.0 and 1.9.0. The source writes
 * sample-major native double vectors and a separate tab-delimited name stream.
 * This decoder fixes the admitted ABI to little-endian IEEE754 binary64; it
 * neither guesses the producer architecture nor quantifies reads. See
 * {@code META-INF/NOTICE-salmon.txt}. Both original streams must be complete.
 */
public final class SalmonInferentialReplicates {
    private SalmonInferentialReplicates() { }

    /** Consumer of every decoded value in original sample/target order. */
    @FunctionalInterface public interface Consumer {
        /**
         * Receives an unmodified finite nonnegative estimated fragment count.
         * @param replicate zero-based replicate index
         * @param targetIndex zero-based index in the declared complete target list
         * @param target exact transcript identity
         * @param count original binary64 value, including a possible signed zero
         * @throws IOException when the staged consumer cannot retain a value
         */
        void accept(int replicate, int targetIndex, String target, double count) throws IOException;
    }

    /**
     * Complete-population receipt; it is produced only after both gzip footers,
     * exact end of both streams and all values have passed validation.
     * @param targets exact ordered transcript identities
     * @param replicates complete observed replicate count
     * @param values complete observed value count
     * @param minimumReplicateTotal smallest sum of estimated fragment counts
     * @param maximumReplicateTotal largest sum of estimated fragment counts
     * @param negativeZeroObserved whether an original negative zero was retained
     */
    public record Summary(List<String> targets, int replicates, long values,
                          double minimumReplicateTotal, double maximumReplicateTotal,
                          boolean negativeZeroObserved) {
        /** Copies the target list. */
        public Summary { targets = List.copyOf(targets); }
    }

    /**
     * Decodes a complete declared inferential population using bounded working
     * memory. Both supplied streams are closed on success or failure. Each is
     * exactly one original gzip member; extra members, trailing bytes, CRC/size
     * errors, wrong identities, truncated/extra values and nonfinite/negative
     * counts fail. No summary is returned for a successful prefix.
     *
     * <p>The consumer can receive values before a later integrity error. Write to
     * staged output and admit it only after this method returns successfully.
     * Replicate counts and the complete target list must come from separately
     * checked quantifier metadata; no truncated-prefix inference is performed.
     * Bootstrap and Gibbs values remain estimates, not raw observed read counts.
     *
     * @param namesGzip complete original names.tsv.gz
     * @param countsGzip complete original bootstraps.gz
     * @param expectedTargets complete ordered original target population
     * @param expectedReplicates exact positive replicate count
     * @param maximumValues explicit maximum complete population size
     * @param consumer staged receiver of every value
     * @param cancellation cancellation check, whose exception propagates
     * @return complete receipt
     * @throws IOException for any malformed, incomplete or out-of-capacity stream
     */
    public static Summary read(InputStream namesGzip, InputStream countsGzip,
                               List<String> expectedTargets, int expectedReplicates,
                               long maximumValues, Consumer consumer, Runnable cancellation) throws IOException {
        Objects.requireNonNull(namesGzip, "namesGzip"); Objects.requireNonNull(countsGzip, "countsGzip");
        try (InputStream ownedNames = namesGzip; InputStream ownedCounts = countsGzip) {
            Objects.requireNonNull(consumer, "consumer"); Objects.requireNonNull(cancellation, "cancellation");
            List<String> targets = List.copyOf(expectedTargets);
            if (targets.isEmpty() || expectedReplicates < 1 || maximumValues < 1)
                throw new IOException("Salmon inferential counts require complete nonempty targets and a positive declared replicate/value capacity");
            long expectedValues;
            try { expectedValues = Math.multiplyExact((long) targets.size(), expectedReplicates); }
            catch (ArithmeticException overflow) { throw new IOException("Salmon inferential population exceeds integer capacity", overflow); }
            if (expectedValues > maximumValues) throw new IOException("Complete Salmon inferential population exceeds declared value capacity; no prefix is admitted");
            var unique = new HashSet<String>();
            for (String target : targets) {
                if (target.isEmpty() || target.chars().anyMatch(c -> c < 32 || c == 127) || !unique.add(target))
                    throw new IOException("Empty, duplicate or control-character Salmon target identity");
                encoded(target); // Reject unpaired surrogate replacement before reading any values.
            }
            cancellation.run();
            try (InputStream names = new OriginalGzip(ownedNames); InputStream counts = new OriginalGzip(ownedCounts)) {
                for (int i = 0; i < targets.size(); i++) {
                    cancellation.run();
                    for (byte value : encoded(targets.get(i))) if (names.read() != (value & 255))
                        throw new IOException("Salmon inferential target identity/order differs at index " + i);
                    if (names.read() != (i + 1 == targets.size() ? '\n' : '\t'))
                        throw new IOException("Salmon inferential names require exact tab separators and one terminal newline");
                }
                if (names.read() != -1) throw new IOException("Extra Salmon inferential names or trailing name data");
                byte[] raw = new byte[8]; ByteBuffer number = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                double minimum = Double.POSITIVE_INFINITY, maximum = 0; boolean negativeZero = false;
                for (int sample = 0; sample < expectedReplicates; sample++) {
                    cancellation.run(); double total = 0, compensation = 0;
                    for (int i = 0; i < targets.size(); i++) {
                        if ((i & 255) == 0) cancellation.run();
                        int received = counts.readNBytes(raw, 0, raw.length);
                        if (received != raw.length) throw new EOFException("Incomplete Salmon inferential value at replicate " + sample + ", target " + i);
                        double value = number.getDouble(0);
                        if (!Double.isFinite(value) || value < 0) throw new IOException("Nonfinite/negative Salmon inferential count at replicate " + sample + ", target " + i);
                        negativeZero |= Double.doubleToRawLongBits(value) == Long.MIN_VALUE;
                        double adjusted = value - compensation, next = total + adjusted;
                        compensation = (next - total) - adjusted; total = next;
                        if (!Double.isFinite(total)) throw new IOException("Salmon inferential replicate total exceeds finite binary64 capacity");
                        consumer.accept(sample, i, targets.get(i), value);
                    }
                    minimum = Math.min(minimum, total); maximum = Math.max(maximum, total);
                }
                if (counts.read() != -1) throw new IOException("Extra Salmon inferential values; the full population differs from metadata");
                cancellation.run();
                return new Summary(targets, expectedReplicates, expectedValues, minimum, maximum, negativeZero);
            }
        }
    }

    private static byte[] encoded(String target) throws IOException {
        try {
            ByteBuffer result = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(target));
            byte[] bytes = new byte[result.remaining()]; result.get(bytes); return bytes;
        } catch (CharacterCodingException invalid) { throw new IOException("Invalid Unicode Salmon target identity", invalid); }
    }

    /** A single complete gzip member, including strict compressed end-of-file. */
    private static final class OriginalGzip extends InputStream {
        private final PushbackInputStream source;
        private final Inflater inflater;
        private final byte[] compressed = new byte[8192], one = new byte[1];
        private final CRC32 crc = new CRC32();
        private int inputLength;
        private long decoded;
        private boolean ended, complete, closed;

        OriginalGzip(InputStream raw) throws IOException {
            source = new PushbackInputStream(raw, compressed.length);
            CRC32 header = new CRC32(); int first = headerByte(header), second = headerByte(header);
            if (first != 31 || second != 139 || headerByte(header) != 8) throw new IOException("Invalid original Salmon gzip header");
            int flags = headerByte(header); if ((flags & 224) != 0) throw new IOException("Reserved Salmon gzip header flags");
            for (int i = 0; i < 6; i++) headerByte(header);
            if ((flags & 4) != 0) { int size = headerByte(header) | headerByte(header) << 8; for (int i = 0; i < size; i++) headerByte(header); }
            for (int flag : new int[]{8, 16}) if ((flags & flag) != 0) {
                int size = 0; while (headerByte(header) != 0) if (++size > 65536) throw new IOException("Salmon gzip header text exceeds capacity");
            }
            if ((flags & 2) != 0 && (required() | required() << 8) != (header.getValue() & 65535)) throw new IOException("Salmon gzip header CRC differs");
            inflater = new Inflater(true);
        }
        private int required() throws IOException { int value = source.read(); if (value < 0) throw new EOFException("Truncated original Salmon gzip stream"); return value; }
        private int headerByte(CRC32 header) throws IOException { int value = required(); header.update(value); return value; }
        private long uint32() throws IOException { long result = 0; for (int i = 0; i < 4; i++) result |= (long) required() << (8 * i); return result; }
        private void footer() throws IOException {
            int remaining = inflater.getRemaining();
            if (remaining > 0) source.unread(compressed, inputLength - remaining, remaining);
            if (uint32() != crc.getValue() || uint32() != decoded) throw new IOException("Salmon gzip payload CRC or size differs");
            if (source.read() != -1) throw new IOException("Extra member or trailing bytes after original Salmon gzip payload");
            inflater.end(); ended = true; complete = true;
        }
        @Override public int read() throws IOException { return read(one, 0, 1) < 0 ? -1 : one[0] & 255; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (closed) throw new IOException("Closed Salmon gzip stream"); if (length == 0) return 0; if (complete) return -1;
            while (true) {
                if (inflater.finished()) { footer(); return -1; }
                if (inflater.needsInput()) {
                    inputLength = source.read(compressed);
                    if (inputLength < 0) throw new EOFException("Truncated Salmon deflate payload");
                    if (inputLength == 0) continue;
                    inflater.setInput(compressed, 0, inputLength);
                }
                try {
                    int n = inflater.inflate(bytes, offset, length);
                    if (n > 0) { crc.update(bytes, offset, n); decoded = (decoded + n) & 0xffffffffL; return n; }
                    if (inflater.needsDictionary() || !inflater.needsInput() && !inflater.finished()) throw new IOException("Invalid Salmon deflate state");
                } catch (DataFormatException invalid) { throw new IOException("Invalid Salmon deflate payload", invalid); }
            }
        }
        @Override public void close() throws IOException { if (!closed) { closed = true; if (!ended) inflater.end(); source.close(); } }
    }
}
