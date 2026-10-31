/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bounded native admission and original Eagle 2.4.1 genetic-map interpolation.
 * The complete four-column map is scanned; only the selected chromosome is
 * retained. Query coordinates are positive one-based base pairs; the first row
 * of each chromosome may be a signed map anchor, including the original linear
 * resource's zero-base anchor. Genetic positions are signed cM and results are
 * Morgans. The supplied rate column is validated but ignored
 * by the original algorithm, which derives interval slopes from genetic positions.
 *
 * <p>The source is MapInterpolater.cpp in the official Eagle 2.4.1 distribution.
 * Its strict upper-bound lookup and arithmetic order are preserved, including
 * chromosome-dependent extrapolation. This class does not phase genotypes or
 * establish the reference assembly, map provenance or historical paper parity.</p>
 *
 * <p>Admission is deliberately stricter than the original stream extraction:
 * require four fields per nonblank line, finite signed cM, positive chromosomes,
 * unique contiguous chromosome sections, and at least two selected-chromosome rows.
 * Chromosome sections need not be numerically ordered; their supplied final
 * chromosome determines the original sentinel and is never silently changed.
 * Malformed trailing text is an error, never silently accepted as a partial map.
 * The caller owns decoding, compressed/encoded byte limits and input integrity.</p>
 */
public final class EagleGeneticMap {
    /** The exact original header; CR at the end is not silently removed. */
    public static final String HEADER = "chr position COMBINED_rate(cM/Mb) Genetic_Map(cM)";

    /** Reject extrapolation, or explicitly retain the original source boundaries. */
    public enum Outside { REQUIRE_BRACKET, SOURCE_BOUNDARIES }

    /** Location relative to the selected map's original rows. */
    public enum Region { BEFORE_FIRST_ROW, BETWEEN_ROWS, EXACT_ROW, AFTER_LAST_ROW }

    /**
     * Complete logical-character, line, row and retained-point capacities.
     * Input buffers and estimated native arrays are bounded, without claiming
     * that these figures equal total JVM resident memory.
     * @param maximumRows all nonblank data rows, including unselected chromosomes
     * @param maximumSelectedRows retained rows for the selected chromosome
     * @param maximumLineCharacters characters excluding LF, including any CR
     * @param maximumCharacters complete decoded characters, including newlines
     * @param maximumChromosomes distinct ordered chromosomes in the input
     */
    public record Limits(long maximumRows, int maximumSelectedRows, int maximumLineCharacters,
            long maximumCharacters, int maximumChromosomes) {
        public Limits {
            if (maximumRows < 1 || maximumSelectedRows < 2 || maximumSelectedRows > 50_000_000
                    || maximumLineCharacters < HEADER.length() || maximumLineCharacters > 1_048_576
                    || maximumCharacters < 1 || maximumChromosomes < 1)
                throw new IllegalArgumentException("Invalid complete Eagle map capacities");
        }
        /** @return bounded defaults; no accepted map is truncated */
        public static Limits defaults() { return new Limits(50_000_000, 2_000_000, 16_384, 8_589_934_592L, 1_000); }
    }

    /**
     * Full-file inventory and native retained-array accounting.
     * @param rows all admitted data rows
     * @param selectedRows selected chromosome's rows
     * @param chromosomes all admitted chromosomes
     * @param finalChromosome final chromosome in the complete original map
     * @param maximumStoredChromosome highest numeric chromosome having an original stored row or sentinel
     * @param lines physical lines, including blank lines and the header
     * @param characters complete logical characters
     * @param retainedArrayBytes exact bytes of primitive position/cM arrays
     */
    public record Summary(long rows, int selectedRows, int chromosomes, int finalChromosome, int maximumStoredChromosome,
            long lines, long characters, long retainedArrayBytes) { }

    /**
     * Original arithmetic result and its explicit positional disposition.
     * @param morgans genetic position in Morgans; source extrapolation may be negative
     * @param region position relative to the original map rows
     */
    public record Estimate(double morgans, Region region) {
        public Estimate {
            if (!Double.isFinite(morgans)) throw new IllegalArgumentException("Nonfinite Eagle map estimate");
            Objects.requireNonNull(region, "region");
        }
    }

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private final int chromosome;
    private final int[] positions;
    private final double[] cm;
    private final int size;
    private final Outside outside;
    private final Summary summary;

    private EagleGeneticMap(int chromosome, Builder selected, Outside outside, Summary summary) {
        this.chromosome = chromosome; this.positions = selected.positions; this.cm = selected.cm;
        this.size = selected.size; this.outside = outside; this.summary = summary;
    }

    /**
     * Read and verify the complete decoded map without closing the caller's reader.
     * @param input caller-owned decoded text
     * @param chromosome exact positive integer chromosome, with no alias conversion
     * @param outside explicit extrapolation policy
     * @param limits complete population and character capacities
     * @return selected immutable map after complete EOF admission
     * @throws IOException for malformed, incomplete, unselected or over-capacity input
     */
    public static EagleGeneticMap read(Reader input, int chromosome, Outside outside, Limits limits) throws IOException {
        return read(input, chromosome, outside, limits, () -> { });
    }

