/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A nonnegative original RSEM decimal and conservative serialization bounds.
 * A displayed zero from general formatting is exact numeric zero; a displayed
 * fixed-two-decimal zero may represent a positive value below 0.005. Bounds
 * enclose the possible binary64 source values inside the closed decimal
 * serialization range. Boundary ties remain conservative compatibility checks;
 * they do not certify a unique printf string or biological uncertainty.
 */
public final class RsemPrintedNumber {
    /** Original field format. */
    public enum Format {
        /** Exact integral field. */ INTEGER,
        /** Original %.2f result field. */ FIXED_2,
        /** Original %.6g interval/variation field. */ GENERAL_6,
        /** Original %.10g model field. */ GENERAL_10,
        /** Original %.15g theta/mappability field. */ GENERAL_15
    }
    private final String text;
    private final double value, lower, upper;
    private final Format format;

    private RsemPrintedNumber(String text, double value, double lower, double upper, Format format) {
        this.text = text; this.value = value; this.lower = lower; this.upper = upper; this.format = format;
    }

    /**
     * Parses finite nonnegative source text without changing its lexical value.
     * @param text original numeric token
     * @param format original field's print format
     * @return preserved token and feasible binary64 source bounds
     */
    public static RsemPrintedNumber parse(String text, Format format) {
        Objects.requireNonNull(text, "text"); Objects.requireNonNull(format, "format");
        if (text.length() == 0 || text.length() > 512 || !text.matches("-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))
            throw new IllegalArgumentException("Invalid or oversized original decimal");
        try {
            BigDecimal center = new BigDecimal(text); double value = Double.parseDouble(text);
            if (center.signum() < 0 || !Double.isFinite(value) || (center.signum() != 0 && value == 0))
                throw new IllegalArgumentException("Negative, nonfinite or underflowed original decimal");
            if (format == Format.INTEGER && !text.matches("[0-9]+"))
                throw new IllegalArgumentException("Exact integral source field required");
            if (format == Format.FIXED_2 && !text.matches("-?[0-9]+\\.[0-9]{2}"))
                throw new IllegalArgumentException("Original fixed-two-decimal field required");
            BigDecimal radius;
            if (format == Format.INTEGER) radius = BigDecimal.ZERO;
            else if (format == Format.FIXED_2) radius = new BigDecimal("0.005");
            else {
                int digits = format == Format.GENERAL_6 ? 6 : format == Format.GENERAL_10 ? 10 : 15;
                BigDecimal compact = center.stripTrailingZeros();
                if (compact.precision() > digits) throw new IllegalArgumentException("Source token exceeds declared significant-digit format");
                if (center.signum() == 0) radius = BigDecimal.ZERO;
                else {
                    int exponent = Math.subtractExact(compact.precision(), compact.scale()) - 1;
                    if (exponent < -324 || exponent > 308) throw new IllegalArgumentException("Original decimal exponent is outside binary64 source range");
                    radius = BigDecimal.valueOf(5).scaleByPowerOfTen(exponent - digits);
                }
            }
            BigDecimal low = center.subtract(radius).max(BigDecimal.ZERO), high = center.add(radius);
            double lo = low.doubleValue(), hi = high.doubleValue();
            if (!Double.isFinite(lo)) throw new IllegalArgumentException("No finite source value inside presentation bounds");
            if (new BigDecimal(lo).compareTo(low) < 0) lo = Math.nextUp(lo);
            if (!Double.isFinite(hi)) hi = Double.MAX_VALUE;
            if (new BigDecimal(hi).compareTo(high) > 0) hi = Math.nextDown(hi);
            if (lo > hi) throw new IllegalArgumentException("No binary64 source value could produce this declared precision");
            return new RsemPrintedNumber(text, value, lo, hi, format);
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid original decimal precision", e);
        }
    }
    /** @return original token without normalization or rounding */
    public String text() {return text;}
    /** @return the source token's binary64 projection, not recovered hidden precision */
    public double value() {return value;}
    /** @return conservative nonnegative lower serialization bound */
    public double lower() {return lower;}
    /** @return conservative upper serialization bound */
    public double upper() {return upper;}
    /** @return the original declared print format */
    public Format format() {return format;}
    /**
     * Tests whether a finite point is compatible with the source display.
     * @param point unrounded comparison value
     * @return true only inside the outward serialization bounds
     */
    public boolean contains(double point) {return Double.isFinite(point) && point >= lower && point <= upper;}
}
