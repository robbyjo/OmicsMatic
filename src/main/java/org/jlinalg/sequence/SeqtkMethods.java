/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Algorithms adapted from seqtk (MIT), see META-INF/LICENSE-seqtk.txt. */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Native, explicitly bounded seqtk sampling, trimfq and terminal-repeat methods. */
public final class SeqtkMethods {
    private SeqtkMethods() { }
    public static final int MAX_SAMPLE_SIZE = 50_000_000;
    public static final long MAX_RECORDS = (1L << 53) - 1;
    private static final double[] ERROR_PROBABILITIES = errorProbabilities();
    private static double[] errorProbabilities() {
        double[] probabilities = new double[127];
        for (int i = 33; i < probabilities.length; i++) probabilities[i] = StrictMath.pow(10, -(i - 33) / 10.0);
        return probabilities;
    }

    /** seqtk's MT19937-64 generator and 53-bit uniform conversion, not Java Random. */
    public static final class Random {
        private final long[] state = new long[312];
        private int position = 312;
        public Random(long seed) {
            state[0] = seed;
            for (int i = 1; i < state.length; i++)
                state[i] = 6364136223846793005L * (state[i - 1] ^ (state[i - 1] >>> 62)) + i;
        }
        public double nextDouble() {
            if (position == state.length) {
                for (int i = 0; i < state.length; i++) {
                    long joined = (state[i] & 0xffffffff80000000L) | (state[(i + 1) % 312] & 0x7fffffffL);
                    state[i] = state[(i + 156) % 312] ^ (joined >>> 1)
                        ^ ((joined & 1) == 0 ? 0 : 0xb5026f5aa96619e9L);
                }
                position = 0;
            }
            long value = state[position++];
            value ^= (value >>> 29) & 0x5555555555555555L;
            value ^= (value << 17) & 0x71d67fffeda60000L;
            value ^= (value << 37) & 0xfff7eee000000000L;
            value ^= value >>> 43;
            return (value >>> 11) * 0x1.0p-53;
        }
    }

    /** Sorted zero-based reservoir indices; same membership/order as seqtk sample -2. */
    public static long[] sampleIndices(long records, int requested, long seed) {
        if (records < 0 || records > MAX_RECORDS || requested < 0 || requested > MAX_SAMPLE_SIZE)
            throw new IllegalArgumentException("Sampling requires 0..2^53-1 records and 0..50000000 requested records/pairs");
        long[] selected = new long[(int) Math.min(records, requested)];
        if (requested == 0) return selected;
        Random random = new Random(seed);
        for (long index = 0; index < records; index++) {
            double draw = random.nextDouble(); // seqtk consumes a draw even while filling the reservoir
            long slot = index < requested ? index : (long) (draw * (index + 1));
            if (slot < selected.length) selected[(int) slot] = index;
        }
        Arrays.sort(selected);
        return selected;
    }

    /** Quality trimming settings; any positive fixed cut/length disables quality trimming. */
    public record TrimOptions(double errorRate, int minimumRetainedLength, int left, int right, int length) {
        public TrimOptions {
            if (!Double.isFinite(errorRate) || errorRate < 0 || errorRate > 1
                    || minimumRetainedLength < 0 || minimumRetainedLength > 10_000_000
                    || left < 0 || right < 0 || length < 0
                    || left > 10_000_000 || right > 10_000_000 || length > 10_000_000)
                throw new IllegalArgumentException("Invalid trimfq error rate, retained length or fixed cut/length");
        }
    }
    public record Trimmed(FastqIO.Read read, int start, int end, boolean minimumLengthFallback) { }

