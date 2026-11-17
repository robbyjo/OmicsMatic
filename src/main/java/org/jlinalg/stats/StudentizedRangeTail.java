/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import jdistlib.ChiSquare;
import jdistlib.Normal;
import jdistlib.T;
import jdistlib.Tukey;
import jdistlib.math.MathFunctions;

/** Positive-integrand upper tails, avoiding subtraction of a nearly unit CDF. */
final class StudentizedRangeTail {
    private static final double LOG_TWO = Math.log(2), LOG_PI = Math.log(Math.PI);
    private static final double SQRT_TWO = Math.sqrt(2), HALF_MIN_LOG = Math.log(Double.MIN_VALUE) - LOG_TWO;
    private static final Rule RULE32 = rule(32), RULE64 = rule(64), RULE128 = rule(128);
    private final int groups;
    private final double degrees;
    private final Map<Double, Double> densityCache = new HashMap<>();
    private int refinements;

    StudentizedRangeTail(int groups, double degrees) { this.groups = groups; this.degrees = degrees; }
    int refinements() { return refinements; }
    String method() { return groups == 2 ? "exact two-group Student-t identity"
        : refinements > 0 ? "Studentized range with positive-integrand upper-tail refinement" : "Studentized-range distribution"; }

    double critical(double confidence) {
        double target = 1 - confidence;
        double q = groups == 2 ? SQRT_TWO * T.quantile(target / 2, degrees, false, false)
            : Tukey.quantile(confidence, 1, groups, degrees, true, false);
        if (!(q > 0) || !Double.isFinite(q)) throw new ArithmeticException("Unresolved Studentized-range critical value");
        double p = probability(q);
        if (Math.abs(p - target) <= target * 5e-7) return q;
        double low = 0, high = q;
        for (int i = 0; p > target && i < 64; i++) { high *= 2; p = probability(high); }
        if (!Double.isFinite(high) || p > target) throw new ArithmeticException("Could not bracket Studentized-range critical value");
        for (int i = 0; i < 100; i++) {
            q = low + (high - low) / 2;
            p = probability(q);
            if (Math.abs(p - target) <= target * 5e-8) return q;
            if (p > target) low = q; else high = q;
        }
        throw new ArithmeticException("Studentized-range critical value did not converge");
    }

    double probability(double q) {
        if (Double.isNaN(q) || q < 0) throw new IllegalArgumentException("Nonnegative range statistic required");
        if (q == 0) return 1;
        double logPair = LOG_TWO + T.cumulative(q / SQRT_TWO, degrees, false, true);
        if (Double.isNaN(logPair) || logPair > 0) throw new ArithmeticException("Unresolved pairwise Student-t bound");
        if (groups == 2) return Math.exp(logPair);
        double logUpper = Math.min(0, logPair + Math.log((long) groups * (groups - 1) / 2));
        if (logUpper < HALF_MIN_LOG) return 0; // The entire family is below half the smallest representable positive value.
        if (logUpper > Math.log(1e-7)) {
            double value = Tukey.cumulative(q, 1, groups, degrees, false, false);
            if (value > 1e-7 && withinBounds(value, logPair, logUpper)) return value;
        }
        refinements++;
        double value = positiveTail(q);
        if (!withinBounds(value, logPair, logUpper))
            throw new ArithmeticException("Studentized-range upper tail fails independent pairwise probability bounds; no result admitted");
        return value;
    }

    private static boolean withinBounds(double value, double lowerLog, double upperLog) {
        if (!Double.isFinite(value) || value < 0 || value > 1) return false;
        if (value == 0) return upperLog < HALF_MIN_LOG;
        double log = Math.log(value);
        return log >= lowerLog - 2e-7 && log <= upperLog + 2e-7;
    }