    /**
     * Read a complete map with a caller-supplied cancellation/integrity check.
     * The check runs before reading, every 4,096 characters and at completion.
     * @param input caller-owned decoded text
     * @param chromosome selected positive integer chromosome
     * @param outside explicit extrapolation policy
     * @param limits complete population and character capacities
     * @param check caller check; exceptions propagate and prevent admission
     * @return selected immutable map after complete EOF admission
     * @throws IOException for malformed, incomplete, unselected or over-capacity input
     */
    public static EagleGeneticMap read(Reader input, int chromosome, Outside outside, Limits limits,
            Runnable check) throws IOException {
        Objects.requireNonNull(input, "input"); Objects.requireNonNull(outside, "outside");
        Objects.requireNonNull(limits, "limits"); Objects.requireNonNull(check, "check");
        if (chromosome < 1) throw new IllegalArgumentException("Require a positive integer Eagle chromosome");
        check.run(); var lines = new Lines(input, limits, check);
        if (!HEADER.equals(lines.next())) throw lines.failure("Require exact original Eagle map header, including LF-only header termination");
        var selected = new Builder(limits.maximumSelectedRows());
        long rows = 0; int chromosomes = 0, priorChr = 0, priorBp = 0, maximumStoredChr = 0; double priorGen = 0;
        var sections = new HashSet<Integer>();
        String line;
        while ((line = lines.next()) != null) {
            String body = trimAscii(line);
            if (body.isEmpty()) continue;
            if (rows == limits.maximumRows()) throw lines.failure("Complete Eagle map row capacity exceeded");
            String[] fields = body.split("[ \\t\\r\\f\\x0b]+", -1);
            if (fields.length != 4) throw lines.failure("Require exactly four fields per nonblank Eagle map row; no truncated prefix admission");
            int chr = integer(fields[0], true, lines), bp = integer(fields[1], false, lines);
            double rate = decimal(fields[2], lines), gen = decimal(fields[3], lines);
            if (chr == priorChr) {
                if (bp <= 0) throw lines.failure("Map rows after the first anchor must have positive positions");
                if (bp <= priorBp) throw lines.failure("Map positions must be strictly increasing within each chromosome");
                if (gen < priorGen) throw lines.failure("Genetic positions must not decrease within each chromosome");
                long width = (long)bp - priorBp;
                if (width > Integer.MAX_VALUE) throw lines.failure("Original signed-32-bit interval width would overflow");
                double derived = (gen - priorGen) / (1e-6 * (int)width);
                if (!Double.isFinite(derived)) throw lines.failure("Derived original interval rate is nonfinite");
                maximumStoredChr = Math.max(maximumStoredChr, chr);
            } else {
                if (!sections.add(chr)) throw lines.failure("Map chromosome sections must not reappear");
                if (++chromosomes > limits.maximumChromosomes()) throw lines.failure("Complete Eagle map chromosome capacity exceeded");
            }
            // rate is deliberately parsed and checked; original Eagle ignores it.
            if (!Double.isFinite(rate)) throw lines.failure("Nonfinite supplied map rate");
            if (chr == chromosome) selected.add(bp, gen, lines);
            rows++; priorChr = chr; priorBp = bp; priorGen = gen;
        }
        if (selected.size < 2) throw lines.failure("Require at least two rows for the selected chromosome; absent/single-row source behavior is not admitted");
        if (priorBp > Integer.MAX_VALUE - 1_000_000_000)
            throw lines.failure("Original final-map sentinel exceeds signed 32-bit position capacity");
        if (!Double.isFinite(priorGen + 1_000)) throw lines.failure("Original final-map sentinel cM is nonfinite");
        check.run();
        maximumStoredChr = Math.max(maximumStoredChr, priorChr); // The original inserts a final-file sentinel.
        var summary = new Summary(rows, selected.size, chromosomes, priorChr, maximumStoredChr, lines.number, lines.characters,
                12L * selected.positions.length);
        return new EagleGeneticMap(chromosome, selected, outside, summary);
    }

    /** @return exact selected integer chromosome */
    public int chromosome() { return chromosome; }
    /** @return first selected original map-row coordinate, which may be a signed anchor */
    public int firstPosition() { return positions[0]; }
    /** @return last selected original map-row position */
    public int lastPosition() { return positions[size - 1]; }
    /** @return complete input inventory and array accounting */
    public Summary summary() { return summary; }

