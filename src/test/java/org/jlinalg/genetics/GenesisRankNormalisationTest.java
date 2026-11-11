/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenesisRankNormalisationTest {
    private static final GenesisRankNormalisation.Limits LIMITS = new GenesisRankNormalisation.Limits(100, 1_000_000);
    @Test void averageTiesRetainOriginalRowOrder() {
        double[] input = {3, 1, 1, 5}; var result = GenesisRankNormalisation.normalize(input, LIMITS);
        assertArrayEquals(new double[]{3, 1.5, 1.5, 4}, result.averageRanks());
        // Fixed reference quantiles from the original R formula, not a second rank implementation.
        assertArrayEquals(new double[]{.31863936396437514, -.6744897501960817, -.6744897501960817, 1.1503493803760079}, result.scores(), 2e-14);
        assertArrayEquals(new double[]{3, 1, 1, 5}, input); assertTrue(result.groupOrder().isEmpty());
    }
    @Test void signedZerosAreOneNumericTie() {
        var result = GenesisRankNormalisation.normalize(new double[]{-0., 0., -0., 0.}, LIMITS);
        assertArrayEquals(new double[]{2.5, 2.5, 2.5, 2.5}, result.averageRanks()); assertArrayEquals(new double[4], result.scores());
    }
    @Test void explicitGroupsUseIndependentRanksAndRetainFirstAppearanceMetadata() {
        var result = GenesisRankNormalisation.normalizeByGroup(new double[]{1, 2, 3, 4}, List.of("B", "A", "B", "A"), LIMITS);
        assertArrayEquals(new double[]{1, 1, 2, 2}, result.averageRanks()); assertArrayEquals(new int[]{2, 2, 2, 2}, result.groupSizes()); assertEquals(List.of("B", "A"), result.groupOrder());
        assertArrayEquals(new double[]{-.6744897501960817, -.6744897501960817, .6744897501960817, .6744897501960817}, result.scores(), 2e-14);
    }
    @Test void literalNaLabelsUnicodeAndSingletonsRemainDistinct() {
        var result = GenesisRankNormalisation.normalizeByGroup(new double[]{1, 1, 1}, List.of("NA", "NaN", "家族"), LIMITS);
        assertEquals(List.of("NA", "NaN", "家族"), result.groupOrder()); assertArrayEquals(new double[3], result.scores()); assertArrayEquals(new int[]{1, 1, 1}, result.groupSizes());
    }
    @Test void explicitlyEmptyRankVectorsStayEmpty() {
        var result = GenesisRankNormalisation.normalize(new double[0], LIMITS); assertEquals(0, result.scores().length); assertEquals(0, result.averageRanks().length);
        assertEquals(0, GenesisRankNormalisation.normalizeByGroup(new double[0], List.of(), LIMITS).groupSizes().length);
    }
    @Test void nonfiniteValuesAreRefusedWithoutChangingInput() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            double[] input = {1, value, 2}; assertThrows(IllegalArgumentException.class, () -> GenesisRankNormalisation.normalize(input, LIMITS)); assertArrayEquals(new double[]{1, value, 2}, input);
        }
    }
    @Test void invalidGroupsAndCapacitiesRefuseBeforeRanking() {
        assertThrows(IllegalArgumentException.class, () -> GenesisRankNormalisation.normalizeByGroup(new double[]{1}, List.of(), LIMITS));
        for (String label : List.of("", "A\nB", "\ud800")) assertThrows(IllegalArgumentException.class, () -> GenesisRankNormalisation.normalizeByGroup(new double[]{1}, List.of(label), LIMITS));
        assertThrows(IllegalArgumentException.class, () -> GenesisRankNormalisation.normalize(new double[]{1, 2}, new GenesisRankNormalisation.Limits(1, 1_000_000)));
        assertThrows(IllegalArgumentException.class, () -> GenesisRankNormalisation.normalize(new double[]{1}, new GenesisRankNormalisation.Limits(10, 1)));
    }
    @Test void returnedArraysAndMetadataAreDefensive() {
        var result = GenesisRankNormalisation.normalizeByGroup(new double[]{1, 2}, List.of("A", "A"), LIMITS);
        result.averageRanks()[0] = 999; result.scores()[0] = 999; result.groupSizes()[0] = 999;
        assertEquals(1, result.averageRanks()[0]); assertTrue(result.scores()[0] < 0); assertEquals(2, result.groupSizes()[0]); assertThrows(UnsupportedOperationException.class, () -> result.groupOrder().add("B"));
    }
}