    /** P(Q>q) = integral f(normal range r) * P(chi-square_df < df*(r/q)^2) dr. */
    private double positiveTail(double q) {
        var cuts = new TreeSet<Double>();
        for (int i = 0; i <= 120; i++) cuts.add(i * .5);
        // Resolve the narrow chi-square transition for large residual df.
        for (double z : new double[]{-12, -8, -4, -2, -1, 0, 1, 2, 4, 8, 12}) {
            double r = q * (1 + z / Math.sqrt(2 * degrees));
            if (r > 0 && r < 60) cuts.add(r);
        }
        double[] points = cuts.stream().mapToDouble(Double::doubleValue).toArray();
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < points.length; i++) maximum = Math.max(maximum, logIntegrand((points[i-1] + points[i]) / 2, q));
        if (!Double.isFinite(maximum)) throw new ArithmeticException("Unresolved positive Studentized-range tail density");
        double a = integrate(points, RULE32, q, maximum);
        double b = integrate(points, RULE64, q, maximum);
        if (!close(a, b, 2e-9)) {
            a = b; b = integrate(points, RULE128, q, maximum);
            if (!close(a, b, 2e-9)) throw new ArithmeticException("Positive Studentized-range tail quadrature did not converge");
        }
        if (!(b > 0) || !Double.isFinite(b)) throw new ArithmeticException("Unresolved positive Studentized-range tail integral");
        // At r=60 the normal-range union bound is below double underflow, even
        // for the API's maximum one million pairs; no representable mass is lost.
        return Math.exp(maximum + Math.log(b));
    }

    private static boolean close(double a, double b, double relative) {
        return Double.isFinite(a) && Double.isFinite(b) && a > 0 && b > 0
            && Math.abs(a - b) <= relative * Math.max(a, b);
    }

    private double integrate(double[] points, Rule rule, double q, double maximum) {
        double sum = 0, correction = 0;
        for (int i = 1; i < points.length; i++) {
            double half = (points[i] - points[i-1]) / 2, middle = (points[i] + points[i-1]) / 2;
            double panel = 0;
            for (int j = 0; j < rule.nodes.length; j++)
                panel += rule.weights[j] * Math.exp(logIntegrand(middle + half * rule.nodes[j], q) - maximum);
            double adjusted = half * panel - correction, next = sum + adjusted;
            correction = (next - sum) - adjusted; sum = next;
        }
        return sum;
    }

    private double logIntegrand(double r, double q) {
        double logArgument = Math.log(degrees) + 2 * (Math.log(r) - Math.log(q));
        double chi;
        if (logArgument < -30) {
            double shape = degrees / 2, y = Math.exp(logArgument - LOG_TWO);
            chi = shape * (logArgument - LOG_TWO) - MathFunctions.lgammafn(shape + 1)
                + Math.log1p(-shape / (shape + 1) * y);
        } else if (logArgument > Math.log(Double.MAX_VALUE)) chi = 0;
        else chi = ChiSquare.cumulative(Math.exp(logArgument), degrees, true, true);
        double answer = logRangeDensity(r) + chi;
        if (Double.isNaN(answer)) throw new ArithmeticException("Nonfinite positive Studentized-range tail integrand");
        return answer;
    }

    private double logRangeDensity(double r) {
        Double cached = densityCache.get(r);
        if (cached != null) return cached;
        double value;
        if (groups == 3) {
            // The normal three-sample range has this closed-form density.
            value = Math.log(3) - LOG_PI / 2 - r*r/4 + logInterval(0, r * Math.sqrt(2.0 / 3));
        } else {
            double peak = logInterval(0, r);
            double a = inner(r, peak, RULE32), b = inner(r, peak, RULE64);
            if (!close(a, b, 2e-10)) {
                a = b; b = inner(r, peak, RULE128);
                if (!close(a, b, 2e-10)) throw new ArithmeticException("Normal-range density quadrature did not converge");
            }
            value = Math.log((double) groups * (groups - 1)) - Math.log(2 * Math.PI)
                - r*r/4 + LOG_TWO + (groups - 2) * peak + Math.log(b);
        }
        if (densityCache.size() < 16384) densityCache.put(r, value);
        return value;
    }

    private double inner(double r, double peak, Rule rule) {
        double sum = 0;
        for (int i = 0; i < rule.nodes.length; i++) {
            double u = 5 * (rule.nodes[i] + 1);
            sum += rule.weights[i] * Math.exp(-u*u + (groups - 2) * (logInterval(u, r) - peak));
        }
        // The omitted |u|>10 Gaussian mass is negligible relative to the
        // interior: interval probability decreases with |u|.
        return 5 * sum;
    }

    private static double logInterval(double center, double width) {
        if (width < 1e-4) {
            double squared = center * center, w2 = width * width;
            double adjustment = (squared - 1) * w2 / 24
                + (squared*squared - 6*squared + 3) * w2*w2 / 1920;
            return -.5*squared - .5*Math.log(2*Math.PI) + Math.log(width) + Math.log1p(adjustment);
        }
        double low = center - width/2, high = center + width/2;
        if (low >= 0) {
            double a = Normal.cumulative(low, 0, 1, false, true), b = Normal.cumulative(high, 0, 1, false, true);
            return a + Math.log(-Math.expm1(b - a));
        }
        if (high <= 0) {
            double a = Normal.cumulative(high, 0, 1, true, true), b = Normal.cumulative(low, 0, 1, true, true);
            return a + Math.log(-Math.expm1(b - a));
        }
        double a = Normal.cumulative(low, 0, 1, true, true), b = Normal.cumulative(high, 0, 1, false, true);
        double maximum = Math.max(a, b), minimum = Math.min(a, b);
        return Math.log(-Math.expm1(maximum + Math.log1p(Math.exp(minimum - maximum))));
    }

    private record Rule(double[] nodes, double[] weights) { }
    private static Rule rule(int n) {
        double[] nodes = new double[n], weights = new double[n];
        for (int i = 0; i < (n + 1) / 2; i++) {
            double z = Math.cos(Math.PI * (i + .75) / (n + .5)), derivative = 0;
            boolean converged = false;
            for (int iteration = 0; iteration < 40; iteration++) {
                double previous = 1, current = z;
                for (int degree = 2; degree <= n; degree++) {
                    double next = ((2*degree - 1)*z*current - (degree - 1)*previous) / degree;
                    previous = current; current = next;
                }
                derivative = n * (z*current - previous) / (z*z - 1);
                double next = z - current/derivative;
                if (Math.abs(next - z) <= 2e-15) { z = next; converged = true; break; }
                z = next;
            }
            if (!converged) throw new IllegalStateException("Gauss-Legendre node did not converge");
            nodes[i] = -z; nodes[n-1-i] = z;
            weights[i] = 2 / ((1-z*z) * derivative*derivative); weights[n-1-i] = weights[i];
        }
        return new Rule(nodes, weights);
    }
}
