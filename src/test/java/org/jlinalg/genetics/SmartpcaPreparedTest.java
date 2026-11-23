/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SmartpcaPreparedTest {
    @Test void comparesNineUnchangedOriginalProgramEigenvaluesGramsAndAllProjectedScores() throws Exception {
        int compared = 0, excluded = 0;
        try (var stream = getClass().getResourceAsStream("/org/jlinalg/genetics/smartpca-original-reference.zip")) {
            assertNotNull(stream);
            try (var zip = new ZipInputStream(stream)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.getName().endsWith("/reference.json")) continue;
                    Map<?,?> row = object(GprofilerJson.read(zip.readAllBytes())); String name = (String) row.get("case");
                    if (!Boolean.TRUE.equals(row.get("native_comparison_required"))) { excluded++; continue; }
                    assertEquals(0, integer(row.get("exit_code")), name);
                    Map<?,?> input = object(row.get("input")), options = object(row.get("options"));
                    List<?> matrix = list(input.get("genotypes")); byte[][] calls = new byte[matrix.size()][];
                    for (int s = 0; s < calls.length; s++) {
                        List<?> cells = list(matrix.get(s)); calls[s] = new byte[cells.size()];
                        for (int i = 0; i < calls[s].length; i++) calls[s][i] = (byte) integer(cells.get(i));
                    }
                    int[] basis = list(input.get("basis_indices")).stream().mapToInt(SmartpcaPreparedTest::integer).toArray();
                    var settings = new SmartpcaPrepared.Options(integer(options.get("components")), bool(options.get("use_norm")),
                        bool(options.get("alternate_norm_style")), integer(options.get("min_alleles")), integer(options.get("max_missing")),
                        bool(options.get("least_squares")) ? SmartpcaPrepared.Projection.LEAST_SQUARES : SmartpcaPrepared.Projection.ORTHOGONAL,
                        SmartpcaPrepared.Limits.defaults());
                    var fit = SmartpcaPrepared.fit(calls, basis, settings); double[] eigenvalues = fit.eigenvalues();
                    String[] originalValues = ((String) row.get("original_eval")).trim().split("\\s+");
                    assertEquals(eigenvalues.length, originalValues.length, name);
                    for (int i = 0; i < eigenvalues.length; i++) printed(Double.parseDouble(originalValues[i]), eigenvalues[i], 6, name + " eigenvalue " + i);
                    double[][] gram = fit.basisGram(); long used = fit.markers().stream().filter(m -> m.status() == SmartpcaPrepared.MarkerStatus.USED).count();
                    int cells = 0;
                    for (String line : ((String) row.get("original_grm")).lines().toList()) {
                        String[] fields = line.trim().split("\\s+"); int i = Integer.parseInt(fields[0]) - 1, j = Integer.parseInt(fields[1]) - 1;
                        assertEquals(used, Long.parseLong(fields[2]), name + " source used-marker count");
                        printed(Double.parseDouble(fields[3]), gram[i][j] * basis.length / (basis.length - 1), 6, name + " GRM " + i + "," + j); cells++;
                    }
                    assertEquals(basis.length * (basis.length + 1) / 2, cells, name);
                    Map<String,double[]> expected = new LinkedHashMap<>();
                    for (String line : ((String) row.get("original_evec")).lines().skip(1).toList()) {
                        if (line.isBlank()) continue; String[] fields = line.trim().split("\\s+"); double[] values = new double[settings.components()];
                        for (int c = 0; c < values.length; c++) values[c] = Double.parseDouble(fields[c + 1]);
                        assertNull(expected.put(fields[0], values), name + " duplicate original sample");
                    }
                    List<?> ids = list(input.get("sample_ids")); double[][] actual = fit.scores(); double[] signs = new double[settings.components()];
                    for (int c = 0; c < signs.length; c++) {
                        double dot = 0; for (int i : basis) if (expected.containsKey(ids.get(i))) dot += expected.get(ids.get(i))[c] * actual[i][c];
                        assertNotEquals(0, dot, name + " identifiable component orientation"); signs[c] = Math.copySign(1, dot);
                    }
                    for (int i = 0; i < ids.size(); i++) {
                        double[] reference = expected.get(ids.get(i));
                        if (reference == null) {
                            assertEquals(SmartpcaPrepared.ScoreStatus.INSUFFICIENT_NONMISSING_MARKERS, fit.scoreStatus().get(i), name);
                            for (double value : actual[i]) assertTrue(Double.isNaN(value), name + " unreportable sample stays NaN");
                        } else {
                            assertEquals(SmartpcaPrepared.ScoreStatus.COMPUTED, fit.scoreStatus().get(i), name);
                            for (int c = 0; c < signs.length; c++) printed(reference[c], actual[i][c] * signs[c], 4, name + " score " + ids.get(i) + "/" + c);
                        }
                    }
                    compared++;
                }
            }
        }
        assertEquals(9, compared); assertEquals(3, excluded);
    }

    @Test void executableNormalizationOverridesReversedSourceCommentAndNeverPseudocountsTheMean() {
        byte[][] calls = {{0,0,0,2}, {0,1,2,1}, {2,0,1,0}}; int[] basis = {0,1,2,3};
        var yes = SmartpcaPrepared.fit(calls, basis, settings(true, true));
        var no = SmartpcaPrepared.fit(calls, basis, settings(true, false));
        assertEquals(.5, yes.markers().get(0).mean()); assertEquals(.5, no.markers().get(0).mean());
        assertEquals(1 / Math.sqrt(.25 * .75), yes.markers().get(0).multiplier());
        assertEquals(1 / Math.sqrt(.3 * .7), no.markers().get(0).multiplier());
        assertNotEquals(yes.covarianceScale(), no.covarianceScale());
    }
    @Test void targetGenotypesNeverEnterReferenceAlleleFrequenciesOrBasisGram() {
        byte[][] calls = {{0,1,2,0,0},{2,0,1,2,0},{1,2,0,0,0},{0,0,2,1,0}}; int[] basis = {0,1,2,3};
        var first = SmartpcaPrepared.fit(calls, basis, settings(true,true));
        for (byte[] marker : calls) marker[4] = 2;
        var changed = SmartpcaPrepared.fit(calls, basis, settings(true,true));
        assertEquals(first.markers(), changed.markers()); assertArrayEquals(first.eigenvalues(), changed.eigenvalues());
        for (int i = 0; i < basis.length; i++) assertArrayEquals(first.basisGram()[i], changed.basisGram()[i]);
        assertFalse(Arrays.equals(first.scores()[4], changed.scores()[4]));
    }
    @Test void disabledAlleleNormalizationIgnoresAlternateDenominatorStyle() {
        byte[][] calls = {{0,1,2,0},{2,0,1,2},{1,2,0,0},{0,0,2,1}}; int[] basis = {0,1,2,3};
        var first = SmartpcaPrepared.fit(calls,basis,settings(false,true));
        var second = SmartpcaPrepared.fit(calls,basis,settings(false,false));
        assertArrayEquals(first.eigenvalues(),second.eigenvalues());
        for (int i=0;i<basis.length;i++) assertArrayEquals(first.scores()[i],second.scores()[i]);
    }
    @Test void refusesInvalidCallsIndicesRaggedPanelsAndZeroTraceWithoutRepair() {
        var settings = settings(true,true);
        assertThrows(IllegalArgumentException.class, () -> SmartpcaPrepared.fit(new byte[][]{{0,3,2},{1,0,2}},new int[]{0,1,2},settings));
        assertThrows(IllegalArgumentException.class, () -> SmartpcaPrepared.fit(new byte[][]{{0,1,2},{1,0}},new int[]{0,1,2},settings));
        assertThrows(IllegalArgumentException.class, () -> SmartpcaPrepared.fit(new byte[][]{{0,1,2},{1,0,2}},new int[]{0,0,2},settings));
        assertThrows(IllegalArgumentException.class, () -> SmartpcaPrepared.fit(new byte[][]{{0,1,2},{1,0,2}},new int[]{0,1,3},settings));
        assertThrows(IllegalArgumentException.class, () -> SmartpcaPrepared.fit(new byte[][]{{0,0,0},{2,2,2},{9,9,9}},new int[]{0,1,2},settings));
    }
    @Test void refusesExplicitCapacityBeforeAllocatingCompleteMatrixAndHonorsInterrupt() {
        byte[][] calls={{0,1,2},{2,0,1},{0,2,1}}; int[] basis={0,1,2};
        var tiny=new SmartpcaPrepared.Options(1,true,true,1,99,SmartpcaPrepared.Projection.ORTHOGONAL,new SmartpcaPrepared.Limits(1,1));
        assertThrows(IllegalArgumentException.class, () -> SmartpcaPrepared.fit(calls,basis,tiny));
        Thread.currentThread().interrupt();
        try { assertThrows(IllegalStateException.class, () -> SmartpcaPrepared.fit(calls,basis,settings(true,true))); }
        finally { Thread.interrupted(); }
    }
    @Test void resultAndCallerInputsRemainIndependent() {
        byte[][] calls={{0,1,2},{2,0,1},{0,2,1}}; int[] basis={0,1,2}; String before=Arrays.deepToString(calls);
        var fit=SmartpcaPrepared.fit(calls,basis,settings(true,true));
        assertEquals(before,Arrays.deepToString(calls)); assertArrayEquals(new int[]{0,1,2},basis);
        double value=fit.scores()[0][0];fit.scores()[0][0]=999;fit.basisIndices()[0]=999;fit.eigenvalues()[0]=999;
        assertEquals(value,fit.scores()[0][0]); assertEquals(0,fit.basisIndices()[0]); assertNotEquals(999,fit.eigenvalues()[0]);
    }
    private static SmartpcaPrepared.Options settings(boolean useNorm, boolean alternate) {
        return new SmartpcaPrepared.Options(1,useNorm,alternate,1,9_999_999,SmartpcaPrepared.Projection.ORTHOGONAL,SmartpcaPrepared.Limits.defaults());
    }
    private static void printed(double expected,double actual,int decimals,String message) {
        assertTrue(Double.isFinite(actual),message); double envelope=.50001*Math.pow(10,-decimals)+32*Math.ulp(expected);
        assertEquals(expected,actual,envelope,message+"; original decimal printing envelope only");
    }
    private static Map<?,?> object(Object value) { if(value instanceof Map<?,?> map)return map;throw new IllegalArgumentException("Expected object"); }
    private static List<?> list(Object value) { if(value instanceof List<?> values)return values;throw new IllegalArgumentException("Expected array"); }
    private static int integer(Object value) { return Integer.parseInt(((GprofilerJson.Numeric)value).token()); }
    private static boolean bool(Object value) { return (Boolean)value; }

    /** Runs the same seven JUnit methods in isolated compiler/reference profiles. */
    public static void main(String[] arguments)throws Exception {
        var tests=new SmartpcaPreparedTest();
        tests.comparesNineUnchangedOriginalProgramEigenvaluesGramsAndAllProjectedScores();
        tests.executableNormalizationOverridesReversedSourceCommentAndNeverPseudocountsTheMean();
        tests.targetGenotypesNeverEnterReferenceAlleleFrequenciesOrBasisGram();
        tests.disabledAlleleNormalizationIgnoresAlternateDenominatorStyle();
        tests.refusesInvalidCallsIndicesRaggedPanelsAndZeroTraceWithoutRepair();
        tests.refusesExplicitCapacityBeforeAllocatingCompleteMatrixAndHonorsInterrupt();
        tests.resultAndCallerInputsRemainIndependent();
        System.out.println("PASS: seven smartpca prepared-component methods; nine original whole-program comparisons. No workflow/catalog admission.");
    }
}