    /** Mott maximum scoring interval and seqtk's quality-window fallback; no read filtering. */
    public static Trimmed trim(FastqIO.Read read, TrimOptions options) {
        if (read == null || read.sequence() == null || read.quality() == null
                || read.sequence().length() != read.quality().length()
                || read.quality().chars().anyMatch(c -> c < 33 || c > 126))
            throw new IllegalArgumentException("Require equal sequence/quality lengths and Phred+33 qualities");
        int length = read.sequence().length(), begin = 0, end = length;
        boolean fallback = false;
        if (options.left > 0 || options.right > 0 || options.length > 0) {
            begin = options.left; end = length - options.right;
            if (begin >= end) begin = end = 0;
            if (options.length > 0 && end - begin > options.length) end = begin + options.length;
        } else if (length > options.minimumRetainedLength) {
            double score = 0, maximum = 0;
            int nextBegin = 0;
            for (int i = 0; i < length; i++) {
                int quality = Math.max(36, read.quality().charAt(i));
                score += options.errorRate - ERROR_PROBABILITIES[quality];
                if (score > maximum) { maximum = score; begin = nextBegin; end = i + 1; }
                if (score < 0) { score = 0; nextBegin = i + 1; }
            }
            if (maximum == 0) { begin = 0; end = options.minimumRetainedLength; fallback = true; }
            if (end - begin < options.minimumRetainedLength) {
                fallback = true;
                int sum = 0;
                for (int i = 0; i < options.minimumRetainedLength; i++) sum += read.quality().charAt(i) - 33;
                int best = sum; begin = 0;
                for (int i = options.minimumRetainedLength; i < length; i++) {
                    sum += read.quality().charAt(i) - read.quality().charAt(i - options.minimumRetainedLength);
                    if (sum > best) { best = sum; begin = i - options.minimumRetainedLength + 1; }
                }
                end = begin + options.minimumRetainedLength;
            }
        }
        return new Trimmed(new FastqIO.Read(read.header(), read.sequence().substring(begin, end),
            read.quality().substring(begin, end)), begin, end, fallback);
    }

    /** Random access permits chunked FASTA operation without loading a chromosome into memory. */
    public interface Bases {
        long length();
        char base(long index) throws IOException;
    }
    /** Zero-based half-open terminal segment; end is left or right in the supplied orientation. */
    public record Telomere(long start, long end, String terminalEnd, long score) { }
    public record TeloOptions(String motif, int penalty, int maximumDrop, int minimumScore) {
        public TeloOptions {
            if (motif == null || !motif.matches("[ACGTacgt]{1,31}") || penalty < 0
                    || penalty > 10_000_000 || maximumDrop < 0 || minimumScore < 1)
                throw new IllegalArgumentException("Telomere motif requires 1..31 A/C/G/T bases, nonnegative penalty/drop and positive minimum score");
            motif = motif.toUpperCase(Locale.ROOT);
        }
    }
    /** seqtk telo's asymmetric endpoint scoring, rotation matching and maximum-drop stopping. */
    public static List<Telomere> telomeres(Bases sequence, TeloOptions options) throws IOException {
        if (sequence.length() < 0) throw new IllegalArgumentException("Negative sequence length");
        int width = options.motif.length();
        long mask = (1L << (2 * width)) - 1;
        var rotations = new HashSet<Long>();
        for (int offset = 0; offset < width; offset++) {
            long key = 0;
            for (int i = 0; i < width; i++) key = (key << 2) | baseCode(options.motif.charAt((i + offset) % width));
            rotations.add(key);
        }
        var result = new ArrayList<Telomere>();
        long stop = 0;
        for (boolean reverse : new boolean[]{false, true}) {
            long score = 0, maximum = 0, bestIndex = -1, consecutive = 0, key = 0;
            for (long step = 0, limit = sequence.length() - (reverse ? stop : 0); step < limit; step++) {
                long index = reverse ? sequence.length() - 1 - step : step;
                int code = baseCode(sequence.base(index)); boolean hit = false;
                if (code >= 0) {
                    key = ((key << 2) | (reverse ? 3 - code : code)) & mask;
                    hit = ++consecutive >= width && rotations.contains(key);
                } else { consecutive = 0; key = 0; }
                if (reverse ? step + 1 >= width : step >= width) score += hit ? 1 : -options.penalty;
                if (score > maximum) { maximum = score; bestIndex = index; }
                else if (maximum - score > options.maximumDrop) break;
            }
            if (maximum >= options.minimumScore) {
                if (reverse) result.add(new Telomere(bestIndex, sequence.length(), "right", maximum));
                else { stop = bestIndex + 1; result.add(new Telomere(0, stop, "left", maximum)); }
            }
        }
        return List.copyOf(result);
    }
    private static int baseCode(char base) {
        return switch (base) { case 'A', 'a' -> 0; case 'C', 'c' -> 1; case 'G', 'g' -> 2; case 'T', 't' -> 3; default -> -1; };
    }
}