    /**
     * Interpolate with the original strict upper-bound arithmetic.
     * Before the first row, use the first two rows' slope. A chromosome with no
     * sentinel has a constant right tail only if a higher stored chromosome
     * supplies the upper lookup; otherwise that original lookup fails. The final
     * file chromosome receives a 1 cM/Mb sentinel at lastBp+1e9. Beyond it, a
     * higher stored chromosome makes the original value constant; without one,
     * reaching the sentinel makes the original lookup fail.
     * REQUIRE_BRACKET rejects all queries outside the original selected endpoints.
     * @param position positive signed-32-bit one-based coordinate
     * @return original Morgans and explicit interval/extrapolation disposition
     * @throws IllegalArgumentException for unsupported outside coordinates or nonfinite arithmetic
     */
    public Estimate estimate(int position) {
        if (position < 1) throw new IllegalArgumentException("Require positive one-based map query position");
        if (outside == Outside.REQUIRE_BRACKET && (position < firstPosition() || position > lastPosition()))
            throw new IllegalArgumentException("Map query is outside explicitly required original bracket");
        int low = 0, high = size;
        while (low < high) { int mid = (low + high) >>> 1; if (positions[mid] <= position) low = mid + 1; else high = mid; }
        Region region = position < firstPosition() ? Region.BEFORE_FIRST_ROW : position > lastPosition()
                ? Region.AFTER_LAST_ROW : low > 0 && positions[low - 1] == position ? Region.EXACT_ROW : Region.BETWEEN_ROWS;
        final double value;
        if (low == size) {
            boolean higher = chromosome < summary.maximumStoredChromosome();
            if (chromosome != summary.finalChromosome()) {
                if (!higher) throw new IllegalArgumentException("Original upper-bound lookup reaches the end of the complete map");
                value = 0.01 * cm[size - 1];
            }
            else {
                int end = positions[size - 1] + 1_000_000_000;
                if (position >= end) {
                    if (!higher) throw new IllegalArgumentException("Query reaches/passes the original final-map sentinel");
                    value = 0.01 * (cm[size - 1] + 1_000);
                } else value = 0.01 * (cm[size - 1] + 1_000 + 1e-6 * (position - end));
            }
        } else {
            int upper = Math.max(1, low); // Original constructor does not insert each chromosome's first row.
            double rate = (cm[upper] - cm[upper - 1]) / (1e-6 * (positions[upper] - positions[upper - 1]));
            value = 0.01 * (cm[upper] + 1e-6 * rate * (position - positions[upper]));
        }
        return new Estimate(value, region);
    }

    private static int integer(String value, boolean positive, Lines lines) throws IOException {
        if (!value.matches("[+-]?[0-9]+")) throw lines.failure("Require signed decimal integer map fields");
        try { int n = Integer.parseInt(value); if (!positive || n > 0) return n; }
        catch (NumberFormatException ignored) { /* handled below */ }
        throw lines.failure("Map integer exceeds signed-32-bit capacity or chromosome is not positive");
    }
    private static double decimal(String value, Lines lines) throws IOException {
        if (!DECIMAL.matcher(value).matches()) throw lines.failure("Require finite decimal map rate and cM fields");
        try {
            double n = Double.parseDouble(value);
            if (n == 0) {
                for (int i = 0; i < value.length() && value.charAt(i) != 'e' && value.charAt(i) != 'E'; i++)
                    if (value.charAt(i) >= '1' && value.charAt(i) <= '9')
                        throw lines.failure("Nonzero decimal map field underflows binary64; do not silently replace it with zero");
            }
            if (Double.isFinite(n)) return n;
        }
        catch (NumberFormatException ignored) { /* handled below */ }
        throw lines.failure("Nonfinite or invalid map rate/cM");
    }
    private static boolean whitespace(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\f' || c == 11; }
    private static String trimAscii(String value) {
        int first = 0, end = value.length();
        while (first < end && whitespace(value.charAt(first))) first++;
        while (end > first && whitespace(value.charAt(end - 1))) end--;
        return value.substring(first, end);
    }
    private static final class Builder {
        private int[] positions; private double[] cm; private int size; private final int maximum;
        Builder(int maximum) { this.maximum = maximum; positions = new int[Math.min(32, maximum)]; cm = new double[positions.length]; }
        void add(int bp, double gen, Lines lines) throws IOException {
            if (size == maximum) throw lines.failure("Selected Eagle map row capacity exceeded");
            if (size == positions.length) {
                int capacity = (int)Math.min(maximum, 2L * positions.length);
                positions = Arrays.copyOf(positions, capacity); cm = Arrays.copyOf(cm, capacity);
            }
            positions[size] = bp; cm[size++] = gen;
        }
    }
    private static final class Lines {
        private final Reader input; private final Limits limits; private final Runnable check;
        private long characters, number;
        Lines(Reader input, Limits limits, Runnable check) {
            this.input = input instanceof BufferedReader ? input : new BufferedReader(input, 8192);
            this.limits = limits; this.check = check;
        }
        String next() throws IOException {
            var value = new StringBuilder();
            for (;;) {
                int c = input.read();
                if (c == -1) { if (value.isEmpty()) return null; number++; return value.toString(); }
                if (characters == limits.maximumCharacters()) throw failure("Complete decoded map character capacity exceeded");
                characters++; if ((characters & 4095) == 0) check.run();
                if (c == '\n') { number++; return value.toString(); }
                if (value.length() == limits.maximumLineCharacters()) throw failure("Map line character capacity exceeded");
                value.append((char)c);
            }
        }
        IOException failure(String message) { return new IOException(message + " near map line " + (number + 1)); }
    }
}
