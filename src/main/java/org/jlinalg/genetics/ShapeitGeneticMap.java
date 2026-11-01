/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * SHAPEIT4/5 map parsing, original interpolation and first-variant normalization.
 * Derived from the original MIT-licensed gmap_reader.cpp and variant_map.cpp;
 * the upstream notices are retained in META-INF/licenses/SHAPEIT4-MIT.txt and
 * SHAPEIT5-MIT.txt. This method does not estimate haplotypes or admit a phaser.
 *
 * <p>Duplicate map coordinates retain their order: exact assignments use the
 * last matching point, whereas interpolation retains every original knot.
 * The rate column is ignored. Outside the map, the source uses the whole-map
 * mean slope. Known original out-of-bounds reads are refused explicitly rather
 * than reported as reproduced numerical results. Non-finite values accepted by
 * the permissive original parser remain inspectable but cannot form an admitted
 * numerical map. Callers must separately verify file EOF and source identity.
 */
public final class ShapeitGeneticMap {
    private ShapeitGeneticMap() { }

    /** Exact source versions whose map functions were inspected and controlled. */
    public enum Profile {
        /** Original SHAPEIT4 tag v4.1.3. */
        SHAPEIT4_4_1_3,
        /** Original SHAPEIT4 tag v4.2.2; this does not certify its phasing runtime. */
        SHAPEIT4_4_2_2,
        /** SHAPEIT5 source c34d4db3e99a2f7e23deb727671ae260901a5886. */
        SHAPEIT5_C34D
    }

    /** Compiler-dependent multiplication/addition contract; choose explicitly. */
    public enum Arithmetic {
        /** Original GCC -O3 -mavx2 -mfma build with FP contraction. */
        FUSED_BINARY64,
        /** Original source built with explicit -ffp-contract=off. */
        SPLIT_BINARY64
    }

