/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * Derived from scrattch.bigcat 0.0.5, R/find_noise_cl.R, find_doublet_by_marker.
 * Upstream commit bbfe5277de63c65a5342e43f3e1eeb1015755fc7, License: GPL-3.
 * Authors and supported scope are recorded in META-INF/licenses/scrattch-bigcat005.txt.
 */
package org.jlinalg.singlecell.scrattch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native numeric components of scrattch.bigcat 0.0.5. */
public final class ScrattchBigcat005 {
    private ScrattchBigcat005() {}

    /** R's missing-real payload, used when an unknown logical index selects a column. */
    public static final long R_NA_REAL_BITS = 0x7ff00000000007a2L;
    private static final double R_NA_REAL = Double.longBitsToDouble(R_NA_REAL_BITS);

    /**
     * A plain dense R numeric matrix. Explicit columns also represent zero-row
     * matrices. Null names mean absent names, and null name elements mean R NA
     * strings. The flag distinguishes absent dimnames from list(NULL, NULL).
     * Arrays remain caller-owned; the operation never changes them.
     */
    public record DenseMatrix(double[][] values, int columns, String[] rowNames,
                              String[] columnNames, boolean dimnamesPresent) {
        public DenseMatrix {
            Objects.requireNonNull(values, "values");
            if (columns < 0) throw new IllegalArgumentException("Negative column count");
            for (double[] row : values)
                if (row == null || row.length != columns)
                    throw new IllegalArgumentException("Nonrectangular matrix");
            if (rowNames != null && rowNames.length != values.length)
                throw new IllegalArgumentException("Row-name count differs from matrix");
            if (columnNames != null && columnNames.length != columns)
                throw new IllegalArgumentException("Column-name count differs from matrix");
            if (!dimnamesPresent && (rowNames != null || columnNames != null))
                throw new IllegalArgumentException("Supplied names require a dimnames attribute");
            if (rowNames != null && rowNames.length == 0) rowNames = null;
            if (columnNames != null && columnNames.length == 0) columnNames = null;
        }

        public DenseMatrix(double[][] values, int columns, String[] rowNames, String[] columnNames) {
            this(values, columns, rowNames, columnNames, rowNames != null || columnNames != null);
        }
    }

    /** Result arrays are newly allocated; no R runtime or external tool is invoked. */
    public record MarkerDoublets(DenseMatrix matrix, boolean requiresNonFiniteReview) {}

