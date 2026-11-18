/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import jdistlib.accelerator.CpuComputeBackend;

/** FP64 Java square compensation and explicitly selected OLS spectral unmixing. */
public final class CytometryCompensation {
    public record Diagnostics(String method, int rank, int fluorochromes, int detectors,
                              double relativeCutoff, List<Double> singularValues) {
        public Diagnostics { singularValues = List.copyOf(singularValues); }
    }
    private final List<String> detectors, fluorochromes;
    private final double[] inverse;
    private final Diagnostics diagnostics;

    private CytometryCompensation(List<String> detectors, List<String> fluorochromes,
                                  double[] inverse, Diagnostics diagnostics) {
        this.detectors = names(detectors); this.fluorochromes = names(fluorochromes);
        this.inverse = inverse.clone(); this.diagnostics = diagnostics;
    }
    /**
     * Spectrum rows are fluorochromes and columns are detectors: observed row =
     * signal row times spectrum. Produces observed times inverse/pseudoinverse.
     * Square spectra must have full numerical rank. Rectangular spectra use the
     * Moore-Penrose OLS solution at the caller's explicit relative SVD cutoff;
     * this does not select NNLS or a vendor's proprietary unmixing model.
     */
    public static CytometryCompensation spectrum(List<String> detectors, List<String> fluorochromes,
                                                 double[][] spectrum, double relativeCutoff) {
        List<String> ds = names(detectors), fs = names(fluorochromes); int m = ds.size(), n = fs.size();
        if (m < n) throw new IllegalArgumentException("Spectrum needs at least as many detectors as fluorochromes");
        if (!Double.isFinite(relativeCutoff) || relativeCutoff < 0 || relativeCutoff >= 1) throw new IllegalArgumentException("Declare an SVD relative cutoff in [0,1)");
        double[] matrix = matrix(spectrum, n, m); double[] inverse = new double[m*n];
        double[] singular; double[] left; double[] right;
        try (CpuComputeBackend backend = new CpuComputeBackend()) {
            var decomposition = backend.dgesvd(matrix, n, m);
            singular = decomposition.singularValues(); left = decomposition.leftSingularVectors(); right = decomposition.rightSingularVectorsTransposed();
        }
        if (singular.length != n || left.length != n*n || right.length != n*m) throw new IllegalStateException("Unexpected complete Java SVD dimensions");
        int rank = 0; double threshold = singular[0]*relativeCutoff;
        for (double s : singular) {
            if (!Double.isFinite(s) || s < 0) throw new ArithmeticException("Spectrum SVD is nonfinite or invalid");
            if (s > threshold) rank++;
        }
        if (n == m && rank != n) throw new IllegalArgumentException("Square compensation spectrum is singular/unresolved at the declared SVD cutoff; no pseudoinverse fallback");
        for (int detector = 0; detector < m; detector++) for (int signal = 0; signal < n; signal++) {
            double value = 0;
            for (int k = 0; k < n; k++) if (singular[k] > threshold) value += right[k*m+detector]*(left[signal*n+k]/singular[k]);
            if (!Double.isFinite(value)) throw new ArithmeticException("Spectrum inverse is not finite at these units/precision");
            inverse[detector*n+signal] = value;
        }
        return new CytometryCompensation(ds, fs, inverse, new Diagnostics(n == m ? "square-svd-inverse" : "ols-svd-pseudoinverse", rank, n, m, relativeCutoff, java.util.Arrays.stream(singular).boxed().toList()));
    }
    /** Uses an explicitly supplied already-inverted detector-by-fluorochrome matrix unchanged. */
    public static CytometryCompensation alreadyInverted(List<String> detectors, List<String> fluorochromes,
                                                        double[][] inverse) {
        List<String> ds = names(detectors), fs = names(fluorochromes);
        if (ds.size() < fs.size()) throw new IllegalArgumentException("Unmixing needs at least as many detectors as fluorochromes");
        return new CytometryCompensation(ds, fs, matrix(inverse, ds.size(), fs.size()), new Diagnostics("explicit-already-inverted", -1, fs.size(), ds.size(), 0, List.of()));
    }
    public List<String> detectors() { return detectors; }
    public List<String> fluorochromes() { return fluorochromes; }
    public Diagnostics diagnostics() { return diagnostics; }
    /** A defensive row-major detector-by-fluorochrome inverse/unmixing matrix. */
    public double[] inverse() { return inverse.clone(); }
    /** Requires all measured channels in declared detector order; negative results are retained. */
    public double[] apply(double[] measured) {
        if (measured == null || measured.length != detectors.size()) throw new IllegalArgumentException("Require the complete declared detector vector");
        for (double value : measured) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite detector measurement; no event drop, substitution or compensation result");
        int n = fluorochromes.size(); double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0, correction = 0;
            for (int j = 0; j < measured.length; j++) {
                double term = measured[j]*inverse[j*n+i];
                double adjusted = term-correction, updated = sum+adjusted;
                correction = (updated-sum)-adjusted; sum = updated;
            }
            if (!Double.isFinite(sum)) throw new ArithmeticException("Nonfinite compensated output; no result at these units/precision");
            result[i] = sum;
        }
        return result;
    }
    private static List<String> names(List<String> values) {
        Objects.requireNonNull(values, "Channel names are required");
        if (values.isEmpty() || values.size() > 512 || new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("Require 1..512 unique, exact channel names");
        for (String value : values) if (value == null || value.isEmpty()) throw new IllegalArgumentException("Empty channel name");
        return List.copyOf(values);
    }
    private static double[] matrix(double[][] values, int rows, int columns) {
        if (values == null || values.length != rows) throw new IllegalArgumentException("Matrix row count differs from its declared names");
        double[] result = new double[rows*columns];
        for (int i = 0; i < rows; i++) {
            if (values[i] == null || values[i].length != columns) throw new IllegalArgumentException("Matrix columns differ from their declared names");
            for (int j = 0; j < columns; j++) {
                if (!Double.isFinite(values[i][j])) throw new IllegalArgumentException("Nonfinite spectrum/inverse coefficient");
                result[i*columns+j] = values[i][j];
            }
        }
        return result;
    }
}
