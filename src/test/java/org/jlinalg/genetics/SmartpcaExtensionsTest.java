/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SmartpcaExtensionsTest {
    @Test void comparesTwentyThreeUnchangedOriginalOutlierFunctionCallsIncludingStrictAndNonfiniteBoundaries() throws Exception {
        int cases=0;
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/genetics/smartpca-original-outlier-reference.zip")) {
            assertNotNull(stream);
            try(var zip=new ZipInputStream(stream)) {
                java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    if(!entry.getName().endsWith("/reference.json"))continue;
                    var reference=object(GprofilerJson.read(zip.readAllBytes()));String name=(String)reference.get("case");
                    List<?> raw=list(reference.get("vectors"));double[][] vectors=new double[raw.size()][];
                    for(int i=0;i<vectors.length;i++)vectors[i]=list(raw.get(i)).stream().mapToDouble(SmartpcaExtensionsTest::number).toArray();
                    int mode=integer(reference.get("mode"));var actual=SmartpcaOutliers.detect(vectors,vectors[0].length,number(reference.get("threshold")),SmartpcaOutliers.Mode.values()[mode]);
                    var result=object(reference.get("result"));var hits=list(result.get("hits"));assertEquals(integer(result.get("count")),actual.hits().size(),name);
                    for(int i=0;i<hits.size();i++) {
                        var expected=object(hits.get(i));var hit=actual.hits().get(i);
                        assertEquals(integer(expected.get("sample")),hit.sampleOrdinal(),name);assertEquals(integer(expected.get("zero_based_axis")),hit.zeroBasedAxis(),name);
                        var score=object(expected.get("score"));
                        if(score.get("kind").equals("FINITE")) {
                            double value=number(score.get("value"));assertEquals(value,hit.signedDeviation(),3e-12*Math.abs(value)+16*Math.ulp(value),name);
                        } else {
                            String literal=(String)score.get("literal");
                            if(literal.equals("NaN"))assertTrue(Double.isNaN(hit.signedDeviation()),name);
                            else assertEquals(literal.equals("Inf")?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY,hit.signedDeviation(),name);
                            assertTrue(actual.nonfiniteDiagnostics(),name);
                        }
                    }
                    cases++;
                }
            }
        }
        assertEquals(23,cases);
    }
    @Test void comparesOriginalWholeProgramShrinkageCoordinatesAndEigenvalues() throws Exception {
        var reference=originalShrinkage();var input=object(reference.get("input"));var options=object(reference.get("options"));
        byte[][] calls=calls(input);int[] basis=list(input.get("basis_indices")).stream().mapToInt(SmartpcaExtensionsTest::integer).toArray();
        var result=SmartpcaShrinkage.fit(calls,basis,settings(options));
        String[] eigenvalues=((String)reference.get("original_eval")).trim().split("\\s+");
        assertEquals(eigenvalues.length,result.eigenvalues().length);
        for(int i=0;i<eigenvalues.length;i++)assertEquals(Double.parseDouble(eigenvalues[i]),result.eigenvalues()[i],.50001e-6,"original printed eigenvalue "+i);
        var lines=((String)reference.get("original_evec")).lines().skip(1).filter(line->!line.isBlank()).toList();
        double[][] scores=result.scores();assertEquals(scores.length,lines.size());double[][] expected=new double[scores.length][scores[0].length];
        for(int i=0;i<scores.length;i++) {
            String[] cells=lines.get(i).trim().split("\\s+");assertEquals(list(input.get("sample_ids")).get(i),cells[0]);
            for(int c=0;c<scores[i].length;c++)expected[i][c]=Double.parseDouble(cells[c+1]);
        }
        for(int c=0;c<scores[0].length;c++) {
            double dot=0,norm=0;for(int i=0;i<scores.length;i++){dot+=scores[i][c]*expected[i][c];norm+=scores[i][c]*scores[i][c];}
            double sign=Math.copySign(1,dot);assertEquals(1,norm,3e-12);
            for(int i=0;i<scores.length;i++)assertEquals(expected[i][c],scores[i][c]*sign,.50001e-4,"original shrink score "+i+"/"+c);
        }
        assertEquals(basis.length*scores[0].length,result.perturbations().size());
    }
    @Test void changingProjectionTargetsPreservesBasisCoefficientsButChangesAllSampleOutputScaling() throws Exception {
        var reference=originalShrinkage();var input=object(reference.get("input"));var options=settings(object(reference.get("options")));
        byte[][] calls=calls(input);int[] basis=list(input.get("basis_indices")).stream().mapToInt(SmartpcaExtensionsTest::integer).toArray();
        var first=SmartpcaShrinkage.fit(calls,basis,options);
        for(byte[] row:calls)row[row.length-1]=2;
        var second=SmartpcaShrinkage.fit(calls,basis,options);
        assertArrayEquals(first.eigenvalues(),second.eigenvalues());
        for(int sample:basis)assertArrayEquals(first.rawProjectionCoefficients()[sample],second.rawProjectionCoefficients()[sample]);
        assertFalse(Arrays.equals(first.scores()[0],second.scores()[0]));
    }
    @Test void degenerateOutlierDiagnosticsAreRetainedAndResultArraysAreIndependent() {
        double[][] constant={{1},{1},{1},{1}};
        var result=SmartpcaOutliers.detect(constant,1,1,SmartpcaOutliers.Mode.ALL_SAMPLES);
        assertTrue(result.hits().isEmpty());assertTrue(result.nonfiniteDiagnostics());
        for(double[] row:result.deviations())assertTrue(Double.isNaN(row[0]));
        result.deviations()[0][0]=42;assertTrue(Double.isNaN(result.deviations()[0][0]));assertEquals(1,constant[0][0]);
    }
    @Test void resourceAndInputFailuresDoNotRepairOrSubsetAndInterruptIsHonored() {
        assertThrows(IllegalArgumentException.class,()->SmartpcaOutliers.detect(new double[][]{{Double.NaN},{1},{0}},1,1,SmartpcaOutliers.Mode.ALL_SAMPLES));
        assertThrows(IllegalArgumentException.class,()->SmartpcaOutliers.detect(new double[][]{{1},{0},{2}},1,1,SmartpcaOutliers.Mode.LEAVE_ONE_OUT,new SmartpcaOutliers.Limits(1,1)));
        var tiny=new SmartpcaPrepared.Options(1,true,true,1,99,SmartpcaPrepared.Projection.LEAST_SQUARES,new SmartpcaPrepared.Limits(1,1));
        assertThrows(IllegalArgumentException.class,()->SmartpcaShrinkage.fit(new byte[][]{{0,1,2},{2,0,1},{1,2,0}},new int[]{0,1,2},tiny));
        Thread.currentThread().interrupt();
        try { assertThrows(IllegalStateException.class,()->SmartpcaOutliers.detect(new double[][]{{1},{0},{2}},1,1,SmartpcaOutliers.Mode.LEAVE_ONE_OUT)); }
        finally { Thread.interrupted(); }
    }
    private static Map<?,?> originalShrinkage()throws Exception {
        try(var stream=SmartpcaExtensionsTest.class.getResourceAsStream("/org/jlinalg/genetics/smartpca-original-reference.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)) {
                java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals("shrinkage-original-only/reference.json"))return object(GprofilerJson.read(zip.readAllBytes()));
            }
        }
        throw new IllegalStateException("Missing original shrinkage witness");
    }
    private static SmartpcaPrepared.Options settings(Map<?,?> options) {
        return new SmartpcaPrepared.Options(integer(options.get("components")),(Boolean)options.get("use_norm"),(Boolean)options.get("alternate_norm_style"),
            integer(options.get("min_alleles")),integer(options.get("max_missing")),SmartpcaPrepared.Projection.LEAST_SQUARES,SmartpcaPrepared.Limits.defaults());
    }
    private static byte[][] calls(Map<?,?> input) {
        List<?> matrix=list(input.get("genotypes"));byte[][] calls=new byte[matrix.size()][];
        for(int s=0;s<calls.length;s++){var row=list(matrix.get(s));calls[s]=new byte[row.size()];for(int i=0;i<calls[s].length;i++)calls[s][i]=(byte)integer(row.get(i));}return calls;
    }
    private static Map<?,?> object(Object value){if(value instanceof Map<?,?> map)return map;throw new IllegalArgumentException("Expected object");}
    private static List<?> list(Object value){if(value instanceof List<?> rows)return rows;throw new IllegalArgumentException("Expected array");}
    private static double number(Object value){return Double.parseDouble(((GprofilerJson.Numeric)value).token());}
    private static int integer(Object value){return Integer.parseInt(((GprofilerJson.Numeric)value).token());}
    public static void main(String[] arguments)throws Exception {
        var tests=new SmartpcaExtensionsTest();
        tests.comparesTwentyThreeUnchangedOriginalOutlierFunctionCallsIncludingStrictAndNonfiniteBoundaries();
        tests.comparesOriginalWholeProgramShrinkageCoordinatesAndEigenvalues();
        tests.changingProjectionTargetsPreservesBasisCoefficientsButChangesAllSampleOutputScaling();
        tests.degenerateOutlierDiagnosticsAreRetainedAndResultArraysAreIndependent();
        tests.resourceAndInputFailuresDoNotRepairOrSubsetAndInterruptIsHonored();
        System.out.println("PASS: five smartpca extension methods;23 original outlier calls and one original shrinkage case. Workflow qualification remains open.");
    }
}
