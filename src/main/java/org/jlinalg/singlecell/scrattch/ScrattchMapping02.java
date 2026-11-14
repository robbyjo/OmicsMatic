/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * Derived from scrattch.mapping 0.2, R/HANN_utils.R, l2norm.
 * Upstream commit df8751129e47a140e2d3ceecfd71438fca1149ef, License: GPL-3.
 * See META-INF/licenses/scrattch-mapping02.txt for attribution and scope.
 */
package org.jlinalg.singlecell.scrattch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Numeric components of the original scrattch.mapping 0.2 workflow. */
public final class ScrattchMapping02 {
    private ScrattchMapping02() {}

    public static final String COLUMN_ZERO_WARNING =
            "L2 norms of zero detected for distance='Cosine, no transformation";
    public static final String ROW_ZERO_WARNING =
            "L2 norms of zero detected for distance='Cosine'";

    /**
     * The caller owns the returned arrays. Inputs are never mutated. Null names
     * mean absent dimnames; a null element is an R missing name, while "NA" is
     * a literal name. Warnings are original message text, not R condition objects.
     */
    public record Normalization(double[][] values, int columns, String[] rowNames,
                                String[] columnNames, List<String> warnings,
                                boolean requiresNonFiniteReview) {
        public Normalization {
            warnings = List.copyOf(warnings);
        }
    }

    /** Original failure of the normalization condition; no result is supplied. */
    public static final class OriginalNormalizationException extends ArithmeticException {
        private static final long serialVersionUID = 1L;
        private OriginalNormalizationException(String message) { super(message); }
    }

    /** Normalize an unnamed dense matrix; columns is explicit for zero-row inputs. */
    public static Normalization l2norm(double[][] values, int columns, String by) {
        return l2norm(values, columns, null, null, by);
    }

    /**
     * Execute the original numeric matrix operation, including its zero-norm
     * branches. Exactly "column" selects columns; other non-null strings select
     * rows, as in the original. If any column has zero norm, the entire matrix
     * stays unchanged. If any row has zero norm, every row is divided by
     * max(norm, 1), including nonzero rows with norm below one. Values are squared
     * before summation; overflow/underflow is retained rather than rescaled.
     *
     * <p>R missing and NaN payload identities are not distinguished by this
     * numeric API. Input dimensions and supplied names are preserved. This
     * operation does not perform nearest-neighbor or hierarchical mapping.</p>
     */
    public static Normalization l2norm(double[][] values, int columns,
                                      String[] rowNames, String[] columnNames, String by) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(by, "by must be one non-missing string");
        if (columns < 0) throw new IllegalArgumentException("Negative column count");
        if (rowNames != null && rowNames.length != values.length)
            throw new IllegalArgumentException("Row-name count differs from matrix");
        if (columnNames != null && columnNames.length != columns)
            throw new IllegalArgumentException("Column-name count differs from matrix");
        double[][] output = new double[values.length][columns];
        for (int r = 0; r < values.length; r++) {
            if (values[r] == null || values[r].length != columns)
                throw new IllegalArgumentException("Nonrectangular matrix");
            System.arraycopy(values[r], 0, output[r], 0, columns);
        }
        boolean column = by.equals("column");
        double[] norms = new double[column ? columns : values.length];
        boolean zero = false, missing = false;
        for (int i = 0; i < norms.length; i++) {
            norms[i] = norm(values, columns, i, column);
            zero |= norms[i] == 0;
            missing |= Double.isNaN(norms[i]);
        }
        // In R, any(TRUE, NA) is TRUE; any(FALSE, NA) remains NA.
        if (!zero && missing)
            throw new OriginalNormalizationException("missing value where TRUE/FALSE needed");
        List<String> warnings = new ArrayList<>(1);
        if (zero) warnings.add(column ? COLUMN_ZERO_WARNING : ROW_ZERO_WARNING);
        if (!(column && zero)) {
            for (int r = 0; r < output.length; r++) {
                for (int c = 0; c < columns; c++) {
                    double divisor = norms[column ? c : r];
                    if (!column && zero) divisor = Math.max(divisor, 1);
                    output[r][c] /= divisor;
                }
            }
        }
        boolean review = false;
        for (double[] row : output)
            for (double value : row) review |= !Double.isFinite(value);
        return new Normalization(output, columns,
                rowNames == null ? null : rowNames.clone(),
                columnNames == null ? null : columnNames.clone(), warnings, review);
    }

    private static double norm(double[][] values, int columns, int index, boolean column) {
        // Compensated summation approximates R's extended-precision sum; the
        // source comparisons prescribe a finite-value tolerance, not bit parity.
        double sum = 0, correction = 0;
        int count = column ? values.length : columns;
        for (int i = 0; i < count; i++) {
            double value = column ? values[i][index] : values[index][i];
            double square = value * value;
            double next = sum + square;
            if (Double.isFinite(sum) && Double.isFinite(square) && Double.isFinite(next)) {
                correction += Math.abs(sum) >= Math.abs(square)
                        ? (sum - next) + square : (square - next) + sum;
            } else {
                correction = 0;
            }
            sum = next;
        }
        return Math.sqrt(sum + correction);
    }
}
