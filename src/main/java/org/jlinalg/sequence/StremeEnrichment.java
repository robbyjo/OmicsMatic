/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/**
 * Independent exact right-tail Fisher calculation for STREME's differential
 * enrichment output. This checks a supplied fixed-margin table; it does not
 * discover motifs, choose thresholds, validate a holdout split, or turn a
 * training score into a significance claim. Unequal-length binomial and central
 * distance tests require different calculations and are not silently replaced.
 */
public final class StremeEnrichment {
    private StremeEnrichment() { }

    /**
     * Original sequence counts, not occurrence counts. The two pass counts use
     * the same motif threshold and the declared positive/control populations.
     * @param positives total positive sequences in this subset
     * @param negatives total control sequences in this subset
     * @param positivePass positive sequences passing the threshold
     * @param negativePass control sequences passing the threshold
     */
    public record Table(int positives, int negatives, int positivePass, int negativePass) {
        /** Rejects impossible cells before allocating integers. */
        public Table {
            if (positives < 0 || negatives < 0 || positivePass < 0 || positivePass > positives
                    || negativePass < 0 || negativePass > negatives)
                throw new IllegalArgumentException("Invalid STREME Fisher sequence-count table");
        }
    }

    /**
     * Complete-tail resource capacities, not operating-system heap quotas.
     * @param maximumPopulation maximum positive plus control sequences
     * @param maximumTailTerms maximum terms in the complete inclusive upper tail
     * @param maximumIntegerBits maximum intermediate integer size
     * @param maximumOperations maximum counted integer arithmetic operations
     */
    public record Limits(int maximumPopulation, int maximumTailTerms,
                         int maximumIntegerBits, long maximumOperations) {
        /** Rejects invalid or unsupported capacities. */
        public Limits {
            if (maximumPopulation < 1 || maximumPopulation > 1_000_000 || maximumTailTerms < 1
                    || maximumTailTerms > 1_000_001 || maximumIntegerBits < 1
                    || maximumIntegerBits > 2_000_000 || maximumOperations < 1
                    || maximumOperations > 100_000_000)
                throw new IllegalArgumentException("Invalid explicit STREME exact Fisher capacities");
        }
        /** @return bounded per-table defaults; family callers must also bound cumulative work */
        public static Limits defaults() { return new Limits(100_000, 100_001, 200_000, 2_000_000); }
    }

    /**
     * Exact reduced rational probability plus explicitly rounded binary64 views.
     * A zero binary64 probability can represent underflow; the exact fraction and
     * finite log10 remain available. No pseudocount or multiple-test correction
     * is added. This quantity is a training score when the source has no holdout.
     * @param table unchanged supplied table
     * @param numerator exact reduced numerator
     * @param denominator exact reduced denominator
     * @param probability binary64 view, possibly underflowed to zero
     * @param log10Probability finite log10 approximation of the exact fraction
     * @param tailTerms complete inclusive upper-tail terms visited
     * @param operations integer arithmetic operations counted
     */
    public record FisherTail(Table table, BigInteger numerator, BigInteger denominator,
                             double probability, double log10Probability, int tailTerms, long operations) { }

