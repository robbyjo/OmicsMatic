/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class DnaPssmTest {
    private static double[][] classic() { return new double[][]{{.8,.05,.05,.1},{.05,.8,.1,.05},{.1,.05,.8,.05},{.05,.1,.05,.8}}; }
    private static double[] uniform() { return new double[]{.25,.25,.25,.25}; }
    private static String fixture(String name, String expectedHash) throws Exception {
        byte[] packed;
        try (var stream = DnaPssmTest.class.getResourceAsStream("pssm/" + name)) { assertNotNull(stream); packed = stream.readAllBytes(); }
        assertEquals(expectedHash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed)));
        try (var stream = new GZIPInputStream(new java.io.ByteArrayInputStream(packed))) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }

    @Test void all118OriginalFunctionModelsAnd24069WordsMatchFrozenIndependentReferences() throws Exception {
        String[] input = fixture("original-5.5.9-input.txt.gz", "c8f36b018345517d4bcc49baf918e6d69d3562b9f9abce4f70f96f886c837bd8").trim().split("\\s+");
        String[] expected = fixture("original-5.5.9-reference.tsv.gz", "52a34587a82302dd05ce923dc9a1aaf6b51bafe90ff184619b3809d052d0802f").split("\n");
        int at = 0, line = 0, models = 0, cells = 0, scoredWords = 0;
        while (at < input.length) {
            String id = input[at++]; int width = Integer.parseInt(input[at++]); int sites = Integer.parseInt(input[at++]);
            double pseudo = Double.parseDouble(input[at++]); double[] bg = new double[4];
            for (int j = 0; j < 4; j++) bg[j] = Double.parseDouble(input[at++]);
            int words = Integer.parseInt(input[at++]); double[][] matrix = new double[width][4];
            for (int i = 0; i < width; i++) for (int j = 0; j < 4; j++) matrix[i][j] = Double.parseDouble(input[at++]);
            var model = DnaPssm.build(matrix, bg, sites, pseudo); models++;
            String[] fields = expected[line++].split("\t"); assertEquals("M", fields[0]); assertEquals(id, fields[1]);
            assertEquals(width, Integer.parseInt(fields[2])); assertEquals(Double.parseDouble(fields[3]), model.scale()); assertEquals(Double.parseDouble(fields[4]), model.offset());
            assertEquals(width * DnaPssm.SCORE_RANGE + 1, Integer.parseInt(fields[5]));
            for (int i = 0; i < width; i++) for (int j = 0; j < 4; j++) {
                fields = expected[line++].split("\t"); assertEquals("P", fields[0]); assertEquals(id, fields[1]);
                assertEquals(i, Integer.parseInt(fields[2])); assertEquals(j, Integer.parseInt(fields[3]));
                double p = Double.parseDouble(fields[4]); assertEquals(p, model.smoothedProbability(i, j), Math.abs(p) * 1e-14, id);
                assertEquals(Integer.parseInt(fields[5]), model.cellScore(i, j), id); cells++;
            }
            for (int i = 0; i < words; i++) {
                String word = input[at++]; var actual = model.score(word); fields = expected[line++].split("\t");
                assertEquals("S", fields[0]); assertEquals(id, fields[1]); assertEquals(i, Integer.parseInt(fields[2])); assertEquals(word, fields[3]);
                assertEquals(Integer.parseInt(fields[4]), actual.integerScore(), id + "/" + word);
                assertEquals(Double.parseDouble(fields[5]), actual.logOdds(), 1e-12, id + "/" + word);
                double p = Double.parseDouble(fields[6]);
                assertEquals(p, actual.pValue(), Math.max(Math.ulp(p) * 16, Math.abs(p) * 5e-12), id + "/" + word);
                scoredWords++;
            }
        }
        assertEquals(118, models); assertEquals(5340, cells); assertEquals(24069, scoredWords); assertEquals(expected.length, line);
    }

    @Test void weightedSinglePositionNullHasKnownTailAndNoImplicitStrandAveraging() {
        var model = DnaPssm.build(new double[][]{{.7,.1,.1,.1}}, new double[]{.1,.2,.3,.4}, 20, 0);
        assertEquals(.1, model.score("A").pValue(), 1e-15); assertEquals(.3, model.score("C").pValue(), 1e-15);
        assertEquals(.6, model.score("G").pValue(), 1e-15); assertEquals(1, model.score("T").pValue(), 1e-15);
        assertArrayEquals(new double[]{.1,.2,.3,.4}, model.background());
    }

    @Test void constantModelHasZeroScoreUnitTailAndNonzeroQuantizationScale() {
        var model = DnaPssm.build(new double[][]{uniform(),uniform(),uniform(),uniform()}, uniform(), 20, .1);
        assertEquals(1000, model.scale()); assertEquals(-1, model.offset());
        var site = model.score("ACGT"); assertEquals(4000, site.integerScore()); assertEquals(0, site.logOdds()); assertEquals(0, site.continuousLogOdds()); assertEquals(1, site.pValue());
    }

    @Test void originalQuantizedScoreAndContinuousScoreRemainDistinct() {
        var model = DnaPssm.build(classic(), uniform(), 20, .1); var site = model.score("ACGT");
        assertEquals(213, model.scale()); assertEquals(-3, model.offset()); assertEquals(3980, site.integerScore());
        assertEquals(6.685446009389672, site.logOdds(), 1e-14); assertEquals(1.0 / 256, site.pValue());
        double continuous = 4 * Math.log(((.8 * 20 + .1 * .25) / 20.1) / .25) / Math.log(2);
        assertEquals(continuous, site.continuousLogOdds(), 1e-13); assertTrue(Math.abs(site.logOdds() - continuous) > 1e-3);
    }

    @Test void resolvedInputAndReturnedBackgroundArraysCannotMutateTheModel() {
        double[][] matrix = classic(); double[] bg = uniform(); var model = DnaPssm.build(matrix, bg, 20, .1); var before = model.score("ACGT");
        Arrays.fill(bg, 0); for (double[] row : matrix) Arrays.fill(row, 0); double[] returned = model.background(); Arrays.fill(returned, 0);
        assertEquals(before, model.score("ACGT")); assertArrayEquals(uniform(), model.background());
    }

    @Test void zeroCellsNeedPositivePseudocountAndAreNeverFilledWithAnInferredEpsilon() {
        double[][] matrix = {{1,0,0,0}};
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(matrix, uniform(), 20, 0));
        var model = DnaPssm.build(matrix, uniform(), 20, .1); assertEquals(.25, model.score("A").pValue());
        assertEquals((0 + .1 * .25) / 20.1, model.smoothedProbability(0, 1));
    }

    @Test void invalidProbabilitiesSitesPseudocountsAndDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(new double[0][], uniform(), 20, .1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(new double[][]{{.5,.5}}, uniform(), 20, .1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(new double[][]{{Double.NaN,.25,.25,.25}}, uniform(), 20, .1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(new double[][]{{-.25,.5,.5,.25}}, uniform(), 20, .1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(new double[][]{{.1,.1,.1,.1}}, uniform(), 20, .1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(classic(), new double[]{1,0,0,0}, 20, .1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(classic(), uniform(), 0, .1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(classic(), uniform(), 20, -1));
        assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(classic(), uniform(), Double.MAX_VALUE, Double.MAX_VALUE));
    }

    @Test void explicitWorkspaceAndConvolutionBudgetsAreEnforcedBeforeBuildingTheNullTable() {
        assertThrows(IllegalArgumentException.class, () -> new DnaPssm.Limits(0, 10));
        var workspace = assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(classic(), uniform(), 20, .1, new DnaPssm.Limits(1, 1000)));
        assertTrue(workspace.getMessage().contains("byte budget"));
        var operations = assertThrows(IllegalArgumentException.class, () -> DnaPssm.build(classic(), uniform(), 20, .1, new DnaPssm.Limits(1_000_000, 1)));
        assertTrue(operations.getMessage().contains("operation budget"));
    }

    @Test void canonicalWordsAreCaseInsensitiveButAmbiguousAndWrongLengthWordsHaveNoCoreScore() {
        var model = DnaPssm.build(classic(), uniform(), 20, .1); assertEquals(model.score("ACGT"), model.score("acgt"));
        assertThrows(IllegalArgumentException.class, () -> model.score("ACGN")); assertThrows(IllegalArgumentException.class, () -> model.score("ACG"));
    }

    @Test void veryRareFiniteTailIsNotRoundedToZeroByAnAbsoluteErrorTolerance() {
        double[][] matrix = new double[48][4]; for (double[] row : matrix) row[0] = 1;
        var model = DnaPssm.build(matrix, new double[]{.0001,.0099,.4,.59}, 20, .1);
        double expected = Math.pow(.0001, 48), actual = model.score("A".repeat(48)).pValue();
        assertTrue(actual > 0); assertEquals(expected, actual, expected * 5e-12);
    }

    @Test void anAttainableTailBelowFloatingPointRangeFailsInsteadOfPublishingAnExactZero() {
        double[][] matrix = {{1,0,0,0},{1,0,0,0},{1,0,0,0},{1,0,0,0}};
        var model = DnaPssm.build(matrix,new double[]{1e-100,.1,.4,.5},20,.1);
        var failure = assertThrows(ArithmeticException.class, () -> model.score("AAAA"));
        assertTrue(failure.getMessage().contains("underflow"));
    }

    @Test void scanBackgroundPreprocessingIsExplicitAndSeparateFromTheCoreBackground() {
        double[] supplied={.0001,.0099,.4,.59};var resolved=DnaPssm.fimoScanBackground(supplied,false);
        assertEquals((.0001+5e-7)/(1+2e-6),resolved[0],1e-19);
        assertEquals((.0099+5e-7)/(1+2e-6),resolved[1],1e-17);
        assertEquals(.0001,supplied[0]);
        var both=DnaPssm.fimoScanBackground(supplied,true);assertEquals(both[0],both[3]);assertEquals(both[1],both[2]);
        assertEquals(((.0001+.59)/2+5e-7)/(1+2e-6),both[0],1e-15);
        assertThrows(IllegalArgumentException.class,()->DnaPssm.fimoScanBackground(new double[]{.1,-.1,.6,.4},true));
    }
}
