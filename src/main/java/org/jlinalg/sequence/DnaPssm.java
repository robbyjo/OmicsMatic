/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.Arrays;
import java.util.Objects;

/**
 * Independent mathematical DNA position-specific scores and zero-order null tails.
 * Inputs are resolved
 * row probabilities and an explicitly resolved A/C/G/T background, rather than
 * counts, a motif database identifier or an inferred genomic model. A 1000-point
 * per-column score lattice follows the tested original FIMO 5.5.9 convention.
 * It does not compute q-values, priors, strand averaging or file parsing.
 * The native implementation uses no external runtime or MEME library code.
 * Original-model documentation: https://meme-suite.org/meme/doc/fimo.html.
 * Reference provenance and scope are retained with the formal test fixtures.
 */
public final class DnaPssm {
    /** Per-column integer score range used by the reference convention. */
    public static final int SCORE_RANGE = 1000;
    private static final double LOG_TWO = Math.log(2.0);

    /**
     * Explicit native model-building budgets.
     * @param maximumWorkspaceBytes estimated primitive arrays plus conservative row overhead
     * @param maximumConvolutionOperations maximum number of null convolution updates
     */
    public record Limits(long maximumWorkspaceBytes, long maximumConvolutionOperations) {
        /** Checks positive budgets. */
        public Limits {
            if (maximumWorkspaceBytes < 1 || maximumConvolutionOperations < 1)
                throw new IllegalArgumentException("Model budgets must be positive");
        }
        /** @return 256 MiB and one billion null-convolution updates */
        public static Limits defaults() { return new Limits(268_435_456, 1_000_000_000); }
    }

    /**
     * Score and tail for one complete oriented canonical-DNA word.
     * @param integerScore original-convention lattice score
     * @param logOdds back-transformed lattice score in bits
     * @param pValue probability of a null lattice score at least this large
     * @param continuousLogOdds sum before lattice rounding; not the score used for pValue
     */
    public record Site(int integerScore, double logOdds, double pValue, double continuousLogOdds) { }

    private final int[][] integerMatrix;
    private final double[][] probabilities;
    private final double[][] continuous;
    private final double[] background;
    private final double[] tails;
    private final int scale;
    private final double offset;

    private DnaPssm(int[][] integers, double[][] probabilities, double[][] continuous,
                    double[] background, double[] tails, int scale, double offset) {
        this.integerMatrix = integers; this.probabilities = probabilities;
        this.continuous = continuous; this.background = background;
        this.tails = tails; this.scale = scale; this.offset = offset;
    }

    /**
     * Builds a model with the default resource budgets.
     * @param frequencies row probabilities in A/C/G/T order
     * @param background explicitly resolved positive A/C/G/T probabilities
     * @param sites positive finite motif site count
     * @param pseudocount nonnegative finite background-weighted pseudocount
     * @return an immutable score model
     */
    public static DnaPssm build(double[][] frequencies, double[] background, double sites, double pseudocount) {
        return build(frequencies, background, sites, pseudocount, Limits.defaults());
    }

    /**
     * Resolves the separately tested FIMO 5.5.9 DNA scan background preprocessing.
     * Complement averaging, when selected, precedes addition of 5e-7 per base and
     * normalization. This is a file-scanner convention, independent of the motif
     * pseudocount. Core {@link #build(double[][], double[], double, double)} never
     * applies it implicitly. Inputs here must already be positive and normalized.
     * @param supplied resolved positive A/C/G/T probabilities
     * @param bothStrands whether complementary frequencies are averaged
     * @return a new resolved scan background
     */
    public static double[] fimoScanBackground(double[] supplied, boolean bothStrands) {
        double[] bg = vector(supplied,true,"scan background");
        if (bothStrands) {
            double at = (bg[0]+bg[3])/2, cg = (bg[1]+bg[2])/2;
            bg = new double[]{at,cg,cg,at};
        }
        double denominator = 2e-6;
        for (double probability : bg) denominator += probability;
        for (int base = 0; base < 4; base++) bg[base] = (bg[base]+5e-7)/denominator;
        return bg;
    }

