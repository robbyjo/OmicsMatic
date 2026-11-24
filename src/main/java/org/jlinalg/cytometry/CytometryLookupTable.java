/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.util.Arrays;

/** Explicit, finite, strictly monotone calibration; interpolation never changes the supplied knots. */
public final class CytometryLookupTable implements CytometryTransforms.Scale {
    private final double[] input, output;
    public CytometryLookupTable(double[] input, double[] output) {
        if (input == null || output == null || input.length < 2 || input.length > 1000000 || input.length != output.length)
            throw new IllegalArgumentException("Supply 2..1000000 complete calibration pairs");
        this.input = input.clone(); this.output = output.clone();
        for (int i = 0; i < input.length; i++) {
            if (!Double.isFinite(input[i]) || !Double.isFinite(output[i]) || i > 0 && (!(input[i] > input[i-1]) || !(output[i] > output[i-1])))
                throw new IllegalArgumentException("Calibration knots must be finite and strictly increasing in both axes; no sorting, deduplication or repair");
        }
    }
    public int size() { return input.length; }
    public double input(int index) { return input[index]; }
    public double output(int index) { return output[index]; }
    /** Linear interpolation with declared endpoint saturation, as required by the WSP biex LUT profile. */
    @Override public double apply(double value) { return interpolate(value, input, output); }
    @Override public double inverse(double value) { return interpolate(value, output, input); }
    private static double interpolate(double value, double[] x, double[] y) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite lookup input; no replacement or dropped event");
        if (value <= x[0]) return y[0];
        if (value >= x[x.length-1]) return y[y.length-1];
        int k = Arrays.binarySearch(x, value);
        if (k >= 0) return y[k];
        int hi = -k-1, lo = hi-1;
        double result = y[lo]+(value-x[lo])*((y[hi]-y[lo])/(x[hi]-x[lo]));
        if (!Double.isFinite(result)) throw new ArithmeticException("Calibration interpolation is unresolved at these units/precision");
        return result;
    }
}
