/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Complete bulk {@code quant.sf} identity and printed-normalization audit for
 * original Salmon 1.0.0, 1.1.0, 1.5.2, 1.8.0 and 1.9.0. It preserves original
 * decimal tokens and bounds the uncertainty introduced by fixed-point output.
 * It does not reproduce mapping, fitted effective lengths or abundance inference.
 */
public final class SalmonQuantification {
    private SalmonQuantification() { }
    private static final String HEADER = "Name\tLength\tEffectiveLength\tTPM\tNumReads";
    private static final Pattern FIXED = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    private static final BigDecimal MILLION = BigDecimal.valueOf(1000000), TWO = BigDecimal.valueOf(2);
    private static final BigDecimal EPSILON = BigDecimal.ONE.divide(new BigDecimal(2).pow(53));
    private static final BigDecimal NORMAL_MINIMUM = new BigDecimal(Double.MIN_NORMAL);
    private static final BigDecimal SUBNORMAL_UNIT = new BigDecimal(Double.MIN_VALUE);
    private static final MathContext DOWN = new MathContext(80, RoundingMode.FLOOR), UP = new MathContext(80, RoundingMode.CEILING);

    /**
     * A separately protected member of the complete quantified target population.
     * Raw reference decoys/deduplication exclusions require a separate ledger.
     * @param name exact original transcript identity
     * @param length expected original complete transcript length, in nucleotides
     */
    public record Target(String name, long length) {
        /** Validates identity and original uint32 transcript-length domain. */
        public Target {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty() || name.chars().anyMatch(c -> c < 32 || c == 127)
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(name) || length < 1 || length > 0xffffffffL)
                throw new IllegalArgumentException("Invalid Salmon target identity or uint32 length");
        }
    }

    /**
     * A retained fixed-point source token and its exact nonnegative decimal value.
     * A printed negative zero remains visible in {@code text}.
     * @param text complete original token
     * @param value exactly matching decimal value including its scale
     */
    public record Printed(String text, BigDecimal value) {
        /** Validates exact text/value binding without replacing source decimals. */
        public Printed {
            Objects.requireNonNull(text, "text"); Objects.requireNonNull(value, "value");
            if (!FIXED.matcher(text).matches() || !new BigDecimal(text).equals(value)
                    || value.signum() < 0 || !Double.isFinite(value.doubleValue()))
                throw new IllegalArgumentException("Invalid nonnegative finite Salmon fixed-point value");
        }
    }

    /**
     * One original row; the row order is preserved.
     * @param target exact identity/length
     * @param effectiveLength fitted effective length, not raw reference length
     * @param tpm relative abundance in transcripts per million
     * @param estimatedFragments fractional estimated counts, not observed integer reads
     */
    public record Row(Target target, Printed effectiveLength, Printed tpm, Printed estimatedFragments) {
        /** Requires every original field. */
        public Row { Objects.requireNonNull(target); Objects.requireNonNull(effectiveLength); Objects.requireNonNull(tpm); Objects.requireNonNull(estimatedFragments); }
    }

    /**
     * Complete bounded parse. Construction checks precision and unique population.
     * @param rows every source row in original order
     * @param countPrecision source sigDigits for effective length and estimated counts
     * @param characters complete consumed character count
     */
    public record Table(List<Row> rows, int countPrecision, long characters) {
        /** Copies rows and checks that no duplicate/precision-changing family is admitted. */
        public Table {
            rows = List.copyOf(rows);
            if (rows.isEmpty() || countPrecision < 0 || countPrecision > 1000 || characters < 1)
                throw new IllegalArgumentException("Invalid Salmon table population/precision/census");
            var names = new HashSet<String>();
            for (Row row : rows) if (!names.add(row.target().name()) || row.effectiveLength().value().scale() != countPrecision
                    || row.estimatedFragments().value().scale() != countPrecision || row.tpm().value().scale() != 6)
                throw new IllegalArgumentException("Duplicate or unexpected-precision Salmon row");
        }
    }

    /**
     * Per-transcript conservative compatibility interval; it is not a confidence interval.
     * @param target exact transcript identity
     * @param minimumCompatibleTpm smallest allowed normalized TPM under printed intervals
     * @param maximumCompatibleTpm largest allowed normalized TPM under printed intervals
     * @param printedPointTpm normalization using printed point counts/lengths;
     *        null if every count rounds to zero
     * @param roundingRequired whether printed point inputs alone miss the reported TPM rounding bin
     */
    public record RowAudit(String target, BigDecimal minimumCompatibleTpm, BigDecimal maximumCompatibleTpm,
                           BigDecimal printedPointTpm, boolean roundingRequired) { }

    /**
     * Complete count and TPM arithmetic receipt. It certifies no fit/model/biological accuracy.
     * @param rows every target compatibility check
     * @param expectedAssignedFragments separately audited original assigned-fragment normalization mass
     * @param printedCountSum exact sum of printed estimated counts
     * @param minimumCountSum smallest count sum allowed by fixed-point printing
     * @param maximumCountSum largest count sum allowed by fixed-point printing
     * @param printedTpmSum exact sum of printed TPM
     * @param floatingPointTpmAllowance conservative binary64 arithmetic allowance in TPM units
     * @param rowsRequiringRounding number of rows requiring printed-input uncertainty
     */
    public record Audit(List<RowAudit> rows, long expectedAssignedFragments, BigDecimal printedCountSum,
                         BigDecimal minimumCountSum, BigDecimal maximumCountSum, BigDecimal printedTpmSum,
                         BigDecimal floatingPointTpmAllowance, int rowsRequiringRounding) {
        /** Copies all per-target checks. */
        public Audit { rows = List.copyOf(rows); }
    }

    /**
     * Parses every expected original row and exact field precision, including terminal
     * newlines and end of file. Both incomplete and extra populations fail. The reader
     * is closed on success or failure. Reference IDs/lengths must be supplied from a
     * separately checked complete reference/index ledger, not inferred from this file.
     * @param input original UTF-8-decoded quant.sf
     * @param expectedTargets complete ordered quantified population
     * @param countPrecision explicit original sigDigits, zero to 1000
     * @param maximumCharacters explicit whole-file character capacity
     * @param maximumLineCharacters explicit whole-line character capacity
     * @param cancellation cancellation callback
     * @return complete parse retaining original tokens
     * @throws IOException for shape, identity, precision, domain or capacity errors
     */
    public static Table read(Reader input, List<Target> expectedTargets, int countPrecision,
                             long maximumCharacters, int maximumLineCharacters, Runnable cancellation) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(cancellation);
        try (Reader owned = input) {
            List<Target> targets = List.copyOf(expectedTargets);
            if (targets.isEmpty() || countPrecision < 0 || countPrecision > 1000 || maximumCharacters < 1 || maximumLineCharacters < HEADER.length())
                throw new IOException("Require a complete Salmon target population, explicit precision and adequate character/line capacities");
            var unique = new HashSet<String>(); for (Target target : targets) if (!unique.add(target.name())) throw new IOException("Duplicate declared Salmon target identity");
            var lines = new Lines(owned, maximumCharacters, maximumLineCharacters, cancellation);
            if (!HEADER.equals(lines.next())) throw new IOException("Original Salmon quant.sf requires its exact five-column header");
            var rows = new ArrayList<Row>();
            for (Target target : targets) {
                cancellation.run(); String line = lines.next();
                if (line == null) throw new EOFException("Missing Salmon quantification row: " + target.name());
                String[] cells = line.split("\t", -1);
                if (cells.length != 5 || !cells[0].equals(target.name()) || !cells[1].equals(Long.toString(target.length())))
                    throw new IOException("Complete Salmon target identity, order or original length differs: " + target.name());
                rows.add(new Row(target, printed(cells[2], countPrecision), printed(cells[3], 6), printed(cells[4], countPrecision)));
            }
            if (lines.next() != null) throw new IOException("Extra Salmon quantification rows; no prefix is a complete result");
            cancellation.run(); return new Table(rows, countPrecision, lines.characters);
        }
    }

    /**
     * Checks count-mass and source TPM normalization using outward-rounded decimal
     * intervals for all original printed fields. The original writer divides each
     * projected count by mapped-fragment mass and effective length, sums rates, then
     * normalizes to one million. Output formatting can hide nonzero counts or move
     * printed point normalizations; original values are never overwritten here.
     * A conservative nonnegative binary64 rounding/underflow envelope is reported
     * separately. Per-target compatibility is a necessary constraint, not a proof
     * of one unique joint unprinted vector or of the fitted model. Grossly
     * unresolved numerical domains fail instead of receiving a
     * misleading certificate. A zero-mass family remains unreportable, not a
     * negative biological finding. This does not check the fitted effective-length
     * model, EM/VB optimization, library/reference admission or full output bundle.
     * @param table complete original parse
     * @param expectedAssignedFragments positive separately audited original normalization mass;
     *        this is not an arbitrary processed-read or input-read count
     * @param cancellation cancellation callback
     * @return complete arithmetic receipt
     * @throws IOException for mass/normalization mismatch or unresolved numeric domain
     */
    public static Audit audit(Table table, long expectedAssignedFragments, Runnable cancellation) throws IOException {
        Objects.requireNonNull(table); Objects.requireNonNull(cancellation); cancellation.run();
        if (expectedAssignedFragments < 1 || expectedAssignedFragments > 9007199254740991L)
            throw new IOException("Salmon normalization needs positive exactly representable assigned-fragment mass; zero mass is unreportable, not biological absence");
        BigDecimal quantum = BigDecimal.ONE.scaleByPowerOfTen(-table.countPrecision()).divide(TWO);
        BigDecimal tpmQuantum = new BigDecimal("0.0000005"), mass = BigDecimal.valueOf(expectedAssignedFragments);
        BigDecimal countSum = BigDecimal.ZERO, countLow = BigDecimal.ZERO, countHigh = BigDecimal.ZERO;
        BigDecimal tpmSum = BigDecimal.ZERO, tpmLow = BigDecimal.ZERO, tpmHigh = BigDecimal.ZERO;
        BigDecimal rateLow = BigDecimal.ZERO, rateHigh = BigDecimal.ZERO, pointRateSum = BigDecimal.ZERO, minimumLength = null, maximumLength = BigDecimal.ZERO;
        var low = new ArrayList<BigDecimal>(); var high = new ArrayList<BigDecimal>(); var points = new ArrayList<BigDecimal>();
        for (Row row : table.rows()) {
            cancellation.run(); BigDecimal count = row.estimatedFragments().value(), length = row.effectiveLength().value(), tpm = row.tpm().value();
            BigDecimal cLow = count.subtract(quantum).max(BigDecimal.ZERO), cHigh = count.add(quantum), lLow = length.subtract(quantum), lHigh = length.add(quantum);
            if (lLow.signum() <= 0) throw new IOException("Salmon effective length is unresolved at printed precision for " + row.target().name() + "; retain source fields and request adequate sigDigits");
            countSum = countSum.add(count); countLow = countLow.add(cLow); countHigh = countHigh.add(cHigh);
            tpmSum = tpmSum.add(tpm); tpmLow = tpmLow.add(tpm.subtract(tpmQuantum).max(BigDecimal.ZERO)); tpmHigh = tpmHigh.add(tpm.add(tpmQuantum));
            BigDecimal a = cLow.divide(lHigh, DOWN), b = cHigh.divide(lLow, UP), point = count.divide(length, DOWN);
            low.add(a); high.add(b); points.add(point); rateLow = rateLow.add(a); rateHigh = rateHigh.add(b); pointRateSum = pointRateSum.add(point);
            minimumLength = minimumLength == null ? lLow : minimumLength.min(lLow);
            maximumLength = maximumLength.max(lHigh);
        }
        BigDecimal operations = BigDecimal.valueOf(2L * table.rows().size() + 16), product = operations.multiply(EPSILON);
        BigDecimal gamma = product.divide(BigDecimal.ONE.subtract(product), UP);
        BigDecimal massSlack = mass.multiply(gamma, UP);
        if (countLow.subtract(massSlack).compareTo(mass) > 0 || countHigh.add(massSlack).compareTo(mass) < 0)
            throw new IOException("Complete Salmon estimated-count sum disagrees with separately audited assigned-fragment mass");
        // Positive protected total mass still bounds the denominator when every
        // individual printed count can be zero at a coarse chosen precision.
        BigDecimal protectedRateLow = rateLow.max(mass.subtract(massSlack).max(BigDecimal.ZERO).divide(maximumLength, DOWN));
        if (protectedRateLow.divide(mass, DOWN).compareTo(NORMAL_MINIMUM) < 0)
            throw new IOException("Salmon normalization denominator is unresolved/subnormal at printed precision; retain the original family as unreportable");
        BigDecimal underflow = SUBNORMAL_UNIT.multiply(mass).multiply(operations)
            .multiply(BigDecimal.ONE.divide(minimumLength, UP).add(BigDecimal.ONE), UP).divide(protectedRateLow, UP).multiply(MILLION, UP);
        BigDecimal fp = MILLION.multiply(gamma, UP).add(underflow);
        if (fp.compareTo(BigDecimal.ONE) >= 0) throw new IOException("Salmon normalization floating-point uncertainty exceeds the verified numerical domain");
        if (tpmLow.subtract(fp).compareTo(MILLION) > 0 || tpmHigh.add(fp).compareTo(MILLION) < 0)
            throw new IOException("Complete Salmon TPM family does not normalize to one million within source printing/arithmetic bounds");
        var checks = new ArrayList<RowAudit>(); int rounded = 0;
        for (int i = 0; i < table.rows().size(); i++) {
            cancellation.run(); Row row = table.rows().get(i); BigDecimal numerator = low.get(i), upperNumerator = high.get(i);
            BigDecimal denominator = numerator.add(rateHigh.subtract(high.get(i))), upperDenominator = upperNumerator.add(rateLow.subtract(low.get(i)));
            BigDecimal minimum = denominator.signum() == 0 ? BigDecimal.ZERO : numerator.multiply(MILLION).divide(denominator, DOWN).subtract(fp).max(BigDecimal.ZERO);
            BigDecimal maximum = upperDenominator.signum() == 0 ? MILLION : upperNumerator.multiply(MILLION).divide(upperDenominator, UP).add(fp).min(MILLION.add(fp));
            BigDecimal reportedLow = row.tpm().value().subtract(tpmQuantum).max(BigDecimal.ZERO), reportedHigh = row.tpm().value().add(tpmQuantum);
            if (minimum.compareTo(reportedHigh) > 0 || maximum.compareTo(reportedLow) < 0)
                throw new IOException("Salmon count/effective-length/TPM normalization disagrees for " + row.target().name());
            BigDecimal point = pointRateSum.signum() == 0 ? null : points.get(i).multiply(MILLION).divide(pointRateSum, DOWN);
            boolean needsRounding = point == null || point.compareTo(reportedLow) < 0 || point.compareTo(reportedHigh) > 0;
            if (needsRounding) rounded++;
            checks.add(new RowAudit(row.target().name(), minimum, maximum, point, needsRounding));
        }
        return new Audit(checks, expectedAssignedFragments, countSum, countLow, countHigh, tpmSum, fp, rounded);
    }

    private static Printed printed(String text, int precision) throws IOException {
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.scale() != precision || precision == 0 && text.indexOf('.') >= 0) throw new IllegalArgumentException("Wrong fixed-point precision");
            return new Printed(text, value);
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid original Salmon fixed-point field/precision: " + text, invalid); }
    }

    private static final class Lines {
        final Reader input; final long maximum; final int lineMaximum; final Runnable check; long characters;
        Lines(Reader input, long maximum, int lineMaximum, Runnable check) { this.input=input; this.maximum=maximum; this.lineMaximum=lineMaximum; this.check=check; }
        String next() throws IOException {
            var line = new StringBuilder();
            while (true) {
                if ((characters & 4095) == 0) check.run(); int c = input.read();
                if (c < 0) { if (!line.isEmpty()) throw new EOFException("Original Salmon row lacks its terminal newline"); return null; }
                if (++characters > maximum) throw new IOException("Complete Salmon table exceeds character capacity; no prefix is admitted");
                if (c == '\n') return line.toString();
                if (line.length() >= lineMaximum) throw new IOException("Complete Salmon row exceeds declared line capacity");
                line.append((char)c);
            }
        }
    }
}