    /**
     * Builds an explicitly bounded model without silently normalizing inputs.
     * A zero cell requires a positive pseudocount. Background/row sums must be
     * within 1e-12 of one; resolved probabilities themselves remain unchanged.
     * @param frequencies row probabilities in A/C/G/T order
     * @param suppliedBackground explicitly resolved positive A/C/G/T probabilities
     * @param sites positive finite motif site count
     * @param pseudocount nonnegative finite background-weighted pseudocount
     * @param limits explicit workspace and operation budgets
     * @return an immutable score model
     */
    public static DnaPssm build(double[][] frequencies, double[] suppliedBackground, double sites,
                                double pseudocount, Limits limits) {
        Objects.requireNonNull(frequencies, "frequencies"); Objects.requireNonNull(limits, "limits");
        double[] bg = vector(suppliedBackground, true, "background");
        if (frequencies.length < 1 || frequencies.length > (Integer.MAX_VALUE - 1) / SCORE_RANGE)
            throw new IllegalArgumentException("Require a positive motif width fitting the integer lattice");
        if (!Double.isFinite(sites) || sites <= 0 || !Double.isFinite(pseudocount) || pseudocount < 0
                || !Double.isFinite(sites + pseudocount))
            throw new IllegalArgumentException("Require finite positive sites and nonnegative pseudocount with finite sum");
        int width = frequencies.length, cells = Math.addExact(Math.multiplyExact(width, SCORE_RANGE), 1);
        long workspace = Math.addExact(Math.multiplyExact(24L, cells), Math.addExact(4096L, Math.multiplyExact(256L, width)));
        if (workspace > limits.maximumWorkspaceBytes()) throw new IllegalArgumentException("PSSM workspace estimate " + workspace + " exceeds the declared byte budget");
        double[][] probabilities = new double[width][4], raw = new double[width][4];
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (int row = 0; row < width; row++) {
            double[] values = vector(frequencies[row], false, "motif row " + row);
            for (int base = 0; base < 4; base++) {
                double p = pseudocount == 0 ? values[base]
                        : (values[base] * sites + pseudocount * bg[base]) / (sites + pseudocount);
                double odds = p / bg[base];
                if (!(p > 0) || !(odds > 0) || !Double.isFinite(odds))
                    throw new IllegalArgumentException("Require positive smoothed probabilities and finite log odds; no epsilon substitution");
                probabilities[row][base] = p; raw[row][base] = Math.log(odds) / LOG_TWO;
                low = Math.min(low, raw[row][base]); high = Math.max(high, raw[row][base]);
            }
        }
        double offset = Math.floor(low == high ? low - 1 : low);
        double proposedScale = Math.floor(SCORE_RANGE / (high - offset));
        if (!Double.isFinite(proposedScale) || proposedScale < 1 || proposedScale > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Likelihood range cannot be represented on the supported lattice");
        int scale = (int) proposedScale; int[][] scores = new int[width][4];
        int prefixLow = 0, prefixHigh = 0; long operations = 0;
        for (int row = 0; row < width; row++) {
            int rowLow = Integer.MAX_VALUE, rowHigh = 0;
            for (int base = 0; base < 4; base++) {
                int value = (int) Math.floor((raw[row][base] - offset) * scale + 0.5);
                if (value < 0 || value > SCORE_RANGE) throw new IllegalArgumentException("Rounded cell outside the score lattice");
                scores[row][base] = value; rowLow = Math.min(rowLow, value); rowHigh = Math.max(rowHigh, value);
            }
            operations = Math.addExact(operations, Math.multiplyExact(4L, prefixHigh - prefixLow + 1L));
            prefixLow = Math.addExact(prefixLow, rowLow); prefixHigh = Math.addExact(prefixHigh, rowHigh);
        }
        if (operations > limits.maximumConvolutionOperations()) throw new IllegalArgumentException("PSSM null convolution estimate " + operations + " exceeds the declared operation budget");
        double[] mass = new double[cells], next = new double[cells]; mass[0] = 1;
        int first = 0, last = 0;
        for (int row = 0; row < width; row++) {
            int rowLow = Arrays.stream(scores[row]).min().orElseThrow(), rowHigh = Arrays.stream(scores[row]).max().orElseThrow();
            int nextFirst = first + rowLow, nextLast = last + rowHigh;
            Arrays.fill(next, nextFirst, nextLast + 1, 0.0);
            for (int base = 0; base < 4; base++) for (int state = first; state <= last; state++) {
                if (mass[state] != 0) next[state + scores[row][base]] += mass[state] * bg[base];
            }
            double[] swap = mass; mass = next; next = swap; first = nextFirst; last = nextLast;
        }
        double[] tails = new double[cells]; double tail = 0;
        for (int state = cells - 1; state >= 0; state--) {
            if (state >= first && state <= last) tail += mass[state];
            if (!Double.isFinite(tail) || tail < 0 || tail > 1 + 1e-10)
                throw new IllegalArgumentException("Null distribution is outside probability bounds; verify resolved background normalization");
            tails[state] = Math.min(1, tail);
        }
        return new DnaPssm(scores, probabilities, raw, bg, tails, scale, offset);
    }

    private static double[] vector(double[] input, boolean strictlyPositive, String name) {
        if (input == null || input.length != 4) throw new IllegalArgumentException("Require four A/C/G/T values for " + name);
        double[] values = input.clone(); double sum = 0;
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0 || value > 1 || strictlyPositive && value == 0)
                throw new IllegalArgumentException("Invalid resolved probability in " + name);
            sum += value;
        }
        if (Math.abs(sum - 1) > 1e-12) throw new IllegalArgumentException("Resolved probabilities must sum to one in " + name);
        return values;
    }

    /** @return motif width */
    public int width() { return integerMatrix.length; }
    /** @return integer quantization multiplier */
    public int scale() { return scale; }
    /** @return per-position quantization offset in bits */
    public double offset() { return offset; }
    /** @return a defensive copy of the resolved background */
    public double[] background() { return background.clone(); }
    /**
     * @param row zero-based position
     * @param base A/C/G/T column index, zero through three
     * @return the integer lattice cell
     */
    public int cellScore(int row, int base) { return integerMatrix[row][base]; }
    /**
     * @param row zero-based position
     * @param base A/C/G/T column index, zero through three
     * @return the smoothed position probability
     */
    public double smoothedProbability(int row, int base) { return probabilities[row][base]; }

    /**
     * Scores exactly one complete canonical word. No strand/background changes are inferred.
     * @param word oriented A/C/G/T word, case insensitive, of exactly the motif width
     * @return lattice score and null tail plus the separately labeled continuous score
     */
    public Site score(String word) {
        if (word == null || word.length() != width()) throw new IllegalArgumentException("Require a word of exactly the motif width");
        int integer = 0; double unrounded = 0;
        for (int row = 0; row < width(); row++) {
            int base = switch (word.charAt(row)) {
                case 'A', 'a' -> 0; case 'C', 'c' -> 1; case 'G', 'g' -> 2; case 'T', 't' -> 3;
                default -> throw new IllegalArgumentException("Complete core words require canonical A/C/G/T; ambiguous windows have no score");
            };
            integer += integerMatrix[row][base]; unrounded += continuous[row][base];
        }
        if (tails[integer] == 0) throw new ArithmeticException("Null tail underflow for an attainable DNA word; a zero p-value must not be treated as exact. Use a suitable high-precision method or a less extreme model");
        return new Site(integer, integer / (double) scale + width() * offset, tails[integer], unrounded);
    }
}
