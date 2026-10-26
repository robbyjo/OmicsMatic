/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Streaming, unwrapped four-line DNA/RNA FASTQ with Phred+33 quality strings. */
public final class FastqIO {
    private FastqIO() { }

    /** Complete header (including @), sequence and original quality string. */
    public record Read(String header, String sequence, String quality) {
        /** Pair identity: first header token, with a terminal /1 or /2 removed. */
        public String pairId() {
            String token = header.substring(1).split("\\s+", 2)[0];
            return token.replaceFirst("/[12]$", "");
        }
    }

    /** Constant-memory totals for a validated stream. Empty streams have minLength zero. */
    public record Stats(long reads, long bases, int minLength, int maxLength) { }
    /** Totals for a single file or synchronized pair. read2 is null for a single file. */
    public record Inspection(Stats read1, Stats read2) { }

    /** Validate every record, including paired observation order, without storing reads. */
    public static Inspection inspect(Path read1, Path read2) throws IOException {
        if (read2 != null && Files.isSameFile(read1, read2)) throw new IOException("FASTQ mates must be different files");
        try (Reader first = new Reader(read1); Reader second = read2 == null ? null : new Reader(read2)) {
            long n = 0, bases1 = 0, bases2 = 0;
            int min1 = Integer.MAX_VALUE, min2 = Integer.MAX_VALUE, max1 = 0, max2 = 0;
            Read a;
            while ((a = first.next()) != null) {
                n = Math.addExact(n, 1); bases1 = Math.addExact(bases1, a.sequence().length());
                min1 = Math.min(min1, a.sequence().length()); max1 = Math.max(max1, a.sequence().length());
                if (second != null) {
                    Read b = second.next();
                    if (b == null || !a.pairId().equals(b.pairId()))
                        throw new IOException("FASTQ pair count/identity/order mismatch at record " + n);
                    bases2 = Math.addExact(bases2, b.sequence().length());
                    min2 = Math.min(min2, b.sequence().length()); max2 = Math.max(max2, b.sequence().length());
                }
            }
            if (second != null && second.next() != null) throw new IOException("FASTQ mate has extra records");
            return new Inspection(new Stats(n, bases1, n == 0 ? 0 : min1, max1),
                second == null ? null : new Stats(n, bases2, n == 0 ? 0 : min2, max2));
        }
    }

    /** Reader for plain or .gz FASTQ. Wrapped records and non-IUPAC bases are rejected. */
    public static final class Reader implements AutoCloseable {
        private final BufferedReader input;
        private final Path file;
        private long record;

        public Reader(Path file) throws IOException {
            this.file = file;
            InputStream raw = Files.newInputStream(file);
            try {
                InputStream decoded = file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gz") ? new GZIPInputStream(raw) : raw;
                input = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
            } catch (IOException | RuntimeException failure) { raw.close(); throw failure; }
        }

        public Read next() throws IOException {
            String header = input.readLine();
            if (header == null) return null;
            record++;
            String sequence = input.readLine(), plus = input.readLine(), quality = input.readLine();
            if (!header.startsWith("@") || header.length() < 2 || Character.isWhitespace(header.charAt(1))
                    || sequence == null || plus == null || quality == null || !plus.startsWith("+")
                    || sequence.length() != quality.length() || sequence.length() > 10_000_000)
                throw invalid("Require complete unwrapped four-line records with equal sequence/quality lengths");
            for (int i = 0; i < sequence.length(); i++) {
                if ("ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(sequence.charAt(i)) < 0)
                    throw invalid("Non-IUPAC DNA/RNA base");
                if (quality.charAt(i) < 33 || quality.charAt(i) > 126)
                    throw invalid("Quality characters must use Phred+33 ASCII");
            }
            if (plus.length() > 1) {
                String repeated = plus.substring(1).split("\\s+", 2)[0];
                if (!repeated.equals(header.substring(1).split("\\s+", 2)[0]))
                    throw invalid("Repeated + header differs from read identity");
            }
            return new Read(header, sequence, quality);
        }
        private IOException invalid(String reason) { return new IOException(reason + " at FASTQ record " + record + " in " + file); }
        @Override public void close() throws IOException { input.close(); }
    }

    /** Canonical four-line writer to a new plain or .gz file; complete headers and qualities are preserved. */
    public static final class Writer implements AutoCloseable {
        private final BufferedWriter output;
        public Writer(Path file) throws IOException {
            OutputStream raw = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                OutputStream encoded = file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gz") ? new GZIPOutputStream(raw) : raw;
                output = new BufferedWriter(new OutputStreamWriter(encoded, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException failure) { raw.close(); throw failure; }
        }
        public void write(Read read) throws IOException {
            if (read == null || read.header() == null || read.sequence() == null || read.quality() == null || !read.header().startsWith("@")
                    || read.header().length() < 2 || Character.isWhitespace(read.header().charAt(1)) || read.header().indexOf('\n') >= 0 || read.header().indexOf('\r') >= 0
                    || read.sequence().length() > 10_000_000 || read.sequence().length() != read.quality().length()
                    || read.sequence().chars().anyMatch(c -> "ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(c) < 0)
                    || read.quality().chars().anyMatch(c -> c < 33 || c > 126)) throw new IllegalArgumentException("Invalid unwrapped FASTQ record");
            output.write(read.header()); output.write('\n'); output.write(read.sequence()); output.write("\n+\n"); output.write(read.quality()); output.write('\n');
        }
        @Override public void close() throws IOException { output.close(); }
    }
}