    /** A source boundary for which no defined original result is claimed. */
    public static final class SourceBoundaryException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        /**
         * Stable diagnostic code retained with a skipped analysis.
         * @serial
         */
        private final String code;
        private SourceBoundaryException(String code, String message) { super(message); this.code = code; }
        /**
         * Read the stable diagnostic code.
         * @return reason code for a skipped dependent analysis
         */
        public String code() { return code; }
    }

    /**
     * Complete-population capacities, never a row-sampling policy.
     * @param mapPoints maximum retained knots
     * @param variants maximum complete target variants
     * @param lineCharacters maximum characters per supplied line
     * @param totalCharacters maximum characters across all supplied lines
     * @param retainedBytes maximum estimated mapping working storage
     */
    public record Limits(int mapPoints, int variants, int lineCharacters, long totalCharacters, long retainedBytes) {
        /** Validate strictly positive capacities. */
        public Limits {
            if (mapPoints < 1 || variants < 1 || lineCharacters < 1 || totalCharacters < 1 || retainedBytes < 1)
                throw new IllegalArgumentException("Require positive complete SHAPEIT map capacities");
        }
        /**
         * Use bounded default capacities.
         * @return defaults that callers may explicitly raise for complete data
         */
        public static Limits defaults() { return new Limits(1000000, 1000000, 1048576, 268435456L, 536870912L); }
    }

    /**
     * One physical map knot, including original duplicates and raw double bits.
     * @param position nonnegative base-pair coordinate
     * @param centiMorgan declared third-column value; may be non-finite for inspection
     */
    public record Point(int position, double centiMorgan) {
        /** Validate the coordinate without silently changing the numerical value. */
        public Point { if (position < 0) throw new IllegalArgumentException("Negative SHAPEIT map coordinate"); }
    }

    /**
     * Parsed original data; NaN follows the original comparison-based order check.
     * @param discardedHeader first line, which the source unconditionally discards
     * @param points every supplied map row, in original order
     * @param coercedNumericTokens count of numeric tokens with no conversion or trailing text
     */
    public record Data(String discardedHeader, List<Point> points, int coercedNumericTokens) {
        /** Preserve rows and reproduce the source's nondecreasing order test. */
        public Data {
            Objects.requireNonNull(discardedHeader); points = List.copyOf(points);
            if (coercedNumericTokens < 0) throw new IllegalArgumentException("Negative coercion count");
            int previousPosition = 0; double previousCm = 0;
            for (Point point : points) {
                if (point.position() < previousPosition || point.centiMorgan() < previousCm)
                    throw new IllegalArgumentException("Wrong order in SHAPEIT genetic map");
                previousPosition = point.position(); previousCm = point.centiMorgan();
            }
        }
    }

    /**
     * Complete, immutable numerical result in target-variant order.
     * @param absoluteCentiMorgan map values before first-variant subtraction
     * @param relativeCentiMorgan values after original first-variant subtraction
     * @param directAssignments original assignment count, including duplicate writes
     * @param interpolatedVariants original interpolation count
     */
    public record Mapping(double[] absoluteCentiMorgan, double[] relativeCentiMorgan,
                          int directAssignments, int interpolatedVariants) {
        /** Defensively retain both complete arrays. */
        public Mapping {
            absoluteCentiMorgan = absoluteCentiMorgan.clone(); relativeCentiMorgan = relativeCentiMorgan.clone();
            if (absoluteCentiMorgan.length != relativeCentiMorgan.length || directAssignments < 0 || interpolatedVariants < 0)
                throw new IllegalArgumentException("Invalid complete SHAPEIT map result");
        }
        /**
         * Read the complete absolute values.
         * @return a defensive copy in original target order
         */
        @Override public double[] absoluteCentiMorgan() { return absoluteCentiMorgan.clone(); }
        /**
         * Read the complete normalized values.
         * @return a defensive copy in original target order
         */
        @Override public double[] relativeCentiMorgan() { return relativeCentiMorgan.clone(); }
    }

    private record NumberValue(double value, boolean coerced) { }
    private static final Pattern INTEGER = Pattern.compile("[\\x09-\\x0d\\x20]*([+-]?)([0-9]+)");
    private static final Pattern DOUBLE = Pattern.compile("[\\x09-\\x0d\\x20]*[+-]?(?:inf(?:inity)?|nan(?:\\([a-z0-9_]*\\))?|0x(?:[0-9a-f]+(?:\\.[0-9a-f]*)?|\\.[0-9a-f]+)(?:p[+-]?[0-9]+)?|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:e[+-]?[0-9]+)?)", Pattern.CASE_INSENSITIVE);

    /**
     * Parse complete already-separated lines using the original C-locale numeric prefixes.
     * @param lines complete input lines, without LF terminators
     * @param limits explicit complete-population capacities
     * @return original header, knots and coercion count
     * @throws IllegalArgumentException for malformed, unordered or excessive input
     * @throws SourceBoundaryException for a source-undefined splitter or atoi overflow
     */
    public static Data parse(Iterable<String> lines, Limits limits) {
        Objects.requireNonNull(lines); Objects.requireNonNull(limits);
        var iterator = lines.iterator(); String header = ""; long characters = 0; int lineNumber = 0, coerced = 0;
        var points = new ArrayList<Point>();
        while (iterator.hasNext()) {
            String line = Objects.requireNonNull(iterator.next()); lineNumber++;
            characters = Math.addExact(characters, line.length());
            if (line.length() > limits.lineCharacters() || characters > limits.totalCharacters() || line.indexOf('\n') >= 0)
                throw new IllegalArgumentException("Complete SHAPEIT map text exceeds capacity or contains an embedded LF; supply complete lines and explicitly raise capacities when needed");
            if (lineNumber == 1) { header = line; continue; }
            var fields = new ArrayList<String>(); int start = 0;
            while (start < line.length()) {
                while (start < line.length() && (line.charAt(start) == ' ' || line.charAt(start) == '\t')) start++;
                int end = start;
                while (end < line.length() && line.charAt(end) != ' ' && line.charAt(end) != '\t') end++;
                if (end > start) fields.add(line.substring(start, end));
                start = end;
            }
            if (!line.isEmpty() && fields.isEmpty()) throw boundary("WHITESPACE_SPLITTER", "Original string_utils dereferences an empty token vector on a whitespace-only line. Supply a corrected map and retain the original input.");
            if (fields.size() != 3) throw new IllegalArgumentException("SHAPEIT map line " + lineNumber + " requires exactly three columns");
            int last = fields.size() - 1; String tail = fields.get(last);
            if (tail.endsWith("\r")) fields.set(last, tail.substring(0, tail.length() - 1));
            if (points.size() >= limits.mapPoints()) throw new IllegalArgumentException("Complete SHAPEIT map row capacity exceeded; raise mapPoints rather than truncating rows");
            var position = integer(fields.get(0)); var cm = decimal(fields.get(2));
            coerced = Math.addExact(coerced, (position.coerced() ? 1 : 0) + (cm.coerced() ? 1 : 0));
            points.add(new Point((int)position.value(), cm.value()));
        }
        return new Data(header, points, coerced);
    }

    /**
     * Apply the original supplied-map functions for a defined, finite source domain.
     * @param data complete original map rows
     * @param suppliedPositions complete sorted target positions; duplicates are retained
     * @param profile exact source profile
     * @param arithmetic compiler arithmetic to reproduce
     * @param limits explicit complete-population capacities
     * @return both original absolute and first-variant-relative values
     * @throws SourceBoundaryException when the original would access outside a vector or overflow its count
     * @throws IllegalArgumentException for invalid, non-finite, unordered or excessive numerical input
     */
    public static Mapping interpolate(Data data, int[] suppliedPositions, Profile profile, Arithmetic arithmetic, Limits limits) {
        Objects.requireNonNull(data); Objects.requireNonNull(profile); Objects.requireNonNull(arithmetic); Objects.requireNonNull(limits);
        int[] positions = positions(suppliedPositions, limits); List<Point> points = data.points();
        if (points.isEmpty()) throw boundary("EMPTY_MAP", "Original interpolation dereferences an empty map. Supply a complete genetic map or explicitly choose the original uniform-rate method.");
        if (points.size() > limits.mapPoints()) throw new IllegalArgumentException("Complete SHAPEIT map exceeds mapPoints capacity");
        for (Point point : points) if (!Double.isFinite(point.centiMorgan()) || point.centiMorgan() < 0)
            throw new IllegalArgumentException("The original parser retained non-finite or negative cM data; it is not admitted as a numerical map. Inspect the parsed values and supply a corrected finite, nonnegative map.");
        Point first = points.get(0), last = points.get(points.size() - 1);
        if (profile == Profile.SHAPEIT4_4_1_3 && positions[positions.length - 1] < first.position())
            throw boundary("ALL_BEFORE_4_1_3", "SHAPEIT4 4.1.3 reads past the target vector when all variants precede the map. Extend map coverage or validate an appropriate corrected upstream runtime; skip dependent phasing until resolved.");
        if (positions[positions.length - 1] > last.position())
            throw boundary("RIGHT_INTERVAL_READ", "These original map functions read past the knot vector before testing its bound on right extrapolation. Extend the supplied map through the last target position, or separately validate a corrected upstream runtime; skip dependent phasing until resolved.");
        if (first.position() == last.position() && positions[0] != first.position())
            throw new IllegalArgumentException("A zero-span map gives a non-finite original mean rate. Supply at least two distinct coordinates covering the target variants.");
        double[] absolute = new double[positions.length]; boolean[] assigned = new boolean[positions.length]; long direct = 0;
        for (int from = 0; from < points.size();) {
            int to = from + 1;
            while (to < points.size() && points.get(to).position() == points.get(from).position()) to++;
            int lower = lower(positions, points.get(from).position()), upper = lower;
            while (upper < positions.length && positions[upper] == points.get(from).position()) upper++;
            direct = Math.addExact(direct, Math.multiplyExact((long)(to - from), upper - lower));
            if (direct > Integer.MAX_VALUE) throw boundary("DIRECT_COUNT_OVERFLOW", "The original signed assignment counter overflows for this duplicate population. Retain all rows and use a separately validated upstream counter fix before dependent phasing.");
            Arrays.fill(absolute, lower, upper, points.get(to - 1).centiMorgan()); Arrays.fill(assigned, lower, upper, true); from = to;
        }
        double meanRate = (last.centiMorgan() - first.centiMorgan()) / (last.position() - first.position());
        int interpolated = 0, closest = 1;
        for (int i = 0; i < positions.length; i++) {
            if (assigned[i]) continue;
            if (positions[i] < first.position()) absolute[i] = combine(first.centiMorgan(), -meanRate, first.position() - positions[i], arithmetic);
            else {
                while (closest < points.size() && positions[i] > points.get(closest).position()) closest++;
                Point left = points.get(closest - 1), right = points.get(closest);
                double rate = (right.centiMorgan() - left.centiMorgan()) / (right.position() - left.position());
                absolute[i] = combine(left.centiMorgan(), rate, positions[i] - left.position(), arithmetic);
            }
            interpolated++;
        }
        return result(absolute, (int)direct, interpolated);
    }

    /**
     * Apply the original no-map assumption of 1 cM/Mb, then subtract the first value.
     * @param suppliedPositions complete sorted target positions
     * @param limits explicit complete-population capacities
     * @return complete absolute and relative values; supplied-map counters are zero
     */
    public static Mapping uniform(int[] suppliedPositions, Limits limits) {
        Objects.requireNonNull(limits); int[] positions = positions(suppliedPositions, limits); double[] absolute = new double[positions.length];
        for (int i = 0; i < positions.length; i++) absolute[i] = positions[i] * 1.0 / 1e6;
        return result(absolute, 0, 0);
    }

    private static Mapping result(double[] absolute, int direct, int interpolated) {
        double[] relative = new double[absolute.length]; double baseline = absolute[0];
        for (int i = 0; i < relative.length; i++) relative[i] = absolute[i] - baseline;
        return new Mapping(absolute, relative, direct, interpolated);
    }
    private static double combine(double base, double rate, double distance, Arithmetic arithmetic) {
        return arithmetic == Arithmetic.FUSED_BINARY64 ? Math.fma(rate, distance, base) : base + rate * distance;
    }
    private static int[] positions(int[] supplied, Limits limits) {
        Objects.requireNonNull(supplied);
        if (supplied.length == 0) throw boundary("EMPTY_VARIANTS", "Original map normalization dereferences the first variant. Supply a nonempty complete target selection; skip an analysis with no selected variants.");
        long memory = Math.multiplyExact(64L, supplied.length);
        if (supplied.length > limits.variants() || memory > limits.retainedBytes() || memory > Runtime.getRuntime().maxMemory() / 2)
            throw new IllegalArgumentException("Complete SHAPEIT mapping exceeds variants, retainedBytes or Java heap capacity; raise the explicit capacity and -Xmx rather than sampling variants");
        int[] positions = supplied.clone();
        for (int i = 0; i < positions.length; i++) if (positions[i] < 1 || i > 0 && positions[i] < positions[i - 1])
            throw new IllegalArgumentException("Require complete nondecreasing positive target positions");
        return positions;
    }
    private static int lower(int[] values, int value) {
        int lo = 0, hi = values.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (values[mid] < value) lo = mid + 1; else hi = mid; }
        return lo;
    }
    private static NumberValue integer(String token) {
        var matcher = INTEGER.matcher(token);
        if (!matcher.lookingAt()) return new NumberValue(0, true);
        boolean negative = matcher.group(1).equals("-"); long limit = negative ? 2147483648L : Integer.MAX_VALUE, value = 0;
        for (int i = matcher.start(2); i < matcher.end(2); i++) {
            int digit = token.charAt(i) - '0';
            if (value > (limit - digit) / 10) throw boundary("ATOI_OVERFLOW", "C atoi overflows outside signed int32. Supply valid map coordinates and retain the original offending token.");
            value = value * 10 + digit;
        }
        return new NumberValue(negative ? -value : value, matcher.end() != token.length());
    }
    private static NumberValue decimal(String token) {
        var matcher = DOUBLE.matcher(token);
        if (!matcher.lookingAt()) return new NumberValue(0, true);
        String value = matcher.group().stripLeading(); String lower = value.toLowerCase(java.util.Locale.ROOT);
        double decoded;
        if (lower.contains("nan")) {
            long payload = 0; int open = lower.indexOf('(');
            if (open >= 0) {
                String digits = lower.substring(open + 1, lower.length() - 1); int radix = 10;
                if (digits.startsWith("0x")) { radix = 16; digits = digits.substring(2); }
                else if (digits.startsWith("0")) radix = 8;
                boolean numeric = !digits.isEmpty();
                for (int i = 0; i < digits.length(); i++) numeric &= Character.digit(digits.charAt(i), radix) >= 0;
                if (numeric) { try { payload = Long.parseUnsignedLong(digits, radix); } catch (NumberFormatException overflow) { payload = -1L; } }
            }
            decoded = Double.longBitsToDouble((lower.startsWith("-") ? Long.MIN_VALUE : 0) | 0x7ff8000000000000L | (payload & 0x0007ffffffffffffL));
        } else if (lower.contains("inf")) decoded = lower.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        else {
            if (lower.contains("0x") && lower.indexOf('p') < 0) value += "p0";
            decoded = Double.parseDouble(value);
        }
        return new NumberValue(decoded, matcher.end() != token.length());
    }
    private static SourceBoundaryException boundary(String code, String action) { return new SourceBoundaryException(code, action); }
}