    /**
     * Sums every fixed-margin outcome at least as enriched as positivePass.
     * The integer recurrence uses a common denominator, so tail summation is
     * exact even when the probability cannot be represented by a double.
     * @param table original sequence counts
     * @param limits explicit complete-tail capacities
     * @param cancellation caller cancellation callback, also called inside arithmetic loops
     * @return exact reduced inclusive right-tail fraction and numeric views
     * @throws IllegalArgumentException if the whole table/tail exceeds a declared capacity
     */
    public static FisherTail fisher(Table table, Limits limits, Runnable cancellation) {
        Objects.requireNonNull(table); Objects.requireNonNull(limits); Objects.requireNonNull(cancellation);
        var work = new Work(limits, cancellation); work.check();
        long population = (long) table.positives() + table.negatives();
        if (population > limits.maximumPopulation())
            throw new IllegalArgumentException("Complete STREME Fisher population exceeds capacity; no table was truncated");
        int total = (int) population, successes = table.positivePass() + table.negativePass();
        int first = table.positivePass(), last = Math.min(table.positives(), successes);
        int terms = last - first + 1;
        if (terms > limits.maximumTailTerms())
            throw new IllegalArgumentException("Complete STREME Fisher right tail exceeds capacity; no states were truncated");
        BigInteger denominator = choose(total, successes, work);
        BigInteger term = work.multiply(choose(table.positives(), first, work),
                                       choose(table.negatives(), successes - first, work));
        BigInteger numerator = BigInteger.ZERO;
        for (int k = first; k <= last; k++) {
            numerator = work.add(numerator, term);
            if (k < last) {
                BigInteger top = work.multiply(work.multiply(term, BigInteger.valueOf(table.positives() - k)),
                                               BigInteger.valueOf(successes - k));
                BigInteger bottom = work.multiply(BigInteger.valueOf(k + 1L),
                                                  BigInteger.valueOf((long) table.negatives() - successes + k + 1));
                term = work.divide(top, bottom);
            }
        }
        if (numerator.signum() <= 0 || numerator.compareTo(denominator) > 0)
            throw new ArithmeticException("Impossible exact STREME Fisher right tail");
        work.operation(); BigInteger common = numerator.gcd(denominator); work.check();
        numerator = work.divide(numerator, common); denominator = work.divide(denominator, common);
        double probability = new BigDecimal(numerator).divide(new BigDecimal(denominator), MathContext.DECIMAL128).doubleValue();
        double log10 = numerator.equals(denominator) ? 0 : log10Ratio(numerator, denominator);
        if (!Double.isFinite(log10) || log10 > 0 || !Double.isFinite(probability) || probability < 0 || probability > 1)
            throw new ArithmeticException("Invalid numeric view of exact STREME Fisher probability");
        work.check();
        return new FisherTail(table, numerator, denominator, probability, log10, terms, work.operations);
    }

    private static BigInteger choose(int n, int k, Work work) {
        if (k < 0 || k > n) throw new IllegalArgumentException("Impossible STREME Fisher margin");
        k = Math.min(k, n - k); BigInteger value = BigInteger.ONE;
        for (int i = 1; i <= k; i++)
            value = work.divide(work.multiply(value, BigInteger.valueOf((long) n - k + i)), BigInteger.valueOf(i));
        return value;
    }

    private static double log10Ratio(BigInteger numerator, BigInteger denominator) {
        int a = Math.max(0, numerator.bitLength() - 53), b = Math.max(0, denominator.bitLength() - 53);
        double x = numerator.shiftRight(a).doubleValue(), y = denominator.shiftRight(b).doubleValue();
        // Taking the exponent difference first avoids cancellation of two large logs.
        return (Math.log(x / y) + (a - b) * Math.log(2)) / Math.log(10);
    }

    private static final class Work {
        final Limits limits; final Runnable cancellation; long operations;
        Work(Limits limits, Runnable cancellation) { this.limits = limits; this.cancellation = cancellation; }
        void check() { cancellation.run(); }
        void operation() {
            if (operations >= limits.maximumOperations())
                throw new IllegalArgumentException("Complete STREME Fisher arithmetic exceeds operation capacity");
            if ((operations++ & 255) == 0) check();
        }
        BigInteger size(BigInteger value) {
            if (value.bitLength() > limits.maximumIntegerBits())
                throw new IllegalArgumentException("Complete STREME Fisher arithmetic exceeds integer-bit capacity");
            return value;
        }
        BigInteger multiply(BigInteger a, BigInteger b) { operation(); return size(a.multiply(b)); }
        BigInteger add(BigInteger a, BigInteger b) { operation(); return size(a.add(b)); }
        BigInteger divide(BigInteger a, BigInteger b) {
            operation(); BigInteger[] result = a.divideAndRemainder(b);
            if (result[1].signum() != 0) throw new ArithmeticException("Nonintegral STREME Fisher exact recurrence");
            return size(result[0]);
        }
    }
}
