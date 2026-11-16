/*
 * Copyright (C) 2026 JLinAlg contributors (adaptation).
 * Symmetric Householder/QL steps adapted from public-domain JAMA 1.0.3,
 * The MathWorks and NIST; https://math.nist.gov/javanumerics/jama/.
 * Eigenvectors omitted; input scaling, finite checks and an iteration bound added.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.genetics;

/** Eigenvalues alone, avoiding eigenvector accumulation in PoPS's covariance adjustment. */
final class PopsEigenvalues {
    private PopsEigenvalues() { }

    static double[] values(double[][] matrix) {
        int n = matrix.length;
        double[][] v = PopsMath.copy(matrix, n, "symmetric covariance");
        if (v[0].length != n) throw new IllegalArgumentException("Square covariance required");
        double scale = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (v[i][j] != v[j][i]) throw new IllegalArgumentException("Symmetric covariance required");
            scale = Math.max(scale, Math.abs(v[i][j]));
        }
        double[] d = new double[n], e = new double[n];
        if (scale == 0) return d;
        for (double[] row : v) for (int j = 0; j < n; j++) row[j] /= scale;
        System.arraycopy(v[n - 1], 0, d, 0, n);
        for (int i = n - 1; i > 0; i--) {
            double rowScale = 0, h = 0;
            for (int k = 0; k < i; k++) rowScale += Math.abs(d[k]);
            if (rowScale == 0) {
                e[i] = d[i - 1];
                for (int j = 0; j < i; j++) { d[j] = v[i - 1][j]; v[i][j] = 0; v[j][i] = 0; }
            } else {
                for (int k = 0; k < i; k++) { d[k] /= rowScale; h += d[k] * d[k]; }
                double f = d[i - 1], g = Math.sqrt(h);
                if (f > 0) g = -g;
                e[i] = rowScale * g; h -= f * g; d[i - 1] = f - g;
                for (int j = 0; j < i; j++) e[j] = 0;
                for (int j = 0; j < i; j++) {
                    f = d[j]; v[j][i] = f; g = e[j] + v[j][j] * f;
                    for (int k = j + 1; k < i; k++) { g += v[k][j] * d[k]; e[k] += v[k][j] * f; }
                    e[j] = g;
                }
                f = 0;
                for (int j = 0; j < i; j++) { e[j] /= h; f += e[j] * d[j]; }
                double hh = f / (h + h);
                for (int j = 0; j < i; j++) e[j] -= hh * d[j];
                for (int j = 0; j < i; j++) {
                    f = d[j]; g = e[j];
                    for (int k = j; k < i; k++) v[k][j] -= f * e[k] + g * d[k];
                    d[j] = v[i - 1][j]; v[i][j] = 0;
                }
            }
        }
        // The similarity reduction leaves its diagonal in v. Reflector
        // accumulation into eigenvectors is unnecessary for eigenvalues.
        for (int i = 0; i < n; i++) d[i] = v[i][i];
        for (int i = 1; i < n; i++) e[i - 1] = e[i];
        e[n - 1] = 0;
        double shift = 0, threshold = 0, eps = Math.ulp(1.0);
        for (int l = 0; l < n; l++) {
            threshold = Math.max(threshold, Math.abs(d[l]) + Math.abs(e[l]));
            int m = l;
            while (m < n && Math.abs(e[m]) > eps * threshold) m++;
            if (m > l) {
                int iterations = 0;
                do {
                    if (++iterations > 256) throw new ArithmeticException("PoPS covariance eigenvalues did not converge; no covariance adjustment");
                    double g = d[l], p = (d[l + 1] - g) / (2 * e[l]), r = Math.hypot(p, 1);
                    if (p < 0) r = -r;
                    d[l] = e[l] / (p + r); d[l + 1] = e[l] * (p + r);
                    double dl1 = d[l + 1], h = g - d[l];
                    for (int i = l + 2; i < n; i++) d[i] -= h;
                    shift += h;
                    p = d[m];
                    double c = 1, c2 = c, c3 = c, el1 = e[l + 1], s = 0, s2 = 0;
                    for (int i = m - 1; i >= l; i--) {
                        c3 = c2; c2 = c; s2 = s; g = c * e[i]; h = c * p;
                        r = Math.hypot(p, e[i]); e[i + 1] = s * r;
                        s = e[i] / r; c = p / r; p = c * d[i] - s * g;
                        d[i + 1] = h + s * (c * g + s * d[i]);
                    }
                    p = -s * s2 * c3 * el1 * e[l] / dl1; e[l] = s * p; d[l] = c * p;
                    PopsMath.finite(d[l], "PoPS covariance QL step"); PopsMath.finite(e[l], "PoPS covariance QL residual");
                } while (Math.abs(e[l]) > eps * threshold);
            }
            d[l] += shift; e[l] = 0;
        }
        for (int i = 0; i < n; i++) { d[i] *= scale; PopsMath.finite(d[i], "PoPS covariance eigenvalue"); }
        java.util.Arrays.sort(d); return d;
    }
}
