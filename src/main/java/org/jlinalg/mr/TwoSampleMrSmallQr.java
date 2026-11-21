/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Norm follows Reference BLAS DNRM2, Algorithm 978 (BSD-3-Clause).
 * See LICENSES/LAPACK-3.12.1-BSD.txt for the retained upstream notice. */
package org.jlinalg.mr;

/** Owned one/two-column Householder fit with the R lm default rank tolerance. */
final class TwoSampleMrSmallQr {
    private TwoSampleMrSmallQr() { }
    record Fit(int rank, double[] coefficients, double[] covarianceDiagonal,
               double residualVariance, double sigma, boolean essentiallyPerfect) { }

    static Fit fit(double[] x, double[] y, double[] weights, boolean intercept) {
        int n = x.length, p = intercept ? 2 : 1;
        double[][] a = new double[p][n]; double[] qty = new double[n], roots = new double[n];
        for (int i = 0; i < n; i++) {
            roots[i] = Math.sqrt(weights[i]); qty[i] = roots[i] * y[i];
            a[0][i] = roots[i] * (intercept ? 1 : x[i]);
            if (intercept) a[1][i] = roots[i] * x[i];
            if (!Double.isFinite(qty[i]) || !Double.isFinite(a[0][i])
                    || (intercept && !Double.isFinite(a[1][i])))
                throw new IllegalArgumentException("weighted regression exceeds the source profile's numerical range");
        }
        double[] initial = new double[p], auxiliary = new double[p];
        for (int j = 0; j < p; j++) initial[j] = norm(a[j], 0);
        int rank = p;
        for (int j = 0; j < p; j++) {
            double length = norm(a[j], j);
            if (length < initial[j] * 1e-7 || length == 0) { rank = j; break; }
            if (a[j][j] != 0) length = Math.copySign(length, a[j][j]);
            // dqrdc2 uses DSCAL with one rounded reciprocal, not one division per entry.
            double reciprocal = 1 / length;
            for (int i = j; i < n; i++) a[j][i] *= reciprocal;
            a[j][j] += 1;
            for (int k = j + 1; k < p; k++) {
                double dot = 0;
                for (int i = j; i < n; i++) dot += a[j][i] * a[k][i];
                double t = -dot / a[j][j];
                for (int i = j; i < n; i++) a[k][i] += t * a[j][i];
            }
            auxiliary[j] = a[j][j]; a[j][j] = -length;
        }
        for (int j = 0; j < rank; j++) reflect(a[j], auxiliary[j], j, qty);
        double[] coefficients = new double[rank]; System.arraycopy(qty, 0, coefficients, 0, rank);
        for (int j = rank - 1; j >= 0; j--) {
            coefficients[j] /= a[j][j];
            for (int i = 0; i < j; i++) coefficients[i] -= coefficients[j] * a[j][i];
        }
        double[] residual = qty.clone();
        for (int i = 0; i < rank; i++) residual[i] = 0;
        for (int j = rank - 1; j >= 0; j--) reflect(a[j], auxiliary[j], j, residual);
        double rss = 0, meanFitted = 0; double[] fitted = new double[n];
        for (int i = 0; i < n; i++) {
            double unweighted = residual[i] / roots[i];
            rss += weights[i] * (unweighted * unweighted);
            fitted[i] = y[i] - unweighted; meanFitted += fitted[i] / n;
        }
        double[] covariance = new double[rank];
        if (rank > 0) { double inverse = 1 / a[0][0]; covariance[0] = inverse * inverse; }
        if (rank > 1) {
            double inverse = 1 / a[1][1], offDiagonal = -inverse * ((1 / a[0][0]) * a[1][0]);
            covariance[0] += offDiagonal * offDiagonal; covariance[1] = inverse * inverse;
        }
        double variance = rss / (n - rank);
        double fittedVariance = 0;
        for (double value : fitted) { double difference = value - meanFitted; fittedVariance += difference * difference; }
        fittedVariance /= n - 1;
        boolean essentiallyPerfect = variance < (meanFitted * meanFitted + fittedVariance) * 1e-30;
        return new Fit(rank, coefficients, covariance, variance, Math.sqrt(variance), essentiallyPerfect);
    }
    private static void reflect(double[] column, double auxiliary, int start, double[] vector) {
        double dot = auxiliary * vector[start];
        for (int i = start + 1; i < vector.length; i++) dot += column[i] * vector[i];
        double factor = -dot / auxiliary; vector[start] += factor * auxiliary;
        for (int i = start + 1; i < vector.length; i++) vector[i] += factor * column[i];
    }
    private static double norm(double[] vector, int start) {
        double small = Math.scalb(1.0, -511), big = Math.scalb(1.0, 486);
        double scaleSmall = Math.scalb(1.0, 537), scaleBig = Math.scalb(1.0, -538);
        double accumulatedSmall = 0, accumulatedMedium = 0, accumulatedBig = 0;
        boolean noBig = true;
        for (int i = start; i < vector.length; i++) {
            double value = Math.abs(vector[i]);
            if (value > big) {
                double scaled = value * scaleBig; accumulatedBig += scaled * scaled; noBig = false;
            } else if (value < small) {
                if (noBig) { double scaled = value * scaleSmall; accumulatedSmall += scaled * scaled; }
            } else accumulatedMedium += value * value;
        }
        if (accumulatedBig > 0) {
            if (accumulatedMedium > 0 || Double.isNaN(accumulatedMedium))
                accumulatedBig += (accumulatedMedium * scaleBig) * scaleBig;
            return (1 / scaleBig) * Math.sqrt(accumulatedBig);
        }
        if (accumulatedSmall > 0) {
            if (accumulatedMedium > 0 || Double.isNaN(accumulatedMedium)) {
                double mediumNorm = Math.sqrt(accumulatedMedium), smallNorm = Math.sqrt(accumulatedSmall) / scaleSmall;
                double maximum = Math.max(mediumNorm, smallNorm), minimum = Math.min(mediumNorm, smallNorm);
                double ratio = minimum / maximum;
                return Math.sqrt(maximum * maximum * (1 + ratio * ratio));
            }
            return (1 / scaleSmall) * Math.sqrt(accumulatedSmall);
        }
        return Math.sqrt(accumulatedMedium);
    }
}
