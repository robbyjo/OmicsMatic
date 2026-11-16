/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.expression;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class GeNormTest {
    private static Map<String,String> fixtures() throws Exception {
        Map<String,String> data = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(Objects.requireNonNull(GeNormTest.class.getResourceAsStream(
                "/org/jlinalg/expression/genorm-normqpcr158-reference.zip")))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry())
                data.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));
        }
        return data;
    }
    private static String[][] table(String text) {
        return text.lines().skip(1).map(line -> line.split("\t",-1)).toArray(String[][]::new);
    }
    private static void close(double expected,double actual,String label) {
        assertTrue(Double.isFinite(actual),label);
        assertEquals(expected,actual,5e-12*Math.max(1,Math.abs(expected)),label);
    }
    @Test void allFortyFiveCompleteOriginalMatrixMethodReferences() throws Exception {
        var files = fixtures();String[] cases = files.get("case-index.txt").lines().toArray(String[]::new);
        assertEquals(45,cases.length);int values = 0;
        for (String name : cases) {
            String prefix = name + "/";String[][] input = table(files.get(prefix+"input.tsv"));
            double[][] x = new double[input.length][input[0].length-1];
            for (int s = 0; s < x.length; s++) for (int g = 0; g < x[s].length; g++) x[s][g] = Double.parseDouble(input[s][g+1]);
            String scaleProperty = files.get(prefix+"options.properties").strip();
            assertTrue(java.util.Set.of("scale=QUANTITY","scale=LOG2_QUANTITY").contains(scaleProperty),name+" explicit reference scale");
            var scale = scaleProperty.equals("scale=QUANTITY") ? GeNorm.Scale.QUANTITY : GeNorm.Scale.LOG2_QUANTITY;
            var result = GeNorm.select(x,scale);String[][] ranking = table(files.get(prefix+"ranking.tsv"));
            int[] expected = Arrays.stream(ranking).mapToInt(row -> Integer.parseInt(row[1].substring(1))-1).toArray();
            assertArrayEquals(expected,result.order(),name+" complete order (including tie policy)");
            String[][] mean = table(files.get(prefix+"meanM.tsv")), variation = table(files.get(prefix+"variation.tsv"));
            assertEquals(mean.length,result.steps().size());assertEquals(x[0].length-1,mean.length);
            for (int step = 0; step < result.steps().size(); step++) {
                var actual = result.steps().get(step);int count = Integer.parseInt(mean[step][0]);
                assertEquals(count,actual.columns().length);close(Double.parseDouble(mean[step][1]),actual.meanM(),name+" meanM at "+count);values++;
                if (count > 2) {
                    assertEquals((count-1)+"/"+count,variation[step][0]);
                    close(Double.parseDouble(variation[step][1]),actual.variationToNext(),name+" V at "+count);values++;
                    assertEquals(expected[count-1],actual.removedColumn());
                } else { assertTrue(Double.isNaN(actual.variationToNext()));assertEquals(-1,actual.removedColumn()); }
            }
            String[][] stability = table(files.get(prefix+"stability.tsv"));int row = 0;
            for (var step : result.steps()) {
                int[] columns = step.columns();double[] m = step.stabilityM();
                for (int j = 0; j < columns.length; j++,row++) {
                    assertEquals(Integer.toString(columns.length),stability[row][0]);
                    assertEquals(Integer.toString(columns[j]+1),stability[row][1]);
                    close(Double.parseDouble(stability[row][2]),m[j],name+" complete M trace row "+row);values++;
                }
            }
            assertEquals(stability.length,row);
            String[][] nf = table(files.get(prefix+"normalization.tsv"));
            double[] factors = GeNorm.log2Factors(x,scale,Arrays.copyOf(result.order(),2));
            for (int s = 0; s < factors.length; s++) {close(Double.parseDouble(nf[s][1]),factors[s],name+" NF sample "+s);values++;}
        }
        assertTrue(values>4000,"Complete reference values, not only a selected scalar: "+values);
        assertTrue(files.get("large_quantity/literal-quantity-branch.txt").contains("original_literal_quantity_nonfinite_variation=TRUE"));
    }
    @Test void independentlyDerivedThreeGeneExampleAndTargetNormalization() {
        double[][] x = {{0,0,0},{0,1,2},{0,2,4}};
        var result = GeNorm.select(x,GeNorm.Scale.LOG2_QUANTITY);
        assertArrayEquals(new int[]{1,2,0},result.order());
        assertArrayEquals(new double[]{1.5,1,1.5},result.steps().get(0).stabilityM(),0);
        assertEquals(4.0/3,result.steps().get(0).meanM());assertEquals(0.5,result.steps().get(0).variationToNext());
        double[] factors = GeNorm.log2Factors(x,GeNorm.Scale.LOG2_QUANTITY,new int[]{1,2});
        assertArrayEquals(new double[]{0,1.5,3},factors,0);
        var normalized = GeNorm.normalizeLog2(new double[][]{{2},{4},{6}},GeNorm.Scale.LOG2_QUANTITY,factors);
        assertEquals(2,normalized[0][0]);assertEquals(2.5,normalized[1][0]);assertEquals(3,normalized[2][0]);
        double[][] linear = {{1,1,1},{1,2,4},{1,4,16}};
        assertArrayEquals(result.order(),GeNorm.select(linear,GeNorm.Scale.QUANTITY).order());
        assertArrayEquals(factors,GeNorm.log2Factors(linear,GeNorm.Scale.QUANTITY,new int[]{1,2}),1e-15);
    }
    @Test void commonSampleFactorsCancelAndExactTiesKeepOriginalSurvivorOrder() {
        double[][] x = {{0,0,0},{0,1,2},{0,2,4}},shifted = {{-8,-8,-8},{12,13,14},{20,22,24}};
        var a = GeNorm.select(x,GeNorm.Scale.LOG2_QUANTITY);var b = GeNorm.select(shifted,GeNorm.Scale.LOG2_QUANTITY);
        assertArrayEquals(a.order(),b.order());
        assertArrayEquals(a.steps().get(0).stabilityM(),b.steps().get(0).stabilityM(),0);
        double[][] tied = {{-2,0,4,8},{-1,1,5,9},{1,3,7,11}};
        assertArrayEquals(new int[]{2,3,1,0},GeNorm.select(tied,GeNorm.Scale.LOG2_QUANTITY).order());
        var two = GeNorm.select(new double[][]{{0,1},{2,4}},GeNorm.Scale.LOG2_QUANTITY);
        assertArrayEquals(new int[]{0,1},two.order());assertEquals(1,two.steps().size());
        assertArrayEquals(new double[]{Math.sqrt(0.5),Math.sqrt(0.5)},two.steps().get(0).stabilityM(),0);
    }
    @Test void inputsAndReturnedDiagnosticsAreDefensiveCopies() {
        double[][] x = {{0,0,0},{0,1,2},{0,2,4}},before = Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);
        var result = GeNorm.select(x,GeNorm.Scale.LOG2_QUANTITY);result.order()[0]=999;
        result.steps().get(0).columns()[0]=999;result.steps().get(0).stabilityM()[0]=999;
        assertArrayEquals(new int[]{1,2,0},result.order());assertEquals(1.5,result.steps().get(0).stabilityM()[0]);
        assertEquals(0,result.steps().get(0).columns()[0]);
        for(int i=0;i<x.length;i++) assertArrayEquals(before[i],x[i],0);
        assertThrows(UnsupportedOperationException.class,()->result.steps().clear());
    }
    @Test void incompleteNonpositiveRaggedUnboundedAndNonfiniteArithmeticAreExplicitErrors() {
        for (double value : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->GeNorm.select(new double[][]{{value,1},{1,2}},GeNorm.Scale.QUANTITY));
        assertThrows(IllegalArgumentException.class,()->GeNorm.select(new double[][]{{0,1}},GeNorm.Scale.LOG2_QUANTITY));
        assertThrows(IllegalArgumentException.class,()->GeNorm.select(new double[][]{{0,1},{1}},GeNorm.Scale.LOG2_QUANTITY));
        assertThrows(IllegalArgumentException.class,()->GeNorm.select(new double[2][513],GeNorm.Scale.LOG2_QUANTITY));
        assertThrows(IllegalArgumentException.class,()->GeNorm.select(new double[][]{{0},{1}},GeNorm.Scale.LOG2_QUANTITY));
        assertThrows(ArithmeticException.class,()->GeNorm.select(new double[][]{{1e308,-1e308},{1,2}},GeNorm.Scale.LOG2_QUANTITY));
        var x = new double[][]{{1,2},{3,4}};
        for(int[] cols : new int[][]{{0},{0,0},{-1,1},{0,2}})
            assertThrows(IllegalArgumentException.class,()->GeNorm.log2Factors(x,GeNorm.Scale.QUANTITY,cols));
        assertThrows(IllegalArgumentException.class,()->GeNorm.normalizeLog2(x,GeNorm.Scale.QUANTITY,new double[]{1}));
        assertThrows(ArithmeticException.class,()->GeNorm.normalizeLog2(x,GeNorm.Scale.QUANTITY,new double[]{Double.NaN,1}));
    }
}
