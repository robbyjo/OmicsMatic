/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GenesisPcairPartitionTest {
    private static final GenesisPcairPartition.Limits LIMITS = new GenesisPcairPartition.Limits(100, 1000000, 10000000);
    private static GenesisPcairPartition.Options options(List<String> forced, List<String> included) {
        return new GenesisPcairPartition.Options(.02, -.02, forced, included);
    }
    private static double[][] diagonal(int n) { double[][] values = new double[n][n]; for (int i = 0; i < n; i++) values[i][i] = .5; return values; }
    private static GenesisPcairPartition.Matrix fixture() { return new GenesisPcairPartition.Matrix(List.of("NA", "NaN", "family\u03b1"), diagonal(3)); }

    @Test void originalInputsAndReturnedMatrixCopiesCannotChangeThePartition() {
        List<String> ids = new ArrayList<>(List.of("NA", "NaN", "family\u03b1")); double[][] values = diagonal(3);
        var matrix = new GenesisPcairPartition.Matrix(ids, values); ids.set(0, "changed"); values[0][0] = Double.NaN;
        matrix.values()[1][1] = Double.POSITIVE_INFINITY;
        var result = GenesisPcairPartition.partition(matrix, null, options(null, null), LIMITS);
        assertEquals(List.of("NA", "NaN", "family\u03b1"), result.unrelated()); assertTrue(result.related().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.unrelated().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> matrix.ids().set(0, "changed"));
    }

    @Test void optionsDoNotRetainMutableCallerSelections() {
        List<String> included = new ArrayList<>(List.of("NA", "NaN")); var request = options(null, included); included.clear();
        assertEquals(List.of("NA", "NaN"), request.sampleInclude());
    }

    @Test void missingAndInvalidUnicodeIdentitiesAreRefusedWithoutReplacement() {
        for (String bad : List.of("", "embedded\0NUL", "\ud800", "\udc00"))
            assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(List.of(bad, "b"), diagonal(2)));
        List<String> nullId = new ArrayList<>(); nullId.add(null); nullId.add("b");
        assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(nullId, diagonal(2)));
        assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(List.of("same", "same"), diagonal(2)));
    }

    @Test void nonfiniteAndAsymmetricMatricesAreExplicitlyOutsideThisProfile() {
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double[][] values = diagonal(2); values[0][1] = values[1][0] = bad;
            assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(List.of("a", "b"), values));
        }
        double[][] asymmetric = diagonal(2); asymmetric[0][1] = .2;
        assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(List.of("a", "b"), asymmetric));
    }

    @Test void incompleteOrUnlabelledOriginalMatricesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(List.of(), new double[0][0]));
        assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(List.of("a", "b"), new double[][]{{.5}, {.5}}));
        assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Matrix(List.of("a", "b"), new double[][]{null, {.5, .5}}));
    }

    @Test void sourceEmptySelectionDiffersFromTheOriginalSingletonDimensionFailure() {
        var empty = GenesisPcairPartition.partition(fixture(), null, options(null, List.of()), LIMITS);
        assertEquals(0, empty.selectedSamples()); assertTrue(empty.related().isEmpty()); assertTrue(empty.unrelated().isEmpty());
        var failure = assertThrows(GenesisPcairPartition.SourceFailure.class,
            () -> GenesisPcairPartition.partition(fixture(), null, options(null, List.of("NA")), LIMITS));
        assertEquals("MATRIX_DIMENSION_DROP", failure.sourceCode());
    }

    @Test void strictCutoffAndSignedZeroInputsRetainSourceValues() {
        double[][] values = diagonal(3); values[0][1] = values[1][0] = .02; values[1][2] = -0.0; values[2][1] = +0.0;
        var matrix = new GenesisPcairPartition.Matrix(List.of("a", "b", "c"), values);
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(matrix.values()[1][2]));
        assertEquals(List.of("a", "b", "c"), GenesisPcairPartition.partition(matrix, null, options(null, null), LIMITS).unrelated());
    }

    @Test void originalLengthOneSimplificationIsFlaggedRatherThanOptimized() {
        // Complete original control: length-one-simplification-related-pairs.
        double[][] values = new double[4][4]; values[0][1] = values[1][0] = values[2][3] = values[3][2] = .25;
        var matrix = new GenesisPcairPartition.Matrix(List.of("a", "b", "c", "d"), values);
        var result = GenesisPcairPartition.partition(matrix, null, options(List.of("b", "d"), null), LIMITS);
        assertEquals(List.of("a", "b", "c", "d"), result.unrelated()); assertTrue(result.related().isEmpty());
        assertEquals(2, result.unrelatedPairsAboveThreshold()); assertFalse(result.unrelatedSetPassesDeclaredKinshipCutoff());
    }

    @Test void sourceFailureSerializationPreservesWarningsAndCauseCategory() throws Exception {
        double[][] values = {{.5, .5}, {.5, .5}}; var matrix = new GenesisPcairPartition.Matrix(List.of("a", "b"), values);
        var failure = assertThrows(GenesisPcairPartition.SourceFailure.class,
            () -> GenesisPcairPartition.partition(matrix, null, options(null, List.of("a", "b", "unknown")), LIMITS));
        assertEquals("ALL_SAMPLES_RELATED", failure.sourceCode()); assertEquals(1, failure.warnings().size());
        var bytes = new ByteArrayOutputStream(); try (var stream = new ObjectOutputStream(bytes)) { stream.writeObject(failure); }
        GenesisPcairPartition.SourceFailure restored;
        try (var stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { restored = (GenesisPcairPartition.SourceFailure) stream.readObject(); }
        assertEquals(failure.sourceCode(), restored.sourceCode()); assertEquals(failure.warnings(), restored.warnings());
        assertThrows(UnsupportedOperationException.class, () -> restored.warnings().add("replacement"));
    }

    @Test void logicalBudgetsBoundCompleteInputsBeforeSelection() {
        assertThrows(IllegalArgumentException.class, () -> GenesisPcairPartition.partition(fixture(), null, options(null, List.of()), new GenesisPcairPartition.Limits(2, 1000000, 10000000)));
        assertThrows(IllegalArgumentException.class, () -> GenesisPcairPartition.partition(fixture(), null, options(null, null), new GenesisPcairPartition.Limits(100, 1, 10000000)));
        assertThrows(IllegalArgumentException.class, () -> GenesisPcairPartition.partition(fixture(), null, options(null, null), new GenesisPcairPartition.Limits(100, 1000000, 1)));
        assertThrows(IllegalArgumentException.class, () -> new GenesisPcairPartition.Options(Double.NaN, -.02, null, null));
    }
}
