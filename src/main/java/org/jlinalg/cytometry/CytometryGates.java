/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.math.BigDecimal;

/** Immutable geometric gate predicates with explicit Gating-ML boundary rules. */
public final class CytometryGates {
    private CytometryGates() { }
    @FunctionalInterface public interface Region { boolean contains(double[] point); }

    /** Cartesian product of [minimum,maximum); infinite endpoints mean unbounded sides. */
    public static Region rectangle(double[] minimum, double[] maximum) {
        if (minimum == null || maximum == null || minimum.length < 1 || minimum.length != maximum.length || minimum.length > 4096) throw new IllegalArgumentException("Require complete equal-length rectangle bounds");
        double[] lower = minimum.clone(), upper = maximum.clone();
        for (int i = 0; i < lower.length; i++) if (!(lower[i] < upper[i])) throw new IllegalArgumentException("Rectangle bounds must be ordered and not NaN");
        return point -> {
            point(point, lower.length);
            for (int i = 0; i < lower.length; i++) if (point[i] < lower[i] || point[i] >= upper[i]) return false;
            return true;
        };
    }

    /** Even/odd polygon fill, including the boundary; self-intersections are supported. */
    public static Region polygon(double[][] vertices) {
        if (vertices == null || vertices.length < 3 || vertices.length > 100000) throw new IllegalArgumentException("Require 3..100000 complete polygon vertices");
        double[][] copy = new double[vertices.length][];
        for (int i = 0; i < vertices.length; i++) { point(vertices[i], 2); copy[i] = vertices[i].clone(); }
        return point -> {
            point(point, 2); boolean inside = false;
            for (int i = 0, j = copy.length-1; i < copy.length; j = i++) {
                double[] a = copy[j], b = copy[i]; int side = orientation(a, b, point);
                if (side == 0 && point[0] >= Math.min(a[0], b[0]) && point[0] <= Math.max(a[0], b[0]) && point[1] >= Math.min(a[1], b[1]) && point[1] <= Math.max(a[1], b[1])) return true;
                if ((a[1] > point[1]) != (b[1] > point[1]) && (side > 0) == (b[1] > a[1])) inside = !inside;
            }
            return inside;
        };
    }
    /** Mahalanobis distance squared <= the supplied threshold; covariance must be symmetric SPD. */
    public static Region ellipsoid(double[] mean, double[][] covariance, double distanceSquared) {
        if (mean == null || mean.length < 2 || mean.length > 512 || !Double.isFinite(distanceSquared) || distanceSquared < 0) throw new IllegalArgumentException("Require a finite nonnegative ellipsoid threshold and 2..512 dimensions");
        point(mean, mean.length); double[] center = mean.clone(); int n = mean.length;
        if (covariance == null || covariance.length != n) throw new IllegalArgumentException("Complete ellipsoid covariance is required");
        for (double[] row : covariance) point(row, n);
        for (int i = 0; i < n; i++) for (int j = 0; j < i; j++) if (covariance[i][j] != covariance[j][i]) throw new IllegalArgumentException("Ellipsoid covariance is asymmetric; no averaging or repair");
        double[][] lower = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++) {
            double sum = covariance[i][j];
            for (int k = 0; k < j; k++) sum -= lower[i][k]*lower[j][k];
            if (i == j) {
                if (!(sum > 0) || !Double.isFinite(sum)) throw new IllegalArgumentException("Ellipsoid covariance is not positive definite/resolved at these units/precision; no jitter");
                lower[i][j] = Math.sqrt(sum);
            } else {
                lower[i][j] = sum/lower[j][j];
                if (!Double.isFinite(lower[i][j])) throw new ArithmeticException("Ellipsoid covariance factor is not finite");
            }
        }
        return point -> {
            point(point, n); double[] standardized = new double[n]; double sumSquares = 0;
            for (int i = 0; i < n; i++) {
                double value = point[i]-center[i];
                for (int j = 0; j < i; j++) value -= lower[i][j]*standardized[j];
                standardized[i] = value/lower[i][i];
                if (!Double.isFinite(standardized[i])) throw new ArithmeticException("Ellipsoid distance cannot be resolved at these units/precision");
                sumSquares += standardized[i]*standardized[i];
                if (sumSquares > distanceSquared) return false;
            }
            return sumSquares <= distanceSquared;
        };
    }
    private static void point(double[] point, int dimensions) {
        if (point == null || point.length != dimensions) throw new IllegalArgumentException("Require one complete gate coordinate vector");
        for (double value : point) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite gate coordinate; no dropped event or inferred membership");
    }
    private static int orientation(double[] a, double[] b, double[] p) {
        double left = (b[0]-a[0])*(p[1]-a[1]), right = (b[1]-a[1])*(p[0]-a[0]);
        double determinant = left-right, bound = (Math.abs(left)+Math.abs(right))*3.3306690738754716e-16;
        if (Double.isFinite(determinant) && Math.abs(determinant) > bound) return determinant > 0 ? 1 : -1;
        // Exact arithmetic on the original binary-double coordinates at ambiguous edges.
        BigDecimal ax = new BigDecimal(a[0]), ay = new BigDecimal(a[1]);
        BigDecimal bx = new BigDecimal(b[0]), by = new BigDecimal(b[1]);
        BigDecimal px = new BigDecimal(p[0]), py = new BigDecimal(p[1]);
        return bx.subtract(ax).multiply(py.subtract(ay)).subtract(by.subtract(ay).multiply(px.subtract(ax))).signum();
    }
}