    /** Original R message text, without claiming an R condition object or call stack. */
    public static final class OriginalMarkerException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private OriginalMarkerException(String message) { super(message); }
    }

    /** Use the original eight marker groups and threshold 3.5. */
    public static MarkerDoublets findDoubletByMarker(DenseMatrix clusterMeans) {
        return findDoubletByMarker(clusterMeans, new String[][] {
                {"Slc32a1", "Slc17a7", "Slc17a6"}, {"Sox10", "Opalin"},
                {"Aqp4"}, {"Ly6c1"}, {"Slc6a13"}, {"Kcnj8"}, {"Acta2"}, {"C1qc"}
        }, 3.5);
    }

    /**
     * Execute find_doublet_by_marker for a plain dense double matrix, a list of
     * character marker groups, and one numeric threshold. A null group denotes
     * R NULL; a null marker denotes NA_character_. Names and duplicate markers
     * are literal. Group names do not affect this operation's result.
     *
     * <p>The original drops dimensions before transposing: one selected cluster
     * loses its cluster name, and one marker with no selected clusters returns
     * a 1-by-0 matrix. Zero or one input columns, and an empty group list, retain
     * the original rowSums failure. Missing genes retain the original failure.
     * An unknown group comparison selects an entirely missing column rather
     * than silently dropping it. Existing numeric payloads are copied unchanged.
     * This component does not perform clustering, harmonization, or cell mapping.</p>
     */
    public static MarkerDoublets findDoubletByMarker(DenseMatrix clusterMeans,
                                                      String[][] markerGroups, double threshold) {
        Objects.requireNonNull(clusterMeans, "clusterMeans");
        Objects.requireNonNull(markerGroups, "markerGroups");
        int columns = clusterMeans.columns();
        List<int[]> groups = new ArrayList<>(markerGroups.length);
        List<Integer> flattened = new ArrayList<>();
        for (String[] group : markerGroups) {
            // R character(0) still requires dimnames for character indexing;
            // R NULL uses a different index type and does not require them.
            if (group != null && !clusterMeans.dimnamesPresent())
                throw new OriginalMarkerException("no 'dimnames' attribute for array");
            int[] indices = new int[group == null ? 0 : group.length];
            for (int j = 0; j < indices.length; j++) {
                if (!clusterMeans.dimnamesPresent())
                    throw new OriginalMarkerException("no 'dimnames' attribute for array");
                int index = firstRow(clusterMeans.rowNames(), group[j]);
                if (index < 0) throw new OriginalMarkerException("subscript out of bounds");
                indices[j] = index;
                flattened.add(index);
            }
            groups.add(indices);
        }
        if (markerGroups.length == 0 || columns < 2)
            throw new OriginalMarkerException("'x' must be an array of at least two dimensions");

        List<Integer> selected = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            int positives = 0;
            boolean unknown = Double.isNaN(threshold);
            for (int[] group : groups) {
                double maximum = Double.NEGATIVE_INFINITY;
                for (int row : group) {
                    double value = clusterMeans.values()[row][c];
                    if (Double.isNaN(value)) unknown = true;
                    else if (value > maximum) maximum = value;
                }
                if (maximum > threshold) positives++;
            }
            if (unknown || positives > 1) selected.add(unknown ? -1 : c);
        }

        int markerCount = flattened.size(), selectedCount = selected.size();
        // R's default drop happens on the subset, before the final transpose.
        boolean vector = markerCount == 1 || selectedCount == 1;
        int outRows = vector ? 1 : selectedCount;
        int outColumns = vector ? markerCount * selectedCount : markerCount;
        double[][] output = new double[outRows][outColumns];
        String[] outRowNames = null, outColumnNames = null;
        boolean outDimnames;
        if (vector) {
            if (markerCount == 1) {
                for (int i = 0; i < selectedCount; i++)
                    output[0][i] = element(clusterMeans, flattened.get(0), selected.get(i));
                if (selectedCount != 1)
                    outColumnNames = selectedNames(clusterMeans.columnNames(), selected);
            } else {
                for (int i = 0; i < markerCount; i++)
                    output[0][i] = element(clusterMeans, flattened.get(i), selected.get(0));
                outColumnNames = markerNames(clusterMeans.rowNames(), flattened);
            }
            outDimnames = outColumnNames != null && outColumnNames.length > 0;
        } else {
            for (int r = 0; r < selectedCount; r++)
                for (int c = 0; c < markerCount; c++)
                    output[r][c] = element(clusterMeans, flattened.get(c), selected.get(r));
            outRowNames = selectedNames(clusterMeans.columnNames(), selected);
            outColumnNames = markerNames(clusterMeans.rowNames(), flattened);
            outDimnames = clusterMeans.dimnamesPresent();
        }
        boolean review = false;
        for (double[] row : output)
            for (double value : row) review |= !Double.isFinite(value);
        return new MarkerDoublets(new DenseMatrix(output, outColumns, outRowNames,
                outColumnNames, outDimnames), review);
    }

    private static int firstRow(String[] names, String gene) {
        if (names != null && gene != null && !gene.isEmpty())
            for (int i = 0; i < names.length; i++) if (gene.equals(names[i])) return i;
        return -1;
    }

    private static double element(DenseMatrix matrix, int row, int column) {
        return column < 0 ? R_NA_REAL : matrix.values()[row][column];
    }

    private static String[] markerNames(String[] names, List<Integer> rows) {
        if (names == null || rows.isEmpty()) return null;
        String[] result = new String[rows.size()];
        for (int i = 0; i < result.length; i++) result[i] = names[rows.get(i)];
        return result;
    }

    private static String[] selectedNames(String[] names, List<Integer> columns) {
        if (names == null || columns.isEmpty()) return null;
        String[] result = new String[columns.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = columns.get(i) < 0 ? null : names[columns.get(i)];
        return result;
    }
}
